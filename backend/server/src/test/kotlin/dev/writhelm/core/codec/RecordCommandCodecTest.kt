// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.workflow.*
import dev.writhelm.core.knowledge.*
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class RecordCommandCodecTest {
    private val command = """{"formatVersion":1,"requestKey":"00000000-0000-0000-0000-00000000000a","projectId":"00000000-0000-0000-0000-000000000001","operation":"revoke_access","payload":{"principalId":"00000000-0000-0000-0000-000000000006"}}"""
    @Test fun `independent fingerprint and formatting equivalence`() {
        val codec = CommandCodec(); val decoded = codec.decode(command.toByteArray())
        assertEquals("0225e74fafc5334382e0f302eea0177656ba3b62c387930ef64643bc9e6ef1c9", codec.fingerprint(decoded).hex)
        assertEquals(codec.fingerprint(decoded), codec.fingerprint(codec.decode(command.replace(":", " : ").toByteArray())))
        assertEquals(codec.fingerprint(decoded), codec.fingerprint(codec.decode(command.replace("00000000000a", "00000000000b").toByteArray())))
        assertNotEquals(codec.fingerprint(decoded), codec.fingerprint(codec.decode(command.replace("000000000006", "000000000007").toByteArray())))
    }
    @Test fun `closed envelopes unknown missing null wrong actor injection and version`() {
        val codec = CommandCodec(); val tree = StrictJson.decode(command.toByteArray()) as JObject
        tree.entries.keys.forEach { key ->
            for (replacement in listOf<JsonValue?>(null, JNull, JBoolean(false), JArray(emptyList()))) {
                val f = tree.entries.toMutableMap(); if (replacement == null) f.remove(key) else f[key] = replacement
                assertFailsWith<CodecFault>(key) { codec.decode(StrictJson.encode(JObject(f))) }
            }
        }
        listOf(command.replace("\"principalId\":", "\"actorId\":\"00000000-0000-0000-0000-000000000002\",\"principalId\":"),
            command.replace("\"principalId\"", "\"unknown\""), command.replace("revoke_access", "unknown"), command.replace("00000000000a", "00000000000A"))
            .forEach { assertFailsWith<CodecFault> { codec.decode(it.toByteArray()) } }
        assertEquals(CodecProblem.UNSUPPORTED_FORMAT, assertFailsWith<CodecFault> { codec.decode(command.replace("\"formatVersion\":1", "\"formatVersion\":2").toByteArray()) }.problem)
    }
    @Test fun `all six operations round trip and every payload field enters fingerprint`() {
        val f = WorkflowFixture().baseline(); val d = f.a; val id = UUID(0, 999)
        val payloads = listOf<CommandPayload>(CommandPayload.Propose(d), CommandPayload.Request(id, f.initial.packet),
            CommandPayload.Resolve(f.initial.id, f.initial.digest, f.initial.packet.digest, StructuredAnswer.Approve),
            CommandPayload.Assess(f.baselineAssessment), CommandPayload.Pin(id, id, listOf(d.x()), listOf(d.contextReference()), emptyList(), f.baselineAssessment.id), CommandPayload.Revoke(id))
        val codec = CommandCodec()
        payloads.forEach { p ->
            val c = CoreCommand(id, project, p); val encoded = codec.encode(c)
            assertContentEquals(encoded, codec.encode(codec.decode(encoded)))
            val root = StrictJson.decode(encoded) as JObject
            val payload = root.entries.getValue("payload") as JObject
            payload.entries.keys.forEach { key ->
                val changed = JObject(root.entries + ("payload" to JObject(payload.entries - key)))
                assertFailsWith<CodecFault>("${p.operation}.$key") { codec.decode(StrictJson.encode(changed)) }
            }
        }
        val a = CoreCommand(id, project, CommandPayload.Propose(d))
        val b = CoreCommand(id, project, CommandPayload.Propose(doc(30, "Xa")))
        assertNotEquals(codec.fingerprint(a), codec.fingerprint(b))
        val reordered = packet(f.initial.packet.items.reversed(), f.initial.packet.contexts)
        assertNotEquals(codec.fingerprint(CoreCommand(id, project, CommandPayload.Request(id, f.initial.packet))), codec.fingerprint(CoreCommand(id, project, CommandPayload.Request(id, reordered))))
    }
    @Test fun `body string and final encoded command admission are distinct`() {
        val c = CoreCommand(UUID(0, 999), project, CommandPayload.Propose(doc(30)))
        val encoded = CommandCodec().encode(c)
        for (max in encoded.size-1..encoded.size+1) {
            if (max < encoded.size) assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { CommandCodec(JsonLimits(bytes = max)).decode(encoded) }.problem)
            else CommandCodec(JsonLimits(bytes = max)).decode(encoded)
        }
        val bodySize=(c.payload as CommandPayload.Propose).revision.content.sizeBytes
        for(n in bodySize-1..bodySize+1) {
            if(n < bodySize) assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { CommandCodec(bodyBytes=n).decode(encoded) }.problem)
            else CommandCodec(bodyBytes=n).decode(encoded)
        }
    }
    @Test fun `checked generation boundary and all target preflight`() {
        assertEquals(Long.MAX_VALUE, checkedNextGeneration(Long.MAX_VALUE - 1))
        assertEquals(null, checkedNextGeneration(Long.MAX_VALUE))
        assertTrue(canAdvanceGenerations(listOf(0L, Long.MAX_VALUE - 1)))
        assertFalse(canAdvanceGenerations(listOf(0L, Long.MAX_VALUE)))
        assertFalse(canAdvanceGenerations(listOf(Long.MAX_VALUE, 0L)))
    }

    @Test fun `all independently supplied resolve guards IDs answer variants and assessment project bind`() {
        val f = WorkflowFixture().baseline(); val codec = CommandCodec(); val id = UUID(0,999)
        val original = CoreCommand(id,project,CommandPayload.Resolve(f.initial.id,f.initial.digest,f.initial.packet.digest,StructuredAnswer.Approve))
        val changed = listOf(
            CommandPayload.Resolve(id,f.initial.digest,f.initial.packet.digest,StructuredAnswer.Approve),
            CommandPayload.Resolve(f.initial.id,Digest.parse("0".repeat(64)),f.initial.packet.digest,StructuredAnswer.Approve),
            CommandPayload.Resolve(f.initial.id,f.initial.digest,Digest.parse("0".repeat(64)),StructuredAnswer.Approve),
            CommandPayload.Resolve(f.initial.id,f.initial.digest,f.initial.packet.digest,StructuredAnswer.Reject),
            CommandPayload.Resolve(f.initial.id,f.initial.digest,f.initial.packet.digest,StructuredAnswer.ChangesRequired(mapOf(id to "Exact change"))),
        )
        changed.forEach { assertNotEquals(codec.fingerprint(original),codec.fingerprint(CoreCommand(id,project,it))) }
        assertFailsWith<CodecFault> { CoreCommand(id,otherProject,CommandPayload.Assess(f.baselineAssessment)) }
        val root = StrictJson.decode(codec.encode(original)) as JObject
        val p = (root.entries.getValue("payload") as JObject).entries
        val badAnswer = obj("kind" to str("approve"),"comments" to JArray(emptyList()))
        assertFailsWith<CodecFault> { codec.decode(StrictJson.encode(JObject(root.entries + ("payload" to JObject(p + ("answer" to badAnswer)))))) }
        val comments = obj("kind" to str("changes-required"),"comments" to JArray(listOf(obj("itemId" to uuid(id),"text" to str("one")),obj("itemId" to uuid(id),"text" to str("two")))))
        assertEquals(CodecProblem.DUPLICATE_IDENTITY,assertFailsWith<CodecFault> { codec.decode(StrictJson.encode(JObject(root.entries + ("payload" to JObject(p + ("answer" to comments)))))) }.problem)
    }

    @Test fun `independent grant golden and record key binary framing`() {
        val principal = UUID(0,6); val project = UUID(0,1)
        val key = obj("principalId" to uuid(principal),"projectId" to uuid(project))
        val encoded = StrictJson.encode(CoreRecordCodec.envelope("grants",key,obj("capabilities" to JArray(listOf(str("PLAN"),str("READ"))))))
        val expected = """{"family":"grants","formatVersion":1,"key":{"principalId":"00000000-0000-0000-0000-000000000006","projectId":"00000000-0000-0000-0000-000000000001"},"payload":{"capabilities":["PLAN","READ"]}}"""
        assertEquals(expected,encoded.decodeToString())
        val state=ApprovalWorkflow.start(AccessPolicy(listOf(Grant(principal,project,setOf(Capability.READ,Capability.PLAN)))))
        assertEquals(expected,entries(state).single { "/records/grants/" in it.path }.bytes().decodeToString())
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            listOf("writhelm.record-key.v1","grants","""{"principalId":"00000000-0000-0000-0000-000000000006","projectId":"00000000-0000-0000-0000-000000000001"}""").forEach { value -> val b=value.toByteArray(); out.writeInt(b.size); out.write(b) }
        }
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("core/records/grants/${h.take(2)}/$h.json",CoreRecordCodec.path("grants",key))
    }
}
