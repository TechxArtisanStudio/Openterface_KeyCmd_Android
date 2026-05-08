#!/usr/bin/env bash

set -euo pipefail

# Push this repo's Emulator_Downloads/ into the emulator's public Downloads directory
# (/sdcard/Download by default; merges with adb push).
#
# Usage:
#   ./scripts/push_emulator_downloads.sh
#   ./scripts/push_emulator_downloads.sh <emulator_serial>
#
# Optional environment variables:
#   REMOTE_DOWNLOAD_PATH   Path on device (default: /sdcard/Download)
#   LOCAL_DOWNLOADS_DIR    Host folder to push from (default: <repo>/Emulator_Downloads)
#
# Requires: adb, and an emulator in "device" state (see ./scripts/start_emulator.sh).

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [[ -x "/opt/homebrew/bin" ]]; then
  export PATH="/opt/homebrew/bin:$PATH"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  echo "Expected under: $ANDROID_HOME/platform-tools"
  exit 1
fi

REMOTE="${REMOTE_DOWNLOAD_PATH:-/sdcard/Download}"
SRC="${LOCAL_DOWNLOADS_DIR:-$ROOT_DIR/Emulator_Downloads}"

SERIAL="${1:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { print $1; exit }')"
fi

if [[ -z "$SERIAL" ]]; then
  echo "Error: no emulator in 'device' state found."
  echo "Start one with ./scripts/start_emulator.sh or pass a serial: $0 emulator-5554"
  adb devices
  exit 1
fi

if ! adb devices | awk -v s="$SERIAL" 'NR>1 && $1==s && $2=="device" { found=1 } END { exit(found?0:1) }'; then
  echo "Error: device '$SERIAL' is not connected in 'device' state."
  adb devices
  exit 1
fi

if ! adb -s "$SERIAL" shell "test -d '$REMOTE'" </dev/null; then
  echo "Error: remote folder does not exist: $SERIAL:$REMOTE"
  exit 1
fi

if [[ ! -d "$SRC" ]]; then
  echo "Error: local source is not a directory: $SRC"
  echo "Create it or set LOCAL_DOWNLOADS_DIR to an existing folder."
  exit 1
fi

shopt -s nullglob dotglob
entries=( "$SRC"/* )
shopt -u nullglob dotglob
if [[ ${#entries[@]} -eq 0 ]]; then
  echo "Nothing to push: $SRC is empty."
  exit 0
fi

echo "==> Pushing contents of $SRC to $SERIAL:$REMOTE ..."
adb -s "$SERIAL" push "$SRC/." "$REMOTE/"

echo "==> Done. Host folder merged into emulator Download: $SERIAL:$REMOTE"
