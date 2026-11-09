// SPDX-License-Identifier: AGPL-3.0-only
// Linux actual Git/custody/bytes with explicit MOCK service projection and external native SDK.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { execFileSync } from "node:child_process"
import type { OpenCodeClient } from "@opencode/client"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxGitWitness, type GitSnapshot } from "../src/linux-git-witness.js"
import { NativePassiveWorkers } from "../src/native-passive-worker.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
const git=(root:string,...args:string[])=>execFileSync("/usr/bin/git",["-c","core.hooksPath=/dev/null","-C",root,...args],
  {env:{PATH:"/usr/bin:/bin",LC_ALL:"C",GIT_CONFIG_GLOBAL:"/dev/null",GIT_CONFIG_NOSYSTEM:"1"},encoding:"utf8"}).trim()
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
    profileJSON:JSON.stringify({family:"profiles",key:profileKey,payload:{qualification:"qualified",model:{providerId:"MOCK",modelId:"no-call",variant:null}}}),
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
