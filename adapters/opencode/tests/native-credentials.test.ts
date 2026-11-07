// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createServer } from "node:http"
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import type { SessionApi } from "@opencode/client/promise/api"

const id = "00000000-0000-0000-0000-000000000001"
const other = "00000000-0000-0000-0000-000000000002"

test("real local credential transport with MOCK service policy/native session: prepare, bind, reopen, current identity loss", async () => {
  const configured = process.env.WRITHELM_NATIVE_TEST_ROOT
  assert.ok(configured)
  await fs.mkdir(configured, { recursive: true })
  const area = await fs.mkdtemp(path.join(configured, "native-registry-")), work = path.join(area, "work")
  await fs.mkdir(work)
  const custody = new LinuxNativeCustody(path.join(area, "private"), [work]); await custody.initialize()
  const requests = new LinuxWorkRequestCustody(path.join(area, "requests"), [work])
  const proof = Buffer.alloc(32, 2).toString("base64url")
  const generation = "9223372036854775807"
  const bindingJSON = `{"family":"assignment-bindings","key":{"projectId":"${id}","assignmentId":"${id}","generation":${generation}},"digest":"${"a".repeat(64)}"}`
  const subject = { assignmentId: id, executionGeneration: generation, bindingJSON }
  let deny = false, principal = id, nativeSessionId = "ses_fixture", location = work, pausing = false
  let envelopeDigest: string | null = null
  let freshEnvelope = false
  const server = createServer(async (request, response) => {
    const chunks: Buffer[] = []
    for await (const part of request) chunks.push(Buffer.from(part))
    const input = JSON.parse(Buffer.concat(chunks).toString("utf8"))
    assert.equal(request.headers["x-writhelm-native-proof"], proof)
    const credentialId = request.headers.authorization!.split(".")[2]
    if (deny) { response.writeHead(403); response.end("{}"); return }
    response.setHeader("Content-Type", "application/json")
    const controller = credentialId === other
    response.end(JSON.stringify(input.operation === "identity" ? { formatVersion: 1, storeId: id, projectId: id,
      controllerId: id, nativeHostId: id, principalId: principal, credentialId, scope: controller ? "controller" : "assignment",
      delegationId: controller ? null : id, assignmentId: controller ? null : id,
      executionGeneration: controller ? null : subject.executionGeneration } : input.operation === "packet"
      ? { formatVersion: 1, envelopeDigest, bindingJSON: subject.bindingJSON, nativeHostId: id, nativeSessionId, freshEnvelope,
        instructions: [{ key: "writhelm-task", value: "fixture mandatory task" }] }
      : { formatVersion: 1, basis: {}, active: !pausing && !freshEnvelope, quiescent: false,
      freshEnvelope, freshPause: controller && !pausing, prepare: freshEnvelope, dispatch: !!envelopeDigest && !freshEnvelope,
      pause: pausing, delivery: !!envelopeDigest && !freshEnvelope, bindingJSON: subject.bindingJSON, nativeHostId: id,
      nativeSessionId, intentId: pausing ? id : null, intentOperation: pausing ? "pause" : null,
      attemptId: envelopeDigest ? id : null, envelopeDigest }))
  })
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve))
  try {
    const address = server.address(); assert.ok(address && typeof address !== "string")
    const origin = `http://127.0.0.1:${address.port}`
    const service = new NativeRuntimeClient(origin, proof, id, id, id, id)
    let interrupts = 0
    const ctx = { app: { version: "2.2.5" }, session: { get: async ({ sessionID }: { sessionID: string }) =>
      ({ id: sessionID, location: { directory: location } }), interrupt: async () => { interrupts++; throw new Error("MOCK lost interrupt reply") } } } as unknown as Plugin.Context
    const registry = new NativeCredentialRegistry(ctx, custody, service, origin, requests, "a".repeat(64))
    const prepared = await registry.prepare(id, id)
    assert.deepEqual(await registry.prepare(id, id), prepared)
    assert.match(prepared.verifierDigest, /^[0-9a-f]{64}$/)
    assert.equal(Object.hasOwn(prepared, "token"), false)
    await assert.rejects(registry.bind("ses_fixture", id, work)) // assignment requires canonical subject
    nativeSessionId = "ses_other"
    await assert.rejects(registry.bind("ses_fixture", id, work, subject))
    nativeSessionId = "ses_fixture"
    await registry.bind("ses_fixture", id, work, subject)
    const actual = { sessionID: "ses_fixture", messageID: "msg_fixture", id: "call_fixture", signal: new AbortController().signal } as unknown as ToolContext
    assert.equal((await registry.resolve(actual))?.scope, "assignment")
    const reopened = new NativeCredentialRegistry(ctx, custody, service, origin, requests, "a".repeat(64))
    assert.equal((await reopened.resolve(actual))?.scope, "assignment")
    assert.deepEqual(await reopened.restoreAll(), ["ses_fixture"])
    assert.equal(reopened.managed("ses_fixture"), true)
    assert.equal(await reopened.resolve({ ...actual, sessionID: "ses_unbound" as ToolContext["sessionID"] }), undefined)
    principal = other
    assert.equal(await reopened.resolve(actual), undefined)
    principal = id; deny = true
    assert.equal(await reopened.resolve(actual), undefined)
    deny = false; location = area
    assert.equal(await reopened.resolve(actual), undefined)
    location = work
    const newIncarnation = new NativeCredentialRegistry(ctx, custody, service, origin, requests, "b".repeat(64))
    assert.equal(await newIncarnation.resolve(actual), undefined) // no silent epoch adoption
    await registry.prepare(id, other)
    await registry.bind("ses_controller", other, work)
    const bridge = new ProtectedNativeBridge(registry, custody, "ses_controller", id)
    assert.equal(bridge.managed("ses_fixture"), true) // freshly bound sessions are guarded before a first envelope
    const rawJSON = `{"assignment":{"assignmentId":"${id}","generation":${generation}},"binding":${bindingJSON},"nativeHostId":"${id}","nativeSessionId":"ses_fixture"}`
    const target = { key: "binding", rawJSON, digest: createHash("sha256").update(rawJSON).digest("hex"),
      bindingJSON: subject.bindingJSON, hostID: id, sessionID: "ses_fixture" }
    await bridge.retainOriginal("binding", subject, target)
    assert.equal(bridge.managed("ses_fixture"), true)
    await assert.rejects(bridge.claimPauseDispatch(target.key, target.digest, id))
    pausing = true
    assert.equal(await bridge.claimPauseDispatch(target.key, target.digest, id), true)
    assert.equal(await bridge.claimPauseDispatch(target.key, target.digest, id), false)
    const restoredBridge = new ProtectedNativeBridge(reopened, custody, "ses_controller", id)
    await restoredBridge.manage("ses_fixture")
    assert.equal(await restoredBridge.claimPauseDispatch(target.key, target.digest, id), false)
    await bridge.retainObservation(target.key, '{"kind":"binding-pause-outcome-unknown"}')
    deny = true // immutable native observations must not reenter current service authority
    assert.deepEqual(await restoredBridge.observations(target.key), ['{"kind":"binding-pause-outcome-unknown"}'])
    deny = false; pausing = false
    const metadata = { continuationId: id, assignmentId: id, executionGeneration: subject.executionGeneration }
    const nativeJSON = JSON.stringify({ nativeHostId: id, nativeSessionId: "ses_fixture", nativeMessageId: "msg_fixture",
      text: "Blocker B resolved; continue.", metadata, delivery: "steer", resume: true })
    const envelope = { key: "envelope", rawJSON: nativeJSON, digest: createHash("sha256").update(nativeJSON).digest("hex"),
      bindingJSON: subject.bindingJSON, hostID: id, sessionID: "ses_fixture", messageID: "msg_fixture",
      text: "Blocker B resolved; continue.", metadata, instructions: [{ key: "writhelm-task", value: "fixture mandatory task" }] }
    envelopeDigest = envelope.digest
    freshEnvelope = true
    await bridge.retainOriginal("envelope", subject, envelope)
    freshEnvelope = false
    assert.equal(await bridge.current(envelope), "active")
    assert.equal(await bridge.claimDispatch(envelope.key, envelope.digest), true)
    assert.equal(await restoredBridge.claimDispatch(envelope.key, envelope.digest), false)
    pausing = true
    assert.equal(await bridge.current(envelope), "held") // paused/interrupt is never qualified quiescence
    assert.equal(await bridge.absentAndQuiescent(envelope), false)
    await assert.rejects(bridge.retainOriginal("envelope", subject, { ...envelope, text: "forged lowering" }))
    // Actual protected transport -> concrete bridge -> native adapter; only external SDK effect is
    // mocked. This is not an installed host, JVM engine composition or physical writer test.
    const reserve = createServer()
    await new Promise<void>(resolve => reserve.listen(0, "127.0.0.1", resolve))
    const reserved = reserve.address(); assert.ok(reserved && typeof reserved !== "string")
    await new Promise<void>((resolve, reject) => reserve.close(error => error ? reject(error) : resolve()))
    const nativeOrigin = `http://127.0.0.1:${reserved.port}`, peerProof = Buffer.alloc(32, 3)
    const transport = await listenNativeTransport(nativeOrigin, peerProof, restoredBridge,
      new ConnectedNativeAdapter(ctx, {} as Pick<SessionApi, "inbox" | "message" | "context">, restoredBridge, id))
    try {
      const call = (auth: string, operation: string, extra = {}) => fetch(nativeOrigin + "/native/v1", { method: "POST",
        headers: { "Content-Type": "application/json", "X-Writhelm-Native-Proof": auth },
        body: JSON.stringify({ formatVersion: 1, operation, key: target.key, digest: target.digest, ...extra }) })
      assert.equal((await call("A".repeat(43), "pause", { intent: id })).status, 403)
      assert.equal((await call(peerProof.toString("base64url"), "pause", { intent: other })).status, 403)
      // The earlier pause claim survives transport startup: no second interrupt after lost reply.
      assert.equal((await call(peerProof.toString("base64url"), "pause", { intent: id })).status, 200)
      assert.equal(interrupts, 0)
      assert.equal((await call(peerProof.toString("base64url"), "rehold")).status, 200)
      // Custody loss cannot be mistaken for a fresh original dispatch, even with a canonical
      // pending permit. Neither claim nor prepare is allowed to silently rebuild these guards.
      const removeGuard = (kind: string, key: string) => fs.unlink(path.join(custody.root,
        `${kind}-${createHash("sha256").update(key).digest("hex")}.json`))
      await removeGuard("native-delivery-guard", envelope.key)
      await assert.rejects(bridge.claimDispatch(envelope.key, envelope.digest))
      await assert.rejects(bridge.retainOriginal("envelope", subject, envelope))
      await removeGuard("native-pause-guard", target.key)
      await assert.rejects(bridge.claimPauseDispatch(target.key, target.digest, id))
      await assert.rejects(bridge.retainOriginal("binding", subject, target))
    } finally { await transport.close(); await bridge.close(); await restoredBridge.close() }
  } finally { await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) }
})
