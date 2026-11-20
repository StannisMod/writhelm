// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { createHash, randomBytes } from "node:crypto"
import * as fs from "node:fs/promises"
import { setTimeout as delay } from "node:timers/promises"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { prepareNativePeerProofs } from "./native-bootstrap-secrets.js"
import { NativeRuntimeClient, nativeUnavailable, uuidPattern, closedObject } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"
import { exactLoopback } from "./strict-json.js"
import { installFreshPrimaryPolicy, requirePrimaryPolicyFile, primaryPermissionRules, primaryPolicyStatements } from "./primary-policy.js"
import { decodeNativeExecutionDeployment } from "./native-initial.js"
import { decodeNativeContextPolicy } from "./native-context-policy.js"
import { pathToFileURL } from "node:url"
import type { OpenCodeClient } from "@opencode/client"
import { assertNativeContextPolicy } from "./native-context-policy.js"
import {selectedContractSchemas,selectContractRelease,type ContractReleaseInput} from "./contract-release.js"
import {ReviewDisplayCustody} from "./review-display-custody.js"
import type {ReviewDisplayScope} from "./review-display.js"

/** Native registry is the source of execution settings and credentials remain native-owned.
 * Canonical task/profile identity is still authoritative: inheritance cannot rewrite it. */
export async function provisionHarnessExecutionDeployment(config:Readonly<{
  root:string;projectId:string;forbiddenAreas:readonly string[];sessionID:string;workRoot:string;profileRecordJSON:string
}>,native:Pick<OpenCodeClient,"session"|"model"|"config">){
  const before=await native.session.get({sessionID:config.sessionID})
  if(before.id!==config.sessionID||before.revert||before.location.directory!==config.workRoot)nativeUnavailable()
  const inventory=await native.model.list({location:{directory:config.workRoot}})
  const selected:{providerID:string;id:string;variant?:string}|undefined=before.model??await native.model.default({location:{directory:config.workRoot}}).then(r=>
    r.data?{providerID:r.data.providerID,id:r.data.id}:undefined)
  if(!selected)throw new Error("OpenCode has no selected available execution model")
  const model=inventory.data.find(m=>m.enabled&&m.providerID===selected.providerID&&m.id===selected.id)
  if(!model)nativeUnavailable()
  const variant=selected.variant??"default",override=model.variants.find(v=>v.id===variant)
  if(variant!=="default"&&!override)nativeUnavailable()
  // Model.Info is materialized by OpenCode after provider/model transforms. Never retain its
  // headers/body/settings wholesale: they can contain credentials and private provider metadata.
  const settings={...model.settings,...override?.settings}
  const p=exactJSON(config.profileRecordJSON,nativeUnavailable).payload
  const expected={providerID:p?.model?.providerId,id:p?.model?.modelId,variant:p?.model?.variant??"default"}
  if(contextJSON({...selected,variant})!==contextJSON(expected))nativeUnavailable()
  const effortKeys=["reasoningEffort","effort"].filter(key=>typeof settings[key]==="string")
  if(p.effort!=="native-default"&&(effortKeys.length!==1||settings[effortKeys[0]!]!==p.effort))
    throw new Error("canonical effort does not match an observable OpenCode effort setting")
  const effortOptionKey=p.effort==="native-default"?null:effortKeys[0]!
  const reserve=(v:any)=>{const n=Number(v?.value);if(!Number.isSafeInteger(n)||n<1)nativeUnavailable();return n}
  if(reserve(p.contextTokens)>model.limit.context)nativeUnavailable()
  const maxTokens=Math.min(reserve(p.outputReserveTokens),reserve(p.compactionReserveTokens),model.limit.output,
    settings.maxTokens===undefined?model.limit.output:settings.maxTokens)
  if(!Number.isSafeInteger(maxTokens)||maxTokens<1)nativeUnavailable()
  const entries=await native.config.get({location:{directory:config.workRoot}})
  let keepTokens:number|undefined,bufferTokens:number|undefined
  for(const entry of entries)if(entry.type==="document"){
    if(entry.info.compaction?.keep?.tokens!==undefined)keepTokens=entry.info.compaction.keep.tokens
    if(typeof entry.info.compaction?.buffer==="number")bufferTokens=entry.info.compaction.buffer
  }
  const profileKeyJSON=contextJSON(exactJSON(config.profileRecordJSON,nativeUnavailable).key)
  const contextPolicyJSON=contextJSON({formatVersion:1,profileKeyJSON,policyId:p.compactionPolicyId,nativeProfileId:p.nativeProfileId,
    auto:true,background:false,keepTokens:keepTokens??null,bufferTokens:bufferTokens??null})
  assertNativeContextPolicy(entries,config.profileRecordJSON,contextPolicyJSON)
  const after=await native.session.get({sessionID:config.sessionID})
  if(after.revert||after.location.directory!==config.workRoot||contextJSON(before.model??null)!==contextJSON(after.model??null))nativeUnavailable()
  return provisionExecutionDeployment({...config,contextPolicyJSON,executionJSON:contextJSON({
    formatVersion:1,profileKeyJSON,model:{providerID:selected.providerID,id:selected.id,...(p.model.variant===null?{}:{variant:p.model.variant})},
    baseURL:settings.baseURL??null,routeID:p.route,effortOptionKey,
    nativeOptions:{...(effortOptionKey===null?{}:{[effortOptionKey]:settings[effortOptionKey]}),maxTokens}
  })})
}

export type ExecutionDeployment=Readonly<{profileRecordJSON:string;executionJSON:string;contextPolicyJSON:string}>

/** Trusted installation input only, not a model tool or a grant derived from canonical profile
 * metadata. Installs existing runtime formats; no native-config mutation, credential copy or inference.
 * The original bundle precedes its projections so concurrent/conflicting installs cannot mix them. */
export function executionDeploymentSelection(config:ExecutionDeployment&Readonly<{
  root:string;projectId:string;forbiddenAreas:readonly string[]
}>){
  if(process.platform!=="linux"||!uuidPattern.test(config.projectId)||!path.isAbsolute(config.root))nativeUnavailable()
  for(const raw of [config.profileRecordJSON,config.executionJSON,config.contextPolicyJSON])
    if(typeof raw!=="string"||Buffer.byteLength(raw)>262144)nativeUnavailable()
  const record=closedObject(exactJSON(config.profileRecordJSON,nativeUnavailable),["formatVersion","family","key","payload"])
  const key=closedObject(record.key,["projectId","profileId","revision"])
  if(record.formatVersion?.value!=="1"||record.family!=="profiles"||key.projectId!==config.projectId||
    typeof key.profileId!=="string"||!uuidPattern.test(key.profileId)||typeof key.revision?.value!=="string"||
    !/^(0|[1-9][0-9]*)$/.test(key.revision.value)||BigInt(key.revision.value)>9223372036854775807n)nativeUnavailable()
  const payload=closedObject(record.payload,["nativeProfileId","qualificationEvidence","model","route","effort","contextTokens",
    "outputReserveTokens","toolReserveTokens","compactionReserveTokens","compactionPolicyId","requestAccountingProfileId",
    "executionAssurance","permissions","platform","qualification"])
  closedObject(payload.model,["providerId","modelId","variant"])
  if([payload.model.providerId,payload.model.modelId].some(value=>typeof value!=="string"||!value.length||value.length>256)||
    payload.model.variant!==null&&(typeof payload.model.variant!=="string"||!payload.model.variant.length||payload.model.variant.length>128))nativeUnavailable()
  if(!Array.isArray(payload.qualificationEvidence)||payload.qualificationEvidence.length>256||
    !["unqualified","qualified"].includes(payload.qualification)||payload.executionAssurance!=="E-COOP-1"||
    [payload.nativeProfileId,payload.route,payload.effort,payload.compactionPolicyId,payload.requestAccountingProfileId]
      .some(value=>typeof value!=="string"||!value.length||value.length>256))nativeUnavailable()
  const execution=decodeNativeExecutionDeployment(config.profileRecordJSON,config.executionJSON)
  const policy=decodeNativeContextPolicy(config.profileRecordJSON,config.contextPolicyJSON)
  const p=execution.p
  const count=(v:any)=>{
    if(typeof v?.value!=="string"||!/^[1-9][0-9]*$/.test(v.value)||BigInt(v.value)>2147483647n)nativeUnavailable()
    return BigInt(v.value)
  }
  if(count(p.outputReserveTokens)+count(p.toolReserveTokens)+count(p.compactionReserveTokens)>=count(p.contextTokens)||
    BigInt(execution.options.maxTokens as number)>count(p.compactionReserveTokens))nativeUnavailable()
  const profileKeyJSON=contextJSON(key),executionJSON=contextJSON(exactJSON(config.executionJSON,nativeUnavailable))
  const contextPolicyJSON=contextJSON(exactJSON(config.contextPolicyJSON,nativeUnavailable))
  const original=contextJSON({formatVersion:1,profileRecordJSON:contextJSON(record),executionJSON,contextPolicyJSON})
  return {profileKeyJSON,executionJSON,contextPolicyJSON,original,policy}
}
export async function provisionExecutionDeployment(config:ExecutionDeployment&Readonly<{
  root:string;projectId:string;forbiddenAreas:readonly string[]
}>){
  const {profileKeyJSON,executionJSON,contextPolicyJSON,original,policy}=executionDeploymentSelection(config)
  const custody=new LinuxNativeCustody(path.join(config.root,"bootstrap"),config.forbiddenAreas);await custody.initialize()
  const projections=[["execution-profile",executionJSON],["native-context-policy",contextPolicyJSON]] as const
  for(const [kind,expected]of projections){
    const previous=await custody.read(kind,profileKeyJSON)
    if(previous!==undefined&&previous!==expected)nativeUnavailable()
  }
  await custody.retain("execution-deployment",profileKeyJSON,original)
  for(const [kind,expected]of projections)await custody.retain(kind,profileKeyJSON,expected)
  for(const [kind,expected]of projections)if(await custody.read(kind,profileKeyJSON)!==expected)nativeUnavailable()
  return Object.freeze({kind:"deployment-provisioned" as const,profileKeyJSON,
    deploymentDigest:createHash("sha256").update(original).digest("hex"),keepTokens:policy.keep,bufferTokens:policy.buffer})
}

/** Trusted test-client installer, never a model tool or public broker operation. Reopening reuses
 * the original private proof. Existing profiles/descriptor conflicts hold instead of being reset. */
export async function provisionAutomaticPrimary(config:Readonly<{root:string;forbiddenAreas:readonly string[];
  storeId:string;serviceOrigin:string;nativeOrigin:string;nativeRegistrationFile:string;clientPolicyFile:string;
  plugin:string;schemas:Readonly<Record<string,string>>;executionDeployments?:readonly ExecutionDeployment[]}>):Promise<void>{
  if(process.platform!=="linux"||!uuidPattern.test(config.storeId)||
    ![config.root,config.nativeRegistrationFile,config.clientPolicyFile,config.plugin].every(value=>path.isAbsolute(value))||
    !Object.keys(config.schemas).length||Object.values(config.schemas).some(raw=>typeof raw!=="string"||Buffer.byteLength(raw)>512*1024))nativeUnavailable()
  exactLoopback(config.serviceOrigin,nativeUnavailable);exactLoopback(config.nativeOrigin,nativeUnavailable)
  const bootstrap=new LinuxNativeCustody(path.join(config.root,"bootstrap"),config.forbiddenAreas)
  await bootstrap.initialize()
  if(config.executionDeployments!==undefined){
    if(!Array.isArray(config.executionDeployments)||config.executionDeployments.length>32)nativeUnavailable()
    const keys=new Set<string>()
    for(const deployment of config.executionDeployments){
      closedObject(deployment,["profileRecordJSON","executionJSON","contextPolicyJSON"])
      const projectId=exactJSON(deployment.profileRecordJSON,nativeUnavailable).key?.projectId
      const selected=executionDeploymentSelection({...deployment,root:config.root,projectId,forbiddenAreas:config.forbiddenAreas})
      if(keys.has(selected.profileKeyJSON))nativeUnavailable()
      keys.add(selected.profileKeyJSON)
    }
    await bootstrap.retain("automatic-execution-deployments","current",contextJSON({formatVersion:1,deployments:config.executionDeployments}))
  }
  const profile=new LinuxNativeCustody(path.dirname(config.clientPolicyFile),config.forbiddenAreas);await profile.initialize()
  try{
    await fs.writeFile(config.clientPolicyFile,JSON.stringify({plugins:[config.plugin],permissions:primaryPermissionRules,policies:primaryPolicyStatements})+"\n",{flag:"wx",mode:0o600})
  }catch(error){if((error as NodeJS.ErrnoException).code!=="EEXIST")throw error}
  await requirePrimaryPolicyFile(config.clientPolicyFile)
  const policy=JSON.parse(await fs.readFile(config.clientPolicyFile,"utf8"))
  if(!Array.isArray(policy.plugins)||!policy.plugins.includes(config.plugin)||
    !Array.isArray(policy.permissions)||policy.permissions.length<primaryPermissionRules.length||
    primaryPermissionRules.some((required,index)=>{
      const rule=policy.permissions.slice(-primaryPermissionRules.length)[index]
      return !rule||typeof rule!=="object"||Object.keys(rule).length!==3||
        rule.action!==required.action||rule.resource!==required.resource||rule.effect!==required.effect
    }))nativeUnavailable()
  const previous=await bootstrap.read("automatic-bootstrap","current")
  const descriptor={formatVersion:1,storeId:config.storeId,serviceOrigin:config.serviceOrigin,nativeOrigin:config.nativeOrigin,
    nativeRegistrationFile:config.nativeRegistrationFile,clientPolicyFile:config.clientPolicyFile,schemas:config.schemas}
  if(previous!==undefined){
    const old=closedObject(exactJSON(previous,nativeUnavailable),[...Object.keys(descriptor),"enrollmentProof"])
    if(typeof old.enrollmentProof!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(old.enrollmentProof))nativeUnavailable()
    const {enrollmentProof:_,...original}=old
    // Add the two previously omitted read contracts without rewriting a retained bootstrap or
    // changing any existing contract bytes. Other installation conflicts still hold.
    if(!original.schemas||typeof original.schemas!=="object"||Array.isArray(original.schemas)||
      contextJSON(original)!==contextJSON({...descriptor,schemas:original.schemas}))nativeUnavailable()
    const selected=await selectedContractSchemas(bootstrap,previous)
    const hasSelection=await bootstrap.read("contract-selection","current")!==undefined
    if(hasSelection){
      if(contextJSON(selected,2*1024*1024)!==contextJSON(config.schemas,2*1024*1024))nativeUnavailable()
      return
    }
    if(Object.entries(original.schemas).some(([key,value])=>config.schemas[key]!==value)||
      Object.keys(config.schemas).some(key=>!Object.hasOwn(original.schemas,key)&&!["knowledge-read1","work-read2"].includes(key)))nativeUnavailable()
    for(const name of ["knowledge-read1","work-read2"])
      if(config.schemas[name]!==undefined&&!Object.hasOwn(original.schemas,name))
        await bootstrap.retain("read-contract",name,config.schemas[name]!)
    return
  }
  const proof=randomBytes(32)
  try{await bootstrap.retain("automatic-bootstrap","current",contextJSON({...descriptor,enrollmentProof:proof.toString("base64url")}))}
  finally{proof.fill(0)}
}

/** Explicit trusted installation action, never automatic startup, a model tool or owner-key use. */
export async function provisionAutomaticContractRelease(config:ContractReleaseInput&Readonly<{root:string;forbiddenAreas:readonly string[]}>){
  if(process.platform!=="linux"||!path.isAbsolute(config.root)||
    !Array.isArray(config.forbiddenAreas)||!config.forbiddenAreas.length||
    config.forbiddenAreas.some(value=>typeof value!=="string"||!path.isAbsolute(value)))nativeUnavailable()
  const storage=new LinuxNativeCustody(path.join(config.root,"bootstrap"),config.forbiddenAreas);await storage.initialize()
  const original=await storage.read("automatic-bootstrap","current")
  if(original===undefined)nativeUnavailable()
  return selectContractRelease(storage,original!,config)
}

/** Trusted declaration for exact legacy same-language source, not a translation/owner decision.
 * No source bodies, credentials, request mutation or native/model effects are written. */
export async function provisionReviewSourceLanguage(config:Readonly<{root:string;forbiddenAreas:readonly string[];
  expectedBootstrapDigest:string;scope:ReviewDisplayScope;language:string}>){
  if(process.platform!=="linux"||!path.isAbsolute(config.root)||!Array.isArray(config.forbiddenAreas)||
    !config.forbiddenAreas.length||config.forbiddenAreas.some(x=>typeof x!=="string"||!path.isAbsolute(x)))nativeUnavailable()
  const bootstrap=new LinuxNativeCustody(path.join(config.root,"bootstrap"),config.forbiddenAreas);await bootstrap.initialize()
  const original=await bootstrap.read("automatic-bootstrap","current")
  if(original===undefined||createHash("sha256").update(original).digest("hex")!==config.expectedBootstrapDigest||
    exactJSON(original,nativeUnavailable).storeId!==config.scope.storeId)nativeUnavailable()
  const storage=new LinuxNativeCustody(path.join(config.root,"review-display"),config.forbiddenAreas);await storage.initialize()
  const raw=await new ReviewDisplayCustody(storage).retainSourceLanguage(config.scope,config.language)
  return {kind:"exact-source-language-retained",digest:createHash("sha256").update(raw).digest("hex")}
}

export type PrimaryBootstrap = Readonly<{
  root:string;workRoot:string;storeId:string;projectId:string;controllerId:string;hostId:string
  serviceOrigin:string;nativeOrigin:string;nativeRegistrationFile:string;transportOrigin:string
  schemas:Readonly<Record<string,string>>
  controller:Readonly<{principalId:string;credentialId:string;token:string}>
  serviceProof:Uint8Array;transportProof:Uint8Array
  executionDeployments?:readonly ExecutionDeployment[]
}>
/** Trusted local provisioning of an ALREADY enrolled controller, not a model tool or authority
 * issuance API. The private service reauthenticates its real scope before binding/connect. Never
 * read an owner profile, copy provider auth, choose a native session, start a host or call a model. */
export async function preparePrimaryBootstrap(config:PrimaryBootstrap):Promise<void>{
  if(process.platform!=="linux"||!path.isAbsolute(config.root)||!path.isAbsolute(config.workRoot)||!path.isAbsolute(config.nativeRegistrationFile)
    ||![config.storeId,config.projectId,config.controllerId,config.hostId,config.controller.principalId,config.controller.credentialId].every(id=>uuidPattern.test(id))
    ||!config.controller.token.startsWith(`wh1.${config.storeId}.${config.controller.credentialId}.`))nativeUnavailable()
  for(const origin of [config.serviceOrigin,config.nativeOrigin,config.transportOrigin])exactLoopback(origin,nativeUnavailable)
  if(!Object.keys(config.schemas).length||Object.values(config.schemas).some(raw=>typeof raw!=="string"||Buffer.byteLength(raw)>512*1024))nativeUnavailable()
  const service=new NativeRuntimeClient(config.serviceOrigin,Buffer.from(config.serviceProof).toString("base64url"),
    config.storeId,config.projectId,config.controllerId,config.hostId)
  const identity=await service.identity(config.controller.token)
  if(identity.scope!=="controller"||identity.principalId!==config.controller.principalId||identity.credentialId!==config.controller.credentialId)nativeUnavailable()
  if(config.executionDeployments!==undefined){
    if(!Array.isArray(config.executionDeployments)||config.executionDeployments.length>32)nativeUnavailable()
    for(const deployment of config.executionDeployments)await provisionExecutionDeployment({
      ...deployment,root:config.root,projectId:config.projectId,forbiddenAreas:[config.workRoot]})
  }
  const custody=new LinuxNativeCustody(path.join(config.root,"native"),[config.workRoot]);await custody.initialize()
  await installFreshPrimaryPolicy(config.workRoot)
  await custody.retain("credential",config.controller.credentialId,JSON.stringify({formatVersion:1,kind:"native-scoped-key-v1",
    storeId:config.storeId,projectId:config.projectId,principalId:config.controller.principalId,credentialId:config.controller.credentialId,token:config.controller.token}))
  const peers=await prepareNativePeerProofs(custody,config)
  try{
    const bootstrap=new LinuxNativeCustody(path.join(config.root,"bootstrap"),[config.workRoot]);await bootstrap.initialize()
    const descriptor={formatVersion:1,storeId:config.storeId,projectId:config.projectId,controllerId:config.controllerId,hostId:config.hostId,
      controllerCredentialId:config.controller.credentialId,workRoot:config.workRoot,serviceOrigin:config.serviceOrigin,nativeOrigin:config.nativeOrigin,
      nativeRegistrationFile:config.nativeRegistrationFile,transportOrigin:config.transportOrigin,
      serviceProof:Buffer.from(peers.serviceProof).toString("base64url"),transportProof:Buffer.from(peers.transportProof).toString("base64url"),schemas:config.schemas}
    await bootstrap.retain("primary-bootstrap","current",contextJSON(descriptor))
    // Nonsecret source identity for operator comparison; no secret/profile export is returned.
    await bootstrap.retain("primary-bootstrap-source","current",contextJSON({formatVersion:1,
      schemas:Object.fromEntries(Object.entries(config.schemas).map(([name,raw])=>[name,createHash("sha256").update(raw).digest("hex")]))}))
  }finally{peers.serviceProof.fill(0);peers.transportProof.fill(0)}
}

export type AutomaticSessionEnrollment=Readonly<{sessionID:string;workRoot:string;projectId:string;controllerId:string;
  principalId:string;credentialId:string;token:string}>
export type AutomaticNativeBootstrap=Readonly<{root:string;hostId:string;transportOrigin:string;serviceProof:string;transportProof:string}>
/** Server-side test-client broker channel. No owner credential, folder/actor/grant arguments or
 * arbitrary service discovery. An uncertain response does not allocate another session/request;
 * the broker reconciles original project/session enrollment from retained native identity. */
export class AutomaticEnrollmentClient {
  readonly #origin:string
  readonly #proof:string
  constructor(origin:string,proof:Uint8Array,private readonly storeId:string,private readonly fetcher:typeof fetch=fetch){
    this.#origin=exactLoopback(origin,nativeUnavailable)
    if(!uuidPattern.test(storeId)||proof.byteLength!==32)nativeUnavailable()
    this.#proof=Buffer.from(proof).toString("base64url")
  }
  private async request(operation:"open-session"|"activate-session",sessionID:string,signal?:AbortSignal){
    if(!/^ses_[A-Za-z0-9_-]{1,240}$/.test(sessionID))nativeUnavailable()
    const deadline=signal?AbortSignal.any([signal,AbortSignal.timeout(30000)]):AbortSignal.timeout(30000)
    const request={method:"POST",redirect:"error" as const,credentials:"omit" as const,
      headers:{"Content-Type":"application/json","X-Writhelm-Enrollment-Proof":this.#proof},
      body:JSON.stringify({formatVersion:1,operation,sessionID}),signal:deadline}
    let response:Response
    for(let attempt=0;;attempt++){
      response=await this.fetcher(this.#origin+"/internal/enrollment/v1",request)
      // This private endpoint's HTTP admission emits 429 BEFORE body/auth/broker dispatch.
      // Only that definite non-effect may be retried. Lost replies/other failures stay held.
      if(response.status!==429||attempt===31)break
      await response.body?.cancel()
      await delay(100,undefined,{signal:deadline})
    }
    if(!response.ok||!response.body){await response.body?.cancel();throw Object.assign(new Error("Enrollment transport held"),{enrollmentStatus:response.status})}
    const reader=response.body.getReader(),parts:Uint8Array[]=[]
    let total=0
    try {
      for(;;){
        const {done,value}=await reader.read();if(done)break
        total+=value.byteLength;if(total>16384)nativeUnavailable()
        parts.push(value)
      }
    } finally {await reader.cancel();reader.releaseLock()}
    return exactJSON(new TextDecoder("utf-8",{fatal:true}).decode(Buffer.concat(parts)),nativeUnavailable)
  }
  async activateSession(binding:AutomaticSessionEnrollment,signal?:AbortSignal):Promise<AutomaticNativeBootstrap>{
    const result=closedObject(await this.request("activate-session",binding.sessionID,signal),
      ["formatVersion","kind","sessionID","projectId","controllerId","credentialId","runtimeJSON"])
    if(result.formatVersion?.value!=="1"||result.kind!=="runtime-configured"||result.sessionID!==binding.sessionID||
      result.projectId!==binding.projectId||result.controllerId!==binding.controllerId||result.credentialId!==binding.credentialId||typeof result.runtimeJSON!=="string")nativeUnavailable()
    const profile=closedObject(exactJSON(result.runtimeJSON,nativeUnavailable),["root","hostId","transportOrigin","serviceProof","transportProof"])
    if(typeof profile.root!=="string"||!path.isAbsolute(profile.root)||typeof profile.hostId!=="string"||!uuidPattern.test(profile.hostId))nativeUnavailable()
    exactLoopback(profile.transportOrigin,nativeUnavailable)
    for(const raw of [profile.serviceProof,profile.transportProof]){
      if(typeof raw!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(raw))nativeUnavailable()
      const bytes=Buffer.from(raw,"base64url")
      try {if(bytes.length!==32||bytes.toString("base64url")!==raw)nativeUnavailable()}finally{bytes.fill(0)}
    }
    return Object.freeze(profile) as AutomaticNativeBootstrap
  }
  async openSession(sessionID:string,signal?:AbortSignal):Promise<AutomaticSessionEnrollment>{
    const result=closedObject(await this.request("open-session",sessionID,signal),
      ["formatVersion","kind","sessionID","workRoot","projectId","controllerId","principalId","credentialId","token"])
    if(result.formatVersion?.value!=="1"||result.kind!=="session-enrolled"||result.sessionID!==sessionID||
      ![result.projectId,result.controllerId,result.principalId,result.credentialId].every(id=>typeof id==="string"&&uuidPattern.test(id))||
      typeof result.workRoot!=="string"||!path.isAbsolute(result.workRoot)||typeof result.token!=="string"||
      !new RegExp(`^wh1\\.${this.storeId}\\.${result.credentialId}\\.[A-Za-z0-9_-]{43}$`).test(result.token))nativeUnavailable()
    const secret=Buffer.from(result.token.split(".")[3]!,"base64url")
    try {if(secret.byteLength!==32||secret.toString("base64url")!==result.token.split(".")[3])nativeUnavailable()}
    finally {secret.fill(0)}
    return Object.freeze({sessionID,workRoot:result.workRoot,projectId:result.projectId,controllerId:result.controllerId,
      principalId:result.principalId,credentialId:result.credentialId,token:result.token})
  }
}

/** Operator/installer entrypoint. Configuration arrives on stdin, never model tool arguments.
 * Existing imports have no CLI side effects; no service/native discovery or model API is used. */
async function executionProvisioningCLI(){
  const operation=process.argv[2]
  if(process.argv.length!==3||!["provision-execution","provision-contract-release","provision-review-source-language"].includes(operation!))nativeUnavailable()
  const parts:Buffer[]=[];let size=0
  for await(const chunk of process.stdin){
    const bytes=Buffer.from(chunk);size+=bytes.length
    if(size>(operation==="provision-contract-release"?2*1024*1024:1024*1024))nativeUnavailable()
    parts.push(bytes)
  }
  const config=closedObject(exactJSON(new TextDecoder("utf-8",{fatal:true}).decode(Buffer.concat(parts)),nativeUnavailable),
    operation==="provision-contract-release"?
      ["root","forbiddenAreas","expectedBootstrapDigest","previousBundleDigest","previousReleaseDigest","schemas"]:
    operation==="provision-review-source-language"?
      ["root","forbiddenAreas","expectedBootstrapDigest","scope","language"]:
      ["root","projectId","forbiddenAreas","profileRecordJSON","executionJSON","contextPolicyJSON"])
  if(!Array.isArray(config.forbiddenAreas)||config.forbiddenAreas.length<1||config.forbiddenAreas.length>256||
    config.forbiddenAreas.some(value=>typeof value!=="string"||!path.isAbsolute(value)))nativeUnavailable()
  const result=operation==="provision-contract-release"?
    await provisionAutomaticContractRelease(config as Parameters<typeof provisionAutomaticContractRelease>[0]):
    operation==="provision-review-source-language"?
      await provisionReviewSourceLanguage(config as Parameters<typeof provisionReviewSourceLanguage>[0]):
    await provisionExecutionDeployment(config as Parameters<typeof provisionExecutionDeployment>[0])
  process.stdout.write(JSON.stringify(result)+"\n")
}
if(process.argv[1]&&import.meta.url===pathToFileURL(path.resolve(process.argv[1])).href)
  void executionProvisioningCLI().catch(()=>{
    process.stderr.write(process.argv[2]==="provision-review-source-language"?
      "Writhelm exact source-language declaration held; no request, approval, translation or model effect.\n":
      process.argv[2]==="provision-contract-release"?
      "Writhelm contract release held; no bootstrap replacement, model or native host was started.\n":
      "Writhelm execution deployment held; no model or native host was started.\n")
    process.exitCode=2
  })
