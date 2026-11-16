// SPDX-License-Identifier: AGPL-3.0-only
// Real preparation/passive/Git/service/delegation/bind. Native SDK is synthetic by default;
// installedNative selects the pinned isolated 2.2.5 service and actual automatic coordinator.
// Prompt is forbidden and qualification is supplied by the explicitly synthetic JVM fixture.
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { execFileSync } from "node:child_process"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { LinuxGitWitness } from "../src/linux-git-witness.js"
import { NativePassiveWorkers } from "../src/native-passive-worker.js"
import { NativeWorkerPreparation } from "../src/native-worker-preparation.js"
import { contextJSON } from "../src/native-context-codec.js"
import { OpenCode } from "@opencode/client"
import { Service } from "@opencode/client/service"
import { createHash, randomUUID } from "node:crypto"
import { createServer } from "node:net"
import { NativeAutomaticCandidateReview } from "../src/native-automatic-review.js"
import { NativeCandidateReviewLifecycle } from "../src/native-candidate-review.js"
import { ProtectedNativeBridge } from "../src/native-bridge.js"
import { ConnectedNativeAdapter } from "../src/connected-native.js"
import { listenNativeTransport } from "../src/native-transport.js"
import { NativeResultRetention, NativeCandidateTests } from "../src/native-result-retention.js"
import { NativeProjectChecks, type ProjectCheckProfile } from "../src/native-project-checks.js"
import { initialReleasePermissions, NativeInitialAdapter } from "../src/native-initial.js"
import { NativeContextSources } from "../src/native-context-source.js"

const cfg=JSON.parse(await fs.readFile(process.argv[2]!,"utf8")),service=cfg.service
const sessions=new Map<string,any>(),inventory:Array<{directory:string;strategy:string}>=[]
let creates=0,trees=0,lost=true
let native:any={project:{list:async()=>[{id:"synthetic-project",canonical:cfg.sourceRoot,vcs:"git"}]},worktree:{list:async()=>inventory,
  create:async(input:any)=>{
    trees++;const directory=path.join(input.directory,input.name)
    execFileSync(cfg.git.path,["-c","core.hooksPath=/dev/null","-C",cfg.sourceRoot,"worktree","add","--detach",directory,input.branch],
      {env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},stdio:"pipe"})
    inventory.push({directory,strategy:"git"});return {directory}
  }},session:{get:async({sessionID}:{sessionID:string})=>{
    if(sessionID===cfg.sessionID)return {id:sessionID,location:{directory:cfg.sourceRoot}}
    const value=sessions.get(sessionID)
    if(!value)throw {_tag:"SessionNotFoundError",sessionID,message:"Synthetic missing receiver"}
    return value
  },create:async(input:any)=>{
    creates++;const value={...input,projectID:"synthetic-project",cost:0,tokens:{input:0,output:0,reasoning:0,cache:{read:0,write:0}}}
    sessions.set(input.id,value)
    if(lost){lost=false;throw Error("Synthetic lost create acknowledgement")}
    return value
  },context:async()=>[],inbox:{list:async()=>[]},active:async()=>({}),prompt:async()=>{throw Error("No model calls authorized")}}}
let nativeProjectId="synthetic-project"
let installedClient:ReturnType<typeof OpenCode.make>|undefined
const registration=cfg.installedNative?path.join(cfg.root,"installed-native/state/opencode/service.json"):undefined
let transport:Awaited<ReturnType<typeof listenNativeTransport>>|undefined
try{
if(cfg.installedNative){
  assert.equal(createHash("sha256").update(await fs.readFile(cfg.cli.path)).digest("hex"),cfg.cli.sha256)
  const root=path.join(cfg.root,"installed-native")
  for(const name of ["home","config","data","state","cache","runtime"])await fs.mkdir(path.join(root,name),{recursive:true,mode:0o700})
  const environment={
    HOME:path.join(root,"home"),XDG_CONFIG_HOME:path.join(root,"config"),XDG_DATA_HOME:path.join(root,"data"),
    XDG_STATE_HOME:path.join(root,"state"),XDG_CACHE_HOME:path.join(root,"cache"),XDG_RUNTIME_DIR:path.join(root,"runtime"),
    OPENCODE_DB:path.join(root,"data/client.db"),OPENCODE_DESKTOP_TEST_ROOT:root,PATH:"/usr/bin:/bin",WRITHELM_TEST_SECRET:"synthetic-never-child"}
  if(!cfg.expectRevoked){
    const socket=createServer();await new Promise<void>(resolve=>socket.listen(0,"127.0.0.1",resolve))
    const address=socket.address();assert.ok(address&&typeof address!=="string")
    await new Promise<void>((resolve,reject)=>socket.close(error=>error?reject(error):resolve()))
    execFileSync(cfg.cli.path,["service","set","port",String(address.port)],{cwd:environment.HOME,env:environment,stdio:"pipe"})
  }
  const endpoint=await Service.ensure({file:registration,version:"2.2.5",command:[cfg.cli.path,"serve","--service"],env:environment})
  const client=OpenCode.make({baseUrl:endpoint.url,headers:{...Service.headers(endpoint),"x-opencode-directory":cfg.sourceRoot}})
  installedClient=client
  if(!cfg.expectRevoked)await client.session.create({id:cfg.sessionID,location:{directory:cfg.sourceRoot},permissions:[{action:"*",resource:"*",effect:"deny"}]})
  const projects=await client.project.list()
  nativeProjectId=projects.find(p=>p.canonical===cfg.sourceRoot)!.id
  native={project:client.project,worktree:{...client.worktree,create:async(...args:Parameters<typeof client.worktree.create>)=>{
    trees++;return client.worktree.create(...args)
  }},session:{...client.session,get:async(...args:Parameters<typeof client.session.get>)=>{
    const value=await client.session.get(...args)
    if(value.id!==cfg.sessionID)await fs.writeFile(path.join(cfg.root,"observed-native-session.json"),JSON.stringify(value),{mode:0o600})
    return value
  },create:async(...args:Parameters<typeof client.session.create>)=>{
    creates++;const value=await client.session.create(...args)
    if(lost){lost=false;throw Error("Synthetic lost create acknowledgement")}
    return value
  },prompt:async()=>{throw Error("No model calls authorized")}}}
}
const custody=new LinuxNativeCustody(path.join(cfg.root,"native"),[cfg.sourceRoot,cfg.worktreeParent]);await custody.initialize()
const ctx={app:{version:"2.2.5"},session:native.session} as never
const registry=new NativeCredentialRegistry(ctx,custody,
  new NativeRuntimeClient(service.origin,service.proof,service.storeId,cfg.projectId,service.controllerId,service.hostId),service.origin,
  new LinuxWorkRequestCustody(path.join(cfg.root,"requests"),[cfg.sourceRoot,cfg.worktreeParent]),cfg.epoch)
await registry.adopt(cfg.principalId,cfg.credentialId,service.token);await registry.bind(cfg.sessionID,cfg.credentialId,cfg.sourceRoot)
const workers=new NativePassiveWorkers(native as never,registry,custody,cfg.sessionID,{sourceRoot:cfg.sourceRoot,worktreeParent:cfg.worktreeParent,
  nativeProjectId,repositoryId:cfg.repositoryId,controllerId:service.controllerId,hostId:service.hostId,
  agents:{"executor-v1":"build","reviewer-v1":"plan"}},new LinuxGitWitness(cfg.git))
const bridge=new ProtectedNativeBridge(registry,custody,cfg.sessionID,service.hostId)
const retention=new NativeResultRetention(cfg.sourceRoot,custody,new LinuxGitWitness(cfg.git),async()=>false,async()=>false)
const deployment=new LinuxNativeCustody(path.join(cfg.root,"deployment"),[cfg.sourceRoot,cfg.worktreeParent]);await deployment.initialize()
const initialAdapter=new NativeInitialAdapter(ctx,native,registry,custody,deployment,cfg.sessionID)
const sources=new NativeContextSources(registry,custody)
transport=await listenNativeTransport(cfg.transportOrigin,Buffer.from(cfg.transportProof,"base64url"),bridge,
  new ConnectedNativeAdapter(ctx,native.session,bridge,service.hostId),sources,workers,initialAdapter,undefined,
  retention)
const driver=()=>new NativeWorkerPreparation(registry,workers,custody,cfg.sessionID,
  {projectId:cfg.projectId,controllerId:service.controllerId,hostId:service.hostId,worktreeParent:cfg.worktreeParent})
const signal=new AbortController().signal
if(cfg.expectRevoked){
  const restoredReceipt=await retention.candidateProof(cfg.profile.operationId,signal);assert.ok(restoredReceipt)
  const restoredAssembly=JSON.parse(JSON.parse(restoredReceipt).original)
  assert.ok(await retention.candidateInputsProof(cfg.profile.operationId,JSON.stringify({snapshots:[
    {...restoredAssembly.head,...cfg.candidate,retainedLocatorId:cfg.profile.candidateId},restoredAssembly.head,
    ...restoredAssembly.inputs.map((input:any)=>input.result)],results:restoredAssembly.inputs}),signal))
  if(cfg.installedNative){
    const restored=await native.session.get({sessionID:cfg.expectedReviewerSession})
    assert.equal(restored.id,cfg.expectedReviewerSession)
    assert.deepEqual(await native.session.context({sessionID:restored.id}),[])
    assert.equal(restored.cost,0)
  }
  const held=await driver().runController(cfg.selectionJSON,cfg.preparationId,signal)
  assert.equal(held.phase,"reservation-held")
  assert.equal(creates,0);assert.equal(trees,0)
  process.stdout.write(JSON.stringify({revocationHeld:true,creates,trees,modelCalls:0,nativeRestartObserved:Boolean(cfg.installedNative),inputProofRecovered:true}))
}else{
let prepared:any
if(cfg.installedNative){
  const lifecycle=new NativeCandidateReviewLifecycle(registry,{runController:async()=>{throw Error("Test cut before model admission")}},custody,cfg.sessionID,cfg.projectId)
  const automatic=new NativeAutomaticCandidateReview(registry,{runController:async(...args)=>{
    const value=await driver().runController(...args);prepared=value;return value
  }},lifecycle,custody,cfg.sessionID,cfg.projectId)
  await assert.rejects(automatic.prepare(cfg.profile,cfg.candidate,cfg.selectedJSON,signal),/Synthetic lost create acknowledgement/)
  await assert.rejects(automatic.prepare(cfg.profile,cfg.candidate,cfg.selectedJSON,signal),/Test cut before model admission/)
  await assert.rejects(automatic.prepare(cfg.profile,cfg.candidate,cfg.selectedJSON,signal),/Test cut before model admission/)
}else{
  await assert.rejects(driver().runController(cfg.selectionJSON,cfg.preparationId,signal),/Synthetic lost create acknowledgement/)
}
const first=prepared??await driver().runController(cfg.selectionJSON,cfg.preparationId,signal)
assert.equal(first.phase,"binding-confirmed")
const replay=await driver().runController(cfg.selectionJSON,cfg.preparationId,signal)
assert.equal(replay.phase,"binding-confirmed");assert.equal(contextJSON(first),contextJSON(replay))
assert.equal(creates,1);assert.equal(trees,1)
const proofRequest=JSON.stringify({formatVersion:1,operation:"worker-proof",assignmentId:first.assignmentId,executionGeneration:"0"})
const deniedProof=await fetch(cfg.transportOrigin+"/native/v1",{method:"POST",headers:{"Content-Type":"application/json",
  "X-Writhelm-Native-Proof":Buffer.alloc(32,0).toString("base64url")},body:proofRequest})
assert.equal(deniedProof.status,403);await deniedProof.body?.cancel()
const actualProof=await fetch(cfg.transportOrigin+"/native/v1",{method:"POST",headers:{"Content-Type":"application/json",
  "X-Writhelm-Native-Proof":cfg.transportProof},body:proofRequest})
assert.equal(actualProof.status,200)
assert.equal((await actualProof.json() as any).result.contextCount,0)
const info=await native.session.get({sessionID:first.nativeSessionId})
const readGit=(...args:string[])=>execFileSync(cfg.git.path,["-c","core.hooksPath=/dev/null","-C",info.location.directory,...args],
  {env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},encoding:"utf8"}).trim()
assert.equal(readGit("rev-parse","HEAD"),cfg.candidate.commit.hex)
assert.equal(readGit("rev-parse","HEAD^{tree}"),cfg.candidate.tree.hex)
assert.equal(readGit("show","HEAD:result.txt"),"implemented")
const originalSource=await fs.readFile(path.join(cfg.sourceRoot,"result.txt"))
try{
  await fs.writeFile(path.join(cfg.sourceRoot,"result.txt"),"uncommitted owner bytes, not reviewer input\n")
  assert.equal(readGit("show","HEAD:result.txt"),"implemented")
  assert.equal(await fs.readFile(path.join(info.location.directory,"result.txt"),"utf8"),"implemented\n")
}finally{await fs.writeFile(path.join(cfg.sourceRoot,"result.txt"),originalSource)}
const execution=first.execution
assert.ok(execution)
const rawCredential=await custody.read("credential",execution.credentialId)
assert.ok(rawCredential)
const credential=JSON.parse(rawCredential) // Private disposable fixture output only, never console/published evidence.
const candidateProofJSON=await retention.candidateProof(cfg.profile.operationId,signal)
assert.ok(candidateProofJSON)
const retainedAssembly=JSON.parse(JSON.parse(candidateProofJSON).original)
const inputRequest=JSON.stringify({snapshots:[{...retainedAssembly.head,...cfg.candidate,retainedLocatorId:cfg.profile.candidateId},
  retainedAssembly.head,...retainedAssembly.inputs.map((input:any)=>input.result)],results:retainedAssembly.inputs})
assert.ok(await retention.candidateInputsProof(cfg.profile.operationId,inputRequest,signal))
const candidateRef=`refs/heads/writhelm-candidate-${cfg.profile.candidateId}`
const sourceGit=(...args:string[])=>execFileSync(cfg.git.path,["-c","core.hooksPath=/dev/null","-C",cfg.sourceRoot,...args],
  {env:{PATH:"/usr/bin:/bin",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},stdio:"pipe"})
try{
  sourceGit("update-ref",candidateRef,cfg.profile.head.commit.hex,cfg.candidate.commit.hex)
  await assert.rejects(retention.candidateProof(cfg.profile.operationId,signal))
}finally{sourceGit("update-ref",candidateRef,cfg.candidate.commit.hex,cfg.profile.head.commit.hex)}
const input=retainedAssembly.inputs[0],inputRef=`refs/writhelm/results/${input.result.retainedLocatorId}`
try{
  sourceGit("update-ref",inputRef,input.base.commit.hex,input.result.commit.hex)
  await assert.rejects(retention.candidateInputsProof(cfg.profile.operationId,inputRequest,signal))
}finally{sourceGit("update-ref",inputRef,input.result.commit.hex,input.base.commit.hex)}
const driverOriginal=await custody.read("preparation-driver",cfg.preparationId);assert.ok(driverOriginal)
let reviewerPolicyProbe=false
let projectChecksProbe=false
if(installedClient){
  const checkClient=installedClient
  const node=await fs.realpath(process.execPath),env=await fs.realpath("/usr/bin/env")
  const executable={path:node,sha256:createHash("sha256").update(await fs.readFile(node)).digest("hex")}
  const testScript=(pass:boolean)=>`const t=require('node:test'),a=require('node:assert/strict'),f=require('node:fs');t('exact candidate and clean environment',()=>{a.equal(f.readFileSync('result.txt','utf8'),'implemented\\n');a.equal(process.env.WRITHELM_TEST_SECRET,undefined);a.equal(${pass},true)})`
  let nativeJobs=0,loseJobReply=true
  const checksNative={project:installedClient.project,worktree:installedClient.worktree,shell:{...installedClient.shell,
    create:async(...args:Parameters<typeof checkClient.shell.create>)=>{
      nativeJobs++;const job=await installedClient!.shell.create(...args)
      if(loseJobReply){loseJobReply=false;throw Error("Synthetic lost native job acknowledgement after actual create")}
      return job
    }}}
  const checkProfile:ProjectCheckProfile={
    formatVersion:1,platform:"linux",nativeProjectId,sourceRoot:cfg.sourceRoot,worktreeParent:cfg.worktreeParent,
    envExecutable:{path:env,sha256:createHash("sha256").update(await fs.readFile(env)).digest("hex")},
    checks:[...([true,false].map(pass=>({id:pass?"node-pass":"node-fail",executable,args:["-e",testScript(pass)],environment:{},timeoutMillis:10000,outputBytes:32768}))),
      {id:"node-timeout",executable,args:["-e","setTimeout(()=>{},2000)"],environment:{},timeoutMillis:100,outputBytes:32768}]
  }
  const checker=new NativeProjectChecks(checksNative,custody,new LinuxGitWitness(cfg.git),checkProfile,async()=>true) // Explicit adapter-test authorization; production composition uses actual service permits.
  const jobs=new NativeCandidateTests(custody,async()=>true,(request,abort)=>checker.run(request,cfg.profile.head,abort))
  const sourceBefore=await fs.readFile(path.join(cfg.sourceRoot,"result.txt"))
  try{
    await fs.writeFile(path.join(cfg.sourceRoot,"result.txt"),"dirty owner bytes, never check input\n")
    for(const pass of [true,false]){
      const request={runId:randomUUID(),candidate:cfg.candidate,checkId:pass?"project-pass":"project-fail",
        command:["project-check-v1",pass?"node-pass":"node-fail"],safeRepeat:false,previousRunId:null}
      const result=await jobs.run(request,signal)
      assert.equal(result.outcome,pass?"passed":"failed",result.output)
      assert.deepEqual(await jobs.run(request,signal),result)
      assert.equal(await checker.current(request,cfg.profile.head),true)
      const changed=new NativeProjectChecks(checksNative,custody,new LinuxGitWitness(cfg.git),
        {...checkProfile,checks:checkProfile.checks.map(check=>({...check,outputBytes:check.outputBytes+1}))},async()=>true)
      assert.equal(await changed.current(request,cfg.profile.head),false,"Changed execution policy must not inherit a cached pass")
    }
    assert.equal(nativeJobs,2,"Replay or lost create reply must not dispatch another native job")
    const rejected={runId:randomUUID(),candidate:cfg.candidate,checkId:"project-rejected",command:["project-check-v1","unapproved-id"],safeRepeat:false,previousRunId:null}
    assert.equal((await checker.run(rejected,cfg.profile.head,signal)).outcome,"unavailable")
    await assert.rejects(new NativeProjectChecks(checksNative,custody,new LinuxGitWitness(cfg.git),checkProfile,async()=>false)
      .run({...rejected,command:["project-check-v1","node-pass"]},cfg.profile.head,signal))
    assert.equal(nativeJobs,2,"Missing profile ID or revoked authorization must precede native dispatch")
    let unknownAttempts=0
    const lost=new NativeProjectChecks({...checksNative,shell:{...checksNative.shell,create:async()=>{
      unknownAttempts++;throw Error("Synthetic unknown native create outcome")
    }}},custody,new LinuxGitWitness(cfg.git),checkProfile,async()=>true)
    const unknownJobs=new NativeCandidateTests(custody,async()=>true,(request,abort)=>lost.run(request,cfg.profile.head,abort))
    const uncertain={...rejected,runId:randomUUID(),checkId:"project-unknown",command:["project-check-v1","node-pass"]}
    assert.equal((await unknownJobs.run(uncertain,signal)).outcome,"unknown")
    assert.equal((await unknownJobs.run(uncertain,signal)).outcome,"unknown");assert.equal(unknownAttempts,1)
    const timeout={...rejected,runId:randomUUID(),checkId:"project-timeout",command:["project-check-v1","node-timeout"]}
    assert.equal((await jobs.run(timeout,signal)).outcome,"unknown","Timeout is not proof of writer quiescence")
    assert.equal(await fs.readFile(path.join(cfg.sourceRoot,"result.txt"),"utf8"),"dirty owner bytes, never check input\n")
    projectChecksProbe=true
  }finally{await fs.writeFile(path.join(cfg.sourceRoot,"result.txt"),sourceBefore)}
  const sessionID=first.nativeSessionId
  const before=await installedClient.session.get({sessionID})
  const permissions=initialReleasePermissions([{action:"*",resource:"*",effect:"deny"},
    ...["read","glob","grep","edit","shell","execute","subagent","external_directory"].map(action=>({action,resource:"*",effect:"allow" as const}))],"reviewer-v1")
  try{
    await installedClient.session.update({sessionID,permissions})
    for(const action of ["edit","shell","execute","subagent","external_directory"]){
      const decision=await installedClient.permission.create({sessionID,action,resources:[path.join(info.location.directory,"result.txt")]})
      assert.equal(decision.effect,"deny",`Reviewer ${action} must be denied by the actual native permission engine`)
    }
    const read=await installedClient.permission.create({sessionID,action:"read",resources:[path.join(info.location.directory,"result.txt")]})
    assert.equal(read.effect,"allow")
    assert.equal(await fs.readFile(path.join(info.location.directory,"result.txt"),"utf8"),"implemented\n")
    reviewerPolicyProbe=true
  }finally{await installedClient.session.update({sessionID,permissions:before.permissions})}
  await assert.rejects(initialAdapter.prepare({assignmentId:first.assignmentId,executionGeneration:"0",bindingJSON:contextJSON(first.binding)},cfg.preparationId),
    "A ticket is not a canonical start intent or a real held boundary")
  await assert.rejects(initialAdapter.release(sessionID))
  await assert.rejects(initialAdapter.admitTool(sessionID))
  const queryProof=async(operation:string,extra:Record<string,string>)=>fetch(cfg.transportOrigin+"/native/v1",{method:"POST",
    headers:{"Content-Type":"application/json","X-Writhelm-Native-Proof":cfg.transportProof},
    body:JSON.stringify({formatVersion:1,operation,...extra})})
  const initialProof=await queryProof("initial-proof",{intentId:cfg.preparationId})
  assert.equal(initialProof.status,200);assert.equal((await initialProof.json() as any).result,null)
  const sourceProof=await queryProof("source-proof",{locatorId:cfg.preparationId})
  assert.equal(sourceProof.status,403);await sourceProof.body?.cancel()
}
process.stdout.write(contextJSON({...first,reviewerToken:credential.token,reservationJSON:JSON.parse(driverOriginal).reserveJSON,candidateProofJSON,
  creates,trees,modelCalls:0,exactCandidateInputs:true,proofTransport:"authenticated-native-v1",reviewerPolicyProbe,
  unobservedStartSourceHeld:Boolean(installedClient),projectChecksProbe,
  nativeSDK:cfg.installedNative?"installed-2.2.5":"synthetic-test-only"}))
}
}finally{await transport?.close();if(registration)await Service.stop({file:registration})}
