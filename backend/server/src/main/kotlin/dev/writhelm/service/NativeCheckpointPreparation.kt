// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.nio.file.*

/** Resolve only the ORIGINAL service-bound work area from protected native preparation custody.
 * This is not current native liveness, writer fencing, or permission to prepare a running worker.
 * ConnectedWorkEngine must still establish its qualified native hold before any preparation. */
internal class NativeRecordedWorkArea(private val controllerRoot:Path,private val sourceRoot:Path,
    private val privateRoot:Path,private val epoch:String) {
    fun observe(capture:CoordinationCapture,subject:WAssignmentSubject):Path? {return try {
        val work=capture.work;val a=work.assignment(subject.assignmentId)
        if(subject.generation!=0L || a.executionGeneration!=0L || a.intendedExecution.controllerId!=capture.controller || a.binding==null)return null
        val layout=PassiveRuntimeLayout.read(controllerRoot) ?: return null
        val key=Digest.of(StrictJson.encode(JArray(listOf(uuid(subject.assignmentId),str(subject.generation.toString()))))).hex
        val root=controllerRoot.resolve("native")
        val original=protectedRuntimeBytes(root,root.resolve("worker-preparation-$key.json"),2*1024*1024)
        val prepared=StrictJson.decode(protectedRuntimeBytes(root,root.resolve("worker-prepared-$key.json"),2*1024*1024),
            JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,tokens=100000))
            .fields("formatVersion","kind","preparationJSON","nativeInfoJSON","contextCount","inboxCount","nativeRunning","workspaceDirectory","qualification")
        if(prepared.getValue("formatVersion").long()!=1L || prepared.getValue("kind").string()!="passive-receiver-observation" ||
            prepared.getValue("preparationJSON").string()!=original.toString(Charsets.UTF_8) ||
            prepared.getValue("contextCount").long()!=0L || prepared.getValue("inboxCount").long()!=0L || prepared.getValue("nativeRunning")!=JBoolean(false))return null
        val job=StrictJson.decode(original,JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,tokens=100000)).fields(
            "formatVersion","assignmentId","generation","intentId","workspaceId","intendedExecution","taskJSON","profileJSON",
            "baseJSON","snapshotJSON","artifactLocatorId","epoch","directory","agent","model")
        val profile=WorkCodec(modelVersion=work.modelVersion).decodeRecord(job.getValue("profileJSON").string().toByteArray())
        if(job.getValue("formatVersion").long()!=1L || uuid(job.getValue("assignmentId"))!=subject.assignmentId ||
            job.getValue("generation").string()!="0" || uuid(job.getValue("workspaceId"))!=a.workspaceId || job.getValue("epoch").string()!=epoch ||
            decodeWExecutionIntent(job.getValue("intendedExecution"))!=a.intendedExecution ||
            identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,job.getValue("taskJSON").string().toByteArray())!=a.task ||
            profile!=work.record(WorkFamily.PROFILES,a.profile))return null
        val base=work.task(a.task).base?.code?.singleOrNull() ?: return null
        val recordedBase=decodeWSnapshot(StrictJson.decode(job.getValue("baseJSON").string().toByteArray()))
        val recordedSnapshot=decodeWSnapshot(StrictJson.decode(job.getValue("snapshotJSON").string().toByteArray()))
        if(base.repositoryId!=layout.repository || recordedBase!=base || recordedSnapshot.repositoryId!=base.repositoryId ||
            recordedSnapshot.commit!=base.commit || recordedSnapshot.tree!=base.tree || recordedSnapshot.dirtyContentDigest!=null ||
            recordedSnapshot.retainedLocatorId!=uuid(job.getValue("artifactLocatorId")))return null
        val path=layout.worktreeParent.resolve(a.workspaceId.toString())
        if(job.getValue("directory").string()!=path.toString() || prepared.getValue("workspaceDirectory").string()!=path.toString() ||
            !Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) || path.toRealPath()!=path || Files.isSymbolicLink(path) ||
            Files.getAttribute(path,"unix:uid")!=Files.getAttribute(Path.of("/proc/self"),"unix:uid"))return null
        if(listOf(sourceRoot,privateRoot).any {path.startsWith(it)||it.startsWith(path)})return null
        val info=(StrictJson.decode(prepared.getValue("nativeInfoJSON").string().toByteArray()) as JObject).entries
        val location=(info.getValue("location") as JObject).entries
        if(info.getValue("id").string()!=a.intendedExecution.nativeSessionId || location.getValue("directory").string()!=path.toString() ||
            listOf("parentID","fork","revert").any {info[it]?.let {v->v!==JNull}==true})return null
        path
    }catch(_:Exception){null}}
}

/** Real first-generation unchanged-base continuation. The exact checkpoint is selected by the
 * canonical assignment, never latest-file guessing. Changed-base/rebind preparation needs its
 * explicit authorized edit plan and stays held; no merge or checkpoint fabrication is attempted. */
internal class NativeCheckpointPreparation(private val workArea:NativeRecordedWorkArea):ConnectedPreparation {
    private data class Selection(val capture:CoordinationCapture,val registration:OwnedPreparationRegistration,val rootIdentity:Any)
    private val selected=linkedMapOf<WAssignmentSubject,Selection>()
    @Synchronized override fun plan(capture:CoordinationCapture,subject:WAssignmentSubject):ConnectedPreparationPlan {
        Model2Inspector(capture.work).requireAssignmentContext(subject)
        val a=capture.work.assignment(subject.assignmentId)
        workRequire(a.executionGeneration==subject.generation && a.intendedExecution.controllerId==capture.controller,WorkProblem.INVALID_BINDING)
        val checkpoint=a.checkpoint ?: throw WorkFault(WorkProblem.INCOMPLETE_HANDOFF)
        Model2Inspector(capture.work).requireCheckpointContext(checkpoint,subject)
        val data=(capture.work.resolve(checkpoint).payload as WCheckpointsPayload).value
        val snapshot=data.artifacts.singleOrNull() ?: throw WorkFault(WorkProblem.NOT_READY)
        val root=workArea.observe(capture,subject) ?: throw WorkFault(WorkProblem.NOT_READY)
        val plan=ConnectedPreparationPlan(subject,subject,checkpoint,snapshot,snapshot,"unchanged-base",null,
            listOf(WSourceVariant2Value(WSourceVariant2("work",checkpoint))),emptyList())
        if(selected.size>=256 && subject !in selected)throw WorkFault(WorkProblem.NOT_READY)
        val registration=OwnedPreparationRegistration(root,plan)
        val checked=LinuxOwnedDirectoryPreparation(listOf(registration)).plan(capture,subject)
        val identity=Files.readAttributes(root,java.nio.file.attribute.BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS).fileKey()
            ?: throw WorkFault(WorkProblem.NOT_READY)
        selected[subject]=Selection(capture,registration,identity)
        return checked
    }
    private fun adapter(plan:ConnectedPreparationPlan):LinuxOwnedDirectoryPreparation? {
        val selection=selected[plan.assignment] ?: return null
        if(selection.registration.plan!=plan || workArea.observe(selection.capture,plan.assignment)!=selection.registration.root)return null
        val identity=try {Files.readAttributes(selection.registration.root,java.nio.file.attribute.BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS).fileKey()}catch(_:Exception){return null}
        if(identity!=selection.rootIdentity)return null
        return LinuxOwnedDirectoryPreparation(listOf(selection.registration))
    }
    @Synchronized override fun prepare(plan:ConnectedPreparationPlan):PreparationOutcome =
        adapter(plan)?.prepare(plan) ?: PreparationOutcome.Held("missing/changed exact work-area registration")
    @Synchronized override fun reconcile(plan:ConnectedPreparationPlan):PreparationOutcome =
        adapter(plan)?.reconcile(plan) ?: PreparationOutcome.Held("missing/changed exact work-area registration")
}
