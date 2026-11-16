// SPDX-License-Identifier: AGPL-3.0-only
// Real protected filesystem; service selection/checks and delivery runner are explicit MOCK ports.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { CanonicalCandidateActivation } from "../src/native-candidate-activation.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import type { CandidateDeliveryProfile } from "../src/native-result-retention.js"
import type { GitSnapshot } from "../src/linux-git-witness.js"

const project="10000000-0000-4000-8000-000000000001",plan="10000000-0000-4000-8000-000000000002"
const reference=JSON.stringify({family:"dispatch-plans",key:{projectId:project,id:plan},digest:"a".repeat(64)})
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"candidate-activation-")),source=path.join(root,"source");await fs.mkdir(source)
  const custody=new LinuxNativeCustody(path.join(root,"custody"),[source]);await custody.initialize()
  const head:GitSnapshot={repositoryId:project,commit:{algorithm:"sha1",hex:"1".repeat(40)},tree:{algorithm:"sha1",hex:"2".repeat(40)},dirtyContentDigest:null,retainedLocatorId:plan}
  let ready=false,authorized=true,checks=0,closes=0
  let bases=[head]
  const profiles:CandidateDeliveryProfile[]=[]
  const create=()=>new CanonicalCandidateActivation(custody,project,"ses_controller",async()=>{
    if(!authorized)throw Error("MOCK revoked service selection")
    return ready?{inputs:bases.map(base=>({base}))}:null
  },async()=>{checks++;if(!authorized)throw Error("MOCK missing canonical check plan")},profile=>{
    profiles.push(profile);return {async close(){closes++}}
  })
  return {custody,head,create,profiles,counts:()=>({checks,closes}),ready(){ready=true},deny(){authorized=false},differentBases(){bases=[head,{...head,tree:{algorithm:"sha1",hex:"3".repeat(40)}}]}}
}
test("complete canonical selection activates once and preserves candidate/check operation IDs after restart",async()=>{
  const f=await fixture(),a=f.create()
  assert.equal((await a.activate(reference)).phase,"waiting-for-results")
  assert.equal((await f.custody.scan("candidate-activation")).length,0)
  f.ready();await Promise.all([a.activate(reference),a.activate(reference)])
  assert.deepEqual((await a.activate(reference)).profile,f.profiles[0])
  assert.equal(f.profiles.length,1);assert.equal(f.counts().checks,4)
  await a.close();assert.equal(f.counts().closes,1)
  const b=f.create();await b.restore();assert.equal(f.profiles.length,2)
  assert.deepEqual(f.profiles[1],f.profiles[0])
  f.deny();await assert.rejects(b.activate(reference));assert.equal(f.profiles.length,2)
  await b.close();await assert.rejects(b.activate(reference))
})
test("wrong project, partial/different bases, unknown check plan and cancellation do not dispatch delivery",async()=>{
  const f=await fixture(),a=f.create();f.ready()
  const wrong=reference.replace(project,"20000000-0000-4000-8000-000000000001")
  await assert.rejects(a.activate(wrong));await assert.rejects(a.activate(reference,AbortSignal.abort()))
  f.differentBases();await assert.rejects(a.activate(reference))
  f.deny();await assert.rejects(a.activate(reference))
  assert.equal(f.profiles.length,0);assert.equal((await f.custody.scan("candidate-activation")).length,0)
})
