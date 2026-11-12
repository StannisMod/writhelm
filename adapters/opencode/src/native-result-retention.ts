// SPDX-License-Identifier: AGPL-3.0-only
import { LinuxNativeCustody } from "./native-runtime-custody.js"
import { LinuxGitWitness, type GitSnapshot, type CandidateMergeInput, type GitObject } from "./linux-git-witness.js"
import { nativeUnavailable, uuidPattern } from "./native-runtime-client.js"
import { randomUUID, createHash } from "node:crypto"
import type { NativeCredentialRegistry } from "./native-credentials.js"
import { contextJSON } from "./native-context-codec.js"
import { exactJSON } from "./strict-json.js"

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
  /** Immutable assembly observation for a service holding its own authority snapshot. No callback
   * into that service and no publication effect: absent/partial custody is never an outcome. */
  async candidateProof(operationId:string,signal:AbortSignal):Promise<string|null>{
    if(!uuidPattern.test(operationId))nativeUnavailable()
    const original=await this.custody.read("candidate-operation",operationId)
    const receipt=await this.custody.read("candidate-receipt",operationId)
    if(original===undefined||receipt===undefined)return null
    const retained=JSON.parse(receipt),request=JSON.parse(original)
    if(retained.original!==original||request.operationId!==operationId||
      await this.custody.read("candidate-publish",operationId)!==receipt||
      await this.custody.read("candidate-objects",operationId)!==JSON.stringify(retained.result))return null
    await this.git.qualifySource(this.source,{...request.head,...retained.result},signal)
    return receipt
  }

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

export type CandidateAssemblyRequest=Readonly<{operationId:string;candidateId:string;head:GitSnapshot;
  inputs:readonly CandidateMergeInput[];time:string;selectedJSON:string}>
export type CandidateAssemblyResult=Readonly<{commit:GitObject;tree:GitObject}>

/** Operational custody is not service authority. The authorization callback must check the exact
 * selected results and candidate subject at the current service head on every execution/recovery.
 * Publication intent without its receipt remains unknown: never dispatch a second ref update. */
export class NativeCandidateAssembly {
  constructor(private readonly source:string,private readonly custody:LinuxNativeCustody,
    private readonly git:Pick<LinuxGitWitness,"buildCandidate"|"publishCandidateOnce"|"qualifySource">,
    private readonly authorize:(request:CandidateAssemblyRequest,signal:AbortSignal)=>Promise<boolean>){}

  async assemble(input:CandidateAssemblyRequest,signal:AbortSignal):Promise<CandidateAssemblyResult>{
    const raw=JSON.stringify(input),request=JSON.parse(raw) as CandidateAssemblyRequest
    if(!uuidPattern.test(request.operationId)||!uuidPattern.test(request.candidateId)||!request.inputs.length||request.inputs.length>256)nativeUnavailable()
    const key=request.operationId
    const authority=async()=>{if(signal.aborted||!await this.authorize(request,signal))nativeUnavailable()}
    await authority()
    const original=await this.custody.read("candidate-operation",key)
    if(original!==undefined&&original!==raw)nativeUnavailable()
    if(original===undefined&&!await this.custody.retain("candidate-operation",key,raw))nativeUnavailable()
    const receipt=await this.custody.read("candidate-receipt",key)
    if(receipt!==undefined){
      const retained=JSON.parse(receipt)
      if(retained.original!==raw||typeof retained.result!=="object"||retained.result===null)nativeUnavailable()
      await this.git.qualifySource(this.source,{...request.head,...retained.result},signal)
      await authority();return retained.result
    }
    if(await this.custody.read("candidate-publish",key)!==undefined)nativeUnavailable()
    let built=await this.custody.read("candidate-objects",key)
    if(built===undefined){
      const result=await this.git.buildCandidate(this.source,request.head,request.inputs,request.operationId,request.time,signal)
      built=JSON.stringify(result)
      if(!await this.custody.retain("candidate-objects",key,built))nativeUnavailable()
    }
    const result=JSON.parse(built) as CandidateAssemblyResult
    await this.git.qualifySource(this.source,{...request.head,...result},signal)
    await authority()
    if(!await this.custody.retain("candidate-publish",key,JSON.stringify({original:raw,result})))nativeUnavailable()
    await authority()
    await this.git.publishCandidateOnce(this.source,request.candidateId,request.head.commit,result.commit,signal)
    await authority()
    await this.custody.retain("candidate-receipt",key,JSON.stringify({original:raw,result}))
    return result
  }
}

export type CandidateTestRequest=Readonly<{runId:string;candidate:CandidateAssemblyResult;checkId:string;
  command:readonly string[];safeRepeat:boolean;previousRunId:string|null}>
export type CandidateTestOutcome=Readonly<{outcome:"passed"|"failed"|"cancelled"|"unavailable"|"unknown";output:string}>
const candidateSubject=(candidate:CandidateAssemblyResult)=>JSON.stringify([candidate.commit.algorithm,candidate.commit.hex,candidate.tree.algorithm,candidate.tree.hex])
const testSubject=(request:CandidateTestRequest)=>createHash("sha256").update(JSON.stringify([candidateSubject(request.candidate),request.checkId,request.command])).digest("hex")
/** Portable job custody. The adapter must observe the exact immutable candidate and execute only
 * the planned check in its authorized isolated area. No model/owner credentials are granted here. */
export class NativeCandidateTests {
  constructor(private readonly custody:LinuxNativeCustody,
    private readonly authorize:(request:CandidateTestRequest,signal:AbortSignal)=>Promise<boolean>,
    private readonly execute:(request:CandidateTestRequest,signal:AbortSignal)=>Promise<CandidateTestOutcome>){}
  async selectedRun(first:CandidateTestRequest):Promise<CandidateTestRequest>{
    let current=first
    const seen=new Set<string>()
    for(;;){
      if(seen.has(current.runId)||seen.size>=256)nativeUnavailable()
      seen.add(current.runId)
      const next=await this.custody.read("candidate-test-successor",current.runId)
      if(next===undefined)return current
      const successor=JSON.parse(next) as CandidateTestRequest
      const previous=await this.custody.read("candidate-test-result",current.runId)
      if(previous===undefined||!current.safeRepeat||!successor.safeRepeat||successor.previousRunId!==current.runId||
        !uuidPattern.test(successor.runId)||testSubject(successor)!==testSubject(first))nativeUnavailable()
      const retained=JSON.parse(previous)
      if(retained.original!==JSON.stringify(current)||retained.result.outcome==="unknown")nativeUnavailable()
      current=successor
    }
  }
  async run(input:CandidateTestRequest,signal:AbortSignal):Promise<CandidateTestOutcome>{
    const raw=JSON.stringify(input),request=JSON.parse(raw) as CandidateTestRequest,key=request.runId
    if(!uuidPattern.test(key)||!request.checkId||!request.command.length||request.command.length>64)nativeUnavailable()
    if(!await this.authorize(request,signal)||signal.aborted)nativeUnavailable()
    const original=await this.custody.read("candidate-test-run",key)
    if(original!==undefined&&original!==raw)nativeUnavailable()
    const receipt=await this.custody.read("candidate-test-result",key)
    if(receipt!==undefined){
      const retained=JSON.parse(receipt)
      if(original!==raw||retained.original!==raw)nativeUnavailable()
      return retained.result
    }
    if(original!==undefined)return {outcome:"unknown",output:"Original execution has no retained result; no second dispatch"}
    const subject=testSubject(request)
    const first=await this.custody.read("candidate-test-subject",subject)
    if(request.previousRunId===null){
      if(first!==undefined&&first!==raw)nativeUnavailable()
      if(first===undefined&&!await this.custody.retain("candidate-test-subject",subject,raw))nativeUnavailable()
    }
    if(request.previousRunId!==null){
      if(!request.safeRepeat||!uuidPattern.test(request.previousRunId)||request.previousRunId===key)nativeUnavailable()
      const old=await this.custody.read("candidate-test-result",request.previousRunId)
      if(old===undefined)nativeUnavailable()
      const previous=JSON.parse(old),prior=JSON.parse(previous.original) as CandidateTestRequest
      if(!prior.safeRepeat||previous.result.outcome==="unknown"||prior.checkId!==request.checkId||
        JSON.stringify(prior.candidate)!==JSON.stringify(request.candidate)||JSON.stringify(prior.command)!==JSON.stringify(request.command))nativeUnavailable()
      if(first===undefined||testSubject(prior)!==subject)nativeUnavailable()
      let ancestor=prior,depth=0
      while(ancestor.previousRunId!==null){
        if(++depth>256)nativeUnavailable()
        const ancestorRaw=await this.custody.read("candidate-test-run",ancestor.previousRunId)
        if(ancestorRaw===undefined)nativeUnavailable()
        ancestor=JSON.parse(ancestorRaw)
        if(testSubject(ancestor)!==subject)nativeUnavailable()
      }
      if(JSON.stringify(ancestor)!==first)nativeUnavailable()
      const successor=await this.custody.read("candidate-test-successor",request.previousRunId)
      if(successor!==undefined&&successor!==raw)nativeUnavailable()
      if(successor===undefined&&!await this.custody.retain("candidate-test-successor",request.previousRunId,raw))nativeUnavailable()
    }
    if(!await this.custody.retain("candidate-test-run",key,raw))nativeUnavailable()
    let result:CandidateTestOutcome
    try {result=await this.execute(request,signal)}catch {result={outcome:"unknown",output:"Execution adapter lost its outcome"}}
    if(!["passed","failed","cancelled","unavailable","unknown"].includes(result.outcome)||typeof result.output!=="string"||
      Buffer.byteLength(result.output)>1024*1024)result={outcome:"unknown",output:"Invalid or oversized execution outcome"}
    await this.custody.retain("candidate-test-result",key,JSON.stringify({original:raw,result}))
    return result
  }
  async readyForReview(candidate:CandidateAssemblyResult,planId:string):Promise<boolean>{
    if(!uuidPattern.test(planId))return false
    const rawPlan=await this.custody.read("candidate-test-plan",planId)
    if(rawPlan===undefined)return false
    const plan=JSON.parse(rawPlan),subject=JSON.parse(plan.subject)
    if(candidateSubject(subject.candidate)!==candidateSubject(candidate)||
      await this.custody.read("candidate-required-plan",candidateSubject(candidate))!==JSON.stringify({planId,subject:plan.subject}))return false
    const required=[] as Array<{checkId:string;runId:string}>
    for(const check of subject.checks){
      const run=plan.runs.find((item:any)=>item.checkId===check.checkId)
      if(!run)return false
      const first=await this.custody.read("candidate-test-run",run.runId)
      if(first===undefined)return false
      const selected=await this.selectedRun(JSON.parse(first))
      required.push({checkId:check.checkId,runId:selected.runId})
    }
    if(!required.length||new Set(required.map(check=>check.checkId)).size!==required.length)return false
    for(const check of required){
      const raw=await this.custody.read("candidate-test-result",check.runId)
      if(raw===undefined)return false
      const retained=JSON.parse(raw),request=JSON.parse(retained.original) as CandidateTestRequest
      const planned=subject.checks.find((item:any)=>item.checkId===check.checkId)
      if(request.runId!==check.runId||request.checkId!==check.checkId||retained.result.outcome!=="passed"||
        !planned||JSON.stringify(request.command)!==JSON.stringify(planned.command)||request.safeRepeat!==planned.safeRepeat||
        JSON.stringify(request.candidate)!==JSON.stringify(candidate)||
        await this.custody.read("candidate-test-run",check.runId)!==retained.original)return false
    }
    return true
  }
}

export type PlannedCandidateCheck=Readonly<{checkId:string;command:readonly string[];safeRepeat:boolean;dependencies:readonly string[]}>
/** Operational scheduler: no batch barrier, no inference and no approval implied by a green run.
 * Persisted plan/run identities are reused on restart, including the one explicitly safe retry. */
export async function runCandidateChecks(custody:LinuxNativeCustody,jobs:NativeCandidateTests,planId:string,
  candidate:CandidateAssemblyResult,checks:readonly PlannedCandidateCheck[],capacity:number,signal:AbortSignal){
  if(!uuidPattern.test(planId)||!checks.length||checks.length>256||!Number.isInteger(capacity)||capacity<1||capacity>64||
    new Set(checks.map(check=>check.checkId)).size!==checks.length)nativeUnavailable()
  const byId=new Map(checks.map(check=>[check.checkId,check]))
  const visiting=new Set<string>(),visited=new Set<string>()
  const visit=(id:string)=>{
    if(visiting.has(id)||!byId.has(id))nativeUnavailable()
    if(visited.has(id))return
    visiting.add(id);for(const dependency of byId.get(id)!.dependencies)visit(dependency)
    visiting.delete(id);visited.add(id)
  }
  for(const check of checks)visit(check.checkId)
  const subject=JSON.stringify({candidate,checks}),old=await custody.read("candidate-test-plan",planId)
  const required=JSON.stringify({planId,subject}),priorPlan=await custody.read("candidate-required-plan",candidateSubject(candidate))
  if(priorPlan!==undefined&&priorPlan!==required)nativeUnavailable()
  if(priorPlan===undefined&&!await custody.retain("candidate-required-plan",candidateSubject(candidate),required))nativeUnavailable()
  const plan=old===undefined?{subject,runs:checks.map(check=>({checkId:check.checkId,runId:randomUUID(),retryId:randomUUID()}))}:JSON.parse(old)
  if(plan.subject!==subject)nativeUnavailable()
  if(old===undefined&&!await custody.retain("candidate-test-plan",planId,JSON.stringify(plan)))nativeUnavailable()
  const results=new Map<string,{runId:string;result:CandidateTestOutcome}>(),running=new Map<string,Promise<void>>()
  const pending=new Set(checks.map(check=>check.checkId))
  while(pending.size||running.size){
    for(const id of pending){
      if(signal.aborted||running.size>=capacity)break
      const check=byId.get(id)!
      if(!check.dependencies.every(dependency=>results.get(dependency)?.result.outcome==="passed"))continue
      pending.delete(id)
      const run=plan.runs.find((item:any)=>item.checkId===id)
      if(!run||!uuidPattern.test(run.runId)||!uuidPattern.test(run.retryId))nativeUnavailable()
      const operation=(async()=>{
        let runId=run.runId
        const request={runId,candidate,checkId:id,command:check.command,safeRepeat:check.safeRepeat,previousRunId:null}
        const selected=await jobs.selectedRun(request)
        runId=selected.runId
        let result=await jobs.run(selected,signal)
        if(runId===run.runId&&result.outcome==="failed"&&check.safeRepeat&&!signal.aborted){
          runId=run.retryId;result=await jobs.run({...request,runId,previousRunId:run.runId},signal)
        }
        results.set(id,{runId,result})
      })().finally(()=>{running.delete(id)})
      running.set(id,operation)
    }
    if(!running.size)break // failed dependency/cancellation holds remaining jobs, never drops a gate
    try {await Promise.race(running.values())}
    catch(error){await Promise.allSettled(running.values());throw error}
  }
  return {results,held:[...pending],ready:pending.size===0&&await jobs.readyForReview(candidate,planId)}
}

export type CandidateDeliveryProfile=Readonly<{planRefJSON:string;operationId:string;candidateId:string;head:GitSnapshot;
  time:string;testPlanId:string;capacity:number}>
export type CandidateReviewTask=Readonly<{formatVersion:1;reviewId:string;contextId:string;reviewerPrincipalId:string;
  candidate:CandidateAssemblyResult;selectedJSON:string;planRefJSON:string;testPlanId:string;access:"immutable-snapshot-read-only"}>
export type CandidateReviewReport=Readonly<{reviewId:string;contextId:string;reviewerPrincipalId:string;candidate:CandidateAssemblyResult;
  summary:string;findings:readonly Readonly<{severity:"blocking"|"nonblocking";requirement:string;scenario:string;expected:string;actual:string;evidence:string}>[]}>
export type CandidateReviewAdapter=Readonly<{reviewId:string;contextId:string;reviewerPrincipalId:string;
  execute:(task:CandidateReviewTask,signal:AbortSignal)=>Promise<CandidateReviewReport>}>

/** This trusted adapter must establish a fresh non-author context with separate report/scratch.
 * It receives no candidate writer/integration capability. This cooperative interface does not
 * qualify OS isolation or turn a model-free review double into semantic conformance evidence. */
async function reviewCandidate(custody:LinuxNativeCustody,task:CandidateReviewTask,excluded:readonly string[],adapter:CandidateReviewAdapter,signal:AbortSignal){
  if(![task.reviewId,task.contextId,task.reviewerPrincipalId].every(id=>uuidPattern.test(id))||
    excluded.includes(task.reviewerPrincipalId))nativeUnavailable()
  const raw=JSON.stringify(task),original=await custody.read("candidate-review-task",task.reviewId)
  const subject=JSON.stringify([candidateSubject(task.candidate),task.planRefJSON,task.testPlanId])
  const retainedSubject=await custody.read("candidate-review-subject",subject)
  if(retainedSubject!==undefined&&retainedSubject!==raw)nativeUnavailable()
  if(retainedSubject===undefined&&!await custody.retain("candidate-review-subject",subject,raw))nativeUnavailable()
  if(original!==undefined&&original!==raw)nativeUnavailable()
  const validReport=(report:CandidateReviewReport)=>report!==null&&typeof report==="object"&&
    report.reviewId===task.reviewId&&report.contextId===task.contextId&&report.reviewerPrincipalId===task.reviewerPrincipalId&&
    report.candidate!==null&&typeof report.candidate==="object"&&candidateSubject(report.candidate)===candidateSubject(task.candidate)&&
    typeof report.summary==="string"&&!!report.summary.trim()&&Array.isArray(report.findings)&&report.findings.length<=256&&
    !report.findings.some(finding=>!["blocking","nonblocking"].includes(finding.severity)||
      [finding.requirement,finding.scenario,finding.expected,finding.actual,finding.evidence].some(text=>typeof text!=="string"||!text.trim()))
  const receipt=await custody.read("candidate-review-report",task.reviewId)
  if(receipt!==undefined){
    const saved=JSON.parse(receipt)
    if(original!==raw||saved.original!==raw||!validReport(saved.report))nativeUnavailable()
    return saved.report as CandidateReviewReport
  }
  if(original!==undefined)return null // unresolved original review, not permission for another dispatch
  const context=await custody.read("candidate-review-context",task.contextId)
  if(context!==undefined&&context!==raw)nativeUnavailable()
  if(context===undefined&&!await custody.retain("candidate-review-context",task.contextId,raw))nativeUnavailable()
  if(!await custody.retain("candidate-review-task",task.reviewId,raw))nativeUnavailable()
  const frozen=Object.freeze({...task,candidate:Object.freeze({commit:Object.freeze({...task.candidate.commit}),tree:Object.freeze({...task.candidate.tree})})})
  const report=await adapter.execute(frozen,signal)
  if(!validReport(report))nativeUnavailable()
  const saved=JSON.stringify({original:raw,report})
  if(Buffer.byteLength(saved)>1024*1024)nativeUnavailable()
  await custody.retain("candidate-review-report",task.reviewId,saved)
  return report
}
/** One ordinary-runtime delivery tick. All required implementation selections and the complete
 * check plan come from the protected service, not a caller-selected successful subset. */
export async function advanceCandidateDelivery(source:string,custody:LinuxNativeCustody,git:LinuxGitWitness,
  registry:NativeCredentialRegistry,sessionID:string,profile:CandidateDeliveryProfile,
  execute:(request:CandidateTestRequest,signal:AbortSignal)=>Promise<CandidateTestOutcome>,signal:AbortSignal,review?:CandidateReviewAdapter){
  const planRefJSON=contextJSON(exactJSON(profile.planRefJSON,nativeUnavailable))
  const selection=await registry.candidateDelivery(sessionID,planRefJSON,signal)
  if(selection===null)return {phase:"waiting-for-results" as const}
  const request:CandidateAssemblyRequest={operationId:profile.operationId,candidateId:profile.candidateId,head:profile.head,
    time:profile.time,selectedJSON:selection.selectedJSON,inputs:selection.inputs}
  const assembly=new NativeCandidateAssembly(source,custody,git,(input,abort)=>registry.candidateAssemblyPermit(sessionID,JSON.stringify(input),abort))
  const candidate=await assembly.assemble(request,signal)
  const checks=(await registry.candidateTestPlan(sessionID,planRefJSON,signal)).map(check=>({...check,
    command:check.command.map(argument=>argument.replaceAll("{candidateCommit}",candidate.commit.hex).replaceAll("{candidateTree}",candidate.tree.hex))}))
  const jobs=new NativeCandidateTests(custody,(job,abort)=>registry.candidateTestPermit(sessionID,JSON.stringify(request),planRefJSON,JSON.stringify(job),abort),
    async(job,abort)=>{await git.qualifySource(source,{...profile.head,...job.candidate},abort);return execute(job,abort)})
  const tests=await runCandidateChecks(custody,jobs,profile.testPlanId,candidate,checks,profile.capacity,signal)
  if(tests.ready&&review){
    // Recapture current exact service selection before sending any review context.
    const current=await registry.candidateDelivery(sessionID,planRefJSON,signal)
    if(current===null||current.selectedJSON!==request.selectedJSON)nativeUnavailable()
    await registry.candidateAssemblyPermit(sessionID,JSON.stringify(request),signal)
    const task:CandidateReviewTask={formatVersion:1,reviewId:review.reviewId,contextId:review.contextId,reviewerPrincipalId:review.reviewerPrincipalId,
      candidate,selectedJSON:request.selectedJSON,planRefJSON,testPlanId:profile.testPlanId,access:"immutable-snapshot-read-only"}
    const report=await reviewCandidate(custody,task,current.excludedReviewers,review,signal)
    return {phase:report===null?"review-unknown" as const:report.findings.some(finding=>finding.severity==="blocking")?"review-blocked" as const:"review-complete" as const,
      candidate,tests,report,integrationAllowed:false as const}
  }
  return {phase:tests.ready?"ready-for-review" as const:"tests-held" as const,candidate,tests}
}
