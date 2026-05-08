---
name: Export creator name meta
overview: Add optional meta.creator for gamepad JSON exports, persist the name in SharedPreferences, prompt on first save/share, stamp on export, and show/edit the name in customize (edit) mode on the top chrome bar immediately left of the connection icon (mapping-hints toggle stays in the centered edit row below).
todos:
  - id: meta-creator-field
    content: Add nullable creator to GamepadLayoutPresetDocument.Meta + optional validate
    status: pending
  - id: prefs-key-dialog
    content: Add GamepadPreferenceKeys + ensureExportCreatorNameThen() dialog; wire save/share entry points
    status: pending
  - id: stamp-export
    content: Set meta.creator in buildPresetExportJson + GamepadLayoutPresetSnapshotBuilder from prefs
    status: pending
  - id: chrome-creator-chip
    content: fragment_gamepad.xml chip before connection_wrap; GamepadFragment visibility sync with edit mode; tap opens edit dialog; refresh label from prefs
    status: pending
  - id: strings-tests
    content: Add strings + unit test for meta.creator round-trip / validation
    status: pending
isProject: false
---

# Gamepad export creator name in `meta` + edit-mode chip

## Current state

- [`GamepadLayoutPresetDocument.Meta`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetDocument.java): `id`, `displayName`, `description`, `exportedAt`, `sourceAppVersion` — no creator.
- Export: [`GamepadFragment.buildPresetExportJson`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java), [`GamepadLayoutPresetSnapshotBuilder.buildFrom`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetSnapshotBuilder.java).
- Top chrome: [`fragment_gamepad.xml`](Openterface_KeyMod_Android/app/src/main/res/layout/fragment_gamepad.xml) `gamepad_chrome_bar` ends with a weighted spacer then [`gamepad_chrome_connection_wrap`](Openterface_KeyMod_Android/app/src/main/res/layout/fragment_gamepad.xml) (Bluetooth/USB). Edit-only controls live in [`toggle_row`](Openterface_KeyMod_Android/app/src/main/res/layout/fragment_gamepad.xml) (background, add module, [`gamepad_mapping_hints_toggle`](Openterface_KeyMod_Android/app/src/main/res/layout/fragment_gamepad.xml)); visibility driven by [`setEditModeToolbarExtrasVisible`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java).

## Target behavior (export + meta)

- Add **`meta.creator`** (nullable String, Gson camelCase). Stamp from SharedPreferences on every save-to-file and share export when non-empty.
- **First save/share** when creator pref empty: dialog (reuse [`dialog_gamepad_text_field.xml`](Openterface_KeyMod_Android/app/src/main/res/layout/dialog_gamepad_text_field.xml)) then persist pref and continue.
- **Entry points**: preset sheet save, overflow save file, `sharePresetJson` — gate with `ensureExportCreatorNameThen(Runnable)` before SAFF launch / JSON build.
- **Prefs**: new key in [`GamepadPreferenceKeys`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadPreferenceKeys.java) (e.g. `GAMEPAD_PRESET_EXPORT_CREATOR_NAME`).

## Edit-mode UI (new requirement)

- **Placement**: In [`fragment_gamepad.xml`](Openterface_KeyMod_Android/app/src/main/res/layout/fragment_gamepad.xml), inside `gamepad_chrome_bar`, insert a **tappable** control **immediately before** `gamepad_chrome_connection_wrap` (so reading left-to-right: … spacer … **creator** | **connection**). That sits on the same top strip as the connection icon; the **mapped key hints** toggle remains in `toggle_row` below (still the closest edit-mode control to the right cluster — acceptable unless we later move hints into the chrome row).
- **Visibility**: Show the creator chip **only while customize/edit mode is on** (same effective visibility as [`toggle_row`](Openterface_KeyMod_Android/app/src/main/res/layout/fragment_gamepad.xml) / [`setEditModeToolbarExtrasVisible`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java)); hide when leaving edit mode.
- **Label**: Show trimmed pref value, or a short placeholder (e.g. “Creator”) when empty — tap always opens **edit creator** dialog (same validation as first-time prompt), writes prefs, updates chip text, optional toast.
- **Implementation**: `MaterialButton` style aligned with other chrome controls (`maxWidth` + `ellipsize` for long names); `findViewById` in [`GamepadFragment`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java); call `refreshExportCreatorChipUi()` from pref whenever entering edit mode and after dialog OK.

## Validation / compatibility

- Optional `meta.creator` length / trim in `validateOrThrow` if desired.
- Schema stays **v8**; field is additive.

## Strings

- Export prompt strings + chip CD/title/hint (e.g. `gamepad_creator_chip_cd`, `gamepad_creator_edit_title`).

## Optional follow-up

- Move `gamepad_mapping_hints_toggle` into `gamepad_chrome_bar` to the left of the new chip for strict left-to-right **hints | creator | connection** (tighter phones — defer unless needed).
