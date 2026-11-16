// SPDX-License-Identifier: AGPL-3.0-only
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"

type Frontier=Readonly<{start():void;wake():void;close():Promise<void>}>
/** Activation selects an existing canonical plan, never a model-supplied ready flag or bootstrap
 * path. Frontier effects retain their own service qualification/authority/original-effect gates. */
export class CanonicalDispatchActivation {
  private readonly active=new Map<string,Frontier>()
  private serial:Promise<unknown>=Promise.resolve()
  private closed=false
  constructor(private readonly custody:LinuxNativeCustody,private readonly projectId:string,
    private readonly controllerSessionID:string,private readonly observe:(planId:string,signal?:AbortSignal)=>Promise<{planJSON:string}>,
    private readonly create:(planId:string)=>Frontier){}
  activate(planId:string,signal?:AbortSignal):Promise<void>{
    const operation=this.serial.then(()=>this.activateOne(planId,signal))
    this.serial=operation.catch(()=>{})
    return operation
  }
  private async activateOne(planId:string,signal?:AbortSignal){
    if(this.closed||!uuidPattern.test(planId)||signal?.aborted)nativeUnavailable()
    const frame=await this.observe(planId,signal)
    const plan=exactJSON(frame.planJSON,nativeUnavailable)
    if(plan.family!=="dispatch-plans"||plan.key?.projectId!==this.projectId||plan.key?.id!==planId)nativeUnavailable()
    if(this.closed||signal?.aborted)nativeUnavailable()
    const current=this.active.get(planId)
    if(current){current.wake();return}
    if(this.active.size>=32)nativeUnavailable()
    const original=JSON.stringify({formatVersion:1,projectId:this.projectId,controllerSessionID:this.controllerSessionID,planId})
    const prior=await this.custody.read("dispatch-activation",planId)
    if(prior!==undefined&&prior!==original)nativeUnavailable()
    if(prior===undefined&&!await this.custody.retain("dispatch-activation",planId,original))nativeUnavailable()
    if(this.closed||signal?.aborted)nativeUnavailable()
    const frontier=this.create(planId)
    // Retained activation only authorizes starting the qualified reconciler, not a native effect.
    this.active.set(planId,frontier)
    try{frontier.start()}catch(error){this.active.delete(planId);await frontier.close();throw error}
  }
  async restore(){
    for(const entry of await this.custody.scan("dispatch-activation")){
      const record=closedObject(exactJSON(entry.raw,nativeUnavailable),["formatVersion","projectId","controllerSessionID","planId"])
      if(record.formatVersion?.value!=="1"||record.projectId!==this.projectId||record.controllerSessionID!==this.controllerSessionID||
        typeof record.planId!=="string"||!uuidPattern.test(record.planId)||await this.custody.read("dispatch-activation",record.planId)!==entry.raw)nativeUnavailable()
      await this.activate(record.planId)
    }
  }
  async close(){this.closed=true;await this.serial;await Promise.allSettled([...this.active.values()].map(value=>value.close()));this.active.clear()}
}
