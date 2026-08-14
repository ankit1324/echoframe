package com.nothingai.capture.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(capture: Capture)

    @Query("SELECT * FROM captures ORDER BY isFavorite DESC, timestamp DESC")
    fun observeAll(): Flow<List<Capture>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: String): Capture?

    @Query("UPDATE captures SET transcript = :transcript, status = :status WHERE id = :id")
    suspend fun updateTranscript(id: String, transcript: String, status: CaptureStatus)

    @Query("UPDATE captures SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE captures SET title = :title, transcript = :transcript WHERE id = :id")
    suspend fun updateNote(id: String, title: String, transcript: String)

    @Query("UPDATE captures SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: String, tags: String)

    @Query("UPDATE captures SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: String, category: String)

    @Query("UPDATE captures SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)

    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: CaptureStatus)

    /**
     * Applies the outcome of a finished recording without disturbing fields the user may have
     * edited while it was still in flight (title, tags, favourite, category) — a full-row REPLACE
     * here would silently reset them to defaults.
     *
     * Source fields use COALESCE because `onHandleAssist` can land after `onShow`: a late value
     * fills in, but an absent one never erases what was already recorded. Returns rows updated.
     */
    @Query(
        "UPDATE captures SET hasScreenshot = :hasScreenshot, durationMs = :durationMs, status = :status, " +
            "sourcePackage = COALESCE(:sourcePackage, sourcePackage), " +
            "sourceUrl = COALESCE(:sourceUrl, sourceUrl) WHERE id = :id"
    )
    suspend fun finalizeRecording(
        id: String,
        hasScreenshot: Boolean,
        durationMs: Long,
        status: CaptureStatus,
        sourcePackage: String?,
        sourceUrl: String?,
    ): Int

    /** Captures in a non-terminal state, used by [CaptureRecovery] to repair interrupted work. */
    @Query("SELECT * FROM captures WHERE status IN ('RECORDING', 'PENDING', 'TRANSCRIBING')")
    suspend fun unfinished(): List<Capture>

    @Query("UPDATE captures SET sourceUrl = :url WHERE id = :id")
    suspend fun updateSourceUrl(id: String, url: String?)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM captures WHERE title LIKE '%' || :q || '%' OR transcript LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' ORDER BY isFavorite DESC, timestamp DESC")
    fun search(q: String): Flow<List<Capture>>
}
