// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import java.nio.file.Files
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Actual Linux Git edges with synthetic peer validators; not actual APP/authority qualification. */
class H1BoundaryContractTest {
    private data class Edge(val h: GitOid,val c: GitOid,val receipt: String,val after: String?)
    private fun checkEdge(h: StoreSnapshot,c: StoreSnapshot,r: Receipt): Edge {
        assertEquals(h.oid,r.validatedAt)
        assertNotEquals(h.oid,c.oid)
        assertNull(h.file(r.path)); assertContentEquals(JournalCodec.encode(r),c.file(r.path))
        val actual=(h.entries.keys+c.entries.keys).filter { h.entries[it]!=c.entries[it] }.toSet()
        assertEquals(r.writes.map { it.path }.toSet()+r.path,actual)
        for(w in r.writes) {
            assertEquals(w.beforeDigest,h.file(w.path)?.let(::sha))
            assertEquals(w.afterDigest,c.file(w.path)?.let(::sha))
        }
        return Edge(h.oid,c.oid,r.path,r.writes.singleOrNull()?.afterDigest)
    }
    private fun subjectChecked(h: StoreSnapshot,c: StoreSnapshot,r: Receipt): Edge {
        val edge=checkEdge(h,c,r)
        for(w in r.writes.filter { it.record.namespace=="work" && it.record.family=="task-heads" }) {
            val raw=assertNotNull(c.file(w.path))
            val envelope=StrictJson.decode(raw).fields("formatVersion","family","key","payload")
            val p=envelope.getValue("payload").fields("generation","title","draft","executable")
            val draft=p.getValue("draft")
            if(draft!==JNull) {
                val target=draft.fields("projectId","taskId","revision")
                val key=envelope.getValue("key").fields("projectId","taskId")
                integrity(uuid(target.getValue("projectId"))==uuid(key.getValue("projectId")) && uuid(target.getValue("taskId"))==uuid(key.getValue("taskId")))
            }
        }
        return edge
    }
    private fun foreignRecord(f: H1PublicationTest.Fixture): ByteArray {
        val e=StrictJson.decode(f.record()) as JObject
        val p=e.entries.getValue("payload") as JObject
        val foreign=obj("projectId" to uuid(id(2)),"taskId" to uuid(id(99)),"revision" to num(0))
        return StrictJson.encode(JObject(e.entries+("payload" to JObject(p.entries+("draft" to foreign)))))
    }

    @Test fun `publication and every reopened original edge see exact H C after bytes not latest pointer`() {
        val f=H1PublicationTest.Fixture(); val dir=storeTestDirectory("h1-boundary-history-")
        val edges=mutableListOf<Edge>(); var legacyCalls=0
        f.boundary={ h,c,r -> edges.add(subjectChecked(h,c,r)) }
        val p=f.ports(); val ports=StorePorts(p.namespaces,p.validateTransition,{ _,_ -> legacyCalls++ },h1=f.binding)
        GitCommandStore.initialize(dir,id(1),f.genesis(),ports,admission=AdmissionPolicy(formatVersion=2)).use { s ->
            val first=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals(first.publication,edges[0].c)
            val second=assertIs<CommandResult.Settled>(s.execute(f.command(6,title="next",generation=num(0)),f.access,f.evaluator(f.record(1,"next"))))
            assertEquals(first.publication,edges[1].h); assertEquals(second.publication,edges[1].c)
            assertIs<CommandResult.Settled>(s.execute(f.command(7),f.access,f.reject()))
            assertEquals(3,legacyCalls); assertEquals(3,edges.size)
        }
        val originals=edges.toList(); edges.clear()
        GitCommandStore.open(dir,id(1),ports).close()
        assertEquals(originals,edges,"audit must supply each original boundary, including rejection, not the final H/C")
        assertEquals(6,legacyCalls,"legacy historical metadata/provenance callback still runs")
        assertEquals(sha(f.record()),originals[0].after)
        assertEquals(sha(f.record(1,"next")),originals[1].after)
    }

    @Test fun `callback validates complete registration across core auth work on publication and reopen`() {
        val f=H1RegistrationTest.Fixture(); val dir=storeTestDirectory("h1-boundary-registration-")
        val edges=mutableListOf<Edge>()
        f.boundary={ h,c,r ->
            edges.add(checkEdge(h,c,r)); assertEquals(setOf("core","auth","work"),r.writes.map { it.record.namespace }.toSet())
            for((key,payload) in f.payloads)assertContentEquals(f.record(key,payload),c.file(key.path))
            assertNotNull(h.file(f.assignment.path))
            assertEquals(6,r.writes.count { h.file(it.path)==null })
        }
        GitCommandStore.initialize(dir,id(1),f.genesis,f.ports,admission=AdmissionPolicy(formatVersion=2)).use { s ->
            assertIs<CommandResult.Settled>(s.execute(f.command,f.access,f.evaluation()))
        }
        GitCommandStore.open(dir,id(1),f.ports).close()
        assertEquals(2,edges.size); assertEquals(edges[0],edges[1])
    }

    @Test fun `shape valid foreign subject is rejected by boundary callback before CAS without durable outcome`() {
        val f=H1PublicationTest.Fixture(); var calls=0
        f.boundary={ h,c,r -> calls++; subjectChecked(h,c,r) }
        f.create().use { s ->
            val h=f.head(s)
            val result=assertIs<CommandResult.Refused>(s.execute(f.command(),f.access,f.evaluator(foreignRecord(f))))
            assertEquals(StoreProblem.INTEGRITY,result.problem); assertEquals(1,calls); assertEquals(0,f.cas)
            assertEquals(h,f.head(s)); assertNull(s.read(id(2),ReadAccess { _,_ -> true }) { it.file(f.key.path) })
            val good=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertFalse(good.commandReplayed); assertEquals(2,calls); assertEquals(1,f.cas)
        }
    }

    @Test fun `result generation correspondence is independently rejected before joint callback`() {
        val f=H1PublicationTest.Fixture(); var calls=0
        f.boundary={ h,c,r -> calls++; subjectChecked(h,c,r) }
        f.create().use { s ->
            val bad=CommandEvaluator { h,c,a ->
                val e=f.evaluator().evaluate(h,c,a)
                Evaluation(LogicalOutcome.Success(c.operation,obj("key" to f.key.key,"generation" to num(9),"digest" to str(sha(f.record()))),false),e.changes)
            }
            assertEquals(StoreProblem.INTEGRITY,assertIs<CommandResult.Refused>(s.execute(f.command(),f.access,bad)).problem)
            assertEquals(0,calls); assertEquals(0,f.cas)
            assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals(1,calls)
        }
    }

    @Test fun `startup callback rejects historical wrong after subject even with valid shape hashes and tree`() {
        val f=H1PublicationTest.Fixture(); val dir=storeTestDirectory("h1-boundary-audit-refusal-")
        // Deliberately deficient synthetic initial validator seeds a structurally valid wrong edge.
        // This is not a genuine CORE/AUTH-admitted workflow; it isolates the historical callback.
        f.boundary={ h,c,r -> checkEdge(h,c,r) }
        f.create(dir).use { s -> assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator(foreignRecord(f)))) }
        val ref=dir.resolve("data.git/refs/heads/state"); val published=Files.readString(ref); var calls=0
        f.boundary={ h,c,r -> calls++; subjectChecked(h,c,r) }
        assertEquals(StoreProblem.INTEGRITY,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),f.ports()) }.problem)
        assertEquals(1,calls); assertEquals(published,Files.readString(ref)); assertEquals(1,f.cas)
    }

    @Test fun `lost acknowledgement reconciles the identical original callback edge without second publication`() {
        val f=H1PublicationTest.Fixture(); val edges=mutableListOf<Edge>()
        f.boundary={ h,c,r -> edges.add(subjectChecked(h,c,r)) }
        val driver=f.publication(PublicationDriver { git,h,c -> git.update(h,c).checked(); GitReply(1,byteArrayOf(),"completed lost ack".toByteArray()) })
        f.create(driver=driver).use { s ->
            assertTrue(assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator())).commandReplayed)
            assertEquals(2,edges.size); assertEquals(edges[0],edges[1]); assertEquals(1,f.cas); assertEquals(1,f.evaluated)
        }
    }

    @Test fun `nonfinal and final recapture callback failure after real publication retain unknown barriers`() {
        for(attempts in listOf(1,3)) {
            val f=H1PublicationTest.Fixture(); val dir=storeTestDirectory("h1-boundary-unknown-$attempts-"); val edges=mutableListOf<Edge>()
            f.boundary={ h,c,r -> edges.add(subjectChecked(h,c,r)); if(edges.size==2)fail(StoreProblem.UNAVAILABLE) }
            val driver=f.publication(PublicationDriver { git,h,c -> git.update(h,c).checked(); GitReply(1,byteArrayOf(),"completed lost ack".toByteArray()) })
            f.create(dir,bounds=StoreBounds(attempts=attempts),driver=driver).use { s ->
                assertIs<CommandResult.Unknown>(s.execute(f.command(),f.access,f.evaluator()))
                assertEquals(2,edges.size); assertEquals(edges[0],edges[1]); assertEquals(1,f.cas); assertEquals(1,f.evaluated)
                assertEquals(edges[0].c.hex,Files.readString(dir.resolve("data.git/refs/heads/state")).trim())
                assertTrue(Files.exists(dir.resolve("publication-in-flight"))); assertTrue(Files.exists(dir.resolve("runtime-active")))
                assertIs<CommandResult.Unknown>(s.execute(f.command(),f.access,f.evaluator())); assertEquals(1,f.cas)
            }
            assertTrue(Files.exists(dir.resolve("publication-in-flight"))); assertTrue(Files.exists(dir.resolve("runtime-active")))
            assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),f.ports()) }.problem)
        }
    }

    @Test fun `boundary candidate reads consume shared audit byte budget rather than bypassing it`() {
        val f=H1PublicationTest.Fixture(); val dir=storeTestDirectory("h1-boundary-budget-")
        f.boundary={ h,c,r -> subjectChecked(h,c,r) }
        f.create(dir).use { s -> assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator())) }
        var callbackReached=false
        f.boundary={ h,c,r ->
            subjectChecked(h,c,r); callbackReached=true
            repeat(1000) { assertNotNull(c.file(f.key.path)) }
        }
        val result=assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),f.ports(),bounds=StoreBounds(auditBytes=65536)) }
        assertTrue(callbackReached); assertEquals(StoreProblem.UNAVAILABLE,result.problem); assertEquals(1,f.cas)
    }
}
