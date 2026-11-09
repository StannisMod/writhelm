// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { createHash } from "node:crypto"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { prepareNativePeerProofs } from "./native-bootstrap-secrets.js"
import { NativeRuntimeClient, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { exactLoopback } from "./strict-json.js"
import { installFreshPrimaryPolicy } from "./primary-policy.js"

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
