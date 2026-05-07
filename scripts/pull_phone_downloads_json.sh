#!/usr/bin/env bash

set -euo pipefail

# Pull *.json files from a connected physical phone's Download folder into this
# repo's Emulator_Downloads/ (top-level files only, not subfolders).
#
# Usage:
#   ./scripts/pull_phone_downloads_json.sh
#   ./scripts/pull_phone_downloads_json.sh <device_serial>
#
# Optional environment variables:
#   REMOTE_DOWNLOAD_PATH   Path on device (default: /sdcard/Download)
#   LOCAL_DOWNLOADS_DIR    Host destination (default: <repo>/Emulator_Downloads)
#
# Requires: adb, USB debugging, and a non-emulator device in "device" state
# (same discovery as ./scripts/control_phone.sh).

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
  SERIAL="$(
    adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }'
  )"
fi

if [[ -z "$SERIAL" ]]; then
  echo "Error: no connected physical Android phone found."
  echo "Tip: connect phone, enable USB debugging, accept RSA prompt, or pass serial: $0 <serial>"
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

json_names=()
while IFS= read -r name; do
  name="${name//$'\r'/}"
  [[ -z "$name" ]] && continue
  json_names[${#json_names[@]}]="$name"
done < <(
  adb -s "$SERIAL" shell "ls -1 '$REMOTE' 2>/dev/null" | tr -d '\r' | grep -E '\.json$' || true
)

if [[ ${#json_names[@]} -eq 0 ]]; then
  echo "No .json files found in $SERIAL:$REMOTE"
  exit 0
fi

echo "==> Pulling ${#json_names[@]} JSON file(s) from $SERIAL:$REMOTE into $DEST ..."
for name in "${json_names[@]}"; do
  [[ -z "$name" ]] && continue
  echo "    $name"
  adb -s "$SERIAL" pull "$REMOTE/$name" "$DEST/"
done

echo "==> Done."
