// SPDX-License-Identifier: AGPL-3.0-only
import {createHash} from "node:crypto"
import {contextJSON} from "./native-context-codec.js"
import {exactJSON} from "./strict-json.js"
import {closedObject,nativeUnavailable,uuidPattern} from "./native-runtime-client.js"
import type {LinuxNativeCustody} from "./native-runtime-custody.js"

type Storage=Pick<LinuxNativeCustody,"read"|"retain"|"replace">
type Schemas=Readonly<Record<string,string>>
const hash=(raw:string)=>createHash("sha256").update(raw).digest("hex")
const digest=(value:unknown):value is string=>typeof value==="string"&&/^[a-f0-9]{64}$/.test(value)
const encode=(value:unknown)=>contextJSON(value,2*1024*1024)
function schemas(value:unknown,verifySources=false):Schemas{
  if(!value||typeof value!=="object"||Array.isArray(value))nativeUnavailable()
  const entries=Object.entries(value!)
  if(!entries.length||entries.length>16)nativeUnavailable()
  let bytes=0
  for(const [name,raw]of entries){
    if(!/^[a-z][a-z0-9-]{0,63}$/.test(name)||typeof raw!=="string"||!raw.isWellFormed()||
      Buffer.byteLength(raw)>512*1024||(bytes+=Buffer.byteLength(raw))>1536*1024)nativeUnavailable()
    if(verifySources){
      const contract=closedObject(exactJSON(raw as string,nativeUnavailable),["formatVersion","kind","namespace","notice","files"])
      if(contract.formatVersion?.value!=="1"||contract.kind!=="canonical-codec-source-v1"||contract.namespace!==name||
        typeof contract.notice!=="string"||!Array.isArray(contract.files)||!contract.files.length||contract.files.length>32)nativeUnavailable()
      const paths=new Set<string>()
      for(const file of contract.files){
        const f=closedObject(file,["path","sha256","source"])
        if(typeof f.path!=="string"||!/^[A-Za-z0-9][A-Za-z0-9_./-]{0,255}$/.test(f.path)||
          f.path.split("/").some((part:string)=>part===".."||part==="."||part==="")||paths.has(f.path)||
          typeof f.source!=="string"||Buffer.byteLength(f.source)>262144||!digest(f.sha256)||hash(f.source)!==f.sha256)nativeUnavailable()
        paths.add(f.path)
      }
    }
  }
  return value as Schemas
}
export const contractBundleDigest=(value:Schemas)=>hash("writhelm.client-contract-bundle.v1\0"+encode(schemas(value)))
const selection=(bootstrapDigest:string,bundleDigest:string,releaseDigest:string)=>encode({
  formatVersion:1,bootstrapDigest,bundleDigest,releaseDigest})
async function baseline(storage:Storage,bootstrapRaw:string){
  const cfg=exactJSON(bootstrapRaw,nativeUnavailable)
  if(typeof cfg.storeId!=="string"||!uuidPattern.test(cfg.storeId))nativeUnavailable()
  let current=schemas(cfg.schemas)
  for(const name of ["knowledge-read1","work-read2"]){
    const supplemental=await storage.read("read-contract",name)
    if(supplemental===undefined)continue
    const contract=exactJSON(supplemental,nativeUnavailable)
    if(contract.kind!=="canonical-codec-source-v1"||contract.namespace!==name||
      Object.hasOwn(current,name)&&current[name]!==supplemental)nativeUnavailable()
    current={...current,[name]:supplemental}
  }
  return {bootstrapDigest:hash(bootstrapRaw),storeId:cfg.storeId as string,schemas:current,bundleDigest:contractBundleDigest(current)}
}
async function release(storage:Storage,base:Awaited<ReturnType<typeof baseline>>,id:string){
  let next:string|null=id,previousBundle:string|undefined,chosen:any,bytes=0
  const seen=new Set<string>()
  while(next!==null){
    if(!digest(next)||seen.has(next)||seen.size>=32)nativeUnavailable()
    seen.add(next)
    const raw=await storage.read("contract-release",next)
    if(raw===undefined||hash(raw!)!==next||(bytes+=Buffer.byteLength(raw!))>32*1024*1024)nativeUnavailable()
    const record=closedObject(exactJSON(raw!,nativeUnavailable),["formatVersion","bootstrapDigest","storeId",
      "previousBundleDigest","previousReleaseDigest","bundleDigest","schemas"])
    const current=schemas(record.schemas,true)
    if(record.formatVersion?.value!=="1"||record.bootstrapDigest!==base.bootstrapDigest||record.storeId!==base.storeId||
      !digest(record.bundleDigest)||record.bundleDigest!==contractBundleDigest(current)||!digest(record.previousBundleDigest)||
      record.previousReleaseDigest!==null&&!digest(record.previousReleaseDigest)||
      Object.keys(current).sort().join(",")!==Object.keys(base.schemas).sort().join(",")||
      previousBundle!==undefined&&previousBundle!==record.bundleDigest)nativeUnavailable()
    chosen??=record
    previousBundle=record.previousBundleDigest
    next=record.previousReleaseDigest
  }
  if(previousBundle!==base.bundleDigest)nativeUnavailable()
  return chosen as {bundleDigest:string;schemas:Schemas}
}
/** Explicit selection is operational installation state, not canonical authority or shared planner
 * memory. Unselected prepared releases never become current by scanning or choosing the latest. */
export async function selectedContractSchemas(storage:Storage,bootstrapRaw:string):Promise<Schemas>{
  const base=await baseline(storage,bootstrapRaw),raw=await storage.read("contract-selection","current")
  if(raw===undefined)return base.schemas
  const s=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","bootstrapDigest","bundleDigest","releaseDigest"])
  if(s.formatVersion?.value!=="1"||s.bootstrapDigest!==base.bootstrapDigest||!digest(s.bundleDigest)||!digest(s.releaseDigest)||
    selection(s.bootstrapDigest,s.bundleDigest,s.releaseDigest)!==raw)nativeUnavailable()
  const current=await release(storage,base,s.releaseDigest)
  if(current.bundleDigest!==s.bundleDigest)nativeUnavailable()
  return current.schemas
}
export type ContractReleaseInput=Readonly<{expectedBootstrapDigest:string;previousBundleDigest:string;
  previousReleaseDigest:string|null;schemas:Schemas}>
/** Trusted operator only. Original predecessor is supplied explicitly, never inferred from a
 * package mismatch. Release durability precedes compare-and-swap selection; retries keep identity. */
export async function selectContractRelease(storage:Storage,bootstrapRaw:string,input:ContractReleaseInput){
  const base=await baseline(storage,bootstrapRaw)
  if(!digest(input.expectedBootstrapDigest)||input.expectedBootstrapDigest!==base.bootstrapDigest||
    !digest(input.previousBundleDigest)||input.previousReleaseDigest!==null&&!digest(input.previousReleaseDigest))nativeUnavailable()
  const target=schemas(input.schemas,true),bundleDigest=contractBundleDigest(target)
  if(Object.keys(target).sort().join(",")!==Object.keys(base.schemas).sort().join(",")||bundleDigest===input.previousBundleDigest)nativeUnavailable()
  const predecessor=input.previousReleaseDigest===null?base:await release(storage,base,input.previousReleaseDigest)
  if(predecessor.bundleDigest!==input.previousBundleDigest)nativeUnavailable()
  const original=encode({formatVersion:1,bootstrapDigest:base.bootstrapDigest,storeId:base.storeId,
    previousBundleDigest:input.previousBundleDigest,previousReleaseDigest:input.previousReleaseDigest,bundleDigest,schemas:target})
  const releaseDigest=hash(original),next=selection(base.bootstrapDigest,bundleDigest,releaseDigest)
  const expected=input.previousReleaseDigest===null?undefined:selection(base.bootstrapDigest,input.previousBundleDigest,input.previousReleaseDigest)
  const old=await storage.read("contract-selection","current")
  if(old!==expected&&old!==next)nativeUnavailable()
  await storage.retain("contract-release",releaseDigest,original)
  if(old!==next){
    if(old===undefined)await storage.retain("contract-selection","current",next)
    else if(!await storage.replace("contract-selection","current",hash(old),next))nativeUnavailable()
  }
  if(await storage.read("contract-selection","current")!==next||
    encode(await selectedContractSchemas(storage,bootstrapRaw))!==encode(target))nativeUnavailable()
  return {kind:"contract-release-selected" as const,bootstrapDigest:base.bootstrapDigest,bundleDigest,releaseDigest}
}
