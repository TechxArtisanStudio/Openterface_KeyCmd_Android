# Changelog

All notable changes to this project are documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

- **Gamepad (breaking)**: Preset store **v7** removes the built-in **Two buttons** (`preset_two_buttons`) and **Classic_1–Classic_4** layouts (`preset_classic_*`) from the on-disk index and deletes their JSON files. If the active preset was one of those, the app switches to **`preset_default`** and applies it. Only **`preset_default`** remains **deletion-protected**; the classic factory-reset toolbar control and overflow **Reset layout** action are removed with those presets.
- **Gamepad (breaking)**: Preset schema **v6** removes optional extra-thumb module ids (`stick_key_extra`, `stick_aux_*`). Upgrading **v5 → v6** renames a lone `stick_key_extra` back to **`stick_right`** and drops duplicate extras when both slots exist. The **Add module** menu no longer offers an extra thumb; **right stick** pointer (mouse) mode keeps per-module **`stickMouseSensitivity`** and the in-dialog pointer sensitivity slider.
- **Gamepad (breaking)**: Preset schema **v7** — **`stick_left`** is optional (a layout may have **no** left-thumb modules). **v6 → v7** is a no-op version bump. **Add module → D-Pad / Stick** inserts **`stick_left`** when that id is missing, otherwise **`stick_left_2`**, **`stick_left_3`**, …. **`stick_left`** can be removed like extras. When the applied layout has **no** eligible left-thumb module, **`GamepadLayoutPresetApplier`** removes legacy prefs **`gamepad_stick_mode`**, **`gamepad_stick_up`**, **`gamepad_stick_left`**, **`gamepad_stick_down`**, **`gamepad_stick_right`**, **`gamepad_stick_size`**; if only **`stick_left_2+`** exist, prefs mirror the **lowest-numbered** aux module.
- **Branding**: The app’s display name is **KeyMod**; the **Welcome** screen shows the **KeyMod** wordmark; other in-app surfaces (nav, composite, gamepad, etc.) still use the **Openterface** wordmark where applicable; CI artifact names and APK filename prefix use **KeyMod**; `applicationId` remains **`com.openterface.keymod`** (existing installs upgrade in place).
- **Gamepad**: presets may include **multiple touchpads** (`touchpad_1`, `touchpad_2`, …); **Add module → Touchpad** allocates the next id and a slightly offset anchor. Bundled **L/M/R mouse buttons** are added only when missing (shared across touchpads); removing the **last** touchpad still removes those buttons if present.
- **Gamepad**: **`stick_left_2+`** default to **D-pad cross** + WASD; **split** D-pad is not allowed on those ids (global hit-target collision). Aux modules support **D-pad cross**, **stick keys**, or **stick mouse** in configuration.
- **Gamepad**: touchpad **L/M/R mouse buttons** use a larger default draw radius; bundled modules no longer inherit a mistaken **0.38** module scale (legacy saves are normalized on load). Optional layout field **`touchpadMouseButtonScale`** (0.5–2×) is configurable via touchpad long-press **Mouse button size…**; each mouse module also has **This button size…** for its own **scale**.
- **Keyboard & Mouse (Basic)**: full-screen mode hides the main app header; dedicated **Basic** keyboard, **numpad**, **touchpad**, and **IME** compose surfaces replace the old `CustomKeyboardView` shortcut strip for this tier. Row-1 chrome mirrors menu, sub-modes, target OS, and connection. Shared **HID transport** helpers (`KeyboardHidTransport`, `MouseRelHidTransport`, `Ch9329PacketUtil`) are introduced and wired from `CustomKeyboardView` for keyboard reports. The unused `KbMousePlaceholderFragment` placeholder is removed. Key taps use **pressed-state** visuals, **haptic** feedback (same `haptic_feedback` preference as Pro), and **theme** key surfaces via `key_background` (`colorSurfaceContainerHighest` idle, `colorPrimaryContainer` pressed). **Portrait**: row-1 chrome no longer uses `HorizontalScrollView` + weighted `wrap_content` (which could collapse the keyboard height to zero); `BasicKeyboardFragment` rebuilds on `onConfigurationChanged` because `MainActivity` lists `orientation` in `configChanges`. While **Keyboard & Mouse (Basic)** is showing, **immersive system UI is disabled** (`systemUiVisibility` cleared) so the content area excludes the nav/gesture inset and weighted rows fill the screen; leaving Basic restores fullscreen immersive. **Basic numpad** is **5×8** in portrait (Prt Sc / Scr Lk / Pause / Home / End …) with row height ratio **1:1:2:2:3:3:3:3**, **two-row** `+` and **Enter**, and a **two-column** `0`; **00** sends two `NUMPAD_0` taps. **Landscape** uses `layout-land/fragment_basic_numpad.xml`: an **8×5** grid (nav cluster lower-left, digit block right) with tall `+`, `Enter`, and `00`, and wide top-row `Del` and bottom-row `0`. Because **MainActivity** declares `configChanges` for orientation, **`BasicNumPadFragment`** re-inflates `fragment_basic_numpad` in **`onConfigurationChanged`** so portrait keeps the **5×8** layout and landscape gets the **8×5** layout after rotation. **Basic IME** adds **Clear**, **Redo clear**, and **Send** / **Stop** with the same **ASCII-only** send gate as Pro IME sub-compose (`ImeComposeSendGate`).
- **Keyboard & Mouse (Basic) full keyboard**: `BasicKeyboardFragment` applies **navigation bar** window insets to `BasicPhysicalKeyboardView` padding using `basic_keyboard_content_inset` (start/bottom) and `basic_keyboard_content_inset_end` (end); landscape uses **14dp** start and **10dp** end plus nav insets so the right edge sits slightly closer to the nav strip (display cutout is not merged into horizontal padding). **Long-press repeat**: character and function keys (not sticky modifiers / Caps) fire on key-down and **auto-repeat** after ~400 ms at ~50 ms via `BasicKeyFeedback.repeatableKeyTouchListener` (e.g. hold Backspace or a letter). **Tap preview**: on press, a non-touchable floating bubble (`BasicKeyPreview`) shows the effective label or character (shift/caps-aware) above the key, or **below** when the top row would clip; `BasicKeyFeedback` gains optional preview suppliers, and `bind()` dismisses any showing popup when the layout rebuilds.
- **Keyboard & Mouse Pro**: unchanged composite experience (`CompositeFragment` + full `CustomKeyboardView`).

## [0.9] — 2026-05-04

### Release summary (pull request and GitHub release)

Openterface KeyMod **0.9** (`versionCode` **9**) includes the **gamepad-ux** workstream: a more capable **virtual gamepad** (square touchpad with long-press size, bundled touchpad mouse buttons, **Preset**-based layouts with tap/long-press, Material toolbar, stick cardinal and haptics fixes, optional **rightStickMouseGain** for right-stick mouse), a reworked **Shortcut Hub** with a visual **strip catalog**, **Symbols** / **Math** Rows 2–3 profiles, **canonical slot IDs**, a **full-screen strip key editor**, **fixed strip page 3** profile and strip quick toggles, **Presentation mode** strings in the localization set, and **CI** tweaks. The **keyboard** gains a correctness fix for **fixed strip page 2 (punctuation)**: **local Fn** top-right corner hints now follow the **same-column latch swap** (shared `PAGE2_ROW*` caps with the catalog), not HID overlay pairings. See **Upgrade notes** for gamepad migration.

### Highlights

- **Gamepad**: **touchpad** defaults to a **square** footprint with a long-press **size** dialog; **new touchpads** bundle three **mouse button** modules (left / middle / right); **right-stick mouse** uses a slightly tighter dead zone, a gentler response curve, and optional layout field **`rightStickMouseGain`** (also mirrored to preferences when a preset applies). *(Later schema v6 removed the optional third thumb slot `stick_key_extra` / `stick_aux_*`.)*
- **Gamepad**: the separate **1 Button / 2 Buttons** toggle is removed; **one-button** and **two-button** layouts are **built-in presets** (`preset_default` and `preset_two_buttons`). Use **short tap** on **Preset** to cycle the active layout, **long-press** for the full preset list (import, add module, export). Upgrades from the legacy two-button preference activate the two-button preset automatically once.
- **Gamepad UI**: Material toolbar for **Edit** / **Preset** / **Done**, active **preset name** beside Preset, **cardinal key highlights** fixed for dynamic stick bounds (`stick_left` / `stick_right`), light **haptic tick** when a stick cardinal newly engages and **haptics only for `button_*` face keys** (not mouse clicks), **empty-area long-press** for background and **add module** only while **Editing**, with the same add actions as the preset menu.
- **Shortcut Hub** rework: clearer navigation between Row 1 app shortcuts and Rows 2–3 fixed-strip behavior, a visual strip catalog, and stronger profile and preset workflows.
- **Rows 2–3 strip profiles**: built-in **Symbols** and **Math** themes, canonical slot IDs for imports and tooling, and a dedicated full-screen editor for each strip key.
- **Keyboard strip page 3**: six quick slots for Row 1 profiles and six for strip profiles (seventh column on row 2 stays empty; row 3 keeps **FN**).
- **Presentation mode** strings are now part of the app’s localization set.

### Shortcut Hub and profiles

- Refined hub layout and flows; **strip presets** and **profile sharing** are easier to discover and use.
- Toolbar **Export** removed; sharing is handled via the profile row control.
- **Favorites**: remove from favorites, better sync with the strip, and fixes for category/catalog edge cases.
- **Default profile** can be **reset to factory** shortcuts from the hub.
- **Tap a shortcut row** to edit; **reorder categories**; **edit** affordance and **tap-to-run** with a short themed highlight on the row.
- **Edit Shortcut** experience aligned with the **New shortcut** bottom sheet for consistency.

### Fixed strip and Rows 2–3 strip profiles

- **Strip catalog** in the hub: browse fixed-strip pages and slots with clearer labels (including Row 1 vs Rows 2–3 scope).
- **Strip catalog grid** and underlying **Rows 2–3 strip profile** model: multiple named strip profiles, import/export style workflows, and unit tests for builtins and slot maps.
- **Built-in strip profiles**: six themed presets only (**Symbols**, **Math**, **Box & Lines**, **Latin Extended**, **Arrows**, **Currency**); the old factory **Default** strip built-in (`strip_default`) is removed on upgrade (active and quick-toggle prefs migrate; legacy keyboard-strip data becomes a deletable **Migrated strip** profile when the one-time v1 migration still applies).
- **Canonical slot keys** (for example `b-p0r2c1` … `f-p…`) used consistently for maps, docs, and sharing.
- **Rows 2–3 slot editor**: replaces the old small sheet with a **full-screen, single-key** editor; UI patterns match the create-shortcut flow.
- After hub edits, the keyboard **reloads strip-related preferences**; slot rows show **id badges** where helpful; strip profiles support **Reset** where applicable.
- **Icon picker** for Rows 2–3 uses **theme-aware** selection styling.
- **Correctness**: fixes for page-0 **local Fn** overlays, page-2 **paren / grave** behavior with **Fn latch**, and **Unicode** entry for strip shortcuts where applicable.
- **Fixed strip page 2 (punctuation):** local **Fn** top-right corner hints use **same-column latch swap** (shared `PAGE2_ROW*` caps with the strip catalog), not HID overlay pairings—so **`[`**↔**`'`**, **`:`**↔**`%`**, **`@`**↔**`|`** among others stay consistent when the latch toggles.

### Keyboard (page 3 quick toggles)

- On **Keyboard and Mouse** mode, **fixed strip page 3** now exposes **six Row 1 profile** quick toggles and **six Rows 2–3 strip profile** quick toggles (defaults include Fusion 360, VS Code, Photoshop for new profile slots; strip quick-toggle defaults are **Symbols**, **Math**, **Box & Lines**, **Latin Extended**, **Arrows**, **Currency**). Column 7 on row 2 remains intentionally blank; column 7 on row 3 remains **FN**.

### Localization

- **Presentation mode**: UI and control strings are internationalized like the rest of the app.

### Build and CI

- **Pull request** builds use **assembleDebug** so CI does not require release signing secrets.
- **Release** builds on `main` still produce a signed **release APK** and upload artifacts as before.
- **Gradle setup** action updated to remove deprecated/invalid cache inputs compatible with `gradle/actions/setup-gradle@v4`.

### Upgrade notes

- **Gamepad**: after update, use **Preset** (tap to cycle, long-press for the list). The old **1 Button / 2 Buttons** control is gone; your previous two-button preference selects the **Two buttons** built-in preset on first launch after upgrade.
- **Gamepad QA** (manual): add a touchpad and confirm bundled mouse buttons fire correct clicks; resize touchpad and reload preset; add **Arrow stick**, remap keys, confirm combined keyboard report with left/right key sticks; exercise **right-stick mouse** feel and, if set in shared JSON, **`rightStickMouseGain`** in `[0.25, 4]`.
- If you use **shared strip maps** or automation, prefer **canonical slot ids** (`b-p{page}r{row}c{col}` / `f-…`) over any legacy key forms.
- After upgrading, open **Shortcut Hub** once if strip or profile labels look stale; the app refreshes prefs-driven UI on relevant changes.

---

## [0.7] — earlier

Internationalization work (locales, welcome panel, settings, connection UI, Shortcut Hub defaults, Traditional Chinese and related resources). See git history before tag `0.7` for full detail.
