// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

/**
 * Structural protocol declarations. JSON Schema, operation documentation and structural admission
 * share this declaration; domain construction/authority/history checks remain independent.
 * No historical encoding or fingerprint is defined by a model-facing projection.
 */
internal sealed class ProtocolShape {
    abstract fun accepts(v: JsonValue): Boolean
    abstract fun schema(): JsonValue
    class Text(private val maximum: Int=1024*1024,private val pattern:String?=null):ProtocolShape() {
        override fun accepts(v:JsonValue)=v is JString&&v.value.codePointCount(0,v.value.length)<=maximum&&
            (pattern==null||Regex(pattern).containsMatchIn(v.value))
        override fun schema()=JObject(linkedMapOf("type" to str("string"),"maxLength" to num(maximum)).also {
            if(pattern!=null)it["pattern"]=str(pattern)
        })
    }
    class Integer(private val minimum:Long,private val maximum:Long):ProtocolShape() {
        override fun accepts(v:JsonValue)=v is JNumber&&v.value in minimum..maximum
        override fun schema()=obj("type" to str("integer"),"minimum" to JNumber(minimum),"maximum" to JNumber(maximum))
    }
    class Constant(private val value:JsonValue):ProtocolShape() {
        override fun accepts(v:JsonValue)=v==value
        override fun schema()=obj("const" to value)
    }
    class ListOf(private val item:ProtocolShape,private val minimum:Int=0,private val maximum:Int=4096):ProtocolShape() {
        override fun accepts(v:JsonValue)=v is JArray&&v.values.size in minimum..maximum&&v.values.all(item::accepts)
        override fun schema()=obj("type" to str("array"),"minItems" to num(minimum),"maxItems" to num(maximum),"items" to item.schema())
    }
    class Record(private val required:Map<String,ProtocolShape>,private val optional:Map<String,ProtocolShape> = emptyMap()):ProtocolShape() {
        override fun accepts(v:JsonValue)=v is JObject&&v.entries.keys.containsAll(required.keys)&&
            v.entries.all { (name,value)->(required[name]?:optional[name])?.accepts(value)==true }
        override fun schema()=obj("type" to str("object"),"additionalProperties" to JBoolean(false),
            "required" to array(required.keys,::str),"properties" to JObject((required+optional).mapValues {it.value.schema()}))
    }
    class Choice(private vararg val choices:ProtocolShape):ProtocolShape() {
        override fun accepts(v:JsonValue)=choices.any {it.accepts(v)}
        override fun schema()=obj("anyOf" to JArray(choices.map {it.schema()}))
    }
    class Ref(private val name:String):ProtocolShape() {
        override fun accepts(v:JsonValue)=KnowledgeProtocol.definitions.getValue(name).accepts(v)
        override fun schema()=obj("\$ref" to str("#/\$defs/$name"))
    }
}

internal object KnowledgeProtocol {
    private fun text(n:Int=1024*1024,pattern:String?=null)=ProtocolShape.Text(n,pattern)
    private fun ref(name:String)=ProtocolShape.Ref(name)
    private fun record(vararg fields:Pair<String,ProtocolShape>)=ProtocolShape.Record(linkedMapOf(*fields))
    private fun list(item:ProtocolShape,min:Int=0,max:Int=4096)=ProtocolShape.ListOf(item,min,max)
    private fun literal(v:String)=ProtocolShape.Constant(str(v))
    private fun nullable(shape:ProtocolShape)=ProtocolShape.Choice(shape,ProtocolShape.Constant(JNull))
    private val uuid=text(36,"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val digest=text(64,"^[0-9a-f]{64}$")
    private val language=text(12,"^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$")
    private fun presentations(itemField:String,itemIdentity:ProtocolShape)=list(record(
        "language" to language,"sourceLanguage" to language,"items" to list(ProtocolShape.Record(linkedMapOf(
            itemField to itemIdentity,"title" to text(200,"\\S"),"text" to text(65536,"\\S")),
            mapOf("explanation" to text(4096,"\\S"))),1)),0,8)
    val definitions:Map<String,ProtocolShape> = linkedMapOf(
        "uuid" to uuid,"digest" to digest,
        "documentKey" to record("projectId" to uuid,"documentId" to uuid),
        "revisionKey" to record("document" to ref("documentKey"),"revisionId" to uuid),
        "span" to record("start" to ProtocolShape.Integer(0,Int.MAX_VALUE.toLong()),"endExclusive" to ProtocolShape.Integer(1,Int.MAX_VALUE.toLong())),
        "structure" to record("clauses" to list(record("id" to text(),"span" to ref("span"))),"context" to list(ref("span"))),
        "clause" to record("kind" to literal("clause"),"revision" to ref("revisionKey"),"revisionDigest" to digest,
            "clauseId" to text(),"contentDigest" to digest),
        "context" to record("kind" to literal("context"),"revision" to ref("revisionKey"),"revisionDigest" to digest,"contextDigest" to digest),
        "reference" to ProtocolShape.Choice(ref("clause"),ref("context")),
        "item" to record("id" to uuid,"target" to ref("reference"),"before" to nullable(ref("reference"))),
        "followupItem" to record("previousItemId" to nullable(uuid),"itemId" to nullable(uuid),
            "disposition" to ProtocolShape.Choice(*listOf("revised","unchanged","added","removed","retained").map(::literal).toTypedArray()),
            "explanation" to text(4096,"\\S")),
        "followup" to record("requestId" to uuid,"requestDigest" to digest,"packetDigest" to digest,
            "items" to list(ref("followupItem"),1,256)),
        "packet" to ProtocolShape.Record(linkedMapOf("items" to list(ref("item"),1),"question" to text(),"scope" to text(),"rationale" to text(),
            "consequences" to list(text()),"alternatives" to list(text()),"uncertainties" to list(text()),
            "contexts" to list(ref("context")),"dependencies" to list(ref("reference")),"informationGaps" to list(text())),
            mapOf("followup" to ref("followup"),"presentations" to presentations("itemId",uuid))),
        "answer" to ProtocolShape.Choice(record("kind" to literal("approve")),record("kind" to literal("reject")),
            record("kind" to literal("reject-with-reason"),"reason" to text(4096,"\\S")),
            record("kind" to literal("changes-required"),"comments" to list(record("itemId" to uuid,"text" to text()),1))),
        "modelSource" to ProtocolShape.Record(mapOf("reference" to text(32,"^wr1_[A-Za-z0-9_-]{24}$")),
            mapOf("clauseId" to text(128))),
        "reviewSource" to ProtocolShape.Choice(ref("reference"),record("kind" to literal("revision"),
            "revisionKey" to ref("revisionKey"),"revisionDigest" to digest,"clauseId" to nullable(text(128)))),
        "reviewDraft" to ProtocolShape.Record(linkedMapOf(
            "items" to list(record("id" to uuid,"target" to ref("reviewSource"),"before" to nullable(ref("reviewSource"))),1,256),
            "question" to text(4096,"\\S"),"scope" to text(8192,"\\S"),"rationale" to text(8192,"\\S"),
            "consequences" to list(text(4096),0,64),"alternatives" to list(text(4096),0,64),
            "uncertainties" to list(text(4096),0,64),"contexts" to list(ref("reviewSource"),0,256),
            "dependencies" to list(ref("reviewSource"),0,256),"informationGaps" to list(text(4096),0,64)),
            mapOf("presentations" to presentations("itemId",uuid),
                "followup" to record("requestId" to uuid,"items" to list(ref("followupItem"),1,256)))),
    )
    val payloads:Map<String,ProtocolShape> = linkedMapOf(
        "propose_revision" to record("revisionKey" to ref("revisionKey"),"body" to text(),"structure" to ref("structure")),
        "request_approval" to record("requestId" to uuid,"packet" to ref("packet")),
        "submit_review" to record("requestId" to uuid,"draft" to ref("reviewDraft")),
        "resolve_approval" to record("requestId" to uuid,"requestDigest" to digest,"packetDigest" to digest,"answer" to ref("answer")),
        "record_assessment" to record("assessmentId" to uuid,"references" to list(ref("reference")),"rationale" to text(),
            "links" to list(record("source" to ref("reference"),"target" to ref("reference"))),"blocked" to list(ref("reference"))),
        "pin_consumer" to record("manifestId" to uuid,"consumerId" to uuid,"clauses" to list(ref("clause"),1),
            "contexts" to list(ref("context")),"dependencies" to list(ref("reference")),"assessmentId" to uuid),
        "revoke_access" to record("principalId" to uuid),
    )
    val reads:Map<String,ProtocolShape> = linkedMapOf(
        "list_projects" to record("cursor" to nullable(text()),"pageSize" to ProtocolShape.Integer(1,32)),
        "list_documents" to record("cursor" to nullable(text()),"pageSize" to ProtocolShape.Integer(1,32)),
        "list_revisions" to record("documentId" to uuid,"cursor" to nullable(text()),"pageSize" to ProtocolShape.Integer(1,32)),
        "list_requests" to record("pendingOnly" to ProtocolShape.Choice(ProtocolShape.Constant(JBoolean(true)),ProtocolShape.Constant(JBoolean(false))),
            "cursor" to nullable(text()),"pageSize" to ProtocolShape.Integer(1,32)),
        "list_manifests" to record("cursor" to nullable(text()),"pageSize" to ProtocolShape.Integer(1,32)),
        "read_revision" to record("revisionKey" to ref("revisionKey"),"revisionDigest" to digest),
        "read_clause" to ref("clause"),"read_context" to ref("context"),"read_approval_trace" to ref("reference"),
        "read_request" to record("id" to uuid),"read_resolution" to record("id" to uuid),"read_manifest" to record("id" to uuid),
        "read_review_bundle" to record("id" to uuid,"requestDigest" to digest,"packetDigest" to digest),
    )
    fun validateCommand(value:JsonValue) {
        val f=value.fields("formatVersion","requestKey","projectId","operation","payload")
        val operation=f.getValue("operation").string()
        shape((payloads[operation]?:refuse(CodecProblem.INVALID_SHAPE)).accepts(f.getValue("payload")))
    }
    private fun schema(shape:ProtocolShape)=JObject((shape.schema() as JObject).entries+("\$defs" to JObject(definitions.mapValues {it.value.schema()})))
    private val intent=text(128,"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    val authoring:Map<String,ProtocolShape> = linkedMapOf(
        "propose_document" to ProtocolShape.Record(linkedMapOf("intentId" to intent,
            "clauses" to list(record("id" to text(128,"\\S"),"title" to text(200,"\\S"),"text" to text(65536,"\\S")),1,256)),
            mapOf("documentId" to uuid,"context" to text(65536))),
        "request_review" to ProtocolShape.Record(linkedMapOf("intentId" to intent,
            "items" to list(record("key" to text(128,"\\S"),"target" to ref("modelSource"),"before" to nullable(ref("modelSource"))),1,256),
            "question" to text(4096,"\\S"),"scope" to text(8192,"\\S"),"rationale" to text(8192,"\\S"),
            "consequences" to list(text(4096),0,64),"alternatives" to list(text(4096),0,64),
            "uncertainties" to list(text(4096),0,64),"contexts" to list(ref("modelSource"),0,256),
            "dependencies" to list(ref("modelSource"),0,256),"informationGaps" to list(text(4096),0,64)),
            mapOf("reviewLanguage" to language,"presentations" to presentations("itemKey",text(128,"\\S")),"followup" to record("requestId" to uuid,"items" to list(record(
                "previousItemId" to nullable(uuid),"itemKey" to nullable(text(128)),
                "disposition" to ProtocolShape.Choice(*listOf("revised","unchanged","added","removed","retained").map(::literal).toTypedArray()),
                "explanation" to text(4096,"\\S")),1,256)))),
    )
    val help:Map<String,String> = mapOf(
        "propose_document" to "Author English titles/text: one independently discussable norm per permanent clause id, not a thematic section. Could the owner dispute one part without the others? If yes, prepare separate anchors, retaining conditions/exceptions. No automatic prose splitting. context is a normative revision dependency, NOT introductory/process text; use review question/scope for framing. Rendering: optional normative context + two LF; each clause id + ' — ' + title + LF + text + two LF. Adapter prepares exact UTF-8 spans/digests. Reuse identical intentId/input after uncertainty. Changing content needs a new intentId; existing documentId preserves identity. Proposal is not approval.",
        "request_review" to "Each item has a stable key and exact source wr1_ + clauseId. Omit clauseId to select normative context. Initial unapproved context must be in contexts AND a decision item, unless already approved. before is an approved head, never an unapproved draft. New friendly reviews require reviewLanguage and a complete matching presentation with sourceLanguage=en; optional per-item explanation gives substantive reasons distinct from normative wording. Alternatives are product/design choices, not answer buttons; consequences concern product/cost/risk, not workflow. Use empty arrays honestly when none identified. Historical exact originals retain their route. Creation/delivery is not approval readiness; inspect reported blockers.",
        "propose_revision" to "Low-level existing wire payload. Prefer propose_document to avoid calculating hashes/spans. Exact immutable revision; no approved flag. Content/structure domain validation remains authoritative.",
        "request_approval" to "Low-level existing review packet. Prefer request_review for exact references and preparation checks. Packet needs nonempty items; substantive alternatives/consequences may be empty. before is the direct approved head; followup is review/comment history. Owner decision is separate.",
        "submit_review" to "Submit a bounded review draft with exact immutable source selectors. Service resolves every source and predecessor on validation state H and publishes the complete packet atomically. Retry identical original command and key; no latest lookup, arbitrary batching or owner approval.",
        "record_assessment" to "Records an attributed consistency assessment of exact references. It is not approval or proof of semantic completeness; service authority and graph checks remain required.",
        "pin_consumer" to "Pins exact approved clause/context/dependency inputs with a declared assessment. Service independently checks current authority, approval, contexts and dependency closure.",
    )
    fun exported():JsonValue = obj(
        "formatVersion" to num(1),
        "commands" to JObject(payloads.mapValues {schema(it.value)}),
        "reads" to JObject(reads.mapValues {schema(it.value)}),
        "authoring" to JObject(authoring.mapValues {schema(it.value)}),
        "help" to JObject(help.mapValues {str(it.value)}),
    )
}

/** Build-only entrypoint: no store, authentication, network or model access. */
internal object GenerateKnowledgeProtocol {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1)
        val output=java.io.File(args[0]);output.parentFile.mkdirs()
        output.writeBytes(StrictJson.encode(KnowledgeProtocol.exported(),1024*1024))
        java.io.File(output.parentFile,"model-work-export-v1.json").writeBytes(StrictJson.encode(WorkProtocol.exported(),1024*1024))
    }
}
