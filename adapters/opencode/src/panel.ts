// SPDX-License-Identifier: AGPL-3.0-only
import { createEffect, onCleanup } from "solid-js"
import type { IpcClient } from "@opencode/plugin/gui"
import { Review, ReviewDrafts, reply, type Session, type Reply } from "./contracts.js"
import { serviceResult, completeBundle, referenceKey, type Json } from "./wire.js"

import type { ExactLink } from "./link.js"
export { exactLink, type ExactLink } from "./link.js"
export function nativePanel(session: () => Session, bridge: IpcClient<typeof Review.spec>, linked: () => ExactLink | undefined,
  onLoaded?:(session:Session,ref:ExactLink)=>Promise<void>, drafts?:IpcClient<typeof ReviewDrafts.spec>,
  language?:()=>string,onResolved?:(session:Session,ref:ExactLink,message:string)=>Promise<void>|void): HTMLElement {
  const root = document.createElement("section"); root.setAttribute("aria-label", "Writhelm exact native review")
  const fit = (element: HTMLElement) => Object.assign(element.style, { minWidth: "0", maxWidth: "100%", boxSizing: "border-box", overflowWrap: "anywhere" })
  // The host clips its definite panel viewport. Own the scroll surface, not the host's CSS.
  fit(root); Object.assign(root.style, { display: "block", width: "100%", height: "100%", minHeight: "0", padding: "24px", overflowY: "auto", overflowX: "auto", whiteSpace: "normal",
    fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif", fontSize: "15px", lineHeight: "1.65", color: "inherit",userSelect:"text" })
  root.tabIndex = 0 // Native wheel/keyboard scrolling; never intercept text/radio navigation keys.
  const visibility=document.createElement("style")
  visibility.textContent='section[aria-label="Writhelm exact native review"] [hidden]{display:none!important}'
  root.append(visibility)
  let epoch = 0, current = session(), handle = "", bundle: Json, project = "", cursor = "",activeRef:ExactLink|undefined
  const status = document.createElement("p"); status.setAttribute("role", "status")
  const decisionStatus=document.createElement("p")
  decisionStatus.setAttribute("role","status");decisionStatus.setAttribute("aria-live","polite")
  decisionStatus.dataset.decisionStatus="true";decisionStatus.hidden=true
  Object.assign(decisionStatus.style,{maxWidth:"76ch",margin:"12px auto",padding:"12px",border:"1px solid #7776",borderRadius:"8px",fontWeight:"600"})
  let submittedChoice=""
  let connectionTimer:ReturnType<typeof setTimeout>|undefined
  const decisionMessage=(text:string)=>{decisionStatus.textContent=text;decisionStatus.hidden=!text}
  const pending = document.createElement("section"), content = document.createElement("section"), choices = document.createElement("fieldset")
  Object.assign(content.style,{maxWidth:"76ch",marginInline:"auto"})
  Object.assign(choices.style,{maxWidth:"76ch",margin:"24px auto",padding:"20px",border:"1px solid #7776",borderRadius:"12px"})
  fit(choices); Object.assign(choices.style, { minInlineSize: "0", width: "100%" })
  const comments = new Map<string, HTMLTextAreaElement>(); let selected = ""
  let closePointMenu=()=>{}
  let removePointMenu=()=>{}
  let flushDraft:()=>Promise<void>=async()=>{}
  const button = (label: string, action: () => Promise<void> | void) => {
    const b = document.createElement("button"); b.type = "button"; b.textContent = label
    fit(b); Object.assign(b.style, { display: "block", whiteSpace: "normal", marginBlock: "0.5rem",padding:"10px 16px",border:"1px solid #7776",borderRadius:"8px",font:"inherit",cursor:"pointer" })
    b.onclick = () => { Promise.resolve().then(action).catch(() => { choices.disabled = true; status.textContent = "UNAVAILABLE — no rollback inferred; retain the original command." }) }
    return b
  }
  function paragraph(label: string, value: string, target:HTMLElement = content, prose=false) {
    const h = document.createElement("h3"); h.textContent = label
    Object.assign(h.style,{fontFamily:"inherit",fontSize:"18px",fontWeight:"650",lineHeight:"1.35",margin:"28px 0 12px"})
    const p = document.createElement(prose?"div":"pre")
    fit(p); Object.assign(p.style, { whiteSpace: "pre-wrap", wordBreak: "normal",fontFamily:prose?"inherit":"monospace",fontSize:prose?"inherit":"12px",margin:"0 0 18px" })
    // Lossless visual paragraphs, not summarisation or Markdown/HTML interpretation.
    // Keep punctuation and separating whitespace in their original order/codepoints.
    if(prose){
      for(const text of value.match(/[^.!?\n]+(?:[.!?]+(?:\s+|$)|\n+|$)|[.!?\n]+/gu)??[value]){
        const block=document.createElement("span");block.textContent=text
        Object.assign(block.style,{display:"block",marginBottom:"10px"});p.append(block)
      }
      if(p.textContent!==value)p.textContent=value
    }else p.textContent = value
    target.append(h, p)
  }
  function reset() { if(connectionTimer){clearTimeout(connectionTimer);connectionTimer=undefined}closePointMenu();removePointMenu();removePointMenu=()=>{};void flushDraft();flushDraft=async()=>{};++epoch; handle = ""; bundle = undefined;activeRef=undefined; selected = "";submittedChoice="";decisionMessage(""); comments.clear(); content.replaceChildren(); choices.replaceChildren(); choices.disabled = true }
  async function finishDecision(result:Reply,mine:number,ref:ExactLink,bound:Session){
    if(mine!==epoch||!result.ok||result.pending||!result.resultJSON)return
    const receipt=serviceResult(result.resultJSON)
    // The canonical success result is the exact resolved request ID. This also covers original-
    // command reconciliation without a second write or closing a different request's view.
    if(receipt.kind!=="settled"||receipt.identity?.operation!=="resolve_approval"||
      receipt.identity.projectId!==ref.project||receipt.outcome?.kind!=="success"||receipt.outcome.result!==ref.requestId){
      decisionMessage("Ответ не подтверждает решение именно по этому запросу. Повторно отправлять его нельзя до проверки исходной команды.")
      return
    }
    const message=submittedChoice==="approve"?"Контракт утверждён.":submittedChoice==="changes-required"?
      "Запрос возвращён с замечаниями.":submittedChoice==="reject"?"Запрос отклонён.":"Решение сохранено."
    try{await onResolved?.(bound,ref,message)}catch{decisionMessage(message+" Не удалось закрыть панель; повторно отправлять решение не нужно.")}
  }
  function outcome(r: Reply) {
    retry.hidden=!r.pending
    status.textContent = (r.code==="OK"?"":r.code) + (r.pending ? " — исходная команда сохранена; проверь её результат, не создавай новую." : "")
    if (r.resultJSON) {
      const details=document.createElement("details"),summary=document.createElement("summary")
      summary.textContent="Результат отправки · технические подробности";details.append(summary);content.append(details)
      paragraph("Original canonical outcome (not a rollback claim)",r.resultJSON,details)
      if(r.ok&&!r.pending){
        status.textContent=submittedChoice==="approve"?"Контракт утверждён. Решение сохранено.":"Решение сохранено."
        decisionMessage(status.textContent)
      }
    }
    if(r.pending)decisionMessage("Отправка ещё не подтверждена. Проверь исходную команду; повторно утверждать не нужно.")
    else if(!r.ok&&submittedChoice)decisionMessage("Решение не подтверждено: "+r.code+".")
    if(submittedChoice&&!decisionStatus.hidden)decisionStatus.scrollIntoView?.({block:"nearest"})
    if (!r.ok) choices.disabled = true
  }
  async function refresh(attempt=0) {
    reset(); const mine = epoch, bound = current
    const r = await bridge.status({ session: bound }).catch(()=>reply("UNAVAILABLE")); if (mine !== epoch) return; outcome(r)
    if(!r.ok&&r.code==="UNAVAILABLE"&&!r.pending&&attempt<4){
      status.textContent="Подключаюсь к сервису… Решение повторно не отправляется."
      connectionTimer=setTimeout(()=>{if(mine===epoch)void refresh(attempt+1)},500)
      return
    }
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
    for (const item of page.items) pending.append(button("Открыть запрос " + item.id, () => open({ project, requestId: item.id, requestDigest: item.requestDigest, packetDigest: item.packetDigest })))
    cursor = page.nextCursor ?? ""; more.hidden = !cursor
  }
  async function open(ref: ExactLink) {
    reset() // Controller.open invalidates the old handle atomically; no delayed dismiss race.
    pending.replaceChildren()
    const mine = epoch, bound = current
    const r = await bridge.open({ session: bound, requestId: ref.requestId, requestDigest: ref.requestDigest, packetDigest: ref.packetDigest })
    if (mine !== epoch) return; outcome(r); if (!r.ok) return
    const b = completeBundle(r.bundleJSON, ref.requestId, ref.requestDigest, ref.packetDigest, ref.project)
    bundle = b; handle = r.handle;activeRef=ref
    dismiss.textContent = "Закрыть без решения"
    const p = b.request.payload.packet
    const requestedLanguage=language?.()
    const presentations=p.presentations??[]
    const presentation=requestedLanguage?presentations.find((v:Json)=>v.language===requestedLanguage)??
      presentations.find((v:Json)=>v.language===requestedLanguage.split("-")[0]):undefined
    const languageMissing=!!requestedLanguage&&!presentation
    if(languageMissing)paragraph("Перевод этой версии отсутствует",
      "Для языка "+requestedLanguage+" нет привязанного к пунктам перевода. Ниже английский оригинал; решение недоступно до подготовки соответствующего представления.",content,true)
    const context=document.createElement("details"),contextTitle=document.createElement("summary")
    contextTitle.textContent="Обоснование и рамки решения";context.append(contextTitle);content.append(context)
    Object.assign(contextTitle.style,{textAlign:"right",cursor:"pointer"})
    if(b.followup){
      const answer=b.followup.resolution.payload.answer
      if(answer.kind==="reject-with-reason")paragraph("Причина предыдущего отклонения",answer.reason,content,true)
      paragraph("Повторное согласование","Открыты изменённые и новые пункты. Неизменённые пункты, всё ещё требующие решения, доступны ниже и входят в это согласование.",content,true)
    }
    for (const [title, value] of [["Что предлагается утвердить", p.question], ["Требования и границы", p.scope], ["Зачем нужно решение", p.rationale], ["Предлагаемые правила и последствия", p.consequences.join("\n")], ["Другие варианты", p.alternatives.join("\n")], ["Что ещё не определено", p.uncertainties.join("\n")]]) {
      if(value)paragraph(title,value,context,true)
    }
    const fullDocument=document.createElement("details"),fullTitle=document.createElement("summary")
    fullTitle.textContent="Английский оригинал · точная версия";fullDocument.append(fullTitle)
    Object.assign(fullTitle.style,{textAlign:"right",cursor:"pointer",fontWeight:"600",padding:"10px"})
    content.prepend(fullDocument)
    for (const material of b.materials) {
      const metadata=document.createElement("details"),label=document.createElement("summary")
      label.textContent="Точная версия и статус источника";metadata.append(label)
      paragraph("Идентификаторы и статус",JSON.stringify(material.reference)+"\n"+material.observation,metadata);fullDocument.append(metadata)
      for (const f of material.fragments) paragraph("Текст утверждаемого документа · "+f.span.start+"–"+f.span.endExclusive,f.text,fullDocument,true)
    }
    // Optional diagnostics do not duplicate transport JSON into the ordinary decision view.
    // All exact bytes remain available; required source fragments above are never collapsed away.
    const details=document.createElement("details"),summary=document.createElement("summary")
    summary.textContent="Технические подробности и исходные данные";details.append(summary);content.append(details)
    paragraph("Complete canonical packet, material and original attribution", r.bundleJSON,details)
    paragraph("Limits / known gate", "Source coverage is not proof of semantic sufficiency, truth or comprehension. Native host qualification, Windows/macOS, K-RUN abnormal recovery, resource maxima, release and full M1 remain held. L001 historical startup timeout remains unexplained. Native T1 is cooperative, not physical-human proof.",details)
    choices.replaceChildren(); const legend = document.createElement("legend"); legend.textContent = "Твоё решение"; choices.append(legend)
    fit(legend)
    const actions=document.createElement("div"),reason=document.createElement("textarea")
    let queueChange=()=>{},draftReady=!drafts,draftFailed=false,submitting=false
    fit(reason)
    reason.setAttribute("aria-label","Причина отклонения");reason.placeholder="Почему контракт отклонён?";reason.maxLength=4096
    reason.value=""
    Object.assign(reason.style,{display:"block",width:"100%",minHeight:"80px",font:"inherit",padding:"10px",boxSizing:"border-box",resize:"vertical"})
    reason.hidden=true
    const updateActions=()=>{
      const annotated=[...comments.values()].some(a=>!!a.value.trim())
      approve.hidden=reject.hidden=annotated;changes.hidden=!annotated
      if(annotated)reason.hidden=true
    }
    const unchanged=document.createElement("details"),unchangedTitle=document.createElement("summary")
    unchangedTitle.textContent="Неизменённые пункты, входящие в решение";unchanged.append(unchangedTitle)
    unchanged.hidden=!p.followup?.items.some((c:Json)=>c.disposition==="unchanged")
    if(b.followup)content.append(unchanged)
    const pointMenu=document.createElement("div")
    pointMenu.setAttribute("role","menu");pointMenu.setAttribute("aria-label","Действие с пунктом")
    pointMenu.setAttribute("popover","manual")
    Object.assign(pointMenu.style,{position:"fixed",zIndex:"10000",padding:"4px",border:"1px solid #777",
      inset:"auto",margin:"0",width:"max-content",minWidth:"120px",whiteSpace:"nowrap",overflowWrap:"normal",
      borderRadius:"6px",background:"var(--background-base, Canvas)",color:"var(--text-base, CanvasText)",boxShadow:"0 4px 16px #0004"})
    pointMenu.hidden=true
    let menuArea:HTMLTextAreaElement|undefined
    const object=button("возразить",()=>{
      if(!menuArea||menuArea.disabled)return
      const area=menuArea
      closePointMenu();area.hidden=false;area.focus()
    })
    object.setAttribute("role","menuitem");object.style.margin="0"
    pointMenu.append(object);content.append(pointMenu)
    closePointMenu=()=>{
      if(typeof pointMenu.hidePopover==="function"&&pointMenu.isConnected)pointMenu.hidePopover()
      pointMenu.hidden=true;menuArea=undefined
    }
    const dismissMenu=(event:Event)=>{if(!pointMenu.contains(event.target as Node))closePointMenu()}
    const escapeMenu=(event:KeyboardEvent)=>{if(event.key==="Escape"&&!pointMenu.hidden){event.preventDefault();const area=menuArea;closePointMenu();area?.closest<HTMLElement>("[data-item-id]")?.focus()}}
    document.addEventListener("pointerdown",dismissMenu)
    root.addEventListener("keydown",escapeMenu)
    removePointMenu=()=>{document.removeEventListener("pointerdown",dismissMenu);root.removeEventListener("keydown",escapeMenu)}
    for (const item of p.items) {
      const change=p.followup?.items.find((c:Json)=>c.itemId===item.id)
      const previousItem=b.followup?.request.payload.packet.items.find((i:Json)=>i.id===change?.previousItemId)
      const point=document.createElement("section"),heading=document.createElement("h3")
      point.dataset.itemId=item.id
      point.tabIndex=0
      Object.assign(point.style,{padding:"0",marginBlock:"24px",userSelect:"text"})
      // The canonical clause anchor is the number/name. Never manufacture one from a sentence.
      heading.textContent=item.target.kind==="clause"?item.target.clauseId:"Контекст документа"
      const exactMaterial=b.materials.find((m:Json)=>referenceKey(m.reference)===referenceKey(item.target))
      const rawFirstLine=exactMaterial?.fragments[0]?.text.split(/\r?\n/u,1)[0]
      const firstLine=rawFirstLine?.replace(/^#{1,6}\s+/u,"").replace(/^\*\*(.*?)\*\*$/u,"$1")
      const translated=presentation?.items.find((entry:Json)=>entry.itemId===item.id)
      // Display an explicitly written heading; identity remains the canonical clause anchor.
      if(item.target.kind==="clause"&&typeof firstLine==="string"&&firstLine.length<=200&&
        firstLine.startsWith(item.target.clauseId+" — "))heading.textContent=firstLine
      if(translated)heading.textContent=(item.target.clauseId??"Контекст")+" — "+translated.title
      point.append(heading)
      if(change)paragraph("Ответ автора · "+({revised:"изменён",added:"добавлен",unchanged:"без изменений"} as Record<string,string>)[change.disposition],
        change.explanation,point,true)
      const oldComment=b.followup?.resolution.payload.answer.comments?.find((c:Json)=>c.itemId===change?.previousItemId)
      if(oldComment)paragraph("Замечание к предыдущей версии",oldComment.text,point,true)
      const oldRef=previousItem?.target??item.before
      if(!exactMaterial)throw Error("Exact point material unavailable")
      for(const [index,fragment]of (translated?[{text:translated.text}]:exactMaterial.fragments).entries()){
        const text=document.createElement("div")
        text.dataset.canonicalText="true"
        let visible=fragment.text
        // The explicitly authored heading is rendered once, above its own exact body, not as a
        // translated/card paraphrase. Full untouched source bytes remain in the source disclosure.
        if(!translated&&index===0&&heading.textContent===firstLine&&rawFirstLine!==undefined){
          visible=visible.slice(rawFirstLine.length)
          if(visible.startsWith("\r\n"))visible=visible.slice(2)
          else if(visible.startsWith("\n"))visible=visible.slice(1)
        }
        text.textContent=visible
        Object.assign(text.style,{whiteSpace:"pre-wrap",overflowWrap:"anywhere",userSelect:"text",marginBottom:"12px"})
        point.append(text)
      }
      if(oldRef&&change?.disposition!=="unchanged"){
        const previous=document.createElement("details"),title=document.createElement("summary")
        title.textContent="Предыдущая формулировка пункта";previous.append(title)
        const material=[...b.materials,...(b.followup?.materials??[])].find((m:Json)=>referenceKey(m.reference)===referenceKey(oldRef))
        if(!material)throw Error("Exact previous material unavailable")
        for(const fragment of material.fragments)paragraph("Было",fragment.text,previous,true)
        point.append(previous)
      }
      const area = document.createElement("textarea")
      area.setAttribute("aria-label","Возражение к "+heading.textContent);area.placeholder="возражение"
      area.hidden=true;area.maxLength=4096;area.value="";comments.set(item.id,area);point.append(area)
      fit(area); Object.assign(area.style, { display: "block", width: "100%", resize: "vertical",font:"inherit",padding:"10px",minHeight:"72px" })
      area.oninput=()=>{updateActions();queueChange()}
      const showMenu=(x:number,y:number)=>{
        if(area.disabled)return
        closePointMenu();menuArea=area;pointMenu.hidden=false
        // Desktop panes establish transformed containing blocks. The top layer keeps the menu
        // in viewport coordinates instead of clipping/squeezing it at the pane's right edge.
        if(typeof pointMenu.showPopover==="function")pointMenu.showPopover()
        const bounds=pointMenu.getBoundingClientRect()
        pointMenu.style.left=Math.max(0,Math.min(x,window.innerWidth-bounds.width))+"px"
        pointMenu.style.top=Math.max(0,Math.min(y,window.innerHeight-bounds.height))+"px"
        object.focus()
      }
      point.oncontextmenu=event=>{
        // Keep native editing/selection context actions inside an already opened objection.
        if(event.target===area)return
        event.preventDefault();event.stopPropagation();showMenu(event.clientX,event.clientY)
      }
      point.onkeydown=event=>{
        if(event.target!==point)return
        if(event.key==="ContextMenu"||(event.shiftKey&&event.key==="F10")){
          event.preventDefault();const bounds=heading.getBoundingClientRect();showMenu(bounds.left,bounds.bottom)
        }
      }
      ;(change?.disposition==="unchanged"?unchanged:content).append(point)
    }
    if(b.followup){
      const outside=document.createElement("details"),title=document.createElement("summary")
      title.textContent="Сохранённые и исключённые пункты предыдущего запроса";outside.append(title)
      outside.hidden=!p.followup.items.some((c:Json)=>!c.itemId)
      for(const change of p.followup.items.filter((c:Json)=>!c.itemId)){
        const item=b.followup.request.payload.packet.items.find((i:Json)=>i.id===change.previousItemId)
        paragraph((change.disposition==="retained"?"Сохранён ранее утверждённый пункт · ":"Исключён из предложения · ")+
          (item.target.clauseId??"контекст"),change.explanation,outside,true)
        const comment=b.followup.resolution.payload.answer.comments?.find((c:Json)=>c.itemId===change.previousItemId)
        if(comment)paragraph("Предыдущее замечание",comment.text,outside,true)
      }
      content.append(outside)
    }
    const sourceActionable=!!b.actionable&&!r.pending&&!languageMissing
    const setEditable=()=>{
      if(mine!==epoch)return
      choices.disabled=!sourceActionable||!draftReady||draftFailed||submitting
      reason.disabled=!sourceActionable||!draftReady||submitting
      for(const area of comments.values())area.disabled=!sourceActionable||!draftReady||submitting
    }
    const send=async(choice:"approve"|"reject"|"changes-required")=>{
      if (!handle || !sourceActionable || r.pending||!draftReady||draftFailed||submitting) return
      const anchored = [...comments].filter(([, a]) => a.value.trim()).map(([itemId, a]) => ({ itemId, text: a.value }))
      if(choice!=="changes-required"&&anchored.length)return
      if (choice === "changes-required" && !anchored.length) { status.textContent = "Add a nonblank item-anchored comment."; return }
      if(choice==="reject"&&!reason.value.trim()){reason.hidden=false;reason.focus();return}
      const preparedEpoch = epoch;submitting=true;submittedChoice=choice;decisionMessage("Сохраняю решение…"); choices.disabled = true
      reason.disabled=true;for(const area of comments.values())area.disabled=true
      await flushDraft()
      if(preparedEpoch!==epoch)return
      if(draftFailed){submitting=false;decisionMessage("Решение не отправлено: сохранение черновика не подтверждено.");setEditable();return}
      const result = await bridge.answer({ session: current, handle, choice, comments: anchored,...(choice==="reject"?{reason:reason.value}:{}) })
      if (preparedEpoch === epoch){outcome(result);await finishDecision(result,preparedEpoch,ref,bound)}
    }
    const approve=button("Утвердить",()=>send("approve")),reject=button("Отклонить",()=>send("reject")),
      changes=button("Вернуть с замечаниями",()=>send("changes-required"))
    actions.append(approve,reject,changes);choices.append(reason,actions);updateActions()
    if(b.resolution){
      const kind=b.resolution.payload.answer.kind
      decisionMessage(kind==="approve"?"Контракт утверждён. Решение сохранено.":kind==="changes-required"?"Запрос возвращён с замечаниями.":"Запрос отклонён.")
    }
    setEditable()
    if(drafts){
      const note=document.createElement("p");note.setAttribute("role","status");note.textContent="Загружаю точный черновик…"
      const retrySave=button("Повторить сохранение черновика",async()=>{await flushDraft()});retrySave.hidden=true
      content.append(note,retrySave)
      const selector={session:bound,requestId:ref.requestId,requestDigest:ref.requestDigest,packetDigest:ref.packetDigest}
      const areas=new Map(comments)
      let digest="",timer:ReturnType<typeof setTimeout>|undefined,queue=Promise.resolve(),last=""
      const snapshot=()=>({comments:[...areas].filter(([,area])=>area.value!=="").map(([itemId,area])=>({itemId,text:area.value})),reason:reason.value})
      const persist=()=>{
        const value=snapshot(),serialized=JSON.stringify(value)
        queue=queue.then(async()=>{
          if(serialized===last&&!draftFailed)return
          try{
            const saved=await drafts.save({...selector,expectedDigest:digest,...value})
            if(!saved.ok)throw Error(saved.code)
            const result=JSON.parse(saved.pageJSON)
            if(typeof result.digest!=="string"||!/^[a-f0-9]{64}$/.test(result.digest))throw Error("INTEGRITY")
            digest=result.digest;last=serialized;draftFailed=false
            if(mine===epoch){note.textContent="Черновик сохранён для этой точной версии. Решение не отправлено.";retrySave.hidden=true;setEditable()}
          }catch{
            draftFailed=true
            if(mine===epoch){note.textContent="Черновик не подтверждён. Текст оставлен здесь; другая версия не перезаписана. Повтори сохранение или сохрани текст отдельно перед закрытием.";retrySave.hidden=false;setEditable()}
          }
        })
        return queue
      }
      flushDraft=()=>{
        if(timer){clearTimeout(timer);timer=undefined}
        if(!draftReady||!sourceActionable)return queue
        return persist()
      }
      queueChange=()=>{
        if(!draftReady||!sourceActionable)return
        note.textContent="Сохраняю черновик…"
        if(timer)clearTimeout(timer)
        timer=setTimeout(()=>{timer=undefined;void persist()},300)
      }
      reason.oninput=queueChange
      try{
        const loaded=await drafts.load(selector)
        if(mine!==epoch)return
        if(!loaded.ok)throw Error(loaded.code)
        const value=JSON.parse(loaded.pageJSON)
        if(typeof value.digest!=="string"||!/^([a-f0-9]{64})?$/.test(value.digest)||typeof value.reason!=="string"||
          !Array.isArray(value.comments)||value.comments.length>16||new Set(value.comments.map((c:any)=>c.itemId)).size!==value.comments.length||
          value.comments.some((c:any)=>!areas.has(c.itemId)||typeof c.text!=="string"))throw Error("INTEGRITY")
        digest=value.digest;reason.value=value.reason
        for(const comment of value.comments){const area=areas.get(comment.itemId)!;area.value=comment.text;area.hidden=comment.text===""}
        last=JSON.stringify(snapshot());draftReady=true;updateActions()
        if(reason.value.trim()&&![...areas.values()].some(a=>a.value.trim()))reason.hidden=false
        note.textContent=b.resolution?"Запрос завершён; сохранённый черновик не меняет решение.":digest?"Восстановлен черновик этой точной версии. Решение не отправлено.":"Комментарии сохраняются только для этого запроса и версии."
        setEditable()
      }catch{
        draftFailed=true;note.textContent="Не удалось проверить сохранённый черновик. Решение недоступно, чтобы не потерять замечания.";setEditable()
      }
    }
    try{await onLoaded?.(bound,ref)}catch{
      // Notification loss cannot rewrite the canonical view or turn a complete panel into assent.
      if(mine===epoch)status.textContent="Exact request loaded; rendering acknowledgement is held. No owner decision submitted."
    }
    if(mine===epoch&&b.resolution&&!r.pending){
      const kind=b.resolution.payload.answer.kind
      try{await onResolved?.(bound,ref,kind==="approve"?"Контракт утверждён.":kind==="changes-required"?"Запрос возвращён с замечаниями.":"Запрос отклонён.")}catch{
        decisionMessage("Решение сохранено. Не удалось закрыть панель; повторно отправлять его не нужно.")
      }
    }
  }
  const connect = button("Connect protected client profile (native chooser)", async () => { outcome(await bridge.connect({ session: current })); await refresh() })
  const origin = document.createElement("input"); origin.placeholder = "Pinned loopback service origin (not a key)"; origin.setAttribute("aria-label", "Service origin")
  fit(origin); origin.style.width = "100%"
  const setup = button("Set up a NEW local store and protected client files", async () => { outcome(await bridge.setup({ session: current, origin: origin.value })) })
  const retry = button("Проверить исходную отправленную команду", async () => {
    const mine=epoch,ref=activeRef,bound=current,result=await bridge.reconcile({session:bound})
    if(mine!==epoch)return
    outcome(result);if(ref)await finishDecision(result,mine,ref,bound)
  })
  const rotate = button("Rotate owner credential (persist replacement first)", async () => { outcome(await bridge.rotate({ session: current })) })
  const dismiss = button("Закрыть без решения", async () => { const h = handle, s = current; reset(); if (h) outcome(await bridge.dismiss({ session: s, handle: h })) })
  dismiss.dataset.action = "dismiss"
  const more = button("Next inbox snapshot page (not a decision packet)", inbox); more.hidden = true
  const administration=document.createElement("details"),administrationTitle=document.createElement("summary")
  administrationTitle.textContent="Настройки подключения";administration.append(administrationTitle,connect,origin,setup,rotate)
  const navigation=document.createElement("details"),navigationTitle=document.createElement("summary")
  navigationTitle.textContent="Другие запросы и подключение";navigation.append(navigationTitle,button("Ожидающие решения",refresh),administration)
  retry.hidden=true
   root.append(status, pending, more, content, choices,decisionStatus,retry,navigation)
  let mounted = false, previousLink = "",previousLanguage=""
  createEffect(() => {
    const next = session(), link = JSON.stringify(linked()),nextLanguage=language?.()??""
    if (mounted && next.key === current.key && next.directory === current.directory && next.projectRoot === current.projectRoot && link === previousLink&&nextLanguage===previousLanguage) return
    const previous = current, h = handle; reset(); current = next; mounted = true; previousLink = link;previousLanguage=nextLanguage
    if (h) void bridge.dismiss({ session: previous, handle: h })
    void refresh().catch(() => { choices.disabled = true; status.textContent = "UNAVAILABLE" })
  })
  onCleanup(() => { const h = handle, s = current; reset(); if (h) void bridge.dismiss({ session: s, handle: h }) })
  return root
}
