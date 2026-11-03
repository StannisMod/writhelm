// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.workflow

import dev.writhelm.core.knowledge.*
import java.util.UUID

/** Isolated immutable internal DTO, never a client upload/replace-state API. */
internal class WorkflowSnapshot(revisions: Collection<DocumentRevision>, grants: Collection<Grant>,
    proposals: Map<RevisionKey, Actor>, requests: Map<UUID, ApprovalRequest>, resolutions: Map<UUID, ApprovalResolution>,
    approvals: Map<KnowledgeReference, ApprovalTrace>, continuity: Map<KnowledgeReference, StoredContinuity>,
    assessments: Map<UUID, RecordedAssessment>, manifests: Map<UUID, InputManifest>, heads: Map<SubjectKey, KnowledgeReference>,
    generations: Map<SubjectKey, Long>, blocked: Set<KnowledgeReference>) {
    val revisions = frozenList(revisions)
    val grants = frozenList(grants.map { Grant(it.principalId, it.projectId, frozenSet(it.capabilities)) })
    val proposals = frozenMap(proposals)
    val requests = frozenMap(requests)
    val resolutions = frozenMap(resolutions)
    val approvals = frozenMap(approvals)
    val continuity = frozenMap(continuity)
    val assessments = frozenMap(assessments)
    val manifests = frozenMap(manifests)
    val heads = frozenMap(heads)
    val generations = frozenMap(generations)
    val blocked = frozenSet(blocked)
}
