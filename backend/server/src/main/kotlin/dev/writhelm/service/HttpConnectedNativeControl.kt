// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** Real private loopback client, not a planner relay or native qualification. The peer is selected
 * only by trusted startup and its separate retained proof, never a command/tool argument. Effects
 * are never retried here. Immutable reconcile deliberately does NOT first retain/re-authorize the
 * envelope: application runtime-facts capture can own the monitor during that call. */
internal class HttpConnectedNativeControl(origin: String,proof: ByteArray): ConnectedNativeControl,NativeSourceProofControl,NativePreparationProofControl,NativeInitialProofControl,AutoCloseable {
    private val origin=HttpProfile(origin).origin
    private val proof=proof.copyOf().also { require(it.size==32) }
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
    @Volatile private var closed=false
    private fun call(operation: String,vararg fields: Pair<String,JsonValue>,maximum: Int=16384,stringMaximum:Int=8192): JsonValue {
        check(!closed) { "native transport closed" }
        val bytes=StrictJson.encode(obj("formatVersion" to num(1),"operation" to str(operation),*fields),4*1024*1024)
        val request=HttpRequest.newBuilder(URI(origin+"/native/v1")).timeout(Duration.ofSeconds(10))
            .header("Content-Type","application/json").header("X-Writhelm-Native-Proof",Base64.getUrlEncoder().withoutPadding().encodeToString(proof))
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
        val response=client.send(request,HttpResponse.BodyHandlers.ofInputStream())
        val body=response.body().use { it.readNBytes(maximum+1) }
        workRequire(response.statusCode()==200 && body.size<=maximum,WorkProblem.NATIVE_UNKNOWN)
        val f=StrictJson.decode(body,JsonLimits(bytes=maximum,stringBytes=stringMaximum,collection=4096,tokens=65536,depth=32)).fields("formatVersion","kind","result")
        workRequire(f.getValue("formatVersion").long()==1L && f.getValue("kind").string()=="native-result",WorkProblem.NATIVE_UNKNOWN)
        return f.getValue("result")
    }
    private fun key(e: ConnectedEnvelope)=e.continuation.id.toString()
    fun identity(): Map<String,JsonValue> = call("identity").fields("hostId","controllerId","principalId","credentialId","sessionID","epoch","workRoots")
    fun startupIdentity(): Map<String,JsonValue> = call("identity-immutable").fields("hostId","controllerId","principalId","credentialId","sessionID","epoch","workRoots")
    /** Narrow immutable source witness, not a raw artifact disclosure. May be called by runtime
     * capture under the service monitor: receiving peer must not query current service policy. */
    override fun sourceProof(locator: java.util.UUID): JsonValue?=try { call("source-proof","locatorId" to uuid(locator),maximum=1024*1024) }
        catch(_: Exception) { null }
    override fun preparationProof(assignment:java.util.UUID,generation:Long):JsonValue?=try {
        call("worker-proof","assignmentId" to uuid(assignment),"executionGeneration" to str(generation.toString()),maximum=2*1024*1024,stringMaximum=1024*1024)
    }catch(_:Exception){null}
    override fun initialProof(intent:java.util.UUID):JsonValue?=try {
        call("initial-proof","intentId" to uuid(intent),maximum=1024*1024,stringMaximum=512*1024)
    }catch(_:Exception){null}
    private fun envelopeCall(operation: String,e: ConnectedEnvelope)=call(operation,"key" to str(key(e)),"digest" to digest(Digest.of(e.bytes())))
    /** Trusted packet provisioner seam for the dependent context stage. Current service digest and
     * binding are verified by the receiving concrete bridge before any original is retained. This
     * does not assert semantic packet/source completeness or physical work-area qualification. */
    private fun lowering(e: ConnectedEnvelope): Pair<JObject,JObject> {
        val subject=obj("assignmentId" to uuid(e.assignment.assignmentId),"executionGeneration" to str(e.assignment.generation.toString()),
            "bindingJSON" to str(identityCodec.encodeReference(e.binding).decodeToString()))
        val target=obj("key" to str(key(e)),"rawJSON" to str(e.bytes().decodeToString()),"digest" to digest(Digest.of(e.bytes())),
            "bindingJSON" to subject.entries.getValue("bindingJSON"),"hostID" to uuid(e.nativeHostId),"sessionID" to str(e.nativeSessionId),
            "messageID" to str(e.messageId),"text" to str(e.text),"metadata" to obj("continuationId" to uuid(e.continuation.id),
                "assignmentId" to uuid(e.assignment.assignmentId),"executionGeneration" to str(e.assignment.generation.toString())))
        return subject to target
    }
    fun retainEnvelope(e: ConnectedEnvelope,instructions: List<Pair<String,String>>) {
        val (subject,target)=lowering(e)
        call("retain","kind" to str("envelope"),"subject" to subject,"target" to JObject(target.entries+
            ("instructions" to JArray(instructions.map { obj("key" to str(it.first),"value" to str(it.second)) }))))
    }
    private fun binding(t: ConnectedBindingTarget): Pair<String,JObject> {
        val raw=StrictJson.encode(obj("assignment" to encodeWAssignmentSubject(t.assignment),
            "binding" to StrictJson.decode(identityCodec.encodeReference(t.binding)),"nativeHostId" to uuid(t.nativeHostId),"nativeSessionId" to str(t.nativeSessionId)))
        val digest=Digest.of(raw)
        val key="binding:${digest.hex}"
        return key to obj("key" to str(key),"rawJSON" to str(raw.decodeToString()),"digest" to digest(digest),
            "bindingJSON" to str(identityCodec.encodeReference(t.binding).decodeToString()),"hostID" to uuid(t.nativeHostId),"sessionID" to str(t.nativeSessionId))
    }
    override fun hold(envelope: ConnectedEnvelope): Boolean {
        val (subject,target)=lowering(envelope)
        call("prepare","subject" to subject,"target" to target)
        return (envelopeCall("hold",envelope) as? JBoolean)?.value==true
    }
    override fun rehold(target: ConnectedBindingTarget) {
        val (key,value)=binding(target)
        call("retain","kind" to str("binding"),"subject" to obj("assignmentId" to uuid(target.assignment.assignmentId),
            "executionGeneration" to str(target.assignment.generation.toString()),"bindingJSON" to value.entries.getValue("bindingJSON")),"target" to value)
        call("rehold","key" to str(key),"digest" to value.entries.getValue("digest"))
    }
    override fun pause(target: ConnectedBindingTarget,intent: WorkKey.Id) {
        val (key,value)=binding(target)
        call("pause","key" to str(key),"digest" to value.entries.getValue("digest"),"intent" to uuid(intent.id))
    }
    override fun reconcilePause(target: ConnectedBindingTarget,intent: WorkKey.Id): WorkNativeObservation?=null
    // The selected physical-effect observer is not qualified. An interrupt reply/transport receipt
    // MUST NOT manufacture a WorkNativeObservation(writersFenced=true) or release claims.
    private fun outcome(value: JsonValue): NativeConnectedOutcome=when(value.string()) {
        "matched-wake" -> NativeConnectedOutcome.MatchedWake
        "unknown" -> NativeConnectedOutcome.Unknown
        "conflict" -> NativeConnectedOutcome.Conflict
        "not-performed" -> NativeConnectedOutcome.NotPerformed
        else -> throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
    }
    override fun deliver(envelope: ConnectedEnvelope): NativeConnectedOutcome=try { outcome(envelopeCall("deliver",envelope)) }
        catch(_: Exception) {
            // Lost delivery or an already retained claim only permits immutable original-effect
            // reconciliation. Never retry the external action or invent not-performed from absence.
            try { outcome(envelopeCall("reconcile",envelope)) } catch(_: Exception) { NativeConnectedOutcome.Unknown }
        }
    override fun reconcile(envelope: ConnectedEnvelope): NativeConnectedOutcome=try { outcome(envelopeCall("reconcile",envelope)) }
        catch(_: Exception) { NativeConnectedOutcome.Unknown }
    override fun release(envelope: ConnectedEnvelope) { envelopeCall("release",envelope) }
    override fun close() { closed=true;client.close();proof.fill(0) }
}
