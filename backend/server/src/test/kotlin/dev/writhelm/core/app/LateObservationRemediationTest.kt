// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.CredentialStatus
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Qualified fencing/isolation are attributed fixtures, not physical native-process evidence. */
class LateObservationRemediationTest {
    private fun recovered(): ReviewAppFixture {
        val f=ReviewAppFixture();f.activateWithAttributedFixtureObservations()
        var sequence=23000
        fun apply(op: dev.writhelm.core.work.WorkOperation,facts: WorkExternalFacts=f.observations.facts) {
            val candidate=f.key(plannerKey) { f.bridge.work(f.base,WorkCommand(id(sequence++),project,op),it,H1ObservedFacts(f.head,facts,f.observations.qualifiedProfiles)) }
            assertIs<LogicalOutcome.Success>(candidate.evaluation.outcome);f.install(candidate)
        }
        val subject=WAssignmentSubject(f.assignment,0);val intent=id(23050);val correlation=id(23051)
        val execution=f.base.work.getValue(project).assignment(f.assignment).execution!!
        apply(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,4,intent,correlation,"stop",null,emptyList())))
        val unknown=WorkNativeObservation(subject,intent,correlation,"stop","unknown",listOf(f.source),execution,false,"active")
        apply(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,5,intent,0,"unknown",listOf(f.source),emptyList())),f.observations.facts.copy(native=mapOf(intent to unknown)))
        apply(CRecordRecoveryOperation(CRecordRecovery(WorkKey.Id(project,id(23052)),6,WRecoveries(subject,"revoked-isolated",listOf(f.source),listOf(f.resource),emptyList(),listOf("late stop unresolved")),emptyList())),f.observations.facts.copy(isolatedAssignments=setOf(subject)))
        return f
    }
    @Test fun `cold late stop keeps exact identity companions and isolated ownership`() {
        val f=recovered()
        RuntimeOwner.acquire(directory()).use { owner ->
            val git=NativeGit(owner);git.initialize();val b=ParityBoundary(f,git)
            val (command,candidate)=b.observe("confirmed","active")
            // All transformed candidates recompute descriptors/result digests, like the review probe.
            b.edge(command,candidate,{ b.assignment(it) { a -> a.copy(state="active") } },true)
            b.edge(command,candidate,{ b.intent(it) { i -> i.copy(expectedWorkspace=id(23990)) } },true)
            b.edge(command,candidate,{ b.intent(it) { i -> i.copy(correlationId=id(23991)) } },true)
            b.edge(command,candidate,{ b.intent(it) { i -> i.copy(expectedSession="ses_another") } },true)
            // Observe has no claim-release effect: even a validly encoded descriptor cannot add it.
            assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> {
                b.edge(command,candidate,{ changes ->
                    val record=b.base().work.getValue(project).record(WorkFamily.CLAIMS,f.resource)
                    val claim=(record.payload as WClaimsPayload).value
                    val freed=record.copy(payload=WClaimsPayload(claim.copy(generation=claim.generation+1,state="free",holder=null,workspaceId=null)))
                    changes+RecordChange(RecordKey("work","claims",StrictJson.decode(identityCodec.encodeKey(f.resource))),f.codec.encodeRecord(freed))
                })
            }.problem)
            b.edge(command,candidate)
            val state=b.base()
            assertEquals("stopped",state.work.getValue(project).assignment(f.assignment).state)
            assertNull(state.work.getValue(project).assignment(f.assignment).currentIntent)
            assertEquals(CredentialStatus.REVOKED,state.authority.delegations.getValue(f.delegation).status)
            assertEquals("isolated",(state.work.getValue(project).record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
        }
    }
    @Test fun `live late stop publishes after isolation but grants no new worker or execution authority`() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            var sequence=24000;val subject=WAssignmentSubject(f.assignment,0);val intent=id(24050);val correlation=id(24051)
            val execution=WExecution(f.intended.executionId,f.worker,f.workerKey,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace)
            fun command(op: dev.writhelm.core.work.WorkOperation)=WorkCommand(id(sequence++),project,op)
            fun publish(op: dev.writhelm.core.work.WorkOperation): CommandResult.Settled {
                val result=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(command(op))))
                assertIs<LogicalOutcome.Success>(result.receipt.outcome);return result
            }
            fun payload(family: WorkFamily,key: WorkKey,generation: Long): JObject {
                val exact=WorkRead.Exact(project,WorkRecordIdentity(family,key),null,generation)
                val data=(app.read2(plannerToken,f.codec.encodeRead(exact)) as JObject).entries.getValue("data")
                return data.fields("formatVersion","family","key","payload").getValue("payload") as JObject
            }
            publish(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,4,intent,correlation,"stop",null,emptyList())))
            f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(subject,intent,correlation,"stop","unknown",listOf(f.source),execution,false,"active")),isolatedAssignments=setOf(subject))
            publish(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,5,intent,0,"unknown",listOf(f.source),emptyList())))
            publish(CRecordRecoveryOperation(CRecordRecovery(WorkKey.Id(project,id(24052)),6,WRecoveries(subject,"revoked-isolated",listOf(f.source),listOf(f.resource),emptyList(),listOf("still no physical stop claim")),emptyList())))
            val assignment=WorkKey.Id(project,f.assignment)
            assertEquals("quarantined",payload(WorkFamily.ASSIGNMENTS,assignment,7).entries.getValue("state").string())
            val isolated=payload(WorkFamily.CLAIMS,f.resource,1)
            val h=app.inspectAccess(plannerToken).basis
            val observe=CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,7,intent,1,"confirmed",listOf(f.source),emptyList()))
            // Missing qualified fencing must still refuse without mutating or releasing claims.
            f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(subject,intent,correlation,"stop","confirmed",listOf(f.source),execution,false,"active")))
            val unfenced=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(command(observe))))
            // The closed observe refusal catalogue maps missing fencing to INVALID_BINDING.
            assertEquals("INVALID_BINDING",assertIs<LogicalOutcome.Rejected>(unfenced.receipt.outcome).code)
            assertTrue(unfenced.receipt.writes.isEmpty())
            f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(subject,intent,correlation,"stop","confirmed",listOf(f.source),execution,true,"active")))
            val lateCommand=command(observe)
            assertIs<CommandResult.Denied>(app.execute(ownerToken,f.codec.encodeCommand(lateCommand))) // not the matching controller
            val denied=app.execute(token(f.workerKey),f.codec.encodeCommand(lateCommand))
            assertIs<CommandResult.Denied>(denied)
            val success=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(lateCommand)))
            assertIs<LogicalOutcome.Success>(success.receipt.outcome)
            assertEquals(setOf("assignments","native-intents"),success.receipt.writes.map { it.record.family }.toSet())
            assertNotEquals(h,app.inspectAccess(plannerToken).basis)
            assertEquals("stopped",payload(WorkFamily.ASSIGNMENTS,assignment,8).entries.getValue("state").string())
            assertSameJson(isolated,payload(WorkFamily.CLAIMS,f.resource,1))
            val status=app.read2(ownerToken,Auth2ReadCodec().encode(Auth2Read(project,Auth2ReadOperation.DELEGATION_STATUS,f.delegation))) as JObject
            assertEquals("revoked",(status.entries.getValue("data") as JObject).entries.getValue("status").string())
            assertIs<CommandResult.Denied>(app.execute(token(f.workerKey),f.codec.encodeCommand(command(CUpdatePlanOperation(CUpdatePlan(WorkKey.Revision(project,id(24060),0),f.task,null,emptyList(),"no new work",null,emptyList(),subject))))))
            val startCommand=command(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,8,id(24061),id(24062),"start",null,emptyList())))
            val start=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(startCommand)))
            assertIs<LogicalOutcome.Rejected>(start.receipt.outcome);assertTrue(start.receipt.writes.isEmpty())
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(lateCommand))).commandReplayed)
            assertSameJson(isolated,payload(WorkFamily.CLAIMS,f.resource,1))
        }
    }
    private fun assertSameJson(a: JsonValue,b: JsonValue)=assertContentEquals(StrictJson.encode(a),StrictJson.encode(b))
}
