// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import java.util.UUID

/** Deterministic canonical review task from the FULL service-selected result/requirement closure.
 * No profile qualification or reviewer authority is minted here. Conflicting bases/profiles and
 * oversized closures hold instead of selecting a convenient producer or truncating obligations. */
internal data class AutomaticCandidateReviewPlan(val taskCommand:WorkCommand,val manifestCommand:WorkCommand,
    val selection:JsonValue,val preparationId:UUID,val reviewId:UUID,val contextId:UUID)

internal fun automaticCandidateReviewPlan(work:WorkState,planRef:WorkReference,evidence:ObservedCandidateReviewReceipts,
    checkPlanId:UUID):AutomaticCandidateReviewPlan {
    val plan=(work.resolve(planRef).payload as WDispatchPlanPayload).value
    require(plan.projectId==work.projectId && evidence.selected.isNotEmpty() && plan.candidateChecks.isNotEmpty())
    val required=plan.entries.map {entry ->
        val task=if(entry.futureDependencies.isEmpty())entry.task else requireNotNull(dispatchClosedSelection(work,planRef,entry)).task
        (chooseDispatchResult(work,DispatchFutureDependency(task,"implemented")) as DispatchResultChoice.Selected).result
    }
    require(required==evidence.selected && evidence.checks.map {it.checkId}.toSet()==plan.candidateChecks.map {it.checkId}.toSet())
    val subject=StrictJson.encode(obj("project" to uuid(work.projectId),"plan" to StrictJson.decode(identityCodec.encodeReference(planRef)),
        "candidate" to encodeWSnapshot(evidence.candidate),"checkPlan" to uuid(checkPlanId)))
    fun id(kind:String)=UUID.nameUUIDFromBytes("writhelm.candidate-review.v1\u0000$kind\u0000".toByteArray()+subject)
    val taskKey=WorkKey.TaskRevision(work.projectId,id("task"),0)
    val verificationKey=WorkKey.Revision(work.projectId,id("verification-plan"),0)
    val manifestKey=WorkKey.Id(work.projectId,id("manifest"))
    val tasks=evidence.selected.map {(work.resolve(it).payload as WResultsPayload).value.task}
    val terms=tasks.map {work.task(it)}
    val profile=terms.map {requireNotNull(it.executionProfile)}.distinct().single()
    val commonBase=terms.map {requireNotNull(it.base).copy(code=emptyList())}.distinct().single()
    val base=commonBase.copy(code=listOf(evidence.candidate))
    val criteria=mutableListOf<WCriterion>();val gates=mutableListOf<WGate>();val inputs=mutableListOf<WManifestItem>()
    val manifests=tasks.map {task ->
        val result=(work.resolve(evidence.selected[tasks.indexOf(task)]).payload as WResultsPayload).value
        (work.resolve(result.manifest).payload as WInputManifestsPayload).value
    }
    require(manifests.map {it.applicability}.distinct().size==1 && manifests.map {it.declaredClosureAssessment}.distinct().size==1 && manifests.all {it.knownGaps.isEmpty()})
    tasks.zip(terms).forEach { (key,task) ->
        val reference=work.reference(WorkFamily.TASK_REVISIONS,key)
        val source=WSourceVariant2Value(WSourceVariant2("work",reference))
        val ids=task.criteria.associate {criterion ->criterion.id to id("criterion:${key.taskId}:${key.revision}:${criterion.id}")}
        criteria+=task.criteria.map {it.copy(id=ids.getValue(it.id),clauses=(it.clauses+source).distinct())}
        val verification=requireNotNull(task.verificationPlan)
        val original=(work.record(WorkFamily.VERIFICATION_PLANS,verification).payload as WVerificationPlansPayload).value
        gates+=original.gates.map {it.copy(id=id("gate:${key.taskId}:${key.revision}:${it.id}"),criterionIds=it.criterionIds.map(ids::getValue),independent=true,groupId=null)}
        inputs+=WManifestItem(source,true,"Exact producer task requirements",emptyList())
        inputs+=WManifestItem(WSourceVariant2Value(WSourceVariant2("work",work.reference(WorkFamily.VERIFICATION_PLANS,verification))),true,"Exact producer verification plan",emptyList())
    }
    evidence.selected.forEach {inputs+=WManifestItem(WSourceVariant2Value(WSourceVariant2("work",it)),true,"Required all-result review subject",emptyList())}
    inputs+=manifests.flatMap {it.items}.map {it.copy(mandatory=true)}
    inputs+=WManifestItem(WSourceVariant1Value(WSourceVariant1("artifact",evidence.candidate)),true,"Exact immutable all-result candidate",emptyList())
    val whole=WCriterion(id("whole-diff-criterion"),"The complete immutable candidate satisfies every mandatory selected task/input without unrelated changes",emptyList())
    criteria+=whole
    gates+=WGate(id("whole-diff-gate"),listOf(whole.id),"native",true,"acceptance",null,false,"Independent whole-candidate semantic review; checks alone do not establish it")
    require(criteria.size<=256 && gates.size<=256)
    val review=terms.first().copy(kind="verification",state="executable",objective="Independently review the exact all-result candidate against every selected task and mandatory input",
        base=base,scope=WScope("The exact immutable whole candidate and all selected task/base/input/check requirements",
            "Only the reviewer's own report and scratch; never candidate or producer work areas","Concrete findings and explicit review limits; no acceptance or integration",
            (terms.flatMap {it.scope.exclusions}+listOf("Candidate edits","Producer plan/state changes","Acceptance, integration and publication")).distinct()),
        constraints=(terms.flatMap {it.constraints}+"Preserve every selected task's mandatory obligations; missing or conflicting input holds review").distinct(),
        escalation=(terms.flatMap {it.escalation}+"Escalate missing mandatory input, ambiguous profile/base or uncertain original native effects; do not invent readiness").distinct(),
        dependencies=tasks.zip(evidence.selected).map {(task,result)->WDependency(task,"implemented",result,emptyList(),"Exact required result for independent candidate review")},
        criteria=criteria,expectedOutput="Canonical verification report on the complete immutable candidate, with concrete findings and explicit limits; no integration authority",
        executionProfile=profile,verificationPlan=verificationKey,approvedDesign=terms.flatMap {it.approvedDesign}.distinct(),unresolved=emptyList())
    val manifest=WInputManifests(taskKey,base,manifests.first().applicability,inputs.distinct(),manifests.first().declaredClosureAssessment,emptyList())
    val row=WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,manifestKey),WInputManifestsPayload(manifest))
    val codec=WorkCodec(modelVersion=work.modelVersion)
    val manifestRef=WorkReference(row.identity,codec.digest(row))
    val create=WorkCommand(id("create-command"),work.projectId,CCreateTaskRevisionOperation(CCreateTaskRevision(taskKey,review,
        CCreateTaskRevisionNewVerificationPlan(verificationKey,WVerificationPlans(taskKey,null,criteria,gates,"Service-derived complete candidate review requirements",emptyList())),emptyList())))
    val pin=WorkCommand(id("manifest-command"),work.projectId,CPinInputManifestOperation(CPinInputManifest(manifestKey,manifest,emptyList())))
    codec.encodeCommand(create);codec.encodeCommand(pin) // enforce existing closed schemas/bounds
    val selection=obj("task" to StrictJson.decode(identityCodec.encodeKey(taskKey)),"manifest" to StrictJson.decode(identityCodec.encodeReference(manifestRef)),
        "profile" to StrictJson.decode(identityCodec.encodeKey(profile)),"resources" to JArray(listOf(obj("resource" to StrictJson.decode(identityCodec.encodeKey(WorkKey.Resource(work.projectId,id("workspace-resource")))),"expectedGeneration" to JNull))),
        "reviewSubjects" to JArray(evidence.selected.map {StrictJson.decode(identityCodec.encodeReference(it))}))
    return AutomaticCandidateReviewPlan(create,pin,selection,id("preparation"),id("review"),id("context"))
}
