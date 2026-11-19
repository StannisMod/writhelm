// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.*

@EnabledOnOs(OS.LINUX)
class SystemGitCompatibilityTest {
    @Test fun realSystemGitAdmitsAllRequiredProfilesWithoutAnExactVersionPin() {
        for(profile in LinuxSystemGit.Profile.entries) {
            val observed=LinuxSystemGit.observe(LinuxSystemGit.defaultExecutable(),profile)
            assertTrue(observed.version.startsWith("git version "))
            assertEquals(profile.tag,observed.profile)
        }
    }
    @Test fun changedCompatibleExecutableIsReobservedAndAnIncompatibleReplacementDoesNotUseOldCache() {
        val root=storeTestDirectory("git-capabilities-");val file=root.resolve("git-fixture")
        // Explicit Linux-only synthetic launcher around real Git; not evidence of a second Git version.
        fun wrapper(version:String,bad:Boolean=false) {
            Files.writeString(file,"#!/bin/sh\nif [ \"\$1\" = --version ]; then echo 'git version $version'; exit 0; fi\n"+
                (if(bad)"echo unsupported; exit 1\n" else "exec /usr/bin/git \"\$@\"\n"))
            Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rwx------"))
        }
        wrapper("2.43.0.synthetic")
        assertEquals("git version 2.43.0.synthetic",LinuxSystemGit.observe(file,LinuxSystemGit.Profile.STORE).version)
        wrapper("2.56.1.synthetic-build")
        assertEquals("git version 2.56.1.synthetic-build",LinuxSystemGit.observe(file,LinuxSystemGit.Profile.STORE).version)
        val link=root.resolve("system-git-link");Files.createSymbolicLink(link,file)
        assertEquals("git version 2.56.1.synthetic-build",LinuxSystemGit.observe(link,LinuxSystemGit.Profile.STORE).version)
        wrapper("2.56.1.synthetic-build",true)
        assertEquals(StoreProblem.GIT_INCOMPATIBLE,assertFailsWith<StoreFault>{
            LinuxSystemGit.observe(file,LinuxSystemGit.Profile.STORE)
        }.problem)
        assertEquals(StoreProblem.GIT_INCOMPATIBLE,assertFailsWith<StoreFault>{
            LinuxSystemGit.observe(link,LinuxSystemGit.Profile.STORE)
        }.problem)
        assertEquals(StoreProblem.GIT_UNAVAILABLE,assertFailsWith<StoreFault>{
            LinuxSystemGit.observe(root.resolve("missing"),LinuxSystemGit.Profile.PROJECT)
        }.problem)
    }
}
