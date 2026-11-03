// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.KnowledgeReference
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ContinuityManifestTest {
    @Test
    fun `WF-05 WF-06 changed X and independent Y preserve two consumers and original authority`() {
        val f = WorkflowFixture().baseline()
        val first = f.pin(listOf(f.a.x(), f.a.y()), listOf(f.a.contextReference()), f.baselineAssessment)
        val oldState = f.state
        val (b, assessment) = f.prepareB()
        f.answer(f.pendingX(b))
        val second = f.pin(listOf(b.x(), b.y()), listOf(b.contextReference()), assessment)
        f.add(doc(32, "X2"))
        repeat(3) {
            assertSame(first, good(f.state.readManifest(reader, project, first.id)))
            assertSame(second, good(f.state.readManifest(reader, project, second.id)))
            assertEquals("X0\n", good(f.state.readClause(reader, first.clauses[0])).located.content.text())
            assertEquals("X1\n", good(f.state.readClause(reader, second.clauses[0])).located.content.text())
            assertEquals("Y0\n", good(f.state.readClause(reader, second.clauses[1])).located.content.text())
        }
        assertSame(first, good(oldState.readManifest(reader, project, first.id)))
        val y = second.provenance.getValue(b.y())
        assertEquals(f.a.y(), y.origin)
        assertEquals(f.initial.id, y.requestId)
        assertEquals(listOf(assessment.id), y.assessmentIds)
        assertEquals(f.initial.id, first.provenance.getValue(f.a.y()).requestId)
    }

    @Test
    fun `WF-04 WF-06 an X-only ruling does not approve edited Y and explicit old Y mix can be pinned`() {
        val f = WorkflowFixture().baseline()
        val (b, assessment) = f.prepareB(doc(31, "X1", "Y1"), carryY = false)
        f.answer(f.pendingX(b))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.y()))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.pin(planner, project, f.id(), f.id(),
            listOf(b.x(), b.y()), listOf(b.contextReference()), emptyList(), assessment.id))
        val mixed = f.pin(listOf(b.x(), f.a.y()), listOf(b.contextReference(), f.a.contextReference()), assessment)
        assertEquals(listOf(b.x(), f.a.y()), mixed.clauses)
        assertEquals(f.initial.id, mixed.provenance.getValue(f.a.y()).requestId)
    }

    @Test
    fun `WF-05 invalid multi-link assessment cannot partially grant even its valid context link`() {
        val f = WorkflowFixture().baseline()
        val b = doc(31, "X1", "Y1")
        f.add(b)
        val assessment = ConsistencyAssessment(f.id(), project, refs(f.a, b), "Both proposed unchanged",
            listOf(ContinuityLink(f.a.contextReference(), b.contextReference()), ContinuityLink(f.a.y(), b.y())))
        bad(WorkflowProblem.INVALID_CONTINUITY, f.state.assess(planner, assessment))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.contextReference()))
        assertEquals(f.initial.id, good(f.state.approvalTrace(reader, f.a.y())).requestId)
    }

    @Test
    fun `WF-02 exact actual context is mandatory and equal bytes elsewhere do not substitute`() {
        val f = WorkflowFixture().baseline()
        val b = doc(31, "X1")
        f.add(b)
        bad(WorkflowProblem.MISSING_CONTEXT, f.state.request(planner, project, f.id(),
            packet(listOf(ReviewItem(f.id(), b.x(), f.a.x())), listOf(f.a.contextReference()))))
        val request = f.pendingX(b)
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.resolve(owner, project, request.id,
            request.digest, request.packet.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.x()))
        val explicit = f.pending(packet(listOf(ReviewItem(f.id(), b.x(), f.a.x()),
            ReviewItem(f.id(), b.contextReference(), f.a.contextReference())),
            listOf(f.a.contextReference(), b.contextReference())))
        f.answer(explicit)
        assertEquals(explicit.id, good(f.state.approvalTrace(reader, b.x())).requestId)
    }

    @Test
    fun `WF-05 hash equality alone is not continuity and existing targets cannot be overwritten`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB(carryY = false)
        assertEquals(f.a.y().contentDigest, b.y().contentDigest)
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.y()))
        val assessed = f.assessment(refs(f.a, b), listOf(ContinuityLink(f.a.y(), b.y())))
        val trace = good(f.state.approvalTrace(reader, b.y()))
        assertEquals(f.initial.id, trace.requestId)
        bad(WorkflowProblem.INVALID_CONTINUITY, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, refs(f.a, b), "Duplicate mapping", assessed.links)))
        assertEquals(listOf(assessed.id), good(f.state.approvalTrace(reader, b.y())).assessmentIds)
    }

    @Test
    fun `WF-05 changed context is not byte-only equivalence even after the context itself is approved`() {
        val f = WorkflowFixture().baseline()
        val b = doc(31, "X1", "Y0", "A different scope\n")
        f.add(b)
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), b.contextReference(), f.a.contextReference())),
            listOf(f.a.contextReference(), b.contextReference()))))
        bad(WorkflowProblem.CONTEXT_CHANGED, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, refs(f.a, b), "Body equality is insufficient",
                listOf(ContinuityLink(f.a.y(), b.y())))))
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), b.y(), f.a.y())),
            listOf(f.a.contextReference(), b.contextReference()))))
        val assessment = f.assessment(refs(f.a, b))
        bad(WorkflowProblem.UNSUPPORTED_CONTEXT_MIX, f.state.pin(planner, project, f.id(), f.id(),
            listOf(f.a.x(), b.y()), listOf(f.a.contextReference(), b.contextReference()), emptyList(), assessment.id))
        bad(WorkflowProblem.UNSUPPORTED_CONTEXT_MIX, f.state.pin(planner, project, f.id(), f.id(),
            listOf(b.y()), listOf(b.contextReference()), listOf(f.a.contextReference()), assessment.id))
        val onlyNew = f.pin(listOf(b.y()), listOf(b.contextReference()), assessment)
        assertEquals(listOf(b.y()), onlyNew.clauses)
    }

    @Test
    fun `WF-05 same bytes under another stable ID cannot inherit authority`() {
        val f = WorkflowFixture(a = doc(30, "Same", "Same")).baseline()
        val b = doc(31, "Same", "Same")
        f.add(b)
        bad(WorkflowProblem.INVALID_CONTINUITY, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, refs(f.a, b), "Different IDs are different subjects",
                listOf(ContinuityLink(f.a.y(), b.x())))))
    }

    @Test
    fun `WF-05 new-source chains and cycles cannot bootstrap authority within one batch`() {
        val f = WorkflowFixture().baseline()
        val b = doc(31)
        val c = doc(32)
        f.add(b)
        f.add(c)
        val contextRefs = listOf(f.a.contextReference(), b.contextReference(), c.contextReference())
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, contextRefs, "New source is not an approved source",
                listOf(ContinuityLink(contextRefs[0], contextRefs[1]), ContinuityLink(contextRefs[1], contextRefs[2])))))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.contextReference()))
        bad(WorkflowProblem.INVALID_CONTINUITY, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, contextRefs, "No cycle",
                listOf(ContinuityLink(contextRefs[0], contextRefs[1]), ContinuityLink(contextRefs[1], contextRefs[0])))))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.contextReference()))
    }

    @Test
    fun `WF-05 assessment must name its exact link and blocker sources and contexts`() {
        val f = WorkflowFixture().baseline()
        val b = doc(31)
        f.add(b)
        bad(WorkflowProblem.INCOMPLETE_ASSESSMENT, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, listOf(f.a.contextReference(), b.contextReference()), "Missing Y refs",
                listOf(ContinuityLink(f.a.y(), b.y())))))
        bad(WorkflowProblem.MISSING_CONTEXT, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, listOf(f.a.y(), b.y()), "Missing contexts")))
        bad(WorkflowProblem.INCOMPLETE_ASSESSMENT, f.state.assess(planner,
            ConsistencyAssessment(f.id(), project, refs(f.a), "Missing blocked source", blocked = listOf(b.y()))))
    }

    @Test
    fun `WF-04 WF-06 blockers affect new use through continuity but do not rewrite historical manifests`() {
        val f = WorkflowFixture().baseline()
        val old = f.pin(listOf(f.a.x(), f.a.y()), listOf(f.a.contextReference()), f.baselineAssessment)
        val (b, assessment) = f.prepareB()
        val pending = f.pendingX(b, listOf(f.a.y()))
        f.assessment(refs(f.a), blocked = listOf(f.a.y()))
        bad(WorkflowProblem.BLOCKED_INPUT, f.state.resolve(owner, project, pending.id,
            pending.digest, pending.packet.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.BLOCKED_INPUT, f.state.approvalTrace(reader, b.y()))
        assertSame(old, good(f.state.readManifest(reader, project, old.id)))
        assertEquals(f.initial.id, old.provenance.getValue(f.a.y()).requestId)
        assertEquals("Y0\n", good(f.state.readClause(reader, old.clauses[1])).located.content.text())
        f.answer(f.pendingX(b)) // No Y dependency; an unrelated known finding is not a global stop.
        f.pin(listOf(b.x()), listOf(b.contextReference()), assessment)
        bad(WorkflowProblem.BLOCKED_INPUT, f.state.pin(planner, project, f.id(), f.id(),
            listOf(b.x(), b.y()), listOf(b.contextReference()), emptyList(), assessment.id))
    }

    @Test
    fun `WF-04 a blocked member prevents every effect of its coherent approval group`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB(doc(31, "X1", "Y1"), carryY = false)
        val request = f.pending(packet(listOf(ReviewItem(f.id(), b.x(), f.a.x()), ReviewItem(f.id(), b.y(), f.a.y())),
            listOf(f.a.contextReference(), b.contextReference())))
        f.assessment(refs(f.a, b), blocked = listOf(b.y()))
        bad(WorkflowProblem.BLOCKED_INPUT, f.state.resolve(owner, project, request.id,
            request.digest, request.packet.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.UNAPPROVED_INPUT, f.state.approvalTrace(reader, b.x()))
        bad(WorkflowProblem.NOT_FOUND, f.state.readResolution(reader, project, request.id))
    }

    @Test
    fun `WF-04 a fresh ruling cannot bypass the known blocked source of an unchanged target`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val c = doc(32, "X2", "Y2")
        f.add(c)
        f.assessment(refs(f.a, c), listOf(ContinuityLink(f.a.contextReference(), c.contextReference())))
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), c.y(), f.a.y())),
            listOf(f.a.contextReference(), c.contextReference()))))
        f.assessment(refs(f.a), blocked = listOf(f.a.y()))
        val oldAlias = f.pending(packet(listOf(ReviewItem(f.id(), b.y(), c.y())),
            listOf(b.contextReference(), c.contextReference())))
        bad(WorkflowProblem.BLOCKED_INPUT, f.state.resolve(owner, project, oldAlias.id,
            oldAlias.digest, oldAlias.packet.digest, StructuredAnswer.Approve))
        bad(WorkflowProblem.BLOCKED_INPUT, f.state.approvalTrace(reader, b.y()))
        assertEquals(c.y(), good(f.state.approvalTrace(reader, c.y())).origin)
    }

    @Test
    fun `WF-06 one assessment covers the selected joint use and manifests cannot be overwritten`() {
        val f = WorkflowFixture().baseline()
        val partial = f.assessment(listOf(f.a.x(), f.a.contextReference()))
        bad(WorkflowProblem.INCOMPLETE_ASSESSMENT, f.state.pin(planner, project, f.id(), f.id(),
            listOf(f.a.x(), f.a.y()), listOf(f.a.contextReference()), emptyList(), partial.id))
        bad(WorkflowProblem.INCOMPLETE_ASSESSMENT, f.state.pin(planner, project, f.id(), f.id(),
            listOf(f.a.x()), listOf(f.a.contextReference()), emptyList(), UUID(0, 999)))
        val valid = f.pin(listOf(f.a.x(), f.a.y()), listOf(f.a.contextReference()), f.baselineAssessment)
        bad(WorkflowProblem.DUPLICATE_ID, f.state.pin(planner, project, valid.id, f.id(),
            listOf(f.a.x()), listOf(f.a.contextReference()), emptyList(), partial.id))
        assertSame(valid, good(f.state.readManifest(reader, project, valid.id)))
    }

    @Test
    fun `WF-06 duplicate or conflicting clause versions are refused rather than resolved by order`() {
        val f = WorkflowFixture().baseline()
        val (b, assessment) = f.prepareB()
        f.answer(f.pendingX(b))
        bad(WorkflowProblem.CONFLICTING_SELECTION, f.state.pin(planner, project, f.id(), f.id(),
            listOf(f.a.x(), f.a.x()), listOf(f.a.contextReference()), emptyList(), assessment.id))
        bad(WorkflowProblem.CONFLICTING_SELECTION, f.state.pin(planner, project, f.id(), f.id(),
            listOf(b.x()), listOf(b.contextReference(), f.a.contextReference()), listOf(f.a.x()), assessment.id))
        bad(WorkflowProblem.MISSING_CONTEXT, f.state.pin(planner, project, f.id(), f.id(),
            listOf(b.x(), b.y()), listOf(f.a.contextReference()), emptyList(), assessment.id))
    }

    @Test
    fun `WF-01 planning and historical read commands enforce current capabilities`() {
        val f = WorkflowFixture().baseline()
        val assessment = ConsistencyAssessment(f.id(), project, refs(f.a), "Permission checks")
        listOf(reader, contributor, outsider).forEach { actor ->
            bad(WorkflowProblem.FORBIDDEN, f.state.assess(actor, assessment))
            bad(WorkflowProblem.FORBIDDEN, f.state.pin(actor, project, f.id(), f.id(),
                listOf(f.a.x()), listOf(f.a.contextReference()), emptyList(), f.baselineAssessment.id))
        }
        val manifest = f.pin(listOf(f.a.x()), listOf(f.a.contextReference()), f.baselineAssessment)
        bad(WorkflowProblem.FORBIDDEN, f.state.readManifest(outsider, project, manifest.id))
        bad(WorkflowProblem.NOT_FOUND, f.state.readManifest(foreignOwner, otherProject, manifest.id))
        bad(WorkflowProblem.FORBIDDEN, f.state.readClause(outsider, f.a.x()))
    }

    @Test
    fun `WF-02 empty context needs no phantom approval item`() {
        val f = WorkflowFixture(a = doc(30, context = ""))
        f.add(f.a)
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), f.a.x()), ReviewItem(f.id(), f.a.y())), emptyList())))
        val references: List<KnowledgeReference> = listOf(f.a.x(), f.a.y())
        val assessment = f.assessment(references)
        val manifest = f.pin(listOf(f.a.x(), f.a.y()), emptyList(), assessment)
        assertEquals(emptyList(), manifest.contexts)
    }
}
