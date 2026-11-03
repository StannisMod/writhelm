// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.workflow.*
import dev.writhelm.core.knowledge.*
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class SnapshotCodecTest {
    @Test fun `twelve families frozen history later direct approval blocker revocation reject and changes`() {
        val f = WorkflowFixture(secondOwner = true).baseline(); val (b, assessment) = f.prepareB()
        val manifest = f.pin(listOf(b.y()), listOf(b.contextReference()), assessment)
        val pending = f.pendingX(b); f.answer(pending)
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), b.y(), f.a.y())), listOf(f.a.contextReference(), b.contextReference()))))
        val reject = f.pending(packet(listOf(ReviewItem(f.id(), f.a.y(), b.y())), listOf(f.a.contextReference(), b.contextReference())))
        f.answer(reject, StructuredAnswer.Reject)
        val changes = f.pending(packet(listOf(ReviewItem(f.id(), f.a.y(), b.y())), listOf(f.a.contextReference(), b.contextReference())))
        val answer = StructuredAnswer.ChangesRequired(mapOf(changes.packet.items.single().id to "Keep exact history")); f.answer(changes, answer)
        f.assessment(refs(f.a), blocked = listOf(f.a.y()))
        f.state = good(f.state.revokeAccess(owner, project, reader.principalId)).state
        val restored = restored(f.state)
        assertTrue(sameEntries(f.state, restored))
        val families = entries(restored).filter { "/records/" in it.path }.map { it.path.split('/')[2] }.toSet()
        assertEquals(CoreRecordCodec.families, families)
        assertEquals(manifest.provenance.getValue(b.y()).assessmentIds, good(restored.readManifest(owner, project, manifest.id)).provenance.getValue(b.y()).assessmentIds)
        bad(WorkflowProblem.FORBIDDEN, restored.readManifest(reader, project, manifest.id))
        bad(WorkflowProblem.BLOCKED_INPUT, restored.approvalTrace(owner, f.a.y()))
        good(restored.approvalTrace(owner, b.y())) // Later direct approval takes precedence; old manifest trace stays frozen.
        assertTrue(good(restored.resolve(owner, project, reject.id, reject.digest, reject.packet.digest, StructuredAnswer.Reject)).replayed)
        assertTrue(good(restored.resolve(owner, project, changes.id, changes.digest, changes.packet.digest, answer)).replayed)
    }
    @Test fun `ABA stale pending and all six next command effects preserve equivalence`() {
        val f = WorkflowFixture(secondOwner = true).baseline(); val (b, a) = f.prepareB()
        val stale = f.pendingX(b); f.answer(f.pendingX(b))
        f.answer(f.pending(packet(listOf(ReviewItem(f.id(), f.a.x(), b.x())), listOf(f.a.contextReference(), b.contextReference()))))
        val r = restored(f.state)
        bad(WorkflowProblem.STALE_SUBJECT, r.resolve(owner, project, stale.id, stale.digest, stale.packet.digest, StructuredAnswer.Approve))
        fun <T> next(run: (ApprovalWorkflow) -> WorkflowResult<Transition<T>>) {
            val x = good(run(f.state)); val y = good(run(r)); assertEquals(x.replayed, y.replayed); assertTrue(sameEntries(x.state, y.state))
        }
        next { it.propose(planner, doc(999)) }
        next { it.request(planner, project, UUID(0, 999), packet(listOf(ReviewItem(UUID(0, 998), b.x(), f.a.x())), listOf(f.a.contextReference(), b.contextReference()))) }
        next { it.resolve(owner, project, stale.id, stale.digest, stale.packet.digest, StructuredAnswer.Reject) }
        next { it.assess(planner, ConsistencyAssessment(UUID(0, 999), project, refs(f.a), "Synthetic")) }
        next { it.pin(planner, project, UUID(0, 999), UUID(0, 998), listOf(b.y()), listOf(b.contextReference()), emptyList(), a.id) }
        next { it.revokeAccess(owner, project, reader.principalId) }
    }
    @Test fun `two consumers still read their exact old and new revisions`() {
        val f = WorkflowFixture().baseline(); val old = f.pin(listOf(f.a.x()), listOf(f.a.contextReference()), f.baselineAssessment)
        val (b, a) = f.prepareB(); f.answer(f.pendingX(b)); val fresh = f.pin(listOf(b.x()), listOf(b.contextReference()), a)
        val r = restored(f.state)
        assertEquals(old.clauses, good(r.readManifest(owner, project, old.id)).clauses)
        assertEquals(fresh.clauses, good(r.readManifest(owner, project, fresh.id)).clauses)
        assertNotEquals(good(r.readClause(owner, old.clauses.single())).located.content, good(r.readClause(owner, fresh.clauses.single())).located.content)
    }
    @Test fun `missing counterpart digest guards heads provenance and unknown entries refuse atomically`() {
        val f = WorkflowFixture().baseline(); val all = entries(f.state)
        for (family in listOf("proposals", "approvals", "heads", "generations")) assertFailsWith<CodecFault>(family) {
            CoreSnapshotCodec().restore(all.filterNot { it.path.contains("/records/$family/") }.asSequence())
        }
        val badGeneration = mutateRecord(all, "generations") { JObject(it + ("payload" to obj("generation" to JNumber(Long.MAX_VALUE)))) }
        assertEquals(CodecProblem.INVALID_GRAPH, assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(badGeneration.asSequence()) }.problem)
        val badDigest = mutateRecord(all, "revisions") { fields -> val p = (fields.getValue("payload") as JObject).entries
            JObject(fields + ("payload" to JObject(p + ("revisionDigest" to JString("0".repeat(64)))))) }
        assertEquals(CodecProblem.DIGEST_MISMATCH, assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(badDigest.asSequence()) }.problem)
        assertFailsWith<CodecFault> { CoreSnapshotCodec().restore((all + all[1]).asSequence()) }
        assertFailsWith<CodecFault> { CoreSnapshotCodec().restore((all + CoreEntry("core/unknown", byteArrayOf())).asSequence()) }
        assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(sequence { yield(all.first()); error("source failed") }) }
        assertTrue(sameEntries(f.state, restored(f.state)))
    }
    @Test fun `strict required record fields and key path mismatch`() {
        val all = entries(WorkflowFixture().baseline().state)
        for (entry in all.filter { "/records/" in it.path }) {
            val root = StrictJson.decode(entry.bytes()) as JObject
            root.entries.keys.forEach { key -> assertFailsWith<CodecFault> {
                CoreSnapshotCodec().restore(all.map { if (it === entry) CoreEntry(it.path, StrictJson.encode(JObject(root.entries - key))) else it }.asSequence())
            } }
        }
        assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(all.map { if ("/records/" in it.path) CoreEntry(it.path + "x", it.bytes()) else it }.asSequence()) }
    }
    @Test fun `exact body variants shared blobs empty grants and immutable export buffers`() {
        for (text in listOf("", "X", "X\n", "X\r\n", "\ufeffé🙂", "e\u0301")) {
            val content = Utf8Content.fromText(text)
            val index = StructureIndex(emptyList(), if (content.sizeBytes == 0) emptyList() else listOf(ByteSpan(0, content.sizeBytes)))
            var state = ApprovalWorkflow.start(AccessPolicy(listOf(Grant(owner.principalId, project, Capability.entries.toSet()), Grant(reader.principalId, project, emptySet()))))
            for (id in 1L..2L) state = good(state.propose(owner, DocumentRevision(RevisionKey(document, UUID(0, id)), content, index))).state
            val e = entries(state); assertEquals(1, e.count { "/bodies/" in it.path }); assertTrue(sameEntries(state, CoreSnapshotCodec().restore(e.asSequence())))
            val bytes = e.first().bytes(); bytes.fill(0); assertEquals('{'.code.toByte(), e.first().bytes().first())
        }
    }
    @Test fun `restore budgets exact bounds admission separation and standalone completeness limits`() {
        val f = WorkflowFixture().baseline(); val all = entries(f.state); val n = all.sumOf { it.bytes().size.toLong() }
        for (max in n-1..n+1) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(bytes = max))
            if (max < n) assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> { codec.restore(all.asSequence()) }.problem)
            else codec.restore(all.asSequence())
        }
        CoreSnapshotCodec(admission = JsonLimits(bytes = 1)).restore(all.asSequence())
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> { CoreSnapshotCodec(budget = RestoreBudget(nodes = 1)).restore(all.asSequence()) }.problem)
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> { CoreSnapshotCodec(budget = RestoreBudget(decodedText = 1)).restore(all.asSequence()) }.problem)
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> { CoreSnapshotCodec(budget = RestoreBudget(edges = 1)).restore(all.asSequence()) }.problem)
        // An independent empty subgraph is internally valid, not authenticated source completeness.
        val empty = ApprovalWorkflow.start(AccessPolicy(emptyList())); assertTrue(sameEntries(empty, restored(empty)))
    }

    @Test fun `exact header nodes text entry counts and traversal budgets`() {
        val empty = entries(ApprovalWorkflow.start(AccessPolicy(emptyList())))
        for(n in 5L..7L) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(nodes = n))
            if(n < 6) {
                assertFailsWith<CodecFault> { codec.restore(empty.asSequence()) }
                assertFailsWith<CodecFault> { entries(ApprovalWorkflow.start(AccessPolicy(emptyList())),codec) }
            } else { codec.restore(empty.asSequence()); entries(ApprovalWorkflow.start(AccessPolicy(emptyList())),codec) }
        }
        for(n in 24L..26L) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(decodedText = n))
            if(n < 25) {
                assertFailsWith<CodecFault> { codec.restore(empty.asSequence()) }
                assertFailsWith<CodecFault> { entries(ApprovalWorkflow.start(AccessPolicy(emptyList())),codec) }
            } else { codec.restore(empty.asSequence()); entries(ApprovalWorkflow.start(AccessPolicy(emptyList())),codec) }
        }
        val f = WorkflowFixture().baseline(); val all = entries(f.state)
        assertEquals(19, all.count { "/records/" in it.path })
        for(n in 18..20) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(records = n))
            if(n < 19) {
                assertFailsWith<CodecFault> { codec.restore(all.asSequence()) }
                assertFailsWith<CodecFault> { entries(f.state, codec) }
            } else { codec.restore(all.asSequence()); entries(f.state, codec) }
        }
        for(n in 20..22) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(edges = n))
            if(n < 21) assertFailsWith<CodecFault> { codec.restore(all.asSequence()) } else codec.restore(all.asSequence())
        }
        f.add(doc(31, "different")); val two = entries(f.state)
        for(n in 1..3) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(bodies = n))
            if(n < 2) assertFailsWith<CodecFault> { codec.restore(two.asSequence()) } else codec.restore(two.asSequence())
        }
    }

    @Test fun `forged frozen provenance missing context blocker union and guard extras refuse`() {
        val f = WorkflowFixture().baseline(); val (b,a) = f.prepareB()
        val m = f.pin(listOf(b.y()), listOf(b.contextReference()), a); val s = f.state.snapshot()
        fun invalid(snapshot: WorkflowSnapshot) {
            assertEquals(CodecProblem.INVALID_GRAPH, assertFailsWith<CodecFault> { WorkflowSnapshotValidator.restore(snapshot, 65536, 256) }.problem)
        }
        val forged = ApprovalTrace(f.a.y(), f.initial.id, owner.principalId, emptyList())
        val badManifest = InputManifest(m.id, m.consumerId, m.projectId, m.createdBy, m.clauses, m.contexts, m.dependencies, m.assessmentId, m.provenance + (b.y() to forged))
        invalid(snapshotWith(s, manifests = s.manifests + (m.id to badManifest)))
        val noContext = InputManifest(m.id, m.consumerId, m.projectId, m.createdBy, m.clauses, emptyList(), m.dependencies, m.assessmentId, m.provenance)
        invalid(snapshotWith(s, manifests = s.manifests + (m.id to noContext)))
        val noProvenance = InputManifest(m.id, m.consumerId, m.projectId, m.createdBy, m.clauses, m.contexts, m.dependencies, m.assessmentId, emptyMap())
        invalid(snapshotWith(s, manifests = s.manifests + (m.id to noProvenance)))
        invalid(snapshotWith(s, blocked = setOf(f.a.y())))
        invalid(snapshotWith(s, approvals = s.approvals + (f.a.y() to ApprovalTrace(f.a.y(), f.initial.id, outsider.principalId))))
        val request = f.initial
        val extra = ApprovalRequest(request.id, project, request.proposedBy, request.packet, request.generations + (SubjectKey(document, ClauseId("unknown")) to 0L))
        invalid(snapshotWith(s, requests = s.requests + (request.id to extra)))
        invalid(snapshotWith(s, heads = s.heads + (f.a.x().subject() to b.x())))
        invalid(snapshotWith(s, continuity = emptyMap()))
    }

    @Test fun `bounded provenance depth and retained cycle even after direct authority`() {
        val f = WorkflowFixture(a = doc(30, context = "")).baseline()
        val b = doc(31, context = ""); f.prepareB(b)
        val c = doc(32, context = ""); f.add(c)
        f.assessment(refs(b,c), listOf(ContinuityLink(b.y(), c.y())))
        val all = entries(f.state)
        for(n in 1..3) {
            val codec = CoreSnapshotCodec(budget = RestoreBudget(depth = n))
            if(n < 2) assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> { codec.restore(all.asSequence()) }.problem)
            else codec.restore(all.asSequence())
        }
        val s = f.state.snapshot(); val id = UUID(0,999)
        val cycle = ConsistencyAssessment(id, project, refs(f.a,c), "Synthetic cycle", listOf(ContinuityLink(c.y(),f.a.y())))
        val bad = snapshotWith(s, assessments = s.assessments + (id to RecordedAssessment(cycle,planner)), continuity = s.continuity + (f.a.y() to StoredContinuity(c.y(),id)))
        assertEquals(CodecProblem.INVALID_GRAPH, assertFailsWith<CodecFault> { WorkflowSnapshotValidator.restore(bad,65536,256) }.problem)
    }

    @Test fun `body coverage substitution malformed bodies and explicit successful stream end`() {
        val f = WorkflowFixture().baseline(); val all = entries(f.state)
        assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(all.filterNot { "/bodies/" in it.path }.asSequence()) }
        val body = all.first { "/bodies/" in it.path }
        assertEquals(CodecProblem.DIGEST_MISMATCH, assertFailsWith<CodecFault> {
            CoreSnapshotCodec().restore(all.map { if(it === body) CoreEntry(it.key,"wrong".toByteArray()) else it }.asSequence())
        }.problem)
        assertEquals(CodecProblem.MALFORMED, assertFailsWith<CodecFault> {
            CoreSnapshotCodec().restore(all.map { if(it === body) CoreEntry(it.key,byteArrayOf(0xff.toByte())) else it }.asSequence())
        }.problem)
        val extra = Utf8Content.fromText("extra")
        val withExtra = listOf(all.first()) + (all.drop(1)+CoreEntry(CoreEntryKey.Body(extra.digest),extra.bytes())).sortedBy { it.path }
        assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(withExtra.asSequence()) }
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> {
            CoreSnapshotCodec().restore(sequence { yieldAll(all); error("source did not complete") })
        }.problem)
        assertEquals(CodecProblem.RESOURCE_UNAVAILABLE, assertFailsWith<CodecFault> {
            CoreSnapshotCodec().export(f.state) { error("sink failed") }
        }.problem)
        assertTrue(sameEntries(f.state, restored(f.state)))
    }

    @Test fun `metadata admission exact final bytes does not tighten historical restore`() {
        val f = WorkflowFixture().baseline(); val all = entries(f.state)
        val grant = all.first { "/grants/" in it.path }; val size = grant.bytes().size
        for(n in size-1..size+1) {
            val codec = CoreSnapshotCodec(admission = JsonLimits(bytes = n))
            if(n < size) assertEquals(CodecProblem.ADMISSION_LIMIT, assertFailsWith<CodecFault> { codec.admitRecord(grant) }.problem)
            else codec.admitRecord(grant)
            codec.restore(all.asSequence())
        }
        val malformed = mutateRecord(all,"grants") { f -> JObject(f + ("payload" to obj("capabilities" to JArray(listOf(str("READ"),str("READ")))))) }.first { "/grants/" in it.path }
        assertEquals(CodecProblem.DUPLICATE_IDENTITY, assertFailsWith<CodecFault> { CoreSnapshotCodec().admitRecord(malformed) }.problem)
    }

    @Test fun `same assessment cannot bootstrap source authority within its batch`() {
        val f = WorkflowFixture(a = doc(30,context="")).baseline()
        val b=doc(31,context=""); f.prepareB(b)
        val c=doc(32,context=""); val d=doc(33,context=""); f.add(c); f.add(d)
        val s=f.state.snapshot(); val id=UUID(0,999)
        val a=ConsistencyAssessment(id,project,refs(b,c,d),"Invalid batch bootstrap",listOf(ContinuityLink(b.y(),c.y()),ContinuityLink(c.y(),d.y())))
        val snapshot=snapshotWith(s,assessments=s.assessments+(id to RecordedAssessment(a,planner)),continuity=s.continuity+
            mapOf(c.y() to StoredContinuity(b.y(),id),d.y() to StoredContinuity(c.y(),id)))
        assertEquals(CodecProblem.INVALID_GRAPH,assertFailsWith<CodecFault> { WorkflowSnapshotValidator.restore(snapshot,65536,256) }.problem)
    }

    @Test fun `independent raw body hashes frozen before codec exists`() {
        val goldens=mapOf("" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "X\n" to "7058299627365fc7a3dd7840fd3d56f29306cd30c0f2c13cb500fe79617290ff",
            "X\r\n" to "d26a7528134491bbe88c1c728df61f64d493faf74932d00ea636567fb5a91fb9",
            "é🙂" to "7382f537af6a53054b6a72792df00fa0be0f5d2e8214db9cd6ebbcc3d57d02b9")
        goldens.forEach { (text,hash) ->
            val b=Utf8Content.fromText(text); val structure=StructureIndex(emptyList(),if(b.sizeBytes==0)emptyList() else listOf(ByteSpan(0,b.sizeBytes)))
            val state=good(ApprovalWorkflow.start(AccessPolicy(listOf(Grant(owner.principalId,project,Capability.entries.toSet())))).propose(owner,DocumentRevision(RevisionKey(document,UUID(0,30)),b,structure))).state
            val entry=entries(state).single { it.key is CoreEntryKey.Body }
            assertEquals("core/bodies/${hash.take(2)}/$hash.txt",entry.path)
            assertContentEquals(text.toByteArray(),entry.bytes())
        }
    }

    @Test fun `withdrawn historical approver and later continuity source blocker do not corrupt history`() {
        val f=WorkflowFixture(secondOwner=true).baseline(); val (b,a)=f.prepareB()
        val manifest=f.pin(listOf(b.y()),listOf(b.contextReference()),a)
        f.assessment(refs(f.a),blocked=listOf(f.a.y()))
        f.state=good(f.state.revokeAccess(coOwner,project,owner.principalId)).state
        val r=restored(f.state)
        assertTrue(sameEntries(f.state,r))
        assertEquals(manifest.provenance.getValue(b.y()).assessmentIds,good(r.readManifest(planner,project,manifest.id)).provenance.getValue(b.y()).assessmentIds)
        bad(WorkflowProblem.BLOCKED_INPUT,r.approvalTrace(planner,b.y()))
        bad(WorkflowProblem.FORBIDDEN,r.resolve(owner,project,f.initial.id,f.initial.digest,f.initial.packet.digest,StructuredAnswer.Approve))
        bad(WorkflowProblem.FORBIDDEN,r.readResolution(owner,project,f.initial.id))
        assertEquals(owner,good(r.readResolution(coOwner,project,f.initial.id)).actor)
    }

    @Test fun `unordered actual maps sets canonicalize without reordering model lists`() {
        val f=WorkflowFixture().baseline(); val (b,a)=f.prepareB(); f.pin(listOf(b.y(),f.a.x()),listOf(b.contextReference(),f.a.contextReference()),a)
        val all=entries(f.state)
        val reordered=all.map { entry ->
            if("/records/" !in entry.path)entry else {
                val fields=(StrictJson.decode(entry.bytes()) as JObject).entries
                val payload=(fields.getValue("payload") as JObject).entries.toMutableMap()
                for(name in listOf("capabilities","generations","provenance")) (payload[name] as? JArray)?.let { payload[name]=JArray(it.values.reversed()) }
                CoreEntry(entry.key,StrictJson.encode(JObject(fields+("payload" to JObject(payload)))))
            }
        }
        assertTrue(sameEntries(f.state,CoreSnapshotCodec().restore(reordered.asSequence())))
    }

    @Test fun `all nested record object fields are required and closed`() {
        val f=WorkflowFixture().baseline(); val (b,a)=f.prepareB(); f.pin(listOf(b.y()),listOf(b.contextReference()),a)
        val pending=f.pendingX(b)
        f.answer(pending,StructuredAnswer.ChangesRequired(mapOf(pending.packet.items.single().id to "Exact synthetic comment")))
        val all=entries(f.state); var mutations=0
        for(entry in all.filter { "/records/" in it.path }) {
            val json=StrictJson.decode(entry.bytes())
            for(mutant in shapeMutants(json)) {
                mutations++
                assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(all.map { if(it===entry)CoreEntry(it.key,StrictJson.encode(mutant)) else it }.asSequence()) }
            }
        }
        assertTrue(mutations>200)
    }

    @Test fun `unsupported core versions duplicate header and unsafe entry spelling refuse without fallback`() {
        for(field in listOf("formatVersion","modelVersion")) {
            val header=CoreEntry(CoreEntryKey.Header,StrictJson.encode(JObject((CoreRecordCodec.header() as JObject).entries+(field to num(2)))))
            assertEquals(CodecProblem.UNSUPPORTED_FORMAT,assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(sequenceOf(header)) }.problem)
        }
        val header=CoreEntry(CoreEntryKey.Header,StrictJson.encode(CoreRecordCodec.header()))
        assertEquals(CodecProblem.DUPLICATE_IDENTITY,assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(sequenceOf(header,header)) }.problem)
        for(path in listOf("../core/format.json","core//format.json","core/bodies/aa/${"0".repeat(64)}.txt","core/records/unknown/00/${"0".repeat(64)}.json"))
            assertFailsWith<CodecFault> { CoreEntry(path,byteArrayOf()) }
    }

    @Test fun `read JSON resource profiles have exact independent small boundaries`() {
        val state=ApprovalWorkflow.start(AccessPolicy(listOf(Grant(owner.principalId,project,Capability.entries.toSet()))))
        val all=entries(state)
        fun boundary(n: Int,limit: (Int)->JsonLimits) {
            for(max in n-1..n+1) {
                val codec=CoreSnapshotCodec(budget=RestoreBudget(json=limit(max)))
                if(max<n) {
                    assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { codec.restore(all.asSequence()) }.problem)
                    assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { entries(state,codec) }.problem)
                } else { codec.restore(all.asSequence()); entries(state,codec) }
            }
        }
        boundary(36) { RestoreBudget().json.copy(stringBytes=it) }
        boundary(13) { RestoreBudget().json.copy(nameBytes=it) }
        boundary(5) { RestoreBudget().json.copy(collection=it) }
        boundary(3) { RestoreBudget().json.copy(depth=it) }
        boundary(24) { RestoreBudget().json.copy(tokens=it) }
        val size=all.maxOf { it.bytes().size }
        boundary(size) { RestoreBudget().json.copy(bytes=it) }
    }

    @Test fun `new revision body and combined index admission have independent boundaries`() {
        val body=Utf8Content.fromText("abcdefg")
        val structure=StructureIndex((0..4).map { ClauseSpan(ClauseId("C$it"),ByteSpan(it,it+1)) },listOf(ByteSpan(5,6),ByteSpan(6,7)))
        val revision=DocumentRevision(RevisionKey(document,UUID(0,30)),body,structure)
        val state=good(ApprovalWorkflow.start(AccessPolicy(listOf(Grant(owner.principalId,project,Capability.entries.toSet())))).propose(owner,revision)).state
        val entry=entries(state).single { "/revisions/" in it.path }
        for(n in 6..8) {
            val bodyCodec=CoreSnapshotCodec(bodyBytes=n)
            val indexCodec=CoreSnapshotCodec(admission=JsonLimits(collection=n))
            if(n<7) {
                assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { bodyCodec.admitRecord(entry,body) }.problem)
                assertEquals(CodecProblem.ADMISSION_LIMIT,assertFailsWith<CodecFault> { indexCodec.admitRecord(entry,body) }.problem)
            } else { bodyCodec.admitRecord(entry,body); indexCodec.admitRecord(entry,body) }
        }
    }

    @Test fun `snapshot and entry own collections and byte buffers`() {
        val f=WorkflowFixture().baseline(); val s=restored(f.state).snapshot()
        assertFailsWith<UnsupportedOperationException> { (s.requests as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { (s.revisions as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (s.grants.first().capabilities as MutableSet).clear() }
        val bytes=StrictJson.encode(CoreRecordCodec.header()); val expected=bytes.copyOf(); val entry=CoreEntry(CoreEntryKey.Header,bytes)
        bytes.fill(0); assertContentEquals(expected,entry.bytes())
        for(n in expected.size-1..expected.size+1) {
            if(n<expected.size) assertEquals(CodecProblem.RESOURCE_UNAVAILABLE,assertFailsWith<CodecFault> { CoreEntry(CoreEntryKey.Header,expected,n) }.problem)
            else assertContentEquals(expected,CoreEntry(CoreEntryKey.Header,expected,n).bytes())
        }
    }

    @Test fun `all stored bound digests and count overflow are checked rather than trusted`() {
        val all=entries(WorkflowFixture().baseline().state)
        fun changed(family: String,field: String,value: JsonValue): List<CoreEntry> = mutateRecord(all,family) { root ->
            val payload=(root.getValue("payload") as JObject).entries
            JObject(root+("payload" to JObject(payload+(field to value))))
        }
        for(field in listOf("structureDigest","revisionDigest","contextDigest")) assertEquals(CodecProblem.DIGEST_MISMATCH,
            assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(changed("revisions",field,str("0".repeat(64))).asSequence()) }.problem)
        for(field in listOf("packetDigest","requestDigest")) assertEquals(CodecProblem.DIGEST_MISMATCH,
            assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(changed("requests",field,str("0".repeat(64))).asSequence()) }.problem)
        for(count in listOf(-1L,Int.MAX_VALUE.toLong()+1)) assertEquals(CodecProblem.INVALID_SHAPE,
            assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(changed("revisions","bodyBytes",JNumber(count)).asSequence()) }.problem)
        val packet=mutateRecord(all,"requests") { root ->
            val payload=(root.getValue("payload") as JObject).entries
            val p=(payload.getValue("packet") as JObject).entries
            JObject(root+("payload" to JObject(payload+("packet" to JObject(p+("question" to str("Changed exact wording")))))))
        }
        assertEquals(CodecProblem.DIGEST_MISMATCH,assertFailsWith<CodecFault> { CoreSnapshotCodec().restore(packet.asSequence()) }.problem)
    }
}
