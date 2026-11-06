// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.ClauseRead
import dev.writhelm.core.knowledge.ClauseReference
import dev.writhelm.core.knowledge.ContextReference
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.DocumentRevision
import dev.writhelm.core.knowledge.KnowledgeReference
import dev.writhelm.core.knowledge.ReadResult
import dev.writhelm.core.knowledge.RevisionCatalog
import dev.writhelm.core.knowledge.RevisionKey
import dev.writhelm.core.knowledge.Utf8Content
import java.util.UUID

/** Pure immutable transition boundary; no credentials, wire parsing, persistence or UI-origin proof. */
class ApprovalWorkflow private constructor(
    private val catalog: RevisionCatalog,
    private val policy: AccessPolicy,
    proposals: Map<RevisionKey, Actor> = emptyMap(),
    requests: Map<UUID, ApprovalRequest> = emptyMap(),
    resolutions: Map<UUID, ApprovalResolution> = emptyMap(),
    approvals: Map<KnowledgeReference, ApprovalTrace> = emptyMap(),
    continuity: Map<KnowledgeReference, StoredContinuity> = emptyMap(),
    assessments: Map<UUID, RecordedAssessment> = emptyMap(),
    manifests: Map<UUID, InputManifest> = emptyMap(),
    heads: Map<SubjectKey, KnowledgeReference> = emptyMap(),
    generations: Map<SubjectKey, Long> = emptyMap(),
    blocked: Set<KnowledgeReference> = emptySet(),
) {
    private val proposals = frozenMap(proposals)
    private val requests = frozenMap(requests)
    private val resolutions = frozenMap(resolutions)
    private val approvals = frozenMap(approvals)
    private val continuity = frozenMap(continuity)
    private val assessments = frozenMap(assessments)
    private val manifests = frozenMap(manifests)
    private val heads = frozenMap(heads)
    private val generations = frozenMap(generations)
    private val blocked = frozenSet(blocked)

    internal fun snapshot(maxRecords: Int = Int.MAX_VALUE): WorkflowSnapshot {
        val count = catalog.snapshotRevisions().size.toLong() + policy.snapshotGrantCount() + proposals.size + requests.size +
            resolutions.size + approvals.size + continuity.size + assessments.size + manifests.size + heads.size + generations.size + blocked.size
        if (count > maxRecords) throw dev.writhelm.core.codec.CodecFault(dev.writhelm.core.codec.CodecProblem.RESOURCE_UNAVAILABLE)
        return WorkflowSnapshot(catalog.snapshotRevisions(), policy.snapshotGrants(), proposals, requests,
            resolutions, approvals, continuity, assessments, manifests, heads, generations, blocked)
    }

    fun propose(actor: Actor, revision: DocumentRevision): WorkflowResult<Transition<RevisionKey>> {
        val project = revision.key.document.projectId
        if (!policy.allows(actor, project, Capability.PROPOSE)) return failure(WorkflowProblem.FORBIDDEN)
        if (catalog.contextOf(revision.key) is ReadResult.Found) return failure(WorkflowProblem.DUPLICATE_ID)
        return success(next(catalog = catalog.withRevision(revision), proposals = proposals + (revision.key to actor)), revision.key)
    }

    fun request(
        actor: Actor,
        project: UUID,
        id: UUID,
        packet: ReviewPacket,
    ): WorkflowResult<Transition<ApprovalRequest>> {
        if (!policy.allows(actor, project, Capability.PLAN)) return failure(WorkflowProblem.FORBIDDEN)
        if (id in requests) return failure(WorkflowProblem.DUPLICATE_ID)
        validateReferences(project, packet.references)?.let { return failure(it) }
        validateContexts(packet.references, packet.contexts)?.let { return failure(it) }
        if (packet.items.any { it.before != heads[it.target.subject()] }) return failure(WorkflowProblem.STALE_SUBJECT)
        val guards = packet.items.associate { it.target.subject() to generation(it.target) }
        val request = ApprovalRequest(id, project, actor.principalId, packet, guards)
        return success(next(requests = requests + (id to request)), request)
    }

    fun resolve(
        actor: Actor,
        project: UUID,
        requestId: UUID,
        requestDigest: Digest,
        packetDigest: Digest,
        answer: StructuredAnswer,
    ): WorkflowResult<Transition<ApprovalResolution>> {
        if (!policy.allows(actor, project, Capability.APPROVE)) return failure(WorkflowProblem.FORBIDDEN)
        val request = requests[requestId]?.takeIf { it.projectId == project } ?: return failure(WorkflowProblem.NOT_FOUND)
        if (request.digest != requestDigest || request.packet.digest != packetDigest) {
            return failure(WorkflowProblem.DIGEST_MISMATCH)
        }
        resolutions[requestId]?.let {
            return if (it.actor == actor && it.answer == answer) success(this, it, replayed = true)
            else failure(WorkflowProblem.ALREADY_RESOLVED)
        }
        if (answer is StructuredAnswer.ChangesRequired && answer.comments.keys.any { id ->
                request.packet.items.none { it.id == id }
            }) return failure(WorkflowProblem.UNKNOWN_COMMENT_ITEM)

        val targets = request.packet.items.map { it.target }
        if (answer == StructuredAnswer.Approve) {
            if (request.packet.informationGaps.isNotEmpty()) return failure(WorkflowProblem.INCOMPLETE_PACKET)
            if (request.packet.items.any {
                    request.generations[it.target.subject()] != generation(it.target) || it.before != heads[it.target.subject()]
                }) return failure(WorkflowProblem.STALE_SUBJECT)
            if (request.packet.references.any { blockedThroughProvenance(it) }) return failure(WorkflowProblem.BLOCKED_INPUT)
            val needed = request.packet.contexts + request.packet.dependencies
            for (ref in needed.filter { it !in targets }) {
                val authority = authority(ref)
                if (authority is WorkflowResult.Failure) return authority
            }
        }
        val approved = if (answer == StructuredAnswer.Approve) targets else emptyList()
        if (!canAdvanceGenerations(approved.map { generation(it) })) return failure(WorkflowProblem.GENERATION_EXHAUSTED)
        val resolution = ApprovalResolution(requestId, actor, answer, approved)
        val newApprovals = approvals.toMutableMap()
        val newHeads = heads.toMutableMap()
        val newGenerations = generations.toMutableMap()
        approved.forEach {
            newApprovals.putIfAbsent(it, ApprovalTrace(it, requestId, actor.principalId))
            newHeads[it.subject()] = it
            newGenerations[it.subject()] = checkNotNull(checkedNextGeneration(generation(it)))
        }
        return success(
            next(
                resolutions = resolutions + (requestId to resolution), approvals = newApprovals,
                heads = newHeads, generations = newGenerations,
            ),
            resolution,
        )
    }

    fun assess(actor: Actor, assessment: ConsistencyAssessment): WorkflowResult<Transition<RecordedAssessment>> {
        if (!policy.allows(actor, assessment.projectId, Capability.PLAN)) return failure(WorkflowProblem.FORBIDDEN)
        if (assessment.id in assessments) return failure(WorkflowProblem.DUPLICATE_ID)
        validateReferences(assessment.projectId, assessment.references)?.let { return failure(it) }
        validateContexts(assessment.references, assessment.references.filterIsInstance<ContextReference>())?.let {
            return failure(it)
        }
        if (assessment.blocked.any { it !in assessment.references }) return failure(WorkflowProblem.INCOMPLETE_ASSESSMENT)
        for (link in assessment.links) {
            if (link.source !in assessment.references || link.target !in assessment.references) {
                return failure(WorkflowProblem.INCOMPLETE_ASSESSMENT)
            }
            if (link.source == link.target || link.source.subject() != link.target.subject() ||
                link.target in approvals || link.target in continuity || bytes(link.source) != bytes(link.target)
            ) return failure(WorkflowProblem.INVALID_CONTINUITY)
            val original = authority(link.source)
            if (original is WorkflowResult.Failure) return original
            if (link.source is ClauseReference && link.target is ClauseReference &&
                contextBytes(link.source.revision) != contextBytes(link.target.revision)
            ) return failure(WorkflowProblem.CONTEXT_CHANGED)
        }
        val stored = RecordedAssessment(assessment, actor)
        val newLinks = continuity + assessment.links.associate { it.target to StoredContinuity(it.source, assessment.id) }
        return success(
            next(
                assessments = assessments + (assessment.id to stored), continuity = newLinks,
                blocked = blocked + assessment.blocked,
            ),
            stored,
        )
    }

    fun pin(
        actor: Actor,
        project: UUID,
        id: UUID,
        consumerId: UUID,
        clauses: List<ClauseReference>,
        contexts: List<ContextReference>,
        dependencies: List<KnowledgeReference>,
        assessmentId: UUID,
    ): WorkflowResult<Transition<InputManifest>> {
        if (!policy.allows(actor, project, Capability.PLAN)) return failure(WorkflowProblem.FORBIDDEN)
        if (id in manifests) return failure(WorkflowProblem.DUPLICATE_ID)
        val selectedClauses = frozenList(clauses)
        val selectedContexts = frozenList(contexts)
        val selectedDependencies = frozenList(dependencies)
        if (selectedClauses.isEmpty() || selectedClauses.distinct().size != selectedClauses.size ||
            selectedContexts.distinct().size != selectedContexts.size || selectedDependencies.distinct().size != selectedDependencies.size
        ) return failure(WorkflowProblem.CONFLICTING_SELECTION)
        val references = (selectedClauses + selectedContexts + selectedDependencies).distinct()
        validateReferences(project, references)?.let { return failure(it) }
        validateContexts(references, selectedContexts)?.let { return failure(it) }
        if (references.filterIsInstance<ClauseReference>().groupBy { it.subject() }.any { it.value.distinct().size > 1 }) {
            return failure(WorkflowProblem.CONFLICTING_SELECTION)
        }
        if (references.filterIsInstance<ContextReference>().groupBy { it.revision.document }.any { group ->
                group.value.map { bytes(it) }.distinct().size > 1
            }) return failure(WorkflowProblem.UNSUPPORTED_CONTEXT_MIX)
        val assessment = assessments[assessmentId]?.takeIf { it.assessment.projectId == project }
            ?: return failure(WorkflowProblem.INCOMPLETE_ASSESSMENT)
        if (!assessment.assessment.references.containsAll(references)) return failure(WorkflowProblem.INCOMPLETE_ASSESSMENT)
        val provenance = LinkedHashMap<KnowledgeReference, ApprovalTrace>()
        for (ref in references) {
            when (val result = authority(ref)) {
                is WorkflowResult.Failure -> return result
                is WorkflowResult.Success -> provenance[ref] = result.value
            }
        }
        val manifest = InputManifest(
            id, consumerId, project, actor.principalId, selectedClauses, selectedContexts,
            selectedDependencies, assessmentId, provenance,
        )
        return success(next(manifests = manifests + (id to manifest)), manifest)
    }

    fun revokeAccess(actor: Actor, project: UUID, principal: UUID): WorkflowResult<Transition<UUID>> {
        if (!policy.allows(actor, project, Capability.MANAGE)) return failure(WorkflowProblem.FORBIDDEN)
        if (!policy.hasGrant(project, principal)) return failure(WorkflowProblem.NOT_FOUND)
        if (!policy.revocationKeepsAuthority(project, principal)) return failure(WorkflowProblem.LAST_AUTHORITY)
        return success(next(policy = policy.revoke(project, principal)), principal)
    }

    fun readRequest(actor: Actor, project: UUID, id: UUID): WorkflowResult<ApprovalRequest> {
        if (!policy.allows(actor, project, Capability.READ)) return WorkflowResult.Failure(WorkflowProblem.FORBIDDEN)
        return requests[id]?.takeIf { it.projectId == project }?.let { WorkflowResult.Success(it) }
            ?: WorkflowResult.Failure(WorkflowProblem.NOT_FOUND)
    }

    fun readResolution(actor: Actor, project: UUID, id: UUID): WorkflowResult<ApprovalResolution> {
        if (!policy.allows(actor, project, Capability.READ)) return WorkflowResult.Failure(WorkflowProblem.FORBIDDEN)
        if (requests[id]?.projectId != project) return WorkflowResult.Failure(WorkflowProblem.NOT_FOUND)
        return resolutions[id]?.let { WorkflowResult.Success(it) } ?: WorkflowResult.Failure(WorkflowProblem.NOT_FOUND)
    }

    /** Historical record only; does not authorize future execution of these inputs. */
    fun readManifest(actor: Actor, project: UUID, id: UUID): WorkflowResult<InputManifest> {
        if (!policy.allows(actor, project, Capability.READ)) return WorkflowResult.Failure(WorkflowProblem.FORBIDDEN)
        return manifests[id]?.takeIf { it.projectId == project }?.let { WorkflowResult.Success(it) }
            ?: WorkflowResult.Failure(WorkflowProblem.NOT_FOUND)
    }

    fun readClause(actor: Actor, reference: ClauseReference): WorkflowResult<ClauseRead> {
        if (!policy.allows(actor, reference.revision.document.projectId, Capability.READ)) {
            return WorkflowResult.Failure(WorkflowProblem.FORBIDDEN)
        }
        return when (val result = catalog.read(reference)) {
            is ReadResult.Found -> WorkflowResult.Success(result.value)
            is ReadResult.Failed -> WorkflowResult.Failure(WorkflowProblem.INVALID_REFERENCE)
        }
    }

    fun approvalTrace(actor: Actor, reference: KnowledgeReference): WorkflowResult<ApprovalTrace> {
        if (!policy.allows(actor, reference.revision.document.projectId, Capability.READ)) {
            return WorkflowResult.Failure(WorkflowProblem.FORBIDDEN)
        }
        validateReferences(reference.revision.document.projectId, listOf(reference))?.let { return WorkflowResult.Failure(it) }
        return authority(reference)
    }

    /** Internal current validation facts for an already authorized typed write. These disclose no
     * protected content/trace and confer no READ permission. Never substitute them for readClause,
     * approvalTrace or subject-sensitive service authorization; no synthetic owner Actor is needed. */
    internal fun validationSourceAvailable(reference: KnowledgeReference): Boolean =
        validateReferences(reference.revision.document.projectId,listOf(reference))==null && !blockedThroughProvenance(reference)
    internal fun validationDecisionApproved(reference: KnowledgeReference): Boolean =
        validateReferences(reference.revision.document.projectId,listOf(reference))==null && authority(reference) is WorkflowResult.Success

    private fun authority(ref: KnowledgeReference, seen: Set<KnowledgeReference> = emptySet()): WorkflowResult<ApprovalTrace> {
        if (ref in blocked) return WorkflowResult.Failure(WorkflowProblem.BLOCKED_INPUT)
        if (ref in seen) return WorkflowResult.Failure(WorkflowProblem.INVALID_CONTINUITY)
        val visited = seen + ref
        if (ref is ClauseReference) {
            requiredContext(ref)?.let {
                val contextAuthority = authority(it, visited)
                if (contextAuthority is WorkflowResult.Failure) return contextAuthority
            }
        }
        approvals[ref]?.let { return WorkflowResult.Success(it) }
        val link = continuity[ref] ?: return WorkflowResult.Failure(WorkflowProblem.UNAPPROVED_INPUT)
        return when (val result = authority(link.source, visited)) {
            is WorkflowResult.Failure -> result
            is WorkflowResult.Success -> WorkflowResult.Success(
                ApprovalTrace(
                    result.value.origin, result.value.requestId, result.value.approvedBy,
                    result.value.assessmentIds + link.assessmentId,
                )
            )
        }
    }

    private fun blockedThroughProvenance(ref: KnowledgeReference, seen: Set<KnowledgeReference> = emptySet()): Boolean {
        if (ref in blocked) return true
        if (ref in seen) return false
        val visited = seen + ref
        if (ref is ClauseReference && requiredContext(ref)?.let { blockedThroughProvenance(it, visited) } == true) return true
        if (ref in approvals) return false
        return continuity[ref]?.let { blockedThroughProvenance(it.source, visited) } ?: false
    }

    private fun validateReferences(project: UUID, refs: List<KnowledgeReference>): WorkflowProblem? {
        if (refs.any { it.revision.document.projectId != project }) return WorkflowProblem.FOREIGN_REFERENCE
        return if (refs.any { !valid(it) }) WorkflowProblem.INVALID_REFERENCE else null
    }

    private fun validateContexts(refs: List<KnowledgeReference>, contexts: List<ContextReference>): WorkflowProblem? =
        if (refs.filterIsInstance<ClauseReference>().mapNotNull { requiredContext(it) }.any { it !in contexts }) {
            WorkflowProblem.MISSING_CONTEXT
        } else null

    private fun requiredContext(ref: ClauseReference): ContextReference? {
        val context = (catalog.contextOf(ref.revision) as ReadResult.Found).value
        return context.takeIf { contextBytes(ref.revision).isNotEmpty() }
    }

    private fun contextBytes(key: RevisionKey): List<Utf8Content> {
        val context = (catalog.contextOf(key) as ReadResult.Found).value
        return bytes(context)
    }

    private fun valid(ref: KnowledgeReference): Boolean = when (ref) {
        is ClauseReference -> catalog.read(ref) is ReadResult.Found
        is ContextReference -> catalog.read(ref) is ReadResult.Found
    }

    private fun bytes(ref: KnowledgeReference): List<Utf8Content> = when (ref) {
        is ClauseReference -> listOf((catalog.read(ref) as ReadResult.Found).value.located.content)
        is ContextReference -> (catalog.read(ref) as ReadResult.Found).value.fragments.map { it.content }
    }

    private fun generation(ref: KnowledgeReference): Long = generations[ref.subject()] ?: 0L

    private fun next(
        catalog: RevisionCatalog = this.catalog,
        policy: AccessPolicy = this.policy,
        proposals: Map<RevisionKey, Actor> = this.proposals,
        requests: Map<UUID, ApprovalRequest> = this.requests,
        resolutions: Map<UUID, ApprovalResolution> = this.resolutions,
        approvals: Map<KnowledgeReference, ApprovalTrace> = this.approvals,
        continuity: Map<KnowledgeReference, StoredContinuity> = this.continuity,
        assessments: Map<UUID, RecordedAssessment> = this.assessments,
        manifests: Map<UUID, InputManifest> = this.manifests,
        heads: Map<SubjectKey, KnowledgeReference> = this.heads,
        generations: Map<SubjectKey, Long> = this.generations,
        blocked: Set<KnowledgeReference> = this.blocked,
    ) = ApprovalWorkflow(catalog, policy, proposals, requests, resolutions, approvals, continuity, assessments, manifests,
        heads, generations, blocked)

    companion object {
        fun start(policy: AccessPolicy): ApprovalWorkflow = ApprovalWorkflow(RevisionCatalog(), policy)

        internal fun restoreValidated(s: WorkflowSnapshot): ApprovalWorkflow = with(s) {
            ApprovalWorkflow(RevisionCatalog(revisions), AccessPolicy(grants), proposals, requests, resolutions, approvals,
                continuity, assessments, manifests, heads, generations, blocked)
        }
    }
}

internal data class StoredContinuity(val source: KnowledgeReference, val assessmentId: UUID)

private fun failure(problem: WorkflowProblem): WorkflowResult.Failure = WorkflowResult.Failure(problem)

private fun <T> success(state: ApprovalWorkflow, value: T, replayed: Boolean = false): WorkflowResult<Transition<T>> =
    WorkflowResult.Success(Transition(state, value, replayed))
