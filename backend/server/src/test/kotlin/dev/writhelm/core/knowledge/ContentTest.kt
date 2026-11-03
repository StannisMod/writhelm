// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.security.MessageDigest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ContentTest {
    @Test
    fun `KC-01 known SHA-256 and exact ASCII bytes`() {
        val content = Utf8Content.fromBytes(byteArrayOf(97, 98, 99))
        assertEquals("abc", content.text())
        assertEquals(3, content.sizeBytes)
        assertContentEquals(byteArrayOf(97, 98, 99), content.bytes())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", content.digest.hex)
    }

    @TestFactory
    fun `KC-01 valid text regions round trip without normalization`(): List<DynamicTest> = listOf(
        "empty" to "",
        "CRLF" to "a\r\nb\r\n",
        "no final newline" to "a\nb",
        "final newline" to "a\nb\n",
        "non ASCII and supplementary" to "é α 🙂",
        "decomposed Unicode" to "e\u0301",
        "BOM" to "\uFEFFtext",
        "embedded zero codepoint" to "a\u0000b",
    ).map { (region, text) ->
        DynamicTest.dynamicTest(region) {
            val expected = text.toByteArray(Charsets.UTF_8)
            val content = Utf8Content.fromBytes(expected)
            assertContentEquals(expected, content.bytes())
            assertEquals(text, content.text())
            assertEquals(expected.size, content.sizeBytes)
            assertEquals(oracleSha(expected), content.digest.hex)
            assertEquals(content, Utf8Content.fromText(text))
        }
    }

    @Test
    fun `KC-01 byte-different equivalent-looking inputs keep different identities`() {
        assertNotEquals(Utf8Content.fromText("a\r\n").digest, Utf8Content.fromText("a\n").digest)
        assertNotEquals(Utf8Content.fromText("a").digest, Utf8Content.fromText("a\n").digest)
        assertNotEquals(Utf8Content.fromText("é").digest, Utf8Content.fromText("e\u0301").digest)
        assertNotEquals(Utf8Content.fromText("x").digest, Utf8Content.fromText("\uFEFFx").digest)
    }

    @TestFactory
    fun `KC-01 malformed UTF-8 is refused rather than repaired`(): List<DynamicTest> = listOf(
        "continuation alone" to byteArrayOf(0x80.toByte()),
        "overlong" to byteArrayOf(0xc0.toByte(), 0x80.toByte()),
        "truncated two byte" to byteArrayOf(0xc2.toByte()),
        "truncated three byte" to byteArrayOf(0xe2.toByte(), 0x82.toByte()),
        "encoded surrogate" to byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
        "outside Unicode" to byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
        "invalid leading byte" to byteArrayOf(0xff.toByte()),
    ).map { (region, bytes) ->
        DynamicTest.dynamicTest(region) { assertFailsWith<IllegalArgumentException> { Utf8Content.fromBytes(bytes) } }
    }

    @TestFactory
    fun `KC-01 unpaired text surrogates are refused`(): List<DynamicTest> = listOf("\uD800", "\uDC00", "a\uD800b").map { text ->
        DynamicTest.dynamicTest("unpaired code units ${text.map { it.code }}") {
            assertFailsWith<IllegalArgumentException> { Utf8Content.fromText(text) }
        }
    }

    @Test
    fun `KC-05 input output and slice arrays cannot mutate existing content`() {
        val source = "hello".toByteArray()
        val content = Utf8Content.fromBytes(source)
        val digest = content.digest
        source.fill(0)
        content.bytes().fill(0)
        val slice = content.slice(ByteSpan(1, 4))
        slice.bytes().fill(0)
        assertEquals("hello", content.text())
        assertEquals(digest, content.digest)
        assertEquals("ell", slice.text())
        assertEquals(Utf8Content.fromText("hello"), content)
        assertEquals(Utf8Content.fromText("hello").hashCode(), content.hashCode())
    }

    @Test
    fun `KC-01 slices reject partial codepoints and out of bounds without changing content`() {
        val content = Utf8Content.fromText("é🙂x")
        assertEquals("🙂", content.slice(ByteSpan(2, 6)).text())
        listOf(ByteSpan(0, 1), ByteSpan(1, 2), ByteSpan(2, 5), ByteSpan(0, 8), ByteSpan(9, 10)).forEach {
            assertFailsWith<IllegalArgumentException> { content.slice(it) }
        }
        assertEquals("é🙂x", content.text())
    }

    @Test
    fun `KC-01 digest parser is strict and canonical`() {
        val valid = "0123456789abcdef".repeat(4)
        assertEquals(valid, Digest.parse(valid).hex)
        listOf(valid.uppercase(), "0".repeat(63), "z".repeat(64), " $valid").forEach {
            assertFailsWith<IllegalArgumentException> { Digest.parse(it) }
        }
    }
}

internal fun oracleSha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
