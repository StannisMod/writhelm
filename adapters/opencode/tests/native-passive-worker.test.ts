// SPDX-License-Identifier: AGPL-3.0-only
// Linux actual Git/custody/bytes with explicit MOCK service projection and external native SDK.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash, randomBytes } from "node:crypto"
import { execFileSync } from "node:child_process"
import { spawn } from "node:child_process"
import { once } from "node:events"
import { createServer } from "node:net"
import type { OpenCodeClient } from "@opencode/client"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxGitWitness, type GitSnapshot } from "../src/linux-git-witness.js"
import { NativePassiveWorkers } from "../src/native-passive-worker.js"
import { NativeResultRetention, NativeCandidateAssembly, NativeCandidateTests, runCandidateChecks, advanceCandidateDelivery } from "../src/native-result-retention.js"
import { listenNativeTransport } from "../src/native-transport.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
test("equivalent plan JSON cannot bypass blocked or unknown final-review custody",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const custody=new LinuxNativeCustody(path.join(f.root,"review-subjects"),[f.source,f.parent]);await custody.initialize()
  git(f.source,"update-ref",`refs/writhelm/results/${f.snapshot.retainedLocatorId}`,f.snapshot.commit.hex)
  const reference={family:"dispatch-plans",key:{projectId:id(1),id:id(470)},digest:"a".repeat(64)}
  const registry={candidateDelivery:async()=>({selectedJSON:"[]",inputs:[{base:f.snapshot,result:f.snapshot}],excludedReviewers:[id(1)]}),
    candidateAssemblyPermit:async()=>true,candidateTestPlan:async()=>[{checkId:"check",command:["fixture"],safeRepeat:true,dependencies:[]}],candidateTestPermit:async()=>true}
  for(const [index,unknown] of [false,true].entries()){
    const profile={planRefJSON:JSON.stringify(reference),operationId:id(480+index*10),candidateId:id(481+index*10),head:f.snapshot,time:"1794390000 +0000",testPlanId:id(482+index*10),capacity:1}
    git(f.source,"update-ref",`refs/heads/writhelm-candidate-${profile.candidateId}`,f.snapshot.commit.hex)
    let reviews=0
    const reviewer={reviewId:id(483+index*10),contextId:id(484+index*10),reviewerPrincipalId:id(999),execute:async(task:any)=>{
      reviews++;if(unknown)throw Error("MOCK lost reviewer result")
      return {...task,summary:"MOCK blocked review",findings:[{severity:"blocking" as const,requirement:"H1-09",scenario:"fixture",expected:"conform",actual:"blocker",evidence:"fixture"}]}
    }}
    const execute=async()=>({outcome:"passed" as const,output:"MOCK test"})
    const run=(p=profile,r=reviewer)=>advanceCandidateDelivery(f.source,custody,f.witness,registry as never,"ses_controller",p,execute,signal,r)
    if(unknown)await assert.rejects(run());else assert.equal((await run()).phase,"review-blocked")
    assert.equal((await run()).phase,unknown?"review-unknown":"review-blocked")
    await assert.rejects(run({...profile,planRefJSON:JSON.stringify(reference,null,2)},
      {...reviewer,reviewId:id(485+index*10),contextId:id(486+index*10)}))
    assert.equal(reviews,1)
  }
})
test("third safe successful run satisfies the complete plan without erasing two failures",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const custody=new LinuxNativeCustody(path.join(f.root,"third-run"),[f.source,f.parent]);await custody.initialize()
  const candidate={commit:f.snapshot.commit,tree:f.snapshot.tree}
  let executions=0
  const jobs=new NativeCandidateTests(custody,async()=>true,async()=>({outcome:++executions<3?"failed":"passed",output:`MOCK execution ${executions}`}))
  const checks=[{checkId:"repeatable",command:["fixture"],safeRepeat:true,dependencies:[]}]
  const failed=await runCandidateChecks(custody,jobs,id(460),candidate,checks,1,signal)
  assert.equal(failed.ready,false);assert.equal(executions,2)
  const retryId=failed.results.get("repeatable")!.runId
  const third={runId:id(461),candidate,checkId:"repeatable",command:["fixture"],safeRepeat:true,previousRunId:retryId}
  assert.equal((await jobs.run(third,signal)).outcome,"passed")
  assert.equal(await jobs.readyForReview(candidate,id(460)),true)
  assert.equal((await runCandidateChecks(custody,jobs,id(460),candidate,checks,1,signal)).ready,true)
  assert.equal(executions,3)
  const plan=JSON.parse((await custody.read("candidate-test-plan",id(460)))!)
  for(const runId of [plan.runs[0].runId,retryId])assert.equal(JSON.parse((await custody.read("candidate-test-result",runId))!).result.outcome,"failed")
})
test("candidate scheduler holds unknown and unavailable dependencies without dispatching unsafe reruns",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const custody=new LinuxNativeCustody(path.join(f.root,"test-jobs"),[f.source,f.parent]);await custody.initialize()
  const candidate={commit:f.snapshot.commit,tree:f.snapshot.tree}
  const executed:string[]=[]
  const jobs=new NativeCandidateTests(custody,async()=>true,async(job)=>{
    executed.push(job.checkId)
    if(job.checkId==="unknown")throw Error("MOCK lost process result")
    return {outcome:job.checkId==="cancelled"?"cancelled":"unavailable",output:"MOCK adapter outcome"}
  })
  const checks=[{checkId:"unknown",command:["fixture"],safeRepeat:true,dependencies:[]},
    {checkId:"unavailable",command:["fixture"],safeRepeat:false,dependencies:[]},
    {checkId:"cancelled",command:["fixture"],safeRepeat:false,dependencies:[]},
    {checkId:"blocked",command:["fixture"],safeRepeat:true,dependencies:["unknown"]}]
  const result=await runCandidateChecks(custody,jobs,id(450),candidate,checks,2,signal)
  assert.equal(result.ready,false);assert.deepEqual(result.held,["blocked"])
  assert.equal(result.results.get("unknown")?.result.outcome,"unknown")
  assert.equal(result.results.get("cancelled")?.result.outcome,"cancelled")
  assert.equal(result.results.get("unavailable")?.result.outcome,"unavailable")
  await runCandidateChecks(custody,jobs,id(450),candidate,checks,2,signal)
  assert.equal(executed.length,3)
  await assert.rejects(runCandidateChecks(custody,jobs,id(451),candidate,checks,2,signal))
  await assert.rejects(jobs.run({runId:id(452),candidate,checkId:"unknown",command:["fixture"],safeRepeat:false,previousRunId:null},signal))
  await assert.rejects(runCandidateChecks(custody,jobs,id(453),candidate,[checks[1]!],2,signal))
  assert.equal(executed.length,3)
})
const git=(root:string,...args:string[])=>execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-C",root,...args],
  {env:{PATH:"/usr/bin:/bin",LC_ALL:"C",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},encoding:"utf8"}).trim()
test("candidate objects preserve unrelated work and publication uses exact candidate CAS",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const commit=(message:string)=>{git(f.source,"add",".");git(f.source,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m",message)}
  await fs.writeFile(path.join(f.source,"score.txt"),"score=1\n");commit("producer")
  const result:GitSnapshot={...f.snapshot,commit:{algorithm:"sha1",hex:git(f.source,"rev-parse","HEAD")},tree:{algorithm:"sha1",hex:git(f.source,"rev-parse","HEAD^{tree}")},retainedLocatorId:id(402)}
  git(f.source,"update-ref",`refs/writhelm/results/${result.retainedLocatorId}`,result.commit.hex)
  git(f.source,"switch","--detach",f.snapshot.commit.hex)
  await fs.writeFile(path.join(f.source,"unrelated.txt"),"keep\n");commit("unrelated candidate work")
  const head:GitSnapshot={...f.snapshot,commit:{algorithm:"sha1",hex:git(f.source,"rev-parse","HEAD")},tree:{algorithm:"sha1",hex:git(f.source,"rev-parse","HEAD^{tree}")}}
  const candidateId=id(403),ref=`refs/heads/writhelm-candidate-${candidateId}`
  git(f.source,"update-ref",ref,head.commit.hex)
  await fs.writeFile(path.join(f.source,"local-untracked.txt"),"never touch\n")
  const candidate=await f.witness.buildCandidate(f.source,head,[{base:f.snapshot,result}],id(404),"1794390000 +0000",signal)
  assert.equal(git(f.source,"show",`${candidate.commit.hex}:score.txt`),"score=1")
  assert.equal(git(f.source,"show",`${candidate.commit.hex}:unrelated.txt`),"keep")
  assert.equal(git(f.source,"rev-parse","HEAD"),head.commit.hex)
  assert.equal(await fs.readFile(path.join(f.source,"local-untracked.txt"),"utf8"),"never touch\n")
  await f.witness.publishCandidateOnce(f.source,candidateId,head.commit,candidate.commit,signal)
  assert.equal(git(f.source,"rev-parse",ref),candidate.commit.hex)
  await assert.rejects(f.witness.publishCandidateOnce(f.source,candidateId,head.commit,result.commit,signal))
  assert.equal(git(f.source,"rev-parse",ref),candidate.commit.hex)
  const custody=new LinuxNativeCustody(path.join(f.root,"candidate-custody"),[f.source,f.parent]);await custody.initialize()
  let authorized=true,writes=0,losePublication=false
  const effects={buildCandidate:f.witness.buildCandidate.bind(f.witness),qualifySource:f.witness.qualifySource.bind(f.witness),
    publishCandidateOnce:async(...args:Parameters<LinuxGitWitness["publishCandidateOnce"]>)=>{
      writes++;await f.witness.publishCandidateOnce(...args);if(losePublication)throw Error("lost publication reply")
    }}
  const assembly=new NativeCandidateAssembly(f.source,custody,effects,async()=>authorized)
  const request={operationId:id(405),candidateId:id(406),head,inputs:[{base:f.snapshot,result}],time:"1794390000 +0000",selectedJSON:"[]"}
  git(f.source,"update-ref",`refs/heads/writhelm-candidate-${request.candidateId}`,head.commit.hex)
  authorized=false;await assert.rejects(assembly.assemble(request,signal));assert.equal(writes,0)
  authorized=true
  const assembled=await assembly.assemble(request,signal)
  const reopened=new NativeCandidateAssembly(f.source,custody,effects,async()=>authorized)
  assert.deepEqual(await reopened.assemble(request,signal),assembled);assert.equal(writes,1)
  await assert.rejects(reopened.assemble({...request,time:"1794390001 +0000"},signal));assert.equal(writes,1)
  const unknown={...request,operationId:id(407),candidateId:id(408)}
  git(f.source,"update-ref",`refs/heads/writhelm-candidate-${unknown.candidateId}`,head.commit.hex)
  losePublication=true
  await assert.rejects(assembly.assemble(unknown,signal));assert.equal(writes,2)
  await assert.rejects(reopened.assemble(unknown,signal));assert.equal(writes,2)
  authorized=false;await assert.rejects(reopened.assemble(request,signal));assert.equal(writes,2)
  let runs=0
  const checks=new NativeCandidateTests(custody,async()=>true,async(job)=>{
    await f.witness.qualifySource(f.source,{...head,...job.candidate},signal)
    assert.equal(git(f.source,"show",`${job.candidate.commit.hex}:score.txt`),"score=1")
    const exit=runs++===0?"1":"0" // Explicit fixture transient failure, not candidate mutation.
    try {
      execFileSync(process.execPath,job.command.slice(1),{env:{FIXTURE_EXIT:exit},encoding:"utf8"})
      return {outcome:"passed",output:`actual node exit 0; FIXTURE_EXIT=${exit}`}
    }catch {return {outcome:"failed",output:`actual node exit 1; FIXTURE_EXIT=${exit}`}}
  })
  const first={runId:id(410),candidate:assembled,checkId:"fixture-transient",command:["node","-e","process.exit(Number(process.env.FIXTURE_EXIT))"],safeRepeat:true,previousRunId:null}
  assert.equal((await checks.run(first,signal)).outcome,"failed")
  assert.equal(await checks.readyForReview(assembled,id(412)),false)
  const retry={...first,runId:id(411),previousRunId:first.runId}
  assert.equal((await checks.run(retry,signal)).outcome,"passed")
  assert.equal((await checks.run(first,signal)).outcome,"failed");assert.equal(runs,2)
  assert.equal((await checks.run(retry,signal)).outcome,"passed");assert.equal(runs,2)
  const subject=JSON.stringify({candidate:assembled,checks:[{checkId:first.checkId,command:first.command,safeRepeat:true,dependencies:[]}]})
  await custody.retain("candidate-test-plan",id(412),JSON.stringify({subject,runs:[{checkId:first.checkId,runId:first.runId,retryId:retry.runId}]}))
  await custody.retain("candidate-required-plan",JSON.stringify([assembled.commit.algorithm,assembled.commit.hex,assembled.tree.algorithm,assembled.tree.hex]),JSON.stringify({planId:id(412),subject}))
  assert.equal(await checks.readyForReview(assembled,id(412)),true)
  assert.equal(await checks.readyForReview(candidate,id(412)),false)
})
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"passive-")),source=path.join(root,"source"),parent=path.join(root,"areas")
  await fs.mkdir(source);await fs.mkdir(parent,{mode:0o700})
  git(source,"init","-b","main");await fs.writeFile(path.join(source,"score.txt"),"score=0\n")
  git(source,"add","score.txt");git(source,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","Synthetic score baseline")
  const snapshot:GitSnapshot={repositoryId:id(20),commit:{algorithm:"sha1",hex:git(source,"rev-parse","HEAD")},
    tree:{algorithm:"sha1",hex:git(source,"rev-parse","HEAD^{tree}")},dirtyContentDigest:null,retainedLocatorId:id(21)}
  const key={projectId:id(1),taskId:id(2),revision:0},profileKey={projectId:id(1),profileId:id(3),revision:0},manifestKey={projectId:id(1),id:id(4)}
  const intended={controllerId:id(5),executionId:id(6),template:"executor-v1",nativeHostId:id(7),nativeSessionId:"ses_passive_original"}
  const assignment={family:"assignments",key:{projectId:id(1),id:id(8)},payload:{state:"reserved",executionGeneration:0,
    intendedExecution:intended,currentIntent:{projectId:id(1),id:id(9)},workspaceId:id(10),task:key,profile:profileKey,
    manifest:{family:"input-manifests",key:manifestKey,digest:"a".repeat(64)}}}
  const frame={assignmentJSON:JSON.stringify(assignment),intentJSON:JSON.stringify({family:"native-intents",key:assignment.payload.currentIntent,
    payload:{assignment:{assignmentId:id(8),generation:0},operation:"prepare",outcome:"pending",expectedSession:intended.nativeSessionId,expectedWorkspace:id(10)}}),
    taskJSON:JSON.stringify({family:"task-revisions",key,payload:{state:"executable",base:{code:[snapshot]}}}),
    profileJSON:JSON.stringify({family:"profiles",key:profileKey,payload:{qualification:"unqualified",qualificationEvidence:[],model:{providerId:"MOCK",modelId:"no-call",variant:null}}}),
    manifestJSON:JSON.stringify({family:"input-manifests",key:manifestKey})}
  let readable=true,lost=false,creates=0,sessions=0,startup:string|undefined
  const inventory:Array<{directory:string;strategy:string}>=[]
  const nativeSessions=new Map<string,any>()
  const native={project:{list:async()=>[{id:"native-project",canonical:source,vcs:"git",commands:{start:startup}}]},
    worktree:{list:async()=>inventory,create:async(input:any)=>{
      creates++;const directory=path.join(input.directory,input.name)
      git(source,"worktree","add","--detach",directory,input.branch)
      inventory.push({directory,strategy:"git"});return {directory}
    }},session:{create:async(input:any)=>{
      sessions++;const value={...input,projectID:"native-project",location:input.location,cost:0,tokens:{input:0,output:0,reasoning:0,cache:{read:0,write:0}}}
      nativeSessions.set(input.id,value)
      if(lost){lost=false;throw Error("MOCK lost native create acknowledgement")}
      return value
    },get:async({sessionID}:{sessionID:string})=>{const value=nativeSessions.get(sessionID);if(!value)throw {_tag:"SessionNotFoundError",sessionID,message:"MOCK missing receiver"};return value},
    context:async()=>[],inbox:{list:async()=>[]},active:async()=>({}),prompt:async()=>{throw Error("MODEL WAKE FORBIDDEN IN PASSIVE PREPARATION")}}} as unknown as OpenCodeClient
  const registry={inspect:async()=>({scope:"controller",hostId:id(7),epoch:"a".repeat(64)}),
    preparationContext:async()=>{if(!readable)throw Error("MOCK current dispatch/read revoked");return frame}} as unknown as NativeCredentialRegistry
  const witness=new LinuxGitWitness({path:"/usr/bin/git",sha256:createHash("sha256").update(await fs.readFile("/usr/bin/git")).digest("hex")})
  const preparer=new NativePassiveWorkers(native,registry,new LinuxNativeCustody(path.join(root,"custody"),[source,parent]),"ses_controller",
    {sourceRoot:source,worktreeParent:parent,nativeProjectId:"native-project",repositoryId:id(20),controllerId:id(5),hostId:id(7),agents:{"executor-v1":"build","reviewer-v1":"plan"}},witness)
  return {root,source,parent,frame,snapshot,witness,preparer,nativeSessions,revoke:()=>{readable=false},
    lose:()=>{lost=true},startup:(value:string)=>{startup=value},counts:()=>({creates,sessions})}
}

test("original lost passive-session acknowledgement reconciles exact receiver without another create or wake",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  f.lose();await assert.rejects(f.preparer.prepare(id(8),"0",signal))
  assert.deepEqual(f.counts(),{creates:1,sessions:1})
  assert.equal(await f.preparer.proof(id(8),"0"),null)
  const prepared=await f.preparer.prepare(id(8),"0",signal)
  const witness=await f.preparer.proof(id(8),"0")
  assert.equal(witness?.contextCount,0);assert.equal(witness?.inboxCount,0)
  assert.equal(JSON.parse(JSON.stringify(witness)).contextCount,0)
  assert.equal(prepared.sessionID,"ses_passive_original")
  assert.deepEqual(f.counts(),{creates:1,sessions:1})
  const info=f.nativeSessions.get(prepared.sessionID)
  assert.equal(info.parentID,undefined);assert.deepEqual(info.permissions,[{action:"*",resource:"*",effect:"deny"}])
  info.title="A human title change does not select another execution"
  assert.equal((await f.preparer.prepare(id(8),"0",signal)).observation,prepared.observation)
  info.model={providerID:"MOCK",id:"different"}
  await assert.rejects(f.preparer.prepare(id(8),"0",signal))
  assert.deepEqual(f.counts(),{creates:1,sessions:1})
})

test("current authority and no-startup preflight precede native effects; existing suffixed paths are not adopted",async()=>{
  const signal=new AbortController().signal
  const revoked=await fixture();revoked.revoke()
  await assert.rejects(revoked.preparer.prepare(id(8),"0",signal));assert.deepEqual(revoked.counts(),{creates:0,sessions:0})
  const startup=await fixture();startup.startup("execute a project command")
  await assert.rejects(startup.preparer.prepare(id(8),"0",signal));assert.deepEqual(startup.counts(),{creates:0,sessions:0})
  const collision=await fixture();await fs.mkdir(path.join(collision.parent,id(10)))
  await assert.rejects(collision.preparer.prepare(id(8),"0",signal));assert.deepEqual(collision.counts(),{creates:0,sessions:0})
  const existing=await fixture();existing.nativeSessions.set("ses_passive_original",{id:"ses_passive_original",metadata:{unrelated:true}})
  await assert.rejects(existing.preparer.prepare(id(8),"0",signal));assert.deepEqual(existing.counts(),{creates:0,sessions:0})
  await assert.rejects(collision.preparer.prepare(id(8),"1",signal))
})

test("Linux witness verifies actual pinned Git/tree/content and never treats changed or extra files as the clean base",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const prepared=await f.preparer.prepare(id(8),"0",signal)
  await f.witness.verify(prepared.directory,f.source,f.snapshot,signal)
  await fs.writeFile(path.join(prepared.directory,"score.txt"),"score=CHANGED\n")
  await assert.rejects(f.witness.verify(prepared.directory,f.source,f.snapshot,signal))
  await fs.writeFile(path.join(prepared.directory,"score.txt"),"score=0\n")
  await fs.writeFile(path.join(prepared.directory,"untracked.txt"),"not in the selected base")
  await assert.rejects(f.witness.verify(prepared.directory,f.source,f.snapshot,signal))
})

test("Linux diff observation binds exact clean worktree snapshots and rejects forged or dirty results",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const prepared=await f.preparer.prepare(id(8),"0",signal)
  await fs.writeFile(path.join(prepared.directory,"score.txt"),"score=1\n")
  git(prepared.directory,"add","score.txt")
  git(prepared.directory,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","Synthetic worker change")
  const result:GitSnapshot={...f.snapshot,commit:{algorithm:"sha1",hex:git(prepared.directory,"rev-parse","HEAD")},
    tree:{algorithm:"sha1",hex:git(prepared.directory,"rev-parse","HEAD^{tree}")},retainedLocatorId:id(22)}
  await assert.rejects(f.witness.verifyRetainedRef(f.source,result,signal))
  const ref=`refs/writhelm/results/${result.retainedLocatorId}`
  await f.witness.pinRetainedRefOnce(f.source,result,signal)
  await f.witness.verifyRetainedRef(f.source,result,signal)
  await assert.rejects(f.witness.pinRetainedRefOnce(f.source,result,signal)) // never overwrite an existing ref
  await assert.rejects(f.witness.verifyRetainedRef(f.source,{...result,retainedLocatorId:id(23)},signal))
  await assert.rejects(f.witness.verifyRetainedRef(f.source,{...result,tree:f.snapshot.tree},signal))
  git(f.source,"update-ref",ref,f.snapshot.commit.hex,result.commit.hex)
  await assert.rejects(f.witness.verifyRetainedRef(f.source,result,signal))
  await assert.rejects(f.witness.pinRetainedRefOnce(f.source,result,signal))
  assert.equal(git(f.source,"rev-parse",ref),f.snapshot.commit.hex)
  git(f.source,"update-ref",ref,result.commit.hex,f.snapshot.commit.hex)
  const observed=await f.witness.observeDiff(prepared.directory,f.source,f.snapshot,result,signal)
  const expected=execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-c","core.fsmonitor=false","-C",prepared.directory,
    "diff","--no-ext-diff","--no-textconv","--no-color","--binary","--full-index",f.snapshot.commit.hex,result.commit.hex,"--"],
    {env:{PATH:"/usr/bin:/bin",LC_ALL:"C",GIT_CONFIG_NOSYSTEM:"1",GIT_CONFIG_GLOBAL:"/dev/null",GIT_TERMINAL_PROMPT:"0",GIT_OPTIONAL_LOCKS:"0"}})
  assert.deepEqual(observed.bytes,expected);assert.equal(observed.sha256,createHash("sha256").update(expected).digest("hex"))
  assert.match(observed.bytes.toString("utf8"),/\+score=1/)
  const proof=await f.preparer.resultDiffProof(id(8),"0",JSON.stringify(result),signal)
  assert.equal(proof?.diffSha256,observed.sha256);assert.equal(proof?.diffByteLength,observed.byteLength)
  assert.equal(await f.preparer.resultDiffProof(id(777),"0",JSON.stringify(result),signal),null)
  await assert.rejects(f.preparer.resultDiffProof(id(8),"1",JSON.stringify(result),signal))
  const mutated=observed.bytes;mutated.fill(0)
  assert.deepEqual(observed.bytes,expected) // callers cannot mutate the retained observation bytes
  await assert.rejects(f.witness.observeDiff(prepared.directory,f.source,f.snapshot,{...result,tree:f.snapshot.tree},signal))
  await assert.rejects(f.witness.observeDiff(prepared.directory,f.source,f.snapshot,{...result,repositoryId:id(999)},signal))
  await fs.writeFile(path.join(prepared.directory,"score.txt"),"score=uncommitted\n")
  await assert.rejects(f.witness.observeDiff(prepared.directory,f.source,f.snapshot,result,signal))
  await assert.rejects(f.preparer.resultDiffProof(id(8),"0",JSON.stringify(result),signal))
  await fs.writeFile(path.join(prepared.directory,"score.txt"),"score=1\n")
  git(f.source,"worktree","remove",prepared.directory)
  git(f.source,"gc","--prune=now")
  const reopened=new LinuxGitWitness({path:"/usr/bin/git",sha256:createHash("sha256").update(await fs.readFile("/usr/bin/git")).digest("hex")})
  await reopened.verifyRetainedRef(f.source,result,signal)
})

test("retention records original binding, refuses collision and never repeats an unknown effect",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const custody=new LinuxNativeCustody(path.join(f.root,"retention-custody"),[f.source,f.parent])
  await custody.initialize()
  let authorized=true,calls=0
  const gitControl={qualifySource:f.witness.qualifySource.bind(f.witness),verifyRetainedRef:f.witness.verifyRetainedRef.bind(f.witness),
    pinRetainedRefOnce:async(...args:Parameters<LinuxGitWitness["pinRetainedRefOnce"]>)=>{calls++;await f.witness.pinRetainedRefOnce(...args)}}
  const control=new NativeResultRetention(f.source,custody,gitControl,async()=>authorized,async()=>true) // explicit mocked worktree observation
  const request={assignmentId:id(8),generation:"0",executionId:id(6),snapshot:{...f.snapshot,retainedLocatorId:id(40)}}
  assert.equal(await control.proof(request,signal),null)
  await control.retain(request,signal);await control.retain(request,signal)
  assert.equal(JSON.parse((await control.proof(request,signal))!).executionId,id(6))
  assert.equal(await control.proof({...request,executionId:id(7)},signal),null)
  const port=await new Promise<number>((resolve,reject)=>{const s=createServer();s.once("error",reject);s.listen(0,"127.0.0.1",()=>{
    const address=s.address();if(!address||typeof address==="string")return reject(Error("no test port"))
    s.close(error=>error?reject(error):resolve(address.port))
  })})
  const origin=`http://127.0.0.1:${port}`,secret=randomBytes(32)
  const transport=await listenNativeTransport(origin,secret,{} as never,{} as never,undefined,undefined,undefined,undefined,control)
  try {
    const body=JSON.stringify({formatVersion:1,operation:"result-retention-proof",assignmentId:request.assignmentId,
      executionGeneration:request.generation,executionId:request.executionId,snapshotJSON:JSON.stringify(request.snapshot)})
    const send=(proof:string)=>fetch(`${origin}/native/v1`,{method:"POST",headers:{"content-type":"application/json","x-writhelm-native-proof":proof},body})
    assert.equal((await send(randomBytes(32).toString("base64url"))).status,403)
    const response=await send(secret.toString("base64url"));assert.equal(response.status,200)
    assert.equal((await response.json() as {result:string}).result,await control.proof(request,signal))
  } finally {await transport.close()}
  assert.equal(calls,1)
  await assert.rejects(control.retain({...request,executionId:id(7)},signal))
  authorized=false;await assert.rejects(control.retain(request,signal));authorized=true
  const reopenedCustody=new LinuxNativeCustody(path.join(f.root,"retention-custody"),[f.source,f.parent]);await reopenedCustody.initialize()
  await new NativeResultRetention(f.source,reopenedCustody,gitControl,async()=>true,async()=>true).retain(request,signal)
  assert.equal(calls,1)
  const lost={...request,snapshot:{...request.snapshot,retainedLocatorId:id(41)}}
  const lostControl=new NativeResultRetention(f.source,reopenedCustody,{...gitControl,pinRetainedRefOnce:async(...args)=>{
    await gitControl.pinRetainedRefOnce(...args);throw Error("MOCK lost effect acknowledgement")}},async()=>true,async()=>true)
  await assert.rejects(lostControl.retain(lost,signal));assert.equal(calls,2)
  assert.equal(await lostControl.proof(lost,signal),null)
  await assert.rejects(new NativeResultRetention(f.source,reopenedCustody,gitControl,async()=>true,async()=>true).retain(lost,signal))
  assert.equal(calls,2) // an observed ref cannot manufacture a receipt
})

test("Linux process loss after ref write leaves original retention unknown without a second effect",{timeout:30000},async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const root=path.join(f.root,"fault-custody"),snapshot={...f.snapshot,retainedLocatorId:id(51)}
  const request={assignmentId:id(8),generation:"0",executionId:id(6),snapshot}
  const script=`
    import {LinuxNativeCustody} from ${JSON.stringify(new URL("../src/native-runtime-custody.js",import.meta.url).href)};
    import {LinuxGitWitness} from ${JSON.stringify(new URL("../src/linux-git-witness.js",import.meta.url).href)};
    import {NativeResultRetention} from ${JSON.stringify(new URL("../src/native-result-retention.js",import.meta.url).href)};
    import {readFile} from 'node:fs/promises'; import {createHash} from 'node:crypto';
    const [root,source,parent,encoded]=process.argv.slice(1),request=JSON.parse(Buffer.from(encoded,'base64url').toString());
    const custody=new LinuxNativeCustody(root,[source,parent]);await custody.initialize();
    const witness=new LinuxGitWitness({path:'/usr/bin/git',sha256:createHash('sha256').update(await readFile('/usr/bin/git')).digest('hex')});
    const git={qualifySource:witness.qualifySource.bind(witness),verifyRetainedRef:witness.verifyRetainedRef.bind(witness),
      pinRetainedRefOnce:async(...args)=>{await witness.pinRetainedRefOnce(...args);await new Promise(resolve=>process.stdout.write('PINNED\\n',resolve));process.stdin.resume();await new Promise(()=>{})}};
    await new NativeResultRetention(source,custody,git,async()=>true,async()=>true).retain(request,new AbortController().signal);
  `
  const child=spawn(process.execPath,["--input-type=module","-e",script,root,f.source,f.parent,Buffer.from(JSON.stringify(request)).toString("base64url")],
    {stdio:["pipe","pipe","pipe"]})
  const exited=once(child,"exit"),lines=once(child.stdout,"data"),timeout=new Promise<never>((_,reject)=>{
    const timer=setTimeout(()=>reject(Error("test child did not reach ref checkpoint")),15000);timer.unref()
  })
  try {
    const [data]=await Promise.race([lines,timeout]);assert.equal(String(data),"PINNED\n")
    assert.equal(child.kill("SIGKILL"),true)
    const [code,killedBy]=await exited;assert.equal(code,null);assert.equal(killedBy,"SIGKILL")
    const custody=new LinuxNativeCustody(root,[f.source,f.parent]);await custody.initialize()
    let repeated=0
    const gitControl={qualifySource:f.witness.qualifySource.bind(f.witness),verifyRetainedRef:f.witness.verifyRetainedRef.bind(f.witness),
      pinRetainedRefOnce:async(...args:Parameters<LinuxGitWitness["pinRetainedRefOnce"]>)=>{repeated++;await f.witness.pinRetainedRefOnce(...args)}}
    const recovered=new NativeResultRetention(f.source,custody,gitControl,async()=>true,async()=>true)
    assert.equal(await recovered.proof(request,signal),null)
    await assert.rejects(recovered.retain(request,signal));assert.equal(repeated,0)
    assert.equal(git(f.source,"rev-parse",`refs/writhelm/results/${snapshot.retainedLocatorId}`),snapshot.commit.hex)
  } finally {
    if(child.exitCode===null && child.signalCode===null){child.kill("SIGKILL");await exited}
  }
})
