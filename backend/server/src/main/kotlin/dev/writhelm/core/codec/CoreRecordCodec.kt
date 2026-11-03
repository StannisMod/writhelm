// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

/** A typed descriptor defers payload construction until the sorted stream consumes this record. */
internal class RecordDescriptor(val family: String, val key: JsonValue, val payload: () -> JsonValue) {
    fun path(maximumBytes: Int) = CoreRecordCodec.path(family,key,maximumBytes)
}
internal data class RecordDto(val family: String, val key: JsonValue, val payload: JsonValue)
internal object CoreRecordCodec {
    val families = frozenSet(listOf("revisions", "proposals", "grants", "requests", "resolutions", "approvals", "continuity", "assessments", "manifests", "heads", "generations", "blocked"))
    fun header(): JsonValue = obj("formatVersion" to num(1), "modelVersion" to num(1))
    fun checkHeader(v: JsonValue) { val f=v.fields("formatVersion","modelVersion"); version(f.getValue("formatVersion")); version(f.getValue("modelVersion")) }
    fun envelope(family: String, key: JsonValue, payload: JsonValue): JsonValue = obj("formatVersion" to num(1),"family" to str(family),"key" to key,"payload" to payload)
    fun path(family: String, key: JsonValue, maximumBytes: Int=16*1024*1024): String {
        val h=framedDigest("writhelm.record-key.v1") { writeFramed(encodeUtf8(family)); writeFramed(StrictJson.encode(key,maximumBytes,true)) }.hex
        return "core/records/$family/${h.take(2)}/$h.json"
    }
    fun decode(v: JsonValue, expectedPath: String, maximumBytes: Int=16*1024*1024): RecordDto {
        val f=v.fields("formatVersion","family","key","payload"); version(f.getValue("formatVersion"))
        val family=f.getValue("family").string(); if(family !in families) refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val key=when(family) {
            "revisions","proposals" -> revisionKey(revisionKey(f.getValue("key")))
            "grants" -> { val k=f.getValue("key").fields("principalId","projectId"); obj("principalId" to uuid(uuid(k.getValue("principalId"))),"projectId" to uuid(uuid(k.getValue("projectId")))) }
            "requests","resolutions","assessments","manifests" -> uuid(uuid(f.getValue("key")))
            "heads","generations" -> subject(subject(f.getValue("key")))
            else -> reference(reference(f.getValue("key")))
        }
        graph(path(family,key,maximumBytes)==expectedPath)
        return RecordDto(family,key,f.getValue("payload"))
    }
    fun records(s: WorkflowSnapshot): Sequence<RecordDescriptor> = sequence {
        s.revisions.forEach { r -> yield(RecordDescriptor("revisions",revisionKey(r.key)) { obj("bodyDigest" to digest(r.content.digest),"bodyBytes" to num(r.content.sizeBytes),"structure" to structure(r.structure),"structureDigest" to digest(r.structure.digest),"revisionDigest" to digest(r.digest),"contextDigest" to digest(r.contextDigest)) }) }
        s.proposals.forEach { (k,v) -> yield(RecordDescriptor("proposals",revisionKey(k)) { obj("principalId" to uuid(v.principalId)) }) }
        s.grants.forEach { v -> yield(RecordDescriptor("grants",obj("principalId" to uuid(v.principalId),"projectId" to uuid(v.projectId))) { obj("capabilities" to array(v.capabilities.sortedBy { it.name }) { str(it.name) }) }) }
        s.requests.forEach { (k,v) -> yield(RecordDescriptor("requests",uuid(k)) { obj("projectId" to uuid(v.projectId),"proposedBy" to uuid(v.proposedBy),"packet" to packet(v.packet),"generations" to sortedMap(v.generations,::subject) { key,value -> obj("subject" to subject(key),"generation" to JNumber(value)) },"packetDigest" to digest(v.packet.digest),"requestDigest" to digest(v.digest)) }) }
        s.resolutions.forEach { (k,v) -> yield(RecordDescriptor("resolutions",uuid(k)) { obj("actorId" to uuid(v.actor.principalId),"answer" to answer(v.answer),"approved" to array(v.approved,::reference)) }) }
        s.approvals.forEach { (k,v) -> yield(RecordDescriptor("approvals",reference(k)) { trace(v) }) }
        s.continuity.forEach { (k,v) -> yield(RecordDescriptor("continuity",reference(k)) { obj("source" to reference(v.source),"assessmentId" to uuid(v.assessmentId)) }) }
        s.assessments.forEach { (k,v) -> yield(RecordDescriptor("assessments",uuid(k)) { with(v.assessment) { obj("projectId" to uuid(projectId),"actorId" to uuid(v.actor.principalId),"references" to array(references,::reference),"rationale" to str(rationale),"links" to array(links,::link),"blocked" to array(blocked,::reference)) } }) }
        s.manifests.forEach { (k,v) -> yield(RecordDescriptor("manifests",uuid(k)) { obj("consumerId" to uuid(v.consumerId),"projectId" to uuid(v.projectId),"createdBy" to uuid(v.createdBy),"clauses" to array(v.clauses,::reference),"contexts" to array(v.contexts,::reference),"dependencies" to array(v.dependencies,::reference),"assessmentId" to uuid(v.assessmentId),"provenance" to sortedMap(v.provenance,::reference) { key,value -> obj("reference" to reference(key),"trace" to trace(value)) }) }) }
        s.heads.forEach { (k,v) -> yield(RecordDescriptor("heads",subject(k)) { obj("reference" to reference(v)) }) }
        s.generations.forEach { (k,v) -> yield(RecordDescriptor("generations",subject(k)) { obj("generation" to JNumber(v)) }) }
        s.blocked.forEach { yield(RecordDescriptor("blocked",reference(it)) { obj() }) }
    }
}

internal fun uuid(v: UUID): JsonValue = str(v.toString())
internal fun uuid(v: JsonValue): UUID { val s=v.string(); shape(s.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))); return UUID.fromString(s) }
internal fun digest(v: Digest): JsonValue = str(v.hex)
internal fun digest(v: JsonValue): Digest = checked { Digest.parse(v.string()) }
internal fun document(v: DocumentKey): JsonValue = obj("projectId" to uuid(v.projectId),"documentId" to uuid(v.documentId))
internal fun document(v: JsonValue): DocumentKey { val f=v.fields("projectId","documentId"); return DocumentKey(uuid(f.getValue("projectId")),uuid(f.getValue("documentId"))) }
internal fun revisionKey(v: RevisionKey): JsonValue = obj("document" to document(v.document),"revisionId" to uuid(v.revisionId))
internal fun revisionKey(v: JsonValue): RevisionKey { val f=v.fields("document","revisionId"); return RevisionKey(document(f.getValue("document")),uuid(f.getValue("revisionId"))) }
internal fun subject(v: SubjectKey): JsonValue = obj("document" to document(v.document),"clauseId" to (v.clauseId?.let { str(it.value) } ?: JNull))
internal fun subject(v: JsonValue): SubjectKey { val f=v.fields("document","clauseId"); return SubjectKey(document(f.getValue("document")),f.getValue("clauseId").let { if(it==JNull) null else checked { ClauseId(it.string()) } }) }
internal fun reference(v: KnowledgeReference): JsonValue = when(v) {
    is ClauseReference -> obj("kind" to str("clause"),"revision" to revisionKey(v.revision),"revisionDigest" to digest(v.revisionDigest),"clauseId" to str(v.clauseId.value),"contentDigest" to digest(v.contentDigest))
    is ContextReference -> obj("kind" to str("context"),"revision" to revisionKey(v.revision),"revisionDigest" to digest(v.revisionDigest),"contextDigest" to digest(v.contextDigest))
}
internal fun reference(v: JsonValue): KnowledgeReference {
    val fields=(v as? JObject)?.entries ?: refuse(CodecProblem.INVALID_SHAPE)
    return when(fields["kind"]?.string()) {
        "clause" -> { val f=v.fields("kind","revision","revisionDigest","clauseId","contentDigest"); ClauseReference(revisionKey(f.getValue("revision")),checked { ClauseId(f.getValue("clauseId").string()) },digest(f.getValue("revisionDigest")),digest(f.getValue("contentDigest"))) }
        "context" -> { val f=v.fields("kind","revision","revisionDigest","contextDigest"); ContextReference(revisionKey(f.getValue("revision")),digest(f.getValue("revisionDigest")),digest(f.getValue("contextDigest"))) }
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
}
internal fun clause(v: JsonValue) = reference(v) as? ClauseReference ?: refuse(CodecProblem.INVALID_SHAPE)
internal fun context(v: JsonValue) = reference(v) as? ContextReference ?: refuse(CodecProblem.INVALID_SHAPE)
internal fun span(v: ByteSpan): JsonValue = obj("start" to num(v.start),"endExclusive" to num(v.endExclusive))
internal fun span(v: JsonValue): ByteSpan { val f=v.fields("start","endExclusive"); return checked { ByteSpan(f.getValue("start").int(),f.getValue("endExclusive").int()) } }
internal fun structure(v: StructureIndex): JsonValue = obj("clauses" to array(v.clauses) { obj("id" to str(it.id.value),"span" to span(it.span)) },"context" to array(v.context,::span))
internal fun structure(v: JsonValue): StructureIndex {
    val f=v.fields("clauses","context")
    return checked { StructureIndex(f.getValue("clauses").list().map { val c=it.fields("id","span"); ClauseSpan(ClauseId(c.getValue("id").string()),span(c.getValue("span"))) },f.getValue("context").list().map(::span)) }
}
internal fun packet(v: ReviewPacket): JsonValue = obj("items" to array(v.items) { obj("id" to uuid(it.id),"target" to reference(it.target),"before" to (it.before?.let(::reference) ?: JNull)) },"question" to str(v.question),"scope" to str(v.scope),"rationale" to str(v.rationale),"consequences" to array(v.consequences,::str),"alternatives" to array(v.alternatives,::str),"uncertainties" to array(v.uncertainties,::str),"contexts" to array(v.contexts,::reference),"dependencies" to array(v.dependencies,::reference),"informationGaps" to array(v.informationGaps,::str))
internal fun packet(v: JsonValue): ReviewPacket {
    val f=v.fields("items","question","scope","rationale","consequences","alternatives","uncertainties","contexts","dependencies","informationGaps")
    fun texts(k: String)=f.getValue(k).list().map { it.string() }
    return checked { ReviewPacket(f.getValue("items").list().map { val i=it.fields("id","target","before"); ReviewItem(uuid(i.getValue("id")),reference(i.getValue("target")),i.getValue("before").let { before -> if(before==JNull) null else reference(before) }) },f.getValue("question").string(),f.getValue("scope").string(),f.getValue("rationale").string(),texts("consequences"),texts("alternatives"),texts("uncertainties"),f.getValue("contexts").list().map(::context),f.getValue("dependencies").list().map(::reference),texts("informationGaps")) }
}
internal fun answer(v: StructuredAnswer): JsonValue = when(v) {
    StructuredAnswer.Approve -> obj("kind" to str("approve"))
    StructuredAnswer.Reject -> obj("kind" to str("reject"))
    is StructuredAnswer.ChangesRequired -> {
        JsonConstruction.collection(v.comments.size)
        obj("kind" to str("changes-required"),"comments" to array(v.comments.entries.sortedBy { it.key.toString() }) { obj("itemId" to uuid(it.key),"text" to str(it.value)) })
    }
}
internal fun answer(v: JsonValue): StructuredAnswer {
    val fields=(v as? JObject)?.entries ?: refuse(CodecProblem.INVALID_SHAPE)
    return when(fields["kind"]?.string()) {
        "approve" -> { v.fields("kind"); StructuredAnswer.Approve }
        "reject" -> { v.fields("kind"); StructuredAnswer.Reject }
        "changes-required" -> { val f=v.fields("kind","comments"); val comments=LinkedHashMap<UUID,String>(); f.getValue("comments").list().forEach { val c=it.fields("itemId","text"); uniquePut(comments,uuid(c.getValue("itemId")),c.getValue("text").string()) }; checked { StructuredAnswer.ChangesRequired(comments) } }
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
}
internal fun link(v: ContinuityLink): JsonValue = obj("source" to reference(v.source),"target" to reference(v.target))
internal fun link(v: JsonValue): ContinuityLink { val f=v.fields("source","target"); return ContinuityLink(reference(f.getValue("source")),reference(f.getValue("target"))) }
internal fun trace(v: ApprovalTrace): JsonValue = obj("origin" to reference(v.origin),"requestId" to uuid(v.requestId),"approvedBy" to uuid(v.approvedBy),"assessmentIds" to array(v.assessmentIds,::uuid))
internal fun trace(v: JsonValue): ApprovalTrace { val f=v.fields("origin","requestId","approvedBy","assessmentIds"); return ApprovalTrace(reference(f.getValue("origin")),uuid(f.getValue("requestId")),uuid(f.getValue("approvedBy")),f.getValue("assessmentIds").list().map(::uuid)) }
internal fun <T> array(v: Collection<T>, encode: (T)->JsonValue): JsonValue {
    JsonConstruction.collection(v.size)
    return JArray(v.map(encode))
}
internal fun <K,V> sortedMap(v: Map<K,V>, key: (K)->JsonValue, encode: (K,V)->JsonValue): JsonValue {
    JsonConstruction.collection(v.size)
    // Sorting keys are temporary, not decoded record nodes. Compute once, outside construction meter.
    val keys=v.entries.map { entry ->
        val maximumBytes=JsonConstruction.encodingLimit()
        val bytes=JsonConstruction.withoutMeter { StrictJson.encode(key(entry.key),maximumBytes,true) }
        JsonConstruction.temporary(bytes.size)
        entry to bytes
    }
    return JArray(keys.sortedWith { a,b -> unsignedCompare(a.second,b.second) }.map { encode(it.first.key,it.first.value) })
}
internal fun unsignedCompare(a: ByteArray,b: ByteArray): Int { for(i in 0 until minOf(a.size,b.size)) { val c=(a[i].toInt() and 255).compareTo(b[i].toInt() and 255); if(c!=0)return c }; return a.size.compareTo(b.size) }
internal fun <T> checked(block: ()->T): T = try { block() } catch(_: IllegalArgumentException) { refuse(CodecProblem.INVALID_SHAPE) }
internal fun <K,V> uniquePut(map: MutableMap<K,V>,key: K,value: V) { if(map.containsKey(key))refuse(CodecProblem.DUPLICATE_IDENTITY); map[key]=value }
