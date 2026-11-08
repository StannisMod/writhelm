// SPDX-License-Identifier: AGPL-3.0-only
import { exactLoopback } from "./strict-json.js"
import { Fault, requireThat } from "./contracts.js"
import { contextJSON } from "./native-context-codec.js"
import { workResult } from "./work-wire.js"
export { mutableWorkFamilies, workResult } from "./work-wire.js"

const fail = (): never => { throw new Fault("INTEGRITY") }
/** Human main-process READ-only transport. The protected selected client owns the token; no
 * renderer/model argument chooses token, origin, project, operation or a command route. */
export async function workRead(origin: string, token: string, projectId: string, operation: string, subject: unknown,
  fetcher: typeof fetch, signal: AbortSignal) {
  exactLoopback(origin, fail)
  const body = contextJSON({ formatVersion: 2, projectId, operation, subject },16384)
  const response = await fetcher(origin + "/api/v1/reads", { method: "POST", redirect: "error", credentials: "omit",
    headers: { "Content-Type": "application/json", Authorization: "Bearer " + token }, body,
    signal: AbortSignal.any([signal,AbortSignal.timeout(10000)]) })
  requireThat(response.body, "UNAVAILABLE")
  const chunks: Uint8Array[] = []; let size = 0
  const reader = response.body.getReader()
  try { for (;;) { const next = await reader.read(); if (next.done) break
    size += next.value.byteLength; requireThat(size <= 4*1024*1024,"UNAVAILABLE"); chunks.push(next.value)
  } } finally { reader.releaseLock() }
  const raw = new TextDecoder("utf-8",{ fatal: true }).decode(Buffer.concat(chunks))
  return { raw, value: workResult(raw,operation) }
}
