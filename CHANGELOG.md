# Changelog

All notable changes to this project are documented in this file.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.8] — 2026-05-04

### Highlights

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
- **Built-in strip profiles**: **Symbols** and **Math** presets for the Rows 2–3 strip.
- **Canonical slot keys** (for example `b-p0r2c1` … `f-p…`) used consistently for maps, docs, and sharing.
- **Rows 2–3 slot editor**: replaces the old small sheet with a **full-screen, single-key** editor; UI patterns match the create-shortcut flow.
- After hub edits, the keyboard **reloads strip-related preferences**; slot rows show **id badges** where helpful; strip profiles support **Reset** where applicable.
- **Icon picker** for Rows 2–3 uses **theme-aware** selection styling.
- **Correctness**: fixes for page-0 **local Fn** overlays, page-2 **paren / grave** behavior with **Fn latch**, and **Unicode** entry for strip shortcuts where applicable.

### Keyboard (page 3 quick toggles)

- On **Keyboard and Mouse** mode, **fixed strip page 3** now exposes **six Row 1 profile** quick toggles and **six Rows 2–3 strip profile** quick toggles (defaults include Fusion 360, VS Code, Photoshop for new profile slots, and Arrows / Currency / Box Lines for new strip slots). Column 7 on row 2 remains intentionally blank; column 7 on row 3 remains **FN**.

### Localization

- **Presentation mode**: UI and control strings are internationalized like the rest of the app.

### Build and CI

- **Pull request** builds use **assembleDebug** so CI does not require release signing secrets.
- **Release** builds on `main` still produce a signed **release APK** and upload artifacts as before.
- **Gradle setup** action updated to remove deprecated/invalid cache inputs compatible with `gradle/actions/setup-gradle@v4`.

### Upgrade notes

- If you use **shared strip maps** or automation, prefer **canonical slot ids** (`b-p{page}r{row}c{col}` / `f-…`) over any legacy key forms.
- After upgrading, open **Shortcut Hub** once if strip or profile labels look stale; the app refreshes prefs-driven UI on relevant changes.

---

## [0.7] — earlier

Internationalization work (locales, welcome panel, settings, connection UI, Shortcut Hub defaults, Traditional Chinese and related resources). See git history before tag `0.7` for full detail.
