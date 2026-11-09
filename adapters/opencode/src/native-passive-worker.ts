// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import path from "node:path"
import { randomUUID } from "node:crypto"
import type { OpenCodeClient } from "@opencode/client"
import { isSessionNotFoundError } from "@opencode/client"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativeCredentialRegistry } from "./native-credentials.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { LinuxGitWitness, type GitSnapshot } from "./linux-git-witness.js"

type Client=Pick<OpenCodeClient,"project"|"worktree"|"session">
export type PassiveWorkerConfiguration=Readonly<{
  sourceRoot:string;worktreeParent:string;nativeProjectId:string;controllerId:string;hostId:string
  repositoryId:string
  agents:Readonly<Record<"executor-v1"|"reviewer-v1",string>>
}>
const parse=(raw:string)=>exactJSON(raw,nativeUnavailable)
const same=(a:unknown,b:unknown)=>contextJSON(a)===contextJSON(b)
const permission=Object.freeze([Object.freeze({action:"*",resource:"*",effect:"deny" as const})])

/** Reserve precedes this helper. It creates ONLY passive receivers, never a native prompt/wake,
 * delegation, bind, resource release or a writer-fencing fact. Original dispatch is durable before
 * each native create. Existing dispatch means reconcile ONLY; absence is UNKNOWN, not permission
 * to repeat create. SDK-shaped tests do not qualify the actual host's create/startup semantics. */
export class NativePassiveWorkers {
  private readonly config:PassiveWorkerConfiguration
  constructor(private readonly native:Client,private readonly registry:NativeCredentialRegistry,
    private readonly custody:LinuxNativeCustody,private readonly controllerSessionID:string,
    config:PassiveWorkerConfiguration,private readonly git:LinuxGitWitness){
    this.config=Object.freeze({...config,agents:Object.freeze({...config.agents})})
  }
  private async current(assignmentId:string,generation:string,signal:AbortSignal){
    const raw=await this.registry.preparationContext(this.controllerSessionID,assignmentId,generation,signal)
    const a=parse(raw.assignmentJSON),i=parse(raw.intentJSON),task=parse(raw.taskJSON),profile=parse(raw.profileJSON),manifest=parse(raw.manifestJSON)
    const data=a.payload,intended=data?.intendedExecution
    if(a.family!=="assignments"||a.key?.id!==assignmentId||data?.state!=="reserved"||data.executionGeneration?.value!==generation||
      intended?.controllerId!==this.config.controllerId||intended?.nativeHostId!==this.config.hostId||
      !["executor-v1","reviewer-v1"].includes(intended?.template)||!/^ses_[A-Za-z0-9_-]{1,128}$/.test(intended?.nativeSessionId)||
      i.family!=="native-intents"||i.key?.id!==data.currentIntent?.id||i.payload?.operation!=="prepare"||i.payload.outcome!=="pending"||
      !same(i.payload.assignment,{assignmentId,generation:BigInt(generation)})||i.payload.expectedSession!==intended.nativeSessionId||i.payload.expectedWorkspace!==data.workspaceId||
      task.family!=="task-revisions"||!same(task.key,data.task)||task.payload?.state!=="executable"||
      profile.family!=="profiles"||!same(profile.key,data.profile)||!same(manifest.key,data.manifest?.key))nativeUnavailable()
    return {raw,a,i,task,profile,manifest}
  }
  private async project(signal:AbortSignal){
    const projects=await this.native.project.list({},{signal})
    const project=projects.find(p=>p.id===this.config.nativeProjectId)
    if(!project||project.canonical!==this.config.sourceRoot||project.vcs!=="git"||project.commands?.start)
      throw new Error("passive worktree requires the exact qualified Git project and no startup command")
    return project
  }
  private async inventory(signal:AbortSignal){
    const inventory=await this.native.worktree.list({projectID:this.config.nativeProjectId},{signal})
    if(inventory.length>1024||inventory.some(p=>!path.isAbsolute(p.directory)))nativeUnavailable()
    return inventory
  }
  async prepare(assignmentId:string,generation:string,signal:AbortSignal){
    if(process.platform!=="linux"||!uuidPattern.test(assignmentId)||!/^(0|[1-9][0-9]{0,18})$/.test(generation)||
      BigInt(generation)>9223372036854775807n||signal.aborted)nativeUnavailable()
    if(generation!=="0")throw new Error("fresh passive preparation cannot replace a rebind/recovery protocol")
    if(await fs.realpath(this.config.sourceRoot)!==this.config.sourceRoot||await fs.realpath(this.config.worktreeParent)!==this.config.worktreeParent)nativeUnavailable()
    const parent=await fs.lstat(this.config.worktreeParent)
    if(!parent.isDirectory()||parent.uid!==process.getuid!()||parent.mode&0o022)nativeUnavailable()
    await this.custody.initialize();await this.custody.requireOutside(this.config.sourceRoot);await this.custody.requireOutside(this.config.worktreeParent)
    const basis=await this.current(assignmentId,generation,signal),a=basis.a.payload,intended=a.intendedExecution
    const base=basis.task.payload.base?.code
    // The first adapter supports an explicitly selected clean Git snapshot only; never discard or
    // silently omit required dirty contents, another repository, submodules or a second code base.
    if(!Array.isArray(base)||base.length!==1||base[0].repositoryId!==this.config.repositoryId||base[0].dirtyContentDigest!==null||!base[0].commit)
      throw new Error("passive Git preparation requires one exact registered clean code base")
    const agent=this.config.agents[intended.template as "executor-v1"|"reviewer-v1"]
    const model=basis.profile.payload.model
    if(!agent||!model?.providerId||!model.modelId||basis.profile.payload.qualification!=="qualified")nativeUnavailable()
    const modelRef={providerID:model.providerId,id:model.modelId,...(model.variant===null?{}:{variant:model.variant})}
    const directory=path.join(this.config.worktreeParent,a.workspaceId)
    const key=contextJSON([assignmentId,generation])
    const controller=await this.registry.inspect(this.controllerSessionID)
    if(controller?.scope!=="controller"||controller.hostId!==this.config.hostId||!/^[0-9a-f]{64}$/.test(controller.epoch))nativeUnavailable()
    const previous=await this.custody.read("worker-preparation",key)
    const artifactLocatorId=previous===undefined?randomUUID():parse(previous).artifactLocatorId
    if(!uuidPattern.test(artifactLocatorId))nativeUnavailable()
    const snapshot={repositoryId:base[0].repositoryId,commit:base[0].commit,tree:base[0].tree,dirtyContentDigest:null,retainedLocatorId:artifactLocatorId}
    const expected={formatVersion:1,assignmentId,generation,intentId:basis.i.key.id,workspaceId:a.workspaceId,
      intendedExecution:intended,taskJSON:contextJSON(basis.task.key),profileJSON:contextJSON(basis.profile),
      baseJSON:contextJSON(base[0]),snapshotJSON:contextJSON(snapshot),artifactLocatorId,epoch:controller.epoch,directory,agent,model:modelRef}
    const raw=contextJSON(expected)
    await this.custody.retain("worker-preparation",key,raw)
    const unchanged=async()=>{
      const now=await this.current(assignmentId,generation,signal)
      if(!same(now.a.payload.intendedExecution,intended)||now.a.payload.workspaceId!==a.workspaceId||now.i.key.id!==basis.i.key.id||
        !same(now.task,basis.task)||!same(now.profile,basis.profile)||!same(now.manifest,basis.manifest))nativeUnavailable()
      await this.project(signal)
    }
    await unchanged()
    await this.git.qualifySource(this.config.sourceRoot,parse(expected.baseJSON) as GitSnapshot,signal)
    const unusedSession=async()=>{
      try{
        await this.native.session.get({sessionID:intended.nativeSessionId},{signal})
      }catch(error){
        if(isSessionNotFoundError(error)&&error.sessionID===intended.nativeSessionId)return
        throw error // no transport/auth/other 404 error is evidence of absence
      }
      throw new Error("native session ID collision; do not create or adopt an unrelated receiver")
    }
    if(await this.custody.read("session-create",key)===undefined)await unusedSession()
    const before=await this.inventory(signal)
    const issued=await this.custody.read("worktree-create",key)
    if(issued===undefined){
      if(before.some(p=>p.directory.startsWith(directory)))throw new Error("worktree name collision; no creation attempted")
      try{await fs.lstat(directory);throw new Error("worktree path already exists")}catch(error){if((error as NodeJS.ErrnoException).code!=="ENOENT")throw error}
      await unchanged()
      if(await this.custody.retain("worktree-create",key,raw)){
        const created=await this.native.worktree.create({projectID:this.config.nativeProjectId,from:this.config.sourceRoot,
          branch:base[0].commit.hex,directory:this.config.worktreeParent,name:a.workspaceId},{signal})
        if(created.directory!==directory)throw new Error("native worktree collision/refusal; retain original identity for recovery")
      }
    }else if(issued!==raw)nativeUnavailable()
    await unchanged()
    const matches=(await this.inventory(signal)).filter(p=>p.directory===directory)
    if(matches.length!==1||matches[0]!.strategy!=="git")throw new Error("original worktree outcome unknown or conflicting; never repeat create")
    const area=await fs.lstat(directory)
    if(!area.isDirectory()||area.isSymbolicLink()||area.uid!==process.getuid!())nativeUnavailable()
    await this.git.verify(directory,this.config.sourceRoot,parse(expected.baseJSON) as GitSnapshot,signal)
    const metadata={writhelm:{kind:"passive-preparation-v1",assignmentId,executionGeneration:generation,
      controllerId:this.config.controllerId,executionId:intended.executionId,workspaceId:a.workspaceId,
      taskJSON:expected.taskJSON,profileKeyJSON:contextJSON(basis.profile.key)}}
    const sessionRaw=contextJSON({sessionID:intended.nativeSessionId,directory,agent,model:modelRef,permissions:permission,metadata})
    const template=parse(sessionRaw) // immutable expected bytes, independent of any SDK input object
    const original=await this.custody.read("session-create",key)
    if(original===undefined){
      await unchanged()
      await unusedSession()
      if(await this.custody.retain("session-create",key,sessionRaw)){
        const created=await this.native.session.create({id:intended.nativeSessionId,location:{directory},agent,model:modelRef,
          title:`Bounded ${intended.template} — ${assignmentId}`,permissions:permission,metadata},{signal})
        if(created.id!==intended.nativeSessionId)nativeUnavailable()
      }
    }else if(original!==sessionRaw)nativeUnavailable()
    await unchanged()
    const info=await this.native.session.get({sessionID:intended.nativeSessionId},{signal})
    if(info.id!==intended.nativeSessionId||info.parentID||info.fork||info.revert||info.projectID!==this.config.nativeProjectId||
      info.location.directory!==directory||info.agent!==agent||!same(info.model,template.model)||!same(info.permissions,template.permissions)||!same(info.metadata,template.metadata))
      throw new Error("original passive native receiver is missing or changed")
    if(info.cost!==0||[info.tokens?.input,info.tokens?.output,info.tokens?.reasoning,info.tokens?.cache?.read,info.tokens?.cache?.write].some(n=>n!==0))
      throw new Error("native receiver has usage or unavailable usage metadata; passive creation is not confirmed")
    const [context,inbox,active]=await Promise.all([this.native.session.context({sessionID:info.id},{signal}),
      this.native.session.inbox.list({sessionID:info.id},{signal}),this.native.session.active({signal})])
    if(context.length||inbox.length||active[info.id])throw new Error("passive receiver has input/history/running work; preparation cannot grant execution")
    await this.git.verify(directory,this.config.sourceRoot,parse(expected.baseJSON) as GitSnapshot,signal)
    await unchanged()
    const observation=contextJSON({formatVersion:1,kind:"passive-receiver-observation",preparationJSON:raw,nativeInfoJSON:JSON.stringify(info),
      contextCount:context.length,inboxCount:inbox.length,nativeRunning:Boolean(active[info.id]),
      workspaceDirectory:directory,qualification:"passive preparation only; no bind/start/completion/fencing authority"})
    const retained=await this.custody.read("worker-prepared",key)
    if(retained!==undefined){
      const old=closedObject(parse(retained),["formatVersion","kind","preparationJSON","nativeInfoJSON","contextCount","inboxCount","nativeRunning","workspaceDirectory","qualification"])
      if(old.kind!=="passive-receiver-observation"||old.preparationJSON!==raw||old.workspaceDirectory!==directory)nativeUnavailable()
    }else await this.custody.retain("worker-prepared",key,observation)
    return Object.freeze({assignmentId,generation,sessionID:info.id,directory,observation:retained??observation})
  }
  /** Immutable private observer only. May be called while the service owns its monitor/admission:
   * no current service request, SDK query, new creation or authority decision is made here. Native
   * cost/time numbers stay opaque; only known scalar identity/model/permission fields are lowered. */
  async proof(assignmentId:string,generation:string){
    if(!uuidPattern.test(assignmentId)||generation!=="0")nativeUnavailable()
    const raw=await this.custody.read("worker-prepared",contextJSON([assignmentId,generation]))
    if(raw===undefined)return null
    const observed=closedObject(parse(raw),["formatVersion","kind","preparationJSON","nativeInfoJSON","contextCount","inboxCount","nativeRunning","workspaceDirectory","qualification"])
    const job=parse(observed.preparationJSON),native=JSON.parse(observed.nativeInfoJSON)
    if(observed.formatVersion?.value!=="1"||job.formatVersion?.value!=="1"||observed.kind!=="passive-receiver-observation"||
      observed.contextCount?.value!=="0"||observed.inboxCount?.value!=="0"||observed.nativeRunning!==false||
      job.assignmentId!==assignmentId||job.generation!==generation||job.directory!==observed.workspaceDirectory)nativeUnavailable()
    return {formatVersion:1,preparationJSON:observed.preparationJSON,snapshotJSON:job.snapshotJSON,
      contextCount:0,inboxCount:0,nativeRunning:false, // explicitly validated zero; never round-trip domain Int64 metadata through Number
      nativeJSON:contextJSON({sessionID:native.id,directory:native.location.directory,parentID:native.parentID??null,
        fork:native.fork??null,revert:native.revert??null,agent:native.agent,model:native.model,permissions:native.permissions,metadata:native.metadata})}
  }
}
