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

## Tests

```sh
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

## License

See [LICENSE](LICENSE).
