// SPDX-License-Identifier: AGPL-3.0-only
import { randomUUID } from "node:crypto"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { NativeInitialAdapter } from "./native-initial.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"

const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
type Actual=Pick<ToolContext,"sessionID"|"messageID"|"id"|"signal">

/** Canonical request-start -> one retained NORMAL prompt -> held-boundary witness -> canonical
 * observed ACTIVE -> release. Parent/tool cancellation never implies rollback, safe retry with new
 * IDs, completed work or fenced writers. This helper contains no provider/model implementation. */
export class NativeInitialStart {
  constructor(private readonly registry:NativeCredentialRegistry,private readonly initial:NativeInitialAdapter,
    private readonly custody:LinuxNativeCustody,private readonly deployment:LinuxNativeCustody,
    private readonly controllerSessionID:string,private readonly projectId:string){}
  async run(selectionJSON:string,actual:Actual,startId?:string){
    if(actual.sessionID!==this.controllerSessionID||actual.signal.aborted)nativeUnavailable()
    const channel=await this.registry.resolve(actual)
    if(channel?.scope!=="controller"||channel.projectID!==this.projectId)nativeUnavailable()
    await this.registry.sourceIdentity(actual.sessionID,actual.signal)
    const selection=closedObject(parse(selectionJSON),["assignment","binding"]),key=selection.assignment?.key,binding=selection.binding
    if(selection.assignment?.family!=="assignments"||key?.projectId!==this.projectId||binding?.family!=="assignment-bindings"||
      binding.key?.projectId!==this.projectId||binding.key?.assignmentId!==key.id||binding.key?.generation?.value!=="0")nativeUnavailable()
    await this.custody.initialize()
    const call=contextJSON([actual.sessionID,actual.messageID,actual.id])
    let id=startId
    if(id===undefined){
      const old=await this.custody.read("initial-call",call)
      id=old===undefined?randomUUID():parse(old).startId
      if(typeof id!=="string"||!uuidPattern.test(id))nativeUnavailable()
      await this.custody.retain("initial-call",call,contextJSON({formatVersion:1,startId:id,selectionJSON:contextJSON(selection)}))
    }
    if(typeof id!=="string"||!uuidPattern.test(id))nativeUnavailable()
    let raw=await this.custody.read("initial-driver",id)
    if(raw===undefined){
      const exact=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId:this.projectId,operation:"read_assignments",
        subject:{key,generation:selection.assignment.generation}}),actual.signal)
      if(exact.kind!=="read"||!same(exact.data?.key,key)||!same(exact.data?.payload?.binding,binding))nativeUnavailable()
      const a=exact.data.payload
      if(a.state!=="bound"||a.currentIntent!==null)return {startId:id,phase:"start-held",state:a.state,currentIntent:a.currentIntent}
      if(await this.deployment.read("execution-profile",contextJSON(a.profile))===undefined)
        return {startId:id,phase:"start-held",reason:"No qualified execution deployment is configured; no start intent or model wake"}
      const intentId=randomUUID()
      raw=contextJSON({formatVersion:1,id,controllerSessionID:actual.sessionID,selectionJSON:contextJSON(selection),intentId,observeKey:randomUUID(),
        requestJSON:contextJSON({formatVersion:3,projectId:this.projectId,requestKey:randomUUID(),operation:"request_native_transition",payload:{
          assignmentId:key.id,expectedGeneration:selection.assignment.generation,intentId,correlationId:randomUUID(),transition:"start",checkpoint:null,bodies:[]}})})
      await this.custody.retain("initial-driver",id,raw)
    }
    const job=closedObject(parse(raw),["formatVersion","id","controllerSessionID","selectionJSON","intentId","observeKey","requestJSON"])
    if(job.formatVersion?.value!=="1"||job.id!==id||job.controllerSessionID!==actual.sessionID||job.selectionJSON!==contextJSON(selection))nativeUnavailable()
    const command=parse(job.requestJSON)
    if(command.operation!=="request_native_transition"||command.projectId!==this.projectId||command.payload?.transition!=="start"||
      command.payload.assignmentId!==key.id||command.payload.intentId!==job.intentId)nativeUnavailable()
    const requested=await sendContext(channel,"commands",job.requestJSON,actual.signal)
    if(requested.kind!=="settled"||requested.outcome?.kind!=="success")return {startId:id,phase:"start-intent-held",outcome:requested}
    const subject={assignmentId:key.id,executionGeneration:"0",bindingJSON:contextJSON(binding)}
    let frame=await this.registry.initialContext(actual.sessionID,subject,job.intentId,actual.signal)
    const envelope=parse(frame.envelopeJSON)
    if(frame.active){
      await this.initial.prepare(subject,job.intentId);await this.initial.release(envelope.nativeSessionId)
      return {startId:id,phase:"active-admission-restored",assignmentId:key.id,intentId:job.intentId,completionAsserted:false}
    }
    await this.initial.deliver(subject,job.intentId)
    const witness=await this.initial.witness(job.intentId)
    if(!witness?.heldJSON)await this.initial.awaitBoundary(envelope.nativeSessionId,actual.signal)
    frame=await this.registry.initialContext(actual.sessionID,subject,job.intentId,actual.signal)
    let observed=await this.custody.read("initial-observe-command",id)
    if(observed===undefined){
      const a=parse(frame.assignmentJSON).payload,i=parse(frame.intentJSON)
      observed=contextJSON({formatVersion:3,projectId:this.projectId,requestKey:job.observeKey,operation:"observe_native_transition",payload:{
        assignmentId:key.id,expectedGeneration:a.generation,intentId:job.intentId,expectedIntentGeneration:i.payload.generation,outcome:"confirmed",
        observations:[{kind:"work",reference:binding}],bodies:[]}})
      await this.custody.retain("initial-observe-command",id,observed)
    }
    const result=await sendContext(channel,"commands",observed,actual.signal)
    if(result.kind!=="settled"||result.outcome?.kind!=="success")return {startId:id,phase:"active-observation-held",outcome:result}
    await this.initial.release(envelope.nativeSessionId)
    return {startId:id,phase:"active-admission-confirmed",assignmentId:key.id,intentId:job.intentId,completionAsserted:false}
  }
}
