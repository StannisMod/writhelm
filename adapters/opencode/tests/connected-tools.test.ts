// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { boundedWorkChannel, boundedDispatchAttemptChannel, LinuxWorkRequestCustody, registerConnectedWorkTools } from "../src/connected-tools.js"
import { exactJSON } from "../src/strict-json.js"
import { OwnerOrderHold } from "../src/native-owner-order.js"

type MockTool = { name: string; execute(input: unknown, actual: ToolContext): Promise<{ content?: unknown }> }
test("operation help specializes only the requesting chat's work schema; typed executor retains exact Int64 wire",async()=>{
  const tools=new Map<string,any>(),sent:string[]=[];let hook!:(event:any)=>void
  const id="00000000-0000-0000-0000-000000000001"
  const ctx={
    session:{hook:async(_name:string,callback:any)=>{hook=callback;return {dispose:async()=>{}}}},
    tool:{reload:async()=>{},transform:async(callback:any)=>{
      callback({add:(t:any)=>tools.set(t.name,t)})
      return {dispose:async()=>{}}
    }},
  } as unknown as Plugin.Context
  const registered=await registerConnectedWorkTools(ctx,{resolve:async()=>({scope:"controller",projectID:id,
    send:async(_route,raw)=>{sent.push(raw);return '{"kind":"settled"}'}})})
  try{
    const actual={sessionID:"ses_typed",signal:new AbortController().signal} as ToolContext
    await tools.get("writhelm_work_contract").execute({kind:"commands",operation:"update_plan"},actual)
    const a={sessionID:"ses_typed",tools:{writhelm_work_command:{input:{}}}},b={sessionID:"ses_other",tools:{writhelm_work_command:{input:{}}}}
    hook(a);hook(b)
    assert.equal((a.tools.writhelm_work_command.input as any).properties.operation.const,"update_plan")
    assert.equal((b.tools.writhelm_work_command.input as any).properties.operation.const,undefined)
    const payload={key:{projectId:id,id,revision:"0"},task:{projectId:id,taskId:id,revision:"0"},
      expectedGeneration:"9223372036854775807",steps:[],reason:"Exact typed plan",previousCheckpoint:null,bodies:[],assignment:null}
    const result=await tools.get("writhelm_work_command").execute({operation:"update_plan",requestKey:id,payload},actual)
    assert.equal(JSON.parse(result.content).kind,"settled")
    assert.ok(sent[0]!.includes('"expectedGeneration":9223372036854775807'))
    await tools.get("writhelm_work_command").execute({operation:"update_plan",requestKey:id,payload:{...payload,owner:"wrong"}},actual)
    assert.equal(sent.length,1,"excess typed authority fields refuse before dispatch")
  }finally{await registered.dispose()}
})
test("raw launch-command preflight runs on retained originals including replay before HTTP effects",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"launch-command-")),work=path.join(root,"work");await fs.mkdir(work)
  const custody=new LinuxWorkRequestCustody(path.join(root,"requests"),[work])
  const project="00000000-0000-0000-0000-000000000002",key="00000000-0000-0000-0000-000000000003"
  const raw=` {"formatVersion":3,"projectId":"${project}","requestKey":"${key}","operation":"reserve_assignment","payload":{}} `
  let posts=0,checks=0
  const channel=boundedWorkChannel(project,"controller","http://127.0.0.1:18679","wh1.synthetic",4096,custody,"binding",
    async()=>{posts++;return new Response('{"kind":"error","code":"FORBIDDEN"}')})
  const tools=new Map<string,MockTool>()
  const ctx={tool:{reload:async()=>{},transform:async(callback:any)=>{
    callback({add:(tool:MockTool)=>tools.set(tool.name,tool)});return {dispose:async()=>{}}
  }}} as unknown as Plugin.Context
  const registered=await registerConnectedWorkTools(ctx,{resolve:async()=>channel},undefined,undefined,async original=>{
    checks++;assert.equal(original,raw);throw new OwnerOrderHold("OWNER_ORDER_REQUIRED")
  })
  try{
    const actual={sessionID:"ses_launch",id:"call_launch",messageID:"msg_launch",signal:new AbortController().signal} as unknown as ToolContext
    for(let i=0;i<2;i++)assert.match(String((await tools.get("writhelm_work_command")!.execute({json:raw},actual)).content),/OWNER_ORDER_REQUIRED/)
    assert.equal(checks,2);assert.equal(posts,0)
    const retained=await fs.readdir(path.join(root,"requests"));assert.equal(retained.length,1)
  }finally{await registered.dispose()}
})
test("bound malformed work listing reports its contract while unbound reads remain unavailable",async()=>{
  const tools=new Map<string,MockTool>();let sent=0
  const ctx={tool:{transform:async(callback:any)=>{callback({add:(tool:MockTool)=>tools.set(tool.name,tool)});return {dispose:async()=>{}}},reload:async()=>{}}} as unknown as Plugin.Context
  await registerConnectedWorkTools(ctx,{resolve:async actual=>actual.sessionID==="ses_bound"?{
    scope:"controller",projectID:"project",send:async()=>{sent++;return '{"kind":"read","data":{"items":[]}}'}
  } as never:undefined})
  const actual={sessionID:"ses_bound",signal:new AbortController().signal} as unknown as ToolContext
  const tool=tools.get("writhelm_work_read")!
  const invalid=await tool.execute({json:'{"projectId":"project","operation":"list"}',format:"canonical"},actual)
  assert.equal(JSON.parse(String(invalid.content)).code,"INVALID_WORK_READ_INPUT");assert.equal(sent,0)
  const valid=await tool.execute({json:'{"formatVersion":2,"projectId":"project","operation":"list_work_records","subject":{"family":"task-heads","taskId":null,"pageSize":32,"cursor":null}}',format:"canonical"},actual)
  assert.equal(JSON.parse(String(valid.content)).kind,"read");assert.equal(sent,1)
  const denied=await tool.execute({json:'{"operation":"list"}',format:"canonical"},{...actual,sessionID:"ses_unbound"} as unknown as ToolContext)
  assert.equal(JSON.parse(String(denied.content)).code,"WORK_UNAVAILABLE_OR_FORBIDDEN")
})
test("MOCK native tool uses actual ToolContext, exact Int64 request bytes, protected original custody and no owner/actor argument", async () => {
  const configured = process.env.WRITHELM_NATIVE_TEST_ROOT
  assert.ok(configured); await fs.mkdir(configured, { recursive: true })
  const root = await fs.mkdtemp(path.join(configured, "work-tools-")), work = path.join(root, "work")
  await fs.mkdir(work)
  const custody = new LinuxWorkRequestCustody(path.join(root, "requests"), [work])
  const project = "00000000-0000-0000-0000-000000000002", key = "00000000-0000-0000-0000-000000000003"
  const raw = ` { "formatVersion":3,"projectId":"${project}","requestKey":"${key}","operation":"observe_continuation","payload":{"expectedGeneration":9223372036854775807}} `
  let requests = 0, seen = "", resolved = 0
  const channel = boundedWorkChannel(project, "assignment", "http://127.0.0.1:12345", "wh1.synthetic.key.DO_NOT_DISCLOSE_SECRET", 4096,
    custody, "exact protected binding", (async (_url, init) => {
      requests++; seen = String(init?.body)
      return new Response('{"formatVersion":1,"kind":"error","code":"FORBIDDEN"}', { status: 403 })
    }) as typeof fetch)
  const tools = new Map<string, MockTool>()
  const ctx = { tool: { transform: async (callback: (editor: { add(tool: MockTool): void }) => void) => {
    callback({ add: tool => { tools.set(tool.name, tool) } }); return { dispose: async () => {} }
  }, reload: async () => {} } } as unknown as Plugin.Context
  await registerConnectedWorkTools(ctx, { resolve: async actual => { resolved++; return actual.sessionID === "ses_bound" ? channel : undefined } })
  const actual = { sessionID: "ses_bound", messageID: "msg_bound", id: "call_bound", signal: new AbortController().signal } as unknown as ToolContext
  const tool = tools.get("writhelm_work_command")!
  const result = await tool.execute({ json: raw }, actual)
  assert.equal(seen, raw); assert.equal(requests, 1); assert.equal(resolved, 1)
  assert.ok(!String(result.content).includes("DO_NOT_DISCLOSE_SECRET"))
  const files = await fs.readdir(path.join(root, "requests")); assert.equal(files.length, 1)
  assert.equal(await fs.readFile(path.join(root, "requests", files[0]!), "utf8"), raw)
  await tool.execute({ json: raw, sessionID: "ses_owner", actor: "owner" }, actual)
  assert.equal(requests, 1); assert.equal(resolved, 1)
  await tool.execute({ json: raw.replace('"observe_continuation"', '"resolve_approval"') }, actual)
  assert.equal(requests, 1); assert.equal(resolved, 1)
  await tool.execute({ json: raw.replace("9223372036854775807", "9223372036854775808") }, actual)
  assert.equal(requests, 1)
  await tool.execute({ json: raw.replace("9223372036854775807", "1") }, actual)
  assert.equal(requests, 1) // original retained request cannot be silently replaced under same identity
  const cancelled = new AbortController(); cancelled.abort()
  await tool.execute({ json: raw }, { ...actual, signal: cancelled.signal })
  assert.equal(requests, 1)
})

test("shared strict parser retains legacy scalar/UTF8/duplicate refusal without GUI imports", () => {
  const fail = (): never => { throw new Error("invalid") }
  const decoded = exactJSON('{"n":9223372036854775807,"text":"é"}', fail)
  assert.equal(decoded.n.value, "9223372036854775807")
  for (const text of ['{"n":1.0}', '{"n":1e0}', '{"n":-0}', '{"x":1,"\\u0078":2}', '{"s":"\\ud800"}', '\ufeff{}'])
    assert.throws(() => exactJSON(text, fail))
})

test("bounded transport does not strip an outer JSON BOM before exact read validation",async()=>{
  const channel=boundedWorkChannel("project","controller","http://127.0.0.1:18679","wh1.synthetic",4096,
    {retain:async(_b,_k,raw)=>raw},"synthetic binding",async()=>new Response('\ufeff{"kind":"error","code":"FORBIDDEN"}'))
  await assert.rejects(channel.send("reads",'{"formatVersion":2,"projectId":"project","operation":"list_work_records","subject":{}}',
    new AbortController().signal))
})

test("result preflight sees retained original before effects and replay never repeats effect",async()=>{
  const project="00000000-0000-0000-0000-000000000002",key="00000000-0000-0000-0000-000000000003"
  const originals=new Map<string,string>();let effect=0,sends=0,receipt:"missing"|"match"|"hold"="missing"
  const custody={retain:async(_b:string,_k:string,raw:string)=>raw,retainWithStatus:async(b:string,k:string,raw:string)=>{
    const id=b+k,old=originals.get(id);if(old!==undefined&&old!==raw)throw Error("conflict")
    originals.set(id,raw);return {raw,fresh:old===undefined}
  }}
  const channel=boundedWorkChannel(project,"assignment","http://127.0.0.1:12345","wh1.synthetic.key.secret",4096,custody,"worker",
    (async()=>{sends++;return new Response('{"kind":"settled"}')}) as typeof fetch)
  const tools=new Map<string,MockTool>()
  const ctx={tool:{transform:async(callback:(editor:{add(tool:MockTool):void})=>void)=>{
    callback({add:tool=>tools.set(tool.name,tool)});return {dispose:async()=>{}}},reload:async()=>{}}} as unknown as Plugin.Context
  await registerConnectedWorkTools(ctx,{resolve:async()=>channel},async(raw,fresh)=>{
    assert.equal(originals.size,1);assert.equal([...originals.values()][0],raw)
    if(receipt==="match")return
    if(receipt!=="missing"||!fresh)throw Error("HOLD")
    effect++
  })
  const actual={sessionID:"ses_worker",messageID:"msg_worker",id:"call_worker",signal:new AbortController().signal} as unknown as ToolContext
  const tool=tools.get("writhelm_work_command")!,raw=JSON.stringify({formatVersion:3,projectId:project,requestKey:key,operation:"submit_result",payload:{}})
  await tool.execute({json:raw},actual);assert.equal(effect,1);assert.equal(sends,1)
  await tool.execute({json:raw},actual);assert.equal(effect,1);assert.equal(sends,1) // missing receipt after dispatch holds
  receipt="match";await tool.execute({json:raw},actual);assert.equal(effect,1);assert.equal(sends,2)
  receipt="hold";await tool.execute({json:raw},actual);assert.equal(effect,1);assert.equal(sends,2)
})

test("private dispatch channel sends only retained attempt binding, never arbitrary commands",async()=>{
  const project="00000000-0000-0000-0000-000000000002",requestKey="00000000-0000-0000-0000-000000000003"
  let original="",sends=0
  const custody={retain:async(_binding:string,_key:string,raw:string)=>{if(original&&original!==raw)throw Error("conflict");original=raw;return raw}}
  const channel=boundedDispatchAttemptChannel(project,"http://127.0.0.1:12345","wh1.synthetic.key.secret",custody,"controller",
    (async()=>{sends++;return new Response('{"kind":"settled"}')}) as typeof fetch)
  const signal=new AbortController().signal
  const raw=JSON.stringify({formatVersion:3,projectId:project,requestKey,operation:"bind_dispatch_attempt",payload:{}})
  await channel.send("commands",raw,signal);await channel.send("commands",raw,signal)
  assert.equal(sends,2);assert.equal(original,raw)
  await assert.rejects(channel.send("commands",raw.replace("bind_dispatch_attempt","reserve_assignment"),signal))
  await assert.rejects(channel.send("commands",raw.replace('"payload":{}','"payload":{"changed":true}'),signal))
  assert.equal(sends,2)
})
