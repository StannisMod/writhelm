// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.auth.*
import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.*
import dev.writhelm.core.work.WorkState
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
            val decoded=StrictJson.decode(bytes,JsonLimits(bytes=16384)) as? JObject ?: error("shape")
            val h1=decoded.entries["mode"]?.string() in setOf("initialize-h1","verify-h1")
            val names=listOf("formatVersion","mode","storeDirectory","storeId","projectId","ownerPrincipalId","ownerCredentialId","ownerVerifierDigest","agentPrincipalId","agentCredentialId","agentVerifierDigest")+
                if(h1)listOf("controllerId","workPermissions")else emptyList()
            val f=decoded.fields(*names.toTypedArray())
            version(f.getValue("formatVersion"))
            val store=uuid(f.getValue("storeId"));val project=uuid(f.getValue("projectId"))
            val owner=uuid(f.getValue("ownerPrincipalId"));val agent=uuid(f.getValue("agentPrincipalId"))
            val ownerKey=uuid(f.getValue("ownerCredentialId"));val agentKey=uuid(f.getValue("agentCredentialId"))
            val ownerDigest=digest(f.getValue("ownerVerifierDigest"));val agentDigest=digest(f.getValue("agentVerifierDigest"))
            val directory=Path.of(f.getValue("storeDirectory").string());require(directory.isAbsolute)
            val mode=f.getValue("mode").string()
            when(mode) {
                "initialize","initialize-h1" -> {
                    val policy=AccessPolicy(listOf(Grant(owner,project,Capability.entries.toSet()),Grant(agent,project,setOf(Capability.READ,Capability.PLAN,Capability.PROPOSE))))
                    val authority=AuthorityView.checked(store,listOf(owner,agent),listOf(ProjectBinding(project,owner)),listOf(
                        Credential(ownerKey,store,project,owner,ownerDigest,CredentialStatus.ACTIVE,0),
                        Credential(agentKey,store,project,agent,agentDigest,CredentialStatus.ACTIVE,0)),
                        mapOf(Enrollment(owner,project) to 0L,Enrollment(agent,project) to 0L),policy)
                    // Explicit Linux adapter prerequisite: the frozen store requires an existing
                    // private empty directory. createDirectory refuses ANY existing target.
                    // Validate H1 authority/permissions BEFORE any store-directory creation.
                    val h1Genesis=if(h1) {
                        val controller=uuid(f.getValue("controllerId"))
                        val permissions=(f.getValue("workPermissions") as? JArray)?.values?.map { WorkPermission.valueOf(it.string()) } ?: error("permissions")
                        require(permissions.distinct().size==permissions.size)
                        val records=ArrayList<Auth2Record>()
                        authority.principals.forEach { records.add(Auth2Record.Principal(it)) }
                        authority.projects.values.forEach { records.add(Auth2Record.Project(it)) }
                        authority.guards.forEach { (enrollment,generation) ->
                            records.add(Auth2Record.Guard(enrollment,generation))
                            records.add(Auth2Record.WorkGrants(WorkGrant(enrollment,0,if(enrollment.principalId==agent)permissions else WorkPermission.entries)))
                        }
                        authority.credentials.values.forEach { records.add(Auth2Record.Key(ScopedCredential(it,
                            if(it.principalId==agent)CredentialScope.Controller(controller)else CredentialScope.Project))) }
                        records.add(Auth2Record.Controller(ControllerBinding(controller,project,agent)))
                        H1Genesis(ApprovalWorkflow.start(policy),records,mapOf(project to WorkState.empty(project)))
                    } else null
                    Files.createDirectory(directory,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                    if(h1Genesis!=null)H1Application.initialize(directory,store,h1Genesis,H1Options(UnavailableH1RuntimeFacts)).use { }
                    else MechanismApp.initialize(directory,AppGenesis(ApprovalWorkflow.start(policy),authority)).use { }
                }
                "verify" -> MechanismApp.open(directory,store).use { }
                "verify-h1" -> H1Application.open(directory,store,H1Options(UnavailableH1RuntimeFacts)).use { }
                else -> error("unsupported")
            }
            System.out.write(StrictJson.encode(obj("formatVersion" to num(1),"kind" to str(if(mode in setOf("initialize","initialize-h1"))"initialized" else "verified"),"storeId" to uuid(store))))
        } catch(_: Exception) {
            System.out.write(StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("refused"))))
            kotlin.system.exitProcess(2)
        }
    }
}
