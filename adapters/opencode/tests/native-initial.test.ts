// SPDX-License-Identifier: AGPL-3.0-only
// Actual custody + pinned hook shapes; native/model/service behavior is explicit MOCK.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import type { OpenCodeClient } from "@opencode/client"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { NativeInitialAdapter } from "../src/native-initial.js"
import { contextJSON } from "../src/native-context-codec.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"initial-")),work=path.join(root,"work");await fs.mkdir(work)
  const hooks=new Map<string,(e:any)=>Promise<void>>(),instructions:Array<{key:string;value:string}>=[],messages:any[]=[]
  const binding={family:"assignment-bindings",key:{projectId:id(1),assignmentId:id(2),generation:0},digest:"a".repeat(64)}
  const envelope={formatVersion:1,kind:"initial-start",projectId:id(1),assignmentId:id(2),executionGeneration:"0",binding,
    intentId:id(3),correlationId:id(4),nativeHostId:id(5),nativeSessionId:"ses_initial_worker",messageId:"msg_initial_original",text:"Begin exact bounded task",
    metadata:{nativeIntentId:id(3),assignmentId:id(2),executionGeneration:"0"},delivery:"queue",resume:true}
  const raw=contextJSON(envelope),packet=contextJSON([{key:"writhelm-task",value:"EXACT pinned contract, no summary substitution"}])
  const profile={family:"profiles",key:{projectId:id(1),profileId:id(6),revision:0},payload:{platform:"linux",qualification:"qualified",model:{providerId:"MOCK",modelId:"no-call",variant:null},
    outputReserveTokens:512,effort:"bounded",route:"no-call",permissions:[{tool:"read",action:"allow",scope:"workspace"}]}}
  let active=false,dispatch=true,wakes=0,lost=false,suppressBoundary=false,epoch="b".repeat(64),permissions:any[]=[{action:"*",resource:"*",effect:"deny"}],boundary:Promise<void>|undefined
  const event=()=>({sessionID:envelope.nativeSessionId,model:{providerID:"MOCK",id:"no-call"},system:[],options:{inherited:"must disappear"},tools:{read:{},shell:{}}})
  const hook=(domain:string)=>(name:string,callback:(e:any)=>Promise<void>)=>{hooks.set(domain+":"+name,callback);return Promise.resolve({dispose:async()=>{hooks.delete(domain+":"+name)}})}
  const ctx={app:{version:"2.2.5"},session:{hook:hook("session"),instructions:{entry:{list:async()=>instructions,put:async({key,value}:any)=>{instructions.push({key,value})}}},
    update:async(i:any)=>{permissions=i.permissions},prompt:async(i:any)=>{
      await hooks.get("session:prompt")!({sessionID:i.sessionID,messageID:i.id,prompt:{text:i.text},metadata:i.metadata,delivery:i.delivery})
      wakes++;messages.push({id:i.id,type:"user",text:i.text,metadata:i.metadata,time:{created:1}})
      if(!suppressBoundary)boundary=hooks.get("session:context")!(event())
      if(lost)throw Error("MOCK lost original prompt ack")
      return {id:i.id,type:"user",sessionID:i.sessionID,payload:{text:i.text,metadata:i.metadata},delivery:i.delivery}
    }},tool:{hook:hook("tool")}} as unknown as Plugin.Context
  const native={session:{get:async()=>({id:envelope.nativeSessionId,location:{directory:work},model:{providerID:"MOCK",id:"no-call"},permissions,cost:0}),
    context:async()=>messages,inbox:{list:async()=>[]}}} as unknown as OpenCodeClient
  const registry={inspect:async()=>({scope:"assignment",assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding),workRoot:work,epoch}),
    initialContext:async()=>({envelopeJSON:raw,envelopeDigest:createHash("sha256").update(raw).digest("hex"),packetJSON:packet,profileJSON:contextJSON(profile),
      assignmentJSON:contextJSON({payload:{intendedExecution:{template:"executor-v1"}}}),active,dispatch})} as unknown as NativeCredentialRegistry
  const custody=new LinuxNativeCustody(path.join(root,"private"),[work]);await custody.initialize()
  const deployment=new LinuxNativeCustody(path.join(root,"deployment"),[work]);await deployment.initialize()
  await deployment.retain("execution-profile",contextJSON(profile.key),contextJSON({formatVersion:1,profileKeyJSON:contextJSON(profile.key),model:{providerID:"MOCK",id:"no-call"},
    baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{effort:"bounded",maxTokens:64}}))
  const adapter=new NativeInitialAdapter(ctx,native,registry,custody,deployment,"ses_controller");await adapter.register()
  return {adapter,hooks,envelope,root,event,subject:{assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding)},
    restart:()=>new NativeInitialAdapter(ctx,native,registry,custody,deployment,"ses_controller"),changeEpoch:()=>{epoch="c".repeat(64)},
    active:()=>{active=true;dispatch=false},revoke:()=>{active=false;dispatch=false},lose:()=>{lost=true},noBoundary:()=>{suppressBoundary=true},wakes:()=>wakes,boundary:()=>boundary,permissions:()=>permissions}
}

test("initial NORMAL prompt is dispatched once; held scoped context without sessionID is required before ACTIVE release",async()=>{
  const f=await fixture()
  try{
    f.lose();await f.adapter.deliver(f.subject,id(3))
    await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    assert.equal(f.wakes(),1)
    const witness=await f.adapter.witness(id(3));assert.ok(witness?.heldJSON)
    await assert.rejects(f.adapter.release(f.envelope.nativeSessionId))
    await assert.rejects(f.adapter.admitTool(f.envelope.nativeSessionId))
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
    await f.adapter.admitTool(f.envelope.nativeSessionId)
    assert.ok(f.permissions().some(r=>r.action==="external_directory"&&r.effect==="deny"))
    // Restoring already ACTIVE must not invoke the original prompt again.
    await f.adapter.prepare(f.subject,id(3));await f.adapter.release(f.envelope.nativeSessionId)
    assert.equal(f.wakes(),1)
    f.revoke();await assert.rejects(f.adapter.admitTool(f.envelope.nativeSessionId))
  }finally{await f.adapter.close()}
})

test("unexpected first input, unbound tool and mismatched route cannot pass initial admission",async()=>{
  const f=await fixture()
  try{
    await f.adapter.prepare(f.subject,id(3))
    await assert.rejects(f.hooks.get("session:prompt")!({sessionID:f.envelope.nativeSessionId,messageID:"msg_unrelated",prompt:{text:"code without the contract"},metadata:{},delivery:"queue"}))
    await assert.rejects(f.adapter.admitTool("ses_unknown"))
    await f.adapter.deliver(f.subject,id(3));await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
    await assert.rejects(f.adapter.admitModel({sessionID:f.envelope.nativeSessionId,kind:"primary",model:{providerID:"MOCK",id:"no-call"},headers:{},baseURL:"https://different.example"} as never))
  }finally{await f.adapter.close()}
})

test("initial inbox/ack alone cannot manufacture a held-boundary witness or release; retry never wakes again",async()=>{
  const f=await fixture()
  try{
    f.noBoundary();await f.adapter.deliver(f.subject,id(3))
    assert.equal((await f.adapter.witness(id(3)))?.heldJSON,null)
    await f.adapter.deliver(f.subject,id(3));assert.equal(f.wakes(),1)
    f.active();await assert.rejects(f.adapter.release(f.envelope.nativeSessionId))
    await assert.rejects(f.adapter.admitTool(f.envelope.nativeSessionId))
  }finally{await f.adapter.close()}
})

test("same-incarnation initial recovery restores current ACTIVE admission without another normal prompt",async()=>{
  const f=await fixture(),restarted=f.restart()
  try{
    await f.adapter.deliver(f.subject,id(3));await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary();await f.adapter.close()
    await restarted.register()
    const recovered=await restarted.restore()
    assert.deepEqual(recovered,[{sessionID:f.envelope.nativeSessionId,intentId:id(3),phase:"active-admission-restored"}])
    await restarted.admitTool(f.envelope.nativeSessionId)
    assert.equal(f.wakes(),1)
    f.revoke();await assert.rejects(restarted.admitTool(f.envelope.nativeSessionId))
  }finally{await f.adapter.close();await restarted.close()}
})

test("startup recovery cannot promote ack-only, pending or another incarnation into initial ACTIVE admission",async()=>{
  for(const mode of ["ack-only","pending","new-incarnation"]){
    const f=await fixture(),restarted=f.restart()
    try{
      if(mode!=="new-incarnation")f.noBoundary()
      await f.adapter.deliver(f.subject,id(3))
      if(mode!=="pending")f.active()
      if(mode==="new-incarnation"){
        await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
        await f.adapter.release(f.envelope.nativeSessionId);await f.boundary();f.changeEpoch()
      }
      await f.adapter.close();await restarted.register()
      assert.equal((await restarted.restore())[0]?.phase,"initial-held",mode)
      await assert.rejects(restarted.admitTool(f.envelope.nativeSessionId))
      assert.equal(f.wakes(),1)
    }finally{await f.adapter.close();await restarted.close()}
  }
})
