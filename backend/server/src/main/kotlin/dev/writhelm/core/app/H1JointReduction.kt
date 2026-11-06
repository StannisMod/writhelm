// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import dev.writhelm.core.auth2.WorkOperation as AuthorityOperation

/** Unpublished exact CORE+AUTH result; no partial enrollment or automatic external/native effects.
 * A real publisher binding must validate and publish all changes + one receipt together. */
internal class H1JointCandidate internal constructor(val before: H1BoundState,val core: ApprovalWorkflow,
    val authority: Auth2State,val work: Map<java.util.UUID,WorkState>,val evaluation: Evaluation)

internal class H1JointReduction {
    private val authCodec=Auth2RecordCodec(auth2Admission())
    private val workCodec=WorkCodec()
    /** Actual closed codecs, not generic StoreCommand admission or a new binding per request. */
    fun canonicalize(bytes: ByteArray): ByteArray {
        val value=StrictJson.decodeWorkCommand(bytes,JsonLimits(bytes=2*1024*1024,stringBytes=65536,depth=32,collection=1024,tokens=100000),1024*1024)
        val f=value.fields("formatVersion","requestKey","projectId","operation","payload")
        if(f.getValue("formatVersion").long()!=3L)fail(StoreProblem.UNSUPPORTED)
        return if(f.getValue("operation").string() in H1Catalog.authOperations) Auth3CommandCodec().let { it.encode(it.decode(bytes)) }
        else workCodec.encodeCommand(workCodec.decodeCommand(bytes))
    }
    fun invocation(command: WorkCommand): WorkInvocation {
        val p=StrictJson.decodeWorkCommand(workCodec.encodeCommand(command),JsonLimits(bytes=2*1024*1024,stringBytes=65536),1024*1024)
            .fields("formatVersion","requestKey","projectId","operation","payload").getValue("payload") as JObject
        val fields=p.entries
        val subject=(fields["assignment"] ?: (fields["data"] as? JObject)?.entries?.get("assignment"))?.let { v ->
            if(v===JNull) null else { val q=v.fields("assignmentId","generation"); AssignmentSubject(command.projectId,uuid(q.getValue("assignmentId")),q.getValue("generation").long()) }
        }
        val controller=(fields["intendedExecution"] as? JObject)?.entries?.get("controllerId")?.let(::uuid)
            ?: (fields["key"] as? JObject)?.entries?.get("controllerId")?.let(::uuid)
        val op=AuthorityOperation.entries.singleOrNull { it.tag==command.operation.name } ?: fail(StoreProblem.UNSUPPORTED)
        return WorkInvocation(command.projectId,op,command.requestKey,subject,controller,if(op==AuthorityOperation.UPDATE_PLAN)subject else null)
    }
    fun authorizeWork(base: H1BoundState,command: WorkCommand,presented: PresentedCredential): WorkAdmission? {
        val credential=base.authority.authenticate(presented,command.projectId) ?: return null
        var call=invocation(command)
        if(command.operation is CUpdatePlanOperation) {
            val target=command.operation.value
            val previous=base.work[command.projectId]?.records?.get(WorkRecordIdentity(WorkFamily.PLANS,target.key))?.payload as? WPlansPayload
            if(previous!=null) {
                if(previous.value.task!=target.task)return null
                call=call.copy(planOwner=previous.value.assignment?.let { AssignmentSubject(command.projectId,it.assignmentId,it.generation) })
            }
        }
        // Evidence intentionally has no body assignment field. Its subject comes solely from the
        // current immutable credential binding; labels or arbitrary referenced records cannot select it.
        if(command.operation is CRecordEvidenceOperation || command.operation is CConfirmConditionOperation && credential.scope is CredentialScope.Assignment) {
            val c=base.authority.authenticate(presented,command.projectId) ?: return null
            val scope=c.scope as? CredentialScope.Assignment ?: return null
            val b=base.authority.delegations[scope.delegationId]?.binding ?: return null
            call=call.copy(assignment=AssignmentSubject(b.projectId,b.assignmentId,b.executionGeneration))
        }
        val admission=Auth2Policy(base.authority,base.projection).authorizeWork(presented,call) ?: return null
        val binding=admission.binding
        if(binding==null) {
            val target=when(val op=command.operation) {
                is CBindAssignmentOperation -> op.value.assignmentId
                is CRequestNativeTransitionOperation -> op.value.assignmentId
                is CObserveNativeTransitionOperation -> op.value.assignmentId
                is CRecordRecoveryOperation -> op.value.data.assignment.assignmentId
                is CRebindAssignmentOperation -> op.value.assignmentId
                is CCompleteAssignmentOperation -> op.value.assignmentId
                is CRecordDeliveryOperation -> op.value.assignment.assignmentId
                is CReplaceIsolatedAssignmentOperation -> op.value.old.key.id
                is CRecordConditionOperation -> op.value.data.consumer.assignmentId
                is CPrepareContinuationOperation -> op.value.data.assignment.assignmentId
                is CObserveContinuationOperation -> {
                    val record=base.work[command.projectId]?.records?.get(WorkRecordIdentity(WorkFamily.CONTINUATIONS,op.value.key))?.payload as? WContinuationsPayload
                    record?.value?.assignment?.assignmentId ?: return null
                }
                is CConfirmConditionOperation -> {
                    val record=base.work[command.projectId]?.records?.get(op.value.data.condition.identity)?.payload as? WConditionsPayload ?: return null
                    val designated=(record.value.assessor as? WConditionAssessorVariant0Value)?.value ?: return null
                    if((credential.scope as? CredentialScope.Controller)?.controllerId!=designated.controllerId)return null
                    record.value.consumer.assignmentId
                }
                else -> null
            }
            if(target!=null) {
                val assignment=(base.work[command.projectId]?.records?.get(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(command.projectId,target)))?.payload as? WAssignmentsPayload)?.value ?: return null
                if((credential.scope as? CredentialScope.Controller)?.controllerId!=assignment.intendedExecution.controllerId)return null
                val terminalGuard=command.operation.name in setOf("replace_isolated_assignment","record_condition","prepare_continuation","observe_continuation",
                    "bind_assignment","request_native_transition","observe_native_transition","rebind_assignment","record_recovery","complete_assignment")
                val continuationDone=(command.operation as? CObserveContinuationOperation)?.let { op ->
                    (base.work[command.projectId]?.records?.get(WorkRecordIdentity(WorkFamily.CONTINUATIONS,op.value.key))?.payload as? WContinuationsPayload)?.value?.phase=="resume-confirmed"
                }==true
                if(terminalGuard && assignment.state in setOf("completed","replaced") || continuationDone)
                    return WorkAdmission(admission.identity,admission.credentialId,admission.scope,AdmissionMode.REPLAY_ONLY,null)
            }
            return admission
        }
        val state=base.work[command.projectId] ?: return null
        val subject=state.subjectProjection(WAssignmentSubject(binding.assignmentId,binding.executionGeneration))
        val shape=StrictJson.decodeWorkCommand(workCodec.encodeCommand(command),JsonLimits(bytes=2*1024*1024,stringBytes=65536),1024*1024)
            .fields("formatVersion","requestKey","projectId","operation","payload").getValue("payload") as JObject
        val data=(shape.entries["data"] as? JObject)?.entries ?: shape.entries
        data["task"]?.let { if(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(it))!=subject.binding.task)return null }
        data["manifest"]?.let { if(identityCodec.decodeReference(StrictJson.encode(it))!=subject.binding.manifest)return null }
        if(command.operation is CSubmitVerificationReportOperation && command.operation.value.data.subjects!=binding.subjectResults)return null
        val proof=H1ProtectedReads().closure(base,credential)
        val resources=proof.exactTask+proof.taskAndBase+proof.ownContinuation+proof.reviewGroup+proof.activeOwnRecords+
            proof.ownedResults.flatMap { own -> listOf(ProtectedResource.ImmutableWork(own.reference))+own.intrinsicBodies.map { ProtectedResource.WorkBody(own.reference,it) } }
        val refs=resources.filterIsInstance<ProtectedResource.ImmutableWork>().map { it.reference }.toSet()
        var allowed=true
        fun references(value: JsonValue) {
            when(value) {
                is JObject -> if(value.entries.keys==setOf("family","key","digest")) {
                    if(identityCodec.decodeReference(StrictJson.encode(value)) !in refs)allowed=false
                } else value.entries.values.forEach(::references)
                is JArray -> value.values.forEach(::references)
                else -> Unit
            }
        }
        references(shape)
        if(!allowed)return null
        fun owned(family: WorkFamily,value: JsonValue): Boolean {
            val key=identityCodec.decodeKey(family,StrictJson.encode(value))
            return resources.any { r -> when(r) {
                is ProtectedResource.ImmutableWork -> r.reference.identity==WorkRecordIdentity(family,key)
                is ProtectedResource.MutableWork -> r.selector.identity==WorkRecordIdentity(family,key)
                else -> false
            } }
        }
        if(command.operation is CRecordCheckpointOperation) {
            for((name,family) in listOf("loadedView" to WorkFamily.LOADED_VIEWS,"plan" to WorkFamily.PLANS,"memory" to WorkFamily.MEMORY))
                if(data[name]?.let { owned(family,it) }!=true)return null
            data["notification"]?.takeIf { it!==JNull }?.let { value ->
                val key=identityCodec.decodeKey(WorkFamily.DELIVERIES,StrictJson.encode(value))
                val delivery=(state.records[WorkRecordIdentity(WorkFamily.DELIVERIES,key)]?.payload as? WDeliveriesPayload)?.value ?: return null
                if(delivery.assignment!=WAssignmentSubject(binding.assignmentId,binding.executionGeneration))return null
            }
        }
        if(command.operation is CSubmitVerificationReportOperation) {
            val plan=(state.records[WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,command.operation.value.data.plan)]?.payload as? WVerificationPlansPayload)?.value ?: return null
            if(plan.task!=subject.binding.task)return null
        }
        if(command.operation is CRecordSourceCutOperation) {
            val source=command.operation.value.data;val execution=subject.binding.execution
            if(source.profile!=subject.binding.profile || source.nativeHostId!=execution.nativeHostId || source.nativeSessionId!=execution.nativeSessionId)return null
        }
        val outcome=when(val op=command.operation) {
            is CRecordCheckpointOperation -> op.value.data.result
            is CRecordRetrospectiveOperation -> op.value.data.result
            else -> null
        }
        if(outcome!=null && proof.ownedResults.none { it.reference==outcome })return null
        data["sourceCutId"]?.let { if(!owned(WorkFamily.SOURCE_CUTS,obj("projectId" to uuid(command.projectId),"id" to it)))return null }
        if(command.operation is CRecordMemoryOperation || command.operation is CRecordLoadedViewOperation) {
            val previous=data["previous"]
            if(previous!=null && previous!==JNull && !owned(if(command.operation is CRecordMemoryOperation)WorkFamily.MEMORY else WorkFamily.LOADED_VIEWS,previous))return null
        }
        return admission
    }

    /** Register through BOTH actual reducers, checking postconditions before emitting any changes. */
    fun auth3(base: H1BoundState,command: Auth3Command,presented: PresentedCredential): H1JointCandidate {
        val transition=base.authority.execute(presented,command,base.projection)
        return when(transition) {
            Auth3Transition.Denied -> fail(StoreProblem.DENIED)
            is Auth3Transition.Rejected -> H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("auth",transition.problem.name),emptyList()))
            is Auth3Transition.Success -> {
                var core=base.core;var work=base.work;val changes=ArrayList<RecordChange>()
                transition.enrollmentEffects?.let { enrollment ->
                    integrity(command.payload is Auth3Payload.RegisterDelegation)
                    val e=enrollment.enrollment;val snapshot=core.snapshot(8192)
                    integrity(snapshot.grants.none { it.principalId==e.principalId && it.projectId==e.projectId })
                    core=withGrants(snapshot,snapshot.grants+enrollment.coreReadGrant)
                    val before=work[e.projectId] ?: fail(StoreProblem.INTEGRITY)
                    val a=before.assignment(enrollment.assignment.assignmentId)
                    val effect=WorkReducer().bindRegistration(before,WorkRegistrationBinding(enrollment.assignment.assignmentId,
                        enrollment.expectedAssignmentGeneration,enrollment.assignment.executionGeneration,a.task,a.intendedExecution,a.reviewSubjects,enrollment.delegationId))
                    integrity(effect.before===before)
                    work=frozenMap(work+(e.projectId to effect.after))
                    enrollment.verifyApplied(corePolicy(core.snapshot(8192)),h1WorkProjection(base.basis,work))
                    changes+=workChanges(effect)
                    val grant=obj("principalId" to uuid(e.principalId),"projectId" to uuid(e.projectId))
                    changes+=RecordChange(RecordKey("core","grants",grant),StrictJson.encode(CoreRecordCodec.envelope("grants",grant,
                        obj("capabilities" to JArray(listOf(str("READ")))))))
                }
                integrity((command.payload is Auth3Payload.RegisterDelegation)==(transition.enrollmentEffects!=null))
                val authority=Auth2State.checked(base.storeId,headBasis(base.basis),transition.candidate.records(),corePolicy(core.snapshot(8192)),h1WorkProjection(base.basis,work))
                changes+=authChanges(base.authority,authority)
                val outcome=Auth3OutcomeCodec.decode(Auth3OutcomeCodec.encode(transition)) as Auth3Outcome.Success
                val result=if(outcome.replacedCredentialId==null)uuid(outcome.resultId) else obj("credentialId" to uuid(outcome.resultId),"replacedCredentialId" to uuid(outcome.replacedCredentialId))
                H1JointCandidate(base,core,authority,work,Evaluation(LogicalOutcome.Success(outcome.operation,result,false),changes.sortedBy { it.key.path }))
            }
        }
    }
    /** External observations must be trusted, head-bound adapter facts, not RPC/model assertions.
     * This function does not fabricate absent native/packet/source/target qualifications. */
    fun work(base: H1BoundState,command: WorkCommand,presented: PresentedCredential,observations: H1ObservedFacts): H1JointCandidate {
        integrity(observations.basis==base.basis)
        val admission=authorizeWork(base,command,presented) ?: fail(StoreProblem.DENIED)
        if(admission.mode!=AdmissionMode.EVALUATE)fail(StoreProblem.DENIED) // replay-only never calls CORE
        val credential=base.authority.authenticate(presented,command.projectId) ?: fail(StoreProblem.DENIED)
        val state=base.work[command.projectId] ?: WorkState.empty(command.projectId)
        val scope=credential.scope
        val binding=admission.binding
        val subject=binding?.let { WAssignmentSubject(it.assignmentId,it.executionGeneration) }
        val execution=subject?.let { state.binding(it).execution }
        val actor=WorkExecutionInput(admission.identity.principalId,admission.credentialId,
            (scope as? CredentialScope.Controller)?.controllerId,subject,execution)
        val profile=when(val op=command.operation) {
            is CReserveAssignmentOperation -> op.value.profile
            is CRebindAssignmentOperation -> op.value.profile
            is CReplaceIsolatedAssignmentOperation -> op.value.next.profile
            is CBindAssignmentOperation -> state.assignment(op.value.assignmentId).profile
            is CRequestNativeTransitionOperation -> if(op.value.transition in setOf("start","resume"))state.assignment(op.value.assignmentId).profile else null
            else -> null
        }
        if(profile!=null && state.reference(WorkFamily.PROFILES,profile) !in observations.qualifiedProfiles)
            return H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work","NOT_READY"),emptyList()))
        // Knowledge facts come from the actual core at H, not the adapter's proposed grant/source sets.
        val snapshot=base.core.snapshot(8192);val catalog=RevisionCatalog(snapshot.revisions)
        val refs=linkedSetOf<KnowledgeReference>()
        fun requested(value: JsonValue) {
            when(value) {
                is JObject -> if(value.entries.keys in setOf(setOf("kind","revision","revisionDigest","clauseId","contentDigest"),setOf("kind","revision","revisionDigest","contextDigest"))) {
                    refs.add(reference(value));if(refs.size>16384)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                } else if(value.entries.keys==setOf("family","key","digest")) {
                    val ref=identityCodec.decodeReference(StrictJson.encode(value))
                    state.records[ref.identity]?.let { refs.addAll(workRecordFacts(it).knowledge) }
                } else value.entries.values.forEach(::requested)
                is JArray -> value.values.forEach(::requested)
                else -> Unit
            }
        }
        requested(StrictJson.decodeWorkCommand(workCodec.encodeCommand(command),JsonLimits(bytes=2*1024*1024,stringBytes=65536),1024*1024))
        refs.addAll(observations.facts.knowledgeSources);refs.addAll(observations.facts.approvedDecisions)
        val taskKey=when(val op=command.operation) {
            is CReserveAssignmentOperation -> op.value.task
            is CRebindAssignmentOperation -> op.value.task
            is CReplaceIsolatedAssignmentOperation -> op.value.next.task
            is CBindAssignmentOperation -> state.assignment(op.value.assignmentId).task
            is CRequestNativeTransitionOperation -> state.assignment(op.value.assignmentId).task
            else -> null
        }
        taskKey?.let { refs.addAll(state.task(it).approvedDesign) }
        profile?.let { refs.addAll(workRecordFacts(state.record(WorkFamily.PROFILES,it)).knowledge) }
        if(refs.size>16384)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val assigned=subject?.let { state.subjectProjection(it) }
        val pinned=assigned?.base?.let { pinnedKnowledge(snapshot,it) }
        val declared=assigned?.let { projection -> projection.manifest.knowledge+
            projection.retainedOwnContinuation.filter { it.identity.family==WorkFamily.LOADED_VIEWS }.flatMap { it.knowledge } }.orEmpty().toSet()
        val readable=refs.filter { ref -> when(ref) {
            is ClauseReference -> catalog.read(ref) is ReadResult.Found
            is ContextReference -> catalog.read(ref) is ReadResult.Found
        } }.filter { it.revision.document.projectId==command.projectId && base.core.validationSourceAvailable(it) &&
            (pinned==null || it in pinned || it in declared || it in observations.facts.knowledgeSources) }.toSet()
        // The runtime's exact per-command scope/applicability qualification is not replaced by
        // "every approved clause in the project". Actual core approval/current withdrawal and fixed
        // subject scope independently constrain each ruling; protected reads separately require READ.
        val approved=readable.filter { it in observations.facts.approvedDecisions && base.core.validationDecisionApproved(it) }.toSet()
        val registered=base.authority.delegations.values.filter { it.binding.projectId==command.projectId && it.status==CredentialStatus.ACTIVE }.mapNotNull { d ->
            val b=d.binding
            val key=base.authority.credentials.values.singleOrNull { it.scope==CredentialScope.Assignment(d.id) && it.credential.status==CredentialStatus.ACTIVE && it.credential.replacedBy==null }
                ?: return@mapNotNull null
            val assignment=state.assignment(b.assignmentId)
            if(assignment.executionGeneration!=b.executionGeneration || assignment.delegationId!=d.id)return@mapNotNull null
            d.id to WExecution(b.executionId,b.principalId,key.credential.id,assignment.intendedExecution.nativeHostId,
                assignment.intendedExecution.nativeSessionId,assignment.workspaceId)
        }.toMap()
        val assignedArtifacts=assigned?.let { projection ->
            val submitted=(command.operation as? CSubmitResultOperation)?.value?.data?.let { data ->
                observations.facts.submittedDiffs.filter { it.task==projection.binding.task && it.artifacts==data.artifacts && it.changes==data.changes }.flatMap { it.artifacts }
            }.orEmpty()
            (projection.base.code+projection.manifest.artifacts+projection.reviewSubjects.flatMap { it.artifacts }+
                projection.retainedOwnResults.flatMap { it.artifacts }+projection.retainedOwnReports.flatMap { it.artifacts }+submitted).toSet()
        }
        if(assigned!=null && command.operation is CRecordEvidenceOperation) {
            val allowed=(listOf(assigned.task,assigned.manifest)+assigned.retainedOwnContinuation+
                assigned.reviewSubjects+assigned.retainedOwnResults+assigned.retainedOwnReports).flatMap { r ->
                    listOf(WorkReference(r.identity,r.digest))+r.references
                }.toSet()
            val data=command.operation.value.data
            val sources=data.subjects+data.significantInputs.map { it.source }+data.clauseCases.map { it.clause }
            if(sources.filterIsInstance<WSourceVariant2Value>().any { it.value.reference !in allowed })
                return H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work","INVALID_SUBJECT"),emptyList()))
        }
        val facts=observations.facts.copy(knowledgeSources=readable,approvedDecisions=approved,registeredExecutions=registered,
            retainedSnapshots=observations.facts.retainedSnapshots.filter { assignedArtifacts==null || it in assignedArtifacts }.toSet())
        return try {
            if(command.operation.name in model2RefusalCodes)workRequire(facts.model2.basis==WorkGitOid(base.basis.algorithm,base.basis.hex),WorkProblem.STALE_GENERATION)
            val activation=when(val op=command.operation) {
                is CRequestNativeTransitionOperation -> if(op.value.transition in setOf("start","resume"))op.value.assignmentId else null
                is CObserveNativeTransitionOperation -> if(op.value.outcome=="confirmed" &&
                    (state.records[WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,WorkKey.Id(command.projectId,op.value.intentId))]?.payload as? WNativeIntentsPayload)?.value?.operation in setOf("start","resume"))op.value.assignmentId else null
                is CPrepareContinuationOperation -> op.value.data.assignment.assignmentId
                is CObserveContinuationOperation -> if(op.value.outcome in setOf("armed","confirmed"))
                    (state.record(WorkFamily.CONTINUATIONS,op.value.key).payload as WContinuationsPayload).value.assignment.assignmentId else null
                else -> null
            }
            activation?.let { id ->base.requireCurrentContinuation(command.projectId,WAssignmentSubject(id,state.assignment(id).executionGeneration)) }
            if(command.operation is CBindAssignmentOperation || command.operation is CRequestNativeTransitionOperation && command.operation.value.transition in setOf("start","resume")) {
                val id=when(val op=command.operation) { is CBindAssignmentOperation -> op.value.assignmentId;is CRequestNativeTransitionOperation -> op.value.assignmentId;else -> error("unreachable") }
                val delegation=state.assignment(id).delegationId?.let { base.authority.delegations[it] }
                workRequire(delegation!=null && delegation.status==CredentialStatus.ACTIVE,WorkProblem.NOT_READY)
                workRequire(base.authority.credentials.values.any { it.scope==CredentialScope.Assignment(delegation!!.id) && it.credential.status==CredentialStatus.ACTIVE },WorkProblem.NOT_READY)
                workRequire(base.authority.policy.allows(Actor(delegation!!.binding.principalId),command.projectId,Capability.READ),WorkProblem.BLOCKED_INPUT)
            }
            val governing=when(val op=command.operation) {
                is CAcceptResultOperation -> op.value.data.verificationPlan
                is CSubmitVerificationReportOperation -> op.value.data.plan
                else -> null
            }
            if(governing!=null) {
                var cursor: WorkKey.Revision?=governing
                val seen=HashSet<WorkKey.Revision>()
                while(cursor!=null) {
                    if(seen.size>=256)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                    workRequire(seen.add(cursor),WorkProblem.INVALID_REFERENCE)
                    val plan=(state.record(WorkFamily.VERIFICATION_PLANS,cursor).payload as WVerificationPlansPayload).value
                    workRequire(plan.authorizedChanges.all { it in approved },WorkProblem.BLOCKED_INPUT)
                    cursor=plan.previous
                }
            }
            var auth=base.authority
            if(command.operation is CRecordRecoveryOperation && command.operation.value.data.decision=="revoked-isolated") {
                val assignment=state.assignment(command.operation.value.data.assignment.assignmentId)
                val id=assignment.delegationId
                val delegation=id?.let { auth.delegations[it] ?: fail(StoreProblem.INTEGRITY) }
                if(delegation!=null && delegation.status==CredentialStatus.ACTIVE) {
                    when(val revoked=auth.execute(presented,Auth3Command(command.requestKey,command.projectId,Auth3Payload.RevokeDelegation(delegation.id,delegation.generation)),base.projection)) {
                        Auth3Transition.Denied -> fail(StoreProblem.DENIED)
                        is Auth3Transition.Rejected -> if(revoked.problem==Auth2Problem.AUTH_GENERATION_EXHAUSTED)throw WorkFault(WorkProblem.GENERATION_EXHAUSTED) else fail(StoreProblem.INTEGRITY)
                        is Auth3Transition.Success -> auth=revoked.candidate
                    }
                }
            }
            val effect=WorkReducer().reduce(state,command,actor,facts)
            val reused=effect.changedRecords.flatMap { workLinks(it.payload).bodies }.filter { it.digest in state.bodies }.toSet()
            if(!H1ProtectedReads().mayReuseBodies(base,credential,reused))fail(StoreProblem.DENIED)
            effect.changedRecords.filter { it.payload is WVerificationPlansPayload }.forEach { record ->
                var cursor: WorkKey.Revision?=record.identity.key as WorkKey.Revision;val seen=HashSet<WorkKey.Revision>()
                while(cursor!=null) {
                    if(seen.size>=256)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                    workRequire(seen.add(cursor),WorkProblem.INVALID_REFERENCE)
                    val plan=(effect.after.record(WorkFamily.VERIFICATION_PLANS,cursor).payload as WVerificationPlansPayload).value
                    workRequire(plan.authorizedChanges.all { it in approved },WorkProblem.BLOCKED_INPUT)
                    cursor=plan.previous
                }
            }
            val work=frozenMap(base.work+(command.projectId to effect.after))
            val authority=Auth2State.checked(base.storeId,headBasis(base.basis),auth.records(),base.authority.policy,h1WorkProjection(base.basis,work))
            val result=effect.after.records.getValue(effect.result)
            val resultKey=StrictJson.decode(identityCodec.encodeKey(result.identity.key))
            val outcome=LogicalOutcome.Success(command.operation.name,obj("key" to resultKey,
                "generation" to (recordGeneration(result.payload)?.let(::JNumber) ?: JNull),"digest" to digest(workCodec.digest(result))),false)
            H1JointCandidate(base,base.core,authority,work,Evaluation(outcome,(workChanges(effect)+authChanges(base.authority,authority)).sortedBy { it.key.path }))
        } catch(e: WorkFault) {
            val classified=workRefusal(command.operation.name,e)
            H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work",classified.problem.name),emptyList()))
        }
    }
    private fun workChanges(effect: WorkEffect): List<RecordChange> = effect.changedRecords.map { r ->
        RecordChange(RecordKey("work",r.identity.family.tag,StrictJson.decode(identityCodec.encodeKey(r.identity.key))),workCodec.encodeRecord(r))
    }+effect.addedBodies.map { RecordChange(RecordKey("work","bodies",digest(it.digest)),it.bytes()) }
    private fun authChanges(before: Auth2State,after: Auth2State): List<RecordChange> {
        val old=before.records().associateBy { authCodec.path(it) }
        val next=after.records().associateBy { authCodec.path(it) }
        integrity(next.keys.containsAll(old.keys))
        return next.filter { (path,record) -> old[path]!=record }.values.map { r ->
            val (family,key)=authCodec.identity(r);RecordChange(RecordKey("auth",family,key),authCodec.encode(r))
        }
    }
    private fun withGrants(s: WorkflowSnapshot,grants: List<Grant>)=ApprovalWorkflow.restoreValidated(
        WorkflowSnapshot(s.revisions,grants,s.proposals,s.requests,s.resolutions,s.approvals,s.continuity,s.assessments,s.manifests,s.heads,s.generations,s.blocked))
}
internal class H1ObservedFacts(val basis: GitOid,facts: WorkExternalFacts,qualifiedProfiles: Set<WorkReference>) {
    init {
        var entries=qualifiedProfiles.size.toLong()+facts.knowledgeSources.size+facts.approvedDecisions.size+facts.retainedSnapshots.size+
            facts.native.size+facts.sourceCuts.size+facts.targets.size+facts.completedAssignments.size+facts.isolatedAssignments.size+
            facts.verifiedPackets.size+facts.registeredExecutions.size+facts.integrations.size+facts.submittedDiffs.size
        if(entries>16384)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        fun add(n: Int) { if(n>16384-entries)refuse(CodecProblem.RESOURCE_UNAVAILABLE);entries+=n }
        facts.native.values.forEach { add(it.sources.size) }
        facts.sourceCuts.values.forEach { add(it.messages.size) }
        facts.integrations.values.forEach { add(it.evidence.size);add(it.observations.size);add(it.pending.size) }
        facts.submittedDiffs.forEach { add(it.artifacts.size) }
        add(facts.model2.controllers.size);add(facts.model2.observedEvidence.size);add(facts.model2.replacements.size)
        add(facts.model2.stages.size);add(facts.model2.notPerformedAttempts.size)
        add(facts.model2.replacementFailures.size)
        facts.model2.replacements.values.forEach { add(it.retainedClaims.size);add(it.pending.size);add(it.planned.resources.size) }
        facts.model2.stages.values.forEach { add(it.sources.size);add(it.evidence.size) }
    }
    val qualifiedProfiles=frozenSet(qualifiedProfiles)
    val facts=facts.copy(knowledgeSources=frozenSet(facts.knowledgeSources),approvedDecisions=frozenSet(facts.approvedDecisions),
        retainedSnapshots=frozenSet(facts.retainedSnapshots),native=frozenMap(facts.native.mapValues { it.value.copy(sources=frozenList(it.value.sources)) }),
        sourceCuts=frozenMap(facts.sourceCuts.mapValues { it.value.copy(messages=frozenList(it.value.messages)) }),targets=frozenSet(facts.targets),
        completedAssignments=frozenSet(facts.completedAssignments),isolatedAssignments=frozenSet(facts.isolatedAssignments),
        verifiedPackets=frozenSet(facts.verifiedPackets),registeredExecutions=frozenMap(facts.registeredExecutions),
        integrations=frozenMap(facts.integrations.mapValues { (_,v) -> v.copy(evidence=frozenList(v.evidence),observations=frozenList(v.observations),pending=frozenList(v.pending)) }),
        submittedDiffs=frozenSet(facts.submittedDiffs.map { it.copy(artifacts=frozenList(it.artifacts)) }),
        model2=facts.model2.copy(controllers=frozenMap(facts.model2.controllers),observedEvidence=frozenSet(facts.model2.observedEvidence),
            replacementFailures=frozenMap(facts.model2.replacementFailures),
            notPerformedAttempts=frozenSet(facts.model2.notPerformedAttempts),
            stages=frozenMap(facts.model2.stages.mapValues { (_,v) ->v.copy(sources=frozenList(v.sources),evidence=frozenList(v.evidence)) }),
            replacements=frozenMap(facts.model2.replacements.mapValues { (_,v) ->v.copy(
                before=WorkCodec().decodeRecord(WorkCodec().encodeRecord(v.before,true),true),
                planned=decodeCReplaceIsolatedAssignmentNext(encodeCReplaceIsolatedAssignmentNext(v.planned)),
                retainedClaims=frozenMap(v.retainedClaims.mapValues { decodeWClaims(encodeWClaims(it.value)) }),pending=frozenList(v.pending)) })))
}
