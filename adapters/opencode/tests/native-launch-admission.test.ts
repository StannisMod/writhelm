// SPDX-License-Identifier: AGPL-3.0-only
// Real protected custody and launch drivers; service/native operations below are explicit doubles.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { NativeWorkerPreparation, NativeDispatchFrontier } from "../src/native-worker-preparation.js"
import { NativeInitialStart } from "../src/native-initial-start.js"
import { CanonicalDispatchActivation } from "../src/native-plan-activation.js"
import { NativeLaunchAdmission, type LaunchAdmission } from "../src/native-launch-admission.js"
import { NativeOwnerOrder, OwnerOrderHold } from "../src/native-owner-order.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { contextJSON } from "../src/native-context-codec.js"
import { exactJSON } from "../src/strict-json.js"
import type { NativeCredentialRegistry } from "../src/native-credentials.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
const project=id(1),sessionID="ses_launch"
const task={projectId:project,taskId:id(4),revision:0}
const plan=contextJSON({formatVersion:1,family:"dispatch-plans",key:{projectId:project,id:id(3)},payload:{entries:[]}})
const parse=(raw:string)=>exactJSON(raw,()=>{throw Error("invalid fixture")})
const selection={task,manifest:{family:"input-manifests",key:{projectId:project,id:id(5)},digest:"a".repeat(64)},
  profile:{projectId:project,id:id(6),revision:0},resources:[{projectId:project,id:id(7)}],reviewSubjects:[]}
const initialSelection={assignment:{family:"assignments",key:{projectId:project,id:id(8)},generation:0},
  binding:{family:"assignment-bindings",key:{projectId:project,assignmentId:id(8),generation:0},digest:"b".repeat(64)}}
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"launch-order-")),work=path.join(root,"work")
  await fs.mkdir(work)
  const custody=new LinuxNativeCustody(path.join(root,"custody"),[work]);await custody.initialize()
  let effects=0,admissions=0
  const channel={scope:"controller",projectID:project,send:async()=>{effects++;throw Error("must not dispatch")}}
  const registry={sourceIdentity:async()=>({}),resolve:async()=>channel,contextChannel:async()=>channel,
    dispatchContext:async()=>({planJSON:plan,entries:[]})} as unknown as NativeCredentialRegistry
  const deny=async()=>{admissions++;throw new OwnerOrderHold("OWNER_ORDER_REQUIRED")}
  const gate:LaunchAdmission={plan:deny,task:deny,assignment:deny,preparation:deny}
  return {custody,registry,gate,work,effects:()=>effects,admissions:()=>admissions}
}
test("direct/planned preparation and every preparation recovery hold before reservation effects",async()=>{
  const f=await fixture(),preparation=new NativeWorkerPreparation(f.registry,{} as never,f.custody,sessionID,
    {projectId:project,controllerId:id(2),hostId:id(9),worktreeParent:f.work},f.gate)
  const signal=new AbortController().signal,raw=contextJSON(selection),attempt={planRefJSON:"unused"}
  await assert.rejects(preparation.runController(raw,id(20),signal),OwnerOrderHold)
  await assert.rejects(preparation.runPlannedController(raw,id(21),attempt,signal),OwnerOrderHold)
  await f.custody.retain("preparation-driver",id(22),contextJSON({formatVersion:1,id:id(22),controllerSessionID:sessionID,
    selectionJSON:raw,reserveJSON:"{}",principalId:id(30),credentialId:id(31),delegationId:id(32),enrollmentKey:id(33),bindKey:id(34)}))
  await assert.rejects(preparation.restoreController(id(22),signal),OwnerOrderHold)
  await assert.rejects(preparation.restorePlannedController(id(22),attempt,signal),OwnerOrderHold)
  assert.equal(f.admissions(),4);assert.equal(f.effects(),0)
  assert.equal(await f.custody.read("preparation-driver",id(20)),undefined)
})
test("direct start, controller start and retained recovery hold before intent/prompt effects",async()=>{
  const f=await fixture(),start=new NativeInitialStart(f.registry,{} as never,f.custody,f.custody,sessionID,project,f.gate)
  const signal=new AbortController().signal,raw=contextJSON(initialSelection)
  await assert.rejects(start.run(raw,{sessionID,messageID:"msg_test",id:"call_test",signal} as never),OwnerOrderHold)
  await assert.rejects(start.runController(raw,id(20),signal),OwnerOrderHold)
  await f.custody.retain("initial-driver",id(21),contextJSON({formatVersion:1,id:id(21),controllerSessionID:sessionID,
    selectionJSON:raw,intentId:id(30),observeKey:id(31),requestJSON:"{}"}))
  await assert.rejects(start.restoreController(id(21),signal),OwnerOrderHold)
  assert.equal(f.admissions(),3);assert.equal(f.effects(),0)
})
test("activation/recovery and legacy frontier cannot bypass absent owner selection",async()=>{
  const f=await fixture();let created=0
  const activation=new CanonicalDispatchActivation(f.custody,project,sessionID,async()=>({planJSON:plan}),()=>{
    created++;return {start(){},wake(){},async close(){}}
  },f.gate)
  await assert.rejects(activation.activate(id(3)),OwnerOrderHold)
  await f.custody.retain("dispatch-activation",id(3),JSON.stringify({formatVersion:1,projectId:project,controllerSessionID:sessionID,planId:id(3)}))
  await activation.restore();assert.equal(created,0)
  const frontier=new NativeDispatchFrontier(f.registry,{} as never,{} as never,f.custody,sessionID,id(3),4,1000,f.gate)
  await assert.rejects((frontier as any).scan(),OwnerOrderHold)
  assert.equal(f.admissions(),3);assert.equal(f.effects(),0)
  await frontier.close();await activation.close()
})
test("shared service-bound gate rejects changed exact plan/task after owner selection and after restore",async()=>{
  const f=await fixture(),order=new NativeOwnerOrder(f.custody,{storeId:id(50),projectId:project,controllerId:id(2),sessionID})
  let planJSON=plan
  const row=contextJSON({formatVersion:1,family:"task-revisions",key:task,payload:{objective:"exact synthetic task"}})
  const registry={sourceIdentity:async()=>({}),dispatchContext:async()=>({planJSON,entries:[{taskJSON:row}]})} as unknown as NativeCredentialRegistry
  const gate=new NativeLaunchAdmission(order,registry,sessionID,project)
  const proposal=await gate.propose(id(3),[],id(40))
  assert.equal((await order.pending())?.selected,false)
  await assert.rejects(gate.task(task,new AbortController().signal),OwnerOrderHold)
  await order.select(proposal.proposalId,proposal.proposalDigest,null)
  await gate.task(task,new AbortController().signal)
  const restored=new NativeLaunchAdmission(new NativeOwnerOrder(f.custody,{storeId:id(50),projectId:project,controllerId:id(2),sessionID}),registry,sessionID,project)
  await restored.task(task,new AbortController().signal)
  await assert.rejects(restored.task({...task,revision:1},new AbortController().signal),OwnerOrderHold)
  planJSON=contextJSON({...parse(plan),payload:{entries:[],changed:true}})
  await assert.rejects(restored.task(task,new AbortController().signal),OwnerOrderHold)
  await assert.rejects(restored.plan(planJSON),OwnerOrderHold)
})

test("ordered admission reads current service launch evidence; original recovery generations are not rewritten",async()=>{
  const f=await fixture(),order=new NativeOwnerOrder(f.custody,{storeId:id(50),projectId:project,controllerId:id(2),sessionID})
  const next={...task,taskId:id(5)},assignmentKey={projectId:project,id:id(10)}
  let state="bound",intentOutcome="pending"
  const rows=[task,next].map(key=>({taskJSON:contextJSON({formatVersion:1,family:"task-revisions",key,payload:{objective:"ordered fixture"}}),
    selectionJSON:contextJSON({...selection,task:key})}))
  const channel={scope:"controller",projectID:project,send:async(_route:string,raw:string)=>{
    const request=parse(raw),family=request.subject.family
    if(request.operation==="list_work_records")return contextJSON({kind:"read",data:{snapshotId:id(90),complete:true,nextCursor:null,
      items:family==="assignments"?[{family,key:assignmentKey,generation:8}]:
        [{family:"native-intents",key:{projectId:project,id:id(11)},generation:1}]}})
    if(request.operation==="read_assignments"){
      if(request.subject.generation.value==="0")return '{"kind":"error","code":"STALE_GENERATION"}'
      assert.equal(request.subject.generation.value,"8","current authorization read, not mutation of retained command")
      return contextJSON({kind:"read",data:{family:"assignments",key:assignmentKey,payload:{task,generation:8,state,
        manifest:selection.manifest,profile:selection.profile,requiredResources:selection.resources,reviewSubjects:[]}}})
    }
    return contextJSON({kind:"read",data:{family:"native-intents",key:{projectId:project,id:id(11)},
      payload:{assignment:{assignmentId:id(10),generation:0},operation:"start",outcome:intentOutcome}}})
  }}
  const registry={sourceIdentity:async()=>({}),contextChannel:async()=>channel,
    dispatchContext:async()=>({planJSON:plan,entries:rows})} as unknown as NativeCredentialRegistry
  const gate=new NativeLaunchAdmission(order,registry,sessionID,project),signal=new AbortController().signal
  const proposal=await gate.propose(id(3),[{taskId:next.taskId,after:[task.taskId]}],id(80))
  await order.select(proposal.proposalId,proposal.proposalDigest,null)
  await assert.rejects(gate.task(next,signal),e=>e instanceof OwnerOrderHold&&e.code==="OWNER_ORDER_PREDECESSOR_UNKNOWN")
  state="active";await gate.task(next,signal)
  state="stopped";intentOutcome="confirmed";await gate.task(next,signal)
  await assert.rejects(gate.preparation({...selection,manifest:{...selection.manifest,digest:"f".repeat(64)}},signal),
    e=>e instanceof OwnerOrderHold&&e.code==="OWNER_ORDER_INPUTS_CHANGED")
  await gate.preparation(selection,signal)
  // A recovered original may carry generation zero while the current assignment is generation
  // eight. Admission checks its immutable task; the original service command remains untouched.
  await gate.assignment(assignmentKey,0,signal)
})

test("closed dependency revision requires a new exact choice and becomes admissible only after it",async()=>{
  const f=await fixture(),order=new NativeOwnerOrder(f.custody,{storeId:id(50),projectId:project,controllerId:id(2),sessionID})
  const original=contextJSON({formatVersion:1,family:"task-revisions",key:task,payload:{objective:"dependency closure"}})
  const newKey={...task,revision:1},closed=contextJSON({formatVersion:1,family:"task-revisions",key:newKey,payload:{objective:"closed"}})
  let closedJSON:string|null=null
  const registry={sourceIdentity:async()=>({}),dispatchContext:async()=>({planJSON:plan,entries:[{taskJSON:original,closedJSON}]})} as unknown as NativeCredentialRegistry
  const gate=new NativeLaunchAdmission(order,registry,sessionID,project),signal=new AbortController().signal
  const first=await gate.propose(id(3),[],id(81));await order.select(first.proposalId,first.proposalDigest,null)
  const old=(await order.current()).decisionDigest
  closedJSON=contextJSON({taskJSON:closed,selectionJSON:contextJSON({...selection,task:newKey})})
  await assert.rejects(gate.task(newKey,signal),OwnerOrderHold)
  const second=await gate.propose(id(3),[],id(82));await order.select(second.proposalId,second.proposalDigest,old)
  await gate.task(newKey,signal)
  await assert.rejects(gate.task(task,signal),OwnerOrderHold)
})
