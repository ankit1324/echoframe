package com.nothingai.capture.stt

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ModelDownloaderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MODEL_ID = "modelId"
        const val KEY_PROGRESS = "progress"
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val model = WhisperModel.values().firstOrNull { it.id == id } ?: return Result.failure()

        val dir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val out = File(dir, model.filename)
        if (out.exists()) return Result.success()

        val tmp = File(dir, "${model.filename}.tmp")
        val client = OkHttpClient()
        val request = Request.Builder().url(model.url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.retry()
                val body = response.body ?: return Result.failure()
                val length = body.contentLength()
                val source = body.source()

                FileOutputStream(tmp).use { fos ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L
                    var lastProgress = -1
                    
                    while (source.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        totalRead += read
                        if (length > 0) {
                            val progress = ((totalRead * 100) / length).toInt()
                            if (progress != lastProgress) {
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                                lastProgress = progress
                            }
                        }
                    }
                    fos.flush()
                }
            }
            if (tmp.renameTo(out)) {
                retranscribeAwaiting(model.id)
                return Result.success()
            }
            return Result.failure()
        } catch (e: Exception) {
            tmp.delete()
            return Result.retry()
        }
    }

    /**
     * When the model that finished downloading is the one currently selected,
     * re-queue every capture that was left FAILED/PENDING (e.g. recorded before
     * the model was on-device) so it transcribes automatically — no manual retry.
     */
    private suspend fun retranscribeAwaiting(downloadedId: String) {
        val prefs = applicationContext.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
        val selected = prefs.getString("whisper_model", "tiny") ?: "tiny"
        if (selected != downloadedId) return

        val dao = CaptureDatabase.get(applicationContext).captureDao()
        val ids = dao.idsAwaitingTranscription()
        if (ids.isEmpty()) return

        val wm = WorkManager.getInstance(applicationContext)
        for (captureId in ids) {
            dao.updateStatus(captureId, CaptureStatus.PENDING)
            wm.enqueue(
                OneTimeWorkRequestBuilder<TranscribeWorker>()
                    .setInputData(workDataOf(TranscribeWorker.KEY_ID to captureId))
                    .build()
            )
        }
    }
}
