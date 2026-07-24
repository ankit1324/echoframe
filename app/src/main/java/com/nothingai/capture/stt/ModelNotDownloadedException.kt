package com.nothingai.capture.stt

/**
 * Thrown by [WhisperTranscriber.getModelFile] when the currently-selected model
 * (read from the `whisper_model` preference) has not been downloaded yet into
 * `filesDir/models/`. Callers (e.g. [TranscribeWorker]) should treat this as a
 * retryable, user-facing condition rather than a crash: prompt a download
 * (Settings or the setup wizard) and try again once it completes.
 */
class ModelNotDownloadedException(val modelId: String) :
    Exception("Whisper model '$modelId' is not downloaded yet")
