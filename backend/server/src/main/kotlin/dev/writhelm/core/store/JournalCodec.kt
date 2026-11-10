// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.util.UUID

internal object JournalCodec {
    private fun limits(maximum: Int)=JsonLimits(bytes=maximum,stringBytes=1024*1024,depth=32,collection=4096,tokens=200000)
    fun encode(r: Receipt,maximum: Int=4*1024*1024): ByteArray {
        val p=obj("storeId" to uuid(r.storeId),"fingerprintVersion" to num(r.fingerprintVersion),"fingerprint" to str(r.fingerprint),"validatedAt" to r.validatedAt.json(),"authenticatedByCredentialId" to uuid(r.authenticatedByCredentialId),"outcome" to r.outcome.json(),"writes" to JArray(r.writes.map { it.json() }))
        val bytes=StrictJson.encode(obj("formatVersion" to num(r.journalVersion),"family" to str("command-receipt"),"key" to r.identity.json(),"payload" to p),maximum)
        decode(bytes,r.path,maximum)
        return bytes
    }
    fun decode(bytes: ByteArray,path: String,maximum: Int=4*1024*1024): Receipt {
        val e=StrictJson.decode(bytes,limits(maximum),true).fields("formatVersion","family","key","payload")
        val journalVersion=e.getValue("formatVersion").int()
        if(journalVersion !in setOf(2,3))fail(StoreProblem.UNSUPPORTED)
        integrity(e.getValue("family").string()=="command-receipt")
        val identity=CommandIdentity.parse(e.getValue("key")); integrity(identity.path==path)
        val p=e.getValue("payload").fields("storeId","fingerprintVersion","fingerprint","validatedAt","authenticatedByCredentialId","outcome","writes")
        val o=p.getValue("outcome"); val kind=(o as? JObject)?.entries?.get("kind")?.string()
        val outcome=when(kind) {
            "success" -> { val f=o.fields("kind","operation","result","domainReplayed"); val replay=(f.getValue("domainReplayed") as? JBoolean)?.value ?: fail(StoreProblem.INTEGRITY); LogicalOutcome.Success(f.getValue("operation").string(),f.getValue("result"),replay) }
            "rejected" -> { val f=o.fields("kind","domain","code"); LogicalOutcome.Rejected(f.getValue("domain").string(),f.getValue("code").string()) }
            else -> fail(StoreProblem.INTEGRITY)
        }
        val writes=p.getValue("writes").list().map { w ->
            val f=w.fields("namespace","family","key","beforeDigest","afterDigest")
            fun hash(name: String)=f.getValue(name).let { if(it===JNull)null else checkDigest(it.string()) }
            WriteDescriptor(RecordKey(f.getValue("namespace").string(),f.getValue("family").string(),f.getValue("key")),hash("beforeDigest"),hash("afterDigest"))
        }
        return Receipt(uuid(p.getValue("storeId")),identity,p.getValue("fingerprintVersion").int(),p.getValue("fingerprint").string(),GitOid.parse(p.getValue("validatedAt")),uuid(p.getValue("authenticatedByCredentialId")),outcome,writes,journalVersion)
    }
}

/** Candidate command4 catalogue. Not registered in the existing H1 writer or service. */
internal object MechanicalJournalCatalog {
    val resultFamilies=mapOf("reserve_mechanical" to WorkFamily.ASSIGNMENTS,"bind_mechanical" to WorkFamily.ASSIGNMENTS,
        "prepare_mechanical_effect" to WorkFamily.NATIVE_INTENTS,"observe_mechanical_effect" to WorkFamily.NATIVE_INTENTS,
        "record_mechanical_retrospective" to WorkFamily.RETROSPECTIVES,"submit_mechanical_result" to WorkFamily.RESULTS)
    private val common=setOf(WorkProblem.FOREIGN_REFERENCE,WorkProblem.INVALID_REFERENCE,WorkProblem.NOT_FOUND,
        WorkProblem.GENERATION_EXHAUSTED,WorkProblem.INVALID_SUBJECT,WorkProblem.INVALID_BINDING,WorkProblem.STALE_GENERATION)
    val codes=mapOf(
        "reserve_mechanical" to common+setOf(WorkProblem.NOT_READY,WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.CLAIM_CONFLICT),
        "bind_mechanical" to common+setOf(WorkProblem.NOT_READY,WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_TRANSITION),
        "prepare_mechanical_effect" to common+setOf(WorkProblem.NOT_READY,WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.NATIVE_UNKNOWN,WorkProblem.MISSING_BODY),
        "observe_mechanical_effect" to common+setOf(WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_TRANSITION,WorkProblem.NATIVE_UNKNOWN),
        "record_mechanical_retrospective" to common+setOf(WorkProblem.NOT_READY,WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_TRANSITION,WorkProblem.MISSING_BODY),
        "submit_mechanical_result" to common+setOf(WorkProblem.NOT_READY,WorkProblem.BLOCKED_INPUT,WorkProblem.DIGEST_MISMATCH,WorkProblem.DUPLICATE_ID,WorkProblem.WRONG_ASSIGNMENT,WorkProblem.INVALID_TRANSITION,WorkProblem.NATIVE_UNKNOWN,WorkProblem.MISSING_BODY))
}

internal data class MechanicalReceiptIdentity(val principalId:UUID,val projectId:UUID,val operation:String,val requestKey:UUID) {
    init { integrity(operation in MechanicalJournalCatalog.resultFamilies) }
    fun json()=obj("principalId" to uuid(principalId),"projectId" to uuid(projectId),"operation" to str(operation),"requestKey" to uuid(requestKey))
    val path:String get() {
        val hash=framedDigest("writhelm.record-key.v1") { writeFramed(encodeUtf8("command-receipt"));writeFramed(StrictJson.encode(json())) }.hex
        return "commands/receipts/${hash.take(2)}/$hash.json"
    }
    companion object { fun parse(v:JsonValue):MechanicalReceiptIdentity {
        val f=v.fields("principalId","projectId","operation","requestKey")
        return MechanicalReceiptIdentity(uuid(f.getValue("principalId")),uuid(f.getValue("projectId")),f.getValue("operation").string(),uuid(f.getValue("requestKey")))
    } }
}
internal data class MechanicalReceiptResult(val identity:WorkRecordIdentity,val generation:Long?,val digest:Digest)
internal sealed interface MechanicalReceiptOutcome {
    data class Success(val result:MechanicalReceiptResult):MechanicalReceiptOutcome
    data class Rejected(val problem:WorkProblem):MechanicalReceiptOutcome
}

/** Closed structural receipt4 candidate. Exact H->C effects, historical/current authority and
 * independently qualified native facts are checked elsewhere and are NOT established by this codec. */
internal class MechanicalReceipt(val storeId:UUID,val identity:MechanicalReceiptIdentity,val fingerprint:Digest,val validatedAt:GitOid,
    val credentialId:UUID,val outcome:MechanicalReceiptOutcome,writes:List<WriteDescriptor>) {
    val writes:List<WriteDescriptor> = java.util.Collections.unmodifiableList(ArrayList(writes))
    val path get()=identity.path
    init {
        integrity(writes.size<=258 && writes.map { it.path }==writes.map { it.path }.distinct().sorted())
        writes.forEach { w ->
            integrity(w.record.namespace=="work" && w.afterDigest!=null)
            if(w.record.family=="bodies")integrity(w.afterDigest==digest(w.record.key).hex)
            else integrity(WorkIdentityCodec().decodeKey(H1Catalog.family(w.record.family),StrictJson.encode(w.record.key)).projectId==identity.projectId)
        }
        when(outcome) {
            is MechanicalReceiptOutcome.Rejected -> { integrity(outcome.problem in MechanicalJournalCatalog.codes.getValue(identity.operation));integrity(writes.isEmpty()) }
            is MechanicalReceiptOutcome.Success -> validateSuccess(outcome.result)
        }
    }
    private fun validateSuccess(result:MechanicalReceiptResult) {
        val family=MechanicalJournalCatalog.resultFamilies.getValue(identity.operation)
        integrity(result.identity.family==family && result.identity.key.projectId==identity.projectId)
        integrity(if(family.mutable)result.generation!=null && result.generation>=0 else result.generation==null)
        val primary=RecordKey("work",family.tag,StrictJson.decode(WorkIdentityCodec().encodeKey(result.identity.key)))
        val write=writes.singleOrNull { it.path==primary.path } ?: fail(StoreProblem.INTEGRITY)
        integrity(write.afterDigest==result.digest.hex)
        if(family.mutable)integrity(if(write.beforeDigest==null)result.generation==0L else result.generation!!>0L)
        val grouped=writes.groupBy { it.record.family }
        fun count(name:String)=grouped[name].orEmpty().size
        fun created(name:String)=grouped[name].orEmpty().all { it.beforeDigest==null }
        fun updated(name:String)=grouped[name].orEmpty().all { it.beforeDigest!=null }
        val valid=when(identity.operation) {
            "reserve_mechanical" -> count("assignments")==1 && created("assignments") && count("claims") in 1..256 && grouped.keys==setOf("assignments","claims")
            "bind_mechanical" -> count("assignments")==1 && updated("assignments") && count("assignment-bindings")==1 && created("assignment-bindings") && grouped.keys==setOf("assignments","assignment-bindings")
            "prepare_mechanical_effect" -> count("assignments")==1 && updated("assignments") && count("native-intents")==1 && created("native-intents") && count("bodies")<=2 && created("bodies") && grouped.keys.all { it in setOf("assignments","native-intents","bodies") }
            "observe_mechanical_effect" -> count("assignments")==1 && updated("assignments") && count("native-intents")==1 && updated("native-intents") && grouped.keys==setOf("assignments","native-intents")
            "record_mechanical_retrospective" -> count("retrospectives")==1 && created("retrospectives") && writes.size==1
            "submit_mechanical_result" -> count("results")==1 && created("results") && writes.size==1
            else -> false
        }
        integrity(valid)
    }
}

internal object MechanicalReceiptCodec {
    private val limits=JsonLimits(bytes=4*1024*1024,stringBytes=65536,depth=32,collection=4096,tokens=200000)
    fun encode(r:MechanicalReceipt):ByteArray {
        val outcome=when(val o=r.outcome) {
            is MechanicalReceiptOutcome.Rejected -> obj("kind" to str("rejected"),"domain" to str("work"),"code" to str(o.problem.name))
            is MechanicalReceiptOutcome.Success -> obj("kind" to str("success"),"operation" to str(r.identity.operation),"domainReplayed" to JBoolean(false),
                "result" to obj("key" to StrictJson.decode(WorkIdentityCodec().encodeKey(o.result.identity.key)),"generation" to (o.result.generation?.let(::JNumber) ?: JNull),"digest" to digest(o.result.digest)))
        }
        val payload=obj("storeId" to uuid(r.storeId),"fingerprintVersion" to num(4),"fingerprint" to digest(r.fingerprint),"validatedAt" to r.validatedAt.json(),
            "authenticatedByCredentialId" to uuid(r.credentialId),"outcome" to outcome,"writes" to JArray(r.writes.map { it.json() }))
        val bytes=StrictJson.encode(obj("formatVersion" to num(4),"family" to str("command-receipt"),"key" to r.identity.json(),"payload" to payload),limits.bytes,maxDepth=limits.depth)
        decode(bytes,r.path);return bytes
    }
    fun decode(bytes:ByteArray,path:String):MechanicalReceipt {
        val root=StrictJson.decode(bytes,limits,true).fields("formatVersion","family","key","payload")
        if(root.getValue("formatVersion").long()!=4L)fail(StoreProblem.UNSUPPORTED)
        integrity(root.getValue("family").string()=="command-receipt")
        val identity=MechanicalReceiptIdentity.parse(root.getValue("key"));integrity(identity.path==path)
        val p=root.getValue("payload").fields("storeId","fingerprintVersion","fingerprint","validatedAt","authenticatedByCredentialId","outcome","writes")
        integrity(p.getValue("fingerprintVersion").long()==4L)
        val o=p.getValue("outcome");val kind=(o as? JObject)?.entries?.get("kind")?.string()
        val outcome=when(kind) {
            "rejected" -> { val f=o.fields("kind","domain","code");integrity(f.getValue("domain").string()=="work")
                MechanicalReceiptOutcome.Rejected(WorkProblem.entries.singleOrNull { it.name==f.getValue("code").string() } ?: fail(StoreProblem.INTEGRITY)) }
            "success" -> { val f=o.fields("kind","operation","domainReplayed","result")
                integrity(f.getValue("operation").string()==identity.operation && f.getValue("domainReplayed")==JBoolean(false))
                val r=f.getValue("result").fields("key","generation","digest");val family=MechanicalJournalCatalog.resultFamilies.getValue(identity.operation)
                val key=WorkIdentityCodec().decodeKey(family,StrictJson.encode(r.getValue("key")))
                MechanicalReceiptOutcome.Success(MechanicalReceiptResult(WorkRecordIdentity(family,key),r.getValue("generation").let { if(it===JNull)null else it.long() },digest(r.getValue("digest")))) }
            else -> fail(StoreProblem.INTEGRITY)
        }
        val writes=p.getValue("writes").list().map { w -> val f=w.fields("namespace","family","key","beforeDigest","afterDigest")
            fun hash(name:String)=f.getValue(name).let { if(it===JNull)null else checkDigest(it.string()) }
            WriteDescriptor(RecordKey(f.getValue("namespace").string(),f.getValue("family").string(),f.getValue("key")),hash("beforeDigest"),hash("afterDigest")) }
        return MechanicalReceipt(uuid(p.getValue("storeId")),identity,digest(p.getValue("fingerprint")),GitOid.parse(p.getValue("validatedAt")),uuid(p.getValue("authenticatedByCredentialId")),outcome,writes)
    }
}
