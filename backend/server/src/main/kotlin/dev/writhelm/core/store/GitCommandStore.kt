// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.subject
import java.io.ByteArrayOutputStream
import java.nio.file.*
import java.util.Collections
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class StoreSnapshot internal constructor(val oid: GitOid,entries: Map<String,GitOid>,private val git: NativeGit,private val meter: ((Int)->Unit)?=null,private val observe: (String)->Unit={ }) {
    internal val entries: Map<String,GitOid> = Collections.unmodifiableMap(LinkedHashMap(entries))
    fun file(path: String): ByteArray?=entries[path]?.let { observe(path); git.readBlob(it).also { b -> meter?.invoke(b.size) } }
    internal fun withMeter(meter: (Int)->Unit)=StoreSnapshot(oid,entries,git,meter,observe)
}

/** Internal, one-root store. No listener, credential verifier, raw-patch client or lifecycle API.
 * Every command callback receives the same frozen command. Only publication acknowledgement proves
 * success; an orphan object never participates in receipt lookup.
 */
internal class GitCommandStore private constructor(private val owner: RuntimeOwner,private val git: NativeGit,
    val storeId: UUID,private val ports: StorePorts,private val bounds: StoreBounds,private val publication: PublicationDriver): AutoCloseable {
    /** Dispensable proof bound to this exact validated immutable H, never a canonical side counter.
     * Created only by genesis, successful full audit, or confirmed single-parent publication. */
    private class ValidatedHead(val snapshot: StoreSnapshot,val publishedCommitCount: Int) {
        init { integrity(publishedCommitCount>0) }
    }
    private var validated: ValidatedHead?=null
    private var closed=false
    private val commandHeader=obj("formatVersion" to num(2))
    private val expectedNamespaces: Map<String,JsonValue> = mapOf("core" to CoreRecordCodec.header(),"commands" to commandHeader)+ports.namespaces.mapValues { it.value.header }
    init {
        val policyHeader=ports.namespaces["policy"]?.header ?: fail(StoreProblem.UNSUPPORTED)
        if(!StrictJson.encode(policyHeader).contentEquals(StrictJson.encode(obj("formatVersion" to num(1)))))fail(StoreProblem.UNSUPPORTED)
    }
    private fun header(): JsonValue=obj("formatVersion" to num(1),"storeId" to uuid(storeId),"gitObjectFormat" to str("sha1"),"namespaces" to JObject(expectedNamespaces))

    @Synchronized fun <T> read(projectId: UUID,access: ReadAccess,resolve: (StoreSnapshot)->T): T {
        usable(); val snapshot=capture().snapshot
        if(!access.authorize(snapshot,projectId))fail(StoreProblem.DENIED)
        return resolve(snapshot)
    }
    @Synchronized fun execute(command: StoreCommand,access: CommandAccess,evaluator: CommandEvaluator): CommandResult {
        var publicationStarted=false
        git.deadlineNanos=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(bounds.commandMillis)
        try {
            usable(); var originalIdentity: CommandIdentity?=null; var originalCredential: UUID?=null
            repeat(bounds.attempts) { attempt ->
                val base=capture(); val h=base.snapshot
                // The callback closes over the SAME presented credential. It must authenticate and
                // authorize in this H, including on every CAS rebuild and before any receipt lookup.
                val actor=access.authorize(h,command) ?: return CommandResult.Denied
                originalCredential?.let { integrity(it==actor.credentialId) }; originalCredential=actor.credentialId
                val policy=AdmissionPolicy.decode(h.file("policy/admission.json") ?: fail(StoreProblem.INTEGRITY))
                policy.command(command)
                val identity=CommandIdentity(actor.principalId,command.projectId,command.operation,command.requestKey)
                originalIdentity?.let { integrity(it==identity) }; originalIdentity=identity
                h.file(identity.path)?.let { bytes ->
                    val receipt=JournalCodec.decode(bytes,identity.path)
                    integrity(receipt.identity==identity && receipt.storeId==storeId)
                    return if(receipt.fingerprintVersion==command.fingerprintVersion && receipt.fingerprint==command.fingerprint)
                        CommandResult.Settled(receipt,true,null) else CommandResult.IdentityConflict
                }
                // Authorization and original receipt recovery precede this operational growth cap.
                // Every new outcome needs one commit, including rejection/domain-replay receipts.
                // No evaluator, candidate, CAS or durable rejection is allocated at capacity.
                if(base.publishedCommitCount>=bounds.historyCommits)return CommandResult.Refused(StoreProblem.UNAVAILABLE)
                val evaluation=evaluator.evaluate(h,command,actor)
                val changes=evaluation.changes
                if(changes.size>policy.writeDescriptors)fail(StoreProblem.ADMISSION)
                integrity(changes.map { it.key.path }.distinct().size==changes.size)
                if(evaluation.outcome is LogicalOutcome.Rejected || evaluation.outcome is LogicalOutcome.Success && evaluation.outcome.domainReplayed)integrity(changes.isEmpty())
                if(evaluation.outcome is LogicalOutcome.Success) {
                    integrity(evaluation.outcome.operation==command.operation)
                    validateCommandResult(command,evaluation.outcome)
                }
                val permitted=allowedPaths(h,command)
                val descriptors=changes.sortedBy { it.key.path }.map { change ->
                    integrity(change.key.path in permitted)
                    val before=h.file(change.key.path); val after=change.bytes()
                    after?.let { policy.file(change.key.path,it) }
                    validateRecord(change.key,before,after)
                    WriteDescriptor(change.key,before?.let(::sha),after?.let(::sha))
                }
                changes.forEach { change ->
                    val bytes=change.bytes()
                    if(bytes!=null && change.key.namespace=="core" && change.key.family!="bodies") {
                        val body=if(change.key.family=="revisions") {
                            val envelope=StrictJson.decode(bytes).fields("formatVersion","family","key","payload")
                            val d=digest((envelope.getValue("payload") as? JObject)?.entries?.get("bodyDigest") ?: fail(StoreProblem.INTEGRITY))
                            val path=bodyPath(d)
                            val raw=changes.firstOrNull { it.key.path==path }?.bytes() ?: h.file(path) ?: fail(StoreProblem.INTEGRITY)
                            Utf8Content.fromBytes(raw)
                        } else null
                        CoreSnapshotCodec(admission=JsonLimits(bytes=policy.coreRecordBytes),bodyBytes=policy.bodyBytes).admitRecord(CoreEntry(change.key.path,bytes),body)
                    }
                }
                validateRequiredEffects(h,command,evaluation)
                // Required domain-specific affected-reference validation is never replaced by shape
                // checks or a full-state import. Port must enforce reducer outcome and graph laws.
                ports.validateTransition(h,command,actor,evaluation)
                val receipt=Receipt(storeId,identity,command.fingerprintVersion,command.fingerprint,h.oid,actor.credentialId,evaluation.outcome,descriptors)
                ports.validateHistoricalReceipt(h,receipt)
                val receiptBytes=JournalCodec.encode(receipt,policy.receiptBytes)
                val entries=LinkedHashMap(h.entries)
                changes.forEach { change ->
                    val after=change.bytes()
                    if(after==null)entries.remove(change.key.path) else entries[change.key.path]=git.blob(after)
                }
                integrity(!entries.containsKey(receipt.path)); entries[receipt.path]=git.blob(receiptBytes)
                if(entries.size>bounds.files)fail(StoreProblem.UNAVAILABLE)
                val tree=writeTree(entries); val c=git.commit(tree,h.oid)
                // Candidate's actual diff is independently checked against the immutable descriptors.
                val candidate=snapshot(c); validateBoundary(h,candidate,receipt)
                owner.beginPublication(); publicationStarted=true
                val published=publication.compareAndSet(git,h.oid,c)
                if(published.exit==0 && published.stderr.isEmpty()) {
                    integrity(git.head()==c)
                    owner.finishPublication(); publicationStarted=false
                    validated=ValidatedHead(candidate,base.publishedCommitCount+1)
                    return CommandResult.Settled(receipt,false,c)
                }
                // update-ref child has ended and IO has joined. Expected-old conflict alone is not
                // canonical failure; clear barrier only after termination, recapture and validate.
                val head=git.head()
                if(head==h.oid) {
                    owner.finishPublication(); publicationStarted=false
                    return CommandResult.Refused(StoreProblem.UNAVAILABLE)
                }
                if(attempt==bounds.attempts-1) {
                    // Reconciliation is mandatory even when no CAS rebuild remains. Keep the
                    // joined-child barrier until the published line, current access and receipt
                    // are resolved. Any failure/deadline here is unknown, never definite absence.
                    // capture/audit retain the ORIGINAL aggregate command deadline.
                    validated=null
                    val current=capture().snapshot
                    val currentActor=access.authorize(current,command)
                    if(currentActor==null) {
                        owner.finishPublication(); publicationStarted=false
                        return CommandResult.Denied
                    }
                    integrity(currentActor.principalId==actor.principalId && currentActor.credentialId==actor.credentialId)
                    val recovered=current.file(identity.path)?.let { bytes ->
                        val original=JournalCodec.decode(bytes,identity.path)
                        integrity(original.identity==identity && original.storeId==storeId)
                        if(original.fingerprintVersion==command.fingerprintVersion && original.fingerprint==command.fingerprint)
                            CommandResult.Settled(original,true,null) else CommandResult.IdentityConflict
                    }
                    owner.finishPublication(); publicationStarted=false
                    return recovered ?: CommandResult.Refused(StoreProblem.UNAVAILABLE)
                }
                owner.finishPublication(); publicationStarted=false
                validated=null
                // Next iteration reauthenticates; unrecognized movement gets bounded full audit.
            }
            return CommandResult.Refused(StoreProblem.UNAVAILABLE)
        } catch(e: StoreFault) {
            if(publicationStarted || e.problem==StoreProblem.RECOVERY_REQUIRED) { owner.unknown(); return CommandResult.Unknown }
            return CommandResult.Refused(e.problem)
        } catch(e: CodecFault) {
            if(publicationStarted) { owner.unknown(); return CommandResult.Unknown }
            return CommandResult.Refused(if(e.problem in setOf(CodecProblem.ADMISSION_LIMIT))StoreProblem.ADMISSION else if(e.problem==CodecProblem.RESOURCE_UNAVAILABLE)StoreProblem.UNAVAILABLE else StoreProblem.INTEGRITY)
        } catch(_: Exception) {
            if(publicationStarted) { owner.unknown(); return CommandResult.Unknown }
            return CommandResult.Refused(StoreProblem.UNAVAILABLE)
        } finally { git.deadlineNanos=Long.MAX_VALUE }
    }
    private fun usable() { if(closed)fail(StoreProblem.RECOVERY_REQUIRED); owner.requireUsable() }
    private fun capture(): ValidatedHead {
        val head=git.head(); validated?.let { if(it.snapshot.oid==head)return it }
        return audit(head).also { validated=it }
    }
    private fun snapshot(oid: GitOid): StoreSnapshot {
        val bytes=git.call(listOf("ls-tree","-rz","--full-tree",oid.hex),outputLimit=bounds.treeBytes).checked()
        val entries=LinkedHashMap<String,GitOid>(); var offset=0
        while(offset<bytes.size) {
            val end=(offset until bytes.size).firstOrNull { bytes[it]==0.toByte() } ?: fail(StoreProblem.INTEGRITY)
            if(entries.size>=bounds.files)fail(StoreProblem.UNAVAILABLE)
            // Physical paths are generated ASCII; reject modes, symlinks, submodules and alternates.
            val line=bytes.copyOfRange(offset,end)
            integrity(line.all { it.toInt() in 1..127 })
            val match=Regex("100644 blob ([0-9a-f]{40})\\t([a-z0-9/.-]+)").matchEntire(line.toString(Charsets.US_ASCII)) ?: fail(StoreProblem.INTEGRITY)
            val path=match.groupValues[2]; validatePhysicalPath(path)
            integrity(entries.put(path,GitOid("sha1",match.groupValues[1]))==null)
            offset=end+1
        }
        return StoreSnapshot(oid,entries,git,observe=ports.fileRead)
    }
    private fun validatePhysicalPath(path: String) {
        if(path=="store.json")return
        val namespace=path.substringBefore('/')
        if(namespace !in expectedNamespaces)fail(StoreProblem.UNSUPPORTED)
        if(path=="$namespace/format.json")return
        when(namespace) {
            "core" -> CoreEntryKey.parse(path)
            "auth" -> integrity(Regex("auth/records/(principals|projects|credentials|grant-guards)/([0-9a-f]{2})/([0-9a-f]{64})\\.json").matchEntire(path)?.let { it.groupValues[2]==it.groupValues[3].take(2) }==true)
            "commands" -> integrity(Regex("commands/receipts/([0-9a-f]{2})/([0-9a-f]{64})\\.json").matchEntire(path)?.let { it.groupValues[1]==it.groupValues[2].take(2) }==true)
            "policy" -> integrity(path=="policy/admission.json")
        }
    }
    private fun validateHeaders(s: StoreSnapshot) {
        val bytes=s.file("store.json") ?: fail(StoreProblem.INTEGRITY)
        val h=StrictJson.decode(bytes,JsonLimits(bytes=16384)).fields("formatVersion","storeId","gitObjectFormat","namespaces")
        if(h.getValue("formatVersion").int()!=1)fail(StoreProblem.UNSUPPORTED)
        integrity(uuid(h.getValue("storeId"))==storeId && h.getValue("gitObjectFormat").string()=="sha1")
        val registry=(h.getValue("namespaces") as? JObject)?.entries ?: fail(StoreProblem.INTEGRITY)
        if(registry.keys!=expectedNamespaces.keys)fail(StoreProblem.UNSUPPORTED)
        expectedNamespaces.forEach { (name,expected) ->
            if(!StrictJson.encode(registry.getValue(name)).contentEquals(StrictJson.encode(expected)))fail(StoreProblem.UNSUPPORTED)
            val actual=s.file("$name/format.json") ?: fail(StoreProblem.INTEGRITY)
            if(!StrictJson.encode(StrictJson.decode(actual,JsonLimits(bytes=16384))).contentEquals(StrictJson.encode(expected)))fail(StoreProblem.UNSUPPORTED)
        }
    }
    private fun validateSnapshot(s: StoreSnapshot) {
        validateHeaders(s)
        AdmissionPolicy.decode(s.file("policy/admission.json") ?: fail(StoreProblem.INTEGRITY))
        val paths=s.entries.keys.filter { it.startsWith("core/") }
        val ordered=listOf("core/format.json")+paths.filter { it!="core/format.json" }.sorted()
        CoreSnapshotCodec().restore(ordered.asSequence().map { CoreEntry(it,s.file(it) ?: fail(StoreProblem.INTEGRITY)) })
        ports.namespaces.values.forEach { it.validate(s) }
    }
    private fun parents(oid: GitOid): List<GitOid> {
        val commit=git.call(listOf("cat-file","commit",oid.hex),outputLimit=16384).checked().toString(Charsets.US_ASCII)
        val headers=commit.substringBefore("\n\n").lines()
        integrity(headers.firstOrNull()?.matches(Regex("tree [0-9a-f]{40}"))==true)
        val parents=headers.filter { it.startsWith("parent ") }.map { GitOid("sha1",it.removePrefix("parent ")) }
        integrity(parents.size<=1)
        return parents
    }
    private fun audit(head: GitOid): ValidatedHead {
        val priorDeadline=git.deadlineNanos
        git.deadlineNanos=minOf(priorDeadline,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(bounds.auditMillis))
        try { return auditWithinBudget(head) } finally { git.deadlineNanos=priorDeadline }
    }
    private fun auditWithinBudget(head: GitOid): ValidatedHead {
        // Startup/reconciliation only: native object/tree/commit hashes are checked separately from
        // core domain digests. Never put a whole-object audit on the recognized normal-write path.
        val objects=git.call(listOf("fsck","--strict","--no-progress","--no-reflogs","--no-dangling",head.hex))
        if(objects.exit!=0 || objects.stderr.isNotEmpty() || objects.output.isNotEmpty())fail(StoreProblem.INTEGRITY)
        var consumed=0L
        fun consume(n: Int) { if(n.toLong()>bounds.auditBytes-consumed)fail(StoreProblem.UNAVAILABLE); consumed+=n }
        val history=git.call(listOf("rev-list","--first-parent","--reverse","--max-count=${bounds.historyCommits+1}",head.hex),outputLimit=bounds.treeBytes).checked().toString(Charsets.US_ASCII).lines().filter { it.isNotEmpty() }
        if(history.isEmpty())fail(StoreProblem.INTEGRITY)
        if(history.size>bounds.historyCommits)fail(StoreProblem.UNAVAILABLE)
        var previous: StoreSnapshot?=null
        var current: StoreSnapshot?=null
        for(hex in history) {
            val oid=GitOid("sha1",hex); val s=snapshot(oid).withMeter(::consume); val p=parents(oid)
            validateHeaders(s)
            if(previous==null) {
                integrity(p.isEmpty() && s.entries.keys.none { it.startsWith("commands/receipts/") })
                validateSnapshot(s)
            } else {
                integrity(p==listOf(previous.oid))
                val added=s.entries.keys.filter { it.startsWith("commands/receipts/") && it !in previous.entries }
                integrity(added.size==1)
                val receipt=JournalCodec.decode(s.file(added.single())!!,added.single())
                validateBoundary(previous,s,receipt)
                ports.validateHistoricalReceipt(previous,receipt)
            }
            previous=s; current=s
        }
        val result=current ?: fail(StoreProblem.INTEGRITY)
        integrity(result.oid==head)
        validateSnapshot(result)
        return ValidatedHead(StoreSnapshot(result.oid,result.entries,git,observe=ports.fileRead),history.size)
    }
    private fun validateBoundary(h: StoreSnapshot,c: StoreSnapshot,receipt: Receipt) {
        integrity(receipt.storeId==storeId && receipt.validatedAt==h.oid && parents(c.oid)==listOf(h.oid))
        integrity(receipt.path !in h.entries && receipt.path in c.entries)
        val actual=(h.entries.keys+c.entries.keys).filter { h.entries[it]!=c.entries[it] }.toSet()
        integrity(actual==receipt.writes.map { it.path }.toSet()+receipt.path)
        receipt.writes.forEach { w ->
            val before=h.file(w.path); val after=c.file(w.path)
            integrity(before?.let(::sha)==w.beforeDigest && after?.let(::sha)==w.afterDigest)
            validateRecord(w.record,before,after,false)
        }
        validateResultReference(c,receipt)
    }
    private fun validateRecord(key: RecordKey,before: ByteArray?,after: ByteArray?,admission: Boolean=true) {
        integrity(before!=null || after!=null)
        if(before!=null)integrity(key.mutable)
        if(after==null) { integrity(key.removable); return }
        val maximum=if(key.namespace=="auth")16384 else if(key.family=="bodies")1024*1024 else 4*1024*1024
        if(admission && after.size>maximum)fail(StoreProblem.ADMISSION)
        if(key.namespace=="core" && key.family=="bodies") {
            integrity(Utf8Content.fromBytes(after).digest==digest(key.key)); return
        }
        fun decode(bytes: ByteArray): Map<String,JsonValue> {
            val limits=if(!admission)JsonLimits(bytes=8*1024*1024,stringBytes=4*1024*1024,depth=128,collection=16384,tokens=500000)
                else if(key.namespace=="auth")JsonLimits(bytes=maximum,stringBytes=1024,depth=16,collection=64,tokens=2048) else JsonLimits(bytes=maximum)
            val f=StrictJson.decode(bytes,limits).fields("formatVersion","family","key","payload")
            version(f.getValue("formatVersion")); integrity(f.getValue("family").string()==key.family)
            integrity(StrictJson.encode(f.getValue("key")).contentEquals(StrictJson.encode(key.key)))
            return f
        }
        val f=decode(after)
        if(key.namespace=="core")CoreRecordCodec.decode(JObject(f),key.path)
        if(before!=null && key.namespace=="auth") {
            val a=f.getValue("payload"); val b=decode(before).getValue("payload")
            if(key.family=="credentials") {
                val names=arrayOf("storeId","projectId","principalId","verifierVersion","verifierDigest","status","generation","replacedBy")
                val old=b.fields(*names); val next=a.fields(*names)
                names.filter { it !in setOf("status","generation","replacedBy") }.forEach { name -> integrity(StrictJson.encode(old.getValue(name)).contentEquals(StrictJson.encode(next.getValue(name)))) }
                val generation=old.getValue("generation").long(); integrity(generation<Long.MAX_VALUE && next.getValue("generation").long()==generation+1)
                integrity(next.getValue("status").string()=="revoked")
                if(old.getValue("status").string()=="revoked")integrity(old.getValue("replacedBy")===JNull && next.getValue("replacedBy")!==JNull)
                if(old.getValue("replacedBy")!==JNull)fail(StoreProblem.INTEGRITY)
            } else {
                val old=b.fields("generation").getValue("generation").long(); val next=a.fields("generation").getValue("generation").long()
                integrity(old<Long.MAX_VALUE && next==old+1)
            }
        }
    }
    private fun validateResultReference(c: StoreSnapshot,r: Receipt) {
        val outcome=r.outcome as? LogicalOutcome.Success ?: return
        val family=when(outcome.operation) {
            "propose_revision" -> "revisions"
            "request_approval" -> "requests"
            "resolve_approval" -> "resolutions"
            "record_assessment" -> "assessments"
            "pin_consumer" -> "manifests"
            "rotate_credential","revoke_credential" -> "credentials"
            else -> return // Current mutable grant removals are bound by exact diff and trusted reducer.
        }
        val key=if(outcome.operation=="rotate_credential")outcome.result.fields("credentialId","replacedCredentialId").getValue("credentialId") else outcome.result
        val path=RecordKey(if(family=="credentials")"auth" else "core",family,key).path
        integrity(path in c.entries)
        if(outcome.operation=="rotate_credential")integrity(RecordKey("auth","credentials",outcome.result.fields("credentialId","replacedCredentialId").getValue("replacedCredentialId")).path in c.entries)
    }
    private fun validateCommandResult(c: StoreCommand,o: LogicalOutcome.Success) {
        val payload=(c.payload as JObject).entries
        val expected=when(c.operation) {
            "propose_revision" -> payload.getValue("revisionKey")
            "request_approval","resolve_approval" -> payload.getValue("requestId")
            "record_assessment" -> payload.getValue("assessmentId")
            "pin_consumer" -> payload.getValue("manifestId")
            "revoke_access","replace_grants" -> payload.getValue("principalId")
            "revoke_credential" -> payload.getValue("credentialId")
            else -> obj("credentialId" to payload.getValue("newCredentialId"),"replacedCredentialId" to payload.getValue("credentialId"))
        }
        integrity(StrictJson.encode(expected).contentEquals(StrictJson.encode(o.result)))
    }
    private fun validateRequiredEffects(h: StoreSnapshot,c: StoreCommand,e: Evaluation) {
        val outcome=e.outcome as? LogicalOutcome.Success ?: return
        if(outcome.domainReplayed)return
        val p=(c.payload as JObject).entries
        fun requireChange(namespace: String,family: String,key: JsonValue,new: Boolean=false,delete: Boolean=false) {
            val path=RecordKey(namespace,family,key).path
            val change=e.changes.singleOrNull { it.key.path==path } ?: fail(StoreProblem.INTEGRITY)
            integrity((change.bytes()==null)==delete)
            integrity((path !in h.entries)==new)
        }
        when(c.operation) {
            "propose_revision" -> { requireChange("core","revisions",p.getValue("revisionKey"),true); requireChange("core","proposals",p.getValue("revisionKey"),true) }
            "request_approval" -> requireChange("core","requests",p.getValue("requestId"),true)
            "resolve_approval" -> requireChange("core","resolutions",p.getValue("requestId"),true)
            "record_assessment" -> requireChange("core","assessments",p.getValue("assessmentId"),true)
            "pin_consumer" -> requireChange("core","manifests",p.getValue("manifestId"),true)
            "revoke_access","replace_grants" -> {
                val key=obj("principalId" to p.getValue("principalId"),"projectId" to uuid(c.projectId))
                requireChange("auth","grant-guards",key)
                if(c.operation=="revoke_access")requireChange("core","grants",key,delete=true)
            }
            "revoke_credential" -> requireChange("auth","credentials",p.getValue("credentialId"))
            "rotate_credential" -> { requireChange("auth","credentials",p.getValue("credentialId")); requireChange("auth","credentials",p.getValue("newCredentialId"),true) }
        }
    }
    private fun allowedPaths(h: StoreSnapshot,c: StoreCommand): Set<String> {
        val paths=HashSet<String>(); val p=(c.payload as JObject).entries
        fun add(namespace: String,family: String,key: JsonValue) { paths.add(RecordKey(namespace,family,key).path) }
        fun core(family: String,key: JsonValue)=add("core",family,key)
        fun targetGrant()=obj("principalId" to p.getValue("principalId"),"projectId" to uuid(c.projectId))
        when(c.operation) {
            "propose_revision" -> { val k=p.getValue("revisionKey"); core("revisions",k); core("proposals",k); core("bodies",digest(Utf8Content.fromText(p.getValue("body").string()).digest)) }
            "request_approval" -> core("requests",p.getValue("requestId"))
            "resolve_approval" -> {
                val k=p.getValue("requestId"); core("resolutions",k)
                val b=h.file(RecordKey("core","requests",k).path)
                if(b!=null) {
                    val e=StrictJson.decode(b).fields("formatVersion","family","key","payload")
                    val packet=(e.getValue("payload") as JObject).entries.getValue("packet")
                    packet(packet).items.forEach { item -> core("approvals",reference(item.target)); core("heads",subject(item.target.subject())); core("generations",subject(item.target.subject())) }
                }
            }
            "record_assessment" -> { core("assessments",p.getValue("assessmentId")); p.getValue("links").list().forEach { core("continuity",it.fields("source","target").getValue("target")) }; p.getValue("blocked").list().forEach { core("blocked",it) } }
            "pin_consumer" -> core("manifests",p.getValue("manifestId"))
            "revoke_access","replace_grants" -> { core("grants",targetGrant()); add("auth","grant-guards",targetGrant()) }
            "revoke_credential","rotate_credential" -> { add("auth","credentials",p.getValue("credentialId")); if(c.operation=="rotate_credential")add("auth","credentials",p.getValue("newCredentialId")) }
        }
        return paths
    }
    private fun writeTree(entries: Map<String,GitOid>): GitOid {
        if(entries.size>bounds.files)fail(StoreProblem.UNAVAILABLE)
        class Node { val directories=sortedMapOf<String,Node>(); val files=sortedMapOf<String,GitOid>() }
        val root=Node()
        entries.forEach { (path,oid) ->
            validatePhysicalPath(path); val pieces=path.split('/'); var node=root
            pieces.dropLast(1).forEach { part -> integrity(part !in node.files); node=node.directories.getOrPut(part) { Node() } }
            integrity(pieces.last() !in node.directories); node.files[pieces.last()]=oid
        }
        fun tree(node: Node): GitOid {
            val items=ArrayList<Pair<String,String>>()
            node.files.forEach { (name,oid)-> items.add(name to "100644 blob ${oid.hex}\t$name\u0000") }
            node.directories.forEach { (name,sub)-> items.add("$name/" to "040000 tree ${tree(sub).hex}\t$name\u0000") }
            val output=ByteArrayOutputStream()
            items.sortedBy { it.first }.forEach { (_,line) -> val bytes=line.toByteArray(Charsets.US_ASCII); if(bytes.size>8*1024*1024-output.size())fail(StoreProblem.UNAVAILABLE); output.write(bytes) }
            return git.oid(git.call(listOf("mktree","-z"),output.toByteArray()).checked())
        }
        return tree(root)
    }
    @Synchronized override fun close() { if(closed)return; closed=true; owner.close() }
    companion object {
        fun initialize(directory: Path,storeId: UUID,files: List<StoreFile>,ports: StorePorts,bounds: StoreBounds=StoreBounds(),processBounds: ProcessBounds=ProcessBounds(),publication: PublicationDriver=PublicationDriver.Native,admission: AdmissionPolicy=AdmissionPolicy()): GitCommandStore {
            val owner=RuntimeOwner.acquire(directory)
            try {
                // Only explicit empty designated directories, apart from our own owner artifacts.
                Files.list(directory).use { stream -> integrity(stream.allMatch { it.fileName.toString() in setOf("owner.lock","runtime-active") }) }
                integrity(ports.namespaces.keys==setOf("auth","policy"))
                val git=NativeGit(owner,processBounds); git.qualify(); git.initialize()
                val store=GitCommandStore(owner,git,storeId,ports,bounds,publication)
                val entries=LinkedHashMap<String,GitOid>()
                val all=files+StoreFile("store.json",StrictJson.encode(store.header()))+StoreFile("commands/format.json",StrictJson.encode(store.commandHeader))+StoreFile("policy/admission.json",admission.bytes())
                integrity(all.size<=bounds.files)
                all.forEach { f -> store.validatePhysicalPath(f.path); integrity(f.path !in entries && !f.path.startsWith("commands/receipts/")); val bytes=f.bytes(); admission.file(f.path,bytes); entries[f.path]=git.blob(bytes) }
                val candidate=git.commit(store.writeTree(entries),null)
                store.validateSnapshot(store.snapshot(candidate))
                owner.beginPublication()
                val result=git.update(null,candidate)
                if(result.exit!=0 || result.stderr.isNotEmpty() || git.head()!=candidate) { owner.unknown(); fail(StoreProblem.RECOVERY_REQUIRED) }
                owner.finishPublication(); store.validated=ValidatedHead(store.snapshot(candidate),1)
                return store
            } catch(e: Exception) { owner.close(); throw e }
        }
        fun open(directory: Path,storeId: UUID,ports: StorePorts,bounds: StoreBounds=StoreBounds(),processBounds: ProcessBounds=ProcessBounds(),publication: PublicationDriver=PublicationDriver.Native): GitCommandStore {
            val owner=RuntimeOwner.acquire(directory)
            try {
                val git=NativeGit(owner,processBounds); git.qualify(); git.verifyRepository()
                val store=GitCommandStore(owner,git,storeId,ports,bounds,publication); store.validated=store.audit(git.head()); return store
            } catch(e: Exception) { owner.close(); throw e }
        }
    }
}
