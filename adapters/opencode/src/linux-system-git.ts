// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import path from "node:path"
import {execFile} from "node:child_process"

export class GitCompatibilityError extends Error{
  constructor(readonly code:"GIT_UNAVAILABLE"|"GIT_INCOMPATIBLE"){super(code)}
}
type Observation=Readonly<{version:string;profile:"system-git-worktree-v1"}>
const cache=new Map<string,{stamp:string;observation:Observation}>()
const unavailable=():never=>{throw new GitCompatibilityError("GIT_UNAVAILABLE")}
const incompatible=():never=>{throw new GitCompatibilityError("GIT_INCOMPATIBLE")}
async function stamp(executable:string):Promise<string>{
  if(process.platform!=="linux"||!path.isAbsolute(executable))unavailable()
  try{
    const s=await fs.stat(executable,{bigint:true})
    const resolved=await fs.realpath(executable)
    if(!s.isFile()||s.mode&0o022n)unavailable()
    await fs.access(executable,fs.constants.X_OK)
    return [resolved,s.dev,s.ino,s.size,s.mtimeNs,s.ctimeNs,s.mode].join(":")
  }catch{return unavailable()}
}
const probe=(executable:string,args:string[],signal:AbortSignal,help=false)=>new Promise<string>((resolve,reject)=>{
  execFile(executable,args,{cwd:path.dirname(executable),encoding:"utf8",maxBuffer:262144,timeout:3000,signal,
    env:{PATH:"/usr/bin:/bin",LC_ALL:"C",GIT_CONFIG_NOSYSTEM:"1",GIT_CONFIG_GLOBAL:"/dev/null",GIT_TERMINAL_PROMPT:"0"}},
    (error,stdout,stderr)=>{
      if(error&&(!help||!["129","0"].includes(String((error as NodeJS.ErrnoException).code)))){
        reject(new GitCompatibilityError(typeof (error as {code?:unknown}).code==="number"?
          "GIT_INCOMPATIBLE":"GIT_UNAVAILABLE"));return
      }
      resolve(stdout+stderr)
    })
})
/** Cheap, disposable capability admission, not a binary pin or execution authorization.
 * File identity invalidates the cache after an OS update; no approved digest is involved.
 * Individual Git operations still verify exact results and retain their original-effect guards.
 */
export async function observeSystemGit(executable:string,signal:AbortSignal):Promise<Observation>{
  if(signal.aborted)unavailable()
  const before=await stamp(executable),cached=cache.get(executable)
  if(cached&&cached.stamp===before)return cached.observation
  const version=(await probe(executable,["--version"],signal)).trim()
  if(!/^git version \d+\.\d+(?:\.\d+)?[A-Za-z0-9.+ _()-]{0,128}$/.test(version))incompatible()
  const commands=new Set((await probe(executable,["--list-cmds=main"],signal)).trim().split(/\s+/))
  if(!["init","rev-parse","hash-object","cat-file","mktree","ls-tree","commit-tree","update-ref",
    "worktree","merge-tree","merge-base","diff","show-ref","for-each-ref"].every(c=>commands.has(c)))incompatible()
  const help=(await probe(executable,["merge-tree","-h"],signal,true)).replaceAll("--[no-]","--")
  if(!["--write-tree","--merge-base"].every(option=>help.includes(option)))incompatible()
  if(await stamp(executable)!==before)unavailable()
  const observation=Object.freeze({version,profile:"system-git-worktree-v1" as const})
  if(cache.size>=32)cache.clear()
  cache.set(executable,{stamp:before!,observation});return observation
}
