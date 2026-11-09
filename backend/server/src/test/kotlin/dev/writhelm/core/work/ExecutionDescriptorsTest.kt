// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Pure descriptor compatibility/negative gates. Not an admitted command, writer or native effect. */
class ExecutionDescriptorsTest {
    private val codec=ExecutionDescriptorCodec()
    private val project=uid(1)
    private val key=WorkKey.Profile(project,uid(2),0)
    private val native=WExecution(uid(3),uid(4),uid(5),uid(6),"ses_legacy",uid(7))
    private val mechanical=MechanicalProducerDescriptor(uid(8),uid(9),uid(6),uid(7),WorkKey.Id(project,uid(10)))
    private fun profile(platform:String="linux")=MechanicalProfileDescriptor(key,MechanicalProfile("mechanical-text-v1",emptyList(),"checks-v1",
        1024*1024,8*1024*1024,65536,60000,"E-COOP-1",emptyList(),platform,"unqualified"))
    private fun rewrite(bytes:ByteArray,transform:(JObject)->JsonValue)=StrictJson.encode(transform(StrictJson.decode(bytes) as JObject))

    @Test fun legacyProfileAndProducerUseUnchangedCanonicalBytesAndStayExplicitlyLegacy() {
        val p=WProfile("native-v1",emptyList(),WorkModelReference("synthetic","no-call",null),"route","effort",1024,64,64,64,
            "compact","accounting","E-COOP-1",emptyList(),"linux","unqualified")
        val legacy=WorkCodec();val original=legacy.encodeRecord(WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,key),WProfilesPayload(p)))
        val decoded=assertIs<NativeProfileDescriptor>(codec.decodeProfile(original))
        assertEquals(p,decoded.value);assertContentEquals(original,codec.encodeProfile(decoded))
        assertEquals(Digest.of(original),Digest.of(codec.encodeProfile(decoded,true)))
        val execution=StrictJson.encode(encodeWExecution(native))
        assertEquals(NativeProducerDescriptor(native),codec.decodeProducer(execution,project))
        assertContentEquals(execution,codec.encodeProducer(codec.decodeProducer(execution,project),project))
    }
    @Test fun mechanicalProfileAndProducerContainNoModelCredentialOrChatSessionAndOldReadersRefuse() {
        for(platform in listOf("linux","windows","macos")) {
            val value=profile(platform);val bytes=codec.encodeProfile(value)
            assertEquals(value,codec.decodeProfile(bytes));assertContentEquals(bytes,codec.encodeProfile(codec.decodeProfile(bytes)))
            assertFalse(bytes.decodeToString().contains("\"model\""));assertFalse(bytes.decodeToString().contains("nativeSessionId"))
            assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes) }
            for(field in listOf("model","route","effort","nativeSessionId","credentialId","actor")) {
                val bad=rewrite(bytes) { row ->val p=row.entries.getValue("payload") as JObject
                    JObject(row.entries+("payload" to JObject(p.entries+(field to str("must-refuse"))))) }
                assertFailsWith<CodecFault>(field) { codec.decodeProfile(bad) }
            }
        }
        val bytes=codec.encodeProducer(mechanical,project)
        assertEquals(mechanical,codec.decodeProducer(bytes,project))
        assertFailsWith<CodecFault> { decodeWExecution(StrictJson.decode(bytes)) }
        assertFailsWith<CodecFault> { codec.decodeProducer(bytes,uid(999)) }
        for(field in listOf("principalId","credentialId","nativeSessionId","model","actor"))
            assertFailsWith<CodecFault>(field) { codec.decodeProducer(rewrite(bytes) { JObject(it.entries+(field to str("injected"))) },project) }
    }
    @Test fun closedVersionAndLimitProfilesRejectMalformedTokensAndForeignEvidence() {
        val original=codec.encodeProfile(profile())
        for(version in listOf("2.0","2e0","-0","9223372036854775808")) {
            val changed=original.decodeToString().replace("\"formatVersion\":2","\"formatVersion\":$version").toByteArray()
            assertFailsWith<CodecFault> { codec.decodeProfile(changed) }
        }
        for(change in listOf(profile().value.copy(maximumPatchBytes=0),profile().value.copy(maximumPatchBytes=1024*1024+1),
            profile().value.copy(maximumFileBytes=8*1024*1024+1),profile().value.copy(maximumCheckMillis=3600001),
            profile().value.copy(executionAssurance="sandbox-by-label"),profile().value.copy(qualification="ready")))
            assertFailsWith<CodecFault> { codec.encodeProfile(MechanicalProfileDescriptor(key,change)) }
        val foreign=WorkReference(WorkRecordIdentity(WorkFamily.RESULTS,WorkKey.Id(uid(999),uid(40))),Digest.of(byteArrayOf(1)))
        val source=WSourceVariant2Value(WSourceVariant2("work",foreign))
        assertFailsWith<CodecFault> { codec.encodeProfile(MechanicalProfileDescriptor(key,profile().value.copy(qualificationEvidence=listOf(source)))) }
    }
    @Test fun declaredAuthorsAreDeterministicButCannotOmitAuthenticatedSubmitterOrKnownPredecessors() {
        val a=DeclaredResultAuthorship(uid(20),listOf(uid(21),uid(20)),emptyList())
        val bytes=codec.encodeAuthorship(a,project)
        assertContentEquals(bytes,codec.encodeAuthorship(a.copy(authors=a.authors.reversed()),project))
        val decoded=codec.decodeAuthorship(bytes,project)
        requireDeclaredAuthorship(decoded,uid(20),setOf(uid(21)))
        assertFailsWith<WorkFault> { requireDeclaredAuthorship(decoded,uid(22),setOf(uid(21))) }
        assertFailsWith<WorkFault> { requireDeclaredAuthorship(decoded,uid(20),setOf(uid(23))) }
        assertFailsWith<CodecFault> { codec.encodeAuthorship(a.copy(authors=listOf(uid(20),uid(20))),project) }
        assertFailsWith<CodecFault> { codec.encodeAuthorship(a.copy(authors=listOf(uid(21))),project) }
        assertFailsWith<CodecFault> { codec.decodeAuthorship(rewrite(bytes) { JObject(it.entries+("independent" to JBoolean(true))) },project) }
    }
    @Test fun anotherExecutionOrApplierIdentityCannotMakePatchAuthorAnIndependentReviewer() {
        val declared=DeclaredResultAuthorship(uid(20),listOf(uid(20),native.principalId),emptyList())
        val known=setOf(native.principalId)
        assertFalse(nonAuthorCandidate(uid(20),uid(100),mechanical,declared,true,known))
        assertFalse(nonAuthorCandidate(native.principalId,uid(101),NativeProducerDescriptor(native),declared,true,known))
        assertFalse(nonAuthorCandidate(uid(30),mechanical.executionId,mechanical,declared,true,known))
        assertFalse(nonAuthorCandidate(uid(30),uid(102),mechanical,LegacyProducerAuthorship(native.principalId),false,known))
        assertFalse(nonAuthorCandidate(uid(30),uid(102),NativeProducerDescriptor(native),LegacyProducerAuthorship(native.principalId),true,known))
        assertFalse(nonAuthorCandidate(uid(30),uid(102),mechanical,declared.copy(authors=listOf(uid(20))),true,known))
        assertTrue(nonAuthorCandidate(uid(30),uid(102),mechanical,declared,true,known)) // candidate only, no rights/freshness/evidence proved
    }
}
