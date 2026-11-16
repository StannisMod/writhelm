// SPDX-License-Identifier: AGPL-3.0-only
import { randomUUID } from "node:crypto"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"
import type { CandidateDeliveryProfile } from "./native-result-retention.js"
import type { GitSnapshot } from "./linux-git-witness.js"

type Delivery=Readonly<{status?:unknown;close():Promise<void>}>
type ActivationStatus=Readonly<{phase:"waiting-for-results"|"candidate-delivery-activated";deliveryStatus:unknown;profile:CandidateDeliveryProfile|null}>
type Selection=Readonly<{inputs:readonly Readonly<{base:GitSnapshot}>[]}>
/** Only a canonical plan reference is selected by the planner. The complete service selection and
 * check plan are recaptured by the ordinary delivery loop at each effect boundary. */
export class CanonicalCandidateActivation {
  private readonly active=new Map<string,Delivery>()
  private serial:Promise<unknown>=Promise.resolve()
  private closed=false
  constructor(private readonly custody:LinuxNativeCustody,private readonly projectId:string,
    private readonly controllerSessionID:string,
    private readonly selection:(reference:string,signal?:AbortSignal)=>Promise<Selection|null>,
    private readonly checks:(reference:string,signal?:AbortSignal)=>Promise<unknown>,
    private readonly create:(profile:CandidateDeliveryProfile)=>Delivery){}
  activate(reference:string,signal?:AbortSignal):Promise<ActivationStatus>{
    const operation=this.serial.then(()=>this.activateOne(reference,signal))
    this.serial=operation.catch(()=>{})
    return operation
  }
  private async activateOne(reference:string,signal?:AbortSignal):Promise<ActivationStatus>{
    const ref=closedObject(exactJSON(reference,nativeUnavailable),["family","key","digest"])
    const key=closedObject(ref.key,["projectId","id"])
    if(this.closed||signal?.aborted||ref.family!=="dispatch-plans"||key.projectId!==this.projectId||
      typeof key.id!=="string"||!uuidPattern.test(key.id)||typeof ref.digest!=="string"||!/^([0-9a-f]{64})$/.test(ref.digest))nativeUnavailable()
    const canonical=contextJSON(ref)
    await this.checks(canonical,signal) // exact canonical plan, current credential, complete check set
    const selection=await this.selection(canonical,signal)
    if(selection===null)return {phase:"waiting-for-results",deliveryStatus:null,profile:null}
    const base=selection.inputs[0]?.base
    if(!base||!selection.inputs.length||selection.inputs.length>256||
      selection.inputs.some(input=>contextJSON(input.base)!==contextJSON(base)))nativeUnavailable()
    if(this.closed||signal?.aborted)nativeUnavailable()
    const current=this.active.get(canonical)
    if(current){
      const retained=await this.custody.read("candidate-activation",canonical)
      if(retained===undefined)nativeUnavailable()
      return {phase:"candidate-delivery-activated",deliveryStatus:current.status??null,profile:this.profile(retained,canonical)}
    }
    if(this.active.size>=32)nativeUnavailable()
    let raw=await this.custody.read("candidate-activation",canonical)
    if(raw===undefined){
      const profile:CandidateDeliveryProfile={planRefJSON:canonical,operationId:randomUUID(),candidateId:randomUUID(),head:base,
        time:`${Math.floor(Date.now()/1000)} +0000`,testPlanId:randomUUID(),capacity:4}
      raw=JSON.stringify({formatVersion:1,projectId:this.projectId,controllerSessionID:this.controllerSessionID,profile})
      if(!await this.custody.retain("candidate-activation",canonical,raw))nativeUnavailable()
    }
    const profile=this.profile(raw,canonical)
    if(contextJSON(profile.head)!==contextJSON(base))nativeUnavailable()
    if(this.closed||signal?.aborted)nativeUnavailable()
    const delivery=this.create(profile)
    this.active.set(canonical,delivery)
    return {phase:"candidate-delivery-activated",deliveryStatus:delivery.status??null,profile}
  }
  private profile(raw:string,reference:string):CandidateDeliveryProfile {
    exactJSON(raw,nativeUnavailable)
    const r=closedObject(JSON.parse(raw),["formatVersion","projectId","controllerSessionID","profile"])
    const p=closedObject(r.profile,["planRefJSON","operationId","candidateId","head","time","testPlanId","capacity"])
    if(r.formatVersion!==1||r.projectId!==this.projectId||r.controllerSessionID!==this.controllerSessionID||p.planRefJSON!==reference||
      ![p.operationId,p.candidateId,p.testPlanId].every(id=>typeof id==="string"&&uuidPattern.test(id))||
      typeof p.time!=="string"||!/^\d{10} \+0000$/.test(p.time)||p.capacity!==4)nativeUnavailable()
    return p as CandidateDeliveryProfile
  }
  async restore(){
    for(const entry of await this.custody.scan("candidate-activation")){
      exactJSON(entry.raw,nativeUnavailable)
      const reference=JSON.parse(entry.raw)?.profile?.planRefJSON
      if(typeof reference!=="string")nativeUnavailable()
      this.profile(entry.raw,reference)
      if(await this.custody.read("candidate-activation",reference)!==entry.raw)nativeUnavailable()
      await this.activate(reference)
    }
  }
  async close(){this.closed=true;await this.serial;await Promise.allSettled([...this.active.values()].map(value=>value.close()));this.active.clear()}
}
