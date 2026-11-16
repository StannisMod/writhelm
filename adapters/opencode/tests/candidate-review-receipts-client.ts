// SPDX-License-Identifier: AGPL-3.0-only
// Actual Git/custody/assembly/check adapters. Authorization is explicit MOCK, not qualification.
import * as fs from "node:fs/promises"
import path from "node:path"
import { execFileSync } from "node:child_process"
import { createHash, randomUUID } from "node:crypto"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxGitWitness, type GitSnapshot } from "../src/linux-git-witness.js"
import { NativeCandidateAssembly, NativeCandidateTests, runCandidateChecks } from "../src/native-result-retention.js"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { NativeProjectChecks, bindProjectCheckTemplate } from "../src/native-project-checks.js"
import { OpenCode } from "@opencode/client"
import { Service } from "@opencode/client/service"
import { createServer } from "node:net"

const [root,gitPath,gitDigest,configurationPath]=process.argv.slice(2)
if(!root||!gitPath||!gitDigest)throw Error("Explicit disposable root and Git pin required")
const configured=configurationPath?JSON.parse(await fs.readFile(configurationPath,"utf8")):undefined
const source=configured?.sourceRoot??path.join(root,"source")
if(!configured)await fs.mkdir(source,{mode:0o700})
const git=(...args:string[])=>execFileSync(gitPath,["-c","core.hooksPath=/dev/null","-C",source,...args],
  {encoding:"utf8",env:{PATH:"/usr/bin:/bin",LANG:"C",GIT_CONFIG_NOSYSTEM:"1",GIT_CONFIG_GLOBAL:"/dev/null"}}).trim()
if(!configured){
  git("init","--initial-branch=main");git("config","user.name","Synthetic receipt fixture");git("config","user.email","fixture@writhelm.invalid")
  await fs.writeFile(path.join(source,"result.txt"),"base\n");git("add","result.txt");git("commit","-m","base")
}
const projectId=configured?.projectId??randomUUID()
const snapshot=():GitSnapshot=>({repositoryId:projectId,commit:{algorithm:"sha1",hex:git("rev-parse","HEAD")},
  tree:{algorithm:"sha1",hex:git("rev-parse","HEAD^{tree}")},dirtyContentDigest:null,retainedLocatorId:randomUUID()})
const base:GitSnapshot=configured?.base??snapshot()
if(!configured){await fs.writeFile(path.join(source,"result.txt"),"implemented\n");git("commit","-am","implementation")}
const result:GitSnapshot=configured?.result??snapshot(),signal=new AbortController().signal,witness=new LinuxGitWitness({path:gitPath,sha256:gitDigest})
if(!configured)await witness.pinRetainedRefOnce(source,result,signal)
else await witness.verifyRetainedRef(source,result,signal)
const custody=new LinuxNativeCustody(path.join(root,"native"),[source]);await custody.initialize()
const assemblyId=randomUUID(),testPlanId=randomUUID(),candidateId=randomUUID()
const selectedJSON=configured?.selectedJSON??JSON.stringify([{family:"results",key:{projectId,id:randomUUID()},digest:"a".repeat(64)}])
const service=configured?.service
const client=service?new NativeRuntimeClient(service.origin,service.proof,service.storeId,projectId,service.controllerId,service.hostId):undefined
const request={operationId:assemblyId,candidateId,head:base,time:"1790000000 +0000",selectedJSON,inputs:[{base,result}]}
const assembly=new NativeCandidateAssembly(source,custody,witness,(input,abort)=>client?
  client.candidateAssemblyPermit(service.token,JSON.stringify(input),abort):Promise.resolve(true)) // default mode has explicit MOCK authorization
const candidate=await assembly.assemble(request,signal)
if(git("rev-parse",`refs/heads/writhelm-candidate-${candidateId}`)!==candidate.commit.hex||git("rev-parse","main")!==result.commit.hex)
  throw Error("Candidate publication changed a source branch or lost its exact candidate ref")
let refused=false
try{await witness.publishCandidateOnce(source,candidateId,base.commit,result.commit,signal)}catch{refused=true}
if(!refused||git("rev-parse",`refs/heads/writhelm-candidate-${candidateId}`)!==candidate.commit.hex)
  throw Error("Existing nonmatching candidate ref must never be overwritten")
const digest=createHash("sha256").update("implemented\n").digest("hex")
const checks=client?await client.candidateTestPlan(service.token,service.planRefJSON,signal):
  [{checkId:"whole-candidate-file",command:["immutable-git-file-sha256-v1","result.txt",digest],safeRepeat:false,dependencies:[]}]
let registration:string|undefined
try{
const authorize=(job:Parameters<NativeCandidateTests["run"]>[0],abort:AbortSignal)=>client?
  client.candidateTestPermit(service.token,JSON.stringify(request),service.planRefJSON,JSON.stringify(job),abort):Promise.resolve(true)
let projectChecks:NativeProjectChecks|undefined
if(configured?.installedNative){
  if(createHash("sha256").update(await fs.readFile(configured.cli)).digest("hex")!=="8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")throw Error("Native pin mismatch")
  const nativeRoot=path.join(root,"checks-native")
  for(const name of ["home","config","data","state","cache","runtime"])await fs.mkdir(path.join(nativeRoot,name),{recursive:true,mode:0o700})
  const environment={HOME:path.join(nativeRoot,"home"),XDG_CONFIG_HOME:path.join(nativeRoot,"config"),XDG_DATA_HOME:path.join(nativeRoot,"data"),
    XDG_STATE_HOME:path.join(nativeRoot,"state"),XDG_CACHE_HOME:path.join(nativeRoot,"cache"),XDG_RUNTIME_DIR:path.join(nativeRoot,"runtime"),
    OPENCODE_DB:path.join(nativeRoot,"data/checks.db"),OPENCODE_DESKTOP_TEST_ROOT:nativeRoot,PATH:"/usr/bin:/bin"}
  const socket=createServer();await new Promise<void>(resolve=>socket.listen(0,"127.0.0.1",resolve))
  const address=socket.address();if(!address||typeof address==="string")throw Error("Isolated native port unavailable")
  await new Promise<void>((resolve,reject)=>socket.close(error=>error?reject(error):resolve()))
  execFileSync(configured.cli,["service","set","port",String(address.port)],{cwd:environment.HOME,env:environment,stdio:"pipe"})
  registration=path.join(nativeRoot,"state/opencode/service.json")
  const endpoint=await Service.ensure({file:registration,version:"2.2.5",command:[configured.cli,"serve","--service"],env:environment})
  const native=OpenCode.make({baseUrl:endpoint.url,headers:{...Service.headers(endpoint),"x-opencode-directory":source}})
  const session=await native.session.create({location:{directory:source},permissions:[{action:"*",resource:"*",effect:"deny"}]})
  const profile=bindProjectCheckTemplate(configured.projectCheckTemplateJSON,session.projectID)
  if(profile.sourceRoot!==source)throw Error("Approved check policy belongs to another project")
  projectChecks=new NativeProjectChecks(native,custody,witness,profile,authorize)
}
const jobs=new NativeCandidateTests(custody,authorize,async(job,abort)=>job.command[0]==="project-check-v1"?
  projectChecks?projectChecks.run(job,base,abort):{outcome:"unavailable",output:"Protected project check unavailable"}:
  witness.checkCandidateFile(source,{...base,...job.candidate},job.command[1]!,job.command[2]!,abort))
const completed=await runCandidateChecks(custody,jobs,testPlanId,candidate,checks,1,signal)
if(!completed.ready)throw Error("Actual immutable candidate check did not pass")
process.stdout.write(JSON.stringify({projectId,assemblyId,testPlanId,checks,candidateId}))
}finally{if(registration)await Service.stop({file:registration})}
