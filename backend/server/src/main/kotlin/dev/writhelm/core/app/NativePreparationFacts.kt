// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.CredentialStatus
import dev.writhelm.core.auth2.CredentialScope
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import java.util.UUID

internal fun interface NativePreparationProofControl { fun preparationProof(assignment:UUID,generation:Long):JsonValue? }

/** Private retained passive preparation producer. No caller/model `prepared=true`, source path,
 * scope label or native status is accepted as authority. Current profile/enrollment/command policy
 * stays in ordinary H1 reduction. This produces no start, completion or writer-fencing fact. */
internal class NativePreparationFacts(private val delegate:H1RuntimeFacts,private val native:NativePreparationProofControl,
    private val epoch:String):H1RuntimeFacts {
    init { require(epoch.matches(Regex("[0-9a-f]{64}"))) }
    private fun same(a:JsonValue,b:JsonValue)=StrictJson.encode(a,2*1024*1024).contentEquals(StrictJson.encode(b,2*1024*1024))
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val original=delegate.capture(state,command)
        val bind=(command.operation as? CBindAssignmentOperation)?.value ?: return original
        val observed=try { observe(state,command.projectId,bind) }catch(_:Exception){null} ?: return original
        return H1ObservedFacts(original.basis,original.facts.copy(native=original.facts.native+(bind.intentId to observed.first),
            retainedSnapshots=original.facts.retainedSnapshots+observed.second),original.qualifiedProfiles)
    }
    internal fun observe(state:H1BoundState,project:UUID,bind:CBindAssignment):Pair<WorkNativeObservation,WSnapshot>? {
        val work=state.work.getValue(project);val a=work.assignment(bind.assignmentId)
        if(a.executionGeneration!=0L)return null // later generations need their explicit rebind/recovery producer
        val raw=native.preparationProof(bind.assignmentId,a.executionGeneration) ?: return null
        val frame=raw.fields("formatVersion","preparationJSON","snapshotJSON","contextCount","inboxCount","nativeRunning","nativeJSON")
        workRequire(frame.getValue("formatVersion").long()==1L && frame.getValue("contextCount").long()==0L &&
            frame.getValue("inboxCount").long()==0L && frame.getValue("nativeRunning")==JBoolean(false),WorkProblem.INVALID_BINDING)
        val job=StrictJson.decode(frame.getValue("preparationJSON").string().toByteArray(),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024))
            .fields("formatVersion","assignmentId","generation","intentId","workspaceId","intendedExecution","taskJSON","profileJSON",
                "baseJSON","snapshotJSON","artifactLocatorId","epoch","directory","agent","model")
        val subject=WAssignmentSubject(bind.assignmentId,0)
        workRequire(job.getValue("formatVersion").long()==1L && uuid(job.getValue("assignmentId"))==bind.assignmentId && job.getValue("generation").string()=="0" &&
            uuid(job.getValue("intentId"))==bind.intentId && a.currentIntent==WorkKey.Id(project,bind.intentId) &&
            uuid(job.getValue("workspaceId"))==a.workspaceId && job.getValue("epoch").string()==epoch &&
            decodeWExecutionIntent(job.getValue("intendedExecution"))==a.intendedExecution && a.state=="reserved",WorkProblem.INVALID_BINDING)
        workRequire(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,job.getValue("taskJSON").string().toByteArray())==a.task &&
            WorkCodec().decodeRecord(job.getValue("profileJSON").string().toByteArray())==work.record(WorkFamily.PROFILES,a.profile),WorkProblem.INVALID_BINDING)
        val base=decodeWSnapshot(StrictJson.decode(job.getValue("baseJSON").string().toByteArray()))
        val snapshot=decodeWSnapshot(StrictJson.decode(frame.getValue("snapshotJSON").string().toByteArray()))
        workRequire(work.task(a.task).base?.code==listOf(base) && base.dirtyContentDigest==null && base.commit!=null &&
            snapshot==base.copy(retainedLocatorId=uuid(job.getValue("artifactLocatorId"))) &&
            job.getValue("snapshotJSON").string()==frame.getValue("snapshotJSON").string(),WorkProblem.INVALID_SUBJECT)
        val n=StrictJson.decode(frame.getValue("nativeJSON").string().toByteArray(),JsonLimits(bytes=65536)).fields(
            "sessionID","directory","parentID","fork","revert","agent","model","permissions","metadata")
        workRequire(n.getValue("sessionID").string()==a.intendedExecution.nativeSessionId && n.getValue("directory")==job.getValue("directory") &&
            n.getValue("parentID")===JNull && n.getValue("fork")===JNull && n.getValue("revert")===JNull && n.getValue("agent")==job.getValue("agent") &&
            same(n.getValue("permissions"),JArray(listOf(obj("action" to str("*"),"resource" to str("*"),"effect" to str("deny"))))),WorkProblem.INVALID_BINDING)
        val profile=(work.record(WorkFamily.PROFILES,a.profile).payload as WProfilesPayload).value
        val model=n.getValue("model") as? JObject ?: return null
        val m=model.fields(*(if("variant" in model.entries)arrayOf("providerID","id","variant")else arrayOf("providerID","id")))
        workRequire(same(model,job.getValue("model")) && m.getValue("providerID").string()==profile.model.providerId &&
            m.getValue("id").string()==profile.model.modelId && m["variant"]?.string()==profile.model.variant,WorkProblem.INVALID_BINDING)
        val metadata=n.getValue("metadata").fields("writhelm").getValue("writhelm").fields("kind","assignmentId","executionGeneration",
            "controllerId","executionId","workspaceId","taskJSON","profileKeyJSON")
        workRequire(metadata.getValue("kind").string()=="passive-preparation-v1" && uuid(metadata.getValue("assignmentId"))==bind.assignmentId &&
            metadata.getValue("executionGeneration").string()=="0" && uuid(metadata.getValue("controllerId"))==a.intendedExecution.controllerId &&
            uuid(metadata.getValue("executionId"))==a.intendedExecution.executionId && uuid(metadata.getValue("workspaceId"))==a.workspaceId &&
            metadata.getValue("taskJSON")==job.getValue("taskJSON") && identityCodec.decodeKey(WorkFamily.PROFILES,
                metadata.getValue("profileKeyJSON").string().toByteArray())==a.profile,WorkProblem.INVALID_BINDING)
        val delegation=a.delegationId?.let { state.authority.delegations[it] } ?: return null
        if(delegation.status!=CredentialStatus.ACTIVE)return null
        val key=state.authority.credentials.values.singleOrNull { it.scope==CredentialScope.Assignment(delegation.id) &&
            it.credential.status==CredentialStatus.ACTIVE && it.credential.replacedBy==null } ?: return null
        val b=delegation.binding
        workRequire(b.assignmentId==bind.assignmentId && b.executionGeneration==0L && b.executionId==a.intendedExecution.executionId &&
            b.controllerId==a.intendedExecution.controllerId && b.taskId==a.task.taskId,WorkProblem.INVALID_BINDING)
        val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,bind.intentId)).payload as WNativeIntentsPayload).value
        val execution=WExecution(b.executionId,b.principalId,key.credential.id,a.intendedExecution.nativeHostId,a.intendedExecution.nativeSessionId,a.workspaceId)
        val sources=listOf<WSource>(WSourceVariant1Value(WSourceVariant1("artifact",snapshot)))
        return WorkNativeObservation(subject,bind.intentId,intent.correlationId,"prepare","confirmed",sources,execution,false,"reserved") to snapshot
    }
}
