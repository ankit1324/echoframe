package com.nothingai.capture.stt

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAnalyzerTest {
    @Test
    fun formatsTextAndRankedUniqueLabels() {
        val note = formatImageNote(
            text = "Invoice #1042\nTotal: \$45.00",
            labels = listOf(
                ImageLabelResult("Document", 0.91f),
                ImageLabelResult("Paper", 0.82f),
                ImageLabelResult("document", 0.75f),
                ImageLabelResult("Receipt", 0.69f),
            ),
        )

        assertEquals(
            "Invoice #1042\nTotal: \$45.00\n\nImage labels: Document, Paper",
            note,
        )
    }

    @Test
    fun formatsLabelsWhenNoTextIsReadable() {
        assertEquals(
            "Image labels: Screen, Electronics",
            formatImageNote(
                text = "",
                labels = listOf(
                    ImageLabelResult("Electronics", 0.72f),
                    ImageLabelResult("Screen", 0.96f),
                ),
            ),
        )
    }

    @Test
    fun usesSafeFallbackWhenAnalysisIsEmpty() {
        assertEquals(
            "No readable text or image labels found.",
            formatImageNote("", emptyList()),
        )
    }

    @Test
    fun titleUsesFirstTextLineThenBestLabel() {
        assertEquals(
            "Invoice #1042",
            titleFromImageAnalysis("Invoice #1042\nTotal: \$45.00", emptyList()),
        )
        assertEquals(
            "Document",
            titleFromImageAnalysis("", listOf(ImageLabelResult("document", 0.91f))),
        )
        assertEquals("Image note", titleFromImageAnalysis("", emptyList()))
    }

    @Test
    fun cropRegionConvertsNormalizedBoundsToPixels() {
        assertEquals(
            PixelCrop(left = 20, top = 20, width = 60, height = 60),
            CropRegion(left = .2f, top = .1f, right = .8f, bottom = .4f).toPixels(100, 200),
        )
    }

    @Test
    fun separatesEditableTextFromReadOnlyLabels() {
        val note = splitImageNote("Invoice\nTotal: \$450\n\nImage labels: Document, Paper")
        assertEquals("Invoice\nTotal: \$450", note.text)
        assertEquals(listOf("Document", "Paper"), note.labels)
        assertEquals("Updated total\n\nImage labels: Document, Paper", mergeImageNote("Updated total", note.labels))
    }

    @Test
    fun separatesLabelsOnlyNote() {
        assertEquals(
            EditableImageNote("", listOf("Screen", "Electronics")),
            splitImageNote("Image labels: Screen, Electronics"),
        )
    }
}
