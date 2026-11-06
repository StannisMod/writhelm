// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal class ServiceFault(val code: String,val status: Int): RuntimeException(code)
internal fun stop(code: String,status: Int): Nothing=throw ServiceFault(code,status)
internal class ServiceReply(val status: Int,val value: JsonValue) {
    fun bytes(maximum: Int=4*1024*1024)=StrictJson.encode(value,maximum,true)
}
internal fun error(code: String,status: Int)=ServiceReply(status,obj("formatVersion" to num(1),"kind" to str("error"),"code" to str(code)))
internal val agentOperations=setOf("propose_revision","request_approval","record_assessment","pin_consumer")
internal class ServiceLimits(val responseBytes: Int=4*1024*1024,val snapshotBytes: Int=1024*1024,
    val snapshots: Int=2,val cursorCount: Int=512,val snapshotMillis: Long=300000) {
    init { require(responseBytes in 65536..4*1024*1024 && snapshotBytes in 1..1024*1024 &&
        snapshots in 1..2 && cursorCount in 1..512 && snapshotMillis in 1..300000) }
}
/** Shared actual checked application boundary. No production auth/transition doubles. */
internal class WrithelmService private constructor(val app: MechanismApp?,val limits: ServiceLimits,
    private val now: ()->Long,private val h1: H1Application?) {
    constructor(app: MechanismApp,limits: ServiceLimits=ServiceLimits(),now: ()->Long={System.nanoTime()/1000000}):this(app,limits,now,null)
    constructor(app: H1Application,limits: ServiceLimits=ServiceLimits(),now: ()->Long={System.nanoTime()/1000000}):this(null,limits,now,app)
    private class Listing(val id: UUID,val principal: UUID,val project: UUID,val operation: String,
        val selector: String,val pageSize: Int,val basis: GitOid,val born: Long,val rows: List<JsonValue>)
    private class Cursor(val listing: Listing,val offset: Int)
    private val listings=linkedMapOf<UUID,Listing>()
    private val cursors=linkedMapOf<UUID,Cursor>()
    fun access(token: String)=h1?.inspectAccess(token) ?: checkNotNull(app).inspectAccess(token)
    fun <T> guarded(block: ()->T,onError: (ServiceReply)->T): T = try { block() }
        catch(e: ServiceFault) { onError(error(e.code,e.status)) }
        catch(e: AppAccessFault) { onError(error(e.problem.name,if(e.problem==AppAccessProblem.UNAUTHENTICATED)401 else 403)) }
        catch(e: CodecFault) { onError(error(if(e.problem==CodecProblem.RESOURCE_UNAVAILABLE)"UNAVAILABLE" else "INVALID_INPUT",if(e.problem==CodecProblem.RESOURCE_UNAVAILABLE)503 else 400)) }
         catch(e: StoreFault) { onError(error(when(e.problem) { StoreProblem.INTEGRITY -> "INTEGRITY"; StoreProblem.UNSUPPORTED -> "UNSUPPORTED"; StoreProblem.DENIED -> "FORBIDDEN"; else -> "UNAVAILABLE" },when(e.problem) { StoreProblem.INTEGRITY -> 500; StoreProblem.UNSUPPORTED -> 400; StoreProblem.DENIED -> 403; else -> 503 })) }
         catch(e: H1ReadFault) { onError(error(e.code,when(e.code) { "NOT_FOUND" -> 404;"UNAVAILABLE","SNAPSHOT_EXPIRED","INCOMPLETE_PACKET" -> 503;"FORBIDDEN" -> 403;else -> 400 })) }
         catch(e: dev.writhelm.core.work.WorkFault) { onError(error(e.problem.name,when(e.problem) { dev.writhelm.core.work.WorkProblem.NOT_FOUND -> 404;dev.writhelm.core.work.WorkProblem.DIGEST_MISMATCH -> 409;else -> 400 })) }
    fun command(token: String,bytes: ByteArray,agent: Boolean=false): ServiceReply=guarded({
        val result=if(h1!=null) {
            val a=access(token)
            val f=StrictJson.decodeWorkCommand(bytes,JsonLimits(bytes=8*1024*1024,stringBytes=4*1024*1024),1024*1024).fields("formatVersion","requestKey","projectId","operation","payload")
            val op=f.getValue("operation").string()
            if(a.projectId!=uuid(f.getValue("projectId")))stop("FORBIDDEN",403)
            if(agent && (op !in agentOperations || a.scope is dev.writhelm.core.auth2.CredentialScope.Assignment || dev.writhelm.core.auth.requiredCapability(op) !in a.capabilities))stop("FORBIDDEN",403)
            h1.execute(token,bytes)
        } else {
            val c=StoreCommand.decode(bytes)
            if(agent && c.operation !in agentOperations)stop("FORBIDDEN",403)
            val a=access(token)
            if(a.projectId!=c.projectId || dev.writhelm.core.auth.requiredCapability(c.operation) !in a.capabilities)stop("FORBIDDEN",403)
            checkNotNull(app).execute(token,bytes)
        }
        // Bounded identity/code-only outcome, never the potentially large command/packet.
        when(val r=result) {
            is CommandResult.Settled -> ServiceReply(if(r.receipt.outcome is LogicalOutcome.Rejected)409 else 200,
                obj("formatVersion" to num(1),"kind" to str("settled"),"identity" to r.receipt.identity.json(),
                    "fingerprint" to str(r.receipt.fingerprint),"validatedAt" to r.receipt.validatedAt.json(),
                    "authenticatedByCredentialId" to uuid(r.receipt.authenticatedByCredentialId),
                    "outcome" to r.receipt.outcome.json(),"commandReplayed" to JBoolean(r.commandReplayed),
                    "publication" to (r.publication?.json() ?: JNull)))
            CommandResult.IdentityConflict -> error("IDENTITY_CONFLICT",409)
            CommandResult.Denied -> error("FORBIDDEN",403)
            CommandResult.Unknown -> error("PUBLICATION_UNKNOWN",503)
            is CommandResult.Refused -> error(when(r.problem) {
                StoreProblem.INTEGRITY -> "INTEGRITY"; StoreProblem.DENIED -> "FORBIDDEN"
                StoreProblem.UNSUPPORTED -> "UNSUPPORTED"; else -> "UNAVAILABLE"
            },when(r.problem) { StoreProblem.INTEGRITY -> 500; StoreProblem.DENIED -> 403; StoreProblem.UNSUPPORTED -> 400; else -> 503 })
        }
    },{it})
    @Synchronized fun read(token: String,bytes: ByteArray,agent: Boolean=false): ServiceReply=guarded({
        val f=StrictJson.decode(bytes,JsonLimits(bytes=16384)).fields("formatVersion","projectId","operation","subject")
        if(h1!=null) {
            val version=f.getValue("formatVersion").long()
            if(agent && version!=1L)stop("FORBIDDEN",403)
            val value=when(version) { 1L -> h1.read1(token,bytes);2L -> h1.read2(token,bytes);else -> stop("INVALID_INPUT",400) }
            return@guarded ServiceReply(200,value).also { it.bytes(limits.responseBytes) }
        }
        version(f.getValue("formatVersion"))
        val op=f.getValue("operation").string();val sub=f.getValue("subject")
        val view=checkNotNull(app).captureRead(token);val a=view.access
        if(op=="list_projects") { if(f.getValue("projectId")!=JNull)stop("INVALID_INPUT",400) }
        else if(uuid(f.getValue("projectId"))!=a.projectId)stop("FORBIDDEN",403)
        val s=view.workflow.snapshot(8192)
        var basis=a.basis
        val data=if(op.startsWith("list_"))page(a,s,op,sub).let { basis=it.first;it.second } else when(op) {
            "read_revision" -> {
                val x=sub.fields("revisionKey","revisionDigest");val k=revisionKey(x.getValue("revisionKey"))
                val r=s.revisions.firstOrNull { it.key==k && k.document.projectId==a.projectId } ?: stop("NOT_FOUND",404)
                if(r.digest!=digest(x.getValue("revisionDigest")))stop("INVALID_INPUT",400)
                obj("revisionKey" to revisionKey(k),"revisionDigest" to digest(r.digest),"bodyDigest" to digest(r.content.digest),
                    "structure" to structure(r.structure),"body" to str(r.content.text()),"proposedBy" to uuid(s.proposals.getValue(k).principalId))
            }
            "read_clause","read_context","read_approval_trace" -> {
                val ref=reference(sub)
                if(op=="read_clause" && ref !is ClauseReference || op=="read_context" && ref !is ContextReference)stop("INVALID_INPUT",400)
                material(view,s,ref)
            }
            "read_request","read_resolution","read_manifest" -> {
                val id=uuid(sub.fields("id").getValue("id"));val family=when(op) { "read_request" -> "requests"; "read_resolution" -> "resolutions"; else -> "manifests" }
                obj("record" to record(s,a.projectId,family,id))
            }
            "read_review_bundle" -> bundle(view,s,sub)
            else -> stop("INVALID_INPUT",400)
        }
        val reply=ServiceReply(200,obj("formatVersion" to num(1),"kind" to str("read"),"basis" to basis.json(),"operation" to str(op),"data" to data))
        reply.bytes(limits.responseBytes)
        reply
    },{it})
    private fun page(a: AppAccessView,s: WorkflowSnapshot,op: String,sub: JsonValue): Pair<GitOid,JsonValue> {
        val extras=when(op) { "list_projects","list_documents","list_manifests" -> emptyArray(); "list_revisions" -> arrayOf("documentId"); "list_requests" -> arrayOf("pendingOnly"); else -> stop("INVALID_INPUT",400) }
        val f=sub.fields("cursor","pageSize",*extras);val n=f.getValue("pageSize").int()
        if(n !in 1..32)stop("INVALID_INPUT",400)
        val selector=StrictJson.encode(JObject(f-"cursor")).decodeToString();val time=now()
        listings.values.filter { time-it.born>=limits.snapshotMillis }.map { it.id }.forEach { id ->
            listings.remove(id);cursors.entries.removeIf { it.value.listing.id==id }
        }
        val cursor=f.getValue("cursor")
        val pair=if(cursor!=JNull) {
            val c=cursors[uuid(cursor)] ?: stop("SNAPSHOT_EXPIRED",503);val l=c.listing
            if(l.principal!=a.principalId || l.project!=a.projectId || l.operation!=op || l.selector!=selector || l.pageSize!=n)stop("FORBIDDEN",403)
            l to c.offset
        } else {
            if(listings.size>=limits.snapshots)stop("UNAVAILABLE",503)
            val project=a.projectId
            val rows=when(op) {
                "list_projects" -> listOf(obj("projectId" to uuid(project)))
                "list_documents" -> s.revisions.filter { it.key.document.projectId==project }.map { it.key.document }.distinct().sortedBy { it.documentId.toString() }.map { obj("document" to document(it)) }
                "list_revisions" -> { val d=uuid(f.getValue("documentId"));s.revisions.filter { it.key.document.projectId==project && it.key.document.documentId==d }.sortedBy { it.key.revisionId.toString() }.map { obj("revisionKey" to revisionKey(it.key),"revisionDigest" to digest(it.digest)) } }
                "list_requests" -> { val pending=(f.getValue("pendingOnly") as? JBoolean)?.value ?: stop("INVALID_INPUT",400)
                    s.requests.values.filter { it.projectId==project && (!pending || it.id !in s.resolutions) }.sortedBy { it.id.toString() }.map { obj("id" to uuid(it.id),"requestDigest" to digest(it.digest),"packetDigest" to digest(it.packet.digest),"pending" to JBoolean(it.id !in s.resolutions)) } }
                else -> s.manifests.values.filter { it.projectId==project }.sortedBy { it.id.toString() }.map { obj("id" to uuid(it.id),"consumerId" to uuid(it.consumerId)) }
            }
            if(rows.size>8192)stop("UNAVAILABLE",503)
            StrictJson.encode(JArray(rows),limits.snapshotBytes,true)
            val l=Listing(UUID.randomUUID(),a.principalId,project,op,selector,n,a.basis,time,rows)
            listings[l.id]=l;l to 0
        }
        val (l,offset)=pair;val end=minOf(offset+n,l.rows.size)
        val next=if(end==l.rows.size)null else {
            if(cursors.size>=limits.cursorCount)stop("UNAVAILABLE",503)
            UUID.randomUUID().also { cursors[it]=Cursor(l,end) }
        }
        if(next==null && offset==0)listings.remove(l.id)
        return l.basis to obj("snapshotId" to uuid(l.id),"nextCursor" to (next?.let(::uuid) ?: JNull),"complete" to JBoolean(next==null),"items" to JArray(l.rows.subList(offset,end)))
    }
    private fun record(s: WorkflowSnapshot,project: UUID,family: String,id: UUID): JsonValue {
        val exists=when(family) { "requests","resolutions" -> s.requests[id]?.projectId==project && (family!="resolutions" || id in s.resolutions); "manifests" -> s.manifests[id]?.projectId==project; else -> false }
        if(!exists)stop("NOT_FOUND",404)
        val r=CoreRecordCodec.records(s).first { it.family==family && uuid(it.key)==id }
        return CoreRecordCodec.envelope(r.family,r.key,r.payload())
    }
    private fun material(v: AppReadView,s: WorkflowSnapshot,ref: KnowledgeReference): JsonValue {
        if(ref.revision.document.projectId!=v.access.projectId)stop("FORBIDDEN",403)
        val catalog=RevisionCatalog(s.revisions)
        val fragments=when(ref) {
            is ClauseReference -> when(val r=catalog.read(ref)) { is ReadResult.Found -> listOf(r.value.located); else -> stop("NOT_FOUND",404) }
            is ContextReference -> when(val r=catalog.read(ref)) { is ReadResult.Found -> r.value.fragments; else -> stop("NOT_FOUND",404) }
        }
        val observation=v.workflow.approvalTrace(Actor(v.access.principalId),ref)
        // Historical attribution is not current eligibility. Retain the frozen chain even when
        // today's observation is BLOCKED_INPUT (the pure authority accessor intentionally refuses).
        fun recorded(ref: KnowledgeReference,seen: Set<KnowledgeReference> = emptySet()): ApprovalTrace? {
            if(seen.size>=256 || ref in seen)stop("UNAVAILABLE",503)
            s.approvals[ref]?.let { return it }
            val link=s.continuity[ref] ?: return null
            val source=recorded(link.source,seen+ref) ?: return null
            return ApprovalTrace(source.origin,source.requestId,source.approvedBy,source.assessmentIds+link.assessmentId)
        }
        return obj("reference" to reference(ref),"fragments" to JArray(fragments.map { obj("span" to span(it.span),"text" to str(it.content.text())) }),
            "observation" to str(if(observation is WorkflowResult.Success)"APPROVED" else (observation as WorkflowResult.Failure).problem.name),
            "trace" to ((observation as? WorkflowResult.Success)?.value?.let(::trace) ?: recorded(ref)?.let(::trace) ?: JNull))
    }
    private fun bundle(v: AppReadView,s: WorkflowSnapshot,sub: JsonValue): JsonValue {
        val f=sub.fields("id","requestDigest","packetDigest");val id=uuid(f.getValue("id"))
        val r=s.requests[id]?.takeIf { it.projectId==v.access.projectId } ?: stop("NOT_FOUND",404)
        if(r.digest!=digest(f.getValue("requestDigest")) || r.packet.digest!=digest(f.getValue("packetDigest")))stop("INVALID_INPUT",400)
        if(r.packet.informationGaps.isNotEmpty())stop("INCOMPLETE_PACKET",503)
        val materials=r.packet.references.map { material(v,s,it) };val blockers=mutableListOf<String>()
        if(id in s.resolutions)blockers.add("ALREADY_RESOLVED")
        else when(val probe=v.workflow.resolve(Actor(v.access.principalId),r.projectId,id,r.digest,r.packet.digest,StructuredAnswer.Approve)) {
            is WorkflowResult.Failure -> blockers.add(probe.problem.name)
            is WorkflowResult.Success -> Unit
        }
        return obj("request" to record(s,r.projectId,"requests",id),"resolution" to (if(id in s.resolutions)record(s,r.projectId,"resolutions",id) else JNull),
            "materials" to JArray(materials),"actionable" to JBoolean(blockers.isEmpty()),"blockers" to JArray(blockers.map(::str)),"complete" to JBoolean(true))
    }
}
