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

/** Trusted test-client installer, never a model tool or public broker operation. Reopening reuses
 * the original private proof. Existing profiles/descriptor conflicts hold instead of being reset. */
export async function provisionAutomaticPrimary(config:Readonly<{root:string;forbiddenAreas:readonly string[];
  storeId:string;serviceOrigin:string;nativeOrigin:string;nativeRegistrationFile:string;clientPolicyFile:string;
  plugin:string;schemas:Readonly<Record<string,string>>}>):Promise<void>{
  if(process.platform!=="linux"||!uuidPattern.test(config.storeId)||
    ![config.root,config.nativeRegistrationFile,config.clientPolicyFile,config.plugin].every(value=>path.isAbsolute(value))||
    !Object.keys(config.schemas).length||Object.values(config.schemas).some(raw=>typeof raw!=="string"||Buffer.byteLength(raw)>512*1024))nativeUnavailable()
  exactLoopback(config.serviceOrigin,nativeUnavailable);exactLoopback(config.nativeOrigin,nativeUnavailable)
  const bootstrap=new LinuxNativeCustody(path.join(config.root,"bootstrap"),config.forbiddenAreas)
  await bootstrap.initialize()
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
    if(contextJSON(original)!==contextJSON(descriptor))nativeUnavailable()
    return
  }
  const proof=randomBytes(32)
  try{await bootstrap.retain("automatic-bootstrap","current",contextJSON({...descriptor,enrollmentProof:proof.toString("base64url")}))}
  finally{proof.fill(0)}
}

export type PrimaryBootstrap = Readonly<{
  root:string;workRoot:string;storeId:string;projectId:string;controllerId:string;hostId:string
  serviceOrigin:string;nativeOrigin:string;nativeRegistrationFile:string;transportOrigin:string
  schemas:Readonly<Record<string,string>>
  controller:Readonly<{principalId:string;credentialId:string;token:string}>
  serviceProof:Uint8Array;transportProof:Uint8Array
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
