#!/usr/bin/env bash

set -euo pipefail

# Scripted action testing for Openterface KM on a connected Android device or emulator.
#
# Usage:
#   ./scripts/screenshot-capture/action_test_keymod.sh smoke
#   ./scripts/screenshot-capture/action_test_keymod.sh run scripts/screenshot-capture/actions/keymod_smoke.actions
#   ./scripts/screenshot-capture/action_test_keymod.sh smoke <device_serial>
#
# Action file format (one command per line):
#   WAIT <seconds>
#   TAP <x> <y>
#   TAP_REL <xfrac> <yfrac>   # 0.0–1.0 relative to wm size (set after reading screen size)
#   SWIPE <x1> <y1> <x2> <y2> [duration_ms]
#   SWIPE_REL <x1f> <y1f> <x2f> <y2f> [duration_ms]   # fractions of width/height
#   KEY <android_keycode_name_or_number>
#   TEXT <text_with_spaces>
#   SHELL <raw adb shell command>
#   SCREENSHOT <label>
#   ORIENTATION portrait|landscape   # locks rotation (OEM differences possible)
#   LAUNCH_WELCOME
#   LAUNCH_KM_BASIC keyboard|touchpad|numpad
#   PREPARE_CAPTURE   # writes shared_prefs flags via run-as (debug builds)
#
# Notes:
# - Lines starting with '#' are ignored.
# - Artifacts: ./artifacts/action-tests/<timestamp>/ unless ACTION_TEST_ARTIFACT_ROOT is set.
# - If ACTION_TEST_SKIP_INITIAL_LAUNCH=1, the runner does not launch the app or take 01_launched
#   before the action file (caller already brought the app to the right activity).
# - adb_cmd redirects stdin from /dev/null so `adb shell` never eats the action file when stdin
#   is redirected with `done <file>` (otherwise only the first adb shell command runs).
# - ACTION_TEST_VERBOSE=1 prints each action line to stderr before executing (replay audit).
# - If MainActivity shell launch is blocked (non-exported activity on some builds), set
#   ACTION_TEST_KM_USE_TAPS=1 to fallback to Start + tab taps.

CAPTURE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$CAPTURE_DIR/../.." && pwd)"
APP_REPO="${APP_REPO:-$REPO_ROOT}"

ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/platform-tools:/opt/homebrew/bin:/usr/local/bin:$PATH"

APP_WELCOME_ACTIVITY="com.openterface.keymod/.LaunchPanelActivity"
APP_MAIN_ACTIVITY="com.openterface.keymod/.MainActivity"

MODE="${1:-smoke}"
SECOND_ARG="${2:-}"

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb not found in PATH."
  exit 1
fi

# Prefer a running emulator when present (README / CI flows), else first physical device.
pick_default_device() {
  local d
  d="$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ { print $1; exit }')"
  if [[ -n "$d" ]]; then
    echo "$d"
    return
  fi
  adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }'
}

if [[ "$MODE" == "run" ]]; then
  ACTION_FILE="${SECOND_ARG:-}"
  TARGET_SERIAL="${3:-}"
  if [[ -z "$ACTION_FILE" ]]; then
    echo "Error: missing action file."
    echo "Usage: ./scripts/screenshot-capture/action_test_keymod.sh run <action_file> [device_serial]"
    exit 1
  fi
else
  ACTION_FILE=""
  TARGET_SERIAL="${SECOND_ARG:-}"
fi

if [[ -z "$TARGET_SERIAL" ]]; then
  TARGET_SERIAL="$(pick_default_device)"
fi

if [[ -z "$TARGET_SERIAL" ]]; then
  echo "Error: no connected Android device or emulator found."
  adb devices
  exit 1
fi

if ! adb devices | awk -v target="$TARGET_SERIAL" 'NR>1 && $2=="device" && $1==target { found=1 } END { exit(found?0:1) }'; then
  echo "Error: target device '$TARGET_SERIAL' is not in 'device' state."
  adb devices
  exit 1
fi

TS="$(date +%Y%m%d_%H%M%S)"
if [[ -n "${ACTION_TEST_ARTIFACT_ROOT:-}" ]]; then
  OUT_DIR="$ACTION_TEST_ARTIFACT_ROOT"
else
  OUT_DIR="$REPO_ROOT/artifacts/action-tests/$TS"
fi
mkdir -p "$OUT_DIR"

LOGCAT_FILE="$OUT_DIR/logcat.txt"
LOGCAT_PID=""

cleanup() {
  if [[ -n "${LOGCAT_PID:-}" ]]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

adb_cmd() {
  # Never let adb read the script's stdin. `while read ... done <file` leaves stdin
  # as the action file; `adb shell` would otherwise consume the rest of the file.
  adb -s "$TARGET_SERIAL" "$@" </dev/null
}

take_screenshot() {
  local label="$1"
  # Include PID so back-to-back capture runs (e.g. multiple locales) never reuse the same remote path.
  local remote_file="/sdcard/keymod_cap_${label}_${TS}_$$.png"
  local local_file="$OUT_DIR/${label}.png"
  adb_cmd shell screencap -p "$remote_file" >/dev/null
  adb_cmd pull "$remote_file" "$local_file" >/dev/null
  adb_cmd shell rm -f "$remote_file" >/dev/null
  echo "  screenshot: $local_file"
}

launch_app() {
  adb_cmd shell am start -n "$APP_WELCOME_ACTIVITY" >/dev/null
}

launch_welcome() {
  adb_cmd shell am force-stop com.openterface.keymod >/dev/null 2>&1 || true
  adb_cmd shell am start -n "$APP_WELCOME_ACTIVITY" --ez show_panel true >/dev/null
}

launch_km_basic_with_taps() {
  local submode="$1"
  launch_welcome
  sleep 2
  # Welcome "Start" button.
  tap_rel_coords 0.5 0.88
  sleep 2
  # Heuristic top tab row targets for KM Basic submodes.
  case "$submode" in
    keyboard)
      tap_rel_coords 0.18 0.10
      ;;
    touchpad)
      tap_rel_coords 0.50 0.10
      ;;
    numpad)
      tap_rel_coords 0.82 0.10
      ;;
    *)
      echo "Error: invalid KM Basic submode for fallback: $submode"
      return 1
      ;;
  esac
}

launch_km_basic() {
  local submode
  submode="$(echo "${1:-}" | tr '[:upper:]' '[:lower:]')"
  case "$submode" in
    keyboard|touchpad|numpad) ;;
    *)
      echo "Error: LAUNCH_KM_BASIC expects keyboard|touchpad|numpad, got '${1:-}'"
      return 1
      ;;
  esac

  adb_cmd shell am force-stop com.openterface.keymod >/dev/null 2>&1 || true

  local output
  output="$(
    adb_cmd shell am start -n "$APP_MAIN_ACTIVITY" \
      --es launch_mode keyboard_mouse \
      --es kb_mouse_initial_submode "$submode" 2>&1 || true
  )"

  if [[ "$output" == *"Error:"* || "$output" == *"Exception"* || "$output" == *"Permission Denial"* ]]; then
    if [[ "${ACTION_TEST_KM_USE_TAPS:-0}" == "1" ]]; then
      echo "  launch fallback: MainActivity shell launch blocked, using tap flow for $submode"
      launch_km_basic_with_taps "$submode"
      return 0
    fi
    echo "Error: failed to launch KM Basic via am start."
    echo "$output"
    echo "Hint: set ACTION_TEST_KM_USE_TAPS=1 to use Start + tab tap fallback."
    return 1
  fi
}

prepare_capture() {
  local tmp_dir tutorial_xml launch_xml remote_tutorial remote_launch
  tmp_dir="$(mktemp -d)"
  tutorial_xml="$tmp_dir/TutorialPrefs.xml"
  launch_xml="$tmp_dir/LaunchPanelPrefs.xml"
  remote_tutorial="/sdcard/TutorialPrefs.capture.xml"
  remote_launch="/sdcard/LaunchPanelPrefs.capture.xml"

  cat >"$tutorial_xml" <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="tutorial_shown_v2" value="true" />
</map>
EOF
  cat >"$launch_xml" <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="rememberChoice" value="false" />
</map>
EOF

  adb_cmd push "$tutorial_xml" "$remote_tutorial" >/dev/null
  adb_cmd push "$launch_xml" "$remote_launch" >/dev/null

  local prep_ok=1
  adb_cmd shell "run-as com.openterface.keymod sh -c 'mkdir -p shared_prefs && cat $remote_tutorial > shared_prefs/TutorialPrefs.xml'" >/dev/null 2>&1 || prep_ok=0
  adb_cmd shell "run-as com.openterface.keymod sh -c 'mkdir -p shared_prefs && cat $remote_launch > shared_prefs/LaunchPanelPrefs.xml'" >/dev/null 2>&1 || prep_ok=0
  adb_cmd shell rm -f "$remote_tutorial" "$remote_launch" >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"

  if [[ "$prep_ok" == "1" ]]; then
    echo "  capture prep: TutorialPrefs + LaunchPanelPrefs written via run-as"
  else
    echo "  capture prep: run-as write failed (likely non-debug build); continuing"
  fi
}

tap() {
  local x="$1"
  local y="$2"
  adb_cmd shell input tap "$x" "$y"
}

tap_rel_coords() {
  local xf="$1"
  local yf="$2"
  local x y
  x="$(awk -v w="$width" -v f="$xf" 'BEGIN { printf "%d", w * f + 0.5 }')"
  y="$(awk -v h="$height" -v f="$yf" 'BEGIN { printf "%d", h * f + 0.5 }')"
  tap "$x" "$y"
}

swipe() {
  local x1="$1"
  local y1="$2"
  local x2="$3"
  local y2="$4"
  local duration="${5:-250}"
  adb_cmd shell input swipe "$x1" "$y1" "$x2" "$y2" "$duration"
}

swipe_rel_coords() {
  local x1f="$1"
  local y1f="$2"
  local x2f="$3"
  local y2f="$4"
  local duration="${5:-250}"
  local x1 y1 x2 y2
  x1="$(awk -v w="$width" -v f="$x1f" 'BEGIN { printf "%d", w * f + 0.5 }')"
  y1="$(awk -v h="$height" -v f="$y1f" 'BEGIN { printf "%d", h * f + 0.5 }')"
  x2="$(awk -v w="$width" -v f="$x2f" 'BEGIN { printf "%d", w * f + 0.5 }')"
  y2="$(awk -v h="$height" -v f="$y2f" 'BEGIN { printf "%d", h * f + 0.5 }')"
  swipe "$x1" "$y1" "$x2" "$y2" "$duration"
}

set_orientation() {
  local mode
  mode="$(echo "${1:-}" | tr '[:upper:]' '[:lower:]')"
  adb_cmd shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
  if [[ "$mode" == "landscape" ]]; then
    adb_cmd shell settings put system user_rotation 1 >/dev/null 2>&1 || true
  else
    adb_cmd shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  fi
  sleep 2
}

key() {
  local code="$1"
  adb_cmd shell input keyevent "$code"
}

text() {
  local value="$*"
  local escaped="${value// /%s}"
  adb_cmd shell input text "$escaped"
}

start_logcat_capture() {
  adb_cmd logcat -c || true
  adb_cmd logcat >"$LOGCAT_FILE" 2>&1 &
  LOGCAT_PID="$!"
}

screen_size() {
  # Prefer Override size when set (matches layout / touch coordinates on many emulators).
  adb_cmd shell wm size | tr -d '\r' | awk -F': ' '
    /Override size/ { o = $2 }
    /Physical size/ { p = $2 }
    END {
      if (length(o) > 0) print o
      else if (length(p) > 0) print p
    }
  '
}

run_smoke() {
  local size width height center_x
  size="$(screen_size)"
  width="$(echo "$size" | awk -Fx '{print $1}')"
  height="$(echo "$size" | awk -Fx '{print $2}')"

  if [[ -z "$width" || -z "$height" ]]; then
    echo "Error: unable to parse device screen size."
    exit 1
  fi

  center_x=$((width / 2))

  echo "==> Running smoke actions on $TARGET_SERIAL (${width}x${height})"
  launch_app
  sleep 2
  take_screenshot "01_launched"

  echo "  action: vertical swipe down"
  swipe "$center_x" $((height * 75 / 100)) "$center_x" $((height * 25 / 100)) 350
  sleep 1
  take_screenshot "02_after_swipe_down"

  echo "  action: vertical swipe up"
  swipe "$center_x" $((height * 25 / 100)) "$center_x" $((height * 75 / 100)) 350
  sleep 1
  take_screenshot "03_after_swipe_up"

  echo "  action: back -> reopen app"
  key KEYCODE_BACK
  sleep 1
  launch_app
  sleep 2
  take_screenshot "04_reopened"
}

run_action_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "Error: action file not found: $file"
    exit 1
  fi

  local size width height
  size="$(screen_size)"
  width="$(echo "$size" | awk -Fx '{print $1}')"
  height="$(echo "$size" | awk -Fx '{print $2}')"
  if [[ -z "$width" || -z "$height" ]]; then
    echo "Error: unable to parse device screen size (wm size)."
    exit 1
  fi

  echo "==> Running actions from: $file (${width}x${height})"
  if [[ "${ACTION_TEST_SKIP_INITIAL_LAUNCH:-0}" != "1" ]]; then
    launch_app
    sleep 2
    take_screenshot "01_launched"
  fi

  local line cmd rest
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" ]] && continue
    [[ "${line:0:1}" == "#" ]] && continue

    cmd="$(echo "$line" | awk '{print toupper($1)}')"
    rest="${line#* }"
    [[ "$cmd" == "$line" ]] && rest=""

    if [[ "${ACTION_TEST_VERBOSE:-0}" == "1" ]]; then
      echo "  replay: $line" >&2
    fi

    case "$cmd" in
      WAIT)
        sleep "$rest"
        ;;
      TAP)
        tap $(echo "$rest" | awk '{print $1, $2}')
        ;;
      TAP_REL)
        tap_rel_coords $(echo "$rest" | awk '{print $1, $2}')
        ;;
      SWIPE)
        swipe $(echo "$rest" | awk '{print $1, $2, $3, $4, ($5==""?250:$5)}')
        ;;
      SWIPE_REL)
        swipe_rel_coords $(echo "$rest" | awk '{print $1, $2, $3, $4, ($5==""?250:$5)}')
        ;;
      ORIENTATION)
        set_orientation "$rest"
        ;;
      LAUNCH_WELCOME)
        launch_welcome
        ;;
      LAUNCH_KM_BASIC)
        launch_km_basic "$rest"
        ;;
      PREPARE_CAPTURE)
        prepare_capture
        ;;
      KEY)
        key "$rest"
        ;;
      TEXT)
        text "$rest"
        ;;
      SHELL)
        adb_cmd shell "$rest"
        ;;
      SCREENSHOT)
        take_screenshot "$rest"
        ;;
      *)
        echo "Warning: unknown command ignored: $line"
        ;;
    esac
  done <"$file"
}

echo "==> Target device: $TARGET_SERIAL"
echo "==> App repo: $APP_REPO"
echo "==> Output directory: $OUT_DIR"
start_logcat_capture

case "$MODE" in
  smoke)
    run_smoke
    ;;
  run)
    run_action_file "$ACTION_FILE"
    ;;
  *)
    echo "Error: unsupported mode '$MODE'"
    echo "Usage:"
    echo "  ./scripts/screenshot-capture/action_test_keymod.sh smoke [device_serial]"
    echo "  ./scripts/screenshot-capture/action_test_keymod.sh run <action_file> [device_serial]"
    echo "Env: ACTION_TEST_ARTIFACT_ROOT, ACTION_TEST_SKIP_INITIAL_LAUNCH=1, ACTION_TEST_VERBOSE=1"
    exit 1
    ;;
esac

echo "==> Done."
echo "Artifacts:"
echo "  - Screenshots/logs: $OUT_DIR"
echo "  - Logcat: $LOGCAT_FILE"
