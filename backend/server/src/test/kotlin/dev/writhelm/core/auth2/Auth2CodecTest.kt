// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

internal fun ready(name: String): JsonValue = StrictJson.decode(Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_H1_READY_ROOT")),name)),JsonLimits(bytes=8*1024*1024,collection=4096,tokens=500000))
internal fun JsonValue.member(name: String)=(this as JObject).entries.getValue(name)

class Auth2CodecTest {
    private val codec=Auth3CommandCodec()
    @TestFactory fun frozenCommand3Goldens() = ready("goldens-v2.json").member("command3").list().filter {
        it.member("input").member("operation").string() in setOf("replace_work_grants","register_delegation","rotate_delegated_credential","revoke_delegated_credential","revoke_delegation")
    }.map { golden -> dynamicTest(golden.member("input").member("operation").string()) {
        val bytes=StrictJson.encode(golden.member("input")); val command=codec.decode(bytes)
        assertContentEquals(bytes,codec.encode(command))
        assertEquals(golden.member("expectedSha256").string(),codec.fingerprint(command).hex)
        assertEquals(codec.fingerprint(command),codec.fingerprint(command.copy(requestKey=aid(999))))
        assertEquals(codec.fingerprint(command),codec.fingerprint(codec.decode((" \n"+bytes.toString(Charsets.UTF_8).replace("controllerId","controller\\u0049d")).toByteArray())))
    } }
    @TestFactory fun retainedCommand1And2IndependentGoldens() = ready("goldens-v2.json").member("preservedFingerprint1and2").list().map { golden ->
        dynamicTest(golden.member("input").member("operation").string()) {
            val bytes=StrictJson.encode(golden.member("input")); val expected=golden.member("expectedSha256").string()
            val actual=if(golden.member("input").member("formatVersion").long()==1L) CommandCodec().let { it.fingerprint(it.decode(bytes)) } else AuthCommandCodec().let { it.fingerprint(it.decode(bytes)) }
            assertEquals(expected,actual.hex)
        }
    }
    @Test fun sevenRecordFamiliesCanonicalAndPathBound() {
        val f=Fixture(DelegationTemplate.REVIEWER); val c=Auth2RecordCodec(); val records=f.registered().records()
        assertEquals(7,records.map { c.identity(it).first }.distinct().size)
        records.forEach { record ->
            val bytes=c.encode(record); val path=c.path(record)
            assertContentEquals(bytes,c.encode(c.decode(bytes,path)))
            assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { c.decode(bytes,path+"x") }.problem)
            assertFailsWith<CodecFault> { c.decode((" "+bytes.toString(Charsets.UTF_8)).toByteArray(),path) }
            assertFailsWith<CodecFault> { c.decode(bytes.toString(Charsets.UTF_8).replace("\"formatVersion\":2","\"formatVersion\":1").toByteArray(),path) }
        }
        c.checkHeader(c.header()); assertFailsWith<CodecFault> { c.checkHeader(AuthRecordCodec().header()) }
    }
    @Test fun originalRecordPathsAndUnchangedPayloadGoldens() {
        val golden=StrictJson.decode(Files.readAllBytes(Path.of(requireNotNull(System.getenv("WRITHELM_H1_AUTH_READY_ROOT")),"goldens/auth-v2.json")))
        val c=Auth2RecordCodec()
        for(v in golden.member("vectors").list()) {
            val name=v.member("name").string()
            if(!name.startsWith("record-") || name=="record-delegations") continue // superseded singular subject shape
            val bytes=v.member("canonicalRecordUtf8").string().toByteArray(); val path=v.member("path").string()
            val r=c.decode(bytes,path); assertContentEquals(bytes,c.encode(r)); assertEquals(v.member("recordSha256").string(),Digest.of(bytes).hex)
        }
        for(v in golden.member("vectors").list().filter { it.member("name").string().startsWith("work-grant-") }) {
            assertEquals(v.member("sha256").string(),workGrantDigest(uuid(v.member("projectId")),uuid(v.member("principalId")),permissions(v.member("permissions"))).hex)
        }
    }
    @TestFactory fun strictAuth3NegativeMutations() : List<org.junit.jupiter.api.DynamicTest> {
        val bytes=codec.encode(Fixture().register()); val text=bytes.toString(Charsets.UTF_8)
        val bad=listOf(
            text.replace("\"formatVersion\":3","\"formatVersion\":2"),text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":-0"),
            text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":1.0"),text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":1e0"),
            text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":9223372036854775808"),text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":-1"),
            text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":null"),text.replace("\"expectedAssignmentGeneration\":0","\"expectedAssignmentGeneration\":\"0\""),
            text.replace("\"knowledgeCapabilities\":[\"READ\"]","\"knowledgeCapabilities\":[\"READ\",\"PROPOSE\"]"),
            text.replace("\"taskId\"","\"actor\""),text.replace("\"subjectResults\":[]","\"subjectResultId\":null"),
            text.replace("\"subjectResults\":[]","\"subjectResults\":[],\"subject\\u0052esults\":[]"),
            text.replace("\"binding\":{","\"binding\":{\"scope\":\"project\","),
            text.replace("\"payload\":{","\"payload\":{\"secret\":\"fake\","),text+"{}",text.replace("executor-v1","reviewer-v1"),
            text.replace("executor-v1","\\ud800"),text.replace("\"formatVersion\":3","\"formatVersion\":3,\"format\\u0056ersion\":3")
        ).map { it.toByteArray() }+listOf(byteArrayOf(0xc0.toByte(),0x80.toByte()),byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+bytes)
        return bad.mapIndexed { i,b -> dynamicTest("strict-$i") { assertFailsWith<CodecFault> { codec.decode(b) } } }
    }
    @Test fun commandSetCanonicalizationAndSequenceSensitivity() {
        val f=Fixture(); val a=f.command(Auth3Payload.ReplaceWorkGrants(planner,0,zero,listOf(WorkPermission.TASK_PLAN,WorkPermission.RESULT_ACCEPT)))
        val b=f.command(Auth3Payload.ReplaceWorkGrants(planner,0,zero,listOf(WorkPermission.RESULT_ACCEPT,WorkPermission.TASK_PLAN)))
        assertEquals(codec.fingerprint(a),codec.fingerprint(b))
        assertFailsWith<CodecFault> { codec.decode(codec.encode(a).toString(Charsets.UTF_8).replace("\"TASK_PLAN\"","\"TASK_PLAN\",\"TASK_PLAN\"").toByteArray()) }
        val reviewer=Fixture(DelegationTemplate.REVIEWER); val r=reviewer.register(); val p=r.payload as Auth3Payload.RegisterDelegation
        val original=p.binding; val reverse=DelegationBinding(original.controllerId,project,worker,workerKey,task,assignment,execution,0,original.template,original.subjectResults.reversed())
        assertNotEquals(codec.fingerprint(r),codec.fingerprint(r.copy(payload=p.copy(binding=reverse))))
    }
    @Test fun allByteAndParserAdmissionFenceposts() {
        val bytes=codec.encode(Fixture().register()); val n=bytes.size
        for(size in listOf(n,n+1)) assertNotNull(Auth3CommandCodec(auth2Admission().copy(bytes=size)).decode(bytes))
        assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { Auth3CommandCodec(auth2Admission().copy(bytes=n-1)).decode(bytes) }.problem)
        for(limits in listOf(auth2Admission().copy(depth=1),auth2Admission().copy(collection=1),auth2Admission().copy(tokens=1),auth2Admission().copy(stringBytes=35))) {
            assertFailsWith<CodecFault> { Auth3CommandCodec(limits).decode(bytes) }
            assertFailsWith<CodecFault> { Auth3CommandCodec(limits).encode(Fixture().register()) }
        }
    }
    @Test fun historicalGroupLargerThanNewAdmissionRemainsReadable() {
        val f=Fixture(DelegationTemplate.REVIEWER)
        val subjects=(1..256).map { ref(WorkFamily.RESULTS,1000+it) }
        val b=DelegationBinding(controller,project,worker,workerKey,task,assignment,execution,0,DelegationTemplate.REVIEWER,subjects)
        val r=Auth2Record.DelegationRecord(Delegation(delegation,b,0,CredentialStatus.ACTIVE))
        val c=Auth2RecordCodec(); val bytes=c.encode(r)
        assertNotNull(c.decode(bytes,c.path(r)))
        assertFailsWith<CodecFault> { Auth2RecordCodec(auth2Admission()).encode(r) }
        assertFailsWith<CodecFault> { codec.encode(f.command(Auth3Payload.RegisterDelegation(delegation,b,0,zero))) }
    }
}
