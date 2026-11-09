// SPDX-License-Identifier: AGPL-3.0-only
import { Effect, Scope } from "effect"
import type { Plugin } from "@opencode/plugin/effect"
import { NativeWorkerCompaction,nativeWorkerRetired } from "./native-worker-compaction.js"
import { NativeControllerCompaction } from "./native-controller-compaction.js"
import { nativeRuntimeReference, type RuntimeReference } from "./native-runtime.js"
import { retainNativeContextAdmission, type NativeContextAdmission } from "./native-context-reference.js"
import type { ContextExtractionProfile } from "./native-context-extraction.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable } from "./native-runtime-client.js"

const io=<A>(f:(signal:AbortSignal)=>Promise<A>)=>Effect.tryPromise({try:f,catch:error=>error instanceof Error?error:new Error("native task-context unavailable")})
const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
type Coordinator=NativeWorkerCompaction|NativeControllerCompaction
type Route={runtime:RuntimeReference;coordinator:Coordinator;profileJSON:string;ready:boolean}
export type NativeContextDispatchConfiguration=Readonly<{root:string;hostId:string;workRoot:string}>

function extraction(raw:string,profileJSON:string):ContextExtractionProfile {
  const d=closedObject(parse(raw),["formatVersion","profileKeyJSON","model","baseURL","routeID","effortOptionKey","nativeOptions"])
  if(d.formatVersion?.value!=="1"||d.profileKeyJSON!==profileJSON||typeof d.routeID!=="string"||typeof d.effortOptionKey!=="string")nativeUnavailable()
  const m=closedObject(d.model,Object.hasOwn(d.model,"variant")?["providerID","id","variant"]:["providerID","id"])
  if(typeof m.providerID!=="string"||typeof m.id!=="string"||m.variant!==undefined&&typeof m.variant!=="string"||
    !d.nativeOptions||typeof d.nativeOptions!=="object"||Array.isArray(d.nativeOptions))nativeUnavailable()
  const nativeOptions:Record<string,string|boolean|number>={}
  for(const [key,value]of Object.entries(d.nativeOptions)){
    if(typeof value==="string"||typeof value==="boolean")nativeOptions[key]=value
    else {
      const number=(value as any)?.value
      if(typeof number!=="string"||!/^(0|[1-9][0-9]*)$/.test(number)||BigInt(number)>2147483647n)nativeUnavailable()
      nativeOptions[key]=Number(number)
    }
  }
  return {profileJSON,model:{providerID:m.providerID,id:m.id,...(m.variant===undefined?{}:{variant:m.variant})},
    baseURL:d.baseURL,routeID:d.routeID,effortOptionKey:d.effortOptionKey,nativeOptions}
}

/** Foreground native dispatcher. Lazy registrations belong to the plugin's lifetime Scope, not
 * the compaction request. Only native scheduling invokes compact; no timer/counter/model loop.
 * Promise hooks find its admission reference but never run this Effect or generate themselves. */
export function registerNativeContextDispatcher(ctx:Plugin.Context,config:NativeContextDispatchConfiguration,
  lookup:()=>RuntimeReference|undefined=()=>nativeRuntimeReference(config.root,config.hostId)):
  Effect.Effect<void,never,Scope.Scope> {
  return Effect.gen(function*(){
    if(process.platform!=="linux"||ctx.app.version!=="2.2.5")nativeUnavailable()
    const scope=yield* Scope.Scope
    const routes=new Map<string,Route>()
    let closed=false
    const runtime=()=>{
      const current=lookup()
      if(closed||!current||current.hostId!==config.hostId||current.pid!==process.pid)nativeUnavailable()
      return current
    }
    const authority=async(sessionID:string,unfinished=false,profileJSON?:string)=>{
      const current=runtime(),binding=await current.registry.inspect(sessionID)
      if(!binding||binding.hostId!==config.hostId||binding.workRoot!==config.workRoot)nativeUnavailable()
      const identity=await current.registry.sourceIdentity(sessionID)
      if(identity.scope!==binding.scope)nativeUnavailable()
      if(binding.scope==="controller"){
        if(sessionID!==current.controllerSessionID)nativeUnavailable()
        if(profileJSON!==undefined){
          const frame=await current.registry.controllerContext(sessionID,profileJSON)
          if(frame.nativeReady!==true)nativeUnavailable()
          await current.checkContextPolicy(sessionID,frame.profileRecordJSON)
        }
      }else {
        const frame=await current.registry.workerContext(sessionID)
        if(frame.nativeReady!==true)nativeUnavailable()
        if(profileJSON!==undefined&&contextJSON(parse(frame.profileRecordJSON).key)!==profileJSON)nativeUnavailable()
        await current.checkContextPolicy(sessionID,frame.profileRecordJSON)
        if(unfinished||frame.finishedHandoff!==true)await current.admitTool(sessionID)
      }
      if(runtime()!==current)nativeUnavailable()
      return current
    }
    const route=(sessionID:string)=>{
      const entry=routes.get(sessionID)
      if(!entry?.ready||entry.runtime!==runtime())nativeUnavailable()
      return entry
    }
    const admission:NativeContextAdmission=Object.freeze({
      async compaction(event){await authority(event.sessionID,false,routes.get(event.sessionID)?.profileJSON)},
      async generate(event){
        const entry=route(event.sessionID);entry.coordinator.assertGenerate(event)
        await authority(event.sessionID,true,entry.profileJSON)
        if(route(event.sessionID)!==entry)nativeUnavailable()
        entry.coordinator.assertGenerate(event)
      },
      async request(event){
        const entry=route(event.sessionID);entry.coordinator.assertRequest(event)
        await authority(event.sessionID,true,entry.profileJSON)
        if(route(event.sessionID)!==entry)nativeUnavailable()
        entry.coordinator.assertRequest(event)
      }
    })
    const ensure=(sessionID:string):Effect.Effect<Route,unknown>=>Effect.gen(function*(){
      const previous=routes.get(sessionID)
      const current=yield* io(()=>authority(sessionID,false,previous?.profileJSON))
      if(previous){if(!previous.ready||previous.runtime!==current)nativeUnavailable();return previous}
      if(routes.size>=256)nativeUnavailable()
      const binding=yield* io(()=>current.registry.inspect(sessionID));if(!binding)nativeUnavailable()
      let profileJSON:string,governingTaskJSON:string|undefined
      if(binding.scope==="controller"){
        const raw=yield* io(()=>current.deployment.read("controller-context","current"))
        if(raw===undefined)throw new Error("qualified controller context deployment unavailable")
        const descriptor=closedObject(parse(raw),["formatVersion","profileKeyJSON","governingTaskJSON"])
        if(descriptor.formatVersion?.value!=="1"||typeof descriptor.profileKeyJSON!=="string"||typeof descriptor.governingTaskJSON!=="string")nativeUnavailable()
        profileJSON=descriptor.profileKeyJSON;governingTaskJSON=descriptor.governingTaskJSON
      }else {
        const frame=yield* io(signal=>current.registry.workerContext(sessionID,signal))
        profileJSON=contextJSON(parse(frame.profileRecordJSON).key)
      }
      const raw=yield* io(()=>current.deployment.read("execution-profile",profileJSON))
      if(raw===undefined)throw new Error("qualified extraction deployment unavailable")
      const profile=extraction(raw,profileJSON)
      // Recheck after asynchronous profile reads, before publishing a route or registering hooks.
      yield* io(()=>authority(sessionID,false,profileJSON))
      const concurrent=routes.get(sessionID)
      if(concurrent){if(!concurrent.ready||concurrent.runtime!==current)nativeUnavailable();return concurrent}
      const includes=(id:string)=>!closed&&id===sessionID&&lookup()===current
      const coordinator:Coordinator=binding.scope==="controller"?
        new NativeControllerCompaction(ctx,current.registry,current.sources,current.custody,profile,governingTaskJSON!,includes):
        new NativeWorkerCompaction(ctx,current.registry,current.sources,current.custody,profile,includes)
      const entry:Route={runtime:current,coordinator,profileJSON,ready:false};routes.set(sessionID,entry)
      yield* coordinator.register(false).pipe(Effect.provideService(Scope.Scope,scope))
      if(runtime()!==current)nativeUnavailable()
      entry.ready=true
      return entry
    })
    yield* ctx.session.hook("compaction",event=>Effect.gen(function*(){
      const info=yield* ctx.session.get({sessionID:event.sessionID})
      if(info.location.directory!==config.workRoot)return
      const entry=yield* ensure(event.sessionID)
      yield* entry.coordinator.compact(event)
    }).pipe(Effect.orDie))
    yield* ctx.session.hook("generate",event=>Effect.gen(function*(){
      const info=yield* ctx.session.get({sessionID:event.sessionID})
      if(info.location.directory===config.workRoot)yield* io(()=>admission.generate(event))
    }).pipe(Effect.orDie))
    yield* ctx.session.hook("model.request",event=>Effect.gen(function*(){
      if(event.kind==="primary")return
      const info=yield* ctx.session.get({sessionID:event.sessionID})
      if(info.location.directory===config.workRoot)yield* io(()=>admission.request(event))
    }).pipe(Effect.orDie))
    const retirement=(sessionID:string)=>Effect.gen(function*(){
      const info=yield* ctx.session.get({sessionID:sessionID as Parameters<Plugin.Context["session"]["get"]>[0]["sessionID"]})
      if(info.location.directory!==config.workRoot)return
      const current=runtime()
      if(yield* nativeWorkerRetired(ctx,current.registry,sessionID))nativeUnavailable()
    }).pipe(Effect.orDie)
    yield* ctx.session.hook("context",event=>retirement(event.sessionID))
    yield* ctx.tool.hook("execute.before",event=>retirement(event.sessionID))
    const release=yield* Effect.sync(()=>retainNativeContextAdmission(config.root,config.hostId,config.workRoot,admission))
    yield* Effect.addFinalizer(()=>Effect.sync(()=>{closed=true;release();routes.clear()}))
  })
}
