// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"
import type { LinuxNativeCustody } from "./native-runtime-custody.js"
import type { OwnerOrderScope } from "./native-owner-order.js"
import type { NativeRoleContinuation } from "./native-role-continuation.js"

export type PlannerNativeState=Readonly<{id:string;directory:string;busy:number|undefined;idle:number|undefined;
  outcome:string|undefined;archived:boolean;reverted:boolean;binding:string}>
type Store=Pick<LinuxNativeCustody,"read"|"retain"|"scan">
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
/** Resume only a retained successful controller wait at the SAME observed native execution and
 * binding. Background polling reads state; it never calls a model API. Failed/interrupted/archived/
 * reverted/newer executions and new incarnations retire the old wait, not adopt it.
 * This is cooperative native admission with last-moment rechecks, not a host-side CAS or fencing.
 */
export class NativePlannerWait {
  private closed=false
  private running:Promise<void>|undefined
  private timer:ReturnType<typeof setInterval>|undefined
  private failure:string|undefined
  private guard:((state:PlannerNativeState)=>boolean)|undefined
  constructor(private readonly store:Store,private readonly scope:OwnerOrderScope,private readonly directory:string,
    private readonly nativeState:()=>Promise<PlannerNativeState>,private readonly roles:NativeRoleContinuation){}
  get lastFailure(){return this.failure}
  async afterIdle(sessionID:string){
    if(this.closed||sessionID!==this.scope.sessionID||this.roles.state(sessionID)!=="external-wait")return
    const state=await this.nativeState()
    if(this.closed||state.id!==sessionID||state.directory!==this.directory||state.archived||state.reverted||
      !Number.isSafeInteger(state.busy)||state.busy!<0||!state.binding)nativeUnavailable()
    const raw=contextJSON({formatVersion:1,scope:this.scope,directory:this.directory,busy:state.busy,binding:state.binding})
    await this.store.retain("planner-wait",raw,raw)
  }
  /** Called from the common role observer on every refresh, including immediately before native
   * input admission. Normal successful pre-idle use has no background guard installed. */
  async permitted(){
    if(this.closed)return false
    return this.guard?this.guard(await this.nativeState()):true
  }
  poll(){
    if(this.closed)return Promise.resolve()
    if(this.running)return this.running
    this.running=this.scan().then(()=>{this.failure=undefined},()=>{this.failure="planner-wait-observation-held"})
      .finally(()=>{this.running=undefined})
    return this.running
  }
  private async scan(){
    const rows=await this.store.scan("planner-wait")
    if(rows.length>512)nativeUnavailable()
    for(const row of rows){
      if(this.closed)return
      const anchor=parse(row.raw)
      if(contextJSON(anchor.scope)!==contextJSON(this.scope))continue
      if(hash(row.raw)!==row.keyDigest||anchor.formatVersion?.value!=="1"||anchor.directory!==this.directory||
        typeof anchor.binding!=="string")nativeUnavailable()
      if(await this.store.read("planner-wait-retired",row.keyDigest)!==undefined)continue
      const busy=Number(anchor.busy?.value)
      if(!Number.isSafeInteger(busy)||busy<0)nativeUnavailable()
      const matching=(s:PlannerNativeState)=>s.id===this.scope.sessionID&&s.directory===this.directory&&
        !s.archived&&!s.reverted&&s.binding===anchor.binding&&s.busy===busy
      const allowed=(s:PlannerNativeState)=>matching(s)&&s.outcome==="succeeded"&&
        typeof s.idle==="number"&&s.idle>=busy
      const state=await this.nativeState()
      if(!matching(state)||["failed","interrupted"].includes(state.outcome??"")){
        await this.store.retain("planner-wait-retired",row.keyDigest,"native-wait-held-or-obsolete");continue
      }
      if(!allowed(state))continue // successful pre-idle may not yet be a terminal transition
      this.guard=allowed
      try{
        await this.roles.idle({sessionID:this.scope.sessionID,background:[]})
        const status=this.roles.state(this.scope.sessionID)
        if(status!=="external-wait")await this.store.retain("planner-wait-retired",row.keyDigest,"native-wait-observed")
      }finally{this.guard=undefined}
    }
  }
  start(){
    if(this.closed||this.timer)nativeUnavailable()
    this.timer=setInterval(()=>void this.poll(),3000);this.timer.unref()
    void this.poll()
  }
  async close(){this.closed=true;if(this.timer)clearInterval(this.timer);await this.running}
}
