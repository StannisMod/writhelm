// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal class Auth2State private constructor(
    val storeId: UUID, val basis: String, principals: Set<UUID>, projects: Map<UUID, ProjectBinding>,
    credentials: Map<UUID, ScopedCredential>, guards: Map<Enrollment, Long>, controllers: Map<UUID, ControllerBinding>,
    workGrants: Map<Enrollment, WorkGrant>, delegations: Map<UUID, Delegation>, val policy: AccessPolicy,
) {
    val principals=frozenSet(principals); val projects=frozenMap(projects); val credentials=frozenMap(credentials)
    val guards=frozenMap(guards); val controllers=frozenMap(controllers); val workGrants=frozenMap(workGrants)
    val delegations=frozenMap(delegations)

    /** Request-local verifier object is reused, never a successor key, on recapture. */
    fun authenticate(presented: PresentedCredential, projectId: UUID): ScopedCredential? {
        if(presented.storeId!=storeId) return null
        val c=credentials[presented.credentialId] ?: return null
        if(c.credential.storeId!=storeId || c.credential.status!=CredentialStatus.ACTIVE ||
            c.credential.projectId!=projectId || !presented.verifies(c.credential)) return null
        if(c.scope is CredentialScope.Assignment && delegations[c.scope.delegationId]?.status!=CredentialStatus.ACTIVE) return null
        return c
    }
    /** Historical boundary proof ONLY: validate retained original credential provenance in its
     * original published H, without inventing a raw secret. Never use for live request admission. */
    internal fun retainedCredential(credentialId: UUID, projectId: UUID): ScopedCredential? {
        val c=credentials[credentialId] ?: return null
        if(c.credential.storeId!=storeId || c.credential.projectId!=projectId || c.credential.status!=CredentialStatus.ACTIVE) return null
        if(c.scope is CredentialScope.Assignment && delegations[c.scope.delegationId]?.status!=CredentialStatus.ACTIVE) return null
        return c
    }
    internal fun owner(c: ScopedCredential, project: UUID): Boolean = c.scope !is CredentialScope.Assignment &&
        projects[project]?.ownerPrincipalId==c.credential.principalId && allows(c,project,Capability.MANAGE)
    internal fun allows(c: ScopedCredential, project: UUID, capability: Capability): Boolean =
        policy.allows(Actor(c.credential.principalId),project,capability)
    internal fun workAllows(c: ScopedCredential, project: UUID, permission: WorkPermission): Boolean =
        c.scope !is CredentialScope.Assignment && permission in workGrants[Enrollment(c.credential.principalId,project)]?.permissions.orEmpty()
    internal fun controller(c: ScopedCredential, project: UUID): UUID? {
        val id=(c.scope as? CredentialScope.Controller)?.controllerId ?: return null
        return id.takeIf { controllers[id]?.let { it.projectId==project && it.principalId==c.credential.principalId }==true }
    }
    private fun dispatch(c: ScopedCredential, project: UUID, id: UUID): Boolean =
        controller(c,project)==id && workAllows(c,project,WorkPermission.TASK_DISPATCH)
    /** One current-policy prerequisite for future mechanical command4 admission. expectedController
     * must be obtained from the validated canonical assignment/binding, never from a body actor or
     * a cached session claim. This does not authorize task scope, replay, evidence or native effects. */
    internal fun authenticateMechanicalController(presented:PresentedCredential,project:UUID,expectedController:UUID):ScopedCredential? {
        val c=authenticate(presented,project) ?: return null
        return c.takeIf { dispatch(it,project,expectedController) }
    }
    internal fun requireProjection(work: AuthWorkProjection) { graph(work.basis==basis) }
    internal fun boundIdentityMatches(a: AssignmentAuthorityFacts, d: Delegation): Boolean = a.boundExecution?.let {
        it.principalId==d.binding.principalId && credentials[it.credentialId]?.let { c ->
            c.scope==CredentialScope.Assignment(d.id) && c.credential.principalId==it.principalId && c.credential.projectId==d.binding.projectId
        }==true
    }==true

    /** Authorization phase before receipt lookup; does not run guards or allocate domain outcomes.
     * A replay of a controller enrollment can reconcile after completion, but still needs current dispatch.
     */
    fun authorizeCommand(presented: PresentedCredential, command: Auth3Command): ScopedCredential? {
        val project=command.projectId; val c=authenticate(presented,project) ?: return null
        val allowed=when(val p=command.payload) {
            is Auth3Payload.ReplaceWorkGrants -> owner(c,project)
            is Auth3Payload.RegisterDelegation -> p.binding.projectId==project && dispatch(c,project,p.binding.controllerId)
            is Auth3Payload.RotateDelegatedCredential -> ownsDelegation(c,project,p.delegationId,p.credentialId)
            is Auth3Payload.RevokeDelegatedCredential -> ownsDelegation(c,project,p.delegationId,p.credentialId)
            is Auth3Payload.RevokeDelegation -> owner(c,project) || ownsDelegation(c,project,p.delegationId)
        }
        return c.takeIf { allowed }
    }
    /** Ownership is determined from current trusted metadata before any target status is returned. */
    internal fun ownsDelegation(c: ScopedCredential, project: UUID, id: UUID, key: UUID?=null): Boolean {
        val ctl=controller(c,project) ?: return false
        if(!workAllows(c,project,WorkPermission.TASK_DISPATCH)) return false
        val d=delegations[id] ?: return false
        if(d.binding.projectId!=project || d.binding.controllerId!=ctl) return false
        return key==null || credentials[key]?.let { it.credential.projectId==project && it.scope==CredentialScope.Assignment(id) }==true
    }

    /** Evaluator only on an AUTHORIZED journal miss. Returns candidate + mandatory coupled effects,
     * never updates CORE or a publication ref. */
    fun execute(presented: PresentedCredential, command: Auth3Command, work: AuthWorkProjection): Auth3Transition {
        Auth3CommandCodec().encode(command) // selected admission also applies to internal typed callers
        requireProjection(work)
        val auth=authorizeCommand(presented,command) ?: return Auth3Transition.Denied
        val project=command.projectId
        fun reject(p: Auth2Problem)=Auth3Transition.Rejected(p)
        fun success(next: Auth2State, id: UUID, replaced: UUID?=null, effects: DelegationEnrollmentEffects?=null): Auth3Transition {
            next.checkBudgets()
            val reader=Auth2RecordCodec(); val writer=Auth2RecordCodec(auth2Admission())
            val before=records().associateBy { reader.path(it) }
            next.records().forEach { record ->
                val old=before[reader.path(record)]
                if(old!=record) writer.encode(record)
            }
            return Auth3Transition.Success(next,command.payload.operation,id,replaced,auth.credential.id,effects)
        }
        return when(val p=command.payload) {
            is Auth3Payload.ReplaceWorkGrants -> {
                val e=Enrollment(p.principalId,project)
                val old=workGrants[e] ?: return reject(Auth2Problem.AUTH_TARGET_NOT_FOUND)
                if(delegations.values.any { it.binding.principalId==p.principalId }) return reject(Auth2Problem.AUTH_FIXED_OWNER_POLICY)
                if(old.generation!=p.expectedGeneration || workGrantDigest(project,p.principalId,old.permissions)!=p.expectedWorkGrantDigest) return reject(Auth2Problem.AUTH_STALE_GRANT)
                if(old.generation==Long.MAX_VALUE) return reject(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
                success(copy(workGrants=workGrants+(e to WorkGrant(e,old.generation+1,p.permissions))),p.principalId)
            }
            is Auth3Payload.RegisterDelegation -> {
                val b=p.binding
                if(p.delegationId in delegations || b.principalId in principals) return reject(Auth2Problem.AUTH_DUPLICATE_ID)
                if(b.initialCredentialId in credentials) return reject(Auth2Problem.AUTH_DUPLICATE_CREDENTIAL)
                val e=Enrollment(b.principalId,project)
                if(policy.snapshotGrants().any { it.principalId==b.principalId } || e in guards || e in workGrants) return reject(Auth2Problem.AUTH_INVALID_BINDING)
                val a=work.assignments[AssignmentSubject(project,b.assignmentId,b.executionGeneration)] ?: return reject(Auth2Problem.AUTH_INVALID_BINDING)
                if(a.assignmentGeneration!=p.expectedAssignmentGeneration) return reject(Auth2Problem.AUTH_STALE_ASSIGNMENT)
                if(!a.matches(b) || !a.independent()) return reject(Auth2Problem.AUTH_TEMPLATE_CONFLICT)
                if(a.phase!=AssignmentPhase.RESERVED || a.delegationId!=null || a.historicalBinding!=null) return reject(Auth2Problem.AUTH_SCOPE_CLOSED)
                if(a.assignmentGeneration==Long.MAX_VALUE) return reject(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
                reserveRecords(5)
                val d=Delegation(p.delegationId,b,0,CredentialStatus.ACTIVE)
                val c=ScopedCredential(Credential(b.initialCredentialId,storeId,project,b.principalId,p.verifierDigest,CredentialStatus.ACTIVE,0),CredentialScope.Assignment(d.id))
                val grant=Grant(b.principalId,project,setOf(Capability.READ))
                val next=copy(principals=principals+b.principalId, credentials=credentials+(c.credential.id to c), guards=guards+(e to 0L),
                    workGrants=workGrants+(e to WorkGrant(e,0,emptySet())), delegations=delegations+(d.id to d),
                    policy=AccessPolicy(policy.snapshotGrants()+grant))
                success(next,d.id,effects=DelegationEnrollmentEffects(basis,e,grant,AssignmentSubject(project,b.assignmentId,b.executionGeneration),a.assignmentGeneration,d.id,a))
            }
            is Auth3Payload.RotateDelegatedCredential -> {
                val d=delegations.getValue(p.delegationId); val c=credentials.getValue(p.credentialId)
                if(c.credential.generation!=p.expectedGeneration) return reject(Auth2Problem.AUTH_STALE_CREDENTIAL)
                if(d.generation!=p.expectedDelegationGeneration) return reject(Auth2Problem.AUTH_STALE_DELEGATION)
                if(c.credential.replacedBy!=null) return reject(Auth2Problem.AUTH_ALREADY_REPLACED)
                if(c.credential.status!=CredentialStatus.ACTIVE || d.status!=CredentialStatus.ACTIVE) return reject(Auth2Problem.AUTH_ALREADY_REVOKED)
                val a=work.assignment(d.binding) ?: return reject(Auth2Problem.AUTH_SCOPE_CLOSED)
                if(a.assignmentGeneration!=p.expectedAssignmentGeneration) return reject(Auth2Problem.AUTH_STALE_ASSIGNMENT)
                if(a.delegationId!=d.id || a.phase !in rotationPhases) return reject(Auth2Problem.AUTH_SCOPE_CLOSED)
                if(p.newCredentialId in credentials) return reject(Auth2Problem.AUTH_DUPLICATE_CREDENTIAL)
                if(c.credential.generation==Long.MAX_VALUE) return reject(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
                reserveRecords(1)
                val successor=ScopedCredential(c.credential.copy(id=p.newCredentialId,verifierDigest=p.newVerifierDigest,generation=0,status=CredentialStatus.ACTIVE,replacedBy=null),c.scope)
                val old=c.copy(credential=c.credential.copy(status=CredentialStatus.REVOKED,generation=c.credential.generation+1,replacedBy=p.newCredentialId))
                success(copy(credentials=credentials+(p.credentialId to old)+(p.newCredentialId to successor)),p.newCredentialId,p.credentialId)
            }
            is Auth3Payload.RevokeDelegatedCredential -> {
                val d=delegations.getValue(p.delegationId); val c=credentials.getValue(p.credentialId)
                if(c.credential.generation!=p.expectedGeneration) return reject(Auth2Problem.AUTH_STALE_CREDENTIAL)
                if(d.generation!=p.expectedDelegationGeneration) return reject(Auth2Problem.AUTH_STALE_DELEGATION)
                if(c.credential.status!=CredentialStatus.ACTIVE) return reject(Auth2Problem.AUTH_ALREADY_REVOKED)
                if(c.credential.generation==Long.MAX_VALUE) return reject(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
                success(copy(credentials=credentials+(p.credentialId to c.copy(credential=c.credential.copy(status=CredentialStatus.REVOKED,generation=c.credential.generation+1)))),p.credentialId)
            }
            is Auth3Payload.RevokeDelegation -> {
                val d=delegations[p.delegationId]?.takeIf { it.binding.projectId==project } ?: return reject(Auth2Problem.AUTH_TARGET_NOT_FOUND)
                if(d.generation!=p.expectedGeneration) return reject(Auth2Problem.AUTH_STALE_DELEGATION)
                if(d.status!=CredentialStatus.ACTIVE) return reject(Auth2Problem.AUTH_ALREADY_REVOKED)
                if(d.generation==Long.MAX_VALUE) return reject(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
                success(copy(delegations=delegations+(d.id to d.copy(status=CredentialStatus.REVOKED,generation=d.generation+1))),d.id)
            }
        }
    }

    /** Existing auth-command2 bytes/meaning with profile2 scope and owner protections. */
    fun executeLegacy(presented: PresentedCredential, command: AuthCommand): Auth2LegacyTransition {
        val c=authenticate(presented,command.projectId) ?: return Auth2LegacyTransition.Denied
        if(!owner(c,command.projectId)) return Auth2LegacyTransition.Denied
        val p=command.payload
        if(p is AuthPayload.ReplaceGrants && delegations.values.any { it.binding.principalId==p.principalId } &&
            p.capabilities.any { it!=Capability.READ }) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_FIXED_OWNER_POLICY)
        val target=when(p) { is AuthPayload.ReplaceGrants -> null; is AuthPayload.RevokeCredential -> p.credentialId; is AuthPayload.RotateCredential -> p.credentialId }
        // Reuse the unchanged AU grant framing. No token reconstruction or whole-graph restore here.
        val transition=when(p) {
            is AuthPayload.ReplaceGrants -> {
                val e=Enrollment(p.principalId,command.projectId); val g=guards[e] ?: return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_TARGET_NOT_FOUND)
                val caps=policy.snapshotGrants().firstOrNull { it.principalId==p.principalId && it.projectId==command.projectId }?.capabilities
                if(g!=p.expectedGeneration || grantDigest(command.projectId,p.principalId,caps)!=p.expectedGrantDigest) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_STALE_GRANT)
                if(g==Long.MAX_VALUE) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
                val special=setOf(Capability.APPROVE,Capability.MANAGE)
                if(if(projects.getValue(command.projectId).ownerPrincipalId==p.principalId) !p.capabilities.containsAll(special) else p.capabilities.any { it in special }) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_FIXED_OWNER_POLICY)
                val grants=policy.snapshotGrants().filterNot { it.principalId==p.principalId && it.projectId==command.projectId }+Grant(p.principalId,command.projectId,p.capabilities)
                return Auth2LegacyTransition.Success(copy(guards=guards+(e to g+1),policy=AccessPolicy(grants)),c.credential.id,GrantPatch(e,g,p.expectedGrantDigest,p.capabilities))
            }
            is AuthPayload.RevokeCredential, is AuthPayload.RotateCredential -> credentials[target]?.takeIf { it.credential.projectId==command.projectId } ?: return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_TARGET_NOT_FOUND)
        }
        val old=transition.credential
        val expected=when(p) { is AuthPayload.RevokeCredential -> p.expectedGeneration; is AuthPayload.RotateCredential -> p.expectedGeneration }
        if(old.generation!=expected) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_STALE_CREDENTIAL)
        if(p is AuthPayload.RevokeCredential && old.status==CredentialStatus.REVOKED) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_ALREADY_REVOKED)
        if(p is AuthPayload.RotateCredential && old.replacedBy!=null) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_ALREADY_REPLACED)
        if(p is AuthPayload.RotateCredential && p.newCredentialId in credentials) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_DUPLICATE_CREDENTIAL)
        if(old.generation==Long.MAX_VALUE) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
        if(p is AuthPayload.RotateCredential) reserveRecords(1)
        var changed=credentials+(old.id to transition.copy(credential=old.copy(status=CredentialStatus.REVOKED,generation=old.generation+1,replacedBy=if(p is AuthPayload.RotateCredential)p.newCredentialId else old.replacedBy)))
        if(p is AuthPayload.RotateCredential) changed=changed+(p.newCredentialId to transition.copy(credential=old.copy(id=p.newCredentialId,verifierDigest=p.newVerifierDigest,status=CredentialStatus.ACTIVE,generation=0,replacedBy=null)))
        if(!ownerLive(command.projectId,changed)) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_OWNER_LOCKOUT)
        return Auth2LegacyTransition.Success(copy(credentials=changed),c.credential.id,null)
    }
    fun revokeAccess(presented: PresentedCredential, project: UUID, principal: UUID): Auth2LegacyTransition {
        val c=authenticate(presented,project) ?: return Auth2LegacyTransition.Denied
        if(!owner(c,project)) return Auth2LegacyTransition.Denied
        val old=policy.snapshotGrants().firstOrNull { it.principalId==principal && it.projectId==project } ?: return Auth2LegacyTransition.CoreRejected(WorkflowProblem.NOT_FOUND)
        if(!policy.revocationKeepsAuthority(project,principal)) return Auth2LegacyTransition.CoreRejected(WorkflowProblem.LAST_AUTHORITY)
        val e=Enrollment(principal,project); val g=guards.getValue(e)
        if(g==Long.MAX_VALUE) return Auth2LegacyTransition.Rejected(Auth2Problem.AUTH_GENERATION_EXHAUSTED)
        val patch=GrantPatch(e,g,grantDigest(project,principal,old.capabilities),null)
        return Auth2LegacyTransition.Success(copy(guards=guards+(e to g+1),policy=AccessPolicy(policy.snapshotGrants().filterNot { it.principalId==principal && it.projectId==project })),c.credential.id,patch)
    }
    private fun ownerLive(project: UUID, cs: Map<UUID,ScopedCredential>): Boolean = cs.values.any {
        it.credential.principalId==projects.getValue(project).ownerPrincipalId && it.credential.projectId==project &&
            it.credential.storeId==storeId && it.credential.status==CredentialStatus.ACTIVE && it.scope==CredentialScope.Project
    }
    private fun copy(principals: Set<UUID> = this.principals, credentials: Map<UUID,ScopedCredential> = this.credentials,
        guards: Map<Enrollment,Long> = this.guards, workGrants: Map<Enrollment,WorkGrant> = this.workGrants,
        delegations: Map<UUID,Delegation> = this.delegations, policy: AccessPolicy = this.policy) =
        Auth2State(storeId,basis,principals,projects,credentials,guards,controllers,workGrants,delegations,policy)
    private fun checkBudgets() {
        reserveRecords(0)
    }
    private fun reserveRecords(additional: Int) {
        if(principals.size.toLong()+projects.size+credentials.size+guards.size+controllers.size+workGrants.size+delegations.size+additional>8192) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
    }
    fun records(): List<Auth2Record> = principals.map { Auth2Record.Principal(it) }+projects.values.map { Auth2Record.Project(it) }+
        credentials.values.map { Auth2Record.Key(it) }+guards.map { Auth2Record.Guard(it.key,it.value) }+
        controllers.values.map { Auth2Record.Controller(it) }+workGrants.values.map { Auth2Record.WorkGrants(it) }+delegations.values.map { Auth2Record.DelegationRecord(it) }

    companion object {
        private val rotationPhases=setOf(AssignmentPhase.RESERVED,AssignmentPhase.BOUND,AssignmentPhase.ACTIVE,AssignmentPhase.PAUSING,AssignmentPhase.PAUSED,AssignmentPhase.STOPPING,AssignmentPhase.STOPPED)
        fun checked(storeId: UUID, basis: String, records: Collection<Auth2Record>, policy: AccessPolicy, work: AuthWorkProjection): Auth2State {
            if(records.size>8192) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            graph(basis.isNotEmpty() && basis==work.basis)
            val codec=Auth2RecordCodec(); val paths=HashSet<String>()
            records.forEach { graph(paths.add(codec.path(it))) }
            val ps=records.filterIsInstance<Auth2Record.Principal>().map { it.id }.toSet()
            val projects=records.filterIsInstance<Auth2Record.Project>().associate { it.binding.projectId to it.binding }
            val cs=records.filterIsInstance<Auth2Record.Key>().associate { it.value.credential.id to it.value }
            val gs=records.filterIsInstance<Auth2Record.Guard>().associate { it.enrollment to it.generation }
            val ctls=records.filterIsInstance<Auth2Record.Controller>().associate { it.binding.controllerId to it.binding }
            val ws=records.filterIsInstance<Auth2Record.WorkGrants>().associate { it.value.enrollment to it.value }
            val ds=records.filterIsInstance<Auth2Record.DelegationRecord>().associate { it.value.id to it.value }
            // Existing validated AccessPolicy is still the sole knowledge-values authority.
            AuthorityView.checked(storeId,ps,projects.values,cs.values.map { it.credential },gs,policy)
            graph(gs.keys==ws.keys)
            graph(ctls.values.all { Enrollment(it.principalId,it.projectId) in gs })
            val delegatedPrincipals=ds.values.map { it.binding.principalId }
            graph(delegatedPrincipals.distinct().size==delegatedPrincipals.size)
            val incoming=HashSet<UUID>()
            cs.values.forEach { c ->
                when(val scope=c.scope) {
                    CredentialScope.Project -> graph(c.credential.principalId !in delegatedPrincipals)
                    is CredentialScope.Controller -> graph(ctls[scope.controllerId]?.let { it.projectId==c.credential.projectId && it.principalId==c.credential.principalId && it.principalId !in delegatedPrincipals }==true)
                    is CredentialScope.Assignment -> graph(ds[scope.delegationId]?.binding?.let { it.projectId==c.credential.projectId && it.principalId==c.credential.principalId }==true)
                }
                c.credential.replacedBy?.let { graph(incoming.add(it)); graph(cs[it]?.scope==c.scope) }
            }
            ds.values.forEach { d ->
                val b=d.binding; val e=Enrollment(b.principalId,b.projectId)
                graph(e in gs && gs.keys.count { it.principalId==b.principalId }==1)
                graph(ws.getValue(e).permissions.isEmpty())
                graph(policy.snapshotGrants().filter { it.principalId==b.principalId }.all { it.projectId==b.projectId && it.capabilities.all { cap -> cap==Capability.READ } })
                graph(ctls[b.controllerId]?.let { it.projectId==b.projectId && it.principalId!=b.principalId }==true)
                graph(ctls.values.none { it.principalId==b.principalId })
                val initial=cs[b.initialCredentialId]; graph(initial!=null && initial.scope==CredentialScope.Assignment(d.id) && b.initialCredentialId !in incoming)
                val chain=HashSet<UUID>(); var cursor: UUID?=b.initialCredentialId
                while(cursor!=null) { graph(chain.add(cursor)); cursor=cs.getValue(cursor).credential.replacedBy }
                graph(chain==cs.values.filter { it.credential.principalId==b.principalId }.map { it.credential.id }.toSet())
                val a=work.assignment(b); graph(a!=null && a.delegationId==d.id && a.independent())
                graph(a!!.phase==AssignmentPhase.RESERVED || a.historicalBinding!=null ||
                    a.phase==AssignmentPhase.REPLACED && a.boundExecution==null && a.retiredReservationProof!=null)
                if(a.historicalBinding!=null) graph(resultBoundIdentity(a,d,cs))
            }
            val result=Auth2State(storeId,basis,ps,projects,cs,gs,ctls,ws,ds,policy)
            graph(projects.keys.all { result.ownerLive(it,cs) })
            result.checkBudgets(); return result
        }
        private fun resultBoundIdentity(a: AssignmentAuthorityFacts, d: Delegation, cs: Map<UUID,ScopedCredential>): Boolean =
            a.boundExecution?.let { it.principalId==d.binding.principalId && cs[it.credentialId]?.scope==CredentialScope.Assignment(d.id) }==true
    }
}
internal sealed interface Auth2LegacyTransition {
    data object Denied: Auth2LegacyTransition
    data class Rejected(val problem: Auth2Problem): Auth2LegacyTransition
    data class CoreRejected(val problem: WorkflowProblem): Auth2LegacyTransition
    class Success internal constructor(val candidate: Auth2State, val authenticatedByCredentialId: UUID, val grantPatch: GrantPatch?): Auth2LegacyTransition
}
