// SPDX-License-Identifier: AGPL-3.0-only
import { Command, LinkHandler, Panel, Plugin, Store, type PanelTab } from "@opencode/plugin/gui"
import { Schema } from "effect"
import { createEffect, createSignal, onCleanup } from "solid-js"
import { Review, ReviewDrafts, ApprovalPresentation, OwnerOrderPresentation, type Session } from "./contracts.js"
import { ownerOrderPanel } from "./owner-order-panel.js"
import { nativePanel } from "./panel.js"
import { workPanel } from "./work-panel.js"
import { approvalReference, exactLink, ownsReviewReference, type ExactLink } from "./link.js"
import { main } from "./main.js"

export default Plugin.define({ main, stores:{
  reviewLink:Store.session(Schema.Struct({reference:Schema.String}),{reference:""})
},setup(ctx) {
  if (ctx.app.platform !== "desktop" || ctx.app.version !== "2.2.5") return
  const [links, setLinks] = createSignal<Record<string, ExactLink>>({})
  createEffect(()=>{
    const current=ctx.sessions.current();if(!current)return
    let closed=false,busy=false,last=""
    const poll=async()=>{
      if(closed||busy||ctx.sessions.current()?.key!==current.key)return
      busy=true
      try{
        const session:Session={key:current.key,id:current.id,server:current.server.id,directory:current.directory,
          projectRoot:current.project?.worktree??current.directory}
        const result=await ctx.main(OwnerOrderPresentation).view({session})
        if(closed||ctx.sessions.current()?.key!==current.key||!result.ok||!result.pageJSON)return
        const view=JSON.parse(result.pageJSON)
        if(!view.selected&&view.proposalDigest!==last){last=view.proposalDigest;ctx.layout.open(`${ctx.id}:owner-order`,current)}
      }catch{/* Missing authority/host delivery remains a hold, never an implicit order choice. */}
      finally{busy=false}
    }
    void poll();const timer=setInterval(()=>void poll(),3000)
    onCleanup(()=>{closed=true;clearInterval(timer)})
  })
  // Only the routed chat may open its own panel. Persisted server intents are polled through the
  // native-main boundary, not scraped from model prose or delivered to a project-global "planner".
  createEffect(()=>{
    const session=ctx.sessions.current()
    if(!session)return
    let closed=false,busy=false,last=""
    const poll=async()=>{
      if(closed||busy||ctx.sessions.current()?.key!==session.key)return
      busy=true
      try{
        const bound:Session={key:session.key,id:session.id,server:session.server.id,directory:session.directory,
          projectRoot:session.project?.worktree??session.directory}
         const result=await ctx.main(ApprovalPresentation).requested({session:bound,language:ctx.locale?.locale()||"ru"})
        if(closed||ctx.sessions.current()?.key!==session.key||!result.ok||!result.pageJSON)return
        const ref=exactLink(approvalReference(JSON.parse(result.pageJSON)))
        if(!ref||approvalReference(ref)===last)return
        last=approvalReference(ref)
        setLinks(before=>({...before,[session.key]:ref}))
        ctx.layout.open(`${ctx.id}:review`,session)
      }catch{/* Delivery remains pending; never fabricate a loaded/approved acknowledgement. */}
      finally{busy=false}
    }
    void poll()
    const timer=setInterval(()=>void poll(),1000)
    onCleanup(()=>{closed=true;clearInterval(timer)})
  })
  const tab: PanelTab = { id: "review", title: "Утверждение · Writhelm", closable: "compact", fallback: true,
    label:state=>{
      const label=document.createElement("span");label.textContent="Утверждение · Writhelm"
      label.style.cssText="display:block;width:100%;cursor:pointer"
      label.onclick=event=>{
        const session=state.session
        if(state.active){event.preventDefault();event.stopPropagation();ctx.layout.close(`${ctx.id}:review`,session)}
      }
      return label
    }}
  ctx.add(Panel, { id: "review", region: "side", wide: true, list: input => input.open.includes("review") ? [tab] : [], render: props => nativePanel((): Session => ({ key: props.session.key, id: props.session.id, server: props.session.server.id, directory: props.session.directory, projectRoot: props.session.project?.worktree ?? props.session.directory }), ctx.main(Review), () => {
    // A session-scoped UI pointer restores only the exact previously loaded packet. The native
    // controller still reauthorizes its source and digests; this is neither a planner nor assent.
    return links()[props.session.key]??exactLink(ctx.stores?.reviewLink(props.session).value?.reference??"")
  },
    async(session,ref)=>{
      ctx.stores?.reviewLink(props.session).set({reference:approvalReference(ref)})
       const result=await ctx.main(ApprovalPresentation).shown({session,requestId:ref.requestId,requestDigest:ref.requestDigest,packetDigest:ref.packetDigest,language:ctx.locale?.locale()||"ru"});if(!result.ok)throw Error("approval rendering acknowledgement held")
     },ctx.main(ReviewDrafts),()=>ctx.locale?.locale()||"ru",async(session,ref,message)=>{
      const mounted=props.session
      if(mounted.key!==session.key||mounted.id!==session.id)return
      ctx.layout.close(`${ctx.id}:review`,mounted)
      if(links()[session.key]&&approvalReference(links()[session.key])===approvalReference(ref))
        setLinks(before=>Object.fromEntries(Object.entries(before).filter(([key])=>key!==session.key)))
      const stored=ctx.stores?.reviewLink(mounted)
      if(stored?.value?.reference===approvalReference(ref))stored.set({reference:""})
      ctx.toast.show({title:message,description:"Результат сохранён. Дальнейший шаг — в чате.",variant:"success"})
    }) })
  ctx.add(Command, () => ({ id: "review", title: "Контракт · Writhelm", enabled: !!ctx.sessions.current(), run: () => { const s = ctx.sessions.current(); if (s) ctx.layout.toggle(`${ctx.id}:review`, s) } }))
  ctx.add(Panel,{id:"owner-order",region:"side",wide:true,
    list:input=>input.open.includes("owner-order")?[{id:"owner-order",title:"Порядок запуска Writhelm",closable:"compact",fallback:true}]:[],
    render:props=>ownerOrderPanel(()=>({key:props.session.key,id:props.session.id,server:props.session.server.id,
      directory:props.session.directory,projectRoot:props.session.project?.worktree??props.session.directory}),ctx.main(OwnerOrderPresentation))})
  ctx.add(Command,()=>({id:"owner-order",title:"Порядок запуска Writhelm",enabled:!!ctx.sessions.current(),
    run:()=>{const session=ctx.sessions.current();if(session)ctx.layout.open(`${ctx.id}:owner-order`,session)}}))
  const workTab: PanelTab = { id:"work",title:"Writhelm governed work",closable:"compact",fallback:true }
  ctx.add(Panel,{ id:"work",region:"side",wide:true,list:input=>input.open.includes("work")?[workTab]:[],render:props=>workPanel(():Session=>({
    key:props.session.key,id:props.session.id,server:props.session.server.id,directory:props.session.directory,
    projectRoot:props.session.project?.worktree??props.session.directory }),ctx.main(Review)) })
  ctx.add(Command,()=>({ id:"work",title:"Writhelm tasks and governed workflow",enabled:!!ctx.sessions.current(),
    run:()=>{const s=ctx.sessions.current();if(s)ctx.layout.open(`${ctx.id}:work`,s)} }))
  ctx.add(LinkHandler, { priority: 50, match: link => ownsReviewReference(link.href), exists: link => !!exactLink(link.href) && !!link.session && link.session.key === ctx.sessions.current()?.key, open: link => {
    const target = exactLink(link.href), session = link.session
    if (!target || !session || session.key !== ctx.sessions.current()?.key) {
      ctx.toast.show({ variant: "error", title: "Invalid or stale Writhelm request reference", description: "No request was opened. Use an exact reference in its originating session." })
      return
    }
    const previous=links()[session.key]
    if(previous&&approvalReference(previous)===approvalReference(target))ctx.layout.toggle(`${ctx.id}:review`,session)
    else {setLinks(before => ({ ...before, [session.key]: target })); ctx.layout.open(`${ctx.id}:review`, session)}
  } })
} })
