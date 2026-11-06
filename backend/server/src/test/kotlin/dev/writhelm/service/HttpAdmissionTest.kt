// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.AppFixture
import dev.writhelm.core.app.plannerToken
import dev.writhelm.core.app.project
import dev.writhelm.core.app.revision
import dev.writhelm.core.codec.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import java.net.Socket
import java.io.IOException
import java.util.UUID
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlin.test.*

private val projectsRead=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to JNull,"operation" to str("list_projects"),"subject" to obj("cursor" to JNull,"pageSize" to num(8)))).decodeToString()
private class AdmissionFixture(val admission: HttpAdmission=HttpAdmission(),probe: suspend (HttpDeliveryEvent)->Unit={}): AutoCloseable {
    val fixture=AppFixture()
    private val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
    val origin="http://127.0.0.1:$port"
    val adapter=HttpAdapter(WrithelmService(fixture.app),HttpProfile(origin),beforeDelivery=probe,admission=admission)
    private val server=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
    private val client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    fun call(method: String,path: String,body: String="",token: String=plannerToken,media: String="application/json",headers: List<Pair<String,String>> = emptyList())=client.sendAsync(
        HttpRequest.newBuilder(URI(origin+path)).timeout(java.time.Duration.ofSeconds(10))
            .header("Authorization","Bearer $token").header("Content-Type",media).apply { headers.forEach { (k,v)->header(k,v) } }
            .method(method,if(body.isEmpty())HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
    override fun close() { runBlocking { adapter.mcp.close() };server.stop(0,5000);client.close();fixture.close() }
    suspend fun idle()=withTimeout(5000) { while(admission.snapshot().total!=0)yield() }
    fun socket()=Socket(InetAddress.getByName("127.0.0.1"),port).apply { soTimeout=5000 }
    fun partialHeaders(path: String,size: Int)="POST $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nAuthorization: Bearer $plannerToken\r\nContent-Type: application/json\r\nAccept: application/json, text/event-stream\r\nContent-Length: $size\r\nConnection: close\r\n\r\n"
}

/** Synthetic DELIVERY scheduling only; authentication/application/store/HTTP remain real. */
@Timeout(30)
class HttpAdmissionTest {
    @Test fun getFirstDoesNotExcludeStatefulPost()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val held=AtomicBoolean()
        AdmissionFixture { if(it.control && it.status==405 && held.compareAndSet(false,true)) { entered.complete(Unit);release.await() } }.use { f ->
            val get=f.call("GET","/mcp")
            try {
                withTimeout(5000) { entered.await() }
                assertEquals(200,f.call("POST","/api/v1/reads",projectsRead).get(5,TimeUnit.SECONDS).statusCode())
            } finally { release.complete(Unit);assertEquals(405,get.get(5,TimeUnit.SECONDS).statusCode()) }
        }
    }
    @Test fun postFirstDoesNotExcludeAuthenticatedGet()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val held=AtomicBoolean()
        AdmissionFixture { if(!it.control && it.status==200 && held.compareAndSet(false,true)) { entered.complete(Unit);release.await() } }.use { f ->
            val post=f.call("POST","/api/v1/reads",projectsRead)
            try {
                withTimeout(5000) { entered.await() }
                assertEquals(405,f.call("GET","/mcp").get(5,TimeUnit.SECONDS).statusCode())
            } finally { release.complete(Unit);assertEquals(200,post.get(5,TimeUnit.SECONDS).statusCode()) }
        }
    }
    @Test fun immutableReplyTailDoesNotHoldDispatch()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val held=AtomicBoolean()
        AdmissionFixture { if(!it.control && it.status==200 && held.compareAndSet(false,true)) { entered.complete(Unit);release.await() } }.use { f ->
            val first=f.call("POST","/api/v1/reads",projectsRead)
            try {
                withTimeout(5000) { entered.await() }
                assertEquals(200,f.call("POST","/api/v1/reads",projectsRead).get(5,TimeUnit.SECONDS).statusCode())
            } finally { release.complete(Unit);assertEquals(200,first.get(5,TimeUnit.SECONDS).statusCode()) }
        }
    }
    @Test fun fourthLifetimeIncludesDeliveryAndFifthRefuses()=runBlocking {
        val entered=Channel<Unit>(4);val release=CompletableDeferred<Unit>()
        AdmissionFixture { if(it.status==200) { entered.send(Unit);release.await() } }.use { f ->
            val calls=mutableListOf<java.util.concurrent.CompletableFuture<HttpResponse<String>>>()
            try {
                repeat(4) {
                    calls+=f.call("POST","/api/v1/reads",projectsRead)
                    withTimeout(5000) { entered.receive() }
                    assertEquals(it+1,f.admission.snapshot().total)
                    assertEquals(0,f.admission.snapshot().stateful)
                }
                assertEquals(429,f.call("GET","/mcp").get(5,TimeUnit.SECONDS).statusCode())
                assertEquals(4,f.admission.snapshot().total)
                assertEquals(0,f.fixture.app.captureRead(plannerToken).workflow.snapshot().revisions.size)
            } finally { release.complete(Unit);calls.forEach { assertEquals(200,it.get(5,TimeUnit.SECONDS).statusCode()) } }
            f.idle();assertEquals(0,f.admission.snapshot().bytes)
        }
    }
    @Test fun controlStillAuthenticatesAndHeaderErrorsReleaseOwnership()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val first=AtomicBoolean()
        AdmissionFixture { if(it.status==200 && first.compareAndSet(false,true)) { entered.complete(Unit);release.await() } }.use { f ->
            val post=f.call("POST","/api/v1/reads",projectsRead)
            try {
                withTimeout(5000) { entered.await() }
                assertEquals(401,f.call("GET","/mcp",token="invalid").get(5,TimeUnit.SECONDS).statusCode())
                assertEquals(403,f.call("GET","/mcp",headers=listOf("Origin" to "null")).get(5,TimeUnit.SECONDS).statusCode())
                assertEquals(400,f.call("GET","/mcp",headers=listOf("MCP-Session-Id" to "a","MCP-Session-Id" to "b")).get(5,TimeUnit.SECONDS).statusCode())
                assertEquals(405,f.call("GET","/mcp").get(5,TimeUnit.SECONDS).statusCode())
            } finally { release.complete(Unit);assertEquals(200,post.get(5,TimeUnit.SECONDS).statusCode()) }
            f.idle();assertEquals(0,f.admission.snapshot().control)
        }
    }
    @Test fun parseMediaAndProtocolRefusalsAreCompleteAndDoNotFenceDispatch()=runBlocking {
        AdmissionFixture().use { f ->
            val basis=f.fixture.app.captureRead(plannerToken).access.basis
            assertEquals(415,f.call("POST","/api/v1/reads","{}",media="text/plain").get(5,TimeUnit.SECONDS).statusCode());f.idle()
            assertEquals(400,f.call("POST","/api/v1/reads","{").get(5,TimeUnit.SECONDS).statusCode());f.idle()
            val malformed=f.call("POST","/mcp","{",headers=listOf("Accept" to "application/json, text/event-stream")).get(5,TimeUnit.SECONDS)
            assertEquals(200,malformed.statusCode());assertTrue(malformed.body().contains("-32700"));f.idle()
            assertFalse(f.admission.snapshot().fenced)
            assertEquals(200,f.call("POST","/api/v1/reads",projectsRead).get(5,TimeUnit.SECONDS).statusCode());f.idle()
            assertEquals(basis,f.fixture.app.captureRead(plannerToken).access.basis)
        }
    }
    @Test fun globalRateWindowIsSharedByControlAndStatefulWithoutSleeps()=runBlocking {
        val clock=AtomicLong();AdmissionFixture(HttpAdmission(clock::get)).use { f ->
            val basis=f.fixture.app.captureRead(plannerToken).access.basis
            repeat(32) { n ->
                val response=if(n%2==0)f.call("GET","/mcp") else f.call("POST","/api/v1/reads",projectsRead)
                assertEquals(if(n%2==0)405 else 200,response.get(5,TimeUnit.SECONDS).statusCode());f.idle()
            }
            assertEquals(32,f.admission.snapshot().messages)
            assertEquals(429,f.call("GET","/mcp").get(5,TimeUnit.SECONDS).statusCode());f.idle()
            assertEquals(basis,f.fixture.app.captureRead(plannerToken).access.basis)
            clock.set(1000000000L)
            assertEquals(405,f.call("GET","/mcp").get(5,TimeUnit.SECONDS).statusCode());f.idle()
            assertEquals(1,f.admission.snapshot().messages)
        }
    }
    @Test fun activeBodyOwnsStatefulSlotButNotGetControl()=runBlocking {
        AdmissionFixture().use { f ->
            val message="""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"synthetic","version":"1"}}}"""
            f.socket().use { socket ->
                val output=socket.getOutputStream();output.write((f.partialHeaders("/mcp",message.toByteArray().size)+message.take(1)).toByteArray());output.flush()
                try {
                    withTimeout(5000) { while(f.admission.snapshot().stateful!=1)yield() }
                    assertEquals(429,f.call("POST","/api/v1/reads",projectsRead).get(5,TimeUnit.SECONDS).statusCode())
                    assertEquals(405,f.call("GET","/mcp").get(5,TimeUnit.SECONDS).statusCode())
                    assertEquals(1,f.admission.snapshot().stateful)
                } finally { output.write(message.drop(1).toByteArray());output.flush() }
                assertTrue(socket.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 200"))
            }
            f.idle();assertFalse(f.admission.snapshot().fenced)
        }
    }
    @Test fun postPublicationDeliveryFailureIsUnknownNotRollbackAndSameKeyReplays()=runBlocking {
        val failed=AtomicBoolean()
        AdmissionFixture { if(it.status==200 && failed.compareAndSet(false,true))throw IOException("synthetic delivery failure") }.use { f ->
            val command=CommandCodec().encode(CoreCommand(UUID.randomUUID(),project,CommandPayload.Propose(revision(900)))).decodeToString()
            val lost=f.call("POST","/api/v1/commands",command).get(5,TimeUnit.SECONDS)
            assertEquals(503,lost.statusCode());assertTrue(lost.body().contains("PUBLICATION_UNKNOWN"));f.idle()
            assertEquals(1,f.fixture.app.captureRead(plannerToken).workflow.snapshot().revisions.size)
            val replay=f.call("POST","/api/v1/commands",command).get(5,TimeUnit.SECONDS)
            assertEquals(200,replay.statusCode());assertTrue(replay.body().contains("\"commandReplayed\":true"));f.idle()
            assertEquals(1,f.fixture.app.captureRead(plannerToken).workflow.snapshot().revisions.size)
            assertEquals(0,f.admission.snapshot().bytes)
        }
    }
    @Test fun cancelledDeliveryReleasesLifetimeAndDoesNotPoisonDispatch()=runBlocking {
        val once=AtomicBoolean()
        AdmissionFixture { if(it.status==200 && once.compareAndSet(false,true))throw java.util.concurrent.CancellationException("synthetic delivery cancellation") }.use { f ->
            val result=f.call("POST","/api/v1/reads",projectsRead)
            try { result.get(5,TimeUnit.SECONDS) } catch(_: java.util.concurrent.ExecutionException) { }
            f.idle();assertEquals(0,f.admission.snapshot().bytes)
            assertEquals(200,f.call("POST","/api/v1/reads",projectsRead).get(5,TimeUnit.SECONDS).statusCode());f.idle()
        }
    }
}

@Timeout(30)
class HttpAdmissionOwnershipTest {
    @Test fun fullPoolStillHasSmallPrimaryAndEmergencySpaceForAdmittedWork() {
        val a=HttpAdmission();val prior=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertTrue(prior.planBytes(12*1024*1024));prior.finishWork()
        val second=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertTrue(second.planBytes(4*1024*1024-4*HttpAdmission.SMALL_REPLY_BYTES));second.finishWork()
        val publishing=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertEquals(HttpAdmission.REPLY_BYTES,a.snapshot().bytes)
        assertTrue(publishing.planBytes(4096));publishing.finishWork()
        assertTrue(publishing.reserveFallback(512))
        assertEquals(HttpAdmission.REPLY_BYTES,a.snapshot().bytes)
        publishing.close();second.close();prior.close();assertEquals(0,a.snapshot().bytes)
    }
    @Test fun cancellationCannotReleaseContinuingProtectedWork()=runBlocking {
        val a=HttpAdmission();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val finalized=AtomicBoolean()
        val lease=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        val job=launch {
            try { lease.protectedWork { entered.complete(Unit);release.await();finalized.set(true) } }
            finally { lease.close() }
        }
        entered.await();job.cancel()
        try { assertEquals(1,a.snapshot().stateful);assertEquals(HttpAdmission.Refusal.STATEFUL,a.enter(HttpAdmission.Lane.STATEFUL).refusal) }
        finally { release.complete(Unit);job.join() }
        assertTrue(finalized.get());assertEquals(0,a.snapshot().total);assertEquals(0,a.snapshot().stateful)
    }
    @Test fun oneWorkPerLaneAndDeliveryKeepsTotal() {
        val a=HttpAdmission();val post=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertEquals(HttpAdmission.Refusal.STATEFUL,a.enter(HttpAdmission.Lane.STATEFUL).refusal)
        val get=a.enter(HttpAdmission.Lane.CONTROL).lease!!
        assertEquals(HttpAdmission.Refusal.CONTROL,a.enter(HttpAdmission.Lane.CONTROL).refusal)
        assertEquals(2,a.snapshot().total)
        post.finishWork();get.finishWork();assertEquals(0,a.snapshot().stateful);assertEquals(0,a.snapshot().control)
        val next=a.enter(HttpAdmission.Lane.STATEFUL).lease!!;next.finishWork()
        val fourth=a.enter(HttpAdmission.Lane.CONTROL).lease!!;fourth.finishWork()
        assertEquals(4,a.snapshot().total);assertEquals(HttpAdmission.Refusal.TOTAL,a.enter(HttpAdmission.Lane.STATEFUL).refusal)
        listOf(post,get,next,fourth).forEach { it.close();it.close() };assertEquals(0,a.snapshot().total);assertEquals(0,a.snapshot().bytes)
    }
    @Test fun aggregateWireBudgetAndFallbackAreBounded() {
        val a=HttpAdmission();val first=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertTrue(first.planBytes(12*1024*1024));first.finishWork()
        val second=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertFalse(second.planBytes(12*1024*1024));assertTrue(second.planBytes(4*1024*1024-2*HttpAdmission.SMALL_REPLY_BYTES));second.finishWork()
        assertEquals(HttpAdmission.REPLY_BYTES,a.snapshot().bytes)
        assertEquals(HttpAdmission.Refusal.BYTES,a.enter(HttpAdmission.Lane.CONTROL).refusal)
        assertFalse(first.reserveFallback(HttpAdmission.SMALL_REPLY_BYTES+1))
        assertTrue(first.reserveFallback(100));assertEquals(HttpAdmission.REPLY_BYTES,a.snapshot().bytes)
        second.close();assertEquals(12*1024*1024+HttpAdmission.SMALL_REPLY_BYTES,a.snapshot().bytes)
        first.close();assertEquals(0,a.snapshot().bytes)
    }
    @Test fun lifetimeClockAndUnsafeSdkFenceNeverReusesContext() {
        val clock=AtomicLong();val a=HttpAdmission(clock::get);val lease=a.enter(HttpAdmission.Lane.STATEFUL).lease!!
        assertEquals(90000L,lease.remainingMillis());clock.set(90000000000L);assertEquals(0L,lease.remainingMillis())
        lease.fence();lease.close()
        assertEquals(0,a.snapshot().total);assertEquals(0,a.snapshot().bytes);assertTrue(a.snapshot().fenced)
        assertEquals(HttpAdmission.Refusal.FENCED,a.enter(HttpAdmission.Lane.STATEFUL).refusal)
        val control=a.enter(HttpAdmission.Lane.CONTROL).lease!!;control.close()
    }
}

/** Actual unwrapped SDK sequence; response observer only, no scheduling barrier before list. */
class HttpAdmissionSdkTest {
    @Test @Timeout(45) fun coldSdkListAndOptionalGetBothSucceed()=runBlocking {
        AdmissionFixture().use { f ->
            val get=CompletableDeferred<Int>()
            val observer=createClientPlugin("admission_get_status_only") {
                onResponse { response -> if(response.call.request.method==HttpMethod.Get && response.call.request.url.encodedPath=="/mcp")get.complete(response.status.value) }
            }
            val http=io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) { install(SSE);install(observer) }
            val sdk=Client(Implementation("admission-synthetic-client","1"))
            val transport=StreamableHttpClientTransport(http,f.origin+"/mcp") { headers.append("Authorization","Bearer $plannerToken") }.apply { protocolVersion="2025-11-25" }
            try {
                sdk.connect(transport)
                assertEquals(setOf("writhelm_read","writhelm_command"),sdk.listTools().tools.map { it.name }.toSet())
                assertEquals(405,withTimeout(5000) { get.await() })
                transport.terminateSession()
                assertEquals(0,f.fixture.app.captureRead(plannerToken).workflow.snapshot().revisions.size)
            } finally { sdk.close();http.close() }
            f.idle()
        }
        Unit
    }
}
