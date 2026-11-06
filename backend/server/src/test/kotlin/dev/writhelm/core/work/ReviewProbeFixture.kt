// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import kotlin.test.*
import org.junit.jupiter.api.Test

internal class ReviewProbeFixture {
    val project=uid(1);val controller=uid(2);val assignmentId=uid(3);val workspace=uid(4)
    val taskKey=WorkKey.TaskRevision(project,uid(5),0);val profileKey=WorkKey.Profile(project,uid(6),0)
    val planKey=WorkKey.Revision(project,uid(7),0);val appKey=WorkKey.Revision(project,uid(8),0)
    val manifestKey=WorkKey.Id(project,uid(9));val resource=WorkKey.Resource(project,uid(10));val intentId=uid(11)
    val criterion=WCriterion(uid(12),"Complete bounded work",emptyList())
    val snapshot=WSnapshot(uid(13),WorkGitOid("sha1","0".repeat(40)),WorkGitOid("sha1","1".repeat(40)),null,uid(14))
    val source=WSourceVariant1Value(WSourceVariant1("artifact",snapshot))
    val base=WBase(listOf(snapshot),uid(15),Digest.of(byteArrayOf(1)))
    val profile=WProfile("qualified-synthetic",listOf(source),WorkModelReference("synthetic","no-call",null),"no-call","bounded",8192,512,512,512,"native","synthetic","E-COOP-1",emptyList(),"linux","qualified")
    val task=WTaskRevisions("research","executable","Research bounded question",base,WScope("declared input closure","bounded work area","routine implementation",emptyList()),emptyList(),emptyList(),listOf(criterion),listOf("escalate changed meaning"),"report",profileKey,planKey,emptyList(),emptyList())
    val plan=WVerificationPlans(taskKey,null,listOf(criterion),listOf(WGate(uid(16),listOf(criterion.id),"pure",false,"acceptance",null,false,"bounded test")),"initial",emptyList())
    val executionIntent=WExecutionIntent(controller,uid(17),"executor-v1",uid(18),"ses_synthetic")
    val execution=WExecution(uid(17),uid(19),uid(20),uid(18),"ses_synthetic",workspace)
    val actor=WorkExecutionInput(uid(21),uid(22),controller,null,null)
    val worker=WorkExecutionInput(execution.principalId,execution.credentialId,null,WAssignmentSubject(assignmentId,0),execution)
    val codec=WorkCodec();val reducer=WorkReducer()
    var facts=WorkExternalFacts(emptySet(),emptySet(),setOf(snapshot),emptyMap(),emptyMap(),emptySet(),emptySet(),emptySet(),emptySet(),emptyMap(),emptyMap(),emptySet())
    var state: WorkState
    val manifest: WorkReference
    init {
        fun record(k: WorkKey,p: WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(p),k),p)
        val manifestRecord=record(manifestKey,WInputManifestsPayload(WInputManifests(taskKey,base,appKey,listOf(WManifestItem(source,true,"exact input",emptyList())),source,emptyList())))
        manifest=WorkReference(manifestRecord.identity,codec.digest(manifestRecord))
        facts=facts.copy(verifiedPackets=setOf(manifest))
        state=WorkState.restore(project,listOf(record(taskKey,WTaskRevisionsPayload(task)),record(profileKey,WProfilesPayload(profile)),record(planKey,WVerificationPlansPayload(plan)),record(appKey,WApplicabilityPayload(WApplicability(null,emptyList(),listOf(source)))),manifestRecord),emptyList())
    }
    fun command(op: WorkOperation)=WorkCommand(uid(90),project,op)
    fun apply(op: WorkOperation,who: WorkExecutionInput=actor): WorkEffect = reducer.reduce(state,command(op),who,facts).also { state=it.after }
    fun reserve()=apply(CReserveAssignmentOperation(CReserveAssignment(assignmentId,taskKey,manifest,profileKey,workspace,listOf(CReserveAssignmentResourcesItem(resource,null)),emptyList(),intentId,uid(23),emptyList(),executionIntent)))
    fun enroll() {
        val a=state.assignment(assignmentId)
        state=reducer.bindRegistration(state,WorkRegistrationBinding(assignmentId,a.generation,0,taskKey,executionIntent,emptyList(),uid(24))).after
        facts=facts.copy(registeredExecutions=mapOf(uid(24) to execution))
    }
    fun bind() {
        facts=facts.copy(native=mapOf(intentId to WorkNativeObservation(WAssignmentSubject(assignmentId,0),intentId,uid(23),"prepare","confirmed",listOf(source),execution,true,"reserved")))
        apply(CBindAssignmentOperation(CBindAssignment(assignmentId,state.assignment(assignmentId).generation,intentId,0,execution.nativeHostId,execution.nativeSessionId,workspace,listOf(source),emptyList())))
    }
    fun active() {
        reserve();enroll();bind()
        val start=uid(25)
        apply(CRequestNativeTransitionOperation(CRequestNativeTransition(assignmentId,state.assignment(assignmentId).generation,start,uid(26),"start",null,emptyList())))
        assertEquals("bound",state.assignment(assignmentId).state)
        facts=facts.copy(native=facts.native+(start to WorkNativeObservation(WAssignmentSubject(assignmentId,0),start,uid(26),"start","confirmed",listOf(source),execution,false,"bound")))
        apply(CObserveNativeTransitionOperation(CObserveNativeTransition(assignmentId,state.assignment(assignmentId).generation,start,0,"confirmed",listOf(source),emptyList())))
    }
    val report=Utf8Content.fromText("Exact synthetic whole-diff/report\r\n")
    val reportRef get()=WorkBodyReference(report.digest,report.sizeBytes)
    fun evidence(n: Int=100): WorkReference {
        val ev=CRecordEvidenceData(listOf(source),emptyList(),"actual synthetic probe","pure",reportRef,uid(n+1),"passed",emptyList(),listOf(CRecordEvidenceDataClauseCasesItem(source,"case","actual-test")),emptyList(),reportRef)
        val bodies=if(report.digest in state.bodies)emptyList()else listOf(CRecordEvidenceBodiesItem(report.digest,report.text()))
        val result=apply(CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,uid(n)),ev,bodies)),worker)
        return state.reference(result.result.family,result.result.key)
    }
    fun result(n: Int=110,evidence: WorkReference?=null): WorkReference {
        val retrospective=WorkKey.Id(project,uid(n+1))
        apply(CRecordRetrospectiveOperation(CRecordRetrospective(retrospective,CRecordRetrospectiveData(taskKey,worker.assignment!!,null,"no-significant-new-findings",emptyList()),emptyList())),worker)
        val retro=state.reference(WorkFamily.RETROSPECTIVES,retrospective)
        facts=facts.copy(submittedDiffs=facts.submittedDiffs+WorkSubmittedDiff(taskKey,listOf(snapshot),reportRef))
        val data=CSubmitResultData(taskKey,worker.assignment!!,manifest,listOf(snapshot),reportRef,listOf(criterion.id),listOfNotNull(evidence),retro,emptyList())
        val bodies=if(report.digest in state.bodies)emptyList()else listOf(CSubmitResultBodiesItem(report.digest,report.text()))
        val result=apply(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,uid(n)),data,bodies)),worker)
        return state.reference(result.result.family,result.result.key)
    }
}
