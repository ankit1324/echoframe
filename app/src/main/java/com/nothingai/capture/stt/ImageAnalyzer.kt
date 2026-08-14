package com.nothingai.capture.stt

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nothingai.capture.util.decodeForAnalysis
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class ImageLabelResult(val text: String, val confidence: Float)

data class ImageAnalysis(val text: String, val labels: List<ImageLabelResult>)

data class EditableImageNote(val text: String, val labels: List<String>)

data class PixelCrop(val left: Int, val top: Int, val width: Int, val height: Int)

data class CropRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toPixels(width: Int, height: Int): PixelCrop {
        require(width > 0 && height > 0)
        val startX = (left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1)
        val startY = (top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
        val endX = minOf(width, maxOf(startX + 1, (right.coerceIn(0f, 1f) * width).toInt()))
        val endY = minOf(height, maxOf(startY + 1, (bottom.coerceIn(0f, 1f) * height).toInt()))
        return PixelCrop(startX, startY, endX - startX, endY - startY)
    }
}

class ImageAnalyzer {
    suspend fun analyze(imageFile: File, cropRegion: CropRegion? = null): ImageAnalysis {
        require(imageFile.isFile) { "Screenshot is missing" }
        val bitmap = decodeForAnalysis(imageFile)
            ?: error("Screenshot could not be decoded")
        val selectedBitmap = cropRegion?.toPixels(bitmap.width, bitmap.height)?.let {
            android.graphics.Bitmap.createBitmap(bitmap, it.left, it.top, it.width, it.height)
        } ?: bitmap
        val image = InputImage.fromBitmap(selectedBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_LABEL_CONFIDENCE).build()
        )

        return try {
            val text = recognizer.process(image).await().text.trim()
            val labels = labeler.process(image).await().map { ImageLabelResult(it.text, it.confidence) }
            ImageAnalysis(text, labels)
        } finally {
            recognizer.close()
            labeler.close()
            if (selectedBitmap !== bitmap) selectedBitmap.recycle()
            bitmap.recycle()
        }
    }
}

fun formatImageNote(text: String, labels: List<ImageLabelResult>): String {
    val cleanText = text.trim()
    val cleanLabels = rankedLabels(labels)
    return listOfNotNull(
        cleanText.takeIf { it.isNotEmpty() },
        cleanLabels.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Image labels: "),
    ).joinToString("\n\n").ifBlank { "No readable text or image labels found." }
}

fun splitImageNote(note: String): EditableImageNote {
    val marker = "\n\nImage labels: "
    val labelsOnlyMarker = "Image labels: "
    if (note.startsWith(labelsOnlyMarker)) {
        return EditableImageNote(
            text = "",
            labels = note.removePrefix(labelsOnlyMarker).split(',').map(String::trim).filter(String::isNotEmpty),
        )
    }
    val index = note.lastIndexOf(marker)
    if (index < 0) return EditableImageNote(note, emptyList())
    return EditableImageNote(
        text = note.substring(0, index),
        labels = note.substring(index + marker.length).split(',').map(String::trim).filter(String::isNotEmpty),
    )
}

fun mergeImageNote(text: String, labels: List<String>): String = listOfNotNull(
    text.trim(),
    labels.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Image labels: "),
).filter(String::isNotEmpty).joinToString("\n\n")

fun titleFromImageAnalysis(text: String, labels: List<ImageLabelResult>): String {
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    if (firstLine != null) return titleFromTranscript(firstLine)
    return rankedLabels(labels).firstOrNull()?.replaceFirstChar { it.titlecase() } ?: "Image note"
}

private fun rankedLabels(labels: List<ImageLabelResult>): List<String> = labels
    .asSequence()
    .filter { it.confidence >= MIN_LABEL_CONFIDENCE && it.text.isNotBlank() }
    .sortedByDescending(ImageLabelResult::confidence)
    .distinctBy { it.text.lowercase() }
    .take(MAX_LABELS)
    .map { it.text.trim().replaceFirstChar { first -> first.titlecase() } }
    .toList()

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
}

private const val MIN_LABEL_CONFIDENCE = 0.70f
private const val MAX_LABELS = 5
