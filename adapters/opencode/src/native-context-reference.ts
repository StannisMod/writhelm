// SPDX-License-Identifier: AGPL-3.0-only
import type { SessionCompaction, SessionGenerate, SessionModelRequest } from "@opencode/plugin/promise/session"
import { nativeUnavailable } from "./native-runtime-client.js"

export type NativeContextAdmission = Readonly<{
  compaction(event: SessionCompaction): Promise<void>
  generate(event: SessionGenerate): Promise<void>
  request(event: SessionModelRequest): Promise<void>
}>
const references=new Map<string,{hostId:string;pid:number;admission:NativeContextAdmission}>()
const key=(root:string,workRoot:string)=>JSON.stringify([root,workRoot])

/** Lifecycle routing only. The recipient must check actual current authority and its own extraction
 * lease; installing a companion or finding this reference never grants generation permission. */
export function nativeContextAdmission(root:string,hostId:string,workRoot:string):NativeContextAdmission {
  const entry=references.get(key(root,workRoot))
  if(!entry||entry.hostId!==hostId||entry.pid!==process.pid)nativeUnavailable()
  return entry.admission
}
export function retainNativeContextAdmission(root:string,hostId:string,workRoot:string,admission:NativeContextAdmission):()=>void {
  const id=key(root,workRoot)
  if(references.has(id)||references.size>=256)nativeUnavailable()
  const entry={hostId,pid:process.pid,admission};references.set(id,entry)
  return ()=>{if(references.get(id)===entry)references.delete(id)}
}
