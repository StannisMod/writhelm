// SPDX-License-Identifier: AGPL-3.0-only
import { createEffect, onCleanup } from "solid-js"
import { isLosslessNumber } from "lossless-json"
import type { IpcClient } from "@opencode/plugin/gui"
import { OwnerOrderPresentation, type Session } from "./contracts.js"
import { exactJSON } from "./strict-json.js"

/** Trusted native-main data only; source strings are text, never executable HTML. No decision
 * happens at mount/poll/reconnect. The sole select RPC is an explicit enabled owner button. */
export function ownerOrderPanel(session:()=>Session,bridge:IpcClient<typeof OwnerOrderPresentation.spec>){
  const root=document.createElement("section"),status=document.createElement("p"),body=document.createElement("div")
  root.style.cssText="height:100%;overflow:auto;padding:0.75rem;overflow-wrap:anywhere"
  root.append(status,body)
  let epoch=0
  const parse=(s:string)=>exactJSON(s,()=>{throw Error("Invalid exact owner-order view")})
  createEffect(()=>{
    const bound=session(),mine=++epoch;let closed=false,busy=false
    const refresh=async()=>{
      if(busy||closed)return
      busy=true
      try{
        const result=await bridge.view({session:bound})
        if(closed||mine!==epoch)return
        if(!result.ok||!result.pageJSON){status.textContent=result.ok?"Нет предложенного порядка запуска.":"Порядок запуска недоступен: "+result.code;body.replaceChildren();return}
        const view=parse(result.pageJSON)
        status.textContent=view.selected?"Этот точный порядок выбран. Это не подтверждение выполнения задач.":"Выбери порядок запуска перед выполнением."
        body.replaceChildren()
        const note=document.createElement("p")
        note.textContent="Без явных ограничений готовые задачи выполняются параллельно. «После» означает после запуска указанной задачи, не после её завершения. Утверждение контрактов и остальные проверки остаются обязательными."
        body.append(note)
        const render=(label:string,value:any,parent:HTMLElement)=>{
          if(value===null||typeof value!=="object"||isLosslessNumber(value)){
            const p=document.createElement("p");p.style.whiteSpace="pre-wrap"
            p.textContent=label+": "+(isLosslessNumber(value)?value.value:value===null?"—":String(value));parent.append(p);return
          }
          for(const [key,item]of Object.entries(value))render(label?label+"."+key:key,item,parent)
        }
        for(let i=0;i<view.selection.tasks.length;i++){
          const item=view.selection.tasks[i],record=parse(view.records[i]),ref=parse(item.taskJSON)
          const section=document.createElement("section"),heading=document.createElement("h3"),edge=document.createElement("p")
          heading.textContent=record.data?.payload?.objective??ref.key.taskId
          edge.textContent="Задача "+ref.key.taskId+" / версия "+ref.key.revision.value+
            (item.after.length?" · После запуска: "+item.after.join(", "):" · Без ограничения порядка владельцем")
          section.append(heading,edge);render("Контракт",record.data,section);body.append(section)
        }
        const choose=document.createElement("button")
        choose.textContent="Выбрать показанный точный порядок запуска";choose.disabled=view.selected
        choose.onclick=async()=>{
          choose.disabled=true
          try{
            const answer=await bridge.select({session:bound,proposalId:view.selection.proposalId,
              proposalDigest:view.proposalDigest,expectedDecision:view.expectedDecision??""})
            if(closed||mine!==epoch)return
            status.textContent=answer.ok?"Порядок выбран. Запуск всё ещё требует текущего допуска сервиса.":"Выбор не подтверждён: "+answer.code
          }catch{if(!closed&&mine===epoch)status.textContent="Исход выбора неизвестен; обнови точное состояние. Новый выбор автоматически не отправляется."}
        }
        body.append(choose)
      }catch{if(!closed&&mine===epoch){status.textContent="Проверка точного порядка недоступна.";body.replaceChildren()}}
      finally{busy=false}
    }
    void refresh();const timer=setInterval(()=>void refresh(),3000)
    onCleanup(()=>{closed=true;clearInterval(timer)})
  })
  return root
}
