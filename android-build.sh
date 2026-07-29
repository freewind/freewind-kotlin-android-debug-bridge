#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="freewind-kotlin-android-debug-bridge"
MODULE_PATH=":demo-app"
MODULE_DIR="demo-app"
BUILD_TYPE="${1:-debug}"

case "$BUILD_TYPE" in
  debug)
    TASK="${MODULE_PATH}:assembleDebug"
    VARIANT_DIR="debug"
    ;;
  release)
    TASK="${MODULE_PATH}:assembleRelease"
    VARIANT_DIR="release"
    ;;
  *)
    echo "Usage: ./android-build.sh [debug|release]" >&2
    exit 1
    ;;
esac

APK_ROOT="$PWD/$MODULE_DIR/build/outputs/apk"
APK_DIR="$APK_ROOT/$VARIANT_DIR"
rm -rf "$APK_ROOT"

./gradlew "$TASK"

shopt -s nullglob
apks=("$APK_DIR"/*.apk)
shopt -u nullglob

if [[ "${#apks[@]}" -ne 1 ]]; then
  echo "Expected exactly one APK in $APK_DIR, got ${#apks[@]}" >&2
  exit 1
fi

APK_PATH="${apks[0]}"
BUILD_TIME="$(date +%H%M)"
TARGET_APK_PATH="$APK_DIR/$PROJECT_NAME-$BUILD_TYPE.$BUILD_TIME.apk"

if [[ "$APK_PATH" != "$TARGET_APK_PATH" ]]; then
  mv -f "$APK_PATH" "$TARGET_APK_PATH"
fi

if command -v open >/dev/null 2>&1; then
  open "$APK_DIR"
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$APK_DIR"
fi

echo "$TARGET_APK_PATH"
