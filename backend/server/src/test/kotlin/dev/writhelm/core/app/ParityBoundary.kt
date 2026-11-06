// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.store.*
import dev.writhelm.core.work.*
import org.junit.jupiter.api.Test
import kotlin.test.*

internal class ParityBoundary(val f: ReviewAppFixture,val git: NativeGit) {
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
