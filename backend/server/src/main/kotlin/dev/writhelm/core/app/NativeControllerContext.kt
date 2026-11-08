// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.PresentedCredential
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.Capability
import java.util.UUID

/** Own-controller cross-task basis. Include the COMPLETE own active-assignment set, exact prior
 * continuation frontier and declared unassigned task heads/plans. No timestamp/latest-tip heuristic,
 * fabricated executor assignment, other-controller continuation or implicit work/owner permission. */
internal object NativeControllerContext {
    internal class Basis(val profile: WorkRecord,val previous: WorkRecord?,val frontier: List<WControllerContinuationsFrontierItem>,
        val plans: List<WorkRecord>,val packet: String,val obligations: List<UUID>)
    fun basis(state: H1BoundState,project: UUID,controller: UUID,profileKey: WorkKey.Profile): Basis {
        val work=state.work.getValue(project)
        workRequire(profileKey.projectId==project,WorkProblem.FOREIGN_REFERENCE)
        val profile=work.record(WorkFamily.PROFILES,profileKey)
        val own=work.records.values.filter { it.identity.family==WorkFamily.CONTROLLER_CONTINUATIONS &&
            (it.identity.key as WorkKey.ControllerContinuation).controllerId==controller }
        val predecessors=own.mapNotNull { (it.payload as WControllerContinuationsPayload).value.previous?.key }.toSet()
        val tips=own.filter { it.identity.key !in predecessors }
        workRequire(tips.size<=1,WorkProblem.INCOMPLETE_HANDOFF)
        val previous=tips.singleOrNull()
        previous?.let { workRequire((it.payload as WControllerContinuationsPayload).value.gaps.isEmpty(),WorkProblem.INCOMPLETE_HANDOFF) }
        val assignments=work.records.values.filter { (it.payload as? WAssignmentsPayload)?.value?.let { a ->
            a.intendedExecution.controllerId==controller && a.state !in setOf("completed","replaced") }==true }
        workRequire(assignments.size<=256,WorkProblem.INCOMPLETE_HANDOFF)
        val tasks=linkedSetOf<WorkKey.TaskRevision>()
        assignments.forEach { tasks.add((it.payload as WAssignmentsPayload).value.task) }
        previous?.let { tasks.addAll((it.payload as WControllerContinuationsPayload).value.frontier.map { f ->f.task }) }
        // A project planner may have declared ready/draft work without a delegated assignment yet.
        // Keep those exact heads as planning data; they do not acquire execution/approval authority.
        val assignedElsewhere=work.records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }
            .filter { it.intendedExecution.controllerId!=controller && it.state !in setOf("completed","replaced") }.map { it.task }.toSet()
        work.records.values.mapNotNull { (it.payload as? WTaskHeadsPayload)?.value }.forEach { head ->
            listOfNotNull(head.draft,head.executable).filter { it !in assignedElsewhere }.forEach(tasks::add)
        }
        val plans=work.records.values.filter { (it.payload as? WPlansPayload)?.value?.assignment==null && it.payload is WPlansPayload }
            .filter { (it.payload as WPlansPayload).value.task !in assignedElsewhere }
        plans.forEach { tasks.add((it.payload as WPlansPayload).value.task) }
        workRequire(tasks.isNotEmpty() && tasks.size<=256,WorkProblem.INCOMPLETE_HANDOFF)
        val knowledge=state.core.snapshot(8192);val catalog=RevisionCatalog(knowledge.revisions)
        val sources=linkedSetOf<KnowledgeReference>()
        val taskRecords=tasks.sortedBy { identityCodec.recordPath(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,it)) }.map { key ->
            val row=work.record(WorkFamily.TASK_REVISIONS,key);val task=(row.payload as WTaskRevisionsPayload).value
            task.base?.let { sources.addAll(pinnedKnowledge(knowledge,it)) }
            sources.addAll(task.approvedDesign)
            row
        }
        workRequire(sources.size<=256,WorkProblem.INCOMPLETE_HANDOFF)
        val sourceBodies=sources.map { ref ->
            workRequire(state.core.validationSourceAvailable(ref),WorkProblem.BLOCKED_INPUT)
            val fragments=when(ref) {
                is ClauseReference -> listOf((catalog.read(ref) as? ReadResult.Found)?.value?.located ?: throw WorkFault(WorkProblem.BLOCKED_INPUT))
                is ContextReference -> (catalog.read(ref) as? ReadResult.Found)?.value?.fragments ?: throw WorkFault(WorkProblem.BLOCKED_INPUT)
            }
            obj("reference" to reference(ref),"role" to str("exact-pinned-task-source"),"fragments" to JArray(fragments.map {
                obj("start" to num(it.span.start),"end" to num(it.span.endExclusive),"text" to str(it.content.text()))
            }))
        }.sortedBy { StrictJson.encode(it).decodeToString() }
        val frontier=taskRecords.map { row ->
            val key=row.identity.key as WorkKey.TaskRevision
            val active=assignments.filter { (it.payload as WAssignmentsPayload).value.task==key }.map { r ->
                val a=(r.payload as WAssignmentsPayload).value
                WControllerContinuationsFrontierItemAssignmentsItem((r.identity.key as WorkKey.Id).id,a.generation,a.executionGeneration)
            }.sortedBy { it.assignmentId.toString() }
            val ids=((row.payload as WTaskRevisionsPayload).value.criteria.map { it.id }+plans.filter { (it.payload as WPlansPayload).value.task==key }
                .flatMap { (it.payload as WPlansPayload).value.steps }.filter { it.state!="complete" }.map { it.id }).distinct().sortedBy { it.toString() }
            WControllerContinuationsFrontierItem(key,active,ids)
        }
        fun encoded(row: WorkRecord)=StrictJson.decode(WorkCodec().encodeRecord(row,true),JsonLimits(bytes=4*1024*1024,stringBytes=262144,depth=128,collection=4096,tokens=500000))
        val packetValue=obj("profile" to encoded(profile),"exactTaskContracts" to JArray(taskRecords.map(::encoded)),
            "exactPinnedSources" to JArray(sourceBodies),"scope" to str("own-controller-cross-task; not a delegated executor or approval authority"))
        val value=StrictJson.encode(packetValue,256*1024).decodeToString()
        val packet=StrictJson.encode(JArray(listOf(obj("key" to str("writhelm-controller-$controller"),"value" to str(value)))),2*1024*1024).decodeToString()
        return Basis(profile,previous,frontier,plans,packet,frontier.flatMap { it.obligationIds }.distinct())
    }
    fun read(state: H1BoundState,key: PresentedCredential,project: UUID,controller: UUID,profileKey: WorkKey.Profile,
        safety: NativeControllerRuntimeSafety): JsonValue {
        val c=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(c.scope!=CredentialScope.Controller(controller) || !state.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val b=basis(state,project,controller,profileKey);val work=state.work.getValue(project)
        fun record(row: WorkRecord)=str(WorkCodec().encodeRecord(row,true).decodeToString())
        val reusable=linkedMapOf<Digest,WorkBodyReference>();val protected=H1ProtectedReads()
        work.records.values.forEach { row ->
            val bodies=workLinks(row.payload).bodies
            if(bodies.isEmpty())return@forEach
            val facts=workRecordFacts(row)
            try {
                protected.readWork(state,key,WorkRead.Exact(project,row.identity,if(row.identity.family.mutable)null else facts.digest,facts.generation))
            }catch(_: AppAccessFault) { return@forEach }
            bodies.forEach { body ->if(work.bodies[body.digest]?.sizeBytes==body.byteLength)reusable[body.digest]=body }
        }
        workRequire(reusable.size<=4096,WorkProblem.INCOMPLETE_HANDOFF)
        return obj("formatVersion" to num(1),"basis" to state.basis.json(),"controllerId" to uuid(controller),
            "nativeReady" to JBoolean(state.authority.workAllows(c,project,WorkPermission.TASK_PLAN) && safety.safeAccess(state,controller)),"profileJSON" to str(identityCodec.encodeKey(profileKey).decodeToString()),
            "profileRecordJSON" to record(b.profile),"frontierJSON" to str(StrictJson.encode(JArray(b.frontier.map(::encodeWControllerContinuationsFrontierItem)),2*1024*1024).decodeToString()),
            "plansJSON" to JArray(b.plans.map(::record)),"previousJSON" to (b.previous?.let(::record) ?: JNull),
            "previousReferenceJSON" to (b.previous?.let { str(identityCodec.encodeReference(work.reference(WorkFamily.CONTROLLER_CONTINUATIONS,it.identity.key)).decodeToString()) } ?: JNull),
            "reusableBodies" to JArray(reusable.values.sortedBy { it.digest.hex }.map { StrictJson.decode(identityCodec.encodeBodyReference(it)) }),
            "obligationIds" to JArray(b.obligations.map(::uuid)),"packet" to str(b.packet))
    }
}
