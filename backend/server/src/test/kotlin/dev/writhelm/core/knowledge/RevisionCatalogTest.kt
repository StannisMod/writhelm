// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RevisionCatalogTest {
    @Test
    fun `KC-04 old and new pinned reads survive independent revision reorder`() {
        val a = revision("x0|y0", listOf(clause("X", 0, 2), clause("Y", 3, 5)), listOf(ByteSpan(2, 3)))
        val b = revision(
            "y0|x1", listOf(clause("Y", 0, 2), clause("X", 3, 5)), listOf(ByteSpan(2, 3)),
            sampleKey.copy(revisionId = UUID(0, 4)),
        )
        val old = RevisionCatalog(listOf(a))
        val both = old.withRevision(b)
        repeat(3) {
            assertEquals("x0", both.clauseText(a.clauseReference(ClauseId("X"))))
            assertEquals("x1", both.clauseText(b.clauseReference(ClauseId("X"))))
            assertEquals("y0", both.clauseText(a.clauseReference(ClauseId("Y"))))
            assertEquals("y0", both.clauseText(b.clauseReference(ClauseId("Y"))))
        }
        assertEquals("x0", old.clauseText(a.clauseReference(ClauseId("X"))))
        assertEquals(ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND), old.read(b.clauseReference(ClauseId("X"))))
        assertEquals(a.clauseReference(ClauseId("Y")).contentDigest, b.clauseReference(ClauseId("Y")).contentDigest)
        assertNotEquals(a.clauseReference(ClauseId("Y")), b.clauseReference(ClauseId("Y")))
    }

    @Test
    fun `KC-04 a renamed heading remains part of exact old clause content`() {
        val oldText = "## Old\nrule"
        val newText = "## New heading\nrule"
        val a = revision(oldText, listOf(clause("RULE", 0, oldText.toByteArray().size)))
        val b = revision(newText, listOf(clause("RULE", 0, newText.toByteArray().size)), key = sampleKey.copy(revisionId = UUID(0, 4)))
        val catalog = RevisionCatalog(listOf(a, b))
        assertEquals(oldText, catalog.clauseText(a.clauseReference(ClauseId("RULE"))))
        assertEquals(newText, catalog.clauseText(b.clauseReference(ClauseId("RULE"))))
        assertNotEquals(a.clauseReference(ClauseId("RULE")).contentDigest, b.clauseReference(ClauseId("RULE")).contentDigest)
    }

    @Test
    fun `KC-04 absent scoped revisions and selectors never fall back`() {
        val rev = revision("abc", listOf(clause("A", 0, 2)), listOf(ByteSpan(2, 3)))
        val catalog = RevisionCatalog(listOf(rev))
        val ref = rev.clauseReference(ClauseId("A"))
        listOf(
            sampleKey.copy(revisionId = UUID(0, 99)),
            sampleKey.copy(document = sampleDocument.copy(documentId = UUID(0, 99))),
            sampleKey.copy(document = sampleDocument.copy(projectId = UUID(0, 99))),
        ).forEach { key ->
            assertEquals(ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND), catalog.read(ref.copy(revision = key)))
            assertEquals(ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND), catalog.read(rev.contextReference().copy(revision = key)))
        }
        assertEquals(ReadResult.Failed(ReadFailure.CLAUSE_NOT_FOUND), catalog.read(ref.copy(clauseId = ClauseId("MISSING"))))
        assertFailsWith<IllegalArgumentException> { rev.clauseReference(ClauseId("MISSING")) }
    }

    @Test
    fun `KC-03 KC-04 tampered revision and addressed digests produce distinct failures`() {
        val rev = revision("abc", listOf(clause("A", 0, 2)), listOf(ByteSpan(2, 3)))
        val catalog = RevisionCatalog(listOf(rev))
        val wrong = Digest.parse("0".repeat(64))
        val clause = rev.clauseReference(ClauseId("A"))
        val context = rev.contextReference()
        assertEquals(ReadResult.Failed(ReadFailure.REVISION_DIGEST_MISMATCH), catalog.read(clause.copy(revisionDigest = wrong)))
        assertEquals(ReadResult.Failed(ReadFailure.CONTENT_DIGEST_MISMATCH), catalog.read(clause.copy(contentDigest = wrong)))
        assertEquals(ReadResult.Failed(ReadFailure.REVISION_DIGEST_MISMATCH), catalog.read(context.copy(revisionDigest = wrong)))
        assertEquals(ReadResult.Failed(ReadFailure.CONTENT_DIGEST_MISMATCH), catalog.read(context.copy(contextDigest = wrong)))
        val reinterpreted = revision("abc", listOf(clause("A", 1, 3)), listOf(ByteSpan(0, 1)))
        assertEquals(ReadResult.Failed(ReadFailure.REVISION_DIGEST_MISMATCH), RevisionCatalog(listOf(reinterpreted)).read(clause))
    }

    @Test
    fun `KC-04 revision-local resolution also refuses a foreign identity`() {
        val rev = revision("ab", listOf(clause("A", 0, 1)), listOf(ByteSpan(1, 2)))
        val foreign = sampleKey.copy(document = sampleDocument.copy(projectId = UUID(0, 77)))
        assertEquals(
            ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND),
            rev.clauseRead(rev.clauseReference(ClauseId("A")).copy(revision = foreign)),
        )
        assertEquals(ReadResult.Failed(ReadFailure.REVISION_NOT_FOUND), rev.contextRead(rev.contextReference().copy(revision = foreign)))
    }

    @Test
    fun `KC-04 KC-05 duplicate revision cannot overwrite and input list mutation is isolated`() {
        val original = revision("ab", listOf(clause("A", 0, 2)))
        val input = mutableListOf(original)
        val catalog = RevisionCatalog(input)
        input.clear()
        val edited = revision("cd", listOf(clause("A", 0, 2)))
        assertFailsWith<IllegalArgumentException> { catalog.withRevision(edited) }
        assertFailsWith<IllegalArgumentException> { RevisionCatalog(listOf(original, original)) }
        assertEquals("ab", catalog.clauseText(original.clauseReference(ClauseId("A"))))
    }

    @Test
    fun `KC-05 KC-06 located context fragments resist collection and byte mutation`() {
        val rev = revision("a|b", listOf(clause("A", 1, 2)), listOf(ByteSpan(0, 1), ByteSpan(2, 3)))
        val catalog = RevisionCatalog(listOf(rev))
        val ref = rev.contextReference()
        val read = (catalog.read(ref) as ReadResult.Found).value
        read.fragments[0].content.bytes().fill(0)
        assertFailsWith<UnsupportedOperationException> { (read.fragments as MutableList<LocatedContent>).clear() }
        assertEquals(listOf("a", "b"), read.fragments.map { it.content.text() })
        val repeated = (catalog.read(ref) as ReadResult.Found).value
        assertEquals(read.reference, repeated.reference)
        assertEquals(read.fragments, repeated.fragments)
    }
}

private fun RevisionCatalog.clauseText(reference: ClauseReference): String =
    (read(reference) as ReadResult.Found).value.located.content.text()
