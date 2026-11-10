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
    @Test fun candidateCommand4ReservationAndBindingAreClosedDeterministicAndRefusedByLegacyReaders() {
        val f=mechanicalGraph();val id=(f.assignment.identity.key as WorkKey.Id).id
        val codec=MechanicalCommandCodec()
        val resource=f.data.resources.single()
        val reserve=MechanicalCommand(uid(960),project,MechanicalCommandOperation.Reserve(id,f.data,listOf(MechanicalResourceExpectation(resource,null))))
        val bytes=codec.encode(reserve);val decoded=codec.decode(bytes)
        assertContentEquals(bytes,codec.encode(decoded))
        assertEquals(codec.fingerprint(decoded),codec.fingerprint(decoded.copy(requestKey=uid(961))))
        val second=WorkKey.Resource(project,uid(959))
        val pair=(reserve.operation as MechanicalCommandOperation.Reserve).copy(binding=f.data.copy(resources=listOf(resource,second)),
            resources=listOf(MechanicalResourceExpectation(resource,null),MechanicalResourceExpectation(second,3)))
        val reordered=pair.copy(binding=pair.binding.copy(resources=pair.binding.resources.reversed()),resources=pair.resources.reversed())
        assertContentEquals(codec.encode(reserve.copy(operation=pair)),codec.encode(reserve.copy(operation=reordered)))
        assertEquals(codec.fingerprint(reserve.copy(operation=pair)),codec.fingerprint(reserve.copy(operation=reordered)))
        assertNotEquals(codec.fingerprint(decoded),codec.fingerprint(decoded.copy(operation=(decoded.operation as MechanicalCommandOperation.Reserve).copy(
            resources=listOf(MechanicalResourceExpectation(resource,0))))))
        assertFailsWith<CodecFault> { WorkCodec().decodeCommand(bytes) }
        assertFailsWith<CodecFault> { codec.decode(bytes.decodeToString().replace("\"formatVersion\":4","\"formatVersion\":3").toByteArray()) }
        for(field in listOf("principalId","credentialId","actor","nativeSessionId","model")) {
            val root=StrictJson.decode(bytes) as JObject;val payload=root.entries.getValue("payload") as JObject
            val injected=StrictJson.encode(JObject(root.entries+("payload" to JObject(payload.entries+(field to str("injected"))))))
            assertFailsWith<CodecFault>(field) { codec.decode(injected) }
        }
        val changed=decoded.copy(operation=(decoded.operation as MechanicalCommandOperation.Reserve).copy(resources=listOf(MechanicalResourceExpectation(WorkKey.Resource(project,uid(962)),null))))
        assertFailsWith<CodecFault> { codec.encode(changed) }
        val duplicate=decoded.copy(operation=(decoded.operation as MechanicalCommandOperation.Reserve).copy(resources=listOf(
            MechanicalResourceExpectation(resource,null),MechanicalResourceExpectation(resource,0))))
        assertFailsWith<CodecFault> { codec.encode(duplicate) }
        assertFailsWith<CodecFault> { codec.decode(bytes.decodeToString().replace("\"formatVersion\":4","\"formatVersion\":4,\"formatVersion\":4").toByteArray()) }
        assertFailsWith<CodecFault> { codec.decode(bytes.decodeToString().replace("\"formatVersion\":4","\"formatVersion\":4.0").toByteArray()) }
        val foreign=decoded.copy(projectId=uid(999))
        assertFailsWith<CodecFault> { codec.encode(foreign) }
        val bindingKey=WorkKey.AssignmentBinding(project,id,0)
        val ref=WorkReference(WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,bindingKey),Digest.of("binding".toByteArray()))
        val bind=MechanicalCommand(uid(963),project,MechanicalCommandOperation.Bind(id,1,ref))
        assertEquals(bind,codec.decode(codec.encode(bind)))
        assertFailsWith<CodecFault> { codec.encode(bind.copy(operation=(bind.operation as MechanicalCommandOperation.Bind).copy(assignmentId=uid(964)))) }
        assertFailsWith<CodecFault> { codec.encode(bind.copy(operation=(bind.operation as MechanicalCommandOperation.Bind).copy(generation=-1))) }
        val root=StrictJson.decode(bytes) as JObject
        assertFailsWith<CodecFault> { codec.decode(StrictJson.encode(JObject(root.entries+("operation" to str("unknown_mechanical_effect"))))) }
    }
    @Test fun candidateCommand4EffectAndResultMetadataCannotSubstituteForOriginalGuardsOrNativeFacts() {
        val f=mechanicalGraph();val id=(f.assignment.identity.key as WorkKey.Id).id;val codec=MechanicalCommandCodec()
        val binding=WorkReference(WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,id,0)),Digest.of("binding".toByteArray()))
        val patch=WorkBodyReference(Digest.of("patch".toByteArray()),5);val witnesses=WorkBodyReference(Digest.of("witness".toByteArray()),7)
        val effect=MechanicalOperation(0,WAssignmentSubject(id,0),binding,patch,witnesses,Digest.of(byteArrayOf(1)),Digest.of(byteArrayOf(2)),"pending",null,false,emptyList(),emptyList())
        val prepare=MechanicalCommand(uid(970),project,MechanicalCommandOperation.Prepare(id,1,f.data.producer.operation,effect))
        assertEquals(prepare,codec.decode(codec.encode(prepare)))
        assertFailsWith<CodecFault> { codec.encode(prepare.copy(operation=(prepare.operation as MechanicalCommandOperation.Prepare).copy(effect=effect.copy(generation=1)))) }
        assertFailsWith<CodecFault> { codec.encode(prepare.copy(operation=(prepare.operation as MechanicalCommandOperation.Prepare).copy(effect=effect.copy(assignment=WAssignmentSubject(uid(971),0))))) }
        val observed=effect.copy(generation=1,outcome="unknown",observedDigest=effect.afterDigest)
        val observe=MechanicalCommand(uid(972),project,MechanicalCommandOperation.Observe(id,2,f.data.producer.operation,0,observed))
        assertEquals(observe,codec.decode(codec.encode(observe)))
        assertFailsWith<CodecFault> { codec.encode(observe.copy(operation=(observe.operation as MechanicalCommandOperation.Observe).copy(expectedOperationGeneration=1))) }
        assertFailsWith<CodecFault> { codec.encode(observe.copy(operation=(observe.operation as MechanicalCommandOperation.Observe).copy(effect=observed.copy(writerCompleted=true)))) }
        val native=(f.state.resolve(f.data.authorship.sourceResults.single()).payload as WResultsPayload).value
        val result=MechanicalResultDescriptor(WorkKey.Id(project,uid(973)),MechanicalResult(f.data.task,WAssignmentSubject(id,0),f.data.producer,
            f.data.manifest,native.artifacts,native.changes,emptyList(),emptyList(),native.retrospective,emptyList(),patch,f.data.authorship))
        val submit=MechanicalCommand(uid(974),project,MechanicalCommandOperation.Submit(id,3,1,result))
        val decoded=codec.decode(codec.encode(submit));assertContentEquals(codec.encode(submit),codec.encode(decoded))
        assertFailsWith<CodecFault> { codec.encode(submit.copy(operation=(submit.operation as MechanicalCommandOperation.Submit).copy(assignmentId=uid(971)))) }
        assertFailsWith<CodecFault> { WorkCodec().decodeCommand(codec.encode(submit)) }
        // Codec shapes are NOT authenticated observations, retained bodies, result admission or a native effect.
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
        validateMechanicalRecordEdge(null,f.assignment)
        validateMechanicalRecordEdge(null,bindingRow)
        validateMechanicalRecordEdge(f.assignment,f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)))
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(bindingRow,bindingRow) }
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(f.assignment,f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment.copy(generation=2)))) }
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(f.assignment,f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment.copy(bindingData=assignment.bindingData.copy(producer=assignment.bindingData.producer.copy(controllerId=uid(964))))))) }
        var state=f.state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment)),bindingRow),emptyList())
        val patch=Utf8Content.fromText("synthetic exact patch\n");val witnesses=Utf8Content.fromText("synthetic retained original witnesses\n")
        var operation=MechanicalOperation(0,subject,binding,WorkBodyReference(patch.digest,patch.sizeBytes),WorkBodyReference(witnesses.digest,witnesses.sizeBytes),
            Digest.of(byteArrayOf(1)),Digest.of(byteArrayOf(2)),"pending",null,false,emptyList(),emptyList())
        val opId=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,f.data.producer.operation)
        val originalRow=WorkRecord(opId,WMechanicalOperationsPayload(operation))
        validateMechanicalRecordEdge(null,originalRow)
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(null,originalRow.copy(payload=WMechanicalOperationsPayload(operation.copy(outcome="unknown")))) }
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(originalRow,null) }
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(originalRow,originalRow.copy(identity=WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,WorkKey.Id(project,uid(965))))) }
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(originalRow,originalRow.copy(payload=WMechanicalOperationsPayload(operation.copy(outcome="unknown")))) }
        validateMechanicalRecordEdge(originalRow,originalRow.copy(payload=WMechanicalOperationsPayload(operation.copy(generation=1,outcome="unknown"))))
        assertFailsWith<WorkFault> { validateMechanicalRecordEdge(originalRow,originalRow.copy(payload=WMechanicalOperationsPayload(operation.copy(generation=1,outcome="unknown",patch=operation.witnesses)))) }
        assignment=assignment.copy(generation=2,state="active")
        assertFailsWith<CodecFault> { state.derive(listOf(WorkRecord(opId,WMechanicalOperationsPayload(operation))),listOf(patch,witnesses)) }
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
        val retrospective=WorkRecord(WorkRecordIdentity(WorkFamily.RETROSPECTIVES,WorkKey.Id(project,uid(975))),
            WRetrospectivesPayload(WRetrospectives(f.data.task,subject,null,"no-significant-new-findings",emptyList())))
        val retro=WorkReference(retrospective.identity,codec.digest(retrospective))
        val original=(state.resolve(f.data.authorship.sourceResults.single()).payload as WResultsPayload).value
        val result=MechanicalResult(f.data.task,subject,f.data.producer,f.data.manifest,original.artifacts,original.changes,
            emptyList(),emptyList(),retro,emptyList(),operation.patch,f.data.authorship)
        val resultRecord=WorkRecord(WorkRecordIdentity(WorkFamily.RESULTS,WorkKey.Id(project,uid(976))),WMechanicalResultsPayload(result))
        assertFailsWith<CodecFault> { state.derive(listOf(retrospective,resultRecord.copy(payload=WMechanicalResultsPayload(result.copy(patch=operation.witnesses)))),emptyList()) }
        state=state.derive(listOf(retrospective,resultRecord),emptyList())
        val resultRef=state.reference(WorkFamily.RESULTS,resultRecord.identity.key)
        assertEquals(resultRecord,state.resolve(resultRef))
        assertEquals(result.task,recordTasks(resultRecord).single())
        // Synthetic native review bindings exercise restore/derive independence, not admission,
        // fresh-context, immutable input custody or actual report quality.
        val originalBinding=state.records.values.mapNotNull { (it.payload as? WAssignmentBindingsPayload)?.value }.first()
        val reviewKey=WorkKey.AssignmentBinding(project,uid(977),0)
        fun reviewer(principal:java.util.UUID,executionId:java.util.UUID):WorkRecord {
            val execution=originalBinding.execution.copy(principalId=principal,executionId=executionId)
            val intent=originalBinding.intendedExecution.copy(executionId=executionId,template="reviewer-v1")
            return WorkRecord(WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,reviewKey),WAssignmentBindingsPayload(
                originalBinding.copy(task=f.data.task,manifest=f.data.manifest,execution=execution,intendedExecution=intent,reviewSubjects=listOf(resultRef))))
        }
        for(principal in result.authorship.authors)assertFailsWith<CodecFault> { state.derive(listOf(reviewer(principal,uid(978))),emptyList()) }
        assertFailsWith<CodecFault> { state.derive(listOf(reviewer(uid(979),result.producer.executionId)),emptyList()) }
        val candidateReview=state.derive(listOf(reviewer(uid(979),uid(978))),emptyList())
        assertEquals(candidateReview.records,WorkState.restore(project,candidateReview.records.values,candidateReview.bodies.values,modelVersion=3).records)
        val reviewerBinding=(candidateReview.record(WorkFamily.ASSIGNMENT_BINDINGS,reviewKey).payload as WAssignmentBindingsPayload).value
        val reviewPlan=(candidateReview.task(reviewerBinding.task).verificationPlan)!!
        val gate=(candidateReview.record(WorkFamily.VERIFICATION_PLANS,reviewPlan).payload as WVerificationPlansPayload).value.gates.single().id
        val report=WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_REPORTS,WorkKey.Id(project,uid(991))),WVerificationReportsPayload(
            WVerificationReports(reviewerBinding.task,WAssignmentSubject(reviewKey.assignmentId,0),reviewerBinding.execution,listOf(resultRef),reviewPlan,
                listOf(gate),f.source,listOf(f.source),emptyList(),emptyList(),original.changes,emptyList())))
        val withReport=candidateReview.derive(listOf(report),emptyList())
        assertEquals(withReport.records,WorkState.restore(project,withReport.records.values,withReport.bodies.values,modelVersion=3).records)
        val forged=(report.payload as WVerificationReportsPayload).value.copy(reviewer=reviewerBinding.execution.copy(principalId=result.authorship.authors.first()))
        assertFailsWith<CodecFault> { candidateReview.derive(listOf(report.copy(payload=WVerificationReportsPayload(forged))),emptyList()) }
        val reportRef=withReport.reference(WorkFamily.VERIFICATION_REPORTS,report.identity.key)
        val evidenceRecord=WorkRecord(WorkRecordIdentity(WorkFamily.EVIDENCE,WorkKey.Id(project,uid(992))),WEvidencePayload(
            WEvidence(listOf(WSourceVariant2Value(WSourceVariant2("work",resultRef))),emptyList(),"synthetic check","pure",original.changes,
                uid(993),"passed",emptyList(),listOf(WEvidenceClauseCasesItem(f.source,"case","test")),emptyList(),original.changes)))
        val evidenceRef=WorkReference(evidenceRecord.identity,codec.digest(evidenceRecord))
        val applicability=WorkRecord(WorkRecordIdentity(WorkFamily.EVIDENCE_APPLICABILITY,WorkKey.Id(project,uid(994))),WEvidenceApplicabilityPayload(
            WEvidenceApplicability(resultRef,listOf(WEvidenceApplicabilityEvidenceItem(evidenceRef,"retained",emptyList(),"same result")),emptyList(),listOf(f.source),uid(995))))
        val applicabilityRef=WorkReference(applicability.identity,codec.digest(applicability))
        val mechanicalPlan=(state.task(result.task).verificationPlan)!!
        val criterion=state.task(result.task).criteria.single().id
        val acceptance=WAcceptances(resultRef,result.task,mechanicalPlan,listOf(evidenceRef),listOf(reportRef),emptyList(),applicabilityRef,listOf(criterion),emptyList(),original.changes,uid(996))
        val acceptanceKey=WorkRecordIdentity(WorkFamily.ACCEPTANCES,WorkKey.Id(project,uid(997)))
        val ready=withReport.derive(listOf(evidenceRecord,applicability),emptyList())
        for(author in result.authorship.authors)assertFailsWith<CodecFault> { ready.derive(listOf(WorkRecord(acceptanceKey,WAcceptancesPayload(acceptance.copy(acceptedBy=author)))),emptyList()) }
        val accepted=ready.derive(listOf(WorkRecord(acceptanceKey,WAcceptancesPayload(acceptance))),emptyList())
        assertEquals(accepted.records,WorkState.restore(project,accepted.records.values,accepted.bodies.values,modelVersion=3).records)
        val alternatePlan=mechanicalPlan.copy(id=uid(998))
        val rootPlan=(state.record(WorkFamily.VERIFICATION_PLANS,mechanicalPlan).payload as WVerificationPlansPayload).value
        val alternateRow=WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,alternatePlan),WVerificationPlansPayload(rootPlan.copy(previous=mechanicalPlan)))
        val alternateReport=report.copy(identity=WorkRecordIdentity(WorkFamily.VERIFICATION_REPORTS,WorkKey.Id(project,uid(999))),
            payload=WVerificationReportsPayload((report.payload as WVerificationReportsPayload).value.copy(plan=alternatePlan)))
        val withAlternate=ready.derive(listOf(alternateRow,alternateReport),emptyList())
        val alternateRef=withAlternate.reference(WorkFamily.VERIFICATION_REPORTS,alternateReport.identity.key)
        assertFailsWith<CodecFault> { withAlternate.derive(listOf(WorkRecord(acceptanceKey,WAcceptancesPayload(acceptance.copy(reports=listOf(alternateRef))))),emptyList()) }
        // Synthetic structural gate only: no command route, current rights, actual checks or acceptance claim.
        // A subsequent correction must inherit every declared predecessor author, not only the
        // immediately preceding physical producer or the newest submitter.
        val nextTask=WorkKey.TaskRevision(project,uid(980),0);val nextPlan=WorkKey.Revision(project,uid(981),0)
        val nextManifestKey=WorkKey.Id(project,uid(982));val nextResource=WorkKey.Resource(project,uid(983));val nextId=uid(984)
        fun row(k:WorkKey,p:WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(p),k),p)
        val task=(state.record(WorkFamily.TASK_REVISIONS,f.data.task).payload as WTaskRevisionsPayload).value
        val nextTaskRow=row(nextTask,WTaskRevisionsPayload(task.copy(verificationPlan=nextPlan,
            dependencies=listOf(WDependency(f.data.task,"implemented",resultRef,emptyList(),"exact mechanical predecessor")))))
        val nextPlanRow=row(nextPlan,WVerificationPlansPayload((state.record(WorkFamily.VERIFICATION_PLANS,task.verificationPlan!!).payload as WVerificationPlansPayload).value.copy(task=nextTask)))
        val nextManifestRow=row(nextManifestKey,WInputManifestsPayload((state.resolve(f.data.manifest).payload as WInputManifestsPayload).value.copy(task=nextTask)))
        val nextManifest=WorkReference(nextManifestRow.identity,codec.digest(nextManifestRow))
        val nextProducer=MechanicalProducerDescriptor(uid(985),uid(986),uid(987),uid(988),WorkKey.Id(project,uid(989)))
        val author=uid(990)
        val nextBinding=MechanicalBinding(nextTask,nextManifest,f.data.profile,nextProducer,listOf(nextResource),
            DeclaredResultAuthorship(author,listOf(author)+f.data.authorship.authors,listOf(resultRef)))
        val nextAssignment=row(WorkKey.Id(project,nextId),WMechanicalAssignmentsPayload(MechanicalAssignment(0,nextBinding,null,"reserved")))
        val nextClaim=row(nextResource,WClaimsPayload(WClaims(0,WClaimsHolderVariant0Value(WClaimsHolderVariant0("assignment",WAssignmentSubject(nextId,0))),nextProducer.workspaceId,"held",emptyList(),f.source)))
        val newRows=listOf(nextTaskRow,nextPlanRow,nextManifestRow,nextAssignment,nextClaim)
        val derived=state.derive(newRows,emptyList())
        assertEquals(derived.records,WorkState.restore(project,derived.records.values,derived.bodies.values,modelVersion=3).records)
        val omitted=nextAssignment.copy(payload=WMechanicalAssignmentsPayload(MechanicalAssignment(0,nextBinding.copy(authorship=nextBinding.authorship.copy(authors=listOf(author))),null,"reserved")))
        assertFailsWith<CodecFault> { state.derive(newRows.filter { it.identity!=nextAssignment.identity }+omitted,emptyList()) }
        assertEquals(state.records,WorkState.restore(project,state.records.values,state.bodies.values,modelVersion=3).records)
        assertFailsWith<WorkFault> { state.derive(listOf(f.assignment.copy(payload=WMechanicalAssignmentsPayload(assignment.copy(generation=5,state="active")))),emptyList()) }
        // These are synthetic stored facts; no authenticated command or physical writer proof is asserted.
    }
}
