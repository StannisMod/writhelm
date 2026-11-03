// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import java.nio.file.Path
import java.util.UUID

/** Internal accepted-mechanism binding. No transport, signup, raw-path/patch API or secret persistence. */
internal class MechanismApp private constructor(private val binding: AppBinding): AutoCloseable {
    private lateinit var store: GitCommandStore
    private val reduction=AppReduction(binding)
    private class Request(val presented: String,val command: StoreCommand) {
        var plan: AppPlan?=null
        override fun toString()="Request(redacted)"
    }
    private var active: Request?=null
    private fun ports()=StorePorts(binding.namespaceValidators(),{ h,c,actor,evaluation ->
        val request=active ?: fail(StoreProblem.INTEGRITY)
        integrity(c===request.command)
        val expected=reduction.evaluate(h,c,request.presented,actor)
        integrity(StrictJson.encode(expected.evaluation.outcome.json()).contentEquals(StrictJson.encode(evaluation.outcome.json())))
        val actual=evaluation.changes.sortedBy { it.key.path }; val wanted=expected.evaluation.changes.sortedBy { it.key.path }
        integrity(actual.map { it.key.path }==wanted.map { it.key.path })
        actual.zip(wanted).forEach { (a,b)->
            val x=a.bytes(); val y=b.bytes(); integrity(if(x==null)y==null else y!=null && x.contentEquals(y))
        }
        request.plan=expected
    },binding::historical,binding.options.fileRead)

    @Synchronized fun execute(presented: String,bytes: ByteArray): CommandResult {
        val command=StoreCommand.decode(bytes)
        reduction.admit(command)
        check(active==null)
        val request=Request(presented,command); active=request
        try {
            val result=store.execute(command,CommandAccess { h,c ->
                binding.load(h).authority.authorize(presented,c.projectId,c.operation)?.let { AuthenticatedActor(it.actor.principalId,it.credentialId) }
            },CommandEvaluator { h,c,actor -> reduction.evaluate(h,c,presented,actor).evaluation })
            if(result is CommandResult.Settled && result.publication!=null) {
                val plan=request.plan ?: fail(StoreProblem.INTEGRITY)
                integrity(plan.basis==result.receipt.validatedAt)
                integrity(result.receipt.fingerprint==command.fingerprint && result.receipt.identity.principalId==plan.actor.principalId && result.receipt.authenticatedByCredentialId==plan.actor.credentialId)
                integrity(StrictJson.encode(result.receipt.outcome.json()).contentEquals(StrictJson.encode(plan.evaluation.outcome.json())))
                // Only a confirmed store publication of THIS exact reducer/validated delta can carry
                // state forward. Unknown outcomes, orphan candidates and bare OID labels never do.
                binding.confirmed(result.publication,plan.next)
            }
            return if(result is CommandResult.Refused && result.problem==StoreProblem.DENIED)CommandResult.Denied else result
        } finally { active=null }
    }
    private fun <T> read(presented: String,project: UUID,resolve: (BoundState,Actor)->WorkflowResult<T>): WorkflowResult<T> {
        var actor: Actor?=null
        return try {
            store.read(project,ReadAccess { h,p ->
                val access=binding.load(h).authority.authorize(presented,p,"read"); actor=access?.actor; access!=null
            }) { h -> resolve(binding.load(h),actor ?: fail(StoreProblem.DENIED)) }
        } catch(e: StoreFault) { if(e.problem==StoreProblem.DENIED)WorkflowResult.Failure(WorkflowProblem.FORBIDDEN) else throw e }
    }
    @Synchronized fun readClause(presented: String,reference: ClauseReference)=read(presented,reference.revision.document.projectId) { s,a -> s.core.readClause(a,reference) }
    @Synchronized fun readManifest(presented: String,project: UUID,id: UUID)=read(presented,project) { s,a -> s.core.readManifest(a,project,id) }
    @Synchronized fun readRequest(presented: String,project: UUID,id: UUID)=read(presented,project) { s,a -> s.core.readRequest(a,project,id) }
    @Synchronized fun readResolution(presented: String,project: UUID,id: UUID)=read(presented,project) { s,a -> s.core.readResolution(a,project,id) }
    @Synchronized fun approvalTrace(presented: String,reference: KnowledgeReference)=read(presented,reference.revision.document.projectId) { s,a -> s.core.approvalTrace(a,reference) }
    @Synchronized override fun close()=store.close()
    companion object {
        fun initialize(directory: Path,genesis: AppGenesis,options: AppOptions=AppOptions()): MechanismApp {
            val binding=AppBinding(genesis.authority.storeId,options); val files=binding.genesis(genesis)
            val app=MechanismApp(binding)
            app.store=GitCommandStore.initialize(directory,binding.storeId,files,app.ports(),options.storeBounds,options.processBounds,options.publication,options.admission)
            return app
        }
        fun open(directory: Path,storeId: UUID,options: AppOptions=AppOptions()): MechanismApp {
            val app=MechanismApp(AppBinding(storeId,options))
            app.store=GitCommandStore.open(directory,storeId,app.ports(),options.storeBounds,options.processBounds,options.publication)
            return app
        }
    }
}
