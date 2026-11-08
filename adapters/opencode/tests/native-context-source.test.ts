// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createServer } from "node:http"
import { createHash } from "node:crypto"
import { Effect, Schema, Fiber } from "effect"
import { Message } from "@opencode/ai/effect"
import { SessionMessage } from "@opencode/schema/session-message"
import type { Plugin as PromisePlugin } from "@opencode/plugin"
import type { Plugin } from "@opencode/plugin/effect"
import type { SessionCompaction } from "@opencode/plugin/effect/session"
import { contextJSON } from "../src/native-context-codec.js"
import { NativeContextSources } from "../src/native-context-source.js"
import { NativeContextExtraction } from "../src/native-context-extraction.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"

test("CD03 context framing keeps native JSON opaque and matches scalar/control/Int64 rules", () => {
  assert.equal(contextJSON({ z: "\n\t\b\r\f\u0000", a: "literal\\n/é😀", n: 9223372036854775807n }),
    '{"a":"literal\\\\n/é😀","n":9223372036854775807,"z":"\\u000a\\u0009\\u0008\\u000d\\u000c\\u0000"}')
  assert.equal(contextJSON({ "😀": 1, "\ue000": 2 }), '{"\ue000":2,"😀":1}')
  assert.throws(() => contextJSON([, "hidden gap"]))
  assert.throws(() => contextJSON({ n: 1.25 }))
  assert.throws(() => contextJSON({ n: 9223372036854775808n }))
  assert.throws(() => contextJSON({ text: "\ud800" }))
  assert.throws(() => contextJSON({ text: "cannot silently truncate" }, 8))
})

test("REAL scoped HTTP/custody with MOCK Effect-native source: exact cut, pending suffix, current READ and original proof", async () => {
  const base = process.env.WRITHELM_NATIVE_TEST_ROOT; assert.ok(base)
  await fs.mkdir(base, { recursive: true })
  const area = await fs.mkdtemp(path.join(base, "context-source-")), work = path.join(area, "work")
  await fs.mkdir(work)
  const id = "00000000-0000-0000-0000-000000000001", other = "00000000-0000-0000-0000-000000000002"
  const proof = Buffer.alloc(32, 4).toString("base64url"), generation = "9223372036854775807"
  const bindingJSON = `{"family":"assignment-bindings","key":{"projectId":"${id}","assignmentId":"${id}","generation":${generation}},"digest":"${"a".repeat(64)}"}`
  let readable = true, calls = 0
  const server = createServer(async (request, response) => {
    calls++; const chunks: Buffer[] = []
    for await (const chunk of request) chunks.push(Buffer.from(chunk))
    const input = JSON.parse(Buffer.concat(chunks).toString("utf8"))
    if (input.operation === "source-identity" && !readable) { response.writeHead(403); response.end("{}"); return }
    response.setHeader("Content-Type", "application/json")
    response.end(JSON.stringify(input.operation.endsWith("identity") ? { formatVersion: 1, storeId: id, projectId: id,
      controllerId: id, nativeHostId: id, principalId: id, credentialId: id, scope: "assignment", delegationId: id,
      assignmentId: id, executionGeneration: generation } : { formatVersion: 1, basis: {}, active: readable,
      prepare: false, dispatch: false, pause: false, delivery: false, quiescent: false, freshEnvelope: false, freshPause: false,
      bindingJSON: readable ? bindingJSON : null, nativeHostId: readable ? id : null, nativeSessionId: readable ? "ses_source" : null,
      intentId: null, intentOperation: null, attemptId: null, envelopeDigest: null }))
  })
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve))
  try {
    const address = server.address(); assert.ok(address && typeof address !== "string")
    const origin = `http://127.0.0.1:${address.port}`
    const custody = new LinuxNativeCustody(path.join(area, "private"), [work]); await custody.initialize()
    const promiseContext = { app: { version: "2.2.5" }, session: { get: async ({ sessionID }: { sessionID: string }) =>
      ({ id: sessionID, location: { directory: work } }) } } as unknown as PromisePlugin.Context
    const registry = new NativeCredentialRegistry(promiseContext, custody, new NativeRuntimeClient(origin, proof, id, id, id, id), origin,
      new LinuxWorkRequestCustody(path.join(area, "requests"), [work]), "a".repeat(64))
    await registry.prepare(id, id); await registry.bind("ses_source", id, work, { assignmentId: id, executionGeneration: generation, bindingJSON })
    const source = new NativeContextSources(registry, custody)
    const decode = Schema.decodeSync(SessionMessage.Info as unknown as Schema.Codec<unknown, unknown>)
    const native = (messageId: string, text: string, metadata: Record<string, unknown> = {}) =>
      decode({ id: messageId, type: "user", text, time: { created: 1 }, metadata })
    let observations = [native("msg_first", "Before cut", { nativeFloat: 1.25, exactGeneration: generation }),
      native("msg_cut", "Required remaining work"), native("msg_late", "Unconsumed after-cut input", { originalTask: other })]
    const entries = [{ key: "writhelm-task", value: "EXACT accepted task\nNo summary substitution" }]
    const ctx = { app: { version: "2.2.5" }, session: {
      context: () => Effect.succeed(observations), instructions: { entry: { list: () => Effect.succeed(entries) } },
    } } as unknown as Plugin.Context
    const event = { sessionID: "ses_source", model: { providerID: "MOCK", id: "no-live-model", variant: undefined },
      messages: [{ id: "msg_first" }, { id: "msg_cut" }] } as unknown as SessionCompaction
    const profile = `{"projectId":"${id}","profileId":"${other}","revision":${generation}}`
    const captured = await Effect.runPromise(source.capture(ctx, event, profile, "Extract facts, never promote proposals", "complete tracker/frontier", contextJSON(entries)))
    assert.deepEqual(captured.proof.nativeMessages.map(m => m.messageId), ["msg_first", "msg_cut"])
    assert.equal(captured.artifact.pendingNativeMessages[0]!.messageId, "msg_late")
    assert.match(captured.artifact.nativeMessages[0]!.rawJSON, /"nativeFloat":1.25/)
    assert.match(captured.artifact.profileJSON, /9223372036854775807/)
    assert.equal(captured.proof.renderedRequestDigest, createHash("sha256").update(contextJSON({
      system: captured.artifact.system, prompt: captured.artifact.prompt })).digest("hex"))
    assert.equal((await source.read("ses_source", captured.artifact.locatorId, captured.digest)).scope.assignmentId, id)
    // Explicit MOCK native generation: the production helper composes the actual Effect APIs,
    // while this fixture drives their hooks and cancellation without any model/provider request.
    const hooks = new Map<string, (event: any) => Effect.Effect<void, unknown>>()
    let mode: "good" | "wrong-draft" | "promote" | "omit" | "cancel" = "good", generations = 0, interrupted = false
    let entered!: () => void
    const enteredModel = new Promise<void>(resolve => { entered = resolve })
    const extractionContext = { app: { version: "2.2.5" }, session: {
      ...ctx.session,
      hook: (name: string, callback: (event: any) => Effect.Effect<void, unknown>) => Effect.acquireRelease(
        Effect.sync(() => { hooks.set(name, callback); return { dispose: Effect.sync(() => { hooks.delete(name) }) } }),
        () => Effect.sync(() => { hooks.delete(name) })),
      generate: ({ sessionID, prompt }: { sessionID: string; prompt: string }): Effect.Effect<{ text: string }, unknown> => Effect.gen(function* () {
        const request = { ...event, sessionID, system: [{ type: "text", text: "must not inherit" }],
          messages: [Message.user(mode === "wrong-draft" ? "unrelated generation" : prompt)],
          tools: { write: { description: "must not inherit" } }, options: { unrelated: "must not inherit" } }
        yield* hooks.get("generate")!(request)
        assert.deepEqual(request.tools, {})
        assert.deepEqual(request.options, { maxTokens: 64, effort: "MOCK-explicit" })
        yield* hooks.get("model.request")!({ sessionID, model: event.model, kind: "generate", headers: {} })
        generations++
        if (mode === "cancel") {
          entered()
          yield* Effect.never.pipe(Effect.onInterrupt(() => Effect.sync(() => { interrupted = true })))
        }
        return { text: JSON.stringify({ formatVersion: 1, entries: [{ status: mode === "promote" ? "accepted-reference" : "proposed",
          text: "A proposed improvement is not an accepted norm", sourceMessageIds: ["msg_first"], obligationIds: mode === "omit" ? [] : [other],
          uncertainty: "not effectiveness-qualified", exactFragments: ["Before cut"] }], gaps: [], pending: [] }) }
      }),
    } } as unknown as Plugin.Context
    const extraction = new NativeContextExtraction(extractionContext, source, { profileJSON: profile,
      model: event.model, baseURL: null, routeID: "MOCK", effortOptionKey: "effort", nativeOptions: { maxTokens: 64, effort: "MOCK-explicit" } })
    await Effect.runPromise(Effect.scoped(Effect.gen(function* () {
      yield* extraction.register()
      const result = yield* extraction.extract(event, "ALL canonical remaining obligations", contextJSON(entries), [other])
      assert.equal(result.entries[0]!.status, "proposed"); assert.equal(event.result, undefined)
      const beforeWrong = generations; mode = "wrong-draft"
      const wrong = yield* extraction.extract(event, "basis", contextJSON(entries), [other]).pipe(Effect.exit)
      assert.equal(wrong._tag, "Failure"); assert.equal(generations, beforeWrong)
      mode = "promote"
      const promoted = yield* extraction.extract(event, "basis", contextJSON(entries), [other]).pipe(Effect.exit)
      assert.equal(promoted._tag, "Failure")
      mode = "omit"
      const omitted = yield* extraction.extract(event, "basis", contextJSON(entries), [other]).pipe(Effect.exit)
      assert.equal(omitted._tag, "Failure")
      mode = "cancel"
      const fiber = yield* extraction.extract(event, "basis", contextJSON(entries), [other]).pipe(Effect.forkScoped)
      yield* Effect.promise(() => enteredModel)
      yield* Fiber.interrupt(fiber)
      assert.equal(interrupted, true); assert.equal(event.result, undefined)
      mode = "good"
      const afterCancel = yield* extraction.extract(event, "basis", contextJSON(entries), [other])
      assert.equal(afterCancel.entries[0]!.status, "proposed") // owned lease was finalized, not orphaned
      const title = { sessionID: "ses_source" }
      yield* hooks.get("title")!(title)
      assert.equal((title as { result?: string }).result, "Writhelm managed work")
    })))
    assert.equal(hooks.size, 0)
    await assert.rejects(source.read("ses_other", captured.artifact.locatorId, captured.digest))
    await assert.rejects(source.read("ses_source", captured.artifact.locatorId, "0".repeat(64)))
    readable = false
    await assert.rejects(source.read("ses_source", captured.artifact.locatorId, captured.digest))
    const before = calls
    assert.equal((await source.proof(captured.artifact.locatorId)).packetDigest, captured.proof.packetDigest)
    assert.equal(calls, before) // immutable runtime-facts proof never reenters service authority
    readable = true
    await assert.rejects(Effect.runPromise(source.capture(ctx, { ...event, messages: [{ id: "msg_missing" }] } as unknown as SessionCompaction,
      profile, "system", "basis", contextJSON(entries))))
    await assert.rejects(Effect.runPromise(source.capture(ctx, event, profile, "system", "basis", "forged packet")))
    observations = [native("msg_first", "not silently redacted", { headers: { authorization: "PRIVATE" } }), native("msg_cut", "tail")]
    await assert.rejects(Effect.runPromise(source.capture(ctx, event, profile, "system", "basis", contextJSON(entries))))
    assert.equal(event.result, undefined)
  } finally { await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) }
})
