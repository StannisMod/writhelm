// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.URI
import java.io.IOException
import java.util.concurrent.CancellationException

/** Explicit literal-loopback application configuration, not a production launcher/recovery tool. */
internal class HttpProfile(val origin: String) {
    val authority: String
    init {
        val uri=URI(origin)
        require(uri.scheme=="http" && uri.host in setOf("127.0.0.1","[::1]","::1") && uri.port in 1..65535 &&
            uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null && uri.rawPath.isNullOrEmpty())
        authority=uri.rawAuthority
    }
}
/** Server-owned no-op phase observer; never changes bytes, authorization or client-selected limits. */
internal data class HttpDeliveryEvent(val control: Boolean,val status: Int)
/** Install only into an explicitly bounded loopback engine. No external credentials/config/loggers. */
internal class HttpAdapter(private val service: WrithelmService,val profile: HttpProfile,
    private val beforeDelivery: suspend (HttpDeliveryEvent)->Unit={},
    private val admission: HttpAdmission=HttpAdmission()) {
    val mcp=McpAdapter(service)
    private fun boundary(call: ApplicationCall,protected: Boolean): String? {
        val headers=call.request.headers
        var bytes=0L;var count=0
        headers.entries().forEach { (k,vs) -> vs.forEach { v ->
            count++;bytes+=k.toByteArray().size+v.toByteArray().size+4
            if(v.toByteArray().size>8192 || bytes>16384 || count>64)stop("INGRESS_TOO_LARGE",413)
        } }
        if(headers.getAll(HttpHeaders.Host)!=listOf(profile.authority))stop("FORBIDDEN",403)
        headers.getAll(HttpHeaders.Origin)?.let { if(it!=listOf(profile.origin))stop("FORBIDDEN",403) }
        if(call.request.queryString().isNotEmpty() || headers.contains(HttpHeaders.Cookie) ||
            headers.names().any { it.startsWith("X-Forwarded-",true) || it.equals("Forwarded",true) })stop("FORBIDDEN",403)
        if(!protected)return null
        val values=headers.getAll(HttpHeaders.Authorization)
        if(values==null || values.size!=1)stop("UNAUTHENTICATED",401)
        val v=values.single()
        if(v.length>512 || !v.matches(Regex("(?i:Bearer) [A-Za-z0-9_.-]{121}")))stop("UNAUTHENTICATED",401)
        return v.substring(7).also { service.access(it) }
    }
    private suspend fun body(call: ApplicationCall,maximum: Int,lease: HttpAdmission.Lease): ByteArray {
        if(call.request.headers.getAll(HttpHeaders.ContentEncoding)!=null)stop("INVALID_INPUT",415)
        val media=call.request.headers.getAll(HttpHeaders.ContentType)
        if(media==null || media.size!=1 || !media.single().matches(Regex("(?i:application/json)(?:;\\s*(?i:charset=utf-8))?")))stop("INVALID_INPUT",415)
        val length=call.request.headers.getAll(HttpHeaders.ContentLength)
        if(length!=null && (length.size!=1 || length.single().toLongOrNull()==null || length.single().toLong()>maximum || length.single().toLong()<0))stop("INGRESS_TOO_LARGE",413)
        if(lease.remainingMillis()<=0)stop("UNAVAILABLE",503)
        return withTimeout(minOf(10000,lease.remainingMillis())) {
            val channel=call.receiveChannel();val result=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true) {
                val n=channel.readAvailable(buffer,0,buffer.size)
                if(n<0)break
                if(n>maximum-result.size())stop("INGRESS_TOO_LARGE",413)
                result.write(buffer,0,n)
            }
            result.toByteArray()
        }
    }
    /** Defensive copy; no callback/caller/session can change bytes after dispatch ownership ends. */
    private class FrozenReply(val status: Int,body: ByteArray,private val sessionId: String?) {
        private val content=body.copyOf()
        suspend fun emit(call: ApplicationCall) {
            call.response.header(HttpHeaders.CacheControl,"no-store")
            call.response.header("X-Content-Type-Options","nosniff")
            sessionId?.let { call.response.header("MCP-Session-Id",it) }
            if(content.isEmpty())call.respond(HttpStatusCode.fromValue(status))
            else call.respondBytes(content,ContentType.Application.Json,HttpStatusCode.fromValue(status))
        }
    }
    private fun wire(r: ServiceReply)=WireReply(r.status,r.bytes(service.limits.responseBytes))
    private fun freeze(reply: WireReply,lease: HttpAdmission.Lease,maximum: Int): FrozenReply {
        if(reply.body.size>maximum || !lease.planBytes(reply.body.size)) {
            val failure=wire(error("UNAVAILABLE",503))
            check(lease.planBytes(failure.body.size)) // primary/error reserve retained before dispatch
            return FrozenReply(failure.status,failure.body,null)
        }
        return FrozenReply(reply.status,reply.body,reply.sessionId)
    }
    private suspend fun prepare(call: ApplicationCall,lease: HttpAdmission.Lease): FrozenReply {
        val path=call.request.path();val method=call.request.httpMethod
        var mayPublish=false;var sdkStarted=false;var sdkFinalized=false
        try {
            val t=checkNotNull(boundary(call,true))
            currentCoroutineContext().ensureActive()
            val session=if(path=="/mcp")call.request.headers.getAll("MCP-Session-Id") else null
            val version=if(path=="/mcp")call.request.headers.getAll("MCP-Protocol-Version") else null
            if(session!=null && session.size!=1 || version!=null && version.size!=1)stop("INVALID_INPUT",400)
            if(lease.lane==HttpAdmission.Lane.CONTROL) return freeze(wire(error("INVALID_INPUT",405)),lease,65536)
            if(path=="/mcp" && method==HttpMethod.Post) {
                val accept=call.request.headers.getAll(HttpHeaders.Accept).orEmpty().flatMap(::parseHeaderValue)
                fun accepts(type: String)=accept.any { entry -> entry.value.equals(type,true) && entry.params.firstOrNull { it.name.equals("q",true) }?.value.let { it==null || (it.toDoubleOrNull()?.let { q -> q>0 && q<=1 } == true) } }
                if(!accepts("application/json") || !accepts("text/event-stream"))stop("INVALID_INPUT",406)
            }
            val bytes=when {
                path=="/api/v1/commands" && method==HttpMethod.Post -> body(call,8*1024*1024,lease)
                path=="/api/v1/reads" && method==HttpMethod.Post -> body(call,16384,lease)
                path=="/mcp" && method==HttpMethod.Post -> body(call,8*1024*1024+65536,lease)
                else -> byteArrayOf()
            }
            currentCoroutineContext().ensureActive()
            if(lease.remainingMillis()<70000)stop("UNAVAILABLE",503)
            // Cancellation of the HTTP caller must not unwind active SDK/domain context early.
            return lease.protectedWork {
                val reply=when(path) {
                    "/api/v1/commands" -> { if(method!=HttpMethod.Post)stop("INVALID_INPUT",405);mayPublish=true;wire(service.command(t,bytes)) }
                    "/api/v1/reads" -> { if(method!=HttpMethod.Post)stop("INVALID_INPUT",405);wire(service.read(t,bytes)) }
                    "/mcp" -> {
                        when(method) {
                            HttpMethod.Delete -> { sdkStarted=true;mcp.delete(t,session?.single(),version?.single()).also { sdkFinalized=true } }
                            HttpMethod.Post -> {
                                mayPublish=true;sdkStarted=true
                                mcp.post(t,session?.single(),version?.single(),bytes).also { sdkFinalized=true }
                            }
                            else -> stop("INVALID_INPUT",405)
                        }
                    }
                    else -> stop("NOT_FOUND",404)
                }
                freeze(reply,lease,if(path=="/mcp")12*1024*1024 else if(path=="/api/v1/commands")65536 else service.limits.responseBytes)
            }
        } catch(e: TimeoutCancellationException) {
            if(sdkStarted && !sdkFinalized)lease.fence()
            if(!currentCoroutineContext().isActive)throw e
            return freeze(wire(error(if(mayPublish)"PUBLICATION_UNKNOWN" else "UNAVAILABLE",503)),lease,65536)
        } catch(e: CancellationException) {
            if(sdkStarted && !sdkFinalized)lease.fence()
            throw e
        } catch(e: ServiceFault) { return freeze(wire(error(e.code,e.status)),lease,65536) }
          catch(e: dev.writhelm.core.app.AppAccessFault) { return freeze(wire(error(e.problem.name,if(e.problem==dev.writhelm.core.app.AppAccessProblem.UNAUTHENTICATED)401 else 403)),lease,65536) }
          catch(_: Exception) {
              if(sdkStarted && !sdkFinalized)lease.fence()
              return freeze(wire(error(if(mayPublish)"PUBLICATION_UNKNOWN" else "INTEGRITY",if(mayPublish)503 else 500)),lease,65536)
          }
    }
    private suspend fun handle(call: ApplicationCall) {
        val control=call.request.path()=="/mcp" && call.request.httpMethod==HttpMethod.Get
        val attempt=admission.enter(if(control)HttpAdmission.Lane.CONTROL else HttpAdmission.Lane.STATEFUL)
        val lease=attempt.lease
        if(lease==null) {
            // No body/auth/dispatch or protected response allocation. Best-effort refusal is bounded;
            // engine connection/flood admission remains a separately qualified boundary.
            try { withTimeout(1000) { val r=wire(error(if(attempt.refusal==HttpAdmission.Refusal.FENCED)"UNAVAILABLE" else "RATE_LIMIT",if(attempt.refusal==HttpAdmission.Refusal.FENCED)503 else 429));FrozenReply(r.status,r.body,null).emit(call) } }
            catch(e: CancellationException) { throw e } catch(_: Exception) { throw IOException("bounded refusal delivery failed") }
            return
        }
        try {
            currentCoroutineContext().ensureActive()
            val reply=prepare(call,lease)
            lease.finishWork() // session/token/domain cleanup + private immutable bounded bytes done
            val sendBudget=minOf(HttpAdmission.DELIVERY_MILLIS,lease.remainingMillis())
            if(sendBudget<=0)throw CancellationException("delivery budget exhausted")
            val start=System.nanoTime()
            try {
                withTimeout(sendBudget) { beforeDelivery(HttpDeliveryEvent(control,reply.status));reply.emit(call) }
            } catch(e: CancellationException) { throw e }
              catch(_: Exception) {
                  if(!call.response.isCommitted) {
                      val publishing=call.request.httpMethod==HttpMethod.Post && call.request.path() in setOf("/api/v1/commands","/mcp")
                      val failure=wire(error(if(publishing)"PUBLICATION_UNKNOWN" else "UNAVAILABLE",503))
                      val remaining=sendBudget-(System.nanoTime()-start)/1000000L
                      if(remaining>0 && lease.reserveFallback(failure.body.size)) {
                          withTimeout(remaining) { FrozenReply(failure.status,failure.body,null).emit(call) }
                      } else throw IOException("bounded response delivery failed")
                  } else throw IOException("committed response delivery failed")
              }
        } finally { lease.close() }
    }
    fun install(application: Application) {
        application.routing { route("/{path...}") { handle { this@HttpAdapter.handle(call) } } }
    }
}
