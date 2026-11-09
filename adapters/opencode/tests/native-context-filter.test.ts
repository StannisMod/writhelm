// SPDX-License-Identifier: AGPL-3.0-only
// Hook-only fixture: no service, provider, model call or installed-host qualification.
import test from "node:test"
import assert from "node:assert/strict"
import { Deferred,Effect,Fiber } from "effect"
import { Message } from "@opencode/ai/effect"
import type { Plugin } from "@opencode/plugin/effect"
import { NativeContextExtraction } from "../src/native-context-extraction.js"
import type { NativeContextSources } from "../src/native-context-source.js"
import { registerNativeContextDispatcher } from "../src/native-context-dispatch.js"
import { nativeContextAdmission } from "../src/native-context-reference.js"
import type { RuntimeReference } from "../src/native-runtime.js"
import contextServer from "../src/context-server.js"

test("session-scoped extraction ignores another profile but rejects its own uncorrelated generation",async()=>{
  const hooks=new Map<string,Array<(event:any)=>Effect.Effect<any,any,any>>>()
  const ctx={app:{version:"2.2.5"},session:{hook:(name:string,callback:any)=>Effect.sync(()=>{
    hooks.set(name,[...(hooks.get(name)??[]),callback])
  })}} as unknown as Plugin.Context
  const sources={scopeKind:()=>"assignment"} as unknown as NativeContextSources
  const profile={profileJSON:'{"profile":"fixture"}',model:{providerID:"MOCK",id:"no-call"},baseURL:null,
    nativeOptions:{maxTokens:64,effort:"bounded"},routeID:"fixture",effortOptionKey:"effort"}
  const first=new NativeContextExtraction(ctx,sources,profile,id=>id==="ses_first")
  const second=new NativeContextExtraction(ctx,sources,{...profile,model:{...profile.model,id:"another-no-call"}},id=>id==="ses_second")
  await Effect.runPromise(Effect.scoped(Effect.gen(function*(){
    yield* first.register();yield* second.register()
    const foreign={sessionID:"ses_other",model:profile.model}
    for(const callback of hooks.get("generate")!)yield* callback(foreign) as Effect.Effect<void,unknown>
    const callbacks=hooks.get("generate")!
    const own=yield* Effect.exit(callbacks[0]!({sessionID:"ses_first",model:profile.model}) as Effect.Effect<void,unknown>)
    assert.equal(own._tag,"Failure")
    yield* callbacks[1]!({sessionID:"ses_first",model:profile.model}) as Effect.Effect<void,unknown>
    const title={sessionID:"ses_other",result:undefined}
    for(const callback of hooks.get("title")!)yield* callback(title) as Effect.Effect<void,unknown>
    assert.equal(title.result,undefined)
  })))
})

test("Effect companion refuses absent protected bootstrap without constructing any generation",async()=>{
  const old=process.env.WRITHELM_PRIMARY_ROOT;delete process.env.WRITHELM_PRIMARY_ROOT
  try{
    const outcome=await Effect.runPromise(Effect.scoped(contextServer.effect({app:{version:"2.2.5"}} as Plugin.Context)).pipe(Effect.exit))
    assert.equal(outcome._tag,"Failure")
  }finally{if(old!==undefined)process.env.WRITHELM_PRIMARY_ROOT=old}
})

test("host Effect interruption retires the extraction lease; no auxiliary admission survives cancellation",async()=>{
  const callbacks=new Map<string,(event:any)=>Effect.Effect<void,unknown>>()
  const profile={profileJSON:'{"profile":"fixture"}',model:{providerID:"MOCK",id:"no-call"},baseURL:null,
    nativeOptions:{maxTokens:64,effort:"bounded"},routeID:"fixture",effortOptionKey:"effort"}
  const source={scope:{scope:"assignment"},system:"MOCK exact system",prompt:"MOCK extraction draft"}
  const sources={scopeKind:()=>"assignment",currentScope:async()=>source.scope,capture:()=>Effect.succeed({artifact:source})} as unknown as NativeContextSources
  await Effect.runPromise(Effect.scoped(Effect.gen(function*(){
    const entered=yield* Deferred.make<void>()
    let captured:any
    const ctx={app:{version:"2.2.5"},session:{hook:(name:string,callback:any)=>Effect.sync(()=>{callbacks.set(name,callback)}),
      generate:({sessionID,prompt}:any)=>Effect.gen(function*(){
        captured={sessionID,model:profile.model,messages:[Message.user(prompt)],system:[],options:{inherited:true},tools:{shell:{}}}
        yield* callbacks.get("generate")!(captured)
        yield* callbacks.get("model.request")!({sessionID,model:profile.model,kind:"generate",headers:{}})
        yield* Deferred.succeed(entered,undefined)
        return yield* Effect.never
      })}} as unknown as Plugin.Context
    const extractor=new NativeContextExtraction(ctx,sources,profile,id=>id==="ses_cancel")
    yield* extractor.register()
    const fiber=yield* Effect.forkChild(extractor.extract({sessionID:"ses_cancel",model:profile.model} as never,"MOCK basis","MOCK packet",[]))
    yield* Deferred.await(entered)
    assert.deepEqual(captured.tools,{})
    assert.deepEqual(captured.options,{maxTokens:64,effort:"bounded"})
    extractor.assertGenerate(captured)
    yield* Fiber.interrupt(fiber)
    assert.throws(()=>extractor.assertGenerate(captured))
    assert.throws(()=>extractor.assertRequest({sessionID:"ses_cancel",model:profile.model,kind:"generate"} as never))
  })))
})

test("dispatcher references close with Scope; missing/replaced runtime and uncorrelated generation hold",async()=>{
  const config={root:"/MOCK-context-private",hostId:"MOCK-host",workRoot:"/MOCK-work"}
  const hooks=new Map<string,Array<(event:any)=>Effect.Effect<void,unknown>>>()
  const hook=(name:string,callback:any)=>Effect.acquireRelease(Effect.sync(()=>{
    hooks.set(name,[...(hooks.get(name)??[]),callback]);return {dispose:Effect.void}
  }),()=>Effect.sync(()=>hooks.set(name,(hooks.get(name)??[]).filter(c=>c!==callback))))
  const context=()=>Effect.succeed([{type:"compaction",metadata:{writhelm:{finishedHandoff:true,checkpoint:{id:"MOCK-cp"}}}}])
  const ctx={app:{version:"2.2.5"},session:{get:()=>Effect.succeed({location:{directory:config.workRoot}}),hook,context},
    tool:{hook:(name:string,callback:any)=>hook("tool:"+name,callback)}} as unknown as Plugin.Context
  let readable=true,finished=false,current:RuntimeReference|undefined
  const registry={scopeKind:()=>"assignment",inspect:async()=>({scope:"assignment",hostId:config.hostId,workRoot:config.workRoot}),
    sourceIdentity:async()=>{if(!readable)throw Error("MOCK READ withdrawn");return {scope:"assignment"}},
    workerContext:async()=>({nativeReady:true,finishedHandoff:finished,checkpointReferenceJSON:'{"id":"MOCK-cp"}'})}
  const runtime={hostId:config.hostId,pid:process.pid,registry,admitTool:async()=>{},deployment:{read:async()=>undefined}} as unknown as RuntimeReference
  assert.throws(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot))
  let retained:ReturnType<typeof nativeContextAdmission>|undefined
  await Effect.runPromise(Effect.scoped(Effect.gen(function*(){
    yield* registerNativeContextDispatcher(ctx,config,()=>current)
    retained=nativeContextAdmission(config.root,config.hostId,config.workRoot)
    assert.throws(()=>nativeContextAdmission(config.root,"another-host",config.workRoot))
    assert.throws(()=>nativeContextAdmission(config.root,config.hostId,"/another-work-area"))
    const event={sessionID:"ses_fixture",model:{providerID:"MOCK",id:"no-call"}} as never
    yield* Effect.promise(()=>assert.rejects(retained!.compaction(event)))
    current=runtime
    yield* Effect.promise(()=>retained!.compaction(event))
    yield* Effect.promise(()=>assert.rejects(retained!.generate(event)))
    yield* Effect.promise(()=>assert.rejects(retained!.request({...event as any,kind:"generate"})))
    readable=false;yield* Effect.promise(()=>assert.rejects(retained!.compaction(event)));readable=true
    finished=true
    assert.equal((yield* Effect.exit(hooks.get("context")![0]!({sessionID:"ses_fixture"})))._tag,"Failure")
    assert.equal((yield* Effect.exit(hooks.get("tool:execute.before")![0]!({sessionID:"ses_fixture"})))._tag,"Failure")
    current=undefined;yield* Effect.promise(()=>assert.rejects(retained!.compaction(event)))
  })))
  assert.throws(()=>nativeContextAdmission(config.root,config.hostId,config.workRoot))
  await assert.rejects(retained!.compaction({sessionID:"ses_fixture"} as never))
  assert.ok([...hooks.values()].every(callbacks=>callbacks.length===0))
})
