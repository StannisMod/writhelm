// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.Utf8Content
import kotlin.test.*
import org.junit.jupiter.api.Test

private class ReviewJourney(val f: ReviewProbeFixture,val producer: WorkReference,val n: Int) {
    val task=f.taskKey.copy(taskId=uid(n));val verification=f.planKey.copy(id=uid(n+1))
    val assignment=uid(n+2);val workspace=uid(n+3);val resource=WorkKey.Resource(f.project,uid(n+4))
    val subject=WAssignmentSubject(assignment,0)
    val intended=f.executionIntent.copy(executionId=uid(n+5),template="reviewer-v1",nativeSessionId="ses_review_$n")
    val execution=WExecution(intended.executionId,uid(n+6),uid(n+7),intended.nativeHostId,intended.nativeSessionId,workspace)
    val actor=WorkExecutionInput(execution.principalId,execution.credentialId,null,subject,execution)
    val manifest: WorkReference
    val outcome: WorkReference
    val cutId=uid(n+15)
    val plan=WorkKey.Revision(f.project,uid(n+16),0)
    val view=WorkKey.Revision(f.project,uid(n+17),0)
    val memory=WorkKey.Revision(f.project,uid(n+18),0)
    init {
        val terms=f.task.copy(kind="verification",verificationPlan=verification,dependencies=listOf(WDependency(f.taskKey,"implemented",producer,emptyList(),"review exact result")))
        f.apply(CCreateTaskRevisionOperation(CCreateTaskRevision(task,terms,CCreateTaskRevisionNewVerificationPlan(verification,f.plan.copy(task=task)),emptyList())))
        val manifestKey=WorkKey.Id(f.project,uid(n+8))
        f.apply(CPinInputManifestOperation(CPinInputManifest(manifestKey,WInputManifests(task,f.base,f.appKey,listOf(WManifestItem(f.source,true,"review input",emptyList())),f.source,emptyList()),emptyList())))
        manifest=f.state.reference(WorkFamily.INPUT_MANIFESTS,manifestKey)
        f.facts=f.facts.copy(verifiedPackets=f.facts.verifiedPackets+manifest)
        val prepare=uid(n+9)
        f.apply(CReserveAssignmentOperation(CReserveAssignment(assignment,task,manifest,f.profileKey,workspace,listOf(CReserveAssignmentResourcesItem(resource,null)),listOf(producer),prepare,uid(n+10),emptyList(),intended)))
        f.state=f.reducer.bindRegistration(f.state,WorkRegistrationBinding(assignment,0,0,task,intended,listOf(producer),uid(n+11))).after
        f.facts=f.facts.copy(registeredExecutions=f.facts.registeredExecutions+(uid(n+11) to execution),
            native=f.facts.native+(prepare to WorkNativeObservation(subject,prepare,uid(n+10),"prepare","confirmed",listOf(f.source),execution,true,"reserved")))
        f.apply(CBindAssignmentOperation(CBindAssignment(assignment,1,prepare,0,execution.nativeHostId,execution.nativeSessionId,workspace,listOf(f.source),emptyList())))
        val start=uid(n+12)
        f.apply(CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,start,uid(n+13),"start",null,emptyList())))
        f.facts=f.facts.copy(native=f.facts.native+(start to WorkNativeObservation(subject,start,uid(n+13),"start","confirmed",listOf(f.source),execution,false,"bound")))
        f.apply(CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,start,0,"confirmed",listOf(f.source),emptyList())))
        val reportKey=WorkKey.Id(f.project,uid(n+14))
        f.apply(CSubmitVerificationReportOperation(CSubmitVerificationReport(reportKey,CSubmitVerificationReportData(task,subject,listOf(producer),verification,
            f.plan.gates.map { it.id },f.source,listOf(f.source),emptyList(),emptyList(),f.reportRef,emptyList()),emptyList())),actor)
        outcome=f.state.reference(WorkFamily.VERIFICATION_REPORTS,reportKey)
    }
    fun cut(id: java.util.UUID=cutId) {
        val locator=uid(n+19);val digest=Digest.of(byteArrayOf(1))
        f.facts=f.facts.copy(sourceCuts=f.facts.sourceCuts+(locator to WorkRetainedCut(execution.nativeHostId,execution.nativeSessionId,digest,digest,listOf("msg_exact_$n" to digest))))
        f.apply(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(f.project,id),WSourceCuts(task,subject,execution.nativeHostId,execution.nativeSessionId,f.profileKey,
            1,digest,digest,locator,listOf(WSourceCutsNativeMessagesItem("msg_exact_$n",digest)),emptyList(),emptyList()),emptyList())),actor)
    }
    fun continuation() {
        cut()
        f.apply(CUpdatePlanOperation(CUpdatePlan(plan,task,null,emptyList(),"own review tracker",null,emptyList(),subject)),actor)
        f.apply(CRecordLoadedViewOperation(CRecordLoadedView(view,CRecordLoadedViewData(task,manifest,subject,null,emptyList(),emptyList()),emptyList())),actor)
        f.apply(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(task,subject,null,cutId,emptyList(),emptyList(),emptyList()),emptyList())),actor)
    }
    fun retrospective(key: WorkKey.Id=WorkKey.Id(f.project,uid(n+20))): WorkReference {
        f.apply(CRecordRetrospectiveOperation(CRecordRetrospective(key,CRecordRetrospectiveData(task,subject,outcome,"no-significant-new-findings",emptyList()),emptyList())),actor)
        return f.state.reference(WorkFamily.RETROSPECTIVES,key)
    }
    fun delivery(key: WorkKey.Id=WorkKey.Id(f.project,uid(n+21)),cut: java.util.UUID=cutId): WorkKey.Id {
        // Destination is a separate task (primary), not an invented executor identity for reviewer.
        f.apply(CRecordDeliveryOperation(CRecordDelivery(key,subject,cut,f.taskKey,f.reportRef,null,"pending",emptyList(),emptyList())))
        return key
    }
    fun checkpoint(retro: WorkReference,notification: WorkKey.Id)=CRecordCheckpoint(WorkKey.Id(f.project,uid(n+22)),
        CRecordCheckpointData(task,subject,manifest,view,cutId,plan,0,memory,emptyList(),emptyList(),emptyList(),f.reportRef,"finished",retro,outcome,notification),
        f.state.assignment(assignment).generation,emptyList())
}

class ReviewerHandoffRemediationTest {
    @Test fun `F3 separate reviewer own report continuation retrospective durable notification and completion`() {
        val f=ReviewProbeFixture();f.active();val producer=f.result()
        val review=ReviewJourney(f,producer,800);review.continuation()
        val retro=review.retrospective();val delivery=review.delivery()
        val unfinished=review.checkpoint(retro,delivery)
        f.apply(CRecordCheckpointOperation(unfinished.copy(key=WorkKey.Id(f.project,uid(823)),
            data=unfinished.data.copy(handoff="unfinished",result=null,notification=null))),review.actor)
        val cp=review.checkpoint(retro,delivery)
        val beforeGeneration=f.state.assignment(review.assignment).generation
        f.apply(CRecordCheckpointOperation(cp),review.actor)
        assertEquals(beforeGeneration+1,f.state.assignment(review.assignment).generation)
        assertEquals(0,f.state.assignment(review.assignment).executionGeneration)
        val checkpoint=f.state.reference(WorkFamily.CHECKPOINTS,cp.key)
        val complete=CCompleteAssignment(review.assignment,f.state.assignment(review.assignment).generation,checkpoint,listOf(f.source),listOf(review.resource),emptyList())
        assertEquals(WorkProblem.NATIVE_UNKNOWN,assertFailsWith<WorkFault> { f.apply(CCompleteAssignmentOperation(complete)) }.problem)
        f.facts=f.facts.copy(completedAssignments=setOf(review.subject))
        assertEquals(WorkProblem.INCOMPLETE_HANDOFF,assertFailsWith<WorkFault> { f.apply(CCompleteAssignmentOperation(complete)) }.problem)
        assertEquals("held",(f.state.record(WorkFamily.CLAIMS,review.resource).payload as WClaimsPayload).value.state)
        f.apply(CRecordDeliveryOperation(CRecordDelivery(delivery,review.subject,review.cutId,f.taskKey,f.reportRef,0,"durable-delivered",listOf(f.source),emptyList())))
        f.apply(CCompleteAssignmentOperation(complete))
        assertEquals("completed",f.state.assignment(review.assignment).state)
        assertEquals("free",(f.state.record(WorkFamily.CLAIMS,review.resource).payload as WClaimsPayload).value.state)
        assertEquals("active",f.state.assignment(f.assignmentId).state)
        assertEquals("held",(f.state.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
        assertEquals(listOf(review.outcome.identity),f.state.subjectProjection(review.subject).retainedOwnReports.map { it.identity })
        assertTrue(f.state.subjectProjection(review.subject).retainedOwnResults.isEmpty())
        WorkState.restore(f.project,f.state.records.values,f.state.bodies.values)
    }
    @Test fun `F3 foreign outcome role task generation retrospective or notification lineage refuses atomically`() {
        val f=ReviewProbeFixture();f.active();val producer=f.result()
        val a=ReviewJourney(f,producer,900);a.continuation();val retro=a.retrospective();val notification=a.delivery()
        val b=ReviewJourney(f,producer,1000);b.cut();val otherDelivery=b.delivery()
        val producerRetro=(f.state.resolve(producer).payload as WResultsPayload).value.retrospective
        val extraCut=uid(1030);a.cut(extraCut);val otherCutNotification=a.delivery(WorkKey.Id(f.project,uid(1031)),extraCut)
        val different=Utf8Content.fromText("Different notification content, not this checkpoint report")
        val wrongContent=WorkKey.Id(f.project,uid(1033))
        f.apply(CRecordDeliveryOperation(CRecordDelivery(wrongContent,a.subject,a.cutId,f.taskKey,WorkBodyReference(different.digest,different.sizeBytes),null,"pending",emptyList(),
            listOf(CRecordDeliveryBodiesItem(different.digest,different.text())))))
        val valid=a.checkpoint(retro,notification)
        val arbitrary=f.state.reference(WorkFamily.INPUT_MANIFESTS,f.manifestKey)
        val invalid=listOf(
            valid.copy(data=valid.data.copy(result=producer)),
            valid.copy(data=valid.data.copy(result=b.outcome)),
            valid.copy(data=valid.data.copy(assignment=a.subject.copy(generation=1))),
            valid.copy(data=valid.data.copy(task=f.taskKey)),
            valid.copy(data=valid.data.copy(retrospective=producerRetro)),
            valid.copy(data=valid.data.copy(notification=otherDelivery)),
            valid.copy(data=valid.data.copy(notification=otherCutNotification)),
            valid.copy(data=valid.data.copy(notification=wrongContent)),
            valid.copy(data=valid.data.copy(result=arbitrary)),
            valid.copy(data=valid.data.copy(result=null)),
            valid.copy(data=valid.data.copy(notification=null))
        )
        invalid.forEach { bad ->
            val before=f.state
            assertFails { f.apply(CRecordCheckpointOperation(bad),a.actor) }
            assertSame(before,f.state)
            assertNull(f.state.assignment(a.assignment).checkpoint)
        }
        // A foreign retrospective outcome is invalid even before attempting checkpoint.
        assertFailsWith<WorkFault> { f.apply(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(f.project,uid(1032)),
            CRecordRetrospectiveData(a.task,a.subject,b.outcome,"no-significant-new-findings",emptyList()),emptyList())),a.actor) }
        f.apply(CRecordCheckpointOperation(valid),a.actor)
        // Full restore also rejects a malicious handoff, rather than relying on current role labels.
        val actual=f.state.record(WorkFamily.CHECKPOINTS,valid.key)
        val corrupt=actual.copy(payload=WCheckpointsPayload((actual.payload as WCheckpointsPayload).value.copy(result=b.outcome)))
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> {
            WorkState.restore(f.project,f.state.records.values.filter { it.identity!=actual.identity }+corrupt,f.state.bodies.values)
        }.problem)
    }
}
