// SPDX-License-Identifier: AGPL-3.0-only
import protocol from "./generated/model-knowledge.js"
import {nativeUnavailable} from "./native-runtime-client.js"
type Schema=Record<string,any>
export class ModelContractFault extends Error {
  constructor(readonly field:string){super("INVALID_MODEL_INPUT at "+field+"; follow the typed operation schema. No command was sent.")}
}
export const knowledgeProtocol=protocol as unknown as {formatVersion:number;commands:Record<string,Schema>;reads:Record<string,Schema>;authoring:Record<string,Schema>;help:Record<string,string>}
/** No eval/compilation or independent handwritten shape rules. Interprets the bounded generated
 * structural vocabulary only. Unknown schema features fail closed; service domain admission remains
 * authoritative. Exact numbers elsewhere retain their existing lossless wire path. */
export function validateModelContract(schema:Schema,value:unknown):void {
  let nodes=0
  let field="$"
  // Check every schema branch before evaluation: an unsupported feature must not disappear
  // behind a successful union arm or an unselected conditional. Property/definition names
  // and const/enum values are data, not schema keywords.
  const keywords=new Set(["$defs","$ref","type","const","enum","anyOf","oneOf","allOf","if","then","else",
    "minLength","maxLength","pattern","minimum","maximum","items","minItems","maxItems","uniqueItems",
    "properties","required","additionalProperties","description"])
  const inspect=(s:any,depth=0):void=>{
    if(++nodes>100000||depth>40||!s||typeof s!=="object"||Array.isArray(s)||
      Object.keys(s).some(key=>!keywords.has(key)))throw new ModelContractFault("$")
    if(s.type!==undefined&&!["null","boolean","string","integer","array","object"].includes(s.type))
      throw new ModelContractFault("$")
    if(s.additionalProperties!==undefined&&typeof s.additionalProperties!=="boolean")throw new ModelContractFault("$")
    for(const key of ["$defs","properties"])for(const child of Object.values(s[key]??{}))inspect(child,depth+1)
    for(const key of ["anyOf","oneOf","allOf"])if(s[key]!==undefined){
      if(!Array.isArray(s[key]))throw new ModelContractFault("$")
      for(const child of s[key])inspect(child,depth+1)
    }
    for(const key of ["items","if","then","else"])if(s[key]!==undefined)inspect(s[key],depth+1)
  }
  inspect(schema)
  nodes=0
  const identity=(v:any):string=>Array.isArray(v)?"["+v.map(identity).join(",")+"]":
    v&&typeof v==="object"?"{"+Object.keys(v).sort().map(k=>JSON.stringify(k)+":"+identity(v[k])).join(",")+"}":JSON.stringify(v)
  const check=(s:Schema,v:any,depth=0,path="$"):boolean=>{
    field=path
    if(++nodes>100000||depth>40)return false
    if(s.$ref){
      if(typeof s.$ref!=="string"||!s.$ref.startsWith("#/$defs/"))return false
      const target=schema.$defs?.[s.$ref.slice(8)]
      if(!target||!check(target,v,depth+1,path))return false
    }
    if(s.anyOf&&!s.anyOf.some((part:Schema)=>check(part,v,depth+1,path)))return false
    if(s.oneOf&&s.oneOf.filter((part:Schema)=>check(part,v,depth+1,path)).length!==1)return false
    if(s.allOf&&!s.allOf.every((part:Schema)=>check(part,v,depth+1,path)))return false
    if(s.if){const branch=check(s.if,v,depth+1,path)?s.then:s.else;if(branch&&!check(branch,v,depth+1,path))return false}
    if(Object.hasOwn(s,"const")&&identity(v)!==identity(s.const))return false
    if(s.enum&&!s.enum.some((item:any)=>identity(item)===identity(v)))return false
    if(s.type!==undefined&&!({
      null:v===null,boolean:typeof v==="boolean",string:typeof v==="string",
      integer:Number.isSafeInteger(v),array:Array.isArray(v),
      object:v!==null&&typeof v==="object"&&!Array.isArray(v),
    } as Record<string,boolean>)[s.type])return false
    if(typeof v==="string"&&!(v.isWellFormed()&&
      (s.minLength===undefined||[...v].length>=s.minLength)&&(s.maxLength===undefined||[...v].length<=s.maxLength)&&
      (s.pattern===undefined||new RegExp(s.pattern,"u").test(v))))return false
    if(typeof v==="number"&&!((s.minimum===undefined||v>=s.minimum)&&(s.maximum===undefined||v<=s.maximum)))return false
    if(Array.isArray(v)&&!((s.minItems===undefined||v.length>=s.minItems)&&(s.maxItems===undefined||v.length<=s.maxItems)&&
      (s.items===undefined||v.every((item,index)=>check(s.items,item,depth+1,path+"["+index+"]")))&&
      (!s.uniqueItems||new Set(v.map(identity)).size===v.length)))return false
    if(v!==null&&typeof v==="object"&&!Array.isArray(v))return (
      (s.required??[]).every((key:string)=>{if(!Object.hasOwn(v,key)){field=path+"."+key;return false}return true})&&
      Object.keys(v).every(key=>Object.hasOwn(s.properties??{},key)?check(s.properties[key],v[key],depth+1,path+"."+key):s.additionalProperties!==false))
    return true
  }
  if(!check(schema,value))throw new ModelContractFault(field)
}
/** Export only the referenced definition closure, not all unrelated protocol vocabulary. */
export function operationSchema(kind:"commands"|"reads"|"authoring",operation:string):Schema {
  const original=knowledgeProtocol[kind][operation]
  if(!original)nativeUnavailable()
  const {$defs,...shape}=original,definitions:Schema={}
  const visit=(value:any)=>{
    if(!value||typeof value!=="object")return
    if(typeof value.$ref==="string"){
      const name=value.$ref.slice(8)
      if(!Object.hasOwn(definitions,name)){
        if(!$defs?.[name])nativeUnavailable()
        definitions[name]=$defs[name];visit($defs[name])
      }
    }
    for(const [key,child]of Object.entries(value))if(key!=="$defs")visit(child)
  }
  visit(shape)
  return {...shape,...(Object.keys(definitions).length?{$defs:definitions}:{})}
}
export const typedKnowledgeReadInput={
  type:"object",
  $defs:knowledgeProtocol.reads.read_revision!.$defs,
  anyOf:[
    {type:"object",additionalProperties:false,required:["reference"],properties:{
      reference:{type:"string",pattern:"^wr1_[A-Za-z0-9_-]{24}$"},format:{type:"string",enum:["readable","canonical"]}}},
    ...Object.keys(knowledgeProtocol.reads).map(operation=>{
      const schema=operationSchema("reads",operation),{$defs,...subject}=schema
      return {type:"object",additionalProperties:false,required:["operation","subject"],
        properties:{operation:{const:operation},subject,format:{type:"string",enum:["readable","canonical"]}}}
    }),
  ],
}
export function typedReadInput(input:any,projectId:string){
  if(input?.reference!==undefined||input?.json!==undefined)return input // exact retained legacy calls
  if(!input||typeof input!=="object"||Object.keys(input).some(k=>!["operation","subject","format"].includes(k))||
    (input.format!==undefined&&!["readable","canonical"].includes(input.format)))nativeUnavailable()
  validateModelContract(operationSchema("reads",input.operation),input.subject)
  return {json:JSON.stringify({formatVersion:1,projectId:input.operation==="list_projects"?null:projectId,
    operation:input.operation,subject:input.subject}),...(input.format?{format:input.format}:{})}
}
/** Compact diagnostics use explicit operation/definition/file selection; never return the source
 * bundle by default or direct an agent to a spill file outside its granted filesystem scope. */
export function modelContractHelp(schemas:Readonly<Record<string,string>>,input:any):string {
  if(!input||typeof input!=="object"||Object.keys(input).some(k=>!["name","operation","definition","file","offset"].includes(k))||
    typeof input.name!=="string"||!Object.hasOwn(schemas,input.name))nativeUnavailable()
  const kind=input.name==="knowledge-command1"?"commands":input.name==="knowledge-read1"?"reads":undefined
  if(input.file!==undefined){
    if(typeof input.file!=="string"||input.operation!==undefined||input.definition!==undefined)nativeUnavailable()
    const bundle=JSON.parse(schemas[input.name]!),file=bundle.files?.find((f:any)=>f.path===input.file)
    const offset=input.offset??0
    if(!file||!Number.isSafeInteger(offset)||offset<0||offset>file.source.length)nativeUnavailable()
    if(offset>0&&/[\uDC00-\uDFFF]/u.test(file.source[offset]??"")&&/[\uD800-\uDBFF]/u.test(file.source[offset-1]))nativeUnavailable()
    let end=Math.min(offset+4000,file.source.length)
    if(end<file.source.length&&/[\uD800-\uDBFF]/u.test(file.source[end-1]))end--
    return JSON.stringify({kind:"diagnostic-source-page",namespace:input.name,path:file.path,sha256:file.sha256,
      offset,nextOffset:end<file.source.length?end:null,source:file.source.slice(offset,end)})
  }
  if(input.offset!==undefined)nativeUnavailable()
  if(kind&&input.operation!==undefined){
    const selected=Object.hasOwn(knowledgeProtocol.authoring,input.operation)?"authoring":kind
    const schema=operationSchema(selected,input.operation)
    if(input.definition!==undefined){
      const definition=schema.$defs?.[input.definition];if(!definition)nativeUnavailable()
      return JSON.stringify({operation:input.operation,definition:input.definition,schema:definition})
    }
    const {$defs,...shape}=schema
    return JSON.stringify({formatVersion:knowledgeProtocol.formatVersion,operation:input.operation,
      description:knowledgeProtocol.help[input.operation]??"Exact current-authorized read. No latest fallback, cached rights or canonical mutation.",
      schema:shape,definitions:Object.keys($defs??{}),
      detail:"Select definition by name through this same tool. Sources are optional diagnostics, not required authoring input."})
  }
  if(input.definition!==undefined||input.operation!==undefined)nativeUnavailable()
  const bundle=JSON.parse(schemas[input.name]!)
  return JSON.stringify({namespace:input.name,operations:kind?Object.keys(knowledgeProtocol[kind]):[],
    authoring:kind==="commands"?["writhelm_propose_document","writhelm_request_review"]:[],
    diagnosticFiles:(bundle.files??[]).map((f:any)=>({path:f.path,sha256:f.sha256})),
    detail:input.name.startsWith("work-")
      ?"Use writhelm_work_contract with kind commands/reads, then operation and optional definition. It loads one exact typed operation into this chat, not the whole work protocol. Sources are optional diagnostics."
      :"Use typed tool inputs. For details select operation, then definition; optional sources use file and offset (4000-character pages). No external filesystem read required."})
}
