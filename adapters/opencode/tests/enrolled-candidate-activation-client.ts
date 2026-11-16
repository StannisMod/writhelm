// SPDX-License-Identifier: AGPL-3.0-only
// Test-only service transport wiring. Canonical execution facts/caller are synthetic;
// candidate activation, delivery, Git assembly, permits and native jobs are real adapters.
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { execFileSync } from "node:child_process"
import { createServer } from "node:net"
import { setTimeout as wait } from "node:timers/promises"
import { OpenCode } from "@opencode/client"
import { Service } from "@opencode/client/service"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import type { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxGitWitness } from "../src/linux-git-witness.js"
import { NativeProjectChecks, bindProjectCheckTemplate } from "../src/native-project-checks.js"
import { CanonicalCandidateActivation } from "../src/native-candidate-activation.js"
import { startCandidateDeliveryLoop } from "../src/native-runtime.js"

const [root,gitPath,gitDigest,configFile]=process.argv.slice(2)
if(!root||!gitPath||!gitDigest||!configFile)throw Error("Explicit disposable inputs required")
const cfg=JSON.parse(await fs.readFile(configFile,"utf8")),s=cfg.service,sessionID=cfg.controllerSessionID
assert.equal(typeof sessionID,"string")
assert.equal(createHash("sha256").update(await fs.readFile(cfg.cli)).digest("hex"),"8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")
const client=new NativeRuntimeClient(s.origin,s.proof,s.storeId,cfg.projectId,s.controllerId,s.hostId)
const custody=new LinuxNativeCustody(path.join(root,"native"),[cfg.sourceRoot]);await custody.initialize()
const witness=new LinuxGitWitness({path:gitPath,sha256:gitDigest})
await witness.verifyRetainedRef(cfg.sourceRoot,cfg.result,new AbortController().signal)
const caller=(actual:string)=>assert.equal(actual,sessionID)
const registry={
  candidateDelivery:async(actual:string,ref:string,signal?:AbortSignal)=>{caller(actual);return client.candidateDelivery(s.token,ref,signal)},
  candidateTestPlan:async(actual:string,ref:string,signal?:AbortSignal)=>{caller(actual);return client.candidateTestPlan(s.token,ref,signal)},
  candidateAssemblyPermit:async(actual:string,raw:string,signal?:AbortSignal)=>{caller(actual);return client.candidateAssemblyPermit(s.token,raw,signal)},
  candidateTestPermit:async(actual:string,assembly:string,ref:string,job:string,signal?:AbortSignal)=>{
    caller(actual);return client.candidateTestPermit(s.token,assembly,ref,job,signal)
  },
} as unknown as NativeCredentialRegistry // No permit/qualification result is fabricated here.
const nativeRoot=path.join(root,"activation-native")
for(const name of ["home","config","data","state","cache","runtime"])await fs.mkdir(path.join(nativeRoot,name),{recursive:true,mode:0o700})
const env={HOME:path.join(nativeRoot,"home"),XDG_CONFIG_HOME:path.join(nativeRoot,"config"),XDG_DATA_HOME:path.join(nativeRoot,"data"),
  XDG_STATE_HOME:path.join(nativeRoot,"state"),XDG_CACHE_HOME:path.join(nativeRoot,"cache"),XDG_RUNTIME_DIR:path.join(nativeRoot,"runtime"),
  OPENCODE_DB:path.join(nativeRoot,"data/native.db"),OPENCODE_DESKTOP_TEST_ROOT:nativeRoot,PATH:"/usr/bin:/bin"}
const socket=createServer();await new Promise<void>(resolve=>socket.listen(0,"127.0.0.1",resolve))
const address=socket.address();assert.ok(address&&typeof address!=="string")
await new Promise<void>((resolve,reject)=>socket.close(error=>error?reject(error):resolve()))
execFileSync(cfg.cli,["service","set","port",String(address.port)],{cwd:env.HOME,env,stdio:"pipe"})
const registration=path.join(nativeRoot,"state/opencode/service.json")
let activation:CanonicalCandidateActivation|undefined
try{
  const endpoint=await Service.ensure({file:registration,version:"2.2.5",command:[cfg.cli,"serve","--service"],env})
  const native=OpenCode.make({baseUrl:endpoint.url,headers:{...Service.headers(endpoint),"x-opencode-directory":cfg.sourceRoot}})
  const createdJobs:string[]=[]
  const checkedNative={...native,shell:{...native.shell,create:async(...args:Parameters<typeof native.shell.create>)=>{
    const job=await native.shell.create(...args);createdJobs.push(job.data.id);return job
  }}}
  const nativeSession=await native.session.create({location:{directory:cfg.sourceRoot},permissions:[{action:"*",resource:"*",effect:"deny"}]})
  const policy=bindProjectCheckTemplate(cfg.projectCheckTemplateJSON,nativeSession.projectID)
  assert.equal(policy.sourceRoot,cfg.sourceRoot)
  const loops:ReturnType<typeof startCandidateDeliveryLoop>[]=[]
  activation=new CanonicalCandidateActivation(custody,cfg.projectId,sessionID,
    (ref,signal)=>registry.candidateDelivery(sessionID,ref,signal),
    (ref,signal)=>registry.candidateTestPlan(sessionID,ref,signal),profile=>{
      const checks=new NativeProjectChecks(checkedNative,custody,witness,policy,async(job,signal)=>{
        const original=await custody.read("candidate-operation",profile.operationId);assert.ok(original)
        return registry.candidateTestPermit(sessionID,original,profile.planRefJSON,JSON.stringify(job),signal)
      })
      const loop=startCandidateDeliveryLoop(cfg.sourceRoot,custody,witness,registry,sessionID,{profile,pollMillis:1000,
        checkAdmission:job=>checks.current(job,profile.head),execute:(job,signal)=>checks.run(job,profile.head,signal)})
      loops.push(loop);return loop
    })
  const activated=await activation.activate(s.planRefJSON);assert.equal(activated.phase,"candidate-delivery-activated")
  assert.ok(activated.profile);assert.equal(loops.length,1)
  await loops[0]!.initial
  const deadline=Date.now()+15000
  let status=loops[0]!.status as any
  while(status?.phase!=="ready-for-review"&&Date.now()<deadline){await wait(50);status=loops[0]!.status}
  assert.equal(status?.phase,"ready-for-review",JSON.stringify(status))
  const before=(await native.shell.list({location:{directory:cfg.sourceRoot}})).data.map(job=>job.id)
  const replay=await activation.activate(s.planRefJSON)
  assert.deepEqual(replay.profile,activated.profile);assert.equal(loops.length,1)
  assert.deepEqual((await native.shell.list({location:{directory:cfg.sourceRoot}})).data.map(job=>job.id),before)
  assert.equal(createdJobs.length,1,"Exactly one actual native create, including activation replay")
  const plan=await registry.candidateTestPlan(sessionID,s.planRefJSON)
  process.stdout.write(JSON.stringify({projectId:cfg.projectId,assemblyId:activated.profile.operationId,
    testPlanId:activated.profile.testPlanId,candidateId:activated.profile.candidateId,checks:plan}))
}finally{await activation?.close();await Service.stop({file:registration})}
