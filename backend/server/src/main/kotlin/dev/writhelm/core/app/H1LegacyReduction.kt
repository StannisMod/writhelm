// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*

/** Original command1/2 codecs and real reducers, intersected with current AUTH2 scope. */
internal class H1LegacyReduction(private val limits: AppLimits=AppLimits()) {
    fun evaluate(base: H1BoundState,command: StoreCommand,presented: PresentedCredential): H1JointCandidate {
        val c=Auth2Policy(base.authority,base.projection).authorizeLegacyCommand(presented,command.projectId,command.operation) ?: fail(StoreProblem.DENIED)
        val actor=Actor(c.credential.principalId);var core=base.core;var authority=base.authority;var patch: GrantPatch?=null
        fun applyAuth(result: Auth2LegacyTransition): LogicalOutcome = when(result) {
            Auth2LegacyTransition.Denied -> fail(StoreProblem.DENIED)
            is Auth2LegacyTransition.Rejected -> LogicalOutcome.Rejected("auth",result.problem.name)
            is Auth2LegacyTransition.CoreRejected -> LogicalOutcome.Rejected("core",result.problem.name)
            is Auth2LegacyTransition.Success -> {
                integrity(result.authenticatedByCredentialId==c.credential.id)
                authority=result.candidate;patch=result.grantPatch
                patch?.let { p ->
                    val s=core.snapshot(8192);val e=p.enrollment
                    integrity(base.authority.guards[e]==p.expectedGeneration && p.expectedGeneration<Long.MAX_VALUE)
                    val old=s.grants.firstOrNull { it.principalId==e.principalId && it.projectId==e.projectId }?.capabilities
                    integrity(grantDigest(e.projectId,e.principalId,old)==p.beforeDigest)
                    val grants=s.grants.filterNot { it.principalId==e.principalId && it.projectId==e.projectId }+
                        listOfNotNull(p.capabilities?.let { Grant(e.principalId,e.projectId,it) })
                    core=ApprovalWorkflow.restoreValidated(WorkflowSnapshot(s.revisions,grants,s.proposals,s.requests,s.resolutions,s.approvals,s.continuity,s.assessments,s.manifests,s.heads,s.generations,s.blocked))
                }
                val payload=command.payload as JObject
                val resultId=when(command.operation) {
                    "replace_grants","revoke_access" -> payload.entries.getValue("principalId")
                    "revoke_credential" -> payload.entries.getValue("credentialId")
                    "rotate_credential" -> obj("credentialId" to payload.entries.getValue("newCredentialId"),"replacedCredentialId" to payload.entries.getValue("credentialId"))
                    else -> fail(StoreProblem.UNSUPPORTED)
                }
                LogicalOutcome.Success(command.operation,resultId,false)
            }
        }
        fun <T> effect(result: WorkflowResult<Transition<T>>,id: (T)->JsonValue): LogicalOutcome=when(result) {
            is WorkflowResult.Failure -> if(result.problem==WorkflowProblem.FORBIDDEN)fail(StoreProblem.DENIED) else LogicalOutcome.Rejected("core",result.problem.name)
            is WorkflowResult.Success -> { core=result.value.state;LogicalOutcome.Success(command.operation,id(result.value.value),result.value.replayed) }
        }
        var typed: CoreCommand?=null
        val outcome=if(command.fingerprintVersion==2)applyAuth(base.authority.executeLegacy(presented,AuthCommandCodec().decode(command.bytes()))) else {
            val v=CommandCodec().decode(command.bytes());typed=v
            affected(base.core.snapshot(limits.coreRecords),roots(v,base.core.snapshot(limits.coreRecords)))
            when(val p=v.payload) {
                is CommandPayload.Propose -> effect(core.propose(actor,p.revision),::revisionKey)
                is CommandPayload.Request -> effect(core.request(actor,v.projectId,p.requestId,p.packet)) { uuid(it.id) }
                is CommandPayload.Resolve -> effect(core.resolve(actor,v.projectId,p.requestId,p.requestDigest,p.packetDigest,p.answer)) { uuid(it.requestId) }
                is CommandPayload.Assess -> effect(core.assess(actor,p.assessment)) { uuid(it.assessment.id) }
                is CommandPayload.Pin -> effect(core.pin(actor,v.projectId,p.manifestId,p.consumerId,p.clauses,p.contexts,p.dependencies,p.assessmentId)) { uuid(it.id) }
                is CommandPayload.Revoke -> applyAuth(base.authority.revokeAccess(presented,v.projectId,p.principalId))
            }
        }
        if(outcome is LogicalOutcome.Rejected || outcome is LogicalOutcome.Success && outcome.domainReplayed)
            return H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(outcome,emptyList()))
        typed?.let { affected(core.snapshot(limits.coreRecords),roots(it,core.snapshot(limits.coreRecords))) }
        authority=Auth2State.checked(base.storeId,headBasis(base.basis),authority.records(),corePolicy(core.snapshot(8192)),base.projection)
        val changes=coreChanges(base.core,core,typed,patch)+authChanges(base.authority,authority)
        return H1JointCandidate(base,core,authority,base.work,Evaluation(outcome,changes.sortedBy { it.key.path }))
    }
    private fun roots(c: CoreCommand,s: WorkflowSnapshot): List<KnowledgeReference> = when(val p=c.payload) {
        is CommandPayload.Request -> p.packet.references
        is CommandPayload.Resolve -> s.requests[p.requestId]?.packet?.references.orEmpty()
        is CommandPayload.Assess -> p.assessment.references+p.assessment.links.flatMap { listOf(it.source,it.target) }+p.assessment.blocked
        is CommandPayload.Pin -> p.clauses+p.contexts+p.dependencies
        else -> emptyList()
    }
    private fun affected(s: WorkflowSnapshot,roots: List<KnowledgeReference>) {
        val revisions=s.revisions.associateBy { it.key };val queue=ArrayDeque<Pair<KnowledgeReference,Int>>()
        roots.forEach { queue.add(it to 0) };var edges=0
        while(queue.isNotEmpty()) {
            val (reference,depth)=queue.removeLast()
            if(++edges>limits.affectedEdges || depth>limits.depth)refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            if(reference is ClauseReference)revisions[reference.revision]?.takeIf { it.structure.context.isNotEmpty() }?.let { queue.add(it.contextReference() to depth+1) }
            s.continuity[reference]?.let { queue.add(it.source to depth+1) }
        }
    }
    private fun authChanges(before: Auth2State,after: Auth2State): List<RecordChange> {
        val codec=Auth2RecordCodec(auth2Admission());val old=before.records().associateBy { codec.path(it) }
        return after.records().filter { old[codec.path(it)]!=it }.map { r ->
            val (family,key)=codec.identity(r);RecordChange(RecordKey("auth",family,key),codec.encode(r))
        }
    }
    private fun coreChanges(before: ApprovalWorkflow,after: ApprovalWorkflow,command: CoreCommand?,patch: GrantPatch?): List<RecordChange> {
        val a=before.snapshot(8192);val b=after.snapshot(8192);val paths=HashSet<String>()
        fun path(f: String,k: JsonValue) { paths.add(RecordKey("core",f,k).path) }
        patch?.let { path("grants",obj("principalId" to uuid(it.enrollment.principalId),"projectId" to uuid(it.enrollment.projectId))) }
        when(val p=command?.payload) {
            is CommandPayload.Propose -> { path("revisions",revisionKey(p.revision.key));path("proposals",revisionKey(p.revision.key)) }
            is CommandPayload.Request -> path("requests",uuid(p.requestId))
            is CommandPayload.Resolve -> { path("resolutions",uuid(p.requestId));b.resolutions[p.requestId]?.approved?.forEach { ref ->
                path("approvals",reference(ref));path("heads",subject(ref.subject()));path("generations",subject(ref.subject())) } }
            is CommandPayload.Assess -> { path("assessments",uuid(p.assessment.id));p.assessment.links.forEach { path("continuity",reference(it.target)) };p.assessment.blocked.forEach { path("blocked",reference(it)) } }
            is CommandPayload.Pin -> path("manifests",uuid(p.manifestId))
            else -> Unit
        }
        fun records(s: WorkflowSnapshot)=CoreRecordCodec.records(s).filter { it.path(8*1024*1024) in paths }.associate { r ->
            val key=RecordKey("core",r.family,r.key);key.path to (key to StrictJson.encode(CoreRecordCodec.envelope(r.family,r.key,r.payload()),8*1024*1024)) }
        val old=records(a);val next=records(b)
        val delta=(old.keys+next.keys).mapNotNull { p ->
            val x=old[p];val y=next[p]
            if(x?.second?.contentEquals(y?.second ?: byteArrayOf())==true)null else RecordChange((y ?: x!!).first,y?.second)
        }.toMutableList()
        val proposed=(command?.payload as? CommandPayload.Propose)?.revision
        if(proposed!=null && a.revisions.none { it.content.digest==proposed.content.digest })delta.add(RecordChange(RecordKey("core","bodies",digest(proposed.content.digest)),proposed.content.bytes()))
        return delta
    }
}
