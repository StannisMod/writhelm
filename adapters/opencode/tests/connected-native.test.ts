// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import type { Plugin } from "@opencode/plugin"
import type { SessionApi } from "@opencode/client/promise/api"
import { ConnectedNativeAdapter, type ConnectedNativeBridge, type RetainedConnectedEnvelope } from "../src/connected-native.js"

// Explicit SDK-shaped MOCK. No host/client constructor, inference, installed profile or network call.
function fixture() {
  const rawJSON = '{"assignment":{"generation":9007199254740993},"opaque":"exact service bytes"}'
  const envelope: RetainedConnectedEnvelope = { key: "k", rawJSON, digest: createHash("sha256").update(rawJSON).digest("hex"), bindingJSON: "exact-binding-json",
    hostID: "host", sessionID: "ses_mock", messageID: "msg_mock", text: "Blocker B resolved; continue.",
    metadata: { continuationId: "condition", assignmentId: "assignment", executionGeneration: "9007199254740993" },
    instructions: [{ key: "writhelm-task", value: "exact assigned task" }] }
  const hooks = new Map<string, (event: { sessionID: string }) => Promise<void>>()
  let payload: unknown, wakes = 0, claims = 0, state: "qualified-quiescent" | "active" | "held" = "qualified-quiescent"
  let loseReply = false, admitWithoutWake = false, badBody = false, interrupts = 0
  let failBeforeAdmit = false, qualifiedAbsent = false
  const pauses = new Set<string>()
  let nextCurrent: { enter: () => void; wait: Promise<void> } | undefined
  let observed!: () => void
  const boundary = new Promise<void>(resolve => { observed = resolve })
  const observations: string[] = []
  const bridge: ConnectedNativeBridge = {
    retained: async () => envelope, managed: sessionID => sessionID === envelope.sessionID,
    current: async () => {
      const captured = state, pending = nextCurrent; nextCurrent = undefined
      if (pending) { pending.enter(); await pending.wait }
      return captured
    }, binding: async () => envelope.bindingJSON,
    retainObservation: async (_key, raw) => { observations.push(raw); if (raw.includes("held-boundary")) observed() },
    observations: async () => observations,
    claimDispatch: async () => { if (claims) return false; claims++; return true },
    claimPauseDispatch: async (_key, _digest, intent) => {
      assert.equal(state, "held"); assert.equal(intent, "exact-pause-intent")
      if (pauses.has(intent)) return false
      pauses.add(intent); return true
    },
    absentAndQuiescent: async () => qualifiedAbsent,
  }
  const ctx = {
    app: { version: "2.2.5" },
    session: {
      get: async () => ({ id: envelope.sessionID }),
      interrupt: async () => { interrupts++; throw new Error("MOCK lost interrupt acknowledgement") },
      instructions: { entry: { list: async () => [...envelope.instructions] } },
      hook: async (name: string, callback: (event: { sessionID: string }) => Promise<void>) => { hooks.set(name, callback); return { dispose: async () => {} } },
      synthetic: async (input: Record<string, unknown>) => {
        if (failBeforeAdmit) throw new Error("MOCK original dispatch ended before admission")
        payload = { id: input.id, sessionID: input.sessionID, type: "synthetic", payload: { text: badBody ? "forged body" : input.text, metadata: input.metadata } }
        // Native ID admission dedup would NOT prevent another wake on another invocation.
        if (!admitWithoutWake) wakes++
        if (loseReply || admitWithoutWake) throw new Error("mock external acknowledgement lost")
        return payload
      },
    },
    tool: { hook: async (_name: string, callback: (event: { sessionID: string }) => Promise<void>) => { hooks.set("tool", callback); return { dispose: async () => {} } } },
  } as unknown as Plugin.Context
  const client = { inbox: { list: async () => payload ? [payload] : [] }, message: { get: async () => { if (!payload) throw new Error("absent"); return payload } }, context: async () => payload ? [payload] : [] } as unknown as Pick<SessionApi, "inbox" | "message" | "context">
  const adapter = new ConnectedNativeAdapter(ctx, client, bridge, "host")
  return { adapter, envelope, hooks, boundary, observations, get wakes() { return wakes },
    freshAdapter() { return new ConnectedNativeAdapter(ctx, client, bridge, "host") },
    get interrupts() { return interrupts },
    suspendCurrent() {
      let enter!: () => void, resume!: () => void
      const entered = new Promise<void>(resolve => { enter = resolve }), wait = new Promise<void>(resolve => { resume = resolve })
      nextCurrent = { enter, wait }; return { entered, resume }
    },
    failBeforeAdmit() { failBeforeAdmit = true },
    qualifyAbsent() { assert.equal(payload, undefined); qualifiedAbsent = true },
    authorizeLinkedRetry() { assert.equal(qualifiedAbsent, true); assert.equal(payload, undefined); claims = 0; failBeforeAdmit = false },
    active() { state = "active" }, revoke() { state = "held" },
    lose() { loseReply = true }, noWake() { admitWithoutWake = true }, forge() { badBody = true } }
}

test("MOCK pinned synthetic path: original admission+wake, atomic no-repeat claim, active release", async () => {
  const f = fixture(); await f.adapter.register(); assert.equal(await f.adapter.hold("k"), true)
  await assert.rejects(f.hooks.get("tool")!({ sessionID: "ses_mock" }))
  assert.equal(await f.adapter.deliver("k"), "matched-wake")
  await assert.rejects(f.adapter.deliver("k"), /reconcile original dispatch/)
  assert.equal(f.wakes, 1)
  await assert.rejects(f.adapter.release("k"), /canonical active/)
  f.active(); await f.adapter.release("k"); await f.hooks.get("tool")!({ sessionID: "ses_mock" })
  assert.equal(f.envelope.metadata.executionGeneration, "9007199254740993")
  assert.match(f.envelope.rawJSON, /9007199254740993/)
  f.revoke(); await assert.rejects(f.hooks.get("tool")!({ sessionID: "ses_mock" }))
  await f.adapter.close()
})
test("MOCK released ACTIVE rehold precedes exact pause; lost interrupt reply cannot confirm or replay", async () => {
  const f = fixture(); await f.adapter.register(); await f.adapter.hold("k")
  await f.adapter.deliver("k"); f.active(); await f.adapter.release("k")
  await f.hooks.get("tool")!({ sessionID: "ses_mock" })
  await assert.rejects(f.adapter.hold("k"), /cannot silently reclose/)
  f.revoke(); await f.adapter.rehold("k")
  await assert.rejects(f.hooks.get("tool")!({ sessionID: "ses_mock" }))
  await f.adapter.pause("k", "exact-pause-intent"); await f.adapter.pause("k", "exact-pause-intent")
  assert.equal(f.interrupts, 1); assert.equal(f.wakes, 1)
  assert.ok(f.observations.some(v => v.includes("pause-outcome-unknown")))
  await assert.rejects(f.adapter.release("k"), /canonical active/)
  await f.adapter.close()
})
test("MOCK lost reply needs actual held-boundary evidence, not inbox equality", async () => {
  const f = fixture(); f.lose(); await f.adapter.register(); await f.adapter.hold("k")
  assert.equal(await f.adapter.deliver("k"), "unknown")
  assert.equal(await f.adapter.reconcile("k"), "unknown")
  const pending = f.hooks.get("context")!({ sessionID: "ses_mock" })
  await f.boundary
  assert.equal(await f.adapter.reconcile("k"), "matched-wake")
  f.active(); await f.adapter.release("k"); await pending
  assert.equal(f.wakes, 1); await assert.rejects(f.adapter.deliver("k"))
  await f.adapter.close()
})
test("MOCK rehold invalidates in-flight tool and context admissions even when old current read returns active", async () => {
  for (const hook of ["tool", "context"]) {
    const f = fixture(); await f.adapter.register(); await f.adapter.hold("k")
    await f.adapter.deliver("k"); f.active(); await f.adapter.release("k")
    const cut = f.suspendCurrent(), admission = f.hooks.get(hook)!({ sessionID: "ses_mock" })
    const rejected = assert.rejects(admission)
    await cut.entered; f.revoke(); await f.adapter.rehold("k"); cut.resume(); await rejected
    assert.equal(f.wakes, 1); await f.adapter.close()
  }
})
test("MOCK admit-before-wake crash holds; same-ID wrong body conflicts; neither proves wake", async () => {
  const f = fixture(); f.noWake(); await f.adapter.register(); await f.adapter.hold("k")
  assert.equal(await f.adapter.deliver("k"), "unknown"); assert.equal(await f.adapter.reconcile("k"), "unknown")
  assert.equal(f.wakes, 0); await assert.rejects(f.adapter.deliver("k")); await f.adapter.close()
  const g = fixture(); g.forge(); await g.adapter.register(); await g.adapter.hold("k")
  assert.equal(await g.adapter.deliver("k"), "conflict"); assert.equal(await g.adapter.reconcile("k"), "conflict")
  await g.adapter.close()
})
test("MOCK positive not-performed requires qualified original quiescence before linked new-attempt dispatch", async () => {
  const f = fixture(); f.failBeforeAdmit(); await f.adapter.register(); await f.adapter.hold("k")
  assert.equal(await f.adapter.deliver("k"), "unknown")
  assert.equal(await f.adapter.reconcile("k"), "unknown")
  await assert.rejects(f.adapter.deliver("k"), /reconcile original dispatch/)
  f.qualifyAbsent(); assert.equal(await f.adapter.reconcile("k"), "not-performed")
  f.authorizeLinkedRetry() // protected MOCK bridge changes its canonical current armed-attempt claim
  assert.equal(await f.adapter.deliver("k"), "matched-wake"); assert.equal(f.wakes, 1)
  await assert.rejects(f.adapter.deliver("k"), /reconcile original dispatch/)
  await f.adapter.close()
})
test("MOCK fresh adapter rebuilds local gate from retained receiver and protected original-dispatch facts", async () => {
  const f = fixture(); await f.adapter.register(); await f.adapter.hold("k")
  assert.equal(await f.adapter.deliver("k"), "matched-wake"); await f.adapter.close()
  const fresh = f.freshAdapter(); await fresh.register(); assert.equal(await fresh.hold("k"), true)
  assert.equal(await fresh.reconcile("k"), "matched-wake")
  await assert.rejects(fresh.deliver("k"), /reconcile original dispatch/)
  f.active(); await fresh.release("k"); assert.equal(f.wakes, 1)
  await fresh.close()
})
