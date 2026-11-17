# Lifetrenz QR Reader (Android)

Scans barcodes with the phone camera and types the value into the laptop as a Bluetooth keyboard. Nothing to install on the laptop.

- Android 12+ (minSdk 31, targetSdk 35), arm64 phones
- Kotlin, Jetpack Compose, CameraX, ML Kit (bundled, offline), `BluetoothHidDevice`
- No network permissions

## Build

Needs Java 17 and the Android SDK (platform 35, build-tools 35.0.0). `local.properties` points to the SDK.

```
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`. A copy is in `../releases/`.

## Signing key (keep it safe)

`keystore/qrreader-release.jks` and `keystore.properties` sign the release APK. Back both up somewhere safe.
If they are lost, new versions cannot be installed over old ones; every phone would need an uninstall first.
Do not share them or commit them.

## Source map

- `hid/HidKeyboard.kt` registers the phone as a keyboard and types text
- `hid/KeyMap.kt` US key table (letters, digits, symbols)
- `MainViewModel.kt` scan handling, duplicate guard, queue, feedback
- `ui/` Scan, Connect, History and Settings screens, camera preview
- `data/` settings and history storage
