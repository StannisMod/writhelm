// SPDX-License-Identifier: AGPL-3.0-only
import { createHash, randomUUID } from "node:crypto"
import { NativeOwnerOrder, OwnerOrderHold } from "./native-owner-order.js"
import type { NativeCredentialRegistry } from "./native-credentials.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"

const parse=(s:string)=>exactJSON(s,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
const reference=(raw:string)=>{const r=parse(raw);return contextJSON({family:r.family,key:r.key,
  digest:createHash("sha256").update(contextJSON(r)).digest("hex")})}
const taskRecord=(entry:{taskJSON:string;closedJSON?:string|null})=>
  entry.closedJSON?parse(entry.closedJSON).taskJSON as string:entry.taskJSON
export interface LaunchAdmission {
  plan(planJSON:string,signal?:AbortSignal):Promise<void>
  task(task:unknown,signal:AbortSignal):Promise<void>
  preparation(selection:unknown,signal:AbortSignal):Promise<void>
  assignment(key:unknown,generation:unknown,signal:AbortSignal):Promise<void>
}
/** One production admission object for direct, automatic and recovered launch drivers.
 * Uses fresh controller-authorized service records, never model readiness/started assertions. */
export class NativeLaunchAdmission implements LaunchAdmission {
  constructor(readonly order:NativeOwnerOrder,private readonly registry:NativeCredentialRegistry,
    private readonly sessionID:string,private readonly projectId:string){}
  async propose(planId:string,edges:ReadonlyArray<{taskId:string;after:readonly string[]}>,proposalId:string=randomUUID()){
    const frame=await this.registry.dispatchContext(this.sessionID,planId)
    const tasks=frame.entries.map((entry:any)=>{
      const raw=taskRecord(entry),row=parse(raw)
      if(row.family!=="task-revisions"||row.key?.projectId!==this.projectId)nativeUnavailable()
      return {taskJSON:reference(raw),after:edges.find(e=>e.taskId===row.key.taskId)?.after??[]}
    })
    if(edges.some(e=>!tasks.some((t:any)=>parse(t.taskJSON).key.taskId===e.taskId))||
      new Set(edges.map(e=>e.taskId)).size!==edges.length)nativeUnavailable()
    return this.order.propose({proposalId,planRefJSON:reference(frame.planJSON),tasks})
  }
  async plan(planJSON:string,signal?:AbortSignal){
    if(signal?.aborted)nativeUnavailable()
    await this.registry.sourceIdentity(this.sessionID,signal)
    await this.order.admit(reference(planJSON),undefined,async()=>"unknown")
  }
  private async records(family:string,taskId:string|null,signal:AbortSignal){
    const channel=await this.registry.contextChannel(this.sessionID,signal)
    let cursor:string|null=null;const visited=new Set<string>(),rows:any[]=[]
    do{
      const page=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId:this.projectId,
        operation:"list_work_records",subject:{family,taskId,pageSize:32,cursor}}),signal)
      if(page.kind!=="read"||!Array.isArray(page.data?.items)||typeof page.data.complete!=="boolean"||
        page.data.complete!==(page.data.nextCursor===null))nativeUnavailable()
      for(const item of page.data.items){
        if(rows.length>=8192||item.family!==family||item.key?.projectId!==this.projectId)nativeUnavailable()
        const record=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId:this.projectId,
          operation:"read_"+family.replaceAll("-","_"),subject:{key:item.key,
            ...(item.generation!==undefined?{generation:item.generation}:{digest:item.digest})}}),signal)
        if(record.kind!=="read"||record.data?.family!==family||!same(record.data.key,item.key))nativeUnavailable()
        rows.push(record.data)
      }
      cursor=page.data.nextCursor
      if(cursor!==null&&(typeof cursor!=="string"||visited.has(cursor)))nativeUnavailable()
      if(cursor)visited.add(cursor)
    }while(cursor)
    return rows
  }
  async task(task:unknown,signal:AbortSignal){
    if(signal.aborted)nativeUnavailable()
    const current=await this.order.current(),plan=parse(current.selection.planRefJSON)
    const frame=await this.registry.dispatchContext(this.sessionID,plan.key.id,signal)
    if(reference(frame.planJSON)!==current.selection.planRefJSON)throw new OwnerOrderHold("OWNER_ORDER_PLAN_CHANGED")
    const row=frame.entries.find((entry:any)=>same(parse(taskRecord(entry)).key,task))
    if(!row)throw new OwnerOrderHold("OWNER_ORDER_TASK_NOT_SELECTED")
    await this.order.admit(reference(frame.planJSON),reference(taskRecord(row)),async prior=>{
      const key=parse(prior).key
      const assignments=await this.records("assignments",key.taskId,signal)
      const matching=assignments.filter(a=>same(a.payload?.task,key))
      // A fresh ACTIVE record is sufficient evidence of launch, not completion or acceptance.
      if(matching.some(a=>a.payload.state==="active"))return "started"
      const intents=await this.records("native-intents",key.taskId,signal)
      return intents.some(i=>i.payload?.operation==="start"&&i.payload.outcome==="confirmed"&&
        matching.some(a=>a.key.id===i.payload.assignment?.assignmentId))?"started":
        intents.some(i=>i.payload?.operation==="start"&&i.payload.outcome!=="rejected")?"unknown":"not-started"
    })
    if(signal.aborted)nativeUnavailable()
  }
  async assignment(key:unknown,generation:unknown,signal:AbortSignal){
    const expected=parse(contextJSON(generation))
    if(!/^(0|[1-9][0-9]*)$/.test(expected?.value??""))nativeUnavailable()
    // Admission asks which immutable task owns the CURRENT assignment. It does not change the
    // original command/selector/generation being replayed by a recovered driver.
    const channel=await this.registry.contextChannel(this.sessionID,signal)
    const exact=await sendContext(channel,"reads",contextJSON({formatVersion:2,projectId:this.projectId,
      operation:"read_assignments",subject:{key,generation}}),signal)
    let record:any
    if(exact.kind==="read"&&exact.data?.family==="assignments"&&same(exact.data.key,key))record=exact.data
    else if(exact.kind==="error"&&exact.code==="STALE_GENERATION"){
      const rows=await this.records("assignments",null,signal);record=rows.find(row=>same(row.key,key))
    }else nativeUnavailable()
    if(!record||BigInt(record.payload.generation.value)<BigInt(expected.value))nativeUnavailable()
    const a=record.payload
    await this.preparation({task:a.task,manifest:a.manifest,profile:a.profile,resources:a.requiredResources,
      reviewSubjects:a.reviewSubjects},signal)
  }
  async preparation(selection:unknown,signal:AbortSignal){
    const supplied=parse(contextJSON(selection)),current=await this.order.current(),plan=parse(current.selection.planRefJSON)
    const frame=await this.registry.dispatchContext(this.sessionID,plan.key.id,signal)
    const row=frame.entries.find((entry:any)=>same(parse(taskRecord(entry)).key,supplied.task))
    if(!row)throw new OwnerOrderHold("OWNER_ORDER_TASK_NOT_SELECTED")
    const expected=row.closedJSON?parse(row.closedJSON).selectionJSON:row.selectionJSON
    if(!same(parse(expected),supplied))throw new OwnerOrderHold("OWNER_ORDER_INPUTS_CHANGED")
    await this.task(supplied.task,signal)
  }
}
