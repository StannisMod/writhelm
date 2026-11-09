// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Actual canonical enrollment/bind; external passive observer is explicit MOCK. Node separately
 * verifies actual owned Git/worktree bytes and original SDK-shaped receiver reconciliation. */
class NativePreparationFactsTest {
    @Test fun bindingRequiresMatchingPrivatePassiveObservationAndNeverStartsOrFencesWorker() {
        val f=ReviewAppFixture();var proof:JsonValue?=null
        var captured:H1BoundState?=null
        val delegate=H1RuntimeFacts { state,_ ->captured=state;H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) }
        val observer=NativePreparationFacts(delegate,NativePreparationProofControl { assignment,generation ->
            if(assignment==f.assignment && generation==0L)proof else null
        },"a".repeat(64))
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),H1Options(observer)).use { app ->
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,WorkCodec().encodeCommand(f.reserveCommand()))).receipt.outcome)
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,dev.writhelm.core.auth2.Auth3CommandCodec().encode(f.register()))).receipt.outcome)
            val state=app.coordinationCapture(plannerToken,project).work
            val snapshot=requireNotNull(state.task(f.task).base).code.single().copy(retainedLocatorId=id(59008))
            val taskJSON=identityCodec.encodeKey(f.task).decodeToString();val profileJSON=identityCodec.encodeKey(f.profile).decodeToString()
            val model=obj("providerID" to str("synthetic"),"id" to str("no-call"))
            val directory=str("/MOCK-owned-worker")
            val metadata=obj("writhelm" to obj("kind" to str("passive-preparation-v1"),"assignmentId" to uuid(f.assignment),"executionGeneration" to str("0"),
                "controllerId" to uuid(f.controller),"executionId" to uuid(f.intended.executionId),"workspaceId" to uuid(f.workspace),
                "taskJSON" to str(taskJSON),"profileKeyJSON" to str(profileJSON)))
            val native=obj("sessionID" to str(f.intended.nativeSessionId),"directory" to directory,"parentID" to JNull,"fork" to JNull,"revert" to JNull,
                "agent" to str("build"),"model" to model,"permissions" to JArray(listOf(obj("action" to str("*"),"resource" to str("*"),"effect" to str("deny")))),"metadata" to metadata)
            val snapshotJSON=StrictJson.encode(encodeWSnapshot(snapshot)).decodeToString()
            val job=obj("formatVersion" to num(1),"assignmentId" to uuid(f.assignment),"generation" to str("0"),"intentId" to uuid(f.intent),
                "workspaceId" to uuid(f.workspace),"intendedExecution" to encodeWExecutionIntent(f.intended),"taskJSON" to str(taskJSON),
                "profileJSON" to str(WorkCodec().encodeRecord(state.record(WorkFamily.PROFILES,f.profile),true).decodeToString()),
                "baseJSON" to str(StrictJson.encode(encodeWSnapshot(requireNotNull(state.task(f.task).base).code.single())).decodeToString()),
                "snapshotJSON" to str(snapshotJSON),"artifactLocatorId" to uuid(snapshot.retainedLocatorId),"epoch" to str("a".repeat(64)),
                "directory" to directory,"agent" to str("build"),"model" to model)
            fun witness(j:JsonValue=job,n:JsonValue=native)=obj("formatVersion" to num(1),"preparationJSON" to str(StrictJson.encode(j).decodeToString()),
                "snapshotJSON" to str(snapshotJSON),"contextCount" to num(0),"inboxCount" to num(0),"nativeRunning" to JBoolean(false),
                "nativeJSON" to str(StrictJson.encode(n).decodeToString()))
            var request=59010
            fun bind():CommandResult.Settled {
                val data=CBindAssignment(f.assignment,1,f.intent,0,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace,
                    listOf(WSourceVariant1Value(WSourceVariant1("artifact",snapshot))),emptyList())
                return assertIs<CommandResult.Settled>(app.execute(plannerToken,WorkCodec().encodeCommand(WorkCommand(id(request++),project,CBindAssignmentOperation(data)))))
            }
            assertIs<LogicalOutcome.Rejected>(bind().receipt.outcome)
            proof=witness(j=JObject(job.entries+("epoch" to str("b".repeat(64)))))
            assertIs<LogicalOutcome.Rejected>(bind().receipt.outcome)
            proof=witness(n=JObject(native.entries+("permissions" to JArray(listOf(obj("action" to str("*"),"resource" to str("*"),"effect" to str("allow")))))))
            assertIs<LogicalOutcome.Rejected>(bind().receipt.outcome)
            proof=witness()
            assertNotNull(observer.observe(requireNotNull(captured),project,CBindAssignment(f.assignment,1,f.intent,0,f.intended.nativeHostId,
                f.intended.nativeSessionId,f.workspace,listOf(WSourceVariant1Value(WSourceVariant1("artifact",snapshot))),emptyList())))
            assertIs<LogicalOutcome.Success>(bind().receipt.outcome)
            val bound=app.coordinationCapture(plannerToken,project).work.assignment(f.assignment)
            assertEquals("bound",bound.state);assertNull(bound.currentIntent)
            assertEquals(f.intended.nativeSessionId,requireNotNull(bound.execution).nativeSessionId)
        }
    }
}
