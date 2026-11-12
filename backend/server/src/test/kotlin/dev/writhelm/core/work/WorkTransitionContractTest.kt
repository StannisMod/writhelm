// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import kotlin.test.*
import org.junit.jupiter.api.Test

private class WorkFixture {
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

class WorkTransitionContractTest {
    @Test fun `model4 attempt binds exact original reservation to its plan`() {
        val f=WorkFixture();val graph=WorkState.restore(f.project,f.state.records.values,f.state.bodies.values,modelVersion=4)
        val entry=DispatchPlanEntry(f.taskKey,f.manifest,f.profileKey,listOf(f.resource),uid(300),uid(301))
        val plan=DispatchPlanCandidate(f.project,uid(302),f.controller,listOf(entry))
        val planned=recordDispatchPlan(graph,f.actor,plan).after
        val planRef=planned.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(f.project,plan.planId))
        val reserve=CReserveAssignment(f.assignmentId,f.taskKey,f.manifest,f.profileKey,f.workspace,listOf(CReserveAssignmentResourcesItem(f.resource,null)),
            emptyList(),f.intentId,uid(23),emptyList(),f.executionIntent)
        val command=f.command(CReserveAssignmentOperation(reserve))
        val attempt=DispatchAttempt(planRef,entry.preparationId,command)
        val row=WorkRecord(WorkRecordIdentity(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(f.project,entry.preparationId)),WDispatchAttemptPayload(attempt))
        val codec=WorkCodec(modelVersion=4);val bytes=codec.encodeRecord(row)
        assertEquals(row,codec.decodeRecord(bytes));assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes) }
        assertEquals(WorkProblem.INVALID_BINDING,assertFailsWith<WorkFault> { bindDispatchAttempt(planned,f.worker,attempt) }.problem)
        val bound=bindDispatchAttempt(planned,f.actor,attempt).after
        assertEquals(attempt,(bound.records.getValue(row.identity).payload as WDispatchAttemptPayload).value)
        val anotherPlan=plan.copy(planId=uid(305),entries=listOf(entry.copy(preparationId=uid(306),startId=uid(307))))
        val otherGraph=recordDispatchPlan(bound,f.actor,anotherPlan).after
        val otherRef=otherGraph.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(f.project,anotherPlan.planId))
        val reused=attempt.copy(plan=otherRef,preparationId=uid(306))
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> { bindDispatchAttempt(otherGraph,f.actor,reused) }.problem)
        val reserved=f.reducer.reduce(bound,command,f.actor,f.facts)
        assertEquals("held",(reserved.after.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
        assertEquals(attempt,(WorkState.restore(f.project,reserved.after.records.values,reserved.after.bodies.values,modelVersion=4).records.getValue(row.identity).payload as WDispatchAttemptPayload).value)
        val tampered=attempt.copy(reservation=command.copy(operation=CReserveAssignmentOperation(reserve.copy(workspaceId=uid(303),profile=f.profileKey.copy(profileId=uid(304))))))
        val bad=row.copy(payload=WDispatchAttemptPayload(tampered))
        assertFailsWith<CodecFault> { WorkState.restore(f.project,planned.records.values+bad,planned.bodies.values,modelVersion=4) }
        val already=f.reducer.reduce(planned,command,f.actor,f.facts).after
        assertEquals(WorkProblem.INVALID_TRANSITION,assertFailsWith<WorkFault> { bindDispatchAttempt(already,f.actor,attempt) }.problem)
    }
    @Test fun `WORK S09 S10 reserve register passive bind start are separate atomic effects`() {
        val f=WorkFixture();val effect=f.reserve()
        assertEquals(3,effect.changedRecords.size)
        assertNull(f.state.assignment(f.assignmentId).execution)
        assertEquals(WorkProblem.INVALID_BINDING,assertFailsWith<WorkFault> { f.bind() }.problem)
        f.enroll();assertEquals(1,f.state.assignment(f.assignmentId).generation)
        f.bind();assertEquals("bound",f.state.assignment(f.assignmentId).state)
        assertEquals(2,f.state.assignment(f.assignmentId).generation)
        assertEquals(0,f.state.assignment(f.assignmentId).executionGeneration)
        assertEquals(f.execution,f.state.binding(WAssignmentSubject(f.assignmentId,0)).execution)
        assertEquals(WorkProblem.CLAIM_CONFLICT,assertFailsWith<WorkFault> { f.reserve() }.problem)
        val p=f.state.assignmentProjection(f.assignmentId)
        assertEquals(f.execution,p.historicalBindings.getValue(0).execution)
    }
    @Test fun `WORK S10 registration foreign stale or duplicate refuses no partial update`() {
        val f=WorkFixture();f.reserve();val before=f.state
        val reg=WorkRegistrationBinding(f.assignmentId,0,0,f.taskKey,f.executionIntent,emptyList(),uid(24))
        assertEquals(WorkProblem.INVALID_BINDING,assertFailsWith<WorkFault> { f.reducer.bindRegistration(before,reg.copy(intendedExecution=f.executionIntent.copy(controllerId=uid(99)))) }.problem)
        assertEquals(WorkProblem.STALE_GENERATION,assertFailsWith<WorkFault> { f.reducer.bindRegistration(before,reg.copy(expectedGeneration=1)) }.problem)
        f.enroll();assertFailsWith<WorkFault> { f.reducer.bindRegistration(f.state,reg) }
        assertNull(before.assignment(f.assignmentId).delegationId)
    }
    @Test fun `WORK S11 S12 uncertain native outcome retains claims and prevents fresh start`() {
        val f=WorkFixture();f.active();val intent=uid(30)
        f.apply(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignmentId,f.state.assignment(f.assignmentId).generation,intent,uid(31),"pause",null,emptyList())))
        f.facts=f.facts.copy(native=f.facts.native+(intent to WorkNativeObservation(WAssignmentSubject(f.assignmentId,0),intent,uid(31),"pause","unknown",listOf(f.source),f.execution,false,"active")))
        f.apply(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignmentId,f.state.assignment(f.assignmentId).generation,intent,0,"unknown",listOf(f.source),emptyList())))
        assertEquals("unknown",f.state.assignment(f.assignmentId).state)
        assertEquals("held",(f.state.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
        assertFailsWith<WorkFault> { f.apply(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignmentId,f.state.assignment(f.assignmentId).generation,uid(32),uid(33),"start",null,emptyList()))) }
        val s=f.worker.assignment!!;f.facts=f.facts.copy(isolatedAssignments=setOf(s))
        f.apply(CRecordRecoveryOperation(CRecordRecovery(WorkKey.Id(f.project,uid(34)),f.state.assignment(f.assignmentId).generation,
            WRecoveries(s,"revoked-isolated",listOf(f.source),listOf(f.resource),emptyList(),listOf("physical stop is not claimed")),emptyList())))
        assertEquals("quarantined",f.state.assignment(f.assignmentId).state)
        assertEquals("isolated",(f.state.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
    }
    @Test fun `WORK S05 S13 controller and worker plans own exact assignment not interchangeable`() {
        val f=WorkFixture();f.active();val key=WorkKey.Revision(f.project,uid(40),0)
        val v=CUpdatePlan(key,f.taskKey,null,emptyList(),"refine within contract",null,emptyList(),f.worker.assignment)
        f.apply(CUpdatePlanOperation(v),f.worker)
        assertFailsWith<WorkFault> { f.apply(CUpdatePlanOperation(v.copy(expectedGeneration=0,assignment=null))) }
        assertFailsWith<WorkFault> { f.apply(CUpdatePlanOperation(v.copy(key=WorkKey.Revision(f.project,uid(41),0),assignment=null)),f.worker) }
        val old=f.state
        val maxRecord=WorkRecord(WorkRecordIdentity(WorkFamily.PLANS,key),WPlansPayload(WPlans(f.taskKey,Long.MAX_VALUE,emptyList(),"retained",null,f.worker.assignment)))
        f.state=WorkState.restore(f.project,old.records.values.filter { it.identity!=maxRecord.identity }+maxRecord,old.bodies.values)
        assertEquals(WorkProblem.GENERATION_EXHAUSTED,assertFailsWith<WorkFault> { f.apply(CUpdatePlanOperation(v.copy(expectedGeneration=Long.MAX_VALUE)),f.worker) }.problem)
        assertEquals(Long.MAX_VALUE,(f.state.record(WorkFamily.PLANS,key).payload as WPlansPayload).value.generation)
    }
    @Test fun `WORK S06 S34 missing foreign and cyclic dependency graph refuses`() {
        val f=WorkFixture();val missing=f.task.copy(dependencies=listOf(WDependency(f.taskKey.copy(taskId=uid(99)),"implemented",null,emptyList(),"required")))
        fun restore(t: WTaskRevisions)=WorkState.restore(f.project,f.state.records.values.filter { it.identity.family!=WorkFamily.TASK_REVISIONS }+WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,f.taskKey),WTaskRevisionsPayload(t)),emptyList())
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { restore(missing) }.problem)
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { restore(missing.copy(dependencies=listOf(WDependency(f.taskKey,"implemented",null,emptyList(),"cycle")))) }.problem)
        assertEquals(CodecProblem.INVALID_SHAPE,assertFailsWith<CodecFault> { restore(f.task.copy(executionProfile=f.profileKey.copy(projectId=uid(99)))) }.problem)
    }
    @Test fun `unselected dependency does not become selected when producer result arrives`() {
        val f=WorkFixture();f.active();val result=f.result()
        fun make(revision:Long,selected:WorkReference?):Pair<WorkState,WorkReference> {
            val task=f.taskKey.copy(taskId=uid(191),revision=revision)
            val plan=WorkKey.Revision(f.project,uid(192),revision)
            val manifest=WorkKey.Id(f.project,uid(193+revision.toInt()))
            val dependency=WDependency(f.taskKey,"implemented",selected,emptyList(),"explicit producer selection")
            fun record(key:WorkKey,payload:WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(payload),key),payload)
            val mr=record(manifest,WInputManifestsPayload(WInputManifests(task,f.base,f.appKey,listOf(WManifestItem(f.source,true,"exact input",emptyList())),f.source,emptyList())))
            val ref=WorkReference(mr.identity,f.codec.digest(mr))
            val rows=f.state.records.values+listOf(record(task,WTaskRevisionsPayload(f.task.copy(dependencies=listOf(dependency),verificationPlan=plan))),
                record(plan,WVerificationPlansPayload(f.plan.copy(task=task))),mr)
            return WorkState.restore(f.project,rows,f.state.bodies.values) to ref
        }
        fun reserve(state:WorkState,manifest:WorkReference):WorkEffect {
            val task=(state.resolve(manifest).payload as WInputManifestsPayload).value.task
            val cmd=CReserveAssignment(uid(197),task,manifest,f.profileKey,uid(198),listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(f.project,uid(199)),null)),
                emptyList(),uid(200),uid(201),emptyList(),f.executionIntent.copy(executionId=uid(202),nativeSessionId="ses_dependent"))
            return f.reducer.reduce(state,f.command(CReserveAssignmentOperation(cmd)),f.actor,f.facts.copy(verifiedPackets=f.facts.verifiedPackets+manifest))
        }
        val (unselected,missing)=make(0,null)
        assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> { reserve(unselected,missing) }.problem)
        val (selectedLegacy,selectedManifest)=make(1,result)
        val selected4=WorkState.restore(f.project,selectedLegacy.records.values,selectedLegacy.bodies.values,modelVersion=4)
        val selectedTask=(selected4.resolve(selectedManifest).payload as WInputManifestsPayload).value.task
        val reservation=CReserveAssignment(uid(197),selectedTask,selectedManifest,f.profileKey,uid(198),
            listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(f.project,uid(199)),null)),emptyList(),uid(200),uid(201),emptyList(),
            f.executionIntent.copy(executionId=uid(202),nativeSessionId="ses_dependent"))
        val reserveCommand=f.command(CReserveAssignmentOperation(reservation));val fingerprint=f.codec.fingerprint(reserveCommand)
        val early=WorkPrebindingClosure(selectedTask,selectedManifest,f.profileKey,fingerprint)
        val inputOnly=f.facts.copy(verifiedPackets=emptySet(),prebindingClosures=setOf(early))
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            f.reducer.reduce(selected4,reserveCommand,f.actor,inputOnly.copy(prebindingClosures=emptySet()))
        }.problem)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            f.reducer.reduce(selected4,reserveCommand,f.actor,inputOnly.copy(prebindingClosures=setOf(early.copy(fingerprint=Digest.of(byteArrayOf(7))))))
        }.problem)
        assertEquals("reserved",f.reducer.reduce(selected4,reserveCommand,f.actor,inputOnly).after.assignment(reservation.assignmentId).state)
        // The early proof is reservation-only. Binding/start still requires the existing full packet.
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            WorkTransaction(selected4,f.actor,inputOnly,reserveCommand.requestKey,fingerprint).taskReady(selectedTask,selectedManifest,f.profileKey)
        }.problem)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            f.reducer.reduce(selectedLegacy,reserveCommand,f.actor,inputOnly)
        }.problem)
        val model4=WorkState.restore(f.project,unselected.records.values,unselected.bodies.values,modelVersion=4)
        val consumer=(model4.resolve(missing).payload as WInputManifestsPayload).value.task
        val entry=DispatchPlanEntry(consumer,missing,f.profileKey,listOf(WorkKey.Resource(f.project,uid(199))),uid(203),uid(204),
            listOf(DispatchFutureDependency(f.taskKey,"implemented")))
        val planned=DispatchPlanCandidate(f.project,uid(205),f.controller,listOf(entry))
        val effect=recordDispatchPlan(model4,f.actor,planned)
        assertEquals(DispatchResultChoice.Selected(result),chooseDispatchResult(effect.after,entry.futureDependencies.single()))
        val planRef=effect.after.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(f.project,planned.planId))
        val newTask=consumer.copy(revision=1);val verify=WorkKey.Revision(f.project,uid(210),0);val newManifest=WorkKey.Id(f.project,uid(211))
        val codec=DispatchMaterializationCommandCodec()
        val command=DispatchMaterializationCommand(uid(212),planRef,consumer,newTask,verify,newManifest)
        val encoded=codec.encode(command)
        assertEquals(command,codec.decode(encoded));assertContentEquals(encoded,codec.encode(codec.decode(encoded)))
        assertEquals(codec.fingerprint(command),codec.fingerprint(command.copy(requestKey=uid(213))))
        assertNotEquals(codec.fingerprint(command),codec.fingerprint(command.copy(manifest=WorkKey.Id(f.project,uid(214)))))
        assertFailsWith<CodecFault> { codec.encode(command.copy(newTask=consumer.copy(revision=2))) }
        assertFailsWith<CodecFault> { codec.encode(command.copy(manifest=WorkKey.Id(uid(999),uid(211)))) }
        assertFailsWith<CodecFault> { WorkCodec().decodeCommand(encoded) }
        val materialized=materializeDispatchDependency(effect.after,planRef,consumer,newTask,verify,newManifest)
        assertEquals(result,materialized.task.data.dependencies.single().selectedResult)
        assertEquals(f.base,materialized.task.data.base)
        assertTrue(materialized.manifest.data.knownGaps.any { it.contains("closure assessment") })
        assertTrue(materialized.manifest.data.items.any { it.mandatory && (it.source as? WSourceVariant2Value)?.value?.reference==result })
        val revised=f.reducer.reduce(effect.after,f.command(CCreateTaskRevisionOperation(materialized.task)),f.actor,f.facts)
        val pinned=f.reducer.reduce(revised.after,f.command(CPinInputManifestOperation(materialized.manifest)),f.actor,f.facts)
        val atomic=reduceDispatchMaterialization(effect.after,f.actor,f.facts,planRef,consumer,newTask,verify,newManifest,uid(212))
        val withProvenance=reduceDispatchMaterialization(effect.after,f.actor,f.facts,codec.decode(encoded))
        assertEquals(atomic.after.records,withProvenance.after.records-withProvenance.result)
        val provenance=withProvenance.after.records.getValue(withProvenance.result)
        assertEquals(command,(provenance.payload as WDispatchMaterializationPayload).value)
        val inventory=prebindingMaterializedInputs(withProvenance.after,command.requestKey,f.facts)
        val automatic=proposeDeclaredMaterializedManifest(withProvenance.after,command.requestKey,WorkKey.Id(f.project,uid(299)),f.facts)
        assertTrue(automatic.data.knownGaps.isEmpty())
        assertEquals((withProvenance.after.resolve(inventory.manifest).payload as WInputManifestsPayload).value.items,automatic.data.items)
        assertEquals(withProvenance.after.reference(WorkFamily.DISPATCH_MATERIALIZATIONS,withProvenance.result.key),
            (automatic.data.declaredClosureAssessment as WSourceVariant2Value).value.reference)
        assertFailsWith<WorkFault> { proposeDeclaredMaterializedManifest(withProvenance.after,command.requestKey,
            WorkKey.Id(f.project,uid(299)),f.facts.copy(retainedSnapshots=emptySet())) }
        assertEquals(withProvenance.after.reference(WorkFamily.TASK_REVISIONS,newTask),inventory.task)
        assertEquals(withProvenance.after.reference(WorkFamily.INPUT_MANIFESTS,newManifest),inventory.manifest)
        assertEquals(listOf(result),inventory.dependencies)
        val applicabilityRecord=withProvenance.after.record(WorkFamily.APPLICABILITY,f.appKey)
        val applicabilityData=(applicabilityRecord.payload as WApplicabilityPayload).value
        val missingSource=WSourceVariant1Value(WSourceVariant1("artifact",f.snapshot.copy(retainedLocatorId=uid(222))))
        val changedApplicability=applicabilityRecord.copy(payload=WApplicabilityPayload(applicabilityData.copy(
            entries=listOf(WApplicabilityEntry(missingSource,emptyList(),emptyList(),"applicable","required")))))
        val missingInput=WorkState.restore(f.project,withProvenance.after.records.values.filter { it.identity!=changedApplicability.identity }+
            changedApplicability,withProvenance.after.bodies.values,modelVersion=4)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            prebindingMaterializedInputs(missingInput,command.requestKey,f.facts.copy(retainedSnapshots=f.facts.retainedSnapshots+missingSource.value.artifact))
        }.problem)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { reserve(withProvenance.after,inventory.manifest) }.problem)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            prebindingMaterializedInputs(withProvenance.after,command.requestKey,f.facts.copy(retainedSnapshots=emptySet()))
        }.problem)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            prebindingMaterializedInputs(withProvenance.after,command.requestKey,f.facts.copy(submittedDiffs=emptySet()))
        }.problem)
        assertFailsWith<WorkFault> { prebindingMaterializedInputs(withProvenance.after,uid(999),f.facts) }
        // Explicitly synthetic independent assessment: this only exercises construction of an
        // unpublished proposal; it is not a qualified assessment or authorization to publish.
        val assessmentSnapshot=f.snapshot.copy(retainedLocatorId=uid(218))
        val assessmentSource=WSourceVariant1Value(WSourceVariant1("artifact",assessmentSnapshot))
        val closureFacts=f.facts.copy(retainedSnapshots=f.facts.retainedSnapshots+assessmentSnapshot)
        val basis=WorkGitOid("sha1","a".repeat(40));val closedKey=WorkKey.Id(f.project,uid(219))
        val proof=PrebindingClosureAssessment(basis,command.requestKey,inventory,assessmentSource,uid(224),uid(225))
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            proposeClosedMaterializedManifest(missingInput,basis,command.requestKey,closedKey,
                closureFacts.copy(retainedSnapshots=closureFacts.retainedSnapshots+missingSource.value.artifact),proof)
        }.problem)
        val closureCodec=DispatchClosureCommandCodec()
        val materializationRef=withProvenance.after.reference(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(f.project,command.requestKey))
        val closureCommand=DispatchClosureCommand(uid(220),materializationRef,basis,assessmentSource,closedKey)
        val closureBytes=closureCodec.encode(closureCommand)
        assertEquals(closureCommand,closureCodec.decode(closureBytes))
        assertContentEquals(closureBytes,closureCodec.encode(closureCodec.decode(closureBytes)))
        assertEquals(closureCodec.fingerprint(closureCommand),closureCodec.fingerprint(closureCommand.copy(requestKey=uid(221))))
        assertNotEquals(closureCodec.fingerprint(closureCommand),closureCodec.fingerprint(closureCommand.copy(expectedHead=WorkGitOid("sha1","b".repeat(40)))))
        assertFailsWith<CodecFault> { closureCodec.encode(closureCommand.copy(manifest=WorkKey.Id(uid(999),uid(219)))) }
        assertFailsWith<CodecFault> { WorkCodec().decodeCommand(closureBytes) } // not registered or published
        val proposal=proposeClosedMaterializedManifest(withProvenance.after,basis,command.requestKey,closedKey,closureFacts,proof)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            f.reducer.reduce(withProvenance.after,f.command(CPinInputManifestOperation(proposal)),f.actor,closureFacts)
        }.problem) // ordinary pin must not bypass the dedicated closure operation
        val stillGapped=proposal.copy(data=proposal.data.copy(knownGaps=listOf("independent assessment still required")))
        assertEquals(closedKey,f.reducer.reduce(withProvenance.after,f.command(CPinInputManifestOperation(stillGapped)),f.actor,closureFacts).result.key)
        assertEquals(newTask,proposal.data.task);assertTrue(proposal.data.knownGaps.isEmpty())
        assertEquals(assessmentSource,proposal.data.declaredClosureAssessment)
        assertTrue((withProvenance.after.resolve(inventory.manifest).payload as WInputManifestsPayload).value.knownGaps.isNotEmpty())
        assertFailsWith<WorkFault> { proposeClosedMaterializedManifest(withProvenance.after,basis,command.requestKey,closedKey,closureFacts,
            proof.copy(basis=WorkGitOid("sha1","b".repeat(40)))) }
        assertFailsWith<WorkFault> { proposeClosedMaterializedManifest(withProvenance.after,basis,command.requestKey,closedKey,closureFacts,
            proof.copy(inputs=inventory.copy(dependencies=emptyList()))) }
        assertFailsWith<WorkFault> { proposeClosedMaterializedManifest(withProvenance.after,basis,command.requestKey,closedKey,f.facts,proof) }
        assertEquals(WorkProblem.STALE_GENERATION,assertFailsWith<WorkFault> {
            reduceDispatchClosure(withProvenance.after,WorkGitOid("sha1","b".repeat(40)),f.actor,closureFacts,closureCommand,proof)
        }.problem)
        assertEquals(WorkProblem.INVALID_BINDING,assertFailsWith<WorkFault> {
            reduceDispatchClosure(withProvenance.after,basis,f.worker,closureFacts,closureCommand,proof)
        }.problem)
        val closedEffect=reduceDispatchClosure(withProvenance.after,basis,f.actor,closureFacts,closureCommand,proof)
        val closureRow=closedEffect.after.records.getValue(closedEffect.result)
        assertEquals(closureCommand,(closureRow.payload as WDispatchClosurePayload).value)
        assertEquals(proof.assessor,(closureRow.payload as WDispatchClosurePayload).assessor)
        assertEquals(proof.delegation,(closureRow.payload as WDispatchClosurePayload).delegation)
        val model4Codec=WorkCodec(modelVersion=4)
        assertEquals(closureRow,model4Codec.decodeRecord(model4Codec.encodeRecord(closureRow)))
        assertFailsWith<CodecFault> { WorkCodec().decodeRecord(model4Codec.encodeRecord(closureRow)) }
        validateDispatchClosureEdge(withProvenance.after,closedEffect.after,basis,closureCommand)
        assertFailsWith<WorkFault> { validateDispatchClosureEdge(withProvenance.after,closedEffect.after,
            WorkGitOid("sha1","b".repeat(40)),closureCommand) }
        assertFailsWith<WorkFault> { validateDispatchClosureEdge(withProvenance.after,closedEffect.after,basis,
            closureCommand.copy(requestKey=uid(223))) }
        val changedManifest=closedEffect.after.record(WorkFamily.INPUT_MANIFESTS,closedKey)
        assertFailsWith<CodecFault> {
            WorkState.restore(f.project,closedEffect.after.records.values.filter { it.identity!=changedManifest.identity }+
                changedManifest.copy(payload=WInputManifestsPayload(proposal.data.copy(knownGaps=listOf("unexpected")))),
                closedEffect.after.bodies.values,modelVersion=4)
        }
        val closedRef=closedEffect.after.reference(WorkFamily.INPUT_MANIFESTS,closedKey)
        val declared=declaredMaterializedInputs(closedEffect.after,command.requestKey,closureFacts,closedRef)
        assertEquals(inventory.task,declared.task)
        assertEquals(inventory.dependencies,declared.dependencies)
        assertEquals(closedRef,declared.manifest)
        assertFailsWith<WorkFault> {
            declaredMaterializedInputs(closedEffect.after,command.requestKey,closureFacts.copy(retainedSnapshots=emptySet()),closedRef)
        }
        assertFailsWith<WorkFault> {
            declaredMaterializedInputs(closedEffect.after,command.requestKey,closureFacts.copy(submittedDiffs=emptySet()),closedRef)
        }
        assertNull(dispatchClosedSelection(withProvenance.after,planRef,entry))
        val selection=assertNotNull(dispatchClosedSelection(closedEffect.after,planRef,entry))
        assertEquals(newTask,selection.task);assertEquals(closedRef,selection.manifest)
        assertEquals(closedEffect.after.reference(WorkFamily.DISPATCH_CLOSURES,closedEffect.result.key),selection.closure)
        val competing=closureCommand.copy(requestKey=uid(233),manifest=WorkKey.Id(f.project,uid(234)))
        val competingEffect=reduceDispatchClosure(closedEffect.after,basis,f.actor,closureFacts,competing,proof)
        assertNull(dispatchClosedSelection(competingEffect.after,planRef,entry)) // never guess between closures
        val nextReservation=reservation.copy(task=newTask,manifest=closedRef)
        val nextCommand=f.command(CReserveAssignmentOperation(nextReservation))
        val dependentAttempt=DispatchAttempt(planRef,entry.preparationId,nextCommand)
        val dependentBound=bindDispatchAttempt(closedEffect.after,f.actor,dependentAttempt)
        assertEquals(dependentAttempt,(dependentBound.after.records.getValue(dependentBound.result).payload as WDispatchAttemptPayload).value)
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> {
            bindDispatchAttempt(closedEffect.after,f.actor,dependentAttempt.copy(reservation=nextCommand.copy(
                operation=CReserveAssignmentOperation(nextReservation.copy(manifest=inventory.manifest)))))
        }.problem)
        val anotherPlan=planned.copy(planId=uid(230),entries=listOf(entry.copy(preparationId=uid(231),startId=uid(232))))
        val anotherGraph=recordDispatchPlan(closedEffect.after,f.actor,anotherPlan).after
        val anotherRef=anotherGraph.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(f.project,anotherPlan.planId))
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> {
            bindDispatchAttempt(anotherGraph,f.actor,dependentAttempt.copy(plan=anotherRef,preparationId=uid(231)))
        }.problem) // a closure from another plan cannot authorize this attempt
        val nextFingerprint=f.codec.fingerprint(nextCommand)
        val verifiedInputs=closureFacts.copy(verifiedPackets=emptySet(),
            prebindingClosures=setOf(WorkPrebindingClosure(newTask,closedRef,f.profileKey,nextFingerprint)))
        assertEquals("reserved",f.reducer.reduce(dependentBound.after,nextCommand,f.actor,verifiedInputs).after.assignment(nextReservation.assignmentId).state)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
            WorkTransaction(closedEffect.after,f.actor,verifiedInputs,nextCommand.requestKey,nextFingerprint).taskReady(newTask,closedRef,f.profileKey)
        }.problem) // separate full packet is still mandatory after binding
        validateDispatchMaterializationEdge(effect.after,withProvenance.after,command)
        val v4=WorkCodec(modelVersion=4);val bytes=v4.encodeRecord(provenance)
        assertEquals(provenance,v4.decodeRecord(bytes));assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes) }
        assertEquals(withProvenance.after.records,WorkState.restore(f.project,withProvenance.after.records.values,withProvenance.after.bodies.values,modelVersion=4).records)
        assertEquals(pinned.after.records,atomic.after.records)
        assertEquals(setOf(WorkFamily.TASK_REVISIONS,WorkFamily.VERIFICATION_PLANS,WorkFamily.INPUT_MANIFESTS),atomic.changedRecords.map { it.identity.family }.toSet())
        assertEquals(WorkProblem.INVALID_BINDING,assertFailsWith<WorkFault> {
            reduceDispatchMaterialization(effect.after,f.worker,f.facts,planRef,consumer,newTask,verify,newManifest,uid(212))
        }.problem)
        assertEquals(WorkProblem.DUPLICATE_ID,assertFailsWith<WorkFault> {
            reduceDispatchMaterialization(effect.after,f.actor,f.facts,planRef,consumer,newTask,verify,missing.identity.key as WorkKey.Id,uid(212))
        }.problem)
        assertFalse(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,newTask) in effect.after.records)
        val another=f.result(120)
        val extra=f.state.records.values.filter { it.identity !in effect.after.records }
        assertTrue(extra.any { it.identity==another.identity })
        val ambiguous=WorkState.restore(f.project,effect.after.records.values+extra,effect.after.bodies.values,modelVersion=4)
        assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> {
            validateDispatchMaterializationEdge(ambiguous,withProvenance.after,command)
        }.problem)
        // A later producer result cannot retroactively invalidate an already recorded exact choice.
        assertEquals(withProvenance.after.records.size+extra.size,
            WorkState.restore(f.project,withProvenance.after.records.values+extra,withProvenance.after.bodies.values,modelVersion=4).records.size)
        assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> {
            reduceDispatchMaterialization(ambiguous,f.actor,f.facts,codec.decode(encoded))
        }.problem)
        assertFalse(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,newTask) in ambiguous.records)
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> {
            validateDispatchMaterializationEdge(effect.after,withProvenance.after,command.copy(requestKey=uid(215)))
        }.problem)
        val newRef=pinned.after.reference(WorkFamily.INPUT_MANIFESTS,newManifest)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { reserve(pinned.after,newRef) }.problem)
        assertEquals(WorkProblem.INVALID_SUBJECT,assertFailsWith<WorkFault> {
            materializeDispatchDependency(effect.after,planRef,consumer,consumer.copy(revision=2),verify,newManifest)
        }.problem)
        val wrong=planned.copy(planId=uid(206),entries=listOf(entry.copy(futureDependencies=listOf(DispatchFutureDependency(consumer,"implemented")))))
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> { recordDispatchPlan(model4,f.actor,wrong) }.problem)
        val (selected,exact)=make(1,result)
        assertEquals("reserved",(reserve(selected,exact).after.assignment(uid(197))).state)
    }
    @Test fun `future dependency chooses only a unique exact producer result`() {
        val f=WorkFixture();val rule=DispatchFutureDependency(f.taskKey,"implemented")
        assertEquals(DispatchResultChoice.Pending,chooseDispatchResult(f.state,rule))
        f.active();val first=f.result(110)
        assertEquals(DispatchResultChoice.Selected(first),chooseDispatchResult(f.state,rule))
        f.result(120)
        assertEquals(DispatchResultChoice.Ambiguous,chooseDispatchResult(f.state,rule))
        assertEquals(WorkProblem.INVALID_SUBJECT,assertFailsWith<WorkFault> { chooseDispatchResult(f.state,rule.copy(readiness="accepted")) }.problem)
        assertEquals(WorkProblem.INVALID_SUBJECT,assertFailsWith<WorkFault> { chooseDispatchResult(f.state,rule.copy(producer=f.taskKey.copy(projectId=uid(999)))) }.problem)
    }
    @Test fun `WORK S04 S35 exact missing body attachments not upload or reupload`() {
        val f=WorkFixture();f.active()
        val body=Utf8Content.fromText("Exact dirty change summary\r\n")
        val key=WorkKey.Id(f.project,uid(50))
        val ev=CRecordEvidenceData(listOf(f.source),emptyList(),"actual component probe","pure",WorkBodyReference(body.digest,body.sizeBytes),uid(51),"passed",emptyList(),emptyList(),emptyList(),WorkBodyReference(body.digest,body.sizeBytes))
        val op=CRecordEvidence(key,ev,emptyList())
        assertEquals(WorkProblem.MISSING_BODY,assertFailsWith<WorkFault> { f.apply(CRecordEvidenceOperation(op),f.worker) }.problem)
        val added=f.apply(CRecordEvidenceOperation(op.copy(bodies=listOf(CRecordEvidenceBodiesItem(body.digest,body.text())))),f.worker)
        assertEquals(1,added.addedBodies.size)
        assertEquals(WorkProblem.DUPLICATE_ID,assertFailsWith<WorkFault> { f.apply(CRecordEvidenceOperation(op),f.worker) }.problem)
        assertEquals(WorkProblem.MISSING_BODY,assertFailsWith<WorkFault> { f.apply(CRecordEvidenceOperation(op.copy(key=WorkKey.Id(f.project,uid(52)),bodies=listOf(CRecordEvidenceBodiesItem(body.digest,body.text())))),f.worker) }.problem)
        assertEquals("held",(f.state.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
    }
    @Test fun `WORK S05 draft can't reserve and lack approved scope can't be bypassed`() {
        val f=WorkFixture();val r=WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,f.taskKey),WTaskRevisionsPayload(f.task.copy(state="draft",unresolved=listOf("requires decision"))))
        f.state=WorkState.restore(f.project,f.state.records.values.filter { it.identity!=r.identity }+r,emptyList())
        assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> { f.reserve() }.problem)
    }
    @Test fun `WORK S35 restore budgets exhausted are unavailable not empty state`() {
        val f=WorkFixture()
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { WorkState.restore(f.project,f.state.records.values,emptyList(),WorkGraphBudget(records=1)) }.problem)
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { WorkState.restore(f.project,f.state.records.values,emptyList(),WorkGraphBudget(edges=1)) }.problem)
    }
    @Test fun `WORK S13 overflow in participating claim refuses entire reservation`() {
        val f=WorkFixture();val claim=WorkRecord(WorkRecordIdentity(WorkFamily.CLAIMS,f.resource),WClaimsPayload(WClaims(Long.MAX_VALUE,null,null,"free",emptyList(),f.source)))
        f.state=WorkState.restore(f.project,f.state.records.values+claim,emptyList());val old=f.state
        val op=CReserveAssignment(f.assignmentId,f.taskKey,f.manifest,f.profileKey,f.workspace,listOf(CReserveAssignmentResourcesItem(f.resource,Long.MAX_VALUE)),emptyList(),f.intentId,uid(23),emptyList(),f.executionIntent)
        assertEquals(WorkProblem.GENERATION_EXHAUSTED,assertFailsWith<WorkFault> { f.apply(CReserveAssignmentOperation(op)) }.problem)
        assertSame(old,f.state);assertTrue(f.state.records.keys.none { it.family==WorkFamily.ASSIGNMENTS })
    }
    @Test fun `WORK S04 S35 admitted Unicode body above64KiB still requires exact missing closure`() {
        val f=WorkFixture();f.active()
        val body=Utf8Content.fromText("🙂".repeat(18000));val ref=WorkBodyReference(body.digest,body.sizeBytes)
        val data=CRecordEvidenceData(listOf(f.source),emptyList(),"actual bounded probe","pure",ref,uid(230),"passed",emptyList(),emptyList(),emptyList(),ref)
        val op=CRecordEvidence(WorkKey.Id(f.project,uid(231)),data,listOf(CRecordEvidenceBodiesItem(body.digest,body.text())))
        val bounded=WorkCodec(commandLimits=JsonLimits(bytes=2*1024*1024,stringBytes=65536))
        val command=bounded.decodeCommand(bounded.encodeCommand(f.command(CRecordEvidenceOperation(op))))
        val effect=f.reducer.reduce(f.state,command,f.worker,f.facts)
        assertEquals(body,effect.after.bodies[body.digest]);assertEquals(1,effect.addedBodies.size)
        assertEquals(WorkProblem.MISSING_BODY,assertFailsWith<WorkFault> { f.apply(CRecordEvidenceOperation(op.copy(bodies=emptyList())),f.worker) }.problem)
        val changed=op.copy(bodies=listOf(CRecordEvidenceBodiesItem(body.digest,body.text()+"x")))
        assertEquals(WorkProblem.DIGEST_MISMATCH,assertFailsWith<WorkFault> { f.apply(CRecordEvidenceOperation(changed),f.worker) }.problem)
    }
    @Test fun `WORK S14 R1 remains immutable while R2 work continues no claim release`() {
        val f=WorkFixture();f.active();val ev=f.evidence();val r1=f.result(evidence=ev)
        val bytes=f.codec.encodeRecord(f.state.resolve(r1))
        f.result(120,ev)
        assertContentEquals(bytes,f.codec.encodeRecord(f.state.resolve(r1)))
        assertEquals("active",f.state.assignment(f.assignmentId).state)
        assertEquals("held",(f.state.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
        assertEquals(2,f.state.assignmentProjection(f.assignmentId).ownResults.size)
        val selector=WorkRead.Exact(f.project,r1.identity,r1.digest,null)
        assertEquals(f.report,f.state.readBody(WorkRead.Body(f.project,f.report.digest,selector)))
        assertFailsWith<WorkFault> { f.state.readExact(selector.copy(digest=Digest.of(byteArrayOf(99)))) }
        assertFailsWith<WorkFault> { f.state.readBody(WorkRead.Body(f.project,Digest.of(byteArrayOf(99)),selector)) }
    }
    @Test fun `WORK S26 S27 exact acceptance and actual integration stay separate unknown holds claims`() {
        val f=WorkFixture();f.active();val ev=f.evidence();val result=f.result(evidence=ev)
        val appKey=WorkKey.Id(f.project,uid(140))
        f.apply(CRecordEvidenceApplicabilityOperation(CRecordEvidenceApplicability(appKey,CRecordEvidenceApplicabilityData(result,
            listOf(CRecordEvidenceApplicabilityDataEvidenceItem(ev,"retained",emptyList(),"exact unchanged subject")),emptyList(),listOf(f.source)),emptyList())))
        val app=f.state.reference(WorkFamily.EVIDENCE_APPLICABILITY,appKey)
        val acceptance=CAcceptResult(WorkKey.Id(f.project,uid(141)),CAcceptResultData(result,f.taskKey,f.planKey,listOf(ev),emptyList(),emptyList(),app,listOf(f.criterion.id),listOf(uid(142)),f.reportRef),emptyList())
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { f.apply(CAcceptResultOperation(acceptance.copy(data=acceptance.data.copy(evidence=emptyList())))) }.problem)
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { f.apply(CAcceptResultOperation(acceptance),f.actor.copy(principalId=f.execution.principalId)) }.problem)
        f.apply(CAcceptResultOperation(acceptance))
        val accepted=f.state.reference(WorkFamily.ACCEPTANCES,acceptance.key)
        assertEquals(0,f.state.listSelectors(WorkFamily.INTEGRATIONS,null).size)
        val intent=WorkKey.Id(f.project,uid(143));val resource=WorkKey.Resource(f.project,uid(144))
        val target=WorkTargetObservation(f.snapshot.repositoryId,uid(142),f.snapshot.commit!!,f.snapshot.tree,null)
        val prepare=CPrepareIntegration(intent,result,accepted,target.repositoryId,target.targetId,target.commit,target.tree,null,listOf(CPrepareIntegrationResourcesItem(resource,null)),uid(145),emptyList())
        assertEquals(WorkProblem.STALE_TARGET,assertFailsWith<WorkFault> { f.apply(CPrepareIntegrationOperation(prepare)) }.problem)
        f.facts=f.facts.copy(targets=setOf(target));f.apply(CPrepareIntegrationOperation(prepare))
        val unknown=CObserveIntegration(WorkKey.Id(f.project,uid(146)),intent,0,"unknown",null,null,emptyList(),listOf(f.source),emptyList(),emptyList())
        f.facts=f.facts.copy(integrations=mapOf(intent to WorkIntegrationObservation(intent,uid(145),"unknown",null,null,emptyList(),listOf(f.source),emptyList())))
        f.apply(CObserveIntegrationOperation(unknown))
        assertEquals("held",(f.state.record(WorkFamily.CLAIMS,resource).payload as WClaimsPayload).value.state)
        val confirmed=unknown.copy(expectedGeneration=1,outcome="confirmed",resultingSnapshot=f.snapshot,applicability=app,evidence=listOf(ev))
        f.facts=f.facts.copy(integrations=mapOf(intent to WorkIntegrationObservation(intent,uid(145),"confirmed",f.snapshot,app,listOf(ev),listOf(f.source),emptyList())))
        f.apply(CObserveIntegrationOperation(confirmed))
        assertEquals("free",(f.state.record(WorkFamily.CLAIMS,resource).payload as WClaimsPayload).value.state)
        assertEquals(1,f.state.listSelectors(WorkFamily.INTEGRATIONS,f.taskKey.taskId).size)
        assertFailsWith<WorkFault> { f.apply(CObserveIntegrationOperation(confirmed)) }
    }
    @Test fun `WORK S15 source cut memory exact owned plan checkpoint freeze and later plan update`() {
        val f=WorkFixture();f.active();f.evidence()
        val cutId=uid(160);val locator=uid(161);val digest=Digest.of(byteArrayOf(1));val s=f.worker.assignment!!
        val cut=WSourceCuts(f.taskKey,s,f.execution.nativeHostId,f.execution.nativeSessionId,f.profileKey,1,digest,digest,locator,
            listOf(WSourceCutsNativeMessagesItem("msg_exact",digest)),emptyList(),emptyList())
        assertEquals(WorkProblem.INCOMPLETE_HANDOFF,assertFailsWith<WorkFault> { f.apply(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(f.project,cutId),cut,emptyList())),f.worker) }.problem)
        f.facts=f.facts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.execution.nativeHostId,f.execution.nativeSessionId,digest,digest,listOf("msg_exact" to digest))))
        f.apply(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(f.project,cutId),cut,emptyList())),f.worker)
        val plan=WorkKey.Revision(f.project,uid(162),0);val steps=listOf(WStep(uid(163),"remaining work","pending",listOf("keep exact source"),emptyList()))
        f.apply(CUpdatePlanOperation(CUpdatePlan(plan,f.taskKey,null,steps,"initial",null,emptyList(),s)),f.worker)
        val view=WorkKey.Revision(f.project,uid(164),0)
        f.apply(CRecordLoadedViewOperation(CRecordLoadedView(view,CRecordLoadedViewData(f.taskKey,f.manifest,s,null,emptyList(),emptyList()),emptyList())),f.worker)
        val memory=WorkKey.Revision(f.project,uid(165),0)
        f.apply(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(f.taskKey,s,null,cutId,emptyList(),emptyList(),emptyList()),emptyList())),f.worker)
        val checkpoint=WorkKey.Id(f.project,uid(166))
        val data=CRecordCheckpointData(f.taskKey,s,f.manifest,view,cutId,plan,0,memory,steps,emptyList(),emptyList(),f.reportRef,"unfinished",null,null,null)
        f.apply(CRecordCheckpointOperation(CRecordCheckpoint(checkpoint,data,f.state.assignment(f.assignmentId).generation,emptyList())),f.worker)
        val exact=f.state.reference(WorkFamily.CHECKPOINTS,checkpoint)
        f.apply(CUpdatePlanOperation(CUpdatePlan(plan,f.taskKey,0,emptyList(),"refine after frozen cut",exact,emptyList(),s)),f.worker)
        assertEquals(steps,(f.state.resolve(exact).payload as WCheckpointsPayload).value.planSteps)
        assertEquals(f.base,f.state.subjectProjection(s).base)
        assertEquals(5,f.state.subjectProjection(s).retainedOwnContinuation.size) // plan, view, cut, memory, checkpoint
        val result=f.result();val retro=(f.state.resolve(result).payload as WResultsPayload).value.retrospective
        val delivery=WorkKey.Id(f.project,uid(167))
        f.apply(CRecordDeliveryOperation(CRecordDelivery(delivery,s,cutId,f.taskKey,f.reportRef,null,"pending",emptyList(),emptyList())))
        val finished=checkpoint.copy(id=uid(168))
        f.apply(CRecordCheckpointOperation(CRecordCheckpoint(finished,data.copy(planGeneration=1,planSteps=emptyList(),handoff="finished",retrospective=retro,result=result,notification=delivery),f.state.assignment(f.assignmentId).generation,emptyList())),f.worker)
        val finishedRef=f.state.reference(WorkFamily.CHECKPOINTS,finished)
        f.facts=f.facts.copy(completedAssignments=setOf(s))
        val complete=CCompleteAssignment(f.assignmentId,f.state.assignment(f.assignmentId).generation,finishedRef,listOf(f.source),listOf(f.resource),emptyList())
        assertEquals(WorkProblem.INCOMPLETE_HANDOFF,assertFailsWith<WorkFault> { f.apply(CCompleteAssignmentOperation(complete)) }.problem)
        f.apply(CRecordDeliveryOperation(CRecordDelivery(delivery,s,cutId,f.taskKey,f.reportRef,0,"durable-delivered",listOf(f.source),emptyList())))
        val retainedH=f.state // independent pure completion and safe-rebind branches from this H
        f.apply(CCompleteAssignmentOperation(complete))
        assertEquals("completed",f.state.assignment(f.assignmentId).state)
        assertEquals("free",(f.state.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
        assertFailsWith<WorkFault> { f.evidence(180) }
        f.state=retainedH
        val stop=uid(181)
        f.apply(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignmentId,f.state.assignment(f.assignmentId).generation,stop,uid(182),"stop",finishedRef,emptyList())))
        f.facts=f.facts.copy(native=f.facts.native+(stop to WorkNativeObservation(s,stop,uid(182),"stop","confirmed",listOf(f.source),f.execution,true,"active")))
        f.apply(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignmentId,f.state.assignment(f.assignmentId).generation,stop,0,"confirmed",listOf(f.source),emptyList())))
        val recovery=WorkKey.Id(f.project,uid(183))
        f.apply(CRecordRecoveryOperation(CRecordRecovery(recovery,f.state.assignment(f.assignmentId).generation,WRecoveries(s,"bind-existing",listOf(f.source),emptyList(),listOf(f.resource),emptyList()),emptyList())))
        val before=f.codec.encodeRecord(f.state.resolve(result))
        f.apply(CRebindAssignmentOperation(CRebindAssignment(f.assignmentId,f.state.assignment(f.assignmentId).generation,f.taskKey,f.manifest,f.profileKey,
            finishedRef,f.state.reference(WorkFamily.RECOVERIES,recovery),uid(184),uid(185),emptyList(),f.executionIntent.copy(executionId=uid(186),nativeSessionId="ses_replacement"))))
        assertEquals(1,f.state.assignment(f.assignmentId).executionGeneration)
        assertEquals("reserved",f.state.assignment(f.assignmentId).state)
        assertNull(f.state.assignment(f.assignmentId).delegationId)
        assertContentEquals(before,f.codec.encodeRecord(f.state.resolve(result)))
        assertEquals(f.execution,f.state.binding(s).execution)
        assertEquals(listOf(result.identity),f.state.subjectProjection(s).retainedOwnResults.map { it.identity })
        assertEquals(result.digest,f.state.subjectProjection(s).retainedOwnResults.single().digest)
        assertEquals(WorkProblem.WRONG_ASSIGNMENT,assertFailsWith<WorkFault> { f.evidence(180) }.problem)
    }
    @Test fun `parent P6 controller continuation exact frontier own controller and stale observations`() {
        val f=WorkFixture();f.active();f.evidence()
        val locator=uid(170);val digest=Digest.of(byteArrayOf(1))
        f.facts=f.facts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.execution.nativeHostId,"ses_controller",digest,digest,emptyList())))
        val source=WNativeSourceBasis(f.execution.nativeHostId,"ses_controller",1,digest,digest,locator,emptyList(),emptyList())
        val frontier=WControllerContinuationsFrontierItem(f.taskKey,listOf(WControllerContinuationsFrontierItemAssignmentsItem(f.assignmentId,f.state.assignment(f.assignmentId).generation,0)),listOf(f.criterion.id))
        val v=CRecordControllerContinuation(WorkKey.ControllerContinuation(f.project,f.controller,uid(171)),WControllerContinuations(f.profileKey,null,source,listOf(frontier),emptyList(),emptyList(),emptyList(),f.reportRef),emptyList())
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> { f.apply(CRecordControllerContinuationOperation(v),f.actor.copy(controllerId=uid(999))) }.problem)
        val stale=v.copy(data=v.data.copy(frontier=listOf(frontier.copy(assignments=listOf(frontier.assignments.single().copy(recordGeneration=0))))))
        assertEquals(WorkProblem.STALE_GENERATION,assertFailsWith<WorkFault> { f.apply(CRecordControllerContinuationOperation(stale)) }.problem)
        f.apply(CRecordControllerContinuationOperation(v))
        assertEquals(1,f.state.listSelectors(WorkFamily.CONTROLLER_CONTINUATIONS,f.taskKey.taskId).size)
        assertEquals(0,f.state.listSelectors(WorkFamily.CONTROLLER_CONTINUATIONS,uid(999)).size)
    }
    @Test fun `WORK S21 grouped reviewer checks every producer and exact ordered bound subjects`() {
        val f=WorkFixture();f.active();val ev=f.evidence();val r1=f.result(evidence=ev);val r2=f.result(120,ev)
        val group=listOf(r1,r2);val task=WorkKey.TaskRevision(f.project,uid(200),0);val planKey=WorkKey.Revision(f.project,uid(201),0)
        val data=f.task.copy(kind="verification",verificationPlan=planKey,dependencies=group.map { WDependency(f.taskKey,"implemented",it,emptyList(),"assess immutable group") })
        val plan=f.plan.copy(task=task)
        f.apply(CCreateTaskRevisionOperation(CCreateTaskRevision(task,data,CCreateTaskRevisionNewVerificationPlan(planKey,plan),emptyList())))
        val manifestKey=WorkKey.Id(f.project,uid(202))
        f.apply(CPinInputManifestOperation(CPinInputManifest(manifestKey,WInputManifests(task,f.base,f.appKey,listOf(WManifestItem(f.source,true,"required input",emptyList())),f.source,emptyList()),emptyList())))
        val manifest=f.state.reference(WorkFamily.INPUT_MANIFESTS,manifestKey)
        f.facts=f.facts.copy(verifiedPackets=f.facts.verifiedPackets+manifest)
        val assignment=uid(203);val workspace=uid(204);val intent=uid(205)
        val intended=f.executionIntent.copy(executionId=uid(206),template="reviewer-v1",nativeSessionId="ses_reviewer")
        val reserve=CReserveAssignment(assignment,task,manifest,f.profileKey,workspace,listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(f.project,uid(207)),null)),group,intent,uid(208),emptyList(),intended)
        assertEquals(WorkProblem.INVALID_REFERENCE,assertFailsWith<WorkFault> { f.apply(CReserveAssignmentOperation(reserve.copy(intendedExecution=intended.copy(executionId=f.execution.executionId)))) }.problem)
        f.apply(CReserveAssignmentOperation(reserve))
        f.state=f.reducer.bindRegistration(f.state,WorkRegistrationBinding(assignment,0,0,task,intended,group,uid(209))).after
        val execution=WExecution(intended.executionId,uid(210),uid(211),intended.nativeHostId,intended.nativeSessionId,workspace)
        val subject=WAssignmentSubject(assignment,0)
        f.facts=f.facts.copy(registeredExecutions=f.facts.registeredExecutions+(uid(209) to execution),native=f.facts.native+(intent to WorkNativeObservation(subject,intent,uid(208),"prepare","confirmed",listOf(f.source),execution,true,"reserved")))
        f.apply(CBindAssignmentOperation(CBindAssignment(assignment,1,intent,0,execution.nativeHostId,execution.nativeSessionId,workspace,listOf(f.source),emptyList())))
        val start=uid(212)
        f.apply(CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,start,uid(213),"start",null,emptyList())))
        f.facts=f.facts.copy(native=f.facts.native+(start to WorkNativeObservation(subject,start,uid(213),"start","confirmed",listOf(f.source),execution,false,"bound")))
        f.apply(CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,start,0,"confirmed",listOf(f.source),emptyList())))
        val reviewer=WorkExecutionInput(execution.principalId,execution.credentialId,null,subject,execution)
        val report=CSubmitVerificationReport(WorkKey.Id(f.project,uid(214)),CSubmitVerificationReportData(task,subject,group,planKey,plan.gates.map { it.id },f.source,listOf(f.source),emptyList(),listOf(ev),f.reportRef,emptyList()),emptyList())
        assertEquals(WorkProblem.INVALID_SUBJECT,assertFailsWith<WorkFault> { f.apply(CSubmitVerificationReportOperation(report.copy(data=report.data.copy(subjects=group.reversed()))),reviewer) }.problem)
        f.apply(CSubmitVerificationReportOperation(report),reviewer)
        assertEquals(group,(f.state.record(WorkFamily.VERIFICATION_REPORTS,report.key).payload as WVerificationReportsPayload).value.subjects)
        assertEquals("active",f.state.assignment(f.assignmentId).state)
        val finding=WFinding(uid(215),"violation",f.source,"exact case","required behavior","observed mismatch",listOf(ev),f.plan.gates.map { it.id },listOf((r1.identity.key as WorkKey.Id).id),true)
        val blocked=report.copy(key=WorkKey.Id(f.project,uid(216)),data=report.data.copy(findings=listOf(finding)))
        f.apply(CSubmitVerificationReportOperation(blocked),reviewer)
        val appKey=WorkKey.Id(f.project,uid(217))
        f.apply(CRecordEvidenceApplicabilityOperation(CRecordEvidenceApplicability(appKey,CRecordEvidenceApplicabilityData(r1,listOf(CRecordEvidenceApplicabilityDataEvidenceItem(ev,"retained",emptyList(),"exact input")),emptyList(),listOf(f.source)),emptyList())))
        val acceptance=CAcceptResult(WorkKey.Id(f.project,uid(218)),CAcceptResultData(r1,f.taskKey,f.planKey,listOf(ev),emptyList(),emptyList(),f.state.reference(WorkFamily.EVIDENCE_APPLICABILITY,appKey),listOf(f.criterion.id),emptyList(),f.reportRef),emptyList())
        // Omitting the known blocking report from the request cannot hide its finding.
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { f.apply(CAcceptResultOperation(acceptance)) }.problem)
        val disposition=WorkKey.Id(f.project,uid(219))
        f.apply(CRecordDispositionOperation(CRecordDisposition(disposition,CRecordDispositionData(r1,f.state.reference(WorkFamily.VERIFICATION_REPORTS,blocked.key),finding.id,f.plan.gates.map { it.id },"not-applicable",listOf(f.source),null,null),emptyList())))
        f.apply(CAcceptResultOperation(acceptance.copy(data=acceptance.data.copy(dispositions=listOf(f.state.reference(WorkFamily.DISPOSITIONS,disposition))))))
        assertEquals(1,f.state.listSelectors(WorkFamily.ACCEPTANCES,f.taskKey.taskId).size)
    }
}
