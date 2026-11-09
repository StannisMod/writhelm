// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"

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
