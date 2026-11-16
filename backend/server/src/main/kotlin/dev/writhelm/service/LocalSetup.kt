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
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.util.UUID
import java.security.SecureRandom
import dev.writhelm.core.store.*
import dev.writhelm.core.knowledge.Digest

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

/** Private test-client-owned service genesis/lifetime. No fixture identity, owner-key export,
 * global harness lookup or automatic reset of an incomplete/conflicting instance. Linux only. */
internal class LinuxTestClientInstance private constructor(val app:H1Application,val storeId:UUID,
    val anchorProjectId:UUID,internal val ownerToken:String,private val lock:java.nio.channels.FileLock,
    private val channel:FileChannel):AutoCloseable {
    private var closed=false
    @Synchronized override fun close(){
        if(closed)return
        closed=true
        try{app.close()}finally{try{lock.release()}finally{channel.close()}}
    }
    companion object {
        fun open(root:Path,options:H1Options):LinuxTestClientInstance {
            val custody=LinuxConnectedCustody(root)
            val file=root.resolve("instance.lock")
            require(!Files.isSymbolicLink(file))
            val channel=FileChannel.open(file,setOf(StandardOpenOption.CREATE,StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            var held:java.nio.channels.FileLock?=null
            var app:H1Application?=null
            try {
                require(Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(root,"unix:uid") &&
                    (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
                    Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
                held=channel.tryLock() ?: error("Test-client instance already owned")
                val id=UUID.nameUUIDFromBytes("writhelm.test-client.instance.v1".toByteArray(Charsets.UTF_8))
                val descriptor=root.resolve("$id.command")
                if(!Files.exists(descriptor,LinkOption.NOFOLLOW_LINKS)){
                    // A missing descriptor never authorizes replacing an existing store.
                    require(!Files.exists(root.resolve("store"),LinkOption.NOFOLLOW_LINKS))
                    val store=UUID.randomUUID();val anchor=UUID.randomUUID();val owner=UUID.randomUUID();val key=UUID.randomUUID()
                    val secret=ByteArray(32).also(SecureRandom()::nextBytes)
                    try {
                        custody.retain(id,"command",StrictJson.encode(obj("formatVersion" to num(1),"kind" to str("test-client-instance-v1"),
                            "storeId" to uuid(store),"anchorProjectId" to uuid(anchor),"ownerPrincipalId" to uuid(owner),"ownerCredentialId" to uuid(key),
                            "ownerToken" to str("wh1.$store.$key."+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret)),
                            "ownerVerifier" to digest(CredentialVerifier.digest(store,anchor,owner,key,secret)))))
                    }finally{secret.fill(0)}
                }
                require(Files.isRegularFile(descriptor,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(descriptor) && Files.size(descriptor)<=16384 &&
                    (Files.getAttribute(descriptor,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
                    Files.getAttribute(descriptor,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(root,"unix:uid") &&
                    Files.getAttribute(descriptor,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
                val f=StrictJson.decode(Files.readAllBytes(descriptor)).fields("formatVersion","kind","storeId","anchorProjectId","ownerPrincipalId","ownerCredentialId","ownerToken","ownerVerifier")
                require(f.getValue("formatVersion").long()==1L && f.getValue("kind").string()=="test-client-instance-v1")
                val store=uuid(f.getValue("storeId"));val anchor=uuid(f.getValue("anchorProjectId"));val owner=uuid(f.getValue("ownerPrincipalId"));val key=uuid(f.getValue("ownerCredentialId"))
                val token=f.getValue("ownerToken").string();val verifier=digest(f.getValue("ownerVerifier"))
                val ownerCredential=Credential(key,store,anchor,owner,verifier,CredentialStatus.ACTIVE,0)
                require(CredentialVerifier.parse(token)?.use {it.storeId==store && it.credentialId==key && it.verifies(ownerCredential)}==true)
                val policy=AccessPolicy(listOf(Grant(owner,anchor,Capability.entries.toSet())))
                val enrollment=Enrollment(owner,anchor)
                val records=listOf(Auth2Record.Principal(owner),Auth2Record.Project(ProjectBinding(anchor,owner)),Auth2Record.Guard(enrollment,0),
                    Auth2Record.WorkGrants(WorkGrant(enrollment,0,WorkPermission.entries)),Auth2Record.Key(ScopedCredential(ownerCredential,CredentialScope.Project)))
                val directory=root.resolve("store")
                app=if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS))H1Application.open(directory,store,options)
                else {
                    Files.createDirectory(directory,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                    H1Application.initialize(directory,store,H1Genesis(ApprovalWorkflow.start(policy),records,
                        mapOf(anchor to WorkState.empty(anchor,modelVersion=4))),options)
                }
                val access=app.inspectAccess(token)
                require(access.storeId==store && access.projectId==anchor && access.principalId==owner &&
                    access.scope==CredentialScope.Project && Capability.MANAGE in access.capabilities)
                return LinuxTestClientInstance(app,store,anchor,token,held,channel)
            }catch(error:Throwable){try{app?.close()}finally{try{held?.release()}finally{channel.close()}};throw error}
        }
    }
}

/** Trusted Linux test-client broker, not a service tool or public registration endpoint. The
 * constructor's anchor owner credential must come from protected client storage, never a prompt.
 * This increment manages original enrollment custody only; native session identity verification
 * and non-destructive repository setup belong to the client integration, not caller role labels. */
internal class LinuxTestClientEnrollment(private val app:H1Application,private val root:Path,
    private val storeId:UUID,private val anchorProject:UUID,private val anchorOwnerToken:String,
    private val repositories:LinuxTestClientRepository,private val observeSession:(String)->ObservedTestClientSession) {
    companion object { private val processEnrollmentLock=Any() }
    private val custody=LinuxConnectedCustody(root)
    private val random=SecureRandom()
    private val anchorOwner=app.inspectAccess(anchorOwnerToken).also {
        require(it.storeId==storeId && it.projectId==anchorProject && it.scope==CredentialScope.Project && Capability.MANAGE in it.capabilities)
    }.principalId
    internal class Project internal constructor(val directory:Path,val projectId:UUID,internal val ownerToken:String)
    internal class Controller internal constructor(val workRoot:Path,val projectId:UUID,val controllerId:UUID,val principalId:UUID,
        val credentialId:UUID,internal val token:String)
    /** Provided by trusted native-client adapter, not a request-body owner/role assertion. */
    internal data class ObservedTestClientSession(val id:String,val directory:Path,val parentSessionId:String?,val forked:Boolean=false,val reverted:Boolean=false)
    fun session(nativeSessionId:String):Controller {
        require(nativeSessionId.matches(Regex("ses_[A-Za-z0-9_-]{1,240}")))
        val observed=observeSession(nativeSessionId)
        require(observed.id==nativeSessionId && observed.parentSessionId==null && !observed.forked && !observed.reverted)
        app.requireUnassignedEnrollmentSession(anchorOwnerToken,anchorProject,nativeSessionId)
        val project=project(observed.directory)
        locked {
            val id=UUID.nameUUIDFromBytes(("writhelm.test-client.session.v1\u0000$storeId\u0000$nativeSessionId").toByteArray(Charsets.UTF_8))
            val pin=pending(id) {obj("formatVersion" to num(1),"kind" to str("session"),"sessionId" to str(nativeSessionId),
                "projectId" to uuid(project.projectId),"directory" to str(project.directory.toString()))}
            require(pin.keys==setOf("formatVersion","kind","sessionId","projectId","directory") && pin.getValue("formatVersion").long()==1L &&
                pin.getValue("kind").string()=="session" && pin.getValue("sessionId").string()==nativeSessionId &&
                uuid(pin.getValue("projectId"))==project.projectId && pin.getValue("directory").string()==project.directory.toString()) {
                "Session location changed; explicit controller rebind/recovery required"
            }
        }
        return controller(project,nativeSessionId)
    }
    private fun <T> locked(block:()->T):T = synchronized(processEnrollmentLock) {
        val file=root.resolve("enrollment.lock")
        require(!Files.isSymbolicLink(file))
        FileChannel.open(file,setOf(StandardOpenOption.CREATE,StandardOpenOption.WRITE),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).use { channel ->
            require(Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(root,"unix:uid") &&
                (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
                Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
            channel.lock().use { block() }
        }
    }
    private fun pending(id:UUID,create:()->JsonValue):Map<String,JsonValue> {
        val file=root.resolve("$id.command")
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))custody.retain(id,"command",StrictJson.encode(create()))
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && Files.size(file)<=16384 &&
            Files.getAttribute(file,"unix:uid",LinkOption.NOFOLLOW_LINKS)==Files.getAttribute(root,"unix:uid") &&
            (Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS) as Int) and 0x3f==0 &&
            Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)==1)
        return (StrictJson.decode(Files.readAllBytes(file)) as JObject).entries
    }
    private fun secret()=ByteArray(32).also(random::nextBytes)
    private fun token(credential:UUID,secret:ByteArray)="wh1.$storeId.$credential."+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
    private fun publish(owner:String,command:Auth3Command) {
        val result=app.execute(owner,Auth3CommandCodec().encode(command))
        require(result is CommandResult.Settled && result.receipt.outcome is LogicalOutcome.Success) { "Writhelm enrollment held; retain original operation for reconciliation" }
    }
    fun project(directory:Path):Project=locked {
        val requested=directory.toRealPath()
        require(Files.isDirectory(requested) && !requested.startsWith(root) && !root.startsWith(requested))
        val canonical=repositories.prepare(requested)
        require(!canonical.startsWith(root) && !root.startsWith(canonical))
        val id=UUID.nameUUIDFromBytes(("writhelm.test-client.project.v1\u0000$storeId\u0000$canonical").toByteArray(Charsets.UTF_8))
        val f=pending(id) {
            val project=UUID.randomUUID();val credential=UUID.randomUUID();val secret=secret()
            try { obj("formatVersion" to num(1),"kind" to str("project"),"directory" to str(canonical.toString()),
                "projectId" to uuid(project),"credentialId" to uuid(credential),"token" to str(token(credential,secret)),
                "verifier" to digest(CredentialVerifier.digest(storeId,project,anchorOwner,credential,secret))) }
            finally {secret.fill(0)}
        }
        require(f.keys==setOf("formatVersion","kind","directory","projectId","credentialId","token","verifier") &&
            f.getValue("formatVersion").long()==1L && f.getValue("kind").string()=="project" && f.getValue("directory").string()==canonical.toString())
        val project=uuid(f.getValue("projectId"));val credential=uuid(f.getValue("credentialId"))
        publish(anchorOwnerToken,Auth3Command(id,anchorProject,Auth3Payload.RegisterProject(project,credential,digest(f.getValue("verifier")))))
        val token=f.getValue("token").string();val access=app.inspectAccess(token)
        require(access.projectId==project && access.principalId==anchorOwner && access.scope==CredentialScope.Project)
        Project(canonical,project,token)
    }
    private fun controller(project:Project,nativeSessionId:String):Controller=locked {
        app.requireUnassignedEnrollmentSession(anchorOwnerToken,anchorProject,nativeSessionId)
        val id=UUID.nameUUIDFromBytes(("writhelm.test-client.controller.v1\u0000$storeId\u0000${project.projectId}\u0000$nativeSessionId").toByteArray(Charsets.UTF_8))
        val f=pending(id) {
            val principal=UUID.randomUUID();val credential=UUID.randomUUID();val secret=secret()
            try {obj("formatVersion" to num(1),"kind" to str("controller"),"projectId" to uuid(project.projectId),"sessionId" to str(nativeSessionId),
                "controllerId" to uuid(UUID.randomUUID()),"principalId" to uuid(principal),"credentialId" to uuid(credential),
                "token" to str(token(credential,secret)),"verifier" to digest(CredentialVerifier.digest(storeId,project.projectId,principal,credential,secret)))}
            finally {secret.fill(0)}
        }
        require(f.keys==setOf("formatVersion","kind","projectId","sessionId","controllerId","principalId","credentialId","token","verifier") &&
            f.getValue("formatVersion").long()==1L && f.getValue("kind").string()=="controller" && uuid(f.getValue("projectId"))==project.projectId &&
            f.getValue("sessionId").string()==nativeSessionId)
        val controller=uuid(f.getValue("controllerId"));val principal=uuid(f.getValue("principalId"));val credential=uuid(f.getValue("credentialId"))
        publish(project.ownerToken,Auth3Command(id,project.projectId,Auth3Payload.RegisterController(controller,principal,credential,digest(f.getValue("verifier")))))
        val token=f.getValue("token").string();val access=app.inspectAccess(token)
        require(access.projectId==project.projectId && access.principalId==principal && access.scope==CredentialScope.Controller(controller))
        Controller(project.directory,project.projectId,controller,principal,credential,token)
    }
}

/** Explicit Linux project preparation; executable identity is selected by trusted client startup.
 * No staging/reset/checkout/clean, inherited Git environment, hooks or user-configured templates.
 * Existing repository roots are reused, including when a subdirectory is opened. */
internal class LinuxTestClientRepository(private val executable:Path,private val executableDigest:Digest) {
    init {require(System.getProperty("os.name")=="Linux" && executable.isAbsolute)}
    private data class Reply(val code:Int,val text:String)
    private fun git(directory:Path,vararg args:String,input:ByteArray=byteArrayOf()):Reply {
        require(Files.isRegularFile(executable) && Files.isExecutable(executable) && Digest.of(Files.readAllBytes(executable))==executableDigest)
        val process=ProcessBuilder(executable.toString(),"-c","core.hooksPath=/dev/null","-c","commit.gpgSign=false",
            "-c","core.fsmonitor=false","-c","core.quotePath=false","-C",directory.toString(),*args).redirectError(ProcessBuilder.Redirect.DISCARD)
        process.environment().clear()
        process.environment().putAll(mapOf("PATH" to executable.parent.toString(),"LC_ALL" to "C","GIT_CONFIG_NOSYSTEM" to "1",
            "GIT_CONFIG_GLOBAL" to "/dev/null","GIT_TERMINAL_PROMPT" to "0","GIT_AUTHOR_NAME" to "Writhelm setup",
            "GIT_AUTHOR_EMAIL" to "setup@writhelm.invalid","GIT_COMMITTER_NAME" to "Writhelm setup","GIT_COMMITTER_EMAIL" to "setup@writhelm.invalid"))
        val child=process.start()
        try {
            child.outputStream.use { it.write(input) }
            val bytes=child.inputStream.readNBytes(16385)
            require(bytes.size<=16384 && child.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)) { "Repository preparation outcome unavailable" }
            return Reply(child.exitValue(),bytes.toString(Charsets.UTF_8).trimEnd('\n','\r'))
        } finally { if(child.isAlive){child.destroy();if(!child.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))child.destroyForcibly()} }
    }
    @Synchronized fun prepare(directory:Path):Path {
        val canonical=directory.toRealPath();require(Files.isDirectory(canonical))
        val found=git(canonical,"rev-parse","--show-toplevel")
        if(found.code==0) {
            val repository=Path.of(found.text).toRealPath()
            require(canonical.startsWith(repository) && git(repository,"rev-parse","--is-bare-repository").let { it.code==0 && it.text=="false" })
            require(git(repository,"rev-parse","--verify","HEAD^{commit}").code==0) { "Existing unborn repository requires explicit recovery; never commit its index" }
            return repository
        }
        require(git(canonical,"rev-parse","--is-bare-repository").let { it.code!=0 || it.text!="true" }) { "Bare repository is not a working project directory" }
        // A failed lookup inside an existing/corrupt/unsafe repository is not permission to re-init.
        var ancestor:Path?=canonical
        while(ancestor!=null) {
            require(!Files.exists(ancestor.resolve(".git"),LinkOption.NOFOLLOW_LINKS)) { "Existing repository could not be verified at ${ancestor.resolve(".git")}" }
            ancestor=ancestor.parent
        }
        require(git(canonical,"init","--template=","--initial-branch=main").code==0)
        val tree=git(canonical,"hash-object","-t","tree","-w","--stdin").also {require(it.code==0)}.text
        require(tree.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}")))
        val commit=git(canonical,"commit-tree",tree,"-m","Initialize Writhelm project without staging user files").also {require(it.code==0)}.text
        require(commit.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}")))
        require(git(canonical,"update-ref","HEAD",commit,"0".repeat(commit.length)).code==0) { "Initial HEAD changed concurrently; no overwrite performed" }
        return canonical
    }
}

/** Real read-only native session observation using the test client's explicitly selected service
 * and service credential. No discovery of the user's main OpenCode, redirect, model call or body-
 * supplied location/actor. Authentication material is never returned in enrollment replies. */
internal class TestClientNativeSessions(origin:String,private val authorization:String) {
    private val origin=HttpProfile(origin).origin
    private val client=java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER)
        .connectTimeout(java.time.Duration.ofSeconds(5)).build()
    init {require(authorization.startsWith("Basic ") && authorization.length in 7..1024 && authorization.none { it=='\r' || it=='\n' })}
    fun observe(sessionId:String):LinuxTestClientEnrollment.ObservedTestClientSession {
        require(sessionId.matches(Regex("ses_[A-Za-z0-9_-]{1,240}")))
        val request=java.net.http.HttpRequest.newBuilder(java.net.URI("$origin/api/session/$sessionId"))
            .timeout(java.time.Duration.ofSeconds(8)).header("Authorization",authorization).GET().build()
        val response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofInputStream())
        val bytes=response.body().use {it.readNBytes(65537)}
        require(response.statusCode()==200 && bytes.size<=65536) { "Native session identity unavailable" }
        val envelope=StrictJson.decode(bytes,JsonLimits(bytes=65536,stringBytes=16384,collection=1024,tokens=8192)).fields("data")
        val info=envelope.getValue("data") as? JObject ?: error("native session shape")
        val fields=info.entries
        require(fields.getValue("id").string()==sessionId)
        val location=(fields.getValue("location") as? JObject)?.entries ?: error("native session location unavailable")
        val directory=Path.of(location.getValue("directory").string());require(directory.isAbsolute)
        return LinuxTestClientEnrollment.ObservedTestClientSession(sessionId,directory,
            fields["parentID"]?.takeIf {it!==JNull}?.string(),fields["fork"]?.let {it!==JNull} ?: false,
            fields["revert"]?.let {it!==JNull} ?: false)
    }
}
