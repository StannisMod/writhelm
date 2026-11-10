// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.work

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.knowledge.Utf8Content
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
    @Test fun candidateModel3ProfilesUseTheSameCanonicalGraphAndPreserveDescriptorAndLegacyBytes() {
        val selected=WorkCodec(modelVersion=3)
        val row=WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,key),WMechanicalProfilesPayload(profile().value))
        val bytes=codec.encodeProfile(profile())
        assertContentEquals(bytes,selected.encodeRecord(row))
        assertEquals(row,selected.decodeRecord(bytes))
        assertEquals(CodecProblem.UNSUPPORTED_FORMAT,assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes,true) }.problem)
        assertFailsWith<CodecFault> { WorkState.restore(project,listOf(row),emptyList()) }
        val f=ReviewProbeFixture()
        val legacyRow=f.state.record(WorkFamily.PROFILES,f.profileKey)
        assertContentEquals(WorkCodec().encodeRecord(legacyRow,true),selected.encodeRecord(legacyRow,true))
        val state=WorkState.restore(project,listOf(row,legacyRow),emptyList(),modelVersion=3)
        assertEquals(3,state.modelVersion)
        val ref=state.reference(WorkFamily.PROFILES,key)
        assertEquals(Digest.of(bytes),ref.digest);assertEquals(row,state.resolve(ref))
        assertEquals(row,state.readExact(WorkRead.Exact(project,row.identity,ref.digest,null)))
        assertEquals(ref.digest,workRecordFacts(row,3).digest)
        val next=WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,WorkKey.Profile(project,uid(900),0)),row.payload)
        val derived=state.derive(listOf(next),emptyList())
        assertEquals(3,derived.modelVersion)
        assertEquals(state.encodedByteCount+selected.encodeRecord(next).size,derived.encodedByteCount)
        assertEquals(derived.records,WorkState.restore(project,derived.records.values,derived.bodies.values,modelVersion=3).records)
        assertFailsWith<CodecFault> { WorkState.restore(project,listOf(row),emptyList(),WorkGraphBudget(aggregateBytes=bytes.size.toLong()-1),modelVersion=3) }
    }
    @Test fun candidateProfileEvidenceUsesExistingReferenceAndEdgeBudgetsRatherThanAnOverlay() {
        val f=ReviewProbeFixture();val nativeRow=f.state.record(WorkFamily.PROFILES,f.profileKey)
        val source=WSourceVariant2Value(WSourceVariant2("work",f.state.reference(WorkFamily.PROFILES,f.profileKey)))
        val row=WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,key),WMechanicalProfilesPayload(profile().value.copy(qualificationEvidence=listOf(source,source))))
        val restored=WorkState.restore(project,listOf(row,nativeRow),emptyList(),modelVersion=3)
        assertEquals(4L,restored.fullRestoreEdges) // reference checks plus immutable-cycle traversal
        assertFailsWith<CodecFault> { WorkState.restore(project,listOf(row),emptyList(),modelVersion=3) }
        assertFailsWith<CodecFault> { WorkState.restore(project,listOf(row,nativeRow),emptyList(),WorkGraphBudget(edges=1),modelVersion=3) }
        assertFailsWith<CodecFault> { WorkState.empty(project,WorkGraphBudget(edges=1),3).derive(listOf(row,nativeRow),emptyList()) }
    }
    @Test fun candidateReaderDoesNotEnableLegacyCommandsReadsOrFakeNativeAssignments() {
        val f=ReviewProbeFixture();val selected=WorkCodec(modelVersion=3)
        val operation=CCreateProfileOperation(CCreateProfile(f.profileKey,(f.state.record(WorkFamily.PROFILES,f.profileKey).payload as WProfilesPayload).value,emptyList()))
        val command=WorkCommand(uid(902),project,operation)
        assertFailsWith<CodecFault> { selected.decodeCommand(WorkCodec().encodeCommand(command)) }
        val ref=f.state.reference(WorkFamily.PROFILES,f.profileKey)
        val read=WorkRead.Exact(project,ref.identity,ref.digest,null)
        assertFailsWith<CodecFault> { selected.decodeRead(WorkCodec().encodeRead(read)) }
        assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> {
            f.reducer.reduce(WorkState.empty(project,modelVersion=3),command,f.actor,f.facts)
        }.problem)
        f.active()
        val mechanicalRow=WorkRecord(WorkRecordIdentity(WorkFamily.PROFILES,f.profileKey),WMechanicalProfilesPayload(profile().value))
        assertFailsWith<CodecFault> { WorkState.restore(project,f.state.records.values.filter { it.identity!=mechanicalRow.identity }+mechanicalRow,
            f.state.bodies.values,modelVersion=3) }
    }
    private data class MechanicalGraphFixture(val state:WorkState,val assignment:WorkRecord,val claim:WorkRecord,val data:MechanicalBinding,val source:WSource)
    private fun mechanicalGraph():MechanicalGraphFixture {
        val f=ReviewProbeFixture();f.active();val predecessor=f.result()
        val selected=WorkCodec(modelVersion=3)
        fun record(k:WorkKey,p:WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(p),k),p)
        val task=WorkKey.TaskRevision(project,uid(950),0);val plan=WorkKey.Revision(project,uid(951),0)
        val manifest=record(WorkKey.Id(project,uid(952)),WInputManifestsPayload((f.state.record(WorkFamily.INPUT_MANIFESTS,f.manifestKey).payload as WInputManifestsPayload).value.copy(task=task)))
        val resource=WorkKey.Resource(project,uid(954));val assignmentId=uid(953)
        val producer=MechanicalProducerDescriptor(uid(957),f.controller,uid(958),uid(955),WorkKey.Id(project,uid(956)))
        val data=MechanicalBinding(task,WorkReference(manifest.identity,selected.digest(manifest)),key,producer,listOf(resource),
            DeclaredResultAuthorship(f.actor.principalId,listOf(f.actor.principalId,f.execution.principalId),listOf(predecessor)))
        val assignment=record(WorkKey.Id(project,assignmentId),WMechanicalAssignmentsPayload(MechanicalAssignment(0,data,null,"reserved")))
        val claim=record(resource,WClaimsPayload(WClaims(0,WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",WAssignmentSubject(assignmentId,0))),producer.workspaceId,"held",emptyList(),f.source)))
        val rows=f.state.records.values+listOf(manifest,assignment,claim,record(key,WMechanicalProfilesPayload(profile().value)),
            record(task,WTaskRevisionsPayload(f.task.copy(kind="remediation",executionProfile=key,verificationPlan=plan,
                dependencies=listOf(WDependency(f.taskKey,"implemented",predecessor,emptyList(),"exact correction predecessor"))))),
            record(plan,WVerificationPlansPayload(f.plan.copy(task=task))))
        val state=WorkState.restore(project,rows,f.state.bodies.values,modelVersion=3)
        return MechanicalGraphFixture(state,state.records.getValue(assignment.identity),state.records.getValue(claim.identity),
            (state.records.getValue(assignment.identity).payload as WMechanicalAssignmentsPayload).value.bindingData,f.source)
    }
    @Test fun mechanicalAssignmentUsesRealSharedClaimsAndTaskSelectedPredecessorAuthorsWithoutNativeIdentity() {
        val f=mechanicalGraph();val a=(f.assignment.payload as WMechanicalAssignmentsPayload).value
        val bytes=WorkCodec(modelVersion=3).encodeRecord(f.assignment)
        assertFalse(bytes.decodeToString().contains("nativeSessionId"));assertFalse(bytes.decodeToString().contains("credentialId"))
        assertEquals(CodecProblem.UNSUPPORTED_FORMAT,assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes) }.problem)
        assertEquals(WorkProblem.WRONG_ASSIGNMENT,assertFailsWith<WorkFault> { f.state.assignment((f.assignment.identity.key as WorkKey.Id).id) }.problem)
        for(field in listOf("nativeSessionId","credentialId","actor","model"))assertFailsWith<CodecFault> {
            WorkCodec(modelVersion=3).decodeRecord(rewrite(bytes) { row ->val p=row.entries.getValue("payload") as JObject
                JObject(row.entries+("payload" to JObject(p.entries+(field to str("injected"))))) })
        }
        val claim=(f.claim.payload as WClaimsPayload).value
        assertFailsWith<CodecFault> { f.state.derive(listOf(f.claim.copy(payload=WClaimsPayload(claim.copy(generation=1,state="free",holder=null,workspaceId=null)))),emptyList()) }
        assertFailsWith<CodecFault> { f.state.derive(listOf(f.claim.copy(payload=WClaimsPayload(claim.copy(generation=1,state="isolated")))),emptyList()) }
        for(bad in listOf(a.bindingData.copy(producer=a.bindingData.producer.copy(workspaceId=uid(4))),
            a.bindingData.copy(authorship=a.bindingData.authorship.copy(authors=listOf(a.bindingData.authorship.declaredBy))),
            a.bindingData.copy(profile=WorkKey.Profile(project,uid(6),0)))) {
            val changed=f.assignment.copy(payload=WMechanicalAssignmentsPayload(a.copy(bindingData=bad)))
            assertFailsWith<CodecFault> { WorkState.restore(project,f.state.records.values.filter { it.identity!=changed.identity }+changed,f.state.bodies.values,modelVersion=3) }
        }
    }
    @Test fun retainedUnknownMechanicalEffectCannotReleaseClaimsAndCompletedOriginalCannotBeRetargetedOrReopened() {
        val f=mechanicalGraph();val codec=WorkCodec(modelVersion=3)
        val id=(f.assignment.identity.key as WorkKey.Id).id;val subject=WAssignmentSubject(id,0)
        val bindingRow=WorkRecord(WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,id,0)),WMechanicalBindingsPayload(f.data))
        val binding=WorkReference(bindingRow.identity,codec.digest(bindingRow))
        var assignment=(f.assignment.payload as WMechanicalAssignmentsPayload).value.copy(generation=1,binding=binding,state="bound")
        var state=f.state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)),bindingRow),emptyList())
        val patch=Utf8Content.fromText("synthetic exact patch\n");val witnesses=Utf8Content.fromText("synthetic retained original witnesses\n")
        var operation=MechanicalOperation(0,subject,binding,WorkBodyReference(patch.digest,patch.sizeBytes),WorkBodyReference(witnesses.digest,witnesses.sizeBytes),
            Digest.of(byteArrayOf(1)),Digest.of(byteArrayOf(2)),"pending",null,false,emptyList(),emptyList())
        val opId=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,f.data.producer.operation)
        assignment=assignment.copy(generation=2,state="active")
        assertFailsWith<CodecFault> { state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)),
            WorkRecord(opId,WMechanicalOperationsPayload(operation.copy(outcome="unknown")))),listOf(patch,witnesses)) }
        state=state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)),WorkRecord(opId,WMechanicalOperationsPayload(operation))),listOf(patch,witnesses))
        for(row in state.records.values.filter { it.payload is WMechanicalAssignmentsPayload || it.payload is WMechanicalBindingsPayload || it.payload is WMechanicalOperationsPayload }) {
            val bytes=codec.encodeRecord(row,true)
            assertEquals(row,codec.decodeRecord(bytes,true))
            assertEquals(CodecProblem.UNSUPPORTED_FORMAT,assertFailsWith<CodecFault> { WorkCodec().decodeRecord(bytes,true) }.problem)
        }
        for(bad in listOf(operation.copy(outcome="applied",observedDigest=operation.afterDigest),
            operation.copy(writerCompleted=true),operation.copy(outcome="partial",writerCompleted=true,observedDigest=operation.afterDigest),
            operation.copy(assignment=subject.copy(generation=1)),operation.copy(generation=-1))) {
            assertFailsWith<CodecFault> { codec.encodeRecord(WorkRecord(opId,WMechanicalOperationsPayload(bad))) }
        }
        assignment=assignment.copy(generation=3,state="unknown")
        operation=operation.copy(generation=1,outcome="unknown",observedDigest=operation.afterDigest)
        state=state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)),WorkRecord(opId,WMechanicalOperationsPayload(operation))),emptyList())
        val claim=(f.claim.payload as WClaimsPayload).value
        val free=f.claim.copy(payload=WClaimsPayload(claim.copy(generation=1,state="free",holder=null,workspaceId=null)))
        assertFailsWith<CodecFault> { state.derive(listOf(free),emptyList()) } // after bytes do not establish writer completion
        assertFailsWith<WorkFault> { state.derive(listOf(WorkRecord(opId,WMechanicalOperationsPayload(operation.copy(generation=2,afterDigest=Digest.of(byteArrayOf(3)))))),emptyList()) }
        operation=operation.copy(generation=2,outcome="applied",writerCompleted=true,observations=listOf(f.source))
        assignment=assignment.copy(generation=4,state="completed")
        state=state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)),WorkRecord(opId,WMechanicalOperationsPayload(operation)),free),emptyList())
        assertEquals(state.records,WorkState.restore(project,state.records.values,state.bodies.values,modelVersion=3).records)
        assertFailsWith<WorkFault> { state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment.copy(generation=5,state="active")))),emptyList()) }
        // These are synthetic stored facts; no authenticated command or physical writer proof is asserted.
    }
}
