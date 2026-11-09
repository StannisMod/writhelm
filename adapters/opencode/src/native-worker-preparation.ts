// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { randomUUID } from "node:crypto"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativePassiveWorkers } from "./native-passive-worker.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"
import type { BoundWorkChannel } from "./connected-tools.js"

const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
type Actual=Pick<ToolContext,"sessionID"|"messageID"|"id"|"signal">

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
    await this.registry.sourceIdentity(actual.sessionID,actual.signal)
    const selection=closedObject(parse(selectionJSON),["task","manifest","profile","resources","reviewSubjects"])
    if(selection.task?.projectId!==this.config.projectId||selection.manifest?.key?.projectId!==this.config.projectId||
      selection.manifest.family!=="input-manifests"||selection.profile?.projectId!==this.config.projectId||
      !Array.isArray(selection.resources)||!selection.resources.length||!Array.isArray(selection.reviewSubjects))nativeUnavailable()
    await this.custody.initialize()
    let id=preparationId
    const callKey=contextJSON([actual.sessionID,actual.messageID,actual.id])
    if(id===undefined){
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
