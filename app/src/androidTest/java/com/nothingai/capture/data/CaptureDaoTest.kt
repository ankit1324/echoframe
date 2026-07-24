package com.nothingai.capture.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class CaptureDaoTest {
    private lateinit var db: CaptureDatabase
    private lateinit var dao: CaptureDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CaptureDatabase::class.java).build()
        dao = db.captureDao()
    }
    @After fun teardown() = db.close()

    @Test fun upsertAndObserveNewestFirst() = runTest {
        dao.upsert(Capture("20260101-000001-000", 1, false, 0, null, CaptureStatus.PENDING))
        dao.upsert(Capture("20260101-000002-000", 2, true, 500, null, CaptureStatus.PENDING))
        val all = dao.observeAll().first()
        assertThat(all.map { it.id })
            .containsExactly("20260101-000002-000", "20260101-000001-000").inOrder()
    }

    @Test fun updateTranscriptSetsTextAndStatus() = runTest {
        dao.upsert(Capture("a", 1, true, 100, null, CaptureStatus.TRANSCRIBING))
        dao.updateTranscript("a", "hello world", CaptureStatus.DONE)
        val c = dao.get("a")!!
        assertThat(c.transcript).isEqualTo("hello world")
        assertThat(c.status).isEqualTo(CaptureStatus.DONE)
    }

    @Test fun searchMatchesTranscript() = runTest {
        dao.upsert(Capture("a", 1, true, 1, "buy milk", CaptureStatus.DONE))
        dao.upsert(Capture("b", 2, true, 1, "call mom", CaptureStatus.DONE))
        val hits = dao.search("milk").first()
        assertThat(hits.map { it.id }).containsExactly("a")
    }
}
