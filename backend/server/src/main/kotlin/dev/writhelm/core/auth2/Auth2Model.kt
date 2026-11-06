// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal enum class WorkPermission { TASK_PLAN, TASK_DISPATCH, RESULT_ACCEPT, TARGET_INTEGRATE }
internal enum class DelegationTemplate(val tag: String) { EXECUTOR("executor-v1"), REVIEWER("reviewer-v1") }
internal sealed interface CredentialScope {
    data object Project: CredentialScope
    data class Controller(val controllerId: UUID): CredentialScope
    data class Assignment(val delegationId: UUID): CredentialScope
}
internal data class ScopedCredential(val credential: Credential, val scope: CredentialScope)
internal data class ControllerBinding(val controllerId: UUID, val projectId: UUID, val principalId: UUID)
internal class WorkGrant(val enrollment: Enrollment, val generation: Long, permissions: Collection<WorkPermission>) {
    val permissions = frozenSet(permissions)
    init { require(generation >= 0 && this.permissions.size == permissions.size) }
}
internal class DelegationBinding(
    val controllerId: UUID, val projectId: UUID, val principalId: UUID, val initialCredentialId: UUID,
    val taskId: UUID, val assignmentId: UUID, val executionId: UUID, val executionGeneration: Long,
    val template: DelegationTemplate, subjectResults: Collection<WorkReference>,
) {
    val subjectResults = frozenList(subjectResults)
    init {
        require(executionGeneration >= 0 && subjectResults.size <= 256)
        require(subjectResults.all { it.identity.family == WorkFamily.RESULTS && it.identity.key.projectId == projectId })
        require(subjectResults.map { it.identity }.distinct().size == subjectResults.size)
        require(if (template == DelegationTemplate.EXECUTOR) subjectResults.isEmpty() else subjectResults.isNotEmpty())
    }
    // Ordered group equality; not role labels or a subset of subject IDs.
    override fun equals(other: Any?): Boolean = other is DelegationBinding && controllerId == other.controllerId &&
        projectId == other.projectId && principalId == other.principalId && initialCredentialId == other.initialCredentialId &&
        taskId == other.taskId && assignmentId == other.assignmentId && executionId == other.executionId &&
        executionGeneration == other.executionGeneration && template == other.template && subjectResults == other.subjectResults
    override fun hashCode(): Int = listOf(controllerId, projectId, principalId, initialCredentialId, taskId,
        assignmentId, executionId, executionGeneration, template, subjectResults).hashCode()
}
internal data class Delegation(val id: UUID, val binding: DelegationBinding, val generation: Long, val status: CredentialStatus) {
    init { require(generation >= 0) }
}
internal sealed interface Auth2Record {
    data class Principal(val id: UUID): Auth2Record
    data class Project(val binding: ProjectBinding): Auth2Record
    data class Key(val value: ScopedCredential): Auth2Record
    data class Guard(val enrollment: Enrollment, val generation: Long): Auth2Record { init { require(generation >= 0) } }
    data class Controller(val binding: ControllerBinding): Auth2Record
    data class WorkGrants(val value: WorkGrant): Auth2Record
    data class DelegationRecord(val value: Delegation): Auth2Record
}
internal fun workGrantDigest(projectId: UUID, principalId: UUID, permissions: Set<WorkPermission>): Digest =
    framedDigest("writhelm.work-grant-state.v1") {
        listOf(projectId, principalId).forEach { writeLong(it.mostSignificantBits); writeLong(it.leastSignificantBits) }
        val names = permissions.map { it.name }.sorted(); writeInt(names.size)
        names.forEach { writeFramed(encodeUtf8(it)) }
    }

internal enum class AssignmentPhase {
    RESERVED, BOUND, ACTIVE, PAUSING, PAUSED, STOPPING, STOPPED, UNKNOWN, QUARANTINED, UNBOUND, COMPLETED, REPLACED
}
internal data class BoundExecutionFacts(val principalId: UUID, val credentialId: UUID,
    val executionId: UUID, val nativeHostId: UUID, val nativeSessionId: String, val workspaceId: UUID)
internal data class RetiredReservationProof(val replacement: WorkReference,val originalDigest: Digest,val terminalDigest: Digest)
/** Facts derived from validated CORE at the SAME H as Auth2State. Never persisted as a second registry.
 * The reservation is the original immutable pinned subject, including native/profile/claim evidence.
 * Historical bindings remain supplied after rebind so terminal reconciliation does not become active.
 */
internal class AssignmentAuthorityFacts(
    val projectId: UUID, val assignmentId: UUID, val taskId: UUID, val assignmentGeneration: Long,
    val executionGeneration: Long, val controllerId: UUID, val executionId: UUID,
    val template: DelegationTemplate, reviewSubjects: Collection<WorkReference>,
    val phase: AssignmentPhase, val delegationId: UUID?, val taskRevision: WorkReference,
    val inputManifest: WorkReference, val profile: WorkReference, val nativeHostId: UUID, val nativeSessionId: String,
    val workspaceId: UUID, val boundExecution: BoundExecutionFacts?,
    val prepareIntent: WorkMutableSelector, claims: Collection<WorkMutableSelector>,
    val historicalBinding: WorkReference?, subjectProducers: Map<WorkReference, Set<UUID>>,
    val retiredReservationProof: RetiredReservationProof?=null,
) {
    init {
        if(reviewSubjects.size>256 || claims.size>256 || subjectProducers.size>256 ||
            subjectProducers.values.sumOf { it.size.toLong() }>16384) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
    }
    val reviewSubjects = frozenList(reviewSubjects)
    val claims = frozenList(claims)
    val subjectProducers = frozenMap(subjectProducers.mapValues { frozenSet(it.value) })
    init {
        require(assignmentGeneration >= 0 && executionGeneration >= 0 && claims.size <= 256)
        require(nativeSessionId.startsWith("ses") && nativeSessionId.codePointCount(0,nativeSessionId.length) in 3..256)
        encodeUtf8(nativeSessionId)
        require(taskRevision.identity.family == WorkFamily.TASK_REVISIONS && taskRevision.identity.key.projectId == projectId)
        require((taskRevision.identity.key as WorkKey.TaskRevision).taskId == taskId)
        require(inputManifest.identity.family == WorkFamily.INPUT_MANIFESTS && inputManifest.identity.key.projectId == projectId)
        require(profile.identity.family == WorkFamily.PROFILES && profile.identity.key.projectId == projectId)
        require(prepareIntent.identity.family == WorkFamily.NATIVE_INTENTS && prepareIntent.identity.key.projectId == projectId)
        require(claims.all { it.identity.family == WorkFamily.CLAIMS && it.identity.key.projectId == projectId })
        require(claims.map { it.identity }.distinct().size == claims.size && claims.isNotEmpty())
        require(reviewSubjects.size <= 256 && reviewSubjects.map { it.identity }.distinct().size == reviewSubjects.size)
        require(reviewSubjects.all { it.identity.family == WorkFamily.RESULTS && it.identity.key.projectId == projectId })
        require(if (template == DelegationTemplate.EXECUTOR) reviewSubjects.isEmpty() else reviewSubjects.isNotEmpty())
        require(subjectProducers.keys == reviewSubjects.toSet() && subjectProducers.values.all { it.isNotEmpty() })
        historicalBinding?.let {
            require(it.identity.family == WorkFamily.ASSIGNMENT_BINDINGS &&
                it.identity.key == WorkKey.AssignmentBinding(projectId, assignmentId, executionGeneration))
        }
        require((historicalBinding==null)==(boundExecution==null))
        retiredReservationProof?.let { proof ->
            require(phase==AssignmentPhase.REPLACED && historicalBinding==null && boundExecution==null && delegationId!=null)
            require(proof.replacement.identity.family==WorkFamily.ASSIGNMENT_REPLACEMENTS && proof.replacement.identity.key.projectId==projectId)
        }
        boundExecution?.let { require(it.executionId==executionId && it.nativeHostId==nativeHostId &&
            it.nativeSessionId==nativeSessionId && it.workspaceId==workspaceId) }
    }
    fun matches(b: DelegationBinding): Boolean = projectId == b.projectId && assignmentId == b.assignmentId &&
        taskId == b.taskId && executionId == b.executionId && executionGeneration == b.executionGeneration &&
        controllerId == b.controllerId && template == b.template && reviewSubjects == b.subjectResults
    fun independent(): Boolean = subjectProducers.values.none { executionId in it }
}
internal data class AssignmentSubject(val projectId: UUID, val assignmentId: UUID, val executionGeneration: Long) {
    init { require(executionGeneration >= 0) }
}
/** No callback/default allow implementation. APP constructs this only from validated same-H CORE. */
internal class AuthWorkProjection(val basis: String, facts: Collection<AssignmentAuthorityFacts>) {
    val assignments: Map<AssignmentSubject, AssignmentAuthorityFacts>
    init {
        require(basis.isNotEmpty())
        if (facts.size > 8192) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        var edges=0L
        facts.forEach {
            val added=it.claims.size.toLong()+it.reviewSubjects.size+it.subjectProducers.values.sumOf { p -> p.size.toLong() }
            if(added>16384-edges) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            edges+=added
        }
        assignments = frozenMap(facts.associateBy { AssignmentSubject(it.projectId, it.assignmentId, it.executionGeneration) })
        graph(assignments.size == facts.size)
    }
    fun assignment(b: DelegationBinding): AssignmentAuthorityFacts? =
        assignments[AssignmentSubject(b.projectId, b.assignmentId, b.executionGeneration)]?.takeIf { it.matches(b) }
}
/** Required peer effects. A candidate is NOT publishable until APP applies both at expected-old H,
 * checks CORE's resulting assignment + policy, and journals the entire auth/core/work byte diff.
 */
internal class DelegationEnrollmentEffects internal constructor(
    val validatedAt: String, val enrollment: Enrollment, val coreReadGrant: Grant, val assignment: AssignmentSubject,
    val expectedAssignmentGeneration: Long, val delegationId: UUID, val reservation: AssignmentAuthorityFacts,
) {
    val nextAssignmentGeneration: Long = Math.addExact(expectedAssignmentGeneration, 1)
    /** Mandatory APP postcondition on the joint candidate before publication. Full cross-namespace
     * diff/receipt validation remains PUBLISH's responsibility; this is not an alternative publisher. */
    fun verifyApplied(actualCorePolicy: AccessPolicy, actualWork: AuthWorkProjection) {
        graph(actualWork.basis==validatedAt)
        val grant=actualCorePolicy.snapshotGrants().singleOrNull {
            it.principalId==enrollment.principalId && it.projectId==enrollment.projectId
        }
        graph(grant?.capabilities==setOf(Capability.READ))
        val a=actualWork.assignments[assignment]; graph(a!=null)
        a!!
        graph(a.assignmentGeneration==nextAssignmentGeneration && a.delegationId==delegationId && a.phase==AssignmentPhase.RESERVED)
        graph(a.historicalBinding==null && a.boundExecution==null && a.controllerId==reservation.controllerId &&
            a.taskId==reservation.taskId && a.executionId==reservation.executionId && a.template==reservation.template &&
            a.reviewSubjects==reservation.reviewSubjects && a.taskRevision==reservation.taskRevision &&
            a.inputManifest==reservation.inputManifest && a.profile==reservation.profile &&
            a.nativeHostId==reservation.nativeHostId && a.nativeSessionId==reservation.nativeSessionId &&
            a.workspaceId==reservation.workspaceId && a.prepareIntent==reservation.prepareIntent && a.claims==reservation.claims &&
            a.subjectProducers==reservation.subjectProducers)
    }
}
