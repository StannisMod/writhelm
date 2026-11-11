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
import { NativeResultRetention } from "../src/native-result-retention.js"
import { listenNativeTransport } from "../src/native-transport.js"

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
