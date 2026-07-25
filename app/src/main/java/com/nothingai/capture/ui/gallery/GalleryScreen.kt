package com.nothingai.capture.ui.gallery

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.ui.theme.Coral
import com.nothingai.capture.ui.theme.CoralLight
import com.nothingai.capture.ui.theme.Ink
import com.nothingai.capture.ui.theme.InkFaint
import com.nothingai.capture.ui.theme.InkLight
import com.nothingai.capture.ui.theme.MustardLight
import com.nothingai.capture.ui.theme.ParchmentDark
import com.nothingai.capture.ui.theme.SageLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class GalleryFilter { ALL, FAVORITES, TAGGED }
private val quickTags = listOf("Idea", "Reminder", "Meeting", "Personal")

@Composable
fun GalleryScreen(onOpen: (String) -> Unit, onSettings: () -> Unit, vm: GalleryViewModel = viewModel()) {
    val captures by vm.items.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    val context = LocalContext.current
    val visibleCaptures = when (filter) {
        GalleryFilter.ALL -> captures
        GalleryFilter.FAVORITES -> captures.filter { it.isFavorite }
        GalleryFilter.TAGGED -> captures.filter { it.tags.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Little moments", style = MaterialTheme.typography.displaySmall)
                Text(
                    if (visibleCaptures.isEmpty()) "Your voice scrapbook" else "${visibleCaptures.size} saved ${if (visibleCaptures.size == 1) "moment" else "moments"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkLight,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = InkLight)
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(CoralLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Coral, modifier = Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        NotebookDoodle(modifier = Modifier.fillMaxWidth().height(18.dp))
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.setQuery(it) },
            placeholder = { Text("Search titles, notes, tags…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = InkLight) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = .5f),
                focusedContainerColor = Color.White.copy(alpha = .8f),
                unfocusedBorderColor = ParchmentDark,
                focusedBorderColor = Coral,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == GalleryFilter.ALL, onClick = { filter = GalleryFilter.ALL }, label = { Text("All") })
            FilterChip(selected = filter == GalleryFilter.FAVORITES, onClick = { filter = GalleryFilter.FAVORITES }, label = { Text("Favorites") })
            FilterChip(selected = filter == GalleryFilter.TAGGED, onClick = { filter = GalleryFilter.TAGGED }, label = { Text("Tagged") })
        }
        Spacer(Modifier.height(14.dp))

        if (visibleCaptures.isEmpty()) {
            EmptyGallery()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(visibleCaptures, key = { it.id }) { capture ->
                    CaptureCard(
                        capture = capture,
                        onClick = { onOpen(capture.id) },
                        onFavorite = { vm.setFavorite(capture.id, !capture.isFavorite) },
                        onDelete = { vm.delete(capture.id) },
                        onShare = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${capture.title ?: "Voice note"}\n\n${capture.transcript.orEmpty()}")
                            }, "Share note"))
                        },
                        onTag = { vm.setTags(capture.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    capture: Capture,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onTag: (String) -> Unit,
) {
    val cardColor = when (capture.id.lastOrNull()?.code?.rem(3)) {
        0 -> Color.White
        1 -> MustardLight.copy(alpha = .65f)
        else -> SageLight.copy(alpha = .6f)
    }
    val snippet = when (capture.status) {
        CaptureStatus.DONE -> capture.transcript?.take(130) ?: "A quiet moment, waiting for words."
        CaptureStatus.FAILED -> "This note needs another listen."
        CaptureStatus.RECORDING -> "Listening…"
        else -> "Finding the words…"
    }
    val date = remember(capture.timestamp) { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(capture.timestamp)) }
    var tagMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("The recording, screenshot, and transcript will be removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date, style = MaterialTheme.typography.labelMedium, color = InkLight)
                    if (capture.hasScreenshot) Icon(Icons.Outlined.CameraAlt, contentDescription = "Screenshot saved", tint = InkLight, modifier = Modifier.padding(start = 7.dp).size(15.dp))
                }
                Spacer(Modifier.height(7.dp))
                Text(capture.title ?: if (capture.status == CaptureStatus.DONE) "Untitled moment" else "New voice note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                val transition = rememberInfiniteTransition(label = "cardPulse")
                val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "textAlpha")
                val isProcessing = capture.status == CaptureStatus.PENDING || capture.status == CaptureStatus.TRANSCRIBING
                Text(snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3, modifier = Modifier.alpha(if (isProcessing) alpha else 1f))
                if (capture.tags.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = CoralLight) {
                        Text(capture.tags, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Ink)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(capture)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onFavorite) { Icon(if (capture.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = if (capture.isFavorite) "Unfavorite" else "Favorite", tint = if (capture.isFavorite) Coral else InkLight) }
                    Box {
                        IconButton(onClick = { tagMenuOpen = true }) { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = "Add tag", tint = InkLight) }
                        DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                            quickTags.forEach { tag -> DropdownMenuItem(text = { Text(tag) }, onClick = { onTag(tag); tagMenuOpen = false }) }
                        }
                    }
                    IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share note", tint = InkLight) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete note", tint = Coral) }
                }
            }
            IconButton(onClick = onClick) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Open capture", tint = InkLight) }
        }
    }
}

@Composable
private fun StatusPill(capture: Capture) {
    val (label, background, foreground) = when (capture.status) {
        CaptureStatus.DONE -> Triple("ready · ${capture.durationMs / 1000}s", SageLight, Ink)
        CaptureStatus.FAILED -> Triple("needs retry", CoralLight, Ink)
        else -> Triple("transcribing…", MustardLight, Ink)
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = foreground)
            if (capture.status == CaptureStatus.PENDING || capture.status == CaptureStatus.TRANSCRIBING) {
                ProcessingDots()
            }
        }
    }
}

@Composable
private fun ProcessingDots() {
    val transition = rememberInfiniteTransition(label = "processingDots")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Text(" · · ·", modifier = Modifier.alpha(dotAlpha), style = MaterialTheme.typography.labelSmall, color = Ink)
}

@Composable
private fun EmptyGallery() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(CoralLight),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", style = MaterialTheme.typography.displayMedium, color = Coral)
        }
        Spacer(Modifier.height(18.dp))
        Text("Nothing here yet", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(7.dp))
        Text("Hold the power button, say what’s on your mind,\nand your first little moment will appear here.", style = MaterialTheme.typography.bodyMedium, color = InkLight)
        Spacer(Modifier.height(18.dp))
        Text("your thoughts, kept close", style = MaterialTheme.typography.labelSmall, color = InkFaint)
    }
}

@Composable
private fun NotebookDoodle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(4.dp.toPx(), size.height * .55f)
            cubicTo(size.width * .17f, 0f, size.width * .25f, size.height, size.width * .38f, size.height * .47f)
            cubicTo(size.width * .54f, 0f, size.width * .62f, size.height, size.width * .73f, size.height * .48f)
            cubicTo(size.width * .84f, 0f, size.width * .92f, size.height * .8f, size.width - 4.dp.toPx(), size.height * .45f)
        }
        drawPath(path, color = Coral.copy(alpha = .6f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(InkFaint, radius = 2.dp.toPx(), center = Offset(size.width * .08f, size.height * .52f))
    }
}
