// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import dev.writhelm.service.SharedH1ControllerFacts
import dev.writhelm.service.HttpConnectedNativeControl
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Synthetic registrations only; real protected filesystem, no operational qualification claim. */
class RuntimeQualificationTest {
    private fun root()=Files.createTempDirectory("writhelm-registry-").also {
        Files.setPosixFilePermissions(it,PosixFilePermissions.fromString("rwx------"))
    }
    private fun write(path:Path,bytes:ByteArray) {
        Files.write(path,bytes)
        Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"))
    }
    private fun entry():RegisteredRuntimeQualification {
        val f=JointFixture();val command=f.reserveCommand()
        val subject=ProfileQualificationSubject(command.projectId,f.intended.nativeHostId,"a".repeat(64),"2.2.5",
            Digest.of("synthetic runtime".toByteArray()),Digest.of("synthetic config".toByteArray()),
            f.base.work.getValue(command.projectId).reference(WorkFamily.PROFILES,f.profile))
        return RegisteredRuntimeQualification(RegisteredProfileQualification(subject,
            LinuxRegisteredProfileFacts.requiredChecks.associateWith {Digest.of("synthetic $it".toByteArray())}),emptyMap())
    }
    @Test fun catalogueIsRereadAndRequiresExactCompleteUniqueSubjects() {
        val root=root();val registry=LinuxRuntimeQualificationRegistry(root);val e=entry()
        assertTrue(registry.registrations().isEmpty())
        val file=root.resolve("registry.json")
        write(file,LinuxRuntimeQualificationRegistry.encode(listOf(e)))
        assertEquals(listOf(e),registry.registrations())
        write(file,LinuxRuntimeQualificationRegistry.encode(listOf(e,e)))
        assertFails {registry.registrations()}
        write(file,LinuxRuntimeQualificationRegistry.encode(listOf(e.copy(execution=e.execution.copy(artifacts=e.execution.artifacts-"recovery")))))
        assertFails {registry.registrations()}
        write(file,LinuxRuntimeQualificationRegistry.encode(listOf(e.copy(retentionArtifacts=mapOf("recovery" to Digest.of(byteArrayOf(1)))))))
        assertFails {registry.registrations()}
        write(file,"{}".toByteArray());assertFails {registry.registrations()}
    }
    @Test fun artifactMissingTamperedInsecureSymlinkAndHardlinkAllDeny() {
        val root=root();val registry=LinuxRuntimeQualificationRegistry(root)
        val bytes="synthetic retained evidence".toByteArray();val digest=Digest.of(bytes)
        val checks=mapOf("synthetic" to digest);val file=root.resolve("${digest.hex}.evidence")
        assertFalse(registry.retained(checks))
        write(file,bytes);assertTrue(registry.retained(checks))
        write(file,"tampered".toByteArray());assertFalse(registry.retained(checks))
        write(file,bytes);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r--r--"))
        assertFalse(registry.retained(checks))
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"))
        val link=root.resolve("hardlink");Files.createLink(link,file);assertFalse(registry.retained(checks))
        Files.delete(link);Files.move(file,root.resolve("original"));Files.createSymbolicLink(file,root.resolve("original"))
        assertFalse(registry.retained(checks))
    }
    @Test fun insecureCatalogueAndDirectoryDenyWithoutChangingBytes() {
        val root=root();val registry=LinuxRuntimeQualificationRegistry(root);val file=root.resolve("registry.json")
        val bytes=LinuxRuntimeQualificationRegistry.encode(listOf(entry()));write(file,bytes)
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r-----"));assertFails {registry.registrations()}
        assertContentEquals(bytes,Files.readAllBytes(file))
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"))
        Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwxr-x---"));assertFails {registry.registrations()}
        assertContentEquals(bytes,Files.readAllBytes(file))
    }
    @Test fun executionNeedsCurrentProcessConfigurationAndExactNativeOperationNotCampaigns() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations()
        val work=f.base.work.getValue(f.task.projectId);val a=work.assignment(f.assignment)
        // Synthetic canonical paused/bound state; no native execution is performed by this test.
        val rows=work.records.values.map {if(it.payload is WAssignmentsPayload)it.copy(payload=WAssignmentsPayload(a.copy(state="bound")))else it}
        f.base=H1BoundState.checked(f.base.storeId,f.base.basis,f.base.core,f.base.authority.records(),
            mapOf(work.projectId to WorkState.restore(work.projectId,rows,work.bodies.values)))
        val command=WorkCommand(id(1920),work.projectId,CRequestNativeTransitionOperation(CRequestNativeTransition(
            f.assignment,a.generation,id(1921),id(1922),"start",null,emptyList())))
        val e=entry();val s=e.execution.subject
        val root=root();val registry=LinuxRuntimeQualificationRegistry(root)
        var process:ObservedNativeProcess?=ObservedNativeProcess(s.runtimeVersion,s.runtimeDigest,s.runtimeEpoch)
        var configuration:Digest?=s.configurationDigest
        var native=false
        val environment=ControllerProfileEnvironment(command.projectId,f.intended.controllerId,f.intended.nativeHostId,s.runtimeEpoch,
            {process},{configuration},NativeExecutionProbe {_,_,purpose->native&&purpose in setOf("bound-access","start")})
        val facts=ControllerPhaseAdmission(command.projectId,environment).executionFacts(UnavailableH1RuntimeFacts)
        fun admitted()=facts.capture(f.base,command).executionAdmission
        assertNull(admitted())
        write(root.resolve("registry.json"),LinuxRuntimeQualificationRegistry.encode(listOf(e)))
        assertNull(admitted(),"Historical catalogue cannot substitute for native restrictions")
        e.execution.artifacts.forEach {(name,digest)->write(root.resolve("${digest.hex}.evidence"),"synthetic $name".toByteArray())}
        assertNull(admitted())
        native=true
        assertEquals(NativeExecutionAdmission.expected(f.base,command),admitted())
        Files.delete(root.resolve("registry.json"))
        assertNotNull(admitted(),"Current verified operation does not need campaign registration")
        configuration=null;assertNull(admitted())
        configuration=s.configurationDigest;process=process!!.copy(epoch="b".repeat(64));assertNull(admitted())
        process=null;assertNull(admitted())
        val other=ControllerProfileEnvironment(command.projectId,java.util.UUID.randomUUID(),s.host,s.runtimeEpoch,
            {ObservedNativeProcess(s.runtimeVersion,s.runtimeDigest,s.runtimeEpoch)},{s.configurationDigest})
        assertNull(other.observe(f.base,command))
    }
    @Test fun retentionRequiresCurrentNativeObservationAndExactExecutionWithoutCampaigns() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations()
        val e=entry();val s=e.execution.subject;val root=root()
        val registry=LinuxRuntimeQualificationRegistry(root)
        var nativeAccess=false;var nativeRetention=false
        val environment=ControllerProfileEnvironment(s.project,f.intended.controllerId,s.host,s.runtimeEpoch,
            {ObservedNativeProcess(s.runtimeVersion,s.runtimeDigest,s.runtimeEpoch)},{s.configurationDigest},
            NativeExecutionProbe {_,_,purpose->when(purpose){"access"->nativeAccess;"retention"->nativeRetention;else->false}})
        val qualification=ControllerPhaseAdmission(s.project,environment)
        val subject=WAssignmentSubject(f.assignment,0)
        fun allowed()=qualification.qualified(f.base,s.project,subject,f.intended.executionId,f.snapshot)
        assertFalse(qualification.safeAccess(f.base,subject))
        e.execution.artifacts.forEach {(name,digest)->write(root.resolve("${digest.hex}.evidence"),"synthetic $name".toByteArray())}
        write(root.resolve("registry.json"),LinuxRuntimeQualificationRegistry.encode(listOf(e)))
        assertFalse(qualification.safeAccess(f.base,subject))
        nativeAccess=true
        assertTrue(qualification.safeAccess(f.base,subject))
        assertFalse(qualification.safeAccess(f.base,subject.copy(generation=1)))
        assertFalse(qualification.safeAccess(f.base,WAssignmentSubject(java.util.UUID.randomUUID(),0)))
        assertFalse(allowed())
        assertTrue(qualification.reservationInputs(f.base,f.reserveCommand()))
        val retention=LinuxRuntimeQualificationRegistry.retentionChecks.associateWith {Digest.of("synthetic retention $it".toByteArray())}
        write(root.resolve("registry.json"),LinuxRuntimeQualificationRegistry.encode(listOf(e.copy(retentionArtifacts=retention))))
        assertFalse(allowed())
        retention.forEach {(name,digest)->write(root.resolve("${digest.hex}.evidence"),"synthetic retention $name".toByteArray())}
        assertFalse(allowed(),"Stored pass labels are not current native effect admission")
        nativeRetention=true
        assertTrue(allowed())
        assertTrue(qualification.reservationInputs(f.base,f.reserveCommand()))
        assertFalse(qualification.qualified(f.base,s.project,subject,java.util.UUID.randomUUID(),f.snapshot))
        write(root.resolve("${retention.getValue("recovery").hex}.evidence"),"tampered".toByteArray())
        assertTrue(allowed())
        assertTrue(qualification.reservationInputs(f.base,f.reserveCommand()))
        assertTrue(qualification.safeAccess(f.base,subject),"Retention is a separate gate from execution access")
        write(root.resolve("${e.execution.artifacts.getValue("recovery").hex}.evidence"),"tampered".toByteArray())
        assertTrue(qualification.safeAccess(f.base,subject))
        nativeRetention=false;assertFalse(allowed())
        nativeAccess=false;assertFalse(qualification.safeAccess(f.base,subject))
    }
    @Test fun sharedNativeAccessRoutesOnlyToCanonicalCurrentControllerAndRemovesOnDisconnect() {
        ConnectedFixture().use { connected ->
            val f=JointFixture();f.activateWithAttributedFixtureObservations()
            val project=f.reserveCommand().projectId;val subject=WAssignmentSubject(f.assignment,0)
            val shared=SharedH1ControllerFacts();val safety=shared.options().nativeSafety
            assertFalse(safety.safeAccess(f.base,subject))
            HttpConnectedNativeControl("http://127.0.0.1:43199",ByteArray(32){1}).use { native ->
                var calls=0
                val mock=NativeRuntimeSafety {state,actual->calls++;state===f.base && actual==subject}
                shared.install(project,java.util.UUID.randomUUID(),native,connected.connected!!,
                    RetentionEffectQualification {_,_,_,_,_->false},mock)
                assertFalse(safety.safeAccess(f.base,subject));assertEquals(0,calls)
                shared.install(project,f.intended.controllerId,native,connected.connected!!,
                    RetentionEffectQualification {_,_,_,_,_->false},mock)
                assertTrue(safety.safeAccess(f.base,subject));assertEquals(1,calls)
                shared.remove(f.intended.controllerId)
                assertFalse(safety.safeAccess(f.base,subject));assertEquals(1,calls)
            }
        }
    }
}
