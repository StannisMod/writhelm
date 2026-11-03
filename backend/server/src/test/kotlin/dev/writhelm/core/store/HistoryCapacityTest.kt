// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.app.AppFixture
import dev.writhelm.core.app.AppOptions
import dev.writhelm.core.app.MechanismApp
import dev.writhelm.core.app.plannerToken as actualPlannerToken
import dev.writhelm.core.app.readerToken as actualReaderToken
import dev.writhelm.core.app.project as actualProject
import dev.writhelm.core.app.storeId as actualStoreId
import dev.writhelm.core.app.revision as actualRevision
import dev.writhelm.core.app.x as actualX
import dev.writhelm.core.app.value as actualValue
import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.WorkflowResult
import java.nio.file.Files
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

/** Tiny synthetic receipt fixtures test the store budget itself. Actual-token/graph binding is
 * separately exercised below with the UNCHANGED delivered app and its existing synthetic vectors. */
class HistoryCapacityTest {
    private fun directory()=storeTestDirectory("history-capacity-")
    private fun ports()=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },
        "policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_-> },{ _,_-> })
    private fun genesis()=listOf(StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),
        StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))),
        StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
    private fun access()=CommandAccess { _,_-> AuthenticatedActor(id(3),id(4)) }
    private fun rejection()=Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
    private fun settled(result: CommandResult)=assertIs<CommandResult.Settled>(result)
    private fun unavailable(result: CommandResult) { assertIs<CommandResult.Refused>(result); assertEquals(StoreProblem.UNAVAILABLE,result.problem) }
    private fun head(s: GitCommandStore)=s.read(id(2),ReadAccess { _,_-> true }) { it.oid }
    private fun receipts(s: GitCommandStore)=s.read(id(2),ReadAccess { _,_-> true }) { snap -> snap.entries.keys.filter { it.startsWith("commands/receipts/") }.toSet() }

    @Test fun `genesis uses the only slot with no evaluation CAS or receipt on refusal`() {
        val dir=directory(); val bounds=StoreBounds(historyCommits=1); var evaluations=0; var publications=0
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds,publication=driver).use { s ->
            val before=head(s)
            unavailable(s.execute(authCommand(),access(),CommandEvaluator { _,_,_-> evaluations++; rejection() }))
            assertEquals(0,evaluations); assertEquals(0,publications); assertEquals(before,head(s)); assertTrue(receipts(s).isEmpty())
            assertIs<CommandResult.Denied>(s.execute(authCommand(),CommandAccess { _,_-> null },CommandEvaluator { _,_,_-> fail("denied evaluation") }))
            assertContentEquals(StrictJson.encode(CoreRecordCodec.header()),s.read(id(2),ReadAccess { _,_-> true }) { it.file("core/format.json")!! })
            assertFalse(Files.exists(dir.resolve("publication-in-flight")))
        }
        GitCommandStore.open(dir,id(1),ports(),bounds).use { assertTrue(receipts(it).isEmpty()) }
    }
    @TestFactory fun `rejection receipts consume one slot but replay conflict denial and reads consume none`() = listOf(2,3).map { limit -> dynamicTest("history-limit-$limit") {
        val dir=directory(); val bounds=StoreBounds(historyCommits=limit); var evaluations=0; var publications=0
        val evaluate=CommandEvaluator { _,_,_-> evaluations++; rejection() }
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds,publication=driver).use { s ->
            for(n in 0 until (limit-1))assertFalse(settled(s.execute(authCommand(100+n),access(),evaluate)).commandReplayed)
            val before=head(s); val keys=receipts(s); assertEquals(limit-1,keys.size)
            unavailable(s.execute(authCommand(999),access(),evaluate))
            assertEquals(limit-1,evaluations); assertEquals(limit-1,publications)
            val replay=settled(s.execute(authCommand(100),access(),evaluate)); assertTrue(replay.commandReplayed); assertIs<LogicalOutcome.Rejected>(replay.receipt.outcome)
            assertIs<CommandResult.IdentityConflict>(s.execute(authCommand(100,generation=1),access(),evaluate))
            assertIs<CommandResult.Denied>(s.execute(authCommand(100),CommandAccess { _,_-> null },evaluate))
            assertEquals(before,head(s)); assertEquals(keys,receipts(s)); assertEquals(limit-1,evaluations); assertEquals(limit-1,publications)
        }
        GitCommandStore.open(dir,id(1),ports(),bounds).use { s ->
            unavailable(s.execute(authCommand(999),access(),CommandEvaluator { _,_,_-> fail("cold full budget evaluated") }))
            assertTrue(settled(s.execute(authCommand(100),access(),CommandEvaluator { _,_,_-> fail("cold replay evaluated") })).commandReplayed)
        }
    } }
    @Test fun `successful cold audit derives the actual remaining slot not a genesis label`() {
        val dir=directory(); val bounds=StoreBounds(historyCommits=3)
        val evaluate=CommandEvaluator { _,_,_-> rejection() }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds).use { settled(it.execute(authCommand(100),access(),evaluate)) }
        GitCommandStore.open(dir,id(1),ports(),bounds).use { s ->
            settled(s.execute(authCommand(101),access(),evaluate)); assertEquals(2,receipts(s).size)
            unavailable(s.execute(authCommand(102),access(),CommandEvaluator { _,_,_-> fail("audit count was reset") }))
        }
        GitCommandStore.open(dir,id(1),ports(),bounds).close()
    }

    /** Valid rejection-only competing publication; distinct commit prose avoids identical candidate
     * OIDs while conveying NO canonical business fact. No second service/multi-writer claim. */
    private fun competitor(git: NativeGit,h: GitOid,command: StoreCommand=authCommand(900)) {
        val receipt=Receipt(id(1),CommandIdentity(id(3),id(2),command.operation,command.requestKey),2,command.fingerprint,h,id(4),rejection().outcome,emptyList())
        val files=git.call(listOf("ls-tree","-rz","--full-tree",h.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }.associate { line ->
            val f=line.split('\t'); f[1] to GitOid("sha1",f[0].substringAfterLast(' '))
        }.toMutableMap()
        files[receipt.path]=git.blob(JournalCodec.encode(receipt))
        fun tree(prefix: String): GitOid {
            val names=files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
            val bytes=names.joinToString("") { name ->
                val path=prefix+name; val oid=files[path]
                if(oid==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "100644 blob ${oid.hex}\t$name\u0000"
            }.toByteArray(Charsets.US_ASCII)
            return git.oid(git.call(listOf("mktree","-z"),bytes).checked())
        }
        val root=tree("")
        val commit=git.oid(git.call(listOf("commit-tree",root.hex,"-p",h.hex),"capacity competitor\n".toByteArray()).checked())
        git.update(h,commit).checked()
    }
    @Test fun `CAS recapture notices competitor consumed last slot before reevaluation`() {
        val dir=directory(); val bounds=StoreBounds(historyCommits=2); var raced=false; var evaluations=0; var publications=0; var authorizations=0
        val driver=PublicationDriver { git,h,c -> publications++; if(!raced) { raced=true; competitor(git,h) }; git.update(h,c) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds,publication=driver).use { s ->
            unavailable(s.execute(authCommand(),CommandAccess { _,_-> authorizations++; AuthenticatedActor(id(3),id(4)) },CommandEvaluator { _,_,_-> evaluations++; rejection() }))
            assertEquals(1,evaluations); assertEquals(1,publications); assertEquals(2,authorizations)
            assertEquals(setOf(CommandIdentity(id(3),id(2),"revoke_credential",id(900)).path),receipts(s))
        }
        GitCommandStore.open(dir,id(1),ports(),bounds).close()
    }
    @Test fun `final CAS reconciliation recovers matching receipt even when competitor filled capacity`() {
        val dir=directory(); val bounds=StoreBounds(historyCommits=2,attempts=1); val command=authCommand(); var evaluations=0; var publications=0; var authorizations=0
        val driver=PublicationDriver { git,h,c -> publications++; competitor(git,h,command); git.update(h,c) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds,publication=driver).use { s ->
            val result=settled(s.execute(command,CommandAccess { _,_-> authorizations++; AuthenticatedActor(id(3),id(4)) },CommandEvaluator { _,_,_-> evaluations++; rejection() }))
            assertTrue(result.commandReplayed); assertEquals(1,evaluations); assertEquals(1,publications); assertEquals(2,authorizations)
            unavailable(s.execute(authCommand(6),access(),CommandEvaluator { _,_,_-> fail("final audit lost capacity") }))
        }
        GitCommandStore.open(dir,id(1),ports(),bounds).close()
    }
    @Test fun `completed error reconciliation derives count exactly once from the published line`() {
        val bounds=StoreBounds(historyCommits=3,attempts=1); var evaluations=0; var publications=0
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); GitReply(1,byteArrayOf(),"injected completed error".toByteArray()) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),bounds,publication=driver).use { s ->
            for(n in listOf(100,101))assertTrue(settled(s.execute(authCommand(n),access(),CommandEvaluator { _,_,_-> evaluations++; rejection() })).commandReplayed)
            unavailable(s.execute(authCommand(102),access(),CommandEvaluator { _,_,_-> evaluations++; rejection() }))
            assertEquals(2,evaluations); assertEquals(2,publications); assertEquals(2,receipts(s).size)
        }
    }
    @Test fun `unpublished orphan consumes no derived slot and is never promoted as replay`() {
        var publications=0
        val driver=PublicationDriver { git,h,c -> if(++publications==1)GitReply(1,byteArrayOf(),byteArrayOf()) else git.update(h,c) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),StoreBounds(historyCommits=2),publication=driver).use { s ->
            unavailable(s.execute(authCommand(100),access(),CommandEvaluator { _,_,_-> rejection() }))
            assertTrue(receipts(s).isEmpty())
            assertFalse(settled(s.execute(authCommand(101),access(),CommandEvaluator { _,_,_-> rejection() })).commandReplayed)
            unavailable(s.execute(authCommand(100),access(),CommandEvaluator { _,_,_-> fail("orphan consumed or bypassed capacity") }))
            assertEquals(2,publications); assertEquals(1,receipts(s).size)
        }
    }
    @Test fun `unknown publication retains barrier rather than offering a speculative slot`() {
        val dir=directory(); var evaluations=0; var publications=0
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); throw StoreFault(StoreProblem.RECOVERY_REQUIRED) }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),StoreBounds(historyCommits=2),publication=driver).use { s ->
            assertIs<CommandResult.Unknown>(s.execute(authCommand(100),access(),CommandEvaluator { _,_,_-> evaluations++; rejection() }))
            assertIs<CommandResult.Unknown>(s.execute(authCommand(101),access(),CommandEvaluator { _,_,_-> fail("unknown state evaluated") }))
            assertEquals(1,evaluations); assertEquals(1,publications); assertTrue(Files.exists(dir.resolve("publication-in-flight")))
        }
        assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),ports(),StoreBounds(historyCommits=2)) }.problem)
    }
    @TestFactory fun `unchanged real token app refuses count frontier and cleanly reopens same profile`() = listOf(1,2,3).map { limit -> dynamicTest("actual-app-history-limit-$limit") {
        val options=AppOptions(storeBounds=StoreBounds(historyCommits=limit)); val path=directory()
        AppFixture(path,options=options).use { f ->
            for(n in 0 until (limit-1))settled(f.core(CommandPayload.Propose(actualRevision(100+n,"X$n")),k=id(200+n)))
            unavailable(f.core(CommandPayload.Propose(actualRevision(999)),k=id(999)))
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Propose(actualRevision(999)),actualReaderToken,id(998)))
            if(limit>1) {
                assertTrue(settled(f.core(CommandPayload.Propose(actualRevision(100)),k=id(200))).commandReplayed)
                assertIs<CommandResult.IdentityConflict>(f.core(CommandPayload.Propose(actualRevision(100,"changed")),k=id(200)))
                assertContentEquals("X0\n".toByteArray(),actualValue(f.app.readClause(actualReaderToken,actualRevision(100).actualX())).located.content.bytes())
            }
            assertIs<WorkflowResult.Failure>(f.app.readClause(actualReaderToken,actualRevision(999).actualX()))
        }
        MechanismApp.open(path,actualStoreId,options).use { app ->
            unavailable(app.execute(actualPlannerToken,CommandCodec().encode(CoreCommand(id(999),actualProject,CommandPayload.Propose(actualRevision(999))))))
            if(limit>1) {
                val replay=settled(app.execute(actualPlannerToken,CommandCodec().encode(CoreCommand(id(200),actualProject,CommandPayload.Propose(actualRevision(100))))))
                assertTrue(replay.commandReplayed)
                assertContentEquals("X0\n".toByteArray(),actualValue(app.readClause(actualReaderToken,actualRevision(100).actualX())).located.content.bytes())
            }
        }
    } }
}
