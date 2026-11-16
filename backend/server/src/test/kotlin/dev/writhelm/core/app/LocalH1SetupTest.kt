// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.core.workflow.subject
import dev.writhelm.core.store.H1Catalog
import dev.writhelm.service.LocalSetup
import dev.writhelm.service.LocalH1Runtime
import dev.writhelm.service.LinuxTestClientEnrollment
import dev.writhelm.service.LinuxTestClientRepository
import dev.writhelm.service.TestClientEnrollmentEndpoint
import dev.writhelm.service.TestClientNativeSessions
import dev.writhelm.service.HttpAdapter
import dev.writhelm.service.HttpProfile
import dev.writhelm.service.WrithelmService
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.Test
import kotlin.test.*

/** H1-COMPOSE-MODEL-FREE-01: test-source-only synthetic observation port. Direct permits bind exact
 * command/basis; derived task probes must match an exact scoped retained frontier intent at H.
 * Never a qualification registry/launcher input or authorization for a model/native effect. */
internal class CompositionSyntheticFacts : H1RuntimeFacts {
    internal data class Scope(val store:java.util.UUID,val project:java.util.UUID,val controller:java.util.UUID,
        val basis:dev.writhelm.core.store.GitOid)
    private data class Permit(val scope:Scope,val commandDigest:Digest,val facts:dev.writhelm.core.work.WorkExternalFacts,
        val profiles:Set<WorkReference>)
    private var permit:Permit?=null
    var trustedFallback:H1RuntimeFacts?=null
    private data class DispatchIntents(val store:java.util.UUID,val project:java.util.UUID,val controller:java.util.UUID,
        val custody:Path,val preparation:java.util.UUID,val facts:WorkExternalFacts)
    private var dispatchIntents:DispatchIntents?=null
    var syntheticCaptures=0;private set
    var unavailableCaptures=0;private set
    @Synchronized fun arm(scope:Scope,command:WorkCommand,facts:WorkExternalFacts,profiles:Set<WorkReference> = emptySet()) {
        require(command.projectId==scope.project)
        require(profiles.all {it.identity.key.projectId==scope.project})
        permit=Permit(scope,Digest.of(WorkCodec().encodeCommand(command)),facts,profiles)
    }
    @Synchronized fun allowRetainedDispatchIntents(store:java.util.UUID,project:java.util.UUID,controller:java.util.UUID,
        custody:Path,preparation:java.util.UUID,facts:WorkExternalFacts){
        dispatchIntents=DispatchIntents(store,project,controller,custody,preparation,facts)
    }
    @Synchronized fun disarm(){permit=null;dispatchIntents=null}
    private fun retainedDispatchFacts(state:H1BoundState,command:WorkCommand):WorkExternalFacts? {
        val d=dispatchIntents ?: return null
        if(state.storeId!=d.store || command.projectId!=d.project || state.authority.controllers[d.controller]?.projectId!=d.project)return null
        fun retained(kind:String,key:java.util.UUID):ByteArray? {
            val path=d.custody.resolve("$kind-${Digest.of(key.toString().toByteArray()).hex}.json")
            return if(Files.exists(path))protectedRuntimeBytes(d.custody,path,2*1024*1024)else null
        }
        val raw=retained("dispatch-materialization",d.preparation) ?: return null
        val materialization=DispatchMaterializationCommandCodec().decode(raw)
        val work=state.work[d.project] ?: return null
        val plan=(work.resolve(materialization.plan).payload as WDispatchPlanPayload).value
        if(plan.controllerId!=d.controller || materialization.task.projectId!=d.project)return null
        val expected=if(command.requestKey==materialization.requestKey){
            val proposal=materializeDispatchDependency(work,materialization.plan,materialization.task,materialization.newTask,
                materialization.verificationPlan,materialization.manifest)
            WorkCommand(materialization.requestKey,d.project,CCreateTaskRevisionOperation(proposal.task))
        }else {
            var closureRaw=retained("dispatch-closure",d.preparation) ?: return null
            var closure=DispatchClosureCommandCodec().decode(closureRaw)
            var depth=0
            while(closure.requestKey!=command.requestKey){
                if(++depth>256)return null
                closureRaw=retained("dispatch-closure-successor",closure.requestKey) ?: return null
                closure=DispatchClosureCommandCodec().decode(closureRaw)
            }
            if(closure.materialization.identity.key!=WorkKey.Id(d.project,materialization.requestKey) ||
                closure.expectedHead!=WorkGitOid(state.basis.algorithm,state.basis.hex))return null
            WorkCommand(closure.requestKey,d.project,CCreateTaskRevisionOperation(CCreateTaskRevision(materialization.newTask,
                work.task(materialization.newTask),null,emptyList())))
        }
        if(!WorkCodec().encodeCommand(expected).contentEquals(WorkCodec().encodeCommand(command)))return null
        return d.facts
    }
    @Synchronized override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val p=permit
        if(p==null || state.storeId!=p.scope.store || state.basis!=p.scope.basis || command.projectId!=p.scope.project ||
            state.authority.controllers[p.scope.controller]?.projectId!=p.scope.project ||
            Digest.of(WorkCodec().encodeCommand(command))!=p.commandDigest){
            val retained=retainedDispatchFacts(state,command)
            if(retained!=null){syntheticCaptures++;return H1ObservedFacts(state.basis,retained,emptySet())}
            unavailableCaptures++;return (trustedFallback ?: UnavailableH1RuntimeFacts).capture(state,command)
        }
        syntheticCaptures++
        return H1ObservedFacts(state.basis,p.facts,p.profiles,NativeExecutionAdmission.expected(state,command)) // explicitly armed synthetic probe
    }
}

/** Real local helper/Git setup with public deterministic verifier vectors, never live keys. */
class LocalH1SetupTest {
    @Test fun launcherPreDiscoveryPreparationIsIdempotentAndPreservesUserBytesAndUnsafeGit() {
        val root=Files.createTempDirectory("writhelm-launch-preparation-")
        val workspace=Files.createDirectory(root.resolve("project"));val client=root.resolve("client")
        val git=Path.of("/usr/bin/git").toRealPath();val pin=Digest.of(Files.readAllBytes(git))
        Files.writeString(workspace.resolve("owner.txt"),"do not stage or commit\n")
        assertEquals(workspace,dev.writhelm.service.prepareAutomaticClientProject(workspace,client,git,pin))
        val head=Files.readAllBytes(workspace.resolve(".git/refs/heads/main"))
        assertEquals(workspace,dev.writhelm.service.prepareAutomaticClientProject(workspace,client,git,pin))
        assertContentEquals(head,Files.readAllBytes(workspace.resolve(".git/refs/heads/main")))
        assertEquals("do not stage or commit\n",Files.readString(workspace.resolve("owner.txt")))
        assertFalse(Files.exists(workspace.resolve(".git/index")),"User files must not be staged")
        val inside=Files.createDirectories(client.resolve("project"))
        assertFails {dev.writhelm.service.prepareAutomaticClientProject(inside,client,git,pin)}
        assertFalse(Files.exists(inside.resolve(".git")))
        val corrupt=Files.createDirectory(root.resolve("corrupt"));Files.writeString(corrupt.resolve(".git"),"ambiguous metadata")
        assertFails {dev.writhelm.service.prepareAutomaticClientProject(corrupt,client,git,pin)}
        assertEquals("ambiguous metadata",Files.readString(corrupt.resolve(".git")))
    }
    @Test fun compositionTestOwnedStoreEnrollsOneControllerWithoutImportingFixtureAuthority() = runEnrolledComposition(false)
    internal fun runEnrolledComposition(installedChecks:Boolean,registeredChecks:Boolean=false,recoveryOutcome:String?=null,dispatchChecks:Boolean=false,readHoldChecks:Boolean=false,remediationChecks:Boolean=false,reviewerSurfaceChecks:Boolean=false) {
        val root=if(installedChecks)Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"enrolled-composition-")
            else Files.createTempDirectory("writhelm-composition-enrollment-")
        // Match managed launch: user source is outside the client/installation root.
        val workspace=if(installedChecks)Files.createTempDirectory(root.parent,"enrolled-project-")
            else Files.createDirectory(root.resolve("project"))
        val worktrees=root.resolve("private/check-worktrees")
        val checkDeployment=if(installedChecks){
            LinuxConnectedCustody(worktrees)
            val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE"))).toRealPath()
            val env=Path.of("/usr/bin/env").toRealPath()
            fun pin(file:Path)=obj("path" to str(file.toString()),"sha256" to digest(dev.writhelm.service.projectCheckExecutableDigest(file)))
            dev.writhelm.service.AutomaticProjectCheckDeployment(root.resolve("installation")).also {deployment->
                deployment.approve(listOf(obj("formatVersion" to num(1),"platform" to str("linux"),"sourceRoot" to str(workspace.toString()),
                    "envExecutable" to pin(env),"checks" to JArray(listOf(obj("id" to str("project-node-test"),"executable" to pin(node),
                        "args" to JArray(listOf(str("--test"),str("tests/candidate.test.cjs"))),
                        "environment" to obj("WRITHELM_EXECUTION_MARKER" to str("{outputRoot}/executions.log")),
                        "timeoutMillis" to num(if(recoveryOutcome=="unknown")2000 else 10000),"outputBytes" to num(32768)))))))
            }
        }else null
        var controllerRoot:Path?=null
        val observations=CompositionSyntheticFacts()
        var captured:H1BoundState?=null
        val qualifiedProfilesByRequest=java.util.concurrent.ConcurrentHashMap<java.util.UUID,Set<WorkReference>>()
        val options=H1Options(H1RuntimeFacts {state,command->
            captured=state;observations.capture(state,command).also {qualifiedProfilesByRequest[command.requestKey]=it.qualifiedProfiles}
        },workModelVersion=4,candidateAssemblyProof={operation->
            controllerRoot?.let {area->try {
                str(protectedRuntimeBytes(area.resolve("native"),
                    area.resolve("native/candidate-receipt-${Digest.of(operation.toString().toByteArray()).hex}.json"),2*1024*1024).decodeToString())
            }catch(_:Exception){null}}
        })
        dev.writhelm.service.LinuxTestClientInstance.open(root.resolve("private/instance"),options).use {instance->
            val installed=if(registeredChecks)InstalledCompositionHarness(root,workspace,instance,requireNotNull(checkDeployment))else null
            observations.trustedFallback=installed?.trustedFacts
            try {
            val git=Path.of("/usr/bin/git").toRealPath()
            val broker=installed?.broker ?: LinuxTestClientEnrollment(instance.app,root.resolve("private/enrollment"),instance.storeId,
                instance.anchorProjectId,instance.ownerToken,LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git)))) {session->
                // Native-session observation is explicitly synthetic in this harness unit test;
                // the installed composition journey must replace it with TestClientNativeSessions.
                LinuxTestClientEnrollment.ObservedTestClientSession(session,workspace,null)
            }
            val controllerSession=installed?.start() ?: "ses_model_free_composition"
            val enrolled=try {broker.session(controllerSession)}catch(error:Exception){
                throw AssertionError("Test-owned composition enrollment held at $workspace",error)
            }
            controllerRoot=root.resolve("private/controllers/controller-${enrolled.controllerId}")
            LinuxConnectedCustody(controllerRoot!!)
            // Same registrar provisioner as ordinary enrollment; no hand-authored bootstrap profile.
            if(installed==null)checkDeployment?.provision(enrolled.workRoot,controllerRoot!!,worktrees)
            else installed.awaitConnected(enrolled.controllerId)
            assertEquals(enrolled.controllerId,broker.session(controllerSession).controllerId)
            val capture=instance.app.coordinationCapture(enrolled.token,enrolled.projectId)
            assertTrue(capture.work.records.isEmpty(),"Never import JointFixture tasks/results/authority")
            val command=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,
                CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),
                    CRecordRetrospectiveData(WorkKey.TaskRevision(enrolled.projectId,java.util.UUID.randomUUID(),0),
                        WAssignmentSubject(java.util.UUID.randomUUID(),0),null,"no-significant-new-findings",emptyList()),emptyList())))
            val codec=WorkCodec()
            val denied=instance.app.execute(enrolled.token,codec.encodeCommand(command))
            assertFalse(denied is dev.writhelm.core.store.CommandResult.Settled &&
                denied.receipt.outcome is dev.writhelm.core.store.LogicalOutcome.Success,
                "Controller credentials cannot submit executor records, even in the test-owned service")
            val prototype=revision(91001)
            val revision=dev.writhelm.core.knowledge.DocumentRevision(
                dev.writhelm.core.knowledge.RevisionKey(dev.writhelm.core.knowledge.DocumentKey(enrolled.projectId,java.util.UUID.randomUUID()),java.util.UUID.randomUUID()),
                prototype.content,prototype.structure)
            fun core(payload:CommandPayload,token:String=enrolled.token):dev.writhelm.core.store.CommandResult.Settled {
                val result=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(token,
                    CommandCodec().encode(CoreCommand(java.util.UUID.randomUUID(),enrolled.projectId,payload))))
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(result.receipt.outcome,
                    "${payload.operation}: ${(result.receipt.outcome as? dev.writhelm.core.store.LogicalOutcome.Rejected)?.code}")
                return result
            }
            core(CommandPayload.Propose(revision))
            val packet=packet(listOf(dev.writhelm.core.workflow.ReviewItem(java.util.UUID.randomUUID(),revision.x()),
                dev.writhelm.core.workflow.ReviewItem(java.util.UUID.randomUUID(),revision.contextReference())),listOf(revision.contextReference()))
            val approvalId=java.util.UUID.randomUUID()
            core(CommandPayload.Request(approvalId,packet))
            val requestDigest=dev.writhelm.core.workflow.ApprovalRequest(approvalId,enrolled.projectId,enrolled.principalId,packet,
                packet.items.associate {it.target.subject() to 0L}).digest
            core(CommandPayload.Resolve(approvalId,requestDigest,packet.digest,
                dev.writhelm.core.workflow.StructuredAnswer.Approve),broker.project(workspace).ownerToken)
            val assessmentId=java.util.UUID.randomUUID()
            core(CommandPayload.Assess(dev.writhelm.core.workflow.ConsistencyAssessment(assessmentId,enrolled.projectId,
                listOf(revision.x(),revision.contextReference()),"Test-only canonical seed",emptyList(),emptyList())))
            val manifestId=java.util.UUID.randomUUID()
            core(CommandPayload.Pin(manifestId,java.util.UUID.randomUUID(),listOf(revision.x()),listOf(revision.contextReference()),emptyList(),assessmentId))
            val profileKey=WorkKey.Profile(enrolled.projectId,java.util.UUID.randomUUID(),0)
            val profile=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CCreateProfileOperation(CCreateProfile(profileKey,
                WProfile("test-only-no-provider",listOf(WSourceVariant0Value(WSourceVariant0("knowledge",revision.x()))),WorkModelReference("synthetic","no-call",null),"no-call","bounded",
                    8192,512,512,512,"native","synthetic","E-COOP-1",emptyList(),"linux","qualified"),emptyList())))
            val published=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,codec.encodeCommand(profile)))
            assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(published.receipt.outcome)
            val taskKey=WorkKey.TaskRevision(enrolled.projectId,java.util.UUID.randomUUID(),0)
            fun gitObject(expression:String):String {
                val child=ProcessBuilder(git.toString(),"-C",workspace.toString(),"rev-parse",expression).start()
                val text=child.inputStream.readAllBytes().decodeToString().trim()
                assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertEquals(0,child.exitValue());return text
            }
            val snapshot=WSnapshot(java.util.UUID.randomUUID(),WorkGitOid("sha1",gitObject("HEAD")),
                WorkGitOid("sha1",gitObject("HEAD^{tree}")),null,java.util.UUID.randomUUID())
            val manifestRecord=dev.writhelm.core.codec.CoreRecordCodec.records(assertNotNull(captured).core.snapshot())
                .single {it.family=="manifests" && uuid(it.key)==manifestId}
            val base=WBase(listOf(snapshot),manifestId,Digest.of(StrictJson.encode(
                dev.writhelm.core.codec.CoreRecordCodec.envelope(manifestRecord.family,manifestRecord.key,manifestRecord.payload()))))
            val planKey=WorkKey.Revision(enrolled.projectId,java.util.UUID.randomUUID(),0)
            val criterion=WCriterion(java.util.UUID.randomUUID(),"Run approved candidate test",emptyList())
            val verification=WVerificationPlans(taskKey,null,listOf(criterion),listOf(WGate(java.util.UUID.randomUUID(),
                listOf(criterion.id),"pure",false,"acceptance",null,false,"Run approved project test")),"initial",emptyList())
            val task=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CCreateTaskRevisionOperation(CCreateTaskRevision(taskKey,
                WTaskRevisions("research","executable","Test-only composition seed; no model calls",base,
                    WScope("disposable project","isolated result worktree","no production qualification",emptyList()),
                    listOf("synthetic execution facts are not production evidence"),emptyList(),
                    listOf(criterion),listOf("unknown external outcome"),"candidate and check receipt",profileKey,planKey,emptyList(),
                    emptyList()),CCreateTaskRevisionNewVerificationPlan(planKey,verification),emptyList())))
            val taskPublished=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,codec.encodeCommand(task)))
            assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(taskPublished.receipt.outcome)
            val replay=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,codec.encodeCommand(task)))
            assertEquals(taskPublished.receipt.identity,replay.receipt.identity)
            assertEquals(taskPublished.receipt.fingerprint,replay.receipt.fingerprint)
            assertEquals(taskPublished.receipt.validatedAt,replay.receipt.validatedAt,
                "Original command replay must reuse its canonical receipt basis")
            var fixtureFacts=UnavailableH1RuntimeFacts.capture(assertNotNull(captured),command).facts.copy(
                retainedSnapshots=setOf(snapshot),knowledgeSources=setOf(revision.x()))
            var fixtureProfiles=emptySet<WorkReference>()
            fun publish(command:WorkCommand,who:String=enrolled.token):WorkReference? {
                synchronized(instance.app){ // Exact-basis test permit and command share APP's monitor.
                val current=instance.app.coordinationCapture(enrolled.token,enrolled.projectId)
                observations.arm(CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,current.basis),command,
                    fixtureFacts,fixtureProfiles)
                val executed=instance.app.execute(who,codec.encodeCommand(command))
                val result=assertIs<dev.writhelm.core.store.CommandResult.Settled>(executed,
                    "${command.operation.name}: ${(executed as? dev.writhelm.core.store.CommandResult.Refused)?.problem}")
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(result.receipt.outcome,
                    "${command.operation.name}: ${(result.receipt.outcome as? dev.writhelm.core.store.LogicalOutcome.Rejected)?.code}")
                val success=result.receipt.outcome
                val row=H1Catalog.result(success.operation,success.result);val family=H1Catalog.family(row.family)
                val key=WorkIdentityCodec().decodeKey(family,StrictJson.encode(row.key))
                if(family in setOf(WorkFamily.ASSIGNMENTS,WorkFamily.NATIVE_INTENTS))return null
                return instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work.reference(family,key)
                }
            }
            val applicability=WorkKey.Revision(enrolled.projectId,java.util.UUID.randomUUID(),0)
            val source=WSourceVariant1Value(WSourceVariant1("artifact",snapshot))
            publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CReviseApplicabilityOperation(
                CReviseApplicability(applicability,WApplicability(null,emptyList(),listOf(source)),emptyList()))))
            val inputKey=WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID())
            val input=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CPinInputManifestOperation(CPinInputManifest(inputKey,
                WInputManifests(taskKey,base,applicability,listOf(WManifestItem(source,true,"exact test input",emptyList())),source,emptyList()),emptyList())))))
            val profileRef=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work.reference(WorkFamily.PROFILES,profileKey)
            fixtureFacts=fixtureFacts.copy(verifiedPackets=setOf(input))
            fixtureProfiles=setOf(profileRef) // Explicit test-only profile admission, never registry evidence.
            val assignmentId=java.util.UUID.randomUUID();val workspaceId=java.util.UUID.randomUUID()
            val intentId=java.util.UUID.randomUUID();val correlationId=java.util.UUID.randomUUID()
            val intended=WExecutionIntent(enrolled.controllerId,java.util.UUID.randomUUID(),"executor-v1",
                installed?.host(enrolled.controllerId) ?: java.util.UUID.randomUUID(),"ses_synthetic_composition_executor")
            val reserve=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CReserveAssignmentOperation(CReserveAssignment(
                assignmentId,taskKey,input,profileKey,workspaceId,listOf(CReserveAssignmentResourcesItem(
                    WorkKey.Resource(enrolled.projectId,java.util.UUID.randomUUID()),null)),emptyList(),intentId,correlationId,emptyList(),intended)))
            publish(reserve)
            val worker=java.util.UUID.randomUUID();val workerKey=java.util.UUID.randomUUID();val delegation=java.util.UUID.randomUUID()
            val secret=ByteArray(32){13}
            val verifier=dev.writhelm.core.auth.CredentialVerifier.digest(instance.storeId,enrolled.projectId,worker,workerKey,secret)
            val register=Auth3Command(java.util.UUID.randomUUID(),enrolled.projectId,Auth3Payload.RegisterDelegation(delegation,
                DelegationBinding(enrolled.controllerId,enrolled.projectId,worker,workerKey,taskKey.taskId,assignmentId,
                    intended.executionId,0,DelegationTemplate.EXECUTOR,emptyList()),0,verifier))
            val registered=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,Auth3CommandCodec().encode(register)))
            assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(registered.receipt.outcome)
            val execution=WExecution(intended.executionId,worker,workerKey,intended.nativeHostId,intended.nativeSessionId,workspaceId)
            val subject=WAssignmentSubject(assignmentId,0)
            fixtureFacts=fixtureFacts.copy(native=mapOf(intentId to WorkNativeObservation(subject,intentId,correlationId,
                "prepare","confirmed",listOf(source),execution,true,"reserved")))
            publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CBindAssignmentOperation(CBindAssignment(assignmentId,1,
                intentId,0,intended.nativeHostId,intended.nativeSessionId,workspaceId,listOf(source),emptyList()))))
            if(dispatchChecks){
                val bound=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
                val binding=bound.reference(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(enrolled.projectId,assignmentId,0))
                val selection=obj("assignment" to obj("family" to str("assignments"),"key" to obj("projectId" to uuid(enrolled.projectId),
                    "id" to uuid(assignmentId)),"generation" to JNumber(bound.assignment(assignmentId).generation)),
                    "binding" to StrictJson.decode(WorkIdentityCodec().encodeReference(binding)))
                val start=(requireNotNull(installed).invoke(controllerSession,"writhelm_start_worker",obj(
                    "json" to str(StrictJson.encode(selection).decodeToString()))) as JObject).entries
                assertEquals(str("start-held"),start.getValue("phase"))
                assertTrue(start.getValue("reason").string().contains("No qualified execution deployment"))
                assertTrue(Files.list(controllerRoot!!.resolve("native")).use {paths->paths.noneMatch {it.fileName.toString().startsWith("initial-driver-")}})
            }
            val startId=java.util.UUID.randomUUID();val startCorrelation=java.util.UUID.randomUUID()
            publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRequestNativeTransitionOperation(
                CRequestNativeTransition(assignmentId,2,startId,startCorrelation,"start",null,emptyList()))))
            fixtureFacts=fixtureFacts.copy(native=mapOf(startId to WorkNativeObservation(subject,startId,startCorrelation,
                "start","confirmed",listOf(source),execution,false,"bound")))
            publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CObserveNativeTransitionOperation(
                CObserveNativeTransition(assignmentId,3,startId,0,"confirmed",listOf(source),emptyList()))))
            val workerToken="wh1.${instance.storeId}.$workerKey."+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
            val retro=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRecordRetrospectiveOperation(
                CRecordRetrospective(WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),
                    CRecordRetrospectiveData(taskKey,subject,null,"no-significant-new-findings",emptyList()),emptyList()))),workerToken))
            // Mechanically authored test artifact, not model-produced implementation evidence.
            Files.writeString(workspace.resolve("result.txt"),"implemented\n")
            Files.createDirectory(workspace.resolve("tests"))
            Files.writeString(workspace.resolve("tests/candidate.test.cjs"),
                "if(process.env.WRITHELM_EXECUTION_MARKER)require('node:fs').appendFileSync(process.env.WRITHELM_EXECUTION_MARKER,'executed\\n');\n"+
                    "require('node:test')('exact project result',()=>require('node:assert/strict').equal(require('node:fs').readFileSync('result.txt','utf8'),'implemented\\n'))\n"+
                    if(recoveryOutcome=="unknown")"setTimeout(()=>{},10000)\n" else "")
            fun runGit(vararg args:String):String {
                val builder=ProcessBuilder(git.toString(),"-c","core.hooksPath=/dev/null","-c","commit.gpgSign=false",
                    "-c","user.name=Composition test author","-c","user.email=fixture@writhelm.invalid","-C",workspace.toString(),*args)
                builder.environment().clear();builder.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","LANG" to "C",
                    "GIT_CONFIG_NOSYSTEM" to "1","GIT_CONFIG_GLOBAL" to "/dev/null"))
                val child=builder.start();val text=child.inputStream.readAllBytes().decodeToString()
                assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertEquals(0,child.exitValue());return text
            }
            runGit("add","result.txt","tests/candidate.test.cjs");runGit("commit","-m","Test-only mechanically authored project result")
            val resultSnapshot=snapshot.copy(commit=WorkGitOid("sha1",gitObject("HEAD")),tree=WorkGitOid("sha1",gitObject("HEAD^{tree}")),
                retainedLocatorId=java.util.UUID.randomUUID())
            var expectedResultText="implemented\n"
            runGit("update-ref","refs/writhelm/results/${resultSnapshot.retainedLocatorId}",resultSnapshot.commit!!.hex,"0".repeat(40))
            val diff=dev.writhelm.core.knowledge.Utf8Content.fromText(runGit("diff","--no-ext-diff","--no-textconv","--no-color",
                "--binary","--full-index",snapshot.commit!!.hex,resultSnapshot.commit.hex,"--")+"\n")
            fixtureFacts=fixtureFacts.copy(retainedSnapshots=setOf(snapshot,resultSnapshot),
                submittedDiffs=setOf(WorkSubmittedDiff(taskKey,listOf(resultSnapshot),WorkBodyReference(diff.digest,diff.sizeBytes))))
            val submit=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CSubmitResultOperation(
                CSubmitResult(WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),CSubmitResultData(taskKey,subject,input,listOf(resultSnapshot),
                    WorkBodyReference(diff.digest,diff.sizeBytes),listOf(criterion.id),emptyList(),retro,emptyList()),
                    listOf(CSubmitResultBodiesItem(diff.digest,diff.text())))))
            val controllerAttempt=instance.app.execute(enrolled.token,codec.encodeCommand(submit.copy(requestKey=java.util.UUID.randomUUID())))
            assertFalse(controllerAttempt is dev.writhelm.core.store.CommandResult.Settled &&
                controllerAttempt.receipt.outcome is dev.writhelm.core.store.LogicalOutcome.Success)
            val resultRef=assertNotNull(publish(submit,workerToken))
            assertEquals(WorkFamily.RESULTS,resultRef.identity.family)
            val publishedResult=(instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work.resolve(resultRef).payload as WResultsPayload).value
            assertEquals(worker,publishedResult.producer.principalId)
            assertEquals(listOf(resultSnapshot),publishedResult.artifacts)
            val candidatePlan=DispatchPlanCandidate(enrolled.projectId,java.util.UUID.randomUUID(),enrolled.controllerId,
                listOf(DispatchPlanEntry(taskKey,input,profileKey,reserve.operation.let {
                    (it as CReserveAssignmentOperation).value.resources.map {item->item.resource}
                },java.util.UUID.randomUUID(),java.util.UUID.randomUUID())),
                listOf(DispatchCandidateCheck("required-project-test",listOf("project-check-v1","project-node-test"),false,emptyList())))
            val planPublication=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,
                DispatchPlanCommandCodec().encode(DispatchPlanCommand(java.util.UUID.randomUUID(),candidatePlan))))
            assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(planPublication.receipt.outcome)
            val planRef=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work.reference(
                WorkFamily.DISPATCH_PLANS,WorkKey.Id(enrolled.projectId,candidatePlan.planId))
            val selectedWork=instance.app.coordinationCapture(enrolled.token,enrolled.projectId)
            val projectionProbe=WorkCommand((resultRef.identity.key as WorkKey.Id).id,enrolled.projectId,
                CCreateTaskRevisionOperation(CCreateTaskRevision(taskKey,selectedWork.work.task(taskKey),null,emptyList())))
            observations.disarm()
            assertFailsWith<WorkFault> {instance.app.nativeCandidateDelivery(enrolled.token,enrolled.projectId,enrolled.controllerId,intended.nativeHostId,planRef)}
            observations.arm(CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,selectedWork.basis),
                projectionProbe,fixtureFacts,fixtureProfiles)
            val selection=instance.app.nativeCandidateDelivery(enrolled.token,enrolled.projectId,enrolled.controllerId,intended.nativeHostId,planRef) as JObject
            assertEquals(str("ready"),selection.entries.getValue("kind"))
            val selected=StrictJson.decode(selection.entries.getValue("selectedJSON").string().toByteArray()).list()
            assertEquals(listOf(resultRef),selected.map {WorkIdentityCodec().decodeReference(StrictJson.encode(it))})
            val mandatory=instance.app.nativeCandidateTestPlan(enrolled.token,enrolled.projectId,enrolled.controllerId,intended.nativeHostId,planRef) as JObject
            assertTrue(mandatory.entries.getValue("planJSON").string().contains("project-node-test"))
            if(registeredChecks){
                val client=requireNotNull(installed)
                Files.writeString(workspace.resolve("owner-dirty.txt"),"registered-client owner bytes\n")
                val originalIndex=Files.readAllBytes(workspace.resolve(".git/index"))
                val discovery=(client.invoke(controllerSession,"writhelm_project_checks",obj()) as JObject).entries
                assertEquals(JBoolean(true),discovery.getValue("available"))
                val activation=(client.invoke(controllerSession,"writhelm_activate_candidate",obj(
                    "planRefJSON" to str(WorkIdentityCodec().encodeReference(planRef).decodeToString()))) as JObject).entries
                assertEquals(str("candidate-delivery-activated"),activation.getValue("phase"))
                val profile=(activation.getValue("profile") as JObject).entries
                val assembly=uuid(profile.getValue("operationId"));val testPlan=uuid(profile.getValue("testPlanId"))
                val observer=dev.writhelm.service.NativeCandidateReviewReceipts(controllerRoot!!.resolve("native"))
                val nativeCustody=controllerRoot!!.resolve("native")
                fun testResult():Map<String,JsonValue>?=Files.list(nativeCustody).use {paths->
                    paths.filter {it.fileName.toString().startsWith("candidate-test-result-")}.findFirst().orElse(null)?.let {
                        (StrictJson.decode(Files.readAllBytes(it)) as JObject).entries
                    }
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
                var receipt=observer.observe(enrolled.projectId,assembly,testPlan,candidatePlan)
                while(testResult()==null&&System.nanoTime()<deadline){Thread.sleep(100);receipt=observer.observe(enrolled.projectId,assembly,testPlan,candidatePlan)}
                val outcome=(assertNotNull(testResult(),"Check has no retained outcome; inspect $root").getValue("result") as JObject).entries.getValue("outcome").string()
                assertEquals(recoveryOutcome ?: "passed",outcome)
                receipt=observer.observe(enrolled.projectId,assembly,testPlan,candidatePlan)
                if(recoveryOutcome=="unknown")assertNull(receipt,"Unknown is never ready-for-review")
                else assertEquals(resultSnapshot.tree,assertNotNull(receipt).candidate.tree)
                assertNull(observer.observe(enrolled.projectId,assembly,testPlan,candidatePlan.copy(candidateChecks=
                    candidatePlan.candidateChecks.map {it.copy(command=listOf("project-check-v1","wrong-check"))})))
                val repeated=(client.invoke(controllerSession,"writhelm_activate_candidate",obj(
                    "planRefJSON" to str(WorkIdentityCodec().encodeReference(planRef).decodeToString())),cached=true) as JObject).entries
                assertContentEquals(StrictJson.encode(activation.getValue("profile")),StrictJson.encode(repeated.getValue("profile")))
                assertEquals(receipt,observer.observe(enrolled.projectId,assembly,testPlan,candidatePlan))
                if(recoveryOutcome!=null){
                    fun retainedExecution()=Files.list(nativeCustody).use {paths->paths.filter {path->
                        listOf("candidate-activation-","candidate-test-","project-check-").any {path.fileName.toString().startsWith(it)}
                    }.toList().associate {it.fileName.toString() to Digest.of(Files.readAllBytes(it))}}
                    val originalEvidence=retainedExecution()
                    assertTrue(originalEvidence.keys.any {it.startsWith("project-check-job-")},"Native original job ID must be retained")
                    val originalJob=Files.list(nativeCustody).use {paths->paths.filter {it.fileName.toString().startsWith("project-check-job-")}.findFirst().orElseThrow()}
                    val job=(StrictJson.decode(Files.readAllBytes(originalJob)) as JObject).entries
                    val originalResult=assertNotNull(testResult())
                    val run=(StrictJson.decode(originalResult.getValue("original").string().toByteArray()) as JObject).entries.getValue("runId")
                    assertEquals(run,job.getValue("runId"))
                    val workParent=Path.of(job.getValue("cwd").string()).parent
                    val marker=workParent.resolve("output-${run.string()}/executions.log")
                    assertEquals("executed\n",Files.readString(marker),"The real project command must have entered exactly once before restart")
                    fun areas()=Files.list(workParent).use {paths->paths.map {it.fileName.toString()}.sorted().toList()}
                    val originalAreas=areas()
                    val processes=client.restart(controllerSession)
                    var resumed=(client.invoke(controllerSession,"writhelm_activate_candidate",obj(
                        "planRefJSON" to str(WorkIdentityCodec().encodeReference(planRef).decodeToString()))) as JObject).entries
                    val resumeDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                    while(resumed.getValue("deliveryStatus")==JNull&&System.nanoTime()<resumeDeadline){
                        Thread.sleep(100)
                        resumed=(client.invoke(controllerSession,"writhelm_activate_candidate",obj(
                            "planRefJSON" to str(WorkIdentityCodec().encodeReference(planRef).decodeToString())),cached=true) as JObject).entries
                    }
                    val resumedPhase=(resumed.getValue("deliveryStatus") as JObject).entries.getValue("phase").string()
                    assertEquals(if(outcome=="unknown")"tests-held" else "delivery-held",resumedPhase,
                        "Wait for an actual resumed delivery tick; neither unknown checks nor absent reviewer qualification are success")
                    assertContentEquals(StrictJson.encode(activation.getValue("profile")),StrictJson.encode(resumed.getValue("profile")))
                    client.invoke(controllerSession,"writhelm_project_checks",obj())
                    assertEquals(enrolled.controllerId,broker.session(controllerSession).controllerId)
                    assertEquals(originalEvidence,retainedExecution(),"Normal native restart/resume cannot mint a run/job or rewrite unknown outcomes")
                    assertEquals("executed\n",Files.readString(marker),"The actual test command must not execute again")
                    assertEquals(originalAreas,areas(),"Restart cannot allocate a second check/output area")
                    assertEquals(outcome,(assertNotNull(testResult()).getValue("result") as JObject).entries.getValue("outcome").string())
                    assertEquals(receipt,observer.observe(enrolled.projectId,assembly,testPlan,candidatePlan))
                    Files.write(root.resolve("recovery-evidence.json"),StrictJson.encode(obj("sessionID" to str(controllerSession),
                        "controllerId" to uuid(enrolled.controllerId),"assemblyId" to uuid(assembly),"testPlanId" to uuid(testPlan),
                        "runId" to run,"nativeJobId" to job.getValue("id"),"outcomeBefore" to str(outcome),"outcomeAfter" to str(outcome),
                        "retainedExecutionUnchanged" to JBoolean(true),"nativeProcessRestarted" to JBoolean(true),
                        "actualCommandEntriesBefore" to num(1),"actualCommandEntriesAfter" to num(1),
                        "nativePidBefore" to JNumber(processes.first),"nativePidAfter" to JNumber(processes.second),"postResumePhase" to str(resumedPhase),
                        "syntheticCallContext" to JBoolean(true),"syntheticExecutionFacts" to JBoolean(true),
                        "modelCalls" to num(0),"productionQualified" to JBoolean(false),"activeExecutorFencingQualified" to JBoolean(false))))
                }
                assertEquals("registered-client owner bytes\n",Files.readString(workspace.resolve("owner-dirty.txt")))
                assertContentEquals(originalIndex,Files.readAllBytes(workspace.resolve(".git/index")))
                assertEquals(resultSnapshot.commit.hex,gitObject("HEAD"))
                Files.write(root.resolve("composition-evidence.json"),StrictJson.encode(obj("storeId" to uuid(instance.storeId),
                    "controllerId" to uuid(enrolled.controllerId),"sessionID" to str(controllerSession),"registeredToolInvoked" to JBoolean(true),
                    "syntheticCallContext" to JBoolean(true),"syntheticExecutionFacts" to JBoolean(true),
                    "postCheckDeliveryStatus" to repeated.getValue("deliveryStatus"),
                    "projectProfile" to str("initially-empty-prepared-before-native"),"emptyFolderPreDiscoveryOrderingVerified" to JBoolean(true),
                    "previouslyCachedNonGitRecoveryQualified" to JBoolean(false),
                    "syntheticNativeEnrollmentObservation" to JBoolean(false),"productionQualified" to JBoolean(false),
                    "modelCalls" to num(0),"integrationAllowed" to JBoolean(false),"assemblyId" to uuid(assembly),
                    "testPlanId" to uuid(testPlan),"checkId" to str("required-project-test"))))
            }else if(installedChecks){
                val area=controllerRoot
                val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE"))).toRealPath()
                assertNotNull(checkDeployment!!.observe(workspace,area,worktrees))
                val template=protectedRuntimeBytes(area.resolve("bootstrap"),
                    area.resolve("bootstrap/project-check-template-${Digest.of("current".toByteArray()).hex}.json"),1024*1024)
                val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use {it.localPort}
                val origin="http://127.0.0.1:$port";val proof=ByteArray(32){29}
                val service=WrithelmService(instance.app)
                val endpoint=service.nativeEndpoint(enrolled.projectId,enrolled.controllerId,intended.nativeHostId,proof)
                val adapter=HttpAdapter(service,HttpProfile(origin),nativeEndpoint=endpoint)
                val server=embeddedServer(CIO,host="127.0.0.1",port=port){adapter.install(this)}.start(wait=false)
                try {
                    Files.writeString(workspace.resolve("owner-dirty.txt"),"owner bytes must survive\n")
                    val originalIndex=Files.readAllBytes(workspace.resolve(".git/index"))
                    val config=root.resolve("checks-config.json")
                    val json=obj("sourceRoot" to str(workspace.toString()),"projectId" to uuid(enrolled.projectId),
                        "controllerSessionID" to str("ses_model_free_composition"),
                        "base" to encodeWSnapshot(snapshot),"result" to encodeWSnapshot(resultSnapshot),"installedNative" to JBoolean(true),
                        "cli" to str(requireNotNull(System.getenv("WRITHELM_NATIVE_CLI"))),"projectCheckTemplateJSON" to str(template.decodeToString()),
                        "selectedJSON" to selection.entries.getValue("selectedJSON"),"service" to obj("origin" to str(origin),
                            "proof" to str(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proof)),"storeId" to uuid(instance.storeId),
                            "controllerId" to uuid(enrolled.controllerId),"hostId" to uuid(intended.nativeHostId),"token" to str(enrolled.token),
                            "planRefJSON" to str(WorkIdentityCodec().encodeReference(planRef).decodeToString())))
                    Files.write(config,StrictJson.encode(json),java.nio.file.StandardOpenOption.CREATE_NEW)
                    Files.setPosixFilePermissions(config,PosixFilePermissions.fromString("rw-------"))
                    val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER"))).resolve("tests/enrolled-candidate-activation-client.js")
                    val child=ProcessBuilder(node.toString(),script.toString(),area.toString(),git.toString(),
                        Digest.of(Files.readAllBytes(git)).hex,config.toString()).redirectOutput(root.resolve("checks-output.json").toFile())
                        .redirectError(root.resolve("checks.log").toFile()).start()
                    assertTrue(child.waitFor(60,TimeUnit.SECONDS),"Native check driver outcome unknown; inspect $root")
                    assertEquals(0,child.exitValue(),"Same enrolled-store real checks failed; inspect $root")
                    val frame=StrictJson.decode(Files.readAllBytes(root.resolve("checks-output.json"))).fields("projectId","assemblyId","testPlanId","checks","candidateId")
                    val receipt=assertNotNull(dev.writhelm.service.NativeCandidateReviewReceipts(area.resolve("native")).observe(
                        enrolled.projectId,uuid(frame.getValue("assemblyId")),uuid(frame.getValue("testPlanId")),candidatePlan))
                    assertEquals("required-project-test",receipt.checks.single().checkId)
                    assertEquals(resultSnapshot.tree,receipt.candidate.tree,"Independent receipt must bind the exact selected candidate tree")
                    assertNull(dev.writhelm.service.NativeCandidateReviewReceipts(area.resolve("native")).observe(
                        enrolled.projectId,uuid(frame.getValue("assemblyId")),uuid(frame.getValue("testPlanId")),
                        candidatePlan.copy(candidateChecks=candidatePlan.candidateChecks.map {it.copy(command=listOf("project-check-v1","other-id"))})))
                    assertEquals("owner bytes must survive\n",Files.readString(workspace.resolve("owner-dirty.txt")))
                    assertContentEquals(originalIndex,Files.readAllBytes(workspace.resolve(".git/index")))
                    assertEquals(resultSnapshot.commit.hex,gitObject("HEAD"))
                    Files.write(root.resolve("composition-evidence.json"),StrictJson.encode(obj("storeId" to uuid(instance.storeId),
                        "controllerId" to uuid(enrolled.controllerId),"syntheticExecutionFacts" to JBoolean(true),
                        "syntheticNativeEnrollmentObservation" to JBoolean(true),"registeredToolInvoked" to JBoolean(false),
                        "activationAdapter" to str("CanonicalCandidateActivation/startCandidateDeliveryLoop"),
                        "productionQualified" to JBoolean(false),"modelCalls" to num(0),"integrationAllowed" to JBoolean(false),
                        "assemblyId" to frame.getValue("assemblyId"),"testPlanId" to frame.getValue("testPlanId"),
                        "candidateId" to frame.getValue("candidateId"),"checkId" to str(receipt.checks.single().checkId))))
                }finally{server.stop(0,1000)}
            }
            if(remediationChecks){
                val client=requireNotNull(installed);val area=controllerRoot!!
                val observer=dev.writhelm.service.NativeCandidateReviewReceipts(area.resolve("native"))
                val activationFile=Files.list(area.resolve("native")).use {paths->paths.filter {it.fileName.toString().startsWith("candidate-activation-")}.findFirst().orElseThrow()}
                val oldProfile=((StrictJson.decode(Files.readAllBytes(activationFile)) as JObject).entries.getValue("profile") as JObject).entries
                val oldAssembly=uuid(oldProfile.getValue("operationId"));val oldChecks=uuid(oldProfile.getValue("testPlanId"))
                val assessed=assertNotNull(observer.observe(enrolled.projectId,oldAssembly,oldChecks,candidatePlan))
                fixtureFacts=fixtureFacts.copy(retainedSnapshots=fixtureFacts.retainedSnapshots+assessed.candidate,approvedDecisions=setOf(revision.x()))
                fun task(kind:String,dependency:WDependency,clauses:List<WSource>,taskBase:WBase):Triple<WorkKey.TaskRevision,WorkReference,WorkKey.Revision>{
                    val key=WorkKey.TaskRevision(enrolled.projectId,java.util.UUID.randomUUID(),0)
                    val plan=WorkKey.Revision(enrolled.projectId,java.util.UUID.randomUUID(),0)
                    val criteria=listOf(criterion.copy(clauses=clauses))
                    val terms=selectedWork.work.task(taskKey).copy(kind=kind,objective="Test-only $kind iteration",base=taskBase,
                        dependencies=listOf(dependency),criteria=criteria,verificationPlan=plan,approvedDesign=listOf(revision.x()))
                    publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CCreateTaskRevisionOperation(CCreateTaskRevision(key,terms,
                        CCreateTaskRevisionNewVerificationPlan(plan,verification.copy(task=key,criteria=criteria)),emptyList()))))
                    val manifest=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CPinInputManifestOperation(
                        CPinInputManifest(WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),WInputManifests(key,taskBase,applicability,
                            (listOf<WSource>(source)+taskBase.code.map {WSourceVariant1Value(WSourceVariant1("artifact",it))}+
                                if(kind=="verification")listOf(WSourceVariant1Value(WSourceVariant1("artifact",assessed.candidate)))else emptyList())
                                .distinct().map {WManifestItem(it,true,"Test-only exact declared input",emptyList())},source,emptyList()),emptyList())))))
                    fixtureFacts=fixtureFacts.copy(verifiedPackets=fixtureFacts.verifiedPackets+manifest)
                    return Triple(key,manifest,plan)
                }
                data class Actor(val subject:WAssignmentSubject,val execution:WExecution,val token:String)
                fun actor(task:WorkKey.TaskRevision,manifest:WorkReference,template:String,subjects:List<WorkReference>):Actor {
                    val assignment=java.util.UUID.randomUUID();val workspace=java.util.UUID.randomUUID()
                    val intent=java.util.UUID.randomUUID();val correlation=java.util.UUID.randomUUID()
                    val intended=WExecutionIntent(enrolled.controllerId,java.util.UUID.randomUUID(),template,intended.nativeHostId,"ses_synthetic_"+assignment.toString().replace("-",""))
                    publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CReserveAssignmentOperation(CReserveAssignment(assignment,task,manifest,
                        profileKey,workspace,listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(enrolled.projectId,java.util.UUID.randomUUID()),null)),subjects,
                        intent,correlation,emptyList(),intended))))
                    val principal=java.util.UUID.randomUUID();val key=java.util.UUID.randomUUID();val secret=ByteArray(32){23}
                    val command=Auth3Command(java.util.UUID.randomUUID(),enrolled.projectId,Auth3Payload.RegisterDelegation(java.util.UUID.randomUUID(),
                        DelegationBinding(enrolled.controllerId,enrolled.projectId,principal,key,task.taskId,assignment,intended.executionId,0,
                            if(template=="reviewer-v1")DelegationTemplate.REVIEWER else DelegationTemplate.EXECUTOR,subjects),0,
                        dev.writhelm.core.auth.CredentialVerifier.digest(instance.storeId,enrolled.projectId,principal,key,secret)))
                    assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(assertIs<dev.writhelm.core.store.CommandResult.Settled>(
                        instance.app.execute(enrolled.token,Auth3CommandCodec().encode(command))).receipt.outcome)
                    val execution=WExecution(intended.executionId,principal,key,intended.nativeHostId,intended.nativeSessionId,workspace)
                    val subject=WAssignmentSubject(assignment,0)
                    fixtureFacts=fixtureFacts.copy(native=mapOf(intent to WorkNativeObservation(subject,intent,correlation,"prepare","confirmed",listOf(source),execution,true,"reserved")))
                    publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CBindAssignmentOperation(CBindAssignment(assignment,1,intent,0,
                        intended.nativeHostId,intended.nativeSessionId,workspace,listOf(source),emptyList()))))
                    val start=java.util.UUID.randomUUID();val startCorrelation=java.util.UUID.randomUUID()
                    publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,start,startCorrelation,"start",null,emptyList()))))
                    fixtureFacts=fixtureFacts.copy(native=mapOf(start to WorkNativeObservation(subject,start,startCorrelation,"start","confirmed",listOf(source),execution,false,"bound")))
                    publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,start,0,"confirmed",listOf(source),emptyList()))))
                    return Actor(subject,execution,"wh1.${instance.storeId}.$key."+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret))
                }
                val review=task("verification",WDependency(taskKey,"implemented",resultRef,emptyList(),"Exact prior result"),emptyList(),base)
                val reviewer=actor(review.first,review.second,"reviewer-v1",listOf(resultRef))
                assertNotEquals(worker,reviewer.execution.principalId);assertNotEquals(enrolled.principalId,reviewer.execution.principalId)
                val digest=Digest.of("SYNTHETIC reviewer source; no inference".toByteArray());val locator=java.util.UUID.randomUUID()
                fixtureFacts=fixtureFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(reviewer.execution.nativeHostId,reviewer.execution.nativeSessionId,
                    digest,digest,listOf("synthetic-review-message" to digest))))
                val cut=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRecordSourceCutOperation(CRecordSourceCut(
                    WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),WSourceCuts(review.first,reviewer.subject,reviewer.execution.nativeHostId,
                        reviewer.execution.nativeSessionId,profileKey,1,digest,digest,locator,listOf(WSourceCutsNativeMessagesItem("synthetic-review-message",digest)),emptyList(),emptyList()),emptyList()))),reviewer.token))
                val finding=WFinding(java.util.UUID.randomUUID(),"violation",WSourceVariant2Value(WSourceVariant2("work",resultRef)),
                    "Test-only expected revision", "remediated", "implemented",emptyList(),verification.gates.map {it.id},listOf((resultRef.identity.key as WorkKey.Id).id),true)
                val reportText=dev.writhelm.core.knowledge.Utf8Content.fromText("SYNTHETIC blocking finding; not semantic/model review")
                val report=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CSubmitVerificationReportOperation(CSubmitVerificationReport(
                    WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),CSubmitVerificationReportData(review.first,reviewer.subject,listOf(resultRef),review.third,
                        verification.gates.map {it.id},WSourceVariant2Value(WSourceVariant2("work",cut)),listOf(WSourceVariant1Value(WSourceVariant1("artifact",assessed.candidate))),
                        listOf(finding),emptyList(),WorkBodyReference(reportText.digest,reportText.sizeBytes),listOf("Synthetic lifecycle and source facts")),
                    listOf(CSubmitVerificationReportBodiesItem(reportText.digest,reportText.text()))))),reviewer.token))
                val remediation=task("remediation",WDependency(taskKey,"implemented",resultRef,emptyList(),"Remediate exact reviewed result"),
                    listOf(WSourceVariant2Value(WSourceVariant2("work",report))),base.copy(code=listOf(resultSnapshot)))
                val author=actor(remediation.first,remediation.second,"executor-v1",emptyList())
                expectedResultText="remediated\n";Files.writeString(workspace.resolve("result.txt"),expectedResultText)
                Files.writeString(workspace.resolve("tests/candidate.test.cjs"),Files.readString(workspace.resolve("tests/candidate.test.cjs")).replace("implemented","remediated"))
                runGit("add","result.txt","tests/candidate.test.cjs");runGit("commit","-m","Test-only finding remediation")
                val amended=resultSnapshot.copy(commit=WorkGitOid("sha1",gitObject("HEAD")),tree=WorkGitOid("sha1",gitObject("HEAD^{tree}")),retainedLocatorId=java.util.UUID.randomUUID())
                runGit("update-ref","refs/writhelm/results/${amended.retainedLocatorId}",amended.commit!!.hex,"0".repeat(40))
                val changes=dev.writhelm.core.knowledge.Utf8Content.fromText(runGit("diff","--no-ext-diff","--no-textconv","--binary","--full-index",resultSnapshot.commit.hex,amended.commit.hex,"--")+"\n")
                val retro=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRecordRetrospectiveOperation(CRecordRetrospective(
                    WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),CRecordRetrospectiveData(remediation.first,author.subject,null,"no-significant-new-findings",emptyList()),emptyList()))),author.token))
                fixtureFacts=fixtureFacts.copy(retainedSnapshots=fixtureFacts.retainedSnapshots+amended,
                    submittedDiffs=fixtureFacts.submittedDiffs+WorkSubmittedDiff(remediation.first,listOf(amended),WorkBodyReference(changes.digest,changes.sizeBytes)))
                val amendedResult=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CSubmitResultOperation(CSubmitResult(
                    WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),CSubmitResultData(remediation.first,author.subject,remediation.second,listOf(amended),
                        WorkBodyReference(changes.digest,changes.sizeBytes),listOf(criterion.id),emptyList(),retro,emptyList()),listOf(CSubmitResultBodiesItem(changes.digest,changes.text()))))),author.token))
                val plan=DispatchPlanCandidate(enrolled.projectId,java.util.UUID.randomUUID(),enrolled.controllerId,listOf(DispatchPlanEntry(remediation.first,
                    remediation.second,profileKey,listOf(WorkKey.Resource(enrolled.projectId,java.util.UUID.randomUUID())),java.util.UUID.randomUUID(),java.util.UUID.randomUUID())),candidatePlan.candidateChecks)
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,
                    DispatchPlanCommandCodec().encode(DispatchPlanCommand(java.util.UUID.randomUUID(),plan)))).receipt.outcome)
                val current=instance.app.coordinationCapture(enrolled.token,enrolled.projectId)
                val amendedPlan=current.work.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(enrolled.projectId,plan.planId))
                val oldProbe=WorkCommand((resultRef.identity.key as WorkKey.Id).id,enrolled.projectId,CCreateTaskRevisionOperation(CCreateTaskRevision(
                    taskKey,current.work.task(taskKey),null,emptyList())))
                observations.arm(CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,current.basis),oldProbe,fixtureFacts,fixtureProfiles)
                val oldCheckRecord=(StrictJson.decode(Files.readAllBytes(area.resolve("native/candidate-test-result-${Digest.of(assessed.checks.single().runId.toString().toByteArray()).hex}.json"))) as JObject).entries
                val oldPermit=assertFailsWith<WorkFault> {instance.app.nativeCandidateTestPermit(enrolled.token,enrolled.projectId,enrolled.controllerId,intended.nativeHostId,
                    assessed.assemblyJSON,amendedPlan,oldCheckRecord.getValue("original").string())}
                assertEquals(WorkProblem.BLOCKED_INPUT,oldPermit.problem,"Service admission must reject old result/check evidence for amended plan")
                val probe=WorkCommand((amendedResult.identity.key as WorkKey.Id).id,enrolled.projectId,CCreateTaskRevisionOperation(CCreateTaskRevision(
                    remediation.first,current.work.task(remediation.first),null,emptyList())))
                observations.arm(CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,current.basis),probe,fixtureFacts,fixtureProfiles)
                val activated=(client.invoke(controllerSession,"writhelm_activate_candidate",obj("planRefJSON" to str(WorkIdentityCodec().encodeReference(amendedPlan).decodeToString()))) as JObject).entries
                val profile=(activated.getValue("profile") as JObject).entries
                val assembly=uuid(profile.getValue("operationId"));val checks=uuid(profile.getValue("testPlanId"))
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
                var fresh=observer.observe(enrolled.projectId,assembly,checks,plan)
                while(fresh==null&&System.nanoTime()<deadline){Thread.sleep(100);fresh=observer.observe(enrolled.projectId,assembly,checks,plan)}
                val checked=assertNotNull(fresh,"Amended candidate needs real fresh checks; inspect $root")
                assertEquals(amended.tree,checked.candidate.tree);assertNotEquals(assessed.checks.single().runId,checked.checks.single().runId)
                val work=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
                assertTrue((work.resolve(report).payload as WVerificationReportsPayload).value.findings.single().blocking)
                assertTrue(work.records.values.none {(it.payload as? WVerificationReportsPayload)?.value?.subjects?.contains(amendedResult)==true},
                    "The amended result still needs a fresh independent review")
                val guarded=task("verification",WDependency(remediation.first,"reviewed",amendedResult,emptyList(),"Fresh review of amended result required"),emptyList(),base.copy(code=listOf(amended)))
                val guardCommand=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CReserveAssignmentOperation(CReserveAssignment(
                    java.util.UUID.randomUUID(),guarded.first,guarded.second,profileKey,java.util.UUID.randomUUID(),
                    listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(enrolled.projectId,java.util.UUID.randomUUID()),null)),emptyList(),
                    java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),emptyList(),WExecutionIntent(enrolled.controllerId,java.util.UUID.randomUUID(),
                        "executor-v1",intended.nativeHostId,"ses_synthetic_review_gate"))))
                synchronized(instance.app){
                    val now=instance.app.coordinationCapture(enrolled.token,enrolled.projectId)
                    observations.arm(CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,now.basis),guardCommand,fixtureFacts,fixtureProfiles)
                    val denied=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,codec.encodeCommand(guardCommand)))
                    assertEquals("NOT_READY",assertIs<dev.writhelm.core.store.LogicalOutcome.Rejected>(denied.receipt.outcome).code,
                        "A prior report cannot satisfy reviewed readiness for the amended result")
                }
                fixtureFacts=fixtureFacts.copy(retainedSnapshots=fixtureFacts.retainedSnapshots+checked.candidate)
                val assessedBytes=listOf(workspace.resolve("result.txt"),workspace.resolve("tests/candidate.test.cjs"),workspace.resolve(".git/index"))
                    .associateWith {Digest.of(Files.readAllBytes(it))}
                val repeatedReview=task("verification",WDependency(remediation.first,"implemented",amendedResult,emptyList(),
                    "Fresh exact amended result"),emptyList(),base.copy(code=listOf(checked.candidate)))
                val preparationId=java.util.UUID.randomUUID()
                val nativeArea=area.resolve("native")
                fun preparationEffects()=Files.list(nativeArea).use {paths->paths.filter {path->
                    listOf("session-","passive-","preparation-enroll-","preparation-bind-","initial-driver-","initial-dispatch-")
                        .any {path.fileName.toString().startsWith(it)}
                }.toList().associate {it.fileName.toString() to Digest.of(Files.readAllBytes(it))}}
                val beforeProductionPreparation=preparationEffects()
                val trustedCapturesBefore=client.trustedCaptureCount()
                val productionSelection=obj("task" to StrictJson.decode(WorkIdentityCodec().encodeKey(repeatedReview.first)),
                    "manifest" to StrictJson.decode(WorkIdentityCodec().encodeReference(repeatedReview.second)),
                    "profile" to StrictJson.decode(WorkIdentityCodec().encodeKey(profileKey)),
                    "resources" to JArray(listOf(obj("resource" to StrictJson.decode(WorkIdentityCodec().encodeKey(
                        WorkKey.Resource(enrolled.projectId,java.util.UUID.randomUUID()))),"expectedGeneration" to JNull))),
                    "reviewSubjects" to JArray(listOf(StrictJson.decode(WorkIdentityCodec().encodeReference(amendedResult)))))
                val preparation=(client.invoke(controllerSession,"writhelm_prepare_worker",obj("preparationId" to uuid(preparationId),
                    "json" to str(StrictJson.encode(productionSelection).decodeToString()))) as JObject).entries
                assertEquals(str("reservation-held"),preparation.getValue("phase"))
                val originalDriver=nativeArea.resolve("preparation-driver-${Digest.of(preparationId.toString().toByteArray()).hex}.json")
                val driver=(StrictJson.decode(Files.readAllBytes(originalDriver)) as JObject).entries
                val reserveBytes=driver.getValue("reserveJSON").string().toByteArray()
                val productionReserve=WorkCodec().decodeCommand(reserveBytes)
                val reservation=(productionReserve.operation as CReserveAssignmentOperation).value
                assertEquals("reviewer-v1",reservation.intendedExecution.template)
                assertEquals(repeatedReview.first,reservation.task)
                assertEquals(listOf(amendedResult),reservation.reviewSubjects)
                val actualReceipt=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,reserveBytes))
                assertTrue(actualReceipt.commandReplayed,"Read original production reservation outcome; do not replace its request")
                val qualificationRejection=assertIs<dev.writhelm.core.store.LogicalOutcome.Rejected>(actualReceipt.receipt.outcome)
                assertEquals("NOT_READY",qualificationRejection.code)
                assertEquals(emptySet(),qualifiedProfilesByRequest[productionReserve.requestKey],
                    "The actual production reserve capture must receive no synthetic profile qualification")
                assertTrue(client.trustedCaptureCount()>trustedCapturesBefore,"Unscoped production reservation must reach the connected trusted observer")
                val qualificationAudit=client.qualificationObserverEvidence(enrolled.controllerId) as JObject
                assertTrue(qualificationAudit.entries.getValue("configurationObservationSuccesses").long()>0,
                    "Missing qualification must not be confused with broken configuration observation wiring")
                assertEquals(JBoolean(false),qualificationAudit.entries.getValue("qualificationRegistryPresent"))
                val heldWork=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
                assertTrue(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(enrolled.projectId,reservation.assignmentId)) !in heldWork.records)
                assertTrue(WorkRecordIdentity(WorkFamily.NATIVE_INTENTS,WorkKey.Id(enrolled.projectId,reservation.intentId)) !in heldWork.records)
                assertEquals(beforeProductionPreparation,preparationEffects(),"Qualification refusal must precede receiver creation/delegation/bind/start")
                val productionReplay=(client.invoke(controllerSession,"writhelm_prepare_worker",obj("preparationId" to uuid(preparationId),
                    "json" to str(StrictJson.encode(productionSelection).decodeToString())),cached=true) as JObject).entries
                assertEquals(str("reservation-held"),productionReplay.getValue("phase"))
                assertContentEquals(reserveBytes,(StrictJson.decode(Files.readAllBytes(originalDriver)) as JObject).entries.getValue("reserveJSON").string().toByteArray())
                assertEquals(beforeProductionPreparation,preparationEffects())
                Files.write(root.resolve("production-reviewer-preparation-evidence.json"),StrictJson.encode(obj(
                    "preparationId" to uuid(preparationId),"reservationRequestId" to uuid(productionReserve.requestKey),
                    "assignmentId" to uuid(reservation.assignmentId),"plannedNativeSessionID" to str(reservation.intendedExecution.nativeSessionId),
                    "registeredProductionPreparationInvoked" to JBoolean(true),"phase" to preparation.getValue("phase"),
                    "canonicalRejection" to str(qualificationRejection.code),"originalReservationReceiptReplayed" to JBoolean(true),
                    "receiverDelegationBindingEffectsUnchanged" to JBoolean(true),"syntheticQualificationGrantedToPreparation" to JBoolean(false),
                    "actualReserveQualifiedProfileCount" to num(0),"canonicalAssignmentCreated" to JBoolean(false),
                    "trustedConnectedObserverReached" to JBoolean(true),"nativeConfigurationObserverInstalled" to JBoolean(true),
                    "qualificationObserver" to qualificationAudit,
                    "canonicalPrepareIntentCreated" to JBoolean(false),
                    "syntheticCallerContext" to JBoolean(true),"canonicalInputsHaveSyntheticHistory" to JBoolean(true),
                    "modelCalls" to num(0),"productionQualified" to JBoolean(false))))
                val freshReviewer=actor(repeatedReview.first,repeatedReview.second,"reviewer-v1",listOf(amendedResult))
                assertTrue(freshReviewer.execution.principalId !in setOf(worker,enrolled.principalId,author.execution.principalId,reviewer.execution.principalId))
                val freshDigest=Digest.of("SYNTHETIC amended-candidate review source; no inference".toByteArray())
                val freshLocator=java.util.UUID.randomUUID()
                fixtureFacts=fixtureFacts.copy(sourceCuts=fixtureFacts.sourceCuts+(freshLocator to WorkRetainedCut(
                    freshReviewer.execution.nativeHostId,freshReviewer.execution.nativeSessionId,freshDigest,freshDigest,
                    listOf("synthetic-fresh-review-message" to freshDigest))))
                val freshCut=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRecordSourceCutOperation(CRecordSourceCut(
                    WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),WSourceCuts(repeatedReview.first,freshReviewer.subject,
                        freshReviewer.execution.nativeHostId,freshReviewer.execution.nativeSessionId,profileKey,1,freshDigest,freshDigest,freshLocator,
                        listOf(WSourceCutsNativeMessagesItem("synthetic-fresh-review-message",freshDigest)),emptyList(),emptyList()),emptyList()))),freshReviewer.token))
                val freshText=dev.writhelm.core.knowledge.Utf8Content.fromText("SYNTHETIC repeated review record; no semantic review performed")
                fun freshReport(subjects:List<WorkReference>,cutRef:WorkReference)=WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,
                    CSubmitVerificationReportOperation(CSubmitVerificationReport(WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),
                        CSubmitVerificationReportData(repeatedReview.first,freshReviewer.subject,subjects,repeatedReview.third,
                            verification.gates.map {it.id},WSourceVariant2Value(WSourceVariant2("work",cutRef)),
                            listOf(WSourceVariant1Value(WSourceVariant1("artifact",checked.candidate))),emptyList(),emptyList(),
                            WorkBodyReference(freshText.digest,freshText.sizeBytes),listOf("Synthetic lifecycle/source/report; not production qualification")),
                        listOf(CSubmitVerificationReportBodiesItem(freshText.digest,freshText.text())))))
                // Delegation/source-cut checks must reject reuse before publishing the new report.
                for(invalid in listOf(freshReport(listOf(resultRef),freshCut),freshReport(listOf(amendedResult),cut))){
                    synchronized(instance.app){
                        val now=instance.app.coordinationCapture(enrolled.token,enrolled.projectId)
                        observations.arm(CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,now.basis),invalid,fixtureFacts,fixtureProfiles)
                        val rejected=instance.app.execute(freshReviewer.token,codec.encodeCommand(invalid))
                        assertFalse(rejected is dev.writhelm.core.store.CommandResult.Settled && rejected.receipt.outcome is dev.writhelm.core.store.LogicalOutcome.Success,
                            "Prior subjects/source-cut cannot satisfy the fresh reviewer assignment")
                    }
                }
                val freshReportRef=assertNotNull(publish(freshReport(listOf(amendedResult),freshCut),freshReviewer.token))
                val finalWork=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
                val finalReport=(finalWork.resolve(freshReportRef).payload as WVerificationReportsPayload).value
                assertEquals(listOf(amendedResult),finalReport.subjects)
                assertEquals(freshReviewer.execution,finalReport.reviewer)
                assertEquals(listOf(WSourceVariant1Value(WSourceVariant1("artifact",checked.candidate))),finalReport.readOnlySnapshotBasis)
                assertTrue(finalReport.findings.isEmpty())
                publish(guardCommand.copy(requestKey=java.util.UUID.randomUUID()))
                // Original NOT_READY receipt remains immutable, even after readiness changes.
                val originalDenial=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,codec.encodeCommand(guardCommand)))
                assertTrue(originalDenial.commandReplayed)
                assertEquals("NOT_READY",assertIs<dev.writhelm.core.store.LogicalOutcome.Rejected>(originalDenial.receipt.outcome).code)
                assertEquals(assessedBytes,assessedBytes.keys.associateWith {Digest.of(Files.readAllBytes(it))},
                    "Canonical report publication must leave assessed source/test/index bytes untouched")
                assertTrue(finalWork.records.values.none {it.identity.family in setOf(WorkFamily.ACCEPTANCES,WorkFamily.INTEGRATIONS)})
                val shellSurface=(client.probeNativeReviewerShellSurface(controllerSession,freshReviewer.token) as JObject).entries
                assertEquals(JBoolean(true),shellSurface.getValue("delegatedReviewerCredentialTested"))
                assertEquals(2,shellSurface.getValue("nativeAuthenticationControls").list().size)
                val readSurface=client.probeReviewerReadSurface(shellSurface.getValue("nativeSessionID").string(),controllerSession)
                assertEquals(JBoolean(false),(readSurface as JObject).entries.getValue("executionHooksQualified"))
                assertEquals(JBoolean(true),readSurface.entries.getValue("positiveControlPassed"))
                assertEquals(JBoolean(false),readSurface.entries.getValue("reviewerPositiveControlPassed"))
                val readOutcomes=readSurface.entries.getValue("outcomes").list().map {(it as JObject).entries}
                assertEquals(JBoolean(true),(readOutcomes[1].getValue("errorClass") as JObject).entries.getValue("scopedExecutionHeld"))
                assertEquals(JBoolean(true),(readOutcomes[2].getValue("errorClass") as JObject).entries.getValue("externalDirectoryDenied"))
                assertEquals(JBoolean(true),(readOutcomes[3].getValue("errorClass") as JObject).entries.getValue("scopedExecutionHeld"))
                assertEquals(JBoolean(false),readSurface.entries.getValue("credentialReachabilityQualified"))
                assertEquals(JBoolean(false),readSurface.entries.getValue("pathConfinementQualified"))
                Files.write(root.resolve("remediation-evidence.json"),StrictJson.encode(obj("controllerId" to uuid(enrolled.controllerId),
                    "blockingReport" to StrictJson.decode(WorkIdentityCodec().encodeReference(report)),"remediationResult" to StrictJson.decode(WorkIdentityCodec().encodeReference(amendedResult)),
                    "priorCheckRunId" to uuid(assessed.checks.single().runId),"freshCheckRunId" to uuid(checked.checks.single().runId),
                    "oldChecksRejectedForAmendedPlan" to JBoolean(true),"freshIndependentReviewStillRequired" to JBoolean(false),
                    "freshCanonicalReport" to StrictJson.decode(WorkIdentityCodec().encodeReference(freshReportRef)),
                    "freshReviewerPrincipal" to uuid(freshReviewer.execution.principalId),"priorSourceCutAndSubjectsRejected" to JBoolean(true),
                    "canonicalReviewedReadinessAfterFreshReport" to str("reservation-succeeded"),"originalNotReadyReceiptPreserved" to JBoolean(true),
                    "semanticReviewPerformed" to JBoolean(false),"productionQualified" to JBoolean(false),
                    "nonSyntheticIndependentReviewStillRequired" to JBoolean(true),"assessedBytesUnchanged" to JBoolean(true),
                    "reviewerTokenRejectedByNativeOperatorApi" to JBoolean(true),
                    "canonicalReviewedReadinessRejected" to str("NOT_READY"),
                    "syntheticReviewerLifecycleAndSource" to JBoolean(true),"syntheticCallContext" to JBoolean(true),"modelCalls" to num(0),"integrated" to JBoolean(false))))
            }
            if(dispatchChecks){
                val client=requireNotNull(installed)
                fixtureFacts=fixtureFacts.copy(approvedDecisions=setOf(revision.x()))
                val dispatchEntries=ArrayList<DispatchPlanEntry>()
                var first:WorkKey.TaskRevision?=null
                for(index in 0..3){
                    val taskId=WorkKey.TaskRevision(enrolled.projectId,java.util.UUID.randomUUID(),0)
                    val verificationKey=WorkKey.Revision(enrolled.projectId,java.util.UUID.randomUUID(),0)
                    val dependencies=when(index){
                        2->listOf(WDependency(requireNotNull(first),"implemented",null,emptyList(),"Pending exact producer result"))
                        3->listOf(WDependency(taskKey,"implemented",resultRef,emptyList(),"Existing exact canonical result"))
                        else->emptyList()
                    }
                    val terms=selectedWork.work.task(taskKey).copy(kind="implementation",objective="Model-free dispatch readiness $index",
                        verificationPlan=verificationKey,dependencies=dependencies,approvedDesign=listOf(revision.x()))
                    publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CCreateTaskRevisionOperation(CCreateTaskRevision(taskId,terms,
                        CCreateTaskRevisionNewVerificationPlan(verificationKey,verification.copy(task=taskId)),emptyList()))))
                    val manifestKey=WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID())
                    val manifest=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CPinInputManifestOperation(
                        CPinInputManifest(manifestKey,WInputManifests(taskId,base,applicability,
                            listOf(WManifestItem(source,true,"Exact dispatch fixture base",emptyList())),source,emptyList()),emptyList())))))
                    dispatchEntries+=DispatchPlanEntry(taskId,manifest,profileKey,listOf(WorkKey.Resource(enrolled.projectId,java.util.UUID.randomUUID())),
                        java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),if(index==2)listOf(DispatchFutureDependency(requireNotNull(first),"implemented"))else emptyList())
                    if(index==0)first=taskId
                }
                val dispatchPlan=DispatchPlanCandidate(enrolled.projectId,java.util.UUID.randomUUID(),enrolled.controllerId,dispatchEntries)
                val published=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,
                    DispatchPlanCommandCodec().encode(DispatchPlanCommand(java.util.UUID.randomUUID(),dispatchPlan))))
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(published.receipt.outcome)
                val projection=(instance.app.nativeDispatchContext(enrolled.token,enrolled.projectId,enrolled.controllerId,intended.nativeHostId,dispatchPlan.planId) as JObject).entries
                val entries=projection.getValue("entries").list().map {(it as JObject).entries}
                assertEquals(JBoolean(false),entries.single {it.getValue("preparationId")==uuid(dispatchEntries[2].preparationId)}.getValue("futureReady"))
                val activation=(client.invoke(controllerSession,"writhelm_activate_dispatch",obj("planId" to uuid(dispatchPlan.planId))) as JObject).entries
                assertEquals(str("dispatch-activated"),activation.getValue("phase"))
                val custody=controllerRoot!!.resolve("native")
                fun driver(entry:DispatchPlanEntry)=custody.resolve("preparation-driver-${Digest.of(entry.preparationId.toString().toByteArray()).hex}.json")
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                val considered=listOf(dispatchEntries[0],dispatchEntries[1],dispatchEntries[3])
                while(considered.any {!Files.exists(driver(it))}&&System.nanoTime()<deadline)Thread.sleep(100)
                assertTrue(dispatchEntries.take(2).all {Files.exists(driver(it))},"Real frontier must consider both independent entries; inspect $root")
                assertTrue(Files.exists(driver(dispatchEntries[3])),"An exact implemented dependency can enter frontier consideration")
                assertFalse(Files.exists(driver(dispatchEntries[2])),"Pending dependent cannot enter preparation")
                assertTrue(Files.list(custody).use {paths->paths.noneMatch {it.fileName.toString().startsWith("initial-driver-")}},
                    "No qualified execution profile means no native initial dispatch")
                val activationFile=custody.resolve("dispatch-activation-${Digest.of(dispatchPlan.planId.toString().toByteArray()).hex}.json")
                val originalActivation=Files.readAllBytes(activationFile)
                val replay=(client.invoke(controllerSession,"writhelm_activate_dispatch",obj("planId" to uuid(dispatchPlan.planId)),cached=true,parallel=true) as JObject).entries
                assertEquals(str("dispatch-activated"),replay.getValue("phase"))
                assertContentEquals(originalActivation,Files.readAllBytes(activationFile))
                val replayCall=(StrictJson.decode(Files.readAllBytes(root.resolve("tool-request.json"))) as JObject).entries.getValue("id").string()
                val provenance=Files.readAllLines(root.resolve("tool-provenance/$replayCall.jsonl")).filter {it.isNotBlank()}
                    .map {(StrictJson.decode(it.toByteArray()) as JObject).entries}
                assertEquals(16,provenance.count {it.getValue("phase")==str("entered")})
                val readHolds=provenance.filter {it.getValue("phase")==str("failed")}
                val returned=provenance.count {it.getValue("phase")==str("returned")}
                assertTrue(returned>0)
                assertEquals(16,returned+readHolds.size)
                assertTrue(readHolds.all {it.getValue("status") in setOf(JNumber(429),JNumber(503))},
                    "Only explicit transient read admission holds are valid under burst pressure; never ignore forbidden/unknown failures")
                assertTrue(provenance.all {it.getValue("id")==str(replayCall) && it.getValue("sessionID")==str(controllerSession)})
                if(readHoldChecks){
                    fun effects()=Files.list(custody).use {paths->paths.filter {path->
                        listOf("dispatch-activation-","preparation-driver-","initial-driver-","project-check-dispatch-","project-check-job-",
                            "candidate-test-run-","candidate-test-result-").any {path.fileName.toString().startsWith(it)}
                    }.toList().associate {it.fileName.toString() to Digest.of(Files.readAllBytes(it))}}
                    val before=effects();val refusalsBefore=client.readRefusals()
                    client.holdReads(true)
                    val held=try {(client.invoke(controllerSession,"writhelm_activate_dispatch",obj("planId" to uuid(dispatchPlan.planId)),
                        cached=true,originalCallId=replayCall) as JObject).entries}finally{client.holdReads(false)}
                    assertEquals(str("dispatch-held"),held.getValue("phase"))
                    assertEquals(num(429),held.getValue("httpStatus"))
                    assertEquals(str("native-service-read-admission-limit"),held.getValue("reason"))
                    assertEquals(uuid(dispatchPlan.planId),held.getValue("planId"))
                    assertEquals(JBoolean(false),held.getValue("effectsAsserted"))
                    assertTrue(held.getValue("message").string().contains("retry the same plan"))
                    assertTrue(client.readRefusals()-refusalsBefore>=9,"Actual read path must exhaust its initial request plus eight existing retries")
                    assertEquals(before,effects(),"A read admission hold cannot create new dispatch/check effects")
                    val resumed=(client.invoke(controllerSession,"writhelm_activate_dispatch",obj("planId" to uuid(dispatchPlan.planId)),
                        cached=true,originalCallId=replayCall) as JObject).entries
                    assertEquals(str("dispatch-activated"),resumed.getValue("phase"))
                    val delivery=(StrictJson.decode(Files.readAllBytes(root.resolve("tool-request.json"))) as JObject).entries
                    assertEquals(str(replayCall),delivery.getValue("id"),"Only test driver delivery nonce changes, never original tool/plan identity")
                    assertEquals(before,effects())
                    assertContentEquals(originalActivation,Files.readAllBytes(activationFile))
                    Files.write(root.resolve("read-hold-evidence.json"),StrictJson.encode(obj("controllerId" to uuid(enrolled.controllerId),
                        "sessionID" to str(controllerSession),"planId" to uuid(dispatchPlan.planId),"originalCallId" to str(replayCall),
                        "hold" to JObject(held),"resumedPhase" to resumed.getValue("phase"),"originalEffectCustodyUnchanged" to JBoolean(true),
                        "injectedHttpAdmissionFault" to JBoolean(true),"syntheticCallContext" to JBoolean(true),"syntheticExecutionFacts" to JBoolean(true),
                        "modelCalls" to num(0),"productionQualified" to JBoolean(false))))
                }
                // A synthetic executor publishes a real canonical producer result. Do not patch
                // dependency/readiness rows or substitute a fake dispatchContext response.
                val producer=dispatchEntries.first()
                fixtureFacts=fixtureFacts.copy(verifiedPackets=fixtureFacts.verifiedPackets+producer.manifest)
                val producerAssignment=java.util.UUID.randomUUID();val producerWorkspace=java.util.UUID.randomUUID()
                val prepareId=java.util.UUID.randomUUID();val prepareCorrelation=java.util.UUID.randomUUID()
                val producerIntent=WExecutionIntent(enrolled.controllerId,java.util.UUID.randomUUID(),"executor-v1",intended.nativeHostId,"ses_synthetic_dispatch_producer")
                publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CReserveAssignmentOperation(CReserveAssignment(
                    producerAssignment,producer.task,producer.manifest,producer.profile,producerWorkspace,
                    producer.resources.map {CReserveAssignmentResourcesItem(it,null)},emptyList(),prepareId,prepareCorrelation,emptyList(),producerIntent))))
                val principal=java.util.UUID.randomUUID();val credential=java.util.UUID.randomUUID();val producerSecret=ByteArray(32){19}
                val enroll=Auth3Command(java.util.UUID.randomUUID(),enrolled.projectId,Auth3Payload.RegisterDelegation(java.util.UUID.randomUUID(),
                    DelegationBinding(enrolled.controllerId,enrolled.projectId,principal,credential,producer.task.taskId,producerAssignment,
                        producerIntent.executionId,0,DelegationTemplate.EXECUTOR,emptyList()),0,
                    dev.writhelm.core.auth.CredentialVerifier.digest(instance.storeId,enrolled.projectId,principal,credential,producerSecret)))
                val delegated=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,Auth3CommandCodec().encode(enroll)))
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(delegated.receipt.outcome)
                val producerExecution=WExecution(producerIntent.executionId,principal,credential,producerIntent.nativeHostId,producerIntent.nativeSessionId,producerWorkspace)
                val producerSubject=WAssignmentSubject(producerAssignment,0)
                fixtureFacts=fixtureFacts.copy(native=mapOf(prepareId to WorkNativeObservation(producerSubject,prepareId,prepareCorrelation,
                    "prepare","confirmed",listOf(source),producerExecution,true,"reserved")))
                publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CBindAssignmentOperation(CBindAssignment(producerAssignment,1,prepareId,0,
                    producerIntent.nativeHostId,producerIntent.nativeSessionId,producerWorkspace,listOf(source),emptyList()))))
                val producerStart=java.util.UUID.randomUUID();val producerCorrelation=java.util.UUID.randomUUID()
                publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRequestNativeTransitionOperation(CRequestNativeTransition(
                    producerAssignment,2,producerStart,producerCorrelation,"start",null,emptyList()))))
                fixtureFacts=fixtureFacts.copy(native=mapOf(producerStart to WorkNativeObservation(producerSubject,producerStart,producerCorrelation,
                    "start","confirmed",listOf(source),producerExecution,false,"bound")))
                publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CObserveNativeTransitionOperation(CObserveNativeTransition(
                    producerAssignment,3,producerStart,0,"confirmed",listOf(source),emptyList()))))
                val producerToken="wh1.${instance.storeId}.$credential."+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(producerSecret)
                val retrospective=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CRecordRetrospectiveOperation(
                    CRecordRetrospective(WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),CRecordRetrospectiveData(producer.task,producerSubject,
                        null,"no-significant-new-findings",emptyList()),emptyList()))),producerToken))
                Files.writeString(workspace.resolve("producer-A.txt"),"mechanical test-only producer result\n")
                runGit("add","producer-A.txt");runGit("commit","-m","Test-only dispatch producer result")
                val producerSnapshot=resultSnapshot.copy(commit=WorkGitOid("sha1",gitObject("HEAD")),tree=WorkGitOid("sha1",gitObject("HEAD^{tree}")),
                    retainedLocatorId=java.util.UUID.randomUUID())
                runGit("update-ref","refs/writhelm/results/${producerSnapshot.retainedLocatorId}",producerSnapshot.commit!!.hex,"0".repeat(40))
                val producerDiff=dev.writhelm.core.knowledge.Utf8Content.fromText(runGit("diff","--no-ext-diff","--no-textconv","--no-color",
                    "--binary","--full-index",snapshot.commit!!.hex,producerSnapshot.commit.hex,"--")+"\n")
                fixtureFacts=fixtureFacts.copy(retainedSnapshots=fixtureFacts.retainedSnapshots+producerSnapshot,
                    submittedDiffs=fixtureFacts.submittedDiffs+WorkSubmittedDiff(producer.task,listOf(producerSnapshot),WorkBodyReference(producerDiff.digest,producerDiff.sizeBytes)))
                observations.allowRetainedDispatchIntents(instance.storeId,enrolled.projectId,enrolled.controllerId,custody,
                    dispatchEntries[2].preparationId,fixtureFacts)
                val producerResult=assertNotNull(publish(WorkCommand(java.util.UUID.randomUUID(),enrolled.projectId,CSubmitResultOperation(CSubmitResult(
                    WorkKey.Id(enrolled.projectId,java.util.UUID.randomUUID()),CSubmitResultData(producer.task,producerSubject,producer.manifest,listOf(producerSnapshot),
                        WorkBodyReference(producerDiff.digest,producerDiff.sizeBytes),listOf(criterion.id),emptyList(),retrospective,emptyList()),
                    listOf(CSubmitResultBodiesItem(producerDiff.digest,producerDiff.text()))))),producerToken))
                val readyProjection=(instance.app.nativeDispatchContext(enrolled.token,enrolled.projectId,enrolled.controllerId,intended.nativeHostId,dispatchPlan.planId) as JObject).entries
                val readyEntries=readyProjection.getValue("entries").list().map {(it as JObject).entries}
                assertEquals(JBoolean(true),readyEntries.single {it.getValue("preparationId")==uuid(dispatchEntries[2].preparationId)}.getValue("futureReady"))
                val materialization=custody.resolve("dispatch-materialization-${Digest.of(dispatchEntries[2].preparationId.toString().toByteArray()).hex}.json")
                val reactionDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                while(!Files.exists(materialization)&&System.nanoTime()<reactionDeadline)Thread.sleep(100)
                assertTrue(Files.exists(materialization),"Already active registered frontier must react to producer publication; inspect $root")
                val actualWork=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
                assertIs<DispatchResultChoice.Pending>(chooseDispatchResult(actualWork,DispatchFutureDependency(dispatchEntries[1].task,"implemented")),
                    "Unrelated independent work still has no result when dependent readiness is acted upon")
                assertEquals(producerResult,assertIs<DispatchResultChoice.Selected>(
                    chooseDispatchResult(actualWork,DispatchFutureDependency(producer.task,"implemented"))).result)
                val intent=(StrictJson.decode(Files.readAllBytes(materialization)) as JObject).entries
                assertEquals(str("materialize_dispatch_dependency"),intent.getValue("operation"))
                val payload=(intent.getValue("payload") as JObject).entries
                assertEquals(dispatchEntries[2].task,WorkIdentityCodec().decodeKey(WorkFamily.TASK_REVISIONS,StrictJson.encode(payload.getValue("task"))))
                val dispatchPlanRef=actualWork.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(enrolled.projectId,dispatchPlan.planId))
                val closureDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                var closed=dispatchClosedSelection(actualWork,dispatchPlanRef,dispatchEntries[2])
                while(closed==null&&System.nanoTime()<closureDeadline){
                    Thread.sleep(100)
                    closed=dispatchClosedSelection(instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work,dispatchPlanRef,dispatchEntries[2])
                }
                val completed=assertNotNull(closed,"Canonical materialization/declared-input closure remains held; inspect $root")
                val closedWork=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
                assertEquals(producerResult,closedWork.task(completed.task).dependencies.single().selectedResult)
                assertTrue((closedWork.resolve(completed.manifest).payload as WInputManifestsPayload).value.knownGaps.isEmpty())
                val replayMaterialization=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,Files.readAllBytes(materialization)))
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(replayMaterialization.receipt.outcome)
                assertTrue(replayMaterialization.commandReplayed,"Observe committed original materialization, never overwrite its outcome")
                var closureFile=custody.resolve("dispatch-closure-${Digest.of(dispatchEntries[2].preparationId.toString().toByteArray()).hex}.json")
                var closureCommand=DispatchClosureCommandCodec().decode(Files.readAllBytes(closureFile))
                var closureDepth=0
                while(closureCommand.requestKey!=(completed.closure.identity.key as WorkKey.Id).id){
                    assertTrue(++closureDepth<=256)
                    closureFile=custody.resolve("dispatch-closure-successor-${Digest.of(closureCommand.requestKey.toString().toByteArray()).hex}.json")
                    closureCommand=DispatchClosureCommandCodec().decode(Files.readAllBytes(closureFile))
                }
                val replayClosure=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(enrolled.token,Files.readAllBytes(closureFile)))
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(replayClosure.receipt.outcome)
                assertTrue(replayClosure.commandReplayed)
                assertTrue(Files.list(custody).use {paths->paths.noneMatch {it.fileName.toString().startsWith("initial-driver-")}},
                    "Readiness release cannot bypass the missing execution-profile hold")
                Files.write(root.resolve("dispatch-evidence.json"),StrictJson.encode(obj("planId" to uuid(dispatchPlan.planId),
                    "controllerId" to uuid(enrolled.controllerId),"registeredDispatchToolInvoked" to JBoolean(true),
                    "concurrentCachedReplays" to num(16),"sameOriginalReplayCallId" to str(replayCall),"handlerProvenanceVerified" to JBoolean(true),
                    "concurrentCachedReadHolds" to num(readHolds.size),
                    "independentPreparationDriversObserved" to num(2),"pendingDependentPreparationDriverObserved" to JBoolean(false),
                    "selectedDependencyPreparationDriverObserved" to JBoolean(true),
                    "producerResult" to StrictJson.decode(WorkIdentityCodec().encodeReference(producerResult)),
                    "pendingDependencyBecameFutureReady" to JBoolean(true),"frontierMaterializationIntentObserved" to JBoolean(true),
                    "canonicalMaterializationCommitted" to JBoolean(true),"canonicalDeclaredInputClosureCommitted" to JBoolean(true),
                    "originalMaterializationAndClosureReceiptsReplayed" to JBoolean(true),
                    "closedTask" to StrictJson.decode(WorkIdentityCodec().encodeKey(completed.task)),
                    "unrelatedIndependentResultStillPending" to JBoolean(true),"realNoBatchBarrierExecutionVerified" to JBoolean(false),
                    "missingExecutionProfileStartHeld" to JBoolean(true),"realWorkerOverlapVerified" to JBoolean(false),
                    "syntheticExecutionFacts" to JBoolean(true),"syntheticCallContext" to JBoolean(true),"modelCalls" to num(0))))
            }
            val state=assertNotNull(captured)
            assertEquals(enrolled.projectId,state.authority.controllers.getValue(enrolled.controllerId).projectId)
            val scope=CompositionSyntheticFacts.Scope(instance.storeId,enrolled.projectId,enrolled.controllerId,state.basis)
            val syntheticSnapshot=WSnapshot(java.util.UUID.randomUUID(),WorkGitOid("sha1","1".repeat(40)),
                WorkGitOid("sha1","2".repeat(40)),null,java.util.UUID.randomUUID())
            val facts=UnavailableH1RuntimeFacts.capture(state,command).facts.copy(retainedSnapshots=setOf(syntheticSnapshot))
            observations.arm(scope,profile,facts)
            assertEquals(setOf(syntheticSnapshot),observations.capture(state,profile).facts.retainedSnapshots)
            assertTrue(observations.capture(state,profile.copy(requestKey=java.util.UUID.randomUUID())).facts.retainedSnapshots.isEmpty())
            observations.disarm()
            assertTrue(observations.capture(state,profile).facts.retainedSnapshots.isEmpty())
            val retained=instance.app.coordinationCapture(enrolled.token,enrolled.projectId).work
            assertTrue(retained.records.size>5)
            assertNotNull(retained.records[WorkRecordIdentity(WorkFamily.PROFILES,profileKey)])
            assertNotNull(retained.records[WorkRecordIdentity(WorkFamily.TASK_REVISIONS,taskKey)])
            assertNotNull(retained.records[WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,inputKey)])
            assertFalse(Files.exists(root.resolve("private/profile-qualification")))
            if(registeredChecks){
                val client=requireNotNull(installed)
                if(reviewerSurfaceChecks){
                    val surface=(client.probeNativeReviewerShellSurface(controllerSession) as JObject).entries
                    assertEquals(JBoolean(false),surface.getValue("canonicalReviewerAssignment"))
                    assertEquals(JBoolean(false),surface.getValue("activeReviewerLifecycleQualified"))
                    assertEquals(JBoolean(true),surface.getValue("assessedSourceUnchanged"))
                }
                val custody=controllerRoot!!.resolve("native")
                fun activationEvidence()=Files.list(custody).use {paths->paths.filter {path->
                    path.fileName.toString().startsWith("candidate-activation-") || path.fileName.toString().startsWith("candidate-test-result-")
                }.toList().associate {it.fileName.toString() to Digest.of(Files.readAllBytes(it))}}
                val beforeRevoke=activationEvidence()
                val cachedDiscovery=(client.invoke(controllerSession,"writhelm_project_checks",obj(),cached=true) as JObject).entries
                assertEquals(JBoolean(true),cachedDiscovery.getValue("available"),"The same cached handler must work before revocation")
                val owner=broker.project(workspace).ownerToken
                val revoked=assertIs<dev.writhelm.core.store.CommandResult.Settled>(instance.app.execute(owner,CommandCodec().encode(
                    CoreCommand(java.util.UUID.randomUUID(),enrolled.projectId,CommandPayload.Revoke(enrolled.principalId)))))
                assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(revoked.receipt.outcome)
                client.invoke(controllerSession,"writhelm_project_checks",obj(),cached=true,expectHeld=true)
                client.invoke(controllerSession,"writhelm_activate_candidate",obj(
                    "planRefJSON" to str(WorkIdentityCodec().encodeReference(planRef).decodeToString())),cached=true,expectHeld=true)
                if(dispatchChecks){
                    val dispatchLedger=(StrictJson.decode(Files.readAllBytes(root.resolve("dispatch-evidence.json"))) as JObject).entries
                    client.invoke(controllerSession,"writhelm_activate_dispatch",obj("planId" to dispatchLedger.getValue("planId")),cached=true,expectHeld=true)
                }
                assertEquals(beforeRevoke,activationEvidence(),"Revoked cached calls cannot mint activation or check receipts")
                assertEquals(expectedResultText,Files.readString(workspace.resolve("result.txt")))
                assertEquals("registered-client owner bytes\n",Files.readString(workspace.resolve("owner-dirty.txt")))
                val evidence=root.resolve("composition-evidence.json")
                val ledger=(StrictJson.decode(Files.readAllBytes(evidence)) as JObject).entries
                Files.write(evidence,StrictJson.encode(JObject(ledger+mapOf(
                    "cachedHandlersDeniedAfterCanonicalRevocation" to JBoolean(true),
                    "revocationReusedNativeSession" to JBoolean(true),"activeReviewerBypassQualified" to JBoolean(false),
                    "reviewerExecutionSurfacesHeld" to JArray(listOf("builtin-edit-write-patch","shell-execute","plugin-code-mode",
                        "subagent","external-path-symlink","permission-change","allowed-read-report").map(::str)),
                    "activeExecutorFencingQualified" to JBoolean(false)))))
            }
            }finally{installed?.close()}
        }
    }
    @Test fun compositionSyntheticFactsAreExplicitExactAndUnavailableOutsideTheirEnrolledScope() {
        val f=JointFixture();val observations=CompositionSyntheticFacts()
        val command=f.reserveCommand()
        val scope=CompositionSyntheticFacts.Scope(storeId,project,f.controller,f.base.basis)
        assertTrue(observations.capture(f.base,command).qualifiedProfiles.isEmpty())
        observations.arm(scope,command,f.observations.facts,f.observations.qualifiedProfiles)
        assertEquals(f.observations.qualifiedProfiles,observations.capture(f.base,command).qualifiedProfiles)
        assertTrue(observations.capture(f.base,command.copy(requestKey=java.util.UUID.randomUUID())).qualifiedProfiles.isEmpty())
        observations.arm(scope.copy(controller=java.util.UUID.randomUUID()),command,f.observations.facts,f.observations.qualifiedProfiles)
        assertTrue(observations.capture(f.base,command).qualifiedProfiles.isEmpty())
        observations.arm(scope.copy(store=java.util.UUID.randomUUID()),command,f.observations.facts,f.observations.qualifiedProfiles)
        assertTrue(observations.capture(f.base,command).qualifiedProfiles.isEmpty())
        observations.arm(scope.copy(basis=dev.writhelm.core.store.GitOid("sha1","9".repeat(40))),command,f.observations.facts,f.observations.qualifiedProfiles)
        assertTrue(observations.capture(f.base,command).qualifiedProfiles.isEmpty())
        observations.disarm()
        assertTrue(observations.capture(f.base,command).qualifiedProfiles.isEmpty())
        assertEquals(1,observations.syntheticCaptures)
        assertEquals(6,observations.unavailableCaptures)
    }
    @Test fun packagedClientContractsContainExactSourceCodecsInsteadOfSyntheticSchemaStrings() {
        val contracts=dev.writhelm.service.PackagedClientContracts.load()
        assertTrue(contracts.keys.containsAll(setOf("work-command3","work-execution-values","knowledge-command1","wire-json")))
        val work=StrictJson.decode(contracts.getValue("work-command3").toByteArray(),JsonLimits(bytes=512*1024,stringBytes=262144,tokens=100000)) as JObject
        assertEquals(str("canonical-codec-source-v1"),work.entries.getValue("kind"))
        val files=(work.entries.getValue("files") as JArray).values.map {it as JObject}
        val decoder=files.single {it.entries.getValue("path").string()=="work/WorkCodec.kt"}
        val source=Files.readAllBytes(Path.of("src/main/kotlin/dev/writhelm/core/work/WorkCodec.kt"))
        assertEquals(source.decodeToString(),decoder.entries.getValue("source").string())
        assertEquals(dev.writhelm.core.knowledge.Digest.of(source).hex,decoder.entries.getValue("sha256").string())
    }
    @Test fun ownedTestClientInstanceCreatesAndColdReopensWithoutFixtureOrReplacementIdentities() {
        val root=Files.createTempDirectory("writhelm-owned-instance-").resolve("instance")
        val facts=dev.writhelm.service.SharedH1ControllerFacts()
        val first=dev.writhelm.service.LinuxTestClientInstance.open(root,facts.options())
        val store=first.storeId;val anchor=first.anchorProjectId;val token=first.ownerToken
        val before=first.app.inspectAccess(token).basis
        assertFails {dev.writhelm.service.LinuxTestClientInstance.open(root,facts.options())}
        first.close()
        dev.writhelm.service.LinuxTestClientInstance.open(root,facts.options()).use { reopened ->
            assertEquals(store,reopened.storeId);assertEquals(anchor,reopened.anchorProjectId);assertEquals(token,reopened.ownerToken)
            assertEquals(before,reopened.app.inspectAccess(reopened.ownerToken).basis)
        }
        // A missing custody descriptor never grants permission to replace an existing store.
        val descriptor=Files.list(root).use {paths ->paths.filter {it.fileName.toString().endsWith(".command")}.findFirst().orElseThrow()}
        val preserved=root.resolve("preserved-descriptor")
        Files.move(descriptor,preserved)
        assertFails {dev.writhelm.service.LinuxTestClientInstance.open(root,facts.options())}
        assertFalse(Files.exists(descriptor));assertTrue(Files.isDirectory(root.resolve("store")))
        Files.move(preserved,descriptor)
        dev.writhelm.service.LinuxTestClientInstance.open(root,facts.options()).use {assertEquals(store,it.storeId)}
    }
    @Test fun privateEnrollmentTransportUsesObservedNativeSessionNotCallerFolderOrActor() {
        val f=JointFixture();val root=Files.createTempDirectory("writhelm-enrollment-http-")
        val directory=Files.createDirectory(root.resolve("project"));val git=Path.of("/usr/bin/git")
        val proof=ByteArray(32){3};val encoded=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proof)
        f.publishedActiveApp(modelVersion=4).use { app ->
            var observations=0
            val native=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
            native.createContext("/api/session/") { exchange ->
                observations++
                assertEquals("Basic dGVzdA==",exchange.requestHeaders.getFirst("Authorization"))
                val id=exchange.requestURI.path.substringAfterLast('/')
                val response=StrictJson.encode(obj("data" to obj("id" to str(id),"location" to obj("directory" to str(directory.toString())))))
                exchange.sendResponseHeaders(200,response.size.toLong());exchange.responseBody.use {it.write(response)}
            }
            native.start()
            val nativeSessions=TestClientNativeSessions("http://127.0.0.1:${native.address.port}","Basic dGVzdA==")
            val broker=LinuxTestClientEnrollment(app,root.resolve("broker"),storeId,project,ownerToken,
                LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git))),nativeSessions::observe)
            val endpoint=TestClientEnrollmentEndpoint(broker,proof)
            val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val origin="http://127.0.0.1:$port"
            val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),enrollmentEndpoint=endpoint)
            val server=embeddedServer(CIO,host="127.0.0.1",port=port){adapter.install(this)}.start(wait=false)
            val client=java.net.http.HttpClient.newHttpClient()
            fun send(body:JsonValue,secret:String?=encoded):java.net.http.HttpResponse<String> {
                val request=java.net.http.HttpRequest.newBuilder(java.net.URI("$origin/internal/enrollment/v1"))
                    .header("Content-Type","application/json")
                if(secret!=null)request.header("X-Writhelm-Enrollment-Proof",secret)
                return client.send(request.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(StrictJson.encode(body))).build(),java.net.http.HttpResponse.BodyHandlers.ofString())
            }
            val body=obj("formatVersion" to num(1),"operation" to str("open-session"),"sessionID" to str("ses_client_created"))
            try {
                assertEquals(403,send(body,null).statusCode());assertEquals(0,observations)
                assertEquals(400,send(JObject(body.entries+("directory" to str("/untrusted")))).statusCode());assertEquals(0,observations)
                val first=send(body);assertEquals(200,first.statusCode())
                val binding=StrictJson.decode(first.body().toByteArray()) as JObject
                assertEquals("session-enrolled",binding.entries.getValue("kind").string())
                assertEquals(directory.toString(),binding.entries.getValue("workRoot").string())
                val token=binding.entries.getValue("token").string();val access=app.inspectAccess(token)
                assertIs<dev.writhelm.core.auth2.CredentialScope.Controller>(access.scope)
                assertFalse(dev.writhelm.core.workflow.Capability.MANAGE in access.capabilities)
                assertEquals(first.body(),send(body).body())
                endpoint.close();assertEquals(403,send(body).statusCode())
            } finally {server.stop(0,5000);runBlocking {adapter.mcp.close()};endpoint.close();native.stop(0)}
        }
    }
    @Test fun automaticRepositoryPreparationPreservesFilesAndReusesExistingRoot() {
        val root=Files.createTempDirectory("writhelm-auto-repository-")
        val project=Files.createDirectory(root.resolve("new-project"))
        val user=project.resolve("important.txt");val bytes="owner data\n".toByteArray();Files.write(user,bytes)
        val git=Path.of("/usr/bin/git")
        val adapter=LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git)))
        assertEquals(project,adapter.prepare(project))
        assertContentEquals(bytes,Files.readAllBytes(user))
        fun read(vararg args:String):String {
            val process=ProcessBuilder(git.toString(),"-C",project.toString(),*args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val result=process.inputStream.readAllBytes().toString(Charsets.UTF_8);assertTrue(process.waitFor(15,TimeUnit.SECONDS));assertEquals(0,process.exitValue())
            return result.trim()
        }
        assertEquals("",read("ls-tree","--name-only","HEAD")) // user bytes were not staged/committed
        val initial=read("rev-parse","HEAD")
        val nested=Files.createDirectory(project.resolve("nested"))
        assertEquals(project,adapter.prepare(nested))
        assertEquals(initial,read("rev-parse","HEAD"))
        assertContentEquals(bytes,Files.readAllBytes(user))
        assertFailsWith<IllegalArgumentException> { LinuxTestClientRepository(git,Digest.of(byteArrayOf(1))).prepare(project) }
        val broken=Files.createDirectory(root.resolve("broken"));Files.writeString(broken.resolve(".git"),"invalid git metadata")
        assertFailsWith<IllegalArgumentException> {adapter.prepare(broken)}
        assertEquals("invalid git metadata",Files.readString(broken.resolve(".git")))
    }
    @Test fun brokerAutomaticallyReusesProjectAndIssuesDistinctRecoverableSessionControllers() {
        val f=JointFixture();val store=directory();val root=Files.createTempDirectory("writhelm-enrollment-")
        val workspace=Files.createDirectory(root.resolve("workspace"))
        val other=Files.createDirectory(root.resolve("other-project"))
        val privateRoot=root.resolve("broker")
        val git=Path.of("/usr/bin/git");val repositories=LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git)))
        var moved=false
        val nativeSessions:(String)->LinuxTestClientEnrollment.ObservedTestClientSession={ session ->
            LinuxTestClientEnrollment.ObservedTestClientSession(session,if(session=="ses_other_project" || moved && session=="ses_new_one")other else workspace,
                if(session=="ses_child")"ses_new_one" else null)
        }
        var projectId:java.util.UUID?=null;var controllerId:java.util.UUID?=null
        f.publishedActiveApp(modelVersion=4,root=store).use { app ->
            val broker=LinuxTestClientEnrollment(app,privateRoot,storeId,project,ownerToken,repositories,nativeSessions)
            val enrolled=broker.project(workspace);projectId=enrolled.projectId
            val alias=root.resolve("alias");Files.createSymbolicLink(alias,workspace)
            assertEquals(enrolled.projectId,broker.project(alias).projectId)
            val first=broker.session("ses_new_one");controllerId=first.controllerId
            val second=broker.session("ses_new_two")
            assertNotEquals(first.controllerId,second.controllerId);assertNotEquals(first.principalId,second.principalId);assertNotEquals(first.credentialId,second.credentialId)
            assertEquals(first.controllerId,broker.session("ses_new_one").controllerId)
            assertFailsWith<IllegalArgumentException> { broker.session("ses_child") }
            assertFailsWith<AppAccessFault> { broker.session(f.intended.nativeSessionId) } // parentless reserved/bound worker is not a planner
            val different=broker.project(other)
            val isolated=broker.session("ses_other_project")
            assertNotEquals(enrolled.projectId,different.projectId);assertNotEquals(first.controllerId,isolated.controllerId)
            moved=true;assertFailsWith<IllegalArgumentException> {broker.session("ses_new_one")};moved=false
            assertEquals(listOf(".git"),Files.list(workspace).use { paths ->paths.map { it.fileName.toString() }.toList() }) // no secret/config files in work area
            assertEquals(enrolled.projectId,broker.project(Files.createDirectory(workspace.resolve("nested"))).projectId)
            val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val parallel=(1..2).map { pool.submit<java.util.UUID> {
                    val instance=LinuxTestClientEnrollment(app,privateRoot,storeId,project,ownerToken,repositories,nativeSessions)
                    instance.session("ses_concurrent").controllerId
                } }.map { it.get(20,TimeUnit.SECONDS) }
                assertEquals(1,parallel.distinct().size)
            } finally {pool.shutdownNow()}
        }
        H1Application.open(store,storeId,H1Options(UnavailableH1RuntimeFacts,workModelVersion=4)).use { app ->
            val broker=LinuxTestClientEnrollment(app,privateRoot,storeId,project,ownerToken,repositories,nativeSessions)
            val restored=broker.project(workspace)
            assertEquals(projectId,restored.projectId)
            assertEquals(controllerId,broker.session("ses_new_one").controllerId)
        }
    }
    @Test fun protectedModel4BootstrapConnectsOnlyAfterExplicitNativeObservation() {
        val f=ReviewAppFixture();val store=directory()
        val original=f.base.work.getValue(project)
        val work=WorkState.restore(project,original.records.values,original.bodies.values,modelVersion=4)
        H1Application.initialize(store,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to work)),
            H1Options(UnavailableH1RuntimeFacts,workModelVersion=4)).close()
        val root=Files.createTempDirectory("writhelm-bootstrap-connect-")
        val secrets=Files.createDirectory(root.resolve("native"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val area=Files.createDirectory(root.resolve("work"));val proof=ByteArray(32){7}
        fun retained(kind:String,key:String,value:JsonValue):Path {
            val file=secrets.resolve("$kind-${Digest.of(key.toByteArray()).hex}.json")
            Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            Files.write(file,StrictJson.encode(value));return file
        }
        retained("credential",plannerKey.toString(),obj("formatVersion" to num(1),"kind" to str("native-scoped-key-v1"),
            "storeId" to uuid(storeId),"projectId" to uuid(project),"principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken)))
        listOf("service","transport").forEach { retained("native-peer",it,obj("formatVersion" to num(1),"kind" to str("native-peer-proof-v1"),
            "secret" to str(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proof)))) }
        val selection=retained("service-work-profile","current",obj("formatVersion" to num(1),"modelVersion" to num(4)))
        val peer=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        var calls=0;var observations=0;var qualified=false
        peer.createContext("/native/v1") { exchange ->
            calls++
            assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proof),exchange.requestHeaders.getFirst("X-Writhelm-Native-Proof"))
            val input=StrictJson.decode(exchange.requestBody.readAllBytes()) as JObject
            assertEquals("identity-immutable",input.entries.getValue("operation").string())
            val result=obj("hostId" to uuid(f.intended.nativeHostId),"controllerId" to uuid(f.controller),"principalId" to uuid(planner),
                "credentialId" to uuid(plannerKey),"sessionID" to str("ses_explicit_mock_host"),"epoch" to str("a".repeat(64)),"workRoots" to JArray(listOf(str(area.toString()))))
            val bytes=StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("native-result"),"result" to result))
            exchange.sendResponseHeaders(200,bytes.size.toLong());exchange.responseBody.use { it.write(bytes) }
        }
        peer.start()
        fun port()=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        try {
            fun open()=LocalH1Runtime.openFromNativeCustody(store,storeId,project,f.controller,f.intended.nativeHostId,
                "http://127.0.0.1:${port()}","http://127.0.0.1:${peer.address.port}",secrets,plannerKey,root.resolve("runtime"),listOf(area),
                observeNativeProcess={ _,epoch ->observations++;assertEquals("a".repeat(64),epoch);qualified })
            open().use { runtime ->
                assertEquals(4,runtime.app.coordinationCapture(plannerToken,project).work.modelVersion)
                assertFailsWith<IllegalArgumentException> { runtime.connectNative() }
                assertEquals(1,observations)
                qualified=true;runtime.connectNative();assertEquals(2,observations)
                runtime.connectNative();assertEquals(2,observations);assertEquals(2,calls)
            }
            Files.write(selection,StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(3))))
            assertFailsWith<IllegalArgumentException> { open() }
        } finally {peer.stop(0)}
    }
    private fun run(input: JObject,root: Path): Pair<Int,JObject> {
        val executable=Path.of(System.getProperty("java.home"),"bin","java")
        val classes=listOf(LocalSetup::class.java,kotlin.Unit::class.java,com.fasterxml.jackson.core.JsonFactory::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
        val process=ProcessBuilder(executable.toString(),"-Xmx128m","-XX:ActiveProcessorCount=2","-cp",classes.joinToString(java.io.File.pathSeparator),LocalSetup::class.java.name)
            .redirectError(root.resolve("setup-${System.nanoTime()}.stderr").toFile()).start()
        process.outputStream.use { it.write(StrictJson.encode(input,16384)) }
        val result=process.inputStream.readNBytes(4097)
        assertTrue(process.waitFor(30,TimeUnit.SECONDS))
        return process.exitValue() to (StrictJson.decode(result) as JObject)
    }
    private fun input(directory: Path,permissions: List<String>,mode: String="initialize-h1")=obj(
        "formatVersion" to num(1),"mode" to str(mode),"storeDirectory" to str(directory.toString()),"storeId" to uuid(storeId),"projectId" to uuid(project),
        "ownerPrincipalId" to uuid(owner),"ownerCredentialId" to uuid(ownerKey),"ownerVerifierDigest" to digest(credential(ownerKey,owner).verifierDigest),
        "agentPrincipalId" to uuid(planner),"agentCredentialId" to uuid(plannerKey),"agentVerifierDigest" to digest(credential(plannerKey,planner).verifierDigest),
        "controllerId" to uuid(id(60)),"workPermissions" to JArray(permissions.map(::str)))
    @Test fun freshH1SetupHasExplicitControllerPermissionsAndCannotResetAnExistingStore() {
        val root=directory();val store=root.resolve("new-store")
        val request=input(store,WorkPermission.entries.map { it.name })
        assertEquals(0,run(request,root).first)
        H1Application.open(store,storeId,H1Options(UnavailableH1RuntimeFacts)).use { app ->
            assertEquals(CredentialScope.Controller(id(60)),app.inspectAccess(plannerToken).scope)
            assertEquals(CredentialScope.Project,app.inspectAccess(ownerToken).scope)
            assertTrue(app.coordinationCapture(plannerToken,project).work.records.isEmpty())
        }
        assertEquals(2,run(request,root).first)
        assertEquals(0,run(input(store,emptyList(),"verify-h1"),root).first)
        val protectedRoot=Files.createTempDirectory("writhelm-h1-runtime-test-")
        fun port()=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:${port()}";val nativeOrigin="http://127.0.0.1:${port()}"
        val proof=ByteArray(32) { 8 }
        val runtime=LocalH1Runtime.open(store,storeId,project,id(60),id(61),origin,nativeOrigin,proof,proof,plannerToken,
            protectedRoot.resolve("runtime"),listOf(root))
        runtime.use { opened ->
            assertEquals(CredentialScope.Controller(id(60)),opened.app.inspectAccess(plannerToken).scope)
            assertFails { opened.connectNative() } // no permissive absent-peer startup
            assertTrue(opened.holds().isEmpty())
        }
        assertFailsWith<IllegalStateException> { runtime.connectNative() }
        runtime.close()
        assertFails { LocalH1Runtime.open(store,storeId,project,id(60),id(61),origin,nativeOrigin,proof,proof,ownerToken,
            protectedRoot.resolve("wrong-key"),listOf(root)) }
        val nativeSecrets=Files.createDirectory(protectedRoot.resolve("native"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        fun retained(kind: String,key: String,value: JsonValue): Path {
            val file=nativeSecrets.resolve("$kind-${dev.writhelm.core.knowledge.Digest.of(key.toByteArray()).hex}.json")
            Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            Files.write(file,StrictJson.encode(value))
            return file
        }
        val scoped=obj("formatVersion" to num(1),"kind" to str("native-scoped-key-v1"),"storeId" to uuid(storeId),"projectId" to uuid(project),
            "principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken))
        val retainedKey=retained("credential",plannerKey.toString(),scoped)
        listOf("service","transport").forEach { retained("native-peer",it,obj("formatVersion" to num(1),"kind" to str("native-peer-proof-v1"),
            "secret" to str(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proof)))) }
        LocalH1Runtime.openFromNativeCustody(store,storeId,project,id(60),id(61),origin,nativeOrigin,nativeSecrets,plannerKey,
            protectedRoot.resolve("from-native"),listOf(root)).use { runtime ->
            assertEquals(CredentialScope.Controller(id(60)),runtime.app.inspectAccess(plannerToken).scope)
        }
        Files.write(retainedKey,StrictJson.encode(JObject(scoped.entries+("principalId" to uuid(id(49999))))))
        assertFails { LocalH1Runtime.openFromNativeCustody(store,storeId,project,id(60),id(61),origin,nativeOrigin,nativeSecrets,plannerKey,
            protectedRoot.resolve("mismatched-native"),listOf(root)) }
        val restricted=root.resolve("plan-only")
        assertEquals(0,run(input(restricted,listOf("TASK_PLAN")),root).first)
        H1Application.open(restricted,storeId,H1Options(UnavailableH1RuntimeFacts)).use { app ->
            assertEquals(CredentialScope.Controller(id(60)),app.inspectAccess(plannerToken).scope)
            assertFails { app.coordinationCapture(plannerToken,project) } // no promotion of PLAN into dispatch
        }
        val malformed=root.resolve("invalid-permissions")
        assertEquals(2,run(input(malformed,listOf("TASK_PLAN","TASK_PLAN")),root).first)
        assertFalse(Files.exists(malformed))
    }
}
