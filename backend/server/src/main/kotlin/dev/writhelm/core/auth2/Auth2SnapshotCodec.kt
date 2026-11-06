// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.auth2

import dev.writhelm.core.codec.*
import dev.writhelm.core.workflow.AccessPolicy
import java.util.UUID

/** Internal consumed-entry stream. Caller must confirm whole-store coverage/end at captured H;
 * this codec owns only auth, and never opens host files or resets an incomplete namespace. */
internal class Auth2SnapshotCodec {
    private val codec=Auth2RecordCodec()
    fun restore(storeId: UUID, basis: String, entries: Sequence<Pair<String,ByteArray>>, policy: AccessPolicy,
        work: AuthWorkProjection): Auth2State {
        var bytes=0L; var header=false; val records=ArrayList<Auth2Record>(); val paths=HashSet<String>()
        for((path,content) in entries) {
            if(content.size>67108864L-bytes || paths.size>=8193) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
            bytes+=content.size; graph(paths.add(path))
            if(path=="auth/format.json") { graph(!header); codec.checkHeader(content); shape(content.contentEquals(codec.header())); header=true }
            else {
                if(records.size>=8192) refuse(CodecProblem.RESOURCE_UNAVAILABLE)
                records.add(codec.decode(content,path))
            }
        }
        graph(header)
        return Auth2State.checked(storeId,basis,records,policy,work)
    }
    fun export(state: Auth2State): Sequence<Pair<String,ByteArray>> = sequence {
        yield("auth/format.json" to codec.header())
        for(record in state.records().sortedBy { codec.path(it) }) yield(codec.path(record) to codec.encode(record))
    }
}
