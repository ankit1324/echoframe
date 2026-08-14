package com.nothingai.capture.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapDecodeTest {

    // --- display path (edge budget) -----------------------------------------

    @Test
    fun keepsImagesAlreadyWithinTheDisplayBudget() {
        assertEquals(1, sampleSizeFor(800, 600, MAX_DISPLAY_EDGE))
        assertEquals(1, sampleSizeFor(1440, 1000, MAX_DISPLAY_EDGE))
    }

    @Test
    fun halvesUntilTheLongestEdgeFitsTheDisplayBudget() {
        assertEquals(4, sampleSizeFor(1440, 3120, MAX_DISPLAY_EDGE))
        assertEquals(8, sampleSizeFor(4000, 6000, MAX_DISPLAY_EDGE))
    }

    @Test
    fun edgeBudgetIsOrientationIndependent() {
        assertEquals(4, sampleSizeFor(3120, 1440, MAX_DISPLAY_EDGE))
        assertEquals(4, sampleSizeFor(1440, 3120, MAX_DISPLAY_EDGE))
    }

    // --- analysis path (area budget) ----------------------------------------

    @Test
    fun ordinaryScreenshotsReachOcrAtFullResolution() {
        // ~4.5MP. The previous edge-based budget halved this and pushed small UI text under
        // ML Kit's readable floor, defeating the OCR it was supposed to protect.
        assertEquals(1, sampleSizeForPixels(1440, 3120, MAX_ANALYSIS_PIXELS))
        assertEquals(1, sampleSizeForPixels(2560, 1600, MAX_ANALYSIS_PIXELS))
        assertEquals(1, sampleSizeForPixels(2208, 1840, MAX_ANALYSIS_PIXELS))
    }

    @Test
    fun genuinelyHugeImagesAreStillSubsampled() {
        assertEquals(2, sampleSizeForPixels(4000, 6000, MAX_ANALYSIS_PIXELS))     // 24MP -> 6.0MP
        assertEquals(8, sampleSizeForPixels(12000, 12000, MAX_ANALYSIS_PIXELS))   // 144MP -> 2.25MP; 4 would leave 9MP
    }

    @Test
    fun analysisBudgetIsNeverExceeded() {
        for ((w, h) in listOf(1440 to 3120, 4000 to 6000, 12000 to 12000, 20000 to 3)) {
            val sample = sampleSizeForPixels(w, h, MAX_ANALYSIS_PIXELS)
            val pixels = (w / sample).toLong() * (h / sample)
            assertTrue("$w x $h sampled by $sample = $pixels", pixels <= MAX_ANALYSIS_PIXELS)
        }
    }

    // --- shared guards ------------------------------------------------------

    @Test
    fun undecodableBoundsFallBackToFullResolution() {
        assertEquals(1, sampleSizeFor(0, 0, MAX_DISPLAY_EDGE))
        assertEquals(1, sampleSizeFor(-1, 100, MAX_DISPLAY_EDGE))
        assertEquals(1, sampleSizeForPixels(0, 0, MAX_ANALYSIS_PIXELS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveEdgeBudget() {
        sampleSizeFor(100, 100, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositivePixelBudget() {
        sampleSizeForPixels(100, 100, 0)
    }

    // --- thumbnail sizing ---------------------------------------------------

    @Test
    fun thumbnailFitsTheRequestedHeight() {
        val (w, h) = thumbnailSize(1440, 3120, maxHeightPx = 396)
        assertEquals(396, h)
        assertEquals(182, w)
    }

    @Test
    fun thumbnailClampsWidthForExtremeAspectRatios() {
        // Scaling by height alone would ask for 1080 * 396 px of width here.
        val (w, h) = thumbnailSize(1080, 1, maxHeightPx = 396)
        assertTrue("width $w must stay within the edge cap", w <= MAX_DISPLAY_EDGE)
        assertTrue("height $h must stay positive", h >= 1)
    }

    @Test
    fun thumbnailNeverUpscales() {
        assertEquals(40 to 20, thumbnailSize(40, 20, maxHeightPx = 396))
    }

    @Test
    fun thumbnailDegradesSafelyOnBadInput() {
        assertEquals(1 to 1, thumbnailSize(0, 0, maxHeightPx = 396))
        assertEquals(1 to 1, thumbnailSize(-5, 10, maxHeightPx = 396))
    }

    @Test
    fun thumbnailStaysWithinAFixedByteBudget() {
        val maxBytes = 4 * 1024 * 1024
        for ((w, h) in listOf(1440 to 3120, 1 to 4000, 4000 to 1, 12000 to 12000, 1080 to 1)) {
            val (tw, th) = thumbnailSize(w, h, maxHeightPx = 396)
            assertTrue("$w x $h -> $tw x $th", tw.toLong() * th * 4 <= maxBytes)
        }
    }
}
