// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class AppRemediationTest {
    @Test fun `incremental core counterparts consume the same retained work edge budget`() {
        val f=ReviewAppFixture();val original=f.base.work.getValue(project)
        val task=f.task.copy(taskId=id(17400));val plan=f.plan.copy(id=id(17401))
        val checks=(original.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value.copy(task=task)
        val command=WorkCommand(id(17402),project,CCreateTaskRevisionOperation(CCreateTaskRevision(task,original.task(f.task).copy(verificationPlan=plan),CCreateTaskRevisionNewVerificationPlan(plan,checks),emptyList())))
        val candidate=f.key(plannerKey) { f.bridge.work(f.base,command,it,f.observations) }
        val writes=candidate.evaluation.changes.map { WriteDescriptor(it.key,null,sha(it.bytes()!!)) }.sortedBy { it.path }
        val receipt=Receipt(storeId,dev.writhelm.core.store.CommandIdentity(planner,project,command.operation.name,command.requestKey),3,f.codec.fingerprint(command).hex,f.head,plannerKey,candidate.evaluation.outcome,writes,3)
        val workEdges=candidate.work.values.sumOf { it.fullRestoreEdges }.toInt()
        val limits=AppLimits(affectedEdges=workEdges)
        assertEquals(StoreProblem.UNAVAILABLE,assertFailsWith<StoreFault> {
            H1BoundState.validatedCandidate(candidate,GitOid("sha1","9".repeat(40)),f.base.entryBytes+10000,limits,receipt)
        }.problem)
        val admitted=H1BoundState.validatedCandidate(candidate,GitOid("sha1","9".repeat(40)),f.base.entryBytes+10000,AppLimits(),receipt)
        assertEquals(original.task(f.task).copy(verificationPlan=plan),admitted.work.getValue(project).task(task))
    }
    @Test fun `authorized own evidence dedup reuses storage without an attachment`() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            val text=Utf8Content.fromText("genuinely supplied own content")
            val body=WorkBodyReference(text.digest,text.sizeBytes)
            fun evidence(n: Int,attachments: List<CRecordEvidenceBodiesItem>): WorkCommand {
                val data=CRecordEvidenceData(listOf(f.source),emptyList(),"own check","component",body,id(n),"passed",emptyList(),emptyList(),emptyList(),body)
                return WorkCommand(id(n+1),project,CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(n+2)),data,attachments)))
            }
            val first=assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),f.codec.encodeCommand(evidence(16000,listOf(CRecordEvidenceBodiesItem(text.digest,text.text()))))))
            assertIs<LogicalOutcome.Success>(first.receipt.outcome)
            val second=assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),f.codec.encodeCommand(evidence(16010,emptyList()))))
            assertIs<LogicalOutcome.Success>(second.receipt.outcome)
            assertFalse(second.receipt.writes.any { it.record.family=="bodies" })
            val exact=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.EVIDENCE,WorkKey.Id(project,id(16012))),
                digest(second.receipt.outcome.let { (it as LogicalOutcome.Success).result.fields("key","generation","digest").getValue("digest") }),null)
            assertNotNull(app.read2(token(f.workerKey),f.codec.encodeRead(WorkRead.Body(project,text.digest,exact))))
            // REF-01 still refuses re-upload of retained bytes and inconsistent declared length.
            val extra=assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),f.codec.encodeCommand(evidence(16020,listOf(CRecordEvidenceBodiesItem(text.digest,text.text()))))))
            assertIs<LogicalOutcome.Rejected>(extra.receipt.outcome)
            val original=(evidence(16030,emptyList()).operation as CRecordEvidenceOperation).value
            val wrong=original.copy(data=original.data.copy(report=body.copy(byteLength=body.byteLength+1)))
            val rejected=app.execute(token(f.workerKey),f.codec.encodeCommand(WorkCommand(id(16031),project,CRecordEvidenceOperation(wrong))))
            assertFalse(rejected is CommandResult.Settled && rejected.receipt.outcome is LogicalOutcome.Success)
        }
    }

    @Test fun `forged task bases refuse and valid exact base survives reopen`() {
        val f=ReviewAppFixture();val dir=directory()
        val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })
        H1Application.initialize(dir,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val original=f.base.work.getValue(project).task(f.task)
            for((index,base) in listOf(original.base!!.copy(knowledgeManifestDigest=Digest.of(byteArrayOf(88))),
                original.base!!.copy(knowledgeManifestId=id(17090))).withIndex()) {
                val task=f.task.copy(taskId=id(17000+index));val plan=f.plan.copy(id=id(17010+index))
                val checks=(f.base.work.getValue(project).record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value.copy(task=task)
                val command=WorkCommand(id(17020+index),project,CCreateTaskRevisionOperation(CCreateTaskRevision(task,original.copy(base=base,verificationPlan=plan),CCreateTaskRevisionNewVerificationPlan(plan,checks),emptyList())))
                val h=app.inspectAccess(plannerToken).basis
                val result=app.execute(plannerToken,f.codec.encodeCommand(command))
                assertFalse(result is CommandResult.Settled && result.receipt.outcome is LogicalOutcome.Success)
                assertEquals(h,app.inspectAccess(plannerToken).basis)
            }
        }
        H1Application.open(dir,storeId,options).use { app ->
            assertNotNull(app.inspectAccess(plannerToken))
            val task=f.task.copy(taskId=id(17050));val plan=f.plan.copy(id=id(17051))
            val work=f.base.work.getValue(project)
            val checks=(work.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value.copy(task=task)
            val command=WorkCommand(id(17052),project,CCreateTaskRevisionOperation(CCreateTaskRevision(task,work.task(f.task).copy(verificationPlan=plan),CCreateTaskRevisionNewVerificationPlan(plan,checks),emptyList())))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(command))).receipt.outcome)
        }
        H1Application.open(dir,storeId,options).close()
    }

    @Test fun `new input manifests and knowledge references require actual core counterparts`() {
        val f=ReviewAppFixture();val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val original=(f.base.work.getValue(project).record(WorkFamily.INPUT_MANIFESTS,f.manifestKey).payload as WInputManifestsPayload).value
            val bad=original.copy(base=original.base.copy(knowledgeManifestDigest=Digest.of(byteArrayOf(7))))
            val result=app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(17100),project,CPinInputManifestOperation(CPinInputManifest(WorkKey.Id(project,id(17101)),bad,emptyList())))))
            assertFalse(result is CommandResult.Settled && result.receipt.outcome is LogicalOutcome.Success)
            val rejection=assertIs<CommandResult.Settled>(result)
            assertIs<LogicalOutcome.Rejected>(rejection.receipt.outcome)
            assertTrue(rejection.receipt.writes.isEmpty()) // domain rejection receipt is lawful
            val valid=app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(17102),project,CPinInputManifestOperation(CPinInputManifest(WorkKey.Id(project,id(17103)),original,emptyList())))))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(valid).receipt.outcome)
            // Draft creation does not run the executable-design approval gate; its declared
            // knowledge links still must resolve in the actual core, not just WORK's graph.
            val task=f.task.copy(taskId=id(17110));val plan=f.plan.copy(id=id(17111))
            val work=f.base.work.getValue(project)
            val reference=f.base.core.snapshot().manifests.values.single().clauses.single()
            val invalid=reference.copy(contentDigest=Digest.of(byteArrayOf(9)))
            val data=work.task(f.task).copy(state="draft",approvedDesign=listOf(invalid),verificationPlan=plan)
            val checks=(work.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value.copy(task=task)
            val h=app.inspectAccess(plannerToken).basis
            val forged=app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(17112),project,CCreateTaskRevisionOperation(CCreateTaskRevision(task,data,CCreateTaskRevisionNewVerificationPlan(plan,checks),emptyList())))))
            assertFalse(forged is CommandResult.Settled && forged.receipt.outcome is LogicalOutcome.Success)
            assertEquals(h,app.inspectAccess(plannerToken).basis)
        }
    }
    @Test fun `READ loss denies retained reuse but not genuinely supplied new content`() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            fun evidence(n: Int,text: Utf8Content,supply: Boolean): WorkCommand {
                val ref=WorkBodyReference(text.digest,text.sizeBytes)
                val data=CRecordEvidenceData(listOf(f.source),emptyList(),"own check","component",ref,id(n),"passed",emptyList(),emptyList(),emptyList(),ref)
                return WorkCommand(id(n+1),project,CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(n+2)),data,
                    if(supply)listOf(CRecordEvidenceBodiesItem(text.digest,text.text()))else emptyList())))
            }
            val text=Utf8Content.fromText("private own retained content")
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),f.codec.encodeCommand(evidence(17200,text,true)))).receipt.outcome)
            assertIs<CommandResult.Settled>(app.execute(ownerToken,CommandCodec().encode(CoreCommand(id(17210),project,CommandPayload.Revoke(f.worker)))))
            val reuse=app.execute(token(f.workerKey),f.codec.encodeCommand(evidence(17220,text,false)))
            assertEquals(StoreProblem.DENIED,assertIs<CommandResult.Refused>(reuse).problem)
            val fresh=app.execute(token(f.workerKey),f.codec.encodeCommand(evidence(17230,Utf8Content.fromText("new genuinely supplied content after READ loss"),true)))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(fresh).receipt.outcome)
        }
    }
    @Test fun `controller reuse requires an original authorized parent not another controller hash`() {
        val f=ReviewAppFixture();val other=id(17300);val locator=id(17301);val hash=Digest.of(byteArrayOf(4))
        val secret=Utf8Content.fromText("another controller's private retained body")
        val source=WNativeSourceBasis(f.intended.nativeHostId,"ses_controller",1,hash,hash,locator,emptyList(),emptyList())
        fun data(ref: WorkBodyReference)=WControllerContinuations(f.profile,null,source,emptyList(),emptyList(),emptyList(),emptyList(),ref)
        val foreign=WorkRecord(WorkRecordIdentity(WorkFamily.CONTROLLER_CONTINUATIONS,WorkKey.ControllerContinuation(project,other,id(17302))),
            WControllerContinuationsPayload(data(WorkBodyReference(secret.digest,secret.sizeBytes))))
        val original=f.base.work.getValue(project)
        val work=WorkState.restore(project,original.records.values+foreign,listOf(secret))
        val auth=f.base.authority.records()+Auth2Record.Controller(ControllerBinding(other,project,owner))
        f.base=H1BoundState.checked(storeId,f.head,f.base.core,auth,mapOf(project to work))
        f.runtimeFacts=f.runtimeFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(source.nativeHostId,source.nativeSessionId,hash,hash,emptyList())))
        val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val deniedParent=WorkRead.Exact(project,foreign.identity,f.codec.digest(foreign),null)
            assertFailsWith<AppAccessFault> { app.read2(plannerToken,f.codec.encodeRead(WorkRead.Body(project,secret.digest,deniedParent))) }
            fun command(n: Int,text: Utf8Content,supply: Boolean)=WorkCommand(id(n),project,CRecordControllerContinuationOperation(CRecordControllerContinuation(
                WorkKey.ControllerContinuation(project,f.controller,id(n+1)),data(WorkBodyReference(text.digest,text.sizeBytes)),
                if(supply)listOf(CRecordControllerContinuationBodiesItem(text.digest,text.text()))else emptyList())))
            val h=app.inspectAccess(plannerToken).basis
            assertEquals(StoreProblem.DENIED,assertIs<CommandResult.Refused>(app.execute(plannerToken,f.codec.encodeCommand(command(17310,secret,false)))).problem)
            assertEquals(h,app.inspectAccess(plannerToken).basis)
            val own=Utf8Content.fromText("controller genuinely supplied own material")
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(command(17320,own,true)))).receipt.outcome)
            val dedup=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(command(17330,own,false))))
            assertIs<LogicalOutcome.Success>(dedup.receipt.outcome)
            assertFalse(dedup.receipt.writes.any { it.record.family=="bodies" })
        }
    }
}
