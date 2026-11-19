// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.directory
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class SystemGitLegacyProfileTest {
    @Test fun oldGitHashesRemainHistoricalAndExistingPassiveBytesAreNeverRewritten(){
        val root=directory()
        fun dir(name:String)=Files.createDirectory(root.resolve(name),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val source=dir("source");val private=dir("private");val controller=dir("controller");val worktrees=dir("worktrees")
        Files.writeString(source.resolve("user.txt"),"Keep this uncommitted user input.")
        val git=Path.of("/usr/bin/git")
        val zero=Digest.parse("0".repeat(64));val other=Digest.parse("1".repeat(64))
        assertEquals(source,LinuxTestClientRepository(git,zero).prepare(source))
        assertTrue(Files.exists(source.resolve("user.txt")))
        assertEquals(source,LinuxTestClientRepository(git,other).prepare(source))
        val project=UUID(0,1);val cid=UUID(0,2)
        AutomaticPassiveDeployment(worktrees,private,git,zero).provision(project,cid,source,controller)
        val file=controller.resolve("bootstrap/passive-profile-${Digest.of("current".toByteArray()).hex}.json")
        val original=Files.readAllBytes(file)
        AutomaticPassiveDeployment(worktrees,private,git,other).provision(project,cid,source,controller)
        assertContentEquals(original,Files.readAllBytes(file))
        val record=StrictJson.decode(original) as JObject
        val wrong=JObject(record.entries+("worktreeParent" to str(source.toString())))
        Files.write(file,StrictJson.encode(wrong))
        assertFails { AutomaticPassiveDeployment(worktrees,private,git,other).provision(project,cid,source,controller) }
    }
}
