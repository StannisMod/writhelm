// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.PresentedCredential
import dev.writhelm.core.auth.CredentialStatus
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.Capability
import java.util.UUID

/** Current passive preparation basis, not a caller's ready/qualified flag. Reservation already
 * passed the ordinary task/profile/manifest reducer. Recheck mutable authority/withdrawal/claims
 * before native I/O; actual runtime/source/profile qualification remains the adapter's own gate. */
internal object NativePreparationContext {
    fun read(state:H1BoundState,key:PresentedCredential,project:UUID,controller:UUID,host:UUID,
        subject:WAssignmentSubject,safety:NativeRuntimeSafety=UnavailableNativeRuntimeSafety):JsonValue {
        val actor=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(actor.scope!=CredentialScope.Controller(controller)||!state.authority.allows(actor,project,Capability.READ)||
            !state.authority.workAllows(actor,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val work=state.work.getValue(project);val a=work.assignment(subject.assignmentId)
        workRequire(a.executionGeneration==subject.generation && a.state=="reserved" && a.binding==null && a.execution==null &&
            a.intendedExecution.controllerId==controller && a.intendedExecution.nativeHostId==host,WorkProblem.INVALID_BINDING)
        val intent=work.record(WorkFamily.NATIVE_INTENTS,requireNotNull(a.currentIntent))
        val i=(intent.payload as WNativeIntentsPayload).value
        workRequire(i.assignment==subject && i.operation=="prepare" && i.outcome=="pending" &&
            i.expectedSession==a.intendedExecution.nativeSessionId && i.expectedWorkspace==a.workspaceId,WorkProblem.INVALID_BINDING)
        a.delegationId?.let { id ->
            val delegation=state.authority.delegations[id] ?: throw WorkFault(WorkProblem.NOT_READY)
            workRequire(delegation.status==CredentialStatus.ACTIVE && state.authority.credentials.values.any {
                it.scope==CredentialScope.Assignment(id) && it.credential.status==CredentialStatus.ACTIVE && it.credential.replacedBy==null
            },WorkProblem.NOT_READY)
            workRequire(state.authority.policy.allows(dev.writhelm.core.workflow.Actor(delegation.binding.principalId),project,Capability.READ),WorkProblem.BLOCKED_INPUT)
        }
        Model2Inspector(work).requireAssignmentContext(subject)
        state.requireCurrentAssignmentInputs(project,subject)
        workRequire(safety.safeAccess(state,subject),WorkProblem.NOT_READY)
        val task=work.record(WorkFamily.TASK_REVISIONS,a.task);val profile=work.record(WorkFamily.PROFILES,a.profile)
        workRequire((task.payload as WTaskRevisionsPayload).value.state=="executable",WorkProblem.NOT_READY)
        fun record(row:WorkRecord)=str(WorkCodec().encodeRecord(row,true).decodeToString())
        return obj("formatVersion" to num(1),"basis" to state.basis.json(),"assignmentJSON" to record(work.record(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,subject.assignmentId))),
            "intentJSON" to record(intent),"taskJSON" to record(task),"profileJSON" to record(profile),"manifestJSON" to record(work.resolve(a.manifest)))
    }
}
