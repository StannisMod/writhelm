// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class AppContractTest {
    @Test fun `cheap real token core publication replay and clean namespace reload`() {
        val path=directory(); val r=revision(100); val key=id(200)
        AppFixture(path).use { f ->
            val first=settled(f.core(CommandPayload.Propose(r),k=key))
            assertFalse(first.commandReplayed); assertEquals(3,first.receipt.writes.size)
            assertEquals(plannerKey,first.receipt.authenticatedByCredentialId)
            assertContentEquals("X0\n".toByteArray(),value(f.app.readClause(plannerToken,r.x())).located.content.bytes())
            val replay=settled(f.core(CommandPayload.Propose(r),k=key)); assertTrue(replay.commandReplayed)
            assertIs<CommandResult.IdentityConflict>(f.core(CommandPayload.Propose(revision(100,"changed")),k=key))
        }
        MechanismApp.open(path,storeId).use { app ->
            assertContentEquals("X0\n".toByteArray(),value(app.readClause(readerToken,r.x())).located.content.bytes())
            assertTrue(settled(app.execute(plannerToken,CommandCodec().encode(CoreCommand(key,project,CommandPayload.Propose(r))))).commandReplayed)
        }
    }
    @Test fun `invalid credentials and absent project capability deny before receipts`() {
        AppFixture().use { f ->
            val r=revision(100); val key=id(200); val p=CommandPayload.Propose(r)
            val wrongSecret=CredentialVerifier.token(storeId,plannerKey,secret(99))
            for(t in listOf("",plannerKey.toString(),f.initial.authority.credentials.getValue(plannerKey).verifierDigest.hex,plannerToken+"=",wrongSecret,CredentialVerifier.token(id(99),plannerKey,secret(6)),token(id(99)),readerToken)) {
                assertIs<CommandResult.Denied>(f.core(p,t,key))
            }
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Revoke(reader),plannerToken))
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Revoke(reader),ownerToken,j=id(99)))
            assertFalse(settled(f.core(p,plannerToken,key)).commandReplayed)
        }
    }
    @Test fun `canonical receipts contain no presented token and malformed input has no settlement`() {
        AppFixture().use { f ->
            val r=revision(100); val command=CommandCodec().encode(CoreCommand(id(200),project,CommandPayload.Propose(r)))
            assertFailsWith<CodecFault> { f.app.execute(plannerToken,command.toString(Charsets.UTF_8).replace("\"payload\":","\"actor\":\"owner\",\"payload\":").toByteArray()) }
            val result=settled(f.app.execute(plannerToken,command)); val receipt=JournalCodec.encode(result.receipt).toString(Charsets.UTF_8)
            assertFalse(receipt.contains(plannerToken)); assertFalse(receipt.contains(plannerToken.substringAfterLast('.')))
            assertFalse(result.commandReplayed)
        }
    }
    @Test fun `normal confirmed commands use H derived cache without unrelated historical body reads`() {
        val reads=arrayListOf<String>()
        AppFixture(options=AppOptions(fileRead={ reads.add(it) })).use { f ->
            val a=revision(100); f.propose(a); reads.clear()
            f.propose(revision(101,"X1"))
            assertFalse(bodyPath(a.content.digest) in reads,"ordinary command reloaded unrelated historical body")
            assertContentEquals("X0\n".toByteArray(),value(f.app.readClause(readerToken,a.x())).located.content.bytes())
            assertFalse(bodyPath(a.content.digest) in reads,"ordinary exact read unnecessarily reloaded body")
        }
    }
    @Test fun `terminal rejection key never becomes success after relevant state changes`() {
        AppFixture().use { f ->
            val r=revision(100); val key=id(300)
            val p=CommandPayload.Assess(ConsistencyAssessment(id(301),project,refs(r),"Not yet present"))
            val original=settled(f.core(p,k=key)); assertEquals("INVALID_REFERENCE",(original.receipt.outcome as LogicalOutcome.Rejected).code)
            f.propose(r)
            val replay=settled(f.core(p,k=key)); assertTrue(replay.commandReplayed); assertIs<LogicalOutcome.Rejected>(replay.receipt.outcome)
            assertIs<LogicalOutcome.Success>(settled(f.core(p)).receipt.outcome)
        }
    }
    @Test fun `bootstrap rejects second policy authority rather than assuming it is canonical`() {
        val initial=genesis(); val different=ApprovalWorkflow.start(AccessPolicy(listOf(Grant(owner,project,setOf(Capability.APPROVE,Capability.MANAGE)))))
        assertFails { MechanismApp.initialize(directory(),AppGenesis(different,initial.authority)) }
    }
    @Test fun `small receipt admission refusal neither publishes nor poisons derived state`() {
        AppFixture(options=AppOptions(admission=AdmissionPolicy(receiptBytes=1))).use { f ->
            val r=revision(100); val result=f.core(CommandPayload.Propose(r)) as CommandResult.Refused
            assertEquals(StoreProblem.ADMISSION,result.problem)
            assertIs<WorkflowResult.Failure>(f.app.readClause(readerToken,r.x()))
        }
    }
    @Test fun `normal cached growth cannot exceed supported load budget or settle an unavailable key`() {
        val path=directory(); val r=revision(100,"x".repeat(8192)); val key=id(200)
        AppFixture(path,options=AppOptions(limits=AppLimits(entryBytes=8192))).use { f ->
            val result=f.core(CommandPayload.Propose(r),k=key)
            assertIs<CommandResult.Refused>(result); assertEquals(StoreProblem.UNAVAILABLE,result.problem)
            assertIs<WorkflowResult.Failure>(f.app.readClause(readerToken,r.x()))
        }
        MechanismApp.open(path,storeId).use { app ->
            val result=settled(app.execute(plannerToken,CommandCodec().encode(CoreCommand(key,project,CommandPayload.Propose(r)))))
            assertFalse(result.commandReplayed)
        }
    }
    @Test fun `oversize core candidate refuses before publication without promoting reducer state`() {
        val g=genesis(); var maximum=1
        CoreSnapshotCodec().export(g.workflow) { if(it.path.startsWith("core/records/"))maximum=maxOf(maximum,it.bytes().size) }
        AppFixture(initial=g,options=AppOptions(admission=AdmissionPolicy(coreRecordBytes=maximum))).use { f ->
            val r=revision(100)
            assertEquals(StoreProblem.ADMISSION,(f.core(CommandPayload.Propose(r)) as CommandResult.Refused).problem)
            assertIs<WorkflowResult.Failure>(f.app.readClause(readerToken,r.x()))
        }
    }
    @Test fun `unregistered served project rejected even when standalone core graph is valid`() {
        val initial=genesis(); val r=revision(100)
        val foreign=dev.writhelm.core.knowledge.DocumentRevision(
            dev.writhelm.core.knowledge.RevisionKey(dev.writhelm.core.knowledge.DocumentKey(id(99),id(20)),id(100)),r.content,r.structure)
        val s=initial.workflow.snapshot()
        val graph=WorkflowSnapshot(listOf(foreign),s.grants,mapOf(foreign.key to Actor(owner)),s.requests,s.resolutions,s.approvals,s.continuity,s.assessments,s.manifests,s.heads,s.generations,s.blocked)
        val standalone=WorkflowSnapshotValidator.restore(graph,65536,256)
        assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> { MechanismApp.initialize(directory(),AppGenesis(standalone,initial.authority)) }.problem)
    }
    @Test fun `unknown publication keeps original conservative barrier`() {
        val driver=PublicationDriver { git,h,c -> git.update(h,c).checked(); throw StoreFault(StoreProblem.RECOVERY_REQUIRED) }
        val path=directory()
        AppFixture(path,options=AppOptions(publication=driver)).use { f -> assertIs<CommandResult.Unknown>(f.core(CommandPayload.Propose(revision(100)))) }
        assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { MechanismApp.open(path,storeId) }.problem)
    }
}
