package com.nothingai.capture.ui.detail

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, vm: DetailViewModel = viewModel()) {
    val captureFlow = remember(id) { vm.observe(id) }
    val capture by captureFlow.collectAsStateWithLifecycle()

    // Track the currently-playing MediaPlayer (if any) so it can be released when this
    // composable leaves composition or when a new playback is started.
    var player by remember(id) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(id) {
        onDispose {
            player?.release()
            player = null
        }
    }

    val c = capture ?: run {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.height(12.dp))
            Text("Capture not found.")
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(12.dp))

        val shot = vm.screenshotFile(id)
        if (c.hasScreenshot && shot.exists()) {
            val bmp = remember(id) { BitmapFactory.decodeFile(shot.absolutePath) }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "screenshot",
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("No screenshot (secure screen).")
            }
        } else {
            Text("No screenshot (secure screen).")
        }

        Spacer(Modifier.height(12.dp))
        Text("Transcript", style = MaterialTheme.typography.titleMedium)
        Text(c.transcript ?: "(${c.status.name.lowercase()})")

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val audio = vm.audioFile(id)
                if (audio.exists()) {
                    player?.release()
                    player = runCatching {
                        MediaPlayer().apply {
                            setDataSource(audio.absolutePath)
                            setOnCompletionListener { mp -> mp.release(); player = null }
                            prepare()
                            start()
                        }
                    }.getOrNull()
                }
            }) { Text("Play") }
            Button(onClick = { vm.retry(id) }) { Text("Retry") }
            Button(onClick = { vm.delete(id); onBack() }) { Text("Delete") }
        }
    }
}
