// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import kotlin.test.*

class Auth2GraphTest {
    @TestFactory fun invalidGraphs() = listOf("missing-work-grant","missing-guard","project-owner","privileged-worker",
        "worker-project-key","worker-controller","foreign-controller","scope-swap","orphan-lineage","duplicate-key","no-project-owner-key").map { mutation ->
        dynamicTest(mutation) {
            val f=Fixture(); val s=f.registered(); var records=s.records(); var policy=s.policy
            records=when(mutation) {
                "missing-work-grant" -> records.filterNot { it is Auth2Record.WorkGrants && it.value.enrollment.principalId==worker }
                "missing-guard" -> records.filterNot { it is Auth2Record.Guard && it.enrollment.principalId==worker }
                "project-owner" -> records.map { if(it is Auth2Record.Project) Auth2Record.Project(ProjectBinding(project,worker)) else it }
                "privileged-worker" -> records.also { policy=AccessPolicy(s.policy.snapshotGrants().filterNot { it.principalId==worker }+Grant(worker,project,setOf(Capability.PLAN))) }
                "worker-project-key" -> records.map { if(it is Auth2Record.Key && it.value.credential.id==workerKey) Auth2Record.Key(it.value.copy(scope=CredentialScope.Project)) else it }
                "worker-controller" -> records+Auth2Record.Controller(ControllerBinding(aid(88),project,worker))
                "foreign-controller" -> records.map { if(it is Auth2Record.Controller) Auth2Record.Controller(it.binding.copy(projectId=aid(99))) else it }
                "scope-swap" -> records.map { if(it is Auth2Record.Key && it.value.credential.id==workerKey) Auth2Record.Key(it.value.copy(scope=CredentialScope.Assignment(aid(99)))) else it }
                "orphan-lineage" -> records+Auth2Record.Key(scopedKey(aid(89),worker,CredentialScope.Assignment(delegation)))
                "duplicate-key" -> records+records.first()
                else -> records.filterNot { it is Auth2Record.Key && it.value.credential.id==ownerKey }
            }
            assertFailsWith<CodecFault> { f.state(records,policy,f.work(AssignmentPhase.ACTIVE,1,true)) }
        }
    }
    @Test fun restoreExportCompleteAndFailureLeavesSourceIntact() {
        val f=Fixture(); val s=f.registered(); val codec=Auth2SnapshotCodec(); val exported=codec.export(s).toList()
        val restored=codec.restore(store,"H",exported.asSequence(),s.policy,f.work(AssignmentPhase.ACTIVE,1,true))
        val roundtrip=codec.export(restored).toList()
        assertEquals(exported.map { it.first },roundtrip.map { it.first })
        exported.zip(roundtrip).forEach { (a,b) -> assertContentEquals(a.second,b.second) }
        for(entries in listOf(exported.drop(1),exported+exported.last(),exported+ ("auth/unknown.json" to byteArrayOf()))) {
            assertFailsWith<CodecFault> { codec.restore(store,"H",entries.asSequence(),s.policy,f.work(AssignmentPhase.ACTIVE,1,true)) }
        }
        val invalid=exported.map { (path,bytes) -> path to if(path=="auth/format.json") AuthRecordCodec().header() else bytes }
        assertFailsWith<CodecFault> { codec.restore(store,"H",invalid.asSequence(),s.policy,f.work(AssignmentPhase.ACTIVE,1,true)) }
        assertEquals(exported.size,codec.export(s).count())
    }
    @Test fun recordBudgetFencepostsAndRotationRefuseGrowthWithoutPartialState() {
        val f=Fixture(); val s=f.state(); val base=s.records()
        val full=base+(1..8192-base.size).map { Auth2Record.Principal(aid(10000+it)) }
        assertNotNull(f.state(full.dropLast(1))); val atLimit=f.state(full)
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { f.state(full+Auth2Record.Principal(aid(999999))) }.problem)
        token(ownerKey) { key ->
            assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> {
                atLimit.executeLegacy(key,AuthCommand(aid(30),project,AuthPayload.RotateCredential(ownerKey,0,aid(90),zero)))
            }.problem)
            assertNotNull(atLimit.authenticate(key,project))
        }
        token(plannerKey) { key -> assertFailsWith<CodecFault> { atLimit.execute(key,f.register(),f.work()) } }
        assertEquals(8192,atLimit.records().size); assertFalse(worker in atLimit.principals)
    }
    @Test fun closedOutcomeAndStatusCodecsRejectExtraOrWrongVariants() {
        val f=Fixture(); val s=f.registered(); val c=Auth2ReadCodec()
        for(op in Auth2ReadOperation.entries) {
            val subject=when(op) { Auth2ReadOperation.CREDENTIAL_STATUS -> workerKey; Auth2ReadOperation.WORK_GRANT -> worker; Auth2ReadOperation.DELEGATION_STATUS -> delegation }
            val read=Auth2Read(project,op,subject)
            val result=token(ownerKey) { s.readStatus(it,read) }; val bytes=c.encodeResult(result)
            assertContentEquals(bytes,c.encodeResult(c.decodeResult(op,bytes)))
            val extra=bytes.toString(Charsets.UTF_8).replaceFirst("{","{\"verifierDigest\":\"forbidden\",").toByteArray()
            assertFailsWith<CodecFault> { c.decodeResult(op,extra) }
            assertFailsWith<CodecFault> { c.decode(c.encode(read).toString(Charsets.UTF_8).replace("\"subject\":{","\"subject\":{\"actor\":\"owner\",").toByteArray()) }
        }
        for(problem in Auth2Problem.entries) {
            val bytes=Auth3OutcomeCodec.encode(Auth3Transition.Rejected(problem))
            assertEquals(Auth3Outcome.Rejected(problem),Auth3OutcomeCodec.decode(bytes))
        }
        val success=token(plannerKey) { assertIs<Auth3Transition.Success>(f.state().execute(it,f.register(),f.work())) }
        val bytes=Auth3OutcomeCodec.encode(success)
        assertEquals(Auth3Outcome.Success("register_delegation",delegation,null),Auth3OutcomeCodec.decode(bytes))
        for(text in listOf(bytes.toString(Charsets.UTF_8).replace("false","true"),bytes.toString(Charsets.UTF_8).replace("register_delegation","rotate_delegated_credential"),bytes.toString(Charsets.UTF_8).replace("register_delegation","grant_everything"))) {
            assertFailsWith<CodecFault> { Auth3OutcomeCodec.decode(text.toByteArray()) }
        }
    }
    @Test fun counterMaxMinusOneSucceedsWithoutWrapping() {
        val f=Fixture(); val s=f.registered(); val rs=s.records().map { r -> when(r) {
            is Auth2Record.DelegationRecord -> Auth2Record.DelegationRecord(r.value.copy(generation=Long.MAX_VALUE-1))
            else -> r
        } }
        val near=f.state(rs,s.policy,f.work(AssignmentPhase.ACTIVE,1,true))
        val t=assertIs<Auth3Transition.Success>(f.execute(near,Auth3Payload.RevokeDelegation(delegation,Long.MAX_VALUE-1),ownerKey))
        assertEquals(Long.MAX_VALUE,t.candidate.delegations.getValue(delegation).generation)
    }
}
