// SPDX-License-Identifier: AGPL-3.0-only
import { dialog } from "electron"
import path from "node:path"
import { randomUUID } from "node:crypto"
import { Schema } from "effect"
import type { Plugin } from "@opencode/plugin/gui"
import { Review, Inputs, reply, Fault, requireThat, type Session, type Reply } from "./contracts.js"
import { Controller } from "./controller.js"
import { openProtected } from "./protected.js"
import { prepareSetup, PublicSetupRunner, type JavaRuntime } from "./helper.js"

export const main: Plugin.MainSetup = async ctx => {
  requireThat(ctx.app.platform === "desktop" && ctx.app.version === "2.2.5", "UNAVAILABLE")
  const verify = async (s: Session, window: number) => {
    requireThat(ctx.windows.get(window) && s.server === "sidecar", "FORBIDDEN")
    const actual = await ctx.client.session.get({ sessionID: s.id })
    requireThat(actual.id === s.id && actual.location.directory === s.directory && s.key === `${s.server}\n${s.id}`, "STALE_VIEW")
    const projects = await ctx.client.project.list()
    requireThat(projects.some(p => p.canonical === s.projectRoot || p.sandboxes.includes(s.projectRoot)), "FORBIDDEN")
  }
  const projects = async (s: Session) => [...new Set([s.directory, s.projectRoot, ...(await ctx.client.project.list()).flatMap(p => [p.canonical, ...p.sandboxes])])]
  const controller = new Controller({ verify })
  const rawRuntime: unknown = ctx.options.javaRuntime
  let runtime: JavaRuntime | undefined
  if (rawRuntime !== undefined) {
    requireThat(rawRuntime !== null && typeof rawRuntime === "object" && !Array.isArray(rawRuntime))
    const value = rawRuntime as Record<string, unknown>
    requireThat(Object.keys(value).sort().join(",") === "classpath,executable" && typeof value.executable === "string" && Array.isArray(value.classpath) && value.classpath.length <= 32 && value.classpath.every(p => typeof p === "string"))
    runtime = { executable: value.executable, classpath: value.classpath }
  }
  const runner = new PublicSetupRunner(runtime)
  const saved = ctx.storage.store("profile-location", { schema: Schema.Struct({ root: Schema.String }), initial: { root: "" } })
  let connected = false
  const ensure = async (s: Session) => {
    if (!connected && saved.value.root) { await controller.attach(await openProtected(saved.value.root, await projects(s))); connected = true }
  }
  const safe = async (body: () => Promise<Reply>) => { try { return await body() } catch (e) { return reply(e instanceof Fault ? e.code : "UNAVAILABLE") } }
  ctx.provide(Review, {
    status: (i, c) => safe(async () => { await verify(i.session, c.window); await ensure(i.session); return controller.dispatch("status", i, c) }),
    connect: (i, c) => safe(async () => {
      Schema.decodeUnknownSync(Inputs.connect)(i); await verify(i.session, c.window)
      const win = ctx.windows.get(c.window)!
      const picked = await dialog.showOpenDialog(win, { properties: ["openFile"], title: "Select protected Writhelm profile.json (never paste a key)", filters: [{ name: "Protected profile", extensions: ["json"] }] })
      if (picked.canceled) return reply("UNCONFIGURED")
      requireThat(path.basename(picked.filePaths[0]) === "profile.json", "PROTECTION_FAILED")
      const root = path.dirname(picked.filePaths[0]); await controller.attach(await openProtected(root, await projects(i.session)))
      saved.set({ root }); connected = true; return controller.dispatch("status", i, c)
    }),
    setup: (i, c) => safe(async () => {
      Schema.decodeUnknownSync(Inputs.setup)(i); await verify(i.session, c.window)
      const win = ctx.windows.get(c.window)!
      const clientDir = await dialog.showOpenDialog(win, { properties: ["openDirectory"], title: "Parent for NEW protected client directory outside projects" })
      if (clientDir.canceled) return reply("UNCONFIGURED")
      const storeDir = await dialog.showOpenDialog(win, { properties: ["openDirectory"], title: "Parent for NEW private Writhelm store (never an existing store)" })
      if (storeDir.canceled) return reply("UNCONFIGURED")
      const root = path.join(clientDir.filePaths[0], "writhelm-client-" + randomUUID())
      const store = path.join(storeDir.filePaths[0], "writhelm-store-" + randomUUID())
      const forbidden = await projects(i.session)
      for (const project of forbidden) { const relative = path.relative(path.resolve(project), store); requireThat(relative !== "" && (relative.startsWith(".." + path.sep) || path.isAbsolute(relative)), "PROTECTION_FAILED") }
      const files = await openProtected(root, forbidden, true)
      let handedOff = false
      try {
        const profile = prepareSetup(i.origin, i.session, store)
        await files.write(profile); await files.writeAgent(profile)
        handedOff = true; await controller.attach(files) // attach owns disposal, including refusal
      } finally { if (!handedOff) await files.close() }
      saved.set({ root }); connected = true
      return controller.finishSetup(runner, false)
    }),
    pending: (i, c) => controller.dispatch("pending", i, c), open: (i, c) => controller.dispatch("open", i, c),
    answer: (i, c) => controller.dispatch("answer", i, c), reconcile: (i, c) => controller.retry(i, c, runner),
    rotate: (i, c) => controller.dispatch("rotate", i, c), dismiss: (i, c) => controller.dispatch("dismiss", i, c),
    workList: (i,c) => safe(async () => { await verify(i.session,c.window);await ensure(i.session);return controller.dispatch("workList",i,c) }),
    workOpen: (i,c) => safe(async () => { await verify(i.session,c.window);await ensure(i.session);return controller.dispatch("workOpen",i,c) }),
    workBody: (i,c) => safe(async () => { await verify(i.session,c.window);await ensure(i.session);return controller.dispatch("workBody",i,c) }),
  })
  ctx.windows.on("close", window => controller.closeWindow(window.id))
  return () => controller.close()
}
