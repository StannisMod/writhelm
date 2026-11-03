// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

internal fun id(n: Int)=UUID(0,n.toLong())
internal fun authCommand(key: Int=5,generation: Int=0)=StoreCommand.decode("""{"formatVersion":2,"requestKey":"${id(key)}","projectId":"${id(2)}","operation":"revoke_credential","payload":{"credentialId":"${id(4)}","expectedGeneration":$generation}}""".toByteArray())

class JournalContractTest {
    @Test fun `independent command v2 golden and request key exclusion`() {
        assertEquals("fe4611fbb1e3038c17d417382462d3f72e3507508d64fb4594d8163cc22465f0",authCommand().fingerprint)
        assertEquals(authCommand().fingerprint,authCommand(6).fingerprint)
        assertNotEquals(authCommand().fingerprint,authCommand(generation=1).fingerprint)
    }
    @Test fun `receipt round trip excludes current commit and retains original credential`() {
        val c=authCommand(); val r=Receipt(id(1),CommandIdentity(id(3),c.projectId,c.operation,c.requestKey),2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Rejected("auth","AUTH_STALE_CREDENTIAL"),emptyList())
        val bytes=JournalCodec.encode(r)
        val restored=JournalCodec.decode(bytes,r.path)
        assertContentEquals(bytes,JournalCodec.encode(restored))
        assertEquals(id(4),restored.authenticatedByCredentialId)
        assertEquals(0,restored.writes.size)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("publicationCommit"))
    }
    @Test fun `closed receipt shape and embedded key path binding`() {
        val c=authCommand(); val r=Receipt(id(1),CommandIdentity(id(3),c.projectId,c.operation,c.requestKey),2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Rejected("auth","AUTH_STALE_CREDENTIAL"),emptyList())
        val b=JournalCodec.encode(r)
        assertFails { JournalCodec.decode(b,r.path.replace(".json","x.json")) }
        assertFails { JournalCodec.decode(b.toString(Charsets.UTF_8).replace("\"formatVersion\":2","\"formatVersion\":1").toByteArray(),r.path) }
        assertFails { JournalCodec.decode(b.toString(Charsets.UTF_8).replace("\"AUTH_STALE_CREDENTIAL\"","\"FORBIDDEN\"").toByteArray(),r.path) }
        assertFails { JournalCodec.decode(b.toString(Charsets.UTF_8).replace("\"storeId\":","\"publicationCommit\":null,\"storeId\":").toByteArray(),r.path) }
    }
    @Test fun `descriptor paths typed and immutable effects cannot be deleted`() {
        val k=RecordKey("core","requests",uuid(id(8)))
        assertTrue(k.path.startsWith("core/records/requests/"))
        assertFails { RecordKey("other","requests",uuid(id(8))) }
        assertFails { RecordKey("auth","credentials",str("not-uuid")) }
        assertFails { WriteDescriptor(k,"0".repeat(64),"0".repeat(64)) }
        assertFails { WriteDescriptor(k,null,null) }
        assertFails { GitOid("sha1","0".repeat(64)) }
    }
    @Test fun `rejected and replayed outcomes cannot claim changes`() {
        val c=authCommand(); val identity=CommandIdentity(id(3),c.projectId,c.operation,c.requestKey)
        val w=WriteDescriptor(RecordKey("auth","credentials",uuid(id(4))),"1".repeat(64),"2".repeat(64))
        assertFails { Receipt(id(1),identity,2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Rejected("auth","AUTH_STALE_CREDENTIAL"),listOf(w)) }
        assertFails { Receipt(id(1),identity,2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Success(c.operation,uuid(id(4)),true),listOf(w)) }
    }
    @Test fun `receipt byte and descriptor budgets are explicit`() {
        val c=authCommand(); val r=Receipt(id(1),CommandIdentity(id(3),c.projectId,c.operation,c.requestKey),2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Rejected("auth","AUTH_STALE_CREDENTIAL"),emptyList())
        val n=JournalCodec.encode(r).size
        assertEquals(n,JournalCodec.encode(r,n).size)
        assertFails { JournalCodec.encode(r,n-1) }
    }
    @Test fun `raw actor unknown fields and auth version coercion refused`() {
        val c=authCommand().bytes().toString(Charsets.UTF_8)
        assertFails { StoreCommand.decode(c.replace("\"formatVersion\":2","\"formatVersion\":1").toByteArray()) }
        assertFails { StoreCommand.decode(c.replace("\"expectedGeneration\":0","\"expectedGeneration\":0,\"actor\":\"owner\"").toByteArray()) }
        assertFails { StoreCommand.decode(c.replace("\"expectedGeneration\":0","\"expectedGeneration\":-0").toByteArray()) }
    }
}
