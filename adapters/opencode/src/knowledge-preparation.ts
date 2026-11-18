// SPDX-License-Identifier: AGPL-3.0-only
import {createHash} from "node:crypto"
import {nativeUnavailable} from "./native-runtime-client.js"
export type AuthoredClause={id:string;title:string;text:string}
const hash=(bytes:Uint8Array|string)=>createHash("sha256").update(bytes).digest()
const int=(n:number)=>{const b=Buffer.alloc(4);b.writeInt32BE(n);return b}
const frame=(b:Uint8Array)=>Buffer.concat([int(b.length),b])
const framed=(domain:string,parts:Uint8Array[])=>hash(Buffer.concat([frame(Buffer.from(domain)),...parts]))
/** Not a model/parser-generated structure: caller explicitly authors each clause identity/title/text.
 * Document bytes are exactly optional context + LF LF, then ID — title LF text LF LF per clause.
 * Never trim, normalize, renumber, split prose or silently retire a historical clause. */
export function prepareKnowledgeDocument(projectId:string,documentId:string,revisionId:string,clauses:readonly AuthoredClause[],context=""){
  const valid=(s:unknown):s is string=>typeof s==="string"&&s.isWellFormed()
  if(!valid(context)||!clauses.length||clauses.length>256||new Set(clauses.map(c=>c.id)).size!==clauses.length)nativeUnavailable()
  let body="",offset=0
  const structure={clauses:[] as {id:string;span:{start:number;endExclusive:number}}[],context:[] as {start:number;endExclusive:number}[]}
  if(context!==""){body=context+"\n\n";offset=Buffer.byteLength(body);structure.context.push({start:0,endExclusive:offset})}
  for(const c of clauses){
    if(!valid(c.id)||!valid(c.title)||!valid(c.text)||!c.id.trim()||c.id!==c.id.trim()||
      /[\p{Cc}\p{Zl}\p{Zp}]/u.test(c.id)||!c.title.trim()||/[\r\n]/u.test(c.title)||!c.text.trim())nativeUnavailable()
    const section=c.id+" — "+c.title+"\n"+c.text+"\n\n",end=offset+Buffer.byteLength(section)
    structure.clauses.push({id:c.id,span:{start:offset,endExclusive:end}});body+=section;offset=end
  }
  if(offset>1024*1024)nativeUnavailable()
  const revisionKey={document:{projectId,documentId},revisionId}
  const structureDigest=framed("writhelm.structure-index.v1",[int(structure.clauses.length),
    ...structure.clauses.flatMap(c=>[frame(Buffer.from(c.id)),int(c.span.start),int(c.span.endExclusive)]),
    int(structure.context.length),...structure.context.flatMap(s=>[int(s.start),int(s.endExclusive)])])
  const revisionDigest=framed("writhelm.document-revision.v1",[
    ...[projectId,documentId,revisionId].map(id=>Buffer.from(id.replaceAll("-",""),"hex")),hash(body),structureDigest]).toString("hex")
  return {revisionKey,revisionDigest,body,structure}
}
/** A logical intent is scoped to the protected exact chat binding. Reusing its name with different
 * bytes reaches existing original-command conflict handling; a new native call is not a new effect. */
export function knowledgeIntentIdentity(binding:string,intent:string,purpose:string){
  if(typeof binding!=="string"||!binding||typeof intent!=="string"||!intent.isWellFormed()||!intent.trim()||Buffer.byteLength(intent)>128)nativeUnavailable()
  const bytes=hash(JSON.stringify(["writhelm.model-intent.v1",binding,intent,purpose])).subarray(0,16)
  bytes[6]=(bytes[6]!&15)|80;bytes[8]=(bytes[8]!&63)|128
  const h=bytes.toString("hex")
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`
}
