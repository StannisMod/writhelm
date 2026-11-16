// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.KnowledgeReference
import dev.writhelm.core.work.*

/** Reobserve service-derived reviewer inputs before reservation. Not a packet/closure or permission
 * assertion: current process admission, the ordinary full closure verifier and actual object/diff
 * observations remain mandatory. No historical execution campaign is required for input reads. */
internal class NativeCandidateReviewInputFacts(private val delegate:H1RuntimeFacts,
    private val receipts:NativeCandidateReviewReceipts,private val native:HttpConnectedNativeControl,
    private val qualification:ControllerPhaseAdmission):H1RuntimeFacts {
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val original=delegate.capture(state,command)
        val reserve=(command.operation as? CReserveAssignmentOperation)?.value ?: return original
        if(original.basis!=state.basis || reserve.intendedExecution.template!="reviewer-v1" ||
            !qualification.reservationInputs(state,command))return original
        return try {
            var assembly:java.util.UUID?=null
            val candidate=receipts.reservationCandidate(state,command){id->assembly=id;native.candidateAssemblyProof(id)} ?: return original
            val work=state.work.getValue(command.projectId);val task=work.task(reserve.task)
            val manifest=(work.resolve(reserve.manifest).payload as WInputManifestsPayload).value
            val sources=manifest.items.map {it.source}+manifest.declaredClosureAssessment+task.criteria.flatMap {it.clauses}
            val resultRefs=(sources.mapNotNull {(it as? WSourceVariant2Value)?.value?.reference?.takeIf {ref->ref.identity.family==WorkFamily.RESULTS}}+
                task.dependencies.mapNotNull {it.selectedResult}).distinct()
            val results=resultRefs.map {(work.resolve(it).payload as WResultsPayload).value}
            require(results.size<=256 && results.all {it.artifacts.size==1})
            val snapshots=(task.base!!.code+candidate+sources.mapNotNull {(it as? WSourceVariant1Value)?.value?.artifact}+
                results.flatMap {it.artifacts}).distinct()
            require(snapshots.size<=1024)
            val request=StrictJson.encode(obj("snapshots" to JArray(snapshots.map(::encodeWSnapshot)),"results" to JArray(results.map {result->
                val base=work.task(result.task).base!!.code.single()
                obj("base" to encodeWSnapshot(base),"result" to encodeWSnapshot(result.artifacts.single()))
            })),1024*1024).toString(Charsets.UTF_8)
            val proof=native.candidateInputsProof(requireNotNull(assembly),request) ?: return original
            val frame=proof.fields("formatVersion","requestDigest","diffs")
            require(frame.getValue("formatVersion").long()==1L && digest(frame.getValue("requestDigest"))==Digest.of(request.toByteArray()))
            val diffs=frame.getValue("diffs").list()
            require(diffs.size==results.size)
            results.zip(diffs).forEach {(result,raw)->
                val diff=raw.fields("sha256","byteLength")
                require(digest(diff.getValue("sha256"))==result.changes.digest && diff.getValue("byteLength").long()==result.changes.byteLength.toLong())
            }
            val knowledge=retainedReviewerKnowledgeInputs(state,command.projectId,task.base,sources)
            if(receipts.reservationCandidate(state,command,native::candidateAssemblyProof)!=candidate ||
                !qualification.reservationInputs(state,command))return original
            H1ObservedFacts(original.basis,original.facts.copy(retainedSnapshots=original.facts.retainedSnapshots+snapshots,
                submittedDiffs=original.facts.submittedDiffs+results.map {WorkSubmittedDiff(it.task,it.artifacts,it.changes)},
                knowledgeSources=original.facts.knowledgeSources+knowledge),original.qualifiedProfiles,original.executionAdmission)
        }catch(_:Exception){original}
    }
}

/** Canonical immutable manifest/content verification, not a role label or a latest-document read. */
internal fun retainedReviewerKnowledgeInputs(state:H1BoundState,project:java.util.UUID,base:WBase,sources:List<WSource>):Set<KnowledgeReference> {
    val snapshot=state.core.snapshot(8192)
    require(snapshot.manifests[base.knowledgeManifestId]?.projectId==project)
    val pinned=pinnedKnowledge(snapshot,base)
    val knowledge=sources.mapNotNull {(it as? WSourceVariant0Value)?.value?.reference}.toSet()
    require(knowledge.all {it.revision.document.projectId==project && it in pinned && state.core.validationSourceAvailable(it)})
    return knowledge
}
