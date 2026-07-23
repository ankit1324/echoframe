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

    @Query("SELECT * FROM captures ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Capture>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: String): Capture?

    @Query("UPDATE captures SET transcript = :transcript, status = :status WHERE id = :id")
    suspend fun updateTranscript(id: String, transcript: String, status: CaptureStatus)

    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: CaptureStatus)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM captures WHERE transcript LIKE '%' || :q || '%' ORDER BY timestamp DESC")
    fun search(q: String): Flow<List<Capture>>
}
