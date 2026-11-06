// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Real Git whole-effect mechanics with explicitly synthetic trusted semantic ports. */
class H1RegistrationTest {
    internal class Fixture {
        var boundary: (StoreSnapshot,StoreSnapshot,Receipt)->Unit={ _,_,_ -> } // Synthetic test integration only.
        val grant=obj("principalId" to uuid(id(3)),"projectId" to uuid(id(2)))
        val assignment=RecordKey("work","assignments",obj("projectId" to uuid(id(2)),"id" to uuid(id(13))))
        val execution=obj("controllerId" to uuid(id(11)),"executionId" to uuid(id(14)),"template" to str("executor-v1"),"nativeHostId" to uuid(id(23)),"nativeSessionId" to str("ses_synthetic"))
        val binding=obj("controllerId" to uuid(id(11)),"projectId" to uuid(id(2)),"principalId" to uuid(id(3)),"initialCredentialId" to uuid(id(4)),"taskId" to uuid(id(12)),"assignmentId" to uuid(id(13)),"executionId" to uuid(id(14)),"executionGeneration" to num(0),"template" to str("executor-v1"),"subjectResults" to JArray(emptyList()))
        val commandBytes=StrictJson.encode(obj("formatVersion" to num(3),"requestKey" to uuid(id(5)),"projectId" to uuid(id(2)),"operation" to str("register_delegation"),"payload" to obj("delegationId" to uuid(id(15)),"binding" to binding,"expectedAssignmentGeneration" to num(0),"verifierVersion" to num(1),"verifierDigest" to str("1".repeat(64)),"knowledgeCapabilities" to JArray(listOf(str("READ"))))))
        fun record(key: RecordKey,payload: JsonValue)=StrictJson.encode(obj("formatVersion" to num(if(key.namespace=="auth")2 else 1),"family" to str(key.family),"key" to key.key,"payload" to payload))
        fun assignmentPayload(delegation: JsonValue=JNull,generation: Int=0)=obj(
            "task" to obj("projectId" to uuid(id(2)),"taskId" to uuid(id(12)),"revision" to num(0)),
            "manifest" to obj("family" to str("input-manifests"),"key" to obj("projectId" to uuid(id(2)),"id" to uuid(id(30))),"digest" to str("0".repeat(64))),
            "profile" to obj("projectId" to uuid(id(2)),"profileId" to uuid(id(20)),"revision" to num(0)),
            "generation" to num(generation),"executionGeneration" to num(0),"execution" to JNull,"binding" to JNull,
            "workspaceId" to uuid(id(21)),"requiredResources" to JArray(listOf(obj("projectId" to uuid(id(2)),"resourceId" to uuid(id(22))))),
            "reviewSubjects" to JArray(emptyList()),"state" to str("reserved"),"currentIntent" to obj("projectId" to uuid(id(2)),"id" to uuid(id(25))),
            "checkpoint" to JNull,"recovery" to JNull,"intendedExecution" to execution,"delegationId" to delegation)
        val payloads=listOf(
            RecordKey("core","grants",grant) to obj("capabilities" to JArray(listOf(str("READ")))),
            RecordKey("auth","principals",uuid(id(3))) to obj(),
            RecordKey("auth","grant-guards",grant) to obj("generation" to num(0)),
            RecordKey("auth","work-grants",grant) to obj("generation" to num(0),"permissions" to JArray(emptyList())),
            RecordKey("auth","credentials",uuid(id(4))) to obj("storeId" to uuid(id(1)),"projectId" to uuid(id(2)),"principalId" to uuid(id(3)),"verifierVersion" to num(1),"verifierDigest" to str("1".repeat(64)),"status" to str("active"),"generation" to num(0),"replacedBy" to JNull,"scope" to obj("kind" to str("assignment"),"delegationId" to uuid(id(15)))),
            RecordKey("auth","delegations",uuid(id(15))) to obj("binding" to binding,"generation" to num(0),"status" to str("active")),
            assignment to assignmentPayload(uuid(id(15)),1))
        val bindings=H1PublicationBindings(
            canonicalize={ raw -> integrity(raw.contentEquals(commandBytes)); raw.copyOf() },
            authorize={ _,_ -> CurrentCommandScope.NewEffect(AuthenticatedActor(id(10),id(11))) },
            allowedWrites={ _,_ -> payloads.map { it.first }.toSet() },validateRecord={ _,_,_ -> },preflight={ _,_,_ -> },validateBoundary={ h,c,r -> boundary(h,c,r) })
        val ports=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(2),"profileVersion" to num(2))) { },"work" to NamespaceValidator(obj("formatVersion" to num(1),"modelVersion" to num(2))) { },"policy" to NamespaceValidator(obj("formatVersion" to num(2))) { }),{ _,_,_,_ -> },{ _,_ -> },h1=bindings)
        val genesis=listOf(StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),StoreFile("auth/format.json",StrictJson.encode(ports.namespaces.getValue("auth").header)),StoreFile("work/format.json",StrictJson.encode(ports.namespaces.getValue("work").header)),StoreFile("policy/format.json",StrictJson.encode(ports.namespaces.getValue("policy").header)),StoreFile(assignment.path,record(assignment,assignmentPayload())))
        val command=StoreCommand.decodeH1(commandBytes,bindings)
        val access=CommandAccess { _,_ -> error("no legacy access") }
        fun evaluation(values: List<Pair<RecordKey,JsonValue>> = payloads)=CommandEvaluator { _,_,_ -> Evaluation(LogicalOutcome.Success("register_delegation",uuid(id(15)),false),values.map { RecordChange(it.first,record(it.first,it.second)) }) }
        fun create()=GitCommandStore.initialize(storeTestDirectory("h1-registration-"),id(1),genesis,ports,admission=AdmissionPolicy(formatVersion=2))
    }

    @Test fun `registration publishes exactly six auth core additions and one reserved assignment advance atomically`() {
        val f=Fixture()
        f.create().use { s ->
            val result=assertIs<CommandResult.Settled>(s.execute(f.command,f.access,f.evaluation()))
            assertEquals(7,result.receipt.writes.size)
            assertEquals(6,result.receipt.writes.count { it.beforeDigest==null })
            val assignment=result.receipt.writes.single { it.record.namespace=="work" }
            assertEquals(f.assignment.path,assignment.path); assertNotNull(assignment.beforeDigest)
            f.payloads.forEach { (key,payload) -> assertContentEquals(f.record(key,payload),s.read(id(2),ReadAccess { _,_ -> true }) { it.file(key.path)!! }) }
            assertTrue(assertIs<CommandResult.Settled>(s.execute(f.command,f.access,CommandEvaluator { _,_,_ -> error("no repeat registration") })).commandReplayed)
        }
    }

    @Test fun `every partial enrollment and unrelated assignment edit refuses without durable partial state`() {
        val f=Fixture()
        f.create().use { s ->
            val original=s.read(id(2),ReadAccess { _,_ -> true }) { it.oid }
            f.payloads.indices.forEach { index ->
                assertIs<CommandResult.Refused>(s.execute(f.command,f.access,f.evaluation(f.payloads.filterIndexed { i,_ -> i!=index })),"omission $index")
                assertEquals(original,s.read(id(2),ReadAccess { _,_ -> true }) { it.oid })
            }
            val values=f.payloads.map { (key,value) -> if(key.namespace=="work")key to JObject((value as JObject).entries+("workspaceId" to uuid(id(99)))) else key to value }
            assertIs<CommandResult.Refused>(s.execute(f.command,f.access,f.evaluation(values)))
            assertEquals(original,s.read(id(2),ReadAccess { _,_ -> true }) { it.oid })
            assertIs<CommandResult.Settled>(s.execute(f.command,f.access,f.evaluation()))
        }
    }

    @Test fun `registration scope grant and generation coupling cannot be faked by valid descriptor hashes`() {
        val f=Fixture()
        f.create().use { s ->
            val mutations=listOf(
                "core" to ("capabilities" to JArray(listOf(str("MANAGE")))),
                "work-grants" to ("permissions" to JArray(listOf(str("TASK_DISPATCH")))),
                "credentials" to ("scope" to obj("kind" to str("project"))),
                "assignments" to ("generation" to num(2)))
            mutations.forEach { (family,change) ->
                val values=f.payloads.map { (key,value) -> if(key.family==family || key.namespace==family)key to JObject((value as JObject).entries+change) else key to value }
                assertIs<CommandResult.Refused>(s.execute(f.command,f.access,f.evaluation(values)),family)
            }
            assertIs<CommandResult.Settled>(s.execute(f.command,f.access,f.evaluation()))
        }
    }
}
