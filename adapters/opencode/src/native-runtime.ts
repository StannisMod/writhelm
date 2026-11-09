// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import type { OpenCodeClient } from "@opencode/client"
import type { Plugin } from "@opencode/plugin"
import { NativeRuntimeClient, nativeUnavailable } from "./native-runtime-client.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { createNativeCredentialRegistry } from "./native-host-identity.js"
import { LinuxWorkRequestCustody, registerConnectedWorkTools } from "./connected-tools.js"
import { ProtectedNativeBridge } from "./native-bridge.js"
import { ConnectedNativeAdapter } from "./connected-native.js"
import { listenNativeTransport } from "./native-transport.js"
import { prepareNativePeerProofs } from "./native-bootstrap-secrets.js"
import { NativeContextSources } from "./native-context-source.js"
import { NativePassiveWorkers } from "./native-passive-worker.js"
import { NativeWorkerPreparation } from "./native-worker-preparation.js"
import { LinuxGitWitness } from "./linux-git-witness.js"
import { closedObject } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"
import type { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeInitialAdapter } from "./native-initial.js"
import { NativeInitialStart } from "./native-initial-start.js"
import type { SessionContext,SessionModelRequest,SessionPrompt } from "@opencode/plugin/promise/session"
import { nativeContextAdmission } from "./native-context-reference.js"

export type RuntimeReference=Readonly<{hostId:string;pid:number;registry:NativeCredentialRegistry;
  sources:NativeContextSources;custody:LinuxNativeCustody;deployment:LinuxNativeCustody;controllerSessionID:string;
  bridge:ProtectedNativeBridge;adapter:ConnectedNativeAdapter;initial:NativeInitialAdapter;
  admitContext(sessionID:string,event?:SessionContext):Promise<void>;admitTool(sessionID:string):Promise<void>;admitModel(event:SessionModelRequest):Promise<void>;
  admitPrompt(event:SessionPrompt):Promise<void>}>
const runtimeReferences=new Map<string,RuntimeReference>()
/** In-process reference only, never a canonical authority/cache. Missing/reloaded/other-host
 * references HOLD; consumers must still enter the actual adapter's current admission methods. */
export function nativeRuntimeReference(root:string,hostId:string):RuntimeReference|undefined{
  const value=runtimeReferences.get(root)
  return value?.hostId===hostId&&value.pid===process.pid?value:undefined
}

export type NativeRuntimeConfiguration = Readonly<{
  root: string; nativeOrigin: string; serviceOrigin: string; transportOrigin: string
  storeId: string; projectId: string; controllerId: string; hostId: string
  controllerSessionID: string; assignmentSessionIDs: readonly string[]
  serviceProof: Uint8Array; transportProof: Uint8Array
}>

/** Explicit pinned in-process startup composition. Configuration/proofs come from trusted local
 * bootstrap, never ctx.options/tool/RPC/model fields. Existing protected bindings are prerequisites;
 * this function does not enroll, grant, adopt a changed incarnation, install a working profile or
 * wake any session. Missing packet/source/physical qualification continues to hold execution. */
export async function startProtectedNativeRuntime(ctx: Plugin.Context, native: OpenCodeClient, config: NativeRuntimeConfiguration) {
  if (process.platform !== "linux" || ctx.app.version !== "2.2.5" || config.serviceProof.length !== 32
    || config.transportProof.length !== 32 || !path.isAbsolute(config.root)
    || config.assignmentSessionIDs.length > 256 || new Set(config.assignmentSessionIDs).size !== config.assignmentSessionIDs.length
    || config.assignmentSessionIDs.includes(config.controllerSessionID)||runtimeReferences.has(config.root)) nativeUnavailable()
  // Derive excluded coding locations from actual SDK sessions, not caller-asserted work roots.
  const actual = await Promise.all([config.controllerSessionID, ...config.assignmentSessionIDs].map(async sessionID => {
    const value = await ctx.session.get({ sessionID })
    if (value.id !== sessionID || value.revert) nativeUnavailable()
    return value.location.directory
  }))
  const forbidden = [...new Set([ctx.location.directory, ...actual])]
  const custody = new LinuxNativeCustody(path.join(config.root, "native"), forbidden)
  const requests = new LinuxWorkRequestCustody(path.join(config.root, "requests"), forbidden)
  const service = new NativeRuntimeClient(config.serviceOrigin, Buffer.from(config.serviceProof).toString("base64url"),
    config.storeId, config.projectId, config.controllerId, config.hostId)
  const registry = await createNativeCredentialRegistry(ctx, native, config.nativeOrigin, custody, service, config.serviceOrigin, requests)
  const restored = await registry.restoreAll()
  const proofs = await prepareNativePeerProofs(custody, config)
  proofs.serviceProof.fill(0); proofs.transportProof.fill(0)
  const controller = await registry.inspect(config.controllerSessionID)
  if (!controller || controller.scope !== "controller") nativeUnavailable()
  const bridge = new ProtectedNativeBridge(registry, custody, config.controllerSessionID, config.hostId)
  const sources = new NativeContextSources(registry, custody)
  await Promise.all([...new Set([...restored, ...config.assignmentSessionIDs])].map(sessionID => bridge.manage(sessionID)))
  const adapter = new ConnectedNativeAdapter(ctx, native.session, bridge, config.hostId)
  const deployment=new LinuxNativeCustody(path.join(config.root,"bootstrap"),forbidden);await deployment.initialize()
  const initial=new NativeInitialAdapter(ctx,native,registry,custody,deployment,config.controllerSessionID,sid=>adapter.owns(sid),async event=>{
    const binding=await registry.inspect(event.sessionID);if(!binding)nativeUnavailable()
    await nativeContextAdmission(config.root,config.hostId,binding.workRoot).request(event)
  })
  const initialStart=new NativeInitialStart(registry,initial,custody,deployment,config.controllerSessionID,config.projectId)
  await adapter.register(sid=>adapter.owns(sid)||!initial.managed(sid))
  await initial.register()
  let workers:NativePassiveWorkers|undefined,preparations:NativeWorkerPreparation|undefined
  let transport: Awaited<ReturnType<typeof listenNativeTransport>> | undefined
  let preparationTool:{dispose():Promise<void>}|undefined,tools:{dispose():Promise<void>}|undefined
  try {
  const passiveRaw=await deployment.read("passive-profile","current")
  if(passiveRaw!==undefined){
    const p=closedObject(exactJSON(passiveRaw,nativeUnavailable),["formatVersion","repositoryId","worktreeParent","git","agents"])
    const git=closedObject(p.git,["path","sha256"]),agents=closedObject(p.agents,["executor-v1","reviewer-v1"])
    if(p.formatVersion?.value!=="1"||typeof p.worktreeParent!=="string"||!path.isAbsolute(p.worktreeParent)||
      typeof p.repositoryId!=="string"||typeof git.path!=="string"||typeof git.sha256!=="string"||
      [agents["executor-v1"],agents["reviewer-v1"]].some(s=>typeof s!=="string"||!s.length))nativeUnavailable()
    workers=new NativePassiveWorkers(native,registry,custody,config.controllerSessionID,{sourceRoot:controller.workRoot,
      worktreeParent:p.worktreeParent,nativeProjectId:ctx.location.project.id,repositoryId:p.repositoryId,
      controllerId:config.controllerId,hostId:config.hostId,agents:agents as Record<"executor-v1"|"reviewer-v1",string>},
      new LinuxGitWitness({path:git.path,sha256:git.sha256}))
    preparations=new NativeWorkerPreparation(registry,workers,custody,config.controllerSessionID,
      {projectId:config.projectId,controllerId:config.controllerId,hostId:config.hostId,worktreeParent:p.worktreeParent})
  }
    transport = await listenNativeTransport(config.transportOrigin, config.transportProof, bridge, adapter, sources,workers,initial)
    tools = await registerConnectedWorkTools(ctx, registry)
    preparationTool=await ctx.tool.transform(editor=>{
      editor.add({name:"writhelm_prepare_worker",
      description:"Reserve an exact ready task, create a passive native receiver, enroll its bounded credential and bind. No model wake/start. Input JSON: task key, manifest reference, profile key, resources, reviewSubjects. Retry original preparationId after uncertain reply; never create a new identity to bypass a refusal.",
      input:{type:"object",additionalProperties:false,required:["json"],properties:{json:{type:"string",maxLength:16384},preparationId:{type:"string",maxLength:36}}},
      execute:async(input,actual)=>{
        if(!input||typeof input!=="object"||Object.keys(input).some(k=>!["json","preparationId"].includes(k)))nativeUnavailable()
        const i=input as {json:unknown;preparationId?:unknown}
        if(typeof i.json!=="string"||Buffer.byteLength(i.json)>16384||i.preparationId!==undefined&&typeof i.preparationId!=="string")nativeUnavailable()
        const caller=await registry.resolve(actual)
        if(caller?.scope!=="controller"||actual.sessionID!==config.controllerSessionID)nativeUnavailable()
        await registry.sourceIdentity(actual.sessionID,actual.signal)
        if(!preparations)return {content:contextJSON({phase:"preparation-held",reason:"No passive deployment profile is configured; service/native qualification is not inferred"})}
        return {content:contextJSON(await preparations.run(i.json,actual,i.preparationId as string|undefined))}
      },options:{codemode:false}})
      editor.add({name:"writhelm_start_worker",description:"Request the distinct initial normal prompt for an exact bound assignment, confirm its held native boundary and release only after canonical ACTIVE. Does not assert completion/fencing. JSON: assignment selector and exact binding reference. Retry original startId after uncertainty, never new native IDs.",
        input:{type:"object",additionalProperties:false,required:["json"],properties:{json:{type:"string",maxLength:16384},startId:{type:"string",maxLength:36}}},
        execute:async(input,actual)=>{
          if(!input||typeof input!=="object"||Object.keys(input).some(k=>!["json","startId"].includes(k)))nativeUnavailable()
          const i=input as {json:unknown;startId?:unknown}
          if(typeof i.json!=="string"||Buffer.byteLength(i.json)>16384||i.startId!==undefined&&typeof i.startId!=="string")nativeUnavailable()
          return {content:contextJSON(await initialStart.run(i.json,actual,i.startId as string|undefined))}
        },options:{codemode:false}})
    })
    await ctx.tool.reload()
    const reference:RuntimeReference=Object.freeze({hostId:config.hostId,pid:process.pid,registry,bridge,adapter,initial,sources,custody,deployment,
      controllerSessionID:config.controllerSessionID,
      async admitContext(sessionID:string,event?:SessionContext){
        if(adapter.owns(sessionID)){if(event&&initial.managed(sessionID))await initial.configure(event);await adapter.admitContext(sessionID)}
        else await initial.admitContext(sessionID,event)
      },
      async admitTool(sessionID:string){if(adapter.owns(sessionID))await adapter.admitTool(sessionID);else await initial.admitTool(sessionID)},
      async admitModel(event:SessionModelRequest){if(initial.managed(event.sessionID))await initial.admitModel(event);
        if(adapter.owns(event.sessionID))await adapter.admitTool(event.sessionID);else await initial.admitTool(event.sessionID)},
      async admitPrompt(event:SessionPrompt){if(adapter.owns(event.sessionID))await adapter.admitTool(event.sessionID);else await initial.admitPrompt(event)}
    })
    runtimeReferences.set(config.root,reference)
    return Object.freeze({ registry, bridge, sources, workers, preparations, initial, initialStart, async close() {
      if(runtimeReferences.get(config.root)===reference)runtimeReferences.delete(config.root)
      await transport!.close()
      await preparationTool?.dispose()
      await tools?.dispose()
      await initial.close()
      await adapter.close() // managed sessions keep fail-closed admission hooks
      await bridge.close()
    } })
  } catch (error) {
    if (transport) await transport.close()
    await preparationTool?.dispose()
    await tools?.dispose()
    await initial.close()
    await adapter.close()
    await bridge.close()
    throw error
  }
}
