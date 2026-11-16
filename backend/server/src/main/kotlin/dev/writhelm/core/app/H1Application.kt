// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import dev.writhelm.core.workflow.*
import java.nio.file.Path
import java.util.UUID
import dev.writhelm.core.auth2.WorkOperation as AuthorityOperation

/** Trusted same-H adapter input only. No default provider, actor override or permissive runtime. */
internal fun interface H1RuntimeFacts { fun capture(state: H1BoundState,command: WorkCommand): H1ObservedFacts }
/** Trusted platform observer, never a wire assertion. No installed physical-access profile has
 * been qualified: production's explicit unavailable adapter holds; component profiles use MOCK. */
internal fun interface NativeRuntimeSafety { fun safeAccess(state: H1BoundState,subject: WAssignmentSubject): Boolean }
internal object UnavailableNativeRuntimeSafety: NativeRuntimeSafety {
    override fun safeAccess(state: H1BoundState,subject: WAssignmentSubject)=false
}
internal fun interface NativeControllerRuntimeSafety { fun safeAccess(state: H1BoundState,controller: UUID): Boolean }
internal object UnavailableNativeControllerSafety: NativeControllerRuntimeSafety {
    override fun safeAccess(state: H1BoundState,controller: UUID)=false
}
/** Trusted deployment qualification for this exact retention effect; never supplied by a worker. */
internal fun interface RetentionEffectQualification {
    fun qualified(state:H1BoundState,project:UUID,subject:WAssignmentSubject,execution:UUID,result:WSnapshot):Boolean
}
internal class H1Options(val runtime: H1RuntimeFacts,val app: AppOptions=AppOptions(admission=AdmissionPolicy(formatVersion=2)),
    val commandReplyBytes: Int=65536,val navigationClock: ()->Long={System.nanoTime()/1000000},
    val nativeSafety: NativeRuntimeSafety=UnavailableNativeRuntimeSafety,
    val controllerSafety: NativeControllerRuntimeSafety=UnavailableNativeControllerSafety,
    val workModelVersion:Int=2,val retentionEffectQualification:RetentionEffectQualification=RetentionEffectQualification { _,_,_,_,_->false },
    val candidateAssemblyProof:(UUID)->JsonValue?={null},
    val candidateAssemblyProofByController:(UUID,UUID)->JsonValue?={ operation,_->candidateAssemblyProof(operation) },
    val candidateReviewTickets:(UUID)->CandidateReviewTicketIssuer?={null}) {
    init { require(app.admission.formatVersion==2 && commandReplyBytes in 1024..65536 && workModelVersion in setOf(2,4)) }
}
internal class H1Genesis(val core: ApprovalWorkflow,val auth: Collection<Auth2Record>,val work: Map<UUID,WorkState>)
internal enum class ReservationRetryEligibility { HOLD, DEFINITIVE_REFUSAL }
internal enum class OriginalResultReceipt { MATCH, MISSING, HOLD }
/** Exact durable original receipt from a validated snapshot, never a caller-provided outcome. */
internal fun definitiveReservationRefusal(snapshot:StoreSnapshot,storeId:UUID,principal:UUID,command:WorkCommand):Boolean {
    if(command.operation !is CReserveAssignmentOperation)return false
    val identity=dev.writhelm.core.store.CommandIdentity(principal,command.projectId,"reserve_assignment",command.requestKey)
    val bytes=snapshot.file(identity.path) ?: return false
    val receipt=JournalCodec.decode(bytes,identity.path)
    if(receipt.identity!=identity || receipt.storeId!=storeId || receipt.fingerprintVersion!=3 ||
        receipt.fingerprint!=WorkCodec().fingerprint(command).hex || receipt.writes.isNotEmpty())return false
    val rejected=receipt.outcome as? LogicalOutcome.Rejected ?: return false
    return rejected.domain=="work" && rejected.code in setOf("NOT_READY","BLOCKED_INPUT","CLAIM_CONFLICT","STALE_GENERATION")
}

/** One persistent H1 publisher profile. Every callback sees one bounded request-local presentation;
 * synchronized execution/read and finally cleanup prevent credential sharing across callers. */
internal class H1Application private constructor(private val storeId: UUID,private val options: H1Options): AutoCloseable {
    private lateinit var store: GitCommandStore
    private val cache=H1SnapshotCache(storeId,options.app.limits,options.workModelVersion)
    private val boundary=H1BoundaryValidator(cache)
    private val reduction=H1JointReduction()
    private val legacy=H1LegacyReduction(options.app.limits)
    private val protected=H1ProtectedReads()
    private val navigation=H1Navigation(options.navigationClock)
    private val legacyReads=H1LegacyReads(navigation)
    private class Request(val presentation: PresentedCredential,val command: StoreCommand) {
        var candidate: H1JointCandidate?=null
        val observations=LinkedHashMap<GitOid,H1ObservedFacts>()
        override fun toString()="H1Request(redacted)"
    }
    private var active: Request?=null
    private val publishedObservers=linkedSetOf<()->Unit>()
    private val nativeObservers=linkedSetOf<()->Unit>()
    @Synchronized internal fun onNativeObservation(observer: ()->Unit): AutoCloseable {
        nativeObservers.add(observer)
        return AutoCloseable { synchronized(this) { nativeObservers.remove(observer) } }
    }
    @Synchronized internal fun nativeObserved(token: String,project: UUID,controller: UUID,host: UUID,
        subject: WAssignmentSubject,binding: WorkReference): JsonValue=capture(token) { state,key ->
        val status=NativeRuntimeAccess(controller,host).status(state,key,project,subject,binding,null) as JObject
        if(status.entries.getValue("bindingJSON")===JNull)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        // A bounded wake hint only; it never advances canonical state or supplies native facts.
        nativeObservers.toList().forEach { it() }
        obj("formatVersion" to num(1),"kind" to str("native-hint"))
    }
    /** Operational wake hints only. Called under execution ownership: observers must only signal
     * their lifecycle worker, never perform native I/O or wait for a callback into this service.
     * Canonical frontier remains the restart source of truth. */
    @Synchronized internal fun onPublished(observer: ()->Unit): AutoCloseable {
        publishedObservers.add(observer)
        return AutoCloseable { synchronized(this) { publishedObservers.remove(observer) } }
    }
    private fun request(c: StoreCommand): Request=(active ?: fail(StoreProblem.INTEGRITY)).also { integrity(it.command===c) }
    private val bindings=H1PublicationBindings(reduction::canonicalize,
        { h,c -> authorize(cache.load(h),request(c)) },
        { h,c ->
            val request=request(c);val plan=request.candidate ?: fail(StoreProblem.INTEGRITY);integrity(plan.before.basis==h.oid)
            if(c.operation=="bind_dispatch_attempt" && plan.evaluation.outcome is LogicalOutcome.Success) {
                val original=DispatchAttemptCommandCodec().decode(c.bytes()).attempt.reservation
                val admission=reduction.authorizeWork(plan.before,original,request.presentation) ?: fail(StoreProblem.DENIED)
                val identity=dev.writhelm.core.store.CommandIdentity(admission.identity.principalId,original.projectId,original.operation.name,original.requestKey)
                if(h.file(identity.path)!=null)fail(StoreProblem.DENIED)
                val attempt=DispatchAttemptCommandCodec().decode(c.bytes()).attempt
                attempt.previous?.let { ref ->
                    val predecessor=(plan.before.work.getValue(original.projectId).resolve(ref).payload as? WDispatchAttemptPayload)?.value ?: fail(StoreProblem.DENIED)
                    if(!definitiveReservationRefusal(h,storeId,admission.identity.principalId,predecessor.reservation))fail(StoreProblem.DENIED)
                }
            }
            plan.evaluation.changes.map { it.key }.toSet()
        },
        { key,bytes,historical -> when(key.namespace) {
            "auth" -> Auth2RecordCodec(if(historical)auth2History() else auth2Admission()).decode(bytes,key.path)
            "work" -> { val codec=WorkCodec(modelVersion=options.workModelVersion);val r=codec.decodeRecord(bytes,historical);integrity(identityCodec.recordPath(r.identity)==key.path && bytes.contentEquals(codec.encodeRecord(r,historical))) }
            else -> fail(StoreProblem.UNSUPPORTED)
        } },
        { _,c,_ -> preflight(c) },
        { h,c,r ->
            val normal=active?.takeIf { it.command.fingerprint==r.fingerprint && it.command.requestKey==r.identity.requestKey &&
                it.command.operation==r.identity.operation && it.presentation.credentialId==r.authenticatedByCredentialId &&
                it.candidate?.before?.basis==h.oid }?.candidate
            boundary.validate(h,c,r,normal)
        })
    private fun authorize(base: H1BoundState,r: Request): CurrentCommandScope {
        val c=r.command;val key=r.presentation
        if(c.operation=="close_dispatch_inputs") {
            if(options.workModelVersion!=4)return CurrentCommandScope.Denied
            val command=DispatchClosureCommandCodec().decode(c.bytes())
            val admission=reduction.authorizeDispatchClosure(base,command,key) ?: return CurrentCommandScope.Denied
            return CurrentCommandScope.NewEffect(AuthenticatedActor(admission.identity.principalId,admission.credentialId))
        }
        if(c.operation=="materialize_dispatch_dependency") {
            if(options.workModelVersion!=4)return CurrentCommandScope.Denied
            val command=DispatchMaterializationCommandCodec().decode(c.bytes())
            val admission=reduction.authorizeDispatchMaterialization(base,command,key) ?: return CurrentCommandScope.Denied
            return CurrentCommandScope.NewEffect(AuthenticatedActor(admission.identity.principalId,admission.credentialId))
        }
        if(c.operation=="bind_dispatch_attempt") {
            if(options.workModelVersion!=4)return CurrentCommandScope.Denied
            val cmd=DispatchAttemptCommandCodec().decode(c.bytes());val reserve=cmd.attempt.reservation
            val controller=(reserve.operation as CReserveAssignmentOperation).value.intendedExecution.controllerId
            val call=WorkInvocation(reserve.projectId,AuthorityOperation.BIND_DISPATCH_ATTEMPT,cmd.requestKey,null,controller,null)
            val admission=Auth2Policy(base.authority,base.projection).authorizeWork(key,call) ?: return CurrentCommandScope.Denied
            val original=reduction.authorizeWork(base,reserve,key) ?: return CurrentCommandScope.Denied
            if(original.mode!=AdmissionMode.EVALUATE || original.identity.principalId!=admission.identity.principalId || original.credentialId!=admission.credentialId)return CurrentCommandScope.Denied
            return CurrentCommandScope.NewEffect(AuthenticatedActor(admission.identity.principalId,admission.credentialId))
        }
        if(c.operation=="record_dispatch_plan") {
            if(options.workModelVersion!=4)return CurrentCommandScope.Denied
            val command=DispatchPlanCommandCodec().decode(c.bytes())
            val call=WorkInvocation(command.plan.projectId,AuthorityOperation.RECORD_DISPATCH_PLAN,command.requestKey,null,command.plan.controllerId,null)
            val admission=Auth2Policy(base.authority,base.projection).authorizeWork(key,call) ?: return CurrentCommandScope.Denied
            return CurrentCommandScope.NewEffect(AuthenticatedActor(admission.identity.principalId,admission.credentialId))
        }
        if(c.fingerprintVersion==3 && c.operation !in H1Catalog.authOperations) {
            val admission=reduction.authorizeWork(base,WorkCodec().decodeCommand(c.bytes()),key) ?: return CurrentCommandScope.Denied
            val actor=AuthenticatedActor(admission.identity.principalId,admission.credentialId)
            return if(admission.mode==AdmissionMode.REPLAY_ONLY)CurrentCommandScope.ReplayOnly(actor) else CurrentCommandScope.NewEffect(actor)
        }
        val credential=if(c.fingerprintVersion==3) {
            val command=Auth3CommandCodec().decode(c.bytes())
            val credential=base.authority.authorizeCommand(key,command)
            val payload=command.payload
            if(credential!=null && payload is Auth3Payload.RegisterDelegation) {
                val subject=base.projection.assignments[AssignmentSubject(command.projectId,payload.binding.assignmentId,payload.binding.executionGeneration)]
                if(subject?.controllerId!=base.authority.controller(credential,command.projectId))return CurrentCommandScope.Denied
            }
            credential
        } else Auth2Policy(base.authority,base.projection).authorizeLegacyCommand(key,c.projectId,c.operation)
        return credential?.let { CurrentCommandScope.NewEffect(AuthenticatedActor(it.credential.principalId,it.credential.id)) } ?: CurrentCommandScope.Denied
    }
    private fun evaluate(base: H1BoundState,r: Request): H1JointCandidate {
        val c=r.command
        return if(c.operation=="close_dispatch_inputs") {
                val command=DispatchClosureCommandCodec().decode(c.bytes())
                val admission=reduction.authorizeDispatchClosure(base,command,r.presentation) ?: fail(StoreProblem.DENIED)
                try {
                    workRequire(command.expectedHead==WorkGitOid(base.basis.algorithm,base.basis.hex),WorkProblem.STALE_GENERATION)
                    workRequire(command.assessment==WSourceVariant2Value(WSourceVariant2("work",command.materialization)),WorkProblem.BLOCKED_INPUT)
                    val work=base.work.getValue(command.manifest.projectId)
                    val materialization=(work.resolve(command.materialization).payload as WDispatchMaterializationPayload).value
                    val task=work.task(materialization.newTask)
                    val probe=WorkCommand(command.requestKey,work.projectId,CCreateTaskRevisionOperation(
                        CCreateTaskRevision(materialization.newTask,task,null,emptyList())))
                    val observed=r.observations.getOrPut(base.basis) { options.runtime.capture(base,probe) }
                    workRequire(observed.basis==base.basis,WorkProblem.STALE_GENERATION)
                    val plan=(work.resolve(materialization.plan).payload as WDispatchPlanPayload).value
                    val actor=WorkExecutionInput(admission.identity.principalId,admission.credentialId,plan.controllerId,null,null)
                    val effect=reduceDeclaredDispatchClosure(work,command.expectedHead,actor,observed.facts,command)
                    val codec=WorkCodec(modelVersion=4)
                    val changes=effect.changedRecords.map { row ->RecordChange(RecordKey("work",row.identity.family.tag,
                        StrictJson.decode(identityCodec.encodeKey(row.identity.key))),codec.encodeRecord(row)) }
                    val row=effect.after.records.getValue(effect.result)
                    val result=obj("key" to StrictJson.decode(identityCodec.encodeKey(row.identity.key)),"generation" to JNull,"digest" to digest(codec.digest(row)))
                    H1JointCandidate(base,base.core,base.authority,base.work+(work.projectId to effect.after),
                        Evaluation(LogicalOutcome.Success(c.operation,result,false),changes.sortedBy { it.key.path }))
                } catch(e:WorkFault) {
                    H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work",e.problem.name),emptyList()))
                }
            } else if(c.operation=="materialize_dispatch_dependency") {
                val command=DispatchMaterializationCommandCodec().decode(c.bytes())
                val admission=reduction.authorizeDispatchMaterialization(base,command,r.presentation) ?: fail(StoreProblem.DENIED)
                try {
                    val state=base.work.getValue(command.task.projectId)
                    val proposal=materializeDispatchDependency(state,command.plan,command.task,command.newTask,command.verificationPlan,command.manifest)
                    val taskCommand=WorkCommand(command.requestKey,state.projectId,CCreateTaskRevisionOperation(proposal.task))
                    val observations=r.observations.getOrPut(base.basis) { options.runtime.capture(base,taskCommand) }
                    val first=reduction.work(base,taskCommand,r.presentation,observations)
                    if(first.evaluation.outcome is LogicalOutcome.Rejected)first else {
                        val middle=H1BoundState.checked(base.storeId,base.basis,first.core,first.authority.records(),first.work,options.app.limits,4)
                        val pin=WorkCommand(command.requestKey,state.projectId,CPinInputManifestOperation(proposal.manifest))
                        val second=reduction.work(middle,pin,r.presentation,observations)
                        if(second.evaluation.outcome is LogicalOutcome.Rejected)
                            H1JointCandidate(base,base.core,base.authority,base.work,second.evaluation)
                        else {
                            val identity=WorkRecordIdentity(WorkFamily.DISPATCH_MATERIALIZATIONS,WorkKey.Id(state.projectId,command.requestKey))
                            workRequire(identity !in state.records,WorkProblem.DUPLICATE_ID)
                            val row=WorkRecord(identity,WDispatchMaterializationPayload(command))
                            val after=second.work.getValue(state.projectId).derive(listOf(row),emptyList())
                            validateDispatchMaterializationEdge(state,after,command)
                            val codec=WorkCodec(modelVersion=4)
                            val change=RecordChange(RecordKey("work",identity.family.tag,StrictJson.decode(identityCodec.encodeKey(identity.key))),codec.encodeRecord(row))
                            val result=obj("key" to StrictJson.decode(identityCodec.encodeKey(identity.key)),"generation" to JNull,"digest" to digest(codec.digest(row)))
                            H1JointCandidate(base,base.core,base.authority,base.work+(state.projectId to after),
                                Evaluation(LogicalOutcome.Success(c.operation,result,false),(first.evaluation.changes+second.evaluation.changes+change).sortedBy { it.key.path }))
                        }
                    }
                } catch(e:WorkFault) {
                    H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work",e.problem.name),emptyList()))
                }
            } else if(c.operation=="bind_dispatch_attempt") {
                val command=DispatchAttemptCommandCodec().decode(c.bytes())
                try {
                    val candidate=reduction.dispatchAttempt(base,command,r.presentation)
                    val record=candidate.effect.after.records.getValue(candidate.effect.result)
                    val result=obj("key" to StrictJson.decode(identityCodec.encodeKey(record.identity.key)),"generation" to JNull,
                        "digest" to digest(WorkCodec(modelVersion=4).digest(record)))
                    H1JointCandidate(base,base.core,base.authority,base.work+(command.attempt.reservation.projectId to candidate.effect.after),
                        Evaluation(LogicalOutcome.Success(c.operation,result,false),listOf(candidate.change)))
                } catch(e:WorkFault) {
                    H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work",e.problem.name),emptyList()))
                }
            } else if(c.operation=="record_dispatch_plan") {
                val command=DispatchPlanCommandCodec().decode(c.bytes())
                try {
                    val candidate=reduction.dispatchPlan(base,command,r.presentation)
                    val record=candidate.effect.after.records.getValue(candidate.effect.result)
                    val result=obj("key" to StrictJson.decode(identityCodec.encodeKey(record.identity.key)),"generation" to JNull,
                        "digest" to digest(WorkCodec(modelVersion=4).digest(record)))
                    H1JointCandidate(base,base.core,base.authority,base.work+(command.plan.projectId to candidate.effect.after),
                        Evaluation(LogicalOutcome.Success(c.operation,result,false),listOf(candidate.change)))
                } catch(e:WorkFault) {
                    H1JointCandidate(base,base.core,base.authority,base.work,Evaluation(LogicalOutcome.Rejected("work",e.problem.name),emptyList()))
                }
            } else if(c.fingerprintVersion!=3)legacy.evaluate(base,c,r.presentation)
            else if(c.operation in H1Catalog.authOperations)reduction.auth3(base,Auth3CommandCodec().decode(c.bytes()),r.presentation)
            else WorkCodec().decodeCommand(c.bytes()).let { command ->
                val facts=r.observations.getOrPut(base.basis) { options.runtime.capture(base,command) }
                reduction.work(base,command,r.presentation,facts)
            }
    }
    private fun ports()=StorePorts(mapOf(
        "auth" to NamespaceValidator(StrictJson.decode(Auth2RecordCodec().header())) { cache.load(it) },
        "work" to NamespaceValidator(obj("formatVersion" to num(1),"modelVersion" to num(options.workModelVersion))) { cache.load(it) },
        "policy" to NamespaceValidator(obj("formatVersion" to num(2))) { h ->
            integrity(h.entries.keys.filter { it.startsWith("policy/") }.toSet()==setOf("policy/format.json","policy/admission.json"))
            integrity(AdmissionPolicy.decode(h.file("policy/admission.json") ?: fail(StoreProblem.INTEGRITY)).formatVersion==2)
        }),
        { h,c,actor,evaluation ->
            val r=request(c);val base=cache.load(h)
            val actual=authorize(base,r)
            val authenticated=when(actual) { is CurrentCommandScope.NewEffect -> actual.actor; else -> fail(StoreProblem.DENIED) }
            integrity(authenticated==actor)
            val expected=evaluate(base,r)
            integrity(StrictJson.encode(expected.evaluation.outcome.json()).contentEquals(StrictJson.encode(evaluation.outcome.json())))
            val a=evaluation.changes.sortedBy { it.key.path };val b=expected.evaluation.changes.sortedBy { it.key.path }
            integrity(a.map { it.key.path }==b.map { it.key.path })
            a.zip(b).forEach { (x,y) -> val p=x.bytes();val q=y.bytes();integrity(if(p==null)q==null else q!=null && p.contentEquals(q)) }
            r.candidate=expected
        },::historicalMetadata,options.app.fileRead,bindings)

    /** Meaningful old hook: original active canonical credential, principal/project and original
     * operation authority. Detailed after-record subject/actor checks are in the required H/C hook. */
    private fun historicalMetadata(h: StoreSnapshot,r: Receipt) {
        integrity(r.validatedAt==h.oid && r.storeId==storeId)
        val state=cache.load(h);val c=state.authority.retainedCredential(r.authenticatedByCredentialId,r.identity.projectId) ?: fail(StoreProblem.INTEGRITY)
        integrity(c.credential.principalId==r.identity.principalId)
        val op=r.identity.operation
        if(op in H1Catalog.workOperations) {
            val operation=AuthorityOperation.entries.single { it.tag==op }
            val b=(c.scope as? CredentialScope.Assignment)?.let { state.authority.delegations.getValue(it.delegationId).binding }
            val subject=b?.let { AssignmentSubject(it.projectId,it.assignmentId,it.executionGeneration) }
            val controller=(c.scope as? CredentialScope.Controller)?.controllerId
            val call=WorkInvocation(r.identity.projectId,operation,r.identity.requestKey,subject,controller,if(operation==AuthorityOperation.UPDATE_PLAN)subject else null)
            integrity(Auth2Policy(state.authority,state.projection).authorizeHistoricalWork(r.authenticatedByCredentialId,call)?.mode==AdmissionMode.EVALUATE)
        } else if(op in H1Catalog.authOperations) {
            integrity(if(op in setOf("register_project","register_controller"))state.authority.owner(c,r.identity.projectId) && c.scope==CredentialScope.Project
                else if(op=="replace_work_grants")state.authority.owner(c,r.identity.projectId)
                else op=="revoke_delegation" && state.authority.owner(c,r.identity.projectId) ||
                    state.authority.controller(c,r.identity.projectId)!=null && state.authority.workAllows(c,r.identity.projectId,WorkPermission.TASK_DISPATCH))
        } else {
            val required=requiredCapability(op) ?: fail(StoreProblem.INTEGRITY)
            integrity(c.scope !is CredentialScope.Assignment && state.authority.allows(c,r.identity.projectId,required))
            if(required in setOf(Capability.MANAGE,Capability.APPROVE))integrity(state.authority.projects[r.identity.projectId]?.ownerPrincipalId==c.credential.principalId)
        }
    }
    /** Command outcomes are a closed UUID/key/counter/digest/code grammar, never record bodies.
     * This conservative encoded witness dominates every result key (three UUIDs + Int64), both
     * OID widths and all operation/code tags. HTTP's primary+emergency small lane is already held. */
    private fun preflight(c: StoreCommand) {
        val id=UUID(-1,-1);val oid=obj("algorithm" to str("sha256"),"hex" to str("f".repeat(64)))
        val witness=obj("formatVersion" to num(1),"kind" to str("settled"),"identity" to obj("principalId" to uuid(id),"projectId" to uuid(id),"operation" to str(c.operation),"requestKey" to uuid(id)),
            "fingerprint" to str("f".repeat(64)),"validatedAt" to oid,"authenticatedByCredentialId" to uuid(id),
            "outcome" to obj("kind" to str("success"),"operation" to str(c.operation),"result" to obj("key" to obj("projectId" to uuid(id),"controllerId" to uuid(id),"id" to uuid(id),"revision" to JNumber(Long.MAX_VALUE)),"generation" to JNumber(Long.MAX_VALUE),"digest" to str("f".repeat(64)),"credentialId" to uuid(id),"replacedCredentialId" to uuid(id)),"domainReplayed" to JBoolean(false)),
            "commandReplayed" to JBoolean(false),"publication" to oid)
        StrictJson.encode(witness,options.commandReplyBytes,true)
    }
    @Synchronized fun execute(token: String,bytes: ByteArray): CommandResult {
        val parsed=CredentialVerifier.parse(token) ?: return CommandResult.Denied
        parsed.use { key ->
            val f=StrictJson.decodeWorkCommand(bytes,JsonLimits(bytes=8*1024*1024,stringBytes=4*1024*1024),1024*1024).fields("formatVersion","requestKey","projectId","operation","payload")
            val command=if(f.getValue("formatVersion").long()==3L)StoreCommand.decodeH1(bytes,bindings) else StoreCommand.decode(bytes)
            check(active==null);val request=Request(key,command);active=request
            val outcome=try {
                store.execute(command,CommandAccess { _,_ -> fail(StoreProblem.INTEGRITY) },CommandEvaluator { h,c,_ ->
                    evaluate(cache.load(h),request(c)).also { request.candidate=it }.evaluation
                })
            } finally { active=null }
            if(outcome is CommandResult.Settled && outcome.receipt.outcome is LogicalOutcome.Success)
                publishedObservers.toList().forEach { runCatching { it() } } // never relabel durable success as rollback
            return outcome
        }
    }
    private fun <T> capture(token: String,block: (H1BoundState,PresentedCredential)->T): T {
        val key=CredentialVerifier.parse(token) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        return key.use { presentation ->
            var captured: H1BoundState?=null
            store.read(storeId,ReadAccess { h,_ ->
                val state=cache.load(h)
                val target=state.authority.credentials[presentation.credentialId] ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
                if(state.authority.authenticate(presentation,target.credential.projectId)==null)throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
                captured=state;true
            }) { h -> val state=checkNotNull(captured);integrity(state.basis==h.oid);block(state,presentation) }
        }
    }
    @Synchronized fun inspectAccess(token: String): AppAccessView=capture(token) { state,key ->
        val target=state.authority.credentials[key.credentialId] ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        val c=state.authority.authenticate(key,target.credential.projectId) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        if(Auth2Policy(state.authority,state.projection).sessionBinding(key,c.credential.projectId)==null)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val caps=state.authority.policy.snapshotGrants().firstOrNull { it.projectId==c.credential.projectId && it.principalId==c.credential.principalId }?.capabilities.orEmpty()
        AppAccessView(state.basis,c.credential.projectId,c.credential.principalId,caps,c.scope,storeId)
    }
    /** Trusted enrollment-broker guard. Native session identity is observed by the configured
     * native peer, not accepted from a prompt. Reserved/retired worker IDs cannot be promoted to
     * broad controllers merely because their native sessions intentionally have no parentID. */
    @Synchronized internal fun requireUnassignedEnrollmentSession(ownerToken:String,anchorProject:UUID,sessionId:String)=capture(ownerToken) { state,key ->
        val owner=state.authority.authenticate(key,anchorProject) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        if(owner.scope!=CredentialScope.Project || !state.authority.owner(owner,anchorProject))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(state.work.values.any { work ->work.records.values.any { row ->
            (row.payload as? WAssignmentsPayload)?.value?.intendedExecution?.nativeSessionId==sessionId ||
                (row.payload as? WAssignmentBindingsPayload)?.value?.execution?.nativeSessionId==sessionId
        } })throw AppAccessFault(AppAccessProblem.FORBIDDEN)
    }
    @Synchronized fun read2(token: String,bytes: ByteArray): JsonValue=capture(token) { state,key ->
        val f=StrictJson.decode(bytes,JsonLimits(bytes=16384)).fields("formatVersion","projectId","operation","subject")
        val operation=f.getValue("operation").string()
        val data=if(operation in Auth2ReadOperation.entries.map { it.tag }) {
            val codec=Auth2ReadCodec();val read=codec.decode(bytes)
            when(val result=state.authority.readStatus(key,read)) {
                Auth2ReadResult.Denied -> throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                Auth2ReadResult.Missing -> throw H1ReadFault("NOT_FOUND")
                else -> StrictJson.decode(Auth2ReadCodec(auth2History()).encodeResult(result),auth2History())
            }
        } else when(val selector=WorkCodec().decodeRead(bytes)) {
            is WorkRead.ConditionStatus -> protected.conditionStatus(state,key,selector.condition)
            is WorkRead.Exact -> StrictJson.decode(WorkCodec().encodeRecord(protected.readWork(state,key,selector),true))
            is WorkRead.Body -> protected.readBody(state,key,selector).let { obj("digest" to digest(it.digest),"byteLength" to num(it.sizeBytes),"body" to str(it.text())) }
            is WorkRead.Listing -> {
                val target=state.authority.credentials[key.credentialId] ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
                val c=state.authority.authenticate(key,target.credential.projectId) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
                if(c.credential.projectId!=selector.projectId || c.scope is CredentialScope.Assignment || !state.authority.allows(c,selector.projectId,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                val family=WorkFamily.entries.singleOrNull { it.tag==selector.value.family } ?: throw H1ReadFault("INVALID_INPUT")
                if(family==WorkFamily.CONTROLLER_CONTINUATIONS && c.scope !is CredentialScope.Controller)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                shape(selector.value.pageSize in 1..32)
                val binding=AuthSessionBinding(storeId,c.credential.principalId,c.credential.projectId,c.scope)
                val normalized=obj("family" to str(family.tag),"taskId" to (selector.value.taskId?.let(::uuid) ?: JNull),"pageSize" to JNumber(selector.value.pageSize))
                val page=navigation.page(binding,state.basis,operation,StrictJson.encode(normalized).decodeToString(),selector.value.pageSize.toInt(),selector.value.cursor) {
                    val work=state.work[selector.projectId] ?: WorkState.empty(selector.projectId)
                    work.listSelectors(family,selector.value.taskId).filter { row ->
                        val resource=if(row.identity.family.mutable)ProtectedResource.MutableWork(WorkMutableSelector(row.identity,requireNotNull(row.generation)))
                            else ProtectedResource.ImmutableWork(WorkReference(row.identity,requireNotNull(row.digest)))
                        Auth2Policy(state.authority,state.projection).authorizeDisclosure(key,DisclosureRequest(selector.projectId,operation,listOf(DisclosureUnit(resource,ContentApplicability.CURRENT))),protected.closure(state,c))
                    }.map { row ->
                        val fields=linkedMapOf<String,JsonValue>("family" to str(row.identity.family.tag),"key" to StrictJson.decode(identityCodec.encodeKey(row.identity.key)))
                        if(row.generation!=null)fields["generation"]=JNumber(row.generation) else fields["digest"]=digest(requireNotNull(row.digest))
                        JObject(fields)
                    }
                }
                return@capture obj("formatVersion" to num(2),"kind" to str("read"),"basis" to page.first.json(),"operation" to str(operation),"data" to page.second)
            }
        }
        obj("formatVersion" to num(2),"kind" to str("read"),"basis" to state.basis.json(),"operation" to str(operation),"data" to data)
    }
    @Synchronized fun read1(token: String,bytes: ByteArray): JsonValue=capture(token) { state,key ->
        var text=0L
        JsonConstruction.bounded(JsonLimits(bytes=4*1024*1024,stringBytes=4*1024*1024,depth=128,collection=8192,tokens=200000),{ _,n ->
            if(n>4L*1024*1024-text)refuse(CodecProblem.RESOURCE_UNAVAILABLE);text+=n
        }) { legacyReads.read(state,key,bytes) }.also { StrictJson.encode(it,4*1024*1024,true) }
    }
    @Synchronized fun readClause(token: String,ref: ClauseReference): ClauseRead=capture(token) { state,key -> protected.readClause(state,key,ref) }
    /** Trusted controller implementation seam, NOT a transport/model raw-store read route. Every
     * use authenticates the same bounded controller and current READ/TASK_DISPATCH at captured H. */
    @Synchronized internal fun coordinationCapture(token: String,project: UUID): CoordinationCapture=capture(token) { state,key ->
        val c=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val controller=(c.scope as? CredentialScope.Controller)?.controllerId ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(!state.authority.workAllows(c,project,WorkPermission.TASK_DISPATCH) || !state.authority.allows(c,project,Capability.READ))
            throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        CoordinationCapture(state.basis,controller,state.work[project] ?: WorkState.empty(project))
    }
    /** Inspect only the original canonical reservation receipt at the captured current H. Missing,
     * success, non-retryable rejection or fingerprint mismatch holds. This does not mint an attempt,
     * authorize new terms, remove claims, or treat an unknown external outcome as absent. */
    @Synchronized internal fun reservationRetryEligibility(token:String,project:UUID,controller:UUID,original:ByteArray):ReservationRetryEligibility {
        val key=CredentialVerifier.parse(token) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        return key.use { presented ->
            val codec=WorkCodec();val command=codec.decodeCommand(original)
            if(command.projectId!=project || command.operation !is CReserveAssignmentOperation ||
                command.operation.value.intendedExecution.controllerId!=controller)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            var identity:dev.writhelm.core.store.CommandIdentity?=null
            store.read(project,ReadAccess { snapshot,_ ->
                val state=cache.load(snapshot)
                val admission=reduction.authorizeWork(state,command,presented) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                if(admission.mode!=AdmissionMode.EVALUATE || admission.scope!=CredentialScope.Controller(controller))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                identity=dev.writhelm.core.store.CommandIdentity(admission.identity.principalId,project,command.operation.name,command.requestKey)
                true
            }) { snapshot ->
                val expected=checkNotNull(identity)
                if(definitiveReservationRefusal(snapshot,storeId,expected.principalId,command))
                    ReservationRetryEligibility.DEFINITIVE_REFUSAL else ReservationRetryEligibility.HOLD
            }
        }
    }
    /** MISSING is an observation at this published head, only under current evaluate admission.
     * It is NOT a new-effect permit or proof that an unknown external effect was absent. */
    @Synchronized internal fun originalResultReceipt(token:String,project:UUID,controller:UUID,host:UUID,original:ByteArray):OriginalResultReceipt {
        val key=CredentialVerifier.parse(token) ?: throw AppAccessFault(AppAccessProblem.UNAUTHENTICATED)
        return key.use { presented ->
            val codec=WorkCodec();val command=codec.decodeCommand(original)
            val data=(command.operation as? CSubmitResultOperation)?.value?.data ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            if(command.projectId!=project)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            var admission:WorkAdmission?=null
            store.read(project,ReadAccess { snapshot,_ ->
                val state=cache.load(snapshot)
                val current=reduction.authorizeWork(state,command,presented) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                val scope=current.scope as? CredentialScope.Assignment ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                val delegation=state.authority.delegations[scope.delegationId] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                val assignment=state.work.getValue(project).assignment(data.assignment.assignmentId)
                if(delegation.binding.controllerId!=controller || assignment.intendedExecution.nativeHostId!=host ||
                    delegation.binding.assignmentId!=data.assignment.assignmentId || delegation.binding.executionGeneration!=data.assignment.generation)
                    throw AppAccessFault(AppAccessProblem.FORBIDDEN)
                admission=current;true
            }) { snapshot ->
                val current=checkNotNull(admission)
                val identity=dev.writhelm.core.store.CommandIdentity(current.identity.principalId,project,"submit_result",command.requestKey)
                val bytes=snapshot.file(identity.path) ?: return@read if(current.mode==AdmissionMode.EVALUATE)
                    OriginalResultReceipt.MISSING else OriginalResultReceipt.HOLD
                val receipt=JournalCodec.decode(bytes,identity.path)
                val state=cache.load(snapshot)
                val originalCredential=state.authority.retainedCredential(receipt.authenticatedByCredentialId,project)
                if(receipt.identity==identity && receipt.storeId==storeId && receipt.fingerprintVersion==3 &&
                    receipt.fingerprint==codec.fingerprint(command).hex && originalCredential?.scope==current.scope &&
                    originalCredential.credential.principalId==current.identity.principalId)OriginalResultReceipt.MATCH else OriginalResultReceipt.HOLD
            }
        }
    }
    @Synchronized internal fun continuationPermit(token: String,project: UUID,subject: WAssignmentSubject,binding: WorkReference): Unit=capture(token) { state,key ->
        val c=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if((c.scope as? CredentialScope.Controller)?.controllerId!=state.work.getValue(project).assignment(subject.assignmentId).intendedExecution.controllerId ||
            !state.authority.workAllows(c,project,WorkPermission.TASK_DISPATCH) || !state.authority.allows(c,project,Capability.READ))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        state.requireCurrentContinuation(project,subject)
        val work=state.work.getValue(project)
        val assignment=work.assignment(subject.assignmentId)
        workRequire(assignment.binding==binding,WorkProblem.INVALID_BINDING)
        workRequire(assignment.state=="active" && assignment.currentIntent==null,WorkProblem.INVALID_TRANSITION)
        Model2Inspector(work).requireActivation(subject,true)
    }
    @Synchronized internal fun nativeCandidateTestPlan(token:String,project:UUID,controller:UUID,host:UUID,planRef:WorkReference):JsonValue = capture(token) { state,key ->
        NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
        val credential=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(credential.scope!=CredentialScope.Controller(controller)||!state.authority.workAllows(credential,project,WorkPermission.TASK_PLAN)||
            !state.authority.workAllows(credential,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val work=state.work.getValue(project)
        val row=work.resolve(planRef);val plan=(row.payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(plan.controllerId==controller && plan.candidateChecks.isNotEmpty(),WorkProblem.BLOCKED_INPUT)
        obj("formatVersion" to num(1),"basis" to state.basis.json(),"planJSON" to str(WorkCodec(modelVersion=work.modelVersion).encodeRecord(row,true).decodeToString()))
    }
    @Synchronized internal fun nativeIssueCandidateReviewTicket(token:String,project:UUID,controller:UUID,host:UUID,raw:String):JsonValue = capture(token) {state,key ->
        NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
        val actor=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(actor.scope!=CredentialScope.Controller(controller) || !state.authority.workAllows(actor,project,WorkPermission.TASK_PLAN) ||
            !state.authority.workAllows(actor,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val ticket=CandidateReviewTicketCodec().decode(raw.toByteArray(Charsets.UTF_8))
        if(ticket.project!=project || ticket.controller!=controller || ticket.execution.nativeHostId!=host)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        validateCandidateReviewTicketState(state,ticket,options.nativeSafety)
        state.requireCurrentAssignmentInputs(project,ticket.assignment)
        val issuer=options.candidateReviewTickets(controller) ?: throw WorkFault(WorkProblem.NOT_READY)
        val bytes=issuer.issue(state,ticket) {assembly ->nativeCandidateAssemblyPermit(token,project,controller,host,assembly)}
        workRequire(bytes.contentEquals(CandidateReviewTicketCodec().encode(ticket)),WorkProblem.INVALID_BINDING)
        obj("formatVersion" to num(1),"kind" to str("candidate-review-ticket"),"ticketJSON" to str(bytes.toString(Charsets.UTF_8)),
            "ticketDigest" to digest(Digest.of(bytes)))
    }
    @Synchronized internal fun nativeCandidateReviewPreparation(token:String,project:UUID,controller:UUID,host:UUID,
        planRef:WorkReference,assemblyId:UUID,checkPlanId:UUID):JsonValue = capture(token) {state,key ->
        NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
        val actor=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(actor.scope!=CredentialScope.Controller(controller) || !state.authority.workAllows(actor,project,WorkPermission.TASK_PLAN) ||
            !state.authority.workAllows(actor,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val work=state.work.getValue(project)
        val plan=(work.resolve(planRef).payload as? WDispatchPlanPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
        workRequire(plan.controllerId==controller,WorkProblem.INVALID_BINDING)
        val issuer=options.candidateReviewTickets(controller) ?: throw WorkFault(WorkProblem.NOT_READY)
        val evidence=issuer.preparationReceipts(project,assemblyId,checkPlanId,plan) ?: throw WorkFault(WorkProblem.NOT_READY)
        nativeCandidateAssemblyPermit(token,project,controller,host,evidence.assemblyJSON)
        val proposal=automaticCandidateReviewPlan(work,planRef,evidence,checkPlanId)
        val codec=WorkCodec(modelVersion=work.modelVersion)
        val basis=obj("formatVersion" to num(1),"projectId" to uuid(project),"reviewId" to uuid(proposal.reviewId),"controllerId" to uuid(controller),
            "plan" to StrictJson.decode(identityCodec.encodeReference(planRef)),"assemblyId" to uuid(assemblyId),"candidate" to encodeWSnapshot(evidence.candidate),
            "selected" to JArray(evidence.selected.map {StrictJson.decode(identityCodec.encodeReference(it))}),"checkPlanId" to uuid(checkPlanId),
            "checks" to JArray(evidence.checks.map {obj("checkId" to str(it.checkId),"runId" to uuid(it.runId),"receiptDigest" to digest(it.receiptDigest))}),
            "contextId" to uuid(proposal.contextId),"excludedPrincipals" to JArray(candidateReviewExcludedPrincipals(state,project,controller,evidence.selected).map(::uuid)))
        obj("formatVersion" to num(1),"kind" to str("candidate-review-preparation"),"taskCommandJSON" to str(codec.encodeCommand(proposal.taskCommand).toString(Charsets.UTF_8)),
            "manifestCommandJSON" to str(codec.encodeCommand(proposal.manifestCommand).toString(Charsets.UTF_8)),"selectionJSON" to str(StrictJson.encode(proposal.selection).toString(Charsets.UTF_8)),
            "preparationId" to uuid(proposal.preparationId),"ticketBasisJSON" to str(StrictJson.encode(basis).toString(Charsets.UTF_8)))
    }
    @Synchronized internal fun nativeCandidateReviewReport(token:String,project:UUID,controller:UUID,host:UUID,reviewId:UUID,report:WorkReference):JsonValue = capture(token) {state,key ->
        NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
        val actor=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(actor.scope!=CredentialScope.Controller(controller) || !state.authority.workAllows(actor,project,WorkPermission.TASK_PLAN) ||
            !state.authority.workAllows(actor,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val issuer=options.candidateReviewTickets(controller) ?: throw WorkFault(WorkProblem.NOT_READY)
        val ticket=issuer.retained(reviewId) ?: throw WorkFault(WorkProblem.NOT_READY)
        workRequire(ticket.project==project && ticket.controller==controller && ticket.execution.nativeHostId==host &&
            issuer.currentReceipts(state,ticket),WorkProblem.NOT_READY)
        val work=state.work.getValue(project)
        workRequire(report.identity.family==WorkFamily.VERIFICATION_REPORTS && report.identity.key.projectId==project,WorkProblem.INVALID_REFERENCE)
        H1ProtectedReads().readWork(state,key,WorkRead.Exact(project,report.identity,report.digest,null))
        val row=work.resolve(report);val value=(row.payload as WVerificationReportsPayload).value
        val assignment=work.assignment(ticket.assignment.assignmentId)
        workRequire(assignment.executionGeneration==ticket.assignment.generation && assignment.binding==ticket.binding &&
            work.binding(ticket.assignment).execution==ticket.execution && value.assignment==ticket.assignment && value.reviewer==ticket.execution &&
            value.task==assignment.task && value.plan==work.task(assignment.task).verificationPlan &&
            value.subjects.size==ticket.selected.size && value.subjects.toSet()==ticket.selected.toSet() &&
            candidateReviewExcludedPrincipals(state,project,controller,ticket.selected).toSet()==ticket.excludedPrincipals.toSet(),WorkProblem.INVALID_BINDING)
        val verification=(work.record(WorkFamily.VERIFICATION_PLANS,value.plan).payload as WVerificationPlansPayload).value
        val requiredGates=verification.gates.filterNot {it.deferred}.map {it.id}
        workRequire(requiredGates.isNotEmpty() && value.gateIds.containsAll(requiredGates),WorkProblem.NOT_READY)
        val cut=(value.freshContextBasis as? WSourceVariant2Value)?.value?.reference ?: throw WorkFault(WorkProblem.NOT_READY)
        workRequire(cut.identity.family==WorkFamily.SOURCE_CUTS && cut.identity.key==WorkKey.Id(project,ticket.contextId),WorkProblem.INVALID_BINDING)
        val source=(work.resolve(cut).payload as WSourceCutsPayload).value
        workRequire(source.assignment==ticket.assignment && source.nativeHostId==ticket.execution.nativeHostId &&
            source.nativeSessionId==ticket.execution.nativeSessionId &&
            value.readOnlySnapshotBasis.any {(it as? WSourceVariant1Value)?.value?.artifact==ticket.candidate},WorkProblem.NOT_READY)
        obj("formatVersion" to num(1),"kind" to str("candidate-review-report"),"ticketDigest" to digest(CandidateReviewTicketCodec().digest(ticket)),
            "reportJSON" to str(WorkCodec(modelVersion=work.modelVersion).encodeRecord(row,true).toString(Charsets.UTF_8)),
            "integrationAllowed" to JBoolean(false))
    }
    @Synchronized internal fun nativeCandidateReviewStatus(token:String,project:UUID,controller:UUID,host:UUID,reviewId:UUID):JsonValue = capture(token) {state,key ->
        NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
        val actor=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(actor.scope!=CredentialScope.Controller(controller) || !state.authority.workAllows(actor,project,WorkPermission.TASK_PLAN) ||
            !state.authority.workAllows(actor,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val issuer=options.candidateReviewTickets(controller) ?: throw WorkFault(WorkProblem.NOT_READY)
        val ticket=issuer.retained(reviewId) ?: throw WorkFault(WorkProblem.NOT_READY)
        workRequire(ticket.project==project && ticket.controller==controller && ticket.execution.nativeHostId==host &&
            issuer.currentReceipts(state,ticket),WorkProblem.NOT_READY)
        val work=state.work.getValue(project)
        val reports=work.records.values.filter { row ->
            val report=(row.payload as? WVerificationReportsPayload)?.value
            report?.assignment==ticket.assignment && report.reviewer==ticket.execution &&
                report.subjects.size==ticket.selected.size && report.subjects.toSet()==ticket.selected.toSet()
        }
        workRequire(reports.size<=1,WorkProblem.NOT_READY) // no latest-report or successful-subset selection
        if(reports.isEmpty())obj("formatVersion" to num(1),"kind" to str("candidate-review-pending"),
            "ticketDigest" to digest(CandidateReviewTicketCodec().digest(ticket)))
        else {
            val reference=work.reference(WorkFamily.VERIFICATION_REPORTS,reports.single().identity.key)
            val verified=nativeCandidateReviewReport(token,project,controller,host,reviewId,reference) as JObject
            JObject(verified.entries+("reportRefJSON" to str(identityCodec.encodeReference(reference).toString(Charsets.UTF_8))))
        }
    }
    @Synchronized internal fun nativeCandidateDelivery(token:String,project:UUID,controller:UUID,host:UUID,planRef:WorkReference):JsonValue {
        nativeCandidateTestPlan(token,project,controller,host,planRef)
        val selection=capture(token) { state,_ ->
            val work=state.work.getValue(project);val plan=(work.resolve(planRef).payload as WDispatchPlanPayload).value
            plan.entries.map { entry ->
                val task=if(entry.futureDependencies.isEmpty())entry.task else dispatchClosedSelection(work,planRef,entry)?.task ?: return@capture null
                val result=chooseDispatchResult(work,DispatchFutureDependency(task,"implemented")) as? DispatchResultChoice.Selected ?: return@capture null
                result.result
            }
        } ?: return obj("formatVersion" to num(1),"kind" to str("pending"))
        val inputs=candidateAssemblyInputs(token,project,controller,selection)
        val excluded=capture(token) { state,_ ->
            val work=state.work.getValue(project)
            (selection.map { (work.resolve(it).payload as WResultsPayload).value.producer.principalId }+
                state.authority.controllers.getValue(controller).principalId).distinct()
        }
        return obj("formatVersion" to num(1),"kind" to str("ready"),
            "selectedJSON" to str(StrictJson.encode(JArray(selection.map { StrictJson.decode(identityCodec.encodeReference(it)) })).decodeToString()),
            "inputsJSON" to str(StrictJson.encode(JArray(inputs.map { obj("base" to encodeWSnapshot(it.base),"result" to encodeWSnapshot(it.result)) })).decodeToString()),
            "excludedReviewersJSON" to str(StrictJson.encode(JArray(excluded.map(::uuid))).decodeToString()))
    }
    @Synchronized internal fun nativeCandidateTestPermit(token:String,project:UUID,controller:UUID,host:UUID,
        assemblyJSON:String,planRef:WorkReference,jobJSON:String):JsonValue {
        nativeCandidateAssemblyPermit(token,project,controller,host,assemblyJSON)
        nativeCandidateTestPlan(token,project,controller,host,planRef)
        return capture(token) { state,_ ->
            val work=state.work.getValue(project);val plan=(work.resolve(planRef).payload as WDispatchPlanPayload).value
            val assembly=StrictJson.decode(assemblyJSON.toByteArray()) as JObject
            val selected=StrictJson.decode(assembly.entries.getValue("selectedJSON").string().toByteArray()).list().map {
                val ref=identityCodec.decodeReference(StrictJson.encode(it))
                (work.resolve(ref).payload as WResultsPayload).value.task
            }
            val required=plan.entries.map { entry -> if(entry.futureDependencies.isEmpty())entry.task
                else dispatchClosedSelection(work,planRef,entry)?.task ?: throw WorkFault(WorkProblem.NOT_READY) }
            workRequire(selected.size==required.size && selected.toSet()==required.toSet(),WorkProblem.BLOCKED_INPUT)
            val job=StrictJson.decode(jobJSON.toByteArray()).fields("runId","candidate","checkId","command","safeRepeat","previousRunId")
            uuid(job.getValue("runId"));val candidate=job.getValue("candidate").fields("commit","tree")
            val commit=workOid(candidate.getValue("commit"));val tree=workOid(candidate.getValue("tree"))
            workRequire(commit.algorithm==tree.algorithm,WorkProblem.INVALID_SUBJECT)
            val proof=options.candidateAssemblyProofByController(uuid(assembly.entries.getValue("operationId")),controller)
                ?: throw WorkFault(WorkProblem.BLOCKED_INPUT)
            workRequire(proof!==JNull,WorkProblem.BLOCKED_INPUT)
            val retained=StrictJson.decode(proof.string().toByteArray(),JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,collection=4096,tokens=100000))
                .fields("original","result")
            workRequire(retained.getValue("original").string()==assemblyJSON &&
                StrictJson.encode(retained.getValue("result")).contentEquals(StrictJson.encode(job.getValue("candidate"))),WorkProblem.BLOCKED_INPUT)
            val check=plan.candidateChecks.singleOrNull { it.checkId==job.getValue("checkId").string() } ?: throw WorkFault(WorkProblem.BLOCKED_INPUT)
            workRequire(job.getValue("command").list().map { it.string() }==check.command.map { it.replace("{candidateCommit}",commit.hex).replace("{candidateTree}",tree.hex) } &&
                (job.getValue("safeRepeat") as? JBoolean)?.value==check.safeRepeat,WorkProblem.BLOCKED_INPUT)
            obj("formatVersion" to num(1),"kind" to str("candidate-test-permit"),"jobDigest" to digest(Digest.of(jobJSON.toByteArray())),
                "planDigest" to digest(planRef.digest),"assemblyDigest" to digest(Digest.of(assemblyJSON.toByteArray())))
        }
    }
    @Synchronized internal fun nativeCandidateAssemblyPermit(token:String,project:UUID,controller:UUID,host:UUID,raw:String):JsonValue {
        // Exact operation bytes are echoed only after selection, rights and repository checks.
        val request=StrictJson.decode(raw.toByteArray(),JsonLimits(bytes=1024*1024,stringBytes=512*1024,collection=4096,tokens=100000))
            .fields("operationId","candidateId","head","inputs","time","selectedJSON")
        uuid(request.getValue("operationId"));uuid(request.getValue("candidateId"))
        workRequire(request.getValue("time").string().matches(Regex("[0-9]{10} \\+0000")),WorkProblem.INVALID_SUBJECT)
        val references=StrictJson.decode(request.getValue("selectedJSON").string().toByteArray()).list()
            .map { identityCodec.decodeReference(StrictJson.encode(it)) }
        val inputs=candidateAssemblyInputs(token,project,controller,references)
        val head=decodeWSnapshot(request.getValue("head"))
        workRequire(head.dirtyContentDigest==null && head.commit!=null && head.repositoryId==inputs.first().result.repositoryId,WorkProblem.INVALID_SUBJECT)
        val supplied=request.getValue("inputs").list().map { item ->val f=item.fields("base","result");decodeWSnapshot(f.getValue("base")) to decodeWSnapshot(f.getValue("result")) }
        workRequire(supplied==inputs.map { it.base to it.result },WorkProblem.INVALID_SUBJECT)
        return capture(token) { state,key ->
            NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
            obj("formatVersion" to num(1),"basis" to state.basis.json(),"requestDigest" to digest(Digest.of(raw.toByteArray())),"kind" to str("candidate-assembly-permit"))
        }
    }
    /** Current captured-head authorization and observation of an exact ordered candidate selection.
     * This read does not execute Git, grant a reusable publication permit or accept the results. */
    @Synchronized internal fun candidateAssemblyInputs(token:String,project:UUID,controller:UUID,
        selected:List<WorkReference>):List<CandidateInput> = capture(token) { state,key ->
        val credential=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(credential.scope!=CredentialScope.Controller(controller) ||
            !state.authority.allows(credential,project,Capability.READ) ||
            !state.authority.workAllows(credential,project,WorkPermission.TASK_PLAN) ||
            !state.authority.workAllows(credential,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        workRequire(selected.isNotEmpty() && selected.size<=256 && selected.distinct().size==selected.size,WorkProblem.INVALID_SUBJECT)
        val work=state.work.getValue(project)
        selected.map { ref ->
            val result=(work.resolve(ref).payload as? WResultsPayload)?.value ?: throw WorkFault(WorkProblem.INVALID_REFERENCE)
            val task=work.task(result.task)
            val probe=WorkCommand((ref.identity.key as WorkKey.Id).id,project,
                CCreateTaskRevisionOperation(CCreateTaskRevision(result.task,task,null,emptyList())))
            val observed=options.runtime.capture(state,probe)
            workRequire(observed.basis==state.basis,WorkProblem.STALE_GENERATION)
            candidateInputs(work,listOf(ref),observed.facts).single()
        }.also { inputs ->
            workRequire(inputs.map { it.task.taskId }.distinct().size==inputs.size &&
                inputs.map { it.result.repositoryId }.distinct().size==1,WorkProblem.INVALID_SUBJECT)
        }
    }
    @Synchronized override fun close()=store.close()
    /** Only installed on the separately authenticated private native transport, never a work tool
     * or generic raw-state read. Capture and actor policy are shared with ordinary service requests. */
    @Synchronized internal fun nativeRuntimeRead(token: String,project: UUID,controller: UUID,host: UUID,
        subject: WAssignmentSubject?,binding: WorkReference?,continuation: UUID?,packet: Boolean=false,sourceIdentity: Boolean=false): JsonValue=capture(token) { state,key ->
        val access=NativeRuntimeAccess(controller,host)
        if(subject==null) {
            require(binding==null && continuation==null)
            if(sourceIdentity)access.sourceIdentity(state,key,project)else access.identity(state,key,project)
        } else if(packet)access.packet(state,key,project,subject,requireNotNull(binding),requireNotNull(continuation),options.candidateReviewTickets(controller))
        else access.status(state,key,project,subject,requireNotNull(binding),continuation,options.nativeSafety)
    }
    @Synchronized internal fun nativeWorkerContext(token: String,project: UUID,controller: UUID,host: UUID): JsonValue=
        capture(token) { state,key -> NativeWorkerContext.read(state,key,project,controller,host,options.nativeSafety,options.candidateReviewTickets(controller)) }
    @Synchronized internal fun nativeRetentionPermit(token:String,project:UUID,controller:UUID,host:UUID,
        subject:WAssignmentSubject,execution:UUID,result:WSnapshot):JsonValue=
        capture(token) { state,key ->
            val permit=NativeRuntimeAccess(controller,host).retentionPermit(state,key,project,subject,execution,result)
            if(!options.retentionEffectQualification.qualified(state,project,subject,execution,result))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            permit
        }
    @Synchronized internal fun nativeControllerContext(token: String,project: UUID,controller: UUID,profile: WorkKey.Profile): JsonValue=
        capture(token) { state,key -> NativeControllerContext.read(state,key,project,controller,profile,options.controllerSafety) }
    /** A same-head proposal only: these commands are not published, and their later submission
     * must not be mistaken for a same-head validation of future-result uniqueness or closure. */
    @Synchronized internal fun nativeDispatchMaterialization(token:String,project:UUID,controller:UUID,host:UUID,
        planId:UUID,task:WorkKey.TaskRevision,newTask:WorkKey.TaskRevision,newPlan:WorkKey.Revision,newManifest:WorkKey.Id,
        taskRequest:UUID,manifestRequest:UUID):JsonValue=capture(token) { state,key ->
        val credential=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(credential.scope!=CredentialScope.Controller(controller) || !state.authority.allows(credential,project,Capability.READ) ||
            !state.authority.workAllows(credential,project,WorkPermission.TASK_PLAN) ||
            !state.authority.workAllows(credential,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
        val work=state.work[project] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(work.modelVersion!=4 || taskRequest==manifestRequest)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val planKey=WorkKey.Id(project,planId)
        val plan=(work.record(WorkFamily.DISPATCH_PLANS,planKey).payload as? WDispatchPlanPayload)?.value
            ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        if(plan.controllerId!=controller)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
        val proposal=materializeDispatchDependency(work,work.reference(WorkFamily.DISPATCH_PLANS,planKey),task,newTask,newPlan,newManifest)
        val codec=WorkCodec()
        obj("formatVersion" to num(1),"basis" to state.basis.json(),"kind" to str("unpublished-proposal"),
            "taskCommandJSON" to str(codec.encodeCommand(WorkCommand(taskRequest,project,CCreateTaskRevisionOperation(proposal.task))).decodeToString()),
            "manifestCommandJSON" to str(codec.encodeCommand(WorkCommand(manifestRequest,project,CPinInputManifestOperation(proposal.manifest))).decodeToString()))
    }
    /** Controller-only current-H projection for one exact immutable dispatch plan. It supplies
     * inputs, not readiness, authority to start, capacity, or permission to retry a new attempt. */
    @Synchronized internal fun nativeDispatchContext(token:String,project:UUID,controller:UUID,host:UUID,planId:UUID):JsonValue=
        capture(token) { state,key ->
            val c=state.authority.authenticate(key,project) ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            if(c.scope!=CredentialScope.Controller(controller) || !state.authority.allows(c,project,Capability.READ) ||
                !state.authority.workAllows(c,project,WorkPermission.TASK_DISPATCH))throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            NativeRuntimeAccess(controller,host).sourceIdentity(state,key,project)
            val work=state.work[project] ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            if(work.modelVersion!=4)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            val row=work.record(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,planId))
            val plan=(row.payload as? WDispatchPlanPayload)?.value ?: throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            if(plan.controllerId!=controller)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            val codec=WorkCodec(modelVersion=4)
            val claims=work.records.values.mapNotNull { row ->
                (row.payload as? WClaimsPayload)?.value?.let { (row.identity.key as WorkKey.Resource) to it }
            }.toMap()
            val planRef=work.reference(WorkFamily.DISPATCH_PLANS,row.identity.key)
            val entries=plan.entries.map { e ->
                val closed=if(e.futureDependencies.isEmpty())null else dispatchClosedSelection(work,planRef,e)
                val closedJSON=closed?.let { selected ->
                    val effective=e.copy(task=selected.task,manifest=selected.manifest,futureDependencies=emptyList())
                    StrictJson.encode(obj("materializationJSON" to str(codec.encodeRecord(work.resolve(selected.materialization),true).decodeToString()),
                        "closureJSON" to str(codec.encodeRecord(work.resolve(selected.closure),true).decodeToString()),
                        "taskJSON" to str(codec.encodeRecord(work.record(WorkFamily.TASK_REVISIONS,selected.task),true).decodeToString()),
                        "manifestJSON" to str(codec.encodeRecord(work.resolve(selected.manifest),true).decodeToString()),
                        "selectionJSON" to str(dispatchPreparationSelection(work,effective).decodeToString()))).decodeToString()
                }
                obj("preparationId" to uuid(e.preparationId),"startId" to uuid(e.startId),
                "taskJSON" to str(codec.encodeRecord(work.record(WorkFamily.TASK_REVISIONS,e.task),true).decodeToString()),
                "selectionJSON" to str(dispatchPreparationSelection(work,e).decodeToString()),
                "closedJSON" to (closedJSON?.let(::str) ?: JNull),
                "claimsAvailable" to JBoolean(e.resources.all { resource -> claims.entries.none { (other,claim) ->
                    claim.state!="free" && (other==resource || resource in claim.incompatible || other in claims[resource]?.incompatible.orEmpty()) } }),
                "futureReady" to JBoolean(e.futureDependencies.isNotEmpty() && e.futureDependencies.all { chooseDispatchResult(work,it) is DispatchResultChoice.Selected })) }
            val active=work.records.values.filter { (it.payload as? WAssignmentsPayload)?.value?.let { a ->
                a.intendedExecution.controllerId==controller && a.state !in setOf("completed","replaced") }==true }
            workRequire(active.size<=256,WorkProblem.INCOMPLETE_HANDOFF)
            val attempts=work.records.values.filter { (it.payload as? WDispatchAttemptPayload)?.value?.plan?.identity==row.identity }
            workRequire(attempts.size<=1024,WorkProblem.INCOMPLETE_HANDOFF)
            obj("formatVersion" to num(1),"basis" to state.basis.json(),"planJSON" to str(codec.encodeRecord(row,true).decodeToString()),
                "entries" to JArray(entries),"activeAssignments" to JArray(active.map { str(codec.encodeRecord(it,true).decodeToString()) }),
                "attemptsJSON" to JArray(attempts.map { str(codec.encodeRecord(it,true).decodeToString()) }))
        }
    @Synchronized internal fun nativePreparationContext(token:String,project:UUID,controller:UUID,host:UUID,subject:WAssignmentSubject):JsonValue=
        capture(token) { state,key -> NativePreparationContext.read(state,key,project,controller,host,subject,options.nativeSafety) }
    @Synchronized internal fun nativeInitialContext(token:String,project:UUID,controller:UUID,host:UUID,subject:WAssignmentSubject,binding:WorkReference,intent:UUID):JsonValue=
        capture(token) { state,key -> NativeInitialContext.read(state,key,project,controller,host,subject,binding,intent,options.nativeSafety,options.candidateReviewTickets(controller)) }
    companion object {
        fun initialize(directory: Path,storeId: UUID,genesis: H1Genesis,options: H1Options): H1Application {
            // This composition currently selects the existing Linux NativeGit/RuntimeOwner adapter.
            // Other service OS targets require separately qualified adapters, not POSIX fallbacks.
            if(System.getProperty("os.name")!="Linux")fail(StoreProblem.UNSUPPORTED)
            val app=H1Application(storeId,options)
            val validated=H1BoundState.checked(storeId,GitOid("sha1","0".repeat(40)),genesis.core,genesis.auth,genesis.work,options.app.limits,options.workModelVersion)
            val files=ArrayList<StoreFile>()
            CoreSnapshotCodec().export(validated.core) { files.add(StoreFile(it.path,it.bytes())) }
            Auth2SnapshotCodec().export(validated.authority).forEach { files.add(StoreFile(it.first,it.second)) }
            files.add(StoreFile("work/format.json",StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(options.workModelVersion)))))
            val bodies=HashSet<Digest>()
            validated.work.values.forEach { work ->
                work.records.values.forEach { files.add(StoreFile(identityCodec.recordPath(it.identity),WorkCodec(modelVersion=options.workModelVersion).encodeRecord(it,true))) }
                work.bodies.forEach { (digest,body) -> if(bodies.add(digest))files.add(StoreFile("work/bodies/${digest.hex.take(2)}/${digest.hex}.txt",body.bytes())) }
            }
            files.add(StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(2)))))
            val o=options.app
            app.store=GitCommandStore.initialize(directory,storeId,files,app.ports(),o.storeBounds,o.processBounds,o.publication,o.admission)
            return app
        }
        fun open(directory: Path,storeId: UUID,options: H1Options): H1Application {
            if(System.getProperty("os.name")!="Linux")fail(StoreProblem.UNSUPPORTED)
            val app=H1Application(storeId,options);val o=options.app
            app.store=GitCommandStore.open(directory,storeId,app.ports(),o.storeBounds,o.processBounds,o.publication)
            return app
        }
    }
}
internal class H1ReadFault(val code: String): RuntimeException(code)
