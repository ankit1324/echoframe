# Source-Context Capture — Echoframe

**Date:** 2026-07-25
**Status:** Approved design, pre-implementation
**Feature:** Record the source app + URL of each capture, alongside the existing screenshot/voice/transcript.

## 1. Summary

When Echoframe captures (power-button-hold → assistant session), also record **where** the capture came from: which foreground app (name + icon) and, when available, the source **URL** (web page, or a deep URL the app exposes). For apps that don't expose a URL (e.g. Instagram reels), provide an **editable field** to paste it manually. The timestamp is already captured.

This rides the **same assist invocation** as the screenshot — no new permission, no background service.

## 2. Feasibility (honest limits)

- ✅ **Foreground app** (package → real name + icon): always available via the assist `AssistStructure.activityComponent`.
- ✅ **Web URL in browsers** (Chrome, Brave, Firefox, Samsung Internet): reliable via `AssistContent.webUri`.
- ⚠️ **In-app deep URLs** (YouTube video, some apps): only when the app implements `onProvideAssistContent`. YouTube usually does; many don't.
- ❌ **Instagram/most social reel/post URLs**: those apps do **not** expose the URL to assistants, and Android has no API to read it. Not auto-obtainable → handled by the manual field.

App name + icon are resolved for **every** app via `PackageManager` — no curated "supported apps" list needed (supports all apps, not just mainstream ones).

## 3. Fields captured (per capture)

- `sourcePackage: String?` — the foreground app's package (e.g. `com.instagram.android`). Null if no foreground component (e.g. launched from keyguard).
- `sourceUrl: String?` — auto from `AssistContent.webUri` when present; otherwise null until the user pastes one.
- App **name + icon** are NOT stored — resolved at display time from `sourcePackage` via `PackageManager` (keeps icons fresh, no storage bloat).
- Timestamp: already exists on `Capture`.

Explicitly **out of scope** (declined during brainstorming): page/content title, source-type category tag.

## 4. Capture mechanism

In `CaptureSession`, add `onHandleAssist(request: AssistState)` alongside the existing `onHandleScreenshot`, tied to the same `captureId`:

- `sourcePackage` ← `AssistStructure.getActivityComponent()?.packageName` (from the request's structure).
- `sourceUrl` ← `AssistContent.getWebUri()?.toString()` (null-safe).
- Both callbacks may fire in either order for one invocation; store into the same capture row (the row is finalized on STOP / onHide as today). Capture these into locals on the main thread before the IO write (same discipline as the existing captureId/hasScreenshot handling).
- Wrap the extraction in try/catch — a malformed assist structure must never crash the assistant process.

## 5. Schema

`Capture` gains `sourcePackage: String?` and `sourceUrl: String?`. Room migration **v2 → v3** (current version is 2 — title/tags/isFavorite):

```sql
ALTER TABLE captures ADD COLUMN sourcePackage TEXT DEFAULT NULL
ALTER TABLE captures ADD COLUMN sourceUrl TEXT DEFAULT NULL
```

New DAO method: `updateSourceUrl(id: String, url: String?)`.

## 6. App-info resolver

New helper `AppInfo` (in `data/` or a small `util/`):
- `fun label(context, pkg: String?): String` — `PackageManager.getApplicationLabel`, fallback to the raw package (or "Unknown app") if uninstalled/null.
- `fun icon(context, pkg: String?): Drawable?` — `getApplicationIcon`, null on failure.
Pure-ish, unit-testable with a mocked `PackageManager`.

## 7. UI

- **Gallery card:** small real app icon + app-name chip (resolved from `sourcePackage`); a small link glyph when `sourceUrl != null`.
- **Detail:** a "Source" row — app icon + name; the URL shown as a tappable link (opens via `ACTION_VIEW`); an **editable text field** to paste/fix the URL, saving via `updateSourceUrl`. Empty package → row hidden or shows "Unknown source".

## 8. Edge cases

- No foreground component → `sourcePackage` null → source chip hidden.
- `webUri` null → `sourceUrl` null → manual field empty, user can paste.
- App uninstalled later → resolver falls back to the package string.
- FLAG_SECURE screen → screenshot already null; source capture still proceeds if the assist structure is provided.

## 9. Testing

- **Unit:** `AppInfo.label`/`icon` (mock `PackageManager`, incl. uninstalled fallback); `updateSourceUrl` DAO; the v2→v3 migration (Room migration test).
- **Device:** `onHandleAssist` extraction — verify `sourcePackage`/`sourceUrl` populate when invoking the assistant over a browser (URL present) vs a social app (package only). Deferred to real-device checklist like the rest of the session.

## 10. Non-goals

- No curated app allowlist (generic for all apps).
- No scraping the assist view-tree text or OCR for URLs.
- No page title / type-tag.
- No network — resolution and extraction are all local.

## 11. Coordination note

This feature edits `CaptureSession`, `Capture`/`CaptureDao`/`CaptureDatabase`, `DetailScreen`, `GalleryScreen` — files a second agent is currently editing (model rework). **Implement only after that work has landed** to avoid merge collisions. Re-base this design's line references against the then-current files at implementation time.
