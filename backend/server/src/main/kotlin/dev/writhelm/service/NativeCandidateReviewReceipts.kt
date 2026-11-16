// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.work.*
import java.nio.file.*
import java.util.UUID

/** Independent read of actual native immutable journals, not a callback's `ready` assertion. No
 * native/service request, model wake, retry, ref update or custody repair occurs during capture. */
internal class NativeCandidateReviewReceipts(private val root:Path) {
    fun reservationCandidate(state:H1BoundState,command:WorkCommand,proof:(UUID)->JsonValue?):WSnapshot? {return try {
        val reserve=(command.operation as? CReserveAssignmentOperation)?.value ?: return null
        require(reserve.intendedExecution.template=="reviewer-v1")
        val work=state.work.getValue(command.projectId)
        val paths=Files.list(root).use {it.filter {p->p.fileName.toString().startsWith("candidate-review-automatic-")}.limit(257).toList()}
        require(paths.size<=256)
        val matched=paths.mapNotNull {path ->
            val bytes=protectedRuntimeBytes(root,path,2*1024*1024)
            val frame=(StrictJson.decode(bytes,JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,tokens=100000)) as JObject).entries
            val selection=(StrictJson.decode(frame.getValue("selectionJSON").string().toByteArray()) as JObject).entries
            if(identityCodec.decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(selection.getValue("task")))!=reserve.task)return@mapNotNull null
            val basis=(StrictJson.decode(frame.getValue("ticketBasisJSON").string().toByteArray()) as JObject).entries
            val assembly=uuid(basis.getValue("assemblyId"));val checks=uuid(basis.getValue("checkPlanId"))
            val key=StrictJson.encode(JArray(listOf(uuid(assembly),uuid(checks))))
            require(path.fileName.toString()=="candidate-review-automatic-${Digest.of(key).hex}.json")
            val planRef=identityCodec.decodeReference(StrictJson.encode(basis.getValue("plan")))
            val plan=(work.resolve(planRef).payload as WDispatchPlanPayload).value
            require(plan.controllerId==reserve.intendedExecution.controllerId)
            val evidence=requireNotNull(observe(command.projectId,assembly,checks,plan))
            val proposal=automaticCandidateReviewPlan(work,planRef,evidence,checks)
            val codec=WorkCodec(modelVersion=work.modelVersion)
            require(frame.getValue("taskCommandJSON").string()==codec.encodeCommand(proposal.taskCommand).toString(Charsets.UTF_8) &&
                frame.getValue("manifestCommandJSON").string()==codec.encodeCommand(proposal.manifestCommand).toString(Charsets.UTF_8) &&
                frame.getValue("preparationId")==uuid(proposal.preparationId) &&
                StrictJson.encode(selection.getValue("manifest")).contentEquals(identityCodec.encodeReference(reserve.manifest)) &&
                StrictJson.encode(proposal.selection).contentEquals(StrictJson.encode(JObject(selection))))
            val actual=requireNotNull(proof(assembly))
            val receipt=actual.string().toByteArray()
            val expected=protectedRuntimeBytes(root,root.resolve("candidate-receipt-${Digest.of(assembly.toString().toByteArray()).hex}.json"),2*1024*1024)
            require(receipt.contentEquals(expected))
            require(observe(command.projectId,assembly,checks,plan)==evidence && protectedRuntimeBytes(root,path,2*1024*1024).contentEquals(bytes))
            evidence.candidate
        }
        matched.singleOrNull()
    }catch(_:Exception){null}}
    fun freshReceiver(state:H1BoundState,ticket:CandidateReviewTicket,epoch:String):Boolean=try {
        val work=state.work.getValue(ticket.project);val assignment=work.assignment(ticket.assignment.assignmentId)
        require(ticket.assignment.generation==0L && assignment.state=="bound" && assignment.currentIntent==null)
        val key=Digest.of(StrictJson.encode(JArray(listOf(uuid(ticket.assignment.assignmentId),str("0"))))).hex
        val original=protectedRuntimeBytes(root,root.resolve("worker-preparation-$key.json"),2*1024*1024)
        val frame=(StrictJson.decode(protectedRuntimeBytes(root,root.resolve("worker-prepared-$key.json"),2*1024*1024),
            JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,tokens=100000)) as JObject).entries
        require(frame.getValue("formatVersion").long()==1L && frame.getValue("kind").string()=="passive-receiver-observation" &&
            frame.getValue("preparationJSON").string()==original.toString(Charsets.UTF_8) &&
            frame.getValue("contextCount").long()==0L && frame.getValue("inboxCount").long()==0L && frame.getValue("nativeRunning")==JBoolean(false))
        val job=(StrictJson.decode(original,JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,tokens=100000)) as JObject).entries
        // Binding completes the prepare intent and clears currentIntent. Observe the exact
        // original preparation ID in custody, never guess a pending pointer or a latest intent.
        val intent=(work.record(WorkFamily.NATIVE_INTENTS,WorkKey.Id(ticket.project,uuid(job.getValue("intentId")))).payload as WNativeIntentsPayload).value
        require(intent.operation=="prepare" && intent.outcome=="confirmed" && intent.assignment==ticket.assignment &&
            intent.expectedSession==ticket.execution.nativeSessionId && intent.expectedWorkspace==ticket.execution.workspaceId)
        val intended=decodeWExecutionIntent(job.getValue("intendedExecution"))
        require(uuid(job.getValue("assignmentId"))==ticket.assignment.assignmentId && job.getValue("generation").string()=="0" &&
            uuid(job.getValue("workspaceId"))==ticket.execution.workspaceId && intended==assignment.intendedExecution && intended.template=="reviewer-v1" &&
            job.getValue("epoch").string()==epoch)
        val info=(StrictJson.decode(frame.getValue("nativeInfoJSON").string().toByteArray()) as JObject).entries
        require(info.getValue("id").string()==ticket.execution.nativeSessionId && listOf("parentID","fork","revert").none {info[it]?.let {v->v!==JNull}==true})
        require(protectedRuntimeBytes(root,root.resolve("worker-preparation-$key.json"),2*1024*1024).contentEquals(original))
        true
    }catch(_:Exception){false}
    fun observe(project:UUID,assemblyId:UUID,testPlanId:UUID,plan:DispatchPlanCandidate):ObservedCandidateReviewReceipts? {return try {
        require(plan.projectId==project && plan.candidateChecks.isNotEmpty())
        val captured=linkedMapOf<Path,ByteArray>()
        fun bytes(kind:String,key:String):ByteArray {
            val path=root.resolve("$kind-${Digest.of(key.toByteArray(Charsets.UTF_8)).hex}.json")
            val value=protectedRuntimeBytes(root,path,2*1024*1024)
            val prior=captured.putIfAbsent(path,value);require(prior==null || prior.contentEquals(value))
            return value
        }
        fun read(kind:String,key:String)=StrictJson.decode(bytes(kind,key),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,collection=4096,tokens=100000))
        fun optional(kind:String,key:String):JsonValue? {
            val path=root.resolve("$kind-${Digest.of(key.toByteArray(Charsets.UTF_8)).hex}.json")
            return if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))read(kind,key) else null
        }
        fun candidate(value:JsonValue):Pair<WorkGitOid,WorkGitOid> {
            val f=value.fields("commit","tree");return workOid(f.getValue("commit")) to workOid(f.getValue("tree"))
        }
        val original=bytes("candidate-operation",assemblyId.toString()).toString(Charsets.UTF_8)
        val request=StrictJson.decode(original.toByteArray()).fields("operationId","candidateId","head","inputs","time","selectedJSON")
        require(uuid(request.getValue("operationId"))==assemblyId)
        val receipt=read("candidate-receipt",assemblyId.toString()).fields("original","result")
        require(receipt.getValue("original").string()==original)
        val result=candidate(receipt.getValue("result"))
        val publication=read("candidate-publish",assemblyId.toString()).fields("original","result")
        require(publication.getValue("original").string()==original && candidate(publication.getValue("result"))==result &&
            candidate(read("candidate-objects",assemblyId.toString()))==result)
        val head=decodeWSnapshot(request.getValue("head"))
        require(head.commit!=null && head.dirtyContentDigest==null && result.first.algorithm==head.commit.algorithm && result.second.algorithm==head.tree.algorithm)
        val snapshot=head.copy(commit=result.first,tree=result.second,retainedLocatorId=uuid(request.getValue("candidateId")))
        val selected=StrictJson.decode(request.getValue("selectedJSON").string().toByteArray()).list().map {
            identityCodec.decodeReference(StrictJson.encode(it))
        }
        require(selected.size in 1..256 && selected.distinct().size==selected.size &&
            selected.all {it.identity.family==WorkFamily.RESULTS && it.identity.key.projectId==project})
        val savedPlan=read("candidate-test-plan",testPlanId.toString()).fields("subject","runs")
        val subjectRaw=savedPlan.getValue("subject").string()
        val subject=StrictJson.decode(subjectRaw.toByteArray()).fields("candidate","checks")
        require(candidate(subject.getValue("candidate"))==result)
        val candidateKey=StrictJson.encode(JArray(listOf(str(result.first.algorithm),str(result.first.hex),str(result.second.algorithm),str(result.second.hex)))).toString(Charsets.UTF_8)
        val required=read("candidate-required-plan",candidateKey).fields("planId","subject")
        require(uuid(required.getValue("planId"))==testPlanId && required.getValue("subject").string()==subjectRaw)
        val checks=subject.getValue("checks").list();val runs=savedPlan.getValue("runs").list()
        require(checks.size==plan.candidateChecks.size && runs.size==checks.size)
        val observed=checks.map {raw ->
            val check=raw.fields("checkId","command","safeRepeat","dependencies");val id=check.getValue("checkId").string()
            val canonical=plan.candidateChecks.single {it.checkId==id}
            val command=canonical.command.map {it.replace("{candidateCommit}",result.first.hex).replace("{candidateTree}",result.second.hex)}
            require(check.getValue("command").list().map {it.string()}==command &&
                check.getValue("safeRepeat")==JBoolean(canonical.safeRepeat) &&
                check.getValue("dependencies").list().map {it.string()}==canonical.dependencies)
            val run=runs.map {it.fields("checkId","runId","retryId")}.single {it.getValue("checkId").string()==id}
            uuid(run.getValue("retryId"))
            var runId=uuid(run.getValue("runId"));var previous:UUID?=null;val seen=mutableSetOf<UUID>()
            while(true) {
                require(seen.size<256 && seen.add(runId))
                val rawRun=bytes("candidate-test-run",runId.toString()).toString(Charsets.UTF_8)
                val invocation=StrictJson.decode(rawRun.toByteArray()).fields("runId","candidate","checkId","command","safeRepeat","previousRunId")
                require(uuid(invocation.getValue("runId"))==runId && candidate(invocation.getValue("candidate"))==result &&
                    invocation.getValue("checkId").string()==id && invocation.getValue("command").list().map {it.string()}==command &&
                    invocation.getValue("safeRepeat")==JBoolean(canonical.safeRepeat) &&
                    invocation.getValue("previousRunId")== (previous?.let(::uuid) ?: JNull))
                val outcomeBytes=bytes("candidate-test-result",runId.toString())
                val outcome=StrictJson.decode(outcomeBytes).fields("original","result")
                require(outcome.getValue("original").string()==rawRun)
                val status=outcome.getValue("result").fields("outcome","output")
                status.getValue("output").string()
                val successor=optional("candidate-test-successor",runId.toString())
                if(successor==null) {
                    require(status.getValue("outcome").string()=="passed")
                    break
                }
                require(canonical.safeRepeat && status.getValue("outcome").string() in setOf("passed","failed","cancelled","unavailable"))
                val next=(successor as JObject).entries
                val nextId=uuid(next.getValue("runId"))
                require(StrictJson.encode(successor).contentEquals(StrictJson.encode(read("candidate-test-run",nextId.toString()))))
                previous=runId;runId=nextId
            }
            CandidateReviewCheckReceipt(id,runId,Digest.of(bytes("candidate-test-result",runId.toString())))
        }
        require(observed.map {it.checkId}.distinct().size==plan.candidateChecks.size && observed.map {it.runId}.distinct().size==observed.size)
        captured.forEach {(file,value)->require(protectedRuntimeBytes(root,file,2*1024*1024).contentEquals(value))}
        val evidence=JArray(captured.entries.map {obj("file" to str(it.key.fileName.toString()),"digest" to digest(Digest.of(it.value)))})
        ObservedCandidateReviewReceipts(original,snapshot,selected,observed,Digest.of(StrictJson.encode(evidence,2*1024*1024)))
    }catch(_:Exception){null}}
}
