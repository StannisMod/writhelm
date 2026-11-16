// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.util.UUID

/** Service-issued binding, not a model's reviewer claim or a qualification assertion. The issuer
 * must derive exclusions from canonical authors and independently observe assembly/check receipts.
 * This codec alone does not register a wire operation, grant a credential or dispatch a session. */
internal data class CandidateReviewTicket(val project:UUID,val reviewId:UUID,val controller:UUID,
    val plan:WorkReference,val assemblyId:UUID,val candidate:WSnapshot,val selected:List<WorkReference>,
    val checkPlanId:UUID,val checks:List<CandidateReviewCheckReceipt>,val assignment:WAssignmentSubject,
    val binding:WorkReference,val execution:WExecution,val contextId:UUID,val excludedPrincipals:List<UUID>)
internal data class CandidateReviewCheckReceipt(val checkId:String,val runId:UUID,val receiptDigest:Digest)
internal data class ObservedCandidateReviewReceipts(val assemblyJSON:String,val candidate:WSnapshot,val selected:List<WorkReference>,
    val checks:List<CandidateReviewCheckReceipt>,val evidenceDigest:Digest)
internal fun interface CandidateReviewTicketIssuer {
    fun issue(state:H1BoundState,ticket:CandidateReviewTicket,authorizeAssembly:(String)->Unit):ByteArray
    fun retained(reviewId:UUID):CandidateReviewTicket?=null
    fun forAssignment(subject:WAssignmentSubject):CandidateReviewTicket?=null
    fun currentReceipts(state:H1BoundState,ticket:CandidateReviewTicket):Boolean=false
    fun preparationReceipts(project:UUID,assemblyId:UUID,checkPlanId:UUID,plan:DispatchPlanCandidate):ObservedCandidateReviewReceipts?=null
}

internal class CandidateReviewTicketCodec {
    private fun reference(value:WorkReference)=StrictJson.decode(identityCodec.encodeReference(value))
    fun encode(ticket:CandidateReviewTicket):ByteArray {
        validate(ticket)
        return StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(ticket.project),"reviewId" to uuid(ticket.reviewId),
            "controllerId" to uuid(ticket.controller),"plan" to reference(ticket.plan),"assemblyId" to uuid(ticket.assemblyId),
            "candidate" to encodeWSnapshot(ticket.candidate),"selected" to JArray(ticket.selected.map(::reference)),
            "checkPlanId" to uuid(ticket.checkPlanId),"checks" to JArray(ticket.checks.map {obj("checkId" to str(it.checkId),
                "runId" to uuid(it.runId),"receiptDigest" to digest(it.receiptDigest))}),
            "assignment" to encodeWAssignmentSubject(ticket.assignment),"binding" to reference(ticket.binding),
            "execution" to encodeWExecution(ticket.execution),"contextId" to uuid(ticket.contextId),
            "excludedPrincipals" to JArray(ticket.excludedPrincipals.map(::uuid))),262144)
    }
    fun decode(bytes:ByteArray):CandidateReviewTicket {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,collection=4096,tokens=100000))
            .fields("formatVersion","projectId","reviewId","controllerId","plan","assemblyId","candidate","selected",
                "checkPlanId","checks","assignment","binding","execution","contextId","excludedPrincipals")
        require(f.getValue("formatVersion").long()==1L)
        fun ref(value:JsonValue)=identityCodec.decodeReference(StrictJson.encode(value))
        return CandidateReviewTicket(uuid(f.getValue("projectId")),uuid(f.getValue("reviewId")),uuid(f.getValue("controllerId")),
            ref(f.getValue("plan")),uuid(f.getValue("assemblyId")),decodeWSnapshot(f.getValue("candidate")),
            f.getValue("selected").list().map(::ref),uuid(f.getValue("checkPlanId")),f.getValue("checks").list().map {
                val c=it.fields("checkId","runId","receiptDigest")
                CandidateReviewCheckReceipt(c.getValue("checkId").string(),uuid(c.getValue("runId")),digest(c.getValue("receiptDigest")))
            },decodeWAssignmentSubject(f.getValue("assignment")),ref(f.getValue("binding")),decodeWExecution(f.getValue("execution")),
            uuid(f.getValue("contextId")),f.getValue("excludedPrincipals").list().map(::uuid)).also(::validate)
    }
    fun digest(ticket:CandidateReviewTicket)=Digest.of(encode(ticket))
    private fun validate(t:CandidateReviewTicket) {
        fun local(ref:WorkReference,family:WorkFamily)=ref.identity.family==family && ref.identity.key.projectId==t.project
        require(local(t.plan,WorkFamily.DISPATCH_PLANS) && local(t.binding,WorkFamily.ASSIGNMENT_BINDINGS))
        require(t.selected.size in 1..256 && t.selected.distinct().size==t.selected.size && t.selected.all {local(it,WorkFamily.RESULTS)})
        require(t.candidate.commit!=null && t.candidate.dirtyContentDigest==null && t.candidate.commit.algorithm==t.candidate.tree.algorithm)
        require(t.checks.size in 1..256 && t.checks.map {it.checkId}.distinct().size==t.checks.size &&
            t.checks.map {it.runId}.distinct().size==t.checks.size && t.checks.all {it.checkId.isNotBlank() && it.checkId.length<=256})
        require(t.assignment.generation>=0 && t.excludedPrincipals.size in 1..257 &&
            t.excludedPrincipals.distinct().size==t.excludedPrincipals.size && t.execution.principalId !in t.excludedPrincipals)
        require(t.execution.nativeSessionId.matches(Regex("ses_[A-Za-z0-9_-]{1,240}")))
    }
}

/** Never accept an exclusion list asserted by a planner or native reviewer callback. */
internal fun candidateReviewExcludedPrincipals(state:H1BoundState,project:UUID,controller:UUID,
    selected:List<WorkReference>):List<UUID> {
    require(selected.isNotEmpty() && selected.size<=256 && selected.distinct().size==selected.size)
    val owner=state.authority.controllers.getValue(controller)
    require(owner.projectId==project)
    val work=state.work.getValue(project)
    val authors=selected.map {reference ->
        require(reference.identity.family==WorkFamily.RESULTS && reference.identity.key.projectId==project)
        (work.resolve(reference).payload as WResultsPayload).value.producer.principalId
    }
    return (listOf(owner.principalId)+authors).distinct()
}

/** Canonical identity/independence validation, NOT an effect permit. The service issuer must ALSO
 * independently verify the assembly/check receipt bytes and original fresh-context custody before
 * retaining/dispatching the ticket. Valid codec bytes or a new UUID alone grant no access. */
internal fun validateCandidateReviewTicketState(state:H1BoundState,ticket:CandidateReviewTicket,
    safety:NativeRuntimeSafety=UnavailableNativeRuntimeSafety) {
    CandidateReviewTicketCodec().encode(ticket)
    val work=state.work.getValue(ticket.project)
    val plan=(work.resolve(ticket.plan).payload as WDispatchPlanPayload).value
    require(plan.controllerId==ticket.controller)
    val required=plan.entries.map {entry ->
        val task=if(entry.futureDependencies.isEmpty())entry.task else
            requireNotNull(dispatchClosedSelection(work,ticket.plan,entry)).task
        (chooseDispatchResult(work,DispatchFutureDependency(task,"implemented")) as DispatchResultChoice.Selected).result
    }
    require(required.size==ticket.selected.size && required.toSet()==ticket.selected.toSet())
    require(plan.candidateChecks.isNotEmpty() && plan.candidateChecks.map {it.checkId}.toSet()==ticket.checks.map {it.checkId}.toSet())
    require(candidateReviewExcludedPrincipals(state,ticket.project,ticket.controller,ticket.selected).toSet()==ticket.excludedPrincipals.toSet())
    val assignment=work.assignment(ticket.assignment.assignmentId)
    require(assignment.executionGeneration==ticket.assignment.generation && assignment.binding==ticket.binding &&
        assignment.intendedExecution.controllerId==ticket.controller && assignment.intendedExecution.template=="reviewer-v1" &&
        assignment.state in setOf("bound","active"))
    val binding=work.resolve(ticket.binding).payload as WAssignmentBindingsPayload
    require(binding.value.execution==ticket.execution && work.binding(ticket.assignment).execution==ticket.execution &&
        assignment.reviewSubjects.toSet()==ticket.selected.toSet() && assignment.reviewSubjects.size==ticket.selected.size)
    Model2Inspector(work).requireAssignmentContext(ticket.assignment)
    require(safety.safeAccess(state,ticket.assignment))
}
