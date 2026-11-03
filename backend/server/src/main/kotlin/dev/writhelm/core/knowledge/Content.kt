// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.knowledge

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Exact byte identity only; never a claim about approval or semantic equivalence. */
@JvmInline
value class Digest private constructor(val hex: String) {
    internal fun bytes(): ByteArray = ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        fun parse(hex: String): Digest {
            require(hex.matches(Regex("[0-9a-f]{64}"))) { "Expected a canonical SHA-256 digest" }
            return Digest(hex)
        }

        fun of(bytes: ByteArray): Digest = Digest(
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        )
    }
}

/** Immutable exact UTF-8. Every byte array crossing this boundary is copied. */
class Utf8Content private constructor(private val data: ByteArray) {
    val sizeBytes: Int get() = data.size
    val digest: Digest = Digest.of(data)

    fun bytes(): ByteArray = data.copyOf()

    fun text(): String = data.toString(Charsets.UTF_8)

    fun slice(span: ByteSpan): Utf8Content {
        require(span.endExclusive <= data.size && isBoundary(span.start) && isBoundary(span.endExclusive)) {
            "Range is outside content or splits a UTF-8 codepoint"
        }
        return fromBytes(data.copyOfRange(span.start, span.endExclusive))
    }

    internal fun isBoundary(offset: Int): Boolean =
        offset in 0..data.size && (offset == data.size || (data[offset].toInt() and 0xc0) != 0x80)

    override fun equals(other: Any?): Boolean = other is Utf8Content && data.contentEquals(other.data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Utf8Content(sizeBytes=$sizeBytes, digest=${digest.hex})"

    companion object {
        fun fromBytes(bytes: ByteArray): Utf8Content {
            val copy = bytes.copyOf()
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(copy))
            } catch (error: CharacterCodingException) {
                throw IllegalArgumentException("Content is not valid UTF-8", error)
            }
            return Utf8Content(copy)
        }

        fun fromText(text: String): Utf8Content = fromBytes(encodeUtf8(text))
    }
}

internal fun encodeUtf8(text: String): ByteArray {
    try {
        val buffer = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    } catch (error: CharacterCodingException) {
        throw IllegalArgumentException("Text contains invalid Unicode", error)
    }
}

internal fun framedDigest(domain: String, write: DataOutputStream.() -> Unit): Digest {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
        output.writeFramed(encodeUtf8(domain))
        output.write()
    }
    return Digest.of(bytes.toByteArray())
}

internal fun DataOutputStream.writeFramed(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}
