// SPDX-License-Identifier: AGPL-3.0-only
// Model-free component journey: actual protected custody/controller/DOM, synthetic authority replies.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { randomUUID } from "node:crypto"
import { createRoot } from "solid-js"
import { parseHTML } from "linkedom"
import { AutomaticApprovalClients, approvalClientDirectory } from "../src/automatic-approval.js"
import { ApprovalDelivery } from "../src/approval-delivery.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxProtectedFiles } from "../src/protected.js"
import { credential } from "../src/helper.js"
import { nativePanel } from "../src/panel.js"
import { Inputs, reply, type Session, type Reply } from "../src/contracts.js"
import { contextJSON } from "../src/native-context-codec.js"
import { approvalReference, type ExactLink } from "../src/link.js"

const fixtureRaw=await fs.readFile(new URL("../../tests/fixtures/bundle.json",import.meta.url),"utf8")
const request=JSON.parse(fixtureRaw).data.request
const id=(n:number)=>`00000000-0000-0000-0000-${String(n).padStart(12,"0")}`
const ref:ExactLink={project:request.payload.projectId,requestId:request.key,requestDigest:request.payload.requestDigest,packetDigest:request.payload.packetDigest}
const caller={window:1,signal:new AbortController().signal}
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"approval-")),root=path.join(area,"private"),work=path.join(area,"project")
  await fs.mkdir(work);await fs.mkdir(root,{mode:0o700})
  const session=(name:string):Session=>({key:`sidecar\nses_${name}`,id:`ses_${name}`,server:"sidecar",directory:work,projectRoot:work})
  const a=session("approval_a"),b=session("approval_b"),other=session("approval_other")
  const owner=credential(id(1),ref.project,id(3)),agent=credential(id(1),ref.project,id(5))
  async function install(s:Session,project=ref.project){
    const files=await LinuxProtectedFiles.open(approvalClientDirectory(root,s.id),[work],false).catch(async()=>{
      await fs.mkdir(path.dirname(approvalClientDirectory(root,s.id)),{recursive:true,mode:0o700})
      return LinuxProtectedFiles.open(approvalClientDirectory(root,s.id),[work],true)
    })
    const key=project===ref.project?owner:credential(id(1),project,id(3))
    const scoped=project===ref.project?agent:credential(id(1),project,id(5))
    await files.write({formatVersion:1,origin:"http://127.0.0.1:45678",storeId:id(1),projectId:project,principalId:id(3),
      active:{credentialId:key.credentialId,token:key.token},agent:{principalId:id(5),credentialId:scoped.credentialId,token:scoped.token},pending:null})
    await fs.writeFile(path.join(approvalClientDirectory(root,s.id),"binding.json"),contextJSON({formatVersion:1,
      storeId:id(1),projectId:project,sessionId:s.id,directory:work,projectRoot:work,controllerId:id(7)}),{mode:0o600})
    await files.close()
  }
  await install(a);await install(b);await install(other,id(9))
  let sends=0,lost=true
  const fetcher:typeof fetch=async(_url,input)=>{
    if(String(_url).endsWith("/reads")){
      const operation=JSON.parse(String(input?.body)).operation
      if(operation==="read_review_bundle")return new Response(fixtureRaw)
      return new Response(JSON.stringify({formatVersion:1,kind:"read",basis:{algorithm:"sha1",hex:"a".repeat(40)},operation,
        data:{complete:true,nextCursor:null,snapshotId:randomUUID(),items:[{projectId:ref.project}]}}))
    }
    sends++
    if(lost)throw Error("MOCK lost canonical reply")
    const c=JSON.parse(String(input?.body))
    return new Response(JSON.stringify({formatVersion:1,kind:"settled",identity:{principalId:id(3),projectId:ref.project,
      operation:c.operation,requestKey:c.requestKey},fingerprint:"a".repeat(64),validatedAt:{algorithm:"sha1",hex:"b".repeat(40)},
      authenticatedByCredentialId:owner.credentialId,outcome:{kind:"success",operation:c.operation,result:c.payload.requestId,domainReplayed:false},
      commandReplayed:true,publication:null}))
  }
  const host={verify:async(s:Session,window:number)=>{
    assert.equal(window,1);assert.equal(s.key,`sidecar\n${s.id}`);assert.equal(s.directory,work);assert.equal(s.projectRoot,work)
  }}
  const clients=()=>new AutomaticApprovalClients(root,host,async()=>[work],fetcher)
  const custody=new LinuxNativeCustody(path.join(root,"approval-delivery"),[work]);await custody.initialize()
  return {root,work,a,b,other,clients,custody,delivery:new ApprovalDelivery(custody),sends:()=>sends,reply:()=>{lost=false}}
}
const open=(session:Session)=>({session,requestId:ref.requestId,requestDigest:ref.requestDigest,packetDigest:ref.packetDigest})

test("automatic clients are chat/project bound; exact handles and unknown answers never cross chats",async()=>{
  const f=await fixture(),clients=f.clients()
  try{
    const a=await clients.get(f.a,1),b=await clients.get(f.b,1),other=await clients.get(f.other,1)
    assert.notEqual(a.controller,b.controller);assert.equal(other.projectId,id(9))
    const view=await a.controller.dispatch("open",open(f.a),caller);assert.equal(view.code,"OK")
    const wrong=await b.controller.dispatch("answer",{session:f.b,handle:view.handle,choice:"approve",comments:[]},caller)
    assert.equal(wrong.ok,false);assert.equal(f.sends(),0)
    const pending=await a.controller.dispatch("answer",{session:f.a,handle:view.handle,choice:"approve",comments:[]},caller)
    assert.equal(pending.pending,true);assert.equal(f.sends(),1)
    const before=await fs.readFile(path.join(approvalClientDirectory(f.root,f.a.id),"profile.json"),"utf8")
    await clients.get(f.other,1);await clients.get(f.b,1)
    assert.equal(await fs.readFile(path.join(approvalClientDirectory(f.root,f.a.id),"profile.json"),"utf8"),before)
    assert.equal(JSON.parse(await fs.readFile(path.join(approvalClientDirectory(f.root,f.b.id),"profile.json"),"utf8")).pending,null)
    await assert.rejects(clients.get({...f.a,projectRoot:path.dirname(f.work)},1))
  }finally{await clients.close()}
})

test("delivery survives reopening, refuses cross-project/stale acknowledgements and is never an answer",async()=>{
  const f=await fixture()
  assert.equal((await f.delivery.request(f.a.id,ref)).state,"requested")
  const restarted=new ApprovalDelivery(f.custody)
  assert.deepEqual(await restarted.current(f.a.id,ref.project),ref)
  assert.equal(await restarted.current(f.b.id,ref.project),undefined)
  await assert.rejects(restarted.current(f.a.id,id(9)))
  await assert.rejects(restarted.acknowledge(f.a.id,{...ref,packetDigest:"f".repeat(64)}))
  await restarted.acknowledge(f.a.id,ref)
  assert.equal((await restarted.request(f.a.id,ref)).state,"rendered")
  assert.equal(f.sends(),0)
  const next={...ref,requestId:randomUUID()}
  await restarted.request(f.a.id,next)
  await restarted.acknowledge(f.a.id,ref) // late acknowledgement stays on its old exact request
  assert.equal(await restarted.shown(f.a.id,next),false)
})

test("multiple prepared contracts and a cut before pointer publication retain every unrendered intent",async()=>{
  const f=await fixture(),next={...ref,requestId:randomUUID()}
  await f.delivery.request(f.a.id,ref);await f.delivery.request(f.a.id,next)
  assert.deepEqual(await f.delivery.current(f.a.id,ref.project),next)
  await f.delivery.acknowledge(f.a.id,next)
  assert.deepEqual(await f.delivery.current(f.a.id,ref.project),ref)
  await f.delivery.acknowledge(f.a.id,ref)
  const reference=approvalReference(ref),key=contextJSON({sessionID:f.b.id,reference})
  await f.custody.retain("approval-open",key,contextJSON({formatVersion:1,sessionID:f.b.id,reference}))
  assert.deepEqual(await new ApprovalDelivery(f.custody).current(f.b.id,ref.project),ref)
})

test("rendered explicit B stays selected while pending A remains recoverable across restart",async()=>{
  const f=await fixture(),next={...ref,requestId:randomUUID()}
  await f.delivery.request(f.a.id,ref)
  const before=await f.custody.scan("approval-open")
  await f.delivery.request(f.a.id,next)
  assert.deepEqual(await f.delivery.selected(f.a.id,ref.project),next)
  await f.delivery.acknowledge(f.a.id,next)
  const restarted=new ApprovalDelivery(f.custody)
  assert.deepEqual(await restarted.selected(f.a.id,ref.project),next)
  assert.equal(await restarted.shown(f.a.id,next),true)
  assert.equal(await restarted.shown(f.a.id,ref),false)
  assert.deepEqual(await restarted.current(f.a.id,ref.project),ref)
  const retained=await f.custody.scan("approval-open")
  assert.ok(before.every(entry=>retained.some(item=>item.keyDigest===entry.keyDigest&&item.raw===entry.raw)))
  assert.equal(await restarted.selected(f.b.id,ref.project),undefined)
  await assert.rejects(restarted.selected(f.a.id,id(9)))
  await assert.rejects(restarted.acknowledge(f.b.id,next))
  assert.equal(f.sends(),0)
})

test("model-free exact intent -> bound controller -> readable panel -> rendered ack; only explicit owner click sends answer",async()=>{
  const f=await fixture(),clients=f.clients()
  let dispose=()=>{},root!:HTMLElement,acknowledged=0
  try{
    await f.delivery.request(f.a.id,ref)
    const target=await clients.get(f.a,1)
    const {document,window}=parseHTML("<html><body></body></html>")
    Object.assign(globalThis,{document,window})
    const bridge:any={state:()=>undefined,on:()=>()=>{}}
    for(const method of ["status","pending","open","answer","dismiss","reconcile"])
      bridge[method]=(input:any)=>target.controller.dispatch(method as keyof typeof Inputs,input,caller)
    for(const method of ["connect","setup","rotate"])bridge[method]=async()=>reply("FORBIDDEN")
    createRoot(d=>{
      dispose=d
      root=nativePanel(()=>f.a,bridge,()=>ref,async(session,exact)=>{
        await f.delivery.acknowledge(session.id,exact);acknowledged++
      });document.body.append(root)
    })
    const deadline=Date.now()+5000
    while(acknowledged===0&&Date.now()<deadline)await new Promise(resolve=>setImmediate(resolve))
    assert.equal(acknowledged,1,root.textContent??"no panel content");assert.equal(await f.delivery.shown(f.a.id,ref),true)
    assert.equal(f.sends(),0)
    assert.ok(root.textContent?.includes(request.payload.packet.question))
    const canonical=[...root.querySelectorAll("h3")].find(h=>h.textContent==="Complete canonical packet, material and original attribution")!
    assert.equal(canonical.closest("details")!.hasAttribute("open"),false)
    assert.equal(canonical.nextElementSibling!.textContent,fixtureRaw)
    assert.equal(root.querySelectorAll('input[type="radio"]').length,0)
    const submit=[...root.querySelectorAll("button")].find(b=>b.textContent==="Утвердить")!
    f.reply();submit.click()
    while(f.sends()===0&&Date.now()<deadline)await new Promise(resolve=>setImmediate(resolve))
    assert.equal(f.sends(),1)
  }finally{dispose();await clients.close()}
})

test("pending original answer reconciles after client restart without another key",async()=>{
  const f=await fixture();let clients=f.clients()
  try{
    const target=await clients.get(f.a,1),view=await target.controller.dispatch("open",open(f.a),caller)
    const uncertain:Reply=await target.controller.dispatch("answer",{session:f.a,handle:view.handle,choice:"approve",comments:[]},caller)
    assert.equal(uncertain.pending,true)
    const file=path.join(approvalClientDirectory(f.root,f.a.id),"profile.json")
    const original=JSON.parse(await fs.readFile(file,"utf8")).pending.commandJSON
    await clients.close();clients=f.clients();f.reply()
    const restarted=await clients.get(f.a,1)
    const reconciled=await restarted.controller.dispatch("reconcile",{session:f.a},caller)
    assert.equal(reconciled.code,"OK");assert.equal(reconciled.pending,false)
    assert.equal(JSON.parse(reconciled.resultJSON).identity.requestKey,JSON.parse(original).requestKey)
    assert.equal(f.sends(),2)
  }finally{await clients.close()}
})
