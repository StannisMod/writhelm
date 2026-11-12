// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { execFile } from "node:child_process"
import { nativeUnavailable, uuidPattern } from "./native-runtime-client.js"

export type GitObject = Readonly<{algorithm:"sha1"|"sha256";hex:string}>
export type GitSnapshot = Readonly<{repositoryId:string;commit:GitObject;tree:GitObject;dirtyContentDigest:null;retainedLocatorId:string}>
export type GitExecutable = Readonly<{path:string;sha256:string}>
export type GitDiffObservation = Readonly<{base:GitSnapshot;result:GitSnapshot;sha256:string;byteLength:number;bytes:Buffer}>
export type CandidateMergeInput = Readonly<{base:GitSnapshot;result:GitSnapshot}>

/** Explicit Linux Git/work-area witness with narrowly named one-shot ref/object primitives,
 * not a portable executable default, authority decision or writer fence. No checkout/reset/clean,
 * shell, filters, credentials or source-tree mutation here. */
export class LinuxGitWitness {
  private readonly executable:GitExecutable
  constructor(executable:GitExecutable){this.executable=Object.freeze({...executable})}
  private async run(directory:string,args:readonly string[],signal:AbortSignal,dates?:Readonly<{time:string}>):Promise<Buffer>{
    if(process.platform!=="linux"||!path.isAbsolute(this.executable.path)||!/^[0-9a-f]{64}$/.test(this.executable.sha256))nativeUnavailable()
    const file=await fs.open(this.executable.path,"r")
    try{
      const stat=await file.stat();if(!stat.isFile()||stat.size>128*1024*1024)nativeUnavailable()
      const hash=createHash("sha256"),buffer=Buffer.alloc(65536);let size=0
      for(;;){const next=await file.read(buffer,0,buffer.length,null);if(!next.bytesRead)break;size+=next.bytesRead;if(size>stat.size)nativeUnavailable();hash.update(buffer.subarray(0,next.bytesRead))}
      const after=await file.stat()
      if(size!==stat.size||stat.size!==after.size||stat.dev!==after.dev||stat.ino!==after.ino||hash.digest("hex")!==this.executable.sha256)nativeUnavailable()
    }finally{await file.close()}
    return new Promise((resolve,reject)=>{
      execFile(this.executable.path,["-c","core.hooksPath=/dev/null","-c","core.fsmonitor=false","-C",directory,...args],
         {encoding:"buffer",maxBuffer:8*1024*1024,timeout:10000,signal,env:{PATH:"/usr/bin:/bin",LC_ALL:"C",GIT_CONFIG_NOSYSTEM:"1",GIT_CONFIG_GLOBAL:"/dev/null",GIT_TERMINAL_PROMPT:"0",GIT_OPTIONAL_LOCKS:"0",
           ...(dates?{GIT_AUTHOR_DATE:dates.time,GIT_COMMITTER_DATE:dates.time}:{})}},
        (error,stdout)=>error?reject(new Error("qualified Git witness unavailable")):resolve(stdout))
    })
  }
  /** Low-level candidate object construction only. The caller supplies service-selected, observed
   * inputs and must retain original operation identity before dispatch. No acceptance, checkout,
   * worktree mutation or branch publication. Conflict rejects rather than selecting a resolution. */
  async buildCandidate(source:string,head:GitSnapshot,inputs:readonly CandidateMergeInput[],operationId:string,
    time:string,signal:AbortSignal):Promise<Readonly<{commit:GitObject;tree:GitObject}>>{
    if(!uuidPattern.test(operationId)||!/^\d{10} \+0000$/.test(time)||!inputs.length||inputs.length>256)nativeUnavailable()
    await this.qualifySource(source,head,signal)
    let commit=head.commit.hex,tree=head.tree.hex
    for(const input of inputs){
      if(input.base.repositoryId!==head.repositoryId||input.result.repositoryId!==head.repositoryId||
        input.base.commit.algorithm!==head.commit.algorithm||input.result.commit.algorithm!==head.commit.algorithm)nativeUnavailable()
      await this.qualifySource(source,input.base,signal)
      await this.verifyRetainedRef(source,input.result,signal)
      tree=(await this.run(source,["merge-tree","--write-tree",`--merge-base=${input.base.commit.hex}`,commit,input.result.commit.hex],signal)).toString("utf8").trim()
      if(!new RegExp(`^[0-9a-f]{${head.commit.algorithm==="sha1"?40:64}}$`).test(tree))nativeUnavailable()
      commit=(await this.run(source,["-c","user.name=Writhelm candidate","-c","user.email=candidate@writhelm.invalid",
        "commit-tree",tree,"-p",commit,"-m",`Candidate operation ${operationId}; result ${input.result.commit.hex}`],signal,{time})).toString("utf8").trim()
      if(!new RegExp(`^[0-9a-f]{${head.commit.algorithm==="sha1"?40:64}}$`).test(commit))nativeUnavailable()
    }
    return Object.freeze({commit:Object.freeze({algorithm:head.commit.algorithm,hex:commit}),tree:Object.freeze({algorithm:head.tree.algorithm,hex:tree})})
  }
  /** One-shot CAS on a dedicated candidate ref, never main. Caller must retain intent before this
   * effect and reconcile unknown outcomes without a new operation or a second dispatch. */
  async publishCandidateOnce(source:string,candidateId:string,expected:GitObject,next:GitObject,signal:AbortSignal):Promise<void>{
    if(!uuidPattern.test(candidateId)||expected.algorithm!==next.algorithm||
      ![expected.hex,next.hex].every(hex=>new RegExp(`^[0-9a-f]{${expected.algorithm==="sha1"?40:64}}$`).test(hex)))nativeUnavailable()
    await this.run(source,["update-ref",`refs/heads/writhelm-candidate-${candidateId}`,next.hex,expected.hex],signal)
  }
  /** Explicit Linux immutable-object check adapter, not an arbitrary process/project-test runner.
   * Reads one blob from the selected candidate without checkout, filters or worktree writes. */
  async checkCandidateFile(source:string,candidate:GitSnapshot,relative:string,expectedDigest:string,signal:AbortSignal){
    if(!relative||relative.startsWith("/")||relative.includes("\\")||relative.split("/").some(part=>["",".",".."].includes(part))||
      !/^[0-9a-f]{64}$/.test(expectedDigest))nativeUnavailable()
    await this.qualifySource(source,candidate,signal)
    const entry=(await this.run(source,["ls-tree","-z",candidate.commit.hex,"--",relative],signal)).toString("utf8")
    if(!new RegExp(`^100(?:644|755) blob [0-9a-f]+\\t`).test(entry)||entry.split("\0").length!==2||entry.slice(entry.indexOf("\t")+1,-1)!==relative)nativeUnavailable()
    const bytes=await this.run(source,["cat-file","blob",`${candidate.commit.hex}:${relative}`],signal)
    const actualDigest=createHash("sha256").update(bytes).digest("hex")
    return {outcome:actualDigest===expectedDigest?"passed" as const:"failed" as const,
      output:JSON.stringify({method:"immutable-git-file-sha256-v1",candidate:{commit:candidate.commit,tree:candidate.tree},relative,expectedDigest,actualDigest,byteLength:bytes.length})}
  }
  async qualifySource(source:string,snapshot:GitSnapshot,signal:AbortSignal):Promise<void>{
    if(await fs.realpath(source)!==source||!snapshot.commit||snapshot.dirtyContentDigest!==null||
      !["sha1","sha256"].includes(snapshot.commit.algorithm)||snapshot.tree.algorithm!==snapshot.commit.algorithm)nativeUnavailable()
    const length=snapshot.commit.algorithm==="sha1"?40:64
    if(![snapshot.commit.hex,snapshot.tree.hex].every(s=>new RegExp(`^[0-9a-f]{${length}}$`).test(s)))nativeUnavailable()
    const text=async(args:string[])=>(await this.run(source,args,signal)).toString("utf8").trim()
    if(await text(["rev-parse","--show-object-format"])!==snapshot.commit.algorithm||
      await text(["rev-parse","--verify",snapshot.commit.hex+"^{tree}"])!==snapshot.tree.hex)nativeUnavailable()
  }
  /** Checks one locator-specific ref and exact commit/tree. Observation alone is neither an
   * authorized retention operation nor a durability, assignment-binding or fencing guarantee. */
  async verifyRetainedRef(source:string,snapshot:GitSnapshot,signal:AbortSignal):Promise<void>{
    if(!uuidPattern.test(snapshot.retainedLocatorId))nativeUnavailable()
    await this.qualifySource(source,snapshot,signal)
    const ref=`refs/writhelm/results/${snapshot.retainedLocatorId}`
    const actual=(await this.run(source,["show-ref","--verify","--hash",ref],signal)).toString("utf8").trim()
    if(actual!==snapshot.commit.hex)nativeUnavailable()
    await this.qualifySource(source,snapshot,signal)
    const after=(await this.run(source,["show-ref","--verify","--hash",ref],signal)).toString("utf8").trim()
    if(after!==actual)nativeUnavailable()
  }
  /** Low-level, uninstalled one-shot effect. The caller MUST first durably record the exact
   * authorized operation and MUST NOT retry this effect after dispatch, including on error or
   * timeout. A read of the ref alone is not an operation receipt. Does not establish retention,
   * assignment authority, synchronization, process fencing or recovery qualification. */
  async pinRetainedRefOnce(source:string,snapshot:GitSnapshot,signal:AbortSignal):Promise<void>{
    if(!uuidPattern.test(snapshot.retainedLocatorId)||signal.aborted)nativeUnavailable()
    await this.qualifySource(source,snapshot,signal)
    const ref=`refs/writhelm/results/${snapshot.retainedLocatorId}`
    const zero="0".repeat(snapshot.commit.algorithm==="sha1"?40:64)
    await this.run(source,["update-ref",ref,snapshot.commit.hex,zero],signal)
    await this.verifyRetainedRef(source,snapshot,signal)
  }
  async verify(directory:string,source:string,snapshot:GitSnapshot,signal:AbortSignal):Promise<void>{
    if(await fs.realpath(directory)!==directory||await fs.realpath(source)!==source||snapshot.dirtyContentDigest!==null||!snapshot.commit)nativeUnavailable()
    const algorithm=snapshot.commit.algorithm,length=algorithm==="sha1"?40:64
    if(!["sha1","sha256"].includes(algorithm)||snapshot.tree.algorithm!==algorithm||
      ![snapshot.commit.hex,snapshot.tree.hex].every(s=>new RegExp(`^[0-9a-f]{${length}}$`).test(s)))nativeUnavailable()
    const text=async(root:string,args:string[])=>(await this.run(root,args,signal)).toString("utf8").trim()
    if(await text(directory,["rev-parse","--show-object-format"])!==algorithm||await text(directory,["rev-parse","--show-toplevel"])!==directory||
      await text(directory,["rev-parse","--verify","HEAD^{commit}"])!==snapshot.commit.hex||
      await text(directory,["rev-parse","--verify","HEAD^{tree}"])!==snapshot.tree.hex)nativeUnavailable()
    const common=async(root:string)=>fs.realpath(path.resolve(root,await text(root,["rev-parse","--git-common-dir"])))
    if(await common(directory)!==await common(source))nativeUnavailable()
    const entries=new TextDecoder("utf-8",{fatal:true}).decode(await this.run(directory,["ls-tree","-rz","--full-tree","HEAD"],signal)).split("\0").filter(Boolean)
    if(entries.length>8192)nativeUnavailable()
    const expected=new Map<string,{mode:string;oid:string}>()
    for(const row of entries){
      const match=/^(100644|100755|120000) blob ([0-9a-f]+)\t(.+)$/.exec(row)
      if(!match||!match[3]!.isWellFormed()||path.isAbsolute(match[3]!)||match[3]!.split("/").some(x=>["",".","..",".git"].includes(x)))nativeUnavailable()
      expected.set(match[3]!,{mode:match[1]!,oid:match[2]!})
    }
    let bytes=0,count=0
    const walk=async(relative:string):Promise<void>=>{
      for(const entry of await fs.readdir(path.join(directory,relative),{withFileTypes:true})){
        if(!relative&&entry.name===".git")continue
        if(++count>16384)nativeUnavailable()
        const name=relative?relative+"/"+entry.name:entry.name,file=path.join(directory,name)
        if(entry.isDirectory()){await walk(name);continue}
        const item=expected.get(name);if(!item)nativeUnavailable()
        const before=await fs.lstat(file);if(before.size>16*1024*1024)nativeUnavailable()
        let digest:string,size=0
        if(item.mode==="120000"){
          if(!before.isSymbolicLink())nativeUnavailable()
          const data=await fs.readlink(file,{encoding:"buffer"});size=data.length
          digest=createHash(algorithm).update(Buffer.from(`blob ${size}\0`)).update(data).digest("hex")
        }else{
          if(!before.isFile()||Boolean(before.mode&0o111)!==(item.mode==="100755"))nativeUnavailable()
          const handle=await fs.open(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW|fs.constants.O_NONBLOCK)
          try{
            const opened=await handle.stat();if(opened.dev!==before.dev||opened.ino!==before.ino)nativeUnavailable()
            const hash=createHash(algorithm).update(Buffer.from(`blob ${before.size}\0`)),buffer=Buffer.alloc(65536)
            for(;;){
              if(signal.aborted)nativeUnavailable()
              const next=await handle.read(buffer,0,buffer.length,null);if(!next.bytesRead)break
              size+=next.bytesRead;if(size>before.size)nativeUnavailable();hash.update(buffer.subarray(0,next.bytesRead))
            }
            digest=hash.digest("hex")
          }finally{await handle.close()}
        }
        bytes+=size;if(bytes>64*1024*1024)nativeUnavailable()
        const after=await fs.lstat(file)
        if(before.dev!==after.dev||before.ino!==after.ino||before.size!==after.size||before.mtimeMs!==after.mtimeMs||
          size!==before.size||digest!==item.oid)nativeUnavailable()
        expected.delete(name)
      }
    }
    await walk("");if(expected.size)nativeUnavailable()
    if(await text(directory,["rev-parse","--verify","HEAD^{commit}"])!==snapshot.commit.hex||
      await text(directory,["rev-parse","--verify","HEAD^{tree}"])!==snapshot.tree.hex)nativeUnavailable()
  }
  /** Linux read-only observation of a clean worktree and the exact Git-generated binary diff.
   * This is not writer fencing, a service fact, result acceptance, or proof of semantic scope. */
  async observeDiff(directory:string,source:string,base:GitSnapshot,result:GitSnapshot,signal:AbortSignal):Promise<GitDiffObservation>{
    const capture=(s:GitSnapshot):GitSnapshot=>Object.freeze({...s,commit:Object.freeze({...s.commit}),tree:Object.freeze({...s.tree})})
    base=capture(base);result=capture(result)
    if(base.repositoryId!==result.repositoryId||base.commit.algorithm!==result.commit.algorithm||
      base.dirtyContentDigest!==null||result.dirtyContentDigest!==null)nativeUnavailable()
    await this.qualifySource(source,base,signal)
    await this.qualifySource(source,result,signal)
    await this.verify(directory,source,result,signal)
    await this.run(directory,["merge-base","--is-ancestor",base.commit.hex,result.commit.hex],signal)
    const bytes=await this.run(directory,["diff","--no-ext-diff","--no-textconv","--no-color","--binary","--full-index",
      base.commit.hex,result.commit.hex,"--"],signal)
    await this.verify(directory,source,result,signal)
    const captured=Buffer.from(bytes),sha256=createHash("sha256").update(captured).digest("hex")
    return Object.freeze({base,result,sha256,byteLength:captured.length,get bytes(){return Buffer.from(captured)}})
  }
}
