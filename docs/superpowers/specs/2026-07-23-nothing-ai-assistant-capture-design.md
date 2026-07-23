# Nothing AI — Assistant-Triggered Capture

**Date:** 2026-07-23
**Status:** Approved design, pre-implementation
**Platform:** Android (native)

## 1. Summary

An Android app that registers as the system **Digital Assistant**. When the user
long-presses the power button, the OS launches the app's assistant session. The
session captures a screenshot of the current screen (provided by the assist API),
records voice until the user taps stop, saves both to local app-private storage,
and transcribes the audio on-device with whisper.cpp. Captures are browsable in an
in-app gallery.

No cloud. No account. Fully offline.

## 2. Why the assistant role (not power-button interception)

Stock Android does not deliver `KEYCODE_POWER` to apps and does not expose
power-button hold duration. The mechanism every voice assistant uses is the
**assistant role**: the OS intercepts the power-hold gesture and hands off to
whichever app holds `RoleManager.ROLE_ASSISTANT`. That handoff also delivers the
current-screen screenshot via `onHandleScreenshot(Bitmap)` — the same path behind
Assistant's "what's on my screen." This gives the user's original vision
(power-button trigger + automatic screenshot) on a non-rooted device.

## 3. Constraints and honest limits

- **Not literal "record while held."** The long-press *launches* the session (the
  button is released); recording then runs until the user stops it. This matches
  every voice-assistant UX. True hold-exactly-while-pressed is not exposed by the OS.
- **Screenshot requires a user toggle** ("use screen context / screenshot" in
  assistant settings) and is **blocked on `FLAG_SECURE` screens** (banking, DRM
  video). The OS returns nothing there; app records audio only and flags the capture.
- **OEM fragmentation.** Pixel/stock: clean. Samsung/Xiaomi may hijack power-hold
  for their own assistant or place the gesture toggle elsewhere. Handled by wizard
  copy, not code.
- **One-time manual setup** (set default assistant, flip the power gesture, enable
  screenshot). Cannot be automated — OS security.

## 4. Stack

- **Native Android, Kotlin.** Assistant role is Android-native only; no
  Flutter/React Native path.
- **UI:** Jetpack Compose.
- **DB:** Room.
- **Background work:** WorkManager (transcription jobs).
- **STT:** whisper.cpp via NDK/JNI. `base` multilingual model, q5 quantized
  (~74MB) to handle English + Hindi (Hinglish) code-switching.
- **minSdk 31** (Android 12 — where the power-hold-assistant gesture exists).
  targetSdk latest.

## 5. Capture pipeline (core flow)

```
Long-press power (OS)
  -> OS launches VoiceInteractionSessionService
  -> onHandleScreenshot(Bitmap)      -> save screenshot.png (or flag if FLAG_SECURE)
  -> session UI: mic starts, live timer + STOP button
  -> user taps STOP
  -> save audio.wav, close session
  -> enqueue WorkManager transcribe job
```

- One `captureId` (timestamp-based) ties screenshot + audio + transcript together.
- Screen unavailable (`FLAG_SECURE` or screenshot toggle off): save audio only,
  set `hasScreenshot = false`.

## 6. Transcription

- WorkManager job runs whisper.cpp on the WAV, produces text.
- Writes transcript into Room and a `transcript.txt` beside the media files.
- Per-capture status: `recording -> pending -> transcribing -> done | failed`.
- Runs off the UI thread. Gallery shows "transcribing…" until done; failure is
  retryable from the detail screen.

## 7. Storage & data model

- **App-internal storage** (`filesDir/captures/<captureId>/`). Rationale:
  screenshots leak whatever is on screen; keep them out of the shared media gallery.
  Per-item export/share on demand.
- Files per capture: `screenshot.png`, `audio.wav`, `transcript.txt`.
- Room `Capture` entity:
  `id (captureId)`, `timestamp`, `hasScreenshot: Boolean`, `durationMs: Long`,
  `transcript: String?`, `status: enum`.

## 8. App UI

- **Gallery:** list, newest first. Each row: screenshot thumbnail, transcript
  snippet, duration, play button. Search by transcript text. Swipe/long-press delete.
- **Detail:** full screenshot, full transcript, audio player, share, delete,
  retry-transcribe.
- **Setup wizard:** first-run and re-checkable. Step-by-step to (a) grant assistant
  role via `RoleManager`, (b) grant mic permission, (c) flip Settings → Gestures →
  Press & hold power → Digital assistant, (d) enable "use screenshot." Deep-links to
  the correct Settings screens where the OS allows.

## 9. Permissions

- `RECORD_AUDIO`
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`
- Assistant role via `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)`
- Manifest: `VoiceInteractionService` with `BIND_VOICE_INTERACTION`, meta-data XML
  pointing at the recognition + session services.

## 10. Testing

- **Unit:** storage writer, Room DAO, whisper JNI wrapper (mocked native layer).
- **Instrumented:** session lifecycle, screenshot save path, `FLAG_SECURE`
  fallback, WorkManager transcribe job.
- **Manual device checklist:** assistant-role handshake and power-gesture launch
  (cannot be emulated reliably).

## 11. Non-goals (YAGNI)

- No cloud, accounts, or sync.
- No transcript editing.
- No literal "record exactly while power held."
- No OEM-specific code hacks beyond wizard wording.
- No shared-gallery storage (privacy).

## 12. Open questions / risks

- whisper `base` q5 latency on low-end phones — may offer `tiny` fallback if too slow.
- Some OEMs disallow third-party assistants entirely; wizard must detect and warn.
- Assist screenshot resolution/format varies by OEM; normalize on save.
