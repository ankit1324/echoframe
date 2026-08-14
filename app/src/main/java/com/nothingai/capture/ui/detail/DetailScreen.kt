package com.nothingai.capture.ui.detail

import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import com.nothingai.capture.data.CaptureStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.drawable.toBitmap
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureAction
import com.nothingai.capture.data.extractCaptureActions
import com.nothingai.capture.data.markdownFilename
import com.nothingai.capture.stt.CropRegion
import com.nothingai.capture.stt.mergeImageNote
import com.nothingai.capture.stt.splitImageNote
import com.nothingai.capture.util.AppInfo
import com.nothingai.capture.util.decodeForDisplay
import com.nothingai.capture.ui.theme.semantic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, vm: DetailViewModel = viewModel()) {
    val capture by remember(id) { vm.observe(id) }.collectAsStateWithLifecycle()
    var player by remember(id) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(id) {
        onDispose { player?.release(); player = null }
    }

    val c = capture
    val context = LocalContext.current
    val semantic = MaterialTheme.semantic
    var isPlaying by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }
    val parsedNote = remember(c?.transcript) { splitImageNote(c?.transcript.orEmpty()) }
    var titleDraft by remember(id, c?.title) { mutableStateOf(c?.title.orEmpty()) }
    var bodyDraft by remember(id, parsedNote.text) { mutableStateOf(parsedNote.text) }
    var saveStatus by remember(id) { mutableStateOf("Saved") }
    val latestTitle = rememberUpdatedState(titleDraft)
    val latestBody = rememberUpdatedState(bodyDraft)
    val latestSavedTitle = rememberUpdatedState(c?.title.orEmpty())
    val latestSavedBody = rememberUpdatedState(parsedNote.text)
    val latestLabels = rememberUpdatedState(parsedNote.labels)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("The recording, screenshot, and analyzed note will be removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    if (showCrop) {
        CropDialog(
            onDismiss = { showCrop = false },
            onAnalyze = { region -> showCrop = false; vm.analyzeCrop(id, region) },
        )
    }

    LaunchedEffect(player) {
        while (isActive) {
            isPlaying = player?.isPlaying == true
            delay(100)
        }
    }

    if (c == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(20.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Capture not found", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    val exportMarkdown = rememberUpdatedState(vm.markdown(c, titleDraft, mergeImageNote(bodyDraft, parsedNote.labels)))
    val actions = remember(bodyDraft, parsedNote.labels) {
        extractCaptureActions(mergeImageNote(bodyDraft, parsedNote.labels))
    }
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportMarkdown.value) }
        }
    }

    val isDirty = { latestTitle.value != latestSavedTitle.value || latestBody.value != latestSavedBody.value }
    val flushEdits = { vm.updateNote(id, latestTitle.value, latestBody.value, latestLabels.value) }

    DisposableEffect(id) {
        onDispose { if (isDirty()) flushEdits() }
    }

    // onDispose does not run when the app is merely backgrounded, so a Home press inside the 400ms
    // save debounce would lose the edit if the process were then reclaimed. updateNote launches
    // ATOMIC + NonCancellable, so the write survives the scope going away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (isDirty()) flushEdits()
                // Playback would otherwise keep going with the screen gone and no way to stop it.
                runCatching { player?.takeIf { it.isPlaying }?.pause() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(id, titleDraft, bodyDraft, c.title, c.transcript) {
        if (titleDraft != c.title.orEmpty() || bodyDraft != parsedNote.text) {
            saveStatus = "Saving…"
            delay(400)
            vm.saveNote(id, titleDraft, bodyDraft, parsedNote.labels)
        }
        saveStatus = "Saved"
    }

    val date = remember(c.timestamp) { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(c.timestamp)) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.padding(start = 4.dp)) {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    placeholder = { Text("Untitled moment") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium,
                )
                Text("$date · $saveStatus", style = MaterialTheme.typography.bodySmall, color = semantic.mutedText)
            }
        }
        Spacer(Modifier.height(18.dp))

        val shot = vm.screenshotFile(id)
        if (c.hasScreenshot && shot.exists()) {
            // Decoded off the main thread and subsampled: a full-size screenshot is ~18MB and
            // decoding it during composition janks the screen (or OOMs on mid-range hardware).
            val bitmap by produceState<Bitmap?>(initialValue = null, id) {
                value = withContext(Dispatchers.IO) {
                    runCatching { decodeForDisplay(shot) }.getOrNull()
                }
            }
            val loadedShot = bitmap
            if (loadedShot != null) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column {
                        Image(loadedShot.asImageBitmap(), contentDescription = "Captured screenshot", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(7.dp))
                        TextButton(onClick = { showCrop = true }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Outlined.Crop, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Crop and analyze")
                        }
                    }
                }
            }
        } else {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = semantic.warningSoft)) {
                Text("No screenshot saved for this moment.", modifier = Modifier.fillMaxWidth().padding(28.dp), style = MaterialTheme.typography.bodyMedium, color = semantic.onSoft)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Note", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (c.status == CaptureStatus.DONE && bodyDraft.isNotBlank()) {
                val currentNote = mergeImageNote(bodyDraft, parsedNote.labels)
                IconButton(onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Note text", currentNote))
                }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy text", tint = semantic.mutedText, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, (titleDraft.ifBlank { "Capture note" }) + "\n\n" + currentNote)
                    }, "Share note"))
                }) { Icon(Icons.Outlined.Share, contentDescription = "Share text", tint = semantic.mutedText, modifier = Modifier.size(20.dp)) }
                TextButton(onClick = { markdownLauncher.launch(markdownFilename(titleDraft)) }) { Text("Export .md") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = semantic.noteSurface)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = bodyDraft,
                    onValueChange = { bodyDraft = it },
                    placeholder = { Text("(${c.status.name.lowercase()})") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                if (parsedNote.labels.isNotEmpty()) {
                    Text("Image labels: ${parsedNote.labels.joinToString()}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = semantic.mutedText)
                }
            }
        }

        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Useful actions", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                actions.forEach { action ->
                    FilterChip(
                        selected = false,
                        onClick = { launchCaptureAction(context, action) },
                        label = { Text(actionLabel(action)) },
                    )
                }
            }
        }

        SourceCard(capture = c, onSaveUrl = { vm.updateSourceUrl(id, it) })

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (isPlaying) {
                    player?.release()
                    player = null
                } else {
                    val audio = vm.audioFile(id)
                    if (audio.exists()) {
                        player?.release()
                        val next = MediaPlayer()
                        player = try {
                            next.setDataSource(audio.absolutePath)
                            next.setOnCompletionListener { completed -> completed.release(); if (player === completed) player = null }
                            next.prepare(); next.start(); next
                        } catch (_: Exception) { next.release(); null }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(if (isPlaying) "Stop playback" else "Play recording")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.retry(id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Retry")
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Delete")
            }
        }
    }
}

private fun actionLabel(action: CaptureAction): String = when (action) {
    is CaptureAction.Amount -> "Copy ${action.value}"
    is CaptureAction.DateTime -> "Add to calendar"
    is CaptureAction.Phone -> "Dial ${action.value}"
    is CaptureAction.Link -> "Open link"
    is CaptureAction.Address -> "Open map"
}

@Composable
private fun CropDialog(onDismiss: () -> Unit, onAnalyze: (CropRegion) -> Unit) {
    var left by remember { mutableStateOf(0f) }
    var top by remember { mutableStateOf(0f) }
    var right by remember { mutableStateOf(1f) }
    var bottom by remember { mutableStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select screenshot region") },
        text = {
            Column {
                CropSlider("Left", left) { left = it.coerceAtMost(right - .05f) }
                CropSlider("Right", right) { right = it.coerceAtLeast(left + .05f) }
                CropSlider("Top", top) { top = it.coerceAtMost(bottom - .05f) }
                CropSlider("Bottom", bottom) { bottom = it.coerceAtLeast(top + .05f) }
                Text("Percentages are measured from the screenshot edges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.semantic.mutedText)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnalyze(CropRegion(left, top, right, bottom)) }) { Text("Analyze region") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Text("$label: ${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
    Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
}

@Composable
private fun SourceCard(capture: Capture, onSaveUrl: (String?) -> Unit) {
    if (capture.sourcePackage == null && capture.sourceUrl == null) return
    val context = LocalContext.current
    var url by remember(capture.id) { mutableStateOf(capture.sourceUrl ?: "") }

    Spacer(Modifier.height(18.dp))
    Text("Source", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.semantic.noteSurface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val icon = remember(capture.sourcePackage) { AppInfo.icon(context, capture.sourcePackage) }
                if (icon != null) {
                    Image(icon.toBitmap(48, 48).asImageBitmap(), contentDescription = null, modifier = Modifier.size(28.dp))
                }
                Text(AppInfo.label(context, capture.sourcePackage), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Source URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onSaveUrl(url) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Save") }
                if (!capture.sourceUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(capture.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }, shape = RoundedCornerShape(14.dp)) { Text("Open") }
                }
            }
        }
    }
}
