// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*

internal class AppPlan(val basis: GitOid,val actor: AuthenticatedActor,val command: StoreCommand,val next: BoundState,val evaluation: Evaluation)
internal class AppReduction(private val binding: AppBinding) {
    private val limits=binding.options.limits
    fun admit(c: StoreCommand) {
        if(c.fingerprintVersion==1) {
            val codec=CommandCodec(); val command=codec.decode(c.bytes())
            verifyCommand(c,command.requestKey,command.projectId,command.payload.operation,codec.fingerprint(command),codec.encode(command))
        } else {
            val codec=AuthCommandCodec(); val command=codec.decode(c.bytes())
            verifyCommand(c,command.requestKey,command.projectId,command.payload.operation,codec.fingerprint(command),codec.encode(command))
        }
    }
    fun evaluate(h: StoreSnapshot,c: StoreCommand,presented: String,actor: AuthenticatedActor): AppPlan {
        val base=binding.load(h)
        val authenticated=base.authority.authorize(presented,c.projectId,c.operation) ?: fail(StoreProblem.DENIED)
        integrity(authenticated.actor.principalId==actor.principalId && authenticated.credentialId==actor.credentialId)
        var next=base; var coreCommand: CoreCommand?=null; var patch: GrantPatch?=null
        fun <T> coreResult(result: WorkflowResult<Transition<T>>,identity: (T)->JsonValue): LogicalOutcome = when(result) {
            is WorkflowResult.Failure -> {
                if(result.problem==WorkflowProblem.FORBIDDEN)fail(StoreProblem.DENIED)
                LogicalOutcome.Rejected("core",result.problem.name)
            }
            is WorkflowResult.Success -> {
                val t=result.value
                integrity(grantMap(corePolicy(t.state.snapshot(limits.coreRecords)))==grantMap(base.authority.policy))
                next=BoundState(t.state,base.authority)
                LogicalOutcome.Success(c.operation,identity(t.value),t.replayed)
            }
        }
        fun authResult(result: AuthTransition,coreRevocation: ApprovalWorkflow?=null): LogicalOutcome=when(result) {
            AuthTransition.Denied -> fail(StoreProblem.DENIED)
            is AuthTransition.Rejected -> LogicalOutcome.Rejected("auth",result.problem.name)
            is AuthTransition.CoreRejected -> LogicalOutcome.Rejected("core",result.problem.name)
            is AuthTransition.Success -> {
                integrity(result.operation==c.operation && result.authenticatedByCredentialId==actor.credentialId)
                patch=result.grantPatch
                val updated=patch?.let { applyGrantPatch(base,it,result.next) } ?: base.core
                if(coreRevocation!=null) {
                    integrity(sameHistory(updated.snapshot(limits.coreRecords),coreRevocation.snapshot(limits.coreRecords)))
                    integrity(grantMap(corePolicy(updated.snapshot(limits.coreRecords)))==grantMap(corePolicy(coreRevocation.snapshot(limits.coreRecords))))
                }
                val core=coreRevocation ?: updated
                val policy=corePolicy(core.snapshot(limits.coreRecords)); integrity(grantMap(policy)==grantMap(result.next.policy))
                val a=result.next
                next=BoundState(core,AuthorityView.checked(a.storeId,a.principals,a.projects.values,a.credentials.values,a.guards,policy))
                val identity=if(result.replacedCredentialId==null)uuid(result.targetId) else obj("credentialId" to uuid(result.targetId),"replacedCredentialId" to uuid(result.replacedCredentialId))
                LogicalOutcome.Success(c.operation,identity,false)
            }
        }
        val outcome=if(c.fingerprintVersion==1) {
            val codec=CommandCodec(); val command=codec.decode(c.bytes()); coreCommand=command
            verifyCommand(c,command.requestKey,command.projectId,command.payload.operation,codec.fingerprint(command),codec.encode(command))
            affected(base.core.snapshot(limits.coreRecords),roots(command,base.core.snapshot(limits.coreRecords)))
            val a=authenticated.actor
            when(val p=command.payload) {
                is CommandPayload.Propose -> coreResult(base.core.propose(a,p.revision),::revisionKey)
                is CommandPayload.Request -> coreResult(base.core.request(a,command.projectId,p.requestId,p.packet)) { uuid(it.id) }
                is CommandPayload.Resolve -> coreResult(base.core.resolve(a,command.projectId,p.requestId,p.requestDigest,p.packetDigest,p.answer)) { uuid(it.requestId) }
                is CommandPayload.Assess -> coreResult(base.core.assess(a,p.assessment)) { uuid(it.assessment.id) }
                is CommandPayload.Pin -> coreResult(base.core.pin(a,command.projectId,p.manifestId,p.consumerId,p.clauses,p.contexts,p.dependencies,p.assessmentId)) { uuid(it.id) }
                is CommandPayload.Revoke -> {
                    when(val result=base.core.revokeAccess(a,command.projectId,p.principalId)) {
                        is WorkflowResult.Failure -> { if(result.problem==WorkflowProblem.FORBIDDEN)fail(StoreProblem.DENIED); LogicalOutcome.Rejected("core",result.problem.name) }
                        is WorkflowResult.Success -> authResult(base.authority.revokeAccess(presented,command.projectId,p.principalId),result.value.state)
                    }
                }
            }
        } else {
            val codec=AuthCommandCodec(); val command=codec.decode(c.bytes())
            verifyCommand(c,command.requestKey,command.projectId,command.payload.operation,codec.fingerprint(command),codec.encode(command))
            authResult(base.authority.execute(presented,command))
        }
        if(outcome is LogicalOutcome.Rejected || outcome is LogicalOutcome.Success && outcome.domainReplayed) {
            return AppPlan(h.oid,actor,c,base,Evaluation(outcome,emptyList()))
        }
        if(next.authority.principals.size.toLong()+next.authority.projects.size+next.authority.credentials.size+next.authority.guards.size>limits.authRecords)fail(StoreProblem.UNAVAILABLE)
        coreCommand?.let { affected(next.core.snapshot(limits.coreRecords),roots(it,next.core.snapshot(limits.coreRecords))) }
        val delta=coreDelta(base.core,next.core,coreCommand,patch)+authDelta(base.authority,next.authority)
        integrity(base.entryBytes>0)
        var bytes=base.entryBytes
        // Exact derived byte accounting from validated H plus ONLY affected files. No historical
        // body scan and no cache-only permission to grow beyond a supported full-load budget.
        delta.forEach { change -> bytes-=h.file(change.key.path)?.size ?: 0; bytes+=change.bytes()?.size ?: 0 }
        if(bytes>limits.entryBytes)fail(StoreProblem.UNAVAILABLE)
        integrity(bytes>0)
        return AppPlan(h.oid,actor,c,BoundState(next.core,next.authority,bytes),Evaluation(outcome,delta.sortedBy { it.key.path }))
    }
    private fun verifyCommand(c: StoreCommand,key: java.util.UUID,project: java.util.UUID,operation: String,fingerprint: Digest,bytes: ByteArray) {
        integrity(c.requestKey==key && c.projectId==project && c.operation==operation && c.fingerprint==fingerprint.hex && c.bytes().contentEquals(bytes))
    }
    private fun applyGrantPatch(base: BoundState,p: GrantPatch,next: AuthorityView): ApprovalWorkflow {
        val e=p.enrollment
        integrity(base.authority.guards[e]==p.expectedGeneration && p.expectedGeneration<Long.MAX_VALUE)
        integrity(base.authority.grantDigest(e.principalId,e.projectId)==p.beforeDigest)
        integrity(next.guards[e]==p.expectedGeneration+1 && next.guards.filterKeys { it!=e }==base.authority.guards.filterKeys { it!=e })
        val s=base.core.snapshot(limits.coreRecords)
        integrity(grantMap(corePolicy(s))==grantMap(base.authority.policy))
        val grants=s.grants.filterNot { it.principalId==e.principalId && it.projectId==e.projectId }+
            listOfNotNull(p.capabilities?.let { Grant(e.principalId,e.projectId,it) })
        integrity(grantMap(AccessPolicy(grants))==grantMap(next.policy))
        // ONLY this validated reducer-produced exact grant patch reaches the internal snapshot seam.
        // All immutable graph collections/records are retained, not reinterpreted or replayed.
        return ApprovalWorkflow.restoreValidated(WorkflowSnapshot(s.revisions,grants,s.proposals,s.requests,s.resolutions,s.approvals,s.continuity,s.assessments,s.manifests,s.heads,s.generations,s.blocked))
    }
    private fun roots(c: CoreCommand,s: WorkflowSnapshot): List<KnowledgeReference> = when(val p=c.payload) {
        is CommandPayload.Request -> p.packet.references
        is CommandPayload.Resolve -> s.requests[p.requestId]?.packet?.references.orEmpty()
        is CommandPayload.Assess -> p.assessment.references+p.assessment.links.flatMap { listOf(it.source,it.target) }+p.assessment.blocked
        is CommandPayload.Pin -> p.clauses+p.contexts+p.dependencies
        else -> emptyList()
    }
    private fun affected(s: WorkflowSnapshot,roots: List<KnowledgeReference>) {
        val revisions=s.revisions.associateBy { it.key }; val pending=ArrayDeque<Pair<KnowledgeReference,Int>>()
        roots.forEach { pending.add(it to 0) }; var count=0
        while(pending.isNotEmpty()) {
            val (ref,depth)=pending.removeLast()
            if(++count>limits.affectedEdges || depth>limits.depth)fail(StoreProblem.UNAVAILABLE)
            if(ref is ClauseReference)revisions[ref.revision]?.takeIf { it.structure.context.isNotEmpty() }?.let { pending.add(it.contextReference() to depth+1) }
            s.continuity[ref]?.let { pending.add(it.source to depth+1) }
        }
    }
    private fun coreDelta(before: ApprovalWorkflow,after: ApprovalWorkflow,c: CoreCommand?,patch: GrantPatch?): List<RecordChange> {
        val a=before.snapshot(limits.coreRecords); val b=after.snapshot(limits.coreRecords); val paths=HashSet<String>()
        fun add(family: String,key: JsonValue) { paths.add(RecordKey("core",family,key).path) }
        if(patch!=null)add("grants",obj("principalId" to uuid(patch.enrollment.principalId),"projectId" to uuid(patch.enrollment.projectId)))
        when(val p=c?.payload) {
            is CommandPayload.Propose -> { add("revisions",revisionKey(p.revision.key)); add("proposals",revisionKey(p.revision.key)) }
            is CommandPayload.Request -> add("requests",uuid(p.requestId))
            is CommandPayload.Resolve -> { add("resolutions",uuid(p.requestId)); b.resolutions[p.requestId]?.approved?.forEach { add("approvals",reference(it)); add("heads",subject(it.subject())); add("generations",subject(it.subject())) } }
            is CommandPayload.Assess -> { add("assessments",uuid(p.assessment.id)); p.assessment.links.forEach { add("continuity",reference(it.target)) }; p.assessment.blocked.forEach { add("blocked",reference(it)) } }
            is CommandPayload.Pin -> add("manifests",uuid(p.manifestId))
            else -> Unit
        }
        fun encoded(s: WorkflowSnapshot): Map<String,Pair<RecordKey,ByteArray>> {
            val out=LinkedHashMap<String,Pair<RecordKey,ByteArray>>(); var consumed=0L
            CoreRecordCodec.records(s).forEach { r ->
                val key=RecordKey("core",r.family,r.key)
                if(key.path in paths) {
                    val bytes=StrictJson.encode(CoreRecordCodec.envelope(r.family,r.key,r.payload()),8*1024*1024)
                    if(bytes.size>limits.entryBytes-consumed)fail(StoreProblem.UNAVAILABLE)
                    consumed+=bytes.size; out[key.path]=key to bytes
                }
            }
            return out
        }
        val old=encoded(a); val new=encoded(b); val changes=ArrayList<RecordChange>()
        (old.keys+new.keys).sorted().forEach { path ->
            val x=old[path]; val y=new[path]
            if(x?.second?.contentEquals(y?.second ?: byteArrayOf())!=true)changes.add(RecordChange((y ?: x!!).first,y?.second))
        }
        val proposed=(c?.payload as? CommandPayload.Propose)?.revision
        if(proposed!=null) {
            val existing=a.revisions.firstOrNull { it.content.digest==proposed.content.digest }
            if(existing==null)changes.add(RecordChange(RecordKey("core","bodies",digest(proposed.content.digest)),proposed.content.bytes()))
            else integrity(existing.content==proposed.content)
        }
        return changes
    }
    private fun authDelta(before: AuthorityView,after: AuthorityView): List<RecordChange> {
        integrity(before.principals==after.principals && before.projects==after.projects && before.guards.keys==after.guards.keys)
        integrity(before.credentials.keys.all { it in after.credentials })
        val codec=AuthRecordCodec(); val changed=ArrayList<AuthRecord>()
        after.credentials.forEach { (id,c)-> if(before.credentials[id]!=c)changed.add(AuthRecord.CredentialRecord(c)) }
        after.guards.forEach { (e,g)-> if(before.guards[e]!=g)changed.add(AuthRecord.Guard(e,g)) }
        return changed.map { r ->
            val bytes=codec.encode(r); val f=StrictJson.decode(bytes).fields("formatVersion","family","key","payload")
            val key=RecordKey("auth",f.getValue("family").string(),f.getValue("key")); integrity(key.path==codec.path(r)); RecordChange(key,bytes)
        }
    }
}
private fun sameHistory(a: WorkflowSnapshot,b: WorkflowSnapshot)=a.revisions==b.revisions && a.proposals==b.proposals && a.requests==b.requests && a.resolutions==b.resolutions && a.approvals==b.approvals && a.continuity==b.continuity && a.assessments==b.assessments && a.manifests==b.manifests && a.heads==b.heads && a.generations==b.generations && a.blocked==b.blocked
