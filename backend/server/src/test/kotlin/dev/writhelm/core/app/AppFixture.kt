// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.workflow.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertIs

internal fun id(n: Int)=UUID(0,n.toLong())
internal val storeId=id(1)
internal val project=id(2)
internal val owner=id(3)
internal val ownerKey=id(4)
internal val planner=id(5)
internal val plannerKey=id(6)
internal val reader=id(7)
internal val readerKey=id(8)
// PUBLIC deterministic synthetic vectors only. Never provision these as live credentials.
internal fun secret(n: Int)=ByteArray(32) { (it+n).toByte() }
internal fun token(key: UUID)=CredentialVerifier.token(storeId,key,secret(key.leastSignificantBits.toInt()))
internal val ownerToken=token(ownerKey)
internal val plannerToken=token(plannerKey)
internal val readerToken=token(readerKey)
internal fun credential(key: UUID,principal: UUID)=Credential(key,storeId,project,principal,CredentialVerifier.digest(storeId,project,principal,key,secret(key.leastSignificantBits.toInt())),CredentialStatus.ACTIVE,0)
internal fun directory(): Path {
    val configured=System.getenv("WRITHELM_APP_TEST_ROOT")
    if(configured==null)return Files.createTempDirectory("app-test-")
    val root=Path.of(configured).toAbsolutePath().normalize(); Files.createDirectories(root)
    return Files.createTempDirectory(root,"app-test-")
}
internal fun genesis(extraOwner: Boolean=false,guard: Long=0): AppGenesis {
    val policy=AccessPolicy(listOf(Grant(owner,project,Capability.entries.toSet()),Grant(planner,project,setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE)),Grant(reader,project,setOf(Capability.READ))))
    val authority=AuthorityView.checked(storeId,listOf(owner,planner,reader),listOf(ProjectBinding(project,owner)),
        listOf(credential(ownerKey,owner),credential(plannerKey,planner),credential(readerKey,reader))+if(extraOwner)listOf(credential(id(9),owner)) else emptyList(),
        mapOf(Enrollment(owner,project) to 0L,Enrollment(planner,project) to guard,Enrollment(reader,project) to 0L),policy)
    return AppGenesis(ApprovalWorkflow.start(policy),authority)
}
internal fun revision(n: Int,x: String="X0",y: String="Y0"): DocumentRevision {
    val text="Scope\n$x\n$y\n"; val a="Scope\n".toByteArray().size; val b="$x\n".toByteArray().size
    return DocumentRevision(RevisionKey(DocumentKey(project,id(20)),id(n)),Utf8Content.fromText(text),StructureIndex(listOf(ClauseSpan(ClauseId("X"),ByteSpan(a,a+b)),ClauseSpan(ClauseId("Y"),ByteSpan(a+b,text.toByteArray().size))),listOf(ByteSpan(0,a))))
}
internal fun DocumentRevision.x()=clauseReference(ClauseId("X"))
internal fun DocumentRevision.y()=clauseReference(ClauseId("Y"))
internal fun refs(vararg rs: DocumentRevision): List<KnowledgeReference> = rs.flatMap { listOf(it.x(),it.y(),it.contextReference()) }
internal fun packet(items: List<ReviewItem>,contexts: List<ContextReference>)=ReviewPacket(items,"Accept exact targets?","Named synthetic items only","Synthetic binding test",listOf("Changes named inputs"),listOf("Keep prior inputs"),emptyList(),contexts)
internal fun settled(r: CommandResult)=assertIs<CommandResult.Settled>(r)
internal fun <T> value(r: WorkflowResult<T>): T=assertIs<WorkflowResult.Success<T>>(r).value
internal class AppFixture(val path: Path=directory(),val initial: AppGenesis=genesis(),options: AppOptions=AppOptions()): AutoCloseable {
    val app=MechanismApp.initialize(path,initial,options)
    private var sequence=1000
    fun next()=id(sequence++)
    fun core(p: CommandPayload,t: String=plannerToken,k: UUID=next(),j: UUID=project)=app.execute(t,CommandCodec().encode(CoreCommand(k,j,p)))
    fun auth(p: AuthPayload,t: String=ownerToken,k: UUID=next())=app.execute(t,AuthCommandCodec().encode(AuthCommand(k,project,p)))
    fun propose(r: DocumentRevision)=settled(core(CommandPayload.Propose(r)))
    fun request(p: ReviewPacket): ApprovalRequest {
        val id=next(); settled(core(CommandPayload.Request(id,p))); return value(app.readRequest(plannerToken,project,id))
    }
    fun answer(r: ApprovalRequest,k: UUID=next())=core(CommandPayload.Resolve(r.id,r.digest,r.packet.digest,StructuredAnswer.Approve),ownerToken,k)
    fun assess(references: List<KnowledgeReference>,links: List<ContinuityLink> = emptyList(),blocked: List<KnowledgeReference> = emptyList()): UUID {
        val id=next(); settled(core(CommandPayload.Assess(ConsistencyAssessment(id,project,references,"Exact synthetic assessment",links,blocked)))); return id
    }
    fun pin(clauses: List<ClauseReference>,contexts: List<ContextReference>,assessment: UUID): UUID {
        val id=next(); settled(core(CommandPayload.Pin(id,next(),clauses,contexts,emptyList(),assessment))); return id
    }
    override fun close()=app.close()
}
