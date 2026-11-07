// SPDX-License-Identifier: AGPL-3.0-only
import { test } from "node:test"
import assert from "node:assert/strict"
import { createServer } from "node:http"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"

const id = "00000000-0000-0000-0000-000000000001"
const token = `wh1.${id}.${id}.${Buffer.alloc(32, 1).toString("base64url")}`
const proof = Buffer.alloc(32, 2).toString("base64url")
const subject = { assignmentId: id, executionGeneration: "9223372036854775807", bindingJSON: "exact retained binding" }

test("real local HTTP client preserves exact generation and refuses inconsistent service permits", async () => {
  let reply: Record<string, unknown> = { formatVersion: 1, basis: {}, active: true, prepare: false,
    dispatch: false, pause: false, delivery: false, quiescent: false, freshEnvelope: false, freshPause: false, bindingJSON: subject.bindingJSON, nativeHostId: id,
    nativeSessionId: "ses_fixture", intentId: null, intentOperation: null, attemptId: null, envelopeDigest: null }
  let received: Record<string, unknown> | undefined
  let rateLimited = 0
  const server = createServer(async (request, response) => {
    assert.equal(request.url, "/internal/native/v1")
    assert.equal(request.headers.authorization, `Bearer ${token}`)
    assert.equal(request.headers["x-writhelm-native-proof"], proof)
    const parts: Buffer[] = []
    for await (const part of request) parts.push(Buffer.from(part))
    received = JSON.parse(Buffer.concat(parts).toString("utf8"))
    if (rateLimited > 0) { rateLimited--; response.writeHead(429); response.end("{}"); return }
    response.setHeader("Content-Type", "application/json")
    response.end(JSON.stringify(reply))
  })
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve))
  try {
    const address = server.address()
    assert.ok(address && typeof address !== "string")
    const client = new NativeRuntimeClient(`http://127.0.0.1:${address.port}`, proof, id, id, id, id)
    assert.equal((await client.status(token, subject)).active, true)
    assert.equal(received?.executionGeneration, subject.executionGeneration)
    const valid = { ...reply }
    for (const invalid of [
      { bindingJSON: null }, { bindingJSON: "different binding" }, { nativeHostId: null },
      { intentOperation: "invented" }, { intentOperation: "pause" },
      { quiescent: true },
    ]) {
      reply = { ...valid, ...invalid }
      await assert.rejects(client.status(token, subject), /unavailable or forbidden/)
    }
    reply = { ...valid, active: false, bindingJSON: null, nativeHostId: null, nativeSessionId: null }
    assert.equal((await client.status(token, subject)).active, false)
    reply = valid; rateLimited = 1
    assert.equal((await client.status(token, subject)).active, true) // bounded read recapture, not an effect retry
    const cancel = new AbortController(); cancel.abort()
    await assert.rejects(client.status(token, subject, null, cancel.signal))
  } finally { await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) }
})
