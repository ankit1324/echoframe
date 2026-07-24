package com.nothingai.capture.stt

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage

/**
 * Background transcription: reads the recorded WAV for a capture, runs it through
 * on-device whisper, and persists the resulting transcript + status. Enqueued by the
 * capture flow (Task 7) once recording finishes.
 */
class TranscribeWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object { const val KEY_ID = "captureId" }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val storage = CaptureStorage(applicationContext)
        val dao = CaptureDatabase.get(applicationContext).captureDao()
        val wav = storage.audioFile(id)
        if (!wav.exists()) {
            dao.updateStatus(id, CaptureStatus.FAILED)
            return Result.failure()
        }
        return try {
            dao.updateStatus(id, CaptureStatus.TRANSCRIBING)
            val text = WhisperTranscriber(applicationContext).transcribe(wav)
            storage.saveTranscript(id, text)
            dao.updateTranscript(id, text, CaptureStatus.DONE)
            Result.success()
        } catch (e: Exception) {
            dao.updateStatus(id, CaptureStatus.FAILED)
            Result.failure()
        }
    }
}
