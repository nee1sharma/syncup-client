#!/bin/bash
set -e

# Function to print error and exit
error_exit() {
    echo "ERROR: $1" >&2
    exit 1
}

# Check if gh cli is installed
command -v gh >/dev/null 2>&1 || error_exit "GitHub CLI (gh) is not installed. Please install it first."

# Extract version from version.properties
if [ ! -f version.properties ]; then
    error_exit "version.properties not found."
fi

VERSION_NAME=$(grep 'VERSION_NAME' version.properties | cut -d'=' -f2)
VERSION_CODE=$(grep 'VERSION_CODE' version.properties | cut -d'=' -f2)
TAG="v$VERSION_NAME.$VERSION_CODE"

echo "Project: SyncUp"
echo "Target Version: $TAG"

# Build the release APK
echo "Building release APK..."
./gradlew :app:assembleRelease || error_exit "Gradle build failed."

# Locate the APK
# Based on 'base { archivesName.set("SyncUp") }' in build.gradle.kts
APK_PATH="app/build/outputs/apk/release/SyncUp-release.apk"

if [ ! -f "$APK_PATH" ]; then
    # Fallback search if the name is different
    APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" | head -n 1)
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    error_exit "Could not find the release APK."
fi

echo "Found APK: $APK_PATH"

# Check if tag already exists
if gh release view "$TAG" >/dev/null 2>&1; then
    read -p "Release $TAG already exists. Do you want to overwrite it? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
    echo "Updating release $TAG..."
    gh release upload "$TAG" "$APK_PATH" --clobber
else
    echo "Creating GitHub release $TAG..."
    gh release create "$TAG" "$APK_PATH" --title "Release $TAG" --generate-notes
fi

echo "Successfully released $TAG"
