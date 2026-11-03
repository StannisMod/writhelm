// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import org.junit.jupiter.api.Test
import kotlin.test.*

class StrictJsonTest {
    @Test fun `independent canonical control and scalar ordering golden`() {
        val input = "{\"z\":9223372036854775807,\"a\":\"\\n\\r\\t\\b\\f\\u0000\\u001f/\\\"\\\\é🙂\"}"
        assertEquals("{\"a\":\"\\u000a\\u000d\\u0009\\u0008\\u000c\\u0000\\u001f/\\\"\\\\é🙂\",\"z\":9223372036854775807}", canonical(input))
        assertEquals("{\"\ue000\":0,\"🙂\":1}", canonical("{\"🙂\":1,\"\ue000\":0}"))
    }
    @Test fun `strict syntax Unicode integer and nested escaped duplicates refuse`() {
        listOf("{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}", "{\"x\":[{\"a\":1,\"\\u0061\":2}]}",
            "{} {}", "{\"a\":-0}", "{\"a\":1.0}", "{\"a\":1e0}", "{\"a\":01}", "{\"a\":9223372036854775808}",
            "{\"a\":\"\\uD800\"}", "{\"\\uDC00\":1}", "{\"a\":1,}", "{/*x*/\"a\":1}", "\ufeff{}", "[true,]", "", "{\"x\":NaN}")
            .forEach { assertFailsWith<CodecFault>(it) { StrictJson.decode(it.toByteArray()) } }
        listOf(byteArrayOf(0xc0.toByte(), 0x80.toByte()), byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()), byteArrayOf(0xf0.toByte(), 0x9f.toByte()))
            .forEach { assertEquals(CodecProblem.MALFORMED, assertFailsWith<CodecFault> { StrictJson.decode(it) }.problem) }
    }
    @Test fun `every parser budget has small below exact above boundaries`() {
        fun boundary(input: String, n: Int, limit: (Int) -> JsonLimits) {
            for (max in n-1..n+1) {
                if (max < n) assertEquals(CodecProblem.ADMISSION_LIMIT,
                    assertFailsWith<CodecFault> { StrictJson.decode(input.toByteArray(), limit(max)) }.problem)
                else StrictJson.decode(input.toByteArray(), limit(max))
            }
        }
        boundary("{}", 2) { JsonLimits(bytes = it) }
        boundary("\"é\"", 2) { JsonLimits(stringBytes = it) }
        boundary("{\"é\":0}", 2) { JsonLimits(nameBytes = it) }
        boundary("[[],[]]", 2) { JsonLimits(collection = it) }
        boundary("[[]]", 2) { JsonLimits(depth = it) }
        boundary("[0]", 3) { JsonLimits(tokens = it) }
        for (max in 1..3) {
            if (max < 2) assertFailsWith<CodecFault> { StrictJson.encode(JObject(emptyMap()), max) }
            else assertEquals("{}", StrictJson.encode(JObject(emptyMap()), max).decodeToString())
        }
    }
    @Test fun `decoded values freeze caller and exposed collections`() {
        val input = "{\"a\":[0]}".toByteArray()
        val result = StrictJson.decode(input) as JObject
        input.fill(0)
        assertEquals("{\"a\":[0]}", StrictJson.encode(result).decodeToString())
        assertFailsWith<UnsupportedOperationException> { (result.entries as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> { ((result.entries.getValue("a") as JArray).values as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (CoreRecordCodec.families as MutableSet).clear() }
    }
    @Test fun `very long integers are invalid format not insufficient restore resources`() {
        for(unavailable in listOf(false,true)) assertEquals(CodecProblem.INVALID_SHAPE,
            assertFailsWith<CodecFault> { StrictJson.decode(("{\"x\":"+"9".repeat(100)+"}").toByteArray(),unavailable=unavailable) }.problem)
    }
    private fun canonical(s: String) = StrictJson.encode(StrictJson.decode(s.toByteArray())).decodeToString()
}
