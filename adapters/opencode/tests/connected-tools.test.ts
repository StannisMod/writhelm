// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { boundedWorkChannel, boundedDispatchAttemptChannel, LinuxWorkRequestCustody, registerConnectedWorkTools } from "../src/connected-tools.js"
import { exactJSON } from "../src/strict-json.js"

type MockTool = { name: string; execute(input: unknown, actual: ToolContext): Promise<{ content?: unknown }> }
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
