// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal sealed interface CommandPayload {
    val operation: String
    class Propose(val revision: DocumentRevision): CommandPayload { override val operation="propose_revision" }
    class Request(val requestId: UUID,val packet: ReviewPacket): CommandPayload { override val operation="request_approval" }
    class Resolve(val requestId: UUID,val requestDigest: Digest,val packetDigest: Digest,val answer: StructuredAnswer): CommandPayload { override val operation="resolve_approval" }
    class Assess(val assessment: ConsistencyAssessment): CommandPayload { override val operation="record_assessment" }
    class Pin(val manifestId: UUID,val consumerId: UUID,clauses: List<ClauseReference>,contexts: List<ContextReference>,dependencies: List<KnowledgeReference>,val assessmentId: UUID): CommandPayload {
        override val operation="pin_consumer"; val clauses=frozenList(clauses); val contexts=frozenList(contexts); val dependencies=frozenList(dependencies)
    }
    class Revoke(val principalId: UUID): CommandPayload { override val operation="revoke_access" }
}
internal class CoreCommand(val requestKey: UUID,val projectId: UUID,val payload: CommandPayload) {
    init { if(payload is CommandPayload.Assess)shape(payload.assessment.projectId==projectId) }
}
/** Decode is structural admission, not execution, authentication or an existence check in H. */
internal class CommandCodec(private val limits: JsonLimits=JsonLimits(bytes=8*1024*1024),private val bodyBytes: Int=1024*1024) {
    init { require(bodyBytes>0) }
    fun decode(bytes: ByteArray): CoreCommand {
        val f=StrictJson.decode(bytes,limits).fields("formatVersion","requestKey","projectId","operation","payload"); version(f.getValue("formatVersion"))
        val project=uuid(f.getValue("projectId")); val p=f.getValue("payload")
        val payload=when(f.getValue("operation").string()) {
            "propose_revision" -> { val q=p.fields("revisionKey","body","structure"); val k=revisionKey(q.getValue("revisionKey")); shape(k.document.projectId==project)
                val b=checked { Utf8Content.fromText(q.getValue("body").string()) }; if(b.sizeBytes>bodyBytes)refuse(CodecProblem.ADMISSION_LIMIT)
                val s=structure(q.getValue("structure")); if(s.clauses.size.toLong()+s.context.size>limits.collection)refuse(CodecProblem.ADMISSION_LIMIT)
                CommandPayload.Propose(checked { DocumentRevision(k,b,s) }) }
            "request_approval" -> { val q=p.fields("requestId","packet"); CommandPayload.Request(uuid(q.getValue("requestId")),packet(q.getValue("packet"))) }
            "resolve_approval" -> { val q=p.fields("requestId","requestDigest","packetDigest","answer"); CommandPayload.Resolve(uuid(q.getValue("requestId")),digest(q.getValue("requestDigest")),digest(q.getValue("packetDigest")),answer(q.getValue("answer"))) }
            "record_assessment" -> { val q=p.fields("assessmentId","references","rationale","links","blocked"); CommandPayload.Assess(checked { ConsistencyAssessment(uuid(q.getValue("assessmentId")),project,q.getValue("references").list().map(::reference),q.getValue("rationale").string(),q.getValue("links").list().map(::link),q.getValue("blocked").list().map(::reference)) }) }
            "pin_consumer" -> { val q=p.fields("manifestId","consumerId","clauses","contexts","dependencies","assessmentId"); val c=q.getValue("clauses").list().map(::clause); val x=q.getValue("contexts").list().map(::context); val d=q.getValue("dependencies").list().map(::reference)
                shape(c.isNotEmpty()); listOf(c,x,d).forEach { shape(it.distinct().size==it.size) }
                CommandPayload.Pin(uuid(q.getValue("manifestId")),uuid(q.getValue("consumerId")),c,x,d,uuid(q.getValue("assessmentId"))) }
            "revoke_access" -> { val q=p.fields("principalId"); CommandPayload.Revoke(uuid(q.getValue("principalId"))) }
            else -> refuse(CodecProblem.INVALID_SHAPE)
        }
        val refs=when(payload) {
            is CommandPayload.Request -> payload.packet.references
            is CommandPayload.Assess -> with(payload.assessment) { references+links.flatMap { listOf(it.source,it.target) }+blocked }
            is CommandPayload.Pin -> payload.clauses+payload.contexts+payload.dependencies
            else -> emptyList()
        }
        shape(refs.all { it.revision.document.projectId==project })
        return CoreCommand(uuid(f.getValue("requestKey")),project,payload)
    }
    fun encode(c: CoreCommand): ByteArray {
        if(c.payload is CommandPayload.Propose && c.payload.revision.content.sizeBytes>bodyBytes)refuse(CodecProblem.ADMISSION_LIMIT)
        return JsonConstruction.bounded(limits,{ _,_-> },false) { StrictJson.encode(envelope(c,true),limits.bytes) }.also { decode(it) }
    }
    fun fingerprint(c: CoreCommand): Digest {
        val fields=(StrictJson.decode(encode(c),limits) as JObject).entries
        val b=StrictJson.encode(JObject(fields-"requestKey"),limits.bytes)
        return framedDigest("writhelm.command.v1") { writeFramed(b) }
    }
    private fun envelope(c: CoreCommand,withKey: Boolean): JsonValue {
        val f=linkedMapOf("formatVersion" to num(1),"projectId" to uuid(c.projectId),"operation" to str(c.payload.operation),"payload" to payload(c.payload))
        if(withKey)f["requestKey"]=uuid(c.requestKey); return JObject(f)
    }
    private fun payload(v: CommandPayload): JsonValue=when(v) {
        is CommandPayload.Propose -> obj("revisionKey" to revisionKey(v.revision.key),"body" to str(v.revision.content.text()),"structure" to structure(v.revision.structure))
        is CommandPayload.Request -> obj("requestId" to uuid(v.requestId),"packet" to packet(v.packet))
        is CommandPayload.Resolve -> obj("requestId" to uuid(v.requestId),"requestDigest" to digest(v.requestDigest),"packetDigest" to digest(v.packetDigest),"answer" to answer(v.answer))
        is CommandPayload.Assess -> with(v.assessment) { obj("assessmentId" to uuid(id),"references" to array(references,::reference),"rationale" to str(rationale),"links" to array(links,::link),"blocked" to array(blocked,::reference)) }
        is CommandPayload.Pin -> obj("manifestId" to uuid(v.manifestId),"consumerId" to uuid(v.consumerId),"clauses" to array(v.clauses,::reference),"contexts" to array(v.contexts,::reference),"dependencies" to array(v.dependencies,::reference),"assessmentId" to uuid(v.assessmentId))
        is CommandPayload.Revoke -> obj("principalId" to uuid(v.principalId))
    }
}
