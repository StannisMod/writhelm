// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import dev.writhelm.service.*
import kotlinx.coroutines.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Actual portable CORE/AUTH composition. All external/native observations are explicitly synthetic;
 * no qualified native runtime, source custody, model or physical completion is implied. */
private class JointFixture {
    val head=GitOid("sha1","1".repeat(40));val controller=id(60);val assignment=id(61);val delegation=id(62)
    val worker=id(63);val workerKey=id(64);val workspace=id(65);val resource=WorkKey.Resource(project,id(66))
    val intent=id(67);val correlation=id(68);val task=WorkKey.TaskRevision(project,id(69),0)
    val profile=WorkKey.Profile(project,id(70),0);val plan=WorkKey.Revision(project,id(71),0)
    val applicability=WorkKey.Revision(project,id(72),0);val manifestKey=WorkKey.Id(project,id(73))
    val intended=WExecutionIntent(controller,id(74),"executor-v1",id(75),"ses_joint_fixture")
    val bridge=H1JointReduction();val codec=WorkCodec();var base: H1BoundState
    val snapshot=WSnapshot(id(80),WorkGitOid("sha1","2".repeat(40)),WorkGitOid("sha1","3".repeat(40)),null,id(81))
    val source=WSourceVariant1Value(WSourceVariant1("artifact",snapshot))
    val manifest: WorkReference
    val observations: H1ObservedFacts
    lateinit var runtimeFacts: WorkExternalFacts
    init {
        val g=genesis();var core=g.workflow;val revision=revision(90)
        core=value(core.propose(Actor(planner),revision)).state
        val packet=packet(listOf(ReviewItem(id(91),revision.x()),ReviewItem(id(98),revision.contextReference())),listOf(revision.contextReference()))
        val request=value(core.request(Actor(planner),project,id(92),packet));core=request.state
        core=value(core.resolve(Actor(owner),project,id(92),request.value.digest,packet.digest,StructuredAnswer.Approve)).state
        val refs=listOf(revision.x(),revision.contextReference())
        core=value(core.assess(Actor(planner),ConsistencyAssessment(id(93),project,refs,"actual exact assessment",emptyList(),emptyList()))).state
        core=value(core.pin(Actor(planner),project,id(94),id(95),listOf(revision.x()),listOf(revision.contextReference()),emptyList(),id(93))).state
        val ks=core.snapshot();val kr=CoreRecordCodec.records(ks).single { it.family=="manifests" }
        val pinned=WBase(listOf(snapshot),id(94),Digest.of(StrictJson.encode(CoreRecordCodec.envelope(kr.family,kr.key,kr.payload()))))
        val criterion=WCriterion(id(96),"bounded output",emptyList())
        fun record(key: WorkKey,payload: WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(payload),key),payload)
        val m=record(manifestKey,WInputManifestsPayload(WInputManifests(task,pinned,applicability,listOf(WManifestItem(source,true,"exact input",emptyList())),source,emptyList())))
        manifest=WorkReference(m.identity,codec.digest(m))
        val work=WorkState.restore(project,listOf(
            record(task,WTaskRevisionsPayload(WTaskRevisions("research","executable","bounded research",pinned,WScope("pinned inputs","workspace","routine",emptyList()),emptyList(),emptyList(),listOf(criterion),listOf("changed meaning"),"result",profile,plan,emptyList(),emptyList()))),
            record(profile,WProfilesPayload(WProfile("fixture-not-a-real-native-profile",listOf(source),WorkModelReference("synthetic","no-call",null),"no-call","bounded",8192,512,512,512,"native","synthetic","E-COOP-1",emptyList(),"linux","qualified"))),
            record(plan,WVerificationPlansPayload(WVerificationPlans(task,null,listOf(criterion),listOf(WGate(id(97),listOf(criterion.id),"pure",false,"acceptance",null,false,"bounded")),"initial",emptyList()))),
            record(applicability,WApplicabilityPayload(WApplicability(null,emptyList(),listOf(source)))),m),emptyList())
        val auth=ArrayList<Auth2Record>()
        g.authority.principals.forEach { auth+=Auth2Record.Principal(it) }
        g.authority.projects.values.forEach { auth+=Auth2Record.Project(it) }
        g.authority.guards.forEach { (e,guard) -> auth+=Auth2Record.Guard(e,guard);auth+=Auth2Record.WorkGrants(WorkGrant(e,0,if(e.principalId in setOf(owner,planner))WorkPermission.entries else emptyList())) }
        g.authority.credentials.values.forEach { auth+=Auth2Record.Key(ScopedCredential(it,if(it.principalId==planner)CredentialScope.Controller(controller) else CredentialScope.Project)) }
        auth+=Auth2Record.Controller(ControllerBinding(controller,project,planner))
        base=H1BoundState.checked(storeId,head,core,auth,mapOf(project to work))
        observations=H1ObservedFacts(head,WorkExternalFacts(emptySet(),emptySet(),setOf(snapshot),emptyMap(),emptyMap(),emptySet(),emptySet(),emptySet(),setOf(manifest),emptyMap(),emptyMap(),emptySet()),setOf(work.reference(WorkFamily.PROFILES,profile)))
        runtimeFacts=observations.facts
    }
    fun <T> key(id: java.util.UUID,block: (PresentedCredential)->T)=requireNotNull(CredentialVerifier.parse(token(id))).use(block)
    fun install(candidate: H1JointCandidate) { base=H1BoundState.checked(storeId,head,candidate.core,candidate.authority.records(),candidate.work) }
    fun reserveCommand()=WorkCommand(id(100),project,CReserveAssignmentOperation(CReserveAssignment(assignment,task,manifest,profile,workspace,
        listOf(CReserveAssignmentResourcesItem(resource,null)),emptyList(),intent,correlation,emptyList(),intended)))
    fun reserve() { install(key(plannerKey) { bridge.work(base,reserveCommand(),it,observations) }) }
    fun register()=Auth3Command(id(101),project,Auth3Payload.RegisterDelegation(delegation,
        DelegationBinding(controller,project,worker,workerKey,task.taskId,assignment,intended.executionId,0,DelegationTemplate.EXECUTOR,emptyList()),0,
        CredentialVerifier.digest(storeId,project,worker,workerKey,secret(workerKey.leastSignificantBits.toInt()))))
    fun activateWithAttributedFixtureObservations() {
        reserve();install(key(plannerKey) { bridge.auth3(base,register(),it) })
        val execution=WExecution(intended.executionId,worker,workerKey,intended.nativeHostId,intended.nativeSessionId,workspace)
        val subject=WAssignmentSubject(assignment,0)
        val prepare=WorkNativeObservation(subject,intent,correlation,"prepare","confirmed",listOf(source),execution,true,"reserved")
        val bind=WorkCommand(id(110),project,CBindAssignmentOperation(CBindAssignment(assignment,1,intent,0,intended.nativeHostId,intended.nativeSessionId,workspace,listOf(source),emptyList())))
        install(key(plannerKey) { bridge.work(base,bind,it,H1ObservedFacts(head,observations.facts.copy(native=mapOf(intent to prepare)),observations.qualifiedProfiles)) })
        val startId=id(111);val startCorrelation=id(112)
        val request=WorkCommand(id(113),project,CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,startId,startCorrelation,"start",null,emptyList())))
        install(key(plannerKey) { bridge.work(base,request,it,observations) })
        val start=WorkNativeObservation(subject,startId,startCorrelation,"start","confirmed",listOf(source),execution,false,"bound")
        val observe=WorkCommand(id(114),project,CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,startId,0,"confirmed",listOf(source),emptyList())))
        install(key(plannerKey) { bridge.work(base,observe,it,H1ObservedFacts(head,observations.facts.copy(native=mapOf(startId to start)),observations.qualifiedProfiles)) })
    }
    fun publishedActiveApp(onCapture: ()->Unit={},publication: PublicationDriver=PublicationDriver.Native): H1Application {
        val options=H1Options(H1RuntimeFacts { state,_ -> onCapture();H1ObservedFacts(state.basis,runtimeFacts,observations.qualifiedProfiles) },AppOptions(admission=AdmissionPolicy(formatVersion=2),publication=publication))
        val app=H1Application.initialize(directory(),storeId,H1Genesis(base.core,base.authority.records(),base.work),options)
        fun publish(command: WorkCommand) {
            val result=assertIs<CommandResult.Settled>(app.execute(plannerToken,codec.encodeCommand(command)))
            assertIs<LogicalOutcome.Success>(result.receipt.outcome)
        }
        publish(reserveCommand());assertIs<CommandResult.Settled>(app.execute(plannerToken,Auth3CommandCodec().encode(register())))
        val execution=WExecution(intended.executionId,worker,workerKey,intended.nativeHostId,intended.nativeSessionId,workspace)
        val subject=WAssignmentSubject(assignment,0)
        runtimeFacts=runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(subject,intent,correlation,"prepare","confirmed",listOf(source),execution,true,"reserved")))
        publish(WorkCommand(id(110),project,CBindAssignmentOperation(CBindAssignment(assignment,1,intent,0,intended.nativeHostId,intended.nativeSessionId,workspace,listOf(source),emptyList()))))
        publish(WorkCommand(id(113),project,CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,id(111),id(112),"start",null,emptyList()))))
        runtimeFacts=runtimeFacts.copy(native=mapOf(id(111) to WorkNativeObservation(subject,id(111),id(112),"start","confirmed",listOf(source),execution,false,"bound")))
        publish(WorkCommand(id(114),project,CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,id(111),0,"confirmed",listOf(source),emptyList()))))
        return app
    }
}

class H1JointBindingTest {
    @Test fun capturedRuntimeFactsAreBoundedImmutableSnapshotsNotLiveAdapterCollections() {
        val f=JointFixture();val snapshots=mutableSetOf(f.snapshot);val native=linkedMapOf<java.util.UUID,WorkNativeObservation>()
        val sources=mutableListOf(f.source)
        native[id(990)]=WorkNativeObservation(WAssignmentSubject(f.assignment,0),id(990),id(991),"prepare","confirmed",sources,null,true,"reserved")
        val profiles=f.observations.qualifiedProfiles.toMutableSet()
        val captured=H1ObservedFacts(f.head,f.observations.facts.copy(retainedSnapshots=snapshots,native=native),profiles)
        snapshots.clear();native.clear();sources.clear();profiles.clear()
        assertEquals(setOf(f.snapshot),captured.facts.retainedSnapshots)
        assertEquals(listOf(f.source),captured.facts.native.getValue(id(990)).sources)
        assertEquals(f.observations.qualifiedProfiles,captured.qualifiedProfiles)
        val huge=(1..16385).map { WorkReference(WorkRecordIdentity(WorkFamily.RESULTS,WorkKey.Id(project,id(10000+it))),Digest.parse("0".repeat(64))) }.toSet()
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { H1ObservedFacts(f.head,f.observations.facts,huge) }.problem)
    }
    @Test fun actualGroupedReviewerKeepsExactR1R2SubjectsCannotMutateProducerPlanOrAccept() {
        val f=JointFixture()
        f.publishedActiveApp().use { app ->
            var sequence=900
            fun publish(op: dev.writhelm.core.work.WorkOperation,who: String=plannerToken)=assertIs<CommandResult.Settled>(app.execute(who,f.codec.encodeCommand(WorkCommand(id(sequence++),project,op))))
            fun reference(result: CommandResult.Settled): WorkReference {
                val o=assertIs<LogicalOutcome.Success>(result.receipt.outcome);val r=H1Catalog.result(o.operation,o.result);val family=H1Catalog.family(r.family)
                return WorkReference(WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(r.key))),digest(o.result.fields("key","generation","digest").getValue("digest")))
            }
            val worker=token(f.workerKey);val producerSubject=WAssignmentSubject(f.assignment,0)
            val producerPlan=WorkKey.Revision(project,id(930),0)
            publish(CUpdatePlanOperation(CUpdatePlan(producerPlan,f.task,null,emptyList(),"producer-owned tracker",null,emptyList(),producerSubject)),worker)
            fun result(n: Int,artifact: WSnapshot): WorkReference {
                val retro=reference(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(n)),CRecordRetrospectiveData(f.task,producerSubject,null,"no-significant-new-findings",emptyList()),emptyList())),worker))
                val report=Utf8Content.fromText("immutable result $n from an attributed fixture snapshot")
                val body=WorkBodyReference(report.digest,report.sizeBytes)
                f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+artifact,submittedDiffs=f.runtimeFacts.submittedDiffs+WorkSubmittedDiff(f.task,listOf(artifact),body))
                return reference(publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(n+1)),CSubmitResultData(f.task,producerSubject,f.manifest,listOf(artifact),body,listOf(id(96)),emptyList(),retro,emptyList()),listOf(CSubmitResultBodiesItem(report.digest,report.text())))),worker))
            }
            val r1=result(940,f.snapshot)
            val r2Snapshot=f.snapshot.copy(tree=WorkGitOid("sha1","9".repeat(40)),retainedLocatorId=id(952))
            val r2=result(950,r2Snapshot)
            val task=f.task.copy(taskId=id(960));val plan=f.plan.copy(id=id(961));val manifestKey=WorkKey.Id(project,id(962))
            val oldTerms=f.base.work.getValue(project).task(f.task)
            val oldPlan=(f.base.work.getValue(project).record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value
            val terms=oldTerms.copy(kind="verification",verificationPlan=plan,dependencies=listOf(
                WDependency(f.task,"implemented",r1,emptyList(),"assess immutable R1"),WDependency(f.task,"implemented",r2,emptyList(),"assess immutable R2")))
            publish(CCreateTaskRevisionOperation(CCreateTaskRevision(task,terms,CCreateTaskRevisionNewVerificationPlan(plan,oldPlan.copy(task=task)),emptyList())))
            val input=WInputManifests(task,requireNotNull(terms.base),f.applicability,listOf(WManifestItem(f.source,true,"review group input",emptyList())),f.source,emptyList())
            val manifest=reference(publish(CPinInputManifestOperation(CPinInputManifest(manifestKey,input,emptyList()))))
            f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=f.runtimeFacts.verifiedPackets+manifest)
            val assignment=id(963);val prepare=id(964);val correlation=id(965);val delegation=id(966);val reviewer=id(967);val key=id(968);val workspace=id(969)
            val intended=f.intended.copy(executionId=id(970),template="reviewer-v1",nativeSessionId="ses_group_review_fixture")
            val resources=listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(project,id(971)),null))
            publish(CReserveAssignmentOperation(CReserveAssignment(assignment,task,manifest,f.profile,workspace,resources,listOf(r1,r2),prepare,correlation,emptyList(),intended)))
            val binding=DelegationBinding(f.controller,project,reviewer,key,task.taskId,assignment,intended.executionId,0,DelegationTemplate.REVIEWER,listOf(r1,r2))
            val registration=Auth3Payload.RegisterDelegation(delegation,binding,0,CredentialVerifier.digest(storeId,project,reviewer,key,secret(key.leastSignificantBits.toInt())))
            val reordered=registration.copy(binding=DelegationBinding(f.controller,project,reviewer,key,task.taskId,assignment,intended.executionId,0,DelegationTemplate.REVIEWER,listOf(r2,r1)))
            val refused=assertIs<CommandResult.Settled>(app.execute(plannerToken,Auth3CommandCodec().encode(Auth3Command(id(972),project,reordered))))
            assertEquals("AUTH_TEMPLATE_CONFLICT",assertIs<LogicalOutcome.Rejected>(refused.receipt.outcome).code);assertTrue(refused.receipt.writes.isEmpty())
            assertIs<CommandResult.Settled>(app.execute(plannerToken,Auth3CommandCodec().encode(Auth3Command(id(973),project,registration))))
            val subject=WAssignmentSubject(assignment,0);val execution=WExecution(intended.executionId,reviewer,key,intended.nativeHostId,intended.nativeSessionId,workspace)
            f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(prepare to WorkNativeObservation(subject,prepare,correlation,"prepare","confirmed",listOf(f.source),execution,true,"reserved")))
            publish(CBindAssignmentOperation(CBindAssignment(assignment,1,prepare,0,intended.nativeHostId,intended.nativeSessionId,workspace,listOf(f.source),emptyList())))
            publish(CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,id(974),id(975),"start",null,emptyList())))
            f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(id(974) to WorkNativeObservation(subject,id(974),id(975),"start","confirmed",listOf(f.source),execution,false,"bound")))
            publish(CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,id(974),0,"confirmed",listOf(f.source),emptyList())))
            val reviewerToken=token(key);val before=app.inspectAccess(plannerToken).basis
            val mutate=WorkCommand(id(976),project,CUpdatePlanOperation(CUpdatePlan(producerPlan,task,0,emptyList(),"reviewer cannot adopt producer plan",null,emptyList(),subject)))
            assertIs<CommandResult.Denied>(app.execute(reviewerToken,f.codec.encodeCommand(mutate)));assertEquals(before,app.inspectAccess(plannerToken).basis)
            val text=Utf8Content.fromText("exact ordered R1/R2 group report; no acceptance implied")
            val body=WorkBodyReference(text.digest,text.sizeBytes)
            val report=reference(publish(CSubmitVerificationReportOperation(CSubmitVerificationReport(WorkKey.Id(project,id(977)),CSubmitVerificationReportData(task,subject,listOf(r1,r2),plan,listOf(id(97)),f.source,listOf(f.source,WSourceVariant1Value(WSourceVariant1("artifact",r2Snapshot))),emptyList(),emptyList(),body,listOf("Native/fresh-context/artifact qualifications are attributed fixtures, not an actual model/host pass")),listOf(CSubmitVerificationReportBodiesItem(text.digest,text.text())))),reviewerToken))
            val observed=app.read2(reviewerToken,f.codec.encodeRead(WorkRead.Exact(project,r1.identity,r1.digest,null))) as JObject
            assertEquals("results",observed.entries.getValue("data").fields("formatVersion","family","key","payload").getValue("family").string())
            val invalid=WorkReference(WorkRecordIdentity(WorkFamily.EVIDENCE_APPLICABILITY,WorkKey.Id(project,id(978))),Digest.parse("0".repeat(64)))
            val accept=WorkCommand(id(979),project,CAcceptResultOperation(CAcceptResult(WorkKey.Id(project,id(980)),CAcceptResultData(r1,f.task,f.plan,emptyList(),listOf(report),emptyList(),invalid,listOf(id(96)),emptyList(),body),emptyList())))
            val current=app.inspectAccess(plannerToken).basis
            assertIs<CommandResult.Denied>(app.execute(reviewerToken,f.codec.encodeCommand(accept)));assertEquals(current,app.inspectAccess(plannerToken).basis)
            // Producer is still active and retains its claims while immutable R1/R2 are reviewed.
            assertEquals(f.worker,app.inspectAccess(worker).principalId)
        }
    }
    @Test fun realAtomicRecoveryRevokesScopeWithoutFreeingIsolatedClaimsOrEnablingHeldReplacement() {
        val f=JointFixture()
        f.publishedActiveApp().use { app ->
            val subject=WAssignmentSubject(f.assignment,0);var sequence=800
            fun publish(op: dev.writhelm.core.work.WorkOperation,who: String=token(f.workerKey))=
                assertIs<CommandResult.Settled>(app.execute(who,f.codec.encodeCommand(WorkCommand(id(sequence++),project,op))))
            fun ref(result: CommandResult.Settled): WorkReference {
                val outcome=assertIs<LogicalOutcome.Success>(result.receipt.outcome);val record=H1Catalog.result(outcome.operation,outcome.result)
                val family=H1Catalog.family(record.family)
                return WorkReference(WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(record.key))),digest(outcome.result.fields("key","generation","digest").getValue("digest")))
            }
            val digest=Digest.of(byteArrayOf(8));val cut=id(810);val locator=id(811);val report=Utf8Content.fromText("unfinished; physical completion not asserted")
            val body=WorkBodyReference(report.digest,report.sizeBytes)
            f.runtimeFacts=f.runtimeFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.intended.nativeHostId,f.intended.nativeSessionId,digest,digest,listOf("recovery-fixture" to digest))))
            publish(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(project,cut),WSourceCuts(f.task,subject,f.intended.nativeHostId,f.intended.nativeSessionId,f.profile,1,digest,digest,locator,listOf(WSourceCutsNativeMessagesItem("recovery-fixture",digest)),emptyList(),emptyList()),emptyList())))
            val plan=WorkKey.Revision(project,id(812),0);val view=WorkKey.Revision(project,id(813),0);val memory=WorkKey.Revision(project,id(814),0)
            publish(CUpdatePlanOperation(CUpdatePlan(plan,f.task,null,emptyList(),"unfinished tracker",null,emptyList(),subject)))
            publish(CRecordLoadedViewOperation(CRecordLoadedView(view,CRecordLoadedViewData(f.task,f.manifest,subject,null,emptyList(),emptyList()),emptyList())))
            publish(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(f.task,subject,null,cut,emptyList(),emptyList(),emptyList()),emptyList())))
            val checkpoint=ref(publish(CRecordCheckpointOperation(CRecordCheckpoint(WorkKey.Id(project,id(815)),CRecordCheckpointData(f.task,subject,f.manifest,view,cut,plan,0,memory,emptyList(),emptyList(),emptyList(),body,"unfinished",null,null,null),4,listOf(CRecordCheckpointBodiesItem(report.digest,report.text()))))))
            val execution=WExecution(f.intended.executionId,f.worker,f.workerKey,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace)
            publish(CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,5,id(816),id(817),"pause",null,emptyList())),plannerToken)
            f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(id(816) to WorkNativeObservation(subject,id(816),id(817),"pause","unknown",listOf(f.source),execution,false,"active")),isolatedAssignments=setOf(subject))
            publish(CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,6,id(816),0,"unknown",listOf(f.source),emptyList())),plannerToken)
            val result=publish(CRecordRecoveryOperation(CRecordRecovery(WorkKey.Id(project,id(818)),7,WRecoveries(subject,"revoked-isolated",listOf(f.source),listOf(f.resource),emptyList(),listOf("Background completion is unknown")),emptyList())),plannerToken)
            val recovery=ref(result)
            assertEquals(setOf("recoveries","assignments","claims","delegations"),result.receipt.writes.map { it.record.family }.toSet())
            assertIs<CommandResult.Denied>(app.execute(token(f.workerKey),f.codec.encodeCommand(WorkCommand(id(819),project,CUpdatePlanOperation(CUpdatePlan(plan,f.task,0,emptyList(),"cannot revive revoked scope",null,emptyList(),subject))))))
            val read=f.codec.encodeRead(WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.CLAIMS,f.resource),null,1))
            val claim=(app.read2(plannerToken,read) as JObject).entries.getValue("data").fields("formatVersion","family","key","payload").getValue("payload") as JObject
            assertEquals("isolated",claim.entries.getValue("state").string())
            val rebind=CRebindAssignment(f.assignment,8,f.task,f.manifest,f.profile,checkpoint,recovery,id(820),id(821),emptyList(),f.intended.copy(executionId=id(822),nativeSessionId="ses_replacement_fixture"))
            val held=publish(CRebindAssignmentOperation(rebind),plannerToken)
            assertEquals("NATIVE_UNKNOWN",assertIs<LogicalOutcome.Rejected>(held.receipt.outcome).code)
            assertTrue(held.receipt.writes.isEmpty())
        }
    }
    @Test fun realH1ReplyOwnershipSurvivesAnotherCallerControlLaneAndFailedDelivery()=runBlocking {
        val f=JointFixture();val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val admission=HttpAdmission();val held=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val hold=java.util.concurrent.atomic.AtomicBoolean(true);val failDelivery=java.util.concurrent.atomic.AtomicBoolean(false)
            val port=java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort };val origin="http://127.0.0.1:$port"
            val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),beforeDelivery={ event ->
                if(!event.control && event.status==200) {
                    if(failDelivery.compareAndSet(true,false))throw java.io.IOException("synthetic pre-delivery failure, not rollback")
                    if(hold.compareAndSet(true,false)) { held.complete(Unit);release.await() }
                }
            },admission=admission)
            val engine=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
            val client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
            fun command(n: Int)=CommandCodec().encode(CoreCommand(id(n),project,CommandPayload.Propose(revision(n+1)))).decodeToString()
            fun post(body: String,key: String)=client.send(HttpRequest.newBuilder(URI(origin+"/api/v1/commands")).timeout(java.time.Duration.ofSeconds(20))
                .header("Authorization","Bearer $key").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
            try {
                val first=async(Dispatchers.IO) { post(command(750),plannerToken) }
                withTimeout(20000) { held.await() }
                assertEquals(1,admission.snapshot().total);assertEquals(0,admission.snapshot().stateful)
                val second=post(command(752),ownerToken)
                assertEquals(200,second.statusCode(),second.body())
                val ownerReply=StrictJson.decode(second.body().toByteArray()) as JObject
                assertEquals(ownerKey,uuid(ownerReply.entries.getValue("authenticatedByCredentialId")))
                val control=client.send(HttpRequest.newBuilder(URI(origin+"/mcp")).header("Authorization","Bearer $plannerToken").GET().build(),HttpResponse.BodyHandlers.ofString())
                assertEquals(405,control.statusCode())
                release.complete(Unit)
                val response=first.await();assertEquals(200,response.statusCode(),response.body())
                assertEquals(plannerKey,uuid((StrictJson.decode(response.body().toByteArray()) as JObject).entries.getValue("authenticatedByCredentialId")))
                withTimeout(3000) { while(admission.snapshot().total!=0)delay(1) }
                assertEquals(0,admission.snapshot().bytes)
                failDelivery.set(true)
                val lost=post(command(754),plannerToken)
                assertEquals(503,lost.statusCode());assertTrue(lost.body().contains("PUBLICATION_UNKNOWN"))
                val recovered=post(command(754),plannerToken)
                assertEquals(200,recovered.statusCode(),recovered.body());assertTrue(recovered.body().contains("\"commandReplayed\":true"))
                withTimeout(3000) { while(admission.snapshot().total!=0)delay(1) }
                assertEquals(0,admission.snapshot().bytes);assertFalse(admission.snapshot().fenced)
            } finally { release.complete(Unit);adapter.mcp.close();engine.stop(0,5000);client.close() }
        }
    }
    @Test fun additionalQualifiedSameBaseReadGrowsLoadedViewWithoutPromotingAProposalOrChangingBase() {
        val f=JointFixture()
        f.publishedActiveApp().use { app ->
            val worker=token(f.workerKey);val r=revision(90)
            assertFailsWith<AppAccessFault> { app.readClause(worker,r.y()) }
            val key=WorkKey.Revision(project,id(700),0)
            val loaded=WorkCommand(id(701),project,CRecordLoadedViewOperation(CRecordLoadedView(key,CRecordLoadedViewData(f.task,f.manifest,WAssignmentSubject(f.assignment,0),null,
                listOf(WLoadedItem(WSourceVariant0Value(WSourceVariant0("knowledge",r.y())),"qualified same-base contextual discovery",listOf(id(96)))),emptyList()),emptyList())))
            fun publish(c: WorkCommand)=assertIs<CommandResult.Settled>(app.execute(worker,f.codec.encodeCommand(c)))
            assertIs<LogicalOutcome.Rejected>(publish(loaded).receipt.outcome)
            f.runtimeFacts=f.runtimeFacts.copy(knowledgeSources=setOf(r.y())) // trusted fixture scope qualification, not a body actor/grant
            assertTrue(publish(loaded).commandReplayed)
            assertIs<LogicalOutcome.Success>(publish(loaded.copy(requestKey=id(702))).receipt.outcome)
            assertEquals("Y0\n",app.readClause(worker,r.y()).located.content.text())
            val full=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(project),"operation" to str("read_revision"),"subject" to obj("revisionKey" to revisionKey(r.key),"revisionDigest" to digest(r.digest))))
            assertEquals(r.content.text(),(app.read1(worker,full) as JObject).entries.getValue("data").fields("revisionKey","revisionDigest","bodyDigest","structure","body","proposedBy").getValue("body").string())
            val read=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(project),"operation" to str("read_clause"),"subject" to reference(r.y())))
            val data=(app.read1(worker,read) as JObject).entries.getValue("data") as JObject
            assertEquals("UNAPPROVED_INPUT",data.entries.getValue("observation").string())
            assertEquals(JNull,data.entries.getValue("trace"))
            val assignment=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,f.assignment)),null,4)
            val row=(app.read2(plannerToken,f.codec.encodeRead(assignment)) as JObject).entries.getValue("data").fields("formatVersion","family","key","payload")
            assertContentEquals(identityCodec.encodeReference(f.manifest),StrictJson.encode((row.getValue("payload") as JObject).entries.getValue("manifest")))
            val assessment=ConsistencyAssessment(id(703),project,listOf(r.y(),r.contextReference()),"Withdraw this exact discovered source",emptyList(),listOf(r.y()))
            assertIs<CommandResult.Settled>(app.execute(ownerToken,CommandCodec().encode(CoreCommand(id(704),project,CommandPayload.Assess(assessment)))))
            assertFailsWith<AppAccessFault> { app.readClause(worker,r.y()) }
            assertFailsWith<AppAccessFault> { app.read1(worker,full) }
            assertEquals("X0\n",app.readClause(worker,r.x()).located.content.text())
        }
    }
    @Test fun approvedScopedPlanRulingRestoresHistoricallyButWithdrawnRulingCannotAuthorizeNewUse() {
        val f=JointFixture();val dir=directory()
        val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) })
        val text=Utf8Content.fromText("For task ${f.task.taskId}, permit gate ${id(97)} to move from acceptance to milestone; exact owner ruling.")
        val ruling=DocumentRevision(RevisionKey(DocumentKey(project,id(710)),id(711)),text,StructureIndex(listOf(ClauseSpan(ClauseId("R"),ByteSpan(0,text.sizeBytes))),emptyList()))
        val reference=ruling.clauseReference(ClauseId("R"));val key=WorkKey.Revision(project,id(712),0)
        val root=(f.base.work.getValue(project).record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value
        val data=root.copy(previous=f.plan,gates=root.gates.map { it.copy(stage="milestone") },reason="exact scoped owner decision",authorizedChanges=listOf(reference))
        val change=WorkCommand(id(713),project,CReviseVerificationPlanOperation(CReviseVerificationPlan(key,data,emptyList())))
        H1Application.initialize(dir,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            fun core(p: CommandPayload,n: Int)=assertIs<CommandResult.Settled>(app.execute(ownerToken,CommandCodec().encode(CoreCommand(id(n),project,p))))
            core(CommandPayload.Propose(ruling),714)
            val packet=packet(listOf(ReviewItem(id(715),reference)),emptyList())
            core(CommandPayload.Request(id(716),packet),717)
            val read=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(project),"operation" to str("read_request"),"subject" to obj("id" to uuid(id(716)))))
            val request=(app.read1(ownerToken,read) as JObject).entries.getValue("data").fields("record").getValue("record").fields("formatVersion","family","key","payload").getValue("payload") as JObject
            core(CommandPayload.Resolve(id(716),digest(request.entries.getValue("requestDigest")),packet.digest,StructuredAnswer.Approve),718)
            val first=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(change)))
            assertIs<LogicalOutcome.Rejected>(first.receipt.outcome) // project approval alone is not exact per-command applicability
            f.runtimeFacts=f.runtimeFacts.copy(approvedDecisions=setOf(reference))
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(change))).commandReplayed)
            val accepted=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(change.copy(requestKey=id(719)))))
            assertIs<LogicalOutcome.Success>(accepted.receipt.outcome)
            core(CommandPayload.Assess(ConsistencyAssessment(id(720),project,listOf(reference),"Withdraw current use; preserve historical legitimacy",emptyList(),listOf(reference))),721)
        }
        H1Application.open(dir,storeId,options).use { app ->
            val method=data.copy(previous=key,reason="method-only child",authorizedChanges=emptyList())
            val revised=WorkCommand(id(722),project,CReviseVerificationPlanOperation(CReviseVerificationPlan(key.copy(id=id(723)),method,emptyList())))
            val result=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(revised)))
            assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(result.receipt.outcome).code)
            // The old attributable plan remains present/readable; no restore reset or today's READ
            // requirement is applied as a historical corruption test.
            val old=app.read2(plannerToken,StrictJson.encode(obj("formatVersion" to num(2),"projectId" to uuid(project),"operation" to str("read_verification_plans"),
                "subject" to obj("key" to StrictJson.decode(identityCodec.encodeKey(key)),"digest" to digest(f.codec.digest(WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,key),WVerificationPlansPayload(data))))))))
            assertEquals("read_verification_plans",(old as JObject).entries.getValue("operation").string())
        }
    }
    @Test fun actualAppLostPublicationAcknowledgmentRevalidatesOriginalEdgeWithoutAnotherCas() {
        val f=JointFixture();var calls=0
        val driver=PublicationDriver { git,h,c ->
            calls++;val result=git.update(h,c);assertEquals(0,result.exit);assertEquals(c,git.head())
            GitReply(1,byteArrayOf(),byteArrayOf(1)) // actual published C; response deliberately lost
        }
        val dir=directory();val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.observations.facts,f.observations.qualifiedProfiles) },AppOptions(admission=AdmissionPolicy(formatVersion=2),publication=driver))
        val bytes=CommandCodec().encode(CoreCommand(id(650),project,CommandPayload.Propose(revision(651))))
        H1Application.initialize(dir,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val result=assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes))
            assertTrue(result.commandReplayed);assertEquals(1,calls)
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes)).commandReplayed)
            assertEquals(1,calls)
        }
        H1Application.open(dir,storeId,H1Options(options.runtime)).use { app ->
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes)).commandReplayed)
        }
    }
    @Test fun actualAppUnknownAfterPublishedCandidateKeepsBarriersOnFinalAndNonfinalAttempts() {
        for(attempts in listOf(1,3)) {
            val f=JointFixture();val dir=directory();var failAudit=false;var calls=0;var published=false
            val driver=PublicationDriver { git,h,c ->
                calls++;assertEquals(0,git.update(h,c).exit);published=git.head()==c;failAudit=true
                GitReply(1,byteArrayOf(),byteArrayOf(1))
            }
            val o=AppOptions(admission=AdmissionPolicy(formatVersion=2),storeBounds=StoreBounds(attempts=attempts),publication=driver,
                fileRead={ if(failAudit)throw StoreFault(StoreProblem.UNAVAILABLE) })
            val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.observations.facts,f.observations.qualifiedProfiles) },o)
            val bytes=CommandCodec().encode(CoreCommand(id(652),project,CommandPayload.Propose(revision(653))))
            H1Application.initialize(dir,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
                assertIs<CommandResult.Unknown>(app.execute(plannerToken,bytes));assertTrue(published);assertEquals(1,calls)
                assertIs<CommandResult.Unknown>(app.execute(plannerToken,bytes)) // still unqualified, never definite rollback/absence
                assertEquals(1,calls)
            }
            assertTrue(java.nio.file.Files.exists(dir.resolve("publication-in-flight")))
            assertTrue(java.nio.file.Files.exists(dir.resolve("runtime-active")))
            assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { H1Application.open(dir,storeId,H1Options(options.runtime)) }.problem)
        }
    }
    @Test fun ownControllerContinuationNavigationAndMcpSessionsBindActualFullScope()=runBlocking {
        val f=JointFixture();val secondController=id(600);val secondKey=id(601);var time=0L
        val extra=listOf(Auth2Record.Controller(ControllerBinding(secondController,project,planner)),
            Auth2Record.Key(ScopedCredential(credential(secondKey,planner),CredentialScope.Controller(secondController))))
        val sourceDigest=Digest.of(byteArrayOf(7));val locator=id(602);val nativeSession="ses_controller_fixture"
        val observed=f.observations.facts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.intended.nativeHostId,nativeSession,sourceDigest,sourceDigest,listOf("message-controller" to sourceDigest))))
        val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,observed,f.observations.qualifiedProfiles) },navigationClock={ time })
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records()+extra,f.base.work),options).use { app ->
            val source=WNativeSourceBasis(f.intended.nativeHostId,nativeSession,1,sourceDigest,sourceDigest,locator,listOf(WNativeSourceBasisNativeMessagesItem("message-controller",sourceDigest)),emptyList())
            val bodyControllers=mutableSetOf<java.util.UUID>()
            fun continuation(controller: java.util.UUID,n: Int): WorkCommand {
                // Different controller scopes do not acquire another scope's private body merely
                // by knowing its digest. Keep this navigation/session test's reports independently owned.
                val report=Utf8Content.fromText("own controller $controller frontier; no latest branch is selected")
                val data=WControllerContinuations(f.profile,null,source,listOf(WControllerContinuationsFrontierItem(f.task,emptyList(),listOf(id(96)))),emptyList(),emptyList(),emptyList(),WorkBodyReference(report.digest,report.sizeBytes))
                val bodies=if(controller in bodyControllers)emptyList() else listOf(CRecordControllerContinuationBodiesItem(report.digest,report.text()))
                return WorkCommand(id(n+1000),project,CRecordControllerContinuationOperation(CRecordControllerContinuation(WorkKey.ControllerContinuation(project,controller,id(n)),data,bodies)))
            }
            fun publish(controller: java.util.UUID,n: Int,key: String) {
                assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(key,f.codec.encodeCommand(continuation(controller,n)))).receipt.outcome);bodyControllers.add(controller)
            }
            val taskPlanOnly=Auth3Command(id(603),project,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),setOf(WorkPermission.TASK_PLAN)))
            assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(taskPlanOnly)))
            publish(f.controller,604,plannerToken);publish(f.controller,605,plannerToken);publish(secondController,606,token(secondKey))
            val before=app.inspectAccess(plannerToken).basis
            assertIs<CommandResult.Denied>(app.execute(plannerToken,f.codec.encodeCommand(continuation(secondController,607))))
            assertEquals(before,app.inspectAccess(plannerToken).basis)
            fun listing(cursor: java.util.UUID?=null)=f.codec.encodeRead(WorkRead.Listing(project,RListWorkRecords("controller-continuations",f.task.taskId,cursor,1)))
            val first=app.read2(plannerToken,listing()) as JObject
            val data=first.entries.getValue("data").fields("snapshotId","nextCursor","complete","items")
            val cursor=uuid(data.getValue("nextCursor"));val row=(data.getValue("items").list().single() as JObject).entries
            assertEquals(f.controller,uuid(row.getValue("key").fields("projectId","controllerId","id").getValue("controllerId")))
            assertEquals(AppAccessProblem.FORBIDDEN,assertFailsWith<AppAccessFault> { app.read2(token(secondKey),listing(cursor)) }.problem)
            publish(f.controller,608,plannerToken)
            val next=app.read2(plannerToken,listing(cursor)) as JObject
            assertContentEquals(StrictJson.encode(first.entries.getValue("basis")),StrictJson.encode(next.entries.getValue("basis")))
            assertEquals(JBoolean(true),next.entries.getValue("data").fields("snapshotId","nextCursor","complete","items").getValue("complete"))
            time=300001
            assertEquals("SNAPSHOT_EXPIRED",assertFailsWith<H1ReadFault> { app.read2(plannerToken,listing(cursor)) }.code)
            val fresh=(app.read2(plannerToken,listing()) as JObject).entries.getValue("data").fields("snapshotId","nextCursor","complete","items")
            val freshCursor=uuid(fresh.getValue("nextCursor"))
            val mcp=McpAdapter(WrithelmService(app))
            try {
                val init=mcp.post(plannerToken,null,null,"""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"synthetic-scope","version":"1"}}}""".toByteArray())
                val session=assertNotNull(init.sessionId)
                mcp.post(plannerToken,session,"2025-11-25","""{"jsonrpc":"2.0","method":"notifications/initialized"}""".toByteArray())
                try { mcp.post(token(secondKey),session,"2025-11-25","""{"jsonrpc":"2.0","id":2,"method":"ping"}""".toByteArray());fail("Other immutable controller scope reused protocol session") }
                catch(e: ServiceFault) { assertEquals(403,e.status) }
                val noRead=setOf(Capability.PLAN,Capability.PROPOSE)
                val command=AuthCommand(id(609),project,AuthPayload.ReplaceGrants(planner,0,grantDigest(project,planner,setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE)),noRead))
                assertIs<CommandResult.Settled>(app.execute(ownerToken,AuthCommandCodec().encode(command)))
                assertEquals(AppAccessProblem.FORBIDDEN,assertFailsWith<AppAccessFault> { app.read2(plannerToken,listing(freshCursor)) }.problem)
            } finally { mcp.close() }
        }
    }
    @Test fun realCompletedGenerationReconcilesOnlyOriginalKeysAndReadsOnlyIntrinsicOwnOutcome() {
        val f=JointFixture();var captures=0;var publications=0
        val driver=PublicationDriver { git,h,c -> publications++;git.update(h,c) }
        f.publishedActiveApp({ captures++ },driver).use { app ->
            var sequence=160;val subject=WAssignmentSubject(f.assignment,0);val workerToken=token(f.workerKey)
            fun command(op: dev.writhelm.core.work.WorkOperation)=WorkCommand(id(sequence++),project,op)
            fun publish(c: WorkCommand,who: String=workerToken): CommandResult.Settled = assertIs<CommandResult.Settled>(app.execute(who,f.codec.encodeCommand(c)))
            fun ref(result: CommandResult.Settled): WorkReference {
                val outcome=assertIs<LogicalOutcome.Success>(result.receipt.outcome)
                val selector=H1Catalog.result(outcome.operation,outcome.result)
                val family=H1Catalog.family(selector.family)
                return WorkReference(WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(selector.key))),digest(outcome.result.fields("key","generation","digest").getValue("digest")))
            }
            val body=Utf8Content.fromText("exact retained finished result; native completion is an attributed test observation")
            val report=WorkBodyReference(body.digest,body.sizeBytes)
            val retro0=command(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(170)),CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())))
            val retrospective=ref(publish(retro0))
            val rejection=retro0.copy(requestKey=id(171))
            assertIs<LogicalOutcome.Rejected>(publish(rejection).receipt.outcome)
            f.runtimeFacts=f.runtimeFacts.copy(submittedDiffs=setOf(WorkSubmittedDiff(f.task,listOf(f.snapshot),report)))
            val resultCommand=command(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(172)),CSubmitResultData(f.task,subject,f.manifest,listOf(f.snapshot),report,listOf(id(96)),emptyList(),retrospective,emptyList()),listOf(CSubmitResultBodiesItem(body.digest,body.text())))))
            val outcome=ref(publish(resultCommand))
            val digest=Digest.of(byteArrayOf(1));val cut=id(173);val locator=id(174)
            f.runtimeFacts=f.runtimeFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.intended.nativeHostId,f.intended.nativeSessionId,digest,digest,listOf("fixture-message" to digest))))
            publish(command(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(project,cut),WSourceCuts(f.task,subject,f.intended.nativeHostId,f.intended.nativeSessionId,f.profile,1,digest,digest,locator,listOf(WSourceCutsNativeMessagesItem("fixture-message",digest)),emptyList(),emptyList()),emptyList()))))
            val plan=WorkKey.Revision(project,id(175),0);val view=WorkKey.Revision(project,id(176),0);val memory=WorkKey.Revision(project,id(177),0)
            publish(command(CUpdatePlanOperation(CUpdatePlan(plan,f.task,null,emptyList(),"own final tracker",null,emptyList(),subject))))
            publish(command(CRecordLoadedViewOperation(CRecordLoadedView(view,CRecordLoadedViewData(f.task,f.manifest,subject,null,emptyList(),emptyList()),emptyList()))))
            publish(command(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(f.task,subject,null,cut,emptyList(),emptyList(),emptyList()),emptyList()))))
            val retro1=ref(publish(command(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(178)),CRecordRetrospectiveData(f.task,subject,outcome,"no-significant-new-findings",emptyList()),emptyList())))))
            val delivery=WorkKey.Id(project,id(179))
            publish(command(CRecordDeliveryOperation(CRecordDelivery(delivery,subject,cut,f.task,report,null,"durable-delivered",listOf(f.source),emptyList()))),plannerToken)
            val checkpoint=ref(publish(command(CRecordCheckpointOperation(CRecordCheckpoint(WorkKey.Id(project,id(180)),
                CRecordCheckpointData(f.task,subject,f.manifest,view,cut,plan,0,memory,emptyList(),emptyList(),emptyList(),report,"finished",retro1,outcome,delivery),4,emptyList())))))
            f.runtimeFacts=f.runtimeFacts.copy(completedAssignments=setOf(subject))
            assertIs<LogicalOutcome.Success>(publish(command(CCompleteAssignmentOperation(CCompleteAssignment(f.assignment,5,checkpoint,listOf(f.source),listOf(f.resource),emptyList()))),plannerToken).receipt.outcome)
            val before=app.inspectAccess(plannerToken).basis;val calls=captures;val cas=publications
            assertTrue(publish(resultCommand).commandReplayed)
            assertTrue(publish(rejection).commandReplayed)
            assertIs<CommandResult.Denied>(app.execute(workerToken,f.codec.encodeCommand(resultCommand.copy(requestKey=id(181)))))
            val altered=resultCommand.copy(operation=CSubmitResultOperation((resultCommand.operation as CSubmitResultOperation).value.copy(data=(resultCommand.operation as CSubmitResultOperation).value.data.copy(claimedCriteria=listOf(id(96),id(999))))))
            assertIs<CommandResult.IdentityConflict>(app.execute(workerToken,f.codec.encodeCommand(altered)))
            assertEquals(before,app.inspectAccess(plannerToken).basis);assertEquals(calls,captures);assertEquals(cas,publications)
            val selector=WorkRead.Exact(project,outcome.identity,outcome.digest,null)
            assertEquals(2L,(app.read2(workerToken,f.codec.encodeRead(selector)) as JObject).entries.getValue("formatVersion").long())
            val retained=app.read2(workerToken,f.codec.encodeRead(WorkRead.Body(project,body.digest,selector))) as JObject
            assertEquals(body.text(),retained.entries.getValue("data").fields("digest","byteLength","body").getValue("body").string())
            val task=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.TASK_REVISIONS,f.task),f.base.work.getValue(project).reference(WorkFamily.TASK_REVISIONS,f.task).digest,null)
            assertFailsWith<AppAccessFault> { app.read2(workerToken,f.codec.encodeRead(task)) }
            val remove=CommandCodec().encode(CoreCommand(id(182),project,CommandPayload.Revoke(f.worker)))
            assertIs<CommandResult.Settled>(app.execute(ownerToken,remove))
            assertTrue(publish(resultCommand).commandReplayed)
            assertFailsWith<AppAccessFault> { app.read2(workerToken,f.codec.encodeRead(selector)) }
            assertIs<CommandResult.Settled>(app.execute(ownerToken,Auth3CommandCodec().encode(Auth3Command(id(183),project,Auth3Payload.RevokeDelegation(f.delegation,0)))))
            assertIs<CommandResult.Denied>(app.execute(workerToken,f.codec.encodeCommand(resultCommand)))
        }
    }
    @Test fun realH1HttpMcpScopedReadsWorkAdmissionAndNoLegacyToolEscalation()=runBlocking {
        val f=JointFixture()
        f.publishedActiveApp().use { app ->
            val port=java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val origin="http://127.0.0.1:$port";val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin))
            val engine=embeddedServer(CIO,host="127.0.0.1",port=port) { adapter.install(this) }.start(wait=false)
            val client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
            fun call(path: String,bytes: String,token: String=token(f.workerKey),headers: Map<String,String> = emptyMap()): HttpResponse<String> {
                val b=HttpRequest.newBuilder(URI(origin+path)).timeout(java.time.Duration.ofSeconds(20)).header("Authorization","Bearer $token").header("Content-Type","application/json")
                headers.forEach { (k,v)->b.header(k,v) }
                return client.send(b.POST(HttpRequest.BodyPublishers.ofString(bytes)).build(),HttpResponse.BodyHandlers.ofString())
            }
            fun legacyRead(op: String,subject: JsonValue)=StrictJson.encode(obj("formatVersion" to num(1),"projectId" to uuid(project),"operation" to str(op),"subject" to subject)).decodeToString()
            try {
                val r=revision(90)
                val removeDispatch=Auth3CommandCodec().encode(Auth3Command(id(139),project,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptySet()))).decodeToString()
                assertEquals(200,call("/api/v1/commands",removeDispatch,ownerToken).statusCode())
                val clause=legacyRead("read_clause",reference(r.x()))
                assertEquals(200,call("/api/v1/reads",clause).statusCode())
                val neighbor=call("/api/v1/reads",legacyRead("read_clause",reference(r.y())))
                assertEquals(403,neighbor.statusCode());assertFalse(neighbor.body().contains("Y0"))
                assertEquals(403,call("/api/v1/reads",legacyRead("read_revision",obj("revisionKey" to revisionKey(r.key),"revisionDigest" to digest(r.digest)))).statusCode())
                val init=call("/mcp","""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"synthetic-h1","version":"1"}}}""",headers=mapOf("Accept" to "application/json, text/event-stream"))
                assertEquals(200,init.statusCode(),init.body())
                val session=init.headers().firstValue("MCP-Session-Id").orElseThrow()
                val headers=mapOf("Accept" to "application/json, text/event-stream","MCP-Session-Id" to session,"MCP-Protocol-Version" to "2025-11-25")
                assertEquals(202,call("/mcp","""{"jsonrpc":"2.0","method":"notifications/initialized"}""",headers=headers).statusCode())
                val tools=call("/mcp","""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",headers=headers)
                assertTrue(tools.body().contains("writhelm_read"));assertFalse(tools.body().contains("writhelm_command"))
                val work=WorkCommand(id(140),project,CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(141)),CRecordRetrospectiveData(f.task,WAssignmentSubject(f.assignment,0),null,"no-significant-new-findings",emptyList()),emptyList())))
                val command=f.codec.encodeCommand(work).decodeToString()
                val before=app.inspectAccess(plannerToken).basis
                val denied=call("/mcp","""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"writhelm_command","arguments":{"command":$command}}}""",headers=headers)
                assertTrue(denied.body().contains("FORBIDDEN"),denied.body());assertEquals(before,app.inspectAccess(plannerToken).basis)
                val selector=f.codec.encodeRead(WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.TASK_REVISIONS,f.task),f.base.work.getValue(project).reference(WorkFamily.TASK_REVISIONS,f.task).digest,null)).decodeToString()
                val read2=call("/mcp","""{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"writhelm_read","arguments":{"read":$selector}}}""",headers=headers)
                assertTrue(read2.body().contains("FORBIDDEN"),read2.body())
                val accepted=call("/api/v1/commands",command)
                assertEquals(200,accepted.statusCode(),accepted.body())
                assertTrue(call("/api/v1/commands",command).body().contains("\"commandReplayed\":true"))
                assertEquals(200,call("/api/v1/reads",selector).statusCode())
                val unchanged=app.inspectAccess(plannerToken).basis
                val malformed=command.replace("\"formatVersion\":3","\"formatVersion\":3.0")
                assertEquals(400,call("/api/v1/commands",malformed).statusCode());assertEquals(unchanged,app.inspectAccess(plannerToken).basis)
                assertEquals(403,call("/api/v1/reads",clause,headers=mapOf("Origin" to "null")).statusCode())
                val revoke=CommandCodec().encode(CoreCommand(id(142),project,CommandPayload.Revoke(f.worker))).decodeToString()
                assertEquals(200,call("/api/v1/commands",revoke,ownerToken).statusCode())
                val current=call("/mcp","""{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"writhelm_read","arguments":{"read":$clause}}}""",headers=headers)
                assertTrue(current.body().contains("FORBIDDEN"),current.body())
            } finally { adapter.mcp.close();engine.stop(0,5000);client.close() }
        }
    }
    @Test fun actualPersistentPublisherRegistersReplaysLegacyCommandsAndReopensOriginalBoundaries() {
        val f=JointFixture();val dir=directory()
        val options=H1Options(H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.observations.facts,f.observations.qualifiedProfiles) })
        val registration=Auth3CommandCodec().encode(f.register());val reserve=f.codec.encodeCommand(f.reserveCommand())
        H1Application.initialize(dir,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            assertIs<CredentialScope.Controller>(app.inspectAccess(plannerToken).scope)
            val first=assertIs<CommandResult.Settled>(app.execute(plannerToken,reserve))
            assertEquals("assignments",first.receipt.writes.single { it.record.family=="assignments" }.record.family)
            val enrolled=assertIs<CommandResult.Settled>(app.execute(plannerToken,registration))
            assertEquals(7,enrolled.receipt.writes.size);assertEquals(3,enrolled.receipt.journalVersion)
            val replay=assertIs<CommandResult.Settled>(app.execute(plannerToken,registration))
            assertTrue(replay.commandReplayed);assertNull(replay.publication);assertEquals(enrolled.receipt.fingerprint,replay.receipt.fingerprint)
            val duplicate=Auth3CommandCodec().encode(f.register().copy(requestKey=id(130)))
            assertEquals("AUTH_DUPLICATE_ID",(assertIs<CommandResult.Settled>(app.execute(plannerToken,duplicate)).receipt.outcome as LogicalOutcome.Rejected).code)
            val proposed=revision(131)
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,CommandCodec().encode(CoreCommand(id(132),project,CommandPayload.Propose(proposed))))).receipt.outcome)
            val selector=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,f.assignment)),null,1)
            val read=app.read2(plannerToken,f.codec.encodeRead(selector))
            assertEquals(f.delegation,uuid(((read as JObject).entries.getValue("data") as JObject).entries.getValue("payload").fields("task","manifest","profile","generation","executionGeneration","execution","binding","workspaceId","requiredResources","reviewSubjects","state","currentIntent","checkpoint","recovery","intendedExecution","delegationId").getValue("delegationId")))
        }
        assertEquals(StoreProblem.UNSUPPORTED,assertFailsWith<StoreFault> { MechanismApp.open(dir,storeId) }.problem)
        H1Application.open(dir,storeId,options).use { reopened ->
            assertTrue(assertIs<CommandResult.Settled>(reopened.execute(plannerToken,registration)).commandReplayed)
            assertEquals("X0\n",reopened.readClause(plannerToken,revision(90).x()).located.content.text())
        }
    }
    @Test fun actualPersistentPreflightRefusesBeforeRuntimeOrAnyPublication() {
        val f=JointFixture();var evaluated=0
        val options=H1Options(H1RuntimeFacts { state,_ -> evaluated++;H1ObservedFacts(state.basis,f.observations.facts,f.observations.qualifiedProfiles) },commandReplyBytes=1024)
        H1Application.initialize(directory(),storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val before=app.inspectAccess(plannerToken).basis
            assertIs<CommandResult.Refused>(app.execute(plannerToken,f.codec.encodeCommand(f.reserveCommand())))
            assertEquals(0,evaluated);assertEquals(before,app.inspectAccess(plannerToken).basis)
        }
    }
    @Test fun originalParentCandidateBoundaryUsesActualGitBytesAndRejectsForeignCredentialAttribution() {
        val f=JointFixture();f.reserve()
        // Model-free Linux Git storage test, NOT a substitute publisher/route or native profile.
        RuntimeOwner.acquire(directory()).use { ownerRuntime ->
            val git=NativeGit(ownerRuntime);git.initialize()
            fun stored(files: Map<String,ByteArray>,parent: GitOid?): StoreSnapshot {
                val entries=files.mapValues { git.blob(it.value) }
                class Node { val dirs=sortedMapOf<String,Node>();val files=sortedMapOf<String,GitOid>() }
                val root=Node()
                entries.forEach { (path,oid) ->
                    var node=root;val parts=path.split('/')
                    parts.dropLast(1).forEach { node=node.dirs.getOrPut(it) { Node() } };node.files[parts.last()]=oid
                }
                fun tree(node: Node): GitOid {
                    val lines=node.files.map { (name,oid) -> name to "100644 blob ${oid.hex}\t$name\u0000" }+
                        node.dirs.map { (name,child) -> "$name/" to "040000 tree ${tree(child).hex}\t$name\u0000" }
                    return git.oid(git.call(listOf("mktree","-z"),lines.sortedBy { it.first }.joinToString("") { it.second }.toByteArray()).checked())
                }
                return StoreSnapshot(git.commit(tree(root),parent),entries,git)
            }
            val files=linkedMapOf<String,ByteArray>()
            CoreSnapshotCodec().export(f.base.core) { files[it.path]=it.bytes() }
            Auth2SnapshotCodec().export(f.base.authority).forEach { (path,bytes) -> files[path]=bytes }
            files["work/format.json"]=StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))
            f.base.work.values.forEach { work ->
                work.records.values.forEach { files[identityCodec.recordPath(it.identity)]=f.codec.encodeRecord(it,true) }
                work.bodies.forEach { (digest,body) -> files["work/bodies/${digest.hex.take(2)}/${digest.hex}.txt"]=body.bytes() }
            }
            val parent=stored(files,null);val cache=H1SnapshotCache(storeId);val before=cache.load(parent)
            val command=f.register();val candidate=f.key(plannerKey) { f.bridge.auth3(before,command,it) }
            val writes=candidate.evaluation.changes.sortedBy { it.key.path }.map { change ->
                WriteDescriptor(change.key,files[change.key.path]?.let(::sha),change.bytes()?.let(::sha))
            }
            val receipt=Receipt(storeId,dev.writhelm.core.store.CommandIdentity(planner,project,command.payload.operation,command.requestKey),3,
                Auth3CommandCodec().fingerprint(command).hex,parent.oid,plannerKey,candidate.evaluation.outcome,writes,3)
            val afterFiles=LinkedHashMap(files)
            candidate.evaluation.changes.forEach { afterFiles[it.key.path]=requireNotNull(it.bytes()) }
            afterFiles[receipt.path]=JournalCodec.encode(receipt)
            val after=stored(afterFiles,parent.oid)
            H1BoundaryValidator(cache).validate(parent,after,receipt,candidate)
            // Clear all derived state: original H/C are independently available to cold audit.
            H1BoundaryValidator(H1SnapshotCache(storeId)).validate(parent,after,receipt)
            val forged=Receipt(storeId,dev.writhelm.core.store.CommandIdentity(owner,project,command.payload.operation,command.requestKey),3,
                receipt.fingerprint,parent.oid,ownerKey,receipt.outcome,writes,3)
            val forgedFiles=LinkedHashMap(afterFiles);forgedFiles.remove(receipt.path);forgedFiles[forged.path]=JournalCodec.encode(forged)
            val foreign=stored(forgedFiles,parent.oid)
            assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> { H1BoundaryValidator(H1SnapshotCache(storeId)).validate(parent,foreign,forged) }.problem)
        }
    }
    @Test fun actualPinnedClauseReadAndWorkSubjectDoNotOpenNeighborOrOtherSelectors() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations();val reads=H1ProtectedReads()
        val revision=f.base.core.snapshot().revisions.single()
        f.key(f.workerKey) { key ->
            assertEquals("X0\n",reads.readClause(f.base,key,revision.x()).located.content.text())
            val denied=assertFailsWith<AppAccessFault> { reads.readClause(f.base,key,revision.y()) }
            assertEquals(AppAccessProblem.FORBIDDEN,denied.problem)
            val work=f.base.work.getValue(project);val task=work.reference(WorkFamily.TASK_REVISIONS,f.task)
            assertEquals(task.identity,reads.readWork(f.base,key,WorkRead.Exact(project,task.identity,task.digest,null)).identity)
            val foreign=WorkRead.Exact(project,WorkRecordIdentity(WorkFamily.TASK_REVISIONS,f.task.copy(taskId=id(999))),task.digest,null)
            assertEquals(AppAccessProblem.FORBIDDEN,assertFailsWith<AppAccessFault> { reads.readWork(f.base,key,foreign) }.problem)
        }
        f.install(f.key(ownerKey) { f.bridge.auth3(f.base,Auth3Command(id(125),project,Auth3Payload.RevokeDelegation(f.delegation,0)),it) })
        f.key(f.workerKey) { key -> assertFailsWith<AppAccessFault> { reads.readClause(f.base,key,revision.x()) } }
    }
    @Test fun passiveBindStartAndWorkerBodyUseActualAuthorityAndReducersWithoutIssuerCascade() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations()
        assertEquals("active",f.base.work.getValue(project).assignment(f.assignment).state)
        val facts=f.base.projection.assignments.values.single()
        assertEquals(f.worker,assertNotNull(facts.boundExecution).principalId)
        assertEquals(f.workerKey,facts.boundExecution!!.credentialId)
        val remove=Auth3Command(id(120),project,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),emptyList()))
        f.install(f.key(ownerKey) { f.bridge.auth3(f.base,remove,it) })
        val body=Utf8Content.fromText("actual portable component binding; native observations are fixtures\r\n")
        val report=WorkBodyReference(body.digest,body.sizeBytes)
        val data=CRecordEvidenceData(listOf(f.source),emptyList(),"bounded actual JVM check","component",report,id(121),"passed",emptyList(),emptyList(),emptyList(),report)
        val command=WorkCommand(id(122),project,CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(123)),data,listOf(CRecordEvidenceBodiesItem(body.digest,body.text())))))
        val candidate=f.key(f.workerKey) { f.bridge.work(f.base,command,it,f.observations) }
        assertIs<LogicalOutcome.Success>(candidate.evaluation.outcome)
        assertEquals(setOf("evidence","bodies"),candidate.evaluation.changes.map { it.key.family }.toSet())
        f.install(candidate)
        assertEquals(body,f.base.work.getValue(project).bodies[body.digest])
        val revoke=Auth3Command(id(124),project,Auth3Payload.RevokeDelegation(f.delegation,0))
        f.install(f.key(ownerKey) { f.bridge.auth3(f.base,revoke,it) })
        assertEquals(StoreProblem.DENIED,assertFailsWith<StoreFault> { f.key(f.workerKey) { f.bridge.work(f.base,command,it,f.observations) } }.problem)
        assertEquals("held",(f.base.work.getValue(project).record(WorkFamily.CLAIMS,f.resource).payload as WClaimsPayload).value.state)
    }
    @Test fun actualReservationProjectionAndSevenEffectRegistration() {
        val f=JointFixture();f.reserve();val before=f.base
        val assignment=before.work.getValue(project).assignment(f.assignment)
        assertNull(assignment.delegationId);assertNull(assignment.execution)
        val facts=before.projection.assignments.values.single()
        assertEquals(assignment.task.taskId,facts.taskId);assertEquals(assignment.intendedExecution.nativeSessionId,facts.nativeSessionId)
        assertEquals(AssignmentPhase.RESERVED,facts.phase);assertNull(facts.boundExecution)
        val candidate=f.key(plannerKey) { f.bridge.auth3(before,f.register(),it) }
        assertEquals(7,candidate.evaluation.changes.size)
        assertEquals(setOf("core/grants","auth/principals","auth/grant-guards","auth/work-grants","auth/credentials","auth/delegations","work/assignments"),
            candidate.evaluation.changes.map { "${it.key.namespace}/${it.key.family}" }.toSet())
        assertNull(before.work.getValue(project).assignment(f.assignment).delegationId)
        assertFalse(f.worker in before.authority.principals)
        f.install(candidate)
        assertEquals(f.delegation,f.base.work.getValue(project).assignment(f.assignment).delegationId)
        assertEquals(1,f.base.work.getValue(project).assignment(f.assignment).generation)
        assertTrue(f.worker in f.base.authority.principals)
        assertEquals(setOf(Capability.READ),f.base.authority.policy.snapshotGrants().single { it.principalId==f.worker }.capabilities)
        assertTrue(f.base.authority.workGrants.getValue(Enrollment(f.worker,project)).permissions.isEmpty())
        f.key(f.workerKey) { assertNull(Auth2Policy(f.base.authority,f.base.projection).sessionBinding(it,project)) }
    }
    @Test fun wrongControllerStaleGuardAndDuplicateIdentityCannotPartiallyEnroll() {
        val f=JointFixture();f.reserve();val before=f.base
        assertEquals(StoreProblem.DENIED,assertFailsWith<StoreFault> { f.key(ownerKey) { f.bridge.auth3(before,f.register(),it) } }.problem)
        val stale=f.register().copy(payload=(f.register().payload as Auth3Payload.RegisterDelegation).copy(expectedAssignmentGeneration=1))
        val rejected=f.key(plannerKey) { f.bridge.auth3(before,stale,it) }
        assertEquals("AUTH_STALE_ASSIGNMENT",(rejected.evaluation.outcome as LogicalOutcome.Rejected).code)
        assertTrue(rejected.evaluation.changes.isEmpty());assertSame(before.authority,rejected.authority)
        f.install(f.key(plannerKey) { f.bridge.auth3(before,f.register(),it) })
        val duplicate=f.key(plannerKey) { f.bridge.auth3(f.base,f.register(),it) }
        assertEquals("AUTH_DUPLICATE_ID",(duplicate.evaluation.outcome as LogicalOutcome.Rejected).code)
        assertTrue(duplicate.evaluation.changes.isEmpty())
    }
    @Test fun actualCoreBaseManifestDigestAndSharedDomainBudgetFailClosed() {
        val f=JointFixture();val base=f.base;val state=base.work.getValue(project)
        val original=state.task(f.task)
        val badBase=original.base!!.copy(knowledgeManifestDigest=Digest.of(byteArrayOf(5)))
        val altered=WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,f.task),WTaskRevisionsPayload(original.copy(base=badBase)))
        val graph=WorkState.restore(project,state.records.values.map { record -> when(val p=record.payload) {
            is WTaskRevisionsPayload -> altered
            is WInputManifestsPayload -> WorkRecord(record.identity,WInputManifestsPayload(p.value.copy(base=badBase)))
            else -> record
        } },state.bodies.values)
        assertFailsWith<StoreFault> { H1BoundState.checked(storeId,f.head,base.core,base.authority.records(),mapOf(project to graph)) }
        assertEquals(StoreProblem.UNAVAILABLE,assertFailsWith<StoreFault> { H1BoundState.checked(storeId,f.head,base.core,base.authority.records(),base.work,AppLimits(entryBytes=base.entryBytes-1)) }.problem)
        assertEquals(base.entryBytes,H1BoundState.checked(storeId,f.head,base.core,base.authority.records(),base.work,AppLimits(entryBytes=base.entryBytes)).entryBytes)
    }
    @Test fun actualClosedCommandAdmissionPreservesComponentFingerprintsAndRejectsActor() {
        val f=JointFixture();val codec=Auth3CommandCodec();val command=f.register()
        val bytes=codec.encode(command)
        assertContentEquals(bytes,f.bridge.canonicalize(bytes))
        val injection=bytes.toString(Charsets.UTF_8).replace("\"payload\":{","\"payload\":{\"actor\":\"owner\",").toByteArray()
        assertFailsWith<CodecFault> { f.bridge.canonicalize(injection) }
        val work=WorkCommand(id(105),project,CSetTaskHeadOperation(CSetTaskHead(WorkKey.Task(project,f.task.taskId),null,"title",null,null,emptyList())))
        val wb=f.codec.encodeCommand(work)
        assertContentEquals(wb,f.bridge.canonicalize(wb))
        assertEquals(f.codec.fingerprint(work),f.codec.fingerprint(f.codec.decodeCommand(f.bridge.canonicalize(wb))))
    }
}
