// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

internal enum class StoreProblem { INTEGRITY, UNSUPPORTED, UNAVAILABLE, ADMISSION, RECOVERY_REQUIRED, DENIED }
internal class StoreFault(val problem: StoreProblem): RuntimeException(problem.name)
internal fun fail(p: StoreProblem): Nothing=throw StoreFault(p)
internal fun integrity(ok: Boolean) { if(!ok)fail(StoreProblem.INTEGRITY) }
internal fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun checkDigest(s: String): String { integrity(s.matches(Regex("[0-9a-f]{64}"))); return s }
internal data class GitOid(val algorithm: String,val hex: String) {
    init { integrity(algorithm in setOf("sha1","sha256")); integrity(hex.matches(Regex("[0-9a-f]{${if(algorithm=="sha1")40 else 64}}"))) }
    fun json()=obj("algorithm" to str(algorithm),"hex" to str(hex))
    companion object { fun parse(v: JsonValue): GitOid { val f=v.fields("algorithm","hex"); return GitOid(f.getValue("algorithm").string(),f.getValue("hex").string()) } }
}
internal val coreOperations=setOf("propose_revision","request_approval","resolve_approval","record_assessment","pin_consumer","revoke_access")
internal val authOperations=setOf("replace_grants","revoke_credential","rotate_credential")

/** Canonical admitted input is frozen once; retries never replace targets or credentials. */
internal class StoreCommand private constructor(private val value: JObject,val requestKey: UUID,val projectId: UUID,val operation: String,val fingerprintVersion: Int,val fingerprint: String,val presentedBytes: Int) {
    val payload: JsonValue get()=value.entries.getValue("payload")
    fun bytes()=StrictJson.encode(value,8*1024*1024)
    companion object {
        fun decode(bytes: ByteArray): StoreCommand {
            val v=StrictJson.decode(bytes,JsonLimits(bytes=8*1024*1024))
            val f=v.fields("formatVersion","requestKey","projectId","operation","payload")
            val version=f.getValue("formatVersion").int(); val op=f.getValue("operation").string()
            val canonical=if(version==1) {
                val codec=CommandCodec(); StrictJson.decode(codec.encode(codec.decode(bytes)),JsonLimits(bytes=8*1024*1024)) as JObject
            } else {
                if(version!=2 || op !in authOperations)fail(StoreProblem.UNSUPPORTED)
                if(bytes.size>16384)fail(StoreProblem.ADMISSION)
                StrictJson.decode(bytes,JsonLimits(bytes=16384,stringBytes=1024,depth=16,collection=64,tokens=2048))
                val p=f.getValue("payload")
                when(op) {
                    "replace_grants" -> { val a=p.fields("principalId","expectedGeneration","expectedGrantDigest","capabilities"); uuid(a.getValue("principalId")); a.getValue("expectedGeneration").long(); digest(a.getValue("expectedGrantDigest"))
                        val caps=a.getValue("capabilities").list().map { it.string() }; shape(caps.distinct().size==caps.size && caps.all { it in setOf("READ","PLAN","PROPOSE","APPROVE","MANAGE") }) }
                    "revoke_credential" -> { val a=p.fields("credentialId","expectedGeneration"); uuid(a.getValue("credentialId")); a.getValue("expectedGeneration").long() }
                    "rotate_credential" -> { val a=p.fields("credentialId","expectedGeneration","newCredentialId","verifierVersion","newVerifierDigest"); uuid(a.getValue("credentialId")); a.getValue("expectedGeneration").long(); uuid(a.getValue("newCredentialId")); version(a.getValue("verifierVersion")); digest(a.getValue("newVerifierDigest")) }
                }
                // The only auth set is normalized, ordered payloads are otherwise unchanged.
                val normalized=if(op=="replace_grants") {
                    val a=(p as JObject).entries
                    JObject(a+ ("capabilities" to JArray(a.getValue("capabilities").list().sortedBy { it.string() })))
                } else p
                JObject(f+("payload" to normalized))
            }
            val fields=canonical.entries
            val fp=framedDigest("writhelm.command.v$version") { writeFramed(StrictJson.encode(JObject(fields-"requestKey"),8*1024*1024)) }.hex
            return StoreCommand(canonical,uuid(fields.getValue("requestKey")),uuid(fields.getValue("projectId")),op,version,fp,bytes.size)
        }
    }
}
internal data class CommandIdentity(val principalId: UUID,val projectId: UUID,val operation: String,val requestKey: UUID) {
    init { integrity(operation in coreOperations+authOperations) }
    fun json()=obj("principalId" to uuid(principalId),"projectId" to uuid(projectId),"operation" to str(operation),"requestKey" to uuid(requestKey))
    val path: String get() {
        val h=framedDigest("writhelm.record-key.v1") { writeFramed(encodeUtf8("command-receipt")); writeFramed(StrictJson.encode(json())) }.hex
        return "commands/receipts/${h.take(2)}/$h.json"
    }
    companion object { fun parse(v: JsonValue): CommandIdentity { val f=v.fields("principalId","projectId","operation","requestKey"); return CommandIdentity(uuid(f.getValue("principalId")),uuid(f.getValue("projectId")),f.getValue("operation").string(),uuid(f.getValue("requestKey"))) } }
}
/** Only generated physical keys; this is NOT a raw-path patch interface. */
internal class RecordKey(val namespace: String,val family: String,val key: JsonValue) {
    init {
        integrity(namespace in setOf("core","auth"))
        if(namespace=="core") when(family) {
            "revisions","proposals" -> revisionKey(key)
            "grants" -> grantKey(key)
            "requests","resolutions","assessments","manifests" -> uuid(key)
            "heads","generations" -> subject(key)
            "approvals","continuity","blocked" -> reference(key)
            "bodies" -> digest(key)
            else -> fail(StoreProblem.UNSUPPORTED)
        } else when(family) {
            "principals","projects","credentials" -> uuid(key)
            "grant-guards" -> grantKey(key)
            else -> fail(StoreProblem.UNSUPPORTED)
        }
    }
    val path: String get() {
        if(namespace=="core" && family=="bodies")return bodyPath(digest(key))
        return CoreRecordCodec.path(family,key,8*1024*1024).replaceFirst("core/","$namespace/")
    }
    val mutable: Boolean get()=if(namespace=="core")family in setOf("grants","heads","generations") else family in setOf("credentials","grant-guards")
    val removable: Boolean get()=namespace=="core" && family=="grants"
}
internal fun grantKey(v: JsonValue) { val f=v.fields("principalId","projectId"); uuid(f.getValue("principalId")); uuid(f.getValue("projectId")) }
internal class WriteDescriptor(val record: RecordKey,val beforeDigest: String?,val afterDigest: String?) {
    init { beforeDigest?.let(::checkDigest); afterDigest?.let(::checkDigest); integrity(beforeDigest!=afterDigest) }
    val path get()=record.path
    fun json()=obj("namespace" to str(record.namespace),"family" to str(record.family),"key" to record.key,"beforeDigest" to (beforeDigest?.let(::str) ?: JNull),"afterDigest" to (afterDigest?.let(::str) ?: JNull))
}
internal sealed interface LogicalOutcome {
    fun json(): JsonValue
    class Success(val operation: String,val result: JsonValue,val domainReplayed: Boolean): LogicalOutcome {
        init {
            integrity(operation in coreOperations+authOperations)
            when(operation) {
                "propose_revision" -> revisionKey(result)
                "rotate_credential" -> { val f=result.fields("credentialId","replacedCredentialId"); uuid(f.getValue("credentialId")); uuid(f.getValue("replacedCredentialId")) }
                else -> uuid(result)
            }
            integrity(operation !in authOperations || !domainReplayed)
        }
        override fun json()=obj("kind" to str("success"),"operation" to str(operation),"result" to result,"domainReplayed" to JBoolean(domainReplayed))
    }
    class Rejected(val domain: String,val code: String): LogicalOutcome {
        init { integrity(if(domain=="auth")code in authRejections else domain=="core" && code in coreRejections) }
        override fun json()=obj("kind" to str("rejected"),"domain" to str(domain),"code" to str(code))
    }
}
internal val authRejections=setOf("AUTH_TARGET_NOT_FOUND","AUTH_STALE_CREDENTIAL","AUTH_STALE_GRANT","AUTH_ALREADY_REVOKED","AUTH_ALREADY_REPLACED","AUTH_DUPLICATE_CREDENTIAL","AUTH_OWNER_LOCKOUT","AUTH_FIXED_OWNER_POLICY","AUTH_GENERATION_EXHAUSTED")
internal val coreRejections=setOf("NOT_FOUND","DUPLICATE_ID","INVALID_REFERENCE","FOREIGN_REFERENCE","MISSING_CONTEXT","STALE_SUBJECT","DIGEST_MISMATCH","ALREADY_RESOLVED","UNKNOWN_COMMENT_ITEM","INCOMPLETE_PACKET","UNAPPROVED_INPUT","BLOCKED_INPUT","INVALID_CONTINUITY","CONTEXT_CHANGED","INCOMPLETE_ASSESSMENT","CONFLICTING_SELECTION","UNSUPPORTED_CONTEXT_MIX","LAST_AUTHORITY","GENERATION_EXHAUSTED")
internal class Receipt(val storeId: UUID,val identity: CommandIdentity,val fingerprintVersion: Int,val fingerprint: String,val validatedAt: GitOid,val authenticatedByCredentialId: UUID,val outcome: LogicalOutcome,writes: List<WriteDescriptor>) {
    val writes: List<WriteDescriptor> = Collections.unmodifiableList(ArrayList(writes))
    val path get()=identity.path
    init {
        checkDigest(fingerprint); integrity(fingerprintVersion==if(identity.operation in coreOperations)1 else 2)
        integrity(writes.size<=4096 && writes.map { it.path }==writes.map { it.path }.distinct().sorted())
        if(outcome is LogicalOutcome.Success)integrity(outcome.operation==identity.operation)
        if(outcome is LogicalOutcome.Success && identity.operation in authOperations)integrity(writes.any { it.record.namespace=="auth" })
        if(outcome is LogicalOutcome.Rejected || outcome is LogicalOutcome.Success && outcome.domainReplayed)integrity(writes.isEmpty())
        writes.forEach { integrity(it.afterDigest!=null || it.record.removable); integrity(it.beforeDigest==null || it.record.mutable) }
        if(outcome is LogicalOutcome.Success && !outcome.domainReplayed)validateEffectShape(outcome)
    }
    private fun validateEffectShape(o: LogicalOutcome.Success) {
        fun key(namespace: String,family: String,value: JsonValue)=RecordKey(namespace,family,value)
        fun required(k: RecordKey,new: Boolean=false,delete: Boolean=false) {
            val w=writes.singleOrNull { it.path==k.path } ?: fail(StoreProblem.INTEGRITY)
            integrity((w.beforeDigest==null)==new && (w.afterDigest==null)==delete)
        }
        fun families(vararg allowed: Pair<String,String>) { integrity(writes.all { it.record.namespace to it.record.family in allowed }) }
        when(o.operation) {
            "propose_revision" -> {
                required(key("core","revisions",o.result),true); required(key("core","proposals",o.result),true)
                families("core" to "revisions","core" to "proposals","core" to "bodies"); integrity(writes.size in 2..3)
            }
            "request_approval","pin_consumer" -> {
                required(key("core",if(o.operation=="request_approval")"requests" else "manifests",o.result),true); integrity(writes.size==1)
            }
            "resolve_approval" -> { required(key("core","resolutions",o.result),true); families("core" to "resolutions","core" to "approvals","core" to "heads","core" to "generations") }
            "record_assessment" -> { required(key("core","assessments",o.result),true); families("core" to "assessments","core" to "continuity","core" to "blocked") }
            "revoke_access","replace_grants" -> {
                val grant=obj("principalId" to o.result,"projectId" to uuid(identity.projectId))
                required(key("auth","grant-guards",grant)); families("core" to "grants","auth" to "grant-guards")
                integrity(writes.all { it.path in setOf(key("auth","grant-guards",grant).path,key("core","grants",grant).path) })
                if(o.operation=="revoke_access") { required(key("core","grants",grant),delete=true); integrity(writes.size==2) }
                else integrity(writes.size in 1..2)
            }
            "revoke_credential" -> { required(key("auth","credentials",o.result)); integrity(writes.size==1) }
            "rotate_credential" -> {
                val r=o.result.fields("credentialId","replacedCredentialId")
                required(key("auth","credentials",r.getValue("credentialId")),true)
                required(key("auth","credentials",r.getValue("replacedCredentialId"))); integrity(writes.size==2)
            }
        }
    }
}
internal class StoreFile(val path: String,bytes: ByteArray) {
    init { if(bytes.size>8*1024*1024)fail(StoreProblem.UNAVAILABLE) }
    private val content=bytes.copyOf()
    fun bytes()=content.copyOf()
}
internal class RecordChange(val key: RecordKey,bytes: ByteArray?) {
    init { if(bytes!=null && bytes.size>8*1024*1024)fail(StoreProblem.UNAVAILABLE) }
    private val content=bytes?.copyOf()
    fun bytes()=content?.copyOf()
}
internal class Evaluation(val outcome: LogicalOutcome,changes: List<RecordChange>) {
    init { if(changes.size>4096)fail(StoreProblem.ADMISSION) }
    val changes: List<RecordChange> = Collections.unmodifiableList(ArrayList(changes))
}
internal data class AuthenticatedActor(val principalId: UUID,val credentialId: UUID)
internal fun interface CommandAccess { fun authorize(snapshot: StoreSnapshot,command: StoreCommand): AuthenticatedActor? }
internal fun interface ReadAccess { fun authorize(snapshot: StoreSnapshot,projectId: UUID): Boolean }
internal fun interface CommandEvaluator { fun evaluate(snapshot: StoreSnapshot,command: StoreCommand,actor: AuthenticatedActor): Evaluation }
/** Injection seam for bounded tests/supervisor integration, never a transport/client parameter. */
internal fun interface PublicationDriver {
    fun compareAndSet(git: NativeGit,parent: GitOid,candidate: GitOid): GitReply
    companion object { val Native=PublicationDriver { git,h,c -> git.update(h,c) } }
}
/** Mandatory validators are trusted server integration code, never supplied by a client. */
internal class NamespaceValidator(val header: JsonValue,val validate: (StoreSnapshot)->Unit)
internal class StorePorts(namespaces: Map<String,NamespaceValidator>,
    val validateTransition: (StoreSnapshot,StoreCommand,AuthenticatedActor,Evaluation)->Unit,
    val validateHistoricalReceipt: (StoreSnapshot,Receipt)->Unit,
    val fileRead: (String)->Unit={ }) {
    val namespaces: Map<String,NamespaceValidator> = Collections.unmodifiableMap(LinkedHashMap(namespaces))
    init { integrity(namespaces.keys.all { it in setOf("auth","policy") }) }
}
internal data class StoreBounds(val attempts: Int=3,val commandMillis: Long=45000,val treeBytes: Int=16*1024*1024,val files: Int=32768,val historyCommits: Int=4096,val auditBytes: Long=128L*1024*1024,val auditMillis: Long=45000) {
    init { require(attempts in 1..3 && commandMillis in 1..45000 && treeBytes in 1..16*1024*1024 && files in 1..32768 && historyCommits in 1..4096 && auditBytes in 1..128L*1024*1024 && auditMillis in 1..45000) }
}
internal sealed interface CommandResult {
    class Settled(val receipt: Receipt,val commandReplayed: Boolean,val publication: GitOid?): CommandResult
    data object IdentityConflict: CommandResult
    data object Denied: CommandResult
    class Refused(val problem: StoreProblem): CommandResult
    data object Unknown: CommandResult
}
