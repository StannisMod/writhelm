// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { createHash } from "node:crypto"
import * as fs from "node:fs/promises"
import { Plugin } from "@opencode/plugin"
import { OpenCode, isSessionNotFoundError } from "@opencode/client"
import { Service } from "@opencode/client/service"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "./connected-tools.js"
import { NativeRuntimeClient, closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { createNativeCredentialRegistry } from "./native-host-identity.js"
import { registerPrimaryGovernor, registerAutomaticPrimaryGovernor, type AutomaticSessionRuntime } from "./native-primary.js"
import { startProtectedNativeRuntime, nativeRuntimeReference, nativeRuntimeForSession, NativeSessionToolRouter, loadCandidateDeliveryBootstrap } from "./native-runtime.js"
import { AutomaticEnrollmentClient, provisionExecutionDeployment } from "./primary-bootstrap.js"
import { exactJSON } from "./strict-json.js"
import { nativeContextAdmission } from "./native-context-reference.js"
import { ClientAdmission } from "./primary-rpc.js"
import { NativeSessionCreation, userSessionID } from "./native-session-creation.js"
import { ApprovalDelivery } from "./approval-delivery.js"
import { NativePlannerActivity } from "./native-planner-progress.js"
import {selectedContractSchemas} from "./contract-release.js"
import {ReviewDisplayCustody} from "./review-display-custody.js"

/** Automatic-mode entrypoint shared with model-free boundary tests. Activation resolves only after
 * the real native runtime, tools and service connect handshake, not after credential issuance. */
export async function startAutomaticPrimaryServer(ctx:Plugin.Context,root:string,bootstrap:LinuxNativeCustody,raw:string){
  const cfg=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","storeId","serviceOrigin","nativeOrigin","nativeRegistrationFile","enrollmentProof","clientPolicyFile","schemas"])
  if(cfg.formatVersion?.value!=="1"||typeof cfg.storeId!=="string"||!uuidPattern.test(cfg.storeId)||
    typeof cfg.nativeRegistrationFile!=="string"||!path.isAbsolute(cfg.nativeRegistrationFile)||typeof cfg.clientPolicyFile!=="string"||!path.isAbsolute(cfg.clientPolicyFile)||
    typeof cfg.enrollmentProof!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(cfg.enrollmentProof)||
    !cfg.schemas||typeof cfg.schemas!=="object"||!Object.keys(cfg.schemas).length||Object.values(cfg.schemas).some(value=>typeof value!=="string"||Buffer.byteLength(value)>512*1024))nativeUnavailable()
  cfg.schemas=await selectedContractSchemas(bootstrap,raw)
  const proof=Buffer.from(cfg.enrollmentProof,"base64url")
  const enrollment=new AutomaticEnrollmentClient(cfg.serviceOrigin,proof,cfg.storeId);proof.fill(0)
  const endpoint=await Service.discover({file:cfg.nativeRegistrationFile,version:"2.2.5"})
  if(!endpoint||endpoint.url!==cfg.nativeOrigin)nativeUnavailable()
  const native=OpenCode.make({baseUrl:endpoint.url,headers:Service.headers(endpoint)})
  const tools=new NativeSessionToolRouter(ctx)
  const deliveryCustody=new LinuxNativeCustody(path.join(root,"approval-delivery"),[ctx.location.directory])
  await deliveryCustody.initialize()
  const approvalDelivery=new ApprovalDelivery(deliveryCustody)
  const displayStorage=new LinuxNativeCustody(path.join(root,"review-display"),[ctx.location.directory])
  await displayStorage.initialize()
  const reviewDisplay={storeId:cfg.storeId as string,custody:new ReviewDisplayCustody(displayStorage)}
  const governor=await registerAutomaticPrimaryGovernor(ctx,bootstrap,enrollment,async binding=>{
    // Diagnostics are not authority or immutable recovery intent. Keep one bounded cursor per
    // session OUTSIDE bootstrap inventory, and never make progress logging a condition of admission.
    const diagnostics=new LinuxNativeCustody(path.join(root,"diagnostics"),[binding.workRoot])
    let diagnosticsReady=false
    try{await diagnostics.initialize();diagnosticsReady=true}catch{/* canonical admission still checks its own custody */}
    const stage=async(name:string)=>{
      if(!diagnosticsReady)return
      try{
        const raw=JSON.stringify({stage:name,pid:process.pid}),old=await diagnostics.read("activation",binding.sessionID)
        if(old===undefined)await diagnostics.retain("activation",binding.sessionID,raw)
        else await diagnostics.replace("activation",binding.sessionID,createHash("sha256").update(old).digest("hex"),raw)
      }catch{/* diagnostic loss neither grants nor revokes canonical/runtime authority */}
    }
    await stage("activation-requested")
    const selected=await enrollment.activateSession(binding)
    await stage("activation-issued")
    if(path.relative(root,selected.root).split(path.sep).includes("..")||await fs.realpath(selected.root)!==selected.root)nativeUnavailable()
    const custody=new LinuxNativeCustody(path.join(selected.root,"native"),[binding.workRoot])
    await custody.initialize()
    await custody.retain("credential",binding.credentialId,JSON.stringify({formatVersion:1,kind:"native-scoped-key-v1",storeId:cfg.storeId,
      projectId:binding.projectId,principalId:binding.principalId,credentialId:binding.credentialId,token:binding.token}))
    const requests=new LinuxWorkRequestCustody(path.join(selected.root,"requests"),[binding.workRoot])
    await stage("credential-retained")
    const service=new NativeRuntimeClient(cfg.serviceOrigin,selected.serviceProof,cfg.storeId,binding.projectId,binding.controllerId,selected.hostId)
    const registry=await createNativeCredentialRegistry(ctx,native,cfg.nativeOrigin,custody,service,cfg.serviceOrigin,requests)
    await stage("host-verified")
    try{await registry.restoreAll()}catch(error){await stage("registry-restore-held");throw error}
    await stage("registry-restored")
    let runtime:Awaited<ReturnType<typeof startProtectedNativeRuntime>>|undefined
    await stage("scoped-context-requested")
    const scoped=tools.context(binding.sessionID,async sessionID=>sessionID===binding.sessionID||registry.bound(sessionID)||runtime?.registry.bound(sessionID)===true)
    await stage("scoped-context-created")
    const deployment=new LinuxNativeCustody(path.join(selected.root,"bootstrap"),[binding.workRoot]);await deployment.initialize()
    const plannerStore=new LinuxNativeCustody(path.join(selected.root,"planner-state"),[binding.workRoot]);await plannerStore.initialize()
    const plannerActivity=new NativePlannerActivity(plannerStore,{storeId:cfg.storeId,projectId:binding.projectId,
      controllerId:binding.controllerId,sessionID:binding.sessionID})
    const executionPolicies=await bootstrap.read("automatic-execution-deployments","current")
    if(executionPolicies!==undefined){
      const policies=closedObject(exactJSON(executionPolicies,nativeUnavailable),["formatVersion","deployments"])
      if(policies.formatVersion?.value!=="1"||!Array.isArray(policies.deployments)||policies.deployments.length>32)nativeUnavailable()
      for(const value of policies.deployments){
        const policy=closedObject(value,["profileRecordJSON","executionJSON","contextPolicyJSON"])
        if(exactJSON(policy.profileRecordJSON,nativeUnavailable).key?.projectId===binding.projectId)
          await provisionExecutionDeployment({profileRecordJSON:policy.profileRecordJSON,executionJSON:policy.executionJSON,
            contextPolicyJSON:policy.contextPolicyJSON,root:selected.root,projectId:binding.projectId,forbiddenAreas:[binding.workRoot]})
      }
    }
    await stage("deployment-initialized")
    const serviceProof=Buffer.from(selected.serviceProof,"base64url"),transportProof=Buffer.from(selected.transportProof,"base64url")
    let primary:Awaited<ReturnType<typeof registerPrimaryGovernor>>|undefined
    try {
      primary=await registerPrimaryGovernor(scoped,registry,deployment,{workRoot:binding.workRoot,controllerId:binding.controllerId,
        controllerCredentialId:binding.credentialId,projectId:binding.projectId,schemas:cfg.schemas,clientPolicyFile:cfg.clientPolicyFile,admissionHooks:false,activationStage:stage,approvalDelivery,
         plannerActivity:()=>plannerActivity,reviewDisplay},async sessionID=>{
        await stage("native-runtime-requested")
        runtime=await startProtectedNativeRuntime(scoped,native,{root:selected.root,nativeOrigin:cfg.nativeOrigin,serviceOrigin:cfg.serviceOrigin,transportOrigin:selected.transportOrigin,
          storeId:cfg.storeId,projectId:binding.projectId,controllerId:binding.controllerId,hostId:selected.hostId,controllerSessionID:sessionID,ownerOrderRoot:root,
          assignmentSessionIDs:[],serviceProof,transportProof,candidateDelivery:await loadCandidateDeliveryBootstrap(deployment,binding.workRoot),activationStage:stage})
        await stage("native-runtime-started")
        const connected=await registry.connectRuntime(sessionID)
        await stage(connected?"service-connected":"service-held")
      })
      await stage("governor-registered")
      await primary.selected(binding.sessionID,true)
      const current=async()=>{await primary!.selected(binding.sessionID)}
      const context=()=>nativeContextAdmission(selected.root,selected.hostId,binding.workRoot)
      return {sessionID:binding.sessionID,projectId:binding.projectId,workRoot:binding.workRoot,role:"controller" as const,current,
        admitPrompt:current,admitContext:current,admitTool:current,
        admitModel:async(event:unknown)=>{await current();if((event as {kind:string}).kind!=="primary")await context().request(event as never)},
        admitGenerate:async(event:unknown)=>{await current();await context().generate(event as never)},
        admitCompaction:async(event:unknown)=>{await current();await context().compaction(event as never)},
        close:async()=>{await primary!.close();await runtime?.close();serviceProof.fill(0);transportProof.fill(0)}}
    }catch(error){
      // Retain code locations only: exception messages/transport payloads may contain credentials.
      const frames=error instanceof Error?error.stack?.split("\n").filter(line=>/^\s+at /.test(line)&&!line.includes("http")).slice(0,12):[]
      await custody.retain("activation-code-locations",binding.sessionID,JSON.stringify({frames}))
      await stage("primary-activation-failed");await primary?.close();await runtime?.close();serviceProof.fill(0);transportProof.fill(0);throw error
    }
  },async sessionID=>{
    const found=nativeRuntimeForSession(sessionID)
    if(!found)return undefined
    const ref=found.runtime,binding=await ref.registry.inspect(sessionID)
    if(binding?.scope!=="assignment")return undefined
    const identity=await ref.registry.sourceIdentity(sessionID)
    const current=async()=>{await ref.registry.sourceIdentity(sessionID)}
    const context=()=>nativeContextAdmission(found.root,ref.hostId,binding.workRoot)
    return {sessionID,projectId:identity.projectId,workRoot:binding.workRoot,role:"assignment" as const,current,
      admitPrompt:async event=>{await ref.admitPrompt(event as never)},admitContext:async event=>{await ref.admitContext(sessionID,event as never)},
      admitTool:async(_sessionID:string,toolName?:string)=>{await ref.admitTool(sessionID,toolName)},admitModel:async event=>{await ref.admitModel(event as never)},
      admitGenerate:async event=>{await current();await ref.admitTool(sessionID);await context().generate(event as never)},
      admitCompaction:async event=>{await current();await ref.admitTool(sessionID);await context().compaction(event as never)},close:async()=>{}} satisfies AutomaticSessionRuntime
  })
  try{
    const admissionDiagnostics=new LinuxNativeCustody(path.join(root,"diagnostics"),[ctx.location.directory])
    let admissionDiagnosticsReady=false
    try{await admissionDiagnostics.initialize();admissionDiagnosticsReady=true}catch{/* not authority */}
    const creation=new NativeSessionCreation(new LinuxNativeCustody(path.join(root,"session-creations"),[ctx.location.directory]),ctx.location.directory,{
      observe:async sessionID=>{
        // Use the generated HTTP error contract here. The in-process domain throws a different
        // Session.NotFoundError; treating arbitrary exceptions as absence would be unsafe.
        try{return await native.session.get({sessionID})}
        catch(error){if(isSessionNotFoundError(error)&&error.sessionID===sessionID)return undefined;throw error}
      },create:sessionID=>ctx.session.create({id:sessionID,location:{directory:ctx.location.directory}}),
    })
    await creation.initialize()
    const sessionInput=(input:unknown)=>{
      if(!input||typeof input!=="object"||Object.keys(input).join(",")!=="sessionID")nativeUnavailable()
      const sessionID=(input as {sessionID:unknown}).sessionID
      if(!userSessionID(sessionID))nativeUnavailable()
      return sessionID
    }
    const admit=async(sessionID:string)=>{
      const runtime=await governor.ensure(sessionID)
      if(runtime.role!=="controller")nativeUnavailable()
      await runtime.current()
      if(admissionDiagnosticsReady)try{
        const raw=JSON.stringify({stage:"controller-admitted",pid:process.pid,observedAt:Date.now()}),
          old=await admissionDiagnostics.read("ui-admission",sessionID)
        if(old===undefined)await admissionDiagnostics.retain("ui-admission",sessionID,raw)
        else await admissionDiagnostics.replace("ui-admission",sessionID,createHash("sha256").update(old).digest("hex"),raw)
      }catch{/* observational cursor loss never grants or revokes authority */}
      return {kind:"governed-planning",role:"controller",sessionID:runtime.sessionID,projectId:runtime.projectId}
    }
    const admission=await ctx.rpc.register(ClientAdmission,{
      admitController:async(input:unknown)=>admit(sessionInput(input)),
      createController:async(input:unknown)=>{
        const sessionID=sessionInput(input)
        await creation.create(sessionID)
        return admit(sessionID)
      },
    })
    // A native session may have been created before the original UI saw its reply/event. Restore
    // only exact retained chat requests, never arbitrary native history or a last-used directory.
    const recovering=creation.recover().then(sessions=>Promise.allSettled(sessions.map(session=>governor.ensure(session.id))))
    void recovering.catch(()=>{/* actual admission remains guarded by exact native/current checks */})
    return async()=>{await admission.dispose();await creation.close();await recovering.catch(()=>{});await governor.close();tools.close()}
  }catch(error){await governor.close();tools.close();throw error}
}

/** Real server entrypoint. Enabling the package without a trusted protected bootstrap refuses;
 * there is no GUI-only success followed by ordinary unrestricted coding. No ctx.options token,
 * discovery of the user's existing service, owner-profile import or synthetic ready task. */
export default Plugin.define({id:"writhelm-opencode",async setup(ctx){
  const root=process.env.WRITHELM_PRIMARY_ROOT
  if(process.platform!=="linux"||ctx.app.version!=="2.2.5"||!root||!path.isAbsolute(root)||await fs.realpath(root)!==root)nativeUnavailable()
  const workRoot=await fs.realpath(ctx.location.directory)
  const bootstrap=new LinuxNativeCustody(path.join(root,"bootstrap"),[workRoot])
  await bootstrap.initialize()
  const automatic=await bootstrap.read("automatic-bootstrap","current")
  if(automatic!==undefined)return startAutomaticPrimaryServer(ctx,root,bootstrap,automatic)
  const raw=await bootstrap.read("primary-bootstrap","current")
  if(!raw)nativeUnavailable()
  const cfg=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","storeId","projectId","controllerId","hostId","controllerCredentialId",
    "workRoot","serviceOrigin","nativeOrigin","nativeRegistrationFile","transportOrigin","serviceProof","transportProof","schemas"])
  if(cfg.formatVersion?.value!=="1"||![cfg.storeId,cfg.projectId,cfg.controllerId,cfg.hostId,cfg.controllerCredentialId].every(v=>typeof v==="string"&&uuidPattern.test(v)))nativeUnavailable()
  const context=()=>nativeContextAdmission(root,cfg.hostId,workRoot)
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
      const runtime=await receiver(event.sessionID)
      if(event.kind!=="primary")await context().request(event)
      else await runtime.admitModel(event)
    }})
    await ctx.session.hook("title",async event=>{if(await belongs(event.sessionID))event.result="Writhelm — bounded session"})
    await ctx.session.hook("generate",async event=>{if(await belongs(event.sessionID)){await receiver(event.sessionID);await context().generate(event)}})
    await ctx.session.hook("compaction",async event=>{if(await belongs(event.sessionID)){await receiver(event.sessionID);await context().compaction(event)}})
    await ctx.tool.hook("execute.before",async event=>{if(await belongs(event.sessionID)){const runtime=await receiver(event.sessionID);await runtime.admitTool(event.sessionID,event.tool)}})
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
  const plannerStore=new LinuxNativeCustody(path.join(root,"planner-state"),[workRoot]);await plannerStore.initialize()
  const governor=await registerPrimaryGovernor(ctx,registry,bootstrap,{workRoot,controllerId:cfg.controllerId,
    controllerCredentialId:cfg.controllerCredentialId,projectId:cfg.projectId,schemas:cfg.schemas,
    plannerActivity:sessionID=>new NativePlannerActivity(plannerStore,{storeId:cfg.storeId,projectId:cfg.projectId,
      controllerId:cfg.controllerId,sessionID})},async sessionID=>{
    const candidateDelivery=await loadCandidateDeliveryBootstrap(bootstrap,workRoot)
    runtime=await startProtectedNativeRuntime(ctx,native,{root,nativeOrigin:cfg.nativeOrigin,serviceOrigin:cfg.serviceOrigin,transportOrigin:cfg.transportOrigin,
      storeId:cfg.storeId,projectId:cfg.projectId,controllerId:cfg.controllerId,hostId:cfg.hostId,
      controllerSessionID:sessionID,assignmentSessionIDs:[],serviceProof,transportProof,candidateDelivery})
    await registry.connectRuntime(sessionID)
  },context)
  return async()=>{await governor.close();await runtime?.close();serviceProof.fill(0);transportProof.fill(0)}
}})
