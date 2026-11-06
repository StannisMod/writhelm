// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import java.util.UUID
import kotlin.test.*

internal fun aid(n: Int)=UUID(0,n.toLong())
internal val store=aid(1); internal val project=aid(2); internal val owner=aid(3); internal val ownerKey=aid(4)
internal val planner=aid(5); internal val plannerKey=aid(6); internal val controller=aid(7)
internal val worker=aid(8); internal val workerKey=aid(9); internal val delegation=aid(10)
internal val task=aid(11); internal val assignment=aid(12); internal val execution=aid(13)
internal val zero=Digest.parse("0".repeat(64))
internal val fixtureSecret=ByteArray(32) { it.toByte() } // Public test vector only, never provision.
internal fun scopedKey(id: UUID, p: UUID, scope: CredentialScope)=ScopedCredential(Credential(id,store,project,p,
    CredentialVerifier.digest(store,project,p,id,fixtureSecret),CredentialStatus.ACTIVE,0),scope)
internal fun <T> token(key: UUID, block: (PresentedCredential)->T): T =
    requireNotNull(CredentialVerifier.parse(CredentialVerifier.token(store,key,fixtureSecret))).use(block)
internal fun ref(family: WorkFamily, id: Int): WorkReference {
    val key=when(family.keyKind) {
        WorkKeyKind.TASK_REVISION -> WorkKey.TaskRevision(project,task,0)
        WorkKeyKind.PROFILE -> WorkKey.Profile(project,aid(id),0)
        WorkKeyKind.ASSIGNMENT_BINDING -> WorkKey.AssignmentBinding(project,assignment,0)
        else -> WorkKey.Id(project,aid(id))
    }
    return WorkReference(WorkRecordIdentity(family,key),zero)
}
internal class Fixture(val template: DelegationTemplate=DelegationTemplate.EXECUTOR) {
    val results=if(template==DelegationTemplate.REVIEWER) listOf(ref(WorkFamily.RESULTS,40),ref(WorkFamily.RESULTS,41)) else emptyList()
    val binding=DelegationBinding(controller,project,worker,workerKey,task,assignment,execution,0,template,results)
    fun facts(phase: AssignmentPhase=AssignmentPhase.RESERVED, generation: Long=0, registered: Boolean=false,
        producers: Map<WorkReference,Set<UUID>> = results.associateWith { setOf(aid(90)) }): AssignmentAuthorityFacts =
        AssignmentAuthorityFacts(project,assignment,task,generation,0,controller,execution,template,results,phase,
            if(registered) delegation else null,ref(WorkFamily.TASK_REVISIONS,20),ref(WorkFamily.INPUT_MANIFESTS,21),ref(WorkFamily.PROFILES,22),aid(23),"ses_fixture",aid(24),
            if(registered && phase!=AssignmentPhase.RESERVED) BoundExecutionFacts(worker,workerKey,execution,aid(23),"ses_fixture",aid(24)) else null,
            WorkMutableSelector(WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,aid(25))),0),
            listOf(WorkMutableSelector(WorkRecordIdentity(WorkFamily.CLAIMS,WorkKey.Resource(project,aid(26))),0)),
            if(registered && phase!=AssignmentPhase.RESERVED) ref(WorkFamily.ASSIGNMENT_BINDINGS,0) else null,producers)
    fun work(phase: AssignmentPhase=AssignmentPhase.RESERVED, generation: Long=0, registered: Boolean=false) = AuthWorkProjection("H",listOf(facts(phase,generation,registered)))
    val baseRecords: List<Auth2Record> = listOf(Auth2Record.Principal(owner),Auth2Record.Principal(planner),Auth2Record.Project(ProjectBinding(project,owner)),
        Auth2Record.Key(scopedKey(ownerKey,owner,CredentialScope.Project)),Auth2Record.Key(scopedKey(plannerKey,planner,CredentialScope.Controller(controller))),
        Auth2Record.Controller(ControllerBinding(controller,project,planner)),Auth2Record.Guard(Enrollment(owner,project),0),Auth2Record.Guard(Enrollment(planner,project),0),
        Auth2Record.WorkGrants(WorkGrant(Enrollment(owner,project),0,WorkPermission.entries)),Auth2Record.WorkGrants(WorkGrant(Enrollment(planner,project),0,WorkPermission.entries)))
    val basePolicy=AccessPolicy(listOf(Grant(owner,project,Capability.entries.toSet()),Grant(planner,project,setOf(Capability.READ,Capability.PLAN))))
    fun state(records: List<Auth2Record> = baseRecords, policy: AccessPolicy=basePolicy, work: AuthWorkProjection=work()) = Auth2State.checked(store,"H",records,policy,work)
    fun command(p: Auth3Payload)=Auth3Command(aid(30),project,p)
    fun register()=command(Auth3Payload.RegisterDelegation(delegation,binding,0,scopedKey(workerKey,worker,CredentialScope.Assignment(delegation)).credential.verifierDigest))
    fun registered(phase: AssignmentPhase=AssignmentPhase.ACTIVE): Auth2State {
        val success=token(plannerKey) { assertIs<Auth3Transition.Success>(state().execute(it,register(),work())) }
        val work=work(phase,1,true)
        return state(success.candidate.records(),success.candidate.policy,work)
    }
    fun execute(s: Auth2State, p: Auth3Payload, key: UUID=plannerKey, work: AuthWorkProjection=work(AssignmentPhase.ACTIVE,1,true)) = token(key) { s.execute(it,command(p),work) }
    fun call(op: WorkOperation)=WorkInvocation(project,op,aid(30),AssignmentSubject(project,assignment,0),null,
        if(op==WorkOperation.UPDATE_PLAN) AssignmentSubject(project,assignment,0) else null)
}
