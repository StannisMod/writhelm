// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import java.nio.file.Files
import kotlin.test.*
import org.junit.jupiter.api.Test

class PublicationSafetyTest {
    private fun directory()=storeTestDirectory("publication-test-")
    private fun ports()=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },"policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_-> },{ _,_-> })
    private fun genesis()=listOf(StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))),StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
    private fun rejecting()=CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) }
    private fun access()=CommandAccess { _,_-> AuthenticatedActor(id(3),id(4)) }

    // Creates a bounded legitimate rejection-only competitor on the same disposable published line.
    // Test-only direct Git writer is not a qualified second service instance.
    private fun competitor(git: NativeGit,h: GitOid,command: StoreCommand=authCommand(900)): GitOid {
        val receipt=Receipt(id(1),CommandIdentity(id(3),id(2),command.operation,command.requestKey),2,command.fingerprint,h,id(4),LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
        val lines=git.call(listOf("ls-tree","-rz","--full-tree",h.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }
        val files=lines.associate { line -> val pieces=line.split('\t'); pieces[1] to GitOid("sha1",pieces[0].substringAfterLast(' ')) }.toMutableMap()
        files[receipt.path]=git.blob(JournalCodec.encode(receipt))
        fun tree(prefix: String): GitOid {
            val selected=files.filterKeys { it.startsWith(prefix) }
            val names=selected.keys.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
            val input=names.joinToString("") { name ->
                val path=prefix+name
                val oid=files[path]
                if(oid!=null)"100644 blob ${oid.hex}\t$name\u0000" else "040000 tree ${tree("$path/").hex}\t$name\u0000"
            }.toByteArray(Charsets.US_ASCII)
            return git.oid(git.call(listOf("mktree","-z"),input).checked())
        }
        val c=git.commit(tree(""),h); git.update(h,c).checked(); return c
    }
    @Test fun `CAS rebuild captures current auth preserves unrelated receipt and same command`() {
        var raced=false; var authorization=0; var evaluations=0
        val command=authCommand()
        val driver=PublicationDriver { git,h,c ->
            if(!raced) { raced=true; competitor(git,h) }
            git.update(h,c)
        }
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),publication=driver).use { s ->
            val result=s.execute(command,CommandAccess { _,original -> assertSame(command,original); authorization++; AuthenticatedActor(id(3),id(4)) },CommandEvaluator { _,original,_-> assertSame(command,original); evaluations++; Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) })
            assertIs<CommandResult.Settled>(result)
            assertEquals(2,authorization); assertEquals(2,evaluations)
            assertTrue((s.execute(authCommand(900),access(),rejecting()) as CommandResult.Settled).commandReplayed)
        }
    }
    @Test fun `access revoked on retry denies without revealing orphan receipt`() {
        var raced=false; var checks=0
        val driver=PublicationDriver { git,h,c -> if(!raced) { raced=true; competitor(git,h) }; git.update(h,c) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),publication=driver).use { s ->
            val outcome=s.execute(authCommand(),CommandAccess { _,_-> if(++checks==1)AuthenticatedActor(id(3),id(4)) else null },rejecting())
            assertIs<CommandResult.Denied>(outcome); assertEquals(2,checks)
            val next=s.execute(authCommand(),access(),rejecting()) as CommandResult.Settled
            assertFalse(next.commandReplayed) // Receipt in losing orphan did not settle this identity.
        }
    }
    @Test fun `trusted callback cannot silently substitute a credential on CAS retry`() {
        var raced=false; var checks=0
        val driver=PublicationDriver { git,h,c -> if(!raced) { raced=true; competitor(git,h) }; git.update(h,c) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),publication=driver).use { s ->
            val result=s.execute(authCommand(),CommandAccess { _,_-> AuthenticatedActor(id(3),if(++checks==1)id(4) else id(44)) },rejecting()) as CommandResult.Refused
            assertEquals(StoreProblem.INTEGRITY,result.problem)
        }
    }
    @Test fun `lost publication response retains barrier and blocks restart even if ref advanced`() {
        val dir=directory()
        val driver=PublicationDriver { git,h,c -> git.update(h,c).checked(); throw StoreFault(StoreProblem.RECOVERY_REQUIRED) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),publication=driver).use { s ->
            assertIs<CommandResult.Unknown>(s.execute(authCommand(),access(),rejecting()))
            assertTrue(Files.exists(dir.resolve("publication-in-flight")))
            assertIs<CommandResult.Unknown>(s.execute(authCommand(),access(),rejecting()))
        }
        assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),ports()) }.problem)
        assertTrue(Files.exists(dir.resolve("runtime-active")))
        assertTrue(Files.exists(dir.resolve("publication-in-flight")))
    }
    @Test fun `CAS retry cap is unavailable not durable rejection`() {
        val driver=PublicationDriver { git,h,c -> competitor(git,h); git.update(h,c) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
            val result=s.execute(authCommand(),access(),rejecting()) as CommandResult.Refused
            assertEquals(StoreProblem.UNAVAILABLE,result.problem)
        }
    }
    @Test fun `last attempt real publication then error reply reconciles without reexecution`() {
        for(reply in listOf(GitReply(1,byteArrayOf(),"injected error".toByteArray()),GitReply(0,byteArrayOf(),"injected stderr".toByteArray()))) {
            var evaluations=0; var authorizations=0; var publications=0
            val command=authCommand()
            val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); reply }
            GitCommandStore.initialize(directory(),id(1),genesis(),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
                val result=s.execute(command,CommandAccess { _,original -> assertSame(command,original); authorizations++; AuthenticatedActor(id(3),id(4)) },CommandEvaluator { _,_,_-> evaluations++; Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) })
                assertIs<CommandResult.Settled>(result)
                assertTrue(result.commandReplayed)
                assertEquals("AUTH_TARGET_NOT_FOUND",(result.receipt.outcome as LogicalOutcome.Rejected).code)
                assertEquals(1,evaluations); assertEquals(1,publications); assertEquals(2,authorizations)
            }
        }
    }
    @Test fun `last attempt error reply requires current access before recovery`() {
        var checks=0; var evaluations=0
        val driver=PublicationDriver { git,h,c -> git.update(h,c).checked(); GitReply(1,byteArrayOf(),byteArrayOf()) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
            val result=s.execute(authCommand(),CommandAccess { _,_-> if(++checks==1)AuthenticatedActor(id(3),id(4)) else null },CommandEvaluator { _,_,_-> evaluations++; Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) })
            assertIs<CommandResult.Denied>(result); assertEquals(2,checks); assertEquals(1,evaluations)
        }
    }
    @Test fun `last attempt changed fingerprint in published identity conflicts`() {
        var evaluations=0
        val driver=PublicationDriver { git,h,_ -> competitor(git,h,authCommand(generation=1)); GitReply(1,byteArrayOf(),byteArrayOf()) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
            val result=s.execute(authCommand(),access(),CommandEvaluator { _,_,_-> evaluations++; Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) })
            assertIs<CommandResult.IdentityConflict>(result); assertEquals(1,evaluations)
        }
    }
    @Test fun `last attempt failed reconciliation retains unknown and barrier`() {
        val dir=directory(); var checks=0
        val driver=PublicationDriver { git,h,c -> git.update(h,c).checked(); GitReply(1,byteArrayOf(),byteArrayOf()) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
            val result=s.execute(authCommand(),CommandAccess { _,_-> if(++checks==2)throw StoreFault(StoreProblem.UNAVAILABLE); AuthenticatedActor(id(3),id(4)) },rejecting())
            assertIs<CommandResult.Unknown>(result)
            assertTrue(Files.exists(dir.resolve("publication-in-flight")))
        }
        assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),ports()) }.problem)
    }
    @Test fun `last attempt expired aggregate budget remains unknown without a new deadline`() {
        val dir=directory(); var publications=0
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); git.deadlineNanos=System.nanoTime()-1; GitReply(1,byteArrayOf(),byteArrayOf()) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
            assertIs<CommandResult.Unknown>(s.execute(authCommand(),access(),rejecting()))
            assertEquals(1,publications); assertTrue(Files.exists(dir.resolve("publication-in-flight")))
        }
    }
    @Test fun `last attempt successful effect is recovered from published receipt after error`() {
        val key=RecordKey("auth","credentials",uuid(id(4)))
        fun record(status: String,generation: Int)=StrictJson.encode(CoreRecordCodec.envelope("credentials",key.key,obj(
            "storeId" to uuid(id(1)),"projectId" to uuid(id(2)),"principalId" to uuid(id(3)),"verifierVersion" to num(1),
            "verifierDigest" to str("0".repeat(64)),"status" to str(status),"generation" to num(generation),"replacedBy" to JNull)))
        val after=record("revoked",1); var evaluations=0
        val driver=PublicationDriver { git,h,c -> git.update(h,c).checked(); GitReply(1,byteArrayOf(),"injected error".toByteArray()) }
        GitCommandStore.initialize(directory(),id(1),genesis()+StoreFile(key.path,record("active",0)),ports(),bounds=StoreBounds(attempts=1),publication=driver).use { s ->
            val result=s.execute(authCommand(),access(),CommandEvaluator { _,c,_-> evaluations++; Evaluation(LogicalOutcome.Success(c.operation,uuid(id(4)),false),listOf(RecordChange(key,after))) })
            assertIs<CommandResult.Settled>(result); assertTrue(result.commandReplayed)
            assertIs<LogicalOutcome.Success>(result.receipt.outcome); assertEquals(1,evaluations)
            assertContentEquals(after,s.read(id(2),ReadAccess { _,_-> true }) { it.file(key.path)!! })
        }
    }
    @Test fun `auth record deletion refused even when trusted evaluator asks for it`() {
        val dir=directory(); val credentialKey=RecordKey("auth","credentials",uuid(id(4)))
        val data=StrictJson.encode(CoreRecordCodec.envelope("credentials",uuid(id(4)),obj("synthetic" to str("not real auth"))))
        // Intentionally narrow synthetic namespace validator: validates store mechanism, not auth.
        GitCommandStore.initialize(dir,id(1),genesis()+StoreFile(credentialKey.path,data),ports()).use { s ->
            val result=s.execute(authCommand(),access(),CommandEvaluator { _,c,_-> Evaluation(LogicalOutcome.Success(c.operation,uuid(id(4)),false),listOf(RecordChange(credentialKey,null))) })
            assertIs<CommandResult.Refused>(result) // Even trusted callback cannot delete auth records.
            assertContentEquals(data,s.read(id(2),ReadAccess { _,_-> true }) { it.file(credentialKey.path)!! })
        }
    }
    @Test fun `native bounded output input and owner clean release`() {
        val dir=directory(); val owner=RuntimeOwner.acquire(dir)
        NativeGit(owner).also { it.qualify(); it.initialize() }
        val git=NativeGit(owner,ProcessBounds(inputBytes=16,outputBytes=64,errorBytes=32))
        git.call(listOf("hash-object","-w","--stdin"),ByteArray(15)).checked()
        git.call(listOf("hash-object","-w","--stdin"),ByteArray(16)).checked()
        assertFails { git.call(listOf("hash-object","-w","--stdin"),ByteArray(17)) }
        assertFails { git.call(listOf("--version"),inRepository=false,outputLimit=1).checked() }
        owner.close()
        RuntimeOwner.acquire(dir).close()
    }
}
