# Changelog

All notable changes to this project are documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.9] — 2026-05-04

### Release summary (pull request and GitHub release)

Openterface KeyMod **0.9** (`versionCode` **9**) includes the **gamepad-ux** workstream: a more capable **virtual gamepad** (optional third key stick, square touchpad with long-press size, bundled touchpad mouse buttons, **Preset**-based layouts with tap/long-press, Material toolbar, stick cardinal and haptics fixes, optional **rightStickMouseGain** for right-stick mouse), a reworked **Shortcut Hub** with a visual **strip catalog**, **Symbols** / **Math** Rows 2–3 profiles, **canonical slot IDs**, a **full-screen strip key editor**, **fixed strip page 3** profile and strip quick toggles, **Presentation mode** strings in the localization set, and **CI** tweaks. The **keyboard** gains a correctness fix for **fixed strip page 2 (punctuation)**: **local Fn** top-right corner hints now follow the **same-column latch swap** (shared `PAGE2_ROW*` caps with the catalog), not HID overlay pairings. See **Upgrade notes** for gamepad migration.

### Highlights

- **Gamepad**: optional **third key stick** (`stick_key_extra`, arrow keys by default, same key-mapping dialog as the left stick); **touchpad** defaults to a **square** footprint with a long-press **size** dialog; **new touchpads** bundle three **mouse button** modules (left / middle / right); **right-stick mouse** uses a slightly tighter dead zone, a gentler response curve, and optional layout field **`rightStickMouseGain`** (also mirrored to preferences when a preset applies).
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
