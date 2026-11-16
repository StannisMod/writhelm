// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { setTimeout as wait } from "node:timers/promises"
import type { OpenCodeClient } from "@opencode/client"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { LinuxGitWitness, type GitSnapshot } from "./linux-git-witness.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import type { CandidateTestRequest, CandidateTestOutcome } from "./native-result-retention.js"

export type ProjectCheckProfile=Readonly<{formatVersion:1;platform:"linux";nativeProjectId:string;sourceRoot:string;worktreeParent:string;
  envExecutable:Readonly<{path:string;sha256:string}>;checks:readonly Readonly<{id:string;executable:Readonly<{path:string;sha256:string}>;
    args:readonly string[];environment:Readonly<Record<string,string>>;timeoutMillis:number;outputBytes:number}>[]}>
const hash=(raw:string|Buffer)=>createHash("sha256").update(raw).digest("hex")
const quote=(value:string)=>"'"+value.replaceAll("'","'\\''")+"'"
export function decodeProjectCheckProfile(raw:string):ProjectCheckProfile {
  const value=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","platform","nativeProjectId","sourceRoot","worktreeParent","envExecutable","checks"])
  // lossless-json invokes onDuplicateKey only for unequal values. Protected execution policy
  // rejects equal duplicates too. JSON syntax/escapes have already been validated above.
  const objects:Array<Set<string>|null>=[]
  for(let i=0;i<raw.length;i++){
    if(raw[i]==='"'){
      const start=i++
      while(raw[i]!=='"'){if(raw[i]==="\\")i++;i++}
      let next=i+1;while(/\s/.test(raw[next]??""))next++
      if(raw[next]===":"){
        const keys=objects[objects.length-1],key=JSON.parse(raw.slice(start,i+1)) as string
        if(!keys||keys.has(key))nativeUnavailable()
        keys.add(key)
      }
    }else if(raw[i]==="{")objects.push(new Set())
    else if(raw[i]==="[")objects.push(null)
    else if(raw[i]==="}"||raw[i]==="]")objects.pop()
  }
  closedObject(value.envExecutable,["path","sha256"])
  if(!Array.isArray(value.checks))nativeUnavailable()
  for(const item of value.checks){
    const check=closedObject(item,["id","executable","args","environment","timeoutMillis","outputBytes"])
    closedObject(check.executable,["path","sha256"])
    if(typeof check.id!=="string"||!check.id.length||check.id.length>128||!Array.isArray(check.args)||check.args.some(arg=>typeof arg!=="string")||
      !check.environment||typeof check.environment!=="object"||Array.isArray(check.environment)||Object.values(check.environment).some(v=>typeof v!=="string"))nativeUnavailable()
  }
  return JSON.parse(raw) as ProjectCheckProfile
}
/** Registrar selects policy/path binding; only the actual SDK project identity is supplied here. */
export function bindProjectCheckTemplate(raw:string,nativeProjectId:string):ProjectCheckProfile {
  closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","platform","sourceRoot","worktreeParent","envExecutable","checks"])
  if(typeof nativeProjectId!=="string"||!nativeProjectId.length)nativeUnavailable()
  const offset=raw.indexOf("{")
  return decodeProjectCheckProfile(raw.slice(0,offset+1)+`"nativeProjectId":${JSON.stringify(nativeProjectId)},`+raw.slice(offset+1))
}

/** Cooperative Linux/native-shell profile, not an OS sandbox. Executes only trusted profile argv,
 * never command prose/argv supplied by a worker. A dispatch intent without an exact native job
 * observation remains unknown; a new instance must not dispatch another job. Work areas are never
 * reused for another run, and timeout/cancellation do not claim descendant/writer termination. */
export class NativeProjectChecks {
  private readonly profile:ProjectCheckProfile
  constructor(private readonly native:Pick<OpenCodeClient,"project"|"worktree"|"shell">,private readonly custody:LinuxNativeCustody,
    private readonly git:LinuxGitWitness,profile:ProjectCheckProfile,
    private readonly authorize:(request:CandidateTestRequest,signal:AbortSignal)=>Promise<boolean>){
    this.profile=decodeProjectCheckProfile(JSON.stringify(profile))
  }
  private async pin(pin:Readonly<{path:string;sha256:string}>){
    if(!path.isAbsolute(pin.path)||!/^[a-f0-9]{64}$/.test(pin.sha256)||await fs.realpath(pin.path)!==pin.path)nativeUnavailable()
    const stat=await fs.lstat(pin.path)
    if(!stat.isFile()||stat.size>128*1024*1024||stat.mode&0o022||hash(await fs.readFile(pin.path))!==pin.sha256)nativeUnavailable()
  }
  /** Called even for cached test receipts. A changed/missing execution profile cannot inherit
   * a pass recorded under another invocation or bypass its original runner custody. */
  async current(request:CandidateTestRequest,base:GitSnapshot):Promise<boolean>{
    if(request.command.length!==2||request.command[0]!=="project-check-v1"||!this.profile.checks.some(check=>check.id===request.command[1]))return false
    const original=await this.custody.read("project-check-original",request.runId)
    if(original!==undefined)return original===JSON.stringify({request,base,profile:this.profile})
    return await this.custody.read("candidate-test-result",request.runId)===undefined
  }
  async run(request:CandidateTestRequest,base:GitSnapshot,signal:AbortSignal):Promise<CandidateTestOutcome>{
    const unavailable=(output:string):CandidateTestOutcome=>({outcome:"unavailable",output})
    const unknown=(output:string):CandidateTestOutcome=>({outcome:"unknown",output})
    const p=this.profile
    if(process.platform!=="linux"||p.platform!=="linux"||p.formatVersion!==1)return unavailable("Unqualified project-check platform")
    if(request.command.length!==2||request.command[0]!=="project-check-v1")return unavailable("Unsupported project-check selector")
    if(!uuidPattern.test(request.runId)||!Array.isArray(p.checks)||p.checks.length>128||new Set(p.checks.map(c=>c.id)).size!==p.checks.length)nativeUnavailable()
    const check=p.checks.find(c=>c.id===request.command[1])
    if(!check)return unavailable("Selected check is not in the protected execution profile")
    if(!Number.isSafeInteger(check.timeoutMillis)||check.timeoutMillis<1||check.timeoutMillis>120000||
      !Number.isSafeInteger(check.outputBytes)||check.outputBytes<1||check.outputBytes>512*1024||check.args.length>128)nativeUnavailable()
    if(Object.keys(check.environment).some(key=>!/^[A-Z][A-Z0-9_]*$/.test(key)||/TOKEN|SECRET|AUTH|PASSWORD|CREDENTIAL|API_KEY|^LD_|^DYLD_|BASH_ENV|ENV$/.test(key)))nativeUnavailable()
    const authority=async()=>{if(signal.aborted||!await this.authorize(request,signal))nativeUnavailable()}
    await authority();await this.custody.initialize()
    if(await fs.realpath(p.sourceRoot)!==p.sourceRoot||await fs.realpath(p.worktreeParent)!==p.worktreeParent)nativeUnavailable()
    await this.custody.requireOutside(p.sourceRoot);await this.custody.requireOutside(p.worktreeParent)
    const parent=await fs.lstat(p.worktreeParent)
    if(!parent.isDirectory()||parent.uid!==process.getuid!()||parent.mode&0o022)nativeUnavailable()
    await this.pin(p.envExecutable);await this.pin(check.executable)
    const snapshot={...base,...request.candidate,retainedLocatorId:request.runId}
    const directory=path.join(p.worktreeParent,`check-${request.runId}`),outputRoot=path.join(p.worktreeParent,`output-${request.runId}`)
    const original=JSON.stringify({request,base,profile:p}),digest=hash(original)
    const old=await this.custody.read("project-check-original",request.runId)
    if(old!==undefined&&old!==original)nativeUnavailable()
    if(old===undefined&&!await this.custody.retain("project-check-original",request.runId,original))nativeUnavailable()
    const projects=await this.native.project.list({},{signal})
    if(!projects.some(project=>project.id===p.nativeProjectId&&project.canonical===p.sourceRoot&&project.vcs==="git"&&!project.commands?.start))nativeUnavailable()
    const inventory=()=>this.native.worktree.list({projectID:p.nativeProjectId},{signal})
    if(await this.custody.read("project-check-area",request.runId)===undefined){
      if((await inventory()).some(area=>area.directory.startsWith(directory)))nativeUnavailable()
      try{await fs.lstat(directory);nativeUnavailable()}catch(error){if((error as NodeJS.ErrnoException).code!=="ENOENT")throw error}
      await authority()
      if(await this.custody.retain("project-check-area",request.runId,original)){
        const area=await this.native.worktree.create({projectID:p.nativeProjectId,from:p.sourceRoot,branch:snapshot.commit.hex,
          directory:p.worktreeParent,name:path.basename(directory)},{signal})
        if(area.directory!==directory)nativeUnavailable()
      }
    }
    if((await inventory()).filter(area=>area.directory===directory&&area.strategy==="git").length!==1)return unknown("Original check worktree outcome unavailable")
    await this.git.verify(directory,p.sourceRoot,snapshot,signal)
    const areaStat=await fs.lstat(directory)
    if(!areaStat.isDirectory()||areaStat.isSymbolicLink())nativeUnavailable()
    if(await this.custody.read("project-check-output-area",request.runId)===undefined){
      await fs.mkdir(outputRoot,{mode:0o700})
      await this.custody.retain("project-check-output-area",request.runId,original)
    }
    const outputStat=await fs.lstat(outputRoot)
    if(!outputStat.isDirectory()||outputStat.isSymbolicLink()||outputStat.uid!==process.getuid!()||outputStat.mode&0o077)nativeUnavailable()
    const expand=(value:string)=>{
      if(typeof value!=="string"||value.includes("\0")||Buffer.byteLength(value)>16384)nativeUnavailable()
      return value.replaceAll("{workRoot}",directory).replaceAll("{outputRoot}",outputRoot)
        .replaceAll("{candidateCommit}",snapshot.commit.hex).replaceAll("{candidateTree}",snapshot.tree.hex)
    }
    const environment:Record<string,string>={...check.environment,HOME:outputRoot,TMPDIR:outputRoot,PATH:"/usr/bin:/bin",LANG:"C.UTF-8"}
    const command="exec "+[p.envExecutable.path,"-i",...Object.entries(environment).map(([key,value])=>`${key}=${expand(value)}`),
      check.executable.path,...check.args.map(expand)].map(quote).join(" ")
    const metadata={writhelmProjectCheck:{runId:request.runId,digest}}
    let jobId:string|undefined
    const dispatched=await this.custody.read("project-check-dispatch",request.runId)
    if(dispatched!==undefined&&dispatched!==original)nativeUnavailable()
    if(dispatched===undefined){
      await authority();await this.pin(p.envExecutable);await this.pin(check.executable)
      if(await this.custody.retain("project-check-dispatch",request.runId,original)){
        try{
          const job=await this.native.shell.create({location:{directory:p.sourceRoot},cwd:directory,command,timeout:check.timeoutMillis,metadata},{signal})
          jobId=job.data.id
        }catch{/* A lost response never authorizes another create. Observe only. */}
      }
    }
    const jobs=(await this.native.shell.list({location:{directory:p.sourceRoot}},{signal})).data
    const matches=jobs.filter(job=>job.metadata?.writhelmProjectCheck&&contextJSON(job.metadata.writhelmProjectCheck)===contextJSON(metadata.writhelmProjectCheck))
    if(matches.length!==1||jobId!==undefined&&matches[0]!.id!==jobId)return unknown("Original native check job is missing or ambiguous; no second dispatch")
    const job=matches[0]!
    if(job.command!==command||job.cwd!==directory)return unknown("Native check job does not match original invocation")
    // Completed jobs need not survive native process restart in its inventory. Retain the exact
    // observed original ID independently; this is custody, never permission for another create.
    const jobWitness=contextJSON({runId:request.runId,digest,id:job.id,command,cwd:directory})
    await this.custody.retain("project-check-job",request.runId,jobWitness)
    if(await this.custody.read("project-check-job",request.runId)!==jobWitness)nativeUnavailable()
    const deadline=Date.now()+check.timeoutMillis+1000
    let current=job
    while(current.status==="running"){
      if(signal.aborted||Date.now()>=deadline)return unknown("Check still running or cancellation unconfirmed; work area remains held")
      await wait(50);current=(await this.native.shell.get({id:job.id,location:{directory:p.sourceRoot}},{signal})).data
    }
    if(current.status!=="exited"||!Number.isSafeInteger(current.exit))return unknown("Native timeout/kill does not establish writer quiescence")
    if(current.id!==job.id||current.command!==command||current.cwd!==directory||
      contextJSON(current.metadata?.writhelmProjectCheck)!==contextJSON(metadata.writhelmProjectCheck))return unknown("Native terminal job identity changed")
    const result=await this.native.shell.output({id:job.id,location:{directory:p.sourceRoot},limit:check.outputBytes},{signal})
    await this.git.verify(directory,p.sourceRoot,snapshot,signal);await authority()
    const text=JSON.stringify(result.data)
    return {outcome:current.exit===0?"passed":"failed",output:`Native project check exit=${current.exit}; output frame${Buffer.byteLength(text)>check.outputBytes?" truncated":""}:\n`+
      Buffer.from(text).subarray(0,check.outputBytes).toString("utf8")}
  }
}
