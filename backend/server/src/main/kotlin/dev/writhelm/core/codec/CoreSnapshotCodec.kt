// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

/** Exact owned bytes. Paths are generated/validated core keys, never opened as filesystem paths. */
internal sealed interface CoreEntryKey {
    val path: String
    data object Header: CoreEntryKey { override val path="core/format.json" }
    data class Body(val digest: Digest): CoreEntryKey { override val path=bodyPath(digest) }
    data class Record(val family: String,val digest: Digest): CoreEntryKey {
        init { if(family !in CoreRecordCodec.families)refuse(CodecProblem.UNSUPPORTED_FORMAT) }
        override val path="core/records/$family/${digest.hex.take(2)}/${digest.hex}.json"
    }
    companion object {
        fun parse(path: String): CoreEntryKey {
            if(path==Header.path)return Header
            val body=Regex("core/bodies/([0-9a-f]{2})/([0-9a-f]{64})\\.txt").matchEntire(path)
            if(body!=null) { val key=Body(Digest.parse(body.groupValues[2])); graph(key.path==path); return key }
            val record=Regex("core/records/([a-z]+)/([0-9a-f]{2})/([0-9a-f]{64})\\.json").matchEntire(path)
            if(record!=null) { val key=Record(record.groupValues[1],Digest.parse(record.groupValues[3])); graph(key.path==path); return key }
            refuse(CodecProblem.UNSUPPORTED_FORMAT)
        }
    }
}
internal class CoreEntry(val key: CoreEntryKey, bytes: ByteArray, allocationLimit: Int=64*1024*1024) {
    constructor(path: String,bytes: ByteArray,allocationLimit: Int=64*1024*1024): this(CoreEntryKey.parse(path),bytes,allocationLimit)
    // An explicit owned-entry buffer budget, not a version-1 body-size law. Adapters must bound
    // their own upstream buffers too; the codec never authorizes reading an arbitrary file first.
    init { require(allocationLimit>0); if(bytes.size>allocationLimit)refuse(CodecProblem.RESOURCE_UNAVAILABLE) }
    val path get()=key.path
    private val content=bytes.copyOf()
    fun bytes()=content.copyOf()
    internal val size get()=content.size
    internal fun json(limits: JsonLimits, consume: (Long,Long)->Unit)=StrictJson.decode(content,limits,true,consume)
    internal fun admission(limits: JsonLimits)=StrictJson.decode(content,limits)
    internal fun body()=try { Utf8Content.fromBytes(content) } catch(_: IllegalArgumentException) { refuse(CodecProblem.MALFORMED) }
}
internal data class RestoreBudget(val bytes: Long=64L*1024*1024,val records: Int=8192,val bodies: Int=8192,
    val edges: Int=65536,val depth: Int=256,val decodedText: Long=32L*1024*1024,val nodes: Long=250000,
    val json: JsonLimits=JsonLimits(bytes=16*1024*1024,stringBytes=4*1024*1024,depth=128,collection=16384,tokens=500000)) {
    init { require(bytes>0 && records>0 && bodies>0 && edges>0 && depth>0 && decodedText>0 && nodes>0) }
}
/** Only a normal end of enumeration is success. A thrown source/sink fault is sanitized unavailable. */
internal class CoreSnapshotCodec(private val admission: JsonLimits=JsonLimits(),private val budget: RestoreBudget=RestoreBudget(),private val bodyBytes: Int=1024*1024) {
    init { require(bodyBytes>0) }
    fun export(workflow: ApprovalWorkflow,sink: (CoreEntry)->Unit) = boundary {
        val s=workflow.snapshot(budget.records); val meter=SnapshotMeter(budget)
        val bodies=LinkedHashMap<Digest,Utf8Content>()
        s.revisions.forEach {
            val existing=bodies[it.content.digest]
            if(existing!=null) { if(existing!=it.content)refuse(CodecProblem.DUPLICATE_IDENTITY) }
            else { if(bodies.size>=budget.bodies)refuse(CodecProblem.RESOURCE_UNAVAILABLE); bodies[it.content.digest]=it.content }
        }
        val records=ArrayList<Pair<String,RecordDescriptor>>()
        CoreRecordCodec.records(s).forEach {
            if(records.size>=budget.records)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            records.add(it.path(budget.json.bytes) to it)
        }
        val sortedRecords=records.sortedBy { it.first }
        if(sortedRecords.zipWithNext().any { (a,b)->a.first==b.first })refuse(CodecProblem.DUPLICATE_IDENTITY)
        fun emit(path: String,bytes: ByteArray) {
            meter.bytes(bytes.size)
            sink(CoreEntry(path,bytes,maxOf(1,bytes.size)))
        }
        fun json(path: String,value: ()->JsonValue) {
            val bytes=JsonConstruction.bounded(budget.json,meter::consume) {
                StrictJson.encode(value(),meter.fileLimit(),true,budget.json.depth)
            }
            emit(path,bytes)
        }
        json("core/format.json",CoreRecordCodec::header)
        bodies.values.sortedBy { it.digest.hex }.forEach { meter.consume(0,it.sizeBytes.toLong()); if(it.sizeBytes.toLong()>meter.remaining())refuse(CodecProblem.RESOURCE_UNAVAILABLE); emit(bodyPath(it.digest),it.bytes()) }
        sortedRecords.forEach { (path,r) -> json(path) { JsonConstruction.reuse(r.key); CoreRecordCodec.envelope(r.family,r.key,r.payload()) } }
    }
    fun restore(entries: Sequence<CoreEntry>): ApprovalWorkflow = boundary {
        val meter=SnapshotMeter(budget); val bodies=LinkedHashMap<Digest,Utf8Content>(); val builder=SnapshotBuilder()
        val seen=HashSet<CoreEntryKey>()
        var header=false; var previous: String?=null; var records=0; var bodyCount=0
        for(entry in entries) {
            meter.bytes(entry.size)
            if(!seen.add(entry.key))refuse(CodecProblem.DUPLICATE_IDENTITY)
            if(!header) { graph(entry.path=="core/format.json"); CoreRecordCodec.checkHeader(entry.json(budget.json,meter::consume)); header=true; continue }
            previous?.let { graph(unsignedCompare(encodeUtf8(it),encodeUtf8(entry.path))<0) }; previous=entry.path
            when {
                entry.path.startsWith("core/bodies/") -> {
                    if(++bodyCount>budget.bodies)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                    meter.consume(0,entry.size.toLong())
                    val body=entry.body(); if(entry.path!=bodyPath(body.digest))refuse(CodecProblem.DIGEST_MISMATCH)
                    uniquePut(bodies,body.digest,body)
                }
                entry.path.startsWith("core/records/") -> {
                    if(++records>budget.records)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                    builder.add(CoreRecordCodec.decode(entry.json(budget.json,meter::consume),entry.path,budget.json.bytes),bodies)
                }
                else -> refuse(CodecProblem.UNSUPPORTED_FORMAT)
            }
        }
        graph(header)
        val s=builder.snapshot(); graph(bodies.keys==s.revisions.map { it.content.digest }.toSet())
        WorkflowSnapshotValidator.restore(s,budget.edges,budget.depth)
    }
    /** New metadata admission does not execute a transition or tighten historical reads. */
    fun admitRecord(entry: CoreEntry, body: Utf8Content? = null) {
        val json=entry.admission(admission)
        val r=CoreRecordCodec.decode(json,entry.path,admission.bytes)
        if(body != null && body.sizeBytes > bodyBytes)refuse(CodecProblem.ADMISSION_LIMIT)
        val builder=SnapshotBuilder()
        builder.add(r,body?.let { mapOf(it.digest to it) } ?: emptyMap())
        builder.revisions.values.forEach { if(it.structure.clauses.size.toLong()+it.structure.context.size>admission.collection)refuse(CodecProblem.ADMISSION_LIMIT) }
        // Local shape/digest admission only. Cross-record references need the validated candidate;
        // this deliberately returns no usable workflow or authority.
    }
    private fun <T> boundary(block: ()->T): T = try { block() } catch(e: CodecFault) { throw e }
        catch(_: Exception) { refuse(CodecProblem.RESOURCE_UNAVAILABLE) }
}
internal fun bodyPath(d: Digest)="core/bodies/${d.hex.take(2)}/${d.hex}.txt"
private class SnapshotMeter(private val budget: RestoreBudget) {
    private var bytes=0L; private var nodes=0L; private var text=0L
    fun remaining()=budget.bytes-bytes
    fun fileLimit(): Int { if(remaining()<=0)refuse(CodecProblem.RESOURCE_UNAVAILABLE); return minOf(remaining(),budget.json.bytes.toLong()).toInt() }
    fun bytes(n: Int) { if(n.toLong()>remaining())refuse(CodecProblem.RESOURCE_UNAVAILABLE); bytes+=n }
    fun consume(n: Long,t: Long) { if(n>budget.nodes-nodes || t>budget.decodedText-text)refuse(CodecProblem.RESOURCE_UNAVAILABLE); nodes+=n; text+=t }
}

private class SnapshotBuilder {
    val revisions=LinkedHashMap<RevisionKey,DocumentRevision>(); val grants=LinkedHashMap<Pair<UUID,UUID>,Grant>()
    val proposals=LinkedHashMap<RevisionKey,Actor>(); val requests=LinkedHashMap<UUID,ApprovalRequest>(); val resolutions=LinkedHashMap<UUID,ApprovalResolution>()
    val approvals=LinkedHashMap<KnowledgeReference,ApprovalTrace>(); val continuity=LinkedHashMap<KnowledgeReference,StoredContinuity>()
    val assessments=LinkedHashMap<UUID,RecordedAssessment>(); val manifests=LinkedHashMap<UUID,InputManifest>()
    val heads=LinkedHashMap<SubjectKey,KnowledgeReference>(); val generations=LinkedHashMap<SubjectKey,Long>(); val blocked=LinkedHashSet<KnowledgeReference>()
    fun add(r: RecordDto,bodies: Map<Digest,Utf8Content>) {
        val k=r.key; val p=r.payload
        when(r.family) {
            "revisions" -> {
                val f=p.fields("bodyDigest","bodyBytes","structure","structureDigest","revisionDigest","contextDigest")
                val b=bodies[digest(f.getValue("bodyDigest"))] ?: refuse(CodecProblem.INVALID_GRAPH); val i=structure(f.getValue("structure"))
                val v=checked { DocumentRevision(revisionKey(k),b,i) }
                if(f.getValue("bodyBytes").int()!=b.sizeBytes || digest(f.getValue("structureDigest"))!=i.digest || digest(f.getValue("revisionDigest"))!=v.digest || digest(f.getValue("contextDigest"))!=v.contextDigest)refuse(CodecProblem.DIGEST_MISMATCH)
                uniquePut(revisions,v.key,v)
            }
            "proposals" -> { val f=p.fields("principalId"); uniquePut(proposals,revisionKey(k),Actor(uuid(f.getValue("principalId")))) }
            "grants" -> {
                val key=k.fields("principalId","projectId"); val f=p.fields("capabilities")
                val caps=f.getValue("capabilities").list().map { v -> Capability.entries.firstOrNull { it.name==v.string() } ?: refuse(CodecProblem.INVALID_SHAPE) }
                if(caps.distinct().size!=caps.size)refuse(CodecProblem.DUPLICATE_IDENTITY)
                val g=Grant(uuid(key.getValue("principalId")),uuid(key.getValue("projectId")),frozenSet(caps)); uniquePut(grants,g.principalId to g.projectId,g)
            }
            "requests" -> {
                val f=p.fields("projectId","proposedBy","packet","generations","packetDigest","requestDigest"); val guards=LinkedHashMap<SubjectKey,Long>()
                f.getValue("generations").list().forEach { val g=it.fields("subject","generation"); uniquePut(guards,subject(g.getValue("subject")),g.getValue("generation").long()) }
                val packet=packet(f.getValue("packet")); graph(guards.keys==packet.items.map { it.target.subject() }.toSet())
                val request=ApprovalRequest(uuid(k),uuid(f.getValue("projectId")),uuid(f.getValue("proposedBy")),packet,guards)
                if(digest(f.getValue("packetDigest"))!=packet.digest || digest(f.getValue("requestDigest"))!=request.digest)refuse(CodecProblem.DIGEST_MISMATCH)
                uniquePut(requests,request.id,request)
            }
            "resolutions" -> { val f=p.fields("actorId","answer","approved"); uniquePut(resolutions,uuid(k),ApprovalResolution(uuid(k),Actor(uuid(f.getValue("actorId"))),answer(f.getValue("answer")),f.getValue("approved").list().map(::reference))) }
            "approvals" -> uniquePut(approvals,reference(k),trace(p))
            "continuity" -> { val f=p.fields("source","assessmentId"); uniquePut(continuity,reference(k),StoredContinuity(reference(f.getValue("source")),uuid(f.getValue("assessmentId")))) }
            "assessments" -> {
                val f=p.fields("projectId","actorId","references","rationale","links","blocked")
                val a=checked { ConsistencyAssessment(uuid(k),uuid(f.getValue("projectId")),f.getValue("references").list().map(::reference),f.getValue("rationale").string(),f.getValue("links").list().map(::link),f.getValue("blocked").list().map(::reference)) }
                uniquePut(assessments,a.id,RecordedAssessment(a,Actor(uuid(f.getValue("actorId")))))
            }
            "manifests" -> {
                val f=p.fields("consumerId","projectId","createdBy","clauses","contexts","dependencies","assessmentId","provenance"); val provenance=LinkedHashMap<KnowledgeReference,ApprovalTrace>()
                f.getValue("provenance").list().forEach { val v=it.fields("reference","trace"); uniquePut(provenance,reference(v.getValue("reference")),trace(v.getValue("trace"))) }
                val m=InputManifest(uuid(k),uuid(f.getValue("consumerId")),uuid(f.getValue("projectId")),uuid(f.getValue("createdBy")),f.getValue("clauses").list().map(::clause),f.getValue("contexts").list().map(::context),f.getValue("dependencies").list().map(::reference),uuid(f.getValue("assessmentId")),provenance)
                uniquePut(manifests,m.id,m)
            }
            "heads" -> { val f=p.fields("reference"); uniquePut(heads,subject(k),reference(f.getValue("reference"))) }
            "generations" -> { val f=p.fields("generation"); uniquePut(generations,subject(k),f.getValue("generation").long()) }
            "blocked" -> { p.fields(); if(!blocked.add(reference(k)))refuse(CodecProblem.DUPLICATE_IDENTITY) }
            else -> refuse(CodecProblem.UNSUPPORTED_FORMAT)
        }
    }
    fun snapshot()=WorkflowSnapshot(revisions.values,grants.values,proposals,requests,resolutions,approvals,continuity,assessments,manifests,heads,generations,blocked)
}
