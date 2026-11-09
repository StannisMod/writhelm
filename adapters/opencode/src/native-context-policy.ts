// SPDX-License-Identifier: AGPL-3.0-only
import type { OpenCodeClient,ConfigEntry } from "@opencode/client"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject,nativeUnavailable } from "./native-runtime-client.js"

const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const tokens=(value:any)=>{
  const raw=value?.value
  if(typeof raw!=="string"||!/^[1-9][0-9]*$/.test(raw)||BigInt(raw)>2147483647n)nativeUnavailable()
  return Number(raw)
}

/** Conservative check over normalized pinned Config.Entry DTOs, not another config merger.
 * All explicit layers must agree; defaults, percentages, conflicts and background extraction refuse.
 * No raw config/provider/credential fields leave this function. This produces NO qualification,
 * source-safety, writer-completion, exact-threshold or request-accounting fact. */
export function assertNativeContextPolicy(entries:readonly ConfigEntry[],profileRecordJSON:string,policyJSON:string):void {
  const profile=parse(profileRecordJSON),p=profile.payload
  if(profile.family!=="profiles"||!p||p.platform!=="linux"||typeof p.compactionPolicyId!=="string"||!p.compactionPolicyId||typeof p.nativeProfileId!=="string"||!p.nativeProfileId)nativeUnavailable()
  const policy=closedObject(parse(policyJSON),["formatVersion","profileKeyJSON","policyId","nativeProfileId","auto","background","keepTokens","bufferTokens"])
  if(policy.formatVersion?.value!=="1"||policy.profileKeyJSON!==contextJSON(profile.key)||policy.policyId!==p.compactionPolicyId||
    policy.nativeProfileId!==p.nativeProfileId||policy.auto!==true||policy.background!==false)nativeUnavailable()
  const keep=tokens(policy.keepTokens),buffer=tokens(policy.bufferTokens)
  const context=tokens(p.contextTokens)
  if(BigInt(keep)+BigInt(buffer)>=BigInt(context))nativeUnavailable()
  if(!Array.isArray(entries)||!entries.length||entries.length>256)nativeUnavailable()
  let auto=false,foreground=false,kept=false,buffered=false
  for(const entry of entries){
    if(entry.type==="directory")continue
    if(entry.type!=="document"||!entry.info||typeof entry.info!=="object")nativeUnavailable()
    const info=entry.info
    if(info.experimental?.background_compaction!==undefined){
      if(info.experimental.background_compaction!==false)nativeUnavailable()
      foreground=true
    }
    const compact=info.compaction
    if(compact===undefined)continue
    if(!compact||typeof compact!=="object"||Array.isArray(compact))nativeUnavailable()
    if(compact.auto!==undefined){if(compact.auto!==true)nativeUnavailable();auto=true}
    if(compact.keep?.tokens!==undefined){if(!Number.isSafeInteger(compact.keep.tokens)||compact.keep.tokens!==keep)nativeUnavailable();kept=true}
    if(compact.buffer!==undefined){if(typeof compact.buffer!=="number"||!Number.isSafeInteger(compact.buffer)||compact.buffer!==buffer)nativeUnavailable();buffered=true}
  }
  if(!auto||!foreground||!kept||!buffered)throw new Error("explicit qualified foreground compaction policy unavailable")
}

/** Explicit authenticated native client only; config.get never starts a host. The registry/current
 * authority remains the caller's independent gate. Reading config is not permission to modify it. */
export class NativeContextPolicy {
  constructor(private readonly native:Pick<OpenCodeClient,"session"|"config">,private readonly deployment:LinuxNativeCustody){}
  async check(sessionID:string,workRoot:string,profileRecordJSON:string):Promise<void>{
    if(process.platform!=="linux")nativeUnavailable()
    const profile=parse(profileRecordJSON),key=contextJSON(profile.key)
    const policy=await this.deployment.read("native-context-policy",key)
    if(policy===undefined)throw new Error("qualified native context policy is not configured")
    const before=await this.native.session.get({sessionID})
    if(before.id!==sessionID||before.revert||before.location.directory!==workRoot)nativeUnavailable()
    const entries=await this.native.config.get({location:{directory:workRoot}})
    assertNativeContextPolicy(entries,profileRecordJSON,policy)
    const after=await this.native.session.get({sessionID})
    if(after.id!==sessionID||after.revert||after.location.directory!==workRoot)nativeUnavailable()
  }
}
