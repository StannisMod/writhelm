// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import * as fs from "node:fs/promises"
import { Plugin } from "@opencode/plugin"
import { OpenCode } from "@opencode/client"
import { Service } from "@opencode/client/service"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "./connected-tools.js"
import { NativeRuntimeClient, closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { createNativeCredentialRegistry } from "./native-host-identity.js"
import { registerPrimaryGovernor } from "./native-primary.js"
import { startProtectedNativeRuntime, nativeRuntimeReference } from "./native-runtime.js"
import { exactJSON } from "./strict-json.js"

/** Real server entrypoint. Enabling the package without a trusted protected bootstrap refuses;
 * there is no GUI-only success followed by ordinary unrestricted coding. No ctx.options token,
 * discovery of the user's existing service, owner-profile import or synthetic ready task. */
export default Plugin.define({id:"writhelm-opencode",async setup(ctx){
  const root=process.env.WRITHELM_PRIMARY_ROOT
  if(process.platform!=="linux"||ctx.app.version!=="2.2.5"||!root||!path.isAbsolute(root)||await fs.realpath(root)!==root)nativeUnavailable()
  const workRoot=await fs.realpath(ctx.location.directory)
  const bootstrap=new LinuxNativeCustody(path.join(root,"bootstrap"),[workRoot])
  await bootstrap.initialize()
  const raw=await bootstrap.read("primary-bootstrap","current")
  if(!raw)nativeUnavailable()
  const cfg=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","storeId","projectId","controllerId","hostId","controllerCredentialId",
    "workRoot","serviceOrigin","nativeOrigin","nativeRegistrationFile","transportOrigin","serviceProof","transportProof","schemas"])
  if(cfg.formatVersion?.value!=="1"||![cfg.storeId,cfg.projectId,cfg.controllerId,cfg.hostId,cfg.controllerCredentialId].every(v=>typeof v==="string"&&uuidPattern.test(v)))nativeUnavailable()
  // Desktop first loads its default location too. A configured governed stand must not silently
  // become ordinary coding there. Keep the GUI available, but hold that location's execution until
  // its project has an explicit controller binding; never repoint the configured primary.
  if(cfg.workRoot!==workRoot){
    const belongs=async(sessionID:string)=>(await ctx.session.get({sessionID})).location.directory===workRoot
    const receiver=async(sessionID:string)=>{
      const runtime=nativeRuntimeReference(root,cfg.hostId)
      const binding=await runtime?.registry.inspect(sessionID)
      if(!runtime||binding?.scope!=="assignment"||binding.workRoot!==workRoot||binding.hostId!==cfg.hostId)
        throw new Error("Writhelm location is not a bound worker; open the configured governed project before starting work")
      await runtime.registry.sourceIdentity(sessionID)
      return runtime
    }
    await ctx.session.hook("prompt",async event=>{if(await belongs(event.sessionID)){const runtime=await receiver(event.sessionID);await runtime.admitPrompt(event)}})
    await ctx.session.hook("context",async event=>{if(await belongs(event.sessionID)){const runtime=await receiver(event.sessionID);await runtime.admitContext(event.sessionID,event)}})
    await ctx.session.hook("model.request",async event=>{if(await belongs(event.sessionID)){
      if(event.kind!=="primary")throw new Error("Qualified worker auxiliary-generation/compaction composition is required")
      const runtime=await receiver(event.sessionID);await runtime.admitModel(event)
    }})
    await ctx.session.hook("title",async event=>{if(await belongs(event.sessionID))event.result="Writhelm — bounded session"})
    await ctx.session.hook("compaction",async event=>{if(await belongs(event.sessionID))throw new Error("Generic worker compaction is held; task-shaped composition required")})
    await ctx.tool.hook("execute.before",async event=>{if(await belongs(event.sessionID)){const runtime=await receiver(event.sessionID);await runtime.admitTool(event.sessionID)}})
    await ctx.permission.hook("evaluate",async event=>{if(await belongs(event.sessionID)){
      try{const runtime=await receiver(event.sessionID);await runtime.admitTool(event.sessionID)}
      catch{event.effect="deny";event.message="Unenrolled/unreconciled Writhelm worker location is held"}
    }})
    return
  }
  const proof=(value:unknown)=>{
    if(typeof value!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(value))nativeUnavailable()
    const bytes=Buffer.from(value,"base64url")
    if(bytes.length!==32||bytes.toString("base64url")!==value)nativeUnavailable()
    return bytes
  }
  const serviceProof=proof(cfg.serviceProof),transportProof=proof(cfg.transportProof)
  if(typeof cfg.nativeRegistrationFile!=="string"||!path.isAbsolute(cfg.nativeRegistrationFile))nativeUnavailable()
  const endpoint=await Service.discover({file:cfg.nativeRegistrationFile,version:"2.2.5"})
  if(!endpoint||endpoint.url!==cfg.nativeOrigin)nativeUnavailable()
  const native=OpenCode.make({baseUrl:endpoint.url,headers:Service.headers(endpoint)})
  const custody=new LinuxNativeCustody(path.join(root,"native"),[workRoot])
  const requests=new LinuxWorkRequestCustody(path.join(root,"requests"),[workRoot])
  const service=new NativeRuntimeClient(cfg.serviceOrigin,cfg.serviceProof,cfg.storeId,cfg.projectId,cfg.controllerId,cfg.hostId)
  const registry=await createNativeCredentialRegistry(ctx,native,cfg.nativeOrigin,custody,service,cfg.serviceOrigin,requests)
  await registry.restoreAll()
  let runtime:Awaited<ReturnType<typeof startProtectedNativeRuntime>>|undefined
  const governor=await registerPrimaryGovernor(ctx,registry,bootstrap,{workRoot,controllerId:cfg.controllerId,
    controllerCredentialId:cfg.controllerCredentialId,projectId:cfg.projectId,schemas:cfg.schemas},async sessionID=>{
    runtime=await startProtectedNativeRuntime(ctx,native,{root,nativeOrigin:cfg.nativeOrigin,serviceOrigin:cfg.serviceOrigin,transportOrigin:cfg.transportOrigin,
      storeId:cfg.storeId,projectId:cfg.projectId,controllerId:cfg.controllerId,hostId:cfg.hostId,
      controllerSessionID:sessionID,assignmentSessionIDs:[],serviceProof,transportProof})
    await registry.connectRuntime(sessionID)
  })
  return async()=>{await governor.close();await runtime?.close();serviceProof.fill(0);transportProof.fill(0)}
}})
