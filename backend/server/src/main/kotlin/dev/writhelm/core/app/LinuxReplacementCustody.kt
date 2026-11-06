// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** Qualified native custody reads this retained artifact and reinspects actual barrier/writer epochs.
 * It is not an RPC flag, claimed UUID/path, cached heartbeat or a proof from canonical metadata. */
internal data class ReinspectedIsolation(val retained: Utf8Content,val facts: FValidatedReplacementIsolation,val oldArea: Path,val plannedArea: Path,
    val resourceAreas: Map<WorkKey.Resource,Path>)
internal fun interface QualifiedIsolationSource {
    /** null means unavailable/unqualified/unknown; source and platform gates stay held. */
    fun readAndReinspect(capture: CoordinationCapture,command: CReplaceIsolatedAssignment): ReinspectedIsolation?
}

/** Concrete bounded physical inventory/custody half of R2. Native fencing qualification remains a
 * required separately supplied boundary; this adapter never manufactures it from directory names.
 * This initial adapter explicitly handles filesystem-area resources only, not unknown external IO. */
internal class LinuxReplacementCustody(private val source: QualifiedIsolationSource) {
    init { require(System.getProperty("os.name")=="Linux") { "unqualified replacement platform" } }
    fun capture(captured: CoordinationCapture,command: CReplaceIsolatedAssignment): Model2ReplacementProof? {
        val observation=source.readAndReinspect(captured,command) ?: return null
        val raw=observation.retained
        workRequire(raw.sizeBytes<=262144 && raw.digest==command.proof.digest,WorkProblem.DIGEST_MISMATCH)
        // FValidatedReplacementIsolation is DERIVED, not the retained artifact's wire format.
        // Otherwise embedding proof.digest in the bytes it hashes would require a hash fixed point.
        val p=decodeFValidatedReplacementIsolation(encodeFValidatedReplacementIsolation(observation.facts))
        val before=captured.work.record(WorkFamily.ASSIGNMENTS,command.old.key)
        val a=(before.payload as WAssignmentsPayload).value
        workRequire(p.basisH==WorkGitOid(captured.basis.algorithm,captured.basis.hex) && p.oldAssignment==command.old.key &&
            p.oldRecordDigest==WorkCodec().digest(before) && p.oldExecutionGeneration==a.executionGeneration && p.oldBinding==a.binding &&
            p.oldIntendedExecution==a.intendedExecution && p.oldWorkspaceId==a.workspaceId && p.proof.retainedLocatorId==command.proof.retainedLocatorId && p.proof.digest==command.proof.digest &&
            p.newAssignmentId==command.next.assignmentId && p.newExecutionGeneration==nextGeneration(a.executionGeneration) &&
            p.newIntendedExecution==command.next.intendedExecution && p.newWorkspaceId==command.next.workspaceId,WorkProblem.INVALID_BINDING)
        workRequire(p.allocationState=="planned-absent",WorkProblem.INVALID_SUBJECT)
        val old=observation.oldArea
        val planned=observation.plannedArea
        workRequire(old.isAbsolute && planned.isAbsolute && Files.isDirectory(old,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(old) &&
            !Files.exists(planned,LinkOption.NOFOLLOW_LINKS),WorkProblem.CLAIM_CONFLICT)
        val oldReal=old.toRealPath()
        val parent=planned.parent.toRealPath()
        val plannedReal=parent.resolve(planned.fileName).normalize()
        workRequire(!plannedReal.startsWith(oldReal) && !oldReal.startsWith(plannedReal),WorkProblem.CLAIM_CONFLICT)
        val descriptor=areaDescriptor(planned)
        workRequire(descriptor==p.newAreaDescriptorDigest,WorkProblem.DIGEST_MISMATCH)
        val required=(a.requiredResources+command.next.resources.map { it.resource }).toSet()
        workRequire(observation.resourceAreas.keys==required,WorkProblem.CLAIM_CONFLICT)
        val oldAreas=a.requiredResources.map { observation.resourceAreas.getValue(it).toRealPath() }
        command.next.resources.forEach { resource ->
            val candidate=observation.resourceAreas.getValue(resource.resource)
            val real=if(Files.exists(candidate,LinkOption.NOFOLLOW_LINKS))candidate.toRealPath() else candidate.parent.toRealPath().resolve(candidate.fileName).normalize()
            workRequire(oldAreas.none { real.startsWith(it) || it.startsWith(real) },WorkProblem.CLAIM_CONFLICT)
        }
        val claims=a.requiredResources.associateWith { (captured.work.record(WorkFamily.CLAIMS,it).payload as WClaimsPayload).value }
        val expectedSelectors=required.mapNotNull { key ->
            (captured.work.records[WorkRecordIdentity(WorkFamily.CLAIMS,key)]?.payload as? WClaimsPayload)?.value?.let { key to it.generation }
        }.toMap()
        workRequire(p.claims.associate { it.key to it.generation }==expectedSelectors &&
            p.isolatedResources.toSet()+p.retainedClaims.toSet()==a.requiredResources.toSet() &&
            p.pendingEventIds==command.continuation.pending.map { it.id },WorkProblem.INVALID_REFERENCE)
        return Model2ReplacementProof(command.proof.retainedLocatorId,raw.digest,before,command.next,claims,command.continuation.pending)
    }
    companion object {
        fun areaDescriptor(planned: Path): Digest {
            val parent=planned.parent.toRealPath()
            val attrs=Files.readAttributes(parent,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
            workRequire(attrs.isDirectory && attrs.fileKey()!=null,WorkProblem.NATIVE_UNKNOWN)
            return Digest.of(StrictJson.encode(obj("parent" to str(parent.toString()),"parentFileKey" to str(attrs.fileKey().toString()),
                "name" to str(planned.fileName.toString()),"allocation" to str("planned-absent"))))
        }
    }
}

/** Same captured-H adapter composition. No upload/boolean always-allow API; resource exhaustion
 * remains unavailable, while scoped mechanical refusals use the sealed WORK vocabulary. */
internal class ReplacementRuntimeFacts(private val delegate: H1RuntimeFacts,private val custody: LinuxReplacementCustody,
    private val controller: java.util.UUID): H1RuntimeFacts {
    override fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts {
        val original=delegate.capture(state,command)
        val op=(command.operation as? CReplaceIsolatedAssignmentOperation)?.value ?: return original
        val work=state.work[command.projectId] ?: throw WorkFault(WorkProblem.NOT_READY)
        var facts=original.facts.model2.copy(basis=WorkGitOid(state.basis.algorithm,state.basis.hex))
        try {
            custody.capture(CoordinationCapture(state.basis,controller,work),op)?.let { proof ->facts=facts.copy(replacements=mapOf(proof.digest to proof)) }
        } catch(e: WorkFault) { facts=facts.copy(replacementFailures=mapOf(op.proof.digest to e.problem)) }
        return H1ObservedFacts(state.basis,original.facts.copy(model2=facts),original.qualifiedProfiles)
    }
}
