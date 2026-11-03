// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.util.UUID

data class DocumentKey(val projectId: UUID, val documentId: UUID)

data class RevisionKey(val document: DocumentKey, val revisionId: UUID)

sealed interface KnowledgeReference {
    val revision: RevisionKey
    val revisionDigest: Digest
    val addressedDigest: Digest
}

data class ClauseReference(
    override val revision: RevisionKey,
    val clauseId: ClauseId,
    override val revisionDigest: Digest,
    val contentDigest: Digest,
) : KnowledgeReference {
    override val addressedDigest: Digest get() = contentDigest
}

data class ContextReference(
    override val revision: RevisionKey,
    override val revisionDigest: Digest,
    val contextDigest: Digest,
) : KnowledgeReference {
    override val addressedDigest: Digest get() = contextDigest
}

data class LocatedContent(val span: ByteSpan, val content: Utf8Content)

data class ClauseRead(val reference: ClauseReference, val located: LocatedContent)

class ContextRead internal constructor(val reference: ContextReference, fragments: List<LocatedContent>) {
    val fragments: List<LocatedContent> = immutableList(fragments)
}

enum class ReadFailure {
    REVISION_NOT_FOUND,
    CLAUSE_NOT_FOUND,
    REVISION_DIGEST_MISMATCH,
    CONTENT_DIGEST_MISMATCH,
}

sealed interface ReadResult<out T> {
    data class Found<T>(val value: T) : ReadResult<T>
    data class Failed(val reason: ReadFailure) : ReadResult<Nothing>
}
