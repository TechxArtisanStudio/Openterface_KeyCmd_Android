---
name: Optional left thumb modules
overview: Make all left D-pad/stick modules optional (including removable `stick_left`), unify add-module behavior, and treat legacy prefs as non-goals—breaking changes and schema bump are acceptable.
todos:
  - id: schema-validate
    content: Bump preset schema if needed (e.g. v7); relax GamepadLayoutPresetDocument.validateOrThrow—remove mandatory stick_left block and hasStickLeft; add upgrader note if bumping
    status: completed
  - id: editor-remove-add
    content: GamepadLayoutDocEditor—canRemove allows stick_left; addLeftDpadStickModule fills stick_left when absent else stick_left_N; Fragment add menu calls it
    status: completed
  - id: applier-breaking
    content: GamepadLayoutPresetApplier—no require(stick_left); when no left-thumb module remove or ignore STICK_* prefs (breaking); else mirror first resolved left module
    status: completed
  - id: fragment-view
    content: GamepadFragment syncFieldsFromLayoutDoc else-reset when no stick_left; label/dialog null-safety; GamepadView sanity if any NPE paths
    status: completed
  - id: tests-changelog
    content: Unit tests for layouts with zero left sticks and aux-only; CHANGELOG breaking note
    status: completed
isProject: false
---

# Optional left thumb (breaking OK)

## User constraint

**Legacy compatibility is not required.** Breaking changes are acceptable (schema bump, prefs behavior, migration rules as needed).

## Goals

- **Remove** every left D-pad/stick module from the canvas, including `stick_left` and all `stick_left_2+`.
- **Merge add semantics**: one **Add module → D-Pad / Stick** action—create `stick_left` when that id is absent, otherwise append `stick_left_N` (current aux behavior).
- **Merge remove semantics**: `canRemove` allows `stick_left` (still protect `button_a`).

## Implementation

### 1. Schema and validation ([`GamepadLayoutPresetDocument.java`](app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetDocument.java))

- **Remove** the dedicated mandatory `stick_left` validation block (~lines 226–249) and the final **`if (!hasStickLeft)`** throw (~457–459). Per-module loop rules already validate any present stick modules.
- **Optional schema v7**: If you want an explicit “v6 always had stick_left” story, bump `SCHEMA_VERSION` in [`GamepadLayoutPresetConstants`](app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetConstants.java), add [`GamepadLayoutPresetUpgrader`](app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetUpgrader.java) migration v6→v7 (can be no-op if only validation changes), and document in CHANGELOG. **Alternatively** keep schema at 6 if you treat this as “validation correction only”—still breaking for old assumptions but no file version bump.

### 2. Editor ([`GamepadLayoutDocEditor.java`](app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutDocEditor.java))

- `canRemove`: allow `"stick_left"`.
- Replace / extend **`addAuxLeftStickDpadCross`** with **`addLeftDpadStickModule`**: if `stick_left` missing → insert primary slot with DPAD cross + WASD; else → `nextAuxLeftStickModuleId` + staggered anchor (existing aux logic).
- [`GamepadFragment.showAddModuleMenu`](app/src/main/java/com/openterface/fragment/GamepadFragment.java): call the unified add method.

### 3. Applier — breaking prefs ([`GamepadLayoutPresetApplier.java`](app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetApplier.java))

- Replace `require(modules, "stick_left")` with: resolve **first** left-thumb module (`stick_left` if present, else lowest `stick_left_N` among STICK_KEY/DPAD/STICK_MOUSE), or **none**.
- **Breaking**: when **no** left-thumb module exists, **remove** `STICK_MODE`, `STICK_UP`, `STICK_LEFT`, `STICK_DOWN`, `STICK_RIGHT`, `STICK_SIZE` (or whatever keys are defined in [`GamepadPreferenceKeys`](app/src/main/java/com/openterface/keymod/gamepad/GamepadPreferenceKeys.java)) from the editor `SharedPreferences`, instead of inventing silent defaults. Any code that still reads those prefs without JSON fallback must tolerate absence (audit [`GamepadFragment`](app/src/main/java/com/openterface/fragment/GamepadFragment.java) / [`GamepadConfigManager`](app/src/main/java/com/openterface/keymod/GamepadConfigManager.java) on load).

### 4. Fragment / UI ([`GamepadFragment.java`](app/src/main/java/com/openterface/fragment/GamepadFragment.java))

- When `stick_left` is absent after `syncFieldsFromLayoutDoc`, **`else`** branch: reset in-memory primary left stick fields (`stickMode`, cardinal keys, split ratios, `stickSizeScale`) to app defaults so preset switches do not leave stale state.
- **`updateGamepadLabels`**: if no left-thumb modules at all, omit or neutralize `stick_*` label keys used only for the left ring.
- Stick dialog rollback / live split sliders: guard `findModuleById("stick_left") == null`.

### 5. Tests and changelog

- [`GamepadLayoutPresetDocumentTest`](app/src/test/java/com/openterface/keymod/gamepad/GamepadLayoutPresetDocumentTest.java): valid document with **only** `button_a`; valid with **only** `stick_left_2`; invalid cases unchanged.
- Optional: editor test for add order (first → `stick_left`, second → `stick_left_2`).
- **[`CHANGELOG.md`](CHANGELOG.md)** Unreleased: **breaking** — left D-pad/stick optional; `stick_left` removable; add flow refills primary id when empty; prefs keys cleared when no left thumb.

## Out of scope

- Numeric-only id migration (`stick_left_1` everywhere) unless requested later.

## Risks (accepted)

- Installs or external JSON that assumed `stick_left` always present may fail validation until fixed upstream—acceptable per breaking-change policy.
