// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import type { LinuxNativeCustody } from "./native-runtime-custody.js"
import type { NativeCredentialRegistry } from "./native-credentials.js"
import type { NativeOwnerOrder, OwnerOrderScope } from "./native-owner-order.js"
import type { PlannerRoleProgress } from "./native-role-continuation.js"
import { sendContext } from "./native-context-protocol.js"

const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
const sha=(raw:string)=>createHash("sha256").update(raw).digest("hex")
type Store=Pick<LinuxNativeCustody,"read"|"retain"|"scan">
/** Chat-bound original-command observations, not a second task store or a completion assertion.
 * Only selectors/receipt identity are retained. Canonical records remain the source of content,
 * authority, current scope and outcome. No transcript, task body or credential is retained here. */
export class NativePlannerActivity {
  constructor(private readonly store:Store,readonly scope:OwnerOrderScope){}
  private key(operation:string,requestKey:string){return contextJSON([this.scope,operation,requestKey])}
  async before(raw:string){
    const c=parse(raw)
    if(c.projectId!==this.scope.projectId||!uuidPattern.test(c.requestKey)||typeof c.operation!=="string")nativeUnavailable()
    const p=c.payload
    const tasks=c.operation==="create_task_revision"?[p.key]:
      c.operation==="set_task_head"?[p.draft,p.executable].filter(Boolean):[]
    for(const key of tasks)if(key?.projectId!==this.scope.projectId||!uuidPattern.test(key.taskId)||key.revision?.value===undefined)nativeUnavailable()
    const requestId=["request_approval","submit_review"].includes(c.operation)?p.requestId:null
    if(requestId!==null&&!uuidPattern.test(requestId))nativeUnavailable()
    const intent=contextJSON({formatVersion:1,scope:this.scope,operation:c.operation,requestKey:c.requestKey,
      commandDigest:sha(raw),tasks,requestId})
    await this.store.retain("planner-intent",this.key(c.operation,c.requestKey),intent)
  }
  async after(raw:string,reply:string){
    const c=parse(raw),r=parse(reply)
    // The frozen service rejects INVALID_INPUT before command execution. In particular do NOT
    // treat a later FORBIDDEN/UNAUTHENTICATED or transient error as proof about a prior lost effect.
    const invalid=r.kind==="error"&&r.code==="INVALID_INPUT"
    if(r.kind!=="settled"&&!invalid)return
    if(!invalid&&(r.identity?.projectId!==this.scope.projectId||r.identity?.operation!==c.operation||
      r.identity?.requestKey!==c.requestKey||!["success","rejected"].includes(r.outcome?.kind)))nativeUnavailable()
    const key=this.key(c.operation,c.requestKey),original=await this.store.read("planner-intent",key)
    if(original===undefined||parse(original).commandDigest!==sha(raw))nativeUnavailable()
    const taskReferences:any[]=[]
    if(!invalid&&r.outcome.kind==="success"&&c.operation==="create_task_revision"){
      const ref=r.outcome.result
      // MechanicalReceiptCodec deliberately omits family; the authenticated operation supplies
      // that discriminator. Do not mistake this receipt descriptor for a WorkReference envelope.
      if(!ref||Object.keys(ref).sort().join(",")!=="digest,generation,key"||ref.generation!==null||
        !same(ref.key,c.payload.key)||typeof ref.digest!=="string"||!/^[a-f0-9]{64}$/.test(ref.digest))nativeUnavailable()
      taskReferences.push({family:"task-revisions",key:ref.key,digest:ref.digest})
    }
    await this.store.retain("planner-settled",key,contextJSON({formatVersion:1,scope:this.scope,
      operation:c.operation,requestKey:c.requestKey,commandDigest:sha(raw),success:!invalid&&r.outcome.kind==="success",taskReferences}))
  }
  async snapshot(){
    const tasks=new Map<string,unknown>(),references=new Map<string,any>(),requests=new Set<string>()
    let armed=false,unknown=false
    for(const entry of await this.store.scan("planner-intent")){
      const i=parse(entry.raw)
      if(!same(i.scope,this.scope))continue
      const key=this.key(i.operation,i.requestKey)
      if(sha(key)!==entry.keyDigest||i.formatVersion?.value!=="1"||!Array.isArray(i.tasks))nativeUnavailable()
      armed=true
      const settled=await this.store.read("planner-settled",key)
      if(settled===undefined){unknown=true;continue}
      const s=parse(settled)
      if(s.formatVersion?.value!=="1"||!same(s.scope,this.scope)||s.commandDigest!==i.commandDigest||
        s.operation!==i.operation||s.requestKey!==i.requestKey||typeof s.success!=="boolean"||!Array.isArray(s.taskReferences))nativeUnavailable()
      if(s.success){
        for(const key of i.tasks)tasks.set(contextJSON(key),key)
        for(const ref of s.taskReferences){
          if(ref.family!=="task-revisions"||!i.tasks.some((key:any)=>same(key,ref.key))||typeof ref.digest!=="string"||
            !/^[a-f0-9]{64}$/.test(ref.digest))nativeUnavailable()
          const previous=references.get(contextJSON(ref.key))
          if(previous&&!same(previous,ref))nativeUnavailable()
          references.set(contextJSON(ref.key),ref)
        }
        if(i.requestId!==null)requests.add(i.requestId)
      }
    }
    return {armed,unknown,tasks:[...tasks.values()],references:[...references.values()],requests:[...requests].sort()}
  }
}

/** Fresh canonical observations of ONLY this chat's explicitly recorded/selected task scope.
 * In particular this does not adopt project-wide unassigned heads from controllerContext. */
export class NativePlannerProgress {
  constructor(private readonly activity:NativePlannerActivity,private readonly registry:NativeCredentialRegistry,
    private readonly order:NativeOwnerOrder){}
  private async list(family:string,signal:AbortSignal,select:(row:any)=>boolean=()=>true){
    const {projectId,sessionID}=this.activity.scope,channel=await this.registry.contextChannel(sessionID,signal)
    let cursor:string|null=null,count=0;const seen=new Set<string>(),rows:any[]=[]
    do{
      const page=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId,operation:"list_work_records",
        subject:{family,taskId:null,pageSize:32,cursor}}),signal)
      if(page.kind!=="read"||!Array.isArray(page.data?.items)||typeof page.data.complete!=="boolean"||
        page.data.complete!==(page.data.nextCursor===null))nativeUnavailable()
      for(const item of page.data.items){
        if(++count>8192||item.family!==family||item.key?.projectId!==projectId)nativeUnavailable()
        if(!select(item))continue
        const r=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId,operation:"read_"+family.replaceAll("-","_"),
          subject:{key:item.key,...(item.generation!==undefined?{generation:item.generation}:{digest:item.digest})}}),signal)
        if(r.kind!=="read"||r.data?.family!==family||!same(r.data.key,item.key))nativeUnavailable()
        rows.push(r.data)
      }
      cursor=page.data.nextCursor
      if(cursor!==null&&(typeof cursor!=="string"||seen.has(cursor)))nativeUnavailable()
      if(cursor)seen.add(cursor)
    }while(cursor)
    return rows
  }
  async observe(signal:AbortSignal):Promise<PlannerRoleProgress|undefined>{
    const scope=this.activity.scope,{sessionID,projectId,controllerId}=scope
    const identity=await this.registry.sourceIdentity(sessionID,signal)
    if(identity.scope!=="controller"||identity.storeId!==scope.storeId||identity.controllerId!==controllerId||identity.projectId!==projectId)nativeUnavailable()
    const original=await this.activity.snapshot(),proposal=await this.order.pending()
    if(!original.armed&&!proposal)return
    const canContinue=await this.registry.controllerPlanningAdmission(sessionID,signal)
    const steps:any[]=[],pending:any[]=[],resolvedRequestIds:string[]=[]
    const add=(id:string,state:string,source:unknown|null=null,kind="event")=>{
      steps.push({id,state,blockers:source===null?[]:[source]})
      if(state==="blocked")pending.push({id,kind,source})
    }
    const bindingJSON=contextJSON(scope)
    const result=(done=false):PlannerRoleProgress=>({controllerId,bindingJSON,nativeReady:canContinue,outcomeComplete:done,
      resolvedRequestIds,
      progressJSON:contextJSON({payload:{steps:steps.sort((a,b)=>a.id.localeCompare(b.id))}}),
      waitJSON:pending.length?contextJSON({payload:{pending,result:null,retrospective:null,notification:null}}):null})
    if(original.unknown){add("original-command","blocked",null,"effect");return result()}
    const knowledge=original.requests.length?await this.registry.planningKnowledgeContextChannel(sessionID,signal):undefined
    const approvalSources=new Map<string,string>()
    for(const requestId of original.requests){
      const r=parse(await knowledge!.send("reads",contextJSON({formatVersion:1,projectId,operation:"read_resolution",subject:{id:requestId}}),signal))
      if(r.kind==="error"&&r.code==="NOT_FOUND"){
        const requested=parse(await knowledge!.send("reads",contextJSON({formatVersion:1,projectId,operation:"read_request",subject:{id:requestId}}),signal))
        const record=requested.data?.record
        if(requested.kind!=="read"||record?.family!=="requests"||record.key!==requestId||record.payload?.projectId!==projectId)nativeUnavailable()
        const packet=record.payload.packet
        if(!Array.isArray(packet?.items)||!Array.isArray(packet.contexts)||!Array.isArray(packet.dependencies))nativeUnavailable()
        for(const ref of [...packet.items.flatMap((i:any)=>[i.target,...(i.before?[i.before]:[])]),...packet.contexts,...packet.dependencies])
          approvalSources.set(contextJSON(ref),requestId)
        add("approval-"+requestId,"blocked",{kind:"approval-request",projectId,requestId},"decision")
      }
      else {
        if(r.kind!=="read"||r.data?.record?.family!=="resolutions"||r.data.record.key!==requestId)nativeUnavailable()
        resolvedRequestIds.push(requestId)
      }
    }
    if(proposal&&!proposal.selected)add("owner-order","blocked",{kind:"owner-order",proposalDigest:proposal.proposalDigest},"decision")
    const keys=new Map<string,any>(original.tasks.map(key=>[contextJSON(key),key]))
    const references=new Map<string,any>(original.references.map(ref=>[contextJSON(ref.key),ref]))
    if(proposal)for(const task of proposal.selection.tasks){
      const ref=parse(task.taskJSON);keys.set(contextJSON(ref.key),ref.key);references.set(contextJSON(ref.key),ref)
    }
    // Canonical heads, not timestamps or list order, select the current revision of a task
    // this chat has already declared. Unknown task identities are never added from a broad list.
    const ids=new Set([...keys.values()].map(k=>k.taskId))
    if(ids.size){
      const heads=await this.list("task-heads",signal,r=>ids.has(r.key.taskId))
      for(const head of heads){
        for(const [encoded,key]of keys)if(key.taskId===head.key.taskId)keys.delete(encoded)
        for(const key of [head.payload.draft,head.payload.executable].filter(Boolean))keys.set(contextJSON(key),key)
      }
    }
    if(!keys.size){if(!steps.length)add("declare-exact-planner-frontier","active");return result()}
    if([...keys].some(([key])=>!references.has(key))&&proposal){
      const planRef=parse(proposal.selection.planRefJSON)
      const frame=await this.registry.dispatchContext(sessionID,planRef.key.id,signal),plan=parse(frame.planJSON)
      if(plan.family!=="dispatch-plans"||!same(plan.key,planRef.key)||sha(contextJSON(plan))!==planRef.digest)nativeUnavailable()
      for(const entry of frame.entries){
        const raw=entry.closedJSON?parse(entry.closedJSON).taskJSON:entry.taskJSON,r=parse(raw)
        if(keys.has(contextJSON(r.key)))references.set(contextJSON(r.key),{family:r.family,key:r.key,digest:sha(contextJSON(r))})
      }
    }
    const plans:any[]=[],integrations:any[]=[]
    for(const [encoded,key]of keys){
      const ref=references.get(encoded);if(!ref)nativeUnavailable()
      const observed=await this.registry.plannerTaskProgress(sessionID,contextJSON(ref),signal)
      for(const plan of observed.plans){
        const planKey=parse(plan.keyJSON)
        if(planKey.projectId!==projectId||!Array.isArray(plan.steps)||plan.steps.length>256||
          plan.steps.some((s:any)=>!uuidPattern.test(s.id)||!["pending","active","blocked","complete"].includes(s.state)||!Array.isArray(s.blockers)))
          nativeUnavailable()
        plans.push({key:planKey,task:key,steps:plan.steps})
      }
      for(const intent of observed.integrationStates){
        if(!uuidPattern.test(intent.id)||!["prepared","unknown","confirmed","not-performed"].includes(intent.state))nativeUnavailable()
        integrations.push(intent)
      }
    }
    const continuations=(await this.list("controller-continuations",signal,r=>r.key.controllerId===controllerId))
      .filter(r=>r.payload.source?.nativeSessionId===sessionID)
    const predecessors=new Set(continuations.map(r=>r.payload.previous?contextJSON(r.payload.previous.key):null).filter(Boolean))
    const tips=continuations.filter(r=>!predecessors.has(contextJSON(r.key)))
    if(tips.length>1)nativeUnavailable()
    const continuation=tips[0]?.payload
    if(continuation){
      if(continuation.frontier.some((f:any)=>!ids.has(f.task.taskId)))nativeUnavailable()
      if(continuation.gaps.length)add("canonical-context-gaps","blocked",null,"effect")
      for(const p of continuation.pending){
        // Unknown effects never become permission to retry. Unverified external wait prose
        // is actionable work to reconcile, not a permanent model-authored wait assertion.
        if(p.kind==="effect")add("pending-"+p.id,"blocked",null,"effect")
        else if(["decision","event"].includes(p.kind)&&p.source?.kind==="work"&&p.source.reference?.family==="conditions"){
          const ref=p.source.reference,channel=await this.registry.contextChannel(sessionID,signal)
          const status=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId,operation:"read_condition_status",
            subject:{key:ref.key,digest:ref.digest}}),signal)
          if(status.kind!=="read")nativeUnavailable()
          if(status.data.state!=="resolved")add("condition-"+p.id,"blocked",p.source,p.kind)
        }else if(["decision","event"].includes(p.kind)&&p.source!=null)add("pending-"+p.id,"blocked",p.source,p.kind)
        else add("reconcile-"+p.id,"active")
      }
      if(continuation.entries.some((entry:any)=>entry.entry.remainingObligationIds.length))
        add("canonical-remaining-obligations","active")
    }
    const assignments=await this.list("assignments",signal),acceptances=await this.list("acceptances",signal)
    const externallyBlockedTasks=new Set<string>()
    for(const key of keys.values()){
      const scoped=plans.filter(p=>same(p.task,key)),unfinished=scoped.flatMap(p=>p.steps).filter(s=>s.state!=="complete")
      if(unfinished.length&&unfinished.every(s=>s.state==="blocked"&&s.blockers.length&&s.blockers.every((b:any)=>
        b.kind==="knowledge"&&approvalSources.has(contextJSON(b.reference)))))externallyBlockedTasks.add(contextJSON(key))
    }
    for(const plan of plans)for(const step of plan.steps){
      const blocked=step.state!=="complete"&&externallyBlockedTasks.has(contextJSON(plan.task))
      add("plan-"+contextJSON(plan.key)+"-"+step.id,step.state==="complete"?"complete":blocked?"blocked":"active",
        blocked?{kind:"approval-sources",sources:step.blockers}:null,"decision")
    }
    for(const intent of integrations){
      if(intent.state==="unknown")add("integration-"+intent.id,"blocked",null,"effect")
      else if(intent.state==="prepared")add("integration-"+intent.id,"active")
    }
    let allAccepted=true
    for(const key of keys.values()){
      const own=assignments.filter(r=>same(r.payload.task,key)&&r.payload.intendedExecution.controllerId===controllerId&&
        !["completed","replaced"].includes(r.payload.state))
      const foreign=assignments.filter(r=>same(r.payload.task,key)&&r.payload.intendedExecution.controllerId!==controllerId&&
        !["completed","replaced"].includes(r.payload.state))
      let handoffReady=false
      for(const assignment of own){
        const ref=assignment.payload.checkpoint
        if(!ref)continue
        const channel=await this.registry.contextChannel(sessionID,signal)
        const checkpoint=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId,operation:"read_checkpoints",
          subject:{key:ref.key,digest:ref.digest}}),signal)
        if(checkpoint.kind!=="read"||checkpoint.data?.family!=="checkpoints"||!same(checkpoint.data.key,ref.key)||
          !same(checkpoint.data.payload?.task,key)||checkpoint.data.payload?.assignment?.assignmentId!==assignment.key.id||
          !same(checkpoint.data.payload.assignment.generation,assignment.payload.executionGeneration))nativeUnavailable()
        if(checkpoint.data?.payload?.handoff==="finished")handoffReady=true
      }
      const accepted=acceptances.some(r=>same(r.payload.task,key))
      if(own.length||!accepted)allAccepted=false
      if(accepted&&!own.length)add(contextJSON(key),"complete")
      else if(!own.length&&externallyBlockedTasks.has(contextJSON(key)))
        add(contextJSON(key),"blocked",{kind:"approval-sources",task:key},"decision")
      else if(handoffReady)add("reconcile-handoff-"+contextJSON(key),"active")
      else if(own.length&&own.every(r=>r.payload.state==="active"))
        add(contextJSON(key),"blocked",{kind:"assignment-set",assignments:own.map(r=>r.key)},"event")
      else if(!own.length&&foreign.length)add(contextJSON(key),"blocked",{kind:"foreign-assignment-set",assignments:foreign.map(r=>r.key)},"event")
      else add(contextJSON(key),"active")
    }
    return result(allAccepted&&steps.every(s=>s.state==="complete")&&pending.length===0)
  }
}
