// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import java.util.UUID

internal enum class Auth2ReadOperation(val tag: String, val selector: String) {
    CREDENTIAL_STATUS("read_credential_status","credentialId"), WORK_GRANT("read_work_grant","principalId"),
    DELEGATION_STATUS("read_delegation_status","delegationId")
}
internal data class Auth2Read(val projectId: UUID, val operation: Auth2ReadOperation, val subjectId: UUID)
/** No verifier/secret-bearing record escapes through the status result types. */
internal sealed interface Auth2ReadResult {
    data object Denied: Auth2ReadResult
    data object Missing: Auth2ReadResult
    data class CredentialStatusResult(val credentialId: UUID, val principalId: UUID, val status: CredentialStatus,
        val generation: Long, val replacedBy: UUID?, val scope: CredentialScope): Auth2ReadResult
    data class WorkGrantResult(val principalId: UUID, val generation: Long, val permissions: Set<WorkPermission>): Auth2ReadResult
    data class DelegationStatusResult(val delegationId: UUID, val binding: DelegationBinding, val generation: Long,
        val status: CredentialStatus): Auth2ReadResult
}
internal class Auth2ReadCodec(private val limits: JsonLimits=auth2Admission()) {
    fun decode(bytes: ByteArray): Auth2Read {
        val f=StrictJson.decode(bytes,limits).fields("formatVersion","projectId","operation","subject")
        if(f.getValue("formatVersion").long()!=2L) refuse(CodecProblem.UNSUPPORTED_FORMAT)
        val op=Auth2ReadOperation.entries.firstOrNull { it.tag==f.getValue("operation").string() } ?: refuse(CodecProblem.INVALID_SHAPE)
        return Auth2Read(uuid(f.getValue("projectId")),op,uuid(f.getValue("subject").fields(op.selector).getValue(op.selector)))
    }
    fun encode(read: Auth2Read): ByteArray=StrictJson.encode(obj("formatVersion" to num(2), "projectId" to uuid(read.projectId),
        "operation" to str(read.operation.tag), "subject" to obj(read.operation.selector to uuid(read.subjectId))),limits.bytes).also { decode(it) }
    fun encodeResult(result: Auth2ReadResult): ByteArray=JsonConstruction.bounded(limits,{ _,_-> },false) {
        StrictJson.encode(when(result) {
            Auth2ReadResult.Denied, Auth2ReadResult.Missing -> error("Transport refusal is not status content")
            is Auth2ReadResult.CredentialStatusResult -> obj("credentialId" to uuid(result.credentialId),"principalId" to uuid(result.principalId),
                "status" to statusJson(result.status),"generation" to JNumber(result.generation),"replacedBy" to nullableUuid(result.replacedBy),"scope" to scopeJson(result.scope))
            is Auth2ReadResult.WorkGrantResult -> obj("principalId" to uuid(result.principalId),"generation" to JNumber(result.generation),"permissions" to permissionJson(result.permissions))
            is Auth2ReadResult.DelegationStatusResult -> obj("delegationId" to uuid(result.delegationId),"binding" to bindingJson(result.binding),"generation" to JNumber(result.generation),"status" to statusJson(result.status))
        },limits.bytes)
    }
    fun decodeResult(operation: Auth2ReadOperation, bytes: ByteArray): Auth2ReadResult {
        val v=StrictJson.decode(bytes,limits)
        return when(operation) {
            Auth2ReadOperation.CREDENTIAL_STATUS -> {
                val f=v.fields("credentialId","principalId","status","generation","replacedBy","scope")
                Auth2ReadResult.CredentialStatusResult(uuid(f.getValue("credentialId")),uuid(f.getValue("principalId")),status(f.getValue("status")),
                    f.getValue("generation").long(),nullableUuid(f.getValue("replacedBy")),scope(f.getValue("scope")))
            }
            Auth2ReadOperation.WORK_GRANT -> {
                val f=v.fields("principalId","generation","permissions")
                Auth2ReadResult.WorkGrantResult(uuid(f.getValue("principalId")),f.getValue("generation").long(),permissions(f.getValue("permissions")))
            }
            Auth2ReadOperation.DELEGATION_STATUS -> {
                val f=v.fields("delegationId","binding","generation","status")
                Auth2ReadResult.DelegationStatusResult(uuid(f.getValue("delegationId")),binding(f.getValue("binding")),f.getValue("generation").long(),status(f.getValue("status")))
            }
        }
    }
}
internal fun Auth2State.readStatus(presented: PresentedCredential, read: Auth2Read): Auth2ReadResult {
    val c=authenticate(presented,read.projectId) ?: return Auth2ReadResult.Denied
    val isOwner=owner(c,read.projectId)
    if(c.scope is CredentialScope.Assignment) return Auth2ReadResult.Denied
    if(!isOwner) {
        // Owning controller proof precedes status retrieval; absence/foreign have the same denial.
        val d=when(read.operation) {
            Auth2ReadOperation.WORK_GRANT -> return Auth2ReadResult.Denied
            Auth2ReadOperation.DELEGATION_STATUS -> read.subjectId
            Auth2ReadOperation.CREDENTIAL_STATUS -> (credentials[read.subjectId]?.scope as? CredentialScope.Assignment)?.delegationId ?: return Auth2ReadResult.Denied
        }
        if(!ownsDelegation(c,read.projectId,d,if(read.operation==Auth2ReadOperation.CREDENTIAL_STATUS)read.subjectId else null)) return Auth2ReadResult.Denied
    }
    return when(read.operation) {
        Auth2ReadOperation.CREDENTIAL_STATUS -> credentials[read.subjectId]?.takeIf { it.credential.projectId==read.projectId }?.let {
            Auth2ReadResult.CredentialStatusResult(it.credential.id,it.credential.principalId,it.credential.status,it.credential.generation,it.credential.replacedBy,it.scope)
        } ?: Auth2ReadResult.Missing
        Auth2ReadOperation.WORK_GRANT -> workGrants[Enrollment(read.subjectId,read.projectId)]?.let {
            Auth2ReadResult.WorkGrantResult(read.subjectId,it.generation,it.permissions)
        } ?: Auth2ReadResult.Missing
        Auth2ReadOperation.DELEGATION_STATUS -> delegations[read.subjectId]?.takeIf { it.binding.projectId==read.projectId }?.let {
            Auth2ReadResult.DelegationStatusResult(it.id,it.binding,it.generation,it.status)
        } ?: Auth2ReadResult.Missing
    }
}
