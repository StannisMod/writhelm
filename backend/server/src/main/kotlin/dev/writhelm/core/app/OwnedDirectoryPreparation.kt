// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

internal data class CoordinationCapture(val basis: dev.writhelm.core.store.GitOid,val controller: UUID,val work: WorkState)
internal data class ExactFileChange(val relative: String,val before: Digest?,val after: Utf8Content?)
internal data class ConnectedPreparationPlan(val origin: WAssignmentSubject,val assignment: WAssignmentSubject,
    val sourceCheckpoint: WorkReference,val before: WSnapshot,val after: WSnapshot,val mode: String,
    val rebind: WContinuationRebind?,val basis: List<WSource>,val changes: List<ExactFileChange>)
internal sealed interface PreparationOutcome {
    data class Matched(val before: WSnapshot,val after: WSnapshot,val basis: List<WSource>): PreparationOutcome
    data class Held(val reason: String): PreparationOutcome
    data class Unknown(val reason: String): PreparationOutcome
}
internal interface ConnectedPreparation {
    fun plan(capture: CoordinationCapture,subject: WAssignmentSubject): ConnectedPreparationPlan
    fun prepare(plan: ConnectedPreparationPlan): PreparationOutcome
    fun reconcile(plan: ConnectedPreparationPlan): PreparationOutcome
}
/** Explicit service-managed work-area registration. Paths never come from tool/RPC/model input.
 * Rebind changes are exact authorized preimage->retained-bytes edits, not a merge strategy. */
internal data class OwnedPreparationRegistration(val root: Path,val plan: ConnectedPreparationPlan)

/** Actual bounded filesystem adapter for an explicitly selected Linux cooperative profile. It does
 * not establish physical native writer fencing; the controller must hold a qualified safe boundary.
 * Unsupported platforms refuse before access rather than falling back to shell/default paths. */
internal class LinuxOwnedDirectoryPreparation(registrations: Collection<OwnedPreparationRegistration>): ConnectedPreparation {
    private val areas=registrations.associateBy { it.plan.assignment }
    init {
        require(System.getProperty("os.name")=="Linux") { "unqualified preparation platform" }
        require(areas.size==registrations.size)
        areas.values.forEach { registration ->
            require(registration.root.isAbsolute && Files.isDirectory(registration.root,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(registration.root))
            require(registration.plan.changes.size<=256)
        }
    }
    override fun plan(capture: CoordinationCapture,subject: WAssignmentSubject): ConnectedPreparationPlan {
        Model2Inspector(capture.work).requireAssignmentContext(subject)
        val registration=areas[subject] ?: throw WorkFault(WorkProblem.NOT_READY)
        val plan=registration.plan;val assignment=capture.work.assignment(subject.assignmentId)
        Model2Inspector(capture.work).requireCheckpointContext(plan.sourceCheckpoint,plan.origin)
        workRequire(assignment.executionGeneration==subject.generation && assignment.intendedExecution.controllerId==capture.controller,WorkProblem.INVALID_BINDING)
        val checkpoint=(capture.work.resolve(plan.sourceCheckpoint).payload as WCheckpointsPayload).value
        workRequire(checkpoint.assignment==plan.origin && plan.before in checkpoint.artifacts,WorkProblem.INCOMPLETE_HANDOFF)
        if(plan.mode=="unchanged-base")workRequire(plan.origin==subject && plan.before==plan.after && plan.changes.isEmpty(),WorkProblem.INVALID_SUBJECT)
        else {
            val rebind=plan.rebind ?: throw WorkFault(WorkProblem.INVALID_BINDING)
            workRequire(rebind.newBinding==assignment.binding && rebind.task==assignment.task && rebind.manifest==assignment.manifest && rebind.decisionSources.isNotEmpty(),WorkProblem.INVALID_BINDING)
            workRequire(capture.work.task(assignment.task).base!!.code.any { it.repositoryId==plan.after.repositoryId && it.tree==plan.after.tree },WorkProblem.INVALID_SUBJECT)
        }
        return plan
    }
    private fun path(root: Path,key: String): Path {
        require(key.isNotEmpty() && !key.startsWith('/') && '\\' !in key && key.split('/').none { it.isEmpty() || it in setOf(".","..",".git") })
        val path=key.split('/').fold(root) { p,part ->p.resolve(part) }.normalize()
        require(path.startsWith(root.normalize()))
        var cursor=root
        key.split('/').forEach { part ->cursor=cursor.resolve(part);require(!Files.isSymbolicLink(cursor)) { "symlink work path" } }
        return path
    }
    private fun files(root: Path): Map<String,Utf8Content> {
        val values=linkedMapOf<String,Utf8Content>();var entries=0;var bytes=0L
        Files.walkFileTree(root,object: SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path,attrs: BasicFileAttributes): FileVisitResult {
                if(++entries>4096)throw WorkFault(WorkProblem.NOT_READY)
                if(dir!=root && dir.fileName.toString()==".git")return FileVisitResult.SKIP_SUBTREE
                require(!Files.isSymbolicLink(dir))
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path,attrs: BasicFileAttributes): FileVisitResult {
                if(++entries>4096)throw WorkFault(WorkProblem.NOT_READY)
                if(file.fileName.toString()==".git")return FileVisitResult.CONTINUE
                require(attrs.isRegularFile && !attrs.isSymbolicLink)
                if(attrs.size()>4*1024*1024 || attrs.size()>64L*1024*1024-bytes)throw WorkFault(WorkProblem.NOT_READY)
                val content=Utf8Content.fromBytes(Files.readAllBytes(file));bytes+=content.sizeBytes
                val key=root.relativize(file).joinToString("/") { it.toString() }
                values[key]=content;return FileVisitResult.CONTINUE
            }
        })
        return values
    }
    private fun snapshot(expected: WSnapshot,files: Map<String,Utf8Content>): WSnapshot {
        val entries=files.entries.sortedBy { it.key }.map { (name,body) ->obj("path" to str(name),"digest" to digest(body.digest),"byteLength" to num(body.sizeBytes)) }
        return expected.copy(dirtyContentDigest=Digest.of(StrictJson.encode(JArray(entries),4*1024*1024)))
    }
    override fun reconcile(plan: ConnectedPreparationPlan): PreparationOutcome { return try {
        val registration=areas[plan.assignment] ?: return PreparationOutcome.Held("unregistered work area")
        if(registration.plan!=plan)return PreparationOutcome.Held("changed preparation subject")
        val root=registration.root
        val actual=files(root)
        val after=snapshot(plan.after,actual)
        if(after==plan.after)PreparationOutcome.Matched(plan.before,after,plan.basis)
        else PreparationOutcome.Unknown("work area is not the exact retained resulting snapshot")
    } catch(e: Exception) { PreparationOutcome.Unknown(e.message ?: "inventory unavailable") } }
    override fun prepare(plan: ConnectedPreparationPlan): PreparationOutcome { return try {
        val registration=areas[plan.assignment] ?: return PreparationOutcome.Held("unregistered work area")
        if(registration.plan!=plan)return PreparationOutcome.Held("changed preparation subject")
        val root=registration.root;val original=files(root)
        if(snapshot(plan.before,original)!=plan.before)return PreparationOutcome.Held("dirty/base conflict; all user bytes preserved")
        val candidate=original.toMutableMap()
        plan.changes.forEach { edit ->
            path(root,edit.relative)
            if(original[edit.relative]?.digest!=edit.before)return PreparationOutcome.Held("exact edit preimage conflict")
            if(edit.after==null)candidate.remove(edit.relative)else candidate[edit.relative]=edit.after
        }
        if(snapshot(plan.after,candidate)!=plan.after)return PreparationOutcome.Held("authorized resulting snapshot mismatch")
        // Reinspect the whole before image immediately before first write, not a cached digest claim.
        if(snapshot(plan.before,files(root))!=plan.before)return PreparationOutcome.Held("concurrent work-area change")
        plan.changes.forEach { edit ->
            val target=path(root,edit.relative)
            if(edit.after==null)Files.delete(target)
            else {
                Files.createDirectories(target.parent)
                val temporary=Files.createTempFile(target.parent,"writhelm-prepare-",".part")
                try { Files.write(temporary,edit.after.bytes());Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
                finally { Files.deleteIfExists(temporary) }
            }
        }
        reconcile(plan)
    } catch(e: Exception) { PreparationOutcome.Unknown(e.message ?: "preparation outcome unknown; reconcile before another edit") } }
    companion object {
        /** Actual bytes read from an explicitly supplied owned fixture/retained root, never hash-as-possession. */
        fun snapshot(root: Path,basis: WSnapshot): WSnapshot {
            val adapter=LinuxOwnedDirectoryPreparation(emptyList())
            return adapter.snapshot(basis,adapter.files(root))
        }
    }
}
