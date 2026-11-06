// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { boundedWorkChannel, LinuxWorkRequestCustody, registerConnectedWorkTools } from "../src/connected-tools.js"
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
