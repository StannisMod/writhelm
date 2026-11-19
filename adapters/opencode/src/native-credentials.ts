// SPDX-License-Identifier: AGPL-3.0-only
import { createHash, randomBytes, randomUUID } from "node:crypto"
import * as fs from "node:fs/promises"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { isLosslessNumber } from "lossless-json"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativeRuntimeClient, NativeRuntimeReadHold, closedObject, nativeUnavailable, uuidPattern, type NativeSubject } from "./native-runtime-client.js"
import { boundedWorkChannel, boundedPlanningKnowledgeChannel, boundedDelegationEnrollmentChannel, boundedDispatchAttemptChannel, boundedDispatchMaterializationChannel, LinuxWorkRequestCustody, type BoundWorkChannel, type NativeWorkCredentialResolver } from "./connected-tools.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"

type SecretRecord = Readonly<{ formatVersion: 1; kind: "native-scoped-key-v1"; storeId: string; projectId: string
  principalId: string; credentialId: string; token: string }>
type SessionRecord = Readonly<{ formatVersion: 1; kind: "native-session-v1"; sessionID: string; hostId: string; epoch: string
  workRoot: string; principalId: string; credentialId: string; scope: "controller" | "assignment"
  delegationId: string | null; assignmentId: string | null; executionGeneration: string | null; bindingJSON: string | null }>
export type PublicNativeCredential = Readonly<{ storeId: string; projectId: string; principalId: string; credentialId: string; verifierDigest: string }>
export type NativeCredentialContext = Pick<Plugin.Context, "app"> & Readonly<{
  session: Pick<Plugin.Context["session"], "get">
}>
const parse = (raw: string) => exactJSON(raw, nativeUnavailable)
const version = (v: any) => isLosslessNumber(v) && v.value === "1"
const session = (id: string) => /^ses_[A-Za-z0-9_-]{1,128}$/.test(id)
function verifier(store: string, project: string, principal: string, credential: string, secret: Buffer) {
  const frame = (b: Buffer) => { const n = Buffer.alloc(4); n.writeUInt32BE(b.length); return Buffer.concat([n, b]) }
  const ids = [store, project, principal, credential].map(id => Buffer.from(id.replaceAll("-", ""), "hex"))
  return createHash("sha256").update(Buffer.concat([frame(Buffer.from("writhelm.credential-verifier.v1")), ...ids, frame(secret)])).digest("hex")
}

/** Client-owned scoped secrets and native mapping. This is never installed as a model tool/RPC.
 * Service identity/current policy is rechecked; metadata role labels and cache entries grant nothing. */
export class NativeCredentialRegistry implements NativeWorkCredentialResolver {
  readonly #ctx: NativeCredentialContext
  readonly #custody: LinuxNativeCustody
  readonly #service: NativeRuntimeClient
  readonly #serviceOrigin: string
  readonly #requests: LinuxWorkRequestCustody
  readonly #epoch: string
  readonly #known = new Map<string, SessionRecord>()
  constructor(ctx: NativeCredentialContext, custody: LinuxNativeCustody, service: NativeRuntimeClient,
    serviceOrigin: string, requests: LinuxWorkRequestCustody, epoch: string) {
    if (ctx.app.version !== "2.2.5" || process.platform !== "linux" || !/^[0-9a-f]{64}$/.test(epoch)) nativeUnavailable()
    this.#ctx = ctx; this.#custody = custody; this.#service = service; this.#serviceOrigin = serviceOrigin
    this.#requests = requests; this.#epoch = epoch
  }
  async prepare(principalId: string, credentialId = randomUUID()): Promise<PublicNativeCredential> {
    if (![principalId, credentialId].every(id => uuidPattern.test(id))) nativeUnavailable()
    let old = await this.#custody.read("credential", credentialId)
    if (old === undefined) {
      const secret = randomBytes(32)
      try {
        const value: SecretRecord = { formatVersion: 1, kind: "native-scoped-key-v1", storeId: this.#service.storeId,
          projectId: this.#service.projectId, principalId, credentialId,
          token: `wh1.${this.#service.storeId}.${credentialId}.${secret.toString("base64url")}` }
        old = JSON.stringify(value)
        await this.#custody.retain("credential", credentialId, old)
      } finally { secret.fill(0) }
    }
    const value = this.secret(old, credentialId)
    if (value.principalId !== principalId) nativeUnavailable()
    const secret = Buffer.from(value.token.split(".")[3]!, "base64url")
    try { return Object.freeze({ storeId: value.storeId, projectId: value.projectId, principalId,
      credentialId, verifierDigest: verifier(value.storeId, value.projectId, principalId, credentialId, secret) }) }
    finally { secret.fill(0) }
  }
  /** Existing-key adoption is for trusted local bootstrap. Project/owner credentials are refused
   * by actual service identity before persistence; no imported role label is accepted. */
  async adopt(principalId: string, credentialId: string, token: string): Promise<void> {
    const actual = await this.#service.identity(token)
    if (actual.principalId !== principalId || actual.credentialId !== credentialId) nativeUnavailable()
    const value: SecretRecord = { formatVersion: 1, kind: "native-scoped-key-v1", storeId: actual.storeId,
      projectId: actual.projectId, principalId, credentialId, token }
    await this.#custody.retain("credential", credentialId, JSON.stringify(value))
  }
  private secret(raw: string, id: string): SecretRecord {
    const v = closedObject(parse(raw), ["formatVersion", "kind", "storeId", "projectId", "principalId", "credentialId", "token"])
    if (!version(v.formatVersion) || v.kind !== "native-scoped-key-v1" || v.storeId !== this.#service.storeId
      || v.projectId !== this.#service.projectId || v.credentialId !== id || !uuidPattern.test(v.principalId)
      || typeof v.token !== "string" || !new RegExp(`^wh1\\.${v.storeId}\\.${id}\\.[A-Za-z0-9_-]{43}$`).test(v.token)) nativeUnavailable()
    const bytes = Buffer.from(v.token.split(".")[3], "base64url")
    try { if (bytes.length !== 32 || bytes.toString("base64url") !== v.token.split(".")[3]) nativeUnavailable() }
    finally { bytes.fill(0) }
    return v as SecretRecord
  }
  private async credential(id: string) {
    if (!uuidPattern.test(id)) nativeUnavailable()
    const raw = await this.#custody.read("credential", id)
    if (raw === undefined) nativeUnavailable()
    return this.secret(raw, id)
  }
  private metadata(raw: string, id: string, inventoryPreviousAssignments=false): SessionRecord {
    const v = closedObject(parse(raw), ["formatVersion", "kind", "sessionID", "hostId", "epoch", "workRoot", "principalId", "credentialId",
      "scope", "delegationId", "assignmentId", "executionGeneration", "bindingJSON"])
    if (!version(v.formatVersion) || v.kind !== "native-session-v1" || v.sessionID !== id || !session(id)
      || v.hostId !== this.#service.hostId || typeof v.epoch!=="string" || !/^[0-9a-f]{64}$/.test(v.epoch)
      || v.scope!=="controller" && v.epoch!==this.#epoch && !inventoryPreviousAssignments || !uuidPattern.test(v.principalId)
      || !uuidPattern.test(v.credentialId) || typeof v.workRoot !== "string" || !path.isAbsolute(v.workRoot)
      || !["controller", "assignment"].includes(v.scope)) nativeUnavailable()
    if (v.scope === "controller" ? v.delegationId !== null || v.assignmentId !== null || v.executionGeneration !== null || v.bindingJSON !== null
      : !uuidPattern.test(v.delegationId) || !uuidPattern.test(v.assignmentId) || typeof v.executionGeneration !== "string" || typeof v.bindingJSON !== "string") nativeUnavailable()
    return v as SessionRecord
  }
  /** Called only by trusted passive-session bootstrap after canonical enrollment/binding. SDK get
   * and the actual scoped service are joined; no model-supplied ToolContext/session label suffices. */
  async bind(sessionID: string, credentialId: string, workRoot: string, subject?: NativeSubject): Promise<void> {
    if (!session(sessionID) || !path.isAbsolute(workRoot)) nativeUnavailable()
    const credential = await this.credential(credentialId)
    const identity = await this.#service.identity(credential.token)
    const actual = await this.#ctx.session.get({ sessionID })
    const physical = await fs.realpath(workRoot)
    await this.#custody.requireOutside(physical)
    await this.#requests.requireOutside(physical)
    if (actual.id !== sessionID || actual.revert || actual.location.directory !== physical || physical !== path.resolve(workRoot)) nativeUnavailable()
    if (identity.scope === "assignment") {
      if (!subject || identity.assignmentId !== subject.assignmentId || identity.executionGeneration !== subject.executionGeneration) nativeUnavailable()
      const current = await this.#service.status(credential.token, subject)
      if (current.nativeSessionId !== sessionID || current.bindingJSON !== subject.bindingJSON || current.nativeHostId !== this.#service.hostId) nativeUnavailable()
    } else if (subject !== undefined) nativeUnavailable()
    const value: SessionRecord = { formatVersion: 1, kind: "native-session-v1", sessionID, hostId: this.#service.hostId, epoch: this.#epoch,
      workRoot: physical, principalId: identity.principalId, credentialId, scope: identity.scope,
      delegationId: identity.delegationId, assignmentId: subject?.assignmentId ?? null,
      executionGeneration: subject?.executionGeneration ?? null, bindingJSON: subject?.bindingJSON ?? null }
    await this.#custody.retain("session", sessionID, JSON.stringify(value))
    this.#known.set(sessionID, Object.freeze(value))
  }
  /** Rebuild only requested actual native scope from protected retained bytes, not a unique cache. */
  async restore(sessionID: string): Promise<boolean> {
    if (!session(sessionID)) return false
    const raw = await this.#custody.read("session", sessionID)
    if (raw === undefined) return false
    const value = this.metadata(raw, sessionID)
    await this.#custody.requireOutside(value.workRoot)
    await this.#requests.requireOutside(value.workRoot)
    const actual = await this.#ctx.session.get({ sessionID })
    if (actual.id !== sessionID || actual.revert || actual.location.directory !== value.workRoot
      || await fs.realpath(value.workRoot) !== value.workRoot) nativeUnavailable()
    if(value.scope==="controller"){
      if(actual.parentID||actual.fork)nativeUnavailable()
      // The retained epoch is historical connection evidence, not the lifetime of a controller.
      // Reconnect the same session/key only under current canonical controller authority. Never
      // apply this projection to assignment records or rewrite their execution epoch.
      if(value.epoch!==this.#epoch && !this.#known.has(sessionID)){
        const key=await this.credential(value.credentialId),identity=await this.#service.identity(key.token)
        if(identity.scope!=="controller"||identity.controllerId!==this.#service.controllerId||
          identity.principalId!==value.principalId||identity.credentialId!==value.credentialId||
          identity.projectId!==this.#service.projectId||identity.storeId!==this.#service.storeId||
          identity.delegationId!==null||identity.assignmentId!==null||identity.executionGeneration!==null)nativeUnavailable()
        await this.#custody.retain("controller-connection",`${sessionID}:${this.#epoch}`,JSON.stringify({formatVersion:1,
          sessionID,projectId:identity.projectId,controllerId:identity.controllerId,principalId:value.principalId,
          credentialId:value.credentialId,workRoot:value.workRoot,hostId:value.hostId,epoch:this.#epoch}))
      }
    }
    this.#known.set(sessionID, Object.freeze(value.scope==="controller"?{...value,epoch:this.#epoch}:value))
    return true
  }
  managed(sessionID: string): boolean { return this.#known.get(sessionID)?.scope === "assignment" }
  bound(sessionID: string): boolean { return this.#known.has(sessionID) } // classification only, never authority
  scopeKind(sessionID: string): "assignment" | "controller" | undefined { return this.#known.get(sessionID)?.scope }
  async restoreAll(): Promise<readonly string[]> {
    const assignments: string[] = []
    for (const entry of await this.#custody.scan("session")) {
      const id = parse(entry.raw).sessionID
      if (typeof id !== "string" || createHash("sha256").update(id).digest("hex") !== entry.keyDigest
        || await this.#custody.read("session", id) !== entry.raw) nativeUnavailable()
      const historical=this.metadata(entry.raw,id,true)
      // Inventory is not adoption: old workers remain unbound, retained and denied by restore().
      // Their recovery must not prevent the permanent planner identity from reconnecting.
      if(historical.scope==="assignment"&&historical.epoch!==this.#epoch)continue
      if(!await this.restore(id))nativeUnavailable()
      if (this.managed(id)) assignments.push(id)
      if (assignments.length > 256) nativeUnavailable()
    }
    return Object.freeze(assignments.sort())
  }
  /** Adopt an ALREADY canonical replacement key, never perform/grant a rotation implicitly.
   * Old mapping bytes remain retained; current service identity and native binding must agree. */
  async activateReplacement(sessionID: string, credentialId: string): Promise<void> {
    if (!await this.restore(sessionID)) nativeUnavailable()
    const previousRaw = await this.#custody.read("session", sessionID)
    if (previousRaw === undefined) nativeUnavailable()
    const previous = this.metadata(previousRaw, sessionID)
    const key = await this.credential(credentialId), current = await this.#service.identity(key.token)
    if (current.principalId !== previous.principalId || current.scope !== previous.scope
      || current.delegationId !== previous.delegationId || current.assignmentId !== previous.assignmentId || current.executionGeneration !== previous.executionGeneration) nativeUnavailable()
    if (previous.scope === "assignment") {
      const status = await this.#service.status(key.token, { assignmentId: previous.assignmentId!,
        executionGeneration: previous.executionGeneration!, bindingJSON: previous.bindingJSON! })
      if (status.bindingJSON !== previous.bindingJSON || status.nativeSessionId !== sessionID) nativeUnavailable()
    }
    if (previous.credentialId === credentialId) return
    const digest = createHash("sha256").update(previousRaw).digest("hex")
    await this.#custody.retain("session-history", `${sessionID}:${digest}`, previousRaw)
    const replacement: SessionRecord = { ...previous, formatVersion: 1, credentialId }
    await this.#custody.replace("session", sessionID, digest, JSON.stringify(replacement))
    this.#known.set(sessionID, Object.freeze(replacement))
  }
  /** Private runtime calls, never exported through the work-tool catalogue. */
  async inspect(sessionID: string): Promise<Readonly<SessionRecord> | undefined> {
    return await this.restore(sessionID) ? this.#known.get(sessionID) : undefined
  }
  private async verifiedCredential(sessionID: string) {
    if (!await this.restore(sessionID)) nativeUnavailable()
    const binding = this.#known.get(sessionID)!, key = await this.credential(binding.credentialId)
    const identity = await this.#service.identity(key.token)
    if (identity.principalId !== binding.principalId || identity.scope !== binding.scope
      || identity.delegationId !== binding.delegationId || identity.assignmentId !== binding.assignmentId || identity.executionGeneration !== binding.executionGeneration) nativeUnavailable()
    return key
  }
  async probe(sessionID: string, subject: NativeSubject, continuationId: string | null = null) {
    return this.#service.status((await this.verifiedCredential(sessionID)).token, subject, continuationId)
  }
  async packet(sessionID: string, subject: NativeSubject, continuationId: string) {
    return this.#service.packet((await this.verifiedCredential(sessionID)).token, subject, continuationId)
  }
  async identity(sessionID: string) {
    return this.#service.identity((await this.verifiedCredential(sessionID)).token)
  }
  /** Immutable private startup witness, not authorization or a public disclosure operation. The
   * service independently checks its current controller credential before consuming this witness. */
  async immutableControllerIdentity(sessionID:string) {
    const record=await this.inspect(sessionID)
    if(record?.scope!=="controller")nativeUnavailable()
    return {controllerId:this.#service.controllerId,principalId:record.principalId,credentialId:record.credentialId}
  }
  async sourceIdentity(sessionID: string, signal?: AbortSignal) {
    return this.#service.identity((await this.verifiedCredential(sessionID)).token, signal, true)
  }
  async controllerPlanningAdmission(sessionID:string,signal:AbortSignal){
    if((await this.inspect(sessionID))?.scope!=="controller")nativeUnavailable()
    const key=await this.verifiedCredential(sessionID)
    return this.#service.controllerPlanningAdmission(key.token,signal)
  }
  async plannerTaskProgress(sessionID:string,taskReferenceJSON:string,signal:AbortSignal){
    if((await this.inspect(sessionID))?.scope!=="controller")nativeUnavailable()
    return this.#service.plannerTaskProgress((await this.verifiedCredential(sessionID)).token,taskReferenceJSON,signal)
  }
  async workerContext(sessionID: string, signal?: AbortSignal) {
    const binding = await this.inspect(sessionID)
    if (!binding || binding.scope !== "assignment") nativeUnavailable()
    const frame = await this.#service.workerContext((await this.verifiedCredential(sessionID)).token, signal)
    if (frame.assignmentId !== binding.assignmentId || frame.executionGeneration !== binding.executionGeneration || frame.bindingJSON !== binding.bindingJSON) nativeUnavailable()
    return frame
  }
  async retentionPermit(sessionID:string,request:{assignmentId:string;generation:string;executionId:string;snapshot:unknown},signal?:AbortSignal):Promise<boolean>{
    const binding=await this.inspect(sessionID)
    if(!binding||binding.scope!=="assignment"||binding.assignmentId!==request.assignmentId||binding.executionGeneration!==request.generation)nativeUnavailable()
    return this.#service.retentionPermit((await this.verifiedCredential(sessionID)).token,request,signal)
  }
  async controllerContext(sessionID: string, profileJSON: string, signal?: AbortSignal) {
    const binding = await this.inspect(sessionID)
    if (!binding || binding.scope !== "controller") nativeUnavailable()
    return this.#service.controllerContext((await this.verifiedCredential(sessionID)).token, profileJSON, signal)
  }
  async dispatchContext(sessionID:string,planId:string,signal?:AbortSignal) {
    const binding=await this.inspect(sessionID)
    if(!binding||binding.scope!=="controller")nativeUnavailable()
    return this.#service.dispatchContext((await this.verifiedCredential(sessionID)).token,planId,signal)
  }
  async reservationRetryEligibility(sessionID:string,reservationJSON:string,signal?:AbortSignal) {
    const binding=await this.inspect(sessionID)
    if(!binding||binding.scope!=="controller")nativeUnavailable()
    return this.#service.reservationRetryEligibility((await this.verifiedCredential(sessionID)).token,reservationJSON,signal)
  }
  async originalResultReceipt(sessionID:string,commandJSON:string,signal?:AbortSignal) {
    const binding=await this.inspect(sessionID)
    if(!binding||binding.scope!=="assignment")nativeUnavailable()
    return this.#service.originalResultReceipt((await this.verifiedCredential(sessionID)).token,commandJSON,signal)
  }
  /** Private hook-owned channel: native session selection is actual runtime state, not fabricated
   * ToolContext/message IDs or model fields. Service still authenticates each canonical request. */
  async contextChannel(sessionID: string, signal?: AbortSignal): Promise<BoundWorkChannel> {
    const binding = await this.inspect(sessionID)
    if (!binding) nativeUnavailable()
    const key = await this.verifiedCredential(sessionID)
    await this.#service.identity(key.token, signal, true)
    return boundedWorkChannel(this.#service.projectId, binding.scope, this.#serviceOrigin, key.token, 4 * 1024 * 1024,
      this.#requests, JSON.stringify([this.#service.storeId, this.#service.projectId, binding.hostId, binding.epoch,
        binding.sessionID, binding.assignmentId, binding.executionGeneration, binding.bindingJSON]))
  }
  async planningKnowledgeChannel(actual: Pick<ToolContext,"sessionID"|"messageID"|"id"|"signal">): Promise<BoundWorkChannel> {
    const bound=await this.resolve(actual)
    if(!bound || bound.scope!=="controller")nativeUnavailable()
    const key=await this.verifiedCredential(actual.sessionID)
    await this.sourceIdentity(actual.sessionID,actual.signal)
    // Controller lifetime is the exact chat, not the current host incarnation. Historical
    // connection custody supplies ONLY original-command lookup namespaces, never current authority.
    const raw=await this.#custody.read("session",actual.sessionID)
    if(raw===undefined)nativeUnavailable()
    const original=this.metadata(raw!,actual.sessionID)
    if(original.scope!=="controller")nativeUnavailable()
    const epochs=new Set([original.epoch])
    for(const row of await this.#custody.scan("controller-connection")){
      const v=closedObject(parse(row.raw),["formatVersion","sessionID","projectId","controllerId","principalId",
        "credentialId","workRoot","hostId","epoch"])
      if(!version(v.formatVersion)||typeof v.epoch!=="string"||!/^[a-f0-9]{64}$/.test(v.epoch)||
        typeof v.sessionID!=="string"||createHash("sha256").update(`${v.sessionID}:${v.epoch}`).digest("hex")!==row.keyDigest)nativeUnavailable()
      if(v.sessionID!==actual.sessionID)continue
      if(v.projectId!==this.#service.projectId||v.controllerId!==this.#service.controllerId||
        v.principalId!==original.principalId||v.credentialId!==original.credentialId||
        v.workRoot!==original.workRoot||v.hostId!==original.hostId)nativeUnavailable()
      epochs.add(v.epoch)
    }
    if(epochs.size>64)nativeUnavailable()
    const bindings=[...epochs].map(epoch=>JSON.stringify([this.#service.storeId,this.#service.projectId,epoch,actual.sessionID,"primary-knowledge-v1"]))
    return {...boundedPlanningKnowledgeChannel(bound.projectID,this.#serviceOrigin,key.token,this.#requests,
      JSON.stringify([this.#service.storeId,this.#service.projectId,this.#epoch,actual.sessionID,"primary-knowledge-v1"]),undefined,bindings),
      readBinding:bound.readBinding}
  }
  /** Internal current-controller observation, not a fabricated native ToolContext or model tool.
   * No command route, caller-selected credential/principal or persisted authorization cache. */
  async planningKnowledgeContextChannel(sessionID:string,signal:AbortSignal){
    if((await this.inspect(sessionID))?.scope!=="controller")nativeUnavailable()
    const key=await this.verifiedCredential(sessionID)
    await this.sourceIdentity(sessionID,signal)
    const channel=boundedPlanningKnowledgeChannel(this.#service.projectId,this.#serviceOrigin,key.token,this.#requests,
      contextJSON([this.#service.storeId,this.#service.projectId,this.#epoch,sessionID,"planner-knowledge-observation"]))
    return {send:async(route:"reads",raw:string,actualSignal:AbortSignal)=>{
      if(route!=="reads")nativeUnavailable()
      return channel.send("reads",raw,actualSignal)
    }}
  }
  async connectRuntime(sessionID:string) {
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.connectRuntime((await this.verifiedCredential(sessionID)).token)
  }
  async preparationContext(sessionID:string,assignmentId:string,generation:string,signal?:AbortSignal){
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.preparationContext((await this.verifiedCredential(sessionID)).token,assignmentId,generation,signal)
  }
  async preparationEnrollmentChannel(sessionID:string):Promise<BoundWorkChannel>{
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    const key=await this.verifiedCredential(sessionID)
    await this.sourceIdentity(sessionID)
    return boundedDelegationEnrollmentChannel(this.#service.projectId,this.#serviceOrigin,key.token,this.#requests,
      JSON.stringify([this.#service.storeId,this.#service.projectId,this.#epoch,sessionID,"prepared-delegation-v1"]))
  }
  async dispatchAttemptChannel(sessionID:string):Promise<BoundWorkChannel>{
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    const key=await this.verifiedCredential(sessionID)
    await this.sourceIdentity(sessionID)
    return boundedDispatchAttemptChannel(this.#service.projectId,this.#serviceOrigin,key.token,this.#requests,
      JSON.stringify([this.#service.storeId,this.#service.projectId,this.#epoch,sessionID,"dispatch-attempt-v1"]))
  }
  async dispatchMaterializationChannel(sessionID:string):Promise<BoundWorkChannel>{
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    const key=await this.verifiedCredential(sessionID)
    await this.sourceIdentity(sessionID)
    return boundedDispatchMaterializationChannel(this.#service.projectId,this.#serviceOrigin,key.token,this.#requests,
      JSON.stringify([this.#service.storeId,this.#service.projectId,this.#epoch,sessionID,"dispatch-materialization-v1"]))
  }
  async candidateAssemblyPermit(sessionID:string,requestJSON:string,signal?:AbortSignal):Promise<boolean>{
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    await this.sourceIdentity(sessionID)
    return this.#service.candidateAssemblyPermit((await this.verifiedCredential(sessionID)).token,requestJSON,signal)
  }
  async candidateTestPlan(sessionID:string,planRefJSON:string,signal?:AbortSignal){
    if((await this.inspect(sessionID))?.scope!=="controller")nativeUnavailable()
    return this.#service.candidateTestPlan((await this.verifiedCredential(sessionID)).token,planRefJSON,signal)
  }
  async candidateDelivery(sessionID:string,planRefJSON:string,signal?:AbortSignal){
    if((await this.inspect(sessionID))?.scope!=="controller")nativeUnavailable()
    return this.#service.candidateDelivery((await this.verifiedCredential(sessionID)).token,planRefJSON,signal)
  }
  async issueCandidateReviewTicket(sessionID:string,ticketJSON:string,signal?:AbortSignal){
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.issueCandidateReviewTicket((await this.verifiedCredential(sessionID)).token,ticketJSON,signal)
  }
  async candidateReviewReport(sessionID:string,reviewId:string,reportRefJSON:string,ticketDigest:string,signal?:AbortSignal){
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.candidateReviewReport((await this.verifiedCredential(sessionID)).token,reviewId,reportRefJSON,ticketDigest,signal)
  }
  async candidateReviewStatus(sessionID:string,reviewId:string,ticketDigest:string,signal?:AbortSignal){
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.candidateReviewStatus((await this.verifiedCredential(sessionID)).token,reviewId,ticketDigest,signal)
  }
  async candidateReviewPreparation(sessionID:string,planRefJSON:string,assemblyId:string,checkPlanId:string,signal?:AbortSignal){
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.candidateReviewPreparation((await this.verifiedCredential(sessionID)).token,planRefJSON,assemblyId,checkPlanId,signal)
  }
  async candidateTestPermit(sessionID:string,assemblyJSON:string,planRefJSON:string,jobJSON:string,signal?:AbortSignal){
    if((await this.inspect(sessionID))?.scope!=="controller")nativeUnavailable()
    return this.#service.candidateTestPermit((await this.verifiedCredential(sessionID)).token,assemblyJSON,planRefJSON,jobJSON,signal)
  }
  async initialContext(sessionID:string,subject:NativeSubject,intentId:string,signal?:AbortSignal){
    const binding=await this.inspect(sessionID)
    if(binding?.scope!=="controller")nativeUnavailable()
    return this.#service.initialContext((await this.verifiedCredential(sessionID)).token,subject,intentId,signal)
  }
  async hint(sessionID: string, subject: NativeSubject, signal: AbortSignal) {
    return this.#service.hint((await this.verifiedCredential(sessionID)).token, subject, signal)
  }
  async resolve(actual: Pick<ToolContext, "sessionID" | "messageID" | "id" | "signal">): Promise<BoundWorkChannel | undefined> {
    try {
      if (actual.signal.aborted || !session(actual.sessionID) || !actual.messageID || !actual.id || !await this.restore(actual.sessionID)) return undefined
      const binding = this.#known.get(actual.sessionID)!
      const credential = await this.credential(binding.credentialId)
      const identity = await this.#service.identity(credential.token, actual.signal)
      if (identity.scope !== binding.scope || identity.principalId !== binding.principalId || identity.delegationId !== binding.delegationId || identity.assignmentId !== binding.assignmentId
        || identity.executionGeneration !== binding.executionGeneration) return undefined
      return {...boundedWorkChannel(this.#service.projectId, binding.scope, this.#serviceOrigin, credential.token, 4 * 1024 * 1024,
        this.#requests, JSON.stringify([this.#service.storeId, this.#service.projectId, binding.hostId, binding.epoch,
          binding.sessionID, binding.assignmentId, binding.executionGeneration, binding.bindingJSON])),
        readBinding:contextJSON({storeId:this.#service.storeId,projectId:this.#service.projectId,sessionID:actual.sessionID,
          controllerId:this.#service.controllerId,principalId:binding.principalId,scope:binding.scope,
          delegationId:binding.delegationId,assignmentId:binding.assignmentId,
          executionGeneration:binding.executionGeneration,bindingJSON:binding.bindingJSON})}
    } catch(error) {
      if(error instanceof NativeRuntimeReadHold)throw error // Preserve a transient hold; never turn it into an unbound caller.
      return undefined
    }
  }
}
