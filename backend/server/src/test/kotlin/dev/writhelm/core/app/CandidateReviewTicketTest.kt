// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.util.UUID
import java.nio.file.Files
import dev.writhelm.service.NativeCandidateReviewReceipts
import dev.writhelm.service.ServiceCandidateReviewTickets
import dev.writhelm.service.CandidateReviewTicketJournal
import dev.writhelm.service.NativeRuntimeEndpoint
import dev.writhelm.service.ServiceFault
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Synthetic ticket vectors only, not a service-issued review or native execution qualification. */
class CandidateReviewTicketTest {
    private fun ticket():CandidateReviewTicket {
        val f=JointFixture();f.activateWithAttributedFixtureObservations()
        val project=f.reserveCommand().projectId;val work=f.base.work.getValue(project)
        val subject=WAssignmentSubject(f.assignment,0)
        fun ref(family:WorkFamily)=WorkReference(WorkRecordIdentity(family,WorkKey.Id(project,UUID.randomUUID())),Digest.of(family.toString().toByteArray()))
        return CandidateReviewTicket(project,UUID.randomUUID(),f.controller,ref(WorkFamily.DISPATCH_PLANS),UUID.randomUUID(),
            f.snapshot,listOf(ref(WorkFamily.RESULTS)),UUID.randomUUID(),
            listOf(CandidateReviewCheckReceipt("mandatory-check",UUID.randomUUID(),Digest.of("synthetic retained receipt".toByteArray()))),
            subject,work.assignment(f.assignment).binding!!,work.binding(subject).execution,UUID.randomUUID(),
            listOf(f.base.authority.controllers.getValue(f.controller).principalId))
    }
    @Test fun roundTripBindsCandidateChecksReviewerAndContext() {
        val ticket=ticket();val codec=CandidateReviewTicketCodec()
        assertEquals(ticket,codec.decode(codec.encode(ticket)))
        assertNotEquals(codec.digest(ticket),codec.digest(ticket.copy(contextId=UUID.randomUUID())))
        assertNotEquals(codec.digest(ticket),codec.digest(ticket.copy(checks=ticket.checks.map {it.copy(receiptDigest=Digest.of("different receipt".toByteArray()))})))
        assertNotEquals(codec.digest(ticket),codec.digest(ticket.copy(candidate=ticket.candidate.copy(tree=WorkGitOid("sha1","9".repeat(40))))))
    }
    @Test fun selfReviewForeignSubjectsMissingChecksAndDirtyCandidateRefuse() {
        val t=ticket();val codec=CandidateReviewTicketCodec()
        assertFails {codec.encode(t.copy(excludedPrincipals=t.excludedPrincipals+t.execution.principalId))}
        assertFails {codec.encode(t.copy(project=UUID.randomUUID()))}
        assertFails {codec.encode(t.copy(selected=emptyList()))}
        assertFails {codec.encode(t.copy(selected=t.selected+t.selected))}
        assertFails {codec.encode(t.copy(checks=emptyList()))}
        assertFails {codec.encode(t.copy(checks=t.checks+t.checks))}
        assertFails {codec.encode(t.copy(candidate=t.candidate.copy(dirtyContentDigest=Digest.of("dirty".toByteArray()))))}
        assertFails {codec.encode(t.copy(excludedPrincipals=emptyList()))}
    }
    @Test fun closedCodecRejectsCallerAddedAuthorityAndDuplicateFields() {
        val codec=CandidateReviewTicketCodec();val raw=codec.encode(ticket())
        val parsed=StrictJson.decode(raw) as JObject
        assertFails {codec.decode(StrictJson.encode(JObject(parsed.entries+("integrationAllowed" to JBoolean(true)))))}
        val duplicate=raw.toString(Charsets.UTF_8).replaceFirst("{","{\"formatVersion\":1,")
        assertFails {codec.decode(duplicate.toByteArray())}
    }
    @Test fun validTicketBytesDoNotCreateCanonicalPlanOrGrantReviewerAccess() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations()
        val t=ticket();val decoded=CandidateReviewTicketCodec().decode(CandidateReviewTicketCodec().encode(t))
        assertFails {validateCandidateReviewTicketState(f.base,decoded)}
        assertTrue(f.base.work.getValue(t.project).records.values.none {it.payload is WDispatchPlanPayload})
        assertFails {candidateReviewExcludedPrincipals(f.base,t.project,t.controller,t.selected)}
        assertFails {candidateReviewExcludedPrincipals(f.base,UUID.randomUUID(),t.controller,t.selected)}
    }
    @Test fun issuerDoesNotRetainAnUnboundOrUnqualifiedTicket() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations();val t=ticket()
        val root=Files.createTempDirectory("writhelm-review-issuance-")
        val issuer=ServiceCandidateReviewTickets(root,t.controller,NativeCandidateReviewReceipts(root.resolve("missing-native")),UnavailableNativeRuntimeSafety)
        assertNull(issuer.retained(t.reviewId))
        assertFails {issuer.issue(f.base,t){error("Unqualified ticket must not reach assembly authorization")}}
        assertNull(issuer.retained(t.reviewId))
        assertTrue(Files.list(root.resolve("tickets")).use {it.findAny().isEmpty})
    }
    @Test fun authenticatedEndpointNeverIssuesForWorkerInvalidCredentialOrUnqualifiedController() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations();val t=ticket()
        val store=Files.createTempDirectory("writhelm-review-api-");var issued=0
        val options=H1Options(UnavailableH1RuntimeFacts,candidateReviewTickets={CandidateReviewTicketIssuer {_,_,_->issued++;error("must remain held")}})
        H1Application.initialize(store,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use {app ->
            NativeRuntimeEndpoint(app,t.project,t.controller,t.execution.nativeHostId,ByteArray(32){1}).use {endpoint ->
                val request=StrictJson.encode(obj("formatVersion" to num(1),"operation" to str("candidate-review-ticket"),
                    "projectId" to uuid(t.project),"ticketJSON" to str(CandidateReviewTicketCodec().encode(t).toString(Charsets.UTF_8))))
                for(token in listOf(plannerToken,token(f.workerKey),"invalid credential")) {
                    assertFailsWith<ServiceFault> {endpoint.readCandidate(token,request)}
                    val report=StrictJson.encode(obj("formatVersion" to num(1),"operation" to str("candidate-review-report"),
                        "projectId" to uuid(t.project),"reviewId" to uuid(t.reviewId),
                        "reportRefJSON" to str(identityCodec.encodeReference(t.selected.single()).toString(Charsets.UTF_8))))
                    assertFailsWith<ServiceFault> {endpoint.readCandidate(token,report)}
                }
                assertEquals(0,issued)
            }
        }
    }
    @Test fun interruptedIssuanceIsNotVisibleToPacketAndExactCommittedTicketSurvivesReopen() {
        val ticket=ticket();val root=Files.createTempDirectory("writhelm-review-publication-")
        val original=CandidateReviewTicketJournal(root,ticket.controller)
        original.reserve(ticket)
        assertNull(original.retained(ticket.reviewId));assertNull(original.forAssignment(ticket.assignment))
        val reopened=CandidateReviewTicketJournal(root,ticket.controller)
        assertNull(reopened.retained(ticket.reviewId))
        val reserved=Files.readAllBytes(root.resolve("tickets/${ticket.reviewId}.dispatch"))
        assertFails {reopened.reserve(ticket.copy(reviewId=UUID.randomUUID()))}
        assertContentEquals(reserved,Files.readAllBytes(root.resolve("tickets/${ticket.reviewId}.dispatch")))
        reopened.reserve(ticket);reopened.commit(ticket)
        val restored=CandidateReviewTicketJournal(root,ticket.controller)
        assertEquals(ticket,restored.retained(ticket.reviewId));assertEquals(ticket,restored.forAssignment(ticket.assignment))
        restored.commit(ticket)
        val witness=root.resolve("issued/${ticket.reviewId}.dispatch")
        Files.writeString(witness,"unknown issuance outcome")
        assertNull(restored.retained(ticket.reviewId));assertNull(restored.forAssignment(ticket.assignment))
        assertFails {restored.commit(ticket)}
        assertEquals("unknown issuance outcome",Files.readString(witness))
    }
}
