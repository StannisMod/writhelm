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
            H1ObservedFacts(original.basis,original.facts,original.qualifiedProfiles+observed.profile,original.executionAdmission)
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
            knowledgeSources=original.facts.knowledgeSources+decisions,approvedDecisions=original.facts.approvedDecisions+decisions),original.qualifiedProfiles,original.executionAdmission)
    }
}

/** Candidate verification seam for a single reservation at its captured Git basis. Implementations
 * must derive these proofs from trusted qualification and packet custody, never command fields.
 * No production implementation is installed: missing proofs leave existing admission unchanged. */
internal data class ReservationProfileProof(val basis: GitOid,val project: UUID,val command: Digest,val profile: WorkReference)
internal data class ReservationPacketProof(val basis: GitOid,val project: UUID,val command: Digest,val task: WorkKey.TaskRevision,
    val manifest: WorkReference,val profile: WorkReference)
internal data class ReservationInputProof(val basis:GitOid,val project:UUID,val command:Digest,val task:WorkKey.TaskRevision,
    val manifest:WorkReference,val profile:WorkReference)
internal interface ReservationProofControl {
    fun profile(state: H1BoundState,command: WorkCommand): ReservationProfileProof?
    fun packet(state: H1BoundState,command: WorkCommand): ReservationPacketProof?
    fun inputs(state:H1BoundState,command:WorkCommand):ReservationInputProof?=null
}

/** Resolve receipt-derived attribution for an exact assessment subject at H. This only establishes
 * who recorded the evidence and its declared subject; the evidence's text, outcome or identity is
 * not by itself an independently qualified completeness assessment or permission to close a gap. */
internal fun attributedClosureEvidence(state:H1BoundState,project:UUID,inventory:PrebindingInputInventory,
    evidence:WorkReference,controllerPrincipal:UUID):H1EvidenceAttribution? {
    return try {
        if(evidence.identity.family!=WorkFamily.EVIDENCE || evidence.identity.key.projectId!=project)return null
        val work=state.work[project] ?: return null
        val attribution=state.evidence[evidence.identity] ?: return null
        if(attribution.reference!=evidence || attribution.principalId==controllerPrincipal)return null
        val delegation=state.authority.delegations[attribution.delegationId] ?: return null
        if(delegation.binding.projectId!=project || delegation.binding.principalId!=attribution.principalId)return null
        val data=(work.resolve(evidence).payload as? WEvidencePayload)?.value ?: return null
        val exact=inventory.dependencies+listOf(inventory.task,inventory.manifest)
        if(exact.any { ref -> data.subjects.none { (it as? WSourceVariant2Value)?.value?.reference==ref } })return null
        if(inventory.dependencies.any { ref ->
            val result=(work.resolve(ref).payload as? WResultsPayload)?.value ?: return null
            result.producer.principalId==attribution.principalId
        })return null
        attribution
    } catch(_:Exception) { null }
}
/** Historical attribution check over retained values; never re-runs a current provider and does
 * not establish the semantic or operational qualification of the recorded assessment. */
internal fun closureAttributionMatches(state:H1BoundState,project:UUID,inputs:PrebindingInputInventory,
    controllerPrincipal:UUID,record:WDispatchClosurePayload):Boolean {
    val source=record.value.assessment as? WSourceVariant2Value ?: return false
    val attribution=attributedClosureEvidence(state,project,inputs,source.value.reference,controllerPrincipal) ?: return false
    val evidence=try { state.work[project]?.resolve(source.value.reference)?.payload as? WEvidencePayload }
        catch(_:Exception) { null } ?: return false
    return evidence.value.outcome=="passed" && record.assessor==attribution.principalId && record.delegation==attribution.delegationId
}

/** A separately qualified assessor must return the exact subject it assessed at H. Receipt
 * attribution alone, a worker's outcome label, and structural inventory are not qualification. */
internal data class QualifiedClosureAssessment(val basis:GitOid,val materialization:WorkReference,
    val inputs:PrebindingInputInventory,val evidence:WorkReference,val assessor:UUID,val delegation:UUID)
internal fun interface ClosureAssessmentQualification {
    fun assess(state:H1BoundState,command:DispatchClosureCommand,inputs:PrebindingInputInventory,
        attribution:H1EvidenceAttribution):QualifiedClosureAssessment?
}

/** Historical verifier must derive its answer from durable qualification provenance available at
 * the original H. It must not consult a live assessor or infer qualification from attribution. */
internal fun interface HistoricalClosureQualification {
    fun verify(state:H1BoundState,command:DispatchClosureCommand,inputs:PrebindingInputInventory,
        attribution:H1EvidenceAttribution):QualifiedClosureAssessment?
}

/** Check the exact historical subject independently of the current runtime's observations.
 * This helper does not install a verifier or authorize publication. */
internal fun historicalClosureQualificationMatches(state:H1BoundState,command:DispatchClosureCommand,
    inputs:PrebindingInputInventory,controllerPrincipal:UUID,record:WDispatchClosurePayload,
    verifier:HistoricalClosureQualification):Boolean = try {
    val work=state.work.getValue(command.manifest.projectId)
    val materialization=(work.resolve(command.materialization).payload as WDispatchMaterializationPayload).value
    val task=work.task(materialization.newTask)
    val exact=PrebindingInputInventory(work.reference(WorkFamily.TASK_REVISIONS,materialization.newTask),
        work.reference(WorkFamily.INPUT_MANIFESTS,materialization.manifest),task.dependencies.map { requireNotNull(it.selectedResult) })
    if(inputs!=exact || record.value!=command || !closureAttributionMatches(state,command.manifest.projectId,inputs,controllerPrincipal,record)) false
    else {
        val evidence=(command.assessment as WSourceVariant2Value).value.reference
        val attribution=attributedClosureEvidence(state,command.manifest.projectId,inputs,evidence,controllerPrincipal)
        val expected=attribution?.let { QualifiedClosureAssessment(state.basis,command.materialization,inputs,evidence,it.principalId,it.delegationId) }
        command.expectedHead==WorkGitOid(state.basis.algorithm,state.basis.hex) && attribution!=null &&
            verifier.verify(state,command,inputs,attribution)==expected
    }
} catch(_:Exception) { false }

/** Builds a closure reducer input only after exact-H inventory, receipt-derived authorship and an
 * independent qualification provider all agree. No default provider is installed. The caller must
 * still authenticate the controller and publish with a same-head CAS and historical audit. */
internal fun verifiedDispatchClosureAssessment(state:H1BoundState,command:DispatchClosureCommand,
    facts:WorkExternalFacts,controllerPrincipal:UUID,qualification:ClosureAssessmentQualification):PrebindingClosureAssessment? {
    return try {
        val project=command.manifest.projectId
        if(command.expectedHead!=WorkGitOid(state.basis.algorithm,state.basis.hex))return null
        val work=state.work[project] ?: return null
        val materialization=(work.resolve(command.materialization).payload as? WDispatchMaterializationPayload)?.value ?: return null
        val inputs=prebindingMaterializedInputs(work,materialization.requestKey,facts)
        val source=command.assessment as? WSourceVariant2Value ?: return null
        val attribution=attributedClosureEvidence(state,project,inputs,source.value.reference,controllerPrincipal) ?: return null
        // A failed or inconclusive report cannot be qualified into a successful closure. A passed
        // label is necessary only: the separately qualified provider still checks its substance.
        val evidence=(work.resolve(source.value.reference).payload as? WEvidencePayload)?.value ?: return null
        if(evidence.outcome!="passed")return null
        val expected=QualifiedClosureAssessment(state.basis,command.materialization,inputs,source.value.reference,
            attribution.principalId,attribution.delegationId)
        if(qualification.assess(state,command,inputs,attribution)!=expected)return null
        PrebindingClosureAssessment(command.expectedHead,materialization.requestKey,inputs,command.assessment,
            attribution.principalId,attribution.delegationId)
    } catch(_:Exception) { null }
}

/** Check declared reservation inputs at the captured head. This does not create or remove a gap,
 * qualify a profile, prove a full assignment packet, or establish discovery of undeclared norms. */
internal class DeclaredPrebindingInputVerifier(private val observations:H1RuntimeFacts):ReservationProofControl {
    override fun profile(state:H1BoundState,command:WorkCommand):ReservationProfileProof?=null
    override fun packet(state:H1BoundState,command:WorkCommand):ReservationPacketProof?=null
    override fun inputs(state:H1BoundState,command:WorkCommand):ReservationInputProof? {
        return try {
            val op=(command.operation as? CReserveAssignmentOperation)?.value ?: return null
            val observed=observations.capture(state,command)
            if(observed.basis!=state.basis)return null
            val facts=observed.facts;val work=state.work.getValue(command.projectId)
            if(work.modelVersion!=4)return null
            val task=work.task(op.task)
            val manifest=(work.resolve(op.manifest).payload as? WInputManifestsPayload)?.value ?: return null
            val profile=work.reference(WorkFamily.PROFILES,op.profile)
            if(task.state!="executable" || task.unresolved.isNotEmpty() || task.executionProfile!=op.profile ||
                manifest.task!=op.task || manifest.base!=task.base || manifest.knownGaps.isNotEmpty() ||
                task.kind in setOf("implementation","remediation") && task.approvedDesign.isEmpty() ||
                task.approvedDesign.any { it !in facts.approvedDecisions })return null
            val base=task.base ?: return null
            if(base.code.any { it !in facts.retainedSnapshots })return null
            val applicability=(work.record(WorkFamily.APPLICABILITY,manifest.applicability).payload as? WApplicabilityPayload)?.value ?: return null
            if(applicability.entries.any { it.state!="applicable" })return null
            fun available(source:WSource):Boolean=when(source) {
                is WSourceVariant0Value -> source.value.reference in facts.knowledgeSources && state.core.validationSourceAvailable(source.value.reference)
                is WSourceVariant1Value -> source.value.artifact in facts.retainedSnapshots
                is WSourceVariant2Value -> {
                    val record=work.resolve(source.value.reference)
                    val result=(record.payload as? WResultsPayload)?.value
                    (result==null || result.artifacts.isNotEmpty() && result.artifacts.all { it in facts.retainedSnapshots } &&
                        WorkSubmittedDiff(result.task,result.artifacts,result.changes) in facts.submittedDiffs) &&
                        workLinks(record.payload).bodies.all { work.bodies[it.digest]?.sizeBytes==it.byteLength }
                }
            }
            if(!available(manifest.declaredClosureAssessment) || manifest.items.any { item ->
                !available(item.source) || item.exactFragments.any { work.bodies[it.digest]?.sizeBytes!=it.byteLength }
            })return null
            val required=applicability.entries.flatMap { listOf(it.source)+it.dependencies }+task.criteria.flatMap { it.clauses }
            if(required.any { requiredSource -> manifest.items.none { it.mandatory && it.source==requiredSource } })return null
            task.dependencies.forEach { dependency ->
                val ref=dependency.selectedResult ?: return null
                val result=(work.resolve(ref).payload as? WResultsPayload)?.value ?: return null
                if(result.task!=dependency.producer || manifest.items.none { it.mandatory && (it.source as? WSourceVariant2Value)?.value?.reference==ref })return null
            }
            val fingerprint=WorkCodec().fingerprint(command)
            ReservationInputProof(state.basis,command.projectId,fingerprint,op.task,op.manifest,profile)
        }catch(_:Exception){null}
    }
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
        val input=if(state.work.getValue(command.projectId).modelVersion==4)try {
            proofs.inputs(state,command)==ReservationInputProof(state.basis,command.projectId,expected.second.command,op.task,op.manifest,expected.second.profile)
        } catch(_:Exception) { false } else false
        val facts=original.facts.copy(verifiedPackets=if(packet)original.facts.verifiedPackets+op.manifest else original.facts.verifiedPackets,
            prebindingClosures=if(input)original.facts.prebindingClosures+WorkPrebindingClosure(op.task,op.manifest,op.profile,expected.second.command) else original.facts.prebindingClosures)
        return H1ObservedFacts(original.basis,facts,
            if(profile)original.qualifiedProfiles+expected.first.profile else original.qualifiedProfiles,original.executionAdmission)
    }
}

/** Trusted adapter output, not a worker assertion. `changes` is the digest and byte length of the
 * exact observed Git diff bytes, not a summary or an assessment of semantic correctness. */
internal data class NativeResultDiffProof(val basis:GitOid,val project:UUID,val command:Digest,
    val assignment:WAssignmentSubject,val executionId:UUID,val task:WorkKey.TaskRevision,val manifest:WorkReference,
    val base:WSnapshot,val result:WSnapshot,val changes:WorkBodyReference)
internal fun interface NativeResultDiffProofControl {
    fun observe(state:H1BoundState,command:WorkCommand):NativeResultDiffProof?
}
internal fun interface NativeResultDiffRawControl {
    fun resultDiffProof(assignment:UUID,generation:Long,result:WSnapshot):JsonValue?
}

/** Decode a protected host observation against the exact current assignment and retained original
 * preparation identity. This is read-only and supplies no snapshot-retention or fencing fact. */
internal class NativeResultDiffTransportProof(private val native:NativeResultDiffRawControl,private val epoch:String):NativeResultDiffProofControl {
    init { require(epoch.matches(Regex("[0-9a-f]{64}"))) }
    override fun observe(state:H1BoundState,command:WorkCommand):NativeResultDiffProof? {
      return try {
        val data=(command.operation as? CSubmitResultOperation)?.value?.data ?: return null
        val work=state.work.getValue(command.projectId);val assignment=work.assignment(data.assignment.assignmentId)
        val execution=assignment.execution ?: return null
        val base=work.task(assignment.task).base?.code?.singleOrNull() ?: return null
        val result=data.artifacts.singleOrNull() ?: return null
        if(data.assignment.generation!=0L || assignment.executionGeneration!=0L || assignment.state!="active")return null
        val raw=native.resultDiffProof(data.assignment.assignmentId,data.assignment.generation,result) ?: return null
        val frame=raw.fields("formatVersion","preparationJSON","resultJSON","diffSha256","diffByteLength")
        workRequire(frame.getValue("formatVersion").long()==1L,WorkProblem.INVALID_SUBJECT)
        val job=StrictJson.decode(frame.getValue("preparationJSON").string().toByteArray(),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024))
            .fields("formatVersion","assignmentId","generation","intentId","workspaceId","intendedExecution","taskJSON","profileJSON",
                "baseJSON","snapshotJSON","artifactLocatorId","epoch","directory","agent","model")
        workRequire(job.getValue("formatVersion").long()==1L && uuid(job.getValue("assignmentId"))==data.assignment.assignmentId &&
            job.getValue("generation").string()=="0" && uuid(job.getValue("workspaceId"))==assignment.workspaceId &&
            job.getValue("epoch").string()==epoch && decodeWExecutionIntent(job.getValue("intendedExecution"))==assignment.intendedExecution &&
            identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,job.getValue("taskJSON").string().toByteArray())==assignment.task &&
            decodeWSnapshot(StrictJson.decode(job.getValue("baseJSON").string().toByteArray()))==base &&
            decodeWSnapshot(StrictJson.decode(frame.getValue("resultJSON").string().toByteArray()))==result,WorkProblem.INVALID_SUBJECT)
        val size=frame.getValue("diffByteLength").long()
        workRequire(size in 0..(8L*1024*1024),WorkProblem.INVALID_SUBJECT)
        NativeResultDiffProof(state.basis,command.projectId,WorkCodec().fingerprint(command),data.assignment,execution.executionId,
            assignment.task,assignment.manifest,base,result,WorkBodyReference(Digest.parse(frame.getValue("diffSha256").string()),size.toInt()))
      } catch(_:Exception) { null }
    }
}

/** Fail-closed adapter; wiring a proof transport does not qualify a retention provider. A provider must
 * independently observe the worktree/diff and bind its proof to the exact command and H; separate
 * trusted facts must already establish retention of the exact result snapshot and locator.
 * It does not establish writer fencing, semantic scope, acceptance or integration. */
internal class NativeResultDiffFacts(private val delegate:H1RuntimeFacts,private val control:NativeResultDiffProofControl):H1RuntimeFacts {
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val original=delegate.capture(state,command)
        val submitted=(command.operation as? CSubmitResultOperation)?.value?.data ?: return original
        if(original.basis!=state.basis)return original
        val expected=try {
            val work=state.work.getValue(command.projectId)
            val assignment=work.assignment(submitted.assignment.assignmentId)
            val execution=assignment.execution ?: return original
            val base=work.task(assignment.task).base?.code?.singleOrNull() ?: return original
            val result=submitted.artifacts.singleOrNull() ?: return original
            if(assignment.state!="active" || assignment.executionGeneration!=submitted.assignment.generation ||
                assignment.task!=submitted.task || assignment.manifest!=submitted.manifest ||
                base.commit==null || result.commit==null || base.dirtyContentDigest!=null || result.dirtyContentDigest!=null ||
                base.repositoryId!=result.repositoryId || base.commit.algorithm!=result.commit.algorithm ||
                result !in original.facts.retainedSnapshots)return original
            NativeResultDiffProof(state.basis,command.projectId,WorkCodec().fingerprint(command),submitted.assignment,
                execution.executionId,submitted.task,submitted.manifest,base,result,submitted.changes)
        } catch(_:Exception) { return original }
        val observed=try { control.observe(state,command) } catch(_:Exception) { null }
        if(observed!=expected)return original
        return H1ObservedFacts(original.basis,original.facts.copy(
            submittedDiffs=original.facts.submittedDiffs+WorkSubmittedDiff(expected.task,listOf(expected.result),expected.changes)),original.qualifiedProfiles,original.executionAdmission)
    }
}

internal fun interface NativeResultRetentionRawControl {
    fun retentionProof(assignment:UUID,generation:Long,execution:UUID,result:WSnapshot):JsonValue?
}

/** A separately configured qualification must establish the retention profile's actual storage,
 * synchronization and recovery guarantees. Neither a native transport secret nor a ref supplies it. */
internal fun interface ResultRetentionQualification { fun qualified(state:H1BoundState,command:WorkCommand):Boolean }

/** Optional, fail-closed service adapter. Caller authentication and submit permission remain in the
 * ordinary work reduction; this is a read-only exact-result observation, never an effect dispatch. */
internal class NativeResultRetentionFacts(private val delegate:H1RuntimeFacts,private val native:NativeResultRetentionRawControl,
    private val qualification:ResultRetentionQualification):H1RuntimeFacts {
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val original=delegate.capture(state,command)
        if(original.basis!=state.basis)return original
        val data=(command.operation as? CSubmitResultOperation)?.value?.data ?: return original
        return try {
            val work=state.work.getValue(command.projectId);val assignment=work.assignment(data.assignment.assignmentId)
            val execution=assignment.execution ?: return original
            val delegation=assignment.delegationId?.let { state.authority.delegations[it] } ?: return original
            val credential=state.authority.credentials[execution.credentialId] ?: return original
            val base=work.task(assignment.task).base?.code?.singleOrNull() ?: return original
            val result=data.artifacts.singleOrNull() ?: return original
            if(assignment.state!="active" || data.assignment.generation!=0L || assignment.executionGeneration!=0L ||
                assignment.task!=data.task || assignment.manifest!=data.manifest ||
                execution.executionId!=assignment.intendedExecution.executionId ||
                delegation.status!=CredentialStatus.ACTIVE || credential.credential.status!=CredentialStatus.ACTIVE ||
                credential.credential.replacedBy!=null || credential.scope!=CredentialScope.Assignment(delegation.id) ||
                delegation.binding.assignmentId!=data.assignment.assignmentId || delegation.binding.executionGeneration!=data.assignment.generation ||
                delegation.binding.executionId!=execution.executionId || base.commit==null || result.commit==null ||
                base.dirtyContentDigest!=null || result.dirtyContentDigest!=null || base.repositoryId!=result.repositoryId ||
                base.commit.algorithm!=result.commit.algorithm || !qualification.qualified(state,command))return original
            val raw=native.retentionProof(data.assignment.assignmentId,data.assignment.generation,execution.executionId,result) ?: return original
            if(raw===JNull)return original
            val frame=StrictJson.decode(raw.string().toByteArray(),JsonLimits(bytes=65536,stringBytes=32768))
                .fields("formatVersion","assignmentId","generation","executionId","snapshot")
            if(frame.getValue("formatVersion").long()!=1L || uuid(frame.getValue("assignmentId"))!=data.assignment.assignmentId ||
                frame.getValue("generation").string()!=data.assignment.generation.toString() ||
                uuid(frame.getValue("executionId"))!=execution.executionId || decodeWSnapshot(frame.getValue("snapshot"))!=result)return original
            H1ObservedFacts(original.basis,original.facts.copy(retainedSnapshots=original.facts.retainedSnapshots+result),original.qualifiedProfiles,original.executionAdmission)
        } catch(_:Exception) { original }
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
            retainedSnapshots=original.facts.retainedSnapshots+observed.second),original.qualifiedProfiles,original.executionAdmission)
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
            m.getValue("id").string()==profile.model.modelId &&
            (m["variant"]?.string()==profile.model.variant || profile.model.variant==null && m["variant"]?.string()=="default"),WorkProblem.INVALID_BINDING)
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
