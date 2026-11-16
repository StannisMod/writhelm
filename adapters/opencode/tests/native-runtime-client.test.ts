// SPDX-License-Identifier: AGPL-3.0-only
import { test } from "node:test"
import assert from "node:assert/strict"
import { createServer } from "node:http"
import { createHash } from "node:crypto"
import { NativeRuntimeClient } from "../src/native-runtime-client.js"

const id = "00000000-0000-0000-0000-000000000001"
const token = `wh1.${id}.${id}.${Buffer.alloc(32, 1).toString("base64url")}`
const proof = Buffer.alloc(32, 2).toString("base64url")
const subject = { assignmentId: id, executionGeneration: "9223372036854775807", bindingJSON: "exact retained binding" }

test("review ticket transport uses authenticated candidate route and rejects changed subject or digest",async()=>{
  const ticketJSON=JSON.stringify({formatVersion:1,reviewId:id,contextId:id}) // Explicit MOCK service ticket, protocol test only.
  let mode="valid",requests=0
  const server=createServer(async(request,response)=>{
    requests++;assert.equal(request.url,"/internal/native/candidate/v1")
    assert.equal(request.headers.authorization,`Bearer ${token}`)
    assert.equal(request.headers["x-writhelm-native-proof"],proof)
    const chunks:Buffer[]=[];for await(const part of request)chunks.push(Buffer.from(part))
    const input=JSON.parse(Buffer.concat(chunks).toString("utf8"))
    assert.equal(input.operation,"candidate-review-ticket");assert.equal(input.ticketJSON,ticketJSON)
    const returned=mode==="changed"?JSON.stringify({formatVersion:1,reviewId:id,contextId:"different"}):ticketJSON
    response.setHeader("Content-Type","application/json")
    response.end(JSON.stringify({formatVersion:1,kind:"candidate-review-ticket",ticketJSON:returned,
      ticketDigest:mode==="bad-digest"?"0".repeat(64):createHash("sha256").update(returned).digest("hex")}))
  })
  await new Promise<void>(resolve=>server.listen(0,"127.0.0.1",resolve))
  try{
    const address=server.address();assert.ok(address&&typeof address!=="string")
    const client=new NativeRuntimeClient(`http://127.0.0.1:${address.port}`,proof,id,id,id,id)
    assert.equal((await client.issueCandidateReviewTicket(token,ticketJSON)).ticketJSON,ticketJSON)
    mode="changed";await assert.rejects(client.issueCandidateReviewTicket(token,ticketJSON))
    mode="bad-digest";await assert.rejects(client.issueCandidateReviewTicket(token,ticketJSON))
    const before=requests;await assert.rejects(client.issueCandidateReviewTicket(token,"x".repeat(262145)));assert.equal(requests,before)
  }finally{await new Promise<void>((resolve,reject)=>server.close(error=>error?reject(error):resolve()))}
})

test("real local HTTP client preserves exact generation and refuses inconsistent service permits", async () => {
  let reply: Record<string, unknown> = { formatVersion: 1, basis: {}, active: true, prepare: false,
    dispatch: false, pause: false, delivery: false, quiescent: false, freshEnvelope: false, freshPause: false, bindingJSON: subject.bindingJSON, nativeHostId: id,
    nativeSessionId: "ses_fixture", intentId: null, intentOperation: null, attemptId: null, envelopeDigest: null }
  let received: Record<string, unknown> | undefined
  let rateLimited = 0
  const server = createServer(async (request, response) => {
    assert.equal(request.url, "/internal/native/v1")
    assert.equal(request.headers.authorization, `Bearer ${token}`)
    assert.equal(request.headers["x-writhelm-native-proof"], proof)
    const parts: Buffer[] = []
    for await (const part of request) parts.push(Buffer.from(part))
    received = JSON.parse(Buffer.concat(parts).toString("utf8"))
    if (rateLimited > 0) { rateLimited--; response.writeHead(429); response.end("{}"); return }
    response.setHeader("Content-Type", "application/json")
    response.end(JSON.stringify(reply))
  })
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve))
  try {
    const address = server.address()
    assert.ok(address && typeof address !== "string")
    const client = new NativeRuntimeClient(`http://127.0.0.1:${address.port}`, proof, id, id, id, id)
    assert.equal((await client.status(token, subject)).active, true)
    assert.equal(received?.executionGeneration, subject.executionGeneration)
    const valid = { ...reply }
    for (const invalid of [
      { bindingJSON: null }, { bindingJSON: "different binding" }, { nativeHostId: null },
      { intentOperation: "invented" }, { intentOperation: "pause" },
      { quiescent: true },
    ]) {
      reply = { ...valid, ...invalid }
      await assert.rejects(client.status(token, subject), /unavailable or forbidden/)
    }
    reply = { ...valid, active: false, bindingJSON: null, nativeHostId: null, nativeSessionId: null }
    assert.equal((await client.status(token, subject)).active, false)
    reply = valid; rateLimited = 3 // more than the former two retries; read-only admission still recovers
    assert.equal((await client.status(token, subject)).active, true) // bounded read recapture, not an effect retry
    const preparationId="11111111-1111-4111-8111-111111111111",startId="22222222-2222-4222-8222-222222222222"
    const task={projectId:id,taskId:id,revision:0},manifest={family:"input-manifests",key:{projectId:id,id},digest:"a".repeat(64)},profile={projectId:id,profileId:id,revision:0},resource={projectId:id,resourceId:id}
    const plan={family:"dispatch-plans",key:{projectId:id,id},payload:{projectId:id,planId:id,controllerId:id,entries:[{preparationId,startId,task,manifest,profile,resources:[resource]}]}}
    const entry={preparationId,startId,futureReady:false,claimsAvailable:true,closedJSON:null,taskJSON:JSON.stringify({family:"task-revisions",key:task}),selectionJSON:JSON.stringify({task,manifest,profile,resources:[{resource,expectedGeneration:null}],reviewSubjects:[]})}
    const digest=createHash("sha256").update(JSON.stringify(plan)).digest("hex")
    const attempt={family:"dispatch-attempts",key:{projectId:id,id:preparationId},payload:{preparationId,plan:{family:"dispatch-plans",key:{projectId:id,id},digest}}}
    reply={formatVersion:1,basis:{},planJSON:JSON.stringify(plan),entries:[entry],activeAssignments:[],attemptsJSON:[JSON.stringify(attempt)]}
    assert.equal((await client.dispatchContext(token,id)).entries[0].preparationId,preparationId)
    assert.equal(received?.operation,"dispatch-context")
    reply={...reply,attemptsJSON:[JSON.stringify({...attempt,key:{projectId:id,id:startId}})]}
    await assert.rejects(client.dispatchContext(token,id),/unavailable or forbidden/)
    reply={...reply,attemptsJSON:[JSON.stringify(attempt)]}
    reply={...reply,entries:[{...entry,startId:preparationId}]}
    await assert.rejects(client.dispatchContext(token,id),/unavailable or forbidden/)
    reply={...reply,entries:[{...entry,selectionJSON:JSON.stringify({task,manifest,profile,resources:[],reviewSubjects:[]})}]}
    await assert.rejects(client.dispatchContext(token,id),/unavailable or forbidden/)
    reply={...reply,planJSON:JSON.stringify({...plan,key:{projectId:id,id:preparationId}}),entries:[entry]}
    await assert.rejects(client.dispatchContext(token,id),/unavailable or forbidden/)
    reply={formatVersion:1,kind:"hold"}
    assert.equal(await client.reservationRetryEligibility(token,"original reservation"),"hold")
    assert.equal(received?.operation,"reservation-retry");assert.equal(received?.reservationJSON,"original reservation")
    reply={formatVersion:1,kind:"definitive-refusal"}
    assert.equal(await client.reservationRetryEligibility(token,"original reservation"),"definitive-refusal")
    reply={formatVersion:1,kind:"retry-now"}
    await assert.rejects(client.reservationRetryEligibility(token,"original reservation"),/unavailable or forbidden/)
    const cancel = new AbortController(); cancel.abort()
    await assert.rejects(client.status(token, subject, null, cancel.signal))
  } finally { await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())) }
})

test("receipt channel is separately bounded and carries escaped full-size commands",async()=>{
  let requests=0,route="",received=""
  const fetcher=(async(url,init)=>{
    requests++;route=String(url);received=String(init?.body)
    return new Response(JSON.stringify({formatVersion:1,kind:"missing"}),{status:200})
  }) as typeof fetch
  const client=new NativeRuntimeClient("http://127.0.0.1:12345",proof,id,id,id,id,fetcher)
  const command="\\".repeat(2*1024*1024)
  assert.equal(await client.originalResultReceipt(token,command),"missing")
  assert.equal(route,"http://127.0.0.1:12345/internal/native/receipt/v1")
  assert.equal(JSON.parse(received).commandJSON,command)
  assert.ok(Buffer.byteLength(received)>2*1024*1024)
  await assert.rejects(client.originalResultReceipt(token,command+"x"))
  assert.equal(requests,1)
})

test("dependent projection checks the exact plan, materialization, closure and successor manifest",async()=>{
  const prep="11111111-1111-4111-8111-111111111111",start="22222222-2222-4222-8222-222222222222"
  const matId="33333333-3333-4333-8333-333333333333",closeId="44444444-4444-4444-8444-444444444444"
  const task={projectId:id,taskId:id,revision:0},nextTask={...task,revision:1}
  const originalManifest={family:"input-manifests",key:{projectId:id,id},digest:"a".repeat(64)}
  const profile={projectId:id,profileId:id,revision:0},resource={projectId:id,resourceId:id}
  const plan={family:"dispatch-plans",key:{projectId:id,id},payload:{projectId:id,planId:id,controllerId:id,
    entries:[{preparationId:prep,startId:start,task,manifest:originalManifest,profile,resources:[resource],futureDependencies:[{producer:task,readiness:"implemented"}]}]}}
  const planJSON=JSON.stringify(plan),ref=(family:string,key:unknown,raw:string)=>({family,key,digest:createHash("sha256").update(raw).digest("hex")})
  const materialization={family:"dispatch-materializations",key:{projectId:id,id:matId},payload:{requestKey:matId,operation:"materialize_dispatch_dependency",
    payload:{plan:ref("dispatch-plans",plan.key,planJSON),task,newTask:nextTask}}}
  const materializationJSON=JSON.stringify(materialization),manifest={family:"input-manifests",key:{projectId:id,id:closeId},payload:{task:nextTask,knownGaps:[]}}
  const manifestJSON=JSON.stringify(manifest),closure={family:"dispatch-closures",key:{projectId:id,id:closeId},payload:{command:{requestKey:closeId,
    operation:"close_dispatch_inputs",payload:{materialization:ref("dispatch-materializations",materialization.key,materializationJSON),manifest:manifest.key}}}}
  const next={task:nextTask,manifest:ref("input-manifests",manifest.key,manifestJSON),profile,resources:[{resource,expectedGeneration:null}],reviewSubjects:[]}
  const closed={materializationJSON,closureJSON:JSON.stringify(closure),taskJSON:JSON.stringify({family:"task-revisions",key:nextTask}),manifestJSON,selectionJSON:JSON.stringify(next)}
  const entry={preparationId:prep,startId:start,futureReady:true,claimsAvailable:true,closedJSON:JSON.stringify(closed),
    taskJSON:JSON.stringify({family:"task-revisions",key:task}),selectionJSON:JSON.stringify({task,manifest:originalManifest,profile,resources:[{resource,expectedGeneration:null}],reviewSubjects:[]})}
  let reply={formatVersion:1,basis:{},planJSON,entries:[entry],activeAssignments:[],attemptsJSON:[]}
  const client=new NativeRuntimeClient("http://127.0.0.1:12345",proof,id,id,id,id,(async()=>new Response(JSON.stringify(reply))) as typeof fetch)
  assert.equal((await client.dispatchContext(token,id)).entries[0].closedJSON,entry.closedJSON)
  reply={...reply,entries:[{...entry,closedJSON:JSON.stringify({...closed,manifestJSON:JSON.stringify({...manifest,payload:{...manifest.payload,knownGaps:["open"]}})})}]}
  await assert.rejects(client.dispatchContext(token,id))
  reply={...reply,entries:[{...entry,closedJSON:JSON.stringify({...closed,materializationJSON:materializationJSON.replace('"revision":1','"revision":2')})}]}
  await assert.rejects(client.dispatchContext(token,id))
})
