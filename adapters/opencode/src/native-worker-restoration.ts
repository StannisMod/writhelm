// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { setTimeout as wait } from "node:timers/promises"
import { Effect } from "effect"
import type { Plugin } from "@opencode/plugin/effect"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeContextSources } from "./native-context-source.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"
import type { BoundWorkChannel } from "./connected-tools.js"

const parse = (text: string) => exactJSON(text, nativeUnavailable)
const hash = (text: string) => createHash("sha256").update(text, "utf8").digest("hex")
const same = (a: unknown, b: unknown) => contextJSON(a) === contextJSON(b)
const io = <A>(f: (signal: AbortSignal) => Promise<A>) => Effect.tryPromise({ try: f, catch: () => new Error("native worker restoration unavailable") })

/** Recover from the CURRENT canonical checkpoint selector, never a unique local-cache pointer.
 * This restores exact keyed obligations and separately attributed residual material only. It does
 * not start/wake/compact work, retire live history, release claims, or grant execution/review rights. */
export class NativeWorkerRestoration {
  constructor(private readonly registry: NativeCredentialRegistry, private readonly sources: NativeContextSources) {}
  private async body(channel: BoundWorkChannel, parent: any, reference: any, signal: AbortSignal): Promise<string> {
    const raw = contextJSON({ formatVersion: 2, projectId: channel.projectID, operation: "read_work_body",
      subject: { digest: reference.digest, record: parent } })
    for (let retry = 0; retry < 3; retry++) {
      const reply = parse(await channel.send("reads", raw, signal))
      if (reply.kind === "error" && reply.code === "RATE_LIMIT" && retry < 2) { await wait(1100, undefined, { signal }); continue }
      if (reply.kind !== "read" || typeof reply.data?.body !== "string" || hash(reply.data.body) !== reference.digest
        || BigInt(Buffer.byteLength(reply.data.body)) !== BigInt(reference.byteLength.value ?? reference.byteLength)) nativeUnavailable()
      return reply.data.body
    }
    nativeUnavailable()
  }
  async recover(sessionID: string, signal: AbortSignal) {
    const frame = await this.registry.workerContext(sessionID, signal)
    if (frame.checkpointJSON === null || frame.checkpointMemoryJSON === null || frame.checkpointSourceJSON === null
      || frame.checkpointReferenceJSON === null) nativeUnavailable()
    const checkpoint = parse(frame.checkpointJSON), memory = parse(frame.checkpointMemoryJSON), cut = parse(frame.checkpointSourceJSON)
    const ref = parse(frame.checkpointReferenceJSON)
    if (ref.family !== "checkpoints" || ref.digest !== hash(contextJSON(checkpoint)) || !same(ref.key, checkpoint.key)
      || !same(checkpoint.payload.memory, memory.key) || checkpoint.payload.sourceCutId !== cut.key.id
      || memory.payload.sourceCutId !== cut.key.id || memory.payload.gaps.length || !same(parse(frame.memoryJSON).key, memory.key)) nativeUnavailable()
    // A later uncheckpointed memory tip is operational evidence to reconcile, not permission to
    // silently substitute a different summary or discard its unique findings on restoration.
    const source = await this.sources.readCut(sessionID, cut.payload)
    const channel = await this.registry.contextChannel(sessionID, signal)
    const memoryRef = { family: "memory", key: memory.key, digest: hash(contextJSON(memory)) }
    const entries: Array<Record<string, unknown>> = []
    for (const item of memory.payload.entries) {
      const content = await this.body(channel, memoryRef, item.content, signal)
      const exactRequiredFragments: string[] = []
      for (const fragment of item.exactRequiredFragments) exactRequiredFragments.push(await this.body(channel, memoryRef, fragment, signal))
      entries.push({ id: item.id, status: item.status, content, sources: item.sources,
        remainingObligationIds: item.remainingObligationIds, uncertainty: item.uncertainty, exactRequiredFragments })
    }
    const report = await this.body(channel, ref, checkpoint.payload.report, signal)
    const latest = await this.registry.workerContext(sessionID, signal)
    if (latest.checkpointReferenceJSON !== frame.checkpointReferenceJSON || latest.bindingJSON !== frame.bindingJSON
      || latest.planJSON !== frame.planJSON || latest.packet !== frame.packet || latest.memoryJSON !== frame.memoryJSON) nativeUnavailable()
    const residual = contextJSON({ formatVersion: 1, kind: "restored-residual-not-norms", checkpoint: ref,
      originalAssignment: checkpoint.payload.assignment, handoff: checkpoint.payload.handoff,
      tracker: parse(frame.planJSON), entries, pending: checkpoint.payload.pending,
      retainedHistoricalArtifacts: checkpoint.payload.artifacts, report,
      provenance: { locatorId: source.locatorId, packetDigest: cut.payload.packetDigest, nativeMessages: cut.payload.nativeMessages } },256*1024)
    return Object.freeze({ checkpoint: ref, packet: frame.packet, residual, safeContextAccess: frame.safeContextAccess === true })
  }
  install(ctx: Plugin.Context, sessionID: string): Effect.Effect<void, unknown> {
    const self = this
    return Effect.gen(function* () {
      const restored = yield* io(signal => self.recover(sessionID, signal))
      if (!restored.safeContextAccess) return yield* Effect.fail(new Error("restoration requires qualified native safe access"))
      const actualID = sessionID as Parameters<Plugin.Context["session"]["get"]>[0]["sessionID"]
      const entries = parse(restored.packet)
      if (!Array.isArray(entries) || !entries.length || entries.length > 256) return yield* Effect.fail(new Error("mandatory restoration packet unavailable"))
      const old = yield* ctx.session.instructions.entry.list({ sessionID: actualID })
      for (const current of old.filter(i => i.key.startsWith("writhelm-"))) {
        if (!entries.some(i => i.key === current.key && i.value === current.value)) return yield* Effect.fail(new Error("unknown native mandatory instruction cannot be replaced"))
      }
      // Only exact mandatory sources enter native instructions. Residual observations/proposals
      // are returned by recover() for the ordinary context/compaction data channel, never promoted
      // into a privileged system instruction merely because they were retained canonically.
      for (const entry of entries) {
        const current = yield* io(signal => self.registry.workerContext(sessionID, signal))
        if (current.safeContextAccess !== true || current.checkpointReferenceJSON !== contextJSON(restored.checkpoint)
          || current.packet !== restored.packet) return yield* Effect.fail(new Error("restoration authority/basis changed"))
        yield* ctx.session.instructions.entry.put({ sessionID: actualID, key: entry.key, value: entry.value })
      }
    })
  }
}
