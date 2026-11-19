// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.net.*
import java.net.http.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.*

@Timeout(60)
class ReviewSubmissionTest {
    private fun source(r:DocumentRevision,clause:String?="X")=obj("kind" to str("revision"),
        "revisionKey" to revisionKey(r.key),"revisionDigest" to digest(r.digest),"clauseId" to (clause?.let(::str) ?: JNull))
    private fun draft(r:DocumentRevision,dependencies:List<JsonValue> = emptyList()):JsonValue=obj(
        "items" to JArray(listOf(obj("id" to uuid(id(800)),"target" to source(r),"before" to JNull))),
        "question" to str("Review exact proposal?"),"scope" to str("Synthetic scope"),
        "rationale" to str("One source closure"),"consequences" to JArray(listOf(str("Named inputs only"))),
        "alternatives" to JArray(listOf(str("Request changes"))),"uncertainties" to JArray(emptyList()),
        "contexts" to JArray(if(r.structure.context.isEmpty())emptyList() else listOf(source(r,null))),
        "dependencies" to JArray(dependencies),"informationGaps" to JArray(emptyList()))
    private fun command(r:DocumentRevision,key:Int=801,request:Int=802,d:JsonValue=draft(r))=
        CommandCodec().encode(CoreCommand(id(key),project,CommandPayload.SubmitReview(id(request),d)))
    private fun result(reply:ServiceReply)=assertIs<JObject>(reply.value).entries

    @Test fun oneHttpSubmissionPublishes26SourcesAndRestartReplayNeverRebuildsAgainstNewState() {
        val root=directory()
        val names=listOf("X")+(1..25).map { "DEP-$it" }
        var offset=0
        val spans=names.map { name->val start=offset;offset+=name.toByteArray().size+1;ClauseSpan(ClauseId(name),ByteSpan(start,offset)) }
        val r=DocumentRevision(RevisionKey(DocumentKey(project,id(21)),id(100)),Utf8Content.fromText(names.joinToString("\n",postfix="\n")),
            StructureIndex(spans,emptyList()))
        val dependencies=names.drop(1)
        val d=draft(r,dependencies.map { source(r,it) })
        val bytes=command(r,d=d);lateinit var frozen:ApprovalRequest
        AppFixture(root).use { f->
            f.propose(r)
            val service=WrithelmService(f.app)
            val port=ServerSocket(0,1,InetAddress.getLoopbackAddress()).use { it.localPort }
            val engine=embeddedServer(CIO,host="127.0.0.1",port=port) {
                HttpAdapter(service,HttpProfile("http://127.0.0.1:$port")).install(this)
            }.start(wait=false)
            try {
                val request=HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/commands"))
                    .header("Authorization","Bearer $plannerToken").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
                val reply=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString())
                assertEquals(200,reply.statusCode(),reply.body())
                val envelope=(StrictJson.decode(reply.body().toByteArray()) as JObject).entries
                assertEquals("submit_review",(envelope.getValue("identity") as JObject).entries.getValue("operation").string())
                frozen=value(f.app.readRequest(plannerToken,project,id(802)))
                assertEquals(25,frozen.packet.dependencies.size)
                assertEquals(r.x(),frozen.packet.items.single().target)
                assertEquals(dependencies.map { r.clauseReference(ClauseId(it)) },frozen.packet.dependencies)
                val conflict=service.command(plannerToken,command(r,d=JObject((d as JObject).entries+("scope" to str("Changed input")))))
                assertEquals("IDENTITY_CONFLICT",result(conflict).getValue("code").string())
                f.propose(revision(200,"New X"))
            } finally { engine.stop(100,1000) }
        }
        MechanismApp.open(root,storeId).use { app->
            val replay=settled(app.execute(plannerToken,bytes))
            assertTrue(replay.commandReplayed)
            assertEquals(frozen.digest,value(app.readRequest(plannerToken,project,id(802))).digest)
            assertEquals(1,replay.receipt.writes.size) // original retained witness, no new publication
            assertNull(replay.publication)
        }
    }

    @Test fun invalidAndForeignSourcesNeverPublishAPartialRequestAndPlanWithoutReadCannotReplay()=AppFixture().use { f->
        val r=revision(100);f.propose(r)
        val wrong=source(r) as JObject
        val stale=JObject(wrong.entries+("revisionDigest" to str("0".repeat(64))))
        val d=draft(r,listOf(stale))
        val rejected=settled(f.app.execute(plannerToken,command(r,d=d)))
        assertEquals("INVALID_REFERENCE",(rejected.receipt.outcome as LogicalOutcome.Rejected).code)
        assertTrue(rejected.receipt.writes.isEmpty())
        assertIs<WorkflowResult.Failure>(f.app.readRequest(plannerToken,project,id(802)))
        val foreign=JObject(wrong.entries+("revisionKey" to revisionKey(RevisionKey(DocumentKey(id(999),id(20)),id(100)))))
        assertIs<CommandResult.Denied>(f.app.execute(plannerToken,command(r,803,804,draft(r,listOf(foreign)))))
        val good=command(r,805,806)
        settled(f.app.execute(plannerToken,good))
        settled(f.auth(AuthPayload.ReplaceGrants(planner,0,f.initial.authority.grantDigest(planner,project),setOf(Capability.PLAN))))
        assertIs<CommandResult.Denied>(f.app.execute(plannerToken,good))
        Unit
    }

    @Test fun h1ControllerUsesSameOperationAndHistoricalCommandRecoveryRemainsUnchanged() {
        val f=JointFixture();val root=directory();val options=H1Options(UnavailableH1RuntimeFacts)
        val original=revision(100)
        val r=DocumentRevision(RevisionKey(DocumentKey(project,id(21)),id(100)),original.content,original.structure)
        val bytes=command(r)
        H1Application.initialize(root,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app->
            val service=WrithelmService(app)
            assertEquals(200,service.command(plannerToken,CommandCodec().encode(CoreCommand(id(811),project,CommandPayload.Propose(r)))).status)
            assertEquals(200,service.command(plannerToken,bytes,agent=true).status)
            val legacy=CommandCodec().encode(CoreCommand(id(812),project,CommandPayload.Request(id(813),
                packet(listOf(ReviewItem(id(814),r.y())),listOf(r.contextReference())))))
            assertEquals(200,service.command(plannerToken,legacy).status)
            assertEquals(JBoolean(true),result(service.command(plannerToken,legacy)).getValue("commandReplayed"))
        }
        H1Application.open(root,storeId,options).use { app->
            val service=WrithelmService(app)
            val replay=service.command(plannerToken,bytes)
            assertEquals(200,replay.status)
            assertEquals(JBoolean(true),result(replay).getValue("commandReplayed"))
        }
    }

    @Test fun localizedFollowupResolvesPredecessorOnSameStateAndMatchesLegacyPacketDigest()=AppFixture().use { f->
        val r=revision(100);f.propose(r)
        val previous=f.request(packet(listOf(ReviewItem(id(850),r.x())),listOf(r.contextReference())))
        settled(f.core(CommandPayload.Resolve(previous.id,previous.digest,previous.packet.digest,
            StructuredAnswer.RejectWithReason("Synthetic reconsideration")),ownerToken))
        val presentation=ReviewPresentation("ru","en",listOf(ReviewPresentationItem(id(800),"Точный пункт","Текст предложения.")))
        val link=ReviewFollowupItem(id(850),id(800),"unchanged","Same source, reconsidered explicitly.")
        val fields=(draft(r) as JObject).entries
        val localized=JObject(fields+mapOf(
            "presentations" to (packet(ReviewPacket(listOf(ReviewItem(id(800),r.x())),
                "Question","Scope","Rationale",listOf("Consequence"),listOf("Alternative"),emptyList(),
                listOf(r.contextReference()),presentations=listOf(presentation))) as JObject).entries.getValue("presentations"),
            "followup" to obj("requestId" to uuid(previous.id),"items" to JArray(listOf(obj(
                "previousItemId" to uuid(id(850)),"itemId" to uuid(id(800)),"disposition" to str("unchanged"),
                "explanation" to str(link.explanation)))))))
        val bytes=command(r,d=localized)
        val receipt=settled(f.app.execute(plannerToken,bytes))
        assertIs<LogicalOutcome.Success>(receipt.receipt.outcome)
        val stored=value(f.app.readRequest(plannerToken,project,id(802)))
        val expected=ReviewPacket(listOf(ReviewItem(id(800),r.x())),"Review exact proposal?","Synthetic scope",
            "One source closure",listOf("Named inputs only"),listOf("Request changes"),emptyList(),listOf(r.contextReference()),
            followup=ReviewFollowup(previous.id,previous.digest,previous.packet.digest,listOf(link)),presentations=listOf(presentation))
        assertEquals(expected.digest,stored.packet.digest)
        assertIs<WorkflowResult.Failure>(f.app.readResolution(ownerToken,project,id(802)))
        val oversized=JObject(fields+("dependencies" to JArray(List(257) { source(r) })))
        assertFailsWith<CodecFault> { command(r,860,861,oversized) }
        Unit
    }
}
