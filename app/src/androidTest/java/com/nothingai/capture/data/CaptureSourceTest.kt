package com.nothingai.capture.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class CaptureSourceTest {
    private lateinit var db: CaptureDatabase
    private lateinit var dao: CaptureDao
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ctx, CaptureDatabase::class.java).build()
        dao = db.captureDao()
    }
    @After fun teardown() = db.close()

    @Test fun roundTripsSourceFieldsAndUpdatesUrl() = runTest {
        dao.upsert(Capture("a", 1, false, 0, null, CaptureStatus.DONE,
            sourcePackage = "com.instagram.android", sourceUrl = null))
        assertThat(dao.get("a")!!.sourcePackage).isEqualTo("com.instagram.android")
        dao.updateSourceUrl("a", "https://instagram.com/reel/xyz")
        assertThat(dao.get("a")!!.sourceUrl).isEqualTo("https://instagram.com/reel/xyz")
    }

    @Test fun migration2to3AddsSourceColumnsAndPreservesRows() {
        val name = "mig23.db"
        ctx.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(d: SupportSQLiteDatabase) {
                        d.execSQL("CREATE TABLE captures (id TEXT NOT NULL PRIMARY KEY, " +
                            "timestamp INTEGER NOT NULL, hasScreenshot INTEGER NOT NULL, " +
                            "durationMs INTEGER NOT NULL, transcript TEXT, status TEXT NOT NULL, " +
                            "title TEXT, tags TEXT NOT NULL DEFAULT '', isFavorite INTEGER NOT NULL DEFAULT 0)")
                        d.execSQL("INSERT INTO captures (id,timestamp,hasScreenshot,durationMs,transcript,status,tags,isFavorite) " +
                            "VALUES ('a',1,0,0,NULL,'DONE','',0)")
                    }
                    override fun onUpgrade(d: SupportSQLiteDatabase, o: Int, n: Int) {}
                }
            ).build()
        )
        val raw = helper.writableDatabase
        CaptureDatabase.MIGRATION_2_3.migrate(raw)
        raw.execSQL("UPDATE captures SET sourcePackage='com.x', sourceUrl='https://x' WHERE id='a'")
        raw.query("SELECT sourcePackage, sourceUrl FROM captures WHERE id='a'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("com.x")
            assertThat(c.getString(1)).isEqualTo("https://x")
        }
        CaptureDatabase.MIGRATION_3_4.migrate(raw)
        raw.query("SELECT category FROM captures WHERE id='a'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo(CaptureCategory.OTHER.name)
        }
        raw.close()
    }
}
