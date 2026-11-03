// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Synthetic trusted ports ONLY: these tests do not qualify credential verification or auth laws. */
class StoreContractTest {
    private fun directory(): Path=storeTestDirectory("store-test-")
    private fun ports()=StorePorts(
        namespaces=mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },
            "policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),
        validateTransition={ _,_,_,_-> },validateHistoricalReceipt={ _,_-> })
    private fun genesis()=listOf(
        StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),
        StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))),
        StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
    private fun rejecting()=CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) }
    private fun access()=CommandAccess { _,_-> AuthenticatedActor(id(3),id(4)) }
    @Test fun `genesis replay conflict denied replay and exclusive owner`() {
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports()).use { s ->
            assertFails { GitCommandStore.open(dir,id(1),ports()) }
            val first=s.execute(authCommand(),access(),rejecting()) as CommandResult.Settled
            assertFalse(first.commandReplayed)
            val replay=s.execute(authCommand(),access(),CommandEvaluator { _,_,_-> error("must not evaluate") }) as CommandResult.Settled
            assertTrue(replay.commandReplayed)
            assertEquals(first.receipt.fingerprint,replay.receipt.fingerprint)
            assertIs<CommandResult.IdentityConflict>(s.execute(authCommand(generation=1),access(),rejecting()))
            assertIs<CommandResult.Denied>(s.execute(authCommand(),CommandAccess { _,_-> null },rejecting()))
            val head=s.read(id(2),ReadAccess { _,_-> true }) { it.oid }
            assertEquals(first.publication,head)
        }
        GitCommandStore.open(dir,id(1),ports()).use { s ->
            assertTrue((s.execute(authCommand(),access(),rejecting()) as CommandResult.Settled).commandReplayed)
        }
        assertFails { GitCommandStore.open(dir,id(99),ports()) }
        assertFails { GitCommandStore.initialize(dir,id(1),genesis(),ports()) }
    }
    @Test fun `single captured snapshot exact bytes and current read authorization`() {
        val dir=directory(); var authorized: GitOid?=null
        GitCommandStore.initialize(dir,id(1),genesis(),ports()).use { s ->
            val bytes=s.read(id(2),ReadAccess { snapshot,_-> authorized=snapshot.oid; true }) { snap ->
                assertEquals(authorized,snap.oid); snap.file("core/format.json")!!
            }
            assertContentEquals(StrictJson.encode(CoreRecordCodec.header()),bytes)
            assertFails { s.read(id(2),ReadAccess { _,_-> false }) { error("must not disclose") } }
        }
    }
    @Test fun `rejection cannot carry a business effect or allocate a premature receipt`() {
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports()).use { s ->
            val result=s.execute(authCommand(),access(),CommandEvaluator { _,_,_->
                Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),listOf(RecordChange(RecordKey("auth","credentials",uuid(id(4))),byteArrayOf(1))))
            })
            assertIs<CommandResult.Refused>(result)
            val after=s.execute(authCommand(),access(),rejecting()) as CommandResult.Settled
            assertFalse(after.commandReplayed)
        }
    }
    @Test fun `later state same principal new trusted credential preserves rejection provenance`() {
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports()).use { s ->
            val first=s.execute(authCommand(),access(),rejecting()) as CommandResult.Settled
            s.execute(authCommand(6),access(),rejecting())
            val replay=s.execute(authCommand(),CommandAccess { _,_-> AuthenticatedActor(id(3),id(44)) },CommandEvaluator { _,_,_-> error("must retain original rejection") }) as CommandResult.Settled
            assertTrue(replay.commandReplayed)
            assertEquals(id(4),replay.receipt.authenticatedByCredentialId)
            assertEquals(first.receipt.validatedAt,replay.receipt.validatedAt)
            assertNull(replay.publication) // Never relabel current H as the original publishing commit.
            val other=s.execute(authCommand(),CommandAccess { _,_-> AuthenticatedActor(id(99),id(100)) },rejecting()) as CommandResult.Settled
            assertFalse(other.commandReplayed)
            assertNotEquals(first.receipt.path,other.receipt.path)
        }
    }
    @Test fun `mutable auth effect and receipt atomic while subsequent access uses new head`() {
        val key=RecordKey("auth","credentials",uuid(id(4)))
        fun record(status: String,generation: Int)=StrictJson.encode(CoreRecordCodec.envelope("credentials",key.key,obj(
            "storeId" to uuid(id(1)),"projectId" to uuid(id(2)),"principalId" to uuid(id(3)),"verifierVersion" to num(1),
            "verifierDigest" to str("0".repeat(64)),"status" to str(status),"generation" to num(generation),"replacedBy" to JNull)))
        val before=record("active",0); val after=record("revoked",1)
        val dir=directory()
        // This is a state-sensitive trusted callback, NOT a bearer-token verifier or owner-policy test.
        val current=CommandAccess { snapshot,_->
            val envelope=StrictJson.decode(snapshot.file(key.path)!!).fields("formatVersion","family","key","payload")
            if((envelope.getValue("payload") as JObject).entries.getValue("status").string()=="active")AuthenticatedActor(id(3),id(4)) else null
        }
        GitCommandStore.initialize(dir,id(1),genesis()+StoreFile(key.path,before),ports()).use { s ->
            val result=s.execute(authCommand(),current,CommandEvaluator { _,c,_-> Evaluation(LogicalOutcome.Success(c.operation,uuid(id(4)),false),listOf(RecordChange(key,after))) }) as CommandResult.Settled
            assertEquals(sha(before),result.receipt.writes.single().beforeDigest)
            assertEquals(sha(after),result.receipt.writes.single().afterDigest)
            assertContentEquals(after,s.read(id(2),ReadAccess { _,_-> true }) { it.file(key.path)!! })
            assertIs<CommandResult.Denied>(s.execute(authCommand(),current,rejecting()))
        }
        GitCommandStore.open(dir,id(1),ports()).close()
    }
    @Test fun `success without required typed effects is not a complete transaction`() {
        GitCommandStore.initialize(directory(),id(1),genesis(),ports()).use { s ->
            assertIs<CommandResult.Refused>(s.execute(authCommand(),access(),CommandEvaluator { _,c,_-> Evaluation(LogicalOutcome.Success(c.operation,uuid(id(4)),false),emptyList()) }))
            val next=s.execute(authCommand(),access(),rejecting()) as CommandResult.Settled
            assertFalse(next.commandReplayed)
        }
    }
    @Test fun `unsupported registry fails writer and missing root never bootstraps`() {
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports()).close()
        assertFails { GitCommandStore.open(dir,id(1),StorePorts(emptyMap(),{ _,_,_,_-> },{ _,_-> })) }
        val empty=directory()
        assertFails { GitCommandStore.open(empty,id(1),ports()) }
    }
    @Test fun `runtime bounds positive and owner stale marker is recovery required`() {
        assertFails { ProcessBounds(inputBytes=0) }
        assertFails { StoreBounds(attempts=4) }
        val dir=directory()
        Files.writeString(dir.resolve("runtime-active"),"ambiguous prior process\n")
        val fault=assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),ports()) }
        assertEquals(StoreProblem.RECOVERY_REQUIRED,fault.problem)
        assertTrue(Files.exists(dir.resolve("runtime-active")))
    }
}
