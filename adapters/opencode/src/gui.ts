// SPDX-License-Identifier: AGPL-3.0-only
import { Command, LinkHandler, Panel, Plugin, type PanelTab } from "@opencode/plugin/gui"
import { createSignal } from "solid-js"
import { Review, type Session } from "./contracts.js"
import { nativePanel } from "./panel.js"
import { exactLink, ownsReviewReference, type ExactLink } from "./link.js"
import { main } from "./main.js"

export default Plugin.define({ main, setup(ctx) {
  if (ctx.app.platform !== "desktop" || ctx.app.version !== "2.2.5") return
  const [links, setLinks] = createSignal<Record<string, ExactLink>>({})
  const tab: PanelTab = { id: "review", title: "Writhelm exact review", closable: "compact", fallback: true }
  ctx.add(Panel, { id: "review", region: "side", wide: true, list: input => input.open.includes("review") ? [tab] : [], render: props => nativePanel((): Session => ({ key: props.session.key, id: props.session.id, server: props.session.server.id, directory: props.session.directory, projectRoot: props.session.project?.worktree ?? props.session.directory }), ctx.main(Review), () => links()[props.session.key]) })
  ctx.add(Command, () => ({ id: "review", title: "Writhelm pending decisions", enabled: !!ctx.sessions.current(), run: () => { const s = ctx.sessions.current(); if (s) ctx.layout.open(`${ctx.id}:review`, s) } }))
  ctx.add(LinkHandler, { priority: 50, match: link => ownsReviewReference(link.href), exists: link => !!exactLink(link.href) && !!link.session && link.session.key === ctx.sessions.current()?.key, open: link => {
    const target = exactLink(link.href), session = link.session
    if (!target || !session || session.key !== ctx.sessions.current()?.key) {
      ctx.toast.show({ variant: "error", title: "Invalid or stale Writhelm request reference", description: "No request was opened. Use an exact reference in its originating session." })
      return
    }
    setLinks(before => ({ ...before, [session.key]: target })); ctx.layout.open(`${ctx.id}:review`, session)
  } })
} })
