// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.work.*
import dev.writhelm.core.store.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.net.http.*
import com.sun.net.httpserver.HttpServer
import kotlin.test.*

/** REAL Git/AUTH/current-H/engine/JVM -> HTTP -> Node bridge/custody -> HTTP -> service.
 * External native SDK and physical-safe-access observer are explicitly deterministic MOCKs.
 * Not an installed host, physical-writer, actual server-incarnation or compaction qualification. */
class NativeBridgeJourneyTest {
    private fun port()=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
    /** Real local fault proxy: drain the ORIGINAL successful peer reply, then lose its delivery.
     * No synthetic alternate response or assertion of native completion is supplied to the engine. */
    private class DropFirstDelivery(private val destination: String,private val lost: (String)->Unit={}): AutoCloseable {
        private val client=HttpClient.newHttpClient()
        private val server=HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        val origin="http://127.0.0.1:${server.address.port}"
        @Volatile var dropped=false;private set
        init {
            server.createContext("/native/v1") { exchange ->
                val body=exchange.requestBody.use { it.readNBytes(4*1024*1024+1) }
                val request=StrictJson.decode(body,JsonLimits(bytes=4*1024*1024,stringBytes=2*1024*1024,depth=128,tokens=500000)) as JObject
                val outbound=HttpRequest.newBuilder(java.net.URI(destination+"/native/v1")).header("Content-Type","application/json")
                    .header("X-Writhelm-Native-Proof",exchange.requestHeaders.getFirst("X-Writhelm-Native-Proof"))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
                val reply=client.send(outbound,HttpResponse.BodyHandlers.ofByteArray())
                if(!dropped && request.entries.getValue("operation").string()=="deliver" && reply.statusCode()==200) {
                    dropped=true;lost(request.entries.getValue("key").string());exchange.close()
                } else {
                    exchange.sendResponseHeaders(reply.statusCode(),reply.body().size.toLong())
                    exchange.responseBody.use { it.write(reply.body()) }
                }
            }
            server.start()
        }
        override fun close() { server.stop(0);client.close() }
    }
    private class Peer(config: JObject,root: Path): AutoCloseable {
        private val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE")))
        private val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("tests/native-bridge-peer.js")
        private val process=ProcessBuilder(node.toString(),"--max-old-space-size=128",script.toString())
            .redirectError(root.resolve("peer-${System.nanoTime()}.stderr").toFile()).start()
        private val writer=process.outputStream.bufferedWriter()
        private val reader=process.inputStream.bufferedReader()
        init {
            writer.write(StrictJson.encode(config,16384).decodeToString()+"\n");writer.flush()
            val ready=StrictJson.decode(requireNotNull(reader.readLine()).toByteArray()).fields("ready","externalNative")
            assertEquals(JBoolean(true),ready.getValue("ready"));assertEquals("MOCK",ready.getValue("externalNative").string())
        }
        fun probe(): Map<String,JsonValue> {
            writer.write("{\"operation\":\"probe\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray()).fields("wakes","interrupts","admitted","instructions")
        }
        override fun close() {
            if(process.isAlive) { writer.write("{\"operation\":\"finish\"}\n");writer.flush() }
            assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Owned component peer failed to stop")
            assertEquals(0,process.exitValue())
            writer.close();reader.close()
        }
    }
    private fun config(f: ConnectedFixture,root: Path,serviceOrigin: String,transportOrigin: String,serviceProof: ByteArray,transportProof: ByteArray): JObject {
        val binding=requireNotNull(f.state().assignment(f.consumerId).binding)
        return obj("root" to str(root.toString()),"workRoot" to str(f.consumerRoot.toString()),"serviceOrigin" to str(serviceOrigin),
            "transportOrigin" to str(transportOrigin),"storeId" to uuid(storeId),"projectId" to uuid(project),
            "controllerId" to uuid(f.f.controller),"hostId" to uuid(f.f.intended.nativeHostId),
            "controllerPrincipal" to uuid(planner),"controllerKey" to uuid(plannerKey),"controllerToken" to str(plannerToken),
            "workerPrincipal" to uuid(id(2006)),"workerKey" to uuid(f.consumerKey),"workerToken" to str(token(f.consumerKey)),
            "controllerSessionID" to str("ses_connected_controller"),"sessionID" to str(f.consumerIntended.nativeSessionId),
            "serviceProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(serviceProof)),
            "transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(transportProof)),
            "subject" to obj("assignmentId" to uuid(f.consumerId),"executionGeneration" to str("0"),"bindingJSON" to str(identityCodec.encodeReference(binding).decodeToString())))
    }
    @Test @Timeout(240) fun realLocalConnectedCycleLostReplyAndAdapterRestartNeedNoPlannerRelayOrRepeatedWake()=cycle(false)
    @Test @Timeout(240) fun lostOriginalAndGuardAfterPossibleWakeRemainUnknownAcrossAdapterRestart()=cycle(true)
    private fun cycle(loseCustody: Boolean) {
        val base=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT")))
        val root=Files.createTempDirectory(base,"jvm-node-bridge-")
        val serviceProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val transportProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        // MOCK only: this does not qualify any real worker/process or original effect surface.
        ConnectedFixture(nativeSafety=NativeRuntimeSafety { _,subject -> subject==WAssignmentSubject(id(2004),0) }).use { f ->
            val serviceOrigin="http://127.0.0.1:${port()}";val transportOrigin="http://127.0.0.1:${port()}"
            val nativeEndpoint=WrithelmService(f.app).nativeEndpoint(project,f.f.controller,f.f.intended.nativeHostId,serviceProof)
            val http=HttpAdapter(WrithelmService(f.app),HttpProfile(serviceOrigin),nativeEndpoint=nativeEndpoint)
            val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(serviceOrigin).port) { http.install(this) }.start(wait=false)
            val config=config(f,root,serviceOrigin,transportOrigin,serviceProof,transportProof)
            try {
                DropFirstDelivery(transportOrigin) { key ->
                    if(loseCustody) {
                        val hash=dev.writhelm.core.knowledge.Digest.of(key.toByteArray(Charsets.UTF_8)).hex
                        listOf("native-original","native-delivery-guard").forEach { Files.delete(root.resolve("custody/$it-$hash.json")) }
                        val attempt=java.util.UUID.nameUUIDFromBytes("$key:delivery".toByteArray(Charsets.UTF_8))
                        Files.delete(f.root.resolve("custody/$attempt.dispatch"))
                    }
                }.use { fault -> HttpConnectedNativeControl(fault.origin,transportProof).use { control ->
                    f.installNative(control)
                    val deliveredHint=CountDownLatch(1)
                    val hintSubscription=f.app.onNativeObservation { deliveredHint.countDown() }
                    Peer(config,root).use { peer ->
                        val before=peer.probe();assertEquals(JBoolean(false),before.getValue("admitted"))
                        val (r,e)=f.repairAndEvidence();f.confirm(r,e)
                        f.startEngine() // only setup; thereafter engine owns the complete connected protocol
                        assertTrue(deliveredHint.await(10,TimeUnit.SECONDS),"Retained native observation must signal its actual service")
                        f.app.coordinationCapture(plannerToken,project) // wait for the callback's application monitor to leave
                        f.engine!!.awaitIdle()
                        assertTrue(fault.dropped)
                        val observed=peer.probe()
                        assertEquals(1L,observed.getValue("wakes").long())
                        if(loseCustody) {
                            assertNotEquals("active",f.state().assignment(f.consumerId).state)
                            assertEquals(JBoolean(false),observed.getValue("admitted"))
                            assertTrue(f.engine!!.holds.isNotEmpty())
                        } else {
                            assertEquals("active",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
                            assertEquals(JBoolean(true),observed.getValue("admitted"))
                        }
                        assertTrue(observed.getValue("instructions").long()>0)
                        f.engine!!.close();f.engine=null
                    }
                    hintSubscription.close()
                    // Controller adapter restart, with the MOCK native effects retained separately.
                    Peer(config,root).use peerRestart@ { peer ->
                        assertEquals(JBoolean(false),peer.probe().getValue("admitted"))
                        f.startEngine()
                        val restored=peer.probe()
                        assertEquals(1L,restored.getValue("wakes").long())
                        if(loseCustody) {
                            assertEquals(JBoolean(false),restored.getValue("admitted"));assertTrue(f.engine!!.holds.isNotEmpty())
                            val claims=f.state().assignment(f.consumerId).requiredResources.map { (f.state().record(WorkFamily.CLAIMS,it).payload as WClaimsPayload).value.state }
                            assertTrue(claims.all { it=="held" })
                            f.engine!!.close();f.engine=null
                            return@peerRestart
                        }
                        assertEquals(JBoolean(true),restored.getValue("admitted"),"${f.engine!!.holds}")
                        val revokeDispatch=Auth3Command(id(49900),project,Auth3Payload.ReplaceWorkGrants(planner,0,
                            workGrantDigest(project,planner,WorkPermission.entries.toSet()),WorkPermission.entries.toSet()-WorkPermission.TASK_DISPATCH))
                        assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(ownerToken,Auth3CommandCodec().encode(revokeDispatch))).receipt.outcome)
                        assertEquals(JBoolean(true),peer.probe().getValue("admitted")) // WA-07: no cascading worker revocation
                        val revokeRead=CoreCommand(id(49901),project,CommandPayload.Revoke(id(2006)))
                        assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(ownerToken,CommandCodec().encode(revokeRead))).receipt.outcome)
                        val denied=peer.probe()
                        assertEquals(JBoolean(false),denied.getValue("admitted"));assertEquals(1L,denied.getValue("wakes").long())
                        f.engine!!.close();f.engine=null
                    }
                } }
            } finally { runBlocking { http.mcp.close() };server.stop(0,5000);nativeEndpoint.close() }
        }
        serviceProof.fill(0);transportProof.fill(0)
    }
    @Test @Timeout(180) fun firstActiveBlockerWithoutEnvelopeUsesOneOriginalPauseAndRetainsClaimsOnRestart() {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"jvm-node-first-blocker-")
        val serviceProof=ByteArray(32).also { SecureRandom().nextBytes(it) };val transportProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        ConnectedFixture(initialCondition=false).use { f ->
            val a=f.state().assignment(f.consumerId);val intent=id(49800);val correlation=id(49801)
            f.publish(CRequestNativeTransitionOperation(CRequestNativeTransition(f.consumerId,a.generation,intent,correlation,"resume",f.checkpoint,emptyList())))
            f.f.runtimeFacts=f.f.runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(f.consumerSubject,intent,correlation,"resume","confirmed",
                listOf(f.f.source),f.state().binding(f.consumerSubject).execution,false,"paused")))
            f.publish(CObserveNativeTransitionOperation(CObserveNativeTransition(f.consumerId,a.generation+1,intent,0,"confirmed",listOf(f.f.source),emptyList())))
            val claims=f.state().assignment(f.consumerId).requiredResources.map { WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString() }
            val serviceOrigin="http://127.0.0.1:${port()}";val transportOrigin="http://127.0.0.1:${port()}"
            val endpoint=WrithelmService(f.app).nativeEndpoint(project,f.f.controller,f.f.intended.nativeHostId,serviceProof)
            val http=HttpAdapter(WrithelmService(f.app),HttpProfile(serviceOrigin),nativeEndpoint=endpoint)
            val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(serviceOrigin).port) { http.install(this) }.start(wait=false)
            val config=config(f,root,serviceOrigin,transportOrigin,serviceProof,transportProof)
            try {
                HttpConnectedNativeControl(transportOrigin,transportProof).use { control ->
                    f.installNative(control)
                    Peer(config,root).use { peer ->
                        f.startEngine()
                        f.declareCondition("first-real-transport-blocker")
                        assertEquals("pausing",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
                        val actual=peer.probe();assertEquals(1L,actual.getValue("interrupts").long())
                        assertEquals(0L,actual.getValue("wakes").long());assertEquals(JBoolean(false),actual.getValue("admitted"))
                        assertTrue(f.state().records.values.none { it.payload is WContinuationsPayload })
                        f.engine!!.close();f.engine=null
                    }
                    val original=f.state().assignment(f.consumerId).currentIntent
                    Peer(config,root).use { peer ->
                        f.startEngine()
                        assertEquals(original,f.state().assignment(f.consumerId).currentIntent)
                        assertEquals("pausing",f.state().assignment(f.consumerId).state) // interrupt return is not fencing
                        val actual=peer.probe();assertEquals(1L,actual.getValue("interrupts").long());assertEquals(0L,actual.getValue("wakes").long())
                        assertEquals(claims,f.state().assignment(f.consumerId).requiredResources.map { WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString() })
                        assertTrue(f.state().records.values.none { it.payload is WContinuationsPayload })
                        f.engine!!.close();f.engine=null
                    }
                }
            } finally { runBlocking { http.mcp.close() };server.stop(0,5000);endpoint.close() }
        }
        serviceProof.fill(0);transportProof.fill(0)
    }
}
