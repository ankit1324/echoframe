package com.nothingai.capture.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothingai.capture.data.CaptureStatus

@Composable
fun GalleryScreen(onOpen: (String) -> Unit, vm: GalleryViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it; vm.setQuery(it) },
            label = { Text("Search transcripts") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.id, style = MaterialTheme.typography.labelMedium)
                        val snippet = when (c.status) {
                            CaptureStatus.DONE -> c.transcript?.take(80) ?: "(empty)"
                            CaptureStatus.FAILED -> "failed"
                            else -> "transcribing…"
                        }
                        Text(snippet)
                        Text(
                            "${c.durationMs / 1000}s" + if (c.hasScreenshot) " • 📷" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
