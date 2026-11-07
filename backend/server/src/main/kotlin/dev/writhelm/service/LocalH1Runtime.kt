// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.auth.CredentialVerifier
import dev.writhelm.core.auth2.CredentialScope
import dev.writhelm.core.codec.*
import dev.writhelm.core.work.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import dev.writhelm.core.knowledge.Digest

/** Explicit local component composition, not a K-RUN launcher/recovery qualification. The owner
 * GUI remains separate. The only retained key used here is a bounded controller credential supplied
 * by trusted client storage, never an owner/project key, model option, route or command-line field. */
internal class LocalH1Runtime private constructor(val app: H1Application,private val http: HttpAdapter,
    private val endpoint: NativeRuntimeEndpoint,private val server: EmbeddedServer<*,*>,
    private val native: HttpConnectedNativeControl,private val root: Path,private val project: UUID,
    private val controller: UUID,private val host: UUID,private val token: String,
    private val preparation: LinuxOwnedDirectoryPreparation,private val checks: OwnedCheckCustody,
    private val installFacts: (ConnectedRuntimeFacts)->Unit): AutoCloseable {
    private var engine: ConnectedWorkEngine?=null
    private var closed=false
    /** Run after the explicitly configured in-process native peer is listening. No worker is
     * launched here; original commands/unknown effects are recovered by a fresh canonical scan. */
    @Synchronized fun connectNative() {
        check(!closed && engine==null)
        val access=app.inspectAccess(token)
        require(access.scope==CredentialScope.Controller(controller) && access.projectId==project)
        app.coordinationCapture(token,project) // actual current READ + TASK_DISPATCH, not role labels
        val identity=native.identity()
        val credential=CredentialVerifier.parse(token)?.use { it.credentialId } ?: error("bounded credential required")
        require(uuid(identity.getValue("hostId"))==host && uuid(identity.getValue("controllerId"))==controller &&
            uuid(identity.getValue("principalId"))==access.principalId && uuid(identity.getValue("credentialId"))==credential)
        val session=identity.getValue("sessionID").string();require(session.matches(Regex("ses_[A-Za-z0-9_-]{1,128}")))
        val epoch=identity.getValue("epoch").string();require(epoch.matches(Regex("[0-9a-f]{64}")))
        val areas=(identity.getValue("workRoots") as? JArray)?.values ?: error("actual native areas required")
        require(areas.isNotEmpty() && areas.size<=257)
        areas.forEach { outside(root,Path.of(it.string()).toRealPath()) }
        val execution=UUID.nameUUIDFromBytes("writhelm-native-controller:$controller:$host:$session:$epoch".toByteArray(Charsets.UTF_8))
        installFacts(ConnectedRuntimeFacts(UnavailableH1RuntimeFacts,preparation,native,
            Model2ControllerExecution(controller,execution,host,session),checks::observed))
        engine=ConnectedWorkEngine(app,project,{token},preparation,native,LinuxConnectedCustody(root.resolve("engine")))
    }
    @Synchronized fun holds()=engine?.holds.orEmpty()
    @Synchronized override fun close() {
        if(closed)return
        closed=true
        engine?.close()
        server.stop(0,5000)
        runBlocking { http.mcp.close() }
        endpoint.close();native.close();app.close()
    }
    companion object {
        private fun outside(root: Path,area: Path) {
            require(!root.startsWith(area) && !area.startsWith(root)) { "runtime custody overlaps managed data/work area" }
        }
        /** Linux-only protected operational root. Existing insecure ancestry is refused BEFORE
         * creating children, never repaired by chmod. Other platforms have no fallback adapter. */
        private fun directory(path: Path): Path {
            require(System.getProperty("os.name")=="Linux" && path.isAbsolute && path==path.normalize())
            val uid=Files.getAttribute(Path.of("/proc/self"),"unix:uid") as Int
            var current=path.root
            path.forEach { component ->
                current=current.resolve(component)
                if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(current,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                require(Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(current))
                val owner=Files.getAttribute(current,"unix:uid",LinkOption.NOFOLLOW_LINKS) as Int
                val mode=Files.getAttribute(current,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int
                require(owner in setOf(0,uid) && (mode and 0x12==0 || owner==0 && mode and 0x200!=0))
            }
            val mode=Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int
            require(Files.getAttribute(path,"unix:uid",LinkOption.NOFOLLOW_LINKS)==uid && mode and 0x3f==0 && path.toRealPath()==path)
            return path
        }
        private fun secretRecord(root: Path,kind: String,key: String): Map<String,JsonValue> {
            require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
            directory(root)
            val path=root.resolve("$kind-${Digest.of(key.toByteArray(Charsets.UTF_8)).hex}.json")
            require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && Files.size(path)<=4096)
            require(Files.getAttribute(path,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(root,"unix:uid") &&
                Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1 &&
                (Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0)
            val before=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
            val bytes=Files.newByteChannel(path,setOf(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)).use { channel ->
                java.nio.channels.Channels.newInputStream(channel).readNBytes(4097)
            }
            val after=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
            require(bytes.size<=4096 && bytes.size.toLong()==before.size() && before.fileKey()==after.fileKey() &&
                before.size()==after.size() && before.lastModifiedTime()==after.lastModifiedTime())
            return (StrictJson.decode(bytes,JsonLimits(bytes=4096)) as? JObject)?.entries ?: error("protected record unavailable")
        }
        /** Read only exact Node-owned scoped-key/peer records from qualified private custody.
         * No raw key/proof in CLI arguments, configuration exports, canonical Git or work tools. */
        fun openFromNativeCustody(storeDirectory: Path,storeId: UUID,project: UUID,controller: UUID,host: UUID,origin: String,
            nativeOrigin: String,nativeCustodyRoot: Path,credentialId: UUID,operationalRoot: Path,managedWorkAreas: Collection<Path>,
            registrations: List<OwnedPreparationRegistration> = emptyList()): LocalH1Runtime {
            require(managedWorkAreas.isNotEmpty())
            managedWorkAreas.forEach { outside(nativeCustodyRoot,it.toRealPath()) }
            outside(nativeCustodyRoot,storeDirectory.toRealPath())
            val key=JObject(secretRecord(nativeCustodyRoot,"credential",credentialId.toString())).fields(
                "formatVersion","kind","storeId","projectId","principalId","credentialId","token")
            require(key.getValue("formatVersion").long()==1L && key.getValue("kind").string()=="native-scoped-key-v1" &&
                uuid(key.getValue("storeId"))==storeId && uuid(key.getValue("projectId"))==project && uuid(key.getValue("credentialId"))==credentialId)
            val token=key.getValue("token").string()
            (CredentialVerifier.parse(token) ?: error("protected credential unavailable")).use {
                require(it.storeId==storeId && it.credentialId==credentialId)
            }
            fun proof(name: String): ByteArray {
                val fields=JObject(secretRecord(nativeCustodyRoot,"native-peer",name)).fields("formatVersion","kind","secret")
                require(fields.getValue("formatVersion").long()==1L && fields.getValue("kind").string()=="native-peer-proof-v1")
                val text=fields.getValue("secret").string();require(text.matches(Regex("[A-Za-z0-9_-]{43}")))
                return java.util.Base64.getUrlDecoder().decode(text).also { require(it.size==32 && java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it)==text) }
            }
            val serviceProof=proof("service")
            try {
                val nativeProof=proof("transport")
                try { return open(storeDirectory,storeId,project,controller,host,origin,nativeOrigin,serviceProof,nativeProof,
                    token,operationalRoot,managedWorkAreas,registrations,uuid(key.getValue("principalId"))) }
                finally { nativeProof.fill(0) }
            } finally { serviceProof.fill(0) }
        }
        fun open(storeDirectory: Path,storeId: UUID,project: UUID,controller: UUID,host: UUID,origin: String,
            nativeOrigin: String,serviceProof: ByteArray,nativeProof: ByteArray,controllerToken: String,
            operationalRoot: Path,managedWorkAreas: Collection<Path>,registrations: List<OwnedPreparationRegistration> = emptyList(),
            expectedPrincipal: UUID?=null): LocalH1Runtime {
            val profile=HttpProfile(origin);HttpProfile(nativeOrigin)
            require(managedWorkAreas.isNotEmpty())
            managedWorkAreas.forEach { outside(operationalRoot.toAbsolutePath().normalize(),it.toRealPath()) }
            val root=directory(operationalRoot);outside(root,storeDirectory.toRealPath())
            val facts=java.util.concurrent.atomic.AtomicReference<ConnectedRuntimeFacts?>()
            val app=H1Application.open(storeDirectory,storeId,H1Options(H1RuntimeFacts { state,command ->
                facts.get()?.capture(state,command) ?: UnavailableH1RuntimeFacts.capture(state,command)
            }))
            var native: HttpConnectedNativeControl?=null
            var endpoint: NativeRuntimeEndpoint?=null
            var http: HttpAdapter?=null
            try {
                val access=app.inspectAccess(controllerToken)
                require(access.scope==CredentialScope.Controller(controller) && access.projectId==project &&
                    (expectedPrincipal==null || access.principalId==expectedPrincipal))
                native=HttpConnectedNativeControl(nativeOrigin,nativeProof)
                endpoint=WrithelmService(app).nativeEndpoint(project,controller,host,serviceProof)
                http=HttpAdapter(WrithelmService(app),profile,nativeEndpoint=endpoint)
                registrations.forEach { outside(root,it.root.toRealPath()) }
                val preparation=LinuxOwnedDirectoryPreparation(registrations)
                val checks=OwnedCheckCustody(directory(root.resolve("reports")),emptyMap())
                val installed=http
                val uri=java.net.URI(origin)
                val server=embeddedServer(CIO,host=uri.host.removeSurrounding("[","]"),port=uri.port) { installed.install(this) }.start(wait=false)
                return LocalH1Runtime(app,http,endpoint,server,native,root,project,controller,host,controllerToken,
                    preparation,checks) { facts.set(it) }
            } catch(error: Exception) {
                http?.let { runBlocking { it.mcp.close() } };endpoint?.close();native?.close();app.close();throw error
            }
        }
    }
}
