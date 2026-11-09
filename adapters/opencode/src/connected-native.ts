// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import type { SessionApi } from "@opencode/client/promise/api"

/** The JVM/strict service decoder lowers only SDK scalars. Domain JSON remains opaque: never
 * JSON.parse/stringify an Int64 assignment/command and claim its original bytes survived. */
export type RetainedConnectedEnvelope = Readonly<{
  key: string; rawJSON: string; digest: string; bindingJSON: string; hostID: string
  sessionID: string; messageID: string; text: string
  metadata: Readonly<{ continuationId: string; assignmentId: string; executionGeneration: string }>
  instructions: readonly Readonly<{ key: string; value: string }>[]
}>
export type ConnectedOutcome = "matched-wake" | "unknown" | "conflict" | "not-performed"
/** Trusted service lowering of an actual binding, NOT a synthetic continuation or notification.
 * Used only to close next-work admission and dispatch the canonical original pause intent. */
export type ConnectedBindingTarget = Readonly<{
  key: string; rawJSON: string; digest: string; bindingJSON: string; hostID: string; sessionID: string
}>
export interface ConnectedNativeBridge {
  // Protected adapter custody and authenticated current service projection, NOT model/RPC fields.
  // retained/observations/absentAndQuiescent are immutable original-effect reads: they MUST NOT
  // reenter current service authority (runtime-facts capture may hold the application monitor).
  retained(key: string): Promise<RetainedConnectedEnvelope>
  /** Actual recaptured service permit: exact generation/claims/authority, full condition frontier
   * and checkpoint context. Assignment.state === active alone is never this proof. */
  current(envelope: RetainedConnectedEnvelope): Promise<"qualified-quiescent" | "active" | "held" | "unavailable">
  managed(sessionID: string): boolean
  binding(sessionID: string): Promise<string | undefined>
  retainObservation(key: string, observation: string): Promise<void>
  observations(key: string): Promise<readonly string[]>
  /** Atomic protected claim for the CURRENT canonical armed effect attempt (not just message ID).
   * A qualified not-performed/new-attempt transition is required before another claim can succeed. */
  claimDispatch(key: string, envelopeDigest: string): Promise<boolean>
  /** Verify exact CURRENT canonical pending pause intent/binding and atomically claim its
   * original dispatch. Key/digest identify either the retained envelope or the exact retained
   * binding target when no envelope exists. False means reconcile, never interrupt again. */
  claimPauseDispatch(key: string, envelopeDigest: string, intent: string): Promise<boolean>
  // This must prove original-call quiescence as well as native absence. No empty-inbox default.
  absentAndQuiescent(envelope: RetainedConnectedEnvelope): Promise<boolean>
}
type Gate = {
  envelope: RetainedConnectedEnvelope; closed: boolean; arrived: boolean
  wait: Promise<void>; resolve: () => void; reject: (error: Error) => void
}
function check(value: unknown, message: string): asserts value { if (!value) throw new Error(message) }
function digest(text: string) { return createHash("sha256").update(text, "utf8").digest("hex") }
function record(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : undefined
}
function matched(value: unknown, e: RetainedConnectedEnvelope, scopedMessage=false): boolean {
  const root = record(value); if (!root || root.id !== e.messageID || root.type !== "synthetic"
    || root.sessionID !== e.sessionID && (!scopedMessage || root.sessionID !== undefined)) return false
  const body = record(root.payload) ?? root, metadata = record(body.metadata)
  return body.text === e.text && !!metadata && Object.keys(metadata).sort().join(",") === "assignmentId,continuationId,executionGeneration"
    && metadata.continuationId === e.metadata.continuationId && metadata.assignmentId === e.metadata.assignmentId
    && metadata.executionGeneration === e.metadata.executionGeneration
}

/** Real pinned native API implementation, not auto-installed. Activation requires an explicitly
 * qualified bridge/profile. Source/type/mock evidence is NOT installed barrier/fencing qualification. */
export class ConnectedNativeAdapter {
  private gates = new Map<string, Gate>(); private closed = false
  private bindingHolds = new Map<string, ConnectedBindingTarget>()
  private disposals: Array<() => Promise<void>> = []
  owns(sessionID:string){return this.gates.has(sessionID)||this.bindingHolds.has(sessionID)}
  constructor(private readonly ctx: Plugin.Context, private readonly client: Pick<SessionApi, "inbox" | "message" | "context">,
    private readonly bridge: ConnectedNativeBridge, private readonly hostID: string) {}
  private async packet(e: RetainedConnectedEnvelope): Promise<boolean> {
    if (!e.instructions.length || e.instructions.length > 256) return false
    let bytes = 0
    for (const entry of e.instructions) {
      if (!/^writhelm-[a-z0-9-]+$/.test(entry.key) || !entry.value.isWellFormed()) return false
      const size = Buffer.byteLength(JSON.stringify(entry.value)); bytes += size
      if (size > 256 * 1024 || bytes > 2 * 1024 * 1024) return false
    }
    const actual = await this.ctx.session.instructions.entry.list({ sessionID: e.sessionID })
    const managed = actual.filter(entry => entry.key.startsWith("writhelm-"))
    return managed.length === e.instructions.length && new Set(e.instructions.map(entry => entry.key)).size === managed.length
      && e.instructions.every(expected => managed.some(entry => entry.key === expected.key && entry.value === expected.value))
  }
  async register(include:(sessionID:string)=>boolean=()=>true) {
    check(process.platform === "linux" && this.ctx.app.version === "2.2.5", "unqualified native platform/version")
    const context = await this.ctx.session.hook("context", async event => {
      if (!include(event.sessionID)||!this.bridge.managed(event.sessionID)) return
      await this.admitContext(event.sessionID)
    })
    const tool = await this.ctx.tool.hook("execute.before", async event => {
      if (!include(event.sessionID)||!this.bridge.managed(event.sessionID)) return
      await this.admitTool(event.sessionID)
    })
    this.disposals.push(context.dispose, tool.dispose)
  }
  /** Reuse the same controller-owned gate from a worker-location plugin instance. A runtime
   * reference/metadata label is never admission; full current binding/packet/policy is rechecked. */
  async admitContext(sessionID:string):Promise<void>{
      check(this.bridge.managed(sessionID),"unmanaged native receiver")
      check(!this.bindingHolds.has(sessionID), "managed binding held without continuation")
      const gate = this.gates.get(sessionID)
      check(gate && !this.closed, "managed native binding not reconciled")
      check(await this.bridge.binding(sessionID) === gate.envelope.bindingJSON, "stale native generation")
      check(await this.packet(gate.envelope), "mandatory native packet changed or incomplete")
      if (!gate.closed) {
        const current = await this.bridge.current(gate.envelope)
        check(this.gates.get(sessionID) === gate && !gate.closed && !this.closed && !this.bindingHolds.has(sessionID) && current === "active", "current authority required")
        return
      }
      // Actual native boundary plus exact promoted input, not inferred merely from inbox presence.
      const messages = await this.client.context({ sessionID })
      // Pinned SessionMessage.Info has no sessionID; the context API was queried for this exact
      // verified receiver. Inbox entries still require their explicit matching sessionID.
      if (messages.some(message => matched(message, gate.envelope,true))) {
        gate.arrived = true
        await this.bridge.retainObservation(gate.envelope.key, JSON.stringify({ kind: "held-boundary", digest: gate.envelope.digest,
          sessionID, messageID: gate.envelope.messageID }))
      }
      await gate.wait // native runtime owns scheduling; no second model loop/trigger
      const current = await this.bridge.current(gate.envelope)
      check(this.gates.get(sessionID) === gate && !gate.closed && !this.closed
        && !this.bindingHolds.has(sessionID) && current === "active", "authority changed while native boundary held")
  }
  async admitTool(sessionID:string):Promise<void>{
      check(this.bridge.managed(sessionID),"unmanaged native receiver")
      check(!this.bindingHolds.has(sessionID), "managed binding held without continuation")
      const gate = this.gates.get(sessionID)
      check(gate, "managed tool admission held")
      const binding = await this.bridge.binding(sessionID), complete = await this.packet(gate.envelope)
      const current = await this.bridge.current(gate.envelope)
      check(this.gates.get(sessionID) === gate && !gate.closed && !this.closed && binding === gate.envelope.bindingJSON
        && complete && !this.bindingHolds.has(sessionID) && current === "active", "managed tool admission held")
  }
  private async envelope(key: string, checkCurrentBinding = true) {
    check(!this.closed, "adapter closed")
    const e = await this.bridge.retained(key)
    check(e.key === key && e.hostID === this.hostID && e.rawJSON.isWellFormed() && Buffer.byteLength(e.rawJSON) <= 16384
      && digest(e.rawJSON) === e.digest, "retained envelope unavailable/mismatched")
    check(e.text.isWellFormed() && Buffer.byteLength(e.text) > 0 && Buffer.byteLength(e.text) <= 4096
      && e.sessionID.startsWith("ses") && e.messageID.startsWith("msg"), "invalid native scalar lowering")
    check(/^(0|[1-9][0-9]{0,18})$/.test(e.metadata.executionGeneration)
      && BigInt(e.metadata.executionGeneration) <= 9223372036854775807n, "invalid exact generation")
    const actual = await this.ctx.session.get({ sessionID: e.sessionID })
    check(actual.id === e.sessionID && !actual.revert, "native subject/revert conflict")
    if (checkCurrentBinding) check(await this.bridge.binding(e.sessionID) === e.bindingJSON, "native subject/revert conflict")
    return e
  }
  async hold(key: string): Promise<boolean> {
    const e = await this.envelope(key), current = this.gates.get(e.sessionID)
    const target = this.bindingHolds.get(e.sessionID)
    if (target) {
      check(target.bindingJSON === e.bindingJSON, "competing native binding target")
      if (await this.bridge.current(e) !== "qualified-quiescent" || !await this.packet(e)) return false
      check(this.bindingHolds.get(e.sessionID) === target && !this.closed, "binding changed during pause reconciliation")
      this.bindingHolds.delete(e.sessionID) // only after actual qualified pause, never interrupt/idle
    }
    if (current) {
      check(current.envelope.digest === e.digest && current.envelope.bindingJSON === e.bindingJSON, "competing native attempt")
      check(current.closed, "cannot silently reclose an active execution")
      return await this.bridge.current(e) === "qualified-quiescent" && await this.packet(e)
    }
    let resolve!: () => void, reject!: (error: Error) => void
    check(this.gates.size < 256, "native binding budget exhausted")
    const wait = new Promise<void>((yes, no) => { resolve = yes; reject = no })
    void wait.catch(() => {}) // close still rejects the hook waiter; no unhandled orphan promise
    this.gates.set(e.sessionID, { envelope: e, closed: true, arrived: false, wait, resolve, reject })
    return await this.bridge.current(e) === "qualified-quiescent" && await this.packet(e)
  }
  /** Install only service-produced exact base entries behind a closed, safe-access barrier.
   * Unknown/changed existing Writhelm entries are not silently deleted or overwritten. */
  async prepare(key: string): Promise<boolean> {
    if (await this.hold(key)) return true
    const e = await this.envelope(key), gate = this.gates.get(e.sessionID)
    if (!gate?.closed || await this.bridge.current(e) !== "qualified-quiescent") return false
    const actual = await this.ctx.session.instructions.entry.list({ sessionID: e.sessionID })
    check(actual.filter(i => i.key.startsWith("writhelm-")).every(i => e.instructions.some(w => w.key === i.key && w.value === i.value)),
      "unknown or changed mandatory native instructions")
    for (const entry of e.instructions) {
      if (actual.some(i => i.key === entry.key && i.value === entry.value)) continue
      check(this.gates.get(e.sessionID) === gate && gate.closed && !this.closed
        && await this.bridge.current(e) === "qualified-quiescent", "safe native packet installation required")
      await this.ctx.session.instructions.entry.put({ sessionID: e.sessionID, key: entry.key, value: entry.value })
    }
    return this.hold(key)
  }
  async deliver(key: string): Promise<ConnectedOutcome> {
    const e = await this.envelope(key), gate = this.gates.get(e.sessionID)
    check(gate?.closed && await this.bridge.current(e) === "qualified-quiescent" && await this.packet(e), "native intent/barrier/current authority required")
    // Protected bridge must atomically retain dispatch before this call. If any dispatch was already
    // possible, controller calls reconcile(), NOT deliver() on the same ID. Duplicate ID can wake.
    check(await this.bridge.claimDispatch(key, e.digest), "reconcile original dispatch; do not wake again")
    try {
      const output = await this.ctx.session.synthetic({ sessionID: e.sessionID, id: e.messageID, text: e.text,
        metadata: { ...e.metadata }, delivery: "steer", resume: true })
      if (!matched(output, e)) return "conflict"
      const actual = await this.ctx.session.get({ sessionID: e.sessionID })
      if (actual.revert) return "unknown"
      await this.bridge.retainObservation(key, JSON.stringify({ kind: "returned-after-wake", digest: e.digest,
        sessionID: e.sessionID, messageID: e.messageID }))
      return "matched-wake"
    } catch { return "unknown" }
  }
  /** Explicit next-work rehold, distinct from hold's qualified physical-access prerequisite.
   * No interrupt acknowledgement, idle state or closed gate constitutes writers-fenced proof. */
  async rehold(key: string) {
    const e = await this.envelope(key), old = this.gates.get(e.sessionID)
    if (old) {
      check(old.envelope.digest === e.digest && old.envelope.bindingJSON === e.bindingJSON, "competing native attempt")
      if (old.closed) return
      old.closed = true // invalidate any waiter belonging to the previously released gate
    }
    let resolve!: () => void, reject!: (error: Error) => void
    check(old || this.gates.size < 256, "native binding budget exhausted")
    const wait = new Promise<void>((yes, no) => { resolve = yes; reject = no })
    void wait.catch(() => {})
    this.gates.set(e.sessionID, { envelope: e, closed: true, arrived: false, wait, resolve, reject })
    await this.bridge.retainObservation(key, JSON.stringify({ kind: "next-work-held", digest: e.digest,
      sessionID: e.sessionID, messageID: e.messageID }))
  }
  async pause(key: string, intent: string) {
    const e = await this.envelope(key), gate = this.gates.get(e.sessionID)
    check(gate?.closed && gate.envelope.digest === e.digest, "rehold before pause required")
    if (!await this.bridge.claimPauseDispatch(key, e.digest, intent)) return
    // Original dispatch is already protected. Lost acknowledgement stays unknown; no blind repeat.
    try {
      await this.ctx.session.interrupt({ sessionID: e.sessionID })
      await this.bridge.retainObservation(key, JSON.stringify({ kind: "pause-interrupt-returned", intent,
        digest: e.digest, sessionID: e.sessionID }))
    } catch {
      await this.bridge.retainObservation(key, JSON.stringify({ kind: "pause-outcome-unknown", intent,
        digest: e.digest, sessionID: e.sessionID }))
    }
  }
  private async bindingTarget(target: ConnectedBindingTarget) {
    target = Object.freeze({ ...target })
    check(!this.closed && target.hostID === this.hostID && target.rawJSON.isWellFormed()
      && Buffer.byteLength(target.rawJSON) <= 16384 && digest(target.rawJSON) === target.digest
      && target.key.isWellFormed() && target.key.length > 0 && Buffer.byteLength(target.key) <= 256
      && target.bindingJSON.isWellFormed() && target.bindingJSON.length > 0 && Buffer.byteLength(target.bindingJSON) <= 16384
      && target.sessionID.startsWith("ses") && target.sessionID.length <= 256 && this.bridge.managed(target.sessionID), "invalid native binding target")
    const actual = await this.ctx.session.get({ sessionID: target.sessionID })
    check(actual.id === target.sessionID && !actual.revert && await this.bridge.binding(target.sessionID) === target.bindingJSON,
      "stale native binding target")
    return target
  }
  /** First blocker on an ordinary ACTIVE binding needs no historical message/checkpoint/envelope.
   * This closes admissions only. No native idle/interrupt fact is promoted to physical fencing. */
  async reholdBinding(target: ConnectedBindingTarget) {
    const t = await this.bindingTarget(target), old = this.bindingHolds.get(t.sessionID), gate = this.gates.get(t.sessionID)
    check(!old || old.digest === t.digest && old.bindingJSON === t.bindingJSON, "competing native binding target")
    check(!gate || gate.envelope.bindingJSON === t.bindingJSON, "competing native binding target")
    check(old || gate || new Set([...this.gates.keys(), ...this.bindingHolds.keys()]).size < 256, "native binding budget exhausted")
    if (gate) gate.closed = true // invalidate admissions whose prior current read is still pending
    this.bindingHolds.set(t.sessionID, t)
    await this.bridge.retainObservation(t.key, JSON.stringify({ kind: "binding-next-work-held", digest: t.digest, sessionID: t.sessionID }))
  }
  async pauseBinding(target: ConnectedBindingTarget, intent: string) {
    const t = await this.bindingTarget(target), held = this.bindingHolds.get(t.sessionID)
    check(held?.digest === t.digest && held.bindingJSON === t.bindingJSON, "binding rehold before pause required")
    if (!await this.bridge.claimPauseDispatch(t.key, t.digest, intent)) return
    check(this.bindingHolds.get(t.sessionID) === held && !this.closed, "binding changed during pause claim")
    try {
      await this.ctx.session.interrupt({ sessionID: t.sessionID })
      await this.bridge.retainObservation(t.key, JSON.stringify({ kind: "binding-pause-interrupt-returned", intent, digest: t.digest, sessionID: t.sessionID }))
    } catch {
      await this.bridge.retainObservation(t.key, JSON.stringify({ kind: "binding-pause-outcome-unknown", intent, digest: t.digest, sessionID: t.sessionID }))
    }
  }
  async reconcile(key: string): Promise<ConnectedOutcome> {
    // The service may capture these immutable original-effect facts while holding its application
    // monitor. Never call back into current service authority here: that creates a cross-process
    // service -> native reconcile -> service deadlock. This read grants no dispatch/admission;
    // deliver/release retain their separate current-binding/current-authority checks.
    const e = await this.envelope(key, false)
    const inbox = await this.client.inbox.list({ sessionID: e.sessionID })
    const pending = inbox.find(item => item.id === e.messageID)
    let promoted: unknown
    try { promoted = await this.client.message.get({ sessionID: e.sessionID, messageID: e.messageID }) } catch { /* absence is not quiescence */ }
    const actual = pending ?? promoted
    if (actual !== undefined && !matched(actual, e,pending===undefined)) return "conflict"
    if (actual === undefined) return await this.bridge.absentAndQuiescent(e) ? "not-performed" : "unknown"
    const observations = (await this.bridge.observations(key)).map(text => record(JSON.parse(text)))
    return observations.some(v => v?.digest === e.digest && v?.sessionID === e.sessionID && v?.messageID === e.messageID
      && (v.kind === "returned-after-wake" || v.kind === "held-boundary")) ? "matched-wake" : "unknown"
  }
  async release(key: string) {
    const e = await this.envelope(key)
    let gate = this.gates.get(e.sessionID)
    if (!gate) {
      // Controller/adapter restart after canonical ACTIVE must restore admission, not wake again.
      // Keep closed until exact retained native effect, packet and current permit are recaptured.
      check(this.gates.size < 256 && !this.closed, "native binding budget exhausted")
      let resolve!: () => void, reject!: (error: Error) => void
      const wait = new Promise<void>((yes, no) => { resolve = yes; reject = no }); void wait.catch(() => {})
      gate = { envelope: e, closed: true, arrived: false, wait, resolve, reject }
      this.gates.set(e.sessionID, gate)
    }
    check(!this.bindingHolds.has(e.sessionID) && gate && gate.envelope.digest === e.digest && await this.bridge.current(e) === "active" && await this.packet(e)
      && await this.reconcile(key) === "matched-wake", "canonical active/current complete frontier and native observation required")
    const current = await this.bridge.current(e)
    check(this.gates.get(e.sessionID) === gate && !this.bindingHolds.has(e.sessionID) && current === "active", "native gate changed during release recapture")
    if (gate.closed) { gate.closed = false; gate.resolve() }
  }
  async close() {
    this.closed = true
    for (const gate of this.gates.values()) if (gate.closed) gate.reject(new Error("native adapter closed while admission held"))
    // Closing a controller is not proof that managed writers stopped. Keep fail-closed admission
    // hooks for still-managed sessions; qualified shutdown/reload must reconcile their actual effects.
    if ([...this.gates.values()].some(gate => this.bridge.managed(gate.envelope.sessionID))
      || [...this.bindingHolds.keys()].some(session => this.bridge.managed(session))) return
    for (const dispose of this.disposals.splice(0)) await dispose()
    this.gates.clear()
  }
}
