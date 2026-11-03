// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.io.DataOutputStream
import java.util.UUID

/** Immutable content/interpretation; intentionally contains no whole-document accepted flag. */
class DocumentRevision(val key: RevisionKey, val content: Utf8Content, val structure: StructureIndex) {
    init {
        structure.validate(content)
    }

    val digest: Digest = framedDigest("writhelm.document-revision.v1") {
        writeUuid(key.document.projectId)
        writeUuid(key.document.documentId)
        writeUuid(key.revisionId)
        write(content.digest.bytes())
        write(structure.digest.bytes())
    }

    private val contextFragments: List<LocatedContent> = immutableList(
        structure.context.map { LocatedContent(it, content.slice(it)) }
    )

    val contextDigest: Digest = framedDigest("writhelm.document-context.v1") {
        writeInt(contextFragments.size)
        contextFragments.forEach { writeFramed(it.content.bytes()) }
    }

    fun clauseReference(id: ClauseId): ClauseReference {
        val span = requireNotNull(structure.clause(id)) { "Clause is absent from this revision" }.span
        return ClauseReference(key, id, digest, content.slice(span).digest)
    }

    fun contextReference(): ContextReference = ContextReference(key, digest, contextDigest)

    internal fun clauseRead(reference: ClauseReference): ReadResult<ClauseRead> {
        if (reference.revision != key) return ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND)
        if (reference.revisionDigest != digest) return ReadResult.Failed(ReadFailure.REVISION_DIGEST_MISMATCH)
        val span = structure.clause(reference.clauseId)?.span
            ?: return ReadResult.Failed(ReadFailure.CLAUSE_NOT_FOUND)
        val body = content.slice(span)
        if (reference.contentDigest != body.digest) return ReadResult.Failed(ReadFailure.CONTENT_DIGEST_MISMATCH)
        return ReadResult.Found(ClauseRead(reference, LocatedContent(span, body)))
    }

    internal fun contextRead(reference: ContextReference): ReadResult<ContextRead> {
        if (reference.revision != key) return ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND)
        if (reference.revisionDigest != digest) return ReadResult.Failed(ReadFailure.REVISION_DIGEST_MISMATCH)
        if (reference.contextDigest != contextDigest) return ReadResult.Failed(ReadFailure.CONTENT_DIGEST_MISMATCH)
        return ReadResult.Found(ContextRead(reference, contextFragments))
    }
}

private fun DataOutputStream.writeUuid(value: UUID) {
    writeLong(value.mostSignificantBits)
    writeLong(value.leastSignificantBits)
}
