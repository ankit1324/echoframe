package com.nothingai.capture.assistant

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.ExistingWorkPolicy
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureId
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.stt.TranscribeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class CaptureSession(context: Context) : VoiceInteractionSession(context) {
    private val storage = CaptureStorage(context)
    private val dao = CaptureDatabase.get(context).captureDao()
    private val recorder = AudioRecorder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var captureId: String? = null
    @Volatile private var sourcePackage: String? = null
    @Volatile private var sourceUrl: String? = null
    @Volatile private var startMs = 0L
    private var timerView: TextView? = null
    private var pulseView: android.view.View? = null
    private var pulseAnim: ObjectAnimator? = null
    private var previewView: ImageView? = null

    /**
     * Claimed by whichever of save/discard runs first, so a capture can never be both saved and
     * deleted — the two can otherwise race across the UI thread and the IO scope.
     */
    private val settled = AtomicBoolean(false)
    @Volatile private var screenshotSaveJob: Job? = null
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    override fun onCreateContentView(): android.view.View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(180, 0, 0, 0)) // dark translucent overlay
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(40), dp(32), dp(40))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F0E8")) // Parchment
                cornerRadius = dp(24).toFloat()
            }
        }

        val pulse = android.view.View(context).apply {
            pulseView = this
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#D4735E")) // Coral
            }
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                bottomMargin = dp(16)
            }
        }
        card.addView(pulse)

        // Shows what is about to be saved. The assistant can be triggered by accident over a PIN
        // pad, an OTP or a private message, so the user must be able to see it and back out.
        previewView = ImageView(context).apply {
            visibility = android.view.View.GONE
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(132)).apply {
                bottomMargin = dp(14)
            }
        }
        card.addView(previewView)

        timerView = TextView(context).apply {
            text = "Listening… 0s"
            setTextColor(Color.parseColor("#2C2C2C")) // Ink
            textSize = 24f
            setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(24)
            }
        }
        card.addView(timerView)

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val discardBtn = TextView(context).apply {
            text = "Discard"
            setTextColor(Color.parseColor("#2C2C2C"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EDE7DB")) // ParchmentDark
                cornerRadius = dp(16).toFloat()
            }
            isClickable = true
            contentDescription = "Discard this capture"
            setOnClickListener { discardCapture(); hide() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(12) }
        }
        buttons.addView(discardBtn)

        val stopBtn = TextView(context).apply {
            text = "Save Note"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2C"))
                cornerRadius = dp(16).toFloat()
            }
            isClickable = true
            contentDescription = "Save this capture"
            setOnClickListener { finishCapture() }
        }
        buttons.addView(stopBtn)
        card.addView(buttons)

        root.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(24)
            leftMargin = dp(24)
            rightMargin = dp(24)
        })

        return root
    }

    private fun ensureCaptureId(): String = captureId ?: CaptureId.from(System.currentTimeMillis()).also {
        captureId = it
        settled.set(false)
    }

    /** Grants the caller sole ownership of the current capture, or null if it is already settled. */
    private fun claimCapture(): String? {
        val id = captureId ?: return null
        return if (settled.compareAndSet(false, true)) id else null
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        val id = ensureCaptureId()
        if (screenshot != null) {
            val previousSave = screenshotSaveJob
            screenshotSaveJob = io.launch(start = CoroutineStart.ATOMIC) {
                withContext(NonCancellable) {
                    previousSave?.join()
                    try { storage.saveScreenshot(id, screenshot) } catch (e: Exception) { Log.e("CaptureAssistant", "onHandleScreenshot failed", e) }
                }
                // Scaled copy for the consent preview; the source bitmap belongs to the framework.
                val thumbnail = try {
                    val scale = dp(132).toFloat() / screenshot.height.coerceAtLeast(1)
                    Bitmap.createScaledBitmap(
                        screenshot,
                        (screenshot.width * scale).toInt().coerceAtLeast(1),
                        (screenshot.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } catch (e: Exception) {
                    Log.e("CaptureAssistant", "thumbnail failed", e); null
                }
                if (thumbnail != null) ui.post {
                    previewView?.apply { setImageBitmap(thumbnail); visibility = android.view.View.VISIBLE }
                }
            }
        }
    }

    override fun onHandleAssist(state: VoiceInteractionSession.AssistState) {
        try {
            sourcePackage = state.assistStructure?.activityComponent?.packageName
            sourceUrl = state.assistContent?.webUri?.toString()
        } catch (t: Throwable) {
            Log.e("CaptureAssistant", "onHandleAssist failed", t)
        }
        super.onHandleAssist(state)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val id = ensureCaptureId()
        startMs = System.currentTimeMillis()
        val ts = startMs
        val pkg = sourcePackage
        val url = sourceUrl
        Log.d("CaptureAssistant", "onShow: start capture id=$id")
        ui.post {
            pulseView?.let { pulse ->
                pulseAnim?.cancel()
                pulseAnim = ObjectAnimator.ofPropertyValuesHolder(
                    pulse,
                    PropertyValuesHolder.ofFloat("alpha", 1f, 0.35f),
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 1.45f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 1.45f),
                ).apply {
                    duration = 900
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        }
        ui.post(tick)
        io.launch {
            try {
                dao.upsert(Capture(id, ts, false, 0, null, CaptureStatus.RECORDING,
                    sourcePackage = pkg, sourceUrl = url))
                recorder.start(storage.audioFile(id))
            } catch (e: Exception) {
                Log.e("CaptureAssistant", "onShow: failed to start recording id=$id", e)
                // Settle it here so the hide() below cannot also try to discard the same capture.
                settled.set(true)
                try { withContext(NonCancellable) { dao.updateStatus(id, CaptureStatus.FAILED) } } catch (_: Exception) {}
                resetSessionState()
                ui.post { ui.removeCallbacks(tick); hide() }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val secs = (System.currentTimeMillis() - startMs) / 1000
            timerView?.text = "Listening… ${secs}s"
            ui.postDelayed(this, 1000)
        }
    }

    private fun finishCapture() {
        finalizeCapture()
        hide()
    }

    override fun onHide() {
        pulseAnim?.cancel()
        pulseAnim = null
        // Dismissing without tapping Save discards. An assistant triggered by accident over a PIN
        // pad, an OTP or a private message must not leave a screenshot and a recording of the room
        // on disk; saving is the deliberate action, not the default one.
        discardCapture()
        super.onHide()
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnim?.cancel()
        // Runs ATOMIC + NonCancellable, so it still completes despite the cancel below.
        discardCapture()
        io.cancel()
    }

    /** Deletes everything this session captured. No-op once the capture has been saved. */
    private fun discardCapture() {
        val id = claimCapture() ?: return
        ui.removeCallbacks(tick)
        io.launch(start = CoroutineStart.ATOMIC) {
            try {
                withContext(NonCancellable) {
                    recorder.stop()
                    screenshotSaveJob?.join()
                    storage.deleteCapture(id)
                    dao.delete(id)
                }
                Log.d("CaptureAssistant", "discarded capture id=$id")
            } catch (e: Exception) {
                Log.e("CaptureAssistant", "discard failed for id=$id", e)
            } finally {
                resetSessionState()
            }
        }
    }

    private fun resetSessionState() {
        captureId = null
        screenshotSaveJob = null
        sourcePackage = null
        sourceUrl = null
        ui.post { previewView?.apply { setImageBitmap(null); visibility = android.view.View.GONE } }
    }

    private fun finalizeCapture() {
        val id = claimCapture() ?: return
        ui.removeCallbacks(tick)
        val ts = startMs
        val pkg = sourcePackage
        val url = sourceUrl
        io.launch(start = CoroutineStart.ATOMIC) {
            try {
                withContext(NonCancellable) {
                    val duration = recorder.stop()
                    screenshotSaveJob?.join()
                    val screenshot = storage.screenshotFile(id).isFile
                    val updated = dao.finalizeRecording(
                        id = id,
                        hasScreenshot = screenshot,
                        durationMs = duration,
                        status = CaptureStatus.PENDING,
                        sourcePackage = pkg,
                        sourceUrl = url,
                    )
                    if (updated == 0) {
                        // onShow's insert never landed — recreate the row so the capture isn't lost.
                        dao.upsert(Capture(id, ts, screenshot, duration, null, CaptureStatus.PENDING,
                            sourcePackage = pkg, sourceUrl = url))
                    }
                    TranscribeWorker.enqueue(context, id, ExistingWorkPolicy.KEEP)
                }
            } catch (e: Exception) {
                try { withContext(NonCancellable) { dao.updateStatus(id, CaptureStatus.FAILED) } } catch (_: Exception) {}
            } finally {
                resetSessionState()
            }
        }
    }
}
