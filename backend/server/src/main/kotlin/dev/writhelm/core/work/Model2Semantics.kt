// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal fun WConditions.notice()=WorkBodyReference(consumerNotice.digest,consumerNotice.byteLength.toInt())
internal fun WContinuations.notice()=WorkBodyReference(notification.digest,notification.byteLength.toInt())
internal fun conditionAssessorExecution(p: WConditionAssessorProvenance): UUID=when(p) {
    is WConditionAssessorProvenanceVariant0Value -> p.value.executionId
    is WConditionAssessorProvenanceVariant1Value -> p.value.execution.executionId
}
internal fun validateModel2Shape(p: WorkPayload) {
    when(p) {
        is WConditionsPayload -> {
            shape(p.value.supersedes.map { it.identity }.distinct().size==p.value.supersedes.size)
            if(p.value.supersedes.isNotEmpty())shape(p.value.decisionSources.isNotEmpty())
        }
        is WConditionConfirmationsPayload -> {
            shape(p.value.supersedes.map { it.identity }.distinct().size==p.value.supersedes.size)
            if(p.value.supersedes.isNotEmpty())shape(p.value.correctionBasis.isNotEmpty())
        }
        is WContinuationsPayload -> validateContinuationShape(p.value)
        else -> Unit
    }
}
internal fun validateContinuationShape(v: WContinuations) {
    if(v.mode=="unchanged-base")shape(v.rebind==null && v.checkpoint!=null && v.origin==v.assignment && v.before==v.after)
    else shape(v.rebind!=null && v.assignment!=v.origin)
    shape(v.conditions.map { it.condition.identity }.distinct().size==v.conditions.size)
    val fixed=mapOf("prepared" to ("environment" to "not-issued"),"environment-confirmed" to ("environment" to "confirmed"),
        "delivery-confirmed" to ("delivery" to "confirmed"),"resume-confirmed" to ("resume" to "confirmed"))
    fixed[v.phase]?.let { shape(v.stage==it.first && v.effect.state==it.second) }
    if(v.phase=="unknown")shape(v.effect.state=="outcome-unknown")
}
internal fun validateModel2OperationShape(op: WorkOperation) {
    when(op) {
        is CPrepareContinuationOperation -> {
            val v=op.value.data
            if(v.mode=="unchanged-base")shape(v.rebind==null && v.checkpoint!=null && v.assignment==v.origin && v.before==v.after)
            else shape(v.rebind!=null && v.assignment!=v.origin)
            shape(v.conditions.map { it.condition.identity }.distinct().size==v.conditions.size)
        }
        is CReplaceIsolatedAssignmentOperation -> shape(op.value.next.resources.map { it.resource }.distinct().size==op.value.next.resources.size)
        else -> Unit
    }
}

/** All conditions come from validated records. Plans/source labels are deliberately not consulted.
 * The derived index is disposable and every activation rebuilds it at the actual captured H. */
internal class Model2Inspector(private val h: WorkState) {
    private val conditions=h.records.values.filter { it.payload is WConditionsPayload }
    private val confirmations=h.records.values.filter { it.payload is WConditionConfirmationsPayload }
    private fun condition(r: WorkRecord)=(r.payload as WConditionsPayload).value
    fun logicalAssignments(id: UUID): Set<UUID> {
        val lineage=linkedSetOf(id);var changed=true
        val links=h.records.values.mapNotNull { (it.payload as? WAssignmentReplacementsPayload)?.value }
        while(changed) {
            changed=false
            links.forEach { r -> if(r.next.assignmentId in lineage && lineage.add(r.oldRecord.key.id))changed=true }
            if(lineage.size>256)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        }
        return lineage
    }
    fun frontier(subject: WAssignmentSubject): List<WorkReference> {
        val logical=logicalAssignments(subject.assignmentId)
        val rows=conditions.filter { condition(it).consumer.assignmentId in logical }
        if(rows.size>256 || rows.sumOf { condition(it).supersedes.size }>4096)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val retired=rows.flatMap { condition(it).supersedes }.map { it.identity }.toSet()
        return rows.filter { it.identity !in retired }.sortedBy { (it.identity.key as WorkKey.Id).id.toString() }
            .map { WorkReference(it.identity,WorkCodec().digest(it)) }
    }
    fun coverage(subject: WAssignmentSubject,basis: WorkGitOid): WConditionCoverage {
        val refs=frontier(subject)
        workRequire(refs.isNotEmpty(),WorkProblem.NOT_READY)
        return WConditionCoverage(basis,Digest.of(StrictJson.encode(JArray(refs.map { StrictJson.decode(identityCodec.encodeReference(it)) }))),refs.size.toLong())
    }
    fun confirmationFrontier(ref: WorkReference): List<WorkReference> {
        val rows=confirmations.filter { (it.payload as WConditionConfirmationsPayload).value.condition==ref }
        if(rows.size>256 || rows.sumOf { (it.payload as WConditionConfirmationsPayload).value.supersedes.size }>4096)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val superseded=rows.flatMap { (it.payload as WConditionConfirmationsPayload).value.supersedes }.map { it.identity }.toSet()
        val tips=rows.filter { it.identity !in superseded }
        workRequire(tips.size<=32,WorkProblem.BLOCKED_INPUT)
        return tips.map { WorkReference(it.identity,WorkCodec().digest(it)) }
    }
    fun resolvedPairs(subject: WAssignmentSubject): List<WConditionPair> {
        val tips=frontier(subject)
        val owners=hashMapOf<WorkRecordIdentity,WorkRecordIdentity>()
        var edges=0
        tips.forEach { tip ->
            val pending=java.util.ArrayDeque<WorkReference>();pending.add(tip)
            val seen=hashSetOf<WorkRecordIdentity>()
            while(pending.isNotEmpty()) {
                val ref=pending.removeFirst()
                if(!seen.add(ref.identity))continue
                // Distinct declaration tips sharing a predecessor are conflicting corrections,
                // not two independent obligations whose separate assessments can clear the fork.
                val owner=owners.putIfAbsent(ref.identity,tip.identity)
                workRequire(owner==null || owner==tip.identity,WorkProblem.NOT_READY)
                val predecessors=(h.resolve(ref).payload as WConditionsPayload).value.supersedes
                edges+=predecessors.size
                if(edges>4096 || owners.size>256)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                pending.addAll(predecessors)
            }
        }
        return tips.map { ref ->
        val c=(h.resolve(ref).payload as WConditionsPayload).value
        workRequire(c.consumer==subject,WorkProblem.NOT_READY) // old agreement never silently retargets a successor
        val tips=confirmationFrontier(ref)
        workRequire(tips.size==1,WorkProblem.NOT_READY)
        val f=(h.resolve(tips.single()).payload as WConditionConfirmationsPayload).value
        workRequire(f.decision=="resolved",WorkProblem.NOT_READY)
        checkConfirmation(c,f)
        WConditionPair(ref,tips.single())
    }
    }
    fun requirePairs(v: WContinuations) {
        requireCheckpointContext(v.sourceCheckpoint,v.origin)
        v.checkpoint?.let { requireCheckpointContext(it,if(it==v.sourceCheckpoint)v.origin else v.assignment) }
        val pairs=resolvedPairs(v.assignment)
        workRequire(v.conditions==pairs && v.coverage.conditionCount==pairs.size.toLong(),WorkProblem.NOT_READY)
        workRequire(v.coverage.conditionsDigest==coverage(v.assignment,v.coverage.basisH).conditionsDigest,WorkProblem.DIGEST_MISMATCH)
    }
    fun requireActivation(subject: WAssignmentSubject,delivered: Boolean) {
        requireAssignmentContext(subject)
        if(frontier(subject).isEmpty())return
        val pairs=resolvedPairs(subject)
        val candidates=h.records.values.mapNotNull { (it.payload as? WContinuationsPayload)?.value }
            .filter { it.assignment==subject && it.conditions==pairs && it.phase !in setOf("held","unknown","prepared") }
        val eligible=candidates.filter { v ->
            if(delivered) v.delivery!=null && (v.phase in setOf("delivery-confirmed","resume-confirmed") || v.phase=="in-flight" && v.stage=="resume")
            else v.phase in setOf("environment-confirmed","delivery-confirmed","resume-confirmed") || v.phase=="in-flight" && v.stage in setOf("delivery","resume")
        }
        workRequire(eligible.size==1,WorkProblem.NOT_READY)
        requirePairs(eligible.single())
        if(delivered) {
            val v=eligible.single();val d=(h.record(WorkFamily.DELIVERIES,requireNotNull(v.delivery)).payload as WDeliveriesPayload).value
            val cut=(h.resolve(v.sourceCheckpoint).payload as WCheckpointsPayload).value
            workRequire(d.assignment==cut.assignment && d.sourceCutId==cut.sourceCutId && d.content==v.notice() && d.state=="durable-delivered" &&
                d.destinationTask==h.binding(subject).task,WorkProblem.INCOMPLETE_HANDOFF)
        }
    }
    /** gaps denote missing extraction/context, not informational entry uncertainty or notes.
     * A newer unrelated memory cannot clear the exact checkpoint's residual gaps. */
    fun requireCheckpointContext(ref: WorkReference,subject: WAssignmentSubject) {
        val c=(h.resolve(ref).payload as WCheckpointsPayload).value
        val m=(h.record(WorkFamily.MEMORY,c.memory).payload as WMemoryPayload).value
        workRequire(c.assignment==subject && m.assignment==subject && m.task==c.task && m.sourceCutId==c.sourceCutId,
            WorkProblem.INCOMPLETE_HANDOFF)
        workRequire(m.gaps.isEmpty(),WorkProblem.INCOMPLETE_HANDOFF)
    }
    fun requireAssignmentContext(subject: WAssignmentSubject) {
        val a=h.assignment(subject.assignmentId)
        workRequire(a.executionGeneration==subject.generation,WorkProblem.STALE_GENERATION)
        a.checkpoint?.let { ref ->
            val checkpoint=(h.resolve(ref).payload as WCheckpointsPayload).value
            // Rebind deliberately retains the original cut/checkpoint. It does not turn it
            // into a new-generation checkpoint or erase its memory obligations.
            workRequire(checkpoint.assignment.assignmentId in logicalAssignments(subject.assignmentId) &&
                (checkpoint.assignment.assignmentId!=subject.assignmentId || checkpoint.assignment.generation<=subject.generation),WorkProblem.INCOMPLETE_HANDOFF)
            requireCheckpointContext(ref,checkpoint.assignment)
        }
    }
    fun checkCondition(c: WConditions) {
        workRequire(h.binding(c.consumer).task==c.consumerTask,WorkProblem.INVALID_SUBJECT)
        h.task(c.responsibleTask)
        val plan=(h.resolve(c.governingPlan).payload as WVerificationPlansPayload).value
        workRequire(plan.task==c.responsibleTask && c.governingPlan.identity.key is WorkKey.Revision,WorkProblem.INVALID_REFERENCE)
        h.governingVerificationPlan(c.responsibleTask,c.governingPlan.identity.key as WorkKey.Revision)
        workRequire(c.requiredGateIds.isNotEmpty() && c.requiredGateIds.all { id -> plan.gates.count { it.id==id }==1 },WorkProblem.INVALID_REFERENCE)
        c.supersedes.forEach { old ->
            val previous=(h.resolve(old).payload as WConditionsPayload).value
            workRequire(previous.consumer.assignmentId in logicalAssignments(c.consumer.assignmentId) && previous.consumerTask.taskId==c.consumerTask.taskId,
                WorkProblem.INVALID_SUBJECT)
        }
        when(val a=c.assessor) {
            is WConditionAssessorVariant0Value -> workRequire(a.value.controllerId==h.binding(c.consumer).intendedExecution.controllerId,WorkProblem.INVALID_SUBJECT)
            is WConditionAssessorVariant1Value -> {
                val b=(h.resolve(a.value.binding).payload as WAssignmentBindingsPayload).value
                workRequire(a.value.binding.identity.key==WorkKey.AssignmentBinding(h.projectId,a.value.assignment.assignmentId,a.value.assignment.generation) &&
                    b.intendedExecution.template=="reviewer-v1",WorkProblem.INVALID_BINDING)
            }
        }
    }
    fun checkConfirmation(c: WConditions,f: WConditionConfirmations) {
        val result=(h.resolve(f.result).payload as WResultsPayload).value
        workRequire(result.task==c.responsibleTask,WorkProblem.INVALID_SUBJECT)
        when(val assessor=c.assessor) {
            is WConditionAssessorVariant0Value -> workRequire((f.assessedBy as? WConditionAssessorProvenanceVariant0Value)?.value?.controllerId==assessor.value.controllerId,WorkProblem.INVALID_SUBJECT)
            is WConditionAssessorVariant1Value -> {
                val a=(f.assessedBy as? WConditionAssessorProvenanceVariant1Value)?.value ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
                val binding=(h.resolve(a.binding).payload as WAssignmentBindingsPayload).value
                workRequire(a.assignment==assessor.value.assignment && a.binding==assessor.value.binding && a.execution==binding.execution && f.result in binding.reviewSubjects,
                    WorkProblem.INVALID_SUBJECT)
            }
        }
        f.supersedes.forEach { old -> workRequire((h.resolve(old).payload as WConditionConfirmationsPayload).value.condition==f.condition,WorkProblem.INVALID_REFERENCE) }
        if(f.decision!="resolved")return
        val plan=(h.resolve(c.governingPlan).payload as WVerificationPlansPayload).value
        val checks=f.evidence.map { h.resolve(it).payload as WEvidencePayload }.map { it.value }
        f.applicability?.let { a ->
            val v=(h.resolve(a).payload as WEvidenceApplicabilityPayload).value
            workRequire(v.result==f.result && f.evidence.all { e -> v.evidence.any { it.evidence==e && it.state=="retained" } } &&
                v.heldGateIds.none { it in c.requiredGateIds },WorkProblem.BLOCKED_INPUT)
        }
        if(c.supersedes.isNotEmpty())workRequire(f.applicability!=null,WorkProblem.BLOCKED_INPUT)
        c.requiredGateIds.forEach { gateId ->
            val gate=plan.gates.single { it.id==gateId }
            val clauses=plan.criteria.filter { it.id in gate.criterionIds }.flatMap { it.clauses }.distinct()
            workRequire(clauses.isNotEmpty() && checks.any { e -> e.outcome=="passed" && e.boundary==gate.boundary &&
                e.subjects.any { s -> s is WSourceVariant2Value && s.value.reference==f.result || s is WSourceVariant1Value && s.value.artifact in result.artifacts } &&
                clauses.all { clause -> e.clauseCases.any { it.clause==clause } } },WorkProblem.NOT_READY)
            if(gate.independent)workRequire(conditionAssessorExecution(f.assessedBy)!=result.producer.executionId,WorkProblem.INVALID_SUBJECT)
        }
    }
}

/** Static whole-state counterpart rules. Original H/C history remains checked independently. */
internal fun validateModel2State(h: WorkState) {
    val inspector=Model2Inspector(h)
    val replacements=h.records.values.filter { it.payload is WAssignmentReplacementsPayload }
    val oldKeys=HashSet<WorkKey.Id>();val nextIds=HashSet<UUID>()
    replacements.forEach { record ->
        val r=(record.payload as WAssignmentReplacementsPayload).value;val before=r.oldRecord.payload
        workRequire(oldKeys.add(r.oldRecord.key) && nextIds.add(r.next.assignmentId),WorkProblem.INVALID_REFERENCE)
        val original=WorkRecord(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,r.oldRecord.key),WAssignmentsPayload(before))
        workRequire(WorkCodec().digest(original)==r.oldRecord.digest,WorkProblem.DIGEST_MISMATCH)
        workRequire(h.assignment(r.oldRecord.key.id)==before.copy(generation=nextGeneration(before.generation),state="replaced"),WorkProblem.INVALID_REFERENCE)
        val after=h.assignment(r.next.assignmentId)
        workRequire(after.executionGeneration==nextGeneration(before.executionGeneration) && after.task==r.next.task && after.reviewSubjects==before.reviewSubjects,
            WorkProblem.INVALID_SUBJECT)
        workRequire((r.isolatedResources+r.retainedClaims).toSet()==before.requiredResources.toSet() && r.isolatedResources.intersect(r.retainedClaims.toSet()).isEmpty(),WorkProblem.INVALID_REFERENCE)
        (r.isolatedResources+r.retainedClaims).forEach { key ->
            val claim=(h.record(WorkFamily.CLAIMS,key).payload as WClaimsPayload).value
            workRequire((claim.holder as? WClaimsHolderVariant0Value)?.value?.subject==WAssignmentSubject(r.oldRecord.key.id,before.executionGeneration) && claim.state!="free",WorkProblem.CLAIM_CONFLICT)
        }
    }
    h.records.values.forEach { record -> when(val p=record.payload) {
        is WConditionsPayload -> inspector.checkCondition(p.value)
        is WConditionConfirmationsPayload -> inspector.checkConfirmation((h.resolve(p.value.condition).payload as WConditionsPayload).value,p.value)
        is WContinuationsPayload -> {
            val v=p.value;val binding=(h.resolve(v.binding).payload as WAssignmentBindingsPayload).value
            workRequire(v.binding.identity.key==WorkKey.AssignmentBinding(h.projectId,v.assignment.assignmentId,v.assignment.generation),WorkProblem.INVALID_BINDING)
            val source=(h.resolve(v.sourceCheckpoint).payload as WCheckpointsPayload).value
            workRequire(source.assignment==v.origin,WorkProblem.INVALID_SUBJECT)
            v.conditions.forEach { pair -> workRequire((h.resolve(pair.condition).payload as WConditionsPayload).value.consumer==v.assignment &&
                (h.resolve(pair.confirmation).payload as WConditionConfirmationsPayload).value.condition==pair.condition,WorkProblem.INVALID_REFERENCE) }
            v.rebind?.let { r ->
                workRequire(r.newBinding==v.binding && r.task==binding.task && r.manifest==binding.manifest &&
                    r.oldBinding.identity.key==WorkKey.AssignmentBinding(h.projectId,v.origin.assignmentId,v.origin.generation),WorkProblem.INVALID_BINDING)
            }
        }
        else -> Unit
    } }
}
