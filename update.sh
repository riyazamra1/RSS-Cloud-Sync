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

# Never overwrite local work or an unfinished merge.
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
    echo "ERROR: Your working tree is not clean."
    echo "Commit or stash your local changes before running update.sh."
    exit 1
fi

if [ -f .git/MERGE_HEAD ]; then
    echo "ERROR: An unfinished Git merge exists."
    echo "Finish or abort the merge before running update.sh."
    exit 1
fi

git merge --ff-only origin/main

echo
echo "[2/4] Preparing Code On The Go build environment..."
chmod +x ./gradlew 2>/dev/null || true

# Do NOT put android.aapt2FromMavenOverride in gradle.properties.
# Code On The Go/AndroidIDE may have a phone-native AAPT2, while the AAPT2
# bundled in AGP is often an incompatible x86_64 Linux binary on Android.
find_aapt2() {
    # First prefer an AAPT2 already available on PATH.
    if command -v aapt2 >/dev/null 2>&1; then
        AAPT2_PATH="$(command -v aapt2)"
        if [ -x "$AAPT2_PATH" ]; then
            echo "$AAPT2_PATH"
            return 0
        fi
    fi

    # Then check common Code On The Go / AndroidIDE SDK locations.
    for sdk in \
        "$ANDROID_SDK_ROOT" \
        "$ANDROID_HOME" \
        "/data/data/com.itsaky.androidide/files/usr/lib/android-sdk" \
        "/data/data/com.itsaky.androidide/files/home/android-sdk" \
        "/data/data/com.itsaky.androidide/files/home/.androidide" \
        "/storage/emulated/0/Android/sdk"; do
        [ -n "$sdk" ] || continue

        for bt in 35.0.0 35.0.1 34.0.0 33.0.3; do
            if [ -x "$sdk/build-tools/$bt/aapt2" ]; then
                echo "$sdk/build-tools/$bt/aapt2"
                return 0
            fi
        done

        if [ -x "$sdk/aapt2" ]; then
            echo "$sdk/aapt2"
            return 0
        fi
    done

    return 1
}

AAPT2_OVERRIDE="$(find_aapt2 2>/dev/null || true)"

if [ -n "$AAPT2_OVERRIDE" ]; then
    echo "Using phone-native AAPT2: $AAPT2_OVERRIDE"
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE"
else
    echo "No phone-native AAPT2 found."
    echo "Gradle will use the AAPT2 supplied by the Android Gradle Plugin."
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