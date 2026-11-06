// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class WorkIdentityCodecTest {
    private val project = UUID(0, 1)
    private val id = UUID(0, 2)
    private val hash = Digest.parse("1".repeat(64))
    private val codec = WorkIdentityCodec()

    private fun sample(kind: WorkKeyKind, generation: Long = Long.MAX_VALUE): WorkKey = when (kind) {
        WorkKeyKind.TASK -> WorkKey.Task(project, id)
        WorkKeyKind.TASK_REVISION -> WorkKey.TaskRevision(project, id, generation)
        WorkKeyKind.PROFILE -> WorkKey.Profile(project, id, generation)
        WorkKeyKind.REVISION -> WorkKey.Revision(project, id, generation)
        WorkKeyKind.ID -> WorkKey.Id(project, id)
        WorkKeyKind.RESOURCE -> WorkKey.Resource(project, id)
        WorkKeyKind.ASSIGNMENT_BINDING -> WorkKey.AssignmentBinding(project, id, generation)
        WorkKeyKind.CONTROLLER_CONTINUATION -> WorkKey.ControllerContinuation(project, UUID(0, 3), id)
    }
    private fun fixture(variable: String, name: String): JsonValue {
        val root = assertNotNull(System.getenv(variable), "$variable must name exact identified fixtures")
        return StrictJson.decode(Files.readAllBytes(Path.of(root).resolve(name)), JsonLimits(bytes = 2 * 1024 * 1024))
    }

    @Test fun `closed registry matches every frozen family key kind and mutability`() {
        val original = fixture("WRITHELM_H1_READY_ROOT", "inventory.json") as JObject
        val inventory = fixture("WRITHELM_COORDINATION_READY_ROOT", "inventory.json") as JObject
        assertEquals(27, original.entries.size)
        assertEquals(setOf("assignment-replacements", "conditions", "condition-confirmations", "continuations"),
            inventory.entries.keys - original.entries.keys)
        assertEquals(inventory.entries.keys, WorkFamily.entries.map { it.tag }.toSet())
        val schemaKinds = mapOf("taskKey" to WorkKeyKind.TASK, "taskRevisionKey" to WorkKeyKind.TASK_REVISION,
            "profileKey" to WorkKeyKind.PROFILE, "revisionKey" to WorkKeyKind.REVISION, "idKey" to WorkKeyKind.ID,
            "resourceKey" to WorkKeyKind.RESOURCE, "assignmentBindingKey" to WorkKeyKind.ASSIGNMENT_BINDING,
            "controllerContinuationKey" to WorkKeyKind.CONTROLLER_CONTINUATION)
        WorkFamily.entries.forEach { family ->
            val entry = (inventory.entries.getValue(family.tag) as JObject).entries
            val keyRef = ((entry.getValue("key") as JObject).entries.getValue("\$ref")).string().substringAfterLast('/')
            assertEquals(schemaKinds.getValue(keyRef), family.keyKind)
            assertEquals(JBoolean(family.mutable), entry.getValue("mutable"))
            original.entries[family.tag]?.let { legacy ->
                val fields = (legacy as JObject).entries
                val oldKey = ((fields.getValue("key") as JObject).entries.getValue("\$ref")).string().substringAfterLast('/')
                assertEquals(oldKey, keyRef)
                assertEquals(fields.getValue("mutable"), entry.getValue("mutable"))
            }
        }
    }

    @Test fun `all key kinds and all families round trip exact max counters`() {
        val paths = mutableSetOf<String>()
        WorkFamily.entries.forEach { family ->
            val key = sample(family.keyKind)
            assertEquals(key, codec.decodeKey(family, codec.encodeKey(key)))
            val identity = WorkRecordIdentity(family, key)
            val path = codec.recordPath(identity)
            assertTrue(path.matches(Regex("work/records/${family.tag}/[0-9a-f]{2}/[0-9a-f]{64}\\.json")))
            assertTrue(paths.add(path))
            if (family.mutable) {
                val selector = WorkMutableSelector(identity, Long.MAX_VALUE)
                assertEquals(selector, codec.decodeMutableSelector(codec.encodeMutableSelector(selector)))
                assertFailsWith<IllegalArgumentException> { WorkReference(identity, hash) }
            } else {
                val reference = WorkReference(identity, hash)
                assertEquals(reference, codec.decodeReference(codec.encodeReference(reference)))
                assertFailsWith<IllegalArgumentException> { WorkMutableSelector(identity, 0) }
            }
        }
        assertEquals(31, paths.size)
    }

    @Test fun `independently supplied original record key paths remain exact`() {
        val vectors = fixture("WRITHELM_H1_WORK_READY_ROOT", "goldens/independent-v1.json") as JObject
        vectors.entries.getValue("recordKeys").list().forEach { entry ->
            val f = (entry as JObject).entries
            val family = WorkFamily.entries.firstOrNull { it.tag == f.getValue("family").string() } ?: return@forEach
            val bytes = f.getValue("expectedKeyUtf8").string().toByteArray(Charsets.UTF_8)
            val key = codec.decodeKey(family, bytes)
            assertContentEquals(bytes, codec.encodeKey(key))
            assertEquals(f.getValue("expectedPath").string(), codec.recordPath(WorkRecordIdentity(family, key)))
        }
    }

    @Test fun `wrong family key type mutable reference and immutable selector refuse`() {
        WorkFamily.entries.forEach { family ->
            WorkKeyKind.entries.filter { it != family.keyKind }.forEach { other ->
                assertFailsWith<IllegalArgumentException> { WorkRecordIdentity(family, sample(other)) }
                assertFailsWith<CodecFault> { codec.decodeKey(family, codec.encodeKey(sample(other))) }
            }
        }
        val key = codec.encodeKey(sample(WorkKeyKind.ID)).decodeToString()
        assertFailsWith<CodecFault> { codec.decodeReference("""{"family":"assignments","key":$key,"digest":"${hash.hex}"}""".toByteArray()) }
        assertFailsWith<CodecFault> { codec.decodeMutableSelector("""{"family":"results","key":$key,"generation":0}""".toByteArray()) }
        listOf("Results", "../results", "bodies", "unknown").forEach { tag ->
            assertFailsWith<CodecFault> { codec.decodeReference("""{"family":"$tag","key":$key,"digest":"${hash.hex}"}""".toByteArray()) }
        }
    }

    @Test fun `every key field is mandatory closed canonical and not an actor claim`() {
        WorkFamily.entries.forEach { family ->
            val key = StrictJson.decode(codec.encodeKey(sample(family.keyKind))) as JObject
            key.entries.keys.forEach { field ->
                assertFailsWith<CodecFault> { codec.decodeKey(family, StrictJson.encode(JObject(key.entries - field))) }
                assertFailsWith<CodecFault> { codec.decodeKey(family, StrictJson.encode(JObject(key.entries + (field to JNull)))) }
            }
            assertFailsWith<CodecFault> { codec.decodeKey(family, StrictJson.encode(JObject(key.entries + ("actor" to str("owner"))))) }
        }
        val encoded = codec.encodeKey(sample(WorkKeyKind.ID)).decodeToString()
        assertFailsWith<CodecFault> { codec.decodeKey(WorkFamily.RESULTS, encoded.replace(project.toString(), "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF").toByteArray()) }
    }

    @Test fun `strict raw numbers duplicates UTF8 and surrogates refuse before conversion`() {
        val key = codec.encodeKey(sample(WorkKeyKind.TASK_REVISION, 0)).decodeToString()
        for (number in listOf("-0", "1.0", "1e0", "-1", "9223372036854775808", "01")) {
            val bad = key.replace("\"revision\":0", "\"revision\":$number").toByteArray()
            assertFailsWith<CodecFault>(number) { codec.decodeKey(WorkFamily.TASK_REVISIONS, bad) }
        }
        val duplicate = key.replace("\"revision\":0", "\"revision\":0,\"revi\\u0073ion\":0")
        assertFailsWith<CodecFault> { codec.decodeKey(WorkFamily.TASK_REVISIONS, duplicate.toByteArray()) }
        assertFailsWith<CodecFault> { codec.decodeKey(WorkFamily.TASK_REVISIONS, key.replace(project.toString(), "\\uD800").toByteArray()) }
        assertFailsWith<CodecFault> { codec.decodeKey(WorkFamily.TASK_REVISIONS, byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }
        assertFailsWith<CodecFault> { codec.decodeKey(WorkFamily.TASK_REVISIONS, byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + key.toByteArray()) }
        assertFailsWith<IllegalArgumentException> { WorkKey.TaskRevision(project, id, -1) }
        assertFailsWith<IllegalArgumentException> { WorkKey.AssignmentBinding(project, id, -1) }
    }

    @Test fun `body length is checked Int32 and logical path cannot choose a filesystem location`() {
        for (size in listOf(0, 1, Int.MAX_VALUE)) {
            val body = WorkBodyReference(hash, size)
            assertEquals(body, codec.decodeBodyReference(codec.encodeBodyReference(body)))
            assertEquals("work/bodies/11/${hash.hex}.txt", codec.bodyPath(body))
        }
        for (size in listOf("-1", "2147483648", "1.0")) {
            assertFailsWith<CodecFault> { codec.decodeBodyReference("""{"digest":"${hash.hex}","byteLength":$size}""".toByteArray()) }
        }
        assertFailsWith<CodecFault> { codec.decodeBodyReference("""{"digest":"${hash.hex}","byteLength":0,"path":"outside"}""".toByteArray()) }
    }

    @Test fun `explicit model variant nullable exact Unicode and scalar bounds without resolution`() {
        val values = listOf(WorkModelReference("provider", "model/with/slashes", null),
            WorkModelReference("e\u0301", "🙂".repeat(256), "é"), WorkModelReference("é", "model", "high"))
        values.forEach { assertEquals(it, codec.decodeModelReference(codec.encodeModelReference(it))) }
        assertNotEquals(codec.encodeModelReference(WorkModelReference("é", "model", null)).decodeToString(),
            codec.encodeModelReference(WorkModelReference("e\u0301", "model", null)).decodeToString())
        assertFailsWith<IllegalArgumentException> { WorkModelReference("", "model", null) }
        assertFailsWith<IllegalArgumentException> { WorkModelReference("provider", "🙂".repeat(257), null) }
        assertFailsWith<IllegalArgumentException> { WorkModelReference("provider", "model", "") }
        assertFailsWith<IllegalArgumentException> { WorkModelReference("provider", "\uD800", null) }
        for (raw in listOf("""{"providerId":"provider","modelId":"model"}""",
            """{"providerId":"provider","modelId":"model","variant":1}""",
            """{"providerId":"provider","modelId":"model","variant":null,"token":"sentinel"}""")) {
            assertFailsWith<CodecFault> { codec.decodeModelReference(raw.toByteArray()) }
        }
    }

    @Test fun `encoded byte boundaries and structural token limits stay distinct`() {
        val key = sample(WorkKeyKind.CONTROLLER_CONTINUATION)
        val bytes = codec.encodeKey(key)
        for (maximum in bytes.size - 1..bytes.size + 1) {
            val bounded = WorkIdentityCodec(JsonLimits(bytes = maximum))
            if (maximum < bytes.size) {
                assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { bounded.encodeKey(key) }.problem)
                assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { bounded.decodeKey(WorkFamily.CONTROLLER_CONTINUATIONS, bytes) }.problem)
            } else {
                assertContentEquals(bytes, bounded.encodeKey(key))
                assertEquals(key, bounded.decodeKey(WorkFamily.CONTROLLER_CONTINUATIONS, bytes))
            }
        }
        val tinyTokens = WorkIdentityCodec(JsonLimits(tokens = 1))
        assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { tinyTokens.encodeKey(key) }.problem)
        assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { tinyTokens.decodeKey(WorkFamily.CONTROLLER_CONTINUATIONS, bytes) }.problem)
    }
}
