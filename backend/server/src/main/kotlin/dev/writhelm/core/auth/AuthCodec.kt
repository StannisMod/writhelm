// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal fun authLimits()=JsonLimits(bytes=16384,stringBytes=1024,depth=16,collection=64,tokens=2048)
internal sealed interface AuthPayload {
    val operation: String
    class ReplaceGrants(val principalId: UUID,val expectedGeneration: Long,val expectedGrantDigest: Digest,capabilities: Set<Capability>): AuthPayload {
        override val operation="replace_grants"; val capabilities=frozenSet(capabilities)
        init { require(expectedGeneration>=0) }
    }
    data class RevokeCredential(val credentialId: UUID,val expectedGeneration: Long): AuthPayload {
        override val operation="revoke_credential"; init { require(expectedGeneration>=0) }
    }
    data class RotateCredential(val credentialId: UUID,val expectedGeneration: Long,val newCredentialId: UUID,val newVerifierDigest: Digest): AuthPayload {
        override val operation="rotate_credential"; init { require(expectedGeneration>=0) }
    }
}
internal class AuthCommand(val requestKey: UUID,val projectId: UUID,val payload: AuthPayload)
internal class AuthCommandCodec(private val limits: JsonLimits=authLimits()) {
    fun decode(bytes: ByteArray): AuthCommand {
        val f=StrictJson.decode(bytes,limits).fields("formatVersion","requestKey","projectId","operation","payload")
        if(f.getValue("formatVersion").long()!=2L) refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val p=f.getValue("payload")
        val payload=when(f.getValue("operation").string()) {
            "replace_grants" -> { val q=p.fields("principalId","expectedGeneration","expectedGrantDigest","capabilities")
                AuthPayload.ReplaceGrants(uuid(q.getValue("principalId")),q.getValue("expectedGeneration").long(),digest(q.getValue("expectedGrantDigest")),capabilities(q.getValue("capabilities"))) }
            "revoke_credential" -> { val q=p.fields("credentialId","expectedGeneration"); AuthPayload.RevokeCredential(uuid(q.getValue("credentialId")),q.getValue("expectedGeneration").long()) }
            "rotate_credential" -> { val q=p.fields("credentialId","expectedGeneration","newCredentialId","verifierVersion","newVerifierDigest"); version(q.getValue("verifierVersion"))
                AuthPayload.RotateCredential(uuid(q.getValue("credentialId")),q.getValue("expectedGeneration").long(),uuid(q.getValue("newCredentialId")),digest(q.getValue("newVerifierDigest"))) }
            else -> refuse(CodecProblem.INVALID_SHAPE)
        }
        return AuthCommand(uuid(f.getValue("requestKey")),uuid(f.getValue("projectId")),payload)
    }
    fun encode(c: AuthCommand): ByteArray = JsonConstruction.bounded(limits,{ _,_-> },false) {
        StrictJson.encode(obj("formatVersion" to num(2),"requestKey" to uuid(c.requestKey),"projectId" to uuid(c.projectId),"operation" to str(c.payload.operation),"payload" to payload(c.payload)),limits.bytes)
    }.also { decode(it) }
    fun fingerprint(c: AuthCommand): Digest {
        val f=(StrictJson.decode(encode(c),limits) as JObject).entries
        val b=StrictJson.encode(JObject(f-"requestKey"),limits.bytes)
        return framedDigest("writhelm.command.v2") { writeFramed(b) }
    }
    private fun payload(p: AuthPayload): JsonValue=when(p) {
        is AuthPayload.ReplaceGrants -> obj("principalId" to uuid(p.principalId),"expectedGeneration" to JNumber(p.expectedGeneration),"expectedGrantDigest" to digest(p.expectedGrantDigest),"capabilities" to caps(p.capabilities))
        is AuthPayload.RevokeCredential -> obj("credentialId" to uuid(p.credentialId),"expectedGeneration" to JNumber(p.expectedGeneration))
        is AuthPayload.RotateCredential -> obj("credentialId" to uuid(p.credentialId),"expectedGeneration" to JNumber(p.expectedGeneration),"newCredentialId" to uuid(p.newCredentialId),"verifierVersion" to num(1),"newVerifierDigest" to digest(p.newVerifierDigest))
    }
}
private fun capabilities(v: JsonValue): Set<Capability> {
    val list=v.list().map { t -> Capability.entries.firstOrNull { it.name==t.string() } ?: refuse(CodecProblem.INVALID_SHAPE) }
    shape(list.distinct().size==list.size); return frozenSet(list)
}
private fun caps(c: Set<Capability>)=JArray(c.sortedBy { it.name }.map { str(it.name) })

internal sealed interface AuthRecord {
    data class Principal(val principalId: UUID): AuthRecord
    data class Project(val binding: ProjectBinding): AuthRecord
    data class CredentialRecord(val credential: Credential): AuthRecord
    data class Guard(val enrollment: Enrollment,val generation: Long): AuthRecord { init { require(generation>=0) } }
}
internal class AuthRecordCodec(private val limits: JsonLimits=authLimits()) {
    fun header(): ByteArray=JsonConstruction.bounded(limits,{ _,_-> },false) {
        StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)),limits.bytes)
    }.also { checkHeader(it) }
    fun checkHeader(bytes: ByteArray) { val f=StrictJson.decode(bytes,limits).fields("formatVersion","profileVersion"); version(f.getValue("formatVersion")); version(f.getValue("profileVersion")) }
    fun path(r: AuthRecord): String {
        val (family,key)=identity(r)
        val h=framedDigest("writhelm.record-key.v1") { writeFramed(encodeUtf8(family)); writeFramed(StrictJson.encode(key,limits.bytes)) }.hex
        return "auth/records/$family/${h.take(2)}/$h.json"
    }
    fun encode(r: AuthRecord): ByteArray=JsonConstruction.bounded(limits,{ _,_-> },false) {
        val (family,key)=identity(r)
        val payload=when(r) {
            is AuthRecord.Principal -> obj()
            is AuthRecord.Project -> obj("ownerPrincipalId" to uuid(r.binding.ownerPrincipalId),"profileVersion" to num(1))
            is AuthRecord.Guard -> obj("generation" to JNumber(r.generation))
            is AuthRecord.CredentialRecord -> with(r.credential) { obj("storeId" to uuid(storeId),"projectId" to uuid(projectId),"principalId" to uuid(principalId),"verifierVersion" to num(1),"verifierDigest" to digest(verifierDigest),"status" to str(if(status==CredentialStatus.ACTIVE)"active" else "revoked"),"generation" to JNumber(generation),"replacedBy" to (replacedBy?.let { uuid(it) } ?: JNull)) }
        }
        StrictJson.encode(obj("formatVersion" to num(1),"family" to str(family),"key" to key,"payload" to payload),limits.bytes)
    }.also { decode(it,path(r)) }
    fun decode(bytes: ByteArray,expectedPath: String): AuthRecord {
        val f=StrictJson.decode(bytes,limits).fields("formatVersion","family","key","payload"); version(f.getValue("formatVersion"))
        val key=f.getValue("key"); val p=f.getValue("payload")
        val r=when(f.getValue("family").string()) {
            "principals" -> { p.fields(); AuthRecord.Principal(uuid(key)) }
            "projects" -> { val q=p.fields("ownerPrincipalId","profileVersion"); version(q.getValue("profileVersion")); AuthRecord.Project(ProjectBinding(uuid(key),uuid(q.getValue("ownerPrincipalId")))) }
            "grant-guards" -> { val k=key.fields("principalId","projectId"); val q=p.fields("generation"); AuthRecord.Guard(Enrollment(uuid(k.getValue("principalId")),uuid(k.getValue("projectId"))),q.getValue("generation").long()) }
            "credentials" -> { val q=p.fields("storeId","projectId","principalId","verifierVersion","verifierDigest","status","generation","replacedBy"); version(q.getValue("verifierVersion"))
                val status=when(q.getValue("status").string()) { "active" -> CredentialStatus.ACTIVE; "revoked" -> CredentialStatus.REVOKED; else -> refuse(CodecProblem.INVALID_SHAPE) }
                val next=q.getValue("replacedBy").let { if(it==JNull)null else uuid(it) }; shape(status!=CredentialStatus.ACTIVE || next==null)
                AuthRecord.CredentialRecord(Credential(uuid(key),uuid(q.getValue("storeId")),uuid(q.getValue("projectId")),uuid(q.getValue("principalId")),digest(q.getValue("verifierDigest")),status,q.getValue("generation").long(),next)) }
            else -> refuse(CodecProblem.UNSUPPORTED_FORMAT)
        }
        graph(path(r)==expectedPath); return r
    }
    private fun identity(r: AuthRecord): Pair<String,JsonValue> = when(r) {
        is AuthRecord.Principal -> "principals" to uuid(r.principalId)
        is AuthRecord.Project -> "projects" to uuid(r.binding.projectId)
        is AuthRecord.CredentialRecord -> "credentials" to uuid(r.credential.id)
        is AuthRecord.Guard -> "grant-guards" to obj("principalId" to uuid(r.enrollment.principalId),"projectId" to uuid(r.enrollment.projectId))
    }
}
