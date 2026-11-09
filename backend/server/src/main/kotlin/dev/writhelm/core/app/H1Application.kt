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
internal class H1Options(val runtime: H1RuntimeFacts,val app: AppOptions=AppOptions(admission=AdmissionPolicy(formatVersion=2)),
    val commandReplyBytes: Int=65536,val navigationClock: ()->Long={System.nanoTime()/1000000},
    val nativeSafety: NativeRuntimeSafety=UnavailableNativeRuntimeSafety,
    val controllerSafety: NativeControllerRuntimeSafety=UnavailableNativeControllerSafety) {
    init { require(app.admission.formatVersion==2 && commandReplyBytes in 1024..65536) }
}
internal class H1Genesis(val core: ApprovalWorkflow,val auth: Collection<Auth2Record>,val work: Map<UUID,WorkState>)

/** One persistent H1 publisher profile. Every callback sees one bounded request-local presentation;
 * synchronized execution/read and finally cleanup prevent credential sharing across callers. */
internal class H1Application private constructor(private val storeId: UUID,private val options: H1Options): AutoCloseable {
    private lateinit var store: GitCommandStore
    private val cache=H1SnapshotCache(storeId,options.app.limits)
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
        { h,c -> val plan=request(c).candidate ?: fail(StoreProblem.INTEGRITY);integrity(plan.before.basis==h.oid);plan.evaluation.changes.map { it.key }.toSet() },
        { key,bytes,historical -> when(key.namespace) {
            "auth" -> Auth2RecordCodec(if(historical)auth2History() else auth2Admission()).decode(bytes,key.path)
            "work" -> { val codec=WorkCodec();val r=codec.decodeRecord(bytes,historical);integrity(identityCodec.recordPath(r.identity)==key.path && bytes.contentEquals(codec.encodeRecord(r,historical))) }
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
        return if(c.fingerprintVersion!=3)legacy.evaluate(base,c,r.presentation)
            else if(c.operation in H1Catalog.authOperations)reduction.auth3(base,Auth3CommandCodec().decode(c.bytes()),r.presentation)
            else WorkCodec().decodeCommand(c.bytes()).let { command ->
                val facts=r.observations.getOrPut(base.basis) { options.runtime.capture(base,command) }
                reduction.work(base,command,r.presentation,facts)
            }
    }
    private fun ports()=StorePorts(mapOf(
        "auth" to NamespaceValidator(StrictJson.decode(Auth2RecordCodec().header())) { cache.load(it) },
        "work" to NamespaceValidator(obj("formatVersion" to num(1),"modelVersion" to num(2))) { cache.load(it) },
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
            integrity(if(op=="replace_work_grants")state.authority.owner(c,r.identity.projectId)
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
    @Synchronized override fun close()=store.close()
    /** Only installed on the separately authenticated private native transport, never a work tool
     * or generic raw-state read. Capture and actor policy are shared with ordinary service requests. */
    @Synchronized internal fun nativeRuntimeRead(token: String,project: UUID,controller: UUID,host: UUID,
        subject: WAssignmentSubject?,binding: WorkReference?,continuation: UUID?,packet: Boolean=false,sourceIdentity: Boolean=false): JsonValue=capture(token) { state,key ->
        val access=NativeRuntimeAccess(controller,host)
        if(subject==null) {
            require(binding==null && continuation==null)
            if(sourceIdentity)access.sourceIdentity(state,key,project)else access.identity(state,key,project)
        } else if(packet)access.packet(state,key,project,subject,requireNotNull(binding),requireNotNull(continuation))
        else access.status(state,key,project,subject,requireNotNull(binding),continuation,options.nativeSafety)
    }
    @Synchronized internal fun nativeWorkerContext(token: String,project: UUID,controller: UUID,host: UUID): JsonValue=
        capture(token) { state,key -> NativeWorkerContext.read(state,key,project,controller,host,options.nativeSafety) }
    @Synchronized internal fun nativeControllerContext(token: String,project: UUID,controller: UUID,profile: WorkKey.Profile): JsonValue=
        capture(token) { state,key -> NativeControllerContext.read(state,key,project,controller,profile,options.controllerSafety) }
    @Synchronized internal fun nativePreparationContext(token:String,project:UUID,controller:UUID,host:UUID,subject:WAssignmentSubject):JsonValue=
        capture(token) { state,key -> NativePreparationContext.read(state,key,project,controller,host,subject,options.nativeSafety) }
    @Synchronized internal fun nativeInitialContext(token:String,project:UUID,controller:UUID,host:UUID,subject:WAssignmentSubject,binding:WorkReference,intent:UUID):JsonValue=
        capture(token) { state,key -> NativeInitialContext.read(state,key,project,controller,host,subject,binding,intent,options.nativeSafety) }
    companion object {
        fun initialize(directory: Path,storeId: UUID,genesis: H1Genesis,options: H1Options): H1Application {
            // This composition currently selects the existing Linux NativeGit/RuntimeOwner adapter.
            // Other service OS targets require separately qualified adapters, not POSIX fallbacks.
            if(System.getProperty("os.name")!="Linux")fail(StoreProblem.UNSUPPORTED)
            val app=H1Application(storeId,options)
            val validated=H1BoundState.checked(storeId,GitOid("sha1","0".repeat(40)),genesis.core,genesis.auth,genesis.work,options.app.limits)
            val files=ArrayList<StoreFile>()
            CoreSnapshotCodec().export(validated.core) { files.add(StoreFile(it.path,it.bytes())) }
            Auth2SnapshotCodec().export(validated.authority).forEach { files.add(StoreFile(it.first,it.second)) }
            files.add(StoreFile("work/format.json",StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))))
            val bodies=HashSet<Digest>()
            validated.work.values.forEach { work ->
                work.records.values.forEach { files.add(StoreFile(identityCodec.recordPath(it.identity),WorkCodec().encodeRecord(it,true))) }
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
