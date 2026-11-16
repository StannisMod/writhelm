// SPDX-License-Identifier: AGPL-3.0-only
// Real service/registry/Git/delivery/lifecycle/report adapters. Native session observation and a
// pre-dispatch fault are explicit synthetic ports; no native prompt/model request is allowed.
import * as fs from "node:fs/promises"
import path from "node:path"
import assert from "node:assert/strict"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { LinuxGitWitness } from "../src/linux-git-witness.js"
import { NativeCandidateReviewLifecycle } from "../src/native-candidate-review.js"
import { advanceCandidateDelivery } from "../src/native-result-retention.js"
import { startCandidateDeliveryLoop } from "../src/native-runtime.js"
import { contextJSON } from "../src/native-context-codec.js"
import { NativeAutomaticCandidateReview } from "../src/native-automatic-review.js"

const cfg=JSON.parse(await fs.readFile(process.argv[2]!,"utf8")),service=cfg.service
const signal=new AbortController().signal
const custody=new LinuxNativeCustody(path.join(cfg.root,"native"),[cfg.sourceRoot]);await custody.initialize()
const ctx={app:{version:"2.2.5"},session:{get:async({sessionID}:{sessionID:string})=>({id:sessionID,location:{directory:cfg.sourceRoot}})}}
const client=new NativeRuntimeClient(service.origin,service.proof,service.storeId,cfg.projectId,service.controllerId,service.hostId)
const registry=new NativeCredentialRegistry(ctx as never,custody,client,service.origin,
  new LinuxWorkRequestCustody(path.join(cfg.root,"requests"),[cfg.sourceRoot]),cfg.epoch)
await registry.adopt(cfg.principalId,cfg.credentialId,service.token)
await registry.bind(cfg.sessionID,cfg.credentialId,cfg.sourceRoot)
const git=new LinuxGitWitness(cfg.git)
const lifecycle=()=>new NativeCandidateReviewLifecycle(registry,{runController:async()=>{throw Error("SYNTHETIC pre-native-dispatch cut; no model authorization")}},custody,cfg.sessionID,cfg.projectId)
const execute=async()=>{throw Error("Checks already have exact receipts; duplicate execution is forbidden")}
const first=lifecycle()
if(cfg.expectRevoked){
  const loop=startCandidateDeliveryLoop(cfg.sourceRoot,custody,git,registry,cfg.sessionID,{profile:cfg.profile,pollMillis:1000,execute,
    canonicalReview:(profile,candidate,selected,abort)=>first.advance(profile,candidate,selected,abort)})
  try{await assert.rejects(loop.initial);assert.equal((loop.status as any).phase,"delivery-held")}
  finally{await loop.close()}
  process.stdout.write(JSON.stringify({revocationHeld:true,modelCalls:0,integrationAllowed:false}));process.exit(0)
}
const waiting=await advanceCandidateDelivery(cfg.sourceRoot,custody,git,registry,cfg.sessionID,cfg.profile,execute,signal,undefined,
  (profile,candidate,selected,abort)=>first.advance(profile,candidate,selected,abort))
assert.equal(waiting.phase,cfg.preparedStart?"review-recorded":"waiting-for-review-assignment")
const ticket=JSON.parse(cfg.ticketJSON)
let automatic:NativeAutomaticCandidateReview
const automaticLifecycle=new NativeCandidateReviewLifecycle(registry,{runController:async()=>{throw Error("SYNTHETIC pre-native-dispatch cut; no model authorization")}},
  custody,cfg.sessionID,cfg.projectId,(...args)=>automatic.prepare(...args))
automatic=new NativeAutomaticCandidateReview(registry,{runController:async()=>({phase:"binding-confirmed",assignmentId:ticket.assignment.assignmentId,
  binding:ticket.binding,execution:ticket.execution})},automaticLifecycle,custody,cfg.sessionID,cfg.projectId) // Synthetic native-binding observation only.
if(!cfg.preparedStart)await assert.rejects(advanceCandidateDelivery(cfg.sourceRoot,custody,git,registry,cfg.sessionID,cfg.profile,execute,signal,undefined,
  (profile,candidate,selected,abort)=>automaticLifecycle.advance(profile,candidate,selected,abort)),/SYNTHETIC pre-native-dispatch/)
const reopened=lifecycle()
const loop=startCandidateDeliveryLoop(cfg.sourceRoot,custody,git,registry,cfg.sessionID,{profile:cfg.profile,pollMillis:1000,execute,
  canonicalReview:(profile,candidate,selected,abort)=>reopened.advance(profile,candidate,selected,abort)})
let completed:Awaited<typeof loop.initial>
try{
  completed=await loop.initial
  assert.equal(completed.phase,"review-recorded");assert.equal(completed.integrationAllowed,false)
  assert.doesNotThrow(()=>contextJSON(loop.status),"Automatic loop status must serialize without Map or test log uploads")
  assert.match((loop.status as any).tests.results[0].receiptDigest,/^[0-9a-f]{64}$/)
  assert.equal(typeof (loop.status as any).selectedJSON,"string")
}finally{await loop.close()}
const again=await advanceCandidateDelivery(cfg.sourceRoot,custody,git,registry,cfg.sessionID,cfg.profile,execute,signal,undefined,
  (profile,candidate,selected,abort)=>reopened.advance(profile,candidate,selected,abort))
assert.equal(again.phase,"review-recorded")
process.stdout.write(JSON.stringify({phase:completed.phase,exactReplay:true,modelCalls:0,integrationAllowed:false}))
