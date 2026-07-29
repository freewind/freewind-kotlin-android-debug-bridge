#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME="freewind-kotlin-android-debug-bridge"
MODULE_DIR="demo-app"
APPLICATION_ID="com.freewind.android.debugbridge.demo"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
APK_DIRS=("$PWD/dist/android" "$PWD/$MODULE_DIR/build/outputs/apk")
APK_SOURCE_DIR=""

apks=()
for apk_dir in "${APK_DIRS[@]}"; do
  current_apks=()
  if [[ -d "$apk_dir" ]]; then
    while IFS= read -r apk_path; do
      current_apks+=("$apk_path")
    done < <(/usr/bin/find "$apk_dir" -type f -name '*.apk' | sort)
  fi
  if [[ "${#current_apks[@]}" -gt 0 ]]; then
    apks=("${current_apks[@]}")
    APK_SOURCE_DIR="$apk_dir"
    break
  fi
done

if [[ "${#apks[@]}" -eq 0 ]]; then
  echo "APK not found under:" >&2
  printf '  %s\n' "${APK_DIRS[@]}" >&2
  echo "Run ./android-build.sh first" >&2
  exit 1
fi

if [[ "${#apks[@]}" -ne 1 ]]; then
  echo "Expected exactly one APK under $APK_SOURCE_DIR, got ${#apks[@]}:" >&2
  printf '%s\n' "${apks[@]}" >&2
  exit 1
fi

APK_PATH="${apks[0]}"

DEVICES=()
while IFS= read -r device; do
  DEVICES+=("$device")
done < <("$ADB" devices | awk '$1 !~ /^emulator-/ && $2 == "device" { print $1 }')

if [[ "${#DEVICES[@]}" -ne 1 ]]; then
  echo "Expected exactly one phone device, got ${#DEVICES[@]}" >&2
  "$ADB" devices >&2
  exit 1
fi

SERIAL="${DEVICES[0]}"
"$ADB" -s "$SERIAL" wait-for-device
"$ADB" -s "$SERIAL" install -r "$APK_PATH"
"$ADB" -s "$SERIAL" shell monkey -p "$APPLICATION_ID" -c android.intent.category.LAUNCHER 1
echo "$SERIAL"
