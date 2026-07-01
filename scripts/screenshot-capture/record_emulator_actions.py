#!/usr/bin/env python3
"""
Record touch gestures from an Android emulator (or device) via adb getevent,
emit a replay file for action_test_keymod.sh.

Pointer events must come from the emulator UI (mouse / finger). **`adb shell input tap` does not produce getevent lines on many emulators**, so you will see no `[raw]` / `[record]` lines if you only use that.

Replay (from testing repo root, after opening the app to the right screen):
  ACTION_TEST_SKIP_INITIAL_LAUNCH=1 \\
    ./scripts/action_test_keymod.sh run scripts/actions/my_flow.actions emulator-5554

While recording, type on this terminal:
  shot LABEL     insert: SCREENSHOT LABEL
Stop: Ctrl+C

Each recorded step is echoed to stderr by default; use --quiet to disable.
A JSONL timeline (--timeline-out) logs the same steps with waits and optional device timestamps.

See scripts/BEHAVIOR_RECORDING.md for the full workflow.
"""

from __future__ import annotations

import argparse
import json
import re
import signal
import subprocess
import sys
import threading
import time
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Optional, Tuple

TRACKING_INACTIVE = 0xFFFFFFFF

EV_SYN = "EV_SYN"
EV_ABS = "EV_ABS"
SYN_REPORT = "SYN_REPORT"
ABS_MT_SLOT = "ABS_MT_SLOT"
ABS_MT_TRACKING_ID = "ABS_MT_TRACKING_ID"
ABS_MT_POSITION_X = "ABS_MT_POSITION_X"
ABS_MT_POSITION_Y = "ABS_MT_POSITION_Y"


def parse_hex_int(s: str) -> int:
    s = s.strip()
    return int(s, 16)


def parse_event_value(s: str) -> int:
    """Parse getevent value token (hex, 0x hex, or signed decimal)."""
    s = s.strip()
    if s.startswith("0x") or s.startswith("0X"):
        return int(s, 16)
    if s.startswith("-") or (s.isdigit() and len(s) <= 10):
        return int(s, 10)
    return int(s, 16)


def adb_wm_size(serial: str) -> Tuple[int, int]:
    p = subprocess.run(
        ["adb", "-s", serial, "shell", "wm", "size"],
        capture_output=True,
        text=True,
        timeout=15,
        stdin=subprocess.DEVNULL,
    )
    if p.returncode != 0:
        raise RuntimeError(f"adb wm size failed: {p.stderr or p.stdout}")
    text = (p.stdout or "").replace("\r", "")
    override, physical = None, None
    for line in text.splitlines():
        if "Override size:" in line:
            override = line.split("Override size:", 1)[1].strip()
        elif "Physical size:" in line:
            physical = line.split("Physical size:", 1)[1].strip()
    size = override or physical
    if not size or "x" not in size:
        raise RuntimeError(f"Could not parse wm size from:\n{text}")
    w, _, h = size.partition("x")
    return int(w.strip()), int(h.strip())


LINE_LABELED = re.compile(
    r"^(?:\[\s*([0-9]+\.[0-9]+)\]\s+)?"
    r"(/dev/input/event\d+):\s+"
    r"(EV_\w+)\s+"
    r"(ABS_\w+|SYN_\w+)\s+"
    r"(.+?)\s*$"
)

LINE_HEX = re.compile(
    r"^(?:\[\s*([0-9]+\.[0-9]+)\]\s+)?"
    r"(/dev/input/event\d+):\s+"
    r"([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\s*$"
)


def decode_hex_event(
    ts: Optional[float], dev: str, typ: int, cod: int, val: int
) -> Optional[Tuple[Optional[float], str, str, str, int]]:
    if typ == 0x00 and cod == 0:
        return ts, dev, EV_SYN, SYN_REPORT, val
    if typ == 0x03:
        code_map = {
            0x2F: ABS_MT_SLOT,
            0x35: ABS_MT_POSITION_X,
            0x36: ABS_MT_POSITION_Y,
            0x39: ABS_MT_TRACKING_ID,
        }
        name = code_map.get(cod)
        if name is None:
            return None
        return ts, dev, EV_ABS, name, val
    return None


def parse_getevent_line(line: str) -> Optional[Tuple[Optional[float], str, str, str, int]]:
    line = line.strip()
    if not line or line.startswith("add device") or line.startswith("name:"):
        return None
    m = LINE_LABELED.match(line)
    if m:
        ts = float(m.group(1)) if m.group(1) else None
        return (
            ts,
            m.group(2),
            m.group(3),
            m.group(4),
            parse_event_value(m.group(5)),
        )
    m = LINE_HEX.match(line)
    if m:
        ts = float(m.group(1)) if m.group(1) else None
        typ, cod, val = (
            parse_hex_int(m.group(3)),
            parse_hex_int(m.group(4)),
            parse_hex_int(m.group(5)),
        )
        return decode_hex_event(ts, m.group(2), typ, cod, val)
    return None


def classify_gesture(
    x1: int, y1: int, x2: int, y2: int, tap_px: int
) -> Tuple[str, Tuple[int, ...]]:
    dist = ((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5
    if dist <= tap_px:
        return "tap", (x2, y2)
    return "swipe", (x1, y1, x2, y2)


def fmt_rel(w: int, h: int, kind: str, coords: Tuple[int, ...], nd: int = 4) -> str:
    f = f"{{:.{nd}f}}"
    if kind == "tap":
        x, y = coords[0], coords[1]
        return f"TAP_REL {f.format(x / w)} {f.format(y / h)}"
    x1, y1, x2, y2 = coords
    return (
        f"SWIPE_REL {f.format(x1 / w)} {f.format(y1 / h)} "
        f"{f.format(x2 / w)} {f.format(y2 / h)} 300"
    )


def fmt_abs(kind: str, coords: Tuple[int, ...]) -> str:
    if kind == "tap":
        x, y = coords[0], coords[1]
        return f"TAP {x} {y}"
    x1, y1, x2, y2 = coords
    return f"SWIPE {x1} {y1} {x2} {y2} 300"


class _SlotState:
    __slots__ = ("finger_down", "down_x", "down_y", "last_x", "last_y")

    def __init__(self) -> None:
        self.finger_down = False
        self.down_x = 0
        self.down_y = 0
        self.last_x = 0
        self.last_y = 0


class PerDeviceTouch:
    """
    Multitouch slot protocol: ABS_MT_SLOT selects slot; ABS_* updates apply to that slot
    until SYN_REPORT, then we detect finger-up per slot.
    """

    def __init__(self) -> None:
        self.active_slot: int = 0
        self.pending: Dict[int, Dict[str, int]] = defaultdict(dict)
        self.slot_state: Dict[int, _SlotState] = defaultdict(_SlotState)

    def feed(self, code: str, val: int) -> None:
        if code == ABS_MT_SLOT:
            self.active_slot = val
            return
        self.pending[self.active_slot][code] = val

    def syn(self) -> List[Tuple[int, int, int, int]]:
        completed: List[Tuple[int, int, int, int]] = []
        for slot, ch in list(self.pending.items()):
            st = self.slot_state[slot]
            if ABS_MT_POSITION_X in ch:
                st.last_x = ch[ABS_MT_POSITION_X]
            if ABS_MT_POSITION_Y in ch:
                st.last_y = ch[ABS_MT_POSITION_Y]

            if ABS_MT_TRACKING_ID not in ch:
                continue

            tid = ch[ABS_MT_TRACKING_ID]
            if tid == TRACKING_INACTIVE or tid == -1 or tid < 0:
                if st.finger_down:
                    completed.append((st.down_x, st.down_y, st.last_x, st.last_y))
                st.finger_down = False
            elif 0 < tid < 0x7FFFFFFF:
                if not st.finger_down:
                    st.down_x, st.down_y = st.last_x, st.last_y
                st.finger_down = True

        self.pending.clear()
        return completed


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", "-s", default="emulator-5554")
    ap.add_argument("--out", "-o", required=True, help="Output .actions path")
    ap.add_argument(
        "--emit-rel",
        action="store_true",
        help="Emit TAP_REL / SWIPE_REL from wm size at record start",
    )
    ap.add_argument("--tap-threshold-px", type=int, default=35)
    ap.add_argument("--wait-min", type=float, default=0.05)
    ap.add_argument("--wait-max", type=float, default=3.0)
    ap.add_argument(
        "--device-timing",
        action="store_true",
        help="Use getevent timestamps for WAIT between gestures (run getevent -tlt)",
    )
    ap.add_argument(
        "--timeline-out",
        metavar="PATH",
        help="Append JSONL timeline (default: <out-stem>.timeline.jsonl next to .actions)",
    )
    ap.add_argument(
        "--no-timeline",
        action="store_true",
        help="Do not write timeline JSONL",
    )
    ap.add_argument(
        "--quiet",
        action="store_true",
        help="Do not print each recorded line to stderr",
    )
    ap.add_argument(
        "--no-mirror-touch",
        action="store_true",
        help="Do not print raw getevent lines that look like touch traffic ([raw] …)",
    )
    ap.add_argument(
        "--evdev",
        metavar="PATH",
        default="",
        help="Pass a single device to getevent (e.g. /dev/input/event1). Default: all devices.",
    )
    ap.add_argument(
        "--exec-out-getevent",
        action="store_true",
        help="Use adb exec-out getevent instead of adb shell getevent (older behavior)",
    )
    ap.add_argument("--raw-out", help="Append raw getevent lines for debugging")
    ap.add_argument(
        "--debug",
        action="store_true",
        help="Print getevent lines that are skipped (wrong device / unparsed) to stderr",
    )
    ap.add_argument(
        "--touch-dev-regex",
        default=(
            r"virtio_input_multi_touch|goldfish|qemu|Virtual|touchscreen|TouchScreen|"
            r"synaptics|sec_touch|mtouch|evdev"
        ),
        help="Regex on device path or its registered name",
    )
    ap.add_argument(
        "--only-dev",
        metavar="PATH",
        help="Only record this input path (e.g. /dev/input/event1) to avoid duplicate MT nodes",
    )
    args = ap.parse_args()
    mirror_touch = not args.no_mirror_touch
    touch_re = re.compile(args.touch_dev_regex, re.I)
    only_dev: Optional[str] = args.only_dev

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    w, h = adb_wm_size(args.serial)

    header = [
        f"# Recorded for adb serial={args.serial}",
        f"# wm size at record start: {w}x{h}",
        f"# emit_rel={args.emit_rel} tap_threshold_px={args.tap_threshold_px}",
        f"# device_timing={args.device_timing} (WAIT from getevent clock if true)",
        "# stdin: shot <label> -> SCREENSHOT",
        "",
    ]
    actions: List[str] = list(header)

    timeline_path = (
        None
        if args.no_timeline
        else (
            Path(args.timeline_out)
            if args.timeline_out
            else out_path.with_name(out_path.stem + ".timeline.jsonl")
        )
    )
    timeline_fp = open(timeline_path, "w", encoding="utf-8") if timeline_path else None
    timeline_seq = 0

    host_lock = threading.Lock()
    last_emit = time.monotonic()
    last_device_gesture_end: Optional[float] = None

    def append_action(
        line: str,
        device_ts: Optional[float] = None,
    ) -> None:
        """Append WAIT (if needed) + line to actions; mirror to timeline + stderr."""
        nonlocal last_emit, last_device_gesture_end, timeline_seq
        wait_sec: Optional[float] = None
        now = time.monotonic()

        if actions and actions[-1].strip() and not actions[-1].startswith("#"):
            if args.device_timing and device_ts is not None:
                if last_device_gesture_end is not None:
                    wait_sec = max(
                        args.wait_min,
                        min(args.wait_max, device_ts - last_device_gesture_end),
                    )
            else:
                wait_sec = max(args.wait_min, min(args.wait_max, now - last_emit))

            if wait_sec is not None and wait_sec >= args.wait_min:
                wline = f"WAIT {wait_sec:.3f}"
                actions.append(wline)
                if not args.quiet:
                    print(f"[record] {wline}", file=sys.stderr, flush=True)
                if timeline_fp:
                    timeline_seq += 1
                    timeline_fp.write(
                        json.dumps(
                            {
                                "seq": timeline_seq,
                                "kind": "wait",
                                "wait_sec": wait_sec,
                                "device_ts": device_ts,
                            },
                            ensure_ascii=False,
                        )
                        + "\n"
                    )
                    timeline_fp.flush()

        actions.append(line)
        last_emit = now
        if device_ts is not None:
            last_device_gesture_end = device_ts
        if not args.quiet:
            print(f"[record] {line}", file=sys.stderr, flush=True)
        if timeline_fp:
            timeline_seq += 1
            timeline_fp.write(
                json.dumps(
                    {
                        "seq": timeline_seq,
                        "kind": "command",
                        "line": line,
                        "device_ts_end": device_ts,
                        "host_mono": now,
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
            timeline_fp.flush()

    def stdin_worker() -> None:
        for line in sys.stdin:
            line = line.strip()
            if line.lower().startswith("shot "):
                label = line[5:].strip()
                if label:
                    with host_lock:
                        append_action(f"SCREENSHOT {label}", device_ts=None)

    threading.Thread(target=stdin_worker, daemon=True).start()

    devices: Dict[str, str] = {}
    pending_dev: Optional[str] = None

    def allowed(dev: str) -> bool:
        if only_dev is not None and dev != only_dev:
            return False
        if args.evdev and dev == args.evdev:
            return True
        name = devices.get(dev, "")
        if touch_re.search(dev) or touch_re.search(name):
            return True
        # Emulators: primary touch is usually event1; name may be briefly empty after add device.
        # Only event1 here — allowing event2..event11 with empty name duplicates one tap many times.
        if args.serial.startswith("emulator") and dev == "/dev/input/event1":
            if not name:
                return True
            if "touch" in name.lower() or "mtouch" in name.lower():
                return True
        return False

    per_dev: Dict[str, PerDeviceTouch] = {}

    if args.exec_out_getevent:
        ge_cmd = ["adb", "-s", args.serial, "exec-out", "getevent"]
        ge_cmd += ["-tlt"] if args.device_timing else ["-lt"]
    else:
        ge_cmd = ["adb", "-s", args.serial, "shell", "getevent"]
        ge_cmd += ["-tlt"] if args.device_timing else ["-lt"]
    if args.evdev:
        ge_cmd.append(args.evdev)
    proc = subprocess.Popen(
        ge_cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        text=True,
        bufsize=1,
    )

    raw_fp = open(args.raw_out, "w", encoding="utf-8") if args.raw_out else None

    stop = threading.Event()

    def shutdown(*_a) -> None:
        stop.set()
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proc.kill()

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    if not args.quiet:
        print(
            "[record] Listening — use the **mouse on the emulator window** (not `adb input`).",
            file=sys.stderr,
            flush=True,
        )
        print(
            f"[record] wm size {w}x{h}  serial={args.serial}  cmd={' '.join(ge_cmd)}",
            file=sys.stderr,
            flush=True,
        )
        print(
            "[record] A line appears when you **lift** your finger (tap/swipe end). Ctrl+C stops.",
            file=sys.stderr,
            flush=True,
        )
        if mirror_touch:
            print(
                "[record] Raw touch-related getevent lines print as [raw] … (disable: --no-mirror-touch).",
                file=sys.stderr,
                flush=True,
            )

    assert proc.stdout is not None
    last_hint = time.monotonic()
    gestures_seen = 0
    try:
        for raw_line in proc.stdout:
            if stop.is_set():
                break
            line = raw_line.replace("\r", "").rstrip("\n")
            if mirror_touch and not args.quiet and (
                "ABS_MT" in line or ("EV_KEY" in line and "BTN" in line)
            ):
                print(f"[raw] {line[:220]}", file=sys.stderr, flush=True)
            if raw_fp:
                raw_fp.write(line + "\n")

            now_loop = time.monotonic()
            if (
                not args.quiet
                and gestures_seen == 0
                and now_loop - last_hint > 12.0
                and "/dev/input/event" in line
            ):
                last_hint = now_loop
                print(
                    "[record] Still no gesture — click inside the emulator screen, then release. "
                    "Try: python3 -u scripts/record_emulator_actions.py …  or  --debug",
                    file=sys.stderr,
                    flush=True,
                )

            m_add = re.match(r"add device \d+:\s+(\S+)", line)
            if m_add:
                pending_dev = m_add.group(1)
                continue
            if pending_dev and "name:" in line:
                m = re.search(r'"([^"]*)"', line)
                if m:
                    devices[pending_dev] = m.group(1)
                pending_dev = None
                continue

            parsed = parse_getevent_line(line)
            if not parsed:
                if args.debug and "/dev/input/event" in line and not line.startswith(
                    "add device"
                ):
                    print(f"[debug] unparsed: {line[:200]}", file=sys.stderr, flush=True)
                continue
            ts, dev, ev_type, code, val = parsed
            if not allowed(dev):
                if args.debug and ev_type in (EV_ABS, EV_SYN):
                    nm = devices.get(dev, "?")
                    print(
                        f"[debug] skip dev={dev} name={nm!r} {ev_type} {code}",
                        file=sys.stderr,
                        flush=True,
                    )
                continue

            if dev not in per_dev:
                per_dev[dev] = PerDeviceTouch()
            dtouch = per_dev[dev]

            if ev_type == EV_ABS:
                dtouch.feed(code, val)
            elif ev_type == EV_SYN and code == SYN_REPORT:
                for g in dtouch.syn():
                    x1, y1, x2, y2 = g
                    kind, coords = classify_gesture(
                        x1, y1, x2, y2, args.tap_threshold_px
                    )
                    dev_ts = ts if args.device_timing else None
                    with host_lock:
                        if args.emit_rel:
                            append_action(
                                fmt_rel(w, h, kind, coords), device_ts=dev_ts
                            )
                        else:
                            append_action(fmt_abs(kind, coords), device_ts=dev_ts)
                    gestures_seen += 1
    finally:
        shutdown()
        if raw_fp:
            raw_fp.close()
        if timeline_fp:
            timeline_fp.close()

    out_path.write_text("\n".join(actions) + "\n", encoding="utf-8")
    meta = {
        "serial": args.serial,
        "wm_w": w,
        "wm_h": h,
        "emit_rel": args.emit_rel,
        "device_timing": args.device_timing,
        "lines": len(actions),
        "timeline": str(timeline_path) if timeline_path else None,
    }
    out_path.with_suffix(out_path.suffix + ".meta.json").write_text(
        json.dumps(meta, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote {out_path} ({len(actions)} lines)", file=sys.stderr, flush=True)
    if timeline_path:
        print(f"Wrote timeline {timeline_path}", file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
