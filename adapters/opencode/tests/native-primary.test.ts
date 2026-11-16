// SPDX-License-Identifier: AGPL-3.0-only
// Actual protected filesystem custody; SDK hook/service authority fixtures are explicit MOCK.
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { hostname } from "node:os"
import { createHash } from "node:crypto"
import { linuxHostEpoch } from "../src/native-host-identity.js"
import type { Plugin } from "@opencode/plugin"
import { registerPrimaryGovernor, primaryInstructions, registerAutomaticPrimaryGovernor } from "../src/native-primary.js"
import { NativeCredentialRegistry } from "../src/native-credentials.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { boundedPlanningKnowledgeChannel } from "../src/connected-tools.js"
import primaryServer from "../src/primary-server.js"
import { installFreshPrimaryPolicy, primaryPermissionRules, primaryPolicyStatements, requirePrimaryPolicy } from "../src/primary-policy.js"
import { AutomaticEnrollmentClient, provisionAutomaticPrimary, provisionExecutionDeployment, provisionHarnessExecutionDeployment } from "../src/primary-bootstrap.js"
import { contextJSON } from "../src/native-context-codec.js"
import { decodeNativeExecutionDeployment } from "../src/native-initial.js"
import { NativeContextExtraction } from "../src/native-context-extraction.js"
import { assertNativeContextPolicy } from "../src/native-context-policy.js"
import { spawn } from "node:child_process"
import { fileURLToPath } from "node:url"
import { NativeSessionToolRouter } from "../src/native-runtime.js"
import { registerAutomaticClientUI } from "../src/primary-tui.js"

const id="00000000-0000-0000-0000-000000000001"
test("native UI persists ordinary sessions and recovers an uncertain creation without a second create",async()=>{
  const state:{pending:null|{sessionID:string;directory:string}}={pending:null},sessions=new Map<string,any>()
  const created:string[]=[],lookedUp:string[]=[],routes:unknown[]=[],commands:any[]=[]
  let loaded=false,lost=true
  const context={app:{version:"2.2.5"},location:{directory:"/synthetic/ui-project"},
    storage:{memory:()=>[state,(mutate:(draft:typeof state)=>void)=>mutate(state)],store:()=>[{pending:null},()=>{}]},
    data:{location:{agent:{sync:async()=>{loaded=true}}}},
    client:{rpc:()=>({createController:async({sessionID}:any,options:any)=>{
      if(!sessions.has(sessionID))await (context as any).client.session.create({id:sessionID,location:options.location})
      return {kind:"governed-planning",role:"controller",sessionID,projectId:id}
    },admitController:async({sessionID}:any)=>({kind:"governed-planning",role:"controller",sessionID,projectId:id})}),session:{create:async(input:any)=>{
      assert.ok(loaded);assert.deepEqual(Object.keys(input).sort(),["id","location"])
      created.push(input.id);const session={id:input.id,location:input.location};sessions.set(input.id,session)
      if(lost)throw Error("MOCK lost reply after native creation")
      return session
    },get:async({sessionID}:any)=>{lookedUp.push(sessionID);if(!sessions.has(sessionID))throw Error("MOCK unavailable native session");return sessions.get(sessionID)}}},
    keymap:{layer:(factory:()=>any)=>commands.push(factory())},
    ui:{router:{current:()=>({type:"home"}),navigate:(route:unknown)=>routes.push(route)},toast:{show:()=>{}}},
  } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
  const first=registerAutomaticClientUI(context,()=>{})
  await assert.rejects(first.create());assert.equal(created.length,1);assert.ok(state.pending)
  first.close();lost=false
  const reopened=registerAutomaticClientUI(context,()=>{})
  await reopened.create()
  assert.equal(created.length,1);assert.deepEqual(lookedUp,[created[0]])
  assert.deepEqual(routes,[{type:"session",sessionID:created[0]}]);assert.equal(state.pending,null)
  assert.equal(commands[0].commands[0].id,"session.new")
  assert.equal(commands[0].commands[0].bind,"<leader>n")
  await reopened.create();assert.equal(created.length,2);assert.notEqual(created[0],created[1])
  reopened.close();await assert.rejects(reopened.create())
})
test("unresolved UI creation is retained and held rather than silently minting another session",async()=>{
  const state={pending:{sessionID:"ses_retained",directory:"/synthetic/ui-project"}}
  let creates=0,navigations=0
  const context={app:{version:"2.2.5"},location:{directory:"/synthetic/ui-project"},
    storage:{memory:()=>[state,()=>{}],store:()=>[{pending:null},()=>{}]},data:{location:{agent:{sync:async()=>{}}}},
    client:{rpc:()=>({createController:async()=>{throw Error("MOCK unresolved original server creation")}}),session:{get:async()=>{throw Error("MOCK unavailable")},create:async()=>{creates++;throw Error("must not recreate")}}},
    keymap:{layer:()=>{}},ui:{router:{current:()=>({type:"home"}),navigate:()=>{navigations++}},toast:{show:()=>{}}},
  } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
  const lifecycle=registerAutomaticClientUI(context,()=>{})
  await assert.rejects(lifecycle.create());await assert.rejects(lifecycle.create())
  assert.equal(creates,0);assert.equal(navigations,0);assert.equal(state.pending.sessionID,"ses_retained")
  lifecycle.close()
})
test("UI New session uses the active native location and checks but never claims the legacy pending store",async()=>{
  const state:{pending:null|{sessionID:string;directory:string}}={pending:null}
  const active={directory:"/synthetic/second-project"};let key="",created=0
  const context={app:{version:"2.2.5"},location:{directory:"/synthetic/first-project"},
    storage:{memory:()=>[state,(mutate:(draft:typeof state)=>void)=>mutate(state)],store:(selected:string)=>{key=selected;return [{pending:null},()=>{}]}},
    data:{location:{agent:{sync:async(location:unknown)=>assert.deepEqual(location,active)}}},
    client:{rpc:()=>({createController:async({sessionID}:any,options:any)=>{
      await (context as any).client.session.create({id:sessionID,location:options.location})
      return {kind:"governed-planning",role:"controller",sessionID,projectId:id}
    },admitController:async({sessionID}:any)=>({kind:"governed-planning",role:"controller",sessionID,projectId:id})}),session:{get:async({sessionID}:any)=>({id:sessionID,location:active}),create:async(input:any)=>{
      assert.deepEqual(input.location,active);created++;return {id:input.id,location:active}
    }}},keymap:{layer:()=>{}},
    ui:{router:{current:()=>({type:"session",sessionID:"ses_active"}),navigate:()=>{}},toast:{show:()=>{}}},
  } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
  const lifecycle=registerAutomaticClientUI(context,()=>{})
  assert.equal(created,0);await lifecycle.create();assert.equal(created,1)
  assert.equal(key,"automatic-session-intent-v1-"+createHash("sha256").update(active.directory).digest("hex"))
  lifecycle.close()
})
test("UI waits for current server admission and holds invalid replies without recreating native sessions",async()=>{
  for(const failure of ["unavailable","worker","wrong-session"]){
    const state:{pending:null|{sessionID:string;directory:string}}={pending:null}
    let creates=0,navigations=0,release!:()=>void,command!:()=>Promise<void>
    const barrier=new Promise<void>(resolve=>{release=resolve}),holds:string[]=[]
    const context={app:{version:"2.2.5"},location:{directory:"/synthetic/admission"},
      storage:{memory:()=>[state,(mutate:(draft:typeof state)=>void)=>mutate(state)],store:()=>[{pending:null},()=>{}]},
      data:{location:{agent:{sync:async()=>{}}}},
      client:{session:{create:async(input:any)=>{creates++;return {id:input.id,location:input.location}},
        get:async()=>({id:state.pending!.sessionID,location:{directory:state.pending!.directory}})},
        rpc:()=>({createController:async({sessionID}:any)=>{
          if(!creates)creates++
          return {kind:"governed-planning",role:"controller",sessionID,projectId:id}
        },admitController:async({sessionID}:any)=>{
          await barrier
          if(failure==="unavailable")throw Error("MOCK missing RPC")
          return {kind:"governed-planning",role:failure==="worker"?"worker":"controller",
            sessionID:failure==="wrong-session"?"ses_other":sessionID,projectId:id}
        }})},keymap:{layer:(factory:()=>any)=>{command=factory().commands[0].run}},
      ui:{router:{current:()=>({type:"home"}),navigate:()=>{navigations++}}},
    } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
    const lifecycle=registerAutomaticClientUI(context,stage=>{holds.push(stage)})
    while(!state.pending)await Promise.resolve()
    const queued=command()
    assert.equal(navigations,0);release();await lifecycle.initial;await queued
    assert.deepEqual(holds,["server-admission"]);assert.ok(state.pending)
    await assert.rejects(lifecycle.create());assert.equal(creates,1);assert.equal(navigations,0)
    lifecycle.close()
  }
})
test("restored UI routes revalidate native identity and current authority without creating sessions",async()=>{
  for(const failure of ["none","identity","authority"]){
    let release!:()=>void,finished=false,creates=0,stores=0,navigations=0
    const barrier=new Promise<void>(resolve=>{release=resolve}),holds:string[]=[],admissions:string[]=[]
    const location={directory:"/synthetic/restored"}
    const context={app:{version:"2.2.5"},storage:{store:()=>{stores++;throw Error("must not create intent")}},
      client:{session:{get:async()=>({id:failure==="identity"?"ses_foreign":"ses_restored",location}),
        create:async()=>{creates++;throw Error("must not create")}},
        rpc:()=>({admitController:async({sessionID}:any,options:any)=>{
          admissions.push(sessionID);assert.deepEqual(options.location,location);await barrier
          if(failure==="authority")throw Error("MOCK revoked authority")
          return {kind:"governed-planning",role:"controller",sessionID,projectId:id}
        }})},keymap:{layer:()=>{}},
      ui:{router:{current:()=>({type:"session",sessionID:"ses_restored"}),navigate:()=>{navigations++}}},
    } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
    const lifecycle=registerAutomaticClientUI(context,stage=>{holds.push(stage)})
    void lifecycle.initial.then(()=>{finished=true})
    await Promise.resolve();await Promise.resolve()
    if(failure!=="identity")assert.equal(finished,false)
    release();await lifecycle.initial
    assert.equal(creates,0);assert.equal(stores,0);assert.equal(navigations,0)
    assert.deepEqual(admissions,failure==="identity"?[]:["ses_restored"])
    assert.deepEqual(holds,failure==="none"?[]:[failure==="identity"?"restored-native-session":"server-admission"])
    lifecycle.close()
  }
})
test("New session received during initial admission creates a distinct session after admission completes",async()=>{
  const state:{pending:null|{sessionID:string;directory:string}}={pending:null}
  let release!:()=>void,command!:()=>Promise<void>,creates=0,admissions=0
  const barrier=new Promise<void>(resolve=>{release=resolve}),routes:string[]=[]
  const location={directory:"/synthetic/early-new"}
  const context={app:{version:"2.2.5"},location,
    storage:{memory:()=>[state,(mutate:(draft:typeof state)=>void)=>mutate(state)],store:()=>[{pending:null},()=>{}]},
    data:{location:{agent:{sync:async()=>{}}}},
    client:{session:{get:async({sessionID}:any)=>({id:sessionID,location}),create:async(input:any)=>{creates++;return {id:input.id,location}}},
      rpc:()=>({createController:async({sessionID}:any)=>{creates++;return {kind:"governed-planning",role:"controller",sessionID,projectId:id}},admitController:async({sessionID}:any)=>{
        if(++admissions===1)await barrier
        return {kind:"governed-planning",role:"controller",sessionID,projectId:id}
      }})},keymap:{layer:(factory:()=>any)=>{command=factory().commands[0].run}},
    ui:{router:{current:()=>({type:"home"}),navigate:(route:any)=>{routes.push(route.sessionID)}}},
  } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
  const lifecycle=registerAutomaticClientUI(context,()=>assert.fail("unexpected hold"))
  while(admissions===0)await Promise.resolve()
  const requested=command();assert.equal(creates,1);release();await requested
  assert.equal(creates,2);assert.equal(routes.length,2);assert.notEqual(routes[0],routes[1])
  lifecycle.close()
})
test("two UI contexts in one folder create distinct chats without mutating a shared pending slot",async()=>{
  const durable:{pending:null|{sessionID:string;directory:string}}={pending:null}
  let creates=0,updates=0
  const chats=new Set<string>()
  const holds:string[]=[]
  // View-local memory survives reload; shared durable storage is read only for legacy refusal.
  const context=()=>({app:{version:"2.2.5"},location:{directory:"/synthetic/concurrent"},
    storage:{memory:()=>{const local={pending:null};return [local,(mutate:any)=>mutate(local)]},store:()=>[durable,async(mutate:(draft:typeof durable)=>void)=>{updates++;mutate(durable)}]},
    data:{location:{agent:{sync:async()=>{}}}},
    client:{session:{get:async({sessionID}:any)=>({id:sessionID,location:{directory:"/synthetic/concurrent"}})},
      rpc:()=>({createController:async({sessionID}:any)=>{creates++;chats.add(sessionID);return {kind:"governed-planning",role:"controller",sessionID,projectId:id}},admitController:async({sessionID}:any)=>({kind:"governed-planning",role:"controller",sessionID,projectId:id})})},
    keymap:{layer:()=>{}},ui:{router:{current:()=>({type:"home"}),navigate:()=>{}}},
  } as unknown as Parameters<typeof registerAutomaticClientUI>[0])
  const a=registerAutomaticClientUI(context(),stage=>{holds.push(stage)}),b=registerAutomaticClientUI(context(),stage=>{holds.push(stage)})
  await Promise.all([a.initial,b.initial])
  assert.equal(creates,2);assert.equal(chats.size,2);assert.deepEqual(holds,[]);assert.equal(durable.pending,null)
  assert.equal(updates,0);a.close();b.close()
})
test("a new view cannot adopt or erase an ambiguous legacy per-folder creation",async()=>{
  const legacy={pending:{sessionID:"ses_legacy",directory:"/synthetic/legacy"}}
  const holds:string[]=[];let rpcCalls=0,memoryCalls=0
  const context={app:{version:"2.2.5"},location:{directory:legacy.pending.directory},
    storage:{store:()=>[legacy,()=>assert.fail("must not clear legacy state")],memory:()=>{memoryCalls++;throw Error("must not claim")}},
    client:{rpc:()=>{rpcCalls++;throw Error("must not dispatch")}},keymap:{layer:()=>{}},
    ui:{router:{current:()=>({type:"home"}),navigate:()=>assert.fail("must not navigate")}},
  } as unknown as Parameters<typeof registerAutomaticClientUI>[0]
  const lifecycle=registerAutomaticClientUI(context,stage=>{holds.push(stage)})
  await lifecycle.initial;assert.deepEqual(holds,["intent-store"])
  assert.equal(rpcCalls,0);assert.equal(memoryCalls,0);assert.equal(legacy.pending.sessionID,"ses_legacy")
  lifecycle.close()
})
async function executionInstallation(){
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"execution-provision-")),work=path.join(area,"work")
  await fs.mkdir(work);await fs.writeFile(path.join(work,"owner.txt"),"untouched\n")
  const key={projectId:id,profileId:id,revision:0},profileKeyJSON=contextJSON(key)
  const profile={formatVersion:1,family:"profiles",key,payload:{nativeProfileId:"test-native",qualificationEvidence:[],
    model:{providerId:"test-provider",modelId:"test-model",variant:null},route:"explicit-route",effort:"bounded",
    contextTokens:8192,outputReserveTokens:512,toolReserveTokens:512,compactionReserveTokens:512,
    compactionPolicyId:"test-foreground",requestAccountingProfileId:"test-accounting",executionAssurance:"E-COOP-1",
    permissions:[{tool:"shell",scope:"workspace",action:"allow"}],platform:"linux",qualification:"unqualified"}}
  const execution={formatVersion:1,profileKeyJSON,model:{providerID:"test-provider",id:"test-model"},baseURL:null,
    routeID:"explicit-route",effortOptionKey:"effort",nativeOptions:{effort:"bounded",maxTokens:64}}
  const policy={formatVersion:1,profileKeyJSON,policyId:"test-foreground",nativeProfileId:"test-native",
    auto:true,background:false,keepTokens:2048,bufferTokens:1024}
  const config={root:path.join(area,"private"),projectId:id,forbiddenAreas:[work],
    profileRecordJSON:contextJSON(profile),executionJSON:contextJSON(execution),contextPolicyJSON:contextJSON(policy)}
  return {area,work,key,profile,execution,policy,config}
}
test("trusted execution provisioning installs runtime-consumed records idempotently and retains bash without model calls",async()=>{
  const f=await executionInstallation()
  const receipt=await provisionExecutionDeployment(f.config)
  assert.equal(receipt.kind,"deployment-provisioned")
  const custody=new LinuxNativeCustody(path.join(f.config.root,"bootstrap"),[f.work]);await custody.initialize()
  assert.equal(await custody.read("execution-profile",contextJSON(f.key)),f.config.executionJSON)
  assert.equal(await custody.read("native-context-policy",contextJSON(f.key)),f.config.contextPolicyJSON)
  assert.deepEqual(await provisionExecutionDeployment(f.config),receipt)
  const translated=decodeNativeExecutionDeployment(f.config.profileRecordJSON,f.config.executionJSON)
  assert.ok(translated.permissions.some(p=>p.action==="shell"&&p.effect==="allow"))
  assertNativeContextPolicy([{type:"document",info:{experimental:{background_compaction:false},
    compaction:{auto:true,keep:{tokens:2048},buffer:1024}}}] as never,f.config.profileRecordJSON,f.config.contextPolicyJSON)
  for(const entry of await fs.readdir(custody.root))assert.equal((await fs.stat(path.join(custody.root,entry))).mode&0o077,0)
  assert.equal(await fs.readFile(path.join(f.work,"owner.txt"),"utf8"),"untouched\n")
})
test("harness provisioning reads current session and materialized native model settings without credential access",async()=>{
  const f=await executionInstallation(),calls:string[]=[]
  const info={id:"ses_harness",model:{providerID:"test-provider",id:"test-model"},location:{directory:f.work}}
  let effort="bounded",changed=false
  const native={
    session:{get:async()=>{calls.push("session.get");return changed?{...info,model:{providerID:"other",id:"test-model"}}:info}},
    model:{list:async()=>{calls.push("model.list");return {data:[{providerID:"test-provider",id:"test-model",enabled:true,
      variants:[],limit:{context:8192,output:512},settings:{effort,apiKey:"DO-NOT-RETAIN",headers:{authorization:"DO-NOT-RETAIN"}}}]}},
      default:async()=>{throw Error("Selected session must not fall back to default")}},
    config:{get:async()=>{calls.push("config.get");return [{type:"document",info:{experimental:{background_compaction:false},
      compaction:{auto:true,keep:{tokens:2048},buffer:1024}}}]}},
    integration:{connection:{resolve:()=>{throw Error("Never read native credentials")}}}
  }
  const config={...f.config,sessionID:info.id,workRoot:f.work}
  await provisionHarnessExecutionDeployment(config,native as never)
  assert.deepEqual(calls,["session.get","model.list","config.get","session.get"])
  const custody=new LinuxNativeCustody(path.join(f.config.root,"bootstrap"),[f.work]);await custody.initialize()
  const raw=await custody.read("execution-profile",contextJSON(f.key));assert.ok(raw)
  assert.equal(JSON.parse(raw).nativeOptions.effort,"bounded")
  assert.equal(raw.includes("DO-NOT-RETAIN"),false)
  effort="other"
  await assert.rejects(provisionHarnessExecutionDeployment(config,native as never))
  effort="bounded";changed=true
  await assert.rejects(provisionHarnessExecutionDeployment(config,native as never))
  assert.equal(await custody.read("execution-profile",contextJSON(f.key)),raw)
})
test("native-default effort inherits OpenCode without an effort override and survives context extraction validation",async()=>{
  const f=await executionInstallation()
  const profile={...f.profile,payload:{...f.profile.payload,effort:"native-default"}}
  const profileRecordJSON=contextJSON(profile)
  const info={id:"ses_default",model:{providerID:"test-provider",id:"test-model"},location:{directory:f.work}}
  const native={session:{get:async()=>info},model:{list:async()=>({data:[{
    providerID:"test-provider",id:"test-model",enabled:true,variants:[],limit:{context:8192,output:512},settings:{}
  }]})},config:{get:async()=>[{type:"document",info:{experimental:{background_compaction:false},
    compaction:{auto:true,keep:{tokens:2048},buffer:1024}}}]}}
  await provisionHarnessExecutionDeployment({...f.config,profileRecordJSON,sessionID:info.id,workRoot:f.work},native as never)
  const custody=new LinuxNativeCustody(path.join(f.config.root,"bootstrap"),[f.work]);await custody.initialize()
  const raw=await custody.read("execution-profile",contextJSON(f.key));assert.ok(raw)
  const translated=decodeNativeExecutionDeployment(profileRecordJSON,raw)
  assert.equal(translated.d.effortOptionKey,null)
  assert.deepEqual(translated.options,{maxTokens:512})
  const extraction=new NativeContextExtraction({app:{version:"2.2.5"}} as never,{} as never,{
    profileJSON:contextJSON(f.key),model:translated.selected,baseURL:null,routeID:f.profile.payload.route,
    effortOptionKey:null,nativeOptions:translated.options})
  extraction.assertProfile(profileRecordJSON)
  assert.throws(()=>extraction.assertProfile(f.config.profileRecordJSON))
  assert.throws(()=>decodeNativeExecutionDeployment(f.config.profileRecordJSON,raw))
  for(const options of [{maxTokens:64,effort:"bounded"},{maxTokens:64,reasoningEffort:"high"},{maxTokens:64,temperature:0.5}])
    assert.throws(()=>decodeNativeExecutionDeployment(profileRecordJSON,contextJSON({...JSON.parse(raw),nativeOptions:options})))
})
test("execution provisioning refuses mismatched model route effort policy secrets and conflicting originals",async()=>{
  const f=await executionInstallation()
  for(const execution of [
    {...f.execution,model:{providerID:"other",id:"test-model"}},
    {...f.execution,routeID:"other"},{...f.execution,nativeOptions:{effort:"other",maxTokens:64}},
    {...f.execution,nativeOptions:{effort:"bounded",maxTokens:513}},
    {...f.execution,nativeOptions:{effort:"bounded",maxTokens:64,apiKey:"not-a-real-key"}},
    {...f.execution,baseURL:"https://user:password@example.invalid/v1"},
    {...f.execution,unexpected:true}
  ])await assert.rejects(provisionExecutionDeployment({...f.config,executionJSON:contextJSON(execution)}))
  for(const policy of [{...f.policy,background:true},{...f.policy,keepTokens:8192},{...f.policy,policyId:"other"}])
    await assert.rejects(provisionExecutionDeployment({...f.config,contextPolicyJSON:contextJSON(policy)}))
  for(const payload of [{...f.profile.payload,compactionReserveTokens:32},{...f.profile.payload,extra:"not-a-profile-field"}])
    await assert.rejects(provisionExecutionDeployment({...f.config,profileRecordJSON:contextJSON({...f.profile,payload})}))
  assert.equal(await fs.stat(f.config.root).then(()=>true,()=>false),false,"Invalid configuration must precede custody creation")
  const receipt=await provisionExecutionDeployment(f.config)
  await assert.rejects(provisionExecutionDeployment({...f.config,executionJSON:contextJSON({...f.execution,
    nativeOptions:{effort:"bounded",maxTokens:32}})}))
  assert.deepEqual(await provisionExecutionDeployment(f.config),receipt)
  await assert.rejects(provisionExecutionDeployment({...f.config,projectId:"00000000-0000-0000-0000-000000000002"}))
})
test("execution provisioning reconciles the original partial publication but refuses another bundle",async()=>{
  const f=await executionInstallation()
  const custody=new LinuxNativeCustody(path.join(f.config.root,"bootstrap"),[f.work]);await custody.initialize()
  const original=contextJSON({formatVersion:1,profileRecordJSON:f.config.profileRecordJSON,
    executionJSON:f.config.executionJSON,contextPolicyJSON:f.config.contextPolicyJSON})
  await custody.retain("execution-deployment",contextJSON(f.key),original)
  await assert.rejects(provisionExecutionDeployment({...f.config,executionJSON:contextJSON({...f.execution,
    nativeOptions:{effort:"bounded",maxTokens:32}})}))
  assert.equal(await custody.read("execution-profile",contextJSON(f.key)),undefined)
  await provisionExecutionDeployment(f.config)
  assert.equal(await custody.read("execution-deployment",contextJSON(f.key)),original)
  assert.equal(await custody.read("native-context-policy",contextJSON(f.key)),f.config.contextPolicyJSON)
})
test("competing trusted deployment installs cannot mix execution and context projections",async()=>{
  const f=await executionInstallation()
  const other={...f.config,executionJSON:contextJSON({...f.execution,nativeOptions:{effort:"bounded",maxTokens:32}}),
    contextPolicyJSON:contextJSON({...f.policy,keepTokens:1024})}
  const attempts=await Promise.allSettled([provisionExecutionDeployment(f.config),provisionExecutionDeployment(other)])
  assert.equal(attempts.filter(r=>r.status==="fulfilled").length,1)
  const winner=attempts[0]!.status==="fulfilled"?f.config:other
  const custody=new LinuxNativeCustody(path.join(f.config.root,"bootstrap"),[f.work]);await custody.initialize()
  assert.equal(await custody.read("execution-profile",contextJSON(f.key)),winner.executionJSON)
  assert.equal(await custody.read("native-context-policy",contextJSON(f.key)),winner.contextPolicyJSON)
})
test("trusted execution CLI provisions from stdin in a separate process without native or service configuration",async()=>{
  const f=await executionInstallation()
  const run=(input:unknown)=>new Promise<{code:number|null;out:string;error:string}>((resolve,reject)=>{
    const child=spawn(process.execPath,[fileURLToPath(new URL("../src/primary-bootstrap.js",import.meta.url)),"provision-execution"],
      {env:{},stdio:["pipe","pipe","pipe"]})
    let out="",error=""
    child.stdout.on("data",chunk=>{out+=chunk});child.stderr.on("data",chunk=>{error+=chunk})
    child.on("error",reject);child.on("close",code=>resolve({code,out,error}))
    child.stdin.end(JSON.stringify(input))
  })
  const first=await run(f.config)
  assert.equal(first.code,0,first.error)
  assert.equal(JSON.parse(first.out).kind,"deployment-provisioned")
  assert.deepEqual(await run(f.config),first)
  const refused=await run({...f.config,executionJSON:contextJSON({...f.execution,routeID:"wrong-route"})})
  assert.equal(refused.code,2);assert.equal(refused.out,"")
  assert.match(refused.error,/deployment held; no model or native host was started/)
  assert.equal((await run(f.config)).out,first.out)
})
test("trusted automatic provisioning creates a protected reusable bootstrap and refuses conflicting profiles",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"automatic-provision-")),work=path.join(area,"work")
  await fs.mkdir(work)
  const config={root:path.join(area,"private"),forbiddenAreas:[work],storeId:id,
    serviceOrigin:"http://127.0.0.1:18671",nativeOrigin:"http://127.0.0.1:18672",
    nativeRegistrationFile:path.join(area,"native-registration.json"),clientPolicyFile:path.join(area,"profile/opencode.json"),
    plugin:path.join(area,"plugin"),schemas:{"work-command3":"explicit synthetic schema"}}
  await provisionAutomaticPrimary(config)
  const custody=new LinuxNativeCustody(path.join(config.root,"bootstrap"),[work]);await custody.initialize()
  const first=await custody.read("automatic-bootstrap","current");assert.ok(first)
  const descriptor=JSON.parse(first)
  assert.match(descriptor.enrollmentProof,/^[A-Za-z0-9_-]{43}$/)
  assert.equal(Object.hasOwn(descriptor,"ownerToken"),false)
  await provisionAutomaticPrimary(config);assert.equal(await custody.read("automatic-bootstrap","current"),first)
  await assert.rejects(provisionAutomaticPrimary({...config,storeId:"00000000-0000-0000-0000-000000000002"}))
  assert.equal(await custody.read("automatic-bootstrap","current"),first)
  const insecure={plugins:[config.plugin],permissions:[...primaryPermissionRules,{action:"shell",resource:"*",effect:"allow"}],policies:primaryPolicyStatements}
  const bytes=JSON.stringify(insecure);await fs.writeFile(config.clientPolicyFile,bytes)
  await assert.rejects(provisionAutomaticPrimary(config))
  assert.equal(await fs.readFile(config.clientPolicyFile,"utf8"),bytes)
  assert.equal(await custody.read("automatic-bootstrap","current"),first)
})
test("automatic provisioning retains explicit execution policy separately and rejects conflicting or invalid deployments",async()=>{
  const f=await executionInstallation()
  const config={root:path.join(f.area,"automatic"),forbiddenAreas:[f.work],storeId:id,
    serviceOrigin:"http://127.0.0.1:18671",nativeOrigin:"http://127.0.0.1:18672",
    nativeRegistrationFile:path.join(f.area,"registration.json"),clientPolicyFile:path.join(f.area,"profile/opencode.json"),
    plugin:path.join(f.area,"plugin"),schemas:{"work-command3":"explicit synthetic schema"}}
  await provisionAutomaticPrimary(config)
  const custody=new LinuxNativeCustody(path.join(config.root,"bootstrap"),[f.work]);await custody.initialize()
  const original=await custody.read("automatic-bootstrap","current")
  const deployment={profileRecordJSON:f.config.profileRecordJSON,executionJSON:f.config.executionJSON,contextPolicyJSON:f.config.contextPolicyJSON}
  await provisionAutomaticPrimary({...config,executionDeployments:[deployment]})
  const policy=await custody.read("automatic-execution-deployments","current");assert.ok(policy)
  assert.equal(await custody.read("automatic-bootstrap","current"),original)
  await provisionAutomaticPrimary(config)
  assert.equal(await custody.read("automatic-execution-deployments","current"),policy)
  await assert.rejects(provisionAutomaticPrimary({...config,executionDeployments:[{...deployment,
    executionJSON:contextJSON({...f.execution,routeID:"wrong"})}]}))
  await assert.rejects(provisionAutomaticPrimary({...config,executionDeployments:[]}))
  assert.equal(await custody.read("automatic-execution-deployments","current"),policy)
})
test("Linux native identity uses OS hostname while retaining PID, origin and executable pins",async()=>{
  const origin="http://127.0.0.1:18671"
  const info={version:"2.2.5",pid:process.pid,hostname:hostname(),urls:[origin],paths:{tmp:"/synthetic"}}
  const digest=createHash("sha256").update(await fs.readFile("/proc/self/exe")).digest("hex")
  assert.match(await linuxHostEpoch(info,origin,digest),/^[a-f0-9]{64}$/)
  for(const changed of [{...info,hostname:hostname()+"-wrong"},{...info,pid:process.pid+1},
    {...info,urls:["http://127.0.0.1:18672"]},{...info,version:"wrong"}])
    await assert.rejects(linuxHostEpoch(changed,origin,digest))
  await assert.rejects(linuxHostEpoch(info,"http://0.0.0.0:18671",digest))
  await assert.rejects(linuxHostEpoch(info,origin,"0".repeat(64)))
})
test("shared custody serializes separate location instances without weakening exclusive records",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"shared-custody-")),work=path.join(root,"work");await fs.mkdir(work)
  const independent=await import(new URL("../src/native-runtime-custody.js?independent-plugin",import.meta.url).href)
  const a=new LinuxNativeCustody(path.join(root,"private"),[work]),b=new independent.LinuxNativeCustody(path.join(root,"private"),[work]) as LinuxNativeCustody
  await a.initialize();await b.initialize()
  const results=await Promise.all(Array.from({length:16},(_,i)=>(i%2?a:b).retain("sample",String(i),String(i))))
  assert.ok(results.every(Boolean))
  for(let i=0;i<8;i++){
    const [,inventory]=await Promise.all([a.retain("sample",`scan-${i}`,String(i)),b.scan("sample")])
    assert.equal(inventory.length,17+i)
  }
  await assert.rejects(b.retain("sample","0","conflicting bytes"))
  assert.equal(await a.read("sample","0"),"0")
})
test("native fallback policy compares exact rule fields independently of JSON object key order",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const work=await fs.mkdtemp(path.join(base,"policy-order-")),file=path.join(work,"opencode.json")
  const rules=primaryPolicyStatements.map(rule=>({effect:rule.effect,resource:rule.resource,action:rule.action}))
  await fs.writeFile(file,JSON.stringify({policies:rules}));await requirePrimaryPolicy(work)
  await fs.writeFile(file,JSON.stringify({policies:rules.map((rule,i)=>i===0?{...rule,effect:"allow"}:rule)}))
  await assert.rejects(requirePrimaryPolicy(work))
  await fs.writeFile(file,JSON.stringify({policies:rules.map((rule,i)=>i===0?{...rule,extra:true}:rule)}))
  await assert.rejects(requirePrimaryPolicy(work))
})
test("shared runtime tools dispatch by session binding not the last registered project",async()=>{
  const transforms=new Set<(editor:any)=>void>(),tools=new Map<string,any>()
  const rebuild=()=>{tools.clear();for(const transform of transforms)transform({add:(tool:any)=>tools.set(tool.name,tool)})}
  const ctx={session:{},permission:{},tool:{transform:async(callback:(editor:any)=>void)=>{
    transforms.add(callback)
    try{rebuild()}catch(error){transforms.delete(callback);rebuild();throw error}
    return {dispose:async()=>{transforms.delete(callback);rebuild()}}
  }}} as unknown as Plugin.Context
  const router=new NativeSessionToolRouter(ctx)
  let ambiguous=false
  const a=router.context("ses_a",async sid=>sid==="ses_a"||sid==="ses_worker_a")
  const b=router.context("ses_b",async sid=>sid==="ses_b"||ambiguous&&sid==="ses_a")
  const definition=(owner:string)=>({name:"writhelm_work_command",description:"scoped command",options:{codemode:false},
    input:{type:"object",properties:{}},execute:async(_:unknown,actual:any)=>({content:owner+":"+actual.sessionID})})
  const first=await a.tool.transform(editor=>editor.add(definition("project-a")))
  const captured=tools.get("writhelm_work_command")
  const second=await b.tool.transform(editor=>editor.add(definition("project-b")))
  assert.equal((await captured.execute({}, {sessionID:"ses_a"})).content,"project-a:ses_a")
  assert.equal((await captured.execute({}, {sessionID:"ses_b"})).content,"project-b:ses_b")
  assert.equal((await captured.execute({}, {sessionID:"ses_worker_a"})).content,"project-a:ses_worker_a")
  await assert.rejects(captured.execute({}, {sessionID:"ses_unregistered"}))
  ambiguous=true;await assert.rejects(captured.execute({}, {sessionID:"ses_a"}));ambiguous=false
  await first.dispose()
  await assert.rejects(captured.execute({}, {sessionID:"ses_a"}))
  assert.equal((await captured.execute({}, {sessionID:"ses_b"})).content,"project-b:ses_b")
  router.close();await assert.rejects(captured.execute({}, {sessionID:"ses_b"}));await second.dispose()
})
test("automatic lifecycle enrolls distinct sessions before context and coalesces creation/prompt races",{timeout:10000},async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const root=await fs.mkdtemp(path.join(base,"automatic-primary-")),work=path.join(root,"work");await fs.mkdir(work)
  const hooks=new Map<string,(event:any)=>Promise<void>>(),queue:any[]=[]
  let wake:(()=>void)|undefined
  const emit=(sessionID:string)=>{queue.push({data:{sessionID}});wake?.()}
  const sessions=new Map(["ses_first","ses_second","ses_failed"].map(sessionID=>[sessionID,{id:sessionID,location:{directory:work},permissions:[]}]))
  const hook=(domain:string)=>(name:string,callback:(event:any)=>Promise<void>)=>{
    hooks.set(domain+":"+name,callback);return Promise.resolve({dispose:async()=>{hooks.delete(domain+":"+name)}})
  }
  const ctx={app:{version:"2.2.5"},location:{directory:work,project:{id:"native_project"}},
    session:{get:async({sessionID}:any)=>sessions.get(sessionID),context:async()=>[],
      update:async({sessionID,permissions}:any)=>{sessions.get(sessionID)!.permissions=permissions},hook:hook("session")},
    tool:{hook:hook("tool")},permission:{hook:hook("permission")},
    event:{subscribe:({signal}:any)=>({async *[Symbol.asyncIterator](){
      signal.addEventListener("abort",()=>wake?.(),{once:true})
      while(!signal.aborted){if(queue.length)yield queue.shift();else await new Promise<void>(resolve=>{wake=resolve})}
    }})}} as unknown as Plugin.Context
  let enter!:()=>void,release!:()=>void
  const entered=new Promise<void>(resolve=>{enter=resolve}),released=new Promise<void>(resolve=>{release=resolve})
  const enrollments:string[]=[],activations:string[]=[],closed:string[]=[]
  const toolAdmissions:Array<{sessionID:string;toolName:string|undefined}>=[]
  const enrollment={openSession:async(sessionID:string)=>{
    enrollments.push(sessionID)
    return {sessionID,workRoot:work,projectId:id,controllerId:id,principalId:id,credentialId:id,token:"MOCK_SCOPED_SECRET_NOT_IN_CONTEXT"}
  }}
  const governor=await registerAutomaticPrimaryGovernor(ctx,new LinuxNativeCustody(path.join(root,"private"),[work]),enrollment,async binding=>{
    activations.push(binding.sessionID)
    if(binding.sessionID==="ses_first"){enter();await released}
    if(binding.sessionID==="ses_failed")throw Error("MOCK unavailable native runtime")
    return {sessionID:binding.sessionID,projectId:id,workRoot:work,role:"controller" as const,current:async()=>{},
      admitPrompt:async()=>{},admitContext:async()=>{},admitModel:async()=>{},admitTool:async(sessionID:string,toolName?:string)=>{toolAdmissions.push({sessionID,toolName})},admitGenerate:async()=>{},admitCompaction:async()=>{},
      close:async()=>{closed.push(binding.sessionID)}}
  },async()=>undefined)
  try {
    emit("ses_foreign_unavailable")
    emit("ses_first");await entered
    const first=hooks.get("session:prompt")!({sessionID:"ses_first",prompt:{text:"ordinary owner task"}})
    const second={sessionID:"ses_second",system:[] as any[],tools:{shell:{},read:{},question:{},writhelm_work_command:{}}}
    await hooks.get("session:context")!(second)
    assert.deepEqual(Object.keys(second.tools),["read","question","writhelm_work_command"])
    assert.ok(second.system.some(part=>part.text.startsWith(primaryInstructions)))
    assert.equal(JSON.stringify(second).includes("MOCK_SCOPED_SECRET"),false)
    assert.equal(enrollments.filter(session=>session==="ses_first").length,1)
    release();await first;await governor.ensure("ses_first")
    assert.equal(activations.filter(session=>session==="ses_first").length,1)
    await assert.rejects(hooks.get("tool:execute.before")!({sessionID:"ses_second",tool:"shell"}))
    await hooks.get("tool:execute.before")!({sessionID:"ses_second",tool:"read"})
    assert.deepEqual(toolAdmissions,[{sessionID:"ses_second",toolName:"read"}],"Actual tool identity must reach the scoped runtime guard")
    await assert.rejects(hooks.get("session:model.request")!({sessionID:"ses_failed",kind:"primary"}))
    await assert.rejects(hooks.get("session:prompt")!({sessionID:"ses_failed",prompt:{text:"retry"}}))
    assert.equal(activations.filter(session=>session==="ses_failed").length,1)
    const permission={sessionID:"ses_failed",action:"edit",effect:"allow"}
    await hooks.get("permission:evaluate")!(permission);assert.equal(permission.effect,"deny")
  }finally{release();await governor.close()}
  assert.deepEqual(closed.sort(),["ses_first","ses_second"])
})
test("automatic enrollment sends only native session identity and never accepts mismatched scoped credentials",async()=>{
  const proof=new Uint8Array(32).fill(7),secret=Buffer.alloc(32,9).toString("base64url")
  let calls=0
  const expected={formatVersion:1,kind:"session-enrolled",sessionID:"ses_new",workRoot:"/synthetic/test-project",projectId:id,
    controllerId:id,principalId:id,credentialId:id,token:`wh1.${id}.${id}.${secret}`}
  let reply:unknown=expected
  const client=new AutomaticEnrollmentClient("http://127.0.0.1:18671",proof,id,async(url,options)=>{
    calls++;assert.equal(url,"http://127.0.0.1:18671/internal/enrollment/v1")
    assert.deepEqual(JSON.parse(options!.body as string),{formatVersion:1,operation:"open-session",sessionID:"ses_new"})
    const headers=options!.headers as Record<string,string>
    assert.equal(headers["X-Writhelm-Enrollment-Proof"],Buffer.from(proof).toString("base64url"))
    assert.equal(headers.Authorization,undefined)
    assert.equal(options!.redirect,"error")
    return new Response(JSON.stringify(reply),{status:200})
  })
  assert.equal((await client.openSession("ses_new")).token,expected.token)
  reply={...expected,sessionID:"ses_other"};await assert.rejects(client.openSession("ses_new"))
  reply={...expected,token:expected.token.replace(`wh1.${id}.`,"wh1.00000000-0000-0000-0000-000000000002.")}
  await assert.rejects(client.openSession("ses_new"))
  reply={...expected,ownerToken:expected.token};await assert.rejects(client.openSession("ses_new"))
  await assert.rejects(client.openSession("../../project"));assert.equal(calls,4)
  let uncertainCalls=0
  const uncertain=new AutomaticEnrollmentClient("http://127.0.0.1:18671",proof,id,async()=>{uncertainCalls++;throw Error("MOCK lost enrollment reply")})
  await assert.rejects(uncertain.openSession("ses_new"));assert.equal(uncertainCalls,1)
  let busyCalls=0
  const busy=new AutomaticEnrollmentClient("http://127.0.0.1:18671",proof,id,async()=>{
    busyCalls++;return busyCalls===1?new Response("",{status:429}):new Response(JSON.stringify(expected),{status:200})
  })
  assert.equal((await busy.openSession("ses_new")).token,expected.token);assert.equal(busyCalls,2)
  let failedCalls=0
  const failed=new AutomaticEnrollmentClient("http://127.0.0.1:18671",proof,id,async()=>{failedCalls++;return new Response("",{status:503})})
  await assert.rejects(failed.openSession("ses_new"));assert.equal(failedCalls,1)
})
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
    assert.ok(primaryInstructions.includes("deterministic non-LLM operation"))
    assert.ok(primaryInstructions.includes("you cannot supply their independent review"))
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

test("primary installs candidate-commit loop and planned umbrella review without adding a per-patch reviewer or write tool",async()=>{
  const f=await fixture()
  try{
    await f.invoke("session:prompt",{sessionID:"ses_primary"})
    const request={sessionID:"ses_primary",system:[] as Array<{type:string;text:string}>,tools:{read:{},question:{},shell:{},patch:{},subagent:{}}}
    await f.invoke("session:context",request)
    const policy=request.system.map(part=>part.text).join("\n")
    for(const rule of [
      "planner -> bounded executor -> planner bearing-point control",
      "deterministic application and targeted tests -> local commit-candidate",
      "diagnostic/author-side, not independent review",
      "not independently verified, accepted, integrated or published",
      "Do not launch a new LLM applicator or independent reviewer for each small known patch",
      "Before execution, plan a bounded coherent umbrella task/MR",
      "independent gate before acceptance/integration",
      "whole exact selected MR diff against requirements",
      "Do not postpone, remove or weaken any existing prescribed earlier independent gate",
      "retain justified unaffected evidence",
      "new exact subject under the actual assignment/freshness rules",
    ])assert.ok(policy.includes(rule),rule)
    assert.deepEqual(Object.keys(request.tools),["read","question"])
    assert.equal(f.tools.has("writhelm_apply_patch"),false)
    await assert.rejects(f.invoke("tool:execute.before",{sessionID:"ses_primary",tool:"patch"}))
    await assert.rejects(f.invoke("tool:execute.before",{sessionID:"ses_primary",tool:"subagent"}))
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
