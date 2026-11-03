// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

internal val sampleDocument = DocumentKey(UUID(0, 1), UUID(0, 2))
internal val sampleKey = RevisionKey(sampleDocument, UUID(0, 3))
internal fun clause(id: String, start: Int, end: Int) = ClauseSpan(ClauseId(id), ByteSpan(start, end))
internal fun revision(
    text: String,
    clauses: List<ClauseSpan>,
    context: List<ByteSpan> = emptyList(),
    key: RevisionKey = sampleKey,
) = DocumentRevision(key, Utf8Content.fromText(text), StructureIndex(clauses, context))

class StructureTest {
    @Test
    fun `KC-02 interleaved UTF-8 clauses and context cover exact original bytes`() {
        val rev = revision("pré\nAα\nB🙂\nfin", listOf(clause("A", 5, 9), clause("B", 9, 15)), listOf(ByteSpan(0, 5), ByteSpan(15, 18)))
        val catalog = RevisionCatalog(listOf(rev))
        assertEquals("Aα\n", (catalog.read(rev.clauseReference(ClauseId("A"))) as ReadResult.Found).value.located.content.text())
        assertEquals("B🙂\n", (catalog.read(rev.clauseReference(ClauseId("B"))) as ReadResult.Found).value.located.content.text())
        val context = (catalog.read(rev.contextReference()) as ReadResult.Found).value
        assertEquals(listOf("pré\n", "fin"), context.fragments.map { it.content.text() })
        assertEquals(listOf(ByteSpan(0, 5), ByteSpan(15, 18)), context.fragments.map { it.span })
    }

    @TestFactory
    fun `KC-02 malformed range regions are refused`(): List<DynamicTest> = listOf(
        "negative start" to (-1 to 1), "empty" to (1 to 1), "reversed" to (2 to 1), "negative end" to (0 to -1),
    ).map { (name, bounds) ->
        DynamicTest.dynamicTest(name) { assertFailsWith<IllegalArgumentException> { ByteSpan(bounds.first, bounds.second) } }
    }

    @TestFactory
    fun `KC-02 partition error regions are refused`(): List<DynamicTest> = listOf<Pair<String, () -> Unit>>(
        "leading gap" to { revision("abc", listOf(clause("A", 1, 3))) },
        "trailing gap" to { revision("abc", listOf(clause("A", 0, 2))) },
        "overlap" to { revision("abc", listOf(clause("A", 0, 2), clause("B", 1, 3))) },
        "outside body" to { revision("abc", listOf(clause("A", 0, 4))) },
        "split codepoint" to { revision("é", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2))) },
        "missing partition" to { revision("abc", emptyList()) },
        "span on empty body" to { revision("", listOf(clause("A", 0, 1))) },
        "duplicate clause identity" to { revision("ab", listOf(clause("A", 0, 1), clause("A", 1, 2))) },
        "clauses out of order" to { revision("ab", listOf(clause("A", 1, 2), clause("B", 0, 1))) },
        "context out of order" to { revision("ab", emptyList(), listOf(ByteSpan(1, 2), ByteSpan(0, 1))) },
    ).map { (name, build) -> DynamicTest.dynamicTest(name) { assertFailsWith<IllegalArgumentException> { build() } } }

    @Test
    fun `KC-02 empty body and context-only document are valid positive cases`() {
        val empty = revision("", emptyList())
        assertEquals(0, empty.content.sizeBytes)
        assertEquals(emptyList(), (RevisionCatalog(listOf(empty)).read(empty.contextReference()) as ReadResult.Found).value.fragments)
        val contextOnly = revision("all context", emptyList(), listOf(ByteSpan(0, 11)))
        val read = (RevisionCatalog(listOf(contextOnly)).read(contextOnly.contextReference()) as ReadResult.Found).value
        assertEquals("all context", read.fragments.single().content.text())
    }

    @Test
    fun `KC-03 structure digest follows independent declared v1 framing`() {
        val index = StructureIndex(listOf(clause("é", 0, 2)), listOf(ByteSpan(2, 3)))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            val domain = "writhelm.structure-index.v1".toByteArray(Charsets.UTF_8)
            it.writeInt(domain.size)
            it.write(domain)
            it.writeInt(1)
            it.writeInt(2) // UTF-8 length of the explicit ID, not UTF-16 code units.
            it.write(byteArrayOf(0xc3.toByte(), 0xa9.toByte()))
            it.writeInt(0)
            it.writeInt(2)
            it.writeInt(1)
            it.writeInt(2)
            it.writeInt(3)
        }
        assertEquals(oracleSha(bytes.toByteArray()), index.digest.hex)
    }

    @Test
    fun `KC-03 revision binds full body index and identity not only selected clause`() {
        val first = revision("ab", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2)))
        val changedOutsideA = revision("ac", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2)))
        val changedId = revision("ab", listOf(clause("B", 0, 1)), listOf(ByteSpan(1, 2)))
        val changedRanges = revision("ab", listOf(clause("A", 1, 2)), listOf(ByteSpan(0, 1)))
        val changedRevision = revision("ab", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2)), sampleKey.copy(revisionId = UUID(0, 4)))
        val changedDocument = revision(
            "ab", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2)),
            RevisionKey(sampleDocument.copy(documentId = UUID(0, 9)), sampleKey.revisionId),
        )
        val changedProject = revision(
            "ab", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2)),
            RevisionKey(sampleDocument.copy(projectId = UUID(0, 9)), sampleKey.revisionId),
        )
        val variants = listOf(first, changedOutsideA, changedId, changedRanges, changedRevision, changedDocument, changedProject)
        assertEquals(7, variants.map { it.digest }.toSet().size)
        assertEquals(first.clauseReference(ClauseId("A")).contentDigest, changedOutsideA.clauseReference(ClauseId("A")).contentDigest)
        assertEquals(first.content.digest, changedRanges.content.digest)
        assertNotEquals(first.structure.digest, changedRanges.structure.digest)
    }

    @Test
    fun `KC-03 revision digest follows independent declared v1 framing`() {
        val indexBytes = ByteArrayOutputStream()
        DataOutputStream(indexBytes).use {
            val domain = "writhelm.structure-index.v1".toByteArray()
            it.writeInt(domain.size)
            it.write(domain)
            it.writeInt(1)
            it.writeInt(1)
            it.writeByte('A'.code)
            it.writeInt(0)
            it.writeInt(3)
            it.writeInt(0)
        }
        val expected = ByteArrayOutputStream()
        DataOutputStream(expected).use {
            val domain = "writhelm.document-revision.v1".toByteArray()
            it.writeInt(domain.size)
            it.write(domain)
            listOf(sampleDocument.projectId, sampleDocument.documentId, sampleKey.revisionId).forEach { id ->
                it.writeLong(id.mostSignificantBits)
                it.writeLong(id.leastSignificantBits)
            }
            it.write(java.security.MessageDigest.getInstance("SHA-256").digest(byteArrayOf(97, 98, 99)))
            it.write(java.security.MessageDigest.getInstance("SHA-256").digest(indexBytes.toByteArray()))
        }
        assertEquals(oracleSha(expected.toByteArray()), revision("abc", listOf(clause("A", 0, 3))).digest.hex)
    }

    @Test
    fun `KC-05 index lists are defensively copied and cannot be mutated through casts`() {
        val clauses = mutableListOf(clause("A", 0, 1))
        val context = mutableListOf(ByteSpan(1, 2))
        val index = StructureIndex(clauses, context)
        val before = index.digest
        clauses.clear()
        context.clear()
        assertEquals(listOf(clause("A", 0, 1)), index.clauses)
        assertEquals(listOf(ByteSpan(1, 2)), index.context)
        assertFailsWith<UnsupportedOperationException> { (index.clauses as MutableList<ClauseSpan>).clear() }
        assertFailsWith<UnsupportedOperationException> { (index.context as MutableList<ByteSpan>).clear() }
        assertEquals(before, index.digest)
    }

    @Test
    fun `KC-06 context keeps ordered fragment boundaries rather than concatenating text`() {
        val a = revision("abc", emptyList(), listOf(ByteSpan(0, 2), ByteSpan(2, 3)))
        val b = revision("abc", emptyList(), listOf(ByteSpan(0, 1), ByteSpan(1, 3)))
        val aRead = (RevisionCatalog(listOf(a)).read(a.contextReference()) as ReadResult.Found).value
        val bRead = (RevisionCatalog(listOf(b)).read(b.contextReference()) as ReadResult.Found).value
        assertEquals(listOf("ab", "c"), aRead.fragments.map { it.content.text() })
        assertEquals(listOf("a", "bc"), bRead.fragments.map { it.content.text() })
        assertNotEquals(a.contextDigest, b.contextDigest)
        assertNotEquals(a.digest, b.digest)
        assertEquals(a.content.digest, b.content.digest)
    }

    @Test
    fun `KC-06 context digest follows independent declared v1 framing`() {
        val expected = ByteArrayOutputStream()
        DataOutputStream(expected).use {
            val domain = "writhelm.document-context.v1".toByteArray()
            it.writeInt(domain.size)
            it.write(domain)
            it.writeInt(2)
            it.writeInt(2)
            it.write(byteArrayOf(97, 98))
            it.writeInt(1)
            it.writeByte(99)
        }
        val rev = revision("abc", emptyList(), listOf(ByteSpan(0, 2), ByteSpan(2, 3)))
        assertEquals(oracleSha(expected.toByteArray()), rev.contextDigest.hex)
    }

    @Test
    fun `KC-02 invalid stable IDs are not normalized into accepted identities`() {
        listOf("", " ", " A", "A ", "A\nB", "\uD800").forEach {
            assertFailsWith<IllegalArgumentException> { ClauseId(it) }
        }
        assertNotEquals(ClauseId("A"), ClauseId("a"))
    }
}
