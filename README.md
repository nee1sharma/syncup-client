# SyncUp

SyncUp backs up photos, videos, and files from an Android device to a computer over a private Wi-Fi network.

## Features

- Choose the phone media library or a custom folder
- Filter by file type and date
- Save a default backup configuration
- Back up to a local Spring Boot server
- Resume and restore files

## Current status

Implemented:

- Android home and configuration screens
- Room-backed default presets
- Custom folder, file-type, and date selection
- Automatic LAN server discovery and identity verification
- Cached reconnect, retry, and manual server address
- Local MediaStore file scanning
- Streaming, resumable file upload with SHA-256 verification

Next:

- Multi-device pairing and PIN authentication
- Restore browser and file downloading

## Build

Requirements:

- Android Studio or JDK 17+
- Android SDK 36

From the project root:

```bash
# Build the debug APK
./gradlew assembleDebug

# Install it on a connected device or emulator
./gradlew installDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/SyncUp-debug.apk
```

## Release

Generate an upload keystore once:

```bash
keytool -genkeypair -v \
  -keystore syncup.syncup.syncup-upload.jks \
  -storetype PKCS12 \
  -alias syncup.syncup.syncup \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Update `keystore.properties` with the real passwords, then build:

```bash
# Signed APK for local distribution
./gradlew assembleRelease

# Install the release APK on a connected device
./gradlew installRelease

# Signed App Bundle for Google Play.
# This increments VERSION_CODE in version.properties automatically.
./gradlew bundleRelease
```

Commit the updated `version.properties` after creating a release bundle. The
displayed version is `<VERSION_NAME>.<VERSION_CODE>`, for example `1.0.2`.

Artifacts:

```text
app/build/outputs/apk/release/SyncUp-release.apk
app/build/outputs/bundle/release/SyncUp-release.aab
```

Without `keystore.properties`, Gradle can still compile the release, but the APK is written as `SyncUp-release-unsigned.apk` and is not ready for distribution.

Verify the APK signature:

```bash
apksigner verify --print-certs \
  app/build/outputs/apk/release/SyncUp-release.apk
```

`keystore.properties` and keystore files are ignored by Git. Back them up securely; future updates must use the same signing identity.

When testing a new build directly on a phone, you can also use `adb`:

```bash
# Debug APK
adb install -r app/build/outputs/apk/debug/SyncUp-debug.apk

# Release APK
adb install -r app/build/outputs/apk/release/SyncUp-release.apk
```

> **Note:** If you switch between Debug and Release builds, you may see `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. You must uninstall the existing app from the device first: `adb uninstall com.hitstudio.syncup.client`.
