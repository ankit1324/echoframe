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

    @Query("UPDATE captures SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: String, tags: String)

    @Query("UPDATE captures SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)

    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: CaptureStatus)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM captures WHERE title LIKE '%' || :q || '%' OR transcript LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' ORDER BY isFavorite DESC, timestamp DESC")
    fun search(q: String): Flow<List<Capture>>
}
