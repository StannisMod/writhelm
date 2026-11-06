// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import java.util.UUID

/** Actual native lifecycle custody must feed H1RuntimeFacts independently before an observation
 * can publish. Source/profile gaps refuse here; native interrupt/wait alone is NOT completion. */
internal interface ConnectedRebindLifecycle {
    fun stopAndReconcile(capture: CoordinationCapture,subject: WAssignmentSubject,intent: WorkKey.Id): List<WSource>
    fun preparePassiveAndReconcile(capture: CoordinationCapture,subject: WAssignmentSubject,intent: WorkKey.Id): List<WSource>
}
internal data class RebindEnrollment(val delegationId: UUID,val principalId: UUID,val credentialId: UUID,val verifier: Digest)
/** Previously authorized exact terms, not an engine-generated task revision or semantic merge. */
internal data class ExactRebindAuthorization(val origin: WAssignmentSubject,val task: WorkKey.TaskRevision,val manifest: WorkReference,
    val profile: WorkKey.Profile,val sourceCheckpoint: WorkReference,val intended: WExecutionIntent,val enrollment: RebindEnrollment,
    val decisions: List<WSource>,val consumerNotice: Utf8Content)

/** Engine-owned ordinary rebind/registration/passive binding, keeping same logical assignment. It
 * deliberately does NOT manufacture successor confirmation: actual assessor must judge retained
 * evidence applicability to the new exact subject before automatic start. */
internal class AuthorizedRebindDriver(private val app: H1Application,private val project: UUID,private val credential: ()->String,
    private val authorization: ExactRebindAuthorization,private val lifecycle: ConnectedRebindLifecycle,private val custody: LinuxConnectedCustody) {
    private fun stable(suffix: String)=UUID.nameUUIDFromBytes("rebind:${authorization.origin}:${authorization.task}:$suffix".toByteArray(Charsets.UTF_8))
    fun ownsAssignment(id: UUID)=id==authorization.origin.assignmentId
    fun needsRecovery(captured: CoordinationCapture,subject: WAssignmentSubject): Boolean {
        if(!ownsAssignment(subject.assignmentId))return false
        if(subject.generation==authorization.origin.generation)return true
        if(subject.generation!=authorization.origin.generation+1)return false
        val a=captured.work.assignment(subject.assignmentId)
        if(a.binding==null)return true
        return originalPairs(captured).any { pair ->captured.work.records.values.none { r ->
            (r.payload as? WConditionsPayload)?.value?.let { it.consumer==subject && pair.condition in it.supersedes }==true } }
    }
    fun originalPairs(captured: CoordinationCapture): List<WConditionPair> {
        val work=captured.work;val inspector=Model2Inspector(work)
        val originals=work.records.values.filter { (it.payload as? WConditionsPayload)?.value?.consumer==authorization.origin }
        if(originals.size>256)throw WorkFault(WorkProblem.NOT_READY)
        val superseded=originals.flatMap { (it.payload as WConditionsPayload).value.supersedes }.map { it.identity }.toSet()
        return originals.filter { it.identity !in superseded }.map { r ->
            val ref=WorkReference(r.identity,WorkCodec().digest(r));val confirmations=inspector.confirmationFrontier(ref)
            workRequire(confirmations.size==1 && (work.resolve(confirmations.single()).payload as WConditionConfirmationsPayload).value.decision=="resolved",WorkProblem.NOT_READY)
            WConditionPair(ref,confirmations.single())
        }
    }
    private fun capture()=app.coordinationCapture(credential(),project)
    private fun send(op: dev.writhelm.core.work.WorkOperation,label: String) {
        val command=WorkCommand(stable(label),project,op);val bytes=WorkCodec().encodeCommand(command)
        custody.retain(command.requestKey,"command",bytes)
        val result=app.execute(credential(),bytes)
        workRequire(result is CommandResult.Settled && result.receipt.outcome is LogicalOutcome.Success,WorkProblem.NOT_READY)
    }
    fun advance(subject: WAssignmentSubject,pairs: List<WConditionPair>) {
        workRequire(ownsAssignment(subject.assignmentId) && authorization.decisions.isNotEmpty() && authorization.consumerNotice.sizeBytes in 1..4096,WorkProblem.INVALID_SUBJECT)
        val oldConditions=pairs.map { capture().work.resolve(it.condition) }
        var captured=capture();var a=captured.work.assignment(subject.assignmentId)
        workRequire(a.executionGeneration in authorization.origin.generation..authorization.origin.generation+1,WorkProblem.INVALID_BINDING)
        if(a.executionGeneration==authorization.origin.generation) {
        workRequire(a.checkpoint==authorization.sourceCheckpoint,WorkProblem.INVALID_BINDING)
        if(a.state=="paused") {
            val intent=stable("stop-intent")
            send(CRequestNativeTransitionOperation(CRequestNativeTransition(subject.assignmentId,a.generation,intent,stable("stop-correlation"),"stop",null,emptyList())),"stop-request")
        }
        captured=capture();a=captured.work.assignment(subject.assignmentId)
        if(a.state=="stopping" || a.state=="unknown") {
            val intent=a.currentIntent ?: throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
            val observed=lifecycle.stopAndReconcile(captured,authorization.origin,intent)
            val i=(captured.work.record(WorkFamily.NATIVE_INTENTS,intent).payload as WNativeIntentsPayload).value
            workRequire(i.operation=="stop",WorkProblem.NATIVE_UNKNOWN)
            send(CObserveNativeTransitionOperation(CObserveNativeTransition(subject.assignmentId,a.generation,intent.id,i.generation,"confirmed",observed,emptyList())),"stop-confirmed")
        }
        captured=capture();a=captured.work.assignment(subject.assignmentId)
        workRequire(a.state=="stopped",WorkProblem.NATIVE_UNKNOWN)
        // Restart must reinspect the original known stop; canonical STOPPED is not physical proof.
        lifecycle.stopAndReconcile(captured,authorization.origin,WorkKey.Id(project,stable("stop-intent")))
        if(a.recovery==null)send(CRecordRecoveryOperation(CRecordRecovery(WorkKey.Id(project,stable("recovery")),a.generation,
            WRecoveries(authorization.origin,"bind-existing",authorization.decisions,emptyList(),a.requiredResources,emptyList()),emptyList())),"recovery")
        captured=capture();a=captured.work.assignment(subject.assignmentId)
        send(CRebindAssignmentOperation(CRebindAssignment(subject.assignmentId,a.generation,authorization.task,authorization.manifest,authorization.profile,
            authorization.sourceCheckpoint,requireNotNull(a.recovery),stable("prepare-intent"),stable("prepare-correlation"),emptyList(),authorization.intended)),"rebind")
        }
        captured=capture();a=captured.work.assignment(subject.assignmentId)
        val target=WAssignmentSubject(subject.assignmentId,a.executionGeneration)
        val enrollment=authorization.enrollment
        if(a.delegationId==null) {
        val register=Auth3Command(stable("register"),project,Auth3Payload.RegisterDelegation(enrollment.delegationId,
            DelegationBinding(captured.controller,project,enrollment.principalId,enrollment.credentialId,authorization.task.taskId,subject.assignmentId,
                authorization.intended.executionId,target.generation,DelegationTemplate.entries.single { it.tag==authorization.intended.template },a.reviewSubjects),
            a.generation,enrollment.verifier))
        val registerBytes=Auth3CommandCodec().encode(register);custody.retain(register.requestKey,"command",registerBytes)
        val registered=app.execute(credential(),registerBytes)
        workRequire(registered is CommandResult.Settled && registered.receipt.outcome is LogicalOutcome.Success,WorkProblem.INVALID_BINDING)
        } else workRequire(a.delegationId==enrollment.delegationId,WorkProblem.INVALID_BINDING)
        captured=capture();a=captured.work.assignment(subject.assignmentId)
        if(a.binding==null) {
        val prepare=a.currentIntent ?: throw WorkFault(WorkProblem.INVALID_BINDING)
        val observed=lifecycle.preparePassiveAndReconcile(captured,target,prepare)
        val nativeIntent=(captured.work.record(WorkFamily.NATIVE_INTENTS,prepare).payload as WNativeIntentsPayload).value
        send(CBindAssignmentOperation(CBindAssignment(subject.assignmentId,a.generation,prepare.id,nativeIntent.generation,authorization.intended.nativeHostId,
            authorization.intended.nativeSessionId,a.workspaceId,observed,emptyList())),"bind")
        }
        // Exact successor declarations keep all old conditions/evidence subjects unchanged.
        oldConditions.forEach { record ->
            captured=capture();val old=(record.payload as WConditionsPayload).value
            val key=WorkKey.Id(project,stable("condition:${(record.identity.key as WorkKey.Id).id}"))
            if(WorkRecordIdentity(WorkFamily.CONDITIONS,key) in captured.work.records)return@forEach
            val notice=authorization.consumerNotice
            val data=CRecordConditionData(authorization.task,target,old.responsibleTask,old.condition,old.assessor,old.requiredGateIds,old.agreement,
                CRecordConditionDataConsumerNotice(notice.digest,notice.sizeBytes.toLong()),old.governingPlan,
                listOf(WorkReference(record.identity,WorkCodec().digest(record))),authorization.decisions)
            send(CRecordConditionOperation(CRecordCondition(key,data,if(notice.digest in captured.work.bodies)emptyList() else listOf(CRecordConditionBodiesItem(notice.digest,notice.text())))),
                "condition:${key.id}")
        }
    }
}
