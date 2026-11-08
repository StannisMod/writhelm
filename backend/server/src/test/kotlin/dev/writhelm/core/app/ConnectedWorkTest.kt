// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import java.nio.file.*
import java.util.UUID
import kotlin.test.*

/** Real canonical Git/service/CORE/AUTH/history and owned filesystem. Native is explicitly a MOCK;
 * synthetic fixture profile/OIDs are not installed-native, physical fencing or source-Git evidence. */
internal class ConnectedFixture(private val reviewerGap: Boolean=false,private val informationalNote: Boolean=false,
    private val initialCondition: Boolean=true,nativeSafety: NativeRuntimeSafety=UnavailableNativeRuntimeSafety,
    controllerSafety: NativeControllerRuntimeSafety=UnavailableNativeControllerSafety): AutoCloseable {
    val f=ReviewAppFixture();val root=directory();val consumerRoot=Files.createDirectory(root.resolve("consumer"))
    val repairRoot=Files.createDirectory(root.resolve("repair"));val store=directory()
    val consumerId=id(2004);val consumerKey=id(2007);val consumerSubject=WAssignmentSubject(consumerId,0)
    val consumerTask=WorkKey.TaskRevision(project,id(2000),0);val consumerPlan=WorkKey.Revision(project,id(2001),0)
    val consumerManifestKey=WorkKey.Id(project,id(2002));val consumerResource=WorkKey.Resource(project,id(2003))
    val consumerWorkspace=id(2008);val consumerIntent=id(2009)
    val consumerIntended=f.intended.copy(executionId=id(2011),nativeSessionId="ses_connected_consumer")
    val codec=WorkCodec();var sequence=3000
    val native=MockNative();val checkCustody=OwnedCheckCustody(root.resolve("reports"),mapOf(id(2800) to repairRoot))
    var nativeControl: ConnectedNativeControl=native
        private set
    val observed=linkedSetOf<WorkReference>()
    lateinit var app: H1Application;lateinit var checkpoint: WorkReference;lateinit var condition: WorkReference
    lateinit var before: WSnapshot;lateinit var preparation: LinuxOwnedDirectoryPreparation
    lateinit var options: H1Options;var connected: ConnectedRuntimeFacts?=null
    var engine: ConnectedWorkEngine?=null
    var driver: AuthorizedRebindDriver?=null
    private val delegate=H1RuntimeFacts { state,_ ->H1ObservedFacts(state.basis,f.runtimeFacts,f.observations.qualifiedProfiles) }
    val consumerManifest: WorkReference
    init {
        Files.writeString(consumerRoot.resolve("consumer.kt"),"consumer uses agreed mechanism\n")
        Files.writeString(consumerRoot.resolve("user.txt"),"UNRELATED_DIRTY_USER_EDIT\n")
        Files.writeString(repairRoot.resolve("mechanism.txt"),"broken\n")
        before=LinuxOwnedDirectoryPreparation.snapshot(consumerRoot,f.snapshot)
        val original=f.base.work.getValue(project)
        val criterion=WCriterion(id(96),"shared mechanism returns agreed value",listOf(f.source))
        val records=original.records.values.map { r -> when(val p=r.payload) {
            is WTaskRevisionsPayload ->r.copy(payload=WTaskRevisionsPayload(p.value.copy(criteria=listOf(criterion))))
            is WVerificationPlansPayload ->r.copy(payload=WVerificationPlansPayload(p.value.copy(criteria=listOf(criterion),gates=p.value.gates.map { it.copy(independent=true) })))
            else ->r
        } }.toMutableList()
        fun add(key: WorkKey,payload: WorkPayload): WorkRecord {
            val r=WorkRecord(WorkRecordIdentity(payloadFamily(payload),key),payload);records.add(r);return r
        }
        val terms=original.task(f.task)
        add(consumerTask,WTaskRevisionsPayload(terms.copy(objective="continue after the agreed mechanism is repaired",criteria=listOf(criterion),verificationPlan=consumerPlan)))
        add(consumerPlan,WVerificationPlansPayload(WVerificationPlans(consumerTask,null,listOf(criterion),listOf(WGate(id(2097),listOf(criterion.id),"pure",false,"acceptance",null,false,"consumer")),"consumer plan",emptyList())))
        val manifest=(original.resolve(f.manifest).payload as WInputManifestsPayload).value.copy(task=consumerTask)
        val m=add(consumerManifestKey,WInputManifestsPayload(manifest));consumerManifest=WorkReference(m.identity,codec.digest(m))
        val work=WorkState.restore(project,records,emptyList())
        options=H1Options(H1RuntimeFacts { state,command ->connected?.capture(state,command) ?: delegate.capture(state,command) },nativeSafety=nativeSafety,controllerSafety=controllerSafety)
        app=H1Application.initialize(store,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to work)),options)
        activate(f.assignment,f.task,f.manifest,f.workspace,f.resource,f.intent,f.correlation,f.intended,f.worker,f.workerKey,f.delegation)
        activate(consumerId,consumerTask,consumerManifest,consumerWorkspace,consumerResource,consumerIntent,id(2010),consumerIntended,id(2006),consumerKey,id(2005))
        checkpoint=checkpoint()
        val a=state().assignment(consumerId)
        val pause=id(2050);val correlation=id(2051)
        publish(CRequestNativeTransitionOperation(CRequestNativeTransition(consumerId,a.generation,pause,correlation,"pause",null,emptyList())))
        val execution=state().binding(consumerSubject).execution
        f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(pause to WorkNativeObservation(consumerSubject,pause,correlation,"pause","confirmed",listOf(f.source),execution,true,"active")))
        publish(CObserveNativeTransitionOperation(CObserveNativeTransition(consumerId,a.generation+1,pause,0,"confirmed",listOf(f.source),emptyList())))
        val plan=ConnectedPreparationPlan(consumerSubject,consumerSubject,checkpoint,before,before,"unchanged-base",null,
            listOf(WSourceVariant2Value(WSourceVariant2("work",checkpoint))),emptyList())
        preparation=LinuxOwnedDirectoryPreparation(listOf(OwnedPreparationRegistration(consumerRoot,plan)))
        connected=ConnectedRuntimeFacts(delegate,preparation,native,Model2ControllerExecution(f.controller,id(2900),f.intended.nativeHostId,"ses_connected_controller"),checkCustody::observed)
        if(initialCondition)condition=declareCondition("B")
    }
    fun state()=app.coordinationCapture(plannerToken,project).work
    fun publish(op: dev.writhelm.core.work.WorkOperation,who: String=plannerToken): CommandResult.Settled {
        val result=assertIs<CommandResult.Settled>(app.execute(who,codec.encodeCommand(WorkCommand(id(sequence++),project,op))))
        assertIs<LogicalOutcome.Success>(result.receipt.outcome,"${op.name}: ${result.receipt.outcome}")
        engine?.let { if(!Thread.holdsLock(it))it.awaitIdle() }
        return result
    }
    fun ref(result: CommandResult.Settled): WorkReference {
        val o=assertIs<LogicalOutcome.Success>(result.receipt.outcome)
        val r=H1Catalog.result(o.operation,o.result);val family=H1Catalog.family(r.family)
        return WorkReference(WorkRecordIdentity(family,identityCodec.decodeKey(family,StrictJson.encode(r.key))),digest(o.result.fields("key","generation","digest").getValue("digest")))
    }
    private fun activate(assignment: UUID,task: WorkKey.TaskRevision,manifest: WorkReference,workspace: UUID,resource: WorkKey.Resource,
        prepare: UUID,correlation: UUID,intended: WExecutionIntent,worker: UUID,key: UUID,delegation: UUID) {
        f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=f.runtimeFacts.verifiedPackets+manifest)
        publish(CReserveAssignmentOperation(CReserveAssignment(assignment,task,manifest,f.profile,workspace,listOf(CReserveAssignmentResourcesItem(resource,null)),emptyList(),prepare,correlation,emptyList(),intended)))
        val registration=Auth3Command(id(sequence++),project,Auth3Payload.RegisterDelegation(delegation,DelegationBinding(f.controller,project,worker,key,
            task.taskId,assignment,intended.executionId,0,DelegationTemplate.EXECUTOR,emptyList()),0,CredentialVerifier.digest(storeId,project,worker,key,secret(key.leastSignificantBits.toInt()))))
        assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(app.execute(plannerToken,Auth3CommandCodec().encode(registration))).receipt.outcome)
        val execution=WExecution(intended.executionId,worker,key,intended.nativeHostId,intended.nativeSessionId,workspace);val subject=WAssignmentSubject(assignment,0)
        f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(prepare to WorkNativeObservation(subject,prepare,correlation,"prepare","confirmed",listOf(f.source),execution,true,"reserved")))
        publish(CBindAssignmentOperation(CBindAssignment(assignment,1,prepare,0,intended.nativeHostId,intended.nativeSessionId,workspace,listOf(f.source),emptyList())))
        val intent=id(sequence++);val corr=id(sequence++)
        publish(CRequestNativeTransitionOperation(CRequestNativeTransition(assignment,2,intent,corr,"start",null,emptyList())))
        f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(subject,intent,corr,"start","confirmed",listOf(f.source),execution,false,"bound")))
        publish(CObserveNativeTransitionOperation(CObserveNativeTransition(assignment,3,intent,0,"confirmed",listOf(f.source),emptyList())))
    }
    private fun checkpoint(): WorkReference {
        val digest=Digest.of("mock native source cut".toByteArray());val cut=id(2020);val locator=id(2021)
        f.runtimeFacts=f.runtimeFacts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(consumerIntended.nativeHostId,consumerIntended.nativeSessionId,digest,digest,listOf("mock-source" to digest))))
        val worker=token(consumerKey)
        publish(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(project,cut),WSourceCuts(consumerTask,consumerSubject,consumerIntended.nativeHostId,
            consumerIntended.nativeSessionId,f.profile,1,digest,digest,locator,listOf(WSourceCutsNativeMessagesItem("mock-source",digest)),emptyList(),emptyList()),emptyList())),worker)
        val plan=WorkKey.Revision(project,id(2022),0);val view=WorkKey.Revision(project,id(2023),0);val memory=WorkKey.Revision(project,id(2024),0)
        publish(CUpdatePlanOperation(CUpdatePlan(plan,consumerTask,null,emptyList(),"waiting for mechanism",null,emptyList(),consumerSubject)),worker)
        publish(CRecordLoadedViewOperation(CRecordLoadedView(view,CRecordLoadedViewData(consumerTask,consumerManifest,consumerSubject,null,emptyList(),emptyList()),emptyList())),worker)
        val note=Utf8Content.fromText("Informational hypothesis, not a missing required input")
        val entries=if(informationalNote)listOf(WMemoryEntry(id(2026),"hypothesis",WorkBodyReference(note.digest,note.sizeBytes),listOf(f.source),emptyList(),
            "Not established; no remaining required obligation depends on this note",emptyList()))else emptyList()
        publish(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(consumerTask,consumerSubject,null,cut,entries,if(reviewerGap)listOf("mandatory source unavailable; required consumer context missing") else emptyList(),emptyList()),
            if(informationalNote)listOf(CRecordMemoryBodiesItem(note.digest,note.text()))else emptyList())),worker)
        val report=Utf8Content.fromText("unfinished; actual owned dirty snapshot captured")
        return ref(publish(CRecordCheckpointOperation(CRecordCheckpoint(WorkKey.Id(project,id(2025)),CRecordCheckpointData(consumerTask,consumerSubject,consumerManifest,
            view,cut,plan,0,memory,emptyList(),listOf(before),emptyList(),WorkBodyReference(report.digest,report.sizeBytes),"unfinished",null,null,null),4,
            listOf(CRecordCheckpointBodiesItem(report.digest,report.text())))),worker))
    }
    fun declareCondition(label: String,supersedes: List<WorkReference> = emptyList()): WorkReference {
        val private=Utf8Content.fromText("PRIVATE_CONDITION_SENTINEL: agreed mechanism $label")
        val notice=Utf8Content.fromText("Blocker $label resolved; continue.")
        return ref(publish(CRecordConditionOperation(CRecordCondition(WorkKey.Id(project,id(sequence++)),CRecordConditionData(consumerTask,consumerSubject,f.task,
            WorkBodyReference(private.digest,private.sizeBytes),WConditionAssessorVariant0Value(WConditionAssessorVariant0("controller",f.controller)),listOf(id(97)),listOf(f.source),
            CRecordConditionDataConsumerNotice(notice.digest,notice.sizeBytes.toLong()),state().reference(WorkFamily.VERIFICATION_PLANS,f.plan),supersedes,if(supersedes.isEmpty())emptyList()else listOf(f.source)),
            listOf(private,notice).filter { it.digest !in state().bodies }.map { CRecordConditionBodiesItem(it.digest,it.text()) }))))
    }
    fun checkpointWithNewGap(): WorkReference {
        val old=(state().resolve(checkpoint).payload as WCheckpointsPayload).value
        val memory=WorkKey.Revision(project,id(sequence++),0)
        publish(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(consumerTask,consumerSubject,old.memory,old.sourceCutId,emptyList(),
            listOf("required retained context unavailable after the previous cut"),emptyList()),emptyList())),token(consumerKey))
        val data=decodeCRecordCheckpointData(encodeWCheckpoints(old.copy(memory=memory)))
        return ref(publish(CRecordCheckpointOperation(CRecordCheckpoint(WorkKey.Id(project,id(sequence++)),data,state().assignment(consumerId).generation,emptyList())),token(consumerKey)))
    }
    fun startEngine() {
        engine=ConnectedWorkEngine(app,project,{plannerToken},preparation,nativeControl,LinuxConnectedCustody(root.resolve("custody")),driver)
        engine!!.tick()
        engine!!.awaitIdle()
    }
    fun installNative(control: ConnectedNativeControl,sourceEpoch: String?=null) {
        check(engine==null)
        nativeControl=control
        val provenance=Model2ControllerExecution(f.controller,id(2900),f.intended.nativeHostId,"ses_connected_controller")
        val observed=if(sourceEpoch!=null)NativeContextFacts(delegate,control as NativeSourceProofControl,sourceEpoch,provenance)else delegate
        connected=ConnectedRuntimeFacts(observed,preparation,control,provenance,checkCustody::observed)
    }
    /** Canonical completed logical handoff, explicitly MOCK implementation observations. No native
     * completion/fencing, result acceptance/integration or resource release is supplied. */
    fun finishConsumerHandoff() {
        val who=token(consumerKey);val subject=consumerSubject
        val a=state().assignment(consumerId);val cp=(state().resolve(a.checkpoint!!).payload as WCheckpointsPayload).value
        val text=Utf8Content.fromText("Complete consumer handoff fixture; no physical completion or independent verification asserted")
        val report=WorkBodyReference(text.digest,text.sizeBytes)
        val retro0=ref(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(sequence++)),
            CRecordRetrospectiveData(consumerTask,subject,null,"no-significant-new-findings",emptyList()),emptyList())),who))
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+before,
            submittedDiffs=f.runtimeFacts.submittedDiffs+WorkSubmittedDiff(consumerTask,listOf(before),report))
        val result=ref(publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(sequence++)),
            CSubmitResultData(consumerTask,subject,consumerManifest,listOf(before),report,state().task(consumerTask).criteria.map { it.id },emptyList(),retro0,emptyList()),
            listOf(CSubmitResultBodiesItem(text.digest,text.text())))),who))
        val retro=ref(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(sequence++)),
            CRecordRetrospectiveData(consumerTask,subject,result,"no-significant-new-findings",emptyList()),emptyList())),who))
        val plan=(state().record(WorkFamily.PLANS,cp.plan).payload as WPlansPayload).value
        publish(CUpdatePlanOperation(CUpdatePlan(cp.plan,consumerTask,plan.generation,plan.steps.map { it.copy(state="complete") },
            "logical handoff finished; acceptance remains separate",a.checkpoint,emptyList(),subject)),who)
        val finalPlan=(state().record(WorkFamily.PLANS,cp.plan).payload as WPlansPayload).value
        val notification=WorkKey.Id(project,id(sequence++))
        publish(CRecordDeliveryOperation(CRecordDelivery(notification,subject,cp.sourceCutId,consumerTask,report,null,
            "durable-delivered",listOf(f.source),emptyList())))
        publish(CRecordCheckpointOperation(CRecordCheckpoint(WorkKey.Id(project,id(sequence++)),
            CRecordCheckpointData(consumerTask,subject,consumerManifest,cp.loadedView,cp.sourceCutId,cp.plan,finalPlan.generation,cp.memory,
                finalPlan.steps,cp.artifacts,emptyList(),report,"finished",retro,result,notification),state().assignment(consumerId).generation,emptyList())),who)
    }
    fun reopen() {
        engine?.close();app.close()
        app=H1Application.open(store,storeId,options)
        startEngine()
    }
    fun repairAndEvidence(): Pair<WorkReference,WorkReference> {
        Files.writeString(repairRoot.resolve("mechanism.txt"),"fixed\n")
        val subject=WAssignmentSubject(f.assignment,0);val worker=token(f.workerKey)
        val retro=ref(publish(CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(sequence++)),CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())),worker))
        val snapshot=LinuxOwnedDirectoryPreparation.snapshot(repairRoot,f.snapshot.copy(retainedLocatorId=id(2801)))
        val changes=Utf8Content.fromText("Actual mechanism.txt bytes repaired; snapshot ${snapshot.dirtyContentDigest!!.hex}")
        val body=WorkBodyReference(changes.digest,changes.sizeBytes)
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+snapshot,submittedDiffs=setOf(WorkSubmittedDiff(f.task,listOf(snapshot),body)))
        val result=ref(publish(CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(sequence++)),CSubmitResultData(f.task,subject,f.manifest,listOf(snapshot),body,listOf(id(96)),emptyList(),retro,emptyList()),
            if(changes.digest in state().bodies)emptyList()else listOf(CSubmitResultBodiesItem(changes.digest,changes.text())))),worker))
        val run=checkCustody.compare(id(2800),"mechanism.txt",Utf8Content.fromText("fixed\n"),result,id(sequence++),
            listOf(WSignificantInput(f.source,"exact governing agreement")))
        val evidence=WEvidence(listOf(WSourceVariant2Value(WSourceVariant2("work",result))),listOf(WSignificantInput(f.source,"exact governing agreement")),
            "owned-file-byte-comparison-v1","pure",WorkBodyReference(run.environment.digest,run.environment.sizeBytes),run.runId,run.outcome,
            listOf(WorkBodyReference(run.report.digest,run.report.sizeBytes)),listOf(WEvidenceClauseCasesItem(f.source,"C01","file comparison")),
            listOf("Native is mock; source Git OIDs/profile are synthetic; filesystem bytes are actually read"),WorkBodyReference(run.report.digest,run.report.sizeBytes))
        val data=decodeCRecordEvidenceData(encodeWEvidence(evidence))
        val evidenceRef=ref(publish(CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(sequence++)),data,
            listOf(run.report,run.environment).filter { it.digest !in state().bodies }.map { CRecordEvidenceBodiesItem(it.digest,it.text()) })),worker))
        checkCustody.bind(evidenceRef,evidence)
        return result to evidenceRef
    }
    fun confirm(result: WorkReference,evidence: WorkReference,target: WorkReference=condition,applicability: WorkReference?=null) {
        val assessment=Utf8Content.fromText("Designated assessor confirms actual repair against unchanged agreed behavior")
        publish(CConfirmConditionOperation(CConfirmCondition(WorkKey.Id(project,id(sequence++)),CConfirmConditionData(target,result,listOf(evidence),
            WorkBodyReference(assessment.digest,assessment.sizeBytes),"resolved",emptyList(),emptyList(),applicability),
            if(assessment.digest in state().bodies)emptyList()else listOf(CConfirmConditionBodiesItem(assessment.digest,assessment.text())))))
    }
    fun confirmationCommand(result: WorkReference,evidence: WorkReference): WorkCommand {
        val assessment=Utf8Content.fromText("bounded negative-assessment attempt")
        return WorkCommand(id(sequence++),project,CConfirmConditionOperation(CConfirmCondition(WorkKey.Id(project,id(sequence++)),CConfirmConditionData(condition,result,listOf(evidence),
            WorkBodyReference(assessment.digest,assessment.sizeBytes),"resolved",emptyList(),emptyList(),null),listOf(CConfirmConditionBodiesItem(assessment.digest,assessment.text())))))
    }
    fun authorizeRebind(): Pair<WAssignmentSubject,WSnapshot> {
        val current=state();val old=current.assignment(consumerId)
        val retained=Files.createDirectory(root.resolve("retained-new-base"))
        Files.writeString(retained.resolve("consumer.kt"),"consumer at explicitly authorized base uses same agreed mechanism\n")
        Files.copy(consumerRoot.resolve("user.txt"),retained.resolve("user.txt"))
        val after=LinuxOwnedDirectoryPreparation.snapshot(retained,before.copy(tree=WorkGitOid("sha1","c".repeat(40)),retainedLocatorId=id(2701)))
        val task=consumerTask.copy(revision=1);val planKey=consumerPlan.copy(revision=1)
        val oldPlan=(current.record(WorkFamily.VERIFICATION_PLANS,consumerPlan).payload as WVerificationPlansPayload).value
        val terms=current.task(consumerTask).copy(base=current.task(consumerTask).base!!.copy(code=listOf(after)),verificationPlan=planKey)
        val taskRef=ref(publish(CCreateTaskRevisionOperation(CCreateTaskRevision(task,terms,
            CCreateTaskRevisionNewVerificationPlan(planKey,oldPlan.copy(task=task,previous=null)),emptyList()))))
        val manifestData=(current.resolve(consumerManifest).payload as WInputManifestsPayload).value.copy(task=task,base=terms.base!!)
        val manifest=ref(publish(CPinInputManifestOperation(CPinInputManifest(WorkKey.Id(project,id(2702)),manifestData,emptyList()))))
        f.runtimeFacts=f.runtimeFacts.copy(verifiedPackets=f.runtimeFacts.verifiedPackets+manifest,retainedSnapshots=f.runtimeFacts.retainedSnapshots+after)
        val intended=consumerIntended.copy(executionId=id(2710),nativeSessionId="ses_authorized_rebound")
        val enrollment=RebindEnrollment(id(2711),id(2712),id(2713),CredentialVerifier.digest(storeId,project,id(2712),id(2713),secret(2713)))
        val target=WAssignmentSubject(consumerId,1)
        val bindingRecord=WorkRecord(WorkRecordIdentity(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,consumerId,1)),
            WAssignmentBindingsPayload(WAssignmentBindings(task,manifest,f.profile,WExecution(intended.executionId,enrollment.principalId,enrollment.credentialId,
                intended.nativeHostId,intended.nativeSessionId,consumerWorkspace),consumerWorkspace,old.requiredResources,intended,enrollment.delegationId,emptyList())))
        val newBinding=WorkReference(bindingRecord.identity,codec.digest(bindingRecord))
        val decisions=listOf<WSource>(WSourceVariant2Value(WSourceVariant2("work",taskRef)))
        val recoveryKey=WorkKey.Id(project,UUID.nameUUIDFromBytes("rebind:$consumerSubject:$task:recovery".toByteArray(Charsets.UTF_8)))
        val recoveryRecord=WorkRecord(WorkRecordIdentity(WorkFamily.RECOVERIES,recoveryKey),WRecoveriesPayload(WRecoveries(consumerSubject,"bind-existing",decisions,emptyList(),old.requiredResources,emptyList())))
        val recovery=WorkReference(recoveryRecord.identity,codec.digest(recoveryRecord))
        val newPlan=ConnectedPreparationPlan(consumerSubject,target,checkpoint,before,after,"authorized-rebind",
            WContinuationRebind(requireNotNull(old.binding),newBinding,task,manifest,decisions,listOf(WSourceVariant2Value(WSourceVariant2("work",recovery)))),
            decisions,listOf(ExactFileChange("consumer.kt",Digest.of(Files.readAllBytes(consumerRoot.resolve("consumer.kt"))),Utf8Content.fromBytes(Files.readAllBytes(retained.resolve("consumer.kt"))))))
        preparation=LinuxOwnedDirectoryPreparation(listOf(OwnedPreparationRegistration(consumerRoot,newPlan)))
        connected=ConnectedRuntimeFacts(delegate,preparation,native,Model2ControllerExecution(f.controller,id(2900),f.intended.nativeHostId,"ses_connected_controller"),checkCustody::observed)
        val lifecycle=object: ConnectedRebindLifecycle {
            override fun stopAndReconcile(capture: CoordinationCapture,subject: WAssignmentSubject,intent: WorkKey.Id): List<WSource> {
                val i=(capture.work.record(WorkFamily.NATIVE_INTENTS,intent).payload as WNativeIntentsPayload).value
                f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(intent.id to WorkNativeObservation(subject,intent.id,i.correlationId,"stop","confirmed",decisions,
                    capture.work.binding(subject).execution,true,"paused")),completedAssignments=setOf(subject))
                return decisions
            }
            override fun preparePassiveAndReconcile(capture: CoordinationCapture,subject: WAssignmentSubject,intent: WorkKey.Id): List<WSource> {
                val i=(capture.work.record(WorkFamily.NATIVE_INTENTS,intent).payload as WNativeIntentsPayload).value
                val execution=WExecution(intended.executionId,enrollment.principalId,enrollment.credentialId,intended.nativeHostId,intended.nativeSessionId,consumerWorkspace)
                f.runtimeFacts=f.runtimeFacts.copy(native=mapOf(intent.id to WorkNativeObservation(subject,intent.id,i.correlationId,"prepare","confirmed",decisions,execution,true,"reserved")))
                return decisions
            }
        }
        driver=AuthorizedRebindDriver(app,project,{plannerToken},ExactRebindAuthorization(consumerSubject,task,manifest,f.profile,checkpoint,intended,enrollment,decisions,
            Utf8Content.fromText("Authorized base revision applied; blocker B resolved; continue.")),lifecycle,LinuxConnectedCustody(root.resolve("rebind-custody")))
        return target to after
    }
    override fun close() { engine?.close();app.close() }
    class MockNative: ConnectedNativeControl {
        var sends=0;var wakes=0;var releases=0;var dropAfterWake=false;var unknown=false
        var qualifiedNotPerformedOnce=false
        var reconciliationAvailable=true
        var afterWake: (()->Unit)?=null
        var allowHold: ()->Boolean={true};var failRelease=false
        private var accepted: ConnectedEnvelope?=null;var closed=true
        val events=mutableListOf<String>();val pauseIntents=linkedSetOf<WorkKey.Id>()
        var onPause: ((WorkKey.Id)->Unit)?=null
        var pauseObservation: WorkNativeObservation?=null
        var pauseTarget: ConnectedBindingTarget?=null
        override fun reconcilePause(target: ConnectedBindingTarget,intent: WorkKey.Id)=pauseObservation
        override fun hold(envelope: ConnectedEnvelope): Boolean { check(closed) { "cannot silently reclose ACTIVE" };return allowHold() }
        override fun rehold(target: ConnectedBindingTarget) { events.add("rehold");closed=true }
        override fun pause(target: ConnectedBindingTarget,intent: WorkKey.Id) {
            pauseTarget=target
            check(closed);events.add("pause");pauseIntents.add(intent);onPause?.invoke(intent)
        }
        override fun deliver(envelope: ConnectedEnvelope): NativeConnectedOutcome {
            sends++;check(closed)
            if(qualifiedNotPerformedOnce && sends==1)return NativeConnectedOutcome.NotPerformed
            if(unknown)return NativeConnectedOutcome.Unknown
            if(accepted!=null && accepted!=envelope)return NativeConnectedOutcome.Conflict
            accepted=envelope;wakes++ // intentionally a duplicate would wake again, like pinned S03
            afterWake?.also { afterWake=null;it() }
            return if(dropAfterWake)NativeConnectedOutcome.Unknown else NativeConnectedOutcome.MatchedWake
        }
        override fun reconcile(envelope: ConnectedEnvelope): NativeConnectedOutcome = when {
            !reconciliationAvailable -> NativeConnectedOutcome.Unknown
            qualifiedNotPerformedOnce && sends==1 && accepted==null -> NativeConnectedOutcome.NotPerformed
            unknown -> NativeConnectedOutcome.Unknown
            accepted==envelope -> NativeConnectedOutcome.MatchedWake
            accepted!=null -> NativeConnectedOutcome.Conflict
            else -> NativeConnectedOutcome.Unknown
        }
        override fun release(envelope: ConnectedEnvelope) { check(accepted==envelope);check(!failRelease);if(closed)releases++;closed=false }
    }
}

class ConnectedWorkTest {
    @Test fun provisionalNativeEnvelopeAndExactPacketAreCurrentReadProjectionsNotSyntheticCanonicalWork() {
        ConnectedFixture().use { f ->
            val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            val capture=f.app.coordinationCapture(plannerToken,project)
            val original=ConnectedRuntimeFacts.provisionalEnvelope(capture.work,capture.basis,f.consumerSubject)
            val records=capture.work.records.size
            val status=f.app.nativeRuntimeRead(plannerToken,project,f.f.controller,f.f.intended.nativeHostId,
                f.consumerSubject,original.binding,original.continuation.id) as JObject
            assertEquals(digest(Digest.of(original.bytes())),status.entries.getValue("envelopeDigest"))
            assertSame(JNull,status.entries.getValue("attemptId"))
            assertEquals(JBoolean(false),status.entries.getValue("quiescent")) // no physical qualification from paused alone
            val packet=f.app.nativeRuntimeRead(plannerToken,project,f.f.controller,f.f.intended.nativeHostId,
                f.consumerSubject,original.binding,original.continuation.id,true)
            assertFalse(StrictJson.encode(packet,4*1024*1024).decodeToString().contains("PRIVATE_CONDITION_SENTINEL"))
            assertEquals(records,f.state().records.size)
            assertTrue(f.state().records.values.none { it.payload is WContinuationsPayload })
            assertFails { f.app.nativeRuntimeRead(plannerToken,project,f.f.controller,f.f.intended.nativeHostId,
                f.consumerSubject,original.binding,id(39999)) }
            f.startEngine()
            val c=f.state().records.values.single { it.payload is WContinuationsPayload }
            assertEquals(original.continuation,c.identity.key)
            val after=f.app.nativeRuntimeRead(plannerToken,project,f.f.controller,f.f.intended.nativeHostId,
                f.consumerSubject,original.binding,original.continuation.id,true) as JObject
            val before=packet as JObject
            assertEquals(JBoolean(true),before.entries.getValue("freshEnvelope"))
            assertEquals(JBoolean(false),after.entries.getValue("freshEnvelope"))
            // Current permission to initialize custody changes, never the immutable base/source bytes.
            assertContentEquals(StrictJson.encode(JObject(before.entries-"freshEnvelope"),4*1024*1024),
                StrictJson.encode(JObject(after.entries-"freshEnvelope"),4*1024*1024))
        }
    }
    @Test fun publicationHintDoesNotHoldServiceMonitorAcrossNativeCallbackIO() {
        ConnectedFixture().use { f ->
            val (result,evidence)=f.repairAndEvidence()
            java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout=10000
                val callback=java.util.concurrent.Executors.newSingleThreadExecutor()
                try {
                    val recaptured=callback.submit<Boolean> {
                        server.accept().use { socket ->
                            socket.soTimeout=10000
                            assertEquals(17,socket.getInputStream().read())
                            // Separate I/O callback thread recaptures the SAME application's monitor.
                            // Old synchronous publication->tick->native->callback deadlocked here.
                            val work=f.state()
                            socket.getOutputStream().write(23)
                            work.assignment(f.consumerId).currentIntent!=null
                        }
                    }
                    f.native.afterWake={
                        java.net.Socket(java.net.InetAddress.getLoopbackAddress(),server.localPort).use { socket ->
                            socket.soTimeout=10000
                            socket.getOutputStream().write(17)
                            assertEquals(23,socket.getInputStream().read())
                        }
                    }
                    f.startEngine()
                    f.confirm(result,evidence)
                    assertTrue(recaptured.get(10,java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals("active",f.state().assignment(f.consumerId).state)
                    assertEquals(1,f.native.sends)
                    assertEquals(1,f.native.wakes)
                } finally { callback.shutdownNow() }
            }
        }
    }
    @Test fun declarationForkNeedsCompleteExplicitCorrectionEvenWhenBothBranchesWereAssessedResolved() {
        ConnectedFixture().use { f ->
            val left=f.declareCondition("fork-left",listOf(f.condition))
            val (r,e)=f.repairAndEvidence();val applicability=refApplicability(f,r,e)
            f.confirm(r,e,left,applicability)
            val work=f.state();val row=work.resolve(left)
            val right=row.copy(identity=WorkRecordIdentity(WorkFamily.CONDITIONS,WorkKey.Id(project,id(39800))))
            val rightRef=WorkReference(right.identity,f.codec.digest(right))
            val confirmation=work.records.values.single { it.payload is WConditionConfirmationsPayload }
            val confirmationValue=(confirmation.payload as WConditionConfirmationsPayload).value
            val rightConfirmation=confirmation.copy(identity=WorkRecordIdentity(WorkFamily.CONDITION_CONFIRMATIONS,WorkKey.Id(project,id(39801))),
                payload=WConditionConfirmationsPayload(confirmationValue.copy(condition=rightRef)))
            // A cold metadata frontier, not a claim that the live reducer admitted a stale tip:
            // both branches retain attributed valid assessment and share the exact same predecessor.
            val fork=WorkState.restore(project,work.records.values+listOf(right,rightConfirmation),work.bodies.values)
            assertFailsWith<WorkFault> { Model2Inspector(fork).resolvedPairs(f.consumerSubject) }
            val other=f.declareCondition("independent-current-tip")
            val corrected=f.declareCondition("complete-correction",listOf(left,other))
            f.confirm(r,e,corrected,applicability);f.startEngine()
            assertEquals("active",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
            assertEquals(1,f.native.sends)
        }
    }
    @Test fun confirmationForkNeedsCompleteCorrectionAndKeepsOldAssessments() {
        ConnectedFixture().use { f ->
            val (r,e)=f.repairAndEvidence();f.confirm(r,e);f.confirm(r,e)
            assertFailsWith<WorkFault> { Model2Inspector(f.state()).resolvedPairs(f.consumerSubject) }
            val old=Model2Inspector(f.state()).confirmationFrontier(f.condition)
            val command=(f.confirmationCommand(r,e).operation as CConfirmConditionOperation).value
            f.publish(CConfirmConditionOperation(command.copy(data=command.data.copy(supersedes=listOf(old.first()),correctionBasis=listOf(f.f.source)))))
            assertFailsWith<WorkFault> { Model2Inspector(f.state()).resolvedPairs(f.consumerSubject) }
            val complete=Model2Inspector(f.state()).confirmationFrontier(f.condition)
            f.publish(CConfirmConditionOperation(command.copy(key=WorkKey.Id(project,id(f.sequence++)),data=command.data.copy(supersedes=complete,correctionBasis=listOf(f.f.source)),bodies=emptyList())))
            assertEquals(4,f.state().records.values.count { it.payload is WConditionConfirmationsPayload })
            f.startEngine();assertEquals("active",f.state().assignment(f.consumerId).state)
        }
    }
    @Test fun assessmentRequiresExactGateRepairDigestAndObservedRunNotGenericDoneOrInventedMetadata() {
        ConnectedFixture().use { f ->
            val (result,evidence)=f.repairAndEvidence()
            assertEquals("paused",f.state().assignment(f.consumerId).state) // submitted DONE/result is not condition clearance
            fun reject(op: dev.writhelm.core.work.WorkOperation) {
                val answer=assertIs<CommandResult.Settled>(f.app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(f.sequence++),project,op))))
                assertIs<LogicalOutcome.Rejected>(answer.receipt.outcome,op.name)
            }
            val c=(f.state().resolve(f.condition).payload as WConditionsPayload).value
            reject(CRecordConditionOperation(CRecordCondition(WorkKey.Id(project,id(f.sequence++)),CRecordConditionData(c.consumerTask,c.consumer,c.responsibleTask,
                c.condition,c.assessor,listOf(id(99999)),c.agreement,CRecordConditionDataConsumerNotice(c.consumerNotice.digest,c.consumerNotice.byteLength),
                c.governingPlan,emptyList(),emptyList()),emptyList())))
            val original=(f.confirmationCommand(result,evidence).operation as CConfirmConditionOperation).value
            reject(CConfirmConditionOperation(original.copy(data=original.data.copy(result=WorkReference(result.identity,Digest.of("wrong repair digest".toByteArray()))))))
            val real=(f.state().resolve(evidence).payload as WEvidencePayload).value
            val mutations=listOf(real.copy(significantInputs=listOf(WSignificantInput(f.f.source,"changed significant input on genuine run"))),
                real.copy(runId=id(99800),significantInputs=listOf(WSignificantInput(f.f.source,"invented run/significant-input assertion"))),
                real.copy(boundary="native"),real.copy(clauseCases=emptyList()))
            for(mutated in mutations) {
                val ref=f.ref(f.publish(CRecordEvidenceOperation(CRecordEvidence(WorkKey.Id(project,id(f.sequence++)),decodeCRecordEvidenceData(encodeWEvidence(mutated)),emptyList())),token(f.f.workerKey)))
                if(mutated.significantInputs!=real.significantInputs || mutated.boundary!=real.boundary) {
                    assertFails { f.checkCustody.bind(ref,mutated) }
                    assertFalse(ref in OwnedCheckCustody(f.root.resolve("reports"),mapOf(id(2800) to f.repairRoot)).observed(f.state()))
                }
                reject(CConfirmConditionOperation(original.copy(key=WorkKey.Id(project,id(f.sequence++)),data=original.data.copy(evidence=listOf(ref)))))
            }
            assertTrue(f.state().records.values.none { it.payload is WConditionConfirmationsPayload })
            assertFailsWith<WorkFault> { Model2Inspector(f.state()).resolvedPairs(f.consumerSubject) }
            assertEquals(0,f.native.sends)
        }
    }
    @Test fun serviceQualifiedNotPerformedRetainsOriginalAttemptAndOnlyThenLinksRetry() {
        ConnectedFixture().use { f ->
            f.native.qualifiedNotPerformedOnce=true;f.startEngine();val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            assertEquals("active",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
            assertEquals(2,f.native.sends);assertEquals(1,f.native.wakes)
            val row=f.state().records.values.single { it.payload is WContinuationsPayload }
            val continuation=(row.payload as WContinuationsPayload).value
            val custody=f.root.resolve("custody")
            assertTrue(Files.list(custody).use { files ->files.anyMatch { it.fileName.toString().endsWith(".dispatch") } })
            val operations=Files.list(custody).use { files ->files.filter { it.fileName.toString().endsWith(".command") }
                .map { f.codec.decodeCommand(Files.readAllBytes(it)).operation }.toList() }
                .filterIsInstance<CObserveContinuationOperation>().map { it.value }
            val absent=operations.single { it.outcome=="not-performed" }
            assertTrue(operations.any { it.outcome=="armed" && it.effect.previousAttempt==absent.effect.attemptId &&
                it.effect.attemptId!=absent.effect.attemptId && it.effect.requestKey!=absent.effect.requestKey })
            assertEquals("resume-confirmed",continuation.phase)
            f.reopen();assertEquals(2,f.native.sends);assertEquals(1,f.native.wakes)
        }
    }
    @Test fun partialMultifilePreparationAndChangedRegistrationNeverGuessReplayOrOverwrite() {
        ConnectedFixture().use { f ->
            val root=f.consumerRoot;Files.writeString(root.resolve("second.txt"),"old second\n")
            val before=LinuxOwnedDirectoryPreparation.snapshot(root,f.before)
            val destination=Files.createDirectory(f.root.resolve("expected-multifile"))
            Files.writeString(destination.resolve("consumer.kt"),"new first\n")
            Files.writeString(destination.resolve("second.txt"),"new second\n")
            Files.copy(root.resolve("user.txt"),destination.resolve("user.txt"))
            val after=LinuxOwnedDirectoryPreparation.snapshot(destination,before)
            val changes=listOf(ExactFileChange("consumer.kt",Digest.of(Files.readAllBytes(root.resolve("consumer.kt"))),Utf8Content.fromText("new first\n")),
                ExactFileChange("second.txt",Digest.of(Files.readAllBytes(root.resolve("second.txt"))),Utf8Content.fromText("new second\n")))
            val plan=ConnectedPreparationPlan(f.consumerSubject,f.consumerSubject,f.checkpoint,before,after,"rebind",null,listOf(f.f.source),changes)
            val registration=OwnedPreparationRegistration(root,plan)
            val adapter=LinuxOwnedDirectoryPreparation(listOf(registration))
            assertIs<PreparationOutcome.Held>(adapter.prepare(plan.copy(basis=emptyList())))
            assertIs<PreparationOutcome.Held>(adapter.reconcile(plan.copy(basis=emptyList())))
            val wrong=plan.copy(changes=changes.map { it.copy(before=Digest.of("wrong preimage".toByteArray())) })
            assertIs<PreparationOutcome.Held>(LinuxOwnedDirectoryPreparation(listOf(OwnedPreparationRegistration(root,wrong))).prepare(wrong))
            Files.writeString(root.resolve("consumer.kt"),"new first\n") // exact cut after first of two writes, not an OS-crash claim
            val fresh=LinuxOwnedDirectoryPreparation(listOf(registration))
            assertIs<PreparationOutcome.Unknown>(fresh.reconcile(plan))
            assertIs<PreparationOutcome.Held>(fresh.prepare(plan))
            assertEquals("new first\n",Files.readString(root.resolve("consumer.kt")))
            assertEquals("old second\n",Files.readString(root.resolve("second.txt")))
            assertEquals("UNRELATED_DIRTY_USER_EDIT\n",Files.readString(root.resolve("user.txt")))
            val custody=LinuxConnectedCustody(f.root.resolve("original-key-conflict"))
            custody.retain(id(39900),"command","original exact request".toByteArray())
            assertFails { custody.retain(id(39900),"command","changed original request".toByteArray()) }
        }
    }
    @Test fun coldContinuationEdgeRejectsFullyRehashedAttemptGenerationAndNativeEnvelopeForgeries() {
        ConnectedFixture().use { f ->
            f.startEngine();val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            assertEquals("active",f.state().assignment(f.consumerId).state)
            f.close()
            RuntimeOwner.acquire(f.store).use { owner ->
                val git=NativeGit(owner)
                fun snapshot(oid: GitOid): StoreSnapshot {
                    val entries=git.call(listOf("ls-tree","-rz","--full-tree",oid.hex)).checked().decodeToString().split('\u0000')
                        .filter { it.isNotEmpty() }.associate { line ->
                            val parts=line.split('\t');parts[1] to GitOid("sha1",parts[0].substringAfterLast(' '))
                        }
                    return StoreSnapshot(oid,entries,git)
                }
                val c=snapshot(git.head());val h=snapshot(git.oid(git.call(listOf("rev-parse","${c.oid.hex}^")).checked()))
                val receiptPath=(c.entries.keys-h.entries.keys).single { it.startsWith("commands/receipts/") }
                val receipt=JournalCodec.decode(git.readBlob(c.entries.getValue(receiptPath)),receiptPath)
                assertEquals("observe_continuation",receipt.identity.operation)
                H1BoundaryValidator(H1SnapshotCache(storeId)).validate(h,c,receipt)
                val state=H1SnapshotCache(storeId).load(c).work.getValue(project)
                val row=state.records.values.single { it.payload is WContinuationsPayload }
                val original=(row.payload as WContinuationsPayload).value
                val path=identityCodec.recordPath(row.identity)
                fun commit(files: Map<String,ByteArray>): StoreSnapshot {
                    class Node { val dirs=sortedMapOf<String,Node>();val files=sortedMapOf<String,GitOid>() }
                    val root=Node();val entries=files.mapValues { git.blob(it.value) }
                    entries.forEach { (name,oid) ->var node=root;val parts=name.split('/');parts.dropLast(1).forEach { node=node.dirs.getOrPut(it) { Node() } };node.files[parts.last()]=oid }
                    fun tree(node: Node): GitOid {
                        val lines=node.files.map { (name,oid) ->name to "100644 blob ${oid.hex}\t$name\u0000" }+
                            node.dirs.map { (name,child) ->"$name/" to "040000 tree ${tree(child).hex}\t$name\u0000" }
                        return git.oid(git.call(listOf("mktree","-z"),lines.sortedBy { it.first }.joinToString("") { it.second }.toByteArray()).checked())
                    }
                    return StoreSnapshot(git.commit(tree(root),h.oid),entries,git)
                }
                for(forged in listOf(original.copy(generation=original.generation+1),original.copy(nativeMessageId="msg_forged"),
                    original.copy(effect=original.effect.copy(attemptId=id(39999))))) {
                    val bytes=f.codec.encodeRecord(row.copy(payload=WContinuationsPayload(forged)))
                    val digest=Digest.of(bytes).hex
                    val fields=(receipt.outcome as LogicalOutcome.Success).result.fields("key","generation","digest")
                    val outcome=LogicalOutcome.Success(receipt.identity.operation,obj("key" to fields.getValue("key"),"generation" to JNumber(forged.generation),"digest" to str(digest)),false)
                    val writes=receipt.writes.map { if(it.record.path==path)WriteDescriptor(it.record,it.beforeDigest,digest)else it }
                    val hostile=Receipt(storeId,receipt.identity,receipt.fingerprintVersion,receipt.fingerprint,receipt.validatedAt,
                        receipt.authenticatedByCredentialId,outcome,writes,receipt.journalVersion)
                    val files=c.entries.mapValues { git.readBlob(it.value) }.toMutableMap()
                    files[path]=bytes;files[receiptPath]=JournalCodec.encode(hostile)
                    assertFails { H1BoundaryValidator(H1SnapshotCache(storeId)).validate(h,commit(files),hostile) }
                }
            }
        }
    }
    @Test fun conditionBodyBoundaryDeniesOtherSameRecordWrongSubjectAndForeignParentThenRecapturesReadLoss() {
        ConnectedFixture().use { f ->
            val c=(f.state().resolve(f.condition).payload as WConditionsPayload).value
            val other=f.declareCondition("foreign-parent")
            fun body(digest: Digest,parent: WorkReference,who: String=token(f.consumerKey))=f.app.read2(who,
                f.codec.encodeRead(WorkRead.Body(project,digest,WorkRead.Exact(project,parent.identity,parent.digest,null))))
            assertFailsWith<AppAccessFault> { body(c.condition.digest,f.condition) }
            assertFailsWith<AppAccessFault> { body(c.notice().digest,f.condition,token(f.f.workerKey)) }
            assertFailsWith<AppAccessFault> { body(c.notice().digest,other) }
            assertTrue(StrictJson.encode(body(c.notice().digest,f.condition)).decodeToString().contains("Blocker B resolved"))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(ownerToken,
                CommandCodec().encode(CoreCommand(id(39200),project,CommandPayload.Revoke(id(2006)))))).receipt.outcome)
            assertFailsWith<AppAccessFault> { body(c.notice().digest,f.condition) }
            assertFailsWith<AppAccessFault> { f.app.read2(token(f.consumerKey),f.codec.encodeRead(WorkRead.ConditionStatus(project,
                WorkRead.Exact(project,f.condition.identity,f.condition.digest,null)))) }
        }
    }
    @Test fun requiredArtifactAvailabilityWithdrawalHoldsExistingResumeAtActualServiceSeam() {
        ConnectedFixture(initialCondition=false).use { f ->
            val before=f.state().assignment(f.consumerId)
            val manifest=(f.state().resolve(f.consumerManifest).payload as WInputManifestsPayload).value
            assertTrue(manifest.items.any { it.mandatory && it.source==f.f.source })
            f.f.runtimeFacts=f.f.runtimeFacts.copy(retainedSnapshots=f.f.runtimeFacts.retainedSnapshots-f.f.snapshot)
            val op=CRequestNativeTransitionOperation(CRequestNativeTransition(f.consumerId,before.generation,id(39400),id(39401),"resume",f.checkpoint,emptyList()))
            val answer=assertIs<CommandResult.Settled>(f.app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(39402),project,op))))
            assertIs<LogicalOutcome.Rejected>(answer.receipt.outcome);assertTrue(answer.receipt.writes.isEmpty())
            assertEquals(before,f.state().assignment(f.consumerId));assertEquals(0,f.native.sends)
            f.f.runtimeFacts=f.f.runtimeFacts.copy(retainedSnapshots=f.f.runtimeFacts.retainedSnapshots+f.f.snapshot)
            f.publish(op)
        }
    }
    @Test fun linkedOldConditionBodyIsNotRecursivelyDisclosedAndCursorRecapturesCurrentRead() {
        ConnectedFixture().use { f ->
            val old=f.condition;val c=(f.state().resolve(old).payload as WConditionsPayload).value
            val current=f.declareCondition("corrected",listOf(old))
            val currentValue=(f.state().resolve(current).payload as WConditionsPayload).value
            assertEquals(listOf(old),currentValue.supersedes)
            fun body(digest: Digest,parent: WorkReference,who: String)=f.app.read2(who,f.codec.encodeRead(WorkRead.Body(project,digest,
                WorkRead.Exact(project,parent.identity,parent.digest,null))))
            assertFailsWith<AppAccessFault> { body(c.condition.digest,current,token(f.consumerKey)) }
            assertFailsWith<AppAccessFault> { body(c.notice().digest,current,token(f.consumerKey)) }
            body(currentValue.notice().digest,current,token(f.consumerKey))
            fun listing(cursor: java.util.UUID?=null)=f.codec.encodeRead(WorkRead.Listing(project,RListWorkRecords("conditions",null,cursor,1)))
            val data=(f.app.read2(plannerToken,listing()) as JObject).entries.getValue("data").fields("snapshotId","nextCursor","complete","items")
            val cursor=uuid(data.getValue("nextCursor"))
            assertFailsWith<AppAccessFault> { f.app.read2(token(f.consumerKey),listing(cursor)) }
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(ownerToken,
                CommandCodec().encode(CoreCommand(id(39500),project,CommandPayload.Revoke(planner))))).receipt.outcome)
            assertFailsWith<AppAccessFault> { f.app.read2(plannerToken,listing(cursor)) }
            assertFailsWith<AppAccessFault> { body(currentValue.notice().digest,current,plannerToken) }
        }
    }
    @Test fun designatedReviewerAssessmentUsesExactGroupBindingAndCurrentExecutionGeneration() {
        ConnectedFixture().use { f ->
            val (result,evidence)=f.repairAndEvidence()
            val task=f.f.task.copy(taskId=id(39600));val plan=f.f.plan.copy(id=id(39601))
            val original=f.state().task(f.f.task)
            val terms=original.copy(kind="verification",verificationPlan=plan,dependencies=listOf(WDependency(f.f.task,"implemented",result,emptyList(),"review exact repair")))
            val oldPlan=(f.state().record(WorkFamily.VERIFICATION_PLANS,f.f.plan).payload as WVerificationPlansPayload).value
            f.publish(CCreateTaskRevisionOperation(CCreateTaskRevision(task,terms,CCreateTaskRevisionNewVerificationPlan(plan,oldPlan.copy(task=task)),emptyList())))
            val manifest=f.ref(f.publish(CPinInputManifestOperation(CPinInputManifest(WorkKey.Id(project,id(39602)),
                WInputManifests(task,requireNotNull(terms.base),f.f.applicability,listOf(WManifestItem(f.f.source,true,"review exact result",emptyList()),
                    WManifestItem(WSourceVariant2Value(WSourceVariant2("work",evidence)),true,"exact observed check assigned for assessment",emptyList())),f.f.source,emptyList()),emptyList()))))
            f.f.runtimeFacts=f.f.runtimeFacts.copy(verifiedPackets=f.f.runtimeFacts.verifiedPackets+manifest)
            val subject=WAssignmentSubject(id(39603),0);val prepare=id(39604);val correlation=id(39605);val worker=id(39606);val key=id(39607);val workspace=id(39608)
            val intended=f.f.intended.copy(executionId=id(39609),template="reviewer-v1",nativeSessionId="ses_designated_condition_reviewer")
            f.publish(CReserveAssignmentOperation(CReserveAssignment(subject.assignmentId,task,manifest,f.f.profile,workspace,
                listOf(CReserveAssignmentResourcesItem(WorkKey.Resource(project,id(39610)),null)),listOf(result),prepare,correlation,emptyList(),intended)))
            val enrollment=Auth3Command(id(39611),project,Auth3Payload.RegisterDelegation(id(39612),DelegationBinding(f.f.controller,project,worker,key,
                task.taskId,subject.assignmentId,intended.executionId,0,DelegationTemplate.REVIEWER,listOf(result)),0,
                CredentialVerifier.digest(storeId,project,worker,key,secret(key.leastSignificantBits.toInt()))))
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(plannerToken,Auth3CommandCodec().encode(enrollment))).receipt.outcome)
            val execution=WExecution(intended.executionId,worker,key,intended.nativeHostId,intended.nativeSessionId,workspace)
            f.f.runtimeFacts=f.f.runtimeFacts.copy(native=mapOf(prepare to WorkNativeObservation(subject,prepare,correlation,"prepare","confirmed",listOf(f.f.source),execution,true,"reserved")))
            f.publish(CBindAssignmentOperation(CBindAssignment(subject.assignmentId,1,prepare,0,intended.nativeHostId,intended.nativeSessionId,workspace,listOf(f.f.source),emptyList())))
            val start=id(39613);val corr=id(39614)
            f.publish(CRequestNativeTransitionOperation(CRequestNativeTransition(subject.assignmentId,2,start,corr,"start",null,emptyList())))
            f.f.runtimeFacts=f.f.runtimeFacts.copy(native=mapOf(start to WorkNativeObservation(subject,start,corr,"start","confirmed",listOf(f.f.source),execution,false,"bound")))
            f.publish(CObserveNativeTransitionOperation(CObserveNativeTransition(subject.assignmentId,3,start,0,"confirmed",listOf(f.f.source),emptyList())))
            val c=(f.state().resolve(f.condition).payload as WConditionsPayload).value
            val binding=f.state().reference(WorkFamily.ASSIGNMENT_BINDINGS,WorkKey.AssignmentBinding(project,subject.assignmentId,0))
            fun designate(s: WAssignmentSubject)=CRecordConditionOperation(CRecordCondition(WorkKey.Id(project,id(f.sequence++)),CRecordConditionData(c.consumerTask,c.consumer,c.responsibleTask,
                c.condition,WConditionAssessorVariant1Value(WConditionAssessorVariant1("reviewer",s,binding)),c.requiredGateIds,c.agreement,
                CRecordConditionDataConsumerNotice(c.consumerNotice.digest,c.consumerNotice.byteLength),c.governingPlan,emptyList(),emptyList()),emptyList()))
            val stale=assertIs<CommandResult.Settled>(f.app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(f.sequence++),project,designate(subject.copy(generation=1))))))
            assertIs<LogicalOutcome.Rejected>(stale.receipt.outcome)
            val condition=f.ref(f.publish(designate(subject)))
            val (outsideResult,outsideEvidence)=f.repairAndEvidence() // genuine result outside the exact bound review group
            val outside=(f.confirmationCommand(outsideResult,outsideEvidence).operation as CConfirmConditionOperation).value
            assertIs<CommandResult.Denied>(f.app.execute(token(key),f.codec.encodeCommand(WorkCommand(id(f.sequence++),project,
                CConfirmConditionOperation(outside.copy(data=outside.data.copy(condition=condition)))))))
            val op=(f.confirmationCommand(result,evidence).operation as CConfirmConditionOperation).value
            val confirmation=f.ref(f.publish(CConfirmConditionOperation(op.copy(data=op.data.copy(condition=condition))),token(key)))
            val assessed=(f.state().resolve(confirmation).payload as WConditionConfirmationsPayload).value
            val provenance=assertIs<WConditionAssessorProvenanceVariant1Value>(assessed.assessedBy).value
            assertEquals(subject,provenance.assignment);assertEquals(binding,provenance.binding);assertEquals(execution,provenance.execution)
            assertNotEquals((f.state().resolve(result).payload as WResultsPayload).value.producer.executionId,execution.executionId)
        }
    }
    @Test fun delegatedH1RotationRecapturesOriginalCredentialAndRejectsStaleAssignmentGuard() {
        ConnectedFixture().use { f ->
            val who=token(f.consumerKey)
            val a=f.state().assignment(f.consumerId)
            val key=WorkKey.Revision(project,id(39700),0)
            val command=WorkCommand(id(39701),project,CUpdatePlanOperation(CUpdatePlan(key,f.consumerTask,null,emptyList(),
                "original delegated credential receipt",null,emptyList(),f.consumerSubject)))
            val bytes=f.codec.encodeCommand(command)
            val original=assertIs<CommandResult.Settled>(f.app.execute(who,bytes))
            assertIs<LogicalOutcome.Success>(original.receipt.outcome)
            fun rotate(expected: Long,newKey: java.util.UUID): CommandResult =f.app.execute(plannerToken,Auth3CommandCodec().encode(Auth3Command(id(f.sequence++),project,
                Auth3Payload.RotateDelegatedCredential(id(2005),f.consumerKey,0,0,expected,newKey,
                    CredentialVerifier.digest(storeId,project,id(2006),newKey,secret(newKey.leastSignificantBits.toInt()))))))
            assertIs<LogicalOutcome.Rejected>(assertIs<CommandResult.Settled>(rotate(a.generation-1,id(39702))).receipt.outcome)
            val newKey=id(39703)
            assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(rotate(a.generation,newKey)).receipt.outcome)
            assertIs<CommandResult.Denied>(f.app.execute(who,bytes))
            assertFailsWith<AppAccessFault> { f.app.read2(who,f.codec.encodeRead(WorkRead.ConditionStatus(project,
                WorkRead.Exact(project,f.condition.identity,f.condition.digest,null)))) }
            val replay=assertIs<CommandResult.Settled>(f.app.execute(token(newKey),bytes))
            assertTrue(replay.commandReplayed);assertEquals(original.receipt.authenticatedByCredentialId,replay.receipt.authenticatedByCredentialId)
            assertEquals(f.consumerKey,replay.receipt.authenticatedByCredentialId)
            f.app.read2(token(newKey),f.codec.encodeRead(WorkRead.ConditionStatus(project,WorkRead.Exact(project,f.condition.identity,f.condition.digest,null))))
            assertFails { f.app.continuationPermit(plannerToken,project,f.consumerSubject.copy(generation=1),a.binding!!) }
        }
    }
    @Test fun firstConditionOnOrdinaryActiveBindingHoldsAndPausesWithoutContinuation() {
        ConnectedFixture(initialCondition=false).use { f ->
            val a=f.state().assignment(f.consumerId);val intent=id(39800);val correlation=id(39801)
            f.publish(CRequestNativeTransitionOperation(CRequestNativeTransition(f.consumerId,a.generation,intent,correlation,"resume",f.checkpoint,emptyList())))
            f.f.runtimeFacts=f.f.runtimeFacts.copy(native=mapOf(intent to WorkNativeObservation(f.consumerSubject,intent,correlation,"resume","confirmed",
                listOf(f.f.source),f.state().binding(f.consumerSubject).execution,false,"paused")))
            f.publish(CObserveNativeTransitionOperation(CObserveNativeTransition(f.consumerId,a.generation+1,intent,0,"confirmed",listOf(f.f.source),emptyList())))
            val before=f.state().assignment(f.consumerId)
            val claims=before.requiredResources.map { f.state().record(WorkFamily.CLAIMS,it) }
            assertEquals("active",before.state)
            assertTrue(f.state().records.values.none { it.payload is WContinuationsPayload })
            f.native.closed=false;f.startEngine()
            assertFalse(f.native.closed) // valid no-condition ACTIVE operation is preserved
            f.native.onPause={ key ->
                val current=f.state().assignment(f.consumerId)
                assertTrue(f.native.closed);assertTrue(current.state in setOf("pausing","unknown"));assertEquals(key,current.currentIntent)
                assertEquals(before.binding,current.binding);assertEquals(before.executionGeneration,current.executionGeneration)
            }
            f.declareCondition("first-ACTIVE-condition")
            assertFails { f.app.continuationPermit(plannerToken,project,f.consumerSubject,before.binding!!) }
            assertTrue(f.native.closed)
            assertEquals(listOf("rehold","pause"),f.native.events.take(2))
            val pending=f.state().assignment(f.consumerId).currentIntent!!
            assertEquals("pausing",f.state().assignment(f.consumerId).state)
            f.native.closed=false;f.reopen();assertTrue(f.native.closed)
            assertEquals(pending,f.state().assignment(f.consumerId).currentIntent)
            assertEquals(ConnectedRuntimeFacts.bindingTarget(f.state(),f.consumerSubject),f.native.pauseTarget)
            val pause=(f.state().record(WorkFamily.NATIVE_INTENTS,pending).payload as WNativeIntentsPayload).value
            f.native.pauseObservation=WorkNativeObservation(f.consumerSubject,pending.id,pause.correlationId,"pause","unknown",
                listOf(f.f.source),f.state().binding(f.consumerSubject).execution,false,"active")
            f.engine!!.tick();assertEquals("unknown",f.state().assignment(f.consumerId).state)
            f.native.pauseObservation=f.native.pauseObservation!!.copy(outcome="confirmed")
            f.engine!!.tick();assertEquals("unknown",f.state().assignment(f.consumerId).state)
            f.native.pauseObservation=f.native.pauseObservation!!.copy(writersFenced=true)
            f.engine!!.tick();assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertNull(f.state().assignment(f.consumerId).currentIntent)
            assertEquals(claims,before.requiredResources.map { f.state().record(WorkFamily.CLAIMS,it) })
            assertTrue(f.state().records.values.none { it.payload is WContinuationsPayload })
            assertEquals(0,f.native.sends);assertEquals(0,f.native.wakes)
        }
    }
    @Test fun requiredCheckpointGapGatesLegacyResumeEvenWithoutAnyCondition() {
        for(gap in listOf(false,true))ConnectedFixture(reviewerGap=gap,initialCondition=false).use { f ->
            val a=f.state().assignment(f.consumerId)
            assertTrue(Model2Inspector(f.state()).frontier(f.consumerSubject).isEmpty())
            val op=CRequestNativeTransitionOperation(CRequestNativeTransition(f.consumerId,a.generation,id(39100),id(39101),"resume",f.checkpoint,emptyList()))
            val result=assertIs<CommandResult.Settled>(f.app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(39102),project,op))))
            if(gap)assertIs<LogicalOutcome.Rejected>(result.receipt.outcome)else assertIs<LogicalOutcome.Success>(result.receipt.outcome)
            assertEquals(if(gap)null else WorkKey.Id(project,id(39100)),f.state().assignment(f.consumerId).currentIntent)
            assertEquals("paused",f.state().assignment(f.consumerId).state)
        }
    }
    @Test fun informationalUncertaintyDoesNotBecomeARequiredGapButNewExactCheckpointDoesGateCurrentPermit() {
        ConnectedFixture(informationalNote=true).use { f ->
            f.startEngine();val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            assertEquals("active",f.state().assignment(f.consumerId).state)
            val old=f.checkpoint
            val latest=f.checkpointWithNewGap()
            assertNotEquals(old,latest)
            assertEquals(latest,f.state().assignment(f.consumerId).checkpoint)
            assertFails { f.app.continuationPermit(plannerToken,project,f.consumerSubject,f.state().assignment(f.consumerId).binding!!) }
            assertTrue(f.native.closed);assertEquals("pausing",f.state().assignment(f.consumerId).state)
            assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes)
        }
    }
    @Test fun pauseReconciliationKeepsUnknownUntilExactQualifiedWriterObservation() {
        ConnectedFixture().use { f ->
            f.startEngine();val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            f.native.onPause={ key ->
                if(f.native.pauseObservation==null) {
                    val i=(f.state().record(WorkFamily.NATIVE_INTENTS,key).payload as WNativeIntentsPayload).value
                    f.native.pauseObservation=WorkNativeObservation(f.consumerSubject,key.id,i.correlationId,"pause","unknown",
                        listOf(f.f.source),f.state().binding(f.consumerSubject).execution,false,"active")
                }
            }
            f.declareCondition("pause-unknown")
            assertEquals("unknown",f.state().assignment(f.consumerId).state);assertTrue(f.native.closed)
            val intent=f.state().assignment(f.consumerId).currentIntent!!
            f.native.pauseObservation=f.native.pauseObservation!!.copy(outcome="confirmed",writersFenced=false)
            f.engine!!.tick() // confirmation without actual fencing must not clear unknown
            assertEquals("unknown",f.state().assignment(f.consumerId).state)
            f.native.pauseObservation=f.native.pauseObservation!!.copy(writersFenced=true)
            f.engine!!.tick()
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertNull(f.state().assignment(f.consumerId).currentIntent)
            assertEquals(setOf(intent),f.native.pauseIntents);assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes)
            assertFails { f.app.continuationPermit(plannerToken,project,f.consumerSubject,f.state().assignment(f.consumerId).binding!!) }
        }
    }
    @Test fun newBlockerReholdsBeforeDurablePauseAndRestartCannotRewakeOrFabricateFencing() {
        ConnectedFixture().use { f ->
            f.startEngine();val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            val before=f.state().assignment(f.consumerId)
            val claims=before.requiredResources.map { f.state().record(WorkFamily.CLAIMS,it) }
            f.native.onPause={ key ->
                val a=f.state().assignment(f.consumerId)
                assertEquals("pausing",a.state);assertEquals(key,a.currentIntent)
                assertEquals(before.executionGeneration,a.executionGeneration);assertEquals(before.binding,a.binding)
            }
            f.declareCondition("after-ACTIVE")
            assertTrue(f.native.closed);assertEquals(listOf("rehold","pause"),f.native.events.take(2))
            val intent=f.state().assignment(f.consumerId).currentIntent
            assertNotNull(intent);assertEquals(1,f.native.pauseIntents.size)
            assertEquals(claims,before.requiredResources.map { f.state().record(WorkFamily.CLAIMS,it) })
            assertFails { f.app.continuationPermit(plannerToken,project,f.consumerSubject,before.binding!!) }
            f.native.closed=false // lose local admission index; canonical pausing must reconstruct it
            f.reopen();assertTrue(f.native.closed)
            assertEquals(intent,f.state().assignment(f.consumerId).currentIntent)
            assertEquals("pausing",f.state().assignment(f.consumerId).state)
            assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes);assertEquals(1,f.native.pauseIntents.size)
        }
    }
    @Test fun checkpointGapCannotBypassPreparationOrOldResumeOrCurrentPermit() {
        ConnectedFixture(reviewerGap=true).use { f ->
            val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            val captured=f.app.coordinationCapture(plannerToken,project);val work=captured.work
            val a=work.assignment(f.consumerId);val inspector=Model2Inspector(work)
            assertFailsWith<WorkFault> { f.preparation.plan(captured,f.consumerSubject) }
            assertFailsWith<WorkFault> { inspector.requireActivation(f.consumerSubject,false) }
            assertFailsWith<WorkFault> { inspector.requireActivation(f.consumerSubject,true) }
            assertFails { f.app.continuationPermit(plannerToken,project,f.consumerSubject,a.binding!!) }
            val pairs=inspector.resolvedPairs(f.consumerSubject)
            val notice=(work.resolve(f.condition).payload as WConditionsPayload).value.notice()
            val prepare=CPrepareContinuationOperation(CPrepareContinuation(WorkKey.Id(project,id(39000)),CPrepareContinuationData(
                f.consumerSubject,f.consumerSubject,a.binding!!,f.checkpoint,f.checkpoint,pairs,
                inspector.coverage(f.consumerSubject,WorkGitOid(captured.basis.algorithm,captured.basis.hex)),a.generation,f.before,f.before,
                "unchanged-base",null,listOf(WSourceVariant2Value(WSourceVariant2("work",f.checkpoint))),"msg_gap_bypass",
                CPrepareContinuationDataNotification(notice.digest,notice.byteLength.toLong()),null),emptyList()))
            for(op in listOf(prepare,CRequestNativeTransitionOperation(CRequestNativeTransition(f.consumerId,a.generation,id(39001),id(39002),"resume",f.checkpoint,emptyList())))) {
                val result=assertIs<CommandResult.Settled>(f.app.execute(plannerToken,f.codec.encodeCommand(WorkCommand(id(f.sequence++),project,op))))
                assertIs<LogicalOutcome.Rejected>(result.receipt.outcome,op.name)
            }
            assertEquals("paused",f.state().assignment(f.consumerId).state);assertEquals(0,f.native.sends)
        }
    }
    @Test fun completeFrontierBeyondThirtyTwoIsNotTruncatedAndExhaustionIsNotEmpty() {
        ConnectedFixture().use { f ->
            repeat(33) { f.declareCondition("unlinked-$it") }
            val work=f.state();val inspector=Model2Inspector(work)
            assertEquals(34,inspector.frontier(f.consumerSubject).size)
            assertEquals(34L,inspector.coverage(f.consumerSubject,WorkGitOid("sha1","1".repeat(40))).conditionCount)
            f.startEngine();val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            assertEquals("paused",f.state().assignment(f.consumerId).state);assertEquals(0,f.native.sends)
            val record=work.resolve(f.condition)
            val extras=(0..256).map { record.copy(identity=WorkRecordIdentity(WorkFamily.CONDITIONS,WorkKey.Id(project,id(40000+it)))) }
            val over=WorkState.restore(project,work.records.values.filter { it.payload !is WConditionsPayload }+extras,work.bodies.values)
            assertFails { Model2Inspector(over).frontier(f.consumerSubject) }
        }
    }
    @Test fun reviewerRequiredCheckpointMemoryGapMustHold() {
        ConnectedFixture(reviewerGap=true).use { f ->
            val checkpoint=(f.state().resolve(f.checkpoint).payload as WCheckpointsPayload).value
            val memory=(f.state().record(WorkFamily.MEMORY,checkpoint.memory).payload as WMemoryPayload).value
            assertTrue(memory.gaps.isNotEmpty())
            f.startEngine(); val (r,e)=f.repairAndEvidence(); f.confirm(r,e)
            println("REVIEW memoryGaps=${memory.gaps} active=${f.state().assignment(f.consumerId).state} sends=${f.native.sends} closed=${f.native.closed}")
            assertEquals("paused",f.state().assignment(f.consumerId).state,"R3.4/C09: unresolved checkpoint context gap must hold continuation")
        }
    }

    @Test fun reviewerNewBlockerAfterReleaseMustCloseBoundary() {
        ConnectedFixture().use { f ->
            f.startEngine(); val (r,e)=f.repairAndEvidence(); f.confirm(r,e)
            assertEquals("active",f.state().assignment(f.consumerId).state)
            assertFalse(f.native.closed)
            f.declareCondition("post-release-D")
            f.engine!!.tick()
            println("REVIEW active=${f.state().assignment(f.consumerId).state} closed=${f.native.closed} sends=${f.native.sends} holds=${f.engine!!.holds}")
            assertTrue(f.native.closed,"R3.7: new authoritative blocker must hold the local next-work boundary")
        }
    }
    @Test fun reviewerFreshCheckCustodyReconstructsFromRetainedBytes() {
        ConnectedFixture().use { f ->
            val (r,e)=f.repairAndEvidence()
            val fresh=OwnedCheckCustody(f.root.resolve("reports"),mapOf(id(2800) to f.repairRoot))
            assertTrue(e in fresh.observed(f.state()))
            Files.writeString(f.repairRoot.resolve("mechanism.txt"),"changed after restart\n")
            assertFalse(e in fresh.observed(f.state()))
        }
    }

    @Test fun serviceReopenAtRetainedStageCutsReconcilesOriginalEffectWithoutPlannerRelay() {
        for(cut in listOf("prepared","environment-confirmed","delivery-armed","delivery-confirmed","active-before-release"))ConnectedFixture().use { f ->
            f.native.allowHold={
                val c=f.state().records.values.mapNotNull { (it.payload as? WContinuationsPayload)?.value }.singleOrNull()
                c==null || if(cut=="delivery-armed") !(c.phase=="in-flight" && c.stage=="delivery") else c.phase!=cut
            }
            f.native.failRelease=cut=="active-before-release"
            f.startEngine();val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            val before=f.state().records.values.single { it.payload is WContinuationsPayload }
            val c=(before.payload as WContinuationsPayload).value
            assertTrue(f.native.closed,cut)
            assertEquals(if(cut=="active-before-release")"resume-confirmed" else if(cut=="delivery-armed")"in-flight" else cut,c.phase,cut)
            val sends=f.native.sends
            f.native.allowHold={true};f.native.failRelease=false
            f.reopen()
            assertEquals("active",f.state().assignment(f.consumerId).state,"$cut: ${f.engine!!.holds}")
            assertEquals(1,f.native.sends,cut);assertEquals(1,f.native.wakes,cut)
            assertFalse(f.native.closed,cut)
            val after=f.state().records.values.single { it.payload is WContinuationsPayload }
            assertEquals(before.identity,after.identity,cut)
            f.engine!!.tick();assertEquals(1,f.native.sends,"$cut (prior sends=$sends)")
        }
    }
    @Test fun unknownNativeEffectRemainsHeldAcrossServiceReopenWithoutBlindRetry() {
        ConnectedFixture().use { f ->
            f.native.unknown=true;f.startEngine();val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            assertEquals(1,f.native.sends);assertEquals(0,f.native.wakes)
            f.reopen();f.engine!!.tick()
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertEquals(1,f.native.sends);assertEquals(0,f.native.releases);assertTrue(f.native.closed)
        }
    }
    @Test fun newUnlinkedBlockerAtWakeRecaptureCannotActivateEvenAfterNotificationWasAdmitted() {
        ConnectedFixture().use { f ->
            f.startEngine();f.native.afterWake={ f.declareCondition("late-D") }
            val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertEquals(1,f.native.wakes);assertEquals(0,f.native.releases);assertTrue(f.native.closed)
            assertEquals(2,Model2Inspector(f.state()).frontier(f.consumerSubject).size)
        }
    }
    @Test fun revokedDelegationOrReadLossAtWakeHoldsActualCurrentHActivation() {
        for(mode in listOf("delegation","read"))ConnectedFixture().use { f ->
            f.startEngine();f.native.afterWake={
                val bytes=if(mode=="delegation")Auth3CommandCodec().encode(Auth3Command(id(9500),project,Auth3Payload.RevokeDelegation(id(2005),0)))
                    else CommandCodec().encode(CoreCommand(id(9501),project,CommandPayload.Revoke(id(2006))))
                assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(ownerToken,bytes)).receipt.outcome)
            }
            val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertTrue(f.native.closed);assertEquals(0,f.native.releases)
            val read=f.codec.encodeRead(WorkRead.ConditionStatus(project,WorkRead.Exact(project,f.condition.identity,f.condition.digest,null)))
            assertFailsWith<AppAccessFault> { f.app.read2(token(f.consumerKey),read) }
        }
    }
    @Test fun dispatchLossHoldsControllerButDoesNotImplicitlyRevokeIndependentWorkerDelegation() {
        ConnectedFixture().use { f ->
            f.startEngine();f.native.afterWake={
                val command=Auth3Command(id(9510),project,Auth3Payload.ReplaceWorkGrants(planner,0,workGrantDigest(project,planner,WorkPermission.entries.toSet()),
                    WorkPermission.entries.filter { it!=WorkPermission.TASK_DISPATCH }))
                assertIs<LogicalOutcome.Success>(assertIs<CommandResult.Settled>(f.app.execute(ownerToken,Auth3CommandCodec().encode(command))).receipt.outcome)
            }
            val (r,e)=f.repairAndEvidence();f.confirm(r,e)
            assertTrue(f.native.closed);assertEquals(0,f.native.releases)
            val workerUpdate=CUpdatePlanOperation(CUpdatePlan(WorkKey.Revision(project,id(9511),0),f.consumerTask,null,emptyList(),
                "own retained bookkeeping still allowed under WA07",null,emptyList(),f.consumerSubject))
            f.publish(workerUpdate,token(f.consumerKey))
            val status=f.app.read2(ownerToken,Auth2ReadCodec().encode(Auth2Read(project,Auth2ReadOperation.DELEGATION_STATUS,id(2005)))) as JObject
            assertEquals("active",(status.entries.getValue("data") as JObject).entries.getValue("status").string())
        }
    }
    @Test fun wrongAssessorAndReattributedOrNoLongerObservedEvidenceRefuseWithoutClearingCondition() {
        ConnectedFixture().use { f ->
            val (r,e)=f.repairAndEvidence();val command=f.confirmationCommand(r,e)
            val bytes=f.codec.encodeCommand(command)
            assertIs<CommandResult.Denied>(f.app.execute(readerToken,bytes))
            assertIs<CommandResult.Denied>(f.app.execute(token(f.f.workerKey),bytes))
            Files.writeString(f.repairRoot.resolve("mechanism.txt"),"changed after recorded check\n")
            val rejected=assertIs<CommandResult.Settled>(f.app.execute(plannerToken,bytes))
            assertEquals("NOT_READY",assertIs<LogicalOutcome.Rejected>(rejected.receipt.outcome).code)
            assertTrue(rejected.receipt.writes.isEmpty())
            assertTrue(Model2Inspector(f.state()).confirmationFrontier(f.condition).isEmpty())
        }
    }
    @Test fun enginePerformsExistingAuthorizedRebindThenFreshSuccessorAssessmentAndExactBasePreparation() {
        ConnectedFixture().use { f ->
            val (target,after)=f.authorizeRebind();f.startEngine()
            val dirty=Files.readAllBytes(f.consumerRoot.resolve("user.txt"))
            val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            assertEquals(1L,f.state().assignment(f.consumerId).executionGeneration,"${f.engine!!.holds}")
            assertEquals("bound",f.state().assignment(f.consumerId).state)
            assertEquals(0,f.native.sends)
            val successor=f.state().records.values.single { (it.payload as? WConditionsPayload)?.value?.let { c ->c.consumer==target && f.condition in c.supersedes }==true }
            val comparison=refApplicability(f,result,evidence)
            f.confirm(result,evidence,WorkReference(successor.identity,f.codec.digest(successor)),comparison)
            assertEquals("active",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
            assertEquals(after, LinuxOwnedDirectoryPreparation.snapshot(f.consumerRoot,after))
            val oldNotice=(f.state().resolve(f.condition).payload as WConditionsPayload).value.notice()
            assertFailsWith<AppAccessFault> { f.app.read2(token(f.consumerKey),f.codec.encodeRead(WorkRead.Body(project,oldNotice.digest,
                WorkRead.Exact(project,f.condition.identity,f.condition.digest,null)))) }
            f.app.inspectAccess(token(id(2713))) // actual current-generation credential retains READ
            assertFailsWith<AppAccessFault> { f.app.read2(token(id(2713)),f.codec.encodeRead(WorkRead.Body(project,oldNotice.digest,
                WorkRead.Exact(project,f.condition.identity,f.condition.digest,null)))) }
            assertContentEquals(dirty,Files.readAllBytes(f.consumerRoot.resolve("user.txt")))
            assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes)
            assertTrue(f.state().records.values.any { (it.payload as? WAssignmentBindingsPayload)?.value?.intendedExecution==f.consumerIntended })
        }
    }
    private fun refApplicability(f: ConnectedFixture,result: WorkReference,evidence: WorkReference): WorkReference = f.ref(f.publish(
        CRecordEvidenceApplicabilityOperation(CRecordEvidenceApplicability(WorkKey.Id(project,id(2790)),CRecordEvidenceApplicabilityData(result,
            listOf(CRecordEvidenceApplicabilityDataEvidenceItem(evidence,"retained",listOf(WSignificantInput(f.f.source,"same agreed mechanism check; consumer base change explicitly assessed")),"exact repair/check subjects unchanged")),
            emptyList(),listOf(f.f.source)),emptyList()))))
    @Test fun engineDrivesActualServiceAndFilesystemWithoutPlannerRelayAfterAssessorConfirmation() {
        ConnectedFixture().use { f ->
            f.startEngine()
            val unchanged=Files.readAllBytes(f.consumerRoot.resolve("user.txt"))
            val (result,evidence)=f.repairAndEvidence()
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            f.confirm(result,evidence) // assessor input, not a planner diff/continue relay
            assertEquals("active",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
            assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes);assertFalse(f.native.closed)
            assertContentEquals(unchanged,Files.readAllBytes(f.consumerRoot.resolve("user.txt")))
            f.engine!!.tick();assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes)
            val status=f.app.read2(token(f.consumerKey),f.codec.encodeRead(WorkRead.ConditionStatus(project,WorkRead.Exact(project,f.condition.identity,f.condition.digest,null))))
            assertFalse(StrictJson.encode(status).decodeToString().contains("PRIVATE_CONDITION_SENTINEL"))
            assertFailsWith<AppAccessFault> { f.app.read2(token(f.consumerKey),f.codec.encodeRead(WorkRead.Exact(project,f.condition.identity,f.condition.digest,null))) }
            val notice=(f.state().resolve(f.condition).payload as WConditionsPayload).value.notice()
            val body=f.app.read2(token(f.consumerKey),f.codec.encodeRead(WorkRead.Body(project,notice.digest,WorkRead.Exact(project,f.condition.identity,f.condition.digest,null))))
            assertTrue(StrictJson.encode(body).decodeToString().contains("Blocker B resolved; continue."))
        }
    }
    @Test fun lostWakeAcknowledgementReconcilesWithoutSecondSynthetic() {
        ConnectedFixture().use { f ->
            f.native.dropAfterWake=true;f.native.reconciliationAvailable=false
            f.startEngine();val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes)
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            f.native.reconciliationAvailable=true // receiver evidence becomes available for restart reconciliation
            f.reopen() // fresh service/engine, same canonical frontier and exact retained operation custody
            assertEquals("active",f.state().assignment(f.consumerId).state,"${f.engine!!.holds}")
            assertEquals(1,f.native.sends);assertEquals(1,f.native.wakes)
        }
    }
    @Test fun unlinkedOtherConditionAndDirtyConflictHoldOnlyAffectedContinuation() {
        ConnectedFixture().use { f ->
            f.startEngine();f.declareCondition("D")
            val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertEquals(0,f.native.sends)
            assertEquals("active",f.state().assignment(f.f.assignment).state)
        }
        ConnectedFixture().use { f ->
            f.startEngine();Files.writeString(f.consumerRoot.resolve("consumer.kt"),"CONFLICTING_WORKER_EDIT\n")
            val (result,evidence)=f.repairAndEvidence();f.confirm(result,evidence)
            assertEquals("paused",f.state().assignment(f.consumerId).state)
            assertEquals("CONFLICTING_WORKER_EDIT\n",Files.readString(f.consumerRoot.resolve("consumer.kt")))
            assertEquals(0,f.native.sends)
            assertEquals("active",f.state().assignment(f.f.assignment).state)
        }
    }
}
