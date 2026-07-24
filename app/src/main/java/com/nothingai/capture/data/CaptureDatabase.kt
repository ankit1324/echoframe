package com.nothingai.capture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class StatusConverter {
    @TypeConverter fun toStatus(v: String) = CaptureStatus.valueOf(v)
    @TypeConverter fun fromStatus(s: CaptureStatus) = s.name
}

@Database(entities = [Capture::class], version = 2, exportSchema = false)
@TypeConverters(StatusConverter::class)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE captures ADD COLUMN title TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE captures ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE captures ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: CaptureDatabase? = null

        fun get(context: Context): CaptureDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CaptureDatabase::class.java,
                    "captures.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
