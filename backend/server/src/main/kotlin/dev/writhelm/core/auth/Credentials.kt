// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth

import dev.writhelm.core.knowledge.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

internal enum class CredentialStatus { ACTIVE, REVOKED }
internal data class Credential(val id: UUID,val storeId: UUID,val projectId: UUID,val principalId: UUID,
    val verifierDigest: Digest,val status: CredentialStatus,val generation: Long,val replacedBy: UUID?=null) {
    init { require(generation>=0); require(status!=CredentialStatus.ACTIVE || replacedBy==null) }
}
internal data class ProjectBinding(val projectId: UUID,val ownerPrincipalId: UUID)
internal data class Enrollment(val principalId: UUID,val projectId: UUID)

/** Request-local only. No raw secret accessor or secret-bearing generated toString/equals. */
internal class PresentedCredential private constructor(val storeId: UUID,val credentialId: UUID,private val secret: ByteArray) : AutoCloseable {
    private var closed=false
    fun verifies(c: Credential): Boolean {
        check(!closed)
        val actual=CredentialVerifier.digest(c.storeId,c.projectId,c.principalId,c.id,secret).bytes()
        val expected=c.verifierDigest.bytes()
        return try { MessageDigest.isEqual(expected,actual) } finally { actual.fill(0); expected.fill(0) }
    }
    override fun close() { secret.fill(0); closed=true }
    override fun toString()="PresentedCredential(redacted)"
    companion object {
        fun parse(text: String): PresentedCredential? {
            if(text.length!=121 || text.any { it.code>127 }) return null
            val parts=text.split('.')
            if(parts.size!=4 || parts[0]!="wh1" || !canonicalUuid(parts[1]) || !canonicalUuid(parts[2]) || !parts[3].matches(Regex("[A-Za-z0-9_-]{43}"))) return null
            val bytes=try { Base64.getUrlDecoder().decode(parts[3]) } catch (_: IllegalArgumentException) { return null }
            if(bytes.size!=32 || Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)!=parts[3]) { bytes.fill(0); return null }
            return PresentedCredential(UUID.fromString(parts[1]),UUID.fromString(parts[2]),bytes)
        }
    }
}
internal object CredentialVerifier {
    fun parse(text: String): PresentedCredential? = PresentedCredential.parse(text)
    fun token(storeId: UUID,credentialId: UUID,secret: ByteArray): String {
        require(secret.size==32) { "Expected 32 secret bytes" }
        return "wh1.$storeId.$credentialId."+Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
    }
    /** Streaming framing avoids a second secret-bearing framed buffer. Caller owns the input. */
    fun digest(storeId: UUID,projectId: UUID,principalId: UUID,credentialId: UUID,secret: ByteArray): Digest {
        require(secret.size==32) { "Expected 32 secret bytes" }
        val hash=MessageDigest.getInstance("SHA-256")
        fun int(n: Int) { for(shift in 24 downTo 0 step 8) hash.update((n ushr shift).toByte()) }
        fun long(n: Long) { for(shift in 56 downTo 0 step 8) hash.update((n ushr shift).toByte()) }
        val domain="writhelm.credential-verifier.v1".toByteArray(Charsets.UTF_8)
        int(domain.size); hash.update(domain)
        listOf(storeId,projectId,principalId,credentialId).forEach { long(it.mostSignificantBits); long(it.leastSignificantBits) }
        int(secret.size); hash.update(secret)
        val bytes=hash.digest()
        return try { Digest.parse(bytes.joinToString("") { "%02x".format(it) }) } finally { bytes.fill(0) }
    }
}
private fun canonicalUuid(s: String)=s.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
