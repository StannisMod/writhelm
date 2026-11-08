// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.PresentedCredential
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.Capability
import java.util.UUID

/** Current worker checkpoint basis, not a client-authored tracker or a latest-body substitution.
 * Missing/ambiguous tracker/view/memory or invalid mandatory inputs refuse before extraction. */
internal object NativeWorkerContext {
    fun read(state: H1BoundState,key: PresentedCredential,project: UUID,controller: UUID,host: UUID,
        safety: NativeRuntimeSafety): JsonValue {
        val c=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val scope=c.scope as? CredentialScope.Assignment ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(!state.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val delegation=state.authority.delegations[scope.delegationId] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(delegation.binding.controllerId!=controller)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val subject=WAssignmentSubject(delegation.binding.assignmentId,delegation.binding.executionGeneration)
        val work=state.work.getValue(project);val a=work.assignment(subject.assignmentId);val binding=work.binding(subject)
        workRequire(a.executionGeneration==subject.generation && a.state !in setOf("completed","replaced") &&
            binding.execution.nativeHostId==host,WorkProblem.INVALID_BINDING)
        state.requireCurrentContinuation(project,subject)
        val nativeReady=a.state=="active" && a.currentIntent==null && try {
            Model2Inspector(work).requireActivation(subject,true);safety.safeAccess(state,subject)
        }catch(_: WorkFault) { false }
        val checkpoint=a.checkpoint?.let { (work.resolve(it).payload as WCheckpointsPayload).value }
        // Rebind retains an ORIGINAL checkpoint for controller-mediated continuity. That pin does
        // not grant the successor worker recursive disclosure of the old generation's memory.
        workRequire(checkpoint==null || checkpoint.assignment==subject,WorkProblem.INCOMPLETE_HANDOFF)
        val plans=work.records.values.filter { (it.payload as? WPlansPayload)?.value?.assignment==subject }
        workRequire(plans.size==1,WorkProblem.INCOMPLETE_HANDOFF)
        val planRecord=plans.single();val plan=(planRecord.payload as WPlansPayload).value
        workRequire(plan.task==a.task && (checkpoint==null || checkpoint.plan==planRecord.identity.key),WorkProblem.INVALID_REFERENCE)
        val views=work.records.values.filter { (it.payload as? WLoadedViewsPayload)?.value?.assignment==subject }
        val oldViews=views.mapNotNull { (it.payload as WLoadedViewsPayload).value.previous }.toSet()
        val view=views.filter { it.identity.key !in oldViews }.singleOrNull() ?: throw WorkFault(WorkProblem.INCOMPLETE_HANDOFF)
        workRequire((view.payload as WLoadedViewsPayload).value.discoveredGaps.isEmpty(),WorkProblem.INCOMPLETE_HANDOFF)
        val memories=work.records.values.filter { (it.payload as? WMemoryPayload)?.value?.assignment==subject }
        val oldMemories=memories.mapNotNull { (it.payload as WMemoryPayload).value.previous }.toSet()
        val memoryTips=memories.filter { it.identity.key !in oldMemories }
        workRequire(memoryTips.size<=1,WorkProblem.INCOMPLETE_HANDOFF)
        val memory=memoryTips.singleOrNull()
        memory?.let { workRequire((it.payload as WMemoryPayload).value.gaps.isEmpty(),WorkProblem.INCOMPLETE_HANDOFF) }
        val task=work.task(a.task)
        val finishedHandoff=checkpoint?.let { cp ->
            cp.handoff=="finished" && cp.result!=null && cp.retrospective!=null && cp.notification!=null && cp.pending.isEmpty() &&
                (work.record(WorkFamily.MEMORY,cp.memory).payload as WMemoryPayload).value.let { it.gaps.isEmpty() && it.pending.isEmpty() } &&
                plan.steps.all { it.state=="complete" } &&
                (work.record(WorkFamily.DELIVERIES,cp.notification).payload as WDeliveriesPayload).value.state=="durable-delivered"
        } ?: false
        val obligations=(task.criteria.map { it.id }+plan.steps.filter { it.state!="complete" }.map { it.id }).distinct().sortedBy { it.toString() }
        fun record(r: WorkRecord)=str(WorkCodec().encodeRecord(r,true).decodeToString())
        val closure=H1ProtectedReads().closure(state,c)
        val policy=Auth2Policy(state.authority,state.projection)
        val reusable=linkedMapOf<Digest,WorkBodyReference>()
        (closure.taskAndBase+closure.ownContinuation+closure.reviewGroup+closure.activeOwnRecords).forEach { resource ->
            val pair=when(resource) {
                is ProtectedResource.WorkBody -> resource.body to ProtectedResource.ImmutableWork(resource.parent)
                is ProtectedResource.MutableWorkBody -> resource.body to ProtectedResource.MutableWork(resource.parent)
                else -> null
            } ?: return@forEach
            if(policy.authorizeDisclosure(key,DisclosureRequest(project,"read_work_body",listOf(pair.second,resource)
                    .map { DisclosureUnit(it,ContentApplicability.CURRENT) }),closure) && work.bodies[pair.first.digest]?.sizeBytes==pair.first.byteLength)
                reusable[pair.first.digest]=pair.first
        }
        workRequire(reusable.size<=4096,WorkProblem.INCOMPLETE_HANDOFF)
        val instructions=NativeAssignmentPacket.render(state,project,subject).map { obj("key" to str(it.first),"value" to str(it.second)) }
        return obj("formatVersion" to num(1),"basis" to state.basis.json(),"nativeReady" to JBoolean(nativeReady),"finishedHandoff" to JBoolean(finishedHandoff),"assignmentId" to uuid(subject.assignmentId),
            "executionGeneration" to str(subject.generation.toString()),"recordGeneration" to str(a.generation.toString()),
            "bindingJSON" to str(identityCodec.encodeReference(requireNotNull(a.binding)).decodeToString()),
            "taskJSON" to str(identityCodec.encodeKey(a.task).decodeToString()),"manifestJSON" to str(identityCodec.encodeReference(a.manifest).decodeToString()),
            "profileJSON" to str(identityCodec.encodeKey(binding.profile).decodeToString()),"profileRecordJSON" to record(work.record(WorkFamily.PROFILES,binding.profile)),
            "planJSON" to record(planRecord),"loadedViewJSON" to record(view),"memoryJSON" to (memory?.let(::record) ?: JNull),
            "checkpointJSON" to (a.checkpoint?.let { record(work.resolve(it)) } ?: JNull),
            "checkpointReferenceJSON" to (a.checkpoint?.let { str(identityCodec.encodeReference(it).decodeToString()) } ?: JNull),
            "checkpointMemoryJSON" to (checkpoint?.let { record(work.record(WorkFamily.MEMORY,it.memory)) } ?: JNull),
            "checkpointSourceJSON" to (checkpoint?.let { record(work.record(WorkFamily.SOURCE_CUTS,WorkKey.Id(project,it.sourceCutId))) } ?: JNull),
            "safeContextAccess" to JBoolean(safety.safeAccess(state,subject)),
            "reusableBodies" to JArray(reusable.values.sortedBy { it.digest.hex }.map { StrictJson.decode(identityCodec.encodeBodyReference(it)) }),
            "obligationIds" to JArray(obligations.map(::uuid)),"packet" to str(StrictJson.encode(JArray(instructions),2*1024*1024).decodeToString()))
    }
}
