// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.auth2.AuthSessionBinding
import dev.writhelm.core.codec.*
import dev.writhelm.core.store.GitOid
import java.util.UUID

/** Derived selector-only protocol resources. Current READ/scope is checked by the actual caller
 * before EVERY page; full immutable scope is also part of the cursor identity, never cached rights. */
internal class H1Navigation(private val now: ()->Long={System.nanoTime()/1000000}) {
    private data class Listing(val id: UUID,val binding: AuthSessionBinding,val operation: String,val selector: String,
        val pageSize: Int,val basis: GitOid,val born: Long,val rows: List<JsonValue>)
    private data class Cursor(val listing: Listing,val offset: Int)
    private val listings=linkedMapOf<UUID,Listing>();private val cursors=linkedMapOf<UUID,Cursor>()
    fun page(binding: AuthSessionBinding,basis: GitOid,operation: String,selector: String,size: Int,cursor: UUID?,
        rows: ()->List<JsonValue>): Pair<GitOid,JsonValue> {
        if(size !in 1..32)throw H1ReadFault("INVALID_INPUT")
        val time=now()
        listings.values.filter { time-it.born>=300000 }.map { it.id }.forEach { id ->
            listings.remove(id);cursors.entries.removeIf { it.value.listing.id==id }
        }
        val (listing,offset)=if(cursor!=null) {
            val c=cursors[cursor] ?: throw H1ReadFault("SNAPSHOT_EXPIRED");val l=c.listing
            if(l.binding!=binding || l.operation!=operation || l.selector!=selector || l.pageSize!=size)throw AppAccessFault(AppAccessProblem.FORBIDDEN)
            l to c.offset
        } else {
            if(listings.size>=2)throw H1ReadFault("UNAVAILABLE")
            val values=rows()
            if(values.size>8192)throw H1ReadFault("UNAVAILABLE")
            StrictJson.encode(JArray(values),1024*1024,true)
            Listing(UUID.randomUUID(),binding,operation,selector,size,basis,time,values.toList()).also { listings[it.id]=it } to 0
        }
        val end=minOf(offset+size,listing.rows.size)
        val next=if(end==listing.rows.size)null else {
            if(cursors.size>=512)throw H1ReadFault("UNAVAILABLE")
            UUID.randomUUID().also { cursors[it]=Cursor(listing,end) }
        }
        if(next==null && offset==0)listings.remove(listing.id)
        return listing.basis to obj("snapshotId" to uuid(listing.id),"nextCursor" to (next?.let(::uuid) ?: JNull),
            "complete" to JBoolean(next==null),"items" to JArray(listing.rows.subList(offset,end)))
    }
}
