// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.util.UUID

internal fun interface NativeInitialProofControl { fun initialProof(intent:UUID):JsonValue? }

/** Confirms original initial-prompt admission at the held native context boundary. An SDK reply,
 * inbox presence, model output or idle is insufficient; no completion/fencing fact is produced. */
internal class NativeInitialFacts(private val delegate:H1RuntimeFacts,private val native:NativeInitialProofControl,
    private val epoch:String):H1RuntimeFacts {
    init { require(epoch.matches(Regex("[0-9a-f]{64}"))) }
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val original=delegate.capture(state,command)
        val operation=(command.operation as? CObserveNativeTransitionOperation)?.value ?: return original
        if(operation.outcome!="confirmed")return original
        val observed=try { observe(state,command.projectId,operation) }catch(_:Exception){null} ?: return original
        return H1ObservedFacts(original.basis,original.facts.copy(native=original.facts.native+(operation.intentId to observed)),original.qualifiedProfiles)
    }
    internal fun observe(state:H1BoundState,project:UUID,operation:CObserveNativeTransition):WorkNativeObservation? {
        val work=state.work.getValue(project);val a=work.assignment(operation.assignmentId)
        val subject=WAssignmentSubject(operation.assignmentId,a.executionGeneration)
        val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,operation.intentId)).payload as WNativeIntentsPayload).value
        if(intent.operation!="start"||a.binding==null||a.currentIntent!=WorkKey.Id(project,operation.intentId))return null
        val raw=native.initialProof(operation.intentId) ?: return null
        val p=raw.fields("formatVersion","envelopeJSON","envelopeDigest","packetDigest","epoch","heldJSON")
        workRequire(p.getValue("formatVersion").long()==1L && p.getValue("epoch").string()==epoch && p.getValue("heldJSON")!==JNull,WorkProblem.INVALID_BINDING)
        val envelope=NativeInitialContext.envelope(state,project,subject,a.binding,operation.intentId)
        val bytes=StrictJson.encode(envelope)
        workRequire(p.getValue("envelopeJSON").string()==bytes.decodeToString() && digest(p.getValue("envelopeDigest"))==Digest.of(bytes),WorkProblem.DIGEST_MISMATCH)
        val instructions=JArray(NativeAssignmentPacket.render(state,project,subject).map { obj("key" to str(it.first),"value" to str(it.second)) })
        workRequire(digest(p.getValue("packetDigest"))==Digest.of(StrictJson.encode(instructions,2*1024*1024)),WorkProblem.DIGEST_MISMATCH)
        val held=StrictJson.decode(p.getValue("heldJSON").string().toByteArray()).fields("digest","messageId","sessionID")
        val e=(envelope as JObject).entries
        workRequire(held.getValue("digest")==p.getValue("envelopeDigest") && held.getValue("messageId")==e.getValue("messageId") &&
            held.getValue("sessionID")==e.getValue("nativeSessionId"),WorkProblem.INVALID_BINDING)
        val execution=work.binding(subject).execution
        return WorkNativeObservation(subject,operation.intentId,intent.correlationId,"start","confirmed",
            listOf(WSourceVariant2Value(WSourceVariant2("work",a.binding))),execution,false,"bound")
    }
}
