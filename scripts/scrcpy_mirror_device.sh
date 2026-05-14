#!/usr/bin/env bash

set -euo pipefail

# Mirror/control a connected Android device on desktop with scrcpy (no APK install).
# Prefers a physical USB device; if none, uses the first online device (e.g. emulator).
#
# Usage:
#   ./scripts/scrcpy_mirror_device.sh
#   ./scripts/scrcpy_mirror_device.sh <device_serial>
#
# Optional env vars:
#   SCRCPY_OPTS="--max-fps=60 --bit-rate=8M"

ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [[ -x "/opt/homebrew/bin" ]]; then
  export PATH="/opt/homebrew/bin:$PATH"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  exit 1
fi

SCRCPY_BIN="${SCRCPY_BIN:-}"
if [[ -z "$SCRCPY_BIN" ]]; then
  if command -v scrcpy >/dev/null 2>&1; then
    SCRCPY_BIN="$(command -v scrcpy)"
  elif [[ -x "/opt/homebrew/bin/scrcpy" ]]; then
    SCRCPY_BIN="/opt/homebrew/bin/scrcpy"
  elif [[ -x "/usr/local/bin/scrcpy" ]]; then
    SCRCPY_BIN="/usr/local/bin/scrcpy"
  fi
fi

if [[ -z "$SCRCPY_BIN" ]]; then
  echo "Error: scrcpy not found in PATH."
  echo "Install on macOS: brew install scrcpy"
  echo "Or set SCRCPY_BIN=/full/path/to/scrcpy"
  exit 1
fi

TARGET_SERIAL="${1:-}"
if [[ -z "$TARGET_SERIAL" ]]; then
  TARGET_SERIAL="$(
    adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }'
  )"
  if [[ -z "$TARGET_SERIAL" ]]; then
    TARGET_SERIAL="$(
      adb devices | awk 'NR>1 && $2=="device" { print $1; exit }'
    )"
  fi
fi

if [[ -z "$TARGET_SERIAL" ]]; then
  echo "Error: no connected Android device found (physical or emulator)."
  echo "Tip: connect a phone with USB debugging, start an emulator, or pass serial:"
  echo "  $0 emulator-5554"
  adb devices
  exit 1
fi

if ! adb devices | awk -v target="$TARGET_SERIAL" 'NR>1 && $2=="device" && $1==target { found=1 } END { exit(found?0:1) }'; then
  echo "Error: target device '$TARGET_SERIAL' is not in 'device' state."
  adb devices
  exit 1
fi

echo "==> Opening scrcpy mirror + control for $TARGET_SERIAL (Ctrl+C to stop) ..."
if [[ "$TARGET_SERIAL" =~ ^emulator- ]]; then
  echo "    (using emulator; plug in a USB phone to prefer it automatically)"
fi
if [[ -n "${SCRCPY_OPTS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_OPTS=( ${SCRCPY_OPTS} )
else
  EXTRA_OPTS=()
fi

exec "$SCRCPY_BIN" -s "$TARGET_SERIAL" --stay-awake "${EXTRA_OPTS[@]}"
