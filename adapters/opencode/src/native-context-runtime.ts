// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { Effect, Scope } from "effect"
import { Plugin } from "@opencode/plugin/effect"
import type { OpenCodeClient } from "@opencode/client"
import { NativeRuntimeClient, nativeUnavailable } from "./native-runtime-client.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "./connected-tools.js"
import { createNativeCredentialRegistry } from "./native-host-identity.js"
import { NativeContextSources } from "./native-context-source.js"
import type { ContextExtractionProfile } from "./native-context-extraction.js"
import { NativeWorkerCompaction } from "./native-worker-compaction.js"
import { NativeControllerCompaction } from "./native-controller-compaction.js"
import { NativeWorkerRestoration } from "./native-worker-restoration.js"
import { NativeControllerRestoration } from "./native-controller-restoration.js"

export type NativeContextConfiguration = Readonly<{
  root: string; nativeOrigin: string; serviceOrigin: string; serviceProof: Uint8Array
  storeId: string; projectId: string; controllerId: string; hostId: string
  controllerSessionID: string; assignmentSessionIDs: readonly string[]
  workerProfile?: ContextExtractionProfile
  controllerProfile?: Readonly<{ extraction: ContextExtractionProfile; governingTaskJSON: string }>
}>
const io = <A>(f: () => Promise<A>) => Effect.tryPromise({ try: f, catch: () => new Error("protected native context bootstrap unavailable") })

/** Actual Effect-native plugin composition using an explicitly authenticated pinned native client.
 * Promise APIs here are READ-ONLY session/host bootstrap, never nested generate/runPromise scopes.
 * Profiles/configuration come from trusted protected bootstrap, NOT ctx.options/model arguments.
 * This registers no second scheduler and launches no host/session/provider or worker. */
export function registerNativeTaskContext(ctx: Plugin.Context, native: OpenCodeClient, config: NativeContextConfiguration):
  Effect.Effect<Readonly<{ worker: NativeWorkerRestoration; controller?: NativeControllerRestoration }>, unknown, Scope.Scope> {
  return Effect.gen(function* () {
    if (process.platform !== "linux" || ctx.app.version !== "2.2.5" || !path.isAbsolute(config.root)
      || config.serviceProof.length !== 32 || config.assignmentSessionIDs.length > 256
      || !config.workerProfile && !config.controllerProfile) return yield* Effect.fail(new Error("unqualified native context profile"))
    const roots = yield* io(() => Promise.all([config.controllerSessionID, ...config.assignmentSessionIDs].map(async sessionID => {
      const info = await native.session.get({ sessionID })
      if (info.id !== sessionID || info.revert) nativeUnavailable()
      return info.location.directory
    })))
    const forbidden = [...new Set([ctx.location.directory, ...roots])]
    const custody = new LinuxNativeCustody(path.join(config.root,"native"),forbidden)
    const requests = new LinuxWorkRequestCustody(path.join(config.root,"requests"),forbidden)
    const service = new NativeRuntimeClient(config.serviceOrigin,Buffer.from(config.serviceProof).toString("base64url"),
      config.storeId,config.projectId,config.controllerId,config.hostId)
    // The same SDK API implementation supplies actual host PID/executable and session location.
    // No locally invented host interface or model-selected session label becomes authority.
    const registry = yield* io(() => createNativeCredentialRegistry({ app: ctx.app,session: native.session },native,
      config.nativeOrigin,custody,service,config.serviceOrigin,requests))
    yield* io(() => registry.restoreAll())
    const controller = yield* io(() => registry.inspect(config.controllerSessionID))
    if (!controller || controller.scope !== "controller") return yield* Effect.fail(new Error("controller binding unavailable"))
    for (const id of config.assignmentSessionIDs) {
      const bound = yield* io(() => registry.inspect(id))
      if (!bound || bound.scope !== "assignment") return yield* Effect.fail(new Error("assignment binding unavailable"))
    }
    const sources = new NativeContextSources(registry,custody)
    yield* ctx.session.hook("compaction",event => Effect.sync(() => {
      const kind=registry.scopeKind(event.sessionID)
      if (kind === "assignment" && !config.workerProfile || kind === "controller" && !config.controllerProfile)
        throw new Error("managed context profile unavailable; generic compaction forbidden")
    }))
    if (config.workerProfile) yield* new NativeWorkerCompaction(ctx,registry,sources,custody,config.workerProfile).register()
    if (config.controllerProfile) yield* new NativeControllerCompaction(ctx,registry,sources,custody,
      config.controllerProfile.extraction,config.controllerProfile.governingTaskJSON).register()
    return Object.freeze({ worker: new NativeWorkerRestoration(registry,sources),
      ...(config.controllerProfile ? { controller: new NativeControllerRestoration(registry,sources,config.controllerProfile.extraction.profileJSON) } : {}) })
  })
}

/** Explicit uninstalled server entrypoint builder. It is not enabled by package defaults and does
 * not fetch secrets, select a model/route, discover/start a server or install a working profile. */
export function nativeTaskContextPlugin(native: OpenCodeClient, config: NativeContextConfiguration) {
  return Plugin.define({ id: "writhelm.native-context", effect: ctx => registerNativeTaskContext(ctx,native,config).pipe(Effect.asVoid,Effect.orDie) })
}
