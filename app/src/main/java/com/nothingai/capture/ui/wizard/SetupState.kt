package com.nothingai.capture.ui.wizard

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class SetupState(val isAssistant: Boolean, val hasMic: Boolean) {
    val ready get() = isAssistant && hasMic
}

object SetupChecks {
    fun read(context: Context): SetupState {
        val rm = context.getSystemService(RoleManager::class.java)
        val isAssistant = rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val hasMic = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return SetupState(isAssistant, hasMic)
    }
}
