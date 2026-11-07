// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One lifecycle-owned worker and one coalesced hint. Publication never runs bridge I/O or waits
 * for this worker under the application's monitor. Hints have no authority and need no persistence:
 * every invocation recaptures the canonical frontier, including after restart. */
internal class ConnectedHints(private val scan: ()->Unit): AutoCloseable {
    private val monitor=Object()
    private var dirty=false
    private var scheduled=false
    private var closed=false
    private var failure: Throwable?=null
    val lastFailure: Throwable? get()=synchronized(monitor) { failure }
    private val worker=Executors.newSingleThreadExecutor { task ->
        Thread(task,"writhelm-connected-frontier").apply { isDaemon=true }
    }
    fun hint()=synchronized(monitor) {
        if(closed)return@synchronized
        dirty=true
        if(!scheduled) {
            scheduled=true
            worker.execute(::drain)
        }
    }
    private fun drain() {
        while(true) {
            synchronized(monitor) {
                if(closed || !dirty) { scheduled=false;monitor.notifyAll();return }
                dirty=false
            }
            try { scan();synchronized(monitor) { failure=null } }
            catch(e: Exception) { synchronized(monitor) { failure=e } }
        }
    }
    /** Bounded component-test/controlled shutdown barrier, never called by publication callbacks. */
    fun awaitIdle() {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
        synchronized(monitor) {
            while(scheduled) {
                val remaining=deadline-System.nanoTime()
                check(remaining>0) { "connected scan did not settle" }
                TimeUnit.NANOSECONDS.timedWait(monitor,remaining)
            }
        }
    }
    override fun close() {
        synchronized(monitor) { closed=true;dirty=false }
        worker.shutdown()
        check(worker.awaitTermination(30,TimeUnit.SECONDS)) { "connected scan still owns effects" }
    }
}
