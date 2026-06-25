#!/usr/bin/env bash
# Shared helpers for locale-loop screenshot capture scripts.
# Source from capture_*_i18n_screenshots.sh wrappers.

i18n_capture_resolve_app_repo() {
  local root_dir="$1"
  if [[ -n "${KEYCMD_APP_REPO:-}" ]]; then
    echo "$KEYCMD_APP_REPO"
  elif [[ -n "${KEYMOD_APP_REPO:-}" ]]; then
    echo "$KEYMOD_APP_REPO"
  else
    echo "$root_dir"
  fi
}

i18n_capture_setup_path() {
  local android_home_default="${1:-/opt/homebrew/share/android-commandlinetools}"
  export ANDROID_HOME="${ANDROID_HOME:-$android_home_default}"
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
  export PATH="$ANDROID_HOME/platform-tools:/opt/homebrew/bin:/usr/local/bin:$PATH"
}

i18n_capture_check_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb not found in PATH." >&2
    return 1
  fi
}

i18n_capture_check_device() {
  local serial="$1"
  if ! adb devices | awk -v target="$serial" 'NR>1 && $2=="device" && $1==target { found=1 } END { exit(found?0:1) }'; then
    echo "Error: device '$serial' is not in 'device' state." >&2
    adb devices >&2
    return 1
  fi
}

i18n_capture_check_api33() {
  local serial="$1"
  local sdk
  sdk="$(adb -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  if [[ -z "$sdk" || "$sdk" -lt 33 ]]; then
    echo "Error: per-app locales require API 33+ (got SDK=${sdk:-unknown}). Use a newer system image." >&2
    return 1
  fi
}

i18n_capture_locale_to_tag() {
  case "$1" in
    en) echo "en-US" ;;
    zh-CN) echo "zh-CN" ;;
    zh-TW) echo "zh-TW" ;;
    zh-HK) echo "zh-HK" ;;
    es) echo "es-ES" ;;
    fr) echo "fr-FR" ;;
    de) echo "de-DE" ;;
    ja) echo "ja-JP" ;;
    ko) echo "ko-KR" ;;
    it) echo "it-IT" ;;
    ru) echo "ru-RU" ;;
    pt-BR) echo "pt-BR" ;;
    *)
      echo "Error: unknown locale key: $1" >&2
      return 1
      ;;
  esac
}

i18n_capture_default_locales() {
  echo "en zh-CN zh-TW zh-HK es fr de ja"
}

i18n_capture_parse_locales() {
  local auto_locales="$1"
  local locales_list="$2"
  if [[ "$auto_locales" == 1 ]]; then
    # shellcheck disable=SC2206
    LOCALES=($(i18n_capture_default_locales))
  else
    local _loc="${locales_list//[[:space:]]/}"
    if [[ -z "$_loc" ]]; then
      echo "Error: --locales is empty." >&2
      return 1
    fi
    IFS=',' read -r -a LOCALES <<<"$_loc"
  fi
}

i18n_capture_adb_serial() {
  adb -s "$I18N_CAPTURE_SERIAL" "$@" </dev/null
}

i18n_capture_launch_launch_panel() {
  i18n_capture_adb_serial shell am start -n "$I18N_CAPTURE_MAIN_ACTIVITY" --ez show_panel true >/dev/null
}

i18n_capture_set_app_locale() {
  local tag="$1"
  i18n_capture_adb_serial shell cmd locale set-app-locales "$I18N_CAPTURE_PACKAGE" --locales "$tag" >/dev/null
}

i18n_capture_reset_app_locales() {
  i18n_capture_adb_serial shell cmd locale set-app-locales "$I18N_CAPTURE_PACKAGE" --locales "[]" >/dev/null 2>&1 || true
}

i18n_capture_convert_png_to_jpeg() {
  local out_dir="$1"
  local convert_jpeg="$2"
  if [[ "$convert_jpeg" != 1 ]]; then
    return 0
  fi
  if ! command -v sips >/dev/null 2>&1; then
    echo "  (install sips on macOS or use ImageMagick to produce .jpg for README parity)"
    return 0
  fi
  echo "  converting PNG -> JPEG in $out_dir"
  shopt -s nullglob
  local png base
  for png in "$out_dir"/*.png; do
    base="$(basename "$png" .png)"
    sips -s format jpeg "$png" --out "$out_dir/${base}.jpg" >/dev/null
  done
  shopt -u nullglob
}

i18n_capture_run_locale_loop() {
  local artifact_subdir="$1"
  local action_runner="$2"
  local action_file="$3"
  local root_dir="$4"

  local locale tag out_dir
  for locale in "${LOCALES[@]}"; do
    locale="${locale//[[:space:]]/}"
    [[ -z "$locale" ]] && continue
    tag="$(i18n_capture_locale_to_tag "$locale")" || return 1
    out_dir="$root_dir/artifacts/$artifact_subdir/$locale"
    mkdir -p "$out_dir"

    echo ""
    echo "==> Locale $locale (set-app-locales $tag) -> $out_dir"

    i18n_capture_set_app_locale "$tag"
    i18n_capture_adb_serial shell am force-stop "$I18N_CAPTURE_PACKAGE" >/dev/null
    i18n_capture_launch_launch_panel
    sleep 2

    export ACTION_TEST_ARTIFACT_ROOT="$out_dir"
    export ACTION_TEST_SKIP_INITIAL_LAUNCH=1
    bash "$action_runner" run "$action_file" "$I18N_CAPTURE_SERIAL"
    unset ACTION_TEST_ARTIFACT_ROOT ACTION_TEST_SKIP_INITIAL_LAUNCH

    i18n_capture_convert_png_to_jpeg "$out_dir" "${I18N_CAPTURE_CONVERT_JPEG:-1}"
  done

  i18n_capture_reset_app_locales
}
