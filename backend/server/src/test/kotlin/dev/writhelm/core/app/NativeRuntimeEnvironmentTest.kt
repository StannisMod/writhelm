// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import com.sun.net.httpserver.HttpServer
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.service.LinuxNativeRuntimeEnvironment
import java.net.InetSocketAddress
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Actual HTTP/filesystem/process observation, synthetic native API responses, no model calls. */
class NativeRuntimeEnvironmentTest {
    @Test fun observesChangesAndRejectsStaleProcessOrCredentialsWithoutTouchingProjectBytes() {
        val root=Files.createTempDirectory("writhelm-observer-")
        fun directory(name:String)=Files.createDirectories(root.resolve(name)).also {
            Files.setPosixFilePermissions(it,PosixFilePermissions.fromString("rwx------"))
        }
        fun write(file:Path,bytes:ByteArray) {Files.write(file,bytes);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"))}
        val source=directory("source");directory("source/.git")
        val user=source.resolve("user.txt");val userBytes="untouched user work\n".toByteArray();write(user,userBytes)
        val private=directory("private");val controller=directory("private/controller");val bootstrap=directory("private/controller/bootstrap")
        val worktrees=directory("worktrees");val plugin=directory("plugin");directory("plugin/build")
        write(plugin.resolve("package.json"),"""{"exports":{".":"./build/main.js"}}""".toByteArray())
        val module=plugin.resolve("build/main.js");write(module,"export default 1".toByteArray())
        val executable=Path.of("/proc/self/exe").toRealPath();val binary=Digest.of(Files.readAllBytes(executable))
        write(bootstrap.resolve("passive-profile-${Digest.of("current".toByteArray()).hex}.json"),StrictJson.encode(obj(
            "formatVersion" to num(1),"repositoryId" to uuid(UUID.randomUUID()),"worktreeParent" to str(worktrees.toString()),
            "git" to obj("path" to str(executable.toString()),"sha256" to digest(binary)),
            "agents" to obj("executor-v1" to str("synthetic executor"),"reviewer-v1" to str("synthetic reviewer")))))
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var config="[]";var agents="{\"data\":[]}";var status=200;var requests=0
        server.createContext("/api/") { exchange ->
            requests++
            val authorized=exchange.requestHeaders.getFirst("Authorization")=="Basic b3BlbmNvZGU6c3ludGhldGlj"
            val body=when(exchange.requestURI.path) {
                "/api/info"->"""{"pid":${ProcessHandle.current().pid()},"version":"synthetic-jvm"}"""
                "/api/config"->config
                "/api/agent"->agents
                else->error("Unexpected observer endpoint")
            }.toByteArray()
            exchange.sendResponseHeaders(if(authorized)status else 401,body.size.toLong())
            exchange.responseBody.use {it.write(body)}
        }
        server.start()
        try {
            val origin="http://127.0.0.1:${server.address.port}"
            val registration=private.resolve("service.json")
            write(registration,StrictJson.encode(obj("url" to str(origin),"password" to str("synthetic"))))
            val stat=Files.readString(Path.of("/proc/self/stat"));val start=stat.substring(stat.lastIndexOf(')')+1).trim().split(Regex("\\s+"))[19]
            val boot=Files.readString(Path.of("/proc/sys/kernel/random/boot_id")).trim()
            val epoch=Digest.of("[\"native-linux-process-v1\",\"${binary.hex}\",\"$boot\",${ProcessHandle.current().pid()},\"$start\",\"$origin\"]".toByteArray()).hex
            LinuxNativeRuntimeEnvironment(origin,registration,source,plugin,controller,PinnedNativeExecutable("synthetic-jvm",binary),private).use { observer ->
                val original=assertNotNull(observer.observe(epoch));assertEquals(original,observer.observe(epoch))
                config="[{\"type\":\"document\",\"info\":{\"model\":\"changed\"}}]"
                assertNotEquals(original,assertNotNull(observer.observe(epoch)))
                config="[]";agents="{\"data\":[{\"id\":\"changed\"}]}"
                assertNotEquals(original,assertNotNull(observer.observe(epoch)))
                agents="{\"data\":[]}";write(module,"export default 2".toByteArray())
                assertNull(observer.observe(epoch),"Changed deployed plugin cannot self-authorize through a new digest")
                write(module,"export default 1".toByteArray())
                assertEquals(original,observer.observe(epoch))
                assertNull(observer.observe("0".repeat(64)))
                config="not JSON";assertNull(observer.observe(epoch));config="[]"
                agents="{}";assertNull(observer.observe(epoch));agents="{\"data\":[]}"
                status=503;assertNull(observer.observe(epoch));status=200
                write(registration,StrictJson.encode(obj("url" to str(origin),"password" to str("wrong"))))
                assertNull(observer.observe(epoch));assertTrue(requests>0)
            }
            assertContentEquals(userBytes,Files.readAllBytes(user))
        } finally {server.stop(0)}
    }
}
