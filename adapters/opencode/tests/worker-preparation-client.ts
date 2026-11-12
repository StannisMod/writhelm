// SPDX-License-Identifier: AGPL-3.0-only
// REAL service/Git/custody/bidirectional HTTP; external OpenCode SDK/profile is an explicit MOCK.
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash, randomUUID } from "node:crypto"
import { execFileSync } from "node:child_process"
import type { Plugin } from "@opencode/plugin"
import type { OpenCodeClient } from "@opencode/client"
import { Schema } from "effect"
import { Tool } from "@opencode/schema/tool"
import { Session } from "@opencode/schema/session"
import { SessionMessage } from "@opencode/schema/session-message"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody, registerConnectedWorkTools } from "../src/connected-tools.js"
import { NativePassiveWorkers } from "../src/native-passive-worker.js"
import { LinuxGitWitness } from "../src/linux-git-witness.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { contextJSON } from "../src/native-context-codec.js"
import { exactJSON } from "../src/strict-json.js"
import { NativeWorkerPreparation, NativeDispatchFrontier } from "../src/native-worker-preparation.js"
import { NativeInitialAdapter } from "../src/native-initial.js"
import { NativeInitialStart } from "../src/native-initial-start.js"
import { sendContext } from "../src/native-context-protocol.js"
import { NativeResultRetention, NativeCandidateAssembly, NativeCandidateTests, runCandidateChecks, advanceCandidateDelivery } from "../src/native-result-retention.js"
import { resultSubmissionPreflight, loadCandidateDeliveryBootstrap, startCandidateDeliveryLoop } from "../src/native-runtime.js"

let input="";for await(const chunk of process.stdin){input+=chunk;assert.ok(Buffer.byteLength(input)<65536)}
const cfg=JSON.parse(input),sessionID="ses_worker_preparation_controller",sessions=new Map<string,any>(),inventory:Array<{directory:string;strategy:string}>=[]
let trees=0,receivers=0,lost=true,wakes=0
let releaseCreates!:()=>void
const bothCreating=new Promise<void>(resolve=>{releaseCreates=resolve})
const hooks=new Map<string,Array<(e:any)=>Promise<void>>>(),messages=new Map<string,any[]>(),instructions=new Map<string,Array<{key:string;value:string}>>()
const registeredTools=new Map<string,any>()
let boundary:Promise<void>|undefined,contextEvent:any
const hook=(domain:string)=>(name:string,callback:(e:any)=>Promise<void>)=>{
  const key=domain+":"+name;hooks.set(key,[...(hooks.get(key)??[]),callback]);return Promise.resolve({dispose:async()=>{}})
}
const invoke=async(name:string,e:any)=>{for(const callback of hooks.get(name)??[])await callback(e)}
const native={project:{list:async()=>[{id:"MOCK-native-project",canonical:cfg.sourceRoot,vcs:"git"}]},worktree:{list:async()=>inventory,
  create:async(i:any)=>{trees++;const directory=path.join(i.directory,i.name)
    execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-C",cfg.sourceRoot,"worktree","add","--detach",directory,i.branch],
      {env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},stdio:"pipe"})
    inventory.push({directory,strategy:"git"});return {directory}}},session:{
    get:async({sessionID:id}:{sessionID:string})=>{if(id===sessionID)return {id,location:{directory:cfg.sourceRoot}}
      const s=sessions.get(id);if(!s)throw {_tag:"SessionNotFoundError",sessionID:id,message:"MOCK missing receiver"};return s},
    create:async(i:any)=>{receivers++;const s={...i,projectID:"MOCK-native-project",cost:0,tokens:{input:0,output:0,reasoning:0,cache:{read:0,write:0}}}
      sessions.set(i.id,s)
      if(cfg.parallel&&!cfg.conflict){if(receivers===2)releaseCreates();await bothCreating}
      if(lost){lost=false;throw Error("MOCK lost original session-create reply")};return s},
    context:async({sessionID:id}:{sessionID:string})=>messages.get(id)??[],inbox:{list:async()=>[]},active:async()=>({}),prompt:async()=>{throw Error("no provider call in this fixture")}}} as unknown as OpenCodeClient
const ctx={app:{version:"2.2.5"},location:{directory:cfg.sourceRoot},session:{...native.session,hook:hook("session"),
  instructions:{entry:{list:async({sessionID:id}:any)=>instructions.get(id)??[],put:async({sessionID:id,key,value}:any)=>{
    instructions.set(id,[...(instructions.get(id)??[]).filter(p=>p.key!==key),{key,value}])}}},
  update:async({sessionID:id,permissions}:any)=>{sessions.get(id).permissions=permissions},
  prompt:async(i:any)=>{
    if(!cfg.start)throw Error("no initial wake allowed in passive-only case")
    await invoke("session:prompt",{sessionID:i.sessionID,messageID:i.id,prompt:{text:i.text},metadata:i.metadata,delivery:i.delivery})
    wakes++;messages.set(i.sessionID,[{id:i.id,type:"user",text:i.text,metadata:i.metadata,time:{created:1}}])
    contextEvent={sessionID:i.sessionID,model:sessions.get(i.sessionID).model,system:[],tools:{},options:{}}
    boundary=invoke("session:context",contextEvent);void boundary.catch(()=>{})
    // Deliberately lose the original normal-prompt reply AFTER its native wake was admitted.
    throw Error("MOCK lost normal prompt acknowledgement")
  }},tool:{hook:hook("tool"),transform:async(fn:(editor:any)=>void)=>{fn({add:(tool:any)=>registeredTools.set(tool.name,tool)});return {dispose:async()=>{}}},reload:async()=>{}}} as unknown as Plugin.Context
const custody=new LinuxNativeCustody(path.join(cfg.root,"native"),[cfg.sourceRoot,cfg.worktreeParent]);await custody.initialize()
const diagnosticFetch:typeof fetch=async(input,options)=>{
  const response=await fetch(input,options)
  if(!response.ok){
    const operation=typeof options?.body==="string"?JSON.parse(options.body).operation:undefined
    process.stderr.write(JSON.stringify({fixture:"native-http-refusal",operation,status:response.status})+"\n")
  }
  return response
}
const registry=new NativeCredentialRegistry(ctx,custody,new NativeRuntimeClient(cfg.origin,cfg.proof,cfg.storeId,cfg.projectId,cfg.controllerId,cfg.hostId,diagnosticFetch),cfg.origin,
  new LinuxWorkRequestCustody(path.join(cfg.root,"requests"),[cfg.sourceRoot,cfg.worktreeParent]),"a".repeat(64))
await registry.adopt(cfg.principalId,cfg.credentialId,cfg.token);await registry.bind(sessionID,cfg.credentialId,cfg.sourceRoot)
const gitWitness=new LinuxGitWitness({path:"/usr/bin/git",sha256:createHash("sha256").update(await fs.readFile("/usr/bin/git")).digest("hex")})
const workers=new NativePassiveWorkers(native,registry,custody,sessionID,{sourceRoot:cfg.sourceRoot,worktreeParent:cfg.worktreeParent,
  nativeProjectId:"MOCK-native-project",repositoryId:cfg.repositoryId,controllerId:cfg.controllerId,hostId:cfg.hostId,
  agents:{"executor-v1":"build","reviewer-v1":"plan"}},gitWitness)
const bridge=new ProtectedNativeBridge(registry,custody,sessionID,cfg.hostId)
const adapter=new ConnectedNativeAdapter(ctx,native.session,bridge,cfg.hostId)
const deployment=new LinuxNativeCustody(path.join(cfg.root,"deployment"),[cfg.sourceRoot,cfg.worktreeParent]);await deployment.initialize()
const initial=new NativeInitialAdapter(ctx,native,registry,custody,deployment,sessionID);await initial.register()
let workerSession:string|undefined
let retentionWrites=0
const retentionGit={qualifySource:gitWitness.qualifySource.bind(gitWitness),verifyRetainedRef:gitWitness.verifyRetainedRef.bind(gitWitness),
  pinRetainedRefOnce:async(...args:Parameters<LinuxGitWitness["pinRetainedRefOnce"]>)=>{retentionWrites++;await gitWitness.pinRetainedRefOnce(...args)}}
const authorizeRetention=async(request:Parameters<NativeResultRetention["retain"]>[0],abort:AbortSignal)=>
  workerSession!==undefined && registry.retentionPermit(workerSession,request,abort)
const observeRetention=async(request:Parameters<NativeResultRetention["retain"]>[0],abort:AbortSignal)=>
  workers.resultRetentionWorktree(request.assignmentId,request.generation,request.executionId,contextJSON(request.snapshot),abort)
const retention=new NativeResultRetention(cfg.sourceRoot,custody,retentionGit,authorizeRetention,observeRetention)
const transport=await listenNativeTransport(cfg.transportOrigin,Buffer.from(cfg.transportProof,"base64url"),bridge,adapter,undefined,workers,initial,undefined,retention)
const workTools=await registerConnectedWorkTools(ctx,registry,resultSubmissionPreflight(registry,custody,cfg.sourceRoot,()=>workers,()=>gitWitness))
let recoveredInitial:NativeInitialAdapter|undefined
const signal=new AbortController().signal,parse=(s:string)=>exactJSON(s,()=>{throw Error("invalid service JSON")})
try{
  const driver=new NativeWorkerPreparation(registry,workers,custody,sessionID,{projectId:cfg.projectId,controllerId:cfg.controllerId,
    hostId:cfg.hostId,worktreeParent:cfg.worktreeParent})
  if(cfg.frontierStart){
    const payload=parse(cfg.reserveJSON).payload
    await deployment.retain("execution-profile",contextJSON(payload.profile),contextJSON({formatVersion:1,profileKeyJSON:contextJSON(payload.profile),
      model:{providerID:"synthetic",id:"no-call"},baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{maxTokens:64,effort:"bounded"}}))
    const starter=new NativeInitialStart(registry,initial,custody,deployment,sessionID,cfg.projectId)
    let complete!:(value:any)=>void
    const active=new Promise<any>(resolve=>{complete=resolve})
    const checked={runController:async(...args:Parameters<NativeInitialStart["runController"]>)=>{
      const result=await starter.runController(...args);assert.equal(result.phase,"active-admission-confirmed");complete(result);return result
    },restoreController:starter.restoreController.bind(starter)}
    const frontier=new NativeDispatchFrontier(registry,driver,checked as never,custody,sessionID,cfg.planId,1,250)
    frontier.start()
    try {
      await Promise.race([active,new Promise<never>((_,reject)=>{const timer=setTimeout(()=>reject(Error("real frontier start timeout")),45000);timer.unref()})])
      await boundary
      assert.equal(wakes,1);assert.equal(contextEvent.options.maxTokens,64)
      const frame=await registry.dispatchContext(sessionID,cfg.planId,signal)
      assert.equal(frame.activeAssignments.length,1)
      const assignment=parse(frame.activeAssignments[0]);assert.equal(assignment.payload.state,"active")
      process.stdout.write(JSON.stringify({passiveAndBound:true,assignmentId:assignment.key.id,trees,receivers,wakes,native:"MOCK",gitAndService:"REAL"}))
    } finally {await frontier.close()}
  } else if(cfg.conflict){
    let complete!:()=>void
    const bound=new Promise<void>(r=>{complete=r})
    const starter={runController:async()=>{complete()},restoreController:async()=>{complete()}}
    const make=()=>new NativeDispatchFrontier(registry,driver,starter as never,custody,sessionID,cfg.planId,2,250)
    let frontier=make();frontier.start()
    const timeout=new Promise<never>((_,reject)=>{const timer=setTimeout(()=>reject(Error("conflict frontier timeout")),45000);timer.unref()})
    await Promise.race([bound,timeout])
    await new Promise(resolve=>setTimeout(resolve,2000));await frontier.close()
    const before=await registry.dispatchContext(sessionID,cfg.planId,signal)
    const attempts=before.attemptsJSON.map((raw:string)=>parse(raw))
    assert.equal(attempts.length,2);assert.equal(before.activeAssignments.length,1)
    const active=parse(before.activeAssignments[0])
    const loser=attempts.find((row:any)=>row.payload.reservation.payload.assignmentId!==active.key.id);assert.ok(loser)
    assert.equal(await registry.reservationRetryEligibility(sessionID,contextJSON(loser.payload.reservation),signal),"definitive-refusal")
    assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
    frontier=make();frontier.start();await new Promise(resolve=>setTimeout(resolve,2000));await frontier.close()
    const after=await registry.dispatchContext(sessionID,cfg.planId,signal)
    assert.equal(after.attemptsJSON.length,attempts.length);assert.equal(after.activeAssignments.length,1)
    assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
    process.stdout.write(JSON.stringify({passiveAndBound:true,assignmentId:active.key.id,trees,receivers,wakes,native:"MOCK",gitAndService:"REAL"}))
  } else if(cfg.parallel){
    const startedAssignments=new Set<string>()
    let complete!:()=>void
    const bound=new Promise<void>(r=>{complete=r})
    const starter={runController:async(raw:string)=>{
      startedAssignments.add(parse(raw).assignment.key.id)
      if(startedAssignments.size===2)complete()
    },restoreController:async()=>{throw Error("unexpected initial restore in passive parallel fixture")}}
    const make=()=>new NativeDispatchFrontier(registry,driver,starter as never,custody,sessionID,cfg.planId,2,250)
    let frontier=make();frontier.start()
    const timeout=new Promise<never>((_,reject)=>{const timer=setTimeout(()=>reject(Error("parallel frontier timeout")),45000);timer.unref()})
    try {await Promise.race([bound,timeout])} finally {await frontier.close()}
    assert.deepEqual({trees,receivers,wakes},{trees:2,receivers:2,wakes:0})
    assert.equal(new Set(inventory.map(x=>x.directory)).size,2)
    const frame=await registry.dispatchContext(sessionID,cfg.planId,signal)
    const attempts=frame.attemptsJSON.map((raw:string)=>parse(raw))
    assert.equal(attempts.length,2)
    const first=attempts.find((row:any)=>row.payload.preparationId===cfg.preparationId);assert.ok(first)
    frontier=make();frontier.start()
    await new Promise(resolve=>setTimeout(resolve,1200));await frontier.close()
    assert.deepEqual({trees,receivers,wakes},{trees:2,receivers:2,wakes:0})
    process.stdout.write(JSON.stringify({passiveAndBound:true,assignmentId:first.payload.reservation.payload.assignmentId,trees,receivers,wakes,native:"MOCK",gitAndService:"REAL"}))
  } else if(cfg.frontier){
    const timeout=(message:string)=>new Promise<never>((_,reject)=>{const timer=setTimeout(()=>reject(Error(message)),30000);timer.unref()})
    let losses=0,unknown!:()=>void,completed!:()=>void
    const lostReply=new Promise<void>(r=>{unknown=r}),bound=new Promise<void>(r=>{completed=r})
    const originalChannel=registry.dispatchAttemptChannel.bind(registry)
    registry.dispatchAttemptChannel=async(id:string)=>{
      const channel=await originalChannel(id)
      return {...channel,send:async(route,raw,abort)=>{
        const reply=await channel.send(route,raw,abort),command=JSON.parse(raw)
        if(command.payload.formatVersion===2&&losses<3){if(++losses===3)unknown();throw Error("MOCK lost successor publication reply")}
        return reply
      }}
    }
    const starter={runController:async()=>{completed()},restoreController:async()=>{completed()}}
    const make=()=>new NativeDispatchFrontier(registry,driver,starter as never,custody,sessionID,cfg.planId,1,250)
    let frontier=make();frontier.start()
    await Promise.race([lostReply,timeout("successor publication timeout")])
    await frontier.close()
    const saved=await custody.read("dispatch-successor",cfg.preparationId);assert.ok(saved)
    const successor=JSON.parse(saved)
    frontier=make();frontier.start()
    try {
      await Promise.race([bound,timeout("successor recovery timeout")])
    } finally {await frontier.close()}
    const frame=await registry.dispatchContext(sessionID,cfg.planId,signal)
    const attempts=frame.attemptsJSON.map((raw:string)=>parse(raw))
    assert.equal(attempts.length,2);assert.equal(attempts.filter((row:any)=>row.key.id===successor.preparationId).length,1)
    assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
    const job=parse((await custody.read("preparation-driver",successor.preparationId))!)
    process.stdout.write(JSON.stringify({passiveAndBound:true,assignmentId:parse(job.reserveJSON).payload.assignmentId,trees,receivers,wakes,native:"MOCK",gitAndService:"REAL"}))
  } else {
  const payload=parse(cfg.reserveJSON).payload
  const selection=contextJSON({task:payload.task,manifest:payload.manifest,profile:payload.profile,resources:payload.resources,reviewSubjects:[]})
  const actual={sessionID:Schema.decodeUnknownSync(Session.ID)(sessionID),messageID:Schema.decodeUnknownSync(SessionMessage.ID)("msg_worker_selection"),
    id:Schema.decodeUnknownSync(Tool.CallID)("call_worker_prepare"),signal}
  const planned={planRefJSON:cfg.planRefJSON as string}
  if(cfg.planned)await assert.rejects(driver.runPlannedController(selection,cfg.preparationId,planned,signal))
  else await assert.rejects(driver.run(selection,actual))
  const prepared=cfg.planned?await driver.restorePlannedController(cfg.preparationId,planned,signal):await driver.run(selection,actual)
  assert.equal(prepared.phase,"binding-confirmed",JSON.stringify(prepared))
  assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
  const replay=cfg.planned?await driver.restorePlannedController(cfg.preparationId,planned,signal):await driver.run(selection,actual,prepared.preparationId)
  assert.equal(replay.phase,"binding-confirmed");assert.equal(contextJSON(replay.binding),contextJSON(prepared.binding))
  assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
  assert.equal((await registry.inspect(prepared.nativeSessionId))?.scope,"assignment")
  workerSession=prepared.nativeSessionId
  assert.equal(wakes,0)
  let resultId:string|undefined
  if(cfg.start){
    await deployment.retain("execution-profile",contextJSON(payload.profile),contextJSON({formatVersion:1,profileKeyJSON:contextJSON(payload.profile),
      model:{providerID:"synthetic",id:"no-call"},baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{maxTokens:64,effort:"bounded"}}))
    const starter=new NativeInitialStart(registry,initial,custody,deployment,sessionID,cfg.projectId)
    const selected=contextJSON({assignment:prepared.assignmentSelector,binding:prepared.binding})
    const call={...actual,id:Schema.decodeUnknownSync(Tool.CallID)("call_initial_start")}
    const started=cfg.planned?await starter.runController(selected,cfg.startId,signal):await starter.run(selected,call)
    assert.equal(started.phase,"active-admission-confirmed");assert.equal(started.completionAsserted,false)
    await boundary;assert.equal(contextEvent.options.maxTokens,64);assert.equal(contextEvent.options.effort,"bounded")
    const restored=cfg.planned?await starter.restoreController(cfg.startId,signal):await starter.run(selected,call,started.startId)
    assert.equal(restored.phase,"active-admission-restored");assert.equal(wakes,1)
    await initial.close()
    hooks.clear() // Explicit MOCK host unload of the old plugin lifetime, not installed lifecycle evidence.
    recoveredInitial=new NativeInitialAdapter(ctx,native,registry,custody,deployment,sessionID);await recoveredInitial.register()
    const recovered=await recoveredInitial.restore()
    assert.equal(recovered.length,1);assert.equal(recovered[0]!.phase,"active-admission-restored")
    await recoveredInitial.admitTool(prepared.nativeSessionId)
    await invoke("tool:execute.before",{sessionID:prepared.nativeSessionId})
    assert.equal(wakes,1)
    if(cfg.diff){
      const directory=path.join(cfg.worktreeParent,prepared.workspaceId)
      const resultText="counter=1\n"+("escaped=\\\"value\\\\path\n".repeat(1000))
      await fs.writeFile(path.join(directory,"counter.txt"),resultText)
      const git=(...args:string[])=>execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-C",directory,...args],
        {env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},encoding:"utf8"}).trim()
      git("add","counter.txt");git("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","synthetic result")
      const dispatch=await registry.dispatchContext(sessionID,cfg.planId,signal)
      const task=parse(dispatch.entries.find((e:any)=>e.preparationId===cfg.preparationId).taskJSON)
      const active=dispatch.activeAssignments.map((raw:string)=>parse(raw)).find((a:any)=>a.key?.id===prepared.assignmentId)
      assert.equal(active?.payload?.state,"active")
      const base=task.payload.base.code[0]
      const result={...base,commit:{algorithm:"sha1",hex:git("rev-parse","HEAD")},tree:{algorithm:"sha1",hex:git("rev-parse","HEAD^{tree}")},retainedLocatorId:randomUUID()}
      const observed=await gitWitness.observeDiff(directory,cfg.sourceRoot,base,result,signal)
      const text=new TextDecoder("utf-8",{fatal:true}).decode(observed.bytes)
      const channel=await registry.contextChannel(prepared.nativeSessionId,signal),subject={assignmentId:prepared.assignmentId,generation:0}
      const retroKey={projectId:cfg.projectId,id:randomUUID()}
      const retrospective=await sendContext(channel,"commands",contextJSON({formatVersion:3,projectId:cfg.projectId,requestKey:randomUUID(),
        operation:"record_retrospective",payload:{key:retroKey,data:{task:payload.task,assignment:subject,result:null,outcome:"no-significant-new-findings",observations:[]},bodies:[]}}),signal)
      assert.equal(retrospective.outcome?.kind,"success",JSON.stringify(retrospective))
      const retro={family:"retrospectives",key:retrospective.outcome.result.key,digest:retrospective.outcome.result.digest}
      resultId=randomUUID()
      const submitJSON=(artifact=result)=>contextJSON({formatVersion:3,projectId:cfg.projectId,requestKey:randomUUID(),operation:"submit_result",
        payload:{key:{projectId:cfg.projectId,id:resultId},data:{task:payload.task,assignment:subject,manifest:payload.manifest,artifacts:[artifact],
          changes:{digest:observed.sha256,byteLength:observed.byteLength},claimedCriteria:[],evidence:[],retrospective:retro,pending:[]},
          bodies:[{digest:observed.sha256,body:text}]}})
      const submit=()=>sendContext(channel,"commands",submitJSON(),signal)
      if(cfg.holdRetention){
        const heldResult={...result,retainedLocatorId:randomUUID()},heldJSON=submitJSON(heldResult)
        const workerCall={sessionID:Schema.decodeUnknownSync(Session.ID)(prepared.nativeSessionId),messageID:Schema.decodeUnknownSync(SessionMessage.ID)("msg_hold_fixture"),
          id:Schema.decodeUnknownSync(Tool.CallID)("call_hold_fixture"),signal}
        const tool=registeredTools.get("writhelm_work_command")!
        assert.equal(await registry.originalResultReceipt(prepared.nativeSessionId,heldJSON,signal),"missing")
        assert.equal(JSON.parse((await tool.execute({json:heldJSON},workerCall)).content).kind,"error")
        assert.equal(JSON.parse((await tool.execute({json:heldJSON},workerCall)).content).kind,"error")
        assert.equal(await registry.originalResultReceipt(prepared.nativeSessionId,heldJSON,signal),"missing")
        assert.throws(()=>git("rev-parse","--verify",`refs/writhelm/results/${heldResult.retainedLocatorId}`))
        assert.equal(retentionWrites,0)
      }else{
      const missing=await submit();assert.equal(missing.outcome?.kind,"rejected",JSON.stringify(missing))
      await assert.rejects(registry.retentionPermit(prepared.nativeSessionId,{assignmentId:prepared.assignmentId,generation:"0",executionId:randomUUID(),snapshot:result},signal))
      await assert.rejects(registry.retentionPermit(prepared.nativeSessionId,{assignmentId:prepared.assignmentId,generation:"0",executionId:active.payload.intendedExecution.executionId,
        snapshot:{...result,repositoryId:randomUUID()}},signal))
      await assert.rejects(registry.retentionPermit(sessionID,{assignmentId:prepared.assignmentId,generation:"0",executionId:active.payload.intendedExecution.executionId,snapshot:result},signal))
      const original={assignmentId:prepared.assignmentId,generation:"0",executionId:active.payload.intendedExecution.executionId,snapshot:result}
      await fs.writeFile(path.join(directory,"counter.txt"),"tampered before pin\n")
      await assert.rejects(retention.retain(original,signal))
      assert.equal(retentionWrites,0)
      await fs.writeFile(path.join(directory,"counter.txt"),resultText)
      await assert.rejects((async()=>{await retention.retain(original,signal);throw Error("MOCK lost completed reply")})())
      const reopenedCustody=new LinuxNativeCustody(path.join(cfg.root,"native"),[cfg.sourceRoot,cfg.worktreeParent]);await reopenedCustody.initialize()
      const reopenedRetention=new NativeResultRetention(cfg.sourceRoot,reopenedCustody,retentionGit,authorizeRetention,observeRetention)
      await reopenedRetention.retain(original,signal)
      assert.equal(retentionWrites,1) // exact original receipt recovered; no second Git effect
      await assert.rejects(reopenedRetention.retain({...original,executionId:randomUUID()},signal))
      await fs.writeFile(path.join(directory,"counter.txt"),"tampered\n")
      const rejected=await submit()
      assert.equal(rejected.outcome?.kind,"rejected",JSON.stringify(rejected))
      assert.equal(rejected.outcome?.code,"INVALID_SUBJECT")
      await fs.writeFile(path.join(directory,"counter.txt"),resultText)
      const toolResult={...result,retainedLocatorId:randomUUID()}
      const finalJSON=submitJSON(toolResult)
      assert.ok(Buffer.byteLength(finalJSON)>8192)
      assert.ok(Buffer.byteLength(JSON.stringify(finalJSON))>Buffer.byteLength(finalJSON)) // escaped nested command
      assert.equal(await registry.originalResultReceipt(prepared.nativeSessionId,finalJSON,signal),"missing")
      const workerCall={sessionID:Schema.decodeUnknownSync(Session.ID)(prepared.nativeSessionId),messageID:Schema.decodeUnknownSync(SessionMessage.ID)("msg_result_fixture"),
        id:Schema.decodeUnknownSync(Tool.CallID)("call_result_fixture"),signal}
      const tool=registeredTools.get("writhelm_work_command")!
      const heldJSON=submitJSON()
      await fs.writeFile(path.join(directory,"counter.txt"),"dirty preflight\n")
      assert.equal(JSON.parse((await tool.execute({json:heldJSON},workerCall)).content).kind,"error")
      await fs.writeFile(path.join(directory,"counter.txt"),resultText)
      assert.equal(JSON.parse((await tool.execute({json:heldJSON},workerCall)).content).kind,"error")
      assert.equal(await registry.originalResultReceipt(prepared.nativeSessionId,heldJSON,signal),"missing")
      const submitted=JSON.parse((await tool.execute({json:finalJSON},workerCall)).content)
      assert.equal(submitted.outcome?.kind,"success",JSON.stringify(submitted))
      assert.equal(git("rev-parse",`refs/writhelm/results/${toolResult.retainedLocatorId}`),toolResult.commit.hex)
      const replay=JSON.parse((await tool.execute({json:finalJSON},workerCall)).content)
      assert.equal(replay.outcome?.kind,"success");assert.equal(replay.commandReplayed,true)
      assert.equal(await registry.originalResultReceipt(prepared.nativeSessionId,finalJSON,signal),"match")
      const changed=JSON.parse(finalJSON);changed.payload.data.claimedCriteria=[randomUUID()]
      assert.equal(await registry.originalResultReceipt(prepared.nativeSessionId,JSON.stringify(changed),signal),"hold")
      assert.equal(JSON.parse((await tool.execute({json:JSON.stringify(changed)},workerCall)).content).kind,"error")
       await assert.rejects(registry.originalResultReceipt(sessionID,finalJSON,signal))
       if(cfg.dependent){
         const candidateId=randomUUID(),operationId=randomUUID()
         const gitInput=(input:string,...args:string[])=>execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-C",directory,...args],
           {input,env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},encoding:"utf8"}).trim()
         const unrelatedBlob=gitInput("preserve unrelated candidate work\n","hash-object","-w","--stdin")
         const candidateTree=gitInput(git("ls-tree",base.commit.hex)+`\n100644 blob ${unrelatedBlob}\tunrelated.txt\n`,"mktree")
         const candidateHead=git("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit-tree",candidateTree,"-p",base.commit.hex,"-m","unrelated candidate work")
         const head={...base,commit:{algorithm:"sha1" as const,hex:candidateHead},tree:{algorithm:"sha1" as const,hex:candidateTree}}
         git("update-ref",`refs/heads/writhelm-candidate-${candidateId}`,candidateHead)
         await fs.writeFile(path.join(cfg.sourceRoot,"untracked-owner.txt"),"preserve local work\n")
         const selectedJSON=contextJSON([{family:"results",key:submitted.outcome.result.key,digest:submitted.outcome.result.digest}])
         const request={operationId,candidateId,head,inputs:[{base,result:toolResult}],time:"1794390000 +0000",selectedJSON}
         let candidateWrites=0
         const effects={buildCandidate:gitWitness.buildCandidate.bind(gitWitness),qualifySource:gitWitness.qualifySource.bind(gitWitness),
           publishCandidateOnce:async(...args:Parameters<LinuxGitWitness["publishCandidateOnce"]>)=>{candidateWrites++;await gitWitness.publishCandidateOnce(...args)}}
         const assembly=new NativeCandidateAssembly(cfg.sourceRoot,custody,effects,
           (input,abort)=>registry.candidateAssemblyPermit(sessionID,JSON.stringify(input),abort))
         const candidate=await assembly.assemble(request,signal)
         assert.equal(git("rev-parse",`refs/heads/writhelm-candidate-${candidateId}`),candidate.commit.hex)
         assert.equal(git("show",`${candidate.commit.hex}:counter.txt`),resultText.trim())
         assert.equal(git("show",`${candidate.commit.hex}:unrelated.txt`),"preserve unrelated candidate work")
         assert.equal(await fs.readFile(path.join(cfg.sourceRoot,"untracked-owner.txt"),"utf8"),"preserve local work\n")
         assert.equal(execFileSync("/usr/bin/git",["-C",cfg.sourceRoot,"rev-parse","HEAD"],{encoding:"utf8"}).trim(),base.commit.hex)
         assert.deepEqual(await assembly.assemble(request,signal),candidate);assert.equal(candidateWrites,1)
         let checkExecutions=0
         const checkCommand=[process.execPath,"-e","process.stdout.write('fixture test output\\n'); process.exit(Number(process.env.FIXTURE_EXIT))"]
         const checks=[{checkId:"candidate-content",command:checkCommand,safeRepeat:true,dependencies:[]},
           {checkId:"preserved-unrelated-content",command:["/usr/bin/git","show",`${candidate.commit.hex}:unrelated.txt`],safeRepeat:false,dependencies:["candidate-content"]}]
         const jobs=new NativeCandidateTests(custody,async(job,abort)=>{
           assert.deepEqual(job.candidate,candidate)
           const planned=checks.find(check=>check.checkId===job.checkId);assert.ok(planned)
           assert.deepEqual(job.command,planned.command);assert.equal(job.safeRepeat,planned.safeRepeat)
           return registry.candidateAssemblyPermit(sessionID,JSON.stringify(request),abort)
         },async(job)=>{
           await gitWitness.qualifySource(cfg.sourceRoot,{...head,...job.candidate},signal)
           assert.equal(git("show",`${job.candidate.commit.hex}:counter.txt`),resultText.trim())
           const exit=checkExecutions++===0?"1":"0"
           try {
             const output=execFileSync(job.command[0]!,job.command.slice(1),{cwd:cfg.sourceRoot,
               env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1",FIXTURE_EXIT:exit},encoding:"utf8"})
             return {outcome:"passed",output:`FIXTURE_EXIT=${exit}\n${output}`}
           }catch(error:any){return {outcome:"failed",output:`FIXTURE_EXIT=${exit}\n${String(error.stdout??"")}`}}
         })
         assert.equal(await jobs.readyForReview(candidate,randomUUID()),false)
         const testPlanId=randomUUID(),tested=await runCandidateChecks(custody,jobs,testPlanId,candidate,checks,2,signal)
         assert.equal(tested.ready,true);assert.equal(checkExecutions,3)
         const savedPlan=JSON.parse((await custody.read("candidate-test-plan",testPlanId))!)
         const firstRun=savedPlan.runs.find((run:any)=>run.checkId==="candidate-content")
         assert.equal(JSON.parse((await custody.read("candidate-test-result",firstRun.runId))!).result.outcome,"failed")
         assert.equal(JSON.parse((await custody.read("candidate-test-result",firstRun.retryId))!).result.outcome,"passed")
         assert.equal((await runCandidateChecks(custody,jobs,testPlanId,candidate,checks,2,signal)).ready,true)
         assert.equal(checkExecutions,3)
         await assert.rejects(assembly.assemble({...request,operationId:randomUUID(),inputs:[{base,result:{...toolResult,retainedLocatorId:randomUUID()}}]},signal))
         assert.equal(candidateWrites,1)
         const conflictBlob=gitInput("counter=conflict\n","hash-object","-w","--stdin")
         const conflictTree=gitInput(`100644 blob ${conflictBlob}\tcounter.txt\n`,"mktree")
         const conflictCommit=git("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit-tree",conflictTree,"-p",base.commit.hex,"-m","conflicting candidate")
         const conflictId=randomUUID(),conflicting={...request,operationId:randomUUID(),candidateId:conflictId,
           head:{...base,commit:{algorithm:"sha1" as const,hex:conflictCommit},tree:{algorithm:"sha1" as const,hex:conflictTree}}}
         git("update-ref",`refs/heads/writhelm-candidate-${conflictId}`,conflictCommit)
         await assert.rejects(assembly.assemble(conflicting,signal))
         assert.equal(git("rev-parse",`refs/heads/writhelm-candidate-${conflictId}`),conflictCommit);assert.equal(candidateWrites,1)
         const unknown={...request,operationId:randomUUID(),candidateId:randomUUID()}
         git("update-ref",`refs/heads/writhelm-candidate-${unknown.candidateId}`,head.commit.hex)
         const lost=new NativeCandidateAssembly(cfg.sourceRoot,custody,{...effects,publishCandidateOnce:async(...args:Parameters<LinuxGitWitness["publishCandidateOnce"]>)=>{
           await effects.publishCandidateOnce(...args);throw Error("MOCK lost ref-update acknowledgement")
         }},(input,abort)=>registry.candidateAssemblyPermit(sessionID,JSON.stringify(input),abort))
         await assert.rejects(lost.assemble(unknown,signal));assert.equal(candidateWrites,2)
         await assert.rejects(assembly.assemble(unknown,signal));assert.equal(candidateWrites,2)
         let lastStart:unknown
         const newlyActive=new Set<string>()
         const starter=new NativeInitialStart(registry,recoveredInitial??initial,custody,deployment,sessionID,cfg.projectId)
         let complete!:()=>void
         const done=new Promise<void>(resolve=>{complete=resolve})
         const checked={runController:async(...args:Parameters<NativeInitialStart["runController"]>)=>{
           const result=await starter.runController(...args).catch(error=>{process.stderr.write(String(error?.stack??error)+"\n");throw error})
           lastStart=result
           assert.equal(result.phase,"active-admission-confirmed");newlyActive.add(result.assignmentId)
           if(newlyActive.size===2)complete();return result
         },restoreController:async(...args:Parameters<NativeInitialStart["restoreController"]>)=>{
           const result=await starter.restoreController(...args);if(args[0]!==cfg.startId)lastStart=result
           if(args[0]!==cfg.startId&&result.phase==="active-admission-restored"){
             newlyActive.add(result.assignmentId);if(newlyActive.size===2)complete()
           }
           return result
         }}
         const frontier=new NativeDispatchFrontier(registry,driver,checked as never,custody,sessionID,cfg.planId,3,1000)
         frontier.start()
         try {
           await Promise.race([done,new Promise<never>((_,reject)=>{const timer=setTimeout(()=>reject(Error("dependent successor timeout: "+String(frontier.lastFailure)+" start="+JSON.stringify(lastStart))),45000);timer.unref()})])
           await boundary
           const current=await registry.dispatchContext(sessionID,cfg.planId,signal)
           const closed=current.entries.find((entry:any)=>entry.closedJSON!==null);assert.ok(closed)
           const selection=parse(parse(closed.closedJSON).selectionJSON)
           const child=current.activeAssignments.map((raw:string)=>parse(raw)).find((row:any)=>row.payload.task.taskId===selection.task.taskId)
           assert.ok(child);assert.equal(child.payload.state,"active")
           assert.equal(child.payload.task.revision.value,"1");assert.equal(wakes,3)
           const slow=current.activeAssignments.map((raw:string)=>parse(raw)).find((row:any)=>row.key.id!==child.key.id&&row.key.id!==prepared.assignmentId)
           assert.ok(slow);assert.equal(slow.payload.state,"active")
            assert.equal(trees,3);assert.equal(new Set(inventory.map(x=>x.directory)).size,3)
            await frontier.close() // all scheduled starts are confirmed; do not keep a 250ms fixture poll racing submissions
            assert.equal(await registry.candidateDelivery(sessionID,cfg.planRefJSON,signal),null)
            const selectedResults=[{family:"results",key:submitted.outcome.result.key,digest:submitted.outcome.result.digest}]
            const mergeInputs=[{base,result:toolResult}]
            for(const [index,assignment] of [child,slow].entries()){
              const worktree=path.join(cfg.worktreeParent,assignment.payload.workspaceId)
              const localGit=(...args:string[])=>execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-C",worktree,...args],
                {env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},encoding:"utf8"}).trim()
              const filename=index===0?"dependent.txt":"independent.txt"
              await fs.writeFile(path.join(worktree,filename),`completed ${filename}\n`)
              localGit("add",filename);localGit("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m",`complete ${filename}`)
              const artifact={...base,commit:{algorithm:"sha1" as const,hex:localGit("rev-parse","HEAD")},
                tree:{algorithm:"sha1" as const,hex:localGit("rev-parse","HEAD^{tree}")},retainedLocatorId:randomUUID()}
              const diff=await gitWitness.observeDiff(worktree,cfg.sourceRoot,base,artifact,signal)
              workerSession=assignment.payload.intendedExecution.nativeSessionId
              const workerChannel=await registry.contextChannel(workerSession!,signal)
              const subject={assignmentId:assignment.key.id,generation:0}
              const retrospective=await sendContext(workerChannel,"commands",contextJSON({formatVersion:3,projectId:cfg.projectId,requestKey:randomUUID(),
                operation:"record_retrospective",payload:{key:{projectId:cfg.projectId,id:randomUUID()},
                  data:{task:assignment.payload.task,assignment:subject,result:null,outcome:"no-significant-new-findings",observations:[]},bodies:[]}}),signal)
              assert.equal(retrospective.outcome?.kind,"success")
              const command=contextJSON({formatVersion:3,projectId:cfg.projectId,requestKey:randomUUID(),operation:"submit_result",
                payload:{key:{projectId:cfg.projectId,id:randomUUID()},data:{task:assignment.payload.task,assignment:subject,manifest:assignment.payload.manifest,
                  artifacts:[artifact],changes:{digest:diff.sha256,byteLength:diff.byteLength},claimedCriteria:[],evidence:[],
                  retrospective:{family:"retrospectives",key:retrospective.outcome.result.key,digest:retrospective.outcome.result.digest},pending:[]},
                  bodies:[{digest:diff.sha256,body:diff.bytes.toString("utf8")}]}})
              const call={sessionID:Schema.decodeUnknownSync(Session.ID)(workerSession!),messageID:Schema.decodeUnknownSync(SessionMessage.ID)(`msg_final_${index}`),
                id:Schema.decodeUnknownSync(Tool.CallID)(`call_final_${index}`),signal}
              const reply=JSON.parse((await tool.execute({json:command},call)).content)
              assert.equal(reply.outcome?.kind,"success",JSON.stringify(reply))
              selectedResults.push({family:"results",key:reply.outcome.result.key,digest:reply.outcome.result.digest})
              mergeInputs.push({base,result:artifact})
            }
            const finalRequest={...request,operationId:randomUUID(),candidateId:randomUUID(),inputs:mergeInputs,selectedJSON:contextJSON(selectedResults)}
            git("update-ref",`refs/heads/writhelm-candidate-${finalRequest.candidateId}`,head.commit.hex)
            const finalCandidate=await assembly.assemble(finalRequest,signal)
            for(const filename of ["dependent.txt","independent.txt","unrelated.txt"])
              assert.ok(git("show",`${finalCandidate.commit.hex}:${filename}`).length)
            assert.equal(git("show",`${finalCandidate.commit.hex}:counter.txt`),resultText.trim())
            assert.equal(await jobs.readyForReview(finalCandidate,testPlanId),false)
            let finalExecutions=0
            const finalChecks=(await registry.candidateTestPlan(sessionID,cfg.planRefJSON,signal)).map(check=>({...check,
              command:check.command.map(argument=>argument.replaceAll("{candidateCommit}",finalCandidate.commit.hex).replaceAll("{candidateTree}",finalCandidate.tree.hex))}))
            await assert.rejects(registry.candidateTestPermit(sessionID,JSON.stringify(finalRequest),cfg.planRefJSON,
              JSON.stringify({runId:randomUUID(),candidate:{commit:head.commit,tree:head.tree},checkId:finalChecks[0]!.checkId,
                command:finalChecks[0]!.command,safeRepeat:finalChecks[0]!.safeRepeat,previousRunId:null}),signal))
            const finalJobs=new NativeCandidateTests(custody,async(job,abort)=>{
              assert.deepEqual(job.candidate,finalCandidate)
              const planned=finalChecks.find(check=>check.checkId===job.checkId);assert.ok(planned);assert.deepEqual(job.command,planned.command)
              return registry.candidateTestPermit(sessionID,JSON.stringify(finalRequest),cfg.planRefJSON,JSON.stringify(job),abort)
            },async(job)=>{
              await gitWitness.qualifySource(cfg.sourceRoot,{...head,...job.candidate},signal)
              const exit=finalExecutions++===0?"1":"0"
              try {return {outcome:"passed",output:execFileSync(job.command[0]!,job.command.slice(1),{cwd:cfg.sourceRoot,
                env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1",FIXTURE_EXIT:exit},encoding:"utf8"})}}
              catch(error:any){return {outcome:"failed",output:`FIXTURE_EXIT=${exit}\n${String(error.stdout??"")}`}}
            })
            const finalPlanId=randomUUID(),finalTests=await runCandidateChecks(custody,finalJobs,finalPlanId,finalCandidate,finalChecks,2,signal)
            assert.equal(finalTests.ready,true);assert.equal(finalExecutions,3)
            const finalPlan=JSON.parse((await custody.read("candidate-test-plan",finalPlanId))!)
            const finalFirst=finalPlan.runs.find((run:any)=>run.checkId==="whole-candidate")
            assert.equal(JSON.parse((await custody.read("candidate-test-result",finalFirst.runId))!).result.outcome,"failed")
            assert.equal(JSON.parse((await custody.read("candidate-test-result",finalFirst.retryId))!).result.outcome,"passed")
            assert.equal((await runCandidateChecks(custody,finalJobs,finalPlanId,finalCandidate,finalChecks,2,signal)).ready,true)
            assert.equal(finalExecutions,3)
            const ordinaryProfile={planRefJSON:cfg.planRefJSON as string,operationId:randomUUID(),candidateId:randomUUID(),head,
              time:"1794390000 +0000",testPlanId:randomUUID(),capacity:2}
            git("update-ref",`refs/heads/writhelm-candidate-${ordinaryProfile.candidateId}`,head.commit.hex)
            let ordinaryExecutions=0
            const executeOrdinary=async(job:Parameters<ConstructorParameters<typeof NativeCandidateTests>[2]>[0])=>{
              const exit=ordinaryExecutions++===0?"1":"0"
              try {return {outcome:"passed" as const,output:execFileSync(job.command[0]!,job.command.slice(1),{cwd:cfg.sourceRoot,
                env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1",FIXTURE_EXIT:exit},encoding:"utf8"})}}
              catch(error:any){return {outcome:"failed" as const,output:String(error.stdout??"")}}
            }
            const ordinary=await advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,ordinaryProfile,executeOrdinary,signal)
            assert.equal(ordinary.phase,"ready-for-review");assert.equal(ordinaryExecutions,3)
            assert.equal((await advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,ordinaryProfile,executeOrdinary,signal)).phase,"ready-for-review")
            assert.equal(ordinaryExecutions,3)
            let reviewProcesses=0
            const reviewerPrincipalId=randomUUID()
            const makeReviewer=(blocking:boolean)=>({reviewId:randomUUID(),contextId:randomUUID(),reviewerPrincipalId,
              execute:async(task:import("../src/native-result-retention.js").CandidateReviewTask)=>{
                assert.equal(task.access,"immutable-snapshot-read-only");assert.notEqual(task.reviewerPrincipalId,cfg.principalId)
                assert.ok(Object.isFrozen(task));assert.ok(Object.isFrozen(task.candidate))
                const files=Object.fromEntries(["counter.txt","dependent.txt","independent.txt","unrelated.txt"].map(name=>[name,git("show",`${task.candidate.commit.hex}:${name}`)]))
                const script=`const fs=require('node:fs');const x=JSON.parse(fs.readFileSync(0,'utf8'));
                  if(Object.keys(x.files).length!==4)throw Error('incomplete read-only snapshot');
                  process.stdout.write(JSON.stringify({reviewId:x.task.reviewId,contextId:x.task.contextId,
                    reviewerPrincipalId:x.task.reviewerPrincipalId,candidate:x.task.candidate,
                    summary:'MOCK semantic reviewer; separate process '+process.pid+'; actual immutable snapshot blobs received',
                    findings:x.blocking?[{severity:'blocking',requirement:'H1-09',scenario:'explicit model-free injected review finding',
                      expected:'candidate conforms',actual:'MOCK blocking finding',evidence:'bound candidate '+x.task.candidate.commit.hex}]:[]}));`
                reviewProcesses++
                return JSON.parse(execFileSync(process.execPath,["-e",script],{input:JSON.stringify({task,files,blocking}),env:{},encoding:"utf8"}))
              }})
            const author=makeReviewer(false)
            const reviewSelection=await registry.candidateDelivery(sessionID,cfg.planRefJSON,signal);assert.ok(reviewSelection)
            for(const excluded of reviewSelection.excludedReviewers)
              await assert.rejects(advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,ordinaryProfile,executeOrdinary,signal,
                {...author,reviewerPrincipalId:excluded}))
            assert.equal(reviewProcesses,0)
            const reviewer=makeReviewer(false)
            const reviewed=await advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,ordinaryProfile,executeOrdinary,signal,reviewer)
            assert.equal(reviewed.phase,"review-complete");assert.equal(reviewed.integrationAllowed,false);assert.equal(reviewProcesses,1)
            assert.equal(git("rev-parse",`refs/heads/writhelm-candidate-${ordinaryProfile.candidateId}`),reviewed.candidate!.commit.hex)
            assert.equal(execFileSync("/usr/bin/git",["-C",cfg.sourceRoot,"rev-parse","HEAD"],{encoding:"utf8"}).trim(),base.commit.hex)
            assert.equal((await advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,ordinaryProfile,executeOrdinary,signal,reviewer)).phase,"review-complete")
            assert.equal(reviewProcesses,1);assert.equal(ordinaryExecutions,3)
            const largeRequest={...finalRequest,inputs:Array.from({length:32},()=>mergeInputs[0]),
              selectedJSON:contextJSON(Array.from({length:32},()=>selectedResults[0]))}
            const largeBody=JSON.stringify({formatVersion:1,operation:"candidate-assembly-permit",projectId:cfg.projectId,requestJSON:JSON.stringify(largeRequest)})
            assert.ok(Buffer.byteLength(largeBody)>16384)
            const ingressDeadline=AbortSignal.timeout(10000)
            const readLarge=()=>fetch(cfg.origin+"/internal/native/candidate/v1",{method:"POST",headers:{"Content-Type":"application/json",
              Authorization:"Bearer "+cfg.token,"X-Writhelm-Native-Proof":cfg.proof},body:largeBody,signal:ingressDeadline})
            let bounded=await readLarge()
            for(let retry=0;bounded.status===429&&retry<8;retry++){
              await bounded.body?.cancel();await new Promise(resolve=>setTimeout(resolve,500+Math.floor(Math.random()*250)));bounded=await readLarge()
            }
            assert.equal(bounded.status,403) // decoded at the larger bound, then refused for duplicate subjects
            await bounded.body?.cancel()
            const bootstrapPlanId=randomUUID()
            const bootstrapEntries=current.entries.map((entry:any)=>{
              const selected=parse(entry.closedJSON===null?entry.selectionJSON:parse(entry.closedJSON).selectionJSON)
              return {task:selected.task,manifest:selected.manifest,profile:selected.profile,resources:selected.resources.map((resource:any)=>resource.resource),
                preparationId:randomUUID(),startId:randomUUID(),futureDependencies:[]}
            })
            const bootstrapChecks=[{checkId:"counter-bytes",command:["immutable-git-file-sha256-v1","counter.txt",createHash("sha256").update(resultText).digest("hex")],safeRepeat:true,dependencies:[]},
              {checkId:"independent-bytes",command:["immutable-git-file-sha256-v1","independent.txt",createHash("sha256").update("completed independent.txt\n").digest("hex")],safeRepeat:true,dependencies:["counter-bytes"]}]
            const bootstrapPlan=await fetch(cfg.origin+"/api/v1/commands",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+cfg.token},
              body:contextJSON({formatVersion:3,projectId:cfg.projectId,requestKey:randomUUID(),operation:"record_dispatch_plan",
                payload:{formatVersion:3,projectId:cfg.projectId,planId:bootstrapPlanId,controllerId:cfg.controllerId,entries:bootstrapEntries,candidateChecks:bootstrapChecks}})})
            const publishedPlan=await bootstrapPlan.json() as any
            assert.equal(publishedPlan.outcome?.kind,"success",JSON.stringify(publishedPlan))
            const bootstrapPlanRef=contextJSON({family:"dispatch-plans",key:publishedPlan.outcome.result.key,digest:publishedPlan.outcome.result.digest})
            const bootstrapProfile={...ordinaryProfile,planRefJSON:bootstrapPlanRef,operationId:randomUUID(),candidateId:randomUUID(),testPlanId:randomUUID()}
            git("update-ref",`refs/heads/writhelm-candidate-${bootstrapProfile.candidateId}`,head.commit.hex)
            const bootstrapStore=new LinuxNativeCustody(path.join(cfg.root,"delivery-bootstrap"),[cfg.sourceRoot]);await bootstrapStore.initialize()
            assert.equal(await loadCandidateDeliveryBootstrap(bootstrapStore,cfg.sourceRoot),undefined)
            await bootstrapStore.retain("candidate-delivery-bootstrap","current",contextJSON({formatVersion:1,profile:bootstrapProfile,pollMillis:1000,
              executor:{kind:"linux-immutable-git-v1",git:{path:"/usr/bin/git",sha256:createHash("sha256").update(await fs.readFile("/usr/bin/git")).digest("hex")}}}))
            const loaded=await loadCandidateDeliveryBootstrap(bootstrapStore,cfg.sourceRoot);assert.ok(loaded)
            const blocker=makeReviewer(true)
            const bootstrapLoop=startCandidateDeliveryLoop(cfg.sourceRoot,custody,gitWitness,registry,sessionID,{...loaded,review:blocker})
            try {
              const blocked=await bootstrapLoop.initial
              assert.equal(blocked.phase,"review-blocked");assert.equal(blocked.integrationAllowed,false)
              const savedReview=JSON.parse((await custody.read("candidate-review-report",blocker.reviewId))!)
              assert.equal(savedReview.report.findings[0].severity,"blocking")
              await assert.rejects(advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,bootstrapProfile,loaded.execute,signal,
                {...blocker,reviewId:randomUUID(),contextId:randomUUID()})) // a renamed review cannot erase this candidate's blocker
              await assert.rejects(advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,
                {...bootstrapProfile,planRefJSON:JSON.stringify(JSON.parse(bootstrapProfile.planRefJSON),null,2)},loaded.execute,signal,
                {...blocker,reviewId:randomUUID(),contextId:randomUUID()}))
              assert.equal((await advanceCandidateDelivery(cfg.sourceRoot,custody,gitWitness,registry,sessionID,bootstrapProfile,loaded.execute,signal,blocker)).phase,"review-blocked")
            }
            finally {await bootstrapLoop.close()}
          } finally {await frontier.close()}
       }
       }
    }
  }
  process.stdout.write(JSON.stringify({passiveAndBound:true,assignmentId:prepared.assignmentId,resultId,trees,receivers,wakes,native:"MOCK",gitAndService:"REAL"}))
  }
}finally{await workTools.dispose();await recoveredInitial?.close();await initial.close();await transport.close()}
