// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

/** Outcome attribution uses the immutable original binding, including the original role/generation.
 * A report's reviewed producer results are not the reviewer's own handoff outcome. */
internal fun WorkState.ownHandoffOutcome(task: WorkKey.TaskRevision,subject: WAssignmentSubject,outcome: WorkReference) {
    val b=binding(subject)
    workRequire(b.task==task,WorkProblem.WRONG_ASSIGNMENT)
    val record=resolve(outcome)
    val matches=when(b.intendedExecution.template) {
        "executor-v1" -> (record.payload as? WResultsPayload)?.value?.let {
            it.task==task && it.assignment==subject && it.producer==b.execution
        }==true
        "reviewer-v1" -> (record.payload as? WVerificationReportsPayload)?.value?.let {
            it.task==task && it.assignment==subject && it.reviewer==b.execution && it.subjects==b.reviewSubjects
        }==true
        else -> false
    }
    workRequire(matches,WorkProblem.INVALID_SUBJECT)
}

internal fun WorkState.checkpointLineage(c: WCheckpoints) {
    val b=binding(c.assignment)
    workRequire(b.task==c.task && b.manifest==c.manifest,WorkProblem.WRONG_ASSIGNMENT)
    if(c.handoff=="finished")workRequire(c.result!=null && c.retrospective!=null && c.notification!=null,WorkProblem.INCOMPLETE_HANDOFF)
    c.result?.let { ownHandoffOutcome(c.task,c.assignment,it) }
    c.retrospective?.let { reference ->
        workRequire(reference.identity.family==WorkFamily.RETROSPECTIVES,WorkProblem.INVALID_REFERENCE)
        val retro=(resolve(reference).payload as WRetrospectivesPayload).value
        workRequire(retro.task==c.task && retro.assignment==c.assignment,WorkProblem.WRONG_ASSIGNMENT)
        // Unfinished continuation may retain an own prior retrospective without declaring a new
        // outcome. Finished handoff (nonnull result above) must agree with its exact outcome.
        workRequire(c.result==null || retro.result==null || retro.result==c.result,WorkProblem.INVALID_SUBJECT)
    }
    c.notification?.let { key ->
        val delivery=(record(WorkFamily.DELIVERIES,key).payload as WDeliveriesPayload).value
        workRequire(delivery.assignment==c.assignment && delivery.sourceCutId==c.sourceCutId && delivery.content==c.report,
            WorkProblem.INCOMPLETE_HANDOFF)
    }
}
