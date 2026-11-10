// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import java.util.UUID

internal sealed interface ExecutionResultDescriptor { val key:WorkKey.Id }
internal data class NativeResultDescriptor(override val key:WorkKey.Id,val value:WResults):ExecutionResultDescriptor
internal data class MechanicalResultDescriptor(override val key:WorkKey.Id,val value:MechanicalResult):ExecutionResultDescriptor
/** Candidate model3 result; old command/review/acceptance paths remain closed until integrated. */
internal data class WMechanicalResultsPayload(val value:MechanicalResult):WorkPayload
internal data class MechanicalResult(
    val task:WorkKey.TaskRevision,val assignment:WAssignmentSubject,val producer:MechanicalProducerDescriptor,
    val manifest:WorkReference,val artifacts:List<WSnapshot>,val changes:WorkBodyReference,val claimedCriteria:List<UUID>,
    val evidence:List<WorkReference>,val retrospective:WorkReference,val pending:List<WPending>,
    val patch:WorkBodyReference,val authorship:DeclaredResultAuthorship
)

/** Versioned result view only, not a new admitted command or writer-registry selector. */
internal class ResultDescriptorCodec {
    private val legacy=WorkCodec()
    private val execution=ExecutionDescriptorCodec()
    fun decode(bytes:ByteArray,historical:Boolean=false):ExecutionResultDescriptor {
        val limits=if(historical)JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)
            else JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000)
        val f=StrictJson.decode(bytes,limits,unavailable=historical).fields("formatVersion","family","key","payload")
        if(f.getValue("formatVersion").long()==1L) {
            val record=legacy.decodeRecord(bytes,historical);shape(record.identity.family==WorkFamily.RESULTS)
            return NativeResultDescriptor(record.identity.key as WorkKey.Id,(record.payload as WResultsPayload).value)
        }
        shape(f.getValue("formatVersion").long()==2L && f.getValue("family").string()=="results")
        val key=identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(f.getValue("key"))) as WorkKey.Id
        val p=f.getValue("payload").fields("kind","task","assignment","producer","manifest","artifacts","changes",
            "claimedCriteria","evidence","retrospective","pending","patch","authorship")
        shape(p.getValue("kind").string()=="mechanical-v1")
        val producer=execution.decodeProducer(StrictJson.encode(p.getValue("producer")),key.projectId) as? MechanicalProducerDescriptor
            ?: refuse(CodecProblem.INVALID_SHAPE)
        val attribution=execution.decodeAuthorship(StrictJson.encode(p.getValue("authorship")),key.projectId)
        shape(attribution.sourceResults.isNotEmpty())
        fun reference(name:String,family:WorkFamily)=identityCodec.decodeReference(StrictJson.encode(p.getValue(name))).also { shape(it.identity.family==family) }
        val result=MechanicalResult(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(p.getValue("task"))) as WorkKey.TaskRevision,
            decodeWAssignmentSubject(p.getValue("assignment")),producer,reference("manifest",WorkFamily.INPUT_MANIFESTS),
            workList(p.getValue("artifacts"),1,128,false,::decodeWSnapshot),identityCodec.decodeBodyReference(StrictJson.encode(p.getValue("changes"))),
            workList(p.getValue("claimedCriteria"),0,256,true) { uuid(it) },
            workList(p.getValue("evidence"),0,256,false) { identityCodec.decodeReference(StrictJson.encode(it)).also { ref ->shape(ref.identity.family==WorkFamily.EVIDENCE) } },
            reference("retrospective",WorkFamily.RETROSPECTIVES),workList(p.getValue("pending"),0,256,false,::decodeWPending),
            identityCodec.decodeBodyReference(StrictJson.encode(p.getValue("patch"))),attribution)
        shape(result.pending.map { it.id }.distinct().size==result.pending.size)
        shape(result.patch.byteLength>0)
        sameProject(encodePayload(result),key.projectId)
        return MechanicalResultDescriptor(key,result)
    }
    fun encode(value:ExecutionResultDescriptor,historical:Boolean=false):ByteArray {
        if(value is NativeResultDescriptor)return legacy.encodeRecord(WorkRecord(WorkRecordIdentity(WorkFamily.RESULTS,value.key),WResultsPayload(value.value)),historical)
        value as MechanicalResultDescriptor
        val bytes=StrictJson.encode(obj("formatVersion" to num(2),"family" to str("results"),
            "key" to StrictJson.decode(identityCodec.encodeKey(value.key)),"payload" to encodePayload(value.value)),if(historical)4*1024*1024 else 262144)
        decode(bytes,historical);return bytes
    }
    internal fun encodePayload(p:MechanicalResult):JsonValue=obj("kind" to str("mechanical-v1"),
        "task" to StrictJson.decode(identityCodec.encodeKey(p.task)),"assignment" to encodeWAssignmentSubject(p.assignment),
        "producer" to StrictJson.decode(execution.encodeProducer(p.producer,p.task.projectId)),
        "manifest" to StrictJson.decode(identityCodec.encodeReference(p.manifest)),"artifacts" to workArray(p.artifacts,1,128,false,::encodeWSnapshot),
        "changes" to StrictJson.decode(identityCodec.encodeBodyReference(p.changes)),"claimedCriteria" to workArray(p.claimedCriteria,0,256,true) { uuid(it) },
        "evidence" to workArray(p.evidence,0,256,false) { StrictJson.decode(identityCodec.encodeReference(it)) },
        "retrospective" to StrictJson.decode(identityCodec.encodeReference(p.retrospective)),"pending" to workArray(p.pending,0,256,false,::encodeWPending),
        "patch" to StrictJson.decode(identityCodec.encodeBodyReference(p.patch)),"authorship" to StrictJson.decode(execution.encodeAuthorship(p.authorship,p.task.projectId)))
    fun reference(value:ExecutionResultDescriptor):WorkReference=WorkReference(WorkRecordIdentity(WorkFamily.RESULTS,value.key),Digest.of(encode(value,true)))
}

internal fun knownResultAuthors(value:ExecutionResultDescriptor):Set<UUID> = when(value) {
    is NativeResultDescriptor -> setOf(value.value.producer.principalId) // known minimum, not inferred complete history
    is MechanicalResultDescriptor -> value.value.authorship.authors.toSet()
}

/** The reducer must supply actual task-selected, resolved immutable predecessors, not data from
 * the applier. This verifies reference closure and known contributors, not semantic completeness. */
internal fun requireMechanicalResultAuthorship(result:MechanicalResultDescriptor,authenticatedSubmitter:UUID,
    predecessors:List<ExecutionResultDescriptor>) {
    val codec=ResultDescriptorCodec()
    val expected=predecessors.map(codec::reference)
    workRequire(expected.isNotEmpty() && expected.distinct().size==expected.size &&
        result.value.authorship.sourceResults.size==expected.size && result.value.authorship.sourceResults.toSet()==expected.toSet() &&
        predecessors.all { it.key.projectId==result.key.projectId },WorkProblem.INVALID_SUBJECT)
    requireDeclaredAuthorship(result.value.authorship,authenticatedSubmitter,predecessors.flatMap { knownResultAuthors(it) }.toSet())
}

internal fun nonAuthorResultCandidate(principal:UUID,execution:UUID,result:ExecutionResultDescriptor,
    knownAuthors:Set<UUID>,requireComplete:Boolean):Boolean = when(result) {
    is NativeResultDescriptor -> nonAuthorCandidate(principal,execution,NativeProducerDescriptor(result.value.producer),
        LegacyProducerAuthorship(result.value.producer.principalId),requireComplete,knownAuthors)
    is MechanicalResultDescriptor -> nonAuthorCandidate(principal,execution,result.value.producer,result.value.authorship,true,knownAuthors)
}
