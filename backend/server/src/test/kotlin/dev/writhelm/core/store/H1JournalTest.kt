// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.Test

class H1JournalTest {
    private fun frozen(name: String): JsonValue {
        val root=Path.of(System.getenv("WRITHELM_H1_READY_ROOT") ?: error("WRITHELM_H1_READY_ROOT required for exact frozen vectors"))
        return StrictJson.decode(Files.readAllBytes(root.resolve(name)),JsonLimits(bytes=8*1024*1024,stringBytes=2*1024*1024,depth=128,collection=8192,tokens=500000))
    }
    private fun fields(value: JsonValue)=(value as JObject).entries
    private fun composed(name: String): JsonValue=StrictJson.decode(Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_COORDINATION_READY_ROOT")),name)),
        JsonLimits(bytes=8*1024*1024,stringBytes=2*1024*1024,depth=128,collection=8192,tokens=500000))
    /** Test-only framing oracle seam. Does NOT purport to validate these domain payloads. */
    private fun framingBinding()=H1PublicationBindings(
        canonicalize={ raw ->
            val v=fields(StrictJson.decode(raw)); val payload=v.getValue("payload")
            val p=fields(payload)
            val normalized=when(v.getValue("operation").string()) {
                "replace_work_grants" -> JObject(p+("permissions" to JArray(p.getValue("permissions").list().sortedBy { it.string() })))
                else -> payload
            }
            StrictJson.encode(JObject(v+("payload" to normalized)))
        },authorize={ _,_ -> error("pure framing only") },allowedWrites={ _,_ -> error("pure framing only") },
        validateRecord={ _,_,_ -> error("pure framing only") },preflight={ _,_,_ -> error("pure framing only") },validateBoundary={ _,_,_ -> error("pure framing only") })
    private fun receipt(operation: String,outcome: LogicalOutcome,writes: List<WriteDescriptor> = emptyList(),fp: Int=3)=Receipt(
        id(1),CommandIdentity(id(3),id(2),operation,id(5)),fp,"1".repeat(64),GitOid("sha1","2".repeat(40)),id(4),outcome,writes,3)

    @Test fun `all34 independent parent command3 fingerprints and both legacy vectors are exact`() {
        val f=fields(frozen("goldens-v2.json")); val b=framingBinding()
        val vectors=f.getValue("command3").list(); assertEquals(34,vectors.size)
        vectors.forEach { row ->
            val r=fields(row); val command=StoreCommand.decodeH1(StrictJson.encode(r.getValue("input")),b)
            assertEquals(r.getValue("expectedSha256").string(),command.fingerprint,command.operation)
            assertContentEquals(r.getValue("expectedSemanticUtf8").string().toByteArray(),StrictJson.encode(JObject(fields(StrictJson.decode(command.bytes()))-"requestKey")))
        }
        f.getValue("preservedFingerprint1and2").list().forEach { row ->
            val r=fields(row); val c=StoreCommand.decode(StrictJson.encode(r.getValue("input")))
            assertEquals(r.getValue("expectedSha256").string(),c.fingerprint)
        }
    }

    @Test fun `frozen operation-specific work codes catalog and null generation correspondence are exhaustive`() {
        val schema=fields(fields(composed("schemas/journal-v3.json")).getValue("$"+"defs"))
        val conditions=fields(schema.getValue("receipt")).getValue("allOf").list()
        val checked=mutableSetOf<String>()
        conditions.forEach { condition ->
            val r=fields(condition)
            val key=(fields(fields(r.getValue("if")).getValue("properties"))["key"] as? JObject)?.entries ?: return@forEach
            val operation=fields(fields(key.getValue("properties")).getValue("operation"))["const"]?.string() ?: return@forEach
            if(operation !in H1Catalog.workOperations)return@forEach
            val payload=fields(fields(fields(fields(r.getValue("then")).getValue("properties")).getValue("payload")).getValue("properties"))
            val outcome=payload["outcome"] ?: return@forEach // separate exact write constraints, not an outcome branch
            val variants=fields(outcome).getValue("oneOf").list()
            val rejected=fields(variants[1]).getValue("allOf").list()[1]
            val codes=fields(fields(fields(rejected).getValue("properties")).getValue("code")).getValue("enum").list().map { it.string() }.toSet()
            assertEquals(codes,H1Catalog.workCodes.getValue(operation),operation); checked.add(operation)
        }
        assertEquals(H1Catalog.workOperations,checked)
        val replies=fields(frozen("concrete-command-replies-v2.json"))
        val newReplies=composed("examples/effects.json").list().associate { row -> fields(row).getValue("operation").string() to fields(row).getValue("reply") }
        H1Catalog.workOperations.forEach { op ->
            val outcome=if(op=="replace_isolated_assignment")fields(fields(composed("examples/replacement-260-receipt.json")).getValue("payload")).getValue("outcome")
                else fields(replies[op] ?: newReplies.getValue(op)).getValue("outcome")
            val result=fields(outcome).getValue("result")
            val key=H1Catalog.result(op,result)
            assertEquals(key.mutable,fields(result).getValue("generation")!==JNull,op)
            val invalid=JObject(fields(result)+("generation" to if(key.mutable)JNull else num(0)))
            assertFails(op) { H1Catalog.result(op,invalid) }
        }
    }

    @Test fun `all31 composed work keys bodies and seven auth keys use closed generated paths and descriptors`() {
        val defs=fields(fields(composed("schemas/work-model2.json")).getValue("$"+"defs"))
        val branches=fields(defs.getValue("record")).getValue("oneOf").list()
        assertEquals(31,branches.size)
        branches.forEach { branch ->
            val p=fields(fields(branch).getValue("properties")); val family=fields(p.getValue("family")).getValue("const").string()
            val ref=fields(p.getValue("key")).getValue("$"+"ref").string().substringAfterLast('/')
            val keyProps=fields(fields(defs.getValue(ref)).getValue("properties"))
            val key=JObject(keyProps.mapValues { (name,_) -> if(name in setOf("revision","generation"))num(0) else uuid(id(2)) })
            val record=RecordKey("work",family,key)
            assertTrue(record.path.startsWith("work/records/$family/"))
            assertNotNull(WriteDescriptor(record,null,"2".repeat(64)))
            if(!record.mutable)assertFails { WriteDescriptor(record,"1".repeat(64),"2".repeat(64)) }
            assertFails { WriteDescriptor(record,"1".repeat(64),null) }
        }
        val body=RecordKey("work","bodies",str("a".repeat(64)))
        assertEquals("work/bodies/aa/${"a".repeat(64)}.txt",body.path)
        assertFails { WriteDescriptor(body,"1".repeat(64),"2".repeat(64)) }
        listOf("principals","projects","credentials","controllers","delegations","grant-guards","work-grants").forEach { family ->
            val key=if(family in setOf("grant-guards","work-grants"))obj("principalId" to uuid(id(3)),"projectId" to uuid(id(2))) else uuid(id(3))
            val record=RecordKey("auth",family,key)
            assertTrue(record.path.startsWith("auth/records/$family/")); assertFails { WriteDescriptor(record,"1".repeat(64),null) }
        }
        assertFails { RecordKey("work","unknown",uuid(id(1))) }
    }

    @Test fun `work rejection code operation fingerprint and domain mismatch refuse with positive neighbor`() {
        val good=receipt("set_task_head",LogicalOutcome.Rejected("work","STALE_GENERATION"))
        assertContentEquals(JournalCodec.encode(good),JournalCodec.encode(JournalCodec.decode(JournalCodec.encode(good),good.path)))
        assertFails { receipt("set_task_head",LogicalOutcome.Rejected("work","NOT_ACCEPTED")) }
        assertFails { receipt("set_task_head",LogicalOutcome.Rejected("auth","AUTH_STALE_CREDENTIAL")) }
        assertFails { receipt("set_task_head",LogicalOutcome.Rejected("work","STALE_GENERATION"),fp=2) }
        assertFails { Receipt(id(1),good.identity,3,good.fingerprint,good.validatedAt,id(4),good.outcome,emptyList()) }
    }

    @Test fun `success primary key digest operation and descriptor exact order bind before encoding`() {
        val key=RecordKey("work","task-heads",obj("projectId" to uuid(id(2)),"taskId" to uuid(id(8))))
        fun outcome(op: String="set_task_head",digest: String="2".repeat(64))=LogicalOutcome.Success(op,obj("key" to key.key,"generation" to num(0),"digest" to str(digest)),false)
        val write=WriteDescriptor(key,null,"2".repeat(64))
        val r=receipt("set_task_head",outcome(),listOf(write))
        assertEquals(3,JournalCodec.decode(JournalCodec.encode(r),r.path).journalVersion)
        assertFails { receipt("set_task_head",outcome(digest="3".repeat(64)),listOf(write)) }
        assertFails { receipt("set_task_head",outcome(),emptyList()) }
        assertFails { receipt("set_task_head",outcome(),listOf(write,write)) }
        assertFails { receipt("update_plan",outcome(),listOf(write)) }
        assertFails { LogicalOutcome.Success("set_task_head",outcome().result,true) }
    }

    @Test fun `policy2 matches independent exact development policy and legacy policy bytes stay pinned`() {
        val selected=fields(frozen("operational-budgets.json")).getValue("admission")
        val bytes=AdmissionPolicy(formatVersion=2).bytes()
        assertContentEquals(StrictJson.encode(selected),bytes)
        assertContentEquals(bytes,AdmissionPolicy.decode(bytes).bytes())
        assertEquals(1,AdmissionPolicy.decode(AdmissionPolicy().bytes()).formatVersion)
        val n=fields(StrictJson.decode(bytes))
        assertFails { AdmissionPolicy.decode(StrictJson.encode(JObject(n-"authTokens"))) }
        assertFails { AdmissionPolicy.decode(StrictJson.encode(JObject(n+("workTokens" to num(100001))))) }
    }
}
