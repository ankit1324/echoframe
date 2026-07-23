# Nothing AI — Assistant-Triggered Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that, as the system digital assistant, captures a screenshot + voice recording on power-button-hold, transcribes voice on-device with whisper.cpp, and browses captures in an offline gallery.

**Architecture:** App registers as `RoleManager.ROLE_ASSISTANT` via a `VoiceInteractionService`. The OS power-hold gesture launches the session, which receives the current-screen screenshot from the assist API, records audio to a WAV, saves both under one `captureId`, and enqueues a WorkManager job that runs whisper.cpp over the WAV. Compose UI provides a setup wizard and a capture gallery backed by Room.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, NDK/CMake, whisper.cpp (JNI), AndroidX.

## Global Constraints

- Platform: **Android native, Kotlin only** — no Flutter/React Native.
- `minSdk = 31` (Android 12), `targetSdk = 35`, `compileSdk = 35`.
- Fully offline: **no network permission, no cloud, no accounts, no sync.**
- STT: **whisper.cpp**, `ggml-base` multilingual, q5_1 quantized (~74 MB), for English+Hindi (Hinglish).
- Storage: **app-internal only** (`filesDir/captures/<captureId>/`). Never write to shared MediaStore.
- Recording ends on **explicit STOP tap** (no silence auto-stop).
- One `captureId` = `String` timestamp `yyyyMMdd-HHmmss-SSS` ties screenshot + audio + transcript.
- Package: `com.nothingai.capture`.
- Frequent commits: one per completed task minimum.

---

## File Structure

```
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    res/xml/voice_interaction_service.xml
    java/com/nothingai/capture/
      NothingApp.kt                 # Application, DI wiring
      data/
        Capture.kt                  # Room @Entity + CaptureStatus enum
        CaptureDao.kt               # Room DAO
        CaptureDatabase.kt          # RoomDatabase
        CaptureStorage.kt           # filesystem writer/reader for a captureId
        CaptureId.kt                # captureId generation/formatting (pure)
      stt/
        WhisperTranscriber.kt       # Kotlin wrapper over JNI
        TranscribeWorker.kt         # WorkManager CoroutineWorker
      assistant/
        CaptureInteractionService.kt        # VoiceInteractionService
        CaptureSessionService.kt            # VoiceInteractionSessionService
        CaptureSession.kt                   # VoiceInteractionSession (capture logic + UI)
        AudioRecorder.kt                    # WAV recorder (pure-ish, unit-testable format logic)
      ui/
        MainActivity.kt
        gallery/GalleryScreen.kt + GalleryViewModel.kt
        detail/DetailScreen.kt + DetailViewModel.kt
        wizard/SetupWizardScreen.kt + SetupState.kt
        theme/ (Compose theme)
    cpp/
      CMakeLists.txt
      whisper_jni.cpp              # thin JNI shim (vendored from whisper.cpp android example)
      whisper/                     # vendored whisper.cpp + ggml sources
  src/test/java/...                # JVM unit tests
  src/androidTest/java/...         # instrumented tests
```

---

### Task 1: Project scaffold — buildable empty app

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/nothingai/capture/ui/MainActivity.kt`
- Create: `app/src/main/java/com/nothingai/capture/NothingApp.kt`

**Interfaces:**
- Produces: `NothingApp : Application`; `MainActivity : ComponentActivity` (Compose host, shows placeholder text).

- [ ] **Step 1: Root Gradle config**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "NothingAI"
include(":app")
```

`build.gradle.kts` (root):
```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 2: App module Gradle**

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.nothingai.capture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nothingai.capture"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true }
    composeOptions { }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
}
```

- [ ] **Step 3: Minimal CMake stub** (real sources added Task 4)

`app/src/main/cpp/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(nothingai)
add_library(whisper_jni SHARED whisper_jni.cpp)
find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
```

`app/src/main/cpp/whisper_jni.cpp` (stub, replaced in Task 4):
```cpp
#include <jni.h>
```

- [ ] **Step 4: Manifest + Application + MainActivity**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".NothingApp"
        android:label="Nothing AI"
        android:allowBackup="false"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`NothingApp.kt`:
```kotlin
package com.nothingai.capture

import android.app.Application

class NothingApp : Application()
```

`MainActivity.kt`:
```kotlin
package com.nothingai.capture.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { Text("Nothing AI") } } }
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. APK produced.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/
git commit -m "chore: scaffold Android app module, builds empty Compose activity"
```

---

### Task 2: CaptureId (pure) — TDD

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/data/CaptureId.kt`
- Test: `app/src/test/java/com/nothingai/capture/data/CaptureIdTest.kt`

**Interfaces:**
- Produces: `object CaptureId { fun from(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String }` → format `yyyyMMdd-HHmmss-SSS`.

- [ ] **Step 1: Failing test**

```kotlin
package com.nothingai.capture.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset

class CaptureIdTest {
    @Test fun formatsEpochMillisAsSortableId() {
        // 2026-07-23T10:05:09.123Z
        val millis = 1_784_973_909_123L
        val id = CaptureId.from(millis, ZoneOffset.UTC)
        assertThat(id).isEqualTo("20260723-100509-123")
    }

    @Test fun idsAreLexicographicallyOrderedByTime() {
        val a = CaptureId.from(1_000L, ZoneOffset.UTC)
        val b = CaptureId.from(2_000L, ZoneOffset.UTC)
        assertThat(a < b).isTrue()
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*CaptureIdTest*"`
Expected: FAIL — `CaptureId` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.nothingai.capture.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CaptureId {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    fun from(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        FORMAT.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CaptureIdTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/data/CaptureId.kt app/src/test/java/com/nothingai/capture/data/CaptureIdTest.kt
git commit -m "feat: add sortable captureId formatter"
```

---

### Task 3: Room entity + DAO + database — TDD (instrumented, in-memory)

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/data/Capture.kt`
- Create: `app/src/main/java/com/nothingai/capture/data/CaptureDao.kt`
- Create: `app/src/main/java/com/nothingai/capture/data/CaptureDatabase.kt`
- Test: `app/src/androidTest/java/com/nothingai/capture/data/CaptureDaoTest.kt`

**Interfaces:**
- Produces:
  - `enum class CaptureStatus { RECORDING, PENDING, TRANSCRIBING, DONE, FAILED }`
  - `@Entity data class Capture(id: String [PK], timestamp: Long, hasScreenshot: Boolean, durationMs: Long, transcript: String?, status: CaptureStatus)`
  - `CaptureDao`: `suspend upsert(c: Capture)`, `fun observeAll(): Flow<List<Capture>>`, `suspend get(id: String): Capture?`, `suspend updateTranscript(id, transcript, status)`, `suspend updateStatus(id, status)`, `suspend delete(id: String)`, `fun search(q: String): Flow<List<Capture>>`
  - `CaptureDatabase.get(context): CaptureDatabase`, `.captureDao()`

- [ ] **Step 1: Failing test**

```kotlin
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
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*CaptureDaoTest*"`
Expected: FAIL — types unresolved. (Requires a booted emulator/device.)

- [ ] **Step 3: Implement entity + DAO + db**

`Capture.kt`:
```kotlin
package com.nothingai.capture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CaptureStatus { RECORDING, PENDING, TRANSCRIBING, DONE, FAILED }

@Entity(tableName = "captures")
data class Capture(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val hasScreenshot: Boolean,
    val durationMs: Long,
    val transcript: String?,
    val status: CaptureStatus
)
```

`CaptureDao.kt`:
```kotlin
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
```

`CaptureDatabase.kt`:
```kotlin
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
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*CaptureDaoTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/data/ app/src/androidTest/java/com/nothingai/capture/data/CaptureDaoTest.kt
git commit -m "feat: add Room Capture entity, DAO, database with tests"
```

---

### Task 4: WAV writer (AudioRecorder format logic) — TDD

Only the WAV header/byte logic is unit-tested (pure). Live `AudioRecord` capture is exercised on device in Task 7.

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/assistant/AudioRecorder.kt`
- Test: `app/src/test/java/com/nothingai/capture/assistant/WavHeaderTest.kt`

**Interfaces:**
- Produces:
  - `object Wav { fun header(pcmByteCount: Int, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray }` (44-byte RIFF header)
  - `class AudioRecorder(sampleRate=16000)` with `fun start(outFile: File)`, `fun stop(): Long /*durationMs*/` — declared now, exercised on device Task 7.

- [ ] **Step 1: Failing test**

```kotlin
package com.nothingai.capture.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {
    @Test fun headerIs44BytesWithRiffAndDataChunks() {
        val h = Wav.header(pcmByteCount = 32000, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        assertThat(h.size).isEqualTo(44)
        assertThat(String(h.copyOfRange(0, 4))).isEqualTo("RIFF")
        assertThat(String(h.copyOfRange(8, 12))).isEqualTo("WAVE")
        assertThat(String(h.copyOfRange(36, 40))).isEqualTo("data")
    }

    @Test fun headerEncodesSizesAndRateLittleEndian() {
        val h = Wav.header(pcmByteCount = 32000, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertThat(bb.getInt(4)).isEqualTo(36 + 32000)   // ChunkSize
        assertThat(bb.getInt(24)).isEqualTo(16000)       // SampleRate
        assertThat(bb.getInt(28)).isEqualTo(16000 * 1 * 16 / 8) // ByteRate
        assertThat(bb.getInt(40)).isEqualTo(32000)       // Subchunk2Size
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*WavHeaderTest*"`
Expected: FAIL — `Wav` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.nothingai.capture.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Wav {
    fun header(pcmByteCount: Int, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcmByteCount)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1)
        bb.putShort(channels.toShort()); bb.putInt(sampleRate); bb.putInt(byteRate)
        bb.putShort((channels * bitsPerSample / 8).toShort()); bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray()); bb.putInt(pcmByteCount)
        return bb.array()
    }
}

/** 16kHz mono PCM16 recorder -> WAV. Started/stopped from the assistant session. */
class AudioRecorder(private val sampleRate: Int = 16000) {
    @Volatile private var recording = false
    private var thread: Thread? = null
    private var pcmBytes = 0L
    private var startMs = 0L
    private var endMs = 0L

    @Suppress("MissingPermission")
    fun start(outFile: File) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
        )
        recording = true
        startMs = System.currentTimeMillis()
        pcmBytes = 0
        thread = Thread {
            val raf = RandomAccessFile(outFile, "rw")
            raf.setLength(0)
            raf.write(Wav.header(0, sampleRate)) // placeholder, patched on stop
            val buf = ByteArray(minBuf)
            record.startRecording()
            while (recording) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) { raf.write(buf, 0, n); pcmBytes += n }
            }
            record.stop(); record.release()
            // patch sizes
            raf.seek(0); raf.write(Wav.header(pcmBytes.toInt(), sampleRate)); raf.close()
        }.also { it.start() }
    }

    /** @return duration in ms */
    fun stop(): Long {
        recording = false
        thread?.join()
        endMs = System.currentTimeMillis()
        return endMs - startMs
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*WavHeaderTest*"`
Expected: PASS. (AudioRecorder class compiles; live capture verified Task 7.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/assistant/AudioRecorder.kt app/src/test/java/com/nothingai/capture/assistant/WavHeaderTest.kt
git commit -m "feat: add WAV header writer and AudioRecorder"
```

---

### Task 5: CaptureStorage — TDD

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/data/CaptureStorage.kt`
- Test: `app/src/androidTest/java/com/nothingai/capture/data/CaptureStorageTest.kt`

**Interfaces:**
- Consumes: `CaptureId`.
- Produces: `class CaptureStorage(context)`:
  - `fun dir(id: String): File` → `filesDir/captures/<id>/` (created)
  - `fun screenshotFile(id): File`, `audioFile(id): File`, `transcriptFile(id): File`
  - `fun saveScreenshot(id: String, bitmap: Bitmap)` (PNG)
  - `fun saveTranscript(id: String, text: String)`
  - `fun deleteCapture(id: String)`

- [ ] **Step 1: Failing test**

```kotlin
package com.nothingai.capture.data

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureStorageTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = CaptureStorage(ctx)

    @Test fun savesScreenshotAndTranscriptUnderCaptureDir() {
        val id = "20260101-000001-000"
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        storage.saveScreenshot(id, bmp)
        storage.saveTranscript(id, "hello")
        assertThat(storage.screenshotFile(id).exists()).isTrue()
        assertThat(storage.transcriptFile(id).readText()).isEqualTo("hello")
        assertThat(storage.dir(id).path).endsWith("captures/$id")
    }

    @Test fun deleteRemovesWholeDir() {
        val id = "20260101-000002-000"
        storage.saveTranscript(id, "x")
        storage.deleteCapture(id)
        assertThat(storage.dir(id).exists()).isFalse()
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*CaptureStorageTest*"`
Expected: FAIL — `CaptureStorage` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.nothingai.capture.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

class CaptureStorage(context: Context) {
    private val root = File(context.filesDir, "captures")

    fun dir(id: String): File = File(root, id).apply { mkdirs() }
    fun screenshotFile(id: String) = File(dir(id), "screenshot.png")
    fun audioFile(id: String) = File(dir(id), "audio.wav")
    fun transcriptFile(id: String) = File(dir(id), "transcript.txt")

    fun saveScreenshot(id: String, bitmap: Bitmap) {
        FileOutputStream(screenshotFile(id)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    fun saveTranscript(id: String, text: String) { transcriptFile(id).writeText(text) }
    fun deleteCapture(id: String) { dir(id).deleteRecursively() }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*CaptureStorageTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/data/CaptureStorage.kt app/src/androidTest/java/com/nothingai/capture/data/CaptureStorageTest.kt
git commit -m "feat: add CaptureStorage file layout for captures"
```

---

### Task 6: whisper.cpp JNI integration + WhisperTranscriber

**No unit TDD** — native transcription needs a device + model file. Test cycle = build the `.so` + on-device smoke test with a bundled sample WAV. Do NOT hand-write JNI/ggml — **vendor the upstream whisper.cpp Android bindings verbatim** (Apache/MIT licensed) and expose a thin Kotlin wrapper.

**Files:**
- Vendor into: `app/src/main/cpp/whisper/` (whisper.cpp + ggml sources)
- Replace: `app/src/main/cpp/whisper_jni.cpp` (from upstream `examples/whisper.android/lib/src/main/jni/whisper/jni.c`)
- Replace: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/java/com/nothingai/capture/stt/WhisperTranscriber.kt`
- Model asset: `app/src/main/assets/models/ggml-base-q5_1.bin`
- Smoke test: `app/src/androidTest/java/com/nothingai/capture/stt/WhisperSmokeTest.kt`
- Sample: `app/src/androidTest/assets/jfk_16k.wav` (bundled test clip)

**Interfaces:**
- Produces: `class WhisperTranscriber(context)`:
  - `fun ensureModel(): File` — copies asset model to `filesDir/models/` once, returns file
  - `fun transcribe(wav: File): String` — blocking; returns text
  - internal `external fun` JNI declarations matching the vendored shim.

- [ ] **Step 1: Vendor whisper.cpp**

Clone pinned upstream and copy sources (run from repo root):
```bash
git clone --depth 1 --branch v1.7.1 https://github.com/ggerganov/whisper.cpp /tmp/whispercpp
mkdir -p app/src/main/cpp/whisper
cp /tmp/whispercpp/whisper.cpp app/src/main/cpp/whisper/
cp /tmp/whispercpp/whisper.h app/src/main/cpp/whisper/
cp -r /tmp/whispercpp/ggml app/src/main/cpp/whisper/ggml
cp /tmp/whispercpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c app/src/main/cpp/whisper_jni.cpp
```
> The upstream `jni.c` exposes: `initContextFromAsset`, `initContext`, `fullTranscribe`, `getTextSegmentCount`, `getTextSegment`, `freeContext`, `benchMemcpy` under class path `com/whispercpp/whisper/WhisperLib$Companion`. Keep that JNI class path OR edit the JNINativeMethod registration to `com/nothingai/capture/stt/WhisperTranscriber`. Choose: edit to our class path.

- [ ] **Step 2: CMake for whisper + shim**

`app/src/main/cpp/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(nothingai)
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whisper)
add_library(whisper_jni SHARED
    whisper_jni.cpp
    ${WHISPER_DIR}/whisper.cpp
    ${WHISPER_DIR}/ggml/src/ggml.c
    ${WHISPER_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_DIR}/ggml/src/ggml-backend.c
    ${WHISPER_DIR}/ggml/src/ggml-quants.c
)
target_include_directories(whisper_jni PRIVATE ${WHISPER_DIR} ${WHISPER_DIR}/ggml/include ${WHISPER_DIR}/ggml/src)
target_compile_definitions(whisper_jni PRIVATE GGML_USE_CPU)
find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
```
> Note: exact ggml source file list matches the pinned v1.7.1 tree. If the upstream file layout differs at the pinned tag, mirror whatever `.c` files live under `ggml/src/` at that tag — verify with `ls app/src/main/cpp/whisper/ggml/src`.

- [ ] **Step 3: Kotlin wrapper**

`WhisperTranscriber.kt`:
```kotlin
package com.nothingai.capture.stt

import android.content.Context
import java.io.File

class WhisperTranscriber(private val context: Context) {
    companion object {
        init { System.loadLibrary("whisper_jni") }
    }

    // JNI — signatures must match whisper_jni.cpp registration.
    private external fun initContext(modelPath: String): Long
    private external fun fullTranscribe(ctx: Long, numThreads: Int, audioData: FloatArray)
    private external fun getTextSegmentCount(ctx: Long): Int
    private external fun getTextSegment(ctx: Long, index: Int): String
    private external fun freeContext(ctx: Long)

    fun ensureModel(): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val out = File(dir, "ggml-base-q5_1.bin")
        if (!out.exists()) {
            context.assets.open("models/ggml-base-q5_1.bin").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        return out
    }

    /** Blocking transcription. Reads 16kHz mono PCM16 WAV. */
    fun transcribe(wav: File): String {
        val ctx = initContext(ensureModel().absolutePath)
        check(ctx != 0L) { "whisper init failed" }
        try {
            val samples = readWavToFloat(wav)
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            fullTranscribe(ctx, threads, samples)
            val sb = StringBuilder()
            for (i in 0 until getTextSegmentCount(ctx)) sb.append(getTextSegment(ctx, i))
            return sb.toString().trim()
        } finally {
            freeContext(ctx)
        }
    }

    private fun readWavToFloat(wav: File): FloatArray {
        val bytes = wav.readBytes()
        val pcmStart = 44 // standard header from AudioRecorder
        val sampleCount = (bytes.size - pcmStart) / 2
        val out = FloatArray(sampleCount)
        var j = pcmStart
        for (i in 0 until sampleCount) {
            val lo = bytes[j].toInt() and 0xff
            val hi = bytes[j + 1].toInt()
            val s = (hi shl 8) or lo
            out[i] = s / 32768f
            j += 2
        }
        return out
    }
}
```
> Adjust `external fun` names/params to exactly match the vendored `whisper_jni.cpp` registration edited in Step 1. If upstream `fullTranscribe` signature differs (e.g. takes sample count), mirror it.

- [ ] **Step 4: Add model asset**

Download the quantized base model (run from repo root):
```bash
mkdir -p app/src/main/assets/models
curl -L -o app/src/main/assets/models/ggml-base-q5_1.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
```
> ~57 MB. Confirm file size > 40 MB after download. This inflates the APK — acceptable per spec (offline requirement).

- [ ] **Step 5: Smoke test (on device)**

`WhisperSmokeTest.kt`:
```kotlin
package com.nothingai.capture.stt

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class WhisperSmokeTest {
    @Test fun transcribesBundledClip() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wav = File(ctx.cacheDir, "jfk.wav")
        ctx.assets.open("jfk_16k.wav").use { i -> wav.outputStream().use { i.copyTo(it) } }
        val text = WhisperTranscriber(ctx).transcribe(wav).lowercase()
        assertThat(text).contains("country")
    }
}
```

- [ ] **Step 6: Build native + run smoke test**

Run: `./gradlew :app:externalNativeBuildDebug`
Expected: builds `libwhisper_jni.so` for arm64-v8a, x86_64.
Run: `./gradlew :app:connectedDebugAndroidTest --tests "*WhisperSmokeTest*"`
Expected: PASS (transcript contains "country"). If native build fails, reconcile the ggml source list (Step 2 note) before proceeding.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/cpp/ app/src/main/java/com/nothingai/capture/stt/WhisperTranscriber.kt app/src/main/assets/models/ app/src/androidTest/
git commit -m "feat: integrate whisper.cpp JNI and WhisperTranscriber"
```
> `.gitattributes`: mark `*.bin` as binary; consider Git LFS for the model if repo size matters.

---

### Task 7: Assistant session — capture pipeline (screenshot + record + save)

**No unit TDD** — `VoiceInteractionService` runs only when the OS invokes it as the assistant. Test cycle = build + install + on-device manual checklist.

**Files:**
- Create: `app/src/main/res/xml/voice_interaction_service.xml`
- Create: `app/src/main/java/com/nothingai/capture/assistant/CaptureInteractionService.kt`
- Create: `app/src/main/java/com/nothingai/capture/assistant/CaptureSessionService.kt`
- Create: `app/src/main/java/com/nothingai/capture/assistant/CaptureSession.kt`
- Modify: `app/src/main/AndroidManifest.xml` (permissions + services)

**Interfaces:**
- Consumes: `CaptureId`, `CaptureStorage`, `AudioRecorder`, `Capture`/`CaptureDao`, `TranscribeWorker` (Task 8 — enqueued by class name; if Task 8 not yet done, stub the enqueue call and wire in Task 8).
- Produces: working session that on invocation saves screenshot+audio and writes a `Capture` row with status `PENDING`.

- [ ] **Step 1: Manifest — permissions + services**

Add to `<manifest>`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```
Add inside `<application>`:
```xml
<service
    android:name=".assistant.CaptureInteractionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true">
    <meta-data
        android:name="android.voice_interaction"
        android:resource="@xml/voice_interaction_service" />
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService" />
    </intent-filter>
</service>
<service
    android:name=".assistant.CaptureSessionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true" />
```

- [ ] **Step 2: voice_interaction_service.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<voice-interaction-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:sessionService="com.nothingai.capture.assistant.CaptureSessionService"
    android:recognitionService="com.nothingai.capture.assistant.CaptureSessionService"
    android:supportsAssist="true"
    android:supportsLaunchVoiceAssistFromKeyguard="false" />
```

- [ ] **Step 3: Interaction + session services**

`CaptureInteractionService.kt`:
```kotlin
package com.nothingai.capture.assistant

import android.service.voice.VoiceInteractionService

class CaptureInteractionService : VoiceInteractionService()
```

`CaptureSessionService.kt`:
```kotlin
package com.nothingai.capture.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class CaptureSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = CaptureSession(this)
}
```

- [ ] **Step 4: CaptureSession — the pipeline**

`CaptureSession.kt`:
```kotlin
package com.nothingai.capture.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureId
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.stt.TranscribeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CaptureSession(context: Context) : VoiceInteractionSession(context) {
    private val storage = CaptureStorage(context)
    private val dao = CaptureDatabase.get(context).captureDao()
    private val recorder = AudioRecorder()
    private val io = CoroutineScope(Dispatchers.IO)

    private lateinit var captureId: String
    private var hasScreenshot = false
    private var timerView: TextView? = null
    private var startMs = 0L

    override fun onCreateContentView() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.argb(220, 0, 0, 0))
        setPadding(48, 96, 48, 96)
        gravity = Gravity.CENTER
        timerView = TextView(context).apply {
            text = "● Recording 0s"; setTextColor(Color.WHITE); textSize = 22f
        }
        addView(timerView)
        addView(Button(context).apply {
            text = "STOP"
            setOnClickListener { finishCapture() }
        })
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        captureId = CaptureId.from(System.currentTimeMillis())
        if (screenshot != null) {
            io.launch { storage.saveScreenshot(captureId, screenshot) }
            hasScreenshot = true
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (!::captureId.isInitialized) captureId = CaptureId.from(System.currentTimeMillis())
        startMs = System.currentTimeMillis()
        io.launch {
            dao.upsert(Capture(captureId, startMs, hasScreenshot, 0, null, CaptureStatus.RECORDING))
            recorder.start(storage.audioFile(captureId))
        }
    }

    private fun finishCapture() {
        io.launch {
            val duration = recorder.stop()
            dao.upsert(Capture(captureId, startMs, hasScreenshot, duration, null, CaptureStatus.PENDING))
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<TranscribeWorker>()
                    .setInputData(workDataOf(TranscribeWorker.KEY_ID to captureId))
                    .build()
            )
        }
        hide()
    }
}
```
> `onHandleScreenshot` is only called when the user has enabled "use screenshot" in assistant settings and the foreground app is not `FLAG_SECURE`. When null, `hasScreenshot` stays false and audio-only capture proceeds — this is the spec's FLAG_SECURE fallback.

- [ ] **Step 5: Build + install**

Run: `./gradlew :app:installDebug`
Expected: installs on connected device.

- [ ] **Step 6: On-device manual verification checklist**

Perform on a real device (assistant role granted in Task 9 — if not yet, run Task 9 first, then return):
1. Set app as default assistant (Task 9 wizard or Settings → Apps → Default apps → Digital assistant).
2. Long-press power → the black overlay with "● Recording" + STOP appears.
3. Speak 3–4 seconds, tap STOP.
4. `adb shell run-as com.nothingai.capture ls files/captures/<id>/` shows `screenshot.png` (if on a non-secure screen) and `audio.wav`.
5. Open a banking app / Netflix, long-press power → overlay still records, `screenshot.png` absent, capture row `hasScreenshot=false`.

Record results in the task checklist. All 5 must pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/xml/voice_interaction_service.xml app/src/main/java/com/nothingai/capture/assistant/ app/src/main/AndroidManifest.xml
git commit -m "feat: add assistant session capture pipeline (screenshot + audio)"
```

---

### Task 8: TranscribeWorker — background transcription

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/stt/TranscribeWorker.kt`
- Test: `app/src/androidTest/java/com/nothingai/capture/stt/TranscribeWorkerTest.kt`

**Interfaces:**
- Consumes: `WhisperTranscriber`, `CaptureStorage`, `CaptureDao`, `CaptureStatus`.
- Produces: `class TranscribeWorker(...) : CoroutineWorker` with `companion object { const val KEY_ID = "captureId" }`. On success: writes transcript file + `dao.updateTranscript(id, text, DONE)`. On failure: `dao.updateStatus(id, FAILED)`, `Result.failure()`.

- [ ] **Step 1: Failing test (uses TestListenableWorkerBuilder + a real short WAV)**

```kotlin
package com.nothingai.capture.stt

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TranscribeWorkerTest {
    @Test fun transcribesAndMarksDone() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "20260101-000009-000"
        val storage = CaptureStorage(ctx)
        // seed a real 16k mono wav from test asset
        ctx.assets.open("jfk_16k.wav").use { i -> storage.audioFile(id).outputStream().use { i.copyTo(it) } }
        val dao = CaptureDatabase.get(ctx).captureDao()
        dao.upsert(Capture(id, 1, true, 1000, null, CaptureStatus.PENDING))

        val worker = TestListenableWorkerBuilder<TranscribeWorker>(ctx)
            .setInputData(workDataOf(TranscribeWorker.KEY_ID to id)).build()
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val c = dao.get(id)!!
        assertThat(c.status).isEqualTo(CaptureStatus.DONE)
        assertThat(c.transcript!!.lowercase()).contains("country")
        assertThat(storage.transcriptFile(id).exists()).isTrue()
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*TranscribeWorkerTest*"`
Expected: FAIL — `TranscribeWorker` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.nothingai.capture.stt

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage

class TranscribeWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object { const val KEY_ID = "captureId" }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val storage = CaptureStorage(applicationContext)
        val dao = CaptureDatabase.get(applicationContext).captureDao()
        val wav = storage.audioFile(id)
        if (!wav.exists()) { dao.updateStatus(id, CaptureStatus.FAILED); return Result.failure() }
        return try {
            dao.updateStatus(id, CaptureStatus.TRANSCRIBING)
            val text = WhisperTranscriber(applicationContext).transcribe(wav)
            storage.saveTranscript(id, text)
            dao.updateTranscript(id, text, CaptureStatus.DONE)
            Result.success()
        } catch (e: Exception) {
            dao.updateStatus(id, CaptureStatus.FAILED)
            Result.failure()
        }
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*TranscribeWorkerTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/stt/TranscribeWorker.kt app/src/androidTest/java/com/nothingai/capture/stt/TranscribeWorkerTest.kt
git commit -m "feat: add TranscribeWorker running whisper in background"
```

---

### Task 9: Setup wizard + assistant role request

**No unit TDD** — role grants and Settings deep-links are OS UI. Test cycle = build + on-device checklist.

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/ui/wizard/SetupState.kt`
- Create: `app/src/main/java/com/nothingai/capture/ui/wizard/SetupWizardScreen.kt`
- Modify: `app/src/main/java/com/nothingai/capture/ui/MainActivity.kt` (nav host + wizard route)

**Interfaces:**
- Produces:
  - `data class SetupState(isAssistant: Boolean, hasMic: Boolean)`
  - `object SetupChecks { fun read(context): SetupState }` — checks `RoleManager.isRoleHeld(ROLE_ASSISTANT)` and `RECORD_AUDIO` grant.
  - `@Composable fun SetupWizardScreen(...)` with buttons launching role request, mic permission, and the power-gesture Settings screen.

- [ ] **Step 1: SetupChecks + state**

`SetupState.kt`:
```kotlin
package com.nothingai.capture.ui.wizard

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class SetupState(val isAssistant: Boolean, val hasMic: Boolean) {
    val ready get() = isAssistant && hasMic
}

object SetupChecks {
    fun read(context: Context): SetupState {
        val rm = context.getSystemService(RoleManager::class.java)
        val isAssistant = rm?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        val hasMic = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return SetupState(isAssistant, hasMic)
    }
}
```

- [ ] **Step 2: Wizard screen**

`SetupWizardScreen.kt`:
```kotlin
package com.nothingai.capture.ui.wizard

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SetupWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SetupChecks.read(context)) }
    fun refresh() { state = SetupChecks.read(context) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)

        StepRow("1. Grant microphone", state.hasMic) {
            micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        StepRow("2. Set as digital assistant", state.isAssistant) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            } else {
                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }
        }
        StepRow("3. Power-hold → assistant", true) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS)) // deep-link varies by OEM
        }
        Text(
            "On step 3, open System → Gestures → Press & hold power button, " +
            "and choose \"Digital assistant\". Also enable \"Use screenshot\" in " +
            "assistant settings so screen captures work.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(enabled = state.ready, onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.ready) "Done" else "Complete steps 1–2 to continue")
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (done) "✓ $label" else label)
        if (!done) Button(onClick = onClick) { Text("Open") }
    }
}
```

- [ ] **Step 3: Wire MainActivity nav (wizard ↔ gallery)**

Replace `MainActivity.kt`:
```kotlin
package com.nothingai.capture.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nothingai.capture.ui.gallery.GalleryScreen
import com.nothingai.capture.ui.wizard.SetupChecks
import com.nothingai.capture.ui.wizard.SetupWizardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val nav = rememberNavController()
                    val start = if (SetupChecks.read(this).ready) "gallery" else "wizard"
                    NavHost(nav, startDestination = start) {
                        composable("wizard") {
                            SetupWizardScreen(onDone = {
                                nav.navigate("gallery") { popUpTo("wizard") { inclusive = true } }
                            })
                        }
                        composable("gallery") { GalleryScreen(onOpen = { /* Task 10 */ }) }
                    }
                }
            }
        }
    }
}
```
> `GalleryScreen` is defined in Task 10. If executing strictly in order, temporarily stub `GalleryScreen(onOpen)` as an empty composable to keep this task building, then flesh out in Task 10.

- [ ] **Step 4: Build + install**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, installs.

- [ ] **Step 5: On-device checklist**

1. Fresh launch → wizard shows, steps 1–2 unchecked.
2. Tap step 1 → mic dialog → grant → row shows ✓.
3. Tap step 2 → assistant role dialog → set → row shows ✓.
4. "Done" enabled → tap → gallery route.
5. Reopen app → starts on gallery (ready state persisted by OS grants).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/ui/wizard/ app/src/main/java/com/nothingai/capture/ui/MainActivity.kt
git commit -m "feat: add setup wizard and assistant role request"
```

---

### Task 10: Gallery + detail UI

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/ui/gallery/GalleryViewModel.kt`
- Create: `app/src/main/java/com/nothingai/capture/ui/gallery/GalleryScreen.kt`
- Create: `app/src/main/java/com/nothingai/capture/ui/detail/DetailViewModel.kt`
- Create: `app/src/main/java/com/nothingai/capture/ui/detail/DetailScreen.kt`
- Modify: `MainActivity.kt` (detail route + real GalleryScreen wiring)
- Test: `app/src/androidTest/java/com/nothingai/capture/ui/GalleryViewModelTest.kt`

**Interfaces:**
- Consumes: `CaptureDao`, `CaptureStorage`, `Capture`, `CaptureStatus`, `TranscribeWorker` (retry).
- Produces:
  - `GalleryViewModel(app)`: `val items: StateFlow<List<Capture>>`, `fun setQuery(q: String)`.
  - `DetailViewModel(app)`: `fun load(id): StateFlow<Capture?>`, `fun delete(id)`, `fun retry(id)`, `fun screenshotFile(id)`, `audioFile(id)`.
  - `GalleryScreen(onOpen: (String) -> Unit)`, `DetailScreen(id, onBack)`.

- [ ] **Step 1: Failing test — VM exposes newest-first + search**

```kotlin
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
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*GalleryViewModelTest*"`
Expected: FAIL — `GalleryViewModel` unresolved.

- [ ] **Step 3: Implement GalleryViewModel**

```kotlin
package com.nothingai.capture.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CaptureDatabase.get(app).captureDao()
    private val query = MutableStateFlow("")
    fun setQuery(q: String) { query.value = q }

    val items: StateFlow<List<Capture>> = query
        .flatMapLatest { q -> if (q.isBlank()) dao.observeAll() else dao.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*GalleryViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Implement GalleryScreen**

```kotlin
package com.nothingai.capture.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothingai.capture.data.CaptureStatus

@Composable
fun GalleryScreen(onOpen: (String) -> Unit, vm: GalleryViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it; vm.setQuery(it) },
            label = { Text("Search transcripts") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.id, style = MaterialTheme.typography.labelMedium)
                        val snippet = when (c.status) {
                            CaptureStatus.DONE -> c.transcript?.take(80) ?: "(empty)"
                            CaptureStatus.FAILED -> "transcription failed"
                            else -> "transcribing…"
                        }
                        Text(snippet)
                        Text("${c.durationMs / 1000}s" + if (c.hasScreenshot) " • 📷" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Implement DetailViewModel + DetailScreen**

`DetailViewModel.kt`:
```kotlin
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CaptureDatabase.get(app).captureDao()
    private val storage = CaptureStorage(app)
    fun observe(id: String): StateFlow<Capture?> =
        dao.observeAll().map { list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun screenshotFile(id: String): File = storage.screenshotFile(id)
    fun audioFile(id: String): File = storage.audioFile(id)
    fun delete(id: String) = viewModelScope.launch { storage.deleteCapture(id); dao.delete(id) }
    fun retry(id: String) = viewModelScope.launch {
        dao.updateStatus(id, CaptureStatus.PENDING)
        WorkManager.getInstance(getApplication()).enqueue(
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(TranscribeWorker.KEY_ID to id)).build()
        )
    }
}
```

`DetailScreen.kt`:
```kotlin
package com.nothingai.capture.ui.detail

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, vm: DetailViewModel = viewModel()) {
    val capture by vm.observe(id).collectAsStateWithLifecycle()
    val c = capture ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        val shot = vm.screenshotFile(id)
        if (c.hasScreenshot && shot.exists()) {
            val bmp = remember(id) { BitmapFactory.decodeFile(shot.absolutePath) }
            bmp?.let { Image(it.asImageBitmap(), contentDescription = "screenshot",
                modifier = Modifier.fillMaxWidth()) }
        } else {
            Text("No screenshot (secure screen).")
        }
        Spacer(Modifier.height(12.dp))
        Text("Transcript", style = MaterialTheme.typography.titleMedium)
        Text(c.transcript ?: "(${c.status.name.lowercase()})")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                MediaPlayer().apply {
                    setDataSource(vm.audioFile(id).absolutePath); prepare(); start()
                }
            }) { Text("Play") }
            Button(onClick = { vm.retry(id) }) { Text("Retry") }
            Button(onClick = { vm.delete(id); onBack() }) { Text("Delete") }
        }
    }
}
```

- [ ] **Step 7: Wire detail route in MainActivity**

Add inside `NavHost`:
```kotlin
composable("gallery") {
    GalleryScreen(onOpen = { id -> nav.navigate("detail/$id") })
}
composable("detail/{id}") { back ->
    DetailScreen(id = back.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
}
```

- [ ] **Step 8: Build + install + on-device verification**

Run: `./gradlew :app:installDebug`
1. Trigger a capture (long-press power, speak, STOP).
2. Open app → gallery row appears, "transcribing…" → then transcript snippet.
3. Tap row → detail: screenshot renders, transcript shows, Play plays audio.
4. Retry re-runs transcription. Delete removes row + files.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/ui/
git commit -m "feat: add capture gallery and detail screens"
```

---

## Self-Review

**Spec coverage:**
- §2 assistant role → Task 7 (service/manifest/xml) + Task 9 (role request). ✓
- §5 capture pipeline (screenshot + record + save + enqueue) → Task 7. ✓
- §5 FLAG_SECURE fallback → Task 7 Step 4 (`onHandleScreenshot` null path) + Step 6 checklist item 5. ✓
- §6 transcription (WorkManager + status) → Task 8. ✓
- §7 storage layout + Room model → Tasks 3, 5. ✓
- §8 gallery + detail + wizard → Tasks 9, 10. ✓
- §9 permissions → Task 7 (manifest) + Task 9 (runtime). ✓
- §4 whisper.cpp base q5 → Task 6. ✓
- Global constraint "no network" → no INTERNET permission added anywhere. ✓ (model + whisper source pulled at build time on dev machine, not at runtime.)

**Placeholder scan:** No TBD/TODO in code steps. Two forward-references are explicit and handled: Task 7→Task 8 (`TranscribeWorker` enqueue — noted), Task 9→Task 10 (`GalleryScreen` stub — noted). Task 6 flags that the vendored JNI signatures must be reconciled against upstream v1.7.1 (unavoidable — depends on the real upstream file, which must not be fabricated).

**Type consistency:** `captureId: String`, `CaptureStatus` values, `CaptureDao` method names (`upsert`, `observeAll`, `get`, `updateTranscript`, `updateStatus`, `delete`, `search`), `TranscribeWorker.KEY_ID`, `CaptureStorage` file accessors — all used consistently across Tasks 3, 5, 7, 8, 10. ✓

**Known risk carried from spec §12:** whisper native build is the highest-risk task (upstream file layout at the pinned tag). Task 6 Steps 2/3/6 tell the implementer to reconcile against the actual vendored tree rather than trust this plan's file list verbatim.
