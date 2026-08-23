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

# Do not overwrite local changes or an unfinished merge.
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    echo "ERROR: Your working tree is not clean."
    echo "Commit/stash your local changes before running update.sh."
    exit 1
fi

git merge --ff-only origin/main

echo
echo "[2/4] Preparing Gradle wrapper..."
chmod +x ./gradlew 2>/dev/null || true

# Code On The Go can run the Gradle wrapper through sh even when shared storage
# does not allow direct execution of ./gradlew.
# It also ships an Android SDK/build-tools AAPT2 that is suitable for the phone.
# AGP 8.11 otherwise downloads the Linux x86_64 AAPT2 from Maven, which can fail
# on Code On The Go with: Syntax error: \"(\" unexpected.
AAPT2_OVERRIDE=""

find_aapt2() {
    for sdk in "$ANDROID_SDK_ROOT" "$ANDROID_HOME" \
        "/data/data/com.itsaky.androidide/files/usr/lib/android-sdk" \
        "/data/data/com.itsaky.androidide/files/home/android-sdk" \
        "/storage/emulated/0/Android/sdk"; do
        if [ -n "$sdk" ] && [ -x "$sdk/build-tools/35.0.0/aapt2" ]; then
            echo "$sdk/build-tools/35.0.0/aapt2"
            return 0
        fi
    done
    return 1
}

AAPT2_OVERRIDE="$(find_aapt2 2>/dev/null || true)"

if [ -n "$AAPT2_OVERRIDE" ]; then
    echo "Using Code On The Go AAPT2: $AAPT2_OVERRIDE"
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE"
else
    echo "WARNING: Code On The Go AAPT2 was not found."
    echo "Gradle will use the normal AAPT2 from the Android Gradle Plugin."
fi

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