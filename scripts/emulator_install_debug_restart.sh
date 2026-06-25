#!/usr/bin/env bash

set -euo pipefail

# Install debug KeyMod (Gradle installDebug) and relaunch the app on any ADB target.
# Default serial is emulator-5554; pass another serial for a phone or different emulator.
# Defaults are tuned for this project on this Mac setup.
#
# Screenshot automation (emulator + adb, API 33+):
#   ./scripts/screenshot-capture/capture_welcome_i18n_screenshots.sh
#   ./scripts/screenshot-capture/capture_readme_i18n_screenshots.sh
#   ./scripts/screenshot-capture/capture_mode_page_screenshots.sh
# See scripts/screenshot-capture/BEHAVIOR_RECORDING.md
#
# Usage:
#   ./scripts/emulator_install_debug_restart.sh
#   ./scripts/emulator_install_debug_restart.sh <device_serial>

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
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Gradle needs a working JDK. Cursor/IDE often exports JAVA_HOME; if it is wrong,
# "A problem occurred starting process 'Gradle build daemon'" is common.
resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    return
  fi
  if [[ -x "$JAVA_HOME_DEFAULT/bin/java" ]]; then
    export JAVA_HOME="$JAVA_HOME_DEFAULT"
    return
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    local mac_home
    mac_home="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null)" || true
    if [[ -n "$mac_home" && -x "$mac_home/bin/java" ]]; then
      export JAVA_HOME="$mac_home"
      return
    fi
  fi
  export JAVA_HOME="$JAVA_HOME_DEFAULT"
}

if [[ -n "${JAVA_HOME:-}" && ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "Warning: JAVA_HOME is set but invalid ($JAVA_HOME); ignoring and searching for JDK."
  unset JAVA_HOME
fi
resolve_java_home

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  echo "Make sure Android platform-tools are installed."
  exit 1
fi

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Error: JAVA_HOME is invalid: $JAVA_HOME"
  echo "Install JDK 17 (e.g. brew install openjdk@17) or set JAVA_HOME to a JDK with bin/java."
  exit 1
fi

# Symlinked JAVA_HOME (e.g. from IDE env) can confuse native Gradle startup; use real path.
if [[ -d "$JAVA_HOME" ]]; then
  JAVA_HOME="$(cd "$JAVA_HOME" && pwd -P)"
  export JAVA_HOME
fi

if ! "$JAVA_HOME/bin/java" -version >/dev/null 2>&1; then
  echo "Error: JAVA_HOME java failed to start: $JAVA_HOME/bin/java"
  echo "Unset JAVA_HOME or fix it; then re-run this script."
  exit 1
fi

echo "==> Using JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>&1 | head -n1 || true

echo "==> Target device: $DEVICE_SERIAL"
if ! adb devices | awk 'NR>1 {print $1}' | grep -qx "$DEVICE_SERIAL"; then
  echo "Error: device '$DEVICE_SERIAL' not found in 'adb devices'."
  adb devices
  exit 1
fi

echo "==> Reinstalling debug build..."
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy

run_install_debug() {
  ANDROID_SERIAL="$DEVICE_SERIAL" "$GRADLEW" installDebug --no-daemon
}

(
  cd "$ROOT_DIR"
  if ! run_install_debug; then
    echo "==> installDebug failed (often: 'Gradle build daemon' JVM could not start)."
    echo "==> Stopping Gradle daemons and retrying once..."
    "$GRADLEW" --stop 2>/dev/null || true
    sleep 1
    run_install_debug
  fi
)

echo "==> Restarting app..."
adb -s "$DEVICE_SERIAL" shell am force-stop "$PACKAGE_NAME"
adb -s "$DEVICE_SERIAL" shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"

echo "==> Done. KeyMod has been reinstalled and restarted on $DEVICE_SERIAL."
