// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*

internal object JournalCodec {
    private fun limits(maximum: Int)=JsonLimits(bytes=maximum,stringBytes=1024*1024,depth=32,collection=4096,tokens=200000)
    fun encode(r: Receipt,maximum: Int=4*1024*1024): ByteArray {
        val p=obj("storeId" to uuid(r.storeId),"fingerprintVersion" to num(r.fingerprintVersion),"fingerprint" to str(r.fingerprint),"validatedAt" to r.validatedAt.json(),"authenticatedByCredentialId" to uuid(r.authenticatedByCredentialId),"outcome" to r.outcome.json(),"writes" to JArray(r.writes.map { it.json() }))
        val bytes=StrictJson.encode(obj("formatVersion" to num(r.journalVersion),"family" to str("command-receipt"),"key" to r.identity.json(),"payload" to p),maximum)
        decode(bytes,r.path,maximum)
        return bytes
    }
    fun decode(bytes: ByteArray,path: String,maximum: Int=4*1024*1024): Receipt {
        val e=StrictJson.decode(bytes,limits(maximum),true).fields("formatVersion","family","key","payload")
        val journalVersion=e.getValue("formatVersion").int()
        if(journalVersion !in setOf(2,3))fail(StoreProblem.UNSUPPORTED)
        integrity(e.getValue("family").string()=="command-receipt")
        val identity=CommandIdentity.parse(e.getValue("key")); integrity(identity.path==path)
        val p=e.getValue("payload").fields("storeId","fingerprintVersion","fingerprint","validatedAt","authenticatedByCredentialId","outcome","writes")
        val o=p.getValue("outcome"); val kind=(o as? JObject)?.entries?.get("kind")?.string()
        val outcome=when(kind) {
            "success" -> { val f=o.fields("kind","operation","result","domainReplayed"); val replay=(f.getValue("domainReplayed") as? JBoolean)?.value ?: fail(StoreProblem.INTEGRITY); LogicalOutcome.Success(f.getValue("operation").string(),f.getValue("result"),replay) }
            "rejected" -> { val f=o.fields("kind","domain","code"); LogicalOutcome.Rejected(f.getValue("domain").string(),f.getValue("code").string()) }
            else -> fail(StoreProblem.INTEGRITY)
        }
        val writes=p.getValue("writes").list().map { w ->
            val f=w.fields("namespace","family","key","beforeDigest","afterDigest")
            fun hash(name: String)=f.getValue(name).let { if(it===JNull)null else checkDigest(it.string()) }
            WriteDescriptor(RecordKey(f.getValue("namespace").string(),f.getValue("family").string(),f.getValue("key")),hash("beforeDigest"),hash("afterDigest"))
        }
        return Receipt(uuid(p.getValue("storeId")),identity,p.getValue("fingerprintVersion").int(),p.getValue("fingerprint").string(),GitOid.parse(p.getValue("validatedAt")),uuid(p.getValue("authenticatedByCredentialId")),outcome,writes,journalVersion)
    }
}
