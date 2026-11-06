// SPDX-License-Identifier: AGPL-3.0-only
// Production GUI + verbatim PRIVATE pinned-host route in synthetic DOM. Not native click acceptance.
import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { createRequire } from "node:module"
import { runInNewContext } from "node:vm"
import { createRoot, createSignal } from "solid-js"
import * as solid from "solid-js"
import * as effect from "effect"
import * as sdk from "@opencode/plugin/gui"
import { parseHTML } from "linkedom"
import { exactLink } from "../src/panel.js"
import { reply } from "../src/contracts.js"

const require=createRequire(import.meta.url),host=require(process.env.OC004_HOST_ROUTE!)
const raw=readFileSync(new URL("../../tests/fixtures/bundle.json",import.meta.url),"utf8"),value=JSON.parse(raw).data
const ref={project:value.request.payload.projectId,requestId:value.request.key,requestDigest:value.request.payload.requestDigest,packetDigest:value.request.payload.packetDigest}
const literal=(r=ref)=>`writhelm-review/v1/${r.project}/${r.requestId}/${r.requestDigest}/${r.packetDigest}`
const legacy=(r=ref)=>`writhelm://review/${r.project}/${r.requestId}/${r.requestDigest}/${r.packetDigest}`
const tick=async()=>{for(let n=0;n<6;n++)await new Promise(r=>setImmediate(r))}

function fixture(reference=literal()){
 const {document,window}=parseHTML('<html><body><div id="message"><p><code></code></p></div><div id="panel"></div></body></html>')
 Object.assign(globalThis,{document,window,Element:window.Element,HTMLElement:window.HTMLElement,HTMLAnchorElement:window.HTMLAnchorElement})
 const code=document.querySelector('code')!;code.textContent=reference
 const session={key:'sidecar\nsource-route-A',id:'source-route-A',server:{id:'sidecar'},directory:'/synthetic/project',project:{worktree:'/synthetic/project'}}
 let current:any=session,contributions:any[]=[],opened:any[]=[],toasts:any[]=[],reads:any[]=[],answers=0,fileCalls=0,dispose!:()=>void,renderDispose:(()=>void)|undefined
 const bridge={status:async()=>reply('OK',{pageJSON:JSON.stringify({formatVersion:1,kind:'read',basis:{algorithm:'sha1',hex:'a'.repeat(40)},operation:'list_projects',data:{snapshotId:'00000000-0000-0000-0000-000000000777',nextCursor:null,complete:true,items:[{projectId:ref.project}]}})}),pending:async()=>reply(),
  open:async(input:any)=>{reads.push(input);return reply('OK',{handle:'00000000-0000-0000-0000-000000000899',bundleJSON:raw})},answer:async()=>{answers++;return reply()},dismiss:async()=>reply(),connect:async()=>reply(),setup:async()=>reply(),rotate:async()=>reply(),reconcile:async()=>reply()}
 const sandbox:any={console,Buffer,URL,TextDecoder,TextEncoder,document,window,setTimeout,clearTimeout,module:{exports:{}},exports:{}}
 sandbox.exports=sandbox.module.exports
 sandbox.require=(id:string)=>id==='@opencode/plugin/gui'?sdk:id==='solid-js'?solid:id==='effect'?effect:id==='electron'?{dialog:{}}:id==='node:child_process'?{spawn(){throw Error('No helper process in GUI route tests')}}:require(id)
 runInNewContext(readFileSync(new URL('../../dist/gui-experimental.cjs',import.meta.url),'utf8'),sandbox)
 const definition=sandbox.module.exports.default??sandbox.module.exports
 createRoot(d=>{dispose=d;definition.setup({app:{platform:'desktop',version:'2.2.5'},id:'writhelm-opencode',sessions:{current:()=>current},add:(token:any,item:any)=>contributions.push({token,item}),main:()=>bridge,toast:{show:(v:any)=>{toasts.push(v);return{dismiss(){}}}},layout:{open:(key:any,s:any)=>opened.push({key,session:s})}})})
 const handler=contributions.find(c=>c.token===sdk.LinkHandler).item
 const route=host.registry([handler,{priority:0,match:()=>true,exists:()=>{fileCalls++;return false},open:()=>{fileCalls++}}])
 const message=document.getElementById('message')!,abort=new AbortController()
 // Actual host callback wiring d3t supplies this originating session to links.exists/open.
 host.crn(message,message,false,abort.signal,(href:string)=>route.exists({href,session}))
 const stop=host.urn(message,()=>((href:string)=>route.open({href,session})))
 return{document,window,message,code,session,handler,route,opened,toasts,reads,setCurrent:(s:any)=>current=s,get fileCalls(){return fileCalls},get answers(){return answers},
  click(){code.dispatchEvent(Object.assign(new window.Event('click',{bubbles:true,cancelable:true}),{button:0}));},
  render(){const p=contributions.find(c=>c.token===sdk.Panel).item;createRoot(d=>{renderDispose=d;document.getElementById('panel')!.append(p.render({session:current,tab:{id:'review'},screen:{}}))})},
  close(){stop();abort.abort();renderDispose?.();dispose()}}
}

test('reference: new closed native literal parses exact immutable identifiers',()=>{assert.deepEqual(exactLink(literal()),ref);assert.deepEqual(exactLink(legacy()),ref)})
test('reference: reject profile/scheme/host/query/fragment/traversal/encoding/empty/case aliases',()=>{
 for(const text of [literal()+'?x=1',literal()+'#x',literal()+'#L1',literal()+'/',literal().replace('/v1/','/v2/'),literal().replace('writhelm-review','WRITHELM-REVIEW'),literal().replace('/v1/','//v1/'),literal().replace('/v1/','/v1/../'),literal().replace('writhelm-review','%77rithelm-review'),literal().replaceAll('/','%2f'),'https://invalid.example/'+literal(),'file:///'+literal(),literal()+'\n',' '+literal(),legacy().replace('/review/','/review//'),legacy().replace('review/','review:80/'),legacy()+'?token=x',legacy()+'#x'])assert.equal(exactLink(text),undefined,text)
})
test('pinned host: https/custom URLs are not native paths; inline code preserves rejected raw variants unlike Markdown URLs',()=>{
 assert.equal(host.Ktn(legacy()),undefined);assert.equal(host.Ltn(legacy()),undefined);assert.equal(host.allowedUri.test(legacy()),false)
 assert.equal(host.Ktn('https://example.invalid/'+literal()),'url');assert.equal(host.Ltn('https://example.invalid/'+literal()),undefined)
 assert.equal(host.Ktn(literal()),'path');assert.equal(host.Ltn(literal()+'?ignored=1'),literal())
 const f=fixture(literal()+'#L1');try{assert.notEqual(f.code.dataset.inlineCodeKind,'path');assert.equal(f.fileCalls,0);f.click();assert.equal(f.opened.length,0)}finally{f.close()}
})
test('pinned host route + production GUI: native inline-code event opens exact originating-session panel, no filesystem or answer',async()=>{
 const f=fixture();try{assert.equal(f.code.dataset.inlineCodeKind,'path');f.click();assert.equal(f.opened.length,1);assert.equal(f.opened[0].session.key,f.session.key);f.render();await tick();assert.equal(f.reads.length,1);assert.equal(f.reads[0].requestId,ref.requestId);assert.equal(f.reads[0].requestDigest,ref.requestDigest);assert.equal(f.reads[0].packetDigest,ref.packetDigest);assert.equal(f.fileCalls,0);assert.equal(f.answers,0)}finally{f.close()}
})
test('reserved malformed refs are not routed to host file fallback and never enable an answer',()=>{
 for(const text of [literal()+'?extra',literal()+'#L1',literal().replace('/v1/','/v2/'),literal().replace('writhelm-review','%77rithelm-review'),literal().replace('writhelm-review','%2577rithelm-review')]){
  const f=fixture(text);try{assert.notEqual(f.code.dataset.inlineCodeKind,'path');assert.equal(f.fileCalls,0,text);f.click();assert.equal(f.opened.length,0);assert.equal(f.answers,0)}finally{f.close()}
 }
})
test('production GUI rejects an originating reference after current session changes, without remapping it',()=>{
 const f=fixture();try{assert.equal(f.code.dataset.inlineCodeKind,'path');f.setCurrent({...f.session,key:'sidecar\nother',id:'other'});f.click();assert.equal(f.opened.length,0);assert.ok(f.toasts.some(x=>x.variant==='error'));assert.equal(f.fileCalls,0);assert.equal(f.answers,0)}finally{f.close()}
})
test('different-project reference visibly refuses in unchanged panel before exact bundle read',async()=>{
 const f=fixture(literal({...ref,project:'00000000-0000-0000-0000-000000000901'}));try{assert.equal(f.code.dataset.inlineCodeKind,'path');f.click();f.render();await tick();assert.ok(f.document.getElementById('panel')!.textContent!.includes('FORBIDDEN'));assert.equal(f.reads.length,0);assert.equal(f.answers,0);assert.equal(f.fileCalls,0)}finally{f.close()}
})

// OC-005 additive required-law cases. Original39 assertions/fixture above remain unchanged.
function nestedW(depth:number):string {
 let prefix='w'
 for(let n=0;n<depth;n++)prefix=prefix==='w'?'%77':prefix.replace('%','%25')
 return prefix+'rithelm-review'+literal().slice('writhelm-review'.length)
}
function refusedOwned(text:string){
 assert.equal(exactLink(text),undefined,'Alias must never become an accepted identity')
 const f=fixture(text)
 try {
  assert.equal(host.Ktn(text),'path');assert.equal(f.fileCalls,0,'Owned reference must NOT enter the default file observer')
  assert.equal(f.handler.match({href:text,session:f.session}),true)
  assert.notEqual(f.code.dataset.inlineCodeKind,'path');f.click()
  assert.equal(f.fileCalls,0);assert.equal(f.opened.length,0);assert.equal(f.reads.length,0);assert.equal(f.answers,0)
 }finally{f.close()}
}
test('REV-OC004-001: original eight-layer238byte input is refused without default file fallback',()=>{
 const text=nestedW(8)
 assert.ok(text.startsWith('%2525252525252577rithelm-review/'));assert.equal(Buffer.byteLength(text),238)
 assert.ok(text.length<=256);refusedOwned(text)
})
test('namespace: every full-suffix fitting depth, exact256 and first overlength states are owned but never accepted',()=>{
 let maximum=0
 for(let depth=1;nestedW(depth).length<=256;depth++){refusedOwned(nestedW(depth));maximum=depth}
 assert.equal(maximum,17);assert.equal(nestedW(maximum).length,256);assert.equal(nestedW(maximum+1).length,258)
 refusedOwned(nestedW(maximum)+'x');refusedOwned(nestedW(maximum+1)) //257 and258 raw chars
 // More than the old256-character prefix: no hidden truncated-tail acceptance/ownership boundary.
 assert.ok(nestedW(130).indexOf('rithelm-review')>256);refusedOwned(nestedW(130))
})
test('namespace: mixed encoded characters/nesting and trailing malformed percent do not fall through',()=>{
 const tail=literal().slice('writhelm-review'.length)
 const prefixes=['%77%72%69%74%68%65%6C%6d-%72%65%76%69%65%77','%2577%72i%2574helm-rev%69ew','%2525252525252577r%69thelm-r%65view','WRITHELM-REVIEW']
 for(const prefix of prefixes)for(const suffix of ['','%','%2','%ZZ'])refusedOwned(prefix+tail+suffix)
 for(const prefix of ['writhelm-review%2Fv1/','%77rithelm-review%2fv1/'])refusedOwned(prefix+literal().split('/').slice(2).join('/'))
})
test('namespace: unrelated and prefix-disambiguated paths retain the ordinary host route',()=>{
 const unrelated=['src/example.ts','writhelm-review-other/file','writhelm-reviewx/v1/x','%77rithelm-reviewx/file','writ%ZZhelm-review/file','%2577rithelm-review%2Efile/path']
 // The encoded namespace extends past the old prefix window. Full-input decoding must retain
 // the following disambiguating character, not guess that a partial prefix is the whole input.
 const longPrefix=nestedW(121).split('/v1/')[0]
 assert.ok(longPrefix.length>256);unrelated.push(longPrefix+'x/file')
 for(const text of unrelated){
  assert.equal(exactLink(text),undefined)
  const f=fixture(text)
  try{assert.equal(host.Ktn(text),'path');assert.equal(f.handler.match({href:text,session:f.session}),false,text);assert.equal(f.fileCalls,1,'Unrelated ordinary path must not be hijacked');f.click();assert.equal(f.opened.length,0);assert.equal(f.answers,0)}finally{f.close()}
 }
})
