// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.store

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Environment passes through Gradle to its forked test JVM without changing build configuration. */
internal fun storeTestDirectory(prefix: String): Path {
    val configured=System.getenv("WRITHELM_STORE_TEST_ROOT")
    return if(configured==null)Files.createTempDirectory(prefix) else {
        val root=Path.of(configured).toAbsolutePath().normalize()
        Files.createDirectories(root)
        Files.createTempDirectory(root,prefix)
    }
}

class StoreTestRootTest {
    @Test fun `test directories use configured root or standard temporary facility`() {
        val directory=storeTestDirectory("root-contract-")
        val expected=Path.of(System.getenv("WRITHELM_STORE_TEST_ROOT") ?: System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
        assertEquals(expected,directory.parent)
        assertTrue(Files.isDirectory(directory))
    }
}
