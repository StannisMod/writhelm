// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import java.util.UUID

/** Trusted bootstrap values: public verifiers only, never raw secrets or a client dictionary. */
internal class AppGenesis(val workflow: ApprovalWorkflow,val authority: AuthorityView)
internal data class AppLimits(val coreRecords: Int=8192,val authRecords: Int=8192,val entryBytes: Long=64L*1024*1024,
    val affectedEdges: Int=16384,val depth: Int=256,val cachedStates: Int=2) {
    init { require(coreRecords in 1..8192 && authRecords in 1..8192 && entryBytes in 1..64L*1024*1024 && affectedEdges in 1..16384 && depth in 1..256 && cachedStates in 1..2) }
}
internal class AppOptions(val limits: AppLimits=AppLimits(),val admission: AdmissionPolicy=AdmissionPolicy(),
    val storeBounds: StoreBounds=StoreBounds(),val processBounds: ProcessBounds=ProcessBounds(),
    val publication: PublicationDriver=PublicationDriver.Native,val fileRead: (String)->Unit={ })
internal class BoundState(val core: ApprovalWorkflow,val authority: AuthorityView,val entryBytes: Long=0)
/** Typed protected service projection; no raw store/path or caller-supplied actor. */
internal class AppAccessView(val basis: GitOid,val projectId: UUID,val principalId: UUID,capabilities: Set<Capability>,
    val scope: dev.writhelm.core.auth2.CredentialScope=dev.writhelm.core.auth2.CredentialScope.Project,val storeId: UUID?=null) {
    val capabilities=frozenSet(capabilities)
}
internal class AppReadView(val access: AppAccessView,val workflow: ApprovalWorkflow)
internal enum class AppAccessProblem { UNAUTHENTICATED, FORBIDDEN }
internal class AppAccessFault(val problem: AppAccessProblem): RuntimeException(problem.name)
internal fun grantMap(policy: AccessPolicy)=policy.snapshotGrants().associate { (it.principalId to it.projectId) to it.capabilities }
internal fun corePolicy(s: WorkflowSnapshot)=AccessPolicy(s.grants)

/** Immutable derived state, keyed only by exact store/format-bound snapshot OID. No canonical facts
 * or credentials live here. Cache misses always perform complete validated loading. */
internal class AppBinding(val storeId: UUID,val options: AppOptions) {
    private val cache=LinkedHashMap<GitOid,BoundState>(4,0.75f,true)
    private val authCodec=AuthRecordCodec()
    private fun remember(oid: GitOid,state: BoundState) {
        cache[oid]=state
        while(cache.size>options.limits.cachedStates)cache.remove(cache.keys.first())
    }
    fun confirmed(oid: GitOid,state: BoundState) { remember(oid,state) }
    fun load(snapshot: StoreSnapshot): BoundState {
        cache[snapshot.oid]?.let { return it }
        var consumed=0L
        fun read(path: String): ByteArray {
            val bytes=snapshot.file(path) ?: fail(StoreProblem.INTEGRITY)
            if(bytes.size.toLong()>options.limits.entryBytes-consumed)fail(StoreProblem.UNAVAILABLE)
            consumed+=bytes.size; return bytes
        }
        val h=StrictJson.decode(read("store.json"),JsonLimits(bytes=16384)).fields("formatVersion","storeId","gitObjectFormat","namespaces")
        version(h.getValue("formatVersion")); integrity(uuid(h.getValue("storeId"))==storeId)
        integrity(h.getValue("gitObjectFormat").string()=="sha1")
        val paths=snapshot.entries.keys.filter { it.startsWith("core/") }
        if(paths.size>options.limits.coreRecords+8193)fail(StoreProblem.UNAVAILABLE)
        val core=CoreSnapshotCodec(budget=RestoreBudget(records=options.limits.coreRecords,depth=options.limits.depth)).restore(
            (listOf("core/format.json")+paths.filter { it!="core/format.json" }.sorted()).asSequence().map { CoreEntry(it,read(it)) })
        authCodec.checkHeader(read("auth/format.json"))
        val authPaths=snapshot.entries.keys.filter { it.startsWith("auth/") && it!="auth/format.json" }.sorted()
        if(authPaths.size>options.limits.authRecords)fail(StoreProblem.UNAVAILABLE)
        val records=authPaths.map { authCodec.decode(read(it),it) }
        val principals=records.filterIsInstance<AuthRecord.Principal>().map { it.principalId }
        val projects=records.filterIsInstance<AuthRecord.Project>().map { it.binding }
        val credentials=records.filterIsInstance<AuthRecord.CredentialRecord>().map { it.credential }
        val guards=records.filterIsInstance<AuthRecord.Guard>()
        integrity(guards.map { it.enrollment }.distinct().size==guards.size)
        val policy=corePolicy(core.snapshot(options.limits.coreRecords))
        val authority=AuthorityView.checked(storeId,principals,projects,credentials,guards.associate { it.enrollment to it.generation },policy)
        val valid=checked(core,authority)
        val state=BoundState(valid.core,valid.authority,consumed)
        remember(snapshot.oid,state); return state
    }
    fun checked(core: ApprovalWorkflow,authority: AuthorityView): BoundState {
        integrity(authority.storeId==storeId)
        if(authority.principals.size.toLong()+authority.projects.size+authority.credentials.size+authority.guards.size>options.limits.authRecords)fail(StoreProblem.UNAVAILABLE)
        val s=core.snapshot(options.limits.coreRecords)
        integrity(grantMap(corePolicy(s))==grantMap(authority.policy))
        val actual=AuthorityView.checked(storeId,authority.principals,authority.projects.values,authority.credentials.values,authority.guards,corePolicy(s))
        val served=s.revisions.map { it.key.document.projectId }+s.grants.map { it.projectId }+
            s.requests.values.map { it.projectId }+s.assessments.values.map { it.assessment.projectId }+s.manifests.values.map { it.projectId }
        integrity(served.all { it in actual.projects })
        fun enrolled(principal: UUID,project: UUID) { integrity(principal in actual.principals && Enrollment(principal,project) in actual.guards) }
        s.proposals.forEach { (k,a)-> enrolled(a.principalId,k.document.projectId) }
        s.requests.values.forEach { enrolled(it.proposedBy,it.projectId) }
        s.resolutions.forEach { (id,r)-> enrolled(r.actor.principalId,s.requests.getValue(id).projectId) }
        s.assessments.values.forEach { enrolled(it.actor.principalId,it.assessment.projectId) }
        s.manifests.values.forEach { enrolled(it.createdBy,it.projectId) }
        return BoundState(core,actual)
    }
    fun genesis(g: AppGenesis): List<StoreFile> {
        val snap=g.workflow.snapshot(options.limits.coreRecords)
        val validated=WorkflowSnapshotValidator.restore(snap,65536,options.limits.depth)
        val state=checked(validated,g.authority)
        val files=ArrayList<StoreFile>(); var size=0L
        fun add(file: StoreFile) { val n=file.bytes().size; if(n>options.limits.entryBytes-size)fail(StoreProblem.UNAVAILABLE); size+=n; files.add(file) }
        CoreSnapshotCodec().export(state.core) { add(StoreFile(it.path,it.bytes())) }
        add(StoreFile("auth/format.json",authCodec.header()))
        val records=authRecords(state.authority)
        if(records.size>options.limits.authRecords)fail(StoreProblem.UNAVAILABLE)
        records.forEach { add(StoreFile(authCodec.path(it),authCodec.encode(it))) }
        add(StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
        return files
    }
    fun namespaceValidators()=mapOf(
        "auth" to NamespaceValidator(StrictJson.decode(authCodec.header())) { load(it) },
        "policy" to NamespaceValidator(obj("formatVersion" to num(1))) { snapshot ->
            integrity(snapshot.entries.keys.filter { it.startsWith("policy/") }.toSet()==setOf("policy/format.json","policy/admission.json"))
            version(StrictJson.decode(snapshot.file("policy/format.json")!!).fields("formatVersion").getValue("formatVersion"))
            AdmissionPolicy.decode(snapshot.file("policy/admission.json") ?: fail(StoreProblem.INTEGRITY))
        })
    /** Recorded public attribution, NOT reauthentication of a historical raw secret. */
    fun historical(h: StoreSnapshot,r: Receipt) {
        integrity(r.storeId==storeId && r.validatedAt==h.oid)
        val authority=load(h).authority
        val credential=authority.credentials[r.authenticatedByCredentialId] ?: fail(StoreProblem.INTEGRITY)
        integrity(credential.status==CredentialStatus.ACTIVE && credential.storeId==storeId && credential.projectId==r.identity.projectId && credential.principalId==r.identity.principalId)
        val capability=requiredCapability(r.identity.operation) ?: fail(StoreProblem.INTEGRITY)
        integrity(authority.policy.allows(Actor(r.identity.principalId),r.identity.projectId,capability))
    }
}
internal fun authRecords(a: AuthorityView): List<AuthRecord> =
    a.principals.map { AuthRecord.Principal(it) }+a.projects.values.map { AuthRecord.Project(it) }+
        a.credentials.values.map { AuthRecord.CredentialRecord(it) }+a.guards.map { (e,g)-> AuthRecord.Guard(e,g) }
