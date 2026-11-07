// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.CredentialStatus
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.util.UUID

/** Exact fixed assignment/base instructions, not a transcript or a replacement for residual
 * checkpoint extraction. Sources are selected from the existing delegated task/base closure;
 * mutable progress and controller-only blocker/assessment records are not recursively copied. */
internal object NativeAssignmentPacket {
    fun render(state: H1BoundState,project: UUID,subject: WAssignmentSubject): List<Pair<String,String>> {
        state.requireCurrentContinuation(project,subject)
        val work=state.work.getValue(project);val a=work.assignment(subject.assignmentId)
        val worker=state.authority.credentials.values.firstOrNull { it.scope==CredentialScope.Assignment(a.delegationId!!) &&
            it.credential.status==CredentialStatus.ACTIVE && it.credential.replacedBy==null } ?: throw WorkFault(WorkProblem.NOT_READY)
        val allowed=H1ProtectedReads().closure(state,worker).taskAndBase
        val knowledge=state.core.snapshot(8192)
        val pinned=pinnedKnowledge(knowledge,work.subjectProjection(subject).base)
        val designs=work.task(a.task).approvedDesign.toSet()
        val catalog=RevisionCatalog(knowledge.revisions)
        val entries=linkedMapOf<String,String>()
        fun add(kind: String,identity: ByteArray,body: JsonValue) {
            val text=StrictJson.encode(body,256*1024).decodeToString()
            val key="writhelm-$kind-${Digest.of(identity).hex}"
            workRequire(entries[key]==null || entries[key]==text,WorkProblem.DIGEST_MISMATCH)
            entries[key]=text
            workRequire(entries.size<=256 && entries.values.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }<=2*1024*1024,WorkProblem.INCOMPLETE_HANDOFF)
        }
        allowed.forEach { resource -> when(resource) {
            is ProtectedResource.ImmutableWork -> {
                workRequire(resource.reference.identity.family !in setOf(WorkFamily.CONDITIONS,WorkFamily.CONDITION_CONFIRMATIONS,
                    WorkFamily.CONTROLLER_CONTINUATIONS),WorkProblem.BLOCKED_INPUT)
                val r=work.resolve(resource.reference)
                add("record",identityCodec.encodeReference(resource.reference),StrictJson.decode(WorkCodec().encodeRecord(r,true),
                    JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000)))
            }
            is ProtectedResource.WorkBody -> {
                val body=work.bodies[resource.body.digest] ?: throw WorkFault(WorkProblem.MISSING_BODY)
                workRequire(body.sizeBytes==resource.body.byteLength,WorkProblem.DIGEST_MISMATCH)
                add("body",identityCodec.encodeBodyReference(resource.body),obj("digest" to digest(body.digest),"body" to str(body.text())))
            }
            is ProtectedResource.Knowledge -> {
                val ref=resource.reference
                workRequire(state.core.validationSourceAvailable(ref),WorkProblem.BLOCKED_INPUT)
                val fragments=when(ref) {
                    is ClauseReference -> listOf((catalog.read(ref) as? ReadResult.Found)?.value?.located ?: throw WorkFault(WorkProblem.BLOCKED_INPUT))
                    is ContextReference -> (catalog.read(ref) as? ReadResult.Found)?.value?.fragments ?: throw WorkFault(WorkProblem.BLOCKED_INPUT)
                }
                val role=when(ref) { in pinned -> "accepted-base-source";in designs -> "approved-design";else -> "declared-input-not-automatically-a-norm" }
                add("source",StrictJson.encode(reference(ref)),obj("reference" to reference(ref),"role" to str(role),"fragments" to JArray(fragments.map {
                    obj("start" to num(it.span.start),"end" to num(it.span.endExclusive),"text" to str(it.content.text()))
                })))
            }
            is ProtectedResource.LegacyRecord -> {
                val r=CoreRecordCodec.records(knowledge).single { it.family=="manifests" && uuid(it.key)==resource.id }
                val body=CoreRecordCodec.envelope(r.family,r.key,r.payload())
                workRequire(Digest.of(StrictJson.encode(body))==resource.digest,WorkProblem.DIGEST_MISMATCH)
                add("base",StrictJson.encode(body),body)
            }
            else -> Unit // Mutable assignment status/progress is restored separately, not frozen as base.
        } }
        workRequire(entries.isNotEmpty(),WorkProblem.INCOMPLETE_HANDOFF)
        return entries.toSortedMap().entries.map { it.key to it.value }
    }
}
