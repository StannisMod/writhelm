// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { isLosslessNumber, stringify as losslessStringify } from "lossless-json"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern, type NativeSubject } from "./native-runtime-client.js"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import type { ConnectedBindingTarget, ConnectedNativeBridge, RetainedConnectedEnvelope } from "./connected-native.js"

type Retained = Readonly<{ formatVersion: 1; kind: "envelope" | "binding"; subject: NativeSubject;
  target: RetainedConnectedEnvelope | ConnectedBindingTarget }>
const hash = (s: string) => createHash("sha256").update(s, "utf8").digest("hex")

/** Concrete protected bridge state. Only the trusted local controller transport may install
 * originals; this class is not a tool/RPC and never accepts an asserted physical qualification.
 * Current permissions always come from the service. Retained-effect reads never call it back. */
export class ProtectedNativeBridge implements ConnectedNativeBridge {
  readonly #sessions = new Set<string>()
  readonly #hintSubjects = new Map<string, NativeSubject>()
  readonly #hintAbort = new AbortController()
  #hintWorker: Promise<void> | undefined
  constructor(private readonly registry: NativeCredentialRegistry, private readonly custody: LinuxNativeCustody,
    private readonly controllerSessionID: string, private readonly hostID: string) {
    if (!uuidPattern.test(hostID)) nativeUnavailable()
  }
  managed(sessionID: string): boolean { return this.#sessions.has(sessionID) || this.registry.managed(sessionID) }
  async identity() {
    const current=await this.registry.identity(this.controllerSessionID)
    if(current.scope!=="controller")nativeUnavailable()
    return this.startupIdentity()
  }
  /** Protected immutable host/session observer; MUST NOT reenter service HTTP admission during
   * its connect callback. Current controller rights are independently checked by the service. */
  async startupIdentity() {
    const session = await this.registry.inspect(this.controllerSessionID)
    const identity = await this.registry.immutableControllerIdentity(this.controllerSessionID)
    if (!session || session.scope !== "controller") nativeUnavailable()
    const workRoots = new Set([session.workRoot])
    for (const id of this.#sessions) {
      const managed = await this.registry.inspect(id)
      if (!managed) nativeUnavailable()
      workRoots.add(managed.workRoot)
    }
    return Object.freeze({ hostId: this.hostID, controllerId: identity.controllerId,
      principalId: identity.principalId, credentialId: identity.credentialId,
      sessionID: this.controllerSessionID, epoch: session.epoch, workRoots: [...workRoots].sort() })
  }
  /** Close native admission before asynchronous validation. A failed restore stays managed/held. */
  async manage(sessionID: string) {
    if (!/^ses_[A-Za-z0-9_-]{1,128}$/.test(sessionID) || !this.#sessions.has(sessionID) && this.#sessions.size >= 256) nativeUnavailable()
    this.#sessions.add(sessionID)
    const binding = await this.registry.inspect(sessionID)
    if (!binding || binding.scope !== "assignment" || binding.hostId !== this.hostID) nativeUnavailable()
  }
  async binding(sessionID: string): Promise<string | undefined> {
    if (!this.managed(sessionID)) return undefined
    const binding = await this.registry.inspect(sessionID)
    if (!binding || binding.scope !== "assignment") return undefined
    const status = await this.registry.probe(this.controllerSessionID, { assignmentId: binding.assignmentId!,
      executionGeneration: binding.executionGeneration!, bindingJSON: binding.bindingJSON! })
    return status.nativeSessionId === sessionID ? status.bindingJSON ?? undefined : undefined
  }
  private decode(raw: string): Retained {
    const value = closedObject(exactJSON(raw, nativeUnavailable), ["formatVersion", "kind", "subject", "target"])
    if (!isLosslessNumber(value.formatVersion) || value.formatVersion.value !== "1"
      || !["envelope", "binding"].includes(value.kind)) nativeUnavailable()
    const subject = closedObject(value.subject, ["assignmentId", "executionGeneration", "bindingJSON"])
    if (!uuidPattern.test(subject.assignmentId) || typeof subject.executionGeneration !== "string"
      || !/^(0|[1-9][0-9]{0,18})$/.test(subject.executionGeneration)
      || BigInt(subject.executionGeneration) > 9223372036854775807n || typeof subject.bindingJSON !== "string") nativeUnavailable()
    const target = closedObject(value.target, value.kind === "envelope"
      ? ["key", "rawJSON", "digest", "bindingJSON", "hostID", "sessionID", "messageID", "text", "metadata", "instructions"]
      : ["key", "rawJSON", "digest", "bindingJSON", "hostID", "sessionID"])
    if (typeof target.key !== "string" || !target.key || Buffer.byteLength(target.key) > 256
      || typeof target.rawJSON !== "string" || Buffer.byteLength(target.rawJSON) > 16384
      || target.digest !== hash(target.rawJSON) || target.hostID !== this.hostID
      || typeof target.sessionID !== "string" || !/^ses_[A-Za-z0-9_-]{1,128}$/.test(target.sessionID)
      || target.bindingJSON !== subject.bindingJSON) nativeUnavailable()
    if (value.kind === "binding") {
      const original = closedObject(exactJSON(target.rawJSON, nativeUnavailable), ["assignment", "binding", "nativeHostId", "nativeSessionId"])
      const assignment = closedObject(original.assignment, ["assignmentId", "generation"])
      if (assignment.assignmentId !== subject.assignmentId || !isLosslessNumber(assignment.generation)
        || assignment.generation.value !== subject.executionGeneration || original.nativeHostId !== target.hostID
        || original.nativeSessionId !== target.sessionID
        || losslessStringify(original.binding) !== losslessStringify(exactJSON(subject.bindingJSON, nativeUnavailable))) nativeUnavailable()
    } else {
      const original = exactJSON(target.rawJSON, nativeUnavailable)
      const metadata = closedObject(target.metadata, ["continuationId", "assignmentId", "executionGeneration"])
      if (!uuidPattern.test(metadata.continuationId) || metadata.assignmentId !== subject.assignmentId
        || metadata.executionGeneration !== subject.executionGeneration || original.nativeHostId !== target.hostID
        || original.nativeSessionId !== target.sessionID || original.nativeMessageId !== target.messageID
        || original.text !== target.text || original.delivery !== "steer" || original.resume !== true
        || original.metadata?.continuationId !== metadata.continuationId
        || original.metadata?.assignmentId !== metadata.assignmentId
        || original.metadata?.executionGeneration !== metadata.executionGeneration
        || typeof target.text !== "string" || !target.text.length || Buffer.byteLength(target.text) > 4096
        || !Array.isArray(target.instructions) || !target.instructions.length || target.instructions.length > 256) nativeUnavailable()
      let bytes = 0
      for (const entry of target.instructions) {
        const instruction = closedObject(entry, ["key", "value"])
        if (typeof instruction.key !== "string" || !/^writhelm-[a-z0-9-]+$/.test(instruction.key)
          || typeof instruction.value !== "string" || Buffer.byteLength(instruction.value) > 256 * 1024) nativeUnavailable()
        bytes += Buffer.byteLength(instruction.value)
      }
      if (bytes > 2 * 1024 * 1024 || new Set(target.instructions.map((i: { key: string }) => i.key)).size !== target.instructions.length) nativeUnavailable()
    }
    return value as Retained
  }
  private async record(key: string): Promise<Retained> {
    const raw = await this.custody.read("native-original", key)
    if (raw === undefined) nativeUnavailable()
    const value = this.decode(raw)
    if (value.target.key !== key || !this.managed(value.target.sessionID)) nativeUnavailable()
    return value
  }
  private async guard(kind: string, key: string, digest: string, mayCreate: boolean) {
    let raw = await this.custody.read(kind, key)
    if (raw === undefined) {
      if (!mayCreate) nativeUnavailable() // missing custody after canonical arming is UNKNOWN, never fresh
      raw = JSON.stringify({ formatVersion: 1, kind: "native-effect-guard-v1", key, digest, claim: null })
      await this.custody.retain(kind, key, raw)
    }
    const value = closedObject(exactJSON(raw, nativeUnavailable), ["formatVersion", "kind", "key", "digest", "claim"])
    if (!isLosslessNumber(value.formatVersion) || value.formatVersion.value !== "1" || value.kind !== "native-effect-guard-v1"
      || value.key !== key || value.digest !== digest || value.claim !== null && !uuidPattern.test(value.claim)) nativeUnavailable()
    return { raw, claim: value.claim as string | null }
  }
  private async claim(kind: string, key: string, digest: string, claim: string): Promise<boolean> {
    const previous = await this.guard(kind, key, digest, false)
    if (previous.claim !== null) {
      if (previous.claim !== claim) nativeUnavailable() // no automatic retirement/new-attempt inference
      return false
    }
    return this.custody.replace(kind, key, hash(previous.raw), JSON.stringify({ formatVersion: 1, kind: "native-effect-guard-v1", key, digest, claim }))
  }
  async retainOriginal(kind: Retained["kind"], subject: NativeSubject, target: Retained["target"]): Promise<void> {
    // Round-trip freezes all nested caller data before the first asynchronous authority check.
    const raw = JSON.stringify({ formatVersion: 1, kind, subject, target }), value = this.decode(raw)
    await this.manage(value.target.sessionID)
    const status = await this.registry.probe(this.controllerSessionID, value.subject,
      value.kind === "envelope" ? (value.target as RetainedConnectedEnvelope).metadata.continuationId : null)
    if (status.bindingJSON !== value.subject.bindingJSON || status.nativeSessionId !== value.target.sessionID
      || value.kind === "envelope" && status.envelopeDigest !== value.target.digest) nativeUnavailable()
    let fresh = status.freshPause
    if (value.kind === "envelope") {
      const e = value.target as RetainedConnectedEnvelope
      const packet = await this.registry.packet(this.controllerSessionID, value.subject, e.metadata.continuationId)
      if (packet.envelopeDigest !== e.digest || packet.nativeSessionId !== e.sessionID
        || JSON.stringify(packet.instructions) !== JSON.stringify(e.instructions)) nativeUnavailable()
      fresh = status.freshEnvelope && packet.freshEnvelope
    }
    const previous = await this.custody.read("native-original", value.target.key)
    if (previous === undefined ? !fresh : previous !== raw) nativeUnavailable()
    await this.guard(value.kind === "envelope" ? "native-delivery-guard" : "native-pause-guard", value.target.key, value.target.digest, fresh)
    await this.custody.retain("native-original", value.target.key, raw)
  }
  /** Complete preparation from current service-produced packet. The incoming domain envelope is
   * opaque original bytes; no caller instruction rendering or numeric conversion is trusted. */
  async prepareEnvelope(subject: NativeSubject, target: Omit<RetainedConnectedEnvelope, "instructions">) {
    const frozen = JSON.parse(JSON.stringify({ subject, target })) as { subject: NativeSubject; target: typeof target }
    const packet = await this.registry.packet(this.controllerSessionID, frozen.subject, frozen.target.metadata.continuationId)
    await this.retainOriginal("envelope", frozen.subject, { ...frozen.target, instructions: packet.instructions })
  }
  async retained(key: string): Promise<RetainedConnectedEnvelope> {
    const value = await this.record(key)
    if (value.kind !== "envelope") nativeUnavailable()
    return value.target as RetainedConnectedEnvelope
  }
  async bindingTarget(key: string, digest: string): Promise<ConnectedBindingTarget> {
    const value = await this.record(key)
    if (value.kind !== "binding" || value.target.digest !== digest) nativeUnavailable()
    return value.target
  }
  async current(envelope: RetainedConnectedEnvelope): Promise<"qualified-quiescent" | "active" | "held" | "unavailable"> {
    try {
      const value = await this.record(envelope.key)
      if (value.kind !== "envelope" || value.target.digest !== envelope.digest) return "unavailable"
      const status = await this.registry.probe(this.controllerSessionID, value.subject, envelope.metadata.continuationId)
      if (status.bindingJSON !== envelope.bindingJSON || status.nativeSessionId !== envelope.sessionID
        || status.envelopeDigest !== envelope.digest) return "held"
      // Only the service's trusted platform observer can provide safe access. Production's
      // unavailable observer never sets this bit; paused/interrupt/idle alone is not sufficient.
      return status.active ? "active" : status.quiescent ? "qualified-quiescent" : "held"
    } catch { return "unavailable" }
  }
  async claimDispatch(key: string, envelopeDigest: string): Promise<boolean> {
    const value = await this.record(key)
    if (value.kind !== "envelope" || value.target.digest !== envelopeDigest) nativeUnavailable()
    const e = value.target as RetainedConnectedEnvelope
    const status = await this.registry.probe(this.controllerSessionID, value.subject, e.metadata.continuationId)
    if (!status.delivery || !status.attemptId || status.envelopeDigest !== envelopeDigest
      || status.bindingJSON !== e.bindingJSON || status.nativeSessionId !== e.sessionID) nativeUnavailable()
    return this.claim("native-delivery-guard", key, envelopeDigest, status.attemptId)
  }
  async claimPauseDispatch(key: string, envelopeDigest: string, intent: string): Promise<boolean> {
    const value = await this.record(key)
    if (value.target.digest !== envelopeDigest || !uuidPattern.test(intent)) nativeUnavailable()
    const status = await this.registry.probe(this.controllerSessionID, value.subject)
    if (!status.pause || status.intentOperation !== "pause" || status.intentId !== intent
      || status.bindingJSON !== value.target.bindingJSON || status.nativeSessionId !== value.target.sessionID) nativeUnavailable()
    return this.claim("native-pause-guard", key, envelopeDigest, intent)
  }
  async observations(key: string): Promise<readonly string[]> {
    await this.record(key)
    const raw = await this.custody.read("native-observations", key)
    if (raw === undefined) return []
    const value = exactJSON(raw, nativeUnavailable)
    if (!Array.isArray(value) || value.length > 256 || value.some(v => typeof v !== "string" || Buffer.byteLength(v) > 16384)) nativeUnavailable()
    return value
  }
  async retainObservation(key: string, observation: string): Promise<void> {
    if (!observation.isWellFormed() || Buffer.byteLength(observation) > 16384) nativeUnavailable()
    exactJSON(observation, nativeUnavailable)
    const original = await this.custody.read("native-observations", key)
    const previous = await this.observations(key)
    // Reconciliation/rehold repeats are not new native facts. Re-emitting their hint creates
    // engine -> rehold -> identical observation -> engine feedback without any canonical change.
    if (previous.includes(observation)) return
    if (previous.length >= 256) nativeUnavailable()
    const next = JSON.stringify([...previous, observation])
    if (original === undefined) await this.custody.retain("native-observations", key, next)
    else await this.custody.replace("native-observations", key, hash(original), next)
    await this.signal(key)
  }
  private async signal(key: string) {
    const value = await this.record(key)
    if (this.#hintAbort.signal.aborted) return
    this.#hintSubjects.set(value.target.sessionID, value.subject)
    this.startHints()
  }
  private startHints() {
    if (this.#hintWorker) return
    this.#hintWorker = (async () => {
      while (this.#hintSubjects.size && !this.#hintAbort.signal.aborted) {
        const [session, subject] = this.#hintSubjects.entries().next().value!
        this.#hintSubjects.delete(session)
        try { await this.registry.hint(this.controllerSessionID, subject, this.#hintAbort.signal) } catch { /* canonical restart scan remains recovery */ }
      }
    })().finally(() => {
      this.#hintWorker = undefined
      if (this.#hintSubjects.size && !this.#hintAbort.signal.aborted) this.startHints()
    })
    // Hints cannot delay original native observation custody or block a native hook/effect reply.
  }
  async close() {
    this.#hintAbort.abort()
    this.#hintSubjects.clear()
    await this.#hintWorker
  }
  async absentAndQuiescent(_envelope: RetainedConnectedEnvelope): Promise<boolean> {
    return false // no qualified original-call/physical-effect quiescence adapter in this profile
  }
}
