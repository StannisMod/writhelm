// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class AuthorityBindingTest {
    @Test fun `real self revocation after last attempt error enforces current access before recovery`() {
        val driver=PublicationDriver { git,h,c -> git.update(h,c).checked(); GitReply(1,byteArrayOf(),"injected completed error".toByteArray()) }
        AppFixture(initial=genesis(extraOwner=true),options=AppOptions(storeBounds=StoreBounds(attempts=1),publication=driver)).use { f ->
            val key=id(200); val command=AuthPayload.RevokeCredential(ownerKey,0)
            assertIs<CommandResult.Denied>(f.auth(command,k=key))
            val recovered=settled(f.auth(command,token(id(9)),key))
            assertTrue(recovered.commandReplayed); assertEquals(ownerKey,recovered.receipt.authenticatedByCredentialId)
        }
    }
    @Test fun `grant replacement revoke regrant ABA share core policy and journal transaction`() {
        AppFixture().use { f ->
            val caps=setOf(Capability.READ)
            val old=f.initial.authority.grantDigest(planner,project)
            val replacement=AuthPayload.ReplaceGrants(planner,0,old,caps); val key=id(201)
            val first=settled(f.auth(replacement,k=key))
            assertTrue(settled(f.auth(replacement,k=key)).commandReplayed)
            assertEquals(setOf("core","auth"),first.receipt.writes.map { it.record.namespace }.toSet())
            assertEquals(2,first.receipt.writes.size)
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Propose(revision(100))))
            val revoked=settled(f.core(CommandPayload.Revoke(planner),ownerToken)); assertEquals(2,revoked.receipt.writes.size)
            val regrant=settled(f.auth(AuthPayload.ReplaceGrants(planner,2,grantDigest(project,planner,null),caps))); assertEquals(2,regrant.receipt.writes.size)
            assertEquals(first.receipt.writes.single { it.record.namespace=="core" }.afterDigest,regrant.receipt.writes.single { it.record.namespace=="core" }.afterDigest)
            val stale=settled(f.auth(AuthPayload.ReplaceGrants(planner,1,grantDigest(project,planner,caps),setOf(Capability.PLAN))))
            assertEquals("AUTH_STALE_GRANT",(stale.receipt.outcome as LogicalOutcome.Rejected).code)
            val noChange=settled(f.auth(AuthPayload.ReplaceGrants(planner,3,grantDigest(project,planner,caps),caps)))
            assertEquals(1,noChange.receipt.writes.size); assertEquals("grant-guards",noChange.receipt.writes.single().record.family)
        }
    }
    @Test fun `rotation new credential recovers original principal receipt and old token is denied`() {
        val path=directory(); val r=revision(100); val key=id(200)
        AppFixture(path).use { f ->
            val original=settled(f.core(CommandPayload.Propose(r),k=key))
            val newId=id(30); val verifier=CredentialVerifier.digest(storeId,project,planner,newId,secret(30))
            val rotateKey=id(201)
            val rotate=settled(f.auth(AuthPayload.RotateCredential(plannerKey,0,newId,verifier),k=rotateKey))
            assertEquals(2,rotate.receipt.writes.size)
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Propose(r),plannerToken,key))
            val replay=settled(f.core(CommandPayload.Propose(r),token(newId),key)); assertTrue(replay.commandReplayed)
            assertEquals(original.receipt.authenticatedByCredentialId,replay.receipt.authenticatedByCredentialId)
            assertIs<CommandResult.Denied>(f.auth(AuthPayload.RotateCredential(plannerKey,0,newId,verifier),token(newId),rotateKey))
        }
        MechanismApp.open(path,storeId).use { app ->
            val replay=settled(app.execute(token(id(30)),CommandCodec().encode(CoreCommand(key,project,CommandPayload.Propose(r))))); assertTrue(replay.commandReplayed)
        }
    }
    @Test fun `owner anti lockout fixed capability and one successor rules are real domain refusals`() {
        AppFixture().use { f ->
            fun code(r: CommandResult)=(settled(r).receipt.outcome as LogicalOutcome.Rejected).code
            assertEquals("AUTH_OWNER_LOCKOUT",code(f.auth(AuthPayload.RevokeCredential(ownerKey,0))))
            assertEquals("AUTH_FIXED_OWNER_POLICY",code(f.auth(AuthPayload.ReplaceGrants(planner,0,f.initial.authority.grantDigest(planner,project),setOf(Capability.APPROVE)))))
            assertEquals("AUTH_FIXED_OWNER_POLICY",code(f.auth(AuthPayload.ReplaceGrants(owner,0,f.initial.authority.grantDigest(owner,project),setOf(Capability.READ)))))
            val newId=id(30); val verifier=CredentialVerifier.digest(storeId,project,planner,newId,secret(30))
            settled(f.auth(AuthPayload.RotateCredential(plannerKey,0,newId,verifier)))
            assertEquals("AUTH_ALREADY_REPLACED",code(f.auth(AuthPayload.RotateCredential(plannerKey,1,id(31),verifier))))
            assertEquals("AUTH_STALE_CREDENTIAL",code(f.auth(AuthPayload.RevokeCredential(newId,1))))
        }
    }
    @Test fun `self revocation success uses prior H and later retry denied while other owner key recovers`() {
        AppFixture(initial=genesis(extraOwner=true)).use { f ->
            val key=id(200); val command=AuthPayload.RevokeCredential(ownerKey,0)
            val first=settled(f.auth(command,k=key)); assertIs<LogicalOutcome.Success>(first.receipt.outcome)
            assertIs<CommandResult.Denied>(f.auth(command,k=key))
            val replay=settled(f.auth(command,token(id(9)),key)); assertTrue(replay.commandReplayed); assertEquals(ownerKey,replay.receipt.authenticatedByCredentialId)
        }
    }
    @Test fun `grant guard exhaustion is unavailable to advance but unrelated core command still works`() {
        AppFixture(initial=genesis(guard=Long.MAX_VALUE)).use { f ->
            val result=settled(f.auth(AuthPayload.ReplaceGrants(planner,Long.MAX_VALUE,f.initial.authority.grantDigest(planner,project),setOf(Capability.READ))))
            assertEquals("AUTH_GENERATION_EXHAUSTED",(result.receipt.outcome as LogicalOutcome.Rejected).code)
            f.propose(revision(100))
        }
    }
}
