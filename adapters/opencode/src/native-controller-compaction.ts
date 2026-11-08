// SPDX-License-Identifier: AGPL-3.0-only
import { Effect, Scope } from "effect"
import type { Plugin } from "@opencode/plugin/effect"
import type { SessionCompaction } from "@opencode/plugin/effect/session"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeContextSources } from "./native-context-source.js"
import { NativeContextExtraction, type ContextExtractionProfile } from "./native-context-extraction.js"
import { NativeControllerContinuations } from "./native-controller-continuation.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"

const io = <A>(f: (signal: AbortSignal) => Promise<A>) => Effect.tryPromise({ try: f, catch: error =>
  error instanceof Error ? error : new Error("controller context unavailable") })

/** Native FOREGROUND planner compaction: all own active assignments and canonical task frontier,
 * not the last chat topic, a fake worker assignment or implicit acceptance/integration authority. */
export class NativeControllerCompaction {
  readonly #active = new Set<string>()
  readonly #extraction: NativeContextExtraction
  readonly #continuations: NativeControllerContinuations
  readonly #profileJSON: string
  #registered = false
  constructor(private readonly ctx: Plugin.Context, private readonly registry: NativeCredentialRegistry,
    private readonly sources: NativeContextSources, custody: LinuxNativeCustody, profile: ContextExtractionProfile, governingTaskJSON: string) {
    this.#profileJSON = profile.profileJSON
    this.#extraction = new NativeContextExtraction(ctx, sources, { ...profile, scopeKind: "controller" })
    this.#continuations = new NativeControllerContinuations(registry, sources, custody, profile.profileJSON, governingTaskJSON)
  }
  register(): Effect.Effect<void, never, Scope.Scope> {
    const self = this
    return Effect.gen(function* () {
      if (self.#registered) return yield* Effect.die(new Error("controller compaction already registered"))
      yield* self.#extraction.register()
      yield* self.ctx.session.hook("compaction", event => self.compact(event).pipe(Effect.orDie))
      self.#registered = true
      yield* Effect.addFinalizer(() => Effect.sync(() => { self.#registered = false }))
    })
  }
  private compact(event: SessionCompaction): Effect.Effect<void, unknown> {
    const self = this
    if (self.sources.scopeKind(event.sessionID) !== "controller") return Effect.void
    return Effect.acquireUseRelease(Effect.sync(() => {
      if (!self.#registered || self.#active.has(event.sessionID) || self.#active.size >= 256 || event.result !== undefined) nativeUnavailable()
      self.#active.add(event.sessionID)
    }), () => Effect.gen(function* () {
      const frame = yield* io(signal => self.registry.controllerContext(event.sessionID, self.#profileJSON, signal))
      if (frame.nativeReady !== true) return yield* Effect.fail(new Error("controller native profile is unqualified"))
      self.#extraction.assertProfile(frame.profileRecordJSON)
      const extracted = yield* self.#extraction.extract(event, contextJSON(frame), frame.packet, frame.obligationIds)
      if (extracted.gaps.length) return yield* Effect.fail(new Error("controller residual frontier has known gaps"))
      const attempt = yield* io(() => self.#continuations.prepare(event.sessionID, frame, extracted))
      const confirmed = yield* io(signal => self.#continuations.publish(event.sessionID, attempt, signal))
      if (confirmed.frame.nativeReady !== true || confirmed.frame.frontierJSON !== frame.frontierJSON
        || confirmed.frame.packet !== extracted.source.packet || !(yield* self.sources.unchanged(self.ctx, extracted.source)))
        return yield* Effect.fail(new Error("controller frontier/native source changed before installation"))
      event.result = { summary: contextJSON({ formatVersion: 1, kind: "writhelm-controller-context", continuation: confirmed.reference,
        frontier: exactJSON(confirmed.frame.frontierJSON, nativeUnavailable), residualFactsNotNorms: extracted.entries,
        pending: exactJSON(confirmed.frame.previousJSON, nativeUnavailable).payload.pending,
        pendingNativeMessageIds: extracted.source.pendingNativeMessages.map(m => m.messageId),
        rule: "Exact task/norm/profile sources restore separately; reports/proposals grant no authority" },256*1024),
      metadata: { writhelm: { formatVersion: 1, controllerContinuation: confirmed.reference,
        sourceLocatorId: extracted.source.locatorId, sourceDigest: extracted.sourceDigest, attemptId: attempt } } }
    }), () => Effect.sync(() => { self.#active.delete(event.sessionID) }))
  }
}
