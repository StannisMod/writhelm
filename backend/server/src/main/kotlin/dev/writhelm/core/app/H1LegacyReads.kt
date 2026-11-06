// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import dev.writhelm.core.work.subjectProjection
import java.util.UUID

/** Exact legacy wire meaning with current subject authorization; never returns a broad workflow. */
internal class H1LegacyReads(private val navigation: H1Navigation) {
    private val protected=H1ProtectedReads()
    fun read(base: H1BoundState,key: PresentedCredential,bytes: ByteArray): JsonValue {
        val f=StrictJson.decode(bytes,JsonLimits(bytes=16384)).fields("formatVersion","projectId","operation","subject")
        version(f.getValue("formatVersion"));val op=f.getValue("operation").string();val sub=f.getValue("subject")
        val stored=base.authority.credentials[key.credentialId] ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        val c=base.authority.authenticate(key,stored.credential.projectId) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        val project=c.credential.projectId
        if(op=="list_projects")shape(f.getValue("projectId")===JNull) else if(uuid(f.getValue("projectId"))!=project)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(!base.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val snapshot=base.core.snapshot(8192);var basis=base.basis
        val data=if(op.startsWith("list_")) {
            if(c.scope is CredentialScope.Assignment)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            val extras=when(op) { "list_projects","list_documents","list_manifests" -> emptyArray();"list_revisions" -> arrayOf("documentId");"list_requests" -> arrayOf("pendingOnly");else -> throw H1ReadFault("INVALID_INPUT") }
            val s=sub.fields("cursor","pageSize",*extras);val size=s.getValue("pageSize").int()
            val cursor=s.getValue("cursor").let { if(it===JNull)null else uuid(it) }
            val binding=AuthSessionBinding(base.storeId,c.credential.principalId,project,c.scope)
            navigation.page(binding,base.basis,op,StrictJson.encode(JObject(s-"cursor")).decodeToString(),size,cursor) {
                when(op) {
                    "list_projects" -> listOf(obj("projectId" to uuid(project)))
                    "list_documents" -> snapshot.revisions.filter { it.key.document.projectId==project }.map { it.key.document }.distinct().sortedBy { it.documentId.toString() }.map { obj("document" to document(it)) }
                    "list_revisions" -> { val id=uuid(s.getValue("documentId"));snapshot.revisions.filter { it.key.document.projectId==project && it.key.document.documentId==id }.sortedBy { it.key.revisionId.toString() }.map { obj("revisionKey" to revisionKey(it.key),"revisionDigest" to digest(it.digest)) } }
                    "list_requests" -> { val pending=(s.getValue("pendingOnly") as? JBoolean)?.value ?: throw H1ReadFault("INVALID_INPUT")
                        snapshot.requests.values.filter { it.projectId==project && (!pending || it.id !in snapshot.resolutions) }.sortedBy { it.id.toString() }.map { obj("id" to uuid(it.id),"requestDigest" to digest(it.digest),"packetDigest" to digest(it.packet.digest),"pending" to JBoolean(it.id !in snapshot.resolutions)) } }
                    else -> snapshot.manifests.values.filter { it.projectId==project }.sortedBy { it.id.toString() }.map { obj("id" to uuid(it.id),"consumerId" to uuid(it.consumerId)) }
                }
            }.let { basis=it.first;it.second }
        } else when(op) {
            "read_revision" -> {
                val q=sub.fields("revisionKey","revisionDigest");val target=revisionKey(q.getValue("revisionKey"));val hash=digest(q.getValue("revisionDigest"))
                protected.authorizeResources(base,key,project,op,listOf(ProtectedResource.Revision(target,hash)))
                val r=snapshot.revisions.firstOrNull { it.key==target && target.document.projectId==project } ?: throw H1ReadFault("NOT_FOUND")
                if(r.digest!=hash)throw H1ReadFault("INVALID_INPUT")
                protected.authorizeResources(base,key,project,op,listOf(ProtectedResource.Revision(target,hash))+
                    (r.structure.clauses.map { r.clauseReference(it.id) }+if(r.structure.context.isEmpty())emptyList() else listOf(r.contextReference())).map { ProtectedResource.Knowledge(it) })
                obj("revisionKey" to revisionKey(target),"revisionDigest" to digest(r.digest),"bodyDigest" to digest(r.content.digest),
                    "structure" to structure(r.structure),"body" to str(r.content.text()),"proposedBy" to uuid(snapshot.proposals.getValue(target).principalId))
            }
            "read_clause","read_context","read_approval_trace" -> {
                val ref=reference(sub)
                shape(op!="read_clause" || ref is ClauseReference);shape(op!="read_context" || ref is ContextReference)
                material(base,key,c,snapshot,ref,op)
            }
            "read_request","read_resolution","read_manifest" -> {
                val id=uuid(sub.fields("id").getValue("id"));val family=when(op) { "read_request" -> "requests";"read_resolution" -> "resolutions";else -> "manifests" }
                obj("record" to record(base,key,c,snapshot,family,id,op))
            }
            "read_review_bundle" -> {
                val q=sub.fields("id","requestDigest","packetDigest");val id=uuid(q.getValue("id"))
                // Whole packet records are not implicitly authorized by IDs in a trace. The exact
                // record itself must belong to the scope before any request existence is disclosed.
                val record=record(base,key,c,snapshot,"requests",id,op)
                val request=snapshot.requests.getValue(id)
                if(request.digest!=digest(q.getValue("requestDigest")) || request.packet.digest!=digest(q.getValue("packetDigest")))throw H1ReadFault("INVALID_INPUT")
                if(request.packet.informationGaps.isNotEmpty())throw H1ReadFault("INCOMPLETE_PACKET")
                val materials=request.packet.references.map { material(base,key,c,snapshot,it,op) }
                val blockers=mutableListOf<String>()
                if(id in snapshot.resolutions)blockers.add("ALREADY_RESOLVED") else when(val probe=base.core.resolve(Actor(c.credential.principalId),project,id,request.digest,request.packet.digest,StructuredAnswer.Approve)) {
                    is WorkflowResult.Failure -> blockers.add(probe.problem.name)
                    is WorkflowResult.Success -> Unit
                }
                obj("request" to record,"resolution" to (if(id in snapshot.resolutions)record(base,key,c,snapshot,"resolutions",id,op) else JNull),
                    "materials" to JArray(materials),"actionable" to JBoolean(blockers.isEmpty()),"blockers" to JArray(blockers.map(::str)),"complete" to JBoolean(true))
            }
            else -> throw H1ReadFault("INVALID_INPUT")
        }
        return obj("formatVersion" to num(1),"kind" to str("read"),"basis" to basis.json(),"operation" to str(op),"data" to data)
    }
    private fun record(base: H1BoundState,key: PresentedCredential,c: ScopedCredential,s: WorkflowSnapshot,family: String,id: UUID,op: String): JsonValue {
        val kind=when(family) { "requests" -> LegacyReadFamily.REQUEST;"resolutions" -> LegacyReadFamily.RESOLUTION;else -> LegacyReadFamily.MANIFEST }
        protected.authorizeLegacyRecord(base,key,c.credential.projectId,op,kind,id)
        val selected=CoreRecordCodec.records(s).firstOrNull { it.family==family && uuid(it.key)==id }
        val project=when(family) { "requests","resolutions" -> s.requests[id]?.projectId;else -> s.manifests[id]?.projectId }
        if(project!=c.credential.projectId || selected==null)throw H1ReadFault("NOT_FOUND")
        return CoreRecordCodec.envelope(selected.family,selected.key,selected.payload())
    }
    private fun material(base: H1BoundState,key: PresentedCredential,c: ScopedCredential,s: WorkflowSnapshot,ref: KnowledgeReference,op: String): JsonValue {
        protected.authorizeResources(base,key,c.credential.projectId,op,listOf(ProtectedResource.Knowledge(ref)))
        val catalog=RevisionCatalog(s.revisions)
        val fragments=when(ref) {
            is ClauseReference -> (catalog.read(ref) as? ReadResult.Found)?.value?.located?.let(::listOf)
            is ContextReference -> (catalog.read(ref) as? ReadResult.Found)?.value?.fragments
        } ?: throw H1ReadFault("NOT_FOUND")
        fun recorded(ref: KnowledgeReference,seen: Set<KnowledgeReference> = emptySet()): ApprovalTrace? {
            if(seen.size>=256 || ref in seen)throw H1ReadFault("UNAVAILABLE")
            s.approvals[ref]?.let { return it }
            val link=s.continuity[ref] ?: return null;val source=recorded(link.source,seen+ref) ?: return null
            return ApprovalTrace(source.origin,source.requestId,source.approvedBy,source.assessmentIds+link.assessmentId)
        }
        val observation=base.core.approvalTrace(Actor(c.credential.principalId),ref)
        val frozen=(c.scope as? CredentialScope.Assignment)?.let { scope ->
            val binding=base.authority.delegations.getValue(scope.delegationId).binding
            val subject=base.work.getValue(c.credential.projectId).subjectProjection(dev.writhelm.core.work.WAssignmentSubject(binding.assignmentId,binding.executionGeneration))
            s.manifests.getValue(subject.base.knowledgeManifestId).provenance[ref]
        }
        return obj("reference" to reference(ref),"fragments" to JArray(fragments.map { obj("span" to span(it.span),"text" to str(it.content.text())) }),
            "observation" to str(if(observation is WorkflowResult.Success)"APPROVED" else (observation as WorkflowResult.Failure).problem.name),
            "trace" to ((frozen ?: (observation as? WorkflowResult.Success)?.value ?: recorded(ref))?.let(::trace) ?: JNull))
    }
}
