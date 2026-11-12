// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.store.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.*

/** Actual canonical reserve/enrollment/bind, Git bytes and bidirectional HTTP; native SDK/profile
 * qualification is an explicit fixture, never operational/native writer fencing evidence. */
class NativeWorkerPreparationJourneyTest {
    @Test @Timeout(180) fun observedRealGitDiffReachesServiceAndPlannerOnlyWithRetainedFixtureFact() {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"diff-result-journey-")
        val source=Files.createDirectory(root.resolve("source"));val worker=root.resolve("worker")
        fun git(dir:Path,vararg args:String):String {
            val process=ProcessBuilder("/usr/bin/git","-c","core.hooksPath=/dev/null","-C",dir.toString(),*args)
                .redirectError(root.resolve("git.stderr").toFile())
            process.environment().clear();process.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
            val p=process.start();val bytes=p.inputStream.readNBytes(8192);assertTrue(p.waitFor(20,TimeUnit.SECONDS))
            assertEquals(0,p.exitValue(),Files.readString(root.resolve("git.stderr")));return bytes.decodeToString().trim()
        }
        git(source,"init","-b","main");Files.writeString(source.resolve("result.txt"),"before\n");git(source,"add","result.txt")
        git(source,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","fixture base")
        val base=WSnapshot(id(59600),WorkGitOid("sha1",git(source,"rev-parse","HEAD")),WorkGitOid("sha1",git(source,"rev-parse","HEAD^{tree}")),null,id(59601))
        git(source,"worktree","add","--detach",worker.toString(),requireNotNull(base.commit).hex)
        Files.writeString(worker.resolve("result.txt"),"after\n");git(worker,"add","result.txt")
        git(worker,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","fixture result")
        val result=base.copy(commit=WorkGitOid("sha1",git(worker,"rev-parse","HEAD")),tree=WorkGitOid("sha1",git(worker,"rev-parse","HEAD^{tree}")),retainedLocatorId=id(59602))
        val witness=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("src/linux-git-witness.js").toUri()
        val script="""
            import {LinuxGitWitness} from ${StrictJson.encode(str(witness.toString())).decodeToString()};
            import {readFile} from 'node:fs/promises'; import {createHash} from 'node:crypto';
            let raw=''; for await (const c of process.stdin) raw+=c; const x=JSON.parse(raw);
            const git='/usr/bin/git', sha256=createHash('sha256').update(await readFile(git)).digest('hex');
            const proof=await new LinuxGitWitness({path:git,sha256}).observeDiff(x.worker,x.source,x.base,x.result,new AbortController().signal);
            process.stdout.write(proof.bytes.toString('base64'));
        """.trimIndent()
        fun observe(baseSnapshot:WSnapshot=base,resultSnapshot:WSnapshot=result):ByteArray {
            val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128","--input-type=module","-e",script)
                .redirectError(root.resolve("observer.stderr").toFile()).start()
            val payload=obj("worker" to str(worker.toString()),"source" to str(source.toString()),"base" to encodeWSnapshot(baseSnapshot),"result" to encodeWSnapshot(resultSnapshot))
            process.outputStream.use { it.write(StrictJson.encode(payload)) }
            val output=process.inputStream.readNBytes(2*1024*1024);assertTrue(process.waitFor(30,TimeUnit.SECONDS))
            if(process.exitValue()!=0)throw IllegalStateException("diff observation unavailable")
            return Base64.getDecoder().decode(output)
        }
        val bytes=Utf8Content.fromBytes(observe());assertTrue(bytes.text().contains("+after"))
        val f=JointFixture(base);val subject=WAssignmentSubject(f.assignment,0);val body=WorkBodyReference(bytes.digest,bytes.sizeBytes)
        var observations=0
        val control=NativeResultDiffProofControl { state,command ->
            observations++
            val actual=observe()
            NativeResultDiffProof(state.basis,project,WorkCodec().fingerprint(command),subject,f.intended.executionId,
                f.task,f.manifest,base,result,WorkBodyReference(Digest.of(actual),actual.size))
        }
        f.publishedActiveApp(diffControl=control).use { app ->
            val service=WrithelmService(app);val workerToken=token(f.workerKey)
            val retrospective=WorkCommand(id(59603),project,CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(59604)),
                CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())))
            assertEquals(200,service.command(workerToken,WorkCodec().encodeCommand(retrospective)).status)
            val state=app.coordinationCapture(plannerToken,project).work
            val retro=state.reference(WorkFamily.RETROSPECTIVES,WorkKey.Id(project,id(59604)))
            val data=CSubmitResultData(f.task,subject,f.manifest,listOf(result),body,listOf(id(96)),emptyList(),retro,emptyList())
            val command=WorkCommand(id(59605),project,CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(59606)),data,
                listOf(CSubmitResultBodiesItem(bytes.digest,bytes.text())))))
            assertEquals(409,service.command(workerToken,WorkCodec().encodeCommand(command)).status)
            assertEquals(0,observations) // Git presence is not retention.
            f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+result) // explicitly SYNTHETIC retention
            Files.writeString(worker.resolve("result.txt"),"uncommitted tampering\n")
            assertEquals(409,service.command(workerToken,WorkCodec().encodeCommand(command.copy(requestKey=id(59608)))).status)
            assertTrue(observations>0)
            Files.writeString(worker.resolve("result.txt"),"after\n")
            val submitted=service.command(workerToken,WorkCodec().encodeCommand(command.copy(requestKey=id(59607))))
            assertEquals(200,submitted.status);assertTrue(observations>0)
            val row=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.RESULTS,WorkKey.Id(project,id(59606)))
            val selector=WorkRead.Exact(project,row.identity,row.digest,null)
            val planner=service.read(plannerToken,WorkCodec().encodeRead(WorkRead.Body(project,bytes.digest,selector)))
            assertEquals(200,planner.status)
            assertEquals(bytes.text(),((planner.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string())
            val resultRead=service.read(plannerToken,WorkCodec().encodeRead(selector));assertEquals(200,resultRead.status)
            val resultRecord=WorkCodec().decodeRecord(StrictJson.encode((resultRead.value as JObject).entries.getValue("data")))
            val resultValue=(resultRecord.payload as WResultsPayload).value
            val current=app.coordinationCapture(plannerToken,project).work
            val taskRef=current.reference(WorkFamily.TASK_REVISIONS,resultValue.task)
            val taskRead=service.read(plannerToken,WorkCodec().encodeRead(WorkRead.Exact(project,taskRef.identity,taskRef.digest,null)))
            assertEquals(200,taskRead.status)
            val taskRecord=WorkCodec().decodeRecord(StrictJson.encode((taskRead.value as JObject).entries.getValue("data")))
            val recordedBase=requireNotNull((taskRecord.payload as WTaskRevisionsPayload).value.base).code.single()
            val independent=observe(recordedBase,resultValue.artifacts.single())
            assertEquals(resultValue.changes.digest,Digest.of(independent))
            assertEquals(resultValue.changes.byteLength,independent.size)
            assertContentEquals(bytes.bytes(),independent)
            val wrong=WorkRead.Exact(project,row.identity,Digest.of("wrong digest".toByteArray()),null)
            assertNotEquals(200,service.read(plannerToken,WorkCodec().encodeRead(wrong)).status)
        }
        assertEquals("before\n",Files.readString(source.resolve("result.txt")))
    }
    @Test @Timeout(180) fun reservedTaskGetsExactPassiveReceiverAndScopedCredentialWithoutModelWake()=cycle(false)
    @Test @Timeout(180) fun firstNormalPromptLostReplyRequiresHeldBoundaryThenActiveAndNeverWakesTwice()=cycle(true)
    @Test @Timeout(180) fun plannedPreparationPersistsAttemptBeforeRealReservationAndRecoversLostReceiver()=cycle(false,true)
    @Test @Timeout(180) fun plannedPreparationAndStartUseRetainedControllerAttempts()=cycle(true,true)
    @Test @Timeout(180) fun frontierUsesRealPreparationAndFullContextStart()=cycle(true,true,frontierStart=true)
    @Test @Timeout(180) fun producerResultAutomaticallyStartsDependentSuccessor()=cycle(true,true,diff=true,dependent=true)
    @Test @Timeout(180) fun frontierRestoresSuccessorAfterLostPublicationReply()=cycle(false,true,true)
    @Test @Timeout(180) fun frontierRunsTwoIndependentPreparedWorktreesConcurrently()=cycle(false,true,false,true)
    @Test @Timeout(180) fun frontierHoldsConflictingClaimWithoutRetryStorm()=cycle(false,true,false,true,true)
    @Test @Timeout(180) fun actualNativeDiffTransportSuppliesResultFactAndPlannerReadsReport()=cycle(true,true,false,false,false,true)
    @Test @Timeout(180) fun actualNativeResultPreflightHoldsWithoutEffectQualification()=cycle(true,true,false,false,false,true,true)
    private fun cycle(start:Boolean,planned:Boolean=false,frontier:Boolean=false,parallel:Boolean=false,conflict:Boolean=false,diff:Boolean=false,holdRetention:Boolean=false,frontierStart:Boolean=false,dependent:Boolean=false) {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"passive-journey-")
        val source=Files.createDirectory(root.resolve("source"));val parent=Files.createDirectory(root.resolve("areas"),
            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")))
        fun git(vararg args:String):String {
            val process=ProcessBuilder("/usr/bin/git","-c","core.hooksPath=/dev/null","-C",source.toString(),*args).redirectError(root.resolve("git.stderr").toFile())
            process.environment().clear();process.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
            val p=process.start();val bytes=p.inputStream.readNBytes(1048577);assertTrue(bytes.size<=1048576,"fixture Git output exceeds bound");assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),Files.readString(root.resolve("git.stderr")))
            return bytes.decodeToString().trim()
        }
        git("init","-b","main");Files.writeString(source.resolve("counter.txt"),"counter=0\n");git("add","counter.txt")
        git("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","Synthetic counter baseline")
        val f=ReviewAppFixture();val codec=WorkCodec()
        val snapshot=WSnapshot(id(59500),WorkGitOid("sha1",git("rev-parse","HEAD")),WorkGitOid("sha1",git("rev-parse","HEAD^{tree}")),null,id(59501))
        val original=f.base.work.getValue(project);val base=requireNotNull(original.task(f.task).base).copy(code=listOf(snapshot))
        val records=original.records.values.map { row ->when(val payload=row.payload){
            is WTaskRevisionsPayload ->row.copy(payload=WTaskRevisionsPayload(payload.value.copy(base=base,
                kind=if(frontier||parallel||frontierStart||dependent)"implementation" else payload.value.kind,
                approvedDesign=if(frontier||parallel||frontierStart||dependent)listOf(revision(90).x()) else payload.value.approvedDesign)))
            is WInputManifestsPayload ->row.copy(payload=WInputManifestsPayload(payload.value.copy(base=base)))
            else ->row
        } }
        val secondTask=f.task.copy(taskId=id(59510));val secondPlan=f.plan.copy(id=id(59511));val secondManifest=WorkKey.Id(project,id(59512));val secondResource=WorkKey.Resource(project,id(59513))
        val slowTask=f.task.copy(taskId=id(59520));val slowPlan=f.plan.copy(id=id(59521));val slowManifest=WorkKey.Id(project,id(59522))
        val extras=if(!parallel&&!dependent)emptyList() else {
            val first=(records.single { it.identity.family==WorkFamily.TASK_REVISIONS }.payload as WTaskRevisionsPayload).value
            val verify=(records.single { it.identity.family==WorkFamily.VERIFICATION_PLANS }.payload as WVerificationPlansPayload).value
            val input=(records.single { it.identity.family==WorkFamily.INPUT_MANIFESTS }.payload as WInputManifestsPayload).value
            listOf(WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,secondTask),WTaskRevisionsPayload(first.copy(verificationPlan=secondPlan,
                dependencies=if(dependent)listOf(WDependency(f.task,"implemented",null,emptyList(),"producer result")) else first.dependencies))),
                WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,secondPlan),WVerificationPlansPayload(verify.copy(task=secondTask))),
                WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,secondManifest),WInputManifestsPayload(input.copy(task=secondTask))))+
                if(dependent)listOf(
                    WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,slowTask),WTaskRevisionsPayload(first.copy(verificationPlan=slowPlan))),
                    WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,slowPlan),WVerificationPlansPayload(verify.copy(task=slowTask))),
                    WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,slowManifest),WInputManifestsPayload(input.copy(task=slowTask)))) else emptyList()
        }
        val work=WorkState.restore(project,records+extras,emptyList(),modelVersion=if(planned)4 else 2);val manifest=work.reference(WorkFamily.INPUT_MANIFESTS,f.manifestKey)
        val packets=if(parallel)setOf(manifest,work.reference(WorkFamily.INPUT_MANIFESTS,secondManifest)) else setOf(manifest)
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+snapshot,verifiedPackets=packets,
            approvedDecisions=if(frontier||parallel||frontierStart||dependent)setOf(revision(90).x()) else f.runtimeFacts.approvedDecisions)
        fun port()=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:${port()}";val nativeOrigin="http://127.0.0.1:${port()}"
        val proof=ByteArray(32).also { SecureRandom().nextBytes(it) };val nativeProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val storeRoot=directory();var actualAssignment:java.util.UUID?=null
        var savedResult:WorkReference?=null;var savedReport:String?=null
        var diagnosticState:H1BoundState?=null
        HttpConnectedNativeControl(nativeOrigin,nativeProof).use { control ->
            val reservations=java.util.concurrent.atomic.AtomicInteger()
            val original=H1RuntimeFacts { state,command ->
                diagnosticState=state
                val facts=if(frontier && command.operation is CReserveAssignmentOperation && reservations.getAndIncrement()==0)
                    f.runtimeFacts.copy(verifiedPackets=emptySet()) else f.runtimeFacts
                val results=state.work.getValue(project).records.values.mapNotNull { (it.payload as? WResultsPayload)?.value }
                val observed=if(dependent)facts.copy(retainedSnapshots=facts.retainedSnapshots+results.flatMap { it.artifacts },
                    submittedDiffs=facts.submittedDiffs+results.map { WorkSubmittedDiff(it.task,it.artifacts,it.changes) },
                    verifiedPackets=state.work.getValue(project).records.values.filter { it.payload is WInputManifestsPayload }
                        .map { state.work.getValue(project).reference(WorkFamily.INPUT_MANIFESTS,it.identity.key) }.toSet()) else facts
                H1ObservedFacts(state.basis,observed,f.observations.qualifiedProfiles)
            }
            // Explicit synthetic qualification; the retained ref and native proof are real fixture effects.
            val retained=NativeResultRetentionFacts(NativeInitialFacts(NativePreparationFacts(original,control,"a".repeat(64)),control,"a".repeat(64)),
                control,ResultRetentionQualification { _,_->diff })
            val observer=NativeResultDiffFacts(retained,
                NativeResultDiffTransportProof(control,"a".repeat(64)))
            val options=H1Options(if(dependent)ReservationProofFacts(observer,DeclaredPrebindingInputVerifier(observer)) else observer,nativeSafety=NativeRuntimeSafety { state,s ->s.generation==0L &&
                     state.work.getValue(project).assignment(s.assignmentId).let { it.task.taskId in setOf(f.task.taskId,secondTask.taskId,slowTask.taskId) && it.intendedExecution.controllerId==f.controller } },
                    workModelVersion=if(planned)4 else 2,
                     retentionEffectQualification=RetentionEffectQualification { _,_,_,_,_->diff&&!holdRetention },
                     candidateAssemblyProof=control::candidateAssemblyProof) // synthetic fixture qualification only
            H1Application.initialize(storeRoot,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to work)),options).use { app ->
                val endpoint=WrithelmService(app).nativeEndpoint(project,f.controller,f.intended.nativeHostId,proof)
                val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),nativeEndpoint=endpoint)
                val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(origin).port) { adapter.install(this) }.start(wait=false)
                try{
                    val reserve=f.reserveCommand().copy(operation=CReserveAssignmentOperation((f.reserveCommand().operation as CReserveAssignmentOperation).value.copy(manifest=manifest)))
                    val preparationId=id(59504);val planId=id(59505)
                    val planRef=if(planned) {
                        val entries=listOf(DispatchPlanEntry(f.task,manifest,f.profile,listOf(f.resource),preparationId,id(59506)))+
                             if(parallel||dependent)listOf(DispatchPlanEntry(secondTask,work.reference(WorkFamily.INPUT_MANIFESTS,secondManifest),f.profile,listOf(if(conflict)f.resource else secondResource),id(59514),id(59515),
                                 if(dependent)listOf(DispatchFutureDependency(f.task,"implemented")) else emptyList())) else emptyList()
                         val allEntries=if(dependent)entries+DispatchPlanEntry(slowTask,work.reference(WorkFamily.INPUT_MANIFESTS,slowManifest),f.profile,
                             listOf(WorkKey.Resource(project,id(59523))),id(59524),id(59525)) else entries
                         val checks=if(dependent)listOf(
                             DispatchCandidateCheck("whole-candidate",listOf(requireNotNull(System.getenv("WRITHELM_NODE")),"-e",
                                 "process.stdout.write('fixture test output\\n'); process.exit(Number(process.env.FIXTURE_EXIT))"),true,emptyList()),
                             DispatchCandidateCheck("whole-tree",listOf("/usr/bin/git","ls-tree","--name-only","{candidateCommit}"),false,listOf("whole-candidate"))) else emptyList()
                         val plan=DispatchPlanCandidate(project,planId,f.controller,allEntries,checks)
                        val result=app.execute(plannerToken,DispatchPlanCommandCodec().encode(DispatchPlanCommand(id(59507),plan)))
                        assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(assertIs<dev.writhelm.core.store.CommandResult.Settled>(result).receipt.outcome)
                        identityCodec.encodeReference(app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,planId))).decodeToString()
                    } else ""
                    val cfg=obj("start" to JBoolean(start),"planned" to JBoolean(planned),"frontier" to JBoolean(frontier),"parallel" to JBoolean(parallel),"conflict" to JBoolean(conflict),"diff" to JBoolean(diff),"holdRetention" to JBoolean(holdRetention),"planId" to uuid(planId),"preparationId" to uuid(preparationId),"startId" to uuid(id(59506)),"planRefJSON" to str(planRef),"root" to str(root.toString()),"sourceRoot" to str(source.toString()),"worktreeParent" to str(parent.toString()),"repositoryId" to uuid(snapshot.repositoryId),
                        "origin" to str(origin),"transportOrigin" to str(nativeOrigin),"proof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(proof)),
                        "transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(nativeProof)),"storeId" to uuid(storeId),"projectId" to uuid(project),
                        "controllerId" to uuid(f.controller),"hostId" to uuid(f.intended.nativeHostId),"principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken),
                        "assignmentId" to uuid(f.assignment),"reserveJSON" to str(codec.encodeCommand(reserve).decodeToString()),
                        "workerPrincipalId" to uuid(f.worker),"workerCredentialId" to uuid(f.workerKey),"delegationId" to uuid(f.delegation),"enrollKey" to uuid(id(59502)),"bindKey" to uuid(id(59503)))
                    val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("tests/worker-preparation-client.js")
                    val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128",script.toString()).redirectError(root.resolve("client.stderr").toFile()).start()
                    process.outputStream.use { it.write(StrictJson.encode(obj(*cfg.entries.map { it.key to it.value }.toTypedArray(),"frontierStart" to JBoolean(frontierStart),"dependent" to JBoolean(dependent)),65536)) }
                    val output=process.inputStream.readNBytes(4097);assertTrue(process.waitFor(120,TimeUnit.SECONDS))
                    if(dependent && process.exitValue()!=0) {
                        val state=requireNotNull(diagnosticState)
                        state.work.getValue(project).records.values.forEach { row ->
                            val assignment=(row.payload as? WAssignmentsPayload)?.value
                            if(assignment?.task?.taskId==secondTask.taskId)
                                NativeAssignmentPacket.render(state,project,WAssignmentSubject((row.identity.key as WorkKey.Id).id,assignment.executionGeneration))
                        }
                    }
                    assertEquals(0,process.exitValue(),Files.readString(root.resolve("client.stderr")))
                    val result=(StrictJson.decode(output) as JObject).entries
                    assertEquals(JBoolean(true),result.getValue("passiveAndBound"));assertEquals(if(dependent)3L else if(start)1L else 0L,result.getValue("wakes").long())
                    actualAssignment=uuid(result.getValue("assignmentId"))
                    val a=app.coordinationCapture(plannerToken,project).work.assignment(actualAssignment!!)
                    assertEquals(if(start)"active" else "bound",a.state);assertNotNull(a.binding);assertNotNull(a.delegationId)
                    if(dependent) {
                        val current=app.coordinationCapture(plannerToken,project).work
                        val child=current.records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }.single { it.task.taskId==secondTask.taskId }
                        assertEquals(secondTask.copy(revision=1),child.task);assertEquals("active",child.state)
                        assertNotEquals(a.workspaceId,child.workspaceId)
                        assertEquals(1,current.records.values.count { (it.payload as? WDispatchClosurePayload)?.automatic==true })
                        val slow=current.records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }.single { it.task==slowTask }
                        assertEquals("active",slow.state)
                        assertEquals(setOf(f.task,secondTask.copy(revision=1),slowTask),
                            current.records.values.mapNotNull { (it.payload as? WResultsPayload)?.value?.task }.toSet())
                    }
                    if(planned) {
                        val current=app.coordinationCapture(plannerToken,project).work
                        val attempt=(current.record(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,preparationId)).payload as WDispatchAttemptPayload).value
                        if(!conflict)assertEquals(a.task,(attempt.reservation.operation as CReserveAssignmentOperation).value.task)
                        if(!frontier&&!conflict)assertEquals(a.workspaceId,(attempt.reservation.operation as CReserveAssignmentOperation).value.workspaceId)
                        else assertEquals(2,current.records.values.count { it.payload is WDispatchAttemptPayload })
                        if(parallel) {
                            assertEquals(2,current.records.values.count { it.payload is WDispatchAttemptPayload })
                            assertEquals(if(conflict)1 else 2,current.records.values.count { (it.payload as? WAssignmentsPayload)?.value?.state=="bound" })
                            assertEquals(if(conflict)1 else 2,current.records.values.count { (it.payload as? WClaimsPayload)?.value?.state=="held" })
                        }
                    }
                    assertEquals("counter=0\n",Files.readString(source.resolve("counter.txt")))
                    if(diff && holdRetention) {
                        assertTrue(app.coordinationCapture(plannerToken,project).work.records.values.none { it.payload is WResultsPayload })
                    } else if(diff) {
                        val resultId=uuid(result.getValue("resultId"));val work=app.coordinationCapture(plannerToken,project).work
                        val ref=work.reference(WorkFamily.RESULTS,WorkKey.Id(project,resultId));val record=(work.resolve(ref).payload as WResultsPayload).value
                        val read=WrithelmService(app).read(plannerToken,codec.encodeRead(WorkRead.Body(project,record.changes.digest,WorkRead.Exact(project,ref.identity,ref.digest,null))))
                        assertEquals(200,read.status)
                        savedResult=ref; savedReport=((read.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string()
                        assertTrue(requireNotNull(savedReport).contains("+counter=1"))
                        // Planner-side bearing check against actual retained Git objects, independent
                        // of the worker's success label and the adapter's reported diff digest.
                        val actualDiff=git("diff","--no-ext-diff","--no-textconv","--no-color","--binary","--full-index",
                            requireNotNull(snapshot.commit).hex,requireNotNull(record.artifacts.single().commit).hex,"--")
                        assertEquals(actualDiff,requireNotNull(savedReport).trim())
                        assertEquals(record.changes.digest,Digest.of(requireNotNull(savedReport).toByteArray()))
                        assertEquals(record.changes.byteLength,requireNotNull(savedReport).toByteArray().size)
                        if(dependent) {
                            val inputs=app.candidateAssemblyInputs(plannerToken,project,f.controller,listOf(ref))
                            assertEquals(ref,inputs.single().reference)
                            assertEquals(record.artifacts.single(),inputs.single().result)
                            assertFailsWith<AppAccessFault> { app.candidateAssemblyInputs(token(f.workerKey),project,f.controller,listOf(ref)) }
                            assertFailsWith<WorkFault> { app.candidateAssemblyInputs(plannerToken,project,f.controller,listOf(ref,ref)) }
                        }
                    }
                }finally{runBlocking { adapter.mcp.close() };server.stop(0,5000);endpoint.close()}
            }
            if(planned) {
                H1Application.open(storeRoot,storeId,options).use { app ->
                    val current=app.coordinationCapture(plannerToken,project).work
                    val attempt=(current.record(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,id(59504))).payload as WDispatchAttemptPayload).value
                    if(!frontier&&!conflict)assertEquals(actualAssignment,(attempt.reservation.operation as CReserveAssignmentOperation).value.assignmentId)
                    assertEquals(if(start)"active" else "bound",current.assignment(actualAssignment!!).state)
                    if(diff && holdRetention) {
                        assertTrue(current.records.values.none { it.payload is WResultsPayload })
                    } else if(diff) {
                        val ref=requireNotNull(savedResult)
                        assertEquals(ref,current.reference(WorkFamily.RESULTS,ref.identity.key))
                        val record=(current.resolve(ref).payload as WResultsPayload).value
                        val selector=WorkRead.Exact(project,ref.identity,ref.digest,null)
                        val service=WrithelmService(app)
                        assertEquals(200,service.read(plannerToken,codec.encodeRead(selector)).status)
                        val read=service.read(plannerToken,codec.encodeRead(WorkRead.Body(project,record.changes.digest,selector)))
                        assertEquals(200,read.status)
                        assertEquals(savedReport,((read.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string())
                    }
                    if(parallel)assertEquals(if(conflict)1 else 2,current.records.values.count { (it.payload as? WAssignmentsPayload)?.value?.state=="bound" })
                }
                if(!conflict)RuntimeOwner.acquire(storeRoot).use { owner ->
                    val git=NativeGit(owner);git.qualify();git.verifyRepository()
                    fun created(path:String)=git.call(listOf("log","--diff-filter=A","--format=%H","--",path)).checked().decodeToString().trim().also { assertTrue(it.matches(Regex("[0-9a-f]{40}"))) }
                    val attemptPath=identityCodec.recordPath(WorkRecordIdentity(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,id(59504))))
                    val assignmentPath=identityCodec.recordPath(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,actualAssignment!!)))
                    val earlier=created(attemptPath);val later=created(assignmentPath)
                    assertNotEquals(earlier,later)
                    assertEquals(0,git.call(listOf("merge-base","--is-ancestor",earlier,later)).exit)
                }
            }
        }
    }
}
