// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.io.CharacterEscapes
import com.fasterxml.jackson.core.io.SerializedString
import com.fasterxml.jackson.core.json.JsonWriteFeature
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections

internal enum class CodecProblem { UNSUPPORTED_FORMAT, MALFORMED, INVALID_SHAPE, ADMISSION_LIMIT, RESOURCE_UNAVAILABLE, DUPLICATE_IDENTITY, DIGEST_MISMATCH, INVALID_GRAPH }
/** Never embeds parser messages, document text or a caller-selected location. */
internal class CodecFault(val problem: CodecProblem) : RuntimeException(problem.name)
internal fun refuse(problem: CodecProblem): Nothing = throw CodecFault(problem)
internal fun shape(ok: Boolean) { if (!ok) refuse(CodecProblem.INVALID_SHAPE) }
internal fun graph(ok: Boolean) { if (!ok) refuse(CodecProblem.INVALID_GRAPH) }
internal data class JsonLimits(val bytes: Int = 4*1024*1024, val stringBytes: Int = 1024*1024, val nameBytes: Int = 128,
    val depth: Int = 32, val collection: Int = 4096, val tokens: Int = 100000) {
    init { require(listOf(bytes, stringBytes, nameBytes, depth, collection, tokens).all { it > 0 }) }
}
internal sealed interface JsonValue { val depth: Int get()=0 }
internal class JObject(fields: Map<String, JsonValue>) : JsonValue {
    override val depth=1+(fields.values.maxOfOrNull { it.depth } ?: 0)
    init { JsonConstruction.depth(depth); JsonConstruction.objectFields(fields) }
    val entries: Map<String, JsonValue> = Collections.unmodifiableMap(LinkedHashMap(fields))
}
internal class JArray(items: List<JsonValue>) : JsonValue {
    override val depth=1+(items.maxOfOrNull { it.depth } ?: 0)
    init { JsonConstruction.depth(depth); JsonConstruction.collection(items.size); JsonConstruction.node(2+items.count { it===JNull }.toLong()) }
    val values: List<JsonValue> = Collections.unmodifiableList(ArrayList(items))
}
internal data class JString(val value: String) : JsonValue { init { JsonConstruction.string(value) } }
internal data class JNumber(val value: Long) : JsonValue { init { JsonConstruction.node(1) } }
internal data class JBoolean(val value: Boolean) : JsonValue { init { JsonConstruction.node(1) } }
internal data object JNull : JsonValue

internal fun utf8Size(text: String): Long {
    var bytes = 0L; var i = 0
    while (i < text.length) {
        val c = text[i++]
        bytes += when {
            c.code < 128 -> 1
            c.code < 2048 -> 2
            c.isHighSurrogate() -> { if (i == text.length || !text[i++].isLowSurrogate()) refuse(CodecProblem.MALFORMED); 4 }
            c.isLowSurrogate() -> refuse(CodecProblem.MALFORMED)
            else -> 3
        }
    }
    return bytes
}

/** Scoped allocation accounting; no state survives the operation or crosses threads. */
internal object JsonConstruction {
    private class State(val limits: JsonLimits,val consume: (Long,Long)->Unit,val problem: CodecProblem) { var tokens=0L }
    private val active=ThreadLocal<State?>()
    fun <T> bounded(limits: JsonLimits,consume: (Long,Long)->Unit,unavailable: Boolean=true,block: ()->T): T {
        val previous=active.get(); active.set(State(limits,consume,if(unavailable)CodecProblem.RESOURCE_UNAVAILABLE else CodecProblem.ADMISSION_LIMIT))
        try { return block() } finally { if(previous==null)active.remove() else active.set(previous) }
    }
    fun node(n: Long) { active.get()?.let { if(n>it.limits.tokens-it.tokens)refuse(it.problem); it.tokens+=n; it.consume(n,0) } }
    fun collection(n: Int) { active.get()?.let { if(n>it.limits.collection)refuse(it.problem) } }
    fun depth(n: Int) { active.get()?.let { if(n>it.limits.depth)refuse(it.problem) } }
    fun temporary(n: Int) { active.get()?.consume?.invoke(0,n.toLong()) }
    fun encodingLimit(): Int = active.get()?.limits?.bytes ?: 16*1024*1024
    fun <T> withoutMeter(block: ()->T): T {
        val previous=active.get(); active.remove()
        try { return block() } finally { if(previous!=null)active.set(previous) }
    }
    fun string(s: String,name: Boolean=false) { active.get()?.let {
        val n=utf8Size(s); if(n>if(name)it.limits.nameBytes else it.limits.stringBytes)refuse(it.problem)
        node(1); it.consume(0,n)
    } }
    fun objectFields(fields: Map<String,JsonValue>) {
        collection(fields.size); node(2+fields.values.count { it===JNull }.toLong()); fields.keys.forEach { string(it,true) }
    }
    fun reuse(v: JsonValue) {
        depth(v.depth)
        when(v) {
            is JObject -> { objectFields(v.entries); v.entries.values.filter { it!==JNull }.forEach(::reuse) }
            is JArray -> { collection(v.values.size); node(2); v.values.forEach(::reuse) }
            is JString -> string(v.value)
            else -> node(1)
        }
    }
}

internal object StrictJson {
    fun decode(bytes: ByteArray, limits: JsonLimits = JsonLimits(), unavailable: Boolean = false,
        consume: (Long, Long) -> Unit = { _, _ -> }): JsonValue = decodeBounded(bytes,limits,unavailable,consume,null)

    /** Command attachment budget only; this is NOT closed command/domain admission. The sole
     * exception is the string at root.payload.bodies[array element].body, never a name-only match.
     * Every token/string still consumes the same aggregate callback and encoded/parser budgets. */
    fun decodeWorkCommand(bytes: ByteArray,limits: JsonLimits,bodyBytes: Int,unavailable: Boolean=false,
        consume: (Long,Long)->Unit={ _,_ -> }): JsonValue {
        require(bodyBytes>0)
        return decodeBounded(bytes,limits,unavailable,consume,bodyBytes)
    }

    private fun decodeBounded(bytes: ByteArray,limits: JsonLimits,unavailable: Boolean,
        consume: (Long,Long)->Unit,attachmentBodyBytes: Int?): JsonValue {
        val exhausted = if (unavailable) CodecProblem.RESOURCE_UNAVAILABLE else CodecProblem.ADMISSION_LIMIT
        if (bytes.size > limits.bytes) refuse(exhausted)
        if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) refuse(CodecProblem.MALFORMED)
        // Fixed scratch buffer validates raw UTF-8 without a second whole decoded copy.
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes); val chars = CharBuffer.allocate(4096)
        while (true) {
            val result = decoder.decode(input, chars, true)
            if (result.isError) refuse(CodecProblem.MALFORMED)
            if (result.isUnderflow) break
            chars.clear()
        }
        var stringMaximum=limits.stringBytes
        fun maximum(path: Int)=if(path==4 && attachmentBodyBytes!=null)attachmentBodyBytes else limits.stringBytes
        val configured=StreamReadConstraints.builder().maxNestingDepth(limits.depth).maxStringLength(maxOf(limits.stringBytes,attachmentBodyBytes ?: limits.stringBytes))
            .maxNameLength(limits.nameBytes).maxNumberLength(20).maxTokenCount(limits.tokens.toLong()).build()
        // Numeric bounds are format laws, not an operator resource shortage. The pinned library's
        // public validation callbacks keep their refusal separate without inspecting error messages.
        val constraints=object: StreamReadConstraints(configured.maxNestingDepth,configured.maxDocumentLength,
            configured.maxNumberLength,configured.maxStringLength,configured.maxNameLength,configured.maxTokenCount) {
            override fun validateIntegerLength(length: Int) { if(length>20)refuse(CodecProblem.INVALID_SHAPE) }
            override fun validateFPLength(length: Int) { refuse(CodecProblem.MALFORMED) }
            // Jackson's growing UTF-16 buffer is bounded before materializing parser.text. UTF-8
            // byte accounting below remains authoritative for multibyte/supplementary characters.
            override fun validateStringLength(length: Int) { if(length>stringMaximum)refuse(exhausted) }
        }
        val factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).streamReadConstraints(constraints).build()
        var count = 0
        fun text(s: String, name: Boolean = false,maximum: Int=limits.stringBytes): String {
            val size = utf8Size(s)
            if (size > if (name) limits.nameBytes else maximum) refuse(exhausted)
            consume(0, size)
            return s
        }
        try {
            factory.createParser(bytes).use { parser ->
                fun next(): JsonToken? {
                    val t = parser.nextToken()
                    if (t != null) { if (++count > limits.tokens) refuse(exhausted); consume(1, 0) }
                    return t
                }
                fun nextValue(path: Int): JsonToken? { stringMaximum=maximum(path); return next() }
                // Path states: root0 -> payload1 -> bodies2 -> array item3 -> body4.
                // All other edges stay -1, including arrays/objects nested under the body field.
                fun value(t: JsonToken?, depth: Int,path: Int): JsonValue = when (t) {
                    JsonToken.START_OBJECT -> {
                        if (depth > limits.depth) refuse(exhausted)
                        val map = LinkedHashMap<String, JsonValue>()
                        while (next() != JsonToken.END_OBJECT) {
                            if (parser.currentToken() != JsonToken.FIELD_NAME) refuse(CodecProblem.MALFORMED)
                            if (map.size >= limits.collection) refuse(exhausted)
                            val key = text(parser.currentName(), true)
                            if (key in map) refuse(CodecProblem.MALFORMED)
                            val child=when { path==0 && key=="payload" -> 1; path==1 && key=="bodies" -> 2; path==3 && key=="body" -> 4; else -> -1 }
                            map[key] = value(nextValue(child), depth+1,child)
                        }
                        JObject(map)
                    }
                    JsonToken.START_ARRAY -> {
                        if (depth > limits.depth) refuse(exhausted)
                        val list = ArrayList<JsonValue>()
                        while (true) {
                            val child=if(path==2)3 else -1
                            val token = nextValue(child); if (token == JsonToken.END_ARRAY) break
                            if (list.size >= limits.collection) refuse(exhausted)
                            list.add(value(token, depth+1,child))
                        }
                        JArray(list)
                    }
                    JsonToken.VALUE_STRING -> { stringMaximum=maximum(path); JString(text(parser.text,maximum=stringMaximum)) }
                    JsonToken.VALUE_NUMBER_INT -> {
                        val spelling = parser.text
                        if (!spelling.matches(Regex("0|-?[1-9][0-9]*"))) refuse(CodecProblem.MALFORMED)
                        JNumber(spelling.toLongOrNull() ?: refuse(CodecProblem.INVALID_SHAPE))
                    }
                    JsonToken.VALUE_TRUE -> JBoolean(true)
                    JsonToken.VALUE_FALSE -> JBoolean(false)
                    JsonToken.VALUE_NULL -> JNull
                    else -> refuse(CodecProblem.MALFORMED)
                }
                val result = value(nextValue(0), 1,0)
                if (next() != null) refuse(CodecProblem.MALFORMED)
                return result
            }
        } catch (_: com.fasterxml.jackson.core.exc.StreamConstraintsException) { refuse(exhausted)
        } catch (_: JacksonException) { refuse(CodecProblem.MALFORMED) }
    }

    fun encode(value: JsonValue, maximumBytes: Int = 16*1024*1024, unavailable: Boolean = false, maxDepth: Int = 128): ByteArray {
        require(maximumBytes > 0)
        val exhausted = if (unavailable) CodecProblem.RESOURCE_UNAVAILABLE else CodecProblem.ADMISSION_LIMIT
        if(value.depth>maxDepth)refuse(exhausted)
        val output = object : ByteArrayOutputStream() {
            override fun write(b: Int) { if (count >= maximumBytes) refuse(exhausted); super.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { if (len > maximumBytes-count) refuse(exhausted); super.write(b, off, len) }
        }
        val factory = JsonFactory.builder().streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(maxDepth).build())
            .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE).disable(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
            .disable(JsonWriteFeature.ESCAPE_NON_ASCII).enable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8).build()
        factory.createGenerator(output).use { g ->
            g.characterEscapes = object : CharacterEscapes() {
                private val codes = standardAsciiEscapesForJSON().also { for (i in 0..31) it[i] = ESCAPE_CUSTOM }
                override fun getEscapeCodesForAscii() = codes
                override fun getEscapeSequence(ch: Int): SerializableString? = if (ch in 0..31) SerializedString("\\u00"+ch.toString(16).padStart(2,'0')) else null
            }
            fun write(v: JsonValue) {
                when (v) {
                    is JObject -> { g.writeStartObject(); v.entries.keys.sortedWith(scalarOrder).forEach { utf8Size(it); g.writeFieldName(it); write(v.entries.getValue(it)) }; g.writeEndObject() }
                    is JArray -> { g.writeStartArray(); v.values.forEach(::write); g.writeEndArray() }
                    is JString -> { utf8Size(v.value); g.writeString(v.value) }
                    is JNumber -> g.writeNumber(v.value)
                    is JBoolean -> g.writeBoolean(v.value)
                    JNull -> g.writeNull()
                }
            }
            write(value)
        }
        return output.toByteArray()
    }
    private val scalarOrder = Comparator<String> { a,b ->
        val x = a.codePoints().iterator(); val y = b.codePoints().iterator(); var c = 0
        while (c == 0 && x.hasNext() && y.hasNext()) c = x.nextInt().compareTo(y.nextInt())
        if (c != 0) c else x.hasNext().compareTo(y.hasNext())
    }
}
internal fun obj(vararg fields: Pair<String, JsonValue>) = JObject(linkedMapOf(*fields))
internal fun str(s: String) = JString(s)
internal fun num(n: Int) = JNumber(n.toLong())
internal fun JsonValue.fields(vararg names: String): Map<String, JsonValue> {
    val f = (this as? JObject)?.entries ?: refuse(CodecProblem.INVALID_SHAPE); shape(f.keys == names.toSet()); return f
}
internal fun JsonValue.string() = (this as? JString)?.value ?: refuse(CodecProblem.INVALID_SHAPE)
internal fun JsonValue.long() = (this as? JNumber)?.value?.takeIf { it >= 0 } ?: refuse(CodecProblem.INVALID_SHAPE)
internal fun JsonValue.int() = long().also { shape(it <= Int.MAX_VALUE) }.toInt()
internal fun JsonValue.list() = (this as? JArray)?.values ?: refuse(CodecProblem.INVALID_SHAPE)
internal fun version(v: JsonValue) { if (v.long() != 1L) refuse(CodecProblem.UNSUPPORTED_FORMAT) }
