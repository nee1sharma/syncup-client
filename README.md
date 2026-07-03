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

Next:

- Scan selected files
- Discover and pair with the server
- Upload, resume, and restore files

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
  -keystore syncup-upload.jks \
  -storetype PKCS12 \
  -alias syncup \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Update `keystore.properties` with the real passwords, then build:

```bash
# First update versionCode and versionName in app/build.gradle.kts

# Signed APK for local distribution
./gradlew assembleRelease

# Signed App Bundle for Google Play
./gradlew bundleRelease
```

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
