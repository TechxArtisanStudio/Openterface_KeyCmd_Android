# Behavior recorder and replay

## Quick start (copy-paste)

Run from the **app repo root** (`Openterface_KeyCmd_Android/`).

**1 — Record** (leave this terminal open; use the **mouse on the emulator window** to tap/swipe):

```bash
cd /path/to/Openterface_KeyCmd_Android
python3 -u scripts/screenshot-capture/record_emulator_actions.py \
  --serial emulator-5554 \
  --emit-rel \
  --out scripts/screenshot-capture/actions/my_flow.actions
```

Right away you should see **`[record] Listening…`** lines.  
**Important:** a gesture is logged only when you **release** the mouse button (finger up), not on every pixel while dragging.

If **`[record] TAP_…` never appears** but you see **`[raw] …`** lines when you tap, the kernel is emitting touch data but the **device node** may not be `event1`. Try:

```bash
python3 -u scripts/screenshot-capture/record_emulator_actions.py --serial emulator-5554 --emit-rel \
  --evdev /dev/input/event2 \
  --out scripts/screenshot-capture/actions/my_flow.actions
```

(Use the `/dev/input/eventN` you see in `[raw]`.)  
If there are **no `[raw]` lines at all** when you click the emulator, clicks are not reaching `getevent` (focus the emulator window, not the host OS / another monitor).

Other flags: **`--debug`** (skip reasons), **`--exec-out-getevent`** (older transport), **`--no-mirror-touch`** (hide `[raw]`). Stop with **Ctrl+C**.  
That creates **`scripts/screenshot-capture/actions/my_flow.actions`** (the script you will replay).

**2 — Replay** (open Openterface KM to the **same starting screen** you used when recording, then):

```bash
cd /path/to/Openterface_KeyCmd_Android
export ACTION_TEST_SKIP_INITIAL_LAUNCH=1
export ACTION_TEST_VERBOSE=1
./scripts/screenshot-capture/action_test_keymod.sh run scripts/screenshot-capture/actions/my_flow.actions emulator-5554
```

You should see `replay: WAIT …` / `replay: TAP_REL …` and the emulator repeats your taps.

**If you have two devices** (phone + emulator), always pass **`emulator-5554`** (or your serial) on both commands.

---

## What gets written

| File | Purpose |
|------|---------|
| `scripts/screenshot-capture/actions/my_flow.actions` | Plain text, **one command per line** — same format as other automation. Replay with `action_test_keymod.sh run …`. |
| `scripts/screenshot-capture/actions/my_flow.actions.meta.json` | Serial, `wm` size, flags, line count, timeline path. |
| `scripts/screenshot-capture/actions/my_flow.timeline.jsonl` | **Optional audit log**: each `WAIT` and each `TAP_REL` / `SCREENSHOT` / … as JSON (seq, kind, times). |

While you record, the script prints each step to **stderr** as `[record] WAIT 0.120` then `[record] TAP_REL …` so your terminal acts as a live log. Use `--quiet` to turn that off.

## Record

1. Start the Android emulator (`adb devices` should list it, e.g. `emulator-5554`).
2. Install Openterface KM and open the screen where you want to start the flow (same as replay).
3. From the **app repo** root:

```bash
python3 scripts/screenshot-capture/record_emulator_actions.py \
  --serial emulator-5554 \
  --emit-rel \
  --device-timing \
  --out scripts/screenshot-capture/actions/my_flow.actions
```

- **`--emit-rel`**: writes `TAP_REL` / `SWIPE_REL` so the same file works after `wm size` changes.
- **`--device-timing`**: runs `getevent -tlt` and sets **`WAIT`** from **on-device** timestamps between finger lifts (clamped by `--wait-min` / `--wait-max`). Omit it to use **host** time between gestures instead.
- **`--no-timeline`**: skip the JSONL timeline.
- **`--timeline-out PATH`**: custom timeline path (default: `my_flow.timeline.jsonl` next to `my_flow.actions`).

4. Perform touches on the **emulator window** (mouse or trackpad). Do not rely on `adb shell input tap` for recording; many emulator images do not mirror those events into `getevent`.

5. Optional: in the **same terminal** where the recorder runs, type:

```text
shot demo-welcome-mode-selection
```

to insert a `SCREENSHOT` line at that point (uses **host** timing for the preceding `WAIT` when `--device-timing` is on).

6. Stop with **Ctrl+C**. Files are flushed to disk.

### More flags

| Flag | Purpose |
|------|---------|
| `--tap-threshold-px` | Movement under this many pixels counts as a tap (default 35) |
| `--wait-min` / `--wait-max` | Clamp `WAIT` between gestures (seconds) |
| `--only-dev /dev/input/event1` | Record only that input node (avoids duplicate gestures on some emulators) |
| `--raw-out path` | Save raw `getevent` lines for debugging |
| `--quiet` | Do not print `[record] …` lines |

## Replay (play the `.actions` file again)

The `.actions` file is **just a script** for the existing runner. From the **app repo** root:

```bash
# App already on the right screen (e.g. you launched LaunchPanelActivity):
export ACTION_TEST_SKIP_INITIAL_LAUNCH=1
export ACTION_TEST_VERBOSE=1
./scripts/screenshot-capture/action_test_keymod.sh run scripts/screenshot-capture/actions/my_flow.actions emulator-5554
```

With **`ACTION_TEST_VERBOSE=1`**, each line from the `.actions` file is printed as `replay: …` before it runs (same idea as `[record]` while capturing).

Or let the runner open the app first (drops the first `WAIT` semantics you might rely on):

```bash
unset ACTION_TEST_SKIP_INITIAL_LAUNCH
./scripts/screenshot-capture/action_test_keymod.sh run scripts/screenshot-capture/actions/my_flow.actions emulator-5554
```

**Timing on replay:** `action_test_keymod.sh` runs each `WAIT` and `TAP_REL` line in order, so **the same pauses and the same taps** are replayed. Optional screenshots go wherever you set `ACTION_TEST_ARTIFACT_ROOT`.

The **`.timeline.jsonl`** file is for humans/tools (audit, diff); replay does **not** read it — only the `.actions` file drives `adb`.

## README / i18n screenshots

1. Record once per flow with `--emit-rel` (and `--device-timing` if you care about pauses).
2. Hand-edit the `.actions` file if you need `ORIENTATION` or longer `WAIT` lines.
3. Run [`capture_readme_i18n_screenshots.sh`](capture_readme_i18n_screenshots.sh) (same folder) for the full README flow (all demo screens per locale).

**Welcome only** (portrait + landscape, faster):

```bash
cd /path/to/Openterface_KeyCmd_Android
./scripts/screenshot-capture/capture_welcome_i18n_screenshots.sh --locales en --skip-install emulator-5554
```

Smoke (two welcome shots, no locale loop):

```bash
export ACTION_TEST_SKIP_INITIAL_LAUNCH=1
export ACTION_TEST_ARTIFACT_ROOT=/tmp/km_smoke
./scripts/screenshot-capture/action_test_keymod.sh run scripts/screenshot-capture/actions/readme_i18n_smoke.actions emulator-5554
```

To add another screen later: new `.actions` file + thin wrapper sourcing [`lib/i18n_capture_common.sh`](lib/i18n_capture_common.sh) (in this folder).

## Mode page screenshots

Capture the six mode-page screenshots (Welcome portrait/landscape and KM Basic submodes):

```bash
cd /path/to/Openterface_KeyCmd_Android
./scripts/screenshot-capture/capture_mode_page_screenshots.sh emulator-5554
# quick replay without reinstall:
./scripts/screenshot-capture/capture_mode_page_screenshots.sh --skip-install emulator-5554
```

If `LAUNCH_KM_BASIC` cannot launch `MainActivity` directly on your device, enable tap fallback:

```bash
export ACTION_TEST_KM_USE_TAPS=1
./scripts/screenshot-capture/capture_mode_page_screenshots.sh --skip-install emulator-5554
```

## Limits

- Layout changes break pixel-based recordings; re-record or adjust `TAP_REL` values.
- Long regression suites should use Espresso / UI Automator with resource ids; this tool targets **repeatable manual flows** and **screenshot automation**.
