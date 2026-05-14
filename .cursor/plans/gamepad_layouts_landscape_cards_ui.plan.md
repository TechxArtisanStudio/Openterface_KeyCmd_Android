---
name: Gamepad Layouts landscape cards UI
overview: Redesign the Gamepad Layouts picker around landscape-only use, replacing the dense icon+list pattern with a card grid where each layout shows a small preview image; includes UX patterns, preview generation strategy, and alignment with existing gamepad orientation lock.
todos:
  - id: ia-toolbar
    content: "Define information architecture: primary grid + persistent toolbar (New / Import / Reset) + per-card actions"
    status: completed
  - id: preview-pipeline
    content: Choose preview strategy (off-screen GamepadView/Canvas vs lightweight renderer); add disk cache keyed by preset id + content hash
    status: completed
  - id: grid-layout
    content: Implement RecyclerView GridLayoutManager with span lookup for width buckets; card layout XML + selected state
    status: completed
  - id: reorder-accessibility
    content: Design reorder without blocking scroll (edit mode or drag handle on card); verify TalkBack order and contrast
    status: completed
isProject: false
---

# Gamepad Layouts page — landscape-first card grid (UI/UX plan)

## Alignment with product reality

**Landscape-only in gamepad mode is already enforced** while `GamepadFragment` is resumed: `requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)` in [`GamepadFragment.onResume()`](app/src/main/java/com/openterface/fragment/GamepadFragment.java) (and cleared in `onPause()`). So the Layouts UI can **assume landscape metrics** whenever it is opened from gamepad mode. The picker is still a **dialog** over `MainActivity` (which uses `configChanges` including orientation); the redesign should **size the dialog for landscape width/height** (e.g. large fraction of screen or full-width band with max height), not maintain a portrait `bottom_sheet_gamepad_presets` mental model.

---

## Design goals (your direction, sharpened)

1. **Landscape-first** — Optimize columns, card aspect ratio, and chrome for **horizontal** space; drop portrait-specific layout variants for *this* surface (or keep a single responsive rule: “minimum N columns only achievable in landscape,” which matches gamepad-only entry).
2. **Card grid instead of list** — Each layout is a **recognizable unit**: preview + name + light metadata (e.g. built-in badge) + clear active state.
3. **Preview image per card** — Small **thumbnail** of the same geometry users see on the canvas (modules, rough positions), not a generic icon.

---

## Information architecture

Split the screen into **zones** so global actions are not buried in icon-only 48dp buttons:

| Zone | Role |
|------|------|
| **Header** | Title (“Layouts”), optional subtitle or count; **text-labeled** actions for **New from current**, **Import**, **Reset built-in** (destructive can stay text button or menu with confirmation). |
| **Primary content** | Scrollable **RecyclerView** with `GridLayoutManager`: **cards** in 2–4 columns depending on smallest width (e.g. `sw600dp` or width buckets). |
| **Active layout** | Visually distinct card (border, elevation, “Active” chip) **or** pin the active card to the top row — pick one pattern consistently. |

**Per-card actions** (avoid cluttering every card):

- **Tap card** — Activate layout (same as today: apply + dismiss picker), *or* activate without dismiss if you move to a full-screen browser pattern (heavier change).
- **Overflow / long-press** — Rename, duplicate, save, share, reset this layout, delete (if allowed) — same capabilities as current overflow menu.
- **Reorder** — Do **not** rely on a list-only drag handle hidden in a dense row. Options: (a) **“Reorder layouts”** mode toggles drag handles on cards; (b) long-press to drag (document for users); (c) dedicated reorder screen. Prefer (a) or (c) for lower accidental reorder.

---

## Card UX (solid defaults)

- **Aspect ratio** — Preview region **16:9 or match gamepad canvas aspect** so thumbnails look like the real play area; fixed preview height per row for a calm grid.
- **Preview** — Top ~60% of card; bottom: **title** (1–2 lines, ellipsize), optional **“Built-in”** pill for shipped presets.
- **States** — Default, **selected/active**, pressed, **disabled** (if any). Use Material 3 **tonal** or **outlined** cards; active = **filled border / primary container** so color is not the only cue (WCAG).
- **Empty / loading** — Shimmer or neutral placeholder while preview generates; **error** state if JSON invalid (icon + “Tap to repair” optional).

---

## Preview images — how to build them reliably

Rendering already exists: dynamic layouts draw through [`GamepadDynamicLayoutRegistry.drawSortedModules`](app/src/main/java/com/openterface/keymod/gamepad/render/GamepadDynamicLayoutRegistry.java) with module draw methods on [`GamepadView`](app/src/main/java/com/openterface/keymod/GamepadView.java). Practical approaches:

1. **Off-screen render (recommended first)**  
   - Load preset JSON → build `GamepadLayoutPresetDocument` → draw into a **Bitmap** via a small dedicated path: either a **minimal `GamepadView`** instance measured at a fixed size, or extract a **static “thumbnail renderer”** that calls the same draw methods with a stub `GamepadView` context (more refactor, cleaner long-term).  
   - Use a **fixed logical size** (e.g. 400×225) then scale down for the card to keep previews consistent across devices.

2. **Cache**  
   - Key: **preset id + content hash** (file mtime, hash of JSON, or store revision).  
   - Store under `context.getCacheDir()` or app files dir; invalidate on edit, import, reset built-in.  
   - Generate **off main thread** (Executor / Coroutine); bind to ViewHolder when ready.

3. **Fallback**  
   - If generation fails, show **placeholder** + layout name only.

**Caution:** `GamepadView` drawing may depend on theme, density, labels, background image — for thumbnails, force **neutral background** (or respect user background at lower opacity) and **non-edit** styling so previews match “play” not “edit”.

---

## Layout of the dialog / surface

- Prefer **wide dialog**: e.g. `MATCH_PARENT` width with horizontal margin, `WRAP_CONTENT` height capped at **~70–85%** of screen height so the grid scrolls inside a stable frame.  
- **Avoid** bottom-sheet gesture conflicts; keeping **AppCompatDialog** (or `DialogFragment`) without `BottomSheetBehavior` is still wise for a scrollable grid.  
- **Window layout** — Replace narrow “38% width” landscape band if it fights a multi-column grid; use **min width for 2 columns of cards** or full width with side padding.

---

## Performance and polish

- **DiffUtil** on preset list (keep current adapter pattern).  
- **RecyclerView** `setItemViewCacheSize` / stable ids if you introduce stable row ids.  
- **Throttle** preview regen when user rapidly imports or resets.  
- **RTL** — Grid and card padding must mirror.

---

## Implementation phases (suggested)

1. **Layout shell** — New dialog layout: header + `RecyclerView` + grid span by width; no previews yet (placeholder color).  
2. **Card adapter** — Bind name, built-in flag, active state; tap to activate.  
3. **Preview pipeline** — Bitmap generation + async + cache; wire into ViewHolder.  
4. **Actions** — Wire overflow menu, import, new preset, reset shipped (reuse existing `GamepadFragment` / repository logic).  
5. **Reorder** — Choose pattern and implement persistence (existing `reorderPresets`).  
6. **Remove** obsolete list row / narrow band dimens if unused; update strings for any new labels.

---

## Risks to manage

- **Thumbnail fidelity vs cost** — Full `GamepadView` per card in scroll is expensive; use **static bitmaps** in `ImageView`, not live views.  
- **Semantic clarity** — Text-labeled header actions fix the old “icon-only” discoverability problem.  
- **Reset built-in** — Keep the **confirmation dialog** copy; optionally move reset under a **“…”** overflow in the header to reduce accidental taps.

This document supersedes the earlier list-only UI review for planning purposes; implementation should follow this architecture unless scope is explicitly narrowed.
