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
internal class ReviewAppFixture {
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
