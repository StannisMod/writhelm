// SPDX-License-Identifier: AGPL-3.0-only
import { dialog } from "electron"
import path from "node:path"
import { randomUUID } from "node:crypto"
import { Schema } from "effect"
import type { Plugin } from "@opencode/plugin/gui"
import { Review, ReviewDrafts, DraftInputs, ApprovalPresentation, OwnerOrderPresentation, OwnerOrderInputs, Inputs, reply, Fault, requireThat, type Session, type Reply, type Input } from "./contracts.js"
import { Controller } from "./controller.js"
import { openProtected } from "./protected.js"
import { prepareSetup, PublicSetupRunner, type JavaRuntime } from "./helper.js"
import { AutomaticApprovalClients } from "./automatic-approval.js"
import { ApprovalDelivery } from "./approval-delivery.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativeOwnerOrder } from "./native-owner-order.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"
import { ReviewDraftStore } from "./review-drafts.js"

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
  const automaticRoot=process.env.WRITHELM_PRIMARY_ROOT
  if(automaticRoot!==undefined)requireThat(path.isAbsolute(automaticRoot),"PROTECTION_FAILED")
  const automatic=automaticRoot?new AutomaticApprovalClients(automaticRoot,{verify},projects):undefined
  const delivery=async(s:Session)=>{
    requireThat(automaticRoot,"UNCONFIGURED")
    const custody=new LinuxNativeCustody(path.join(automaticRoot,"approval-delivery"),await projects(s))
    await custody.initialize()
    return new ApprovalDelivery(custody)
  }
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
  const drafts=async(input:Input<"open">,caller:Parameters<Controller["dispatch"]>[2],writing=false)=>{
    await verify(input.session,caller.window);requireThat(automatic&&automaticRoot,"UNCONFIGURED")
    const client=await automatic.get(input.session,caller.window)
    const items=await client.controller.authorizeDraft(input,caller,writing)
    const custody=new LinuxNativeCustody(path.join(automaticRoot,"review-drafts"),await projects(input.session))
    await custody.initialize()
    return {store:new ReviewDraftStore(custody),items,scope:{storeId:client.storeId,projectId:client.projectId,
      controllerId:client.controllerId,sessionID:input.session.id,requestId:input.requestId,
      requestDigest:input.requestDigest,packetDigest:input.packetDigest}}
  }
  ctx.provide(ReviewDrafts,{
    load:(i,c)=>safe(async()=>{
      Schema.decodeUnknownSync(DraftInputs.load)(i)
      const d=await drafts(i,c)
      return reply("OK",{pageJSON:contextJSON(await d.store.load(d.scope,d.items))})
    }),
    save:(i,c)=>safe(async()=>{
      Schema.decodeUnknownSync(DraftInputs.save)(i)
      // Native draft gate receives only its exact read selector, not write-only IPC fields.
      const d=await drafts({session:i.session,requestId:i.requestId,requestDigest:i.requestDigest,packetDigest:i.packetDigest},c,true)
      return reply("OK",{pageJSON:contextJSON(await d.store.save(d.scope,i.expectedDigest,{comments:i.comments,reason:i.reason},d.items))})
    }),
  })
  const orderView=async(session:Session,caller:Parameters<Controller["dispatch"]>[2])=>{
    await verify(session,caller.window);requireThat(automatic&&automaticRoot,"UNCONFIGURED")
    const client=await automatic.get(session,caller.window)
    const custody=new LinuxNativeCustody(path.join(automaticRoot,"owner-orders"),await projects(session))
    await custody.initialize()
    const order=new NativeOwnerOrder(custody,{storeId:client.storeId,projectId:client.projectId,controllerId:client.controllerId,sessionID:session.id})
    const pending=await order.pending()
    const records:string[]=[]
    if(pending)for(const task of pending.selection.tasks){
      const ref=exactJSON(task.taskJSON,()=>{throw new Fault("INTEGRITY")})
      const result=await client.controller.dispatch("workOpen",{session,family:"task-revisions",
        keyJSON:contextJSON(ref.key),digest:ref.digest,generation:""},caller)
      requireThat(result.ok&&result.bundleJSON,"STALE_VIEW")
      records.push(result.bundleJSON)
    }
    return {order,pending,records}
  }
  ctx.provide(OwnerOrderPresentation,{
    view:(i,c)=>safe(async()=>{
      Schema.decodeUnknownSync(OwnerOrderInputs.view)(i)
      const view=await orderView(i.session,c)
      return reply("OK",{pageJSON:view.pending?contextJSON({...view.pending,records:view.records}):""})
    }),
    select:(i,c)=>safe(async()=>{
      Schema.decodeUnknownSync(OwnerOrderInputs.select)(i)
      // Fresh exact reads on selection as well as rendering: an inaccessible task never becomes
      // launch-authorized through an old native view. The server separately rechecks plan currency.
      const view=await orderView(i.session,c),pending=view.pending
      requireThat(pending&&pending.selection.proposalId===i.proposalId&&pending.proposalDigest===i.proposalDigest&&
        (pending.expectedDecision??"")===i.expectedDecision,"STALE_VIEW")
      await view.order.select(i.proposalId,i.proposalDigest,i.expectedDecision||null)
      return reply()
    }),
  })
  const dispatch=async<K extends keyof typeof Inputs>(name:K,input:Input<K>,caller:Parameters<Controller["dispatch"]>[2])=>safe(async()=>{
    await verify(input.session,caller.window)
    const target=automatic?(await automatic.get(input.session,caller.window)).controller:controller
    if(!automatic)await ensure(input.session)
    return target.dispatch(name,input,caller)
  })
  ctx.provide(ApprovalPresentation,{
    requested:(i,c)=>safe(async()=>{
      Schema.decodeUnknownSync(Inputs.status)(i);await verify(i.session,c.window)
      requireThat(automatic,"UNCONFIGURED")
      const client=await automatic.get(i.session,c.window),journal=await delivery(i.session)
      const ref=await journal.selected(i.session.id,client.projectId)
      return reply("OK",{pageJSON:ref&&!await journal.shown(i.session.id,ref)?JSON.stringify(ref):""})
    }),
    shown:(i,c)=>safe(async()=>{
      Schema.decodeUnknownSync(Inputs.open)(i);await verify(i.session,c.window)
      requireThat(automatic,"UNCONFIGURED")
      const client=await automatic.get(i.session,c.window),journal=await delivery(i.session)
      await journal.acknowledge(i.session.id,{project:client.projectId,requestId:i.requestId,requestDigest:i.requestDigest,packetDigest:i.packetDigest})
      return reply()
    }),
  })
  ctx.provide(Review, {
    status: (i, c) => dispatch("status",i,c),
    connect: (i, c) => safe(async () => {
      Schema.decodeUnknownSync(Inputs.connect)(i); await verify(i.session, c.window);requireThat(!automatic,"FORBIDDEN")
      const win = ctx.windows.get(c.window)!
      const picked = await dialog.showOpenDialog(win, { properties: ["openFile"], title: "Select protected Writhelm profile.json (never paste a key)", filters: [{ name: "Protected profile", extensions: ["json"] }] })
      if (picked.canceled) return reply("UNCONFIGURED")
      requireThat(path.basename(picked.filePaths[0]) === "profile.json", "PROTECTION_FAILED")
      const root = path.dirname(picked.filePaths[0]); await controller.attach(await openProtected(root, await projects(i.session)))
      saved.set({ root }); connected = true; return controller.dispatch("status", i, c)
    }),
    setup: (i, c) => safe(async () => {
      Schema.decodeUnknownSync(Inputs.setup)(i); await verify(i.session, c.window);requireThat(!automatic,"FORBIDDEN")
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
    pending: (i, c) => dispatch("pending", i, c), open: (i, c) => dispatch("open", i, c),
    answer: (i, c) => dispatch("answer", i, c),
    reconcile: (i, c) => automatic?dispatch("reconcile",i,c):controller.retry(i,c,runner),
    rotate: (i, c) => automatic?Promise.resolve(reply("FORBIDDEN")):dispatch("rotate", i, c),
    dismiss: (i, c) => dispatch("dismiss", i, c),
    workList: (i,c) => dispatch("workList",i,c),
    workOpen: (i,c) => dispatch("workOpen",i,c),
    workBody: (i,c) => dispatch("workBody",i,c),
  })
  ctx.windows.on("close", window => {controller.closeWindow(window.id);automatic?.closeWindow(window.id)})
  return async() => {await automatic?.close();await controller.close()}
}
