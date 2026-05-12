#!/usr/bin/env bash

set -euo pipefail

# Install current KeyMod debug app to a connected physical Android phone.
# Usage:
#   ./scripts/install_keymod_to_phone.sh
#   ./scripts/install_keymod_to_phone.sh <device_serial>

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"
 
ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"

export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if [[ ! -x "$GRADLEW" ]]; then
  echo "Error: gradlew not found at $GRADLEW"
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  exit 1
fi

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Error: JAVA_HOME is invalid: $JAVA_HOME"
  exit 1
fi

TARGET_SERIAL="${1:-}"
if [[ -z "$TARGET_SERIAL" ]]; then
  # Pick first connected non-emulator device.
  TARGET_SERIAL="$(
    adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }'
  )"
fi

if [[ -z "$TARGET_SERIAL" ]]; then
  echo "Error: no connected physical Android phone found."
  echo "Tip: connect phone, enable USB debugging, accept RSA prompt, then retry."
  if adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { found=1 } END { exit !found }'; then
    emu="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { print $1; exit }')"
    echo "Tip: this script ignores emulators by default. To install on the emulator, pass its serial, e.g.:"
    echo "  $0 $emu"
  fi
  adb devices
  exit 1
fi

if ! adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$TARGET_SERIAL"; then
  echo "Error: target device '$TARGET_SERIAL' is not in 'device' state."
  adb devices
  exit 1
fi

echo "==> Installing current debug build to phone: $TARGET_SERIAL"
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy
APP_ID="com.openterface.keymod"
INSTALL_LOG="$(mktemp)"

set +e
(
  cd "$ROOT_DIR"
  ANDROID_SERIAL="$TARGET_SERIAL" "$GRADLEW" installDebug --no-daemon
) 2>&1 | tee "$INSTALL_LOG"
INSTALL_EXIT_CODE=${PIPESTATUS[0]}
set -e

INSTALL_OUTPUT="$(<"$INSTALL_LOG")"
if [[ $INSTALL_EXIT_CODE -ne 0 ]] && [[ "$INSTALL_OUTPUT" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* || "$INSTALL_OUTPUT" == *"signatures do not match newer version"* ]]; then
  echo "==> Existing app signature mismatch detected for $APP_ID."
  echo "==> Uninstalling old app from device $TARGET_SERIAL and retrying..."
  adb -s "$TARGET_SERIAL" uninstall "$APP_ID" >/dev/null || true

  (
    cd "$ROOT_DIR"
    ANDROID_SERIAL="$TARGET_SERIAL" "$GRADLEW" installDebug --no-daemon
  )
elif [[ $INSTALL_EXIT_CODE -ne 0 ]]; then
  rm -f "$INSTALL_LOG"
  exit "$INSTALL_EXIT_CODE"
fi

rm -f "$INSTALL_LOG"
echo "==> Done."
