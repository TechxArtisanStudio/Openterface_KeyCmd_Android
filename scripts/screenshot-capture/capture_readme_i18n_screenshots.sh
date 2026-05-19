#!/usr/bin/env bash

set -euo pipefail

# Capture README demo screenshots per locale on an Android emulator (API 33+).
#
# Output: ./artifacts/readme-i18n/<locale>/demo-*.png

CAPTURE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$CAPTURE_DIR/../.." && pwd)"
# shellcheck source=lib/i18n_capture_common.sh
source "$CAPTURE_DIR/lib/i18n_capture_common.sh"

KEYCMD_APP_REPO="$(i18n_capture_resolve_app_repo "$REPO_ROOT")"
KEYMOD_APP_REPO="$KEYCMD_APP_REPO"
INSTALL_SCRIPT="$KEYCMD_APP_REPO/scripts/emulator_install_debug_restart.sh"
ACTION_RUNNER="$CAPTURE_DIR/action_test_keymod.sh"
ACTION_FILE="$CAPTURE_DIR/actions/readme_i18n_capture.actions"

I18N_CAPTURE_PACKAGE="com.openterface.keymod"
I18N_CAPTURE_MAIN_ACTIVITY="com.openterface.keymod/.LaunchPanelActivity"

i18n_capture_setup_path

usage() {
  cat <<'EOF'
Usage:
  ./scripts/screenshot-capture/capture_readme_i18n_screenshots.sh [options] [device_serial]

Options:
  --skip-install     Do not run emulator_install_debug_restart.sh
  --locales LIST     Comma-separated readme locale keys (default: all)
  --no-jpeg          Keep PNG only
  -h, --help         Show this help

Examples:
  ./scripts/screenshot-capture/capture_readme_i18n_screenshots.sh
  ./scripts/screenshot-capture/capture_readme_i18n_screenshots.sh --locales en,ja --skip-install emulator-5554
EOF
}

SKIP_INSTALL=0
AUTO_LOCALES=1
LOCALES_LIST=""
I18N_CAPTURE_CONVERT_JPEG=1
I18N_CAPTURE_SERIAL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-install) SKIP_INSTALL=1; shift ;;
    --locales) AUTO_LOCALES=0; LOCALES_LIST="${2:-}"; shift 2 ;;
    --locales=*) AUTO_LOCALES=0; LOCALES_LIST="${1#*=}"; shift ;;
    --no-jpeg) I18N_CAPTURE_CONVERT_JPEG=0; shift ;;
    -h|--help) usage; exit 0 ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      if [[ -n "$I18N_CAPTURE_SERIAL" ]]; then
        echo "Error: unexpected extra argument: $1" >&2
        exit 1
      fi
      I18N_CAPTURE_SERIAL="$1"
      shift
      ;;
  esac
done
I18N_CAPTURE_SERIAL="${I18N_CAPTURE_SERIAL:-emulator-5554}"

i18n_capture_check_adb
[[ -f "$ACTION_RUNNER" ]] || { echo "Error: action runner not found: $ACTION_RUNNER" >&2; exit 1; }
[[ -f "$ACTION_FILE" ]] || { echo "Error: action file not found: $ACTION_FILE" >&2; exit 1; }

i18n_capture_check_device "$I18N_CAPTURE_SERIAL"
i18n_capture_check_api33 "$I18N_CAPTURE_SERIAL"
i18n_capture_parse_locales "$AUTO_LOCALES" "$LOCALES_LIST"

if [[ "$SKIP_INSTALL" != 1 ]]; then
  [[ -f "$INSTALL_SCRIPT" ]] || {
    echo "Error: install script not found: $INSTALL_SCRIPT" >&2
    exit 1
  }
  echo "==> Installing debug build via: $INSTALL_SCRIPT $I18N_CAPTURE_SERIAL"
  bash "$INSTALL_SCRIPT" "$I18N_CAPTURE_SERIAL"
else
  echo "==> --skip-install: not reinstalling app."
fi

i18n_capture_run_locale_loop "readme-i18n" "$ACTION_RUNNER" "$ACTION_FILE" "$REPO_ROOT"

echo ""
echo "==> Done. Artifacts under: $REPO_ROOT/artifacts/readme-i18n/<locale>/"
