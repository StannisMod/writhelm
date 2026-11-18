// SPDX-License-Identifier: AGPL-3.0-only
// Built GUI + actual native-main/protected storage. Native host/session and HTTP authority are
// explicit doubles; no installed Electron window, service publication or model call is claimed.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import { readFileSync } from "node:fs"
import path from "node:path"
import { createHash } from "node:crypto"
import { createRequire } from "node:module"
import { runInNewContext } from "node:vm"
import { createRoot } from "solid-js"
import * as solid from "solid-js"
import * as effect from "effect"
import * as sdk from "@opencode/plugin/gui"
import { parseHTML } from "linkedom"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxProtectedFiles } from "../src/protected.js"
import { approvalClientDirectory } from "../src/automatic-approval.js"
import { credential } from "../src/helper.js"
import { NativeOwnerOrder } from "../src/native-owner-order.js"
import { NativeLaunchAdmission } from "../src/native-launch-admission.js"
import type { NativeCredentialRegistry } from "../src/native-credentials.js"
import { contextJSON } from "../src/native-context-codec.js"

const require=createRequire(import.meta.url),id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
const sha=(raw:string)=>createHash("sha256").update(raw).digest("hex")
const pause=()=>new Promise(resolve=>setImmediate(resolve))
test("built owner panel requires explicit exact native selection; same persisted choice admits/restores launch scope",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"owner-ui-")),work=path.join(area,"work"),root=path.join(area,"private")
  await fs.mkdir(work);await fs.mkdir(root,{mode:0o700})
  const session={key:"sidecar\nses_owner_ui",id:"ses_owner_ui",server:{id:"sidecar"},directory:work,project:{worktree:work}}
  const owner=credential(id(1),id(2),id(3)),agent=credential(id(1),id(2),id(4))
  const clientRoot=approvalClientDirectory(root,session.id)
  await fs.mkdir(path.dirname(clientRoot),{recursive:true,mode:0o700})
  const files=await LinuxProtectedFiles.open(clientRoot,[work],true)
  await files.write({formatVersion:1,origin:"http://127.0.0.1:18679",storeId:id(1),projectId:id(2),principalId:id(3),
    active:{credentialId:owner.credentialId,token:owner.token},agent:{principalId:id(4),credentialId:agent.credentialId,token:agent.token},pending:null})
  await files.close()
  await fs.writeFile(path.join(clientRoot,"binding.json"),contextJSON({formatVersion:1,storeId:id(1),projectId:id(2),sessionId:session.id,
    directory:work,projectRoot:work,controllerId:id(5)}),{mode:0o600})
  const record={formatVersion:1,family:"task-revisions",key:{projectId:id(2),taskId:id(6),revision:0},
    payload:{objective:"Exact UI task <not executable HTML>",constraints:["Preserve original effects"],state:"executable"}}
  const raw=contextJSON(record),plan=contextJSON({formatVersion:1,family:"dispatch-plans",key:{projectId:id(2),id:id(7)},payload:{entries:[]}})
  const custody=new LinuxNativeCustody(path.join(root,"owner-orders"),[work]);await custody.initialize()
  const order=new NativeOwnerOrder(custody,{storeId:id(1),projectId:id(2),controllerId:id(5),sessionID:session.id})
  const proposal=await order.propose({proposalId:id(8),planRefJSON:contextJSON({family:"dispatch-plans",key:{projectId:id(2),id:id(7)},digest:sha(plan)}),
    tasks:[{taskJSON:contextJSON({family:"task-revisions",key:record.key,digest:sha(raw)}),after:[]}]})
  const {document,window}=parseHTML("<html><body></body></html>")
  Object.assign(globalThis,{document,window})
  const provided=new Map<any,any>(),panels:any[]=[],opens:any[]=[],timers=new Set<()=>void>()
  let selects=0,selectedReply=false,dispose=()=>{},disposePanel=()=>{},close=async()=>{},allowRead=true
  const fetcher:typeof fetch=async(_url,input)=>{
    const request=JSON.parse(String(input?.body))
    if(request.operation!=="read_task_revisions"||!allowRead)return new Response('{"formatVersion":2,"kind":"error","code":"FORBIDDEN"}')
    assert.equal(request.subject.digest,sha(raw))
    assert.equal(new Headers(input?.headers).get("Authorization"),"Bearer "+owner.token)
    return new Response(contextJSON({formatVersion:2,kind:"read",basis:{algorithm:"sha1",hex:"a".repeat(40)},operation:request.operation,data:record}))
  }
  const sandbox:any={console,process:{...process,env:{...process.env,WRITHELM_PRIMARY_ROOT:root}},Buffer,URL,TextEncoder,TextDecoder,
    document,window,AbortController,AbortSignal,fetch:fetcher,performance,setTimeout,clearTimeout,
    setInterval:(fn:()=>void)=>{timers.add(fn);return fn},clearInterval:(fn:()=>void)=>timers.delete(fn),module:{exports:{}},exports:{}}
  sandbox.exports=sandbox.module.exports
  sandbox.require=(name:string)=>name==="@opencode/plugin/gui"?sdk:name==="solid-js"?solid:name==="effect"?effect:name==="electron"?{dialog:{}}:require(name)
  runInNewContext(readFileSync(new URL("../../dist/gui-experimental.cjs",import.meta.url),"utf8"),sandbox)
  const definition=sandbox.module.exports.default??sandbox.module.exports
  const caller={window:1,signal:new AbortController().signal}
  try{
    close=await definition.main({app:{platform:"desktop",version:"2.2.5"},options:{},
      client:{session:{get:async()=>({id:session.id,location:{directory:work}})},project:{list:async()=>[{canonical:work,sandboxes:[]}]}},
      windows:{get:()=>({}),on:()=>{}},storage:{store:()=>({value:{root:""},set(){}})},provide:(token:any,value:any)=>provided.set(token,value)})
    createRoot(d=>{
      dispose=d
      definition.setup({app:{platform:"desktop",version:"2.2.5"},id:"writhelm-opencode",sessions:{current:()=>session},
        add:(token:any,item:any)=>{if(token===sdk.Panel)panels.push(item)},
        main:(token:any)=>new Proxy({},{get:(_target,method)=>async(input:any)=>{
          if(method==="select")selects++
          const result=await provided.get(token)[method](input,caller)
          if(method==="select")selectedReply=result.ok
          return result
        }}),layout:{open:(key:any)=>opens.push(key)},toast:{show:()=>({dismiss(){}})}})
    })
    const deadline=Date.now()+5000
    while(!opens.includes("writhelm-opencode:owner-order")&&Date.now()<deadline)await pause()
    assert.ok(opens.includes("writhelm-opencode:owner-order"))
    assert.equal(selects,0);assert.equal((await order.pending())!.selected,false)
    let panel!:HTMLElement
    createRoot(d=>{disposePanel=d;panel=panels.find(p=>p.id==="owner-order").render({session,tab:{id:"owner-order"},screen:{}});document.body.append(panel)})
    while(!panel.querySelector("button")&&Date.now()<deadline)await pause()
    assert.ok(panel.textContent?.includes(record.payload.objective));assert.equal(panel.querySelector("not"),null)
    assert.equal(selects,0,"render/poll never chooses an order")
    panel.querySelector("button")!.click()
    while(!selectedReply&&Date.now()<deadline)await pause()
    assert.equal(selectedReply,true)
    assert.equal(selects,1);assert.equal((await order.pending())!.selected,true)
    const restored=new NativeLaunchAdmission(new NativeOwnerOrder(custody,{storeId:id(1),projectId:id(2),controllerId:id(5),sessionID:session.id}),
      {sourceIdentity:async()=>({}),dispatchContext:async()=>({planJSON:plan,entries:[{taskJSON:raw}]})} as unknown as NativeCredentialRegistry,session.id,id(2))
    await restored.task(record.key,caller.signal)
    assert.equal((await order.pending())!.selection.proposalId,proposal.proposalId)
    // Revoke this synthetic view's source access: it cannot publish another owner choice.
    allowRead=false
    const native= [...provided.values()].find(p=>p.select&&p.view)
    const rejected=await native.select({session:{key:session.key,id:session.id,server:"sidecar",directory:work,projectRoot:work},proposalId:proposal.proposalId,
      proposalDigest:proposal.proposalDigest,expectedDecision:(await order.current()).decisionDigest},caller)
    assert.equal(rejected.ok,false)
  }finally{disposePanel();dispose();await close()}
  assert.equal(timers.size,0)
})
