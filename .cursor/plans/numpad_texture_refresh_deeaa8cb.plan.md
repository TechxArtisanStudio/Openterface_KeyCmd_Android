---
name: Numpad Texture Refresh
overview: Add a subtle low-profile texture to KM Basic Numpad keycaps only, while preserving existing interaction states and theme compatibility.
todos:
  - id: design-textured-drawable
    content: Design Numpad-only textured key drawable with idle/pressed/selected states
    status: pending
  - id: wire-numpad-styles
    content: Add and apply Numpad textured styles for text and icon key cells
    status: pending
  - id: patch-layouts
    content: Update portrait and landscape Numpad layouts including NUMPAD_0 background
    status: pending
  - id: theme-parity-check
    content: Add/verify day-night color tokens for texture and confirm contrast
    status: pending
  - id: verify-visual-states
    content: Run visual and interaction checks for Numpad without affecting other KM Basic keys
    status: pending
isProject: false
---

# KM Basic Numpad Texture Plan

## Current state (what I found)
- Numpad submode is XML/View based in [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/fragment_basic_numpad.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/fragment_basic_numpad.xml) and [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout-land/fragment_basic_numpad.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout-land/fragment_basic_numpad.xml).
- Numpad text and icon cells share flat key backgrounds via styles in [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values/styles.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values/styles.xml), currently pointing to `@drawable/key_background`.
- Pressed/selected behavior is driven by background states (`state_pressed`, `state_selected`) in [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/drawable/key_background.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/drawable/key_background.xml) and touch handling in [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicKeyFeedback.java`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/basic/BasicKeyFeedback.java).
- Existing subtle pattern language already exists on touchpad surface (`layer-list` + gradients) in [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/drawable/km_basic_touchpad_surface.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/drawable/km_basic_touchpad_surface.xml), which we can mirror stylistically.

## Proposed implementation
- Create a **new Numpad-only key background drawable** (e.g. `key_background_numpad_textured.xml`) using a low-contrast layered gradient texture on idle state.
- Keep pressed and selected states visually consistent with current behavior (primary container + selected stroke), with optional very subtle overlay so feedback remains clear.
- Add dedicated styles for Numpad cells that use the new drawable, instead of changing global `Widget.KeyMod.BasicNumpadCell`/`KeyModBasicNumpadIconCell` behavior for other consumers.
- Update both portrait and landscape Numpad layouts to use textured styles and set `NUMPAD_0` `FrameLayout` background to the textured drawable.
- If new texture colors are introduced, define semantic tokens in `values/colors.xml` and `values-night/colors.xml` to keep day/night parity.

## Files to change
- [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/drawable/key_background_numpad_textured.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/drawable/key_background_numpad_textured.xml) (new)
- [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values/styles.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values/styles.xml)
- [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/fragment_basic_numpad.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/fragment_basic_numpad.xml)
- [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout-land/fragment_basic_numpad.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout-land/fragment_basic_numpad.xml)
- [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values/colors.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values/colors.xml) (only if needed)
- [`/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values-night/colors.xml`](/Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/values-night/colors.xml) (only if needed)

## Validation checklist
- Numpad keycaps show subtle texture in both portrait and landscape.
- Pressed/selected states remain clear and unchanged in interaction quality.
- Contrast for labels/icons remains acceptable in day/night themes.
- No visual regressions in non-Numpad KM Basic keys.
- No TalkBack/content-description regressions (structure unchanged).
