// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal val identityCodec = WorkIdentityCodec()
internal fun workText(v: JsonValue, min: Int, max: Int): String = v.string().also {
    val n=it.codePointCount(0,it.length); shape(n in min..max)
    if(min>0) shape(it.isNotBlank())
}
internal data class WorkGitOid(val algorithm: String,val hex: String)
internal fun workOid(v: JsonValue): WorkGitOid {
    val f=v.fields("algorithm","hex");val algorithm=f.getValue("algorithm").string();val hex=f.getValue("hex").string()
    shape(algorithm in setOf("sha1","sha256"));shape(hex.matches(Regex(if(algorithm=="sha1")"[0-9a-f]{40}" else "[0-9a-f]{64}")))
    return WorkGitOid(algorithm,hex)
}
internal fun workOid(v: WorkGitOid): JsonValue = obj("algorithm" to str(v.algorithm),"hex" to str(v.hex))
internal fun workBoolean(v: JsonValue): Boolean = (v as? JBoolean)?.value ?: refuse(CodecProblem.INVALID_SHAPE)
internal fun <T> workList(v: JsonValue, min: Int, max: Int, unique: Boolean, decode: (JsonValue)->T): List<T> {
    val a=v.list(); shape(a.size in min..max)
    if(unique) shape(a.map { StrictJson.encode(it).toList() }.toSet().size==a.size)
    val ordered=if(unique)workSet(JArray(a)).values else a
    return java.util.Collections.unmodifiableList(ordered.map(decode))
}
internal fun <T> workArray(v: List<T>,min: Int,max: Int,unique: Boolean,encode: (T)->JsonValue): JsonValue {
    shape(v.size in min..max);JsonConstruction.collection(v.size)
    val a=JArray(v.map(encode));return if(unique)workSet(a)else a
}
private val byteOrder=Comparator<ByteArray> { a,b ->
    var c=0; var i=0
    while(c==0 && i<minOf(a.size,b.size)) { c=(a[i].toInt() and 255).compareTo(b[i].toInt() and 255); i++ }
    if(c==0)a.size.compareTo(b.size) else c
}
internal fun workSet(v: JArray): JArray {
    val pairs=v.values.map { StrictJson.encode(it) to it }.sortedWith { a,b -> byteOrder.compare(a.first,b.first) }
    shape(pairs.zipWithNext().none { (a,b)->a.first.contentEquals(b.first) })
    return JArray(pairs.map { it.second })
}
internal fun <T> workOneOf(branches: List<()->T>): T {
    val found=ArrayList<T>(1)
    branches.forEach { try { found.add(it()) } catch(e: CodecFault) {
        if(e.problem !in setOf(CodecProblem.INVALID_SHAPE,CodecProblem.MALFORMED))throw e
    } }
    shape(found.size==1); return found.single()
}

internal data class WorkRecord(val identity: WorkRecordIdentity, val payload: WorkPayload) {
    init { require(identity.family==payloadFamily(payload)) }
}
internal data class WorkCommand(val requestKey: UUID, val projectId: UUID, val operation: WorkOperation)
internal sealed interface WorkRead {
    val projectId: UUID
    data class Exact(override val projectId: UUID, val identity: WorkRecordIdentity,
        val digest: Digest?, val generation: Long?): WorkRead
    data class Body(override val projectId: UUID, val digest: Digest, val record: WorkRead): WorkRead
    data class Listing(override val projectId: UUID, val value: RListWorkRecords): WorkRead
    data class ConditionStatus(override val projectId: UUID,val condition: Exact): WorkRead
}

/** New admission and historical readers are separate finite profiles. No schema fetch at runtime. */
internal class WorkCodec(
    private val commandLimits: JsonLimits=JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,depth=32,collection=1024,tokens=100000),
    private val metadataLimits: JsonLimits=JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000),
    private val historicalLimits: JsonLimits=JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000),
    private val ordinaryStringBytes: Int=minOf(commandLimits.stringBytes,65536),
    private val bodyBytes: Int=1024*1024,
) {
    init { require(ordinaryStringBytes>0 && bodyBytes in 1..1024*1024) }
    // Parser/construction ceilings must accommodate either class. The field-sensitive pass below
    // applies the ordinary cap separately; a custom ordinary cap cannot preempt a legal body.
    private val commandParsingLimits=commandLimits.copy(stringBytes=maxOf(ordinaryStringBytes,bodyBytes))
    fun decodeCommand(bytes: ByteArray): WorkCommand {
        val parsed=StrictJson.decode(bytes,commandParsingLimits)
        fun strings(v: JsonValue,attachment: Boolean=false) {
            when(v) {
                is JObject -> v.entries.forEach { (k,x)->strings(x,attachment && k=="body") }
                is JArray -> v.values.forEach { strings(it,attachment) }
                is JString -> if(utf8Size(v.value)>if(attachment)bodyBytes else ordinaryStringBytes)refuse(CodecProblem.ADMISSION_LIMIT)
                else -> Unit
            }
        }
        val f=parsed.fields("formatVersion","requestKey","projectId","operation","payload")
        val payload=f.getValue("payload") as? JObject ?: refuse(CodecProblem.INVALID_SHAPE)
        payload.entries.forEach { (name,v)->strings(v,name=="bodies") }
        uniqueLogicalIds(payload)
        shape(f.getValue("formatVersion").long()==3L)
        val value=WorkCommand(uuid(f.getValue("requestKey")),uuid(f.getValue("projectId")),
            decodeWorkOperation(f.getValue("operation").string(),f.getValue("payload")))
        sameProject(encodeWorkOperation(value.operation),value.projectId)
        validateOperationShape(value.operation)
        return value
    }
    fun encodeCommand(value: WorkCommand): ByteArray {
        val bytes=JsonConstruction.bounded(commandParsingLimits,{ _,_-> },false) {
            StrictJson.encode(commandValue(value,true),commandLimits.bytes,maxDepth=commandLimits.depth)
        }
        decodeCommand(bytes); return bytes
    }
    fun fingerprint(value: WorkCommand): Digest {
        encodeCommand(value)
        return framedDigest("writhelm.command.v3") { writeFramed(StrictJson.encode(commandValue(value,false),commandLimits.bytes)) }
    }
    private fun commandValue(v: WorkCommand, key: Boolean): JsonValue {
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to JNumber(3),"projectId" to uuid(v.projectId),
            "operation" to str(v.operation.name),"payload" to encodeWorkOperation(v.operation))
        if(key)fields["requestKey"]=uuid(v.requestKey)
        return JObject(fields)
    }
    fun decodeRecord(bytes: ByteArray, historical: Boolean=false): WorkRecord {
        val f=StrictJson.decode(bytes,if(historical)historicalLimits else metadataLimits,unavailable=historical)
            .fields("formatVersion","family","key","payload")
        version(f.getValue("formatVersion"))
        val family=WorkFamily.entries.firstOrNull { it.tag==f.getValue("family").string() } ?: refuse(CodecProblem.INVALID_SHAPE)
        val id=WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(f.getValue("key"))))
        val p=decodeWorkPayload(family,f.getValue("payload"))
        uniqueLogicalIds(f.getValue("payload"))
        sameProject(encodeWorkPayload(p),id.key.projectId)
        validateWorkShape(p)
        validateModel2Shape(p)
        if(!historical && family==WorkFamily.CONDITIONS && bytes.size>16384)refuse(CodecProblem.ADMISSION_LIMIT)
        return WorkRecord(id,p)
    }
    fun encodeRecord(v: WorkRecord, historical: Boolean=false): ByteArray {
        val lim=if(historical)historicalLimits else metadataLimits
        val bytes=JsonConstruction.bounded(lim,{ _,_-> },historical) { StrictJson.encode(obj("formatVersion" to JNumber(1),"family" to str(v.identity.family.tag),
            "key" to StrictJson.decode(identityCodec.encodeKey(v.identity.key)),"payload" to encodeWorkPayload(v.payload)),
            lim.bytes,unavailable=historical,maxDepth=lim.depth) }
        decodeRecord(bytes,historical);return bytes
    }
    fun digest(v: WorkRecord): Digest=Digest.of(encodeRecord(v,true))
    fun decodeRead(bytes: ByteArray): WorkRead {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=16384,depth=16,collection=64,tokens=2048))
            .fields("formatVersion","projectId","operation","subject")
        shape(f.getValue("formatVersion").long()==2L)
        val project=uuid(f.getValue("projectId")); val name=f.getValue("operation").string(); val s=f.getValue("subject")
        if(name=="list_work_records")return WorkRead.Listing(project,decodeRListWorkRecords(s))
        if(name=="read_condition_status")return WorkRead.ConditionStatus(project,exact(project,WorkFamily.CONDITIONS,s,false))
        if(name=="read_work_body") {
            val b=s.fields("digest","record"); val r=b.getValue("record") as? JObject ?: refuse(CodecProblem.INVALID_SHAPE)
            val family=WorkFamily.entries.firstOrNull { it.tag==r.entries["family"]?.string() } ?: refuse(CodecProblem.INVALID_SHAPE)
            val selector=exact(project,family,r,withFamily=true)
            return WorkRead.Body(project,dev.writhelm.core.codec.digest(b.getValue("digest")),selector)
        }
        val family=WorkFamily.entries.firstOrNull { "read_"+it.tag.replace('-','_')==name } ?: refuse(CodecProblem.INVALID_SHAPE)
        return exact(project,family,s,withFamily=false)
    }
    fun encodeRead(v: WorkRead): ByteArray {
        fun selector(s: WorkRead.Exact,family: Boolean): JsonValue {
            val f=linkedMapOf("key" to StrictJson.decode(identityCodec.encodeKey(s.identity.key)))
            if(s.identity.family.mutable) {
                shape(s.digest==null && s.generation!=null);f["generation"]=JNumber(s.generation!!)
            } else {
                shape(s.generation==null && s.digest!=null);f["digest"]=dev.writhelm.core.codec.digest(s.digest!!)
            }
            if(family)f["family"]=str(s.identity.family.tag)
            return JObject(f)
        }
        val operation: String;val subject: JsonValue
        when(v) {
            is WorkRead.Exact -> { operation="read_"+v.identity.family.tag.replace('-','_');subject=selector(v,false) }
            is WorkRead.Body -> {
                operation="read_work_body"
                val r=v.record as? WorkRead.Exact ?: refuse(CodecProblem.INVALID_SHAPE)
                shape(r.projectId==v.projectId)
                subject=obj("digest" to dev.writhelm.core.codec.digest(v.digest),"record" to selector(r,true))
            }
            is WorkRead.Listing -> { operation="list_work_records";subject=encodeRListWorkRecords(v.value) }
            is WorkRead.ConditionStatus -> { operation="read_condition_status";subject=selector(v.condition,false) }
        }
        val bytes=StrictJson.encode(obj("formatVersion" to JNumber(2),"projectId" to uuid(v.projectId),"operation" to str(operation),"subject" to subject),16384)
        decodeRead(bytes);return bytes
    }
    private fun exact(project: UUID,family: WorkFamily,s: JsonValue,withFamily: Boolean): WorkRead.Exact {
        val names=mutableListOf("key",if(family.mutable)"generation" else "digest");if(withFamily)names.add("family")
        val f=s.fields(*names.toTypedArray())
        val id=WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(f.getValue("key"))))
        shape(id.key.projectId==project)
        return WorkRead.Exact(project,id,if(family.mutable)null else dev.writhelm.core.codec.digest(f.getValue("digest")),
            if(family.mutable)f.getValue("generation").long() else null)
    }
}

internal fun sameProject(v: JsonValue, project: UUID) {
    when(v) {
        is JObject -> { v.entries["projectId"]?.let { shape(uuid(it)==project) }; v.entries.values.forEach { sameProject(it,project) } }
        is JArray -> v.values.forEach { sameProject(it,project) }
        else -> Unit
    }
}
private fun uniqueLogicalIds(v: JsonValue) {
    when(v) {
        is JObject -> v.entries.values.forEach(::uniqueLogicalIds)
        is JArray -> {
            for(field in listOf("id","messageId","assignmentId")) {
                val ids=v.values.mapNotNull { (it as? JObject)?.entries?.get(field) }.map { StrictJson.encode(it).toList() }
                shape(ids.size==ids.distinct().size)
            }
            v.values.forEach(::uniqueLogicalIds)
        }
        else -> Unit
    }
}
internal fun validateWorkShape(p: WorkPayload) {
    fun unique(ids: List<UUID>) { shape(ids.size==ids.toSet().size) }
    fun family(r: WorkReference?,f: WorkFamily) { if(r!=null)shape(r.identity.family==f) }
    when(p) {
        is WTaskRevisionsPayload -> p.value.let { v ->
            unique(v.criteria.map { it.id })
            v.dependencies.forEach { family(it.selectedResult,WorkFamily.RESULTS) }
            if(v.state=="executable")shape(v.base!=null && v.executionProfile!=null && v.verificationPlan!=null && v.unresolved.isEmpty())
        }
        is WPlansPayload -> { unique(p.value.steps.map { it.id });family(p.value.previousCheckpoint,WorkFamily.CHECKPOINTS) }
        is WVerificationPlansPayload -> { unique(p.value.criteria.map { it.id });unique(p.value.gates.map { it.id }) }
        is WAssignmentsPayload -> p.value.let { v ->
            family(v.manifest,WorkFamily.INPUT_MANIFESTS);family(v.binding,WorkFamily.ASSIGNMENT_BINDINGS)
            family(v.checkpoint,WorkFamily.CHECKPOINTS);family(v.recovery,WorkFamily.RECOVERIES)
            shape((v.intendedExecution.template=="executor-v1")==v.reviewSubjects.isEmpty())
            unique(v.reviewSubjects.map { (it.identity.key as WorkKey.Id).id })
            if(v.state in setOf("bound","active","pausing","paused","stopping","stopped","completed"))
                shape(v.execution!=null && v.binding!=null && v.delegationId!=null)
            if(v.state=="reserved")shape(v.execution==null && v.binding==null)
        }
        is WAssignmentBindingsPayload -> p.value.let { v ->
            family(v.manifest,WorkFamily.INPUT_MANIFESTS)
            shape((v.intendedExecution.template=="executor-v1")==v.reviewSubjects.isEmpty())
            unique(v.reviewSubjects.map { (it.identity.key as WorkKey.Id).id })
        }
        is WClaimsPayload -> p.value.let { v -> if(v.state=="free")shape(v.holder==null && v.workspaceId==null) else shape(v.holder!=null) }
        is WRetrospectivesPayload -> p.value.let { v -> shape((v.outcome=="no-significant-new-findings")==v.observations.isEmpty())
            v.observations.forEach { if(it.status=="accepted-reference")shape(it.normativeDecision!=null) } }
        is WMemoryPayload -> { unique(p.value.entries.map { it.id }); p.value.entries.forEach { if(it.status=="accepted-reference")
            shape(it.sources.any { s->s is WSourceVariant0Value }) } }
        is WLoadedViewsPayload -> family(p.value.manifest,WorkFamily.INPUT_MANIFESTS)
        is WCheckpointsPayload -> { family(p.value.manifest,WorkFamily.INPUT_MANIFESTS)
            p.value.result?.let { shape(it.identity.family in setOf(WorkFamily.RESULTS,WorkFamily.VERIFICATION_REPORTS)) }
            family(p.value.retrospective,WorkFamily.RETROSPECTIVES) }
        is WResultsPayload -> { family(p.value.manifest,WorkFamily.INPUT_MANIFESTS);family(p.value.retrospective,WorkFamily.RETROSPECTIVES);p.value.evidence.forEach { family(it,WorkFamily.EVIDENCE) } }
        is WVerificationReportsPayload -> { unique(p.value.findings.map { it.id });p.value.subjects.forEach { family(it,WorkFamily.RESULTS) };p.value.evidence.forEach { family(it,WorkFamily.EVIDENCE) } }
        is WDispositionsPayload -> { family(p.value.result,WorkFamily.RESULTS);family(p.value.report,WorkFamily.VERIFICATION_REPORTS);family(p.value.replacementResult,WorkFamily.RESULTS) }
        is WAcceptancesPayload -> { family(p.value.result,WorkFamily.RESULTS);family(p.value.applicability,WorkFamily.EVIDENCE_APPLICABILITY)
            p.value.evidence.forEach { family(it,WorkFamily.EVIDENCE) };p.value.reports.forEach { family(it,WorkFamily.VERIFICATION_REPORTS) };p.value.dispositions.forEach { family(it,WorkFamily.DISPOSITIONS) } }
        is WEvidenceApplicabilityPayload -> { family(p.value.result,WorkFamily.RESULTS);p.value.evidence.forEach { family(it.evidence,WorkFamily.EVIDENCE) } }
        is WIntegrationIntentsPayload -> { family(p.value.result,WorkFamily.RESULTS);family(p.value.acceptance,WorkFamily.ACCEPTANCES);family(p.value.observation,WorkFamily.INTEGRATIONS) }
        is WIntegrationsPayload -> { family(p.value.result,WorkFamily.RESULTS);family(p.value.applicability,WorkFamily.EVIDENCE_APPLICABILITY);p.value.evidence.forEach { family(it,WorkFamily.EVIDENCE) } }
        is WSourceCutsPayload -> shape(p.value.nativeMessages.map { it.messageId }.distinct().size==p.value.nativeMessages.size)
        is WControllerContinuationsPayload -> p.value.let { v ->
            shape(v.frontier.map { it.task }.distinct().size==v.frontier.size)
            unique(v.frontier.flatMap { it.assignments }.map { it.assignmentId })
            shape(v.source.nativeMessages.map { it.messageId }.distinct().size==v.source.nativeMessages.size)
        }
        else -> Unit
    }
}

/** Conditional structural laws not expressible as individual generated field constructors. */
internal fun validateOperationShape(op: WorkOperation) {
    validateModel2OperationShape(op)
    when(op) {
        is CCreateTaskRevisionOperation -> validateWorkShape(WTaskRevisionsPayload(op.value.data))
        is CReserveAssignmentOperation -> {
            val v=op.value;shape((v.intendedExecution.template=="executor-v1")==v.reviewSubjects.isEmpty())
            shape(v.reviewSubjects.map { it.identity }.distinct().size==v.reviewSubjects.size)
        }
        is CObserveIntegrationOperation -> op.value.let { v ->
            if(v.outcome=="confirmed")shape(v.resultingSnapshot!=null && v.applicability!=null)
            else shape(v.resultingSnapshot==null && v.applicability==null)
        }
        is CRecordRetrospectiveOperation -> shape((op.value.data.outcome=="no-significant-new-findings")==op.value.data.observations.isEmpty())
        is CSubmitVerificationReportOperation -> shape(op.value.data.subjects.map { it.identity }.distinct().size==op.value.data.subjects.size)
        is CRecordControllerContinuationOperation -> validateWorkShape(WControllerContinuationsPayload(op.value.data))
        else -> Unit
    }
}
