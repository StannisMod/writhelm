package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class NativeParityReviewTest {
    @Test fun `late confirmed stop after revoked isolated recovery retains isolation and must reconcile`() {
        val f=ReviewAppFixture();f.activateWithAttributedFixtureObservations()
        var sequence=22000
        fun apply(op: dev.writhelm.core.work.WorkOperation,facts: WorkExternalFacts=f.observations.facts) {
            val candidate=f.key(plannerKey) { f.bridge.work(f.base,WorkCommand(id(sequence++),project,op),it,H1ObservedFacts(f.head,facts,f.observations.qualifiedProfiles)) }
            assertIs<LogicalOutcome.Success>(candidate.evaluation.outcome)
            f.install(candidate)
        }
        val subject=WAssignmentSubject(f.assignment,0);val intent=id(22050);val correlation=id(22051)
        val execution=f.base.work.getValue(project).assignment(f.assignment).execution!!
        apply(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,4,intent,correlation,"stop",null,emptyList())))
        val unknown=WorkNativeObservation(subject,intent,correlation,"stop","unknown",listOf(f.source),execution,false,"active")
        apply(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,5,intent,0,"unknown",listOf(f.source),emptyList())),f.observations.facts.copy(native=mapOf(intent to unknown)))
        apply(CRecordRecoveryOperation(CRecordRecovery(WorkKey.Id(project,id(22052)),6,WRecoveries(subject,"revoked-isolated",listOf(f.source),listOf(f.resource),emptyList(),listOf("old stop acknowledgement not yet known")),emptyList())),f.observations.facts.copy(isolatedAssignments=setOf(subject)))
        assertEquals("quarantined",f.base.work.getValue(project).assignment(f.assignment).state)
        assertEquals(CredentialStatus.REVOKED,f.base.authority.delegations.getValue(f.delegation).status)
        RuntimeOwner.acquire(directory()).use { owner ->
            val git=NativeGit(owner);git.initialize();val b=ParityBoundary(f,git)
            val (command,candidate)=b.observe("confirmed","active")
            assertIs<LogicalOutcome.Success>(candidate.evaluation.outcome,"actual CORE+APP reducer admits controller reconciliation")
            assertEquals("stopped",candidate.work.getValue(project).assignment(f.assignment).state)
            val claim=(candidate.work.getValue(project).record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value
            assertEquals("isolated",claim.state,"confirmation must not release isolated old-area claims")
            assertEquals(CredentialStatus.REVOKED,candidate.authority.delegations.getValue(f.delegation).status)
            b.edge(command,candidate)
        }
    }
}
