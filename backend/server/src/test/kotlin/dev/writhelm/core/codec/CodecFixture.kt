// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.workflow.ApprovalWorkflow
import dev.writhelm.core.workflow.*
import dev.writhelm.core.knowledge.*
import java.util.UUID

internal fun entries(state: ApprovalWorkflow, codec: CoreSnapshotCodec = CoreSnapshotCodec()): List<CoreEntry> = buildList { codec.export(state) { add(it) } }
internal fun restored(state: ApprovalWorkflow): ApprovalWorkflow = CoreSnapshotCodec().restore(entries(state).asSequence())
internal fun sameEntries(a: ApprovalWorkflow, b: ApprovalWorkflow): Boolean {
    val x = entries(a); val y = entries(b)
    return x.size == y.size && x.zip(y).all { (l, r) -> l.path == r.path && l.bytes().contentEquals(r.bytes()) }
}

internal fun snapshotWith(s: WorkflowSnapshot,
    requests: Map<UUID, ApprovalRequest> = s.requests,
    approvals: Map<KnowledgeReference, ApprovalTrace> = s.approvals,
    continuity: Map<KnowledgeReference, StoredContinuity> = s.continuity,
    assessments: Map<UUID, RecordedAssessment> = s.assessments,
    manifests: Map<UUID, InputManifest> = s.manifests,
    heads: Map<SubjectKey, KnowledgeReference> = s.heads,
    generations: Map<SubjectKey, Long> = s.generations,
    blocked: Set<KnowledgeReference> = s.blocked,
) = WorkflowSnapshot(s.revisions, s.grants, s.proposals, requests, s.resolutions, approvals, continuity,
    assessments, manifests, heads, generations, blocked)

internal fun shapeMutants(v: JsonValue): Sequence<JsonValue> = sequence {
    when(v) {
        is JObject -> {
            for(key in v.entries.keys) yield(JObject(v.entries-key))
            yield(JObject(v.entries+("_unexpected" to JBoolean(false))))
            for((key,value) in v.entries) for(mutant in shapeMutants(value)) yield(JObject(v.entries+(key to mutant)))
        }
        is JArray -> for((index,value) in v.values.withIndex()) for(mutant in shapeMutants(value)) yield(JArray(v.values.mapIndexed { i,item -> if(i==index)mutant else item }))
        else -> Unit
    }
}
internal fun mutateRecord(entries: List<CoreEntry>, family: String, change: (Map<String, JsonValue>) -> JsonValue): List<CoreEntry> {
    var changed = false
    return entries.map {
        if (!changed && it.path.contains("/records/$family/")) {
            changed = true
            val fields = (StrictJson.decode(it.bytes()) as JObject).entries
            CoreEntry(it.path, StrictJson.encode(change(fields)))
        } else it
    }
}
