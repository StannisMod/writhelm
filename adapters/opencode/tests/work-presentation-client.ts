// SPDX-License-Identifier: AGPL-3.0-only
// REAL checked service/controller/DOM; external native window/session identity is explicit MOCK.
// Public deterministic fixture credentials arrive only on stdin, never ordinary logs or renderer.
import assert from "node:assert/strict"
import path from "node:path"
import { createRoot } from "solid-js"
import { parseHTML } from "linkedom"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review,reply,type Session } from "../src/contracts.js"
import { Controller } from "../src/controller.js"
import { openProtected } from "../src/protected.js"
import { workPanel } from "../src/work-panel.js"

let input="";for await(const chunk of process.stdin){input+=chunk;assert.ok(Buffer.byteLength(input)<=16384)}
const cfg=JSON.parse(input),session:Session={key:"sidecar\nses_work_ui_fixture",id:"ses_work_ui_fixture",server:"sidecar",directory:cfg.workRoot,projectRoot:cfg.workRoot}
const files=await openProtected(path.join(cfg.root,"human-client"),[cfg.workRoot],true)
await files.write({formatVersion:1,origin:cfg.origin,storeId:cfg.storeId,projectId:cfg.projectId,principalId:cfg.ownerPrincipal,
  active:{credentialId:cfg.ownerKey,token:cfg.ownerToken},agent:{principalId:cfg.agentPrincipal,credentialId:cfg.agentKey,token:cfg.agentToken},pending:null})
const transportDiagnostics:unknown[]=[]
const tracedFetch:typeof fetch=async(url,init)=>{
  const response=await fetch(url,init),frame=JSON.parse(await response.clone().text())
  transportDiagnostics.push({operation:JSON.parse(String(init?.body)).operation,status:response.status,kind:frame.kind,code:frame.code})
  return response
}
const controller=new Controller({verify:async(actual,window)=>{assert.equal(window,1);assert.deepEqual(actual,session)}},tracedFetch)
await controller.attach(files)
const caller={window:1,signal:new AbortController().signal},unused=async()=>reply("UNCONFIGURED")
const ipcReplies:unknown[]=[]
const bridge:IpcClient<typeof Review.spec>={state:()=>undefined,on:()=>()=>{},status:unused,connect:unused,pending:unused,open:unused,
  answer:unused,reconcile:unused,rotate:unused,setup:unused,dismiss:unused,
  workList:async i=>{const r=await controller.dispatch("workList",i,caller);ipcReplies.push(r);return r},
  workBody:async i=>{const r=await controller.dispatch("workBody",i,caller);ipcReplies.push(r);return r},
  workOpen:async i=>{const r=await controller.dispatch("workOpen",i,caller);ipcReplies.push(r);return r}}
const {document,window}=parseHTML("<html><body></body></html>");Object.assign(globalThis,{document,window})
let dispose!:()=>void,root!:HTMLElement
createRoot(d=>{dispose=d;root=workPanel(()=>session,bridge);document.body.append(root)})
const until=async(f:()=>boolean)=>{const deadline=performance.now()+15000;while(!f()){assert.ok(performance.now()<deadline,"bounded native work component wait");await new Promise(r=>setTimeout(r,20))}}
try{
  await until(()=>[...root.querySelectorAll("button")].some(b=>b.textContent!.startsWith("Open exact assignments")))
  ;[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open exact assignments")&&b.textContent!.includes(cfg.assignmentId))!.click()
  await until(()=>root.querySelectorAll("details").length===1)
  assert.ok(root.textContent!.includes("Canonical state — not physical completion"))
  assert.ok(root.textContent!.includes("paused"))
  assert.ok(root.textContent!.includes("Complete original service JSON"))
  assert.equal(JSON.stringify(ipcReplies).includes(cfg.ownerToken),false)
  assert.equal(root.textContent!.includes(cfg.ownerToken),false)
  assert.equal(root.querySelectorAll('input[type="radio"]').length,0)
  const family=root.querySelector("select")!
  const choose=async(name:string)=>{
    for(const option of family.querySelectorAll("option"))option.removeAttribute("selected")
    family.querySelector(`option[value="${name}"]`)!.setAttribute("selected","")
    assert.equal(family.value,name)
    family.dispatchEvent(new window.Event("change"))
    await until(()=>!root.querySelector('[role="status"]')!.textContent!.includes("Reading authorized"))
    assert.ok(root.querySelector('[role="status"]')!.textContent!.includes(name+":"),
      `family ${name}: ${root.querySelector('[role="status"]')!.textContent}; transport ${JSON.stringify(transportDiagnostics)}`)
  }
  ;[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open referenced exact checkpoints"))!.click()
  await until(()=>root.textContent!.includes("Handoff — not result acceptance"))
  ;[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Read exact attributed body"))!.click()
  await until(()=>root.textContent!.includes("unfinished; actual owned dirty snapshot captured"))
  assert.ok(root.textContent!.includes("Attributed body — not a norm, verification or acceptance"))
  await choose("conditions")
  ;[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open exact conditions"))!.click()
  // Project owner scope is NOT an own-controller binding. Listing a selector is not authority to
  // inspect its controller-private assessment. Preserve the real refusal, never switch to agent key.
  await until(()=>root.querySelector('[role="status"]')!.textContent!.includes("FORBIDDEN"))
  assert.equal(root.querySelectorAll("details").length,0)
  assert.equal(root.textContent!.includes("PRIVATE_CONDITION_SENTINEL"),false)
  await choose("acceptances")
  assert.equal([...root.querySelectorAll("button")].filter(b=>b.textContent!.startsWith("Open exact")).length,0)
  assert.equal(JSON.stringify(ipcReplies).includes(cfg.ownerToken),false)
  process.stdout.write(JSON.stringify({workPresentation:"passed",nativeHost:"MOCK",service:"REAL",secretInRenderer:false,automaticMutation:false}))
}finally{dispose();await controller.close()}
