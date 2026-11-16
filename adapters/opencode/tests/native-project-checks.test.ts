// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import { NativeProjectChecks, decodeProjectCheckProfile, bindProjectCheckTemplate, type ProjectCheckProfile } from "../src/native-project-checks.js"

const profile:ProjectCheckProfile={formatVersion:1,platform:"linux",nativeProjectId:"explicit-test-project",sourceRoot:"/fixture/source",worktreeParent:"/fixture/areas",
  envExecutable:{path:"/fixture/env",sha256:"a".repeat(64)},checks:[{id:"test",executable:{path:"/fixture/node",sha256:"b".repeat(64)},
    args:["--test"],environment:{},timeoutMillis:1000,outputBytes:1024}]}
test("protected project-check profile is closed and never accepts task-supplied shell policy",()=>{
  assert.deepEqual(decodeProjectCheckProfile(JSON.stringify(profile)),profile)
  assert.throws(()=>decodeProjectCheckProfile(JSON.stringify({...profile,allowArbitraryShell:true})))
  assert.throws(()=>decodeProjectCheckProfile(JSON.stringify({...profile,checks:[{...profile.checks[0],shell:"untrusted"}]})))
  assert.throws(()=>decodeProjectCheckProfile(JSON.stringify(profile).replace('{','{"formatVersion":1,')))
  assert.throws(()=>decodeProjectCheckProfile(JSON.stringify(profile).replace('"environment":{}','"environment":{"A":"x","\\u0041":"x"}')))
})
test("enrollment template binds the actual native project without accepting a model-selected identity",()=>{
  const {nativeProjectId,...template}=profile
  assert.deepEqual(bindProjectCheckTemplate(JSON.stringify(template),nativeProjectId),profile)
  assert.throws(()=>bindProjectCheckTemplate(JSON.stringify(profile),"other-project"))
  assert.throws(()=>bindProjectCheckTemplate(JSON.stringify(template).replace('{','{"formatVersion":1,'),nativeProjectId))
})
test("unknown selectors and credential-bearing environment refuse before native execution or custody",async()=>{
  let authorizations=0
  const create=(p:ProjectCheckProfile)=>new NativeProjectChecks({} as never,{} as never,{} as never,p,async()=>{authorizations++;return true})
  const request={runId:"10000000-0000-4000-8000-000000000001",candidate:{commit:{algorithm:"sha1" as const,hex:"1".repeat(40)},tree:{algorithm:"sha1" as const,hex:"2".repeat(40)}},
    checkId:"test",command:["project-check-v1","not-approved"],safeRepeat:false,previousRunId:null}
  const base={repositoryId:request.runId,...request.candidate,dirtyContentDigest:null,retainedLocatorId:request.runId}
  assert.equal((await create(profile).run(request,base,new AbortController().signal)).outcome,"unavailable")
  assert.equal((await create(profile).run({...request,command:["project-check-v1","test","arbitrary-argv"]},base,new AbortController().signal)).outcome,"unavailable")
  await assert.rejects(create({...profile,checks:[{...profile.checks[0]!,environment:{OPENAI_API_KEY:"must-not-reach-execution"}}]})
    .run({...request,command:["project-check-v1","test"]},base,new AbortController().signal))
  assert.equal(authorizations,0)
})
