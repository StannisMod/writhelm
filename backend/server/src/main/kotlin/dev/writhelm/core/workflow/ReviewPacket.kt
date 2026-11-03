// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.ClauseReference
import dev.writhelm.core.knowledge.ContextReference
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.KnowledgeReference
import dev.writhelm.core.knowledge.encodeUtf8
import dev.writhelm.core.knowledge.framedDigest
import dev.writhelm.core.knowledge.writeFramed
import java.io.DataOutputStream
import java.util.UUID

/** A complete declared packet, not proof of semantic completeness or human comprehension. */
class ReviewPacket(
    items: List<ReviewItem>,
    val question: String,
    val scope: String,
    val rationale: String,
    consequences: List<String>,
    alternatives: List<String>,
    uncertainties: List<String>,
    contexts: List<ContextReference>,
    dependencies: List<KnowledgeReference> = emptyList(),
    informationGaps: List<String> = emptyList(),
) {
    val items = frozenList(items)
    val consequences = frozenList(consequences)
    val alternatives = frozenList(alternatives)
    val uncertainties = frozenList(uncertainties)
    val contexts = frozenList(contexts)
    val dependencies = frozenList(dependencies)
    val informationGaps = frozenList(informationGaps)

    init {
        listOf(question, scope, rationale).forEach { requireText(it) }
        (this.consequences + this.alternatives + this.uncertainties + this.informationGaps).forEach { requireText(it) }
        require(this.items.isNotEmpty()) { "No decision items" }
        require(this.consequences.isNotEmpty() && this.alternatives.isNotEmpty()) { "Missing decision material" }
        require(this.items.map { it.id }.distinct().size == this.items.size) { "Duplicate review item" }
        require(this.items.map { it.target.subject() }.distinct().size == this.items.size) { "Duplicate target subject" }
        require(this.contexts.distinct().size == this.contexts.size) { "Duplicate context" }
        require(this.dependencies.distinct().size == this.dependencies.size) { "Duplicate dependency" }
    }

    internal val references = frozenList(
        (this.items.flatMap { listOfNotNull(it.before, it.target) } + this.contexts + this.dependencies).distinct()
    )

    val digest: Digest = framedDigest("writhelm.review-packet.v1") {
        writeText(question)
        writeText(scope)
        writeText(rationale)
        writeTexts(this@ReviewPacket.consequences)
        writeTexts(this@ReviewPacket.alternatives)
        writeTexts(this@ReviewPacket.uncertainties)
        writeTexts(this@ReviewPacket.informationGaps)
        writeInt(this@ReviewPacket.items.size)
        this@ReviewPacket.items.forEach {
            writeUuid(it.id)
            writeBoolean(it.before != null)
            it.before?.let { before -> writeReference(before) }
            writeReference(it.target)
        }
        writeReferences(this@ReviewPacket.contexts)
        writeReferences(this@ReviewPacket.dependencies)
    }
}

class ApprovalRequest internal constructor(
    val id: UUID,
    val projectId: UUID,
    val proposedBy: UUID,
    val packet: ReviewPacket,
    generations: Map<SubjectKey, Long>,
) {
    internal val generations = frozenMap(generations)
    val digest = framedDigest("writhelm.approval-request.v1") {
        writeUuid(id)
        writeUuid(projectId)
        writeUuid(proposedBy)
        write(packet.digest.bytes())
        writeInt(packet.items.size)
        packet.items.forEach { writeLong(this@ApprovalRequest.generations.getValue(it.target.subject())) }
    }
}

private fun DataOutputStream.writeText(value: String) = writeFramed(encodeUtf8(value))
private fun DataOutputStream.writeTexts(values: List<String>) {
    writeInt(values.size)
    values.forEach { writeText(it) }
}

private fun DataOutputStream.writeReferences(values: List<KnowledgeReference>) {
    writeInt(values.size)
    values.forEach { writeReference(it) }
}

private fun DataOutputStream.writeReference(value: KnowledgeReference) {
    writeByte(if (value is ClauseReference) 1 else 2)
    writeUuid(value.revision.document.projectId)
    writeUuid(value.revision.document.documentId)
    writeUuid(value.revision.revisionId)
    if (value is ClauseReference) writeText(value.clauseId.value)
    write(value.revisionDigest.bytes())
    write(value.addressedDigest.bytes())
}

private fun DataOutputStream.writeUuid(value: UUID) {
    writeLong(value.mostSignificantBits)
    writeLong(value.leastSignificantBits)
}
