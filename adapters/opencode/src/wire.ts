// SPDX-License-Identifier: AGPL-3.0-only
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import service from "./generated/service.cjs"
import profile from "./generated/profile.cjs"
import { Fault, requireThat } from "./contracts.js"
// Raw JSON is retained; `any` here describes JSON guarded by the actual frozen schemas, not SDK APIs.
export type Json = any
export function exact(text: string): Json {
  return exactJSON(text, () => { throw new Fault("INVALID_INPUT") })
}
export function serviceResult(raw: string): Json {
  const value = exact(raw)
  requireThat(service(JSON.parse(raw)), "INTEGRITY")
  return value
}
export function profileResult(raw: string): Json {
  exact(raw); const value = JSON.parse(raw)
  requireThat(profile(value), "PROTECTION_FAILED"); return value
}
export const versionOne = (v: Json) => isLosslessNumber(v) && v.value === "1"
export function referenceKey(ref: Json): string {
  const sort = (v: Json): Json => Array.isArray(v) ? v.map(sort) : v && typeof v === "object" ? Object.fromEntries(Object.keys(v).sort().map(k => [k, sort(v[k])])) : v
  return JSON.stringify(sort(ref))
}
export function completeBundle(raw: string, id: string, requestDigest: string, packetDigest: string, project: string): Json {
  const result = serviceResult(raw)
  requireThat(result.kind === "read" && result.operation === "read_review_bundle", "INCOMPLETE")
  const b = result.data, r = b.request
  requireThat(b.complete === true && r.family === "requests" && r.key === id && r.payload.projectId === project && r.payload.requestDigest === requestDigest && r.payload.packetDigest === packetDigest, "INCOMPLETE")
  const p = r.payload.packet
  requireThat(p.informationGaps.length === 0, "INCOMPLETE")
  const declared = [...p.items.flatMap((i: Json) => [i.before, i.target].filter(Boolean)), ...p.contexts, ...p.dependencies]
  for (const ref of declared) requireThat(b.materials.some((m: Json) => referenceKey(m.reference) === referenceKey(ref)), "INCOMPLETE")
  if(p.followup){
    requireThat(typeof b.followupJSON==="string","INCOMPLETE")
    const previous=exact(b.followupJSON),link=p.followup
    requireThat(Object.keys(previous).sort().join(",")==="link,materials,request,resolution"&&referenceKey(previous.link)===referenceKey(link),"INTEGRITY")
    // Structural validation of service-derived records, NOT a fabricated source/approval.
    const plain=JSON.parse(b.followupJSON)
    const envelope={formatVersion:1,kind:"read",basis:JSON.parse(raw).basis,operation:"read_review_bundle",
      data:{request:plain.request,resolution:plain.resolution,materials:plain.materials,
        complete:true,actionable:false,blockers:["ALREADY_RESOLVED"]}}
    requireThat(service(envelope),"INTEGRITY")
    const old=previous.request
    requireThat(old.key===link.requestId&&old.payload.projectId===project&&old.payload.requestDigest===link.requestDigest&&
      old.payload.packetDigest===link.packetDigest&&previous.resolution?.key===old.key,"INTEGRITY")
    const oldItems=old.payload.packet.items,newItems=p.items
    const oldIds=link.items.map((c:Json)=>c.previousItemId).filter(Boolean),newIds=link.items.map((c:Json)=>c.itemId).filter(Boolean)
    const ids=(items:Json[])=>items.map(i=>i.id).sort().join(",")
    requireThat(new Set(oldIds).size===oldIds.length&&new Set(newIds).size===newIds.length&&
      [...oldIds].sort().join(",")===ids(oldItems)&&[...newIds].sort().join(",")===ids(newItems),"INTEGRITY")
    const oldPacket=old.payload.packet
    const oldRefs=[...oldItems.flatMap((i:Json)=>[i.before,i.target].filter(Boolean)),...oldPacket.contexts,...oldPacket.dependencies]
    for(const ref of oldRefs)requireThat(previous.materials.some((m:Json)=>referenceKey(m.reference)===referenceKey(ref)),"INCOMPLETE")
    const subject=(r:Json)=>referenceKey({kind:r.kind,document:r.revision.document,clauseId:r.clauseId??null})
    for(const change of link.items){
      const before=oldItems.find((i:Json)=>i.id===change.previousItemId)?.target
      const target=newItems.find((i:Json)=>i.id===change.itemId)?.target
      requireThat(change.disposition==="revised"?before&&target&&subject(before)===subject(target)&&referenceKey(before)!==referenceKey(target):
        change.disposition==="unchanged"?before&&target&&referenceKey(before)===referenceKey(target):
        change.disposition==="added"?!before&&!!target:
        change.disposition==="removed"?!!before&&!target:
        change.disposition==="retained"?before&&!target&&p.dependencies.some((r:Json)=>referenceKey(r)===referenceKey(before)):false,"INTEGRITY")
    }
    const answer=previous.resolution.payload.answer
    if(answer.kind==="changes-required")requireThat(answer.comments.every((c:Json)=>oldItems.some((i:Json)=>i.id===c.itemId)),"INTEGRITY")
    b.followup=previous
  }else requireThat(b.followupJSON===undefined,"INTEGRITY")
  return b
}
export function loopback(origin: string): string {
  return exactLoopback(origin, () => { throw new Fault("INVALID_INPUT") })
}
export async function http(origin: string, token: string, route: "reads" | "commands", body: string, fetcher: typeof fetch = fetch): Promise<{ raw: string, value: Json, status: number }> {
  loopback(origin)
  const response = await fetcher(origin + "/api/v1/" + route, { method: "POST", redirect: "error", credentials: "omit", headers: { "Content-Type": "application/json", Authorization: "Bearer " + token }, body, signal: AbortSignal.timeout(10000) })
  requireThat(response.body, "UNAVAILABLE")
  const chunks: Uint8Array[] = []; let size = 0
  const reader = response.body.getReader()
  try { while (true) { const next = await reader.read(); if (next.done) break; size += next.value.byteLength; requireThat(size <= 4 * 1024 * 1024, "UNAVAILABLE"); chunks.push(next.value) } }
  finally { reader.releaseLock() }
  const raw = new TextDecoder("utf-8", { fatal: true }).decode(Buffer.concat(chunks))
  return { raw, value: serviceResult(raw), status: response.status }
}
