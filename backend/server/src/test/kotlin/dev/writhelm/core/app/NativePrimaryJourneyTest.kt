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

class NativePrimaryJourneyTest {
    @Test @Timeout(120) fun ordinaryObjectiveCanCreateDraftButCannotCodeOrPromoteUnapprovedImplementation() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"primary-ui-")
            val work=Files.createDirectory(root.resolve("work"));Files.writeString(work.resolve("README.md"),"UNCHANGED_USER_PROJECT")
            val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val origin="http://127.0.0.1:$port";val proof=ByteArray(32).also { SecureRandom().nextBytes(it) }
            val nativePort=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val nativeOrigin="http://127.0.0.1:$nativePort";val nativeProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
            var connected=0
            val endpoint=WrithelmService(app).nativeEndpoint(project,f.controller,f.intended.nativeHostId,proof) {
                HttpConnectedNativeControl(nativeOrigin,nativeProof).use { control ->
                    val observed=control.startupIdentity()
                    assertEquals(f.controller,uuid(observed.getValue("controllerId")))
                    assertEquals("ses_primary_component",observed.getValue("sessionID").string())
                    assertEquals(plannerKey,uuid(observed.getValue("credentialId")))
                    connected++
                }
            }
            val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),nativeEndpoint=endpoint)
            val server=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
            val original=f.base.work.getValue(project).task(f.task)
            val key=WorkKey.TaskRevision(project,id(58001),0)
            val draft=original.copy(kind="implementation",state="draft",objective="Clarify tournament tracker requirements before implementation",
                base=null,executionProfile=null,verificationPlan=null,approvedDesign=emptyList(),unresolved=listOf("Tournament formats, participant roles and tie-break rules require clarification and approved design"))
            val codec=WorkCodec()
            val draftCommand=codec.encodeCommand(WorkCommand(id(58002),project,CCreateTaskRevisionOperation(CCreateTaskRevision(key,draft,null,emptyList()))))
            val premature=codec.encodeCommand(WorkCommand(id(58003),project,CCreateTaskRevisionOperation(CCreateTaskRevision(key.copy(revision=1),
                original.copy(kind="implementation",approvedDesign=emptyList()),null,emptyList()))))
            try {
                val cfg=obj("root" to str(root.toString()),"workRoot" to str(work.toString()),"origin" to str(origin),"storeId" to uuid(storeId),
                    "projectId" to uuid(project),"controllerId" to uuid(f.controller),"hostId" to uuid(f.intended.nativeHostId),
                    "principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken),
                    "proof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(proof)),
                    "transportOrigin" to str(nativeOrigin),"transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(nativeProof)),
                    "draftJSON" to str(draftCommand.decodeToString()),"prematureJSON" to str(premature.decodeToString()))
                val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("tests/primary-governance-client.js")
                val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128",script.toString())
                    .redirectError(root.resolve("client.stderr").toFile()).start()
                process.outputStream.use { it.write(StrictJson.encode(cfg,65536)) }
                val result=process.inputStream.readNBytes(4097)
                assertTrue(process.waitFor(90,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),Files.readString(root.resolve("client.stderr")))
                assertEquals(JBoolean(true),(StrictJson.decode(result) as JObject).entries.getValue("draftCreated"))
                val state=app.coordinationCapture(plannerToken,project).work
                assertEquals("draft",state.task(key).state)
                assertEquals(1,connected)
                assertFalse(state.records.containsKey(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,key.copy(revision=1))))
                assertEquals("UNCHANGED_USER_PROJECT",Files.readString(work.resolve("README.md")))
            }finally{runBlocking { adapter.mcp.close() };server.stop(0,5000);endpoint.close()}
        }
    }
}
