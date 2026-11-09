// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { execFile } from "node:child_process"
import { nativeUnavailable } from "./native-runtime-client.js"

export type GitObject = Readonly<{algorithm:"sha1"|"sha256";hex:string}>
export type GitSnapshot = Readonly<{repositoryId:string;commit:GitObject;tree:GitObject;dirtyContentDigest:null;retainedLocatorId:string}>
export type GitExecutable = Readonly<{path:string;sha256:string}>

/** Explicit Linux READ-only Git/work-area witness, not a portable executable default or a writer
 * fence. No checkout/reset/clean, shell, filters, credentials or source-tree mutation here. */
export class LinuxGitWitness {
  private readonly executable:GitExecutable
  constructor(executable:GitExecutable){this.executable=Object.freeze({...executable})}
  private async run(directory:string,args:readonly string[],signal:AbortSignal):Promise<Buffer>{
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
        {encoding:"buffer",maxBuffer:8*1024*1024,timeout:10000,signal,env:{PATH:"/usr/bin:/bin",LC_ALL:"C",GIT_CONFIG_NOSYSTEM:"1",GIT_CONFIG_GLOBAL:"/dev/null",GIT_TERMINAL_PROMPT:"0",GIT_OPTIONAL_LOCKS:"0"}},
        (error,stdout)=>error?reject(new Error("qualified Git witness unavailable")):resolve(stdout))
    })
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
}
