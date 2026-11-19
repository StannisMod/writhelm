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
    val followup: ReviewFollowup? = null,
    presentations: List<ReviewPresentation> = emptyList(),
) {
    val presentations = frozenList(presentations)
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
        require(this.presentations.size <= 8)
        require(this.presentations.map { it.language }.distinct().size == this.presentations.size)
        var presentationBytes = 0L
        this.presentations.forEach { presentation ->
            require(presentation.items.map { it.itemId }.toSet() == this.items.map { it.id }.toSet())
            presentation.items.forEach { presentationBytes += encodeUtf8(it.title).size + encodeUtf8(it.text).size }
        }
        require(presentationBytes <= 1024 * 1024)
    }

    internal val references = frozenList(
        (this.items.flatMap { listOfNotNull(it.before, it.target) } + this.contexts + this.dependencies).distinct()
    )

    val digest: Digest = framedDigest(if(this.presentations.isNotEmpty())"writhelm.review-packet.v3" else if(followup==null)"writhelm.review-packet.v1" else "writhelm.review-packet.v2") {
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
        if(this@ReviewPacket.presentations.isNotEmpty())writeBoolean(followup!=null)
        followup?.let { previous ->
            writeUuid(previous.requestId);write(previous.requestDigest.bytes());write(previous.packetDigest.bytes())
            writeInt(previous.items.size)
            previous.items.forEach { item ->
                writeBoolean(item.previousItemId!=null);item.previousItemId?.let {writeUuid(it)}
                writeBoolean(item.itemId!=null);item.itemId?.let {writeUuid(it)}
                writeText(item.disposition);writeText(item.explanation)
            }
        }
        if(this@ReviewPacket.presentations.isNotEmpty()) {
            writeInt(this@ReviewPacket.presentations.size)
            this@ReviewPacket.presentations.forEach { presentation ->
                writeText(presentation.language);writeText(presentation.sourceLanguage)
                writeInt(presentation.items.size)
                presentation.items.forEach { writeUuid(it.itemId);writeText(it.title);writeText(it.text) }
            }
        }
    }
}

/** Mechanical identity binding, not proof of translation equivalence. */
class ReviewPresentation(val language: String, val sourceLanguage: String, items: List<ReviewPresentationItem>) {
    val items = frozenList(items)
    init {
        listOf(language, sourceLanguage).forEach {
            require(Regex("^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$").matches(it))
        }
        require(this.items.isNotEmpty())
        require(this.items.map { it.itemId }.distinct().size == this.items.size)
    }
}
data class ReviewPresentationItem(val itemId: UUID, val title: String, val text: String) {
    init {
        requireText(title);requireText(text)
        require(title.codePointCount(0,title.length) <= 200)
        encodeUtf8(title)
        require(encodeUtf8(text).size <= 65536)
    }
}

/** Explicit authored correspondence, never inferred from paragraph position or similarity. */
data class ReviewFollowupItem(val previousItemId:UUID?,val itemId:UUID?,val disposition:String,val explanation:String) {
    init {
        requireText(explanation)
        require(explanation.toByteArray(Charsets.UTF_8).size<=4096)
        require(when(disposition) {
            "revised","unchanged" -> previousItemId!=null&&itemId!=null
            "removed","retained" -> previousItemId!=null&&itemId==null
            "added" -> previousItemId==null&&itemId!=null
            else -> false
        })
    }
}
class ReviewFollowup(val requestId:UUID,val requestDigest:Digest,val packetDigest:Digest,items:List<ReviewFollowupItem>) {
    val items=frozenList(items)
    init {
        require(this.items.isNotEmpty()&&this.items.size<=256)
        val old=this.items.mapNotNull {it.previousItemId};val next=this.items.mapNotNull {it.itemId}
        require(old.distinct().size==old.size&&next.distinct().size==next.size)
    }
}

/** Shared admission/restore law. Retention never approves a previously unapproved point. */
internal fun validReviewFollowup(packet:ReviewPacket,requests:Map<UUID,ApprovalRequest>,
    resolutions:Map<UUID,ApprovalResolution>,approved:Set<KnowledgeReference>):Boolean {
    val link=packet.followup ?: return true
    val previous=requests[link.requestId] ?: return false
    if(previous.digest!=link.requestDigest||previous.packet.digest!=link.packetDigest||link.requestId !in resolutions)return false
    val old=previous.packet.items.associateBy {it.id};val next=packet.items.associateBy {it.id}
    if(link.items.mapNotNull {it.previousItemId}.toSet()!=old.keys||
        link.items.mapNotNull {it.itemId}.toSet()!=next.keys)return false
    if(packet.items.any {it.target.revision.document.projectId!=previous.projectId})return false
    return link.items.all { change ->
        val before=change.previousItemId?.let {old[it]?.target}
        val target=change.itemId?.let {next[it]?.target}
        when(change.disposition) {
            "unchanged" -> before==target
            "revised" -> before!=null&&target!=null&&before.subject()==target.subject()&&before!=target
            "retained" -> before in approved&&before in packet.dependencies
            "removed" -> before!=null
            "added" -> target!=null
            else -> false
        }
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
