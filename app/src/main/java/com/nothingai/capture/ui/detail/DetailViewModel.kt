package com.nothingai.capture.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.stt.TranscribeWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

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

    fun delete(id: String) = viewModelScope.launch {
        storage.deleteCapture(id)
        dao.delete(id)
    }

    fun retry(id: String) = viewModelScope.launch {
        dao.updateStatus(id, CaptureStatus.PENDING)
        WorkManager.getInstance(getApplication()).enqueue(
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(TranscribeWorker.KEY_ID to id))
                .build()
        )
    }
}
