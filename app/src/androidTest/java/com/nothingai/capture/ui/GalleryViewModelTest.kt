package com.nothingai.capture.ui

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.ui.gallery.GalleryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GalleryViewModelTest {
    @Test fun searchFiltersItems() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dao = CaptureDatabase.get(app).captureDao()
        dao.upsert(Capture("a", 1, true, 1, "buy milk", CaptureStatus.DONE))
        dao.upsert(Capture("b", 2, true, 1, "call mom", CaptureStatus.DONE))
        val vm = GalleryViewModel(app)
        vm.setQuery("milk")
        val items = vm.items.first { it.size == 1 }
        assertThat(items.single().id).isEqualTo("a")
    }
}
