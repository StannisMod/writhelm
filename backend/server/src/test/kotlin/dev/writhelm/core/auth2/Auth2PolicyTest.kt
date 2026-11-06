// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class Auth2PolicyTest {
    @TestFactory fun actualFrozenRoleStateMatrix() : List<org.junit.jupiter.api.DynamicTest> {
        val current=Path.of(requireNotNull(System.getenv("WRITHELM_COORDINATION_READY_ROOT")))
        val matrix=(StrictJson.decode(Files.readAllBytes(current.resolve("operation-role-state-matrix.json")),
            JsonLimits(bytes=8*1024*1024,collection=4096,tokens=500000)) as JObject).entries
        return DelegationTemplate.entries.flatMap { template -> AssignmentPhase.entries.flatMap { phase -> WorkOperation.entries.map { op ->
            dynamicTest("${template.tag}-${phase.name}-${op.tag}") {
                val row=matrix.getValue(op.tag)
                val allowed=template.tag in row.member("assignmentTemplates").list().map { it.string() }
                val evaluates=phase.name.lowercase() in row.member("assignmentNewEffectStates").list().map { it.string() }
                val f=Fixture(template); val s=f.registered(phase); val policy=Auth2Policy(s,f.work(phase,1,true))
                val admission=token(workerKey) { policy.authorizeWork(it,f.call(op)) }
                if(!allowed || phase==AssignmentPhase.RESERVED) assertNull(admission)
                else assertEquals(if(evaluates) AdmissionMode.EVALUATE else AdmissionMode.REPLAY_ONLY,assertNotNull(admission).mode)
            }
        } } }
    }
    @Test fun terminalAndReplacedMissesNeverReachEvaluatorAndExactProofRequired() {
        val f=Fixture()
        for(phase in listOf(AssignmentPhase.COMPLETED,AssignmentPhase.REPLACED,AssignmentPhase.UNKNOWN,AssignmentPhase.PAUSED)) {
            val s=f.registered(phase); val policy=Auth2Policy(s,f.work(phase,1,true)); var evaluates=0
            token(workerKey) { key ->
                val a=assertNotNull(policy.authorizeWork(key,f.call(WorkOperation.SUBMIT_RESULT)))
                fun apply(proof: RetainedScopeProof?): ReceiptResolution {
                    val result=resolveScopedReceipt(a,zero,proof)
                    if(result==ReceiptResolution.EVALUATE) evaluates++
                    return result
                }
                assertEquals(ReceiptResolution.DENIED_MISS,apply(null))
                val proof=RetainedScopeProof(a.identity,3,zero,f.binding)
                assertEquals(ReceiptResolution.MATCH,apply(proof)) // success or retained rejection, no evaluator distinction
                assertEquals(ReceiptResolution.IDENTITY_CONFLICT,apply(proof.copy(fingerprint=Digest.parse("1".repeat(64)))))
                assertEquals(ReceiptResolution.DENIED_PROOF,apply(proof.copy(binding=null)))
                assertEquals(ReceiptResolution.DENIED_PROOF,apply(proof.copy(identity=a.identity.copy(principalId=planner))))
                assertNull(policy.authorizeWork(key,f.call(WorkOperation.SUBMIT_RESULT).copy(assignment=AssignmentSubject(project,assignment,1))))
            }
            assertEquals(0,evaluates)
        }
    }
    @Test fun recaptureUsesSameCredentialAndExplicitRevocationDeniesEvenReplay() {
        val f=Fixture(); val s=f.registered(AssignmentPhase.COMPLETED); val work=f.work(AssignmentPhase.COMPLETED,1,true)
        token(workerKey) { key ->
            assertNotNull(Auth2Policy(s,work).authorizeWork(key,f.call(WorkOperation.SUBMIT_RESULT)))
            val revoked=assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.RevokeDelegation(delegation,0),ownerKey,work)).candidate
            assertNull(Auth2Policy(revoked,work).authorizeWork(key,f.call(WorkOperation.SUBMIT_RESULT)))
            val rotated=token(ownerKey) { owner -> assertIs<Auth2LegacyTransition.Success>(s.executeLegacy(owner,AuthCommand(aid(31),project,AuthPayload.RotateCredential(workerKey,0,aid(50),scopedKey(aid(50),worker,CredentialScope.Assignment(delegation)).credential.verifierDigest)))).candidate }
            assertNull(Auth2Policy(rotated,work).authorizeWork(key,f.call(WorkOperation.SUBMIT_RESULT)))
            token(aid(50)) { successor -> assertEquals(AdmissionMode.REPLAY_ONLY,Auth2Policy(rotated,work).authorizeWork(successor,f.call(WorkOperation.SUBMIT_RESULT))?.mode) }
        }
    }
    @Test fun ownPlanOnlyAndControllerContinuationDoesNotPromoteKnowledgePlan() {
        val f=Fixture(DelegationTemplate.REVIEWER); val s=f.registered(); val policy=Auth2Policy(s,f.work(AssignmentPhase.ACTIVE,1,true))
        token(workerKey) { key ->
            assertNotNull(policy.authorizeWork(key,f.call(WorkOperation.UPDATE_PLAN)))
            assertNull(policy.authorizeWork(key,f.call(WorkOperation.UPDATE_PLAN).copy(planOwner=null)))
            assertNull(policy.authorizeWork(key,f.call(WorkOperation.UPDATE_PLAN).copy(planOwner=AssignmentSubject(project,aid(99),0))))
            assertNull(policy.authorizeWork(key,WorkInvocation(project,WorkOperation.RECORD_CONTROLLER_CONTINUATION,aid(30),null,controller,null)))
        }
        val call=WorkInvocation(project,WorkOperation.RECORD_CONTROLLER_CONTINUATION,aid(30),null,controller,null)
        token(plannerKey) { key -> assertNotNull(policy.authorizeWork(key,call)); assertNull(policy.authorizeWork(key,call.copy(controllerId=aid(99)))) }
        token(ownerKey) { assertNull(policy.authorizeWork(it,call)) }
        val noTaskPlan=assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),setOf(WorkPermission.TASK_DISPATCH)),ownerKey)).candidate
        token(plannerKey) { assertNull(Auth2Policy(noTaskPlan,f.work(AssignmentPhase.ACTIVE,1,true)).authorizeWork(it,call)) }
    }
    @Test fun terminalReadsOnlyIntrinsicOwnResultNotProvenanceGraph() {
        val f=Fixture(); val result=ref(WorkFamily.RESULTS,50); val body=WorkBodyReference(zero,0)
        val own=OwnedResultFacts(result,worker,AssignmentSubject(project,assignment,0),execution,listOf(body))
        val resource=ProtectedResource.ImmutableWork(result); val ownBody=ProtectedResource.WorkBody(result,body)
        val taskRef=ProtectedResource.ImmutableWork(ref(WorkFamily.TASK_REVISIONS,20))
        val linkedResult=ProtectedResource.ImmutableWork(ref(WorkFamily.RESULTS,51))
        val closure=DerivedDisclosureClosure("H",project,f.binding,listOf(taskRef),listOf(taskRef),emptyList(),emptyList(),listOf(own))
        for(phase in listOf(AssignmentPhase.COMPLETED,AssignmentPhase.REPLACED)) {
            val s=f.registered(phase); val policy=Auth2Policy(s,f.work(phase,1,true))
            token(workerKey) { key ->
                fun read(op: String,vararg rs: ProtectedResource)=policy.authorizeDisclosure(key,DisclosureRequest(project,op,rs.map { DisclosureUnit(it,ContentApplicability.CURRENT) }),closure)
                assertTrue(read("read_results",resource)); assertTrue(read("read_work_body",ownBody))
                assertFalse(read("read_task_revisions",taskRef)); assertFalse(read("read_results",linkedResult))
                assertFalse(read("read_results",resource,taskRef)); assertFalse(read("read_work_body",ProtectedResource.WorkBody(ref(WorkFamily.RESULTS,51),body)))
                assertFalse(read("list_work_records",resource)); assertFalse(read("read_revision",resource))
                assertFalse(policy.authorizeDisclosure(key,DisclosureRequest(project,"read_results",listOf(DisclosureUnit(resource,ContentApplicability.WITHDRAWN))),closure))
            }
            val noRead=token(ownerKey) { assertIs<Auth2LegacyTransition.Success>(s.revokeAccess(it,project,worker)).candidate }
            token(workerKey) { key ->
                assertFalse(Auth2Policy(noRead,f.work(phase,1,true)).authorizeDisclosure(key,DisclosureRequest(project,"read_results",listOf(DisclosureUnit(resource,ContentApplicability.CURRENT))),closure))
                assertEquals(AdmissionMode.REPLAY_ONLY,Auth2Policy(noRead,f.work(phase,1,true)).authorizeWork(key,f.call(WorkOperation.SUBMIT_RESULT))?.mode)
            }
        }
    }
    @Test fun exactLegacyFragmentAllowedButNeighborBundleAndListingsDenyAsWhole() {
        val f=Fixture(); val s=f.registered(); val policy=Auth2Policy(s,f.work(AssignmentPhase.ACTIVE,1,true))
        val revision=RevisionKey(DocumentKey(project,aid(70)),aid(71))
        val clause=ProtectedResource.Knowledge(ClauseReference(revision,ClauseId("a"),zero,zero))
        val neighbor=ProtectedResource.Knowledge(ClauseReference(revision,ClauseId("b"),zero,zero))
        val full=ProtectedResource.Revision(revision,zero)
        val closure=DerivedDisclosureClosure("H",project,f.binding,emptyList(),listOf(clause),emptyList(),emptyList(),emptyList())
        token(workerKey) { key ->
            fun read(op: String,rs: List<ProtectedResource>)=policy.authorizeDisclosure(key,DisclosureRequest(project,op,rs.map { DisclosureUnit(it,ContentApplicability.CURRENT) }),closure)
            assertTrue(read("read_clause",listOf(clause)))
            assertFalse(read("read_revision",listOf(full)))
            assertFalse(read("read_review_bundle",listOf(clause,neighbor)))
            for(op in listOf("list_documents","list_manifests","list_revisions","list_projects","list_requests","read")) assertFalse(read(op,listOf(clause)))
            assertNull(policy.authorizeLegacyCommand(key,project,"pin_consumer",true))
        }
        token(plannerKey) { key ->
            assertNotNull(policy.authorizeLegacyCommand(key,project,"pin_consumer",true))
            assertNull(policy.authorizeLegacyCommand(key,project,"replace_work_grants",true))
            assertNull(policy.authorizeLegacyCommand(key,project,"resolve_approval",true))
        }
    }
    @Test fun statusReadsAreBoundedOwnerOrOwningControllerNotWorkerOrForeignController() {
        val f=Fixture(); val s=f.registered(); val codec=Auth2ReadCodec()
        for(op in Auth2ReadOperation.entries) {
            val id=when(op) { Auth2ReadOperation.CREDENTIAL_STATUS -> workerKey; Auth2ReadOperation.WORK_GRANT -> worker; Auth2ReadOperation.DELEGATION_STATUS -> delegation }
            val read=Auth2Read(project,op,id)
            assertEquals(read,codec.decode(codec.encode(read)))
            token(workerKey) { assertIs<Auth2ReadResult.Denied>(s.readStatus(it,read)) }
            token(ownerKey) { key ->
                val encoded=codec.encodeResult(s.readStatus(key,read)).toString(Charsets.UTF_8)
                assertFalse(encoded.contains("verifier")); assertFalse(encoded.contains("secret")); assertFalse(encoded.contains("wh1"))
            }
            token(plannerKey) { key ->
                val result=s.readStatus(key,read)
                if(op==Auth2ReadOperation.WORK_GRANT) assertIs<Auth2ReadResult.Denied>(result) else assertFalse(result is Auth2ReadResult.Denied)
                assertIs<Auth2ReadResult.Denied>(s.readStatus(key,read.copy(subjectId=aid(999))))
            }
        }
    }
    @Test fun wrongHeadProjectionRefusesRatherThanCachedScopeFallback() {
        val f=Fixture(); assertFailsWith<CodecFault> { Auth2Policy(f.state(),AuthWorkProjection("other-H",listOf(f.facts()))) }
    }
    @Test fun currentFullScopeSessionBindingSurvivesOnlySameScopeActiveRotation() {
        val f=Fixture(); val s=f.registered(); val work=f.work(AssignmentPhase.ACTIVE,1,true); val policy=Auth2Policy(s,work)
        val binding=token(workerKey) { assertNotNull(policy.sessionBinding(it,project)) }
        token(workerKey) { key ->
            assertTrue(policy.matchesSession(key,binding)); assertFalse(policy.matchesSession(key,binding.copy(scope=CredentialScope.Project)))
        }
        val next=assertIs<Auth3Transition.Success>(f.execute(s,Auth3Payload.RotateDelegatedCredential(delegation,workerKey,0,0,1,aid(50),scopedKey(aid(50),worker,CredentialScope.Assignment(delegation)).credential.verifierDigest))).candidate
        val current=Auth2Policy(next,work)
        token(workerKey) { assertFalse(current.matchesSession(it,binding)) }
        token(aid(50)) { assertTrue(current.matchesSession(it,binding)) }
        token(plannerKey) { assertFalse(current.matchesSession(it,binding)) }
    }
    @Test fun ownControllerContinuationReadsRequireCurrentReadAndCannotUseProjectScope() {
        val f=Fixture(); val s=f.state(); val policy=Auth2Policy(s,f.work())
        val reference=WorkReference(WorkRecordIdentity(WorkFamily.CONTROLLER_CONTINUATIONS,WorkKey.ControllerContinuation(project,controller,aid(80))),zero)
        val own=ProtectedResource.ImmutableWork(reference)
        val closure=DerivedDisclosureClosure("H",project,null,emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val request=DisclosureRequest(project,"read_controller_continuations",listOf(DisclosureUnit(own,ContentApplicability.CURRENT)),controller)
        token(plannerKey) { assertTrue(policy.authorizeDisclosure(it,request,closure)); assertFalse(policy.authorizeDisclosure(it,DisclosureRequest(project,"read_controller_continuations",request.units,aid(99)),closure)) }
        token(ownerKey) { assertFalse(policy.authorizeDisclosure(it,request,closure)) }
        val noRead=token(ownerKey) { assertIs<Auth2LegacyTransition.Success>(s.revokeAccess(it,project,planner)).candidate }
        token(plannerKey) { assertFalse(Auth2Policy(noRead,f.work()).authorizeDisclosure(it,request,closure)) }
    }
}
