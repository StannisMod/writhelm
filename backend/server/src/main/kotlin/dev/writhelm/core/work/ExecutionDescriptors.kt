// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.framedDigest
import dev.writhelm.core.knowledge.writeFramed
import java.util.UUID

/** Read alternatives, not an enabled writer/dispatch profile. Legacy bytes keep their old codec. */
internal sealed interface ExecutionProfileDescriptor { val key:WorkKey.Profile }
internal data class NativeProfileDescriptor(override val key:WorkKey.Profile,val value:WProfile):ExecutionProfileDescriptor
internal data class MechanicalProfileDescriptor(override val key:WorkKey.Profile,val value:MechanicalProfile):ExecutionProfileDescriptor
/** Same canonical graph, only under explicit candidate model3 selection. No enabled writer. */
internal data class WMechanicalProfilesPayload(val value:MechanicalProfile):WorkPayload
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

/** One bounded correction has one execution/operation and no native session or delegation.
 * New work needs a new assignment, not an adoption/rebind of an uncertain original effect. */
internal data class MechanicalBinding(
    val task:WorkKey.TaskRevision,val manifest:WorkReference,val profile:WorkKey.Profile,
    val producer:MechanicalProducerDescriptor,val resources:List<WorkKey.Resource>,val authorship:DeclaredResultAuthorship
)
internal data class MechanicalAssignment(val generation:Long,val bindingData:MechanicalBinding,val binding:WorkReference?,val state:String)
internal data class MechanicalOperation(
    val generation:Long,val assignment:WAssignmentSubject,val binding:WorkReference,
    val patch:WorkBodyReference,val witnesses:WorkBodyReference,val beforeDigest:Digest,val afterDigest:Digest,
    val outcome:String,val observedDigest:Digest?,val writerCompleted:Boolean,val evidence:List<WorkReference>,val observations:List<WSource>
)
internal data class WMechanicalAssignmentsPayload(val value:MechanicalAssignment):WorkPayload
internal data class WMechanicalBindingsPayload(val value:MechanicalBinding):WorkPayload
internal data class WMechanicalOperationsPayload(val value:MechanicalOperation):WorkPayload

internal data class MechanicalResourceExpectation(val resource:WorkKey.Resource,val generation:Long?)
internal sealed interface MechanicalCommandOperation {
    data class Reserve(val assignmentId:UUID,val binding:MechanicalBinding,val resources:List<MechanicalResourceExpectation>):MechanicalCommandOperation
    data class Bind(val assignmentId:UUID,val generation:Long,val binding:WorkReference):MechanicalCommandOperation
}
internal data class MechanicalCommand(val requestKey:UUID,val projectId:UUID,val operation:MechanicalCommandOperation)

/** Staged command4 reservation/binding codec, NOT a store catalogue, writer, service command or
 * authority. Remaining effect/result operation schemas are deliberately unsupported. */
internal class MechanicalCommandCodec {
    private val mechanical=MechanicalWorkCodec()
    private val limits=JsonLimits(bytes=262144,stringBytes=65536,depth=32,collection=1024,tokens=100000)
    private fun json(k:WorkKey)=StrictJson.decode(identityCodec.encodeKey(k))
    private fun json(r:WorkReference)=StrictJson.decode(identityCodec.encodeReference(r))
    private fun generation(v:JsonValue)=v.long().also { shape(it>=0) }
    fun decode(bytes:ByteArray):MechanicalCommand {
        val f=StrictJson.decode(bytes,limits).fields("formatVersion","requestKey","projectId","operation","payload")
        if(f.getValue("formatVersion").long()!=4L)refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val project=uuid(f.getValue("projectId"))
        val payload=f.getValue("payload")
        val operation=when(f.getValue("operation").string()) {
            "reserve_mechanical" -> {
                val p=payload.fields("assignmentId","binding","resources")
                val binding=mechanical.decodeBinding(p.getValue("binding"),project)
                val resources=workList(p.getValue("resources"),1,256,true) { item ->
                    val fields=item.fields("resource","expectedGeneration")
                    MechanicalResourceExpectation(identityCodec.decodeKey(WorkFamily.CLAIMS,StrictJson.encode(fields.getValue("resource"))) as WorkKey.Resource,
                        fields.getValue("expectedGeneration").let { if(it===JNull)null else generation(it) })
                }
                shape(resources.map { it.resource }.distinct().size==resources.size && resources.map { it.resource }.toSet()==binding.resources.toSet())
                MechanicalCommandOperation.Reserve(uuid(p.getValue("assignmentId")),binding,resources)
            }
            "bind_mechanical" -> {
                val p=payload.fields("assignmentId","expectedGeneration","binding")
                val assignment=uuid(p.getValue("assignmentId"))
                val binding=identityCodec.decodeReference(StrictJson.encode(p.getValue("binding")))
                shape(binding.identity.family==WorkFamily.ASSIGNMENT_BINDINGS && binding.identity.key==WorkKey.AssignmentBinding(project,assignment,0))
                MechanicalCommandOperation.Bind(assignment,generation(p.getValue("expectedGeneration")),binding)
            }
            else -> refuse(CodecProblem.UNSUPPORTED_FORMAT)
        }
        val command=MechanicalCommand(uuid(f.getValue("requestKey")),project,operation)
        sameProject(operationJSON(command.operation).second,project)
        return command
    }
    private fun operationJSON(v:MechanicalCommandOperation):Pair<String,JsonValue> = when(v) {
        is MechanicalCommandOperation.Reserve -> "reserve_mechanical" to obj("assignmentId" to uuid(v.assignmentId),"binding" to mechanical.encodeBinding(v.binding),
            "resources" to workArray(v.resources,1,256,true) { obj("resource" to json(it.resource),"expectedGeneration" to (it.generation?.let(::JNumber) ?: JNull)) })
        is MechanicalCommandOperation.Bind -> "bind_mechanical" to obj("assignmentId" to uuid(v.assignmentId),"expectedGeneration" to JNumber(v.generation),"binding" to json(v.binding))
    }
    private fun value(command:MechanicalCommand,includeKey:Boolean):JsonValue {
        val (name,payload)=operationJSON(command.operation)
        val fields=linkedMapOf<String,JsonValue>("formatVersion" to JNumber(4),"projectId" to uuid(command.projectId),"operation" to str(name),"payload" to payload)
        if(includeKey)fields["requestKey"]=uuid(command.requestKey)
        return JObject(fields)
    }
    fun encode(command:MechanicalCommand):ByteArray {
        val bytes=JsonConstruction.bounded(limits,{ _,_-> },false) { StrictJson.encode(value(command,true),limits.bytes,maxDepth=limits.depth) }
        decode(bytes);return bytes
    }
    fun fingerprint(command:MechanicalCommand):Digest {
        val canonical=decode(encode(command))
        return framedDigest("writhelm.command.v4") { writeFramed(StrictJson.encode(value(canonical,false),limits.bytes)) }
    }
}

/** Candidate record2 shapes only. This codec has no authenticated command, writer or native effect. */
internal class MechanicalWorkCodec {
    private val execution=ExecutionDescriptorCodec()
    private fun key(f:WorkFamily,v:JsonValue)=identityCodec.decodeKey(f,StrictJson.encode(v))
    private fun ref(v:JsonValue,f:WorkFamily)=identityCodec.decodeReference(StrictJson.encode(v)).also { shape(it.identity.family==f) }
    private fun key(v:WorkKey)=StrictJson.decode(identityCodec.encodeKey(v))
    private fun ref(v:WorkReference)=StrictJson.decode(identityCodec.encodeReference(v))
    private fun body(v:WorkBodyReference)=StrictJson.decode(identityCodec.encodeBodyReference(v))
    fun decodeBinding(v:JsonValue,project:UUID):MechanicalBinding {
        val f=v.fields("kind","task","manifest","profile","producer","resources","authorship")
        shape(f.getValue("kind").string()=="mechanical-v1")
        val producer=execution.decodeProducer(StrictJson.encode(f.getValue("producer")),project) as? MechanicalProducerDescriptor ?: refuse(CodecProblem.INVALID_SHAPE)
        val authorship=execution.decodeAuthorship(StrictJson.encode(f.getValue("authorship")),project)
        shape(authorship.sourceResults.isNotEmpty())
        return MechanicalBinding(key(WorkFamily.TASK_REVISIONS,f.getValue("task")) as WorkKey.TaskRevision,
            ref(f.getValue("manifest"),WorkFamily.INPUT_MANIFESTS),key(WorkFamily.PROFILES,f.getValue("profile")) as WorkKey.Profile,producer,
            workList(f.getValue("resources"),1,256,true) { key(WorkFamily.CLAIMS,it) as WorkKey.Resource },authorship)
    }
    fun encodeBinding(v:MechanicalBinding):JsonValue=obj("kind" to str("mechanical-v1"),"task" to key(v.task),"manifest" to ref(v.manifest),
        "profile" to key(v.profile),"producer" to StrictJson.decode(execution.encodeProducer(v.producer,v.task.projectId)),
        "resources" to workArray(v.resources,1,256,true,::key),"authorship" to StrictJson.decode(execution.encodeAuthorship(v.authorship,v.task.projectId)))
    fun decode(family:WorkFamily,v:JsonValue,project:UUID):WorkPayload=when(family) {
        WorkFamily.ASSIGNMENT_BINDINGS -> WMechanicalBindingsPayload(decodeBinding(v,project))
        WorkFamily.ASSIGNMENTS -> {
            val f=v.fields("kind","generation","bindingData","binding","state")
            shape(f.getValue("kind").string()=="mechanical-v1")
            val generation=f.getValue("generation").long().also { shape(it>=0) }
            val state=f.getValue("state").string().also { shape(it in setOf("reserved","bound","active","unknown","completed")) }
            val binding=f.getValue("binding").let { if(it===JNull)null else ref(it,WorkFamily.ASSIGNMENT_BINDINGS) }
            shape((state=="reserved")== (binding==null))
            WMechanicalAssignmentsPayload(MechanicalAssignment(generation,decodeBinding(f.getValue("bindingData"),project),binding,state))
        }
        WorkFamily.NATIVE_INTENTS -> {
            val f=v.fields("kind","generation","assignment","binding","patch","witnesses","beforeDigest","afterDigest","outcome","observedDigest","writerCompleted","evidence","observations")
            shape(f.getValue("kind").string()=="mechanical-v1")
            val generation=f.getValue("generation").long().also { shape(it>=0) }
            val outcome=f.getValue("outcome").string().also { shape(it in setOf("pending","unknown","applied","not-applied","partial")) }
            val before=digest(f.getValue("beforeDigest"));val after=digest(f.getValue("afterDigest"));shape(before!=after)
            val observed=f.getValue("observedDigest").let { if(it===JNull)null else digest(it) }
            val completed=workBoolean(f.getValue("writerCompleted"))
            shape(completed==(outcome in setOf("applied","not-applied","partial")))
            if(outcome=="pending")shape(observed==null)
            if(outcome=="applied")shape(observed==after)
            if(outcome=="not-applied")shape(observed==before)
            if(outcome=="partial")shape(observed!=null && observed!=before && observed!=after)
            val patch=identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("patch")))
            val witnesses=identityCodec.decodeBodyReference(StrictJson.encode(f.getValue("witnesses")))
            shape(patch.byteLength>0 && witnesses.byteLength>0)
            val assignment=decodeWAssignmentSubject(f.getValue("assignment"));shape(assignment.generation==0L)
            WMechanicalOperationsPayload(MechanicalOperation(generation,assignment,ref(f.getValue("binding"),WorkFamily.ASSIGNMENT_BINDINGS),patch,witnesses,before,after,outcome,observed,completed,
                workList(f.getValue("evidence"),0,256,true) { ref(it,WorkFamily.EVIDENCE) },workList(f.getValue("observations"),0,256,false,::decodeWSource)))
        }
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
    fun encode(p:WorkPayload):JsonValue=when(p) {
        is WMechanicalBindingsPayload -> encodeBinding(p.value)
        is WMechanicalAssignmentsPayload -> p.value.let { obj("kind" to str("mechanical-v1"),"generation" to JNumber(it.generation),
            "bindingData" to encodeBinding(it.bindingData),"binding" to (it.binding?.let(::ref) ?: JNull),"state" to str(it.state)) }
        is WMechanicalOperationsPayload -> p.value.let { obj("kind" to str("mechanical-v1"),"generation" to JNumber(it.generation),
            "assignment" to encodeWAssignmentSubject(it.assignment),"binding" to ref(it.binding),"patch" to body(it.patch),"witnesses" to body(it.witnesses),
            "beforeDigest" to digest(it.beforeDigest),"afterDigest" to digest(it.afterDigest),"outcome" to str(it.outcome),
            "observedDigest" to (it.observedDigest?.let(::digest) ?: JNull),"writerCompleted" to JBoolean(it.writerCompleted),
            "evidence" to workArray(it.evidence,0,256,true,::ref),"observations" to workArray(it.observations,0,256,false,::encodeWSource)) }
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
}

/** Structural history invariants only. Actual effect/writer facts still require authenticated
 * command admission; a consistent uploaded record is never authority to apply/release claims. */
internal fun validateMechanicalEdge(old:WorkPayload,next:WorkPayload) {
    if(old is WMechanicalAssignmentsPayload || next is WMechanicalAssignmentsPayload) {
        workRequire(old is WMechanicalAssignmentsPayload && next is WMechanicalAssignmentsPayload,WorkProblem.INVALID_TRANSITION)
        old as WMechanicalAssignmentsPayload;next as WMechanicalAssignmentsPayload
        val a=old.value;val b=next.value
        workRequire(b.generation==nextGeneration(a.generation),WorkProblem.STALE_GENERATION)
        workRequire(a.bindingData==b.bindingData && (a.binding==null || a.binding==b.binding),WorkProblem.INVALID_BINDING)
        val allowed=when(a.state) {
            "reserved" -> setOf("bound")
            "bound" -> setOf("active")
            "active" -> setOf("unknown","completed")
            "unknown" -> setOf("unknown","completed")
            else -> emptySet()
        }
        workRequire(b.state in allowed,WorkProblem.INVALID_TRANSITION)
    }
    if(old is WMechanicalOperationsPayload || next is WMechanicalOperationsPayload) {
        workRequire(old is WMechanicalOperationsPayload && next is WMechanicalOperationsPayload,WorkProblem.INVALID_TRANSITION)
        old as WMechanicalOperationsPayload;next as WMechanicalOperationsPayload
        val a=old.value;val b=next.value
        workRequire(b.generation==nextGeneration(a.generation),WorkProblem.STALE_GENERATION)
        workRequire(a.copy(generation=b.generation,outcome=b.outcome,observedDigest=b.observedDigest,writerCompleted=b.writerCompleted,
            evidence=b.evidence,observations=b.observations)==b,WorkProblem.INVALID_SUBJECT)
        workRequire(!a.writerCompleted && b.outcome!="pending" && b.observations.containsAll(a.observations) && b.evidence.containsAll(a.evidence),WorkProblem.INVALID_TRANSITION)
    }
}

/** Candidate mechanical row edge, suitable for later historical audit integration. Callers must
 * independently validate receipt/actor/current authority, both complete snapshots, exact changed
 * paths, and actual native observations. Absence or matching snapshots alone are not that proof. */
internal fun validateMechanicalRecordEdge(before:WorkRecord?,after:WorkRecord?) {
    val next=after ?: throw WorkFault(WorkProblem.INVALID_TRANSITION)
    val p=next.payload
    workRequire(p is WMechanicalAssignmentsPayload || p is WMechanicalBindingsPayload ||
        p is WMechanicalOperationsPayload || p is WMechanicalProfilesPayload || p is WMechanicalResultsPayload,WorkProblem.INVALID_SUBJECT)
    if(before==null) {
        when(p) {
            is WMechanicalAssignmentsPayload -> workRequire(p.value.generation==0L && p.value.state=="reserved" && p.value.binding==null,WorkProblem.INVALID_TRANSITION)
            is WMechanicalOperationsPayload -> workRequire(p.value.generation==0L && p.value.outcome=="pending" && !p.value.writerCompleted,WorkProblem.INVALID_TRANSITION)
            else -> Unit
        }
        return
    }
    workRequire(before.identity==next.identity,WorkProblem.INVALID_SUBJECT)
    if(!next.identity.family.mutable)throw WorkFault(WorkProblem.INVALID_TRANSITION)
    validateMechanicalEdge(before.payload,p)
}

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
    internal fun encodeMechanicalProfile(p:MechanicalProfile):JsonValue = obj("kind" to str("mechanical-v1"),
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
