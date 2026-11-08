// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import { promises as fs } from "node:fs"
import path from "node:path"
import { randomUUID, createHash } from "node:crypto"
import { Schema } from "effect"
import { createRoot } from "solid-js"
import { parseHTML } from "linkedom"
import type { IpcClient, Caller } from "@opencode/plugin/gui"
import { Inputs, Review, reply, Fault, type Session, type Reply } from "../src/contracts.js"
import { exact, serviceResult, completeBundle } from "../src/wire.js"
import { Controller } from "../src/controller.js"
import { openProtected, LinuxProtectedFiles } from "../src/protected.js"
import { credential, verifier } from "../src/helper.js"
import { nativePanel, exactLink } from "../src/panel.js"

const base = path.resolve(process.env.WRITHELM_NATIVE_ROOT!)
const session: Session = { key: "sidecar\nsynthetic", id: "synthetic", server: "sidecar", directory: path.join(base, "project"), projectRoot: path.join(base, "project") }
const caller: Caller = { window: 1, signal: new AbortController().signal }
const raw = await fs.readFile(new URL("../../tests/fixtures/bundle.json", import.meta.url), "utf8")
const b = serviceResult(raw).data, request = b.request
const open = { session, requestId: request.key, requestDigest: request.payload.requestDigest, packetDigest: request.payload.packetDigest }
const host = { async verify(s: Session, window: number) { if (window < 1 || s.key !== session.key || s.directory !== session.directory) throw new Fault("STALE_VIEW") } }
const uid = (n: number) => "00000000-0000-0000-0000-" + String(n).padStart(12, "0")
async function files() {
  const root = path.join(base, "private-" + randomUUID()); const files = await openProtected(root, [session.directory], true)
  const owner = credential(uid(1), uid(2), uid(3)), agent = credential(uid(1), uid(2), uid(5))
  const profile = { formatVersion: 1, origin: "http://127.0.0.1:45678", storeId: uid(1), projectId: uid(2), principalId: uid(3), active: { credentialId: owner.credentialId, token: owner.token }, agent: { principalId: uid(5), credentialId: agent.credentialId, token: agent.token }, pending: null }
  await files.write(profile); return { root, files, profile }
}
function settled(body: string, profile: any, originalCredential: string) {
  const command = JSON.parse(body)
  return { formatVersion: 1, kind: "settled", identity: { principalId: profile.principalId, projectId: profile.projectId, operation: command.operation, requestKey: command.requestKey }, fingerprint: "a".repeat(64), validatedAt: { algorithm: "sha1", hex: "b".repeat(40) }, authenticatedByCredentialId: originalCredential,
    outcome: { kind: "success", operation: command.operation, result: command.operation === "rotate_credential" ? { credentialId: command.payload.newCredentialId, replacedCredentialId: command.payload.credentialId } : command.payload.requestId, domainReplayed: false }, commandReplayed: true, publication: null }
}
const response = (v: unknown, status = 200) => new Response(JSON.stringify(v), { status, headers: { "Content-Type": "application/json" } })

test("independent accepted verifier golden and lossless strict tokens", () => {
  const actual = verifier(uid(1), uid(2), uid(3), uid(4), Uint8Array.from({ length: 32 }, (_, i) => i))
  assert.equal(actual, "d7e7768bfbb2a58f9417759671900e7f9fc779700b0b00116f080d5825763cb2")
  assert.equal(exact('{"generation":9007199254740993}').generation.value, "9007199254740993")
  assert.throws(() => exact('{"x":1.0}')); assert.throws(() => exact('{"a":1,"\\u0061":2}')); assert.throws(() => exact('{"x":"\\ud800"}'))
  assert.throws(() => exact('{"\\ud800":1}')); assert.throws(() => exact('\ufeff{"x":1}'))
})
test("actual SDK codecs reject excess authority fields, wrong choices and malformed comments", () => {
  assert.throws(() => Schema.decodeUnknownSync(Inputs.open)({ ...open, actor: uid(3) }))
  assert.throws(() => Schema.decodeUnknownSync(Inputs.open)({ ...open, session: { ...session, token: "not-a-token" } }))
  assert.throws(() => Schema.decodeUnknownSync(Inputs.answer)({ session, handle: randomUUID(), choice: "other", comments: [] }))
  assert.doesNotThrow(() => Schema.decodeUnknownSync(Inputs.open)(open))
})
test("work IPC is READ-only, current-session bound and preserves exact Int64 generation without owner-token disclosure",async()=>{
  const f=await files();const requests:string[]=[];let wrong=false
  const key={projectId:uid(2),id:uid(900)}
  const bodyText="attributed progress — proposal, not acceptance",bodyDigest=createHash("sha256").update(bodyText).digest("hex")
  const fetcher:typeof fetch=async(url,init)=>{
    assert.equal(String(url),f.profile.origin+"/api/v1/reads")
    assert.equal(new Headers(init!.headers).get("Authorization"),"Bearer "+f.profile.active.token)
    const body=String(init!.body);requests.push(body);const input=exact(body)
    const data=input.operation==="read_work_body"?JSON.stringify({digest:bodyDigest,byteLength:Buffer.byteLength(bodyText),body:wrong?"wrong body":bodyText}):input.operation==="list_work_records"
      ? `{"snapshotId":"${uid(901)}","complete":true,"nextCursor":null,"items":[{"family":"assignments","key":${JSON.stringify(key)},"generation":9007199254740993}]}`
      : `{"formatVersion":1,"family":"assignments","key":${JSON.stringify(key)},"payload":{"generation":${wrong?"0":"9007199254740993"},"state":"pausing","executionGeneration":9223372036854775807}}`
    return new Response(`{"formatVersion":2,"kind":"read","basis":{"algorithm":"sha1","hex":"${"a".repeat(40)}"},"operation":"${input.operation}","data":${data}}`)
  }
  const c=new Controller(host,fetcher);await c.attach(f.files)
  try{
    const list=await c.dispatch("workList",{session,family:"assignments",cursor:""},caller);assert.equal(list.ok,true)
    const input={session,family:"assignments" as const,keyJSON:JSON.stringify(key),digest:"",generation:"9007199254740993"}
    const record=await c.dispatch("workOpen",input,caller);assert.equal(record.ok,true)
    assert.match(requests[1]!,/"generation":9007199254740993/);assert.ok(record.bundleJSON.includes("9223372036854775807"))
    assert.equal(JSON.stringify([list,record]).includes(f.profile.active.token),false)
    const bodyInput={...input,bodyDigest}
    const body=await c.dispatch("workBody",bodyInput,caller);assert.equal(body.ok,true)
    assert.ok(body.bundleJSON.includes(bodyText));assert.equal(exact(requests[2]!).subject.record.generation.value,"9007199254740993")
    wrong=true;assert.equal((await c.dispatch("workOpen",input,caller)).code,"INTEGRITY")
    assert.equal((await c.dispatch("workBody",bodyInput,caller)).code,"INTEGRITY")
    assert.equal((await c.dispatch("workList",{session:{...session,key:"different"},family:"assignments",cursor:""},caller)).code,"STALE_VIEW")
    const before=requests.length
    assert.equal((await c.dispatch("workList",{session,family:"assignments",cursor:"",actor:uid(3)} as any,caller)).code,"UNAVAILABLE")
    assert.equal(requests.length,before)
  }finally{await c.close()}
})
test("real protected files enforce modes, links, project exclusion and atomic reread", async () => {
  const f = await files()
  try {
    const st = await fs.stat(path.join(f.root, "profile.json")); assert.equal(st.mode & 0o777, 0o600)
    assert.equal((await f.files.read()).storeId, uid(1))
    await fs.chmod(path.join(f.root, "profile.json"), 0o644)
    await assert.rejects(f.files.read()); await fs.chmod(path.join(f.root, "profile.json"), 0o600)
    await fs.link(path.join(f.root, "profile.json"), path.join(f.root, "linked")); await assert.rejects(f.files.read()); await fs.unlink(path.join(f.root, "linked"))
    const alias = path.join(base, "symlink-" + randomUUID()); await fs.symlink(f.root, alias)
    await assert.rejects(LinuxProtectedFiles.open(alias, [session.directory]))
    await assert.rejects(LinuxProtectedFiles.open(f.root, [base]))
    await f.files.write({ ...f.profile, origin: "http://127.0.0.1:45679" }); assert.equal((await f.files.read()).origin, "http://127.0.0.1:45679")
  } finally { await f.files.close() }
})
test("complete bundle coverage is mandatory; source text and all metadata remain exact", () => {
  assert.equal(completeBundle(raw, open.requestId, open.requestDigest, open.packetDigest, uid(2)).complete, true)
  const missing = JSON.parse(raw); missing.data.materials = []
  assert.throws(() => completeBundle(JSON.stringify(missing), open.requestId, open.requestDigest, open.packetDigest, uid(2)))
  assert.throws(() => completeBundle(raw, randomUUID(), open.requestDigest, open.packetDigest, uid(2)))
})
test("answer loss retains exact original bytes; wrong window/session/anchor cannot submit", async () => {
  const f = await files(); let sends = 0; const sent: string[] = []
  const fetcher: typeof fetch = async (url, init) => {
    if (String(url).endsWith("/reads")) return new Response(raw)
    const stored = await f.files.read(); assert.equal(stored.pending.commandJSON, init!.body)
    sent.push(String(init!.body)); if (++sends === 1) throw new Error("synthetic lost reply")
    return response(settled(String(init!.body), f.profile, f.profile.active.credentialId))
  }
  const c = new Controller(host, fetcher); await c.attach(f.files)
  const view = await c.dispatch("open", open, caller); assert.equal(view.ok, true)
  assert.equal((await c.dispatch("answer", { session, handle: view.handle, choice: "approve", comments: [] }, { ...caller, window: 2 })).code, "STALE_VIEW")
  assert.equal((await c.dispatch("answer", { session, handle: view.handle, choice: "changes-required", comments: [{ itemId: randomUUID(), text: "exact" }] }, caller)).code, "INVALID_INPUT")
  const failed = await c.dispatch("answer", { session, handle: view.handle, choice: "approve", comments: [] }, caller)
  assert.equal(failed.pending, true); assert.equal(sends, 1)
  const recovered = await c.dispatch("reconcile", { session }, caller)
  assert.equal(recovered.ok, true); assert.equal(sent[0], sent[1]); assert.equal((await f.files.read()).pending, null)
  assert.ok(!JSON.stringify([view, failed, recovered]).includes(f.profile.active.token)); await c.close()
})
test("rotation persists replacement before dispatch and reload retries original key with new credential", async () => {
  const f = await files(); let published = false; let exactCommand = ""; let nextToken = ""
  const fetcher: typeof fetch = async (_url, init) => {
    const stored = await f.files.read(); nextToken = stored.pending.replacement.token
    assert.ok(stored.active.token !== nextToken)
    const header = new Headers(init!.headers).get("Authorization")
    if (!published && header === "Bearer " + nextToken) return response({ formatVersion: 1, kind: "error", code: "UNAUTHENTICATED" }, 401)
    if (!published) { published = true; exactCommand = String(init!.body); throw new Error("synthetic reply loss AFTER publication") }
    assert.equal(String(init!.body), exactCommand); assert.ok(header === "Bearer " + nextToken)
    return response(settled(exactCommand, f.profile, f.profile.active.credentialId))
  }
  const first = new Controller(host, fetcher); await first.attach(f.files)
  assert.equal((await first.dispatch("rotate", { session }, caller)).pending, true); await first.close()
  const reopened = await openProtected(f.root, [session.directory]); const second = new Controller(host, async (...args) => {
    const stored = await reopened.read(); assert.ok(new Headers(args[1]!.headers).get("Authorization") === "Bearer " + stored.pending.replacement.token)
    assert.equal(String(args[1]!.body), exactCommand); return response(settled(exactCommand, f.profile, f.profile.active.credentialId))
  }); await second.attach(reopened)
  const result = await second.dispatch("reconcile", { session }, caller); assert.equal(result.ok, true)
  assert.ok((await reopened.read()).active.token === nextToken); assert.equal((await reopened.read()).pending, null)
  assert.ok(!JSON.stringify(result).includes(nextToken)); await second.close()
})
test("stale async open completion never replaces a newer view", async () => {
  const f = await files(); let complete!: () => void; let calls = 0
  const c = new Controller(host, async () => { if (++calls === 1) await new Promise<void>(r => { complete = r }); return new Response(raw) })
  await c.attach(f.files)
  const old = c.dispatch("open", open, caller); await new Promise(r => setImmediate(r))
  const current = await c.dispatch("open", open, caller); complete()
  assert.equal((await old).code, "STALE_VIEW"); assert.equal(current.ok, true); await c.close()
})
test("protected retry is not an arbitrary owner proxy; failed persistence never dispatches", async () => {
  const f = await files()
  const malformed = { ...f.profile, pending: { kind: "answer", session, commandJSON: JSON.stringify({ formatVersion: 1, requestKey: randomUUID(), projectId: uid(2), operation: "revoke_access", payload: { principalId: uid(5) } }), replacement: null, registrationJSON: "", storeDirectory: "" } }
  await f.files.write(malformed)
  const bad = new Controller(host); await assert.rejects(bad.attach(f.files)) // ownership closes failed candidate
  const real = await openProtected(f.root, [session.directory]); await real.write(f.profile)
  let commands = 0
  const c = new Controller(host, async (url) => { if (String(url).endsWith("commands")) commands++; return new Response(raw) })
  await c.attach({ read: () => real.read(), readAgent: () => real.readAgent(), writeAgent: value => real.writeAgent(value), write: async () => { throw new Fault("PROTECTION_FAILED") }, close: () => real.close() })
  const view = await c.dispatch("open", open, caller)
  const denied = await c.dispatch("answer", { session, handle: view.handle, choice: "approve", comments: [] }, caller)
  assert.equal(denied.code, "PROTECTION_FAILED"); assert.equal(denied.pending, false); assert.equal(commands, 0)
  assert.equal((await c.dispatch("rotate", { session }, caller)).code, "PROTECTION_FAILED"); assert.equal(commands, 0)
  await c.close()
})
test("view/window state is bounded and closing a window releases its identity", async () => {
  const f = await files(), c = new Controller(host, async () => new Response(raw)); await c.attach(f.files)
  let first = ""
  for (let window = 1; window <= 8; window++) { const r = await c.dispatch("open", open, { ...caller, window }); assert.equal(r.ok, true); if (window === 1) first = r.handle }
  assert.equal((await c.dispatch("open", open, { ...caller, window: 9 })).code, "BUSY")
  c.closeWindow(1)
  assert.equal((await c.dispatch("answer", { session, handle: first, choice: "approve", comments: [] }, caller)).code, "STALE_VIEW")
  assert.equal((await c.dispatch("open", open, { ...caller, window: 9 })).ok, true)
  await c.close()
})
test("native DOM component renders full material, no default assent, anchored changes and dismiss", async () => {
  const { document, window } = parseHTML("<html><body></body></html>")
  Object.assign(globalThis, { document, window })
  const answered: any[] = []; let dismissed = 0
  const bridge: IpcClient<typeof Review.spec> = {
    state: () => undefined, on: () => () => {},
    status: async () => reply("OK", { pageJSON: JSON.stringify({ formatVersion: 1, kind: "read", basis: { algorithm: "sha1", hex: "a".repeat(40) }, operation: "list_projects", data: { snapshotId: randomUUID(), nextCursor: null, complete: true, items: [{ projectId: uid(2) }] } }) }),
    connect: async () => reply(), pending: async () => reply(),
    open: async () => reply("OK", { handle: uid(800), bundleJSON: raw }),
    answer: async i => { answered.push(i); return reply() }, reconcile: async () => reply(), rotate: async () => reply(), setup: async () => reply(),
    dismiss: async () => { dismissed++; return reply() },
    workList: async () => reply("UNCONFIGURED"),workOpen: async () => reply("UNCONFIGURED"),
    workBody: async () => reply("UNCONFIGURED"),
  }
  let dispose!: () => void, root!: HTMLElement
  createRoot(d => { dispose = d; root = nativePanel(() => session, bridge, () => ({ project: uid(2), ...open })); document.body.append(root) })
  const drain = async () => { for (let i = 0; i < 6; i++) await new Promise(r => setImmediate(r)) }
  await drain()
  for (const material of b.materials) for (const fragment of material.fragments) assert.ok(root.textContent!.includes(fragment.text))
  assert.ok(root.textContent!.includes(b.request.payload.packet.consequences[0])); assert.equal(root.querySelectorAll('input[type="radio"]:checked').length, 0)
  const send = [...root.querySelectorAll("button")].find(b => b.textContent === "Submit exact retained answer")!
  send.click(); await drain(); assert.equal(answered.length, 0)
  const radio = root.querySelector('input[value="changes-required"]') as HTMLInputElement; radio.checked = true; radio.dispatchEvent(new window.Event("change"))
  const area = root.querySelector("textarea")! as HTMLTextAreaElement; area.value = "Exact anchored correction\r\n"
  send.click(); await drain(); assert.equal(answered.length, 1); assert.equal(answered[0].comments[0].itemId, b.request.payload.packet.items[0].id); assert.equal(answered[0].comments[0].text, area.value)
  ;(root.querySelector('[data-action="dismiss"]') as HTMLButtonElement).click(); await drain(); assert.equal(answered.length, 1); assert.equal(dismissed, 1)
  dispose()
})
test("exact links carry only closed nonsecret identities, not credentials or assent", () => {
  const link = `writhelm://review/${uid(2)}/${open.requestId}/${open.requestDigest}/${open.packetDigest}`
  assert.equal(exactLink(link)?.requestId, open.requestId); assert.equal(exactLink(link + "?token=forbidden"), undefined)
  assert.equal(exactLink("https://example.invalid/review"), undefined)
})
