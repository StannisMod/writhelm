// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import org.junit.jupiter.api.Test
import kotlin.test.*

class KnowledgeProtocolTest {
    private fun value(raw:String)=StrictJson.decode(raw.toByteArray())
    @Test fun declarationAdmitsExactWirePayloadAndRefusesUnknownFieldsBeforeDomainConstruction() {
        val raw="""{"formatVersion":1,"requestKey":"00000000-0000-0000-0000-000000000001","projectId":"00000000-0000-0000-0000-000000000002","operation":"propose_revision","payload":{"revisionKey":{"document":{"projectId":"00000000-0000-0000-0000-000000000002","documentId":"00000000-0000-0000-0000-000000000003"},"revisionId":"00000000-0000-0000-0000-000000000004"},"body":"A","structure":{"clauses":[{"id":"TEST-01","span":{"start":0,"endExclusive":1}}],"context":[]}}}"""
        val v=value(raw);KnowledgeProtocol.validateCommand(v)
        val codec=CommandCodec();assertEquals(raw.length,codec.encode(codec.decode(raw.toByteArray())).size)
        val wrong=raw.replace("\"body\":\"A\"","\"body\":\"A\",\"actorId\":\"owner\"")
        assertFailsWith<CodecFault>{codec.decode(wrong.toByteArray())}
        val payload=(v as JObject).entries.getValue("payload")
        assertTrue(KnowledgeProtocol.payloads.getValue("propose_revision").accepts(payload))
    }
    @Test fun exportedSchemaAndRuntimeAdmissionComeFromTheSameDeclaration() {
        val exported=KnowledgeProtocol.exported() as JObject
        val schemas=exported.entries.getValue("commands") as JObject
        assertEquals(KnowledgeProtocol.payloads.keys,schemas.entries.keys)
        val proposal=schemas.entries.getValue("propose_revision") as JObject
        val properties=proposal.entries.getValue("properties") as JObject
        assertEquals(setOf("revisionKey","body","structure"),properties.entries.keys)
        assertEquals(JBoolean(false),proposal.entries["additionalProperties"])
        assertTrue((proposal.entries.getValue("\$defs") as JObject).entries.containsKey("followup"))
    }
    @Test fun authoredClausesAndExactReadSelectorsHaveClosedBoundedTypedShapes() {
        val schema=KnowledgeProtocol.authoring.getValue("propose_document")
        assertTrue(schema.accepts(value("""{"intentId":"draft-1","clauses":[{"id":"TEST-01","title":"Заголовок 🧭","text":"Точный текст\r\nБез нормализации."}]}""")))
        for(raw in listOf(
            """{"intentId":"draft-1","clauses":[]}""",
            """{"intentId":"draft-1","clauses":[{"id":"TEST-01","title":"Title","text":"Text","approved":true}]}""",
            """{"intentId":"draft-1","clauses":[{"id":"TEST-01","title":"Title","text":"Text"}],"owner":"me"}"""
        ))assertFalse(schema.accepts(value(raw)))
        val read=KnowledgeProtocol.reads.getValue("list_documents")
        assertTrue(read.accepts(value("""{"cursor":null,"pageSize":32}""")))
        assertFalse(read.accepts(value("""{"cursor":null,"pageSize":33}""")))
        assertFalse(read.accepts(value("""{"cursor":null,"pageSize":32,"latest":true}""")))
    }
}
