// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.UUID

internal data class ConnectedEnvelope(val continuation: WorkKey.Id,val assignment: WAssignmentSubject,val binding: WorkReference,
    val nativeHostId: UUID,val nativeSessionId: String,val messageId: String,val text: String) {
    fun bytes(): ByteArray=StrictJson.encode(obj("formatVersion" to num(1),"continuation" to StrictJson.decode(identityCodec.encodeKey(continuation)),
        "assignment" to encodeWAssignmentSubject(assignment),"binding" to StrictJson.decode(identityCodec.encodeReference(binding)),
        "nativeHostId" to uuid(nativeHostId),"nativeSessionId" to str(nativeSessionId),"nativeMessageId" to str(messageId),"text" to str(text),
        "metadata" to obj("continuationId" to uuid(continuation.id),"assignmentId" to uuid(assignment.assignmentId),"executionGeneration" to str(assignment.generation.toString())),
        "delivery" to str("steer"),"resume" to JBoolean(true)),16384)
}
/** Current canonical native binding, independent of any prior connected delivery. */
internal data class ConnectedBindingTarget(val assignment: WAssignmentSubject,val binding: WorkReference,
    val nativeHostId: UUID,val nativeSessionId: String)
internal sealed interface NativeConnectedOutcome {
    data object MatchedWake: NativeConnectedOutcome
    data object Unknown: NativeConnectedOutcome
    data object Conflict: NativeConnectedOutcome
    /** Qualified absence AND original-call quiescence, never just an empty inbox read. */
    data object NotPerformed: NativeConnectedOutcome
}
internal interface ConnectedNativeControl {
    /** Must establish safe work-area access, including known outstanding tool/effect surfaces.
     * Native idle/metadata alone is insufficient. Unsupported profiles return false. */
    fun hold(envelope: ConnectedEnvelope): Boolean
    /** Close NEXT managed admission only; NOT writer fencing or pause confirmation. */
    fun rehold(target: ConnectedBindingTarget)
    /** Dispatch/reconcile exact durable pause intent; no inferred confirmation or new wake. */
    fun pause(target: ConnectedBindingTarget,intent: WorkKey.Id)
    /** Retained exact native facts, if known. A closed gate/interrupt reply cannot supply fencing. */
    fun reconcilePause(target: ConnectedBindingTarget,intent: WorkKey.Id): WorkNativeObservation?
    fun deliver(envelope: ConnectedEnvelope): NativeConnectedOutcome
    fun reconcile(envelope: ConnectedEnvelope): NativeConnectedOutcome
    fun release(envelope: ConnectedEnvelope)
}

/** Small protected Linux artifact custody, not canonical authority. Store original exact operation
 * before effects; never overwrite an unresolved key. Missing custody holds, not digest possession.
 * All paths derive from UUIDs below an explicit controller-owned directory, never tool arguments. */
internal class LinuxConnectedCustody(private val directory: Path) {
    init {
        require(System.getProperty("os.name")=="Linux") { "unqualified custody platform" }
        require(directory.isAbsolute && !Files.isSymbolicLink(directory));Files.createDirectories(directory)
    }
    @Synchronized fun retain(id: UUID,suffix: String,bytes: ByteArray): Boolean {
        require(suffix in setOf("command","envelope","dispatch"));require(bytes.size<=2*1024*1024)
        val path=directory.resolve("$id.$suffix")
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(path) && Files.size(path)<=2*1024*1024 && Files.readAllBytes(path).contentEquals(bytes)) { "retained operation conflict" }
            return false
        }
        FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW).use { channel ->
            val buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true)
        }
        FileChannel.open(directory,StandardOpenOption.READ).use { it.force(true) }
        return true
    }
}

/** Actual service facts adapter: observations are produced by actual preparation/native reads on
 * recapture, not accepted from a command's outcome/body. Native implementation can be a deterministic
 * MOCK in joint tests; that attribution must never qualify an actual installed profile. */
internal class ConnectedRuntimeFacts(private val delegate: H1RuntimeFacts,private val preparation: ConnectedPreparation,
    private val native: ConnectedNativeControl,private val controller: Model2ControllerExecution,
    private val observedChecks: (WorkState)->Set<WorkReference>): H1RuntimeFacts {
    override fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts {
        val original=delegate.capture(state,command)
        val work=state.work[command.projectId] ?: WorkState.empty(command.projectId)
        val basis=WorkGitOid(state.basis.algorithm,state.basis.hex)
        val stages=linkedMapOf<UUID,Model2StageObservation>()
        val notPerformed=original.facts.model2.notPerformedAttempts.toMutableSet()
        val nativeFacts=original.facts.native.toMutableMap()
        val observe=command.operation as? CObserveContinuationOperation
        if(observe!=null) {
            val current=(work.record(WorkFamily.CONTINUATIONS,observe.value.key).payload as WContinuationsPayload).value
            if(current.stage=="delivery" && native.reconcile(envelope(work,observe.value.key,current))==NativeConnectedOutcome.NotPerformed)
                notPerformed.add(current.effect.attemptId)
        }
        if(observe!=null && observe.value.outcome=="confirmed") {
            val op=observe.value;val current=(work.record(WorkFamily.CONTINUATIONS,op.key).payload as WContinuationsPayload).value
            val capture=CoordinationCapture(state.basis,controller.controllerId,work)
            val plan=preparation.plan(capture,current.assignment)
            val envelope=envelope(work,op.key,current)
            val verified=if(op.stage=="environment")preparation.reconcile(plan) is PreparationOutcome.Matched
                else native.reconcile(envelope)==NativeConnectedOutcome.MatchedWake
            if(verified)stages[current.effect.attemptId]=Model2StageObservation(op.key,op.stage,current.effect.attemptId,current.effect.requestDigest,
                "confirmed",plan.before,plan.after,plan.basis,emptyList(),if(op.stage=="environment")null else Digest.of(envelope.bytes()),op.stage!="environment")
        }
        val nativeObserve=command.operation as? CObserveNativeTransitionOperation
        if(nativeObserve!=null && nativeObserve.value.outcome=="confirmed") {
            val op=nativeObserve.value
            val current=work.records.values.singleOrNull { (it.payload as? WContinuationsPayload)?.value?.let { c ->
                c.assignment.assignmentId==op.assignmentId && c.nativeIntent?.id==op.intentId && c.phase=="in-flight" && c.stage=="resume" }==true }
            if(current!=null) {
                val c=(current.payload as WContinuationsPayload).value
                if(native.reconcile(envelope(work,current.identity.key as WorkKey.Id,c))==NativeConnectedOutcome.MatchedWake) {
                    val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(command.projectId,op.intentId)).payload as WNativeIntentsPayload).value
                    nativeFacts[op.intentId]=WorkNativeObservation(c.assignment,op.intentId,intent.correlationId,intent.operation,"confirmed",op.observations,
                        work.binding(c.assignment).execution,false,if(c.mode=="unchanged-base")"paused" else "bound")
                }
            }
        }
        if(nativeObserve!=null) {
            val op=nativeObserve.value
            val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(command.projectId,op.intentId)).payload as WNativeIntentsPayload).value
            if(intent.operation=="pause") {
                native.reconcilePause(bindingTarget(work,intent.assignment),WorkKey.Id(command.projectId,op.intentId))?.let {
                    nativeFacts[op.intentId]=it
                }
            }
        }
        return H1ObservedFacts(state.basis,original.facts.copy(native=nativeFacts,model2=original.facts.model2.copy(basis=basis,
            controllers=mapOf(controller.controllerId to controller),observedEvidence=observedChecks(work),stages=stages,notPerformedAttempts=notPerformed)),original.qualifiedProfiles)
    }
    companion object {
        fun bindingTarget(work: WorkState,subject: WAssignmentSubject): ConnectedBindingTarget {
            val a=work.assignment(subject.assignmentId)
            workRequire(a.executionGeneration==subject.generation,WorkProblem.STALE_GENERATION)
            val binding=requireNotNull(a.binding);val execution=work.binding(subject).execution
            return ConnectedBindingTarget(subject,binding,execution.nativeHostId,execution.nativeSessionId)
        }
        fun envelope(work: WorkState,key: WorkKey.Id,c: WContinuations): ConnectedEnvelope {
            val e=work.binding(c.assignment).execution
            val body=work.bodies[c.notice().digest] ?: throw WorkFault(WorkProblem.MISSING_BODY)
            workRequire(body.sizeBytes==c.notice().byteLength && body.sizeBytes<=4096,WorkProblem.DIGEST_MISMATCH)
            return ConnectedEnvelope(key,c.assignment,c.binding,e.nativeHostId,e.nativeSessionId,c.nativeMessageId,body.text())
        }
    }
}

/** One bounded connected loop. Publication callbacks are hints; restart starts with a fresh canonical
 * scan. No planner relay/worker reassignment, second task store, model runtime or permissive port. */
internal class ConnectedWorkEngine(private val app: H1Application,private val project: UUID,private val credential: ()->String,
    private val preparation: ConnectedPreparation,private val native: ConnectedNativeControl,private val custody: LinuxConnectedCustody,
    private val authorizedRebind: AuthorizedRebindDriver?=null): AutoCloseable {
    private val codec=WorkCodec();private var running=false
    private val subscription=app.onPublished { tick() }
    val holds=linkedMapOf<WAssignmentSubject,String>()
    private fun stable(base: String)=UUID.nameUUIDFromBytes(base.toByteArray(Charsets.UTF_8))
    private fun capture()=app.coordinationCapture(credential(),project)
    private fun publish(op: dev.writhelm.core.work.WorkOperation,identity: String): CommandResult.Settled {
        val command=WorkCommand(stable(identity),project,op);val bytes=codec.encodeCommand(command)
        custody.retain(command.requestKey,"command",bytes)
        val result=app.execute(credential(),bytes)
        if(result !is CommandResult.Settled || result.receipt.outcome !is LogicalOutcome.Success)
            throw WorkFault(if(result is CommandResult.Settled)WorkProblem.NOT_READY else WorkProblem.NATIVE_UNKNOWN)
        return result
    }
    @Synchronized fun tick() {
        if(running)return
        running=true
        try {
            val initial=capture();val subjects=initial.work.records.values.filter { r ->(r.payload as? WAssignmentsPayload)?.value?.let { a ->
                a.intendedExecution.controllerId==initial.controller &&
                    (a.binding!=null && a.state in setOf("paused","bound","active","pausing","unknown") || authorizedRebind?.ownsAssignment((r.identity.key as WorkKey.Id).id)==true &&
                        a.state in setOf("reserved","stopping","stopped","unknown","bound","paused")) }==true }
            if(subjects.size>256)throw WorkFault(WorkProblem.NOT_READY)
            subjects.forEach { record ->
                val a=(record.payload as WAssignmentsPayload).value
                val subject=WAssignmentSubject((record.identity.key as WorkKey.Id).id,a.executionGeneration)
                try { advance(subject);holds.remove(subject) } catch(e: Exception) {
                    holds[subject]=e.message ?: "affected continuation held"
                    try { holdActive(subject) } catch(pause: Exception) { holds[subject]="${holds[subject]}; pause pending: ${pause.message}" }
                }
            }
        } finally { running=false }
    }
    private fun holdActive(subject: WAssignmentSubject) {
        val captured=capture();val work=captured.work;val a=work.assignment(subject.assignmentId)
        workRequire(a.executionGeneration==subject.generation,WorkProblem.STALE_GENERATION)
        if(a.state !in setOf("active","pausing","unknown"))return
        if(a.state=="active") {
            // A transport/release failure is not a new canonical blocker. Preserve original
            // active-before-release recovery when the actual current permit still holds.
            val permitted=try { app.continuationPermit(credential(),project,subject,requireNotNull(a.binding));true } catch(_: Exception) { false }
            if(permitted)return
        }
        val target=ConnectedRuntimeFacts.bindingTarget(work,subject)
        native.rehold(target) // before pause publication; never assert physical stop
        if(a.currentIntent!=null) {
            val i=(work.record(WorkFamily.NATIVE_INTENTS,a.currentIntent).payload as WNativeIntentsPayload).value
            if(i.operation=="pause")pauseAndReconcile(target,a.currentIntent)
            return // unknown effects keep their original intent and claims
        }
        if(a.state!="active")return
        val intent=WorkKey.Id(project,stable("${target.binding}:pause:${a.generation}"))
        publish(CRequestNativeTransitionOperation(CRequestNativeTransition(subject.assignmentId,a.generation,intent.id,
            stable("${intent.id}:correlation"),"pause",a.checkpoint,emptyList())),"${intent.id}:request")
        pauseAndReconcile(target,intent)
        // observe_native_transition still requires actual writer-fenced facts. Remain pausing
        // until that existing protocol supplies them; closing admission is not such a fact.
    }
    private fun pauseAndReconcile(target: ConnectedBindingTarget,intent: WorkKey.Id) {
        custody.retain(intent.id,"dispatch",StrictJson.encode(obj("assignment" to encodeWAssignmentSubject(target.assignment),
            "binding" to StrictJson.decode(identityCodec.encodeReference(target.binding)),"nativeHostId" to uuid(target.nativeHostId),
            "nativeSessionId" to str(target.nativeSessionId),"intent" to StrictJson.decode(identityCodec.encodeKey(intent)))))
        native.pause(target,intent)
        val proof=native.reconcilePause(target,intent) ?: return
        val work=capture().work;val a=work.assignment(target.assignment.assignmentId)
        val i=(work.record(WorkFamily.NATIVE_INTENTS,intent).payload as WNativeIntentsPayload).value
        workRequire(ConnectedRuntimeFacts.bindingTarget(work,target.assignment)==target && a.currentIntent==intent && i.assignment==target.assignment && i.operation=="pause" &&
            proof.assignment==i.assignment && proof.intentId==intent.id && proof.correlationId==i.correlationId &&
            proof.operation=="pause" && proof.outcome in setOf("unknown","confirmed") &&
            proof.execution==work.binding(target.assignment).execution && (proof.outcome!="confirmed" || proof.writersFenced),WorkProblem.NATIVE_UNKNOWN)
        if(i.outcome==proof.outcome)return
        publish(CObserveNativeTransitionOperation(CObserveNativeTransition(target.assignment.assignmentId,a.generation,intent.id,i.generation,
            proof.outcome,proof.sources,emptyList())),"${intent.id}:${i.generation}:${proof.outcome}")
    }
    private fun advance(subject: WAssignmentSubject) {
        repeat(24) {
            val captured=capture();val work=captured.work;val inspector=Model2Inspector(work)
            if(authorizedRebind?.needsRecovery(captured,subject)==true) {
                val original=authorizedRebind.originalPairs(captured)
                workRequire(original.isNotEmpty(),WorkProblem.NOT_READY)
                authorizedRebind.advance(subject,original);return
            }
            if(inspector.frontier(subject).isEmpty())return
            val pairs=inspector.resolvedPairs(subject)
            val assignment=work.assignment(subject.assignmentId)
            workRequire(assignment.executionGeneration==subject.generation,WorkProblem.STALE_GENERATION)
            val existing=work.records.values.filter { (it.payload as? WContinuationsPayload)?.value?.assignment==subject }
            val retired=existing.mapNotNull { (it.payload as WContinuationsPayload).value.supersedes?.key }.toSet()
            val tip=existing.filter { it.identity.key !in retired }.singleOrNull()
            val plan=preparation.plan(captured,subject)
            if(tip==null) {
                workRequire(assignment.state in setOf("paused","bound"),WorkProblem.INVALID_TRANSITION)
                val coverage=inspector.coverage(subject,WorkGitOid(captured.basis.algorithm,captured.basis.hex))
                val key=WorkKey.Id(project,stable("connected:$subject:${coverage.conditionsDigest.hex}"))
                val notices=pairs.map { (work.resolve(it.condition).payload as WConditionsPayload).value.notice() }.distinct()
                val text=notices.joinToString("\n") { ref ->work.bodies[ref.digest]?.also { body ->workRequire(body.sizeBytes==ref.byteLength,WorkProblem.DIGEST_MISMATCH) }?.text() ?: throw WorkFault(WorkProblem.MISSING_BODY) }
                val body=Utf8Content.fromText(text);workRequire(body.sizeBytes in 1..4096,WorkProblem.INCOMPLETE_HANDOFF)
                val envelope=ConnectedEnvelope(key,subject,requireNotNull(assignment.binding),requireNotNull(assignment.execution).nativeHostId,
                    assignment.execution.nativeSessionId,"msg_${key.id}",text)
                custody.retain(key.id,"envelope",envelope.bytes())
                if(!native.hold(envelope))throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
                val op=CPrepareContinuationOperation(CPrepareContinuation(key,CPrepareContinuationData(plan.origin,subject,requireNotNull(assignment.binding),
                    assignment.checkpoint,plan.sourceCheckpoint,pairs,coverage,assignment.generation,plan.before,plan.after,plan.mode,plan.rebind,plan.basis,
                    envelope.messageId,CPrepareContinuationDataNotification(body.digest,body.sizeBytes.toLong()),null),
                    if(body.digest in work.bodies)emptyList() else listOf(CPrepareContinuationBodiesItem(body.digest,text))))
                publish(op,"${key.id}:prepare")
                return@repeat
            }
            val key=tip.identity.key as WorkKey.Id;val c=(tip.payload as WContinuationsPayload).value
            val envelope=ConnectedRuntimeFacts.envelope(work,key,c)
            custody.retain(key.id,"envelope",envelope.bytes())
            inspector.requirePairs(c)
            if(c.phase=="resume-confirmed") {
                workRequire(assignment.state=="active" && assignment.binding==c.binding,WorkProblem.INVALID_BINDING)
                app.continuationPermit(credential(),project,subject,c.binding)
                native.release(envelope);return
            }
            if(!native.hold(envelope))throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
            fun observe(stage: String,outcome: String,effect: WContinuationEffect,delivery: WorkKey.Id?=c.delivery,intent: WorkKey.Id?=c.nativeIntent,reason: String?=null) {
                val text=reason?.let(Utf8Content::fromText)
                publish(CObserveContinuationOperation(CObserveContinuation(key,c.generation,stage,outcome,plan.basis,emptyList(),delivery,intent,
                    text?.let { WorkBodyReference(it.digest,it.sizeBytes) },effect,
                    if(text!=null && text.digest !in work.bodies)listOf(CObserveContinuationBodiesItem(text.digest,text.text()))else emptyList())),
                    "${key.id}:${c.generation}:$stage:$outcome")
            }
            if(c.phase=="held" && c.effect.state=="not-performed") {
                observe(c.stage,"armed",c.effect.copy(attemptId=stable("${c.effect.attemptId}:retry"),requestKey=stable("${c.effect.requestKey}:retry"),
                    state="armed",previousAttempt=c.effect.attemptId))
                return@repeat
            }
            when(c.phase) {
                "prepared" -> {
                    val request=Digest.of(StrictJson.encode(encodeWSource(plan.basis.first())))
                    observe("environment","armed",WContinuationEffect(stable("${key.id}:environment"),stable("${key.id}:environment-request"),request,"armed",null,plan.basis))
                }
                "environment-confirmed" -> {
                    val intent=WorkKey.Id(project,stable("${key.id}:native"))
                    if(assignment.currentIntent==null) {
                        publish(CRequestNativeTransitionOperation(CRequestNativeTransition(subject.assignmentId,assignment.generation,intent.id,stable("${key.id}:correlation"),
                            if(c.mode=="unchanged-base")"resume" else "start",c.checkpoint,emptyList())),"${key.id}:native-request")
                    } else workRequire(assignment.currentIntent==intent,WorkProblem.NATIVE_UNKNOWN)
                    observe("delivery","armed",WContinuationEffect(stable("${key.id}:delivery"),stable("${key.id}:delivery-request"),Digest.of(envelope.bytes()),"armed",null,plan.basis),intent=intent)
                }
                "delivery-confirmed" -> observe("resume","armed",WContinuationEffect(stable("${key.id}:resume"),stable("${key.id}:resume-request"),Digest.of(envelope.bytes()),"armed",null,plan.basis))
                "in-flight","unknown" -> when(c.stage) {
                    "environment" -> {
                        val fresh=custody.retain(c.effect.attemptId,"dispatch",byteArrayOf(1))
                        val outcome=if(fresh)preparation.prepare(plan) else preparation.reconcile(plan)
                        if(outcome !is PreparationOutcome.Matched) {
                            if(c.effect.state!="outcome-unknown")observe("environment","unknown",c.effect.copy(state="outcome-unknown"))
                            throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
                        }
                        observe("environment","confirmed",c.effect.copy(state="confirmed"))
                    }
                    "delivery" -> {
                        val fresh=custody.retain(c.effect.attemptId,"dispatch",byteArrayOf(1))
                        val outcome=if(fresh)native.deliver(envelope) else native.reconcile(envelope)
                        if(outcome==NativeConnectedOutcome.NotPerformed) {
                            observe("delivery","not-performed",c.effect.copy(state="not-performed"),reason="Original native call is qualified quiescent and did not perform the effect")
                            return@repeat
                        }
                        if(outcome!=NativeConnectedOutcome.MatchedWake) {
                            if(c.effect.state!="outcome-unknown")observe("delivery","unknown",c.effect.copy(state="outcome-unknown"))
                            throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
                        }
                        val delivery=WorkKey.Id(project,stable("${key.id}:delivery-record"))
                        val source=(work.resolve(c.sourceCheckpoint).payload as WCheckpointsPayload).value
                        if(WorkRecordIdentity(WorkFamily.DELIVERIES,delivery) !in work.records)
                            publish(CRecordDeliveryOperation(CRecordDelivery(delivery,source.assignment,source.sourceCutId,work.binding(subject).task,c.notice(),null,
                                "durable-delivered",plan.basis,emptyList())),"${key.id}:durable-delivery")
                        observe("delivery","confirmed",c.effect.copy(state="confirmed"),delivery)
                    }
                    "resume" -> {
                        val nativeIntent=c.nativeIntent ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
                        val intent=(work.record(WorkFamily.NATIVE_INTENTS,nativeIntent).payload as WNativeIntentsPayload).value
                        if(intent.outcome!="confirmed")publish(CObserveNativeTransitionOperation(CObserveNativeTransition(subject.assignmentId,assignment.generation,nativeIntent.id,
                            intent.generation,"confirmed",plan.basis,emptyList())),"${key.id}:native-confirmed")
                        observe("resume","confirmed",c.effect.copy(state="confirmed"))
                    }
                }
                else -> throw WorkFault(WorkProblem.NATIVE_UNKNOWN)
            }
        }
        throw WorkFault(WorkProblem.NOT_READY)
    }
    override fun close()=subscription.close()
}
