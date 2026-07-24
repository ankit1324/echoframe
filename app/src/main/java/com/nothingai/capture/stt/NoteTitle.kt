package com.nothingai.capture.stt

/** Builds a short, readable title without sending the transcript off-device. */
fun titleFromTranscript(transcript: String): String {
    val clean = transcript.replace(Regex("\\s+"), " ").trim()
    if (clean.isBlank()) return "Untitled moment"

    val sentence = clean.substringBeforeAny(".", "?", "!").trim().ifBlank { clean }
    if (sentence.length <= 48) return sentence.replaceFirstChar { it.titlecase() }

    val words = sentence.split(' ').take(7)
    return words.joinToString(" ").trimEnd(',', ':', ';') + "…"
}

private fun String.substringBeforeAny(vararg delimiters: String): String {
    val index = delimiters.mapNotNull { indexOf(it).takeIf { position -> position >= 0 } }.minOrNull()
    return if (index == null) this else substring(0, index)
}
