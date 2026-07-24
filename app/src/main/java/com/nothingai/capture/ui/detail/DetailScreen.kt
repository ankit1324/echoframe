package com.nothingai.capture.ui.detail

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import com.nothingai.capture.data.CaptureStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothingai.capture.ui.theme.Coral
import com.nothingai.capture.ui.theme.CoralLight
import com.nothingai.capture.ui.theme.InkLight
import com.nothingai.capture.ui.theme.MustardLight
import com.nothingai.capture.ui.theme.ParchmentDark
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
    var isPlaying by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("The recording, screenshot, and transcript will be removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
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
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Capture not found", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    val date = remember(c.timestamp) { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(c.timestamp)) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Column(Modifier.padding(start = 4.dp)) {
                Text(c.title ?: "Untitled moment", style = MaterialTheme.typography.headlineMedium)
                Text(date, style = MaterialTheme.typography.bodySmall, color = InkLight)
            }
        }
        Spacer(Modifier.height(18.dp))

        val shot = vm.screenshotFile(id)
        if (c.hasScreenshot && shot.exists()) {
            val bitmap = remember(id) { BitmapFactory.decodeFile(shot.absolutePath) }
            if (bitmap != null) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = ParchmentDark), elevation = CardDefaults.cardElevation(2.dp)) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Captured screenshot", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(7.dp))
                }
            }
        } else {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MustardLight)) {
                Text("No screenshot saved for this moment.", modifier = Modifier.fillMaxWidth().padding(28.dp), style = MaterialTheme.typography.bodyMedium, color = InkLight)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("What you said", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (c.status == CaptureStatus.DONE && !c.transcript.isNullOrBlank()) {
                IconButton(onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Note transcript", c.transcript))
                }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy text", tint = InkLight, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${c.title ?: "Voice note"}\n\n${c.transcript}")
                    }, "Share note"))
                }) { Icon(Icons.Outlined.Share, contentDescription = "Share text", tint = InkLight, modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = ColorNote)) {
            Text(c.transcript ?: "(${c.status.name.lowercase()})", modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyLarge)
        }

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
            colors = ButtonDefaults.buttonColors(containerColor = Coral),
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
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Delete")
            }
        }
    }
}

private val ColorNote = androidx.compose.ui.graphics.Color(0xFFFFFCF5)
