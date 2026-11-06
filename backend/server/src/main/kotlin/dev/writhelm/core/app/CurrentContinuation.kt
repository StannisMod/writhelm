// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.auth.CredentialStatus
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*

/** Current canonical authority/applicability, not physical fencing or a cached adapter grant. */
internal fun H1BoundState.requireCurrentContinuation(project: java.util.UUID,subject: WAssignmentSubject) {
    val work=work.getValue(project);val a=work.assignment(subject.assignmentId)
    Model2Inspector(work).requireAssignmentContext(subject)
    workRequire(a.executionGeneration==subject.generation && a.binding!=null && a.execution!=null,WorkProblem.INVALID_BINDING)
    val delegation=a.delegationId?.let { authority.delegations[it] } ?: throw WorkFault(WorkProblem.NOT_READY)
    workRequire(delegation.status==CredentialStatus.ACTIVE && authority.credentials.values.any {
        it.scope==CredentialScope.Assignment(delegation.id) && it.credential.status==CredentialStatus.ACTIVE && it.credential.replacedBy==null
    },WorkProblem.NOT_READY)
    workRequire(authority.policy.allows(Actor(delegation.binding.principalId),project,Capability.READ),WorkProblem.BLOCKED_INPUT)
    val task=work.task(a.task);val base=task.base ?: throw WorkFault(WorkProblem.NOT_READY)
    val snapshot=core.snapshot(8192)
    workRequire(pinnedKnowledge(snapshot,base).all { core.validationSourceAvailable(it) } && task.approvedDesign.all { core.validationDecisionApproved(it) },WorkProblem.BLOCKED_INPUT)
    val manifest=(work.resolve(a.manifest).payload as WInputManifestsPayload).value
    val applicability=(work.record(WorkFamily.APPLICABILITY,manifest.applicability).payload as WApplicabilityPayload).value
    workRequire(manifest.task==a.task && manifest.base==base && manifest.knownGaps.isEmpty() && applicability.entries.all { it.state=="applicable" },WorkProblem.BLOCKED_INPUT)
    a.requiredResources.forEach { key ->
        val claim=(work.record(WorkFamily.CLAIMS,key).payload as WClaimsPayload).value
        workRequire(claim.state=="held" && claim.workspaceId==a.workspaceId &&
            (claim.holder as? WClaimsHolderVariant0Value)?.value?.subject==subject,WorkProblem.CLAIM_CONFLICT)
    }
}
