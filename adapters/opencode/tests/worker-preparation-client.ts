// SPDX-License-Identifier: AGPL-3.0-only
// REAL service/Git/custody/bidirectional HTTP; external OpenCode SDK/profile is an explicit MOCK.
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
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
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { NativePassiveWorkers } from "../src/native-passive-worker.js"
import { LinuxGitWitness } from "../src/linux-git-witness.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { contextJSON } from "../src/native-context-codec.js"
import { exactJSON } from "../src/strict-json.js"
import { NativeWorkerPreparation } from "../src/native-worker-preparation.js"
import { NativeInitialAdapter } from "../src/native-initial.js"
import { NativeInitialStart } from "../src/native-initial-start.js"

let input="";for await(const chunk of process.stdin){input+=chunk;assert.ok(Buffer.byteLength(input)<65536)}
const cfg=JSON.parse(input),sessionID="ses_worker_preparation_controller",sessions=new Map<string,any>(),inventory:Array<{directory:string;strategy:string}>=[]
let trees=0,receivers=0,lost=true,wakes=0
const hooks=new Map<string,Array<(e:any)=>Promise<void>>>(),messages=new Map<string,any[]>(),instructions=new Map<string,Array<{key:string;value:string}>>()
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
      sessions.set(i.id,s);if(lost){lost=false;throw Error("MOCK lost original session-create reply")};return s},
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
  }},tool:{hook:hook("tool")}} as unknown as Plugin.Context
const custody=new LinuxNativeCustody(path.join(cfg.root,"native"),[cfg.sourceRoot,cfg.worktreeParent]);await custody.initialize()
const registry=new NativeCredentialRegistry(ctx,custody,new NativeRuntimeClient(cfg.origin,cfg.proof,cfg.storeId,cfg.projectId,cfg.controllerId,cfg.hostId),cfg.origin,
  new LinuxWorkRequestCustody(path.join(cfg.root,"requests"),[cfg.sourceRoot,cfg.worktreeParent]),"a".repeat(64))
await registry.adopt(cfg.principalId,cfg.credentialId,cfg.token);await registry.bind(sessionID,cfg.credentialId,cfg.sourceRoot)
const workers=new NativePassiveWorkers(native,registry,custody,sessionID,{sourceRoot:cfg.sourceRoot,worktreeParent:cfg.worktreeParent,
  nativeProjectId:"MOCK-native-project",repositoryId:cfg.repositoryId,controllerId:cfg.controllerId,hostId:cfg.hostId,
  agents:{"executor-v1":"build","reviewer-v1":"plan"}},new LinuxGitWitness({path:"/usr/bin/git",sha256:createHash("sha256").update(await fs.readFile("/usr/bin/git")).digest("hex")}))
const bridge=new ProtectedNativeBridge(registry,custody,sessionID,cfg.hostId)
const adapter=new ConnectedNativeAdapter(ctx,native.session,bridge,cfg.hostId)
const deployment=new LinuxNativeCustody(path.join(cfg.root,"deployment"),[cfg.sourceRoot,cfg.worktreeParent]);await deployment.initialize()
const initial=new NativeInitialAdapter(ctx,native,registry,custody,deployment,sessionID);await initial.register()
const transport=await listenNativeTransport(cfg.transportOrigin,Buffer.from(cfg.transportProof,"base64url"),bridge,adapter,undefined,workers,initial)
let recoveredInitial:NativeInitialAdapter|undefined
const signal=new AbortController().signal,parse=(s:string)=>exactJSON(s,()=>{throw Error("invalid service JSON")})
try{
  const driver=new NativeWorkerPreparation(registry,workers,custody,sessionID,{projectId:cfg.projectId,controllerId:cfg.controllerId,
    hostId:cfg.hostId,worktreeParent:cfg.worktreeParent})
  const payload=parse(cfg.reserveJSON).payload
  const selection=contextJSON({task:payload.task,manifest:payload.manifest,profile:payload.profile,resources:payload.resources,reviewSubjects:[]})
  const actual={sessionID:Schema.decodeUnknownSync(Session.ID)(sessionID),messageID:Schema.decodeUnknownSync(SessionMessage.ID)("msg_worker_selection"),
    id:Schema.decodeUnknownSync(Tool.CallID)("call_worker_prepare"),signal}
  await assert.rejects(driver.run(selection,actual))
  const prepared=await driver.run(selection,actual)
  assert.equal(prepared.phase,"binding-confirmed")
  assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
  const replay=await driver.run(selection,actual,prepared.preparationId)
  assert.equal(replay.phase,"binding-confirmed");assert.equal(contextJSON(replay.binding),contextJSON(prepared.binding))
  assert.deepEqual({trees,receivers,wakes},{trees:1,receivers:1,wakes:0})
  assert.equal((await registry.inspect(prepared.nativeSessionId))?.scope,"assignment")
  assert.equal(wakes,0)
  if(cfg.start){
    await deployment.retain("execution-profile",contextJSON(payload.profile),contextJSON({formatVersion:1,profileKeyJSON:contextJSON(payload.profile),
      model:{providerID:"synthetic",id:"no-call"},baseURL:null,routeID:"no-call",effortOptionKey:"effort",nativeOptions:{maxTokens:64,effort:"bounded"}}))
    const starter=new NativeInitialStart(registry,initial,custody,deployment,sessionID,cfg.projectId)
    const selected=contextJSON({assignment:prepared.assignmentSelector,binding:prepared.binding})
    const call={...actual,id:Schema.decodeUnknownSync(Tool.CallID)("call_initial_start")}
    const started=await starter.run(selected,call)
    assert.equal(started.phase,"active-admission-confirmed");assert.equal(started.completionAsserted,false)
    await boundary;assert.equal(contextEvent.options.maxTokens,64);assert.equal(contextEvent.options.effort,"bounded")
    const restored=await starter.run(selected,call,started.startId)
    assert.equal(restored.phase,"active-admission-restored");assert.equal(wakes,1)
    await initial.close()
    hooks.clear() // Explicit MOCK host unload of the old plugin lifetime, not installed lifecycle evidence.
    recoveredInitial=new NativeInitialAdapter(ctx,native,registry,custody,deployment,sessionID);await recoveredInitial.register()
    const recovered=await recoveredInitial.restore()
    assert.equal(recovered.length,1);assert.equal(recovered[0]!.phase,"active-admission-restored")
    await recoveredInitial.admitTool(prepared.nativeSessionId)
    await invoke("tool:execute.before",{sessionID:prepared.nativeSessionId})
    assert.equal(wakes,1)
  }
  process.stdout.write(JSON.stringify({passiveAndBound:true,assignmentId:prepared.assignmentId,trees,receivers,wakes,native:"MOCK",gitAndService:"REAL"}))
}finally{await recoveredInitial?.close();await initial.close();await transport.close()}
