// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import {createHash} from "node:crypto"
import {selectReviewDisplay,displaySourceLanguage} from "../src/review-display.js"
import {ReviewDisplayCustody} from "../src/review-display-custody.js"
import {LinuxNativeCustody} from "../src/native-runtime-custody.js"
import {provisionReviewSourceLanguage} from "../src/primary-bootstrap.js"
import * as fs from "node:fs/promises"
import path from "node:path"
import {spawnSync} from "node:child_process"
const id="00000000-0000-0000-0000-000000000001"
const scope={storeId:id,projectId:id,sessionID:"ses_language_fixture",requestId:id,requestDigest:"a".repeat(64),packetDigest:"b".repeat(64)}
const packet={items:[{id}],presentations:[{language:"ru",sourceLanguage:"en",items:[{itemId:id,title:"Правило",text:"Полный текст."}]}]}
test("only complete matching display or explicitly declared same-language source is selectable",()=>{
  assert.equal(selectReviewDisplay(packet,"ru-RU").kind,"presentation")
  assert.equal(selectReviewDisplay({...packet,presentations:[]},"ru-RU","ru").kind,"source")
  for(const [p,language,source] of [
    [packet,"de",undefined],[{...packet,presentations:[]},"ru",undefined],
    [{...packet,presentations:[]},"ru","en"],[packet,"",undefined],
    [{...packet,presentations:[{...packet.presentations[0],items:[]}]},"ru",undefined],
    [{...packet,presentations:[{...packet.presentations[0],items:[packet.presentations[0].items[0],packet.presentations[0].items[0]]}]},"ru",undefined],
  ] as const)assert.throws(()=>selectReviewDisplay(p,language,source),/REVIEW_DISPLAY_PREPARATION_REQUIRED/)
})
test("source declaration is immutable, exact-chat/version bound and contains no source/approval",async()=>{
  const rows=new Map<string,string>()
  const storage={read:async(k:string,id:string)=>rows.get(k+id),
    retain:async(k:string,id:string,raw:string)=>{
      const old=rows.get(k+id);if(old!==undefined&&old!==raw)throw Error("immutable conflict");rows.set(k+id,raw);return true},
    replace:async(k:string,id:string,expected:string,raw:string)=>{
      assert.equal(createHash("sha256").update(rows.get(k+id)!).digest("hex"),expected);rows.set(k+id,raw);return true}}
  const store=new ReviewDisplayCustody(storage)
  const raw=await store.retainSourceLanguage(scope,"ru")
  assert.equal(await store.retainSourceLanguage(scope,"ru"),raw)
  await assert.rejects(store.retainSourceLanguage(scope,"en"),/immutable conflict/)
  const {storeId:_,...view}=scope
  assert.equal(displaySourceLanguage(raw,view),"ru")
  for(const delta of [{sessionID:"ses_other"},{requestId:"00000000-0000-0000-0000-000000000002"},
    {packetDigest:"c".repeat(64)},{requestDigest:"d".repeat(64)},{projectId:"00000000-0000-0000-0000-000000000002"}]){
    assert.throws(()=>displaySourceLanguage(raw,{...view,...delta}),/REVIEW_DISPLAY_PREPARATION_REQUIRED/)
    assert.equal(await store.sourceLanguage({...scope,...delta}),undefined)
  }
  assert.deepEqual(Object.keys(JSON.parse(raw)).sort(),["formatVersion","language","scope"])
  await store.rememberInterface(scope.storeId,scope.projectId,scope.sessionID,"ru")
  assert.equal(await store.interfaceLanguage(scope.storeId,scope.projectId,scope.sessionID),"ru")
  await store.rememberInterface(scope.storeId,scope.projectId,scope.sessionID,"de")
  assert.equal(await store.interfaceLanguage(scope.storeId,scope.projectId,scope.sessionID),"de")
  assert.equal(await store.interfaceLanguage(scope.storeId,scope.projectId,"ses_other"),undefined)
})
test("trusted stdin provisioning binds the original bootstrap and cannot rewrite a language declaration",async()=>{
  const base=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(base)
  const area=await fs.mkdtemp(path.join(base,"display-declaration-")),root=path.join(area,"private"),work=path.join(area,"work")
  await fs.mkdir(root,{mode:0o700});await fs.mkdir(work)
  const bootstrap=new LinuxNativeCustody(path.join(root,"bootstrap"),[work]);await bootstrap.initialize()
  const original=JSON.stringify({formatVersion:1,storeId:id,notice:"Synthetic metadata bootstrap; no credentials or model."})
  await bootstrap.retain("automatic-bootstrap","current",original)
  const input={root,forbiddenAreas:[work],expectedBootstrapDigest:createHash("sha256").update(original).digest("hex"),scope,language:"en"}
  const result=spawnSync(process.execPath,[new URL("../src/primary-bootstrap.js",import.meta.url).pathname,"provision-review-source-language"],
    {input:JSON.stringify(input),encoding:"utf8",env:{}})
  assert.equal(result.status,0,result.stderr)
  assert.equal(JSON.parse(result.stdout).kind,"exact-source-language-retained")
  await provisionReviewSourceLanguage(input)
  await assert.rejects(provisionReviewSourceLanguage({...input,language:"ru"}))
  await assert.rejects(provisionReviewSourceLanguage({...input,expectedBootstrapDigest:"0".repeat(64)}))
  assert.equal(await bootstrap.read("automatic-bootstrap","current"),original)
})
