// SPDX-License-Identifier: AGPL-3.0-only
// Model-free native lifecycle/authoritative role-progress doubles. No semantic/live role claim.
import test from "node:test"
import assert from "node:assert/strict"
import { NativeRoleContinuation, type RoleProgress } from "../src/native-role-continuation.js"
import { contextJSON } from "../src/native-context-codec.js"
import { createHash } from "node:crypto"
import * as fs from "node:fs/promises"
import path from "node:path"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"

const sessionID="ses_role"
const initial=():RoleProgress=>({assignmentId:"00000000-0000-0000-0000-000000000001",executionGeneration:"0",
  bindingJSON:"exact synthetic binding",nativeReady:true,finishedHandoff:false,
  planJSON:contextJSON({payload:{generation:0,steps:[{id:"step-1",state:"active",blockers:[]}]}}),checkpointJSON:null})
function fixture(){
  const values=new Map<string,string>(),key=(kind:string,id:string)=>kind+":"+id
  const store={read:async(kind:string,id:string)=>values.get(key(kind,id)),
    retain:async(kind:string,id:string,raw:string)=>{
      const k=key(kind,id),previous=values.get(k)
      if(previous!==undefined){assert.equal(previous,raw);return false}
      values.set(k,raw);return true
    },replace:async(kind:string,id:string,expected:string,raw:string)=>{
      const k=key(kind,id),old=values.get(k)
      assert.equal(createHash("sha256").update(old!).digest("hex"),expected);values.set(k,raw);return true
    }}
  let frame=initial(),sends=0,unknown=false,observations=0,flip=false
  const observe=async()=>{
    observations++
    return flip&&observations%2===0?{...frame,nativeReady:false}:frame
  }
  const send=async(input:any)=>{
    sends++;if(unknown)throw Error("synthetic lost native reply")
    return {type:"synthetic",sessionID:input.sessionID,id:input.id,payload:{text:input.text,metadata:input.metadata}}
  }
  const make=()=>new NativeRoleContinuation(store,observe,send)
  return {make,values,sends:()=>sends,set:(value:Partial<RoleProgress>)=>{frame={...frame,...value}},
    lose:()=>{unknown=true},flip:()=>{flip=true}}
}
const idle={sessionID,background:[]}
test("premature idle gets one retained native continuation, then stalls rather than looping or minting IDs",async()=>{
  const f=fixture(),roles=f.make()
  assert.equal(await roles.idle(idle),"continued");assert.equal(f.sends(),1)
  assert.equal(await roles.idle(idle),"stalled");assert.equal(f.sends(),1)
  assert.equal(await f.make().idle(idle),"stalled");assert.equal(f.sends(),1,"reload cannot reset no-progress control")
  assert.equal([...f.values.keys()].filter(k=>k.startsWith("role-continuation-original")).length,1)
})
test("semantic step progress admits the next continuation; cosmetic plan churn does not",async()=>{
  const f=fixture(),roles=f.make();await roles.idle(idle)
  f.set({planJSON:contextJSON({payload:{generation:99,reason:"rewritten explanation",steps:[{id:"step-1",state:"active",blockers:[]}]}})})
  assert.equal(await roles.idle(idle),"stalled");assert.equal(f.sends(),1)
  f.set({planJSON:contextJSON({payload:{generation:100,steps:[{id:"step-1",state:"complete",blockers:[]}]}})})
  assert.equal(await roles.idle(idle),"continued");assert.equal(f.sends(),2,"finished steps still need durable outcome/handoff")
})
test("service finishedHandoff ends worker/reviewer continuation; prose is not consulted",async()=>{
  const f=fixture(),roles=f.make()
  f.set({finishedHandoff:true,nativeReady:false})
  assert.equal(await roles.idle(idle),"finished");assert.equal(f.sends(),0)
  assert.equal(f.values.size,0)
})
test("native background, withdrawn readiness and changed authority never create a synthetic wake",async()=>{
  const f=fixture(),roles=f.make()
  assert.equal(await roles.idle({sessionID,background:[{sessionID}]}),"background-wait")
  f.set({nativeReady:false});assert.equal(await roles.idle(idle),"admission-held")
  assert.equal(f.sends(),0)
  const changed=fixture();changed.flip()
  assert.equal(await changed.make().idle(idle),"admission-held");assert.equal(changed.sends(),0)
})
test("canonical external wait differs from premature finish; pending effects remain an unknown-effect hold",async()=>{
  const f=fixture(),roles=f.make()
  f.set({planJSON:contextJSON({payload:{steps:[{id:"step-1",state:"blocked",blockers:[{kind:"work",reference:"exact"}]}]}}),
    checkpointJSON:contextJSON({payload:{pending:[{id:"pending-1",kind:"decision",source:{kind:"work",reference:"exact"}}],
      result:null,retrospective:null,notification:null}})})
  assert.equal(await roles.idle(idle),"external-wait");assert.equal(f.sends(),0)
  f.set({checkpointJSON:contextJSON({payload:{pending:[{id:"pending-1",kind:"effect",source:null}],
    result:null,retrospective:null,notification:null}})})
  assert.equal(await roles.idle(idle),"unknown-effect-held");assert.equal(f.sends(),0)
  f.set({planJSON:initial().planJSON,checkpointJSON:null})
  assert.equal(await roles.idle(idle),"continued","resolved wait resumes on the next admitted native boundary")
})
test("a lost native admission reply never resends the same or a newly minted continuation after reopen",async()=>{
  const f=fixture();f.lose()
  assert.equal(await f.make().idle(idle),"dispatch-unknown")
  assert.equal(await f.make().idle(idle),"dispatch-unknown")
  f.set({planJSON:contextJSON({payload:{steps:[{id:"step-1",state:"complete",blockers:[]}]}})})
  assert.equal(await f.make().idle(idle),"dispatch-unknown","changed progress cannot bypass a prior unknown native admission")
  assert.equal(f.sends(),1)
})
test("only pinned pre-idle hook is registered; disposal cancels future continuation",async()=>{
  const f=fixture(),roles=f.make();let hookName="",callback:any,disposed=false
  const registration=await roles.register({session:{hook:async(name:string,fn:any)=>{
    hookName=name;callback=fn;return {dispose:async()=>{disposed=true}}
  }}} as never)
  assert.equal(hookName,"experimental.idle")
  await registration.dispose();assert.equal(disposed,true)
  await callback(idle);assert.equal(f.sends(),0)
})

test("unknown dispatch remains held in real protected custody after reopen and canonical progress",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"role-continuation-")),work=path.join(root,"work")
  await fs.mkdir(work)
  const target=path.join(root,"private")
  let frame=initial(),sends=0
  const custody=new LinuxNativeCustody(target,[work]);await custody.initialize()
  const first=new NativeRoleContinuation(custody,async()=>frame,async()=>{sends++;throw Error("lost native admission")})
  assert.equal(await first.idle(idle),"dispatch-unknown")
  frame={...frame,planJSON:contextJSON({payload:{steps:[{id:"step-1",state:"complete",blockers:[]}]}})}
  const restoredCustody=new LinuxNativeCustody(target,[work]);await restoredCustody.initialize()
  const restored=new NativeRoleContinuation(restoredCustody,async()=>frame,async()=>{sends++;throw Error("must not resend")})
  assert.equal(await restored.idle(idle),"dispatch-unknown");assert.equal(sends,1)
})
