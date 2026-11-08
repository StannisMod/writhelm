// SPDX-License-Identifier: AGPL-3.0-only
// Synthetic component/controller journey through the REAL checked HTTP service. Not an installed host.
import assert from "node:assert/strict"
import path from "node:path"
import { randomUUID, createHash } from "node:crypto"
import { promises as fs } from "node:fs"
import { createRoot } from "solid-js"
import { parseHTML } from "linkedom"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review, reply, Fault, type Session } from "../src/contracts.js"
import { Controller } from "../src/controller.js"
import { openProtected } from "../src/protected.js"
import { prepareSetup, PublicSetupRunner } from "../src/helper.js"
import { http, serviceResult } from "../src/wire.js"
import { nativePanel } from "../src/panel.js"

let input = ""; for await (const chunk of process.stdin) { input += chunk; assert.ok(input.length <= 65536) }
const config = JSON.parse(input), root = path.resolve(config.root), privateRoot = path.join(root, "client"), storeDirectory = path.join(root, "store")
const session: Session = { key: "sidecar\nsynthetic-native", id: "synthetic-native", server: "sidecar", directory: path.join(root, "project"), projectRoot: path.join(root, "project") }
const caller = { window: 1, signal: new AbortController().signal }
const host = { async verify(s: Session, window: number) { assert.equal(window, 1); assert.equal(s.key, session.key); assert.equal(s.directory, session.directory) } }
if (config.stage === "prepare") {
  const files = await openProtected(privateRoot, [session.directory], true)
  const profile = prepareSetup(config.origin, session, storeDirectory)
  assert.ok(!profile.pending.registrationJSON.includes("wh1."))
  await files.write(profile); await files.writeAgent(profile)
  assert.ok(!JSON.stringify(await files.readAgent()).includes(profile.active.token))
  const c = new Controller(host); await c.attach(files)
  class LostSetupReply extends PublicSetupRunner {
    private lost = false
    override async run(registration: any, verify = false) {
      await super.run(registration, verify)
      if (!verify && !this.lost) { this.lost = true; throw new Fault("SETUP_UNKNOWN") }
    }
  }
  const runner = new LostSetupReply(config.runtime)
  assert.equal((await c.finishSetup(runner, false)).code, "SETUP_UNKNOWN")
  assert.equal((await files.read()).pending.kind, "setup")
  assert.equal((await c.retry({ session }, caller, runner)).ok, true)
  // A second initialize refuses without reset; an independent exact-store verify remains possible.
  await assert.rejects(runner.run(JSON.parse(profile.pending.registrationJSON)))
  await runner.run(JSON.parse(profile.pending.registrationJSON), true)
  const checked = await files.read(); assert.equal(checked.pending, null)
  await c.close(); process.stdout.write(JSON.stringify({ storeId: profile.storeId, storeDirectory, privateRoot }))
} else {
  let files = await openProtected(privateRoot, [session.directory]); let profile = await files.read()
  const originalOwner = profile.active.token
  const project = profile.projectId, documentId = randomUUID(), revisionId = randomUUID(), requestId = randomUUID(), itemId = randomUUID()
  const revision = { document: { projectId: project, documentId }, revisionId }
  const body = "\ufeffNative exact X e\u0301\r\nMaterial retention exception\r\n"
  const command = async (operation: string, payload: unknown) => {
    const r = await http(profile.origin, profile.agent.token, "commands", JSON.stringify({ formatVersion: 1, requestKey: randomUUID(), projectId: project, operation, payload }))
    assert.equal(r.status, 200); assert.equal(r.value.kind, "settled"); return r
  }
  await command("propose_revision", { revisionKey: revision, body, structure: { clauses: [{ id: "X", span: { start: 0, endExclusive: Buffer.byteLength(body) } }], context: [] } })
  const revisions = await http(profile.origin, profile.agent.token, "reads", JSON.stringify({ formatVersion: 1, projectId: project, operation: "list_revisions", subject: { documentId, cursor: null, pageSize: 32 } }))
  const ref = { kind: "clause", revision, revisionDigest: revisions.value.data.items[0].revisionDigest, clauseId: "X", contentDigest: createHash("sha256").update(body).digest("hex") }
  await command("request_approval", { requestId, packet: { items: [{ id: itemId, before: null, target: ref }], question: "Approve exact native X?", scope: "Only X, not neighbouring edits", rationale: "Synthetic native binding", consequences: ["Material retention exception stays visible"], alternatives: ["Keep pending or reject"], uncertainties: ["Semantic closure is not proven"], contexts: [], dependencies: [], informationGaps: [] } })
  const requested = await http(profile.origin, profile.agent.token, "reads", JSON.stringify({ formatVersion: 1, projectId: project, operation: "read_request", subject: { id: requestId } }))
  const binding = { project, requestId, requestDigest: requested.value.data.record.payload.requestDigest, packetDigest: requested.value.data.record.payload.packetDigest }
  let loseAnswer = true, loseRotation = true
  const lossy: typeof fetch = async (url, init) => {
    const result = await fetch(url, init)
    if (String(url).endsWith("/commands") && result.status === 200) {
      const op = JSON.parse(String(init!.body)).operation
      if ((op === "resolve_approval" && loseAnswer) || (op === "rotate_credential" && loseRotation)) {
        if (op === "resolve_approval") loseAnswer = false; else loseRotation = false
        await result.arrayBuffer(); throw new Error("synthetic response lost after real confirmed publication")
      }
    }
    return result
  }
  let controller = new Controller(host, lossy); await controller.attach(files)
  const { document, window } = parseHTML("<html><body></body></html>"); Object.assign(globalThis, { document, window })
  const bridge: IpcClient<typeof Review.spec> = {
    state: () => undefined, on: () => () => {},
    status: i => controller.dispatch("status", i, caller), connect: async () => reply(),
    pending: i => controller.dispatch("pending", i, caller), open: i => controller.dispatch("open", i, caller),
    answer: i => controller.dispatch("answer", i, caller), reconcile: i => controller.dispatch("reconcile", i, caller),
    rotate: i => controller.dispatch("rotate", i, caller), setup: async () => reply(), dismiss: i => controller.dispatch("dismiss", i, caller),
    workList: i => controller.dispatch("workList",i,caller),workOpen: i => controller.dispatch("workOpen",i,caller),
    workBody: i => controller.dispatch("workBody",i,caller),
  }
  let dispose!: () => void, panel!: HTMLElement
  createRoot(d => { dispose = d; panel = nativePanel(() => session, bridge, () => binding); document.body.append(panel) })
  const waitFor = async (condition: () => boolean) => { const start = performance.now(); while (!condition()) { assert.ok(performance.now() - start < 15000, "Bounded native component condition timed out"); await new Promise(r => setTimeout(r, 20)) } }
  await waitFor(() => !!panel.querySelector('input[value="approve"]'))
  assert.ok(panel.textContent!.includes(body)); assert.ok(panel.textContent!.includes("Material retention exception stays visible"))
  assert.equal(panel.querySelectorAll('input[type="radio"]:checked').length, 0)
  const radio = panel.querySelector('input[value="approve"]') as HTMLInputElement; radio.checked = true; radio.dispatchEvent(new window.Event("change"))
  const send = [...panel.querySelectorAll("button")].find(b => b.textContent === "Submit exact retained answer")!; send.click()
  await waitFor(() => panel.textContent!.includes("original command retained"))
  const retained = await files.read(); assert.equal(retained.pending.kind, "answer"); const originalBytes = retained.pending.commandJSON
  assert.ok(!panel.textContent!.includes(originalOwner)); assert.ok(!panel.textContent!.includes(profile.agent.token))
  dispose(); await controller.close()
  files = await openProtected(privateRoot, [session.directory]); controller = new Controller(host, lossy); await controller.attach(files)
  const recovered = await controller.dispatch("reconcile", { session }, caller)
  assert.equal(recovered.ok, true); assert.equal(serviceResult(recovered.resultJSON).commandReplayed, true)
  assert.equal(serviceResult(recovered.resultJSON).identity.requestKey, JSON.parse(originalBytes).requestKey)
  assert.equal((await files.read()).pending, null)
  assert.equal((await controller.dispatch("rotate", { session }, caller)).pending, true)
  const rotation = await files.read(), rotationBytes = rotation.pending.commandJSON
  assert.ok(rotation.active.token === originalOwner && rotation.pending.replacement.token !== originalOwner)
  await controller.close(); files = await openProtected(privateRoot, [session.directory]); controller = new Controller(host); await controller.attach(files)
  const rotationResult = await controller.dispatch("reconcile", { session }, caller)
  assert.equal(rotationResult.ok, true); assert.equal(serviceResult(rotationResult.resultJSON).commandReplayed, true)
  assert.equal(serviceResult(rotationResult.resultJSON).identity.requestKey, JSON.parse(rotationBytes).requestKey)
  profile = await files.read(); assert.ok(profile.active.token === rotation.pending.replacement.token)
  const agentProfile = await files.readAgent(); assert.ok(agentProfile.active.token === profile.agent.token)
  assert.ok(!JSON.stringify(agentProfile).includes(originalOwner) && !JSON.stringify(agentProfile).includes(profile.active.token))
  const old = await http(profile.origin, originalOwner, "reads", JSON.stringify({ formatVersion: 1, projectId: project, operation: "read_request", subject: { id: requestId } }))
  assert.equal(old.status, 401)
  const resolution = await http(profile.origin, profile.active.token, "reads", JSON.stringify({ formatVersion: 1, projectId: project, operation: "read_resolution", subject: { id: requestId } }))
  assert.equal(resolution.status, 200); assert.equal(resolution.value.data.record.payload.approved.length, 1)
  const agentRead = await http(profile.origin, agentProfile.active.token, "reads", JSON.stringify({ formatVersion: 1, projectId: project, operation: "read_request", subject: { id: requestId } }))
  assert.equal(agentRead.status, 200)
  assert.ok(!JSON.stringify([recovered, rotationResult]).includes(originalOwner)); assert.ok(!JSON.stringify([recovered, rotationResult]).includes(profile.active.token))
  await controller.close(); process.stdout.write(JSON.stringify({ nativeComponentJourney: "passed", installedHost: false, requestId, originalAnswerReplayed: true, originalRotationReplayed: true, oldKeyDenied: true }))
}
