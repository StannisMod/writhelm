// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** REAL H1 authorization/publication; the external native source-proof adapter is explicit MOCK.
 * Node source producer/schema/custody/current READ have separate actual-I/O component tests. */
class NativeContextFactsTest {
    @Test fun sourceCutNeedsActualMatchingRetainedProofNotWorkerSuppliedDigestsOrFlags() {
        val f=ReviewAppFixture();f.activateWithAttributedFixtureObservations()
        val subject=WAssignmentSubject(f.assignment,0);val work=f.base.work.getValue(project)
        val binding=work.assignment(f.assignment).binding!!;val locator=id(50300)
        val packet=StrictJson.encode(JArray(NativeAssignmentPacket.render(f.base,project,subject).map {
            obj("key" to str(it.first),"value" to str(it.second)) }),2*1024*1024)
        val packetDigest=Digest.of(packet);val requestDigest=Digest.of("exact extraction instruction/draft".toByteArray())
        val messageDigest=Digest.of("PRIVATE_NATIVE_SOURCE_NOT_EXPORTED".toByteArray())
        val scope=obj("storeId" to uuid(storeId),"projectId" to uuid(project),"controllerId" to uuid(f.controller),
            "hostId" to uuid(f.intended.nativeHostId),"sessionID" to str(f.intended.nativeSessionId),"epoch" to str("a".repeat(64)),
            "scope" to str("assignment"),"assignmentId" to uuid(f.assignment),"executionGeneration" to str("0"),
            "bindingJSON" to str(identityCodec.encodeReference(binding).decodeToString()))
        val retained=obj("locatorId" to uuid(locator),"scope" to scope,"profileJSON" to str(identityCodec.encodeKey(f.profile).decodeToString()),
            "modelJSON" to str(StrictJson.encode(obj("providerID" to str("synthetic"),"id" to str("no-call"))).decodeToString()),
            "renderedRequestDigest" to digest(requestDigest),"packetDigest" to digest(packetDigest),
            "nativeMessages" to JArray(listOf(obj("messageId" to str("msg_native"),"digest" to digest(messageDigest)))))
        var proof: JsonValue?=retained
        val observer=NativeContextFacts(UnavailableH1RuntimeFacts,NativeSourceProofControl { proof },"a".repeat(64))
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),H1Options(observer)).use { app ->
            var sequence=50310
            fun publish(packet: Digest=packetDigest): CommandResult.Settled {
                val data=WSourceCuts(f.task,subject,f.intended.nativeHostId,f.intended.nativeSessionId,f.profile,1,
                    requestDigest,packet,locator,listOf(WSourceCutsNativeMessagesItem("msg_native",messageDigest)),emptyList(),emptyList())
                val command=WorkCommand(id(sequence++),project,CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(project,id(sequence++)),data,emptyList())))
                return assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),WorkCodec().encodeCommand(command)))
            }
            val accepted=publish();assertIs<LogicalOutcome.Success>(accepted.receipt.outcome)
            val stored=app.coordinationCapture(plannerToken,project).work.records.values.single { it.payload is WSourceCutsPayload }
            assertFalse(WorkCodec().encodeRecord(stored,true).decodeToString().contains("PRIVATE_NATIVE_SOURCE_NOT_EXPORTED"))
            proof=null;assertIs<LogicalOutcome.Rejected>(publish().receipt.outcome)
            proof=JObject(retained.entries+("scope" to JObject(scope.entries+("hostId" to uuid(id(59999))))))
            assertIs<LogicalOutcome.Rejected>(publish().receipt.outcome)
            proof=JObject(retained.entries+("modelJSON" to str("{\"providerID\":\"wrong\",\"id\":\"no-call\"}")))
            assertIs<LogicalOutcome.Rejected>(publish().receipt.outcome)
            proof=retained;assertIs<LogicalOutcome.Rejected>(publish(Digest.of("forged packet".toByteArray())).receipt.outcome)
            assertEquals(1,app.coordinationCapture(plannerToken,project).work.records.values.count { it.payload is WSourceCutsPayload })
        }
    }
}
