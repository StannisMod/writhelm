// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.app

import dev.writhelm.core.work.*

/** Concrete unqualified-platform observer. No external source, writer, native completion or
 * execution profile is asserted merely because a fresh H1 store can be opened. */
internal object UnavailableH1RuntimeFacts: H1RuntimeFacts {
    override fun capture(state: H1BoundState,command: WorkCommand)=H1ObservedFacts(state.basis,
        WorkExternalFacts(emptySet(),emptySet(),emptySet(),emptyMap(),emptyMap(),emptySet(),emptySet(),emptySet(),
            emptySet(),emptyMap(),emptyMap(),emptySet()),emptySet())
}
