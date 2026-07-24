package com.nothingai.capture.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

class CaptureInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.d("CaptureAssistant", "CaptureInteractionService.onReady")
    }
}
