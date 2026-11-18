// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class NativeControllerContextTest {
    @Test fun nativePlannerAdmissionIsCurrentBooleanNotPublicWorkGrantDisclosure() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use {app->
            fun admission()=app.nativeControllerPlanningAdmission(plannerToken,project,f.controller,f.intended.nativeHostId) as JObject
            val ready=admission()
            assertEquals(setOf("formatVersion","basis","controllerId","canContinue"),ready.entries.keys)
            assertEquals(JBoolean(true),ready.entries.getValue("canContinue"))
            val grantRead=Auth2ReadCodec().encode(Auth2Read(project,Auth2ReadOperation.WORK_GRANT,planner))
            assertFailsWith<AppAccessFault> {app.read2(plannerToken,grantRead)}
            assertFailsWith<AppAccessFault> {app.nativeControllerPlanningAdmission(token(f.workerKey),project,f.controller,f.intended.nativeHostId)}
            assertFailsWith<AppAccessFault> {app.nativeControllerPlanningAdmission(plannerToken,project,id(59911),f.intended.nativeHostId)}
            val revoke=Auth3Command(id(59912),project,Auth3Payload.ReplaceWorkGrants(planner,0,
                workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptySet()))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(revoke))).receipt.outcome)
            assertEquals(JBoolean(false),admission().entries.getValue("canContinue"))
            assertFailsWith<AppAccessFault> {app.read2(plannerToken,grantRead)}
            assertIs<CommandResult.Settled>(app.execute(ownerToken,CommandCodec().encode(CoreCommand(id(59913),project,CommandPayload.Revoke(planner)))))
            assertFailsWith<AppAccessFault> {admission()}
        }
    }
    @Test fun fullOwnFrontierAndCurrentReadRemainDistinctFromPlanningPermission() {
        val f=ReviewAppFixture()
        f.publishedActiveApp().use { app ->
            val frame=app.nativeControllerContext(plannerToken,project,f.controller,f.profile) as JObject
            val frontier=StrictJson.decode(frame.entries.getValue("frontierJSON").string().toByteArray()).list()
            assertEquals(1,frontier.size)
            val assignments=frontier.single().fields("task","assignments","obligationIds").getValue("assignments").list()
            assertEquals(f.assignment,uuid(assignments.single().fields("assignmentId","recordGeneration","executionGeneration").getValue("assignmentId")))
            assertFailsWith<AppAccessFault> { app.nativeControllerContext(token(f.workerKey),project,f.controller,f.profile) }
            assertFailsWith<AppAccessFault> { app.nativeControllerContext(plannerToken,project,id(59900),f.profile) }
            val revoke=Auth3Command(id(59901),project,Auth3Payload.ReplaceWorkGrants(planner,0,
                workGrantDigest(project,planner,WorkPermission.entries.toSet()),WorkPermission.entries.toSet()-WorkPermission.TASK_PLAN))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(revoke))).receipt.outcome)
            val readable=app.nativeControllerContext(plannerToken,project,f.controller,f.profile) as JObject
            assertEquals(JBoolean(false),readable.entries.getValue("nativeReady"))
            assertEquals(frame.entries.getValue("frontierJSON").string(),readable.entries.getValue("frontierJSON").string())
            val noRead=CoreCommand(id(59902),project,CommandPayload.Revoke(planner))
            assertIs<CommandResult.Settled>(app.execute(ownerToken,CommandCodec().encode(noRead)))
            assertFailsWith<AppAccessFault> { app.nativeControllerContext(plannerToken,project,f.controller,f.profile) }
        }
    }
}
