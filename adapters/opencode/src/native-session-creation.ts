// SPDX-License-Identifier: AGPL-3.0-only
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { closedObject, nativeUnavailable } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"
import path from "node:path"

type Session=Readonly<{id:string;location:Readonly<{directory:string}>;parentID?:string|null;fork?:unknown;revert?:unknown}>
type NativeCreation=Readonly<{observe:(sessionID:string)=>Promise<Session|undefined>;create:(sessionID:string)=>Promise<Session>}>
export const userSessionID=(value:unknown):value is string=>typeof value==="string"&&/^ses_[A-Za-z0-9_-]{1,128}$/.test(value)

/** One immutable original creation per future chat, never a shared project/UI pending pointer.
 * A retained request with an absent native session is UNKNOWN, not permission to create again.
 * This journal records native effects only; current controller authority is checked separately. */
export class NativeSessionCreation {
  private readonly pending=new Map<string,Promise<Session>>()
  private closed=false
  constructor(private readonly custody:LinuxNativeCustody,private readonly directory:string,private readonly native:NativeCreation){}
  async initialize(){
    await this.custody.initialize()
    for(const entry of await this.custody.scan("session-creation"))await this.record(entry.raw)
  }
  private async record(raw:string){
    const record=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","sessionID","directory"])
    if(record.formatVersion?.value!=="1"||!userSessionID(record.sessionID)||typeof record.directory!=="string"||
      !path.isAbsolute(record.directory)||await this.custody.read("session-creation",record.sessionID)!==raw)nativeUnavailable()
    return {sessionID:record.sessionID,directory:record.directory}
  }
  create(sessionID:string):Promise<Session>{
    if(this.closed)return Promise.reject(Error("Native session creation is closed"))
    if(!userSessionID(sessionID))return Promise.reject(Error("Invalid native user session identity"))
    const existing=this.pending.get(sessionID);if(existing)return existing
    if(this.pending.size>=32)return Promise.reject(Error("Native session creation capacity held"))
    const request=this.reconcile(sessionID)
    this.pending.set(sessionID,request)
    void request.then(()=>this.pending.delete(sessionID),()=>this.pending.delete(sessionID))
    return request
  }
  async close(){this.closed=true;await Promise.allSettled(this.pending.values())}
  /** Reconcile retained chats after normal startup. Never replay create or wake a model. Missing
   * outcomes remain recorded; another view cannot claim them merely by opening the same folder. */
  async recover():Promise<readonly Session[]>{
    if(this.closed)nativeUnavailable()
    const found:Session[]=[]
    for(const entry of await this.custody.scan("session-creation")){
      const record=await this.record(entry.raw)
      if(record.directory!==this.directory)continue
      const actual=await this.native.observe(record.sessionID)
      if(actual!==undefined)found.push(this.checked(record.sessionID,actual))
    }
    if(this.closed)nativeUnavailable()
    return Object.freeze(found)
  }
  private checked(sessionID:string,session:Session|undefined):Session {
    if(!session||session.id!==sessionID||session.location.directory!==this.directory||session.parentID||session.fork||session.revert)nativeUnavailable()
    return session
  }
  private async reconcile(sessionID:string):Promise<Session>{
    const original=JSON.stringify({formatVersion:1,sessionID,directory:this.directory})
    const retained=await this.custody.read("session-creation",sessionID)
    if(retained!==undefined){
      const record=await this.record(retained)
      if(record.sessionID!==sessionID||record.directory!==this.directory)nativeUnavailable()
      return this.checked(sessionID,await this.native.observe(sessionID))
    }
    // Do not adopt an arbitrary pre-existing conversation under a caller-selected ID.
    if(await this.native.observe(sessionID)!==undefined)nativeUnavailable()
    const won=await this.custody.retain("session-creation",sessionID,original)
    if(!won)return this.checked(sessionID,await this.native.observe(sessionID))
    // Durable intent precedes the ONLY create. Lost replies keep it for read-only reconciliation.
    return this.checked(sessionID,await this.native.create(sessionID))
  }
}
