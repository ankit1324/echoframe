package com.nothingai.capture.stt

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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

    companion object {
        const val KEY_ID = "captureId"
        private const val NOTIF_CHANNEL = "transcribe"
        private const val NOTIF_ID = 1001
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(NOTIF_CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "Transcribing note", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("notes-ai")
            .setContentText("Turning your voice into a note…")
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notif)
    }

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
            dao.updateTitle(id, titleFromTranscript(text))
            Result.success()
        } catch (e: ModelNotDownloadedException) {
            // Selected model isn't on disk yet (first run, mid-download, or user
            // switched to a model they haven't fetched). Not a crash — fail this
            // work request; it's retryable once the model finishes downloading.
            Log.w("TranscribeWorker", "model ${e.modelId} not downloaded, failing capture $id")
            dao.updateStatus(id, CaptureStatus.FAILED)
            Result.failure()
        } catch (e: Exception) {
            dao.updateStatus(id, CaptureStatus.FAILED)
            Result.failure()
        }
    }
}
