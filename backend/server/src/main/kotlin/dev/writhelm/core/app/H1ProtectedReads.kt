// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*

/** Current-H exact protected helpers. No broad AppReadView/workflow escapes to a caller. */
internal class H1ProtectedReads {
    private val codec=WorkCodec()
    /** A retained hash is not possession. Find an authorized exact parent at original H, never
     * the newly attributed C record. Metadata only; no scan/re-hash of historical body bytes. */
    internal fun mayReuseBodies(base: H1BoundState,c: ScopedCredential,required: Set<WorkBodyReference>): Boolean {
        if(required.isEmpty())return true
        val remaining=required.toMutableSet();val proof=closure(base,c)
        val policy=Auth2Policy(base.authority,base.projection);var edges=0
        base.work[c.credential.projectId]?.records?.values?.forEach { record ->
            if(!model2ControllerRecordAllowed(base,c,record))return@forEach
            val bodies=workLinks(record.payload).bodies
            if(bodies.size>16384-edges)fail(StoreProblem.UNAVAILABLE)
            edges+=bodies.size
            val matches=bodies.filter { it in remaining }.distinct()
            if(matches.isNotEmpty()) {
                val parents=resources(workRecordFacts(record))
                val parent=parents.first()
                matches.forEach { body ->
                    val resource=parents.first { it is ProtectedResource.WorkBody && it.body==body || it is ProtectedResource.MutableWorkBody && it.body==body }
                    val request=DisclosureRequest(c.credential.projectId,"read_work_body",listOf(parent,resource).map { DisclosureUnit(it,ContentApplicability.CURRENT) })
                    if(policy.authorizeHistoricalDisclosure(c.credential.id,request,proof))remaining.remove(body)
                }
            }
        }
        return remaining.isEmpty()
    }
    private fun actor(base: H1BoundState,presented: PresentedCredential,project: java.util.UUID): ScopedCredential {
        val target=base.authority.credentials[presented.credentialId] ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        val c=base.authority.authenticate(presented,target.credential.projectId) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        if(c.credential.projectId!=project)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(!base.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        return c
    }
    fun closure(base: H1BoundState,c: ScopedCredential): DerivedDisclosureClosure {
        val project=c.credential.projectId
        val scope=c.scope as? CredentialScope.Assignment
        if(scope==null)return DerivedDisclosureClosure(headBasis(base.basis),project,null,emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val binding=base.authority.delegations[scope.delegationId]?.binding ?: fail(StoreProblem.DENIED)
        val work=base.work[project] ?: fail(StoreProblem.INTEGRITY)
        val subject=WAssignmentSubject(binding.assignmentId,binding.executionGeneration)
        val p=try { work.subjectProjection(subject) } catch(_: WorkFault) { throw AppAccessFault(AppAccessProblem.FORBIDDEN) }
        val knowledge=base.core.snapshot(8192)
        val exactTask=resources(p.task)
        val taskAndBase=(exactTask+resources(p.manifest)+ProtectedResource.ImmutableWork(work.reference(WorkFamily.PROFILES,p.binding.profile))+
            ProtectedResource.ImmutableWork(work.reference(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,subject.assignmentId,subject.generation)))+
            pinnedKnowledge(knowledge,p.base).map { ProtectedResource.Knowledge(it) }).toMutableSet()
        taskAndBase.addAll((p.manifest.knowledge+p.retainedOwnContinuation.filter { it.identity.family==WorkFamily.LOADED_VIEWS }.flatMap { it.knowledge })
            .map { ProtectedResource.Knowledge(it) })
        (p.task.references+p.manifest.references+p.retainedOwnContinuation.filter { it.identity.family==WorkFamily.LOADED_VIEWS }.flatMap { it.references })
            .distinct().forEach { reference -> taskAndBase.addAll(resources(workRecordFacts(work.resolve(reference)))) }
        if(p.assignment.executionGeneration==subject.generation)taskAndBase.add(ProtectedResource.MutableWork(
            WorkMutableSelector(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,subject.assignmentId)),p.assignment.recordGeneration)))
        val manifest=CoreRecordCodec.records(knowledge).single { it.family=="manifests" && uuid(it.key)==p.base.knowledgeManifestId }
        taskAndBase.add(ProtectedResource.LegacyRecord(project,LegacyReadFamily.MANIFEST,p.base.knowledgeManifestId,
            Digest.of(StrictJson.encode(CoreRecordCodec.envelope(manifest.family,manifest.key,manifest.payload())))))
        // A full revision is allowed only if every byte belongs to the selected exact fragments.
        val selected=pinnedKnowledge(knowledge,p.base)+p.manifest.knowledge+
            p.retainedOwnContinuation.filter { it.identity.family==WorkFamily.LOADED_VIEWS }.flatMap { it.knowledge }
        val selectedRevisions=selected.map { it.revision }.toSet()
        knowledge.revisions.filter { it.key in selectedRevisions }.forEach { revision ->
            val spans=ArrayList<ByteSpan>()
            selected.filterIsInstance<ClauseReference>().filter { it.revision==revision.key && it.revisionDigest==revision.digest }.forEach { ref ->
                revision.structure.clause(ref.clauseId)?.let { spans.add(it.span) }
            }
            if(revision.contextReference() in selected)spans.addAll(revision.structure.context)
            var end=0
            spans.sortedBy { it.start }.forEach { span -> if(span.start<=end)end=maxOf(end,span.endExclusive) }
            if(end==revision.content.sizeBytes)taskAndBase.add(ProtectedResource.Revision(revision.key,revision.digest))
        }
        val ownContinuation=p.retainedOwnContinuation.flatMap(::resources)
        val review=linkedSetOf<ProtectedResource>()
        if(binding.template==DelegationTemplate.REVIEWER) {
            val queue=ArrayDeque(p.reviewSubjects);val seen=HashSet<WorkRecordIdentity>();var edges=0
            val families=setOf(WorkFamily.RESULTS,WorkFamily.EVIDENCE,WorkFamily.TASK_REVISIONS,WorkFamily.INPUT_MANIFESTS,
                WorkFamily.VERIFICATION_PLANS,WorkFamily.RETROSPECTIVES,WorkFamily.APPLICABILITY,WorkFamily.EVIDENCE_APPLICABILITY)
            while(queue.isNotEmpty()) {
                val facts=queue.removeFirst();if(!seen.add(facts.identity))continue
                if(seen.size>4096)fail(StoreProblem.UNAVAILABLE)
                review.addAll(resources(facts));review.addAll(facts.knowledge.map { ProtectedResource.Knowledge(it) })
                facts.references.filter { it.identity.family in families }.forEach { ref ->
                    if(++edges>16384)fail(StoreProblem.UNAVAILABLE)
                    queue.add(workRecordFacts(work.resolve(ref)))
                }
            }
            work.records.values.filter { record ->
                val c=(record.payload as? WConditionsPayload)?.value ?: return@filter false
                val assessor=(c.assessor as? WConditionAssessorVariant1Value)?.value
                assessor?.assignment==subject && assessor.binding==work.reference(WorkFamily.ASSIGNMENT_BINDINGS,
                    WorkKey.AssignmentBinding(project,subject.assignmentId,subject.generation)) &&
                    p.reviewSubjects.any { result ->(work.records[result.identity]?.payload as? WResultsPayload)?.value?.task==c.responsibleTask }
            }.forEach { record ->
                review.addAll(resources(workRecordFacts(record)))
                val conditionRef=work.reference(WorkFamily.CONDITIONS,record.identity.key)
                work.records.values.filter { (it.payload as? WConditionConfirmationsPayload)?.value?.condition==conditionRef }
                    .forEach { review.addAll(resources(workRecordFacts(it))) }
            }
        }
        val owned=(p.retainedOwnResults+p.retainedOwnReports).map { facts ->
            val record=work.records.getValue(facts.identity)
            val execution=when(val payload=record.payload) {
                is WResultsPayload -> payload.value.producer
                is WVerificationReportsPayload -> payload.value.reviewer
                else -> fail(StoreProblem.INTEGRITY)
            }
            OwnedResultFacts(WorkReference(facts.identity,facts.digest),execution.principalId,
                AssignmentSubject(project,subject.assignmentId,subject.generation),execution.executionId,facts.directBodies)
        }
        val ownEvidence=base.evidence.values.filter { it.principalId==binding.principalId && it.delegationId==scope.delegationId }.flatMap {
            resources(workRecordFacts(work.resolve(it.reference)))
        }
        return DerivedDisclosureClosure(headBasis(base.basis),project,binding,exactTask,taskAndBase,ownContinuation,review,owned,ownEvidence)
    }
    internal fun authorizeResources(base: H1BoundState,presented: PresentedCredential,project: java.util.UUID,
        operation: String,resources: Collection<ProtectedResource>): ScopedCredential {
        val c=actor(base,presented,project)
        permit(base,presented,operation,c,resources,closure(base,c));return c
    }
    internal fun authorizeLegacyRecord(base: H1BoundState,presented: PresentedCredential,project: java.util.UUID,
        operation: String,family: LegacyReadFamily,id: java.util.UUID) {
        val c=actor(base,presented,project);val proof=closure(base,c)
        val resource=if(c.scope is CredentialScope.Assignment)
            (proof.taskAndBase+proof.ownContinuation+proof.reviewGroup).filterIsInstance<ProtectedResource.LegacyRecord>()
                .singleOrNull { it.family==family && it.id==id } ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            else ProtectedResource.LegacyRecord(project,family,id,Digest.parse("0".repeat(64)))
        permit(base,presented,operation,c,listOf(resource),proof)
    }
    private fun resources(facts: WorkRecordFacts): List<ProtectedResource> {
        if(facts.identity.family.mutable) {
            val selector=WorkMutableSelector(facts.identity,requireNotNull(facts.generation))
            return listOf(ProtectedResource.MutableWork(selector))+facts.directBodies.map { ProtectedResource.MutableWorkBody(selector,it) }
        }
        val ref=WorkReference(facts.identity,facts.digest)
        return listOf(ProtectedResource.ImmutableWork(ref))+facts.directBodies.map { ProtectedResource.WorkBody(ref,it) }
    }
    private fun permit(base: H1BoundState,presented: PresentedCredential,operation: String,c: ScopedCredential,
        resources: Collection<ProtectedResource>,closure: DerivedDisclosureClosure) {
        val pinned=(c.scope as? CredentialScope.Assignment)?.let { scope ->
            val b=base.authority.delegations.getValue(scope.delegationId).binding
            val subject=base.work.getValue(c.credential.projectId).subjectProjection(WAssignmentSubject(b.assignmentId,b.executionGeneration))
            pinnedKnowledge(base.core.snapshot(8192),subject.base)
        }.orEmpty()
        val units=resources.map { resource ->
            val observation=if(c.scope is CredentialScope.Assignment && resource is ProtectedResource.Knowledge)
                base.core.approvalTrace(Actor(c.credential.principalId),resource.reference) else null
            val applicable=if(resource is ProtectedResource.Knowledge && observation is WorkflowResult.Failure &&
                (resource.reference in pinned || observation.problem==WorkflowProblem.BLOCKED_INPUT))ContentApplicability.BLOCKED else ContentApplicability.CURRENT
            DisclosureUnit(resource,applicable)
        }
        // The snapshot is private local metadata only; no target content is looked up or serialized
        // until exact subject and complete returned closure have passed the current policy.
        if(!Auth2Policy(base.authority,base.projection).authorizeDisclosure(presented,
                DisclosureRequest(c.credential.projectId,operation,units),closure))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
    }
    fun readClause(base: H1BoundState,presented: PresentedCredential,reference: ClauseReference): ClauseRead {
        val c=actor(base,presented,reference.revision.document.projectId)
        permit(base,presented,"read_clause",c,listOf(ProtectedResource.Knowledge(reference)),closure(base,c))
        return when(val result=base.core.readClause(Actor(c.credential.principalId),reference)) {
            is WorkflowResult.Success -> result.value
            is WorkflowResult.Failure -> fail(if(result.problem==WorkflowProblem.FORBIDDEN)StoreProblem.DENIED else StoreProblem.INTEGRITY)
        }
    }
    fun readWork(base: H1BoundState,presented: PresentedCredential,selector: WorkRead.Exact): WorkRecord {
        val c=actor(base,presented,selector.projectId)
        base.work[selector.projectId]?.records?.get(selector.identity)?.let { if(!model2ControllerRecordAllowed(base,c,it))throw AppAccessFault(AppAccessProblem.FORBIDDEN) }
        val resource=if(selector.identity.family.mutable)ProtectedResource.MutableWork(WorkMutableSelector(selector.identity,requireNotNull(selector.generation)))
            else ProtectedResource.ImmutableWork(WorkReference(selector.identity,requireNotNull(selector.digest)))
        permit(base,presented,"read_"+selector.identity.family.tag.replace('-','_'),c,listOf(resource),closure(base,c))
        val state=base.work[selector.projectId] ?: fail(StoreProblem.INTEGRITY)
        // The typed record carries reference identities, not recursively materialized knowledge.
        // Dereferencing each link remains a separate exact protected operation.
        return state.readExact(selector)
    }
    fun readBody(base: H1BoundState,presented: PresentedCredential,selector: WorkRead.Body): Utf8Content {
        val c=actor(base,presented,selector.projectId)
        val parent=selector.record as? WorkRead.Exact ?: fail(StoreProblem.UNSUPPORTED)
        val scope=c.scope as? CredentialScope.Assignment
        val binding=scope?.let { base.authority.delegations[it.delegationId]?.binding }
        val consumer=(base.work[selector.projectId]?.records?.get(parent.identity)?.payload as? WConditionsPayload)?.value?.consumer
        if(parent.identity.family==WorkFamily.CONDITIONS && binding!=null && consumer==WAssignmentSubject(binding.assignmentId,binding.executionGeneration)) {
            val data=conditionStatus(base,presented,parent).fields("condition","consumer","state","consumerNotice")
            val allowed=identityCodec.decodeBodyReference(StrictJson.encode(data.getValue("consumerNotice")))
            if(selector.digest!=allowed.digest)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            val body=base.work.getValue(selector.projectId).bodies[allowed.digest] ?: throw H1ReadFault("NOT_FOUND")
            if(body.sizeBytes!=allowed.byteLength)fail(StoreProblem.INTEGRITY)
            return body
        }
        readWork(base,presented,parent)
        val state=base.work.getValue(selector.projectId)
        val record=state.readExact(parent)
        val body=workLinks(record.payload).bodies.filter { it.digest==selector.digest }.distinct().singleOrNull() ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val resource=if(parent.identity.family.mutable)ProtectedResource.MutableWorkBody(WorkMutableSelector(parent.identity,requireNotNull(parent.generation)),body)
            else ProtectedResource.WorkBody(WorkReference(parent.identity,requireNotNull(parent.digest)),body)
        permit(base,presented,"read_work_body",c,listOf(resource),closure(base,c))
        return state.readBody(selector)
    }
    /** Deliberately distinct from a raw-record read: no assessment/agreement/result/history body. */
    fun conditionStatus(base: H1BoundState,presented: PresentedCredential,selector: WorkRead.Exact): JsonValue {
        val c=actor(base,presented,selector.projectId)
        val work=base.work[selector.projectId] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val record=work.records[selector.identity] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val condition=(record.payload as? WConditionsPayload)?.value ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val delegation=(c.scope as? CredentialScope.Assignment)?.let { base.authority.delegations[it.delegationId] }
        if(delegation!=null) {
            val b=delegation.binding;val a=work.assignment(b.assignmentId)
            if(condition.consumer!=WAssignmentSubject(b.assignmentId,b.executionGeneration) || a.executionGeneration!=b.executionGeneration ||
                a.state !in setOf("active","pausing","paused","stopping","stopped") ||
                Auth2Policy(base.authority,base.projection).sessionBinding(presented,selector.projectId)==null)
                throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        } else if((c.scope as? CredentialScope.Controller)?.controllerId!=work.binding(condition.consumer).intendedExecution.controllerId)
            throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(selector.digest!=codec.digest(record))throw H1ReadFault("DIGEST_MISMATCH")
        val inspector=Model2Inspector(work);val reference=WorkReference(record.identity,codec.digest(record))
        val tips=inspector.confirmationFrontier(reference)
        val state=when {
            reference !in inspector.frontier(condition.consumer) -> "superseded"
            tips.size>1 -> "conflict"
            tips.isEmpty() -> "blocked"
            (work.resolve(tips.single()).payload as WConditionConfirmationsPayload).value.decision=="resolved" -> "resolved"
            else -> "blocked"
        }
        return obj("condition" to StrictJson.decode(identityCodec.encodeReference(reference)),"consumer" to encodeWAssignmentSubject(condition.consumer),
            "state" to str(state),"consumerNotice" to StrictJson.decode(identityCodec.encodeBodyReference(condition.notice())))
    }
    private fun model2ControllerRecordAllowed(base: H1BoundState,c: ScopedCredential,record: WorkRecord): Boolean {
        if(c.scope is CredentialScope.Assignment)return true // actual delegated closure still checked below
        val work=base.work[c.credential.projectId] ?: return false
        val controller=when(val p=record.payload) {
            is WConditionsPayload -> work.binding(p.value.consumer).intendedExecution.controllerId
            is WConditionConfirmationsPayload -> work.binding((work.resolve(p.value.condition).payload as WConditionsPayload).value.consumer).intendedExecution.controllerId
            is WContinuationsPayload -> work.binding(p.value.assignment).intendedExecution.controllerId
            is WAssignmentReplacementsPayload -> p.value.oldRecord.payload.intendedExecution.controllerId
            else -> return true
        }
        return (c.scope as? CredentialScope.Controller)?.controllerId==controller
    }
}
