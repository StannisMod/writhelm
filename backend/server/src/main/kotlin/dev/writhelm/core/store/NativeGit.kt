// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.*
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

internal data class ProcessBounds(val inputBytes: Int=8*1024*1024,val outputBytes: Int=16*1024*1024,val errorBytes: Int=16384,val millis: Long=15000) {
    init { require(inputBytes in 1..8*1024*1024 && outputBytes in 1..16*1024*1024 && errorBytes in 1..16384 && millis in 1..15000) }
}
internal class GitReply(val exit: Int,val output: ByteArray,val stderr: ByteArray) {
    fun checked(): ByteArray { if(exit!=0 || stderr.isNotEmpty())fail(StoreProblem.UNAVAILABLE); return output }
}
/** Marker has no business information. Any uncertain child blocks reuse, including after close/restart.
 * No automatic marker/lock removal or child termination. Operator reconciliation is a held gate. */
internal class RuntimeOwner private constructor(val directory: Path,private val channel: FileChannel,private val lock: FileLock): AutoCloseable {
    private val active=directory.resolve("runtime-active")
    private val barrier=directory.resolve("publication-in-flight")
    private var uncertain=false
    private var closed=false
    init {
        if(Files.exists(active,LinkOption.NOFOLLOW_LINKS) || Files.exists(barrier,LinkOption.NOFOLLOW_LINKS)) {
            lock.release(); channel.close(); fail(StoreProblem.RECOVERY_REQUIRED)
        }
        try { writeMarker(active) } catch(e: Exception) { lock.release(); channel.close(); throw e }
    }
    private fun writeMarker(path: Path) {
        FileChannel.open(path,CREATE_NEW,WRITE,LinkOption.NOFOLLOW_LINKS).use { c ->
            val b=ByteBuffer.wrap("recovery-required unless cleanly closed; no canonical facts\n".toByteArray())
            while(b.hasRemaining())c.write(b)
            c.force(true)
        }
        syncDirectory()
    }
    private fun syncDirectory() { FileChannel.open(directory,READ).use { it.force(true) } }
    @Synchronized fun requireUsable() { if(closed || uncertain)fail(StoreProblem.RECOVERY_REQUIRED) }
    @Synchronized fun beginPublication() { requireUsable(); try { writeMarker(barrier) } catch(_: Exception) { uncertain=true; fail(StoreProblem.RECOVERY_REQUIRED) } }
    @Synchronized fun finishPublication() { requireUsable(); try { Files.delete(barrier); syncDirectory() } catch(_: Exception) { uncertain=true; fail(StoreProblem.RECOVERY_REQUIRED) } }
    @Synchronized fun unknown() { uncertain=true }
    @Synchronized override fun close() {
        if(closed)return
        try {
            if(!uncertain && !Files.exists(barrier,LinkOption.NOFOLLOW_LINKS)) { Files.delete(active); syncDirectory() }
        } finally { closed=true; lock.release(); channel.close() }
    }
    companion object {
        fun acquire(directory: Path): RuntimeOwner {
            if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS) || directory.toRealPath()!=directory.toAbsolutePath().normalize())fail(StoreProblem.RECOVERY_REQUIRED)
            val permissions=Files.getPosixFilePermissions(directory,LinkOption.NOFOLLOW_LINKS)
            if(permissions.any { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") })fail(StoreProblem.RECOVERY_REQUIRED)
            val c=FileChannel.open(directory.resolve("owner.lock"),CREATE,WRITE,LinkOption.NOFOLLOW_LINKS)
            val l=try { c.tryLock() } catch(_: Exception) { null }
            if(l==null) { c.close(); fail(StoreProblem.RECOVERY_REQUIRED) }
            return RuntimeOwner(directory,c,l)
        }
    }
}
/** No shell; no inherited Git/config/credential environment. All buffers bounded while consuming.
 * On deadline/IO ambiguity children are NOT assumed dead; owner remains poisoned until safe runbook.
 */
internal class NativeGit(private val owner: RuntimeOwner,private val bounds: ProcessBounds=ProcessBounds()) {
    val repository: Path=owner.directory.resolve("data.git")
    private val base=listOf("/usr/bin/git","--no-replace-objects","-c","core.hooksPath=/dev/null","-c","core.fsync=committed,reference","-c","core.fsyncMethod=fsync","-c","gc.auto=0","-c","maintenance.auto=false","-c","protocol.allow=never")
    private val environment=mapOf("PATH" to "/usr/bin:/bin","LANG" to "C","LC_ALL" to "C","HOME" to owner.directory.toString(),"GIT_CONFIG_NOSYSTEM" to "1","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_NO_REPLACE_OBJECTS" to "1","GIT_TERMINAL_PROMPT" to "0","GIT_AUTHOR_NAME" to "Writhelm","GIT_AUTHOR_EMAIL" to "store@invalid","GIT_COMMITTER_NAME" to "Writhelm","GIT_COMMITTER_EMAIL" to "store@invalid","GIT_AUTHOR_DATE" to "2000-01-01T00:00:00Z","GIT_COMMITTER_DATE" to "2000-01-01T00:00:00Z")
    var deadlineNanos: Long=Long.MAX_VALUE
    fun call(args: List<String>,input: ByteArray=byteArrayOf(),inRepository: Boolean=true,outputLimit: Int=bounds.outputBytes): GitReply {
        owner.requireUsable()
        if(input.size>bounds.inputBytes || outputLimit !in 1..bounds.outputBytes)fail(StoreProblem.UNAVAILABLE)
        val now=System.nanoTime(); val duration=minOf(TimeUnit.MILLISECONDS.toNanos(bounds.millis),deadlineNanos-now)
        if(duration<=0)fail(StoreProblem.UNAVAILABLE)
        val argv=base+(if(inRepository)listOf("--git-dir=$repository") else emptyList())+args
        val builder=ProcessBuilder(argv).directory(owner.directory.toFile())
        builder.environment().clear(); builder.environment().putAll(environment)
        val process=try { builder.start() } catch(_: Exception) { fail(StoreProblem.UNAVAILABLE) }
        val executor=Executors.newFixedThreadPool(3) { r -> Thread(r,"bounded-git-io").also { it.isDaemon=true } }
        val overflow=AtomicBoolean(false)
        fun drain(stream: InputStream,max: Int): ByteArray {
            val bytes=ByteArrayOutputStream(minOf(max,8192)); val chunk=ByteArray(8192)
            stream.use { while(true) { val n=it.read(chunk); if(n<0)break
                val accepted=minOf(n,max-bytes.size()); if(accepted>0)bytes.write(chunk,0,accepted)
                if(accepted<n)overflow.set(true)
            } }
            return bytes.toByteArray()
        }
        val end=now+duration
        val out=executor.submit<ByteArray> { drain(process.inputStream,outputLimit) }
        val err=executor.submit<ByteArray> { drain(process.errorStream,bounds.errorBytes) }
        val write=executor.submit { process.outputStream.use { it.write(input) } }
        fun remaining(): Long { val n=end-System.nanoTime(); if(n<=0)throw TimeoutException(); return n }
        try {
            if(!process.waitFor(remaining(),TimeUnit.NANOSECONDS))throw TimeoutException()
            write.get(remaining(),TimeUnit.NANOSECONDS)
            val o=out.get(remaining(),TimeUnit.NANOSECONDS); val e=err.get(remaining(),TimeUnit.NANOSECONDS)
            if(overflow.get())fail(StoreProblem.UNAVAILABLE)
            return GitReply(process.exitValue(),o,e)
        } catch(e: StoreFault) { throw e
        } catch(_: Exception) { owner.unknown(); fail(StoreProblem.RECOVERY_REQUIRED)
        } finally { executor.shutdown() }
    }
    fun qualify() {
        if(System.getProperty("os.name")!="Linux" || Runtime.version().feature()!=21)fail(StoreProblem.RECOVERY_REQUIRED)
        if(!call(listOf("--version"),inRepository=false).checked().contentEquals("git version 2.43.0\n".toByteArray()))fail(StoreProblem.RECOVERY_REQUIRED)
    }
    fun initialize() {
        integrity(!Files.exists(repository,LinkOption.NOFOLLOW_LINKS))
        val template=owner.directory.resolve("empty-template"); Files.createDirectory(template)
        call(listOf("init","--bare","--initial-branch=state","--object-format=sha1","--template=$template",repository.toString()),inRepository=false).checked()
        verifyRepository()
    }
    fun verifyRepository() {
        if(!Files.isDirectory(repository,LinkOption.NOFOLLOW_LINKS))fail(StoreProblem.RECOVERY_REQUIRED)
        // Reject local includes, worktrees, alternates, replacement refs and unknown configuration BEFORE
        // invoking repository-aware Git. Operator modification is not supported concurrency.
        val config=repository.resolve("config")
        if(!Files.isRegularFile(config,LinkOption.NOFOLLOW_LINKS) || Files.size(config)>4096)fail(StoreProblem.RECOVERY_REQUIRED)
        integrity(Files.readString(config)=="[core]\n\trepositoryformatversion = 0\n\tfilemode = true\n\tbare = true\n")
        listOf("objects/info/alternates","objects/info/http-alternates","commondir","shallow","config.worktree","refs/replace","info/grafts").forEach { if(Files.exists(repository.resolve(it),LinkOption.NOFOLLOW_LINKS))fail(StoreProblem.RECOVERY_REQUIRED) }
        var visited=0
        Files.walk(repository).use { paths ->
            paths.forEach { path ->
                if(++visited>131072)fail(StoreProblem.UNAVAILABLE)
                if(Files.isSymbolicLink(path) || path.fileName.toString().endsWith(".lock"))fail(StoreProblem.RECOVERY_REQUIRED)
            }
        }
        integrity(call(listOf("rev-parse","--show-object-format")).checked().contentEquals("sha1\n".toByteArray()))
        integrity(call(listOf("rev-parse","--is-bare-repository")).checked().contentEquals("true\n".toByteArray()))
    }
    fun oid(bytes: ByteArray)=GitOid("sha1",bytes.toString(Charsets.US_ASCII).removeSuffix("\n"))
    fun head(): GitOid {
        val result=call(listOf("rev-parse","--verify","refs/heads/state"))
        if(result.exit!=0 || result.stderr.isNotEmpty())fail(StoreProblem.RECOVERY_REQUIRED)
        return oid(result.output)
    }
    fun blob(bytes: ByteArray)=oid(call(listOf("hash-object","-w","--stdin"),bytes).checked())
    fun readBlob(oid: GitOid,maximum: Int=8*1024*1024): ByteArray {
        val size=call(listOf("cat-file","-s",oid.hex),outputLimit=minOf(128,bounds.outputBytes)).checked().toString(Charsets.US_ASCII).trim().toLongOrNull() ?: fail(StoreProblem.INTEGRITY)
        if(size>minOf(maximum,bounds.outputBytes) || size<0)fail(StoreProblem.UNAVAILABLE)
        return call(listOf("cat-file","blob",oid.hex),outputLimit=maxOf(1,size.toInt())).checked().also { bytes ->
            integrity(bytes.size.toLong()==size)
            val hash=MessageDigest.getInstance("SHA-1")
            hash.update("blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII)); hash.update(bytes)
            integrity(hash.digest().joinToString("") { "%02x".format(it) }==oid.hex)
        }
    }
    fun commit(tree: GitOid,parent: GitOid?): GitOid=oid(call(listOf("commit-tree",tree.hex)+(parent?.let { listOf("-p",it.hex) } ?: emptyList()),"state\n".toByteArray()).checked())
    fun update(old: GitOid?,candidate: GitOid)=call(listOf("update-ref","refs/heads/state",candidate.hex,old?.hex ?: "0".repeat(40)))
}
