// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** In-process bounded ownership, not an executor, queue, authority cache or recovery mechanism. */
internal class HttpAdmission(private val now: ()->Long=System::nanoTime) {
    enum class Lane { STATEFUL, CONTROL }
    enum class Refusal { TOTAL, STATEFUL, CONTROL, RATE, BYTES, FENCED }
    data class Attempt(val lease: Lease?,val refusal: Refusal?)
    data class Snapshot(val total: Int,val stateful: Int,val control: Int,val bytes: Int,val messages: Int,val fenced: Boolean)
    private var total=0;private var stateful=0;private var control=0;private var bytes=0
    private var window=now();private var messages=0;private var fenced=false
    companion object {
        const val SMALL_REPLY_BYTES=65536
        const val INITIAL_BYTES=2*SMALL_REPLY_BYTES
        const val REPLY_BYTES=16*1024*1024
        const val LIFETIME_MILLIS=90000L
        const val DELIVERY_MILLIS=10000L
    }
    @Synchronized fun enter(lane: Lane): Attempt {
        fun no(reason: Refusal)=Attempt(null,reason)
        if(total==4)return no(Refusal.TOTAL)
        if(lane==Lane.STATEFUL && fenced)return no(Refusal.FENCED)
        if(lane==Lane.STATEFUL && stateful!=0)return no(Refusal.STATEFUL)
        if(lane==Lane.CONTROL && control!=0)return no(Refusal.CONTROL)
        if(INITIAL_BYTES>REPLY_BYTES-bytes)return no(Refusal.BYTES)
        val time=now()
        if(time-window>=1000000000L) { window=time;messages=0 }
        if(messages==32)return no(Refusal.RATE)
        messages++;total++;bytes+=INITIAL_BYTES
        if(lane==Lane.STATEFUL)stateful++ else control++
        return Attempt(Lease(lane,time),null)
    }
    @Synchronized fun snapshot()=Snapshot(total,stateful,control,bytes,messages,fenced)
    inner class Lease internal constructor(val lane: Lane,private val born: Long): AutoCloseable {
        private var held=true;private var done=false;private var reserved=INITIAL_BYTES;private var emergency=SMALL_REPLY_BYTES
        fun remainingMillis(): Long=maxOf(0,LIFETIME_MILLIS-(now()-born)/1000000L)
        suspend fun <T> protectedWork(block: suspend ()->T): T=withContext(NonCancellable) { block() }
        fun planBytes(size: Int): Boolean=synchronized(this@HttpAdmission) {
            check(!done)
            if(size<0 || size>REPLY_BYTES-SMALL_REPLY_BYTES || maxOf(SMALL_REPLY_BYTES,size)+SMALL_REPLY_BYTES-reserved>REPLY_BYTES-bytes)false
            else { val target=maxOf(SMALL_REPLY_BYTES,size)+SMALL_REPLY_BYTES;bytes+=target-reserved;reserved=target;true }
        }
        fun reserveFallback(size: Int): Boolean=synchronized(this@HttpAdmission) {
            check(!done)
            if(size<0 || size>emergency)false else { emergency-=size;true }
        }
        /** Called only after immutable planning and SDK/service-owned context cleanup. */
        fun finishWork()=synchronized(this@HttpAdmission) { check(!done);releaseWork() }
        /** An incomplete SDK operation may still own shared context; never silently reuse it. */
        fun fence()=synchronized(this@HttpAdmission) { check(!done && held && lane==Lane.STATEFUL);fenced=true }
        private fun releaseWork() {
            if(held) {
                if(lane==Lane.CONTROL)control-- else if(!fenced)stateful--
                held=false
            }
        }
        override fun close()=synchronized(this@HttpAdmission) {
            if(!done) { releaseWork();total--;bytes-=reserved;done=true }
        }
    }
}
