// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Real Linux Git boundary; deliberately synthetic codec/scope/reducer ports, NOT assembled AUTH/CORE. */
class H1PublicationTest {
    internal class Fixture {
        var mode=0
        var evaluated=0
        var preflighted=0
        var cas=0
        var boundary: (StoreSnapshot,StoreSnapshot,Receipt)->Unit={ _,_,_ -> } // Synthetic test integration only.
        val authorized=mutableListOf<GitOid>()
        val readPaths=mutableListOf<String>()
        val key=RecordKey("work","task-heads",obj("projectId" to uuid(id(2)),"taskId" to uuid(id(8))))
        val binding=H1PublicationBindings(
            canonicalize={ raw ->
                val f=StrictJson.decode(raw).fields("formatVersion","requestKey","projectId","operation","payload")
                integrity(f.getValue("operation").string()=="set_task_head")
                val p=f.getValue("payload").fields("key","expectedGeneration","title","draft","executable","bodies")
                integrity(StrictJson.encode(p.getValue("key")).contentEquals(StrictJson.encode(key.key)))
                p.getValue("expectedGeneration").let { if(it!==JNull)it.long() }
                integrity(p.getValue("title").string().isNotEmpty() && p.getValue("draft")===JNull && p.getValue("executable")===JNull && p.getValue("bodies").list().isEmpty())
                StrictJson.encode(JObject(f))
            },
            authorize={ h,_ -> authorized.add(h.oid); when(mode) {
                0 -> CurrentCommandScope.NewEffect(AuthenticatedActor(id(3),id(4)))
                1 -> CurrentCommandScope.ReplayOnly(AuthenticatedActor(id(3),id(4)))
                else -> CurrentCommandScope.Denied
            } },
            allowedWrites={ _,_ -> setOf(key) },
            validateRecord={ k,raw,_ ->
                if(k.namespace=="work") {
                    val e=StrictJson.decode(raw).fields("formatVersion","family","key","payload")
                    val p=e.getValue("payload").fields("generation","title","draft","executable")
                    p.getValue("generation").long(); p.getValue("title").string()
                }
            },
            preflight={ _,_,_ -> preflighted++ },validateBoundary={ h,c,r -> boundary(h,c,r) })
        fun ports()=StorePorts(mapOf(
            "auth" to NamespaceValidator(obj("formatVersion" to num(2),"profileVersion" to num(2))) { },
            "work" to NamespaceValidator(obj("formatVersion" to num(1),"modelVersion" to num(2))) { },
            "policy" to NamespaceValidator(obj("formatVersion" to num(2))) { }),
            validateTransition={ _,_,_,_ -> },validateHistoricalReceipt={ _,_ -> },fileRead={ readPaths.add(it) },h1=binding)
        fun genesis()=listOf(
            StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),
            StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(2),"profileVersion" to num(2)))),
            StoreFile("work/format.json",StrictJson.encode(obj("formatVersion" to num(1),"modelVersion" to num(2)))),
            StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(2)))))
        fun command(request: Int=5,title: String="task",generation: JsonValue=JNull)=StoreCommand.decodeH1(StrictJson.encode(obj(
            "formatVersion" to num(3),"requestKey" to uuid(id(request)),"projectId" to uuid(id(2)),"operation" to str("set_task_head"),
            "payload" to obj("key" to key.key,"expectedGeneration" to generation,"title" to str(title),"draft" to JNull,"executable" to JNull,"bodies" to JArray(emptyList())))),binding)
        fun record(generation: Int=0,title: String="task")=StrictJson.encode(CoreRecordCodec.envelope("task-heads",key.key,obj("generation" to num(generation),"title" to str(title),"draft" to JNull,"executable" to JNull)))
        fun evaluator(raw: ByteArray=record())=CommandEvaluator { _,c,_ ->
            evaluated++
            val p=StrictJson.decode(raw).fields("formatVersion","family","key","payload").getValue("payload") as JObject
            Evaluation(LogicalOutcome.Success(c.operation,obj("key" to key.key,"generation" to p.entries.getValue("generation"),"digest" to str(sha(raw))),false),listOf(RecordChange(key,raw)))
        }
        fun reject()=CommandEvaluator { _,_,_ -> evaluated++; Evaluation(LogicalOutcome.Rejected("work","INVALID_REFERENCE"),emptyList()) }
        val access=CommandAccess { _,_ -> error("H1 must use required current scope binding, not legacy callback") }
        fun publication(next: PublicationDriver=PublicationDriver.Native)=PublicationDriver { git,h,c -> cas++; next.compareAndSet(git,h,c) }
        fun create(dir: Path=storeTestDirectory("h1-publication-"),bounds: StoreBounds=StoreBounds(),driver: PublicationDriver=publication(),policy: AdmissionPolicy=AdmissionPolicy(formatVersion=2))=
            GitCommandStore.initialize(dir,id(1),genesis(),ports(),bounds,publication=driver,admission=policy)
        fun head(s: GitCommandStore)=s.read(id(2),ReadAccess { _,_ -> true }) { it.oid }
    }

    @Test fun `fresh exact registry policy2 journal3 work effect receipt and reopen`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-fresh-")
        f.create(dir).use { s ->
            val registry=s.read(id(2),ReadAccess { _,_ -> true }) { StrictJson.decode(it.file("store.json")!!) as JObject }
            assertEquals(setOf("core","auth","work","commands","policy"),(registry.entries.getValue("namespaces") as JObject).entries.keys)
            val first=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals(3,first.receipt.journalVersion)
            assertEquals(1,first.receipt.writes.size)
            assertEquals(1,f.cas)
            assertContentEquals(f.record(),s.read(id(2),ReadAccess { _,_ -> true }) { it.file(f.key.path)!! })
            f.mode=1
            val replay=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertTrue(replay.commandReplayed)
            assertEquals(first.receipt.validatedAt,replay.receipt.validatedAt)
            assertEquals(1,f.evaluated)
        }
        GitCommandStore.open(dir,id(1),f.ports()).use { s -> assertTrue(assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator())).commandReplayed) }
    }

    @Test fun `H1 and legacy profiles refuse each other without migration reset or namespace omission`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-refuse-legacy-")
        val oldPorts=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },"policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_ -> },{ _,_ -> })
        val oldFiles=listOf(StoreFile("core/format.json",StrictJson.encode(CoreRecordCodec.header())),StoreFile("auth/format.json",StrictJson.encode(oldPorts.namespaces.getValue("auth").header)),StoreFile("policy/format.json",StrictJson.encode(oldPorts.namespaces.getValue("policy").header)))
        GitCommandStore.initialize(dir,id(1),oldFiles,oldPorts).close()
        val ref=dir.resolve("data.git/refs/heads/state"); val before=Files.readString(ref)
        assertEquals(StoreProblem.UNSUPPORTED,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),f.ports()) }.problem)
        assertEquals(before,Files.readString(ref))
        GitCommandStore.open(dir,id(1),oldPorts).close()
        val h1=storeTestDirectory("legacy-refuse-h1-")
        f.create(h1).close(); val h1Before=Files.readString(h1.resolve("data.git/refs/heads/state"))
        assertEquals(StoreProblem.UNSUPPORTED,assertFailsWith<StoreFault> { GitCommandStore.open(h1,id(1),oldPorts) }.problem)
        assertEquals(h1Before,Files.readString(h1.resolve("data.git/refs/heads/state")))
        assertFails { f.create(h1) }
        GitCommandStore.open(h1,id(1),f.ports()).close()
    }

    @Test fun `terminal replay-only absence never evaluates preflights CAS or records rejection`() {
        val f=Fixture(); f.mode=1
        f.create(bounds=StoreBounds(historyCommits=1)).use { s ->
            val h=f.head(s)
            repeat(2) { assertIs<CommandResult.Denied>(s.execute(f.command(),f.access,f.reject())) }
            assertEquals(h,f.head(s)); assertEquals(0,f.evaluated); assertEquals(0,f.preflighted); assertEquals(0,f.cas)
            assertFalse(f.readPaths.any { it.startsWith("work/records/") })
        }
    }

    @Test fun `terminal matches rejection and conflicts changed fingerprint but explicit denial hides both`() {
        val f=Fixture()
        f.create().use { s ->
            val first=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.reject()))
            f.mode=1
            val replay=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals((first.receipt.outcome as LogicalOutcome.Rejected).code,(replay.receipt.outcome as LogicalOutcome.Rejected).code)
            assertIs<CommandResult.IdentityConflict>(s.execute(f.command(title="changed"),f.access,f.evaluator()))
            f.readPaths.clear(); f.mode=2
            assertIs<CommandResult.Denied>(s.execute(f.command(),f.access,f.evaluator()))
            assertFalse(f.readPaths.any { it.startsWith("commands/receipts/") })
            assertEquals(1,f.evaluated); assertEquals(1,f.cas)
        }
    }

    @Test fun `history counts genesis and rejection receipts yet original terminal replay remains available`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-history-"); val bounds=StoreBounds(historyCommits=2)
        f.create(dir,bounds).use { s ->
            assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.reject()))
            val refused=assertIs<CommandResult.Refused>(s.execute(f.command(6),f.access,f.reject()))
            assertEquals(StoreProblem.UNAVAILABLE,refused.problem); assertEquals(1,f.evaluated)
            f.mode=1
            assertTrue(assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.reject())).commandReplayed)
        }
        GitCommandStore.open(dir,id(1),f.ports(),bounds).use { s -> assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.reject())) }
        val tooSmall=assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),f.ports(),StoreBounds(historyCommits=1)) }
        assertEquals(StoreProblem.UNAVAILABLE,tooSmall.problem)
    }

    @Test fun `new command3 requires bound canonical codec and legacy decoder does not admit it`() {
        val f=Fixture(); val c=f.command()
        assertFails { StoreCommand.decode(c.bytes()) }
        assertFails { StoreCommand.decodeH1(c.bytes().toString(Charsets.UTF_8).replace("\"title\":\"task\"","\"title\":\"task\",\"actor\":\"owner\"").toByteArray(),f.binding) }
        val other=Fixture()
        f.create().use { s ->
            assertEquals(StoreProblem.UNSUPPORTED,assertIs<CommandResult.Refused>(s.execute(other.command(),f.access,f.evaluator())).problem)
            assertEquals(0,f.evaluated)
        }
    }

    @Test fun `complete mutable generation and result digest binding reject false outcome before publication`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-mutable-history-")
        f.create(dir).use { s ->
            assertIs<CommandResult.Refused>(s.execute(f.command(),f.access,f.evaluator(f.record(1))))
            assertEquals(0,f.cas)
            assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertIs<CommandResult.Refused>(s.execute(f.command(6,generation=num(0)),f.access,f.evaluator(f.record(2,"next"))))
            assertEquals(1,f.cas)
            assertIs<CommandResult.Settled>(s.execute(f.command(6,title="next",generation=num(0)),f.access,f.evaluator(f.record(1,"next"))))
        }
        GitCommandStore.open(dir,id(1),f.ports()).use { s ->
            f.mode=1
            val original=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.reject()))
            assertEquals(0L,((original.receipt.outcome as LogicalOutcome.Success).result as JObject).entries.getValue("generation").long())
            assertContentEquals(f.record(1,"next"),s.read(id(2),ReadAccess { _,_ -> true }) { it.file(f.key.path)!! })
        }
    }

    @Test fun `tighter new work admission does not classify historical supported records as corrupt`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-history-policy-")
        val old=f.record(title="historical text")
        GitCommandStore.initialize(dir,id(1),f.genesis()+StoreFile(f.key.path,old),f.ports(),admission=AdmissionPolicy(formatVersion=2)).close()
        GitCommandStore.open(dir,id(1),f.ports()).use { s -> assertContentEquals(old,s.read(id(2),ReadAccess { _,_ -> true }) { it.file(f.key.path)!! }) }
        val g=Fixture()
        g.create(policy=AdmissionPolicy(formatVersion=2,workRecordBytes=old.size-1)).use { s ->
            assertEquals(StoreProblem.ADMISSION,assertIs<CommandResult.Refused>(s.execute(g.command(title="historical text"),g.access,g.evaluator(old))).problem)
            assertEquals(0,g.cas)
        }
    }

    @Test fun `required outcome preflight fails before evaluator and any CAS`() {
        val f=Fixture()
        val b=H1PublicationBindings(f.binding.canonicalize,f.binding.authorize,f.binding.allowedWrites,f.binding.validateRecord,{ _,_,_ -> fail(StoreProblem.UNAVAILABLE) },f.binding.validateBoundary)
        val p=f.ports(); val ports=StorePorts(p.namespaces,p.validateTransition,p.validateHistoricalReceipt,h1=b)
        GitCommandStore.initialize(storeTestDirectory("h1-preflight-"),id(1),f.genesis(),ports,publication=f.publication(),admission=AdmissionPolicy(formatVersion=2)).use { s ->
            val c=StoreCommand.decodeH1(f.command().bytes(),b)
            assertEquals(StoreProblem.UNAVAILABLE,assertIs<CommandResult.Refused>(s.execute(c,f.access,f.evaluator())).problem)
            assertEquals(0,f.evaluated); assertEquals(0,f.cas)
        }
    }

    private fun competitor(git: NativeGit,h: GitOid,c: StoreCommand) {
        val receipt=Receipt(id(1),CommandIdentity(id(3),c.projectId,c.operation,c.requestKey),3,c.fingerprint,h,id(4),LogicalOutcome.Rejected("work","INVALID_REFERENCE"),emptyList(),3)
        val files=git.call(listOf("ls-tree","-rz","--full-tree",h.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }.associate { line ->
            val f=line.split('\t'); f[1] to GitOid("sha1",f[0].substringAfterLast(' '))
        }.toMutableMap()
        files[receipt.path]=git.blob(JournalCodec.encode(receipt))
        fun tree(prefix: String): GitOid {
            val names=files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
            val bytes=names.joinToString("") { name ->
                val path=prefix+name; val oid=files[path]
                if(oid==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "100644 blob ${oid.hex}\t$name\u0000"
            }.toByteArray(Charsets.US_ASCII)
            return git.oid(git.call(listOf("mktree","-z"),bytes).checked())
        }
        git.update(h,git.commit(tree(""),h)).checked()
    }

    @Test fun `CAS recapture newly terminal absent key never reevaluates or publishes rejection`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-terminal-race-")
        val driver=f.publication(PublicationDriver { git,h,c -> competitor(git,h,f.command(90)); f.mode=1; git.update(h,c) })
        f.create(dir,driver=driver).use { s ->
            val before=f.head(s)
            assertIs<CommandResult.Denied>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals(1,f.evaluated); assertEquals(1,f.cas); assertEquals(1,f.preflighted)
            assertEquals(2,f.authorized.size); assertEquals(before,f.authorized[0]); assertNotEquals(before,f.authorized[1])
            val paths=s.read(id(2),ReadAccess { _,_ -> true }) { it.entries.keys.filter { p -> p.startsWith("commands/receipts/") } }
            assertEquals(listOf(CommandIdentity(id(3),id(2),"set_task_head",id(90)).path),paths)
            assertFalse(Files.exists(dir.resolve("publication-in-flight")))
        }
    }

    @Test fun `CAS recapture terminal matching original outcome settles without new effect`() {
        val f=Fixture()
        val driver=f.publication(PublicationDriver { git,h,c -> git.update(h,c).checked(); f.mode=1; GitReply(1,byteArrayOf(),"completed lost ack".toByteArray()) })
        f.create(driver=driver).use { s ->
            val result=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertTrue(result.commandReplayed); assertEquals(1,f.evaluated); assertEquals(1,f.cas)
            assertEquals(2,f.authorized.size); assertNotEquals(f.authorized[0],f.authorized[1])
            assertEquals(f.authorized[0],result.receipt.validatedAt)
        }
    }

    @Test fun `CAS qualified new-effect neighbor rebuilds same command preserving unrelated receipt`() {
        val f=Fixture(); var raced=false
        val driver=f.publication(PublicationDriver { git,h,c -> if(!raced) { raced=true; competitor(git,h,f.command(90)) }; git.update(h,c) })
        f.create(driver=driver).use { s ->
            val result=assertIs<CommandResult.Settled>(s.execute(f.command(),f.access,f.evaluator()))
            assertFalse(result.commandReplayed); assertEquals(2,f.evaluated); assertEquals(2,f.cas)
            assertEquals(2,s.read(id(2),ReadAccess { _,_ -> true }) { it.entries.keys.count { p -> p.startsWith("commands/receipts/") } })
            f.mode=1
            assertIs<CommandResult.Settled>(s.execute(f.command(90),f.access,f.reject()))
        }
    }

    @Test fun `H1 nonfinal actual publication failed audit retains unknown barriers without rollback`() {
        val f=Fixture(); val dir=storeTestDirectory("h1-unknown-")
        var observed: GitOid?=null
        val driver=f.publication(PublicationDriver { git,h,c -> git.update(h,c).checked(); observed=git.head(); GitReply(1,byteArrayOf(),"completed lost ack".toByteArray()) })
        f.create(dir,bounds=StoreBounds(attempts=3,auditBytes=1),driver=driver).use { s ->
            assertIs<CommandResult.Unknown>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals(1,f.evaluated); assertEquals(1,f.cas)
            assertEquals(observed!!.hex,Files.readString(dir.resolve("data.git/refs/heads/state")).trim())
            assertTrue(Files.exists(dir.resolve("publication-in-flight")))
            assertIs<CommandResult.Unknown>(s.execute(f.command(),f.access,f.evaluator()))
            assertEquals(1,f.cas)
        }
        assertTrue(Files.exists(dir.resolve("publication-in-flight"))); assertTrue(Files.exists(dir.resolve("runtime-active")))
        assertEquals(StoreProblem.RECOVERY_REQUIRED,assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),f.ports()) }.problem)
    }
}
