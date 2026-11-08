// SPDX-License-Identifier: AGPL-3.0-only
// Renderer-safe work2 decoder: no fs, secret storage, host startup or Node runtime dependency.
import { isLosslessNumber } from "lossless-json"
import { exactJSON } from "./strict-json.js"
import { Fault, requireThat, type WorkFamily } from "./contracts.js"

export const mutableWorkFamilies = new Set<WorkFamily>(["task-heads", "assignments", "plans", "continuations", "integration-intents", "deliveries", "claims"])
export function workResult(raw: string, operation?: string): any {
  const value = exactJSON(raw,() => { throw new Fault("INTEGRITY") })
  if (value.kind === "error") {
    if(value.code==="SNAPSHOT_EXPIRED")throw new Fault("EXPIRED_VIEW")
    if(value.code==="DIGEST_MISMATCH")throw new Fault("INTEGRITY")
    if(value.code==="STALE_GENERATION")throw new Fault("STALE_VIEW")
    const codes = ["FORBIDDEN", "UNAUTHENTICATED", "INTEGRITY", "IDENTITY_CONFLICT", "INVALID_INPUT"]
    throw new Fault(codes.includes(value.code) ? value.code : "UNAVAILABLE")
  }
  requireThat(isLosslessNumber(value.formatVersion) && value.formatVersion.value === "2" && value.kind === "read"
    && (!operation || value.operation === operation), "INTEGRITY")
  requireThat(value.basis && ["sha1", "sha256"].includes(value.basis.algorithm)
    && typeof value.basis.hex === "string" && new RegExp(`^[0-9a-f]{${value.basis.algorithm === "sha1" ? 40 : 64}}$`).test(value.basis.hex), "INTEGRITY")
  return value
}
