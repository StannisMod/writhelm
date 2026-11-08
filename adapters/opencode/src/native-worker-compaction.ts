// SPDX-License-Identifier: AGPL-3.0-only
import { Effect, Scope } from "effect"
import type { Plugin } from "@opencode/plugin/effect"
import type { SessionCompaction } from "@opencode/plugin/effect/session"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeContextSources } from "./native-context-source.js"
import { NativeContextExtraction, type ContextExtractionProfile } from "./native-context-extraction.js"
import { NativeWorkerCheckpoints } from "./native-worker-checkpoint.js"
import { NativeWorkerRestoration } from "./native-worker-restoration.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"

const io = <A>(f: (signal: AbortSignal) => Promise<A>) => Effect.tryPromise({ try: f, catch: error =>
  error instanceof Error ? error : new Error("native checkpoint operation unavailable") })

/** Native foreground UNFINISHED-worker protocol. No second scheduler/counter or generic summary
 * fallback. Controller-wide and finished-executor retirement are explicitly unsupported here,
 * not silently treated as an unfinished single task. The outer orchestrator selects that profile.
 * Installed native event ordering/accounting/physical recovery still need their held qualification. */
export class NativeWorkerCompaction {
  readonly #active = new Set<string>()
  readonly #retired = new Set<string>()
  readonly #extraction: NativeContextExtraction
  readonly #checkpoints: NativeWorkerCheckpoints
  #registered = false
  constructor(private readonly ctx: Plugin.Context, private readonly registry: NativeCredentialRegistry,
    private readonly sources: NativeContextSources, private readonly custody: LinuxNativeCustody, profile: ContextExtractionProfile) {
    this.#extraction = new NativeContextExtraction(ctx, sources, { ...profile, scopeKind: "assignment" })
    this.#checkpoints = new NativeWorkerCheckpoints(registry, sources, custody)
  }
  register(): Effect.Effect<void, never, Scope.Scope> {
    const self = this
    return Effect.gen(function* () {
      if (self.#registered) return yield* Effect.die(new Error("worker compaction already registered"))
      yield* self.#extraction.register()
      yield* self.ctx.session.hook("context", event => Effect.gen(function* () {
        if (yield* self.retired(event.sessionID)) return yield* Effect.die(new Error("finished executor context retired; a new bounded task is required"))
      }).pipe(Effect.orDie))
      yield* self.ctx.tool.hook("execute.before", event => Effect.gen(function* () {
        if (yield* self.retired(event.sessionID)) return yield* Effect.die(new Error("finished executor tool admission held"))
      }).pipe(Effect.orDie))
      yield* self.ctx.session.hook("compaction", event => self.compact(event).pipe(Effect.orDie))
      self.#registered = true
      yield* Effect.addFinalizer(() => Effect.sync(() => { self.#registered = false }))
    })
  }
  private retired(sessionID: string): Effect.Effect<boolean, unknown> {
    const self = this
    return Effect.gen(function* () {
      if (self.registry.scopeKind(sessionID) !== "assignment") return false
      if (self.#retired.has(sessionID)) return true
      const messages = yield* self.ctx.session.context({ sessionID: sessionID as SessionCompaction["sessionID"] })
      const markers = messages.filter(message => message.type === "compaction").map(message => message.metadata?.writhelm)
      if (!markers.length) return false
      const frame = yield* io(signal => self.registry.workerContext(sessionID, signal))
      if (frame.finishedHandoff !== true || frame.checkpointReferenceJSON === null) return false
      return markers.some(m => m !== null && typeof m === "object" && (m as any).finishedHandoff === true
        && contextJSON((m as any).checkpoint) === frame.checkpointReferenceJSON)
    })
  }
  private compact(event: SessionCompaction): Effect.Effect<void, unknown> {
    const self = this
    if (self.sources.scopeKind(event.sessionID) !== "assignment") return Effect.void
    return Effect.acquireUseRelease(Effect.sync(() => {
      if (!self.#registered || self.#active.has(event.sessionID) || self.#active.size >= 256 || event.result !== undefined) nativeUnavailable()
      self.#active.add(event.sessionID)
    }), () => Effect.gen(function* () {
      const frame = yield* io(signal => self.registry.workerContext(event.sessionID, signal))
      if (frame.nativeReady !== true) return yield* Effect.fail(new Error("native profile/safe access not qualified for compaction"))
      self.#extraction.assertProfile(frame.profileRecordJSON)
      if (frame.finishedHandoff === true) {
        const restored = yield* io(signal => new NativeWorkerRestoration(self.registry,self.sources).recover(event.sessionID,signal))
        const cut = exactJSON(frame.checkpointSourceJSON,nativeUnavailable).payload
        const source = yield* io(() => self.sources.readCut(event.sessionID,cut))
        if (!restored.safeContextAccess || source.pendingNativeMessages.length || !(yield* self.sources.unchanged(self.ctx,source)))
          return yield* Effect.fail(new Error("finished handoff has new native inputs or unsafe effects"))
        const cp = exactJSON(frame.checkpointJSON,nativeUnavailable).payload
        event.result = { summary: contextJSON({ formatVersion: 1,kind: "writhelm-finished-executor",checkpoint: restored.checkpoint,
          result: cp.result,retrospective: cp.retrospective,notification: cp.notification,
          rule: "Complete durable handoff; acceptance/integration may still be pending. Retirement does not stop writers or release claims." }),
        metadata: { writhelm: { formatVersion: 1,finishedHandoff: true,checkpoint: restored.checkpoint } } }
        self.#retired.add(event.sessionID)
        return // no extraction/model call, no wait for downstream acceptance, no resource release
      }
      if (frame.checkpointJSON !== null && exactJSON(frame.checkpointJSON, nativeUnavailable).payload.handoff === "finished")
        return yield* Effect.fail(new Error("finished handoff requires its own retirement protocol"))
      const extracted = yield* self.#extraction.extract(event, contextJSON(frame), frame.packet, frame.obligationIds)
      if (extracted.gaps.length) return yield* Effect.fail(new Error("extraction reports remaining knowledge gaps"))
      const attempt = yield* io(() => self.#checkpoints.prepare(event.sessionID, extracted))
      const confirmed = yield* io(signal => self.#checkpoints.publish(event.sessionID, attempt, signal))
      if (confirmed.frame.nativeReady !== true || confirmed.frame.packet !== extracted.source.packet
        || !(yield* self.sources.unchanged(self.ctx, extracted.source)))
        return yield* Effect.fail(new Error("native source/packet/frontier changed after checkpoint confirmation"))
      // Never paraphrase exact task/norm/profile/base. They remain the separately validated keyed
      // native instructions. Residual statements retain status and original source attribution.
      const summary = contextJSON({ formatVersion: 1, kind: "writhelm-unfinished-context", checkpoint: confirmed.checkpoint,
        task: exactJSON(confirmed.frame.taskJSON, nativeUnavailable), tracker: exactJSON(confirmed.frame.planJSON, nativeUnavailable),
        residualFactsNotNorms: extracted.entries, pending: exactJSON(confirmed.frame.checkpointJSON, nativeUnavailable).payload.pending,
        pendingNativeMessageIds: extracted.source.pendingNativeMessages.map(m => m.messageId),
        rule: "Exact mandatory obligations restore separately; observations/proposals are not accepted norms" },256*1024)
      event.result = { summary, metadata: { writhelm: { formatVersion: 1, checkpoint: confirmed.checkpoint,
        sourceLocatorId: extracted.source.locatorId, sourceDigest: extracted.sourceDigest, attemptId: attempt } } }
      // The hook returning does not prove installation/acknowledgement. Native stored metadata must
      // be observed on reopen; never clear unknown effects/claims or mark a source installed here.
    }), () => Effect.sync(() => { self.#active.delete(event.sessionID) }))
  }
}
