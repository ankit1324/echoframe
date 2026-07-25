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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureId
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.stt.TranscribeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureSession(context: Context) : VoiceInteractionSession(context) {
    private val storage = CaptureStorage(context)
    private val dao = CaptureDatabase.get(context).captureDao()
    private val recorder = AudioRecorder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var captureId: String? = null
    @Volatile private var hasScreenshot = false
    @Volatile private var sourcePackage: String? = null
    @Volatile private var sourceUrl: String? = null
    @Volatile private var startMs = 0L
    private var timerView: TextView? = null
    private var pulseView: android.view.View? = null
    private var pulseAnim: ObjectAnimator? = null

    @Volatile private var finished = false
    private val ui = Handler(Looper.getMainLooper())
    private var pulseState = false

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
            setOnClickListener { finishCapture() }
        }
        card.addView(stopBtn)

        root.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(24)
            leftMargin = dp(24)
            rightMargin = dp(24)
        })

        return root
    }

    private fun ensureCaptureId(): String = captureId ?: CaptureId.from(System.currentTimeMillis()).also { captureId = it }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        val id = ensureCaptureId()
        if (screenshot != null) {
            hasScreenshot = true
            io.launch {
                try { storage.saveScreenshot(id, screenshot) } catch (e: Exception) { Log.e("CaptureAssistant", "onHandleScreenshot failed", e) }
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
        val screenshot = hasScreenshot
        val pkg = sourcePackage
        val url = sourceUrl
        Log.d("CaptureAssistant", "onShow: start capture id=$id hasScreenshot=$screenshot")
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
                dao.upsert(Capture(id, ts, screenshot, 0, null, CaptureStatus.RECORDING,
                    sourcePackage = pkg, sourceUrl = url))
                recorder.start(storage.audioFile(id))
            } catch (e: Exception) {
                Log.e("CaptureAssistant", "onShow: failed to start recording id=$id", e)
                try { withContext(NonCancellable) { dao.updateStatus(id, CaptureStatus.FAILED) } } catch (_: Exception) {}
                captureId = null
                hasScreenshot = false
                sourcePackage = null
                sourceUrl = null
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
        if (!finished) finalizeCapture()
        super.onHide()
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnim?.cancel()
        io.cancel()
    }

    private fun finalizeCapture() {
        if (finished) return
        val id = captureId ?: return
        finished = true
        ui.removeCallbacks(tick)
        val ts = startMs
        val screenshot = hasScreenshot
        val pkg = sourcePackage
        val url = sourceUrl
        io.launch(start = CoroutineStart.ATOMIC) {
            try {
                withContext(NonCancellable) {
                    val duration = recorder.stop()
                    dao.upsert(Capture(id, ts, screenshot, duration, null, CaptureStatus.PENDING,
                        sourcePackage = pkg, sourceUrl = url))
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<TranscribeWorker>()
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .setInputData(workDataOf(TranscribeWorker.KEY_ID to id))
                            .build()
                    )
                }
            } catch (e: Exception) {
                try { withContext(NonCancellable) { dao.updateStatus(id, CaptureStatus.FAILED) } } catch (_: Exception) {}
            } finally {
                finished = false
                captureId = null
                hasScreenshot = false
                sourcePackage = null
                sourceUrl = null
            }
        }
    }
}
