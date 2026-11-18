// SPDX-License-Identifier: AGPL-3.0-only
// Pure operational custody double; no owner UI, service authorization or native effect is claimed.
import test from "node:test"
import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import { NativeOwnerOrder, OwnerOrderHold } from "../src/native-owner-order.js"
import { contextJSON } from "../src/native-context-codec.js"

const id=(n:number)=>`00000000-0000-0000-0000-${n.toString(16).padStart(12,"0")}`
const scope={storeId:id(50),projectId:id(1),controllerId:id(2),sessionID:"ses_order"}
const plan=contextJSON({family:"dispatch-plans",key:{projectId:id(1),id:id(3)},digest:"a".repeat(64)})
const task=(n:number,revision=0)=>contextJSON({family:"task-revisions",
  key:{projectId:id(1),taskId:id(n),revision},digest:"b".repeat(64)})
const selection=(proposalId=id(9))=>({proposalId,planRefJSON:plan,
  tasks:[{taskJSON:task(4),after:[]},{taskJSON:task(5),after:[id(4)]}]})
const hash=(s:string)=>createHash("sha256").update(s).digest("hex")
function fixture(){
  const values=new Map<string,string>(),key=(kind:string,id:string)=>kind+"\n"+id
  let cut=false
  const store={
    read:async(kind:string,id:string)=>values.get(key(kind,id)),
    retain:async(kind:string,id:string,raw:string)=>{
      if(kind==="owner-order-current"&&cut)throw Error("synthetic publication cut")
      const k=key(kind,id),old=values.get(k)
      if(old!==undefined){if(old!==raw)throw Error("immutable conflict");return false}
      values.set(k,raw);return true
    },
    replace:async(kind:string,id:string,expected:string,raw:string)=>{
      const k=key(kind,id),old=values.get(k)
      if(old===undefined||hash(old)!==expected)throw Error("CAS conflict")
      values.set(k,raw);return true
    },
  }
  return {values,store,gate:new NativeOwnerOrder(store,scope),cut:(value:boolean)=>{cut=value}}
}
const held=(code:string)=>(e:unknown)=>e instanceof OwnerOrderHold&&e.code===code
test("proposal is not owner choice; missing, changed and unselected task admission refuse",async()=>{
  const f=fixture(),proposal=await f.gate.propose(selection())
  await assert.rejects(f.gate.admit(plan,task(4),async()=>"started"),held("OWNER_ORDER_REQUIRED"))
  await assert.rejects(f.gate.select(proposal.proposalId,"c".repeat(64),null),held("OWNER_ORDER_PROPOSAL_CHANGED"))
  const decision=await f.gate.select(proposal.proposalId,proposal.proposalDigest,null)
  assert.equal(await f.gate.admit(plan,task(4),async()=>"not-started"),decision.decisionDigest)
  await assert.rejects(f.gate.admit(plan,task(6),async()=>"started"),held("OWNER_ORDER_TASK_NOT_SELECTED"))
  await assert.rejects(f.gate.admit(plan,task(4,1),async()=>"started"),held("OWNER_ORDER_TASK_NOT_SELECTED"))
  await assert.rejects(f.gate.admit(plan.replace("a".repeat(64),"c".repeat(64)),undefined,async()=>"started"),
    held("OWNER_ORDER_PLAN_CHANGED"))
})
test("only explicit launch ordering edges constrain parallelism; unknown predecessor holds",async()=>{
  const f=fixture(),proposal=await f.gate.propose(selection())
  await f.gate.select(proposal.proposalId,proposal.proposalDigest,null)
  await assert.rejects(f.gate.admit(plan,task(5),async()=>"not-started"),held("OWNER_ORDER_PREDECESSOR_PENDING"))
  await assert.rejects(f.gate.admit(plan,task(5),async()=>"unknown"),held("OWNER_ORDER_PREDECESSOR_UNKNOWN"))
  await f.gate.admit(plan,task(5),async prior=>{assert.equal(prior,task(4));return "started"})
  const parallel=await f.gate.propose({...selection(id(10)),tasks:[{taskJSON:task(4),after:[]},{taskJSON:task(5),after:[]}]})
  const previous=await f.gate.current()
  await f.gate.select(parallel.proposalId,parallel.proposalDigest,previous.decisionDigest)
  await f.gate.admit(plan,task(5),async()=>{throw Error("ordinary list order is not a dependency")})
})
test("exact decision recovers; a publication cut never authorizes a proposal",async()=>{
  const f=fixture(),proposal=await f.gate.propose(selection())
  f.cut(true)
  await assert.rejects(f.gate.select(proposal.proposalId,proposal.proposalDigest,null))
  const restored=new NativeOwnerOrder(f.store,scope)
  await assert.rejects(restored.current(),held("OWNER_ORDER_REQUIRED"))
  f.cut(false)
  const selected=await restored.select(proposal.proposalId,proposal.proposalDigest,null)
  assert.equal((await new NativeOwnerOrder(f.store,scope).current()).decisionDigest,selected.decisionDigest)
  assert.deepEqual(await restored.select(proposal.proposalId,proposal.proposalDigest,null),selected)
  await assert.rejects(new NativeOwnerOrder(f.store,{...scope,sessionID:"ses_other"}).current(),held("OWNER_ORDER_REQUIRED"))
})
test("stale native views cannot overwrite a newer choice; decision changes during observation hold",async()=>{
  const f=fixture(),a=await f.gate.propose(selection()),b=await f.gate.propose(selection(id(10)))
  const first=await f.gate.select(a.proposalId,a.proposalDigest,null)
  await assert.rejects(f.gate.select(b.proposalId,b.proposalDigest,null),held("OWNER_ORDER_SELECTION_STALE"))
  await assert.rejects(f.gate.admit(plan,task(5),async()=>{
    await f.gate.select(b.proposalId,b.proposalDigest,first.decisionDigest);return "started"
  }),held("OWNER_ORDER_SELECTION_CHANGED"))
  await assert.rejects(f.gate.select(a.proposalId,a.proposalDigest,first.decisionDigest),held("OWNER_ORDER_SELECTION_STALE"))
})
test("cycles, duplicate identities, foreign tasks and implicit invalid edges refuse before retention",async()=>{
  for(const tasks of [
    [{taskJSON:task(4),after:[id(4)]}],
    [{taskJSON:task(4),after:[id(5)]},{taskJSON:task(5),after:[id(4)]}],
    [{taskJSON:task(4),after:[]},{taskJSON:task(4),after:[]}],
    [{taskJSON:task(4),after:[id(99)]}],
    [{taskJSON:task(4).replace(id(1),id(99)),after:[]}],
  ]){
    const f=fixture()
    await assert.rejects(f.gate.propose({...selection(),tasks}))
    assert.equal(f.values.size,0)
  }
})
