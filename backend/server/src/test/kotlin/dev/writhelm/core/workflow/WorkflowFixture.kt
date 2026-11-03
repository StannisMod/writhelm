// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.ByteSpan
import dev.writhelm.core.knowledge.ClauseId
import dev.writhelm.core.knowledge.ClauseReference
import dev.writhelm.core.knowledge.ClauseSpan
import dev.writhelm.core.knowledge.ContextReference
import dev.writhelm.core.knowledge.DocumentKey
import dev.writhelm.core.knowledge.DocumentRevision
import dev.writhelm.core.knowledge.KnowledgeReference
import dev.writhelm.core.knowledge.RevisionKey
import dev.writhelm.core.knowledge.StructureIndex
import dev.writhelm.core.knowledge.Utf8Content
import java.util.UUID
import kotlin.test.assertEquals

internal val project = UUID(0, 10)
internal val otherProject = UUID(0, 11)
internal val owner = Actor(UUID(0, 1))
internal val planner = Actor(UUID(0, 2))
internal val contributor = Actor(UUID(0, 3))
internal val reader = Actor(UUID(0, 4))
internal val outsider = Actor(UUID(0, 5))
internal val coOwner = Actor(UUID(0, 6))
internal val foreignOwner = Actor(UUID(0, 7))
internal val document = DocumentKey(project, UUID(0, 20))

internal fun doc(id: Long, x: String = "X0", y: String = "Y0", context: String = "Scope\n") : DocumentRevision {
    val header = context.toByteArray().size
    val first = "$x\n".toByteArray().size
    val second = "$y\n".toByteArray().size
    return DocumentRevision(
        RevisionKey(document, UUID(0, id)), Utf8Content.fromText("$context$x\n$y\n"),
        StructureIndex(
            listOf(ClauseSpan(ClauseId("X"), ByteSpan(header, header + first)),
                ClauseSpan(ClauseId("Y"), ByteSpan(header + first, header + first + second))),
            if (header == 0) emptyList() else listOf(ByteSpan(0, header)),
        ),
    )
}

internal fun DocumentRevision.x(): ClauseReference = clauseReference(ClauseId("X"))
internal fun DocumentRevision.y(): ClauseReference = clauseReference(ClauseId("Y"))
internal fun refs(vararg revisions: DocumentRevision): List<KnowledgeReference> =
    revisions.flatMap { listOf(it.x(), it.y(), it.contextReference()) }

internal fun packet(
    items: List<ReviewItem>,
    contexts: List<ContextReference>,
    dependencies: List<KnowledgeReference> = emptyList(),
    gaps: List<String> = emptyList(),
    question: String = "Accept the exact synthetic change?",
): ReviewPacket = ReviewPacket(
    items, question, "Only the named items, not other draft edits", "Synthetic contract example",
    listOf("The chosen rule changes; unrelated approvals remain"), listOf("Keep the previous rule"),
    listOf("No runtime or storage cost is measured"), contexts, dependencies, gaps,
)

internal fun <T> good(result: WorkflowResult<T>): T = when (result) {
    is WorkflowResult.Success -> result.value
    is WorkflowResult.Failure -> error("Unexpected workflow rejection: ${result.problem}")
}

internal fun bad(problem: WorkflowProblem, result: WorkflowResult<*>) {
    assertEquals(WorkflowResult.Failure(problem), result)
}

internal class WorkflowFixture(secondOwner: Boolean = false, a: DocumentRevision = doc(30)) {
    private var sequence = 100L
    fun id(): UUID = UUID(0, sequence++)

    var state = ApprovalWorkflow.start(AccessPolicy(buildList {
        add(Grant(owner.principalId, project, Capability.entries.toSet()))
        add(Grant(planner.principalId, project, setOf(Capability.READ, Capability.PLAN, Capability.PROPOSE)))
        add(Grant(contributor.principalId, project, setOf(Capability.READ, Capability.PROPOSE)))
        add(Grant(reader.principalId, project, setOf(Capability.READ)))
        add(Grant(foreignOwner.principalId, otherProject, Capability.entries.toSet()))
        if (secondOwner) add(Grant(coOwner.principalId, project, Capability.entries.toSet()))
    }))
    val a = a
    lateinit var initial: ApprovalRequest
    lateinit var baselineAssessment: ConsistencyAssessment

    fun add(revision: DocumentRevision, actor: Actor = planner) {
        state = good(state.propose(actor, revision)).state
    }

    fun pending(packet: ReviewPacket): ApprovalRequest {
        val result = good(state.request(planner, project, id(), packet))
        state = result.state
        return result.value
    }

    fun answer(request: ApprovalRequest, answer: StructuredAnswer = StructuredAnswer.Approve): ApprovalResolution {
        val result = good(state.resolve(owner, project, request.id, request.digest, request.packet.digest, answer))
        state = result.state
        return result.value
    }

    fun assessment(
        references: List<KnowledgeReference>,
        links: List<ContinuityLink> = emptyList(),
        blocked: List<KnowledgeReference> = emptyList(),
    ): ConsistencyAssessment {
        val value = ConsistencyAssessment(id(), project, references, "Planner's exact-scope consistency claim", links, blocked)
        state = good(state.assess(planner, value)).state
        return value
    }

    fun baseline(): WorkflowFixture {
        add(a)
        initial = pending(packet(listOf(ReviewItem(id(), a.x()), ReviewItem(id(), a.y()),
            ReviewItem(id(), a.contextReference())), listOf(a.contextReference())))
        answer(initial)
        baselineAssessment = assessment(refs(a))
        return this
    }

    fun prepareB(b: DocumentRevision = doc(31, "X1"), carryY: Boolean = true): Pair<DocumentRevision, ConsistencyAssessment> {
        add(b)
        val links = listOf(ContinuityLink(a.contextReference(), b.contextReference())) +
            if (carryY) listOf(ContinuityLink(a.y(), b.y())) else emptyList()
        return b to assessment(refs(a, b), links)
    }

    fun pendingX(b: DocumentRevision, dependencies: List<KnowledgeReference> = emptyList()): ApprovalRequest =
        pending(packet(listOf(ReviewItem(id(), b.x(), a.x())), listOf(a.contextReference(), b.contextReference()), dependencies))

    fun pin(
        clauses: List<ClauseReference>,
        contexts: List<ContextReference>,
        assessment: ConsistencyAssessment,
        dependencies: List<KnowledgeReference> = emptyList(),
    ): InputManifest {
        val result = good(state.pin(planner, project, id(), id(), clauses, contexts, dependencies, assessment.id))
        state = result.state
        return result.value
    }
}
