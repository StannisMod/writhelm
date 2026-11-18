// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import { createHash } from "node:crypto"
import { Controller, type HostBoundary } from "./controller.js"
import { LinuxProtectedFiles } from "./protected.js"
import { requireThat, type Session } from "./contracts.js"
import { closedObject, uuidPattern } from "./native-runtime-client.js"
import { contextJSON } from "./native-context-codec.js"

export const approvalClientDirectory=(root:string,sessionID:string)=>
  path.join(root,"enrollment","approval-clients",createHash("sha256").update(sessionID).digest("hex"))

/** Native main owns these clients. Neither root nor owner credentials are model/RPC arguments.
 * One controller per exact chat keeps pending answers isolated across project/window switches. */
export class AutomaticApprovalClients {
  private readonly clients=new Map<string,Promise<{controller:Controller;identity:string;storeId:string;projectId:string;controllerId:string}>>()
  private closed=false
  constructor(private readonly root:string,private readonly host:HostBoundary,
    private readonly forbidden:(session:Session)=>Promise<readonly string[]>,private readonly fetcher:typeof fetch=fetch){}
  async get(session:Session,window:number):Promise<{controller:Controller;storeId:string;projectId:string;controllerId:string}>{
    requireThat(!this.closed && path.isAbsolute(this.root) && /^ses_[A-Za-z0-9_-]{1,240}$/.test(session.id),"UNAVAILABLE")
    await this.host.verify(session,window)
    requireThat(session.server==="sidecar" && session.key===`${session.server}\n${session.id}`,"FORBIDDEN")
    // Recheck the protected binding/profile on every call, not just first activation.
    const files=await LinuxProtectedFiles.open(approvalClientDirectory(this.root,session.id),await this.forbidden(session))
    let handedOff=false
    try{
      const b=closedObject(await files.readBinding(),["formatVersion","storeId","projectId","sessionId","directory","projectRoot","controllerId"])
      requireThat(b.formatVersion?.value==="1" && [b.storeId,b.projectId,b.controllerId].every(x=>typeof x==="string"&&uuidPattern.test(x)) &&
        b.sessionId===session.id && b.directory===session.directory && b.projectRoot===session.projectRoot,"STALE_VIEW")
      const profile=await files.read()
      requireThat(profile.storeId===b.storeId && profile.projectId===b.projectId && profile.pending?.kind!=="setup","FORBIDDEN")
      const identity=contextJSON({session, binding:b})
      requireThat(!this.closed,"UNAVAILABLE")
      let pending=this.clients.get(session.key)
      if(!pending){
        requireThat(this.clients.size<128,"BUSY")
        const controller=new Controller(this.host,this.fetcher)
        pending=controller.attach(files).then(()=>({controller,identity,storeId:b.storeId as string,projectId:b.projectId as string,controllerId:b.controllerId as string}))
        handedOff=true;this.clients.set(session.key,pending)
        // Failed attachment remains a hold for this incarnation, never another fresh client.
      }
      const selected=await pending
      requireThat(!this.closed && selected.identity===identity,"STALE_VIEW")
      return selected
    }finally{if(!handedOff)await files.close()}
  }
  closeWindow(window:number){for(const client of this.clients.values())void client.then(x=>x.controller.closeWindow(window),()=>{})}
  async close(){this.closed=true;await Promise.allSettled([...this.clients.values()].map(async client=>(await client).controller.close()))}
}
