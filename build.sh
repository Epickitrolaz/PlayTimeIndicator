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

echo "=== Building Release APK ==="
./gradlew assembleRelease

cp "$DIR/app/build/outputs/apk/debug/app-debug.apk" "$DIR/PlayTimeIndicator-debug.apk"
cp "$DIR/app/build/outputs/apk/release/app-release.apk" "$DIR/PlayTimeIndicator-v1.0.0.apk"
ln -sf "$DIR/PlayTimeIndicator-v1.0.0.apk" "$DIR/PlayTimeIndicator-release.apk"

echo "=== Build Complete! ==="
echo "Debug APK:   $DIR/PlayTimeIndicator-debug.apk"
echo "Release APK: $DIR/PlayTimeIndicator-v1.0.0.apk"
