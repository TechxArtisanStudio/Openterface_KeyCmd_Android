---
name: Gamepad layer ordering UX
overview: Stacking is already represented in the gamepad preset schema as per-module `zIndex` and is honored for draw order and touch hit-testing. The work is to expose **Bring to front** / **Send to back** in layout edit flows, centralize z mutations, and keep exports/presets consistent—no new JSON field is required.
todos:
  - id: layer-helpers
    content: Add GamepadLayoutDocEditor.bringModuleToFront / sendModuleToBack (and optional normalize) using min/max zIndex across doc.modules
    status: pending
  - id: long-press-menu
    content: Extend showLongPressMenu + handleLongPressMenuChoice with two new options when modules > 1; applyLayoutDocFromMemory after z change
    status: pending
  - id: sheets-touchpad
    content: Add Layer row (two buttons) to mouse button + scroll strip sheets; optionally touchpad resize dialog for parity
    status: pending
  - id: strings-tests
    content: Add string resources; unit tests for layer helpers and export round-trip (zIndex preserved)
    status: pending
isProject: false
---

# Gamepad module layer ordering (Bring to front / Send to back)

## What already exists (no reinventing the wheel)

- **Schema**: [`GamepadLayoutPresetDocument.GamepadModule`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutPresetDocument.java) already has `public int zIndex` (see class javadoc: “Layers”). Bundled JSON such as [`gamepad/default.json`](Openterface_KeyMod_Android/gamepad/default.json) sets `zIndex` per module.
- **Draw order**: [`GamepadDynamicLayoutRegistry.drawSortedModules`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/render/GamepadDynamicLayoutRegistry.java) sorts modules by `zIndex` ascending (lower = drawn earlier = visually behind).
- **Play-mode hit-testing**: During draw, each module appends its id to `dynamicHitTestOrder` in that same sorted order; [`GamepadView.getComponentAt`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/GamepadView.java) walks that list **from the end**, so the topmost drawn module wins when rects overlap (lines ~3138–3147).
- **Edit-mode hit-testing**: When `isEditMode` and rects overlap, `getComponentAt` explicitly sorts by `zIndex` and picks the top hit (lines ~3123–3136), so editing already respects stacking for drag targeting.
- **New modules**: [`GamepadLayoutDocEditor.nextZ`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutDocEditor.java) assigns `max(zIndex)+1` for added/duplicated modules.

**Conclusion:** Supporting the schema “properly” means **reading/writing existing `zIndex`** through the same paths as other module edits (`layoutDoc` → `applyLayoutDocFromMemory()` / preset persistence). A **schema version bump is optional** and only needed if you add validation rules (e.g. uniqueness) or documentation churn you want versioned—not for storing integers.

---

## Semantics for the two actions

Implement deterministic helpers (best home: [`GamepadLayoutDocEditor`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutDocEditor.java) next to `nextZ`):

| Action | Effect on chosen module `m` |
|--------|------------------------------|
| **Bring to front** | `m.zIndex = max(all modules' zIndex) + 1` |
| **Send to back** | `m.zIndex = min(all modules' zIndex) - 1` |

- **Ties**: If several modules share the same `zIndex`, Java’s stable sort preserves list order; after one “bring to front”, that module is strictly on top. Optionally add a follow-up **“Normalize layer order”** (reassign `0..n-1` in current sort order) for cleaner exported JSON—nice-to-have, not required for correctness.
- **Single module**: Hide both actions or no-op (nothing to reorder).

---

## UX in layout editing mode

**Entry points** (all already gated on edit mode for long-press; config chips are edit-only):

1. **List dialog from long-press** — [`GamepadFragment.showLongPressMenu`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java) builds `opts` then `MaterialAlertDialogBuilder.setItems`. Add two entries (e.g. after key/stick config, before Remove): **Bring to front**, **Send to back**, whenever `layoutDoc.modules.size() > 1` and the resolved `moduleId` is a real top-level module (same `moduleId` used today). Wire through [`handleLongPressMenuChoice`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java) to call the helpers + `applyLayoutDocFromMemory()` so the canvas updates immediately.

2. **Module config sheets** (they bypass the list dialog) — add a compact **“Layer”** row (two `MaterialButton`s or outlined buttons) with the same actions:
   - [`showMouseButtonModuleConfigSheet`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java)
   - [`showScrollStripModuleConfigSheet`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java)
   - Optionally the touchpad resize flow ([`showTouchpadResizeDialog`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java)) if you want parity without only relying on the long-press list for touchpads.

3. **Micro-feedback**: Light haptic on success + optional short `Toast` only when already at extreme (e.g. “Already on top”) to avoid noise; otherwise rely on **immediate visual reorder** after `syncGamepadViewFromDoc()`.

4. **Copy / a11y**: New strings in `strings.xml` (and content descriptions if buttons are icon-only). Use clear wording: “Bring to front (draw on top)” is optional longer subtitle in a tooltip/help doc, not necessarily in the menu title.

5. **Remove action**: No change to Remove ordering logic; `GamepadLayoutDocEditor.removeModule` already manipulates the list—orphan `zIndex` gaps are fine.

---

## Implementation checklist (files)

| Area | File(s) |
|------|---------|
| Core z mutations | [`GamepadLayoutDocEditor.java`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/gamepad/GamepadLayoutDocEditor.java) — `bringModuleToFront`, `sendModuleToBack` (+ optional `normalizeModuleZOrder`) |
| Menu + sheets | [`GamepadFragment.java`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/fragment/GamepadFragment.java) — `showLongPressMenu`, `handleLongPressMenuChoice`, sheet builders |
| Strings | `app/src/main/res/values/strings.xml` (and `values-zh-rCN` etc. if you maintain locales) |
| Tests | [`GamepadLayoutPresetDocumentTest.java`](Openterface_KeyMod_Android/app/src/test/java/com/openterface/keymod/gamepad/GamepadLayoutPresetDocumentTest.java) or a small new test class for `GamepadLayoutDocEditor` layer helpers (stacking invariants, single-module no-op) |

**No changes required** to [`GamepadView`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/GamepadView.java) draw/hit logic if you only mutate `zIndex` on the document and refresh the view—unless you later want play-mode `getComponentAt` to always use z-sorted iteration instead of `dynamicHitTestOrder` (functionally equivalent today because order mirrors sorted draw).

---

## Optional follow-ups (out of scope unless you want them)

- **Normalize z on export** for minimal diffs when sharing JSON.
- **Validation**: warn on duplicate `zIndex` in dev tools only (strict validation could break legacy presets).
