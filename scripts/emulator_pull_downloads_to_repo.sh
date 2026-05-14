#!/usr/bin/env bash

set -euo pipefail

# Pull the emulator's public Downloads folder into this repo's Emulator_Downloads/
# (merge: same basenames overwrite on the host).
#
# Usage:
#   ./scripts/emulator_pull_downloads_to_repo.sh
#   ./scripts/emulator_pull_downloads_to_repo.sh <emulator_serial>
#
# Optional environment variables:
#   REMOTE_DOWNLOAD_PATH   Path on device (default: /sdcard/Download)
#   LOCAL_DOWNLOADS_DIR    Host destination (default: <repo>/Emulator_Downloads)
#
# Requires: adb, and an emulator in "device" state (see ./scripts/emulator_start_avd.sh).

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
DEST="${LOCAL_DOWNLOADS_DIR:-$ROOT_DIR/Emulator_Downloads}"

SERIAL="${1:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { print $1; exit }')"
fi

if [[ -z "$SERIAL" ]]; then
  echo "Error: no emulator in 'device' state found."
  echo "Start one with ./scripts/emulator_start_avd.sh or pass a serial: $0 emulator-5554"
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

mkdir -p "$DEST"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/emulator-downloads.XXXXXX")"
cleanup() {
  rm -rf "$WORK"
}
trap cleanup EXIT

echo "==> Pulling $REMOTE from $SERIAL ..."
adb -s "$SERIAL" pull "$REMOTE" "$WORK"

PULLED="$WORK/$(basename "$REMOTE")"
if [[ ! -d "$PULLED" ]]; then
  echo "Error: unexpected pull layout under $WORK (expected $(basename "$REMOTE")/)."
  ls -la "$WORK" || true
  exit 1
fi

echo "==> Merging into $DEST ..."
cp -a "$PULLED/." "$DEST/"

echo "==> Done. Emulator Download merged into: $DEST"
