// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.*
import dev.writhelm.core.work.*
import dev.writhelm.core.store.*
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.*

/** Actual canonical reserve/enrollment/bind, Git bytes and bidirectional HTTP; native SDK/profile
 * qualification is an explicit fixture, never operational/native writer fencing evidence. */
class NativeWorkerPreparationJourneyTest {
    @Test @Timeout(180) fun nativeReviewerPolicyShellEndpointProbeWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true,reviewerSurfaceChecks=true)
    }
    @Test @Timeout(180) fun registeredCandidateRemediationRejectsPriorChecksAndReviewWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true,remediationChecks=true)
    }
    @Test @Timeout(180) fun registeredDispatchDisplaysReadHoldAndResumesOriginalPlanWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true,dispatchChecks=true,readHoldChecks=true)
    }
    @Test @Timeout(180) fun nativeEnrolledRegisteredDispatchObservesReadinessAndMissingExecutionProfileWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true,dispatchChecks=true)
    }
    @Test @Timeout(180) fun nativeEnrolledCandidateRestartPreservesPassedRunAndJobWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true,"passed")
    }
    @Test @Timeout(180) fun nativeEnrolledCandidateRestartPreservesUnknownRunAndJobWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true,"unknown")
    }
    @Test @Timeout(180) fun nativeEnrolledControllerRegisteredCandidateToolRunsChecksWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true,true)
    }
    @Test @Timeout(180) fun enrolledStoreCanonicalExecutorResultRunsActualProjectChecksWithoutModelCalls() {
        LocalH1SetupTest().runEnrolledComposition(true)
    }
    @Test @Timeout(240) fun automaticReviewerUsesInstalledNativePreparationWithoutModelCalls()=candidateReviewServiceJourney(true)
    @Test @Timeout(180) fun authenticatedReviewIssuancePacketReportRestartAndRevocationWithoutModelCalls()=candidateReviewServiceJourney()
    @Test @Timeout(90) fun serviceIndependentlyObservesActualCandidateAssemblyAndCheckReceipts() {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"candidate-review-receipts-")
        val git=Path.of("/usr/bin/git").toRealPath();val pin=Digest.of(Files.readAllBytes(git))
        val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER"))).resolve("tests/candidate-review-receipts-client.js")
        val output=root.resolve("fixture.json")
        val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),script.toString(),root.toString(),git.toString(),pin.hex)
            .redirectOutput(output.toFile()).redirectError(root.resolve("fixture.log").toFile()).start()
        assertTrue(process.waitFor(60,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),"Actual assembly/check fixture failed; inspect $root")
        val frame=StrictJson.decode(Files.readAllBytes(output)).fields("projectId","assemblyId","testPlanId","checks","candidateId")
        val project=uuid(frame.getValue("projectId"));val assembly=uuid(frame.getValue("assemblyId"));val testPlan=uuid(frame.getValue("testPlanId"))
        val checks=frame.getValue("checks").list().map {
            val f=it.fields("checkId","command","safeRepeat","dependencies")
            DispatchCandidateCheck(f.getValue("checkId").string(),f.getValue("command").list().map {it.string()},false,emptyList())
        }
        val plan=DispatchPlanCandidate(project,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),emptyList(),checks)
        val observer=NativeCandidateReviewReceipts(root.resolve("native"))
        val observed=assertNotNull(observer.observe(project,assembly,testPlan,plan))
        assertEquals(uuid(frame.getValue("candidateId")),observed.candidate.retainedLocatorId)
        assertEquals(checks.map {it.checkId},observed.checks.map {it.checkId})
        assertEquals(observed,observer.observe(project,assembly,testPlan,plan))
        assertNull(observer.observe(java.util.UUID.randomUUID(),assembly,testPlan,plan))
        assertNull(observer.observe(project,assembly,java.util.UUID.randomUUID(),plan))
        assertNull(observer.observe(project,assembly,testPlan,plan.copy(candidateChecks=checks.map {it.copy(command=listOf("different-check"))})))
        val run=observed.checks.single().runId
        val receipt=root.resolve("native/candidate-test-result-${Digest.of(run.toString().toByteArray()).hex}.json")
        val original=Files.readAllBytes(receipt)
        Files.writeString(receipt,String(original).replace("\"passed\"","\"unknown\""))
        assertNull(observer.observe(project,assembly,testPlan,plan))
        Files.write(receipt,original)
        Files.setPosixFilePermissions(receipt,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"))
        assertNull(observer.observe(project,assembly,testPlan,plan))
        assertEquals("implemented\n",Files.readString(root.resolve("source/result.txt")))
    }
    @Test @Timeout(240) fun unifiedClientLauncherOwnsProvisioningServiceAndNativeRestart() {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"owned-client-launch-")
        val build=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER")))
        val plugin=Files.createDirectory(root.resolve("plugin"));val pluginSources=Files.createDirectories(plugin.resolve("build/src"))
        Files.list(build.resolve("src")).use {paths ->paths.forEach {if(Files.isRegularFile(it))Files.copy(it,pluginSources.resolve(it.fileName))}}
        Files.createSymbolicLink(plugin.resolve("node_modules"),build.parent.resolve("node_modules"))
        // Test-only driver of the actual installed tool registry. It supplies a synthetic call
        // context, not native model/tool-execution evidence, and never installs a profile or permit.
        Files.writeString(pluginSources.resolve("probe-server.js"),"""
            import primary from './primary-server.js';
            import fs from 'node:fs/promises';
            export default {...primary,async setup(ctx){
              const close=await primary.setup(ctx);let busy=false,done=false;
              const request=${StrictJson.encode(str(root.resolve("check-discovery-request.json").toString())).toString(Charsets.UTF_8)};
              const result=${StrictJson.encode(str(root.resolve("check-discovery-result.json").toString())).toString(Charsets.UTF_8)};
              const timer=setInterval(async()=>{
                if(busy||done)return;busy=true;
                try{
                  const {sessionID}=JSON.parse(await fs.readFile(request,'utf8'));
                  const tools=await ctx.tool.list();
                  const tool=tools.find(t=>t.name==='writhelm_project_checks');
                  if(!tool)return;
                  const value=await tool.execute({},{sessionID,messageID:'msg_model_free_probe',id:'call_model_free_probe',
                    signal:new AbortController().signal,progress:async()=>{}});
                  await fs.writeFile(result,JSON.stringify({syntheticCallContext:true,value}),{mode:0o600,flag:'wx'});done=true;
                }catch(e){if(e?.code!=='ENOENT'){
                  await fs.writeFile(result,JSON.stringify({held:true}),{mode:0o600,flag:'wx'}).catch(()=>{});done=true;
                }}finally{busy=false}
              },100);
              return async()=>{clearInterval(timer);await close?.()};
            }};
        """.trimIndent())
        Files.writeString(plugin.resolve("package.json"),"""{"name":"writhelm-owned-client-launch-test","type":"module","exports":{".":"./build/src/probe-server.js","./tui":"./build/src/primary-tui.js","./bootstrap":"./build/src/primary-bootstrap.js"}}""")
        fun hash(file:Path):Digest {
            val md=java.security.MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use {input ->val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;md.update(bytes,0,n)}}
            return Digest.parse(md.digest().joinToString(""){"%02x".format(it)})
        }
        val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE"))).toRealPath()
        val cli=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_CLI"))).toRealPath();val git=Path.of("/usr/bin/git").toRealPath()
        val projectRoot=Files.createDirectory(root.resolve("project"))
        val env=Path.of("/usr/bin/env").toRealPath()
        val approvedChecks=obj("formatVersion" to num(1),"platform" to str("linux"),"sourceRoot" to str(projectRoot.toString()),
            "envExecutable" to obj("path" to str(env.toString()),"sha256" to digest(hash(env))),"checks" to JArray(listOf(obj(
                "id" to str("project-node-test"),"executable" to obj("path" to str(node.toString()),"sha256" to digest(hash(node))),
                "args" to JArray(listOf(str("--test"))),"environment" to obj(),"timeoutMillis" to num(10000),"outputBytes" to num(32768)))))
        val profile=LinuxAutomaticClientProfile(root.resolve("client"),plugin,node,hash(node),cli,hash(cli),git,hash(git),
            PackagedClientContracts.load())
        val installed=AutomaticTestClientInstall.install(profile,Path.of(requireNotNull(System.getenv("WRITHELM_CLIENT_LAUNCHER"))).toRealPath(),listOf(approvedChecks))
        assertTrue(Files.isExecutable(installed))
        val wrapperBytes=Files.readAllBytes(installed)
        assertEquals(installed,AutomaticTestClientInstall.install(profile,Path.of(requireNotNull(System.getenv("WRITHELM_CLIENT_LAUNCHER"))).toRealPath()))
        assertContentEquals(wrapperBytes,Files.readAllBytes(installed))
        assertFails {AutomaticTestClientInstall.install(profile.copy(schemas=mapOf("work-command3" to "synthetic replacement")),
            Path.of(requireNotNull(System.getenv("WRITHELM_CLIENT_LAUNCHER"))).toRealPath())}
        assertFails {AutomaticTestClientInstall.install(profile.copy(root=root.resolve("bare-native-launcher")),cli)}
        assertFalse(Files.exists(root.resolve("bare-native-launcher")),"A bare native binary must not be installed as an unmanaged launcher")
        val unsafe=Files.createDirectory(root.resolve("unsafe-parent"))
        Files.setPosixFilePermissions(unsafe,java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"))
        try{
            assertFails {AutomaticTestClientInstall.install(profile.copy(root=unsafe.resolve("client")),
                Path.of(requireNotNull(System.getenv("WRITHELM_CLIENT_LAUNCHER"))).toRealPath())}
            assertFalse(Files.exists(unsafe.resolve("client")),"Unsafe parent must hold before creating an installation/owner store")
        }finally{Files.setPosixFilePermissions(unsafe,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))}
        val incomplete=Files.createDirectory(root.resolve("incomplete-plugin"))
        Files.copy(pluginSources.resolve("primary-server.js"),incomplete.resolve("main.js"))
        Files.copy(pluginSources.resolve("primary-bootstrap.js"),incomplete.resolve("bootstrap.js"))
        Files.writeString(incomplete.resolve("package.json"),"""{"type":"module","exports":{".":"./main.js","./bootstrap":"./bootstrap.js"}}""")
        assertFails {AutomaticTestClientInstall.install(profile.copy(root=root.resolve("incomplete-install"),plugin=incomplete),
            Path.of(requireNotNull(System.getenv("WRITHELM_CLIENT_LAUNCHER"))).toRealPath())}
        assertFalse(Files.exists(root.resolve("incomplete-install")),"Missing mandatory TUI export must hold before installation")
        val http=java.net.http.HttpClient.newHttpClient()
        fun request(client:LinuxAutomaticTestClient,method:String,path:String,body:JsonValue?=null):java.net.http.HttpResponse<String> {
            val registration=StrictJson.decode(Files.readAllBytes(client.nativeRegistration)) as JObject
            val auth="Basic "+Base64.getEncoder().encodeToString(("opencode:"+registration.entries.getValue("password").string()).toByteArray())
            val builder=java.net.http.HttpRequest.newBuilder(java.net.URI(client.nativeOrigin+path)).header("Authorization",auth)
                .header("x-opencode-directory",java.net.URLEncoder.encode(projectRoot.toString(),Charsets.UTF_8).replace("+","%20"))
                .timeout(java.time.Duration.ofSeconds(30))
            if(body!=null)builder.header("Content-Type","application/json")
            return http.send(builder.method(method,if(body==null)java.net.http.HttpRequest.BodyPublishers.noBody()
                else java.net.http.HttpRequest.BodyPublishers.ofByteArray(StrictJson.encode(body))).build(),java.net.http.HttpResponse.BodyHandlers.ofString())
        }
        var session="";var controller:java.util.UUID?=null;var origin="";var passiveBytes=ByteArray(0);var checkBytes=ByteArray(0)
        LinuxAutomaticTestClient.start(profile).use {client ->
            origin=client.nativeOrigin
            assertTrue(LinuxAutomaticTestClient.discover(profile),"Existing owned service/native process must be discoverable without opening another store")
            val refused=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI(client.serviceOrigin+"/internal/enrollment/v1"))
                .header("X-Writhelm-Enrollment-Proof",Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32)))
                .GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString())
            assertEquals(403,refused.statusCode(),"Readiness discovery must not bypass private admission")
            assertEquals(200,request(client,"GET","/api/agent").statusCode())
            val created=request(client,"POST","/api/session",obj("location" to obj("directory" to str(projectRoot.toString()))))
            assertEquals(200,created.statusCode())
            session=(StrictJson.decode(created.body().toByteArray()).fields("data").getValue("data") as JObject).entries.getValue("id").string()
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40)
            while(client.connectedControllers.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(100)
            assertEquals(1,client.connectedControllers.size,"Unified client launch activation held; inspect $root")
            controller=client.connectedControllers.single()
            Files.write(root.resolve("check-discovery-request.json"),StrictJson.encode(obj("sessionID" to str(session))))
            val discoveryResult=root.resolve("check-discovery-result.json")
            val discoveryDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(!Files.exists(discoveryResult)&&System.nanoTime()<discoveryDeadline)Thread.sleep(100)
            assertTrue(Files.exists(discoveryResult),"Installed registry discovery must complete without model calls")
            val discovery=(StrictJson.decode(Files.readAllBytes(discoveryResult)) as JObject).entries
            assertEquals(JBoolean(true),discovery["syntheticCallContext"],"Actual registered handler must admit the enrolled controller")
            val toolResult=(discovery.getValue("value") as JObject).entries
            val discovered=(StrictJson.decode(toolResult.getValue("content").string().toByteArray()) as JObject).entries
            assertEquals(JBoolean(true),discovered["available"])
            assertEquals("project-node-test",(discovered.getValue("checks").list().single() as JObject).entries.getValue("id").string())
            assertTrue(discovered.getValue("profileDigest").string().matches(Regex("[0-9a-f]{64}")))
            // Real installed native HTTP/process observation; deployment input only, not a
            // qualification registration and never permission to dispatch a worker/model call.
            val observerRoot=profile.root.resolve("private")
            val observerController=observerRoot.resolve("controllers/controller-$controller")
            val passive=assertNotNull(PassiveRuntimeLayout.read(observerController),"Automatic deployment must precede native controller activation")
            passiveBytes=passive.bytes
            val bootstrap=observerController.resolve("bootstrap")
            checkBytes=protectedRuntimeBytes(bootstrap,bootstrap.resolve("project-check-template-${Digest.of("current".toByteArray()).hex}.json"),1024*1024)
            val checkTemplate=StrictJson.decode(checkBytes).fields("formatVersion","platform","sourceRoot","envExecutable","checks","worktreeParent")
            assertEquals(projectRoot.toString(),checkTemplate.getValue("sourceRoot").string())
            assertEquals(passive.worktreeParent.toString(),checkTemplate.getValue("worktreeParent").string())
            assertEquals("project-node-test",checkTemplate.getValue("checks").list().single().fields("id","executable","args","environment","timeoutMillis","outputBytes").getValue("id").string())
            assertTrue(passive.worktreeParent.startsWith(profile.root.resolve("worktrees")))
            assertTrue(Files.list(passive.worktreeParent).use {it.findAny().isEmpty},"No worker worktree before a qualified assignment")
            assertFalse(Files.exists(observerController.resolve("profile-qualification/registry.json")),"Deployment must not fabricate qualification")
            val nativeInfo=StrictJson.decode(request(client,"GET","/api/info").body().toByteArray()) as JObject
            val pid=nativeInfo.entries.getValue("pid").long()
            val stat=Files.readString(Path.of("/proc/$pid/stat"));val start=stat.substring(stat.lastIndexOf(')')+1).trim().split(Regex("\\s+"))[19]
            val boot=Files.readString(Path.of("/proc/sys/kernel/random/boot_id")).trim()
            val epoch=Digest.of("[\"native-linux-process-v1\",\"${profile.cliDigest.hex}\",\"$boot\",$pid,\"$start\",\"${client.nativeOrigin}\"]".toByteArray()).hex
            LinuxNativeRuntimeEnvironment(client.nativeOrigin,client.nativeRegistration,projectRoot,plugin,observerController,
                PinnedNativeExecutable("2.2.5",profile.cliDigest),observerRoot).use { observer ->
                val observed=assertNotNull(observer.observe(epoch),"Actual installed native configuration observation held; inspect $root")
                assertEquals(observed,observer.observe(epoch),"Unchanged installed native environment must have stable observation")
                assertNull(observer.observe("0".repeat(64)),"A stale process epoch cannot observe a current environment")
            }
        }
        assertFalse(Files.exists(profile.root.resolve("state/opencode/service.json")),"Launcher must stop its own isolated native process")
        assertFalse(LinuxAutomaticTestClient.discover(profile))
        LinuxAutomaticTestClient.start(profile).use {client ->
            assertEquals(origin,client.nativeOrigin)
            assertEquals(200,request(client,"GET","/api/agent").statusCode())
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40)
            while(client.connectedControllers.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(100)
            assertEquals(setOf(controller),client.connectedControllers,"Launcher restart must recover the same controller")
            assertContentEquals(passiveBytes,PassiveRuntimeLayout.read(profile.root.resolve("private/controllers/controller-$controller"))!!.bytes,
                "Restart must reuse original passive deployment bytes")
            val bootstrap=profile.root.resolve("private/controllers/controller-$controller/bootstrap")
            assertContentEquals(checkBytes,protectedRuntimeBytes(bootstrap,bootstrap.resolve("project-check-template-${Digest.of("current".toByteArray()).hex}.json"),1024*1024),
                "Restart must reuse the enrolled approved project-check policy")
            val context=request(client,"GET","/api/session/$session/context")
            assertEquals(200,context.statusCode());assertEquals(emptyList(),StrictJson.decode(context.body().toByteArray()).fields("data").getValue("data").list())
        }
        val instanceDescriptor=Files.list(profile.root.resolve("private/instance")).use {paths ->
            paths.filter {it.fileName.toString().endsWith(".command")}.findFirst().orElseThrow()
        }
        val originalInstance=Files.readAllBytes(instanceDescriptor)
        val lifecycle=profile.root.resolve("lifecycle");LinuxConnectedCustody(lifecycle)
        fun heldLauncher(argument:String):Int {
            val builder=ProcessBuilder(installed.toString(),argument).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("lifecycle-refusal.log").toFile()))
            builder.environment().clear();builder.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","LANG" to "C.UTF-8","HOME" to profile.root.resolve("home").toString()))
            val child=builder.start();assertTrue(child.waitFor(20,TimeUnit.SECONDS));return child.exitValue()
        }
        for(name in listOf("service-owner.json","service-ready.json")){
            val unknown=lifecycle.resolve(name);val original="Explicit test-owned unknown lifecycle marker".toByteArray()
            Files.write(unknown,original,StandardOpenOption.CREATE_NEW)
            Files.setPosixFilePermissions(unknown,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
            assertEquals(2,heldLauncher(projectRoot.toString()),"Unknown lifecycle ownership must refuse before launching a UI/native service")
            assertEquals(2,heldLauncher("--stop-service"),"Unknown lifecycle ownership cannot authorize process termination")
            assertContentEquals(original,Files.readAllBytes(unknown))
            assertFalse(Files.exists(profile.root.resolve("state/opencode/service.json")))
            Files.move(unknown,root.resolve("preserved-$name")) // preserve only this exact test-created marker
        }
        for(name in listOf("automatic-ui-client.py","parallel-ui-client.py")){
            val target=root.resolve(name)
            javaClass.getResourceAsStream("/h1/$name").use {input ->Files.copy(checkNotNull(input),target)}
            Files.setPosixFilePermissions(target,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        }
        val acceptance=ProcessBuilder("/usr/bin/python3",root.resolve("parallel-ui-client.py").toString(),root.toString())
            .redirectErrorStream(true).redirectOutput(root.resolve("parallel-ui-driver.log").toFile()).start()
        assertTrue(acceptance.waitFor(100,TimeUnit.SECONDS),"Installed parallel UI acceptance timed out; inspect $root")
        assertEquals(0,acceptance.exitValue(),"Installed same-folder/first-view-exit acceptance held; inspect $root")
        assertFalse(Files.exists(profile.root.resolve("state/opencode/service.json")))
        assertFalse(Files.exists(profile.root.resolve("lifecycle/service-owner.json")))
        assertFalse(Files.exists(profile.root.resolve("lifecycle/service-ready.json")))
        assertContentEquals(originalInstance,Files.readAllBytes(instanceDescriptor),"Normal background lifetime must reuse original store/owner identities")
    }
    @Test @Timeout(240) fun automaticClientActivatesTwoProjectsWithoutAnyModelRequest() = automaticClientJourney(false)
    @Test @Timeout(300) fun automaticClientRestartsExistingGitProjectWithoutDuplicateEnrollment() = automaticClientJourney(true)
    private fun automaticClientJourney(restart:Boolean) {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"automatic-client-")
        val executable=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_CLI")))
        val adapterBuild=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER")))
        val privateRoot=root.resolve("private");LinuxConnectedCustody(privateRoot)
        fun directory(name:String)=Files.createDirectories(root.resolve(name),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")))
        val home=directory("home");val config=directory("config/opencode");directory("data");directory("state");directory("cache");directory("runtime")
        val plugin=directory("plugin");Files.createDirectory(plugin.resolve("src"))
        Files.list(adapterBuild.resolve("src")).use { paths ->paths.forEach { if(Files.isRegularFile(it))Files.copy(it,plugin.resolve("src").resolve(it.fileName)) } }
        Files.createSymbolicLink(plugin.resolve("node_modules"),adapterBuild.parent.resolve("node_modules"))
        Files.writeString(plugin.resolve("package.json"),"""{"name":"writhelm-automatic-native-test","type":"module","exports":{".":"./src/primary-server.js"}}""")
        val policyFile=config.resolve("opencode.json")
        fun port()=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
        val nativeOrigin="http://127.0.0.1:${port()}";val serviceOrigin="http://127.0.0.1:${port()}"
        val registration=root.resolve("state/opencode/service.json")
        val environment=mapOf("HOME" to home.toString(),"PATH" to "/usr/bin:/bin","LANG" to "C.UTF-8",
            "XDG_CONFIG_HOME" to root.resolve("config").toString(),"XDG_DATA_HOME" to root.resolve("data").toString(),
            "XDG_STATE_HOME" to root.resolve("state").toString(),"XDG_CACHE_HOME" to root.resolve("cache").toString(),
            "XDG_RUNTIME_DIR" to root.resolve("runtime").toString(),"OPENCODE_DESKTOP_TEST_ROOT" to root.toString(),
            "OPENCODE_DB" to root.resolve("data/opencode/test.db").toString(),"WRITHELM_PRIMARY_ROOT" to privateRoot.toString(),"OPENCODE_LOG_LEVEL" to "DEBUG")
        fun cli(vararg args:String):Int {
            val process=ProcessBuilder(executable.toString(),*args).directory(home.toFile()).redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("cli.log").toFile()))
                .redirectError(ProcessBuilder.Redirect.appendTo(root.resolve("cli.log").toFile()))
            process.environment().clear();process.environment().putAll(environment)
            val child=process.start();assertTrue(child.waitFor(35,TimeUnit.SECONDS));return child.exitValue()
        }
        val firstProject=directory("first-project");val secondProject=directory("second-project")
        val facts=SharedH1ControllerFacts()
        var instance=LinuxTestClientInstance.open(privateRoot.resolve("instance"),facts.options())
        val ownedStoreId=instance.storeId;val ownedAnchorProject=instance.anchorProjectId
        try {
        val provision=obj("root" to str(privateRoot.toString()),"forbiddenAreas" to JArray(listOf(str(firstProject.toString()),str(secondProject.toString()))),
            "storeId" to uuid(ownedStoreId),"serviceOrigin" to str(serviceOrigin),"nativeOrigin" to str(nativeOrigin),
            "nativeRegistrationFile" to str(registration.toString()),"clientPolicyFile" to str(policyFile.toString()),"plugin" to str(plugin.toString()),
            "schemas" to JObject(PackagedClientContracts.load().mapValues {str(it.value)}))
        fun provisionClient(){
            val code="const {readFileSync}=await import('node:fs');const {provisionAutomaticPrimary}=await import(process.argv[1]);await provisionAutomaticPrimary(JSON.parse(readFileSync(0,'utf8')))"
            val builder=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--input-type=module","-e",code,plugin.resolve("src/primary-bootstrap.js").toUri().toString())
                .redirectOutput(root.resolve("provision.log").toFile()).redirectError(ProcessBuilder.Redirect.appendTo(root.resolve("provision.log").toFile()))
            builder.environment().clear();builder.environment().putAll(environment)
            val child=builder.start();child.outputStream.use {it.write(StrictJson.encode(provision))}
            assertTrue(child.waitFor(35,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),"Trusted automatic provisioning failed; inspect private test log")
        }
        provisionClient()
        val bootstrapFile=privateRoot.resolve("bootstrap/automatic-bootstrap-${Digest.of("current".toByteArray()).hex}.json")
        val originalBootstrap=Files.readAllBytes(bootstrapFile)
        val proof=Base64.getUrlDecoder().decode((StrictJson.decode(originalBootstrap) as JObject).entries.getValue("enrollmentProof").string())
        Files.writeString(firstProject.resolve("owner.txt"),"preserve owner bytes\n")
        fun git(vararg args:String):String {
            val builder=ProcessBuilder("/usr/bin/git","-c","core.hooksPath=/dev/null","-C",firstProject.toString(),*args)
                .redirectError(root.resolve("git.stderr").toFile())
            builder.environment().clear();builder.environment().putAll(environment+mapOf("GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
            val child=builder.start();val output=child.inputStream.readNBytes(8192)
            assertTrue(child.waitFor(20,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),"Isolated project Git preparation failed")
            return output.decodeToString().trim()
        }
        if(restart){
            git("init","-b","main");git("add","owner.txt")
            git("-c","user.name=Owner fixture","-c","user.email=owner@example.invalid","commit","--no-gpg-sign","-m","Existing owner project")
            Files.writeString(firstProject.resolve("staged.txt"),"staged owner bytes\n");git("add","staged.txt")
            Files.writeString(firstProject.resolve("owner.txt"),"preserve owner bytes\n")
        }
        val originalHead=if(restart)git("rev-parse","HEAD") else null
        val originalIndex=if(restart)Files.readAllBytes(firstProject.resolve(".git/index")) else null
        var app=instance.app
            val nativeSessions:(String)->LinuxTestClientEnrollment.ObservedTestClientSession={ session ->
                val selected=StrictJson.decode(Files.readAllBytes(registration)) as JObject
                assertEquals(nativeOrigin,selected.entries.getValue("url").string())
                val authorization="Basic "+Base64.getEncoder().encodeToString(("opencode:"+selected.entries.getValue("password").string()).toByteArray())
                TestClientNativeSessions(nativeOrigin,authorization).observe(session)
            }
            val git=Path.of("/usr/bin/git")
            var broker=LinuxTestClientEnrollment(app,privateRoot.resolve("enrollment"),ownedStoreId,ownedAnchorProject,instance.ownerToken,
                LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git))),nativeSessions)
            var router=NativeRuntimeEndpointRouter();var manager=SharedH1ControllerRuntime(app,router,privateRoot.resolve("controllers"),facts)
            var endpoint=TestClientEnrollmentEndpoint(broker,proof,manager::configure)
            var http=HttpAdapter(WrithelmService(app),HttpProfile(serviceOrigin),enrollmentEndpoint=endpoint,nativeEndpoints=router)
            var server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(serviceOrigin).port){http.install(this)}.start(wait=false)
            try {
                assertEquals(0,cli("service","set","port",java.net.URI(nativeOrigin).port.toString()))
                assertEquals(0,cli("service","start"))
                assertTrue(Files.isRegularFile(registration),"Isolated native service registration missing; inspect private test log")
                val selected=StrictJson.decode(Files.readAllBytes(registration)) as JObject
                var authorization="Basic "+Base64.getEncoder().encodeToString(("opencode:"+selected.entries.getValue("password").string()).toByteArray())
                val client=java.net.http.HttpClient.newHttpClient()
                fun request(method:String,path:String,body:JsonValue?=null,location:Path?=null):java.net.http.HttpResponse<String> {
                    val builder=java.net.http.HttpRequest.newBuilder(java.net.URI(nativeOrigin+path)).header("Authorization",authorization)
                        .timeout(java.time.Duration.ofSeconds(30))
                    if(body!=null)builder.header("Content-Type","application/json")
                    if(location!=null)builder.header("x-opencode-directory",java.net.URLEncoder.encode(location.toString(),Charsets.UTF_8).replace("+","%20"))
                    return client.send(builder.method(method,if(body==null)java.net.http.HttpRequest.BodyPublishers.noBody()
                        else java.net.http.HttpRequest.BodyPublishers.ofByteArray(StrictJson.encode(body))).build(),java.net.http.HttpResponse.BodyHandlers.ofString())
                }
                // Linux-only operational evidence: the advertised loopback URL alone is not proof
                // that the real native process did not also bind the selected port on a wildcard.
                val serverInfo=request("GET","/api/info")
                assertEquals(200,serverInfo.statusCode())
                val pid=(StrictJson.decode(serverInfo.body().toByteArray()) as JObject).entries.getValue("pid").long()
                val descriptors=Path.of("/proc/$pid/fd")
                val sockets=Files.list(descriptors).use { paths ->paths.map { Files.readSymbolicLink(it).toString() }
                    .filter {it.startsWith("socket:[")}.map {it.removePrefix("socket:[").removeSuffix("]")}.toList().toSet() }
                val portHex=java.net.URI(nativeOrigin).port.toString(16).uppercase().padStart(4,'0')
                val listeners=listOf("tcp","tcp6").flatMap { protocol ->
                    Files.readAllLines(Path.of("/proc/$pid/net/$protocol")).drop(1).map {it.trim().split(Regex("\\s+"))}
                        .filter {it[3]=="0A" && it[9] in sockets && it[1].substringAfter(':')==portHex}.map {it[1]}
                }
                assertEquals(listOf("0100007F:$portHex"),listeners,"Native listener must bind only IPv4 loopback, not wildcard/external interfaces")
                val sessions=listOf(firstProject,secondProject).map { folder ->
                    assertEquals(200,request("GET","/api/agent",location=folder).statusCode(),"Native location/plugin activation failed")
                    val response=request("POST","/api/session",obj("location" to obj("directory" to str(folder.toString()))),folder)
                    assertEquals(200,response.statusCode(),"Native session creation failed; inspect private test log")
                    (StrictJson.decode(response.body().toByteArray()).fields("data").getValue("data") as JObject).entries.getValue("id").string()
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40)
                while(facts.connectedControllers().size<2 && System.nanoTime()<deadline)Thread.sleep(100)
                sessions.forEach { session ->
                    val enrolled=broker.session(session)
                    app.coordinationCapture(enrolled.token,enrolled.projectId)
                }
                assertEquals(2,facts.connectedControllers().size,"Automatic native session activation did not complete; inspect private test logs at $root")
                val thirdResponse=request("POST","/api/session",obj("location" to obj("directory" to str(firstProject.toString()))),firstProject)
                assertEquals(200,thirdResponse.statusCode())
                val third=(StrictJson.decode(thirdResponse.body().toByteArray()).fields("data").getValue("data") as JObject).entries.getValue("id").string()
                val thirdDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40)
                while(facts.connectedControllers().size<3 && System.nanoTime()<thirdDeadline)Thread.sleep(100)
                assertEquals(3,facts.connectedControllers().size,"Second session in the first project must activate independently; inspect $root")
                val allSessions=sessions+third
                val bindings=allSessions.map(broker::session)
                assertNotEquals(bindings[0].projectId,bindings[1].projectId);assertNotEquals(bindings[0].controllerId,bindings[1].controllerId)
                assertEquals(bindings[0].projectId,bindings[2].projectId)
                assertEquals(3,bindings.map {it.controllerId}.toSet().size)
                assertEquals(3,bindings.map {it.credentialId}.toSet().size)
                bindings.forEach {assertTrue(it.controllerId in facts.connectedControllers())}
                if(restart){
                    val before=app.coordinationCapture(bindings[0].token,bindings[0].projectId).basis
                    assertEquals(0,cli("service","stop"))
                    endpoint.close();manager.close();server.stop(0,5000);runBlocking {http.mcp.close()}
                    assertTrue(facts.connectedControllers().isEmpty(),"Old connected facts must not mask missing recovery")
                    instance.close()
                    instance=LinuxTestClientInstance.open(privateRoot.resolve("instance"),facts.options())
                    assertEquals(ownedStoreId,instance.storeId);assertEquals(ownedAnchorProject,instance.anchorProjectId)
                    app=instance.app
                    provisionClient()
                    assertContentEquals(originalBootstrap,Files.readAllBytes(bootstrapFile),"Provisioning must reuse the original private proof and descriptor")
                    broker=LinuxTestClientEnrollment(app,privateRoot.resolve("enrollment"),ownedStoreId,ownedAnchorProject,instance.ownerToken,
                        LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git))),nativeSessions)
                    router=NativeRuntimeEndpointRouter();manager=SharedH1ControllerRuntime(app,router,privateRoot.resolve("controllers"),facts)
                    endpoint=TestClientEnrollmentEndpoint(broker,proof,manager::configure)
                    http=HttpAdapter(WrithelmService(app),HttpProfile(serviceOrigin),enrollmentEndpoint=endpoint,nativeEndpoints=router)
                    server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(serviceOrigin).port){http.install(this)}.start(wait=false)
                    assertEquals(0,cli("service","start"))
                    val reopened=StrictJson.decode(Files.readAllBytes(registration)) as JObject
                    authorization="Basic "+Base64.getEncoder().encodeToString(("opencode:"+reopened.entries.getValue("password").string()).toByteArray())
                    listOf(firstProject,secondProject).forEach {assertEquals(200,request("GET","/api/agent",location=it).statusCode())}
                    val recoveryDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40)
                    while(facts.connectedControllers().size<3 && System.nanoTime()<recoveryDeadline)Thread.sleep(100)
                    val restored=allSessions.map(broker::session)
                    bindings.zip(restored).forEach { (old,current) ->
                        assertEquals(old.projectId,current.projectId);assertEquals(old.controllerId,current.controllerId)
                        assertEquals(old.principalId,current.principalId);assertEquals(old.credentialId,current.credentialId)
                    }
                    assertEquals(before,app.coordinationCapture(restored[0].token,restored[0].projectId).basis,"Reopen/replay must not publish duplicate operations")
                    assertEquals(originalHead,git("rev-parse","HEAD"));assertContentEquals(originalIndex,Files.readAllBytes(firstProject.resolve(".git/index")))
                    assertEquals(3,facts.connectedControllers().size,"Existing scoped runtimes must restore on reopen before any prompt/model request; inspect $root")
                }
                for(session in allSessions) {
                    val info=request("GET","/api/session/$session")
                    assertEquals(200,info.statusCode())
                    val permissions=(StrictJson.decode(info.body().toByteArray()).fields("data").getValue("data") as JObject).entries.getValue("permissions").list()
                    assertTrue(permissions.any { val p=it as JObject;p.entries["action"]==str("edit") && p.entries["effect"]==str("deny") })
                    val context=request("GET","/api/session/$session/context")
                    assertEquals(200,context.statusCode());assertEquals(emptyList(),StrictJson.decode(context.body().toByteArray()).fields("data").getValue("data").list())
                }
                assertEquals("preserve owner bytes\n",Files.readString(firstProject.resolve("owner.txt")))
            } finally {
                try {assertEquals(0,cli("service","stop"))}finally {
                    endpoint.close();manager.close();server.stop(0,5000);runBlocking {http.mcp.close()}
                }
            }
        }finally{instance.close()}
    }
    @Test @Timeout(180) fun observedRealGitDiffReachesServiceAndPlannerOnlyWithRetainedFixtureFact() {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"diff-result-journey-")
        val source=Files.createDirectory(root.resolve("source"));val worker=root.resolve("worker")
        fun git(dir:Path,vararg args:String):String {
            val process=ProcessBuilder("/usr/bin/git","-c","core.hooksPath=/dev/null","-C",dir.toString(),*args)
                .redirectError(root.resolve("git.stderr").toFile())
            process.environment().clear();process.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
            val p=process.start();val bytes=p.inputStream.readNBytes(8192);assertTrue(p.waitFor(20,TimeUnit.SECONDS))
            assertEquals(0,p.exitValue(),Files.readString(root.resolve("git.stderr")));return bytes.decodeToString().trim()
        }
        git(source,"init","-b","main");Files.writeString(source.resolve("result.txt"),"before\n");git(source,"add","result.txt")
        git(source,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","fixture base")
        val base=WSnapshot(id(59600),WorkGitOid("sha1",git(source,"rev-parse","HEAD")),WorkGitOid("sha1",git(source,"rev-parse","HEAD^{tree}")),null,id(59601))
        git(source,"worktree","add","--detach",worker.toString(),requireNotNull(base.commit).hex)
        Files.writeString(worker.resolve("result.txt"),"after\n");git(worker,"add","result.txt")
        git(worker,"-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","fixture result")
        val result=base.copy(commit=WorkGitOid("sha1",git(worker,"rev-parse","HEAD")),tree=WorkGitOid("sha1",git(worker,"rev-parse","HEAD^{tree}")),retainedLocatorId=id(59602))
        val witness=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("src/linux-git-witness.js").toUri()
        val script="""
            import {LinuxGitWitness} from ${StrictJson.encode(str(witness.toString())).decodeToString()};
            import {readFile} from 'node:fs/promises'; import {createHash} from 'node:crypto';
            let raw=''; for await (const c of process.stdin) raw+=c; const x=JSON.parse(raw);
            const git='/usr/bin/git', sha256=createHash('sha256').update(await readFile(git)).digest('hex');
            const proof=await new LinuxGitWitness({path:git,sha256}).observeDiff(x.worker,x.source,x.base,x.result,new AbortController().signal);
            process.stdout.write(proof.bytes.toString('base64'));
        """.trimIndent()
        fun observe(baseSnapshot:WSnapshot=base,resultSnapshot:WSnapshot=result):ByteArray {
            val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128","--input-type=module","-e",script)
                .redirectError(root.resolve("observer.stderr").toFile()).start()
            val payload=obj("worker" to str(worker.toString()),"source" to str(source.toString()),"base" to encodeWSnapshot(baseSnapshot),"result" to encodeWSnapshot(resultSnapshot))
            process.outputStream.use { it.write(StrictJson.encode(payload)) }
            val output=process.inputStream.readNBytes(2*1024*1024);assertTrue(process.waitFor(30,TimeUnit.SECONDS))
            if(process.exitValue()!=0)throw IllegalStateException("diff observation unavailable")
            return Base64.getDecoder().decode(output)
        }
        val bytes=Utf8Content.fromBytes(observe());assertTrue(bytes.text().contains("+after"))
        val f=JointFixture(base);val subject=WAssignmentSubject(f.assignment,0);val body=WorkBodyReference(bytes.digest,bytes.sizeBytes)
        var observations=0
        val control=NativeResultDiffProofControl { state,command ->
            observations++
            val actual=observe()
            NativeResultDiffProof(state.basis,project,WorkCodec().fingerprint(command),subject,f.intended.executionId,
                f.task,f.manifest,base,result,WorkBodyReference(Digest.of(actual),actual.size))
        }
        f.publishedActiveApp(diffControl=control).use { app ->
            val service=WrithelmService(app);val workerToken=token(f.workerKey)
            val retrospective=WorkCommand(id(59603),project,CRecordRetrospectiveOperation(CRecordRetrospective(WorkKey.Id(project,id(59604)),
                CRecordRetrospectiveData(f.task,subject,null,"no-significant-new-findings",emptyList()),emptyList())))
            assertEquals(200,service.command(workerToken,WorkCodec().encodeCommand(retrospective)).status)
            val state=app.coordinationCapture(plannerToken,project).work
            val retro=state.reference(WorkFamily.RETROSPECTIVES,WorkKey.Id(project,id(59604)))
            val data=CSubmitResultData(f.task,subject,f.manifest,listOf(result),body,listOf(id(96)),emptyList(),retro,emptyList())
            val command=WorkCommand(id(59605),project,CSubmitResultOperation(CSubmitResult(WorkKey.Id(project,id(59606)),data,
                listOf(CSubmitResultBodiesItem(bytes.digest,bytes.text())))))
            assertEquals(409,service.command(workerToken,WorkCodec().encodeCommand(command)).status)
            assertEquals(0,observations) // Git presence is not retention.
            f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+result) // explicitly SYNTHETIC retention
            Files.writeString(worker.resolve("result.txt"),"uncommitted tampering\n")
            assertEquals(409,service.command(workerToken,WorkCodec().encodeCommand(command.copy(requestKey=id(59608)))).status)
            assertTrue(observations>0)
            Files.writeString(worker.resolve("result.txt"),"after\n")
            val submitted=service.command(workerToken,WorkCodec().encodeCommand(command.copy(requestKey=id(59607))))
            assertEquals(200,submitted.status);assertTrue(observations>0)
            val row=app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.RESULTS,WorkKey.Id(project,id(59606)))
            val selector=WorkRead.Exact(project,row.identity,row.digest,null)
            val planner=service.read(plannerToken,WorkCodec().encodeRead(WorkRead.Body(project,bytes.digest,selector)))
            assertEquals(200,planner.status)
            assertEquals(bytes.text(),((planner.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string())
            val resultRead=service.read(plannerToken,WorkCodec().encodeRead(selector));assertEquals(200,resultRead.status)
            val resultRecord=WorkCodec().decodeRecord(StrictJson.encode((resultRead.value as JObject).entries.getValue("data")))
            val resultValue=(resultRecord.payload as WResultsPayload).value
            val current=app.coordinationCapture(plannerToken,project).work
            val taskRef=current.reference(WorkFamily.TASK_REVISIONS,resultValue.task)
            val taskRead=service.read(plannerToken,WorkCodec().encodeRead(WorkRead.Exact(project,taskRef.identity,taskRef.digest,null)))
            assertEquals(200,taskRead.status)
            val taskRecord=WorkCodec().decodeRecord(StrictJson.encode((taskRead.value as JObject).entries.getValue("data")))
            val recordedBase=requireNotNull((taskRecord.payload as WTaskRevisionsPayload).value.base).code.single()
            val independent=observe(recordedBase,resultValue.artifacts.single())
            assertEquals(resultValue.changes.digest,Digest.of(independent))
            assertEquals(resultValue.changes.byteLength,independent.size)
            assertContentEquals(bytes.bytes(),independent)
            val wrong=WorkRead.Exact(project,row.identity,Digest.of("wrong digest".toByteArray()),null)
            assertNotEquals(200,service.read(plannerToken,WorkCodec().encodeRead(wrong)).status)
        }
        assertEquals("before\n",Files.readString(source.resolve("result.txt")))
    }
    @Test @Timeout(180) fun reservedTaskGetsExactPassiveReceiverAndScopedCredentialWithoutModelWake()=cycle(false)
    @Test @Timeout(180) fun firstNormalPromptLostReplyRequiresHeldBoundaryThenActiveAndNeverWakesTwice()=cycle(true)
    @Test @Timeout(180) fun plannedPreparationPersistsAttemptBeforeRealReservationAndRecoversLostReceiver()=cycle(false,true)
    @Test @Timeout(180) fun plannedPreparationAndStartUseRetainedControllerAttempts()=cycle(true,true)
    @Test @Timeout(180) fun frontierUsesRealPreparationAndFullContextStart()=cycle(true,true,frontierStart=true)
    @Test @Timeout(180) fun producerResultAutomaticallyStartsDependentSuccessor()=cycle(true,true,diff=true,dependent=true)
    @Test @Timeout(180) fun frontierRestoresSuccessorAfterLostPublicationReply()=cycle(false,true,true)
    @Test @Timeout(180) fun frontierRunsTwoIndependentPreparedWorktreesConcurrently()=cycle(false,true,false,true)
    @Test @Timeout(180) fun frontierHoldsConflictingClaimWithoutRetryStorm()=cycle(false,true,false,true,true)
    @Test @Timeout(180) fun actualNativeDiffTransportSuppliesResultFactAndPlannerReadsReport()=cycle(true,true,false,false,false,true)
    @Test @Timeout(180) fun actualNativeResultPreflightHoldsWithoutEffectQualification()=cycle(true,true,false,false,false,true,true)
    private fun cycle(start:Boolean,planned:Boolean=false,frontier:Boolean=false,parallel:Boolean=false,conflict:Boolean=false,diff:Boolean=false,holdRetention:Boolean=false,frontierStart:Boolean=false,dependent:Boolean=false) {
        val root=Files.createTempDirectory(Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_TEST_ROOT"))),"passive-journey-")
        val source=Files.createDirectory(root.resolve("source"));val parent=Files.createDirectory(root.resolve("areas"),
            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")))
        fun git(vararg args:String):String {
            val process=ProcessBuilder("/usr/bin/git","-c","core.hooksPath=/dev/null","-C",source.toString(),*args).redirectError(root.resolve("git.stderr").toFile())
            process.environment().clear();process.environment().putAll(mapOf("PATH" to "/usr/bin:/bin","GIT_CONFIG_GLOBAL" to "/dev/null","GIT_CONFIG_NOSYSTEM" to "1"))
            val p=process.start();val bytes=p.inputStream.readNBytes(1048577);assertTrue(bytes.size<=1048576,"fixture Git output exceeds bound");assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),Files.readString(root.resolve("git.stderr")))
            return bytes.decodeToString().trim()
        }
        git("init","-b","main");Files.writeString(source.resolve("counter.txt"),"counter=0\n");git("add","counter.txt")
        git("-c","user.name=Fixture","-c","user.email=fixture@example.invalid","commit","--no-gpg-sign","-m","Synthetic counter baseline")
        val f=ReviewAppFixture();val codec=WorkCodec()
        val snapshot=WSnapshot(id(59500),WorkGitOid("sha1",git("rev-parse","HEAD")),WorkGitOid("sha1",git("rev-parse","HEAD^{tree}")),null,id(59501))
        val original=f.base.work.getValue(project);val base=requireNotNull(original.task(f.task).base).copy(code=listOf(snapshot))
        val records=original.records.values.map { row ->when(val payload=row.payload){
            is WTaskRevisionsPayload ->row.copy(payload=WTaskRevisionsPayload(payload.value.copy(base=base,
                kind=if(frontier||parallel||frontierStart||dependent)"implementation" else payload.value.kind,
                approvedDesign=if(frontier||parallel||frontierStart||dependent)listOf(revision(90).x()) else payload.value.approvedDesign)))
            is WInputManifestsPayload ->row.copy(payload=WInputManifestsPayload(payload.value.copy(base=base)))
            else ->row
        } }
        val secondTask=f.task.copy(taskId=id(59510));val secondPlan=f.plan.copy(id=id(59511));val secondManifest=WorkKey.Id(project,id(59512));val secondResource=WorkKey.Resource(project,id(59513))
        val slowTask=f.task.copy(taskId=id(59520));val slowPlan=f.plan.copy(id=id(59521));val slowManifest=WorkKey.Id(project,id(59522))
        val extras=if(!parallel&&!dependent)emptyList() else {
            val first=(records.single { it.identity.family==WorkFamily.TASK_REVISIONS }.payload as WTaskRevisionsPayload).value
            val verify=(records.single { it.identity.family==WorkFamily.VERIFICATION_PLANS }.payload as WVerificationPlansPayload).value
            val input=(records.single { it.identity.family==WorkFamily.INPUT_MANIFESTS }.payload as WInputManifestsPayload).value
            listOf(WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,secondTask),WTaskRevisionsPayload(first.copy(verificationPlan=secondPlan,
                dependencies=if(dependent)listOf(WDependency(f.task,"implemented",null,emptyList(),"producer result")) else first.dependencies))),
                WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,secondPlan),WVerificationPlansPayload(verify.copy(task=secondTask))),
                WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,secondManifest),WInputManifestsPayload(input.copy(task=secondTask))))+
                if(dependent)listOf(
                    WorkRecord(WorkRecordIdentity(WorkFamily.TASK_REVISIONS,slowTask),WTaskRevisionsPayload(first.copy(verificationPlan=slowPlan))),
                    WorkRecord(WorkRecordIdentity(WorkFamily.VERIFICATION_PLANS,slowPlan),WVerificationPlansPayload(verify.copy(task=slowTask))),
                    WorkRecord(WorkRecordIdentity(WorkFamily.INPUT_MANIFESTS,slowManifest),WInputManifestsPayload(input.copy(task=slowTask)))) else emptyList()
        }
        val work=WorkState.restore(project,records+extras,emptyList(),modelVersion=if(planned)4 else 2);val manifest=work.reference(WorkFamily.INPUT_MANIFESTS,f.manifestKey)
        val packets=if(parallel)setOf(manifest,work.reference(WorkFamily.INPUT_MANIFESTS,secondManifest)) else setOf(manifest)
        f.runtimeFacts=f.runtimeFacts.copy(retainedSnapshots=f.runtimeFacts.retainedSnapshots+snapshot,verifiedPackets=packets,
            approvedDecisions=if(frontier||parallel||frontierStart||dependent)setOf(revision(90).x()) else f.runtimeFacts.approvedDecisions)
        fun port()=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val origin="http://127.0.0.1:${port()}";val nativeOrigin="http://127.0.0.1:${port()}"
        val proof=ByteArray(32).also { SecureRandom().nextBytes(it) };val nativeProof=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val storeRoot=directory();var actualAssignment:java.util.UUID?=null
        var savedResult:WorkReference?=null;var savedReport:String?=null
        var diagnosticState:H1BoundState?=null
        HttpConnectedNativeControl(nativeOrigin,nativeProof).use { control ->
            val reservations=java.util.concurrent.atomic.AtomicInteger()
            val original=H1RuntimeFacts { state,command ->
                diagnosticState=state
                val facts=if(frontier && command.operation is CReserveAssignmentOperation && reservations.getAndIncrement()==0)
                    f.runtimeFacts.copy(verifiedPackets=emptySet()) else f.runtimeFacts
                val results=state.work.getValue(project).records.values.mapNotNull { (it.payload as? WResultsPayload)?.value }
                val observed=if(dependent)facts.copy(retainedSnapshots=facts.retainedSnapshots+results.flatMap { it.artifacts },
                    submittedDiffs=facts.submittedDiffs+results.map { WorkSubmittedDiff(it.task,it.artifacts,it.changes) },
                    verifiedPackets=state.work.getValue(project).records.values.filter { it.payload is WInputManifestsPayload }
                        .map { state.work.getValue(project).reference(WorkFamily.INPUT_MANIFESTS,it.identity.key) }.toSet()) else facts
                H1ObservedFacts(state.basis,observed,f.observations.qualifiedProfiles,
                    NativeExecutionAdmission.expected(state,command)) // explicit synthetic current-operation observer
            }
            // Explicit synthetic qualification; the retained ref and native proof are real fixture effects.
            val retained=NativeResultRetentionFacts(NativeInitialFacts(NativePreparationFacts(original,control,"a".repeat(64)),control,"a".repeat(64)),
                control,ResultRetentionQualification { _,_->diff })
            val observer=NativeResultDiffFacts(retained,
                NativeResultDiffTransportProof(control,"a".repeat(64)))
            val options=H1Options(if(dependent)ReservationProofFacts(observer,DeclaredPrebindingInputVerifier(observer)) else observer,nativeSafety=NativeRuntimeSafety { state,s ->s.generation==0L &&
                     state.work.getValue(project).assignment(s.assignmentId).let { it.task.taskId in setOf(f.task.taskId,secondTask.taskId,slowTask.taskId) && it.intendedExecution.controllerId==f.controller } },
                    workModelVersion=if(planned)4 else 2,
                     retentionEffectQualification=RetentionEffectQualification { _,_,_,_,_->diff&&!holdRetention },
                     candidateAssemblyProof=control::candidateAssemblyProof) // synthetic fixture qualification only
            H1Application.initialize(storeRoot,storeId,H1Genesis(f.base.core,f.base.authority.records(),mapOf(project to work)),options).use { app ->
                val endpoint=WrithelmService(app).nativeEndpoint(project,f.controller,f.intended.nativeHostId,proof)
                val adapter=HttpAdapter(WrithelmService(app),HttpProfile(origin),nativeEndpoint=endpoint)
                val server=embeddedServer(CIO,host="127.0.0.1",port=java.net.URI(origin).port) { adapter.install(this) }.start(wait=false)
                try{
                    val reserve=f.reserveCommand().copy(operation=CReserveAssignmentOperation((f.reserveCommand().operation as CReserveAssignmentOperation).value.copy(manifest=manifest)))
                    val preparationId=id(59504);val planId=id(59505)
                    val planRef=if(planned) {
                        val entries=listOf(DispatchPlanEntry(f.task,manifest,f.profile,listOf(f.resource),preparationId,id(59506)))+
                             if(parallel||dependent)listOf(DispatchPlanEntry(secondTask,work.reference(WorkFamily.INPUT_MANIFESTS,secondManifest),f.profile,listOf(if(conflict)f.resource else secondResource),id(59514),id(59515),
                                 if(dependent)listOf(DispatchFutureDependency(f.task,"implemented")) else emptyList())) else emptyList()
                         val allEntries=if(dependent)entries+DispatchPlanEntry(slowTask,work.reference(WorkFamily.INPUT_MANIFESTS,slowManifest),f.profile,
                             listOf(WorkKey.Resource(project,id(59523))),id(59524),id(59525)) else entries
                         val checks=if(dependent)listOf(
                             DispatchCandidateCheck("whole-candidate",listOf(requireNotNull(System.getenv("WRITHELM_NODE")),"-e",
                                 "process.stdout.write('fixture test output\\n'); process.exit(Number(process.env.FIXTURE_EXIT))"),true,emptyList()),
                             DispatchCandidateCheck("whole-tree",listOf("/usr/bin/git","ls-tree","--name-only","{candidateCommit}"),false,listOf("whole-candidate"))) else emptyList()
                         val plan=DispatchPlanCandidate(project,planId,f.controller,allEntries,checks)
                        val result=app.execute(plannerToken,DispatchPlanCommandCodec().encode(DispatchPlanCommand(id(59507),plan)))
                        assertIs<dev.writhelm.core.store.LogicalOutcome.Success>(assertIs<dev.writhelm.core.store.CommandResult.Settled>(result).receipt.outcome)
                        identityCodec.encodeReference(app.coordinationCapture(plannerToken,project).work.reference(WorkFamily.DISPATCH_PLANS,WorkKey.Id(project,planId))).decodeToString()
                    } else ""
                    val cfg=obj("start" to JBoolean(start),"planned" to JBoolean(planned),"frontier" to JBoolean(frontier),"parallel" to JBoolean(parallel),"conflict" to JBoolean(conflict),"diff" to JBoolean(diff),"holdRetention" to JBoolean(holdRetention),"planId" to uuid(planId),"preparationId" to uuid(preparationId),"startId" to uuid(id(59506)),"planRefJSON" to str(planRef),"root" to str(root.toString()),"sourceRoot" to str(source.toString()),"worktreeParent" to str(parent.toString()),"repositoryId" to uuid(snapshot.repositoryId),
                        "origin" to str(origin),"transportOrigin" to str(nativeOrigin),"proof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(proof)),
                        "transportProof" to str(Base64.getUrlEncoder().withoutPadding().encodeToString(nativeProof)),"storeId" to uuid(storeId),"projectId" to uuid(project),
                        "controllerId" to uuid(f.controller),"hostId" to uuid(f.intended.nativeHostId),"principalId" to uuid(planner),"credentialId" to uuid(plannerKey),"token" to str(plannerToken),
                        "assignmentId" to uuid(f.assignment),"reserveJSON" to str(codec.encodeCommand(reserve).decodeToString()),
                        "workerPrincipalId" to uuid(f.worker),"workerCredentialId" to uuid(f.workerKey),"delegationId" to uuid(f.delegation),"enrollKey" to uuid(id(59502)),"bindKey" to uuid(id(59503)))
                    val script=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_JS"))).resolve("tests/worker-preparation-client.js")
                    val process=ProcessBuilder(requireNotNull(System.getenv("WRITHELM_NODE")),"--max-old-space-size=128",script.toString()).redirectError(root.resolve("client.stderr").toFile()).start()
                    process.outputStream.use { it.write(StrictJson.encode(obj(*cfg.entries.map { it.key to it.value }.toTypedArray(),"frontierStart" to JBoolean(frontierStart),"dependent" to JBoolean(dependent)),65536)) }
                    val output=process.inputStream.readNBytes(4097);assertTrue(process.waitFor(120,TimeUnit.SECONDS))
                    if(dependent && process.exitValue()!=0) {
                        val state=requireNotNull(diagnosticState)
                        state.work.getValue(project).records.values.forEach { row ->
                            val assignment=(row.payload as? WAssignmentsPayload)?.value
                            if(assignment?.task?.taskId==secondTask.taskId)
                                NativeAssignmentPacket.render(state,project,WAssignmentSubject((row.identity.key as WorkKey.Id).id,assignment.executionGeneration))
                        }
                    }
                    assertEquals(0,process.exitValue(),Files.readString(root.resolve("client.stderr")))
                    val result=(StrictJson.decode(output) as JObject).entries
                    assertEquals(JBoolean(true),result.getValue("passiveAndBound"));assertEquals(if(dependent)3L else if(start)1L else 0L,result.getValue("wakes").long())
                    actualAssignment=uuid(result.getValue("assignmentId"))
                    val a=app.coordinationCapture(plannerToken,project).work.assignment(actualAssignment!!)
                    assertEquals(if(start)"active" else "bound",a.state);assertNotNull(a.binding);assertNotNull(a.delegationId)
                    if(dependent) {
                        val current=app.coordinationCapture(plannerToken,project).work
                        val child=current.records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }.single { it.task.taskId==secondTask.taskId }
                        assertEquals(secondTask.copy(revision=1),child.task);assertEquals("active",child.state)
                        assertNotEquals(a.workspaceId,child.workspaceId)
                        assertEquals(1,current.records.values.count { (it.payload as? WDispatchClosurePayload)?.automatic==true })
                        val slow=current.records.values.mapNotNull { (it.payload as? WAssignmentsPayload)?.value }.single { it.task==slowTask }
                        assertEquals("active",slow.state)
                        assertEquals(setOf(f.task,secondTask.copy(revision=1),slowTask),
                            current.records.values.mapNotNull { (it.payload as? WResultsPayload)?.value?.task }.toSet())
                    }
                    if(planned) {
                        val current=app.coordinationCapture(plannerToken,project).work
                        val attempt=(current.record(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,preparationId)).payload as WDispatchAttemptPayload).value
                        if(!conflict)assertEquals(a.task,(attempt.reservation.operation as CReserveAssignmentOperation).value.task)
                        if(!frontier&&!conflict)assertEquals(a.workspaceId,(attempt.reservation.operation as CReserveAssignmentOperation).value.workspaceId)
                        else assertEquals(2,current.records.values.count { it.payload is WDispatchAttemptPayload })
                        if(parallel) {
                            assertEquals(2,current.records.values.count { it.payload is WDispatchAttemptPayload })
                            assertEquals(if(conflict)1 else 2,current.records.values.count { (it.payload as? WAssignmentsPayload)?.value?.state=="bound" })
                            assertEquals(if(conflict)1 else 2,current.records.values.count { (it.payload as? WClaimsPayload)?.value?.state=="held" })
                        }
                    }
                    assertEquals("counter=0\n",Files.readString(source.resolve("counter.txt")))
                    if(diff && holdRetention) {
                        assertTrue(app.coordinationCapture(plannerToken,project).work.records.values.none { it.payload is WResultsPayload })
                    } else if(diff) {
                        val resultId=uuid(result.getValue("resultId"));val work=app.coordinationCapture(plannerToken,project).work
                        val ref=work.reference(WorkFamily.RESULTS,WorkKey.Id(project,resultId));val record=(work.resolve(ref).payload as WResultsPayload).value
                        val read=WrithelmService(app).read(plannerToken,codec.encodeRead(WorkRead.Body(project,record.changes.digest,WorkRead.Exact(project,ref.identity,ref.digest,null))))
                        assertEquals(200,read.status)
                        savedResult=ref; savedReport=((read.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string()
                        assertTrue(requireNotNull(savedReport).contains("+counter=1"))
                        // Planner-side bearing check against actual retained Git objects, independent
                        // of the worker's success label and the adapter's reported diff digest.
                        val actualDiff=git("diff","--no-ext-diff","--no-textconv","--no-color","--binary","--full-index",
                            requireNotNull(snapshot.commit).hex,requireNotNull(record.artifacts.single().commit).hex,"--")
                        assertEquals(actualDiff,requireNotNull(savedReport).trim())
                        assertEquals(record.changes.digest,Digest.of(requireNotNull(savedReport).toByteArray()))
                        assertEquals(record.changes.byteLength,requireNotNull(savedReport).toByteArray().size)
                        if(dependent) {
                            val inputs=app.candidateAssemblyInputs(plannerToken,project,f.controller,listOf(ref))
                            assertEquals(ref,inputs.single().reference)
                            assertEquals(record.artifacts.single(),inputs.single().result)
                            assertFailsWith<AppAccessFault> { app.candidateAssemblyInputs(token(f.workerKey),project,f.controller,listOf(ref)) }
                            assertFailsWith<WorkFault> { app.candidateAssemblyInputs(plannerToken,project,f.controller,listOf(ref,ref)) }
                        }
                    }
                }finally{runBlocking { adapter.mcp.close() };server.stop(0,5000);endpoint.close()}
            }
            if(planned) {
                H1Application.open(storeRoot,storeId,options).use { app ->
                    val current=app.coordinationCapture(plannerToken,project).work
                    val attempt=(current.record(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,id(59504))).payload as WDispatchAttemptPayload).value
                    if(!frontier&&!conflict)assertEquals(actualAssignment,(attempt.reservation.operation as CReserveAssignmentOperation).value.assignmentId)
                    assertEquals(if(start)"active" else "bound",current.assignment(actualAssignment!!).state)
                    if(diff && holdRetention) {
                        assertTrue(current.records.values.none { it.payload is WResultsPayload })
                    } else if(diff) {
                        val ref=requireNotNull(savedResult)
                        assertEquals(ref,current.reference(WorkFamily.RESULTS,ref.identity.key))
                        val record=(current.resolve(ref).payload as WResultsPayload).value
                        val selector=WorkRead.Exact(project,ref.identity,ref.digest,null)
                        val service=WrithelmService(app)
                        assertEquals(200,service.read(plannerToken,codec.encodeRead(selector)).status)
                        val read=service.read(plannerToken,codec.encodeRead(WorkRead.Body(project,record.changes.digest,selector)))
                        assertEquals(200,read.status)
                        assertEquals(savedReport,((read.value as JObject).entries.getValue("data") as JObject).entries.getValue("body").string())
                    }
                    if(parallel)assertEquals(if(conflict)1 else 2,current.records.values.count { (it.payload as? WAssignmentsPayload)?.value?.state=="bound" })
                }
                if(!conflict)RuntimeOwner.acquire(storeRoot).use { owner ->
                    val git=NativeGit(owner);git.qualify();git.verifyRepository()
                    fun created(path:String)=git.call(listOf("log","--diff-filter=A","--format=%H","--",path)).checked().decodeToString().trim().also { assertTrue(it.matches(Regex("[0-9a-f]{40}"))) }
                    val attemptPath=identityCodec.recordPath(WorkRecordIdentity(WorkFamily.DISPATCH_ATTEMPTS,WorkKey.Id(project,id(59504))))
                    val assignmentPath=identityCodec.recordPath(WorkRecordIdentity(WorkFamily.ASSIGNMENTS,WorkKey.Id(project,actualAssignment!!)))
                    val earlier=created(attemptPath);val later=created(assignmentPath)
                    assertNotEquals(earlier,later)
                    assertEquals(0,git.call(listOf("merge-base","--is-ancestor",earlier,later)).exit)
                }
            }
        }
    }
}
