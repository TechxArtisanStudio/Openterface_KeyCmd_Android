#!/usr/bin/env bash

set -euo pipefail

# Reinstall and restart KeyMod on an Android device/emulator.
# Defaults are tuned for this project on this Mac setup.
#
# For README screenshots across locales (emulator + adb), see the testing repo:
#   ../Openterface_KeyMod_Android_testing/scripts/capture_readme_i18n_screenshots.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"

ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
PACKAGE_NAME="com.openterface.keymod"
ACTIVITY_NAME=".LaunchPanelActivity"
DEVICE_SERIAL="${1:-emulator-5554}"

if [[ ! -x "$GRADLEW" ]]; then
  echo "Error: gradlew not found or not executable at $GRADLEW"
  exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  echo "Make sure Android platform-tools are installed."
  exit 1
fi

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Error: JAVA_HOME is invalid: $JAVA_HOME"
  echo "Set JAVA_HOME and retry."
  exit 1
fi

echo "==> Target device: $DEVICE_SERIAL"
if ! adb devices | awk 'NR>1 {print $1}' | grep -qx "$DEVICE_SERIAL"; then
  echo "Error: device '$DEVICE_SERIAL' not found in 'adb devices'."
  adb devices
  exit 1
fi

echo "==> Reinstalling debug build..."
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy
(
  cd "$ROOT_DIR"
  ANDROID_SERIAL="$DEVICE_SERIAL" "$GRADLEW" installDebug --no-daemon
)

echo "==> Restarting app..."
adb -s "$DEVICE_SERIAL" shell am force-stop "$PACKAGE_NAME"
adb -s "$DEVICE_SERIAL" shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"

echo "==> Done. KeyMod has been reinstalled and restarted on $DEVICE_SERIAL."
