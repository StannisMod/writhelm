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
internal class JointFixture(val snapshot:WSnapshot=WSnapshot(id(80),WorkGitOid("sha1","2".repeat(40)),WorkGitOid("sha1","3".repeat(40)),null,id(81))) {
    val head=GitOid("sha1","1".repeat(40));val controller=id(60);val assignment=id(61);val delegation=id(62)
    val worker=id(63);val workerKey=id(64);val workspace=id(65);val resource=WorkKey.Resource(project,id(66))
    val intent=id(67);val correlation=id(68);val task=WorkKey.TaskRevision(project,id(69),0)
    val profile=WorkKey.Profile(project,id(70),0);val plan=WorkKey.Revision(project,id(71),0)
    val applicability=WorkKey.Revision(project,id(72),0);val manifestKey=WorkKey.Id(project,id(73))
    val intended=WExecutionIntent(controller,id(74),"executor-v1",id(75),"ses_joint_fixture")
    val bridge=H1JointReduction();val codec=WorkCodec();var base: H1BoundState
    val source=WSourceVariant1Value(WSourceVariant1("artifact",snapshot))
    val manifest: WorkReference
    val observations: H1ObservedFacts
    lateinit var runtimeFacts: WorkExternalFacts
    var capturedRuntimeState:H1BoundState?=null
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
    fun publishedActiveApp(onCapture: ()->Unit={},publication: PublicationDriver=PublicationDriver.Native,modelVersion:Int=2,root:java.nio.file.Path=directory(),diffControl:NativeResultDiffProofControl?=null): H1Application {
        val baseFacts=H1RuntimeFacts { state,_ -> capturedRuntimeState=state;onCapture();H1ObservedFacts(state.basis,runtimeFacts,observations.qualifiedProfiles) }
        val options=H1Options(if(diffControl==null)baseFacts else NativeResultDiffFacts(baseFacts,diffControl),AppOptions(admission=AdmissionPolicy(formatVersion=2),publication=publication),workModelVersion=modelVersion)
        val work=if(modelVersion==2)base.work else base.work.mapValues { (_,w)->WorkState.restore(w.projectId,w.records.values,w.bodies.values,modelVersion=modelVersion) }
        val app=H1Application.initialize(root,storeId,H1Genesis(base.core,base.authority.records(),work),options)
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
    @Test fun `worker report needs matching observed facts and planner reads exact retained result`() {
        val f=JointFixture();f.publishedActiveApp().use { app ->
            val service=WrithelmService(app);val worker=token(f.workerKey);val subject=WAssignmentSubject(f.assignment,0)
            val retro=WorkCommand(id(6100),project,CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(6101)),
                CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())))
            val retroReply=service.command(worker,f.codec.encodeCommand(retro))
            assertEquals(200,retroReply.status)
            val retroOutcome=(retroReply.value as JObject).entries.getValue("outcome") as JObject
            val retroData=retroOutcome.entries.getValue("result").fields("key","generation","digest")
            val retrospective=WorkReference(WorkRecordIdentity(WorkFamily.RETROSPECTIVES,
                identityCodec.decodeKey(WorkFamily.RETROSPECTIVES,StrictJson.encode(retroData.getValue("key")))),digest(retroData.getValue("digest")))
            val text=Utf8Content.fromText("synthetic worker claim, not an independently verified diff")
            val body=WorkBodyReference(text.digest,text.sizeBytes)
            val result=WorkCommand(id(6102),project,CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(6103)),
                CSubmitResultData(f.task,subject,f.manifest,listOf(f.snapshot),body,listOf(id(96)),emptyList(),retrospective,emptyList()),
                listOf(CSubmitResultBodiesItem(text.digest,text.text())))))
            val rejected=service.command(worker,f.codec.encodeCommand(result))
            assertEquals(409,rejected.status)
            assertEquals("INVALID_SUBJECT",((rejected.value as JObject).entries.getValue("outcome") as JObject).entries.getValue("code").string())
            assertFalse(WorkRecordIdentity(WorkFamily.RESULTS,WorkKey.Id(project,id(6103))) in app.coordinationCapture(plannerToken,project).work.records)
            // Explicit synthetic trusted observation; this is not an installed Git/diff observer.
            f.runtimeFacts=f.runtimeFacts.copy(submittedDiffs=setOf(WorkSubmittedDiff(f.task,listOf(f.snapshot),body)))
            val replay=service.command(worker,f.codec.encodeCommand(result))
            assertEquals(409,replay.status);assertEquals(JBoolean(true),(replay.value as JObject).entries.getValue("commandReplayed"))
            val accepted=service.command(worker,f.codec.encodeCommand(result.copy(requestKey=id(6104))))
            assertEquals(200,accepted.status)
            val outcome=((accepted.value as JObject).entries.getValue("outcome") as JObject).entries.getValue("result").fields("key","generation","digest")
            val reference=WorkReference(WorkRecordIdentity(WorkFamily.RESULTS,identityCodec.decodeKey(WorkFamily.RESULTS,StrictJson.encode(outcome.getValue("key")))),digest(outcome.getValue("digest")))
            val selector=WorkRead.Exact(project,reference.identity,reference.digest,null)
            val read=service.read(plannerToken,f.codec.encodeRead(selector));assertEquals(200,read.status)
            val report=service.read(plannerToken,f.codec.encodeRead(WorkRead.Body(project,text.digest,selector)))
            assertEquals(200,report.status)
            assertEquals(text.text(),((report.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string())
            val state=app.coordinationCapture(plannerToken,project).work
            assertTrue(state.records.values.none { it.payload is WAcceptancesPayload || it.payload is WIntegrationsPayload })
        }
    }
    @Test fun `concurrent independent reservations and conflicting claims survive cold Git reopen`() {
        val f=JointFixture();val original=f.base.work.getValue(project);val codec=WorkCodec()
        val baseTask=original.task(f.task)
        val basePlan=(original.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value
        val baseManifest=(original.resolve(f.manifest).payload as WInputManifestsPayload).value
        fun row(key:WorkKey,payload:WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(payload),key),payload)
        val second=f.task.copy(taskId=id(5901));val third=f.task.copy(taskId=id(5902))
        val p2=f.plan.copy(id=id(5903));val p3=f.plan.copy(id=id(5904))
        val m2=WorkKey.Id(project,id(5905));val m3=WorkKey.Id(project,id(5906))
        val rows=listOf(row(second,WTaskRevisionsPayload(baseTask.copy(verificationPlan=p2))),row(third,WTaskRevisionsPayload(baseTask.copy(verificationPlan=p3))),
            row(p2,WVerificationPlansPayload(basePlan.copy(task=second))),row(p3,WVerificationPlansPayload(basePlan.copy(task=third))),
            row(m2,WInputManifestsPayload(baseManifest.copy(task=second))),row(m3,WInputManifestsPayload(baseManifest.copy(task=third))))
        val work=WorkState.restore(project,original.records.values+rows,original.bodies.values,modelVersion=4)
        val manifests=listOf(f.manifest,work.reference(WorkFamily.INPUT_MANIFESTS,m2),work.reference(WorkFamily.INPUT_MANIFESTS,m3))
        f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=manifests.toSet())
        val options=H1Options(H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) },workModelVersion=4)
        val root=directory();val shared=f.resource;val independent=WorkKey.Resource(project,id(5907))
        fun reserve(index:Int,task:WorkKey.TaskRevision,manifest:WorkReference,resource:WorkKey.Resource):WorkCommand {
            val n=5910+index*10
            return WorkCommand(id(n),project,CReserveAssignmentOperation(CReserveAssignment(id(n+1),task,manifest,f.profile,id(n+2),
                listOf(CReserveAssignmentResourcesItem(resource,null)),emptyList(),id(n+3),id(n+4),emptyList(),
                f.intended.copy(executionId=id(n+5),nativeSessionId="ses_parallel_fixture_$index"))))
        }
        val commands=listOf(reserve(0,f.task,manifests[0],shared),reserve(1,second,manifests[1],shared),reserve(2,third,manifests[2],independent))
        H1Application.initialize(root,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to work)),options).use { app ->
            val pool=java.util.concurrent.Executors.newFixedThreadPool(3)
            try {
                val gate=java.util.concurrent.CountDownLatch(1)
                val pending=commands.map { command ->pool.submit<CommandResult> { gate.await();app.execute(plannerToken,codec.encodeCommand(command)) } }
                gate.countDown()
                val outcomes=pending.map { assertIs<CommandResult.Settled>(it.get(60,java.util.concurrent.TimeUnit.SECONDS)) }
                assertEquals(1,outcomes.take(2).count { it.receipt.outcome is LogicalOutcome.Success })
                assertEquals(1,outcomes.take(2).count { (it.receipt.outcome as? LogicalOutcome.Rejected)?.code=="STALE_GENERATION" })
                assertIs<LogicalOutcome.Success>(outcomes[2].receipt.outcome)
                val loser=outcomes.take(2).indexOfFirst { it.receipt.outcome is LogicalOutcome.Rejected }
                val previous=commands[loser];val payload=(previous.operation as CReserveAssignmentOperation).value
                val fresh=previous.copy(requestKey=id(5990),operation=CReserveAssignmentOperation(payload.copy(
                    resources=listOf(CReserveAssignmentResourcesItem(shared,0)))))
                val conflict=assertIs<CommandResult.Settled>(app.execute(plannerToken,codec.encodeCommand(fresh)))
                assertEquals("CLAIM_CONFLICT",assertIs<LogicalOutcome.Rejected>(conflict.receipt.outcome).code)
                assertTrue(conflict.receipt.writes.isEmpty())
                assertEquals(2,app.coordinationCapture(plannerToken,project).work.records.values.count { it.payload is WAssignmentsPayload })
                outcomes.forEachIndexed { i,receipt ->
                    val replay=assertIs<CommandResult.Settled>(app.execute(plannerToken,codec.encodeCommand(commands[i])))
                    assertTrue(replay.commandReplayed);assertEquals(receipt.receipt.fingerprint,replay.receipt.fingerprint)
                }
            } finally { pool.shutdownNow() }
        }
        H1Application.open(root,storeId,options).use { app ->
            assertEquals(2,app.coordinationCapture(plannerToken,project).work.records.values.count { it.payload is WAssignmentsPayload })
            commands.forEach { assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,codec.encodeCommand(it))).commandReplayed) }
        }
    }
    @Test fun `materialization publishes four records atomically and cold audits original head`() {
        val f=JointFixture();val root=directory();var seq=5600;lateinit var heldClosureBytes:ByteArray
        f.publishedActiveApp(modelVersion=4,root=root).use { app ->
            fun publish(op:dev.writhelm.core.work.WorkOperation,token:String=plannerToken):CommandResult.Settled {
                val reply=app.execute(token,f.codec.encodeCommand(WorkCommand(id(seq++),project,op)))
                if(reply is CommandResult.Refused)error("refused ${reply.problem}")
                val settled=assertIs<CommandResult.Settled>(reply);assertIs<LogicalOutcome.Success>(settled.receipt.outcome);return settled
            }
            fun ref(receipt:CommandResult.Settled):WorkReference {
                val o=receipt.receipt.outcome as LogicalOutcome.Success;val key=H1Catalog.result(o.operation,o.result)
                return WorkReference(WorkRecordIdentity(H1Catalog.family(key.family),identityCodec.decodeKey(H1Catalog.family(key.family),StrictJson.encode(key.key))),
                    digest(o.result.fields("key","generation","digest").getValue("digest")))
            }
            val subject=WAssignmentSubject(f.assignment,0);val worker=token(f.workerKey)
            val retro=ref(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(5650)),
                CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())),worker))
            val text=Utf8Content.fromText("synthetic producer result");val body=WorkBodyReference(text.digest,text.sizeBytes)
            f.runtimeFacts=f.runtimeFacts.copy(submittedDiffs=f.runtimeFacts.submittedDiffs+WorkSubmittedDiff(f.task,listOf(f.snapshot),body))
            val result=ref(publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(5651)),
                CSubmitResultData(f.task,subject,f.manifest,listOf(f.snapshot),body,listOf(id(96)),emptyList(),retro,emptyList()),
                listOf(CSubmitResultBodiesItem(text.digest,text.text())))),worker))
            val task=f.task.copy(taskId=id(5652));val verification=f.plan.copy(id=id(5653));val manifest=WorkKey.Id(project,id(5654))
            val template=f.base.work.getValue(project);val old=template.task(f.task)
            val oldPlan=(template.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value
            val terms=old.copy(verificationPlan=verification,dependencies=listOf(WDependency(f.task,"implemented",null,emptyList(),"exact future result")))
            publish(CCreateTaskRevisionOperation(CCreateTaskRevision(task,terms,CCreateTaskRevisionNewVerificationPlan(verification,oldPlan.copy(task=task)),emptyList())))
            val original=(template.resolve(f.manifest).payload as WInputManifestsPayload).value
            val manifestRef=ref(publish(CPinInputManifestOperation(CPinInputManifest(manifest,original.copy(task=task),emptyList()))))
            val plan=DispatchPlanCandidate(project,id(5655),f.controller,listOf(DispatchPlanEntry(task,manifestRef,f.profile,listOf(f.resource),id(5656),id(5657),listOf(DispatchFutureDependency(f.task,"implemented")))))
            val planReply=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchPlanCommandCodec().encode(DispatchPlanCommand(id(5658),plan))))
            val planRef=ref(planReply)
            val command=DispatchMaterializationCommand(id(5659),planRef,task,task.copy(revision=1),verification.copy(id=id(5660)),WorkKey.Id(project,id(5661)))
            val bytes=DispatchMaterializationCommandCodec().encode(command)
            assertIs<CommandResult.Denied>(app.execute(ownerToken,bytes))
            val outcome=app.execute(plannerToken,bytes)
            if(outcome is CommandResult.Refused)error("materialization refused ${outcome.problem}")
            val receipt=assertIs<CommandResult.Settled>(outcome)
            assertIs<LogicalOutcome.Success>(receipt.receipt.outcome)
            assertEquals(setOf("dispatch-materializations","task-revisions","verification-plans","input-manifests"),receipt.receipt.writes.map { it.record.family }.toSet())
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes)).commandReplayed)
            val state=app.coordinationCapture(plannerToken,project).work
            val materializationRef=state.reference(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(project,command.requestKey))
            val closure=DispatchClosureCommand(id(5674),materializationRef,WorkGitOid(f.head.algorithm,f.head.hex),f.source,WorkKey.Id(project,id(5675)))
            f.activateWithAttributedFixtureObservations()
            val bound=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to state),expectedWorkModel=4)
            f.key(plannerKey) { assertNotNull(f.bridge.authorizeDispatchClosure(bound,closure,it)) }
            f.key(ownerKey) { assertNull(f.bridge.authorizeDispatchClosure(bound,closure,it)) }
            var qualificationCalls=0
            val qualification=ClosureAssessmentQualification { _,_,_,_->qualificationCalls++;null }
            assertNull(verifiedDispatchClosureAssessment(bound,closure,f.runtimeFacts,planner,qualification))
            val unretained=closure.copy(assessment=WSourceVariant2Value(WSourceVariant2("work",f.manifest)))
            assertNull(verifiedDispatchClosureAssessment(bound,unretained,f.runtimeFacts,planner,qualification))
            assertNull(verifiedDispatchClosureAssessment(bound,closure.copy(expectedHead=WorkGitOid("sha1","f".repeat(40))),f.runtimeFacts,planner,qualification))
            assertEquals(0,qualificationCalls) // never ask a provider to bless missing attribution or a stale H
            val observed=H1ObservedFacts(bound.basis,f.runtimeFacts,emptySet())
            f.key(plannerKey) { key ->
                assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
                    f.bridge.dispatchClosure(bound,closure,key,observed,qualification)
                }.problem)
                assertEquals(WorkProblem.STALE_GENERATION,assertFailsWith<WorkFault> {
                    f.bridge.dispatchClosure(bound,closure,key,H1ObservedFacts(GitOid("sha1","f".repeat(40)),f.runtimeFacts,emptySet()),qualification)
                }.problem)
            }
            f.key(ownerKey) { key -> assertFailsWith<StoreFault> { f.bridge.dispatchClosure(bound,closure,key,observed,qualification) } }
            // A synthetic graph candidate is not enough for historical authority: an artifact
            // assertion has no receipt-derived independent evidence attribution at original H.
            val inventory=prebindingMaterializedInputs(state,command.requestKey,f.runtimeFacts)
            val fakeSource=WSourceVariant1Value(WSourceVariant1("artifact",f.snapshot.copy(retainedLocatorId=id(5677))))
            val fakeCommand=closure.copy(assessment=fakeSource)
            val fakeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+fakeSource.value.artifact)
            val fakeProof=PrebindingClosureAssessment(fakeCommand.expectedHead,command.requestKey,inventory,fakeSource,f.worker,f.delegation)
            val effect=reduceDispatchClosure(state,fakeCommand.expectedHead,WorkExecutionInput(planner,plannerKey,f.controller,null,null),fakeFacts,fakeCommand,fakeProof)
            val fakeAfter=H1BoundState.checked(storeId,f.head,bound.core,bound.authority.records(),mapOf(project to effect.after),expectedWorkModel=4)
            assertFailsWith<StoreFault> { f.bridge.validateDispatchClosureHistory(bound,fakeAfter,effect.result.key as WorkKey.Id,
                planner,plannerKey,DispatchClosureCommandCodec().fingerprint(fakeCommand)) }
            f.bridge.validateDispatchClosureAuthority(bound,closure,planner,plannerKey)
            assertFailsWith<StoreFault> { f.bridge.validateDispatchClosureAuthority(bound,closure,owner,ownerKey) }
            assertFailsWith<StoreFault> { f.bridge.validateDispatchClosureAuthority(bound,closure,owner,plannerKey) }
            for(removed in listOf(WorkPermission.TASK_PLAN,WorkPermission.TASK_DISPATCH)) {
                val restricted=bound.authority.records().map { row ->
                    if(row is Auth2Record.WorkGrants && row.value.enrollment.principalId==planner)
                        Auth2Record.WorkGrants(WorkGrant(row.value.enrollment,row.value.generation,row.value.permissions-removed)) else row
                }
                val other=H1BoundState.checked(storeId,f.head,bound.core,restricted,bound.work,expectedWorkModel=4)
                f.key(plannerKey) { assertNull(f.bridge.authorizeDispatchClosure(other,closure,it)) }
                assertFailsWith<StoreFault> { f.bridge.validateDispatchClosureAuthority(other,closure,planner,plannerKey) }
            }
            f.key(plannerKey) { assertNull(f.bridge.authorizeDispatchClosure(bound,closure.copy(materialization=materializationRef.copy(digest=Digest.of(byteArrayOf(9)))),it)) }
            val capturedHead=app.coordinationCapture(plannerToken,project).basis
            val heldCommand=closure.copy(expectedHead=WorkGitOid(capturedHead.algorithm,capturedHead.hex))
            val heldBytes=DispatchClosureCommandCodec().encode(heldCommand)
            heldClosureBytes=heldBytes
            assertIs<CommandResult.Denied>(app.execute(ownerToken,heldBytes))
            val noAssessor=assertIs<CommandResult.Settled>(app.execute(plannerToken,heldBytes))
            assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(noAssessor.receipt.outcome).code)
            assertTrue(noAssessor.receipt.writes.isEmpty())
            val staleCommand=heldCommand.copy(requestKey=id(5676))
            val stale=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchClosureCommandCodec().encode(staleCommand)))
            assertEquals("STALE_GENERATION",assertIs<LogicalOutcome.Rejected>(stale.receipt.outcome).code)
            assertTrue(stale.receipt.writes.isEmpty())
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,heldBytes)).commandReplayed)
            assertEquals(state.records,app.coordinationCapture(plannerToken,project).work.records)
            val gapped=(state.record(WorkFamily.INPUT_MANIFESTS,command.manifest).payload as WInputManifestsPayload).value
            val bypass=WorkCommand(id(5678),project,CPinInputManifestOperation(CPinInputManifest(WorkKey.Id(project,id(5679)),
                gapped.copy(knownGaps=emptyList()),emptyList())))
            val rejectedBypass=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(bypass)))
            assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(rejectedBypass.receipt.outcome).code)
            assertTrue(rejectedBypass.receipt.writes.isEmpty())
            assertEquals(result,state.task(command.newTask).dependencies.single().selectedResult)
            assertTrue((state.record(WorkFamily.INPUT_MANIFESTS,command.manifest).payload as WInputManifestsPayload).value.knownGaps.isNotEmpty())
            val newManifest=state.reference(WorkFamily.INPUT_MANIFESTS,command.manifest)
            f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=f.runtimeFacts.verifiedPackets+newManifest)
            val existing=(f.reserveCommand().operation as CReserveAssignmentOperation).value
            val reserve=WorkCommand(id(5667),project,CReserveAssignmentOperation(existing.copy(assignmentId=id(5668),task=command.newTask,
                manifest=newManifest,workspaceId=id(5669),intentId=id(5670),correlationId=id(5671),
                resources=listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(project,id(5672)),null)),
                intendedExecution=existing.intendedExecution.copy(executionId=id(5673),nativeSessionId="ses_materialized_held"))))
            val gated=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(reserve)))
            assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(gated.receipt.outcome).code)
            assertTrue(gated.receipt.writes.isEmpty())

            // Separate, synthetic assessor assignment with actual service-issued identity and
            // receipt. The qualification callback below is deliberately a test double, not a
            // production assessor or proof of semantic completeness.
            val assessTask=f.task.copy(taskId=id(6000));val assessPlan=f.plan.copy(id=id(6001))
            val assessedTask=state.reference(WorkFamily.TASK_REVISIONS,command.newTask)
            fun workSource(reference:WorkReference)=WSourceVariant2Value(WSourceVariant2("work",reference))
            publish(CCreateTaskRevisionOperation(CCreateTaskRevision(assessTask,old.copy(verificationPlan=assessPlan),
                CCreateTaskRevisionNewVerificationPlan(assessPlan,oldPlan.copy(task=assessTask)),emptyList())))
            val assessmentItems=original.items+listOf(assessedTask,newManifest,result).map { WManifestItem(workSource(it),true,"assessment input",emptyList()) }
            val assessManifest=ref(publish(CPinInputManifestOperation(CPinInputManifest(WorkKey.Id(project,id(6002)),
                original.copy(task=assessTask,items=assessmentItems),emptyList()))))
            f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=f.runtimeFacts.verifiedPackets+assessManifest)
            val assessAssignment=id(6003);val assessDelegation=id(6004);val assessor=id(6005);val assessorKey=id(6006)
            val assessWorkspace=id(6007);val assessIntent=id(6008);val assessCorrelation=id(6009)
            val assessExecution=id(6010);val assessHost=id(6011);val assessSession="ses_closure_assessor"
            val intended=WExecutionIntent(f.controller,assessExecution,"executor-v1",assessHost,assessSession)
            publish(CReserveAssignmentOperation(existing.copy(assignmentId=assessAssignment,task=assessTask,manifest=assessManifest,
                workspaceId=assessWorkspace,intentId=assessIntent,correlationId=assessCorrelation,
                resources=listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(project,id(6012)),null)),intendedExecution=intended)))
            val registration=Auth3Command(id(6013),project,Auth3Payload.RegisterDelegation(assessDelegation,
                DelegationBinding(f.controller,project,assessor,assessorKey,assessTask.taskId,assessAssignment,assessExecution,0,
                    DelegationTemplate.EXECUTOR,emptyList()),0,
                CredentialVerifier.digest(storeId,project,assessor,assessorKey,secret(assessorKey.leastSignificantBits.toInt()))))
            val enrolled=assertIs<CommandResult.Settled>(app.execute(plannerToken,Auth3CommandCodec().encode(registration)))
            assertIs<LogicalOutcome.Success>(enrolled.receipt.outcome)
            val assessSubject=WAssignmentSubject(assessAssignment,0)
            val execution=WExecution(assessExecution,assessor,assessorKey,assessHost,assessSession,assessWorkspace)
            val prepare=WorkNativeObservation(assessSubject,assessIntent,assessCorrelation,"prepare","confirmed",listOf(f.source),execution,true,"reserved")
            f.runtimeFacts=f.runtimeFacts.copy(native=f.runtimeFacts.native+(assessIntent to prepare))
            publish(CBindAssignmentOperation(CBindAssignment(assessAssignment,1,assessIntent,0,assessHost,assessSession,assessWorkspace,listOf(f.source),emptyList())))
            val start=id(6014);val startCorrelation=id(6015)
            publish(CRequestNativeTransitionOperation(CRequestNativeTransition(assessAssignment,2,start,startCorrelation,"start",null,emptyList())))
            val started=WorkNativeObservation(assessSubject,start,startCorrelation,"start","confirmed",listOf(f.source),execution,false,"bound")
            f.runtimeFacts=f.runtimeFacts.copy(native=f.runtimeFacts.native+(start to started))
            publish(CObserveNativeTransitionOperation(CObserveNativeTransition(assessAssignment,3,start,0,"confirmed",listOf(f.source),emptyList())))
            val report=Utf8Content.fromText("synthetic closure assessment; not a qualified provider")
            val reportBody=WorkBodyReference(report.digest,report.sizeBytes)
            val assessmentEvidence=ref(publish(CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(6016)),
                CRecordEvidenceData(listOf(assessedTask,newManifest,result).map(::workSource),emptyList(),"synthetic closure fixture","component",
                    reportBody,id(6017),"passed",emptyList(),emptyList(),emptyList(),reportBody),
                listOf(CRecordEvidenceBodiesItem(report.digest,report.text())))),token(assessorKey)))
            val failedEvidence=ref(publish(CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(6024)),
                CRecordEvidenceData(listOf(assessedTask,newManifest,result).map(::workSource),emptyList(),"synthetic closure fixture","component",
                    reportBody,id(6025),"failed",emptyList(),emptyList(),emptyList(),reportBody),emptyList())),token(assessorKey)))
            // Capture the receipt-indexed H without relying on a caller-supplied attribution.
            app.execute(plannerToken,f.codec.encodeCommand(f.reserveCommand().copy(requestKey=id(6018))))
            val assessmentH=assertNotNull(f.capturedRuntimeState)
            val next=closure.copy(requestKey=id(6019),expectedHead=WorkGitOid(assessmentH.basis.algorithm,assessmentH.basis.hex),
                assessment=workSource(assessmentEvidence),manifest=WorkKey.Id(project,id(6020)))
            val double=ClosureAssessmentQualification { s,c,inputs,attribution ->
                QualifiedClosureAssessment(s.basis,c.materialization,inputs,assessmentEvidence,attribution.principalId,attribution.delegationId)
            }
            var failedProviderCalls=0
            val optimistic=ClosureAssessmentQualification { s,c,inputs,attribution ->
                failedProviderCalls++
                QualifiedClosureAssessment(s.basis,c.materialization,inputs,failedEvidence,attribution.principalId,attribution.delegationId)
            }
            f.key(plannerKey) { key -> assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
                f.bridge.dispatchClosure(assessmentH,next.copy(assessment=workSource(failedEvidence)),key,
                    H1ObservedFacts(assessmentH.basis,f.runtimeFacts,emptySet()),optimistic)
            }.problem) }
            assertEquals(0,failedProviderCalls)
            val mismatched=ClosureAssessmentQualification { s,c,inputs,attribution ->
                QualifiedClosureAssessment(s.basis,c.materialization,inputs,assessmentEvidence,attribution.principalId,id(6023))
            }
            f.key(plannerKey) { key -> assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
                f.bridge.dispatchClosure(assessmentH,next,key,H1ObservedFacts(assessmentH.basis,f.runtimeFacts,emptySet()),mismatched)
            }.problem) }
            val candidate=f.key(plannerKey) { key -> f.bridge.dispatchClosure(assessmentH,next,key,
                H1ObservedFacts(assessmentH.basis,f.runtimeFacts,emptySet()),double) }
            val closureRecord=candidate.after.records.getValue(candidate.result).payload as WDispatchClosurePayload
            assertEquals(assessor,closureRecord.assessor);assertEquals(assessDelegation,closureRecord.delegation)
            val exactInputs=prebindingMaterializedInputs(assessmentH.work.getValue(project),command.requestKey,f.runtimeFacts)
            assertFalse(closureAttributionMatches(assessmentH,project,exactInputs,planner,
                closureRecord.copy(value=next.copy(assessment=workSource(failedEvidence)))))
            val candidateH=H1BoundState.checked(storeId,assessmentH.basis,assessmentH.core,assessmentH.authority.records(),
                assessmentH.work+(project to candidate.after),expectedWorkModel=4)
            f.bridge.validateDispatchClosureHistory(assessmentH,candidateH,candidate.result.key as WorkKey.Id,planner,plannerKey,
                DispatchClosureCommandCodec().fingerprint(next))
            val originalClosure=candidate.after.records.getValue(candidate.result)
            val forgedClosure=originalClosure.copy(payload=closureRecord.copy(assessor=f.worker))
            val forgedWork=WorkState.restore(project,candidate.after.records.values.filter { it.identity!=candidate.result }+forgedClosure,
                candidate.after.bodies.values,modelVersion=4)
            val forgedH=H1BoundState.checked(storeId,assessmentH.basis,assessmentH.core,assessmentH.authority.records(),
                assessmentH.work+(project to forgedWork),expectedWorkModel=4)
            assertFailsWith<StoreFault> { f.bridge.validateDispatchClosureHistory(assessmentH,forgedH,candidate.result.key as WorkKey.Id,
                planner,plannerKey,DispatchClosureCommandCodec().fingerprint(next)) }
            val candidateManifest=candidate.after.reference(WorkFamily.INPUT_MANIFESTS,next.manifest)
            assertEquals(WorkProblem.BLOCKED_INPUT,assertFailsWith<WorkFault> {
                WorkTransaction(candidate.after,WorkExecutionInput(planner,plannerKey,f.controller,null,null),
                    f.runtimeFacts.copy(verifiedPackets=emptySet()),id(6021),Digest.of(byteArrayOf(1)))
                    .taskReady(command.newTask,candidateManifest,f.profile)
            }.problem)
            // The capture-triggering rejection itself advanced H, so a stale candidate cannot
            // publish; a fresh command still holds because no qualified provider is installed.
            val staleCandidate=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchClosureCommandCodec().encode(next)))
            assertEquals("STALE_GENERATION",assertIs<LogicalOutcome.Rejected>(staleCandidate.receipt.outcome).code)
            val currentHead=app.coordinationCapture(plannerToken,project).basis
            val fresh=next.copy(requestKey=id(6022),expectedHead=WorkGitOid(currentHead.algorithm,currentHead.hex))
            val serviceHeld=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchClosureCommandCodec().encode(fresh)))
            assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(serviceHeld.receipt.outcome).code)
            assertTrue(serviceHeld.receipt.writes.isEmpty())

            val secondRetro=ref(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(5662)),
                CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())),worker))
            publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(5663)),
                CSubmitResultData(f.task,subject,f.manifest,listOf(f.snapshot),body,listOf(id(96)),emptyList(),secondRetro,emptyList()),emptyList())),worker)
            val changed=command.copy(requestKey=id(5664),verificationPlan=verification.copy(id=id(5665)),manifest=WorkKey.Id(project,id(5666)))
            val held=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchMaterializationCommandCodec().encode(changed)))
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(held.receipt.outcome).code);assertTrue(held.receipt.writes.isEmpty())
        }
        val options=H1Options(H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) },workModelVersion=4)
        H1Application.open(root,storeId,options).use { app ->
            val current=app.coordinationCapture(plannerToken,project).work
            assertIs<WDispatchMaterializationPayload>(current.record(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(project,id(5659))).payload)
            val replay=assertIs<CommandResult.Settled>(app.execute(plannerToken,heldClosureBytes))
            assertTrue(replay.commandReplayed)
            assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(replay.receipt.outcome).code)
            assertTrue(replay.receipt.writes.isEmpty())
        }
    }
    @Test fun `future dependency rule is published and restored as an exact hold`() {
        val f=JointFixture();val original=f.base.work.getValue(project)
        val task=f.task.copy(taskId=id(5401));val verify=f.plan.copy(id=id(5402));val manifestKey=f.manifestKey.copy(id=id(5403))
        val taskValue=original.task(f.task).copy(dependencies=listOf(WDependency(f.task,"implemented",null,emptyList(),"wait for unique result")),verificationPlan=verify)
        val verification=(original.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value.copy(task=task)
        val manifest=(original.resolve(f.manifest).payload as WInputManifestsPayload).value.copy(task=task)
        fun row(k:WorkKey,p:WorkPayload)=WorkRecord(WorkRecordIdentity(payloadFamily(p),k),p)
        val manifestRecord=row(manifestKey,WInputManifestsPayload(manifest));val manifestRef=WorkReference(manifestRecord.identity,WorkCodec().digest(manifestRecord))
        val graph=WorkState.restore(project,original.records.values+listOf(row(task,WTaskRevisionsPayload(taskValue)),row(verify,WVerificationPlansPayload(verification)),manifestRecord),original.bodies.values,modelVersion=4)
        val rule=DispatchFutureDependency(f.task,"implemented")
        val entry=DispatchPlanEntry(task,manifestRef,f.profile,listOf(f.resource),id(5404),id(5405),listOf(rule))
        val plan=DispatchPlanCandidate(project,id(5406),f.controller,listOf(entry));val command=DispatchPlanCommand(id(5407),plan)
        val root=directory();val options=H1Options(UnavailableH1RuntimeFacts,workModelVersion=4)
        H1Application.initialize(root,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to graph)),options).use { app ->
            val reply=assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchPlanCommandCodec().encode(command)))
            assertIs<LogicalOutcome.Success>(reply.receipt.outcome)
        }
        H1Application.open(root,storeId,options).use { app ->
            val current=app.coordinationCapture(plannerToken,project).work
            val restored=(current.record(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,plan.planId)).payload as WDispatchPlanPayload).value
            assertEquals(plan,restored)
            assertEquals(DispatchResultChoice.Pending,chooseDispatchResult(current,restored.entries.single().futureDependencies.single()))
            val ref=current.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,plan.planId))
            val bound=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to current),expectedWorkModel=4)
            val input=DispatchMaterializationCommand(id(5412),ref,task,task.copy(revision=1),verify.copy(id=id(5408)),manifestKey.copy(id=id(5409)))
            f.key(plannerKey) { assertNotNull(f.bridge.authorizeDispatchMaterialization(bound,input,it)) }
            f.key(ownerKey) { assertNull(f.bridge.authorizeDispatchMaterialization(bound,input,it)) }
            for(removed in listOf(WorkPermission.TASK_PLAN,WorkPermission.TASK_DISPATCH)) {
                val restricted=bound.authority.records().map { row ->
                    if(row is Auth2Record.WorkGrants && row.value.enrollment.principalId==planner)
                        Auth2Record.WorkGrants(WorkGrant(row.value.enrollment,row.value.generation,row.value.permissions-removed)) else row
                }
                val other=H1BoundState.checked(storeId,f.head,bound.core,restricted,bound.work,expectedWorkModel=4)
                f.key(plannerKey) { assertNull(f.bridge.authorizeDispatchMaterialization(other,input,it)) }
            }
            f.key(plannerKey) { assertNull(f.bridge.authorizeDispatchMaterialization(bound,input.copy(task=f.task,newTask=f.task.copy(revision=1)),it)) }
            val raw=DispatchMaterializationCommandCodec().encode(input)
            assertIs<CommandResult.Denied>(app.execute(ownerToken,raw))
            val held=assertIs<CommandResult.Settled>(app.execute(plannerToken,raw))
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(held.receipt.outcome).code)
            assertTrue(held.receipt.writes.isEmpty())
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,raw)).commandReplayed)
            assertEquals(WorkProblem.NOT_READY,assertFailsWith<WorkFault> {
                materializeDispatchDependency(current,ref,task,task.copy(revision=1),verify.copy(id=id(5408)),manifestKey.copy(id=id(5409)))
            }.problem)
            val endpoint=NativeRuntimeEndpoint(app,project,f.controller,f.intended.nativeHostId,ByteArray(32))
            fun encoded(k:WorkKey)=str(identityCodec.encodeKey(k).decodeToString())
            val request=StrictJson.encode(obj("formatVersion" to num(1),"operation" to str("dispatch-materialization"),"projectId" to uuid(project),
                "planId" to uuid(plan.planId),"taskJSON" to encoded(task),"newTaskJSON" to encoded(task.copy(revision=1)),
                "verificationPlanJSON" to encoded(verify.copy(id=id(5408))),"manifestJSON" to encoded(manifestKey.copy(id=id(5409))),
                "taskRequestId" to uuid(id(5410)),"manifestRequestId" to uuid(id(5411))))
            assertFailsWith<ServiceFault> { endpoint.read(ownerToken,request) }
            assertFailsWith<ServiceFault> { endpoint.read(plannerToken,request) } // no exact result: held, no commands
            assertEquals(current.records,app.coordinationCapture(plannerToken,project).work.records)
            endpoint.close()
        }
    }
    @Test fun `dispatch plan service publishes replays and cold reopens through Git`() {
        val f=JointFixture();val prior=f.base.work.getValue(project)
        val graph=WorkState.restore(project,prior.records.values,prior.bodies.values,modelVersion=4)
        var allowSynthetic=false
        val options=H1Options(H1RuntimeFacts { state,command ->
            if(!allowSynthetic)UnavailableH1RuntimeFacts.capture(state,command)
            else H1ObservedFacts(state.basis,f.runtimeFacts,setOf(state.work.getValue(project).reference(WorkFamily.PROFILES,f.profile)))
        },workModelVersion=4)
        val entry=DispatchPlanEntry(f.task,f.manifest,f.profile,listOf(f.resource),id(5301),id(5302))
        val plan=DispatchPlanCandidate(project,id(5303),f.controller,listOf(entry))
        val command=DispatchPlanCommand(id(5304),plan);val codec=DispatchPlanCommandCodec();val bytes=codec.encode(command)
        val root=directory()
        H1Application.initialize(root,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to graph)),options).use { app ->
            assertIs<CommandResult.Denied>(app.execute(ownerToken,bytes))
            val observed=app.execute(plannerToken,bytes)
            if(observed is CommandResult.Refused)error("plan publication refused: ${observed.problem}")
            val first=assertIs<CommandResult.Settled>(observed)
            assertIs<LogicalOutcome.Success>(first.receipt.outcome);assertFalse(first.commandReplayed)
            val planRef=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,plan.planId))
            val attempt=DispatchAttempt(planRef,entry.preparationId,f.reserveCommand())
            val bind=DispatchAttemptCommand(id(5311),attempt);val bindCodec=DispatchAttemptCommandCodec();val bindBytes=bindCodec.encode(bind)
            assertIs<CommandResult.Denied>(app.execute(ownerToken,bindBytes))
            val binding=assertIs<CommandResult.Settled>(app.execute(plannerToken,bindBytes))
            assertIs<LogicalOutcome.Success>(binding.receipt.outcome);assertFalse(binding.commandReplayed)
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,bindBytes)).commandReplayed)
            val firstRef=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,entry.preparationId))
            val originalPayload=(f.reserveCommand().operation as CReserveAssignmentOperation).value
            val successorReserve=f.reserveCommand().copy(requestKey=id(5323),operation=CReserveAssignmentOperation(originalPayload.copy(
                assignmentId=id(5324),workspaceId=id(5325),intentId=id(5326),correlationId=id(5327),
                intendedExecution=originalPayload.intendedExecution.copy(executionId=id(5328),nativeSessionId="ses_successor"))))
            val successor=DispatchAttempt(planRef,id(5329),successorReserve,firstRef,entry.preparationId,id(5330))
            val successorCommand=DispatchAttemptCommand(id(5331),successor);val successorBytes=bindCodec.encode(successorCommand)
            assertEquals(StoreProblem.DENIED,assertIs<CommandResult.Refused>(app.execute(plannerToken,successorBytes)).problem)
            val endpoint=NativeRuntimeEndpoint(app,project,f.controller,f.intended.nativeHostId,ByteArray(32))
            val context=StrictJson.encode(obj("formatVersion" to num(1),"operation" to str("dispatch-context"),"projectId" to uuid(project),"planId" to uuid(plan.planId)))
            val frame=endpoint.read(plannerToken,context).value as JObject
            assertEquals(entry.preparationId,uuid(frame.entries.getValue("entries").list().single().member("preparationId")))
            assertEquals(entry.startId,uuid(frame.entries.getValue("entries").list().single().member("startId")))
            assertEquals(1,frame.entries.getValue("attemptsJSON").list().size)
            assertFailsWith<ServiceFault> { endpoint.read(ownerToken,context) }
            endpoint.close()
            val replay=assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes));assertTrue(replay.commandReplayed)
            val changed=command.copy(plan=plan.copy(entries=listOf(entry.copy(startId=id(5305)))))
            assertIs<CommandResult.IdentityConflict>(app.execute(plannerToken,codec.encode(changed)))
            val originalReserve=f.reserveCommand();val originalBytes=f.codec.encodeCommand(originalReserve)
            val retryEndpoint=NativeRuntimeEndpoint(app,project,f.controller,f.intended.nativeHostId,ByteArray(32))
            fun retryFrame(input:ByteArray)=StrictJson.encode(obj("formatVersion" to num(1),"operation" to str("reservation-retry"),
                "projectId" to uuid(project),"reservationJSON" to str(input.decodeToString())))
            assertEquals("hold",retryEndpoint.read(plannerToken,retryFrame(originalBytes)).value.member("kind").string())
            assertFailsWith<ServiceFault> { retryEndpoint.read(ownerToken,retryFrame(originalBytes)) }
            assertEquals(ReservationRetryEligibility.HOLD,app.reservationRetryEligibility(plannerToken,project,f.controller,originalBytes))
            assertFailsWith<AppAccessFault> { app.reservationRetryEligibility(ownerToken,project,f.controller,originalBytes) }
            val held=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(f.reserveCommand())))
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(held.receipt.outcome).code)
            assertTrue(held.receipt.writes.isEmpty())
            val child=assertIs<CommandResult.Settled>(app.execute(plannerToken,successorBytes))
            assertIs<LogicalOutcome.Success>(child.receipt.outcome);assertFalse(child.commandReplayed)
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,successorBytes)).commandReplayed)
            val otherReserve=successorReserve.copy(requestKey=id(5332),operation=CReserveAssignmentOperation(originalPayload.copy(
                assignmentId=id(5333),workspaceId=id(5334),intentId=id(5335),correlationId=id(5336),
                intendedExecution=originalPayload.intendedExecution.copy(executionId=id(5337),nativeSessionId="ses_duplicate_successor"))))
            val sibling=successor.copy(preparationId=id(5338),startId=id(5339),reservation=otherReserve)
            val siblingReply=assertIs<CommandResult.Settled>(app.execute(plannerToken,bindCodec.encode(DispatchAttemptCommand(id(5340),sibling))))
            assertEquals("INVALID_REFERENCE",assertIs<LogicalOutcome.Rejected>(siblingReply.receipt.outcome).code)
            assertTrue(siblingReply.receipt.writes.isEmpty())
            assertEquals(ReservationRetryEligibility.DEFINITIVE_REFUSAL,app.reservationRetryEligibility(plannerToken,project,f.controller,originalBytes))
            assertEquals("definitive-refusal",retryEndpoint.read(plannerToken,retryFrame(originalBytes)).value.member("kind").string())
            val altered=originalReserve.copy(operation=CReserveAssignmentOperation((originalReserve.operation as CReserveAssignmentOperation).value.copy(workspaceId=id(5310))))
            assertEquals(ReservationRetryEligibility.HOLD,app.reservationRetryEligibility(plannerToken,project,f.controller,f.codec.encodeCommand(altered)))
            assertEquals("hold",retryEndpoint.read(plannerToken,retryFrame(f.codec.encodeCommand(altered))).value.member("kind").string())
            val lateEntry=entry.copy(preparationId=id(5313),startId=id(5314))
            val latePlan=plan.copy(planId=id(5315),entries=listOf(lateEntry))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,codec.encode(DispatchPlanCommand(id(5316),latePlan)))).receipt.outcome)
            val lateOriginal=f.reserveCommand()
            val latePayload=(lateOriginal.operation as CReserveAssignmentOperation).value
            val lateReserve=lateOriginal.copy(requestKey=id(5317),operation=CReserveAssignmentOperation(latePayload.copy(
                assignmentId=id(5319),workspaceId=id(5320),intentId=id(5321),
                intendedExecution=latePayload.intendedExecution.copy(executionId=id(5322),nativeSessionId="ses_late_attempt"))))
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(lateReserve))).receipt.outcome).code)
            val lateRef=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,latePlan.planId))
            val lateBind=bindCodec.encode(DispatchAttemptCommand(id(5318),DispatchAttempt(lateRef,lateEntry.preparationId,lateReserve)))
            assertEquals(StoreProblem.DENIED,assertIs<CommandResult.Refused>(app.execute(plannerToken,lateBind)).problem)
            allowSynthetic=true
            val heldAgain=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(f.reserveCommand())))
            assertTrue(heldAgain.commandReplayed)
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(heldAgain.receipt.outcome).code)
            val reserve=successorReserve
            val assigned=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(reserve)))
            assertIs<LogicalOutcome.Success>(assigned.receipt.outcome)
            assertTrue(assigned.receipt.writes.any { it.record.family=="claims" })
            val childRef=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,successor.preparationId))
            val afterSuccess=sibling.copy(preparationId=id(5341),startId=id(5342),previous=childRef)
            assertEquals(StoreProblem.DENIED,assertIs<CommandResult.Refused>(app.execute(plannerToken,bindCodec.encode(DispatchAttemptCommand(id(5343),afterSuccess)))).problem)
            assertEquals(ReservationRetryEligibility.HOLD,app.reservationRetryEligibility(plannerToken,project,f.controller,f.codec.encodeCommand(reserve)))
            assertEquals("hold",retryEndpoint.read(plannerToken,retryFrame(f.codec.encodeCommand(reserve))).value.member("kind").string())
            retryEndpoint.close()
            val changedFrame=app.nativeDispatchContext(plannerToken,project,f.controller,f.intended.nativeHostId,plan.planId) as JObject
            assertEquals(1,changedFrame.entries.getValue("activeAssignments").list().size)
            val selection=StrictJson.decode(changedFrame.entries.getValue("entries").list().single().member("selectionJSON").string().toByteArray())
            assertEquals(0L,selection.member("resources").list().single().member("expectedGeneration").long())
        }
        H1Application.open(root,storeId,options).use { app ->
            val replay=assertIs<CommandResult.Settled>(app.execute(plannerToken,bytes));assertTrue(replay.commandReplayed)
            val attemptRow=app.coordinationCapture(plannerToken,project).work.record(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,entry.preparationId))
            assertEquals(f.reserveCommand(),(attemptRow.payload as WDispatchAttemptPayload).value.reservation)
            val rebound=DispatchAttemptCommand(id(5311),(attemptRow.payload as WDispatchAttemptPayload).value)
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchAttemptCommandCodec().encode(rebound))).commandReplayed)
            val successorRow=app.coordinationCapture(plannerToken,project).work.record(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,id(5329)))
            val successor=(successorRow.payload as WDispatchAttemptPayload).value
            assertEquals(entry.preparationId,successor.rootPreparationId);assertEquals(id(5330),successor.startId)
            assertTrue(assertIs<CommandResult.Settled>(app.execute(plannerToken,DispatchAttemptCommandCodec().encode(DispatchAttemptCommand(id(5331),successor)))).commandReplayed)
            assertEquals(ReservationRetryEligibility.DEFINITIVE_REFUSAL,app.reservationRetryEligibility(plannerToken,project,f.controller,f.codec.encodeCommand(f.reserveCommand())))
            val row=app.coordinationCapture(plannerToken,project).work.record(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,plan.planId))
            assertEquals(plan,(row.payload as WDispatchPlanPayload).value)
            val frame=app.nativeDispatchContext(plannerToken,project,f.controller,f.intended.nativeHostId,plan.planId) as JObject
            assertEquals(entry.preparationId,uuid(frame.entries.getValue("entries").list().single().member("preparationId")))
            assertEquals(2,frame.entries.getValue("attemptsJSON").list().size)
        }
    }
    @Test fun `model4 plan survives a real Git snapshot cold restore`() {
        val f=JointFixture();val prior=f.base.work.getValue(project)
        val graph=WorkState.restore(project,prior.records.values,prior.bodies.values,modelVersion=4)
        val base=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to graph),expectedWorkModel=4)
        val entry=DispatchPlanEntry(f.task,f.manifest,f.profile,listOf(f.resource),id(5201),id(5202))
        val plan=DispatchPlanCandidate(project,id(5203),f.controller,listOf(entry))
        val candidate=f.key(plannerKey) { f.bridge.dispatchPlan(base,DispatchPlanCommand(id(5204),plan),it) }
        val files=linkedMapOf<String,ByteArray>()
        CoreSnapshotCodec().export(base.core) { files[it.path]=it.bytes() }
        Auth2SnapshotCodec().export(base.authority).forEach { files[it.first]=it.second }
        files["work/format.json"]=StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(4)))
        val codec=WorkCodec(modelVersion=4)
        candidate.effect.after.records.values.forEach { files[identityCodec.recordPath(it.identity)]=codec.encodeRecord(it,true) }
        candidate.effect.after.bodies.forEach { (d,b) -> files["work/bodies/${d.hex.take(2)}/${d.hex}.txt"]=b.bytes() }
        val dir=directory()
        RuntimeOwner.acquire(dir).use { owner ->
            val git=NativeGit(owner);git.qualify();git.initialize()
            val objects=files.mapValues { git.blob(it.value) }
            fun tree(prefix:String):GitOid {
                val names=objects.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
                val input=names.joinToString("") { name ->
                    val path=prefix+name;val blob=objects[path]
                    if(blob==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "100644 blob ${blob.hex}\t$name\u0000"
                }.toByteArray(Charsets.US_ASCII)
                return git.oid(git.call(listOf("mktree","-z"),input).checked())
            }
            git.update(null,git.commit(tree(""),null)).checked()
        }
        RuntimeOwner.acquire(dir).use { owner ->
            val git=NativeGit(owner);git.qualify();git.verifyRepository();val head=git.head()
            val rows=git.call(listOf("ls-tree","-r","-z","--full-tree",head.hex)).checked().toString(Charsets.UTF_8)
                .split('\u0000').filter { it.isNotEmpty() }
            val entries=rows.associate { row ->
                val (metadata,path)=row.split('\t',limit=2);val fields=metadata.split(' ')
                assertEquals(listOf("100644","blob"),fields.take(2));path to GitOid("sha1",fields[2])
            }
            assertEquals(files.keys,entries.keys)
            val snapshot=StoreSnapshot(head,entries,git)
            assertFailsWith<StoreFault> { H1BoundState.restore(storeId,snapshot) }
            val restored=H1BoundState.restore(storeId,snapshot,expectedWorkModel=4)
            val record=restored.work.getValue(project).record(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,plan.planId))
            val restoredPlan=(record.payload as WDispatchPlanPayload).value
            assertEquals(plan,restoredPlan)
            assertEquals(entry.preparationId,restoredPlan.entries.single().preparationId)
            assertEquals(entry.startId,restoredPlan.entries.single().startId)
        }
    }
    @Test fun `dispatch plan candidate derives Git change only after controller authorization`() {
        val f=JointFixture()
        val prior=f.base.work.getValue(project)
        val graph=WorkState.restore(project,prior.records.values,prior.bodies.values,modelVersion=4)
        val base=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to graph),expectedWorkModel=4)
        val entry=DispatchPlanEntry(f.task,f.manifest,f.profile,listOf(f.resource),id(5101),id(5102))
        val plan=DispatchPlanCandidate(project,id(5103),f.controller,listOf(entry))
        f.key(ownerKey) { key -> assertEquals(StoreProblem.DENIED,assertFailsWith<StoreFault> { f.bridge.dispatchPlan(base,DispatchPlanCommand(id(5104),plan),key) }.problem) }
        val result=f.key(plannerKey) { key -> f.bridge.dispatchPlan(base,DispatchPlanCommand(id(5104),plan),key) }
        val change=result.change
        assertEquals("dispatch-plans",change.key.family)
        val record=WorkCodec(modelVersion=4).decodeRecord(requireNotNull(change.bytes()))
        assertEquals(plan,(record.payload as WDispatchPlanPayload).value)
        assertFalse(record.identity in base.work.getValue(project).records)
        assertEquals(record,result.effect.after.record(WorkFamily.DISPATCH_PLANS,record.identity.key))
        val after=H1BoundState.checked(storeId,f.head,base.core,base.authority.records(),mapOf(project to result.effect.after),expectedWorkModel=4)
        val fingerprint=DispatchPlanCommandCodec().fingerprint(DispatchPlanCommand(id(5104),plan))
        f.bridge.validateDispatchPlanEdge(base,after,planner,id(5104),plannerKey,fingerprint,WorkKey.Id(project,plan.planId))
        assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> {
            f.bridge.validateDispatchPlanEdge(base,after,planner,id(5104),plannerKey,Digest.of("altered".toByteArray()),WorkKey.Id(project,plan.planId))
        }.problem)
        assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> {
            f.bridge.validateDispatchPlanEdge(base,after,planner,id(5104),ownerKey,fingerprint,WorkKey.Id(project,plan.planId))
        }.problem)
    }
    @Test fun `dispatch plan authorization binds the actual controller and current grants`() {
        val f=JointFixture()
        val policy=Auth2Policy(f.base.authority,f.base.projection)
        val call=WorkInvocation(project,dev.writhelm.core.auth2.WorkOperation.RECORD_DISPATCH_PLAN,id(5000),null,f.controller,null)
        f.key(plannerKey) { key ->
            assertNotNull(policy.authorizeWork(key,call))
            assertNull(policy.authorizeWork(key,call.copy(controllerId=id(5001))))
            assertNull(policy.authorizeWork(key,call.copy(controllerId=null)))
            assertNull(policy.authorizeWork(key,call.copy(assignment=AssignmentSubject(project,f.assignment,0))))
        }
        f.key(ownerKey) { key -> assertNull(policy.authorizeWork(key,call)) }
        for(removed in listOf(WorkPermission.TASK_PLAN,WorkPermission.TASK_DISPATCH)) {
            val records=f.base.authority.records().map { row ->
                if(row is Auth2Record.WorkGrants && row.value.enrollment.principalId==planner)
                    Auth2Record.WorkGrants(WorkGrant(row.value.enrollment,row.value.generation,row.value.permissions-removed)) else row
            }
            val restricted=H1BoundState.checked(storeId,f.head,f.base.core,records,f.base.work)
            f.key(plannerKey) { key -> assertNull(Auth2Policy(restricted.authority,restricted.projection).authorizeWork(key,call)) }
        }
    }
    @Test fun linuxPeerProcessObservationChecksActualExecutableAndIncarnation() {
        val pid=ProcessHandle.current().pid();val origin="http://127.0.0.1:43217"
        val executable=Digest.of(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("/proc/self/exe")))
        val stat=java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/stat"))
        val start=stat.substring(stat.lastIndexOf(')')+1).trim().split(Regex("\\s+"))[19]
        val boot=java.nio.file.Files.readString(java.nio.file.Path.of("/proc/sys/kernel/random/boot_id")).trim()
        // Independent compact JSON oracle matching the existing native host's JSON.stringify array.
        val epoch=Digest.of("[\"native-linux-process-v1\",\"${executable.hex}\",\"$boot\",$pid,\"$start\",\"$origin\"]".toByteArray()).hex
        val pin=PinnedNativeExecutable("test-jvm",executable)
        assertEquals(ObservedNativeProcess("test-jvm",executable,epoch),LinuxNativeProcessObserver.observe(pid,origin,epoch,pin))
        assertNull(LinuxNativeProcessObserver.observe(pid,origin,"0".repeat(64),pin))
        assertNull(LinuxNativeProcessObserver.observe(pid,origin,epoch,pin.copy(digest=Digest.of("other".toByteArray()))))
        assertNull(LinuxNativeProcessObserver.observe(pid,"http://localhost:43217",epoch,pin))
        assertNull(LinuxNativeProcessObserver.observe(Int.MAX_VALUE.toLong(),origin,epoch,pin))
        // This is the real test JVM, not the OpenCode process or evidence of native qualification.
    }
    @Test fun registeredProfileRequiresLiveExactSubjectAndProtectedRetainedCheckArtifacts() {
        val f=JointFixture();val work=f.base.work.getValue(project)
        val root=java.nio.file.Files.createTempDirectory("writhelm-profile-evidence-")
        val checks=LinuxRegisteredProfileFacts.requiredChecks.associateWith { Digest.of("actual fixture check: $it".toByteArray()) }
        checks.forEach { (name,digest) ->
            val path=root.resolve("${digest.hex}.evidence")
            java.nio.file.Files.createFile(path,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")))
            java.nio.file.Files.writeString(path,"actual fixture check: $name")
        }
        val profile=work.reference(WorkFamily.PROFILES,f.profile)
        val subject=ProfileQualificationSubject(project,f.intended.nativeHostId,"a".repeat(64),"2.2.5",
            Digest.of("runtime fixture".toByteArray()),Digest.of("configuration fixture".toByteArray()),profile)
        val registration=RegisteredProfileQualification(subject,checks)
        var current: ProfileQualificationSubject?=subject
        val unregistered=LinuxRegisteredProfileFacts(UnavailableH1RuntimeFacts,root,emptyList(),CurrentProfileEnvironment { _,_->subject })
        assertTrue(unregistered.capture(f.base,f.reserveCommand()).qualifiedProfiles.isEmpty())
        val facts=LinuxRegisteredProfileFacts(UnavailableH1RuntimeFacts,root,listOf(registration),CurrentProfileEnvironment { _,_->current })
        fun capture()=facts.capture(f.base,f.reserveCommand())
        assertEquals(setOf(profile),capture().qualifiedProfiles)
        assertTrue(capture().facts.verifiedPackets.isEmpty())
        current=subject.copy(runtimeEpoch="b".repeat(64));assertTrue(capture().qualifiedProfiles.isEmpty())
        current=subject.copy(configurationDigest=Digest.of("changed".toByteArray()));assertTrue(capture().qualifiedProfiles.isEmpty())
        current=subject.copy(runtimeDigest=Digest.of("different runtime".toByteArray()));assertTrue(capture().qualifiedProfiles.isEmpty())
        current=subject.copy(host=id(1899));assertTrue(capture().qualifiedProfiles.isEmpty())
        current=null;assertTrue(capture().qualifiedProfiles.isEmpty())
        current=subject
        val first=checks.entries.first();val damaged=root.resolve("${first.value.hex}.evidence")
        java.nio.file.Files.writeString(damaged,"modified")
        assertTrue(capture().qualifiedProfiles.isEmpty())
        java.nio.file.Files.writeString(damaged,"actual fixture check: ${first.key}")
        java.nio.file.Files.setPosixFilePermissions(damaged,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"))
        assertTrue(capture().qualifiedProfiles.isEmpty())
        java.nio.file.Files.setPosixFilePermissions(damaged,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        assertEquals(setOf(profile),capture().qualifiedProfiles)
        java.nio.file.Files.delete(damaged)
        java.nio.file.Files.createSymbolicLink(damaged,root.resolve("${checks.values.last().hex}.evidence"))
        assertTrue(capture().qualifiedProfiles.isEmpty())
        assertFailsWith<IllegalArgumentException> { LinuxRegisteredProfileFacts(UnavailableH1RuntimeFacts,root,
            listOf(registration.copy(artifacts=checks-"compaction")),CurrentProfileEnvironment { _,_->subject }) }
        // Registry and observed runtime are synthetic here; only filesystem reads are real.
    }
    @Test fun pinnedDecisionSourceEnablesRealGitTaskPublicationWithoutQualifyingNativeProfile() {
        val f=JointFixture();val work=f.base.work.getValue(project);val original=work.task(f.task)
        val decision=pinnedKnowledge(f.base.core.snapshot(8192),requireNotNull(original.base)).first {
            f.base.core.validationDecisionApproved(it) && f.base.core.validationSourceAvailable(it)
        }
        val task=f.task.copy(taskId=id(1810));val plan=f.plan.copy(id=id(1811))
        val oldPlan=(work.record(WorkFamily.VERIFICATION_PLANS,f.plan).payload as WVerificationPlansPayload).value
        val terms=original.copy(kind="implementation",approvedDesign=listOf(decision),verificationPlan=plan)
        val create=WorkCommand(id(1812),project,CCreateTaskRevisionOperation(CCreateTaskRevision(task,terms,
            CCreateTaskRevisionNewVerificationPlan(plan,oldPlan.copy(task=task,previous=null)),emptyList())))
        val options=H1Options(CanonicalTaskDecisionFacts(UnavailableH1RuntimeFacts))
        val root=directory()
        H1Application.initialize(root,storeId,H1Genesis(f.base.core,f.base.authority.records(),f.base.work),options).use { app ->
            val result=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(create)))
            assertIs<LogicalOutcome.Success>(result.receipt.outcome)
            assertEquals(terms,app.coordinationCapture(plannerToken,project).work.task(task))
            val reserve=assertIs<CommandResult.Settled>(app.execute(plannerToken,f.codec.encodeCommand(f.reserveCommand())))
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(reserve.receipt.outcome).code)
            assertTrue(reserve.receipt.writes.isEmpty())
        }
        H1Application.open(root,storeId,options).use { app ->
            assertEquals(terms,app.coordinationCapture(plannerToken,project).work.task(task))
        }
        // Real local Git publication with synthetic project data; no native qualification is implied.
    }
    @Test fun nativePacketNeedsBoundAssignmentAndCannotVerifyPreReservationInput() {
        val f=JointFixture();val subject=WAssignmentSubject(f.assignment,0)
        assertFails { NativeAssignmentPacket.render(f.base,project,subject) }
        f.reserve()
        assertFails { NativeAssignmentPacket.render(f.base,project,subject) }
        val active=JointFixture();active.activateWithAttributedFixtureObservations()
        assertTrue(NativeAssignmentPacket.render(active.base,project,WAssignmentSubject(active.assignment,0)).isNotEmpty())
        // This proves ordering in the component model, not that a native packet was delivered.
    }
    @Test fun canonicalTaskDecisionFactsUseOnlyCurrentApprovedPinnedCoreInputs() {
        val f=JointFixture();val work=f.base.work.getValue(project);val original=work.task(f.task)
        val pinned=pinnedKnowledge(f.base.core.snapshot(8192),requireNotNull(original.base))
        val decision=pinned.first { f.base.core.validationDecisionApproved(it) && f.base.core.validationSourceAvailable(it) }
        val foreign=when(decision) {
            is ClauseReference -> decision.copy(contentDigest=Digest.of("different".toByteArray()))
            is ContextReference -> decision.copy(contextDigest=Digest.of("different".toByteArray()))
        }
        val task=original.copy(kind="implementation",approvedDesign=listOf(decision,foreign))
        val command=WorkCommand(id(1800),project,CCreateTaskRevisionOperation(CCreateTaskRevision(f.task,task,null,emptyList())))
        val facts=CanonicalTaskDecisionFacts(UnavailableH1RuntimeFacts).capture(f.base,command)
        assertEquals(setOf(decision),facts.facts.approvedDecisions)
        assertEquals(setOf(decision),facts.facts.knowledgeSources)
        assertTrue(facts.qualifiedProfiles.isEmpty());assertTrue(facts.facts.verifiedPackets.isEmpty())
        val noBase=command.copy(operation=CCreateTaskRevisionOperation(CCreateTaskRevision(f.task,task.copy(base=null),null,emptyList())))
        assertTrue(CanonicalTaskDecisionFacts(UnavailableH1RuntimeFacts).capture(f.base,noBase).facts.approvedDecisions.isEmpty())
        val badBase=requireNotNull(task.base).copy(knowledgeManifestDigest=Digest.of("different base".toByteArray()))
        val changedBase=command.copy(operation=CCreateTaskRevisionOperation(CCreateTaskRevision(f.task,task.copy(base=badBase),null,emptyList())))
        assertTrue(CanonicalTaskDecisionFacts(UnavailableH1RuntimeFacts).capture(f.base,changedBase).facts.approvedDecisions.isEmpty())
        // Canonical approval is not native qualification, packet verification or an independent review.
    }
    @Test fun reservationProofFactsRequireExactBasisCommandProfileAndPacketIndependently() {
        val f=JointFixture();val command=f.reserveCommand();val fingerprint=f.codec.fingerprint(command)
        val profile=f.base.work.getValue(project).reference(WorkFamily.PROFILES,f.profile)
        val expectedProfile=ReservationProfileProof(f.head,project,fingerprint,profile)
        val expectedPacket=ReservationPacketProof(f.head,project,fingerprint,f.task,f.manifest,profile)
        var profileProof: ReservationProfileProof?=expectedProfile
        var packetProof: ReservationPacketProof?=expectedPacket
        var error=false;var calls=0
        val control=object: ReservationProofControl {
            override fun profile(state: H1BoundState,command: WorkCommand): ReservationProfileProof? {
                calls++
                if(error)throw IllegalStateException("unavailable")
                return profileProof
            }
            override fun packet(state: H1BoundState,command: WorkCommand): ReservationPacketProof? {
                calls++
                if(error)throw IllegalStateException("unavailable")
                return packetProof
            }
        }
        val empty=H1RuntimeFacts { state,_ -> H1ObservedFacts(state.basis,f.observations.facts.copy(verifiedPackets=emptySet()),emptySet()) }
        val facts=ReservationProofFacts(empty,control)
        fun capture()=facts.capture(f.base,command)
        assertEquals(setOf(profile),capture().qualifiedProfiles)
        assertEquals(setOf(f.manifest),capture().facts.verifiedPackets)
        assertIs<LogicalOutcome.Success>(f.key(plannerKey) { f.bridge.work(f.base,command,it,capture()) }.evaluation.outcome)
        profileProof=expectedProfile.copy(basis=GitOid("sha1","a".repeat(40)))
        packetProof=expectedPacket.copy(command=Digest.of("other command".toByteArray()))
        assertTrue(capture().qualifiedProfiles.isEmpty());assertTrue(capture().facts.verifiedPackets.isEmpty())
        profileProof=expectedProfile;packetProof=null
        assertEquals(setOf(profile),capture().qualifiedProfiles);assertTrue(capture().facts.verifiedPackets.isEmpty())
        profileProof=null;packetProof=expectedPacket
        assertTrue(capture().qualifiedProfiles.isEmpty());assertEquals(setOf(f.manifest),capture().facts.verifiedPackets)
        val denied=f.key(plannerKey) { f.bridge.work(f.base,command,it,capture()) }
        assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(denied.evaluation.outcome).code)
        assertTrue(denied.evaluation.changes.isEmpty())
        profileProof=expectedProfile.copy(project=id(777));packetProof=expectedPacket.copy(task=f.task.copy(taskId=id(778)))
        assertTrue(capture().qualifiedProfiles.isEmpty());assertTrue(capture().facts.verifiedPackets.isEmpty())
        val priorCalls=calls
        val reserve=(command.operation as CReserveAssignmentOperation).value
        val wrongFamily=command.copy(operation=CReserveAssignmentOperation(reserve.copy(manifest=profile)))
        val invalid=facts.capture(f.base,wrongFamily)
        assertTrue(invalid.qualifiedProfiles.isEmpty());assertTrue(invalid.facts.verifiedPackets.isEmpty())
        assertEquals(priorCalls,calls,"invalid canonical inputs must not reach a proof provider")
        error=true
        assertTrue(capture().qualifiedProfiles.isEmpty());assertTrue(capture().facts.verifiedPackets.isEmpty())
        // Synthetic proof control is not an installed native qualification or packet provider.
    }
    @Test fun closureGapRefusesEvenExactClaimedPacketProofBeforeProviderIsConsulted() {
        val f=JointFixture();val original=f.base.work.getValue(project)
        val source=(original.resolve(f.manifest).payload as WInputManifestsPayload).value
        val key=WorkKey.Id(project,id(5801))
        val row=WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,key),WInputManifestsPayload(source.copy(
            knownGaps=listOf("Fresh mandatory-input closure assessment required for selected dependency results"))))
        val graph=WorkState.restore(project,original.records.values+row,original.bodies.values)
        val base=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to graph))
        val manifest=graph.reference(WorkFamily.INPUT_MANIFESTS,key)
        val reserve=(f.reserveCommand().operation as CReserveAssignmentOperation).value.copy(manifest=manifest)
        val command=f.reserveCommand().copy(operation=CReserveAssignmentOperation(reserve))
        var calls=0
        val control=object:ReservationProofControl {
            override fun profile(state:H1BoundState,command:WorkCommand):ReservationProfileProof? { calls++;return null }
            override fun packet(state:H1BoundState,command:WorkCommand):ReservationPacketProof? { calls++;return null }
        }
        // Even a mistakenly optimistic upstream synthetic packet fact cannot bypass knownGaps.
        val optimistic=H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.observations.facts.copy(verifiedPackets=setOf(manifest)),f.observations.qualifiedProfiles) }
        val facts=ReservationProofFacts(optimistic,control).capture(base,command)
        assertEquals(0,calls)
        val denied=f.key(plannerKey) { f.bridge.work(base,command,it,facts) }
        assertEquals("BLOCKED_INPUT",assertIs<LogicalOutcome.Rejected>(denied.evaluation.outcome).code)
        assertTrue(denied.evaluation.changes.isEmpty())
    }
    @Test fun prebindingProofIsBoundToCurrentHeadAndExactReservationNotFullPacket() {
        val f=JointFixture();val original=f.base.work.getValue(project)
        val work=WorkState.restore(project,original.records.values,original.bodies.values,modelVersion=4)
        val base=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to work),expectedWorkModel=4)
        val command=f.reserveCommand();val profile=work.reference(WorkFamily.PROFILES,f.profile)
        val expected=ReservationInputProof(f.head,project,f.codec.fingerprint(command),f.task,f.manifest,profile)
        var proof:ReservationInputProof?=expected
        val control=object:ReservationProofControl {
            override fun profile(state:H1BoundState,command:WorkCommand):ReservationProfileProof?=null
            override fun packet(state:H1BoundState,command:WorkCommand):ReservationPacketProof?=null
            override fun inputs(state:H1BoundState,command:WorkCommand):ReservationInputProof?=proof
        }
        val delegate=H1RuntimeFacts { state,_->H1ObservedFacts(state.basis,f.observations.facts.copy(verifiedPackets=emptySet()),f.observations.qualifiedProfiles) }
        val assessment=PrebindingAssessment(f.head,project,f.codec.fingerprint(command),work.reference(WorkFamily.TASK_REVISIONS,f.task),
            f.manifest,profile,f.source)
        var independent:PrebindingAssessment?=assessment
        val verifier=IndependentPrebindingInputVerifier(delegate,PrebindingAssessmentControl { _,_->independent })
        assertEquals(expected,verifier.inputs(base,command))
        independent=assessment.copy(basis=GitOid("sha1","b".repeat(40)))
        assertNull(verifier.inputs(base,command))
        independent=assessment.copy(manifest=profile)
        assertNull(verifier.inputs(base,command))
        independent=null;assertNull(verifier.inputs(base,command))
        independent=assessment
        val noRetained=H1RuntimeFacts { state,_->H1ObservedFacts(state.basis,f.observations.facts.copy(retainedSnapshots=emptySet()),f.observations.qualifiedProfiles) }
        assertNull(IndependentPrebindingInputVerifier(noRetained,PrebindingAssessmentControl { _,_->independent }).inputs(base,command))
        val extraSnapshot=f.snapshot.copy(retainedLocatorId=id(5890))
        val extraSource=WSourceVariant1Value(WSourceVariant1("artifact",extraSnapshot))
        val applicability=work.record(WorkFamily.APPLICABILITY,f.applicability)
        val data=(applicability.payload as WApplicabilityPayload).value
        val changed=applicability.copy(payload=WApplicabilityPayload(data.copy(entries=listOf(WApplicabilityEntry(extraSource,emptyList(),emptyList(),"applicable","required")))))
        val missingInput=WorkState.restore(project,work.records.values.filter { it.identity!=changed.identity }+changed,work.bodies.values,modelVersion=4)
        val changedBase=H1BoundState.checked(storeId,f.head,f.base.core,f.base.authority.records(),mapOf(project to missingInput),expectedWorkModel=4)
        val enoughFacts=H1RuntimeFacts { state,_->H1ObservedFacts(state.basis,f.observations.facts.copy(retainedSnapshots=f.observations.facts.retainedSnapshots+extraSnapshot),f.observations.qualifiedProfiles) }
        var assessmentCalls=0
        assertNull(IndependentPrebindingInputVerifier(enoughFacts,PrebindingAssessmentControl { _,_->assessmentCalls++;assessment }).inputs(changedBase,command))
        assertEquals(0,assessmentCalls) // a trusted assessment cannot replace a missing mandatory input
        val checkedFacts=ReservationProofFacts(delegate,verifier).capture(base,command)
        assertEquals(setOf(WorkPrebindingClosure(f.task,f.manifest,f.profile,f.codec.fingerprint(command))),checkedFacts.facts.prebindingClosures)
        assertTrue(checkedFacts.facts.verifiedPackets.isEmpty())
        assertIs<LogicalOutcome.Success>(f.key(plannerKey) { f.bridge.work(base,command,it,checkedFacts) }.evaluation.outcome)
        val provider=ReservationProofFacts(delegate,control)
        val exact=provider.capture(base,command)
        assertEquals(setOf(WorkPrebindingClosure(f.task,f.manifest,f.profile,f.codec.fingerprint(command))),exact.facts.prebindingClosures)
        assertTrue(exact.facts.verifiedPackets.isEmpty())
        assertIs<LogicalOutcome.Success>(f.key(plannerKey) { f.bridge.work(base,command,it,exact) }.evaluation.outcome)
        proof=expected.copy(basis=GitOid("sha1","a".repeat(40)))
        assertTrue(provider.capture(base,command).facts.prebindingClosures.isEmpty())
        proof=expected.copy(command=Digest.of("other command".toByteArray()))
        assertTrue(provider.capture(base,command).facts.prebindingClosures.isEmpty())
    }
    @Test fun resultDiffFactsRequireExactAssignmentHeadCommandAndObservedBytes() {
        val f=JointFixture();f.activateWithAttributedFixtureObservations()
        val subject=WAssignmentSubject(f.assignment,0)
        val result=f.snapshot.copy(tree=WorkGitOid("sha1","9".repeat(40)),retainedLocatorId=id(5810))
        val bytes=Utf8Content.fromText("synthetic exact diff bytes\n");val changes=WorkBodyReference(bytes.digest,bytes.sizeBytes)
        val retroKey=WorkKey.Id(project,id(5811))
        val retro=WorkCommand(id(5816),project,CRecordRetrospectiveOperation(CRecordRetrospective(retroKey,
            CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())))
        val prior=f.key(f.workerKey) { f.bridge.work(f.base,retro,it,f.observations) }
        assertIs<LogicalOutcome.Success>(prior.evaluation.outcome);f.install(prior)
        val retrospective=f.base.work.getValue(project).reference(WorkFamily.RETROSPECTIVES,retroKey)
        val data=CSubmitResultData(f.task,subject,f.manifest,listOf(result),changes,listOf(id(96)),emptyList(),retrospective,emptyList())
        val command=WorkCommand(id(5812),project,CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(5813)),data,
            listOf(CSubmitResultBodiesItem(bytes.digest,bytes.text())))))
        val expected=NativeResultDiffProof(f.head,project,f.codec.fingerprint(command),subject,f.intended.executionId,
            f.task,f.manifest,f.snapshot,result,changes)
        val job=obj("formatVersion" to num(1),"assignmentId" to uuid(f.assignment),"generation" to str("0"),"intentId" to uuid(f.intent),
            "workspaceId" to uuid(f.workspace),"intendedExecution" to encodeWExecutionIntent(f.intended),
            "taskJSON" to str(identityCodec.encodeKey(f.task).decodeToString()),"profileJSON" to str("{}"),
            "baseJSON" to str(StrictJson.encode(encodeWSnapshot(f.snapshot)).decodeToString()),"snapshotJSON" to str("{}"),
            "artifactLocatorId" to uuid(id(5817)),"epoch" to str("a".repeat(64)),"directory" to str("synthetic"),"agent" to str("synthetic"),"model" to JNull)
        fun frame(preparation:JObject=job)=obj("formatVersion" to num(1),"preparationJSON" to str(StrictJson.encode(preparation).decodeToString()),
            "resultJSON" to str(StrictJson.encode(encodeWSnapshot(result)).decodeToString()),"diffSha256" to str(changes.digest.hex),"diffByteLength" to num(changes.byteLength))
        var host:JsonValue?=frame()
        val transport=NativeResultDiffTransportProof(NativeResultDiffRawControl { _,_,_->host },"a".repeat(64))
        assertEquals(expected,transport.observe(f.base,command))
        host=frame(JObject(job.entries+("epoch" to str("b".repeat(64)))))
        assertNull(transport.observe(f.base,command))
        host=null;assertNull(transport.observe(f.base,command))
        var proof:NativeResultDiffProof?=expected;var calls=0
        var retained=true
        val delegate=H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,
            if(retained)f.observations.facts.copy(retainedSnapshots=f.observations.facts.retainedSnapshots+result) else f.observations.facts,
            f.observations.qualifiedProfiles) }
        assertFalse(H1Options(delegate).retentionEffectQualification.qualified(f.base,project,subject,f.intended.executionId,result))
        retained=false
        val retentionFrame=obj("formatVersion" to num(1),"assignmentId" to uuid(f.assignment),"generation" to str("0"),
            "executionId" to uuid(f.intended.executionId),"snapshot" to encodeWSnapshot(result))
        var retentionRaw:JsonValue?=str(StrictJson.encode(retentionFrame).decodeToString());var qualified=false;var retentionCalls=0
        val retention=NativeResultRetentionFacts(delegate,NativeResultRetentionRawControl { _,_,_,_->retentionCalls++;retentionRaw },
            ResultRetentionQualification { _,_->qualified })
        assertFalse(result in retention.capture(f.base,command).facts.retainedSnapshots);assertEquals(0,retentionCalls)
        qualified=true
        assertTrue(result in retention.capture(f.base,command).facts.retainedSnapshots)
        val combined=NativeResultDiffFacts(retention,NativeResultDiffProofControl { _,_->expected })
        assertEquals(setOf(WorkSubmittedDiff(f.task,listOf(result),changes)),combined.capture(f.base,command).facts.submittedDiffs)
        assertIs<LogicalOutcome.Success>(f.key(f.workerKey) { f.bridge.work(f.base,command,it,combined.capture(f.base,command)) }.evaluation.outcome)
        retentionRaw=str(StrictJson.encode(JObject(retentionFrame.entries+("executionId" to uuid(id(5814))))).decodeToString())
        assertFalse(result in retention.capture(f.base,command).facts.retainedSnapshots)
        retentionRaw=null;assertFalse(result in retention.capture(f.base,command).facts.retainedSnapshots)
        retained=true
        val facts=NativeResultDiffFacts(delegate,NativeResultDiffProofControl { _,_->calls++;proof })
        fun capture()=facts.capture(f.base,command)
        assertEquals(setOf(WorkSubmittedDiff(f.task,listOf(result),changes)),capture().facts.submittedDiffs)
        assertTrue(result in capture().facts.retainedSnapshots)
        val accepted=f.key(f.workerKey) { f.bridge.work(f.base,command,it,capture()) }
        assertIs<LogicalOutcome.Success>(accepted.evaluation.outcome)
        assertTrue(accepted.evaluation.changes.any { it.key.family=="results" })
        for(bad in listOf(expected.copy(basis=GitOid("sha1","a".repeat(40))),expected.copy(command=Digest.of("different".toByteArray())),
            expected.copy(executionId=id(5814)),expected.copy(result=result.copy(tree=f.snapshot.tree)),
            expected.copy(changes=WorkBodyReference(Digest.of("different bytes".toByteArray()),changes.byteLength)))) {
            proof=bad;assertTrue(capture().facts.submittedDiffs.isEmpty())
        }
        proof=null;assertTrue(capture().facts.submittedDiffs.isEmpty())
        val rejected=f.key(f.workerKey) { f.bridge.work(f.base,command,it,capture()) }
        assertEquals("INVALID_SUBJECT",assertIs<LogicalOutcome.Rejected>(rejected.evaluation.outcome).code)
        assertTrue(rejected.evaluation.changes.isEmpty())
        proof=expected;retained=false
        val beforeMissing=calls
        assertTrue(capture().facts.submittedDiffs.isEmpty());assertEquals(beforeMissing,calls)
        retained=true
        val previous=calls
        val forged=command.copy(operation=CSubmitResultOperation((command.operation as CSubmitResultOperation).value.copy(data=data.copy(
            artifacts=listOf(result.copy(repositoryId=id(5815)))))))
        assertTrue(facts.capture(f.base,forged).facts.submittedDiffs.isEmpty());assertEquals(previous,calls)
    }
    @Test fun reservationNeedsBothQualifiedProfileAndVerifiedPacketWithoutCreatingClaimsOnRefusal() {
        val f=JointFixture()
        fun attempt(facts: H1ObservedFacts)=f.key(plannerKey) { f.bridge.work(f.base,f.reserveCommand(),it,facts) }
        fun rejected(candidate: H1JointCandidate,code: String) {
            assertEquals(code,assertIs<LogicalOutcome.Rejected>(candidate.evaluation.outcome).code)
            assertTrue(candidate.evaluation.changes.isEmpty())
            assertEquals(f.base.work.getValue(project).records,candidate.work.getValue(project).records)
        }
        rejected(attempt(H1ObservedFacts(f.head,f.observations.facts,emptySet())),"NOT_READY")
        rejected(attempt(H1ObservedFacts(f.head,f.observations.facts.copy(verifiedPackets=emptySet()),f.observations.qualifiedProfiles)),"BLOCKED_INPUT")
        assertIs<LogicalOutcome.Success>(attempt(f.observations).evaluation.outcome)
        // Synthetic fixture facts establish only the fail-closed gate, not real host qualification.
    }
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
            fun receipt(c:WorkCommand,token:String=workerToken)=app.originalResultReceipt(token,project,f.controller,f.intended.nativeHostId,f.codec.encodeCommand(c))
            assertEquals(OriginalResultReceipt.MISSING,receipt(resultCommand))
            assertFailsWith<AppAccessFault> { receipt(resultCommand,plannerToken) }
            val outcome=ref(publish(resultCommand))
            assertEquals(OriginalResultReceipt.MATCH,receipt(resultCommand))
            // Synthetic observations in this fixture; this verifies only candidate input selection,
            // not planner assessment, Git assembly, retention qualification or acceptance.
            val candidateWork=app.coordinationCapture(plannerToken,project).work
            val inputs=candidateInputs(candidateWork,listOf(outcome),f.runtimeFacts)
            assertEquals(listOf(outcome),inputs.map { it.reference })
            assertEquals(f.snapshot,inputs.single().base);assertEquals(f.snapshot,inputs.single().result)
            assertFailsWith<WorkFault> { candidateInputs(candidateWork,listOf(outcome,outcome),f.runtimeFacts) }
            assertFailsWith<WorkFault> { candidateInputs(candidateWork,listOf(outcome),f.runtimeFacts.copy(submittedDiffs=emptySet())) }
            assertFailsWith<WorkFault> { candidateInputs(candidateWork,listOf(outcome),f.runtimeFacts.copy(retainedSnapshots=emptySet())) }
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
            assertEquals(OriginalResultReceipt.MATCH,receipt(resultCommand))
            assertEquals(OriginalResultReceipt.HOLD,receipt(resultCommand.copy(requestKey=id(181))))
            val before=app.inspectAccess(plannerToken).basis;val calls=captures;val cas=publications
            assertTrue(publish(resultCommand).commandReplayed)
            assertTrue(publish(rejection).commandReplayed)
            assertIs<CommandResult.Denied>(app.execute(workerToken,f.codec.encodeCommand(resultCommand.copy(requestKey=id(181)))))
            val altered=resultCommand.copy(operation=CSubmitResultOperation((resultCommand.operation as CSubmitResultOperation).value.copy(data=(resultCommand.operation as CSubmitResultOperation).value.data.copy(claimedCriteria=listOf(id(96),id(999))))))
            assertEquals(OriginalResultReceipt.HOLD,receipt(altered))
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
            assertFailsWith<AppAccessFault> { receipt(resultCommand) }
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
    @Test fun closureEvidenceAttributionComesFromReceiptAndExactSubjectsNotCallerClaim() {
        val f=JointFixture()
        f.publishedActiveApp(modelVersion=4).use { app ->
            val task=f.base.work.getValue(project).reference(WorkFamily.TASK_REVISIONS,f.task)
            val inventory=PrebindingInputInventory(task,f.manifest,emptyList())
            fun source(ref:WorkReference)=WSourceVariant2Value(WSourceVariant2("work",ref))
            val body=Utf8Content.fromText("component fixture, not a qualified closure assessment")
            val bodyRef=WorkBodyReference(body.digest,body.sizeBytes)
            val evidenceId=WorkKey.Id(project,id(5900))
            val data=CRecordEvidenceData(listOf(source(task),source(f.manifest)),emptyList(),"fixture","component",bodyRef,id(5901),
                "passed",emptyList(),emptyList(),emptyList(),bodyRef)
            val command=WorkCommand(id(5902),project,CRecordEvidenceOperation(CRecordEvidence(evidenceId,data,listOf(CRecordEvidenceBodiesItem(body.digest,body.text())))))
            val receipt=assertIs<CommandResult.Settled>(app.execute(token(f.workerKey),f.codec.encodeCommand(command)))
            assertIs<LogicalOutcome.Success>(receipt.receipt.outcome)
            // A subsequent evaluated command captures the published H, including its receipt-derived attribution.
            app.execute(plannerToken,f.codec.encodeCommand(f.reserveCommand().copy(requestKey=id(5903))))
            val captured=assertNotNull(f.capturedRuntimeState)
            val evidence=captured.work.getValue(project).reference(WorkFamily.EVIDENCE,evidenceId)
            val attributed=assertNotNull(attributedClosureEvidence(captured,project,inventory,evidence,planner))
            assertEquals(f.worker,attributed.principalId);assertEquals(f.delegation,attributed.delegationId)
            val retained=WDispatchClosurePayload(DispatchClosureCommand(id(5904),evidence,WorkGitOid(captured.basis.algorithm,captured.basis.hex),
                source(evidence),WorkKey.Id(project,id(5905))),f.worker,f.delegation)
            assertTrue(closureAttributionMatches(captured,project,inventory,planner,retained))
            assertFalse(closureAttributionMatches(captured,project,inventory,planner,retained.copy(assessor=owner)))
            assertFalse(closureAttributionMatches(captured,project,inventory,planner,retained.copy(delegation=id(5906))))
            assertNull(attributedClosureEvidence(captured,project,inventory,evidence,f.worker))
            assertNull(attributedClosureEvidence(captured,project,inventory,evidence.copy(digest=Digest.of(byteArrayOf(1))),planner))
            assertNull(attributedClosureEvidence(captured,project,inventory.copy(task=captured.work.getValue(project).reference(WorkFamily.PROFILES,f.profile)),evidence,planner))
            assertNull(attributedClosureEvidence(captured,project,inventory.copy(dependencies=listOf(evidence)),evidence,planner))
            val withoutReceiptIndex=H1BoundState.checked(storeId,captured.basis,captured.core,captured.authority.records(),captured.work,expectedWorkModel=4)
            assertNull(attributedClosureEvidence(withoutReceiptIndex,project,inventory,evidence,planner))
        }
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
