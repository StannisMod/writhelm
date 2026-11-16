// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.*
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Actual H1 publisher/HTTP/authentication, registry, issuer, journal, Git assembly and check runner.
 * Fresh reviewer uses actual preparation/passive/delegation/bind adapters; installedNative also runs
 * the automatic coordinator against the pinned native 2.2.5 service instead of the synthetic SDK.
 * Qualification artifacts, native start/source observations and report prose are explicitly synthetic.
 * No model, real native model release, semantic acceptance or production qualification. */
internal fun candidateReviewServiceJourney(installedNative:Boolean=false) {
    val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"authenticated-review-")
    fun dir(name:String)=root.resolve(name).also {LinuxConnectedCustody(it)}
    fun privateBytes(file:Path,bytes:ByteArray) {Files.write(file,bytes);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"))}
    fun json(file:Path,value:JsonValue)=privateBytes(file,StrictJson.encode(value,2*1024*1024))
    val source=dir("source");val controllerRoot=dir("controller");val store=dir("store")
    val reviewAreas=dir("review-areas")
    val git=Path.of("/usr/bin/git").toRealPath();val gitPin=Digest.of(Files.readAllBytes(git))
    if(installedNative){
        val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE"))).toRealPath();val env=Path.of("/usr/bin/env").toRealPath()
        val policies=AutomaticProjectCheckDeployment(controllerRoot.resolve("installation"))
        policies.approve(listOf(obj("formatVersion" to num(1),"platform" to str("linux"),"sourceRoot" to str(source.toString()),
            "envExecutable" to obj("path" to str(env.toString()),"sha256" to digest(projectCheckExecutableDigest(env))),
            "checks" to JArray(listOf(obj("id" to str("node-test"),"executable" to obj("path" to str(node.toString()),"sha256" to digest(projectCheckExecutableDigest(node))),
                "args" to JArray(listOf(str("--test"),str("tests/candidate.test.cjs"))),"environment" to obj(),"timeoutMillis" to num(10000),"outputBytes" to num(32768)))))))
        policies.provision(source,controllerRoot,reviewAreas)
    }
    fun git(vararg args:String):String {
        val builder=ProcessBuilder(git.toString(),"-c","core.hooksPath=/dev/null","-C",source.toString(),*args).redirectError(root.resolve("git.log").toFile())
        builder.environment().clear();builder.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
        val child=builder.start();val output=child.inputStream.readNBytes(1048577)
        assertTrue(child.waitFor(15,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),"Git fixture failed; inspect $root")
        return output.toString(Charsets.UTF_8).trim()
    }
    git("init","-b","main");git("config","user.name","Synthetic review integration");git("config","user.email","fixture@writhelm.invalid")
    Files.writeString(source.resolve("result.txt"),"base\n")
    Files.createDirectory(source.resolve("tests"))
    Files.writeString(source.resolve("tests/candidate.test.cjs"),
        "require('node:test')('project result contract',()=>require('node:assert/strict').equal(require('node:fs').readFileSync('result.txt','utf8'),'implemented\\n'))\n")
    git("add","result.txt","tests");git("commit","-m","base")
    val base=WSnapshot(id(88000),WorkGitOid("sha1",git("rev-parse","HEAD")),WorkGitOid("sha1",git("rev-parse","HEAD^{tree}")),null,id(88001))
    Files.writeString(source.resolve("result.txt"),"implemented\n");git("commit","-am","implementation")
    val resultSnapshot=base.copy(commit=WorkGitOid("sha1",git("rev-parse","HEAD")),tree=WorkGitOid("sha1",git("rev-parse","HEAD^{tree}")),retainedLocatorId=id(88002))
    git("update-ref","refs/writhelm/results/${resultSnapshot.retainedLocatorId}",resultSnapshot.commit!!.hex,"0".repeat(40))
    val f=JointFixture(base,includeKnowledgeInput=true);val codec=WorkCodec()
    val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort};val origin="http://127.0.0.1:$port"
    val transportPort=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
    val transportOrigin="http://127.0.0.1:$transportPort";val transportProof=ByteArray(32){63}
    val nativeControl=HttpConnectedNativeControl(transportOrigin,transportProof)
    val pin=PinnedNativeExecutable("synthetic-jvm-profile",Digest.of(Files.readAllBytes(Path.of("/proc/self/exe"))))
    val pid=ProcessHandle.current().pid();val stat=Files.readString(Path.of("/proc/self/stat"))
    val processStart=stat.substring(stat.lastIndexOf(')')+1).trim().split(Regex("\\s+"))[19]
    val boot=Files.readString(Path.of("/proc/sys/kernel/random/boot_id")).trim()
    val epoch=Digest.of("[\"native-linux-process-v1\",\"${pin.digest.hex}\",\"$boot\",$pid,\"$processStart\",\"$origin\"]".toByteArray()).hex
    val configuration=Digest.of("SYNTHETIC qualification configuration; no production registration".toByteArray())
    var runtimeAvailable=true
    val qualification=ControllerPhaseAdmission(project,
        ControllerProfileEnvironment(project,f.controller,f.intended.nativeHostId,epoch,
            {if(runtimeAvailable)LinuxNativeProcessObserver.observe(pid,origin,epoch,pin) else null},{configuration},
            NativeExecutionProbe {state,subject,purpose-> // explicit synthetic observer; never production evidence
                val a=state.work.getValue(project).assignment(subject.assignmentId)
                a.executionGeneration==subject.generation && a.execution!=null && purpose in setOf("access","bound-access","retention","start")
            }))
    var issuer=ServiceCandidateReviewTickets(dir("review-tickets"),f.controller,NativeCandidateReviewReceipts(controllerRoot.resolve("native")),qualification,epoch)
    lateinit var options:H1Options
    fun assemblyProof(operation:UUID):JsonValue?=try {
        str(protectedRuntimeBytes(controllerRoot.resolve("native"),controllerRoot.resolve("native/candidate-receipt-${Digest.of(operation.toString().toByteArray()).hex}.json"),2*1024*1024).toString(Charsets.UTF_8))
    }catch(_:Exception){null}
    var app=f.publishedActiveApp(modelVersion=4,root=store,runtimeOptions={runtime ->
        val noReviewerFixture=H1RuntimeFacts {state,command ->
            val captured=runtime.capture(state,command)
            val reserve=(command.operation as? CReserveAssignmentOperation)?.value
            if(!installedNative || reserve?.intendedExecution?.template!="reviewer-v1")captured
            else H1ObservedFacts(captured.basis,captured.facts.copy(retainedSnapshots=emptySet(),verifiedPackets=emptySet(),
                submittedDiffs=emptySet(),knowledgeSources=emptySet(),approvedDecisions=emptySet()),captured.qualifiedProfiles)
        }
        val candidateFacts=NativeCandidateReviewInputFacts(CanonicalTaskDecisionFacts(noReviewerFixture),NativeCandidateReviewReceipts(controllerRoot.resolve("native")),nativeControl,qualification)
        val inputFacts=ReservationProofFacts(candidateFacts,DeclaredPrebindingInputVerifier(candidateFacts))
        H1Options(NativePreparationFacts(inputFacts,nativeControl,epoch),workModelVersion=4,nativeSafety=qualification,candidateAssemblyProof=::assemblyProof,
            candidateReviewTickets={controller ->issuer.takeIf {controller==f.controller}}).also {options=it}
    })
    var sequence=88100
    fun settle(op:dev.writhelm.core.work.WorkOperation,who:String=plannerToken):CommandResult.Settled {
        val settled=assertIs<CommandResult.Settled>(app.execute(who,codec.encodeCommand(WorkCommand(id(sequence++),project,op))))
        assertIs<LogicalOutcome.Success>(settled.receipt.outcome,"${op.name}: ${(settled.receipt.outcome as? LogicalOutcome.Rejected)?.code}")
        return settled
    }
    fun publish(op:dev.writhelm.core.work.WorkOperation,who:String=plannerToken):WorkReference {
        val success=settle(op,who).receipt.outcome as LogicalOutcome.Success
        val row=H1Catalog.result(success.operation,success.result);val family=H1Catalog.family(row.family)
        return WorkReference(WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(row.key))),digest(success.result.fields("key","generation","digest").getValue("digest")))
    }
    val proof=ByteArray(32){47};val http=HttpClient.newHttpClient()
    fun server():EmbeddedServer<CIOApplicationEngine,CIOApplicationEngine.Configuration> {
        val endpoint=WrithelmService(app).nativeEndpoint(project,f.controller,f.intended.nativeHostId,proof)
        val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),nativeEndpoint=endpoint)
        return embeddedServer(CIO,host="127.0.0.1",port=port){adapter.install(this)}.start(wait=false)
    }
    var server=server()
    fun query(operation:String,vararg fields:Pair<String,JsonValue>,who:String=plannerToken):HttpResponse<String> {
        val request=HttpRequest.newBuilder(URI("$origin/internal/native/candidate/v1")).timeout(java.time.Duration.ofSeconds(10))
            .header("Authorization","Bearer $who").header("X-Writhelm-Native-Proof",Base64.getUrlEncoder().withoutPadding().encodeToString(proof))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(StrictJson.encode(obj(
                "formatVersion" to num(1),"projectId" to uuid(project),"operation" to str(operation),*fields),2*1024*1024))).build()
        var response=http.send(request,HttpResponse.BodyHandlers.ofString())
        // These private projections/immutable issuance replays have no native create/start effect.
        // Preserve service ingress limits rather than widening admission for the test campaign.
        repeat(8){
            if(response.statusCode()!=429)return response
            Thread.sleep(250)
            response=http.send(request,HttpResponse.BodyHandlers.ofString())
        }
        return response
    }
    try {
        val producer=WAssignmentSubject(f.assignment,0)
        val retro=publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(sequence++)),
            CRecordRetrospectiveData(f.task,producer,null,"no-significant-new-findings",emptyList()),emptyList())),token(f.workerKey))
        val diff=Utf8Content.fromText(git("diff","--no-ext-diff","--no-textconv","--no-color","--binary","--full-index",base.commit!!.hex,resultSnapshot.commit.hex,"--")+"\n")
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+resultSnapshot,
            submittedDiffs=f.runtimeFacts.submittedDiffs+WorkSubmittedDiff(f.task,listOf(resultSnapshot),WorkBodyReference(diff.digest,diff.sizeBytes)))
        val result=publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(sequence++)),CSubmitResultData(f.task,producer,f.manifest,
            listOf(resultSnapshot),WorkBodyReference(diff.digest,diff.sizeBytes),listOf(id(96)),emptyList(),retro,emptyList()),
            listOf(CSubmitResultBodiesItem(diff.digest,diff.text())))),token(f.workerKey))
        val check=DispatchCandidateCheck("whole-candidate-file",listOf("immutable-git-file-sha256-v1","result.txt",Digest.of("implemented\n".toByteArray()).hex),false,emptyList())
        val plannedChecks=listOf(check)+if(installedNative)listOf(DispatchCandidateCheck("actual-project-test",listOf("project-check-v1","node-test"),false,emptyList())) else emptyList()
        val plan=DispatchPlanCandidate(project,id(88200),f.controller,listOf(DispatchPlanEntry(f.task,f.manifest,f.profile,listOf(f.resource),id(88201),id(88202))),plannedChecks)
        val planResult=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchPlanCommandCodec().encode(DispatchPlanCommand(id(sequence++),plan))))
        assertIs<LogicalOutcome.Success>(planResult.receipt.outcome)
        val planRef=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,plan.planId))
        val selectedJSON=StrictJson.encode(JArray(listOf(StrictJson.decode(identityCodec.encodeReference(result))))).toString(Charsets.UTF_8)
        val config=root.resolve("assembly-config.json")
        json(config,obj("sourceRoot" to str(source.toString()),"projectId" to uuid(project),"base" to encodeWSnapshot(base),"result" to encodeWSnapshot(resultSnapshot),
            "installedNative" to JBoolean(installedNative),"cli" to str(System.getenv("WRITHELM_NATIVE_CLI") ?: "unused"),
            "projectCheckTemplateJSON" to if(installedNative)str(protectedRuntimeBytes(controllerRoot.resolve("bootstrap"),
                controllerRoot.resolve("bootstrap/project-check-template-${Digest.of("current".toByteArray()).hex}.json"),1024*1024).toString(Charsets.UTF_8))else JNull,
            "selectedJSON" to str(selectedJSON),"service" to obj("origin" to str(origin),"proof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(proof)),
                "storeId" to uuid(storeId),"controllerId" to uuid(f.controller),"hostId" to uuid(f.intended.nativeHostId),"token" to str(plannerToken),
                "planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)))))
        val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER"))).resolve("tests/candidate-review-receipts-client.js")
        val child=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),script.toString(),controllerRoot.toString(),git.toString(),gitPin.hex,config.toString())
            .redirectOutput(root.resolve("assembly-output.json").toFile()).redirectError(root.resolve("assembly.log").toFile()).start()
        assertTrue(child.waitFor(60,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),"Real service-authorized assembly/checks failed; inspect $root")
        val frame=StrictJson.decode(Files.readAllBytes(root.resolve("assembly-output.json"))).fields("projectId","assemblyId","testPlanId","checks","candidateId")
        val assembly=uuid(frame.getValue("assemblyId"));val checkPlan=uuid(frame.getValue("testPlanId"))
        val observed=assertNotNull(NativeCandidateReviewReceipts(controllerRoot.resolve("native")).observe(project,assembly,checkPlan,plan))
        if(installedNative)assertTrue(observed.checks.any {it.checkId=="actual-project-test"},"Real project test must be part of the service-selected required check receipts")
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+observed.candidate)
        val proposalResponse=query("candidate-review-preparation","planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),
            "assemblyId" to uuid(assembly),"checkPlanId" to uuid(checkPlan))
        assertEquals(200,proposalResponse.statusCode(),proposalResponse.body())
        val proposal=(StrictJson.decode(proposalResponse.body().toByteArray()) as JObject).entries
        val createCommand=WorkCodec(modelVersion=4).decodeCommand(proposal.getValue("taskCommandJSON").string().toByteArray())
        val creation=(createCommand.operation as CCreateTaskRevisionOperation).value
        val task=creation.key;val verification=creation.data.verificationPlan!!
        val reviewPlan=creation.newVerificationPlan!!.data
        assertEquals("verification",creation.data.kind)
        assertEquals(listOf(observed.candidate),creation.data.base!!.code)
        assertEquals(listOf(result),creation.data.dependencies.map {it.selectedResult})
        assertTrue(reviewPlan.gates.all {it.independent})
        assertTrue(creation.data.criteria.any {it.statement==f.base.work.getValue(project).task(f.task).criteria.single().statement})
        assertTrue(creation.data.criteria.flatMap {it.clauses}.any {it is WSourceVariant0Value},"Exercise a mandatory pinned canonical knowledge input")
        assertEquals(proposalResponse.body(),query("candidate-review-preparation","planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),
            "assemblyId" to uuid(assembly),"checkPlanId" to uuid(checkPlan)).body(),"Automatic reviewer command IDs must be stable")
        val beforeInvalidPreparation=app.coordinationCapture(plannerToken,project).work.records.size
        val missingChecks=query("candidate-review-preparation","planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),
            "assemblyId" to uuid(assembly),"checkPlanId" to uuid(UUID.randomUUID()))
        assertTrue(missingChecks.statusCode() in setOf(400,403),"Missing check custody must not generate a reviewer proposal")
        val workerPreparation=query("candidate-review-preparation","planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),
            "assemblyId" to uuid(assembly),"checkPlanId" to uuid(checkPlan),who=token(f.workerKey))
        assertEquals(403,workerPreparation.statusCode(),"A producer cannot request automatic reviewer preparation")
        assertEquals(beforeInvalidPreparation,app.coordinationCapture(plannerToken,project).work.records.size,
            "Rejected preparation projections must not create tasks or assignments")
        for(name in listOf("taskCommandJSON","manifestCommandJSON")){
            val created=assertIs<CommandResult.Settled>(app.execute(plannerToken,proposal.getValue(name).string().toByteArray()))
            assertIs<LogicalOutcome.Success>(created.receipt.outcome,"Automatic canonical review creation must use actual service admission")
        }
        val selection=(StrictJson.decode(proposal.getValue("selectionJSON").string().toByteArray()) as JObject).entries
        val manifest=identityCodec.decodeReference(StrictJson.encode(selection.getValue("manifest")))
        val ticketBasis=(StrictJson.decode(proposal.getValue("ticketBasisJSON").string().toByteArray()) as JObject).entries
        f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=f.runtimeFacts.verifiedPackets+manifest,retainedSnapshots=f.runtimeFacts.retainedSnapshots+observed.candidate)
        val preparationConfig=root.resolve("preparation-config.json")
        json(preparationConfig,obj("root" to str(controllerRoot.toString()),"sourceRoot" to str(source.toString()),"projectId" to uuid(project),
            "service" to (StrictJson.decode(Files.readAllBytes(config)) as JObject).entries.getValue("service"),
            "sessionID" to str("ses_synthetic_composition_controller"),"principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"epoch" to str(epoch),
            "git" to obj("path" to str(git.toString()),"sha256" to digest(gitPin)),"repositoryId" to uuid(base.repositoryId),
            "worktreeParent" to str(reviewAreas.toString()),"transportOrigin" to str(transportOrigin),
            "transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(transportProof)),
            "selectionJSON" to proposal.getValue("selectionJSON"),"preparationId" to proposal.getValue("preparationId"),
            "installedNative" to JBoolean(installedNative),"cli" to obj("path" to str(System.getenv("WRITHELM_NATIVE_CLI") ?: "unused"),
                "sha256" to str("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")),
            "profile" to obj("planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),"operationId" to uuid(assembly),
                "candidateId" to uuid(observed.candidate.retainedLocatorId),"head" to encodeWSnapshot(base),"time" to str("1790000000 +0000"),"testPlanId" to uuid(checkPlan),"capacity" to num(1)),
            "candidate" to obj("commit" to workOid(observed.candidate.commit!!),"tree" to workOid(observed.candidate.tree)),"selectedJSON" to str(selectedJSON)))
        privateBytes(root.resolve("preparation-output.json"),byteArrayOf()) // Contains a disposable delegated credential.
        val preparationProcess=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),script.parent.resolve("candidate-review-preparation-client.js").toString(),preparationConfig.toString())
            .redirectOutput(root.resolve("preparation-output.json").toFile()).redirectError(root.resolve("preparation.log").toFile()).start()
        assertTrue(preparationProcess.waitFor(60,TimeUnit.SECONDS));assertEquals(0,preparationProcess.exitValue(),"Fresh reviewer preparation failed; inspect $root")
        val prepared=(StrictJson.decode(Files.readAllBytes(root.resolve("preparation-output.json"))) as JObject).entries
        assertEquals(str("authenticated-native-v1"),prepared.getValue("proofTransport"))
        assertEquals(JBoolean(true),prepared.getValue("exactCandidateInputs"))
        if(installedNative){
            assertEquals(JBoolean(true),prepared.getValue("reviewerPolicyProbe"))
            assertEquals(JBoolean(true),prepared.getValue("unobservedStartSourceHeld"))
            assertEquals(JBoolean(true),prepared.getValue("projectChecksProbe"))
        }
        assertFalse(Files.exists(controllerRoot.resolve("preparation-proof.json")),"No test file port may supply preparation facts")
        if(installedNative){
            val capturedState=assertNotNull(f.capturedRuntimeState)
            val reservedCommand=WorkCodec(modelVersion=4).decodeCommand(prepared.getValue("reservationJSON").string().toByteArray())
            val reader=NativeCandidateReviewReceipts(controllerRoot.resolve("native"))
            val actualProof=prepared.getValue("candidateProofJSON")
            assertEquals(observed.candidate,reader.reservationCandidate(capturedState,reservedCommand){actualProof})
            assertNull(reader.reservationCandidate(capturedState,reservedCommand){null})
            assertNull(reader.reservationCandidate(capturedState,reservedCommand){str("changed proof")})
            val checkReceipt=controllerRoot.resolve("native/candidate-test-result-${Digest.of(observed.checks.first().runId.toString().toByteArray()).hex}.json")
            val originalBytes=Files.readAllBytes(checkReceipt)
            try{
                Files.writeString(checkReceipt,originalBytes.toString(Charsets.UTF_8).replace("\"passed\"","\"unknown\""))
                assertNull(reader.reservationCandidate(capturedState,reservedCommand){actualProof})
            }finally{Files.write(checkReceipt,originalBytes)}
        }
        val reviewer=WAssignmentSubject(uuid(prepared.getValue("assignmentId")),0)
        val reviewerToken=prepared.getValue("reviewerToken").string()
        val execution=decodeWExecution(prepared.getValue("execution"))
        val assignment=app.coordinationCapture(plannerToken,project).work.assignment(reviewer.assignmentId)
        val intended=assignment.intendedExecution
        assertEquals("reviewer-v1",intended.template)
        val binding=app.coordinationCapture(plannerToken,project).work.assignment(reviewer.assignmentId).binding!!
        val key=Digest.of(StrictJson.encode(JArray(listOf(uuid(reviewer.assignmentId),str("0"))))).hex
        val job=StrictJson.decode(Files.readAllBytes(controllerRoot.resolve("native/worker-preparation-$key.json")),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024)) as JObject
        val receiverFrame=StrictJson.decode(Files.readAllBytes(controllerRoot.resolve("native/worker-prepared-$key.json")),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024)) as JObject
        fun receiver(original:JObject) {
            json(controllerRoot.resolve("native/worker-preparation-$key.json"),original)
            json(controllerRoot.resolve("native/worker-prepared-$key.json"),JObject(receiverFrame.entries+
                ("preparationJSON" to str(StrictJson.encode(original).toString(Charsets.UTF_8)))))
        }
        receiver(job)
        val ticket=CandidateReviewTicket(project,uuid(ticketBasis.getValue("reviewId")),f.controller,planRef,assembly,observed.candidate,listOf(result),checkPlan,observed.checks,
            reviewer,binding,execution,uuid(ticketBasis.getValue("contextId")),listOf(planner,f.worker))
        val ticketJSON=CandidateReviewTicketCodec().encode(ticket).toString(Charsets.UTF_8)
        fun issue()=query("candidate-review-ticket","ticketJSON" to str(ticketJSON))
        if(!installedNative){
        receiver(JObject(job.entries+("intentId" to uuid(f.intent))))
        assertTrue(issue().statusCode() in setOf(400,403),"Another assignment's prepare intent cannot prove reviewer freshness")
        assertNull(issuer.retained(ticket.reviewId))
        receiver(JObject(job.entries+("epoch" to str("0".repeat(64)))))
        assertTrue(issue().statusCode() in setOf(400,403),"Stale native preparation epoch cannot issue a ticket")
        receiver(job)
        }
        val first=issue()
        assertEquals(200,first.statusCode(),first.body())
        val issued=StrictJson.decode(first.body().toByteArray()).fields("formatVersion","kind","ticketJSON","ticketDigest")
        assertEquals(ticketJSON,issued.getValue("ticketJSON").string())
        assertEquals(CandidateReviewTicketCodec().digest(ticket),digest(issued.getValue("ticketDigest")))
        assertEquals(ticket,issuer.retained(ticket.reviewId));assertEquals(first.body(),issue().body())
        runtimeAvailable=false
        assertTrue(issue().statusCode() in setOf(400,403),"A retained ticket cannot bypass missing current process observation")
        runtimeAvailable=true
        assertEquals(200,issue().statusCode())
        val checkReceipt=controllerRoot.resolve("native/candidate-test-result-${Digest.of(ticket.checks.first().runId.toString().toByteArray()).hex}.json")
        val originalReceipt=Files.readAllBytes(checkReceipt)
        Files.writeString(checkReceipt,originalReceipt.toString(Charsets.UTF_8).replace("\"passed\"","\"unknown\""))
        assertTrue(issue().statusCode() in setOf(400,403),"An unknown check outcome must refuse even after issuance")
        Files.write(checkReceipt,originalReceipt)
        val changedTicket=CandidateReviewTicketCodec().encode(ticket.copy(reviewId=id(88406),contextId=id(88407))).toString(Charsets.UTF_8)
        assertTrue(query("candidate-review-ticket","ticketJSON" to str(changedTicket)).statusCode() in setOf(400,403))
        assertEquals(ticket,issuer.retained(ticket.reviewId));assertEquals(200,issue().statusCode())
        server.stop(100,2000);app.close()
        issuer=ServiceCandidateReviewTickets(root.resolve("review-tickets"),f.controller,NativeCandidateReviewReceipts(controllerRoot.resolve("native")),qualification,epoch)
        app=H1Application.open(store,storeId,options);server=server()
        assertEquals(first.body(),issue().body(),"Restart must preserve the exact issued ticket")
        val start=id(88402)
        settle(CRequestNativeTransitionOperation(CRequestNativeTransition(reviewer.assignmentId,2,start,id(88403),"start",null,emptyList())))
        val packet=app.nativeInitialContext(plannerToken,project,f.controller,intended.nativeHostId,reviewer,binding,start) as JObject
        assertTrue(packet.entries.getValue("packetJSON").string().contains("writhelm-candidate-review-ticket-"))
        assertTrue(packet.entries.getValue("packetJSON").string().contains(ticket.contextId.toString()))
        f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(start to WorkNativeObservation(reviewer,start,id(88403),"start","confirmed",listOf(f.source),execution,false,"bound")))
        settle(CObserveNativeTransitionOperation(CObserveNativeTransition(reviewer.assignmentId,3,start,0,"confirmed",listOf(f.source),emptyList())))
        val cutDigest=Digest.of("SYNTHETIC native context; no inference".toByteArray());val packetDigest=Digest.of(packet.entries.getValue("packetJSON").string().toByteArray());val locator=id(88404)
        f.runtimeFacts=f.runtimeFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(intended.nativeHostId,intended.nativeSessionId,cutDigest,packetDigest,listOf("synthetic-message" to cutDigest))))
        val cut=publish(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(project,ticket.contextId),WSourceCuts(task,reviewer,intended.nativeHostId,intended.nativeSessionId,
            f.profile,1,cutDigest,packetDigest,locator,listOf(WSourceCutsNativeMessagesItem("synthetic-message",cutDigest)),emptyList(),emptyList()),emptyList())),reviewerToken)
        val pending=query("candidate-review-status","reviewId" to uuid(ticket.reviewId))
        assertEquals(200,pending.statusCode(),pending.body())
        assertEquals("candidate-review-pending",(StrictJson.decode(pending.body().toByteArray()) as JObject).entries.getValue("kind").string())
        val reportText=Utf8Content.fromText("Synthetic report for model-free service integration, not semantic acceptance")
        val reviewData=CSubmitVerificationReportData(task,reviewer,listOf(result),verification,
            reviewPlan.gates.map {it.id},WSourceVariant2Value(WSourceVariant2("work",cut)),listOf(WSourceVariant1Value(WSourceVariant1("artifact",ticket.candidate))),emptyList(),emptyList(),
            WorkBodyReference(reportText.digest,reportText.sizeBytes),emptyList())
        val report=publish(CSubmitVerificationReportOperation(CSubmitVerificationReport(WorkKey.Id(project,id(88405)),reviewData,
            listOf(CSubmitVerificationReportBodiesItem(reportText.digest,reportText.text())))),reviewerToken)
        fun readReport()=query("candidate-review-report","reviewId" to uuid(ticket.reviewId),"reportRefJSON" to str(identityCodec.encodeReference(report).toString(Charsets.UTF_8)))
        val reported=readReport();assertEquals(200,reported.statusCode(),reported.body())
        assertEquals(JBoolean(false),(StrictJson.decode(reported.body().toByteArray()) as JObject).entries.getValue("integrationAllowed"))
        server.stop(100,2000);app.close()
        issuer=ServiceCandidateReviewTickets(root.resolve("review-tickets"),f.controller,NativeCandidateReviewReceipts(controllerRoot.resolve("native")),qualification,epoch)
        app=H1Application.open(store,storeId,options);server=server()
        assertEquals(reported.body(),readReport().body(),"Canonical report must survive service restart unchanged")
        assertEquals(200,issue().statusCode(),"Re-reading an issued ticket must not demand another fresh receiver or start")
        assertEquals(403,query("candidate-review-ticket","ticketJSON" to str(ticketJSON),who=reviewerToken).statusCode())
        val discovered=query("candidate-review-status","reviewId" to uuid(ticket.reviewId))
        assertEquals(200,discovered.statusCode(),discovered.body())
        val compositionConfig=root.resolve("composition-config.json")
        json(compositionConfig,obj("root" to str(controllerRoot.toString()),"sourceRoot" to str(source.toString()),"projectId" to uuid(project),
            "service" to (StrictJson.decode(Files.readAllBytes(config)) as JObject).entries.getValue("service"),
            "sessionID" to str("ses_synthetic_composition_controller"),"principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"epoch" to str(epoch),
            "git" to obj("path" to str(git.toString()),"sha256" to digest(gitPin)),"ticketJSON" to str(ticketJSON),
            "preparedStart" to JBoolean(installedNative),
            "profile" to obj("planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),"operationId" to uuid(assembly),
                "candidateId" to uuid(ticket.candidate.retainedLocatorId),"head" to encodeWSnapshot(base),"time" to str("1790000000 +0000"),
                "testPlanId" to uuid(checkPlan),"capacity" to num(1))))
        val composition=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),script.parent.resolve("candidate-review-composition-client.js").toString(),compositionConfig.toString())
            .redirectOutput(root.resolve("composition-output.json").toFile()).redirectError(root.resolve("composition.log").toFile()).start()
        assertTrue(composition.waitFor(30,TimeUnit.SECONDS));assertEquals(0,composition.exitValue(),"Automatic canonical review composition held; inspect $root")
        val partial=publish(CSubmitVerificationReportOperation(CSubmitVerificationReport(WorkKey.Id(project,id(88408)),reviewData.copy(gateIds=listOf(reviewPlan.gates.first().id)),emptyList())),reviewerToken)
        assertEquals(403,query("candidate-review-report","reviewId" to uuid(ticket.reviewId),
            "reportRefJSON" to str(identityCodec.encodeReference(partial).toString(Charsets.UTF_8))).statusCode(),"A partial-gate report cannot be final candidate review")
        assertEquals(403,query("candidate-review-status","reviewId" to uuid(ticket.reviewId)).statusCode(),"Multiple reports must not select a latest/successful subset")
        val revoke=Auth3Command(id(sequence++),project,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptySet()))
        assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(revoke))).receipt.outcome)
        assertEquals(403,issue().statusCode());assertEquals(403,readReport().statusCode())
        assertEquals(403,query("candidate-review-status","reviewId" to uuid(ticket.reviewId)).statusCode())
        assertEquals(403,query("candidate-review-preparation","planRefJSON" to str(identityCodec.encodeReference(planRef).toString(Charsets.UTF_8)),
            "assemblyId" to uuid(assembly),"checkPlanId" to uuid(checkPlan)).statusCode())
        val revokedPreparationConfig=StrictJson.decode(Files.readAllBytes(preparationConfig)) as JObject
        json(preparationConfig,JObject(revokedPreparationConfig.entries+("expectRevoked" to JBoolean(true))+
            ("expectedReviewerSession" to str(intended.nativeSessionId))))
        val revokedPreparation=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),script.parent.resolve("candidate-review-preparation-client.js").toString(),preparationConfig.toString())
            .redirectOutput(root.resolve("revoked-preparation-output.json").toFile()).redirectError(root.resolve("revoked-preparation.log").toFile()).start()
        assertTrue(revokedPreparation.waitFor(30,TimeUnit.SECONDS));assertEquals(0,revokedPreparation.exitValue(),"Revoked preparation must hold without native effects; inspect $root")
        val revokedConfig=StrictJson.decode(Files.readAllBytes(compositionConfig)) as JObject
        json(compositionConfig,JObject(revokedConfig.entries+("expectRevoked" to JBoolean(true))))
        val revokedLoop=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),script.parent.resolve("candidate-review-composition-client.js").toString(),compositionConfig.toString())
            .redirectOutput(root.resolve("revoked-composition-output.json").toFile()).redirectError(root.resolve("revoked-composition.log").toFile()).start()
        assertTrue(revokedLoop.waitFor(30,TimeUnit.SECONDS));assertEquals(0,revokedLoop.exitValue(),"Revoked automatic loop must hold; inspect $root")
        assertEquals(ticket,issuer.retained(ticket.reviewId));assertEquals("implemented\n",Files.readString(source.resolve("result.txt")))
        assertEquals(resultSnapshot.commit.hex,git("rev-parse","main"))
        json(root.resolve("acceptance.json"),obj("passed" to JBoolean(true),"qualification" to str("synthetic-test-only"),"nativeObservations" to str("synthetic-test-only"),
            "authenticatedIssuance" to JBoolean(true),"restartExactReplay" to JBoolean(true),"mandatoryPacket" to JBoolean(true),"canonicalReport" to JBoolean(true),
            "revocationDenies" to JBoolean(true),"automaticReviewComposition" to JBoolean(true),"automaticReviewTaskCreation" to JBoolean(true),"revokedLoopHeld" to JBoolean(true),
            "freshPreparationAdapters" to JBoolean(true),"nativeSDK" to str(if(installedNative)"installed-2.2.5" else "synthetic-test-only"),"revokedPreparationHeld" to JBoolean(true),
            "proofTransport" to str("authenticated-native-v1"),"exactCandidateInputs" to JBoolean(true),
            "candidateReservationFact" to str(if(installedNative)"independently-observed" else "fixture-control"),
            "reviewerVerifiedPacketFixture" to JBoolean(!installedNative),
            "reviewerInputFactsFixture" to JBoolean(!installedNative),
            "nativeReviewerPermissionProbe" to JBoolean(installedNative),"unobservedStartSourceHeld" to JBoolean(installedNative),
            "nativeProjectChecksProbe" to JBoolean(installedNative),
            "authenticatedProjectCheckReceipt" to JBoolean(installedNative),
            "integrationAllowed" to JBoolean(false),"modelCalls" to num(0)))
    } finally {server.stop(100,2000);app.close();http.close();nativeControl.close()}
}
