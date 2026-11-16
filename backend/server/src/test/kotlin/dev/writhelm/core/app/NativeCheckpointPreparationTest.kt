// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import dev.writhelm.service.*
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Real canonical Git/service and filesystem; native preparation records are synthetic. */
class NativeCheckpointPreparationTest {
    @Test fun exactCheckpointPreservesDirtyBytesAndRejectsChangedCustody() {
        ConnectedFixture().use { f ->
            val root=f.root
            fun directory(name:String)=Files.createDirectories(root.resolve(name)).also {
                Files.setPosixFilePermissions(it,PosixFilePermissions.fromString("rwx------"))
            }
            fun write(path:Path,value:JsonValue) {
                Files.write(path,StrictJson.encode(value,2*1024*1024))
                Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"))
            }
            val controller=directory("private/controller");val bootstrap=directory("private/controller/bootstrap")
            val native=directory("private/controller/native");val source=directory("source");val parent=directory("worktrees")
            val area=parent.resolve(f.consumerWorkspace.toString());Files.move(f.consumerRoot,area)
            val user=area.resolve("user.txt");val userBytes=Files.readAllBytes(user)
            val capture=f.app.coordinationCapture(plannerToken,project);val work=capture.work
            val assignment=work.assignment(f.consumerId);val epoch="a".repeat(64)
            val executable=Path.of("/proc/self/exe").toRealPath()
            write(bootstrap.resolve("passive-profile-${Digest.of("current".toByteArray()).hex}.json"),obj(
                "formatVersion" to num(1),"repositoryId" to uuid(f.before.repositoryId),"worktreeParent" to str(parent.toString()),
                "git" to obj("path" to str(executable.toString()),"sha256" to digest(Digest.of(Files.readAllBytes(executable)))),
                "agents" to obj("executor-v1" to str("synthetic executor"),"reviewer-v1" to str("synthetic reviewer"))))
            val job=obj("formatVersion" to num(1),"assignmentId" to uuid(f.consumerId),"generation" to str("0"),
                "intentId" to uuid(f.consumerIntent),"workspaceId" to uuid(f.consumerWorkspace),
                "intendedExecution" to encodeWExecutionIntent(assignment.intendedExecution),
                "taskJSON" to str(identityCodec.encodeKey(assignment.task).toString(Charsets.UTF_8)),
                "profileJSON" to str(f.codec.encodeRecord(work.record(WorkFamily.PROFILES,assignment.profile)).toString(Charsets.UTF_8)),
                "baseJSON" to str(StrictJson.encode(encodeWSnapshot(work.task(assignment.task).base!!.code.single())).toString(Charsets.UTF_8)),
                "snapshotJSON" to str(StrictJson.encode(encodeWSnapshot(work.task(assignment.task).base!!.code.single().copy(retainedLocatorId=f.before.retainedLocatorId))).toString(Charsets.UTF_8)),
                "artifactLocatorId" to uuid(f.before.retainedLocatorId),"epoch" to str(epoch),"directory" to str(area.toString()),
                "agent" to str("synthetic executor"),"model" to JNull)
            val key=Digest.of(StrictJson.encode(JArray(listOf(uuid(f.consumerId),str("0"))))).hex
            val original=native.resolve("worker-preparation-$key.json");write(original,job)
            val receipt=native.resolve("worker-prepared-$key.json")
            fun observed(value:JObject)=obj("formatVersion" to num(1),"kind" to str("passive-receiver-observation"),
                "preparationJSON" to str(StrictJson.encode(value).toString(Charsets.UTF_8)),
                "nativeInfoJSON" to str(StrictJson.encode(obj("id" to str(f.consumerIntended.nativeSessionId),
                    "location" to obj("directory" to str(area.toString())))).toString(Charsets.UTF_8)),
                "contextCount" to num(0),"inboxCount" to num(0),"nativeRunning" to JBoolean(false),
                "workspaceDirectory" to str(area.toString()),"qualification" to str("synthetic"))
            write(receipt,observed(job))
            val resolver=NativeRecordedWorkArea(controller,source,root.resolve("private"),epoch)
            assertEquals(area,resolver.observe(capture,f.consumerSubject))
            val preparation=NativeCheckpointPreparation(resolver)
            val plan=preparation.plan(capture,f.consumerSubject)
            assertEquals(f.checkpoint,plan.sourceCheckpoint)
            assertIs<PreparationOutcome.Matched>(preparation.prepare(plan))
            assertIs<PreparationOutcome.Matched>(preparation.reconcile(plan))
            assertContentEquals(userBytes,Files.readAllBytes(user))
            val stale=JObject(job.entries+("epoch" to str("b".repeat(64))))
            write(original,stale);write(receipt,observed(stale))
            assertNull(resolver.observe(capture,f.consumerSubject))
            assertIs<PreparationOutcome.Held>(preparation.prepare(plan))
            assertContentEquals(userBytes,Files.readAllBytes(user))
            write(original,job);write(receipt,observed(job))
            val wrongBase=JObject(job.entries+("baseJSON" to str("{}")))
            write(original,wrongBase);write(receipt,observed(wrongBase))
            assertNull(resolver.observe(capture,f.consumerSubject))
            assertContentEquals(userBytes,Files.readAllBytes(user))
            write(original,job);write(receipt,observed(job))
            Files.writeString(user,"new user edit\n")
            assertIs<PreparationOutcome.Held>(preparation.prepare(plan))
            assertEquals("new user edit\n",Files.readString(user))
            assertNull(resolver.observe(capture,f.consumerSubject.copy(generation=1)))
            Files.write(user,userBytes)
            val preserved=parent.resolve("preserved-original-workspace")
            Files.move(area,preserved);Files.createDirectory(area)
            Files.list(preserved).use {paths->paths.forEach {Files.copy(it,area.resolve(it.fileName))}}
            assertIs<PreparationOutcome.Held>(preparation.prepare(plan))
            assertContentEquals(userBytes,Files.readAllBytes(preserved.resolve("user.txt")))
            Files.move(receipt,native.resolve("preserved-receipt"))
            assertIs<PreparationOutcome.Held>(preparation.reconcile(plan))
        }
    }
}
