// SPDX-License-Identifier: AGPL-3.0-only
import contract from "./generated/model-work.js"
import {validateModelContract,ModelContractFault} from "./model-contracts.js"
import {contextJSON} from "./native-context-codec.js"
type Schema=Record<string,any>
const protocol=contract as unknown as {commands:Record<string,Schema>;reads:Record<string,Schema>;$defs:Record<string,Schema>}
export const modelWorkOperations={commands:Object.keys(protocol.commands),reads:Object.keys(protocol.reads)}
const long=(s:Schema)=>s.type==="integer"&&(s.maximum===undefined||!Number.isSafeInteger(s.maximum))
const minimum=(s:Schema)=>s.minimum===undefined?-9223372036854775808n:Number.isSafeInteger(s.minimum)?BigInt(s.minimum):-9223372036854775808n
const maximum=(s:Schema)=>s.maximum===undefined||!Number.isSafeInteger(s.maximum)?9223372036854775807n:BigInt(s.maximum)
function external(schema:any):any {
  if(Array.isArray(schema))return schema.map(external)
  if(!schema||typeof schema!=="object")return schema
  if(long(schema))return {type:"string",pattern:minimum(schema)<0n?"^(0|-?[1-9][0-9]*)$":"^(0|[1-9][0-9]*)$",
    maxLength:20,description:"Exact Int64 decimal string, not a JavaScript number. Wire encoding remains the original integer."}
  return Object.fromEntries(Object.entries(schema).map(([k,v])=>[k,external(v)]))
}
export function workOperationSchema(kind:"commands"|"reads",operation:string,model=true):Schema {
  const selected=protocol[kind][operation];if(!selected)throw new ModelContractFault("$.operation")
  const defs:Schema={}
  const visit=(s:any)=>{
    if(!s||typeof s!=="object")return
    if(s.$ref){const name=s.$ref.slice(8);if(!Object.hasOwn(defs,name)){defs[name]=protocol.$defs[name];if(!defs[name])throw Error("Invalid packaged work contract");visit(defs[name])}}
    for(const child of Object.values(s))visit(child)
  }
  const root=selected.$ref?protocol.$defs[selected.$ref.slice(8)]!:selected
  visit(root)
  return model?external({...root,$defs:defs}):{...root,$defs:defs}
}
/** Int64 strings are normalized only where the authoritative schema declares an integer.
 * No global string/number coercion, hashing by the model, lossy JSON parse or change to wire bytes. */
export function prepareTypedWork(kind:"commands"|"reads",input:any,projectId:string):string {
  const field=kind==="commands"?"payload":"subject",keys=kind==="commands"?["operation","requestKey","payload"]:["operation","subject","format"]
  if(!input||typeof input!=="object"||Object.keys(input).some(k=>!keys.includes(k)))throw new ModelContractFault("$")
  if(kind==="commands"&&(typeof input.requestKey!=="string"||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(input.requestKey)))throw new ModelContractFault("$.requestKey")
  if(input.format!==undefined&&!["readable","canonical"].includes(input.format))throw new ModelContractFault("$.format")
  const schema=workOperationSchema(kind,input.operation,false)
  validateModelContract(external(schema),input[field])
  let nodes=0
  const convert=(s:Schema,v:any):any=>{
    if(++nodes>100000)throw new ModelContractFault("$")
    if(s.$ref)return convert(schema.$defs[s.$ref.slice(8)],v)
    if(long(s)){const n=BigInt(v);if(n<minimum(s)||n>maximum(s))throw new ModelContractFault("$.Int64");return n}
    const variants=s.oneOf??s.anyOf
    if(variants){
      const branch=variants.find((part:Schema)=>{try{validateModelContract({...external(part),$defs:external(schema.$defs)},v);return true}catch{return false}})
      if(!branch)throw new ModelContractFault("$")
      return convert(branch,v)
    }
    if(Array.isArray(v))return v.map(x=>convert(s.items,x))
    if(v&&typeof v==="object")return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,s.properties?.[k]?convert(s.properties[k],x):x]))
    return v
  }
  return contextJSON({formatVersion:kind==="commands"?3:2,projectId,operation:input.operation,
    ...(kind==="commands"?{requestKey:input.requestKey}:{}),[field]:convert(schema,input[field])},kind==="commands"?2*1024*1024:16384)
}
/** Per-chat disposable documentation selection, not shared planner state, authority or a source cache.
 * Loading one operation specializes only that chat's next model-facing schema. Runtime always checks
 * the actual requested operation against this same packaged contract and current service rights. */
export class WorkModelContracts {
  private selected=new Map<string,Partial<Record<"commands"|"reads",string>>>()
  input(kind:"commands"|"reads",sessionID?:string):Schema {
    const operation=sessionID?this.selected.get(sessionID)?.[kind]:undefined
    const field=kind==="commands"?"payload":"subject"
    const shape=operation?workOperationSchema(kind,operation):{type:"object"}
    const {$defs,...body}=shape
    return {type:"object",additionalProperties:false,required:kind==="commands"?["operation","requestKey",field]:["operation",field],
      properties:{operation:operation?{const:operation}:{type:"string",enum:modelWorkOperations[kind]},
        ...(kind==="commands"?{requestKey:{type:"string",pattern:"^[a-f0-9-]{36}$"}}:{format:{type:"string",enum:["readable","canonical"]}}),
        [field]:body},...($defs?{$defs}:{})}
  }
  describe(sessionID:string,input:any):string {
    if(!input||typeof input!=="object"||Object.keys(input).some(k=>!["kind","operation","definition"].includes(k))||
      !["commands","reads"].includes(input.kind))throw new ModelContractFault("$")
    const kind=input.kind as "commands"|"reads"
    if(input.operation===undefined)return JSON.stringify({kind,operations:modelWorkOperations[kind],
      next:"Select operation. Its typed input will be loaded into this chat's next work tool. Details use definition, never external files."})
    const schema=workOperationSchema(kind,input.operation),{$defs,...shape}=schema
    if(input.definition!==undefined){
      if(!Object.hasOwn($defs??{},input.definition))throw new ModelContractFault("$.definition")
      return JSON.stringify({operation:input.operation,definition:input.definition,schema:$defs[input.definition]})
    }
    if(this.selected.size>=128&&!this.selected.has(sessionID))this.selected.delete(this.selected.keys().next().value!)
    this.selected.set(sessionID,{...this.selected.get(sessionID),[kind]:input.operation})
    return JSON.stringify({operation:input.operation,schema:shape,definitions:Object.keys($defs??{}),
      inputLoaded:true,integerRule:"Int64 fields are canonical decimal strings in typed tools; adapter preserves exact original integer wire encoding.",
      limits:"Shape is not authority, approval, readiness or execution permission. Current service domain checks remain mandatory."})
  }
}
