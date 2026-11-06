// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import dev.writhelm.core.app.AppAccessView
import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.Capability
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.shared.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID

internal class WireReply(val status: Int,val body: ByteArray=byteArrayOf(),val sessionId: String?=null)
/** SDK owns JSON-RPC parsing, dispatch, lifecycle handlers and result serialization.
 * This bridge binds one bounded HTTP exchange to the SDK's public Transport extension point. */
internal class McpAdapter(private val service: WrithelmService,private val now: ()->Long={System.nanoTime()/1000000}) {
    private val json=WireJson.json
    private class Bridge: Transport {
        var receiver: (suspend (JSONRPCMessage)->Unit)?=null
        var response=CompletableDeferred<JSONRPCMessage>()
        private var closed: ()->Unit={}
        override suspend fun start()=Unit
        override suspend fun send(message: JSONRPCMessage,options: TransportSendOptions?) { response.complete(message) }
        override suspend fun close() { closed() }
        override fun onClose(block: ()->Unit) { closed=block }
        override fun onError(block: (Throwable)->Unit)=Unit
        override fun onMessage(block: suspend (JSONRPCMessage)->Unit) { receiver=block }
    }
    private class Session(val id: String,val principal: UUID,val project: UUID,val store: UUID?,val scope: dev.writhelm.core.auth2.CredentialScope,val born: Long,
        val bridge: Bridge,val server: Server) {
        val ids=hashSetOf<String>();var ready=false
        var token: String?=null;var domain: ByteArray?=null
    }
    private val sessions=linkedMapOf<String,Session>()
    private fun schema(name: String): JsonObject {
        val bytes=checkNotNull(javaClass.getResourceAsStream("/dev/writhelm/review/$name-v1.json")).use { it.readBytes() }
        return json.parseToJsonElement(bytes.decodeToString()).jsonObject
    }
    private fun sdkSchema(s: JsonObject)=ToolSchema(properties=s["properties"]?.jsonObject,
        required=s["required"]?.jsonArray?.map { it.jsonPrimitive.content },defs=s["\$defs"]?.jsonObject)
    private suspend fun create(a: AppAccessView): Session {
        sessions.values.filter { now()-it.born>=300000 }.toList().forEach { old ->
            sessions.remove(old.id);old.server.close()
        }
        if(sessions.size>=8)stop("UNAVAILABLE",503)
        val bridge=Bridge();val server=Server(Implementation("writhelm","0.1.0"),ServerOptions(ServerCapabilities(tools=ServerCapabilities.Tools(listChanged=false))))
        val s=Session(UUID.randomUUID().toString(),a.principalId,a.projectId,a.storeId,a.scope,now(),bridge,server)
        for((name,field) in listOf("writhelm_read" to "read","writhelm_command" to "command")) {
            server.addTool(name=name,description="Exact Writhelm $field; current authority is checked on every call.",inputSchema=sdkSchema(schema("$field-tool"))) { request ->
                val args=request.arguments
                val result=if(args==null || args.keys!=setOf(field) || s.domain==null)error("INVALID_INPUT",400)
                else if(field=="read")service.read(checkNotNull(s.token),checkNotNull(s.domain),agent=true)
                else service.command(checkNotNull(s.token),checkNotNull(s.domain),agent=true)
                val bytes=result.bytes(service.limits.responseBytes);val structured=json.parseToJsonElement(bytes.decodeToString()).jsonObject
                CallToolResult(content=listOf(TextContent(bytes.decodeToString())),structuredContent=structured,isError=result.status!=200)
            }
        }
        server.createSession(bridge);sessions[s.id]=s;return s
    }
    suspend fun close() { sessions.values.toList().forEach { it.server.close() };sessions.clear() }
    suspend fun delete(token: String,id: String?,version: String?): WireReply {
        val a=service.access(token);val s=session(a,id,version);sessions.remove(s.id);s.server.close();return WireReply(204)
    }
    private fun session(a: AppAccessView,id: String?,version: String?): Session {
        if(version!="2025-11-25")stop("INVALID_INPUT",400)
        if(id==null)stop("INVALID_INPUT",400)
        val s=sessions[id] ?: stop("NOT_FOUND",404)
        if(now()-s.born>=300000) { sessions.remove(id);stop("NOT_FOUND",404) }
        if(a.principalId!=s.principal || a.projectId!=s.project || a.storeId!=s.store || a.scope!=s.scope)stop("FORBIDDEN",403)
        return s
    }
    private fun rpcError(code: Int,id: RequestId?=null): WireReply {
        val message=JSONRPCError(id,RPCError(code,"Invalid protocol request"))
        return WireReply(200,json.encodeToString(JSONRPCMessage.serializer(),message).toByteArray())
    }
    suspend fun post(token: String,id: String?,version: String?,bytes: ByteArray): WireReply {
        val a=service.access(token)
        val decoded=try { WireJson.decode(bytes) } catch(_: ServiceFault) { return rpcError(-32700) }
        val root=decoded.root
        val methodToken=root["method"] as? JsonPrimitive
        val method=methodToken?.takeIf { it.isString }?.contentOrNull
        if(root.containsKey("method") && method==null)return rpcError(-32600)
        if(root["jsonrpc"]!=JsonPrimitive("2.0"))return rpcError(-32600)
        if(version!=null && version!="2025-11-25")stop("INVALID_INPUT",400)
        val init=method=="initialize"
        var message=try { json.decodeFromJsonElement(JSONRPCMessage.serializer(),root) } catch(_: Exception) { return rpcError(-32600) }
        val request=message as? JSONRPCRequest
        val s=if(init) {
            if(id!=null || request==null)stop("INVALID_INPUT",400)
            // Validate the SDK's typed initialization contract before allocating bounded state.
            try { request.fromJSON() } catch(_: Exception) { return rpcError(-32602,request.id) }
            val created=create(a)
            // Only this negotiated version is supported by our adapter profile. The SDK's older
            // version implementations are not advertised; fallback follows the MCP lifecycle law.
            val params=request.params!!.jsonObject
            message=request.copy(params=JsonObject(params+("protocolVersion" to JsonPrimitive("2025-11-25"))))
            created
        } else session(a,id,version)
        if(request!=null) {
            val raw=root["id"] as? JsonPrimitive ?: return rpcError(-32600)
            val identity=if(raw.isString) {
                if(utf8Size(raw.content)>128)return rpcError(-32600)
                "s:"+raw.content
            } else {
                if(!raw.content.matches(Regex("0|-?[1-9][0-9]*")) || raw.content.toLongOrNull()==null)return rpcError(-32600)
                "n:"+raw.content
            }
            if(identity in s.ids)return rpcError(-32600,request.id)
            if(s.ids.size>=256) { sessions.remove(s.id);s.server.close();stop("NOT_FOUND",404) }
            s.ids.add(identity)
            if(!init && !s.ready && method!="ping")return rpcError(-32600,request.id)
        } else {
            if(message is JSONRPCNotification) {
                if(method=="notifications/initialized")s.ready=true
                // A tool notification never enters the SDK dispatch path, even with command args.
                if(method!="notifications/initialized" && method!="notifications/cancelled")return WireReply(202)
            } else return WireReply(202) // no server-originated requests in this profile
        }
        s.token=token;s.domain=decoded.domain;s.bridge.response=CompletableDeferred()
        try {
            checkNotNull(s.bridge.receiver).invoke(message)
            if(request==null)return WireReply(202)
            val response=withTimeout(60000) { s.bridge.response.await() }
            var wire=json.encodeToJsonElement(JSONRPCMessage.serializer(),response).jsonObject
            if(method=="tools/list" && response is JSONRPCResponse) {
                val result=wire.getValue("result").jsonObject
                val tools=result.getValue("tools").jsonArray.filter { tool ->
                    if(tool.jsonObject.getValue("name").jsonPrimitive.content=="writhelm_read")Capability.READ in a.capabilities
                    else a.scope !is dev.writhelm.core.auth2.CredentialScope.Assignment && a.capabilities.any { it==Capability.PROPOSE || it==Capability.PLAN }
                }.map { tool ->
                    val t=tool.jsonObject;val name=if(t.getValue("name").jsonPrimitive.content=="writhelm_read")"read" else "command"
                    JsonObject(t+("inputSchema" to schema("$name-tool"))+("outputSchema" to schema("result")))
                }
                wire=JsonObject(wire+("result" to JsonObject(result+("tools" to JsonArray(tools)))))
            }
            val output=wire.toString().toByteArray();if(output.size>12*1024*1024)stop("UNAVAILABLE",503)
            return WireReply(200,output,if(init)s.id else null)
        } finally { s.token=null;s.domain=null }
    }
}
