package com.nothingai.capture.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class CaptureSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d("CaptureAssistant", "CaptureSessionService.onNewSession")
        return CaptureSession(this)
    }
}
