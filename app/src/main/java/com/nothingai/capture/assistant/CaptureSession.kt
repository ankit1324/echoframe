package com.nothingai.capture.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureId
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.stt.TranscribeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The assistant capture session UI + pipeline.
 *
 * Lifecycle when the OS invokes us as the assistant:
 *  onHandleScreenshot(bitmap) -> save screenshot.png (or hasScreenshot=false if null; the
 *      FLAG_SECURE / headless fallback)
 *  onShow -> show "● Recording" timer + STOP button, start audio, write RECORDING row
 *  STOP tap -> save audio, write PENDING row, enqueue TranscribeWorker, hide()
 */
class CaptureSession(context: Context) : VoiceInteractionSession(context) {
    private val storage = CaptureStorage(context)
    private val dao = CaptureDatabase.get(context).captureDao()
    private val recorder = AudioRecorder()
    private val io = CoroutineScope(Dispatchers.IO)

    // Written only on the main thread (onShow / onHandleScreenshot); read on IO threads,
    // so @Volatile for visibility. Generated once and never overwritten so the screenshot,
    // audio and Capture row all share one id regardless of callback ordering.
    @Volatile private var captureId: String? = null
    @Volatile private var hasScreenshot = false
    private var timerView: TextView? = null
    private var startMs = 0L
    private var finished = false

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val secs = (System.currentTimeMillis() - startMs) / 1000
            timerView?.text = "● Recording ${secs}s"
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreateContentView() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.argb(220, 0, 0, 0))
        setPadding(48, 96, 48, 96)
        gravity = Gravity.CENTER
        timerView = TextView(context).apply {
            text = "● Recording 0s"; setTextColor(Color.WHITE); textSize = 22f
        }
        addView(timerView)
        addView(Button(context).apply {
            text = "STOP"
            setOnClickListener { finishCapture() }
        })
    }

    /** Main-thread only (both callers are OS main-thread callbacks): assign the id once. */
    private fun ensureCaptureId(): String =
        captureId ?: CaptureId.from(System.currentTimeMillis()).also { captureId = it }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        val id = ensureCaptureId()
        if (screenshot != null) {
            hasScreenshot = true
            io.launch { storage.saveScreenshot(id, screenshot) }
            Log.d("CaptureAssistant", "onHandleScreenshot: saved screenshot id=$id")
        } else {
            Log.d("CaptureAssistant", "onHandleScreenshot: null bitmap, audio-only id=$id")
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val id = ensureCaptureId()
        startMs = System.currentTimeMillis()
        Log.d("CaptureAssistant", "onShow: start capture id=$id hasScreenshot=$hasScreenshot")
        ui.post(tick)
        io.launch {
            dao.upsert(Capture(id, startMs, hasScreenshot, 0, null, CaptureStatus.RECORDING))
            recorder.start(storage.audioFile(id))
            Log.d("CaptureAssistant", "onShow: RECORDING row written + recorder started id=$id")
        }
    }

    private fun finishCapture() {
        if (finished) return
        finished = true
        ui.removeCallbacks(tick)
        val id = captureId ?: return
        Log.d("CaptureAssistant", "finishCapture: stopping id=$id")
        io.launch {
            val duration = recorder.stop()
            dao.upsert(Capture(id, startMs, hasScreenshot, duration, null, CaptureStatus.PENDING))
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<TranscribeWorker>()
                    .setInputData(workDataOf(TranscribeWorker.KEY_ID to id))
                    .build()
            )
            Log.d(
                "CaptureAssistant",
                "finishCapture: PENDING row written + TranscribeWorker enqueued id=$id durationMs=$duration"
            )
        }
        hide()
    }
}
