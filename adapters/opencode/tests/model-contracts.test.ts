// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import {createRequire} from "node:module"
import {knowledgeProtocol,operationSchema,validateModelContract,modelContractHelp,typedReadInput,typedKnowledgeReadInput} from "../src/model-contracts.js"
import {prepareKnowledgeDocument,knowledgeIntentIdentity} from "../src/knowledge-preparation.js"
import {validateKnowledgeRead} from "../src/model-read-knowledge.js"
import {exactJSON} from "../src/strict-json.js"
import workProtocol from "../src/generated/model-work.js"
const uuid=(n:number)=>`00000000-0000-0000-0000-${String(n).padStart(12,"0")}`
const clauses=[{id:"TEST-01",title:"Точный заголовок 🧭",text:"Обязательство.\r\nБез нормализации: е\u0308."},
  {id:"TEST-02",title:"Второй пункт",text:"Неодобренное предложение."}]
const source=JSON.stringify({kind:"canonical-codec-source-v1",files:[{path:"codec/Test.kt",sha256:"a".repeat(64),source:"// public diagnostic\n".repeat(1000)}]})
test("constraint-only and mixed union/base constraints agree with AJV without coercion",()=>{
  const Ajv=createRequire(import.meta.url)("ajv/dist/2020.js").default,ajv=new Ajv({strict:false})
  const cases:[any,any[]][]=[
    [{maxItems:0},[[],[1],"not an array"]],
    [{minItems:1},[[],[1],null]],
    [{required:["x"],properties:{x:{minimum:2}},additionalProperties:false},[{}, {x:1},{x:2},{x:2,y:0},null,[]]],
    [{minLength:2,maxLength:2},["🧭а","🧭","abc",3]],
    [{minimum:1,maximum:2},[0,1,2,3,"0"]],
    [{const:"a",minLength:2},["a","ab"]],
    [{type:"array",uniqueItems:true},[[{a:1,b:2},{b:2,a:1}],[{a:1},{a:2}],[1,1],[1,2]]],
    [{type:"integer",minimum:2,anyOf:[{const:1},{const:2}]},[1,2,3,"2"]],
    [{$defs:{n:{type:"integer"}},$ref:"#/$defs/n",minimum:2},[1,2,"2"]],
    [{oneOf:[{const:{a:1,b:2}},{const:[1,2]}]},[{b:2,a:1},[1,2],{}]],
  ]
  for(const [schema,values]of cases){
    const validate=ajv.compile(schema)
    for(const value of values)assert.equal((()=>{try{validateModelContract(schema,value);return true}catch{return false}})(),
      validate(value),JSON.stringify({schema,value}))
  }
  for(const schema of [{not:{}},{anyOf:[{}, {not:{}}]},{if:{const:1},then:{},else:{not:{}}},
    {properties:{unused:{not:{}}}},{$defs:{unused:{not:{}}}},{type:"number"}])
    assert.throws(()=>validateModelContract(schema,1),/INVALID_MODEL_INPUT/)
  validateModelContract({type:"integer"},Number.MAX_SAFE_INTEGER)
  assert.throws(()=>validateModelContract({type:"integer"},Number.MAX_SAFE_INTEGER+1))
  assert.throws(()=>validateModelContract({type:"string"},"\ud800"))
})
test("actual retained task and retrospective conditional branches agree with AJV",()=>{
  const Ajv=createRequire(import.meta.url)("ajv/dist/2020.js").default,ajv=new Ajv({strict:false})
  const defs=(workProtocol as any).$defs
  // Deterministic structural witnesses; AJV independently checks every complete fixture.
  const witness=(s:any):any=>{
    if(s.$ref)return witness(defs[s.$ref.slice(8)])
    if(s.const!==undefined)return s.const
    if(s.enum)return s.enum[0]
    if(s.oneOf)return witness(s.oneOf[0])
    if(s.type==="null")return null
    if(s.type==="integer")return s.minimum??0
    if(s.type==="boolean")return false
    if(s.type==="string"){
      if(s.pattern?.includes("{64}"))return "a".repeat(64)
      if(s.pattern?.includes("{8}"))return uuid(1)
      return "a".repeat(Math.max(1,s.minLength??0))
    }
    if(s.type==="array")return Array.from({length:s.minItems??0},()=>witness(s.items))
    if(s.type==="object")return Object.fromEntries(s.required.map((k:string)=>[k,witness(s.properties[k])]))
    throw Error("No witness for "+JSON.stringify(s))
  }
  const task=defs["work-model2--task-revisions"],retro=defs["work-commands-v3--record_retrospective"].properties.data
  const t=witness(task);t.state="executable"
  for(const key of ["base","executionProfile","verificationPlan"])
    t[key]=witness(task.allOf[0].then.properties[key])
  const r=witness(retro),observation=witness(retro.properties.observations.items)
  const compare=(shape:any,value:any,expected:boolean)=>{
    const schema={...shape,$defs:defs},validate=ajv.compile(schema)
    assert.equal(validate(value),expected,JSON.stringify(validate.errors))
    if(expected)validateModelContract(schema,value)
    else assert.throws(()=>validateModelContract(schema,value))
  }
  compare(task,t,true);compare(task,{...t,unresolved:["blocked"]},false)
  compare(task,{...t,state:"draft",unresolved:["blocked"]},true)
  compare(retro,r,true);compare(retro,{...r,observations:[observation]},false)
  compare(retro,{...r,outcome:"findings"},false)
  compare(retro,{...r,outcome:"findings",observations:[observation]},true)
})
test("authoring generates exact Unicode byte partition/digests independently verified by existing read validator",()=>{
  const p=prepareKnowledgeDocument(uuid(1),uuid(2),uuid(3),clauses,"Контекст 🧭")
  const data=exactJSON(JSON.stringify({...p,proposedBy:uuid(4),
    bodyDigest:createRequire(import.meta.url)("node:crypto").createHash("sha256").update(p.body).digest("hex")}),()=>{throw Error("invalid")})
  validateKnowledgeRead(exactJSON(JSON.stringify({operation:"read_revision",projectId:uuid(1),
    subject:{revisionKey:p.revisionKey,revisionDigest:p.revisionDigest}}),()=>{throw Error("invalid")}),data,(ok)=>assert.ok(ok))
  const bytes=Buffer.from(p.body)
  assert.equal(bytes.subarray(p.structure.clauses[0]!.span.start,p.structure.clauses[0]!.span.endExclusive).toString(),
    "TEST-01 — Точный заголовок 🧭\nОбязательство.\r\nБез нормализации: е\u0308.\n\n")
  assert.deepEqual(p,prepareKnowledgeDocument(uuid(1),uuid(2),uuid(3),clauses,"Контекст 🧭"))
  assert.throws(()=>prepareKnowledgeDocument(uuid(1),uuid(2),uuid(3),[clauses[0]!,clauses[0]!]))
  assert.throws(()=>prepareKnowledgeDocument(uuid(1),uuid(2),uuid(3),[{...clauses[0]!,text:"\ud800"}]))
})
test("generated schemas drive both tool registration and closed runtime validation without evaluating source",()=>{
  const require=createRequire(import.meta.url),Ajv=require("ajv/dist/2020.js").default
  const ajv=new Ajv({strict:false})
  for(const kind of ["commands","reads","authoring"] as const)
    for(const operation of Object.keys(knowledgeProtocol[kind]))assert.equal(typeof ajv.compile(operationSchema(kind,operation)),"function")
  const schema=operationSchema("authoring","propose_document"),validate=ajv.compile(schema)
  const good={intentId:"spec-1",clauses}
  assert.equal(validate(good),true);validateModelContract(schema,good)
  for(const bad of [{...good,owner:"actor"},{...good,clauses:[]},{...good,clauses:[{...clauses[0],approved:true}]}]){
    assert.equal(validate(bad),false);assert.throws(()=>validateModelContract(schema,bad))
  }
  const typed=ajv.compile(typedKnowledgeReadInput)
  assert.equal(typed({operation:"list_documents",subject:{cursor:null,pageSize:32}}),true)
  assert.equal(typed({operation:"list_documents",subject:{cursor:null,pageSize:33}}),false)
  assert.equal(typed({operation:"revoke_access",subject:{principalId:uuid(1)}}),false)
})
test("logical intent retries keep identity; a different chat or purpose cannot inherit the effect",()=>{
  const a=knowledgeIntentIdentity("exact-chat-binding","review-1","command")
  assert.equal(a,knowledgeIntentIdentity("exact-chat-binding","review-1","command"))
  assert.notEqual(a,knowledgeIntentIdentity("different-chat-binding","review-1","command"))
  assert.notEqual(a,knowledgeIntentIdentity("exact-chat-binding","review-1","request"))
  assert.match(a,/^[a-f0-9-]{36}$/)
  assert.throws(()=>knowledgeIntentIdentity("binding","","command"))
})
test("operation/definition help and source pages stay bounded, no whole-bundle or external-path fallback",()=>{
  const schemas={"knowledge-command1":source,"knowledge-read1":source,"work-command3":source}
  const index=modelContractHelp(schemas,{name:"knowledge-command1"})
  assert.ok(Buffer.byteLength(index)<4000);assert.equal(index.includes("// public diagnostic"),false)
  const operation=JSON.parse(modelContractHelp(schemas,{name:"knowledge-command1",operation:"request_approval"}))
  assert.deepEqual(operation.schema.required,["requestId","packet"])
  assert.ok(operation.definitions.includes("packet"))
  const detail=modelContractHelp(schemas,{name:"knowledge-command1",operation:"request_approval",definition:"packet"})
  assert.ok(Buffer.byteLength(detail)<8000)
  const page=JSON.parse(modelContractHelp(schemas,{name:"work-command3",file:"codec/Test.kt",offset:0}))
  assert.equal(page.source.length,4000);assert.equal(page.nextOffset,4000)
  assert.throws(()=>modelContractHelp(schemas,{name:"work-command3",file:"../../private/profile.json"}))
  assert.throws(()=>modelContractHelp(schemas,{name:"knowledge-command1",operation:"resolve_approval",definition:"missing"}))
  const read=typedReadInput({operation:"list_documents",subject:{cursor:null,pageSize:32}},uuid(1))
  assert.equal(JSON.parse(read.json).projectId,uuid(1))
  assert.throws(()=>typedReadInput({operation:"list_documents",subject:{cursor:null,pageSize:32},projectId:uuid(2)},uuid(1)))
})
