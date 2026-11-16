// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.nio.channels.Channels
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import dev.writhelm.core.store.GitOid

/** Trusted, current-command observation, never a profile label or caller-provided pass flag. */
internal data class NativeExecutionAdmission(val basis:GitOid,val command:Digest,val binding:WorkReference,val profile:WorkReference) {
    companion object {
        fun expected(state:H1BoundState,command:WorkCommand):NativeExecutionAdmission?=try {
            val op=(command.operation as? CRequestNativeTransitionOperation)?.value
            if(op==null || op.transition !in setOf("start","resume"))null else {
                val work=state.work.getValue(command.projectId);val a=work.assignment(op.assignmentId)
                a.binding?.let {NativeExecutionAdmission(state.basis,WorkCodec().fingerprint(command),it,work.reference(WorkFamily.PROFILES,a.profile))}
            }
        }catch(_:Exception){null}
    }
}

/** Read-only native observation must not reenter service policy while capture owns the monitor. */
internal fun interface NativeExecutionProbe {
    fun observe(state:H1BoundState,subject:WAssignmentSubject,purpose:String):Boolean
}

/** Private service-registration input, never a model upload or a claim derived from a pass label.
 * The trusted registrar must independently verify the actual checks before retaining this entry. */
internal data class RegisteredRuntimeQualification(val execution:RegisteredProfileQualification,
    val retentionArtifacts:Map<String,Digest>)

/** Re-read original protected bytes; missing/insecure/changed files do not become observations. */
internal fun protectedRuntimeBytes(root:Path,file:Path,maximum:Int):ByteArray {
    require(System.getProperty("os.name")=="Linux" && root.isAbsolute && file.parent==root)
    val uid=Files.getAttribute(Path.of("/proc/self"),"unix:uid")
    require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root) && root.toRealPath()==root.normalize() &&
        Files.getAttribute(root,"unix:uid",LinkOption.NOFOLLOW_LINKS)==uid &&
        (Files.getAttribute(root,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0)
    val before=Files.readAttributes(file,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
    val metadata=Files.readAttributes(file,"unix:uid,mode,nlink,ctime",LinkOption.NOFOLLOW_LINKS)
    require(before.isRegularFile && !before.isSymbolicLink && before.fileKey()!=null && before.size() in 1L..maximum.toLong() &&
        Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==uid &&
        Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1 &&
        (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0)
    val bytes=Files.newByteChannel(file,setOf(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)).use {
        Channels.newInputStream(it).readNBytes(maximum+1)
    }
    val after=Files.readAttributes(file,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
    require(bytes.size.toLong()==before.size() && before.fileKey()==after.fileKey() && before.size()==after.size() &&
        before.lastModifiedTime()==after.lastModifiedTime() && before.creationTime()==after.creationTime() &&
        metadata==Files.readAttributes(file,"unix:uid,mode,nlink,ctime",LinkOption.NOFOLLOW_LINKS))
    return bytes
}

/** Historical protected catalogue reader retained for compatibility and its format/security tests.
 * It is not composed into EX-09 runtime admission. Missing catalogue supplies no historical facts. */
internal class LinuxRuntimeQualificationRegistry(val root:Path) {
    init {require(System.getProperty("os.name")=="Linux" && root.isAbsolute && root.normalize()==root)}
    companion object {
        val retentionChecks=setOf("retained-objects","synchronization","recovery")
        private fun checks(value:JsonValue):Map<String,Digest> = (value as JObject).entries.mapValues {digest(it.value)}
        fun encode(entries:List<RegisteredRuntimeQualification>):ByteArray=StrictJson.encode(obj("formatVersion" to num(1),
            "registrations" to JArray(entries.map { entry ->val s=entry.execution.subject
                obj("subject" to obj("projectId" to uuid(s.project),"hostId" to uuid(s.host),"epoch" to str(s.runtimeEpoch),
                    "version" to str(s.runtimeVersion),"runtimeDigest" to digest(s.runtimeDigest),"configurationDigest" to digest(s.configurationDigest),
                    "profile" to StrictJson.decode(identityCodec.encodeReference(s.profile))),
                    "executionArtifacts" to JObject(entry.execution.artifacts.mapValues {digest(it.value)}),
                    "retentionArtifacts" to JObject(entry.retentionArtifacts.mapValues {digest(it.value)}))
            })),2*1024*1024)
    }
    fun registrations():List<RegisteredRuntimeQualification> {
        val file=root.resolve("registry.json")
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return emptyList()
        val f=StrictJson.decode(protectedRuntimeBytes(root,file,2*1024*1024),JsonLimits(bytes=2*1024*1024,stringBytes=65536,collection=4096,tokens=100000))
            .fields("formatVersion","registrations")
        require(f.getValue("formatVersion").long()==1L)
        val entries=f.getValue("registrations").list();require(entries.size<=256)
        return entries.map { raw ->
            val e=raw.fields("subject","executionArtifacts","retentionArtifacts")
            val s=e.getValue("subject").fields("projectId","hostId","epoch","version","runtimeDigest","configurationDigest","profile")
            val subject=ProfileQualificationSubject(uuid(s.getValue("projectId")),uuid(s.getValue("hostId")),s.getValue("epoch").string(),
                s.getValue("version").string(),digest(s.getValue("runtimeDigest")),digest(s.getValue("configurationDigest")),
                identityCodec.decodeReference(StrictJson.encode(s.getValue("profile"))))
            val execution=checks(e.getValue("executionArtifacts"));val retention=checks(e.getValue("retentionArtifacts"))
            require(execution.keys==LinuxRegisteredProfileFacts.requiredChecks && execution.values.toSet().size==execution.size &&
                (retention.isEmpty() || retention.keys==retentionChecks && retention.values.toSet().size==retention.size))
            require(subject.runtimeEpoch.matches(Regex("[0-9a-f]{64}")) && subject.runtimeVersion.matches(Regex("[A-Za-z0-9._-]{1,64}")) &&
                subject.profile.identity.family==WorkFamily.PROFILES && subject.profile.identity.key.projectId==subject.project)
            RegisteredRuntimeQualification(RegisteredProfileQualification(subject,execution),retention)
        }.also { values ->require(values.map {it.execution.subject}.distinct().size==values.size) }
    }
    fun retained(artifacts:Map<String,Digest>):Boolean=try {
        artifacts.values.all {Digest.of(protectedRuntimeBytes(root,root.resolve("${it.hex}.evidence"),1048576))==it}
    }catch(_:Exception){false}
}

/** Independent current observation ports are supplied by trusted service startup, not catalogue
 * fields. Process incarnation and effective configuration are recaptured at each affected boundary. */
internal class ControllerProfileEnvironment(private val project:UUID,private val controller:UUID,private val host:UUID,
    private val epoch:String,private val process:()->ObservedNativeProcess?,private val configuration:()->Digest?,
    private val native:NativeExecutionProbe=NativeExecutionProbe {_,_,_->false}):CurrentProfileEnvironment {
    fun execution(state:H1BoundState,command:WorkCommand):NativeExecutionAdmission?=try {
        val op=(command.operation as? CRequestNativeTransitionOperation)?.value
        if(command.projectId!=project || op==null || op.transition !in setOf("start","resume"))null else {
            val a=state.work.getValue(project).assignment(op.assignmentId)
            val subject=WAssignmentSubject(op.assignmentId,a.executionGeneration)
            if(assignmentAccess(state,subject)==null || !native.observe(state,subject,op.transition))null
            else NativeExecutionAdmission.expected(state,command)
        }
    }catch(_:Exception){null}
    /** Passive preparation needs a current connected process and matching host/controller, not
     * inference configuration or historical execution/retention campaigns. */
    fun preparation(state:H1BoundState,subject:WAssignmentSubject):Boolean = try {
        val work=state.work.getValue(project)
        val a=work.assignment(subject.assignmentId)
        val profile=(work.record(WorkFamily.PROFILES,a.profile).payload as WProfilesPayload).value
        val actual=process()
        a.executionGeneration==subject.generation && a.state=="reserved" && a.execution==null && a.binding==null &&
            a.intendedExecution.controllerId==controller && a.intendedExecution.nativeHostId==host &&
            profile.platform=="linux" && actual!=null && actual.epoch==epoch && process()==actual
    }catch(_:Exception){false}
    fun reservation(state:H1BoundState,command:WorkCommand):Boolean = try {
        val op=(command.operation as? CReserveAssignmentOperation)?.value
        val work=state.work[project]
        val profile=op?.let {work?.record(WorkFamily.PROFILES,it.profile)?.payload as? WProfilesPayload}
        val actual=process()
        command.projectId==project && op!=null && op.intendedExecution.controllerId==controller &&
            op.intendedExecution.nativeHostId==host && profile?.value?.platform=="linux" &&
            actual!=null && actual.epoch==epoch && process()==actual
    }catch(_:Exception){false}
    fun profile(state:H1BoundState,key:WorkKey.Profile):ProfileQualificationSubject? {return try {
        if(key.projectId!=project)return null
        val work=state.work[project] ?: return null
        val reference=work.reference(WorkFamily.PROFILES,key)
        val p=(work.resolve(reference).payload as WProfilesPayload).value
        if(p.platform!="linux")return null
        val actual=process() ?: return null
        if(actual.epoch!=epoch)return null
        val config=configuration() ?: return null
        if(process()!=actual)return null
        ProfileQualificationSubject(project,host,actual.epoch,actual.version,actual.digest,config,reference)
    }catch(_:Exception){null}}
    override fun observe(state:H1BoundState,command:WorkCommand):ProfileQualificationSubject? {
        val reserve=(command.operation as? CReserveAssignmentOperation)?.value ?: return null
        if(command.projectId!=project || reserve.intendedExecution.controllerId!=controller || reserve.intendedExecution.nativeHostId!=host)return null
        return profile(state,reserve.profile)
    }
    fun assignment(state:H1BoundState,subject:WAssignmentSubject,execution:UUID):ProfileQualificationSubject? {return try {
        val a=state.work[project]?.assignment(subject.assignmentId) ?: return null
        val bound=a.execution ?: return null
        if(a.executionGeneration!=subject.generation || a.state!="active" || a.intendedExecution.controllerId!=controller ||
            a.intendedExecution.nativeHostId!=host || bound.executionId!=execution || bound.nativeHostId!=host)return null
        if(!native.observe(state,subject,"retention"))return null
        profile(state,a.profile)
    }catch(_:Exception){null}}
    fun assignmentAccess(state:H1BoundState,subject:WAssignmentSubject):ProfileQualificationSubject? {return try {
        val a=state.work[project]?.assignment(subject.assignmentId) ?: return null
        if(a.executionGeneration!=subject.generation || a.intendedExecution.controllerId!=controller ||
            a.intendedExecution.nativeHostId!=host || a.state !in setOf("reserved","bound","active","paused"))return null
        val execution=a.execution ?: return null
        if(execution.nativeHostId!=host || execution.executionId!=a.intendedExecution.executionId ||
            execution.nativeSessionId!=a.intendedExecution.nativeSessionId || execution.workspaceId!=a.workspaceId)return null
        if(!native.observe(state,subject,if(a.state=="bound")"bound-access" else "access"))return null
        profile(state,a.profile)
    }catch(_:Exception){null}}
}

/** Phase-specific current observations. Historical campaign registration grants no runtime access. */
internal class ControllerPhaseAdmission(private val project:UUID,
    private val environment:ControllerProfileEnvironment):RetentionEffectQualification,ResultRetentionQualification,NativeRuntimeSafety {
    override fun safeAccess(state:H1BoundState,subject:WAssignmentSubject):Boolean {return try {
        if(environment.preparation(state,subject))return true
        environment.assignmentAccess(state,subject)!=null
    }catch(_:Exception){false}}
    fun executionFacts(delegate:H1RuntimeFacts)=H1RuntimeFacts { state,command ->
        val original=delegate.capture(state,command)
        H1ObservedFacts(original.basis,original.facts,original.qualifiedProfiles,
            if(original.basis==state.basis)environment.execution(state,command) else null)
    }
    /** Admission to read-only input observation. Actual retained objects and exact diff bytes
     * are independently recaptured by the native input proof; this grants no retention effect. */
    fun reservationInputs(state:H1BoundState,command:WorkCommand):Boolean=environment.reservation(state,command)
    override fun qualified(state:H1BoundState,project:UUID,subject:WAssignmentSubject,execution:UUID,result:WSnapshot):Boolean {return try {
        if(project!=this.project || subject.generation!=0L || result.commit==null || result.dirtyContentDigest!=null)return false
        val a=state.work[project]?.assignment(subject.assignmentId) ?: return false
        val base=state.work.getValue(project).task(a.task).base?.code?.singleOrNull() ?: return false
        if(base.repositoryId!=result.repositoryId || base.commit==null || base.dirtyContentDigest!=null ||
            base.commit.algorithm!=result.commit.algorithm)return false
        environment.assignment(state,subject,execution)!=null
    }catch(_:Exception){false}}
    override fun qualified(state:H1BoundState,command:WorkCommand):Boolean {
        val data=(command.operation as? CSubmitResultOperation)?.value?.data ?: return false
        val assignment=state.work[command.projectId]?.assignment(data.assignment.assignmentId) ?: return false
        val execution=assignment.execution ?: return false
        val result=data.artifacts.singleOrNull() ?: return false
        return qualified(state,command.projectId,data.assignment,execution.executionId,result)
    }
}
