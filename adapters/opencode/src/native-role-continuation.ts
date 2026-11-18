// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"
import type { LinuxNativeCustody } from "./native-runtime-custody.js"

export type RoleProgress=Readonly<{
  assignmentId:string;executionGeneration:string;bindingJSON:string;nativeReady:boolean;finishedHandoff:boolean;
  planJSON:string;checkpointJSON:string|null
}>
export type PlannerRoleProgress=Readonly<{
  controllerId:string;bindingJSON:string;nativeReady:boolean;outcomeComplete:boolean;
  progressJSON:string;waitJSON:string|null
}>
type Progress=RoleProgress|PlannerRoleProgress
const planner=(p:Progress):p is PlannerRoleProgress=>"controllerId" in p
const complete=(p:Progress)=>planner(p)?p.outcomeComplete:p.finishedHandoff
const progress=(p:Progress)=>planner(p)?p.progressJSON:p.planJSON
const checkpoint=(p:Progress)=>planner(p)?p.waitJSON:p.checkpointJSON
const actor=(p:Progress)=>planner(p)?{kind:"controller",controllerId:p.controllerId}:
  {kind:"assignment",assignmentId:p.assignmentId,executionGeneration:p.executionGeneration}
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
export type RoleContinuationState="finished"|"admission-held"|"background-wait"|"external-wait"|
  "unknown-effect-held"|"continued"|"stalled"|"dispatch-unknown"
/** Outcome boundary, not a semantic reviewer, shell monitor, scheduler or writer fence.
 * The service owns finishedHandoff (including reviewer outcome attribution/exact subjects).
 * One admitted continuation per semantic obligation frontier prevents idle-to-model loops.
 * No automatic resend after an uncertain native synthetic admission, including across reload.
 */
export class NativeRoleContinuation {
  private readonly busy=new Set<string>()
  private readonly status=new Map<string,RoleContinuationState>()
  private closed=false
  constructor(private readonly custody:Pick<LinuxNativeCustody,"read"|"retain"|"replace">,
    private readonly observe:(sessionID:string)=>Promise<Progress|undefined>,
    private readonly send:(input:{sessionID:string;id:string;text:string;metadata:Record<string,string>;
      delivery:"queue";resume:true})=>Promise<unknown>){}
  state(sessionID:string){return this.status.get(sessionID)}
  private set(sessionID:string,state:RoleContinuationState){this.status.set(sessionID,state);return state}
  async idle(event:{sessionID:string;background:readonly {sessionID:string}[]}){
    const sessionID=event.sessionID
    if(this.closed||this.busy.has(sessionID))return
    this.busy.add(sessionID)
    try{
      const frame=await this.observe(sessionID)
      if(this.closed||!frame)return
      if(!/^ses_[A-Za-z0-9_-]{1,240}$/.test(sessionID)||
        !/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(planner(frame)?frame.controllerId:frame.assignmentId)||
        (!planner(frame)&&(!/^(0|[1-9][0-9]*)$/.test(frame.executionGeneration)||
          BigInt(frame.executionGeneration)>9223372036854775807n))||
        typeof frame.nativeReady!=="boolean"||typeof complete(frame)!=="boolean"||
        typeof frame.bindingJSON!=="string"||!frame.bindingJSON.length)nativeUnavailable()
      if(complete(frame))return this.set(sessionID,"finished")
      if(!frame.nativeReady)return this.set(sessionID,"admission-held")
      if(event.background.some(job=>job.sessionID===sessionID))return this.set(sessionID,"background-wait")
      const plan=parse(progress(frame)),cp=checkpoint(frame)?parse(checkpoint(frame)!).payload:undefined
      const steps=plan.payload?.steps
      if(!Array.isArray(steps)||steps.length>256||steps.some((s:any)=>typeof s.id!=="string"||
        !["pending","active","blocked","complete"].includes(s.state))||
        new Set(steps.map((s:any)=>s.id)).size!==steps.length)nativeUnavailable()
      const pending=cp?.pending??[]
      if(!Array.isArray(pending)||pending.length>256||pending.some((p:any)=>typeof p.id!=="string"||
        !["effect","event","decision","obligation","delivery"].includes(p.kind)))nativeUnavailable()
      if(pending.some((p:any)=>p.kind==="effect"))return this.set(sessionID,"unknown-effect-held")
      const unfinished=steps.filter((s:any)=>s.state!=="complete")
      const blocked=unfinished.length>0&&unfinished.every((s:any)=>s.state==="blocked"&&Array.isArray(s.blockers)&&s.blockers.length>0)
      if(blocked&&pending.length>0&&pending.every((p:any)=>["decision","event"].includes(p.kind)&&p.source!=null))
        return this.set(sessionID,"external-wait")
      // Exclude global Git basis, plan generation, rewritten explanations and fresh checkpoint IDs:
      // unrelated publication or cosmetic churn must not mint unlimited "progress" continuations.
      const frontier=contextJSON({sessionID,actor:actor(frame),
        bindingJSON:frame.bindingJSON,steps:steps.map((s:any)=>({id:s.id,state:s.state})).sort((a:any,b:any)=>a.id.localeCompare(b.id)),
        pending:pending.map((p:any)=>({id:p.id,kind:p.kind,source:p.source})).sort((a:any,b:any)=>a.id.localeCompare(b.id)),
        handoff:cp?{result:cp.result!==null,retrospective:cp.retrospective!==null,notification:cp.notification!==null}:null})
      const key=hash(frontier),id="msg_writhelm_role_"+key.slice(0,32)
      const text=planner(frame)?
        "Your chat-bound planner outcome remains unfinished. Continue the known authorized planning/coordination steps without an owner nudge. "+
        "Complete necessary exact tasks/contracts and required approvals; show the concise graph and obtain the owner's native launch-order choice before launch. "+
        "Reconcile actual reports, evidence, independent review and exact acceptance within authorized scope. Do not take another chat's task frontier, "+
        "invent authority, approve as the owner, retry an unknown effect with new IDs or use Goal UI. A component pass or announcing next work is not completion. "+
        "Use native questions or exact canonical requests for genuine decisions, retain blockers/resumption points, and stop for a real pause/failure/unknown effect.":
        "The canonical assigned outcome is not finished. Continue the known authorized task steps without an owner nudge. "+
        "An executor must finish its scoped result/checks and durable exact handoff. A reviewer must finish its task-linked exact-subject assessment, "+
        "evidence/findings and durable verification-report handoff, without editing assessed code. Announcing the next step is not completion. "+
        "Preserve current permissions, native pause/stop, original operation IDs and all unknown-effect holds. "+
        "If truly waiting or blocked, retain the exact blocker/resumption point in canonical task records and report it; do not fabricate completion."
      const metadata:Record<string,string>=planner(frame)?{controllerId:frame.controllerId,frontier:key}:
        {assignmentId:frame.assignmentId,executionGeneration:frame.executionGeneration,frontier:key}
      const original=contextJSON({sessionID,id,text,metadata,delivery:"queue",resume:true})
      const scope=hash(contextJSON([sessionID,actor(frame),frame.bindingJSON]))
      const previous=await this.custody.read("role-continuation-head",scope)
      if(previous!==undefined){
        const old=parse(previous),oldKey=old.metadata?.frontier
        if(typeof oldKey!=="string"||!/^[a-f0-9]{64}$/.test(oldKey))nativeUnavailable()
        const effect=await this.custody.read("role-continuation-dispatched",oldKey)
        if(effect!==undefined){
          if(effect!==previous)nativeUnavailable()
          if(await this.custody.read("role-continuation-ack",oldKey)!==previous)return this.set(sessionID,"dispatch-unknown")
        }
      }
      await this.custody.retain("role-continuation-original",key,original)
      if(previous===undefined)await this.custody.retain("role-continuation-head",scope,original)
      else if(previous!==original)await this.custody.replace("role-continuation-head",scope,hash(previous),original)
      if(await this.custody.read("role-continuation-head",scope)!==original)return this.set(sessionID,"dispatch-unknown")
      const dispatched=await this.custody.read("role-continuation-dispatched",key)
      if(dispatched!==undefined){
        if(dispatched!==original)nativeUnavailable()
        return this.set(sessionID,await this.custody.read("role-continuation-ack",key)===original?"stalled":"dispatch-unknown")
      }
      // Refresh service/native admission before the sole native input effect. A changed observation
      // is not permission to send an already obsolete continuation.
      const fresh=await this.observe(sessionID)
      if(this.closed||!fresh||!fresh.nativeReady||complete(fresh)||
        contextJSON(actor(fresh))!==contextJSON(actor(frame))||
        fresh.bindingJSON!==frame.bindingJSON||progress(fresh)!==progress(frame)||checkpoint(fresh)!==checkpoint(frame))
        return this.set(sessionID,"admission-held")
      if(!await this.custody.retain("role-continuation-dispatched",key,original))return this.set(sessionID,"dispatch-unknown")
      if(this.closed)return this.set(sessionID,"dispatch-unknown")
      try{
        const reply=await this.send({sessionID,id,text,metadata,delivery:"queue",resume:true})
        const value=reply as any,body=value?.payload??value
        if(value?.id!==id||value?.type!=="synthetic"||value?.sessionID!==sessionID||body.text!==text||
          contextJSON(body.metadata)!==contextJSON(metadata))return this.set(sessionID,"dispatch-unknown")
        await this.custody.retain("role-continuation-ack",key,original)
        return this.set(sessionID,"continued")
      }catch{return this.set(sessionID,"dispatch-unknown")}
    }catch{
      if(!this.closed)return this.set(sessionID,"admission-held")
    }finally{this.busy.delete(sessionID)}
  }
  async register(ctx:Plugin.Context,afterIdle?:(sessionID:string)=>Promise<void>){
    const hook=await ctx.session.hook("experimental.idle",async event=>{
      await this.idle(event)
      try{await afterIdle?.(event.sessionID)}catch{this.set(event.sessionID,"admission-held")}
    })
    return {dispose:async()=>{this.closed=true;await hook.dispose()}}
  }
}
