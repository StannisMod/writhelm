// SPDX-License-Identifier: AGPL-3.0-only
import { Plugin } from "@opencode/plugin/tui"
import { createHash } from "node:crypto"
import { ClientAdmission } from "./primary-rpc.js"

/** Native New session is normally a lazy draft. Persist the ordinary user session before routing
 * to it, so the server's automatic enrollment can run before any prompt. This UI issues no grants,
 * sees no Writhelm secrets and never claims that worker/execution admission is ready. */
export function registerAutomaticClientUI(ctx:Plugin.Context,hold:(stage:string)=>Promise<void>|void){
  if(ctx.app.version!=="2.2.5")throw Error("Writhelm test-client UI version is not qualified")
  type Intent={sessionID:string;directory:string}
  // Ephemeral per-view pointer survives plugin reload only. Required original-effect state is
  // retained server-side by exact future chat ID; views never share a project-wide pending slot.
  let action:ReturnType<typeof ctx.storage.memory<{pending:null|Intent}>>|undefined
  let closed=false,held=false,pending:Promise<void>|undefined,lastStage="location"
  const admit=async(sessionID:string,location:{directory:string})=>{
    lastStage="server-admission"
    const admitted=await ctx.client.rpc(ClientAdmission).admitController({sessionID},{location,signal:AbortSignal.timeout(30000)})
    if(!admitted||typeof admitted!=="object"||Object.keys(admitted).sort().join(",")!=="kind,projectId,role,sessionID"||
      (admitted as {kind?:unknown}).kind!=="governed-planning"||(admitted as {role?:unknown}).role!=="controller"||
      (admitted as {sessionID?:unknown}).sessionID!==sessionID||typeof (admitted as {projectId?:unknown}).projectId!=="string")
      throw Error("Current governed controller admission is unavailable")
  }
  const create=():Promise<void>=>{
    if(closed)return Promise.reject(Error("Writhelm UI is closed"))
    if(pending)return pending
    pending=(async()=>{
      const route=ctx.ui.router.current()
      const active=route.type==="session"?await ctx.client.session.get({sessionID:route.sessionID}):undefined
      const location=active?.location??ctx.location??ctx.data.location.default()
      if(!location||typeof location.directory!=="string")throw Error("Native location unavailable")
      lastStage="intent-store"
      const key="automatic-session-intent-v1-"+createHash("sha256").update(location.directory).digest("hex")
      const [legacy]=ctx.storage.store<{pending:null|Intent}>(key,{initial:{pending:null}})
      if(legacy.pending!==null)throw Error("Legacy pending creation requires exact-chat reconciliation; never claim it for a new view")
      action??=ctx.storage.memory<{pending:null|Intent}>("automatic-session-action-v2",{initial:{pending:null}})
      const [state,update]=action
      lastStage="location-sync"
      // Use the host's location-aware API, not a bare unscoped session.create request.
      await ctx.data.location.agent.sync(location)
      const intent=state.pending??{sessionID:`ses_${crypto.randomUUID().replaceAll("-","")}`,directory:location.directory}
      lastStage="intent-retention"
      if(state.pending===null)update(draft=>{
        if(draft.pending!==null)throw Error("Concurrent native creation is pending; reconcile the original intent")
        draft.pending=intent
      })
      if(!intent||intent.directory!==location.directory)throw Error("Pending native creation requires reconciliation in its original location")
      lastStage="native-session"
      // Retried RPC uses the same chat ID. Only the server journal can authorize original create;
      // a retained uncertain request is observed, never turned into a second native effect.
      const admitted=await ctx.client.rpc(ClientAdmission).createController({sessionID:intent.sessionID},{location,signal:AbortSignal.timeout(30000)})
      if(!admitted||typeof admitted!=="object"||Object.keys(admitted).sort().join(",")!=="kind,projectId,role,sessionID"||
        (admitted as {sessionID?:unknown}).sessionID!==intent.sessionID||
        (admitted as {kind?:unknown}).kind!=="governed-planning"||(admitted as {role?:unknown}).role!=="controller"||
        typeof (admitted as {projectId?:unknown}).projectId!=="string")throw Error("Native creation admission mismatch")
      const session=await ctx.client.session.get({sessionID:intent.sessionID})
      if(session.id!==intent.sessionID||session.location.directory!==intent.directory)throw Error("Native session binding mismatch")
      if(closed)return
      await admit(intent.sessionID,location)
      if(closed)return
      lastStage="intent-confirmation"
      update(draft=>{
        if(draft.pending?.sessionID!==intent.sessionID)throw Error("Native creation intent changed; refuse to clear another client's state")
        draft.pending=null
      })
      ctx.ui.router.navigate({type:"session",sessionID:session.id})
      lastStage="session-selected"
    })().finally(()=>{pending=undefined})
    return pending
  }
  const run=async()=>{
    try{await create()}
    catch{held=true;if(!closed)await hold(lastStage)}
  }
  ctx.keymap.layer(()=>({mode:"global",priority:100,commands:[{id:"session.new",title:"New session",group:"Session",
    bind:"<leader>n",palette:true,slash:{name:"new"},run:async()=>{
      // A service-connect witness can precede completion of initial UI admission. An ordinary
      // New session received then is a NEW intent, not a retry of initial creation.
      await initial
      if(!closed&&!held)await run()
    }}],bindings:["session.new"]}))
  const route=ctx.ui.router.current()
  const initial=route.type==="home"?run():(async()=>{
    if(route.type!=="session")return
    try{
      lastStage="restored-native-session"
      const session=await ctx.client.session.get({sessionID:route.sessionID})
      if(session.id!==route.sessionID||typeof session.location?.directory!=="string")throw Error("Restored native session mismatch")
      await admit(session.id,session.location)
    }catch{held=true;if(!closed)await hold(lastStage)}
  })()
  return {create,initial,close(){closed=true}}
}

export default Plugin.define({id:"writhelm-automatic-client-ui",async setup(ctx){
  const lifecycle=registerAutomaticClientUI(ctx,async stage=>{
    try{await ctx.renderer.idle();ctx.renderer.destroy()}finally{
      process.stderr.write(`Writhelm session initialization held (${stage}); no unmanaged fallback. Retained creation intent is preserved.\n`)
      process.exit(2)
    }
  })
  await lifecycle.initial
  return ()=>lifecycle.close()
}})
