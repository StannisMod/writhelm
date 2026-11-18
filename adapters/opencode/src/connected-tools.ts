// SPDX-License-Identifier: AGPL-3.0-only
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import { connectedCommands, connectedReads } from "./connected-catalogue.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import * as fs from "node:fs/promises"
import { ModelReadFault, modelReadInput, readInput, type ModelReads } from "./model-read.js"
import { OwnerOrderHold } from "./native-owner-order.js"
import {WorkModelContracts,prepareTypedWork} from "./model-work.js"
import {ModelContractFault} from "./model-contracts.js"
const invalid = (): never => { throw new Error("invalid exact work JSON/origin") }
const exact = (raw: string) => exactJSON(raw, invalid)

export interface WorkRequestCustody {
  retain(bindingIdentity: string, requestKey: string, raw: string): Promise<string>
  retainWithStatus?(bindingIdentity:string,requestKey:string,raw:string):Promise<{raw:string;fresh:boolean}>
}
/** Operational original-command custody, not canonical work/authority. Explicit Linux adapter;
 * never installs a profile or selects a default directory. Raw bearer secrets are not stored. */
export class LinuxWorkRequestCustody implements WorkRequestCustody {
  private readonly storage: LinuxNativeCustody
  constructor(private readonly root: string, forbiddenWorkAreas: readonly string[]) {
    this.storage = new LinuxNativeCustody(root, forbiddenWorkAreas, 256)
  }
  async requireOutside(work: string): Promise<void> { await this.storage.requireOutside(work) }
  async retain(bindingIdentity: string, requestKey: string, raw: string): Promise<string> {
    return (await this.retainWithStatus(bindingIdentity,requestKey,raw)).raw
  }
  async retainWithStatus(bindingIdentity:string,requestKey:string,raw:string):Promise<{raw:string;fresh:boolean}> {
    if (!/^[0-9a-f-]{36}$/.test(requestKey)) throw new Error("unqualified request custody")
    await this.storage.initialize()
    // Do not interpret an older retained layout as an empty new store and dispatch again.
    // No implicit custody migration or deletion of unresolved original requests.
    if ((await fs.readdir(this.root)).some(name => !/^work-request-[0-9a-f]{64}\.json$/.test(name)))
      throw new Error("unrecognized or unresolved request custody layout")
    // Private operational format only. Retain exact command bytes under an unambiguous scoped
    // identity; shared storage enforces ancestor/owner/link rules before any publication.
    const fresh=await this.storage.retain("work-request", JSON.stringify([bindingIdentity, requestKey]), raw)
    return {raw,fresh}
  }
}

/** Implemented by protected server-controller enrollment/binding custody. Resolution uses actual
 * ToolContext+trusted host incarnation, never a model argument/session label or GUI-owner profile.
 * Only verified controller or registered assignment credentials may enter this interface. */
export interface BoundWorkChannel {
  readonly projectID: string
  readonly scope: "controller" | "assignment"
  /** Nonsecret exact chat/assignment binding, produced by the verified registry, not tool input. */
  readonly readBinding?:string
  send(route: "commands" | "reads", rawJSON: string, signal: AbortSignal, beforeDispatch?:(raw:string,fresh:boolean)=>Promise<void>): Promise<string>
}
export interface NativeWorkCredentialResolver {
  resolve(actual: Pick<ToolContext, "sessionID" | "messageID" | "id" | "signal">): Promise<BoundWorkChannel | undefined>
}

/** Concrete bounded raw-byte HTTP channel. Credentials live in the closure, never tools, metadata,
 * replies or logs. Called only with the verified bounded credential from the protected resolver.
 * No default origin/key, credential transport field, arbitrary URL or owner forwarding route. */
export function boundedWorkChannel(projectID: string, scope: "controller" | "assignment", origin: string, token: string,
  maxReplyBytes: number, custody: WorkRequestCustody, bindingIdentity: string, fetcher: typeof fetch = fetch): BoundWorkChannel {
  return boundedChannel(projectID,scope,origin,token,maxReplyBytes,custody,bindingIdentity,connectedCommands,connectedReads,"3",fetcher)
}

export const planningKnowledgeCommands = new Set(["propose_revision", "request_approval", "record_assessment", "pin_consumer"])
export const planningKnowledgeReads = new Set(["list_projects", "list_documents", "list_revisions", "list_requests", "list_manifests",
  "read_revision", "read_clause", "read_context", "read_approval_trace", "read_request", "read_resolution", "read_manifest", "read_review_bundle"])
/** Same protected controller identity, existing knowledge v1 operations ONLY. Neither resolve_approval
 * nor credential/grant administration can pass this channel, even if the model names them. */
export function boundedPlanningKnowledgeChannel(projectID: string, origin: string, token: string,
  custody: WorkRequestCustody,bindingIdentity: string,fetcher: typeof fetch=fetch): BoundWorkChannel {
  return boundedChannel(projectID,"controller",origin,token,4*1024*1024,custody,bindingIdentity,
    planningKnowledgeCommands,planningKnowledgeReads,"1",fetcher)
}

/** Trusted preparation orchestration only, never registered as a generic model auth tool. This
 * one existing bounded enrollment cannot replace grants, approve norms or administer owners. */
export function boundedDelegationEnrollmentChannel(projectID:string,origin:string,token:string,custody:WorkRequestCustody,
  bindingIdentity:string,fetcher:typeof fetch=fetch):BoundWorkChannel {
  return boundedChannel(projectID,"controller",origin,token,4*1024*1024,custody,bindingIdentity,
    new Set(["register_delegation"]),new Set(),"3",fetcher)
}
/** Private controller-owned plan attempt binding; never exposed as a generic model tool. */
export function boundedDispatchAttemptChannel(projectID:string,origin:string,token:string,custody:WorkRequestCustody,
  bindingIdentity:string,fetcher:typeof fetch=fetch):BoundWorkChannel {
  return boundedChannel(projectID,"controller",origin,token,4*1024*1024,custody,bindingIdentity,
    new Set(["bind_dispatch_attempt"]),new Set(),"3",fetcher)
}
/** Internal controller-only dependency materialization and declared-input closure; not a model tool. */
export function boundedDispatchMaterializationChannel(projectID:string,origin:string,token:string,custody:WorkRequestCustody,
  bindingIdentity:string,fetcher:typeof fetch=fetch):BoundWorkChannel {
  return boundedChannel(projectID,"controller",origin,token,4*1024*1024,custody,bindingIdentity,
    new Set(["materialize_dispatch_dependency","close_dispatch_inputs"]),new Set(),"3",fetcher)
}

function boundedChannel(projectID: string, scope: "controller" | "assignment", origin: string, token: string,
  maxReplyBytes: number, custody: WorkRequestCustody, bindingIdentity: string, commands: ReadonlySet<string>,reads: ReadonlySet<string>,
  commandVersion: string,fetcher: typeof fetch): BoundWorkChannel {
  exactLoopback(origin, invalid)
  if (!["controller", "assignment"].includes(scope) || !token.startsWith("wh1.") || maxReplyBytes < 1024 || maxReplyBytes > 4 * 1024 * 1024)
    throw new Error("unqualified bounded work channel")
  return Object.freeze({ projectID, scope, async send(route: "commands" | "reads", rawJSON: string, signal: AbortSignal,beforeDispatch?:(raw:string,fresh:boolean)=>Promise<void>) {
    if (signal.aborted) throw new Error("work request cancelled before dispatch")
    const parsed = exact(rawJSON)
    const scopedProjects=route==="reads"&&commandVersion==="1"&&parsed.operation==="list_projects"&&parsed.projectId===null
    if (parsed.projectId !== projectID&&!scopedProjects || !(route === "commands" ? commands : reads).has(parsed.operation)) throw new Error("work channel scope mismatch")
    if (route === "commands") {
      if (!isLosslessNumber(parsed.formatVersion) || parsed.formatVersion.value !== commandVersion || typeof parsed.requestKey !== "string") throw new Error("closed command version required")
      if(beforeDispatch){
        if(!custody.retainWithStatus)throw new Error("original custody status unavailable")
        const retained=await custody.retainWithStatus(bindingIdentity+":"+parsed.operation,parsed.requestKey,rawJSON)
        rawJSON=retained.raw
        await beforeDispatch(rawJSON,retained.fresh)
      }else rawJSON = await custody.retain(bindingIdentity + ":" + parsed.operation, parsed.requestKey, rawJSON)
    }
    const response = await fetcher(origin + "/api/v1/" + route, { method: "POST", redirect: "error", credentials: "omit",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token }, body: rawJSON,
      signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]) })
    if (!response.body) throw new Error("work service unavailable")
    const reader = response.body.getReader(), parts: Uint8Array[] = []; let bytes = 0
    try {
      while (true) {
        const chunk = await reader.read(); if (chunk.done) break
        bytes += chunk.value.byteLength
        if (bytes > maxReplyBytes) { await reader.cancel(); throw new Error("work response exceeds selected native reserve") }
        parts.push(chunk.value)
      }
    } finally { reader.releaseLock() }
    const raw = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(Buffer.concat(parts))
    exact(raw) // reject malformed/duplicate/unsafe tokens, but return ORIGINAL bytes, not parsed numbers
    return raw
  } })
}

/** Source-qualified actual native work tools. Catalog is convenience only: service current-H policy
 * still denies captured stale executables/credentials. No extra MCP or owner-key tool is registered. */
export async function registerConnectedWorkTools(ctx: Plugin.Context, resolver: NativeWorkCredentialResolver,
  beforeResult?:(raw:string,fresh:boolean,actual:ToolContext)=>Promise<void>,modelReads?:ModelReads,
  beforeLaunch?:(raw:string,actual:ToolContext)=>Promise<void>,
  afterCommand?:(raw:string,reply:string,actual:ToolContext)=>Promise<void>) {
  const contracts=new WorkModelContracts()
  const readSchema=(sessionID?:string)=>{
    const {$defs,...shape}=contracts.input("reads",sessionID)
    return {type:"object",...($defs?{$defs}:{}),anyOf:[shape,
      {type:"object",additionalProperties:false,required:["reference"],properties:{
        reference:{type:"string",pattern:"^wr1_[A-Za-z0-9_-]{24}$"},format:{type:"string",enum:["readable","canonical"]}}}]}
  }
  const registration = await ctx.tool.transform(editor => {
    editor.add({name:"writhelm_work_contract",
      description:"Compact operation/definition contract. Select kind commands/reads and operation to load that exact typed input into this chat's next work tool. No source-code or external-file reads. Int64 fields use exact decimal strings; service authority remains independent.",
      input:{type:"object",additionalProperties:false,required:["kind"],properties:{kind:{type:"string",enum:["commands","reads"]},
        operation:{type:"string",maxLength:128},definition:{type:"string",maxLength:256}}},
      execute:async(input,actual)=>{
        const channel=await resolver.resolve(actual);if(!channel)throw new Error("Unbound work contract request")
        return {content:contracts.describe(actual.sessionID,input)}
      },options:{codemode:false}})
    for (const [name, route, allowed, version, cap] of [
      ["writhelm_work_command", "commands", connectedCommands, "3", 2 * 1024 * 1024],
      ["writhelm_work_read", "reads", connectedReads, "2", 16384],
    ] as const) editor.add({ name, description: route==="reads"
       ?"Scoped typed work read: operation/subject, or exact wr1_ reference. Load the operation's typed input with writhelm_work_contract(kind='reads',operation=...). Readable result by default; never refresh stale references or copy Int64 counters through JavaScript numbers. work-read2 remains the wire version."
       :"Scoped typed work command: operation/requestKey/payload. First select the operation with writhelm_work_contract(kind='commands',operation=...) to load its exact typed fields. Int64 values are decimal strings; adapter injects project/version and preserves original request bytes. No source-code reading required.",
       input: route==="reads"?readSchema():contracts.input("commands"),
      execute: async (input: unknown, actual: ToolContext) => {
        try {
          if(route==="reads"&&modelReads){
            const channel=await resolver.resolve(actual)
            if(!channel||!["controller","assignment"].includes(channel.scope))throw new Error("unbound work read")
             const typed=input as any
             const prepared=typed?.operation!==undefined?{json:prepareTypedWork("reads",typed,channel.projectID),...(typed.format?{format:typed.format}:{})}:input
             return await modelReads.read("work",channel,actual.sessionID,prepared,allowed,actual.signal)
          }
           if(route==="reads"&&(!input||typeof input!=="object"||(input as {format?:string}).format!=="canonical"))
            throw new ModelReadFault("READ_REFERENCE_UNAVAILABLE")
          // Explicit low-level canonical mode remains available to transport-only integrations
          // without a projection store. Production composition always supplies modelReads.
           if(route==="reads"){
             if((input as any).operation!==undefined){
               const channel=await resolver.resolve(actual);if(!channel)throw new Error("Unbound work read")
               input={json:prepareTypedWork("reads",input,channel.projectID),format:"canonical"}
             }
            const selected=readInput(input)
            if(!selected.json)throw new ModelReadFault("READ_REFERENCE_UNAVAILABLE")
            input={json:selected.json}
           }
           if(route==="commands"&&(input as any)?.operation!==undefined){
             const channel=await resolver.resolve(actual);if(!channel)throw new Error("Unbound work command")
             input={json:prepareTypedWork("commands",input,channel.projectID)}
           }
          if (!input || typeof input !== "object" || Array.isArray(input) || Object.keys(input).join(",") !== "json"
            || typeof (input as { json?: unknown }).json !== "string") throw new Error("closed tool input required")
          const raw = (input as { json: string }).json
          if (!raw.isWellFormed() || Buffer.byteLength(raw) > cap) throw new Error("work admission limit")
          let channel = route==="reads"?await resolver.resolve(actual):undefined
          if (route==="reads"&&(!channel || !["controller", "assignment"].includes(channel.scope)))throw new Error("unbound native work scope")
          let parsed:any
          try{parsed=exact(raw)}catch{
            if(route==="reads")return {content:'{"kind":"error","code":"INVALID_WORK_READ_INPUT","schema":"work-read2"}'}
            throw new Error("invalid work command")
          }
          if (!parsed||typeof parsed!=="object"||Array.isArray(parsed)||!isLosslessNumber(parsed.formatVersion) || parsed.formatVersion.value !== version || !allowed.has(parsed.operation)||
            route==="reads"&&(!parsed.subject||typeof parsed.subject!=="object"||Array.isArray(parsed.subject)))
            if(route==="reads")return {content:'{"kind":"error","code":"INVALID_WORK_READ_INPUT","schema":"work-read2","hint":"Read work-read2. Supply formatVersion:2, projectId, operation and subject; list_work_records is the listing operation. This is a request-format error, not evidence that the service is unavailable."}'}
            else throw new Error("unsupported work operation")
          channel??=await resolver.resolve(actual)
          if (!channel || !["controller","assignment"].includes(channel.scope)||parsed.projectId !== channel.projectID)
            throw new Error("unbound/stale native work scope")
           let original=raw
           const before=route==="commands"&&(beforeResult&&parsed.operation==="submit_result"||beforeLaunch)
             ? async(retained:string,fresh:boolean)=>{
               original=retained
               if(parsed.operation==="submit_result")await beforeResult?.(retained,fresh,actual)
               await beforeLaunch?.(retained,actual)
             } : undefined
           const reply = await channel.send(route, raw, actual.signal,before)
           if(route==="commands")await afterCommand?.(original,reply,actual)
          return { content: reply }
         } catch(error) {
           if(error instanceof OwnerOrderHold)return {content:JSON.stringify({kind:"error",code:error.code,
             hint:"Launch is held for the exact native owner order. Do not bypass with another command, key, chat or latest version."})}
           if(error instanceof ModelReadFault)return {content:`Writhelm read held: ${error.code}\nNo source content returned; no reference was refreshed.`}
           if(error instanceof ModelContractFault)return {content:error.message}
          return { content: '{"kind":"error","code":"WORK_UNAVAILABLE_OR_FORBIDDEN"}' }
        } // no secret-bearing exception text
      }, options: { codemode: false } })
  })
  // Model-facing specialization is per exact chat, never a shared tool mutation. Captured executors
  // still validate the actual supplied operation and re-resolve current scope on every invocation.
  const specialization=ctx.session?.hook?await ctx.session.hook("context",event=>{
    if(event.tools.writhelm_work_command)event.tools.writhelm_work_command.input=contracts.input("commands",event.sessionID)
    if(event.tools.writhelm_work_read)event.tools.writhelm_work_read.input=readSchema(event.sessionID)
  }):undefined
  await ctx.tool.reload()
   return {async dispose(){await specialization?.dispose();await registration.dispose()}}
}
