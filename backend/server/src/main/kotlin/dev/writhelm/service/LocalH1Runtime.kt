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
import dev.writhelm.core.store.CommandResult
import java.nio.channels.FileChannel
import java.security.SecureRandom

internal data class LinuxAutomaticClientProfile(val root:Path,val plugin:Path,val node:Path,val nodeDigest:Digest,
    val cli:Path,val cliDigest:Digest,val git:Path,val gitDigest:Digest,val schemas:Map<String,String>)

internal object PackagedClientContracts {
    fun load():Map<String,String> {
        val bytes=checkNotNull(javaClass.getResourceAsStream("/writhelm/client-contracts.json")){"Packaged client contracts missing"}
            .use {it.readNBytes(1536*1024+1)}
        require(bytes.size<=1536*1024)
        val entries=(StrictJson.decode(bytes,JsonLimits(bytes=2*1024*1024,stringBytes=512*1024,tokens=100000)) as JObject).entries
        require("work-command3" in entries && "knowledge-command1" in entries && entries.size<=16)
        return entries.mapValues { (namespace,value) ->
            val raw=value.string();require(raw.toByteArray(Charsets.UTF_8).size<=512*1024)
            val contract=StrictJson.decode(raw.toByteArray(Charsets.UTF_8),JsonLimits(bytes=512*1024,stringBytes=262144,tokens=100000))
                .fields("formatVersion","kind","namespace","notice","files")
            require(contract.getValue("formatVersion").long()==1L && contract.getValue("kind").string()=="canonical-codec-source-v1" &&
                contract.getValue("namespace").string()==namespace)
            val files=(contract.getValue("files") as JArray).values;require(files.isNotEmpty() && files.size<=32)
            files.forEach {file ->val f=file.fields("path","sha256","source")
                require(!f.getValue("path").string().startsWith("/") && ".." !in f.getValue("path").string().split('/'))
                require(Digest.of(f.getValue("source").string().toByteArray(Charsets.UTF_8))==digest(f.getValue("sha256")))
            }
            raw
        }
    }
}

/** One-time trusted package installer. It emits paths/pins and a wrapper, never per-project IDs. */
object AutomaticTestClientInstall {
    @JvmStatic fun main(args:Array<String>){
        try {
            require(args.size in 6..7 && System.getProperty("os.name")=="Linux")
            val paths=args.take(6).map {Path.of(it).toAbsolutePath().normalize()}
            fun hash(file:Path):Digest {val md=java.security.MessageDigest.getInstance("SHA-256")
                Files.newInputStream(file).use {input ->val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;md.update(bytes,0,n)}}
                return Digest.parse(md.digest().joinToString(""){"%02x".format(it)})}
            val profile=LinuxAutomaticClientProfile(paths[0],paths[1],paths[2],hash(paths[2]),paths[3],hash(paths[3]),paths[4],hash(paths[4]),PackagedClientContracts.load())
            var deployments:List<JsonValue>?=null
            val policies=if(args.size==7){val file=Path.of(args[6]).toRealPath()
                require(Files.size(file)<=1024*1024)
                val value=StrictJson.decode(Files.readAllBytes(file),JsonLimits(bytes=1024*1024,stringBytes=262144,tokens=100000))
                val extended=(value as? JObject)?.entries?.containsKey("executionDeployments")==true
                val f=if(extended)value.fields("formatVersion","policies","executionDeployments")else value.fields("formatVersion","policies")
                if(extended)deployments=f.getValue("executionDeployments").list()
                require(f.getValue("formatVersion").long()==1L);f.getValue("policies").list()}else emptyList()
            println(install(profile,paths[5],policies,deployments))
        }catch(_:Exception){System.err.println("Writhelm test-client installation held; existing installation was not reset");kotlin.system.exitProcess(2)}
    }
    internal fun install(profile:LinuxAutomaticClientProfile,launcher:Path,approvedProjectChecks:List<JsonValue> = emptyList(),
        executionDeployments:List<JsonValue>? = null):Path {
        require(Runtime.version().feature()==21){"This test-client launcher requires the tested JDK21 profile"}
        LinuxAutomaticTestClient.validateProfile(profile)
        require(launcher.isAbsolute && launcher.toRealPath()==launcher && Files.isRegularFile(launcher) && Files.isExecutable(launcher))
        require(Files.size(launcher)<=65536 && Files.readString(launcher).contains("dev.writhelm.service.AutomaticTestClient")){
            "Installer requires the generated Writhelm application launcher, not a bare native client"
        }
        require(profile.schemas==PackagedClientContracts.load()){ "Installer accepts only packaged canonical contracts" }
        if(approvedProjectChecks.isNotEmpty())AutomaticProjectCheckDeployment(profile.root.resolve("installation")).approve(approvedProjectChecks)
        val directory=profile.root.resolve("installation");val custody=LinuxConnectedCustody(directory)
        if(executionDeployments!=null){
            require(executionDeployments.size<=32)
            executionDeployments.forEach {it.fields("profileRecordJSON","executionJSON","contextPolicyJSON").values.forEach {value ->
                require(value.string().toByteArray(Charsets.UTF_8).size<=262144)
            }}
            custody.retain(UUID.nameUUIDFromBytes("writhelm.test-client.execution-deployments.v1".toByteArray(Charsets.UTF_8)),
                "command",StrictJson.encode(obj("formatVersion" to num(1),"deployments" to JArray(executionDeployments))))
        }
        fun pin(path:Path,checksum:Digest)=obj("path" to str(path.toString()),"sha256" to digest(checksum))
        val id=UUID.nameUUIDFromBytes("writhelm.test-client.install-profile.v1".toByteArray())
        val bytes=StrictJson.encode(obj("formatVersion" to num(1),"root" to str(profile.root.toString()),"plugin" to str(profile.plugin.toString()),
            "node" to pin(profile.node,profile.nodeDigest),"cli" to pin(profile.cli,profile.cliDigest),"git" to pin(profile.git,profile.gitDigest)))
        custody.retain(id,"command",bytes)
        val descriptor=directory.resolve("$id.command")
        fun quoted(value:String)="'"+value.replace("'","'\\''")+"'"
        val javaHome=Path.of(System.getProperty("java.home")).toRealPath()
        val script=("#!/bin/sh\nJAVA_HOME="+quoted(javaHome.toString())+"\nexport JAVA_HOME\nWRITHELM_TEST_CLIENT_PROFILE="+quoted(descriptor.toString())+"\nexport WRITHELM_TEST_CLIENT_PROFILE\nexec "+quoted(launcher.toString())+" \"\$@\"\n").toByteArray(Charsets.UTF_8)
        val wrapper=directory.resolve("writhelm")
        if(Files.exists(wrapper,LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(wrapper,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(wrapper) && Files.size(wrapper)==script.size.toLong() &&
                (Files.getAttribute(wrapper,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
                Files.getAttribute(wrapper,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(directory,"unix:uid") &&
                Files.getAttribute(wrapper,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1 && Files.readAllBytes(wrapper).contentEquals(script) && Files.isExecutable(wrapper))
        } else {
            FileChannel.open(wrapper,setOf(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).use {channel ->
                val buffer=java.nio.ByteBuffer.wrap(script);while(buffer.hasRemaining())channel.write(buffer);channel.force(true)
            }
            FileChannel.open(directory,StandardOpenOption.READ).use {it.force(true)}
        }
        return wrapper
    }
}

/** Complete safe repository preparation before native discovery can cache a non-Git project.
 * Reuses the enrollment adapter: never stage user bytes or repair an ambiguous existing .git. */
internal fun prepareAutomaticClientProject(directory:Path,clientRoot:Path,git:Path,gitDigest:Digest):Path {
    val work=directory.toRealPath()
    require(clientRoot.isAbsolute && !work.startsWith(clientRoot) && !clientRoot.startsWith(work))
    LinuxTestClientRepository(git,gitDigest).prepare(work)
    return work
}

/** Installed wrapper selects one private profile; the only user argument is the project folder. */
object AutomaticTestClient {
    @JvmStatic fun main(args:Array<String>){
        try {
            require(args.size==1)
            val profile=automaticClientProfile()
            if(args.single()=="--stop-service"){LinuxAutomaticClientService.stop(profile);return}
            val directory=prepareAutomaticClientProject(Path.of(args.single()),profile.root,profile.git,profile.gitDigest)
            LinuxAutomaticClientService.ensure(profile)
            check(checkNotNull(LinuxAutomaticTestClient.openExistingUI(profile,directory)).waitFor()==0){"Native UI refused"}
        }catch(error:Exception){
            System.err.println("Writhelm test-client startup held; no unmanaged fallback was launched")
            // Fixed code locations only. Exception messages/causes can contain credential-bearing
            // transport payloads and are deliberately not printed into the terminal.
            error.stackTrace.filter {it.className.startsWith("dev.writhelm.")}.take(3).forEach {
                System.err.println("Startup hold at ${it.fileName}:${it.lineNumber}")
            }
            kotlin.system.exitProcess(2)
        }
    }
}

/** Normal-lifecycle Linux test-client launcher. Configuration contains installed paths/pins and
 * schemas, never canonical identities/owner keys. Unknown prior native ownership remains held;
 * this is not abnormal-process/writer recovery qualification or a permissive executor profile. */
internal class LinuxAutomaticTestClient private constructor(private val profile:LinuxAutomaticClientProfile,
    val nativeOrigin:String,val serviceOrigin:String,private val environment:Map<String,String>,
    private val instance:LinuxTestClientInstance,private val facts:SharedH1ControllerFacts,
    private val stopService:()->Unit):AutoCloseable {
    private var closed=false
    val connectedControllers:Set<UUID> get()=facts.connectedControllers()
    val nativeRegistration:Path get()=profile.root.resolve("state/opencode/service.json")
    /** Actual native UI; no prompt, model selection, bootstrap phrase or owner credential argument. */
    fun openUI(directory:Path):Process {
        check(!closed);return startUI(profile,environment,directory)
    }
    @Synchronized override fun close(){if(closed)return;closed=true;try{stopService()}finally{instance.close()}}
    companion object {
        private fun environment(root:Path)=mapOf("HOME" to root.resolve("home").toString(),"PATH" to "/usr/bin:/bin","LANG" to "C.UTF-8",
            "XDG_CONFIG_HOME" to root.resolve("config").toString(),"XDG_DATA_HOME" to root.resolve("data").toString(),
            "XDG_STATE_HOME" to root.resolve("state").toString(),"XDG_CACHE_HOME" to root.resolve("cache").toString(),
            "XDG_RUNTIME_DIR" to root.resolve("runtime").toString(),"OPENCODE_DESKTOP_TEST_ROOT" to root.toString(),
            "OPENCODE_DB" to root.resolve("data/opencode/client.db").toString(),"WRITHELM_PRIMARY_ROOT" to root.resolve("private").toString())
        private fun startUI(profile:LinuxAutomaticClientProfile,environment:Map<String,String>,directory:Path):Process {
            val work=prepareAutomaticClientProject(directory,profile.root,profile.git,profile.gitDigest)
            pinned(profile.cli,profile.cliDigest)
            val builder=ProcessBuilder(profile.cli.toString(),work.toString()).directory(work.toFile()).inheritIO()
            builder.environment().clear();builder.environment().putAll(environment);return builder.start()
        }
        fun openExistingUI(profile:LinuxAutomaticClientProfile,directory:Path):Process? =
            if(discover(profile))startUI(profile,environment(profile.root),directory)else null
        /** Authenticated discovery only: never open a second APP or forward its owner credential. */
        fun discover(profile:LinuxAutomaticClientProfile):Boolean {
            val registration=profile.root.resolve("state/opencode/service.json")
            if(!Files.exists(registration,LinkOption.NOFOLLOW_LINKS))return false
            LinuxConnectedCustody(profile.root)
            fun read(file:Path):JObject {
                LinuxConnectedCustody(file.parent)
                require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && file.toRealPath()==file && Files.size(file)<=2*1024*1024 &&
                    (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
                    Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(file.parent,"unix:uid") &&
                    Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
                return StrictJson.decode(Files.readAllBytes(file)) as JObject
            }
            val bootstrap=read(profile.root.resolve("private/bootstrap/automatic-bootstrap-${Digest.of("current".toByteArray()).hex}.json"))
            val service=bootstrap.entries.getValue("serviceOrigin").string();val native=bootstrap.entries.getValue("nativeOrigin").string()
            HttpProfile(service);HttpProfile(native)
            val client=java.net.http.HttpClient.newHttpClient()
            fun get(url:String,header:String,value:String):JsonValue {
                val request=java.net.http.HttpRequest.newBuilder(java.net.URI(url)).header(header,value).timeout(java.time.Duration.ofSeconds(10)).GET().build()
                val response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofInputStream())
                val bytes=response.body().use {it.readNBytes(65537)}
                check(response.statusCode()==200 && bytes.size<=65536){"Existing test-client readiness held"}
                return StrictJson.decode(bytes)
            }
            val ready=get(service+"/internal/enrollment/v1","X-Writhelm-Enrollment-Proof",bootstrap.entries.getValue("enrollmentProof").string())
                .fields("formatVersion","kind","storeId")
            require(ready.getValue("formatVersion").long()==1L && ready.getValue("kind").string()=="test-client-ready" &&
                ready.getValue("storeId")==bootstrap.entries.getValue("storeId"))
            val registered=read(registration)
            require(registered.entries.getValue("url").string()==native)
            val auth="Basic "+java.util.Base64.getEncoder().encodeToString(("opencode:"+registered.entries.getValue("password").string()).toByteArray())
            val info=get(native+"/api/info","Authorization",auth) as JObject
            require(info.entries.getValue("version").string()=="2.2.5")
            val pid=info.entries.getValue("pid").long();require(pid in 1..Int.MAX_VALUE.toLong())
            require((info.entries.getValue("urls") as JArray).values.any {java.net.URI(it.string()).let {u ->"${u.scheme}://${u.rawAuthority}"==native}})
            pinned(Path.of("/proc/$pid/exe").toRealPath(),profile.cliDigest)
            return true
        }
        private fun pinned(file:Path,expected:Digest){
            require(file.isAbsolute && file.toRealPath()==file && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            val hash=java.security.MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use {input ->val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;hash.update(bytes,0,n)}}
            require(Digest.parse(hash.digest().joinToString(""){"%02x".format(it)})==expected){"Installed executable pin mismatch"}
        }
        internal fun validateProfile(profile:LinuxAutomaticClientProfile){
            require(System.getProperty("os.name")=="Linux" && profile.root.isAbsolute && profile.root.normalize()==profile.root)
            require(profile.root!=profile.root.root)
            // Match native custody's ancestor policy BEFORE emitting a supposedly usable install
            // profile or creating owner credentials. Do not chmod/repair an existing shared root.
            val uid=(Files.getAttribute(Path.of("/proc/self"),"unix:uid") as Number).toLong()
            var current=profile.root.root
            for(part in profile.root){
                current=current.resolve(part)
                if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS))break
                require(Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(current))
                val owner=(Files.getAttribute(current,"unix:uid",LinkOption.NOFOLLOW_LINKS) as Number).toLong()
                val mode=(Files.getAttribute(current,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Number).toInt()
                require(owner==0L || owner==uid)
                require(mode and 0x12==0 || owner==0L && mode and 0x200!=0){"Unsafe test-client data ancestor"}
            }
            if(Files.exists(profile.root,LinkOption.NOFOLLOW_LINKS)){
                require((Files.getAttribute(profile.root,"unix:uid",LinkOption.NOFOLLOW_LINKS) as Number).toLong()==uid &&
                    (Files.getAttribute(profile.root,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Number).toInt() and 0x3f==0)
            }
            pinned(profile.node,profile.nodeDigest);pinned(profile.cli,profile.cliDigest);pinned(profile.git,profile.gitDigest)
            require(profile.cliDigest==Digest.parse("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050"))
            require(profile.plugin.isAbsolute && profile.plugin.toRealPath()==profile.plugin && profile.schemas.isNotEmpty())
            installedModule(profile,".");installedModule(profile,"./tui");installedModule(profile,"./bootstrap")
        }
        private fun installedModule(profile:LinuxAutomaticClientProfile,exportName:String):Path {
            val manifest=profile.plugin.resolve("package.json")
            require(Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS) && Files.size(manifest)<=65536)
            val pkg=StrictJson.decode(Files.readAllBytes(manifest)) as JObject
            val exports=pkg.entries.getValue("exports") as JObject
            val relative=exports.entries.getValue(exportName).string()
            require(relative.startsWith("./") && ".." !in relative.split('/'))
            val module=profile.plugin.resolve(relative).normalize()
            require(module.startsWith(profile.plugin) && module.toRealPath()==module && Files.isRegularFile(module,LinkOption.NOFOLLOW_LINKS))
            return module
        }
        fun start(profile:LinuxAutomaticClientProfile):LinuxAutomaticTestClient {
            validateProfile(profile)
            val custody=LinuxConnectedCustody(profile.root)
            fun directory(name:String)=profile.root.resolve(name).also {LinuxConnectedCustody(it)}
            val home=directory("home");val config=directory("config/opencode");directory("data");directory("state");directory("cache");directory("runtime");directory("logs")
            directory("state/opencode");directory("data/opencode")
            val privateRoot=directory("private");val facts=SharedH1ControllerFacts()
            val instance=LinuxTestClientInstance.open(privateRoot.resolve("instance"),facts.options())
            var closeRuntime:(()->Unit)?=null;var nativeStarted=false
            val environment=environment(profile.root)
            fun cli(vararg args:String){
                val builder=ProcessBuilder(profile.cli.toString(),*args).directory(home.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(profile.root.resolve("logs/native-cli.log").toFile())).redirectErrorStream(true)
                builder.environment().clear();builder.environment().putAll(environment)
                val child=builder.start();check(child.waitFor(35,java.util.concurrent.TimeUnit.SECONDS)){"Native client command timed out"}
                check(child.exitValue()==0){"Native client command refused"}
            }
            try {
                val id=UUID.nameUUIDFromBytes("writhelm.test-client.origins.v1".toByteArray())
                val record=profile.root.resolve("$id.dispatch")
                val origins=if(Files.exists(record,LinkOption.NOFOLLOW_LINKS)){
                    require(Files.isRegularFile(record,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(record) && Files.size(record)<=1024)
                    Files.readAllBytes(record)
                }else {
                    java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use {serviceSocket ->
                        java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use {nativeSocket ->
                            StrictJson.encode(obj("serviceOrigin" to str("http://127.0.0.1:${serviceSocket.localPort}"),
                                "nativeOrigin" to str("http://127.0.0.1:${nativeSocket.localPort}")))
                        }
                    }
                }
                custody.retain(id,"dispatch",origins)
                val selected=StrictJson.decode(origins).fields("serviceOrigin","nativeOrigin")
                val serviceOrigin=selected.getValue("serviceOrigin").string();val nativeOrigin=selected.getValue("nativeOrigin").string()
                HttpProfile(serviceOrigin);HttpProfile(nativeOrigin)
                val registration=profile.root.resolve("state/opencode/service.json")
                check(!Files.exists(registration,LinkOption.NOFOLLOW_LINKS)){"Previous native registration requires reconciliation"}
                val executionPolicy=profile.root.resolve("installation").resolve(
                    "${UUID.nameUUIDFromBytes("writhelm.test-client.execution-deployments.v1".toByteArray(Charsets.UTF_8))}.command")
                val extra=if(Files.exists(executionPolicy,LinkOption.NOFOLLOW_LINKS)){
                    require(Files.isRegularFile(executionPolicy,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(executionPolicy) && Files.size(executionPolicy)<=1024*1024)
                    val policy=StrictJson.decode(protectedRuntimeBytes(executionPolicy.parent,executionPolicy,1024*1024)).fields("formatVersion","deployments")
                    require(policy.getValue("formatVersion").long()==1L)
                    arrayOf("executionDeployments" to policy.getValue("deployments"))
                }else emptyArray()
                val provision=obj(*extra,"root" to str(privateRoot.toString()),"forbiddenAreas" to JArray(listOf(str(home.toString()))),
                    "storeId" to uuid(instance.storeId),"serviceOrigin" to str(serviceOrigin),"nativeOrigin" to str(nativeOrigin),
                    "nativeRegistrationFile" to str(registration.toString()),"clientPolicyFile" to str(config.resolve("opencode.json").toString()),
                    "plugin" to str(profile.plugin.toString()),"schemas" to JObject(profile.schemas.mapValues {str(it.value)}))
                val code="const {readFileSync}=await import('node:fs');const server=(await import(process.argv[2])).default;const tui=(await import(process.argv[3])).default;if(!server||typeof server.setup!=='function'||!tui||typeof tui.setup!=='function')throw Error('Mandatory client plugin definitions unavailable');const {provisionAutomaticPrimary}=await import(process.argv[1]);await provisionAutomaticPrimary(JSON.parse(readFileSync(0,'utf8')))"
                val installer=ProcessBuilder(profile.node.toString(),"--input-type=module","-e",code,installedModule(profile,"./bootstrap").toUri().toString(),
                    installedModule(profile,".").toUri().toString(),installedModule(profile,"./tui").toUri().toString())
                    .redirectOutput(profile.root.resolve("logs/provision.log").toFile()).redirectErrorStream(true)
                installer.environment().clear();installer.environment().putAll(environment)
                val child=installer.start();child.outputStream.use {it.write(StrictJson.encode(provision))}
                check(child.waitFor(35,java.util.concurrent.TimeUnit.SECONDS) && child.exitValue()==0){"Automatic provisioning held"}
                val bootstrap=privateRoot.resolve("bootstrap/automatic-bootstrap-${Digest.of("current".toByteArray()).hex}.json")
                val proof=java.util.Base64.getUrlDecoder().decode((StrictJson.decode(Files.readAllBytes(bootstrap)) as JObject).entries.getValue("enrollmentProof").string())
                val observer:(String)->LinuxTestClientEnrollment.ObservedTestClientSession={session ->
                    val native=StrictJson.decode(Files.readAllBytes(registration)) as JObject
                    require(native.entries.getValue("url").string()==nativeOrigin)
                    val authorization="Basic "+java.util.Base64.getEncoder().encodeToString(("opencode:"+native.entries.getValue("password").string()).toByteArray())
                    TestClientNativeSessions(nativeOrigin,authorization).observe(session)
                }
                val broker=LinuxTestClientEnrollment(instance.app,privateRoot.resolve("enrollment"),instance.storeId,instance.anchorProjectId,instance.ownerToken,
                    LinuxTestClientRepository(profile.git,profile.gitDigest),observer)
                val passive=AutomaticPassiveDeployment(profile.root.resolve("worktrees"),privateRoot,profile.git,profile.gitDigest)
                val projectChecks=AutomaticProjectCheckDeployment(profile.root.resolve("installation"))
                val router=NativeRuntimeEndpointRouter();val manager=SharedH1ControllerRuntime(instance.app,router,privateRoot.resolve("controllers"),facts,
                    configuration={source,controllerRoot ->LinuxNativeRuntimeEnvironment(nativeOrigin,registration,source,profile.plugin,controllerRoot,
                        PinnedNativeExecutable("2.2.5",profile.cliDigest),privateRoot)},
                    provisionPassive={binding,controllerRoot->
                        passive.provision(binding.projectId,binding.controllerId,binding.workRoot,controllerRoot)
                        projectChecks.provision(binding.workRoot,controllerRoot,requireNotNull(PassiveRuntimeLayout.read(controllerRoot)).worktreeParent)})
                val endpoint=try{TestClientEnrollmentEndpoint(broker,proof,manager::configure){
                    val access=instance.app.inspectAccess(instance.ownerToken)
                    require(access.storeId==instance.storeId && access.projectId==instance.anchorProjectId &&
                        access.scope==CredentialScope.Project && dev.writhelm.core.workflow.Capability.MANAGE in access.capabilities)
                    instance.storeId
                }}finally{proof.fill(0)}
                val http=HttpAdapter(WrithelmService(instance.app),HttpProfile(serviceOrigin),enrollmentEndpoint=endpoint,nativeEndpoints=router)
                val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(serviceOrigin).port){http.install(this)}
                closeRuntime={endpoint.close();manager.close();server.stop(0,5000);runBlocking{http.mcp.close()}}
                server.start(wait=false)
                cli("service","set","port",java.net.URI(nativeOrigin).port.toString())
                nativeStarted=true;cli("service","start")
                return LinuxAutomaticTestClient(profile,nativeOrigin,serviceOrigin,environment,instance,facts){
                    try{cli("service","stop")}finally{closeRuntime?.invoke()}
                }
            }catch(error:Throwable){
                try{if(nativeStarted)cli("service","stop")}finally{try{closeRuntime?.invoke()}finally{instance.close()}}
                throw error
            }
        }
    }
}

/** Shared service facts are routed by canonical assignment/controller identity, never by whichever
 * session connected last. Unattributed/unconnected observations remain unavailable. */
internal class SharedH1ControllerFacts:H1RuntimeFacts {
    private data class Peer(val project:UUID,val native:HttpConnectedNativeControl,val facts:H1RuntimeFacts,val retention:RetentionEffectQualification,val safety:NativeRuntimeSafety,val reviews:CandidateReviewTicketIssuer?)
    private val peers=java.util.concurrent.ConcurrentHashMap<UUID,Peer>()
    fun install(project:UUID,controller:UUID,native:HttpConnectedNativeControl,facts:H1RuntimeFacts,retention:RetentionEffectQualification,
        safety:NativeRuntimeSafety=UnavailableNativeRuntimeSafety,reviews:CandidateReviewTicketIssuer?=null) {
        val prior=peers.putIfAbsent(controller,Peer(project,native,facts,retention,safety,reviews))
        require(prior==null || prior.native===native) { "Controller connection already installed" }
    }
    fun remove(controller:UUID){peers.remove(controller)}
    fun connectedControllers():Set<UUID> = peers.keys.toSet()
    private fun retention(state:H1BoundState,project:UUID,subject:WAssignmentSubject,execution:UUID,result:WSnapshot):Boolean {return try {
        val assignment=state.work[project]?.assignment(subject.assignmentId) ?: return false
        val peer=peers[assignment.intendedExecution.controllerId] ?: return false
        peer.project==project && peer.retention.qualified(state,project,subject,execution,result)
    }catch(_:Exception){false}}
    private fun safeAccess(state:H1BoundState,subject:WAssignmentSubject):Boolean {return try {
        val matches=state.work.entries.mapNotNull { (project,work) ->
            val assignment=work.records[WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,subject.assignmentId))]
                ?.payload as? WAssignmentsPayload ?: return@mapNotNull null
            val peer=peers[assignment.value.intendedExecution.controllerId] ?: return@mapNotNull null
            if(peer.project!=project)null else peer
        }
        matches.singleOrNull()?.safety?.safeAccess(state,subject)==true
    }catch(_:Exception){false}}
    fun options()=H1Options(this,workModelVersion=4,nativeSafety=NativeRuntimeSafety(::safeAccess),retentionEffectQualification=RetentionEffectQualification(::retention),candidateReviewTickets={controller->peers[controller]?.reviews},candidateAssemblyProofByController={ operation,controller ->
        peers[controller]?.native?.candidateAssemblyProof(operation)
    })
    override fun capture(state:H1BoundState,command:WorkCommand):H1ObservedFacts {
        val work=state.work[command.projectId] ?: return UnavailableH1RuntimeFacts.capture(state,command)
        val invocation=H1JointReduction().invocation(command)
        val assignment=invocation.assignment?.assignmentId ?: when(val operation=command.operation) {
            is CBindAssignmentOperation -> operation.value.assignmentId
            is CRequestNativeTransitionOperation -> operation.value.assignmentId
            is CObserveNativeTransitionOperation -> operation.value.assignmentId
            is CRebindAssignmentOperation -> operation.value.assignmentId
            is CCompleteAssignmentOperation -> operation.value.assignmentId
            is CReplaceIsolatedAssignmentOperation -> operation.value.old.key.id
            else -> null
        }
        val controller=invocation.controllerId ?: assignment?.let { work.assignment(it).intendedExecution.controllerId } ?:
            (command.operation as? CCreateTaskRevisionOperation)?.let { operation ->
                work.records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }
                    .filter { it.task==operation.value.key }.map { it.intendedExecution.controllerId }.distinct().singleOrNull()
            }
        val peer=controller?.let(peers::get)
        return if(peer!=null && peer.project==command.projectId)peer.facts.capture(state,command)
            else CanonicalTaskDecisionFacts(UnavailableH1RuntimeFacts).capture(state,command)
    }
}

/** Trusted shared-instance runtime activation. Configuration is retained before endpoint creation;
 * it is not an activation/qualification assertion. Actual connection still checks native identity
 * and the pinned process, and every controller shares the same APP publisher. */
internal class SharedH1ControllerRuntime(private val app:H1Application,private val router:NativeRuntimeEndpointRouter,
    private val root:Path,private val observations:SharedH1ControllerFacts,
    private val observeNativeProcess:(HttpConnectedNativeControl,String)->Boolean={ native,epoch ->
        native.observeProcess(epoch,PinnedNativeExecutable("2.2.5",Digest.parse("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")))!=null
    },private val configuration:(Path,Path)->RuntimeConfigurationObservation={_,_->RuntimeConfigurationObservation {null}},
    private val provisionPassive:(LinuxTestClientEnrollment.Controller,Path)->Unit={_,_->}):AutoCloseable {
    private val custody=LinuxConnectedCustody(root)
    private data class Running(val native:HttpConnectedNativeControl,val connection:LocalH1ControllerConnection)
    private val running=linkedMapOf<UUID,Running>()
    private var closed=false
    @Synchronized fun configure(binding:LinuxTestClientEnrollment.Controller):JsonValue {
        check(!closed)
        val access=app.inspectAccess(binding.token)
        require(access.projectId==binding.projectId && access.principalId==binding.principalId && access.scope==CredentialScope.Controller(binding.controllerId))
        val record=root.resolve("${binding.controllerId}.dispatch")
        val bytes=if(Files.exists(record,LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(record,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(record) && Files.size(record)<=16384)
            Files.readAllBytes(record)
        } else {
            val random=SecureRandom()
            fun proof()=ByteArray(32).also(random::nextBytes).let { secret ->
                try{java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret)}finally{secret.fill(0)}
            }
            val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use {it.localPort}
            StrictJson.encode(obj("root" to str(root.resolve("controller-${binding.controllerId}").toString()),"hostId" to uuid(UUID.randomUUID()),
                "transportOrigin" to str("http://127.0.0.1:$port"),"serviceProof" to str(proof()),"transportProof" to str(proof())))
        }
        custody.retain(binding.controllerId,"dispatch",bytes)
        val profile=StrictJson.decode(bytes).fields("root","hostId","transportOrigin","serviceProof","transportProof")
        val operational=Path.of(profile.getValue("root").string())
        require(operational==root.resolve("controller-${binding.controllerId}") && !operational.startsWith(binding.workRoot) && !binding.workRoot.startsWith(operational))
        LinuxConnectedCustody(operational)
        provisionPassive(binding,operational)
        if(binding.controllerId !in running) {
            val native=HttpConnectedNativeControl(profile.getValue("transportOrigin").string(),java.util.Base64.getUrlDecoder().decode(profile.getValue("transportProof").string()))
            val reports=operational.resolve("reports");LinuxConnectedCustody(reports)
            val connection=LocalH1ControllerConnection(app,native,operational,binding.projectId,binding.controllerId,uuid(profile.getValue("hostId")),binding.token,
                LinuxOwnedDirectoryPreparation(emptyList()),OwnedCheckCustody(reports,emptyMap()),
                { facts,retention,safety,reviews ->observations.install(binding.projectId,binding.controllerId,native,facts,retention,safety,reviews) },observeNativeProcess,
                configuration(binding.workRoot,operational),{epoch ->NativeCheckpointPreparation(NativeRecordedWorkArea(operational,binding.workRoot,root.parent,epoch))})
            val endpoint=WrithelmService(app).nativeEndpoint(binding.projectId,binding.controllerId,uuid(profile.getValue("hostId")),
                java.util.Base64.getUrlDecoder().decode(profile.getValue("serviceProof").string()),connection::connectNative)
            try {router.register(binding.controllerId,endpoint);running[binding.controllerId]=Running(native,connection)}
            catch(error:Throwable){endpoint.close();connection.close();native.close();throw error}
        }
        return JObject(profile)
    }
    @Synchronized override fun close() {
        if(closed)return
        closed=true
        running.forEach { (controller,value) -> observations.remove(controller);value.connection.close();value.native.close() }
        running.clear();router.close()
    }
}

/** Per-controller connection on an already owned shared APP/store. Closing a controller never
 * closes another controller's publisher/HTTP service. Qualification behavior is unchanged. */
internal class LocalH1ControllerConnection(private val app:H1Application,
    private val native: HttpConnectedNativeControl,private val root: Path,private val project: UUID,
    private val controller: UUID,private val host: UUID,private val token: String,
     private val preparation: LinuxOwnedDirectoryPreparation,private val checks: OwnedCheckCustody,
     private val installFacts: (H1RuntimeFacts,RetentionEffectQualification,NativeRuntimeSafety,CandidateReviewTicketIssuer)->Unit,
     private val observeNativeProcess:(HttpConnectedNativeControl,String)->Boolean,
     private val configuration:RuntimeConfigurationObservation=RuntimeConfigurationObservation {null},
     private val preparationForEpoch:((String)->ConnectedPreparation)?=null): AutoCloseable {
    private var engine: ConnectedWorkEngine?=null
    private var closed=false
    /** Run after the explicitly configured in-process native peer is listening. No worker is
     * launched here; original commands/unknown effects are recovered by a fresh canonical scan. */
    @Synchronized fun connectNative() {
        check(!closed)
        if(engine!=null)return // duplicate startup handshake never constructs a second engine
        val access=app.inspectAccess(token)
        require(access.scope==CredentialScope.Controller(controller) && access.projectId==project)
        app.coordinationCapture(token,project) // actual current READ + TASK_DISPATCH, not role labels
        val identity=native.startupIdentity()
        val credential=CredentialVerifier.parse(token)?.use { it.credentialId } ?: error("bounded credential required")
        require(uuid(identity.getValue("hostId"))==host && uuid(identity.getValue("controllerId"))==controller &&
            uuid(identity.getValue("principalId"))==access.principalId && uuid(identity.getValue("credentialId"))==credential)
        val session=identity.getValue("sessionID").string();require(session.matches(Regex("ses_[A-Za-z0-9_-]{1,128}")))
        val epoch=identity.getValue("epoch").string();require(epoch.matches(Regex("[0-9a-f]{64}")))
        // Match the existing pinned native-host executable independently through /proc. A selector
        // from the authenticated peer is not itself a process or profile qualification.
        require(observeNativeProcess(native,epoch)) { "native process identity unavailable" }
        val areas=(identity.getValue("workRoots") as? JArray)?.values ?: error("actual native areas required")
        require(areas.isNotEmpty() && areas.size<=257)
        areas.forEach { val area=Path.of(it.string()).toRealPath();require(!root.startsWith(area) && !area.startsWith(root)) }
        val execution=UUID.nameUUIDFromBytes("writhelm-native-controller:$controller:$host:$session:$epoch".toByteArray(Charsets.UTF_8))
        val provenance=Model2ControllerExecution(controller,execution,host,session)
        val environment=ControllerProfileEnvironment(project,controller,host,epoch,
            {native.observeProcess(epoch,PinnedNativeExecutable("2.2.5",Digest.parse("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")))},
             {configuration.observe(epoch)},NativeExecutionProbe {state,subject,purpose->native.executionAdmission(state,project,subject,purpose,epoch)})
        val qualification=ControllerPhaseAdmission(project,environment)
        val reviewTickets=ServiceCandidateReviewTickets(root.resolve("review-tickets"),controller,NativeCandidateReviewReceipts(root.resolve("native")),qualification,epoch)
        val profileFacts=CanonicalTaskDecisionFacts(UnavailableH1RuntimeFacts)
        val nativeFacts=NativeInitialFacts(NativePreparationFacts(NativeContextFacts(profileFacts,native,epoch,provenance,reviewTickets),native,epoch),native,epoch,reviewTickets)
        val retained=NativeResultRetentionFacts(nativeFacts,native,qualification)
        val resultFacts=NativeResultDiffFacts(retained,NativeResultDiffTransportProof(native,epoch))
        val reviewInputs=NativeCandidateReviewInputFacts(resultFacts,NativeCandidateReviewReceipts(root.resolve("native")),native,qualification)
        val inputs=ReservationProofFacts(reviewInputs,DeclaredPrebindingInputVerifier(reviewInputs))
        val selectedPreparation=preparationForEpoch?.invoke(epoch) ?: preparation
        installFacts(qualification.executionFacts(ConnectedRuntimeFacts(inputs,selectedPreparation,native,
            provenance,checks::observed)),qualification,qualification,
            reviewTickets)
        engine=ConnectedWorkEngine(app,project,{token},selectedPreparation,native,LinuxConnectedCustody(root.resolve("engine")))
    }
    @Synchronized fun holds()=engine?.holds.orEmpty()
    @Synchronized override fun close() {
        if(closed)return
        closed=true
        engine?.close()
        configuration.close()
    }
}

/** Explicit local component composition, not a K-RUN launcher/recovery qualification. The owner
 * GUI remains separate. The only retained key used here is a bounded controller credential supplied
 * by trusted client storage, never an owner/project key, model option, route or command-line field. */
internal class LocalH1Runtime private constructor(val app: H1Application,private val http: HttpAdapter,
    private val endpoint: NativeRuntimeEndpoint,private val server: EmbeddedServer<*,*>,
    private val native: HttpConnectedNativeControl,root: Path,project: UUID,
    controller: UUID,host: UUID,token: String,
    preparation: LinuxOwnedDirectoryPreparation,checks: OwnedCheckCustody,
    installFacts: (H1RuntimeFacts)->Unit,
    observeNativeProcess:(HttpConnectedNativeControl,String)->Boolean): AutoCloseable {
    private val connection=LocalH1ControllerConnection(app,native,root,project,controller,host,token,preparation,checks,{facts,_,_,_->installFacts(facts)},observeNativeProcess)
    private var closed=false
    @Synchronized fun connectNative()=connection.connectNative()
    @Synchronized fun holds()=connection.holds()
    @Synchronized override fun close() {
        if(closed)return
        closed=true
        connection.close()
        server.stop(0,5000)
        runBlocking { http.mcp.close() }
        endpoint.close();native.close();app.close()
    }
     companion object {
         private fun observedNativeProcess(native:HttpConnectedNativeControl,epoch:String):Boolean =
             native.observeProcess(epoch,PinnedNativeExecutable("2.2.5",Digest.parse("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")))!=null
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
             registrations: List<OwnedPreparationRegistration> = emptyList(),
             observeNativeProcess:(HttpConnectedNativeControl,String)->Boolean=::observedNativeProcess): LocalH1Runtime {
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
             val profilePath=nativeCustodyRoot.resolve("service-work-profile-${Digest.of("current".toByteArray(Charsets.UTF_8)).hex}.json")
             val modelVersion=if(!Files.exists(profilePath,LinkOption.NOFOLLOW_LINKS))2 else {
                 val selected=JObject(secretRecord(nativeCustodyRoot,"service-work-profile","current")).fields("formatVersion","modelVersion")
                 require(selected.getValue("formatVersion").long()==1L && selected.getValue("modelVersion").long()==4L)
                 4 // explicit protected selection; never auto-migrate a legacy store
             }
             val serviceProof=proof("service")
            try {
                val nativeProof=proof("transport")
                try { return open(storeDirectory,storeId,project,controller,host,origin,nativeOrigin,serviceProof,nativeProof,
                     token,operationalRoot,managedWorkAreas,registrations,uuid(key.getValue("principalId")),modelVersion,observeNativeProcess) }
                finally { nativeProof.fill(0) }
            } finally { serviceProof.fill(0) }
        }
        fun open(storeDirectory: Path,storeId: UUID,project: UUID,controller: UUID,host: UUID,origin: String,
            nativeOrigin: String,serviceProof: ByteArray,nativeProof: ByteArray,controllerToken: String,
            operationalRoot: Path,managedWorkAreas: Collection<Path>,registrations: List<OwnedPreparationRegistration> = emptyList(),
             expectedPrincipal: UUID?=null,workModelVersion:Int=2,
             observeNativeProcess:(HttpConnectedNativeControl,String)->Boolean=::observedNativeProcess): LocalH1Runtime {
            val profile=HttpProfile(origin);HttpProfile(nativeOrigin)
            require(managedWorkAreas.isNotEmpty())
            managedWorkAreas.forEach { outside(operationalRoot.toAbsolutePath().normalize(),it.toRealPath()) }
            val root=directory(operationalRoot);outside(root,storeDirectory.toRealPath())
             val facts=java.util.concurrent.atomic.AtomicReference<H1RuntimeFacts?>()
             require(workModelVersion in setOf(2,4))
             val peer=java.util.concurrent.atomic.AtomicReference<HttpConnectedNativeControl?>()
             val app=H1Application.open(storeDirectory,storeId,H1Options(H1RuntimeFacts { state,command ->
                 facts.get()?.capture(state,command) ?: UnavailableH1RuntimeFacts.capture(state,command)
             },workModelVersion=workModelVersion,candidateAssemblyProof={ operation ->
                 if(workModelVersion==4 && facts.get()!=null)peer.get()?.candidateAssemblyProof(operation) else null
             }))
            var native: HttpConnectedNativeControl?=null
            var endpoint: NativeRuntimeEndpoint?=null
            var http: HttpAdapter?=null
            try {
                val access=app.inspectAccess(controllerToken)
                require(access.scope==CredentialScope.Controller(controller) && access.projectId==project &&
                    (expectedPrincipal==null || access.principalId==expectedPrincipal))
                 native=HttpConnectedNativeControl(nativeOrigin,nativeProof)
                 peer.set(native)
                val composition=java.util.concurrent.atomic.AtomicReference<LocalH1Runtime?>()
                endpoint=WrithelmService(app).nativeEndpoint(project,controller,host,serviceProof) {
                    (composition.get() ?: throw ServiceFault("UNAVAILABLE",503)).connectNative()
                }
                http=HttpAdapter(WrithelmService(app),profile,nativeEndpoint=endpoint)
                registrations.forEach { outside(root,it.root.toRealPath()) }
                val preparation=LinuxOwnedDirectoryPreparation(registrations)
                val checks=OwnedCheckCustody(directory(root.resolve("reports")),emptyMap())
                val installed=http
                val uri=java.net.URI(origin)
                val server=embeddedServer(CIO,host=uri.host.removeSurrounding("[","]"),port=uri.port) { installed.install(this) }.start(wait=false)
                return LocalH1Runtime(app,http,endpoint,server,native,root,project,controller,host,controllerToken,
                     preparation,checks,{ facts.set(it) },observeNativeProcess)
                    .also { composition.set(it) }
            } catch(error: Exception) {
                http?.let { runBlocking { it.mcp.close() } };endpoint?.close();native?.close();app.close();throw error
            }
        }
    }
}
