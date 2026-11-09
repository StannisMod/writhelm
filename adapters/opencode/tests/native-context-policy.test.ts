// SPDX-License-Identifier: AGPL-3.0-only
// Pure normalized-config and SDK-shaped fixtures. No installed host, config write or model call.
import test from "node:test"
import assert from "node:assert/strict"
import type { OpenCodeClient,ConfigEntry } from "@opencode/client"
import { assertNativeContextPolicy,NativeContextPolicy } from "../src/native-context-policy.js"
import type { LinuxNativeCustody } from "../src/native-runtime-custody.js"

const keyJSON='{"profileId":"MOCK-profile","projectId":"MOCK-project","revision":0}'
const profile=JSON.stringify({family:"profiles",key:JSON.parse(keyJSON),payload:{platform:"linux",nativeProfileId:"MOCK-native",compactionPolicyId:"MOCK-policy",contextTokens:1000}})
const policy=JSON.stringify({formatVersion:1,profileKeyJSON:keyJSON,policyId:"MOCK-policy",nativeProfileId:"MOCK-native",auto:true,background:false,keepTokens:100,bufferTokens:200})
const entries=():ConfigEntry[]=>[{type:"document",info:{compaction:{auto:true,keep:{tokens:100},buffer:200},experimental:{background_compaction:false}}}]

test("foreground policy requires explicit matching native keep/buffer/auto and never accepts inherited defaults or background",()=>{
  assert.doesNotThrow(()=>assertNativeContextPolicy(entries(),profile,policy))
  for(const altered of [
    [],[{type:"document",info:{}}],
    [{type:"document",info:{compaction:{auto:true,keep:{tokens:100},buffer:200},experimental:{background_compaction:true}}}],
    [{type:"document",info:{compaction:{auto:true,keep:{tokens:100},buffer:"20%"},experimental:{background_compaction:false}}}],
    [...entries(),{type:"document",info:{compaction:{auto:false}}}],
    [...entries(),{type:"document",info:{compaction:{buffer:201}}}],
    [...entries(),{type:"document",info:{compaction:{keep:{tokens:99}}}}],
  ])assert.throws(()=>assertNativeContextPolicy(altered as ConfigEntry[],profile,policy))
  for(const change of [{profileKeyJSON:"another-profile"},{nativeProfileId:"another-native"},{policyId:"another-policy"},{bufferTokens:900},{auto:false},{background:true}])
    assert.throws(()=>assertNativeContextPolicy(entries(),profile,JSON.stringify({...JSON.parse(policy),...change})))
  assert.throws(()=>assertNativeContextPolicy(entries(),JSON.stringify({...JSON.parse(profile),payload:{...JSON.parse(profile).payload,platform:"windows"}}),policy))
})

test("native policy probe uses the exact current location, performs only reads and never projects provider secrets",async()=>{
  let root="/MOCK-work",reads=0,selection:unknown
  const native={session:{get:async()=>({id:"ses_policy",location:{directory:root}})},config:{get:async(input:any)=>{
    reads++;assert.deepEqual(input,{location:{directory:"/MOCK-work"}})
    return [...entries(),{type:"document",info:{providers:{MOCK:{secret:"MUST_NOT_BE_RETAINED"}}}}]
  }}} as unknown as OpenCodeClient
  const deployment={read:async(kind:string,key:string)=>{selection=[kind,key];return policy}} as unknown as LinuxNativeCustody
  const probe=new NativeContextPolicy(native,deployment)
  assert.equal(await probe.check("ses_policy","/MOCK-work",profile),undefined)
  assert.deepEqual(selection,["native-context-policy",keyJSON]);assert.equal(reads,1)
  root="/MOCK-moved";await assert.rejects(probe.check("ses_policy","/MOCK-work",profile));assert.equal(reads,1)
  const missing=new NativeContextPolicy(native,{read:async()=>undefined} as unknown as LinuxNativeCustody)
  await assert.rejects(missing.check("ses_policy","/MOCK-work",profile));assert.equal(reads,1)
})
