// SPDX-License-Identifier: AGPL-3.0-only
import { NativeCredentialRegistry } from "./native-credentials.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { NativeCandidateReviewLifecycle } from "./native-candidate-review.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"
import { nativeUnavailable } from "./native-runtime-client.js"
import { sendContext } from "./native-context-protocol.js"
import type { CandidateDeliveryProfile, CandidateAssemblyResult } from "./native-result-retention.js"

/** No inferred human/planner approval or native-ready flag. Service derives canonical commands;
 * ordinary command admission, qualified passive preparation, enrollment/bind and held start remain. */
export class NativeAutomaticCandidateReview {
  constructor(private readonly registry:Pick<NativeCredentialRegistry,"candidateReviewPreparation"|"contextChannel">,
    private readonly preparation:Readonly<{runController:(selectionJSON:string,id:string,signal:AbortSignal)=>Promise<any>}>,
    private readonly lifecycle:NativeCandidateReviewLifecycle,private readonly custody:LinuxNativeCustody,
    private readonly sessionID:string,private readonly projectId:string){}
  async prepare(profile:CandidateDeliveryProfile,candidate:CandidateAssemblyResult,selectedJSON:string,signal:AbortSignal){
    if(signal.aborted)nativeUnavailable()
    const proposal=await this.registry.candidateReviewPreparation(this.sessionID,profile.planRefJSON,profile.operationId,profile.testPlanId,signal)
    const basis=exactJSON(proposal.ticketBasisJSON,nativeUnavailable)
    if(basis.projectId!==this.projectId||basis.assemblyId!==profile.operationId||basis.checkPlanId!==profile.testPlanId||
      contextJSON(basis.selected)!==contextJSON(exactJSON(selectedJSON,nativeUnavailable))||
      contextJSON(basis.candidate?.commit)!==contextJSON(candidate.commit)||contextJSON(basis.candidate?.tree)!==contextJSON(candidate.tree))nativeUnavailable()
    const key=contextJSON([profile.operationId,profile.testPlanId])
    const original=JSON.stringify({formatVersion:1,sessionID:this.sessionID,projectId:this.projectId,...proposal})
    const prior=await this.custody.read("candidate-review-automatic",key)
    if(prior!==undefined&&prior!==original)nativeUnavailable()
    if(prior===undefined&&!await this.custody.retain("candidate-review-automatic",key,original))nativeUnavailable()
    const channel=await this.registry.contextChannel(this.sessionID,signal)
    if(channel.scope!=="controller"||channel.projectID!==this.projectId)nativeUnavailable()
    for(const raw of [proposal.taskCommandJSON,proposal.manifestCommandJSON]){
      const command=exactJSON(raw,nativeUnavailable)
      if(command.projectId!==this.projectId||!["create_task_revision","pin_input_manifest"].includes(command.operation))nativeUnavailable()
      const result=await sendContext(channel,"commands",raw,signal)
      if(result.kind!=="settled"||result.outcome?.kind!=="success")return {phase:"review-task-held",integrationAllowed:false as const}
      if(result.identity?.projectId!==command.projectId||result.identity?.operation!==command.operation||result.identity?.requestKey!==command.requestKey)nativeUnavailable()
    }
    const prepared=await this.preparation.runController(proposal.selectionJSON,proposal.preparationId,signal)
    if(prepared.phase!=="binding-confirmed")return {phase:"review-preparation-held",integrationAllowed:false as const}
    if(!prepared.execution||!prepared.binding||typeof prepared.assignmentId!=="string")nativeUnavailable()
    const ticketJSON=contextJSON({...basis,assignment:{assignmentId:prepared.assignmentId,generation:0},binding:prepared.binding,execution:prepared.execution})
    await this.lifecycle.start(ticketJSON,signal)
    return {phase:"review-start-requested",integrationAllowed:false as const}
  }
}
