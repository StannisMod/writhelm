// SPDX-License-Identifier: AGPL-3.0-only
import { randomUUID, createHash } from "node:crypto"
import { isLosslessNumber } from "lossless-json"
import { Schema } from "effect"
import type { Caller } from "@opencode/plugin/gui"
import { Inputs, reply, Fault, requireThat, type Session, type Reply, type Input, type Code } from "./contracts.js"
import { completeBundle, http, loopback, referenceKey, type Json } from "./wire.js"
import type { ProtectedFiles } from "./protected.js"
import { credential, validateRetained, type PublicSetupRunner } from "./helper.js"
import { mutableWorkFamilies, workRead } from "./work-read.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"

type View = { window: number; session: Session; id: string; requestDigest: string; packetDigest: string; raw: string; bundle: Json; born: number }
type Reservation = { epoch: symbol; done: Promise<void>; release: () => void }
export interface HostBoundary { verify(session: Session, window: number): Promise<void> }
export class Controller {
  private files?: ProtectedFiles; private profile?: Json
  private views = new Map<string, View>(); private epochs = new Map<number, string>()
  private active?: Reservation; private candidate?: ProtectedFiles
  private epoch = Symbol(); private closed = false; private closing?: Promise<void>
  private disposals = new WeakMap<ProtectedFiles, Promise<void>>()
  constructor(private readonly host: HostBoundary, private readonly fetcher: typeof fetch = fetch, private readonly now = () => performance.now()) {}
  async attach(files: ProtectedFiles) {
    let reservation: Reservation | undefined
    try {
      reservation = this.reserve(); this.candidate = files
      requireThat(!this.disposals.has(files), "UNAVAILABLE")
      const p = await files.read(); loopback(p.origin); validateRetained(p)
      this.live(reservation.epoch)
      requireThat(p.active.token.startsWith(`wh1.${p.storeId}.${p.active.credentialId}.`), "PROTECTION_FAILED")
      // Reattachment may recover the SAME retained operation, never replace an unresolved one
      // with an idle/obsolete profile (including via another file object for the same path).
      requireThat(!this.profile?.pending || referenceKey(this.profile) === referenceKey(p), "BUSY")
      for (const window of this.epochs.keys()) this.invalidate(window)
      if (this.files !== files) {
        const previous = this.files; this.files = undefined; this.profile = undefined
        if (previous) await this.dispose(previous)
        this.live(reservation.epoch)
      }
      this.files = files; this.profile = p; this.views.clear()
    } catch (e) {
      if (files !== this.files && (reservation || files !== this.candidate)) await this.dispose(files)
      throw e
    } finally {
      if (reservation) { this.candidate = undefined; this.release(reservation) }
    }
  }
  close(): Promise<void> {
    if (!this.closing) {
      this.closed = true; this.epoch = Symbol(); this.views.clear(); this.epochs.clear()
      const active = this.active
      this.closing = (async () => {
        await active?.done
        const files = this.files; this.files = undefined; this.profile = undefined
        if (files) await this.dispose(files)
      })()
    }
    return this.closing
  }
  private live(epoch = this.epoch) { requireThat(!this.closed, "UNAVAILABLE"); requireThat(epoch === this.epoch, "STALE_VIEW") }
  private reserve(): Reservation {
    this.live(); requireThat(!this.active, "BUSY")
    let release!: () => void
    const reservation = { epoch: Symbol(), done: new Promise<void>(r => { release = r }), release: () => release() }
    this.active = reservation; this.epoch = reservation.epoch
    return reservation
  }
  private release(reservation: Reservation) { if (this.active === reservation) this.active = undefined; reservation.release() }
  private dispose(files: ProtectedFiles) {
    let result = this.disposals.get(files)
    if (!result) { result = Promise.resolve().then(() => files.close()); this.disposals.set(files, result) }
    return result
  }
  invalidate(window: number) { this.epochs.set(window, randomUUID()); for (const [k, v] of this.views) if (v.window === window) this.views.delete(k) }
  closeWindow(window: number) { for (const [k, v] of this.views) if (v.window === window) this.views.delete(k); this.epochs.delete(window) }
  private context(session: Session, caller: Caller) { return this.host.verify(session, caller.window) }
  async dispatch<K extends keyof typeof Inputs>(name: K, input: Input<K>, caller: Caller): Promise<Reply> {
    let reservation: Reservation | undefined
    try {
      Schema.decodeUnknownSync(Inputs[name])(input)
      requireThat(Buffer.byteLength(JSON.stringify(input)) <= 96 * 1024)
      if (["answer", "rotate", "reconcile"].includes(name)) reservation = this.reserve()
      else { this.live(); requireThat(!this.active, "BUSY") }
      const epoch = this.epoch
      await this.context(input.session, caller)
      this.live(epoch)
      if (name === "dismiss") {
        const key = (input as Input<"dismiss">).handle, v = this.views.get(key)
        if (v && v.window === caller.window && sameSession(v.session, input.session)) this.views.delete(key)
        return reply("OK", { pending: !!this.profile?.pending })
      }
      if (!this.profile || !this.files) return reply("UNCONFIGURED")
      const p = this.profile
      if (name === "status") { const r = await this.read("list_projects", { cursor: null, pageSize: 8 }); return reply("OK", { pageJSON: r.raw, pending: !!p.pending }) }
      if (name === "pending") {
        const cursor = (input as Input<"pending">).cursor
        const r = await this.read("list_requests", { cursor: cursor || null, pageSize: 32, pendingOnly: true })
        return reply("OK", { pageJSON: r.raw, pending: !!p.pending })
      }
      if (name === "workList" || name === "workOpen" || name === "workBody") {
        let operation: string, subject: unknown
        if (name === "workList") {
          const i = input as Input<"workList">
          requireThat(i.cursor === "" || /^[0-9a-f-]{36}$/.test(i.cursor))
          operation = "list_work_records"; subject = { family: i.family,taskId: null,pageSize: 32,cursor: i.cursor || null }
        } else {
          const i = input as Input<"workOpen"> | Input<"workBody">
          const key = exactJSON(i.keyJSON,() => { throw new Fault("INVALID_INPUT") })
          requireThat(key.projectId === p.projectId,"FORBIDDEN")
          operation = "read_" + i.family.replaceAll("-","_")
          if (mutableWorkFamilies.has(i.family)) {
            requireThat(i.digest === "" && /^(0|[1-9][0-9]{0,18})$/.test(i.generation) && BigInt(i.generation) <= 9223372036854775807n)
            subject = { key,generation: BigInt(i.generation) }
          } else { requireThat(i.generation === "" && /^[0-9a-f]{64}$/.test(i.digest)); subject = { key,digest: i.digest } }
          if(name === "workBody") {
            operation="read_work_body"
            subject={digest:(i as Input<"workBody">).bodyDigest,record:{family:i.family,...subject as object}}
          }
        }
        const r = await workRead(p.origin,p.active.token,p.projectId,operation,subject,this.fetcher,caller.signal)
        this.live(epoch)
        if (name === "workList") {
          requireThat(Array.isArray(r.value.data?.items) && r.value.data.items.length <= 32 && typeof r.value.data.complete === "boolean"
            && r.value.data.complete === (r.value.data.nextCursor === null)
            && typeof r.value.data.snapshotId === "string" && /^[0-9a-f-]{36}$/.test(r.value.data.snapshotId)
            && (r.value.data.nextCursor === null || typeof r.value.data.nextCursor === "string" && /^[0-9a-f-]{36}$/.test(r.value.data.nextCursor)),"INCOMPLETE")
          const i=input as Input<"workList">
          for(const row of r.value.data.items) requireThat(row.family===i.family && row.key?.projectId===p.projectId
            && (mutableWorkFamilies.has(i.family)?isLosslessNumber(row.generation):typeof row.digest==="string"&&/^[0-9a-f]{64}$/.test(row.digest)),"INTEGRITY")
          return reply("OK",{ pageJSON: r.raw })
        }
        const i = input as Input<"workOpen">
        if(name === "workBody") {
          const body=r.value.data
          requireThat(body?.digest===(input as Input<"workBody">).bodyDigest && typeof body.body==="string"
            && isLosslessNumber(body.byteLength) && BigInt(body.byteLength.value)===BigInt(Buffer.byteLength(body.body))
            && createHash("sha256").update(body.body).digest("hex")===body.digest,"INTEGRITY")
          return reply("OK",{bundleJSON:r.raw})
        }
        requireThat(r.value.data?.family === i.family && contextJSON(r.value.data.key) === contextJSON((subject as any).key),"INTEGRITY")
        requireThat(mutableWorkFamilies.has(i.family)
          ? isLosslessNumber(r.value.data.payload?.generation) && r.value.data.payload.generation.value===i.generation
          : createHash("sha256").update(contextJSON(r.value.data)).digest("hex")===i.digest,"INTEGRITY")
        return reply("OK",{ bundleJSON: r.raw })
      }
      if (name === "open") {
        requireThat(this.epochs.has(caller.window) || this.epochs.size < 8, "BUSY")
        this.invalidate(caller.window); const epoch = this.epochs.get(caller.window)
        const i = input as Input<"open">
        const r = await this.read("read_review_bundle", { id: i.requestId, requestDigest: i.requestDigest, packetDigest: i.packetDigest })
        requireThat(epoch === this.epochs.get(caller.window), "STALE_VIEW")
        const bundle = completeBundle(r.raw, i.requestId, i.requestDigest, i.packetDigest, p.projectId)
        for (const [key, view] of this.views) if (this.now() - view.born >= 300000) this.views.delete(key)
        requireThat(this.views.size < 8, "BUSY")
        const handle = randomUUID(); this.views.set(handle, { window: caller.window, session: input.session, id: i.requestId, requestDigest: i.requestDigest, packetDigest: i.packetDigest, raw: r.raw, bundle, born: this.now() })
        return reply("OK", { handle, bundleJSON: r.raw, pending: !!p.pending })
      }
      {
        if (name === "reconcile") return await this.reconcile(input.session)
        requireThat(!p.pending, "BUSY")
        if (name === "answer") {
          const i = input as Input<"answer">; const v = this.views.get(i.handle)
          requireThat(v && v.window === caller.window && sameSession(v.session, i.session), "STALE_VIEW")
          requireThat(this.now() - v.born < 300000, "EXPIRED_VIEW")
          requireThat(v.bundle.actionable === true, "INCOMPLETE")
          requireThat(i.choice === "changes-required" ? i.comments.length > 0 : i.comments.length === 0)
          requireThat(new Set(i.comments.map(x => x.itemId)).size === i.comments.length)
          for (const c of i.comments) requireThat(c.text.isWellFormed() && c.text.trim() && Buffer.byteLength(c.text) <= 4096 && v.bundle.request.payload.packet.items.some((x: Json) => x.id === c.itemId))
          const answer = i.choice === "changes-required" ? { kind: i.choice, comments: i.comments } : { kind: i.choice }
          const command = { formatVersion: 1, projectId: p.projectId, requestKey: randomUUID(), operation: "resolve_approval", payload: { requestId: v.id, requestDigest: v.requestDigest, packetDigest: v.packetDigest, answer } }
          await this.persist({ ...p, pending: { kind: "answer", session: i.session, commandJSON: JSON.stringify(command), replacement: null, registrationJSON: "", storeDirectory: "" } }) // durable BEFORE network
          return await this.reconcile(i.session)
        }
        if (name === "rotate") {
          const next = credential(p.storeId, p.projectId, p.principalId)
          await this.persist({ ...p, pending: { kind: "rotation", session: input.session, commandJSON: JSON.stringify({ formatVersion: 2, projectId: p.projectId, requestKey: randomUUID(), operation: "rotate_credential", payload: { credentialId: p.active.credentialId, expectedGeneration: 0, newCredentialId: next.credentialId, verifierVersion: 1, newVerifierDigest: next.digest } }), replacement: { credentialId: next.credentialId, token: next.token }, registrationJSON: "", storeDirectory: "" } })
          return await this.reconcile(input.session)
        }
        return reply("INVALID_INPUT")
      }
    } catch (e) { return reply(e instanceof Fault ? e.code : "UNAVAILABLE", { pending: !!this.profile?.pending }) }
    finally { if (reservation) this.release(reservation) }
  }
  private async read(operation: string, subject: Json) {
    const epoch = this.epoch
    const p = this.profile; const r = await http(p.origin, p.active.token, "reads", JSON.stringify({ formatVersion: 1, projectId: operation === "list_projects" ? null : p.projectId, operation, subject }), this.fetcher)
    this.live(epoch)
    this.check(r.value); return r
  }
  private async persist(value: Json) {
    this.live(); const files = this.files!
    await files.write(value)
    this.live(); this.profile = value
  }
  private check(value: Json) {
    if (value.kind === "error") {
      const code: Code = ["UNAUTHENTICATED", "FORBIDDEN", "IDENTITY_CONFLICT", "PUBLICATION_UNKNOWN"].includes(value.code) ? value.code : "UNAVAILABLE"
      throw new Fault(code)
    }
  }
  private async reconcile(session: Session): Promise<Reply> {
    const p = this.profile, pending = p.pending
    requireThat(pending && sameSession(pending.session, session), "STALE_VIEW")
    requireThat(pending.kind !== "setup", "SETUP_UNKNOWN")
    let r = await http(p.origin, pending.kind === "rotation" ? pending.replacement.token : p.active.token, "commands", pending.commandJSON, this.fetcher)
    this.live()
    if (pending.kind === "rotation" && r.value.kind === "error" && r.value.code === "UNAUTHENTICATED") {
      r = await http(p.origin, p.active.token, "commands", pending.commandJSON, this.fetcher)
      this.live()
    }
    this.check(r.value)
    const original = JSON.parse(pending.commandJSON)
    requireThat(r.value.kind === "settled" && r.value.identity.requestKey === original.requestKey && r.value.identity.projectId === p.projectId && r.value.identity.principalId === p.principalId && r.value.identity.operation === original.operation, "INTEGRITY")
    const success = r.value.outcome.kind === "success"
    if (success && pending.kind === "rotation") requireThat(r.value.outcome.result.credentialId === pending.replacement.credentialId, "INTEGRITY")
    await this.persist({ ...p, active: success && pending.kind === "rotation" ? pending.replacement : p.active, pending: null }); this.views.clear()
    return reply(success ? "OK" : "DOMAIN_REJECTED", { resultJSON: r.raw })
  }
  async finishSetup(runner: PublicSetupRunner, verify: boolean): Promise<Reply> {
    let reservation: Reservation | undefined
    try {
      reservation = this.reserve()
      return await this.setupReserved(runner, verify)
    } catch (e) { return reply(e instanceof Fault ? e.code : "SETUP_UNKNOWN", { pending: true }) }
    finally { if (reservation) this.release(reservation) }
  }
  private async setupReserved(runner: PublicSetupRunner, verify: boolean): Promise<Reply> {
    const p = this.profile
    requireThat(p?.pending?.kind === "setup", "INVALID_INPUT")
    await runner.run(JSON.parse(p.pending.registrationJSON), verify)
    await this.persist({ ...p, pending: null }); return reply()
  }
  async retry(input: Input<"reconcile">, caller: Caller, runner: PublicSetupRunner): Promise<Reply> {
    let reservation: Reservation | undefined
    try {
      Schema.decodeUnknownSync(Inputs.reconcile)(input); reservation = this.reserve()
      await this.context(input.session, caller); this.live(reservation.epoch)
      if (!this.profile || !this.files) return reply("UNCONFIGURED")
      if (this.profile.pending?.kind === "setup") { requireThat(sameSession(this.profile.pending.session, input.session), "STALE_VIEW"); return await this.setupReserved(runner, true) }
      return await this.reconcile(input.session)
    } catch (e) { return reply(e instanceof Fault ? e.code : "UNAVAILABLE", { pending: !!this.profile?.pending }) }
    finally { if (reservation) this.release(reservation) }
  }
}
export function sameSession(a: Session, b: Session) { return a.key === b.key && a.id === b.id && a.server === b.server && a.directory === b.directory && a.projectRoot === b.projectRoot }
