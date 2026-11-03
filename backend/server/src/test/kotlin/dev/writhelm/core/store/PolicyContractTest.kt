// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class PolicyContractTest {
    private fun directory()=storeTestDirectory("policy-test-")
    private fun ports()=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },"policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_-> },{ _,_-> })
    private fun genesis()=listOf(StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))),StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
    private fun access()=CommandAccess { _,_-> AuthenticatedActor(id(3),id(4)) }
    private fun rejecting()=CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) }
    @Test fun `policy canonical strict version and positive bounded limits`() {
        val p=AdmissionPolicy(); assertEquals(p,AdmissionPolicy.decode(p.bytes()))
        assertFails { AdmissionPolicy(bodyBytes=0) }
        assertFails { AdmissionPolicy(receiptBytes=4*1024*1024+1) }
        assertFails { AdmissionPolicy.decode(p.bytes().toString(Charsets.UTF_8).replace("\"formatVersion\":1","\"formatVersion\":2").toByteArray()) }
        assertFails { AdmissionPolicy.decode(p.bytes().toString(Charsets.UTF_8).replace("\"bodyBytes\":","\"unknown\":0,\"bodyBytes\":").toByteArray()) }
    }
    @Test fun `genesis policy stored immutable and command cap measured on presented input`() {
        val dir=directory(); val command=authCommand(); val n=command.presentedBytes
        val policy=AdmissionPolicy(authCommandBytes=n)
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),admission=policy).use { s ->
            assertContentEquals(policy.bytes(),s.read(id(2),ReadAccess { _,_-> true }) { it.file("policy/admission.json")!! })
            assertIs<CommandResult.Settled>(s.execute(command,access(),rejecting()))
            val padded=StoreCommand.decode(command.bytes()+" ".toByteArray())
            // Canonical bytes are equal, but admission still counts the original presented length.
            val tight=AdmissionPolicy(authCommandBytes=command.bytes().size)
            GitCommandStore.initialize(directory(),id(1),genesis(),ports(),admission=tight).use { t ->
                assertEquals(StoreProblem.ADMISSION,(t.execute(padded,access(),rejecting()) as CommandResult.Refused).problem)
                assertFalse((t.execute(command,access(),rejecting()) as CommandResult.Settled).commandReplayed)
            }
        }
        GitCommandStore.open(dir,id(1),ports()).close()
    }
    @Test fun `receipt budget refusal creates no durable terminal outcome`() {
        val dir=directory()
        GitCommandStore.initialize(dir,id(1),genesis(),ports(),admission=AdmissionPolicy(receiptBytes=1)).use { s ->
            val result=s.execute(authCommand(),access(),rejecting()) as CommandResult.Refused
            assertEquals(StoreProblem.ADMISSION,result.problem)
            assertEquals(0,s.read(id(2),ReadAccess { _,_-> true }) { snapshot -> snapshot.entries.keys.count { it.startsWith("commands/receipts/") } })
        }
    }
    @Test fun `auth command depth and string budgets apply before publication`() {
        val value=authCommand().bytes().toString(Charsets.UTF_8)
        assertFails { StoreCommand.decode(value.replace("\"expectedGeneration\":0","\"expectedGeneration\":0,\"unexpected\":\"${"a".repeat(1025)}\"").toByteArray()) }
    }
}
