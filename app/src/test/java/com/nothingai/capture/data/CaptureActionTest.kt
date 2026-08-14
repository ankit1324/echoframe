package com.nothingai.capture.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureActionTest {
    @Test
    fun extractsSupportedActionsInStableOrder() {
        val actions = extractCaptureActions(
            """
            Pay ₹1,249.50 before 28/07/2026 at 7:30 PM.
            Call +91 98765 43210 or visit example.com/order/42.
            Meet at 12 MG Road, Bengaluru.
            """.trimIndent()
        )

        assertEquals(
            listOf(
                CaptureAction.Amount("₹1,249.50"),
                CaptureAction.DateTime("28/07/2026", "7:30 PM"),
                CaptureAction.Phone("+91 98765 43210"),
                CaptureAction.Link("https://example.com/order/42"),
                CaptureAction.Address("12 MG Road, Bengaluru"),
            ),
            actions,
        )
    }

    @Test
    fun ignoresShortNumbersAndDuplicateLinks() {
        assertEquals(
            listOf(CaptureAction.Link("https://example.com")),
            extractCaptureActions("OTP 1234 https://example.com and example.com"),
        )
    }
}
