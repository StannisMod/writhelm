// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/** Parser-boundary probe only: all effect/auth ports deliberately unreachable, no native or Git. */
class H1BodyAdmissionProbeTest {
    private class TypedCodecReached : RuntimeException("typed codec reached")

    @Test fun `legal body text above ordinary string cap reaches actual typed admission`() {
        val root = checkNotNull(System.getenv("WRITHELM_H1_BODY_PROBE_ROOT"))
        val bytes = Files.readAllBytes(Path.of(root).resolve("valid-checkpoint-70000.json"))
        val bindings = H1PublicationBindings(
            canonicalize = { throw TypedCodecReached() },
            authorize = { _, _ -> error("no authorization in this parser probe") },
            allowedWrites = { _, _ -> error("no writes in this parser probe") },
            validateRecord = { _, _, _ -> error("no record validation in this parser probe") },
            preflight = { _, _, _ -> error("no publication in this parser probe") },
            validateBoundary = { _, _, _ -> error("no boundary in this parser probe") },
        )
        assertFailsWith<TypedCodecReached> { StoreCommand.decodeH1(bytes, bindings) }
    }
}
