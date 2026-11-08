// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import java.util.UUID

/** Private native-controller projection. The host transport separately authenticates its peer;
 * that authentication never substitutes for the presented bounded work credential/current H. */
internal class NativeRuntimeAccess(private val controller: UUID,private val host: UUID) {
    private fun envelope(state: H1BoundState,project: UUID,subject: WAssignmentSubject,binding: WorkReference,id: UUID,mayPrepare: Boolean): Pair<ConnectedEnvelope,WContinuations?> {
        val work=state.work.getValue(project);val k=WorkKey.Id(project,id)
        val record=work.records[WorkRecordIdentity(WorkFamily.CONTINUATIONS,k)]
        if(record==null) {
            workRequire(mayPrepare,WorkProblem.NOT_READY)
            val projected=ConnectedRuntimeFacts.provisionalEnvelope(work,state.basis,subject)
            workRequire(projected.continuation==k && projected.binding==binding,WorkProblem.INVALID_REFERENCE)
            return projected to null
        }
        val v=(record.payload as WContinuationsPayload).value
        if(v.assignment!=subject || v.binding!=binding)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        Model2Inspector(work).requirePairs(v)
        return ConnectedRuntimeFacts.envelope(work,k,v) to v
    }
    private fun actor(state: H1BoundState,key: dev.writhelm.core.auth.PresentedCredential,project: UUID): ScopedCredential {
        val c=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(Auth2Policy(state.authority,state.projection).sessionBinding(key,project)==null)
            throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val owner=when(val scope=c.scope) {
            is CredentialScope.Controller -> scope.controllerId
            is CredentialScope.Assignment -> state.authority.delegations[scope.delegationId]?.binding?.controllerId
            else -> null
        }
        if(owner!=controller)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        return c
    }
    fun identity(state: H1BoundState,key: dev.writhelm.core.auth.PresentedCredential,project: UUID): JsonValue {
        val c=actor(state,key,project)
        val subject=(c.scope as? CredentialScope.Assignment)?.let { scope ->
            state.authority.delegations.getValue(scope.delegationId).binding.let { WAssignmentSubject(it.assignmentId,it.executionGeneration) }
        }
        return obj("formatVersion" to num(1),"storeId" to uuid(state.storeId),"projectId" to uuid(project),
            "controllerId" to uuid(controller),"nativeHostId" to uuid(host),"principalId" to uuid(c.credential.principalId),"credentialId" to uuid(c.credential.id),
            "scope" to str(if(subject==null)"controller" else "assignment"),
            "delegationId" to ((c.scope as? CredentialScope.Assignment)?.let { uuid(it.delegationId) } ?: JNull),
            "assignmentId" to (subject?.let { uuid(it.assignmentId) } ?: JNull),
            "executionGeneration" to (subject?.let { str(it.generation.toString()) } ?: JNull))
    }
    fun sourceIdentity(state: H1BoundState,key: dev.writhelm.core.auth.PresentedCredential,project: UUID): JsonValue {
        val c=actor(state,key,project)
        if(!state.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        return identity(state,key,project)
    }
    fun status(state: H1BoundState,key: dev.writhelm.core.auth.PresentedCredential,project: UUID,
        subject: WAssignmentSubject,binding: WorkReference,continuation: UUID?,safety: NativeRuntimeSafety=UnavailableNativeRuntimeSafety): JsonValue {
        val c=actor(state,key,project)
        (c.scope as? CredentialScope.Assignment)?.let { scope ->
            val b=state.authority.delegations.getValue(scope.delegationId).binding
            if(b.assignmentId!=subject.assignmentId || b.executionGeneration!=subject.generation)
                throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        }
        // No protected assignment/intent lookup after READ loss. Receipt reconciliation can still
        // use the ordinary work route under its own policy; this endpoint grants no live execution.
        if(!state.authority.allows(c,project,Capability.READ))return held(state)
        val work=state.work[project] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val a=work.assignment(subject.assignmentId)
        if(a.intendedExecution.controllerId!=controller || a.executionGeneration!=subject.generation || a.binding!=binding)
            throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val execution=work.binding(subject).execution
        if(execution.nativeHostId!=host)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(a.state in setOf("completed","replaced"))return held(state)
        fun ready(delivered: Boolean): Boolean=try {
            state.requireCurrentContinuation(project,subject)
            Model2Inspector(work).requireActivation(subject,delivered)
            true
        } catch(_: WorkFault) { false }
        val active=a.state=="active" && a.currentIntent==null && ready(true)
        val manager=c.scope is CredentialScope.Controller && state.authority.workAllows(c,project,WorkPermission.TASK_DISPATCH)
        val preparation=manager && a.state in setOf("paused","bound") && try {
            state.requireCurrentContinuation(project,subject)
            Model2Inspector(work).let { it.requireAssignmentContext(subject);it.resolvedPairs(subject) }
            true
        } catch(_: WorkFault) { false }
        val intent=a.currentIntent?.let { (work.record(WorkFamily.NATIVE_INTENTS,it).payload as WNativeIntentsPayload).value }
        val pending=intent?.outcome=="pending"
        val dispatch=manager && pending && intent!!.operation in setOf("start","resume") && ready(false)
        val pause=manager && pending && intent!!.operation=="pause" && a.state in setOf("pausing","unknown")
        var delivery=false
        var attempt: UUID?=null
        var envelopeDigest: Digest?=null
        var freshEnvelope=false
        // Retained original identity is a READ projection, not new dispatch permission. Removing
        // TASK_DISPATCH must not implicitly revoke an already registered worker's active admission.
        if(continuation!=null && c.scope is CredentialScope.Controller) {
            val (original,v)=envelope(state,project,subject,binding,continuation,preparation)
            attempt=v?.effect?.attemptId
            envelopeDigest=Digest.of(original.bytes())
            freshEnvelope=preparation && (v==null || v.phase=="prepared" && v.stage=="environment" && v.effect.state=="not-issued")
            delivery=v!=null && dispatch && v.nativeIntent==a.currentIntent && v.stage=="delivery" && v.phase=="in-flight" && v.effect.state=="armed"
        }
        return obj("formatVersion" to num(1),"basis" to state.basis.json(),"active" to JBoolean(active),"prepare" to JBoolean(preparation),
            "dispatch" to JBoolean(dispatch),"pause" to JBoolean(pause),"delivery" to JBoolean(delivery),
            "quiescent" to JBoolean(!active && (preparation || dispatch) && safety.safeAccess(state,subject)),
            "freshEnvelope" to JBoolean(freshEnvelope),"freshPause" to JBoolean(manager && a.state=="active" && a.currentIntent==null),
            "bindingJSON" to str(identityCodec.encodeReference(binding).decodeToString()),
            "nativeHostId" to uuid(host),"nativeSessionId" to str(execution.nativeSessionId),
            "intentId" to (a.currentIntent?.let { uuid(it.id) } ?: JNull),
            "intentOperation" to (intent?.let { str(it.operation) } ?: JNull),
            "attemptId" to (attempt?.let(::uuid) ?: JNull),"envelopeDigest" to (envelopeDigest?.let(::digest) ?: JNull))
    }
    fun packet(state: H1BoundState,key: dev.writhelm.core.auth.PresentedCredential,project: UUID,
        subject: WAssignmentSubject,binding: WorkReference,continuation: UUID): JsonValue {
        val c=actor(state,key,project)
        if(c.scope !is CredentialScope.Controller || !state.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        // Reuse exactly the same subject/host/current-policy checks as the permit, at the same H.
        val status=status(state,key,project,subject,binding,continuation) as JObject
        val digest=status.entries.getValue("envelopeDigest")
        workRequire(digest!==JNull,WorkProblem.NOT_READY)
        state.requireCurrentContinuation(project,subject)
        return obj("formatVersion" to num(1),"envelopeDigest" to digest,"bindingJSON" to str(identityCodec.encodeReference(binding).decodeToString()),
            "freshEnvelope" to status.entries.getValue("freshEnvelope"),
            "nativeHostId" to uuid(host),"nativeSessionId" to status.entries.getValue("nativeSessionId"),
            "instructions" to JArray(NativeAssignmentPacket.render(state,project,subject).map { obj("key" to str(it.first),"value" to str(it.second)) }))
    }
    private fun held(state: H1BoundState)=obj("formatVersion" to num(1),"basis" to state.basis.json(),
        "active" to JBoolean(false),"prepare" to JBoolean(false),"dispatch" to JBoolean(false),"pause" to JBoolean(false),"delivery" to JBoolean(false),"quiescent" to JBoolean(false),
        "freshEnvelope" to JBoolean(false),"freshPause" to JBoolean(false),
        "bindingJSON" to JNull,"nativeHostId" to JNull,"nativeSessionId" to JNull,"intentId" to JNull,
        "intentOperation" to JNull,"attemptId" to JNull,"envelopeDigest" to JNull)
}
