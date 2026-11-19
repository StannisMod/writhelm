// SPDX-License-Identifier: AGPL-3.0-only
// Canonical service/native observations are explicit doubles; custody, state reduction and
// native continuation admission code are real and model-free.
import test from "node:test"
import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import { contextJSON } from "../src/native-context-codec.js"
import { exactJSON } from "../src/strict-json.js"
import { NativePlannerActivity, NativePlannerProgress } from "../src/native-planner-progress.js"
import { NativePlannerWait, type PlannerNativeState } from "../src/native-planner-wait.js"
import { NativeRoleContinuation } from "../src/native-role-continuation.js"
import { NativeOwnerOrder } from "../src/native-owner-order.js"
import type { NativeCredentialRegistry } from "../src/native-credentials.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
const scope={storeId:id(1),projectId:id(2),controllerId:id(3),sessionID:"ses_planner"}
const task={projectId:scope.projectId,taskId:id(4),revision:0}
const parse=(raw:string)=>exactJSON(raw,()=>{throw Error("fixture JSON")})
const sameFixture=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
function storage(){
  const values=new Map<string,string>(),key=(kind:string,k:string)=>kind+"\n"+k
  return {values,read:async(kind:string,k:string)=>values.get(key(kind,k)),
    retain:async(kind:string,k:string,raw:string)=>{
      const previous=values.get(key(kind,k));if(previous!==undefined){assert.equal(previous,raw);return false}
      values.set(key(kind,k),raw);return true
    },replace:async(kind:string,k:string,expected:string,raw:string)=>{
      assert.equal(hash(values.get(key(kind,k))!),expected);values.set(key(kind,k),raw);return true
    },scan:async(kind:string)=>[...values].filter(([k])=>k.startsWith(kind+"\n")).map(([k,raw])=>({
      keyDigest:hash(k.slice(kind.length+1)),raw}))}
}
const command=(operation="create_task_revision",requestKey=id(10),payload:any={key:task,data:{state:"draft",objective:"not copied"}})=>
  contextJSON({formatVersion:3,projectId:scope.projectId,requestKey,operation,payload})
const receipt=(raw:string,success=true)=>{
  const c=parse(raw),record={formatVersion:1,family:"task-revisions",key:c.payload.key,payload:c.payload.data}
  return contextJSON({kind:"settled",identity:{projectId:c.projectId,operation:c.operation,requestKey:c.requestKey},
    outcome:{kind:success?"success":"rejected",...(c.operation==="create_task_revision"&&success?{result:{
      key:c.payload.key,generation:null,digest:hash(contextJSON(record))}}:{})}})
}
function fixture(){
  const store=storage(),activity=new NativePlannerActivity(store,scope),order=new NativeOwnerOrder(store,scope)
  const records:any[]=[{formatVersion:1,family:"task-revisions",key:task,payload:{state:"draft",objective:"not copied"}}]
  const calls:any[]=[],pendingRequests=new Set<string>(),approvalRef={kind:"clause",revision:{document:{projectId:scope.projectId,
    documentId:id(50)},revisionId:id(51)},clauseId:"contract",revisionDigest:"a".repeat(64),contentDigest:"b".repeat(64)}
  const channel={scope:"controller",projectID:scope.projectId,send:async(_route:string,raw:string)=>{
    const request=parse(raw);calls.push(request)
    if(request.operation==="list_work_records"){
      if(!["task-heads","assignments","results","verification-reports","acceptances","integrations","controller-continuations",
        "assignment-replacements","conditions","condition-confirmations","continuations"].includes(request.subject.family))
        return '{"kind":"error","code":"INVALID_INPUT"}'
      const items=records.filter(r=>r.family===request.subject.family).map(r=>({family:r.family,key:r.key,
        ...(["task-heads","assignments","plans","integration-intents"].includes(r.family)?{generation:r.payload.generation??0}:{digest:hash(contextJSON(r))})}))
      return contextJSON({kind:"read",data:{snapshotId:id(70),nextCursor:null,complete:true,items}})
    }
    if(request.operation==="read_condition_status")return contextJSON({kind:"read",data:{state:"resolved"}})
    const record=records.find(r=>r.family===request.operation.slice(5).replaceAll("_","-")&&contextJSON(r.key)===contextJSON(request.subject.key))
    return record?contextJSON({kind:"read",data:record}):'{"kind":"error","code":"NOT_FOUND"}'
  }}
  let authorized=true,permissions=new Set(["TASK_PLAN","TASK_DISPATCH","RESULT_ACCEPT"])
  const registry={sourceIdentity:async()=>{
    if(!authorized)throw Error("fixture current READ withdrawn")
    return {...scope,scope:"controller"}
  },contextChannel:async()=>channel,controllerPlanningAdmission:async()=>permissions.size>0,
  plannerTaskProgress:async(_session:string,raw:string)=>{
    const ref=parse(raw),record=records.find(r=>r.family==="task-revisions"&&sameFixture(r.key,ref.key))
    assert.ok(record);assert.equal(hash(contextJSON(record)),ref.digest)
    const results=records.filter(r=>r.family==="acceptances"&&sameFixture(r.payload.task,ref.key)).map(r=>r.payload.result)
    return {plans:records.filter(r=>r.family==="plans"&&r.payload.assignment===null&&sameFixture(r.payload.task,ref.key))
      .map(r=>({keyJSON:contextJSON(r.key),steps:r.payload.steps})),
      integrationStates:records.filter(r=>r.family==="integration-intents"&&results.some(result=>sameFixture(result,r.payload.result)))
        .map(r=>({id:r.key.id,state:r.payload.state}))}
  },
  planningKnowledgeContextChannel:async()=>({send:async(_route:string,raw:string)=>{
    const r=parse(raw)
    if(r.operation==="read_resolution")return pendingRequests.has(r.subject.id)?'{"kind":"error","code":"NOT_FOUND"}':
      contextJSON({kind:"read",data:{record:{family:"resolutions",key:r.subject.id}}})
    return contextJSON({kind:"read",data:{record:{family:"requests",key:r.subject.id,
      payload:{projectId:scope.projectId,packet:{items:[{target:approvalRef,before:null}],contexts:[],dependencies:[]}}}}})
  }})} as unknown as NativeCredentialRegistry
  const progress=new NativePlannerProgress(activity,registry,order)
  let sends=0
  const roles=new NativeRoleContinuation(store,async()=>progress.observe(new AbortController().signal),async input=>{
    sends++;assert.equal(Object.hasOwn(input.metadata,"assignmentId"),false,"planner is not a fabricated worker")
    return {type:"synthetic",sessionID:input.sessionID,id:input.id,payload:{text:input.text,metadata:input.metadata}}
  })
  return {store,activity,order,records,calls,pendingRequests,approvalRef,progress,roles,sends:()=>sends,
    revoke:()=>{authorized=false},
    revokeWork:()=>{permissions=new Set()},
    arm:async()=>{const raw=command();await activity.before(raw);await activity.after(raw,receipt(raw))}}
}
const idle={sessionID:scope.sessionID,background:[]}
test("exact B resolution reacts once despite older pending A, survives reopen, then observes A",async()=>{
  const f=fixture()
  for(const n of [81,82]){
    const raw=command("request_approval",id(n+10),{requestId:id(n),packet:{}})
    await f.activity.before(raw);await f.activity.after(raw,receipt(raw));f.pendingRequests.add(id(n))
  }
  assert.equal(await f.roles.idle(idle),"external-wait");assert.equal(f.sends(),0)
  f.pendingRequests.delete(id(82))
  assert.equal(await f.roles.idle(idle),"continued");assert.equal(f.sends(),1)
  const p=await f.progress.observe(new AbortController().signal)
  assert.deepEqual(p!.resolvedRequestIds,[id(82)])
  assert.ok(p!.waitJSON!.includes(id(81)),"old request remains pending")
  assert.equal(await f.roles.idle(idle),"external-wait");assert.equal(f.sends(),1)
  let reopenedSends=0
  const reopened=new NativeRoleContinuation(f.store,()=>f.progress.observe(new AbortController().signal),async input=>{
    reopenedSends++
    assert.match(input.text,/Acknowledge the owner's actual decision/)
    return {type:"synthetic",sessionID:input.sessionID,id:input.id,payload:{text:input.text,metadata:input.metadata}}
  })
  assert.equal(await reopened.idle(idle),"external-wait");assert.equal(reopenedSends,0)
  f.pendingRequests.delete(id(81))
  assert.equal(await reopened.idle(idle),"continued");assert.equal(reopenedSends,1)
  assert.equal(await reopened.idle(idle),"stalled");assert.equal(reopenedSends,1)
})
test("planner activity retains only selector/receipt identity; unknown originals hold through reopen",async()=>{
  const f=fixture(),raw=command()
  await f.activity.before(raw)
  assert.equal((await f.activity.snapshot()).unknown,true)
  assert.equal(await f.roles.idle(idle),"unknown-effect-held");assert.equal(f.sends(),0)
  const reopened=new NativePlannerActivity(f.store,scope)
  await reopened.after(raw,'{"kind":"error","code":"FORBIDDEN"}')
  assert.equal((await reopened.snapshot()).unknown,true,"loss of rights proves nothing about an earlier command")
  await reopened.after(raw,receipt(raw));assert.equal((await reopened.snapshot()).unknown,false)
  for(const bytes of f.store.values.values())assert.equal(bytes.includes("not copied"),false)
  assert.equal(await f.roles.idle(idle),"continued");assert.equal(await f.roles.idle(idle),"stalled")
  const other=new NativePlannerActivity(f.store,{...scope,sessionID:"ses_other",controllerId:id(99)})
  assert.equal((await other.snapshot()).armed,false)
})
test("read-only ordinary reply does not invent a governed task or a synthetic execution",async()=>{
  const f=fixture()
  assert.equal(await f.progress.observe(new AbortController().signal),undefined)
  await f.roles.idle(idle);assert.equal(f.sends(),0)
})
test("planner scope excludes another chat's unassigned heads, plans and continuation",async()=>{
  const f=fixture();await f.arm()
  f.records.push({family:"task-heads",key:{projectId:scope.projectId,taskId:id(99)},payload:{generation:0,draft:{...task,taskId:id(99)},executable:null}},
    {family:"plans",key:{projectId:scope.projectId,id:id(98),revision:0},payload:{generation:0,task:{...task,taskId:id(99)},assignment:null,
      steps:[{id:id(97),state:"active",blockers:[]}]}},
    {family:"controller-continuations",key:{projectId:scope.projectId,controllerId:id(96),revision:0},payload:{}})
  const p=await f.progress.observe(new AbortController().signal)
  assert.equal(p!.progressJSON.includes(id(99)),false)
  assert.equal(f.calls.some(c=>c.operation==="read_task_heads"&&c.subject.key.taskId===id(99)),false)
  assert.equal(f.calls.some(c=>c.operation==="read_controller_continuations"&&c.subject.key.controllerId===id(96)),false)
})
test("an actual chat-linked approval and sourced blocked plan wait; canonical resolution makes work actionable",async()=>{
  const f=fixture();await f.arm()
  const approval=command("request_approval",id(20),{requestId:id(21),packet:{}})
  await f.activity.before(approval);await f.activity.after(approval,receipt(approval));f.pendingRequests.add(id(21))
  f.records.push({family:"plans",key:{projectId:scope.projectId,id:id(22),revision:0},payload:{generation:0,task,assignment:null,
    steps:[{id:id(23),state:"blocked",blockers:[{kind:"knowledge",reference:f.approvalRef}]}]}})
  assert.equal(await f.roles.idle(idle),"external-wait");assert.equal(f.sends(),0)
  f.pendingRequests.delete(id(21))
  assert.equal(await f.roles.idle(idle),"continued");assert.equal(f.sends(),1)
})
test("accepted exact outcomes are completion, but remaining plans, live assignments and unknown integration are not",async()=>{
  const f=fixture();await f.arm()
  f.records.push({family:"acceptances",key:{projectId:scope.projectId,id:id(31)},payload:{task,result:{family:"results",key:{projectId:scope.projectId,id:id(32)},digest:"a".repeat(64)}}})
  assert.equal((await f.progress.observe(new AbortController().signal))!.outcomeComplete,true)
  assert.equal(await f.roles.idle(idle),"finished");assert.equal(f.sends(),0)
  const plan={family:"plans",key:{projectId:scope.projectId,id:id(33),revision:0},payload:{generation:0,task,assignment:null,
    steps:[{id:id(34),state:"active",blockers:[]}]}}
  f.records.push(plan);assert.equal((await f.progress.observe(new AbortController().signal))!.outcomeComplete,false)
  plan.payload.steps[0]!.state="complete"
  const assignment={family:"assignments",key:{projectId:scope.projectId,id:id(35)},payload:{generation:1,executionGeneration:0,task,state:"active",
    checkpoint:null as any,intendedExecution:{controllerId:scope.controllerId}}}
  f.records.push(assignment)
  assert.equal((await f.progress.observe(new AbortController().signal))!.outcomeComplete,false)
  assert.equal(await f.roles.idle(idle),"external-wait")
  const cp={family:"checkpoints",key:{projectId:scope.projectId,id:id(37),revision:0},
    payload:{task,assignment:{assignmentId:id(35),generation:0},handoff:"finished"}}
  assignment.payload.checkpoint={family:"checkpoints",key:cp.key,digest:hash(contextJSON(cp))}
  f.records.push(cp)
  assert.equal(await f.roles.idle(idle),"continued","a finished worker report is planner work, not another wait for that worker")
  f.records.splice(f.records.indexOf(cp),1);f.records.splice(f.records.indexOf(assignment),1)
  f.records.push({family:"integration-intents",key:{projectId:scope.projectId,id:id(36)},payload:{generation:1,state:"unknown",
    result:f.records.find(r=>r.family==="acceptances").payload.result}})
  assert.equal(await f.roles.idle(idle),"unknown-effect-held")
})
test("canonical head revision changes cannot reuse an older accepted task outcome",async()=>{
  const f=fixture();await f.arm()
  f.records.push({family:"acceptances",key:{projectId:scope.projectId,id:id(40)},payload:{task,result:{family:"results",
    key:{projectId:scope.projectId,id:id(41)},digest:"a".repeat(64)}}})
  assert.equal((await f.progress.observe(new AbortController().signal))!.outcomeComplete,true)
  const newer={...task,revision:1}
  const newPayload={state:"draft",objective:"changed exact contract"},created=command("create_task_revision",id(42),{key:newer,data:newPayload})
  await f.activity.before(created);await f.activity.after(created,receipt(created))
  f.records.push({formatVersion:1,family:"task-revisions",key:newer,payload:newPayload},
    {family:"task-heads",key:{projectId:scope.projectId,taskId:task.taskId},payload:{generation:9007199254740993n,draft:newer,executable:null}})
  const frame=await f.progress.observe(new AbortController().signal)
  assert.equal(frame!.outcomeComplete,false)
  assert.equal(parse(parse(frame!.progressJSON).payload.steps[0].id).revision.value,"1")
})
test("malformed input is a known pre-execution refusal; current source withdrawal blocks planner continuation",async()=>{
  const f=fixture(),raw=command()
  await f.activity.before(raw);await f.activity.after(raw,'{"kind":"error","code":"INVALID_INPUT"}')
  assert.equal((await f.activity.snapshot()).unknown,false);assert.deepEqual((await f.activity.snapshot()).tasks,[])
  const corrected=command("create_task_revision",id(11));await f.activity.before(corrected);await f.activity.after(corrected,receipt(corrected));f.revoke()
  assert.equal(await f.roles.idle(idle),"admission-held");assert.equal(f.sends(),0)
  const readonly=fixture();await readonly.arm();readonly.revokeWork()
  assert.equal(await readonly.roles.idle(idle),"admission-held");assert.equal(readonly.sends(),0)
})

function waiting(){
  const f=fixture()
  let native:PlannerNativeState={id:scope.sessionID,directory:"/synthetic",busy:100,idle:90,outcome:"succeeded",
    archived:false,reverted:false,binding:"native-incarnation-a"},blocked=true,sends=0,wait!:NativePlannerWait
  const roles=new NativeRoleContinuation(f.store,async()=>{
    if(wait&&!await wait.permitted())throw Error("native pause/new execution")
    return {controllerId:scope.controllerId,bindingJSON:contextJSON(scope),nativeReady:true,outcomeComplete:false,
      progressJSON:contextJSON({payload:{steps:[{id:"owner",state:blocked?"blocked":"active",blockers:blocked?[{id:"actual-request"}]:[]}]}}),
      waitJSON:blocked?contextJSON({payload:{pending:[{id:"owner",kind:"decision",source:{id:"actual-request"}}],result:null,retrospective:null,notification:null}}):null}
  },async input=>{
    if(!await wait.permitted())throw Error("native admission held")
    sends++;return {type:"synthetic",id:input.id,sessionID:input.sessionID,payload:{text:input.text,metadata:input.metadata}}
  })
  wait=new NativePlannerWait(f.store,scope,"/synthetic",async()=>native,roles)
  return {f,wait,roles,block:(value:boolean)=>{blocked=value},native:(value:Partial<PlannerNativeState>)=>{native={...native,...value}},
    getNative:()=>native,sends:()=>sends}
}
test("successful native wait resumes automatically once, on the same binding/execution, without owner nudge",async()=>{
  const f=waiting()
  await f.roles.idle(idle);await f.wait.afterIdle(scope.sessionID)
  await f.wait.poll();assert.equal(f.sends(),0,"pre-idle is not yet a succeeded terminal execution")
  f.native({idle:101});await f.wait.poll();assert.equal(f.sends(),0,"actual wait is still pending")
  f.block(false);await f.wait.poll();assert.equal(f.sends(),1)
  await f.wait.poll();assert.equal(f.sends(),1)
  await f.wait.close()
})
test("failed/interrupted/newer/archived/reverted/native-incarnation-changed waits never resume a model turn",async()=>{
  for(const change of [{outcome:"interrupted"},{outcome:"failed"},{busy:200,idle:201},{archived:true},{reverted:true},{binding:"native-incarnation-b"}]){
    const f=waiting();await f.roles.idle(idle);await f.wait.afterIdle(scope.sessionID)
    f.native({idle:101,...change});f.block(false);await f.wait.poll()
    assert.equal(f.sends(),0,contextJSON(change));await f.wait.close()
  }
})
test("retained native wait recovers on the same incarnation, not by adopting a new execution epoch",async()=>{
  const f=waiting();await f.roles.idle(idle);await f.wait.afterIdle(scope.sessionID)
  f.native({idle:101});f.block(false)
  const reopened=new NativePlannerWait(f.f.store,scope,"/synthetic",async()=>f.getNative(),f.roles)
  await reopened.poll();assert.equal(f.sends(),1)
  await reopened.close();await f.wait.close()
})
