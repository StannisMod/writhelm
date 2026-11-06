// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.MechanismApp
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.GitOid
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Actual store/HTTP/Node controller/helper + DOM component. NOT an installed OpenCode host pass. */
class NativeControllerJourneyTest {
    @Test @Timeout(180) fun protectedSetupNativeComponentAndLostReplyReconciliation()=runBlocking {
        val base=Path.of(requireNotNull(System.getenv("WRITHELM_APP_TEST_ROOT")));Files.createDirectories(base)
        val root=Files.createTempDirectory(base,"native-journey-")
        val adapterDirectory=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER")))
        val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE")))
        val java=Path.of(System.getProperty("java.home"),"bin","java")
        val classpath=listOf(LocalSetup::class.java,kotlin.Unit::class.java,com.fasterxml.jackson.core.JsonFactory::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:$port"
        fun stage(mode: String): JsonValue {
            val input=obj("stage" to str(mode),"root" to str(root.toString()),"origin" to str(origin),
                "runtime" to obj("executable" to str(java.toString()),"classpath" to JArray(classpath.map(::str))))
            val process=ProcessBuilder(node.toString(),"--max-old-space-size=128","--v8-pool-size=1","--conditions=browser","build/tests/integration-client.js")
                .directory(adapterDirectory.toFile()).redirectError(root.resolve("$mode.stderr").toFile()).apply {
                    environment().clear();environment()["PATH"]=node.parent.toString()+":/usr/bin:/bin";environment()["UV_THREADPOOL_SIZE"]="1"
                }.start()
            process.outputStream.use { it.write(StrictJson.encode(input)) }
            val output=process.inputStream.readNBytes(4097)
            assertTrue(process.waitFor(90,TimeUnit.SECONDS),"Native fixture failed to complete; no process kill attempted")
            assertEquals(0,process.exitValue(),Files.readString(root.resolve("$mode.stderr")))
            assertTrue(output.size<=4096)
            return StrictJson.decode(output)
        }
        val prepared=stage("prepare").fields("storeId","storeDirectory","privateRoot")
        val app=MechanismApp.open(Path.of(prepared.getValue("storeDirectory").string()),uuid(prepared.getValue("storeId")))
        val http=HttpAdapter(WrithelmService(app),HttpProfile(origin))
        val server=embeddedServer(CIO,host="127.0.0.1",port=port) { http.install(this) }.start(wait=false)
        try {
            val result=stage("journey").fields("nativeComponentJourney","installedHost","requestId","originalAnswerReplayed","originalRotationReplayed","oldKeyDenied")
            assertEquals("passed",result.getValue("nativeComponentJourney").string())
            assertEquals(JBoolean(false),result.getValue("installedHost"))
            assertEquals(JBoolean(true),result.getValue("oldKeyDenied"))
            val evidence=Path.of(requireNotNull(System.getenv("API_EVIDENCE_ROOT")));Files.createDirectories(evidence)
            Files.write(evidence.resolve("native-real-journey.json"),StrictJson.encode(JObject(result)))
        } finally { http.mcp.close();server.stop(0,5000);app.close() }
        Unit
    }
}
