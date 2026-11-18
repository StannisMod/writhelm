// SPDX-License-Identifier: AGPL-3.0-only
// Component/style contracts only. linkedom has no layout/default scrolling implementation.
import test from "node:test"
import assert from "node:assert/strict"
import { promises as fs } from "node:fs"
import { randomUUID } from "node:crypto"
import { createRoot, createSignal } from "solid-js"
import { parseHTML } from "linkedom"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review, ReviewDrafts, reply, type Session } from "../src/contracts.js"
import { ReviewDraftStore } from "../src/review-drafts.js"
import { createHash } from "node:crypto"
import { nativePanel, type ExactLink } from "../src/panel.js"
import { completeBundle } from "../src/wire.js"

const raw = await fs.readFile(process.env.OC003_FAILED_BUNDLE ?? new URL("../../tests/fixtures/bundle.json", import.meta.url), "utf8")
const original = JSON.parse(raw).data, request = original.request
const reference = (value = original): ExactLink => ({ project: value.request.payload.projectId, requestId: value.request.key, requestDigest: value.request.payload.requestDigest, packetDigest: value.request.payload.packetDigest })
const session: Session = { key: "sidecar\nlayout-A", id: "layout-A", server: "sidecar", directory: "/synthetic/project-A", projectRoot: "/synthetic/project-A" }
const drain = async () => { for (let n = 0; n < 6; n++) await new Promise(r => setImmediate(r)) }

function mount(bundleRaw = raw, width = 800, height = 699, drafts?:IpcClient<typeof ReviewDrafts.spec>) {
  const { document, window } = parseHTML("<html><body><div id='host'></div></body></html>")
  Object.assign(globalThis, { document, window })
  const host = document.getElementById("host")! as HTMLElement
  // Reproduce the documented INPUT constraint, NOT fabricated computed geometry.
  Object.assign(host.style, { width: width + "px", height: height + "px", overflow: "hidden" })
  const calls: { method: string; input: any }[] = [], bundles = new Map<string, string>(),handles:string[]=[]
  const value = JSON.parse(bundleRaw).data; bundles.set(value.request.key, bundleRaw)
  let setSession!: (s: Session) => void, setLink!: (link: ExactLink) => void, dispose!: () => void, root!: HTMLElement
  const bridge: IpcClient<typeof Review.spec> = {
    state: () => undefined, on: () => () => {},
    status: async input => { calls.push({ method: "status", input }); return reply("OK", { pageJSON: JSON.stringify({ formatVersion: 1, kind: "read", basis: { algorithm: "sha1", hex: "a".repeat(40) }, operation: "list_projects", data: { complete: true, nextCursor: null, snapshotId: randomUUID(), items: [{ projectId: value.request.payload.projectId }] } }) }) },
    pending: async () => reply("OK", { pageJSON: JSON.stringify({ formatVersion: 1, kind: "read", basis: { algorithm: "sha1", hex: "a".repeat(40) }, operation: "list_requests", data: { complete: true, nextCursor: null, snapshotId: randomUUID(), items: [] } }) }),
    open: async input => { calls.push({ method: "open", input });const handle=randomUUID();handles.push(handle);return reply("OK", { handle, bundleJSON: bundles.get(input.requestId)! }) },
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
    root = nativePanel(current, bridge, link,undefined,drafts); host.append(root)
  })
  const control = (label: string) => [...root.querySelectorAll("button")].find(b => b.textContent === label)!
  const pre = (label: string) => [...root.querySelectorAll("h3")].find(h => h.textContent === label)!.nextElementSibling as HTMLPreElement
  return { document, window, host, root, calls, bundles,handles, setSession, setLink, dispose, control, pre }
}

test("draft component: close flushes exact comments, reopening restores, another revision starts empty without assent",async()=>{
  const rows=new Map<string,string>(),key=(kind:string,k:string)=>kind+":"+k
  const store=new ReviewDraftStore({
    read:async(kind:string,k:string)=>rows.get(key(kind,k)),
    retain:async(kind:string,k:string,raw:string)=>{rows.set(key(kind,k),raw);return true},
    replace:async(kind:string,k:string,expected:string,raw:string)=>{
      assert.equal(createHash("sha256").update(rows.get(key(kind,k))!).digest("hex"),expected)
      rows.set(key(kind,k),raw);return true
    }})
  const scoped=(input:any)=>({storeId:"store",projectId:reference().project,controllerId:"controller",
    sessionID:input.session.id,requestId:input.requestId,requestDigest:input.requestDigest,packetDigest:input.packetDigest})
  const items=new Set<string>(request.payload.packet.items.map((i:any)=>i.id))
  const port={load:async(input:any)=>reply("OK",{pageJSON:JSON.stringify(await store.load(scoped(input),items))}),
    save:async(input:any)=>reply("OK",{pageJSON:JSON.stringify(await store.save(scoped(input),input.expectedDigest,
      {comments:input.comments,reason:input.reason},items))})} as unknown as IpcClient<typeof ReviewDrafts.spec>
  const f=mount(raw,800,699,port)
  await drain()
  const text="Точное замечание.\r\nСохранить привязку."
  const area=f.root.querySelector("[data-item-id] textarea") as HTMLTextAreaElement
  assert.equal(area.disabled,false);area.value=text;area.dispatchEvent(new f.window.Event("input"))
  f.dispose();await drain();assert.equal(f.calls.filter(c=>c.method==="answer").length,0)
  const g=mount(raw,800,699,port)
  try{
    await drain()
    assert.equal((g.root.querySelector("[data-item-id] textarea") as HTMLTextAreaElement).value,text)
    assert.equal(g.control("Утвердить").hidden,true)
    const next=JSON.parse(raw);next.data.request.key=randomUUID();next.data.request.payload.packetDigest="f".repeat(64)
    g.bundles.set(next.data.request.key,JSON.stringify(next));g.setLink(reference(next.data));await drain()
    assert.equal((g.root.querySelector("[data-item-id] textarea") as HTMLTextAreaElement).value,"")
    assert.equal(g.calls.filter(c=>c.method==="answer").length,0)
  }finally{g.dispose()}
})

test("point objection: only one context action, no permanent field, exact inline placeholder and keyboard access",async()=>{
  const f=mount()
  try{
    await drain()
    const point=f.root.querySelector("[data-item-id]") as HTMLElement
    const area=point.querySelector("textarea")!
    assert.equal(area.hidden,true)
    assert.equal(area.placeholder,"возражение")
    assert.ok(point.querySelector("h3")!.textContent!.includes(request.payload.packet.items[0].target.clauseId))
    const event=new f.window.Event("contextmenu",{bubbles:true,cancelable:true})
    point.dispatchEvent(event)
    assert.equal(event.defaultPrevented,true)
    const menu=f.root.querySelector('[role="menu"]') as HTMLElement
    assert.equal(menu.hidden,false)
    const actions=menu.querySelectorAll('[role="menuitem"]')
    assert.equal(actions.length,1);assert.equal(actions[0].textContent,"возразить")
    ;(actions[0] as HTMLButtonElement).click();await drain()
    assert.equal(menu.hidden,true);assert.equal(area.hidden,false)
    assert.equal(area.parentElement,point)
    area.value="Точное возражение";area.dispatchEvent(new f.window.Event("input"))
    assert.equal(f.control("Вернуть с замечаниями").hidden,false)
    area.value="";area.dispatchEvent(new f.window.Event("input"))
    assert.equal(area.placeholder,"возражение");assert.equal(area.hidden,false)
    assert.equal(f.control("Утвердить").hidden,false)
    assert.equal(f.calls.filter(c=>c.method==="answer").length,0)
    const keyboard=new f.window.Event("keydown",{bubbles:true,cancelable:true})
    Object.defineProperties(keyboard,{key:{value:"F10"},shiftKey:{value:true}})
    point.dispatchEvent(keyboard);assert.equal(keyboard.defaultPrevented,true);assert.equal(menu.hidden,false)
    const escape=new f.window.Event("keydown",{bubbles:true,cancelable:true})
    Object.defineProperty(escape,"key",{value:"Escape"})
    menu.dispatchEvent(escape);assert.equal(menu.hidden,true)
  }finally{f.dispose()}
})

test("one selectable canonical document plane: explanations collapsed, no point cards or translated duplicate",async()=>{
  const f=mount()
  try{
    await drain()
    assert.equal(f.root.style.userSelect,"text")
    const explanations=[...f.root.querySelectorAll("details")].find(d=>d.querySelector("summary")?.textContent==="Обоснование и рамки решения")!
    assert.equal(explanations.hasAttribute("open"),false)
    assert.ok(explanations.textContent!.includes(request.payload.packet.scope))
    for(const point of f.root.querySelectorAll<HTMLElement>("[data-item-id]")){
      assert.equal(point.style.userSelect,"text")
      assert.notEqual(point.style.border,"1px solid #7776")
      const text=point.querySelector<HTMLElement>("[data-canonical-text]")!
      assert.ok(text);assert.equal(text.style.userSelect,"text")
      assert.equal(text.closest("details"),null,"canonical wording is directly readable, not a second collapsed card plane")
      const menuEvent=new f.window.Event("contextmenu",{bubbles:true,cancelable:true})
      text.dispatchEvent(menuEvent)
      assert.equal((f.root.querySelector('[role="menu"]') as HTMLElement).hidden,false,"right-click exact wording, not only a separate card")
    }
    assert.equal(f.calls.filter(c=>c.method==="answer").length,0)
  }finally{f.dispose()}
})

test("review actions: collapsed full source, inline exact-item comments and mandatory rejection reason",async()=>{
  const f=mount()
  try{
    await drain()
    const full=[...f.root.querySelectorAll("details")].find(d=>d.querySelector("summary")?.textContent==="Открыть полный документ")!
    assert.ok(full);assert.equal(full.hasAttribute("open"),false)
    assert.equal(f.root.querySelectorAll('input[type="radio"]').length,0)
    f.control("Отклонить").click();await drain()
    assert.equal(f.calls.filter(c=>c.method==="answer").length,0)
    const reason=f.root.querySelector('[aria-label="Причина отклонения"]') as HTMLTextAreaElement
    assert.equal(reason.hidden,false);reason.value="Нужно пересмотреть границы."
    f.control("Отклонить").click();await drain()
    assert.equal(f.calls.find(c=>c.method==="answer")!.input.reason,reason.value)
  }finally{f.dispose()}
  const g=mount()
  try{
    await drain()
    const area=g.root.querySelector("[data-item-id] textarea") as HTMLTextAreaElement
    area.value="Исправить этот пункт.";area.dispatchEvent(new g.window.Event("input"))
    assert.equal(g.control("Утвердить").hidden,true);assert.equal(g.control("Отклонить").hidden,true)
    assert.equal(g.control("Вернуть с замечаниями").hidden,false)
    g.control("Вернуть с замечаниями").click();await drain()
    const answer=g.calls.find(c=>c.method==="answer")!.input
    assert.equal(answer.choice,"changes-required");assert.equal(answer.comments[0].itemId,request.payload.packet.items[0].id)
  }finally{g.dispose()}
})

test("linked review: unchanged unapproved points stay scoped, comments stay at exact points, projection tampering refuses",async()=>{
  // Schema-shaped component fixture only. Real service correspondence/admission is tested by JVM/HTTP.
  const fixture=JSON.parse(raw),old=JSON.parse(raw).data.request
  fixture.data.request.key=randomUUID()
  const p=fixture.data.request.payload.packet
  const changes=p.items.map((item:any)=>{
    const previousItemId=item.id;item.id=randomUUID()
    return {previousItemId,itemId:item.id,disposition:"unchanged",explanation:"Пункт ещё требует решения."}
  })
  p.followup={requestId:old.key,requestDigest:old.payload.requestDigest,packetDigest:old.payload.packetDigest,items:changes}
  const comment="Замечание к конкретному прежнему пункту."
  const previous={link:p.followup,request:old,materials:JSON.parse(raw).data.materials,
    resolution:{formatVersion:1,family:"resolutions",key:old.key,payload:{actorId:randomUUID(),
      answer:{kind:"changes-required",comments:[{itemId:changes[0].previousItemId,text:comment}]},approved:[]}}}
  fixture.data.followupJSON=JSON.stringify(previous)
  const linked=JSON.stringify(fixture),f=mount(linked)
  try{
    await drain()
    const group=[...f.root.querySelectorAll("details")].find(d=>d.querySelector("summary")?.textContent==="Неизменённые пункты, входящие в решение")!
    assert.equal(group.hasAttribute("open"),false)
    assert.equal(group.querySelectorAll("[data-item-id]").length,p.items.length)
    assert.ok(group.querySelector(`[data-item-id="${changes[0].itemId}"]`)!.textContent!.includes(comment))
    assert.ok(f.root.textContent!.includes("входят в это согласование"))
    assert.equal(f.calls.filter(c=>c.method==="answer").length,0)
    for(const mutate of [
      (v:any)=>{delete v.data.followupJSON},
      (v:any)=>{const previous=JSON.parse(v.data.followupJSON);previous.request.payload.requestDigest="f".repeat(64);v.data.followupJSON=JSON.stringify(previous)},
      (v:any)=>{const previous=JSON.parse(v.data.followupJSON);previous.materials=[];v.data.followupJSON=JSON.stringify(previous)},
      (v:any)=>{v.data.request.payload.packet.followup.items[0].previousItemId=randomUUID()}
    ]){
      const invalid=JSON.parse(linked);mutate(invalid);const r=invalid.data.request
      assert.throws(()=>completeBundle(JSON.stringify(invalid),r.key,r.payload.requestDigest,r.payload.packetDigest,r.payload.projectId))
    }
  }finally{f.dispose()}
})

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
    for (const [label, value] of [["Что предлагается утвердить", packet.question], ["Требования и границы", packet.scope], ["Зачем нужно решение", packet.rationale], ["Предлагаемые правила и последствия", packet.consequences.join("\n")], ["Другие варианты", packet.alternatives.join("\n")], ["Что ещё не определено", packet.uncertainties.join("\n")]]) if(value) assert.equal(f.pre(label).textContent, value)
    const pres = [...f.root.querySelectorAll("pre")]
    for (const material of original.materials) {
      assert.ok(pres.some(p => p.textContent === JSON.stringify(material.reference) + "\n" + material.observation))
      for (const fragment of material.fragments) assert.ok([...f.root.querySelectorAll("div")].some(p => p.textContent === fragment.text))
    }
    assert.equal(f.pre("Complete canonical packet, material and original attribution").textContent, raw)
    assert.ok(f.root.style.fontFamily.includes("system-ui"))
    assert.equal(f.pre("Требования и границы").tagName,"DIV")
    assert.ok(f.pre("Требования и границы").querySelector("span"))
    assert.equal(f.pre("Идентификаторы и статус").parentElement?.tagName,"DETAILS")
    assert.equal(f.pre("Идентификаторы и статус").parentElement?.hasAttribute("open"),false)
    for (const p of pres) {
      assert.equal(p.style.whiteSpace, "pre-wrap"); assert.equal(p.style.overflowWrap, "anywhere")
      assert.equal(p.style.wordBreak, "normal"); assert.equal(p.style.maxWidth, "100%")
      assert.ok(!p.style.maxHeight && !p.style.textOverflow && !p.style.overflow, "Do not crop/ellipsis individual material blocks")
    }
    const children = [...f.root.children]
    assert.ok(children.indexOf(f.root.querySelector("fieldset")!) > children.indexOf(f.pre("Предлагаемые правила и последствия").parentElement!))
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
    await drain(); const pres = [...f.root.querySelectorAll("pre,div")]
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
    await drain()
    assert.equal(f.root.querySelectorAll('input[type="radio"]').length,0)
    assert.equal(f.calls.filter(c=>c.method==="answer").length,0)
    // Synthetic component event test only, not a claim of visible host click/keyboard navigation.
    const area = f.root.querySelector("[data-item-id] textarea")! as HTMLTextAreaElement; area.value = "Retain this exact anchored correction\r\n";area.dispatchEvent(new f.window.Event("input"))
    assert.equal(area.getAttribute("aria-label"), "Возражение к " + request.payload.packet.items[0].target.clauseId)
    const handle = f.handles[0]
    f.control("Вернуть с замечаниями").click(); await drain()
    assert.deepEqual(f.calls.filter(c => c.method === "answer").map(c => c.input), [{ session, handle, choice: "changes-required", comments: [{ itemId: request.payload.packet.items[0].id, text: area.value }] }])
    assert.equal(f.calls.filter(c => c.method === "answer").length, 1)
  } finally { f.dispose() }
  await drain();assert.equal(f.calls.filter(c=>c.method==="dismiss").length,1)
})

test("binding: reactive session and changed-packet link discard selections rather than remap old answers", async () => {
  const f = mount()
  try {
    await drain(); const oldArea = f.root.querySelector('[data-item-id] textarea')! as HTMLTextAreaElement
    oldArea.value="old request draft";oldArea.dispatchEvent(new f.window.Event("input"))
    const oldHandle = f.handles[0]
    const nextSession = { ...session, key: "sidecar\nlayout-B", id: "layout-B", directory: "/synthetic/project-B", projectRoot: "/synthetic/project-B" }
    f.setSession(nextSession); await drain()
    assert.equal(f.root.querySelectorAll('input[type="radio"]:checked').length, 0)
    assert.equal((f.root.querySelector('[data-item-id] textarea') as HTMLTextAreaElement).value,"")
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
