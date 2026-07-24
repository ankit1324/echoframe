package com.nothingai.capture.stt

enum class WhisperModel(val id: String, val label: String, val sizeMb: Int, val url: String) {
    TINY("tiny", "Tiny (Fastest)", 39, "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"),
    BASE("base", "Base (Balanced)", 53, "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"),
    SMALL("small", "Small (Accurate)", 183, "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"),
    LARGE("large", "Large v3 (Best)", 1110, "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin");

    val filename get() = "ggml-$id.bin"
}
