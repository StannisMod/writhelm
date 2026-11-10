// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.KnowledgeReference

/** Compare obligations, not presentation. Task criteria remain the authoritative statements:
 * reordering/rendering/reasons/grouping may change without an owner ballot. Required source clauses,
 * stable check IDs, coverage, boundary, stage and independence cannot silently be removed. */
internal fun verificationPlanPreserves(old: WVerificationPlans,next: WVerificationPlans): Boolean {
    val criteria=next.criteria.associateBy { it.id }
    val gates=next.gates.associateBy { it.id }
    return old.criteria.all { c -> criteria[c.id]?.clauses?.containsAll(c.clauses)==true } &&
        old.gates.all { g -> gates[g.id]?.let { n ->
            n.criterionIds.containsAll(g.criterionIds) && n.boundary==g.boundary && n.stage==g.stage &&
                (!g.independent || n.independent) && (g.deferred || !n.deferred)
        }==true }
}

/** Exact task-selected root; no alternate null root, foreign task, cycle or opaque approval flag.
 * Approval facts are resolved from the existing core, not from labels or work records. */
internal fun rootedVerificationPlan(taskKey: WorkKey.TaskRevision,task: WTaskRevisions,key: WorkKey.Revision,
    load: (WorkKey.Revision)->WVerificationPlans,approved: Set<KnowledgeReference>,maxDepth: Int): WVerificationPlans {
    val root=task.verificationPlan ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    val seen=HashSet<WorkKey.Revision>();var current=key;var leaf: WVerificationPlans?=null
    while(true) {
        if(seen.size>=maxDepth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        workRequire(current.projectId==taskKey.projectId && seen.add(current),WorkProblem.INVALID_REFERENCE)
        val plan=load(current);if(leaf==null)leaf=plan
        workRequire(plan.task==taskKey,WorkProblem.INVALID_REFERENCE)
        workRequire(plan.authorizedChanges.all { it in approved },WorkProblem.BLOCKED_INPUT)
        if(current==root) {
            workRequire(plan.previous==null && plan.criteria.map { it.id }.containsAll(task.criteria.map { it.id }),WorkProblem.INVALID_REFERENCE)
            return leaf
        }
        val previous=plan.previous ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(previous !in seen && previous.projectId==taskKey.projectId,WorkProblem.INVALID_REFERENCE)
        val old=load(previous)
        workRequire(old.task==taskKey,WorkProblem.INVALID_REFERENCE)
        if(!verificationPlanPreserves(old,plan))workRequire(plan.authorizedChanges.isNotEmpty(),WorkProblem.BLOCKED_INPUT)
        current=previous
    }
}

/** The acceptance's recorded checks must discharge its governing plan on restore too. This checks
 * intrinsic immutable evidence, not today's credential policy or reports written after acceptance. */
internal fun WorkState.acceptanceChecks(v: WAcceptances,plan: WVerificationPlans=governingVerificationPlan(v.task,v.verificationPlan)) {
    val result=resolve(v.result).payload
    val resultTask=when(result) { is WResultsPayload -> result.value.task;is WMechanicalResultsPayload -> result.value.task;else -> throw WorkFault(WorkProblem.INVALID_REFERENCE) }
    val artifacts=when(result) { is WResultsPayload -> result.value.artifacts;is WMechanicalResultsPayload -> result.value.artifacts;else -> throw WorkFault(WorkProblem.INVALID_REFERENCE) }
    val producerExecution=when(result) { is WResultsPayload -> result.value.producer.executionId;is WMechanicalResultsPayload -> result.value.producer.executionId;else -> throw WorkFault(WorkProblem.INVALID_REFERENCE) }
    val authors=when(result) { is WResultsPayload -> setOf(result.value.producer.principalId);is WMechanicalResultsPayload -> result.value.authorship.authors.toSet();else -> throw WorkFault(WorkProblem.INVALID_REFERENCE) }
    val terms=task(v.task)
    workRequire(resultTask==v.task && v.acceptedBy !in authors,WorkProblem.BLOCKED_INPUT)
    workRequire(plan.task==v.task && v.criterionIds.toSet()==terms.criteria.map { it.id }.toSet(),WorkProblem.BLOCKED_INPUT)
    val applicable=(resolve(v.applicability).payload as? WEvidenceApplicabilityPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    workRequire(applicable.result==v.result,WorkProblem.INVALID_SUBJECT)
    val evidence=v.evidence.map { reference ->
        workRequire(applicable.evidence.any { it.evidence==reference && it.state=="retained" },WorkProblem.BLOCKED_INPUT)
        (resolve(reference).payload as? WEvidencePayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
    }
    val reports=v.reports.map { reference ->
        val report=(resolve(reference).payload as? WVerificationReportsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(v.result in report.subjects && report.reviewer.executionId!=producerExecution &&
            (result !is WMechanicalResultsPayload || report.reviewer.principalId !in authors && report.task==v.task && report.plan==v.verificationPlan),WorkProblem.INVALID_SUBJECT)
        reference to report
    }
    val dispositions=v.dispositions.map { (resolve(it).payload as? WDispositionsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE) }
    workRequire(dispositions.all { it.result==v.result },WorkProblem.INVALID_SUBJECT)
    workRequire(evidence.all { e ->e.outcome=="passed" && e.subjects.any { s ->when(s) {
        is WSourceVariant2Value -> s.value.reference==v.result
        is WSourceVariant1Value -> s.value.artifact in artifacts
        else -> false
    } } },WorkProblem.BLOCKED_INPUT)
    plan.gates.filter { it.stage=="acceptance" }.forEach { gate ->
        workRequire(gate.id !in applicable.heldGateIds,WorkProblem.BLOCKED_INPUT)
        val clauses=(terms.criteria+plan.criteria).filter { it.id in gate.criterionIds }.flatMap { it.clauses }.distinct()
        val checked=evidence.any { e ->e.boundary==gate.boundary && e.clauseCases.isNotEmpty() && clauses.all { c ->e.clauseCases.any { it.clause==c } } }
        workRequire(checked && (!gate.independent || reports.any { gate.id in it.second.gateIds }),WorkProblem.BLOCKED_INPUT)
    }
    reports.forEach { (reference,report) ->report.findings.filter { it.blocking &&
        (it.affectedResultIds.isEmpty() || (v.result.identity.key as WorkKey.Id).id in it.affectedResultIds) }.forEach { finding ->
        workRequire(dispositions.any { it.report==reference && it.findingId==finding.id && it.decision in setOf("not-applicable","owner-exception") },WorkProblem.BLOCKED_INPUT)
    } }
}
