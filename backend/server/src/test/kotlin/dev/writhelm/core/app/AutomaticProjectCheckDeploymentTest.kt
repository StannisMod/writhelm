// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.service.AutomaticProjectCheckDeployment
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.Test
import kotlin.test.*

class AutomaticProjectCheckDeploymentTest {
    @Test fun enrollmentBindsOnlyApprovedPolicyAndRefusesChangedMissingOrInsecureEvidence() {
        val root=Files.createTempDirectory("writhelm-check-enrollment-")
        val source=Files.createDirectory(root.resolve("source"));val parent=Files.createDirectory(root.resolve("areas"))
        val owner=Files.createDirectory(root.resolve("owner"));val installation=owner.resolve("installation")
        val controller=owner.resolve("controllers/one");val other=owner.resolve("controllers/two")
        val executable=Path.of("/usr/bin/env").toRealPath()
        val pin=obj("path" to str(executable.toString()),"sha256" to digest(Digest.of(Files.readAllBytes(executable))))
        val policy=obj("formatVersion" to num(1),"platform" to str("linux"),"sourceRoot" to str(source.toString()),"envExecutable" to pin,
            "checks" to JArray(listOf(obj("id" to str("approved-test"),"executable" to pin,"args" to JArray(listOf(str("--version"))),
                "environment" to obj(),"timeoutMillis" to num(1000),"outputBytes" to num(1024)))))
        val deployment=AutomaticProjectCheckDeployment(installation)
        deployment.provision(source,controller,parent)
        assertNull(deployment.observe(source,controller,parent))
        deployment.approve(listOf(policy));deployment.provision(source,controller,parent)
        val expected=assertNotNull(deployment.observe(source,controller,parent))
        Files.list(controller.resolve("bootstrap")).use {files->
            assertTrue(files.allMatch {it.fileName.toString().matches(Regex("[a-z][a-z-]{0,31}-[0-9a-f]{64}\\.json"))},
                "Native bootstrap custody must contain only its accepted JSON records, never service command witnesses")
        }
        assertTrue(Files.isDirectory(controller.resolve("project-check-deployment")))
        deployment.provision(source,controller,parent);assertEquals(expected,deployment.observe(source,controller,parent))
        val second=Files.createDirectory(root.resolve("other-areas"))
        deployment.provision(source,other,second)
        assertNotEquals(expected,deployment.observe(source,other,second),"Each controller has its own work area binding")
        val file=Files.list(installation.resolve("project-check-policies")).use {it.findFirst().orElseThrow()}
        val original=Files.readAllBytes(file)
        try{
            Files.writeString(file,String(original).replace("approved-test","changed-test"))
            assertFails {deployment.observe(source,controller,parent)}
            assertFails {deployment.provision(source,controller,parent)}
        }finally{Files.write(file,original)}
        val retained=root.resolve("retained-policy")
        Files.move(file,retained)
        assertFails {deployment.observe(source,controller,parent)}
        assertFails {deployment.provision(source,controller,parent)}
        Files.move(retained,file)
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r--r--"))
        assertFails {deployment.provision(source,controller,parent)}
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"))
        assertEquals(expected,deployment.observe(source,controller,parent))
        val foreign=Files.createDirectory(root.resolve("foreign-source"))
        deployment.provision(foreign,owner.resolve("controllers/foreign"),second)
        assertNull(deployment.observe(foreign,owner.resolve("controllers/foreign"),second))
    }
}
