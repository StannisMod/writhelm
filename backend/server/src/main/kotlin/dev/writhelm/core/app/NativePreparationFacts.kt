// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.CredentialStatus
import dev.writhelm.core.auth2.CredentialScope
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.KnowledgeReference
import dev.writhelm.core.store.GitOid
import dev.writhelm.core.work.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

internal data class PinnedNativeExecutable(val version: String,val digest: Digest)
internal data class ObservedNativeProcess(val version: String,val digest: Digest,val epoch: String)

/** Independently recapture the Linux peer process behind the protected transport. The executable
 * pin is trusted deployment input, never a model/command field. This proves only this finite
 * process identity, not profile qualification, a safe writer, or request-accounting behavior. */
internal object LinuxNativeProcessObserver {
    fun observe(pid: Long,nativeOrigin: String,claimedEpoch: String,pin: PinnedNativeExecutable): ObservedNativeProcess? { return try {
        if(System.getProperty("os.name")!="Linux" || pid<=0 || pid>Int.MAX_VALUE ||
            !claimedEpoch.matches(Regex("[0-9a-f]{64}")) || !pin.version.matches(Regex("[A-Za-z0-9._-]{1,64}")))return null
        val origin=Regex("http://(?:127\\.0\\.0\\.1|\\[::1\\]):([1-9][0-9]{0,4})").matchEntire(nativeOrigin) ?: return null
        val port=origin.groupValues[1].toInt()
        if(port !in 1..65535 || port==80)return null
        val process=ProcessHandle.of(pid).orElse(null) ?: return null
        if(!process.isAlive)return null
        val root=Path.of("/proc",pid.toString())
        if(Files.getAttribute(root,"unix:uid")!=Files.getAttribute(Path.of("/proc/self"),"unix:uid"))return null
        fun text(path: Path): String = Files.newInputStream(path).use {
            val bytes=it.readNBytes(4097);if(bytes.size>4096)throw IllegalStateException("oversized process metadata")
            bytes.toString(Charsets.UTF_8).trim()
        }
        fun start(): String {
            val stat=text(root.resolve("stat"));val end=stat.lastIndexOf(')')
            if(end<0)throw IllegalStateException("invalid process metadata")
            val fields=stat.substring(end+1).trim().split(Regex("\\s+"))
            return fields.getOrNull(19)?.takeIf { it.matches(Regex("[0-9]+")) } ?: throw IllegalStateException("missing process start")
        }
        val started=start();val executable=root.resolve("exe")
        val before=Files.readAttributes(executable,BasicFileAttributes::class.java)
        if(!before.isRegularFile || before.fileKey()==null || before.size() !in 1L..1073741824L)return null
        val hash=MessageDigest.getInstance("SHA-256");var size=0L
        Files.newInputStream(executable).use { input ->
            val buffer=ByteArray(65536)
            while(true) { val count=input.read(buffer);if(count<0)break;size+=count
                if(size>before.size())throw IllegalStateException("changed executable")
                hash.update(buffer,0,count) }
        }
        val actual=Digest.parse(hash.digest().joinToString("") { "%02x".format(it) })
        val after=Files.readAttributes(executable,BasicFileAttributes::class.java)
        if(actual!=pin.digest || size!=before.size() || before.fileKey()!=after.fileKey() || before.size()!=after.size() ||
            before.lastModifiedTime()!=after.lastModifiedTime() || !process.isAlive || start()!=started)return null
        val boot=text(Path.of("/proc/sys/kernel/random/boot_id"))
        if(!boot.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))return null
        val epoch=Digest.of(StrictJson.encode(JArray(listOf(str("native-linux-process-v1"),digest(actual),str(boot),JNumber(pid),str(started),str(nativeOrigin))))).hex
        if(epoch!=claimedEpoch)null else ObservedNativeProcess(pin.version,actual,epoch)
    } catch(_: Exception) { null } }
}

/** Trusted local qualification registry entry. It is not decoded from a model, command or profile.
 * The registrar is responsible for independently verifying the named actual checks before pinning
 * their exact artifact digests. A label, file presence or reported pass is not such verification. */
internal data class ProfileQualificationSubject(val project: UUID,val host: UUID,val runtimeEpoch: String,
    val runtimeVersion: String,val runtimeDigest: Digest,val configurationDigest: Digest,val profile: WorkReference)
internal data class RegisteredProfileQualification(val subject: ProfileQualificationSubject,val artifacts: Map<String,Digest>)
internal fun interface CurrentProfileEnvironment { fun observe(state: H1BoundState,command: WorkCommand): ProfileQualificationSubject? }

/** Real protected-file reader for independently registered qualification artifacts. The current
 * native environment must be separately observed; stored metadata cannot observe itself. No default
 * registration or environment provider is installed. Linux only, cooperative same-user assurance. */
internal class LinuxRegisteredProfileFacts(private val delegate: H1RuntimeFacts,private val root: Path,
    registrations: Collection<RegisteredProfileQualification>,private val environment: CurrentProfileEnvironment): H1RuntimeFacts {
    private val registered=registrations.map { it.copy(artifacts=it.artifacts.toMap()) }
    companion object {
        internal val requiredChecks=setOf("native-permissions","mandatory-inputs","source-custody",
            "request-accounting","compaction","recovery")
    }
    init {
        require(System.getProperty("os.name")=="Linux" && root.isAbsolute && root.normalize()==root)
        require(registered.size<=256 && registered.map { it.subject }.distinct().size==registered.size)
        require(registered.all { it.artifacts.keys==requiredChecks && it.artifacts.values.toSet().size==requiredChecks.size &&
            it.subject.runtimeEpoch.matches(Regex("[0-9a-f]{64}")) &&
            it.subject.runtimeVersion.matches(Regex("[A-Za-z0-9._-]{1,64}")) && it.subject.profile.identity.family==WorkFamily.PROFILES &&
            it.subject.profile.identity.key.projectId==it.subject.project })
    }
    private fun retained(digest: Digest): Boolean {
        val uid=Files.getAttribute(Path.of("/proc/self"),"unix:uid")
        if(Files.isSymbolicLink(root) || !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || root.toRealPath()!=root ||
            Files.getAttribute(root,"unix:uid",LinkOption.NOFOLLOW_LINKS)!=uid ||
            (Files.getAttribute(root,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f!=0)return false
        val file=root.resolve("${digest.hex}.evidence")
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) ||
            Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)!=uid ||
            Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)!=1 ||
            (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f!=0)return false
        val before=Files.readAttributes(file,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
        if(before.size() !in 1L..1048576L)return false
        val bytes=Files.newByteChannel(file,setOf(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)).use {
            java.nio.channels.Channels.newInputStream(it).readNBytes(1048577)
        }
        val after=Files.readAttributes(file,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
        return before.fileKey()!=null && before.fileKey()==after.fileKey() && before.size()==after.size() &&
            before.lastModifiedTime()==after.lastModifiedTime() && bytes.size.toLong()==before.size() && Digest.of(bytes)==digest
    }
    override fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts {
        val original=delegate.capture(state,command)
        if(original.basis!=state.basis)return original
        val reserve=(command.operation as? CReserveAssignmentOperation)?.value ?: return original
        return try {
            val work=state.work.getValue(command.projectId)
            val record=work.record(WorkFamily.PROFILES,reserve.profile)
            val profile=(record.payload as? WProfilesPayload)?.value ?: return original
            val observed=environment.observe(state,command) ?: return original
            if(profile.platform!="linux" || profile.qualification!="qualified" || profile.qualificationEvidence.isEmpty() ||
                observed.project!=command.projectId || observed.host!=reserve.intendedExecution.nativeHostId ||
                observed.profile!=work.reference(WorkFamily.PROFILES,reserve.profile))return original
            val registration=registered.singleOrNull { it.subject==observed } ?: return original
            if(!registration.artifacts.values.all(::retained))return original
            H1ObservedFacts(original.basis,original.facts,original.qualifiedProfiles+observed.profile)
        } catch(_: Exception) { original }
    }
}

/** Derive only exact approved-design facts from the current core and the task's pinned knowledge
 * base. This proves neither native profile qualification nor packet completeness or possession. */
internal class CanonicalTaskDecisionFacts(private val delegate: H1RuntimeFacts): H1RuntimeFacts {
    override fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts {
        val original=delegate.capture(state,command)
        if(original.basis!=state.basis)return original
        val task=try {
            when(val op=command.operation) {
                is CCreateTaskRevisionOperation -> op.value.data
                is CReserveAssignmentOperation -> state.work.getValue(command.projectId).task(op.value.task)
                else -> return original
            }
        } catch(_: Exception) { return original }
        val base=task.base ?: return original
        val decisions: Set<KnowledgeReference> = try {
            val snapshot=state.core.snapshot(8192)
            if(snapshot.manifests[base.knowledgeManifestId]?.projectId!=command.projectId)return original
            val pinned=pinnedKnowledge(snapshot,base)
            task.approvedDesign.filter { it.revision.document.projectId==command.projectId && it in pinned &&
                state.core.validationSourceAvailable(it) && state.core.validationDecisionApproved(it) }.toSet()
        } catch(_: Exception) { return original }
        return H1ObservedFacts(original.basis,original.facts.copy(
            knowledgeSources=original.facts.knowledgeSources+decisions,approvedDecisions=original.facts.approvedDecisions+decisions),original.qualifiedProfiles)
    }
}

/** Candidate verification seam for a single reservation at its captured Git basis. Implementations
 * must derive these proofs from trusted qualification and packet custody, never command fields.
 * No production implementation is installed: missing proofs leave existing admission unchanged. */
internal data class ReservationProfileProof(val basis: GitOid,val project: UUID,val command: Digest,val profile: WorkReference)
internal data class ReservationPacketProof(val basis: GitOid,val project: UUID,val command: Digest,val task: WorkKey.TaskRevision,
    val manifest: WorkReference,val profile: WorkReference)
internal interface ReservationProofControl {
    fun profile(state: H1BoundState,command: WorkCommand): ReservationProfileProof?
    fun packet(state: H1BoundState,command: WorkCommand): ReservationPacketProof?
}

/** Narrow candidate adapter; not a qualification provider or authority decision. It admits only
 * exact proofs tied to H and this canonical command and never turns an exception into success. */
internal class ReservationProofFacts(private val delegate: H1RuntimeFacts,private val proofs: ReservationProofControl): H1RuntimeFacts {
    override fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts {
        val original=delegate.capture(state,command)
        val op=(command.operation as? CReserveAssignmentOperation)?.value ?: return original
        if(original.basis!=state.basis)return original
        val expected=try {
            val work=state.work.getValue(command.projectId)
            val task=work.task(op.task)
            val profile=work.reference(WorkFamily.PROFILES,op.profile)
            val manifest=(work.resolve(op.manifest).payload as? WInputManifestsPayload)?.value ?: return original
            if(op.manifest.identity.family!=WorkFamily.INPUT_MANIFESTS || manifest.task!=op.task ||
                task.base!=manifest.base || task.executionProfile!=op.profile || manifest.knownGaps.isNotEmpty())return original
            val fingerprint=WorkCodec().fingerprint(command)
            ReservationProfileProof(state.basis,command.projectId,fingerprint,profile) to
                ReservationPacketProof(state.basis,command.projectId,fingerprint,op.task,op.manifest,profile)
        } catch(_: Exception) { return original }
        val profile=try { proofs.profile(state,command)==expected.first } catch(_: Exception) { false }
        val packet=try { proofs.packet(state,command)==expected.second } catch(_: Exception) { false }
        return H1ObservedFacts(original.basis,if(packet)original.facts.copy(verifiedPackets=original.facts.verifiedPackets+op.manifest) else original.facts,
            if(profile)original.qualifiedProfiles+expected.first.profile else original.qualifiedProfiles)
    }
}

internal fun interface NativePreparationProofControl { fun preparationProof(assignment:UUID,generation:Long):JsonValue? }

/** Private retained passive preparation producer. No caller/model `prepared=true`, source path,
 * scope label or native status is accepted as authority. Current profile/enrollment/command policy
 * stays in ordinary H1 reduction. This produces no start, completion or writer-fencing fact. */
internal class NativePreparationFacts(private val delegate:H1RuntimeFacts,private val native:NativePreparationProofControl,
    private val epoch:String):H1RuntimeFacts {
    init { require(epoch.matches(Regex("[0-9a-f]{64}"))) }
    private fun same(a:JsonValue,b:JsonValue)=StrictJson.encode(a,2*1024*1024).contentEquals(StrictJson.encode(b,2*1024*1024))
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val original=delegate.capture(state,command)
        val bind=(command.operation as? CBindAssignmentOperation)?.value ?: return original
        val observed=try { observe(state,command.projectId,bind) }catch(_:Exception){null} ?: return original
        return H1ObservedFacts(original.basis,original.facts.copy(native=original.facts.native+(bind.intentId to observed.first),
            retainedSnapshots=original.facts.retainedSnapshots+observed.second),original.qualifiedProfiles)
    }
    internal fun observe(state:H1BoundState,project:UUID,bind:CBindAssignment):Pair<WorkNativeObservation,WSnapshot>? {
        val work=state.work.getValue(project);val a=work.assignment(bind.assignmentId)
        if(a.executionGeneration!=0L)return null // later generations need their explicit rebind/recovery producer
        val raw=native.preparationProof(bind.assignmentId,a.executionGeneration) ?: return null
        val frame=raw.fields("formatVersion","preparationJSON","snapshotJSON","contextCount","inboxCount","nativeRunning","nativeJSON")
        workRequire(frame.getValue("formatVersion").long()==1L && frame.getValue("contextCount").long()==0L &&
            frame.getValue("inboxCount").long()==0L && frame.getValue("nativeRunning")==JBoolean(false),WorkProblem.INVALID_BINDING)
        val job=StrictJson.decode(frame.getValue("preparationJSON").string().toByteArray(),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024))
            .fields("formatVersion","assignmentId","generation","intentId","workspaceId","intendedExecution","taskJSON","profileJSON",
                "baseJSON","snapshotJSON","artifactLocatorId","epoch","directory","agent","model")
        val subject=WAssignmentSubject(bind.assignmentId,0)
        workRequire(job.getValue("formatVersion").long()==1L && uuid(job.getValue("assignmentId"))==bind.assignmentId && job.getValue("generation").string()=="0" &&
            uuid(job.getValue("intentId"))==bind.intentId && a.currentIntent==WorkKey.Id(project,bind.intentId) &&
            uuid(job.getValue("workspaceId"))==a.workspaceId && job.getValue("epoch").string()==epoch &&
            decodeWExecutionIntent(job.getValue("intendedExecution"))==a.intendedExecution && a.state=="reserved",WorkProblem.INVALID_BINDING)
        workRequire(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,job.getValue("taskJSON").string().toByteArray())==a.task &&
            WorkCodec().decodeRecord(job.getValue("profileJSON").string().toByteArray())==work.record(WorkFamily.PROFILES,a.profile),WorkProblem.INVALID_BINDING)
        val base=decodeWSnapshot(StrictJson.decode(job.getValue("baseJSON").string().toByteArray()))
        val snapshot=decodeWSnapshot(StrictJson.decode(frame.getValue("snapshotJSON").string().toByteArray()))
        workRequire(work.task(a.task).base?.code==listOf(base) && base.dirtyContentDigest==null && base.commit!=null &&
            snapshot==base.copy(retainedLocatorId=uuid(job.getValue("artifactLocatorId"))) &&
            job.getValue("snapshotJSON").string()==frame.getValue("snapshotJSON").string(),WorkProblem.INVALID_SUBJECT)
        val n=StrictJson.decode(frame.getValue("nativeJSON").string().toByteArray(),JsonLimits(bytes=65536)).fields(
            "sessionID","directory","parentID","fork","revert","agent","model","permissions","metadata")
        workRequire(n.getValue("sessionID").string()==a.intendedExecution.nativeSessionId && n.getValue("directory")==job.getValue("directory") &&
            n.getValue("parentID")===JNull && n.getValue("fork")===JNull && n.getValue("revert")===JNull && n.getValue("agent")==job.getValue("agent") &&
            same(n.getValue("permissions"),JArray(listOf(obj("action" to str("*"),"resource" to str("*"),"effect" to str("deny"))))),WorkProblem.INVALID_BINDING)
        val profile=(work.record(WorkFamily.PROFILES,a.profile).payload as WProfilesPayload).value
        val model=n.getValue("model") as? JObject ?: return null
        val m=model.fields(*(if("variant" in model.entries)arrayOf("providerID","id","variant")else arrayOf("providerID","id")))
        workRequire(same(model,job.getValue("model")) && m.getValue("providerID").string()==profile.model.providerId &&
            m.getValue("id").string()==profile.model.modelId && m["variant"]?.string()==profile.model.variant,WorkProblem.INVALID_BINDING)
        val metadata=n.getValue("metadata").fields("writhelm").getValue("writhelm").fields("kind","assignmentId","executionGeneration",
            "controllerId","executionId","workspaceId","taskJSON","profileKeyJSON")
        workRequire(metadata.getValue("kind").string()=="passive-preparation-v1" && uuid(metadata.getValue("assignmentId"))==bind.assignmentId &&
            metadata.getValue("executionGeneration").string()=="0" && uuid(metadata.getValue("controllerId"))==a.intendedExecution.controllerId &&
            uuid(metadata.getValue("executionId"))==a.intendedExecution.executionId && uuid(metadata.getValue("workspaceId"))==a.workspaceId &&
            metadata.getValue("taskJSON")==job.getValue("taskJSON") && identityCodec.decodeKey(WorkFamily.PROFILES,
                metadata.getValue("profileKeyJSON").string().toByteArray())==a.profile,WorkProblem.INVALID_BINDING)
        val delegation=a.delegationId?.let { state.authority.delegations[it] } ?: return null
        if(delegation.status!=CredentialStatus.ACTIVE)return null
        val key=state.authority.credentials.values.singleOrNull { it.scope==CredentialScope.Assignment(delegation.id) &&
            it.credential.status==CredentialStatus.ACTIVE && it.credential.replacedBy==null } ?: return null
        val b=delegation.binding
        workRequire(b.assignmentId==bind.assignmentId && b.executionGeneration==0L && b.executionId==a.intendedExecution.executionId &&
            b.controllerId==a.intendedExecution.controllerId && b.taskId==a.task.taskId,WorkProblem.INVALID_BINDING)
        val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,bind.intentId)).payload as WNativeIntentsPayload).value
        val execution=WExecution(b.executionId,b.principalId,key.credential.id,a.intendedExecution.nativeHostId,a.intendedExecution.nativeSessionId,a.workspaceId)
        val sources=listOf<WSource>(WSourceVariant1Value(WSourceVariant1("artifact",snapshot)))
        return WorkNativeObservation(subject,bind.intentId,intent.correlationId,"prepare","confirmed",sources,execution,false,"reserved") to snapshot
    }
}
