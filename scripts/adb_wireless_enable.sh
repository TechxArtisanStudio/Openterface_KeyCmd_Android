#!/usr/bin/env bash

set -euo pipefail

# Enable wireless (over-IP) ADB debugging on an already-USB-connected Android device.
#
# Usage:
#   ./scripts/adb_wireless_enable.sh
#   ./scripts/adb_wireless_enable.sh <device_serial_or_ip>
#   ./scripts/adb_wireless_enable.sh --check          # query current ADB transport
#
# Steps:
#   1. Detect a device connected via USB or accept a serial as argument.
#   2. On Android 11+ (API 30+): attempt to use `adb pair` + `adb connect`
#      (pairing code is still manual on first connect).
#   3. On older Android / universal fallback: `adb tcpip 5555` then print the
#      connect command.
#
# After running, you can unplug USB and connect wirelessly via:
#   adb connect <device_ip>:5555

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ── PATH helpers (mirrors other scripts in this repo) ──────────────────────
ANDROID_HOME_DEFAULT="/opt/homebrew/share/android-commandlinetools"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_HOME_DEFAULT}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# ── Colour helpers ─────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Colour

info()  { echo -e "${CYAN}INFO:${NC}  $*"; }
ok()    { echo -e "${GREEN}OK:${NC}   $*"; }
warn()  { echo -e "${YELLOW}WARN:${NC} $*"; }
err()   { echo -e "${RED}ERROR:${NC} $*"; }

# ── Prerequisites ──────────────────────────────────────────────────────────

require_adb() {
  if ! command -v adb &>/dev/null; then
    err "adb not found in PATH."
    echo "  Install Android platform-tools or set ANDROID_HOME/platform-tools."
    exit 1
  fi
}

require_device() {
  if [[ -n "${TARGET_SERIAL:-}" ]]; then
    return 0
  fi
  # Find a USB-connected device (not emulator)
  local serial
  serial="$(adb devices -l | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }')"
  if [[ -z "$serial" ]]; then
    err "No USB-connected Android device found."
    echo "  Connect your phone via USB, enable USB debugging, and accept the RSA prompt."
    adb devices
    exit 1
  fi
  TARGET_SERIAL="$serial"
}

get_device_ip() {
  # Try multiple methods to find the device's Wi-Fi IP address.
  local ip=""
  ip="$(adb -s "$TARGET_SERIAL" shell ip route 2>/dev/null | awk '/src/ { print $NF; exit }')"
  if [[ -z "$ip" ]]; then
    ip="$(adb -s "$TARGET_SERIAL" shell ifconfig wlan0 2>/dev/null \
          | awk '/inet / { gsub(/addr:/,""); print $2; exit }')"
  fi
  if [[ -z "$ip" ]]; then
    ip="$(adb -s "$TARGET_SERIAL" shell getprop dhcp.wlan0.ipaddress 2>/dev/null)"
  fi
  echo "$ip"
}

get_api_level() {
  adb -s "$TARGET_SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '[:space:]'
}

# ── Actions ────────────────────────────────────────────────────────────────

cmd_check() {
  require_adb

  # Find a USB-connected device (not emulator) for property queries
  local check_serial
  check_serial="$(adb devices -l | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ { print $1; exit }')"

  echo "━━━ ADB transport status ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  adb devices -l
  echo

  # Show ADB port property if a device is available
  if [[ -n "$check_serial" ]]; then
    local port=""
    port="$(adb -s "$check_serial" shell getprop service.adb.tcp.port 2>/dev/null | tr -d '[:space:]' || true)"
    if [[ -n "$port" ]]; then
      echo "Device: $check_serial"
      echo "service.adb.tcp.port = $port"
      if [[ "$port" == "5555" ]]; then
        ok "ADB-over-TCP is active on port 5555."
      else
        info "ADB-over-TCP port: $port"
      fi
    else
      info "ADB-over-TCP is not active (USB transport only)."
    fi
  else
    info "No USB device found to query ADB-over-TCP status."
  fi

  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

cmd_enable() {
  require_adb
  require_device

  ok "Target device: $TARGET_SERIAL"
  local api
  api="$(get_api_level)"
  info "Android API level: ${api:-unknown}"

  local ip
  ip="$(get_device_ip)"
  if [[ -z "$ip" ]]; then
    warn "Could not auto-detect device IP address."
  fi

  if [[ -n "$api" && "$api" -ge 30 ]]; then
    # ── Android 11+ — Wireless debugging via Pairing UI ──────────
    info "Device is Android 11+ (API $api)."
    echo ""
    echo "  ${YELLOW}On your phone:${NC}"
    echo "    1. Go to  Settings → Developer options → Wireless debugging"
    echo "    2. Tap 'Pair device with pairing code'"
    echo "    3. Note the IP:port and 6-digit code shown."
    echo ""
    echo "  Then run:"
    echo "    adb pair ${ip:-<device_ip>}:<pairing_port>"
    echo "    adb connect ${ip:-<device_ip>}:<connection_port>"
    echo ""
    echo "  (The pairing port and connection port are different — use what the"
    echo "   phone shows on each screen.)"
    echo ""

    # On API 30+, `adb tcpip` still works but requires USB. Give user the option.
    read -r -p "Use legacy 'adb tcpip 5555' instead? [y/N] " reply
    if [[ "$reply" =~ ^[Yy]$ ]]; then
      info "Switching device ADB to TCP mode on port 5555..."
      adb -s "$TARGET_SERIAL" tcpip 5555
      ok "Device is now listening on TCP port 5555."
      echo ""
      echo "  You can now unplug USB and connect via:"
      echo "    ${CYAN}adb connect ${ip:-<device_ip>}:5555${NC}"
      echo ""
      echo "  To verify:  adb devices"
      echo "  To revert:  adb -s ${ip:-<device_ip>}:5555 usb"
    else
      info "OK — use the Wireless debugging UI method described above."
    fi
  else
    # ── Android 10 and below — classic tcpip ────────────────────
    if [[ -z "$ip" ]]; then
      err "Cannot detect device IP (required for wireless connection)."
      echo "  Try: adb -s $TARGET_SERIAL shell ip route"
      exit 1
    fi

    info "Switching device ADB to TCP mode on port 5555..."
    adb -s "$TARGET_SERIAL" tcpip 5555
    ok "Device is now listening on TCP port 5555."
    echo ""
    echo "  ${GREEN}Unplug the USB cable now.${NC}"
    echo ""
    echo "  Connect wirelessly:"
    echo "    ${CYAN}adb connect ${ip}:5555${NC}"
    echo ""
    echo "  Verify:       adb devices"
    echo "  Revert to USB: adb -s ${ip}:5555 usb"
    echo "  Disconnect:   adb disconnect ${ip}:5555"
  fi
}

# ── Main ───────────────────────────────────────────────────────────────────

case "${1:-}" in
  -h|--help)
    echo "Usage: $0 [--check | <device_serial>]"
    echo ""
    echo "  (no args)    Detect USB device and enable ADB-over-IP."
    echo "  --check      Show current ADB transport status."
    echo "  <serial>     Use specified device serial/IP instead of auto-detect."
    exit 0
    ;;
  --check)
    cmd_check
    ;;
  *)
    TARGET_SERIAL="${1:-}"
    cmd_enable
    ;;
esac