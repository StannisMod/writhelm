// SPDX-License-Identifier: AGPL-3.0-only
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { LinuxGitWitness, type GitSnapshot } from "./linux-git-witness.js"
import { nativeUnavailable, uuidPattern } from "./native-runtime-client.js"

export type RetentionRequest = Readonly<{assignmentId:string;generation:string;executionId:string;snapshot:GitSnapshot}>

/** Uninstalled operation coordinator. The authority callback must establish CURRENT service
 * authority for the exact assignment, generation, execution, repository and result, not merely
 * authenticate the caller. Custody is operational evidence, never canonical work authority.
 * A missing receipt after dispatch is unknown even if the ref exists: never dispatch again. */
export class NativeResultRetention {
  constructor(private readonly source:string,private readonly custody:LinuxNativeCustody,
    private readonly git:Pick<LinuxGitWitness,"qualifySource"|"pinRetainedRefOnce"|"verifyRetainedRef">,
    private readonly authorize:(request:RetentionRequest,signal:AbortSignal)=>Promise<boolean>,
    private readonly observeWorktree:(request:RetentionRequest,signal:AbortSignal)=>Promise<boolean>) {}

  private capture(input:RetentionRequest,signal:AbortSignal){
    const s=input.snapshot
    const snapshot=Object.freeze({repositoryId:s.repositoryId,commit:Object.freeze({...s.commit}),tree:Object.freeze({...s.tree}),
      dirtyContentDigest:s.dirtyContentDigest,retainedLocatorId:s.retainedLocatorId})
    const request=Object.freeze({assignmentId:input.assignmentId,generation:input.generation,executionId:input.executionId,snapshot})
    if(!uuidPattern.test(request.assignmentId)||request.generation!=="0"||!uuidPattern.test(request.executionId)||
      !uuidPattern.test(snapshot.repositoryId)||!uuidPattern.test(snapshot.retainedLocatorId)||signal.aborted)nativeUnavailable()
    const raw=JSON.stringify({formatVersion:1,...request}),key=snapshot.retainedLocatorId
    return {request,snapshot,raw,key}
  }

  /** Read-only evidence for a service already holding its authority snapshot. Does not call the
   * service or authorize a new effect. An absent/partial original is never a retention proof. */
  async proof(input:RetentionRequest,signal:AbortSignal):Promise<string|null>{
    const {snapshot,raw,key}=this.capture(input,signal)
    if(await this.custody.read("result-retention",key)!==raw||
      await this.custody.read("result-retention-receipt",key)!==raw)return null
    await this.git.verifyRetainedRef(this.source,snapshot,signal)
    if(await this.custody.read("result-retention",key)!==raw||
      await this.custody.read("result-retention-receipt",key)!==raw)return null
    return raw
  }

  async retain(input:RetentionRequest,signal:AbortSignal):Promise<void>{
    const {request,snapshot,raw,key}=this.capture(input,signal)
    const authority=async()=>{if(signal.aborted||!await this.authorize(request,signal))nativeUnavailable()}
    await authority()
    const original=await this.custody.read("result-retention",key)
    if(original!==undefined){
      if(original!==raw)nativeUnavailable()
      const receipt=await this.custody.read("result-retention-receipt",key)
      if(receipt!==raw)nativeUnavailable() // unknown is not absence and never authorizes another effect
      await this.git.verifyRetainedRef(this.source,snapshot,signal)
      await authority()
      return
    }
    await this.git.qualifySource(this.source,snapshot,signal)
    if(!await this.observeWorktree(request,signal))nativeUnavailable()
    await authority()
    if(!await this.custody.retain("result-retention",key,raw))nativeUnavailable()
    await authority()
    if(!await this.observeWorktree(request,signal))nativeUnavailable()
    await authority()
    await this.git.pinRetainedRefOnce(this.source,snapshot,signal)
    await authority()
    await this.custody.retain("result-retention-receipt",key,raw)
  }
}
