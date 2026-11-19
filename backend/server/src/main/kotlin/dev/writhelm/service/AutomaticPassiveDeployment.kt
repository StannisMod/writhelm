// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** Passive deployment is NOT qualification. Native built-in role selection remains subject to the
 * independent effective-configuration/permission qualification checks before any worker starts.
 * Allocation creates only owned directories; native assignment preparation owns Git-worktree effects. */
internal class AutomaticPassiveDeployment(private val worktrees:Path,private val privateRoot:Path,
    private val git:Path,private val gitDigest:Digest) {
    @Synchronized fun provision(project:UUID,controller:UUID,source:Path,controllerRoot:Path) {
        require(System.getProperty("os.name")=="Linux" && source.isAbsolute && source.toRealPath()==source &&
            git.isAbsolute)
        dev.writhelm.core.store.LinuxSystemGit.observe(git,dev.writhelm.core.store.LinuxSystemGit.Profile.WORKTREE)
        require(Files.isDirectory(source.resolve(".git"),LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(source.resolve(".git")))
        require(listOf(source,privateRoot).none {worktrees.startsWith(it)||it.startsWith(worktrees)})
        LinuxConnectedCustody(worktrees)
        val projectRoot=worktrees.resolve(project.toString());LinuxConnectedCustody(projectRoot)
        val parent=projectRoot.resolve(controller.toString());LinuxConnectedCustody(parent)
        val bootstrap=controllerRoot.resolve("bootstrap");LinuxConnectedCustody(bootstrap)
        // Project registration is stable for a canonical folder; the separate repository namespace
        // never aliases another project's repository or a native/UI identity.
        val repository=UUID.nameUUIDFromBytes("writhelm.automatic.repository.v1\u0000$project".toByteArray(Charsets.UTF_8))
        val bytes=StrictJson.encode(obj("formatVersion" to num(1),"repositoryId" to uuid(repository),
            "worktreeParent" to str(parent.toString()),"git" to obj("path" to str(git.toString()),"sha256" to digest(gitDigest)),
            "agents" to obj("executor-v1" to str("build"),"reviewer-v1" to str("plan"))))
        val file=bootstrap.resolve("passive-profile-${Digest.of("current".toByteArray()).hex}.json")
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
            val old=StrictJson.decode(protectedRuntimeBytes(bootstrap,file,65536)) as JObject
            val next=StrictJson.decode(bytes) as JObject
            // Preserve old bytes/digest as historical evidence. Only the obsolete binary digest
            // may differ; roots, repository, agent choices and executable path must still match.
            val previousGit=old.entries.getValue("git").fields("path","sha256")
            digest(previousGit.getValue("sha256"))
            require(StrictJson.encode(JObject(old.entries+("git" to obj("path" to previousGit.getValue("path"))))).contentEquals(
                StrictJson.encode(JObject(next.entries+("git" to obj("path" to str(git.toString()))))))){
                "Existing passive deployment differs; explicit migration required"
            }
        } else {
            // Original intent is never overwritten/replayed after partial or unknown publication.
            FileChannel.open(file,setOf(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).use {channel ->
                val buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true)
            }
            FileChannel.open(bootstrap,StandardOpenOption.READ).use {it.force(true)}
        }
        require(PassiveRuntimeLayout.read(controllerRoot)?.repository==repository)
    }
}
