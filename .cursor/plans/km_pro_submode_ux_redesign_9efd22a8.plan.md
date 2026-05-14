---
name: KM Pro submode UX redesign
overview: Revise KM Pro to place submode tabs in the top app menu bar next to hamburger, make portrait Keyboard BI/IME toggle actually show IME, restore reliable landscape full keyboard behavior, and allow NumPad/Compose in landscape using portrait-style presentation.
todos:
  - id: move-tabs-to-app-menu-bar
    content: Move KM Pro tabs into MainActivity top menu bar next to hamburger (same chrome location/visual style as KM Basic), and remove fragment-level tab strip.
    status: completed
  - id: fix-ime-toggle-rendering
    content: Implement real BI/IME switching in portrait Keyboard submode with IME host shown in reserved keyboard slot and explicit showSoftInput flow.
    status: completed
  - id: fix-landscape-keyboard-restore
    content: Ensure landscape enters and restores full built-in keyboard reliably; secondary toggle must switch full/split deterministically.
    status: completed
  - id: allow-numpad-compose-landscape
    content: Remove landscape blocking for NumPad/Compose and render both using portrait-style layout behavior while device is landscape.
    status: completed
  - id: state-persistence-cleanup
    content: Align persistence keys and state transitions across MainActivity, CompositeFragment, and CustomKeyboardView for tabs + P1R2C7 toggle.
    status: completed
isProject: false
---

# KM Pro submode UX redesign (test-driven revision)

## What changed from your test

Your feedback requires these behavior updates:

1. Tabs must be in the **top menu bar next to hamburger**, not inside KM Pro fragment content.
2. Portrait Keyboard + BI/IME toggle must **actually show IME keyboard**.
3. Rotating to landscape must reliably show **full keyboard** when expected.
4. NumPad and Compose must be **allowed in landscape** and display as portrait-style layouts (not blocked by toast/redirect).

## Implementation anchors

- Top app chrome location:
  - [`activity_main.xml`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/activity_main.xml)
  - [`MainActivity.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/MainActivity.java)
- KM Pro host behavior:
  - [`CompositeFragment.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/CompositeFragment.java)
- P1R2C7 fixed strip toggle:
  - [`CustomKeyboardView.java`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java)
- Layout variants that must stay in sync:
  - [`fragment_composite.xml`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/fragment_composite.xml)
  - [`layout-land/fragment_composite.xml`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout-land/fragment_composite.xml)
  - [`fragment_composite_split.xml`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout/fragment_composite_split.xml)
  - [`layout-land/fragment_composite_split.xml`](file:///Users/billywang/project/openterface/Openterface_KeyMod_Android/app/src/main/res/layout-land/fragment_composite_split.xml)

## Revised UX model

### 1) Tabs in top menu bar (next to hamburger)

- Host tabs at MainActivity header level (same visual zone as KM Basic top tabs, directly right of hamburger).
- Show KM Pro tabs only when current top fragment is `CompositeFragment`.
- Tabs: `Keyboard | Numpad | Compose`.
- Keep P1R2C7 as secondary control inside keyboard strip.

### 2) Keyboard submode behavior

- Portrait:
  - P1R2C7 toggles `BI <-> IME`.
  - IME implementation uses a dedicated keyboard slot host (same bounds as built-in keyboard area) and actively requests soft input.
  - Touchpad and shortcut panel heights remain stable while switching BI/IME.
- Landscape:
  - P1R2C7 toggles `Full <-> Split` built-in layouts.
  - Rotation from portrait with IME active exits IME and restores last chosen landscape built-in layout.

### 3) NumPad and Compose in landscape

- Remove landscape guard/redirect logic.
- In landscape, both NumPad and Compose render with portrait-style composition:
  - force normal (non-split) container path,
  - keep portrait-like vertical arrangement and weights for those submodes.
- Keyboard submode retains full/split landscape behavior.

## State model (revised)

- Persist:
  - current submode: `keyboard | numpad | compose`
  - portrait input surface (keyboard submode): `built_in | ime`
  - landscape keyboard layout (keyboard submode): `full | split`
- Remove any one-time “portrait-only” redirect flags and strings.
- Ensure MainActivity header tabs and CompositeFragment internal state stay single-source-of-truth (header click -> fragment state update -> keyboard strip label refresh).

## Execution sequence

1. **Header tab migration**
   - Move KM Pro tab controls from fragment-level include to MainActivity header container.
   - Add show/hide + selected-state sync when fragment changes.

2. **IME rendering fix**
   - Add/restore IME host in keyboard slot and explicit show/hide soft keyboard flow in portrait Keyboard mode.
   - Wire P1R2C7 callback to this flow.

3. **Landscape keyboard reliability**
   - Normalize full/split transitions and rotate-entry path.
   - Ensure full keyboard is default/fallback when split is unavailable.

4. **Landscape NumPad/Compose enablement**
   - Remove blocking toast/auto-switch.
   - Force portrait-style layout behavior for NumPad/Compose even in landscape orientation.

5. **Polish and validation**
   - Verify all 4 layout XML variants.
   - Verify P1R2C7 label text updates correctly by orientation/submode.
   - Build and unit test once Java runtime is available.

## Verification checklist

- KM Pro tabs appear in top menu bar next to hamburger and not inside fragment body.
- Portrait Keyboard: BI -> IME shows IME; IME -> BI returns built-in keyboard.
- Rotate portrait IME -> landscape: IME closes; full/split built-in behaves correctly.
- Landscape NumPad/Compose are reachable and render portrait-style layouts.
- No stale “portrait-only” warning appears for NumPad/Compose.
