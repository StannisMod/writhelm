// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import com.sun.net.httpserver.HttpServer
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import dev.writhelm.service.HttpConnectedNativeControl
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Actual JVM HTTP framing; receiving native/authority responses are explicitly MOCK here. */
class HttpConnectedNativeControlTest {
    @Test fun originalInt64BytesAndImmutableReconciliationUseSeparateRequests() {
        val proof=ByteArray(32) { 7 };val seen=CopyOnWriteArrayList<JObject>()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/native/v1") { exchange ->
            assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(proof),exchange.requestHeaders.getFirst("X-Writhelm-Native-Proof"))
            val request=StrictJson.decode(exchange.requestBody.use { it.readNBytes(4*1024*1024+1) }) as JObject
            seen.add(request)
            val result=when(request.entries.getValue("operation").string()) {
                "hold" -> JBoolean(false)
                "reconcile","deliver" -> str("unknown")
                else -> JNull
            }
            val bytes=StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("native-result"),"result" to result))
            exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val f=ReviewAppFixture();f.activateWithAttributedFixtureObservations()
            val binding=f.base.work.getValue(project).assignment(f.assignment).binding!!
            val envelope=ConnectedEnvelope(WorkKey.Id(project,id(49300)),WAssignmentSubject(f.assignment,Long.MAX_VALUE),binding,
                f.intended.nativeHostId,f.intended.nativeSessionId,"msg_exact","Blocker B resolved; continue.")
            HttpConnectedNativeControl("http://127.0.0.1:${server.address.port}",proof).use { client ->
                client.retainEnvelope(envelope,listOf("writhelm-task" to "fixture exact packet"))
                val target=seen.single().entries.getValue("target") as JObject
                assertEquals(envelope.bytes().decodeToString(),target.entries.getValue("rawJSON").string())
                assertTrue(target.entries.getValue("rawJSON").string().contains("9223372036854775807"))
                assertFalse(client.hold(envelope))
                val before=seen.size
                assertSame(NativeConnectedOutcome.Unknown,client.reconcile(envelope))
                assertEquals(before+1,seen.size)
                assertEquals("reconcile",seen.last().entries.getValue("operation").string())
                val t=ConnectedBindingTarget(envelope.assignment,binding,envelope.nativeHostId,envelope.nativeSessionId)
                client.rehold(t);client.pause(t,WorkKey.Id(project,id(49301)))
                assertNull(client.reconcilePause(t,WorkKey.Id(project,id(49301))))
                assertEquals(listOf("retain","prepare","hold","reconcile","retain","rehold","pause"),seen.map { it.entries.getValue("operation").string() })
            }
        } finally { server.stop(0);proof.fill(0) }
    }
}
