// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.*

/** Actual Git/auth/read2 -> Node protected human controller/IPC -> DOM component; native host MOCK. */
class NativeWorkPresentationTest {
    @Test @Timeout(120) fun serviceRecordsAppearWithoutOwnerSecretOrAutomaticWorkMutation() {
        ConnectedFixture(informationalNote=true).use { fixture ->
            val app=fixture.app
            val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"work-ui-")
            val work=Files.createDirectory(root.resolve("work"))
            val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val origin="http://127.0.0.1:$port";val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin))
            val server=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
            val before=app.inspectAccess(ownerToken).basis
            try {
                val cfg=obj("root" to str(root.toString()),"workRoot" to str(work.toString()),"origin" to str(origin),"storeId" to uuid(storeId),
                    "assignmentId" to uuid(fixture.consumerId),
                    "projectId" to uuid(project),"ownerPrincipal" to uuid(owner),"ownerKey" to uuid(ownerKey),"ownerToken" to str(ownerToken),
                    "agentPrincipal" to uuid(planner),"agentKey" to uuid(plannerKey),"agentToken" to str(plannerToken))
                val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("tests/work-presentation-client.js")
                val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128","--conditions=browser",script.toString())
                    .redirectError(root.resolve("client.stderr").toFile()).start()
                process.outputStream.use { it.write(StrictJson.encode(cfg,16384)) }
                val raw=process.inputStream.readNBytes(4097)
                assertTrue(process.waitFor(90,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),Files.readString(root.resolve("client.stderr")))
                val result=StrictJson.decode(raw).fields("workPresentation","nativeHost","service","secretInRenderer","automaticMutation")
                assertEquals("passed",result.getValue("workPresentation").string())
                assertEquals(JBoolean(false),result.getValue("secretInRenderer"));assertEquals(JBoolean(false),result.getValue("automaticMutation"))
                assertEquals(before,app.inspectAccess(ownerToken).basis)
            }finally{runBlocking { adapter.mcp.close() };server.stop(0,5000)}
        }
    }
}
