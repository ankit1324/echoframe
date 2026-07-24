package com.nothingai.capture.ui.wizard

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nothingai.capture.ui.settings.SettingsPrefs
import java.io.File

data class SetupState(val isAssistant: Boolean, val hasMic: Boolean, val hasModel: Boolean) {
    val ready get() = isAssistant && hasMic && hasModel
}

object SetupChecks {
    fun read(context: Context): SetupState {
        val rm = context.getSystemService(RoleManager::class.java)
        val isAssistant = rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val hasMic = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val modelId = SettingsPrefs.get(context).getString("whisper_model", "tiny") ?: "tiny"
        val hasModel = File(context.filesDir, "models/ggml-$modelId.bin").exists()
        return SetupState(isAssistant, hasMic, hasModel)
    }
}
