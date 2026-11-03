// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.util.Collections

data class ByteSpan(val start: Int, val endExclusive: Int) {
    init {
        require(start >= 0 && endExclusive > start) { "Expected a nonempty forward byte range" }
    }
}

@JvmInline
value class ClauseId(val value: String) {
    init {
        require(value.isNotBlank() && value == value.trim() && value.none { it.isISOControl() }) {
            "Expected a nonblank stable clause identity without surrounding whitespace or controls"
        }
        encodeUtf8(value)
    }
}

data class ClauseSpan(val id: ClauseId, val span: ByteSpan)

/** A full, explicit partition, not a heading matcher or a semantic dependency extractor. */
class StructureIndex(clauses: List<ClauseSpan>, context: List<ByteSpan>) {
    val clauses: List<ClauseSpan> = immutableList(clauses)
    val context: List<ByteSpan> = immutableList(context)

    init {
        require(this.clauses.map { it.id }.distinct().size == this.clauses.size) { "Duplicate clause identity" }
        require(this.clauses.zipWithNext().all { (a, b) -> a.span.start < b.span.start }) {
            "Clause ranges must be in document order"
        }
        require(this.context.zipWithNext().all { (a, b) -> a.start < b.start }) {
            "Context ranges must be in document order"
        }
    }

    val digest: Digest = framedDigest("writhelm.structure-index.v1") {
        writeInt(this@StructureIndex.clauses.size)
        this@StructureIndex.clauses.forEach {
            writeFramed(encodeUtf8(it.id.value))
            writeInt(it.span.start)
            writeInt(it.span.endExclusive)
        }
        writeInt(this@StructureIndex.context.size)
        this@StructureIndex.context.forEach {
            writeInt(it.start)
            writeInt(it.endExclusive)
        }
    }

    internal fun validate(content: Utf8Content) {
        val spans = (clauses.map { it.span } + context).sortedBy { it.start }
        var end = 0
        for (span in spans) {
            require(span.start == end) { "Structure has a gap or overlap" }
            require(span.endExclusive <= content.sizeBytes) { "Structure exceeds content" }
            require(content.isBoundary(span.start) && content.isBoundary(span.endExclusive)) {
                "Structure splits a UTF-8 codepoint"
            }
            end = span.endExclusive
        }
        require(end == content.sizeBytes) { "Structure does not cover all content" }
    }

    internal fun clause(id: ClauseId): ClauseSpan? = clauses.find { it.id == id }
}

internal fun <T> immutableList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
