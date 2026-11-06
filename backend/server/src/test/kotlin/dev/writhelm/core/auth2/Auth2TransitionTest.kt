// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import kotlin.test.*

class Auth2TransitionTest {
    @Test fun registrationHasSixRecordsAndMandatoryTypedJointEffects() {
        val f=Fixture(); val s=f.state(); val before=Auth2SnapshotCodec().export(s).toList()
        val t=token(plannerKey) { assertIs<Auth3Transition.Success>(s.execute(it,f.register(),f.work())) }
        assertEquals(5,t.candidate.records().size-s.records().size)
        assertEquals(setOf(Capability.READ),t.candidate.policy.snapshotGrants().single { it.principalId==worker }.capabilities)
        assertEquals(emptySet(),t.candidate.workGrants.getValue(Enrollment(worker,project)).permissions)
        val effects=assertNotNull(t.enrollmentEffects)
        assertEquals(0,effects.expectedAssignmentGeneration); assertEquals(1,effects.nextAssignmentGeneration)
        assertEquals(delegation,effects.delegationId); assertEquals(f.binding.assignmentId,effects.assignment.assignmentId)
        effects.verifyApplied(t.candidate.policy,f.work(AssignmentPhase.RESERVED,1,true))
        assertFailsWith<CodecFault> { effects.verifyApplied(s.policy,f.work(AssignmentPhase.RESERVED,1,true)) }
        assertFailsWith<CodecFault> { effects.verifyApplied(t.candidate.policy,f.work()) }
        assertFailsWith<CodecFault> { effects.verifyApplied(t.candidate.policy,f.work(AssignmentPhase.RESERVED,2,true)) }
        assertEquals(before.map { it.first },Auth2SnapshotCodec().export(s).toList().map { it.first })
        assertEquals(AdmissionMode.REPLAY_ONLY,token(workerKey) {
            Auth2Policy(f.registered(AssignmentPhase.BOUND),f.work(AssignmentPhase.BOUND,1,true)).authorizeWork(it,f.call(WorkOperation.SUBMIT_RESULT))!!.mode
        })
        // Candidate cannot be restored against the pre-registration CORE projection.
        assertFailsWith<CodecFault> { f.state(t.candidate.records(),t.candidate.policy,f.work()) }
    }
    @Test fun projectCredentialCannotClaimControllerAndWrongReservationHasNoEnrollment() {
        val f=Fixture(); val s=f.state()
        token(ownerKey) { assertIs<Auth3Transition.Denied>(s.execute(it,f.register(),f.work())) }
        val bad=Auth3Payload.RegisterDelegation(delegation,f.binding,7,zero)
        assertEquals(Auth2Problem.AUTH_STALE_ASSIGNMENT,assertIs<Auth3Transition.Rejected>(f.execute(s,bad,work=f.work())).problem)
        assertFalse(worker in s.principals)
        val duplicate=Auth3Payload.RegisterDelegation(delegation,DelegationBinding(controller,project,owner,workerKey,task,assignment,execution,0,DelegationTemplate.EXECUTOR,emptyList()),0,zero)
        assertEquals(Auth2Problem.AUTH_DUPLICATE_ID,assertIs<Auth3Transition.Rejected>(f.execute(s,duplicate,work=f.work())).problem)
    }
    @Test fun registrationGenerationOverflowIsAtomic() {
        val f=Fixture(); val p=AuthWorkProjection("H",listOf(f.facts(generation=Long.MAX_VALUE)))
        val cmd=f.command(Auth3Payload.RegisterDelegation(delegation,f.binding,Long.MAX_VALUE,zero))
        val s=f.state(work=p)
        token(plannerKey) { assertEquals(Auth2Problem.AUTH_GENERATION_EXHAUSTED,assertIs<Auth3Transition.Rejected>(s.execute(it,cmd,p)).problem) }
        assertFalse(worker in s.principals)
    }
    @Test fun orderedReviewGroupChecksEveryProducer() {
        val f=Fixture(DelegationTemplate.REVIEWER)
        val a=f.facts(producers=mapOf(f.results[0] to setOf(aid(90)), f.results[1] to setOf(execution,aid(91))))
        val work=AuthWorkProjection("H",listOf(a)); val s=f.state(work=work)
        assertEquals(Auth2Problem.AUTH_TEMPLATE_CONFLICT,assertIs<Auth3Transition.Rejected>(f.execute(s,f.register().payload,work=work)).problem)
        val reversed=DelegationBinding(controller,project,worker,workerKey,task,assignment,execution,0,DelegationTemplate.REVIEWER,f.results.reversed())
        assertEquals(Auth2Problem.AUTH_TEMPLATE_CONFLICT,assertIs<Auth3Transition.Rejected>(f.execute(f.state(),Auth3Payload.RegisterDelegation(delegation,reversed,0,zero),work=f.work())).problem)
        assertNotNull(f.registered().delegations[delegation])
    }
    @Test fun workGrantABAAndEqualSetReplacementAdvanceOnlyWorkGuard() {
        val f=Fixture(); val s=f.state(); val e=Enrollment(planner,project)
        fun replace(s: Auth2State,p: Set<WorkPermission>,g: Long=s.workGrants.getValue(e).generation)=
            assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.ReplaceWorkGrants(planner,g,workGrantDigest(project,planner,s.workGrants.getValue(e).permissions),p),ownerKey,f.work())).candidate
        val removed=replace(s,emptySet()); val restored=replace(removed,WorkPermission.entries.toSet())
        assertEquals(2,restored.workGrants.getValue(e).generation)
        val stale=f.execute(restored,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptySet()),ownerKey,f.work())
        assertEquals(Auth2Problem.AUTH_STALE_GRANT,assertIs<Auth3Transition.Rejected>(stale).problem)
        assertEquals(3,replace(restored,WorkPermission.entries.toSet()).workGrants.getValue(e).generation)
        assertEquals(s.guards,restored.guards); assertEquals(s.policy.snapshotGrants().map { it.capabilities },restored.policy.snapshotGrants().map { it.capabilities })
    }
    @TestFactory fun independentWorkPermissions() = WorkPermission.entries.map { permission -> dynamicTest(permission.name) {
        val f=Fixture(); val rs=f.baseRecords.filterNot { it is Auth2Record.WorkGrants && it.value.enrollment.principalId==planner }+
            Auth2Record.WorkGrants(WorkGrant(Enrollment(planner,project),0,setOf(permission)))
        val s=f.state(rs); val policy=Auth2Policy(s,f.work())
        val operations=mapOf(WorkPermission.TASK_PLAN to WorkOperation.CREATE_TASK_REVISION,WorkPermission.TASK_DISPATCH to WorkOperation.RECORD_DELIVERY,
            WorkPermission.RESULT_ACCEPT to WorkOperation.ACCEPT_RESULT,WorkPermission.TARGET_INTEGRATE to WorkOperation.PREPARE_INTEGRATION)
        token(plannerKey) { key -> operations.forEach { (required,op) ->
            assertEquals(required==permission,policy.authorizeWork(key,WorkInvocation(project,op,aid(30),null,null,null))!=null)
        } }
    } }
    @Test fun revokedDispatchDoesNotCascadeAndOwnerCanRevokeWithoutIt() {
        val f=Fixture(); val s=f.registered()
        val withoutDispatch=assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptySet()),ownerKey)).candidate
        token(workerKey) { assertEquals(AdmissionMode.EVALUATE,Auth2Policy(withoutDispatch,f.work(AssignmentPhase.ACTIVE,1,true)).authorizeWork(it,f.call(WorkOperation.SUBMIT_RESULT))?.mode) }
        assertIs<Auth3Transition.Denied>(f.execute(withoutDispatch,Auth3Payload.RevokeDelegation(delegation,0)))
        val revoked=assertIs<Auth3Transition.Success>(f.execute(withoutDispatch,Auth3Payload.RevokeDelegation(delegation,0),ownerKey)).candidate
        token(workerKey) { assertNull(Auth2Policy(revoked,f.work(AssignmentPhase.ACTIVE,1,true)).authorizeWork(it,f.call(WorkOperation.SUBMIT_RESULT))) }
        val regrant=assertIs<Auth3Transition.Success>(f.execute(revoked,Auth3Payload.ReplaceWorkGrants(planner,1,workGrantDigest(project,planner,emptySet()),WorkPermission.entries.toSet()),ownerKey)).candidate
        token(workerKey) { assertNull(regrant.authenticate(it,project)) }
    }
    @Test fun rotationPreservesScopeAndLineageAndOldKeyDenies() {
        val f=Fixture(); val s=f.registered(); val replacement=scopedKey(aid(50),worker,CredentialScope.Assignment(delegation))
        val t=assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.RotateDelegatedCredential(delegation,workerKey,0,0,1,aid(50),replacement.credential.verifierDigest)))
        val next=f.state(t.candidate.records(),t.candidate.policy,f.work(AssignmentPhase.ACTIVE,1,true))
        assertEquals(CredentialScope.Assignment(delegation),next.credentials.getValue(aid(50)).scope)
        assertEquals(workerKey,next.delegations.getValue(delegation).binding.initialCredentialId)
        token(workerKey) { assertNull(next.authenticate(it,project)) }; token(aid(50)) { assertNotNull(next.authenticate(it,project)) }
        assertEquals(Auth2Problem.AUTH_ALREADY_REPLACED,assertIs<Auth3Transition.Rejected>(f.execute(next,Auth3Payload.RotateDelegatedCredential(delegation,workerKey,1,0,1,aid(51),zero))).problem)
        assertEquals(Auth2Problem.AUTH_DUPLICATE_CREDENTIAL,assertIs<Auth3Transition.Rejected>(f.execute(s,Auth3Payload.RotateDelegatedCredential(delegation,workerKey,0,0,1,plannerKey,zero))).problem)
    }
    @Test fun explicitRevocationBlocksRotationButCleanupWorksAfterTerminal() {
        val f=Fixture(); val s=f.registered(AssignmentPhase.COMPLETED); val work=f.work(AssignmentPhase.COMPLETED,1,true)
        assertEquals(Auth2Problem.AUTH_SCOPE_CLOSED,assertIs<Auth3Transition.Rejected>(f.execute(s,Auth3Payload.RotateDelegatedCredential(delegation,workerKey,0,0,1,aid(50),zero),work=work)).problem)
        val revoked=assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.RevokeDelegation(delegation,0),ownerKey,work)).candidate
        assertEquals(Auth2Problem.AUTH_ALREADY_REVOKED,assertIs<Auth3Transition.Rejected>(f.execute(revoked,Auth3Payload.RotateDelegatedCredential(delegation,workerKey,0,1,1,aid(50),zero),work=work)).problem)
        assertIs<Auth3Transition.Success>(f.execute(revoked,Auth3Payload.RevokeDelegatedCredential(delegation,workerKey,0,1),work=work))
    }
    @Test fun ownerLockoutRequiresProjectNotControllerKey() {
        val f=Fixture(); val ctl=aid(60); val ctlKey=aid(61)
        val rs=f.baseRecords+Auth2Record.Controller(ControllerBinding(ctl,project,owner))+Auth2Record.Key(scopedKey(ctlKey,owner,CredentialScope.Controller(ctl)))
        val s=f.state(rs)
        token(ownerKey) { assertEquals(Auth2Problem.AUTH_OWNER_LOCKOUT,assertIs<Auth2LegacyTransition.Rejected>(s.executeLegacy(it,AuthCommand(aid(30),project,AuthPayload.RevokeCredential(ownerKey,0)))).problem) }
        assertFailsWith<CodecFault> { f.state(rs.filterNot { it is Auth2Record.Key && it.value.credential.id==ownerKey }) }
        val second=f.state(rs+Auth2Record.Key(scopedKey(aid(62),owner,CredentialScope.Project)))
        val done=token(ownerKey) { assertIs<Auth2LegacyTransition.Success>(second.executeLegacy(it,AuthCommand(aid(30),project,AuthPayload.RevokeCredential(ownerKey,0)))).candidate }
        token(ownerKey) { assertNull(done.authenticate(it,project)) }
    }
    @Test fun legacyGrantProtectionAndKnowledgeRevocationDoNotChangeWorkGrant() {
        val f=Fixture(); val s=f.registered(); val e=Enrollment(worker,project)
        token(ownerKey) { key ->
            val digest=grantDigest(project,worker,setOf(Capability.READ))
            assertEquals(Auth2Problem.AUTH_FIXED_OWNER_POLICY,assertIs<Auth2LegacyTransition.Rejected>(s.executeLegacy(key,AuthCommand(aid(30),project,AuthPayload.ReplaceGrants(worker,0,digest,setOf(Capability.PLAN))))).problem)
            val revoked=assertIs<Auth2LegacyTransition.Success>(s.revokeAccess(key,project,worker)).candidate
            assertEquals(s.workGrants.getValue(e).generation,revoked.workGrants.getValue(e).generation)
            assertEquals(1,revoked.guards.getValue(e))
            assertIs<Auth2LegacyTransition.Success>(revoked.executeLegacy(key,AuthCommand(aid(31),project,AuthPayload.ReplaceGrants(worker,1,grantDigest(project,worker,null),setOf(Capability.READ)))))
        }
    }
    @TestFactory fun allAuthCounterOverflows() = listOf("work","credential","delegation").map { kind -> dynamicTest(kind) {
        val f=Fixture(); val s=f.registered(); val rs=s.records().map { r -> when {
            kind=="work" && r is Auth2Record.WorkGrants && r.value.enrollment.principalId==planner -> Auth2Record.WorkGrants(WorkGrant(r.value.enrollment,Long.MAX_VALUE,r.value.permissions))
            kind=="credential" && r is Auth2Record.Key && r.value.credential.id==workerKey -> Auth2Record.Key(r.value.copy(credential=r.value.credential.copy(generation=Long.MAX_VALUE)))
            kind=="delegation" && r is Auth2Record.DelegationRecord -> Auth2Record.DelegationRecord(r.value.copy(generation=Long.MAX_VALUE))
            else -> r
        } }
        val max=f.state(rs,s.policy,f.work(AssignmentPhase.ACTIVE,1,true))
        val p=when(kind) {
            "work" -> Auth3Payload.ReplaceWorkGrants(planner,Long.MAX_VALUE,workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptySet())
            "credential" -> Auth3Payload.RevokeDelegatedCredential(delegation,workerKey,Long.MAX_VALUE,0)
            else -> Auth3Payload.RevokeDelegation(delegation,Long.MAX_VALUE)
        }
        assertEquals(Auth2Problem.AUTH_GENERATION_EXHAUSTED,assertIs<Auth3Transition.Rejected>(f.execute(max,p,ownerKey.takeIf { kind=="work" } ?: plannerKey)).problem)
        assertEquals(rs.size,max.records().size)
    } }
}
