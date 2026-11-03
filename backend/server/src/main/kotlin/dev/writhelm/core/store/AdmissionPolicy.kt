// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*

/** Fixed, bounded genesis policy; ordinary commands cannot edit it. Operational budgets are separate.
 * These caps may tighten new admission, never the supported historical reader/resource profile. */
internal data class AdmissionPolicy(val bodyBytes: Int=1024*1024,val coreRecordBytes: Int=4*1024*1024,
    val coreCommandBytes: Int=8*1024*1024,val authRecordBytes: Int=16384,val authCommandBytes: Int=16384,
    val receiptBytes: Int=4*1024*1024,val writeDescriptors: Int=4096) {
    init {
        require(bodyBytes in 1..1024*1024 && coreRecordBytes in 1..4*1024*1024 && coreCommandBytes in 1..8*1024*1024 &&
            authRecordBytes in 1..16384 && authCommandBytes in 1..16384 && receiptBytes in 1..4*1024*1024 && writeDescriptors in 1..4096)
    }
    fun bytes()=StrictJson.encode(obj("formatVersion" to num(1),"bodyBytes" to num(bodyBytes),"coreRecordBytes" to num(coreRecordBytes),
        "coreCommandBytes" to num(coreCommandBytes),"authRecordBytes" to num(authRecordBytes),"authCommandBytes" to num(authCommandBytes),
        "receiptBytes" to num(receiptBytes),"writeDescriptors" to num(writeDescriptors)),16384)
    fun command(c: StoreCommand) {
        if(c.presentedBytes>if(c.fingerprintVersion==1)coreCommandBytes else authCommandBytes)fail(StoreProblem.ADMISSION)
        if(c.fingerprintVersion==1)CommandCodec(JsonLimits(bytes=coreCommandBytes),bodyBytes).decode(c.bytes())
    }
    fun file(path: String,bytes: ByteArray) {
        val maximum=when {
            path.startsWith("core/bodies/") -> bodyBytes
            path.startsWith("core/records/") -> coreRecordBytes
            path.startsWith("auth/records/") -> authRecordBytes
            path.startsWith("commands/receipts/") -> receiptBytes
            else -> 16384
        }
        if(bytes.size>maximum)fail(StoreProblem.ADMISSION)
    }
    companion object {
        fun decode(bytes: ByteArray): AdmissionPolicy {
            val f=StrictJson.decode(bytes,JsonLimits(bytes=16384)).fields("formatVersion","bodyBytes","coreRecordBytes","coreCommandBytes","authRecordBytes","authCommandBytes","receiptBytes","writeDescriptors")
            if(f.getValue("formatVersion").int()!=1)fail(StoreProblem.UNSUPPORTED)
            return try { AdmissionPolicy(f.getValue("bodyBytes").int(),f.getValue("coreRecordBytes").int(),f.getValue("coreCommandBytes").int(),
                f.getValue("authRecordBytes").int(),f.getValue("authCommandBytes").int(),f.getValue("receiptBytes").int(),f.getValue("writeDescriptors").int())
            } catch(_: IllegalArgumentException) { fail(StoreProblem.UNSUPPORTED) }
        }
    }
}
