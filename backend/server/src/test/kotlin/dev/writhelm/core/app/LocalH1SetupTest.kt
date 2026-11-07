// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.*
import dev.writhelm.core.codec.*
import dev.writhelm.service.LocalSetup
import dev.writhelm.service.LocalH1Runtime
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.Test
import kotlin.test.*

/** Real local helper/Git setup with public deterministic verifier vectors, never live keys. */
class LocalH1SetupTest {
    private fun run(input: JObject,root: Path): Pair<Int,JObject> {
        val executable=Path.of(System.getProperty("java.home"),"bin","java")
        val classes=listOf(LocalSetup::class.java,kotlin.Unit::class.java,com.fasterxml.jackson.core.JsonFactory::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
        val process=ProcessBuilder(executable.toString(),"-Xmx128m","-XX:ActiveProcessorCount=2","-cp",classes.joinToString(java.io.File.pathSeparator),LocalSetup::class.java.name)
            .redirectError(root.resolve("setup-${System.nanoTime()}.stderr").toFile()).start()
        process.outputStream.use { it.write(StrictJson.encode(input,16384)) }
        val result=process.inputStream.readNBytes(4097)
        assertTrue(process.waitFor(30,TimeUnit.SECONDS))
        return process.exitValue() to (StrictJson.decode(result) as JObject)
    }
    private fun input(directory: Path,permissions: List<String>,mode: String="initialize-h1")=obj(
        "formatVersion" to num(1),"mode" to str(mode),"storeDirectory" to str(directory.toString()),"storeId" to uuid(storeId),"projectId" to uuid(project),
        "ownerPrincipalId" to uuid(owner),"ownerCredentialId" to uuid(ownerKey),"ownerVerifierDigest" to digest(credential(ownerKey,owner).verifierDigest),
        "agentPrincipalId" to uuid(planner),"agentCredentialId" to uuid(plannerKey),"agentVerifierDigest" to digest(credential(plannerKey,planner).verifierDigest),
        "controllerId" to uuid(id(60)),"workPermissions" to JArray(permissions.map(::str)))
    @Test fun freshH1SetupHasExplicitControllerPermissionsAndCannotResetAnExistingStore() {
        val root=directory();val store=root.resolve("new-store")
        val request=input(store,WorkPermission.entries.map { it.name })
        assertEquals(0,run(request,root).first)
        H1Application.open(store,storeId,H1Options(UnavailableH1RuntimeFacts)).use { app ->
            assertEquals(CredentialScope.Controller(id(60)),app.inspectAccess(plannerToken).scope)
            assertEquals(CredentialScope.Project,app.inspectAccess(ownerToken).scope)
            assertTrue(app.coordinationCapture(plannerToken,project).work.records.isEmpty())
        }
        assertEquals(2,run(request,root).first)
        assertEquals(0,run(input(store,emptyList(),"verify-h1"),root).first)
        val protectedRoot=Files.createTempDirectory("writhelm-h1-runtime-test-")
        fun port()=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:${port()}";val nativeOrigin="http://127.0.0.1:${port()}"
        val proof=ByteArray(32) { 8 }
        val runtime=LocalH1Runtime.open(store,storeId,project,id(60),id(61),origin,nativeOrigin,proof,proof,plannerToken,
            protectedRoot.resolve("runtime"),listOf(root))
        runtime.use { opened ->
            assertEquals(CredentialScope.Controller(id(60)),opened.app.inspectAccess(plannerToken).scope)
            assertFails { opened.connectNative() } // no permissive absent-peer startup
            assertTrue(opened.holds().isEmpty())
        }
        assertFailsWith<IllegalStateException> { runtime.connectNative() }
        runtime.close()
        assertFails { LocalH1Runtime.open(store,storeId,project,id(60),id(61),origin,nativeOrigin,proof,proof,ownerToken,
            protectedRoot.resolve("wrong-key"),listOf(root)) }
        val nativeSecrets=Files.createDirectory(protectedRoot.resolve("native"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        fun retained(kind: String,key: String,value: JsonValue): Path {
            val file=nativeSecrets.resolve("$kind-${dev.writhelm.core.knowledge.Digest.of(key.toByteArray()).hex}.json")
            Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            Files.write(file,StrictJson.encode(value))
            return file
        }
        val scoped=obj("formatVersion" to num(1),"kind" to str("native-scoped-key-v1"),"storeId" to uuid(storeId),"projectId" to uuid(project),
            "principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken))
        val retainedKey=retained("credential",plannerKey.toString(),scoped)
        listOf("service","transport").forEach { retained("native-peer",it,obj("formatVersion" to num(1),"kind" to str("native-peer-proof-v1"),
            "secret" to str(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proof)))) }
        LocalH1Runtime.openFromNativeCustody(store,storeId,project,id(60),id(61),origin,nativeOrigin,nativeSecrets,plannerKey,
            protectedRoot.resolve("from-native"),listOf(root)).use { runtime ->
            assertEquals(CredentialScope.Controller(id(60)),runtime.app.inspectAccess(plannerToken).scope)
        }
        Files.write(retainedKey,StrictJson.encode(JObject(scoped.entries+("principalId" to uuid(id(49999))))))
        assertFails { LocalH1Runtime.openFromNativeCustody(store,storeId,project,id(60),id(61),origin,nativeOrigin,nativeSecrets,plannerKey,
            protectedRoot.resolve("mismatched-native"),listOf(root)) }
        val restricted=root.resolve("plan-only")
        assertEquals(0,run(input(restricted,listOf("TASK_PLAN")),root).first)
        H1Application.open(restricted,storeId,H1Options(UnavailableH1RuntimeFacts)).use { app ->
            assertEquals(CredentialScope.Controller(id(60)),app.inspectAccess(plannerToken).scope)
            assertFails { app.coordinationCapture(plannerToken,project) } // no promotion of PLAN into dispatch
        }
        val malformed=root.resolve("invalid-permissions")
        assertEquals(2,run(input(malformed,listOf("TASK_PLAN","TASK_PLAN")),root).first)
        assertFalse(Files.exists(malformed))
    }
}
