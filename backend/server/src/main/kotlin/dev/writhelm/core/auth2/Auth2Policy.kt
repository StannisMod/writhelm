// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.CodecProblem
import dev.writhelm.core.codec.refuse
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal enum class WorkOperation(val tag: String, val permission: WorkPermission?) {
    CREATE_TASK_REVISION("create_task_revision",WorkPermission.TASK_PLAN), SET_TASK_HEAD("set_task_head",WorkPermission.TASK_PLAN),
    PIN_INPUT_MANIFEST("pin_input_manifest",WorkPermission.TASK_PLAN), CREATE_PROFILE("create_profile",WorkPermission.TASK_PLAN),
    REVISE_VERIFICATION_PLAN("revise_verification_plan",WorkPermission.TASK_PLAN), REVISE_APPLICABILITY("revise_applicability",WorkPermission.TASK_PLAN),
    UPDATE_PLAN("update_plan",WorkPermission.TASK_PLAN), RESERVE_ASSIGNMENT("reserve_assignment",WorkPermission.TASK_DISPATCH),
    BIND_ASSIGNMENT("bind_assignment",WorkPermission.TASK_DISPATCH), REQUEST_NATIVE_TRANSITION("request_native_transition",WorkPermission.TASK_DISPATCH),
    OBSERVE_NATIVE_TRANSITION("observe_native_transition",WorkPermission.TASK_DISPATCH), RECORD_RECOVERY("record_recovery",WorkPermission.TASK_DISPATCH),
    REBIND_ASSIGNMENT("rebind_assignment",WorkPermission.TASK_DISPATCH), COMPLETE_ASSIGNMENT("complete_assignment",WorkPermission.TASK_DISPATCH),
    RECORD_LOADED_VIEW("record_loaded_view",null), RECORD_MEMORY("record_memory",null), RECORD_CHECKPOINT("record_checkpoint",null),
    RECORD_RETROSPECTIVE("record_retrospective",null), SUBMIT_RESULT("submit_result",null), SUBMIT_VERIFICATION_REPORT("submit_verification_report",null),
    RECORD_EVIDENCE("record_evidence",null), RECORD_DELIVERY("record_delivery",WorkPermission.TASK_DISPATCH),
    RECORD_EVIDENCE_APPLICABILITY("record_evidence_applicability",WorkPermission.RESULT_ACCEPT), RECORD_DISPOSITION("record_disposition",WorkPermission.RESULT_ACCEPT),
    ACCEPT_RESULT("accept_result",WorkPermission.RESULT_ACCEPT), PREPARE_INTEGRATION("prepare_integration",WorkPermission.TARGET_INTEGRATE),
    OBSERVE_INTEGRATION("observe_integration",WorkPermission.TARGET_INTEGRATE), RECORD_SOURCE_CUT("record_source_cut",null),
    RECORD_CONTROLLER_CONTINUATION("record_controller_continuation",WorkPermission.TASK_PLAN),
    REPLACE_ISOLATED_ASSIGNMENT("replace_isolated_assignment",WorkPermission.TASK_DISPATCH),
    RECORD_CONDITION("record_condition",WorkPermission.TASK_PLAN), CONFIRM_CONDITION("confirm_condition",WorkPermission.TASK_PLAN),
    PREPARE_CONTINUATION("prepare_continuation",WorkPermission.TASK_DISPATCH), OBSERVE_CONTINUATION("observe_continuation",WorkPermission.TASK_DISPATCH)
}
internal enum class AdmissionMode { EVALUATE, REPLAY_ONLY }
internal class WorkAdmission internal constructor(val identity: CommandIdentity, val credentialId: UUID,
    val scope: CredentialScope, val mode: AdmissionMode, val binding: DelegationBinding?)
/** Exact subject extracted from an admitted typed CORE command, not caller authority. APP also
 * validates every referenced effect/record against this subject in CORE's reducer. */
internal data class WorkInvocation(val projectId: UUID, val operation: WorkOperation, val requestKey: UUID,
    val assignment: AssignmentSubject?, val controllerId: UUID?, val planOwner: AssignmentSubject?)

internal class Auth2Policy(private val state: Auth2State, private val work: AuthWorkProjection) {
    init { state.requireProjection(work) }
    /** Protocol resources/cursors bind the FULL immutable scope, not role/session labels. Recheck
     * at every message/page. This authenticates only the protocol identity, never a business call. */
    fun sessionBinding(presented: PresentedCredential, project: UUID): AuthSessionBinding? {
        val c=state.authenticate(presented,project) ?: return null
        val d=(c.scope as? CredentialScope.Assignment)?.let { state.delegations[it.delegationId] }
        if(d!=null) {
            val a=work.assignment(d.binding) ?: return null
            if(a.delegationId!=d.id || a.historicalBinding==null || !a.independent() || !state.boundIdentityMatches(a,d)) return null
        }
        return AuthSessionBinding(state.storeId,c.credential.principalId,project,c.scope)
    }
    fun matchesSession(presented: PresentedCredential, binding: AuthSessionBinding): Boolean =
        sessionBinding(presented,binding.projectId)==binding
    fun authorizeWork(presented: PresentedCredential, call: WorkInvocation): WorkAdmission? {
        val c=state.authenticate(presented,call.projectId) ?: return null
        return authorizeWorkCredential(c,call)
    }
    /** Only a required historian at the original H may use retained canonical provenance instead of
     * reauthenticating an unavailable historical secret. Not a live credential-ID authentication API. */
    internal fun authorizeHistoricalWork(credentialId: UUID, call: WorkInvocation): WorkAdmission? {
        val c=state.retainedCredential(credentialId,call.projectId) ?: return null
        return authorizeWorkCredential(c,call)
    }
    private fun authorizeWorkCredential(c: ScopedCredential, call: WorkInvocation): WorkAdmission? {
        val delegated=c.scope as? CredentialScope.Assignment
        if(delegated==null) {
            val permission=call.operation.permission ?: return null
            if(!state.workAllows(c,call.projectId,permission)) return null
            if(call.operation==WorkOperation.RECORD_CONTROLLER_CONTINUATION &&
                (state.controller(c,call.projectId)!=call.controllerId || call.controllerId==null || call.assignment!=null)) return null
            if(call.operation==WorkOperation.RESERVE_ASSIGNMENT &&
                (call.controllerId==null || state.controller(c,call.projectId)!=call.controllerId)) return null
            // A planner's task plan has null owner; cannot adopt a worker's plan through TASK_PLAN.
            if(call.operation==WorkOperation.UPDATE_PLAN && call.planOwner!=null) return null
            return admission(c,call,AdmissionMode.EVALUATE,null)
        }
        val d=state.delegations[delegated.delegationId] ?: return null
        val b=d.binding
        if(call.assignment!=AssignmentSubject(b.projectId,b.assignmentId,b.executionGeneration)) return null
        val a=work.assignment(b) ?: return null
        if(a.delegationId!=d.id || a.historicalBinding==null || !a.independent() || !state.boundIdentityMatches(a,d)) return null
        val allowed=when(call.operation) {
            WorkOperation.SUBMIT_RESULT -> b.template==DelegationTemplate.EXECUTOR
            WorkOperation.SUBMIT_VERIFICATION_REPORT -> b.template==DelegationTemplate.REVIEWER
            WorkOperation.CONFIRM_CONDITION -> b.template==DelegationTemplate.REVIEWER
            WorkOperation.RECORD_EVIDENCE, WorkOperation.RECORD_LOADED_VIEW, WorkOperation.RECORD_MEMORY,
            WorkOperation.RECORD_CHECKPOINT, WorkOperation.RECORD_RETROSPECTIVE, WorkOperation.RECORD_SOURCE_CUT -> true
            WorkOperation.UPDATE_PLAN -> call.planOwner==call.assignment
            else -> false
        }
        if(!allowed) return null
        val activeOnly=call.operation in setOf(WorkOperation.SUBMIT_RESULT,WorkOperation.SUBMIT_VERIFICATION_REPORT,WorkOperation.RECORD_EVIDENCE,WorkOperation.CONFIRM_CONDITION)
        val mode=if(a.phase==AssignmentPhase.ACTIVE || (!activeOnly && a.phase in continuationPhases)) AdmissionMode.EVALUATE else AdmissionMode.REPLAY_ONLY
        return admission(c,call,mode,b)
    }
    private fun admission(c: ScopedCredential, call: WorkInvocation, mode: AdmissionMode, b: DelegationBinding?) =
        WorkAdmission(CommandIdentity(c.credential.principalId,call.projectId,call.operation.tag,call.requestKey),c.credential.id,c.scope,mode,b)

    /** Only P/C knowledge writes; MCP has the original four-op closed allowlist. */
    fun authorizeLegacyCommand(presented: PresentedCredential, project: UUID, operation: String, mcp: Boolean=false): ScopedCredential? {
        val c=state.authenticate(presented,project) ?: return null
        if(c.scope is CredentialScope.Assignment) return null
        if(mcp && operation !in setOf("propose_revision","request_approval","record_assessment","pin_consumer")) return null
        val required=requiredCapability(operation) ?: return null
        if(operation=="read") return null // No broad READ-snapshot admission escape.
        if(!state.allows(c,project,required)) return null
        if(required in setOf(Capability.MANAGE,Capability.APPROVE) && state.projects[project]?.ownerPrincipalId!=c.credential.principalId) return null
        return c
    }

    /** Subject-sensitive protected disclosure. The closure is DERIVED from validated same-H CORE
     * and core withdrawal/applicability facts before reading/serializing protected payloads. */
    fun authorizeDisclosure(presented: PresentedCredential, request: DisclosureRequest, closure: DerivedDisclosureClosure): Boolean {
        val c=state.authenticate(presented,request.projectId) ?: return false
        return authorizeDisclosure(c,request,closure)
    }
    /** Original-H audit only; not a credential-ID authentication endpoint. */
    internal fun authorizeHistoricalDisclosure(credentialId: UUID, request: DisclosureRequest, closure: DerivedDisclosureClosure): Boolean {
        val c=state.retainedCredential(credentialId,request.projectId) ?: return false
        return authorizeDisclosure(c,request,closure)
    }
    private fun authorizeDisclosure(c: ScopedCredential, request: DisclosureRequest, closure: DerivedDisclosureClosure): Boolean {
        if(closure.basis!=state.basis || closure.projectId!=request.projectId || request.units.isEmpty()) return false
        if(request.operation !in protectedReads) return false
        if(!state.allows(c,request.projectId,Capability.READ)) return false
        if(request.units.any { it.applicability!=ContentApplicability.CURRENT || it.resource.projectId!=request.projectId }) return false
        val ctl=state.controller(c,request.projectId)
        if(request.controllerId!=null && (ctl!=request.controllerId || c.scope !is CredentialScope.Controller)) return false
        val delegated=c.scope as? CredentialScope.Assignment
        if(delegated==null) {
            if(request.units.any { it.resource is ProtectedResource.ControllerContinuation && it.resource.controllerId!=ctl }) return false
            if(request.units.any { unit ->
                val ref=(unit.resource as? ProtectedResource.ImmutableWork)?.reference
                ref?.identity?.family==WorkFamily.CONTROLLER_CONTINUATIONS &&
                    (ref.identity.key as WorkKey.ControllerContinuation).controllerId!=ctl
            }) return false
            return true
        }
        if(request.operation in legacyLists || request.operation=="list_work_records") return false
        val d=state.delegations[delegated.delegationId] ?: return false; val b=d.binding
        if(closure.binding!=b) return false
        val a=work.assignment(b) ?: return false
        if(a.historicalBinding==null || a.delegationId!=d.id || !a.independent() || !state.boundIdentityMatches(a,d)) return false
        val terminal=a.phase in setOf(AssignmentPhase.COMPLETED,AssignmentPhase.REPLACED)
        if(terminal && request.operation !in setOf("read_results","read_verification_reports","read_work_body")) return false
        if(terminal && request.operation=="read_results" && b.template!=DelegationTemplate.EXECUTOR) return false
        if(terminal && request.operation=="read_verification_reports" && b.template!=DelegationTemplate.REVIEWER) return false
        val active=a.phase==AssignmentPhase.ACTIVE
        val ordinary=when {
            terminal -> emptySet()
            active -> closure.taskAndBase + closure.ownContinuation + closure.activeOwnRecords + if(b.template==DelegationTemplate.REVIEWER) closure.reviewGroup else emptySet()
            a.phase in continuationPhases -> closure.taskAndBase+closure.ownContinuation
            else -> closure.exactTask+closure.ownContinuation
        }
        val owned=closure.ownedResults.filter { it.principalId==b.principalId && it.assignment==AssignmentSubject(b.projectId,b.assignmentId,b.executionGeneration) && it.executionId==b.executionId &&
            it.reference.identity.family==if(b.template==DelegationTemplate.EXECUTOR) WorkFamily.RESULTS else WorkFamily.VERIFICATION_REPORTS }
        return request.units.all { unit ->
            when(val r=unit.resource) {
                is ProtectedResource.WorkBody -> {
                    val own=owned.any { it.reference==r.parent && r.body in it.intrinsicBodies }
                    own || (!terminal && r in ordinary)
                }
                is ProtectedResource.ImmutableWork -> owned.any { it.reference==r.reference } || r in ordinary
                else -> r in ordinary
            }
        }
    }
    companion object {
        private val continuationPhases=setOf(AssignmentPhase.PAUSING,AssignmentPhase.PAUSED,AssignmentPhase.STOPPING,AssignmentPhase.STOPPED)
        private val legacyLists=setOf("list_projects","list_documents","list_revisions","list_requests","list_manifests")
        private val protectedReads=legacyLists+setOf("read_revision","read_clause","read_context","read_approval_trace",
            "read_request","read_resolution","read_manifest","read_review_bundle","read_work_body","list_work_records")+
            WorkFamily.entries.map { "read_"+it.tag.replace('-','_') }
    }
}
internal data class AuthSessionBinding(val storeId: UUID, val principalId: UUID, val projectId: UUID, val scope: CredentialScope)
internal enum class ContentApplicability { CURRENT, WITHDRAWN, BLOCKED, UNAVAILABLE }
internal sealed interface ProtectedResource {
    val projectId: UUID
    data class Knowledge(val reference: KnowledgeReference): ProtectedResource { override val projectId get()=reference.revision.document.projectId }
    data class Revision(val key: RevisionKey, val digest: Digest): ProtectedResource { override val projectId get()=key.document.projectId }
    data class LegacyRecord(override val projectId: UUID, val family: LegacyReadFamily, val id: UUID, val digest: Digest): ProtectedResource
    data class ImmutableWork(val reference: WorkReference): ProtectedResource { override val projectId get()=reference.identity.key.projectId }
    data class MutableWork(val selector: WorkMutableSelector): ProtectedResource { override val projectId get()=selector.identity.key.projectId }
    data class WorkBody(val parent: WorkReference, val body: WorkBodyReference): ProtectedResource { override val projectId get()=parent.identity.key.projectId }
    data class MutableWorkBody(val parent: WorkMutableSelector, val body: WorkBodyReference): ProtectedResource { override val projectId get()=parent.identity.key.projectId }
    data class ControllerContinuation(override val projectId: UUID, val controllerId: UUID, val reference: WorkReference): ProtectedResource
}
internal enum class LegacyReadFamily { REQUEST, RESOLUTION, MANIFEST, REVIEW_BUNDLE, APPROVAL_TRACE }
internal data class DisclosureUnit(val resource: ProtectedResource, val applicability: ContentApplicability)
internal class DisclosureRequest(val projectId: UUID, val operation: String, units: Collection<DisclosureUnit>, val controllerId: UUID?=null) {
    init { if(units.size>4096)refuse(CodecProblem.RESOURCE_UNAVAILABLE) }
    val units=frozenList(units)
}
internal class OwnedResultFacts(val reference: WorkReference, val principalId: UUID, val assignment: AssignmentSubject,
    val executionId: UUID, intrinsicBodies: Collection<WorkBodyReference>) {
    val intrinsicBodies=frozenSet(intrinsicBodies)
}
/** Exact returned material, not a filename whitelist or an initial loaded-manifest limit. CORE/APP
 * derives taskAndBase from pinned base + declared read scope, including permitted additional reads.
 * Every returned fragment/bundle member needs its own unit; closure failure denies the WHOLE read.
 */
internal class DerivedDisclosureClosure(val basis: String, val projectId: UUID, val binding: DelegationBinding?,
    exactTask: Collection<ProtectedResource>, taskAndBase: Collection<ProtectedResource>,
    ownContinuation: Collection<ProtectedResource>, reviewGroup: Collection<ProtectedResource>, ownedResults: Collection<OwnedResultFacts>,
    activeOwnRecords: Collection<ProtectedResource> = emptyList()) {
    init {
        if(listOf(exactTask,taskAndBase,ownContinuation,reviewGroup,activeOwnRecords).any { it.size>4096 } || ownedResults.size>4096)
            refuse(CodecProblem.RESOURCE_UNAVAILABLE)
    }
    val exactTask=frozenSet(exactTask); val taskAndBase=frozenSet(taskAndBase); val ownContinuation=frozenSet(ownContinuation)
    val reviewGroup=frozenSet(reviewGroup); val ownedResults=frozenList(ownedResults)
    val activeOwnRecords=frozenSet(activeOwnRecords)
    init { require(listOf(exactTask,taskAndBase,ownContinuation,reviewGroup,activeOwnRecords).all { it.all { r -> r.projectId==projectId } }) }
}

internal enum class ReceiptResolution { MATCH, IDENTITY_CONFLICT, DENIED_MISS, EVALUATE, DENIED_PROOF }
/** Publisher supplies validated retained original scope proof and fingerprint, not outcome-era grants.
 * No evaluator or CAS callback exists in this replay-only resolver. */
internal data class RetainedScopeProof(val identity: CommandIdentity, val fingerprintVersion: Int, val fingerprint: Digest,
    val binding: DelegationBinding?)
internal fun resolveScopedReceipt(admission: WorkAdmission, suppliedFingerprint: Digest, retained: RetainedScopeProof?): ReceiptResolution {
    if(retained==null) return if(admission.mode==AdmissionMode.REPLAY_ONLY) ReceiptResolution.DENIED_MISS else ReceiptResolution.EVALUATE
    if(retained.identity!=admission.identity || retained.binding!=admission.binding || retained.fingerprintVersion!=3) return ReceiptResolution.DENIED_PROOF
    return if(retained.fingerprint==suppliedFingerprint) ReceiptResolution.MATCH else ReceiptResolution.IDENTITY_CONFLICT
}
