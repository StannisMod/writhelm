// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.knowledge.Digest
import dev.writhelm.service.*
import java.nio.file.*
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class AutomaticPassiveDeploymentTest {
    @Test fun passiveAllocationIsStableIsolatedAndNeverCreatesQualificationOrChangesUserFiles() {
        val root=Files.createTempDirectory("writhelm-passive-deployment-")
        val source=root.resolve("project");Files.createDirectory(source);Files.createDirectory(source.resolve(".git"))
        val user=source.resolve("user.txt");Files.writeString(user,"user bytes\n")
        val private=root.resolve("private");LinuxConnectedCustody(private)
        val git=Path.of("/usr/bin/git").toRealPath();val pin=Digest.of(Files.readAllBytes(git))
        val deployment=AutomaticPassiveDeployment(root.resolve("worktrees"),private,git,pin)
        val project=UUID.randomUUID();val one=UUID.randomUUID();val two=UUID.randomUUID()
        val controller=private.resolve(one.toString());LinuxConnectedCustody(controller)
        deployment.provision(project,one,source,controller)
        val original=assertNotNull(PassiveRuntimeLayout.read(controller))
        deployment.provision(project,one,source,controller)
        assertContentEquals(original.bytes,PassiveRuntimeLayout.read(controller)!!.bytes)
        assertTrue(Files.list(original.worktreeParent).use {it.findAny().isEmpty})
        assertFalse(Files.exists(controller.resolve("profile-qualification/registry.json")))
        val other=private.resolve(two.toString());LinuxConnectedCustody(other)
        deployment.provision(project,two,source,other)
        val second=assertNotNull(PassiveRuntimeLayout.read(other))
        assertEquals(original.repository,second.repository);assertNotEquals(original.worktreeParent,second.worktreeParent)
        val third=private.resolve("third");LinuxConnectedCustody(third)
        deployment.provision(UUID.randomUUID(),UUID.randomUUID(),source,third)
        assertNotEquals(original.repository,PassiveRuntimeLayout.read(third)!!.repository)
        assertFails {AutomaticPassiveDeployment(source.resolve("worktrees"),private,git,pin).provision(project,one,source,controller)}
        assertFails {AutomaticPassiveDeployment(root.resolve("worktrees"),private,git,Digest.of(byteArrayOf(1))).provision(project,one,source,controller)}
        val catalogue=Files.list(controller.resolve("bootstrap")).use {it.findFirst().orElseThrow()}
        Files.writeString(catalogue,"unknown original")
        assertFails {deployment.provision(project,one,source,controller)}
        assertEquals("unknown original",Files.readString(catalogue));assertEquals("user bytes\n",Files.readString(user))
    }
}
