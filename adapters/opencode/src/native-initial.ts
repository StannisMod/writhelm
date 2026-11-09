// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import type { OpenCodeClient } from "@opencode/client"
import type { SessionContext, SessionPrompt, SessionModelRequest } from "@opencode/plugin/promise/session"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern, type NativeSubject } from "./native-runtime-client.js"

const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
type Original={raw:string;digest:string;packet:string;profileJSON:string;deploymentJSON:string;epoch:string}
type Gate={original:Original;envelope:any;closed:boolean;wait:Promise<void>;resolve:()=>void;reject:(e:Error)=>void;
  held:Promise<void>;heldResolve:()=>void;heldReject:(e:Error)=>void}
type Native=Pick<OpenCodeClient,"session">

/** Distinct initial NORMAL prompt protocol. It never fabricates a continuation or treats an inbox
 * match/ack/native idle as completed work. Canonical ACTIVE plus the observed held boundary opens
 * admission; every later use still checks current bindings/mandatory packet/service policy. */
export class NativeInitialAdapter {
  private readonly gates=new Map<string,Gate>()
  private closed=false
  constructor(private readonly ctx:Plugin.Context,private readonly native:Native,private readonly registry:NativeCredentialRegistry,
    private readonly custody:LinuxNativeCustody,private readonly deployment:LinuxNativeCustody,private readonly controllerSessionID:string,
    private readonly connected:(sessionID:string)=>boolean=()=>false,
    private readonly auxiliary?:(event:SessionModelRequest)=>Promise<void>,
    private readonly contextPolicy?:(sessionID:string,profileRecordJSON:string)=>Promise<void>){}
  managed(sessionID:string){return this.gates.has(sessionID)}
  /** Same-incarnation plugin reload only. Rebuild closed local gates from protected ORIGINAL
   * records, then re-enter the existing canonical ACTIVE release protocol. Never dispatch a prompt
   * or infer a new host incarnation/adoption from retained metadata, native idle or inbox presence. */
  async restore():Promise<ReadonlyArray<Readonly<{sessionID:string;intentId:string;phase:"active-admission-restored"|"initial-held"}>>> {
    if(this.closed)nativeUnavailable()
    const restored:Array<{sessionID:string;intentId:string;phase:"active-admission-restored"|"initial-held"}>=[]
    const originals=await this.custody.scan("initial-original")
    if(originals.length>256)nativeUnavailable()
    const sessions=new Set<string>()
    for(const row of originals){
      const original=closedObject(parse(row.raw),["raw","digest","packet","profileJSON","deploymentJSON","epoch"]),e=parse(original.raw)
      if(!uuidPattern.test(e.intentId)||row.keyDigest!==hash(e.intentId)||hash(original.raw)!==original.digest||
        typeof e.nativeSessionId!=="string"||sessions.has(e.nativeSessionId))nativeUnavailable()
      sessions.add(e.nativeSessionId)
      // Validate the immutable original/held witness even when the current receiver cannot be
      // adopted. Malformed protected records are corruption, never a reason to forget a session.
      await this.witness(e.intentId)
      const binding=await this.registry.inspect(e.nativeSessionId)
      let phase:"active-admission-restored"|"initial-held"="initial-held"
      if(binding?.scope==="assignment"&&binding.assignmentId===e.assignmentId&&binding.executionGeneration===e.executionGeneration&&
        binding.bindingJSON===contextJSON(e.binding)&&binding.epoch===original.epoch){
        try{
          const gate=await this.prepare({assignmentId:e.assignmentId,executionGeneration:e.executionGeneration,bindingJSON:binding.bindingJSON},e.intentId)
          if((await this.frame(gate)).active){await this.release(e.nativeSessionId);phase="active-admission-restored"}
        }catch{
          // Current revocation, source/profile change, pending/unknown outcome or missing held
          // boundary keeps this receiver held. It cannot disable independent primary planning.
        }
      }
      restored.push(Object.freeze({sessionID:e.nativeSessionId,intentId:e.intentId,phase}))
    }
    return Object.freeze(restored)
  }
  private applies(sessionID:string){return this.managed(sessionID)&&!this.connected(sessionID)}
  private async frame(gate:Gate){
    const e=gate.envelope
    if(this.closed||this.gates.get(e.nativeSessionId)!==gate)nativeUnavailable()
    const frame=await this.registry.initialContext(this.controllerSessionID,{assignmentId:e.assignmentId,executionGeneration:e.executionGeneration,
      bindingJSON:contextJSON(e.binding)},e.intentId)
    if(this.closed||this.gates.get(e.nativeSessionId)!==gate||frame.envelopeJSON!==gate.original.raw||frame.envelopeDigest!==gate.original.digest||frame.packetJSON!==gate.original.packet||
      !same(parse(frame.profileJSON),parse(gate.original.profileJSON)))nativeUnavailable()
    return frame
  }
  private async packet(gate:Gate){
    const current=(await this.ctx.session.instructions.entry.list({sessionID:gate.envelope.nativeSessionId})).filter(p=>p.key.startsWith("writhelm-"))
      .map(p=>({key:p.key,value:p.value})).sort((a,b)=>a.key<b.key?-1:a.key>b.key?1:0)
    return contextJSON(current)===gate.original.packet
  }
  private model(gate:Gate){
    const d=parse(gate.original.deploymentJSON),p=parse(gate.original.profileJSON).payload
    const selected={providerID:p.model.providerId,id:p.model.modelId,...(p.model.variant===null?{}:{variant:p.model.variant})}
    if(d.formatVersion?.value!=="1"||d.profileKeyJSON!==contextJSON(parse(gate.original.profileJSON).key)||!same(d.model,selected)||
      d.routeID!==p.route||typeof d.effortOptionKey!=="string"||typeof d.nativeOptions?.[d.effortOptionKey]!=="string"||
      d.nativeOptions[d.effortOptionKey]!==p.effort||!d.nativeOptions.maxTokens||BigInt(d.nativeOptions.maxTokens.value??d.nativeOptions.maxTokens)>BigInt(p.outputReserveTokens.value))nativeUnavailable()
    if(d.baseURL!==null){
      const url=new URL(d.baseURL)
      if(url.username||url.password||url.search||url.hash||!['http:','https:'].includes(url.protocol)||
        url.protocol==='http:'&&!['127.0.0.1','[::1]'].includes(url.hostname))nativeUnavailable()
    }
    const options:Record<string,string|boolean|number>={}
    for(const [key,value]of Object.entries(d.nativeOptions)){
      if(/headers|credentials?|secrets?|api.?key|authorization/i.test(key))nativeUnavailable()
      if(typeof value==='string'||typeof value==='boolean')options[key]=value
      else {const raw=(value as any)?.value;if(typeof raw!=="string"||!/^(0|[1-9][0-9]*)$/.test(raw)||BigInt(raw)>2147483647n)nativeUnavailable();options[key]=Number(raw)}
    }
    if(typeof options.maxTokens!=="number"||options.maxTokens<1)nativeUnavailable()
    if(p.platform!=="linux"||p.qualification!=="qualified"||!Array.isArray(p.permissions))nativeUnavailable()
    const actions:Record<string,string>={read:"read",glob:"glob",grep:"grep",shell:"shell",edit:"edit",write:"edit",patch:"edit",execute:"execute",
      question:"question",subagent:"subagent",external_directory:"external_directory"}
    const permissions:Array<{action:string;resource:string;effect:"allow"|"ask"|"deny"}>=[{action:"*",resource:"*",effect:"deny"}]
    for(const rule of p.permissions){
      if(typeof rule.tool!=="string"||!actions[rule.tool]||!["workspace","*"].includes(rule.scope)||!["allow","ask","deny"].includes(rule.action))
        throw new Error("qualified native permission translation is unavailable; no initial wake")
      permissions.push({action:actions[rule.tool]!,resource:"*",effect:rule.action})
    }
    return {d,p,selected,options,permissions}
  }
  private matches(message:any,gate:Gate,inbox=false){
    const e=gate.envelope
    if(!message||message.id!==e.messageId||message.type!=="user"||
      inbox&&message.sessionID!==e.nativeSessionId||!inbox&&message.sessionID!==undefined&&message.sessionID!==e.nativeSessionId)return false
    const body=inbox?message.payload:message
    return body?.text===e.text&&same(body.metadata,e.metadata)&&![body.files,body.agents,body.skills].some(v=>v?.length)
  }
  private checkPrompt(event:SessionPrompt,gate:Gate){
    const e=gate.envelope
    if(event.messageID!==e.messageId||event.prompt.text!==e.text||!same(event.metadata,e.metadata)||event.delivery!=="queue"||
      [event.prompt.files,event.prompt.agents,event.prompt.skills].some(v=>v?.length))nativeUnavailable()
  }
  async register(){
    if(process.platform!=="linux"||this.ctx.app.version!=="2.2.5")nativeUnavailable()
    await this.ctx.session.hook("prompt",async event=>{
      if(!this.applies(event.sessionID))return
      await this.admitPrompt(event)
    })
    await this.ctx.session.hook("context",async event=>{if(this.applies(event.sessionID))await this.admitContext(event.sessionID,event)})
    await this.ctx.tool.hook("execute.before",async event=>{if(this.applies(event.sessionID))await this.admitTool(event.sessionID)})
    await this.ctx.session.hook("model.request",async event=>{
      if(!this.applies(event.sessionID))return
      await this.admitModel(event)
    })
  }
  async admitPrompt(event:SessionPrompt){
    const gate=this.gates.get(event.sessionID);if(!gate||this.closed)nativeUnavailable()
    if(gate.closed){this.checkPrompt(event,gate);if(!(await this.frame(gate)).dispatch)nativeUnavailable()}
    else await this.admitTool(event.sessionID)
  }
  async prepare(subject:NativeSubject,intentId:string){
    if(this.closed||!uuidPattern.test(intentId)||subject.executionGeneration!=="0")nativeUnavailable()
    const frame=await this.registry.initialContext(this.controllerSessionID,subject,intentId)
    if(this.closed)nativeUnavailable()
    if(typeof frame.envelopeJSON!=="string"||hash(frame.envelopeJSON)!==frame.envelopeDigest)nativeUnavailable()
    const e=parse(frame.envelopeJSON)
    if(e.kind!=="initial-start"||e.intentId!==intentId||e.assignmentId!==subject.assignmentId||e.executionGeneration!==subject.executionGeneration||
      contextJSON(e.binding)!==subject.bindingJSON||e.delivery!=="queue"||e.resume!==true)nativeUnavailable()
    const receiver=await this.registry.inspect(e.nativeSessionId)
    if(receiver?.scope!=="assignment"||receiver.assignmentId!==subject.assignmentId||receiver.bindingJSON!==subject.bindingJSON)nativeUnavailable()
    const key=contextJSON(parse(frame.profileJSON).key)
    const execution=await this.deployment.read("execution-profile",key)
    if(execution===undefined)throw new Error("qualified execution profile is unavailable; no initial model wake")
    const original:Original={raw:frame.envelopeJSON,digest:frame.envelopeDigest,packet:frame.packetJSON,profileJSON:frame.profileJSON,deploymentJSON:execution,epoch:receiver.epoch}
    const serialized=contextJSON(original)
    const retained=await this.custody.read("initial-original",intentId)
    if(retained===undefined&&!frame.dispatch)nativeUnavailable()
    if(retained!==undefined&&retained!==serialized)nativeUnavailable()
    let gate=this.gates.get(e.nativeSessionId)
    if(gate){if(gate.original.digest!==original.digest)nativeUnavailable()}
    else {
      if(this.gates.size>=256)nativeUnavailable()
      let resolve!:()=>void,reject!:(e:Error)=>void
      const wait=new Promise<void>((yes,no)=>{resolve=yes;reject=no});void wait.catch(()=>{})
      let heldResolve!:()=>void,heldReject!:(e:Error)=>void
      const held=new Promise<void>((yes,no)=>{heldResolve=yes;heldReject=no});void held.catch(()=>{})
      gate={original,envelope:e,closed:true,wait,resolve,reject,held,heldResolve,heldReject};this.gates.set(e.nativeSessionId,gate)
    }
    const profile=this.model(gate)
    await this.contextPolicy?.(e.nativeSessionId,gate.original.profileJSON)
    const info=await this.native.session.get({sessionID:e.nativeSessionId})
    if(info.id!==e.nativeSessionId||info.parentID||info.fork||info.revert||info.location.directory!==receiver.workRoot||!same(info.model,profile.selected))nativeUnavailable()
    if(retained===undefined){
      const [context,inbox]=await Promise.all([this.native.session.context({sessionID:e.nativeSessionId}),this.native.session.inbox.list({sessionID:e.nativeSessionId})])
      if(context.length||inbox.length||info.cost!==0||!same(info.permissions,[{action:"*",resource:"*",effect:"deny"}]))nativeUnavailable()
      await this.custody.retain("initial-original",intentId,serialized)
    }
    const entries=parse(original.packet)
    if(!Array.isArray(entries)||!entries.length||entries.length>256)nativeUnavailable()
    const old=await this.ctx.session.instructions.entry.list({sessionID:e.nativeSessionId})
    if(old.filter(p=>p.key.startsWith("writhelm-")).some(p=>!entries.some(v=>v.key===p.key&&v.value===p.value)))nativeUnavailable()
    for(const entry of entries){
      if(old.some(p=>p.key===entry.key&&p.value===entry.value))continue
      const current=await this.frame(gate)
      if(!current.dispatch&&!current.active)nativeUnavailable()
      await this.ctx.session.instructions.entry.put({sessionID:e.nativeSessionId,key:entry.key,value:entry.value})
    }
    if(!await this.packet(gate))nativeUnavailable()
    return gate
  }
  async deliver(subject:NativeSubject,intentId:string){
    const gate=await this.prepare(subject,intentId),e=gate.envelope
    const dispatch=contextJSON({digest:gate.original.digest,messageId:e.messageId})
    const previous=await this.custody.read("initial-dispatch",intentId)
    if(previous!==undefined){if(previous!==dispatch)nativeUnavailable();return this.witness(intentId)}
    if(!(await this.frame(gate)).dispatch)nativeUnavailable()
    if(!await this.custody.retain("initial-dispatch",intentId,dispatch))return this.witness(intentId)
    if(!(await this.frame(gate)).dispatch||this.closed||this.gates.get(e.nativeSessionId)!==gate||!gate.closed)nativeUnavailable()
    try{
      const output=await this.ctx.session.prompt({sessionID:e.nativeSessionId,id:e.messageId,text:e.text,metadata:e.metadata,delivery:"queue",resume:true})
      if(!this.matches(output,gate,true))throw new Error("conflicting original initial prompt reply")
      await this.custody.retain("initial-returned",intentId,contextJSON({digest:gate.original.digest,messageId:e.messageId}))
    }catch{
      await this.custody.retain("initial-unknown",intentId,contextJSON({digest:gate.original.digest,messageId:e.messageId}))
    }
    return this.witness(intentId)
  }
  async admitContext(sessionID:string,event?:SessionContext){
    const gate=this.gates.get(sessionID);if(!gate||this.closed)nativeUnavailable()
    if(event)await this.configure(event)
    if(!await this.packet(gate))nativeUnavailable()
    if(gate.closed){
      const context=await this.native.session.context({sessionID})
      if(!context.some(message=>this.matches(message,gate)))nativeUnavailable()
      await this.custody.retain("initial-held",gate.envelope.intentId,contextJSON({digest:gate.original.digest,messageId:gate.envelope.messageId,sessionID}))
      gate.heldResolve()
      await gate.wait
    }
    await this.admitTool(sessionID)
  }
  async configure(event:SessionContext){
    const sessionID=event.sessionID,gate=this.gates.get(sessionID);if(!gate||this.closed)nativeUnavailable()
    const {selected,options}=this.model(gate)
    await this.contextPolicy?.(sessionID,gate.original.profileJSON)
      if(!same(event.model,selected))nativeUnavailable()
      for(const key of Object.keys(event.options))delete event.options[key]
      Object.assign(event.options,options)
      // The reviewer receives a read/report surface, not assessed-code mutation or nested agents.
      const binding=await this.registry.inspect(sessionID)
      if(binding?.scope!=="assignment")nativeUnavailable()
      const frame=await this.frame(gate),a=parse(frame.assignmentJSON).payload
      if(a.intendedExecution.template==="reviewer-v1")for(const name of Object.keys(event.tools))
        if(!["read","writhelm_work_read","writhelm_work_command"].includes(name))delete event.tools[name]
  }
  async admitModel(event:SessionModelRequest){
    const gate=this.gates.get(event.sessionID);if(!gate||this.closed)nativeUnavailable()
    if(event.kind!=="primary"){
      if(!this.auxiliary)nativeUnavailable()
      await this.auxiliary(event)
      return
    }
    const {d,selected}=this.model(gate)
    if(event.kind!=="primary"||!same(event.model,selected)||(event.baseURL??null)!==d.baseURL)nativeUnavailable()
    await this.contextPolicy?.(event.sessionID,gate.original.profileJSON)
    await this.admitTool(event.sessionID)
  }
  async admitTool(sessionID:string){
    const gate=this.gates.get(sessionID)
    if(!gate||this.closed||gate.closed)nativeUnavailable()
    const complete=await this.packet(gate),frame=await this.frame(gate)
    if(this.gates.get(sessionID)!==gate||this.closed||gate.closed||!complete||!frame.active)nativeUnavailable()
  }
  async awaitBoundary(sessionID:string,signal:AbortSignal){
    const gate=this.gates.get(sessionID);if(!gate||this.closed||signal.aborted)nativeUnavailable()
    await new Promise<void>((resolve,reject)=>{
      const abort=()=>finish(new Error("initial boundary wait cancelled; reconcile original dispatch"))
      const timeout=setTimeout(()=>finish(new Error("initial boundary outcome unknown; retain original dispatch")),30000)
      const finish=(error?:Error)=>{clearTimeout(timeout);signal.removeEventListener("abort",abort);error?reject(error):resolve()}
      signal.addEventListener("abort",abort,{once:true})
      if(signal.aborted)abort()
      gate.held.then(()=>finish(),()=>finish(new Error("initial boundary unavailable")))
    })
  }
  /** Immutable original witness only: safe under the service monitor/HTTP admission. */
  async witness(intentId:string){
    if(!uuidPattern.test(intentId))nativeUnavailable()
    const raw=await this.custody.read("initial-original",intentId)
    if(raw===undefined)return null
    const original=closedObject(parse(raw),["raw","digest","packet","profileJSON","deploymentJSON","epoch"])
    const envelope=parse(original.raw)
    if(hash(original.raw)!==original.digest||envelope.intentId!==intentId||!/^[0-9a-f]{64}$/.test(original.epoch))nativeUnavailable()
    const held=await this.custody.read("initial-held",intentId)
    if(held!==undefined){
      const h=closedObject(parse(held),["digest","messageId","sessionID"])
      if(h.digest!==original.digest||h.messageId!==envelope.messageId||h.sessionID!==envelope.nativeSessionId)nativeUnavailable()
    }
    return {formatVersion:1,envelopeJSON:original.raw,envelopeDigest:original.digest,packetDigest:hash(original.packet),epoch:original.epoch,
      heldJSON:held??null}
  }
  async release(sessionID:string){
    const gate=this.gates.get(sessionID);if(!gate||this.closed)nativeUnavailable()
    gate.closed=true // invalidates in-flight admissions while original permission release reconciles
    const frame=await this.frame(gate)
    const witness=await this.witness(gate.envelope.intentId)
    if(!frame.active||!await this.packet(gate)||!witness?.heldJSON)nativeUnavailable()
    const {permissions}=this.model(gate),a=parse(frame.assignmentJSON).payload
    permissions.push({action:"external_directory",resource:"*",effect:"deny"},{action:"subagent",resource:"*",effect:"deny"})
    if(a.intendedExecution.template==="reviewer-v1")for(const action of ["edit","shell","execute"])permissions.push({action,resource:"*",effect:"deny"})
    const expected=contextJSON({formatVersion:1,sessionID,digest:gate.original.digest,permissionsJSON:contextJSON(permissions)})
    const previous=await this.custody.read("initial-release",gate.envelope.intentId)
    if(previous!==undefined&&previous!==expected)nativeUnavailable()
    const info=await this.native.session.get({sessionID})
    if(info.id!==sessionID)nativeUnavailable()
    if(!same(info.permissions,permissions)){
      // Only a FIRST original transition from the exact passive deny surface may write. A later
      // deny/ask change can be an owner restriction; never erase it just because canonical ACTIVE
      // still permits work. Unknown/lost outcomes reconcile the target, not another update.
      if(previous!==undefined||!same(info.permissions,[{action:"*",resource:"*",effect:"deny"}]))nativeUnavailable()
      if(!await this.custody.retain("initial-release",gate.envelope.intentId,expected))nativeUnavailable()
      if(!(await this.frame(gate)).active)nativeUnavailable()
      const before=await this.native.session.get({sessionID})
      if(this.closed||this.gates.get(sessionID)!==gate||before.id!==sessionID||!same(before.permissions,info.permissions))nativeUnavailable()
      await this.ctx.session.update({sessionID,permissions})
    }else if(previous===undefined){
      // Upgrade/reconciliation of an already exact native target grants no new permission and
      // does not post an update. Retain its original selector for future changed-target detection.
      await this.custody.retain("initial-release",gate.envelope.intentId,expected)
    }
    const actual=await this.native.session.get({sessionID})
    if(actual.id!==sessionID||!same(actual.permissions,permissions))nativeUnavailable()
    if(!(await this.frame(gate)).active||this.closed||this.gates.get(sessionID)!==gate)nativeUnavailable()
    gate.closed=false;gate.resolve()
  }
  async close(){this.closed=true;for(const gate of this.gates.values()){
    const error=new Error("initial native admission closed; reconcile original effects");gate.reject(error);gate.heldReject(error)
  }}
}
