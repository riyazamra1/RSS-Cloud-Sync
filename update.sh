#!/bin/sh

# RSS CLOUD SYNC - Code On The Go update + build helper
# Run from the project root with: sh update.sh

set -e

echo "========================================"
echo " RSS CLOUD SYNC - GitHub Update"
echo "========================================"
echo
echo "[1/4] Getting latest changes from GitHub..."
git fetch origin main

# Refuse to overwrite local work or an unfinished merge.
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    echo "ERROR: Your working tree is not clean."
    echo "Commit/stash your local changes before running update.sh."
    exit 1
fi

# Fast-forward only. This prevents accidental merge conflicts in Code On The Go.
git merge --ff-only origin/main

echo
echo "[2/4] Preparing Gradle wrapper..."
chmod +x ./gradlew 2>/dev/null || true

# Code On The Go can block execution of files located on shared storage.
# Calling the wrapper through /system/bin/sh avoids the Permission denied error.
echo
echo "[3/4] Building debug APK..."
sh ./gradlew --stop 2>/dev/null || true
sh ./gradlew clean assembleDebug --no-daemon

echo
echo "[4/4] Build finished"
echo "========================================"
echo " BUILD COMPLETE"
echo "========================================"
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
echo