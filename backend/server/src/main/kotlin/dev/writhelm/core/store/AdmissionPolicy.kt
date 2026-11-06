// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*

/** Fixed, bounded genesis policy; ordinary commands cannot edit it. Operational budgets are separate.
 * These caps may tighten new admission, never the supported historical reader/resource profile. */
internal data class AdmissionPolicy(val bodyBytes: Int=1024*1024,val coreRecordBytes: Int=4*1024*1024,
    val coreCommandBytes: Int=8*1024*1024,val authRecordBytes: Int=16384,val authCommandBytes: Int=16384,
    val receiptBytes: Int=4*1024*1024,val writeDescriptors: Int=4096,
    val formatVersion: Int=1,val workBodyBytes: Int=1048576,val workRecordBytes: Int=262144,
    val workCommandBytes: Int=2097152,val workReadBytes: Int=16384,val workStringBytes: Int=65536,
    val workCollectionItems: Int=1024,val workDepth: Int=32,val workTokens: Int=100000,
    val authCommand3Bytes: Int=16384,val authDepth: Int=16,val authCollectionItems: Int=64,
    val authTokens: Int=2048,val authStringBytes: Int=1024) {
    init {
        require(bodyBytes in 1..1024*1024 && coreRecordBytes in 1..4*1024*1024 && coreCommandBytes in 1..8*1024*1024 &&
            authRecordBytes in 1..(if(formatVersion==2)65536 else 16384) && authCommandBytes in 1..16384 && receiptBytes in 1..4*1024*1024 && writeDescriptors in 1..4096)
        require(formatVersion in setOf(1,2) && workBodyBytes in 1..1048576 && workRecordBytes in 1..262144 && workCommandBytes in 1..2097152 && workReadBytes in 1..16384 && workStringBytes in 1..65536 && workCollectionItems in 1..1024 && workDepth in 1..32 && workTokens in 1..100000 && authCommand3Bytes in 1..16384 && authDepth in 1..16 && authCollectionItems in 1..64 && authTokens in 1..2048 && authStringBytes in 1..1024)
    }
    private fun extra()=mapOf("workBodyBytes" to num(workBodyBytes),"workRecordBytes" to num(workRecordBytes),"workCommandBytes" to num(workCommandBytes),"workReadBytes" to num(workReadBytes),"workStringBytes" to num(workStringBytes),"workCollectionItems" to num(workCollectionItems),"workDepth" to num(workDepth),"workTokens" to num(workTokens),"authCommand3Bytes" to num(authCommand3Bytes),"authDepth" to num(authDepth),"authCollectionItems" to num(authCollectionItems),"authTokens" to num(authTokens),"authStringBytes" to num(authStringBytes))
    fun bytes()=StrictJson.encode(JObject(obj("formatVersion" to num(formatVersion),"bodyBytes" to num(bodyBytes),"coreRecordBytes" to num(coreRecordBytes),
        "coreCommandBytes" to num(coreCommandBytes),"authRecordBytes" to num(authRecordBytes),"authCommandBytes" to num(authCommandBytes),
        "receiptBytes" to num(receiptBytes),"writeDescriptors" to num(writeDescriptors)).entries+(if(formatVersion==2)extra() else emptyMap())),16384)
    fun command(c: StoreCommand) {
        val maximum=when { c.fingerprintVersion==1 -> coreCommandBytes; c.fingerprintVersion==2 -> authCommandBytes; c.operation in H1Catalog.authOperations && formatVersion==2 -> authCommand3Bytes; formatVersion==2 -> workCommandBytes; else -> fail(StoreProblem.UNSUPPORTED) }
        if(c.presentedBytes>maximum || c.bytes().size>maximum)fail(StoreProblem.ADMISSION)
        if(c.fingerprintVersion==1)CommandCodec(JsonLimits(bytes=coreCommandBytes),bodyBytes).decode(c.bytes())
        if(c.fingerprintVersion==3) {
            if(c.operation in H1Catalog.authOperations)StrictJson.decode(c.bytes(),authLimits(maximum))
            else StrictJson.decodeWorkCommand(c.bytes(),workLimits(maximum),workBodyBytes)
        }
    }
    fun workLimits(maximum: Int)=JsonLimits(bytes=maximum,stringBytes=workStringBytes,depth=workDepth,collection=workCollectionItems,tokens=workTokens)
    fun authLimits(maximum: Int)=JsonLimits(bytes=maximum,stringBytes=authStringBytes,depth=authDepth,collection=authCollectionItems,tokens=authTokens)
    fun file(path: String,bytes: ByteArray) {
        val maximum=when {
            path.startsWith("core/bodies/") -> bodyBytes
            path.startsWith("core/records/") -> coreRecordBytes
            path.startsWith("auth/records/") -> authRecordBytes
            path.startsWith("work/bodies/") && formatVersion==2 -> workBodyBytes
            path.startsWith("work/records/") && formatVersion==2 -> workRecordBytes
            path.startsWith("commands/receipts/") -> receiptBytes
            else -> 16384
        }
        if(bytes.size>maximum)fail(StoreProblem.ADMISSION)
        if(formatVersion==2 && path.startsWith("work/records/"))StrictJson.decode(bytes,workLimits(maximum))
        if(formatVersion==2 && path.startsWith("auth/records/"))StrictJson.decode(bytes,authLimits(maximum))
    }
    companion object {
        fun decode(bytes: ByteArray): AdmissionPolicy {
            val v=StrictJson.decode(bytes,JsonLimits(bytes=16384))
            val version=(v as? JObject)?.entries?.get("formatVersion")?.int() ?: fail(StoreProblem.INTEGRITY)
            if(version !in setOf(1,2))fail(StoreProblem.UNSUPPORTED)
            val additional=if(version==2)AdmissionPolicy(formatVersion=2).extra().keys else emptySet()
            val f=v.fields(*(listOf("formatVersion","bodyBytes","coreRecordBytes","coreCommandBytes","authRecordBytes","authCommandBytes","receiptBytes","writeDescriptors")+additional).toTypedArray())
            fun n(name: String,default: Int)=if(version==2)f.getValue(name).int() else default
            return try { AdmissionPolicy(f.getValue("bodyBytes").int(),f.getValue("coreRecordBytes").int(),f.getValue("coreCommandBytes").int(),
                f.getValue("authRecordBytes").int(),f.getValue("authCommandBytes").int(),f.getValue("receiptBytes").int(),f.getValue("writeDescriptors").int(),version,
                n("workBodyBytes",1048576),n("workRecordBytes",262144),n("workCommandBytes",2097152),n("workReadBytes",16384),n("workStringBytes",65536),n("workCollectionItems",1024),n("workDepth",32),n("workTokens",100000),n("authCommand3Bytes",16384),n("authDepth",16),n("authCollectionItems",64),n("authTokens",2048),n("authStringBytes",1024))
            } catch(_: IllegalArgumentException) { fail(StoreProblem.UNSUPPORTED) }
        }
    }
}
