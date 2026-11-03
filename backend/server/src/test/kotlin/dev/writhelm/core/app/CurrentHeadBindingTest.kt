// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import kotlin.test.*
import org.junit.jupiter.api.Test

/** A competing, actual-reducer-derived transaction on disposable test data only. Not a second service
 * instance or qualified multi-writer mode. The service still owns the one publication barrier. */
class CurrentHeadBindingTest {
    private fun compete(git: NativeGit,h: GitOid,g: AppGenesis,rotate: Boolean) {
        val lines=git.call(listOf("ls-tree","-rz","--full-tree",h.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }
        val files=lines.associate { val f=it.split('\t'); f[1] to GitOid("sha1",f[0].substringAfterLast(' ')) }.toMutableMap()
        val codec=AuthRecordCodec(); val descriptors=arrayListOf<WriteDescriptor>()
        fun change(key: RecordKey,after: ByteArray?) {
            val before=files[key.path]?.let { git.readBlob(it) }
            descriptors.add(WriteDescriptor(key,before?.let(::sha),after?.let(::sha)))
            if(after==null)files.remove(key.path) else files[key.path]=git.blob(after)
        }
        val operation: String; val version: Int; val fingerprint: String; val request=id(9000)
        val outcome: LogicalOutcome.Success
        if(rotate) {
            val newId=id(30); val command=AuthCommand(request,project,AuthPayload.RotateCredential(plannerKey,0,newId,CredentialVerifier.digest(storeId,project,planner,newId,secret(30))))
            val result=assertIs<AuthTransition.Success>(g.authority.execute(ownerToken,command))
            operation=command.payload.operation; version=2; fingerprint=AuthCommandCodec().fingerprint(command).hex
            for(key in listOf(plannerKey,newId)) {
                val record=AuthRecord.CredentialRecord(result.next.credentials.getValue(key))
                change(RecordKey("auth","credentials",uuid(key)),codec.encode(record))
            }
            outcome=LogicalOutcome.Success(operation,obj("credentialId" to uuid(newId),"replacedCredentialId" to uuid(plannerKey)),false)
        } else {
            val command=CoreCommand(request,project,CommandPayload.Revoke(planner))
            val core=assertIs<WorkflowResult.Success<Transition<java.util.UUID>>>(g.workflow.revokeAccess(Actor(owner),project,planner)).value.state
            val result=assertIs<AuthTransition.Success>(g.authority.revokeAccess(ownerToken,project,planner))
            assertEquals(grantMap(corePolicy(core.snapshot())),grantMap(result.next.policy))
            val key=obj("principalId" to uuid(planner),"projectId" to uuid(project))
            change(RecordKey("core","grants",key),null)
            change(RecordKey("auth","grant-guards",key),codec.encode(AuthRecord.Guard(Enrollment(planner,project),1)))
            operation=command.payload.operation; version=1; fingerprint=CommandCodec().fingerprint(command).hex
            outcome=LogicalOutcome.Success(operation,uuid(planner),false)
        }
        val receipt=Receipt(storeId,dev.writhelm.core.store.CommandIdentity(owner,project,operation,request),version,fingerprint,h,ownerKey,outcome,descriptors.sortedBy { it.path })
        files[receipt.path]=git.blob(JournalCodec.encode(receipt))
        fun tree(prefix: String): GitOid {
            val names=files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
            val bytes=names.joinToString("") { name ->
                val path=prefix+name; val oid=files[path]
                if(oid==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "100644 blob ${oid.hex}\t$name\u0000"
            }.toByteArray(Charsets.US_ASCII)
            return git.oid(git.call(listOf("mktree","-z"),bytes).checked())
        }
        git.update(h,git.commit(tree(""),h)).checked()
    }
    @Test fun `CAS retry reauthenticates actual token after core grant revocation`() {
        val g=genesis(); var competed=false
        val driver=PublicationDriver { git,h,c -> if(!competed) { competed=true; compete(git,h,g,false) }; git.update(h,c) }
        AppFixture(initial=g,options=AppOptions(publication=driver)).use { f ->
            val r=revision(100); val key=id(200)
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Propose(r),k=key))
            assertEquals(WorkflowResult.Failure(WorkflowProblem.FORBIDDEN),f.app.readClause(plannerToken,r.x()))
            settled(f.auth(AuthPayload.ReplaceGrants(planner,1,grantDigest(project,planner,null),setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE))))
            assertFalse(settled(f.core(CommandPayload.Propose(r),k=key)).commandReplayed)
        }
    }
    @Test fun `CAS retry rejects rotated original token and new same principal token starts orphaned command`() {
        val g=genesis(); var competed=false
        val driver=PublicationDriver { git,h,c -> if(!competed) { competed=true; compete(git,h,g,true) }; git.update(h,c) }
        AppFixture(initial=g,options=AppOptions(publication=driver)).use { f ->
            val r=revision(100); val key=id(200)
            assertIs<CommandResult.Denied>(f.core(CommandPayload.Propose(r),k=key))
            val recovered=settled(f.core(CommandPayload.Propose(r),token(id(30)),key))
            assertFalse(recovered.commandReplayed); assertEquals(planner,recovered.receipt.identity.principalId); assertEquals(id(30),recovered.receipt.authenticatedByCredentialId)
        }
    }
}
