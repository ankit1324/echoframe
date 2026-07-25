# Source-Context Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record the source app (package → name/icon) and source URL of each capture, with a manual URL field, surfaced in the gallery and detail screens.

**Architecture:** Extend the existing assistant `CaptureSession` with `onHandleAssist` to read the foreground app + `webUri` from the assist API (same power-hold invocation as the screenshot). Persist `sourcePackage`/`sourceUrl` on the `Capture` Room entity (migration v2→v3). Resolve app name/icon at display time via `PackageManager`. Add a Source row (with editable URL) to detail and a source chip to the gallery.

**Tech Stack:** Kotlin, Jetpack Compose, Room, `android.service.voice` assist API, `PackageManager`.

## Global Constraints

- Platform: Android native, Kotlin only. `minSdk 31`.
- No new permissions (the assistant role already grants assist access). No `INTERNET` use added by this feature (opening a URL uses `ACTION_VIEW`, handled by the browser).
- Package `com.nothingai.capture`.
- Room schema is currently **version 2** (columns: id, timestamp, hasScreenshot, durationMs, transcript, status, title, tags, isFavorite) with `exportSchema = false` and `MIGRATION_1_2` registered. This feature goes to **version 3**.
- New fields are nullable: `sourcePackage: String?`, `sourceUrl: String?`.
- App name/icon are NOT stored — resolved at display time from `sourcePackage`.
- Instrumented tests run on an emulator; filter with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` (the `--tests` flag is unsupported for `connectedDebugAndroidTest` on this AGP).

## ⚠️ Coordination gate

`CaptureSession.kt`, `DetailScreen.kt`, `GalleryScreen.kt` are being edited by another agent (model rework). **Do not start Tasks 3–5 until that work has landed and the working tree is clean.** Tasks 1–2 (schema, resolver) touch different files and can proceed first. At implementation time, re-read each target file — anchors below are by method, not line number.

## File Structure

```
data/Capture.kt          # +2 nullable fields
data/CaptureDao.kt       # +updateSourceUrl
data/CaptureDatabase.kt  # version 3 + MIGRATION_2_3
util/AppInfo.kt          # NEW: package -> label/icon resolver
assistant/CaptureSession.kt   # +onHandleAssist, thread source into the Capture row
ui/detail/DetailViewModel.kt  # +updateSourceUrl
ui/detail/DetailScreen.kt     # +Source row (icon/name/URL + editable field)
ui/gallery/GalleryScreen.kt   # +source chip on card
```

---

### Task 1: Schema — source columns, DAO, migration v2→v3

**Files:**
- Modify: `app/src/main/java/com/nothingai/capture/data/Capture.kt`
- Modify: `app/src/main/java/com/nothingai/capture/data/CaptureDao.kt`
- Modify: `app/src/main/java/com/nothingai/capture/data/CaptureDatabase.kt`
- Test: `app/src/androidTest/java/com/nothingai/capture/data/CaptureSourceTest.kt`

**Interfaces:**
- Produces: `Capture.sourcePackage: String?`, `Capture.sourceUrl: String?`; `CaptureDao.updateSourceUrl(id: String, url: String?)`; `CaptureDatabase.MIGRATION_2_3`.

- [ ] **Step 1: Write the failing instrumented test**

`CaptureSourceTest.kt`:
```kotlin
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
        raw.close()
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nothingai.capture.data.CaptureSourceTest`
Expected: FAIL — `sourcePackage`/`updateSourceUrl`/`MIGRATION_2_3` unresolved.

- [ ] **Step 3: Add entity fields**

In `Capture.kt`, append to the `data class Capture(...)` parameter list (after `isFavorite`):
```kotlin
    val sourcePackage: String? = null,
    val sourceUrl: String? = null,
```

- [ ] **Step 4: Add DAO method**

In `CaptureDao.kt`, add:
```kotlin
    @Query("UPDATE captures SET sourceUrl = :url WHERE id = :id")
    suspend fun updateSourceUrl(id: String, url: String?)
```

- [ ] **Step 5: Bump version + migration**

In `CaptureDatabase.kt`: change `version = 2` to `version = 3` in the `@Database` annotation. In the `companion object`, add beside `MIGRATION_1_2`:
```kotlin
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE captures ADD COLUMN sourcePackage TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE captures ADD COLUMN sourceUrl TEXT DEFAULT NULL")
            }
        }
```
And add it to the builder: change `.addMigrations(MIGRATION_1_2)` to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`. (Ensure `import androidx.sqlite.db.SupportSQLiteDatabase` is present — it already is for `MIGRATION_1_2`.)

- [ ] **Step 6: Run, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nothingai.capture.data.CaptureSourceTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/data/ app/src/androidTest/java/com/nothingai/capture/data/CaptureSourceTest.kt
git commit -m "feat(data): add source package/URL columns and v2->v3 migration"
```

---

### Task 2: AppInfo resolver

**Files:**
- Create: `app/src/main/java/com/nothingai/capture/util/AppInfo.kt`
- Test: `app/src/androidTest/java/com/nothingai/capture/util/AppInfoTest.kt`

**Interfaces:**
- Produces: `AppInfo.label(context, pkg: String?): String`, `AppInfo.icon(context, pkg: String?): android.graphics.drawable.Drawable?`.

- [ ] **Step 1: Write the failing instrumented test**

`AppInfoTest.kt`:
```kotlin
package com.nothingai.capture.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInfoTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun resolvesOwnPackageLabelAndIcon() {
        assertThat(AppInfo.label(ctx, ctx.packageName)).isNotEmpty()
        assertThat(AppInfo.icon(ctx, ctx.packageName)).isNotNull()
    }

    @Test fun fallsBackForUnknownOrNull() {
        assertThat(AppInfo.label(ctx, "com.does.not.exist")).isEqualTo("com.does.not.exist")
        assertThat(AppInfo.label(ctx, null)).isEqualTo("Unknown app")
        assertThat(AppInfo.icon(ctx, "com.does.not.exist")).isNull()
        assertThat(AppInfo.icon(ctx, null)).isNull()
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nothingai.capture.util.AppInfoTest`
Expected: FAIL — `AppInfo` unresolved.

- [ ] **Step 3: Implement**

`AppInfo.kt`:
```kotlin
package com.nothingai.capture.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Resolves a package name to its user-facing app label and launcher icon. */
object AppInfo {
    fun label(context: Context, pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Unknown app"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun icon(context: Context, pkg: String?): Drawable? {
        if (pkg.isNullOrBlank()) return null
        return try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nothingai.capture.util.AppInfoTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/util/AppInfo.kt app/src/androidTest/java/com/nothingai/capture/util/AppInfoTest.kt
git commit -m "feat(util): add AppInfo package label/icon resolver"
```

---

### Task 3: Capture source in the assistant session

**Gate:** requires the other agent's `CaptureSession.kt` edits to have landed. No unit test — verified by build + on-device checklist. Re-read the current `CaptureSession.kt` before editing.

**Files:**
- Modify: `app/src/main/java/com/nothingai/capture/assistant/CaptureSession.kt`

**Interfaces:**
- Consumes: `Capture(... sourcePackage, sourceUrl)` (Task 1).
- Produces: capture rows populated with `sourcePackage`/`sourceUrl`.

- [ ] **Step 1: Add source state fields**

Near the other per-capture fields in `CaptureSession` (e.g. beside `hasScreenshot`), add:
```kotlin
    @Volatile private var sourcePackage: String? = null
    @Volatile private var sourceUrl: String? = null
```

- [ ] **Step 2: Override onHandleAssist**

Add this method to `CaptureSession` (import `android.service.voice.VoiceInteractionSession.AssistState` or use the fully-qualified type as below):
```kotlin
    override fun onHandleAssist(state: android.service.voice.VoiceInteractionSession.AssistState) {
        try {
            sourcePackage = state.assistStructure?.activityComponent?.packageName
            sourceUrl = state.assistContent?.webUri?.toString()
        } catch (t: Throwable) {
            android.util.Log.e("CaptureSession", "onHandleAssist failed", t)
        }
        super.onHandleAssist(state)
    }
```
> `AssistState` is the API-30+ callback (fine for minSdk 31). `assistStructure`/`assistContent` are nullable; `webUri` is null unless the foreground app exposes a URL — that is the expected "manual field" path.

- [ ] **Step 3: Thread source into the Capture row**

Wherever `CaptureSession` constructs a `Capture(...)` (the RECORDING row in `onShow` and the PENDING row in `finalizeCapture`), capture the fields into locals on the main thread before the IO launch (same discipline as `captureId`/`hasScreenshot`) and pass them:
```kotlin
    val pkg = sourcePackage
    val url = sourceUrl
    // ... inside io.launch { ... dao.upsert(Capture(id, ..., sourcePackage = pkg, sourceUrl = url)) }
```
In the per-capture state reset at the end of `finalizeCapture` (where `captureId`/`hasScreenshot` are reset), also add:
```kotlin
    sourcePackage = null
    sourceUrl = null
```

- [ ] **Step 4: Build + install**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, installs.

- [ ] **Step 5: On-device checklist** (emulator-5554; ASSISTANT role held + RECORD_AUDIO granted)

1. Open Chrome to any page, invoke the assistant (`adb shell input keyevent 219`), tap STOP.
   Then: `adb shell run-as com.nothingai.capture sqlite3 databases/captures.db "SELECT sourcePackage, sourceUrl FROM captures ORDER BY timestamp DESC LIMIT 1;"`
   Expected: `com.android.chrome|https://…` (a real URL).
2. Open a social app with no exposed URL, capture again → row shows the app's package and an empty `sourceUrl`.
3. No crash of the assistant process in either case (`adb logcat` clean of FATAL).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/assistant/CaptureSession.kt
git commit -m "feat(assistant): capture source app + URL via onHandleAssist"
```

---

### Task 4: Detail — Source row with editable URL

**Gate:** requires the other agent's `DetailScreen.kt` edits to have landed. Build + on-device verified. Re-read current `DetailScreen.kt`/`DetailViewModel.kt` before editing.

**Files:**
- Modify: `app/src/main/java/com/nothingai/capture/ui/detail/DetailViewModel.kt`
- Modify: `app/src/main/java/com/nothingai/capture/ui/detail/DetailScreen.kt`

**Interfaces:**
- Consumes: `CaptureDao.updateSourceUrl` (Task 1), `AppInfo` (Task 2), `Capture.sourcePackage/sourceUrl`.
- Produces: `DetailViewModel.updateSourceUrl(id, url)`.

- [ ] **Step 1: Add VM method**

In `DetailViewModel`, add (matching the existing `dao`/`viewModelScope` usage in that file):
```kotlin
    fun updateSourceUrl(id: String, url: String?) = viewModelScope.launch {
        dao.updateSourceUrl(id, url?.trim()?.ifBlank { null })
    }
```

- [ ] **Step 2: Add the Source row to DetailScreen**

Add this composable and render it inside the detail content (below the transcript, using the loaded `capture`/`c` and `vm`):
```kotlin
@androidx.compose.runtime.Composable
private fun SourceRow(
    capture: com.nothingai.capture.data.Capture,
    onSaveUrl: (String?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (capture.sourcePackage == null && capture.sourceUrl == null) return
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        androidx.compose.material3.Text("Source", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = androidx.compose.ui.Modifier.padding(top = 4.dp)
        ) {
            val icon = com.nothingai.capture.util.AppInfo.icon(context, capture.sourcePackage)
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = androidx.core.graphics.drawable.toBitmap(icon, 48, 48).asImageBitmap(),
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.size(24.dp)
                )
            }
            androidx.compose.material3.Text(com.nothingai.capture.util.AppInfo.label(context, capture.sourcePackage))
        }
        var url by androidx.compose.runtime.remember(capture.id) {
            androidx.compose.runtime.mutableStateOf(capture.sourceUrl ?: "")
        }
        androidx.compose.material3.OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { androidx.compose.material3.Text("Source URL") },
            singleLine = true,
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Button(onClick = { onSaveUrl(url) }) {
                androidx.compose.material3.Text("Save")
            }
            if (capture.sourceUrl != null) {
                androidx.compose.material3.OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(capture.sourceUrl))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { androidx.compose.material3.Text("Open") }
            }
        }
    }
}
```
Then call it in the detail body: `SourceRow(capture = c, onSaveUrl = { vm.updateSourceUrl(c.id, it) })`.
> `androidx.core.graphics.drawable.toBitmap` comes from `androidx.core:core-ktx` (already on the classpath via AndroidX). Fixed 48×48 keeps the decode cheap.

- [ ] **Step 3: Build + install**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device checklist**

1. Open a capture that has a `sourcePackage` → Source row shows the app icon + name.
2. For a capture with a real URL → "Open" launches the browser to it.
3. Type a URL into the field, tap Save, reopen the capture → the URL persists (`updateSourceUrl` worked). Blank + Save → clears it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/ui/detail/
git commit -m "feat(detail): source row with app name/icon and editable URL"
```

---

### Task 5: Gallery — source chip on each card

**Gate:** requires the other agent's `GalleryScreen.kt` edits to have landed. Build + on-device verified. Re-read current `GalleryScreen.kt` before editing.

**Files:**
- Modify: `app/src/main/java/com/nothingai/capture/ui/gallery/GalleryScreen.kt`

**Interfaces:**
- Consumes: `AppInfo` (Task 2), `Capture.sourcePackage/sourceUrl`.

- [ ] **Step 1: Add a source chip to the card**

Inside the per-capture card composable (where duration / 📷 are shown), add, using the row's `capture`:
```kotlin
if (capture.sourcePackage != null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        com.nothingai.capture.util.AppInfo.icon(ctx, capture.sourcePackage)?.let { d ->
            androidx.compose.foundation.Image(
                bitmap = androidx.core.graphics.drawable.toBitmap(d, 36, 36).asImageBitmap(),
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(16.dp)
            )
        }
        androidx.compose.material3.Text(
            com.nothingai.capture.util.AppInfo.label(ctx, capture.sourcePackage),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )
        if (capture.sourceUrl != null) {
            androidx.compose.material3.Text("🔗", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}
```

- [ ] **Step 2: Build + install**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device checklist**

1. Gallery cards for captures with a source show the app icon + name; a 🔗 appears when a URL exists.
2. Captures without a source (e.g. launched from lock screen) show no chip — no crash, no blank icon.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/nothingai/capture/ui/gallery/GalleryScreen.kt
git commit -m "feat(gallery): show source app chip on capture cards"
```

---

## Self-Review

**Spec coverage:**
- §3 fields `sourcePackage`/`sourceUrl` → Task 1. ✓
- §4 `onHandleAssist` capture mechanism → Task 3. ✓
- §5 schema + migration v2→v3 + `updateSourceUrl` → Task 1. ✓
- §6 `AppInfo` resolver (label/icon + fallbacks) → Task 2. ✓
- §7 gallery chip → Task 5; detail source row + editable URL + open → Task 4. ✓
- §8 edge cases: null package (chip hidden — Task 5 Step 1 guard), null webUri (empty field — Task 4), uninstalled fallback (Task 2 test), FLAG_SECURE unaffected (Task 3 independent of screenshot). ✓
- §9 testing: AppInfo + updateSourceUrl + migration (Tasks 1–2 instrumented); device checklists (Tasks 3–5). ✓
- Global "no INTERNET added": only `ACTION_VIEW` (Task 4) — no network call in-app. ✓

**Placeholder scan:** No TBD/TODO. Anchors for Tasks 3–5 reference methods (not line numbers) intentionally, because those files are concurrently edited — the gate + "re-read before editing" instruction covers this.

**Type consistency:** `sourcePackage: String?`, `sourceUrl: String?`, `updateSourceUrl(id: String, url: String?)`, `MIGRATION_2_3`, `AppInfo.label`/`AppInfo.icon` are used consistently across Tasks 1–5. `toBitmap`/`asImageBitmap` used identically in Tasks 4 and 5.
