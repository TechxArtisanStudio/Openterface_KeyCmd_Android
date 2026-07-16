# Screenshot capture automation

ADB-driven flows for README demos, Welcome i18n, and mode-page screenshots.

| Script | Purpose |
|--------|---------|
| [`capture_welcome_i18n_screenshots.sh`](capture_welcome_i18n_screenshots.sh) | Welcome screen, portrait + landscape, per locale |
| [`capture_readme_i18n_screenshots.sh`](capture_readme_i18n_screenshots.sh) | Full README demo flow per locale |
| [`capture_mode_page_screenshots.sh`](capture_mode_page_screenshots.sh) | Six mode-page shots (English, single run) |
| [`action_test_keymod.sh`](action_test_keymod.sh) | Low-level action runner |
| [`record_emulator_actions.py`](record_emulator_actions.py) | Record taps into `.actions` files |

Action definitions live in [`actions/`](actions/). Shared locale/install helpers: [`lib/i18n_capture_common.sh`](lib/i18n_capture_common.sh).

**Docs:** [BEHAVIOR_RECORDING.md](BEHAVIOR_RECORDING.md)

**Prerequisites:** API 33+ device/emulator, `adb`, debug app (`../emulator_install_debug_restart.sh`).

**Quick start** (from repo root):

```bash
./scripts/screenshot-capture/capture_welcome_i18n_screenshots.sh --locales en --skip-install emulator-5554
```

Output: `artifacts/welcome-i18n/`, `artifacts/readme-i18n/`, or `artifacts/mode-pages/` at repo root.
