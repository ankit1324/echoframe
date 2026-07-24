package com.nothingai.capture.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nothingai.capture.stt.ModelDownloaderWorker
import com.nothingai.capture.stt.WhisperModel
import java.io.File

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nothingai.capture.ui.theme.InkLight
import com.nothingai.capture.ui.theme.ParchmentDark

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SettingsPrefs.get(context) }
    
    var language by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
    var theme by remember { mutableStateOf(prefs.getString("theme", "System Default") ?: "System Default") }
    
    val workManager = WorkManager.getInstance(context)
    val workInfos by workManager.getWorkInfosByTagFlow("model_download").collectAsStateWithLifecycle(emptyList())
    var currentModelId by remember { mutableStateOf(prefs.getString("whisper_model", "tiny") ?: "tiny") }
    
    val activeDownload = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val isDownloading = activeDownload != null
    val downloadProgress = activeDownload?.progress?.getInt(ModelDownloaderWorker.KEY_PROGRESS, 0) ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.padding(start = 4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(24.dp))

        Text("Transcription", style = MaterialTheme.typography.titleMedium, color = InkLight, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ParchmentDark)) {
            Column(Modifier.padding(16.dp)) {
                SettingRow("Language", language, listOf("English", "Multilingual (Hinglish)")) { 
                    language = it; prefs.edit().putString("language", it).apply() 
                }
                Spacer(Modifier.height(16.dp))
                val options = WhisperModel.values().map { it.label }
                val selectedLabel = WhisperModel.values().firstOrNull { it.id == currentModelId }?.label ?: WhisperModel.TINY.label
                
                SettingRow(
                    label = if (isDownloading) "Downloading Model ($downloadProgress%)" else "AI Model", 
                    selected = selectedLabel, 
                    options = options
                ) { label ->
                    val model = WhisperModel.values().first { it.label == label }
                    if (model.id == currentModelId) return@SettingRow

                    // No model is bundled anymore — every model, including Base, is only
                    // usable once its file has actually been downloaded.
                    val file = File(context.filesDir, "models/${model.filename}")
                    if (file.exists()) {
                        currentModelId = model.id
                        prefs.edit().putString("whisper_model", model.id).apply()
                    } else if (!isDownloading) {
                        val req = OneTimeWorkRequestBuilder<ModelDownloaderWorker>()
                            .addTag("model_download")
                            .setInputData(workDataOf(ModelDownloaderWorker.KEY_MODEL_ID to model.id))
                            .build()
                        workManager.enqueue(req)
                        // Speculatively select it now; until the download finishes,
                        // WhisperTranscriber.getModelFile() throws ModelNotDownloadedException,
                        // which TranscribeWorker catches and fails the capture gracefully
                        // (retryable once the download completes).
                        currentModelId = model.id
                        prefs.edit().putString("whisper_model", model.id).apply()
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = InkLight, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ParchmentDark)) {
            Column(Modifier.padding(16.dp)) {
                SettingRow("Theme", theme, listOf("System Default", "Light Notebook", "Dark Ink")) { 
                    theme = it; prefs.edit().putString("theme", it).apply() 
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text("notes-ai v0.1.0", style = MaterialTheme.typography.labelSmall, color = InkLight, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingRow(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).width(180.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
                }
            }
        }
    }
}
