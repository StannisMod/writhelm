package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class HistoricalAppProbeTest {
    @Test fun `cold historical validator must reject active assignment with only pending start intent`() {
        val f=ReviewAppFixture();f.reserve();f.install(f.key(plannerKey) { f.bridge.auth3(f.base,f.register(),it) })
        val execution=WExecution(f.intended.executionId,f.worker,f.workerKey,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace)
        val prepare=WorkNativeObservation(WAssignmentSubject(f.assignment,0),f.intent,f.correlation,"prepare","confirmed",listOf(f.source),execution,true,"reserved")
        val bind=WorkCommand(id(15000),project,CBindAssignmentOperation(CBindAssignment(f.assignment,1,f.intent,0,f.intended.nativeHostId,f.intended.nativeSessionId,f.workspace,listOf(f.source),emptyList())))
        f.install(f.key(plannerKey) { f.bridge.work(f.base,bind,it,H1ObservedFacts(f.head,f.observations.facts.copy(native=mapOf(f.intent to prepare)),f.observations.qualifiedProfiles)) })
        RuntimeOwner.acquire(directory()).use { owner ->
            val git=NativeGit(owner);git.initialize()
            fun stored(files: Map<String,ByteArray>,parent: GitOid?): StoreSnapshot {
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
            val files=linkedMapOf<String,ByteArray>()
            CoreSnapshotCodec().export(f.base.core) { files[it.path]=it.bytes() }
            Auth2SnapshotCodec().export(f.base.authority).forEach { (path,bytes) -> files[path]=bytes }
            files["work/format.json"]=StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))
            f.base.work.values.forEach { work ->
                work.records.values.forEach { files[identityCodec.recordPath(it.identity)]=f.codec.encodeRecord(it,true) }
                work.bodies.forEach { (digest,body) -> files["work/bodies/${digest.hex.take(2)}/${digest.hex}.txt"]=body.bytes() }
            }
            val parent=stored(files,null);val cache=H1SnapshotCache(storeId);val before=cache.load(parent)
            val command=WorkCommand(id(15001),project,CRequestNativeTransitionOperation(CRequestNativeTransition(f.assignment,2,id(15002),id(15003),"start",null,emptyList())))
            val candidate=f.key(plannerKey) { f.bridge.work(before,command,it,H1ObservedFacts(parent.oid,f.observations.facts,f.observations.qualifiedProfiles)) }
            assertEquals("bound",candidate.work.getValue(project).assignment(f.assignment).state)
            val changes=candidate.evaluation.changes.map { change ->
                if(change.key.family!="assignments")change else {
                    val record=f.codec.decodeRecord(change.bytes()!!)
                    val data=(record.payload as WAssignmentsPayload).value
                    RecordChange(change.key,f.codec.encodeRecord(record.copy(payload=WAssignmentsPayload(data.copy(state="active")))))
                }
            }
            val writes=changes.sortedBy { it.key.path }.map { WriteDescriptor(it.key,files[it.key.path]?.let(::sha),it.bytes()?.let(::sha)) }
            val receipt=Receipt(storeId,CommandIdentity(planner,project,command.operation.name,command.requestKey),3,f.codec.fingerprint(command).hex,parent.oid,plannerKey,candidate.evaluation.outcome,writes,3)
            val afterFiles=LinkedHashMap(files);changes.forEach { afterFiles[it.key.path]=it.bytes()!! };afterFiles[receipt.path]=JournalCodec.encode(receipt)
            val after=stored(afterFiles,parent.oid)
            assertFailsWith<StoreFault>("cold history must reject fabricated ACTIVE with no confirmed start observation") {
                H1BoundaryValidator(H1SnapshotCache(storeId)).validate(parent,after,receipt)
            }
        }
    }
}
