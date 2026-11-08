// SPDX-License-Identifier: AGPL-3.0-only
import { createHash, randomUUID } from "node:crypto"
import { Effect, Schema } from "effect"
import type { Plugin } from "@opencode/plugin/effect"
import type { SessionCompaction } from "@opencode/plugin/effect/session"
import { SessionMessage } from "@opencode/schema/session-message"
import { isLosslessNumber } from "lossless-json"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"

export type NativeContextScope = Readonly<{
  storeId: string; projectId: string; controllerId: string; hostId: string; sessionID: string; epoch: string
  scope: "assignment" | "controller"; assignmentId: string | null; executionGeneration: string | null; bindingJSON: string | null
}>
export type NativeSourceFormat1 = Readonly<{
  formatVersion: 1; locatorId: string; scope: NativeContextScope; profileJSON: string; modelJSON: string
  system: string; prompt: string; packet: string
  nativeMessages: readonly Readonly<{ messageId: string; rawJSON: string }>[]
  pendingNativeMessages: readonly Readonly<{ messageId: string; rawJSON: string }>[]
}>
export type NativeSourceProof = Readonly<{
  locatorId: string; scope: NativeContextScope; profileJSON: string; modelJSON: string; renderedRequestDigest: string; packetDigest: string
  nativeMessages: readonly Readonly<{ messageId: string; digest: string }>[]
}>
const hash = (text: string) => createHash("sha256").update(text, "utf8").digest("hex")
const same = (a: unknown, b: unknown) => contextJSON(a) === contextJSON(b)
const attempt = <A>(f: (signal: AbortSignal) => Promise<A>) => Effect.tryPromise({ try: f, catch: () => new Error("native context source unavailable") })
// Keep the actual SDK runtime codec; erase its enormous generated union at this consumer boundary
// instead of instantiating that union through every Effect.gen yield. No substitute SDK schema.
const encodeNative = Schema.encodeSync(SessionMessage.Info as unknown as Schema.Codec<unknown, unknown>)

/** Refuse unsupported/secret-bearing structured metadata rather than redact or claim that a
 * partial native observation is the original. This is not universal free-text secret detection. */
function safeNative(value: unknown, depth = 0, budget = { nodes: 0 }): void {
  if (depth > 64 || ++budget.nodes > 100000) nativeUnavailable()
  if (typeof value === "string") {
    if (!value.isWellFormed() || /wh1\.[0-9a-f-]{36}\.[0-9a-f-]{36}\.[A-Za-z0-9_-]{43}/.test(value)) nativeUnavailable()
    return
  }
  if (value === null || typeof value === "boolean") return
  if (typeof value === "number") { if (!Number.isFinite(value)) nativeUnavailable(); return }
  if (Array.isArray(value)) { if (value.length > 4096) nativeUnavailable(); value.forEach(v => safeNative(v, depth + 1, budget)); return }
  if (!value || typeof value !== "object") nativeUnavailable()
  for (const [key, item] of Object.entries(value)) {
    if (/^(headers|authorization|api[-_]?key|credentials?|secrets?|options|providerOptions)$/i.test(key)) nativeUnavailable()
    safeNative(item, depth + 1, budget)
  }
}

/** Private exact-byte source custody. Public source retrieval is selected by the ACTUAL native
 * session and current service READ; UUID/hash possession does not select another subject. */
export class NativeContextSources {
  constructor(private readonly registry: NativeCredentialRegistry, private readonly custody: LinuxNativeCustody) {}
  bound(sessionID: string): boolean { return this.registry.bound(sessionID) }
  scopeKind(sessionID: string) { return this.registry.scopeKind(sessionID) }
  async currentScope(sessionID: string, signal?: AbortSignal): Promise<NativeContextScope> {
    const actual = await this.registry.inspect(sessionID)
    if (!actual) nativeUnavailable()
    const identity = await this.registry.sourceIdentity(sessionID, signal)
    if (identity.scope !== actual.scope || identity.principalId !== actual.principalId || identity.credentialId !== actual.credentialId) nativeUnavailable()
    if (actual.scope === "assignment") {
      const status = await this.registry.probe(sessionID, { assignmentId: actual.assignmentId!,
        executionGeneration: actual.executionGeneration!, bindingJSON: actual.bindingJSON! })
      if (status.bindingJSON !== actual.bindingJSON || status.nativeSessionId !== sessionID || status.nativeHostId !== actual.hostId) nativeUnavailable()
    }
    return Object.freeze({ storeId: identity.storeId, projectId: identity.projectId, controllerId: identity.controllerId,
      hostId: actual.hostId, sessionID, epoch: actual.epoch, scope: actual.scope, assignmentId: actual.assignmentId,
      executionGeneration: actual.executionGeneration, bindingJSON: actual.bindingJSON })
  }
  private decode(raw: string): NativeSourceFormat1 {
    const value = closedObject(exactJSON(raw, nativeUnavailable), ["formatVersion", "locatorId", "scope", "profileJSON", "modelJSON", "system", "prompt", "packet", "nativeMessages", "pendingNativeMessages"])
    if (!isLosslessNumber(value.formatVersion) || value.formatVersion.value !== "1" || !uuidPattern.test(value.locatorId)) nativeUnavailable()
    const scope = closedObject(value.scope, ["storeId", "projectId", "controllerId", "hostId", "sessionID", "epoch", "scope", "assignmentId", "executionGeneration", "bindingJSON"])
    if (![scope.storeId, scope.projectId, scope.controllerId, scope.hostId].every(v => typeof v === "string" && uuidPattern.test(v))
      || typeof scope.sessionID !== "string" || !/^ses_[A-Za-z0-9_-]{1,128}$/.test(scope.sessionID)
      || !/^[0-9a-f]{64}$/.test(scope.epoch) || !["assignment", "controller"].includes(scope.scope)) nativeUnavailable()
    if (scope.scope === "assignment" ? !uuidPattern.test(scope.assignmentId) || typeof scope.executionGeneration !== "string"
      || !/^(0|[1-9][0-9]{0,18})$/.test(scope.executionGeneration) || BigInt(scope.executionGeneration) > 9223372036854775807n
      || typeof scope.bindingJSON !== "string" : scope.assignmentId !== null || scope.executionGeneration !== null || scope.bindingJSON !== null) nativeUnavailable()
    for (const key of ["profileJSON", "modelJSON", "system", "prompt", "packet"]) if (typeof value[key] !== "string" || !value[key].length) nativeUnavailable()
    const profile = closedObject(exactJSON(value.profileJSON, nativeUnavailable), ["projectId", "profileId", "revision"])
    if (profile.projectId !== scope.projectId || !uuidPattern.test(profile.profileId) || !isLosslessNumber(profile.revision)
      || !/^(0|[1-9][0-9]{0,18})$/.test(profile.revision.value) || BigInt(profile.revision.value) > 9223372036854775807n) nativeUnavailable()
    const parsedModel = exactJSON(value.modelJSON, nativeUnavailable)
    const model = closedObject(parsedModel, Object.hasOwn(parsedModel, "variant") ? ["providerID", "id", "variant"] : ["providerID", "id"])
    if ([model.providerID, model.id].some(v => typeof v !== "string" || !v.length || Buffer.byteLength(v) > 256)
      || Object.hasOwn(model, "variant") && (typeof model.variant !== "string" || !model.variant.length || Buffer.byteLength(model.variant) > 128)) nativeUnavailable()
    safeNative({ system: value.system, prompt: value.prompt, packet: value.packet, model })
    if (!Array.isArray(value.nativeMessages) || !value.nativeMessages.length || !Array.isArray(value.pendingNativeMessages)
      || value.nativeMessages.length + value.pendingNativeMessages.length > 4096) nativeUnavailable()
    const seen = new Set<string>()
    for (const item of [...value.nativeMessages, ...value.pendingNativeMessages]) {
      const message = closedObject(item, ["messageId", "rawJSON"])
      if (typeof message.messageId !== "string" || !/^msg_[A-Za-z0-9_-]{1,128}$/.test(message.messageId)
        || seen.has(message.messageId) || typeof message.rawJSON !== "string") nativeUnavailable()
      seen.add(message.messageId)
      // Native JSON may contain native floating metadata. Only its ID/structure is inspected;
      // its ORIGINAL string, not a parsed/reencoded domain number, remains the digest subject.
      const native = JSON.parse(message.rawJSON); safeNative(native)
      if (native.id !== message.messageId || native.type === "compaction" && native.native != null) nativeUnavailable()
    }
    return { ...value, formatVersion: 1 } as NativeSourceFormat1
  }
  private async original(locatorId: string): Promise<NativeSourceFormat1> {
    if (!uuidPattern.test(locatorId)) nativeUnavailable()
    const raw = await this.custody.read("native-source", locatorId)
    if (raw === undefined) nativeUnavailable()
    const value = this.decode(raw)
    if (value.locatorId !== locatorId || contextJSON(value) !== raw) nativeUnavailable()
    return value
  }
  async read(sessionID: string, locatorId: string, expectedDigest: string): Promise<NativeSourceFormat1> {
    const scope = await this.currentScope(sessionID)
    const value = await this.original(locatorId)
    if (!same(value.scope, scope) || hash(contextJSON(value)) !== expectedDigest) nativeUnavailable()
    // Recapture rights after custody I/O; a retained artifact never revives a withdrawn scope.
    if (!same(scope, await this.currentScope(sessionID))) nativeUnavailable()
    return value
  }
  /** Recovery from an EXACT authorized canonical cut, without a unique local-cache pointer.
   * The caller obtains that cut from current scoped service reads, never a model-authored path. */
  async readCut(sessionID: string, cut: Record<string, any>): Promise<NativeSourceFormat1> {
    const scope = await this.currentScope(sessionID)
    if (!uuidPattern.test(cut.retainedLocatorId)) nativeUnavailable()
    const source = await this.original(cut.retainedLocatorId), proof = await this.proof(cut.retainedLocatorId)
    if (!same(source.scope, scope) || !isLosslessNumber(cut.sourceFormatVersion) || cut.sourceFormatVersion.value !== "1"
      || cut.nativeHostId !== scope.hostId || cut.nativeSessionId !== sessionID || !same(exactJSON(source.profileJSON, nativeUnavailable), cut.profile)
      || proof.renderedRequestDigest !== cut.renderedRequestDigest || proof.packetDigest !== cut.packetDigest
      || !same(proof.nativeMessages, cut.nativeMessages)) nativeUnavailable()
    if (!same(scope, await this.currentScope(sessionID))) nativeUnavailable()
    return source
  }
  /** Internal host-observer fact only, NOT a model/RPC source disclosure. Runtime-facts capture
   * may hold the service monitor: immutable proof reads MUST NOT call currentScope/service. */
  async proof(locatorId: string): Promise<NativeSourceProof> {
    const value = await this.original(locatorId)
    return Object.freeze({ locatorId, scope: value.scope, profileJSON: value.profileJSON, modelJSON: value.modelJSON,
      renderedRequestDigest: hash(contextJSON({ system: value.system, prompt: value.prompt })), packetDigest: hash(value.packet),
      nativeMessages: Object.freeze(value.nativeMessages.map(m => Object.freeze({ messageId: m.messageId, digest: hash(m.rawJSON) }))) })
  }
  unchanged(ctx: Plugin.Context, source: NativeSourceFormat1): Effect.Effect<boolean, unknown> {
    const self = this
    return Effect.gen(function* () {
      if (!same(source.scope, yield* attempt(signal => self.currentScope(source.scope.sessionID, signal)))) return false
      const sessionID = source.scope.sessionID as SessionCompaction["sessionID"]
      const current = yield* ctx.session.context({ sessionID })
      if (current.length !== source.nativeMessages.length + source.pendingNativeMessages.length) return false
      const expected = [...source.nativeMessages, ...source.pendingNativeMessages]
      for (let i = 0; i < current.length; i++) {
        const encoded = encodeNative(current[i]); safeNative(encoded)
        if (current[i]!.id !== expected[i]!.messageId || JSON.stringify(encoded) !== expected[i]!.rawJSON) return false
      }
      const entries = (yield* ctx.session.instructions.entry.list({ sessionID })).filter(item => item.key.startsWith("writhelm-"))
        .map(item => ({ key: item.key, value: item.value })).sort((a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0)
      return contextJSON(entries) === source.packet
    })
  }
  /** Actual Effect-native source producer. Every selected model-message ID must resolve in native
   * context; unknown IDs/order/duplicates refuse. No supplied arbitrary native JSON or source path. */
  capture(ctx: Plugin.Context, event: SessionCompaction, profileJSON: string, system: string, basis: string, packet: string):
    Effect.Effect<Readonly<{ artifact: NativeSourceFormat1; digest: string; proof: NativeSourceProof }>, unknown> {
    const self = this
    return Effect.gen(function* () {
      const scope = yield* attempt(signal => self.currentScope(event.sessionID, signal))
      const modelJSON = contextJSON({ providerID: event.model.providerID, id: event.model.id,
        ...(event.model.variant === undefined ? {} : { variant: event.model.variant }) })
      const entries = (yield* ctx.session.instructions.entry.list({ sessionID: event.sessionID }))
        .filter(item => item.key.startsWith("writhelm-")).map(item => ({ key: item.key, value: item.value }))
        .sort((a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0)
      if (!entries.length || contextJSON(entries) !== packet) return yield* Effect.fail(new Error("required native packet rendering changed or unavailable"))
      const ids = event.messages.map(message => message.id)
      if (!ids.length || ids.some(id => typeof id !== "string") || new Set(ids).size !== ids.length) return yield* Effect.fail(new Error("native source coverage is ambiguous"))
      const observations = yield* ctx.session.context({ sessionID: event.sessionID })
      const observed: Array<{ messageId: string; rawJSON: string }> = []
      let next = 0, bytes = 0, cut = -1
      if (observations.length > 4096) return yield* Effect.fail(new Error("native source inventory exceeds bound"))
      for (const native of observations) {
        const encoded = encodeNative(native)
        safeNative(encoded)
        const rawJSON = JSON.stringify(encoded)
        bytes += Buffer.byteLength(rawJSON)
        if (bytes > 1024 * 1024) return yield* Effect.fail(new Error("native source exceeds qualified custody bound"))
        observed.push({ messageId: native.id, rawJSON })
        if (native.id === ids[next]) { next++; cut = observed.length }
      }
      if (next !== ids.length) return yield* Effect.fail(new Error("native source IDs/order unavailable"))
      const selected = observed.slice(0, cut), pendingNativeMessages = observed.slice(cut)
      // The extraction request actually includes every observation named by the cut, not merely
      // caller assertions of coverage. Later native inputs remain separately pending.
      const prompt = contextJSON({ basis, nativeMessages: selected })
      const artifact: NativeSourceFormat1 = { formatVersion: 1, locatorId: randomUUID(), scope, profileJSON, modelJSON,
        system, prompt, packet, nativeMessages: selected, pendingNativeMessages }
      const raw = contextJSON(artifact)
      self.decode(raw)
      if (!same(scope, yield* attempt(signal => self.currentScope(event.sessionID, signal)))) return yield* Effect.fail(new Error("native source scope changed during capture"))
      yield* attempt(() => self.custody.retain("native-source", artifact.locatorId, raw))
      return Object.freeze({ artifact, digest: hash(raw), proof: yield* attempt(() => self.proof(artifact.locatorId)) })
    })
  }
}
