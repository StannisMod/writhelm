// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import * as fs from "node:fs/promises"
import type { Plugin } from "@opencode/plugin"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { planningKnowledgeCommands, planningKnowledgeReads } from "./connected-tools.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { primaryPermissionRules, requirePrimaryPolicy } from "./primary-policy.js"

export const primaryTools = new Set(["question", "read", "writhelm_work_command", "writhelm_work_read", "writhelm_knowledge_command", "writhelm_knowledge_read", "writhelm_schema", "writhelm_prepare_worker", "writhelm_start_worker"])
export const primaryInstructions = `You are the Writhelm primary PLANNER, not a coding executor.
The owner's ordinary message is the objective, not an executable implementation contract. First read applicable canonical knowledge and existing tasks. Clarify consequential missing requirements, intended users, scope, constraints and acceptance; do not invent a stack or prototype merely to start coding. Ask only questions that materially affect the work.
New product/design meaning requires an exact canonical proposal and structured owner approval through the Writhelm native review panel. Explain the decision and surface its exact review reference. Chat assent, model output, a ready flag or a passing test is NOT that approval. Do not ask the owner to approve every ordinary technical step or delegation.
After confirming an approval request and its exact request/packet digests, surface its native reference as standalone inline code: writhelm-review/v1/<project UUID>/<request UUID>/<request SHA256>/<packet SHA256>. Do not make it an HTTPS/file URL or Markdown link and do not invent missing digests. Wait for the canonical owner resolution, not a chat acknowledgement.
Create separately versioned task contracts through the service: objective, exact inputs/approved design, read/write/decision scope, constraints, dependencies, acceptance, escalation, expected output and execution/verification profiles. Keep unresolved work draft. Read operation schemas before constructing canonical JSON. Service refusals are blockers, never permission to use shell or edit canonical Git.
Implementation belongs to exact service-bound worker assignments, never this primary session or a generic subagent. A ready task is not itself a native execution permit. Current rights, complete pinned inputs, resource claims and native safety must pass immediately before launch. If runtime qualification is unavailable, say work is held; do not claim implementation has started.
Use writhelm_prepare_worker for the ready-task reservation/passive-receiver/enrollment/bind sequence, not hand-written native IDs or a generic subagent. Its binding-confirmed phase is NOT active execution or evidence that a model started. Retain preparationId and exact selection for recovery. If preparation is held, resolve its actual blocker without inventing a profile qualification or bypassing the service.
Only then use writhelm_start_worker with the returned exact assignment selector and binding. It uses a retained normal prompt, not a synthetic continuation. Active admission is not produced/verified/accepted work or writer completion. Keep startId for original-effect reconciliation; a lost reply is never permission to start a second worker.
Track canonical progress/checkpoints/blockers, obtain fresh-context non-author review of exact results, check findings, accept the exact conforming result and integrate only to the authorized target. Do not self-verify, invent evidence or waive gates. Proposed, approved, implemented, verified, accepted and integrated are distinct.
Retain original requests after uncertain replies. Never retry a native/model effect with a new identity. Preserve pending decisions and uncertainty across context replacement. Use native task-shaped compaction/restoration only; generic summary is not authority.
The available Writhelm tools contain no owner credential. Never seek profile.json, provider secrets or privileged filesystem access. Tool restrictions are enforced independently of these instructions.`

export type PrimaryConfiguration = Readonly<{
  workRoot: string; controllerId: string; controllerCredentialId: string; projectId: string
  schemas: Readonly<Record<string,string>>
}>

/** Actual ordinary-prompt enrollment + live model/tool/permission admission. This is cooperative
 * harness control, not an OS sandbox. Unknown sessions in the configured project HOLD, not fallback
 * to an unrestricted agent. The primary NEVER receives coding tools, even after a task is ready. */
export async function registerPrimaryGovernor(ctx: Plugin.Context, registry: NativeCredentialRegistry,
  custody: LinuxNativeCustody, config: PrimaryConfiguration, onBound?: (sessionID:string)=>Promise<void>) {
  if(process.platform!=="linux"||ctx.app.version!=="2.2.5"||!path.isAbsolute(config.workRoot)
    ||![config.controllerId,config.controllerCredentialId,config.projectId].every(id=>uuidPattern.test(id)))nativeUnavailable()
  const workRoot=await fs.realpath(config.workRoot)
  if(workRoot!==config.workRoot||ctx.location.directory!==workRoot)nativeUnavailable()
  await requirePrimaryPolicy(workRoot)
  if(!Object.keys(config.schemas).length||Object.values(config.schemas).some(raw=>typeof raw!=="string"||Buffer.byteLength(raw)>512*1024))nativeUnavailable()
  await custody.initialize();await custody.requireOutside(workRoot)
  let closed=false
  let connected:Promise<void>|undefined
  const registrations:Array<{dispose():Promise<void>}>=[]
  const applies=async(sessionID:string)=>registry.bound(sessionID)||(await ctx.session.get({sessionID})).location.directory===workRoot
  const selected=async(sessionID:string,enroll=false)=>{
    if(closed)nativeUnavailable()
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
    const binding=await registry.inspect(sessionID)
    if(binding?.scope!=="controller"||binding.credentialId!==config.controllerCredentialId)nativeUnavailable()
    const current=await registry.sourceIdentity(sessionID)
    if(current.scope!=="controller"||current.projectId!==config.projectId||current.controllerId!==config.controllerId)nativeUnavailable()
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
    registrations.push(await ctx.session.hook("prompt",async event=>{if(await applies(event.sessionID))await selected(event.sessionID,true)}))
    registrations.push(await ctx.session.hook("context",async event=>{
      if(!await applies(event.sessionID)||await selected(event.sessionID)!=="controller")return
      event.system.push({type:"text",text:primaryInstructions+"\nCanonical projectId: "+config.projectId})
      for(const name of Object.keys(event.tools))if(!primaryTools.has(name))delete event.tools[name]
    }))
    registrations.push(await ctx.session.hook("model.request",async event=>{if(await applies(event.sessionID))await selected(event.sessionID)}))
    registrations.push(await ctx.session.hook("compaction",async event=>{
      if(await applies(event.sessionID)&&await selected(event.sessionID)==="controller"&&event.result===undefined)
        throw new Error("Writhelm primary requires confirmed task-shaped controller continuation; generic compaction is held")
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
      editor.add({name:"writhelm_schema",description:"Exact canonical operation schemas supplied by trusted bootstrap; not approval or service state.",
        input:{type:"object",additionalProperties:false,required:["name"],properties:{name:{type:"string",enum:Object.keys(config.schemas)}}},
        execute:async(input,actual)=>{
          if(await selected(actual.sessionID)!=="controller"||!input||typeof input!=="object"||Object.keys(input).join(",")!=="name")nativeUnavailable()
          const name=(input as {name:unknown}).name
          if(typeof name!=="string"||!Object.hasOwn(config.schemas,name))nativeUnavailable()
          return {content:config.schemas[name]!}
        },options:{codemode:false}})
    }))
    await ctx.tool.reload()
    return {selected,async close(){closed=true;for(const r of registrations.reverse())await r.dispose()}}
  } catch(error){closed=true;for(const r of registrations.reverse())await r.dispose();throw error}
}
