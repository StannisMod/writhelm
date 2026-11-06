// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class Model2CodecTest {
    private fun fixture(name: String): JsonValue {
        val root=Path.of(requireNotNull(System.getenv("WRITHELM_COORDINATION_READY_ROOT")))
        return StrictJson.decode(Files.readAllBytes(root.resolve(name)),JsonLimits(bytes=4*1024*1024,depth=128,collection=8192,tokens=500000))
    }
    private fun JsonValue.field(name: String)=(this as JObject).entries.getValue(name)
    @Test fun sealedNewCommandFingerprintsAndRecordKeysAreExact() {
        val codec=WorkCodec()
        val vectors=fixture("goldens/connected-command3.json").field("vectors").list()
        assertEquals(4,vectors.size)
        vectors.forEach { v ->
            val command=codec.decodeCommand(StrictJson.encode(v.field("input")))
            assertEquals(v.field("sha256").string(),codec.fingerprint(command).hex)
            assertContentEquals(v.field("canonicalUtf8").string().toByteArray(Charsets.UTF_8),codec.encodeCommand(command))
            val record=codec.decodeRecord(v.field("recordUtf8").string().toByteArray(Charsets.UTF_8))
            assertEquals(v.field("recordDigest").string(),codec.digest(record).hex)
            assertEquals(v.field("recordKeyPath").string(),identityCodec.recordPath(record.identity))
            val changed=command.copy(requestKey=java.util.UUID.randomUUID())
            assertEquals(codec.fingerprint(command),codec.fingerprint(changed))
        }
    }
    @Test fun sealedReplacementAndExistingCommand3BytesRemainExact() {
        val vectors=fixture("goldens/preserved-old.json").field("command3").list()
        val work=WorkCodec();val auth=dev.writhelm.core.auth2.Auth3CommandCodec()
        vectors.forEach { v ->
            val bytes=StrictJson.encode(v.field("input"))
            val op=v.field("input").field("operation").string()
            val actual=if(op in dev.writhelm.core.store.H1Catalog.authOperations)auth.fingerprint(auth.decode(bytes)) else work.fingerprint(work.decodeCommand(bytes))
            assertEquals(v.field("expectedSha256").string(),actual.hex,op)
        }
        val command=fixture("examples/commands.json").field("replace_isolated_assignment")
        assertEquals(fixture("goldens/preserved-recovery.json").field("sha256").string(),work.fingerprint(work.decodeCommand(StrictJson.encode(command))).hex)
    }
    @Test fun closedFieldsCountersAndChangedBaseRefuseBeforeDomainReduction() {
        val examples=fixture("examples/commands.json")
        val confirm=examples.field("confirm_condition") as JObject
        val payload=confirm.entries.getValue("payload") as JObject
        val forged=JObject(confirm.entries+("payload" to JObject(payload.entries+("actor" to str("owner")))))
        assertFailsWith<CodecFault> { WorkCodec().decodeCommand(StrictJson.encode(forged)) }
        val prepare=examples.field("prepare_continuation") as JObject
        val p=prepare.entries.getValue("payload") as JObject
        val data=p.entries.getValue("data") as JObject
        val changed=JObject(data.entries+mapOf("mode" to str("authorized-rebind"),"rebind" to JNull))
        assertFailsWith<CodecFault> { WorkCodec().decodeCommand(StrictJson.encode(JObject(prepare.entries+("payload" to JObject(p.entries+("data" to changed)))))) }
        assertEquals(31,WorkFamily.entries.size)
        assertEquals(34,dev.writhelm.core.store.H1Catalog.workOperations.size)
    }
}
