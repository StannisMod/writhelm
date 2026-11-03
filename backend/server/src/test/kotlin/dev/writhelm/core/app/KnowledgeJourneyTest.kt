// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class KnowledgeJourneyTest {
    @Test fun `independent Y approval and constrained grant patch preserve X ABA generations`() {
        AppFixture().use { f ->
            val a=revision(100); val b=revision(101,"X1","Y1"); val c=revision(102,"X2")
            f.propose(a)
            settled(f.answer(f.request(packet(listOf(ReviewItem(f.next(),a.x()),ReviewItem(f.next(),a.y()),ReviewItem(f.next(),a.contextReference())),listOf(a.contextReference())))))
            f.propose(b); f.propose(c)
            f.assess(refs(a,b,c),listOf(ContinuityLink(a.contextReference(),b.contextReference()),ContinuityLink(a.contextReference(),c.contextReference())))
            val x=f.request(packet(listOf(ReviewItem(f.next(),b.x(),a.x())),listOf(a.contextReference(),b.contextReference())))
            val stale=f.request(packet(listOf(ReviewItem(f.next(),c.x(),a.x())),listOf(a.contextReference(),c.contextReference())))
            val y=f.request(packet(listOf(ReviewItem(f.next(),b.y(),a.y())),listOf(a.contextReference(),b.contextReference())))
            settled(f.answer(y)); settled(f.answer(x))
            settled(f.auth(dev.writhelm.core.auth.AuthPayload.ReplaceGrants(planner,0,f.initial.authority.grantDigest(planner,project),setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE))))
            val aba=f.request(packet(listOf(ReviewItem(f.next(),a.x(),b.x())),listOf(a.contextReference(),b.contextReference())))
            settled(f.answer(aba))
            val rejected=settled(f.answer(stale))
            assertEquals("STALE_SUBJECT",(rejected.receipt.outcome as LogicalOutcome.Rejected).code)
            assertTrue(rejected.receipt.writes.isEmpty())
            assertEquals(WorkflowResult.Failure(WorkflowProblem.UNAPPROVED_INPUT),f.app.approvalTrace(readerToken,c.x()))
        }
    }
    @Test fun `X only decision retains Y continuity and two immutable consumer manifests across reload`() {
        val path=directory(); var firstId=id(0); var secondId=id(0); val a=revision(100); val b=revision(101,"X1")
        AppFixture(path).use { f ->
            f.propose(a)
            val original=f.request(packet(listOf(ReviewItem(f.next(),a.x()),ReviewItem(f.next(),a.y()),ReviewItem(f.next(),a.contextReference())),listOf(a.contextReference())))
            settled(f.answer(original)); val assessment=f.assess(refs(a)); firstId=f.pin(listOf(a.x(),a.y()),listOf(a.contextReference()),assessment)
            f.propose(b)
            val mixed=f.assess(refs(a,b),listOf(ContinuityLink(a.y(),b.y()),ContinuityLink(a.contextReference(),b.contextReference())))
            val x=f.request(packet(listOf(ReviewItem(f.next(),b.x(),a.x())),listOf(a.contextReference(),b.contextReference())))
            settled(f.answer(x)); secondId=f.pin(listOf(b.x(),b.y()),listOf(b.contextReference()),mixed)
            val frozen=value(f.app.readManifest(readerToken,project,secondId)); assertEquals(original.id,frozen.provenance.getValue(b.y()).requestId)
            assertEquals(listOf(a.x(),a.y()),value(f.app.readManifest(readerToken,project,firstId)).clauses)
            val y=f.request(packet(listOf(ReviewItem(f.next(),b.y(),a.y())),listOf(a.contextReference(),b.contextReference())))
            settled(f.answer(y)); assertEquals(original.id,value(f.app.readManifest(readerToken,project,secondId)).provenance.getValue(b.y()).requestId)
            val patch=settled(f.auth(dev.writhelm.core.auth.AuthPayload.ReplaceGrants(planner,0,f.initial.authority.grantDigest(planner,project),setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE))))
            assertEquals(1,patch.receipt.writes.size)
            assertEquals(original.id,value(f.app.readManifest(readerToken,project,secondId)).provenance.getValue(b.y()).requestId)
            f.assess(listOf(b.x(),b.contextReference()),blocked=listOf(b.x()))
            val blocked=settled(f.core(CommandPayload.Pin(f.next(),f.next(),listOf(b.x()),listOf(b.contextReference()),emptyList(),mixed)))
            assertEquals("BLOCKED_INPUT",(blocked.receipt.outcome as LogicalOutcome.Rejected).code)
            assertEquals(listOf(b.x(),b.y()),value(f.app.readManifest(readerToken,project,secondId)).clauses)
        }
        MechanismApp.open(path,storeId).use { app ->
            assertEquals(listOf(a.x(),a.y()),value(app.readManifest(readerToken,project,firstId)).clauses)
            assertEquals(listOf(b.x(),b.y()),value(app.readManifest(readerToken,project,secondId)).clauses)
            assertContentEquals("X0\n".toByteArray(),value(app.readClause(readerToken,a.x())).located.content.bytes())
            assertContentEquals("X1\n".toByteArray(),value(app.readClause(readerToken,b.x())).located.content.bytes())
        }
    }
    @Test fun `different command key same answer is domain replay then same key is command replay`() {
        AppFixture().use { f ->
            val a=revision(100); f.propose(a)
            val request=f.request(packet(listOf(ReviewItem(f.next(),a.x()),ReviewItem(f.next(),a.contextReference())),listOf(a.contextReference())))
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Resolve(request.id,request.digest,request.packet.digest,StructuredAnswer.Approve),plannerToken))
            val first=settled(f.answer(request)); assertFalse((first.receipt.outcome as LogicalOutcome.Success).domainReplayed)
            assertEquals(listOf(a.x(),a.contextReference()),value(f.app.readResolution(ownerToken,project,request.id)).approved)
            val key=f.next(); val second=settled(f.answer(request,key)); assertTrue((second.receipt.outcome as LogicalOutcome.Success).domainReplayed)
            assertFalse(second.commandReplayed); assertTrue(second.receipt.writes.isEmpty())
            val third=settled(f.answer(request,key)); assertTrue(third.commandReplayed); assertTrue((third.receipt.outcome as LogicalOutcome.Success).domainReplayed)
            settled(f.core(CommandPayload.Revoke(planner),ownerToken))
            assertEquals(WorkflowResult.Failure(WorkflowProblem.FORBIDDEN),f.app.readClause(plannerToken,a.x()))
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Propose(a),plannerToken))
        }
    }
}
