// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

/** Read-only candidate inputs, in the explicitly selected application order. This is not a
 * planner's semantic assessment, authorization, a Git effect, acceptance or integration. */
internal data class CandidateInput(val reference:WorkReference,val task:WorkKey.TaskRevision,
    val base:WSnapshot,val result:WSnapshot,val changes:WorkBodyReference)

/** Refuse ambiguity or missing observations rather than silently selecting a different result.
 * The trusted caller supplies observations at the same captured H; this helper cannot prove
 * retention, diff correctness, authority or freshness by itself. */
internal fun candidateInputs(work:WorkState,selected:List<WorkReference>,facts:WorkExternalFacts):List<CandidateInput> {
    workRequire(selected.isNotEmpty() && selected.size<=256 && selected.distinct().size==selected.size,WorkProblem.INVALID_SUBJECT)
    val entries=selected.map { ref ->
        workRequire(ref.identity.family==WorkFamily.RESULTS && ref.identity.key.projectId==work.projectId,WorkProblem.INVALID_REFERENCE)
        val r=(work.resolve(ref).payload as? WResultsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        val base=work.task(r.task).base?.code?.singleOrNull() ?: throw WorkFault(WorkProblem.BLOCKED_INPUT)
        val result=r.artifacts.singleOrNull() ?: throw WorkFault(WorkProblem.INVALID_SUBJECT)
        workRequire(base.commit!=null && result.commit!=null && base.dirtyContentDigest==null && result.dirtyContentDigest==null &&
            base.repositoryId==result.repositoryId && base.commit.algorithm==result.commit.algorithm,WorkProblem.INVALID_SUBJECT)
        workRequire(result in facts.retainedSnapshots && WorkSubmittedDiff(r.task,listOf(result),r.changes) in facts.submittedDiffs,
            WorkProblem.INVALID_SUBJECT)
        val body=work.bodies[r.changes.digest] ?: throw WorkFault(WorkProblem.MISSING_BODY)
        workRequire(body.sizeBytes==r.changes.byteLength,WorkProblem.DIGEST_MISMATCH)
        CandidateInput(ref,r.task,base,result,r.changes)
    }
    workRequire(entries.map { it.task.taskId }.distinct().size==entries.size &&
        entries.map { it.result.repositoryId }.distinct().size==1,WorkProblem.INVALID_SUBJECT)
    return java.util.Collections.unmodifiableList(entries)
}
