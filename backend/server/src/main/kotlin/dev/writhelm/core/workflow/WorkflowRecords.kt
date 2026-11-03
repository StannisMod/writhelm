// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.ClauseId
import dev.writhelm.core.knowledge.ClauseReference
import dev.writhelm.core.knowledge.ContextReference
import dev.writhelm.core.knowledge.DocumentKey
import dev.writhelm.core.knowledge.KnowledgeReference
import dev.writhelm.core.knowledge.Utf8Content
import java.util.Collections
import java.util.UUID

enum class Capability { READ, PROPOSE, PLAN, APPROVE, MANAGE }

/** Already authenticated by the future transport. Not part of a caller's command payload. */
data class Actor(val principalId: UUID)

data class Grant(val principalId: UUID, val projectId: UUID, val capabilities: Set<Capability>)

class AccessPolicy(grants: Collection<Grant>) {
    private val entries: Map<Pair<UUID, UUID>, Set<Capability>>

    init {
        val copied = LinkedHashMap<Pair<UUID, UUID>, Set<Capability>>()
        grants.forEach {
            val key = it.principalId to it.projectId
            require(key !in copied) { "Duplicate project grant" }
            copied[key] = frozenSet(it.capabilities)
        }
        entries = frozenMap(copied)
    }

    fun allows(actor: Actor, project: UUID, capability: Capability): Boolean =
        capability in entries[actor.principalId to project].orEmpty()

    internal fun hasGrant(project: UUID, principal: UUID): Boolean = entries.containsKey(principal to project)

    internal fun revocationKeepsAuthority(project: UUID, principal: UUID): Boolean {
        val removed = entries[principal to project].orEmpty()
        return listOf(Capability.APPROVE, Capability.MANAGE).all { capability ->
            capability !in removed || entries.any { (key, caps) ->
                key.second == project && key.first != principal && capability in caps
            }
        }
    }

    internal fun revoke(project: UUID, principal: UUID): AccessPolicy = AccessPolicy(
        entries.filterKeys { it != (principal to project) }.map { (key, caps) -> Grant(key.first, key.second, caps) }
    )
}

data class SubjectKey(val document: DocumentKey, val clauseId: ClauseId?)

internal fun KnowledgeReference.subject(): SubjectKey = when (this) {
    is ClauseReference -> SubjectKey(revision.document, clauseId)
    is ContextReference -> SubjectKey(revision.document, null)
}

data class ReviewItem(val id: UUID, val target: KnowledgeReference, val before: KnowledgeReference? = null) {
    init {
        require(before == null || before.subject() == target.subject()) { "Before and target name different subjects" }
        require(before != target) { "A change must name a distinct target occurrence" }
    }
}

sealed interface StructuredAnswer {
    data object Approve : StructuredAnswer
    data object Reject : StructuredAnswer

    class ChangesRequired(comments: Map<UUID, String>) : StructuredAnswer {
        val comments: Map<UUID, String> = frozenMap(comments)

        init {
            require(this.comments.isNotEmpty()) { "A changes request needs an anchored comment" }
            this.comments.values.forEach { requireText(it) }
        }

        override fun equals(other: Any?): Boolean = other is ChangesRequired && comments == other.comments
        override fun hashCode(): Int = comments.hashCode()
    }
}

data class ContinuityLink(val source: KnowledgeReference, val target: KnowledgeReference)

class ConsistencyAssessment(
    val id: UUID,
    val projectId: UUID,
    references: List<KnowledgeReference>,
    val rationale: String,
    links: List<ContinuityLink> = emptyList(),
    blocked: List<KnowledgeReference> = emptyList(),
) {
    val references = frozenList(references)
    val links = frozenList(links)
    val blocked = frozenList(blocked)

    init {
        requireText(rationale)
        require(this.references.distinct().size == this.references.size) { "Duplicate assessed reference" }
        require(this.links.map { it.target }.distinct().size == this.links.size) { "Duplicate continuity target" }
        require(this.blocked.distinct().size == this.blocked.size) { "Duplicate blocked reference" }
    }
}

class ApprovalTrace internal constructor(
    val origin: KnowledgeReference,
    val requestId: UUID,
    val approvedBy: UUID,
    assessmentIds: List<UUID> = emptyList(),
) {
    val assessmentIds = frozenList(assessmentIds)
}

class ApprovalResolution internal constructor(
    val requestId: UUID,
    val actor: Actor,
    val answer: StructuredAnswer,
    approved: List<KnowledgeReference>,
) {
    val approved = frozenList(approved)
}

class InputManifest internal constructor(
    val id: UUID,
    val consumerId: UUID,
    val projectId: UUID,
    val createdBy: UUID,
    clauses: List<ClauseReference>,
    contexts: List<ContextReference>,
    dependencies: List<KnowledgeReference>,
    val assessmentId: UUID,
    provenance: Map<KnowledgeReference, ApprovalTrace>,
) {
    val clauses = frozenList(clauses)
    val contexts = frozenList(contexts)
    val dependencies = frozenList(dependencies)
    val provenance = frozenMap(provenance)
}

class RecordedAssessment internal constructor(val assessment: ConsistencyAssessment, val actor: Actor)

enum class WorkflowProblem {
    FORBIDDEN, NOT_FOUND, DUPLICATE_ID, INVALID_REFERENCE, FOREIGN_REFERENCE, MISSING_CONTEXT,
    STALE_SUBJECT, DIGEST_MISMATCH, ALREADY_RESOLVED, UNKNOWN_COMMENT_ITEM, INCOMPLETE_PACKET,
    UNAPPROVED_INPUT, BLOCKED_INPUT, INVALID_CONTINUITY, CONTEXT_CHANGED, INCOMPLETE_ASSESSMENT,
    CONFLICTING_SELECTION, UNSUPPORTED_CONTEXT_MIX, LAST_AUTHORITY,
}

sealed interface WorkflowResult<out T> {
    data class Success<T>(val value: T) : WorkflowResult<T>
    data class Failure(val problem: WorkflowProblem) : WorkflowResult<Nothing>
}

data class Transition<out T>(val state: ApprovalWorkflow, val value: T, val replayed: Boolean = false)

internal fun requireText(value: String) {
    require(value.isNotBlank()) { "Expected nonblank decision material" }
    Utf8Content.fromText(value)
}

internal fun <T> frozenList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
internal fun <T> frozenSet(values: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
internal fun <K, V> frozenMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
