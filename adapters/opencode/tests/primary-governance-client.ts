// SPDX-License-Identifier: AGPL-3.0-only
// Actual HTTP/service/custody; native session/provider behavior is an explicit model-free fixture.
import assert from "node:assert/strict"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody, registerConnectedWorkTools } from "../src/connected-tools.js"
import { registerPrimaryGovernor } from "../src/native-primary.js"
import { exactJSON } from "../src/strict-json.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { preparePrimaryBootstrap } from "../src/primary-bootstrap.js"

let raw="";for await(const chunk of process.stdin){raw+=chunk;assert.ok(Buffer.byteLength(raw)<65536)}
const cfg=JSON.parse(raw),hooks=new Map<string,(e:any)=>Promise<void>>(),tools=new Map<string,any>()
let permissions:unknown[]=[]
const hook=(domain:string)=>(name:string,callback:(e:any)=>Promise<void>)=>{
  hooks.set(domain+":"+name,callback);return Promise.resolve({dispose:async()=>{hooks.delete(domain+":"+name)}})
}
const ctx={app:{version:"2.2.5"},location:{directory:cfg.workRoot},session:{
  get:async({sessionID}:{sessionID:string})=>({id:sessionID,location:{directory:cfg.workRoot},permissions}),
  context:async()=>[],
  update:async(input:any)=>{permissions=input.permissions},hook:hook("session")},permission:{hook:hook("permission")},
  tool:{hook:hook("tool"),reload:async()=>{},transform:async(callback:(e:any)=>void)=>{callback({add:(t:any)=>tools.set(t.name,t)});return {dispose:async()=>{}}}}} as unknown as Plugin.Context
await preparePrimaryBootstrap({root:cfg.root,workRoot:cfg.workRoot,storeId:cfg.storeId,projectId:cfg.projectId,controllerId:cfg.controllerId,hostId:cfg.hostId,
  serviceOrigin:cfg.origin,nativeOrigin:cfg.origin,nativeRegistrationFile:path.join(cfg.root,"MOCK-native-registration.json"),transportOrigin:cfg.transportOrigin,
  controller:{principalId:cfg.principalId,credentialId:cfg.credentialId,token:cfg.token},
  serviceProof:Buffer.from(cfg.proof,"base64url"),transportProof:Buffer.from(cfg.transportProof,"base64url"),schemas:{task:"MOCK schema fixture"}})
const custody=new LinuxNativeCustody(path.join(cfg.root,"native"),[cfg.workRoot]);await custody.initialize()
const registry=new NativeCredentialRegistry(ctx,custody,new NativeRuntimeClient(cfg.origin,cfg.proof,cfg.storeId,cfg.projectId,cfg.controllerId,cfg.hostId),
  cfg.origin,new LinuxWorkRequestCustody(path.join(cfg.root,"requests"),[cfg.workRoot]),"a".repeat(64))
const registration=await registerConnectedWorkTools(ctx,registry)
const governor=await registerPrimaryGovernor(ctx,registry,new LinuxNativeCustody(path.join(cfg.root,"primary"),[cfg.workRoot]),
  {workRoot:cfg.workRoot,controllerId:cfg.controllerId,controllerCredentialId:cfg.credentialId,projectId:cfg.projectId,schemas:{task:"MOCK schema supplied only for boundary test"}})
let transport:Awaited<ReturnType<typeof listenNativeTransport>>|undefined
try{
  const sessionID="ses_primary_component",input="Build a community chess tournament tracker: pairings, score entry, standings and CSV export"
  const prompt={sessionID,messageID:"msg_owner_objective",prompt:{text:input}}
  await hooks.get("session:prompt")!(prompt);assert.equal(prompt.prompt.text,input)
  const bridge=new ProtectedNativeBridge(registry,custody,sessionID,cfg.hostId)
  const native=new ConnectedNativeAdapter(ctx,{} as never,bridge,cfg.hostId) // no external native effect is invoked
  transport=await listenNativeTransport(cfg.transportOrigin,Buffer.from(cfg.transportProof,"base64url"),bridge,native)
  // Real bidirectional HTTP: service connect owns its HTTP lane while querying the immutable
  // host/session witness. That callback must never reenter service identity/current-authority HTTP.
  await registry.connectRuntime(sessionID)
  const event={sessionID,system:[],tools:{shell:{},patch:{},subagent:{},writhelm_work_command:{},question:{}}}
  await hooks.get("session:context")!(event)
  assert.deepEqual(Object.keys(event.tools),["writhelm_work_command","question"])
  for(const tool of ["shell","patch","subagent"])await assert.rejects(hooks.get("tool:execute.before")!({sessionID,tool}))
  const tool=tools.get("writhelm_work_command"),actual={sessionID,messageID:"msg_planner_draft",id:"call_draft",signal:new AbortController().signal}
  await hooks.get("tool:execute.before")!({...actual,tool:"writhelm_work_command"})
  const draft=exactJSON((await tool.execute({json:cfg.draftJSON},actual)).content,()=>{throw Error("bad service reply")})
  assert.equal(draft.kind,"settled");assert.equal(draft.outcome.kind,"success")
  const executable=exactJSON((await tool.execute({json:cfg.prematureJSON},{...actual,id:"call_premature"})).content,()=>{throw Error("bad service reply")})
  assert.equal(executable.kind,"settled");assert.equal(executable.outcome.kind,"rejected");assert.equal(executable.outcome.code,"BLOCKED_INPUT")
  await assert.rejects(tools.get("writhelm_knowledge_command").execute({json:JSON.stringify({formatVersion:1,projectId:cfg.projectId,operation:"resolve_approval"})},actual))
  process.stdout.write(JSON.stringify({draftCreated:true,prematureRefused:true,implementationToolsHeld:true,ownerApprovalUnavailableToModel:true,native:"MOCK",service:"REAL"}))
}finally{await transport?.close();await governor.close();await registration.dispose()}
