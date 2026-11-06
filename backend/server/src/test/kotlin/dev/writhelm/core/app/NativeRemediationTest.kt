// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Actual cold Git H/C edges; native facts are explicitly attributed fixtures, not host proof. */
class NativeRemediationTest {
    private class Boundary(val f: ReviewAppFixture,val git: NativeGit) {
        val files=linkedMapOf<String,ByteArray>()
        var h: StoreSnapshot
        var sequence=18000
        init {
            CoreSnapshotCodec().export(f.base.core) { files[it.path]=it.bytes() }
            Auth2SnapshotCodec().export(f.base.authority).forEach { (path,bytes) -> files[path]=bytes }
            files["work/format.json"]=StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))
            f.base.work.values.forEach { work ->
                work.records.values.forEach { files[identityCodec.recordPath(it.identity)]=f.codec.encodeRecord(it,true) }
                work.bodies.forEach { (digest,body) -> files["work/bodies/${digest.hex.take(2)}/${digest.hex}.txt"]=body.bytes() }
            }
            h=stored(files,null)
        }
        private fun stored(files: Map<String,ByteArray>,parent: GitOid?): StoreSnapshot {
            val entries=files.mapValues { git.blob(it.value) }
            class Node { val dirs=sortedMapOf<String,Node>();val files=sortedMapOf<String,GitOid>() }
            val root=Node()
            entries.forEach { (path,oid) -> var node=root;val parts=path.split('/');parts.dropLast(1).forEach { node=node.dirs.getOrPut(it) { Node() } };node.files[parts.last()]=oid }
            fun tree(node: Node): GitOid {
                val lines=node.files.map { (name,oid) -> name to "100644 blob ${oid.hex}\t$name\u0000" }+node.dirs.map { (name,child) -> "$name/" to "040000 tree ${tree(child).hex}\t$name\u0000" }
                return git.oid(git.call(listOf("mktree","-z"),lines.sortedBy { it.first }.joinToString("") { it.second }.toByteArray()).checked())
            }
            return StoreSnapshot(git.commit(tree(root),parent),entries,git)
        }
        fun base()=H1SnapshotCache(storeId).load(h)
        fun candidate(command: WorkCommand,facts: WorkExternalFacts=f.observations.facts): H1JointCandidate {
            val base=base()
            return f.key(plannerKey) { f.bridge.work(base,command,it,H1ObservedFacts(h.oid,facts,f.observations.qualifiedProfiles)) }
        }
        fun edge(command: WorkCommand,candidate: H1JointCandidate,transform: (List<RecordChange>)->List<RecordChange> = { it },reject: Boolean=false) {
            val changes=transform(candidate.evaluation.changes)
            val writes=changes.sortedBy { it.key.path }.map { WriteDescriptor(it.key,files[it.key.path]?.let(::sha),it.bytes()?.let(::sha)) }
            val original=assertIs<LogicalOutcome.Success>(candidate.evaluation.outcome)
            val result=H1Catalog.result(original.operation,original.result)
            val changedResult=changes.single { it.key.namespace=="work" && it.key.family==result.family && StrictJson.encode(it.key.key).contentEquals(StrictJson.encode(result.key)) }
            val fields=original.result.fields("key","generation","digest")
            val outcome=LogicalOutcome.Success(original.operation,obj("key" to fields.getValue("key"),"generation" to fields.getValue("generation"),"digest" to str(sha(changedResult.bytes()!!))),false)
            val receipt=Receipt(storeId,CommandIdentity(planner,project,command.operation.name,command.requestKey),3,f.codec.fingerprint(command).hex,h.oid,plannerKey,outcome,writes,3)
            val after=LinkedHashMap(files);changes.forEach { after[it.key.path]=it.bytes()!! };after[receipt.path]=JournalCodec.encode(receipt)
            val c=stored(after,h.oid)
            if(reject)assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> { H1BoundaryValidator(H1SnapshotCache(storeId)).validate(h,c,receipt) }.problem)
            else {
                H1BoundaryValidator(H1SnapshotCache(storeId)).validate(h,c,receipt)
                files.clear();files.putAll(after);h=c
            }
        }
        fun assignment(changes: List<RecordChange>,change: (WAssignments)->WAssignments)=changes.map {
            if(it.key.family!="assignments")it else {
                val record=f.codec.decodeRecord(it.bytes()!!)
                RecordChange(it.key,f.codec.encodeRecord(record.copy(payload=WAssignmentsPayload(change((record.payload as WAssignmentsPayload).value)))))
            }
        }
        fun intent(changes: List<RecordChange>,change: (WNativeIntents)->WNativeIntents)=changes.map {
            if(it.key.family!="native-intents")it else {
                val record=f.codec.decodeRecord(it.bytes()!!)
                RecordChange(it.key,f.codec.encodeRecord(record.copy(payload=WNativeIntentsPayload(change((record.payload as WNativeIntentsPayload).value)))))
            }
        }
        fun request(operation: String): Pair<WorkCommand,H1JointCandidate> {
            val a=base().work.getValue(project).assignment(f.assignment)
            val cmd=WorkCommand(id(sequence++),project,CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,a.generation,id(sequence++),id(sequence++),operation,if(operation=="resume")a.checkpoint else null,emptyList())))
            return cmd to candidate(cmd)
        }
        fun observe(outcome: String,prior: String): Pair<WorkCommand,H1JointCandidate> {
            val state=base().work.getValue(project);val a=state.assignment(f.assignment)
            val key=a.currentIntent!!;val i=(state.record(WorkFamily.NATIVE_INTENTS,key).payload as WNativeIntentsPayload).value
            val cmd=WorkCommand(id(sequence++),project,CObserveNativeTransitionOperation(CObserveNativeTransition(f.assignment,a.generation,key.id,i.generation,outcome,listOf(f.source),emptyList())))
            val proof=WorkNativeObservation(i.assignment,key.id,i.correlationId,i.operation,outcome,listOf(f.source),a.execution,true,prior)
            return cmd to candidate(cmd,f.observations.facts.copy(native=mapOf(key.id to proof)))
        }
    }
    private fun withBound(block: (Boundary)->Unit) {
        val f=ReviewAppFixture();f.reserve();f.install(f.key(plannerKey) { f.bridge.auth3(f.base,f.register(),it) })
        RuntimeOwner.acquire(directory()).use { owner ->
            val git=NativeGit(owner);git.initialize();val b=Boundary(f,git)
            val execution=WExecution(f.intended.executionId,f.worker,f.workerKey,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace)
            val prepare=WorkNativeObservation(WAssignmentSubject(f.assignment,0),f.intent,f.correlation,"prepare","confirmed",listOf(f.source),execution,true,"reserved")
            val bind=WorkCommand(id(17900),project,CBindAssignmentOperation(CBindAssignment(f.assignment,1,f.intent,0,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace,listOf(f.source),emptyList())))
            val candidate=b.candidate(bind,f.observations.facts.copy(native=mapOf(f.intent to prepare)))
            b.edge(bind,candidate,{ b.assignment(it) { a -> a.copy(state="active") } },true)
            b.edge(bind,candidate)
            block(b)
        }
    }
    @Test fun `cold start pending unknown and confirmed companions are exact`()=withBound { b ->
        val (request,candidate)=b.request("start")
        b.edge(request,candidate,{ b.assignment(it) { a -> a.copy(state="active") } },true)
        b.edge(request,candidate,{ b.assignment(it) { a -> a.copy(currentIntent=null) } },true)
        b.edge(request,candidate,{ b.intent(it) { i -> i.copy(outcome="confirmed") } },true)
        b.edge(request,candidate,{ b.intent(it) { i -> i.copy(expectedSession="ses_wrong") } },true)
        b.edge(request,candidate)
        val (unknown,u)=b.observe("unknown","bound")
        b.edge(unknown,u,{ b.assignment(it) { a -> a.copy(state="active") } },true)
        b.edge(unknown,u)
        val (confirm,c)=b.observe("confirmed","bound")
        b.edge(confirm,c,{ b.intent(it) { i -> i.copy(correlationId=id(18999)) } },true)
        b.edge(confirm,c,{ b.assignment(it) { a -> a.copy(state="bound") } },true)
        b.edge(confirm,c)
        assertEquals("active",b.base().work.getValue(project).assignment(b.f.assignment).state)
    }
    @Test fun `cold not performed start and stop preserve safe state without releasing claims`()=withBound { b ->
        val (request,candidate)=b.request("start");b.edge(request,candidate)
        val (notPerformed,n)=b.observe("not-performed","bound")
        b.edge(notPerformed,n,{ b.assignment(it) { a -> a.copy(state="active") } },true)
        b.edge(notPerformed,n)
        val (stop,s)=b.request("stop")
        b.edge(stop,s,{ b.assignment(it) { a -> a.copy(state="stopped") } },true)
        b.edge(stop,s)
        val (confirmed,c)=b.observe("confirmed","bound")
        b.edge(confirmed,c)
        val claim=(b.base().work.getValue(project).record(WorkFamily.CLAIMS,b.f.resource).payload as WClaimsPayload).value
        assertEquals("held",claim.state)
    }
    @Test fun `cold pause request and confirmed observation cannot fabricate alternate states`()=withBound { b ->
        val (start,s)=b.request("start");b.edge(start,s)
        val (started,a)=b.observe("confirmed","bound");b.edge(started,a)
        val (pause,p)=b.request("pause")
        b.edge(pause,p,{ b.assignment(it) { a -> a.copy(state="paused") } },true)
        b.edge(pause,p)
        val (paused,c)=b.observe("confirmed","active")
        b.edge(paused,c,{ b.assignment(it) { a -> a.copy(state="active") } },true)
        b.edge(paused,c)
    }
    @Test fun `cold resume stays paused until confirmed and preserves the exact checkpoint`() {
        val f=ReviewAppFixture();f.activateWithAttributedFixtureObservations()
        val subject=WAssignmentSubject(f.assignment,0);val hash=Digest.of(byteArrayOf(3));val cut=id(19000);val locator=id(19001)
        var sequence=19002
        val facts=f.observations.facts.copy(sourceCuts=mapOf(locator to WorkRetainedCut(f.intended.nativeHostId,f.intended.nativeSessionId,hash,hash,emptyList())))
        fun worker(op: dev.writhelm.core.work.WorkOperation) {
            f.install(f.key(f.workerKey) { f.bridge.work(f.base,WorkCommand(id(sequence++),project,op),it,H1ObservedFacts(f.head,facts,f.observations.qualifiedProfiles)) })
        }
        worker(CRecordSourceCutOperation(CRecordSourceCut(WorkKey.Id(project,cut),WSourceCuts(f.task,subject,f.intended.nativeHostId,f.intended.nativeSessionId,f.profile,1,hash,hash,locator,emptyList(),emptyList(),emptyList()),emptyList())))
        val plan=WorkKey.Revision(project,id(19010),0);val view=WorkKey.Revision(project,id(19011),0);val memory=WorkKey.Revision(project,id(19012),0)
        worker(CUpdatePlanOperation(CUpdatePlan(plan,f.task,null,emptyList(),"own continuation",null,emptyList(),subject)))
        worker(CRecordLoadedViewOperation(CRecordLoadedView(view,CRecordLoadedViewData(f.task,f.manifest,subject,null,emptyList(),emptyList()),emptyList())))
        worker(CRecordMemoryOperation(CRecordMemory(memory,CRecordMemoryData(f.task,subject,null,cut,emptyList(),emptyList(),emptyList()),emptyList())))
        val text=Utf8Content.fromText("unfinished exact checkpoint")
        worker(CRecordCheckpointOperation(CRecordCheckpoint(WorkKey.Id(project,id(19013)),CRecordCheckpointData(f.task,subject,f.manifest,view,cut,plan,0,memory,emptyList(),emptyList(),emptyList(),WorkBodyReference(text.digest,text.sizeBytes),"unfinished",null,null,null),4,listOf(CRecordCheckpointBodiesItem(text.digest,text.text())))))
        RuntimeOwner.acquire(directory()).use { owner ->
            val git=NativeGit(owner);git.initialize();val b=Boundary(f,git)
            val checkpoint=b.base().work.getValue(project).assignment(f.assignment).checkpoint
            val (pause,p)=b.request("pause");b.edge(pause,p)
            val (paused,c)=b.observe("confirmed","active");b.edge(paused,c)
            val (resume,r)=b.request("resume")
            b.edge(resume,r,{ b.assignment(it) { a -> a.copy(state="active") } },true)
            b.edge(resume,r)
            val (notPerformed,n)=b.observe("not-performed","paused");b.edge(notPerformed,n)
            val (retry,t)=b.request("resume");b.edge(retry,t)
            val (unknown,u)=b.observe("unknown","paused");b.edge(unknown,u)
            val (confirmed,v)=b.observe("confirmed","paused");b.edge(confirmed,v)
            assertEquals(checkpoint,b.base().work.getValue(project).assignment(f.assignment).checkpoint)
        }
    }
}
