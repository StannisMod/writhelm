// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Synthetic exact result/attribution values; not native observations or canonical admission. */
class ResultDescriptorsTest {
    private val codec=ResultDescriptorCodec()
    private val project=uid(1)
    private fun reference(family:WorkFamily,id:Int)=WorkReference(WorkRecordIdentity(family,WorkKey.Id(project,uid(id))),Digest.of("ref-$id".toByteArray()))
    private val native=NativeResultDescriptor(WorkKey.Id(project,uid(40)),WResults(WorkKey.TaskRevision(project,uid(11),0),WAssignmentSubject(uid(12),0),
        WExecution(uid(13),uid(14),uid(15),uid(16),"ses_native_result",uid(17)),reference(WorkFamily.INPUT_MANIFESTS,20),
        listOf(WSnapshot(uid(21),WorkGitOid("sha1","2".repeat(40)),WorkGitOid("sha1","1".repeat(40)),null,uid(22))),
        WorkBodyReference(Digest.of("change".toByteArray()),6),emptyList(),emptyList(),reference(WorkFamily.RETROSPECTIVES,23),emptyList()))
    private fun result():MechanicalResultDescriptor {
        val old=native.value
        return MechanicalResultDescriptor(WorkKey.Id(project,uid(41)),MechanicalResult(old.task,WAssignmentSubject(uid(31),0),
            MechanicalProducerDescriptor(uid(43),uid(44),uid(16),uid(17),WorkKey.Id(project,uid(42))),old.manifest,old.artifacts,old.changes,
            old.claimedCriteria,old.evidence,old.retrospective,old.pending,WorkBodyReference(Digest.of("patch".toByteArray()),5),
            DeclaredResultAuthorship(uid(30),listOf(uid(30),old.producer.principalId),listOf(codec.reference(native)))))
    }
    private fun change(bytes:ByteArray,edit:(JObject)->JObject):ByteArray {
        val root=StrictJson.decode(bytes) as JObject
        return StrictJson.encode(JObject(root.entries+("payload" to edit(root.entries.getValue("payload") as JObject))))
    }
    @Test fun legacyResultBytesAndDigestStayLegacyWithoutInventingCompleteContributorHistory() {
        val old=WorkCodec().encodeRecord(WorkRecord(WorkRecordIdentity(WorkFamily.RESULTS,native.key),WResultsPayload(native.value)))
        assertEquals(native,codec.decode(old));assertContentEquals(old,codec.encode(codec.decode(old)))
        assertEquals(Digest.of(old),codec.reference(codec.decode(old)).digest)
        assertEquals(setOf(native.value.producer.principalId),knownResultAuthors(codec.decode(old)))
        assertFalse(nonAuthorResultCandidate(uid(90),uid(91),native,emptySet(),true))
    }
    @Test fun mechanicalResultPinsExplicitProducerPatchAuthorsAndExactSourceResultWithoutLegacyCoercion() {
        val value=result();val bytes=codec.encode(value)
        assertEquals(value.value.copy(authorship=value.value.authorship.copy(authors=value.value.authorship.authors.sorted())),
            (codec.decode(bytes) as MechanicalResultDescriptor).value)
        assertContentEquals(bytes,codec.encode(codec.decode(bytes)))
        assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes) }
        for(field in listOf("accepted","independent","model","nativeSessionId","actor"))
            assertFailsWith<CodecFault>(field) { codec.decode(change(bytes) { JObject(it.entries+(field to JBoolean(true))) }) }
        assertFailsWith<CodecFault> { codec.decode(change(bytes) { JObject(it.entries+("producer" to encodeWExecution(native.value.producer))) }) }
        assertFailsWith<CodecFault> { codec.encode(value.copy(value=value.value.copy(patch=WorkBodyReference(Digest.of(byteArrayOf()),0)))) }
    }
    @Test fun candidateResultCodecPreservesBytesButWholeGraphRefusesUnintegratedResult() {
        val value=result();val codec3=WorkCodec(modelVersion=3)
        val bytes=codec.encode(value);val record=codec3.decodeRecord(bytes)
        assertIs<WMechanicalResultsPayload>(record.payload)
        assertContentEquals(bytes,codec3.encodeRecord(record))
        assertEquals(CodecProblem.UNSUPPORTED_FORMAT,assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes) }.problem)
        assertFailsWith<CodecFault> { WorkState.restore(project,listOf(record),emptyList(),modelVersion=3) }
    }
    @Test fun exactSourceClosureAndActualSubmitterCannotBeReplacedByAnApplierSuppliedAuthorList() {
        val value=result()
        requireMechanicalResultAuthorship(value,uid(30),listOf(native))
        assertFailsWith<WorkFault> { requireMechanicalResultAuthorship(value,uid(90),listOf(native)) }
        assertFailsWith<WorkFault> { requireMechanicalResultAuthorship(value,uid(30),emptyList()) }
        assertFailsWith<WorkFault> { requireMechanicalResultAuthorship(value,uid(30),listOf(native,native)) }
        assertFailsWith<WorkFault> { requireMechanicalResultAuthorship(value,uid(30),listOf(native.copy(key=WorkKey.Id(project,uid(90))))) }
        assertFailsWith<WorkFault> { requireMechanicalResultAuthorship(value.copy(value=value.value.copy(authorship=value.value.authorship.copy(authors=listOf(uid(30))))),uid(30),listOf(native)) }
        val wrong=codec.reference(native).copy(digest=Digest.of("wrong".toByteArray()))
        assertFailsWith<WorkFault> { requireMechanicalResultAuthorship(value.copy(value=value.value.copy(authorship=value.value.authorship.copy(sourceResults=listOf(wrong)))),uid(30),listOf(native)) }
    }
    @Test fun resultAuthorCannotReviewThroughAnotherExecutionOrPhysicalApplierAndNoPassIsInherited() {
        val value=result();val known=knownResultAuthors(native)
        assertFalse(nonAuthorResultCandidate(uid(30),uid(100),value,known,false))
        assertFalse(nonAuthorResultCandidate(native.value.producer.principalId,uid(101),value,known,false))
        assertFalse(nonAuthorResultCandidate(uid(90),value.value.producer.executionId,value,known,false))
        assertTrue(nonAuthorResultCandidate(uid(90),uid(102),value,known,true)) // only negative-author filter, no assessment or acceptance
        val metadata=(StrictJson.decode(codec.encode(value)) as JObject).entries.getValue("payload") as JObject
        assertFalse(metadata.entries.keys.any { it in setOf("review","verified","accepted","integration") })
        assertTrue(value.value.evidence.isEmpty()) // no old check copied or silently fabricated
    }
}
