# Echoframe

An Android app that registers as the device's digital assistant. When invoked, it records
ambient audio, takes a screenshot of the current screen, and notes which app (and web URL,
when available) it came from. The screenshot is read on-device with ML Kit text recognition
and image labelling to produce a searchable note. Everything is stored in the app's private
storage; the app makes no network requests of its own.

See [PRIVACY.md](PRIVACY.md) for exactly what is captured and stored.

## Requirements

- `minSdk` 31 (Android 12), `targetSdk` / `compileSdk` 35
- JDK 17
- Android Gradle Plugin 8.5.2, Kotlin 2.0.20

## Build

```sh
./gradlew assembleDebug          # debug APK, no keystore needed
./gradlew installDebug           # install on a connected device/emulator
```

To actually capture anything, set Echoframe as the default digital assistant:
Settings → Apps → Default apps → Digital assistant app.

### Release build

Release signing credentials are read from `keystore.properties` at the repo root, or from
the `ECHOFRAME_STORE_FILE` / `ECHOFRAME_STORE_PASSWORD` / `ECHOFRAME_KEY_ALIAS` /
`ECHOFRAME_KEY_PASSWORD` environment variables. Copy `keystore.properties.example` to
`keystore.properties` and fill it in. Neither the keystore nor the properties file is
committed. Without credentials, `assembleRelease` still runs but emits an **unsigned**
artifact (it never falls back to the debug key).

The release variant is split by ABI: `arm64-v8a` and `armeabi-v7a` APKs plus a universal
one. ML Kit's x86/x86_64 native libraries are ~47MB that only emulators load, so the
per-ABI artifacts are roughly a third the size of the universal build.

## Releases

Every push to `main` runs `.github/workflows/release.yml`: unit tests, lint, a signed
release build, an explicit `apksigner` check on each APK, then a GitHub release tagged
`v<versionName>-build.<run number>`. Pull requests run the same tests and lint via
`.github/workflows/ci.yml`.

CI signs with four repository secrets. Set them once, from a machine holding the keystore:

```sh
base64 -i echoframe-release.jks | gh secret set ECHOFRAME_KEYSTORE_BASE64
gh secret set ECHOFRAME_STORE_PASSWORD
gh secret set ECHOFRAME_KEY_ALIAS
gh secret set ECHOFRAME_KEY_PASSWORD
```

Without them the release workflow stops before it builds, rather than publishing an
unsigned APK. `versionCode` comes from the workflow run number so each release build is
distinct; local builds stay at 1. Bumping `versionName` in `app/build.gradle.kts` is
still manual.

## Tests

```sh
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

## License

See [LICENSE](LICENSE).
