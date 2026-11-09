// SPDX-License-Identifier: AGPL-3.0-only
// Component executable: REAL HTTP/credentials/custody/bridge, explicitly MOCK external native SDK.
// Synthetic fixture keys arrive on stdin only. No host constructor, provider, profile or inference.
import { createInterface } from "node:readline"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import type { SessionApi } from "@opencode/client/promise/api"
import { NativeRuntimeClient, nativeUnavailable } from "../src/native-runtime-client.js"
import { exactJSON } from "../src/strict-json.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { prepareNativePeerProofs } from "../src/native-bootstrap-secrets.js"
import { NativeContextSources } from "../src/native-context-source.js"
import { NativeContextExtraction } from "../src/native-context-extraction.js"
import { NativeWorkerCheckpoints } from "../src/native-worker-checkpoint.js"
import { NativeWorkerRestoration } from "../src/native-worker-restoration.js"
import { registerNativeContextDispatcher } from "../src/native-context-dispatch.js"
import { nativeContextAdmission } from "../src/native-context-reference.js"
import type { RuntimeReference } from "../src/native-runtime.js"
import { NativeControllerRestoration } from "../src/native-controller-restoration.js"
import { contextJSON } from "../src/native-context-codec.js"
import { Effect, Schema, Cause } from "effect"
import { Message } from "@opencode/ai/effect"
import { SessionMessage } from "@opencode/schema/session-message"
import type { Plugin as EffectPlugin } from "@opencode/plugin/effect"
import type { SessionCompaction } from "@opencode/plugin/effect/session"

const lines = createInterface({ input: process.stdin, crlfDelay: Infinity })
const input = lines[Symbol.asyncIterator]()
const first = await input.next()
if (first.done) throw new Error("explicit component configuration required")
const config = JSON.parse(first.value)
const hooks = new Map<string, (event: { sessionID: string }) => Promise<void>>()
const effectsFile = path.join(config.root, "mock-native-effects.json")
let effects: { wakes: number; interrupts: number; payload?: any; instructions: Array<{ key: string; value: string }>; pendingInputs?: any[];
  controllerInstructions?: Array<{ key: string; value: string }> }
try { effects = JSON.parse(await fs.readFile(effectsFile, "utf8")) }
catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error; effects = { wakes: 0, interrupts: 0, instructions: [] } }
const save = () => fs.writeFile(effectsFile, JSON.stringify(effects), { mode: 0o600 })
const ctx = { app: { version: "2.2.5" }, session: {
  get: async ({ sessionID }: { sessionID: string }) => {
    if (![config.controllerSessionID, config.sessionID].includes(sessionID)) throw new Error("MOCK wrong native receiver")
    return { id: sessionID, location: { directory: config.workRoot } }
  },
  hook: async (name: string, callback: (event: { sessionID: string }) => Promise<void>) => {
    hooks.set(name, callback); return { dispose: async () => { hooks.delete(name) } }
  },
  instructions: { entry: {
    list: async ({ sessionID }: { sessionID: string }) => sessionID === config.controllerSessionID ? effects.controllerInstructions ?? [] : effects.instructions,
    put: async ({ sessionID, key, value }: { sessionID: string; key: string; value: string }) => {
      if (sessionID === config.controllerSessionID) effects.controllerInstructions = [...(effects.controllerInstructions ?? []).filter(i => i.key !== key), { key, value }]
      else effects.instructions = [...effects.instructions.filter(i => i.key !== key), { key, value }]
      await save()
    },
  } },
  synthetic: async (value: { sessionID: string; id: string; text: string; metadata: unknown }) => {
    effects.wakes++ // Duplicate original native ID does NOT deduplicate native wake.
    effects.payload = { id: value.id, sessionID: value.sessionID, type: "synthetic", payload: { text: value.text, metadata: value.metadata } }
    await save(); return effects.payload
  },
  interrupt: async () => { effects.interrupts++; await save() }, // NEVER physical fencing evidence
}, tool: { hook: async (_name: string, callback: (event: { sessionID: string }) => Promise<void>) => {
  hooks.set("tool", callback); return { dispose: async () => { hooks.delete("tool") } }
} } } as unknown as Plugin.Context
const native = { inbox: { list: async () => effects.payload ? [effects.payload] : [] },
  message: { get: async () => { if (!effects.payload) throw new Error("MOCK absent"); return effects.payload } },
  context: async () => effects.payload ? [effects.payload] : [] } as unknown as Pick<SessionApi, "inbox" | "message" | "context">
const custody = new LinuxNativeCustody(path.join(config.root, "custody"), [config.workRoot]); await custody.initialize()
const proofs = await prepareNativePeerProofs(custody, { serviceProof: Buffer.from(config.serviceProof, "base64url"),
  transportProof: Buffer.from(config.transportProof, "base64url") })
proofs.serviceProof.fill(0); proofs.transportProof.fill(0)
const service = new NativeRuntimeClient(config.serviceOrigin, config.serviceProof, config.storeId, config.projectId, config.controllerId, config.hostId,
  async (url, init) => {
    const response = await fetch(url, init)
    if (!response.ok) process.stderr.write(`service-refused: ${response.status}\n`)
    return response
  })
const registry = new NativeCredentialRegistry(ctx, custody, service, config.serviceOrigin,
  new LinuxWorkRequestCustody(path.join(config.root, "requests"), [config.workRoot]), "a".repeat(64))
const probe = registry.probe.bind(registry)
registry.probe = async (...args) => {
  try { return await probe(...args) }
  catch (error) { process.stderr.write(`probe: ${error instanceof Error ? error.message : "refused"}\n`); throw error }
}
await registry.adopt(config.controllerPrincipal, config.controllerKey, config.controllerToken)
await registry.adopt(config.workerPrincipal, config.workerKey, config.workerToken)
await registry.bind(config.controllerSessionID, config.controllerKey, config.workRoot)
await registry.bind(config.sessionID, config.workerKey, config.workRoot, config.subject)
const bridge = new ProtectedNativeBridge(registry, custody, config.controllerSessionID, config.hostId)
const sources = new NativeContextSources(registry, custody)
// Private component diagnostics only: do not log token-bearing configuration or packet/source text.
const retainOriginal = bridge.retainOriginal.bind(bridge)
bridge.retainOriginal = async (...args) => {
  try { await retainOriginal(...args) }
  catch (error) { process.stderr.write(`retain-original: ${error instanceof Error ? error.message : "refused"}\n`); throw error }
}
const current = bridge.current.bind(bridge)
bridge.current = async envelope => { const value = await current(envelope); process.stderr.write(`current: ${value}\n`); return value }
await bridge.manage(config.sessionID)
const adapter = new ConnectedNativeAdapter(ctx, native, bridge, config.hostId); await adapter.register()
const deployment=new LinuxNativeCustody(path.join(config.root,"deployment"),[config.workRoot]);await deployment.initialize()
if(config.profileJSON)await deployment.retain("execution-profile",config.profileJSON,contextJSON({formatVersion:1,profileKeyJSON:config.profileJSON,
  model:{providerID:"synthetic",id:"no-call"},baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{maxTokens:64,effort:"bounded"}}))
if(config.governingTaskJSON)await deployment.retain("controller-context","current",contextJSON({formatVersion:1,profileKeyJSON:config.profileJSON,
  governingTaskJSON:config.governingTaskJSON}))
const runtime={hostId:config.hostId,pid:process.pid,registry,sources,custody,deployment,controllerSessionID:config.controllerSessionID,
  checkContextPolicy:async()=>{}, // Explicit MOCK profile qualification; not an effective native config probe.
  admitTool:(id:string)=>adapter.admitTool(id)} as unknown as RuntimeReference
const dispatchConfig={root:config.root,hostId:config.hostId,workRoot:config.workRoot}
function scopedHooks(){
  const callbacks=new Map<string,Array<(event:any)=>Effect.Effect<void,unknown>>>()
  return {callbacks,hook:(name:string,callback:(event:any)=>Effect.Effect<void,unknown>)=>Effect.acquireRelease(
    Effect.sync(()=>{callbacks.set(name,[...(callbacks.get(name)??[]),callback]);return {dispose:Effect.void}}),
    ()=>Effect.sync(()=>{callbacks.set(name,(callbacks.get(name)??[]).filter(c=>c!==callback))})),
    invoke:(name:string,event:any)=>Effect.gen(function*(){for(const callback of [...(callbacks.get(name)??[])])yield* callback(event)})}
}
const transport = await listenNativeTransport(config.transportOrigin, Buffer.from(config.transportProof, "base64url"), bridge, adapter, sources)
const emit = (value: unknown) => process.stdout.write(JSON.stringify(value) + "\n")
emit({ ready: true, externalNative: "MOCK" })
try {
  for (;;) {
    const next = await input.next(); if (next.done) break
    const command = JSON.parse(next.value)
    if (command.operation === "finish") break
    if (command.operation === "controller-restore") {
      const restored = await new NativeControllerRestoration(registry,sources,config.profileJSON)
        .recover(config.controllerSessionID,new AbortController().signal)
      emit({ continuation: restored.reference,kind: exactJSON(restored.residual,nativeUnavailable).kind,wakes: effects.wakes })
      continue
    }
    if (command.operation === "controller-compact") {
      const frame = await registry.controllerContext(config.controllerSessionID, config.profileJSON)
      effects.controllerInstructions = exactJSON(frame.packet, nativeUnavailable); await save()
      const decode = Schema.decodeSync(SessionMessage.Info as unknown as Schema.Codec<unknown, unknown>)
      const controllerSource = decode({ id: "msg_controller_frontier", type: "user", text: "MOCK retain all controller tasks, dependencies and decisions",
        time: { created: 1 }, metadata: { sourceRole: "controller-not-executor" } })
      const controllerHooks=scopedHooks()
      const event = { sessionID: config.controllerSessionID, model: { providerID: "synthetic", id: "no-call" },
        messages: [{ id: "msg_controller_frontier" }] } as unknown as SessionCompaction
      const effectContext = { app: { version: "2.2.5" }, session: {
        get:({sessionID}:{sessionID:string})=>Effect.succeed({id:sessionID,location:{directory:config.workRoot}}),
        context: () => Effect.succeed([controllerSource]), instructions: { entry: { list: () => Effect.succeed(effects.controllerInstructions) } },
        hook:controllerHooks.hook,
        generate: ({ sessionID, prompt }: { sessionID: string; prompt: string }) => Effect.gen(function* () {
          const request = { sessionID, model: event.model, system: [], messages: [Message.user(prompt)], options: {}, tools: {} }
          yield* controllerHooks.invoke("generate",request)
          // Controller fixture exercises Effect-first ordering; worker below exercises Promise-first.
          yield* Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).generate(request as never))
          const modelRequest={ sessionID, model: event.model, kind: "generate", headers: {} }
          yield* controllerHooks.invoke("model.request",modelRequest)
          yield* Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).request(modelRequest as never))
          return { text: JSON.stringify({ formatVersion: 1, entries: [{ status: "proposed", text: "MOCK planner proposal remains unaccepted",
            sourceMessageIds: ["msg_controller_frontier"], obligationIds: frame.obligationIds, uncertainty: "MOCK semantic extraction",
            exactFragments: [] }], gaps: [], pending: ["A controller decision remains open; no authority is granted by this proposal"] }) }
        }),
      }, tool: { hook:(name:string,callback:any)=>controllerHooks.hook("tool:"+name,callback) } } as unknown as EffectPlugin.Context
      const outcome = await Effect.runPromise(Effect.scoped(Effect.gen(function* () {
        yield* registerNativeContextDispatcher(effectContext,dispatchConfig,()=>runtime)
        yield* Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).compaction(event))
        const result=yield* controllerHooks.invoke("compaction",event).pipe(Effect.exit)
        const extra=yield* Effect.exit(Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).generate(event)))
        if(extra._tag!=="Failure")throw new Error("controller extraction lease survived completion")
        return result
      })))
      if (outcome._tag === "Failure") process.stderr.write(`controller-compaction-refused: ${Cause.pretty(outcome.cause)}\n`)
      const after = await registry.controllerContext(config.controllerSessionID, config.profileJSON)
      emit({ continuation: after.previousReferenceJSON === null ? null : exactJSON(after.previousReferenceJSON, nativeUnavailable),
        nativeResultSet: event.result !== undefined, failed: outcome._tag === "Failure", frontier: exactJSON(after.frontierJSON, nativeUnavailable) })
      continue
    }
    if (["capture-source", "checkpoint-source", "compact-source", "compact-late", "restore-source"].includes(command.operation)) {
      if (!effects.payload) throw new Error("explicit MOCK native message required")
      const decode = Schema.decodeSync(SessionMessage.Info as unknown as Schema.Codec<unknown, unknown>)
      const native = decode({ id: effects.payload.id, type: "synthetic", text: effects.payload.payload.text,
        metadata: effects.payload.payload.metadata, time: { created: 1 } })
      const contextObservations = [native, ...(effects.pendingInputs ?? []).map(value => decode(value))]
      const workerHooks=scopedHooks()
      let modelCalls = 0
      const effectContext = { app: { version: "2.2.5" }, session: {
        get:({sessionID}:{sessionID:string})=>Effect.succeed({id:sessionID,location:{directory:config.workRoot}}),
        context: () => Effect.succeed(contextObservations), instructions: { entry: {
        list: () => Effect.succeed(effects.instructions),
        put: ({ key, value }: { key: string; value: string }) => Effect.promise(async () => {
          effects.instructions = [...effects.instructions.filter(i => i.key !== key), { key, value }]; await save()
        }),
      } }, hook:workerHooks.hook,
      generate: ({ sessionID, prompt }: { sessionID: string; prompt: string }) => Effect.gen(function* () {
        const request = { sessionID, model: { providerID: "synthetic", id: "no-call" }, messages: [Message.user(prompt)], system: [], tools: {}, options: {} }
        if(command.operation!=="checkpoint-source")yield* Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).generate(request as never))
        yield* workerHooks.invoke("generate",request)
        const modelRequest={ sessionID, model: request.model, kind: "generate", headers: {} }
        if(command.operation!=="checkpoint-source")yield* Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).request(modelRequest as never))
        yield* workerHooks.invoke("model.request",modelRequest)
        modelCalls++
        if (command.operation === "compact-late") {
          const late = { id: `msg_after_context_${effects.pendingInputs?.length ?? 0}`, type: "user", text: "Unconsumed late native input",
            time: { created: 2 }, metadata: { originalTask: config.subject.assignmentId } }
          effects.pendingInputs = [...(effects.pendingInputs ?? []), late]; contextObservations.push(decode(late))
          yield* Effect.promise(save)
        }
        return { text: JSON.stringify({ formatVersion: 1, entries: [{ status: "reported", text: "Synthetic fixture progress only, not verification or a norm",
          sourceMessageIds: [effects.payload.id], obligationIds: checkpointFrame!.obligationIds, uncertainty: "MOCK native source/provider",
          exactFragments: [] }], gaps: [], pending: [] }) }
      }),
      }, tool: { hook:(name:string,callback:any)=>workerHooks.hook("tool:"+name,callback) } } as unknown as EffectPlugin.Context
      const event = { sessionID: config.sessionID, model: { providerID: "synthetic", id: "no-call" },
        messages: [{ id: effects.payload.id }] } as unknown as SessionCompaction
      const packet = contextJSON([...effects.instructions].sort((a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0))
      if (command.operation === "restore-source") {
        const restoration = new NativeWorkerRestoration(registry, sources)
        const restored = await restoration.recover(config.sessionID, new AbortController().signal)
        await Effect.runPromise(restoration.install(effectContext, config.sessionID))
        emit({ checkpoint: restored.checkpoint, residualKind: exactJSON(restored.residual, nativeUnavailable).kind,
          wakes: effects.wakes, lateInputs: effects.pendingInputs?.length ?? 0 })
        continue
      }
      let checkpointFrame: Record<string, any> | undefined
      if (["checkpoint-source", "compact-source", "compact-late"].includes(command.operation)) {
        checkpointFrame = await registry.workerContext(config.sessionID)
        const profile = { profileJSON: config.profileJSON, model: event.model, baseURL: null, routeID: "no-call",
          effortOptionKey: "effort", nativeOptions: { maxTokens: 64, effort: "bounded" } }
        if (command.operation !== "checkpoint-source") {
          let retiredAdmissionHeld = false, retiredToolHeld = false
          const outcome = await Effect.runPromise(Effect.scoped(Effect.gen(function* () {
            yield* registerNativeContextDispatcher(effectContext,dispatchConfig,()=>runtime)
            yield* Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).compaction(event))
            const result = yield* workerHooks.invoke("compaction",event).pipe(Effect.exit)
            const extra=yield* Effect.exit(Effect.promise(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot).generate(event)))
            if(extra._tag!=="Failure")throw new Error("worker extraction lease survived completion")
            if ((event.result?.metadata?.writhelm as any)?.finishedHandoff === true) {
              retiredAdmissionHeld = (yield* workerHooks.invoke("context",{ sessionID: config.sessionID }).pipe(Effect.exit))._tag === "Failure"
              retiredToolHeld = (yield* workerHooks.invoke("tool:execute.before",{ sessionID: config.sessionID }).pipe(Effect.exit))._tag === "Failure"
            }
            return result
          })))
          if (outcome._tag === "Failure") process.stderr.write(`compaction-refused: ${Cause.pretty(outcome.cause)}\n`)
          const current = await registry.workerContext(config.sessionID), cp = exactJSON(current.checkpointJSON, nativeUnavailable)
          emit({ checkpoint: { family: "checkpoints", key: cp.key, digest: createHash("sha256").update(contextJSON(cp)).digest("hex") },
            nativeResultSet: event.result !== undefined, failed: outcome._tag === "Failure", lateInputs: effects.pendingInputs?.length ?? 0,
            modelCalls, retiredAdmissionHeld, retiredToolHeld })
          continue
        }
        const extraction = new NativeContextExtraction(effectContext, sources, { profileJSON: config.profileJSON,
          model: event.model, baseURL: null, routeID: "no-call", effortOptionKey: "effort", nativeOptions: { maxTokens: 64, effort: "bounded" } })
        extraction.assertProfile(checkpointFrame.profileRecordJSON)
        const extracted = await Effect.runPromise(Effect.scoped(Effect.gen(function* () {
          yield* extraction.register()
          return yield* extraction.extract(event, contextJSON(checkpointFrame), packet, checkpointFrame!.obligationIds)
        })))
        const checkpoints = new NativeWorkerCheckpoints(registry, sources, custody)
        const attempt = await checkpoints.prepare(config.sessionID, extracted)
        const confirmed = await checkpoints.publish(config.sessionID, attempt, new AbortController().signal)
        const replayed = await checkpoints.publish(config.sessionID, attempt, new AbortController().signal)
        if (contextJSON(confirmed.checkpoint) !== contextJSON(replayed.checkpoint)) throw new Error("checkpoint replay changed identity")
        emit({ checkpoint: confirmed.checkpoint, attempt, nativeResultStillUnset: event.result === undefined })
        continue
      }
      const result = await Effect.runPromise(sources.capture(effectContext, event, config.profileJSON,
        "Extract sourced residual facts, not new norms", "MOCK complete remaining task tracker", packet))
      emit({ locatorId: result.artifact.locatorId, digest: result.digest, proof: result.proof })
      continue
    }
    if (command.operation !== "probe") throw new Error("unknown component operation")
    let admitted = false
    try { await hooks.get("tool")!({ sessionID: config.sessionID }); admitted = true } catch { /* held */ }
    emit({ wakes: effects.wakes, interrupts: effects.interrupts, admitted, instructions: effects.instructions.length })
  }
} finally { await transport.close(); await adapter.close(); await bridge.close(); lines.close(); process.stdin.pause() }
