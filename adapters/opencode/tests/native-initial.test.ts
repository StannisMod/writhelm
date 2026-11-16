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
import { NativeWorkerPreparation, NativeDispatchFrontier } from "../src/native-worker-preparation.js"
import { NativeInitialStart } from "../src/native-initial-start.js"
import { contextJSON } from "../src/native-context-codec.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { createServer } from "node:net"
import { provisionExecutionDeployment } from "../src/primary-bootstrap.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
async function fixture(template="executor-v1"){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"initial-")),work=path.join(root,"work");await fs.mkdir(work)
  const hooks=new Map<string,(e:any)=>Promise<void>>(),instructions:Array<{key:string;value:string}>=[],messages:any[]=[]
  const binding={family:"assignment-bindings",key:{projectId:id(1),assignmentId:id(2),generation:0},digest:"a".repeat(64)}
  const envelope={formatVersion:1,kind:"initial-start",projectId:id(1),assignmentId:id(2),executionGeneration:"0",binding,
    intentId:id(3),correlationId:id(4),nativeHostId:id(5),nativeSessionId:"ses_initial_worker",messageId:"msg_initial_original",text:"Begin exact bounded task",
    metadata:{nativeIntentId:id(3),assignmentId:id(2),executionGeneration:"0"},delivery:"queue",resume:true}
  const raw=contextJSON(envelope),packet=contextJSON([{key:"writhelm-task",value:"EXACT pinned contract, no summary substitution"},
    {key:"writhelm-record-binding",value:contextJSON({family:"assignment-bindings",key:binding.key,payload:{intendedExecution:{template}}})}].sort((a,b)=>a.key.localeCompare(b.key)))
  const profile={formatVersion:1,family:"profiles",key:{projectId:id(1),profileId:id(6),revision:0},payload:{
    nativeProfileId:"explicit-test-native",qualificationEvidence:[],model:{providerId:"MOCK",modelId:"no-call",variant:null},
    platform:"linux",qualification:"unqualified",contextTokens:8192,outputReserveTokens:512,toolReserveTokens:512,compactionReserveTokens:512,
    compactionPolicyId:"explicit-test-foreground",requestAccountingProfileId:"test-no-call",executionAssurance:"E-COOP-1",
    effort:"bounded",route:"no-call",permissions:[{tool:"read",action:"allow",scope:"workspace"}]}}
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
  let nativeVariant:string|undefined
  const native={session:{get:async()=>({id:envelope.nativeSessionId,location:{directory:work},model:{providerID:"MOCK",id:"no-call",...(nativeVariant===undefined?{}:{variant:nativeVariant})},permissions,cost:0}),
    context:async()=>messages,inbox:{list:async()=>[]}}} as unknown as OpenCodeClient
  const registry={inspect:async()=>({scope:"assignment",assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding),workRoot:work,epoch}),
    initialContext:async()=>{
      if(blockedFrame){frameEntered();await blockedFrame}
      return {envelopeJSON:raw,envelopeDigest:createHash("sha256").update(raw).digest("hex"),packetJSON:packet,profileJSON:contextJSON(profile),
        assignmentJSON:contextJSON({payload:{intendedExecution:{template}}}),active,dispatch}
    }} as unknown as NativeCredentialRegistry
  const custody=new LinuxNativeCustody(path.join(root,"private"),[work]);await custody.initialize()
  const deployment=new LinuxNativeCustody(path.join(root,"bootstrap"),[work])
  await provisionExecutionDeployment({root,projectId:id(1),forbiddenAreas:[work],profileRecordJSON:contextJSON(profile),
    executionJSON:contextJSON({formatVersion:1,profileKeyJSON:contextJSON(profile.key),model:{providerID:"MOCK",id:"no-call"},
      baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{effort:"bounded",maxTokens:64}}),
    contextPolicyJSON:contextJSON({formatVersion:1,profileKeyJSON:contextJSON(profile.key),policyId:profile.payload.compactionPolicyId,
      nativeProfileId:profile.payload.nativeProfileId,auto:true,background:false,keepTokens:2048,bufferTokens:1024})})
  await deployment.initialize()
  const checkPolicy=async()=>{if(!policyReady)throw Error("MOCK effective context policy changed")}
  let cooperative=false,cooperativeReads=0,expireCooperative=false
  const create=()=>new NativeInitialAdapter(ctx,native,registry,custody,deployment,"ses_controller",()=>false,undefined,checkPolicy,
    async(sessionID,bindingJSON)=>sessionID===envelope.nativeSessionId&&bindingJSON===contextJSON(binding)&&
      cooperative&&(!expireCooperative||++cooperativeReads===1))
  const adapter=create();await adapter.register()
  return {adapter,hooks,envelope,root,event,admission:(purpose:string)=>contextJSON({subject:{assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding)},
      sessionID:envelope.nativeSessionId,profileJSON:contextJSON(profile),purpose,epoch:"b".repeat(64)}),
    subject:{assignmentId:id(2),executionGeneration:"0",bindingJSON:contextJSON(binding)},
    restart:create,changeEpoch:()=>{epoch="c".repeat(64)},revokePolicy:()=>{policyReady=false},nativeVariant:(value:string)=>{nativeVariant=value},
    restrict:()=>{permissions=[...permissions,{action:"read",resource:"private.txt",effect:"deny"}]},denyAll:()=>{permissions=[{action:"*",resource:"*",effect:"deny"}]},
    permissionUpdates:()=>permissionUpdates,losePermissionReply:()=>{losePermissionReply=true},failPermissionBefore:()=>{failPermissionBefore=true},
    blockFrame:()=>{
      let release!:()=>void
      blockedFrame=new Promise<void>(resolve=>{release=resolve})
      const entered=new Promise<void>(resolve=>{frameEntered=resolve})
      return {entered,release}
    },
    cooperative:(expire=false)=>{cooperative=true;expireCooperative=expire;cooperativeReads=0},
    active:()=>{active=true;dispatch=false},revoke:()=>{active=false;dispatch=false},lose:()=>{lost=true},noBoundary:()=>{suppressBoundary=true},wakes:()=>wakes,boundary:()=>boundary,permissions:()=>permissions}
}

test("phase admission observes deployment identity and applied restrictions without a model call or campaign",async()=>{
  const f=await fixture("reviewer-v1")
  try{
    const raw=f.admission("start")
    const unregistered=f.restart()
    await assert.rejects(unregistered.admission(raw));await unregistered.close()
    assert.equal((await f.adapter.admission(raw)).requestDigest,createHash("sha256").update(raw).digest("hex"))
    assert.equal(f.wakes(),0);assert.equal(f.permissionUpdates(),0)
    await assert.rejects(f.adapter.admission(f.admission("resume")))
    f.nativeVariant("wrong");await assert.rejects(f.adapter.admission(raw));f.nativeVariant("default")
    await f.adapter.deliver(f.subject,id(3))
    await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
    assert.ok(await f.adapter.admission(f.admission("access")))
    assert.ok(await f.adapter.admission(f.admission("retention")))
    f.restrict();await assert.rejects(f.adapter.admission(f.admission("access")))
    f.denyAll();await assert.rejects(f.adapter.admission(f.admission("access")),
      "An owner deny-all after the original release is a restriction, not a first activation")
    f.changeEpoch();await assert.rejects(f.adapter.admission(raw))
    assert.equal(f.wakes(),1)
  }finally{await f.adapter.close()}
})

test("MOCK same-binding resume admission needs the original start and a cooperative witness that remains current",async()=>{
  const f=await fixture()
  try{
    f.cooperative()
    await assert.rejects(f.adapter.admission(f.admission("resume")),"Pause alone cannot replace the original start witness")
    await f.adapter.deliver(f.subject,id(3))
    await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
    assert.ok(await f.adapter.admission(f.admission("resume")))
    f.cooperative(true)
    await assert.rejects(f.adapter.admission(f.admission("resume")),"Witness loss during reads must hold")
    assert.equal(f.wakes(),1,"Read-only resume admission must never wake")
  }finally{await f.adapter.close()}
})
test("private phase-admission transport binds exact request and refuses missing peer proof without a wake",async()=>{
  const f=await fixture()
  const socket=createServer()
  await new Promise<void>(resolve=>socket.listen(0,"127.0.0.1",resolve))
  const port=(socket.address() as {port:number}).port
  await new Promise<void>(resolve=>socket.close(()=>resolve()))
  const origin=`http://127.0.0.1:${port}`,proof=Buffer.alloc(32,31)
  const transport=await listenNativeTransport(origin,proof,{} as never,{} as never,undefined,undefined,f.adapter)
  try{
    const requestJSON=f.admission("start")
    const call=(credential:string)=>fetch(origin+"/native/v1",{method:"POST",headers:{"Content-Type":"application/json","X-Writhelm-Native-Proof":credential},
      body:JSON.stringify({formatVersion:1,operation:"execution-admission",requestJSON})})
    const response=await call(proof.toString("base64url"));assert.equal(response.status,200)
    const body=await response.json() as any
    assert.equal(body.result.requestDigest,createHash("sha256").update(requestJSON).digest("hex"))
    assert.notEqual((await call(Buffer.alloc(32,32).toString("base64url"))).status,200)
    assert.equal(f.wakes(),0);assert.equal(f.permissionUpdates(),0)
  }finally{await transport.close();await f.adapter.close()}
})

test("pinned native default variant matches an omitted profile variant but arbitrary variants refuse before prompt",async()=>{
  const f=await fixture()
  try{
    f.nativeVariant("default");await f.adapter.prepare(f.subject,id(3))
    f.nativeVariant("unexpected-variant");await assert.rejects(f.adapter.prepare(f.subject,id(3)))
    assert.equal(f.wakes(),0)
  }finally{await f.adapter.close()}
})

test("reviewer execute.before rejects cached mutation/plugin/code-mode tools independently of context filtering",async()=>{
  const f=await fixture("reviewer-v1")
  try{
    await f.adapter.deliver(f.subject,id(3))
    await f.adapter.awaitBoundary(f.envelope.nativeSessionId,new AbortController().signal)
    f.active();await f.adapter.release(f.envelope.nativeSessionId);await f.boundary()
    const execute=f.hooks.get("tool:execute.before")!
    for(const tool of ["edit","write","patch","shell","execute","subagent","codemode","plugin_write","permission_update"])
      await assert.rejects(execute({sessionID:f.envelope.nativeSessionId,tool}))
    for(const tool of ["read","writhelm_work_read","writhelm_work_command"])
      await execute({sessionID:f.envelope.nativeSessionId,tool})
    f.revoke();await assert.rejects(execute({sessionID:f.envelope.nativeSessionId,tool:"read"}))
    assert.equal(f.wakes(),1)
  }finally{await f.adapter.close()}
})

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
const projectId="11111111-1111-4111-8111-111111111111"
const driverId="22222222-2222-4222-8222-222222222222"
const signal=new AbortController().signal

test("controller preparation retains original reservation and rejects assignment channel",async()=>{
  const records=new Map<string,string>(),sent:string[]=[]
  const custody={initialize:async()=>{},read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{records.set(n+k,v)}}
  let scope="controller",lose=true,authorized=true
  const channel={scope,projectID:projectId,send:async(_route:string,raw:string)=>{sent.push(raw);if(lose)throw Error("MOCK response lost");return JSON.stringify({kind:"settled",outcome:{kind:"held"}})}}
  let retryRaw=""
  const registry={contextChannel:async()=>({...channel,scope}),sourceIdentity:async()=>{if(!authorized)throw Error("MOCK authority revoked")},
    reservationRetryEligibility:async(_session:string,raw:string)=>{retryRaw=raw;return "definitive-refusal"}}
  const driver=new NativeWorkerPreparation(registry as never,{} as never,custody as never,"ses_controller",{projectId,controllerId:driverId,hostId:driverId,worktreeParent:"/unused"})
  const selection=JSON.stringify({task:{projectId,taskId:driverId},manifest:{family:"input-manifests",key:{projectId}},profile:{projectId},resources:[{}],reviewSubjects:[]})
  await assert.rejects(driver.runController(selection,driverId,signal))
  lose=false
  const restored=new NativeWorkerPreparation(registry as never,{} as never,custody as never,"ses_controller",{projectId,controllerId:driverId,hostId:driverId,worktreeParent:"/unused"})
  assert.equal((await restored.restoreController(driverId,signal)).phase,"reservation-held")
  assert.equal(sent.length,4);assert.ok(sent.every(raw=>raw===sent[0]))
  assert.equal(await restored.retryEligibilityController(driverId,signal),"definitive-refusal")
  assert.equal(retryRaw,sent[0]);assert.equal(sent.length,4)
  await assert.rejects(restored.retryEligibilityController("33333333-3333-4333-8333-333333333333",signal))
  await assert.rejects(driver.runController(selection.replace('"resources":[{}]','"resources":[{},{}]'),driverId,signal))
  authorized=false
  await assert.rejects(driver.runController(selection,driverId,signal))
  authorized=true
  scope="assignment"
  await assert.rejects(driver.runController(selection,driverId,signal))
  await assert.rejects(restored.restoreController(driverId,signal))
  await assert.rejects(restored.retryEligibilityController(driverId,signal))
  scope="controller"
  await assert.rejects(restored.restoreController("33333333-3333-4333-8333-333333333333",signal))
  assert.equal(sent.length,4)
})

test("controller start rejects assignment channel before any operation",async()=>{
  let identityCalls=0
  const registry={contextChannel:async()=>({scope:"assignment",projectID:projectId}),sourceIdentity:async()=>{identityCalls++}}
  const driver=new NativeInitialStart(registry as never,{} as never,{} as never,{} as never,"ses_controller",projectId)
  await assert.rejects(driver.runController("{}",driverId,signal))
  assert.equal(identityCalls,0)
})

test("planned controller binds original reservation before reserve and restores identical binding",async()=>{
  const records=new Map<string,string>(),bindingRequests:string[]=[],reservations:string[]=[]
  const custody={initialize:async()=>{},read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{
    const old=records.get(n+k);if(old!==undefined&&old!==v)throw Error("conflict");records.set(n+k,v)
  }}
  let settled=false,scope="controller"
  const work={scope,projectID:projectId,send:async(_route:string,raw:string)=>{reservations.push(raw);return JSON.stringify({kind:"settled",outcome:{kind:"held"}})}}
  const bind={scope,projectID:projectId,send:async(_route:string,raw:string)=>{
    bindingRequests.push(raw);if(!settled)return JSON.stringify({kind:"error",code:"PUBLICATION_UNKNOWN"})
    const cmd=JSON.parse(raw);return JSON.stringify({kind:"settled",identity:{projectId,operation:cmd.operation,requestKey:cmd.requestKey},
      outcome:{kind:"success",result:{key:{projectId,id:driverId}}}})
  }}
  const registry={contextChannel:async()=>({...work,scope}),dispatchAttemptChannel:async()=>({...bind,scope}),sourceIdentity:async()=>{}}
  const config={projectId,controllerId:driverId,hostId:driverId,worktreeParent:"/unused"}
  const driver=new NativeWorkerPreparation(registry as never,{} as never,custody as never,"ses_controller",config)
  const selection=JSON.stringify({task:{projectId,taskId:driverId},manifest:{family:"input-manifests",key:{projectId}},profile:{projectId},resources:[{}],reviewSubjects:[]})
  const attempt={planRefJSON:JSON.stringify({family:"dispatch-plans",key:{projectId,id:driverId},digest:"a".repeat(64)})}
  assert.equal((await driver.runPlannedController(selection,driverId,attempt,signal)).phase,"attempt-binding-held")
  assert.equal(reservations.length,0);assert.equal(bindingRequests.length,3)
  settled=true
  const restored=new NativeWorkerPreparation(registry as never,{} as never,custody as never,"ses_controller",config)
  assert.equal((await restored.restorePlannedController(driverId,attempt,signal)).phase,"reservation-held")
  assert.ok(bindingRequests.every(raw=>raw===bindingRequests[0]));assert.equal(reservations.length,1)
  assert.deepEqual(JSON.parse(bindingRequests[0]!).payload.reservation,JSON.parse(reservations[0]!))
  await assert.rejects(restored.restorePlannedController(driverId,{planRefJSON:attempt.planRefJSON.replace("a".repeat(64),"b".repeat(64))},signal))
  assert.equal(bindingRequests.length,4);assert.equal(reservations.length,1)
  scope="assignment";await assert.rejects(restored.restorePlannedController(driverId,attempt,signal))
})

test("MOCK frontier overlaps ready work and starts a newly ready task without a batch barrier",async()=>{
  const ids=["aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","cccccccc-cccc-4ccc-8ccc-cccccccccccc"]
  const prep=["11111111-1111-4111-8111-111111111111","22222222-2222-4222-8222-222222222222","33333333-3333-4333-8333-333333333333"]
  const start=["44444444-4444-4444-8444-444444444444","55555555-5555-4555-8555-555555555555","66666666-6666-4666-8666-666666666666"]
  const gates=prep.map(()=>{let resolve!:(v:any)=>void;const promise=new Promise<any>(r=>{resolve=r});return {promise,resolve}})
  const started:string[]=[];let two!:()=>void,third!:()=>void,startedB!:()=>void
  const overlap=new Promise<void>(r=>{two=r}),newlyReady=new Promise<void>(r=>{third=r}),startedSecond=new Promise<void>(r=>{startedB=r})
  let readyThird=false,basis=1
  const plan={key:{projectId,id:driverId},payload:{entries:prep.map((preparationId,i)=>({preparationId,startId:start[i]}))}}
  const registry={dispatchContext:async()=>({basis:{revision:basis},planJSON:JSON.stringify(plan),attemptsJSON:[],
    activeAssignments:readyThird?[JSON.stringify({key:{id:ids[1]},payload:{task:{taskId:ids[1]}}})]:[],
    entries:ids.map((taskId,i)=>({preparationId:prep[i],startId:start[i],selectionJSON:JSON.stringify({task:{taskId}}),
      taskJSON:JSON.stringify({key:{taskId},payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:i===2?[{selectedResult:readyThird?{id:"exact"}:null}]:[]}})}))})}
  const preparation={runPlannedController:async(_selection:string,id:string)=>{
    started.push(id);if(started.length===2)two();if(id===prep[2])third()
    return gates[prep.indexOf(id)]!.promise
  },restorePlannedController:async()=>{throw Error("unexpected restore")}}
  const initial={runController:async()=>{startedB();return {phase:"active"}},restoreController:async()=>{throw Error("unexpected restore")}}
  const custody={read:async()=>undefined}
  const frontier=new NativeDispatchFrontier(registry as never,preparation as never,initial as never,custody as never,"ses_controller",driverId,3,60000)
  frontier.start();await overlap
  assert.deepEqual(new Set(started),new Set(prep.slice(0,2)))
  gates[1]!.resolve({phase:"binding-confirmed",state:"bound",currentIntent:null,assignmentSelector:{},binding:{}})
  await startedSecond;readyThird=true;basis=2;frontier.wake();await newlyReady
  assert.equal(started.filter(id=>id===prep[0]).length,1)
  assert.equal(started.filter(id=>id===prep[2]).length,1)
  gates[0]!.resolve({phase:"reservation-held"});gates[2]!.resolve({phase:"reservation-held"})
  await frontier.close()
})

test("MOCK frontier retries retained attempt after lost reply at unchanged head",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const taskId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
  const plan={key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start}]}}
  const frame={basis:{revision:1},planJSON:JSON.stringify(plan),attemptsJSON:[],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,selectionJSON:JSON.stringify({task:{taskId}}),
    taskJSON:JSON.stringify({key:{taskId},payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[]}})}]}
  let retained=false,created=0,restored=0,done!:()=>void
  const recovered=new Promise<void>(r=>{done=r})
  const preparation={runPlannedController:async()=>{created++;retained=true;throw Error("lost response")},
    restorePlannedController:async()=>{restored++;done();return {phase:"reservation-held"}}}
  const frontier=new NativeDispatchFrontier({dispatchContext:async()=>frame} as never,preparation as never,{} as never,
    {read:async()=>retained?"retained":undefined} as never,"ses_controller",driverId,1,250)
  frontier.start()
  try {
    await Promise.race([recovered,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("recovery timeout")),2000))])
    assert.equal(created,1);assert.equal(restored,1)
  } finally {await frontier.close()}
})

test("MOCK frontier holds unknown outcome and retains one successor after definitive refusal",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const taskId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",reservation={operation:"reserve_assignment",payload:{assignmentId:driverId}}
  const plan={key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start}]}}
  const tip={key:{projectId,id:prep},payload:{preparationId:prep,reservation}}
  const frame={basis:{revision:1},planJSON:JSON.stringify(plan),attemptsJSON:[JSON.stringify(tip)],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,selectionJSON:JSON.stringify({task:{taskId}}),
    taskJSON:JSON.stringify({key:{taskId},payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[]}})}]}
  const records=new Map<string,string>([["preparation-driver"+prep,JSON.stringify({reserveJSON:JSON.stringify(reservation)})]])
  let eligibility="hold",checks=0,firstCheck!:()=>void,success!:()=>void
  const checked=new Promise<void>(r=>{firstCheck=r}),created=new Promise<void>(r=>{success=r})
  const launched:string[]=[]
  const preparation={restorePlannedController:async()=>({phase:"reservation-held"}),
    retryEligibilityController:async()=>{checks++;if(checks===1)firstCheck();return eligibility},
    runPlannedController:async(_selection:string,id:string,binding:any)=>{launched.push(id);assert.equal(binding.rootPreparationId,prep);success();return {phase:"reservation-held"}}}
  const custody={read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{records.set(n+k,v)}}
  const frontier=new NativeDispatchFrontier({dispatchContext:async()=>frame} as never,preparation as never,{} as never,custody as never,
    "ses_controller",driverId,1,250)
  frontier.start()
  try {
    await checked;assert.equal(launched.length,0);assert.equal(records.has("dispatch-successor"+prep),false)
    eligibility="definitive-refusal"
    await Promise.race([created,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("successor timeout")),2000))])
    const saved=JSON.parse(records.get("dispatch-successor"+prep)!)
    assert.equal(launched[0],saved.preparationId);assert.notEqual(saved.startId,saved.preparationId)
  } finally {await frontier.close()}
})

test("MOCK frontier restart retains successor identity after unknown publication",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const taskId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",reservation={operation:"reserve_assignment",payload:{assignmentId:driverId}}
  const plan={key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start}]}}
  const tip={key:{projectId,id:prep},payload:{preparationId:prep,reservation}}
  const frame={basis:{revision:1},planJSON:JSON.stringify(plan),attemptsJSON:[JSON.stringify(tip)],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,selectionJSON:JSON.stringify({task:{taskId}}),
    taskJSON:JSON.stringify({key:{taskId},payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[]}})}]}
  const records=new Map<string,string>([["preparation-driver"+prep,JSON.stringify({reserveJSON:JSON.stringify(reservation)})]])
  const custody={read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{
    const old=records.get(n+k);if(old!==undefined&&old!==v)throw Error("conflicting original");records.set(n+k,v)
  }}
  let first!:()=>void,second!:()=>void
  const unknown=new Promise<void>(r=>{first=r}),recovered=new Promise<void>(r=>{second=r}),ids:string[]=[]
  const preparation={retryEligibilityController:async()=>"definitive-refusal",
    restorePlannedController:async(id:string)=>{if(id===prep)return {phase:"reservation-held"};ids.push(id);second();return {phase:"attempt-binding-held"}},
    runPlannedController:async(_selection:string,id:string)=>{ids.push(id);records.set("preparation-driver"+id,"retained");first();throw Error("publication response lost")}}
  const make=()=>new NativeDispatchFrontier({dispatchContext:async()=>frame} as never,preparation as never,{} as never,custody as never,
    "ses_controller",driverId,1,250)
  const initial=make();initial.start();await unknown;await initial.close()
  const resumed=make();resumed.start()
  try {
    await Promise.race([recovered,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("restart timeout")),2000))])
    assert.equal(ids.length,2);assert.equal(ids[0],ids[1])
    assert.equal(ids[0],JSON.parse(records.get("dispatch-successor"+prep)!).preparationId)
  } finally {await resumed.close()}
})

test("MOCK frontier requests automatic closure and starts only the closed dependent",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const task={projectId,taskId:driverId,revision:0},next={...task,revision:1}
  const records=new Map<string,string>(),operations:string[]=[]
  let closed=false,staleClosure:string|undefined,started!:()=>void
  const done=new Promise<void>(r=>{started=r})
  const planJSON=JSON.stringify({key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start,futureDependencies:[{}]}]}})
  const frame=()=>({basis:{algorithm:"sha1",hex:"a".repeat(40)},planJSON,attemptsJSON:[],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,futureReady:true,claimsAvailable:true,selectionJSON:JSON.stringify({task}),
    taskJSON:JSON.stringify({key:task,payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[{selectedResult:null}]}}),
    closedJSON:closed?JSON.stringify({materializationJSON:"{}",closureJSON:"{}",manifestJSON:"{}",selectionJSON:JSON.stringify({task:next}),
      taskJSON:JSON.stringify({key:next,payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[{selectedResult:{id:driverId}}]}})}):null}]})
  const channel={scope:"controller",projectID:projectId,send:async(_route:string,raw:string)=>{
    const cmd=JSON.parse(raw);operations.push(cmd.operation)
    if(cmd.operation==="close_dispatch_inputs"){
      if(staleClosure===undefined){
        staleClosure=cmd.requestKey
        return JSON.stringify({kind:"settled",identity:{operation:cmd.operation,projectId,requestKey:cmd.requestKey},
          outcome:{kind:"rejected",domain:"work",code:"STALE_GENERATION"}})
      }
      assert.notEqual(cmd.requestKey,staleClosure)
      assert.deepEqual(cmd.payload.assessment.reference,cmd.payload.materialization)
      assert.equal(cmd.payload.expectedHead.hex,"a".repeat(40));closed=true
    }
    return JSON.stringify({kind:"settled",identity:{operation:cmd.operation,projectId,requestKey:cmd.requestKey},
      outcome:{kind:"success",result:{key:{projectId,id:cmd.requestKey},digest:"b".repeat(64)}}})
  }}
  const preparation={runPlannedController:async(raw:string)=>{
    assert.equal(closed,true);assert.deepEqual(JSON.parse(raw).task,next)
    return {phase:"binding-confirmed",state:"bound",currentIntent:null,assignmentSelector:{},binding:{}}
  }}
  const frontier=new NativeDispatchFrontier({dispatchContext:async()=>frame(),dispatchMaterializationChannel:async()=>channel} as never,
    preparation as never,{runController:async()=>{started();return {phase:"active"}}} as never,
    {read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{records.set(n+k,v)}} as never,
    "ses_controller",driverId,1,250)
  frontier.start()
  try {
    await Promise.race([done,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("dependent start timeout")),3000))])
    assert.deepEqual(operations,["materialize_dispatch_dependency","close_dispatch_inputs","materialize_dispatch_dependency","close_dispatch_inputs"])
    assert.ok(records.has("dispatch-closure"+prep))
  } finally {await frontier.close()}
})

test("MOCK frontier holds occupied claim and resumes only after it becomes free",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const taskId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",reservation={operation:"reserve_assignment",payload:{assignmentId:driverId}}
  const plan={key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start}]}}
  const tip={key:{projectId,id:prep},payload:{preparationId:prep,reservation}}
  let free=false,basis=0,checks=0,launched=0,done!:()=>void
  const resumed=new Promise<void>(r=>{done=r})
  const registry={dispatchContext:async()=>({basis:{revision:basis},planJSON:JSON.stringify(plan),attemptsJSON:[JSON.stringify(tip)],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,claimsAvailable:free,selectionJSON:JSON.stringify({task:{taskId}}),
    taskJSON:JSON.stringify({key:{taskId},payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[]}})}]})}
  const records=new Map<string,string>([["preparation-driver"+prep,JSON.stringify({reserveJSON:JSON.stringify(reservation)})]])
  const preparation={restorePlannedController:async()=>({phase:"reservation-held"}),retryEligibilityController:async()=>{checks++;return "definitive-refusal"},
    runPlannedController:async()=>{launched++;done();return {phase:"reservation-held"}}}
  const custody={read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{records.set(n+k,v)}}
  const frontier=new NativeDispatchFrontier(registry as never,preparation as never,{} as never,custody as never,"ses_controller",driverId,1,250)
  frontier.start()
  try {
    await new Promise(resolve=>setTimeout(resolve,600))
    assert.equal(checks,0);assert.equal(launched,0);assert.equal(records.has("dispatch-successor"+prep),false)
    free=true;basis++;frontier.wake()
    await Promise.race([resumed,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("free claim did not resume")),2000))])
    assert.equal(checks,1);assert.equal(launched,1)
  } finally {await frontier.close()}
})

test("MOCK frontier retains materialization command and never reserves an unchecked manifest",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const taskId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",task={projectId,taskId,revision:0}
  const plan={key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start,futureDependencies:[{}]}]}}
  let ready=false,done!:()=>void,first!:()=>void,sends=0
  const checked=new Promise<void>(r=>{first=r}),published=new Promise<void>(r=>{done=r})
  const records=new Map<string,string>(),requests:string[]=[]
  const frame=()=>({basis:{revision:1},planJSON:JSON.stringify(plan),attemptsJSON:[],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,futureReady:ready,selectionJSON:JSON.stringify({task}),
    taskJSON:JSON.stringify({key:task,payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[{selectedResult:null}]}})}]})
  const channel={scope:"controller",projectID:projectId,send:async(_route:string,raw:string)=>{
    requests.push(raw);sends++;if(sends<=3){if(sends===3)first();throw Error("MOCK lost publication reply")}
    const cmd=JSON.parse(raw);done();return JSON.stringify({kind:"settled",identity:{operation:cmd.operation,projectId,requestKey:cmd.requestKey},
      outcome:{kind:"success",result:{key:{projectId,id:cmd.requestKey}}}})
  }}
  const registry={dispatchContext:async()=>frame(),dispatchMaterializationChannel:async()=>channel}
  const custody={read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{records.set(n+k,v)}}
  const preparation={runPlannedController:async()=>{throw Error("unchecked manifest was reserved")}}
  const make=()=>new NativeDispatchFrontier(registry as never,preparation as never,{} as never,custody as never,"ses_controller",driverId,1,250)
  const initial=make();initial.start()
  await new Promise(resolve=>setTimeout(resolve,300));assert.equal(sends,0)
  ready=true;initial.wake()
  await Promise.race([checked,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("materialization timeout")),5000))])
  await initial.close()
  assert.equal(records.has("dispatch-materialization"+prep),true)
  ready=false // Reconcile the original even if a later head is no longer uniquely ready.
  const restored=make();restored.start()
  try {
    await Promise.race([published,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("recovery timeout")),2000))])
    assert.ok(requests.every(raw=>raw===requests[0]));assert.equal(requests.length,4)
  } finally {await restored.close()}
})

test("MOCK frontier dispatches the exact closed dependent successor",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const taskId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",oldTask={projectId,taskId,revision:0},nextTask={...oldTask,revision:1}
  const oldSelection={task:oldTask},nextSelection={task:nextTask,manifest:{id:"closed"}}
  const plan={key:{projectId,id:driverId},payload:{entries:[{preparationId:prep,startId:start,futureDependencies:[{}]}]}}
  const closedJSON=JSON.stringify({materializationJSON:"{}",closureJSON:"{}",taskJSON:JSON.stringify({key:nextTask,
    payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[{selectedResult:{id:"producer"}}]}}),
    manifestJSON:"{}",selectionJSON:JSON.stringify(nextSelection)})
  const frame={basis:{revision:1},planJSON:JSON.stringify(plan),attemptsJSON:[],activeAssignments:[],entries:[{
    preparationId:prep,startId:start,futureReady:true,claimsAvailable:true,closedJSON,selectionJSON:JSON.stringify(oldSelection),
    taskJSON:JSON.stringify({key:oldTask,payload:{kind:"implementation",state:"executable",unresolved:[],dependencies:[{selectedResult:null}]}})}]}
  let dispatched!:()=>void,seen=""
  const started=new Promise<void>(resolve=>{dispatched=resolve})
  const preparation={runPlannedController:async(selection:string)=>{seen=selection;dispatched();return {phase:"reservation-held"}}}
  const frontier=new NativeDispatchFrontier({dispatchContext:async()=>frame} as never,preparation as never,{} as never,
    {read:async()=>undefined} as never,"ses_controller",driverId,1,250)
  frontier.start()
  try {
    await Promise.race([started,new Promise<never>((_,reject)=>setTimeout(()=>reject(Error("dependent dispatch timeout")),2000))])
    assert.deepEqual(JSON.parse(seen),nextSelection)
  } finally {await frontier.close()}
})

test("controller start reuses the retained transition after a lost response",async()=>{
  const records=new Map<string,string>(),commands:string[]=[]
  const custody={initialize:async()=>{},read:async(n:string,k:string)=>records.get(n+k),retain:async(n:string,k:string,v:string)=>{
    const old=records.get(n+k);if(old!==undefined&&old!==v)throw Error("conflicting retained bytes");records.set(n+k,v)
  }}
  const key={projectId,id:driverId},binding={family:"assignment-bindings",key:{projectId,assignmentId:driverId,generation:0}}
  let lose=true,reads=0
  const channel={scope:"controller",projectID:projectId,send:async(route:string,raw:string)=>{
    if(route==="reads"){reads++;return JSON.stringify({kind:"read",data:{key,payload:{binding,state:"bound",currentIntent:null,profile:{projectId}}}})}
    commands.push(raw)
    if(lose)throw Error("MOCK response lost")
    return JSON.stringify({kind:"settled",outcome:{kind:"held"}})
  }}
  const registry={contextChannel:async()=>channel,sourceIdentity:async()=>{}}
  const deployment={read:async()=>"profile"}
  const driver=new NativeInitialStart(registry as never,{} as never,custody as never,deployment as never,"ses_controller",projectId)
  const selection=JSON.stringify({assignment:{family:"assignments",key,generation:0},binding})
  await assert.rejects(driver.runController(selection,driverId,signal))
  lose=false
  const restored=new NativeInitialStart(registry as never,{} as never,custody as never,deployment as never,"ses_controller",projectId)
  assert.equal((await restored.restoreController(driverId,signal)).phase,"start-intent-held")
  assert.equal(reads,1)
  assert.equal(commands.length,4)
  assert.ok(commands.every(raw=>raw===commands[0]))
  await assert.rejects(driver.runController(selection.replace('"generation":0','"generation":1'),driverId,signal))
  await assert.rejects(restored.restoreController("33333333-3333-4333-8333-333333333333",signal))
  assert.equal(commands.length,4)
})
