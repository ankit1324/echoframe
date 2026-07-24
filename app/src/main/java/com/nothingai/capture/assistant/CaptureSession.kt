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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The assistant capture session UI + pipeline.
 *
 * Lifecycle when the OS invokes us as the assistant:
 *  onHandleScreenshot(bitmap) -> save screenshot.png (or hasScreenshot=false if null; the
 *      FLAG_SECURE / headless fallback)
 *  onShow -> show "● Recording" timer + STOP button, start audio, write RECORDING row
 *  STOP tap -> finalizeCapture() (save audio, write PENDING row, enqueue TranscribeWorker) + hide()
 *  onHide (ANY dismissal: home/back/lock/focus loss) -> finalizeCapture() if not already finished
 *
 * This is the system default-assistant session, so a crash or a leaked mic here takes down the
 * assistant. Two hard rules follow from that:
 *   - Every [io] launch body is wrapped in try/catch; a thrown exception must never escape.
 *   - The finalize body runs under [NonCancellable] so session teardown ([onDestroy] -> io.cancel())
 *     cannot corrupt a half-written capture.
 *
 * KNOWN LIMITATION (session reuse): the per-capture state reset happens at the end of
 * [finalizeCapture], on an IO thread. If a reused session delivers the NEXT capture's
 * onHandleScreenshot before onShow — and before that reset has landed — [ensureCaptureId] can
 * still observe the previous (already-finalized) id and bind the new screenshot to it. This
 * onHandleScreenshot-before-onShow race is rare and left undefended on purpose; the common
 * onShow-first reuse path is clean because the reset runs before the next onShow's id lookup in
 * practice.
 */
class CaptureSession(context: Context) : VoiceInteractionSession(context) {
    private val storage = CaptureStorage(context)
    private val dao = CaptureDatabase.get(context).captureDao()
    private val recorder = AudioRecorder()

    // SupervisorJob so a failure in one launch does not cancel siblings; each body also
    // try/catches so nothing escapes to crash the host assistant process.
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Written on the main thread (onShow / onHandleScreenshot) and, at reset time, on an IO
    // thread; read on IO threads -> @Volatile for visibility. The id is generated once per
    // capture and never overwritten mid-capture, so the screenshot, audio and Capture row all
    // share one id regardless of callback ordering. Reset to null after a capture finalizes so
    // a reused session (some OEMs call onShow again on the same instance) starts clean.
    @Volatile private var captureId: String? = null
    @Volatile private var hasScreenshot = false
    @Volatile private var startMs = 0L
    private var timerView: TextView? = null

    // Guards finalizeCapture so it runs at most once per capture. Reset to false when the
    // finalize completes so a reused session can capture again.
    @Volatile private var finished = false

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

    /** Main-thread only (all callers are OS main-thread callbacks): assign a fresh id if none. */
    private fun ensureCaptureId(): String =
        captureId ?: CaptureId.from(System.currentTimeMillis()).also { captureId = it }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        val id = ensureCaptureId()
        if (screenshot != null) {
            hasScreenshot = true
            io.launch {
                try {
                    storage.saveScreenshot(id, screenshot)
                } catch (e: Exception) {
                    Log.e("CaptureAssistant", "onHandleScreenshot: save failed id=$id", e)
                }
            }
            Log.d("CaptureAssistant", "onHandleScreenshot: saved screenshot id=$id")
        } else {
            Log.d("CaptureAssistant", "onHandleScreenshot: null bitmap, audio-only id=$id")
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val id = ensureCaptureId()
        startMs = System.currentTimeMillis()
        // Snapshot cross-thread state on the main thread before dispatching to IO.
        val ts = startMs
        val screenshot = hasScreenshot
        Log.d("CaptureAssistant", "onShow: start capture id=$id hasScreenshot=$screenshot")
        ui.post(tick)
        io.launch {
            try {
                dao.upsert(Capture(id, ts, screenshot, 0, null, CaptureStatus.RECORDING))
                recorder.start(storage.audioFile(id))
                Log.d("CaptureAssistant", "onShow: RECORDING row written + recorder started id=$id")
            } catch (e: Exception) {
                // recorder.start() (or the RECORDING upsert) failed: never leave a fake
                // "● Recording" overlay up. Mark FAILED and tear the UI down.
                Log.e("CaptureAssistant", "onShow: failed to start recording id=$id", e)
                try {
                    withContext(NonCancellable) { dao.updateStatus(id, CaptureStatus.FAILED) }
                } catch (_: Exception) {
                }
                // Nothing was recorded, so there is nothing for finalize to do. Clear state so
                // the onHide() triggered by hide() is a no-op and a reused session starts clean.
                captureId = null
                hasScreenshot = false
                ui.post {
                    ui.removeCallbacks(tick)
                    hide()
                }
            }
        }
    }

    /** STOP button: finalize then dismiss our own overlay. */
    private fun finishCapture() {
        finalizeCapture()
        hide()
    }

    /**
     * onHide fires on ANY dismissal (home, back, lock, focus loss), not just our own hide().
     * Finalize if the STOP path has not already done so. Never call hide() from here.
     */
    override fun onHide() {
        if (!finished) finalizeCapture()
        super.onHide()
    }

    /**
     * Safe to cancel [io] here: the finalize body runs under NonCancellable, so a cancel cannot
     * interrupt the essential stop-recorder / write-row / enqueue work.
     */
    override fun onDestroy() {
        super.onDestroy()
        io.cancel()
    }

    /**
     * Stop the recorder, write the PENDING row and enqueue transcription. Does NOT call hide().
     * Runs at most once per capture (guarded by [finished]). The essential work runs under
     * NonCancellable so it survives session teardown; recorder.stop() (a blocking thread join)
     * runs first, then the DB upsert + WorkManager enqueue. Per-capture state is reset at the
     * very end (after the NonCancellable work) so an in-flight finalize keeps using the right id
     * while a subsequent onShow on a reused session starts clean.
     */
    private fun finalizeCapture() {
        if (finished) return
        val id = captureId ?: return
        finished = true
        ui.removeCallbacks(tick)
        // Snapshot cross-thread state on the main thread before dispatching to IO.
        val ts = startMs
        val screenshot = hasScreenshot
        Log.d("CaptureAssistant", "finalizeCapture: stopping id=$id")
        io.launch {
            try {
                withContext(NonCancellable) {
                    val duration = recorder.stop()
                    dao.upsert(Capture(id, ts, screenshot, duration, null, CaptureStatus.PENDING))
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<TranscribeWorker>()
                            .setInputData(workDataOf(TranscribeWorker.KEY_ID to id))
                            .build()
                    )
                    Log.d(
                        "CaptureAssistant",
                        "finalizeCapture: PENDING row written + TranscribeWorker enqueued id=$id durationMs=$duration"
                    )
                }
            } catch (e: Exception) {
                Log.e("CaptureAssistant", "finalizeCapture failed id=$id", e)
                try {
                    withContext(NonCancellable) { dao.updateStatus(id, CaptureStatus.FAILED) }
                } catch (_: Exception) {
                }
            } finally {
                // Reset per-capture state for session reuse. Done last so the finalize above
                // used the correct id; a reused onShow will now generate a fresh id.
                finished = false
                captureId = null
                hasScreenshot = false
            }
        }
    }
}
