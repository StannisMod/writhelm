// SPDX-License-Identifier: AGPL-3.0-only
// Actual protected custody; canonical service and frontier effects are explicit MOCK ports.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { CanonicalDispatchActivation } from "../src/native-plan-activation.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"

const project="10000000-0000-4000-8000-000000000001",plan="10000000-0000-4000-8000-000000000002"
async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"dispatch-activation-")),source=path.join(root,"source")
  await fs.mkdir(source)
  const custody=new LinuxNativeCustody(path.join(root,"custody"),[source]);await custody.initialize()
  let authorized=true,starts=0,wakes=0,closes=0,observations=0
  const observe=async(planId:string)=>{
    observations++;if(!authorized)throw Error("MOCK service authority revoked or plan unavailable")
    return {planJSON:JSON.stringify({family:"dispatch-plans",key:{projectId:project,id:planId},payload:{}})}
  }
  const create=()=>({start(){starts++},wake(){wakes++},async close(){closes++}})
  return {custody,observe,create,deny(){authorized=false},counts:()=>({starts,wakes,closes,observations})}
}
test("canonical activation coalesces repeated selection and restores reconciler without creating plan or native effects",async()=>{
  const f=await fixture(),a=new CanonicalDispatchActivation(f.custody,project,"ses_controller",f.observe,f.create)
  await Promise.all([a.activate(plan),a.activate(plan)])
  assert.equal(f.counts().starts,1);assert.equal(f.counts().wakes,1)
  assert.equal((await f.custody.scan("dispatch-activation")).length,1)
  await a.close();assert.equal(f.counts().closes,1)
  const reopened=new CanonicalDispatchActivation(f.custody,project,"ses_controller",f.observe,f.create)
  await reopened.restore();assert.equal(f.counts().starts,2)
  f.deny();await assert.rejects(reopened.activate(plan));assert.equal(f.counts().wakes,1)
  await reopened.close();await assert.rejects(reopened.activate(plan))
})
test("wrong project, session, missing canonical authority and aborted activation never start a frontier",async()=>{
  const f=await fixture()
  const wrong=new CanonicalDispatchActivation(f.custody,"20000000-0000-4000-8000-000000000001","ses_controller",f.observe,f.create)
  await assert.rejects(wrong.activate(plan));assert.equal(f.counts().starts,0)
  assert.equal((await f.custody.scan("dispatch-activation")).length,0)
  const a=new CanonicalDispatchActivation(f.custody,project,"ses_controller",f.observe,f.create)
  await assert.rejects(a.activate(plan,AbortSignal.abort()));assert.equal(f.counts().starts,0)
  await a.activate(plan);await a.close()
  const moved=new CanonicalDispatchActivation(f.custody,project,"ses_other",f.observe,f.create)
  await assert.rejects(moved.restore());assert.equal(f.counts().starts,1)
  f.deny()
  const revoked=new CanonicalDispatchActivation(f.custody,project,"ses_controller",f.observe,f.create)
  await assert.rejects(revoked.restore());assert.equal(f.counts().starts,1)
})
