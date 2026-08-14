package com.nothingai.capture.data

fun captureToMarkdown(title: String, timestamp: String, note: String, sourceUrl: String?): String = buildString {
    append("# ").append(title.trim().ifBlank { "Untitled moment" }).append("\n\n")
    append("Captured: ").append(timestamp).append('\n')
    sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { append("Source: ").append(it).append('\n') }
    append("\n## Note\n\n").append(note.trim()).append('\n')
}

fun markdownFilename(title: String): String = title
    .trim()
    .ifBlank { "capture" }
    .replace(Regex("[\\/:*?\"<>|]"), "_")
    .take(80)
    .plus(".md")
