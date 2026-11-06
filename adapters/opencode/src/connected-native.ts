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
export interface ConnectedNativeBridge {
  // Protected adapter custody and authenticated current service projection, NOT model/RPC fields.
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
   * original dispatch. False means reconcile existing effect, never interrupt again. */
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
function matched(value: unknown, e: RetainedConnectedEnvelope): boolean {
  const root = record(value); if (!root || root.id !== e.messageID || root.sessionID !== e.sessionID || root.type !== "synthetic") return false
  const body = record(root.payload) ?? root, metadata = record(body.metadata)
  return body.text === e.text && !!metadata && Object.keys(metadata).sort().join(",") === "assignmentId,continuationId,executionGeneration"
    && metadata.continuationId === e.metadata.continuationId && metadata.assignmentId === e.metadata.assignmentId
    && metadata.executionGeneration === e.metadata.executionGeneration
}

/** Real pinned native API implementation, not auto-installed. Activation requires an explicitly
 * qualified bridge/profile. Source/type/mock evidence is NOT installed barrier/fencing qualification. */
export class ConnectedNativeAdapter {
  private gates = new Map<string, Gate>(); private closed = false
  private disposals: Array<() => Promise<void>> = []
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
  async register() {
    check(process.platform === "linux" && this.ctx.app.version === "2.2.5", "unqualified native platform/version")
    const context = await this.ctx.session.hook("context", async event => {
      if (!this.bridge.managed(event.sessionID)) return
      const gate = this.gates.get(event.sessionID)
      check(gate && !this.closed, "managed native binding not reconciled")
      check(await this.bridge.binding(event.sessionID) === gate.envelope.bindingJSON, "stale native generation")
      check(await this.packet(gate.envelope), "mandatory native packet changed or incomplete")
      if (!gate.closed) {
        const current = await this.bridge.current(gate.envelope)
        check(this.gates.get(event.sessionID) === gate && !gate.closed && !this.closed && current === "active", "current authority required")
        return
      }
      // Actual native boundary plus exact promoted input, not inferred merely from inbox presence.
      const messages = await this.client.context({ sessionID: event.sessionID })
      if (messages.some(message => matched(message, gate.envelope))) {
        gate.arrived = true
        await this.bridge.retainObservation(gate.envelope.key, JSON.stringify({ kind: "held-boundary", digest: gate.envelope.digest,
          sessionID: event.sessionID, messageID: gate.envelope.messageID }))
      }
      await gate.wait // native runtime owns scheduling; no second model loop/trigger
      const current = await this.bridge.current(gate.envelope)
      check(this.gates.get(event.sessionID) === gate && !gate.closed && !this.closed
        && current === "active", "authority changed while native boundary held")
    })
    const tool = await this.ctx.tool.hook("execute.before", async event => {
      if (!this.bridge.managed(event.sessionID)) return
      const gate = this.gates.get(event.sessionID)
      check(gate, "managed tool admission held")
      const binding = await this.bridge.binding(event.sessionID), current = await this.bridge.current(gate.envelope)
      check(this.gates.get(event.sessionID) === gate && !gate.closed && !this.closed && binding === gate.envelope.bindingJSON
        && current === "active", "managed tool admission held")
    })
    this.disposals.push(context.dispose, tool.dispose)
  }
  private async envelope(key: string) {
    check(!this.closed, "adapter closed")
    const e = await this.bridge.retained(key)
    check(e.key === key && e.hostID === this.hostID && e.rawJSON.isWellFormed() && Buffer.byteLength(e.rawJSON) <= 16384
      && digest(e.rawJSON) === e.digest, "retained envelope unavailable/mismatched")
    check(e.text.isWellFormed() && Buffer.byteLength(e.text) > 0 && Buffer.byteLength(e.text) <= 4096
      && e.sessionID.startsWith("ses") && e.messageID.startsWith("msg"), "invalid native scalar lowering")
    check(/^(0|[1-9][0-9]{0,18})$/.test(e.metadata.executionGeneration)
      && BigInt(e.metadata.executionGeneration) <= 9223372036854775807n, "invalid exact generation")
    const actual = await this.ctx.session.get({ sessionID: e.sessionID })
    check(actual.id === e.sessionID && !actual.revert && await this.bridge.binding(e.sessionID) === e.bindingJSON, "native subject/revert conflict")
    return e
  }
  async hold(key: string): Promise<boolean> {
    const e = await this.envelope(key), current = this.gates.get(e.sessionID)
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
  async reconcile(key: string): Promise<ConnectedOutcome> {
    const e = await this.envelope(key)
    const inbox = await this.client.inbox.list({ sessionID: e.sessionID })
    const pending = inbox.find(item => item.id === e.messageID)
    let promoted: unknown
    try { promoted = await this.client.message.get({ sessionID: e.sessionID, messageID: e.messageID }) } catch { /* absence is not quiescence */ }
    const actual = pending ?? promoted
    if (actual !== undefined && !matched(actual, e)) return "conflict"
    if (actual === undefined) return await this.bridge.absentAndQuiescent(e) ? "not-performed" : "unknown"
    const observations = (await this.bridge.observations(key)).map(text => record(JSON.parse(text)))
    return observations.some(v => v?.digest === e.digest && v?.sessionID === e.sessionID && v?.messageID === e.messageID
      && (v.kind === "returned-after-wake" || v.kind === "held-boundary")) ? "matched-wake" : "unknown"
  }
  async release(key: string) {
    const e = await this.envelope(key), gate = this.gates.get(e.sessionID)
    check(gate && gate.envelope.digest === e.digest && await this.bridge.current(e) === "active" && await this.packet(e)
      && await this.reconcile(key) === "matched-wake", "canonical active/current complete frontier and native observation required")
    const current = await this.bridge.current(e)
    check(this.gates.get(e.sessionID) === gate && current === "active", "native gate changed during release recapture")
    if (gate.closed) { gate.closed = false; gate.resolve() }
  }
  async close() {
    this.closed = true
    for (const gate of this.gates.values()) if (gate.closed) gate.reject(new Error("native adapter closed while admission held"))
    // Closing a controller is not proof that managed writers stopped. Keep fail-closed admission
    // hooks for still-managed sessions; qualified shutdown/reload must reconcile their actual effects.
    if ([...this.gates.values()].some(gate => this.bridge.managed(gate.envelope.sessionID))) return
    for (const dispose of this.disposals.splice(0)) await dispose()
    this.gates.clear()
  }
}
