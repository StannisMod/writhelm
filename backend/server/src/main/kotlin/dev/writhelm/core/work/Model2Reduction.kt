// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

/** Same-H trusted capture output, never an HTTP/tool upload. Empty facts fail closed. Actual native
 * qualification is deliberately separate from portable source/record validation. */
internal data class Model2ControllerExecution(val controllerId: UUID,val executionId: UUID,val nativeHostId: UUID,val nativeSessionId: String)
internal data class Model2ReplacementProof(val locator: UUID,val digest: Digest,val before: WorkRecord,
    val planned: CReplaceIsolatedAssignmentNext,val retainedClaims: Map<WorkKey.Resource,WClaims>,val pending: List<WPending>)
internal data class Model2StageObservation(val key: WorkKey.Id,val stage: String,val attempt: UUID,
    val requestDigest: Digest,val outcome: String,val before: WSnapshot,val after: WSnapshot,
    val sources: List<WSource>,val evidence: List<WorkReference>,val envelopeDigest: Digest?,val wakeKnown: Boolean)
internal data class Model2Facts(val basis: WorkGitOid?,val controllers: Map<UUID,Model2ControllerExecution>,
    val observedEvidence: Set<WorkReference>,val replacements: Map<Digest,Model2ReplacementProof>,
    val stages: Map<UUID,Model2StageObservation>,val notPerformedAttempts: Set<UUID>,val replacementFailures: Map<Digest,WorkProblem> = emptyMap()) {
    companion object { fun empty()=Model2Facts(null,emptyMap(),emptySet(),emptyMap(),emptyMap(),emptySet()) }
}

internal class Model2Transaction(private val tx: WorkTransaction) {
    private val h get()=tx.h
    private val actor get()=tx.actor
    private val facts get()=tx.facts.model2
    private val inspector=Model2Inspector(h)
    fun apply(op: WorkOperation): WorkRecordIdentity=when(op) {
        is CRecordConditionOperation -> record(op.value)
        is CConfirmConditionOperation -> confirm(op.value)
        is CPrepareContinuationOperation -> prepare(op.value)
        is CObserveContinuationOperation -> observe(op.value)
        is CReplaceIsolatedAssignmentOperation -> replace(op.value)
        else -> throw WorkFault(WorkProblem.INVALID_TRANSITION)
    }
    private fun controller(subject: WAssignmentSubject): WAssignments {
        val a=h.assignment(subject.assignmentId)
        workRequire(a.executionGeneration==subject.generation && actor.assignment==null && actor.controllerId==a.intendedExecution.controllerId,
            WorkProblem.WRONG_ASSIGNMENT)
        workRequire(a.state !in setOf("completed","replaced"),WorkProblem.INVALID_TRANSITION)
        return a
    }
    private fun record(v: CRecordCondition): WorkRecordIdentity {
        val a=controller(v.data.consumer)
        workRequire(a.task==v.data.consumerTask,WorkProblem.INVALID_SUBJECT)
        val data=(encodeCRecordCondition(v) as JObject).entries.getValue("data") as JObject
        val c=decodeWConditions(JObject(data.entries+mapOf("recordedBy" to uuid(actor.principalId))))
        inspector.checkCondition(c)
        val lineage=inspector.logicalAssignments(c.consumer.assignmentId)
        val existing=h.records.values.filter { (it.payload as? WConditionsPayload)?.value?.consumer?.assignmentId in lineage }
        val retired=existing.flatMap { (it.payload as WConditionsPayload).value.supersedes }.map { it.identity }.toSet()
        val tips=existing.filter { it.identity !in retired }
        if(existing.size>=256)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        c.supersedes.forEach { old -> workRequire(old.identity in tips.map { it.identity } && c.decisionSources.isNotEmpty(),WorkProblem.INVALID_REFERENCE) }
        return tx.put(v.key,WConditionsPayload(c))
    }
    private fun confirm(v: CConfirmCondition): WorkRecordIdentity {
        val c=(h.resolve(v.data.condition).payload as WConditionsPayload).value
        val provenance: WConditionAssessorProvenance=when(val designated=c.assessor) {
            is WConditionAssessorVariant0Value -> {
                workRequire(actor.assignment==null && actor.controllerId==designated.value.controllerId,WorkProblem.WRONG_ASSIGNMENT)
                val observed=facts.controllers[designated.value.controllerId] ?: throw WorkFault(WorkProblem.NOT_READY)
                WConditionAssessorProvenanceVariant0Value(WConditionAssessorProvenanceVariant0("controller",observed.controllerId,
                    actor.principalId,actor.credentialId,observed.executionId,observed.nativeHostId,observed.nativeSessionId))
            }
            is WConditionAssessorVariant1Value -> {
                val s=designated.value.assignment;val a=h.assignment(s.assignmentId)
                workRequire(actor.assignment==s && a.executionGeneration==s.generation && a.state=="active" && a.binding==designated.value.binding &&
                    actor.execution==a.execution && a.execution!=null && v.data.result in a.reviewSubjects,WorkProblem.WRONG_ASSIGNMENT)
                WConditionAssessorProvenanceVariant1Value(WConditionAssessorProvenanceVariant1("reviewer",s,designated.value.binding,requireNotNull(a.execution)))
            }
        }
        val data=(encodeCConfirmCondition(v) as JObject).entries.getValue("data") as JObject
        val f=decodeWConditionConfirmations(JObject(data.entries+mapOf("assessedBy" to encodeWConditionAssessorProvenance(provenance))))
        inspector.checkConfirmation(c,f)
        if(f.decision=="resolved")workRequire(f.evidence.isNotEmpty() && f.evidence.all { it in facts.observedEvidence },WorkProblem.NOT_READY)
        val tips=inspector.confirmationFrontier(f.condition)
        workRequire(f.supersedes.all { it in tips } && tips.size-f.supersedes.size<32 &&
            (f.supersedes.isEmpty() || f.correctionBasis.isNotEmpty()),WorkProblem.BLOCKED_INPUT)
        return tx.put(v.key,WConditionConfirmationsPayload(f))
    }
    private fun prepare(v: CPrepareContinuation): WorkRecordIdentity {
        val a=controller(v.data.assignment)
        workRequire(a.generation==v.data.expectedAssignmentGeneration && a.binding==v.data.binding,WorkProblem.STALE_GENERATION)
        workRequire(a.state==if(v.data.mode=="unchanged-base")"paused" else "bound",WorkProblem.INVALID_TRANSITION)
        workRequire(facts.basis==v.data.coverage.basisH,WorkProblem.STALE_GENERATION)
        val data=(encodeCPrepareContinuation(v) as JObject).entries.getValue("data") as JObject
        val initial=mapOf("generation" to num(0),"phase" to str("prepared"),"stage" to str("environment"),
            "observations" to JArray(emptyList()),"evidence" to JArray(emptyList()),"delivery" to JNull,"nativeIntent" to JNull,"reason" to JNull,
            "effect" to encodeWContinuationEffect(WContinuationEffect(v.key.id,tx.requestKey,tx.fingerprint,"not-issued",null,emptyList())))
        val c=decodeWContinuations(JObject(data.entries+initial))
        validateContinuationShape(c);inspector.requirePairs(c)
        workRequire(c.mode!="unchanged-base" || c.checkpoint==a.checkpoint,WorkProblem.INCOMPLETE_HANDOFF)
        c.rebind?.let { r -> workRequire(r.task==a.task && r.manifest==a.manifest && r.newBinding==a.binding &&
            r.decisionSources.isNotEmpty() && r.observation.isNotEmpty(),WorkProblem.INVALID_BINDING) }
        val competing=h.records.values.filter { (it.payload as? WContinuationsPayload)?.value?.let { old ->old.assignment==c.assignment && old.phase!="resume-confirmed" }==true }
        if(competing.isNotEmpty()) {
            val previous=c.supersedes ?: throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
            workRequire(competing.size==1 && competing.single().identity.key==previous.key,WorkProblem.NATIVE_UNKNOWN)
            val old=(competing.single().payload as WContinuationsPayload).value
            workRequire(old.generation==previous.generation && old.effect.attemptId in facts.notPerformedAttempts,WorkProblem.NATIVE_UNKNOWN)
        }
        return tx.put(v.key,WContinuationsPayload(c))
    }
    private fun observe(v: CObserveContinuation): WorkRecordIdentity {
        val old=(h.record(WorkFamily.CONTINUATIONS,v.key).payload as WContinuationsPayload).value
        controller(old.assignment)
        workRequire(old.generation==v.expectedGeneration && old.phase!="resume-confirmed",WorkProblem.STALE_GENERATION)
        inspector.requirePairs(old)
        var phase: String
        if(v.outcome=="armed") {
            val next=when(old.phase) { "prepared"->"environment";"environment-confirmed"->"delivery";"delivery-confirmed"->"resume";else->old.stage }
            workRequire(v.stage==next && v.effect.state=="armed",WorkProblem.INVALID_TRANSITION)
            if(old.phase in setOf("unknown","held"))workRequire(old.effect.attemptId in facts.notPerformedAttempts &&
                v.effect.previousAttempt==old.effect.attemptId,WorkProblem.NATIVE_UNKNOWN)
            else workRequire(old.phase in setOf("prepared","environment-confirmed","delivery-confirmed"),WorkProblem.INVALID_TRANSITION)
            if(v.stage=="delivery") {
                val intent=(h.record(WorkFamily.NATIVE_INTENTS,v.nativeIntent ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)).payload as WNativeIntentsPayload).value
                workRequire(intent.assignment==old.assignment && intent.outcome=="pending" && intent.operation==if(old.mode=="unchanged-base")"resume" else "start",WorkProblem.INVALID_BINDING)
            }
            phase="in-flight"
        } else {
            workRequire(v.stage==old.stage && old.effect.state in setOf("armed","outcome-unknown") &&
                v.effect.attemptId==old.effect.attemptId && v.effect.requestKey==old.effect.requestKey && v.effect.requestDigest==old.effect.requestDigest,WorkProblem.INVALID_TRANSITION)
            phase=when(v.outcome) {
                "unknown" -> { workRequire(v.effect.state=="outcome-unknown",WorkProblem.INVALID_TRANSITION);"unknown" }
                "held" -> { workRequire(v.reason!=null && v.effect.copy(basis=old.effect.basis)==old.effect,WorkProblem.INCOMPLETE_HANDOFF);"held" }
                "not-performed" -> { workRequire(old.effect.attemptId in facts.notPerformedAttempts && v.effect.state=="not-performed",WorkProblem.NATIVE_UNKNOWN);"held" }
                "confirmed" -> {
                    val proof=facts.stages[old.effect.attemptId] ?: throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
                    workRequire(proof.key==v.key && proof.stage==v.stage && proof.attempt==old.effect.attemptId && proof.requestDigest==v.effect.requestDigest &&
                        proof.outcome=="confirmed" && proof.before==old.before && proof.after==old.after && proof.sources==v.observations && proof.evidence==v.evidence &&
                        v.effect.state=="confirmed",WorkProblem.DIGEST_MISMATCH)
                    if(v.stage=="delivery") {
                        workRequire(proof.wakeKnown && proof.envelopeDigest!=null && v.delivery!=null,WorkProblem.NATIVE_UNKNOWN)
                        val d=(h.record(WorkFamily.DELIVERIES,requireNotNull(v.delivery)).payload as WDeliveriesPayload).value
                        val checkpoint=(h.resolve(old.sourceCheckpoint).payload as WCheckpointsPayload).value
                        workRequire(d.state=="durable-delivered" && d.assignment==checkpoint.assignment && d.sourceCutId==checkpoint.sourceCutId &&
                            d.content==old.notice() && d.destinationTask==h.binding(old.assignment).task,WorkProblem.INCOMPLETE_HANDOFF)
                    }
                    if(v.stage=="resume") {
                        val a=h.assignment(old.assignment.assignmentId)
                        val intent=(h.record(WorkFamily.NATIVE_INTENTS,v.nativeIntent ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)).payload as WNativeIntentsPayload).value
                        workRequire(a.state=="active" && intent.outcome=="confirmed" && intent.assignment==old.assignment,WorkProblem.INVALID_TRANSITION)
                    }
                    when(v.stage) { "environment"->"environment-confirmed";"delivery"->"delivery-confirmed";else->"resume-confirmed" }
                }
                else -> throw WorkFault(WorkProblem.INVALID_TRANSITION)
            }
        }
        val next=old.copy(generation=nextGeneration(old.generation),phase=phase,stage=v.stage,observations=v.observations,evidence=v.evidence,
            delivery=v.delivery ?: old.delivery,nativeIntent=v.nativeIntent ?: old.nativeIntent,reason=v.reason,effect=v.effect)
        validateContinuationShape(next)
        return tx.put(v.key,WContinuationsPayload(next),v.expectedGeneration)
    }
    private fun replace(v: CReplaceIsolatedAssignment): WorkRecordIdentity {
        val old=tx.assignment(v.old.key.id,v.old.expectedGeneration)
        workRequire(old.state !in setOf("completed","replaced") && old.executionGeneration==v.old.executionGeneration &&
            WorkCodec().digest(h.record(WorkFamily.ASSIGNMENTS,v.old.key))==v.old.digest,WorkProblem.STALE_GENERATION)
        facts.replacementFailures[v.proof.digest]?.let { throw WorkFault(it) }
        val proof=facts.replacements[v.proof.digest] ?: throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
        workRequire(proof.digest==v.proof.digest && proof.locator==v.proof.retainedLocatorId && proof.before==h.record(WorkFamily.ASSIGNMENTS,v.old.key) &&
            proof.planned==v.next && proof.pending==v.continuation.pending,WorkProblem.DIGEST_MISMATCH)
        workRequire(v.continuation.gaps.isEmpty() && v.continuation.checkpoint==old.checkpoint && v.recovery==old.recovery,WorkProblem.INCOMPLETE_HANDOFF)
        workRequire(v.next.task.taskId==old.task.taskId && v.next.intendedExecution.controllerId==actor.controllerId &&
            v.next.intendedExecution.template==old.intendedExecution.template && v.next.workspaceId!=old.workspaceId && v.next.assignmentId!=v.old.key.id &&
            v.next.intendedExecution.executionId!=old.intendedExecution.executionId && v.next.intendedExecution.nativeSessionId!=old.intendedExecution.nativeSessionId,
            WorkProblem.INVALID_BINDING)
        val incoming=h.records.values.mapNotNull { (it.payload as? WAssignmentReplacementsPayload)?.let { p ->it to p.value } }
            .singleOrNull { it.second.next.assignmentId==v.old.key.id }?.first?.let { WorkReference(it.identity,WorkCodec().digest(it)) }
        workRequire(incoming==v.previous && h.records.values.none { (it.payload as? WAssignmentReplacementsPayload)?.value?.oldRecord?.key==v.old.key },WorkProblem.INVALID_TRANSITION)
        old.requiredResources.forEach { key -> workRequire(proof.retainedClaims[key]==(h.record(WorkFamily.CLAIMS,key).payload as WClaimsPayload).value,
            WorkProblem.CLAIM_CONFLICT) }
        workRequire(proof.retainedClaims.keys==old.requiredResources.toSet(),WorkProblem.CLAIM_CONFLICT)
        tx.taskReady(v.next.task,v.next.manifest,v.next.profile)
        val lineage=nextGeneration(old.executionGeneration)
        val next=v.next
        val record=WAssignmentReplacements("execution-closed-isolated-replacement",v.previous,
            WAssignmentReplacementsOldRecord(v.old.key,v.old.digest,old),v.recovery,
            decodeWAssignmentReplacementsNext(encodeCReplaceIsolatedAssignmentNext(next)),
            decodeWAssignmentReplacementsContinuation(encodeCReplaceIsolatedAssignmentContinuation(v.continuation)),
            WAssignmentReplacementsProof(v.proof.retainedLocatorId,v.proof.digest),facts.basis ?: throw WorkFault(WorkProblem.NOT_READY),
            proof.retainedClaims.filterValues { it.state=="isolated" }.keys.toList(),proof.retainedClaims.filterValues { it.state!="isolated" }.keys.toList())
        val result=tx.put(v.key,WAssignmentReplacementsPayload(record))
        tx.changeAssignment(v.old.key.id,old.copy(state="replaced"))
        val holder=WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",WAssignmentSubject(next.assignmentId,lineage)))
        tx.acquire(next.resources.map { it.resource to it.expectedGeneration },holder,next.workspaceId,WSourceVariant2Value(WSourceVariant2("work",tx.ref(result))))
        tx.put(tx.key(next.assignmentId),WAssignmentsPayload(WAssignments(next.task,next.manifest,next.profile,0,lineage,null,null,
            next.workspaceId,next.resources.map { it.resource },old.reviewSubjects,"reserved",tx.key(next.intentId),null,null,next.intendedExecution,null)))
        tx.put(tx.key(next.intentId),WNativeIntentsPayload(WNativeIntents(WAssignmentSubject(next.assignmentId,lineage),0,next.correlationId,"prepare",
            next.intendedExecution.nativeSessionId,next.workspaceId,"pending",emptyList(),null)))
        return result
    }
}
