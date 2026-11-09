// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class WorkRemediationContractTest {
    private fun record(key: WorkKey,payload: WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(payload),key),payload)
    private fun revise(f: ReviewProbeFixture,key: WorkKey.Revision,plan: WVerificationPlans)=
        f.apply(CReviseVerificationPlanOperation(CReviseVerificationPlan(key,plan,emptyList())))
    private fun acceptance(f: ReviewProbeFixture,result: WorkReference,plan: WorkKey.Revision,evidence: List<WorkReference>,n: Int): CAcceptResultOperation {
        val app=WorkKey.Id(f.project,uid(n))
        f.apply(CRecordEvidenceApplicabilityOperation(CRecordEvidenceApplicability(app,CRecordEvidenceApplicabilityData(result,
            evidence.map { CRecordEvidenceApplicabilityDataEvidenceItem(it,"retained",emptyList(),"same exact retained subject") },emptyList(),listOf(f.source)),emptyList())))
        return CAcceptResultOperation(CAcceptResult(WorkKey.Id(f.project,uid(n+1)),CAcceptResultData(result,f.taskKey,plan,evidence,emptyList(),emptyList(),
            f.state.reference(WorkFamily.EVIDENCE_APPLICABILITY,app),listOf(f.criterion.id),emptyList(),f.reportRef),emptyList()))
    }
    @Test fun `F1 disconnected plan is rejected at creation and cannot yield acceptance or restore`() {
        val f=ReviewProbeFixture();f.active();val result=f.result();val before=f.state
        val key=f.planKey.copy(id=uid(700));val weak=f.plan.copy(previous=null,gates=f.plan.gates.map { it.copy(stage="milestone") })
        // Equivalent to the immutable reviewer probe, but the whole prohibited path is guarded:
        // correct rejection may occur at revision creation rather than only at accept_result.
        assertFailsWith<WorkFault> {
            revise(f,key,weak)
            f.apply(acceptance(f,result,key,emptyList(),701))
        }
        assertSame(before,f.state)
        assertTrue(f.state.records.keys.none { it.family==WorkFamily.ACCEPTANCES })
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> {
            WorkState.restore(f.project,before.records.values+record(key,WVerificationPlansPayload(weak)),before.bodies.values)
        }.problem)
        val bypass=acceptance(f,result,f.planKey,emptyList(),703).value
        val counterfeit=WAcceptances(bypass.data.result,bypass.data.task,bypass.data.verificationPlan,bypass.data.evidence,bypass.data.reports,
            bypass.data.dispositions,bypass.data.applicability,bypass.data.criterionIds,bypass.data.permittedTargets,bypass.data.basis,f.actor.principalId)
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> {
            WorkState.restore(f.project,f.state.records.values+record(bypass.key,WAcceptancesPayload(counterfeit)),f.state.bodies.values)
        }.problem)
        assertTrue(f.state.records.keys.none { it.family==WorkFamily.ACCEPTANCES })
    }
    @Test fun `F1 method refinements reordering and added checks retain original acceptance obligations`() {
        val f=ReviewProbeFixture();f.active();val ev=f.evidence();val result=f.result(evidence=ev)
        val key=f.planKey.copy(id=uid(730))
        val extra=WCriterion(uid(731),"Supplementary within-scope check",emptyList())
        val added=f.plan.gates.single().copy(id=uid(732),criterionIds=listOf(extra.id),reason="added check")
        val plan=f.plan.copy(previous=f.planKey,criteria=listOf(extra,f.criterion.copy(statement="Readable rendering; exact task still governs")),
            gates=listOf(added,f.plan.gates.single().copy(reason="refined method",groupId=uid(733))),reason="method-only refinement")
        revise(f,key,plan)
        assertFailsWith<WorkFault> { f.apply(acceptance(f,result,key,emptyList(),734)) }
        f.apply(acceptance(f,result,key,listOf(ev),736))
        assertEquals(key,(f.state.records.values.single { it.payload is WAcceptancesPayload }.payload as WAcceptancesPayload).value.verificationPlan)
        WorkState.restore(f.project,f.state.records.values,f.state.bodies.values)
    }
    @Test fun `grouped candidate may be produced and retained but passing checks and a group tag cannot replace independent review`() {
        val f=ReviewProbeFixture()
        // The independent acceptance gate/group is selected BEFORE execution, not after a failure.
        val root=f.plan.copy(gates=f.plan.gates.map { it.copy(independent=true,groupId=uid(780),reason="planned related MR review") })
        f.state=WorkState.restore(f.project,f.state.records.values.filter { it.identity!=WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,f.planKey) }+
            record(f.planKey,WVerificationPlansPayload(root)),f.state.bodies.values)
        f.active();val evidence=f.evidence();val candidate=f.result(evidence=evidence)
        assertEquals(candidate,f.state.reference(WorkFamily.RESULTS,candidate.identity.key))
        assertTrue(f.state.records.keys.none { it.family==WorkFamily.ACCEPTANCES })
        val attempt=acceptance(f,candidate,f.planKey,listOf(evidence),781)
        val before=f.state
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { f.apply(attempt) }.problem)
        assertSame(before,f.state)
        WorkState.restore(f.project,f.state.records.values,f.state.bodies.values) // pending review is a valid produced state
    }
    @Test fun `grouping preserves an existing early independent gate instead of silently moving weakening or deferring it`() {
        val f=ReviewProbeFixture()
        for(stage in listOf("dispatch","acceptance","integration")) {
            val required=f.plan.copy(gates=f.plan.gates.map { it.copy(independent=true,stage=stage) })
            val grouped=required.copy(gates=required.gates.map { it.copy(groupId=uid(790),reason="same requirements in a related review group") })
            assertTrue(verificationPlanPreserves(required,grouped),stage)
            for(weakened in listOf(
                grouped.copy(gates=grouped.gates.map { it.copy(stage="milestone") }),
                grouped.copy(gates=grouped.gates.map { it.copy(independent=false) }),
                grouped.copy(gates=grouped.gates.map { it.copy(deferred=true) }),
                grouped.copy(gates=emptyList()),
            ))assertFalse(verificationPlanPreserves(required,weakened),stage)
        }
    }
    private fun approvedCoreDecision(f: ReviewProbeFixture): KnowledgeReference {
        val owner=Actor(uid(750));val planner=Actor(f.actor.principalId)
        var core=ApprovalWorkflow.start(AccessPolicy(listOf(Grant(owner.principalId,f.project,Capability.entries.toSet()),
            Grant(planner.principalId,f.project,setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE)))))
        val text=Utf8Content.fromText("Exact scoped authorization to move the synthetic acceptance check to milestone.")
        val document=DocumentRevision(RevisionKey(DocumentKey(f.project,uid(751)),uid(752)),text,StructureIndex(emptyList(),listOf(ByteSpan(0,text.sizeBytes))))
        core=good(core.propose(planner,document)).state
        val ref=document.contextReference()
        val packet=ReviewPacket(listOf(ReviewItem(uid(753),ref)),"Approve the exact scoped gate change?","Synthetic task and gate only","Explicit boundary waiver",
            listOf("Gate remains held at milestone"),listOf("Retain acceptance check"),listOf("No factual test pass is asserted"),listOf(ref),emptyList(),emptyList())
        val request=good(core.request(planner,f.project,uid(754),packet))
        core=request.state
        assertIs<WorkflowResult.Failure>(core.approvalTrace(planner,ref))
        val resolved=good(core.resolve(owner,f.project,request.value.id,request.value.digest,request.value.packet.digest,StructuredAnswer.Approve))
        assertIs<WorkflowResult.Success<*>>(resolved.state.approvalTrace(planner,ref))
        return ref
    }
    @Test fun `F1 actual core decision required for rooted weakening and historical restore`() {
        val f=ReviewProbeFixture();f.active();val result=f.result();val decision=approvedCoreDecision(f)
        val key=f.planKey.copy(id=uid(755));val weak=f.plan.copy(previous=f.planKey,gates=f.plan.gates.map { it.copy(stage="milestone") },authorizedChanges=listOf(decision))
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { revise(f,key,weak) }.problem)
        f.facts=f.facts.copy(approvedDecisions=setOf(decision));revise(f,key,weak)
        f.apply(acceptance(f,result,key,emptyList(),756))
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { WorkState.restore(f.project,f.state.records.values,f.state.bodies.values) }.problem)
        val restored=WorkState.restore(f.project,f.state.records.values,f.state.bodies.values,approvedPlanChanges=setOf(decision))
        assertEquals(weak,restored.governingVerificationPlan(f.taskKey,key))
        // A later preserving revision cannot lose the authorization proof of its ancestor.
        val later=key.copy(id=uid(758));revise(f,later,weak.copy(previous=key,authorizedChanges=emptyList(),reason="later method refinement"))
        assertFailsWith<CodecFault> { WorkState.restore(f.project,f.state.records.values,f.state.bodies.values) }
    }
    @Test fun `F1 null foreign missing and cyclic lineage cannot become another governing root`() {
        val f=ReviewProbeFixture();val key=f.planKey.copy(id=uid(760))
        val foreign=f.taskKey.copy(taskId=uid(761));val other=f.planKey.copy(id=uid(762))
        val cases=listOf(
            listOf(record(key,WVerificationPlansPayload(f.plan.copy(previous=null)))),
            listOf(record(key,WVerificationPlansPayload(f.plan.copy(previous=other)))),
            listOf(record(key,WVerificationPlansPayload(f.plan.copy(previous=other))),record(other,WVerificationPlansPayload(f.plan.copy(task=foreign,previous=null)))),
            listOf(record(key,WVerificationPlansPayload(f.plan.copy(previous=other))),record(other,WVerificationPlansPayload(f.plan.copy(previous=key))))
        )
        cases.forEach { extra ->assertFailsWith<CodecFault> { WorkState.restore(f.project,f.state.records.values+extra,f.state.bodies.values) } }
        // Also exercise a fully resolving foreign root, not merely a missing foreign task.
        f.apply(CCreateTaskRevisionOperation(CCreateTaskRevision(foreign,f.task.copy(verificationPlan=other),
            CCreateTaskRevisionNewVerificationPlan(other,f.plan.copy(task=foreign)),emptyList())))
        assertFailsWith<WorkFault> { revise(f,key,f.plan.copy(previous=other)) }
        assertFailsWith<CodecFault> { WorkState.restore(f.project,f.state.records.values+record(key,WVerificationPlansPayload(f.plan.copy(previous=other))),f.state.bodies.values) }
        val weakening=listOf(
            f.plan.copy(previous=f.planKey,gates=f.plan.gates.map { it.copy(stage="milestone") }),
            f.plan.copy(previous=f.planKey,gates=emptyList()),
            f.plan.copy(previous=f.planKey,criteria=emptyList()),
            f.plan.copy(previous=f.planKey,gates=f.plan.gates.map { it.copy(deferred=true) })
        )
        weakening.forEach { plan ->
            // Empty collections violate structural minima; other weakenings are domain/graph refusals.
            assertFails { revise(f,key,plan) }
            assertFails { WorkState.restore(f.project,f.state.records.values+record(key,WVerificationPlansPayload(plan)),f.state.bodies.values) }
        }
        val independent=f.plan.copy(gates=f.plan.gates.map { it.copy(independent=true) })
        f.state=WorkState.restore(f.project,f.state.records.values.filter { it.identity!=WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,f.planKey) }+record(f.planKey,WVerificationPlansPayload(independent)),emptyList())
        assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> { revise(f,key,independent.copy(previous=f.planKey,gates=independent.gates.map { it.copy(independent=false) })) }.problem)
    }
}
