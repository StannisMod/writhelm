// SPDX-License-Identifier: AGPL-3.0-only
import {createHash} from "node:crypto"
import {contextJSON} from "./native-context-codec.js"
import {exactJSON} from "./strict-json.js"
import {nativeUnavailable,closedObject,uuidPattern} from "./native-runtime-client.js"
import {reviewLanguagePattern,type ReviewDisplayScope} from "./review-display.js"
import type {LinuxNativeCustody} from "./native-runtime-custody.js"
type Storage=Pick<LinuxNativeCustody,"read"|"retain"|"replace">
const hash=(text:string)=>createHash("sha256").update(text).digest("hex")
const encode=(value:unknown)=>contextJSON(value)
function scope(value:ReviewDisplayScope){
  if(!value||Object.keys(value).sort().join(",")!=="packetDigest,projectId,requestDigest,requestId,sessionID,storeId"||
    ![value.storeId,value.projectId,value.requestId].every(x=>typeof x==="string"&&uuidPattern.test(x))||
    !/^ses_[A-Za-z0-9_-]{1,240}$/.test(value.sessionID)||
    ![value.requestDigest,value.packetDigest].every(x=>typeof x==="string"&&/^[a-f0-9]{64}$/.test(x)))nativeUnavailable()
  return value
}
export class ReviewDisplayCustody{
  constructor(private readonly storage:Storage){}
  /** Trusted operator declaration only, immutable and exact-scoped. Never an approval or translation. */
  async retainSourceLanguage(binding:ReviewDisplayScope,language:string){
    scope(binding);if(!reviewLanguagePattern.test(language))nativeUnavailable()
    const raw=encode({formatVersion:1,scope:binding,language})
    await this.storage.retain("source-language",encode(binding),raw)
    if(await this.sourceLanguage(binding)!==raw)nativeUnavailable()
    return raw
  }
  async sourceLanguage(binding:ReviewDisplayScope):Promise<string|undefined>{
    scope(binding)
    const raw=await this.storage.read("source-language",encode(binding))
    if(raw===undefined)return undefined
    const value=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","scope","language"])
    if(value.formatVersion?.value!=="1"||encode(value.scope)!==encode(binding)||
      typeof value.language!=="string"||!reviewLanguagePattern.test(value.language))nativeUnavailable()
    return raw
  }
  async rememberInterface(storeId:string,projectId:string,sessionID:string,language:string){
    if(![storeId,projectId].every(x=>uuidPattern.test(x))||!/^ses_[A-Za-z0-9_-]{1,240}$/.test(sessionID)||
      !reviewLanguagePattern.test(language))nativeUnavailable()
    const key=encode({storeId,projectId,sessionID}),next=encode({formatVersion:1,storeId,projectId,sessionID,language})
    const previous=await this.storage.read("interface-language",key)
    if(previous===undefined)await this.storage.retain("interface-language",key,next)
    else if(previous!==next&&!await this.storage.replace("interface-language",key,hash(previous),next))nativeUnavailable()
  }
  async interfaceLanguage(storeId:string,projectId:string,sessionID:string){
    const raw=await this.storage.read("interface-language",encode({storeId,projectId,sessionID}))
    if(raw===undefined)return undefined
    const v=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","storeId","projectId","sessionID","language"])
    if(v.formatVersion?.value!=="1"||v.storeId!==storeId||v.projectId!==projectId||v.sessionID!==sessionID||
      typeof v.language!=="string"||!reviewLanguagePattern.test(v.language))nativeUnavailable()
    return v.language
  }
}
