// SPDX-License-Identifier: AGPL-3.0-only
package dev.writhelm.core.codec

import dev.writhelm.core.workflow.StructuredAnswer
import org.junit.jupiter.api.Test
import kotlin.test.*

class ReasonedRejectionTest {
    @Test fun reasonIsCanonicalAndLegacyBytesRemainDistinct() {
        val legacy="""{"kind":"reject"}"""
        assertEquals(legacy,StrictJson.encode(answer(answer(StrictJson.decode(legacy.toByteArray())))).decodeToString())
        val reason="Нужно уточнить границы.\r\nБез новых полномочий."
        val value=StructuredAnswer.RejectWithReason(reason)
        assertEquals(value,answer(answer(value)))
        assertNotEquals(StructuredAnswer.Reject,answer(answer(value)))
        assertEquals(reason,(answer(answer(value)) as StructuredAnswer.RejectWithReason).reason)
    }
    @Test fun absentBlankOversizedAndExcessReasonFieldsRefuse() {
        for(raw in listOf(
            """{"kind":"reject-with-reason"}""",
            """{"kind":"reject-with-reason","reason":"  "}""",
            """{"kind":"reject-with-reason","reason":"valid","actor":"owner"}"""
        ))assertFails {answer(StrictJson.decode(raw.toByteArray()))}
        assertFails {StructuredAnswer.RejectWithReason("я".repeat(2049))}
    }
}
