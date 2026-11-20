// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import {ModelKnowledge,ReviewSourceReadHold} from "../src/model-knowledge.js"
import {ModelReads} from "../src/model-read.js"
import type {BoundWorkChannel} from "../src/connected-tools.js"
import {knowledgeIntentIdentity} from "../src/knowledge-preparation.js"
import {exactJSON} from "../src/strict-json.js"
import {operationSchema,validateModelContract} from "../src/model-contracts.js"
import {createRequire} from "node:module"

test("review preserves a dependency read refusal and never reaches command submission",async()=>{
  const itemRef="wr1_"+"a".repeat(24),dependencyRef="wr1_"+"b".repeat(24)
  let sends=0,reads=0
  const modelReads={read:async(_domain:string,_channel:unknown,_session:string,input:any)=>{
    reads++
    return {content:JSON.stringify(input.reference===itemRef?
      {kind:"read",operation:"read_clause",data:{reference:{kind:"clause",clauseId:"TECH-01"}}}:
      {kind:"error",code:"FORBIDDEN"})}
  }} as unknown as ModelReads
  const input={intentId:"same-technical-review",items:[{key:"TECH-01",target:{reference:itemRef},before:null}],
    question:"Review?",scope:"One proposal.",rationale:"Scoped synthetic test.",consequences:["Proposal only."],
    alternatives:["Return comments."],uncertainties:[],contexts:[],dependencies:[{reference:dependencyRef}],informationGaps:[]}
  const channel={readBinding:"synthetic exact controller binding",projectID:"00000000-0000-0000-0000-000000000001",
    originalReview:async()=>JSON.stringify({projectId:"00000000-0000-0000-0000-000000000001",
      requestKey:knowledgeIntentIdentity("synthetic exact controller binding",input.intentId,"review-command"),operation:"request_approval",
      payload:{requestId:knowledgeIntentIdentity("synthetic exact controller binding",input.intentId,"approval-request")}})} as unknown as BoundWorkChannel
  await assert.rejects(()=>new ModelKnowledge(modelReads).review(input,channel,"ses_source_hold",new AbortController().signal,async()=>{
    sends++;return ""
  }),error=>{
    assert.ok(error instanceof ReviewSourceReadHold)
    assert.equal(error.code,"FORBIDDEN");assert.equal(error.reference,dependencyRef)
    assert.match(error.message,/No canonical command was submitted/)
    return true
  })
  assert.equal(reads,2);assert.equal(sends,0)
})

const project="00000000-0000-0000-0000-000000000001"
const binding="synthetic exact controller binding"
const revisionKey={document:{projectId:project,documentId:"00000000-0000-0000-0000-000000000002"},
  revisionId:"00000000-0000-0000-0000-000000000003"}
const sourceRef="wr1_"+"c".repeat(24)
const closureInput=()=>({intentId:"atomic-review",reviewLanguage:"ru",items:[{key:"TECH-01",target:{reference:sourceRef,clauseId:"TECH-01"},before:null}],
  question:"Review?",scope:"One proposal.",rationale:"Bounded synthetic closure.",consequences:["Proposal only."],
  alternatives:["Return comments."],uncertainties:[],contexts:[],
  dependencies:Array.from({length:25},(_,i)=>({reference:sourceRef,clauseId:`DEP-${i+1}`})),informationGaps:[],
  presentations:[{language:"ru",sourceLanguage:"en",items:[{itemKey:"TECH-01",title:"Предложение",text:"Точный текст."}]}],
  followup:{requestId:"00000000-0000-0000-0000-000000000004",items:[
    {previousItemId:null,itemKey:"TECH-01",disposition:"added",explanation:"Synthetic added point."}]}})

test("26-source review submits one command with offline exact selectors and no service reads",async()=>{
  let local=0,sends=0;let submitted=""
  const reads={reviewSelector:async()=>{
    local++;return {operation:"read_revision",subject:{revisionKey,revisionDigest:"a".repeat(64)}}
  },read:async()=>{throw Error("No source HTTP reads allowed")}} as unknown as ModelReads
  const model=new ModelKnowledge(reads),input=closureInput()
  const channel={projectID:project,readBinding:binding,originalReview:async()=>undefined} as unknown as BoundWorkChannel
  await model.review(input,channel,"ses_atomic",new AbortController().signal,async raw=>{sends++;submitted=raw;return "synthetic receipt"})
  assert.equal(local,1);assert.equal(sends,1)
  const command=exactJSON(submitted,()=>{throw Error("invalid command")})
  assert.equal(command.operation,"submit_review")
  const projected=JSON.parse(submitted)
  validateModelContract(operationSchema("commands","submit_review"),projected.payload)
  const validate=createRequire(import.meta.url)("ajv/dist/2020.js").default
  const ajv=new validate({strict:false})
  const legacySchema=JSON.parse(await (await import("node:fs/promises")).readFile(
    new URL("../../src/schemas/service.json",import.meta.url),"utf8"))
  const admit=ajv.compile({...legacySchema,$ref:"#/$defs/agentCommand"})
  assert.equal(admit(projected),true,JSON.stringify(admit.errors))
  assert.equal(command.payload.draft.dependencies.length,25)
  assert.equal(command.payload.draft.items[0].target.clauseId,"TECH-01")
  assert.equal(command.payload.draft.presentations[0].items[0].itemId,command.payload.draft.items[0].id)
  assert.equal(command.payload.draft.followup.requestDigest,undefined) // same-H predecessor resolution belongs to service
  const retry={...channel,originalReview:async()=>submitted}
  await model.review(input,retry,"ses_atomic",new AbortController().signal,async raw=>{
    assert.equal(raw,submitted);return "same synthetic receipt"
  })
  await assert.rejects(()=>model.review({...input,scope:"Changed"},retry,"ses_atomic",new AbortController().signal,
    async()=>{throw Error("Conflicting operation must not dispatch")}),/Original review input conflict/)
})

test("new friendly review refuses missing language/coverage before command custody; old originals still reconcile",async()=>{
  let sends=0
  const reads={reviewSelector:async()=>({operation:"read_revision",subject:{revisionKey,revisionDigest:"a".repeat(64)}})} as unknown as ModelReads
  const model=new ModelKnowledge(reads),channel={projectID:project,readBinding:binding,originalReview:async()=>undefined} as unknown as BoundWorkChannel
  const {reviewLanguage:_,...withoutLanguage}=closureInput()
  for(const input of [
    withoutLanguage,
    {...closureInput(),presentations:[]},
    {...closureInput(),presentations:[{language:"ru",sourceLanguage:"ru",items:closureInput().presentations[0].items}]},
    {...closureInput(),presentations:[{language:"ru",sourceLanguage:"en",items:[{itemKey:"wrong",title:"Title",text:"Text"}]}]},
  ])await assert.rejects(()=>model.review(input,channel,"ses_new",new AbortController().signal,async()=>{sends++;return ""}),/Review preparation held/)
  assert.equal(sends,0)
  await assert.rejects(()=>model.review(closureInput(),channel,"ses_new",new AbortController().signal,async()=>{
    sends++;return ""
  },"de"),/REVIEW_LANGUAGE_MISMATCH/)
  await assert.rejects(()=>model.review(closureInput(),channel,"ses_new",new AbortController().signal,async()=>{
    sends++;return ""
  },null),/INTERFACE_LANGUAGE_PENDING/)
  assert.equal(sends,0)
  const input=closureInput();let original=""
  await model.review(input,channel,"ses_new",new AbortController().signal,async raw=>{original=raw;return "receipt"})
  const {reviewLanguage:__,...historical}=input
  await model.review(historical,{...channel,originalReview:async()=>original},"ses_new",new AbortController().signal,async raw=>{
    assert.equal(raw,original);return "same receipt"
  },"de") // a changed interface preference must not replace an uncertain historical command
})
