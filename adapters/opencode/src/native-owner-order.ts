// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import type { LinuxNativeCustody } from "./native-runtime-custody.js"

type Store=Pick<LinuxNativeCustody,"read"|"retain"|"replace">
export type OwnerOrderScope=Readonly<{storeId:string;projectId:string;controllerId:string;sessionID:string}>
export type OwnerOrderTask=Readonly<{taskJSON:string;after:readonly string[]}>
export type OwnerOrderSelection=Readonly<{
  proposalId:string;planRefJSON:string;tasks:readonly OwnerOrderTask[]
}>
export class OwnerOrderHold extends Error {
  constructor(readonly code:string){super(code)}
}
function hold(code:string):never {throw new OwnerOrderHold(code)}
const hash=(raw:string)=>createHash("sha256").update(raw,"utf8").digest("hex")
const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const normalize=(raw:string)=>contextJSON(parse(raw))
const digest=(v:unknown)=>typeof v==="string"&&/^[a-f0-9]{64}$/.test(v)
const natural=(v:any)=>v?.value!==undefined&&/^(0|[1-9][0-9]*)$/.test(v.value)&&
  BigInt(v.value)<=9223372036854775807n

/** Operational launch choice, not task/contract approval, authority, a dependency rewrite or an
 * effect-retry permit. Only the trusted native owner surface may call select(). Model-facing
 * consumers receive proposal IDs and admission, never an owner-decision submission primitive.
 *
 * `after` is explicit owner ordering. An ordinary task list has no implicit edges. Each edge
 * waits for a fresh authoritative observation that its predecessor started; it does not wait
 * for completion/acceptance of unrelated work. Existing service dependencies and claims still win.
 */
export class NativeOwnerOrder {
  private tail:Promise<unknown>=Promise.resolve()
  constructor(private readonly store:Store,private readonly scope:OwnerOrderScope){
    if(![scope.storeId,scope.projectId,scope.controllerId].every(id=>uuidPattern.test(id))||
      !/^ses_[A-Za-z0-9_-]{1,240}$/.test(scope.sessionID))nativeUnavailable()
  }
  private key(){return contextJSON(this.scope)}
  private selection(input:OwnerOrderSelection){
    if(!uuidPattern.test(input.proposalId)||!input.tasks.length||input.tasks.length>128)nativeUnavailable()
    const plan=closedObject(parse(input.planRefJSON),["family","key","digest"])
    closedObject(plan.key,["projectId","id"])
    if(plan.family!=="dispatch-plans"||plan.key?.projectId!==this.scope.projectId||
      !uuidPattern.test(plan.key?.id)||!digest(plan.digest))nativeUnavailable()
    const ids=new Set<string>()
    const tasks=input.tasks.map(task=>{
      const ref=closedObject(parse(task.taskJSON),["family","key","digest"])
      closedObject(ref.key,["projectId","taskId","revision"])
      if(ref.family!=="task-revisions"||ref.key?.projectId!==this.scope.projectId||
        !uuidPattern.test(ref.key?.taskId)||!natural(ref.key?.revision)||!digest(ref.digest)||
        ids.has(ref.key.taskId)||!Array.isArray(task.after))nativeUnavailable()
      ids.add(ref.key.taskId)
      return {taskJSON:contextJSON(ref),after:[...task.after].sort()}
    })
    for(let i=0;i<tasks.length;i++){
      const task=tasks[i]!,id=parse(task.taskJSON).key.taskId
      if(new Set(task.after).size!==task.after.length||task.after.some(p=>p===id||!ids.has(p)))nativeUnavailable()
    }
    const byId=new Map(tasks.map(task=>[parse(task.taskJSON).key.taskId,task]))
    const complete=new Set<string>(),visiting=new Set<string>()
    const visit=(id:string)=>{
      if(complete.has(id))return
      if(visiting.has(id))nativeUnavailable()
      visiting.add(id)
      for(const prior of byId.get(id)!.after)visit(prior)
      visiting.delete(id);complete.add(id)
    }
    for(const id of ids)visit(id)
    return {proposalId:input.proposalId,planRefJSON:contextJSON(plan),tasks}
  }
  private serial<T>(fn:()=>Promise<T>):Promise<T>{
    const run=this.tail.then(fn);this.tail=run.catch(()=>{});return run
  }
  async propose(selection:OwnerOrderSelection){
    const normalized=this.selection(selection)
    const raw=contextJSON({formatVersion:1,scope:this.scope,selection:normalized})
    await this.store.retain("owner-order-proposal",contextJSON([this.scope,selection.proposalId]),raw)
    const previous=await this.store.read("owner-order-pending",this.key())
    if(previous===undefined)await this.store.retain("owner-order-pending",this.key(),raw)
    else if(previous!==raw)await this.store.replace("owner-order-pending",this.key(),hash(previous),raw)
    return {proposalId:selection.proposalId,proposalDigest:hash(raw)}
  }
  async pending(){
    const raw=await this.store.read("owner-order-pending",this.key())
    if(raw===undefined)return
    const p=closedObject(parse(raw),["formatVersion","scope","selection"])
    if(p.formatVersion?.value!=="1"||contextJSON(p.scope)!==contextJSON(this.scope))nativeUnavailable()
    const selection=this.selection(p.selection)
    if(await this.store.read("owner-order-proposal",contextJSON([this.scope,selection.proposalId]))!==raw)nativeUnavailable()
    let current:Awaited<ReturnType<NativeOwnerOrder["current"]>>|undefined
    try{current=await this.current()}catch(e){if(!(e instanceof OwnerOrderHold)||e.code!=="OWNER_ORDER_REQUIRED")throw e}
    return {selection,proposalDigest:hash(raw),expectedDecision:current?.decisionDigest??null,
      selected:current?.selection.proposalId===selection.proposalId}
  }
  /** Native-main owner click only. expectedDecision is a CAS witness from the rendered view.
   * An old view cannot overwrite a later decision. Idempotent repeat preserves the same record. */
  select(proposalId:string,proposalDigest:string,expectedDecision:string|null){
    return this.serial(async()=>{
      if(!uuidPattern.test(proposalId)||!digest(proposalDigest)||
        expectedDecision!==null&&!digest(expectedDecision))nativeUnavailable()
      const raw=await this.store.read("owner-order-proposal",contextJSON([this.scope,proposalId]))
      if(raw===undefined||hash(raw)!==proposalDigest)hold("OWNER_ORDER_PROPOSAL_CHANGED")
      const proposal=closedObject(parse(raw),["formatVersion","scope","selection"])
      if(proposal.formatVersion?.value!=="1"||contextJSON(proposal.scope)!==contextJSON(this.scope)||
        contextJSON(this.selection(proposal.selection))!==contextJSON(proposal.selection))nativeUnavailable()
      const decision=contextJSON({formatVersion:1,scope:this.scope,proposalId,proposalDigest})
      const previous=await this.store.read("owner-order-current",this.key())
      if(previous===decision)return {decisionDigest:hash(decision)}
      if((previous===undefined?null:hash(previous))!==expectedDecision)hold("OWNER_ORDER_SELECTION_STALE")
      // Retain original decision before pointer publication. A cut never invents an answered view.
      await this.store.retain("owner-order-decision",hash(decision),decision)
      if(previous===undefined)await this.store.retain("owner-order-current",this.key(),decision)
      else await this.store.replace("owner-order-current",this.key(),hash(previous),decision)
      if(await this.store.read("owner-order-current",this.key())!==decision)hold("OWNER_ORDER_SELECTION_UNKNOWN")
      return {decisionDigest:hash(decision)}
    })
  }
  async current(){
    const raw=await this.store.read("owner-order-current",this.key())
    if(raw===undefined)hold("OWNER_ORDER_REQUIRED")
    const d=closedObject(parse(raw),["formatVersion","scope","proposalId","proposalDigest"])
    if(d.formatVersion?.value!=="1"||contextJSON(d.scope)!==contextJSON(this.scope)||
      !uuidPattern.test(d.proposalId)||!digest(d.proposalDigest)||
      await this.store.read("owner-order-decision",hash(raw))!==raw)nativeUnavailable()
    const proposal=await this.store.read("owner-order-proposal",contextJSON([this.scope,d.proposalId]))
    if(proposal===undefined||hash(proposal)!==d.proposalDigest)nativeUnavailable()
    const p=closedObject(parse(proposal),["formatVersion","scope","selection"])
    if(p.formatVersion?.value!=="1"||contextJSON(p.scope)!==contextJSON(this.scope))nativeUnavailable()
    return {selection:this.selection(p.selection),decisionDigest:hash(raw)}
  }
  /** The caller supplies current authorized service observations, not model readiness booleans.
   * Check decision stability again after awaits so a changed owner selection holds the effect. */
  async admit(planRefJSON:string,taskJSON:string|undefined,
    observe:(taskJSON:string)=>Promise<"not-started"|"started"|"unknown">){
    const current=await this.current(),selection=current.selection
    if(normalize(planRefJSON)!==selection.planRefJSON)hold("OWNER_ORDER_PLAN_CHANGED")
    if(taskJSON!==undefined){
      const task=selection.tasks.find(row=>row.taskJSON===normalize(taskJSON))
      if(!task)hold("OWNER_ORDER_TASK_NOT_SELECTED")
      for(const predecessor of task.after){
        const prior=selection.tasks.find(row=>parse(row.taskJSON).key.taskId===predecessor)!
        const state=await observe(prior.taskJSON)
        if(state!=="started")hold(state==="unknown"?"OWNER_ORDER_PREDECESSOR_UNKNOWN":"OWNER_ORDER_PREDECESSOR_PENDING")
      }
    }
    if((await this.current()).decisionDigest!==current.decisionDigest)hold("OWNER_ORDER_SELECTION_CHANGED")
    return current.decisionDigest
  }
}
