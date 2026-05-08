#!/usr/bin/env bash

set -euo pipefail

# Remove top-level *.json files from the emulator's public Downloads folder
# (/sdcard/Download by default). Does not recurse into subdirectories.
#
# Usage:
#   ./scripts/clear_emulator_downloads_json.sh
#   ./scripts/clear_emulator_downloads_json.sh <emulator_serial>
#
# Optional environment variables:
#   REMOTE_DOWNLOAD_PATH   Path on device (default: /sdcard/Download)
#
# Requires: adb, and an emulator in "device" state (see ./scripts/start_emulator.sh).

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

json_names=()
while IFS= read -r name; do
  name="${name//$'\r'/}"
  [[ -z "$name" ]] && continue
  json_names[${#json_names[@]}]="$name"
done < <(
  adb -s "$SERIAL" shell "ls -1 '$REMOTE' 2>/dev/null" | tr -d '\r' | grep -E '\.json$' || true
)

if [[ ${#json_names[@]} -eq 0 ]]; then
  echo "No .json files in $SERIAL:$REMOTE (nothing to remove)."
  exit 0
fi

echo "==> Removing ${#json_names[@]} JSON file(s) from $SERIAL:$REMOTE ..."
for name in "${json_names[@]}"; do
  [[ -z "$name" ]] && continue
  echo "    rm $name"
  adb -s "$SERIAL" shell "rm -f '$REMOTE/$name'"
done

echo "==> Done."
