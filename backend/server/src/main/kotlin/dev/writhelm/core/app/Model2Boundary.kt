// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*

internal fun validateModel2Boundary(h: H1BoundState,c: H1BoundState,actor: ScopedCredential,r: Receipt,result: WorkRecord) {
    val before=h.work.getValue(r.identity.projectId);val after=c.work.getValue(r.identity.projectId)
    fun own(subject: WAssignmentSubject) {
        integrity((actor.scope as? CredentialScope.Controller)?.controllerId==before.assignment(subject.assignmentId).intendedExecution.controllerId)
    }
    when(val p=result.payload) {
        is WConditionsPayload -> { own(p.value.consumer);integrity(p.value.recordedBy==actor.credential.principalId) }
        is WConditionConfirmationsPayload -> {
            when(val a=p.value.assessedBy) {
                is WConditionAssessorProvenanceVariant0Value -> integrity((actor.scope as? CredentialScope.Controller)?.controllerId==a.value.controllerId &&
                    a.value.principalId==actor.credential.principalId && a.value.credentialId==actor.credential.id)
                is WConditionAssessorProvenanceVariant1Value -> {
                    val d=(actor.scope as? CredentialScope.Assignment)?.let { h.authority.delegations[it.delegationId] } ?: fail(StoreProblem.INTEGRITY)
                    integrity(a.value.assignment==WAssignmentSubject(d.binding.assignmentId,d.binding.executionGeneration) &&
                        a.value.execution==before.binding(a.value.assignment).execution && a.value.execution.principalId==actor.credential.principalId)
                }
            }
        }
        is WContinuationsPayload -> {
            own(p.value.assignment)
            val previous=(before.records[result.identity]?.payload as? WContinuationsPayload)?.value
            if(previous==null)integrity(r.identity.operation=="prepare_continuation" && p.value.generation==0L && p.value.phase=="prepared")
            else { integrity(r.identity.operation=="observe_continuation");validateContinuationEdge(previous,p.value) }
            // A declaration added after preparation is not omitted from an historical activation.
            if(p.value.phase in setOf("prepared","in-flight","environment-confirmed","delivery-confirmed","resume-confirmed"))Model2Inspector(before).requirePairs(p.value)
            if(p.value.phase in setOf("prepared","in-flight","environment-confirmed","delivery-confirmed","resume-confirmed"))h.requireCurrentContinuation(r.identity.projectId,p.value.assignment)
        }
        is WAssignmentReplacementsPayload -> {
            val v=p.value;val old=before.record(WorkFamily.ASSIGNMENTS,v.oldRecord.key)
            own(WAssignmentSubject(v.oldRecord.key.id,v.oldRecord.payload.executionGeneration))
            integrity(old.payload==WAssignmentsPayload(v.oldRecord.payload) && WorkCodec().digest(old)==v.oldRecord.digest)
            integrity(after.assignment(v.oldRecord.key.id)==v.oldRecord.payload.copy(generation=nextGeneration(v.oldRecord.payload.generation),state="replaced"))
            v.oldRecord.payload.requiredResources.forEach { key -> integrity(before.record(WorkFamily.CLAIMS,key)==after.record(WorkFamily.CLAIMS,key)) }
            val next=after.assignment(v.next.assignmentId)
            integrity(next.state=="reserved" && next.generation==0L && next.executionGeneration==nextGeneration(v.oldRecord.payload.executionGeneration) &&
                next.execution==null && next.delegationId==null && next.binding==null && next.checkpoint==null && next.recovery==null && next.reviewSubjects==v.oldRecord.payload.reviewSubjects)
            integrity(h.authority.records().map { Auth2RecordCodec().encode(it).toList() }.toSet()==c.authority.records().map { Auth2RecordCodec().encode(it).toList() }.toSet())
            integrity(r.writes.all { it.record.namespace=="work" && it.record.family in setOf("assignment-replacements","assignments","claims","native-intents") } &&
                r.writes.size==4+v.next.resources.size)
        }
        else -> Unit
    }
}
