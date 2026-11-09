// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import java.util.UUID

/** Read alternatives, not an enabled writer/dispatch profile. Legacy bytes keep their old codec. */
internal sealed interface ExecutionProfileDescriptor { val key:WorkKey.Profile }
internal data class NativeProfileDescriptor(override val key:WorkKey.Profile,val value:WProfile):ExecutionProfileDescriptor
internal data class MechanicalProfileDescriptor(override val key:WorkKey.Profile,val value:MechanicalProfile):ExecutionProfileDescriptor
internal data class MechanicalProfile(
    val nativeProfileId:String,val qualificationEvidence:List<WSource>,val checkProfileId:String,
    val maximumPatchBytes:Long,val maximumFileBytes:Long,val maximumCheckOutputBytes:Long,val maximumCheckMillis:Long,
    val executionAssurance:String,val permissions:List<WProfilePermissionsItem>,val platform:String,val qualification:String
)

internal sealed interface ExecutionProducerDescriptor { val executionId:UUID;val workspaceId:UUID }
internal data class NativeProducerDescriptor(val value:WExecution):ExecutionProducerDescriptor {
    override val executionId get()=value.executionId
    override val workspaceId get()=value.workspaceId
}
/** No principal/credential/model/chat-session is invented for a deterministic process. The actual
 * controller authenticates separately; this metadata cannot authorize an operation. */
internal data class MechanicalProducerDescriptor(
    override val executionId:UUID,val controllerId:UUID,val nativeHostId:UUID,
    override val workspaceId:UUID,val operation:WorkKey.Id
):ExecutionProducerDescriptor

internal sealed interface ResultAuthorship
internal data class LegacyProducerAuthorship(val principalId:UUID):ResultAuthorship
/** Complete declaration, not mechanical proof that every semantic contributor was discovered. */
internal data class DeclaredResultAuthorship(val declaredBy:UUID,val authors:List<UUID>,val sourceResults:List<WorkReference>):ResultAuthorship

/** A new mechanical result cannot use a legacy producer-only view as complete attribution.
 * knownAuthors must come from actual selected predecessors, not an applier-supplied empty list. */
internal fun requireDeclaredAuthorship(value:DeclaredResultAuthorship,authenticatedSubmitter:UUID,knownAuthors:Set<UUID>) {
    workRequire(value.declaredBy==authenticatedSubmitter && authenticatedSubmitter in value.authors &&
        value.authors.toSet().containsAll(knownAuthors),WorkProblem.INVALID_SUBJECT)
}

/** Structural NON-author candidate only. Does not check rights, fresh context, immutable snapshot,
 * required gates or evidence applicability and therefore cannot itself grant review/acceptance. */
internal fun nonAuthorCandidate(principal:UUID,execution:UUID,producer:ExecutionProducerDescriptor,
    authorship:ResultAuthorship,requireComplete:Boolean,knownAuthors:Set<UUID>):Boolean {
    if(execution==producer.executionId)return false
    if(producer is NativeProducerDescriptor && principal==producer.value.principalId)return false
    return when(authorship) {
        is LegacyProducerAuthorship -> producer is NativeProducerDescriptor && !requireComplete && principal!=authorship.principalId && principal !in knownAuthors
        is DeclaredResultAuthorship -> authorship.authors.isNotEmpty() && authorship.authors.size<=256 &&
            authorship.authors.distinct().size==authorship.authors.size && authorship.declaredBy in authorship.authors &&
            authorship.authors.containsAll(knownAuthors) && principal !in authorship.authors
    }
}

/** Stage codec only: record2 is not silently admitted by command3/record1 or a legacy writer.
 * Selecting a new store/writer profile, reducer/auth/claim/result/history integration remains held. */
internal class ExecutionDescriptorCodec {
    private val legacy=WorkCodec()
    private fun limits(historical:Boolean)=if(historical)
        JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)
        else JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000)
    fun decodeProfile(bytes:ByteArray,historical:Boolean=false):ExecutionProfileDescriptor {
        val f=StrictJson.decode(bytes,limits(historical),unavailable=historical).fields("formatVersion","family","key","payload")
        if(f.getValue("formatVersion").long()==1L) {
            val row=legacy.decodeRecord(bytes,historical)
            shape(row.identity.family==WorkFamily.PROFILES)
            return NativeProfileDescriptor(row.identity.key as WorkKey.Profile,(row.payload as WProfilesPayload).value)
        }
        shape(f.getValue("formatVersion").long()==2L && f.getValue("family").string()=="profiles")
        val key=identityCodec.decodeKey(WorkFamily.PROFILES,StrictJson.encode(f.getValue("key"))) as WorkKey.Profile
        val p=decodeMechanicalProfile(f.getValue("payload"))
        sameProject(encodeMechanicalProfile(p),key.projectId)
        return MechanicalProfileDescriptor(key,p)
    }
    fun encodeProfile(value:ExecutionProfileDescriptor,historical:Boolean=false):ByteArray {
        if(value is NativeProfileDescriptor)return legacy.encodeRecord(WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,value.key),WProfilesPayload(value.value)),historical)
        value as MechanicalProfileDescriptor
        val bytes=StrictJson.encode(obj("formatVersion" to num(2),"family" to str("profiles"),
            "key" to StrictJson.decode(identityCodec.encodeKey(value.key)),"payload" to encodeMechanicalProfile(value.value)),
            if(historical)4*1024*1024 else 262144)
        decodeProfile(bytes,historical);return bytes
    }
    private fun decodeMechanicalProfile(v:JsonValue):MechanicalProfile {
        val f=v.fields("kind","nativeProfileId","qualificationEvidence","checkProfileId","maximumPatchBytes","maximumFileBytes",
            "maximumCheckOutputBytes","maximumCheckMillis","executionAssurance","permissions","platform","qualification")
        shape(f.getValue("kind").string()=="mechanical-v1")
        fun bounded(name:String,maximum:Long)=f.getValue(name).long().also { shape(it in 1..maximum) }
        val assurance=f.getValue("executionAssurance").string().also { shape(it=="E-COOP-1") }
        val platform=f.getValue("platform").string().also { shape(it in setOf("linux","windows","macos")) }
        val qualification=f.getValue("qualification").string().also { shape(it in setOf("unqualified","qualified")) }
        return MechanicalProfile(workText(f.getValue("nativeProfileId"),1,128),workList(f.getValue("qualificationEvidence"),0,256,false,::decodeWSource),
            workText(f.getValue("checkProfileId"),1,128),bounded("maximumPatchBytes",1024*1024),bounded("maximumFileBytes",8*1024*1024),
            bounded("maximumCheckOutputBytes",1024*1024),bounded("maximumCheckMillis",3600000),assurance,
            workList(f.getValue("permissions"),0,256,false,::decodeWProfilePermissionsItem),platform,qualification)
    }
    private fun encodeMechanicalProfile(p:MechanicalProfile):JsonValue = obj("kind" to str("mechanical-v1"),
        "nativeProfileId" to str(p.nativeProfileId),"qualificationEvidence" to workArray(p.qualificationEvidence,0,256,false,::encodeWSource),
        "checkProfileId" to str(p.checkProfileId),"maximumPatchBytes" to JNumber(p.maximumPatchBytes),"maximumFileBytes" to JNumber(p.maximumFileBytes),
        "maximumCheckOutputBytes" to JNumber(p.maximumCheckOutputBytes),"maximumCheckMillis" to JNumber(p.maximumCheckMillis),
        "executionAssurance" to str(p.executionAssurance),"permissions" to workArray(p.permissions,0,256,false,::encodeWProfilePermissionsItem),
        "platform" to str(p.platform),"qualification" to str(p.qualification))

    fun decodeProducer(bytes:ByteArray,project:UUID):ExecutionProducerDescriptor {
        val value=StrictJson.decode(bytes,JsonLimits(bytes=16384,stringBytes=8192,depth=16,collection=64,tokens=2048))
        if(value is JObject && "formatVersion" !in value.entries)return NativeProducerDescriptor(decodeWExecution(value))
        val f=value.fields("formatVersion","kind","executionId","controllerId","nativeHostId","workspaceId","operation")
        shape(f.getValue("formatVersion").long()==2L && f.getValue("kind").string()=="mechanical-v1")
        val operation=identityCodec.decodeKey(WorkFamily.NATIVE_INTENTS,StrictJson.encode(f.getValue("operation"))) as WorkKey.Id
        shape(operation.projectId==project)
        return MechanicalProducerDescriptor(uuid(f.getValue("executionId")),uuid(f.getValue("controllerId")),uuid(f.getValue("nativeHostId")),uuid(f.getValue("workspaceId")),operation)
    }
    fun encodeProducer(value:ExecutionProducerDescriptor,project:UUID):ByteArray {
        val json=when(value) {
            is NativeProducerDescriptor -> encodeWExecution(value.value)
            is MechanicalProducerDescriptor -> obj("formatVersion" to num(2),"kind" to str("mechanical-v1"),"executionId" to uuid(value.executionId),
                "controllerId" to uuid(value.controllerId),"nativeHostId" to uuid(value.nativeHostId),"workspaceId" to uuid(value.workspaceId),
                "operation" to StrictJson.decode(identityCodec.encodeKey(value.operation)))
        }
        val bytes=StrictJson.encode(json,16384);decodeProducer(bytes,project);return bytes
    }
    fun decodeAuthorship(bytes:ByteArray,project:UUID):DeclaredResultAuthorship {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000))
            .fields("formatVersion","kind","declaredBy","authors","sourceResults")
        shape(f.getValue("formatVersion").long()==1L && f.getValue("kind").string()=="declared-authors")
        val author=uuid(f.getValue("declaredBy"))
        val authors=workList<UUID>(f.getValue("authors"),1,256,true) { uuid(it) }
        shape(author in authors)
        val sources=workList(f.getValue("sourceResults"),0,256,true) {
            identityCodec.decodeReference(StrictJson.encode(it)).also { ref ->shape(ref.identity.family==WorkFamily.RESULTS && ref.identity.key.projectId==project) }
        }
        return DeclaredResultAuthorship(author,authors,sources)
    }
    fun encodeAuthorship(value:DeclaredResultAuthorship,project:UUID):ByteArray {
        val bytes=StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("declared-authors"),"declaredBy" to uuid(value.declaredBy),
            "authors" to workArray(value.authors,1,256,true,::uuid),"sourceResults" to workArray(value.sourceResults,0,256,true) {
                StrictJson.decode(identityCodec.encodeReference(it)) }),262144)
        decodeAuthorship(bytes,project);return bytes
    }
}
