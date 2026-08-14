package com.nothingai.capture.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownExportTest {
    @Test
    fun exportsTitleMetadataNoteAndSource() {
        val markdown = captureToMarkdown(
            title = "Invoice",
            timestamp = "2026-07-26 22:30",
            note = "Total: ₹450\n\nImage labels: Document",
            sourceUrl = "https://example.com/invoice",
        )

        assertEquals(
            "# Invoice\n\nCaptured: 2026-07-26 22:30\nSource: https://example.com/invoice\n\n## Note\n\nTotal: ₹450\n\nImage labels: Document\n",
            markdown,
        )
    }

    @Test
    fun sanitizesMarkdownFilename() {
        assertEquals("Invoice _ July.md", markdownFilename("Invoice / July"))
        assertEquals("capture.md", markdownFilename("   "))
    }
}
