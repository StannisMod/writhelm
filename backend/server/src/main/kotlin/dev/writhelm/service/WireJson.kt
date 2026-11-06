// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import com.fasterxml.jackson.core.*
import dev.writhelm.core.codec.*
import dev.writhelm.core.knowledge.Utf8Content
import kotlinx.serialization.json.*

/** Jackson token traversal only: domain bytes are sliced at library byte locations, never rebuilt. */
internal object WireJson {
    val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private val factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(1024*1024)
            .maxNameLength(128).maxNumberLength(256).maxTokenCount(110000).build()).build()
    class Decoded(val root: JsonObject,val domain: ByteArray?)
    fun decode(bytes: ByteArray): Decoded {
        if(bytes.size>8*1024*1024+65536)stop("INGRESS_TOO_LARGE",413)
        if(bytes.take(3)==listOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()))stop("INVALID_INPUT",400)
        try { Utf8Content.fromBytes(bytes) } catch(_: IllegalArgumentException) { stop("INVALID_INPUT",400) }
        var domain: ByteArray?=null
        try {
            factory.createParser(bytes).use { p ->
                fun value(path: List<String>,depth: Int) {
                    if(depth>40)stop("INVALID_INPUT",400)
                    val capture=path==listOf("params","arguments","command") || path==listOf("params","arguments","read")
                    val start=p.currentTokenLocation().byteOffset.toInt()
                    when(p.currentToken()) {
                        JsonToken.START_OBJECT -> {
                            var count=0
                            while(p.nextToken()!=JsonToken.END_OBJECT) {
                                if(p.currentToken()!=JsonToken.FIELD_NAME || ++count>4096)stop("INVALID_INPUT",400)
                                val name=p.currentName();if(utf8Size(name)>128)stop("INVALID_INPUT",400)
                                p.nextToken();value(path+name,depth+1)
                            }
                        }
                        JsonToken.START_ARRAY -> { var count=0;while(p.nextToken()!=JsonToken.END_ARRAY) {
                            if(++count>4096)stop("INVALID_INPUT",400);value(path+"[]",depth+1)
                        } }
                        JsonToken.VALUE_STRING -> if(utf8Size(p.text)>1024*1024)stop("INVALID_INPUT",400)
                        JsonToken.VALUE_NUMBER_INT,JsonToken.VALUE_NUMBER_FLOAT,JsonToken.VALUE_TRUE,JsonToken.VALUE_FALSE,JsonToken.VALUE_NULL -> Unit
                        else -> stop("INVALID_INPUT",400)
                    }
                    if(capture) {
                        if(domain!=null)stop("INVALID_INPUT",400)
                        domain=bytes.copyOfRange(start,p.currentLocation().byteOffset.toInt())
                    }
                }
                p.nextToken();value(emptyList(),0);if(p.nextToken()!=null)stop("INVALID_INPUT",400)
            }
            return Decoded(json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: stop("INVALID_INPUT",400),domain)
        } catch(_: JacksonException) { stop("INVALID_INPUT",400) }
          catch(_: IllegalArgumentException) { stop("INVALID_INPUT",400) }
          catch(_: CodecFault) { stop("INVALID_INPUT",400) }
    }
}
