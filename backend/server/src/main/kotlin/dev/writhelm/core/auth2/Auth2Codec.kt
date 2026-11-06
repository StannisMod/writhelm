// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import java.util.UUID

internal fun auth2Admission() = JsonLimits(bytes=16384, stringBytes=1024, depth=16, collection=64, tokens=2048)
internal fun auth2History() = JsonLimits(bytes=65536, stringBytes=1024, depth=32, collection=256, tokens=32768)
internal class Auth2RecordCodec(private val limits: JsonLimits = auth2History()) {
    fun header(): ByteArray = StrictJson.encode(obj("formatVersion" to num(2), "profileVersion" to num(2)))
    fun checkHeader(bytes: ByteArray) {
        val f = StrictJson.decode(bytes, limits).fields("formatVersion", "profileVersion")
        if (f.getValue("formatVersion").long()!=2L || f.getValue("profileVersion").long()!=2L) refuse(CodecProblem.UNSUPPORTED_FORMAT)
    }
    fun identity(r: Auth2Record): Pair<String, JsonValue> = when (r) {
        is Auth2Record.Principal -> "principals" to uuid(r.id)
        is Auth2Record.Project -> "projects" to uuid(r.binding.projectId)
        is Auth2Record.Key -> "credentials" to uuid(r.value.credential.id)
        is Auth2Record.Guard -> "grant-guards" to grantKey(r.enrollment)
        is Auth2Record.Controller -> "controllers" to uuid(r.binding.controllerId)
        is Auth2Record.WorkGrants -> "work-grants" to grantKey(r.value.enrollment)
        is Auth2Record.DelegationRecord -> "delegations" to uuid(r.value.id)
    }
    fun path(r: Auth2Record): String {
        val (family, key) = identity(r)
        val h = framedDigest("writhelm.record-key.v1") { writeFramed(encodeUtf8(family)); writeFramed(StrictJson.encode(key, limits.bytes)) }.hex
        return "auth/records/$family/${h.take(2)}/$h.json"
    }
    fun encode(r: Auth2Record): ByteArray = JsonConstruction.bounded(limits, { _,_-> }, false) {
        val (family, key) = identity(r)
        val p = when (r) {
            is Auth2Record.Principal -> obj()
            is Auth2Record.Project -> obj("ownerPrincipalId" to uuid(r.binding.ownerPrincipalId), "profileVersion" to num(2))
            is Auth2Record.Key -> with(r.value.credential) {
                obj("storeId" to uuid(storeId), "projectId" to uuid(projectId), "principalId" to uuid(principalId),
                    "verifierVersion" to num(1), "verifierDigest" to digest(verifierDigest), "status" to statusJson(status),
                    "generation" to JNumber(generation), "replacedBy" to nullableUuid(replacedBy), "scope" to scopeJson(r.value.scope))
            }
            is Auth2Record.Guard -> obj("generation" to JNumber(r.generation))
            is Auth2Record.Controller -> obj("projectId" to uuid(r.binding.projectId), "principalId" to uuid(r.binding.principalId))
            is Auth2Record.WorkGrants -> obj("generation" to JNumber(r.value.generation), "permissions" to permissionJson(r.value.permissions))
            is Auth2Record.DelegationRecord -> obj("binding" to bindingJson(r.value.binding), "generation" to JNumber(r.value.generation), "status" to statusJson(r.value.status))
        }
        StrictJson.encode(obj("formatVersion" to num(2), "family" to str(family), "key" to key, "payload" to p), limits.bytes)
    }
    fun decode(bytes: ByteArray, expectedPath: String, canonical: Boolean = true): Auth2Record {
        val f = StrictJson.decode(bytes, limits).fields("formatVersion", "family", "key", "payload")
        if (f.getValue("formatVersion").long()!=2L) refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val k=f.getValue("key"); val p=f.getValue("payload")
        val r=when (f.getValue("family").string()) {
            "principals" -> { p.fields(); Auth2Record.Principal(uuid(k)) }
            "projects" -> { val q=p.fields("ownerPrincipalId", "profileVersion"); shape(q.getValue("profileVersion").long()==2L); Auth2Record.Project(ProjectBinding(uuid(k),uuid(q.getValue("ownerPrincipalId")))) }
            "grant-guards" -> { val q=p.fields("generation"); Auth2Record.Guard(enrollment(k),q.getValue("generation").long()) }
            "controllers" -> { val q=p.fields("projectId", "principalId"); Auth2Record.Controller(ControllerBinding(uuid(k),uuid(q.getValue("projectId")),uuid(q.getValue("principalId")))) }
            "work-grants" -> { val q=p.fields("generation", "permissions"); Auth2Record.WorkGrants(WorkGrant(enrollment(k),q.getValue("generation").long(),permissions(q.getValue("permissions")))) }
            "credentials" -> {
                val q=p.fields("storeId", "projectId", "principalId", "verifierVersion", "verifierDigest", "status", "generation", "replacedBy", "scope")
                version(q.getValue("verifierVersion")); val status=status(q.getValue("status")); val next=nullableUuid(q.getValue("replacedBy"))
                shape(status!=CredentialStatus.ACTIVE || next==null)
                Auth2Record.Key(ScopedCredential(Credential(uuid(k),uuid(q.getValue("storeId")),uuid(q.getValue("projectId")),uuid(q.getValue("principalId")),digest(q.getValue("verifierDigest")),status,q.getValue("generation").long(),next),scope(q.getValue("scope"))))
            }
            "delegations" -> { val q=p.fields("binding", "generation", "status"); Auth2Record.DelegationRecord(Delegation(uuid(k),binding(q.getValue("binding")),q.getValue("generation").long(),status(q.getValue("status")))) }
            else -> refuse(CodecProblem.UNSUPPORTED_FORMAT)
        }
        graph(path(r)==expectedPath)
        if (canonical) shape(bytes.contentEquals(encode(r)))
        return r
    }
}
internal fun grantKey(e: Enrollment) = obj("principalId" to uuid(e.principalId), "projectId" to uuid(e.projectId))
private fun enrollment(v: JsonValue): Enrollment { val f=v.fields("principalId", "projectId"); return Enrollment(uuid(f.getValue("principalId")),uuid(f.getValue("projectId"))) }
internal fun nullableUuid(v: UUID?): JsonValue = v?.let(::uuid) ?: JNull
internal fun nullableUuid(v: JsonValue): UUID? = if(v===JNull) null else uuid(v)
internal fun statusJson(s: CredentialStatus) = str(if(s==CredentialStatus.ACTIVE) "active" else "revoked")
internal fun status(v: JsonValue): CredentialStatus = when(v.string()) { "active" -> CredentialStatus.ACTIVE; "revoked" -> CredentialStatus.REVOKED; else -> refuse(CodecProblem.INVALID_SHAPE) }
internal fun scopeJson(s: CredentialScope): JsonValue = when(s) {
    CredentialScope.Project -> obj("kind" to str("project"))
    is CredentialScope.Controller -> obj("kind" to str("controller"), "controllerId" to uuid(s.controllerId))
    is CredentialScope.Assignment -> obj("kind" to str("assignment"), "delegationId" to uuid(s.delegationId))
}
internal fun scope(v: JsonValue): CredentialScope {
    val kind=(v as? JObject)?.entries?.get("kind")?.string() ?: refuse(CodecProblem.INVALID_SHAPE)
    return when(kind) {
        "project" -> { v.fields("kind"); CredentialScope.Project }
        "controller" -> CredentialScope.Controller(uuid(v.fields("kind", "controllerId").getValue("controllerId")))
        "assignment" -> CredentialScope.Assignment(uuid(v.fields("kind", "delegationId").getValue("delegationId")))
        else -> refuse(CodecProblem.INVALID_SHAPE)
    }
}
internal fun permissionJson(p: Set<WorkPermission>) = JArray(p.sortedBy { it.name }.map { str(it.name) })
internal fun permissions(v: JsonValue): Set<WorkPermission> {
    val p=v.list().map { t -> WorkPermission.entries.firstOrNull { it.name==t.string() } ?: refuse(CodecProblem.INVALID_SHAPE) }
    shape(p.size<=4 && p.distinct().size==p.size); return p.toSet()
}
private val refs=WorkIdentityCodec()
internal fun bindingJson(b: DelegationBinding): JsonValue = with(b) {
    obj("controllerId" to uuid(controllerId), "projectId" to uuid(projectId), "principalId" to uuid(principalId),
        "initialCredentialId" to uuid(initialCredentialId), "taskId" to uuid(taskId), "assignmentId" to uuid(assignmentId),
        "executionId" to uuid(executionId), "executionGeneration" to JNumber(executionGeneration), "template" to str(template.tag),
        "subjectResults" to JArray(subjectResults.map { StrictJson.decode(refs.encodeReference(it)) }))
}
internal fun binding(v: JsonValue): DelegationBinding {
    val f=v.fields("controllerId", "projectId", "principalId", "initialCredentialId", "taskId", "assignmentId", "executionId", "executionGeneration", "template", "subjectResults")
    val template=DelegationTemplate.entries.firstOrNull { it.tag==f.getValue("template").string() } ?: refuse(CodecProblem.INVALID_SHAPE)
    val subjects=f.getValue("subjectResults").list(); shape(subjects.size<=256)
    val results=subjects.map { refs.decodeReference(StrictJson.encode(it,4096)) }
    shape(results.all { it.identity.family==WorkFamily.RESULTS && it.identity.key.projectId==uuid(f.getValue("projectId")) })
    shape(results.map { it.identity }.distinct().size==results.size)
    shape(if(template==DelegationTemplate.EXECUTOR) results.isEmpty() else results.isNotEmpty())
    return DelegationBinding(uuid(f.getValue("controllerId")),uuid(f.getValue("projectId")),uuid(f.getValue("principalId")),uuid(f.getValue("initialCredentialId")),uuid(f.getValue("taskId")),uuid(f.getValue("assignmentId")),uuid(f.getValue("executionId")),f.getValue("executionGeneration").long(),template,results)
}
