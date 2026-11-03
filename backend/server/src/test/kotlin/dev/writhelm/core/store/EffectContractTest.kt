// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class EffectContractTest {
    private fun directory()=storeTestDirectory("effect-test-")
    private fun ports(reads: MutableList<String> = arrayListOf())=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },"policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_-> },{ _,_-> },fileRead={ reads.add(it) })
    private fun exported(workflow: ApprovalWorkflow)=arrayListOf<StoreFile>().also { files -> CoreSnapshotCodec().export(workflow) { files.add(StoreFile(it.path,it.bytes())) } }
    private fun extras()=listOf(StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))),StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
    private fun access()=CommandAccess { _,_-> AuthenticatedActor(planner.principalId,id(444)) }
    private fun changes(before: List<StoreFile>,after: List<StoreFile>): List<RecordChange> {
        val old=before.associateBy { it.path }
        return after.filter { old[it.path]?.bytes()?.contentEquals(it.bytes())!=true }.map { file ->
            val key=if(file.path.startsWith("core/bodies/")) RecordKey("core","bodies",str(file.path.substringAfterLast('/').removeSuffix(".txt")))
            else { val f=StrictJson.decode(file.bytes()).fields("formatVersion","family","key","payload"); RecordKey("core",f.getValue("family").string(),f.getValue("key")) }
            RecordChange(key,file.bytes())
        }
    }
    @Test fun `pure proposal complete effect receipt and exact body without unrelated body reads`() {
        val f=WorkflowFixture().also { it.add(it.a) }; val before=exported(f.state)
        val content=Utf8Content.fromText("\uFEFFe\u0301\r\nnext")
        val r=DocumentRevision(RevisionKey(document,id(800)),content,StructureIndex(listOf(ClauseSpan(ClauseId("X"),ByteSpan(0,content.sizeBytes))),emptyList()))
        val after=good(f.state.propose(planner,r)).state; val delta=changes(before,exported(after))
        val c=StoreCommand.decode(CommandCodec().encode(CoreCommand(id(801),project,CommandPayload.Propose(r))))
        val reads=arrayListOf<String>(); val p=ports(reads); val dir=directory()
        GitCommandStore.initialize(dir,id(1),before+extras(),p).use { s ->
            reads.clear()
            val result=s.execute(c,access(),CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Success(c.operation,revisionKey(r.key),false),delta) }) as CommandResult.Settled
            assertEquals(3,result.receipt.writes.size)
            assertFalse(result.commandReplayed)
            assertFalse(bodyPath(f.a.content.digest) in reads,"ordinary mutation read unrelated body")
            val bytes=s.read(project,ReadAccess { _,_-> true }) { it.file(bodyPath(content.digest))!! }
            assertContentEquals(content.bytes(),bytes)
            assertContentEquals(f.a.content.bytes(),s.read(project,ReadAccess { _,_-> true }) { it.file(bodyPath(f.a.content.digest))!! })
            assertTrue((s.execute(c,access(),CommandEvaluator { _,_,_-> error("no second effect") }) as CommandResult.Settled).commandReplayed)
        }
        GitCommandStore.open(dir,id(1),p).use { s ->
            assertContentEquals(content.bytes(),s.read(project,ReadAccess { _,_-> true }) { it.file(bodyPath(content.digest))!! })
        }
    }
    @Test fun `domain replay new receipt then command replay preserve original domain flag`() {
        val f=WorkflowFixture().baseline(); val request=f.initial
        val dir=directory()
        fun command(n: Int)=StoreCommand.decode(CommandCodec().encode(CoreCommand(id(n),project,CommandPayload.Resolve(request.id,request.digest,request.packet.digest,StructuredAnswer.Approve))))
        val access=CommandAccess { _,_-> AuthenticatedActor(owner.principalId,id(444)) }
        GitCommandStore.initialize(dir,id(1),exported(f.state)+extras(),ports()).use { s ->
            val first=s.execute(command(901),access,CommandEvaluator { _,c,_-> Evaluation(LogicalOutcome.Success(c.operation,uuid(request.id),true),emptyList()) }) as CommandResult.Settled
            assertFalse(first.commandReplayed); assertTrue((first.receipt.outcome as LogicalOutcome.Success).domainReplayed); assertTrue(first.receipt.writes.isEmpty())
            val second=s.execute(command(901),access,CommandEvaluator { _,_,_-> error("must not recompute domain flag") }) as CommandResult.Settled
            assertTrue(second.commandReplayed); assertTrue((second.receipt.outcome as LogicalOutcome.Success).domainReplayed)
        }
        GitCommandStore.open(dir,id(1),ports()).close()
    }
    @Test fun `immutable rewriting and undeclared unrelated key cannot bypass trusted callbacks`() {
        val f=WorkflowFixture().baseline(); val dir=directory(); val c=StoreCommand.decode(CommandCodec().encode(CoreCommand(id(901),project,CommandPayload.Resolve(f.initial.id,f.initial.digest,f.initial.packet.digest,StructuredAnswer.Approve))))
        GitCommandStore.initialize(dir,id(1),exported(f.state)+extras(),ports()).use { s ->
            val immutable=RecordKey("core","resolutions",uuid(f.initial.id))
            val old=s.read(project,ReadAccess { _,_-> true }) { it.file(immutable.path)!! }
            val outcome=s.execute(c,access(),CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Success(c.operation,uuid(f.initial.id),false),listOf(RecordChange(immutable,old))) })
            assertIs<CommandResult.Refused>(outcome)
            val unrelated=RecordKey("core","grants",obj("principalId" to uuid(id(999)),"projectId" to uuid(project)))
            val again=s.execute(c,access(),CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Success(c.operation,uuid(f.initial.id),false),listOf(RecordChange(unrelated,StrictJson.encode(CoreRecordCodec.envelope("grants",unrelated.key,obj("capabilities" to JArray(emptyList()))))))) })
            assertIs<CommandResult.Refused>(again)
        }
    }
    @Test fun `descriptor provenance shape operation and canonical path ordering refuse independently`() {
        val c=authCommand(); val identity=CommandIdentity(id(3),c.projectId,c.operation,c.requestKey)
        val a=WriteDescriptor(RecordKey("auth","credentials",uuid(id(4))),"1".repeat(64),"2".repeat(64))
        val b=WriteDescriptor(RecordKey("auth","credentials",uuid(id(8))),null,"3".repeat(64))
        val sorted=listOf(a,b).sortedBy { it.path }
        assertFails { Receipt(id(1),identity,2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Success(c.operation,uuid(id(4)),false),sorted.reversed()) }
        assertFails { Receipt(id(1),identity,2,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Success("revoke_access",uuid(id(4)),false),emptyList()) }
        assertFails { Receipt(id(1),identity,1,c.fingerprint,GitOid("sha1","1".repeat(40)),id(4),LogicalOutcome.Success(c.operation,uuid(id(4)),false),emptyList()) }
    }
}
