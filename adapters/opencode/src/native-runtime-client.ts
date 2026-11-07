// SPDX-License-Identifier: AGPL-3.0-only
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import { setTimeout as wait } from "node:timers/promises"

export function nativeUnavailable(): never { throw new Error("native runtime unavailable or forbidden") }
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
export function closedObject(value: unknown, names: readonly string[]): Record<string, any> {
  if (!value || typeof value !== "object" || Array.isArray(value)
    || Object.keys(value).sort().join(",") !== [...names].sort().join(",")) nativeUnavailable()
  return value as Record<string, any>
}
export type NativeIdentity = Readonly<{
  storeId: string; projectId: string; controllerId: string; principalId: string; credentialId: string
  scope: "controller" | "assignment"; delegationId: string | null; assignmentId: string | null; executionGeneration: string | null
}>
export type NativeStatus = Readonly<{
  active: boolean; prepare: boolean; dispatch: boolean; pause: boolean; delivery: boolean; quiescent: boolean
  freshEnvelope: boolean; freshPause: boolean
  bindingJSON: string | null; nativeHostId: string | null; nativeSessionId: string | null
  intentId: string | null; intentOperation: string | null; attemptId: string | null; envelopeDigest: string | null
}>
export type NativeSubject = Readonly<{ assignmentId: string; executionGeneration: string; bindingJSON: string }>

/** Real bounded HTTP private-host projection. The private host proof is not a work permission:
 * the service reauthenticates the actual scoped token at current H for every request. */
export class NativeRuntimeClient {
  readonly #origin: string
  readonly #proof: string
  readonly #fetcher: typeof fetch
  constructor(origin: string, proof: string, readonly storeId: string,
    readonly projectId: string, readonly controllerId: string, readonly hostId: string,
    fetcher: typeof fetch = fetch) {
    this.#origin = origin; this.#proof = proof; this.#fetcher = fetcher
    exactLoopback(origin, nativeUnavailable)
    if (![storeId, projectId, controllerId, hostId].every(s => uuidPattern.test(s))
      || !/^[A-Za-z0-9_-]{43}$/.test(proof) || Buffer.from(proof, "base64url").toString("base64url") !== proof) nativeUnavailable()
  }
  private async request(token: string, value: Record<string, unknown>, signal?: AbortSignal, maximum = 16384) {
    if (!new RegExp(`^wh1\\.${this.storeId}\\.[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}$`).test(token)) nativeUnavailable()
    const body = JSON.stringify({ formatVersion: 1, projectId: this.projectId, ...value })
    if (Buffer.byteLength(body) > 16384) nativeUnavailable()
    const deadline = signal ? AbortSignal.any([signal, AbortSignal.timeout(10000)]) : AbortSignal.timeout(10000)
    const send = () => this.#fetcher(this.#origin + "/internal/native/v1", { method: "POST", redirect: "error", credentials: "omit",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token, "X-Writhelm-Native-Proof": this.#proof },
      body, signal: deadline })
    let response = await send()
    // This endpoint contains only read projections and coalesced scheduler hints. An ingress
    // refusal may be recaptured at fresh H; never retry synthetic/interrupt/work commands here.
    for (let retry = 0; response.status === 429 && retry < 2; retry++) {
      await response.body?.cancel()
      await wait(1100, undefined, { signal: deadline })
      response = await send()
    }
    if (!response.ok || !response.body) nativeUnavailable()
    const parts: Uint8Array[] = []; let bytes = 0
    const reader = response.body.getReader()
    try {
      while (true) { const next = await reader.read(); if (next.done) break
        bytes += next.value.byteLength
        if (bytes > maximum) { await reader.cancel(); nativeUnavailable() }
        parts.push(next.value)
      }
    } finally { reader.releaseLock() }
    const valueJSON = exactJSON(new TextDecoder("utf-8", { fatal: true }).decode(Buffer.concat(parts)), nativeUnavailable)
    if (!isLosslessNumber(valueJSON.formatVersion) || valueJSON.formatVersion.value !== "1") nativeUnavailable()
    return valueJSON
  }
  async identity(token: string, signal?: AbortSignal): Promise<NativeIdentity> {
    const value = closedObject(await this.request(token, { operation: "identity" }, signal),
      ["formatVersion", "storeId", "projectId", "controllerId", "nativeHostId", "principalId", "credentialId", "scope", "delegationId", "assignmentId", "executionGeneration"])
    if (value.storeId !== this.storeId || value.projectId !== this.projectId || value.controllerId !== this.controllerId
      || value.nativeHostId !== this.hostId || !uuidPattern.test(value.principalId) || !uuidPattern.test(value.credentialId)
      || token.split(".")[2] !== value.credentialId || !["controller", "assignment"].includes(value.scope)) nativeUnavailable()
    if (value.scope === "controller" ? value.delegationId !== null || value.assignmentId !== null || value.executionGeneration !== null
      : !uuidPattern.test(value.delegationId) || !uuidPattern.test(value.assignmentId) || !/^(0|[1-9][0-9]{0,18})$/.test(value.executionGeneration)
        || BigInt(value.executionGeneration) > 9223372036854775807n) nativeUnavailable()
    return Object.freeze({ storeId: value.storeId, projectId: value.projectId, controllerId: value.controllerId,
      principalId: value.principalId, credentialId: value.credentialId, scope: value.scope,
      delegationId: value.delegationId, assignmentId: value.assignmentId, executionGeneration: value.executionGeneration })
  }
  async status(token: string, subject: NativeSubject, continuationId: string | null = null, signal?: AbortSignal): Promise<NativeStatus> {
    if (!uuidPattern.test(subject.assignmentId) || !/^(0|[1-9][0-9]{0,18})$/.test(subject.executionGeneration)
      || BigInt(subject.executionGeneration) > 9223372036854775807n
      || continuationId !== null && !uuidPattern.test(continuationId)) nativeUnavailable()
    const value = closedObject(await this.request(token, { operation: "status", ...subject, continuationId }, signal),
      ["formatVersion", "basis", "active", "prepare", "dispatch", "pause", "delivery", "quiescent", "freshEnvelope", "freshPause", "bindingJSON", "nativeHostId", "nativeSessionId",
        "intentId", "intentOperation", "attemptId", "envelopeDigest"])
    for (const name of ["active", "prepare", "dispatch", "pause", "delivery", "quiescent", "freshEnvelope", "freshPause"]) if (typeof value[name] !== "boolean") nativeUnavailable()
    if (value.quiescent && (value.active || !value.prepare && !value.dispatch)) nativeUnavailable()
    if (value.freshEnvelope && !value.prepare || value.freshPause && value.intentId !== null) nativeUnavailable()
    if (value.bindingJSON === null) {
      if (value.nativeHostId !== null || value.nativeSessionId !== null
        || [value.active, value.prepare, value.dispatch, value.pause, value.delivery, value.quiescent, value.freshEnvelope, value.freshPause].some(Boolean)) nativeUnavailable()
    } else if (value.nativeHostId !== this.hostId || typeof value.bindingJSON !== "string" || !value.bindingJSON.length
      || value.bindingJSON !== subject.bindingJSON || typeof value.nativeSessionId !== "string"
      || !/^ses_[A-Za-z0-9_-]{1,128}$/.test(value.nativeSessionId)) nativeUnavailable()
    if (value.intentOperation !== null && !["start", "resume", "pause"].includes(value.intentOperation)
      || (value.intentId === null) !== (value.intentOperation === null)) nativeUnavailable()
    for (const name of ["intentId", "attemptId"]) if (value[name] !== null && !uuidPattern.test(value[name])) nativeUnavailable()
    if (value.envelopeDigest !== null && !/^[0-9a-f]{64}$/.test(value.envelopeDigest)) nativeUnavailable()
    return Object.freeze(value) as NativeStatus
  }
  async packet(token: string, subject: NativeSubject, continuationId: string) {
    if (!uuidPattern.test(continuationId)) nativeUnavailable()
    const value = closedObject(await this.request(token, { operation: "packet", ...subject, continuationId }, undefined, 4 * 1024 * 1024),
      ["formatVersion", "envelopeDigest", "bindingJSON", "nativeHostId", "nativeSessionId", "freshEnvelope", "instructions"])
    if (value.bindingJSON !== subject.bindingJSON || value.nativeHostId !== this.hostId
      || typeof value.nativeSessionId !== "string" || !/^ses_[A-Za-z0-9_-]{1,128}$/.test(value.nativeSessionId)
      || !/^[0-9a-f]{64}$/.test(value.envelopeDigest) || typeof value.freshEnvelope !== "boolean" || !Array.isArray(value.instructions)
      || value.instructions.length < 1 || value.instructions.length > 256) nativeUnavailable()
    const instructions: Array<Readonly<{ key: string; value: string }>> = []
    let bytes = 0
    for (const item of value.instructions) {
      const entry = closedObject(item, ["key", "value"])
      if (typeof entry.key !== "string" || !/^writhelm-[a-z0-9-]+$/.test(entry.key) || typeof entry.value !== "string"
        || Buffer.byteLength(JSON.stringify(entry.value)) > 256 * 1024) nativeUnavailable()
      bytes += Buffer.byteLength(JSON.stringify(entry.value))
      instructions.push(Object.freeze({ key: entry.key, value: entry.value }))
    }
    if (bytes > 2 * 1024 * 1024 || new Set(instructions.map(i => i.key)).size !== instructions.length) nativeUnavailable()
    return Object.freeze({ envelopeDigest: value.envelopeDigest as string, bindingJSON: value.bindingJSON as string,
      nativeHostId: value.nativeHostId as string, nativeSessionId: value.nativeSessionId as string,
      freshEnvelope: value.freshEnvelope as boolean, instructions: Object.freeze(instructions) })
  }
  async hint(token: string, subject: NativeSubject, signal: AbortSignal) {
    const value = closedObject(await this.request(token, { operation: "hint", ...subject, continuationId: null }, signal), ["formatVersion", "kind"])
    if (value.kind !== "native-hint") nativeUnavailable()
  }
}
