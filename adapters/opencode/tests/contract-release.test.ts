// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import {createHash} from "node:crypto"
import {contextJSON} from "../src/native-context-codec.js"
import {contractBundleDigest,selectedContractSchemas,selectContractRelease} from "../src/contract-release.js"
import type {LinuxNativeCustody} from "../src/native-runtime-custody.js"
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
const schema=(source:string)=>JSON.stringify({formatVersion:1,kind:"canonical-codec-source-v1",namespace:"knowledge-command1",
  notice:"Synthetic diagnostic source only.",files:[{path:"codec/Synthetic.kt",sha256:hash(source),source}]})
function fixture(){
  const entries=new Map<string,string>(),writes:string[]=[]
  let fail:"before"|"after"|undefined
  const key=(kind:string,id:string)=>kind+":"+id
  const storage={
    read:async(kind:string,id:string)=>entries.get(key(kind,id)),
    retain:async(kind:string,id:string,raw:string)=>{
      if(kind==="contract-selection"&&fail==="before"){fail=undefined;throw Error("MOCK selection not sent")}
      const name=key(kind,id),old=entries.get(name)
      if(old!==undefined){assert.equal(old,raw,"immutable original conflict");return false}
      entries.set(name,raw);writes.push(kind)
      if(kind==="contract-selection"&&fail==="after"){fail=undefined;throw Error("MOCK lost selection acknowledgement")}
      return true
    },
    replace:async(kind:string,id:string,expected:string,raw:string)=>{
      assert.equal(hash(entries.get(key(kind,id))!),expected,"CAS conflict")
      entries.set(key(kind,id),raw);writes.push(kind);return true
    },
  } as unknown as Pick<LinuxNativeCustody,"read"|"retain"|"replace">
  const old={"knowledge-command1":schema("original source")},next={"knowledge-command1":schema("new source")}
  const raw=contextJSON({formatVersion:1,storeId:"00000000-0000-0000-0000-000000000001",schemas:old})
  const input={expectedBootstrapDigest:hash(raw),previousBundleDigest:contractBundleDigest(old),previousReleaseDigest:null,schemas:next}
  return {entries,writes,storage,old,next,raw,input,fail:(phase:"before"|"after")=>{fail=phase}}
}
test("explicit exact release precedes selection; bootstrap/old bundles remain unchanged and retry keeps identity",async()=>{
  const f=fixture()
  assert.deepEqual(await selectedContractSchemas(f.storage,f.raw),f.old)
  const first=await selectContractRelease(f.storage,f.raw,f.input)
  assert.deepEqual(f.writes,["contract-release","contract-selection"])
  assert.deepEqual(await selectedContractSchemas(f.storage,f.raw),f.next)
  assert.deepEqual(await selectContractRelease(f.storage,f.raw,f.input),first)
  assert.equal(f.entries.size,2);assert.equal(f.writes.length,2)
  const third={"knowledge-command1":schema("third source")}
  await assert.rejects(selectContractRelease(f.storage,f.raw,{...f.input,schemas:third}))
  const second=await selectContractRelease(f.storage,f.raw,{...f.input,schemas:third,
    previousBundleDigest:first.bundleDigest,previousReleaseDigest:first.releaseDigest})
  assert.notEqual(second.releaseDigest,first.releaseDigest)
  assert.deepEqual(await selectedContractSchemas(f.storage,f.raw),third)
  assert.ok(f.entries.has("contract-release:"+first.releaseDigest))
  assert.deepEqual(JSON.parse(f.raw).schemas,f.old)
})
test("prepared orphan is not selected; missing acknowledgement reconciles only the same original release",async()=>{
  for(const phase of ["before","after"] as const){
    const f=fixture();f.fail(phase)
    await assert.rejects(selectContractRelease(f.storage,f.raw,f.input),/MOCK/)
    assert.deepEqual(await selectedContractSchemas(f.storage,f.raw),phase==="before"?f.old:f.next)
    assert.equal([...f.entries.keys()].filter(k=>k.startsWith("contract-release:")).length,1)
    const result=await selectContractRelease(f.storage,f.raw,f.input)
    assert.deepEqual(await selectedContractSchemas(f.storage,f.raw),f.next)
    assert.equal(result.bundleDigest,contractBundleDigest(f.next));assert.equal(f.entries.size,2)
  }
})
test("bootstrap, source, bounds, predecessor and selected-record corruption refuse without fallback",async()=>{
  const f=fixture()
  for(const input of [
    {...f.input,expectedBootstrapDigest:"0".repeat(64)},
    {...f.input,previousBundleDigest:"0".repeat(64)},
    {...f.input,previousReleaseDigest:"0".repeat(64)},
    {...f.input,schemas:{"knowledge-command1":schema("x").replace(hash("x"),"0".repeat(64))}},
    {...f.input,schemas:{"knowledge-command1":schema("x".repeat(262145))}},
    {...f.input,schemas:{"unknown-new-contract":schema("new")}},
  ])await assert.rejects(selectContractRelease(f.storage,f.raw,input))
  assert.equal(f.entries.size,0)
  const selected=await selectContractRelease(f.storage,f.raw,f.input)
  await assert.rejects(selectedContractSchemas(f.storage,f.raw.replace("000000000001","000000000002")))
  const key="contract-release:"+selected.releaseDigest,original=f.entries.get(key)!
  f.entries.set(key,original.replace("new source","different"))
  await assert.rejects(selectedContractSchemas(f.storage,f.raw))
  f.entries.set(key,original)
  const pointer=f.entries.get("contract-selection:current")!
  f.entries.set("contract-selection:current",pointer.replace(selected.bundleDigest,"0".repeat(64)))
  await assert.rejects(selectedContractSchemas(f.storage,f.raw))
  f.entries.delete(key)
  await assert.rejects(selectedContractSchemas(f.storage,f.raw))
})
