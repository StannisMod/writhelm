// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.util.Base64
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Real private HTTP/current-H/auth/Git boundary; native activation observations remain fixtures. */
class NativeRuntimeEndpointTest {
    private class Boundary(val f: ReviewAppFixture,val app: H1Application,host: java.util.UUID=f.intended.nativeHostId,
        controller: java.util.UUID=f.controller): AutoCloseable {
        val proof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(proof)
        val endpoint=WrithelmService(app).nativeEndpoint(project,controller,host,proof)
        val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:$port"
        val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),nativeEndpoint=endpoint)
        val server=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
        val client=HttpClient.newHttpClient()
        fun call(body: JsonValue,token: String=token(f.workerKey),proof: String?=encoded): HttpResponse<ByteArray> {
            val builder=HttpRequest.newBuilder(URI(origin+"/internal/native/v1"))
                .header("Authorization","Bearer $token").header("Content-Type","application/json")
            if(proof!=null)builder.header("X-Writhelm-Native-Proof",proof)
            return client.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(StrictJson.encode(body))).build(),HttpResponse.BodyHandlers.ofByteArray())
        }
        fun identity()=obj("formatVersion" to num(1),"operation" to str("identity"),"projectId" to uuid(project))
        fun status(generation: String="0",assignment: java.util.UUID=f.assignment): JObject {
            val binding=app.coordinationCapture(plannerToken,project).work.assignment(f.assignment).binding!!
            return obj("formatVersion" to num(1),"operation" to str("status"),"projectId" to uuid(project),
                "assignmentId" to uuid(assignment),"executionGeneration" to str(generation),
                "bindingJSON" to str(identityCodec.encodeReference(binding).decodeToString()),"continuationId" to JNull)
        }
        override fun close() { server.stop(100,1000);runBlocking { adapter.mcp.close() };endpoint.close();proof.fill(0) }
    }
    @Test fun privateHostProofAndActualScopedCredentialAreBothRequired() {
        val f=ReviewAppFixture();f.publishedActiveApp().use { app -> Boundary(f,app).use { b ->
            assertEquals(403,b.call(b.identity(),proof=null).statusCode())
            assertEquals(403,b.call(b.identity(),proof="A".repeat(43)).statusCode())
            assertEquals(403,b.call(b.identity(),token=ownerToken).statusCode())
            val worker=b.call(b.identity());assertEquals(200,worker.statusCode())
            val fields=(StrictJson.decode(worker.body()) as JObject).entries
            assertEquals("assignment",fields.getValue("scope").string())
            assertEquals(f.workerKey,uuid(fields.getValue("credentialId")))
            assertEquals(f.assignment,uuid(fields.getValue("assignmentId")))
            assertFalse(worker.body().decodeToString().contains(token(f.workerKey)))
            assertEquals("controller",(StrictJson.decode(b.call(b.identity(),plannerToken).body()) as JObject).entries.getValue("scope").string())
        } }
    }
    @Test fun activePermitDoesNotCascadeIssuerDispatchRevocationIntoWorkerAuthority() {
        val f=ReviewAppFixture();f.publishedActiveApp().use { app -> Boundary(f,app).use { b ->
            val request=b.status()
            fun flags(who: String)=(StrictJson.decode(b.call(request,who).also { assertEquals(200,it.statusCode()) }.body()) as JObject).entries
            assertEquals(JBoolean(true),flags(token(f.workerKey)).getValue("active"))
            val remove=Auth3Command(id(49001),project,Auth3Payload.ReplaceWorkGrants(planner,0,
                workGrantDigest(project,planner,WorkPermission.entries.toSet()),WorkPermission.entries.toSet()-WorkPermission.TASK_DISPATCH))
            assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(remove)))
            assertEquals(JBoolean(true),flags(token(f.workerKey)).getValue("active"))
            assertEquals(JBoolean(true),flags(plannerToken).getValue("active"))
            assertEquals(JBoolean(false),flags(plannerToken).getValue("dispatch"))
        } }
    }
    @Test fun currentReadLossAndWrongSubjectNeverBecomeAnActiveLabelPermit() {
        val f=ReviewAppFixture();f.publishedActiveApp().use { app -> Boundary(f,app).use { b ->
            val request=b.status()
            assertEquals(403,b.call(b.status(assignment=id(49999))).statusCode())
            assertEquals(403,b.call(b.status(generation="1")).statusCode())
            assertEquals(400,b.call(b.status(generation="9223372036854775808")).statusCode())
            assertEquals(400,b.call(JObject(request.entries+("actor" to str("owner")))).statusCode())
            assertIs<CommandResult.Settled>(app.execute(ownerToken,CommandCodec().encode(CoreCommand(id(49002),project,CommandPayload.Revoke(f.worker)))))
            val response=b.call(request);assertEquals(200,response.statusCode())
            val fields=(StrictJson.decode(response.body()) as JObject).entries
            assertEquals(JBoolean(false),fields.getValue("active"));assertSame(JNull,fields.getValue("bindingJSON"))
            // Authentication/protocol identity is distinct from protected READ and still supports
            // the ordinary current-policy exact-receipt route; this endpoint grants no execution.
            assertEquals(200,b.call(b.identity()).statusCode())
        } }
    }
    @Test fun authenticatedPeerProofCannotSubstituteAnotherNativeHostOrController() {
        val f=ReviewAppFixture();f.publishedActiveApp().use { app ->
            Boundary(f,app,host=id(49997)).use { b -> assertEquals(403,b.call(b.status()).statusCode()) }
            Boundary(f,app,controller=id(49998)).use { b -> assertEquals(403,b.call(b.identity()).statusCode()) }
        }
    }
}
