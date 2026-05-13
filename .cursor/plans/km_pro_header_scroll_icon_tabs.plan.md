# KM Pro header: scrollable icon tabs + fixed right cluster

## Goals

1. **Match KM Basic behavior**: submode strip sits in a **weighted** horizontal region so tabs can scroll left/right when space is tight; **Target OS**, **KM Pro setup**, and **connection** stay **fixed** at the top-right (siblings outside the scroller), same structural pattern as [km_basic_host_chrome.xml](app/src/main/res/layout/km_basic_host_chrome.xml).
2. **Icon submode tabs (KM Pro only)**: replace text labels with icons sourced from the user’s vectors:
   - Keyboard → `keyboard_24px.xml`
   - NumPad → `calculate_24px.xml`
   - Compose → `edit_24px.xml`

## Assets

- **Source** (user machine): `/Users/billywang/Downloads/keyboard_24px.xml`, `calculate_24px.xml`, `edit_24px.xml` (Material-style vectors, 24dp, `viewport` 960).
- **Project**: copy into [app/src/main/res/drawable/](app/src/main/res/drawable/) with resource-safe names, e.g. `ic_km_pro_submode_keyboard.xml`, `ic_km_pro_submode_numpad.xml`, `ic_km_pro_submode_compose.xml`.
- **Tint note**: those files use `android:tint="?attr/colorControlNormal"` and paths with `fillColor="@android:color/white"`. When aligning with the rest of the header, prefer **one** tint strategy (e.g. `ImageButton` + `@color/text_primary` / `@color/text_secondary` like other header icons, and simplify the vector if double-tinting looks wrong).

## Layout ([activity_main.xml](app/src/main/res/layout/activity_main.xml))

- In the row `toEndOf` the menu button:
  - Set `km_pro_header_tabs_scroll` to **`layout_width="0dp"`** + **`layout_weight="1"`** (and `fillViewport="true"`, `overScrollMode="never"`, `scrollbars="none"`) like Basic’s `HorizontalScrollView`.
  - Keep **`header_right_cluster`** immediately after the scroller (not inside it) so icons stay pinned to the trailing edge.
  - Remove or repurpose the current **weighted `app_title` spacer** between tabs and the right cluster so it does not steal horizontal space from the tab scroller when KM Pro tabs are visible (title may stay hidden or move depending on current product rules; implementation should avoid a large empty gap between tabs and right icons).

## Tab controls: text → icons

- Replace each KM Pro tab `TextView` using `@style/KbMouseSubmodeTab` with an **`ImageButton`** (or `AppCompatImageButton`) per tab:
  - `android:src` → the new drawable per submode.
  - Reuse **`@drawable/nav_item_background_selector`** (or equivalent) for selected/pressed chrome so `setSelected(true/false)` in [MainActivity.syncKmProHeaderTabSelectionUi()](app/src/main/java/com/openterface/keymod/MainActivity.java) still drives highlight.
  - Set **`contentDescription`** to existing strings (`@string/kb_mouse_sub_keyboard`, numpad, compose) for TalkBack parity with the old text tabs.
  - Match tap target size to the row (similar min height/padding to current tab style or header icon size for consistency).

## Code ([MainActivity.java](app/src/main/java/com/openterface/keymod/MainActivity.java))

- Change fields `kmProHeaderTabKeyboard|Numpad|Compose` from `TextView` to **`ImageButton`** (or a common supertype like `View` if preferred).
- **`setupKmProHeaderSubmodeTabs`** / **`syncKmProHeaderTabSelectionUi`**: keep logic the same; optionally **refresh icon tint** on selection if the background selector alone is not enough (mirror `updateKmProHeaderSetupChrome` tint pattern if needed).

## Validation

- Narrow screen / large font: **horizontal scroll** on the three icon tabs; right cluster **does not move** with scroll.
- Selection highlight and persisted submode (`KmProSubmodePrefs`) unchanged.
- Accessibility: each icon tab has correct **contentDescription**.

## Implementation todos

- [ ] Copy the three vector XMLs into `app/src/main/res/drawable/` and normalize tint/fill for app theme.
- [ ] Refactor KM Pro header row: weighted `HorizontalScrollView` + fixed `header_right_cluster`; resolve title spacer vs tab visibility.
- [ ] Swap tab `TextView`s for `ImageButton`s with icons + content descriptions + selection background.
- [ ] Update `MainActivity` field types and any tint/sync code; build and smoke-test KM Pro.
