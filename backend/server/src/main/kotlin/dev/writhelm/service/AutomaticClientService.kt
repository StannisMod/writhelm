// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.LinuxConnectedCustody
import dev.writhelm.core.codec.*
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.nio.channels.FileChannel
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Protected installation configuration, not a controller/owner credential or model input. */
internal fun automaticClientProfile():LinuxAutomaticClientProfile {
    require(System.getProperty("os.name")=="Linux")
    val file=Path.of(requireNotNull(System.getenv("WRITHELM_TEST_CLIENT_PROFILE"))).toAbsolutePath().normalize()
    LinuxConnectedCustody(file.parent)
    require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && file.toRealPath()==file && Files.size(file)<=2*1024*1024 &&
        (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
        Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(file.parent,"unix:uid") &&
        Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
    val f=StrictJson.decode(Files.readAllBytes(file)).fields("formatVersion","root","plugin","node","cli","git")
    require(f.getValue("formatVersion").long()==1L)
    fun pin(name:String):Pair<Path,dev.writhelm.core.knowledge.Digest>{
        val p=f.getValue(name).fields("path","sha256")
        return Path.of(p.getValue("path").string()) to digest(p.getValue("sha256"))
    }
    val node=pin("node");val cli=pin("cli");val git=pin("git")
    return LinuxAutomaticClientProfile(Path.of(f.getValue("root").string()),Path.of(f.getValue("plugin").string()),
        node.first,node.second,cli.first,cli.second,git.first,git.second,PackagedClientContracts.load())
        .also(LinuxAutomaticTestClient::validateProfile)
}

/** Explicit Linux normal-lifecycle adapter. A view never owns the store lifetime. Unknown stale
 * witnesses remain held: a free PID/lock is NOT abnormal writer fencing or permission to restart. */
internal object LinuxAutomaticClientService {
    private fun marker(profile:LinuxAutomaticClientProfile)=profile.root.resolve("lifecycle/service-owner.json")
    private fun ready(profile:LinuxAutomaticClientProfile)=profile.root.resolve("lifecycle/service-ready.json")
    private fun java()=Path.of(System.getProperty("java.home")).resolve("bin/java").toRealPath()
    private fun metadata(file:Path):ByteArray {
        LinuxConnectedCustody(file.parent)
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && file.toRealPath()==file && Files.size(file)<=4096 &&
            (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
            Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(file.parent,"unix:uid") &&
            Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
        return Files.readAllBytes(file)
    }
    private fun retain(file:Path,bytes:ByteArray){
        FileChannel.open(file,setOf(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).use {channel ->
            val buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true)
        }
        FileChannel.open(file.parent,StandardOpenOption.READ).use {it.force(true)}
    }
    private fun witness(profile:LinuxAutomaticClientProfile,profileFile:Path=Path.of(requireNotNull(System.getenv("WRITHELM_TEST_CLIENT_PROFILE")))):Pair<ProcessHandle,ByteArray>{
        val bytes=metadata(marker(profile))
        val f=StrictJson.decode(bytes).fields("formatVersion","pid","started","profile")
        require(f.getValue("formatVersion").long()==1L && f.getValue("profile").string()==
            profileFile.toRealPath().toString())
        val pid=f.getValue("pid").long();require(pid>0 && pid!=ProcessHandle.current().pid())
        val process=ProcessHandle.of(pid).orElseThrow()
        require(process.isAlive && process.info().startInstant().orElseThrow()==Instant.parse(f.getValue("started").string()))
        require(Path.of("/proc/$pid/exe").toRealPath()==java())
        val args=Files.readAllBytes(Path.of("/proc/$pid/cmdline")).toString(Charsets.UTF_8).split('\u0000')
        require("dev.writhelm.service.AutomaticTestClientService" in args)
        return process to bytes
    }
    private fun readyForViews(profile:LinuxAutomaticClientProfile,profileFile:Path=Path.of(requireNotNull(System.getenv("WRITHELM_TEST_CLIENT_PROFILE")))):Boolean {
        if(!Files.exists(ready(profile),LinkOption.NOFOLLOW_LINKS))return false
        return metadata(ready(profile)).contentEquals(witness(profile,profileFile).second)
    }
    fun ensure(profile:LinuxAutomaticClientProfile){
        val marker=marker(profile)
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))witness(profile)
        else {
            check(!Files.exists(ready(profile),LinkOption.NOFOLLOW_LINKS)){"Unreconciled service readiness witness"}
            check(!LinuxAutomaticTestClient.discover(profile)){"Existing foreground service requires its original owner's normal stop"}
            LinuxConnectedCustody(marker.parent)
            // Pinned Node supplies Linux detached spawn without a shell/setsid executable. This
            // isolates the service from terminal signals; it does NOT claim writer containment.
            val script="""
                import {spawn} from 'node:child_process';
                import {openSync,constants,fstatSync} from 'node:fs';
                const fd=openSync(process.argv[4],constants.O_CREAT|constants.O_APPEND|constants.O_WRONLY|constants.O_NOFOLLOW,0o600);
                const s=fstatSync(fd);if(!s.isFile()||s.uid!==process.getuid()||(s.mode&0o077)||s.nlink!==1)throw Error('Unsafe service log');
                const child=spawn(process.argv[1],['-cp',process.argv[2],'dev.writhelm.service.AutomaticTestClientService'],
                  {detached:true,stdio:['ignore',fd,fd],env:{WRITHELM_TEST_CLIENT_PROFILE:process.argv[3],PATH:'/usr/bin:/bin',LANG:'C.UTF-8'}});
                child.once('error',()=>process.exit(2));child.once('spawn',()=>child.unref());
            """.trimIndent()
            val child=ProcessBuilder(profile.node.toString(),"--input-type=module","-e",script,java().toString(),
                System.getProperty("java.class.path"),Path.of(requireNotNull(System.getenv("WRITHELM_TEST_CLIENT_PROFILE"))).toRealPath().toString(),
                marker.parent.resolve("service.log").toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            child.outputStream.close()
            check(child.waitFor(10,TimeUnit.SECONDS) && child.exitValue()==0){"Background service launch held"}
        }
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90)
        while(System.nanoTime()<deadline){
            try{if(readyForViews(profile)&&LinuxAutomaticTestClient.discover(profile)){witness(profile);return}}
            catch(_:Exception){/* no UI/fallback while authenticated readiness is incomplete */}
            Thread.sleep(100)
        }
        error("Background service readiness held")
    }
    fun stop(profile:LinuxAutomaticClientProfile,
        profileFile:Path=Path.of(requireNotNull(System.getenv("WRITHELM_TEST_CLIENT_PROFILE"))),
        discover:()->Boolean={LinuxAutomaticTestClient.discover(profile)}){
        // Only an authenticated READY service can enter normal stop. Unknown startup/crash state
        // is not repaired by guessing at a PID or deleting a marker/native registration.
        check(readyForViews(profile,profileFile)&&discover()){"Service is not ready for normal stop"}
        val (process,bytes)=witness(profile,profileFile)
        check(Files.readAllBytes(marker(profile)).contentEquals(bytes))
        check(process.destroy()){ "Normal service stop refused" }
        process.onExit().get(30,TimeUnit.SECONDS)
        check(!Files.exists(marker(profile),LinkOption.NOFOLLOW_LINKS) && !Files.exists(ready(profile),LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(profile.root.resolve("state/opencode/service.json"),LinkOption.NOFOLLOW_LINKS)){"Normal service stop remains held"}
    }
    fun run(profile:LinuxAutomaticClientProfile){
        val marker=marker(profile);LinuxConnectedCustody(marker.parent)
        check(!Files.exists(ready(profile),LinkOption.NOFOLLOW_LINKS)){"Unreconciled readiness witness"}
        val self=ProcessHandle.current()
        val bytes=StrictJson.encode(obj("formatVersion" to num(1),"pid" to JNumber(self.pid()),
            "started" to str(self.info().startInstant().orElseThrow().toString()),
            "profile" to str(Path.of(requireNotNull(System.getenv("WRITHELM_TEST_CLIENT_PROFILE"))).toRealPath().toString())))
        retain(marker,bytes)
        // Startup failure deliberately preserves the original witness for explicit reconciliation.
        val client=LinuxAutomaticTestClient.start(profile)
        Runtime.getRuntime().addShutdownHook(Thread({
            try{
                client.close()
                check(Files.readAllBytes(marker).contentEquals(bytes))
                if(Files.exists(ready(profile),LinkOption.NOFOLLOW_LINKS)){
                    check(metadata(ready(profile)).contentEquals(bytes));Files.delete(ready(profile))
                }
                Files.delete(marker) // only this process's exact known normal-lifetime witness
                FileChannel.open(marker.parent,StandardOpenOption.READ).use {it.force(true)}
            }catch(_:Exception){System.err.println("Writhelm service shutdown held; original ownership witness retained")}
        },"writhelm-background-service-stop"))
        // Publish normal readiness only AFTER the owned-runtime shutdown hook is installed.
        retain(ready(profile),bytes)
        CountDownLatch(1).await()
    }
}

/** Trusted detached lifetime owner; not a planner/model entrypoint. */
object AutomaticTestClientService {
    @JvmStatic fun main(args:Array<String>){
        try{require(args.isEmpty());LinuxAutomaticClientService.run(automaticClientProfile())}
        catch(e:Exception){
            val code=(e as? dev.writhelm.core.store.StoreFault)?.problem?.name?.takeIf {it.startsWith("GIT_")}
            System.err.println("Writhelm background service held${code?.let {": $it"}?:""}; no store reset or unmanaged fallback")
            kotlin.system.exitProcess(2)
        }
    }
}
