// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.PresentedCredential
import dev.writhelm.core.auth2.CredentialScope
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.Capability
import java.util.UUID

/** The first normal prompt is a different original effect from a connected-work synthetic notice.
 * Its immutable identity is derived from the actual bound assignment/start intent, never a model
 * message/session argument or an invented continuation record. */
internal object NativeInitialContext {
    const val TEXT="Begin the exact service-bound assignment. Follow the installed mandatory task/profile/input instructions. Report progress and remaining obligations through Writhelm. This message grants no approval, acceptance or integration authority."
    fun envelope(state:H1BoundState,project:UUID,subject:WAssignmentSubject,binding:WorkReference,intentId:UUID):JsonValue {
        val work=state.work.getValue(project);val a=work.assignment(subject.assignmentId)
        workRequire(a.executionGeneration==subject.generation && a.binding==binding,WorkProblem.INVALID_BINDING)
        val execution=work.binding(subject).execution
        val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,intentId)).payload as WNativeIntentsPayload).value
        workRequire(intent.assignment==subject && intent.operation=="start" && intent.expectedSession==execution.nativeSessionId &&
            intent.expectedWorkspace==a.workspaceId,WorkProblem.INVALID_BINDING)
        return obj("formatVersion" to num(1),"kind" to str("initial-start"),"projectId" to uuid(project),"assignmentId" to uuid(subject.assignmentId),
            "executionGeneration" to str(subject.generation.toString()),"binding" to StrictJson.decode(identityCodec.encodeReference(binding)),
            "intentId" to uuid(intentId),"correlationId" to uuid(intent.correlationId),"nativeHostId" to uuid(execution.nativeHostId),
            "nativeSessionId" to str(execution.nativeSessionId),"messageId" to str("msg_wh_start_"+intentId.toString().replace("-","")),
            "text" to str(TEXT),"metadata" to obj("nativeIntentId" to uuid(intentId),"assignmentId" to uuid(subject.assignmentId),
                "executionGeneration" to str(subject.generation.toString())),"delivery" to str("queue"),"resume" to JBoolean(true))
    }
    fun read(state:H1BoundState,key:PresentedCredential,project:UUID,controller:UUID,host:UUID,subject:WAssignmentSubject,
        binding:WorkReference,intentId:UUID,safety:NativeRuntimeSafety):JsonValue {
        val actor=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(actor.scope!=CredentialScope.Controller(controller)||!state.authority.allows(actor,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val status=NativeRuntimeAccess(controller,host).status(state,key,project,subject,binding,null,safety) as JObject
        val work=state.work.getValue(project);val a=work.assignment(subject.assignmentId)
        workRequire(a.state !in setOf("completed","replaced"),WorkProblem.INVALID_TRANSITION)
        val original=envelope(state,project,subject,binding,intentId)
        val raw=StrictJson.encode(original).decodeToString()
        val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,intentId)).payload as WNativeIntentsPayload).value
        val dispatch=status.entries.getValue("dispatch")==JBoolean(true) && status.entries.getValue("quiescent")==JBoolean(true) &&
            a.state=="bound" && a.currentIntent==WorkKey.Id(project,intentId) && intent.outcome=="pending"
        state.requireCurrentContinuation(project,subject)
        val instructions=JArray(NativeAssignmentPacket.render(state,project,subject).map { obj("key" to str(it.first),"value" to str(it.second)) })
        return obj("formatVersion" to num(1),"basis" to state.basis.json(),"envelopeJSON" to str(raw),"envelopeDigest" to digest(Digest.of(raw.toByteArray())),
            "packetJSON" to str(StrictJson.encode(instructions,2*1024*1024).decodeToString()),"profileJSON" to str(WorkCodec().encodeRecord(work.record(WorkFamily.PROFILES,a.profile),true).decodeToString()),
            "assignmentJSON" to str(WorkCodec().encodeRecord(work.record(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,subject.assignmentId)),true).decodeToString()),
            "intentJSON" to str(WorkCodec().encodeRecord(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,intentId)),true).decodeToString()),
            "dispatch" to JBoolean(dispatch),"active" to JBoolean(intent.outcome=="confirmed" && status.entries.getValue("active")==JBoolean(true)))
    }
}
