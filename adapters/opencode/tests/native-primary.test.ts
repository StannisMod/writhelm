// SPDX-License-Identifier: AGPL-3.0-only
// Actual protected filesystem custody; SDK hook/service authority fixtures are explicit MOCK.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import type { Plugin } from "@opencode/plugin"
import { registerPrimaryGovernor, primaryInstructions } from "../src/native-primary.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { boundedPlanningKnowledgeChannel } from "../src/connected-tools.js"
import primaryServer from "../src/primary-server.js"
import { installFreshPrimaryPolicy, primaryPermissionRules, requirePrimaryPolicy } from "../src/primary-policy.js"

const id="00000000-0000-0000-0000-000000000001"
test("package server entrypoint refuses absent trusted bootstrap instead of presenting GUI-only governance",async()=>{
  const previous=process.env.WRITHELM_PRIMARY_ROOT
  delete process.env.WRITHELM_PRIMARY_ROOT
  try{await assert.rejects(Promise.resolve().then(()=>primaryServer.setup({app:{version:"2.2.5"}} as unknown as Plugin.Context)))}
  finally{if(previous!==undefined)process.env.WRITHELM_PRIMARY_ROOT=previous}
})
async function fixture() {
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"primary-")),work=path.join(root,"work")
  await fs.mkdir(work);await fs.writeFile(path.join(work,"README.md"),"existing project, read-only")
  await installFreshPrimaryPolicy(work)
  const hooks=new Map<string,(event:any)=>Promise<void>>(),tools=new Map<string,any>(),sessions=new Map<string,any>()
  let readable=true,scope:"controller"|"assignment"="controller",binds=0
  const bound=new Set<string>()
  for(const sessionID of ["ses_primary","ses_competitor","ses_child"])
    sessions.set(sessionID,{id:sessionID,location:{directory:work},parentID:sessionID==="ses_child"?"ses_primary":undefined,permissions:[]})
  const hook=(domain:string)=>(name:string,callback:(event:any)=>Promise<void>)=>{
    hooks.set(domain+":"+name,callback);return Promise.resolve({dispose:async()=>{hooks.delete(domain+":"+name)}})
  }
  const registry={scopeKind:(id:string)=>bound.has(id)?scope:undefined,bound:(id:string)=>bound.has(id),
    restore:async()=>false,
    bind:async(sessionID:string)=>{binds++;bound.add(sessionID)},inspect:async()=>({scope,credentialId:id}),
    sourceIdentity:async()=>{if(!readable)throw Error("MOCK current READ withdrawn");return {scope,projectId:id,controllerId:id}}} as unknown as NativeCredentialRegistry
  const ctx={app:{version:"2.2.5"},location:{directory:work},
    session:{get:async({sessionID}:{sessionID:string})=>sessions.get(sessionID),context:async()=>[],
      update:async({sessionID,permissions}:{sessionID:string;permissions:unknown})=>{sessions.get(sessionID).permissions=permissions},hook:hook("session")},
    permission:{hook:hook("permission")},tool:{hook:hook("tool"),reload:async()=>{},
      transform:async(callback:(editor:any)=>void)=>{callback({add:(t:any)=>tools.set(t.name,t)});return {dispose:async()=>tools.clear()}}}} as unknown as Plugin.Context
  const governor=await registerPrimaryGovernor(ctx,registry,new LinuxNativeCustody(path.join(root,"private"),[work]),
    {workRoot:work,controllerId:id,controllerCredentialId:id,projectId:id,schemas:{"work-command3":"synthetic schema fixture"}})
  const invoke=(name:string,event:any)=>hooks.get(name)!(event)
  return {root,work,hooks,tools,sessions,governor,invoke,binds:()=>binds,revoke:()=>{readable=false},assignment:()=>{scope="assignment"}}
}

test("ordinary broad primary prompt binds once, preserves owner input and cannot dispatch cached shell/patch/subagent",async()=>{
  const f=await fixture()
  try{
    const prompt={sessionID:"ses_primary",messageID:"msg_first",prompt:{text:"Build a community chess tournament tracker with pairings, scores, standings and CSV export"}}
    await f.invoke("session:prompt",prompt)
    assert.equal(prompt.prompt.text,"Build a community chess tournament tracker with pairings, scores, standings and CSV export")
    assert.equal(f.binds(),1)
    const request={sessionID:"ses_primary",system:[],tools:{shell:{},patch:{},subagent:{},read:{},question:{},writhelm_work_command:{}}}
    await f.invoke("session:context",request)
    assert.deepEqual(Object.keys(request.tools),["read","question","writhelm_work_command"])
    assert.ok(request.system.some((part:{text:string})=>part.text.startsWith(primaryInstructions)))
    assert.ok(primaryInstructions.includes("Clarify consequential missing requirements"))
    assert.deepEqual(f.sessions.get("ses_primary").permissions,primaryPermissionRules)
    for(const tool of ["shell","patch","subagent","newly_registered_mutator"])
      await assert.rejects(f.invoke("tool:execute.before",{sessionID:"ses_primary",tool}))
    await f.invoke("tool:execute.before",{sessionID:"ses_primary",tool:"question"})
    for(const kind of ["generate","title","compaction"])
      await assert.rejects(f.invoke("session:model.request",{sessionID:"ses_primary",kind}))
    await assert.rejects(f.invoke("session:generate",{sessionID:"ses_primary",messages:[]}))
    await assert.rejects(f.invoke("session:compaction",{sessionID:"ses_primary",result:{summary:"untrusted prefilled summary"}}))
    const permission={sessionID:"ses_primary",action:"shell",effect:"allow",resources:["*"]}
    await f.invoke("permission:evaluate",permission);assert.equal(permission.effect,"deny")
    const question={sessionID:"ses_primary",action:"question",effect:"allow",resources:["*"]}
    await f.invoke("permission:evaluate",question);assert.equal(question.effect,"allow")
    await assert.rejects(f.invoke("session:prompt",{...prompt,sessionID:"ses_child"}))
    await assert.rejects(f.invoke("session:prompt",{...prompt,sessionID:"ses_competitor"}))
    f.revoke();await assert.rejects(f.invoke("session:context",request))
    await assert.rejects(f.invoke("tool:execute.before",{sessionID:"ses_primary",tool:"writhelm_work_command"}))
  }finally{await f.governor.close()}
})

test("native read retains owner/secret restrictions and primary cannot grant external-directory access",async()=>{
  const f=await fixture()
  try{
    await f.invoke("session:prompt",{sessionID:"ses_primary"})
    await f.invoke("tool:execute.before",{sessionID:"ses_primary",tool:"read"})
    for(const effect of ["allow","ask","deny"]){
      const permission={sessionID:"ses_primary",action:"read",resources:[".env"],effect}
      await f.invoke("permission:evaluate",permission);assert.equal(permission.effect,effect)
    }
    const external={sessionID:"ses_primary",action:"external_directory",resources:["/private/*"],effect:"allow"}
    await f.invoke("permission:evaluate",external);assert.equal(external.effect,"deny")
    const ownerRestriction={action:"read",resource:"private.txt",effect:"deny"}
    f.sessions.get("ses_primary").permissions.push(ownerRestriction)
    await f.invoke("session:model.request",{sessionID:"ses_primary",kind:"primary"})
    assert.ok(f.sessions.get("ses_primary").permissions.some((r:unknown)=>JSON.stringify(r)===JSON.stringify(ownerRestriction)))
    f.revoke();await assert.rejects(f.invoke("tool:execute.before",{sessionID:"ses_primary",tool:"read"}))
  }finally{await f.governor.close()}
})

test("concurrent root selection has one durable winner; unknown direct model/tool/compaction paths hold",async()=>{
  const f=await fixture()
  try{
    await assert.rejects(f.invoke("session:model.request",{sessionID:"ses_primary"}))
    await assert.rejects(f.invoke("tool:execute.before",{sessionID:"ses_child",tool:"shell"}))
    const replies=await Promise.allSettled(["ses_primary","ses_competitor"].map(sessionID=>f.invoke("session:prompt",{sessionID})))
    assert.equal(replies.filter(r=>r.status==="fulfilled").length,1)
    assert.equal(f.binds(),1)
    const winner=replies[0]!.status==="fulfilled"?"ses_primary":"ses_competitor"
    await assert.rejects(f.invoke("session:compaction",{sessionID:winner}))
    // A stale permissive rule after a stored deny is normalized back to exactly one deny rule.
    f.sessions.get(winner).permissions.push({action:"shell",resource:"*",effect:"allow"})
    await f.invoke("session:model.request",{sessionID:winner,kind:"primary"})
    assert.deepEqual(f.sessions.get(winner).permissions.slice(-primaryPermissionRules.length),primaryPermissionRules)
    const unrelated="ses_unrelated"
    f.sessions.set(unrelated,{id:unrelated,location:{directory:path.join(f.root,"unrelated")},permissions:[]})
    const other={sessionID:unrelated,system:[],tools:{shell:{}}}
    await f.invoke("session:context",other);assert.deepEqual(Object.keys(other.tools),["shell"])
    const permission={sessionID:unrelated,effect:"allow"}
    await f.invoke("permission:evaluate",permission);assert.equal(permission.effect,"allow")
  }finally{await f.governor.close()}
})

test("planning knowledge transport cannot approve, administer, dispatch another project or change version",async()=>{
  let posts=0,retains=0
  const channel=boundedPlanningKnowledgeChannel(id,"http://127.0.0.1:18001","wh1.fixture",{retain:async(_b,_k,raw)=>{retains++;return raw}},"MOCK primary binding",async(_url,init)=>{
    posts++;assert.equal(String(_url),"http://127.0.0.1:18001/api/v1/commands")
    assert.equal(new Headers(init!.headers).get("Authorization"),"Bearer wh1.fixture")
    return new Response('{"kind":"error","code":"MOCK_DOMAIN_REJECTION"}')
  })
  const command={formatVersion:1,projectId:id,requestKey:id,operation:"request_approval",payload:{}}
  for(const altered of [{...command,operation:"resolve_approval"},{...command,operation:"rotate_credential"},
    {...command,projectId:"foreign"},{...command,formatVersion:3}])
    await assert.rejects(channel.send("commands",JSON.stringify(altered),new AbortController().signal))
  assert.equal(posts,0);assert.equal(retains,0)
  const reply=await channel.send("commands",JSON.stringify(command),new AbortController().signal)
  assert.equal(posts,1);assert.equal(retains,1);assert.ok(reply.includes("MOCK_DOMAIN_REJECTION"));assert.equal(reply.includes("wh1.fixture"),false)
})

test("native fallback policy is mandatory and existing project configuration is not overwritten",async()=>{
  const f=await fixture()
  try{
    await requirePrimaryPolicy(f.work)
    const other=path.join(f.root,"other");await fs.mkdir(other)
    const original='{"policies":[],"description":"USER_CONFIGURATION"}\n'
    await fs.writeFile(path.join(other,"opencode.json"),original)
    await assert.rejects(installFreshPrimaryPolicy(other))
    assert.equal(await fs.readFile(path.join(other,"opencode.json"),"utf8"),original)
    const policy=JSON.parse(await fs.readFile(path.join(f.work,"opencode.json"),"utf8"))
    assert.deepEqual(policy.policies,[{action:"tool.use",resource:"*",effect:"deny"},
      {action:"tool.use",resource:"question:*",effect:"allow"},{action:"tool.use",resource:"read:*",effect:"allow"},
      {action:"tool.use",resource:"writhelm_*:*",effect:"allow"}])
  }finally{await f.governor.close()}
})
