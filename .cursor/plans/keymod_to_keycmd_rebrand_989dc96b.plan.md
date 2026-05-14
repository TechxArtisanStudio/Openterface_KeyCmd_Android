---
name: KeyMod to KeyCmd rebrand
overview: Rebrand the Android app from KeyMod to KeyCmd by updating user-visible strings, replacing the horizontal wordmark and launcher visuals with artwork derived from [icon/KeyCmd.svg](icon/KeyCmd.svg), renaming theme/widget resource families and the Application class for consistency, and deliberately keeping Java package / applicationId and JSON schema format strings unchanged unless you later choose a migration-heavy path.
todos:
  - id: wordmark-vector
    content: Convert KeyCmd.svg to tint-friendly vector drawable; replace ic_keymod_wordmark (or add ic_keycmd_wordmark + update nav_menu + launch_panel layouts)
    status: pending
  - id: strings-locales
    content: Update app_name, IME/header strings, and all locale files; grep repo for remaining KeyMod user strings
    status: pending
  - id: theme-style-rename
    content: Rename Theme.KeyMod*/Widget.KeyMod*/TextAppearance.KeyMod* in themes (day+night), styles, all layout @style refs, ThemeManager + GamepadFragment + other R.style usages
    status: pending
  - id: application-class
    content: Rename KeyModApplication → KeyCmdApplication; update AndroidManifest android:name
    status: pending
  - id: gradle-docs-scripts
    content: archivesBaseName KeyCmd; install script + README + docs/FAQ + docs/USER_GUIDE + docs/THEME_AND_KM_PRO_COLORS
    status: pending
  - id: launcher-icons
    content: Regenerate adaptive + legacy launcher assets from KeyCmd (vector foreground preferred for API 26+; PNG mipmaps for older)
    status: pending
  - id: strip-drawable-ble-exports
    content: "Decide: ic_keyboard_keycmd_24 rename; BLE regex; export filename prefixes — implement per product choice"
    status: pending
  - id: verify-build
    content: Run assembleDebug and spot-check UI (launcher, launch panel, drawer, strip)
    status: pending
isProject: false
---

# KeyMod → KeyCmd rebrand assessment

## What is branded today

| Area | Current state | Action |
|------|---------------|--------|
| **App label** | [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) `app_name` and locale overrides (`values-de`, `values-es`, `values-fr`, `values-ja`, `values-zh-rCN`, `values-b+zh+Hant`) | Set to **KeyCmd**; translate where locales currently say KeyMod |
| **IME / header copy** | e.g. `top_shortcut_ime_toggle_*`, `header_km_pro_input_builtin_cd` — text contains KeyMod | Replace **KeyMod** with **KeyCmd** in values; optionally rename resource keys `*_keymod` → `*_keycmd` (no Java references found today, so this is safe if desired) |
| **In-app wordmark** | [`app/src/main/res/drawable/ic_keymod_wordmark.xml`](app/src/main/res/drawable/ic_keymod_wordmark.xml) (white paths, tint-friendly) used in [`nav_menu.xml`](app/src/main/res/layout/nav_menu.xml), [`activity_launch_panel.xml`](app/src/main/res/layout/activity_launch_panel.xml), [`layout-land/activity_launch_panel.xml`](app/src/main/res/layout-land/activity_launch_panel.xml) | Replace path data with **KeyCmd** geometry from [`icon/KeyCmd.svg`](icon/KeyCmd.svg) (same pattern: `#FFFFFFFF` fills for tint). Prefer renaming file to `ic_keycmd_wordmark.xml` and updating `android:src` in those three layouts for clarity; delete or keep old file only if nothing references it |
| **Strip “keyboard” slot icon** | [`ic_keyboard_keymod_24.xml`](app/src/main/res/drawable/ic_keyboard_keymod_24.xml) — visually a generic keyboard + “A”, used from [`StripCatalogPhysicalKeyIcons.java`](app/src/main/java/com/openterface/keymod/preset/StripCatalogPhysicalKeyIcons.java) | **Not** the KeyMod wordmark. Options: (a) rename to `ic_keyboard_keycmd_24` and keep the same glyph (name-only alignment), or (b) supply a **new 24dp monogram** derived from KeyCmd (full wordmark will be unreadable at 24dp). Default recommendation: **rename + keep glyph** unless you provide a dedicated small-mark asset |
| **Launcher (adaptive)** | [`mipmap-anydpi-v26/ic_launcher.xml`](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) / `ic_launcher_round.xml` → [`drawable/ic_launcher_foreground.xml`](app/src/main/res/drawable/ic_launcher_foreground.xml) → `@mipmap/ic_launcher_foreground` **PNGs** per density | Regenerate **foreground** from KeyCmd (vector in `drawable/ic_launcher_foreground.xml` is ideal for API 26+). Pre-26 full tiles still live as [`mipmap-*/ic_launcher.png`](app/src/main/res) — regenerate with Android Studio **Image Asset** (or scripted export from SVG) so home screen matches on older devices |
| **Theme / widget style names** | `Theme.KeyMod.*`, `Widget.KeyMod.*`, `TextAppearance.KeyMod.*`, `ThemeOverlay.KeyMod.*` in [`values/themes.xml`](app/src/main/res/values/themes.xml), [`values-night/themes.xml`](app/src/main/res/values-night/themes.xml), [`values/styles.xml`](app/src/main/res/values/styles.xml); referenced from many layouts + [`ThemeManager.java`](app/src/main/java/com/openterface/keymod/ThemeManager.java), [`GamepadFragment.java`](app/src/main/java/com/openterface/fragment/GamepadFragment.java), [`GamepadLayoutPreviewRenderer.java`](app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPreviewRenderer.java), [`MyShortcutsReorderHelpReadModeDialog.java`](app/src/main/java/com/openterface/keymod/util/MyShortcutsReorderHelpReadModeDialog.java), [`Rows23SlotEditorFragment.java`](app/src/main/java/com/openterface/fragment/Rows23SlotEditorFragment.java) | Mechanical rename to **`KeyCmd`** everywhere (`Theme.KeyCmd`, `R.style.Theme_KeyCmd_*`, etc.) |
| **Application class name** | [`KeyModApplication.java`](app/src/main/java/com/openterface/keymod/KeyModApplication.java) + [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) `android:name` | Rename class to **`KeyCmdApplication`**, update manifest |
| **Build artifact name** | [`app/build.gradle`](app/build.gradle) `archivesBaseName "KeyMod"` | Set to **`KeyCmd`** (matches future APK names in docs) |
| **Docs** | [`README.md`](README.md), [`docs/FAQ.md`](docs/FAQ.md), [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md), [`docs/THEME_AND_KM_PRO_COLORS.md`](docs/THEME_AND_KM_PRO_COLORS.md) | Replace product name **KeyMod → KeyCmd**; update APK filename examples to `KeyCmd-*.apk` |
| **Scripts** | [`scripts/install_keymod_to_phone.sh`](scripts/install_keymod_to_phone.sh) | Rename / adjust paths to match new default APK basename |
| **Comments** | e.g. [`top_strip_ime_toggle_background.xml`](app/src/main/res/drawable/top_strip_ime_toggle_background.xml), [`function_button_background_profile_strip.xml`](app/src/main/res/drawable/function_button_background_profile_strip.xml), [`CustomKeyboardView.java`](app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) | Update “KeyMod” in comments to **KeyCmd** where it refers to the product |

## What to leave unchanged (recommended default)

- **`applicationId` / `namespace` / Java package** `com.openterface.keymod` in [`app/build.gradle`](app/build.gradle) and all `package` / `import` / layout `com.openterface.keymod.*` — changing this is a **new Play Store app**, breaks deep links, automation, and every import; it is orthogonal to user-visible “KeyCmd” branding.
- **Persisted JSON format strings** such as `openterface_keymod_rows23_strip_profile`, `openterface_keymod_keyboard_preset` in [`Rows23StripProfileConstants.java`](app/src/main/java/com/openterface/keymod/preset/Rows23StripProfileConstants.java), [`KeyboardStripPresetConstants.java`](app/src/main/java/com/openterface/keymod/preset/KeyboardStripPresetConstants.java), and detection in [`KeyboardStripPreset.java`](app/src/main/java/com/openterface/keymod/preset/KeyboardStripPreset.java) — these are **compatibility contracts** with existing exports. Renaming requires a **versioned migration** (accept old + write new), not a blind replace.
- **Signing `keyAlias` default `"keymod"`** in `build.gradle` — cosmetic to local keystore; changing default only affects **new** keystores, not a user-visible name.

## Gray areas (product decision)

- **BLE device name filter** [`OpenterfaceBleDeviceNames.java`](app/src/main/java/com/openterface/keymod/OpenterfaceBleDeviceNames.java) — pattern includes `(?i)...keymod...`. If hardware BLE names change to **KeyCmd**, extend the regex to match **keycmd** (and keep **keymod** temporarily if older firmware still advertises KeyMod).
- **Share / export filenames** — `keymod_profile_*.json`, `keymod_gamepad_*.json`, [`GamepadLayoutsFragment.java`](app/src/main/java/com/openterface/fragment/GamepadLayoutsFragment.java) `KeyMod_gamepad_*.json`. Renaming prefixes is **user-visible** but can break external scripts; plan default: rename to **`keycmd_*`** / **`KeyCmd_gamepad_*`** for consistency with the rebrand, unless you need backward-compatible filenames.

## Implementation order (low risk → validation)

1. **Vector wordmark** from `KeyCmd.svg` → drawable + layout `src` updates.
2. **All `strings.xml` locales** + any remaining `KeyMod` in default strings (grep after edits).
3. **Theme / style renames** (`Theme.KeyMod` → `Theme.KeyCmd`, `Widget.KeyMod` → `Widget.KeyCmd`, `TextAppearance.KeyMod` → `TextAppearance.KeyCmd`, `KeyModBasicNumpadIconCell` → `KeyCmdBasicNumpadIconCell`) + Java `R.style` updates + manifest `android:theme` references.
4. **`KeyModApplication` → `KeyCmdApplication`** + manifest.
5. **Gradle `archivesBaseName`**, script, README / USER_GUIDE / FAQ / THEME doc.
6. **Launcher assets** — foreground (and legacy mipmaps if you need pixel-perfect pre-26).
7. **Strip drawable rename** + `StripCatalogPhysicalKeyIcons` + optional BLE / export filename tweaks per decisions above.
8. **Verify**: `./gradlew assembleDebug` and quick UI pass (launch panel, nav drawer, app switcher label, settings app name).

## SVG → Android vector notes

[`icon/KeyCmd.svg`](icon/KeyCmd.svg) uses `viewBox="0 0 521.03 113.76"` and fill `#2c2c2c`. Match existing [`ic_keymod_wordmark.xml`](app/src/main/res/drawable/ic_keymod_wordmark.xml) conventions: `vector` with `android:viewportWidth/Height` from SVG, each `<path android:fillColor="#FFFFFFFF"/>` for header/drawer tinting, and proportional `android:width` / `android:height` in `dp` (~4.6:1 aspect).
