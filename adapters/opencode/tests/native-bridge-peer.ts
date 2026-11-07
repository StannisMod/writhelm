// SPDX-License-Identifier: AGPL-3.0-only
// Component executable: REAL HTTP/credentials/custody/bridge, explicitly MOCK external native SDK.
// Synthetic fixture keys arrive on stdin only. No host constructor, provider, profile or inference.
import { createInterface } from "node:readline"
import * as fs from "node:fs/promises"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import type { SessionApi } from "@opencode/client/promise/api"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { prepareNativePeerProofs } from "../src/native-bootstrap-secrets.js"

const lines = createInterface({ input: process.stdin, crlfDelay: Infinity })
const input = lines[Symbol.asyncIterator]()
const first = await input.next()
if (first.done) throw new Error("explicit component configuration required")
const config = JSON.parse(first.value)
const hooks = new Map<string, (event: { sessionID: string }) => Promise<void>>()
const effectsFile = path.join(config.root, "mock-native-effects.json")
let effects: { wakes: number; interrupts: number; payload?: any; instructions: Array<{ key: string; value: string }> }
try { effects = JSON.parse(await fs.readFile(effectsFile, "utf8")) }
catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error; effects = { wakes: 0, interrupts: 0, instructions: [] } }
const save = () => fs.writeFile(effectsFile, JSON.stringify(effects), { mode: 0o600 })
const ctx = { app: { version: "2.2.5" }, session: {
  get: async ({ sessionID }: { sessionID: string }) => {
    if (![config.controllerSessionID, config.sessionID].includes(sessionID)) throw new Error("MOCK wrong native receiver")
    return { id: sessionID, location: { directory: config.workRoot } }
  },
  hook: async (name: string, callback: (event: { sessionID: string }) => Promise<void>) => {
    hooks.set(name, callback); return { dispose: async () => { hooks.delete(name) } }
  },
  instructions: { entry: {
    list: async () => effects.instructions,
    put: async ({ key, value }: { key: string; value: string }) => {
      effects.instructions = [...effects.instructions.filter(i => i.key !== key), { key, value }]; await save()
    },
  } },
  synthetic: async (value: { sessionID: string; id: string; text: string; metadata: unknown }) => {
    effects.wakes++ // Duplicate original native ID does NOT deduplicate native wake.
    effects.payload = { id: value.id, sessionID: value.sessionID, type: "synthetic", payload: { text: value.text, metadata: value.metadata } }
    await save(); return effects.payload
  },
  interrupt: async () => { effects.interrupts++; await save() }, // NEVER physical fencing evidence
}, tool: { hook: async (_name: string, callback: (event: { sessionID: string }) => Promise<void>) => {
  hooks.set("tool", callback); return { dispose: async () => { hooks.delete("tool") } }
} } } as unknown as Plugin.Context
const native = { inbox: { list: async () => effects.payload ? [effects.payload] : [] },
  message: { get: async () => { if (!effects.payload) throw new Error("MOCK absent"); return effects.payload } },
  context: async () => effects.payload ? [effects.payload] : [] } as unknown as Pick<SessionApi, "inbox" | "message" | "context">
const custody = new LinuxNativeCustody(path.join(config.root, "custody"), [config.workRoot]); await custody.initialize()
const proofs = await prepareNativePeerProofs(custody, { serviceProof: Buffer.from(config.serviceProof, "base64url"),
  transportProof: Buffer.from(config.transportProof, "base64url") })
proofs.serviceProof.fill(0); proofs.transportProof.fill(0)
const service = new NativeRuntimeClient(config.serviceOrigin, config.serviceProof, config.storeId, config.projectId, config.controllerId, config.hostId,
  async (url, init) => {
    const response = await fetch(url, init)
    if (!response.ok) process.stderr.write(`service-refused: ${response.status}\n`)
    return response
  })
const registry = new NativeCredentialRegistry(ctx, custody, service, config.serviceOrigin,
  new LinuxWorkRequestCustody(path.join(config.root, "requests"), [config.workRoot]), "a".repeat(64))
const probe = registry.probe.bind(registry)
registry.probe = async (...args) => {
  try { return await probe(...args) }
  catch (error) { process.stderr.write(`probe: ${error instanceof Error ? error.message : "refused"}\n`); throw error }
}
await registry.adopt(config.controllerPrincipal, config.controllerKey, config.controllerToken)
await registry.adopt(config.workerPrincipal, config.workerKey, config.workerToken)
await registry.bind(config.controllerSessionID, config.controllerKey, config.workRoot)
await registry.bind(config.sessionID, config.workerKey, config.workRoot, config.subject)
const bridge = new ProtectedNativeBridge(registry, custody, config.controllerSessionID, config.hostId)
// Private component diagnostics only: do not log token-bearing configuration or packet/source text.
const retainOriginal = bridge.retainOriginal.bind(bridge)
bridge.retainOriginal = async (...args) => {
  try { await retainOriginal(...args) }
  catch (error) { process.stderr.write(`retain-original: ${error instanceof Error ? error.message : "refused"}\n`); throw error }
}
const current = bridge.current.bind(bridge)
bridge.current = async envelope => { const value = await current(envelope); process.stderr.write(`current: ${value}\n`); return value }
await bridge.manage(config.sessionID)
const adapter = new ConnectedNativeAdapter(ctx, native, bridge, config.hostId); await adapter.register()
const transport = await listenNativeTransport(config.transportOrigin, Buffer.from(config.transportProof, "base64url"), bridge, adapter)
const emit = (value: unknown) => process.stdout.write(JSON.stringify(value) + "\n")
emit({ ready: true, externalNative: "MOCK" })
try {
  for (;;) {
    const next = await input.next(); if (next.done) break
    const command = JSON.parse(next.value)
    if (command.operation === "finish") break
    if (command.operation !== "probe") throw new Error("unknown component operation")
    let admitted = false
    try { await hooks.get("tool")!({ sessionID: config.sessionID }); admitted = true } catch { /* held */ }
    emit({ wakes: effects.wakes, interrupts: effects.interrupts, admitted, instructions: effects.instructions.length })
  }
} finally { await transport.close(); await adapter.close(); await bridge.close(); lines.close(); process.stdin.pause() }
