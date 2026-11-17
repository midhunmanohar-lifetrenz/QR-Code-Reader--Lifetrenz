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

APK: `app/build/outputs/apk/release/app-release.apk`. Release copies go in `dist/` (not in git).

A release build stops with an error if `keystore.properties` is missing, so an APK is never signed with the wrong key.

## Signing key (keep it safe)

`keystore/qrreader-release.jks` and `keystore.properties` sign the release APK. Back both up somewhere safe.
If they are lost, new versions cannot be installed over old ones; every phone would need an uninstall first.
Do not share them or commit them. Both are excluded by `.gitignore`.

## Source map

- `hid/HidKeyboard.kt` registers the phone as a keyboard and types text
- `hid/KeyMap.kt` US key table (letters, digits, symbols)
- `MainViewModel.kt` scan handling, duplicate guard, queue, feedback
- `ui/` Scan, Connect, History and Settings screens, camera preview
- `data/` settings and history storage

## Bluetooth baseline

`baseline/bluetooth-frozen.sha256` holds fingerprints of `hid/HidKeyboard.kt` and `hid/KeyMap.kt` as released in 0.3.0.
`scripts/verify-bluetooth-unchanged.sh` fails if either file changes. Run it before adding Wi-Fi mode or any other feature.

## Notes

- Android pauses the Bluetooth keyboard when the app leaves the screen. The app re-registers and reconnects when it returns.
- With several barcodes in view, the one nearest the centre of the picture is read.
- History is deleted after 7 days by default (Settings).

## Versions

- 0.3.0 review fixes: centre-most barcode and per-value repeat guard, no double sends, partial-send warning,
  visibility permission, background pause handling, connect timeout, history auto-clear and clear confirmation,
  laptops-only paired list, release signing guard.
- 0.2.0 connection feedback and log, auto-reconnect, Google Chat style.
- 0.1.0 first build.
