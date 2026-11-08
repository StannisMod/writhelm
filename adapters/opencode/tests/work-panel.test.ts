// SPDX-License-Identifier: AGPL-3.0-only
// DOM/IPC fixture only, not installed OpenCode rendering/interaction qualification.
import test from "node:test"
import assert from "node:assert/strict"
import { createRoot,createSignal } from "solid-js"
import { parseHTML } from "linkedom"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review,reply,type Session } from "../src/contracts.js"
import { workPanel } from "../src/work-panel.js"

const id="00000000-0000-0000-0000-000000000001",next="00000000-0000-0000-0000-000000000002"
const session:Session={ key:"sidecar\nses_work",id:"ses_work",server:"sidecar",directory:"/synthetic-work",projectRoot:"/synthetic-work" }
const key=`{"projectId":"${id}","id":"${next}"}`
const read=(operation:string,data:string)=>`{"formatVersion":2,"kind":"read","basis":{"algorithm":"sha1","hex":"${"a".repeat(40)}"},"operation":"${operation}","data":${data}}`
const turn=async()=>{for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r))}
function mock(workList:IpcClient<typeof Review.spec>["workList"],workOpen:IpcClient<typeof Review.spec>["workOpen"],workBody?:IpcClient<typeof Review.spec>["workBody"]):IpcClient<typeof Review.spec> {
  const unused=async()=>reply("UNCONFIGURED")
  return { state:()=>undefined,on:()=>()=>{},status:unused,connect:unused,pending:unused,open:unused,answer:unused,
    reconcile:unused,rotate:unused,setup:unused,dismiss:unused,workList,workOpen,workBody:workBody??unused }
}
test("native work view keeps exact Int64 selectors, partial pages and distinct workflow states",async()=>{
  const {document,window}=parseHTML("<html><body></body></html>");Object.assign(globalThis,{document,window})
  const calls:any[]=[]
  const bridge=mock(async input=>{
    calls.push({method:"list",...input})
    const data=input.cursor?`{"snapshotId":"${id}","complete":true,"nextCursor":null,"items":[]}`:
      `{"snapshotId":"${id}","complete":false,"nextCursor":"${next}","items":[{"family":"assignments","key":${key},"generation":9007199254740993}]}`
    return reply("OK",{pageJSON:read("list_work_records",data)})
  },async input=>{
    calls.push({method:"open",...input})
    return reply("OK",{bundleJSON:read("read_assignments",`{"formatVersion":1,"family":"assignments","key":${key},"payload":{"state":"unknown","generation":9007199254740993,"executionGeneration":9223372036854775807,"pending":[]}}`)})
  })
  let dispose!:()=>void,root!:HTMLElement
  createRoot(d=>{dispose=d;root=workPanel(()=>session,bridge);document.body.append(root)})
  try{
    await turn();assert.ok(root.textContent!.includes("PARTIAL"))
    const open=[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open exact"))!;open.click();await turn()
    assert.equal(calls.find(c=>c.method==="open").generation,"9007199254740993")
    assert.ok(root.textContent!.includes("9223372036854775807"));assert.ok(root.textContent!.includes("unknown"))
    assert.ok(root.textContent!.includes("not physical completion"));assert.equal(root.querySelectorAll('input[type="radio"]').length,0)
    const more=[...root.querySelectorAll("button")].find(b=>b.textContent==="Next page of the same captured snapshot")!;more.click();await turn()
    assert.equal(calls.filter(c=>c.method==="list")[1].cursor,next)
    assert.ok(root.textContent!.includes("Selector snapshot complete"))
  }finally{dispose()}
})
test("native work view discards an old session's delayed record and renders denial without stale content",async()=>{
  const {document,window}=parseHTML("<html><body></body></html>");Object.assign(globalThis,{document,window})
  let complete!:(value:ReturnType<typeof reply>)=>void,denied=false
  const bridge=mock(async()=>denied?reply("FORBIDDEN"):reply("OK",{pageJSON:read("list_work_records",
    `{"snapshotId":"${id}","complete":true,"nextCursor":null,"items":[{"family":"assignments","key":${key},"generation":0}]}`)}),
    ()=>new Promise(resolve=>{complete=resolve}))
  let dispose!:()=>void,change!:(s:Session)=>void,root!:HTMLElement
  createRoot(d=>{dispose=d;const [current,setCurrent]=createSignal(session);change=setCurrent;root=workPanel(current,bridge);document.body.append(root)})
  try{
    await turn();[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open exact"))!.click();await turn()
    change({...session,key:"sidecar\nses_other",id:"ses_other"});await turn()
    complete(reply("OK",{bundleJSON:read("read_assignments",`{"formatVersion":1,"family":"assignments","key":${key},"payload":{"state":"OLD_SENSITIVE_RECORD"}}`)}));await turn()
    assert.equal(root.textContent!.includes("OLD_SENSITIVE_RECORD"),false)
    denied=true;[...root.querySelectorAll("button")].find(b=>b.textContent==="Refresh current authorized snapshot")!.click();await turn()
    assert.ok(root.textContent!.includes("FORBIDDEN"));assert.equal(root.querySelectorAll("details").length,0)
  }finally{dispose()}
})
test("native work view serializes pages and only the newest exact selection can render",async()=>{
  const {document,window}=parseHTML("<html><body></body></html>");Object.assign(globalThis,{document,window})
  let pages=0,finishPage!:(r:ReturnType<typeof reply>)=>void
  const opens:Array<(r:ReturnType<typeof reply>)=>void>=[]
  const bridge=mock(async()=>{
    pages++
    if(pages>1)return new Promise(resolve=>{finishPage=resolve})
    return reply("OK",{pageJSON:read("list_work_records",`{"snapshotId":"${id}","complete":false,"nextCursor":"${next}","items":[{"family":"assignments","key":${key},"generation":0}]}`)})
  },()=>new Promise(resolve=>{opens.push(resolve)}))
  let dispose!:()=>void,root!:HTMLElement
  createRoot(d=>{dispose=d;root=workPanel(()=>session,bridge);document.body.append(root)})
  try{
    await turn()
    const open=[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open exact"))!
    open.click();open.click();await turn()
    opens[1]!(reply("OK",{bundleJSON:read("read_assignments",`{"payload":{"state":"NEW_SELECTION"}}`)}));await turn()
    opens[0]!(reply("OK",{bundleJSON:read("read_assignments",`{"payload":{"state":"OLD_SELECTION"}}`)}));await turn()
    assert.ok(root.textContent!.includes("NEW_SELECTION"));assert.equal(root.textContent!.includes("OLD_SELECTION"),false)
    open.click();await turn();assert.equal(root.querySelectorAll("details").length,0)
    opens[2]!(reply("FORBIDDEN"));await turn();assert.ok(root.textContent!.includes("FORBIDDEN"))
    const more=[...root.querySelectorAll("button")].find(b=>b.textContent==="Next page of the same captured snapshot")!
    more.click();more.click();await turn();assert.equal(pages,2)
    finishPage(reply("OK",{pageJSON:read("list_work_records",`{"snapshotId":"${id}","complete":true,"nextCursor":null,"items":[]}`)}));await turn()
    assert.ok(root.textContent!.includes("1 selectors"))
  }finally{dispose()}
})
test("native work body display is an explicit exact-parent read and never interprets report text as markup or assent",async()=>{
  const {document,window}=parseHTML("<html><body></body></html>");Object.assign(globalThis,{document,window})
  const digest="b".repeat(64),calls:any[]=[]
  const bridge=mock(async()=>reply("OK",{pageJSON:read("list_work_records",`{"snapshotId":"${id}","complete":true,"nextCursor":null,"items":[{"family":"assignments","key":${key},"generation":9007199254740993}]}`)}),
    async()=>reply("OK",{bundleJSON:read("read_assignments",`{"payload":{"report":{"digest":"${digest}","byteLength":29}}}`)}),
    async input=>{calls.push(input);return reply("OK",{bundleJSON:read("read_work_body",JSON.stringify({digest,byteLength:29,body:"<script>not an assent</script>"}))})})
  let dispose!:()=>void,root!:HTMLElement
  createRoot(d=>{dispose=d;root=workPanel(()=>session,bridge);document.body.append(root)})
  try{
    await turn();[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Open exact"))!.click();await turn()
    assert.equal(calls.length,0)
    ;[...root.querySelectorAll("button")].find(b=>b.textContent!.startsWith("Read exact attributed body"))!.click();await turn()
    assert.equal(calls[0].generation,"9007199254740993");assert.equal(calls[0].bodyDigest,digest)
    assert.ok(root.textContent!.includes("<script>not an assent</script>"));assert.equal(root.querySelectorAll("script").length,0)
    assert.ok(root.textContent!.includes("not a norm, verification or acceptance"))
  }finally{dispose()}
})
