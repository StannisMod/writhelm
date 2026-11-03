// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.util.Collections

/** An immutable resolver, not a persistent SSOT, authority engine or latest-version selector. */
class RevisionCatalog(revisions: Collection<DocumentRevision> = emptyList()) {
    private val byKey: Map<RevisionKey, DocumentRevision>

    init {
        val entries = LinkedHashMap<RevisionKey, DocumentRevision>()
        revisions.forEach {
            require(!entries.containsKey(it.key)) { "Duplicate immutable revision identity" }
            entries[it.key] = it
        }
        byKey = Collections.unmodifiableMap(entries)
    }

    fun withRevision(revision: DocumentRevision): RevisionCatalog = RevisionCatalog(byKey.values + revision)

    fun read(reference: ClauseReference): ReadResult<ClauseRead> =
        byKey[reference.revision]?.clauseRead(reference) ?: ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND)

    fun read(reference: ContextReference): ReadResult<ContextRead> =
        byKey[reference.revision]?.contextRead(reference) ?: ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND)

    fun contextOf(revision: RevisionKey): ReadResult<ContextReference> =
        byKey[revision]?.let { ReadResult.Found(it.contextReference()) }
            ?: ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND)
}
