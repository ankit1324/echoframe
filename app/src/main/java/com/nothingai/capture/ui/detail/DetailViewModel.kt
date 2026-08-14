package com.nothingai.capture.ui.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.data.captureToMarkdown
import com.nothingai.capture.data.categoryFor
import com.nothingai.capture.stt.TranscribeWorker
import com.nothingai.capture.stt.CropRegion
import com.nothingai.capture.stt.ImageAnalyzer
import com.nothingai.capture.stt.formatImageNote
import com.nothingai.capture.stt.titleFromImageAnalysis
import com.nothingai.capture.stt.mergeImageNote
import com.nothingai.capture.stt.splitImageNote
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CaptureDatabase.get(app).captureDao()
    private val storage = CaptureStorage(app)

    /** Newest-first observeAll filtered to a single id, so it reflects live status/transcript updates. */
    fun observe(id: String): StateFlow<Capture?> =
        dao.observeAll()
            .map { list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun screenshotFile(id: String): File = storage.screenshotFile(id)
    fun audioFile(id: String): File = storage.audioFile(id)

    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO) {
        storage.deleteCapture(id)
        dao.delete(id)
    }

    fun updateSourceUrl(id: String, url: String?) = viewModelScope.launch {
        dao.updateSourceUrl(id, url?.trim()?.ifBlank { null })
    }

    suspend fun saveNote(id: String, title: String, text: String, labels: List<String>) = withContext(Dispatchers.IO) {
        val note = mergeImageNote(text, labels)
        storage.saveTranscript(id, note)
        dao.updateNote(id, title.trim().ifBlank { "Untitled moment" }, note)
    }

    fun updateNote(id: String, title: String, text: String, labels: List<String>): Job = viewModelScope.launch(
        Dispatchers.IO,
        start = CoroutineStart.ATOMIC,
    ) {
        withContext(NonCancellable) { saveNote(id, title, text, labels) }
    }

    fun analyzeCrop(id: String, cropRegion: CropRegion): Job = viewModelScope.launch(Dispatchers.IO) {
        dao.updateStatus(id, CaptureStatus.TRANSCRIBING)
        try {
            val analysis = ImageAnalyzer().analyze(storage.screenshotFile(id), cropRegion)
            val note = splitImageNote(formatImageNote(analysis.text, analysis.labels))
            saveNote(id, titleFromImageAnalysis(analysis.text, analysis.labels), note.text, note.labels)
            dao.updateCategory(id, categoryFor(analysis.text, analysis.labels.joinToString { it.text }).name)
            dao.updateStatus(id, CaptureStatus.DONE)
        } catch (exception: Exception) {
            Log.e("DetailViewModel", "Crop analysis failed for $id", exception)
            dao.updateStatus(id, CaptureStatus.FAILED)
        }
    }

    fun markdown(capture: Capture, title: String, note: String): String = captureToMarkdown(
        title = title,
        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(capture.timestamp)),
        note = note,
        sourceUrl = capture.sourceUrl,
    )

    fun retry(id: String) = viewModelScope.launch {
        dao.updateStatus(id, CaptureStatus.PENDING)
        // REPLACE: the user asked for a fresh attempt, so supersede any stale queued one.
        TranscribeWorker.enqueue(getApplication(), id, ExistingWorkPolicy.REPLACE)
    }
}
