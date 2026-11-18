// SPDX-License-Identifier: AGPL-3.0-only
// Mirrors the PUBLIC Content/StructureIndex/DocumentRevision digest framing, not authority policy.
import { createHash } from "node:crypto"
import { isLosslessNumber } from "lossless-json"
import { contextJSON } from "./native-context-codec.js"
type Check=(condition:unknown,code?:string)=>void
const hash=(value:Uint8Array|string)=>createHash("sha256").update(value).digest("hex")
const int=(n:number)=>{const b=Buffer.alloc(4);b.writeInt32BE(n);return b}
const frame=(bytes:Uint8Array)=>Buffer.concat([int(bytes.length),bytes])
const framed=(domain:string,parts:Uint8Array[])=>hash(Buffer.concat([frame(Buffer.from(domain)),...parts]))
const equal=(a:unknown,b:unknown)=>contextJSON(a,4*1024*1024)===contextJSON(b,4*1024*1024)
const digest=(v:unknown)=>typeof v==="string"&&/^[0-9a-f]{64}$/.test(v)
const uuid=(v:unknown)=>typeof v==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v)
function span(value:any,check:Check):[number,number]{
  check(isLosslessNumber(value?.start)&&isLosslessNumber(value?.endExclusive))
  const start=Number(value.start.value),end=Number(value.endExclusive.value)
  check(Number.isInteger(start)&&Number.isInteger(end)&&start>=0&&end>start&&end<=2147483647)
  return [start,end]
}
export function knowledgeContextDigest(texts:string[]){
  return framed("writhelm.document-context.v1",[int(texts.length),...texts.map(text=>frame(Buffer.from(text)))])
}
export function revisionSources(data:any){
  const bytes=Buffer.from(data.body)
  return [...data.structure.clauses.map((clause:any)=>({label:"Clause "+clause.id,reference:{kind:"clause",
    revision:data.revisionKey,revisionDigest:data.revisionDigest,clauseId:clause.id,
    contentDigest:hash(bytes.subarray(Number(clause.span.start.value),Number(clause.span.endExclusive.value)))}})),
    {label:"Context",reference:{kind:"context",revision:data.revisionKey,revisionDigest:data.revisionDigest,
      contextDigest:knowledgeContextDigest(data.structure.context.map((s:any)=>
        bytes.subarray(Number(s.start.value),Number(s.endExclusive.value)).toString("utf8")))}}]
}
export function validateKnowledgeRead(request:any,data:any,check:Check){
  const op=request.operation,project=request.projectId
  const revisionKey=(key:any)=>check(uuid(key?.document?.projectId)&&key.document.projectId===project&&
    uuid(key.document.documentId)&&uuid(key.revisionId))
  const material=(value:any)=>{
    check(value&&value.reference&&Array.isArray(value.fragments)&&
      typeof value.observation==="string"&&value.observation.length>0&&Object.hasOwn(value,"trace"),"INCOMPLETE_READ")
    check(value.fragments.length<=4096,"READ_PROJECTION_TOO_LARGE")
    const ref=value.reference
    revisionKey(ref.revision);check(digest(ref.revisionDigest)&&["clause","context"].includes(ref.kind))
    let end=0
    const texts=value.fragments.map((fragment:any)=>{
      check(typeof fragment.text==="string")
      const [start,next]=span(fragment.span,check)
      check(start>=end&&Buffer.byteLength(fragment.text)===next-start,"INCOMPLETE_READ");end=next
      return fragment.text as string
    })
    if(ref.kind==="clause")check(typeof ref.clauseId==="string"&&texts.length===1&&hash(texts[0]!)===ref.contentDigest,"INCOMPLETE_READ")
    else check(knowledgeContextDigest(texts)===ref.contextDigest,"INCOMPLETE_READ")
  }
  if(op==="read_revision"){
    revisionKey(data.revisionKey)
    check(equal(data.revisionKey,request.subject.revisionKey)&&data.revisionDigest===request.subject.revisionDigest&&
      digest(data.bodyDigest)&&typeof data.body==="string"&&hash(data.body)===data.bodyDigest&&uuid(data.proposedBy))
    const structure=data.structure,bytes=Buffer.from(data.body)
    check(Array.isArray(structure?.clauses)&&Array.isArray(structure?.context),"INCOMPLETE_READ")
    check(structure.clauses.length<=4096&&structure.context.length<=4096,"READ_PROJECTION_TOO_LARGE")
    const clauses:Array<{id:string;range:[number,number]}>=structure.clauses.map((c:any)=>{
      check(typeof c.id==="string"&&c.id.length>0)
      return {id:c.id,range:span(c.span,check)}
    })
    const contexts:Array<[number,number]>=structure.context.map((s:any)=>span(s,check))
    check(new Set(clauses.map(c=>c.id)).size===clauses.length&&
      clauses.every((c,i)=>i===0||clauses[i-1]!.range[0]<c.range[0])&&
      contexts.every((s,i)=>i===0||contexts[i-1]![0]<s[0]),"INCOMPLETE_READ")
    let end=0
    for(const [start,next]of [...clauses.map(c=>c.range),...contexts].sort((a,b)=>a[0]-b[0])){
      check(start===end&&next<=bytes.length,"INCOMPLETE_READ")
      // Fatal UTF-8 decoding rejects boundaries splitting a codepoint, without normalizing source.
      new TextDecoder("utf-8",{fatal:true,ignoreBOM:true}).decode(bytes.subarray(start,next));end=next
    }
    check(end===bytes.length,"INCOMPLETE_READ")
    const structureDigest=framed("writhelm.structure-index.v1",[int(clauses.length),
      ...clauses.flatMap(c=>[frame(Buffer.from(c.id)),int(c.range[0]),int(c.range[1])]),
      int(contexts.length),...contexts.flatMap(s=>[int(s[0]),int(s[1])])])
    const key=data.revisionKey
    const identities=[key.document.projectId,key.document.documentId,key.revisionId]
      .map((id:string)=>Buffer.from(id.replaceAll("-",""),"hex"))
    check(framed("writhelm.document-revision.v1",[...identities,Buffer.from(data.bodyDigest,"hex"),
      Buffer.from(structureDigest,"hex")])===data.revisionDigest,"STALE_READ_REFERENCE")
  }else if(["read_clause","read_context","read_approval_trace"].includes(op)){
    check(equal(data.reference,request.subject)&&
      (op!=="read_clause"||data.reference?.kind==="clause")&&(op!=="read_context"||data.reference?.kind==="context"))
    material(data)
  }else if(op==="read_review_bundle"){
    check(data.complete===true&&Array.isArray(data.materials)&&Array.isArray(data.blockers)&&
      data.blockers.every((s:any)=>typeof s==="string")&&Object.hasOwn(data,"resolution")&&
      typeof data.actionable==="boolean"&&data.actionable===(data.blockers.length===0),"INCOMPLETE_READ")
    const r=data.request,p=r?.payload?.packet
    check(r?.family==="requests"&&r.formatVersion?.value==="1"&&r.key===request.subject.id&&r.payload.projectId===project&&
      r.payload.requestDigest===request.subject.requestDigest&&r.payload.packetDigest===request.subject.packetDigest)
    if(data.resolution!==null)check(data.resolution?.family==="resolutions"&&data.resolution.key===request.subject.id&&
      data.resolution.formatVersion?.value==="1")
    check(p&&Array.isArray(p.items)&&Array.isArray(p.contexts)&&Array.isArray(p.dependencies)&&
      Array.isArray(p.informationGaps)&&p.informationGaps.length===0,"INCOMPLETE_READ")
    check(["question","scope","rationale"].every(name=>typeof p[name]==="string")&&
      ["consequences","alternatives","uncertainties"].every(name=>Array.isArray(p[name])&&p[name].every((s:any)=>typeof s==="string"))&&
      p.items.length>0,"INCOMPLETE_READ")
    const required=[...p.items.flatMap((item:any)=>[item.target,...(item.before?[item.before]:[])]),...p.contexts,...p.dependencies]
    check(required.every(ref=>data.materials.some((m:any)=>equal(ref,m.reference))),"INCOMPLETE_READ")
    data.materials.forEach(material)
  }else{
    const family={read_request:"requests",read_resolution:"resolutions",read_manifest:"manifests"}[op as string]
    check(family&&data.record?.family===family&&data.record.key===request.subject.id&&data.record.formatVersion?.value==="1")
    if(family!=="resolutions")check(data.record.payload?.projectId===project)
  }
}
