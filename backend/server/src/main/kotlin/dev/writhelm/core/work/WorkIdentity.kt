// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.encodeUtf8
import java.util.UUID

internal enum class WorkKeyKind {
    TASK, TASK_REVISION, PROFILE, REVISION, ID, RESOURCE, ASSIGNMENT_BINDING, CONTROLLER_CONTINUATION,
}

/** Closed metadata registry. A tag is a storage identity, not a filesystem or authority input. */
internal enum class WorkFamily(val tag: String, val keyKind: WorkKeyKind, val mutable: Boolean) {
    TASK_REVISIONS("task-revisions", WorkKeyKind.TASK_REVISION, false),
    TASK_HEADS("task-heads", WorkKeyKind.TASK, true),
    PLANS("plans", WorkKeyKind.REVISION, true),
    INPUT_MANIFESTS("input-manifests", WorkKeyKind.ID, false),
    LOADED_VIEWS("loaded-views", WorkKeyKind.REVISION, false),
    PROFILES("profiles", WorkKeyKind.PROFILE, false),
    ASSIGNMENTS("assignments", WorkKeyKind.ID, true),
    ASSIGNMENT_BINDINGS("assignment-bindings", WorkKeyKind.ASSIGNMENT_BINDING, false),
    CLAIMS("claims", WorkKeyKind.RESOURCE, true),
    NATIVE_INTENTS("native-intents", WorkKeyKind.ID, true),
    RECOVERIES("recoveries", WorkKeyKind.ID, false),
    CHECKPOINTS("checkpoints", WorkKeyKind.ID, false),
    DELIVERIES("deliveries", WorkKeyKind.ID, true),
    MEMORY("memory", WorkKeyKind.REVISION, false),
    RETROSPECTIVES("retrospectives", WorkKeyKind.ID, false),
    RESULTS("results", WorkKeyKind.ID, false),
    VERIFICATION_PLANS("verification-plans", WorkKeyKind.REVISION, false),
    VERIFICATION_REPORTS("verification-reports", WorkKeyKind.ID, false),
    EVIDENCE("evidence", WorkKeyKind.ID, false),
    DISPOSITIONS("dispositions", WorkKeyKind.ID, false),
    ACCEPTANCES("acceptances", WorkKeyKind.ID, false),
    INTEGRATION_INTENTS("integration-intents", WorkKeyKind.ID, true),
    INTEGRATIONS("integrations", WorkKeyKind.ID, false),
    APPLICABILITY("applicability", WorkKeyKind.REVISION, false),
    EVIDENCE_APPLICABILITY("evidence-applicability", WorkKeyKind.ID, false),
    SOURCE_CUTS("source-cuts", WorkKeyKind.ID, false),
    CONTROLLER_CONTINUATIONS("controller-continuations", WorkKeyKind.CONTROLLER_CONTINUATION, false),
    ASSIGNMENT_REPLACEMENTS("assignment-replacements", WorkKeyKind.ID, false),
    CONDITIONS("conditions", WorkKeyKind.ID, false),
    CONDITION_CONFIRMATIONS("condition-confirmations", WorkKeyKind.ID, false),
    CONTINUATIONS("continuations", WorkKeyKind.ID, true),
}

internal sealed interface WorkKey {
    val projectId: UUID
    val kind: WorkKeyKind

    data class Task(override val projectId: UUID, val taskId: UUID) : WorkKey {
        override val kind get() = WorkKeyKind.TASK
    }
    data class TaskRevision(override val projectId: UUID, val taskId: UUID, val revision: Long) : WorkKey {
        init { require(revision >= 0) }
        override val kind get() = WorkKeyKind.TASK_REVISION
    }
    data class Profile(override val projectId: UUID, val profileId: UUID, val revision: Long) : WorkKey {
        init { require(revision >= 0) }
        override val kind get() = WorkKeyKind.PROFILE
    }
    data class Revision(override val projectId: UUID, val id: UUID, val revision: Long) : WorkKey {
        init { require(revision >= 0) }
        override val kind get() = WorkKeyKind.REVISION
    }
    data class Id(override val projectId: UUID, val id: UUID) : WorkKey {
        override val kind get() = WorkKeyKind.ID
    }
    data class Resource(override val projectId: UUID, val resourceId: UUID) : WorkKey {
        override val kind get() = WorkKeyKind.RESOURCE
    }
    /** generation here is executionGeneration, never the mutable assignment CAS counter. */
    data class AssignmentBinding(override val projectId: UUID, val assignmentId: UUID, val generation: Long) : WorkKey {
        init { require(generation >= 0) }
        override val kind get() = WorkKeyKind.ASSIGNMENT_BINDING
    }
    data class ControllerContinuation(override val projectId: UUID, val controllerId: UUID, val id: UUID) : WorkKey {
        override val kind get() = WorkKeyKind.CONTROLLER_CONTINUATION
    }
}

internal data class WorkRecordIdentity(val family: WorkFamily, val key: WorkKey) {
    init { require(family.keyKind == key.kind) }
}

/** Immutable metadata only: a mutable current pointer is not an immutable input reference. */
internal data class WorkReference(val identity: WorkRecordIdentity, val digest: Digest) {
    init { require(!identity.family.mutable) }
}

internal data class WorkMutableSelector(val identity: WorkRecordIdentity, val generation: Long) {
    init { require(identity.family.mutable && generation >= 0) }
}

internal data class WorkBodyReference(val digest: Digest, val byteLength: Int) {
    init { require(byteLength >= 0) }
}

/** Explicit selection, not model discovery, availability, native qualification or spend authority. */
internal data class WorkModelReference(val providerId: String, val modelId: String, val variant: String?) {
    init {
        fun bounded(text: String, maximum: Int) {
            require(text.length in 1..maximum * 2)
            require(text.codePointCount(0, text.length) in 1..maximum)
            encodeUtf8(text) // rejects unpaired UTF-16 without trimming/normalizing identifiers
        }
        bounded(providerId, 256)
        bounded(modelId, 256)
        variant?.let { bounded(it, 128) }
    }
}
