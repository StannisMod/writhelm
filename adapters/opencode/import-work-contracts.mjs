// One-time/source-update importer of Writhelm's original sealed structural schemas. No examples,
// private records or source-code inference. Ordinary builds use the retained portable resource.
import {readFile,readdir,writeFile} from "node:fs/promises"
import {createHash} from "node:crypto"
import path from "node:path"
import {parse,stringify,isLosslessNumber} from "lossless-json"
const source=process.argv[2],output=process.argv[3]
if(!source||!output)throw Error("Usage: import-work-contracts.mjs EXPLICIT_SCHEMA_DIRECTORY OUTPUT_JSON")
const byId=new Map(),digests={}
for(const name of await readdir(source)){
 if(!name.endsWith(".json"))continue
 const raw=await readFile(path.join(source,name),"utf8"),value=parse(raw)
 if(typeof value.$id==="string"){byId.set(value.$id,value);digests[name]=createHash("sha256").update(raw).digest("hex")}
}
const definitions={},mapping=new Map()
function resolve(reference){
 const [id,pointer]=reference.split("#")
 const root=byId.get(id)
 if(!root||!pointer?.startsWith("/$defs/"))throw Error("Unresolved offline structural reference: "+reference)
 const name=pointer.slice(7),value=root.$defs[name]
 if(!value)throw Error("Absent definition: "+reference)
 if(!mapping.has(reference)){
   const key=id.slice("urn:writhelm:".length).replaceAll(":","-")+"--"+name
   mapping.set(reference,key);definitions[key]={};definitions[key]=convert(value)
 }
 return {$ref:"#/$defs/"+mapping.get(reference)}
}
function convert(v){
 if(isLosslessNumber(v))return v
 if(Array.isArray(v))return v.map(convert)
 if(v&&typeof v==="object"){
   if(v.$ref)return resolve(v.$ref)
   return Object.fromEntries(Object.entries(v).filter(([k])=>!["$schema","$id","$comment","description"].includes(k))
     .map(([k,x])=>[k,convert(x)]))
 }
 return v
}
function operations(id,kind,field){
 const root=byId.get(id),out={}
 for(const branch of root.$defs[kind].oneOf){
  const operation=branch.properties.operation.const
  if(typeof operation!=="string")throw Error("Nonliteral operation")
  out[operation]=convert(branch.properties[field])
 }
 return out
}
const commands=operations("urn:writhelm:work-commands:v3","command","payload")
const reads=operations("urn:writhelm:work-reads:v2","read","subject")
// Two historical schema omissions contradict their own closed required lists and existing typed
// decoders (WStep/WPending description = workText(1,65536)). Preserve existing accepted wire
// behavior; do not delete the required field or relax additionalProperties to make tests pass.
const structuralCorrections=[]
for(const name of ["work-model2--step","work-model2--pending"]){
 const shape=definitions[name]
 if(!shape||shape.additionalProperties!==false||!shape.required.includes("description")||shape.properties.description)
   throw Error("Unexpected retained-schema basis for description correction")
 shape.properties.description={type:"string",minLength:1,maxLength:65536}
 structuralCorrections.push(name+".description")
}
await writeFile(output,stringify({formatVersion:1,sourceDigests:digests,structuralCorrections,commands,reads,$defs:definitions})+"\n")
console.log("Imported exact sealed work structural contract:",Object.keys(commands).length,"commands,",Object.keys(reads).length,"reads; no offline references unresolved")
