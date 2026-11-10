// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import * as fs from "node:fs/promises"
import { constants } from "node:fs"
import nodePath from "node:path"
import type { LinuxNativeCustody } from "./native-runtime-custody.js"

export type ExactTextCorrection=Readonly<{
  path:string;mode:"100644"|"100755";beforeDigest:string;beforeText:string;patch:string
}>
export type PreparedTextCorrection=Readonly<{
  path:string;mode:"100644"|"100755";beforeDigest:string;afterDigest:string;afterText:string
}>
function refused():never { throw new Error("exact text correction refused") }
const hash=(text:string)=>createHash("sha256").update(text,"utf8").digest("hex")
const text=(value:string,maximum:number)=>{
  if(typeof value!=="string"||!value.isWellFormed()||value.includes("\0")||Buffer.byteLength(value)>maximum)refused()
}
const integer=(value:string|undefined,fallback=1)=>{
  if(value===undefined)return fallback
  if(!/^(0|[1-9][0-9]{0,6})$/.test(value))refused()
  const n=Number(value);if(n>131072)refused();return n
}
const boundedLines=(value:string,maximum:number)=>{
  let count=0,at=0
  for(;;){
    const next=value.indexOf("\n",at)
    if(next<0){if(at<value.length&&++count>maximum)refused();return}
    if(++count>maximum)refused()
    at=next+1
  }
}
type Change={kind:" "|"+"|"-";body:string;noNewline:boolean}

/** Pure preparation of ONE existing regular text file, never filesystem application or authority.
 * Restricted portable ASCII path identifiers; no create/delete/rename/mode/binary changes. Exact
 * positions and preimage bytes only, no fuzzy matching, line-ending normalization or commands.
 * A native caller still needs canonical task/base/authorship/claims, qualified path/file identity,
 * retained original-effect recovery and independent verification before exposing any operation. */
export function prepareExactTextCorrection(input:ExactTextCorrection):PreparedTextCorrection {
  const {path,mode,beforeDigest,beforeText,patch}=input
  if(typeof path!=="string"||path.length>1024||!/^[A-Za-z0-9._/-]+$/.test(path)||path.startsWith("/")||
    path.split("/").some(part=>!part||part==="."||part===".."||part.endsWith(".")||part.toLowerCase()===".git"||
      /^(con|prn|aux|nul|com[0-9]|lpt[0-9])(?:\.|$)/i.test(part))||!["100644","100755"].includes(mode)||
    typeof beforeDigest!=="string"||!/^[0-9a-f]{64}$/.test(beforeDigest))refused()
  text(beforeText,8*1024*1024);text(patch,1024*1024)
  if(hash(beforeText)!==beforeDigest||!patch.endsWith("\n"))refused()
  boundedLines(beforeText,131072);boundedLines(patch,262912)
  const original=beforeText.match(/[^\n]*\n|[^\n]+$/g)??[]
  if(original.length>131072)refused()
  const lines=patch.slice(0,-1).split("\n")
  let at=0,cursor=0,hunks=0,operations=0,outputBytes=0
  const output:string[]=[]
  const append=(body:string)=>{outputBytes+=Buffer.byteLength(body);if(outputBytes>8*1024*1024||output.length>=131072)refused();output.push(body)}
  if(lines[at]===`diff --git a/${path} b/${path}`){
    at++
    if(lines[at]?.startsWith("index ")){
      const index=/^index ([0-9a-f]{40}|[0-9a-f]{64})\.\.([0-9a-f]{40}|[0-9a-f]{64}) (100644|100755)$/.exec(lines[at]!)
      if(!index||index[1]!.length!==index[2]!.length||index[3]!==mode)refused()
      at++ // Git header OIDs are not the preimage witness; beforeDigest checks actual supplied bytes.
    }
  }
  if(lines[at++]!==`--- a/${path}`||lines[at++]!==`+++ b/${path}`)refused()
  while(at<lines.length){
    if(++hunks>256)refused()
    const header=/^@@ -(0|[1-9][0-9]*)(?:,(0|[1-9][0-9]*))? \+(0|[1-9][0-9]*)(?:,(0|[1-9][0-9]*))? @@(?: [^\r\n]*)?$/.exec(lines[at++]!)
    if(!header)refused()
    const oldStart=integer(header[1]),oldCount=integer(header[2]),newStart=integer(header[3]),newCount=integer(header[4])
    if(!oldCount&&!newCount||oldCount>0&&oldStart===0||newCount>0&&newStart===0)refused()
    const target=oldCount===0?oldStart:oldStart-1,newTarget=newCount===0?newStart:newStart-1
    if(target<cursor||target>original.length)refused()
    for(const line of original.slice(cursor,target))append(line)
    cursor=target
    if(output.length!==newTarget)refused()
    const changes:Change[]=[]
    while(at<lines.length&&!lines[at]!.startsWith("@@ ")){
      const line=lines[at++]!
      if(line==="\\ No newline at end of file"){
        const previous=changes.at(-1);if(!previous||previous.noNewline)refused()
        previous.body=previous.body.slice(0,-1);if(!previous.body)refused();previous.noNewline=true;continue
      }
      const kind=line[0]
      if(![" ","+","-"].includes(kind??"")||++operations>131072)refused()
      changes.push({kind:kind as Change["kind"],body:line.slice(1)+"\n",noNewline:false})
    }
    if(changes.filter(change=>change.kind!=="+").length!==oldCount||changes.filter(change=>change.kind!=="-").length!==newCount)refused()
    for(const change of changes){
      if(change.kind!=="+"){if(original[cursor]!==change.body)refused();cursor++}
      if(change.kind!=="-")append(change.body)
    }
  }
  if(!hunks)refused()
  for(const line of original.slice(cursor))append(line)
  // A no-newline marker must describe a final file line, not join two logical output lines.
  if(output.slice(0,-1).some(line=>!line.endsWith("\n")))refused()
  const afterText=output.join("")
  if(afterText===beforeText)refused()
  return Object.freeze({path,mode,beforeDigest,afterDigest:hash(afterText),afterText})
}

type FileIdentity=Readonly<{device:string;inode:string;mode:string;links:string;size:string;modified:string;changed:string}>
export type ExactFileWitness=Readonly<{
  root:string;path:string;directories:readonly FileIdentity[];file:FileIdentity;digest:string
}>
export type RetainedTextEffect=Readonly<{
  operationId:string;before:ExactFileWitness;correction:PreparedTextCorrection;beforeText:string;patch:string;patchDigest:string
}>
export type ExactTextEffectIntent=Readonly<{
  root:string;operationId:string;correction:PreparedTextCorrection;patchDigest:string
}>
/** Retention may have committed even when its reply was lost. Preserve this ORIGINAL operation;
 * neither this error nor a read-only observation authorizes another attempt or claim release. */
export class PendingTextEffect extends Error {
  constructor(readonly effect:RetainedTextEffect,cause:unknown){
    super("exact text effect remains pending reconciliation",{cause})
    this.name="PendingTextEffect"
  }
}
/** Retain the original effect before issuing a write. A manifest is published only after all
 * immutable chunks are retained; pre-existing chunks without that manifest are an unresolved
 * partial retention, not permission to retry. The result is NOT operation admission or proof
 * of writer termination. Existing manifests never authorize a second dispatch. */
export async function retainExactTextEffect(custody:LinuxNativeCustody,effect:RetainedTextEffect):Promise<"new"|"existing"> {
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(effect.operationId))refused()
  const prepared=prepareExactTextCorrection({path:effect.correction.path,mode:effect.correction.mode,
    beforeText:effect.beforeText,beforeDigest:effect.correction.beforeDigest,patch:effect.patch})
  if(effect.before.path!==prepared.path||effect.before.digest!==prepared.beforeDigest||effect.patchDigest!==hash(effect.patch)||
    effect.correction.afterDigest!==prepared.afterDigest||effect.correction.afterText!==prepared.afterText)refused()
  const raw=JSON.stringify(effect)
  const bytes=Buffer.from(raw,"utf8")
  if(bytes.length>20*1024*1024)refused()
  const chunks:string[]=[]
  for(let offset=0;offset<bytes.length;offset+=512*1024)chunks.push(bytes.subarray(offset,offset+512*1024).toString("base64"))
  if(!chunks.length||chunks.length>40)refused()
  const manifest=JSON.stringify({version:1,operationId:effect.operationId,byteLength:bytes.length,digest:hash(raw),
    chunks:chunks.map(hash)})
  const existing=await custody.read("text-effect",effect.operationId)
  if(existing!==undefined){if(existing!==manifest)refused();return "existing"}
  for(let i=0;i<chunks.length;i++){
    // An existing chunk could belong to an interrupted retention or a concurrent caller.
    // Never fill the rest and then claim a fresh authorization to dispatch.
    if(!await custody.retain("text-effect-part",`${effect.operationId}:${i}`,chunks[i]!))refused()
  }
  if(!await custody.retain("text-effect",effect.operationId,manifest))refused()
  return "new"
}
/** Read the exact retained original bytes, not an authority grant or proof of no live writer.
 * Unparseable/incomplete manifests and missing/changed chunks are refused, never treated as absence.
 * The eventual controller must validate the decoded closed effect and current authority separately. */
export async function readRetainedExactTextEffect(custody:LinuxNativeCustody,operationId:string):Promise<string|undefined> {
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(operationId))refused()
  const raw=await custody.read("text-effect",operationId)
  if(raw===undefined)return undefined // not proof that no partial chunks/effect exist
  let manifest:unknown
  try{manifest=JSON.parse(raw)}catch{refused()}
  if(!manifest||typeof manifest!=="object"||Array.isArray(manifest))refused()
  const m=manifest as Record<string,unknown>
  if(Object.keys(m).join(",")!=="version,operationId,byteLength,digest,chunks"||m.version!==1||m.operationId!==operationId||
    typeof m.byteLength!=="number"||!Number.isSafeInteger(m.byteLength)||m.byteLength<=0||m.byteLength>20*1024*1024||
    typeof m.digest!=="string"||!/^[0-9a-f]{64}$/.test(m.digest)||!Array.isArray(m.chunks)||m.chunks.length<1||m.chunks.length>40||
    m.chunks.some((x:unknown)=>typeof x!=="string"||!/^[0-9a-f]{64}$/.test(x))||JSON.stringify(m)!==raw)refused()
  const parts:Buffer[]=[]
  for(let i=0;i<m.chunks.length;i++){
    const encoded=await custody.read("text-effect-part",`${operationId}:${i}`)
    if(encoded===undefined||hash(encoded)!==m.chunks[i])refused()
    const part=Buffer.from(encoded,"base64")
    if(part.toString("base64")!==encoded||part.length===0||part.length>512*1024||i<m.chunks.length-1&&part.length!==512*1024)refused()
    parts.push(part)
  }
  const bytes=Buffer.concat(parts)
  if(bytes.length!==m.byteLength)refused()
  const text=bytes.toString("utf8")
  if(!Buffer.from(text,"utf8").equals(bytes)||hash(text)!==m.digest)refused()
  return text
}
const identity=(s:import("node:fs").BigIntStats):FileIdentity=>Object.freeze({
  device:String(s.dev),inode:String(s.ino),mode:String(s.mode),links:String(s.nlink),size:String(s.size),
  modified:String(s.mtimeNs),changed:String(s.ctimeNs),
})
const sameIdentity=(a:FileIdentity,b:FileIdentity)=>Object.keys(a).every(k=>a[k as keyof FileIdentity]===b[k as keyof FileIdentity])
// Directory content timestamps change when unrelated files are created; only directory object/mode
// identity is relevant. This is cooperative path checking, NOT race-proof openat/OS confinement.
const sameDirectory=(a:FileIdentity,b:FileIdentity)=>a.device===b.device&&a.inode===b.inode&&a.mode===b.mode
async function directories(root:string,relative:string):Promise<readonly FileIdentity[]> {
  if(process.platform!=="linux"||!nodePath.isAbsolute(root)||nodePath.normalize(root)!==root||await fs.realpath(root)!==root)refused()
  const found:FileIdentity[]=[]
  let current=root
  for(const part of ["",...relative.split("/").slice(0,-1)]){
    if(part)current=nodePath.join(current,part)
    const s=await fs.lstat(current,{bigint:true})
    if(!s.isDirectory()||s.isSymbolicLink())refused()
    found.push(identity(s))
  }
  return Object.freeze(found)
}
async function readBounded(handle:fs.FileHandle):Promise<string> {
  const buffer=Buffer.alloc(8*1024*1024+1)
  let used=0
  while(used<buffer.length){
    const {bytesRead}=await handle.read(buffer,used,buffer.length-used,used)
    if(!bytesRead)break
    used+=bytesRead
  }
  if(used>8*1024*1024)refused()
  const bytes=buffer.subarray(0,used),value=bytes.toString("utf8")
  if(!Buffer.from(value,"utf8").equals(bytes))refused()
  text(value,8*1024*1024)
  return value
}
async function captureFile(root:string,input:ExactTextCorrection,write:boolean){
  // Validate syntax and exact supplied patch BEFORE resolving any supplied relative path.
  const correction=prepareExactTextCorrection(input)
  const parents=await directories(root,input.path)
  const expectedMode=input.mode==="100755"?0o755n:0o644n
  const leaf=await fs.lstat(nodePath.join(root,input.path),{bigint:true})
  if(!leaf.isFile()||leaf.isSymbolicLink()||leaf.nlink!==1n||(leaf.mode&0o7777n)!==expectedMode||leaf.size>8n*1024n*1024n)refused()
  const handle=await fs.open(nodePath.join(root,input.path),
    (write?constants.O_RDWR:constants.O_RDONLY)|constants.O_NOFOLLOW|constants.O_NONBLOCK)
  try{
    const s=await handle.stat({bigint:true})
    if(!s.isFile()||s.nlink!==1n||(s.mode&0o7777n)!==expectedMode||s.size>8n*1024n*1024n||!sameIdentity(identity(leaf),identity(s)))refused()
    const beforeText=await readBounded(handle),after=await handle.stat({bigint:true})
    if(!sameIdentity(identity(s),identity(after))||beforeText!==input.beforeText||hash(beforeText)!==input.beforeDigest)refused()
    const pathname=await fs.lstat(nodePath.join(root,input.path),{bigint:true})
    const checked=await directories(root,input.path)
    if(!sameIdentity(identity(after),identity(pathname))||!parents.every((p,i)=>checked[i]&&sameDirectory(p,checked[i]!)))refused()
    const witness:ExactFileWitness=Object.freeze({root,path:input.path,directories:parents,file:identity(after),digest:hash(beforeText)})
    return {handle,witness,correction,beforeText}
  }catch(error){await handle.close();throw error}
}

/** Non-exposed Linux native primitive. Ports belong to the trusted controller integration, NOT
 * caller-provided authorization flags. retain must durably retain the original effect/witnesses;
 * admission must recheck actual current canonical authority/base/manifest/claims. Neither port is
 * implemented here. No service command, check runner, result publication or applying tool follows.
 * E-COOP-1 only: cooperative exclusive workspace required, no hostile path-race protection claimed. */
export async function applyExactTextFile(root:string,input:ExactTextCorrection,operationId:string,ports:Readonly<{
  admit:(intent:ExactTextEffectIntent)=>Promise<void>;retain:(effect:RetainedTextEffect)=>Promise<void>
}>):Promise<RetainedTextEffect> {
  if(process.platform!=="linux"||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(operationId))refused()
  input=Object.freeze({path:input.path,mode:input.mode,beforeDigest:input.beforeDigest,beforeText:input.beforeText,patch:input.patch})
  const intent:ExactTextEffectIntent=Object.freeze({root,operationId,correction:prepareExactTextCorrection(input),patchDigest:hash(input.patch)})
  await ports.admit(intent)
  const captured=await captureFile(root,input,true)
  const effect:RetainedTextEffect=Object.freeze({operationId,before:captured.witness,correction:captured.correction,
    beforeText:captured.beforeText,patch:input.patch,patchDigest:hash(input.patch)})
  try{
    await ports.retain(effect) // no filesystem mutation before retained original identity/witnesses
    await ports.admit(intent) // retention can race revocation; recheck exact intent before first write
    const rechecked=await captureFile(root,input,false)
    try{
      if(!sameIdentity(captured.witness.file,rechecked.witness.file)||
        !captured.witness.directories.every((p,i)=>rechecked.witness.directories[i]&&sameDirectory(p,rechecked.witness.directories[i]!)))refused()
    }finally{await rechecked.handle.close()}
    const bytes=Buffer.from(effect.correction.afterText,"utf8")
    let offset=0
    // Deliberately no truncate-on-open, rename, retry, cleanup or rollback. Partial/error outcome
    // keeps the retained original effect pending; the caller must not infer writer termination.
    while(offset<bytes.length){
      const {bytesWritten}=await captured.handle.write(bytes,offset,bytes.length-offset,offset)
      if(!bytesWritten)throw new Error("exact text effect outcome unknown")
      offset+=bytesWritten
    }
    await captured.handle.truncate(bytes.length)
    await captured.handle.sync()
    const s=await captured.handle.stat({bigint:true})
    const resultingDigest=hash(await readBounded(captured.handle))
    const end=await captured.handle.stat({bigint:true})
    const pathname=await fs.lstat(nodePath.join(root,input.path),{bigint:true})
    const checked=await directories(root,input.path)
    if(s.dev.toString()!==effect.before.file.device||s.ino.toString()!==effect.before.file.inode||
      s.nlink!==1n||s.mode.toString()!==effect.before.file.mode||!sameIdentity(identity(s),identity(end))||!sameIdentity(identity(end),identity(pathname))||
      !effect.before.directories.every((p,i)=>checked[i]&&sameDirectory(p,checked[i]!))||
      resultingDigest!==effect.correction.afterDigest)throw new Error("exact text effect outcome unknown")
    return effect // observed bytes, NOT a canonical completion/result or release of claims
  }catch(error){throw new PendingTextEffect(effect,error)}
  finally{
    try{await captured.handle.close()}catch(error){throw new PendingTextEffect(effect,error)}
  }
}

/** Read-only reconciliation of a retained effect. 'after' means bytes observed, never writer
 * shutdown, canonical completion, check success, publication or permission to release/retry.
 * The original pending operation and its claims remain owned by the controller protocol. */
export async function observeExactTextEffect(effect:RetainedTextEffect):Promise<"before"|"after"|"unknown"> {
  try{
    const c=effect.correction
    const prepared=prepareExactTextCorrection({path:c.path,mode:c.mode,beforeDigest:c.beforeDigest,beforeText:effect.beforeText,patch:effect.patch})
    if(hash(effect.patch)!==effect.patchDigest||effect.before.path!==c.path||effect.before.digest!==c.beforeDigest||
      prepared.afterDigest!==c.afterDigest||prepared.afterText!==c.afterText)return "unknown"
    const parents=await directories(effect.before.root,c.path)
    if(parents.length!==effect.before.directories.length||!parents.every((p,i)=>sameDirectory(p,effect.before.directories[i]!)))return "unknown"
    const leaf=await fs.lstat(nodePath.join(effect.before.root,c.path),{bigint:true})
    if(!leaf.isFile()||leaf.isSymbolicLink()||leaf.nlink!==1n||leaf.size>8n*1024n*1024n)return "unknown"
    const handle=await fs.open(nodePath.join(effect.before.root,c.path),constants.O_RDONLY|constants.O_NOFOLLOW|constants.O_NONBLOCK)
    try{
      const s=await handle.stat({bigint:true})
      if(!s.isFile()||s.nlink!==1n||String(s.dev)!==effect.before.file.device||String(s.ino)!==effect.before.file.inode||String(s.mode)!==effect.before.file.mode||!sameIdentity(identity(leaf),identity(s)))return "unknown"
      const content=await readBounded(handle),end=await handle.stat({bigint:true})
      const pathname=await fs.lstat(nodePath.join(effect.before.root,c.path),{bigint:true})
      const checked=await directories(effect.before.root,c.path)
      if(!sameIdentity(identity(s),identity(end))||!sameIdentity(identity(end),identity(pathname))||
        checked.length!==parents.length||!checked.every((p,i)=>sameDirectory(p,parents[i]!)))return "unknown"
      if(hash(content)===c.afterDigest)return "after"
      // Reverted bytes with changed metadata are not proof that no original effect happened.
      if(hash(content)===c.beforeDigest&&sameIdentity(identity(end),effect.before.file))return "before"
      return "unknown"
    }finally{await handle.close()}
  }catch{return "unknown"}
}
