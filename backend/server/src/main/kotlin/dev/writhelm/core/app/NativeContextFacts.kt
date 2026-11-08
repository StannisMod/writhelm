// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.util.UUID

internal fun interface NativeSourceProofControl { fun sourceProof(locator: UUID): JsonValue? }

/** Actual immutable host-custody producer for worker source cuts. Missing/mismatched source omits
 * the fact and the ordinary reducer refuses the command. No command-body `retained=true` flag,
 * current-authority callback under the monitor, or copying the native transcript into Git.
 * Controller full-frontier packet verification is a separate dependent composition, not guessed. */
internal class NativeContextFacts(private val delegate: H1RuntimeFacts,private val native: NativeSourceProofControl,
    private val epoch: String,private val controller: Model2ControllerExecution?=null): H1RuntimeFacts {
    init { require(epoch.matches(Regex("[0-9a-f]{64}"))) }
    override fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts {
        val original=delegate.capture(state,command)
        val worker=(command.operation as? CRecordSourceCutOperation)?.value?.data
        val planner=(command.operation as? CRecordControllerContinuationOperation)?.value
        val locator=worker?.retainedLocatorId ?: planner?.data?.source?.retainedLocatorId ?: return original
        val observed=try { if(worker!=null)workerCut(state,command,worker)else controllerCut(state,command,requireNotNull(planner)) }catch(_: Exception) { null }
        return if(observed==null)original else H1ObservedFacts(original.basis,
            original.facts.copy(sourceCuts=original.facts.sourceCuts+(locator to observed)),original.qualifiedProfiles)
    }
    private fun controllerCut(state: H1BoundState,command: WorkCommand,request: CRecordControllerContinuation): WorkRetainedCut? {
        val execution=controller ?: return null
        val cut=request.data.source
        val raw=native.sourceProof(cut.retainedLocatorId) ?: return null
        val p=raw.fields("locatorId","scope","profileJSON","modelJSON","renderedRequestDigest","packetDigest","nativeMessages")
        val s=p.getValue("scope").fields("storeId","projectId","controllerId","hostId","sessionID","epoch","scope","assignmentId","executionGeneration","bindingJSON")
        workRequire(request.key.controllerId==execution.controllerId && uuid(p.getValue("locatorId"))==cut.retainedLocatorId &&
            s.getValue("scope").string()=="controller" && uuid(s.getValue("storeId"))==state.storeId && uuid(s.getValue("projectId"))==command.projectId &&
            uuid(s.getValue("controllerId"))==execution.controllerId && uuid(s.getValue("hostId"))==execution.nativeHostId &&
            s.getValue("sessionID").string()==execution.nativeSessionId && s.getValue("epoch").string()==epoch &&
            s.getValue("assignmentId")===JNull && s.getValue("executionGeneration")===JNull && s.getValue("bindingJSON")===JNull,WorkProblem.INVALID_BINDING)
        val key=identityCodec.decodeKey(WorkFamily.PROFILES,p.getValue("profileJSON").string().toByteArray(Charsets.UTF_8)) as WorkKey.Profile
        workRequire(key==request.data.profile,WorkProblem.INVALID_SUBJECT)
        val basis=NativeControllerContext.basis(state,command.projectId,execution.controllerId,key)
        val profile=(basis.profile.payload as WProfilesPayload).value
        val model=StrictJson.decode(p.getValue("modelJSON").string().toByteArray(Charsets.UTF_8)) as? JObject ?: return null
        val m=model.fields(*(if("variant" in model.entries)arrayOf("providerID","id","variant")else arrayOf("providerID","id")))
        workRequire(m.getValue("providerID").string()==profile.model.providerId && m.getValue("id").string()==profile.model.modelId &&
            m["variant"]?.string()==profile.model.variant && digest(p.getValue("packetDigest"))==Digest.of(basis.packet.toByteArray(Charsets.UTF_8)),WorkProblem.DIGEST_MISMATCH)
        return WorkRetainedCut(execution.nativeHostId,execution.nativeSessionId,digest(p.getValue("renderedRequestDigest")),digest(p.getValue("packetDigest")),
            p.getValue("nativeMessages").list().map { item ->val value=item.fields("messageId","digest");value.getValue("messageId").string() to digest(value.getValue("digest")) })
    }
    private fun workerCut(state: H1BoundState,command: WorkCommand,cut: WSourceCuts): WorkRetainedCut? {
        val raw=native.sourceProof(cut.retainedLocatorId) ?: return null
        val proof=raw.fields("locatorId","scope","profileJSON","modelJSON","renderedRequestDigest","packetDigest","nativeMessages")
        val scope=proof.getValue("scope").fields("storeId","projectId","controllerId","hostId","sessionID","epoch","scope","assignmentId","executionGeneration","bindingJSON")
        val work=state.work.getValue(command.projectId);val assignment=work.assignment(cut.assignment.assignmentId)
        val binding=work.binding(cut.assignment)
        workRequire(uuid(proof.getValue("locatorId"))==cut.retainedLocatorId && scope.getValue("scope").string()=="assignment" &&
            uuid(scope.getValue("storeId"))==state.storeId && uuid(scope.getValue("projectId"))==command.projectId &&
            uuid(scope.getValue("controllerId"))==assignment.intendedExecution.controllerId &&
            uuid(scope.getValue("hostId"))==binding.execution.nativeHostId && scope.getValue("sessionID").string()==binding.execution.nativeSessionId &&
            scope.getValue("epoch").string()==epoch && uuid(scope.getValue("assignmentId"))==cut.assignment.assignmentId &&
            scope.getValue("executionGeneration").string()==cut.assignment.generation.toString() &&
            identityCodec.decodeReference(scope.getValue("bindingJSON").string().toByteArray(Charsets.UTF_8))==assignment.binding,WorkProblem.INVALID_BINDING)
        val profileKey=identityCodec.decodeKey(WorkFamily.PROFILES,proof.getValue("profileJSON").string().toByteArray(Charsets.UTF_8))
        workRequire(profileKey==binding.profile && cut.profile==binding.profile,WorkProblem.INVALID_SUBJECT)
        val profile=(work.record(WorkFamily.PROFILES,binding.profile).payload as WProfilesPayload).value
        val model=StrictJson.decode(proof.getValue("modelJSON").string().toByteArray(Charsets.UTF_8)) as? JObject ?: return null
        val mf=model.fields(*(if("variant" in model.entries)arrayOf("providerID","id","variant")else arrayOf("providerID","id")))
        workRequire(mf.getValue("providerID").string()==profile.model.providerId && mf.getValue("id").string()==profile.model.modelId &&
            mf["variant"]?.string()==profile.model.variant,WorkProblem.INVALID_SUBJECT)
        val packet=StrictJson.encode(JArray(NativeAssignmentPacket.render(state,command.projectId,cut.assignment).map {
            obj("key" to str(it.first),"value" to str(it.second)) }),2*1024*1024).decodeToString()
        workRequire(digest(proof.getValue("packetDigest"))==Digest.of(packet.toByteArray(Charsets.UTF_8)),WorkProblem.DIGEST_MISMATCH)
        val messages=proof.getValue("nativeMessages").list().map { item ->
            val m=item.fields("messageId","digest");m.getValue("messageId").string() to digest(m.getValue("digest"))
        }
        return WorkRetainedCut(binding.execution.nativeHostId,binding.execution.nativeSessionId,
            digest(proof.getValue("renderedRequestDigest")),digest(proof.getValue("packetDigest")),messages)
    }
}
