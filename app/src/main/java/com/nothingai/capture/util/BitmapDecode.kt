package com.nothingai.capture.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Pixel budget for bitmaps handed to ML Kit.
 *
 * Budgeted by AREA, not by longest edge: memory scales with area, so an edge cap punishes tall
 * screenshots for their aspect ratio. A 1440x3120 phone screenshot is only ~4.5MP and stays at full
 * resolution here, which matters because OCR needs the detail — ML Kit wants roughly 16px of text
 * height, and halving a screenshot drops ordinary 22px UI captions below that. Genuinely huge images
 * still get subsampled.
 */
const val MAX_ANALYSIS_PIXELS = 6_000_000

/** Edge cap for on-screen previews, which never need more than the display's own resolution. */
const val MAX_DISPLAY_EDGE = 1440

/**
 * Power-of-two subsample factor that brings the longest edge within [maxEdge].
 * Pure, so the sizing policy is testable without a device.
 */
fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    require(maxEdge > 0) { "maxEdge must be positive" }
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / sample > maxEdge) sample *= 2
    return sample
}

/** Power-of-two subsample factor that brings the total pixel count within [maxPixels]. */
fun sampleSizeForPixels(width: Int, height: Int, maxPixels: Int): Int {
    require(maxPixels > 0) { "maxPixels must be positive" }
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while ((width / sample).toLong() * (height / sample) > maxPixels) sample *= 2
    return sample
}

/**
 * Target size for a preview thumbnail, clamped on BOTH axes.
 *
 * Scaling by height alone lets a pathological aspect ratio (a 1080x1 source) request a gigantic
 * width, which fails as an [OutOfMemoryError] rather than something catchable. Never upscales.
 */
fun thumbnailSize(
    srcWidth: Int,
    srcHeight: Int,
    maxHeightPx: Int,
    maxEdgePx: Int = MAX_DISPLAY_EDGE,
): Pair<Int, Int> {
    require(maxHeightPx > 0 && maxEdgePx > 0) { "bounds must be positive" }
    if (srcWidth <= 0 || srcHeight <= 0) return 1 to 1
    val scale = minOf(
        maxHeightPx.toFloat() / srcHeight,
        maxEdgePx.toFloat() / srcWidth,
        1f,
    )
    return (srcWidth * scale).toInt().coerceIn(1, maxEdgePx) to
        (srcHeight * scale).toInt().coerceIn(1, maxEdgePx)
}

/** Decodes [file] for on-screen preview. Returns null when the file is not a decodable image. */
fun decodeForDisplay(file: File): Bitmap? =
    decode(file) { w, h -> sampleSizeFor(w, h, MAX_DISPLAY_EDGE) }

/** Decodes [file] for ML Kit analysis, preserving as much detail as the pixel budget allows. */
fun decodeForAnalysis(file: File): Bitmap? =
    decode(file) { w, h -> sampleSizeForPixels(w, h, MAX_ANALYSIS_PIXELS) }

private fun decode(file: File, sampleSize: (Int, Int) -> Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
