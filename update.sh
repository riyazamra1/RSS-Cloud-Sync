#!/bin/sh

# RSS CLOUD SYNC - update and build helper for Code On The Go
# Run from the project root with: sh update.sh

set -e

echo "========================================"
echo " RSS CLOUD SYNC - GitHub Update"
echo "========================================"
echo

echo "[1/3] Getting latest changes from GitHub..."
git pull --ff-only origin main

echo

echo "[2/3] Making Gradle wrapper executable..."
chmod +x ./gradlew 2>/dev/null || true
echo

echo "[3/3] Building debug APK..."
./gradlew clean assembleDebug

echo
echo "========================================"
echo " BUILD COMPLETE"
echo "========================================"
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
echo
