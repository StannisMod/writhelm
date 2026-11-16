// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.work.*
import dev.writhelm.core.store.*
import dev.writhelm.core.knowledge.Digest
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
    @Test @Timeout(60) fun protectedTransportSelectorIsIndependentlyCheckedAgainstActualNodeProcess() {
        val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE")))
        val js=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS")))
        val root=Files.createTempDirectory("writhelm-process-peer-")
        val script=root.resolve("peer.mjs")
        Files.writeString(script,"""
            import {pathToFileURL} from 'node:url'
            import {createHash} from 'node:crypto'
            import * as fs from 'node:fs/promises'
            import {createReadStream} from 'node:fs'
            const {listenNativeTransport}=await import(pathToFileURL(process.env.TEST_NATIVE_JS+'/src/native-transport.js').href)
            const origin=process.env.TEST_ORIGIN, proof=Buffer.from(process.env.TEST_PROOF,'base64url')
            const hash=createHash('sha256');for await(const chunk of createReadStream('/proc/self/exe'))hash.update(chunk)
            const digest=hash.digest('hex'),stat=await fs.readFile('/proc/self/stat','utf8')
            const start=stat.slice(stat.lastIndexOf(')')+1).trim().split(/\s+/)[19]
            const boot=(await fs.readFile('/proc/sys/kernel/random/boot_id','utf8')).trim()
            const epoch=createHash('sha256').update(JSON.stringify(['native-linux-process-v1',digest,boot,process.pid,start,origin])).digest('hex')
            const selector=()=>({pid:process.env.TEST_SPOOF==='1'?process.pid+1:process.pid,nativeOrigin:origin})
            const transport=await listenNativeTransport(origin,proof,{}, {},undefined,undefined,undefined,selector)
            console.log(JSON.stringify({pid:process.pid,digest,epoch}))
            process.stdin.once('data',async()=>{await transport.close();process.exit(0)})
            process.stdin.resume()
        """.trimIndent())
        val proof=ByteArray(32) { 31 }
        fun testPeer(spoof:Boolean) {
            val origin="http://127.0.0.1:${port()}"
            val builder=ProcessBuilder(node.toString(),script.toString()).redirectError(root.resolve("peer-$spoof.stderr").toFile())
            builder.environment()["TEST_ORIGIN"]=origin
            builder.environment()["TEST_NATIVE_JS"]=js.toString()
            builder.environment()["TEST_PROOF"]=Base64.getUrlEncoder().withoutPadding().encodeToString(proof)
            builder.environment()["TEST_SPOOF"]=if(spoof)"1" else "0"
            val peer=builder.start()
            try {
                val ready=StrictJson.decode(requireNotNull(peer.inputStream.bufferedReader().readLine()).toByteArray()).fields("pid","digest","epoch")
                val epoch=ready.getValue("epoch").string();val pin=PinnedNativeExecutable("fixture-node",digest(ready.getValue("digest")))
                HttpConnectedNativeControl(origin,proof).use { client ->
                    val actual=client.observeProcess(epoch,pin)
                    if(spoof)assertNull(actual) else assertEquals(ObservedNativeProcess(pin.version,pin.digest,epoch),actual)
                    assertNull(client.observeProcess("0".repeat(64),pin))
                    assertNull(client.observeProcess(epoch,pin.copy(digest=Digest.of("wrong executable".toByteArray()))))
                }
                HttpConnectedNativeControl(origin,ByteArray(32) { 32 }).use { client ->
                    assertNull(client.observeProcess(epoch,pin))
                }
                peer.outputStream.write(1);peer.outputStream.flush()
                assertTrue(peer.waitFor(10,TimeUnit.SECONDS));assertEquals(0,peer.exitValue())
            } finally { if(peer.isAlive) { peer.destroy();if(!peer.waitFor(5,TimeUnit.SECONDS))peer.destroyForcibly() } }
        }
        testPeer(false);testPeer(true);proof.fill(0)
        // Real protected HTTP transport and Linux Node process; OpenCode SDK/session is not run.
    }
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
        fun source(): Map<String,JsonValue> {
            writer.write("{\"operation\":\"capture-source\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray(),JsonLimits(bytes=16384,stringBytes=8192)).fields("locatorId","digest","proof")
        }
        fun checkpoint(): Map<String,JsonValue> {
            writer.write("{\"operation\":\"checkpoint-source\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray(),JsonLimits(bytes=16384,stringBytes=8192)).fields("checkpoint","attempt","nativeResultStillUnset")
        }
        fun compact(late: Boolean): Map<String,JsonValue> {
            writer.write("{\"operation\":\"${if(late)"compact-late" else "compact-source"}\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray(),JsonLimits(bytes=16384,stringBytes=8192))
                .fields("checkpoint","nativeResultSet","failed","lateInputs","modelCalls","retiredAdmissionHeld","retiredToolHeld")
        }
        fun restore(): Map<String,JsonValue> {
            writer.write("{\"operation\":\"restore-source\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray(),JsonLimits(bytes=16384,stringBytes=8192))
                .fields("checkpoint","residualKind","wakes","lateInputs")
        }
        fun controllerCompact(): Map<String,JsonValue> {
            writer.write("{\"operation\":\"controller-compact\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray(),JsonLimits(bytes=16384,stringBytes=8192,collection=256))
                .fields("continuation","nativeResultSet","failed","frontier")
        }
        fun controllerRestore(): Map<String,JsonValue> {
            writer.write("{\"operation\":\"controller-restore\"}\n");writer.flush()
            return StrictJson.decode(requireNotNull(reader.readLine()).toByteArray(),JsonLimits(bytes=16384,stringBytes=8192))
                .fields("continuation","kind","wakes")
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
            "profileJSON" to str(identityCodec.encodeKey(f.f.profile).decodeToString()),
            "governingTaskJSON" to str(identityCodec.encodeKey(f.f.task).decodeToString()),
            "controllerPrincipal" to uuid(planner),"controllerKey" to uuid(plannerKey),"controllerToken" to str(plannerToken),
            "workerPrincipal" to uuid(id(2006)),"workerKey" to uuid(f.consumerKey),"workerToken" to str(token(f.consumerKey)),
            "controllerSessionID" to str("ses_connected_controller"),"sessionID" to str(f.consumerIntended.nativeSessionId),
            "serviceProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(serviceProof)),
            "transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(transportProof)),
            "subject" to obj("assignmentId" to uuid(f.consumerId),"executionGeneration" to str("0"),"bindingJSON" to str(identityCodec.encodeReference(binding).decodeToString())))
    }
    @Test @Timeout(240) fun realLocalConnectedCycleLostReplyAndAdapterRestartNeedNoPlannerRelayOrRepeatedWake()=cycle(false)
    @Test @Timeout(240) fun lostOriginalAndGuardAfterPossibleWakeRemainUnknownAcrossAdapterRestart()=cycle(true)
    @Test @Timeout(240) fun completeHandoffRetiresWithoutAcceptanceOrAnotherModelAndKeepsClaims()=cycle(false,true)
    @Test @Timeout(240) fun checkpointCooperativePauseAndSameBindingResumeThroughActualServiceAndTransport()=cycle(false,false,true)
    @Test @Timeout(240) fun checkpointPauseUnknownWaitRetainsOriginalAcrossServiceTransportRestart()=cycle(false,false,true,true)
    private fun cycle(loseCustody: Boolean,finishHandoff: Boolean=false,cooperativeRoundTrip:Boolean=false,unknownWait:Boolean=false) {
        val base=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT")))
        val root=Files.createTempDirectory(base,"jvm-node-bridge-")
        val serviceProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val transportProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        // MOCK only: this does not qualify any real worker/process or original effect surface.
        ConnectedFixture(nativeSafety=NativeRuntimeSafety { _,subject -> subject==WAssignmentSubject(id(2004),0) },
            controllerSafety=NativeControllerRuntimeSafety { _,controller -> controller==id(60) }).use { f ->
            val serviceOrigin="http://127.0.0.1:${port()}";val transportOrigin="http://127.0.0.1:${port()}"
            val nativeEndpoint=WrithelmService(f.app).nativeEndpoint(project,f.f.controller,f.f.intended.nativeHostId,serviceProof)
            val http=HttpAdapter(WrithelmService(f.app),HttpProfile(serviceOrigin),nativeEndpoint=nativeEndpoint)
            val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(serviceOrigin).port) { http.install(this) }.start(wait=false)
            val originalConfig=config(f,root,serviceOrigin,transportOrigin,serviceProof,transportProof)
            val config=if(cooperativeRoundTrip)JObject(originalConfig.entries+mapOf("cooperativePauseModelFree" to JBoolean(true),
                "cooperativeWaitUnknown" to JBoolean(unknownWait)))else originalConfig
            try {
                DropFirstDelivery(transportOrigin) { key ->
                    if(loseCustody) {
                        val hash=dev.writhelm.core.knowledge.Digest.of(key.toByteArray(Charsets.UTF_8)).hex
                        listOf("native-original","native-delivery-guard").forEach { Files.delete(root.resolve("custody/$it-$hash.json")) }
                        val attempt=java.util.UUID.nameUUIDFromBytes("$key:delivery".toByteArray(Charsets.UTF_8))
                        Files.delete(f.root.resolve("custody/$attempt.dispatch"))
                    }
                }.use { fault -> HttpConnectedNativeControl(fault.origin,transportProof).use controlUse@ { control ->
                    f.installNative(control,"a".repeat(64))
                    val deliveredHint=CountDownLatch(1)
                    val hintSubscription=f.app.onNativeObservation { deliveredHint.countDown() }
                    Peer(config,root).use peerInitial@ { peer ->
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
                        if(!loseCustody) {
                            val source=peer.source();val p=source.getValue("proof").fields("locatorId","scope","profileJSON","modelJSON","renderedRequestDigest","packetDigest","nativeMessages")
                            val messages=p.getValue("nativeMessages").list().map { item -> val m=item.fields("messageId","digest");WSourceCutsNativeMessagesItem(m.getValue("messageId").string(),digest(m.getValue("digest"))) }
                            val cut=CRecordSourceCut(WorkKey.Id(project,id(49700)),WSourceCuts(f.consumerTask,f.consumerSubject,f.f.intended.nativeHostId,
                                f.consumerIntended.nativeSessionId,f.f.profile,1,digest(p.getValue("renderedRequestDigest")),digest(p.getValue("packetDigest")),
                                uuid(source.getValue("locatorId")),messages,emptyList(),emptyList()),emptyList())
                            f.publish(CRecordSourceCutOperation(cut),token(f.consumerKey))
                            val stored=f.state().record(WorkFamily.SOURCE_CUTS,cut.key)
                            assertEquals(cut.data,(stored.payload as WSourceCutsPayload).value)
                            assertFalse(WorkCodec().encodeRecord(stored,true).decodeToString().contains("MOCK complete remaining task tracker"))
                            val checkpoint=peer.checkpoint()
                            val reference=identityCodec.decodeReference(StrictJson.encode(checkpoint.getValue("checkpoint")))
                            assertEquals(reference,f.state().assignment(f.consumerId).checkpoint)
                            assertEquals(JBoolean(true),checkpoint.getValue("nativeResultStillUnset")) // canonical persistence alone does not install native context
                            val compacted=peer.compact(false)
                            assertEquals(JBoolean(false),compacted.getValue("failed"))
                            assertEquals(JBoolean(true),compacted.getValue("nativeResultSet"))
                             assertEquals(identityCodec.decodeReference(StrictJson.encode(compacted.getValue("checkpoint"))),f.state().assignment(f.consumerId).checkpoint)
                             if(cooperativeRoundTrip){
                                 val before=f.state().assignment(f.consumerId)
                                 val checkpoint=before.checkpoint
                                 val base=f.state().task(f.consumerTask).base
                                 val files=LinuxOwnedDirectoryPreparation.snapshot(f.consumerRoot,f.f.snapshot)
                                 val claims=before.requiredResources.map {WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString()}
                                 // Production current-checkpoint selector + actual filesystem preparation.
                                 // Only the original native work-area lookup is an explicit fixture port.
                                 f.engine!!.close();f.engine=null
                                 f.preparation=dev.writhelm.service.NativeCheckpointPreparation { _,subject ->
                                     if(subject==f.consumerSubject)f.consumerRoot else null
                                 }
                                 f.installNative(control,"a".repeat(64))
                                 f.startEngine()
                                 val blocker=f.declareCondition("same-binding-cooperative-round-trip")
                                 f.engine!!.awaitIdle()
                                 val paused=f.state().assignment(f.consumerId)
                                 assertEquals(if(unknownWait)"pausing" else "paused",paused.state,"${f.engine!!.holds}")
                                 assertEquals(checkpoint,paused.checkpoint);assertEquals(before.binding,paused.binding)
                                 assertEquals(before.execution,paused.execution);assertEquals(before.workspaceId,paused.workspaceId)
                                 assertEquals(1L,peer.probe().getValue("interrupts").long())
                                 if(unknownWait){
                                     val original=paused.currentIntent
                                     assertNotNull(original)
                                     f.engine!!.tick()
                                     assertEquals(original,f.state().assignment(f.consumerId).currentIntent)
                                     assertEquals(1L,peer.probe().getValue("interrupts").long())
                                     assertEquals(claims,paused.requiredResources.map {WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString()})
                                     assertEquals(base,f.state().task(f.consumerTask).base)
                                     assertEquals(files,LinuxOwnedDirectoryPreparation.snapshot(f.consumerRoot,f.f.snapshot))
                                     f.engine!!.close();f.engine=null
                                     return@peerInitial
                                 }
                                 f.confirm(r,e,blocker)
                                 f.engine!!.awaitIdle()
                                 val resumed=f.state().assignment(f.consumerId)
                                 assertEquals("active",resumed.state,"${f.engine!!.holds}; continuations="+
                                     f.state().records.values.mapNotNull {(it.payload as? WContinuationsPayload)?.value?.let {v->"${v.phase}/${v.stage}/${v.effect.state}"}})
                                 assertEquals(checkpoint,resumed.checkpoint);assertEquals(before.binding,resumed.binding)
                                 assertEquals(before.execution,resumed.execution);assertEquals(before.workspaceId,resumed.workspaceId)
                                 assertEquals(base,f.state().task(f.consumerTask).base)
                                 assertEquals(files,LinuxOwnedDirectoryPreparation.snapshot(f.consumerRoot,f.f.snapshot))
                                 assertEquals(claims,resumed.requiredResources.map {WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString()})
                                 val effects=peer.probe()
                                 assertEquals(1L,effects.getValue("interrupts").long())
                                 assertEquals(2L,effects.getValue("wakes").long())
                                 assertEquals(JBoolean(true),effects.getValue("admitted"))
                                 f.engine!!.close();f.engine=null
                                 return@peerInitial
                             }
                            if(finishHandoff) {
                                f.finishConsumerHandoff()
                                val retired=peer.compact(false)
                                assertEquals(JBoolean(false),retired.getValue("failed"))
                                assertEquals(JBoolean(true),retired.getValue("nativeResultSet"))
                                assertEquals(0L,retired.getValue("modelCalls").long())
                                assertEquals(JBoolean(true),retired.getValue("retiredAdmissionHeld"))
                                assertEquals(JBoolean(true),retired.getValue("retiredToolHeld"))
                                assertTrue(f.state().records.values.none { (it.payload as? WAcceptancesPayload)?.value?.task==f.consumerTask })
                                assertTrue(f.state().assignment(f.consumerId).requiredResources.all { (f.state().record(WorkFamily.CLAIMS,it).payload as WClaimsPayload).value.state=="held" })
                                f.engine!!.close();f.engine=null
                                return@peerInitial
                            }
                            val late=peer.compact(true)
                            assertEquals(JBoolean(true),late.getValue("failed"))
                            assertEquals(JBoolean(false),late.getValue("nativeResultSet"))
                            assertEquals(1L,late.getValue("lateInputs").long()) // native input persisted, not silently included in the cut
                            val restored=peer.restore()
                            assertEquals("restored-residual-not-norms",restored.getValue("residualKind").string())
                            assertEquals(identityCodec.decodeReference(StrictJson.encode(restored.getValue("checkpoint"))),f.state().assignment(f.consumerId).checkpoint)
                            assertEquals(1L,restored.getValue("wakes").long());assertEquals(1L,restored.getValue("lateInputs").long())
                            val controller=peer.controllerCompact()
                            assertEquals(JBoolean(false),controller.getValue("failed"))
                            assertEquals(JBoolean(true),controller.getValue("nativeResultSet"))
                            val cref=identityCodec.decodeReference(StrictJson.encode(controller.getValue("continuation")))
                            val continuation=(f.state().resolve(cref).payload as WControllerContinuationsPayload).value
                            assertTrue(continuation.frontier.flatMap { it.assignments }.map { it.assignmentId }.containsAll(listOf(f.consumerId,f.f.assignment)))
                            assertTrue(continuation.entries.all { it.entry.status=="proposed" })
                            assertTrue(continuation.pending.isNotEmpty())
                            val controllerRestored=peer.controllerRestore()
                            assertEquals(cref,identityCodec.decodeReference(StrictJson.encode(controllerRestored.getValue("continuation"))))
                            assertEquals("restored-controller-residual-not-norms",controllerRestored.getValue("kind").string())
                            assertEquals(1L,controllerRestored.getValue("wakes").long())
                        }
                        f.engine!!.close();f.engine=null
                    }
                    hintSubscription.close()
                    if(cooperativeRoundTrip&&unknownWait){
                        val before=f.state().assignment(f.consumerId)
                        val files=LinuxOwnedDirectoryPreparation.snapshot(f.consumerRoot,f.f.snapshot)
                        val claims=before.requiredResources.map {WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString()}
                        Peer(config,root).use {peer->
                            f.startEngine()
                            val after=f.state().assignment(f.consumerId)
                            assertEquals("pausing",after.state);assertEquals(before.currentIntent,after.currentIntent)
                            assertEquals(before.binding,after.binding);assertEquals(before.checkpoint,after.checkpoint)
                            assertEquals(before.execution,after.execution);assertEquals(before.workspaceId,after.workspaceId)
                            assertEquals(files,LinuxOwnedDirectoryPreparation.snapshot(f.consumerRoot,f.f.snapshot))
                            assertEquals(claims,after.requiredResources.map {WorkCodec().encodeRecord(f.state().record(WorkFamily.CLAIMS,it),true).decodeToString()})
                            val observed=peer.probe()
                            assertEquals(1L,observed.getValue("interrupts").long());assertEquals(1L,observed.getValue("wakes").long())
                            assertEquals(JBoolean(false),observed.getValue("admitted"))
                            f.engine!!.close();f.engine=null
                        }
                    }
                    if(finishHandoff||cooperativeRoundTrip)return@controlUse
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
