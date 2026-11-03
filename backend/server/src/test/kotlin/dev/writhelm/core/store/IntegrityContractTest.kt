// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.WorkflowFixture
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.Test

/** Negative corruption witnesses touch ONLY designated disposable private test-data repos. */
class IntegrityContractTest {
    private fun directory()=storeTestDirectory("integrity-test-")
    private fun ports()=StorePorts(mapOf("auth" to NamespaceValidator(obj("formatVersion" to num(1),"profileVersion" to num(1))) { },"policy" to NamespaceValidator(obj("formatVersion" to num(1))) { }),{ _,_,_,_-> },{ _,_-> })
    private fun genesis(): List<StoreFile> {
        val f=WorkflowFixture().also { it.add(it.a) }
        return buildList {
            CoreSnapshotCodec().export(f.state) { add(StoreFile(it.path,it.bytes())) }
            add(StoreFile("auth/format.json",StrictJson.encode(obj("formatVersion" to num(1),"profileVersion" to num(1)))))
            add(StoreFile("policy/format.json",StrictJson.encode(obj("formatVersion" to num(1)))))
        }
    }
    private fun settled(dir: Path) {
        GitCommandStore.initialize(dir,id(1),genesis(),ports()).use { s ->
            assertIs<CommandResult.Settled>(s.execute(authCommand(),CommandAccess { _,_-> AuthenticatedActor(id(3),id(4)) },CommandEvaluator { _,_,_-> Evaluation(LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList()) }))
        }
    }
    private fun edit(dir: Path,modify: (NativeGit,MutableMap<String,Pair<String,GitOid>>)->Unit) {
        RuntimeOwner.acquire(dir).use { owner ->
            val git=NativeGit(owner); git.qualify(); git.verifyRepository(); val h=git.head()
            val files=git.call(listOf("ls-tree","-rz","--full-tree",h.hex)).checked().toString(Charsets.US_ASCII).split('\u0000').filter { it.isNotEmpty() }.associate { line ->
                val split=line.split('\t'); split[1] to (split[0].substringBefore(' ') to GitOid("sha1",split[0].substringAfterLast(' ')))
            }.toMutableMap()
            modify(git,files)
            fun tree(prefix: String): GitOid {
                val names=files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
                val bytes=names.joinToString("") { name ->
                    val path=prefix+name; val file=files[path]
                    if(file==null)"040000 tree ${tree("$path/").hex}\t$name\u0000" else "${file.first} blob ${file.second.hex}\t$name\u0000"
                }.toByteArray(Charsets.US_ASCII)
                return git.oid(git.call(listOf("mktree","-z"),bytes).checked())
            }
            git.update(h,git.commit(tree(""),h)).checked()
        }
    }
    @Test fun `non genesis no receipt refuses startup without resetting data`() {
        val dir=directory(); settled(dir)
        edit(dir) { _,_-> }
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
        assertTrue(Files.isDirectory(dir.resolve("data.git/objects")))
    }
    @Test fun `published receipt deletion plus new receipt is detected`() {
        val dir=directory(); settled(dir)
        edit(dir) { git,files ->
            val path=files.keys.single { it.startsWith("commands/receipts/") }; files.remove(path)
            val c=authCommand(20); val receipt=Receipt(id(1),CommandIdentity(id(3),id(2),c.operation,c.requestKey),2,c.fingerprint,git.head(),id(4),LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
            files[receipt.path]="100644" to git.blob(JournalCodec.encode(receipt))
        }
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
    }
    @Test fun `two new receipts in one boundary refuse`() {
        val dir=directory(); settled(dir)
        edit(dir) { git,files ->
            for(n in listOf(21,22)) {
                val c=authCommand(n); val receipt=Receipt(id(1),CommandIdentity(id(3),id(2),c.operation,c.requestKey),2,c.fingerprint,git.head(),id(4),LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
                files[receipt.path]="100644" to git.blob(JournalCodec.encode(receipt))
            }
        }
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
    }
    @Test fun `self candidate OID absent but wrong historical basis refuses`() {
        val dir=directory(); settled(dir)
        edit(dir) { git,files ->
            val c=authCommand(23); val receipt=Receipt(id(1),CommandIdentity(id(3),id(2),c.operation,c.requestKey),2,c.fingerprint,GitOid("sha1","e".repeat(40)),id(4),LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
            files[receipt.path]="100644" to git.blob(JournalCodec.encode(receipt))
        }
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
    }
    @Test fun `mode symlink and unknown namespace are not read as service data`() {
        val dir=directory(); settled(dir)
        edit(dir) { git,files -> files["core/format.json"]="120000" to git.blob("/etc/passwd".toByteArray()) }
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
        val other=directory(); settled(other)
        edit(other) { git,files -> files["unknown/format.json"]="100644" to git.blob("{}".toByteArray()) }
        assertEquals(StoreProblem.UNSUPPORTED,assertFailsWith<StoreFault> { GitCommandStore.open(other,id(1),ports()) }.problem)
    }
    @Test fun `current base unrecognized extra effect without descriptor cannot pass audit`() {
        val dir=directory(); settled(dir)
        edit(dir) { git,files ->
            val c=authCommand(24); val r=Receipt(id(1),CommandIdentity(id(3),id(2),c.operation,c.requestKey),2,c.fingerprint,git.head(),id(4),LogicalOutcome.Rejected("auth","AUTH_TARGET_NOT_FOUND"),emptyList())
            files[r.path]="100644" to git.blob(JournalCodec.encode(r))
            val path=files.keys.first { it.startsWith("core/bodies/") }; files[path]="100644" to git.blob("forged body".toByteArray())
        }
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
    }
    @Test fun `repository arbitrary config refused before repository Git reads`() {
        val dir=directory(); settled(dir)
        val config=dir.resolve("data.git/config")
        Files.writeString(config,Files.readString(config)+"[include]\n\tpath = /untrusted/not-read\n")
        assertFails { GitCommandStore.open(dir,id(1),ports()) }
    }
    @Test fun `restore budget shortage is explicit unavailable not corruption or new genesis`() {
        val dir=directory(); settled(dir)
        val fault=assertFailsWith<StoreFault> { GitCommandStore.open(dir,id(1),ports(),StoreBounds(auditBytes=1)) }
        assertEquals(StoreProblem.UNAVAILABLE,fault.problem)
        assertFails { GitCommandStore.initialize(dir,id(1),genesis(),ports()) }
    }
}
