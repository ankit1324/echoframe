package com.nothingai.capture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class StatusConverter {
    @TypeConverter fun toStatus(v: String) = CaptureStatus.valueOf(v)
    @TypeConverter fun fromStatus(s: CaptureStatus) = s.name
}

@Database(entities = [Capture::class], version = 1, exportSchema = false)
@TypeConverters(StatusConverter::class)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    companion object {
        @Volatile private var INSTANCE: CaptureDatabase? = null
        fun get(context: Context): CaptureDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, CaptureDatabase::class.java, "captures.db"
                ).build().also { INSTANCE = it }
            }
    }
}
