package com.nothingai.capture.ui.wizard

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SetupWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SetupChecks.read(context)) }
    fun refresh() { state = SetupChecks.read(context) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)

        StepRow("1. Grant microphone", state.hasMic) {
            micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        StepRow("2. Set as digital assistant", state.isAssistant) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            } else {
                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
        }
        StepRow("3. Power-hold → assistant", true) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS)) // deep-link varies by OEM
        }
        Text(
            "On step 3, open System → Gestures → Press & hold power button, " +
                "and choose \"Digital assistant\". Also enable \"Use screenshot\" in " +
                "assistant settings so screen captures work.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(enabled = state.ready, onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.ready) "Done" else "Complete steps 1–2 to continue")
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (done) "✓ $label" else label)
        if (!done) Button(onClick = onClick) { Text("Open") }
    }
}
