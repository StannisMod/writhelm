// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import java.nio.file.Path
import java.util.UUID

/** Service-owned original ticket retention. Call only inside the authenticated service capture;
 * possession of candidate/ref/UUID bytes is NOT authentication. No native effects are issued here. */
internal class ServiceCandidateReviewTickets(private val root:Path,private val controller:UUID,
    private val receipts:NativeCandidateReviewReceipts,private val safety:NativeRuntimeSafety,
    private val epoch:String?=null):CandidateReviewTicketIssuer {
    private val journal=CandidateReviewTicketJournal(root,controller)
    private val codec=CandidateReviewTicketCodec()
    @Synchronized override fun issue(state:H1BoundState,ticket:CandidateReviewTicket,authorizeAssembly:(String)->Unit):ByteArray {
        require(ticket.controller==controller)
        validateCandidateReviewTicketState(state,ticket,safety)
        val previous=retained(ticket.reviewId)
        if(previous==null)require(epoch!=null && receipts.freshReceiver(state,ticket,epoch))
        else require(previous==ticket)
        val plan=(state.work.getValue(ticket.project).resolve(ticket.plan).payload as WDispatchPlanPayload).value
        val observed=requireNotNull(receipts.observe(ticket.project,ticket.assemblyId,ticket.checkPlanId,plan))
        authorizeAssembly(observed.assemblyJSON)
        require(observed.candidate==ticket.candidate && observed.selected==ticket.selected &&
            observed.checks.associateBy {it.checkId}==ticket.checks.associateBy {it.checkId})
        val bytes=codec.encode(ticket)
        journal.reserve(ticket)
        require(receipts.observe(ticket.project,ticket.assemblyId,ticket.checkPlanId,plan)==observed)
        if(previous==null)require(epoch!=null && receipts.freshReceiver(state,ticket,epoch))
        authorizeAssembly(observed.assemblyJSON)
        // Publish issuance only AFTER final current-authority/evidence checks. A reserved ticket
        // left by failure/crash must not enter a mandatory packet as though issuance succeeded.
        journal.commit(ticket)
        return bytes
    }
    override fun currentReceipts(state:H1BoundState,ticket:CandidateReviewTicket):Boolean=try {
        val plan=(state.work.getValue(ticket.project).resolve(ticket.plan).payload as WDispatchPlanPayload).value
        val observed=receipts.observe(ticket.project,ticket.assemblyId,ticket.checkPlanId,plan)
        observed!=null && observed.candidate==ticket.candidate && observed.selected==ticket.selected &&
            observed.checks.associateBy {it.checkId}==ticket.checks.associateBy {it.checkId}
    }catch(_:Exception){false}
    override fun preparationReceipts(project:UUID,assemblyId:UUID,checkPlanId:UUID,plan:DispatchPlanCandidate)=
        receipts.observe(project,assemblyId,checkPlanId,plan)
    override fun retained(reviewId:UUID)=journal.retained(reviewId)
    override fun forAssignment(subject:WAssignmentSubject)=journal.forAssignment(subject)
}

/** Operational publication protocol only, not authentication/qualification or a wire API. */
internal class CandidateReviewTicketJournal(private val root:Path,private val controller:UUID) {
    private val custody=LinuxConnectedCustody(root)
    private val subjects=LinuxConnectedCustody(root.resolve("subjects"))
    private val contexts=LinuxConnectedCustody(root.resolve("contexts"))
    private val assignments=LinuxConnectedCustody(root.resolve("assignments"))
    private val tickets=LinuxConnectedCustody(root.resolve("tickets"))
    private val issued=LinuxConnectedCustody(root.resolve("issued"))
    private val codec=CandidateReviewTicketCodec()
    private fun subjectId(ticket:CandidateReviewTicket)=UUID.nameUUIDFromBytes(StrictJson.encode(obj(
        "project" to uuid(ticket.project),"candidate" to encodeWSnapshot(ticket.candidate),
        "plan" to StrictJson.decode(identityCodec.encodeReference(ticket.plan)),"checkPlan" to uuid(ticket.checkPlanId))))
    private fun assignmentId(ticket:CandidateReviewTicket)=UUID.nameUUIDFromBytes(StrictJson.encode(encodeWAssignmentSubject(ticket.assignment)))
    @Synchronized fun reserve(ticket:CandidateReviewTicket) {
        require(ticket.controller==controller)
        val bytes=codec.encode(ticket)
        // Distinct UUIDs cannot bypass an uncertain original review of this subject, reuse the
        // same native context, or repurpose one reviewer execution for another candidate.
        subjects.retain(subjectId(ticket),"dispatch",bytes)
        contexts.retain(ticket.contextId,"dispatch",bytes)
        assignments.retain(assignmentId(ticket),"dispatch",bytes)
        tickets.retain(ticket.reviewId,"dispatch",bytes)
        require(protectedRuntimeBytes(root.resolve("tickets"),root.resolve("tickets/${ticket.reviewId}.dispatch"),262144).contentEquals(bytes))
    }
    @Synchronized fun commit(ticket:CandidateReviewTicket) {
        require(ticket.controller==controller)
        val bytes=codec.encode(ticket)
        require(reserved(ticket.reviewId)==ticket)
        issued.retain(ticket.reviewId,"dispatch",bytes)
        require(retained(ticket.reviewId)==ticket)
    }
    private fun reserved(reviewId:UUID):CandidateReviewTicket?=try {
        val raw=protectedRuntimeBytes(root.resolve("tickets"),root.resolve("tickets/$reviewId.dispatch"),262144)
        codec.decode(raw).also {
            require(it.reviewId==reviewId && it.controller==controller)
            listOf("subjects" to subjectId(it),"contexts" to it.contextId,"assignments" to assignmentId(it)).forEach {(kind,id)->
                require(protectedRuntimeBytes(root.resolve(kind),root.resolve("$kind/$id.dispatch"),262144).contentEquals(raw))
            }
        }
    }catch(_:Exception){null}
    fun retained(reviewId:UUID):CandidateReviewTicket? {return try {
        val ticket=reserved(reviewId) ?: return null
        require(protectedRuntimeBytes(root.resolve("issued"),root.resolve("issued/$reviewId.dispatch"),262144).contentEquals(codec.encode(ticket)))
        ticket
    }catch(_:Exception){null}}
    fun forAssignment(subject:WAssignmentSubject):CandidateReviewTicket?=try {
        val id=UUID.nameUUIDFromBytes(StrictJson.encode(encodeWAssignmentSubject(subject)))
        val ticket=codec.decode(protectedRuntimeBytes(root.resolve("assignments"),root.resolve("assignments/$id.dispatch"),262144))
        retained(ticket.reviewId)?.takeIf {it==ticket && it.assignment==subject}
    }catch(_:Exception){null}
}
