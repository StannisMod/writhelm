// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

/** Authentication/current AUTH2 admission is performed by APP before invoking this pure reducer.
 * This input is service-derived, never decoded from a command or treated as a permission database. */
internal data class WorkExecutionInput(val principalId: UUID,val credentialId: UUID,
    val controllerId: UUID?,val assignment: WAssignmentSubject?,val execution: WExecution?)
internal data class WorkNativeObservation(val assignment: WAssignmentSubject,val intentId: UUID,
    val correlationId: UUID,val operation: String,val outcome: String,val sources: List<WSource>,
    val execution: WExecution?,val writersFenced: Boolean,val priorSafeState: String)
internal data class WorkIntegrationObservation(val intent: WorkKey.Id,val correlationId: UUID,val outcome: String,
    val resultingSnapshot: WSnapshot?,val applicability: WorkReference?,val evidence: List<WorkReference>,
    val observations: List<WSource>,val pending: List<WPending>)
internal data class WorkRetainedCut(val nativeHostId: UUID,val nativeSessionId: String,
    val renderedRequestDigest: Digest,val packetDigest: Digest,val messages: List<Pair<String,Digest>>)
internal data class WorkTargetObservation(val repositoryId: UUID,val targetId: UUID,val commit: WorkGitOid,
    val tree: WorkGitOid,val dirtyDigest: Digest?)
internal data class WorkSubmittedDiff(val task: WorkKey.TaskRevision,val artifacts: List<WSnapshot>,val changes: WorkBodyReference)
/** Qualified adapters/core supply exact facts at the same captured H. Missing entries refuse.
 * No default always-allow callback, model assertion or native execution is implemented here. */
internal data class WorkExternalFacts(val knowledgeSources: Set<KnowledgeReference>,
    val approvedDecisions: Set<KnowledgeReference>,val retainedSnapshots: Set<WSnapshot>,
    val native: Map<UUID,WorkNativeObservation>,val sourceCuts: Map<UUID,WorkRetainedCut>,
    val targets: Set<WorkTargetObservation>,val completedAssignments: Set<WAssignmentSubject>,
    val isolatedAssignments: Set<WAssignmentSubject>,val verifiedPackets: Set<WorkReference>,
    val registeredExecutions: Map<UUID,WExecution>,val integrations: Map<WorkKey.Id,WorkIntegrationObservation>,
    val submittedDiffs: Set<WorkSubmittedDiff>,val model2: Model2Facts=Model2Facts.empty())
internal data class WorkEffect(val before: WorkState,val after: WorkState,val result: WorkRecordIdentity,
    val changedRecords: List<WorkRecord>,val addedBodies: List<Utf8Content>)

internal class WorkReducer {
    fun reduce(h: WorkState,original: WorkCommand,actor: WorkExecutionInput,facts: WorkExternalFacts): WorkEffect {
        workRequire(h.modelVersion==2,WorkProblem.NOT_READY) // legacy command3 is not a model3 writer
        val codec=WorkCodec();val command=codec.decodeCommand(codec.encodeCommand(original))
        workRequire(command.projectId==h.projectId,WorkProblem.FOREIGN_REFERENCE)
        val tx=WorkTransaction(h,actor,facts,command.requestKey,codec.fingerprint(command))
        try {
            val result=tx.apply(command.operation)
            return tx.finish(result,command.operation)
        } catch(e: WorkFault) { throw workRefusal(command.operation.name,e)
        } catch(e: CodecFault) {
            // H is already validated. A new candidate's missing/cyclic/foreign counterpart is a
            // command refusal, not evidence that the unchanged published H is corrupt.
            if(e.problem==CodecProblem.INVALID_GRAPH)throw workRefusal(command.operation.name,WorkFault(WorkProblem.INVALID_REFERENCE))
            throw e
        }
    }
    /** Internal enrollment effect, composed atomically with AUTH's six records and one receipt by APP.
     * AUTH must validate its candidate first; this checks exact reserved-work equality independently. */
    fun bindRegistration(h: WorkState,registration: WorkRegistrationBinding): WorkEffect {
        workRequire(h.modelVersion==2,WorkProblem.NOT_READY)
        val a=h.assignment(registration.assignmentId)
        workRequire(a.generation==registration.expectedGeneration,WorkProblem.STALE_GENERATION)
        workRequire(a.state=="reserved" && a.delegationId==null && a.execution==null && a.binding==null,WorkProblem.INVALID_BINDING)
        workRequire(a.task==registration.task && a.executionGeneration==registration.executionGeneration &&
            a.intendedExecution==registration.intendedExecution && a.reviewSubjects==registration.subjectResults,
            WorkProblem.INVALID_BINDING)
        val p=WAssignmentsPayload(a.copy(generation=nextGeneration(a.generation),delegationId=registration.delegationId))
        val key=WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(h.projectId,registration.assignmentId))
        val changed=WorkRecord(key,p)
        val after=h.derive(listOf(changed),emptyList())
        return WorkEffect(h,after,key,listOf(after.records.getValue(key)),emptyList())
    }
}
internal data class WorkRegistrationBinding(val assignmentId: UUID,val expectedGeneration: Long,
    val executionGeneration: Long,val task: WorkKey.TaskRevision,val intendedExecution: WExecutionIntent,
    val subjectResults: List<WorkReference>,val delegationId: UUID)

internal class WorkTransaction(val h: WorkState,val actor: WorkExecutionInput,val facts: WorkExternalFacts,
    val requestKey: UUID,val fingerprint: Digest) {
    private val changes=LinkedHashMap<WorkRecordIdentity,WorkRecord>()
    internal val project get()=h.projectId
    private val codec=WorkCodec()
    private fun id(f: WorkFamily,k: WorkKey)=WorkRecordIdentity(f,k)
    internal fun key(i: UUID)=WorkKey.Id(project,i)
    private fun get(f: WorkFamily,k: WorkKey)=changes[id(f,k)] ?: h.record(f,k)
    internal fun put(k: WorkKey,p: WorkPayload,expected: Long?=null): WorkRecordIdentity {
        val identity=id(payloadFamily(p),k);val old=h.records[identity]
        if(identity.family.mutable) {
            workRequire(recordGeneration(old?.payload ?: p)==(if(old==null)0L else expected),WorkProblem.STALE_GENERATION)
            workRequire((old==null)==(expected==null),WorkProblem.STALE_GENERATION)
            workRequire(recordGeneration(p)==(if(old==null)0L else nextGeneration(expected!!)),WorkProblem.STALE_GENERATION)
        } else workRequire(old==null,WorkProblem.DUPLICATE_ID)
        workRequire(identity !in changes,WorkProblem.DUPLICATE_ID)
        codec.encodeRecord(WorkRecord(identity,p))
        changes[identity]=WorkRecord(identity,p);return identity
    }
    internal fun ref(identity: WorkRecordIdentity): WorkReference {
        val r=changes[identity] ?: h.records[identity] ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        return WorkReference(identity,codec.digest(r))
    }
    internal fun resolve(r: WorkReference,f: WorkFamily): WorkRecord {
        workRequire(r.identity.family==f,WorkProblem.INVALID_REFERENCE);return h.resolve(r)
    }
    internal fun assignment(i: UUID,expected: Long): WAssignments = h.assignment(i).also {
        workRequire(it.generation==expected,WorkProblem.STALE_GENERATION)
        workRequire(actor.controllerId==it.intendedExecution.controllerId,WorkProblem.WRONG_ASSIGNMENT)
    }
    private fun subject(i: UUID,a: WAssignments)=WAssignmentSubject(i,a.executionGeneration)
    internal fun changeAssignment(i: UUID,a: WAssignments)=put(key(i),WAssignmentsPayload(a.copy(generation=nextGeneration(a.generation))),a.generation)
    private fun own(s: WAssignmentSubject,activeOnly: Boolean=false,template: String?=null): WAssignments {
        val a=h.assignment(s.assignmentId)
        workRequire(actor.assignment==s && a.executionGeneration==s.generation && actor.execution==a.execution && a.execution!=null,
            WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.state in (if(activeOnly)setOf("active")else setOf("active","pausing","paused","stopping","stopped")),WorkProblem.INVALID_TRANSITION)
        if(template!=null)workRequire(a.intendedExecution.template==template,WorkProblem.INVALID_SUBJECT)
        return a
    }
    internal fun source(s: WSource) {
        when(s) {
            is WSourceVariant0Value -> workRequire(s.value.reference in facts.knowledgeSources,WorkProblem.BLOCKED_INPUT)
            is WSourceVariant1Value -> workRequire(s.value.artifact in facts.retainedSnapshots,WorkProblem.INVALID_SUBJECT)
            is WSourceVariant2Value -> {
                val reference=s.value.reference
                val coupled=changes[reference.identity]
                if(coupled==null)h.resolve(reference)
                else workRequire(codec.digest(coupled)==reference.digest,WorkProblem.DIGEST_MISMATCH)
            }
        }
    }
    private fun decision(d: KnowledgeReference)=workRequire(d in facts.approvedDecisions,WorkProblem.BLOCKED_INPUT)
    internal fun taskReady(task: WorkKey.TaskRevision,manifest: WorkReference,profile: WorkKey.Profile) {
        val t=h.task(task)
        workRequire(t.state=="executable" && t.unresolved.isEmpty() && t.executionProfile==profile,WorkProblem.NOT_READY)
        workRequire(t.kind !in setOf("implementation","remediation") || t.approvedDesign.isNotEmpty(),WorkProblem.BLOCKED_INPUT)
        t.approvedDesign.forEach(::decision)
        val p=(h.record(WorkFamily.PROFILES,profile).payload as WProfilesPayload).value
        workRequire(p.qualification=="qualified" && p.qualificationEvidence.isNotEmpty(),WorkProblem.NOT_READY)
        p.qualificationEvidence.forEach(::source)
        workRequire(p.outputReserveTokens+p.toolReserveTokens+p.compactionReserveTokens<p.contextTokens,WorkProblem.NOT_READY)
        val m=(resolve(manifest,WorkFamily.INPUT_MANIFESTS).payload as WInputManifestsPayload).value
        workRequire(m.task==task && m.base==t.base && m.knownGaps.isEmpty() && manifest in facts.verifiedPackets,WorkProblem.BLOCKED_INPUT)
        m.items.forEach { source(it.source) };source(m.declaredClosureAssessment)
        val app=(h.record(WorkFamily.APPLICABILITY,m.applicability).payload as WApplicabilityPayload).value
        workRequire(app.entries.none { it.state!="applicable" },WorkProblem.BLOCKED_INPUT)
        t.dependencies.forEach { d ->
            val selected=d.selectedResult ?: throw WorkFault(WorkProblem.NOT_READY)
            val r=(resolve(selected,WorkFamily.RESULTS).payload as WResultsPayload).value
            workRequire(r.task==d.producer,WorkProblem.NOT_READY)
            fun has(f: WorkFamily,predicate: (WorkPayload)->Boolean)=h.records.values.any { it.identity.family==f && predicate(it.payload) }
            val ready=when(d.readiness) {
                "implemented" -> true
                "cheap-checked" -> r.evidence.any { e -> val v=(resolve(e,WorkFamily.EVIDENCE).payload as WEvidencePayload).value
                    v.outcome=="passed" && v.boundary in setOf("pure","component","joint") }
                "reviewed" -> has(WorkFamily.VERIFICATION_REPORTS) { (it as WVerificationReportsPayload).value.subjects.contains(selected) }
                "accepted" -> has(WorkFamily.ACCEPTANCES) { (it as WAcceptancesPayload).value.result==selected }
                "integrated" -> has(WorkFamily.INTEGRATIONS) { (it as WIntegrationsPayload).value.result==selected }
                else -> false
            };workRequire(ready,WorkProblem.NOT_READY)
        }
    }
    internal fun acquire(resources: List<Pair<WorkKey.Resource,Long?>>,holder: WClaimsHolder,workspace: UUID?,basis: WSource) {
        workRequire(resources.map { it.first }.distinct().size==resources.size,WorkProblem.CLAIM_CONFLICT)
        val occupied=h.records.values.mapNotNull { r -> (r.payload as? WClaimsPayload)?.value?.let { r.identity.key as WorkKey.Resource to it } }
        resources.forEach { (resource,expected) ->
            val old=h.records[id(WorkFamily.CLAIMS,resource)]?.payload as? WClaimsPayload
            workRequire(old?.value?.generation==expected,WorkProblem.STALE_GENERATION)
            workRequire(old==null || old.value.state=="free",WorkProblem.CLAIM_CONFLICT)
            occupied.forEach { (other,c) -> if(c.state!="free")workRequire(other!=resource && resource !in c.incompatible && other !in (old?.value?.incompatible ?: emptyList()),WorkProblem.CLAIM_CONFLICT) }
            put(resource,WClaimsPayload(WClaims(if(old==null)0 else nextGeneration(old.value.generation),holder,workspace,"held",old?.value?.incompatible ?: emptyList(),basis)),expected)
        }
    }
    private fun intent(i: UUID,a: WAssignments,expected: Long): WNativeIntents {
        val v=(h.record(WorkFamily.NATIVE_INTENTS,key(i)).payload as WNativeIntentsPayload).value
        workRequire(a.currentIntent==key(i) && v.generation==expected,WorkProblem.STALE_GENERATION)
        workRequire(v.assignment.generation==a.executionGeneration,WorkProblem.INVALID_BINDING);return v
    }
    private fun proof(i: UUID,v: WNativeIntents,outcome: String,sources: List<WSource>): WorkNativeObservation {
        val p=facts.native[i] ?: throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
        workRequire(p.intentId==i && p.assignment==v.assignment && p.correlationId==v.correlationId && p.operation==v.operation && p.outcome==outcome && p.sources==sources,WorkProblem.INVALID_BINDING)
        sources.forEach(::source);return p
    }
    fun apply(op: WorkOperation): WorkRecordIdentity = when(op) {
        is CReplaceIsolatedAssignmentOperation, is CRecordConditionOperation, is CConfirmConditionOperation,
        is CPrepareContinuationOperation, is CObserveContinuationOperation -> Model2Transaction(this).apply(op)
        is CCreateTaskRevisionOperation -> op.value.let { v ->
            if(v.data.state=="executable" && v.data.kind in setOf("implementation","remediation")) {
                workRequire(v.data.approvedDesign.isNotEmpty(),WorkProblem.BLOCKED_INPUT);v.data.approvedDesign.forEach(::decision)
            }
            v.newVerificationPlan?.let { p ->workRequire(v.data.verificationPlan==p.key && p.data.task==v.key,WorkProblem.INVALID_REFERENCE)
                put(p.key,WVerificationPlansPayload(p.data)) }
            put(v.key,WTaskRevisionsPayload(v.data))
        }
        is CSetTaskHeadOperation -> op.value.let { v ->put(v.key,WTaskHeadsPayload(WTaskHeads(v.expectedGeneration?.let(::nextGeneration) ?: 0,v.title,v.draft,v.executable)),v.expectedGeneration) }
        is CCreateProfileOperation -> put(op.value.key,WProfilesPayload(op.value.data))
        is CPinInputManifestOperation -> put(op.value.key,WInputManifestsPayload(op.value.data))
        is CReviseApplicabilityOperation -> put(op.value.key,WApplicabilityPayload(op.value.data))
        is CReviseVerificationPlanOperation -> op.value.let { v ->
            val previous=v.data.previous ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val old=h.governingVerificationPlan(v.data.task,previous)
            v.data.authorizedChanges.forEach(::decision)
            if(!verificationPlanPreserves(old,v.data))workRequire(v.data.authorizedChanges.isNotEmpty(),WorkProblem.BLOCKED_INPUT)
            put(v.key,WVerificationPlansPayload(v.data))
        }
        is CUpdatePlanOperation -> op.value.let { v ->
            if(actor.assignment!=null) { workRequire(v.assignment==actor.assignment,WorkProblem.WRONG_ASSIGNMENT);own(v.assignment!!) }
            else workRequire(v.assignment==null,WorkProblem.WRONG_ASSIGNMENT)
            val old=h.records[id(WorkFamily.PLANS,v.key)]?.payload as? WPlansPayload
            old?.let { workRequire(it.value.task==v.task && it.value.assignment==v.assignment,WorkProblem.WRONG_ASSIGNMENT) }
            put(v.key,WPlansPayload(WPlans(v.task,v.expectedGeneration?.let(::nextGeneration) ?: 0,v.steps,v.reason,v.previousCheckpoint,v.assignment)),v.expectedGeneration)
        }
        is CReserveAssignmentOperation -> reserve(op.value)
        is CBindAssignmentOperation -> bind(op.value)
        is CRequestNativeTransitionOperation -> request(op.value)
        is CObserveNativeTransitionOperation -> observe(op.value)
        is CRecordRecoveryOperation -> recovery(op.value)
        is CRebindAssignmentOperation -> rebind(op.value)
        is CCompleteAssignmentOperation -> complete(op.value)
        is CRecordLoadedViewOperation -> op.value.let { v ->own(v.data.assignment);put(v.key,WLoadedViewsPayload(decodeWLoadedViews(encodeCRecordLoadedViewData(v.data)))) }
        is CRecordMemoryOperation -> op.value.let { v ->own(v.data.assignment);put(v.key,WMemoryPayload(decodeWMemory(encodeCRecordMemoryData(v.data)))) }
        is CRecordRetrospectiveOperation -> op.value.let { v ->own(v.data.assignment);v.data.observations.forEach { it.normativeDecision?.let(::decision) }
            put(v.key,WRetrospectivesPayload(decodeWRetrospectives(encodeCRecordRetrospectiveData(v.data)))) }
        is CRecordCheckpointOperation -> checkpoint(op.value)
        is CSubmitResultOperation -> op.value.let { v ->val a=own(v.data.assignment,true,"executor-v1")
            workRequire(v.data.task==a.task && v.data.manifest==a.manifest,WorkProblem.WRONG_ASSIGNMENT)
            v.data.artifacts.forEach { workRequire(it in facts.retainedSnapshots,WorkProblem.INVALID_SUBJECT) }
            workRequire(WorkSubmittedDiff(v.data.task,v.data.artifacts,v.data.changes) in facts.submittedDiffs,WorkProblem.INVALID_SUBJECT)
            val d=v.data;put(v.key,WResultsPayload(WResults(d.task,d.assignment,a.execution!!,d.manifest,d.artifacts,d.changes,d.claimedCriteria,d.evidence,d.retrospective,d.pending))) }
        is CSubmitVerificationReportOperation -> op.value.let { v ->val a=own(v.data.assignment,true,"reviewer-v1");val d=v.data
            workRequire(d.task==a.task && d.subjects==a.reviewSubjects,WorkProblem.INVALID_SUBJECT)
            d.subjects.forEach { workRequire((resolve(it,WorkFamily.RESULTS).payload as WResultsPayload).value.producer.executionId!=a.execution!!.executionId,WorkProblem.INVALID_SUBJECT) }
            source(d.freshContextBasis);d.readOnlySnapshotBasis.forEach(::source)
            put(v.key,WVerificationReportsPayload(WVerificationReports(d.task,d.assignment,a.execution!!,d.subjects,d.plan,d.gateIds,d.freshContextBasis,d.readOnlySnapshotBasis,d.findings,d.evidence,d.report,d.limits))) }
        is CRecordEvidenceOperation -> op.value.let { v ->
            val s=actor.assignment ?: throw WorkFault(WorkProblem.WRONG_ASSIGNMENT);own(s,true)
            v.data.subjects.forEach(::source)
            // Scope is the actual AUTH2 admitted source closure; raw artifact sources still need retained custody.
            put(v.key,WEvidencePayload(decodeWEvidence(encodeCRecordEvidenceData(v.data)))) }
        is CRecordDeliveryOperation -> delivery(op.value)
        is CRecordSourceCutOperation -> op.value.let { v ->own(v.data.assignment);verifyCut(v.data.retainedLocatorId,
            v.data.nativeHostId,v.data.nativeSessionId,v.data.renderedRequestDigest,v.data.packetDigest,v.data.nativeMessages.map { it.messageId to it.digest })
            put(v.key,WSourceCutsPayload(v.data)) }
        is CRecordControllerContinuationOperation -> controllerContinuation(op.value)
        is CRecordEvidenceApplicabilityOperation -> op.value.let { v ->val d=v.data
            put(v.key,WEvidenceApplicabilityPayload(WEvidenceApplicability(d.result,d.evidence.map { WEvidenceApplicabilityEvidenceItem(it.evidence,it.state,it.significantInputComparison,it.rationale) },d.heldGateIds,d.basis,actor.principalId))) }
        is CRecordDispositionOperation -> disposition(op.value)
        is CAcceptResultOperation -> acceptance(op.value)
        is CPrepareIntegrationOperation -> prepareIntegration(op.value)
        is CObserveIntegrationOperation -> observeIntegration(op.value)
    }
    private fun reserve(v: CReserveAssignment): WorkRecordIdentity {
        workRequire(actor.controllerId==v.intendedExecution.controllerId,WorkProblem.INVALID_BINDING)
        taskReady(v.task,v.manifest,v.profile)
        workRequire(h.records.values.none { r -> (r.payload as? WAssignmentsPayload)?.value?.let { a ->
            a.state !in setOf("completed","replaced") && (a.workspaceId==v.workspaceId || a.task.taskId==v.task.taskId && a.intendedExecution.template=="executor-v1" && v.intendedExecution.template=="executor-v1") }==true },WorkProblem.CLAIM_CONFLICT)
        workRequire((v.intendedExecution.template=="executor-v1")==v.reviewSubjects.isEmpty(),WorkProblem.INVALID_SUBJECT)
        v.reviewSubjects.forEach { r ->
            val result=(resolve(r,WorkFamily.RESULTS).payload as WResultsPayload).value
            workRequire(result.producer.executionId!=v.intendedExecution.executionId,WorkProblem.INVALID_SUBJECT)
            workRequire(h.task(v.task).dependencies.any { it.selectedResult==r },WorkProblem.INVALID_SUBJECT)
        }
        val subject=WAssignmentSubject(v.assignmentId,0)
        val basis=WSourceVariant2Value(WSourceVariant2("work",v.manifest))
        acquire(v.resources.map { it.resource to it.expectedGeneration },WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",subject)),v.workspaceId,basis)
        put(key(v.intentId),WNativeIntentsPayload(WNativeIntents(subject,0,v.correlationId,"prepare",v.intendedExecution.nativeSessionId,v.workspaceId,"pending",emptyList(),null)))
        return put(key(v.assignmentId),WAssignmentsPayload(WAssignments(v.task,v.manifest,v.profile,0,0,null,null,v.workspaceId,
            v.resources.map { it.resource },v.reviewSubjects,"reserved",key(v.intentId),null,null,v.intendedExecution,null)))
    }
    private fun bind(v: CBindAssignment): WorkRecordIdentity {
        val a=assignment(v.assignmentId,v.expectedGeneration)
        workRequire(a.state=="reserved" && a.delegationId!=null,WorkProblem.INVALID_BINDING)
        val i=intent(v.intentId,a,v.expectedIntentGeneration)
        workRequire(i.operation=="prepare" && i.outcome=="pending",WorkProblem.INVALID_TRANSITION)
        val p=proof(v.intentId,i,"confirmed",v.observations)
        val e=facts.registeredExecutions[a.delegationId] ?: throw WorkFault(WorkProblem.INVALID_BINDING)
        workRequire(p.execution==e,WorkProblem.INVALID_BINDING)
        val intended=a.intendedExecution
        workRequire(e.executionId==intended.executionId && e.nativeHostId==intended.nativeHostId && e.nativeSessionId==intended.nativeSessionId &&
            v.nativeHostId==e.nativeHostId && v.nativeSessionId==e.nativeSessionId && e.workspaceId==a.workspaceId && v.workspaceId==a.workspaceId,WorkProblem.INVALID_BINDING)
        val b=put(WorkKey.AssignmentBinding(project,v.assignmentId,a.executionGeneration),WAssignmentBindingsPayload(WAssignmentBindings(a.task,a.manifest,a.profile,e,a.workspaceId,a.requiredResources,a.intendedExecution,a.delegationId!!,a.reviewSubjects)))
        put(key(v.intentId),WNativeIntentsPayload(i.copy(generation=nextGeneration(i.generation),outcome="confirmed",observations=v.observations)),i.generation)
        return changeAssignment(v.assignmentId,a.copy(execution=e,binding=ref(b),state="bound",currentIntent=null))
    }
    private fun request(v: CRequestNativeTransition): WorkRecordIdentity {
        val a=assignment(v.assignmentId,v.expectedGeneration)
        workRequire(a.currentIntent==null && a.binding!=null,WorkProblem.INVALID_TRANSITION)
        val allowed=when(v.transition) { "start"->setOf("bound");"pause"->setOf("active");"stop"->setOf("bound","active","paused");"resume"->setOf("paused");else->emptySet() }
        workRequire(a.state in allowed,WorkProblem.INVALID_TRANSITION)
        if(v.transition in setOf("start","resume")) {
            taskReady(a.task,a.manifest,a.profile)
            Model2Inspector(h).requireActivation(subject(v.assignmentId,a),false)
        }
        if(v.transition=="resume")workRequire(v.checkpoint==a.checkpoint && v.checkpoint!=null,WorkProblem.INCOMPLETE_HANDOFF)
        val result=put(key(v.intentId),WNativeIntentsPayload(WNativeIntents(subject(v.assignmentId,a),0,v.correlationId,v.transition,a.execution!!.nativeSessionId,a.workspaceId,"pending",emptyList(),null)))
        changeAssignment(v.assignmentId,a.copy(currentIntent=key(v.intentId),state=when(v.transition) { "pause"->"pausing";"stop"->"stopping";else->a.state }))
        return result
    }
    private fun observe(v: CObserveNativeTransition): WorkRecordIdentity {
        val a=assignment(v.assignmentId,v.expectedGeneration);val i=intent(v.intentId,a,v.expectedIntentGeneration)
        workRequire(a.state !in setOf("completed","replaced"),WorkProblem.INVALID_TRANSITION)
        workRequire(i.outcome in setOf("pending","unknown") && i.operation!="prepare",WorkProblem.INVALID_TRANSITION)
        val p=proof(v.intentId,i,v.outcome,v.observations)
        if(v.outcome=="confirmed" && i.operation in setOf("start","resume"))
            Model2Inspector(h).requireActivation(subject(v.assignmentId,a),true)
        if(v.outcome=="confirmed" && i.operation in setOf("pause","stop"))workRequire(p.writersFenced,WorkProblem.NATIVE_UNKNOWN)
        val state=when(v.outcome) {
            "unknown" -> "unknown"
            "confirmed" -> when(i.operation) { "start","resume"->"active";"pause"->"paused";"stop"->"stopped";else->throw WorkFault(WorkProblem.INVALID_TRANSITION) }
            else -> {
                val prior=when(i.operation) { "start"->setOf("bound");"resume"->setOf("paused");"pause"->setOf("active");"stop"->setOf("bound","active","paused");else->emptySet() }
                workRequire(p.priorSafeState in prior,WorkProblem.INVALID_TRANSITION);p.priorSafeState
            }
        }
        val result=put(key(v.intentId),WNativeIntentsPayload(i.copy(generation=nextGeneration(i.generation),outcome=v.outcome,observations=v.observations)),i.generation)
        changeAssignment(v.assignmentId,a.copy(state=state,currentIntent=if(v.outcome=="unknown")key(v.intentId)else null))
        return result
    }
    private fun recovery(v: CRecordRecovery): WorkRecordIdentity {
        val a=assignment(v.data.assignment.assignmentId,v.expectedGeneration)
        workRequire(v.data.assignment==subject(v.data.assignment.assignmentId,a),WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.state in setOf("unknown","quarantined","reserved","stopped"),WorkProblem.INVALID_TRANSITION)
        v.data.basis.forEach(::source)
        if(v.data.decision=="revoked-isolated")workRequire(v.data.assignment in facts.isolatedAssignments,WorkProblem.NATIVE_UNKNOWN)
        if(v.data.decision=="not-started")workRequire(v.data.assignment in facts.completedAssignments,WorkProblem.NATIVE_UNKNOWN)
        val isolated=v.data.isolatedResources.toSet();val retained=v.data.retainedClaims.toSet()
        workRequire(isolated.intersect(retained).isEmpty() && isolated+retained==a.requiredResources.toSet(),WorkProblem.INVALID_SUBJECT)
        if(isolated.isNotEmpty())workRequire(v.data.decision=="revoked-isolated",WorkProblem.INVALID_SUBJECT)
        v.data.isolatedResources.forEach { resource ->
            val c=(h.record(WorkFamily.CLAIMS,resource).payload as WClaimsPayload).value
            workRequire((c.holder as? WClaimsHolderVariant0Value)?.value?.subject==v.data.assignment,WorkProblem.CLAIM_CONFLICT)
            put(resource,WClaimsPayload(c.copy(generation=nextGeneration(c.generation),state="isolated",basis=v.data.basis.first())),c.generation)
        }
        val result=put(v.key,WRecoveriesPayload(v.data))
        changeAssignment(v.data.assignment.assignmentId,a.copy(recovery=ref(result),state=if(v.data.decision=="revoked-isolated")"quarantined" else a.state))
        return result
    }
    private fun rebind(v: CRebindAssignment): WorkRecordIdentity {
        val a=assignment(v.assignmentId,v.expectedGeneration);val s=subject(v.assignmentId,a)
        workRequire(s in facts.completedAssignments || s in facts.isolatedAssignments,WorkProblem.NATIVE_UNKNOWN)
        // This wire operation retains workspace/resources. Isolation alone cannot authorize reuse of
        // that same work area; an isolated replacement in a new area needs a separate assignment.
        workRequire(s in facts.completedAssignments,WorkProblem.NATIVE_UNKNOWN)
        workRequire(a.checkpoint==v.checkpoint && a.recovery==v.recovery,WorkProblem.INVALID_REFERENCE)
        resolve(v.checkpoint,WorkFamily.CHECKPOINTS);val r=(resolve(v.recovery,WorkFamily.RECOVERIES).payload as WRecoveriesPayload).value
        workRequire(r.assignment==s && r.decision in setOf("not-started","bind-existing","revoked-isolated"),WorkProblem.NATIVE_UNKNOWN)
        workRequire(v.intendedExecution.controllerId==actor.controllerId && v.intendedExecution.executionId!=a.intendedExecution.executionId &&
            v.intendedExecution.template==a.intendedExecution.template,WorkProblem.INVALID_BINDING)
        taskReady(v.task,v.manifest,v.profile);val generation=nextGeneration(a.executionGeneration)
        a.requiredResources.forEach { resource ->val c=(h.record(WorkFamily.CLAIMS,resource).payload as WClaimsPayload).value
            workRequire((c.holder as? WClaimsHolderVariant0Value)?.value?.subject==s,WorkProblem.CLAIM_CONFLICT)
            put(resource,WClaimsPayload(c.copy(generation=nextGeneration(c.generation),state="held",holder=WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",WAssignmentSubject(v.assignmentId,generation))))),c.generation) }
        put(key(v.intentId),WNativeIntentsPayload(WNativeIntents(WAssignmentSubject(v.assignmentId,generation),0,v.correlationId,"prepare",v.intendedExecution.nativeSessionId,a.workspaceId,"pending",emptyList(),null)))
        return changeAssignment(v.assignmentId,a.copy(task=v.task,manifest=v.manifest,profile=v.profile,executionGeneration=generation,
            execution=null,binding=null,delegationId=null,state="reserved",currentIntent=key(v.intentId),intendedExecution=v.intendedExecution))
    }
    private fun complete(v: CCompleteAssignment): WorkRecordIdentity {
        val a=assignment(v.assignmentId,v.expectedGeneration);val s=subject(v.assignmentId,a)
        workRequire(a.state !in setOf("completed","replaced") && a.binding!=null,WorkProblem.INVALID_TRANSITION)
        workRequire(s in facts.completedAssignments,WorkProblem.NATIVE_UNKNOWN)
        workRequire(a.checkpoint==v.checkpoint,WorkProblem.INCOMPLETE_HANDOFF)
        val c=(resolve(v.checkpoint,WorkFamily.CHECKPOINTS).payload as WCheckpointsPayload).value
        workRequire(c.assignment==s && c.handoff=="finished" && c.notification!=null,WorkProblem.INCOMPLETE_HANDOFF)
        h.checkpointLineage(c)
        val delivery=(h.record(WorkFamily.DELIVERIES,c.notification!!).payload as WDeliveriesPayload).value
        workRequire(delivery.state=="durable-delivered",WorkProblem.INCOMPLETE_HANDOFF)
        v.completionBasis.forEach(::source)
        release(v.releaseResources,WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",s)))
        return changeAssignment(v.assignmentId,a.copy(state="completed",currentIntent=null))
    }
    private fun release(resources: List<WorkKey.Resource>,holder: WClaimsHolder) {
        resources.forEach { resource ->val c=(h.record(WorkFamily.CLAIMS,resource).payload as WClaimsPayload).value
            workRequire(c.holder==holder,WorkProblem.CLAIM_CONFLICT)
            put(resource,WClaimsPayload(c.copy(generation=nextGeneration(c.generation),state="free",holder=null,workspaceId=null)),c.generation) }
    }
    private fun checkpoint(v: CRecordCheckpoint): WorkRecordIdentity {
        val d=v.data;val a=own(d.assignment)
        workRequire(a.generation==v.expectedAssignmentGeneration,WorkProblem.STALE_GENERATION)
        val plan=(h.record(WorkFamily.PLANS,d.plan).payload as WPlansPayload).value
        workRequire(plan.assignment==d.assignment && plan.task==d.task && plan.generation==d.planGeneration && plan.steps==d.planSteps,WorkProblem.STALE_GENERATION)
        val result=put(v.key,WCheckpointsPayload(decodeWCheckpoints(encodeCRecordCheckpointData(d))))
        changeAssignment(d.assignment.assignmentId,a.copy(checkpoint=ref(result)))
        return result
    }
    private fun delivery(v: CRecordDelivery): WorkRecordIdentity {
        val old=h.records[id(WorkFamily.DELIVERIES,v.key)]?.payload as? WDeliveriesPayload
        old?.value?.let { o ->workRequire(o.assignment==v.assignment && o.sourceCutId==v.sourceCutId && o.destinationTask==v.destinationTask && o.content==v.content && o.state!="durable-delivered",WorkProblem.INVALID_TRANSITION) }
        if(v.state=="durable-delivered") { workRequire(v.basis.isNotEmpty(),WorkProblem.INCOMPLETE_HANDOFF);v.basis.forEach(::source) }
        return put(v.key,WDeliveriesPayload(WDeliveries(v.assignment,v.sourceCutId,v.destinationTask,v.content,v.expectedGeneration?.let(::nextGeneration) ?: 0,v.state,v.basis)),v.expectedGeneration)
    }
    private fun verifyCut(locator: UUID,host: UUID,session: String,request: Digest,packet: Digest,messages: List<Pair<String,Digest>>) {
        val cut=facts.sourceCuts[locator] ?: throw WorkFault(WorkProblem.INCOMPLETE_HANDOFF)
        workRequire(cut==WorkRetainedCut(host,session,request,packet,messages),WorkProblem.DIGEST_MISMATCH)
    }
    private fun controllerContinuation(v: CRecordControllerContinuation): WorkRecordIdentity {
        workRequire(actor.assignment==null && actor.controllerId==v.key.controllerId,WorkProblem.INVALID_SUBJECT)
        val d=v.data;verifyCut(d.source.retainedLocatorId,d.source.nativeHostId,d.source.nativeSessionId,d.source.renderedRequestDigest,d.source.packetDigest,d.source.nativeMessages.map { it.messageId to it.digest })
        d.frontier.forEach { f ->f.assignments.forEach { seen ->val a=h.assignment(seen.assignmentId)
            workRequire(a.task==f.task && a.generation==seen.recordGeneration && a.executionGeneration==seen.executionGeneration && a.intendedExecution.controllerId==actor.controllerId,WorkProblem.STALE_GENERATION) } }
        val observed=d.frontier.flatMap { it.assignments }.map { it.assignmentId }.toSet()
        val active=h.records.values.filter { (it.payload as? WAssignmentsPayload)?.value?.let { a ->a.intendedExecution.controllerId==actor.controllerId && a.state !in setOf("completed","replaced") }==true }.map { (it.identity.key as WorkKey.Id).id }.toSet()
        workRequire(observed.containsAll(active) || d.gaps.isNotEmpty(),WorkProblem.INCOMPLETE_HANDOFF)
        return put(v.key,WControllerContinuationsPayload(d))
    }
    private fun disposition(v: CRecordDisposition): WorkRecordIdentity {
        val d=v.data;resolve(d.result,WorkFamily.RESULTS)
        val report=(resolve(d.report,WorkFamily.VERIFICATION_REPORTS).payload as WVerificationReportsPayload).value
        workRequire(d.result in report.subjects && report.findings.any { it.id==d.findingId },WorkProblem.INVALID_SUBJECT)
        if(d.decision=="owner-exception")decision(d.ownerDecision ?: throw WorkFault(WorkProblem.BLOCKED_INPUT))
        if(d.decision=="fixed-by-result")workRequire(d.replacementResult!=null && d.replacementResult!=d.result,WorkProblem.BLOCKED_INPUT)
        return put(v.key,WDispositionsPayload(WDispositions(d.result,d.report,d.findingId,d.gateIds,d.decision,d.basis,d.replacementResult,d.ownerDecision,actor.principalId)))
    }
    private fun acceptance(v: CAcceptResult): WorkRecordIdentity {
        val d=v.data;val result=(resolve(d.result,WorkFamily.RESULTS).payload as WResultsPayload).value
        workRequire(actor.assignment==null && actor.principalId!=result.producer.principalId,WorkProblem.BLOCKED_INPUT)
        workRequire(d.task==result.task,WorkProblem.INVALID_SUBJECT)
        val acceptance=WAcceptances(d.result,d.task,d.verificationPlan,d.evidence,d.reports,d.dispositions,d.applicability,d.criterionIds,d.permittedTargets,d.basis,actor.principalId)
        h.acceptanceChecks(acceptance)
        val dispositions=d.dispositions.map { (resolve(it,WorkFamily.DISPOSITIONS).payload as WDispositionsPayload).value }
        val knownReports=h.records.values.mapNotNull { (it.payload as? WVerificationReportsPayload)?.value }.filter { d.result in it.subjects }
        knownReports.forEach { r ->r.findings.filter { it.blocking && (it.affectedResultIds.isEmpty() || (d.result.identity.key as WorkKey.Id).id in it.affectedResultIds) }.forEach { finding ->
            workRequire(dispositions.any { disposition ->disposition.result==d.result && disposition.findingId==finding.id &&
                (resolve(disposition.report,WorkFamily.VERIFICATION_REPORTS).payload as WVerificationReportsPayload).value==r &&
                disposition.decision in setOf("not-applicable","owner-exception") },WorkProblem.BLOCKED_INPUT)
        } }
        return put(v.key,WAcceptancesPayload(acceptance))
    }
    private fun prepareIntegration(v: CPrepareIntegration): WorkRecordIdentity {
        val a=(resolve(v.acceptance,WorkFamily.ACCEPTANCES).payload as WAcceptancesPayload).value
        workRequire(a.result==v.result && v.targetId in a.permittedTargets,WorkProblem.NOT_ACCEPTED)
        workRequire(WorkTargetObservation(v.targetRepositoryId,v.targetId,v.expectedTarget,v.expectedTree,v.expectedDirtyDigest) in facts.targets,WorkProblem.STALE_TARGET)
        val holder=WClaimsHolderVariant1Value(WClaimsHolderVariant1("integration",v.key.id))
        acquire(v.resources.map { it.resource to it.expectedGeneration },holder,null,WSourceVariant2Value(WSourceVariant2("work",v.acceptance)))
        return put(v.key,WIntegrationIntentsPayload(WIntegrationIntents(v.result,v.acceptance,v.targetRepositoryId,v.targetId,v.expectedTarget,v.expectedTree,v.expectedDirtyDigest,v.resources.map { it.resource },v.correlationId,0,"prepared",null)))
    }
    private fun observeIntegration(v: CObserveIntegration): WorkRecordIdentity {
        val i=(h.record(WorkFamily.INTEGRATION_INTENTS,v.intent).payload as WIntegrationIntentsPayload).value
        workRequire(i.generation==v.expectedGeneration && i.state in setOf("prepared","unknown"),WorkProblem.STALE_GENERATION)
        workRequire(facts.integrations[v.intent]==WorkIntegrationObservation(v.intent,i.correlationId,v.outcome,
            v.resultingSnapshot,v.applicability,v.evidence,v.observations,v.pending),WorkProblem.NATIVE_UNKNOWN)
        v.observations.forEach(::source)
        var observation: WorkReference?=null
        if(v.outcome=="confirmed") {
            val snapshot=v.resultingSnapshot ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
            val app=v.applicability ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            workRequire(snapshot.repositoryId==i.targetRepositoryId && snapshot in facts.retainedSnapshots,WorkProblem.STALE_TARGET)
            val accepted=(resolve(i.acceptance,WorkFamily.ACCEPTANCES).payload as WAcceptancesPayload).value
            val plan=(h.record(WorkFamily.VERIFICATION_PLANS,accepted.verificationPlan).payload as WVerificationPlansPayload).value
            val applicability=(resolve(app,WorkFamily.EVIDENCE_APPLICABILITY).payload as WEvidenceApplicabilityPayload).value
            workRequire(applicability.result==i.result,WorkProblem.INVALID_SUBJECT)
            val evidence=v.evidence.map { e ->
                workRequire(applicability.evidence.any { it.evidence==e && it.state=="retained" },WorkProblem.BLOCKED_INPUT)
                (resolve(e,WorkFamily.EVIDENCE).payload as WEvidencePayload).value
            }
            val gates=plan.gates.filter { it.stage=="integration" }
            workRequire(gates.none { it.id in applicability.heldGateIds },WorkProblem.BLOCKED_INPUT)
            gates.forEach { gate ->workRequire(evidence.any { e ->e.outcome=="passed" && e.boundary==gate.boundary &&
                e.subjects.any { s ->s is WSourceVariant1Value && s.value.artifact==snapshot || s is WSourceVariant2Value && s.value.reference==i.result } },WorkProblem.BLOCKED_INPUT) }
            val result=put(v.key,WIntegrationsPayload(WIntegrations(v.intent,nextGeneration(i.generation),i.result,snapshot,app,v.evidence,v.observations,actor.principalId,v.pending)))
            observation=ref(result)
        } else workRequire(v.resultingSnapshot==null && v.applicability==null,WorkProblem.INVALID_SUBJECT)
        if(v.outcome!="unknown")release(i.requiredResources,WClaimsHolderVariant1Value(WClaimsHolderVariant1("integration",v.intent.id)))
        return put(v.intent,WIntegrationIntentsPayload(i.copy(generation=nextGeneration(i.generation),state=v.outcome,observation=observation)),i.generation)
    }
    fun finish(result: WorkRecordIdentity,op: WorkOperation): WorkEffect {
        // A closed operation has this common attachment envelope; never accept a generic payload.
        val encoded=encodeWorkOperation(op) as JObject
        val attachments=encoded.entries.getValue("bodies").list().map { a ->val f=a.fields("digest","body")
            val body=Utf8Content.fromText(f.getValue("body").string())
            workRequire(body.digest==digest(f.getValue("digest")),WorkProblem.DIGEST_MISMATCH)
            if(body.sizeBytes>1024*1024)refuse(CodecProblem.ADMISSION_LIMIT);body }
        workRequire(attachments.map { it.digest }.distinct().size==attachments.size,WorkProblem.MISSING_BODY)
        val required=changes.values.flatMap { workLinks(it.payload).bodies }
        required.forEach { b ->h.bodies[b.digest]?.let { workRequire(it.sizeBytes==b.byteLength,WorkProblem.DIGEST_MISMATCH) } }
        val missing=required.filter { it.digest !in h.bodies }
        workRequire(attachments.map { it.digest }.toSet()==missing.map { it.digest }.toSet(),WorkProblem.MISSING_BODY)
        missing.forEach { b ->workRequire(attachments.single { it.digest==b.digest }.sizeBytes==b.byteLength,WorkProblem.DIGEST_MISMATCH) }
        // Explicitly validate supplied external source claims rather than promoting stored labels to authority.
        changes.values.forEach { r ->
            fun visit(v: JsonValue) {
                when(v) {
                    is JObject -> { if(v.entries["kind"]?.let { it is JString && it.value in setOf("knowledge","artifact","work") }==true) {
                        source(decodeWSource(v))
                    } else {
                        if((v.entries["status"] as? JString)?.value=="accepted-reference" && "sources" in v.entries) {
                            val sourced=v.entries.getValue("sources").list().map(::decodeWSource)
                            workRequire(sourced.any { it is WSourceVariant0Value && it.value.reference in facts.approvedDecisions },WorkProblem.BLOCKED_INPUT)
                        }
                        v.entries.values.forEach(::visit)
                    } }
                    is JArray -> v.values.forEach(::visit)
                    else -> Unit
                }
            };visit(encodeWorkPayload(r.payload))
        }
        val after=h.derive(changes.values,attachments,facts.approvedDecisions)
        return WorkEffect(h,after,result,changes.keys.sortedBy(identityCodec::recordPath).map { after.records.getValue(it) },attachments)
    }
}
