// SPDX-License-Identifier: AGPL-3.0-only
// Executes the built production GUI in synthetic DOM. Not a claim of installed Electron rendering.
import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { createRequire } from "node:module"
import { runInNewContext } from "node:vm"
import { createRoot, createSignal } from "solid-js"
import * as solid from "solid-js"
import * as effect from "effect"
import * as sdk from "@opencode/plugin/gui"
import { parseHTML } from "linkedom"
import { reply } from "../src/contracts.js"
import * as fs from "node:fs/promises"
import path from "node:path"
import { ApprovalDelivery } from "../src/approval-delivery.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"

const require=createRequire(import.meta.url)
const raw=readFileSync(new URL("../../tests/fixtures/bundle.json",import.meta.url),"utf8"),record=JSON.parse(raw).data.request
const ref={project:record.payload.projectId,requestId:record.key,requestDigest:record.payload.requestDigest,packetDigest:record.payload.packetDigest}
const tick=async()=>{for(let n=0;n<12;n++)await new Promise(resolve=>setImmediate(resolve))}
function fixture(delayed=false,journal?:ApprovalDelivery){
  const {document,window}=parseHTML("<html><body></body></html>")
  Object.assign(globalThis,{document,window})
  const original={key:"sidecar\nses_gui_a",id:"ses_gui_a",server:{id:"sidecar"},directory:"/synthetic/project",project:{worktree:"/synthetic/project"}}
  const opens:any[]=[],closes:any[]=[],toggles:any[]=[],requests:any[]=[],acks:any[]=[],reads:any[]=[],contributions:any[]=[],timers=new Set<()=>void>()
  let resolve!:(value:any)=>void,dispose=()=>{},disposePanel=()=>{},change!:(s:typeof original)=>void,answers=0,ackFails=false
  const bridge={
    requested:async(input:any)=>{requests.push(input)
      if(journal){
        const selected=await journal.selected(input.session.id,ref.project)
        return reply("OK",{pageJSON:selected&&!await journal.shown(input.session.id,selected)?JSON.stringify(selected):""})
      }
      return delayed&&input.session.id===original.id?
      new Promise(r=>{resolve=r}):reply("OK",{pageJSON:input.session.id===original.id?JSON.stringify(ref):""})},
    shown:async(input:any)=>{acks.push(input)
      if(journal)await journal.acknowledge(input.session.id,{...ref,requestId:input.requestId,requestDigest:input.requestDigest,packetDigest:input.packetDigest})
      return reply(ackFails?"UNAVAILABLE":"OK")},
    status:async()=>reply("OK",{pageJSON:JSON.stringify({formatVersion:1,kind:"read",basis:{algorithm:"sha1",hex:"a".repeat(40)},
      operation:"list_projects",data:{complete:true,nextCursor:null,snapshotId:"00000000-0000-0000-0000-000000000777",items:[{projectId:ref.project}]}})}),
    open:async(input:any)=>{reads.push(input);return reply("OK",{handle:"00000000-0000-0000-0000-000000000899",bundleJSON:raw})},
    pending:async()=>reply(),answer:async()=>{answers++;return reply()},dismiss:async()=>reply(),
    reconcile:async()=>reply(),rotate:async()=>reply(),setup:async()=>reply(),connect:async()=>reply(),
    load:async()=>reply("OK",{pageJSON:JSON.stringify({digest:"",comments:[],reason:""})}),
    save:async()=>reply("OK",{pageJSON:JSON.stringify({digest:"a".repeat(64)})}),
  }
  // No process global: renderer evaluation must not initialize Node custody imported by main.
  const sandbox:any={console,Buffer,URL,TextDecoder,TextEncoder,document,window,setTimeout,clearTimeout,
    setInterval:(fn:()=>void)=>{timers.add(fn);return fn},clearInterval:(fn:()=>void)=>timers.delete(fn),module:{exports:{}},exports:{}}
  sandbox.exports=sandbox.module.exports
  sandbox.require=(id:string)=>id==="@opencode/plugin/gui"?sdk:id==="solid-js"?solid:id==="effect"?effect:
    id==="electron"?{dialog:{}}:require(id)
  runInNewContext(readFileSync(new URL("../../dist/gui-experimental.cjs",import.meta.url),"utf8"),sandbox)
  const definition=sandbox.module.exports.default??sandbox.module.exports
  createRoot(d=>{
    dispose=d;const [current,set]=createSignal(original);change=set
    definition.setup({app:{platform:"desktop",version:"2.2.5"},id:"writhelm-opencode",
      sessions:{current},add:(token:any,item:any)=>contributions.push({token,item}),main:()=>bridge,
      layout:{open:(key:any,session:any)=>opens.push({key,session}),
        close:(key:any,session:any)=>closes.push({key,session}),toggle:(key:any,session:any)=>toggles.push({key,session})},toast:{show:()=>({dismiss(){}})}})
  })
  return {original,opens,closes,toggles,contributions,window,requests,acks,reads,change,timers,answers:()=>answers,
    release:()=>resolve(reply("OK",{pageJSON:JSON.stringify(ref)})),failAck:()=>{ackFails=true},
    render(){const panel=contributions.find(x=>x.token===sdk.Panel).item;let root!:HTMLElement
      createRoot(d=>{disposePanel=d;root=panel.render({session:original,tab:{id:"review"},screen:{}});document.body.append(root)});return root},
    close(){disposePanel();dispose()},
  }
}
test("active tab label and repeated exact link close through native layout without a decision",async()=>{
  const f=fixture()
  try{
    await tick()
    const panel=f.contributions.find(x=>x.token===sdk.Panel).item
    const tab=panel.list({open:["review"]})[0]
    const label=tab.label({active:true,preview:false,session:f.original})
    label.dispatchEvent(new f.window.Event("click",{bubbles:true,cancelable:true}))
    assert.equal(f.closes.length,1);assert.equal(f.closes[0].session.id,f.original.id)
    const handler=f.contributions.find(x=>x.token===sdk.LinkHandler).item
    handler.open({href:`writhelm-review/v1/${ref.project}/${ref.requestId}/${ref.requestDigest}/${ref.packetDigest}`,session:f.original})
    assert.equal(f.toggles.length,1);assert.equal(f.answers(),0)
    assert.equal(f.reads.length,0,"closing does not submit or reopen content")
  }finally{f.close()}
})
test("built GUI automatically opens exact routed-chat panel and acknowledges only loaded material",async()=>{
  const f=fixture()
  try{
    await tick();assert.equal(f.opens.length,1);assert.equal(f.opens[0].key,"writhelm-opencode:review")
    assert.equal(f.opens[0].session.key,f.original.key);assert.equal(f.acks.length,0);assert.equal(f.answers(),0)
    const root=f.render();await tick()
    assert.equal(f.reads.length,1);assert.equal(f.reads[0].requestDigest,ref.requestDigest)
    assert.equal(f.acks.length,1);assert.equal(f.acks[0].session.id,f.original.id)
    assert.equal(root.querySelector("fieldset")!.disabled,false);assert.equal(f.answers(),0)
    for(const timer of f.timers)timer();await tick()
    assert.equal(f.opens.length,1,"polling must not repeatedly steal focus")
  }finally{f.close()}
  assert.equal(f.timers.size,0)
})
test("late delivery for another routed chat never opens or remaps its panel",async()=>{
  const f=fixture(true)
  try{
    f.change({...f.original,key:"sidecar\nses_gui_b",id:"ses_gui_b"});await tick()
    f.release();await tick();assert.equal(f.opens.length,0);assert.equal(f.acks.length,0);assert.equal(f.answers(),0)
  }finally{f.close()}
  assert.equal(f.timers.size,0)
})
test("render acknowledgement loss does not submit, discard or disable a complete exact decision",async()=>{
  const f=fixture()
  try{
    await tick();f.failAck();const root=f.render();await tick()
    assert.equal(f.acks.length,1);assert.equal(f.answers(),0)
    assert.equal(root.querySelector("fieldset")!.disabled,false)
    assert.ok(root.textContent?.includes("acknowledgement is held"))
  }finally{f.close()}
})

test("rendering selected B never automatically opens retained pending A",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"gui-selection-"))
  const work=path.join(area,"work");await fs.mkdir(work)
  const custody=new LinuxNativeCustody(path.join(area,"private"),[work])
  await custody.initialize()
  const journal=new ApprovalDelivery(custody),sessionID="ses_gui_a"
  const older={...ref,requestId:"00000000-0000-0000-0000-000000000778"}
  await journal.request(sessionID,older);await journal.request(sessionID,ref)
  const f=fixture(false,journal)
  try{
    for(let n=0;n<100&&!f.opens.length;n++)await tick()
    assert.equal(f.opens.length,1);assert.equal(f.acks.length,0)
    assert.equal(await journal.shown(sessionID,ref),false,"no shown receipt before load")
    f.render();await tick()
    assert.equal(f.acks.length,1)
    for(let n=0;n<100&&!await journal.shown(sessionID,ref);n++)await tick()
    assert.equal(await journal.shown(sessionID,ref),true)
    for(let n=0;n<3;n++){for(const timer of f.timers)timer();await tick()}
    assert.equal(f.opens.length,1,"backlog must not steal the selected panel")
    assert.equal(f.reads.length,1);assert.equal(f.reads[0].requestId,ref.requestId)
    assert.equal(await journal.shown(sessionID,older),false)
    assert.deepEqual(await journal.current(sessionID,ref.project),older)
    assert.equal(f.answers(),0)
  }finally{f.close()}
})
