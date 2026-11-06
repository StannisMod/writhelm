// SPDX-License-Identifier: AGPL-3.0-only
import { createEffect, onCleanup } from "solid-js"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review, type Session, type Reply } from "./contracts.js"
import { serviceResult, completeBundle, type Json } from "./wire.js"

import type { ExactLink } from "./link.js"
export { exactLink, type ExactLink } from "./link.js"
export function nativePanel(session: () => Session, bridge: IpcClient<typeof Review.spec>, linked: () => ExactLink | undefined): HTMLElement {
  const root = document.createElement("section"); root.setAttribute("aria-label", "Writhelm exact native review")
  const fit = (element: HTMLElement) => Object.assign(element.style, { minWidth: "0", maxWidth: "100%", boxSizing: "border-box", overflowWrap: "anywhere" })
  // The host clips its definite panel viewport. Own the scroll surface, not the host's CSS.
  fit(root); Object.assign(root.style, { display: "block", width: "100%", height: "100%", minHeight: "0", padding: "0.75rem", overflowY: "auto", overflowX: "auto", whiteSpace: "normal" })
  root.tabIndex = 0 // Native wheel/keyboard scrolling; never intercept text/radio navigation keys.
  let epoch = 0, current = session(), handle = "", bundle: Json, project = "", cursor = ""
  const status = document.createElement("p"); status.setAttribute("role", "status")
  const pending = document.createElement("section"), content = document.createElement("section"), choices = document.createElement("fieldset")
  fit(choices); Object.assign(choices.style, { minInlineSize: "0", width: "100%" })
  const comments = new Map<string, HTMLTextAreaElement>(); let selected = ""
  const button = (label: string, action: () => Promise<void> | void) => {
    const b = document.createElement("button"); b.type = "button"; b.textContent = label
    fit(b); Object.assign(b.style, { display: "block", whiteSpace: "normal", marginBlock: "0.25rem" })
    b.onclick = () => { Promise.resolve().then(action).catch(() => { choices.disabled = true; status.textContent = "UNAVAILABLE — no rollback inferred; retain the original command." }) }
    return b
  }
  function paragraph(label: string, value: string, target = content) {
    const h = document.createElement("h3"); h.textContent = label; const p = document.createElement("pre")
    fit(p); Object.assign(p.style, { whiteSpace: "pre-wrap", wordBreak: "normal" })
    p.textContent = value; target.append(h, p) // Wrapping is visual only; retain every original codepoint.
  }
  function reset() { ++epoch; handle = ""; bundle = undefined; selected = ""; comments.clear(); content.replaceChildren(); choices.replaceChildren(); choices.disabled = true }
  function outcome(r: Reply) {
    status.textContent = r.code + (r.pending ? " — original command retained; use Reconcile, never a new key." : "")
    if (r.resultJSON) paragraph("Original canonical outcome (not a rollback claim)", r.resultJSON)
    if (!r.ok) choices.disabled = true
  }
  async function refresh() {
    reset(); const mine = epoch, bound = current
    const r = await bridge.status({ session: bound }); if (mine !== epoch) return; outcome(r)
    if (!r.ok || !r.pageJSON) return
    const projects = serviceResult(r.pageJSON).data
    if (projects.complete !== true || projects.items.length !== 1) { status.textContent = "INCOMPLETE"; return }
    project = projects.items[0].projectId
    paragraph("Pinned authorized project", project)
    const link = linked()
    if (link) { if (link.project !== project) { status.textContent = "FORBIDDEN — link/profile project mismatch"; return }; await open(link) }
    else { cursor = ""; pending.replaceChildren(); await inbox() }
  }
  async function inbox() {
    const mine = epoch, bound = current
    const r = await bridge.pending({ session: bound, cursor }); if (mine !== epoch) return; outcome(r)
    if (!r.ok) return
    const page = serviceResult(r.pageJSON).data
    if (page.complete !== (page.nextCursor === null)) { status.textContent = "INCOMPLETE"; return }
    for (const item of page.items) pending.append(button("Review exact request " + item.id, () => open({ project, requestId: item.id, requestDigest: item.requestDigest, packetDigest: item.packetDigest })))
    cursor = page.nextCursor ?? ""; more.hidden = !cursor
  }
  async function open(ref: ExactLink) {
    reset() // Controller.open invalidates the old handle atomically; no delayed dismiss race.
    const mine = epoch, bound = current
    const r = await bridge.open({ session: bound, requestId: ref.requestId, requestDigest: ref.requestDigest, packetDigest: ref.packetDigest })
    if (mine !== epoch) return; outcome(r); if (!r.ok) return
    const b = completeBundle(r.bundleJSON, ref.requestId, ref.requestDigest, ref.packetDigest, ref.project)
    bundle = b; handle = r.handle
    dismiss.textContent = "Dismiss — leave pending"
    const p = b.request.payload.packet
    for (const [title, value] of [["Decision", p.question], ["Scope and exclusions", p.scope], ["Rationale", p.rationale], ["Consequences / risks", p.consequences.join("\n")], ["Alternatives", p.alternatives.join("\n")], ["Uncertainties", p.uncertainties.join("\n")]]) paragraph(title, value)
    for (const material of b.materials) {
      paragraph("Exact material / status / provenance", JSON.stringify(material.reference) + "\n" + material.observation)
      for (const f of material.fragments) paragraph("Located fragment " + f.span.start + "–" + f.span.endExclusive, f.text)
    }
    // The exact original JSON retains every metadata field, large integer, packet item and trace.
    paragraph("Complete canonical packet, material and original attribution", r.bundleJSON)
    paragraph("Limits / known gate", "Source coverage is not proof of semantic sufficiency, truth or comprehension. Native host qualification, Windows/macOS, K-RUN abnormal recovery, resource maxima, release and full M1 remain held. L001 historical startup timeout remains unexplained. Native T1 is cooperative, not physical-human proof.")
    choices.replaceChildren(); const legend = document.createElement("legend"); legend.textContent = "One coherent decision; choose explicitly"; choices.append(legend)
    fit(legend)
    const commentBox = document.createElement("section"); commentBox.hidden = true
    for (const kind of ["approve", "reject", "changes-required"]) {
      const label = document.createElement("label"), radio = document.createElement("input"); radio.type = "radio"; radio.name = "writhelm-answer-" + r.handle; radio.value = kind
      fit(label); label.style.display = "block"
      radio.onchange = () => { selected = kind; commentBox.hidden = kind !== "changes-required" }; label.append(radio, document.createTextNode(kind)); choices.append(label)
    }
    for (const item of p.items) {
      const label = document.createElement("label"), area = document.createElement("textarea"); label.textContent = "Changes for exact item " + item.id; area.maxLength = 4096; comments.set(item.id, area); label.append(area); commentBox.append(label)
      fit(label); label.style.display = "block"
      fit(area); Object.assign(area.style, { display: "block", width: "100%", resize: "vertical" })
    }
    choices.append(commentBox, button("Submit exact retained answer", async () => {
      if (!handle || !selected || !bundle?.actionable || r.pending) { status.textContent = "Choose explicitly from a complete actionable view, or reconcile the retained original command."; return }
      const choice = selected as "approve" | "reject" | "changes-required"
      const anchored = choice === "changes-required" ? [...comments].filter(([, a]) => a.value.trim()).map(([itemId, a]) => ({ itemId, text: a.value })) : []
      if (choice === "changes-required" && !anchored.length) { status.textContent = "Add a nonblank item-anchored comment."; return }
      const preparedEpoch = epoch; choices.disabled = true
      dismiss.textContent = "Close view — submitted command may already be published"
      const result = await bridge.answer({ session: current, handle, choice, comments: anchored })
      if (preparedEpoch === epoch) outcome(result)
    }))
    choices.disabled = !b.actionable || r.pending
  }
  const connect = button("Connect protected client profile (native chooser)", async () => { outcome(await bridge.connect({ session: current })); await refresh() })
  const origin = document.createElement("input"); origin.placeholder = "Pinned loopback service origin (not a key)"; origin.setAttribute("aria-label", "Service origin")
  fit(origin); origin.style.width = "100%"
  const setup = button("Set up a NEW local store and protected client files", async () => { outcome(await bridge.setup({ session: current, origin: origin.value })) })
  const retry = button("Reconcile ORIGINAL retained command", async () => { outcome(await bridge.reconcile({ session: current })) })
  const rotate = button("Rotate owner credential (persist replacement first)", async () => { outcome(await bridge.rotate({ session: current })) })
  const dismiss = button("Dismiss — leave pending", async () => { const h = handle, s = current; reset(); if (h) outcome(await bridge.dismiss({ session: s, handle: h })) })
  dismiss.dataset.action = "dismiss"
  const more = button("Next inbox snapshot page (not a decision packet)", inbox); more.hidden = true
  root.append(connect, origin, setup, button("Pending inbox", refresh), retry, rotate, dismiss, status, pending, more, content, choices)
  let mounted = false, previousLink = ""
  createEffect(() => {
    const next = session(), link = JSON.stringify(linked())
    if (mounted && next.key === current.key && next.directory === current.directory && next.projectRoot === current.projectRoot && link === previousLink) return
    const previous = current, h = handle; reset(); current = next; mounted = true; previousLink = link
    if (h) void bridge.dismiss({ session: previous, handle: h })
    void refresh().catch(() => { choices.disabled = true; status.textContent = "UNAVAILABLE" })
  })
  onCleanup(() => { const h = handle, s = current; reset(); if (h) void bridge.dismiss({ session: s, handle: h }) })
  return root
}
