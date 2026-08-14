package com.nothingai.capture.ui.wizard

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nothingai.capture.ui.theme.semantic

@Composable
fun SetupWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val mutedText = MaterialTheme.semantic.mutedText
    var state by remember { mutableStateOf(SetupChecks.read(context)) }
    fun refresh() { state = SetupChecks.read(context) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        StarDoodle(modifier = Modifier.size(50.dp))
        Spacer(Modifier.height(18.dp))
        Text("Hello there.", style = MaterialTheme.typography.displayMedium)
        Text("Let’s get your digital notebook ready.", style = MaterialTheme.typography.bodyLarge, color = mutedText)
        Spacer(Modifier.height(36.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StepCard(
                title = "Allow microphone",
                desc = "So we can hear your thoughts.",
                icon = { Icon(Icons.Outlined.MicNone, null, tint = MaterialTheme.colorScheme.onSurface) },
                done = state.hasMic,
                onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
            )
            StepCard(
                title = "Set as digital assistant",
                desc = "To capture anywhere you are.",
                icon = { Icon(Icons.Outlined.SettingsSuggest, null, tint = MaterialTheme.colorScheme.onSurface) },
                done = state.isAssistant,
                onClick = {
                    val rm = context.getSystemService(RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                    } else {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    }
                }
            )
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("3. Hold power button", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "After completing step 2, open System → Gestures → Press & hold power button, and choose \"Digital assistant\". Enable \"Use screenshot\" in assistant settings.",
                        style = MaterialTheme.typography.bodySmall, color = mutedText
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
                        Text("Open settings")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            enabled = state.ready,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(if (state.ready) "Start capturing" else "Complete steps to continue", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepCard(
    title: String,
    desc: String,
    icon: @Composable () -> Unit,
    done: Boolean,
    buttonLabel: String = "Open",
    buttonEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val semantic = MaterialTheme.semantic
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (done) semantic.warningSoft else semantic.cardSurface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (done) Icon(Icons.Outlined.CheckCircleOutline, null, tint = semantic.onSoft, modifier = Modifier.size(28.dp)) else icon()
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = semantic.mutedText)
            }
            if (!done) {
                OutlinedButton(onClick = onClick, enabled = buttonEnabled, shape = RoundedCornerShape(12.dp)) { Text(buttonLabel) }
            }
        }
    }
}

@Composable
private fun StarDoodle(modifier: Modifier = Modifier) {
    val strokeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * .5f, h * .1f)
            quadraticTo(w * .5f, h * .45f, w * .1f, h * .5f)
            quadraticTo(w * .5f, h * .55f, w * .5f, h * .9f)
            quadraticTo(w * .5f, h * .55f, w * .9f, h * .5f)
            quadraticTo(w * .5f, h * .45f, w * .5f, h * .1f)
            close()
        }
        drawPath(path, color = strokeColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}
