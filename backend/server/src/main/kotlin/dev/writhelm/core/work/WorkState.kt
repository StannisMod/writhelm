// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import java.util.UUID
import java.util.Collections

internal enum class WorkProblem { BLOCKED_INPUT, CLAIM_CONFLICT, DIGEST_MISMATCH, DUPLICATE_ID,
    FOREIGN_REFERENCE, GENERATION_EXHAUSTED, INCOMPLETE_HANDOFF, INVALID_BINDING, INVALID_REFERENCE,
    INVALID_SUBJECT, INVALID_TRANSITION, MISSING_BODY, NATIVE_UNKNOWN, NOT_ACCEPTED, NOT_FOUND,
    NOT_READY, STALE_GENERATION, STALE_TARGET, WRONG_ASSIGNMENT }
internal class WorkFault(val problem: WorkProblem): RuntimeException(problem.name)
internal fun workRequire(ok: Boolean, problem: WorkProblem) { if(!ok)throw WorkFault(problem) }
internal fun nextGeneration(n: Long): Long {
    workRequire(n>=0 && n<Long.MAX_VALUE,WorkProblem.GENERATION_EXHAUSTED); return n+1
}
internal data class WorkGraphBudget(val records: Int=8192,val bodies: Int=8192,val edges: Int=16384,
    val depth: Int=256,val aggregateBytes: Long=64L*1024*1024) {
    init { require(records>0 && bodies>0 && edges>0 && depth>0 && aggregateBytes>0) }
}
internal data class WorkLinks(val records: List<WorkReference>,val bodies: List<WorkBodyReference>)
/** Traversal of an already closed/typed payload, never unvalidated upload admission. */
internal fun workLinks(p: WorkPayload): WorkLinks {
    val refs=ArrayList<WorkReference>();val bodies=ArrayList<WorkBodyReference>()
    fun visit(v: JsonValue) {
        when(v) {
            is JObject -> when(v.entries.keys) {
                setOf("family","key","digest") -> refs.add(identityCodec.decodeReference(StrictJson.encode(v)))
                setOf("digest","byteLength") -> bodies.add(identityCodec.decodeBodyReference(StrictJson.encode(v)))
                else -> v.entries.values.forEach(::visit)
            }
            is JArray -> v.values.forEach(::visit)
            else -> Unit
        }
    }
    visit(encodeWorkPayload(p));return WorkLinks(refs,bodies)
}
internal fun recordGeneration(p: WorkPayload): Long? = when(p) {
    is WTaskHeadsPayload -> p.value.generation
    is WPlansPayload -> p.value.generation
    is WAssignmentsPayload -> p.value.generation
    is WClaimsPayload -> p.value.generation
    is WNativeIntentsPayload -> p.value.generation
    is WDeliveriesPayload -> p.value.generation
    is WIntegrationIntentsPayload -> p.value.generation
    is WContinuationsPayload -> p.value.generation
    else -> null
}

/** Validated immutable snapshot. The constructor is intentionally private; no trusted-map shortcut. */
internal class WorkState private constructor(
    val projectId: UUID,
    val records: Map<WorkRecordIdentity,WorkRecord>,
    val bodies: Map<Digest,Utf8Content>,
    val budget: WorkGraphBudget,
    private val encodedSizes: Map<WorkRecordIdentity,Int>,
    private val totalBytes: Long,
    private val approvedPlanChanges: Set<KnowledgeReference>,
    val modelVersion:Int,
) {
    private val codec get()=WorkCodec(modelVersion=modelVersion)
    val encodedByteCount: Long get()=totalBytes
    private var dependencies: Map<WorkRecordIdentity,Set<WorkRecordIdentity>> = emptyMap()
    private var reverseDependencies: Map<WorkRecordIdentity,Set<WorkRecordIdentity>> = emptyMap()
    private var intrinsicEdgeCosts: Map<WorkRecordIdentity,Int> = emptyMap()
    private var immutableEdgeCosts: Map<WorkRecordIdentity,Int> = emptyMap()
    /** Exact cost of this graph's full restore traversal, maintained from validated affected rows.
     * APP can share one namespace budget across per-project states without rescanning old bodies. */
    internal val fullRestoreEdges: Long get()=intrinsicEdgeCosts.values.sumOf { it.toLong() }+
        immutableEdgeCosts.values.sumOf { it.toLong() }+records.keys.count { it.family==WorkFamily.TASK_REVISIONS }
    fun record(f: WorkFamily,k: WorkKey): WorkRecord = records[WorkRecordIdentity(f,k)] ?: throw WorkFault(WorkProblem.NOT_FOUND)
    fun resolve(ref: WorkReference): WorkRecord {
        workRequire(ref.identity.key.projectId==projectId,WorkProblem.FOREIGN_REFERENCE)
        val r=records[ref.identity] ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(codec.digest(r)==ref.digest,WorkProblem.DIGEST_MISMATCH);return r
    }
    fun assignment(id: UUID): WAssignments = (record(WorkFamily.ASSIGNMENTS,WorkKey.Id(projectId,id)).payload as WAssignmentsPayload).value
    fun task(key: WorkKey.TaskRevision): WTaskRevisions = (record(WorkFamily.TASK_REVISIONS,key).payload as WTaskRevisionsPayload).value
    fun binding(subject: WAssignmentSubject): WAssignmentBindings = (record(WorkFamily.ASSIGNMENT_BINDINGS,
        WorkKey.AssignmentBinding(projectId,subject.assignmentId,subject.generation)).payload as WAssignmentBindingsPayload).value
    fun reference(f: WorkFamily,k: WorkKey): WorkReference {
        val r=record(f,k);return WorkReference(r.identity,codec.digest(r))
    }
    fun governingVerificationPlan(taskKey: WorkKey.TaskRevision,key: WorkKey.Revision): WVerificationPlans =
        rootedVerificationPlan(taskKey,task(taskKey),key,{ (record(WorkFamily.VERIFICATION_PLANS,it).payload as WVerificationPlansPayload).value },approvedPlanChanges,budget.depth)
    companion object {
        fun empty(project: UUID,budget: WorkGraphBudget=WorkGraphBudget(),modelVersion:Int=2): WorkState = restore(project,emptyList(),emptyList(),budget,modelVersion=modelVersion)
        fun restore(project: UUID,records: Collection<WorkRecord>,bodies: Collection<Utf8Content>,budget: WorkGraphBudget=WorkGraphBudget(),
            approvedPlanChanges: Set<KnowledgeReference> = emptySet(),modelVersion:Int=2): WorkState {
            if(records.size>budget.records || bodies.size>budget.bodies)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            if(approvedPlanChanges.size>budget.edges)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            var bytes=0L; val codec=WorkCodec(modelVersion=modelVersion)
            val copied=LinkedHashMap<WorkRecordIdentity,WorkRecord>();val sizes=LinkedHashMap<WorkRecordIdentity,Int>()
            records.forEach { r ->
                graph(r.identity.key.projectId==project)
                val encoded=codec.encodeRecord(r,true)
                if(encoded.size>budget.aggregateBytes-bytes)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                bytes+=encoded.size
                sizes[r.identity]=encoded.size
                if(copied.put(r.identity,codec.decodeRecord(encoded,true))!=null)refuse(CodecProblem.DUPLICATE_IDENTITY)
            }
            val bodyMap=LinkedHashMap<Digest,Utf8Content>()
            bodies.forEach { b ->
                if(b.sizeBytes>4*1024*1024 || b.sizeBytes>budget.aggregateBytes-bytes)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                bytes+=b.sizeBytes
                if(bodyMap.put(b.digest,b)!=null)refuse(CodecProblem.DUPLICATE_IDENTITY)
            }
            val state=WorkState(project,Collections.unmodifiableMap(copied),Collections.unmodifiableMap(bodyMap),budget,Collections.unmodifiableMap(sizes),bytes,
                Collections.unmodifiableSet(HashSet(approvedPlanChanges)),modelVersion)
            state.validate();return state
        }
    }
    /** Trusted reducer-only candidate construction. Not an upload/patch operation. Reuses unchanged
     * validated objects at H; checks changed records plus transitive reverse counterparts under a
     * finite affected-record budget. No historical body re-decode or whole metadata re-encoding. */
    fun derive(changed: Collection<WorkRecord>,addedBodies: Collection<Utf8Content>,newApprovedPlanChanges: Set<KnowledgeReference> = emptySet()): WorkState {
        if(newApprovedPlanChanges.size>budget.edges)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val approvals=approvedPlanChanges+newApprovedPlanChanges
        if(approvals.size>budget.edges)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val changedMap=changed.associateBy { it.identity }
        graph(changedMap.size==changed.size)
        if(changed.size>4096)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val copied=LinkedHashMap(records);val sizes=LinkedHashMap(encodedSizes);val bodyMap=LinkedHashMap(bodies)
        var bytes=totalBytes;val codec=this.codec
        changed.forEach { r ->
            graph(r.identity.key.projectId==projectId)
            val old=records[r.identity]
            if(old!=null) {
                graph(r.identity.family.mutable)
                graph(recordGeneration(r.payload)==nextGeneration(recordGeneration(old.payload)!!))
                if(old.payload is WContinuationsPayload && r.payload is WContinuationsPayload)
                    validateContinuationEdge(old.payload.value,r.payload.value)
            } else if(r.identity.family.mutable)graph(recordGeneration(r.payload)==0L)
            val encoded=codec.encodeRecord(r)
            bytes-=sizes[r.identity] ?: 0
            if(encoded.size>budget.aggregateBytes-bytes)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            bytes+=encoded.size;sizes[r.identity]=encoded.size;copied[r.identity]=codec.decodeRecord(encoded)
        }
        addedBodies.forEach { b ->
            graph(b.digest !in bodies && b.digest !in bodyMap)
            if(b.sizeBytes>1024*1024 || b.sizeBytes>budget.aggregateBytes-bytes)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            bytes+=b.sizeBytes;bodyMap[b.digest]=b
        }
        if(copied.size>budget.records || bodyMap.size>budget.bodies)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        val affected=changedMap.keys.toMutableSet()
        val queue=java.util.ArrayDeque(changedMap.keys)
        while(queue.isNotEmpty()) {
            reverseDependencies[queue.removeFirst()].orEmpty().forEach { if(affected.add(it)) {
                if(affected.size>4096)refuse(CodecProblem.RESOURCE_UNAVAILABLE);queue.add(it)
            } }
        }
        val state=WorkState(projectId,Collections.unmodifiableMap(copied),Collections.unmodifiableMap(bodyMap),budget,Collections.unmodifiableMap(sizes),bytes,
            Collections.unmodifiableSet(approvals),modelVersion)
        state.dependencies=dependencies
        state.reverseDependencies=reverseDependencies
        state.intrinsicEdgeCosts=intrinsicEdgeCosts
        state.immutableEdgeCosts=immutableEdgeCosts
        state.validate(affected)
        return state
    }
    private fun validate(selected: Set<WorkRecordIdentity>?=null) {
        var edges=0
        val discovered=LinkedHashMap<WorkRecordIdentity,MutableSet<WorkRecordIdentity>>()
        val costs=LinkedHashMap<WorkRecordIdentity,Int>()
        val immutableCosts=LinkedHashMap<WorkRecordIdentity,Int>()
        var current: WorkRecordIdentity?=null
        fun dependency(target: WorkRecordIdentity) { current?.let { discovered.getOrPut(it) { LinkedHashSet() }.add(target) } }
        fun edge() {
            if(edges==budget.edges)refuse(CodecProblem.RESOURCE_UNAVAILABLE);edges++
            current?.let { costs[it]=(costs[it] ?: 0)+1 }
        }
        fun required(f: WorkFamily,k: WorkKey): WorkRecord { edge();graph(k.projectId==projectId);val id=WorkRecordIdentity(f,k);dependency(id);return records[id] ?: refuse(CodecProblem.INVALID_GRAPH) }
        fun task(k: WorkKey.TaskRevision)=required(WorkFamily.TASK_REVISIONS,k)
        fun bind(s: WAssignmentSubject)=required(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(projectId,s.assignmentId,s.generation))
        val checked=selected?.map { records.getValue(it) } ?: records.values
        checked.forEach { r ->
            current=r.identity
            discovered[r.identity]=LinkedHashSet()
            costs[r.identity]=0
            val links=workLinks(r.payload)
            if(!r.identity.family.mutable) {
                val previous=when(val p=r.payload) {
                    is WLoadedViewsPayload -> p.value.previous!=null
                    is WMemoryPayload -> p.value.previous!=null
                    is WApplicabilityPayload -> p.value.previous!=null
                    is WVerificationPlansPayload -> p.value.previous!=null
                    is WControllerContinuationsPayload -> p.value.previous!=null
                    else -> false
                }
                immutableCosts[r.identity]=links.records.size+if(previous)1 else 0
            }
            links.records.forEach { ref -> edge();dependency(ref.identity);try { resolve(ref) }catch(_: WorkFault) { refuse(CodecProblem.INVALID_GRAPH) } }
            links.bodies.forEach { b -> edge();graph(bodies[b.digest]?.sizeBytes==b.byteLength) }
            when(val p=r.payload) {
                is WTaskRevisionsPayload -> p.value.let { v ->
                    v.dependencies.forEach { task(it.producer); it.selectedResult?.let { selected ->
                        graph(selected.identity.family==WorkFamily.RESULTS)
                        graph((resolve(selected).payload as WResultsPayload).value.task==it.producer)
                    } }
                    v.executionProfile?.let { required(WorkFamily.PROFILES,it) }
                    v.verificationPlan?.let { graph((required(WorkFamily.VERIFICATION_PLANS,it).payload as WVerificationPlansPayload).value.task==r.identity.key) }
                }
                is WTaskHeadsPayload -> { p.value.draft?.let { graph(it.taskId==(r.identity.key as WorkKey.Task).taskId);task(it) }
                    p.value.executable?.let { graph(it.taskId==(r.identity.key as WorkKey.Task).taskId);graph((task(it).payload as WTaskRevisionsPayload).value.state=="executable") } }
                is WPlansPayload -> { task(p.value.task);p.value.assignment?.let { graph((bind(it).payload as WAssignmentBindingsPayload).value.task==p.value.task) } }
                is WInputManifestsPayload -> { graph((task(p.value.task).payload as WTaskRevisionsPayload).value.base==p.value.base);required(WorkFamily.APPLICABILITY,p.value.applicability) }
                is WAssignmentsPayload -> p.value.let { v ->
                    task(v.task);graph(required(WorkFamily.PROFILES,v.profile).payload is WProfilesPayload)
                    graph(v.manifest.identity.family==WorkFamily.INPUT_MANIFESTS)
                    graph((resolve(v.manifest).payload as WInputManifestsPayload).value.task==v.task)
                    v.currentIntent?.let { graph((required(WorkFamily.NATIVE_INTENTS,it).payload as WNativeIntentsPayload).value.assignment==WAssignmentSubject((r.identity.key as WorkKey.Id).id,v.executionGeneration)) }
                    v.binding?.let { ref ->
                        graph(ref.identity==WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(projectId,(r.identity.key as WorkKey.Id).id,v.executionGeneration)))
                        val b=(resolve(ref).payload as WAssignmentBindingsPayload).value
                        graph(b.task==v.task && b.manifest==v.manifest && b.profile==v.profile && b.execution==v.execution && b.delegationId==v.delegationId && b.intendedExecution==v.intendedExecution && b.reviewSubjects==v.reviewSubjects && b.resources==v.requiredResources)
                    }
                    v.requiredResources.forEach { resource ->
                        val c=(required(WorkFamily.CLAIMS,resource).payload as WClaimsPayload).value
                        if(v.state !in setOf("completed","replaced"))graph((c.holder as? WClaimsHolderVariant0Value)?.value?.subject==WAssignmentSubject((r.identity.key as WorkKey.Id).id,v.executionGeneration))
                    }
                }
                is WAssignmentBindingsPayload -> p.value.let { v ->
                    task(v.task);graph(required(WorkFamily.PROFILES,v.profile).payload is WProfilesPayload)
                    val e=v.execution;val i=v.intendedExecution
                    graph(e.executionId==i.executionId && e.nativeHostId==i.nativeHostId && e.nativeSessionId==i.nativeSessionId && e.workspaceId==v.workspaceId)
                    v.reviewSubjects.forEach { subject ->
                        graph(subject.identity.family==WorkFamily.RESULTS)
                        graph((resolve(subject).payload as WResultsPayload).value.producer.executionId!=e.executionId)
                    }
                }
                is WLoadedViewsPayload -> { val v=p.value;val b=(bind(v.assignment).payload as WAssignmentBindingsPayload).value
                    graph(b.task==v.task && b.manifest==v.manifest)
                    v.previous?.let { val old=(required(WorkFamily.LOADED_VIEWS,it).payload as WLoadedViewsPayload).value;graph(old.assignment==v.assignment && old.manifest==v.manifest && old.task==v.task) } }
                is WMemoryPayload -> { val v=p.value;graph((bind(v.assignment).payload as WAssignmentBindingsPayload).value.task==v.task)
                    val cut=(required(WorkFamily.SOURCE_CUTS,WorkKey.Id(projectId,v.sourceCutId)).payload as WSourceCutsPayload).value
                    graph(cut.assignment==v.assignment && cut.task==v.task)
                    v.previous?.let { val old=(required(WorkFamily.MEMORY,it).payload as WMemoryPayload).value;graph(old.assignment==v.assignment && old.task==v.task) } }
                is WSourceCutsPayload -> { val v=p.value;val b=(bind(v.assignment).payload as WAssignmentBindingsPayload).value
                    graph(b.task==v.task && b.profile==v.profile && b.execution.nativeHostId==v.nativeHostId && b.execution.nativeSessionId==v.nativeSessionId) }
                is WCheckpointsPayload -> { val v=p.value;val b=(bind(v.assignment).payload as WAssignmentBindingsPayload).value
                    graph(b.task==v.task && b.manifest==v.manifest)
                    val m=(required(WorkFamily.MEMORY,v.memory).payload as WMemoryPayload).value
                    graph(m.assignment==v.assignment && m.task==v.task && m.sourceCutId==v.sourceCutId)
                    val view=(required(WorkFamily.LOADED_VIEWS,v.loadedView).payload as WLoadedViewsPayload).value
                    graph(view.assignment==v.assignment && view.task==v.task && view.manifest==v.manifest)
                    val plan=(required(WorkFamily.PLANS,v.plan).payload as WPlansPayload).value
                    graph(plan.assignment==v.assignment && plan.task==v.task)
                    v.notification?.let { required(WorkFamily.DELIVERIES,it) }
                    try { checkpointLineage(v) }catch(_: WorkFault) { refuse(CodecProblem.INVALID_GRAPH) }
                }
                is WResultsPayload -> { val v=p.value;val b=(bind(v.assignment).payload as WAssignmentBindingsPayload).value
                    graph(v.task==b.task && v.manifest==b.manifest && v.producer==b.execution && b.intendedExecution.template=="executor-v1")
                    graph(v.retrospective.identity.family==WorkFamily.RETROSPECTIVES)
                    val retro=(resolve(v.retrospective).payload as WRetrospectivesPayload).value
                    graph(retro.assignment==v.assignment && retro.task==v.task)
                    graph(v.claimedCriteria.all { selected -> (task(v.task).payload as WTaskRevisionsPayload).value.criteria.any { it.id==selected } }) }
                is WVerificationReportsPayload -> { val v=p.value;val b=(bind(v.assignment).payload as WAssignmentBindingsPayload).value
                    graph(v.task==b.task && v.reviewer==b.execution && b.intendedExecution.template=="reviewer-v1" && v.subjects==b.reviewSubjects)
                    val plan=(required(WorkFamily.VERIFICATION_PLANS,v.plan).payload as WVerificationPlansPayload).value
                    graph(v.gateIds.all { gate ->plan.gates.any { it.id==gate } }) }
                is WVerificationPlansPayload -> { val v=p.value;val t=(task(v.task).payload as WTaskRevisionsPayload).value
                    graph(v.gates.all { gate->gate.criterionIds.all { selected ->v.criteria.any { it.id==selected } } })
                    try { rootedVerificationPlan(v.task,t,r.identity.key as WorkKey.Revision,
                        { (required(WorkFamily.VERIFICATION_PLANS,it).payload as WVerificationPlansPayload).value },approvedPlanChanges,budget.depth) }
                    catch(_: WorkFault) { refuse(CodecProblem.INVALID_GRAPH) } }
                is WRetrospectivesPayload -> { graph((bind(p.value.assignment).payload as WAssignmentBindingsPayload).value.task==p.value.task)
                    p.value.result?.let { try { ownHandoffOutcome(p.value.task,p.value.assignment,it) }catch(_: WorkFault) { refuse(CodecProblem.INVALID_GRAPH) } } }
                is WRecoveriesPayload -> { required(WorkFamily.ASSIGNMENTS,WorkKey.Id(projectId,p.value.assignment.assignmentId)) }
                is WNativeIntentsPayload -> { required(WorkFamily.ASSIGNMENTS,WorkKey.Id(projectId,p.value.assignment.assignmentId)) }
                is WClaimsPayload -> p.value.let { c ->
                    when(val holder=c.holder) {
                        is WClaimsHolderVariant0Value -> {
                            val a=(required(WorkFamily.ASSIGNMENTS,WorkKey.Id(projectId,holder.value.subject.assignmentId)).payload as WAssignmentsPayload).value
                            graph(holder.value.subject.generation<=a.executionGeneration)
                            if(c.state=="held")graph(holder.value.subject.generation==a.executionGeneration && c.workspaceId==a.workspaceId && r.identity.key in a.requiredResources)
                        }
                        is WClaimsHolderVariant1Value -> {
                            val i=(required(WorkFamily.INTEGRATION_INTENTS,WorkKey.Id(projectId,holder.value.integrationIntentId)).payload as WIntegrationIntentsPayload).value
                            graph(i.state in setOf("prepared","unknown") && r.identity.key in i.requiredResources)
                        }
                        null -> Unit
                    }
                    c.incompatible.forEach { incompatible ->
                        val other=records[WorkRecordIdentity(WorkFamily.CLAIMS,incompatible)]?.payload as? WClaimsPayload
                        if(c.state!="free" && other!=null)graph(other.value.state=="free" || other.value.holder==c.holder)
                    }
                }
                is WDeliveriesPayload -> { bind(p.value.assignment);graph((required(WorkFamily.SOURCE_CUTS,WorkKey.Id(projectId,p.value.sourceCutId)).payload as WSourceCutsPayload).value.assignment==p.value.assignment);task(p.value.destinationTask) }
                is WApplicabilityPayload -> p.value.previous?.let { required(WorkFamily.APPLICABILITY,it) }
                is WControllerContinuationsPayload -> { val v=p.value;graph(required(WorkFamily.PROFILES,v.profile).payload is WProfilesPayload)
                    val key=r.identity.key as WorkKey.ControllerContinuation
                    v.previous?.let { graph(it.key.controllerId==key.controllerId);val old=required(WorkFamily.CONTROLLER_CONTINUATIONS,it.key);graph(WorkCodec().digest(old)==it.digest) }
                    v.frontier.forEach { item ->task(item.task);item.assignments.forEach { a ->required(WorkFamily.ASSIGNMENTS,WorkKey.Id(projectId,a.assignmentId)) } }
                    val frontier=v.frontier.map { it.task }.toSet();v.entries.forEach { graph(it.tasks.all(frontier::contains)) }
                }
                is WAcceptancesPayload -> { val v=p.value;val result=(resolve(v.result).payload as WResultsPayload).value
                    graph(v.task==result.task && v.acceptedBy!=result.producer.principalId)
                    try { val plan=rootedVerificationPlan(v.task,(task(v.task).payload as WTaskRevisionsPayload).value,v.verificationPlan,
                        { (required(WorkFamily.VERIFICATION_PLANS,it).payload as WVerificationPlansPayload).value },approvedPlanChanges,budget.depth)
                        acceptanceChecks(v,plan) }
                    catch(_: WorkFault) { refuse(CodecProblem.INVALID_GRAPH) }
                    graph((resolve(v.applicability).payload as WEvidenceApplicabilityPayload).value.result==v.result)
                    v.reports.forEach { graph(v.result in (resolve(it).payload as WVerificationReportsPayload).value.subjects) }
                    v.dispositions.forEach { graph((resolve(it).payload as WDispositionsPayload).value.result==v.result) } }
                is WDispositionsPayload -> { val v=p.value;val report=(resolve(v.report).payload as WVerificationReportsPayload).value
                    graph(v.result in report.subjects && report.findings.any { it.id==v.findingId })
                    if(v.decision=="owner-exception")graph(v.ownerDecision!=null)
                    if(v.decision=="fixed-by-result")graph(v.replacementResult!=null && v.replacementResult!=v.result) }
                is WIntegrationIntentsPayload -> { val v=p.value;val a=(resolve(v.acceptance).payload as WAcceptancesPayload).value
                    graph(v.result==a.result && v.targetId in a.permittedTargets)
                    graph((v.state=="confirmed")== (v.observation!=null))
                    v.observation?.let { observation ->val o=(resolve(observation).payload as WIntegrationsPayload).value
                        graph(o.intent==r.identity.key && o.intentGeneration==v.generation && o.result==v.result) } }
                is WIntegrationsPayload -> { val v=p.value;val i=(required(WorkFamily.INTEGRATION_INTENTS,v.intent).payload as WIntegrationIntentsPayload).value
                    graph(i.state=="confirmed" && i.result==v.result && i.generation==v.intentGeneration && i.observation?.identity==r.identity)
                    graph(v.resultingSnapshot.repositoryId==i.targetRepositoryId)
                    graph((resolve(v.applicability).payload as WEvidenceApplicabilityPayload).value.result==v.result) }
                else -> Unit
            }
        }
        // Dependency readiness cycles are independent of intentional task/verification-plan back-links.
        val visiting=HashSet<WorkKey.TaskRevision>();val done=HashSet<WorkKey.TaskRevision>()
        fun visit(k: WorkKey.TaskRevision,depth: Int) {
            if(depth>budget.depth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            if(k in done)return;graph(visiting.add(k))
            task(k).payload.let { (it as WTaskRevisionsPayload).value.dependencies.forEach { d->visit(d.producer,depth+1) } }
            visiting.remove(k);done.add(k)
        }
        current=null
        checked.filter { it.identity.family==WorkFamily.TASK_REVISIONS }.forEach { visit(it.identity.key as WorkKey.TaskRevision,0) }
        val immutableVisiting=HashSet<WorkRecordIdentity>();val immutableDone=HashSet<WorkRecordIdentity>()
        fun immutable(k: WorkRecordIdentity,depth: Int) {
            if(depth>budget.depth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            if(k in immutableDone)return;graph(immutableVisiting.add(k))
            val r=records.getValue(k)
            val refs=workLinks(r.payload).records.map { it.identity }.toMutableList()
            val previous=when(val p=r.payload) {
                is WLoadedViewsPayload -> p.value.previous?.let { WorkRecordIdentity(WorkFamily.LOADED_VIEWS,it) }
                is WMemoryPayload -> p.value.previous?.let { WorkRecordIdentity(WorkFamily.MEMORY,it) }
                is WApplicabilityPayload -> p.value.previous?.let { WorkRecordIdentity(WorkFamily.APPLICABILITY,it) }
                is WVerificationPlansPayload -> p.value.previous?.let { WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,it) }
                is WControllerContinuationsPayload -> p.value.previous?.let { WorkRecordIdentity(WorkFamily.CONTROLLER_CONTINUATIONS,it.key) }
                else -> null
            }
            previous?.let(refs::add)
            refs.forEach { edge();immutable(it,depth+1) }
            immutableVisiting.remove(k);immutableDone.add(k)
        }
        checked.filter { !it.identity.family.mutable }.forEach { immutable(it.identity,0) }
        if(selected==null) {
            val live=records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }
                .filter { it.state !in setOf("completed","replaced") }
            graph(live.map { it.workspaceId }.distinct().size==live.size)
            val executors=live.filter { it.intendedExecution.template=="executor-v1" }
            graph(executors.map { it.task.taskId }.distinct().size==executors.size)
        }
        val reverse=LinkedHashMap(reverseDependencies)
        discovered.forEach { (from,targets) ->
            dependencies[from].orEmpty().forEach { target ->reverse[target]=reverse[target].orEmpty()-from }
            targets.forEach { target ->reverse[target]=reverse[target].orEmpty()+from }
        }
        reverseDependencies=Collections.unmodifiableMap(reverse)
        dependencies=Collections.unmodifiableMap(dependencies.filterKeys { it !in discovered }+
            discovered.mapValues { Collections.unmodifiableSet(it.value) })
        intrinsicEdgeCosts=Collections.unmodifiableMap(intrinsicEdgeCosts+costs)
        immutableEdgeCosts=Collections.unmodifiableMap(immutableEdgeCosts+immutableCosts)
        if(fullRestoreEdges>budget.edges)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
        if(selected==null)graph(fullRestoreEdges==edges.toLong())
        try { validateModel2State(this) } catch(_: WorkFault) { refuse(CodecProblem.INVALID_GRAPH) }
    }
}

/** Concrete facts only. AUTH2 checks current credential/grants and selects permitted closure. */
internal data class WorkAssignmentProjection(
    val key: WorkKey.Id,val recordGeneration: Long,val executionGeneration: Long,val state: String,
    val task: WorkKey.TaskRevision,val manifest: WorkReference,val profile: WorkKey.Profile,
    val intendedExecution: WExecutionIntent,val delegationId: UUID?,val execution: WExecution?,
    val reviewSubjects: List<WorkReference>,val historicalBindings: Map<Long,WAssignmentBindings>,
    val ownResults: List<WorkReference>,val ownReports: List<WorkReference>,
    val directBodies: Map<WorkRecordIdentity,List<WorkBodyReference>>,
)
internal fun WorkState.assignmentProjection(id: UUID): WorkAssignmentProjection {
    val a=assignment(id)
    val bindings=records.values.filter { it.identity.family==WorkFamily.ASSIGNMENT_BINDINGS && (it.identity.key as WorkKey.AssignmentBinding).assignmentId==id }
        .associate { (it.identity.key as WorkKey.AssignmentBinding).generation to (it.payload as WAssignmentBindingsPayload).value }
    val own=records.values.filter { r -> when(val p=r.payload) {
        is WResultsPayload -> p.value.assignment.assignmentId==id
        is WVerificationReportsPayload -> p.value.assignment.assignmentId==id
        else -> false
    } }
    return WorkAssignmentProjection(WorkKey.Id(projectId,id),a.generation,a.executionGeneration,a.state,a.task,a.manifest,a.profile,
        a.intendedExecution,a.delegationId,a.execution,a.reviewSubjects,bindings,
        own.filter { it.identity.family==WorkFamily.RESULTS }.map { reference(it.identity.family,it.identity.key) },
        own.filter { it.identity.family==WorkFamily.VERIFICATION_REPORTS }.map { reference(it.identity.family,it.identity.key) },
        own.associate { it.identity to workLinks(it.payload).bodies })
}
