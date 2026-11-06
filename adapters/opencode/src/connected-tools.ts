// SPDX-License-Identifier: AGPL-3.0-only
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import { connectedCommands, connectedReads } from "./connected-catalogue.js"
import { createHash } from "node:crypto"
import * as fs from "node:fs/promises"
import path from "node:path"
const invalid = (): never => { throw new Error("invalid exact work JSON/origin") }
const exact = (raw: string) => exactJSON(raw, invalid)

export interface WorkRequestCustody { retain(bindingIdentity: string, requestKey: string, raw: string): Promise<string> }
/** Operational original-command custody, not canonical work/authority. Explicit Linux adapter;
 * never installs a profile or selects a default directory. Raw bearer secrets are not stored. */
export class LinuxWorkRequestCustody implements WorkRequestCustody {
  constructor(private readonly root: string, private readonly forbiddenWorkAreas: readonly string[]) {}
  async retain(bindingIdentity: string, requestKey: string, raw: string): Promise<string> {
    if (process.platform !== "linux" || !path.isAbsolute(this.root) || !/^[0-9a-f-]{36}$/.test(requestKey)) throw new Error("unqualified request custody")
    for (const work of this.forbiddenWorkAreas) {
      const relative = path.relative(path.resolve(work), path.resolve(this.root))
      if (!relative || !relative.startsWith(".." + path.sep) && !path.isAbsolute(relative)) throw new Error("custody inside work area")
    }
    await fs.mkdir(this.root, { recursive: true, mode: 0o700 })
    const stat = await fs.lstat(this.root)
    if (!stat.isDirectory() || stat.isSymbolicLink() || stat.uid !== process.getuid!() || stat.mode & 0o077
      || await fs.realpath(this.root) !== path.resolve(this.root)) throw new Error("unprotected request custody")
    const prefix = createHash("sha256").update(bindingIdentity).digest("hex")
    const name = `${prefix}-${requestKey}.json`, file = path.join(this.root, name)
    try {
      const old = await fs.lstat(file)
      if (!old.isFile() || old.isSymbolicLink() || old.mode & 0o077 || old.size > 2 * 1024 * 1024) throw new Error("retained request unavailable")
      const original = await fs.readFile(file, "utf8")
      if (original !== raw) throw new Error("retained request identity conflict; reconcile original bytes")
      return original
    } catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error }
    let bytes = 0, count = 0
    for await (const entry of await fs.opendir(this.root)) {
      if (++count > 256) throw new Error("request custody exhausted")
      if (!/^[0-9a-f]{64}-[0-9a-f-]{36}\.json$/.test(entry.name)) throw new Error("invalid retained request entry")
      const existing = await fs.lstat(path.join(this.root, entry.name))
      if (!existing.isFile() || existing.isSymbolicLink() || existing.size > 2 * 1024 * 1024) throw new Error("invalid retained request entry")
      bytes += existing.size
    }
    if (count >= 256 || Buffer.byteLength(raw) > 64 * 1024 * 1024 - bytes) throw new Error("request custody exhausted")
    const handle = await fs.open(file, "wx", 0o600)
    try { await handle.writeFile(raw, "utf8"); await handle.sync() } finally { await handle.close() }
    const directory = await fs.open(this.root, "r")
    try { await directory.sync() } finally { await directory.close() }
    return raw
  }
}

/** Implemented by protected server-controller enrollment/binding custody. Resolution uses actual
 * ToolContext+trusted host incarnation, never a model argument/session label or GUI-owner profile.
 * Only verified controller or registered assignment credentials may enter this interface. */
export interface BoundWorkChannel {
  readonly projectID: string
  readonly scope: "controller" | "assignment"
  send(route: "commands" | "reads", rawJSON: string, signal: AbortSignal): Promise<string>
}
export interface NativeWorkCredentialResolver {
  resolve(actual: Pick<ToolContext, "sessionID" | "messageID" | "id" | "signal">): Promise<BoundWorkChannel | undefined>
}

/** Concrete bounded raw-byte HTTP channel. Credentials live in the closure, never tools, metadata,
 * replies or logs. Called only with the verified bounded credential from the protected resolver.
 * No default origin/key, credential transport field, arbitrary URL or owner forwarding route. */
export function boundedWorkChannel(projectID: string, scope: "controller" | "assignment", origin: string, token: string,
  maxReplyBytes: number, custody: WorkRequestCustody, bindingIdentity: string, fetcher: typeof fetch = fetch): BoundWorkChannel {
  exactLoopback(origin, invalid)
  if (!["controller", "assignment"].includes(scope) || !token.startsWith("wh1.") || maxReplyBytes < 1024 || maxReplyBytes > 4 * 1024 * 1024)
    throw new Error("unqualified bounded work channel")
  return Object.freeze({ projectID, scope, async send(route: "commands" | "reads", rawJSON: string, signal: AbortSignal) {
    if (signal.aborted) throw new Error("work request cancelled before dispatch")
    const parsed = exact(rawJSON)
    if (parsed.projectId !== projectID || !(route === "commands" ? connectedCommands : connectedReads).has(parsed.operation)) throw new Error("work channel scope mismatch")
    if (route === "commands") {
      if (!isLosslessNumber(parsed.formatVersion) || parsed.formatVersion.value !== "3" || typeof parsed.requestKey !== "string") throw new Error("closed command3 required")
      rawJSON = await custody.retain(bindingIdentity + ":" + parsed.operation, parsed.requestKey, rawJSON)
    }
    const response = await fetcher(origin + "/api/v1/" + route, { method: "POST", redirect: "error", credentials: "omit",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + token }, body: rawJSON,
      signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]) })
    if (!response.body) throw new Error("work service unavailable")
    const reader = response.body.getReader(), parts: Uint8Array[] = []; let bytes = 0
    try {
      while (true) {
        const chunk = await reader.read(); if (chunk.done) break
        bytes += chunk.value.byteLength
        if (bytes > maxReplyBytes) { await reader.cancel(); throw new Error("work response exceeds selected native reserve") }
        parts.push(chunk.value)
      }
    } finally { reader.releaseLock() }
    const raw = new TextDecoder("utf-8", { fatal: true }).decode(Buffer.concat(parts))
    exact(raw) // reject malformed/duplicate/unsafe tokens, but return ORIGINAL bytes, not parsed numbers
    return raw
  } })
}

/** Source-qualified actual native work tools. Catalog is convenience only: service current-H policy
 * still denies captured stale executables/credentials. No extra MCP or owner-key tool is registered. */
export async function registerConnectedWorkTools(ctx: Plugin.Context, resolver: NativeWorkCredentialResolver) {
  const registration = await ctx.tool.transform(editor => {
    for (const [name, route, allowed, version, cap] of [
      ["writhelm_work_command", "commands", connectedCommands, "3", 2 * 1024 * 1024],
      ["writhelm_work_read", "reads", connectedReads, "2", 16384],
    ] as const) editor.add({ name, description: "Scoped canonical work operation using exact JSON text; current authority is checked by Writhelm",
      input: { type: "object", additionalProperties: false, required: ["json"], properties: { json: { type: "string", maxLength: cap } } },
      execute: async (input: unknown, actual: ToolContext) => {
        try {
          if (!input || typeof input !== "object" || Array.isArray(input) || Object.keys(input).join(",") !== "json"
            || typeof (input as { json?: unknown }).json !== "string") throw new Error("closed tool input required")
          const raw = (input as { json: string }).json
          if (!raw.isWellFormed() || Buffer.byteLength(raw) > cap) throw new Error("work admission limit")
          const parsed = exact(raw)
          if (!isLosslessNumber(parsed.formatVersion) || parsed.formatVersion.value !== version || !allowed.has(parsed.operation))
            throw new Error("unsupported work operation")
          const channel = await resolver.resolve(actual)
          if (!channel || !["controller", "assignment"].includes(channel.scope) || parsed.projectId !== channel.projectID)
            throw new Error("unbound/stale native work scope")
          const reply = await channel.send(route, raw, actual.signal)
          return { content: reply }
        } catch { return { content: '{"kind":"error","code":"WORK_UNAVAILABLE_OR_FORBIDDEN"}' } } // no secret-bearing exception text
      }, options: { codemode: false } })
  })
  await ctx.tool.reload()
  return registration
}
