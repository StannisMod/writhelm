// SPDX-License-Identifier: AGPL-3.0-only
import type { Plugin } from "@opencode/plugin"
import type { ToolContext } from "@opencode/plugin/promise/tool"
import { isLosslessNumber } from "lossless-json"
import { exactJSON, exactLoopback } from "./strict-json.js"
import { connectedCommands, connectedReads } from "./connected-catalogue.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import * as fs from "node:fs/promises"
const invalid = (): never => { throw new Error("invalid exact work JSON/origin") }
const exact = (raw: string) => exactJSON(raw, invalid)

export interface WorkRequestCustody { retain(bindingIdentity: string, requestKey: string, raw: string): Promise<string> }
/** Operational original-command custody, not canonical work/authority. Explicit Linux adapter;
 * never installs a profile or selects a default directory. Raw bearer secrets are not stored. */
export class LinuxWorkRequestCustody implements WorkRequestCustody {
  private readonly storage: LinuxNativeCustody
  constructor(private readonly root: string, forbiddenWorkAreas: readonly string[]) {
    this.storage = new LinuxNativeCustody(root, forbiddenWorkAreas, 256)
  }
  async requireOutside(work: string): Promise<void> { await this.storage.requireOutside(work) }
  async retain(bindingIdentity: string, requestKey: string, raw: string): Promise<string> {
    if (!/^[0-9a-f-]{36}$/.test(requestKey)) throw new Error("unqualified request custody")
    await this.storage.initialize()
    // Do not interpret an older retained layout as an empty new store and dispatch again.
    // No implicit custody migration or deletion of unresolved original requests.
    if ((await fs.readdir(this.root)).some(name => !/^work-request-[0-9a-f]{64}\.json$/.test(name)))
      throw new Error("unrecognized or unresolved request custody layout")
    // Private operational format only. Retain exact command bytes under an unambiguous scoped
    // identity; shared storage enforces ancestor/owner/link rules before any publication.
    await this.storage.retain("work-request", JSON.stringify([bindingIdentity, requestKey]), raw)
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
