// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth

import dev.writhelm.core.codec.graph
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal fun grantDigest(projectId: UUID,principalId: UUID,capabilities: Set<Capability>?): Digest = framedDigest("writhelm.grant-state.v1") {
    for(id in listOf(projectId,principalId)) { writeLong(id.mostSignificantBits); writeLong(id.leastSignificantBits) }
    writeByte(if(capabilities==null)0 else 1)
    val names=capabilities.orEmpty().map { it.name }.sorted(); writeInt(names.size)
    names.forEach { writeFramed(encodeUtf8(it)) }
}
internal enum class AuthProblem {
    AUTH_TARGET_NOT_FOUND, AUTH_STALE_CREDENTIAL, AUTH_STALE_GRANT, AUTH_ALREADY_REVOKED,
    AUTH_ALREADY_REPLACED, AUTH_DUPLICATE_CREDENTIAL, AUTH_OWNER_LOCKOUT, AUTH_FIXED_OWNER_POLICY,
    AUTH_GENERATION_EXHAUSTED
}
internal data class CommandIdentity(val principalId: UUID,val projectId: UUID,val operation: String,val requestKey: UUID)
internal class AuthorizedRequest internal constructor(val actor: Actor,val credentialId: UUID)

/** Proposed, not applied to a WorkflowSnapshot. Parent binds it only with the entire candidate + receipt.
 * There is no generic snapshot/actor override, and no publisher code is depended on here. */
internal class GrantPatch internal constructor(val enrollment: Enrollment,val expectedGeneration: Long,
    val beforeDigest: Digest,capabilities: Set<Capability>?) {
    val capabilities=capabilities?.let { frozenSet(it) }
}
internal sealed interface AuthTransition {
    /** Access refusal is deliberately not a durable domain rejection. */
    data object Denied: AuthTransition
    class Rejected(val problem: AuthProblem): AuthTransition
    /** revoke_access retains the existing core domain taxonomy, distinct from auth command failures. */
    class CoreRejected(val problem: WorkflowProblem): AuthTransition
    class Success internal constructor(val next: AuthorityView,val operation: String,val targetId: UUID,
        val replacedCredentialId: UUID?,val authenticatedByCredentialId: UUID,val grantPatch: GrantPatch?): AuthTransition
}

/** Immutable auth metadata plus THE core AccessPolicy, not a mirrored capabilities registry.
 * checked is for trusted startup/genesis only; it is not a client enrollment or state upload endpoint.
 * In-memory results are candidates. Secret-file IO, publication, replay persistence and H binding are external.
 */
internal class AuthorityView private constructor(val storeId: UUID,principals: Set<UUID>,projects: Map<UUID,ProjectBinding>,
    credentials: Map<UUID,Credential>,guards: Map<Enrollment,Long>,val policy: AccessPolicy) {
    val principals=frozenSet(principals)
    val projects=frozenMap(projects)
    val credentials=frozenMap(credentials)
    val guards=frozenMap(guards)

    fun grantDigest(principalId: UUID,projectId: UUID): Digest = grantDigest(projectId,principalId,currentCapabilities(Enrollment(principalId,projectId)))
    private fun currentCapabilities(e: Enrollment): Set<Capability>? = policy.snapshotGrants().firstOrNull { it.projectId==e.projectId && it.principalId==e.principalId }?.capabilities

    /** Call again on every newly captured H, with the SAME presented token, including CAS/replay paths. */
    fun authorize(presented: String,projectId: UUID,operation: String): AuthorizedRequest? {
        val required=requiredCapability(operation) ?: return null
        val token=CredentialVerifier.parse(presented) ?: return null
        token.use {
            if(token.storeId!=storeId) return null
            val c=credentials[token.credentialId] ?: return null
            if(c.storeId!=storeId || c.status!=CredentialStatus.ACTIVE || !token.verifies(c)) return null
            if(c.projectId!=projectId || !policy.allows(Actor(c.principalId),projectId,required)) return null
            return AuthorizedRequest(Actor(c.principalId),c.id)
        }
    }
    /** Only constructs the current authorized principal key. Lookup/comparison/original provenance stay external. */
    fun replayIdentity(presented: String,projectId: UUID,operation: String,requestKey: UUID): CommandIdentity? =
        authorize(presented,projectId,operation)?.let { CommandIdentity(it.actor.principalId,projectId,operation,requestKey) }

    fun execute(presented: String,command: AuthCommand): AuthTransition {
        val auth=authorize(presented,command.projectId,command.payload.operation) ?: return AuthTransition.Denied
        val project=command.projectId
        return when(val p=command.payload) {
            is AuthPayload.ReplaceGrants -> {
                val e=Enrollment(p.principalId,project)
                val g=guards[e] ?: return reject(AuthProblem.AUTH_TARGET_NOT_FOUND)
                if(g!=p.expectedGeneration || grantDigest(p.principalId,project)!=p.expectedGrantDigest) return reject(AuthProblem.AUTH_STALE_GRANT)
                if(g==Long.MAX_VALUE) return reject(AuthProblem.AUTH_GENERATION_EXHAUSTED)
                if(!validOwnerCapabilities(e,p.capabilities)) return reject(AuthProblem.AUTH_FIXED_OWNER_POLICY)
                val patch=GrantPatch(e,g,p.expectedGrantDigest,p.capabilities)
                val next=grantCandidate(patch)
                succeeded(next,p.operation,p.principalId,null,auth,patch)
            }
            is AuthPayload.RevokeCredential -> {
                val c=credentials[p.credentialId]?.takeIf { it.projectId==project } ?: return reject(AuthProblem.AUTH_TARGET_NOT_FOUND)
                if(c.generation!=p.expectedGeneration) return reject(AuthProblem.AUTH_STALE_CREDENTIAL)
                if(c.status==CredentialStatus.REVOKED) return reject(AuthProblem.AUTH_ALREADY_REVOKED)
                if(c.generation==Long.MAX_VALUE) return reject(AuthProblem.AUTH_GENERATION_EXHAUSTED)
                val changed=credentials+(c.id to c.copy(status=CredentialStatus.REVOKED,generation=c.generation+1))
                if(!ownerLive(project,changed)) return reject(AuthProblem.AUTH_OWNER_LOCKOUT)
                succeeded(copy(credentials=changed),p.operation,c.id,null,auth,null)
            }
            is AuthPayload.RotateCredential -> {
                val c=credentials[p.credentialId]?.takeIf { it.projectId==project } ?: return reject(AuthProblem.AUTH_TARGET_NOT_FOUND)
                if(c.generation!=p.expectedGeneration) return reject(AuthProblem.AUTH_STALE_CREDENTIAL)
                if(c.replacedBy!=null) return reject(AuthProblem.AUTH_ALREADY_REPLACED)
                if(p.newCredentialId in credentials) return reject(AuthProblem.AUTH_DUPLICATE_CREDENTIAL)
                if(c.generation==Long.MAX_VALUE) return reject(AuthProblem.AUTH_GENERATION_EXHAUSTED)
                val successor=Credential(p.newCredentialId,c.storeId,c.projectId,c.principalId,p.newVerifierDigest,CredentialStatus.ACTIVE,0)
                val changed=credentials+(c.id to c.copy(status=CredentialStatus.REVOKED,generation=c.generation+1,replacedBy=successor.id))+(successor.id to successor)
                if(!ownerLive(project,changed)) return reject(AuthProblem.AUTH_OWNER_LOCKOUT)
                succeeded(copy(credentials=changed),p.operation,successor.id,c.id,auth,null)
            }
        }
    }
    /** v1/core operation; parent must include this guard advance with its core effect in journal v2.
     * Call only on a command-journal miss. A matching command replay must not execute this again. */
    fun revokeAccess(presented: String,projectId: UUID,principalId: UUID): AuthTransition {
        val auth=authorize(presented,projectId,"revoke_access") ?: return AuthTransition.Denied
        val e=Enrollment(principalId,projectId)
        if(currentCapabilities(e)==null) return AuthTransition.CoreRejected(WorkflowProblem.NOT_FOUND)
        if(!policy.revocationKeepsAuthority(projectId,principalId)) return AuthTransition.CoreRejected(WorkflowProblem.LAST_AUTHORITY)
        val generation=guards.getValue(e)
        if(generation==Long.MAX_VALUE) return reject(AuthProblem.AUTH_GENERATION_EXHAUSTED)
        val patch=GrantPatch(e,generation,grantDigest(principalId,projectId),null)
        return succeeded(grantCandidate(patch),"revoke_access",principalId,null,auth,patch)
    }
    private fun grantCandidate(patch: GrantPatch): AuthorityView {
        val e=patch.enrollment
        val grants=policy.snapshotGrants().filterNot { it.projectId==e.projectId && it.principalId==e.principalId }+
            listOfNotNull(patch.capabilities?.let { Grant(e.principalId,e.projectId,it) })
        return copy(guards=guards+(e to patch.expectedGeneration+1),policy=AccessPolicy(grants))
    }
    private fun validOwnerCapabilities(e: Enrollment,caps: Set<Capability>): Boolean {
        val special=setOf(Capability.APPROVE,Capability.MANAGE)
        return if(projects.getValue(e.projectId).ownerPrincipalId==e.principalId)caps.containsAll(special) else caps.intersect(special).isEmpty()
    }
    private fun ownerLive(project: UUID,cs: Map<UUID,Credential>): Boolean {
        val owner=projects.getValue(project).ownerPrincipalId
        return cs.values.any { it.storeId==storeId && it.projectId==project && it.principalId==owner && it.status==CredentialStatus.ACTIVE && Enrollment(owner,project) in guards }
    }
    private fun copy(credentials: Map<UUID,Credential> = this.credentials,guards: Map<Enrollment,Long> = this.guards,policy: AccessPolicy=this.policy)=AuthorityView(storeId,principals,projects,credentials,guards,policy)
    private fun reject(p: AuthProblem)=AuthTransition.Rejected(p)
    private fun succeeded(next: AuthorityView,op: String,target: UUID,replaced: UUID?,auth: AuthorizedRequest,patch: GrantPatch?)=AuthTransition.Success(next,op,target,replaced,auth.credentialId,patch)

    companion object {
        fun checked(storeId: UUID,principals: Collection<UUID>,projects: Collection<ProjectBinding>,credentials: Collection<Credential>,guards: Map<Enrollment,Long>,policy: AccessPolicy): AuthorityView {
            graph(principals.toSet().size==principals.size)
            graph(projects.map { it.projectId }.toSet().size==projects.size)
            graph(credentials.map { it.id }.toSet().size==credentials.size)
            val ps=projects.associateBy { it.projectId }; val cs=credentials.associateBy { it.id }
            graph(ps.values.all { it.ownerPrincipalId in principals && Enrollment(it.ownerPrincipalId,it.projectId) in guards })
            graph(guards.all { (e,g) -> e.principalId in principals && e.projectId in ps && g>=0 })
            graph(cs.values.all { it.storeId==storeId && Enrollment(it.principalId,it.projectId) in guards })
            val v=AuthorityView(storeId,principals.toSet(),ps,cs,guards,policy)
            graph(policy.snapshotGrants().all { Enrollment(it.principalId,it.projectId) in guards && v.validOwnerCapabilities(Enrollment(it.principalId,it.projectId),it.capabilities) })
            graph(ps.values.all { b -> policy.allows(Actor(b.ownerPrincipalId),b.projectId,Capability.APPROVE) && policy.allows(Actor(b.ownerPrincipalId),b.projectId,Capability.MANAGE) && v.ownerLive(b.projectId,cs) })
            val incoming=HashSet<UUID>()
            cs.values.forEach { c -> c.replacedBy?.let { next ->
                val n=cs[next]; graph(n!=null && n.storeId==c.storeId && n.projectId==c.projectId && n.principalId==c.principalId && c.status==CredentialStatus.REVOKED && c.generation>0)
                graph(incoming.add(next))
            } }
            // Iterative linear-time cycle check. No recursion or one traversal per ancestor.
            val done=HashSet<UUID>()
            cs.keys.forEach { start ->
                val chain=HashSet<UUID>(); var cursor: UUID?=start
                while(cursor!=null && cursor !in done) { graph(chain.add(cursor)); cursor=cs.getValue(cursor).replacedBy }
                done.addAll(chain)
            }
            return v
        }
    }
}
internal fun requiredCapability(operation: String): Capability? = when(operation) {
    "read" -> Capability.READ
    "propose_revision" -> Capability.PROPOSE
    "request_approval","record_assessment","pin_consumer" -> Capability.PLAN
    "resolve_approval" -> Capability.APPROVE
    "revoke_access","replace_grants","revoke_credential","rotate_credential" -> Capability.MANAGE
    else -> null
}
