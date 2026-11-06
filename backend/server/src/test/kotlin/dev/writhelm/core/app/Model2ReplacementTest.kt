// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import java.nio.file.*
import kotlin.test.*

/** Actual Git/CORE/AUTH/restore plus real disjoint directory inventory; native isolation source is
 * explicitly MOCK, not evidence of native fencing or OS sandboxing. Unbound A has never started. */
class Model2ReplacementTest {
    private fun scenario(alias: Boolean,bound: Boolean=false) {
        val f=ReviewAppFixture();val codec=WorkCodec();val path=directory();val root=directory()
        val oldArea=Files.createDirectory(root.resolve("old"));Files.writeString(oldArea.resolve("retained.txt"),"old bytes remain")
        val planned=root.resolve("new")
        val newResource=WorkKey.Resource(project,id(7001));val nextId=id(7002)
        val retained=Utf8Content.fromText("MOCK native isolation epoch for never-started reserved worker; physical paths are independently inspected")
        val artifact=root.resolve("isolation.json");Files.write(artifact,retained.bytes())
        val locator=id(7003)
        var command: CReplaceIsolatedAssignment?=null
        val custody=LinuxReplacementCustody(QualifiedIsolationSource { capture,op ->
            val work=capture.work;val a=work.assignment(f.assignment)
            val proof=FValidatedReplacementIsolation(WorkGitOid(capture.basis.algorithm,capture.basis.hex),FValidatedReplacementIsolationProof(locator,retained.digest),
                WorkKey.Id(project,f.assignment),a.executionGeneration,codec.digest(work.record(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,f.assignment))),a.binding,
                a.intendedExecution,a.workspaceId,nextId,nextGeneration(a.executionGeneration),op.next.intendedExecution,op.next.workspaceId,
                LinuxReplacementCustody.areaDescriptor(planned),"planned-absent",listOf(FValidatedReplacementIsolationClaimsItem("claims",f.resource,
                    (work.record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.generation)),emptyList(),a.requiredResources,f.source,
                id(7010),emptyList(),emptyList(),f.manifest.digest)
            ReinspectedIsolation(Utf8Content.fromBytes(Files.readAllBytes(artifact)),proof,oldArea,planned,
                mapOf(f.resource to oldArea,newResource to if(alias)oldArea else planned))
        })
        val delegate=H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) }
        val options=H1Options(ReplacementRuntimeFacts(delegate,custody,f.controller))
        var app=H1Application.initialize(path,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options)
        try {
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,codec.encodeCommand(f.reserveCommand()))).receipt.outcome)
            val register=Auth3CommandCodec().encode(f.register())
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,register)).receipt.outcome)
            var sequence=7100
            var originalResultBytes: ByteArray?=null
            fun publish(op: dev.writhelm.core.work.WorkOperation,who: String=plannerToken): CommandResult.Settled {
                val bytes=codec.encodeCommand(WorkCommand(id(sequence++),project,op))
                if(op is CSubmitResultOperation)originalResultBytes=bytes
                val answer=assertIs<CommandResult.Settled>(app.execute(who,bytes))
                assertIs<LogicalOutcome.Success>(answer.receipt.outcome);return answer
            }
            fun ref(answer: CommandResult.Settled): WorkReference {
                val out=assertIs<LogicalOutcome.Success>(answer.receipt.outcome);val result=H1Catalog.result(out.operation,out.result);val family=H1Catalog.family(result.family)
                return WorkReference(WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(result.key))),digest(out.result.fields("key","generation","digest").getValue("digest")))
            }
            val text=Utf8Content.fromText("intrinsic original bound result")
            var result: WorkReference?=null
            if(bound) {
                val subject=WAssignmentSubject(f.assignment,0)
                val execution=WExecution(f.intended.executionId,f.worker,f.workerKey,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace)
                f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(f.intent to WorkNativeObservation(subject,f.intent,f.correlation,"prepare","confirmed",listOf(f.source),execution,true,"reserved")))
                publish(CBindAssignmentOperation(CBindAssignment(f.assignment,1,f.intent,0,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace,listOf(f.source),emptyList())))
                publish(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,2,id(7090),id(7091),"start",null,emptyList())))
                f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(id(7090) to WorkNativeObservation(subject,id(7090),id(7091),"start","confirmed",listOf(f.source),execution,false,"bound")))
                publish(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,3,id(7090),0,"confirmed",listOf(f.source),emptyList())))
                val retro=ref(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(7092)),CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())),token(f.workerKey)))
                val body=WorkBodyReference(text.digest,text.sizeBytes)
                f.runtimeFacts=f.runtimeFacts.copy(submittedDiffs=setOf(WorkSubmittedDiff(f.task,listOf(f.snapshot),body)))
                result=ref(publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(7093)),CSubmitResultData(f.task,subject,f.manifest,listOf(f.snapshot),body,listOf(id(96)),emptyList(),retro,emptyList()),
                    listOf(CSubmitResultBodiesItem(text.digest,text.text())))),token(f.workerKey)))
            }
            val before=app.coordinationCapture(plannerToken,project).work
            val oldRecord=before.record(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,f.assignment))
            val old=(oldRecord.payload as WAssignmentsPayload).value
            val oldClaim=codec.encodeRecord(before.record(WorkFamily.CLAIMS,f.resource))
            val statusRead=Auth2ReadCodec().encode(Auth2Read(project,Auth2ReadOperation.DELEGATION_STATUS,f.delegation))
            val originalStatus=StrictJson.encode((app.read2(plannerToken,statusRead) as JObject).entries.getValue("data"))
            command=CReplaceIsolatedAssignment(WorkKey.Id(project,id(7004)),null,CReplaceIsolatedAssignmentOld(oldRecord.identity.key as WorkKey.Id,old.generation,
                old.executionGeneration,codec.digest(oldRecord)),null,CReplaceIsolatedAssignmentNext(nextId,f.task,f.manifest,f.profile,id(7005),
                listOf(CReplaceIsolatedAssignmentNextResourcesItem(newResource,null)),f.intended.copy(executionId=id(7006),nativeSessionId="ses_passive_replacement"),id(7007),id(7008)),
                CReplaceIsolatedAssignmentContinuation(null,emptyList(),emptyList()),CReplaceIsolatedAssignmentProof(locator,retained.digest),emptyList())
            val request=WorkCommand(id(7020),project,CReplaceIsolatedAssignmentOperation(command))
            val bytes=codec.encodeCommand(request)
            val outcome=assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes))
            if(alias) {
                assertEquals("CLAIM_CONFLICT",assertIs<LogicalOutcome.Rejected>(outcome.receipt.outcome).code)
                assertTrue(outcome.receipt.writes.isEmpty())
                assertEquals("reserved",app.coordinationCapture(plannerToken,project).work.assignment(f.assignment).state)
                return
            }
            assertIs<LogicalOutcome.Success>(outcome.receipt.outcome,"replacement outcome: ${outcome.receipt.outcome}")
            assertEquals(5,outcome.receipt.writes.size)
            assertTrue(outcome.receipt.writes.all { it.record.namespace=="work" })
            val after=app.coordinationCapture(plannerToken,project).work
            assertEquals("replaced",after.assignment(f.assignment).state)
            assertEquals(old.copy(generation=old.generation+1,state="replaced"),after.assignment(f.assignment))
            assertEquals("reserved",after.assignment(nextId).state);assertEquals(1L,after.assignment(nextId).executionGeneration)
            assertNull(after.assignment(nextId).binding);assertNull(after.assignment(nextId).delegationId)
            assertContentEquals(oldClaim,codec.encodeRecord(after.record(WorkFamily.CLAIMS,f.resource)))
            assertContentEquals(originalStatus,StrictJson.encode((app.read2(plannerToken,statusRead) as JObject).entries.getValue("data")))
            assertFalse(Files.exists(planned)) // canonical prepare is NOT passive native creation
            assertEquals("old bytes remain",Files.readString(oldArea.resolve("retained.txt")))
            assertIs<CommandResult.Denied>(app.execute(token(f.workerKey),codec.encodeCommand(WorkCommand(id(7021),project,CUpdatePlanOperation(
                 CUpdatePlan(WorkKey.Revision(project,id(7022),0),f.task,null,emptyList(),"cannot revive unbound retired scope",null,emptyList(),WAssignmentSubject(f.assignment,0)))))))
            if(bound) {
                assertTrue(assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),originalResultBytes!!)).commandReplayed)
                val selector=WorkRead.Exact(project,result!!.identity,result!!.digest,null)
                app.read2(token(f.workerKey),codec.encodeRead(selector))
                val body=app.read2(token(f.workerKey),codec.encodeRead(WorkRead.Body(project,text.digest,selector)))
                assertTrue(StrictJson.encode(body).decodeToString().contains(text.text()))
                assertFailsWith<AppAccessFault> { app.read2(token(f.workerKey),codec.encodeRead(WorkRead.Exact(project,f.manifest.identity,f.manifest.digest,null))) }
                assertFailsWith<AppAccessFault> { app.read2(token(f.workerKey),codec.encodeRead(WorkRead.Listing(project,RListWorkRecords("results",f.task.taskId,null,1)))) }
            }
            val basis=app.inspectAccess(plannerToken).basis
            assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes))
            assertEquals(basis,app.inspectAccess(plannerToken).basis)
            assertIs<CommandResult.Denied>(app.execute(plannerToken,codec.encodeCommand(request.copy(requestKey=id(7023)))))
            assertIs<CommandResult.Settled>(app.execute(plannerToken,register)) // controller retained original enrollment receipt
            app.close();app=H1Application.open(path,storeId,options) // actual full journal/metadata/H->C restore
            assertEquals("replaced",app.coordinationCapture(plannerToken,project).work.assignment(f.assignment).state)
            assertEquals(1,app.coordinationCapture(plannerToken,project).work.records.values.count { it.payload is WAssignmentReplacementsPayload })
            if(bound) {
                app.close()
                RuntimeOwner.acquire(path).use { owner ->
                    val git=NativeGit(owner)
                    fun snapshot(oid: GitOid): StoreSnapshot {
                        val entries=git.call(listOf("ls-tree","-rz","--full-tree",oid.hex)).checked().decodeToString().split('\u0000').filter { it.isNotEmpty() }.associate { line ->
                            val parts=line.split('\t');parts[1] to GitOid("sha1",parts[0].substringAfterLast(' ')) }
                        return StoreSnapshot(oid,entries,git)
                    }
                    val c=snapshot(git.head());val h=snapshot(git.oid(git.call(listOf("rev-parse","${c.oid.hex}^")).checked()))
                    val receiptPath=(c.entries.keys-h.entries.keys).single { it.startsWith("commands/receipts/") }
                    val receipt=JournalCodec.decode(git.readBlob(c.entries.getValue(receiptPath)),receiptPath)
                    assertEquals("replace_isolated_assignment",receipt.identity.operation)
                    H1BoundaryValidator(H1SnapshotCache(storeId)).validate(h,c,receipt)
                    val claimPath=identityCodec.recordPath(before.record(WorkFamily.CLAIMS,f.resource).identity)
                    val claimRow=before.record(WorkFamily.CLAIMS,f.resource)
                    val claim=(claimRow.payload as WClaimsPayload).value
                    val authPath=c.entries.keys.first { it.startsWith("auth/records/credentials/") && git.readBlob(c.entries.getValue(it)).decodeToString().contains("\"generation\":0") }
                    val authBytes=git.readBlob(c.entries.getValue(authPath)).decodeToString().replaceFirst("\"generation\":0","\"generation\":1").toByteArray()
                    for((changed,bytes) in listOf(claimPath to codec.encodeRecord(claimRow.copy(payload=WClaimsPayload(claim.copy(generation=claim.generation+1)))),authPath to authBytes)) {
                        val files=c.entries.mapValues { git.readBlob(it.value) }.toMutableMap();files[changed]=bytes
                        val descriptor=if(changed==claimPath)RecordKey("work","claims",StrictJson.decode(identityCodec.encodeKey(f.resource))) else {
                            val envelope=StrictJson.decode(bytes).fields("formatVersion","family","key","payload")
                            RecordKey("auth",envelope.getValue("family").string(),envelope.getValue("key"))
                        }
                        val writes=(receipt.writes+WriteDescriptor(descriptor,Digest.of(git.readBlob(h.entries.getValue(changed))).hex,Digest.of(bytes).hex)).sortedBy { it.path }
                        // Construct hostile bytes directly: AUTH companions are forbidden already
                        // by the receipt shape guard, so a Receipt constructor cannot represent one.
                        val envelope=(StrictJson.decode(JournalCodec.encode(receipt)) as JObject).entries
                        val payload=(envelope.getValue("payload") as JObject).entries
                        val hostileBytes=StrictJson.encode(JObject(envelope+ ("payload" to JObject(payload+ ("writes" to JArray(writes.map { w ->
                            obj("namespace" to str(w.record.namespace),"family" to str(w.record.family),"key" to w.record.key,
                                "beforeDigest" to (w.beforeDigest?.let(::str) ?: JNull),"afterDigest" to (w.afterDigest?.let(::str) ?: JNull))
                        }))))))
                        files[receiptPath]=hostileBytes
                        class Node { val dirs=sortedMapOf<String,Node>();val files=sortedMapOf<String,GitOid>() }
                        val rootNode=Node();val entries=files.mapValues { git.blob(it.value) }
                        entries.forEach { (name,oid) ->var node=rootNode;val parts=name.split('/');parts.dropLast(1).forEach { node=node.dirs.getOrPut(it) { Node() } };node.files[parts.last()]=oid }
                        fun tree(node: Node): GitOid {
                            val lines=node.files.map { (name,oid) ->name to "100644 blob ${oid.hex}\t$name\u0000" }+node.dirs.map { (name,child) ->"$name/" to "040000 tree ${tree(child).hex}\t$name\u0000" }
                            return git.oid(git.call(listOf("mktree","-z"),lines.sortedBy { it.first }.joinToString("") { it.second }.toByteArray()).checked())
                        }
                        val forged=StoreSnapshot(git.commit(tree(rootNode),h.oid),entries,git)
                        assertFails { H1BoundaryValidator(H1SnapshotCache(storeId)).validate(h,forged,JournalCodec.decode(hostileBytes,receiptPath)) }
                    }
                }
            }
        } finally { app.close() }
    }
    @Test fun registeredUnboundR2KeepsAuthAndOldClaimsAndRestoresWithoutFakeBinding()=scenario(false)
    @Test fun actualPhysicalAliasIsRefusedEvenWhenResourceAndWorkspaceUuidsDiffer()=scenario(true)
    @Test fun retainedBoundR2KeepsOnlyOriginalIntrinsicResultRightsAndNeverRevivesMutation()=scenario(false,true)
}
