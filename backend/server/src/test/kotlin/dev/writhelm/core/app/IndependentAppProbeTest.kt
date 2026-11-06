package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class IndependentAppProbeTest {
    @Test fun `new task with forged core base manifest digest must be refused before publication`() {
        val f=ReviewAppFixture()
        val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })
        val dir=directory()
        H1Application.initialize(dir,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val work=f.base.work.getValue(project)
            val task=f.task.copy(taskId=id(13000));val plan=f.plan.copy(id=id(13001))
            val original=work.task(f.task)
            val corrupted=original.copy(base=original.base!!.copy(knowledgeManifestDigest=Digest.of(byteArrayOf(99))),verificationPlan=plan)
            val checks=(work.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value.copy(task=task)
            val command=WorkCommand(id(13002),project,CCreateTaskRevisionOperation(CCreateTaskRevision(task,corrupted,CCreateTaskRevisionNewVerificationPlan(plan,checks),emptyList())))
            val before=app.inspectAccess(plannerToken).basis
            val result=app.execute(plannerToken,f.codec.encodeCommand(command))
            assertFalse(result is CommandResult.Settled && result.receipt.outcome is LogicalOutcome.Success,
                "Published a new executable task whose WBase digest does not resolve to the actual core manifest; cold restore will reject it")
            assertEquals(before,app.inspectAccess(plannerToken).basis)
        }
    }
    @Test fun `referencing a denied controller body from own evidence must not disclose its text`() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            val secret=Utf8Content.fromText("private controller-only source material; worker must not acquire this through a digest")
            val body=WorkBodyReference(secret.digest,secret.sizeBytes)
            val sourceHash=Digest.of(byteArrayOf(42));val locator=id(12000)
            val nativeSession="ses_controller_private"
            f.runtimeFacts=f.runtimeFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.intended.nativeHostId,nativeSession,sourceHash,sourceHash,emptyList())))
            val native=WNativeSourceBasis(f.intended.nativeHostId,nativeSession,1,sourceHash,sourceHash,locator,emptyList(),emptyList())
            val frontier=listOf(WControllerContinuationsFrontierItem(f.task,listOf(WControllerContinuationsFrontierItemAssignmentsItem(f.assignment,4,0)),listOf(id(96))))
            val data=WControllerContinuations(f.profile,null,native,frontier,emptyList(),emptyList(),emptyList(),body)
            val command=WorkCommand(id(12001),project,CRecordControllerContinuationOperation(CRecordControllerContinuation(WorkKey.ControllerContinuation(project,f.controller,id(12002)),data,listOf(CRecordControllerContinuationBodiesItem(secret.digest,secret.text())))))
            val created=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(command)))
            val outcome=assertIs<LogicalOutcome.Success>(created.receipt.outcome)
            val fields=outcome.result.fields("key","generation","digest")
            val parent=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.CONTROLLER_CONTINUATIONS,identityCodec.decodeKey(WorkFamily.CONTROLLER_CONTINUATIONS,StrictJson.encode(fields.getValue("key")))),digest(fields.getValue("digest")),null)
            val worker=token(f.workerKey)
            assertFailsWith<AppAccessFault> { app.read2(worker,f.codec.encodeRead(WorkRead.Body(project,secret.digest,parent))) }
            // A distinct, genuinely supplied environment isolates the unauthorized reused report
            // from the separate duplicate-reference read failure observed in run001.
            val environment=Utf8Content.fromText("public worker-supplied environment")
            val evidence=CRecordEvidenceData(listOf(f.source),emptyList(),"own bounded check","component",WorkBodyReference(environment.digest,environment.sizeBytes),id(12003),"passed",emptyList(),emptyList(),emptyList(),body)
            val attempt=WorkCommand(id(12004),project,CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(12005)),evidence,listOf(CRecordEvidenceBodiesItem(environment.digest,environment.text())))))
            val settled=app.execute(worker,f.codec.encodeCommand(attempt))
            if(settled is CommandResult.Settled && settled.receipt.outcome is LogicalOutcome.Success) {
                val value=(settled.receipt.outcome as LogicalOutcome.Success).result.fields("key","generation","digest")
                val own=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.EVIDENCE,WorkKey.Id(project,id(12005))),digest(value.getValue("digest")),null)
                val read=app.read2(worker,f.codec.encodeRead(WorkRead.Body(project,secret.digest,own))) as JObject
                val leaked=read.entries.getValue("data").fields("digest","byteLength","body").getValue("body").string()
                assertNotEquals(secret.text(),leaked,"Digest-only reuse laundered a denied body into active own-evidence read authority")
            }
        }
    }
    @Test fun `valid own body shared by evidence environment and report remains readable`() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            val text=Utf8Content.fromText("own worker material reused in two legitimate fields")
            val body=WorkBodyReference(text.digest,text.sizeBytes)
            val evidence=CRecordEvidenceData(listOf(f.source),emptyList(),"own check","component",body,id(14000),"passed",emptyList(),emptyList(),emptyList(),body)
            val command=WorkCommand(id(14001),project,CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(14002)),evidence,listOf(CRecordEvidenceBodiesItem(text.digest,text.text())))))
            val result=assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),f.codec.encodeCommand(command)))
            val outcome=assertIs<LogicalOutcome.Success>(result.receipt.outcome)
            val parent=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.EVIDENCE,WorkKey.Id(project,id(14002))),digest(outcome.result.fields("key","generation","digest").getValue("digest")),null)
            val value=app.read2(token(f.workerKey),f.codec.encodeRead(WorkRead.Body(project,text.digest,parent))) as JObject
            assertEquals(text.text(),value.entries.getValue("data").fields("digest","byteLength","body").getValue("body").string())
        }
    }
}
