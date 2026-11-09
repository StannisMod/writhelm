// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.*

/** Actual canonical reserve/enrollment/bind, Git bytes and bidirectional HTTP; native SDK/profile
 * qualification is an explicit fixture, never operational/native writer fencing evidence. */
class NativeWorkerPreparationJourneyTest {
    @Test @Timeout(180) fun reservedTaskGetsExactPassiveReceiverAndScopedCredentialWithoutModelWake()=cycle(false)
    @Test @Timeout(180) fun firstNormalPromptLostReplyRequiresHeldBoundaryThenActiveAndNeverWakesTwice()=cycle(true)
    private fun cycle(start:Boolean) {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"passive-journey-")
        val source=Files.createDirectory(root.resolve("source"));val parent=Files.createDirectory(root.resolve("areas"),
            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")))
        fun git(vararg args:String):String {
            val process=ProcessBuilder("/usr/bin/git","-c","core.hooksPath=/dev/null","-C",source.toString(),*args).redirectError(root.resolve("git.stderr").toFile())
            process.environment().clear();process.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
            val p=process.start();val bytes=p.inputStream.readNBytes(8192);assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),Files.readString(root.resolve("git.stderr")))
            return bytes.decodeToString().trim()
        }
        git("init","-b","main");Files.writeString(source.resolve("counter.txt"),"counter=0\n");git("add","counter.txt")
        git("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","Synthetic counter baseline")
        val f=ReviewAppFixture();val codec=WorkCodec()
        val snapshot=WSnapshot(id(59500),WorkGitOid("sha1",git("rev-parse","HEAD")),WorkGitOid("sha1",git("rev-parse","HEAD^{tree}")),null,id(59501))
        val original=f.base.work.getValue(project);val base=requireNotNull(original.task(f.task).base).copy(code=listOf(snapshot))
        val records=original.records.values.map { row ->when(val payload=row.payload){
            is WTaskRevisionsPayload ->row.copy(payload=WTaskRevisionsPayload(payload.value.copy(base=base)))
            is WInputManifestsPayload ->row.copy(payload=WInputManifestsPayload(payload.value.copy(base=base)))
            else ->row
        } }
        val work=WorkState.restore(project,records,emptyList());val manifest=work.reference(WorkFamily.INPUT_MANIFESTS,f.manifestKey)
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+snapshot,verifiedPackets=setOf(manifest))
        fun port()=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:${port()}";val nativeOrigin="http://127.0.0.1:${port()}"
        val proof=ByteArray(32).also { SecureRandom().nextBytes(it) };val nativeProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        HttpConnectedNativeControl(nativeOrigin,nativeProof).use { control ->
            val observer=NativeInitialFacts(NativePreparationFacts(H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) },control,"a".repeat(64)),control,"a".repeat(64))
            H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to work)),
                H1Options(observer,nativeSafety=NativeRuntimeSafety { state,s ->s.generation==0L &&
                    state.work.getValue(project).assignment(s.assignmentId).let { it.task==f.task && it.intendedExecution.controllerId==f.controller } })).use { app ->
                val endpoint=WrithelmService(app).nativeEndpoint(project,f.controller,f.intended.nativeHostId,proof)
                val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),nativeEndpoint=endpoint)
                val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(origin).port) { adapter.install(this) }.start(wait=false)
                try{
                    val reserve=f.reserveCommand().copy(operation=CReserveAssignmentOperation((f.reserveCommand().operation as CReserveAssignmentOperation).value.copy(manifest=manifest)))
                    val cfg=obj("start" to JBoolean(start),"root" to str(root.toString()),"sourceRoot" to str(source.toString()),"worktreeParent" to str(parent.toString()),"repositoryId" to uuid(snapshot.repositoryId),
                        "origin" to str(origin),"transportOrigin" to str(nativeOrigin),"proof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(proof)),
                        "transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(nativeProof)),"storeId" to uuid(storeId),"projectId" to uuid(project),
                        "controllerId" to uuid(f.controller),"hostId" to uuid(f.intended.nativeHostId),"principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken),
                        "assignmentId" to uuid(f.assignment),"reserveJSON" to str(codec.encodeCommand(reserve).decodeToString()),
                        "workerPrincipalId" to uuid(f.worker),"workerCredentialId" to uuid(f.workerKey),"delegationId" to uuid(f.delegation),"enrollKey" to uuid(id(59502)),"bindKey" to uuid(id(59503)))
                    val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("tests/worker-preparation-client.js")
                    val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128",script.toString()).redirectError(root.resolve("client.stderr").toFile()).start()
                    process.outputStream.use { it.write(StrictJson.encode(cfg,65536)) }
                    val output=process.inputStream.readNBytes(4097);assertTrue(process.waitFor(120,TimeUnit.SECONDS))
                    assertEquals(0,process.exitValue(),Files.readString(root.resolve("client.stderr")))
                    val result=(StrictJson.decode(output) as JObject).entries
                    assertEquals(JBoolean(true),result.getValue("passiveAndBound"));assertEquals(if(start)1L else 0L,result.getValue("wakes").long())
                    val a=app.coordinationCapture(plannerToken,project).work.assignment(uuid(result.getValue("assignmentId")))
                    assertEquals(if(start)"active" else "bound",a.state);assertNotNull(a.binding);assertNotNull(a.delegationId)
                    assertEquals("counter=0\n",Files.readString(source.resolve("counter.txt")))
                }finally{runBlocking { adapter.mcp.close() };server.stop(0,5000);endpoint.close()}
            }
        }
    }
}
