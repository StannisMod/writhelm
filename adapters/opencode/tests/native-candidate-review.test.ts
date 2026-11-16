// SPDX-License-Identifier: AGPL-3.0-only
// Real protected journal; ticket service and native initial-start ports are explicit MOCKs.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { NativeCandidateReviewLifecycle } from "../src/native-candidate-review.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"

const project="10000000-0000-4000-8000-000000000001",review="10000000-0000-4000-8000-000000000002",assignment="10000000-0000-4000-8000-000000000003"
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"review-lifecycle-")),source=path.join(root,"source");await fs.mkdir(source)
  const custody=new LinuxNativeCustody(path.join(root,"native"),[source]);await custody.initialize()
  const ticket={formatVersion:1,projectId:project,reviewId:review,contextId:review,assignment:{assignmentId:assignment,generation:0},
    binding:{family:"assignment-bindings",key:{projectId:project,assignmentId:assignment,generation:0},digest:"a".repeat(64)}}
  let authorized=true,starts=0,reports=0,reportState:"pending"|"recorded"|"limited"|"blocked"="pending"
  const ids:string[]=[]
  const registry={async issueCandidateReviewTicket(_session:string,ticketJSON:string){
    if(!authorized)throw Error("MOCK current service permission/qualification held")
    return {ticketJSON,ticketDigest:createHash("sha256").update(ticketJSON).digest("hex")}
  },async candidateReviewReport(){if(!authorized)throw Error("MOCK report read revoked");reports++;return {reportJSON:"canonical MOCK report",integrationAllowed:false as const}},
  async candidateReviewStatus(){
    if(!authorized)throw Error("MOCK current service report permission revoked")
    return reportState==="pending"?{kind:"pending" as const}:{kind:"report" as const,integrationAllowed:false as const,reportRefJSON:"exact MOCK canonical ref",
      reportJSON:JSON.stringify({payload:{findings:reportState==="blocked"?[{blocking:true}]:[],limits:reportState==="limited"?["Missing input"]:[]}})}
  }}
  const initial={async runController(selectionJSON:string,startId:string){
    assert.ok(await custody.read("candidate-review-start",review));assert.equal(JSON.parse(selectionJSON).assignment.key.id,assignment)
    starts++;ids.push(startId);return {phase:"held-native-MOCK"}
  }}
  return {custody,ticket,ids,registry,initial,deny(){authorized=false},setReport(value:typeof reportState){reportState=value},counts:()=>({starts,reports}),
    create:()=>new NativeCandidateReviewLifecycle(registry,initial,custody,"ses_controller",project)}
}
test("review lifecycle retains ticket before using the original held start and reauthenticates replay/report",async()=>{
  const f=await fixture(),a=f.create(),signal=new AbortController().signal
  await a.start(JSON.stringify(f.ticket),signal)
  const original=await f.custody.read("candidate-review-start",review)
  const b=f.create();await b.start(JSON.stringify(f.ticket),signal)
  assert.deepEqual(f.ids,[review,review]);assert.equal(await f.custody.read("candidate-review-start",review),original)
  assert.equal((await b.report(review,"exact reference",signal)).integrationAllowed,false)
  f.deny();await assert.rejects(b.start(JSON.stringify(f.ticket),signal));await assert.rejects(b.report(review,"exact reference",signal))
  assert.deepEqual(f.counts(),{starts:2,reports:1})
})
test("automatic final-review selection uses exact candidate/plan/check identities and reauthenticates after reopen",async()=>{
  const f=await fixture(),signal=new AbortController().signal
  const candidate={commit:{algorithm:"sha1" as const,hex:"1".repeat(40)},tree:{algorithm:"sha1" as const,hex:"2".repeat(40)}}
  const plan={family:"dispatch-plans",key:{projectId:project,id:assignment},digest:"b".repeat(64)}
  const selected=[{family:"results",key:{projectId:project,id:review},digest:"c".repeat(64)}]
  const profile={planRefJSON:JSON.stringify(plan),operationId:review,candidateId:assignment,head:{repositoryId:project,...candidate,dirtyContentDigest:null,retainedLocatorId:assignment},
    time:"1790000000 +0000",testPlanId:assignment,capacity:1} as const
  const ticket={...f.ticket,assemblyId:review,checkPlanId:assignment,plan,candidate:profile.head,selected}
  const a=f.create()
  assert.equal((await a.advance(profile,candidate,JSON.stringify(selected),signal)).phase,"waiting-for-review-assignment")
  await a.start(JSON.stringify(ticket),signal)
  assert.equal((await a.advance(profile,candidate,JSON.stringify(selected),signal)).phase,"review-pending")
  const reopened=f.create();f.setReport("recorded")
  assert.equal((await reopened.advance(profile,candidate,JSON.stringify(selected),signal)).phase,"review-recorded")
  f.setReport("limited");assert.equal((await reopened.advance(profile,candidate,JSON.stringify(selected),signal)).phase,"review-limited")
  f.setReport("blocked");assert.equal((await reopened.advance(profile,candidate,JSON.stringify(selected),signal)).phase,"review-blocked")
  await assert.rejects(reopened.advance(profile,{...candidate,tree:{algorithm:"sha1",hex:"9".repeat(40)}},JSON.stringify(selected),signal))
  f.deny();await assert.rejects(reopened.advance(profile,candidate,JSON.stringify(selected),signal))
  assert.equal(f.counts().starts,1,"Status/recovery polling must not start another native context")
})
test("no original, wrong project, aborted request and conflicting ticket do not dispatch another review",async()=>{
  const f=await fixture(),a=f.create(),signal=new AbortController().signal
  await assert.rejects(a.report(review,"reference",signal))
  await assert.rejects(a.start(JSON.stringify(f.ticket),AbortSignal.abort()))
  await assert.rejects(a.start(JSON.stringify({...f.ticket,projectId:review}),signal))
  assert.equal(f.counts().starts,0)
  await a.start(JSON.stringify(f.ticket),signal)
  await assert.rejects(a.start(JSON.stringify({...f.ticket,contextId:assignment}),signal))
  assert.equal(f.counts().starts,1)
})
