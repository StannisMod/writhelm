// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import java.nio.file.*
import java.util.concurrent.*

/** Linux external dependency adapter, not executable attestation or a sandbox. Compatibility
 * observations are disposable, keyed by current filesystem identity, never by an approved hash.
 * Real operations still enforce their own results, bounds and publication uncertainty.
 */
internal object LinuxSystemGit {
    enum class Profile(val tag:String) { STORE("system-git-store-v1"), PROJECT("system-git-project-v1"), WORKTREE("system-git-worktree-v1") }
    data class Observation(val version:String,val profile:String)
    private data class Cached(val stamp:Map<String,Any>,val observation:Observation)
    private val cache=linkedMapOf<Pair<Path,Profile>,Cached>()
    fun defaultExecutable():Path {
        if(System.getProperty("os.name")!="Linux")fail(StoreProblem.GIT_INCOMPATIBLE)
        return Path.of("/usr/bin/git")
    }
    private fun stamp(file:Path):Map<String,Any> = try {
        if(System.getProperty("os.name")!="Linux" || !file.isAbsolute)fail(StoreProblem.GIT_UNAVAILABLE)
        val resolved=file.toRealPath()
        if(!Files.isRegularFile(resolved,LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(resolved))fail(StoreProblem.GIT_UNAVAILABLE)
        val values=Files.readAttributes(resolved,"unix:dev,ino,size,lastModifiedTime,ctime,mode",LinkOption.NOFOLLOW_LINKS)
        if((values.getValue("mode") as Int) and 0x12!=0)fail(StoreProblem.GIT_UNAVAILABLE)
        values+("resolvedExecutable" to resolved.toString())
    } catch(e:StoreFault){throw e} catch(_:Exception){fail(StoreProblem.GIT_UNAVAILABLE)}
    private fun probe(file:Path,args:List<String>,expected:Set<Int> = setOf(0)):String {
        val child=try { ProcessBuilder(listOf(file.toString())+args).directory(file.parent.toFile())
            .redirectErrorStream(true).also { it.environment().clear();it.environment().putAll(mapOf(
                "PATH" to "/usr/bin:/bin","LC_ALL" to "C","GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_CONFIG_GLOBAL" to "/dev/null","GIT_TERMINAL_PROMPT" to "0")) }.start()
        }catch(_:Exception){fail(StoreProblem.GIT_UNAVAILABLE)}
        val io=Executors.newSingleThreadExecutor { r->Thread(r,"git-compatibility-probe").also {it.isDaemon=true} }
        val output=io.submit<ByteArray>{child.inputStream.use {it.readNBytes(262145)}}
        try {
            child.outputStream.close()
            if(!child.waitFor(3,TimeUnit.SECONDS))fail(StoreProblem.GIT_UNAVAILABLE)
            val bytes=output.get(1,TimeUnit.SECONDS)
            if(bytes.size>262144 || child.exitValue() !in expected)fail(StoreProblem.GIT_INCOMPATIBLE)
            return bytes.toString(Charsets.UTF_8)
        }catch(e:StoreFault){throw e}catch(_:Exception){fail(StoreProblem.GIT_UNAVAILABLE)}
        finally { if(child.isAlive)child.destroy();io.shutdownNow() }
    }
    @Synchronized fun observe(file:Path,profile:Profile):Observation {
        val before=stamp(file);val key=file to profile
        cache[key]?.takeIf {it.stamp==before}?.let {return it.observation}
        val version=probe(file,listOf("--version")).trim()
        if(!version.matches(Regex("git version [0-9]+\\.[0-9]+(?:\\.[0-9]+)?[A-Za-z0-9.+ _()-]{0,128}")))fail(StoreProblem.GIT_INCOMPATIBLE)
        val commands=probe(file,listOf("--list-cmds=main")).lineSequence().map {it.trim()}.toSet()
        val required=setOf("init","rev-parse","hash-object","cat-file","mktree","ls-tree","commit-tree","update-ref")+
            if(profile==Profile.WORKTREE)setOf("worktree","merge-tree","merge-base","diff","show-ref","for-each-ref") else emptySet()
        if(!commands.containsAll(required))fail(StoreProblem.GIT_INCOMPATIBLE)
        val init=probe(file,listOf("init","-h"),setOf(0,129))
        if(!listOf("--bare","--initial-branch","--object-format","--template").all {it in init})fail(StoreProblem.GIT_INCOMPATIBLE)
        if(profile==Profile.STORE) {
            val config=probe(file,listOf("help","--config")).lineSequence().toSet()
            if(!config.containsAll(setOf("core.fsync","core.fsyncMethod")))fail(StoreProblem.GIT_INCOMPATIBLE)
        }
        if(profile==Profile.WORKTREE) {
            val help=probe(file,listOf("merge-tree","-h"),setOf(0,129)).replace("--[no-]","--")
            if(!listOf("--write-tree","--merge-base").all {it in help})fail(StoreProblem.GIT_INCOMPATIBLE)
        }
        if(stamp(file)!=before)fail(StoreProblem.GIT_UNAVAILABLE)
        val result=Observation(version,profile.tag)
        if(cache.size>=32)cache.clear()
        cache[key]=Cached(before,result);return result
    }
}
