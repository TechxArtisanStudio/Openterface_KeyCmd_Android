#!/usr/bin/env bash

set -euo pipefail

# Build + install KeyMod debug APK to a connected physical phone,
# then mirror/control the phone on desktop with scrcpy.
#
# Usage:
#   ./scripts/install_and_control_phone.sh
#   ./scripts/install_and_control_phone.sh <device_serial>
#
# Optional env vars:
#   SCRCPY_OPTS="--max-fps=60 --bit-rate=8M"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_SCRIPT="$ROOT_DIR/scripts/install_to_phone.sh"

ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [[ -x "/opt/homebrew/bin" ]]; then
  export PATH="/opt/homebrew/bin:$PATH"
fi

if [[ ! -x "$INSTALL_SCRIPT" ]]; then
  echo "Error: install script not found/executable: $INSTALL_SCRIPT"
  exit 1
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
fi

if [[ -z "$TARGET_SERIAL" ]]; then
  echo "Error: no connected physical Android phone found."
  echo "Tip: connect phone, enable USB debugging, and accept RSA prompt."
  adb devices
  exit 1
fi

if ! adb devices | awk -v target="$TARGET_SERIAL" 'NR>1 && $2=="device" && $1==target { found=1 } END { exit(found?0:1) }'; then
  echo "Error: target device '$TARGET_SERIAL' is not in 'device' state."
  adb devices
  exit 1
fi

echo "==> Installing KeyMod debug app to $TARGET_SERIAL ..."
"$INSTALL_SCRIPT" "$TARGET_SERIAL"

echo "==> Launching app on phone ..."
adb -s "$TARGET_SERIAL" shell monkey -p com.openterface.keymod -c android.intent.category.LAUNCHER 1 >/dev/null

echo "==> Opening scrcpy mirror + control (Ctrl+C to stop) ..."
if [[ -n "${SCRCPY_OPTS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_OPTS=( ${SCRCPY_OPTS} )
else
  EXTRA_OPTS=()
fi

exec "$SCRCPY_BIN" -s "$TARGET_SERIAL" --stay-awake "${EXTRA_OPTS[@]}"
