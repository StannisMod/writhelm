// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.auth.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.*
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** Local public-verifier helper only. No network bootstrap/reset, raw secret or recovery bypass. */
object LocalSetup {
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(args.isEmpty())
            require(System.getProperty("os.name").lowercase()=="linux") // frozen native-store adapter
            val bytes=System.`in`.readNBytes(16385);require(bytes.size<=16384)
            val f=StrictJson.decode(bytes,JsonLimits(bytes=16384)).fields("formatVersion","mode","storeDirectory","storeId","projectId","ownerPrincipalId","ownerCredentialId","ownerVerifierDigest","agentPrincipalId","agentCredentialId","agentVerifierDigest")
            version(f.getValue("formatVersion"))
            val store=uuid(f.getValue("storeId"));val project=uuid(f.getValue("projectId"))
            val owner=uuid(f.getValue("ownerPrincipalId"));val agent=uuid(f.getValue("agentPrincipalId"))
            val ownerKey=uuid(f.getValue("ownerCredentialId"));val agentKey=uuid(f.getValue("agentCredentialId"))
            val ownerDigest=digest(f.getValue("ownerVerifierDigest"));val agentDigest=digest(f.getValue("agentVerifierDigest"))
            val directory=Path.of(f.getValue("storeDirectory").string());require(directory.isAbsolute)
            val mode=f.getValue("mode").string()
            when(mode) {
                "initialize" -> {
                    val policy=AccessPolicy(listOf(Grant(owner,project,Capability.entries.toSet()),Grant(agent,project,setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE))))
                    val authority=AuthorityView.checked(store,listOf(owner,agent),listOf(ProjectBinding(project,owner)),listOf(
                        Credential(ownerKey,store,project,owner,ownerDigest,CredentialStatus.ACTIVE,0),
                        Credential(agentKey,store,project,agent,agentDigest,CredentialStatus.ACTIVE,0)),
                        mapOf(Enrollment(owner,project) to 0L,Enrollment(agent,project) to 0L),policy)
                    // Explicit Linux adapter prerequisite: the frozen store requires an existing
                    // private empty directory. createDirectory refuses ANY existing target.
                    Files.createDirectory(directory,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                    MechanismApp.initialize(directory,AppGenesis(ApprovalWorkflow.start(policy),authority)).use { }
                }
                "verify" -> MechanismApp.open(directory,store).use { }
                else -> error("unsupported")
            }
            System.out.write(StrictJson.encode(obj("formatVersion" to num(1),"kind" to str(if(mode=="initialize")"initialized" else "verified"),"storeId" to uuid(store))))
        } catch(_: Exception) {
            System.out.write(StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("refused"))))
            kotlin.system.exitProcess(2)
        }
    }
}
