// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { createHash, randomUUID } from "node:crypto"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativePassiveWorkers } from "./native-passive-worker.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"
import type { BoundWorkChannel } from "./connected-tools.js"
import type { NativeInitialStart } from "./native-initial-start.js"

const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
type Actual=Pick<ToolContext,"sessionID"|"messageID"|"id"|"signal">
type PlannedAttempt=Readonly<{planRefJSON:string;previousRefJSON?:string;rootPreparationId?:string;startId?:string}>

/** Concrete reserve -> passive native creation -> scoped enrollment -> bind. No initial prompt,
 * start intent, active acknowledgement, result acceptance, resource release or model call here.
 * An uncertain reply reuses the same retained IDs/command bytes; current service policy still wins. */
export class NativeWorkerPreparation {
  constructor(private readonly registry:NativeCredentialRegistry,private readonly passive:NativePassiveWorkers,
    private readonly custody:LinuxNativeCustody,private readonly controllerSessionID:string,
    private readonly config:Readonly<{projectId:string;controllerId:string;hostId:string;worktreeParent:string}>){}
  private async assignment(channel:BoundWorkChannel,id:string,taskId:string,signal:AbortSignal){
    let cursor:string|null=null,count=0;const seen=new Set<string>()
    do{
      const result=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId:this.config.projectId,operation:"list_work_records",
        subject:{family:"assignments",taskId,pageSize:32,cursor}}),signal)
      if(result.kind!=="read"||!Array.isArray(result.data?.items))nativeUnavailable()
      for(const row of result.data.items){
        if(++count>8192)nativeUnavailable()
        if(row.family!=="assignments"||row.key?.projectId!==this.config.projectId)nativeUnavailable()
        if(row.key.id!==id)continue
        const exact=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId:this.config.projectId,operation:"read_assignments",
          subject:{key:row.key,generation:row.generation}}),signal)
        if(exact.kind!=="read"||exact.data?.key?.id!==id||!same(exact.data.payload.generation,row.generation))nativeUnavailable()
        return exact.data
      }
      cursor=result.data.nextCursor
      if(cursor!==null&&(typeof cursor!=="string"||seen.has(cursor)))nativeUnavailable()
      if(cursor)seen.add(cursor)
    }while(cursor)
    nativeUnavailable()
  }
  async run(selectionJSON:string,actual:Actual,preparationId?:string){
    if(actual.sessionID!==this.controllerSessionID||actual.signal.aborted)nativeUnavailable()
    const channel=await this.registry.resolve(actual)
    if(channel?.scope!=="controller"||channel.projectID!==this.config.projectId)nativeUnavailable()
    return this.execute(selectionJSON,actual.sessionID,actual.signal,channel,preparationId,contextJSON([actual.sessionID,actual.messageID,actual.id]))
  }
  /** Engine callers supply a durable original ID; no native tool-call identity is invented. */
  async runController(selectionJSON:string,preparationId:string,signal:AbortSignal){
    if(signal.aborted||typeof preparationId!=="string"||!uuidPattern.test(preparationId))nativeUnavailable()
    const channel=await this.registry.contextChannel(this.controllerSessionID,signal)
    if(channel.scope!=="controller"||channel.projectID!==this.config.projectId)nativeUnavailable()
    return this.execute(selectionJSON,this.controllerSessionID,signal,channel,preparationId)
  }
  /** Bind the exact retained reserve command to the canonical plan before any reservation. */
  async runPlannedController(selectionJSON:string,preparationId:string,attempt:PlannedAttempt,signal:AbortSignal){
    if(signal.aborted||typeof preparationId!=="string"||!uuidPattern.test(preparationId))nativeUnavailable()
    const channel=await this.registry.contextChannel(this.controllerSessionID,signal)
    if(channel.scope!=="controller"||channel.projectID!==this.config.projectId)nativeUnavailable()
    return this.execute(selectionJSON,this.controllerSessionID,signal,channel,preparationId,undefined,attempt)
  }
  async restorePlannedController(preparationId:string,attempt:PlannedAttempt,signal:AbortSignal){
    if(signal.aborted||typeof preparationId!=="string"||!uuidPattern.test(preparationId))nativeUnavailable()
    const channel=await this.registry.contextChannel(this.controllerSessionID,signal)
    if(channel.scope!=="controller"||channel.projectID!==this.config.projectId)nativeUnavailable()
    await this.custody.initialize()
    const raw=await this.custody.read("preparation-driver",preparationId)
    if(raw===undefined)nativeUnavailable()
    const job=closedObject(parse(raw),["formatVersion","id","controllerSessionID","selectionJSON","reserveJSON","principalId","credentialId","delegationId","enrollmentKey","bindKey"])
    if(job.id!==preparationId||job.controllerSessionID!==this.controllerSessionID||typeof job.selectionJSON!=="string")nativeUnavailable()
    return this.execute(job.selectionJSON,this.controllerSessionID,signal,channel,preparationId,undefined,attempt)
  }
  /** Recovery never creates an attempt when its original protected driver is absent. */
  async restoreController(preparationId:string,signal:AbortSignal){
    if(signal.aborted||typeof preparationId!=="string"||!uuidPattern.test(preparationId))nativeUnavailable()
    const channel=await this.registry.contextChannel(this.controllerSessionID,signal)
    if(channel.scope!=="controller"||channel.projectID!==this.config.projectId)nativeUnavailable()
    await this.custody.initialize()
    const raw=await this.custody.read("preparation-driver",preparationId)
    if(raw===undefined)nativeUnavailable()
    const job=closedObject(parse(raw),["formatVersion","id","controllerSessionID","selectionJSON","reserveJSON","principalId","credentialId","delegationId","enrollmentKey","bindKey"])
    if(job.id!==preparationId||job.controllerSessionID!==this.controllerSessionID||typeof job.selectionJSON!=="string")nativeUnavailable()
    return this.execute(job.selectionJSON,this.controllerSessionID,signal,channel,preparationId)
  }
  /** A protected original plus a current-authorized canonical rejection is necessary, not
   * sufficient, to authorize a subsequent attempt. No new ID or effect is created here. */
  async retryEligibilityController(preparationId:string,signal:AbortSignal){
    if(signal.aborted||typeof preparationId!=="string"||!uuidPattern.test(preparationId))nativeUnavailable()
    const channel=await this.registry.contextChannel(this.controllerSessionID,signal)
    if(channel.scope!=="controller"||channel.projectID!==this.config.projectId)nativeUnavailable()
    await this.registry.sourceIdentity(this.controllerSessionID,signal)
    await this.custody.initialize()
    const raw=await this.custody.read("preparation-driver",preparationId)
    if(raw===undefined)nativeUnavailable()
    const job=closedObject(parse(raw),["formatVersion","id","controllerSessionID","selectionJSON","reserveJSON","principalId","credentialId","delegationId","enrollmentKey","bindKey"])
    if(job.formatVersion?.value!=="1"||job.id!==preparationId||job.controllerSessionID!==this.controllerSessionID||typeof job.reserveJSON!=="string")nativeUnavailable()
    const original=parse(job.reserveJSON)
    if(original.operation!=="reserve_assignment"||original.projectId!==this.config.projectId||original.payload?.intendedExecution?.controllerId!==this.config.controllerId)nativeUnavailable()
    return this.registry.reservationRetryEligibility(this.controllerSessionID,job.reserveJSON,signal)
  }
  private async execute(selectionJSON:string,sessionID:string,signal:AbortSignal,channel:BoundWorkChannel,preparationId?:string,callKey?:string,planned?:PlannedAttempt){
    const actual={sessionID,signal}
    await this.registry.sourceIdentity(actual.sessionID,actual.signal)
    const selection=closedObject(parse(selectionJSON),["task","manifest","profile","resources","reviewSubjects"])
    if(selection.task?.projectId!==this.config.projectId||selection.manifest?.key?.projectId!==this.config.projectId||
      selection.manifest.family!=="input-manifests"||selection.profile?.projectId!==this.config.projectId||
      !Array.isArray(selection.resources)||!selection.resources.length||!Array.isArray(selection.reviewSubjects))nativeUnavailable()
    await this.custody.initialize()
    let id=preparationId
    if(id===undefined){
      if(callKey===undefined)nativeUnavailable()
      const previous=await this.custody.read("preparation-call",callKey)
      id=previous===undefined?randomUUID():parse(previous).preparationId
      if(typeof id!=="string"||!uuidPattern.test(id))nativeUnavailable()
      await this.custody.retain("preparation-call",callKey,contextJSON({formatVersion:1,preparationId:id,selectionJSON:contextJSON(selection)}))
    }
    if(typeof id!=="string"||!uuidPattern.test(id))nativeUnavailable()
    let raw=await this.custody.read("preparation-driver",id)
    if(raw===undefined){
      const assignmentId=randomUUID(),workspaceId=randomUUID(),intentId=randomUUID(),executionId=randomUUID()
      const payload={assignmentId,task:selection.task,manifest:selection.manifest,profile:selection.profile,workspaceId,resources:selection.resources,
        reviewSubjects:selection.reviewSubjects,intentId,correlationId:randomUUID(),bodies:[],intendedExecution:{controllerId:this.config.controllerId,
          executionId,template:selection.reviewSubjects.length?"reviewer-v1":"executor-v1",nativeHostId:this.config.hostId,
          nativeSessionId:"ses_writhelm_"+randomUUID().replaceAll("-","")}}
      raw=contextJSON({formatVersion:1,id,controllerSessionID:this.controllerSessionID,selectionJSON:contextJSON(selection),
        reserveJSON:contextJSON({formatVersion:3,projectId:this.config.projectId,requestKey:randomUUID(),operation:"reserve_assignment",payload}),
        principalId:randomUUID(),credentialId:randomUUID(),delegationId:randomUUID(),enrollmentKey:randomUUID(),bindKey:randomUUID()})
      await this.custody.retain("preparation-driver",id,raw)
    }
    const job=closedObject(parse(raw),["formatVersion","id","controllerSessionID","selectionJSON","reserveJSON","principalId","credentialId","delegationId","enrollmentKey","bindKey"])
    if(job.formatVersion?.value!=="1"||job.id!==id||job.controllerSessionID!==actual.sessionID||job.selectionJSON!==contextJSON(selection))nativeUnavailable()
    const reserved=parse(job.reserveJSON),expected=reserved.payload
    if(reserved.operation!=="reserve_assignment"||reserved.projectId!==this.config.projectId||
      !same(expected.task,selection.task)||!same(expected.manifest,selection.manifest)||!same(expected.profile,selection.profile)||
      !same(expected.resources,selection.resources)||!same(expected.reviewSubjects,selection.reviewSubjects)||
      expected.intendedExecution?.controllerId!==this.config.controllerId||expected.intendedExecution?.nativeHostId!==this.config.hostId)nativeUnavailable()
    if(planned){
      const plan=parse(planned.planRefJSON)
      if(plan.family!=="dispatch-plans"||plan.key?.projectId!==this.config.projectId||!uuidPattern.test(plan.key?.id)||!/^[a-f0-9]{64}$/.test(plan.digest))nativeUnavailable()
      const successor=planned.previousRefJSON!==undefined||planned.rootPreparationId!==undefined||planned.startId!==undefined
      let payload:Record<string,unknown>={formatVersion:successor?2:1,plan,preparationId:id,reservation:reserved}
      if(successor){
        if(typeof planned.previousRefJSON!=="string"||typeof planned.rootPreparationId!=="string"||!uuidPattern.test(planned.rootPreparationId)||
          typeof planned.startId!=="string"||!uuidPattern.test(planned.startId))nativeUnavailable()
        const previous=parse(planned.previousRefJSON)
        if(previous.family!=="dispatch-attempts"||previous.key?.projectId!==this.config.projectId||!uuidPattern.test(previous.key?.id)||!/^[a-f0-9]{64}$/.test(previous.digest))nativeUnavailable()
        payload={...payload,previous,rootPreparationId:planned.rootPreparationId,startId:planned.startId}
      }
      let binding=await this.custody.read("dispatch-attempt-command",id)
      if(binding===undefined){
        binding=contextJSON({formatVersion:3,projectId:this.config.projectId,requestKey:randomUUID(),operation:"bind_dispatch_attempt",payload})
        await this.custody.retain("dispatch-attempt-command",id,binding)
      }
      const retained=parse(binding)
      if(retained.projectId!==this.config.projectId||retained.operation!=="bind_dispatch_attempt"||!same(retained.payload,payload)||!uuidPattern.test(retained.requestKey))nativeUnavailable()
      const bindChannel=await this.registry.dispatchAttemptChannel(actual.sessionID)
      if(bindChannel.scope!=="controller"||bindChannel.projectID!==this.config.projectId)nativeUnavailable()
      const bound=await sendContext(bindChannel,"commands",binding,actual.signal)
      if(bound.kind!=="settled"||bound.outcome?.kind!=="success")return {preparationId:id,phase:"attempt-binding-held",outcome:bound}
      if(bound.identity?.projectId!==this.config.projectId||bound.identity?.operation!=="bind_dispatch_attempt"||bound.identity?.requestKey!==retained.requestKey||
        bound.outcome?.result?.key?.projectId!==this.config.projectId||bound.outcome?.result?.key?.id!==id)nativeUnavailable()
    }
    const result=await sendContext(channel,"commands",job.reserveJSON,actual.signal)
    if(result.kind!=="settled"||result.outcome?.kind!=="success")return {preparationId:id,phase:"reservation-held",outcome:result}
    let current=await this.assignment(channel,expected.assignmentId,selection.task.taskId,actual.signal),a=current.payload
    if(!same(a.task,expected.task)||!same(a.manifest,expected.manifest)||!same(a.profile,expected.profile)||a.workspaceId!==expected.workspaceId||
      a.executionGeneration?.value!=="0"||!same(a.intendedExecution,expected.intendedExecution))nativeUnavailable()
    const credential=await this.registry.prepare(job.principalId,job.credentialId)
    if(!a.binding){
      const prepared=await this.passive.prepare(expected.assignmentId,"0",actual.signal)
      const observation=await this.passive.proof(expected.assignmentId,"0");if(!observation)nativeUnavailable()
      const enrollmentName="preparation-enroll"
      let enrollment=await this.custody.read(enrollmentName,id)
      if(enrollment===undefined){
        const frame=await this.registry.preparationContext(actual.sessionID,expected.assignmentId,"0",actual.signal)
        enrollment=contextJSON({formatVersion:3,projectId:this.config.projectId,requestKey:job.enrollmentKey,operation:"register_delegation",payload:{
          delegationId:job.delegationId,binding:{controllerId:this.config.controllerId,projectId:this.config.projectId,principalId:credential.principalId,
            initialCredentialId:credential.credentialId,taskId:expected.task.taskId,assignmentId:expected.assignmentId,executionId:expected.intendedExecution.executionId,
            executionGeneration:0,template:expected.intendedExecution.template,subjectResults:expected.reviewSubjects},
          expectedAssignmentGeneration:parse(frame.assignmentJSON).payload.generation,verifierVersion:1,verifierDigest:credential.verifierDigest,knowledgeCapabilities:["READ"]}})
        await this.custody.retain(enrollmentName,id,enrollment)
      }
      const enroller=await this.registry.preparationEnrollmentChannel(actual.sessionID)
      const enrolled=await sendContext(enroller,"commands",enrollment,actual.signal)
      if(enrolled.kind!=="settled"||enrolled.outcome?.kind!=="success")return {preparationId:id,phase:"enrollment-held",outcome:enrolled}
      let bind=await this.custody.read("preparation-bind",id)
      if(bind===undefined){
        const frame=await this.registry.preparationContext(actual.sessionID,expected.assignmentId,"0",actual.signal),i=parse(frame.intentJSON)
        bind=contextJSON({formatVersion:3,projectId:this.config.projectId,requestKey:job.bindKey,operation:"bind_assignment",payload:{assignmentId:expected.assignmentId,
          expectedGeneration:parse(frame.assignmentJSON).payload.generation,intentId:i.key.id,expectedIntentGeneration:i.payload.generation,nativeHostId:this.config.hostId,
          nativeSessionId:prepared.sessionID,workspaceId:expected.workspaceId,observations:[{kind:"artifact",artifact:parse(observation.snapshotJSON)}],bodies:[]}})
        await this.custody.retain("preparation-bind",id,bind)
      }
      const bound=await sendContext(channel,"commands",bind,actual.signal)
      if(bound.kind!=="settled"||bound.outcome?.kind!=="success")return {preparationId:id,phase:"binding-held",outcome:bound}
      current=await this.assignment(channel,expected.assignmentId,selection.task.taskId,actual.signal);a=current.payload
    }
    if(!a.binding||a.execution?.executionId!==expected.intendedExecution.executionId||a.execution.principalId!==credential.principalId||
      a.execution.credentialId!==credential.credentialId||a.delegationId!==job.delegationId||!same(a.intendedExecution,expected.intendedExecution))nativeUnavailable()
    const directory=path.join(this.config.worktreeParent,expected.workspaceId)
    await this.registry.bind(expected.intendedExecution.nativeSessionId,credential.credentialId,directory,
      {assignmentId:expected.assignmentId,executionGeneration:"0",bindingJSON:contextJSON(a.binding)})
    return {preparationId:id,phase:"binding-confirmed",state:a.state,currentIntent:a.currentIntent,assignmentId:expected.assignmentId,executionGeneration:"0",binding:a.binding,
      assignmentSelector:{family:"assignments",key:current.key,generation:a.generation},nativeSessionId:expected.intendedExecution.nativeSessionId,workspaceId:expected.workspaceId}
  }
}

/** Lifecycle-owned bounded frontier. Canonical reserve remains the final readiness/claim arbiter.
 * Materialization publishes only closure-gapped inputs, never a verified packet or a worker start. */
export class NativeDispatchFrontier {
  private readonly controller=new AbortController()
  private readonly inflight=new Map<string,Promise<void>>()
  private readonly lastScan=new Map<string,{basis:string;at:number}>()
  private timer:ReturnType<typeof setInterval>|undefined
  private scanning=false
  private dirty=false
  private closed=false
  private failure:unknown
  constructor(private readonly registry:NativeCredentialRegistry,private readonly preparation:NativeWorkerPreparation,
    private readonly initial:NativeInitialStart,private readonly custody:LinuxNativeCustody,private readonly sessionID:string,
    private readonly planId:string,private readonly capacity:number,private readonly pollMillis:number){
    if(!uuidPattern.test(planId)||!Number.isInteger(capacity)||capacity<1||capacity>32||!Number.isInteger(pollMillis)||pollMillis<250||pollMillis>60000)nativeUnavailable()
  }
  get lastFailure(){return this.failure}
  start(){
    if(this.closed||this.timer)nativeUnavailable()
    this.timer=setInterval(()=>this.wake(),this.pollMillis);this.timer.unref()
    this.wake()
  }
  wake(){
    if(this.closed)return
    this.dirty=true
    if(!this.scanning)void this.drain()
  }
  private async drain(){
    this.scanning=true
    try { while(this.dirty&&!this.closed){this.dirty=false;try {await this.scan();this.failure=undefined}catch(e){this.failure=e}} }
    finally {this.scanning=false;if(this.dirty&&!this.closed)this.wake()}
  }
  private async scan(){
    const signal=this.controller.signal,frame=await this.registry.dispatchContext(this.sessionID,this.planId,signal)
    if(this.closed)return
    const plan=parse(frame.planJSON),basis=contextJSON(frame.basis),planDigest=createHash("sha256").update(frame.planJSON,"utf8").digest("hex")
    const planRefJSON=contextJSON({family:"dispatch-plans",key:plan.key,digest:planDigest})
    const attempts=frame.attemptsJSON.map((raw:string)=>({raw,row:parse(raw)}))
    const active=frame.activeAssignments.map((raw:string)=>parse(raw))
    const activeTasks=new Set<string>(active.map((r:any)=>r.payload?.task?.taskId).filter((id:unknown):id is string=>typeof id==="string"))
    let occupied=active.length+[...this.inflight.keys()].filter(id=>!activeTasks.has(id)).length
    for(const projected of frame.entries){
      if(this.closed)break
      const task=parse(projected.taskJSON),selection=parse(projected.selectionJSON),id=task.key?.taskId
      const declaration=plan.payload.entries.find((e:any)=>e.preparationId===projected.preparationId&&e.startId===projected.startId)
      const last=typeof id==="string"?this.lastScan.get(id):undefined
      if(typeof id!=="string"||!declaration||this.inflight.has(id)||
        (last?.basis===basis&&Date.now()-last.at<this.pollMillis))continue
      const future=Array.isArray(declaration.futureDependencies)&&declaration.futureDependencies.length>0
      if(future){
        if(task.payload?.kind!=="implementation"||task.payload?.state!=="executable"||!Array.isArray(task.payload.unresolved)||task.payload.unresolved.length)continue
        let raw=await this.custody.read("dispatch-materialization",projected.preparationId)
        if(raw===undefined&&!projected.futureReady)continue
        if(raw===undefined){
          const revision=task.key?.revision?.value
          if(typeof revision!=="string"||!/^(0|[1-9][0-9]*)$/.test(revision)||BigInt(revision)>=9223372036854775807n||
            !uuidPattern.test(task.key?.projectId)||!uuidPattern.test(task.key?.taskId))nativeUnavailable()
          raw=contextJSON({formatVersion:3,projectId:task.key.projectId,requestKey:randomUUID(),operation:"materialize_dispatch_dependency",
            payload:{plan:parse(planRefJSON),task:task.key,newTask:{...task.key,revision:parse((BigInt(revision)+1n).toString())},
              verificationPlan:{projectId:task.key.projectId,id:randomUUID(),revision:0},manifest:{projectId:task.key.projectId,id:randomUUID()}}})
          await this.custody.retain("dispatch-materialization",projected.preparationId,raw)
        }
        const command=parse(raw)
        if(command.operation!=="materialize_dispatch_dependency"||command.projectId!==task.key?.projectId||!uuidPattern.test(command.requestKey)||
          !same(command.payload?.plan,parse(planRefJSON))||!same(command.payload?.task,task.key))nativeUnavailable()
        this.lastScan.set(id,{basis,at:Date.now()})
        const operation=(async()=>{
          const channel=await this.registry.dispatchMaterializationChannel(this.sessionID)
          if(channel.scope!=="controller"||channel.projectID!==command.projectId)nativeUnavailable()
          const result=await sendContext(channel,"commands",raw!,signal)
          if(result.kind!=="settled"||result.outcome?.kind!=="success")return
          if(result.identity?.operation!==command.operation||result.identity?.projectId!==command.projectId||result.identity?.requestKey!==command.requestKey||
            result.outcome.result?.key?.projectId!==command.projectId||result.outcome.result?.key?.id!==command.requestKey)nativeUnavailable()
        })().catch(e=>{if(!this.closed)this.failure=e}).finally(()=>{this.inflight.delete(id);this.wake()})
        this.inflight.set(id,operation)
        continue
      }
      if(task.payload?.kind!=="implementation"||task.payload?.state!=="executable"||!Array.isArray(task.payload.unresolved)||task.payload.unresolved.length||
        !Array.isArray(task.payload.dependencies)||task.payload.dependencies.some((d:any)=>d.selectedResult===null))continue
      const chain=attempts.filter(({row}:any)=>(row.payload.rootPreparationId??row.payload.preparationId)===projected.preparationId)
      const previous=new Set(chain.map(({row}:any)=>row.payload.previous?.key?.id).filter(Boolean))
      const tips=chain.filter(({row}:any)=>!previous.has(row.key.id))
      if(tips.length>1)nativeUnavailable()
      const tipRecord=tips[0],tip=tipRecord?.row,attemptId=tip?.payload.preparationId??projected.preparationId
      const ownActive=active.filter((r:any)=>r.payload?.task?.taskId===id)
      if(ownActive.length>1)nativeUnavailable()
      if(ownActive.length && (!tip||tip.payload.reservation?.payload?.assignmentId!==ownActive[0].key?.id))continue
      // A held/incompatible canonical claim is a current-H scheduling hint, never a replacement
      // for atomic reservation. Existing active attempts still need reconciliation.
      if(!ownActive.length&&projected.claimsAvailable===false)continue
      if(!ownActive.length && occupied>=this.capacity)continue
      const retained=await this.custody.read("preparation-driver",attemptId)
      if(tip&&retained===undefined)continue // canonical original exists, but operational custody is missing
      if(tip&&retained!==undefined){
        const job=parse(retained)
        if(typeof job.reserveJSON!=="string"||!same(parse(job.reserveJSON),tip.payload.reservation))nativeUnavailable()
      }
      const binding:PlannedAttempt=tip?.payload.previous?{planRefJSON,previousRefJSON:contextJSON(tip.payload.previous),
        rootPreparationId:tip.payload.rootPreparationId,startId:tip.payload.startId}:{planRefJSON}
      const startId=tip?.payload.startId??projected.startId
      if(!uuidPattern.test(startId)||!uuidPattern.test(attemptId)||selection.task?.taskId!==id)nativeUnavailable()
      this.lastScan.set(id,{basis,at:Date.now()})
      if(!ownActive.length)occupied++
      const operation=(async()=>{
        const prepared=retained===undefined?await this.preparation.runPlannedController(projected.selectionJSON,attemptId,binding,signal):
          await this.preparation.restorePlannedController(attemptId,binding,signal)
        if(prepared.phase==="reservation-held"&&tipRecord&&!ownActive.length){
          const eligibility=await this.preparation.retryEligibilityController(attemptId,signal)
          if(eligibility!=="definitive-refusal")return
          const previousRefJSON=contextJSON({family:"dispatch-attempts",key:tip.key,
            digest:createHash("sha256").update(tipRecord.raw,"utf8").digest("hex")})
          let raw=await this.custody.read("dispatch-successor",attemptId)
          if(raw===undefined){
            raw=contextJSON({formatVersion:1,previousRefJSON,preparationId:randomUUID(),startId:randomUUID()})
            await this.custody.retain("dispatch-successor",attemptId,raw)
          }
          const successor=closedObject(parse(raw),["formatVersion","previousRefJSON","preparationId","startId"])
          if(successor.formatVersion?.value!=="1"||successor.previousRefJSON!==previousRefJSON||
            !uuidPattern.test(successor.preparationId)||!uuidPattern.test(successor.startId)||successor.preparationId===successor.startId)nativeUnavailable()
          const nextBinding:PlannedAttempt={planRefJSON,previousRefJSON,rootPreparationId:projected.preparationId,startId:successor.startId}
          const nextRetained=await this.custody.read("preparation-driver",successor.preparationId)
          if(nextRetained===undefined)await this.preparation.runPlannedController(projected.selectionJSON,successor.preparationId,nextBinding,signal)
          else await this.preparation.restorePlannedController(successor.preparationId,nextBinding,signal)
          return
        }
        if(prepared.phase!=="binding-confirmed"||!['bound','active'].includes(prepared.state))return
        const startRaw=await this.custody.read("initial-driver",startId)
        if(startRaw!==undefined)await this.initial.restoreController(startId,signal)
        else if(prepared.state==="bound"&&prepared.currentIntent===null)
          await this.initial.runController(contextJSON({assignment:prepared.assignmentSelector,binding:prepared.binding}),startId,signal)
      })().catch(e=>{if(!this.closed)this.failure=e}).finally(()=>{this.inflight.delete(id);this.wake()})
      this.inflight.set(id,operation)
    }
  }
  async close(){
    this.closed=true;this.dirty=false;if(this.timer)clearInterval(this.timer);this.timer=undefined
    this.controller.abort();await Promise.allSettled([...this.inflight.values()])
  }
}
