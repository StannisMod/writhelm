// SPDX-License-Identifier: AGPL-3.0-only
// Component/style contracts only. linkedom has no layout/default scrolling implementation.
import test from "node:test"
import assert from "node:assert/strict"
import { promises as fs } from "node:fs"
import { randomUUID } from "node:crypto"
import { createRoot, createSignal } from "solid-js"
import { parseHTML } from "linkedom"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review, reply, type Session } from "../src/contracts.js"
import { nativePanel, type ExactLink } from "../src/panel.js"

const raw = await fs.readFile(process.env.OC003_FAILED_BUNDLE ?? new URL("../../tests/fixtures/bundle.json", import.meta.url), "utf8")
const original = JSON.parse(raw).data, request = original.request
const reference = (value = original): ExactLink => ({ project: value.request.payload.projectId, requestId: value.request.key, requestDigest: value.request.payload.requestDigest, packetDigest: value.request.payload.packetDigest })
const session: Session = { key: "sidecar\nlayout-A", id: "layout-A", server: "sidecar", directory: "/synthetic/project-A", projectRoot: "/synthetic/project-A" }
const drain = async () => { for (let n = 0; n < 6; n++) await new Promise(r => setImmediate(r)) }

function mount(bundleRaw = raw, width = 800, height = 699) {
  const { document, window } = parseHTML("<html><body><div id='host'></div></body></html>")
  Object.assign(globalThis, { document, window })
  const host = document.getElementById("host")! as HTMLElement
  // Reproduce the documented INPUT constraint, NOT fabricated computed geometry.
  Object.assign(host.style, { width: width + "px", height: height + "px", overflow: "hidden" })
  const calls: { method: string; input: any }[] = [], bundles = new Map<string, string>()
  const value = JSON.parse(bundleRaw).data; bundles.set(value.request.key, bundleRaw)
  let setSession!: (s: Session) => void, setLink!: (link: ExactLink) => void, dispose!: () => void, root!: HTMLElement
  const bridge: IpcClient<typeof Review.spec> = {
    state: () => undefined, on: () => () => {},
    status: async input => { calls.push({ method: "status", input }); return reply("OK", { pageJSON: JSON.stringify({ formatVersion: 1, kind: "read", basis: { algorithm: "sha1", hex: "a".repeat(40) }, operation: "list_projects", data: { complete: true, nextCursor: null, snapshotId: randomUUID(), items: [{ projectId: value.request.payload.projectId }] } }) }) },
    pending: async () => reply("OK", { pageJSON: JSON.stringify({ formatVersion: 1, kind: "read", basis: { algorithm: "sha1", hex: "a".repeat(40) }, operation: "list_requests", data: { complete: true, nextCursor: null, snapshotId: randomUUID(), items: [] } }) }),
    open: async input => { calls.push({ method: "open", input }); return reply("OK", { handle: randomUUID(), bundleJSON: bundles.get(input.requestId)! }) },
    answer: async input => { calls.push({ method: "answer", input }); return reply() },
    dismiss: async input => { calls.push({ method: "dismiss", input }); return reply() },
    connect: async () => reply(), setup: async () => reply(), reconcile: async () => reply(), rotate: async () => reply(),
    workList: async () => reply("UNCONFIGURED"),workOpen: async () => reply("UNCONFIGURED"),
    workBody: async () => reply("UNCONFIGURED"),
  }
  createRoot(d => {
    dispose = d
    const [current, changeSession] = createSignal(session), [link, changeLink] = createSignal(reference(value))
    setSession = changeSession; setLink = changeLink
    root = nativePanel(current, bridge, link); host.append(root)
  })
  const control = (label: string) => [...root.querySelectorAll("button")].find(b => b.textContent === label)!
  const pre = (label: string) => [...root.querySelectorAll("h3")].find(h => h.textContent === label)!.nextElementSibling as HTMLPreElement
  return { document, window, host, root, calls, bundles, setSession, setLink, dispose, control, pre }
}

test("layout: own border-box viewport remains constrained under normal, small and narrow hidden hosts", async () => {
  for (const [width, height] of [[800, 699], [320, 240], [160, 120]]) {
    const f = mount(raw, width, height)
    try {
      await drain()
      assert.equal(f.root.parentElement, f.host)
      assert.equal(f.root.style.height, "100%", "A hidden host requires plugin-owned constrained height")
      assert.equal(f.root.style.width, "100%")
      assert.equal(f.root.style.boxSizing, "border-box", "Padding cannot grow beyond the supplied viewport")
      assert.equal(f.root.style.minHeight, "0"); assert.equal(f.root.style.minWidth, "0")
      assert.equal(f.root.style.maxWidth, "100%")
      assert.equal(f.root.style.overflowY, "auto"); assert.equal(f.root.style.overflowX, "auto")
      assert.equal(f.host.style.overflow, "hidden", "The plugin must not change its host wrapper")
      assert.ok(f.root.contains(f.root.querySelector("fieldset")))
      assert.equal(f.pre("Complete canonical packet, material and original attribution").textContent, raw)
    } finally { f.dispose() }
  }
})

test("navigation: labelled root is a native tab stop; wheel and navigation keys are not intercepted or assent", async () => {
  const f = mount()
  try {
    await drain(); assert.equal(f.root.getAttribute("aria-label"), "Writhelm exact native review")
    assert.equal(f.root.getAttribute("tabindex"), "0", "Native keyboard scrolling needs a focusable scroll owner")
    assert.notEqual(typeof f.root.onwheel, "function"); assert.notEqual(typeof f.root.onkeydown, "function")
    const events = [new f.window.Event("wheel", { bubbles: true, cancelable: true }), ...["PageDown", "PageUp", "Home", "End", "ArrowDown", "ArrowUp"].map(key => Object.assign(new f.window.Event("keydown", { bubbles: true, cancelable: true }), { key }))]
    for (const event of events) { f.root.dispatchEvent(event); assert.equal(event.defaultPrevented, false) }
    await drain(); assert.equal(f.calls.filter(c => c.method === "answer" || c.method === "dismiss").length, 0)
    assert.equal(f.root.querySelectorAll('input[type="radio"]:checked').length, 0)
    // No scrollTop/visibility assertion: linkedom cannot execute native wheel/keyboard defaults.
  } finally { f.dispose() }
})

test("preservation: actual failed-view raw bundle, all located fragments and primary qualifications survive wrapping", async () => {
  const f = mount()
  try {
    await drain(); const packet = request.payload.packet
    for (const [label, value] of [["Decision", packet.question], ["Scope and exclusions", packet.scope], ["Rationale", packet.rationale], ["Consequences / risks", packet.consequences.join("\n")], ["Alternatives", packet.alternatives.join("\n")], ["Uncertainties", packet.uncertainties.join("\n")]]) assert.equal(f.pre(label).textContent, value)
    const pres = [...f.root.querySelectorAll("pre")]
    for (const material of original.materials) {
      assert.ok(pres.some(p => p.textContent === JSON.stringify(material.reference) + "\n" + material.observation))
      for (const fragment of material.fragments) assert.ok(pres.some(p => p.textContent === fragment.text))
    }
    assert.equal(f.pre("Complete canonical packet, material and original attribution").textContent, raw)
    for (const p of pres) {
      assert.equal(p.style.whiteSpace, "pre-wrap"); assert.equal(p.style.overflowWrap, "anywhere")
      assert.equal(p.style.wordBreak, "normal"); assert.equal(p.style.maxWidth, "100%")
      assert.ok(!p.style.maxHeight && !p.style.textOverflow && !p.style.overflow, "Do not crop/ellipsis individual material blocks")
    }
    const children = [...f.root.children]
    assert.ok(children.indexOf(f.root.querySelector("fieldset")!) > children.indexOf(f.pre("Consequences / risks").parentElement!))
  } finally { f.dispose() }
})

test("preservation: long unbroken Unicode/source and large integer JSON spellings are never rewritten to fit", async () => {
  // Synthetic schema-shaped component input, NOT a newly canonically verified request/digest.
  const fixture = JSON.parse(raw), body = "\ufeffe\u0301\r\n" + "unbroken-".repeat(2048) + "\t終わり\r\n"
  fixture.data.materials[0].fragments[0].text = body
  fixture.data.request.payload.generations[0].generation = 1234567
  const largeRaw = JSON.stringify(fixture).replace('"generation":1234567', '"generation":9007199254740993')
  const f = mount(largeRaw, 160, 120)
  try {
    await drain(); const pres = [...f.root.querySelectorAll("pre")]
    assert.ok(pres.some(p => p.textContent === body))
    const canonical = f.pre("Complete canonical packet, material and original attribution")
    assert.equal(canonical.textContent, largeRaw); assert.ok(canonical.textContent!.includes('"generation":9007199254740993'))
    assert.ok(!canonical.textContent!.includes('"generation":9007199254740992'))
    assert.equal(canonical.style.overflowWrap, "anywhere")
  } finally { f.dispose() }
})

test("layout: fieldset, exact-anchor labels, buttons and input widths cannot force narrow min-content expansion", async () => {
  const f = mount(raw, 160, 120)
  try {
    await drain(); const fieldset = f.root.querySelector("fieldset")!
    assert.equal(fieldset.style.minInlineSize, "0"); assert.equal(fieldset.style.minWidth, "0")
    assert.equal(fieldset.style.width, "100%"); assert.equal(fieldset.style.boxSizing, "border-box")
    for (const e of f.root.querySelectorAll<HTMLElement>("button,input[aria-label],textarea,label,fieldset")) {
      assert.equal(e.style.maxWidth, "100%")
      assert.equal(e.style.boxSizing, "border-box")
    }
    for (const b of f.root.querySelectorAll("button")) { assert.equal(b.style.whiteSpace, "normal"); assert.equal(b.style.overflowWrap, "anywhere") }
    for (const area of f.root.querySelectorAll("textarea")) { assert.equal(area.style.width, "100%"); assert.equal(area.style.resize, "vertical") }
    assert.equal(f.root.style.overflowWrap, "anywhere")
  } finally { f.dispose() }
})

test("binding: layout retains closed/no-default choices and exact comment/handle identity; disposal is not an answer", async () => {
  const f = mount()
  try {
    await drain(); const inputs = [...f.root.querySelectorAll<HTMLInputElement>('input[type="radio"]')]
    assert.deepEqual(inputs.map(x => x.value), ["approve", "reject", "changes-required"])
    assert.ok(inputs.every(x => !x.checked))
    // Synthetic component event test only, not a claim of visible host click/keyboard navigation.
    inputs[2].checked = true; inputs[2].dispatchEvent(new f.window.Event("change"))
    const area = f.root.querySelector("textarea")! as HTMLTextAreaElement; area.value = "Retain this exact anchored correction\r\n"
    assert.equal(area.parentElement!.firstChild!.textContent, "Changes for exact item " + request.payload.packet.items[0].id)
    assert.equal(area.parentElement!.parentElement!.hidden, false)
    const handle = inputs[2].name.slice("writhelm-answer-".length)
    f.control("Submit exact retained answer").click(); await drain()
    assert.deepEqual(f.calls.filter(c => c.method === "answer").map(c => c.input), [{ session, handle, choice: "changes-required", comments: [{ itemId: request.payload.packet.items[0].id, text: area.value }] }])
    f.control("Close view — submitted command may already be published").click(); await drain()
    assert.equal(f.calls.filter(c => c.method === "answer").length, 1)
    assert.equal(f.calls.filter(c => c.method === "dismiss").length, 1)
  } finally { f.dispose() }
})

test("binding: reactive session and changed-packet link discard selections rather than remap old answers", async () => {
  const f = mount()
  try {
    await drain(); const oldRadio = f.root.querySelector('input[value="approve"]')! as HTMLInputElement
    oldRadio.checked = true; oldRadio.dispatchEvent(new f.window.Event("change"))
    const oldHandle = oldRadio.name.slice("writhelm-answer-".length)
    const nextSession = { ...session, key: "sidecar\nlayout-B", id: "layout-B", directory: "/synthetic/project-B", projectRoot: "/synthetic/project-B" }
    f.setSession(nextSession); await drain()
    assert.equal(f.root.querySelectorAll('input[type="radio"]:checked').length, 0)
    assert.ok(f.calls.some(c => c.method === "dismiss" && c.input.handle === oldHandle && c.input.session.key === session.key))
    // Same component, newly provided packet identity; no canonical publication claimed by this port.
    const next = JSON.parse(raw).data; next.request.key = randomUUID(); next.request.payload.requestDigest = "d".repeat(64); next.request.payload.packetDigest = "e".repeat(64)
    const envelope = JSON.parse(raw); envelope.data = next; f.bundles.set(next.request.key, JSON.stringify(envelope))
    f.setLink(reference(next)); await drain()
    assert.equal(f.root.querySelectorAll('input[type="radio"]:checked').length, 0)
    assert.equal(f.calls.filter(c => c.method === "answer").length, 0)
    assert.equal(f.calls.filter(c => c.method === "open").at(-1)!.input.session.key, nextSession.key)
    assert.equal(f.calls.filter(c => c.method === "open").at(-1)!.input.requestDigest, next.request.payload.requestDigest)
    assert.equal(f.pre("Complete canonical packet, material and original attribution").textContent, JSON.stringify(envelope))
  } finally { f.dispose() }
  assert.equal(f.calls.filter(c => c.method === "answer").length, 0)
})

test("qualification copy: closed F001 is not a current hold; native/platform/operational gates and historical L001 remain explicit", async () => {
  const f = mount()
  try {
    await drain(); const text = f.pre("Limits / known gate").textContent!
    assert.ok(!text.includes("F-001 integration hold remains"))
    assert.ok(text.includes("Native host qualification") && text.includes("Windows/macOS") && text.includes("K-RUN") && text.includes("M1"))
    assert.ok(text.includes("L001 historical startup timeout remains unexplained"))
    assert.ok(text.includes("not proof of semantic sufficiency, truth or comprehension"))
    assert.ok(text.includes("cooperative, not physical-human proof"))
  } finally { f.dispose() }
})
