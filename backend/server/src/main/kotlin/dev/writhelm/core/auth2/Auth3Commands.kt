// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.workflow.*
import java.util.UUID

internal sealed interface Auth3Payload {
    val operation: String
    class ReplaceWorkGrants(val principalId: UUID, val expectedGeneration: Long, val expectedWorkGrantDigest: Digest,
        permissions: Collection<WorkPermission>): Auth3Payload {
        override val operation="replace_work_grants"; val permissions=frozenSet(permissions)
        init { require(expectedGeneration>=0 && this.permissions.size==permissions.size) }
    }
    data class RegisterDelegation(val delegationId: UUID, val binding: DelegationBinding,
        val expectedAssignmentGeneration: Long, val verifierDigest: Digest): Auth3Payload {
        override val operation="register_delegation"; init { require(expectedAssignmentGeneration>=0) }
    }
    data class RotateDelegatedCredential(val delegationId: UUID, val credentialId: UUID, val expectedGeneration: Long,
        val expectedDelegationGeneration: Long, val expectedAssignmentGeneration: Long, val newCredentialId: UUID,
        val newVerifierDigest: Digest): Auth3Payload {
        override val operation="rotate_delegated_credential"
        init { require(expectedGeneration>=0 && expectedDelegationGeneration>=0 && expectedAssignmentGeneration>=0) }
    }
    data class RevokeDelegatedCredential(val delegationId: UUID, val credentialId: UUID, val expectedGeneration: Long,
        val expectedDelegationGeneration: Long): Auth3Payload {
        override val operation="revoke_delegated_credential"; init { require(expectedGeneration>=0 && expectedDelegationGeneration>=0) }
    }
    data class RevokeDelegation(val delegationId: UUID, val expectedGeneration: Long): Auth3Payload {
        override val operation="revoke_delegation"; init { require(expectedGeneration>=0) }
    }
}
internal data class Auth3Command(val requestKey: UUID, val projectId: UUID, val payload: Auth3Payload)
internal class Auth3CommandCodec(private val limits: JsonLimits=auth2Admission()) {
    fun decode(bytes: ByteArray): Auth3Command {
        val f=StrictJson.decode(bytes,limits).fields("formatVersion", "requestKey", "projectId", "operation", "payload")
        if(f.getValue("formatVersion").long()!=3L) refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val v=f.getValue("payload")
        val p=when(f.getValue("operation").string()) {
            "replace_work_grants" -> { val q=v.fields("principalId", "expectedGeneration", "expectedWorkGrantDigest", "permissions")
                Auth3Payload.ReplaceWorkGrants(uuid(q.getValue("principalId")),q.getValue("expectedGeneration").long(),digest(q.getValue("expectedWorkGrantDigest")),permissions(q.getValue("permissions"))) }
            "register_delegation" -> { val q=v.fields("delegationId", "binding", "expectedAssignmentGeneration", "verifierVersion", "verifierDigest", "knowledgeCapabilities")
                version(q.getValue("verifierVersion")); val caps=q.getValue("knowledgeCapabilities").list(); shape(caps.size==1 && caps.single().string()=="READ")
                Auth3Payload.RegisterDelegation(uuid(q.getValue("delegationId")),binding(q.getValue("binding")),q.getValue("expectedAssignmentGeneration").long(),digest(q.getValue("verifierDigest"))) }
            "rotate_delegated_credential" -> { val q=v.fields("delegationId", "credentialId", "expectedGeneration", "expectedDelegationGeneration", "expectedAssignmentGeneration", "newCredentialId", "verifierVersion", "newVerifierDigest")
                version(q.getValue("verifierVersion")); Auth3Payload.RotateDelegatedCredential(uuid(q.getValue("delegationId")),uuid(q.getValue("credentialId")),q.getValue("expectedGeneration").long(),q.getValue("expectedDelegationGeneration").long(),q.getValue("expectedAssignmentGeneration").long(),uuid(q.getValue("newCredentialId")),digest(q.getValue("newVerifierDigest"))) }
            "revoke_delegated_credential" -> { val q=v.fields("delegationId", "credentialId", "expectedGeneration", "expectedDelegationGeneration")
                Auth3Payload.RevokeDelegatedCredential(uuid(q.getValue("delegationId")),uuid(q.getValue("credentialId")),q.getValue("expectedGeneration").long(),q.getValue("expectedDelegationGeneration").long()) }
            "revoke_delegation" -> { val q=v.fields("delegationId", "expectedGeneration"); Auth3Payload.RevokeDelegation(uuid(q.getValue("delegationId")),q.getValue("expectedGeneration").long()) }
            else -> refuse(CodecProblem.INVALID_SHAPE)
        }
        return Auth3Command(uuid(f.getValue("requestKey")),uuid(f.getValue("projectId")),p)
    }
    fun encode(c: Auth3Command): ByteArray = JsonConstruction.bounded(limits,{ _,_-> },false) {
        StrictJson.encode(obj("formatVersion" to num(3), "requestKey" to uuid(c.requestKey), "projectId" to uuid(c.projectId),
            "operation" to str(c.payload.operation), "payload" to payload(c.payload)),limits.bytes)
    }.also { decode(it) }
    fun fingerprint(c: Auth3Command): Digest {
        val f=(StrictJson.decode(encode(c),limits) as JObject).entries
        val bytes=StrictJson.encode(JObject(f-"requestKey"),limits.bytes)
        return framedDigest("writhelm.command.v3") { writeFramed(bytes) }
    }
    private fun payload(p: Auth3Payload): JsonValue = when(p) {
        is Auth3Payload.ReplaceWorkGrants -> obj("principalId" to uuid(p.principalId), "expectedGeneration" to JNumber(p.expectedGeneration), "expectedWorkGrantDigest" to digest(p.expectedWorkGrantDigest), "permissions" to permissionJson(p.permissions))
        is Auth3Payload.RegisterDelegation -> obj("delegationId" to uuid(p.delegationId), "binding" to bindingJson(p.binding), "expectedAssignmentGeneration" to JNumber(p.expectedAssignmentGeneration), "verifierVersion" to num(1), "verifierDigest" to digest(p.verifierDigest), "knowledgeCapabilities" to JArray(listOf(str("READ"))))
        is Auth3Payload.RotateDelegatedCredential -> obj("delegationId" to uuid(p.delegationId), "credentialId" to uuid(p.credentialId), "expectedGeneration" to JNumber(p.expectedGeneration), "expectedDelegationGeneration" to JNumber(p.expectedDelegationGeneration), "expectedAssignmentGeneration" to JNumber(p.expectedAssignmentGeneration), "newCredentialId" to uuid(p.newCredentialId), "verifierVersion" to num(1), "newVerifierDigest" to digest(p.newVerifierDigest))
        is Auth3Payload.RevokeDelegatedCredential -> obj("delegationId" to uuid(p.delegationId), "credentialId" to uuid(p.credentialId), "expectedGeneration" to JNumber(p.expectedGeneration), "expectedDelegationGeneration" to JNumber(p.expectedDelegationGeneration))
        is Auth3Payload.RevokeDelegation -> obj("delegationId" to uuid(p.delegationId), "expectedGeneration" to JNumber(p.expectedGeneration))
    }
}
internal enum class Auth2Problem {
    AUTH_TARGET_NOT_FOUND, AUTH_STALE_CREDENTIAL, AUTH_STALE_GRANT, AUTH_ALREADY_REVOKED,
    AUTH_ALREADY_REPLACED, AUTH_DUPLICATE_CREDENTIAL, AUTH_OWNER_LOCKOUT, AUTH_FIXED_OWNER_POLICY,
    AUTH_GENERATION_EXHAUSTED, AUTH_DUPLICATE_ID, AUTH_STALE_DELEGATION, AUTH_STALE_ASSIGNMENT,
    AUTH_INVALID_BINDING, AUTH_TEMPLATE_CONFLICT, AUTH_SCOPE_CLOSED
}
internal sealed interface Auth3Transition {
    data object Denied: Auth3Transition
    data class Rejected(val problem: Auth2Problem): Auth3Transition
    class Success internal constructor(val candidate: Auth2State, val operation: String, val resultId: UUID,
        val replacedCredentialId: UUID?, val authenticatedByCredentialId: UUID,
        val enrollmentEffects: DelegationEnrollmentEffects?): Auth3Transition
}
internal object Auth3OutcomeCodec {
    fun encode(t: Auth3Transition): ByteArray = StrictJson.encode(when(t) {
        Auth3Transition.Denied -> error("A denial is not a durable outcome")
        is Auth3Transition.Rejected -> obj("kind" to str("rejected"), "domain" to str("auth"), "code" to str(t.problem.name))
        is Auth3Transition.Success -> obj("kind" to str("success"), "operation" to str(t.operation),
            "result" to (if(t.operation=="rotate_delegated_credential") obj("credentialId" to uuid(t.resultId), "replacedCredentialId" to uuid(requireNotNull(t.replacedCredentialId))) else uuid(t.resultId)), "domainReplayed" to JBoolean(false))
    },16384)
    fun decode(bytes: ByteArray): Auth3Outcome {
        val v=StrictJson.decode(bytes,auth2Admission())
        return when((v as? JObject)?.entries?.get("kind")?.string()) {
            "rejected" -> {
                val f=v.fields("kind","domain","code"); shape(f.getValue("domain").string()=="auth")
                val code=Auth2Problem.entries.firstOrNull { it.name==f.getValue("code").string() } ?: refuse(CodecProblem.INVALID_SHAPE)
                Auth3Outcome.Rejected(code)
            }
            "success" -> {
                val f=v.fields("kind","operation","result","domainReplayed")
                shape((f.getValue("domainReplayed") as? JBoolean)?.value==false)
                val operation=f.getValue("operation").string()
                shape(operation in setOf("replace_work_grants","register_delegation","rotate_delegated_credential","revoke_delegated_credential","revoke_delegation"))
                if(operation=="rotate_delegated_credential") {
                    val r=f.getValue("result").fields("credentialId","replacedCredentialId")
                    Auth3Outcome.Success(operation,uuid(r.getValue("credentialId")),uuid(r.getValue("replacedCredentialId")))
                } else Auth3Outcome.Success(operation,uuid(f.getValue("result")),null)
            }
            else -> refuse(CodecProblem.INVALID_SHAPE)
        }
    }
}
internal sealed interface Auth3Outcome {
    data class Success(val operation: String, val resultId: UUID, val replacedCredentialId: UUID?): Auth3Outcome
    data class Rejected(val problem: Auth2Problem): Auth3Outcome
}
