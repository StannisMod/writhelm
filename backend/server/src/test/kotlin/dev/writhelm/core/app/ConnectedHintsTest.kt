// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ConnectedHintsTest {
    @Test fun hintsCoalesceWhileScanIsBlockedAndCloseRejectsFurtherHints() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val scans=AtomicInteger()
        val hints=ConnectedHints {
            if(scans.incrementAndGet()==1) { entered.countDown();check(release.await(10,TimeUnit.SECONDS)) }
        }
        try {
            hints.hint();assertTrue(entered.await(10,TimeUnit.SECONDS))
            repeat(100000) { hints.hint() }
            release.countDown();hints.awaitIdle()
            assertEquals(2,scans.get(),"no per-publication queue")
        } finally { release.countDown();hints.close() }
        hints.hint();assertEquals(2,scans.get())
    }
    @Test fun failedScanIsVisibleAndDoesNotHotRetryWithoutAnotherHint() {
        val scans=AtomicInteger()
        ConnectedHints { scans.incrementAndGet();error("capture refused") }.use { hints ->
            hints.hint();hints.awaitIdle()
            assertEquals("capture refused",hints.lastFailure?.message)
            assertEquals(1,scans.get())
        }
    }
}
