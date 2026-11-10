// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import java.util.UUID

/** Actual CORE-derived authority facts. This is never another persisted work registry. */
internal fun h1WorkProjection(basis: GitOid, projects: Map<UUID,WorkState>): AuthWorkProjection {
    val facts=ArrayList<AssignmentAuthorityFacts>()
    projects.forEach { (project,work) ->
        integrity(work.projectId==project)
        // H1 AUTH2 projection is frozen for native model2 assignments. Model3 mechanical
        // controller authority/author closure is not a native delegation and cannot be silently
        // skipped, cast, or published through this legacy projection.
        integrity(work.modelVersion==2)
        work.records.values.filter { it.identity.family==WorkFamily.ASSIGNMENTS }.forEach { record ->
            val assignmentId=(record.identity.key as WorkKey.Id).id
            val current=work.assignmentProjection(assignmentId)
            val a=work.assignment(assignmentId)
            val generations=current.historicalBindings.keys+current.executionGeneration
            generations.forEach { generation ->
                val old=current.historicalBindings[generation]
                val historical=generation!=current.executionGeneration
                val task=if(historical) old!!.task else a.task
                val manifest=if(historical) old!!.manifest else a.manifest
                val profile=if(historical) old!!.profile else a.profile
                val intent=if(historical) old!!.intendedExecution else a.intendedExecution
                val subjects=if(historical) old!!.reviewSubjects else a.reviewSubjects
                val resources=if(historical) old!!.resources else a.requiredResources
                val execution=if(historical) old!!.execution else a.execution
                val subject=WAssignmentSubject(assignmentId,generation)
                val prepare=work.records.values.singleOrNull {
                    (it.payload as? WNativeIntentsPayload)?.value?.let { n -> n.assignment==subject && n.operation=="prepare" }==true
                } ?: fail(StoreProblem.INTEGRITY)
                val template=DelegationTemplate.entries.singleOrNull { it.tag==intent.template } ?: fail(StoreProblem.INTEGRITY)
                val producers=subjects.associateWith { ref ->
                    val result=(work.resolve(ref).payload as? WResultsPayload)?.value ?: fail(StoreProblem.INTEGRITY)
                    setOf(result.producer.executionId)
                }
                facts.add(AssignmentAuthorityFacts(project,assignmentId,task.taskId,current.recordGeneration,generation,
                    intent.controllerId,intent.executionId,template,subjects,
                    if(historical) AssignmentPhase.REPLACED else AssignmentPhase.entries.singleOrNull { it.name.lowercase()==current.state } ?: fail(StoreProblem.INTEGRITY),
                    if(historical) old!!.delegationId else a.delegationId,
                    work.reference(WorkFamily.TASK_REVISIONS,task),manifest,work.reference(WorkFamily.PROFILES,profile),
                    intent.nativeHostId,intent.nativeSessionId,if(historical)old!!.workspaceId else a.workspaceId,
                    execution?.let { BoundExecutionFacts(it.principalId,it.credentialId,it.executionId,it.nativeHostId,it.nativeSessionId,it.workspaceId) },
                    WorkMutableSelector(prepare.identity,recordGeneration(prepare.payload)!!),
                    resources.map { key -> val claim=work.record(WorkFamily.CLAIMS,key); WorkMutableSelector(claim.identity,recordGeneration(claim.payload)!!) },
                    old?.let { work.reference(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,assignmentId,generation)) },producers,
                    if(a.state=="replaced" && old==null && execution==null && a.delegationId!=null)work.records.values.singleOrNull { r ->
                        (r.payload as? WAssignmentReplacementsPayload)?.value?.let { it.oldRecord.key==WorkKey.Id(project,assignmentId) &&
                            it.oldRecord.payload.state=="reserved" && it.oldRecord.payload.executionGeneration==generation &&
                            it.oldRecord.payload.binding==null && it.oldRecord.payload.execution==null && it.oldRecord.payload.delegationId==a.delegationId }==true
                    }?.let { r ->RetiredReservationProof(WorkReference(r.identity,WorkCodec().digest(r)),
                        (r.payload as WAssignmentReplacementsPayload).value.oldRecord.digest,
                        WorkCodec().digest(work.record(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,assignmentId)))) } else null))
                if(facts.size>8192)fail(StoreProblem.UNAVAILABLE)
            }
        }
    }
    return AuthWorkProjection(headBasis(basis),facts)
}
internal fun headBasis(oid: GitOid)="${oid.algorithm}:${oid.hex}"
private fun workEnrollment(authority: Auth2State,projection: AuthWorkProjection) {
    projection.assignments.values.forEach { facts ->
        integrity(authority.controllers[facts.controllerId]?.projectId==facts.projectId)
        facts.delegationId?.let { id ->
            val delegation=authority.delegations[id] ?: fail(StoreProblem.INTEGRITY)
            integrity(facts.matches(delegation.binding))
            if(facts.boundExecution!=null)integrity(authority.boundIdentityMatches(facts,delegation))
        }
    }
}
internal data class H1EvidenceAttribution(val principalId: UUID,val delegationId: UUID,val reference: WorkReference)
internal fun evidenceAttribution(authority: Auth2State,r: Receipt): H1EvidenceAttribution? {
    val outcome=r.outcome as? LogicalOutcome.Success ?: return null
    if(r.identity.operation!="record_evidence")return null
    val credential=authority.credentials[r.authenticatedByCredentialId] ?: fail(StoreProblem.INTEGRITY)
    val scope=credential.scope as? CredentialScope.Assignment ?: fail(StoreProblem.INTEGRITY)
    integrity(credential.credential.principalId==r.identity.principalId && credential.credential.projectId==r.identity.projectId)
    val result=outcome.result.fields("key","generation","digest")
    val key=identityCodec.decodeKey(WorkFamily.EVIDENCE,StrictJson.encode(result.getValue("key")))
    return H1EvidenceAttribution(r.identity.principalId,scope.delegationId,
        WorkReference(WorkRecordIdentity(WorkFamily.EVIDENCE,key),digest(result.getValue("digest"))))
}
/** Verified retained core authority, independent of today's grants/withdrawal eligibility. The core
 * graph binds direct traces to original successful resolutions and continuity to exact sources. */
internal fun historicalCoreApprovals(snapshot: WorkflowSnapshot,maximum: Int=16384): Set<KnowledgeReference> {
    val approved=snapshot.approvals.keys.toMutableSet()
    val next=snapshot.continuity.entries.groupBy({ it.value.source },{ it.key })
    val queue=ArrayDeque(approved);var edges=0
    while(queue.isNotEmpty())next[queue.removeFirst()].orEmpty().forEach { target ->
        if(++edges>maximum)fail(StoreProblem.UNAVAILABLE)
        if(approved.add(target))queue.add(target)
    }
    if(approved.size>maximum)fail(StoreProblem.UNAVAILABLE)
    return frozenSet(approved)
}
/** Exact core manifest identity, not a mutable/latest selection or an initial loaded-file allowlist.
 * Additional declared-scope reads require a separately validated scope derivation at APP's read
 * boundary; this helper never treats project READ as that derivation. */
internal fun pinnedKnowledge(snapshot: WorkflowSnapshot,base: WBase): Set<KnowledgeReference> {
    val manifest=snapshot.manifests[base.knowledgeManifestId] ?: fail(StoreProblem.INTEGRITY)
    val r=CoreRecordCodec.records(snapshot).single { it.family=="manifests" && uuid(it.key)==manifest.id }
    val bytes=StrictJson.encode(CoreRecordCodec.envelope(r.family,r.key,r.payload()))
    integrity(Digest.of(bytes)==base.knowledgeManifestDigest)
    return (manifest.clauses+manifest.contexts+manifest.dependencies).toSet()
}

/** Shared consuming cross-namespace budget. Normal candidates supply only changed records;
 * immutable core counterparts and all unchanged work rows were validated at H. */
private fun validateWorkCore(snapshot: WorkflowSnapshot,records: Sequence<WorkRecord>,limits: AppLimits): Map<WorkRecordIdentity,Int> {
    val catalog=RevisionCatalog(snapshot.revisions);var edges=0
    val costs=LinkedHashMap<WorkRecordIdentity,Int>()
    fun charge() { if(++edges>limits.affectedEdges)fail(StoreProblem.UNAVAILABLE) }
    records.forEach { record ->
        val start=edges
        val project=record.identity.key.projectId
        workRecordFacts(record).knowledge.forEach { reference ->
            charge()
            integrity(reference.revision.document.projectId==project)
            integrity(when(reference) { is ClauseReference -> catalog.read(reference) is ReadResult.Found;is ContextReference -> catalog.read(reference) is ReadResult.Found })
        }
        val base=when(val p=record.payload) {
            is WTaskRevisionsPayload -> p.value.base
            is WInputManifestsPayload -> p.value.base
            else -> null
        }
        if(base!=null) {
            charge()
            integrity(snapshot.manifests[base.knowledgeManifestId]?.projectId==project)
            pinnedKnowledge(snapshot,base).forEach { charge();integrity(it.revision.document.projectId==project) }
        }
        costs[record.identity]=edges-start
    }
    return frozenMap(costs)
}

/** Validated current-H state. Construction uses actual codecs/graphs/policy, never caller flags. */
internal class H1BoundState private constructor(val storeId: UUID,val basis: GitOid,val core: ApprovalWorkflow,
    val authority: Auth2State,val work: Map<UUID,WorkState>,val projection: AuthWorkProjection,val entryBytes: Long,
    val evidence: Map<WorkRecordIdentity,H1EvidenceAttribution>,private val coreLinkCosts: Map<WorkRecordIdentity,Int>) {
    companion object {
        /** Only a real reducer candidate whose exact changed bytes have been checked against C may
         * reach this seam. Reuses validated unchanged core/work/body objects; no ordinary body scan.
         * Validating C does not establish that C was published. */
        internal fun validatedCandidate(candidate: H1JointCandidate,basis: GitOid,entryBytes: Long,limits: AppLimits,receipt: Receipt): H1BoundState {
            if(entryBytes !in 1..limits.entryBytes)fail(StoreProblem.UNAVAILABLE)
            val projection=h1WorkProjection(basis,candidate.work)
            val snapshot=candidate.core.snapshot(limits.coreRecords)
            val changed=candidate.evaluation.changes.asSequence().filter { it.key.namespace=="work" && it.key.family!="bodies" }.map { change ->
                val family=H1Catalog.family(change.key.family)
                val key=identityCodec.decodeKey(family,StrictJson.encode(change.key.key))
                candidate.work.getValue(key.projectId).record(family,key)
            }
            val coreLinkCosts=frozenMap(candidate.before.coreLinkCosts+validateWorkCore(snapshot,changed,limits))
            val authority=Auth2State.checked(candidate.before.storeId,headBasis(basis),candidate.authority.records(),
                corePolicy(candidate.core.snapshot(limits.coreRecords)),projection)
            workEnrollment(authority,projection)
            if(candidate.work.values.sumOf { it.records.size.toLong() }>8192)fail(StoreProblem.UNAVAILABLE)
            if(candidate.work.values.sumOf { it.fullRestoreEdges }+coreLinkCosts.values.sumOf { it.toLong() }>limits.affectedEdges)fail(StoreProblem.UNAVAILABLE)
            val bodies=HashSet<Digest>()
            candidate.work.values.forEach { state -> state.bodies.keys.forEach { if(bodies.add(it) && bodies.size>8192)fail(StoreProblem.UNAVAILABLE) } }
            val evidence=LinkedHashMap(candidate.before.evidence)
            evidenceAttribution(authority,receipt)?.let { integrity(evidence.put(it.reference.identity,it)==null) }
            return H1BoundState(candidate.before.storeId,basis,candidate.core,authority,candidate.work,projection,entryBytes,frozenMap(evidence),coreLinkCosts)
        }
        fun checked(store: UUID,basis: GitOid,core: ApprovalWorkflow,auth: Collection<Auth2Record>,work: Map<UUID,WorkState>,
            limits: AppLimits=AppLimits()): H1BoundState {
            val projection=h1WorkProjection(basis,work)
            val snapshot=core.snapshot(limits.coreRecords)
            val coreLinkCosts=validateWorkCore(snapshot,work.values.asSequence().flatMap { it.records.values.asSequence() },limits)
            val authority=Auth2State.checked(store,headBasis(basis),auth,corePolicy(snapshot),projection)
            workEnrollment(authority,projection)
            integrity(work.keys.all { it in authority.projects })
            fun enrolled(p: UUID,j: UUID)=integrity(Enrollment(p,j) in authority.guards)
            snapshot.proposals.forEach { (key,actor) -> enrolled(actor.principalId,key.document.projectId) }
            snapshot.requests.values.forEach { enrolled(it.proposedBy,it.projectId) }
            snapshot.resolutions.forEach { (id,r) -> enrolled(r.actor.principalId,snapshot.requests.getValue(id).projectId) }
            snapshot.assessments.values.forEach { enrolled(it.actor.principalId,it.assessment.projectId) }
            snapshot.manifests.values.forEach { enrolled(it.createdBy,it.projectId) }
            val codec=Auth2RecordCodec(); var bytes=0L
            fun add(n: Long) { if(n>limits.entryBytes-bytes)fail(StoreProblem.UNAVAILABLE);bytes+=n }
            CoreSnapshotCodec().export(core) { add(it.bytes().size.toLong()) }
            add(codec.header().size.toLong());auth.forEach { add(codec.encode(it).size.toLong()) }
            add(StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2))).size.toLong())
            // WorkState's per-project count includes bodies. Charge shared content only once globally.
            val bodies=HashSet<Digest>();var records=0L
            work.values.forEach { s ->
                records+=s.records.size; if(records>8192)fail(StoreProblem.UNAVAILABLE)
                add(s.encodedByteCount-s.bodies.values.sumOf { it.sizeBytes.toLong() })
                s.bodies.forEach { (digest,body) -> if(bodies.add(digest))add(body.sizeBytes.toLong()) }
            }
            if(bodies.size>8192 || auth.size>limits.authRecords)fail(StoreProblem.UNAVAILABLE)
            if(work.values.sumOf { it.fullRestoreEdges }+coreLinkCosts.values.sumOf { it.toLong() }>limits.affectedEdges)fail(StoreProblem.UNAVAILABLE)
            return H1BoundState(store,basis,core,authority,frozenMap(work),projection,bytes,emptyMap(),coreLinkCosts)
        }
        /** Full cold restore only. Ordinary publication must carry forward its validated candidate;
         * do not invoke this loader for every command or rescan historical bodies. */
        fun restore(store: UUID,snapshot: StoreSnapshot,limits: AppLimits=AppLimits()): H1BoundState {
            var consumed=0L
            fun read(path: String): ByteArray {
                val bytes=snapshot.file(path) ?: fail(StoreProblem.INTEGRITY)
                if(bytes.size>limits.entryBytes-consumed)fail(StoreProblem.UNAVAILABLE)
                consumed+=bytes.size;return bytes
            }
            val core=CoreSnapshotCodec(budget=RestoreBudget(records=limits.coreRecords,depth=limits.depth)).restore(
                (listOf("core/format.json")+snapshot.entries.keys.filter { it.startsWith("core/") && it!="core/format.json" }.sorted()).asSequence().map { CoreEntry(it,read(it)) })
            val authCodec=Auth2RecordCodec();authCodec.checkHeader(read("auth/format.json"))
            val auth=ArrayList<Auth2Record>()
            snapshot.entries.keys.filter { it.startsWith("auth/") && it!="auth/format.json" }.sorted().forEach { path ->
                if(auth.size>=limits.authRecords)fail(StoreProblem.UNAVAILABLE);auth.add(authCodec.decode(read(path),path))
            }
            val workHeader=StrictJson.decode(read("work/format.json")).fields("formatVersion","modelVersion")
            version(workHeader.getValue("formatVersion"));integrity(workHeader.getValue("modelVersion").long()==2L)
            val records=ArrayList<WorkRecord>();val bodies=LinkedHashMap<Digest,Utf8Content>();val codec=WorkCodec()
            snapshot.entries.keys.filter { it.startsWith("work/") && it!="work/format.json" }.sorted().forEach { path ->
                val bytes=read(path)
                if(path.startsWith("work/bodies/")) {
                    if(bodies.size>=8192)fail(StoreProblem.UNAVAILABLE)
                    val body=Utf8Content.fromBytes(bytes);integrity(path=="work/bodies/${body.digest.hex.take(2)}/${body.digest.hex}.txt")
                    integrity(bodies.put(body.digest,body)==null)
                } else {
                    if(records.size>=8192)fail(StoreProblem.UNAVAILABLE)
                    val r=codec.decodeRecord(bytes,true);integrity(path==identityCodec.recordPath(r.identity))
                    integrity(bytes.contentEquals(codec.encodeRecord(r,true)));records.add(r)
                }
            }
            val referenced=records.flatMap { workLinks(it.payload).bodies }.map { it.digest }.toSet()
            integrity(bodies.keys.containsAll(referenced))
            // Work bodies outlive mutable pointers. Origin/attachment closure is checked at original
            // H->C by the publisher; current absence of a ref must not erase accepted history. Bodies
            // are namespace-wide content, NOT project grants. Reads still need an exact referring record.
            val grouped=records.groupBy { it.identity.key.projectId }
            val enrolledProjects=auth.filterIsInstance<Auth2Record.Project>().map { it.binding.projectId }.toSet()
            integrity(grouped.keys.all { it in enrolledProjects })
            val approvals=historicalCoreApprovals(core.snapshot(limits.coreRecords),limits.affectedEdges)
            val projects=enrolledProjects.associateWith { project ->
                WorkState.restore(project,grouped[project].orEmpty(),bodies.values,WorkGraphBudget(aggregateBytes=limits.entryBytes),
                    approvedPlanChanges=approvals.filter { it.revision.document.projectId==project }.toSet())
            }
            val valid=checked(store,snapshot.oid,core,auth,projects,limits)
            val evidence=LinkedHashMap<WorkRecordIdentity,H1EvidenceAttribution>();var receipts=0
            snapshot.entries.keys.filter { it.startsWith("commands/receipts/") }.sorted().forEach { path ->
                if(++receipts>4096)fail(StoreProblem.UNAVAILABLE)
                val receipt=JournalCodec.decode(read(path),path)
                integrity(receipt.storeId==store && receipt.journalVersion==3)
                evidenceAttribution(valid.authority,receipt)?.let { attribution ->
                    projects[attribution.reference.identity.key.projectId]?.resolve(attribution.reference) ?: fail(StoreProblem.INTEGRITY)
                    integrity(evidence.put(attribution.reference.identity,attribution)==null)
                }
            }
            return H1BoundState(store,snapshot.oid,core,valid.authority,valid.work,valid.projection,consumed,frozenMap(evidence),valid.coreLinkCosts)
        }
    }
}
/** Derived validated states only. A caller must supply an actual store-captured snapshot, never an
 * arbitrary OID label; cache membership is neither publication proof nor request authorization. */
internal class H1SnapshotCache(private val store: UUID,private val limits: AppLimits=AppLimits()) {
    private val states=LinkedHashMap<GitOid,H1BoundState>(4,0.75f,true)
    fun load(snapshot: StoreSnapshot): H1BoundState = states[snapshot.oid] ?: H1BoundState.restore(store,snapshot,limits).also { remember(it) }
    internal fun remember(state: H1BoundState) {
        integrity(state.storeId==store)
        states[state.basis]=state
        while(states.size>limits.cachedStates)states.remove(states.keys.first())
    }
    internal fun candidate(candidate: H1JointCandidate,basis: GitOid,bytes: Long,receipt: Receipt)=
        H1BoundState.validatedCandidate(candidate,basis,bytes,limits,receipt)
}
