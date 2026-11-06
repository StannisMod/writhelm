// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.auth.AuthCommandCodec
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

internal fun readyFile(name: String): ByteArray=Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_H1_READY_ROOT")),name))
internal fun workReadyFile(name: String): ByteArray=Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_H1_WORK_READY_ROOT")),name))
internal fun uid(n: Int): UUID=UUID.fromString("00000000-0000-0000-0000-"+n.toString(16).padStart(12,'0'))

class WorkCodecContractTest {
    private val codec=WorkCodec()
    @Test fun `WORK S41 original fingerprint1 and fingerprint2 frozen independent vectors`() {
        val root=StrictJson.decode(workReadyFile("goldens/independent-v1.json")) as JObject
        root.entries.getValue("preservedFingerprint1and2").list().forEach { row ->
            val fields=(row as JObject).entries;val input=fields.getValue("input") as JObject
            val bytes=StrictJson.encode(input)
            val actual=when(input.entries.getValue("formatVersion").long()) {
                1L -> CommandCodec().let { it.fingerprint(it.decode(bytes)) }
                2L -> AuthCommandCodec().let { it.fingerprint(it.decode(bytes)) }
                else -> error("unexpected fixture version")
            }
            assertEquals(fields.getValue("expectedSha256").string(),actual.hex)
        }
    }
    @Test fun `WORK S01 all 29 frozen command3 independent golden fingerprints`() {
        val root=StrictJson.decode(readyFile("goldens-v2.json")) as JObject
        var count=0
        root.entries.getValue("command3").list().forEach { value ->
            val row=(value as JObject).entries;val input=row.getValue("input") as JObject
            val name=input.entries.getValue("operation").string()
            if(name in setOf("register_delegation","replace_work_grants","rotate_delegated_credential","revoke_delegated_credential","revoke_delegation"))return@forEach
            val command=codec.decodeCommand(StrictJson.encode(input))
            assertEquals(row.getValue("expectedSha256").string(),codec.fingerprint(command).hex,name)
            assertContentEquals(codec.encodeCommand(command),codec.encodeCommand(codec.decodeCommand(codec.encodeCommand(command))))
            assertEquals(codec.fingerprint(command),codec.fingerprint(command.copy(requestKey=uid(199))))
            count++
        };assertEquals(29,count)
    }
    @Test fun `WORK S02 closed commands reject actor injection missing phantom and malformed tokens`() {
        val all=StrictJson.decode(readyFile("concrete-commands-v2.json")) as JObject
        all.entries.filterKeys { it !in setOf("register_delegation","replace_work_grants","rotate_delegated_credential","revoke_delegated_credential","revoke_delegation") }.forEach { (name,v) ->
            val f=(v as JObject).entries;val payload=f.getValue("payload") as JObject
            val badRoot=JObject(f+("actor" to str("owner")))
            val badPayload=JObject(f+("payload" to JObject(payload.entries+("actor" to str("owner")))))
            val missing=JObject(f+("payload" to JObject(payload.entries.filterKeys { it!="bodies" })))
            listOf(badRoot,badPayload,missing).forEach { bad ->
                assertFailsWith<CodecFault>(name) { codec.decodeCommand(StrictJson.encode(bad)) }
            }
        }
        val valid=codec.encodeCommand(WorkCommand(uid(1),uid(2),CSetTaskHeadOperation(CSetTaskHead(WorkKey.Task(uid(2),uid(3)),null,"title",null,null,emptyList())))).toString(Charsets.UTF_8)
        listOf("3.0","3e0","-0","9223372036854775808").forEach { n ->
            assertFailsWith<CodecFault> { codec.decodeCommand(valid.replace("\"formatVersion\":3","\"formatVersion\":$n").toByteArray()) }
        }
        listOf(valid+" {}",valid.replace("\"title\":\"title\"","\"title\":\"title\",\"ti\\u0074le\":\"x\""),valid.replace("title","\\ud800")).forEach {
            assertFailsWith<CodecFault> { codec.decodeCommand(it.toByteArray()) }
        }
        assertFailsWith<CodecFault> { codec.decodeCommand(byteArrayOf(0xc0.toByte(),0xaf.toByte())) }
    }
    @Test fun `WORK S01 sets normalize sequences stay ordered and foreign project refuses`() {
        val all=(StrictJson.decode(readyFile("concrete-commands-v2.json")) as JObject).entries
        val c=codec.decodeCommand(StrictJson.encode(all.getValue("accept_result")))
        val op=(c.operation as CAcceptResultOperation).value
        val a=c.copy(operation=CAcceptResultOperation(op.copy(data=op.data.copy(criterionIds=listOf(uid(5),uid(4))))))
        val b=c.copy(operation=CAcceptResultOperation(op.copy(data=op.data.copy(criterionIds=listOf(uid(4),uid(5))))))
        assertEquals(codec.fingerprint(a),codec.fingerprint(b))
        val duplicate=op.copy(data=op.data.copy(criterionIds=listOf(uid(4),uid(4))))
        assertFailsWith<CodecFault> { codec.encodeCommand(c.copy(operation=CAcceptResultOperation(duplicate))) }
        assertFailsWith<CodecFault> { codec.encodeCommand(c.copy(projectId=uid(999))) }
    }
    @Test fun `WORK S43 closed exact read selectors no latest and new controller reads`() {
        WorkFamily.entries.forEach { family ->
            val key: WorkKey=when(family.keyKind) {
                WorkKeyKind.TASK -> WorkKey.Task(uid(1),uid(2))
                WorkKeyKind.TASK_REVISION -> WorkKey.TaskRevision(uid(1),uid(2),0)
                WorkKeyKind.PROFILE -> WorkKey.Profile(uid(1),uid(2),0)
                WorkKeyKind.REVISION -> WorkKey.Revision(uid(1),uid(2),0)
                WorkKeyKind.ID -> WorkKey.Id(uid(1),uid(2))
                WorkKeyKind.RESOURCE -> WorkKey.Resource(uid(1),uid(2))
                WorkKeyKind.ASSIGNMENT_BINDING -> WorkKey.AssignmentBinding(uid(1),uid(2),0)
                WorkKeyKind.CONTROLLER_CONTINUATION -> WorkKey.ControllerContinuation(uid(1),uid(2),uid(3))
            }
            val selector=linkedMapOf("key" to StrictJson.decode(identityCodec.encodeKey(key)))
            selector[if(family.mutable)"generation" else "digest"]=if(family.mutable)JNumber(0)else str("0".repeat(64))
            val root=obj("formatVersion" to JNumber(2),"projectId" to uuid(uid(1)),"operation" to str("read_"+family.tag.replace('-','_')),"subject" to JObject(selector))
            val read=codec.decodeRead(StrictJson.encode(root))
            assertEquals(WorkRecordIdentity(family,key),(read as WorkRead.Exact).identity)
            assertEquals(read,codec.decodeRead(codec.encodeRead(read)))
            selector.remove(if(family.mutable)"generation" else "digest")
            assertFailsWith<CodecFault> { codec.decodeRead(StrictJson.encode(obj("formatVersion" to JNumber(2),"projectId" to uuid(uid(1)),"operation" to str("read_"+family.tag.replace('-','_')),"subject" to JObject(selector)))) }
        }
    }
    @Test fun `WORK S36 historical metadata cap independent of tight admission`() {
        val r=WorkRecord(WorkRecordIdentity(WorkFamily.TASK_HEADS,WorkKey.Task(uid(1),uid(2))),WTaskHeadsPayload(WTaskHeads(0,"x".repeat(256),null,null)))
        val bytes=codec.encodeRecord(r)
        val tight=WorkCodec(metadataLimits=JsonLimits(bytes=128))
        assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { tight.decodeRecord(bytes) }.problem)
        assertEquals(r,tight.decodeRecord(bytes,true))
    }
    @Test fun `WORK S02 legacy27 and composed31 closed structural families preserve fields and reject unknown missing`() {
        // Historical readiness examples are shape oracles, NOT valid cross-record histories. These
        // explicit parent-v2 field adaptations do not turn their unresolved dummy references valid.
        val commands=(StrictJson.decode(readyFile("concrete-commands-v2.json")) as JObject).entries
        val reserve=((commands.getValue("reserve_assignment") as JObject).entries.getValue("payload") as JObject).entries
        val profile=((commands.getValue("create_profile") as JObject).entries.getValue("payload") as JObject).entries.getValue("data") as JObject
        val additions=StrictJson.decode(readyFile("concrete-new-records-v2.json")).list().associateBy { ((it as JObject).entries.getValue("family")).string() }
        val composed=(StrictJson.decode(Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_COORDINATION_READY_ROOT")),"examples/record-shapes.json")),
            JsonLimits(bytes=4*1024*1024,depth=128,collection=8192,tokens=500000)) as JObject).entries.getValue("records") as JObject
        WorkFamily.entries.forEach { family ->
            val envelope=(if(family in setOf(WorkFamily.ASSIGNMENT_REPLACEMENTS,WorkFamily.CONDITIONS,WorkFamily.CONDITION_CONFIRMATIONS,WorkFamily.CONTINUATIONS))
                composed.entries.getValue(family.tag) else additions[family.tag] ?: StrictJson.decode(workReadyFile("examples/records/${family.tag}.json"))) as JObject
            val raw=(envelope.entries.getValue("payload") as JObject).entries.toMutableMap()
            when(family) {
                WorkFamily.ASSIGNMENTS,WorkFamily.ASSIGNMENT_BINDINGS -> {
                    raw["intendedExecution"]=reserve.getValue("intendedExecution")
                    raw["delegationId"]=if(family==WorkFamily.ASSIGNMENTS)JNull else uuid(uid(1))
                    if(family==WorkFamily.ASSIGNMENT_BINDINGS)raw["reviewSubjects"]=JArray(emptyList())
                }
                WorkFamily.PLANS -> raw["assignment"]=JNull
                WorkFamily.PROFILES -> raw["model"]=profile.entries.getValue("model")
                else -> Unit
            }
            val p=decodeWorkPayload(family,JObject(raw))
            assertContentEquals(StrictJson.encode(JObject(raw)),StrictJson.encode(encodeWorkPayload(p)),family.tag)
            assertFailsWith<CodecFault>(family.tag) { decodeWorkPayload(family,JObject(raw+("actor" to str("owner")))) }
            raw.keys.forEach { missing -> assertFailsWith<CodecFault>("${family.tag}.$missing") { decodeWorkPayload(family,JObject(raw-missing)) } }
        }
    }
    @Test fun `WORK S35 encoded command and metadata boundaries include final escaping overhead`() {
        val c=WorkCommand(uid(1),uid(2),CSetTaskHeadOperation(CSetTaskHead(WorkKey.Task(uid(2),uid(3)),null,"quote\" slash/é\n",null,null,emptyList())))
        val bytes=codec.encodeCommand(c)
        for(n in bytes.size-1..bytes.size+1) {
            val bounded=WorkCodec(commandLimits=JsonLimits(bytes=n))
            if(n<bytes.size)assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { bounded.encodeCommand(c) }.problem)
            else assertContentEquals(bytes,bounded.encodeCommand(c))
        }
        val r=WorkRecord(WorkRecordIdentity(WorkFamily.TASK_HEADS,WorkKey.Task(uid(2),uid(3))),WTaskHeadsPayload(WTaskHeads(0,"quote\"\né",null,null)))
        val record=codec.encodeRecord(r)
        for(n in record.size-1..record.size+1) {
            val bounded=WorkCodec(metadataLimits=JsonLimits(bytes=n))
            if(n<record.size)assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { bounded.encodeRecord(r) }.problem)
            else assertContentEquals(record,bounded.encodeRecord(r))
        }
    }
    @Test fun `WORK exact operation refusal vocabularies match frozen matrix no unsupported business code`() {
        val matrix=(StrictJson.decode(readyFile("operation-role-state-matrix.json")) as JObject).entries
        val composed=(StrictJson.decode(Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_COORDINATION_READY_ROOT")),"operation-role-state-matrix.json"))) as JObject).entries
        assertEquals(34,workRefusalCodes.size)
        workRefusalCodes.forEach { (op,codes) ->
            val row=(matrix[op] ?: composed.getValue(op)) as JObject
            assertEquals(row.entries.getValue("errors").list().map { it.string() }.toSet(),codes.map { it.name }.toSet())
            assertContentEquals(StrictJson.encode(row.entries.getValue("errors")),StrictJson.encode((composed.getValue(op) as JObject).entries.getValue("errors")))
            WorkProblem.entries.forEach { reason ->
                try { assertTrue(workRefusal(op,WorkFault(reason)).problem in codes) }
                catch(e: CodecFault) { assertEquals(CodecProblem.INVALID_GRAPH,e.problem);assertTrue(reason !in codes) }
            }
        }
    }
    @Test fun `WORK S35 body string cap is independent of custom ordinary cap including Unicode UTF8 bytes`() {
        val text="🙂".repeat(18000) // 72000 UTF8 bytes, legal above the ordinary64KiB cap
        val body=Utf8Content.fromText(text);val ref=WorkBodyReference(body.digest,body.sizeBytes)
        val root=(StrictJson.decode(readyFile("concrete-commands-v2.json")) as JObject).entries.getValue("record_evidence")
        val original=codec.decodeCommand(StrictJson.encode(root))
        val evidence=(original.operation as CRecordEvidenceOperation).value
        val method="é".repeat(1024) // 2048 bytes, not1024 UTF8 bytes or2048 scalar characters
        val data=evidence.data.copy(method=method,environment=ref,report=ref)
        val c=original.copy(operation=CRecordEvidenceOperation(evidence.copy(data=data,bodies=listOf(CRecordEvidenceBodiesItem(body.digest,text)))))
        val raw=codec.encodeCommand(c)
        assertEquals(c,codec.decodeCommand(raw))
        for(cap in listOf(2048,65536)) {
            val custom=WorkCodec(commandLimits=JsonLimits(bytes=2*1024*1024,stringBytes=cap))
            assertContentEquals(raw,custom.encodeCommand(c))
            assertEquals(c,custom.decodeCommand(raw))
        }
        val tooSmallOrdinary=WorkCodec(commandLimits=JsonLimits(bytes=2*1024*1024,stringBytes=2047))
        assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { tooSmallOrdinary.decodeCommand(raw) }.problem)
        for(n in body.sizeBytes-1..body.sizeBytes+1) {
            val custom=WorkCodec(commandLimits=JsonLimits(bytes=2*1024*1024,stringBytes=2048),bodyBytes=n)
            if(n<body.sizeBytes) {
                assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { custom.decodeCommand(raw) }.problem)
                assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { custom.encodeCommand(c) }.problem)
            } else {
                assertEquals(c,custom.decodeCommand(raw));assertContentEquals(raw,custom.encodeCommand(c))
            }
        }
        val tinyTotal=WorkCodec(commandLimits=JsonLimits(bytes=raw.size-1,stringBytes=2048))
        assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { tinyTotal.encodeCommand(c) }.problem)
    }
}
