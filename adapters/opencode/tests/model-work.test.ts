// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import {WorkModelContracts,prepareTypedWork,workOperationSchema,modelWorkOperations} from "../src/model-work.js"
import {createRequire} from "node:module"
const id="00000000-0000-0000-0000-000000000001"
const plan={key:{projectId:id,id,revision:"0"},task:{projectId:id,taskId:id,revision:"0"},
  expectedGeneration:null,steps:[{id,description:"Продолжить работу","state":"pending",obligations:["Точное обязательство"],blockers:[]}],
  reason:"Первая версия",previousCheckpoint:null,bodies:[],assignment:{assignmentId:id,generation:"0"}}
test("work contract selection loads one exact schema in only its originating chat",()=>{
  const contracts=new WorkModelContracts()
  const before=JSON.stringify(contracts.input("commands","ses_second"))
  const help=JSON.parse(contracts.describe("ses_first",{kind:"commands",operation:"update_plan"}))
  assert.equal(help.inputLoaded,true)
  const selected=contracts.input("commands","ses_first")
  assert.equal(selected.properties.operation.const,"update_plan")
  assert.ok(JSON.stringify(selected).includes("description"))
  assert.equal(JSON.stringify(contracts.input("commands","ses_second")),before)
  assert.ok(Buffer.byteLength(contracts.describe("ses_first",{kind:"commands"}))<5000)
  assert.ok(Buffer.byteLength(JSON.stringify(selected))<32000)
})
test("typed work preserves exact maximum Int64 and refuses rounded or excessive counters before dispatch",()=>{
  const command={operation:"update_plan",requestKey:id,payload:{...plan,expectedGeneration:"9223372036854775807"}}
  const raw=prepareTypedWork("commands",command,id)
  assert.ok(raw.includes('"expectedGeneration":9223372036854775807'))
  assert.ok(raw.includes('"description":"Продолжить работу"'))
  assert.throws(()=>prepareTypedWork("commands",{...command,payload:{...plan,expectedGeneration:9223372036854775807}},id))
  assert.throws(()=>prepareTypedWork("commands",{...command,payload:{...plan,expectedGeneration:"9223372036854775808"}},id))
  assert.throws(()=>prepareTypedWork("commands",{...command,payload:{...plan,ownerCredential:"not allowed"}},id))
  assert.throws(()=>prepareTypedWork("commands",{...command,operation:"revoke_access"},id))
})
test("all retained operation schema closures compile offline without source-code or network resolution",()=>{
  const require=createRequire(import.meta.url),Ajv=require("ajv/dist/2020.js").default
  const ajv=new Ajv({strict:false})
  assert.equal(modelWorkOperations.commands.length,34);assert.equal(modelWorkOperations.reads.length,34)
  for(const kind of ["commands","reads"] as const)
    for(const operation of modelWorkOperations[kind])assert.equal(typeof ajv.compile(workOperationSchema(kind,operation)),"function",operation)
  const validate=ajv.compile(new WorkModelContracts().input("commands"))
  assert.equal(validate({operation:"update_plan",requestKey:id,payload:plan}),true)
  const selected=new WorkModelContracts()
  selected.describe("ses_first",{kind:"commands",operation:"update_plan"})
  const exact=ajv.compile(selected.input("commands","ses_first"))
  assert.equal(exact({operation:"update_plan",requestKey:id,payload:plan}),true)
  assert.equal(exact({operation:"update_plan",requestKey:id,payload:{...plan,ownerCredential:"wrong"}}),false)
})
