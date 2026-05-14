#!/usr/bin/env bash

set -euo pipefail

# Sync emulator display size/density from a connected physical Android phone.
#
# Usage:
#   ./scripts/emulator_sync_display_from_phone.sh
#   ./scripts/emulator_sync_display_from_phone.sh <phone_serial> <emulator_serial>
#
# Notes:
# - Defaults to first connected physical device and first connected emulator.
# - Uses runtime overrides via `wm size` and `wm density`.
# - To reset later on emulator:
#     adb -s <emulator_serial> shell wm size reset
#     adb -s <emulator_serial> shell wm density reset

export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  exit 1
fi

PHONE_SERIAL="${1:-}"
EMU_SERIAL="${2:-}"

if [[ -z "$PHONE_SERIAL" ]]; then
  PHONE_SERIAL="$(
    adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }'
  )"
fi

if [[ -z "$EMU_SERIAL" ]]; then
  EMU_SERIAL="$(
    adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { print $1; exit }'
  )"
fi

if [[ -z "$PHONE_SERIAL" ]]; then
  echo "Error: no connected physical phone found."
  adb devices
  exit 1
fi

if [[ -z "$EMU_SERIAL" ]]; then
  echo "Error: no connected emulator found."
  adb devices
  exit 1
fi

if ! adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$PHONE_SERIAL"; then
  echo "Error: phone '$PHONE_SERIAL' is not in device state."
  adb devices
  exit 1
fi

if ! adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$EMU_SERIAL"; then
  echo "Error: emulator '$EMU_SERIAL' is not in device state."
  adb devices
  exit 1
fi

SIZE_LINE="$(adb -s "$PHONE_SERIAL" shell wm size | tr -d '\r')"
DENSITY_LINE="$(adb -s "$PHONE_SERIAL" shell wm density | tr -d '\r')"

SIZE="$(echo "$SIZE_LINE" | awk -F': ' '/Physical size/ {print $2}')"
DENSITY="$(echo "$DENSITY_LINE" | awk -F': ' '/Physical density/ {print $2}')"

if [[ -z "$SIZE" || -z "$DENSITY" ]]; then
  echo "Error: failed to parse phone display info."
  echo "wm size output: $SIZE_LINE"
  echo "wm density output: $DENSITY_LINE"
  exit 1
fi

echo "==> Phone ($PHONE_SERIAL): size=$SIZE density=$DENSITY"
echo "==> Applying to emulator ($EMU_SERIAL)..."

adb -s "$EMU_SERIAL" shell wm size "$SIZE"
adb -s "$EMU_SERIAL" shell wm density "$DENSITY"

echo "==> Emulator updated."
echo "==> New emulator values:"
adb -s "$EMU_SERIAL" shell wm size
adb -s "$EMU_SERIAL" shell wm density
