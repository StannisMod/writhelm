// SPDX-License-Identifier: AGPL-3.0-only
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { approvalReference, exactLink, type ExactLink } from "./link.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"
import { closedObject, nativeUnavailable } from "./native-runtime-client.js"
import { createHash } from "node:crypto"

const key=(sessionID:string,ref:ExactLink)=>contextJSON({sessionID,reference:approvalReference(ref)})
/** Operational UI delivery only, never canonical approval. Immutable exact intents and rendered
 * acknowledgements survive lost replies. No owner credential, model call or approval is stored. */
export class ApprovalDelivery {
  constructor(private readonly custody:LinuxNativeCustody){}
  async request(sessionID:string,ref:ExactLink){
    this.validate(sessionID,ref)
    const original=contextJSON({formatVersion:1,sessionID,reference:approvalReference(ref)})
    await this.custody.retain("approval-open",key(sessionID,ref),original)
    const previous=await this.custody.read("approval-current",sessionID)
    if(previous===undefined)await this.custody.retain("approval-current",sessionID,original)
    else if(previous!==original)await this.custody.replace("approval-current",sessionID,createHash("sha256").update(previous).digest("hex"),original)
    if(await this.custody.read("approval-current",sessionID)!==original)nativeUnavailable()
    return {state:await this.shown(sessionID,ref)?"rendered":"requested",reference:approvalReference(ref)}
  }
  /** Explicit presentation selection only. A rendered selection never advances into the backlog. */
  async selected(sessionID:string,projectId:string):Promise<ExactLink|undefined>{
    const raw=await this.custody.read("approval-current",sessionID)
    if(raw===undefined)return undefined
    const selected=this.decode(raw,sessionID,projectId)
    if(await this.custody.read("approval-open",key(sessionID,selected))!==raw)nativeUnavailable()
    return selected
  }
  /** Recovery queue, including retained intents that were never explicitly selected. */
  async current(sessionID:string,projectId:string):Promise<ExactLink|undefined>{
    const selected=await this.selected(sessionID,projectId)
    if(selected&&!await this.shown(sessionID,selected))return selected
    // Do not lose an earlier unrendered request when another contract is prepared, or after a cut
    // between immutable intent and current-pointer publication. The inventory is bounded custody,
    // not model-supplied history; another chat's records can never become this chat's delivery.
    const pending=await this.custody.scan("approval-open")
    for(const entry of [...pending].sort((a,b)=>a.keyDigest.localeCompare(b.keyDigest))){
      const value=closedObject(exactJSON(entry.raw,nativeUnavailable),["formatVersion","sessionID","reference"])
      if(value.sessionID!==sessionID)continue
      const ref=this.decode(entry.raw,sessionID,projectId)
      if(createHash("sha256").update(key(sessionID,ref)).digest("hex")!==entry.keyDigest)nativeUnavailable()
      if(!await this.shown(sessionID,ref))return ref
    }
    return selected
  }
  async acknowledge(sessionID:string,ref:ExactLink){
    this.validate(sessionID,ref)
    // A second request may arrive while the first exact panel is loading. Acknowledge the actual
    // immutable delivered request, never remap that acknowledgement onto the newer pointer.
    const original=contextJSON({formatVersion:1,sessionID,reference:approvalReference(ref)})
    if(await this.custody.read("approval-open",key(sessionID,ref))!==original)nativeUnavailable()
    await this.custody.retain("approval-shown",key(sessionID,ref),contextJSON({formatVersion:1,sessionID,reference:approvalReference(ref)}))
  }
  async shown(sessionID:string,ref:ExactLink){
    this.validate(sessionID,ref)
    const raw=await this.custody.read("approval-shown",key(sessionID,ref))
    if(raw===undefined)return false
    if(raw!==contextJSON({formatVersion:1,sessionID,reference:approvalReference(ref)}))nativeUnavailable()
    return true
  }
  private validate(sessionID:string,ref:ExactLink){
    if(!/^ses_[A-Za-z0-9_-]{1,240}$/.test(sessionID)||!exactLink(approvalReference(ref)))nativeUnavailable()
  }
  private decode(raw:string,sessionID:string,projectId:string):ExactLink{
    const value=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","sessionID","reference"])
    const ref=exactLink(value.reference)
    if(value.formatVersion?.value!=="1"||value.sessionID!==sessionID||!ref||ref.project!==projectId)nativeUnavailable()
    this.validate(sessionID,ref)
    return ref
  }
}
