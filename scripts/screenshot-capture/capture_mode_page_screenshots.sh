#!/usr/bin/env bash

set -euo pipefail

CAPTURE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$CAPTURE_DIR/../.." && pwd)"
KEYMOD_APP_REPO="${KEYMOD_APP_REPO:-$REPO_ROOT}"
KEYMOD_INSTALL_SCRIPT="$KEYMOD_APP_REPO/scripts/emulator_install_debug_restart.sh"
ACTION_RUNNER="$CAPTURE_DIR/action_test_keymod.sh"
ACTION_FILE="$CAPTURE_DIR/actions/mode_pages_capture.actions"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/screenshot-capture/capture_mode_page_screenshots.sh [options] [device_serial]

Options:
  --skip-install     Do not run emulator_install_debug_restart.sh
  -h, --help         Show this help

Examples:
  ./scripts/screenshot-capture/capture_mode_page_screenshots.sh
  ./scripts/screenshot-capture/capture_mode_page_screenshots.sh --skip-install emulator-5554
EOF
}

SKIP_INSTALL=0
SERIAL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-install) SKIP_INSTALL=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      if [[ -n "$SERIAL" ]]; then
        echo "Error: unexpected extra argument: $1" >&2
        exit 1
      fi
      SERIAL="$1"
      shift
      ;;
  esac
done

SERIAL="${SERIAL:-emulator-5554}"

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH." >&2
  exit 1
fi

if [[ ! -f "$ACTION_RUNNER" ]] || [[ ! -f "$ACTION_FILE" ]]; then
  echo "Error: capture scripts or actions missing under $CAPTURE_DIR" >&2
  exit 1
fi

if ! adb devices | awk -v target="$SERIAL" 'NR>1 && $2=="device" && $1==target { found=1 } END { exit(found?0:1) }'; then
  echo "Error: device '$SERIAL' is not in 'device' state." >&2
  adb devices >&2
  exit 1
fi

if [[ "$SKIP_INSTALL" != 1 ]]; then
  [[ -f "$KEYMOD_INSTALL_SCRIPT" ]] || {
    echo "Error: install script not found: $KEYMOD_INSTALL_SCRIPT" >&2
    exit 1
  }
  echo "==> Installing debug build via: $KEYMOD_INSTALL_SCRIPT $SERIAL"
  bash "$KEYMOD_INSTALL_SCRIPT" "$SERIAL"
else
  echo "==> --skip-install: not reinstalling app."
fi

TS="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$REPO_ROOT/artifacts/mode-pages/$TS"
mkdir -p "$OUT_DIR"

echo "==> Capturing mode pages to: $OUT_DIR"
export ACTION_TEST_SKIP_INITIAL_LAUNCH=1
export ACTION_TEST_ARTIFACT_ROOT="$OUT_DIR"
export ACTION_TEST_VERBOSE=1
export ACTION_TEST_KM_USE_TAPS=1
bash "$ACTION_RUNNER" run "$ACTION_FILE" "$SERIAL"
unset ACTION_TEST_SKIP_INITIAL_LAUNCH ACTION_TEST_ARTIFACT_ROOT ACTION_TEST_VERBOSE ACTION_TEST_KM_USE_TAPS

echo "==> Done. Artifact path: $OUT_DIR"
echo "==> Generated PNG files:"
shopt -s nullglob
for png in "$OUT_DIR"/*.png; do
  echo "  - $(basename "$png")"
done
shopt -u nullglob
