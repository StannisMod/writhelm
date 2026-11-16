// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Digest
import dev.writhelm.service.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import java.nio.file.*
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Test-only native client/service owner. Production enrollment, manager and registered handlers;
 * no model calls. Handler call context is deliberately synthetic and recorded as such. */
internal class InstalledCompositionHarness(private val root:Path,private val workspace:Path,
    private val instance:LinuxTestClientInstance,private val checks:AutomaticProjectCheckDeployment):AutoCloseable {
    private val privateRoot=root.resolve("private")
    private val cli=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_CLI"))).toRealPath()
    private val node=Path.of(requireNotNull(System.getenv("WRITHELM_NODE"))).toRealPath()
    private val build=Path.of(requireNotNull(System.getenv("WRITHELM_NATIVE_ADAPTER")))
    private val plugin=root.resolve("plugin")
    private val registration=root.resolve("state/opencode/service.json")
    private val shared=SharedH1ControllerFacts()
    private val trustedCaptures=java.util.concurrent.atomic.AtomicInteger()
    val trustedFacts=H1RuntimeFacts {state,command->
        trustedCaptures.incrementAndGet();shared.capture(state,command)
    }
    fun trustedCaptureCount()=trustedCaptures.get()
    private val configurationReads=java.util.concurrent.atomic.AtomicInteger()
    private val configurationSuccesses=java.util.concurrent.atomic.AtomicInteger()
    fun qualificationObserverEvidence(controller:java.util.UUID):JsonValue {
        val registry=privateRoot.resolve("controllers/controller-$controller/profile-qualification/registry.json")
        return obj("configurationObservationCalls" to num(configurationReads.get()),
            "configurationObservationSuccesses" to num(configurationSuccesses.get()),
            "qualificationRegistryPresent" to JBoolean(Files.exists(registry)),
            "requiredExecutionEvidence" to JArray(LinuxRegisteredProfileFacts.requiredChecks.sorted().map(::str)))
    }
    private val router=NativeRuntimeEndpointRouter()
    private val git=Path.of("/usr/bin/git").toRealPath()
    private fun port()=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use {it.localPort}
    private val nativePort=port()
    private val nativeOrigin="http://127.0.0.1:$nativePort"
    private val servicePort=port()
    private val serviceOrigin="http://127.0.0.1:$servicePort"
    private val env=mapOf("HOME" to root.resolve("home").toString(),"PATH" to "/usr/bin:/bin","LANG" to "C.UTF-8",
        "XDG_CONFIG_HOME" to root.resolve("config").toString(),"XDG_DATA_HOME" to root.resolve("data").toString(),
        "XDG_STATE_HOME" to root.resolve("state").toString(),"XDG_CACHE_HOME" to root.resolve("cache").toString(),
        "XDG_RUNTIME_DIR" to root.resolve("runtime").toString(),"OPENCODE_DESKTOP_TEST_ROOT" to root.toString(),
        "OPENCODE_DB" to root.resolve("data/native.db").toString(),"WRITHELM_PRIMARY_ROOT" to privateRoot.toString())
    private val http=java.net.http.HttpClient.newHttpClient()
    val broker=LinuxTestClientEnrollment(instance.app,privateRoot.resolve("enrollment"),instance.storeId,instance.anchorProjectId,
        instance.ownerToken,LinuxTestClientRepository(git,Digest.of(Files.readAllBytes(git)))) {session->
        TestClientNativeSessions(nativeOrigin,authorization()).observe(session)
    }
    private val passive=AutomaticPassiveDeployment(root.resolve("worktrees"),privateRoot,git,Digest.of(Files.readAllBytes(git)))
    private val manager=SharedH1ControllerRuntime(instance.app,router,privateRoot.resolve("controllers"),shared,
        configuration={source,area->
            val observer=LinuxNativeRuntimeEnvironment(nativeOrigin,registration,source,plugin,area,
                PinnedNativeExecutable("2.2.5",Digest.parse("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050")),privateRoot)
            object:RuntimeConfigurationObservation {
                override fun observe(epoch:String):Digest? {
                    configurationReads.incrementAndGet()
                    return observer.observe(epoch).also {if(it!=null)configurationSuccesses.incrementAndGet()}
                }
                override fun close()=observer.close()
            }
        },
        provisionPassive={binding,area->
            passive.provision(binding.projectId,binding.controllerId,binding.workRoot,area)
            checks.provision(binding.workRoot,area,requireNotNull(PassiveRuntimeLayout.read(area)).worktreeParent)
        })
    private var enrollment:TestClientEnrollmentEndpoint?=null
    private var server:EmbeddedServer<CIOApplicationEngine,CIOApplicationEngine.Configuration>?=null
    private var started=false
    private val readAdmissionFault=java.util.concurrent.atomic.AtomicBoolean(false)
    private val injectedReadRefusals=java.util.concurrent.atomic.AtomicInteger()
    fun holdReads(held:Boolean){readAdmissionFault.set(held)}
    fun readRefusals()=injectedReadRefusals.get()
    private fun authorization():String {
        val f=(StrictJson.decode(Files.readAllBytes(registration)) as JObject).entries
        return "Basic "+Base64.getEncoder().encodeToString(("opencode:"+f.getValue("password").string()).toByteArray())
    }
    private fun cli(vararg args:String){
        val builder=ProcessBuilder(cli.toString(),*args).directory(root.resolve("home").toFile())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("native-cli.log").toFile()))
        builder.environment().clear();builder.environment().putAll(env)
        val child=builder.start();assertTrue(child.waitFor(35,TimeUnit.SECONDS));assertEquals(0,child.exitValue())
    }
    private fun request(method:String,path:String,body:JsonValue?=null):JsonValue {
        val builder=java.net.http.HttpRequest.newBuilder(java.net.URI(nativeOrigin+path)).header("Authorization",authorization())
            .header("x-opencode-directory",java.net.URLEncoder.encode(workspace.toString(),Charsets.UTF_8).replace("+","%20"))
            .timeout(java.time.Duration.ofSeconds(30))
        if(body!=null)builder.header("Content-Type","application/json")
        val reply=http.send(builder.method(method,body?.let {java.net.http.HttpRequest.BodyPublishers.ofByteArray(StrictJson.encode(it))}
            ?: java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200,reply.statusCode());return StrictJson.decode(reply.body())
    }
    fun start():String {
        assertFalse(Files.exists(workspace.resolve(".git")),"This scenario starts with an empty, non-Git project")
        // Actual launcher preparation primitive, before the first native project discovery.
        prepareAutomaticClientProject(workspace,privateRoot,git,Digest.of(Files.readAllBytes(git)))
        val cliHash=java.security.MessageDigest.getInstance("SHA-256")
        Files.newInputStream(cli).use {input->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;cliHash.update(buffer,0,n)}}
        assertEquals("8d060e1d2fbb9bb44cf09ae6ed1f3f6a7cff070b5227e2f90970132b23a92050",cliHash.digest().joinToString(""){"%02x".format(it)})
        for(name in listOf("home","config/opencode","data","state/opencode","cache","runtime","plugin/build/src"))LinuxConnectedCustody(root.resolve(name))
        Files.list(build.resolve("src")).use {paths->paths.forEach {if(Files.isRegularFile(it))Files.copy(it,plugin.resolve("build/src").resolve(it.fileName))}}
        Files.createSymbolicLink(plugin.resolve("node_modules"),build.parent.resolve("node_modules"))
        Files.writeString(plugin.resolve("package.json"),"""{"name":"writhelm-composition-test","type":"module","exports":{".":"./build/src/composition-probe.js","./tui":"./build/src/primary-tui.js","./bootstrap":"./build/src/primary-bootstrap.js"}}""")
        val requestFile=StrictJson.encode(str(root.resolve("tool-request.json").toString())).decodeToString()
        val resultFile=StrictJson.encode(str(root.resolve("tool-result.json").toString())).decodeToString()
        val provenanceRoot=root.resolve("tool-provenance");LinuxConnectedCustody(provenanceRoot)
        val provenanceDirectory=StrictJson.encode(str(provenanceRoot.toString())).decodeToString()
        Files.writeString(plugin.resolve("build/src/composition-probe.js"),"""
            import primary from './primary-server.js';import fs from 'node:fs/promises';import {AsyncLocalStorage} from 'node:async_hooks';
            import {NativeCredentialRegistry} from './native-credentials.js';import {NativeRuntimeClient} from './native-runtime-client.js';
            const callScope=new AsyncLocalStorage(),events=[];let instrumented=false;
            export default {...primary,async setup(ctx){
              await ctx.session.hook('model.request',async()=>{
                await fs.appendFile(${StrictJson.encode(str(root.resolve("model-request-attempts.log").toString())).decodeToString()},'blocked\n',{mode:0o600});
                throw Error('Test-only no-inference boundary');});
              const record=(tool,actual,phase,error)=>{if(events.length<4096)events.push({
                tool,sessionID:actual.sessionID,id:actual.id,phase,aborted:actual.signal?.aborted===true,status:Number.isInteger(error?.status)?error.status:null,
                frames:typeof error?.stack==='string'?error.stack.split('\n').filter(s=>/^\s+at /.test(s)&&!s.includes('http')).slice(0,16):[]});};
              const flush=async()=>{const grouped=new Map();for(const event of events.splice(0)){
                const lines=grouped.get(event.id)||[];lines.push(JSON.stringify(event));grouped.set(event.id,lines);}
                for(const [id,lines] of grouped)await fs.appendFile($provenanceDirectory+'/'+encodeURIComponent(id)+'.jsonl',lines.join('\n')+'\n',{mode:0o600});};
              if(!instrumented){instrumented=true;const originalFetch=globalThis.fetch;globalThis.fetch=async function(...args){
                const response=await originalFetch.apply(this,args);const scope=callScope.getStore();
                const url=typeof args[0]==='string'?new URL(args[0]):null;
                if(scope&&url?.origin===${StrictJson.encode(str(serviceOrigin)).decodeToString()}&&!response.ok)
                   record(scope.tool,scope.actual,'http-'+response.status);return response;
               };for(const [prototype,name] of [[NativeCredentialRegistry.prototype,'resolve'],[NativeCredentialRegistry.prototype,'restore'],
                [NativeCredentialRegistry.prototype,'credential'],[NativeRuntimeClient.prototype,'identity']]){
                const original=prototype[name];prototype[name]=async function(...args){try{const value=await original.apply(this,args);
                   const scope=callScope.getStore();if(scope){
                     if(value===false||value===undefined)record(scope.tool,scope.actual,name+'-missing');
                     else if(name==='restore')record(scope.tool,scope.actual,'restore-returned');
                     else if(name==='identity')record(scope.tool,scope.actual,value.scope==='controller'?'identity-controller':'identity-other');
                   }return value;
                }catch(e){const scope=callScope.getStore();if(scope)record(scope.tool,scope.actual,name+'-failed',e);throw e;}};
              }}
              const tools=new Proxy(ctx.tool,{get(target,key){if(key!=='transform')return Reflect.get(target,key);
                return callback=>target.transform(editor=>callback(new Proxy(editor,{get(original,key){if(key!=='add')return Reflect.get(original,key);
                  return definition=>original.add({...definition,execute:async(input,actual)=>{
                    record(definition.name,actual,'entered');try{const value=await callScope.run({tool:definition.name,actual},()=>definition.execute(input,actual));
                      record(definition.name,actual,'returned');return value;}catch(e){record(definition.name,actual,'failed',e);throw e;}
                  }});}})));}});
              const scoped=new Proxy(ctx,{get(target,key){return key==='tool'?tools:Reflect.get(target,key)}});
              const close=await primary.setup(scoped);let busy=false,last;const cached=new Map();
              const timer=setInterval(async()=>{if(busy)return;busy=true;let q;
                try{q=JSON.parse(await fs.readFile($requestFile,'utf8'));if(q.requestId===last)return;
                  if(q.tool==='test_only_reviewer_read_probe'){
                    const receiver=await ctx.session.get({sessionID:q.sessionID});
                    const read=(await ctx.tool.list()).find(t=>t.name==='read');
                    if(!read){last=q.requestId;await fs.writeFile($resultFile,JSON.stringify({id:q.id,requestId:q.requestId,syntheticCallContext:true,
                      value:{content:JSON.stringify({surface:'builtin-read-handler',available:false,executionHooksQualified:false})}}),{mode:0o600});return;}
                    const allowed=[${StrictJson.encode(str(workspace.resolve("reviewer-read-control.txt").toString())).decodeToString()},
                      ${StrictJson.encode(str(registration.parent.resolve("reviewer-read-sentinel.txt").toString())).decodeToString()},
                      ${StrictJson.encode(str(workspace.resolve("reviewer-read-link.txt").toString())).decodeToString()}];
                    const probes=[{filePath:allowed[0],sessionID:q.input.controllerSession,pathKind:'enrolled-controller-worktree-control'},
                      ...allowed.map((filePath,index)=>({filePath,sessionID:q.sessionID,pathKind:index===0?'reviewer-policy-worktree-control':
                        index===1?'external-registration-sibling':'worktree-symlink'}))];
                    const outcomes=[];for(const probe of probes){const {filePath,sessionID,pathKind}=probe;
                      let status='unavailable',sentinelObserved=false,frames=[],errorClass=null;
                      try{const value=await read.execute({path:filePath,offset:0,limit:2000},{sessionID,agent:receiver.agent,
                        messageID:'msg_read_surface_probe',id:q.id,signal:AbortSignal.timeout(3000),progress:async()=>{}});
                        sentinelObserved=JSON.stringify(value).includes('WRITHELM_PUBLIC_READ_SENTINEL');status='returned';
                      }catch(e){frames=typeof e?.stack==='string'?e.stack.split('\n').filter(s=>/^\s+at /.test(s)&&!s.includes('http')).slice(0,8):[];
                        const text=String(e?.message||e)+' '+String(e?.error?.message||e?.error||'');errorClass={name:typeof e?.name==='string'?e.name:null,
                          missingService:text.includes('Service not found'),invalidInput:/parse|schema|decode|invalid|Expected/i.test(text),
                          permission:/permission|denied|forbidden/i.test(text),
                          scopedExecutionHeld:text.includes('Writhelm session initialization or scoped execution is held'),
                          externalDirectoryDenied:text.includes('Permission denied: external_directory')};}
                      outcomes.push({pathKind,
                        status,sentinelObserved,frames,errorClass});
                    }
                    last=q.requestId;await fs.writeFile($resultFile,JSON.stringify({id:q.id,requestId:q.requestId,syntheticCallContext:true,
                      value:{content:JSON.stringify({surface:'builtin-read-handler',available:true,outcomes,executionHooksQualified:false,
                        positiveControlPassed:outcomes[0].sentinelObserved,reviewerPositiveControlPassed:outcomes[1].sentinelObserved,
                        credentialReachabilityQualified:false,
                        pathConfinementQualified:false,realCredentialRead:false,productionQualified:false})}}),{mode:0o600});return;
                  }
                  if(!['writhelm_project_checks','writhelm_activate_candidate','writhelm_activate_dispatch','writhelm_start_worker','writhelm_prepare_worker'].includes(q.tool))throw Error('unsupported test tool');
                  const tool=q.cached?cached.get(q.tool):(await ctx.tool.list()).find(t=>t.name===q.tool);if(!tool)return;
                  cached.set(q.tool,tool);
                  const actual={sessionID:q.sessionID,messageID:'msg_composition_probe',id:q.id,signal:new AbortController().signal,progress:async()=>{}};
                  const values=await Promise.allSettled(Array.from({length:q.parallel?16:1},()=>tool.execute(q.input,actual)));
                  const failures=values.filter(v=>v.status==='rejected');
                  const holds=events.filter(e=>e.id===q.id&&e.tool===q.tool&&e.phase==='failed');await flush();
                  if(failures.length&&(!q.parallel||holds.length!==failures.length||holds.some(e=>![429,503].includes(e.status))))throw failures[0].reason;
                  const returned=values.find(v=>v.status==='fulfilled'&&(!q.parallel||JSON.parse(v.value.content).phase!=='dispatch-held'))
                    ||values.find(v=>v.status==='fulfilled');if(!returned)throw failures[0].reason;
                  const value=returned.value;
                  const structuredHolds=values.filter(v=>v.status==='fulfilled'&&JSON.parse(v.value.content).phase==='dispatch-held').length;
                  last=q.requestId;await fs.writeFile($resultFile,JSON.stringify({id:q.id,requestId:q.requestId,syntheticCallContext:true,knownReadHolds:failures.length+structuredHolds,value}),{mode:0o600});
                }catch(e){if(q&&e?.code!=='ENOENT'){last=q.requestId;await fs.writeFile($resultFile,JSON.stringify({id:q.id,requestId:q.requestId,held:true,status:Number.isInteger(e?.status)?e.status:null,
                  frames:typeof e?.stack==='string'?e.stack.split('\n').filter(s=>/^\s+at /.test(s)&&!s.includes('http')).slice(0,8):[]}),{mode:0o600});}}
                finally{busy=false}},100);return async()=>{clearInterval(timer);await close?.()};}};
        """.trimIndent())
        val provision=obj("root" to str(privateRoot.toString()),"forbiddenAreas" to JArray(listOf(str(workspace.toString()))),
            "storeId" to uuid(instance.storeId),"serviceOrigin" to str(serviceOrigin),"nativeOrigin" to str(nativeOrigin),
            "nativeRegistrationFile" to str(registration.toString()),"clientPolicyFile" to str(root.resolve("config/opencode/opencode.json").toString()),
            "plugin" to str(plugin.toString()),"schemas" to JObject(PackagedClientContracts.load().mapValues {str(it.value)}))
        val code="const {readFileSync}=await import('node:fs');const {provisionAutomaticPrimary}=await import(process.argv[1]);await provisionAutomaticPrimary(JSON.parse(readFileSync(0,'utf8')))"
        val builder=ProcessBuilder(node.toString(),"--input-type=module","-e",code,plugin.resolve("build/src/primary-bootstrap.js").toUri().toString())
            .redirectError(root.resolve("provision.log").toFile()).redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("provision.log").toFile()))
        builder.environment().clear();builder.environment().putAll(env)
        val child=builder.start();child.outputStream.use {it.write(StrictJson.encode(provision))}
        assertTrue(child.waitFor(35,TimeUnit.SECONDS));assertEquals(0,child.exitValue())
        val bootstrap=privateRoot.resolve("bootstrap/automatic-bootstrap-${Digest.of("current".toByteArray()).hex}.json")
        val proof=Base64.getUrlDecoder().decode((StrictJson.decode(Files.readAllBytes(bootstrap)) as JObject).entries.getValue("enrollmentProof").string())
        enrollment=TestClientEnrollmentEndpoint(broker,proof,manager::configure);proof.fill(0)
        val adapter=HttpAdapter(WrithelmService(instance.app),HttpProfile(serviceOrigin),enrollmentEndpoint=enrollment,nativeEndpoints=router)
        server=embeddedServer(CIO,host="127.0.0.1",port=servicePort){
            intercept(ApplicationCallPipeline.Plugins){
                if(readAdmissionFault.get()&&call.request.uri.substringBefore('?')=="/internal/native/v1"){
                    injectedReadRefusals.incrementAndGet()
                    call.respondText("{\"error\":\"test-only-read-admission\"}",ContentType.Application.Json,HttpStatusCode.TooManyRequests)
                    finish()
                }
            }
            adapter.install(this)
        }.start(wait=false)
        cli("service","set","port",nativePort.toString());cli("service","start");started=true
        request("GET","/api/agent")
        val created=request("POST","/api/session",obj("location" to obj("directory" to str(workspace.toString())))) as JObject
        return (created.entries.getValue("data") as JObject).entries.getValue("id").string()
    }
    fun awaitConnected(controller:java.util.UUID){
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40)
        while(controller !in shared.connectedControllers()&&System.nanoTime()<deadline)Thread.sleep(100)
        assertTrue(controller in shared.connectedControllers(),"Actual enrolled controller held; inspect $root")
    }
    fun restart(session:String):Pair<Long,Long> {
        val before=(request("GET","/api/info") as JObject).entries.getValue("pid").long()
        // Do not replay a previous synthetic tool call merely because a plugin starts again.
        val pending=root.resolve("tool-request.json")
        if(Files.exists(pending))Files.move(pending,root.resolve("tool-request-before-restart.json"),StandardCopyOption.REPLACE_EXISTING)
        cli("service","stop");started=false
        cli("service","start");started=true
        request("GET","/api/agent")
        val info=request("GET","/api/session/$session") as JObject
        assertEquals(session,(info.entries.getValue("data") as JObject).entries.getValue("id").string())
        val after=(request("GET","/api/info") as JObject).entries.getValue("pid").long()
        assertNotEquals(before,after,"Normal restart must replace the native process")
        return before to after
    }
    fun probeNativeReviewerShellSurface(parentSession:String,reviewerToken:String?=null):JsonValue {
        val script=plugin.resolve("build/src/reviewer-shell-surface-probe.js")
        Files.writeString(script,"""
            import assert from 'node:assert/strict';import fs from 'node:fs/promises';import path from 'node:path';
            import {OpenCode} from '@opencode/client';
            import {initialReleasePermissions} from './native-initial.js';
            const [registration,source,parent,output]=process.argv.slice(2);
            let input='';for await(const bytes of process.stdin)input+=bytes;
            const {reviewerToken}=JSON.parse(input);
            const endpoint=JSON.parse(await fs.readFile(registration,'utf8'));
            const registrationStat=await fs.stat(registration);
            assert.equal(registrationStat.mode&0o077,0);
            const relative=path.relative(source,registration);
            assert.ok(relative.startsWith('..'+path.sep)||path.isAbsolute(relative));
            const native=OpenCode.make({baseUrl:endpoint.url,headers:{Authorization:'Basic '+Buffer.from('opencode:'+endpoint.password).toString('base64'),
              'x-opencode-directory':source}});
            const marker=path.join(source,'reviewer-shell-probe-marker.txt');
            const permissions=initialReleasePermissions([{action:'*',resource:'*',effect:'deny'},
              ...['read','shell','execute','edit','subagent','external_directory'].map(action=>({action,resource:'*',effect:'allow'}))],'reviewer-v1');
            const receiver=await native.session.create({parentID:parent,location:{directory:source},permissions,
              title:'TEST ONLY reviewer-policy shell surface; NOT canonical reviewer'});
            const info=await native.session.get({sessionID:receiver.id});
            assert.ok(info.permissions.some(rule=>rule.action==='shell'&&rule.effect==='deny'));
            const decision=await native.permission.create({sessionID:receiver.id,action:'shell',resources:[marker]});
            assert.equal(decision.effect,'deny');
            const files=['result.txt','tests/candidate.test.cjs','.git/index'];
            const before=await Promise.all(files.map(file=>fs.readFile(path.join(source,file))));
            const deniedCalls=[];
            for(const kind of reviewerToken?['none','delegated-reviewer']:['none']){
              let status=null;
              const unprivileged=OpenCode.make({baseUrl:endpoint.url,headers:{'x-opencode-directory':source,
                ...(kind==='delegated-reviewer'?{Authorization:'Bearer '+reviewerToken}:{})},
                fetch:async(...args)=>{const response=await fetch(...args);status=response.status;return response;}});
              let refused=false;
              try{await unprivileged.session.shell({sessionID:receiver.id,id:'msg_shell_'+kind.replaceAll('-','_'),
                command:'printf unauthorized > reviewer-shell-probe-marker.txt'},{signal:AbortSignal.timeout(10000)});}catch{refused=true;}
              assert.equal(refused,true);assert.equal(status,401,'Reject at native authentication, not a permission query or timeout');
              await assert.rejects(fs.access(marker),error=>error.code==='ENOENT');
              deniedCalls.push({credentialKind:kind,httpStatus:status,commandEntryObserved:false});
            }
            // Native session.shell is an operator API, NOT a fabricated tool.execute call.
            // The command can touch only a disposable marker, never assessed source/tests.
            const id='msg_shell_surface_probe';let returned=false;
            try{await native.session.shell({sessionID:receiver.id,id,command:'printf native-shell-entered > reviewer-shell-probe-marker.txt'},
              {signal:AbortSignal.timeout(10000)});returned=true;}catch{}
            let markerBytes=null;
            for(let i=0;i<100;i++){
              try{markerBytes=await fs.readFile(marker,'utf8');break;}catch(e){if(e.code!=='ENOENT')throw e;}
              await new Promise(resolve=>setTimeout(resolve,20));
            }
            for(let i=0;i<files.length;i++)assert.deepEqual(await fs.readFile(path.join(source,files[i])),before[i]);
            assert.equal(returned,true,'Pinned operator shell API must actually complete, not count a timeout as denial');
            assert.equal(markerBytes,'native-shell-entered','Record actual command execution, not just an HTTP reply');
            await fs.writeFile(output,JSON.stringify({nativeSessionID:receiver.id,parentSessionID:parent,
              endpoint:'session.shell',requestReturned:returned,actualCommandEntryObserved:markerBytes!==null,
              permissionQueryEffect:decision.effect,callerAuthority:'native-service-operator-credential',modelCalls:0,
              nativeAuthenticationControls:deniedCalls,delegatedReviewerCredentialTested:reviewerToken!==null,
              nativeRegistrationOutsideWorktree:true,nativeRegistrationGroupOtherAccess:false,
              reviewerCredentialFileReadReachabilityQualified:false,sameUidOsConfinementClaimed:false,
              reviewerPolicyApplied:true,canonicalReviewerAssignment:false,activeReviewerLifecycleQualified:false,
              modelRequestsBlockedByTestPlugin:true,assessedSourceUnchanged:true,
              toolExecutionHooksQualified:false,productionQualified:false}),{mode:0o600,flag:'wx'});
        """.trimIndent())
        val output=root.resolve("reviewer-shell-surface-evidence.json")
        val child=ProcessBuilder(node.toString(),script.toString(),registration.toString(),workspace.toString(),parentSession,output.toString())
            .redirectOutput(root.resolve("reviewer-shell-probe.out").toFile()).redirectError(root.resolve("reviewer-shell-probe.log").toFile()).start()
        child.outputStream.use {it.write(StrictJson.encode(obj("reviewerToken" to (reviewerToken?.let(::str) ?: JNull))))}
        assertTrue(child.waitFor(25,TimeUnit.SECONDS),"Native shell probe outcome unknown; inspect $root")
        assertEquals(0,child.exitValue(),"Native shell surface probe failed; inspect $root")
        assertFalse(Files.exists(root.resolve("model-request-attempts.log")),"A model-free native API probe must not even enter model.request")
        return StrictJson.decode(Files.readAllBytes(output))
    }
    fun probeReviewerReadSurface(receiver:String,controllerSession:String):JsonValue {
        val sentinel=registration.parent.resolve("reviewer-read-sentinel.txt")
        val control=workspace.resolve("reviewer-read-control.txt")
        for(file in listOf(sentinel,control)){
            Files.writeString(file,"WRITHELM_PUBLIC_READ_SENTINEL\n",StandardOpenOption.CREATE_NEW)
            Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        }
        Files.createSymbolicLink(workspace.resolve("reviewer-read-link.txt"),sentinel)
        val outcome=invoke(receiver,"test_only_reviewer_read_probe",obj("controllerSession" to str(controllerSession)))
        val receiverHash=Digest.of(receiver.toByteArray()).hex
        val controllerHash=Digest.of(controllerSession.toByteArray()).hex
        val areas=Files.list(privateRoot.resolve("controllers")).use {paths->paths.filter {Files.isDirectory(it)}.toList()}
        assertTrue(areas.any {Files.exists(it.resolve("native/session-$controllerHash.json"))},
            "The positive read control must use a genuinely retained controller registry binding")
        assertTrue(areas.none {Files.exists(it.resolve("native/session-$receiverHash.json"))},
            "A policy-only child must not acquire an assignment binding from a read probe")
        assertTrue(areas.none {area->Files.list(area.resolve("bootstrap")).use {paths->
            paths.anyMatch {it.fileName.toString().startsWith("execution-profile-")}
        }},"This isolated profile has no qualified execution deployment")
        val fields=(outcome as JObject).entries
        val traced=JObject(fields+mapOf("controllerRegistryBindingObserved" to JBoolean(true),
            "reviewerRegistryBindingObserved" to JBoolean(false),"qualifiedExecutionDeploymentObserved" to JBoolean(false),
            "bindingWasSynthesizedByProbe" to JBoolean(false)))
        Files.write(root.resolve("reviewer-read-surface-evidence.json"),StrictJson.encode(traced))
        assertEquals("WRITHELM_PUBLIC_READ_SENTINEL\n",Files.readString(sentinel))
        assertFalse(Files.exists(root.resolve("model-request-attempts.log")))
        return traced
    }
    fun host(controller:java.util.UUID)=uuid((StrictJson.decode(Files.readAllBytes(privateRoot.resolve("controllers/$controller.dispatch"))) as JObject).entries.getValue("hostId"))
    fun invoke(session:String,tool:String,input:JsonValue,cached:Boolean=false,expectHeld:Boolean=false,parallel:Boolean=false,originalCallId:String?=null):JsonValue {
        val id=originalCallId ?: "call_"+java.util.UUID.randomUUID().toString().replace("-","")
        val requestId=java.util.UUID.randomUUID().toString() // Driver delivery only; NOT another tool/effect identity.
        val temporary=root.resolve("tool-request-next.json")
        Files.write(temporary,StrictJson.encode(obj("id" to str(id),"requestId" to str(requestId),"sessionID" to str(session),"tool" to str(tool),"input" to input,
            "cached" to JBoolean(cached),"parallel" to JBoolean(parallel))))
        Files.move(temporary,root.resolve("tool-request.json"),StandardCopyOption.REPLACE_EXISTING)
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
        while(System.nanoTime()<deadline){
            val f=try {(StrictJson.decode(Files.readAllBytes(root.resolve("tool-result.json"))) as JObject).entries}catch(_:Exception){null}
            if(f?.get("id")==str(id)&&f["requestId"]==str(requestId)){
                if(expectHeld){assertEquals(JBoolean(true),f["held"],"Cached handler must refuse revoked authority");return JObject(f)}
                assertEquals(JBoolean(true),f["syntheticCallContext"],"Registered handler held (status=${f["status"]}); inspect $root")
                return StrictJson.decode((f.getValue("value") as JObject).entries.getValue("content").string().toByteArray())
            };Thread.sleep(100)
        };error("Registered tool outcome unknown; inspect $root")
    }
    override fun close(){try {if(started)cli("service","stop")}finally{server?.stop(0,1000);manager.close();enrollment?.close();http.close()}}
}
