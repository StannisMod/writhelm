// SPDX-License-Identifier: AGPL-3.0-only
// Extend legacy service projections from the executable KnowledgeProtocol declaration.
// Existing definitions/operation branches are not reconstructed or reinterpreted.
import {readFile,writeFile} from "node:fs/promises"
const source=process.env.WRITHELM_MODEL_PROTOCOL??"../../backend/server/build/generated/model-contracts/writhelm/model-knowledge-v1.json"
const protocol=JSON.parse(await readFile(source,"utf8"))
const schema=protocol.commands.submit_review
if(!schema)throw Error("Generate current model contracts first")
const files=["command-tool-v1.json","read-tool-v1.json","read-v1.json","result-v1.json"]
const resources="../../backend/server/src/main/resources/dev/writhelm/review/"
const rewrite=value=>{
  if(Array.isArray(value))return value.map(rewrite)
  if(value&&typeof value==="object")return Object.fromEntries(Object.entries(value).map(([k,v])=>
    [k,k==="$ref"&&typeof v==="string"?v.replace("#/$defs/","#/$defs/submission_"):rewrite(v)]))
  return value
}
for(const name of files){
  const file=resources+name,original=await readFile(file,"utf8"),doc=JSON.parse(original),defs=doc.$defs
  for(const code of ["GIT_UNAVAILABLE","GIT_INCOMPATIBLE"])
    if(!defs.errorCode.enum.includes(code))defs.errorCode.enum.push(code)
  const needed=new Set()
  const collect=value=>{
    if(!value||typeof value!=="object")return
    if(typeof value.$ref==="string"){
      const key=value.$ref.replace("#/$defs/","")
      if(!needed.has(key)){needed.add(key);collect(schema.$defs[key])}
    }
    for(const [k,v]of Object.entries(value))if(k!=="$defs")collect(v)
  }
  collect(schema)
  for(const key of needed)defs["submission_"+key]=rewrite(schema.$defs[key])
  const {$defs,...payload}=schema
  defs.submitReviewPayload=rewrite(payload)
  for(const key of ["command","agentCommand"]){
    const variants=defs[key].oneOf
    const branch={properties:{operation:{const:"submit_review"},payload:{$ref:"#/$defs/submitReviewPayload"}}}
    const old=variants.findIndex(v=>v.properties?.operation?.const==="submit_review")
    if(old<0)variants.push(branch);else variants[old]=branch
  }
  if(!defs.coreOperation.enum.includes("submit_review"))defs.coreOperation.enum.push("submit_review")
  const success=defs.success.oneOf.find(v=>v.properties?.operation?.enum?.includes("request_approval"))
  if(!success.properties.operation.enum.includes("submit_review"))success.properties.operation.enum.push("submit_review")
  const expected=JSON.stringify(doc,null,2)+"\n"
  if(process.argv.includes("--check")){
    if(original!==expected)throw Error("Stale review submission schema: "+name)
  }else await writeFile(file,expected)
  if(name==="result-v1.json"){
    const pinned="src/schemas/service.json"
    if(process.argv.includes("--check")){
      if(await readFile(pinned,"utf8")!==expected)throw Error("Stale pinned service schema")
    }else await writeFile(pinned,expected)
  }
}
