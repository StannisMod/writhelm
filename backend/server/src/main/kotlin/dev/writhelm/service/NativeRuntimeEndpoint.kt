// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** One shared service, separately bound native controller endpoints. Startup/broker code registers
 * endpoints; no request can supply a controller selector or install/replace a peer. Ambiguous proof
 * matches deny rather than picking the last registered controller. */
internal class NativeRuntimeEndpointRouter:AutoCloseable {
    private val endpoints=linkedMapOf<UUID,NativeRuntimeEndpoint>()
    private var closed=false
    @Synchronized fun register(controller:UUID,endpoint:NativeRuntimeEndpoint) {
        check(!closed)
        val previous=endpoints[controller]
        require(previous==null || previous===endpoint) { "Controller endpoint already registered" }
        require(previous!=null || endpoints.size<256) { "Native controller capacity unavailable" }
        endpoints[controller]=endpoint
    }
    @Synchronized fun select(proof:String?):NativeRuntimeEndpoint? {
        if(closed)return null
        return endpoints.values.filter {it.accepts(proof)}.singleOrNull()
    }
    @Synchronized override fun close() {
        closed=true
        endpoints.values.forEach {it.close()}
        endpoints.clear()
    }
}

/** Optional private host transport, not a model tool or canonical command/read version.
 * Both the separately retained native-host proof AND an actual scoped service credential are
 * required. No caller-selected actor or authority administration. The optional connect callback
 * starts only the existing trusted runtime composition, never a caller-specified command. */
internal class NativeRuntimeEndpoint(private val app: H1Application,private val project: UUID,
    private val controller: UUID,private val host: UUID,proof: ByteArray,
    private val onConnect: (()->Unit)?=null): AutoCloseable {
    private val proof=proof.copyOf().also { require(it.size==32) }
    companion object {
        const val RESULT_COMMAND_BYTES=2*1024*1024
        // JSON string escaping may double the original bytes; reserve bounded envelope overhead.
        const val RESULT_REQUEST_BYTES=2*RESULT_COMMAND_BYTES+16384
    }
    @Volatile private var closed=false
    fun accepts(value: String?): Boolean {
        if(closed || value==null || !value.matches(Regex("[A-Za-z0-9_-]{43}")))return false
        val supplied=try { Base64.getUrlDecoder().decode(value) } catch(_: IllegalArgumentException) { return false }
        return try { supplied.size==32 && Base64.getUrlEncoder().withoutPadding().encodeToString(supplied)==value &&
            MessageDigest.isEqual(proof,supplied) } finally { supplied.fill(0) }
    }
    fun read(token: String,bytes: ByteArray): ServiceReply=readBounded(token,bytes,false)
    fun readCandidate(token:String,bytes:ByteArray):ServiceReply=readBounded(token,bytes,true)
    private fun readBounded(token:String,bytes:ByteArray,candidate:Boolean):ServiceReply=try {
        if(closed)throw ServiceFault("UNAVAILABLE",503)
        decode(token,bytes,candidate)
    }
        catch(_: AppAccessFault) { throw ServiceFault("WORK_UNAVAILABLE_OR_FORBIDDEN",403) }
        catch(_: CodecFault) { throw ServiceFault("INVALID_INPUT",400) }
        catch(_: IllegalArgumentException) { throw ServiceFault("INVALID_INPUT",400) }
        catch(_: WorkFault) { throw ServiceFault("WORK_UNAVAILABLE_OR_FORBIDDEN",403) }
    /** Dedicated large read route. Other native operations retain their original 16 KiB limit. */
    fun readResultReceipt(token:String,bytes:ByteArray):ServiceReply=try {
        if(closed)throw ServiceFault("UNAVAILABLE",503)
        val fields=StrictJson.decode(bytes,JsonLimits(bytes=RESULT_REQUEST_BYTES,stringBytes=RESULT_COMMAND_BYTES,collection=32,tokens=256))
            .fields("formatVersion","operation","projectId","commandJSON")
        if(fields.getValue("formatVersion").long()!=1L || fields.getValue("operation").string()!="original-result-receipt")throw ServiceFault("INVALID_INPUT",400)
        if(uuid(fields.getValue("projectId"))!=project)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val original=fields.getValue("commandJSON").string().toByteArray(Charsets.UTF_8)
        if(original.size>RESULT_COMMAND_BYTES)throw ServiceFault("INVALID_INPUT",400)
        val result=app.originalResultReceipt(token,project,controller,host,original)
        ServiceReply(200,obj("formatVersion" to num(1),"kind" to str(when(result) {
            OriginalResultReceipt.MATCH->"match";OriginalResultReceipt.MISSING->"missing";OriginalResultReceipt.HOLD->"hold"
        })))
    } catch(_:AppAccessFault) { throw ServiceFault("WORK_UNAVAILABLE_OR_FORBIDDEN",403) }
      catch(_:CodecFault) { throw ServiceFault("INVALID_INPUT",400) }
      catch(_:IllegalArgumentException) { throw ServiceFault("INVALID_INPUT",400) }
      catch(_:WorkFault) { throw ServiceFault("WORK_UNAVAILABLE_OR_FORBIDDEN",403) }
    private fun decode(token: String,bytes: ByteArray,candidate:Boolean=false): ServiceReply {
        val decoded=StrictJson.decode(bytes,if(candidate)JsonLimits(bytes=4*1024*1024,stringBytes=2*1024*1024,collection=4096,tokens=100000)
            else JsonLimits(bytes=16384,stringBytes=8192,collection=32,tokens=256)) as? JObject
            ?: throw ServiceFault("INVALID_INPUT",400)
        val operation=decoded.entries["operation"]?.string() ?: throw ServiceFault("INVALID_INPUT",400)
        if(candidate && operation !in setOf("candidate-assembly-permit","candidate-test-plan","candidate-test-permit","candidate-delivery","candidate-review-ticket","candidate-review-report","candidate-review-status","candidate-review-preparation"))throw ServiceFault("INVALID_INPUT",400)
        val fields=when(operation) {
            "identity","source-identity","worker-context","connect","controller-planning-admission" -> decoded.fields("formatVersion","operation","projectId")
            "controller-context" -> decoded.fields("formatVersion","operation","projectId","profileJSON")
            "planner-task-progress" -> decoded.fields("formatVersion","operation","projectId","taskReferenceJSON")
            "dispatch-context" -> decoded.fields("formatVersion","operation","projectId","planId")
            "candidate-assembly-permit" -> decoded.fields("formatVersion","operation","projectId","requestJSON")
            "candidate-test-plan" -> decoded.fields("formatVersion","operation","projectId","planRefJSON")
            "candidate-delivery" -> decoded.fields("formatVersion","operation","projectId","planRefJSON")
            "candidate-review-ticket" -> decoded.fields("formatVersion","operation","projectId","ticketJSON")
            "candidate-review-report" -> decoded.fields("formatVersion","operation","projectId","reviewId","reportRefJSON")
            "candidate-review-status" -> decoded.fields("formatVersion","operation","projectId","reviewId")
            "candidate-review-preparation" -> decoded.fields("formatVersion","operation","projectId","planRefJSON","assemblyId","checkPlanId")
            "candidate-test-permit" -> decoded.fields("formatVersion","operation","projectId","planRefJSON","assemblyJSON","jobJSON")
            "dispatch-materialization" -> decoded.fields("formatVersion","operation","projectId","planId","taskJSON","newTaskJSON","verificationPlanJSON","manifestJSON","taskRequestId","manifestRequestId")
            "reservation-retry" -> decoded.fields("formatVersion","operation","projectId","reservationJSON")
            "original-result-receipt" -> decoded.fields("formatVersion","operation","projectId","commandJSON")
            "preparation-context" -> decoded.fields("formatVersion","operation","projectId","assignmentId","executionGeneration")
            "retention-permit" -> decoded.fields("formatVersion","operation","projectId","assignmentId","executionGeneration","executionId","snapshotJSON")
            "initial-context" -> decoded.fields("formatVersion","operation","projectId","assignmentId","executionGeneration","bindingJSON","intentId")
            "status","packet","hint" -> decoded.fields("formatVersion","operation","projectId","assignmentId","executionGeneration","bindingJSON","continuationId")
            else -> throw ServiceFault("INVALID_INPUT",400)
        }
        if(fields.getValue("formatVersion").long()!=1L)throw ServiceFault("INVALID_INPUT",400)
        if(uuid(fields.getValue("projectId"))!=project)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val data=if(operation=="candidate-delivery"||operation=="candidate-test-plan"||operation=="candidate-test-permit") {
            val plan=WorkIdentityCodec().decodeReference(fields.getValue("planRefJSON").string().toByteArray())
            if(operation=="candidate-delivery")app.nativeCandidateDelivery(token,project,controller,host,plan)
            else if(operation=="candidate-test-plan")app.nativeCandidateTestPlan(token,project,controller,host,plan)
            else app.nativeCandidateTestPermit(token,project,controller,host,fields.getValue("assemblyJSON").string(),plan,fields.getValue("jobJSON").string())
        } else if(operation=="candidate-review-preparation") {
            app.nativeCandidateReviewPreparation(token,project,controller,host,WorkIdentityCodec().decodeReference(fields.getValue("planRefJSON").string().toByteArray()),
                uuid(fields.getValue("assemblyId")),uuid(fields.getValue("checkPlanId")))
        } else if(operation=="candidate-review-status") {
            app.nativeCandidateReviewStatus(token,project,controller,host,uuid(fields.getValue("reviewId")))
        } else if(operation=="candidate-review-report") {
            app.nativeCandidateReviewReport(token,project,controller,host,uuid(fields.getValue("reviewId")),
                WorkIdentityCodec().decodeReference(fields.getValue("reportRefJSON").string().toByteArray()))
        } else if(operation=="candidate-review-ticket") {
            app.nativeIssueCandidateReviewTicket(token,project,controller,host,fields.getValue("ticketJSON").string())
        } else if(operation=="candidate-assembly-permit") {
            app.nativeCandidateAssemblyPermit(token,project,controller,host,fields.getValue("requestJSON").string())
        } else if(operation=="connect") {
            val identity=app.nativeRuntimeRead(token,project,controller,host,null,null,null,sourceIdentity=true) as JObject
            if(identity.entries.getValue("scope").string()!="controller")throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            val connect=onConnect ?: throw ServiceFault("UNAVAILABLE",503)
            // TASK_DISPATCH is independent of TASK_PLAN. A matching current READ controller may
            // still clarify/design/create drafts when dispatch startup is withheld. Never turn
            // that refusal into a ready runtime or into loss of its separate planning channel.
            val connected=try { connect();true }catch(_: AppAccessFault) { false }
            obj("formatVersion" to num(1),"kind" to str(if(connected)"runtime-connected" else "runtime-held"))
        } else if(operation=="initial-context") {
            val decimal=fields.getValue("executionGeneration").string()
            if(!decimal.matches(Regex("0|[1-9][0-9]{0,18}")))throw ServiceFault("INVALID_INPUT",400)
            val generation=decimal.toLongOrNull() ?: throw ServiceFault("INVALID_INPUT",400)
            val binding=WorkIdentityCodec().decodeReference(fields.getValue("bindingJSON").string().toByteArray(Charsets.UTF_8))
            app.nativeInitialContext(token,project,controller,host,WAssignmentSubject(uuid(fields.getValue("assignmentId")),generation),binding,uuid(fields.getValue("intentId")))
        } else if(operation=="retention-permit") {
            val generation=fields.getValue("executionGeneration").string().toLongOrNull() ?: throw ServiceFault("INVALID_INPUT",400)
            if(generation!=0L)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            val snapshot=decodeWSnapshot(StrictJson.decode(fields.getValue("snapshotJSON").string().toByteArray(Charsets.UTF_8)))
            app.nativeRetentionPermit(token,project,controller,host,WAssignmentSubject(uuid(fields.getValue("assignmentId")),generation),uuid(fields.getValue("executionId")),snapshot)
        } else if(operation=="preparation-context") {
            val decimal=fields.getValue("executionGeneration").string()
            if(!decimal.matches(Regex("0|[1-9][0-9]{0,18}")))throw ServiceFault("INVALID_INPUT",400)
            val generation=decimal.toLongOrNull() ?: throw ServiceFault("INVALID_INPUT",400)
            app.nativePreparationContext(token,project,controller,host,WAssignmentSubject(uuid(fields.getValue("assignmentId")),generation))
        } else if(operation=="original-result-receipt") {
            val original=fields.getValue("commandJSON").string().toByteArray(Charsets.UTF_8)
            val result=app.originalResultReceipt(token,project,controller,host,original)
            obj("formatVersion" to num(1),"kind" to str(when(result) {
                OriginalResultReceipt.MATCH->"match";OriginalResultReceipt.MISSING->"missing";OriginalResultReceipt.HOLD->"hold"
            }))
        } else if(operation=="reservation-retry") {
            val original=fields.getValue("reservationJSON").string().toByteArray(Charsets.UTF_8)
            val result=app.reservationRetryEligibility(token,project,controller,original)
            obj("formatVersion" to num(1),"kind" to str(if(result==ReservationRetryEligibility.DEFINITIVE_REFUSAL)"definitive-refusal" else "hold"))
        } else if(operation=="dispatch-materialization") {
            val codec=WorkIdentityCodec()
            fun key(family:WorkFamily,name:String)=codec.decodeKey(family,fields.getValue(name).string().toByteArray(Charsets.UTF_8))
            app.nativeDispatchMaterialization(token,project,controller,host,uuid(fields.getValue("planId")),
                key(WorkFamily.TASK_REVISIONS,"taskJSON") as WorkKey.TaskRevision,key(WorkFamily.TASK_REVISIONS,"newTaskJSON") as WorkKey.TaskRevision,
                key(WorkFamily.VERIFICATION_PLANS,"verificationPlanJSON") as WorkKey.Revision,key(WorkFamily.INPUT_MANIFESTS,"manifestJSON") as WorkKey.Id,
                uuid(fields.getValue("taskRequestId")),uuid(fields.getValue("manifestRequestId")))
        } else if(operation=="dispatch-context") {
            app.nativeDispatchContext(token,project,controller,host,uuid(fields.getValue("planId")))
        } else if(operation=="controller-context") {
            val profile=WorkIdentityCodec().decodeKey(WorkFamily.PROFILES,fields.getValue("profileJSON").string().toByteArray(Charsets.UTF_8)) as WorkKey.Profile
            app.nativeControllerContext(token,project,controller,profile)
        } else if(operation=="planner-task-progress"){
            val ref=WorkIdentityCodec().decodeReference(fields.getValue("taskReferenceJSON").string().toByteArray(Charsets.UTF_8))
            app.nativePlannerTaskProgress(token,project,controller,host,ref)
        } else if(operation=="controller-planning-admission")app.nativeControllerPlanningAdmission(token,project,controller,host)
        else if(operation=="worker-context")app.nativeWorkerContext(token,project,controller,host)
        else if(operation in setOf("identity","source-identity"))app.nativeRuntimeRead(token,project,controller,host,null,null,null,sourceIdentity=operation=="source-identity")
        else {
            val decimal=fields.getValue("executionGeneration").string()
            if(!decimal.matches(Regex("0|[1-9][0-9]{0,18}")))throw ServiceFault("INVALID_INPUT",400)
            val generation=decimal.toLongOrNull() ?: throw ServiceFault("INVALID_INPUT",400)
            val binding=WorkIdentityCodec().decodeReference(fields.getValue("bindingJSON").string().toByteArray(Charsets.UTF_8))
            if(binding.identity.family!=WorkFamily.ASSIGNMENT_BINDINGS)throw ServiceFault("INVALID_INPUT",400)
            val continuation=fields.getValue("continuationId").let { if(it===JNull)null else uuid(it) }
            val subject=WAssignmentSubject(uuid(fields.getValue("assignmentId")),generation)
            if(operation=="hint") { require(continuation==null);app.nativeObserved(token,project,controller,host,subject,binding) }
            else app.nativeRuntimeRead(token,project,controller,host,subject,binding,continuation,operation=="packet")
        }
        return ServiceReply(200,data).also { it.bytes(if(candidate || operation in setOf("packet","worker-context","controller-context","dispatch-context","dispatch-materialization","preparation-context","initial-context"))4*1024*1024 else 16384) }
    }
    override fun close() { closed=true;proof.fill(0) }
}
