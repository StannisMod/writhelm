// SPDX-License-Identifier: AGPL-3.0-only
// OC-002 required-behavior regressions, not green witnesses of the forbidden observations.
import test from "node:test"
import assert from "node:assert/strict"
import { promises as fs, constants, openSync, closeSync } from "node:fs"
import path from "node:path"
import { randomUUID } from "node:crypto"
import type { Caller } from "@opencode/plugin/gui"
import { Controller } from "../src/controller.js"
import { openProtected, type ProtectedFiles } from "../src/protected.js"
import { credential, prepareSetup, PublicSetupRunner } from "../src/helper.js"
import { Fault, type Session } from "../src/contracts.js"

const base = path.resolve(process.env.OC002_TEST_ROOT!)
const uid = (n: number) => "00000000-0000-0000-0000-" + String(n).padStart(12, "0")
const session: Session = { key: "sidecar\nretention-regression", id: "retention-regression", server: "sidecar", directory: path.join(base, "project"), projectRoot: path.join(base, "project") }
const caller: Caller = { window: 1, signal: new AbortController().signal }
const host = { async verify() {} } // Cooperative host port; no installed-host/publication claim.
function gate() {
  let release!: () => void
  const promise = new Promise<void>(r => { release = r })
  return { promise, release }
}
async function fixture() {
  const root = path.join(base, "private-" + randomUUID())
  const files = await openProtected(root, [session.directory], true)
  const owner = credential(uid(1), uid(2), uid(3)), agent = credential(uid(1), uid(2), uid(5))
  const profile = { formatVersion: 1, origin: "http://127.0.0.1:45678", storeId: uid(1), projectId: uid(2), principalId: uid(3), active: { credentialId: owner.credentialId, token: owner.token }, agent: { principalId: uid(5), credentialId: agent.credentialId, token: agent.token }, pending: null }
  await files.write(profile)
  return { root, files, profile }
}
function wrap(files: ProtectedFiles, overrides: Partial<ProtectedFiles> = {}): ProtectedFiles {
  return { read: () => files.read(), readAgent: () => files.readAgent(), write: v => files.write(v), writeAgent: v => files.writeAgent(v), close: () => files.close(), ...overrides }
}
function settled(commandJSON: string, profile: any) {
  const command = JSON.parse(commandJSON)
  return new Response(JSON.stringify({ formatVersion: 1, kind: "settled", identity: { principalId: profile.principalId, projectId: profile.projectId, operation: command.operation, requestKey: command.requestKey }, fingerprint: "a".repeat(64), validatedAt: { algorithm: "sha1", hex: "b".repeat(40) }, authenticatedByCredentialId: profile.active.credentialId,
    outcome: { kind: "success", operation: "rotate_credential", result: { credentialId: command.payload.newCredentialId, replacedCredentialId: command.payload.credentialId }, domainReplayed: false }, commandReplayed: true, publication: null }))
}

test("IF001: delayed old attach cannot erase retained rotation or permit a replacement attempt", async () => {
  const f = await fixture(), captured = gate(), releaseRead = gate(), entered = gate(), releaseNetwork = gate()
  const sent: string[] = []; let recover = false
  const c = new Controller(host, async (_url, init) => {
    sent.push(String(init!.body))
    if (sent.length === 1) { entered.release(); await releaseNetwork.promise }
    if (!recover) throw new Error("synthetic lost reply; publication not asserted")
    return settled(String(init!.body), f.profile)
  })
  await c.attach(f.files)
  const second = await openProtected(f.root, [session.directory])
  const reconnect = c.attach(wrap(second, { read: async () => { const old = await second.read(); captured.release(); await releaseRead.promise; return old } }))
  try {
    await captured.promise
    const rotation = c.dispatch("rotate", { session }, caller)
    const first = await Promise.race([rotation.then(result => ({ kind: "reply" as const, result })), entered.promise.then(() => ({ kind: "entered" as const }))])
    releaseRead.release(); await reconnect
    releaseNetwork.release()
    let result = await rotation
    // Refusal while attach owns the controller is an allowed safe serialization outcome.
    if (first.kind === "reply" && result.code === "BUSY") result = await c.dispatch("rotate", { session }, caller)
    const retained = await second.read()
    assert.equal(result.pending, true, "A lost rotation reply must retain the unresolved attempt")
    assert.equal(retained.pending?.kind, "rotation")
    const original = retained.pending.commandJSON, replacement = retained.pending.replacement.token
    assert.equal((await c.dispatch("rotate", { session }, caller)).code, "BUSY", "No new rotation may replace the unresolved one")
    assert.equal(sent.length, 1)
    const same = await openProtected(f.root, [session.directory]); await c.attach(same)
    const afterAttach = await same.read()
    assert.equal(afterAttach.pending.commandJSON, original)
    assert.ok(afterAttach.pending.replacement.token === replacement, "Exact replacement secret must survive reattach")
    recover = true
    const replay = await c.dispatch("reconcile", { session }, caller)
    assert.equal(replay.ok, true); assert.equal(sent[1], original)
    assert.ok((await same.read()).active.token === replacement)
  } finally { releaseRead.release(); releaseNetwork.release(); await reconnect.catch(() => {}); await c.close() }
})

test("IF001: attachment reserves across the previous-file close boundary", async () => {
  const f = await fixture(), enteredClose = gate(), releaseClose = gate(); let closes = 0, sends = 0
  const first = wrap(f.files, { close: async () => { closes++; enteredClose.release(); await releaseClose.promise; await f.files.close() } })
  const c = new Controller(host, async () => { sends++; throw new Error("synthetic unavailable") })
  await c.attach(first)
  const next = await openProtected(f.root, [session.directory]); const connecting = c.attach(next)
  try {
    await enteredClose.promise
    const result = await c.dispatch("rotate", { session }, caller)
    assert.equal(result.code, "BUSY", "No mutation may use a file object while attachment closes it")
    assert.equal(sends, 0)
    releaseClose.release(); await connecting
    assert.equal(closes, 1)
    assert.equal((await c.dispatch("rotate", { session }, caller)).pending, true)
  } finally { releaseClose.release(); await connecting.catch(() => {}); await c.close() }
})

test("IF001: mutation reservation covers awaited host verification and closes refused candidates", async () => {
  const f = await fixture(), enteredVerify = gate(), releaseVerify = gate(); let sends = 0, rejectedCloses = 0
  const c = new Controller({ async verify() { enteredVerify.release(); await releaseVerify.promise } }, async () => { sends++; throw new Error("lost reply") })
  await c.attach(f.files)
  const rotation = c.dispatch("rotate", { session }, caller)
  await enteredVerify.promise
  const extra = await openProtected(f.root, [session.directory])
  try {
    const attached = await c.attach(wrap(extra, { close: async () => { rejectedCloses++; await extra.close() } })).then(() => "accepted", e => e instanceof Fault ? e.code : "other")
    assert.equal(attached, "BUSY", "A pending verification cannot switch the mutation's selected profile")
    assert.equal(rejectedCloses, 1)
    releaseVerify.release(); assert.equal((await rotation).pending, true); assert.equal(sends, 1)
  } finally { releaseVerify.release(); await rotation; await c.close() }
})

test("IF001: shutdown during an awaited attach cannot resurrect a profile", async () => {
  const f = await fixture(), captured = gate(), releaseRead = gate(); let candidateCloses = 0, sends = 0
  const c = new Controller(host, async () => { sends++; throw new Error("No dispatch after shutdown") }); await c.attach(f.files)
  const candidate = await openProtected(f.root, [session.directory])
  const attaching = c.attach(wrap(candidate, { read: async () => { const p = await candidate.read(); captured.release(); await releaseRead.promise; return p }, close: async () => { candidateCloses++; await candidate.close() } })).then(() => "accepted", () => "refused")
  await captured.promise
  const closing = c.close(); releaseRead.release()
  try {
    assert.equal(await attaching, "refused", "A completed old read cannot install state after shutdown")
    await closing; assert.equal(candidateCloses, 1)
    const result = await c.dispatch("rotate", { session }, caller)
    assert.equal(result.ok, false); assert.equal(sends, 0)
    await c.close(); assert.equal(candidateCloses, 1, "Shutdown is idempotent")
  } finally { releaseRead.release(); await attaching; await closing; await c.close() }
})

test("IF001: setup owns its retained state until helper reconciliation completes", async () => {
  const f = await fixture(), helperEntered = gate(), helperRelease = gate(); let refusedCloses = 0
  const setup = prepareSetup(f.profile.origin, session, path.join(base, "new-store-" + randomUUID()))
  await f.files.write(setup)
  class DelayedHelper extends PublicSetupRunner { override async run() { helperEntered.release(); await helperRelease.promise } }
  const c = new Controller(host); await c.attach(f.files)
  const finishing = c.finishSetup(new DelayedHelper(undefined), true); await helperEntered.promise
  const candidate = await openProtected(f.root, [session.directory])
  try {
    const result = await c.attach(wrap(candidate, { close: async () => { refusedCloses++; await candidate.close() } })).then(() => "accepted", e => e instanceof Fault ? e.code : "other")
    assert.equal(result, "BUSY"); assert.equal(refusedCloses, 1)
    helperRelease.release(); assert.equal((await finishing).ok, true)
    assert.equal((await f.files.read()).pending, null)
  } finally { helperRelease.release(); await finishing; await c.close() }
})

test("IF001: failed candidate read releases reservation without closing the active object", async () => {
  const f = await fixture(); let failedCloses = 0
  const c = new Controller(host, async () => { throw new Error("synthetic lost reply") }); await c.attach(f.files)
  const candidate = await openProtected(f.root, [session.directory])
  await assert.rejects(c.attach(wrap(candidate, { read: async () => { throw new Fault("PROTECTION_FAILED") }, close: async () => { failedCloses++; await candidate.close() } })))
  assert.equal(failedCloses, 1); assert.equal((await f.files.read()).storeId, f.profile.storeId)
  assert.equal((await c.dispatch("rotate", { session }, caller)).pending, true)
  await c.close()
})

test("IF001: duplicate attachment of an owned in-flight candidate never closes it", async () => {
  const f = await fixture(), entered = gate(), releaseRead = gate(); let closes = 0
  const c = new Controller(host); await c.attach(f.files)
  const next = await openProtected(f.root, [session.directory])
  const shared = wrap(next, { read: async () => { const p = await next.read(); entered.release(); await releaseRead.promise; return p }, close: async () => { closes++; await next.close() } })
  const attaching = c.attach(shared)
  try {
    await entered.promise
    await assert.rejects(c.attach(shared), e => e instanceof Fault && e.code === "BUSY")
    assert.equal(closes, 0, "The active attachment, not the refused duplicate, owns this candidate")
    releaseRead.release(); await attaching
    assert.equal((await next.read()).storeId, f.profile.storeId)
    await c.close(); assert.equal(closes, 1)
  } finally { releaseRead.release(); await attaching.catch(() => {}); await c.close() }
})

test("IF001: a previous-file close failure leaves no closed active object and allows fresh retry", async () => {
  const f = await fixture(); let closed = false, closes = 0, staleWrites = 0
  const old = wrap(f.files, { close: async () => { closes++; await f.files.close(); closed = true; throw new Fault("UNAVAILABLE") }, write: async value => { if (closed) staleWrites++; await f.files.write(value) } })
  const c = new Controller(host, async () => { throw new Error("lost response") }); await c.attach(old)
  const incoming = await openProtected(f.root, [session.directory]); await assert.rejects(c.attach(incoming))
  assert.equal((await c.dispatch("rotate", { session }, caller)).code, "UNCONFIGURED")
  assert.equal(staleWrites, 0); assert.equal(closes, 1)
  const fresh = await openProtected(f.root, [session.directory]); await c.attach(fresh)
  assert.equal((await c.dispatch("rotate", { session }, caller)).pending, true)
  await c.close(); assert.equal(closes, 1)
})

test("IF001: shutdown during persistence retains disk state without starting a mutation", async () => {
  const f = await fixture(), persisted = gate(), releaseWrite = gate(); let sends = 0
  const c = new Controller(host, async () => { sends++; throw new Error("must not dispatch after close") })
  await c.attach(wrap(f.files, { write: async value => { await f.files.write(value); persisted.release(); await releaseWrite.promise } }))
  const rotating = c.dispatch("rotate", { session }, caller); await persisted.promise
  const closing = c.close(); releaseWrite.release()
  assert.equal((await rotating).ok, false); await closing; assert.equal(sends, 0)
  const disk = await openProtected(f.root, [session.directory])
  const p = await disk.read(); assert.equal(p.pending.kind, "rotation")
  assert.equal(JSON.parse(p.pending.commandJSON).payload.newCredentialId, p.pending.replacement.credentialId)
  await disk.close(); await c.close()
})

test("IF001: shutdown does not start the old-key fallback after an awaited new-key refusal", async () => {
  const f = await fixture(), entered = gate(), response = gate(); let sends = 0
  const c = new Controller(host, async () => {
    sends++; if (sends === 1) { entered.release(); await response.promise }
    return new Response(JSON.stringify({ formatVersion: 1, kind: "error", code: "UNAUTHENTICATED" }), { status: 401 })
  }); await c.attach(f.files)
  const rotating = c.dispatch("rotate", { session }, caller); await entered.promise
  const closing = c.close(); response.release()
  try {
    assert.equal((await rotating).ok, false); await closing
    assert.equal(sends, 1, "Shutdown must not dispatch an unstarted fallback request")
    const disk = await openProtected(f.root, [session.directory]); assert.equal((await disk.read()).pending.kind, "rotation"); await disk.close()
  } finally { response.release(); await rotating; await closing; await c.close() }
})

async function refusesWithoutWriter(root: string, name: "profile" | "agent", swapped = false) {
  const files = await openProtected(root, [])
  const realOpen = fs.open
  let intercepted = false
  if (swapped) {
    const f = await fixture(); await files.write(f.profile); await f.files.close()
    // Replace the owned regular path at the actual open boundary. A path-only precheck is insufficient.
    const intercept: typeof fs.open = async (file, flags, mode) => {
      if (!intercepted && String(file).endsWith("/profile.json")) {
        intercepted = true
        await fs.rename(path.join(root, "profile.json"), path.join(root, "original.json"))
        await fs.rename(path.join(root, "fifo-ready"), path.join(root, "profile.json"))
      }
      return realOpen(file, flags, mode)
    }
    fs.open = intercept
  }
  let timer: ReturnType<typeof setTimeout> | undefined
  const reading = (name === "profile" ? files.read() : files.readAgent()).then(() => "ACCEPTED", e => e instanceof Fault ? e.code : "OTHER_REFUSAL")
  const observed = await Promise.race([reading, new Promise<string>(r => { timer = setTimeout(() => r("WATCHDOG"), 1000) })])
  try {
    assert.notEqual(observed, "WATCHDOG", "Nonregular open must refuse without a FIFO writer")
    assert.equal(observed, "PROTECTION_FAILED")
    if (swapped) assert.equal(intercepted, true)
  } finally {
    if (timer) clearTimeout(timer)
    // Baseline red run is deliberately released, never killed or abandoned with a blocked open.
    if (observed === "WATCHDOG") { const writer = openSync(path.join(root, name + ".json"), constants.O_WRONLY | constants.O_NONBLOCK); closeSync(writer); await reading }
    fs.open = realOpen
    await files.close()
  }
}
test("IF002: owned profile FIFO refuses under an external watchdog with NO writer", () => refusesWithoutWriter(path.join(base, "fifo-profile"), "profile"))
test("IF002: owned agent FIFO refuses under an external watchdog with NO writer", () => refusesWithoutWriter(path.join(base, "fifo-agent"), "agent"))
test("IF002: regular-to-FIFO replacement at open still gets handle-bound refusal", () => refusesWithoutWriter(path.join(base, "fifo-swap"), "profile", true))
