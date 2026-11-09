// SPDX-License-Identifier: AGPL-3.0-only
// Hook-only fixture: no service, provider, model call or installed-host qualification.
import test from "node:test"
import assert from "node:assert/strict"
import { Effect } from "effect"
import type { Plugin } from "@opencode/plugin/effect"
import { NativeContextExtraction } from "../src/native-context-extraction.js"
import type { NativeContextSources } from "../src/native-context-source.js"

test("session-scoped extraction ignores another profile but rejects its own uncorrelated generation",async()=>{
  const hooks=new Map<string,Array<(event:any)=>Effect.Effect<any,any,any>>>()
  const ctx={app:{version:"2.2.5"},session:{hook:(name:string,callback:any)=>Effect.sync(()=>{
    hooks.set(name,[...(hooks.get(name)??[]),callback])
  })}} as unknown as Plugin.Context
  const sources={scopeKind:()=>"assignment"} as unknown as NativeContextSources
  const profile={profileJSON:'{"profile":"fixture"}',model:{providerID:"MOCK",id:"no-call"},baseURL:null,
    nativeOptions:{maxTokens:64,effort:"bounded"},routeID:"fixture",effortOptionKey:"effort"}
  const first=new NativeContextExtraction(ctx,sources,profile,id=>id==="ses_first")
  const second=new NativeContextExtraction(ctx,sources,profile,id=>id==="ses_second")
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
