// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import * as fs from "node:fs/promises"
import type { Plugin } from "@opencode/plugin"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { planningKnowledgeCommands, planningKnowledgeReads } from "./connected-tools.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable, uuidPattern, closedObject } from "./native-runtime-client.js"
import { primaryPermissionRules, requirePrimaryPolicy, requirePrimaryPolicyFile } from "./primary-policy.js"
import type { NativeContextAdmission } from "./native-context-reference.js"
import type { AutomaticEnrollmentClient, AutomaticSessionEnrollment } from "./primary-bootstrap.js"

export const primaryTools = new Set(["question", "read", "writhelm_work_command", "writhelm_work_read", "writhelm_knowledge_command", "writhelm_knowledge_read", "writhelm_schema", "writhelm_prepare_worker", "writhelm_start_worker", "writhelm_activate_dispatch", "writhelm_activate_candidate", "writhelm_start_candidate_review", "writhelm_candidate_review_report", "writhelm_project_checks"])
export const primaryInstructions = `You are the Writhelm primary PLANNER, not a coding executor.
The owner's ordinary message is the objective, not an executable implementation contract. First read applicable canonical knowledge and existing tasks. Clarify consequential missing requirements, intended users, scope, constraints and acceptance; do not invent a stack or prototype merely to start coding. Ask only questions that materially affect the work.
New product/design meaning requires an exact canonical proposal and structured owner approval through the Writhelm native review panel. Explain the decision and surface its exact review reference. Chat assent, model output, a ready flag or a passing test is NOT that approval. Do not ask the owner to approve every ordinary technical step or delegation.
After confirming an approval request and its exact request/packet digests, surface its native reference as standalone inline code: writhelm-review/v1/<project UUID>/<request UUID>/<request SHA256>/<packet SHA256>. Do not make it an HTTPS/file URL or Markdown link and do not invent missing digests. Wait for the canonical owner resolution, not a chat acknowledgement.
Create separately versioned task contracts through the service: objective, exact inputs/approved design, read/write/decision scope, constraints, dependencies, acceptance, escalation, expected output and execution/verification profiles. Keep unresolved work draft. Read operation schemas before constructing canonical JSON. Service refusals are blockers, never permission to use shell or edit canonical Git.
Project mutation belongs to exact service-bound execution, never unrestricted editing by this primary session or a generic subagent. A ready task is not itself a native execution permit. Current rights, complete pinned inputs, resource claims and native safety must pass immediately before launch. If runtime qualification is unavailable, say work is held; do not claim implementation has started.
Use writhelm_prepare_worker for the ready-task reservation/passive-receiver/enrollment/bind sequence, not hand-written native IDs or a generic subagent. Its binding-confirmed phase is NOT active execution or evidence that a model started. Retain preparationId and exact selection for recovery. If preparation is held, resolve its actual blocker without inventing a profile qualification or bypassing the service.
After recording a canonical dispatch plan through the service, activate that exact plan with writhelm_activate_dispatch. This is an internal planning step, not a user button or mode switch. The client reconciles the retained plan automatically after restart; activation does not override task, authority or qualification checks. Never invent another plan identity to bypass an uncertain original effect.
Once the service has selected every required implementation result, use writhelm_activate_candidate with the exact dispatch-plan reference to run assembly and its complete mandatory check plan. A waiting result is not completion; unsupported checks hold. Ready-for-review is not semantic review, acceptance or integration. Do not substitute your own review for the separate qualified non-author reviewer.
Use writhelm_project_checks to discover trusted project test/build/lint check IDs. A planned project-check-v1 command has exactly the selected approved ID as its second element, never arbitrary argv. Missing profile/check support holds project verification; a hash check alone is not test/build/lint coverage.
After required candidate checks, the client automatically derives and creates the canonical whole-candidate reviewer task/verification plan/input manifest from the complete selected requirements, prepares a fresh reviewer-v1 delegation, and requests its service ticket/held start. No manual reviewer task or bootstrap is required. Conflicting bases/profiles, oversized obligations or missing qualification hold instead of guessing. The service, not a supplied principal ID, establishes independence. Use writhelm_start_candidate_review only for explicit recovery of an exact existing service-bound reviewer; retry its original identity after uncertainty. The reviewer publishes its canonical verification report through Writhelm and the delivery loop discovers it automatically. A retained ticket or successful start is not a completed review or integration authority.
Only then use writhelm_start_worker with the returned exact assignment selector and binding. It uses a retained normal prompt, not a synthetic continuation. Active admission is not produced/verified/accepted work or writer completion. Keep startId for original-effect reconciliation; a lost reply is never permission to start a second worker.
For an already determined correction you may propose an exact patch artifact with its requirement-linked diagnosis. Application is a bounded deterministic non-LLM operation, not another agent that rederives the patch and not free shell/edit access for this primary. You are an author of the proposed changes even if a separate process writes them; you cannot supply their independent review. Use that route only when an actual authorized operation/schema is available. A pure preparation helper or stored patch body is not an operational applicator: do not invent a tool, fake a mechanical model/session, hide authorship behind the writer, or bypass task/base/claims and new-result verification. Conflicts/mismatches remain explicit; no automatic model fallback.
Default delivery loop: planner -> bounded executor -> planner bearing-point control -> any determined attributed planner patch -> deterministic application and targeted tests -> local commit-candidate. Your bearing-point control is diagnostic/author-side, not independent review. A candidate commit records produced bytes; it is not independently verified, accepted, integrated or published. Do not launch a new LLM applicator or independent reviewer for each small known patch.
Before execution, plan a bounded coherent umbrella task/MR, exact related result/contract coverage, consumer readiness and its independent gate before acceptance/integration. Keep child work produced/checked/committed while that gate is pending; do not mark it accepted or DONE to unlock consumers. A fresh-context non-author reviewer assesses the whole exact selected MR diff against requirements. A groupId or an umbrella report is not automatic evidence for every child gate. Do not postpone, remove or weaken any existing prescribed earlier independent gate without its authorized decision.
After review findings and fixes, retain justified unaffected evidence and assess changed significant inputs/deltas instead of automatically restarting full review or appointing another reviewer. A reviewer need not be replaced merely for reporting findings, but it must remain non-author and any reassessment must bind the new exact subject under the actual assignment/freshness rules. Never silently retarget an immutable review or relabel an old pass for changed bytes. No recursive review-of-reviews ritual.
Track canonical progress/checkpoints/blockers, check consequential review findings and evidence applicability, accept only the exact conforming result after its prescribed gates and integrate only to the authorized target. Do not self-verify, invent evidence or waive gates. Proposed, approved, implemented, verified, accepted and integrated are distinct.
Retain original requests after uncertain replies. Never retry a native/model effect with a new identity. Preserve pending decisions and uncertainty across context replacement. Use native task-shaped compaction/restoration only; generic summary is not authority.
The available Writhelm tools contain no owner credential. Never seek profile.json, provider secrets or privileged filesystem access. Tool restrictions are enforced independently of these instructions.`

export type PrimaryConfiguration = Readonly<{
  workRoot: string; controllerId: string; controllerCredentialId: string; projectId: string
  schemas: Readonly<Record<string,string>>
  clientPolicyFile?:string;admissionHooks?:boolean
  activationStage?:(stage:string)=>Promise<void>
}>

/** Actual ordinary-prompt enrollment + live model/tool/permission admission. This is cooperative
 * harness control, not an OS sandbox. Unknown sessions in the configured project HOLD, not fallback
 * to an unrestricted agent. The primary NEVER receives coding tools, even after a task is ready. */
export async function registerPrimaryGovernor(ctx: Plugin.Context, registry: NativeCredentialRegistry,
  custody: LinuxNativeCustody, config: PrimaryConfiguration, onBound?: (sessionID:string)=>Promise<void>,
  context?:()=>NativeContextAdmission) {
  await config.activationStage?.("primary-validation-requested")
  if(process.platform!=="linux"||ctx.app.version!=="2.2.5"||!path.isAbsolute(config.workRoot)
    ||![config.controllerId,config.controllerCredentialId,config.projectId].every(id=>uuidPattern.test(id)))nativeUnavailable()
  const workRoot=await fs.realpath(config.workRoot)
  if(workRoot!==config.workRoot||ctx.location.directory!==workRoot)nativeUnavailable()
  if(config.clientPolicyFile)await requirePrimaryPolicyFile(config.clientPolicyFile)
  else await requirePrimaryPolicy(workRoot)
  await config.activationStage?.("primary-policy-verified")
  if(!Object.keys(config.schemas).length||Object.values(config.schemas).some(raw=>typeof raw!=="string"||Buffer.byteLength(raw)>512*1024))nativeUnavailable()
  await custody.initialize();await custody.requireOutside(workRoot)
  await config.activationStage?.("primary-custody-verified")
  let closed=false
  let connected:Promise<void>|undefined
  const registrations:Array<{dispose():Promise<void>}>=[]
  const applies=async(sessionID:string)=>registry.bound(sessionID)||(await ctx.session.get({sessionID})).location.directory===workRoot
  const selected=async(sessionID:string,enroll=false)=>{
    if(closed)nativeUnavailable()
    if(enroll)await config.activationStage?.("controller-selection-requested")
    const info=await ctx.session.get({sessionID})
    if(!registry.bound(sessionID))await registry.restore(sessionID)
    const kind=registry.scopeKind(sessionID)
    if(kind==="assignment")return "assignment" as const // existing exact assignment bridge owns admission
    if(info.location.directory!==workRoot||info.revert)nativeUnavailable()
    let raw=await custody.read("primary-controller",config.controllerId)
    if(raw===undefined){
      if(!enroll||info.parentID)nativeUnavailable()
      if((await ctx.session.context({sessionID})).length)throw new Error("Existing unmanaged history cannot silently become an authorized primary; start a fresh governed session")
      // Immutable atomic custody is the single-winner selection; a race never grants two primaries.
      raw=contextJSON({formatVersion:1,sessionID,controllerId:config.controllerId,projectId:config.projectId,workRoot})
      await custody.retain("primary-controller",config.controllerId,raw)
    }
    const pin=exactJSON(raw,nativeUnavailable)
    if(pin.sessionID!==sessionID||pin.controllerId!==config.controllerId||pin.projectId!==config.projectId||pin.workRoot!==workRoot||info.parentID)nativeUnavailable()
    if(!registry.bound(sessionID)){
      if(!enroll)nativeUnavailable()
      await registry.bind(sessionID,config.controllerCredentialId,workRoot)
    }
    if(enroll)await config.activationStage?.("controller-bound")
    const binding=await registry.inspect(sessionID)
    if(binding?.scope!=="controller"||binding.credentialId!==config.controllerCredentialId)nativeUnavailable()
    const current=await registry.sourceIdentity(sessionID)
    if(current.scope!=="controller"||current.projectId!==config.projectId||current.controllerId!==config.controllerId)nativeUnavailable()
    if(enroll)await config.activationStage?.("controller-authority-verified")
    // Persist native deny rules too: changing the visible agent or capturing an old tool snapshot
    // cannot turn the primary into a build executor. Custom Writhelm calls use their own current
    // service policy; no project/OS permission is needed for their fixed protected routes.
    if(JSON.stringify(info.permissions?.slice(-primaryPermissionRules.length))!==JSON.stringify(primaryPermissionRules)){
      const previous=(info.permissions??[]).filter(rule=>!primaryPermissionRules.some(required=>JSON.stringify(required)===JSON.stringify(rule)))
      await ctx.session.update({sessionID,permissions:[...previous,...primaryPermissionRules]})
    }
    if(onBound){connected??=onBound(sessionID);await connected}
    return "controller" as const
  }
  try {
    if(config.admissionHooks!==false){
    registrations.push(await ctx.session.hook("prompt",async event=>{if(await applies(event.sessionID))await selected(event.sessionID,true)}))
    registrations.push(await ctx.session.hook("context",async event=>{
      if(!await applies(event.sessionID)||await selected(event.sessionID)!=="controller")return
      event.system.push({type:"text",text:primaryInstructions+"\nCanonical projectId: "+config.projectId})
      for(const name of Object.keys(event.tools))if(!primaryTools.has(name))delete event.tools[name]
    }))
    registrations.push(await ctx.session.hook("model.request",async event=>{
      if(!await applies(event.sessionID)||await selected(event.sessionID)!=="controller")return
      if(event.kind!=="primary"){
        if(!context)nativeUnavailable()
        await context().request(event)
      }
    }))
    registrations.push(await ctx.session.hook("generate",async event=>{
      if(!await applies(event.sessionID)||await selected(event.sessionID)!=="controller")return
      if(!context)nativeUnavailable()
      await context().generate(event)
    }))
    registrations.push(await ctx.session.hook("compaction",async event=>{
      if(!await applies(event.sessionID)||await selected(event.sessionID)!=="controller")return
      if(!context)throw new Error("Writhelm primary requires confirmed task-shaped controller continuation; generic compaction is held")
      await context().compaction(event)
    }))
    registrations.push(await ctx.session.hook("title",async event=>{
      if(await applies(event.sessionID)&&await selected(event.sessionID)==="controller")event.result="Writhelm — governed planning"
    }))
    registrations.push(await ctx.tool.hook("execute.before",async event=>{
      if(await applies(event.sessionID)&&await selected(event.sessionID)==="controller"&&!primaryTools.has(event.tool))
        throw new Error("Writhelm primary is planning-only; implementation requires a canonical ready task and bound worker")
    }))
    registrations.push(await ctx.permission.hook("evaluate",async event=>{
      if(!await applies(event.sessionID))return
      try {
        if(await selected(event.sessionID)==="assignment")return
        if(event.action==="question"||event.action==="read")return // preserve native read/secret/directory/owner policies
      } catch { /* unknown/unavailable primary never falls through to native execution */ }
      event.effect="deny";event.message="Writhelm primary cannot execute project/OS actions; use the governed task/worker workflow"
    }))
    }
    await config.activationStage?.("tools-transform-requested")
    registrations.push(await ctx.tool.transform(editor=>{
      for(const [name,route,allowed] of [["writhelm_knowledge_command","commands",planningKnowledgeCommands],
        ["writhelm_knowledge_read","reads",planningKnowledgeReads]] as const)editor.add({name,
        description:"Scoped canonical knowledge v1 planning operation. Read writhelm_schema first. Approval is human-only; never send owner/actor/credential fields.",
        input:{type:"object",additionalProperties:false,required:["json"],properties:{json:{type:"string",maxLength:2*1024*1024}}},
        execute:async(input,actual)=>{
          if(await selected(actual.sessionID)!=="controller"||!input||typeof input!=="object"||Object.keys(input).join(",")!=="json")nativeUnavailable()
          const raw=(input as {json:unknown}).json
          if(typeof raw!=="string"||Buffer.byteLength(raw)>2*1024*1024)nativeUnavailable()
          const command=exactJSON(raw,nativeUnavailable)
          if(!allowed.has(command.operation)||command.projectId!==config.projectId)nativeUnavailable()
          const channel=await registry.planningKnowledgeChannel(actual)
          return {content:await channel.send(route,raw,actual.signal)}
        },options:{codemode:false}})
      editor.add({name:"writhelm_schema",description:"Pinned canonical wire-contract sources supplied by the installed package. Read the exact codecs and referenced value contracts; this is not a standalone JSON Schema validator, approval or service state.",
        input:{type:"object",additionalProperties:false,required:["name"],properties:{name:{type:"string",enum:Object.keys(config.schemas)}}},
        execute:async(input,actual)=>{
          if(await selected(actual.sessionID)!=="controller"||!input||typeof input!=="object"||Object.keys(input).join(",")!=="name")nativeUnavailable()
          const name=(input as {name:unknown}).name
          if(typeof name!=="string"||!Object.hasOwn(config.schemas,name))nativeUnavailable()
          return {content:config.schemas[name]!}
        },options:{codemode:false}})
    }))
    await config.activationStage?.("tools-transform-completed")
    await config.activationStage?.("tools-reload-requested")
    await ctx.tool.reload()
    await config.activationStage?.("tools-reload-completed")
    return {selected,async close(){closed=true;for(const r of registrations.reverse())await r.dispose()}}
  } catch(error){closed=true;for(const r of registrations.reverse())await r.dispose();throw error}
}

export type AutomaticSessionRuntime=Readonly<{
  sessionID:string;projectId:string;workRoot:string;role:"controller"|"assignment"
  current():Promise<void>
  admitPrompt(event:unknown):Promise<void>;admitContext(event:unknown):Promise<void>;admitModel(event:unknown):Promise<void>
  admitTool(sessionID:string,toolName?:string):Promise<void>;admitGenerate(event:unknown):Promise<void>;admitCompaction(event:unknown):Promise<void>
  close():Promise<void>
}>

/** Per-location lifecycle admission, with independently activated controllers per ordinary session.
 * The factory must compose real service/native runtime and tools before resolving; enrollment alone
 * is never ACTIVE. Worker resolution precedes planner enrollment. A failed/unknown activation is
 * cached as a hold for this incarnation, not retried with another controller or ordinary agent. */
export async function registerAutomaticPrimaryGovernor(ctx:Plugin.Context,custody:LinuxNativeCustody,
  enrollment:Pick<AutomaticEnrollmentClient,"openSession">,
  activate:(binding:AutomaticSessionEnrollment,signal:AbortSignal)=>Promise<AutomaticSessionRuntime>,
  assignment:(sessionID:string)=>Promise<AutomaticSessionRuntime|undefined>){
  if(process.platform!=="linux"||ctx.app.version!=="2.2.5")nativeUnavailable()
  const location=await fs.realpath(ctx.location.directory)
  await custody.initialize();await custody.requireOutside(location)
  const abort=new AbortController(),registrations:Array<{dispose():Promise<void>}>=[]
  const pending=new Map<string,Promise<AutomaticSessionRuntime>>(),runtimes=new Set<AutomaticSessionRuntime>()
  let closed=false,streamFailed=false
  const belongs=async(sessionID:string)=>(await ctx.session.get({sessionID})).location.directory===ctx.location.directory
  const ensure=async(sessionID:string):Promise<AutomaticSessionRuntime>=>{
    if(closed||streamFailed||!await belongs(sessionID))nativeUnavailable()
    const delegated=await assignment(sessionID)
    if(delegated){
      if(delegated.sessionID!==sessionID||delegated.role!=="assignment")nativeUnavailable()
      await delegated.current();if(closed||streamFailed)nativeUnavailable();return delegated
    }
    let current=pending.get(sessionID)
    if(!current){
      if(pending.size>=256)nativeUnavailable()
      let activationStage="native-session"
      current=(async()=>{
        const info=await ctx.session.get({sessionID})
        if(info.id!==sessionID||info.parentID||info.fork||info.revert)nativeUnavailable()
        const original=contextJSON({formatVersion:1,sessionID,directory:location,nativeProjectId:ctx.location.project.id})
        const retained=await custody.read("automatic-session",sessionID)
        if(retained!==undefined){
          const intent=closedObject(exactJSON(retained,nativeUnavailable),["formatVersion","sessionID","directory","nativeProjectId"])
          // Native discovery changes its project ID when the broker initializes Git. Authority is
          // the broker's canonical project plus the immutable session/physical-directory binding;
          // retain the original discovery ID as history, never rewrite it or use it to mint scope.
          if(intent.formatVersion?.value!=="1"||intent.sessionID!==sessionID||intent.directory!==location||typeof intent.nativeProjectId!=="string")nativeUnavailable()
        }
        if(retained===undefined){
          if((await ctx.session.context({sessionID})).length)throw Error("Existing unmanaged history cannot become a fresh Writhelm planner")
          await custody.retain("automatic-session",sessionID,original)
        }
        activationStage="open-session"
        const binding=await enrollment.openSession(sessionID,abort.signal)
        activationStage="binding-validation"
        if(binding.sessionID!==sessionID||await fs.realpath(binding.workRoot)!==binding.workRoot||
          path.relative(binding.workRoot,location).split(path.sep).includes(".."))nativeUnavailable()
        activationStage="runtime-activation"
        const runtime=await activate(binding,abort.signal)
        runtimes.add(runtime)
        if(closed||runtime.role!=="controller"||runtime.sessionID!==sessionID||runtime.projectId!==binding.projectId||runtime.workRoot!==binding.workRoot)nativeUnavailable()
        const latest=await ctx.session.get({sessionID})
        if(latest.location.directory!==ctx.location.directory||latest.parentID||latest.fork||latest.revert)nativeUnavailable()
        const permissions=latest.permissions??[]
        if(JSON.stringify(permissions.slice(-primaryPermissionRules.length))!==JSON.stringify(primaryPermissionRules))
          await ctx.session.update({sessionID,permissions:[...permissions,...primaryPermissionRules]})
        await runtime.current()
        return runtime
      })()
      pending.set(sessionID,current)
      void current.catch(async(error:unknown)=>{
        // Never retain transport exception text: it may contain credentials or request bodies.
        const status=(error as {enrollmentStatus?:unknown})?.enrollmentStatus
        const name=(error as {name?:unknown})?.name
        try{await custody.retain("activation-hold",sessionID,JSON.stringify({stage:activationStage,
          status:Number.isInteger(status)&&Number(status)>=100&&Number(status)<=599?status:null,
          timeout:name==="TimeoutError",aborted:abort.signal.aborted}))}catch{/* remain held */}
      })
    }
    const runtime=await current
    if(closed)nativeUnavailable()
    const info=await ctx.session.get({sessionID})
    if(info.location.directory!==ctx.location.directory||info.parentID||info.fork||info.revert)nativeUnavailable()
    await runtime.current();if(closed||streamFailed)nativeUnavailable();return runtime
  }
  let stream:Promise<void>|undefined
  try {
    registrations.push(await ctx.session.hook("prompt",async event=>{if(await belongs(event.sessionID))await (await ensure(event.sessionID)).admitPrompt(event)}))
    registrations.push(await ctx.session.hook("context",async event=>{
      if(!await belongs(event.sessionID))return
      const runtime=await ensure(event.sessionID);await runtime.admitContext(event)
      if(runtime.role==="controller"){
        event.system.push({type:"text",text:primaryInstructions+"\nCanonical projectId: "+runtime.projectId})
        for(const name of Object.keys(event.tools))if(!primaryTools.has(name))delete event.tools[name]
      }
    }))
    registrations.push(await ctx.session.hook("model.request",async event=>{if(await belongs(event.sessionID))await (await ensure(event.sessionID)).admitModel(event)}))
    registrations.push(await ctx.session.hook("generate",async event=>{if(await belongs(event.sessionID))await (await ensure(event.sessionID)).admitGenerate(event)}))
    registrations.push(await ctx.session.hook("compaction",async event=>{if(await belongs(event.sessionID))await (await ensure(event.sessionID)).admitCompaction(event)}))
    registrations.push(await ctx.session.hook("title",async event=>{
      if(await belongs(event.sessionID)&& (await ensure(event.sessionID)).role==="controller")event.result="Writhelm — governed planning"
    }))
    registrations.push(await ctx.tool.hook("execute.before",async event=>{
      if(!await belongs(event.sessionID))return
      const runtime=await ensure(event.sessionID)
      if(runtime.role==="controller"&&!primaryTools.has(event.tool))throw Error("Writhelm planner cannot execute project mutations")
      await runtime.admitTool(event.sessionID,event.tool)
    }))
    registrations.push(await ctx.permission.hook("evaluate",async event=>{
      if(!await belongs(event.sessionID))return
      try {
        const runtime=await ensure(event.sessionID);await runtime.admitTool(event.sessionID)
        if(runtime.role==="assignment"||event.action==="question"||event.action==="read")return
      }catch{/* hold unknown/unavailable session instead of ordinary execution */}
      event.effect="deny";event.message="Writhelm session initialization or scoped execution is held"
    }))
    stream=(async()=>{
      try {
        for await(const event of ctx.event.subscribe({types:["session.created"],signal:abort.signal})){
          // The native event bus can include another location's sessions. A scoped SDK lookup
          // may refuse those: that is not loss of the event stream, nor planner admission.
          // Actual prompt/model/tool hooks still perform the strict lookup and hold on errors.
          try{if(await belongs(event.data.sessionID))void ensure(event.data.sessionID).catch(()=>{})}
          catch{/* unavailable/foreign event never enrolls or disables this location's stream */}
        }
        if(!abort.signal.aborted)streamFailed=true
      }catch{if(!abort.signal.aborted)streamFailed=true}
    })()
    // A reopened location has no session.created event. Recover only protected original intents;
    // never enumerate arbitrary native sessions and silently turn them into planners.
    for(const entry of await custody.scan("automatic-session")){
      const intent=exactJSON(entry.raw,nativeUnavailable)
      if(typeof intent.sessionID!=="string"||typeof intent.directory!=="string")nativeUnavailable()
      if(await custody.read("automatic-session",intent.sessionID)!==entry.raw)nativeUnavailable()
      if(intent.directory===location)void ensure(intent.sessionID).catch(()=>{})
    }
    return {ensure,get held(){return closed||streamFailed},async close(){
      closed=true;abort.abort()
      for(const registration of registrations.reverse())await registration.dispose()
      await stream;await Promise.allSettled(pending.values())
      await Promise.all([...runtimes].map(runtime=>runtime.close()))
    }}
  }catch(error){closed=true;abort.abort();for(const registration of registrations.reverse())await registration.dispose();throw error}
}
