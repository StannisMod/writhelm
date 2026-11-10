// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

/** Pure exact lookup after APP has checked current READ and AUTH2 scope at this same H.
 * No latest fallback, cursor cache, permission evaluator or recursive body traversal. */
internal fun WorkState.readExact(selector: WorkRead.Exact): WorkRecord {
    workRequire(selector.projectId==projectId && selector.identity.key.projectId==projectId,WorkProblem.FOREIGN_REFERENCE)
    val r=record(selector.identity.family,selector.identity.key)
    if(selector.identity.family.mutable) {
        workRequire(selector.digest==null && selector.generation==recordGeneration(r.payload),WorkProblem.STALE_GENERATION)
    } else workRequire(selector.generation==null && selector.digest==WorkCodec(modelVersion=modelVersion).digest(r),WorkProblem.DIGEST_MISMATCH)
    return r
}
internal fun WorkState.readBody(selector: WorkRead.Body): Utf8Content {
    workRequire(selector.projectId==projectId,WorkProblem.FOREIGN_REFERENCE)
    val record=readExact(selector.record as? WorkRead.Exact ?: throw WorkFault(WorkProblem.INVALID_SUBJECT))
    workRequire(workLinks(record.payload).bodies.any { it.digest==selector.digest },WorkProblem.INVALID_SUBJECT)
    return bodies[selector.digest] ?: throw WorkFault(WorkProblem.MISSING_BODY)
}

/** Exact derived selectors only; APP must filter every row before pagination and reauthorize every
 * cursor page. This does not implement APP's protected snapshot/cursor lifecycle. */
internal fun WorkState.listSelectors(family: WorkFamily,taskId: UUID?): List<WorkRead.Exact> {
    workRequire(family in setOf(WorkFamily.TASK_HEADS,WorkFamily.ASSIGNMENTS,WorkFamily.RESULTS,
        WorkFamily.VERIFICATION_REPORTS,WorkFamily.ACCEPTANCES,WorkFamily.INTEGRATIONS,WorkFamily.CONTROLLER_CONTINUATIONS,
        WorkFamily.ASSIGNMENT_REPLACEMENTS,WorkFamily.CONDITIONS,WorkFamily.CONDITION_CONFIRMATIONS,WorkFamily.CONTINUATIONS),WorkProblem.INVALID_SUBJECT)
    return records.values.asSequence().filter { it.identity.family==family }
        .filter { taskId==null || (if(it.payload is WIntegrationsPayload)
            listOf((resolve(it.payload.value.result).payload as WResultsPayload).value.task) else recordTasks(it)).any { task->task.taskId==taskId } ||
            (it.identity.key as? WorkKey.Task)?.taskId==taskId }
        .sortedBy { identityCodec.recordPath(it.identity) }
        .map { r -> WorkRead.Exact(projectId,r.identity,if(family.mutable)null else WorkCodec(modelVersion=modelVersion).digest(r),recordGeneration(r.payload)) }.toList()
}

internal fun recordTasks(r: WorkRecord): List<WorkKey.TaskRevision> = when(val p=r.payload) {
    is WTaskRevisionsPayload -> listOf(r.identity.key as WorkKey.TaskRevision)
    is WAssignmentsPayload -> listOf(p.value.task)
    is WMechanicalAssignmentsPayload -> listOf(p.value.bindingData.task)
    is WMechanicalBindingsPayload -> listOf(p.value.task)
    is WAssignmentBindingsPayload -> listOf(p.value.task)
    is WPlansPayload -> listOf(p.value.task)
    is WInputManifestsPayload -> listOf(p.value.task)
    is WLoadedViewsPayload -> listOf(p.value.task)
    is WMemoryPayload -> listOf(p.value.task)
    is WCheckpointsPayload -> listOf(p.value.task)
    is WRetrospectivesPayload -> listOf(p.value.task)
    is WResultsPayload -> listOf(p.value.task)
    is WMechanicalResultsPayload -> listOf(p.value.task)
    is WVerificationPlansPayload -> listOf(p.value.task)
    is WVerificationReportsPayload -> listOf(p.value.task)
    is WAcceptancesPayload -> listOf(p.value.task)
    is WControllerContinuationsPayload -> p.value.frontier.map { it.task }
    is WAssignmentReplacementsPayload -> listOf(p.value.oldRecord.payload.task,p.value.next.task)
    is WConditionsPayload -> listOf(p.value.consumerTask,p.value.responsibleTask)
    else -> emptyList()
}

internal data class WorkRecordFacts(val identity: WorkRecordIdentity,val tasks: List<WorkKey.TaskRevision>,
    val digest: Digest,val generation: Long?,val references: List<WorkReference>,val directBodies: List<WorkBodyReference>,
    val knowledge: List<KnowledgeReference>,val artifacts: List<WSnapshot>)
internal fun workRecordFacts(r: WorkRecord): WorkRecordFacts = workRecordFacts(r,2)
internal fun workRecordFacts(r: WorkRecord,modelVersion:Int): WorkRecordFacts {
    val knowledge=ArrayList<KnowledgeReference>();val artifacts=ArrayList<WSnapshot>()
    fun visit(v: JsonValue) {
        when(v) {
            is JObject -> {
                if(v.entries.keys in setOf(setOf("kind","revision","revisionDigest","clauseId","contentDigest"),setOf("kind","revision","revisionDigest","contextDigest"))) knowledge.add(reference(v))
                else if(v.entries.keys==setOf("repositoryId","commit","tree","dirtyContentDigest","retainedLocatorId"))artifacts.add(decodeWSnapshot(v))
                else v.entries.values.forEach(::visit)
            }
            is JArray -> v.values.forEach(::visit)
            else -> Unit
        }
    }
    visit(encodeWorkPayload(r.payload));val links=workLinks(r.payload)
    return WorkRecordFacts(r.identity,recordTasks(r),WorkCodec(modelVersion=modelVersion).digest(r),recordGeneration(r.payload),links.records,links.bodies,knowledge,artifacts)
}
internal data class WorkSubjectProjection(val assignment: WorkAssignmentProjection,val subject: WAssignmentSubject,
    val binding: WAssignmentBindings,val base: WBase,val task: WorkRecordFacts,val manifest: WorkRecordFacts,
    val retainedOwnContinuation: List<WorkRecordFacts>,val reviewSubjects: List<WorkRecordFacts>,
    val retainedOwnResults: List<WorkRecordFacts>,val retainedOwnReports: List<WorkRecordFacts>)

/** Fixed facts, NOT effective grants. AUTH2 combines them with current policy/core READ at H.
 * A base manifest must be resolved in existing core, and each referenced fragment authorized there.
 * No recursive link here implies authority to disclose that linked record or body. */
internal fun WorkState.subjectProjection(subject: WAssignmentSubject): WorkSubjectProjection {
    val binding=binding(subject)
    val taskRecord=record(WorkFamily.TASK_REVISIONS,binding.task)
    val base=(taskRecord.payload as WTaskRevisionsPayload).value.base ?: throw WorkFault(WorkProblem.NOT_READY)
    val continuation=records.values.filter { r ->when(val p=r.payload) {
        is WPlansPayload -> p.value.assignment==subject
        is WLoadedViewsPayload -> p.value.assignment==subject
        is WMemoryPayload -> p.value.assignment==subject
        is WCheckpointsPayload -> p.value.assignment==subject
        is WRetrospectivesPayload -> p.value.assignment==subject
        is WSourceCutsPayload -> p.value.assignment==subject
        is WContinuationsPayload -> p.value.assignment==subject
        else -> false
    } }.map(::workRecordFacts)
    return WorkSubjectProjection(assignmentProjection(subject.assignmentId),subject,binding,base,workRecordFacts(taskRecord),
        workRecordFacts(resolve(binding.manifest)),continuation,binding.reviewSubjects.map { workRecordFacts(resolve(it)) },
        records.values.filter { (it.payload as? WResultsPayload)?.value?.assignment==subject }.map(::workRecordFacts),
        records.values.filter { (it.payload as? WVerificationReportsPayload)?.value?.assignment==subject }.map(::workRecordFacts))
}
