// SPDX-License-Identifier: AGPL-3.0-only
// Real protected filesystem journal; native create/read ports are explicit MOCKs.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { NativeSessionCreation } from "../src/native-session-creation.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"

async function fixture(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"chat-creation-")),directory=path.join(area,"work")
  await fs.mkdir(directory)
  const root=path.join(area,"creations"),custody=new LinuxNativeCustody(root,[directory])
  const sessions=new Map<string,{id:string;location:{directory:string}}>()
  let creates=0,lost=false
  const native={observe:async(id:string)=>sessions.get(id),create:async(id:string)=>{
    // Native invocation MUST be preceded by the durable exact-chat request.
    const record=JSON.parse((await custody.read("session-creation",id))!)
    assert.deepEqual(record,{formatVersion:1,sessionID:id,directory})
    creates++;assert.equal(sessions.has(id),false)
    const session={id,location:{directory}};sessions.set(id,session)
    if(lost)throw Error("MOCK lost response after native effect")
    return session
  }}
  const creation=new NativeSessionCreation(custody,directory,native);await creation.initialize()
  return {creation,custody,directory,root,sessions,native,creates:()=>creates,loseReply:()=>{lost=true}}
}
test("separate chats in one project retain distinct records; same request coalesces",async()=>{
  const f=await fixture()
  const [a,retry,b]=await Promise.all([f.creation.create("ses_one"),f.creation.create("ses_one"),f.creation.create("ses_two")])
  assert.equal(a.id,retry.id);assert.notEqual(a.id,b.id);assert.equal(f.creates(),2)
  assert.equal((await f.custody.scan("session-creation")).length,2)
  await f.creation.create("ses_one");assert.equal(f.creates(),2)
})
test("lost native reply is reconciled after reopen by exact chat ID without another create",async()=>{
  const f=await fixture();f.loseReply()
  await assert.rejects(f.creation.create("ses_original"));assert.equal(f.creates(),1)
  const original=await f.custody.read("session-creation","ses_original")
  const reopened=new NativeSessionCreation(new LinuxNativeCustody(f.root,[f.directory]),f.directory,f.native)
  await reopened.initialize();assert.deepEqual((await reopened.recover()).map(s=>s.id),["ses_original"])
  assert.equal((await reopened.create("ses_original")).id,"ses_original")
  assert.equal(f.creates(),1);assert.equal(await f.custody.read("session-creation","ses_original"),original)
})
test("retained unknown absence never authorizes a new native create",async()=>{
  const f=await fixture()
  const original=JSON.stringify({formatVersion:1,sessionID:"ses_unknown",directory:f.directory})
  await f.custody.retain("session-creation","ses_unknown",original)
  assert.deepEqual(await f.creation.recover(),[])
  await assert.rejects(f.creation.create("ses_unknown"));await assert.rejects(f.creation.create("ses_unknown"))
  assert.equal(f.creates(),0);assert.equal(await f.custody.read("session-creation","ses_unknown"),original)
})
test("pre-existing, moved, child and conflicting-location sessions cannot be adopted as original creation",async()=>{
  const f=await fixture()
  f.sessions.set("ses_preexisting",{id:"ses_preexisting",location:{directory:f.directory}})
  await assert.rejects(f.creation.create("ses_preexisting"))
  assert.equal(await f.custody.read("session-creation","ses_preexisting"),undefined)
  await f.creation.create("ses_moved")
  f.sessions.set("ses_moved",{id:"ses_moved",location:{directory:f.directory+"-other"}})
  await assert.rejects(f.creation.create("ses_moved"))
  const other=new NativeSessionCreation(f.custody,f.directory+"-other",f.native)
  await assert.rejects(other.create("ses_moved"));assert.equal(f.creates(),1)
  await f.custody.retain("session-creation","ses_child",JSON.stringify({formatVersion:1,sessionID:"ses_child",directory:f.directory}))
  const child=new NativeSessionCreation(f.custody,f.directory,{...f.native,observe:async id=>({id,location:{directory:f.directory},parentID:"ses_parent"})})
  await assert.rejects(child.create("ses_child"));assert.equal(f.creates(),1)
})
test("native observation failures and invalid IDs hold before retaining or creating",async()=>{
  const f=await fixture()
  const unavailable=new NativeSessionCreation(f.custody,f.directory,{...f.native,observe:async()=>{throw Error("MOCK transport failure, not not-found")}})
  await assert.rejects(unavailable.create("ses_transport"));await assert.rejects(unavailable.create("invalid"))
  assert.equal((await f.custody.scan("session-creation")).length,0);assert.equal(f.creates(),0)
})
test("closing a creator drains its original request and refuses new work without deleting records",async()=>{
  const f=await fixture();await f.creation.create("ses_original");await f.creation.close()
  await assert.rejects(f.creation.create("ses_other"));await assert.rejects(f.creation.recover())
  assert.equal(f.creates(),1);assert.equal((await f.custody.scan("session-creation")).length,1)
})
test("startup refuses a retained record whose filename key does not match its chat",async()=>{
  const f=await fixture()
  await f.custody.retain("session-creation","ses_file",JSON.stringify({formatVersion:1,sessionID:"ses_other",directory:f.directory}))
  const reopened=new NativeSessionCreation(new LinuxNativeCustody(f.root,[f.directory]),f.directory,f.native)
  await assert.rejects(reopened.initialize());assert.equal(f.creates(),0)
})
test("independent creators cannot invoke native create twice for the same retained chat",async()=>{
  const f=await fixture();let entered!:()=>void,release!:()=>void
  const started=new Promise<void>(resolve=>{entered=resolve}),barrier=new Promise<void>(resolve=>{release=resolve})
  const a=new NativeSessionCreation(f.custody,f.directory,{...f.native,create:async id=>{
    entered();await barrier;return f.native.create(id)
  }})
  const b=new NativeSessionCreation(new LinuxNativeCustody(f.root,[f.directory]),f.directory,f.native)
  await b.initialize()
  const original=a.create("ses_race");await started
  await assert.rejects(b.create("ses_race"));assert.equal(f.creates(),0)
  release();await original
  assert.equal((await b.create("ses_race")).id,"ses_race");assert.equal(f.creates(),1)
})
