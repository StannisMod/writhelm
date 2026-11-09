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
  let permissionUpdates=0,losePermissionReply=false,failPermissionBefore=false,policyReady=true,blockedFrame:Promise<void>|undefined,frameEntered:()=>void=()=>{}
  const event=()=>({sessionID:envelope.nativeSessionId,model:{providerID:"MOCK",id:"no-call"},system:[],options:{inherited:"must disappear"},tools:{read:{},shell:{}}})
  const hook=(domain:string)=>(name:string,callback:(e:any)=>Promise<void>)=>{hooks.set(domain+":"+name,callback);return Promise.resolve({dispose:async()=>{hooks.delete(domain+":"+name)}})}
  const ctx={app:{version:"2.2.5"},session:{hook:hook("session"),instructions:{entry:{list:async()=>instructions,put:async({key,value}:any)=>{instructions.push({key,value})}}},
    update:async(i:any)=>{
      permissionUpdates++
      if(failPermissionBefore)throw Error("MOCK permission outcome unknown before visible update")
      permissions=i.permissions
      if(losePermissionReply)throw Error("MOCK lost original permission-update acknowledgement")
    },prompt:async(i:any)=>{
      await hooks.get("session:prompt")!({sessionID:i.sessionID,messageID:i.id,prompt:{text:i.text},metadata:i.metadata,delivery:i.delivery})
      wakes++;messages.push({id:i.id,type:"user",text:i.text,metadata:i.metadata,time:{created:1}})
      if(!suppressBoundary)boundary=hooks.get("session:context")!(event())
      if(lost)throw Error("MOCK lost original prompt ack")
      return {id:i.id,type:"user",sessionID:i.sessionID,payload:{text:i.text,metadata:i.metadata},delivery:i.delivery}
    }},tool:{hook:hook("tool")}} as unknown as Plugin.Context
  const native={session:{get:async()=>({id:envelope.nativeSessionId,location:{directory:work},model:{providerID:"MOCK",id:"no-call"},permissions,cost:0}),
    context:async()=>messages,inbox:{list:async()=>[]}}} as unknown as OpenCodeClient
  const registry={inspect:async()=>({scope:"assignment",assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding),workRoot:work,epoch}),
    initialContext:async()=>{
      if(blockedFrame){frameEntered();await blockedFrame}
      return {envelopeJSON:raw,envelopeDigest:createHash("sha256").update(raw).digest("hex"),packetJSON:packet,profileJSON:contextJSON(profile),
        assignmentJSON:contextJSON({payload:{intendedExecution:{template:"executor-v1"}}}),active,dispatch}
    }} as unknown as NativeCredentialRegistry
  const custody=new LinuxNativeCustody(path.join(root,"private"),[work]);await custody.initialize()
  const deployment=new LinuxNativeCustody(path.join(root,"deployment"),[work]);await deployment.initialize()
  await deployment.retain("execution-profile",contextJSON(profile.key),contextJSON({formatVersion:1,profileKeyJSON:contextJSON(profile.key),model:{providerID:"MOCK",id:"no-call"},
    baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{effort:"bounded",maxTokens:64}}))
  const checkPolicy=async()=>{if(!policyReady)throw Error("MOCK effective context policy changed")}
  const create=()=>new NativeInitialAdapter(ctx,native,registry,custody,deployment,"ses_controller",()=>false,undefined,checkPolicy)
  const adapter=create();await adapter.register()
  return {adapter,hooks,envelope,root,event,subject:{assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding)},
    restart:create,changeEpoch:()=>{epoch="c".repeat(64)},revokePolicy:()=>{policyReady=false},
    restrict:()=>{permissions=[...permissions,{action:"read",resource:"private.txt",effect:"deny"}]},denyAll:()=>{permissions=[{action:"*",resource:"*",effect:"deny"}]},
    permissionUpdates:()=>permissionUpdates,losePermissionReply:()=>{losePermissionReply=true},failPermissionBefore:()=>{failPermissionBefore=true},
    blockFrame:()=>{
      let release!:()=>void
      blockedFrame=new Promise<void>(resolve=>{release=resolve})
      const entered=new Promise<void>(resolve=>{frameEntered=resolve})
      return {entered,release}
    },
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

test("ACTIVE reconciliation never overwrites an observed owner restriction or repeats a permission write",async()=>{
  for(const mode of ["extra-deny","deny-all"]){
    const f=await fixture(),restarted=f.restart()
    try{
      await f.adapter.deliver(f.subject,id(3));await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
      f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
      assert.equal(f.permissionUpdates(),1)
      await f.adapter.release(f.envelope.nativeSessionId);assert.equal(f.permissionUpdates(),1)
      if(mode==="extra-deny")f.restrict();else f.denyAll()
      const ownerPermissions=structuredClone(f.permissions())
      await assert.rejects(f.adapter.release(f.envelope.nativeSessionId));await assert.rejects(f.adapter.admitTool(f.envelope.nativeSessionId))
      await f.adapter.close();await restarted.register()
      assert.equal((await restarted.restore())[0]!.phase,"initial-held")
      await assert.rejects(restarted.admitTool(f.envelope.nativeSessionId))
      assert.deepEqual(f.permissions(),ownerPermissions);assert.equal(f.permissionUpdates(),1)
    }finally{await f.adapter.close();await restarted.close()}
  }
})

test("lost original permission reply reconciles only the exact target; an unknown unchanged deny never retries",async()=>{
  for(const performed of [true,false]){
    const f=await fixture()
    try{
      await f.adapter.deliver(f.subject,id(3));await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
      f.active();if(performed)f.losePermissionReply();else f.failPermissionBefore()
      await assert.rejects(f.adapter.release(f.envelope.nativeSessionId));assert.equal(f.permissionUpdates(),1)
      await assert.rejects(f.adapter.admitTool(f.envelope.nativeSessionId))
      if(performed){await f.adapter.release(f.envelope.nativeSessionId);await f.boundary();await f.adapter.admitTool(f.envelope.nativeSessionId)}
      else await assert.rejects(f.adapter.release(f.envelope.nativeSessionId))
      assert.equal(f.permissionUpdates(),1);assert.equal(f.wakes(),1)
    }finally{void f.boundary()?.catch(()=>{});await f.adapter.close()}
  }
})

test("closing initial admission invalidates an in-flight tool check even if its old service reply is ACTIVE",async()=>{
  const f=await fixture()
  try{
    await f.adapter.deliver(f.subject,id(3));await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
    const held=f.blockFrame(),admission=f.adapter.admitTool(f.envelope.nativeSessionId)
    await held.entered;await f.adapter.close();held.release()
    await assert.rejects(admission)
  }finally{await f.adapter.close()}
})

test("concurrent initial releases can reconcile but cannot post two native permission updates",async()=>{
  const f=await fixture()
  try{
    await f.adapter.deliver(f.subject,id(3));await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active()
    const results=await Promise.allSettled([f.adapter.release(f.envelope.nativeSessionId),f.adapter.release(f.envelope.nativeSessionId)])
    assert.ok(results.some(result=>result.status==="fulfilled"));await f.boundary()
    assert.equal(f.permissionUpdates(),1);assert.equal(f.wakes(),1)
  }finally{await f.adapter.close()}
})

test("closing during initial current-authority read cannot send the original prompt or open permissions",async()=>{
  const f=await fixture()
  try{
    const held=f.blockFrame(),delivery=f.adapter.deliver(f.subject,id(3))
    await held.entered;await f.adapter.close();held.release()
    await assert.rejects(delivery)
    assert.equal(f.wakes(),0);assert.equal(f.permissionUpdates(),0)
  }finally{await f.adapter.close()}
})

test("effective-policy loss blocks both the initial prompt and an already ACTIVE model boundary",async()=>{
  const fresh=await fixture()
  try{
    fresh.revokePolicy();await assert.rejects(fresh.adapter.deliver(fresh.subject,id(3)))
    assert.equal(fresh.wakes(),0);assert.equal(fresh.permissionUpdates(),0)
  }finally{await fresh.adapter.close()}
  const active=await fixture()
  try{
    await active.adapter.deliver(active.subject,id(3));await active.adapter.awaitBoundary(active.envelope.nativeSessionId,new AbortController().signal)
    active.active();await active.adapter.release(active.envelope.nativeSessionId);await active.boundary()
    active.revokePolicy()
    await assert.rejects(active.adapter.admitModel({sessionID:active.envelope.nativeSessionId,kind:"primary",model:{providerID:"MOCK",id:"no-call"},headers:{}} as never))
    assert.equal(active.wakes(),1)
  }finally{await active.adapter.close()}
})
