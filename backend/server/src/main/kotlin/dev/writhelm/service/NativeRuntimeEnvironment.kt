// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.nio.file.*
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.UUID

internal fun interface RuntimeConfigurationObservation:AutoCloseable {
    fun observe(epoch:String):Digest?
    override fun close() {}
}

/** Same private deployment record consumed by native-runtime.ts. No model-supplied root/agent/pin. */
internal data class PassiveRuntimeLayout(val repository:UUID,val worktreeParent:Path,val git:Path,val gitDigest:Digest,val bytes:ByteArray) {
    companion object {
        fun read(controllerRoot:Path):PassiveRuntimeLayout? {
            val root=controllerRoot.resolve("bootstrap")
            val file=root.resolve("passive-profile-${Digest.of("current".toByteArray()).hex}.json")
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null
            val raw=protectedRuntimeBytes(root,file,65536)
            val f=StrictJson.decode(raw).fields("formatVersion","repositoryId","worktreeParent","git","agents")
            require(f.getValue("formatVersion").long()==1L)
            val git=f.getValue("git").fields("path","sha256")
            val agents=f.getValue("agents").fields("executor-v1","reviewer-v1")
            require(agents.values.all {it.string().isNotBlank()})
            val parent=Path.of(f.getValue("worktreeParent").string());val executable=Path.of(git.getValue("path").string())
            require(parent.isAbsolute && parent.toRealPath()==parent && executable.isAbsolute && executable.toRealPath()==executable &&
                Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent) &&
                Files.getAttribute(parent,"unix:uid")==Files.getAttribute(Path.of("/proc/self"),"unix:uid") &&
                (Files.getAttribute(parent,"unix:mode") as Int) and 0x12==0)
            return PassiveRuntimeLayout(uuid(f.getValue("repositoryId")),parent,executable,digest(git.getValue("sha256")),raw)
        }
    }
}

/** Independent service-side observation of actual authenticated native configuration, deployed plugin
 * bytes, Git pin and storage identity. A catalogue cannot observe itself. This is not evidence that
 * permissions/accounting/compaction/durability BEHAVE correctly: the registered checks supply that.
 * Bodies may contain provider configuration; only their digest leaves this adapter, never raw JSON. */
internal class LinuxNativeRuntimeEnvironment(private val nativeOrigin:String,private val registration:Path,
    private val sourceRoot:Path,private val pluginRoot:Path,private val controllerRoot:Path,
    private val pin:PinnedNativeExecutable,private val privateRoot:Path):RuntimeConfigurationObservation {
    private val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build()
    // Trusted startup selects this installed artifact. Recapture must not silently bless changed
    // plugin bytes in the same process merely because they produce another configuration digest.
    private val deployedPlugin=Digest.of(StrictJson.encode(pluginFiles(),2*1024*1024))
    private fun get(path:String,authorization:String):ByteArray {
        val request=HttpRequest.newBuilder(URI(nativeOrigin+path)).timeout(Duration.ofSeconds(5))
            .header("Authorization",authorization).header("x-opencode-directory",URLEncoder.encode(sourceRoot.toString(),Charsets.UTF_8).replace("+","%20")).GET().build()
        val response=http.send(request,HttpResponse.BodyHandlers.ofInputStream())
        val bytes=response.body().use {it.readNBytes(2*1024*1024+1)}
        require(response.statusCode()==200 && bytes.size<=2*1024*1024)
        return bytes
    }
    private fun hash(file:Path,maximum:Long):Digest {
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && file.toRealPath()==file && Files.size(file) in 1..maximum)
        val before=Files.readAttributes(file,"unix:dev,ino,size,lastModifiedTime,ctime",LinkOption.NOFOLLOW_LINKS)
        val digest=MessageDigest.getInstance("SHA-256");var total=0L
        Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS).use {input ->
            val buffer=ByteArray(65536)
            while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=maximum);digest.update(buffer,0,n)}
        }
        require(before==Files.readAttributes(file,"unix:dev,ino,size,lastModifiedTime,ctime",LinkOption.NOFOLLOW_LINKS))
        return Digest.parse(digest.digest().joinToString(""){"%02x".format(it)})
    }
    private fun pluginFiles():JsonValue {
        require(pluginRoot.isAbsolute && pluginRoot.toRealPath()==pluginRoot)
        val manifest=pluginRoot.resolve("package.json");require(Files.size(manifest)<=65536)
        val pkg=StrictJson.decode(Files.readAllBytes(manifest)) as JObject
        val relative=(pkg.entries.getValue("exports") as JObject).entries.getValue(".").string()
        require(relative.startsWith("./") && ".." !in relative.split('/'))
        val parent=pluginRoot.resolve(relative).normalize().parent
        require(parent.startsWith(pluginRoot) && parent.toRealPath()==parent)
        val files=mutableListOf(manifest);var size=0L
        Files.walk(parent).use {paths ->paths.forEach {file ->
            require(!Files.isSymbolicLink(file))
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && file.fileName.toString().substringAfterLast('.') in setOf("js","cjs","json"))files.add(file)
            require(files.size<=512)
        }}
        return JArray(files.distinct().sortedBy {it.toString()}.map {file ->
            size+=Files.size(file);require(size<=64L*1024*1024)
            obj("path" to str(pluginRoot.relativize(file).toString()),"sha256" to digest(hash(file,4L*1024*1024)))
        })
    }
    override fun observe(epoch:String):Digest? {return try {
        require(System.getProperty("os.name")=="Linux" && sourceRoot.toRealPath()==sourceRoot)
        HttpProfile(nativeOrigin)
        val layout=PassiveRuntimeLayout.read(controllerRoot) ?: return null
        require(hash(layout.git,64L*1024*1024)==layout.gitDigest)
        require(listOf(privateRoot,sourceRoot).none {layout.worktreeParent.startsWith(it)||it.startsWith(layout.worktreeParent)})
        val f=(StrictJson.decode(protectedRuntimeBytes(registration.parent,registration,65536)) as JObject).entries
        require(f.getValue("url").string()==nativeOrigin)
        val authorization="Basic "+Base64.getEncoder().encodeToString(("opencode:"+f.getValue("password").string()).toByteArray())
        val info=(StrictJson.decode(get("/api/info",authorization)) as JObject).entries
        val pid=info.getValue("pid").long();require(info.getValue("version").string()==pin.version)
        require(LinuxNativeProcessObserver.observe(pid,nativeOrigin,epoch,pin)!=null)
        val config=get("/api/config?location%5Bdirectory%5D="+URLEncoder.encode(sourceRoot.toString(),Charsets.UTF_8),authorization)
        val agents=get("/api/agent",authorization)
        val limits=JsonLimits(bytes=2*1024*1024,stringBytes=1024*1024,collection=65536,tokens=200000)
        require(StrictJson.decode(config,limits) is JArray)
        val agentEnvelope=StrictJson.decode(agents,limits) as JObject
        require(agentEnvelope.entries.getValue("data") is JArray)
        // Raw response digests conservatively invalidate on any effective/source ordering change.
        val gitRoot=sourceRoot.resolve(".git");require(Files.isDirectory(gitRoot,LinkOption.NOFOLLOW_LINKS) && gitRoot.toRealPath()==gitRoot)
        val storage=Files.getFileStore(gitRoot);require(!storage.isReadOnly)
        val checks=AutomaticProjectCheckDeployment(privateRoot.parent.resolve("installation")).observe(sourceRoot,controllerRoot,layout.worktreeParent)
        val explicitCheckFile=controllerRoot.resolve("bootstrap/project-check-profile-${Digest.of("current".toByteArray()).hex}.json")
        val explicitChecks=if(Files.exists(explicitCheckFile,LinkOption.NOFOLLOW_LINKS))Digest.of(protectedRuntimeBytes(explicitCheckFile.parent,explicitCheckFile,1024*1024)) else null
        require(checks==null || explicitChecks==null)
        val currentPlugin=pluginFiles();require(Digest.of(StrictJson.encode(currentPlugin,2*1024*1024))==deployedPlugin)
        val observed=obj("formatVersion" to num(1),"configuration" to digest(Digest.of(config)),"agents" to digest(Digest.of(agents)),
            "projectChecks" to ((checks?:explicitChecks)?.let(::digest) ?: JNull),
            "deployment" to digest(Digest.of(layout.bytes)),"plugin" to currentPlugin,"repositoryId" to uuid(layout.repository),
            "git" to digest(layout.gitDigest),"source" to str(sourceRoot.toString()),"sourceIdentity" to str(Files.readAttributes(gitRoot,java.nio.file.attribute.BasicFileAttributes::class.java).fileKey().toString()),
            "worktreeParent" to str(layout.worktreeParent.toString()),"worktreeIdentity" to str(Files.readAttributes(layout.worktreeParent,java.nio.file.attribute.BasicFileAttributes::class.java).fileKey().toString()),
            "storageName" to str(storage.name()),"storageType" to str(storage.type()))
        require(LinuxNativeProcessObserver.observe(pid,nativeOrigin,epoch,pin)!=null)
        Digest.of(StrictJson.encode(observed,2*1024*1024))
    }catch(_:Exception){null}}
    override fun close(){http.close()}
}
