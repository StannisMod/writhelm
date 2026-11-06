// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.app.AppFixture
import dev.writhelm.core.app.AppOptions
import dev.writhelm.core.app.MechanismApp
import dev.writhelm.core.app.genesis as actualGenesis
import dev.writhelm.core.app.ownerToken as actualOwnerToken
import dev.writhelm.core.app.readerToken as actualReaderToken
import dev.writhelm.core.app.project as actualProject
import dev.writhelm.core.app.storeId as actualStoreId
import dev.writhelm.core.app.value as actualValue
import dev.writhelm.core.auth.AuthPayload
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

/** Required-law regressions, NOT green assertions of the forbidden F001 observation.
 * Existing NativeGit is a qualified Linux-only test profile; this adds no portable OS adapter. */
class ReconciliationUncertaintyTest {
    private fun directory()=storeTestDirectory("reconciliation-uncertainty-")
    private fun revision(): DocumentRevision {
        val body=Utf8Content.fromText("alpha\r\nβ")
        return DocumentRevision(RevisionKey(DocumentKey(actualProject,id(20)),id(100)),body,
            StructureIndex(listOf(ClauseSpan(ClauseId("X"),ByteSpan(0,body.sizeBytes))),emptyList()))
    }
    private fun errorReply(stderrOnly: Boolean=false)=GitReply(if(stderrOnly)0 else 1,byteArrayOf(),"injected completed reply".toByteArray())
    private fun markers(dir: Path) {
        assertTrue(Files.exists(dir.resolve("publication-in-flight")),"unresolved publication barrier must survive")
        assertTrue(Files.exists(dir.resolve("runtime-active")),"unresolved runtime marker must survive")
    }
    /** Called AFTER the actual publication child completed but BEFORE injecting its reply error.
     * Later ref-file inspection is non-mutating; no uncertain owner/marker is cleared to read data. */
    private fun verifyPublished(git: NativeGit,h: GitOid,c: GitOid,r: DocumentRevision) {
        assertEquals(c,git.head())
        val files=git.call(listOf("ls-tree","-rz","--full-tree",c.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }.associate { line ->
            val f=line.split('\t'); f[1] to GitOid("sha1",f[0].substringAfterLast(' '))
        }
        val paths=files.keys.filter { it.startsWith("commands/receipts/") }; assertEquals(1,paths.size)
        val receipt=JournalCodec.decode(git.readBlob(files.getValue(paths.single())),paths.single())
        assertEquals(h,receipt.validatedAt); assertEquals(3,receipt.writes.size); assertIs<LogicalOutcome.Success>(receipt.outcome)
        assertEquals(id(200),receipt.identity.requestKey); assertEquals("propose_revision",receipt.identity.operation)
        assertEquals(CommandCodec().fingerprint(CoreCommand(id(200),actualProject,CommandPayload.Propose(r))).hex,receipt.fingerprint)
        assertContentEquals(StrictJson.encode(revisionKey(r.key)),StrictJson.encode(receipt.outcome.result))
        assertContentEquals(r.content.bytes(),git.readBlob(files.getValue(bodyPath(r.content.digest))))
    }
    private fun auditFailure(attempts: Int,stderrOnly: Boolean=false) {
        val dir=directory(); val r=revision(); var publications=0; var observed: GitOid?=null; var repository: Path?=null; var verified=false
        val driver=PublicationDriver { git,h,c ->
            publications++; git.update(h,c).checked(); verifyPublished(git,h,c,r)
            observed=c; repository=git.repository; verified=true; errorReply(stderrOnly)
        }
        val f=AppFixture(dir,options=AppOptions(storeBounds=StoreBounds(attempts=attempts,auditBytes=1),publication=driver))
        try {
            val result=f.core(CommandPayload.Propose(r),k=id(200))
            println("attempts=$attempts stderrOnly=$stderrOnly actual=${result.javaClass.simpleName} problem=${(result as? CommandResult.Refused)?.problem} publicationVerified=$verified directory=$dir")
            assertIs<CommandResult.Unknown>(result,"failed changed-head reconciliation must remain unknown on EVERY attempt")
            assertTrue(verified); assertEquals(1,publications); markers(dir)
            assertEquals(observed!!.hex,Files.readString(repository!!.resolve("refs/heads/state")).trim())
            assertIs<CommandResult.Unknown>(f.core(CommandPayload.Propose(r),k=id(201)))
            assertEquals(1,publications)
        } finally {
            f.close()
            println("afterClose attempts=$attempts barrier=${Files.exists(dir.resolve("publication-in-flight"))} runtime=${Files.exists(dir.resolve("runtime-active"))}")
        }
        markers(dir)
        assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { MechanismApp.open(dir,actualStoreId) }.problem)
    }
    @TestFactory fun `nonfinal failed audit must retain unknown and both markers`() = listOf(2,3).flatMap { attempts ->
        listOf(false,true).map { stderr -> dynamicTest("nonfinal-attempts-$attempts-stderrOnly-$stderr") { auditFailure(attempts,stderr) } }
    }
    @Test fun `final attempt failed audit remains fail closed`() { auditFailure(1) }
    @Test fun `adequate audit recovers actual token receipt once without another publication`() {
        val dir=directory(); val r=revision(); var publications=0
        val options=AppOptions(storeBounds=StoreBounds(attempts=2,auditBytes=1024*1024),publication=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); errorReply() })
        AppFixture(dir,options=options).use { f ->
            val result=assertIs<CommandResult.Settled>(f.core(CommandPayload.Propose(r),k=id(200)))
            assertTrue(result.commandReplayed); assertEquals(1,publications)
            assertContentEquals(r.content.bytes(),actualValue(f.app.readClause(actualReaderToken,r.clauseReference(ClauseId("X")))).located.content.bytes())
            assertFalse(Files.exists(dir.resolve("publication-in-flight")))
        }
        MechanismApp.open(dir,actualStoreId).close()
    }
    @Test fun `ordinary confirmed acknowledgement does not audit recognized history`() {
        val r=revision()
        AppFixture(options=AppOptions(storeBounds=StoreBounds(attempts=2,auditBytes=1))).use { f ->
            assertFalse(assertIs<CommandResult.Settled>(f.core(CommandPayload.Propose(r))).commandReplayed)
        }
    }
    @Test fun `nonfinal current denial after real self revocation discloses no receipt`() {
        var publications=0; val dir=directory(); val key=id(200)
        val options=AppOptions(storeBounds=StoreBounds(attempts=2),publication=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); errorReply() })
        AppFixture(dir,actualGenesis(extraOwner=true),options).use { f ->
            val command=AuthPayload.RevokeCredential(id(4),0)
            assertIs<CommandResult.Denied>(f.auth(command,actualOwnerToken,key))
            val recovered=assertIs<CommandResult.Settled>(f.auth(command,dev.writhelm.core.app.token(id(9)),key))
            assertTrue(recovered.commandReplayed); assertEquals(id(4),recovered.receipt.authenticatedByCredentialId)
            assertEquals(1,publications); assertFalse(Files.exists(dir.resolve("publication-in-flight")))
        }
        MechanismApp.open(dir,actualStoreId).close()
    }

    // Synthetic receipt-only ports isolate callback/IO failures and count precise evaluations.
    // Actual current token, namespace and reducer binding is exercised above and by the frozen app suite.
    private fun ports(observe: (String)->Unit={ })=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },
        "policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_-> },{ _,_-> },observe)
    private fun genesis()=listOf(StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),
        StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))),StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
    private fun rejection()=Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
    private fun actor()=AuthenticatedActor(id(3),id(4))
    private fun access()=CommandAccess { _,_-> actor() }
    private fun callbackFailure(receiptRead: Boolean) {
        val dir=directory(); var evaluations=0; var publications=0; var authorizations=0; var failRead=false
        val command=authCommand(); val receiptPath=CommandIdentity(id(3),id(2),command.operation,command.requestKey).path
        val p=ports { if(failRead && it==receiptPath)throw CodecFault(CodecProblem.RESOURCE_UNAVAILABLE) }
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); errorReply() }
        GitCommandStore.initialize(dir,id(1),genesis(),p,StoreBounds(attempts=3),publication=driver).use { s ->
            val result=s.execute(command,CommandAccess { _,_->
                if(++authorizations==2) {
                    if(receiptRead)failRead=true else throw StoreFault(StoreProblem.UNAVAILABLE)
                }; actor()
            },CommandEvaluator { _,_,_-> evaluations++; rejection() })
            assertIs<CommandResult.Unknown>(result); markers(dir)
            assertEquals(1,evaluations); assertEquals(1,publications); assertEquals(2,authorizations)
        }
        markers(dir)
    }
    @Test fun `nonfinal current authorization resource failure remains unknown`() { callbackFailure(false) }
    @Test fun `nonfinal protected receipt read resource failure remains unknown`() { callbackFailure(true) }
    @Test fun `published receipt cannot be returned under substituted retry credential`() {
        val dir=directory(); var checks=0; var evaluations=0; var publications=0
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); errorReply() }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),StoreBounds(attempts=2),publication=driver).use { s ->
            val result=s.execute(authCommand(),CommandAccess { _,_-> AuthenticatedActor(id(3),if(++checks==1)id(4) else id(44)) },CommandEvaluator { _,_,_-> evaluations++; rejection() })
            assertIs<CommandResult.Unknown>(result); markers(dir); assertEquals(1,evaluations); assertEquals(1,publications)
        }
        markers(dir)
    }
    @Test fun `original aggregate deadline is not renewed after nonfinal completed error`() {
        val dir=directory(); var publications=0; var evaluations=0
        val driver=PublicationDriver { git,h,c -> publications++; git.update(h,c).checked(); git.deadlineNanos=System.nanoTime()-1; errorReply() }
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),StoreBounds(attempts=2),publication=driver).use { s ->
            assertIs<CommandResult.Unknown>(s.execute(authCommand(),access(),CommandEvaluator { _,_,_-> evaluations++; rejection() }))
            assertEquals(1,evaluations); assertEquals(1,publications); markers(dir)
        }
        markers(dir)
    }
    private fun competitor(git: NativeGit,h: GitOid,command: StoreCommand) {
        val receipt=Receipt(id(1),CommandIdentity(id(3),id(2),command.operation,command.requestKey),2,command.fingerprint,h,id(4),rejection().outcome,emptyList())
        val files=git.call(listOf("ls-tree","-rz","--full-tree",h.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }.associate { line ->
            val f=line.split('\t'); f[1] to GitOid("sha1",f[0].substringAfterLast(' '))
        }.toMutableMap(); files[receipt.path]=git.blob(JournalCodec.encode(receipt))
        fun tree(prefix: String): GitOid {
            val names=files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
            val bytes=names.joinToString("") { name ->
                val path=prefix+name; val oid=files[path]
                if(oid==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "100644 blob ${oid.hex}\t$name\u0000"
            }.toByteArray(Charsets.US_ASCII)
            return git.oid(git.call(listOf("mktree","-z"),bytes).checked())
        }
        val root=tree(""); val c=git.oid(git.call(listOf("commit-tree",root.hex,"-p",h.hex),"reconciliation control\n".toByteArray()).checked()); git.update(h,c).checked()
    }
    @Test fun `qualified absence permits one same command rebuild preserving unrelated receipt`() {
        var raced=false; var evaluations=0; var publications=0; var authorizations=0; val command=authCommand()
        val driver=PublicationDriver { git,h,c -> publications++; if(!raced) { raced=true; competitor(git,h,authCommand(900)) }; git.update(h,c) }
        GitCommandStore.initialize(directory(),id(1),genesis(),ports(),StoreBounds(attempts=2,historyCommits=3),publication=driver).use { s ->
            val result=s.execute(command,CommandAccess { _,same -> assertSame(command,same); authorizations++; actor() },CommandEvaluator { _,same,_-> assertSame(command,same); evaluations++; rejection() })
            assertIs<CommandResult.Settled>(result); assertFalse(result.commandReplayed)
            assertEquals(2,evaluations); assertEquals(2,publications); assertEquals(2,authorizations)
            assertTrue(assertIs<CommandResult.Settled>(s.execute(authCommand(900),access(),CommandEvaluator { _,_,_-> fail("competitor must replay") })).commandReplayed)
            val full=s.execute(authCommand(901),access(),CommandEvaluator { _,_,_-> fail("history capacity lost") })
            assertIs<CommandResult.Refused>(full); assertEquals(StoreProblem.UNAVAILABLE,full.problem)
        }
    }
    @Test fun `qualified changed fingerprint conflicts without another evaluation or CAS`() {
        var evaluations=0; var publications=0; var authorizations=0
        val driver=PublicationDriver { git,h,c -> publications++; competitor(git,h,authCommand(generation=1)); git.update(h,c) }
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),StoreBounds(attempts=2,historyCommits=2),publication=driver).use { s ->
            val result=s.execute(authCommand(),CommandAccess { _,_-> authorizations++; actor() },CommandEvaluator { _,_,_-> evaluations++; rejection() })
            assertIs<CommandResult.IdentityConflict>(result); assertEquals(1,evaluations); assertEquals(1,publications); assertEquals(2,authorizations)
            assertFalse(Files.exists(dir.resolve("publication-in-flight")))
        }
        assertFalse(Files.exists(dir.resolve("runtime-active")))
    }
}
