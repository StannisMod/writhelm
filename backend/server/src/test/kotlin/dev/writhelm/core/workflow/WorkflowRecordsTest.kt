// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.ContextReference
import dev.writhelm.core.knowledge.KnowledgeReference
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class WorkflowRecordsTest {
    @Test
    fun `WF-02 WF-07 packet fields and inputs are frozen including their digest`() {
        val a = doc(30)
        val item = ReviewItem(UUID(0, 100), a.x())
        val items = mutableListOf(item)
        val contexts = mutableListOf(a.contextReference())
        val consequences = mutableListOf("A consequence")
        val alternatives = mutableListOf("An alternative")
        val uncertainties = mutableListOf("An uncertainty")
        val dependencies = mutableListOf<KnowledgeReference>(a.y())
        val gaps = mutableListOf<String>()
        val p = ReviewPacket(items, "Question", "Scope", "Reason", consequences, alternatives, uncertainties,
            contexts, dependencies, gaps)
        val digest = p.digest
        items.clear()
        contexts.clear()
        consequences[0] = "Replaced"
        alternatives.clear()
        uncertainties.clear()
        dependencies.clear()
        gaps.add("A later unrelated list mutation")
        assertEquals(listOf(item), p.items)
        assertEquals(listOf(a.contextReference()), p.contexts)
        assertEquals(listOf("A consequence"), p.consequences)
        assertEquals(listOf("An alternative"), p.alternatives)
        assertEquals(listOf("An uncertainty"), p.uncertainties)
        assertEquals(listOf(a.y()), p.dependencies)
        assertEquals(emptyList(), p.informationGaps)
        assertEquals(digest, p.digest)
        assertFailsWith<UnsupportedOperationException> { (p.items as MutableList<ReviewItem>).clear() }
        assertFailsWith<UnsupportedOperationException> { (p.contexts as MutableList<ContextReference>).clear() }
        assertFailsWith<UnsupportedOperationException> { (p.consequences as MutableList<String>).clear() }
    }

    @Test
    fun `WF-02 digest binds every ordered decision material field and exact before target references`() {
        val a = doc(30)
        val b = doc(31, "X1", "Y1")
        val c = doc(32, "X2", "Y1")
        val x = ReviewItem(UUID(0, 101), b.x(), a.x())
        val y = ReviewItem(UUID(0, 102), b.y(), a.y())
        fun make(
            items: List<ReviewItem> = listOf(x, y), question: String = "Q", scope: String = "S", reason: String = "R",
            effects: List<String> = listOf("C"), alternatives: List<String> = listOf("A"),
            uncertainty: List<String> = listOf("U"), contexts: List<ContextReference> = listOf(a.contextReference(), b.contextReference()),
            dependencies: List<KnowledgeReference> = emptyList(), gaps: List<String> = emptyList(),
        ) = ReviewPacket(items, question, scope, reason, effects, alternatives, uncertainty, contexts, dependencies, gaps)
        val original = make()
        val variants = listOf(
            make(question = "Q changed"), make(scope = "S changed"), make(reason = "R changed"),
            make(effects = listOf("C changed")), make(alternatives = listOf("A changed")),
            make(uncertainty = listOf("U changed")), make(contexts = original.contexts.reversed()),
            make(dependencies = listOf(a.y())), make(gaps = listOf("Missing information")),
            make(items = listOf(y, x)), make(items = listOf(x.copy(id = UUID(0, 103)), y)),
            make(items = listOf(x.copy(before = null), y)), make(items = listOf(x.copy(target = c.x()), y)),
        )
        variants.forEach { assertNotEquals(original.digest, it.digest) }
        assertEquals(original.digest, make().digest)
    }

    @Test
    fun `WF-02 malformed packet shape is refused rather than silently normalized`() {
        val a = doc(30)
        val item = ReviewItem(UUID(0, 100), a.x())
        assertFailsWith<IllegalArgumentException> { packet(emptyList(), emptyList()) }
        assertFailsWith<IllegalArgumentException> { packet(listOf(item), listOf(a.contextReference()), question = " ") }
        assertFailsWith<IllegalArgumentException> { packet(listOf(item), listOf(a.contextReference()), question = "\uD800") }
        assertFailsWith<IllegalArgumentException> { packet(listOf(item, item), listOf(a.contextReference())) }
        assertFailsWith<IllegalArgumentException> {
            packet(listOf(item, item.copy(id = UUID(0, 101))), listOf(a.contextReference()))
        }
        assertFailsWith<IllegalArgumentException> { packet(listOf(item), listOf(a.contextReference(), a.contextReference())) }
        assertFailsWith<IllegalArgumentException> { packet(listOf(item), listOf(a.contextReference()), listOf(a.y(), a.y())) }
        assertFailsWith<IllegalArgumentException> { ReviewItem(UUID(0, 102), a.x(), a.y()) }
        assertFailsWith<IllegalArgumentException> { ReviewItem(UUID(0, 102), a.x(), a.x()) }
    }

    @Test
    fun `WF-03 WF-07 answer comments are immutable exact text with no new effect on matching replay`() {
        val f = WorkflowFixture().baseline()
        val (b, _) = f.prepareB()
        val r = f.pendingX(b)
        val item = r.packet.items.single().id
        val comments = mutableMapOf(item to " Exact\r\ncomment ")
        val answer = StructuredAnswer.ChangesRequired(comments)
        comments[item] = "Replaced outside"
        comments[f.id()] = "Another item"
        val original = f.answer(r, answer)
        val stored = original.answer as StructuredAnswer.ChangesRequired
        assertEquals(mapOf(item to " Exact\r\ncomment "), stored.comments)
        assertFailsWith<UnsupportedOperationException> { (stored.comments as MutableMap<UUID, String>).clear() }
        val replay = good(f.state.resolve(owner, project, r.id, r.digest, r.packet.digest,
            StructuredAnswer.ChangesRequired(mapOf(item to " Exact\r\ncomment "))))
        assertEquals(true, replay.replayed)
        assertEquals(original, replay.value)
    }

    @Test
    fun `WF-07 assessment and manifest inputs cannot mutate recorded provenance`() {
        val f = WorkflowFixture().baseline()
        val references = refs(f.a).toMutableList()
        val blocked = mutableListOf<KnowledgeReference>()
        val links = mutableListOf<ContinuityLink>()
        val assessment = ConsistencyAssessment(f.id(), project, references, "Joint use", links, blocked)
        f.state = good(f.state.assess(planner, assessment)).state
        references.clear()
        blocked.add(f.a.x())
        links.add(ContinuityLink(f.a.x(), f.a.y()))
        val selected = mutableListOf(f.a.x(), f.a.y())
        val contexts = mutableListOf(f.a.contextReference())
        val dependencies = mutableListOf<KnowledgeReference>()
        val pinned = good(f.state.pin(planner, project, f.id(), f.id(), selected, contexts, dependencies, assessment.id))
        f.state = pinned.state
        selected.clear()
        contexts.clear()
        dependencies.add(f.a.x())
        val manifest = pinned.value
        assertEquals(listOf(f.a.x(), f.a.y()), manifest.clauses)
        assertEquals(listOf(f.a.contextReference()), manifest.contexts)
        assertEquals(emptyList(), manifest.dependencies)
        assertEquals(emptyList(), assessment.blocked)
        assertEquals(emptyList(), assessment.links)
        assertFailsWith<UnsupportedOperationException> {
            (manifest.provenance as MutableMap<KnowledgeReference, ApprovalTrace>).clear()
        }
        assertFailsWith<UnsupportedOperationException> { (manifest.clauses as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> {
            (manifest.provenance.getValue(f.a.y()).assessmentIds as MutableList<UUID>).clear()
        }
        assertEquals(f.initial.id, manifest.provenance.getValue(f.a.y()).requestId)
    }

    @Test
    fun `WF-01 policy construction snapshots capabilities rather than retaining a mutable grant`() {
        val capabilities = Capability.entries.toMutableSet()
        val grants = mutableListOf(Grant(owner.principalId, project, capabilities))
        val policy = AccessPolicy(grants)
        capabilities.clear()
        grants.clear()
        val state = ApprovalWorkflow.start(policy)
        good(state.propose(owner, doc(30)))
        bad(WorkflowProblem.FORBIDDEN, state.propose(outsider, doc(30)))
        assertFailsWith<IllegalArgumentException> {
            AccessPolicy(listOf(Grant(owner.principalId, project, emptySet()), Grant(owner.principalId, project, emptySet())))
        }
    }
}
