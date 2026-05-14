#!/usr/bin/env bash

set -euo pipefail

# Start Android emulator for this project.
#
# Usage:
#   ./scripts/emulator_start_avd.sh
#   ./scripts/emulator_start_avd.sh <avd_name>
#   ./scripts/emulator_start_avd.sh <avd_name> --headless
#
# Defaults:
#   avd_name: KeyMod_API_35

AVD_NAME="${1:-KeyMod_API_35}"
MODE="${2:-}"

ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

if ! command -v emulator >/dev/null 2>&1; then
  echo "Error: emulator binary not found in PATH."
  echo "Expected under: $ANDROID_HOME/emulator"
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  exit 1
fi

if ! emulator -list-avds | grep -qx "$AVD_NAME"; then
  echo "Error: AVD '$AVD_NAME' not found."
  echo "Available AVDs:"
  emulator -list-avds || true
  exit 1
fi

unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy

echo "==> Starting emulator: $AVD_NAME"
if [[ "$MODE" == "--headless" ]]; then
  nohup emulator -avd "$AVD_NAME" -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim >/tmp/emulator-"$AVD_NAME".log 2>&1 &
  echo "==> Headless mode enabled."
else
  nohup emulator -avd "$AVD_NAME" >/tmp/emulator-"$AVD_NAME".log 2>&1 &
fi

echo "==> Waiting for emulator device to appear in adb..."
for _ in $(seq 1 90); do
  if adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/' | grep -q .; then
    break
  fi
  sleep 2
done

adb devices -l
echo "==> Emulator start command sent."
