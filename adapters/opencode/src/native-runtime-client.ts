// SPDX-License-Identifier: AGPL-3.0-only
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import { setTimeout as wait } from "node:timers/promises"
import { contextJSON } from "./native-context-codec.js"
import { createHash } from "node:crypto"

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
    const receipt=value.operation==="original-result-receipt"
    if (Buffer.byteLength(body) > (receipt ? 4*1024*1024+16384 : 16384)) nativeUnavailable()
    const deadline = signal ? AbortSignal.any([signal, AbortSignal.timeout(10000)]) : AbortSignal.timeout(10000)
    const send = () => this.#fetcher(this.#origin + (receipt?"/internal/native/receipt/v1":"/internal/native/v1"), { method: "POST", redirect: "error", credentials: "omit",
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
  async identity(token: string, signal?: AbortSignal, sourceRead = false): Promise<NativeIdentity> {
    const value = closedObject(await this.request(token, { operation: sourceRead ? "source-identity" : "identity" }, signal),
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
  async reservationRetryEligibility(token:string,reservationJSON:string,signal?:AbortSignal):Promise<"definitive-refusal"|"hold"> {
    if(typeof reservationJSON!=="string"||Buffer.byteLength(reservationJSON)>8192)nativeUnavailable()
    const value=closedObject(await this.request(token,{operation:"reservation-retry",reservationJSON},signal),["formatVersion","kind"])
    if(value.kind!=="definitive-refusal"&&value.kind!=="hold")nativeUnavailable()
    return value.kind
  }
  /** Missing is only a current-head observation, never permission for a new effect or retry. */
  async originalResultReceipt(token:string,commandJSON:string,signal?:AbortSignal):Promise<"match"|"missing"|"hold"> {
    if(typeof commandJSON!=="string"||Buffer.byteLength(commandJSON)>2*1024*1024)nativeUnavailable()
    const value=closedObject(await this.request(token,{operation:"original-result-receipt",commandJSON},signal),["formatVersion","kind"])
    if(value.kind!=="match"&&value.kind!=="missing"&&value.kind!=="hold")nativeUnavailable()
    return value.kind
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
  async workerContext(token: string, signal?: AbortSignal) {
    return closedObject(await this.request(token, { operation: "worker-context" }, signal, 4 * 1024 * 1024),
      ["formatVersion", "basis", "nativeReady", "finishedHandoff", "assignmentId", "executionGeneration", "recordGeneration", "bindingJSON", "taskJSON", "manifestJSON",
        "profileJSON", "profileRecordJSON", "planJSON", "loadedViewJSON", "memoryJSON", "checkpointJSON", "checkpointReferenceJSON",
        "checkpointMemoryJSON", "checkpointSourceJSON", "safeContextAccess", "reusableBodies", "obligationIds", "packet"])
  }
  async retentionPermit(token:string,request:{assignmentId:string;generation:string;executionId:string;snapshot:unknown},signal?:AbortSignal):Promise<boolean>{
    const value=closedObject(await this.request(token,{operation:"retention-permit",assignmentId:request.assignmentId,
      executionGeneration:request.generation,executionId:request.executionId,snapshotJSON:contextJSON(request.snapshot)},signal),["formatVersion","kind","basis"])
    return value.kind==="retention-permitted"
  }
  /** Exact current-H read only; readiness and claim acquisition remain service transitions. */
  async dispatchContext(token:string,planId:string,signal?:AbortSignal) {
    if(!uuidPattern.test(planId))nativeUnavailable()
    const value=closedObject(await this.request(token,{operation:"dispatch-context",planId},signal,4*1024*1024),
      ["formatVersion","basis","planJSON","entries","activeAssignments","attemptsJSON"])
    if(typeof value.planJSON!=="string"||!Array.isArray(value.entries)||value.entries.length<1||value.entries.length>256||
      !Array.isArray(value.activeAssignments)||value.activeAssignments.length>256||value.activeAssignments.some((v:unknown)=>typeof v!=="string")||
      !Array.isArray(value.attemptsJSON)||value.attemptsJSON.length>1024||value.attemptsJSON.some((v:unknown)=>typeof v!=="string"))nativeUnavailable()
    const plan=exactJSON(value.planJSON,nativeUnavailable)
    if(plan.family!=="dispatch-plans"||plan.key?.projectId!==this.projectId||plan.key?.id!==planId||
      plan.payload?.projectId!==this.projectId||plan.payload?.planId!==planId||plan.payload?.controllerId!==this.controllerId||
      !Array.isArray(plan.payload.entries)||plan.payload.entries.length!==value.entries.length)nativeUnavailable()
    const seen=new Set<string>()
    for(const raw of value.entries){
      const entry=closedObject(raw,["preparationId","startId","taskJSON","selectionJSON","futureReady","claimsAvailable"])
      if(!uuidPattern.test(entry.preparationId)||!uuidPattern.test(entry.startId)||seen.has(entry.preparationId)||seen.has(entry.startId)||
        entry.preparationId===entry.startId||typeof entry.taskJSON!=="string"||typeof entry.selectionJSON!=="string"||typeof entry.futureReady!=="boolean"||
        typeof entry.claimsAvailable!=="boolean")nativeUnavailable()
      seen.add(entry.preparationId);seen.add(entry.startId)
      const matches=plan.payload.entries.filter((p:any)=>p.preparationId===entry.preparationId&&p.startId===entry.startId)
      if(matches.length!==1)nativeUnavailable()
      const declared=matches[0],task=exactJSON(entry.taskJSON,nativeUnavailable),selection=exactJSON(entry.selectionJSON,nativeUnavailable)
      if(task.family!=="task-revisions"||contextJSON(task.key)!==contextJSON(declared.task)||
        contextJSON(selection.task)!==contextJSON(declared.task)||contextJSON(selection.manifest)!==contextJSON(declared.manifest)||
        contextJSON(selection.profile)!==contextJSON(declared.profile)||!Array.isArray(selection.resources)||!Array.isArray(declared.resources)||
        contextJSON(selection.resources.map((r:any)=>r.resource))!==contextJSON(declared.resources)||
        !Array.isArray(selection.reviewSubjects)||selection.reviewSubjects.length!==0)nativeUnavailable()
    }
    const attempts=new Set<string>()
    const planDigest=createHash("sha256").update(value.planJSON,"utf8").digest("hex")
    for(const raw of value.attemptsJSON){
      const row=exactJSON(raw,nativeUnavailable),a=row.payload
      if(row.family!=="dispatch-attempts"||row.key?.projectId!==this.projectId||!uuidPattern.test(row.key?.id)||
        attempts.has(row.key.id)||a?.preparationId!==row.key.id||a?.plan?.family!=="dispatch-plans"||
        a.plan.key?.projectId!==this.projectId||a.plan.key?.id!==planId||a.plan.digest!==planDigest)nativeUnavailable()
      attempts.add(row.key.id)
    }
    return value
  }
  async controllerContext(token: string, profileJSON: string, signal?: AbortSignal) {
    const frame = closedObject(await this.request(token, { operation: "controller-context", profileJSON }, signal, 4 * 1024 * 1024),
      ["formatVersion", "basis", "controllerId", "nativeReady", "profileJSON", "profileRecordJSON", "frontierJSON", "plansJSON",
        "previousJSON", "previousReferenceJSON", "reusableBodies", "obligationIds", "packet"])
    if (frame.controllerId !== this.controllerId || frame.profileJSON !== profileJSON || typeof frame.nativeReady !== "boolean") nativeUnavailable()
    return frame
  }
  async connectRuntime(token: string, signal?: AbortSignal) {
    const value=closedObject(await this.request(token,{operation:"connect"},signal),["formatVersion","kind"])
    if(!["runtime-connected","runtime-held"].includes(value.kind))nativeUnavailable()
    return value.kind==="runtime-connected"
  }
  async preparationContext(token:string,assignmentId:string,executionGeneration:string,signal?:AbortSignal){
    if(!uuidPattern.test(assignmentId)||!/^(0|[1-9][0-9]{0,18})$/.test(executionGeneration)||BigInt(executionGeneration)>9223372036854775807n)nativeUnavailable()
    return closedObject(await this.request(token,{operation:"preparation-context",assignmentId,executionGeneration},signal,4*1024*1024),
      ["formatVersion","basis","assignmentJSON","intentJSON","taskJSON","profileJSON","manifestJSON"])
  }
  async initialContext(token:string,subject:NativeSubject,intentId:string,signal?:AbortSignal){
    if(!uuidPattern.test(intentId)||!uuidPattern.test(subject.assignmentId)||!/^(0|[1-9][0-9]{0,18})$/.test(subject.executionGeneration)||
      BigInt(subject.executionGeneration)>9223372036854775807n)nativeUnavailable()
    const frame=closedObject(await this.request(token,{operation:"initial-context",...subject,intentId},signal,4*1024*1024),
      ["formatVersion","basis","envelopeJSON","envelopeDigest","packetJSON","profileJSON","assignmentJSON","intentJSON","dispatch","active"])
    if(typeof frame.dispatch!=="boolean"||typeof frame.active!=="boolean"||!/^[0-9a-f]{64}$/.test(frame.envelopeDigest))nativeUnavailable()
    return frame
  }
}
