// SPDX-License-Identifier: AGPL-3.0-only
import { createEffect, onCleanup } from "solid-js"
import { isLosslessNumber, stringify } from "lossless-json"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review, WorkFamilies, WorkListFamilies, type WorkFamily, type Session } from "./contracts.js"
import { workResult, mutableWorkFamilies } from "./work-wire.js"

/** READ-only native workflow view. Exact selectors are never silently replaced with latest bodies;
 * page and record H are displayed separately. No autoaccept/integrate or raw HTML/credential IPC. */
export function workPanel(session: () => Session, bridge: IpcClient<typeof Review.spec>): HTMLElement {
  const root = document.createElement("section"); root.setAttribute("aria-label","Writhelm governed work")
  Object.assign(root.style,{ width: "100%",height: "100%",minWidth: "0",minHeight: "0",overflow: "auto",padding: "0.75rem",boxSizing: "border-box" })
  root.tabIndex = 0
  let current = session(), epoch = 0, cursor = "", family: typeof WorkListFamilies[number] = "assignments", count = 0
  let opening = 0, paging: symbol | undefined
  const status = document.createElement("p");status.setAttribute("role","status")
  const rows = document.createElement("section"), content = document.createElement("section")
  const heading = document.createElement("h2");heading.textContent = "Governed work — exact service records"
  const limits = document.createElement("p")
  limits.textContent = "Read-only. Proposed/accepted knowledge, produced results, verification, acceptance and integration are distinct. Native idle is not writer fencing; a pass field does not accept a result."
  const categories = document.createElement("select");categories.setAttribute("aria-label","Work record family")
  for (const name of WorkListFamilies) { const option = document.createElement("option");option.value=name;option.textContent=name;if(name===family)option.selected=true;categories.append(option) }
  const button = (label: string, action: () => Promise<void>) => {
    const b=document.createElement("button");b.type="button";b.textContent=label
    b.onclick=()=>{ const mine=epoch;void action().catch(()=>{ if(mine===epoch)status.textContent="UNAVAILABLE — retain the exact selector; refresh explicitly, never infer rollback or success." }) }
    return b
  }
  const text = (label: string, value: string, target = content) => {
    const h=document.createElement("h3"), p=document.createElement("pre");h.textContent=label;p.textContent=value
    Object.assign(p.style,{ whiteSpace:"pre-wrap",overflowWrap:"anywhere",maxWidth:"100%" });target.append(h,p)
  }
  const encode = (v: unknown) => { const raw=stringify(v);if(typeof raw!=="string")throw new Error("incomplete work selector");return raw }
  function reset() { epoch++;opening++;paging=undefined;cursor="";count=0;rows.replaceChildren();content.replaceChildren();more.hidden=true;more.disabled=false }
  async function open(selector: any, selectedFamily: WorkFamily, selectedEpoch: number, bound: Session) {
    if(selectedEpoch!==epoch)return
    const request=++opening
    content.replaceChildren()
    const mutable=mutableWorkFamilies.has(selectedFamily)
    const generation=mutable && isLosslessNumber(selector.generation) ? selector.generation.value : ""
    const digest=!mutable && typeof selector.digest==="string" ? selector.digest : ""
    if(mutable ? !generation : !digest)throw new Error("incomplete work selector")
    const reply=await bridge.workOpen({ session:bound,family:selectedFamily,keyJSON:encode(selector.key),generation,digest }).catch(error=>{
      if(selectedEpoch===epoch&&request===opening)throw error
      return undefined
    })
    if(selectedEpoch!==epoch||request!==opening||!reply)return
    if(!reply.ok){status.textContent=reply.code+" — exact selector unavailable; no latest-body fallback.";return}
    const result=workResult(reply.bundleJSON,"read_"+selectedFamily.replaceAll("-","_")), payload=result.data.payload
    content.replaceChildren()
    text("Record read at H",`${result.basis.algorithm}:${result.basis.hex}`)
    text("Exact family / key / generation or digest",`${selectedFamily}\n${encode(selector)}`)
    if(typeof payload?.objective==="string")text("Task objective",payload.objective)
    if(typeof payload?.title==="string")text("Task title",payload.title)
    if(typeof payload?.state==="string")text("Canonical state — not physical completion",payload.state)
    if(Array.isArray(payload?.steps)) {
      const steps=document.createElement("ol")
      for(const step of payload.steps){const item=document.createElement("li");item.textContent=`${step.state}: ${step.description}`;steps.append(item)}
      const h=document.createElement("h3");h.textContent="Canonical progress tracker";content.append(h,steps)
    }
    if(Array.isArray(payload?.pending))text("Pending operations / events / decisions",encode(payload.pending))
    if(Array.isArray(payload?.gaps))text("Known context gaps",encode(payload.gaps))
    if(typeof payload?.handoff==="string")text("Handoff — not result acceptance",payload.handoff)
    for(const field of ["reasons","uncertainty","frontier","blockers","dependencies","criteria","checks","verdict","decision","result","reports","target","code","artifacts"])
      if(payload?.[field]!==undefined)text(field+" — exact attributed value",encode(payload[field]))
    // Body candidates are only links, never proof of source applicability. The service checks the
    // exact parent/generation and direct-body authorization again on EVERY explicit body click.
    const bodies=new Map<string,any>()
    const references=new Map<string,any>()
    const collect=(value:any):void=>{
      if(!value||typeof value!=="object"||isLosslessNumber(value))return
      if(typeof value.digest==="string"&&/^[0-9a-f]{64}$/.test(value.digest)&&isLosslessNumber(value.byteLength))bodies.set(value.digest,value)
      if((WorkFamilies as readonly string[]).includes(value.family)&&value.key
        &&(mutableWorkFamilies.has(value.family)?isLosslessNumber(value.generation):typeof value.digest==="string"&&/^[0-9a-f]{64}$/.test(value.digest)))
        references.set(encode(value),value)
      for(const child of Object.values(value))collect(child)
    }
    collect(payload)
    if(references.size>64)text("PARTIAL exact-reference navigation","Only 64 reference links are shown; all original selectors remain in the record JSON.")
    for(const reference of [...references.values()].slice(0,64))content.append(button("Open referenced exact "+reference.family+" "+encode(reference.key),
      ()=>open(reference,reference.family,selectedEpoch,bound)))
    if(bodies.size>64)text("PARTIAL body navigation","Only 64 body links are shown; complete exact references remain in the original record JSON.")
    for(const body of [...bodies.values()].slice(0,64)){
      const output=document.createElement("section")
      const show=button("Read exact attributed body "+body.digest,async()=>{
        const mine=opening
        const result=await bridge.workBody({session:bound,family:selectedFamily,keyJSON:encode(selector.key),generation,digest,bodyDigest:body.digest})
        if(selectedEpoch!==epoch||request!==opening||mine!==opening)return
        output.replaceChildren()
        if(!result.ok){text("Body unavailable",result.code+" — no cached or latest-body fallback.",output);return}
        const frame=workResult(result.bundleJSON,"read_work_body")
        if(frame.data.digest!==body.digest||!isLosslessNumber(frame.data.byteLength)||frame.data.byteLength.value!==body.byteLength.value
          ||typeof frame.data.body!=="string")throw new Error("incomplete body")
        text("Attributed body — not a norm, verification or acceptance",frame.data.body,output)
        text("Body read at H",`${frame.basis.algorithm}:${frame.basis.hex}`,output)
      })
      content.append(show,output)
    }
    const details=document.createElement("details"), summary=document.createElement("summary");summary.textContent="Complete original service JSON / source and code identities"
    details.append(summary);text("Exact returned record",reply.bundleJSON,details);content.append(details)
    status.textContent="Current authorized exact record. Verification/acceptance/integration require their own attributed records."
  }
  async function page() {
    if(paging)return
    const request=Symbol();paging=request;more.disabled=true
    const mine=epoch,bound=current,selected=family
    try {
    status.textContent="Reading authorized selector snapshot…"
    const reply=await bridge.workList({ session:bound,family:selected,cursor })
    if(mine!==epoch)return
    if(!reply.ok){status.textContent=reply.code+" — no complete view inferred.";return}
    const result=workResult(reply.pageJSON,"list_work_records"), data=result.data
    if(!Array.isArray(data.items)||data.complete!==(data.nextCursor===null))throw new Error("incomplete work page")
    for(const selector of data.items)rows.append(button("Open exact "+selected+" "+encode(selector.key),()=>open(selector,selected,mine,bound)))
    count+=data.items.length;cursor=data.nextCursor??"";more.hidden=!cursor
    status.textContent=`${selected}: ${count} selectors at ${result.basis.algorithm}:${result.basis.hex}. ${data.complete?"Selector snapshot complete.":"PARTIAL — more pages remain; not all work is shown."}`
    } catch(error) { if(mine===epoch)throw error }
    finally { if(paging===request){paging=undefined;more.disabled=false} }
  }
  const refresh=button("Refresh current authorized snapshot",async()=>{reset();await page()})
  const more=button("Next page of the same captured snapshot",page);more.hidden=true
  const load=()=>{const mine=epoch;void page().catch(()=>{if(mine===epoch)status.textContent="UNAVAILABLE"})}
  categories.onchange=()=>{ family=categories.value as typeof WorkListFamilies[number];reset();load() }
  root.append(heading,limits,categories,refresh,status,rows,more,content)
  let mounted=false
  createEffect(()=>{
    const next=session()
    if(mounted&&next.key===current.key&&next.directory===current.directory&&next.projectRoot===current.projectRoot)return
    current=next;mounted=true;reset();load()
  })
  onCleanup(()=>{epoch++;rows.replaceChildren();content.replaceChildren()})
  return root
}
