// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.ByteSpan
import dev.writhelm.core.knowledge.ClauseId
import dev.writhelm.core.knowledge.ClauseSpan
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.DocumentKey
import dev.writhelm.core.knowledge.DocumentRevision
import dev.writhelm.core.knowledge.RevisionKey
import dev.writhelm.core.knowledge.StructureIndex
import dev.writhelm.core.knowledge.Utf8Content
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ApprovalWorkflowTest {
    @Test
    fun `WF-02 proposal and pending request grant no authority then exact owner approval succeeds`() {
        val f = WorkflowFixture()
        val empty = f.state
        f.add(f.a)
        val request = f.pending(packet(listOf(ReviewItem(f.id(), f.a.x()), ReviewItem(f.id(), f.a.y()),
            ReviewItem(f.id(), f.a.contextReference())), listOf(f.a.contextReference())))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, f.a.x()))
        bad(WorkflowProblem.NOT_FOUND, f.state.readResolution(reader, project, request.id))
        assertEquals("X0\n", good(f.state.readClause(reader, f.a.x())).located.content.text())
        bad(WorkflowProblem.INVALID_REFERENCE, empty.readClause(reader, f.a.x()))
        val pendingState = f.state
        val resolution = f.answer(request)
        assertEquals(3, resolution.approved.size)
        assertFailsWith<UnsupportedOperationException> { (resolution.approved as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (request.generations as MutableMap<SubjectKey, Long>).clear() }
        assertEquals(owner, resolution.actor)
        assertEquals(request.id, good(f.state.approvalTrace(reader, f.a.y())).requestId)
        assertEquals(owner.principalId, good(f.state.approvalTrace(reader, f.a.x())).approvedBy)
        bad(WorkflowProblem.UNAPPROVED_INPUT, pendingState.approvalTrace(reader, f.a.x()))
    }

    @Test
    fun `WF-01 current capabilities are independent and project scoped`() {
        val f = WorkflowFixture()
        listOf(reader, outsider).forEach { bad(WorkflowProblem.FORBIDDEN, f.state.propose(it, f.a)) }
        f.add(f.a, contributor)
        val p = packet(listOf(ReviewItem(f.id(), f.a.x())), listOf(f.a.contextReference()))
        listOf(contributor, reader, outsider).forEach {
            bad(WorkflowProblem.FORBIDDEN, f.state.request(it, project, f.id(), p))
        }
        val r = f.pending(p)
        listOf(planner, contributor, reader, outsider).forEach {
            bad(WorkflowProblem.FORBIDDEN, f.state.resolve(it, project, r.id, r.digest, p.digest, StructuredAnswer.Approve))
        }
        bad(WorkflowProblem.FORBIDDEN,
            f.state.resolve(owner, otherProject, r.id, r.digest, p.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.FORBIDDEN, f.state.readRequest(outsider, project, r.id))
        bad(WorkflowProblem.NOT_FOUND, f.state.readRequest(foreignOwner, otherProject, r.id))
    }

    @Test
    fun `WF-02 foreign missing and tampered references are rejected without changing state`() {
        val f = WorkflowFixture().baseline()
        val b = doc(31)
        f.add(b)
        val foreign = DocumentRevision(RevisionKey(DocumentKey(otherProject, UUID(0, 500)), UUID(0, 501)),
            Utf8Content.fromText("F"), StructureIndex(listOf(ClauseSpan(ClauseId("F"), ByteSpan(0, 1))), emptyList()))
        f.add(foreign, foreignOwner)
        val ref = foreign.clauseReference(ClauseId("F"))
        bad(WorkflowProblem.FOREIGN_REFERENCE,
            f.state.request(planner, project, f.id(), packet(listOf(ReviewItem(f.id(), ref)), emptyList())))
        val wrong = b.x().copy(contentDigest = Digest.parse("0".repeat(64)))
        bad(WorkflowProblem.INVALID_REFERENCE,
            f.state.request(planner, project, f.id(), packet(listOf(ReviewItem(f.id(), wrong, f.a.x())),
                listOf(f.a.contextReference(), b.contextReference()))))
        val absent = b.x().copy(revision = b.key.copy(revisionId = UUID(0, 999)))
        bad(WorkflowProblem.INVALID_REFERENCE,
            f.state.request(planner, project, f.id(), packet(listOf(ReviewItem(f.id(), absent, f.a.x())),
                listOf(f.a.contextReference(), b.contextReference()))))
        assertEquals(f.initial.id, good(f.state.approvalTrace(reader, f.a.x())).requestId)
    }

    @Test
    fun `WF-03 exact digests are required before answering and failed answers do not settle`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val r = f.pendingX(b)
        val wrong = Digest.parse("f".repeat(64))
        bad(WorkflowProblem.DIGEST_MISMATCH,
            f.state.resolve(owner, project, r.id, wrong, r.packet.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.DIGEST_MISMATCH,
            f.state.resolve(owner, project, r.id, r.digest, wrong, StructuredAnswer.Approve))
        bad(WorkflowProblem.NOT_FOUND, f.state.readResolution(reader, project, r.id))
        f.answer(r)
        assertEquals(r.id, good(f.state.approvalTrace(reader, b.x())).requestId)
    }

    @Test
    fun `WF-03 reject and anchored changes settle without approving`() {
        for (changes in listOf(false, true)) {
            val f = WorkflowFixture().baseline()
            val (b, _) = f.prepareB()
            val r = f.pendingX(b)
            val answer = if (changes) StructuredAnswer.ChangesRequired(mapOf(r.packet.items.single().id to "Change this item"))
                else StructuredAnswer.Reject
            val resolved = f.answer(r, answer)
            assertEquals(emptyList(), resolved.approved)
            assertSame(resolved, good(f.state.readResolution(reader, project, r.id)))
            bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.x()))
            assertEquals(f.initial.id, good(f.state.approvalTrace(reader, f.a.x())).requestId)
        }
    }

    @Test
    fun `WF-03 changes have no empty or unknown free-text slot`() {
        assertFailsWith<IllegalArgumentException> { StructuredAnswer.ChangesRequired(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { StructuredAnswer.ChangesRequired(mapOf(UUID(0, 1) to "  ")) }
        assertFailsWith<IllegalArgumentException> { StructuredAnswer.ChangesRequired(mapOf(UUID(0, 1) to "\uD800")) }
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val r = f.pendingX(b)
        bad(WorkflowProblem.UNKNOWN_COMMENT_ITEM,
            f.state.resolve(owner, project, r.id, r.digest, r.packet.digest,
                StructuredAnswer.ChangesRequired(mapOf(UUID(0, 999) to "Unknown item"))))
        f.answer(r)
    }

    @Test
    fun `WF-03 matching retry returns the original result but cannot replace actor or answer`() {
        val f = WorkflowFixture(secondOwner = true).baseline()
        val r = f.initial
        val original = good(f.state.readResolution(reader, project, r.id))
        val retry = good(f.state.resolve(owner, project, r.id, r.digest, r.packet.digest, StructuredAnswer.Approve))
        assertSame(f.state, retry.state)
        assertSame(original, retry.value)
        assertTrue(retry.replayed)
        bad(WorkflowProblem.ALREADY_RESOLVED,
            f.state.resolve(owner, project, r.id, r.digest, r.packet.digest, StructuredAnswer.Reject))
        bad(WorkflowProblem.ALREADY_RESOLVED,
            f.state.resolve(coOwner, project, r.id, r.digest, r.packet.digest, StructuredAnswer.Approve))
    }

    @Test
    fun `WF-02 known information gaps block approval not a disclosed uncertainty`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val p = packet(listOf(ReviewItem(f.id(), b.x(), f.a.x())),
            listOf(f.a.contextReference(), b.contextReference()), gaps = listOf("Missing impact analysis"))
        val r = f.pending(p)
        bad(WorkflowProblem.INCOMPLETE_PACKET,
            f.state.resolve(owner, project, r.id, r.digest, p.digest, StructuredAnswer.Approve))
        assertEquals(StructuredAnswer.Reject, f.answer(r, StructuredAnswer.Reject).answer)
        val valid = f.pendingX(b) // Its packet still discloses an unmeasured-runtime uncertainty.
        f.answer(valid)
    }

    @Test
    fun `WF-04 two conflicting X requests have one winner on the current state`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val first = f.pendingX(b)
        val second = f.pendingX(b)
        f.answer(first)
        bad(WorkflowProblem.STALE_SUBJECT,
            f.state.resolve(owner, project, second.id, second.digest, second.packet.digest, StructuredAnswer.Approve))
        assertEquals(first.id, good(f.state.approvalTrace(reader, b.x())).requestId)
        assertEquals(f.initial.id, good(f.state.approvalTrace(reader, b.y())).requestId)
    }

    @Test
    fun `WF-04 independent Y transition is not invalidated by approving X`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB(doc(31, "X1", "Y1"), carryY = false)
        val x = f.pendingX(b)
        val y = f.pending(packet(listOf(ReviewItem(f.id(), b.y(), f.a.y())),
            listOf(f.a.contextReference(), b.contextReference())))
        f.answer(x)
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.y()))
        f.answer(y)
        assertEquals(y.id, good(f.state.approvalTrace(reader, b.y())).requestId)
        assertEquals(f.initial.id, good(f.state.approvalTrace(reader, f.a.y())).requestId)
    }

    @Test
    fun `WF-04 ABA head movement still rejects the old generation and preserves original proof`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val oldPending = f.pendingX(b)
        f.answer(f.pendingX(b))
        val back = f.pending(packet(listOf(ReviewItem(f.id(), f.a.x(), b.x())),
            listOf(f.a.contextReference(), b.contextReference())))
        f.answer(back)
        bad(WorkflowProblem.STALE_SUBJECT,
            f.state.resolve(owner, project, oldPending.id, oldPending.digest, oldPending.packet.digest, StructuredAnswer.Approve))
        assertEquals(f.initial.id, good(f.state.approvalTrace(reader, f.a.x())).requestId)
        assertEquals(f.initial.id, good(f.state.readResolution(reader, project, f.initial.id)).requestId)
    }

    @Test
    fun `WF-01 current access is rechecked on replay and historical reads`() {
        val f = WorkflowFixture(secondOwner = true).baseline()
        val manifest = f.pin(listOf(f.a.x(), f.a.y()), listOf(f.a.contextReference()), f.baselineAssessment)
        f.state = good(f.state.revokeAccess(coOwner, project, owner.principalId)).state
        bad(WorkflowProblem.FORBIDDEN,
            f.state.resolve(owner, project, f.initial.id, f.initial.digest, f.initial.packet.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.FORBIDDEN, f.state.readManifest(owner, project, manifest.id))
        assertEquals(owner, good(f.state.readResolution(coOwner, project, f.initial.id)).actor)
        bad(WorkflowProblem.LAST_AUTHORITY, f.state.revokeAccess(coOwner, project, coOwner.principalId))
        bad(WorkflowProblem.FORBIDDEN, f.state.revokeAccess(planner, project, reader.principalId))
        f.state = good(f.state.revokeAccess(coOwner, project, reader.principalId)).state
        bad(WorkflowProblem.FORBIDDEN, f.state.readRequest(reader, project, f.initial.id))
    }

    @Test
    fun `WF-07 duplicate identities and stale before values do not overwrite records`() {
        val f = WorkflowFixture().baseline()
        bad(WorkflowProblem.DUPLICATE_ID, f.state.propose(planner, doc(30, "different")))
        bad(WorkflowProblem.DUPLICATE_ID,
            f.state.request(planner, project, f.initial.id, f.initial.packet))
        val b = doc(31, "X1")
        f.add(b)
        bad(WorkflowProblem.STALE_SUBJECT, f.state.request(planner, project, f.id(),
            packet(listOf(ReviewItem(f.id(), b.x())), listOf(b.contextReference()))))
        assertEquals("X0\n", good(f.state.readClause(reader, f.a.x())).located.content.text())
    }

    @Test
    fun `WF-04 ordinary update of a deliberately old dependency does not withdraw it`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val dependencyDocument = DocumentKey(project, UUID(0, 800))
        fun dependency(id: Long, text: String) = DocumentRevision(
            RevisionKey(dependencyDocument, UUID(0, id)), Utf8Content.fromText(text),
            StructureIndex(listOf(ClauseSpan(ClauseId("DEP"), ByteSpan(0, text.length))), emptyList()),
        )
        val d0 = dependency(801, "D0")
        val d1 = dependency(802, "D1")
        f.add(d0)
        f.add(d1)
        val dep0 = d0.clauseReference(ClauseId("DEP"))
        val dep1 = d1.clauseReference(ClauseId("DEP"))
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), dep0)), emptyList())))
        val pendingX = f.pendingX(b, listOf(dep0))
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), dep1, dep0)), emptyList())))
        f.answer(pendingX)
        assertEquals(pendingX.id, good(f.state.approvalTrace(reader, b.x())).requestId)
        assertEquals(dep0, good(f.state.approvalTrace(reader, dep0)).origin)
    }

    @Test
    fun `WF-02 every material and item identity affects packet binding`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val i = ReviewItem(f.id(), b.x(), f.a.x())
        val original = packet(listOf(i), listOf(f.a.contextReference(), b.contextReference()))
        val changedQuestion = packet(listOf(i), original.contexts, question = "A different question")
        val changedItem = packet(listOf(i.copy(id = f.id())), original.contexts)
        val changedOrder = packet(listOf(i), original.contexts.reversed())
        assertNotEquals(original.digest, changedQuestion.digest)
        assertNotEquals(original.digest, changedItem.digest)
        assertNotEquals(original.digest, changedOrder.digest)
        val r = f.pending(original)
        val another = f.pending(original)
        assertNotEquals(r.digest, another.digest)
        assertEquals(original.digest, r.packet.digest)
        assertFalse(good(f.state.resolve(owner, project, r.id, r.digest, original.digest,
            StructuredAnswer.Approve)).replayed)
    }
}
