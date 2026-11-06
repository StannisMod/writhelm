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
