#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
export JAVA_HOME="/home/d4f76066/tools/jdk-17"
export ANDROID_HOME="/home/d4f76066/tools/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

cd "$DIR"

echo "=== Running Unit Tests ==="
./gradlew testDebugUnitTest

echo "=== Building Debug APK ==="
./gradlew assembleDebug

cp "$DIR/app/build/outputs/apk/debug/app-debug.apk" "$DIR/PlayTimeIndicator-debug.apk"

echo "=== Build Complete! ==="
echo "Output APK: $DIR/PlayTimeIndicator-debug.apk"
