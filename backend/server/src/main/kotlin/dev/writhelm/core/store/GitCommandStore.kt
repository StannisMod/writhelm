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
    private val journalVersion=if(ports.h1==null)2 else 3
    private val policyVersion=if(ports.h1==null)1 else 2
    private val commandHeader=obj("formatVersion" to num(journalVersion))
    private val expectedNamespaces: Map<String,JsonValue> = mapOf("core" to CoreRecordCodec.header(),"commands" to commandHeader)+ports.namespaces.mapValues { it.value.header }
    init {
        val policyHeader=ports.namespaces["policy"]?.header ?: fail(StoreProblem.UNSUPPORTED)
        if(!StrictJson.encode(policyHeader).contentEquals(StrictJson.encode(obj("formatVersion" to num(policyVersion)))))fail(StoreProblem.UNSUPPORTED)
        if(ports.h1!=null) {
            integrity(ports.namespaces.keys==setOf("auth","policy","work"))
            if(!StrictJson.encode(ports.namespaces.getValue("auth").header).contentEquals(StrictJson.encode(obj("formatVersion" to num(2),"profileVersion" to num(2)))))fail(StoreProblem.UNSUPPORTED)
            if(!StrictJson.encode(ports.namespaces.getValue("work").header).contentEquals(StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))))fail(StoreProblem.UNSUPPORTED)
        }
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
            if(command.fingerprintVersion==3 && (ports.h1==null || command.h1Admission!==ports.h1))fail(StoreProblem.UNSUPPORTED)
            var consumed=0L
            fun bounded(s: StoreSnapshot)=if(ports.h1==null)s else s.withMeter { n ->
                if(n.toLong()>64L*1024*1024-consumed)fail(StoreProblem.UNAVAILABLE)
                consumed+=n
            }
            fun scope(h: StoreSnapshot): CurrentCommandScope=if(ports.h1!=null)ports.h1.authorize(h,command)
                else access.authorize(h,command)?.let { CurrentCommandScope.NewEffect(it) } ?: CurrentCommandScope.Denied
            fun scopedActor(s: CurrentCommandScope): AuthenticatedActor?=when(s) { is CurrentCommandScope.NewEffect -> s.actor; is CurrentCommandScope.ReplayOnly -> s.actor; CurrentCommandScope.Denied -> null }
            var reconciled: Pair<ValidatedHead,CurrentCommandScope>?=null
            repeat(bounds.attempts) {
                // A qualified absence below may supply the next attempt's already captured H and
                // current access. Do not recapture or substitute another credential in between;
                // expected-old CAS still fences any subsequent head movement.
                val prepared=reconciled; reconciled=null
                val base=prepared?.first ?: capture(); val h=bounded(base.snapshot)
                // The callback closes over the SAME presented credential. It must authenticate and
                // authorize in this H, including on every CAS rebuild and before any receipt lookup.
                val currentScope=prepared?.second ?: scope(h)
                val actor=scopedActor(currentScope) ?: return CommandResult.Denied
                originalCredential?.let { integrity(it==actor.credentialId) }; originalCredential=actor.credentialId
                val policy=AdmissionPolicy.decode(h.file("policy/admission.json") ?: fail(StoreProblem.INTEGRITY))
                policy.command(command)
                val identity=CommandIdentity(actor.principalId,command.projectId,command.operation,command.requestKey)
                originalIdentity?.let { integrity(it==identity) }; originalIdentity=identity
                h.file(identity.path)?.let { bytes ->
                    val receipt=decodeReceipt(bytes,identity.path)
                    integrity(receipt.identity==identity && receipt.storeId==storeId)
                    return if(receipt.fingerprintVersion==command.fingerprintVersion && receipt.fingerprint==command.fingerprint)
                        CommandResult.Settled(receipt,true,null) else CommandResult.IdentityConflict
                }
                // A current terminal scope may reconcile ONLY an existing original key. Absence is
                // not a domain rejection, cannot consume history, and MUST NOT reach a reducer/CAS.
                if(currentScope is CurrentCommandScope.ReplayOnly)return CommandResult.Denied
                // Authorization and original receipt recovery precede this operational growth cap.
                // Every new outcome needs one commit, including rejection/domain-replay receipts.
                // No evaluator, candidate, CAS or durable rejection is allocated at capacity.
                if(base.publishedCommitCount>=bounds.historyCommits)return CommandResult.Refused(StoreProblem.UNAVAILABLE)
                ports.h1?.preflight?.invoke(h,command,actor)
                val evaluation=evaluator.evaluate(h,command,actor)
                val changes=evaluation.changes
                if(changes.size>policy.writeDescriptors)fail(StoreProblem.ADMISSION)
                integrity(changes.map { it.key.path }.distinct().size==changes.size)
                if(ports.h1!=null)changes.forEach { change -> change.bytes()?.let {
                    if(it.size.toLong()>64L*1024*1024-consumed)fail(StoreProblem.UNAVAILABLE)
                    consumed+=it.size
                } }
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
                val receipt=Receipt(storeId,identity,command.fingerprintVersion,command.fingerprint,h.oid,actor.credentialId,evaluation.outcome,descriptors,journalVersion)
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
                val candidate=snapshot(c); validateBoundary(h,bounded(candidate),receipt)
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
                // Identical reconciliation on EVERY attempt, including the last. A completed child
                // does not by itself establish the command outcome. Keep ambiguity/barrier until
                // the published line, current rights and original receipt are resolved. These calls
                // retain the ORIGINAL aggregate deadline; any unresolved failure stays Unknown.
                // Retain the old-H ancestry anchor while auditing this changed head. The barrier
                // remains set until the same current-authority/receipt reconciliation completes.
                val currentBase=capture(); val current=bounded(currentBase.snapshot)
                val recapturedScope=scope(current)
                val currentActor=scopedActor(recapturedScope)
                if(currentActor==null) {
                    owner.finishPublication(); publicationStarted=false
                    return CommandResult.Denied
                }
                val samePresentation=currentActor.principalId==actor.principalId && currentActor.credentialId==actor.credentialId
                if(identity.path in current.entries) {
                    // No protected result/conflict is disclosed under a substituted identity.
                    integrity(samePresentation)
                    val bytes=current.file(identity.path) ?: fail(StoreProblem.INTEGRITY)
                    val original=decodeReceipt(bytes,identity.path)
                    integrity(original.identity==identity && original.storeId==storeId)
                    val recovered=if(original.fingerprintVersion==command.fingerprintVersion && original.fingerprint==command.fingerprint)
                        CommandResult.Settled(original,true,null) else CommandResult.IdentityConflict
                    owner.finishPublication(); publicationStarted=false
                    return recovered
                }
                // Validated published absence resolves publication ambiguity, independently of a
                // faulty trusted access port. Reject identity substitution before any rebuild;
                // no receipt is disclosed and no new evaluator/CAS runs for that invalid port.
                owner.finishPublication(); publicationStarted=false
                integrity(samePresentation)
                if(recapturedScope is CurrentCommandScope.ReplayOnly)return CommandResult.Denied
                reconciled=currentBase to recapturedScope
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
    private fun decodeReceipt(bytes: ByteArray,path: String): Receipt=JournalCodec.decode(bytes,path).also { if(it.journalVersion!=journalVersion)fail(StoreProblem.UNSUPPORTED) }
    private fun capture(): ValidatedHead {
        val head=git.head(); validated?.let {
            if(it.snapshot.oid==head)return it
            // An unrecognized ref is not permission to forget already observed publication.
            // A legitimate competing publication must extend this exact validated H.
            val ancestry=git.call(listOf("merge-base","--is-ancestor",it.snapshot.oid.hex,head.hex))
            integrity(ancestry.exit==0 && ancestry.stderr.isEmpty() && ancestry.output.isEmpty())
        }
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
            "auth" -> {
                if(ports.h1==null) {
                    val h1=Regex("auth/records/(controllers|work-grants|delegations)/([0-9a-f]{2})/([0-9a-f]{64})\\.json").matchEntire(path)
                    if(h1!=null && h1.groupValues[2]==h1.groupValues[3].take(2))fail(StoreProblem.UNSUPPORTED)
                }
                val families=if(ports.h1==null)"principals|projects|credentials|grant-guards" else "principals|projects|credentials|grant-guards|controllers|work-grants|delegations"
                integrity(Regex("auth/records/($families)/([0-9a-f]{2})/([0-9a-f]{64})\\.json").matchEntire(path)?.let { it.groupValues[2]==it.groupValues[3].take(2) }==true)
            }
            "work" -> {
                val body=Regex("work/bodies/([0-9a-f]{2})/([0-9a-f]{64})\\.txt").matchEntire(path)
                if(body!=null)integrity(body.groupValues[1]==body.groupValues[2].take(2)) else {
                    val m=Regex("work/records/([a-z-]+)/([0-9a-f]{2})/([0-9a-f]{64})\\.json").matchEntire(path) ?: fail(StoreProblem.INTEGRITY)
                    H1Catalog.family(m.groupValues[1]); integrity(m.groupValues[2]==m.groupValues[3].take(2))
                }
            }
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
        val policy=AdmissionPolicy.decode(s.file("policy/admission.json") ?: fail(StoreProblem.INTEGRITY))
        if(policy.formatVersion!=policyVersion)fail(StoreProblem.UNSUPPORTED)
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
                val receipt=decodeReceipt(s.file(added.single())!!,added.single())
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
        if(receipt.journalVersion!=journalVersion)fail(StoreProblem.UNSUPPORTED)
        receipt.writes.forEach { w ->
            val before=h.file(w.path); val after=c.file(w.path)
            integrity(before?.let(::sha)==w.beforeDigest && after?.let(::sha)==w.afterDigest)
            validateRecord(w.record,before,after,false)
        }
        if(receipt.identity.operation=="register_delegation" && receipt.outcome is LogicalOutcome.Success)validateRegistrationBoundary(h,c,receipt)
        validateResultReference(c,receipt)
        // Required H1 joint/historical validation sees this exact original edge, not a later head.
        // Both snapshots retain their active command/audit meters; no temporary after-record join.
        ports.h1?.validateBoundary?.invoke(h,c,receipt)
    }
    /** Cross-namespace enrollment coupling is a publication invariant, not three independent writes.
     * Full delegation authority/reference laws still belong to the required AUTH/CORE validators. */
    private fun validateRegistrationBoundary(h: StoreSnapshot,c: StoreSnapshot,r: Receipt) {
        val outcome=r.outcome as LogicalOutcome.Success
        fun payload(s: StoreSnapshot,key: RecordKey): Map<String,JsonValue> {
            val raw=s.file(key.path) ?: fail(StoreProblem.INTEGRITY)
            val e=StrictJson.decode(raw,JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)).fields("formatVersion","family","key","payload")
            return (e.getValue("payload") as? JObject)?.entries ?: fail(StoreProblem.INTEGRITY)
        }
        fun equal(a: JsonValue,b: JsonValue)=integrity(StrictJson.encode(a).contentEquals(StrictJson.encode(b)))
        val delegation=payload(c,RecordKey("auth","delegations",outcome.result))
        val binding=(delegation.getValue("binding") as? JObject)?.entries ?: fail(StoreProblem.INTEGRITY)
        integrity(uuid(binding.getValue("projectId"))==r.identity.projectId)
        integrity(delegation.getValue("generation").long()==0L && delegation.getValue("status").string()=="active")
        val principal=binding.getValue("principalId")
        val grant=obj("principalId" to principal,"projectId" to uuid(r.identity.projectId))
        val required=setOf(RecordKey("auth","delegations",outcome.result).path,
            RecordKey("auth","principals",principal).path,RecordKey("auth","credentials",binding.getValue("initialCredentialId")).path,
            RecordKey("auth","grant-guards",grant).path,RecordKey("auth","work-grants",grant).path,RecordKey("core","grants",grant).path,
            RecordKey("work","assignments",obj("projectId" to uuid(r.identity.projectId),"id" to binding.getValue("assignmentId"))).path)
        integrity(r.writes.map { it.path }.toSet()==required)
        equal(payload(c,RecordKey("core","grants",grant)).getValue("capabilities"),JArray(listOf(str("READ"))))
        integrity(payload(c,RecordKey("auth","grant-guards",grant)).getValue("generation").long()==0L)
        val work=payload(c,RecordKey("auth","work-grants",grant))
        integrity(work.getValue("generation").long()==0L && work.getValue("permissions").list().isEmpty())
        val credential=payload(c,RecordKey("auth","credentials",binding.getValue("initialCredentialId")))
        equal(credential.getValue("principalId"),principal); equal(credential.getValue("projectId"),uuid(r.identity.projectId)); equal(credential.getValue("storeId"),uuid(storeId))
        integrity(credential.getValue("generation").long()==0L && credential.getValue("status").string()=="active" && credential.getValue("replacedBy")===JNull)
        equal(credential.getValue("scope"),obj("kind" to str("assignment"),"delegationId" to outcome.result))
        val assignmentKey=RecordKey("work","assignments",obj("projectId" to uuid(r.identity.projectId),"id" to binding.getValue("assignmentId")))
        val old=payload(h,assignmentKey); val next=payload(c,assignmentKey)
        integrity(old.getValue("state").string()=="reserved" && old.getValue("delegationId")===JNull && old.getValue("execution")===JNull && old.getValue("binding")===JNull)
        equal(next.getValue("delegationId"),outcome.result)
        equal(JObject(old-setOf("generation","delegationId")),JObject(next-setOf("generation","delegationId")))
        val generation=old.getValue("generation").long(); integrity(generation<Long.MAX_VALUE && next.getValue("generation").long()==generation+1)
    }
    private fun validateRecord(key: RecordKey,before: ByteArray?,after: ByteArray?,admission: Boolean=true) {
        integrity(before!=null || after!=null)
        if(before!=null)integrity(key.mutable)
        if(after==null) { integrity(key.removable); return }
        if(ports.h1!=null && key.namespace in setOf("auth","work")) {
            validateH1Record(key,before,after,!admission)
            return
        }
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
    private fun validateH1Record(key: RecordKey,before: ByteArray?,after: ByteArray,historical: Boolean) {
        if(key.namespace=="work" && key.family=="bodies") {
            if(after.size>4*1024*1024)fail(StoreProblem.UNAVAILABLE)
            integrity(Utf8Content.fromBytes(after).digest==digest(key.key)); return
        }
        val limits=if(key.namespace=="auth")JsonLimits(bytes=65536,stringBytes=1024,depth=32,collection=256,tokens=32768)
            else JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)
        fun payload(bytes: ByteArray): JsonValue {
            val value=StrictJson.decode(bytes,limits,true)
            integrity(bytes.contentEquals(StrictJson.encode(value,limits.bytes,maxDepth=limits.depth)))
            val f=value.fields("formatVersion","family","key","payload")
            integrity(f.getValue("formatVersion").int()==if(key.namespace=="auth")2 else 1)
            integrity(f.getValue("family").string()==key.family && StrictJson.encode(f.getValue("key")).contentEquals(StrictJson.encode(key.key)))
            return f.getValue("payload")
        }
        val next=payload(after)
        if(key.mutable) {
            fun generation(value: JsonValue)=(value as? JObject)?.entries?.get("generation")?.long() ?: fail(StoreProblem.INTEGRITY)
            val n=generation(next)
            if(before==null)integrity(n==0L) else { val old=generation(payload(before)); integrity(old<Long.MAX_VALUE && n==old+1) }
        }
        ports.h1!!.validateRecord(key,after,historical)
    }
    private fun validateResultReference(c: StoreSnapshot,r: Receipt) {
        val outcome=r.outcome as? LogicalOutcome.Success ?: return
        if(outcome.operation in H1Catalog.workOperations) {
            val key=H1Catalog.result(outcome.operation,outcome.result)
            val raw=c.file(key.path) ?: fail(StoreProblem.INTEGRITY)
            val result=outcome.result.fields("key","generation","digest")
            integrity(sha(raw)==digest(result.getValue("digest")).hex)
            if(key.mutable) {
                val envelope=StrictJson.decode(raw,JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)).fields("formatVersion","family","key","payload")
                val payload=envelope.getValue("payload") as? JObject ?: fail(StoreProblem.INTEGRITY)
                integrity(payload.entries.getValue("generation").long()==result.getValue("generation").long())
            }
            return
        }
        if(outcome.operation in H1Catalog.authOperations) {
            val family=when(outcome.operation) { "register_delegation","revoke_delegation" -> "delegations"; "replace_work_grants" -> "work-grants"; else -> "credentials" }
            val key=when(outcome.operation) { "replace_work_grants" -> obj("principalId" to outcome.result,"projectId" to uuid(r.identity.projectId)); "rotate_delegated_credential" -> outcome.result.fields("credentialId","replacedCredentialId").getValue("credentialId"); else -> outcome.result }
            integrity(RecordKey("auth",family,key).path in c.entries); return
        }
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
        if(c.operation in H1Catalog.workOperations) {
            val key=H1Catalog.result(c.operation,o.result)
            val expected=when(c.operation) {
                "reserve_assignment","bind_assignment","rebind_assignment","complete_assignment" -> obj("projectId" to uuid(c.projectId),"id" to payload.getValue("assignmentId"))
                "request_native_transition","observe_native_transition" -> obj("projectId" to uuid(c.projectId),"id" to payload.getValue("intentId"))
                "observe_integration" -> payload.getValue("intent")
                else -> payload.getValue("key")
            }
            integrity(StrictJson.encode(key.key).contentEquals(StrictJson.encode(expected))); return
        }
        val expected=when(c.operation) {
            "propose_revision" -> payload.getValue("revisionKey")
            "request_approval","resolve_approval" -> payload.getValue("requestId")
            "record_assessment" -> payload.getValue("assessmentId")
            "pin_consumer" -> payload.getValue("manifestId")
            "revoke_access","replace_grants" -> payload.getValue("principalId")
            "revoke_credential" -> payload.getValue("credentialId")
            "replace_work_grants" -> payload.getValue("principalId")
            "register_delegation","revoke_delegation" -> payload.getValue("delegationId")
            "revoke_delegated_credential" -> payload.getValue("credentialId")
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
            "register_delegation" -> {
                val binding=(p.getValue("binding") as? JObject)?.entries ?: fail(StoreProblem.INTEGRITY)
                val principal=binding.getValue("principalId")
                val grant=obj("principalId" to principal,"projectId" to uuid(c.projectId))
                integrity(uuid(binding.getValue("projectId"))==c.projectId)
                requireChange("core","grants",grant,true)
                requireChange("auth","principals",principal,true)
                requireChange("auth","grant-guards",grant,true)
                requireChange("auth","work-grants",grant,true)
                requireChange("auth","credentials",binding.getValue("initialCredentialId"),true)
                requireChange("auth","delegations",p.getValue("delegationId"),true)
                requireChange("work","assignments",obj("projectId" to uuid(c.projectId),"id" to binding.getValue("assignmentId")))
            }
            "replace_work_grants" -> requireChange("auth","work-grants",obj("principalId" to p.getValue("principalId"),"projectId" to uuid(c.projectId)))
            "revoke_delegation" -> requireChange("auth","delegations",p.getValue("delegationId"))
            "revoke_delegated_credential" -> requireChange("auth","credentials",p.getValue("credentialId"))
            "rotate_delegated_credential" -> { requireChange("auth","credentials",p.getValue("credentialId")); requireChange("auth","credentials",p.getValue("newCredentialId"),true) }
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
        if(c.operation in H1Catalog.operations) {
            val keys=ports.h1?.allowedWrites?.invoke(h,c) ?: fail(StoreProblem.UNSUPPORTED)
            if(keys.size>4096)fail(StoreProblem.UNAVAILABLE)
            return keys.map { it.path }.toSet()
        }
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
                integrity(ports.namespaces.keys==if(ports.h1==null)setOf("auth","policy") else setOf("auth","policy","work"))
                if(admission.formatVersion!=if(ports.h1==null)1 else 2)fail(StoreProblem.UNSUPPORTED)
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
