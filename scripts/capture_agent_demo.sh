#!/usr/bin/env bash
# Launch Agent mode for marketing screenshots / screen recording.
# Usage:
#   ./scripts/capture_agent_demo.sh [hero_mixed|cli_only|macro_only] [--auto-play] [--auto-approve] [--skip-gate]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PKG="com.openterface.keymod"
ACTIVITY="${PKG}/.MainActivity"

DEMO_SCRIPT="${1:-hero_mixed}"
AUTO_PLAY=false
AUTO_APPROVE=false
SKIP_GATE=false
PAUSE_AT="act"

shift || true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --auto-play) AUTO_PLAY=true ;;
    --auto-approve) AUTO_APPROVE=true; PAUSE_AT="none" ;;
    --skip-gate) SKIP_GATE=true ;;
    --pause-at-plan) PAUSE_AT="plan" ;;
    *) echo "Unknown flag: $1" >&2; exit 1 ;;
  esac
  shift
done

adb shell am force-stop "${PKG}" >/dev/null 2>&1 || true
adb shell am start -n "${ACTIVITY}" \
  --es launch_mode agent \
  --es agent_demo_script "${DEMO_SCRIPT}" \
  --es agent_demo_pause_at "${PAUSE_AT}" \
  $( [[ "${AUTO_PLAY}" == true ]] && echo --ez agent_demo_auto_play true ) \
  $( [[ "${AUTO_APPROVE}" == true ]] && echo --ez agent_demo_auto_approve true ) \
  $( [[ "${SKIP_GATE}" == true ]] && echo --ez agent_demo_skip_gate true )

echo "Agent demo launched: script=${DEMO_SCRIPT} auto_play=${AUTO_PLAY} auto_approve=${AUTO_APPROVE} skip_gate=${SKIP_GATE} pause_at=${PAUSE_AT}"
