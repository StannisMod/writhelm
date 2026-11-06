// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.CommandResult
import dev.writhelm.core.workflow.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.*
import java.net.http.*
import java.nio.file.*
import java.util.UUID
import kotlin.test.*

private fun id(n: Int)=UUID(0,n.toLong())
private val project=id(2)
private val otherProject=id(900)
private class Fixture: AutoCloseable {
    val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_APP_TEST_ROOT"))),"api-")
    private val store=id(1)
    // Public fixed synthetic vectors, never live credentials.
    val owner=CredentialVerifier.token(store,id(4),ByteArray(32) { 4 })
    val planner=CredentialVerifier.token(store,id(6),ByteArray(32) { 6 })
    val foreignOwner=CredentialVerifier.token(store,id(9),ByteArray(32) { 9 })
    private fun credential(k: Int,p: Int,scope: UUID=project)=Credential(id(k),store,scope,id(p),
        CredentialVerifier.digest(store,scope,id(p),id(k),ByteArray(32) { k.toByte() }),CredentialStatus.ACTIVE,0)
    private val policy=AccessPolicy(listOf(Grant(id(3),project,Capability.entries.toSet()),Grant(id(5),project,setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE)),Grant(id(3),otherProject,Capability.entries.toSet())))
    val app=MechanismApp.initialize(root,AppGenesis(ApprovalWorkflow.start(policy),AuthorityView.checked(store,
        listOf(id(3),id(5)),listOf(ProjectBinding(project,id(3)),ProjectBinding(otherProject,id(3))),listOf(credential(4,3),credential(6,5),credential(9,3,otherProject)),
        mapOf(Enrollment(id(3),project) to 0L,Enrollment(id(5),project) to 0L,Enrollment(id(3),otherProject) to 0L),policy)))
    var sequence=100
    fun command(p: CommandPayload,k: UUID=id(sequence++))=CommandCodec().encode(CoreCommand(k,project,p))
    fun proposal(n: Int,text: String="\ufeffScope\r\nX$n e\u0301\r\n"): DocumentRevision {
        val bytes=text.toByteArray();return DocumentRevision(RevisionKey(DocumentKey(project,id(20)),id(n)),Utf8Content.fromText(text),
            StructureIndex(listOf(ClauseSpan(ClauseId("X"),ByteSpan(0,bytes.size))),emptyList()))
    }
    fun propose(r: DocumentRevision)=assertIs<CommandResult.Settled>(app.execute(planner,command(CommandPayload.Propose(r))))
    fun request(r: DocumentRevision,gap: Boolean=false): ApprovalRequest {
        val rid=id(sequence++)
        val p=ReviewPacket(listOf(ReviewItem(id(50),r.clauseReference(ClauseId("X")),null)),"Accept exact X?","Only X; no neighbouring edits","Synthetic rationale",
            listOf("Retains exact bytes and costs"),listOf("Keep pending"),listOf("Semantics not proven"),emptyList(),informationGaps=if(gap)listOf("Missing required evidence") else emptyList())
        assertIs<CommandResult.Settled>(app.execute(planner,command(CommandPayload.Request(rid,p))))
        return assertIs<WorkflowResult.Success<ApprovalRequest>>(app.readRequest(owner,project,rid)).value
    }
    override fun close()=app.close()
}
private fun read(op: String,subject: JsonValue,p: JsonValue=uuid(project))=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to p,"operation" to str(op),"subject" to subject))
private fun fields(r: ServiceReply)=assertIs<JObject>(r.value).entries
private fun data(r: ServiceReply)=assertIs<JObject>(fields(r).getValue("data")).entries
private fun bundle(r: ApprovalRequest)=read("read_review_bundle",obj("id" to uuid(r.id),"requestDigest" to digest(r.digest),"packetDigest" to digest(r.packet.digest)))
private fun page(cursor: JsonValue=JNull)=obj("cursor" to cursor,"pageSize" to num(1),"documentId" to uuid(id(20)))

@Timeout(60)
class ServiceContractTest {
    @Test fun exactBodyAndWholeBundle()=Fixture().use { f ->
        assertTrue(Runtime.getRuntime().maxMemory()<=128L*1024*1024,"Test JVM exceeds authorized heap")
        assertTrue(Runtime.getRuntime().availableProcessors()<=2,"Test JVM exceeds authorized CPUs")
        val r=f.proposal(30);f.propose(r);val request=f.request(r);val service=WrithelmService(f.app)
        val reply=service.read(f.owner,read("read_revision",obj("revisionKey" to revisionKey(r.key),"revisionDigest" to digest(r.digest))))
        assertEquals(200,reply.status);assertEquals(r.content.text(),data(reply).getValue("body").string())
        val b=service.read(f.owner,bundle(request));assertEquals(200,b.status);assertEquals(JBoolean(true),data(b)["complete"]);assertEquals(JBoolean(true),data(b)["actionable"])
        assertEquals(1,data(b).getValue("materials").list().size)
        val gap=f.request(r,true);assertEquals(503,service.read(f.owner,bundle(gap)).status)
        val denied=service.read(f.planner,read("read_request",obj("id" to uuid(request.id)),uuid(id(999))))
        assertEquals(403,denied.status);assertFalse(denied.bytes().decodeToString().contains(request.id.toString()))
        val resolved=service.command(f.owner,f.command(CommandPayload.Resolve(request.id,request.digest,request.packet.digest,StructuredAnswer.Approve)))
        assertEquals(200,resolved.status);assertEquals(JBoolean(false),data(service.read(f.owner,bundle(request)))["actionable"])
        evidence("bundle.json",b.bytes())
    }
    @Test fun pinnedPagesAndExplicitExpiry()=Fixture().use { f ->
        var clock=0L;val service=WrithelmService(f.app,ServiceLimits(snapshotMillis=100),now={clock})
        f.propose(f.proposal(31));f.propose(f.proposal(32))
        val first=service.read(f.planner,read("list_revisions",page()));assertEquals(200,first.status)
        val cursor=data(first).getValue("nextCursor");assertNotEquals(JNull,cursor)
        f.propose(f.proposal(33))
        val second=service.read(f.planner,read("list_revisions",page(cursor)));assertEquals(200,second.status)
        assertEquals(StrictJson.encode(fields(first).getValue("basis")).decodeToString(),StrictJson.encode(fields(second).getValue("basis")).decodeToString())
        assertEquals(JBoolean(true),data(second)["complete"]);assertEquals(1,data(second).getValue("items").list().size)
        clock=100;assertEquals("SNAPSHOT_EXPIRED",fields(service.read(f.planner,read("list_revisions",page(cursor)))).getValue("code").string())
    }
    @Test fun currentRevocationBeatsSnapshotRights()=Fixture().use { f ->
        val service=WrithelmService(f.app);f.propose(f.proposal(31));f.propose(f.proposal(32))
        val first=service.read(f.planner,read("list_revisions",page()));val cursor=data(first).getValue("nextCursor")
        assertEquals(200,service.command(f.owner,f.command(CommandPayload.Revoke(id(5)))).status)
        assertEquals(403,service.read(f.planner,read("list_revisions",page(cursor))).status)
        assertEquals(200,service.read(f.owner,read("list_projects",obj("cursor" to JNull,"pageSize" to num(8)),JNull)).status)
    }
    @Test fun exactTokenExtractionAndDomainRefusal() {
        val raw="""{"jsonrpc":"2.0","id":9007199254740993,"method":"tools/call","params":{"_meta":{"x":1.25},"arguments":{"command":{"formatVersion":1.0,"requestKey":"00000000-0000-0000-0000-000000000001"}}}}""".toByteArray()
        val decoded=WireJson.decode(raw);assertTrue(checkNotNull(decoded.domain).decodeToString().contains("1.0"))
        assertFailsWith<CodecFault> { StrictJson.decode(checkNotNull(decoded.domain)) }
        assertFailsWith<ServiceFault> { WireJson.decode("""{"a":1,"\u0061":2}""".toByteArray()) }
        assertFailsWith<ServiceFault> { WireJson.decode("""{"x":"\ud800"}""".toByteArray()) }
    }
    @Test fun oversizeBundleRefusesWholeResponse()=Fixture().use { f ->
        val r=f.proposal(30,"x".repeat(66000));f.propose(r);val request=f.request(r)
        val service=WrithelmService(f.app,ServiceLimits(responseBytes=65536))
        val result=service.read(f.owner,bundle(request));assertEquals(503,result.status)
        assertEquals("UNAVAILABLE",fields(result).getValue("code").string());assertFalse(fields(result).containsKey("data"))
        assertIs<WorkflowResult.Failure>(f.app.readResolution(f.owner,project,request.id))
        Unit // JUnit requires a void test; do not silently omit this negative acceptance case.
    }
    @Test fun strictReadFieldsAndNoPriorIds()=Fixture().use { f ->
        val service=WrithelmService(f.app);f.propose(f.proposal(30))
        val foreign=DocumentRevision(RevisionKey(DocumentKey(otherProject,id(901)),id(902)),Utf8Content.fromText("Private Q"),StructureIndex(listOf(ClauseSpan(ClauseId("Q"),ByteSpan(0,9))),emptyList()))
        assertEquals(200,service.command(f.foreignOwner,CommandCodec().encode(CoreCommand(id(903),otherProject,CommandPayload.Propose(foreign)))).status)
        val projects=service.read(f.planner,read("list_projects",obj("cursor" to JNull,"pageSize" to num(8)),JNull))
        assertEquals(project.toString(),data(projects).getValue("items").list().single().fields("projectId").getValue("projectId").string())
        assertFalse(projects.bytes().decodeToString().contains(otherProject.toString()))
        val docs=service.read(f.owner,read("list_documents",obj("cursor" to JNull,"pageSize" to num(8))))
        assertFalse(docs.bytes().decodeToString().contains(id(901).toString()))
        assertEquals(403,service.read(f.owner,read("read_revision",obj("revisionKey" to revisionKey(foreign.key),"revisionDigest" to digest(foreign.digest)),uuid(otherProject))).status)
        assertEquals(400,service.read(f.owner,read("list_documents",obj("cursor" to JNull,"pageSize" to num(8),"actor" to uuid(id(3))))).status)
        evidence("projects.json",projects.bytes())
    }
    @Test fun blockedContinuityRetainsRecordedAttribution()=Fixture().use { f ->
        val service=WrithelmService(f.app)
        val original=f.proposal(30,"Unchanged exact X\r\n");f.propose(original);val request=f.request(original)
        assertEquals(200,service.command(f.owner,f.command(CommandPayload.Resolve(request.id,request.digest,request.packet.digest,StructuredAnswer.Approve))).status)
        val successor=f.proposal(31,"Unchanged exact X\r\n");f.propose(successor)
        val source=original.clauseReference(ClauseId("X"));val target=successor.clauseReference(ClauseId("X"))
        val assessment=id(700)
        assertEquals(200,service.command(f.planner,f.command(CommandPayload.Assess(ConsistencyAssessment(assessment,project,listOf(source,target),"Exact unchanged material",listOf(ContinuityLink(source,target)))))).status)
        assertEquals(200,service.command(f.planner,f.command(CommandPayload.Assess(ConsistencyAssessment(id(701),project,listOf(target),"Known synthetic blocker",blocked=listOf(target))))).status)
        val reply=service.read(f.planner,read("read_clause",reference(target)))
        assertEquals(200,reply.status);assertEquals("BLOCKED_INPUT",data(reply).getValue("observation").string())
        val trace=data(reply).getValue("trace").fields("origin","requestId","approvedBy","assessmentIds")
        assertEquals(request.id,uuid(trace.getValue("requestId")));assertEquals(listOf(assessment),trace.getValue("assessmentIds").list().map(::uuid))
        evidence("blocked-continuity.json",reply.bytes())
    }
    private fun evidence(name: String,bytes: ByteArray) {
        val root=Path.of(requireNotNull(System.getenv("API_EVIDENCE_ROOT")));Files.createDirectories(root);Files.write(root.resolve(name),bytes)
    }
}

@Timeout(90)
class TransportContractTest {
    @Test fun sdkLifecycleRestrictionsAndInvalidDomainNoReceipt()=Fixture().use { f -> runBlocking {
        val service=WrithelmService(f.app);val mcp=McpAdapter(service)
        fun code(r: WireReply)=WireJson.json.parseToJsonElement(r.body.decodeToString()).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.int
        try {
            val init=mcp.post(f.planner,null,null,"""{"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"unsupported","capabilities":{},"clientInfo":{"name":"synthetic","version":"1"}}}""".toByteArray())
            assertEquals(200,init.status);assertTrue(init.body.decodeToString().contains("2025-11-25"))
            val sid=checkNotNull(init.sessionId)
            suspend fun post(text: String)=mcp.post(f.planner,sid,"2025-11-25",text.toByteArray())
            assertEquals(-32600,code(post("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")))
            assertEquals(202,post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").status)
            assertEquals(null,code(post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")))
            assertEquals(-32600,code(post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")))
            val valid=f.command(CommandPayload.Propose(f.proposal(30))).decodeToString()
            val invalid=valid.replace("\"formatVersion\":1","\"formatVersion\":1.0")
            val refusal=post("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"_meta":{"float":1.25},"name":"writhelm_command","arguments":{"command":$invalid}}}""")
            assertTrue(refusal.body.decodeToString().contains("INVALID_INPUT"))
            assertEquals(202,post("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"writhelm_command","arguments":{"command":$valid}}}""").status)
            assertEquals(0,f.app.captureRead(f.planner).workflow.snapshot().revisions.size)
            val accepted=service.command(f.planner,valid.toByteArray());assertEquals(200,accepted.status)
            assertEquals(JBoolean(false),fields(accepted)["commandReplayed"])
            assertEquals(-32600,code(post("""{"jsonrpc":"1.0","id":4,"method":"ping"}""")))
            assertEquals(204,mcp.delete(f.planner,sid,"2025-11-25").status)
            try { post("""{"jsonrpc":"2.0","id":5,"method":"ping"}""");fail("Deleted session accepted") }
            catch(e: ServiceFault) { assertEquals(404,e.status) }
        } finally { mcp.close() }
    } }
    @Test fun realHttpMcpSharedCommandAndBoundary()=Fixture().use { f -> runBlocking {
        val port=java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:$port";val adapter=HttpAdapter(WrithelmService(f.app),HttpProfile(origin))
        val engine=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
        val client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(java.time.Duration.ofSeconds(5)).build()
        fun call(path: String,body: String,token: String=f.planner,headers: Map<String,String> = emptyMap()): HttpResponse<String> {
            val b=HttpRequest.newBuilder(URI(origin+path)).timeout(java.time.Duration.ofSeconds(20)).header("Authorization","Bearer $token").header("Content-Type","application/json")
            headers.forEach { (k,v)->b.header(k,v) };return client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
        }
        try {
            val r=f.proposal(30);val bytes=f.command(CommandPayload.Propose(r));val first=call("/api/v1/commands",bytes.decodeToString())
            assertEquals(200,first.statusCode(),first.body());assertEquals("no-store",first.headers().firstValue("Cache-Control").orElse(""))
            val init=call("/mcp","""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"synthetic-client","version":"1"}}}""",headers=mapOf("Accept" to "application/json, text/event-stream"))
            assertEquals(200,init.statusCode(),init.body());val session=init.headers().firstValue("MCP-Session-Id").orElseThrow()
            val mh=mapOf("Accept" to "application/json, text/event-stream","MCP-Session-Id" to session,"MCP-Protocol-Version" to "2025-11-25")
            assertEquals(202,call("/mcp","""{"jsonrpc":"2.0","method":"notifications/initialized"}""",headers=mh).statusCode())
            val replay=call("/mcp","""{"jsonrpc":"2.0","id":9007199254740993,"method":"tools/call","params":{"name":"writhelm_command","arguments":{"command":${bytes.decodeToString()}}}}""",headers=mh)
            assertEquals(200,replay.statusCode(),replay.body());assertTrue(replay.body().contains("\"commandReplayed\":true"),replay.body());assertTrue(replay.body().contains("9007199254740993"))
            val again=call("/api/v1/commands",bytes.decodeToString());assertTrue(again.body().contains("\"commandReplayed\":true"))
            assertEquals(1, f.app.captureRead(f.planner).workflow.snapshot().revisions.size)
            val denied=call("/api/v1/commands",bytes.decodeToString(),headers=mapOf("Origin" to "null"));assertEquals(403,denied.statusCode())
            assertEquals(401,call("/api/v1/commands",bytes.decodeToString(),token="invalid").statusCode())
            val get=HttpRequest.newBuilder(URI(origin+"/mcp")).header("Authorization","Bearer ${f.planner}").GET().build()
            assertEquals(405,client.send(get,HttpResponse.BodyHandlers.ofString()).statusCode())
            val tools=call("/mcp","""{"jsonrpc":"2.0","id":3,"method":"tools/list"}""",headers=mh)
            assertTrue(tools.body().contains("additionalProperties"));assertFalse(tools.body().contains("urn:writhelm:"))
            val ownerCommand=f.command(CommandPayload.Revoke(id(5)))
            val bypass=call("/mcp","""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"writhelm_command","arguments":{"command":${ownerCommand.decodeToString()}}}}""",headers=mh)
            assertTrue(bypass.body().contains("FORBIDDEN"),bypass.body())
            val read=call("/api/v1/reads",read("read_revision",obj("revisionKey" to revisionKey(r.key),"revisionDigest" to digest(r.digest))).decodeToString())
            assertEquals(200,read.statusCode())
            val content=StrictJson.decode(read.body().toByteArray()).fields("formatVersion","kind","basis","operation","data").getValue("data")
            assertEquals(r.content.text(),(content as JObject).entries.getValue("body").string())
            val sdkHttp=io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) { install(SSE) }
            val sdk=Client(Implementation("api001-sdk-client","1"))
            val transport=StreamableHttpClientTransport(sdkHttp,origin+"/mcp") {
                headers.append("Authorization","Bearer ${f.planner}")
            }.apply { protocolVersion="2025-11-25" }
            try {
                sdk.connect(transport)
                assertEquals(setOf("writhelm_read","writhelm_command"),sdk.listTools().tools.map { it.name }.toSet())
                val args=WireJson.json.parseToJsonElement("""{"read":${read("read_revision",obj("revisionKey" to revisionKey(r.key),"revisionDigest" to digest(r.digest))).decodeToString()}}""").jsonObject
                val result=sdk.callTool(CallToolRequest(CallToolRequestParams(name="writhelm_read",arguments=args)))
                assertEquals(false,result.isError)
                assertEquals(r.content.text(),result.structuredContent!!.getValue("data").jsonObject.getValue("body").jsonPrimitive.content)
                transport.terminateSession()
            } finally { sdk.close();sdkHttp.close() }
        } finally { adapter.mcp.close();engine.stop(0,5000);client.close() }
    } }
}
