// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import * as fs from "node:fs/promises"
import path from "node:path"
import { ReviewDraftStore, type DraftScope } from "../src/review-drafts.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
const scope:DraftScope={storeId:"store",projectId:"project",controllerId:"controller",sessionID:"ses_draft",
  requestId:"request",requestDigest:"a".repeat(64),packetDigest:"b".repeat(64)}
const items=new Set(["point-a","point-b"])
const content={comments:[{itemId:"point-a",text:"Уточнить этот пункт.\r\nБез изменения смысла."}],reason:""}
function memory(){
  const rows=new Map<string,string>(),key=(kind:string,k:string)=>kind+":"+k
  return {rows,read:async(kind:string,k:string)=>rows.get(key(kind,k)),
    retain:async(kind:string,k:string,raw:string)=>{assert.equal(rows.has(key(kind,k)),false);rows.set(key(kind,k),raw);return true},
    replace:async(kind:string,k:string,expected:string,raw:string)=>{
      assert.equal(hash(rows.get(key(kind,k))!),expected);rows.set(key(kind,k),raw);return true
    }}
}
test("draft exact scope survives reopen; every request/chat/version discriminator isolates content",async()=>{
  const custody=memory(),store=new ReviewDraftStore(custody)
  const saved=await store.save(scope,"",content,items)
  assert.deepEqual(await new ReviewDraftStore(custody).load(scope,items),{digest:saved.digest,...content})
  for(const key of Object.keys(scope) as (keyof DraftScope)[]){
    const other={...scope,[key]:scope[key]+"changed"}
    assert.deepEqual(await store.load(other,items),{digest:"",comments:[],reason:""},key)
  }
})
test("concurrent differing drafts refuse, identical original replay reconciles lost acknowledgement",async()=>{
  const custody=memory(),a=new ReviewDraftStore(custody),b=new ReviewDraftStore(custody)
  const first=await a.save(scope,"",content,items)
  assert.deepEqual(await b.save(scope,"",content,items),first)
  await assert.rejects(b.save(scope,"",{comments:[],reason:"Another edit"},items))
  assert.equal((await a.load(scope,items)).digest,first.digest)
  const next={comments:[],reason:"Explicit replacement after a fresh read."}
  await b.save(scope,first.digest,next,items)
  assert.equal((await a.load(scope,items)).reason,next.reason)
})
test("unknown/duplicate anchors, invalid UTF-8 and excess bytes refuse without a write",async()=>{
  const custody=memory(),store=new ReviewDraftStore(custody)
  for(const value of [
    {comments:[{itemId:"other",text:"wrong"}],reason:""},
    {comments:[content.comments[0],content.comments[0]],reason:""},
    {comments:[],reason:"\ud800"},
    {comments:[],reason:"я".repeat(2049)}
  ])await assert.rejects(store.save(scope,"",value,items))
  assert.equal(custody.rows.size,0)
  const saved=await store.save(scope,"",content,items)
  await assert.rejects(store.load(scope,new Set(["point-b"])),"no remap after point-set change")
  assert.equal((await store.load(scope,items)).digest,saved.digest)
})
test("actual protected files reopen exact multiline draft without a canonical command or source cache",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"review-drafts-")),work=path.join(root,"work")
  await fs.mkdir(work)
  const files=new LinuxNativeCustody(path.join(root,"private"),[work]);await files.initialize()
  const saved=await new ReviewDraftStore(files).save(scope,"",content,items)
  const reopened=new LinuxNativeCustody(path.join(root,"private"),[work]);await reopened.initialize()
  assert.deepEqual(await new ReviewDraftStore(reopened).load(scope,items),{digest:saved.digest,...content})
  const entries=await reopened.scan("review-draft");assert.equal(entries.length,1)
  const record=JSON.parse(entries[0].raw)
  assert.deepEqual(Object.keys(record).sort(),["comments","formatVersion","reason","scope"])
})
