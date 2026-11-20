// SPDX-License-Identifier: AGPL-3.0-only
// Invoked only by a disposable JVM HTTP journey. Native SDK/DOM are doubles; service/auth/custody
// and adapter tool/controller logic are real. No provider/model API or owner's instance is used.
import path from "node:path"
import assert from "node:assert/strict"
import { createRoot } from "solid-js"
import * as solid from "solid-js"
import * as effect from "effect"
import * as sdk from "@opencode/plugin/gui"
import { createRequire } from "node:module"
import { runInNewContext } from "node:vm"
import { readFileSync } from "node:fs"
import { randomUUID, createHash } from "node:crypto"
import { parseHTML } from "linkedom"
import type { Plugin } from "@opencode/plugin"
import { approvalClientDirectory } from "../src/automatic-approval.js"
import { ApprovalDelivery } from "../src/approval-delivery.js"
import { LinuxProtectedFiles } from "../src/protected.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { registerPrimaryGovernor } from "../src/native-primary.js"
import { installFreshPrimaryPolicy } from "../src/primary-policy.js"
import { boundedPlanningKnowledgeChannel, boundedWorkChannel, registerConnectedWorkTools, LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import type { Session } from "../src/contracts.js"
import { exactJSON } from "../src/strict-json.js"
import { contextJSON } from "../src/native-context-codec.js"
import { ModelReads } from "../src/model-read.js"
import {ReviewDisplayCustody} from "../src/review-display-custody.js"

let text="";for await(const chunk of process.stdin)text+=chunk
const input=JSON.parse(text) as {root:string;work:string;sessionID:string;controllerId:string;projectId:string;requestId:string;
  requestJSON:string;profileCommandJSON:string;profileReadJSON:string}
const session:Session={key:`sidecar\n${input.sessionID}`,id:input.sessionID,server:"sidecar",directory:input.work,projectRoot:input.work}
const files=await LinuxProtectedFiles.open(approvalClientDirectory(input.root,input.sessionID),[input.work])
const profile=await files.read();await files.close()
const displayStorage=new LinuxNativeCustody(path.join(input.root,"review-display"),[input.work]);await displayStorage.initialize()
const displayCustody=new ReviewDisplayCustody(displayStorage)
assert.equal(profile.projectId,input.projectId)
await installFreshPrimaryPolicy(input.work)
const custody=new LinuxNativeCustody(path.join(input.root,"approval-tool-test"),[input.work]);await custody.initialize()
const deliveryCustody=new LinuxNativeCustody(path.join(input.root,"approval-delivery"),[input.work]);await deliveryCustody.initialize()
const delivery=new ApprovalDelivery(deliveryCustody),tools=new Map<string,any>()
const channel={...boundedPlanningKnowledgeChannel(input.projectId,profile.origin,profile.agent.token,
  new LinuxWorkRequestCustody(path.join(input.root,"approval-tool-requests"),[input.work]),input.sessionID),
  readBinding:contextJSON({storeId:profile.storeId,projectId:input.projectId,controllerId:input.controllerId,sessionID:input.sessionID,principalId:profile.agent.principalId,
    scope:"controller",delegationId:null,assignmentId:null,executionGeneration:null,bindingJSON:null})}
let permissions:any[]=[]
const ctx={app:{version:"2.2.5"},location:{directory:input.work},session:{
  get:async()=>({id:input.sessionID,location:{directory:input.work},permissions}),context:async()=>[],
  update:async(value:any)=>{permissions=value.permissions},hook:async()=>({dispose:async()=>{}})},
  permission:{hook:async()=>({dispose:async()=>{}})},
  tool:{hook:async()=>({dispose:async()=>{}}),reload:async()=>{},transform:async(callback:any)=>{
    callback({add:(tool:any)=>tools.set(tool.name,tool)});return {dispose:async()=>{}}}},
} as unknown as Plugin.Context
let bound=false
const registry={bound:()=>bound,restore:async()=>false,scopeKind:()=>bound?"controller":undefined,bind:async()=>{bound=true},
  inspect:async()=>({scope:"controller",credentialId:profile.agent.credentialId}),
  sourceIdentity:async()=>({scope:"controller",projectId:input.projectId,controllerId:input.controllerId}),
  planningKnowledgeChannel:async()=>channel,
} as unknown as NativeCredentialRegistry
const governor=await registerPrimaryGovernor(ctx,registry,custody,{workRoot:input.work,controllerId:input.controllerId,
  controllerCredentialId:profile.agent.credentialId,projectId:input.projectId,schemas:{"knowledge-read1":"synthetic test schema"},
  approvalDelivery:delivery,admissionHooks:false})
let dispose=()=>{},disposeGUI=()=>{},closeMain=async()=>{}
try{
  await governor.selected(input.sessionID,true)
  const actual={sessionID:input.sessionID,messageID:"msg_approval_test",id:"call_approval_test",signal:new AbortController().signal}
  const created=await tools.get("writhelm_knowledge_command").execute({json:input.requestJSON},actual)
  assert.match(created.content,/Native approval panel: requested/)
  assert.equal((await delivery.current(session.id,input.projectId))?.requestId,input.requestId)
  const opened=await tools.get("writhelm_open_approval").execute({requestId:input.requestId},actual)
  assert.match(opened.content,/requested/)
  const ref=(await delivery.current(session.id,input.projectId))!
  assert.ok(ref)
  const caller={window:1,signal:actual.signal}
  const {document,window}=parseHTML("<html><body></body></html>");Object.assign(globalThis,{document,window})
  const require=createRequire(import.meta.url),provided=new Map<any,any>(),panels:any[]=[],opens:any[]=[],closes:any[]=[],toasts:any[]=[]
  process.env.WRITHELM_PRIMARY_ROOT=input.root // disposable test child only, not user configuration
  const sandbox:any={console,process,Buffer,URL,TextDecoder,TextEncoder,document,window,setTimeout,clearTimeout,setInterval,clearInterval,
    AbortSignal,AbortController,fetch,performance,module:{exports:{}},exports:{}}
  sandbox.exports=sandbox.module.exports
  sandbox.require=(id:string)=>id==="@opencode/plugin/gui"?sdk:id==="solid-js"?solid:id==="effect"?effect:
    id==="electron"?{dialog:{}}:require(id)
  runInNewContext(readFileSync(new URL("../../dist/gui-experimental.cjs",import.meta.url),"utf8"),sandbox)
  const definition=sandbox.module.exports.default??sandbox.module.exports
  closeMain=await definition.main({app:{platform:"desktop",version:"2.2.5"},options:{},
    client:{session:{get:async()=>({id:session.id,location:{directory:input.work}})},
      project:{list:async()=>[{canonical:input.work,sandboxes:[]}]}},
    windows:{get:()=>({}),on:()=>{}},storage:{store:()=>({value:{root:""},set:()=>{}})},
    provide:(token:any,implementation:any)=>provided.set(token,implementation),
  })
  let ownerBundleRaw="",loaded=false,answerResult:undefined|{code:string;pending:boolean},answerRaw:any
  let displayLanguage="en"
  const mounted={...session,server:{id:"sidecar"},project:{worktree:input.work}}
  createRoot(d=>{
    disposeGUI=d
    definition.setup({app:{platform:"desktop",version:"2.2.5"},id:"writhelm-opencode",
      locale:{locale:()=>displayLanguage},
      sessions:{current:()=>mounted},add:(token:any,item:any)=>{if(token===sdk.Panel)panels.push(item)},
      main:(token:any)=>new Proxy({},{get:(_target,method)=>{
          if(method==="state")return()=>undefined
        if(method==="on")return()=>()=>{}
         return async(value:any)=>{
           if(method==="requested"&&displayLanguage==="en"){
             const selected=await delivery.selected(session.id,input.projectId)
             if(selected)await displayCustody.retainSourceLanguage({storeId:profile.storeId,projectId:input.projectId,
               sessionID:session.id,requestId:selected.requestId,requestDigest:selected.requestDigest,packetDigest:selected.packetDigest},"en")
           }
           if(method==="open"&&displayLanguage==="en"){
             // These legacy synthetic source revisions were authored in English. Provision their
             // exact declaration, exercising the actual main custody path rather than raw fallback.
             await displayCustody.retainSourceLanguage({storeId:profile.storeId,projectId:input.projectId,
               sessionID:session.id,requestId:value.requestId,requestDigest:value.requestDigest,packetDigest:value.packetDigest},"en")
           }
           const result=await provided.get(token)[method](value,caller)
          if(method==="open")ownerBundleRaw=result.bundleJSON
          if(method==="shown"&&result.ok)loaded=true
          if(method==="answer"){answerResult={code:result.code,pending:result.pending};answerRaw=result}
          return result
        }
      }}),layout:{open:(key:any,s:any)=>opens.push({key,session:s}),close:(key:any,s:any)=>closes.push({key,session:s})},
      toast:{show:(options:any)=>{toasts.push(options);return {dismiss(){}}}},
    })
  })
  const deadline=Date.now()+15000
  while(opens.length===0&&Date.now()<deadline)await new Promise(resolve=>setImmediate(resolve))
  assert.equal(opens[0]?.key,"writhelm-opencode:review","built GUI/native-main delivery must open the originating panel")
  assert.equal(opens[0]?.session.id,session.id)
  let root!:HTMLElement
  createRoot(d=>{dispose=d;root=panels.find(p=>p.id==="review").render({session:mounted,tab:{id:"review"},screen:{}});document.body.append(root)})
  while(!loaded&&Date.now()<deadline)await new Promise(resolve=>setImmediate(resolve))
  assert.ok(loaded,"wait for the native-main acknowledgement, not a partially published custody file")
  assert.equal(await delivery.shown(session.id,ref),true)
  const body=root.textContent!;assert.equal(body.includes(profile.active.token),false)
  assert.equal(body.includes(profile.agent.token),false)
  const before=await channel.send("reads",JSON.stringify({formatVersion:1,projectId:input.projectId,
    operation:"read_review_bundle",subject:{id:ref.requestId,requestDigest:ref.requestDigest,packetDigest:ref.packetDigest}}),actual.signal)
  const ownerBundle=exactJSON(ownerBundleRaw,()=>{throw Error("invalid owner bundle")})
  assert.equal(root.querySelector("fieldset")!.disabled,false,"actual owner bundle blockers: "+JSON.stringify(ownerBundle.data.blockers))
  assert.equal(exactJSON(before,()=>{throw Error("invalid")}).data.resolution,null,"opening must not approve")
  assert.equal(root.querySelectorAll('input[type="radio"]').length,0)
  const button=[...root.querySelectorAll("button")].find(b=>b.textContent==="Утвердить")!
  button.click()
  // Await the actual native answer reply instead of flooding the HTTP admission with polling.
  while(!answerResult&&Date.now()<deadline)await new Promise(resolve=>setImmediate(resolve))
  assert.ok(answerResult,"explicit synthetic owner action must return its native reply")
  assert.equal(answerResult.code,"OK",JSON.stringify(answerResult))
  assert.equal(answerResult.pending,false)
  const resolution=await channel.send("reads",JSON.stringify({formatVersion:1,projectId:input.projectId,
    operation:"read_resolution",subject:{id:ref.requestId}}),actual.signal)
  assert.equal(exactJSON(resolution,()=>{throw Error("invalid")}).kind,"read","explicit click must publish the exact canonical resolution")
  while(!closes.length&&Date.now()<deadline)await new Promise(resolve=>setImmediate(resolve))
  assert.equal(closes[0]?.key,"writhelm-opencode:review","confirmed exact decision closes the panel: "+JSON.stringify({answer:answerRaw,status:root.textContent!.slice(-1200)}))
  assert.equal(closes[0]?.session.id,session.id)
  assert.equal(toasts[0]?.title,"Контракт утверждён.")
  const after=await tools.get("writhelm_open_approval").execute({requestId:input.requestId},actual)
  assert.match(after.content,/rendered/)
  // Additive reasoned rejection travels through real native-main/controller/HTTP custody.
  // Metadata and owner actions here are explicit disposable test fixtures, not model output.
  const followup=JSON.parse(input.requestJSON),followupId=randomUUID()
  followup.requestKey=randomUUID();followup.payload.requestId=followupId
  const oldRef=followup.payload.packet.items[0].target
  const oldRevision=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:input.projectId,
    operation:"read_revision",subject:{revisionKey:oldRef.revision,revisionDigest:oldRef.revisionDigest}}),actual.signal),()=>{throw Error("invalid old revision")})
  const nextKey={...oldRef.revision,revisionId:"ffffffff-ffff-ffff-ffff-ffffffffffff"}
  const proposed=exactJSON(await channel.send("commands",contextJSON({formatVersion:1,projectId:input.projectId,requestKey:randomUUID(),
    operation:"propose_revision",payload:{revisionKey:nextKey,body:oldRevision.data.body,structure:oldRevision.data.structure}}),actual.signal),
    ()=>{throw Error("invalid next revision")})
  assert.equal(proposed.outcome.kind,"success")
  const catalogue=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:input.projectId,operation:"list_revisions",
    subject:{documentId:nextKey.document.documentId,cursor:null,pageSize:32}}),actual.signal),()=>{throw Error("invalid revisions")})
  const nextDigest=catalogue.data.items.find((row:any)=>row.revisionKey.revisionId===nextKey.revisionId).revisionDigest
  const nextRef=(ref:any)=>({...ref,revision:nextKey,revisionDigest:nextDigest})
  const changeMap:any[]=[]
  for(const item of followup.payload.packet.items){
    const previousItemId=item.id;item.id=randomUUID();item.before=item.target;item.target=nextRef(item.target)
    changeMap.push({previousItemId,itemId:item.id,disposition:"revised",explanation:"Новая точная ревизия синтетического пункта."})
  }
  followup.payload.packet.contexts=[...followup.payload.packet.contexts,...followup.payload.packet.contexts.map(nextRef)]
  followup.payload.packet.followup={requestId:input.requestId,requestDigest:ref.requestDigest,packetDigest:ref.packetDigest,items:changeMap}
  const prepared=exactJSON(await channel.send("commands",JSON.stringify(followup),actual.signal),()=>{throw Error("invalid followup")})
  assert.equal(prepared.kind,"settled",contextJSON(prepared));assert.equal(prepared.outcome.kind,"success",contextJSON(prepared.outcome))
  const followupRead=exactJSON(await channel.send("reads",JSON.stringify({formatVersion:1,projectId:input.projectId,
    operation:"read_request",subject:{id:followupId}}),actual.signal),()=>{throw Error("invalid followup read")})
  const followupPayload=followupRead.data.record.payload
  loaded=false;answerResult=undefined
  await delivery.request(session.id,{project:input.projectId,requestId:followupId,
    requestDigest:followupPayload.requestDigest,packetDigest:followupPayload.packetDigest})
  const followupDeadline=Date.now()+15000
  while(!loaded&&Date.now()<followupDeadline)await new Promise(resolve=>setImmediate(resolve))
  assert.equal(loaded,true,"new exact request must be rendered before rejection")
  const rejectButton=[...root.querySelectorAll("button")].find(b=>b.textContent==="Отклонить")!
  rejectButton.click();for(let n=0;n<6;n++)await new Promise(resolve=>setImmediate(resolve))
  assert.equal(answerResult,undefined,"a rejection without a reason must not dispatch")
  const reason="Отклонено в синтетической проверке: требуется уточнение границ."
  const reasonArea=root.querySelector('[aria-label="Причина отклонения"]') as HTMLTextAreaElement
  assert.equal(reasonArea.hidden,false);reasonArea.value=reason;rejectButton.click()
  while(!answerResult&&Date.now()<followupDeadline)await new Promise(resolve=>setImmediate(resolve))
  assert.equal((answerResult as {code:string}|undefined)?.code,"OK")
  const rejectedRead=exactJSON(await channel.send("reads",JSON.stringify({formatVersion:1,projectId:input.projectId,
    operation:"read_resolution",subject:{id:followupId}}),actual.signal),()=>{throw Error("invalid rejection read")})
  assert.equal(rejectedRead.data.record.payload.answer.kind,"reject-with-reason")
  assert.equal(rejectedRead.data.record.payload.answer.reason,reason)
  console.log("PASS actual native-main/controller -> required rejection reason -> protected original command -> real HTTP canonical reasoned resolution")
  const prepareAndShow=async(command:any)=>{
    const submitted=exactJSON(await channel.send("commands",contextJSON(command),actual.signal),()=>{throw Error("invalid revision request")})
    assert.equal(submitted.outcome.kind,"success",contextJSON(submitted))
    const found=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:input.projectId,
      operation:"read_request",subject:{id:command.payload.requestId}}),actual.signal),()=>{throw Error("invalid revision request read")})
    const record=found.data.record
    loaded=false;answerResult=undefined
    await delivery.request(session.id,{project:input.projectId,requestId:record.key,
      requestDigest:record.payload.requestDigest,packetDigest:record.payload.packetDigest})
    const limit=Date.now()+15000
    while(!loaded&&Date.now()<limit)await new Promise(resolve=>setImmediate(resolve))
    assert.equal(loaded,true,"exact linked revision must load")
    return record
  }
  const feedback=JSON.parse(JSON.stringify(followup))
  feedback.requestKey=randomUUID();feedback.payload.requestId=randomUUID()
  const unchangedMap=feedback.payload.packet.items.map((item:any)=>{
    const previousItemId=item.id;item.id=randomUUID()
    return {previousItemId,itemId:item.id,disposition:"unchanged",explanation:"Пункт остаётся неутверждённым и входит в новое решение."}
  })
  feedback.payload.packet.followup={requestId:followupId,requestDigest:followupPayload.requestDigest,
    packetDigest:followupPayload.packetDigest,items:unchangedMap}
  const feedbackRecord=await prepareAndShow(feedback)
  assert.ok(root.textContent!.includes(reason),"canonical rejection reason must reach the next review")
  const comment="Уточнить именно этот пункт, сохранив остальные ограничения."
  const commentArea=root.querySelector("[data-item-id] textarea") as HTMLTextAreaElement
  assert.equal(commentArea.hidden,true)
  commentArea.closest("[data-item-id]")!.dispatchEvent(new window.Event("contextmenu",{bubbles:true,cancelable:true}))
  ;(root.querySelector('[role="menuitem"]') as HTMLButtonElement).click()
  for(let n=0;n<6;n++)await new Promise(resolve=>setImmediate(resolve))
  assert.equal(commentArea.hidden,false)
  assert.equal(commentArea.placeholder,"возражение")
  commentArea.value=comment;commentArea.dispatchEvent(new window.Event("input"))
  ;[...root.querySelectorAll("button")].find(b=>b.textContent==="Вернуть с замечаниями")!.click()
  const feedbackLimit=Date.now()+15000
  while(!answerResult&&Date.now()<feedbackLimit)await new Promise(resolve=>setImmediate(resolve))
  assert.equal((answerResult as {code:string}|undefined)?.code,"OK")
  const oldResolution=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:input.projectId,
    operation:"read_resolution",subject:{id:feedbackRecord.key}}),actual.signal),()=>{throw Error("invalid point feedback")})
  assert.equal(oldResolution.data.record.payload.answer.comments[0].itemId,feedback.payload.packet.items[0].id)
  assert.equal(oldResolution.data.record.payload.answer.comments[0].text,comment)
  // Real new source bytes, not a renderer-authored summary or an inferred before-reference.
  const changedBytes=Buffer.from(oldRevision.data.body),structure=JSON.parse(contextJSON(oldRevision.data.structure))
  const changedSpan=structure.clauses[0].span
  assert.ok(changedBytes[changedSpan.start]!<128,"this disposable source is ASCII")
  changedBytes[changedSpan.start]=changedBytes[changedSpan.start]===88?90:88
  const revisedKey={...nextKey,revisionId:"ffffffff-ffff-ffff-ffff-fffffffffffe"}
  const revisedProposal=exactJSON(await channel.send("commands",contextJSON({formatVersion:1,projectId:input.projectId,
    requestKey:randomUUID(),operation:"propose_revision",payload:{revisionKey:revisedKey,body:changedBytes.toString(),structure}}),actual.signal),
    ()=>{throw Error("invalid revised source")})
  assert.equal(revisedProposal.outcome.kind,"success")
  const revisedCatalogue=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:input.projectId,
    operation:"list_revisions",subject:{documentId:nextKey.document.documentId,cursor:null,pageSize:32}}),actual.signal),
    ()=>{throw Error("invalid revised catalogue")})
  const revisedDigest=revisedCatalogue.data.items.find((row:any)=>row.revisionKey.revisionId===revisedKey.revisionId).revisionDigest
  const revisedRef=(source:any)=>{
    const result={...source,revision:revisedKey,revisionDigest:revisedDigest}
    if(source.kind==="clause"){
      const span=structure.clauses.find((clause:any)=>clause.id===source.clauseId).span
      result.contentDigest=createHash("sha256").update(changedBytes.subarray(span.start,span.endExclusive)).digest("hex")
    }
    return result
  }
  const correction=JSON.parse(JSON.stringify(feedback))
  correction.requestKey=randomUUID();correction.payload.requestId=randomUUID()
  correction.payload.packet.followup={requestId:feedbackRecord.key,requestDigest:feedbackRecord.payload.requestDigest,
    packetDigest:feedbackRecord.payload.packetDigest,items:correction.payload.packet.items.map((item:any)=>{
      const previousItemId=item.id;item.id=randomUUID();item.target=revisedRef(item.target)
      return {previousItemId,itemId:item.id,disposition:"revised",explanation:"Уточнение автора по точному замечанию предыдущей версии."}
    })}
  const originalContexts=JSON.parse(input.requestJSON).payload.packet.contexts
  correction.payload.packet.contexts=[...originalContexts,...originalContexts.map(revisedRef)]
  const correctionRecord=await prepareAndShow(correction)
  assert.ok(root.textContent!.includes(comment),"service-linked exact prior item comment must be shown beside its correction")
  assert.ok(root.textContent!.includes("Уточнение автора по точному замечанию"))
  const activeBundle=exactJSON(ownerBundleRaw,()=>{throw Error("invalid correction bundle")}).data
  const previousProjection=exactJSON(activeBundle.followupJSON,()=>{throw Error("invalid canonical correction projection")})
  assert.equal(previousProjection.request.key,feedbackRecord.key)
  assert.equal(previousProjection.resolution.payload.answer.comments[0].text,comment)
  assert.ok(root.querySelector("[data-item-id] [data-canonical-text]"),"corrected exact wording belongs directly to its canonical point")
  ;[...root.querySelectorAll("button")].find(b=>b.textContent==="Утвердить")!.click()
  const correctionLimit=Date.now()+15000
  while(!answerResult&&Date.now()<correctionLimit)await new Promise(resolve=>setImmediate(resolve))
  assert.equal((answerResult as {code:string}|undefined)?.code,"OK")
  const approvedCorrection=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:input.projectId,
    operation:"read_resolution",subject:{id:correctionRecord.key}}),actual.signal),()=>{throw Error("invalid correction resolution")})
  assert.equal(approvedCorrection.data.record.payload.answer.kind,"approve")
  console.log("PASS real HTTP linked rejection -> exact inline item comments -> changed source revision -> service-derived predecessor/comment projection -> native re-review approval")
  const authored={intentId:"synthetic-typed-document-v1",context:"Synthetic context 🧭.",
    clauses:[{id:"CHECK-01",title:"Exact deadline 🧭",text:"Preserve the deadline.\r\nDo not normalize the owner's exact words."},
      {id:"CHECK-02",title:"Capacity",text:"Check available seats before confirmation."}]}
  const proposer=tools.get("writhelm_propose_document"),reviewer=tools.get("writhelm_request_review")
  assert.ok(proposer&&reviewer)
  const proposalReply=await proposer.execute(authored,actual),proposal=JSON.parse(proposalReply.content)
  assert.match(proposal.revisionReference,/^wr1_/)
  const proposalAgain=JSON.parse((await proposer.execute(authored,{...actual,id:"call_typed_retry"})).content)
  assert.equal(proposalAgain.revisionId,proposal.revisionId)
  assert.equal(proposalAgain.revisionReference,proposal.revisionReference)
  const typedRead=await tools.get("writhelm_knowledge_read").execute({reference:proposal.revisionReference,format:"canonical"},actual)
  const typedRevision=JSON.parse(typedRead.content).data
  assert.equal(typedRevision.structure.clauses.length,2)
  assert.ok(typedRevision.body.includes("CHECK-01 — Exact deadline 🧭"))
  assert.ok(typedRevision.body.includes("Preserve the deadline.\r\n"))
  loaded=false;answerResult=undefined
  const reviewInput={intentId:"synthetic-typed-review-v1",reviewLanguage:"ru",items:authored.clauses.map(clause=>({
    key:clause.id,target:{reference:proposal.revisionReference,clauseId:clause.id},before:null})),
    question:"Согласовать синтетический договор?",scope:"Два точных пункта.",rationale:"Проверка типизированного пути без чтения исходников.",
    consequences:["Только рассмотрение."],alternatives:["Вернуть с возражениями."],uncertainties:[],
    contexts:[{reference:proposal.revisionReference}],dependencies:[],informationGaps:[],
    presentations:[{language:"ru",sourceLanguage:"en",items:[
      {itemKey:"CHECK-01",title:"Точный срок 🧭",text:"Срок сохраняется.\r\nТочные слова владельца не нормализуются."},
      {itemKey:"CHECK-02",title:"Вместимость",text:"Проверить свободные места перед подтверждением."}]}]}
  displayLanguage="ru-RU"
  const requested=await reviewer.execute(reviewInput,actual)
  assert.match(requested.content,/Native approval panel: requested/)
  const typedLimit=Date.now()+15000
  while(!loaded&&Date.now()<typedLimit)await new Promise(resolve=>setImmediate(resolve))
  assert.equal(loaded,true)
  const typedRequestId=requested.metadata.writhelmRequestId
  assert.ok(typedRequestId)
  assert.equal(answerResult,undefined,"typed preparation and native delivery never decide")
  const typedBundle=exactJSON(ownerBundleRaw,()=>{throw Error("invalid typed bundle")}).data
  assert.equal(typedBundle.request.key,typedRequestId)
  assert.equal(typedBundle.request.payload.packet.items.length,2)
  assert.equal(typedBundle.resolution,null)
  assert.equal(typedBundle.request.payload.packet.presentations[0].items[0].itemId,typedBundle.request.payload.packet.items[0].id)
  assert.ok(root.textContent!.includes("CHECK-01 — Точный срок 🧭"))
  assert.equal(root.querySelector("[data-item-id] [data-canonical-text]")!.textContent,reviewInput.presentations[0].items[0].text)
  assert.equal((root.querySelector("[data-item-id] textarea") as HTMLTextAreaElement).hidden,true)
  console.log("PASS typed Unicode clauses -> adapter-generated exact byte spans/digests -> protected same-intent replay -> real canonical proposal -> exact read-reference review -> installed-bundle native panel; no manual hashes, schema source reads or owner decision")
  const read=tools.get("writhelm_knowledge_read")
  const projects=await read.execute({json:contextJSON({formatVersion:1,projectId:null,operation:"list_projects",subject:{cursor:null,pageSize:32}})},actual)
  assert.ok(projects.content.includes(input.projectId));assert.match(projects.content,/Page completeness: complete/)
  const documents=await read.execute({json:contextJSON({formatVersion:1,projectId:input.projectId,operation:"list_documents",subject:{cursor:null,pageSize:32}})},actual)
  const documentRef=documents.content.match(/Item 1: (wr1_[A-Za-z0-9_-]{24})/)![1]
  const revisions=await read.execute({reference:documentRef},actual)
  const revisionRef=revisions.content.match(/Item 1: (wr1_[A-Za-z0-9_-]{24})/)![1]
  const revision=await read.execute({reference:revisionRef},actual)
  assert.match(revision.content,/not whole-document approval/);assert.match(revision.content,/Clause .*: wr1_/)
  const approvedClause=JSON.parse(input.requestJSON).payload.packet.items.find((item:any)=>item.target.kind==="clause").target.clauseId
  const clauseLine=revision.content.split("\n").find((line:string)=>line.startsWith("Clause "+approvedClause+": "))!
  const clauseRef=clauseLine.match(/wr1_[A-Za-z0-9_-]{24}/)![0]
  const clause=await read.execute({reference:clauseRef},actual)
  assert.match(clause.content,/observation: APPROVED/)
  const canonicalClause=await read.execute({reference:clauseRef,format:"canonical"},actual)
  const clauseSources=exactJSON(canonicalClause.content,()=>{throw Error("invalid canonical clause")}).data.fragments
  const recovered=new ModelReads(new LinuxNativeCustody(path.join(input.root,"read-references"),[input.work]))
  await recovered.initialize()
  const reopened=await recovered.read("knowledge",channel,input.sessionID,{reference:clause.metadata.writhelmReadReference},
    new Set(["read_clause"]),actual.signal)
  assert.equal(reopened.content,clause.content,"same exact source/current observation survives adapter restart")
  const workChannel={...boundedWorkChannel(input.projectId,"controller",profile.origin,profile.agent.token,4*1024*1024,
    new LinuxWorkRequestCustody(path.join(input.root,"work-read-test-requests"),[input.work]),input.sessionID),readBinding:channel.readBinding}
  const workTools=await registerConnectedWorkTools(ctx,{resolve:async()=>workChannel},undefined,recovered)
  try{
    await tools.get("writhelm_work_contract").execute({kind:"commands",operation:"create_profile"},actual)
    const profileCommand=JSON.parse(input.profileCommandJSON)
    profileCommand.payload.key.revision=String(profileCommand.payload.key.revision)
    const profileCreated=await tools.get("writhelm_work_command").execute({operation:profileCommand.operation,
      requestKey:profileCommand.requestKey,payload:profileCommand.payload},actual)
    const profileResult=exactJSON(profileCreated.content,()=>{throw Error("invalid profile metadata receipt")})
    assert.equal(profileResult.kind,"settled");assert.equal(profileResult.outcome.kind,"success")
    const exactProfile=JSON.parse(input.profileReadJSON)
    exactProfile.subject.key.revision=String(exactProfile.subject.key.revision)
    await tools.get("writhelm_work_contract").execute({kind:"reads",operation:exactProfile.operation},actual)
    const profileRead=await tools.get("writhelm_work_read").execute({operation:exactProfile.operation,subject:exactProfile.subject},actual)
    assert.match(profileRead.content,/read-projection-no-call/);assert.equal(profileRead.metadata.writhelmReadMeaning,"exact")
    const profileReopened=await tools.get("writhelm_work_read").execute({reference:profileRead.metadata.writhelmReadReference},actual)
    assert.equal(profileReopened.content,profileRead.content,"Kotlin-selected immutable work digest must survive exact alias resolution")
    const work=await tools.get("writhelm_work_read").execute({json:contextJSON({formatVersion:2,projectId:input.projectId,
      operation:"list_work_records",subject:{family:"task-heads",taskId:null,cursor:null,pageSize:32}})},actual)
    assert.match(work.content,/Page completeness: complete/)
    assert.equal(work.metadata.writhelmReadMeaning,"query")
    console.log("PASS real HTTP typed work profile command/read from per-operation contract; exact integer wire and protected read-reference reopen; no source-code reads")
    // Test-owned disposable service only. Owner authority is used by this trusted test driver,
    // never through a model tool or a production caller-supplied credential argument.
    const revoked=await fetch(profile.origin+"/api/v1/commands",{method:"POST",redirect:"error",
      headers:{"Content-Type":"application/json",Authorization:"Bearer "+profile.active.token},
      body:contextJSON({formatVersion:1,projectId:input.projectId,requestKey:randomUUID(),operation:"revoke_access",
        payload:{principalId:profile.agent.principalId}})})
    const revokeReply=exactJSON(await revoked.text(),()=>{throw Error("invalid fixture revocation reply")})
    assert.equal(revokeReply.kind,"settled");assert.equal(revokeReply.outcome.kind,"success")
    const denied=await read.execute({reference:clause.metadata.writhelmReadReference},actual)
    assert.match(denied.content,/FORBIDDEN|UNAUTHENTICATED/)
    for(const source of clauseSources)assert.equal(denied.content.includes(source.text),false,"a retained handle must not serve cached source after revocation")
    const deniedWork=await tools.get("writhelm_work_read").execute({reference:profileRead.metadata.writhelmReadReference},actual)
    assert.match(deniedWork.content,/FORBIDDEN|UNAUTHENTICATED/)
    assert.equal(deniedWork.content.includes("read-projection-no-call"),false)
  }finally{await workTools.dispose()}
  console.log("PASS real HTTP knowledge/work readable tools -> document/revision/exact approved clause references -> protected reference reopen -> actual service revocation refuses retained knowledge/work handles without cached content")
  console.log("PASS real service/controller credential -> canonical request tool -> built GUI/native-main exact panel opening -> protected chat client -> complete readable panel -> rendered ack -> explicit synthetic owner click -> canonical resolution; no model calls")
}finally{dispose();disposeGUI();await closeMain();await governor.close()}
