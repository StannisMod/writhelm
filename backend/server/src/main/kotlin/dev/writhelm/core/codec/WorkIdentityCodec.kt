// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.knowledge.encodeUtf8
import dev.writhelm.core.knowledge.framedDigest
import dev.writhelm.core.knowledge.writeFramed
import dev.writhelm.core.work.*

/** Structural identity admission only. Does not read a store or authenticate/authorize any action. */
internal class WorkIdentityCodec(private val limits: JsonLimits = JsonLimits(
    bytes = 4096, stringBytes = 1024, nameBytes = 64, depth = 8, collection = 8, tokens = 128,
)) {
    fun decodeKey(family: WorkFamily, bytes: ByteArray): WorkKey = key(family.keyKind, StrictJson.decode(bytes, limits))
    fun encodeKey(value: WorkKey): ByteArray = encode { key(value) }

    fun decodeReference(bytes: ByteArray): WorkReference {
        val f = StrictJson.decode(bytes, limits).fields("family", "key", "digest")
        val identity = identity(f.getValue("family"), f.getValue("key"))
        return checked { WorkReference(identity, digest(f.getValue("digest"))) }
    }
    fun encodeReference(value: WorkReference): ByteArray = encode {
        obj("family" to str(value.identity.family.tag), "key" to key(value.identity.key), "digest" to digest(value.digest))
    }

    fun decodeMutableSelector(bytes: ByteArray): WorkMutableSelector {
        val f = StrictJson.decode(bytes, limits).fields("family", "key", "generation")
        val identity = identity(f.getValue("family"), f.getValue("key"))
        return checked { WorkMutableSelector(identity, f.getValue("generation").long()) }
    }
    fun encodeMutableSelector(value: WorkMutableSelector): ByteArray = encode {
        obj("family" to str(value.identity.family.tag), "key" to key(value.identity.key), "generation" to JNumber(value.generation))
    }

    fun decodeBodyReference(bytes: ByteArray): WorkBodyReference {
        val f = StrictJson.decode(bytes, limits).fields("digest", "byteLength")
        return checked { WorkBodyReference(digest(f.getValue("digest")), f.getValue("byteLength").int()) }
    }
    fun encodeBodyReference(value: WorkBodyReference): ByteArray = encode {
        obj("digest" to digest(value.digest), "byteLength" to num(value.byteLength))
    }

    fun decodeModelReference(bytes: ByteArray): WorkModelReference {
        val f = StrictJson.decode(bytes, limits).fields("providerId", "modelId", "variant")
        return checked { WorkModelReference(f.getValue("providerId").string(), f.getValue("modelId").string(),
            f.getValue("variant").let { if (it === JNull) null else it.string() }) }
    }
    fun encodeModelReference(value: WorkModelReference): ByteArray = encode {
        obj("providerId" to str(value.providerId), "modelId" to str(value.modelId),
            "variant" to (value.variant?.let(::str) ?: JNull))
    }

    /** Generated logical Git path, not a platform filesystem adapter or caller-selected path. */
    fun recordPath(identity: WorkRecordIdentity): String {
        val encoded = encodeKey(identity.key)
        val hash = framedDigest("writhelm.record-key.v1") {
            writeFramed(encodeUtf8(identity.family.tag)); writeFramed(encoded)
        }.hex
        return "work/records/${identity.family.tag}/${hash.take(2)}/$hash.json"
    }
    fun bodyPath(value: WorkBodyReference): String = "work/bodies/${value.digest.hex.take(2)}/${value.digest.hex}.txt"

    private fun encode(value: () -> JsonValue): ByteArray = JsonConstruction.bounded(limits, { _, _ -> }, false) {
        StrictJson.encode(value(), limits.bytes, maxDepth = limits.depth)
    }
    private fun identity(family: JsonValue, value: JsonValue): WorkRecordIdentity {
        val tag = family.string()
        val selected = WorkFamily.entries.firstOrNull { it.tag == tag } ?: refuse(CodecProblem.INVALID_SHAPE)
        return WorkRecordIdentity(selected, key(selected.keyKind, value))
    }
    private fun key(value: WorkKey): JsonValue = when (value) {
        is WorkKey.Task -> obj("projectId" to uuid(value.projectId), "taskId" to uuid(value.taskId))
        is WorkKey.TaskRevision -> obj("projectId" to uuid(value.projectId), "taskId" to uuid(value.taskId), "revision" to JNumber(value.revision))
        is WorkKey.Profile -> obj("projectId" to uuid(value.projectId), "profileId" to uuid(value.profileId), "revision" to JNumber(value.revision))
        is WorkKey.Revision -> obj("projectId" to uuid(value.projectId), "id" to uuid(value.id), "revision" to JNumber(value.revision))
        is WorkKey.Id -> obj("projectId" to uuid(value.projectId), "id" to uuid(value.id))
        is WorkKey.Resource -> obj("projectId" to uuid(value.projectId), "resourceId" to uuid(value.resourceId))
        is WorkKey.AssignmentBinding -> obj("projectId" to uuid(value.projectId), "assignmentId" to uuid(value.assignmentId), "generation" to JNumber(value.generation))
        is WorkKey.ControllerContinuation -> obj("projectId" to uuid(value.projectId), "controllerId" to uuid(value.controllerId), "id" to uuid(value.id))
    }
    private fun key(kind: WorkKeyKind, value: JsonValue): WorkKey = when (kind) {
        WorkKeyKind.TASK -> value.fields("projectId", "taskId").let {
            WorkKey.Task(uuid(it.getValue("projectId")), uuid(it.getValue("taskId"))) }
        WorkKeyKind.TASK_REVISION -> value.fields("projectId", "taskId", "revision").let {
            WorkKey.TaskRevision(uuid(it.getValue("projectId")), uuid(it.getValue("taskId")), it.getValue("revision").long()) }
        WorkKeyKind.PROFILE -> value.fields("projectId", "profileId", "revision").let {
            WorkKey.Profile(uuid(it.getValue("projectId")), uuid(it.getValue("profileId")), it.getValue("revision").long()) }
        WorkKeyKind.REVISION -> value.fields("projectId", "id", "revision").let {
            WorkKey.Revision(uuid(it.getValue("projectId")), uuid(it.getValue("id")), it.getValue("revision").long()) }
        WorkKeyKind.ID -> value.fields("projectId", "id").let {
            WorkKey.Id(uuid(it.getValue("projectId")), uuid(it.getValue("id"))) }
        WorkKeyKind.RESOURCE -> value.fields("projectId", "resourceId").let {
            WorkKey.Resource(uuid(it.getValue("projectId")), uuid(it.getValue("resourceId"))) }
        WorkKeyKind.ASSIGNMENT_BINDING -> value.fields("projectId", "assignmentId", "generation").let {
            WorkKey.AssignmentBinding(uuid(it.getValue("projectId")), uuid(it.getValue("assignmentId")), it.getValue("generation").long()) }
        WorkKeyKind.CONTROLLER_CONTINUATION -> value.fields("projectId", "controllerId", "id").let {
            WorkKey.ControllerContinuation(uuid(it.getValue("projectId")), uuid(it.getValue("controllerId")), uuid(it.getValue("id"))) }
    }
}
