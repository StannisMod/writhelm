// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*

/** Pure preparation against validation state H. No reads, publication, clock or mutable latest lookup.
 * Original selector command stays in the command journal; only its evaluated packet enters workflow.
 */
internal fun CoreCommand.resolveReview(snapshot: WorkflowSnapshot): WorkflowResult<CoreCommand> {
    val submission=payload as? CommandPayload.SubmitReview ?: return WorkflowResult.Success(this)
    class Held(val problem: WorkflowProblem): RuntimeException()
    fun hold(problem: WorkflowProblem): Nothing=throw Held(problem)
    val revisions=snapshot.revisions.associateBy { it.key }
    fun source(value: JsonValue): JsonValue {
        val f=(value as JObject).entries
        val ref=if(f.getValue("kind").string()=="revision") {
            val key=revisionKey(f.getValue("revisionKey"))
            if(key.document.projectId!=projectId)hold(WorkflowProblem.FORBIDDEN)
            val revision=revisions[key] ?: hold(WorkflowProblem.NOT_FOUND)
            if(revision.digest!=digest(f.getValue("revisionDigest")))hold(WorkflowProblem.INVALID_REFERENCE)
            val clause=f.getValue("clauseId")
            if(clause===JNull)revision.contextReference() else {
                val id=checked { ClauseId(clause.string()) }
                if(revision.structure.clause(id)==null)hold(WorkflowProblem.INVALID_REFERENCE)
                revision.clauseReference(id)
            }
        } else reference(value)
        if(ref.revision.document.projectId!=projectId)hold(WorkflowProblem.FORBIDDEN)
        val revision=revisions[ref.revision] ?: hold(WorkflowProblem.NOT_FOUND)
        val found=when(ref) {
            is ClauseReference -> revision.clauseRead(ref) is ReadResult.Found
            is ContextReference -> revision.contextRead(ref) is ReadResult.Found
        }
        if(!found)hold(WorkflowProblem.INVALID_REFERENCE)
        return reference(ref)
    }
    return try {
        val f=(submission.draft as JObject).entries
        val values=f.toMutableMap()
        values["items"]=JArray(f.getValue("items").list().map {
            val item=(it as JObject).entries
            JObject(item+mapOf("target" to source(item.getValue("target")),
                "before" to item.getValue("before").let { before->if(before===JNull)JNull else source(before) }))
        })
        values["contexts"]=JArray(f.getValue("contexts").list().map { value->
            source(value).also { if(reference(it) !is ContextReference)hold(WorkflowProblem.INVALID_REFERENCE) }
        })
        values["dependencies"]=JArray(f.getValue("dependencies").list().map(::source))
        f["followup"]?.let { value->
            val link=(value as JObject).entries
            val previous=snapshot.requests[uuid(link.getValue("requestId"))] ?: hold(WorkflowProblem.NOT_FOUND)
            if(previous.projectId!=projectId)hold(WorkflowProblem.FORBIDDEN)
            values["followup"]=JObject(link+mapOf("requestDigest" to digest(previous.digest),"packetDigest" to digest(previous.packet.digest)))
        }
        WorkflowResult.Success(CoreCommand(requestKey,projectId,CommandPayload.Request(submission.requestId,packet(JObject(values)))))
    } catch(e: Held) {
        WorkflowResult.Failure(e.problem)
    }
}
