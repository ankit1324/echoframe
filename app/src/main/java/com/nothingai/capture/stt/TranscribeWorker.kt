package com.nothingai.capture.stt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nothingai.capture.R
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.data.CaptureCategory
import com.nothingai.capture.data.categoryFor
import kotlinx.coroutines.CancellationException

/** Keeps its historical name so persisted WorkManager requests remain loadable after upgrading. */
class TranscribeWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ID = "captureId"
        private const val NOTIF_CHANNEL = "transcribe"
        private const val NOTIF_ID = 1001
        private const val MAX_ATTEMPTS = 2

        /**
         * Enqueues analysis for [id] as *unique* work, so a user-tapped retry or a recovery sweep
         * can never run concurrently with an attempt already in flight for the same capture —
         * two workers would otherwise race on the same row and transcript file.
         *
         * Use [ExistingWorkPolicy.KEEP] to defer to work already queued (recovery), and
         * [ExistingWorkPolicy.REPLACE] when the user explicitly asked for a fresh attempt.
         */
        fun enqueue(context: Context, id: String, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "transcribe-$id",
                policy,
                OneTimeWorkRequestBuilder<TranscribeWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(workDataOf(KEY_ID to id))
                    .build(),
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIF_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "Analyzing note", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(applicationContext.getString(R.string.analyzing_notification_title))
            .setContentText("Reading screenshot…")
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notification)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val storage = CaptureStorage(applicationContext)
        val dao = CaptureDatabase.get(applicationContext).captureDao()
        val screenshot = storage.screenshotFile(id)
        val audio = storage.audioFile(id)

        return try {
            if (!screenshot.isFile) {
                val note = if (audio.isFile) {
                    "Screenshot unavailable. Audio recording saved."
                } else {
                    "Screenshot unavailable."
                }
                storage.saveTranscript(id, note)
                dao.updateTranscript(id, note, CaptureStatus.DONE)
                dao.updateTitle(id, "Audio note".takeIf { audio.isFile } ?: "Image note")
                dao.updateCategory(id, CaptureCategory.OTHER.name)
                return Result.success()
            }

            dao.updateStatus(id, CaptureStatus.TRANSCRIBING)
            val analysis = ImageAnalyzer().analyze(screenshot)
            val note = formatImageNote(analysis.text, analysis.labels)
            storage.saveTranscript(id, note)
            dao.updateTranscript(id, note, CaptureStatus.DONE)
            dao.updateTitle(id, titleFromImageAnalysis(analysis.text, analysis.labels))
            dao.updateCategory(id, categoryFor(analysis.text, analysis.labels.joinToString { it.text }).name)
            Result.success()
        } catch (cancellation: CancellationException) {
            // Cooperative cancellation is not a failure; WorkManager will reschedule us.
            throw cancellation
        } catch (failure: Throwable) {
            // Throwable, not Exception: a full-size bitmap decode fails with OutOfMemoryError, and
            // letting that escape leaves the capture stuck at TRANSCRIBING with no recovery.
            Log.e("TranscribeWorker", "Screenshot analysis failed for $id", failure)
            if (failure !is OutOfMemoryError && runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                dao.updateStatus(id, CaptureStatus.FAILED)
                Result.failure()
            }
        }
    }
}
