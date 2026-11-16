// SPDX-License-Identifier: AGPL-3.0-only
import { createHash } from "node:crypto"
import { closedObject, nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { exactJSON } from "./strict-json.js"
import { contextJSON } from "./native-context-codec.js"
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import type { NativeCredentialRegistry } from "./native-credentials.js"
import type { CandidateDeliveryProfile, CandidateAssemblyResult } from "./native-result-retention.js"

/** Uses service-issued exact binding and the existing held initial-start lifecycle, not a second
 * direct model client. Ordinary service tools publish the reviewer's canonical verification report. */
export class NativeCandidateReviewLifecycle {
  constructor(private readonly registry:Pick<NativeCredentialRegistry,"issueCandidateReviewTicket"|"candidateReviewReport"|"candidateReviewStatus">,
    private readonly initial:Readonly<{runController:(selectionJSON:string,startId:string,signal:AbortSignal)=>Promise<unknown>}>,private readonly custody:LinuxNativeCustody,
    private readonly sessionID:string,private readonly projectId:string,
    private readonly prepareMissing?:(profile:CandidateDeliveryProfile,candidate:CandidateAssemblyResult,selection:string,signal:AbortSignal)=>Promise<Readonly<{phase:string;integrationAllowed:false}>>){}
  async advance(profile:CandidateDeliveryProfile,candidate:CandidateAssemblyResult,selectedJSON:string,signal:AbortSignal){
    if(signal.aborted)nativeUnavailable()
    const matched=[] as Array<{reviewId:string;ticketDigest:string}>
    for(const entry of await this.custody.scan("candidate-review-start")){
      const record=closedObject(exactJSON(entry.raw,nativeUnavailable),["formatVersion","sessionID","projectId","ticketJSON","ticketDigest","selectionJSON","startId"])
      if(record.formatVersion?.value!=="1"||record.sessionID!==this.sessionID||record.projectId!==this.projectId||typeof record.ticketJSON!=="string"||
        typeof record.startId!=="string"||!uuidPattern.test(record.startId)||typeof record.ticketDigest!=="string"||
        createHash("sha256").update(record.ticketJSON).digest("hex")!==record.ticketDigest||
        await this.custody.read("candidate-review-start",record.startId)!==entry.raw)nativeUnavailable()
      const ticket=exactJSON(record.ticketJSON,nativeUnavailable)
      if(ticket.projectId!==this.projectId||ticket.reviewId!==record.startId)nativeUnavailable()
      if(ticket.assemblyId!==profile.operationId||ticket.checkPlanId!==profile.testPlanId||
        contextJSON(ticket.plan)!==contextJSON(exactJSON(profile.planRefJSON,nativeUnavailable)))continue
      if(ticket.candidate?.repositoryId!==profile.head.repositoryId||ticket.candidate?.retainedLocatorId!==profile.candidateId||
        ticket.candidate?.dirtyContentDigest!==null||contextJSON(ticket.candidate?.commit)!==contextJSON(candidate.commit)||contextJSON(ticket.candidate?.tree)!==contextJSON(candidate.tree)||
        contextJSON(ticket.selected)!==contextJSON(exactJSON(selectedJSON,nativeUnavailable)))nativeUnavailable()
      matched.push({reviewId:record.startId,ticketDigest:record.ticketDigest})
    }
    if(!matched.length)return this.prepareMissing?this.prepareMissing(profile,candidate,selectedJSON,signal):
      {phase:"waiting-for-review-assignment" as const,integrationAllowed:false as const}
    if(matched.length!==1)nativeUnavailable()
    const review=matched[0]!
    const observed=await this.registry.candidateReviewStatus(this.sessionID,review.reviewId,review.ticketDigest,signal)
    if(observed.kind==="pending")return {phase:"review-pending" as const,reviewId:review.reviewId,integrationAllowed:false as const}
    const row=exactJSON(observed.reportJSON,nativeUnavailable),report=row.payload
    if(!report||!Array.isArray(report.findings)||!Array.isArray(report.limits))nativeUnavailable()
    const phase=report.findings.some((finding:any)=>finding.blocking===true)?"review-blocked" as const:
      report.limits.length?"review-limited" as const:"review-recorded" as const
    return {phase,reviewId:review.reviewId,reportRefJSON:observed.reportRefJSON,reportJSON:observed.reportJSON,integrationAllowed:false as const}
  }
  async start(ticketJSON:string,signal:AbortSignal){
    if(signal.aborted)nativeUnavailable()
    const issued=await this.registry.issueCandidateReviewTicket(this.sessionID,ticketJSON,signal)
    const ticket=exactJSON(issued.ticketJSON,nativeUnavailable)
    if(ticket.projectId!==this.projectId||typeof ticket.reviewId!=="string"||!uuidPattern.test(ticket.reviewId)||
      typeof ticket.contextId!=="string"||!uuidPattern.test(ticket.contextId)||ticket.assignment?.generation?.value!=="0"||
      typeof ticket.assignment?.assignmentId!=="string"||!uuidPattern.test(ticket.assignment.assignmentId)||
      ticket.binding?.family!=="assignment-bindings"||ticket.binding.key?.projectId!==this.projectId||
      ticket.binding.key?.assignmentId!==ticket.assignment.assignmentId||ticket.binding.key?.generation?.value!=="0"||
      createHash("sha256").update(issued.ticketJSON).digest("hex")!==issued.ticketDigest)nativeUnavailable()
    const selectionJSON=contextJSON({assignment:{family:"assignments",key:{projectId:this.projectId,id:ticket.assignment.assignmentId}},binding:ticket.binding})
    const original=JSON.stringify({formatVersion:1,sessionID:this.sessionID,projectId:this.projectId,
      ticketJSON:issued.ticketJSON,ticketDigest:issued.ticketDigest,selectionJSON,startId:ticket.reviewId})
    const previous=await this.custody.read("candidate-review-start",ticket.reviewId)
    if(previous!==undefined&&previous!==original)nativeUnavailable()
    if(previous===undefined&&!await this.custody.retain("candidate-review-start",ticket.reviewId,original))nativeUnavailable()
    if(signal.aborted)nativeUnavailable()
    // Repeated/unknown starts use precisely the same retained initial driver. That driver rechecks
    // canonical authority and never creates a fresh native prompt after an uncertain original.
    const status=await this.initial.runController(selectionJSON,ticket.reviewId,signal)
    return {phase:"candidate-review-start",reviewId:ticket.reviewId,ticketDigest:issued.ticketDigest,status,integrationAllowed:false}
  }
  async report(reviewId:string,reportRefJSON:string,signal:AbortSignal){
    if(!uuidPattern.test(reviewId)||signal.aborted)nativeUnavailable()
    const raw=await this.custody.read("candidate-review-start",reviewId)
    if(raw===undefined)nativeUnavailable()
    const original=closedObject(exactJSON(raw,nativeUnavailable),["formatVersion","sessionID","projectId","ticketJSON","ticketDigest","selectionJSON","startId"])
    if(original.formatVersion?.value!=="1"||original.sessionID!==this.sessionID||original.projectId!==this.projectId||original.startId!==reviewId||
      typeof original.ticketJSON!=="string"||typeof original.ticketDigest!=="string"||
      createHash("sha256").update(original.ticketJSON).digest("hex")!==original.ticketDigest)nativeUnavailable()
    // Exact canonical report from the scoped service, not a callback's semantic review assertion.
    return this.registry.candidateReviewReport(this.sessionID,reviewId,reportRefJSON,original.ticketDigest,signal)
  }
}
