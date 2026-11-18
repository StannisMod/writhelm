// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { Fault, requireThat } from "./contracts.js"
import type { LinuxNativeCustody } from "./native-runtime-custody.js"

export type DraftScope=Readonly<{storeId:string;projectId:string;controllerId:string;sessionID:string;
  requestId:string;requestDigest:string;packetDigest:string}>
export type DraftContent={comments:readonly {readonly itemId:string;readonly text:string}[];reason:string}
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
export function validateDraft(value:DraftContent,items:ReadonlySet<string>){
  requireThat(Array.isArray(value.comments)&&value.comments.length<=16&&
    new Set(value.comments.map(c=>c.itemId)).size===value.comments.length)
  for(const c of value.comments)requireThat(Object.keys(c).sort().join(",")==="itemId,text"&&items.has(c.itemId)&&
    typeof c.text==="string"&&c.text.isWellFormed()&&Buffer.byteLength(c.text)<=4096)
  requireThat(typeof value.reason==="string"&&value.reason.isWellFormed()&&Buffer.byteLength(value.reason)<=4096)
}
/** Optimistic exact-scoped drafts, never an approval or a canonical-source cache. Caller must
 * freshly authorize the exact request before every load/save. A repeated identical write can
 * reconcile a lost reply; different concurrent edits never silently overwrite one another. */
export class ReviewDraftStore {
  constructor(private readonly custody:Pick<LinuxNativeCustody,"read"|"retain"|"replace">){}
  private key(scope:DraftScope){return contextJSON(scope)}
  async load(scope:DraftScope,items:ReadonlySet<string>){
    const raw=await this.custody.read("review-draft",this.key(scope))
    if(raw===undefined)return {digest:"",comments:[],reason:""}
    const v=exactJSON(raw,()=>{throw new Fault("INTEGRITY")})
    requireThat(Object.keys(v).sort().join(",")==="comments,formatVersion,reason,scope"&&
      v.formatVersion?.value==="1"&&contextJSON(v.scope)===contextJSON(scope),"INTEGRITY")
    validateDraft(v,items)
    return {digest:hash(raw),comments:v.comments as DraftContent["comments"],reason:v.reason as string}
  }
  async save(scope:DraftScope,expectedDigest:string,value:DraftContent,items:ReadonlySet<string>){
    validateDraft(value,items)
    const key=this.key(scope),raw=contextJSON({formatVersion:1,scope,comments:value.comments,reason:value.reason})
    const old=await this.custody.read("review-draft",key)
    if(old===raw)return {digest:hash(raw)} // Original-byte reconciliation, not last-writer-wins.
    requireThat((old===undefined?"":hash(old))===expectedDigest,"STALE_VIEW")
    if(old===undefined)await this.custody.retain("review-draft",key,raw)
    else await this.custody.replace("review-draft",key,expectedDigest,raw)
    requireThat(await this.custody.read("review-draft",key)===raw,"STALE_VIEW")
    return {digest:hash(raw)}
  }
}
