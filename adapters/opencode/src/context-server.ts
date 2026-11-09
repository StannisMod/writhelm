// SPDX-License-Identifier: AGPL-3.0-only
import path from "node:path"
import * as fs from "node:fs/promises"
import { Effect } from "effect"
import { Plugin } from "@opencode/plugin/effect"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable,uuidPattern } from "./native-runtime-client.js"
import { registerNativeContextDispatcher } from "./native-context-dispatch.js"

const io=<A>(f:()=>Promise<A>)=>Effect.tryPromise({try:f,catch:()=>new Error("protected native context bootstrap unavailable")})
/** Companion to the Promise primary plugin. Direct host Effect scope owns extraction/generation;
 * no nested runPromise bridge, default profile, service discovery or model wake at setup. */
export default Plugin.define({id:"writhelm-task-context",effect:ctx=>Effect.gen(function*(){
  const root=process.env.WRITHELM_PRIMARY_ROOT
  if(process.platform!=="linux"||ctx.app.version!=="2.2.5"||!root||!path.isAbsolute(root)||
    (yield* io(()=>fs.realpath(root)))!==root)nativeUnavailable()
  const workRoot=yield* io(()=>fs.realpath(ctx.location.directory))
  const bootstrap=new LinuxNativeCustody(path.join(root,"bootstrap"),[workRoot])
  yield* io(()=>bootstrap.initialize())
  const raw=yield* io(()=>bootstrap.read("primary-bootstrap","current"))
  if(raw===undefined)nativeUnavailable()
  const descriptor=exactJSON(raw,nativeUnavailable)
  if(descriptor.formatVersion?.value!=="1"||typeof descriptor.hostId!=="string"||!uuidPattern.test(descriptor.hostId))nativeUnavailable()
  yield* registerNativeContextDispatcher(ctx,{root,hostId:descriptor.hostId,workRoot})
}).pipe(Effect.orDie)})
