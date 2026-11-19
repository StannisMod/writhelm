// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import java.nio.file.*
import java.io.File
import java.util.concurrent.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.*

/** Known disposable child; native discovery is explicitly mocked. Process identity, normal signal,
 * readiness/ownership checks and its own shutdown cleanup are real. No live stand/crash experiment. */
object SystemGitStopChild {
    @JvmStatic fun main(args:Array<String>){
        val root=Path.of(args[1]);val profile=Path.of(args[2])
        val self=ProcessHandle.current()
        val raw=StrictJson.encode(obj("formatVersion" to num(1),"pid" to JNumber(self.pid()),
            "started" to str(self.info().startInstant().orElseThrow().toString()),"profile" to str(profile.toRealPath().toString())))
        val owner=root.resolve("lifecycle/service-owner.json");val ready=root.resolve("lifecycle/service-ready.json")
        Files.write(owner,raw);Files.write(ready,raw)
        for(file in listOf(owner,ready))Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        Runtime.getRuntime().addShutdownHook(Thread({Files.delete(ready);Files.delete(owner)}))
        println("ready");System.out.flush();CountDownLatch(1).await()
    }
}
@EnabledOnOs(OS.LINUX)
@Timeout(40)
class SystemGitLifecycleTest {
    @Test fun normalOwnedStopDoesNotDependOnMissingOrIncompatibleGitAndStillRefusesUnknownOwnership(){
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_APP_TEST_ROOT"))),"git-stop-")
        Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
        Files.createDirectory(root.resolve("lifecycle"),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")))
        val descriptor=root.resolve("profile.command");Files.writeString(descriptor,"synthetic identity")
        val java=Path.of(System.getProperty("java.home")).resolve("bin/java")
        val classpath=listOf(SystemGitStopChild::class.java,StrictJson::class.java,kotlin.Unit::class.java,
            com.fasterxml.jackson.core.JsonFactory::class.java).map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
            .distinct().joinToString(File.pathSeparator)
        for(git in listOf(root.resolve("missing-git"),Path.of("/bin/false"))){
            val child=ProcessBuilder(java.toString(),"-cp",classpath,
                SystemGitStopChild::class.java.name,"dev.writhelm.service.AutomaticTestClientService",root.toString(),descriptor.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start()
            try {
                assertEquals("ready",child.inputStream.bufferedReader().readLine())
                val d=Digest.parse("0".repeat(64))
                val profile=LinuxAutomaticClientProfile(root,root,java,d,java,d,git,d,emptyMap())
                assertFails { LinuxAutomaticClientService.stop(profile,descriptor){false} }
                assertTrue(child.isAlive)
                LinuxAutomaticClientService.stop(profile,descriptor){true}
                assertTrue(child.waitFor(5,TimeUnit.SECONDS))
                assertFalse(Files.exists(root.resolve("lifecycle/service-owner.json")))
                assertFails { LinuxAutomaticClientService.stop(profile,descriptor){true} }
            }finally{if(child.isAlive){child.destroy();child.waitFor(5,TimeUnit.SECONDS)}}
        }
    }
}
