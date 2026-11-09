// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class NativePreparationContextTest {
    @Test fun canonicalQualifiedTextCannotReplaceUnavailableNativeSafety() {
        val f=ReviewAppFixture()
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),
            H1Options(H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })).use { app ->
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,WorkCodec().encodeCommand(f.reserveCommand()))).receipt.outcome)
            assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> {
                app.nativePreparationContext(plannerToken,project,f.controller,f.intended.nativeHostId,WAssignmentSubject(f.assignment,0))
            }.problem)
        }
    }
    @Test fun reservedPreparationIsCurrentScopedDispatchNotAWorkerOrOwnerFlag() {
        val f=ReviewAppFixture()
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),
            H1Options(H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) },
                nativeSafety=NativeRuntimeSafety { _,subject ->subject==WAssignmentSubject(f.assignment,0) })).use { app ->
            val result=assertIs<CommandResult.Settled>(app.execute(plannerToken,WorkCodec().encodeCommand(f.reserveCommand())))
            assertIs<LogicalOutcome.Success>(result.receipt.outcome)
            val subject=WAssignmentSubject(f.assignment,0)
            fun read(who:String=plannerToken,host:java.util.UUID=f.intended.nativeHostId,s:WAssignmentSubject=subject)=
                app.nativePreparationContext(who,project,f.controller,host,s)
            val frame=read() as JObject
            assertEquals("reserved",((StrictJson.decode(frame.entries.getValue("assignmentJSON").string().toByteArray()) as JObject)
                .entries.getValue("payload") as JObject).entries.getValue("state").string())
            assertFailsWith<AppAccessFault> { read(ownerToken) }
            assertFailsWith<WorkFault> { read(host=id(59901)) }
            assertFailsWith<WorkFault> { read(s=WAssignmentSubject(f.assignment,1)) }
            val remove=Auth3Command(id(59902),project,Auth3Payload.ReplaceWorkGrants(planner,0,
                workGrantDigest(project,planner,WorkPermission.entries.toSet()),WorkPermission.entries.toSet()-WorkPermission.TASK_DISPATCH))
            assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(remove)))
            val before=app.inspectAccess(ownerToken).basis
            assertFailsWith<AppAccessFault> { read() }
            assertEquals(before,app.inspectAccess(ownerToken).basis)
        }
    }
}
