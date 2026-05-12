# Theme colors and Keyboard & Mouse (Basic + Pro)

This app uses **View + XML** theming (Material 3 `Theme.KeyMod.*`), not Jetpack Compose.

## Canonical sources

| Layer | Location |
|--------|----------|
| Semantic colors (surfaces, text, keyboard caps, touchpad, etc.) | `app/src/main/res/values/colors.xml` with **night overrides** in `values-night/colors.xml` |
| Accent families (orange, blue, …) | `theme_accent_*` in `colors.xml`; wired into Material via `app/src/main/res/values/themes.xml` |
| Runtime accent + day/night | `ThemeManager` (`applyTheme`, `getColorPrimary`, `getColorPrimaryContainer`, `resolveThemeColor`) |
| KM Basic key label / hint / icon tints (Material state lists) | `app/src/main/res/color/basic_key_label_color.xml`, `basic_key_hint_color.xml`, `basic_key_icon_tint.xml` |

## How to pick a color in UI

1. **Surfaces and body text** — use semantic resources so light/dark stay in sync, e.g. `@color/background_light`, `@color/text_primary`, `@color/text_secondary`, `@color/card_background`, `@color/header_background`, `@color/touchpad_background`.
2. **Accent / brand actions** — use theme attributes where the widget is theme-wrapped, e.g. `?attr/colorPrimary`, `?attr/colorPrimaryContainer`, or `ThemeManager.getColorPrimary(context)` when the `Context` may not inherit the activity theme (custom keyboard subviews).
3. **KM Basic key labels** — use `@color/basic_key_label_color` (ColorStateList: `colorOnSurface` / `colorOnPrimaryContainer` when pressed). Hints: `@color/basic_key_hint_color`; icon-only keys and arrow cluster: `@color/basic_key_icon_tint`.
4. **Standard key idle fill** — `@drawable/key_background` uses `@color/key_bg_normal` for the idle state (same family as numpad caps), avoiding Material3 `colorSurfaceContainerHighest`’s purple tint in light mode.
5. **Touchpad scroll strip chevrons** — use `@color/km_touchpad_scroll_strip_chevron` (shared by KM Basic, KM Pro, and gamepad preview). `km_basic_scroll_strip_chevron` remains an **alias** for compatibility.

## KM Basic–specific notes

- **Host shell** (`fragment_keyboard_mouse.xml`, `km_basic_host_chrome.xml`): page background `@color/background_light`; chrome row uses `@color/header_background` and `@dimen/header_elevation` so it reads like the main app header.
- **Keyboard / numpad / compose** (`fragment_basic_keyboard.xml`, `fragment_basic_numpad.xml`, `fragment_basic_compose.xml`, land variants): root **`@color/background_light`** (aligned with app shell, not raw `?android:attr/colorBackground` alone).
- **Touchpad** (`fragment_basic_touchpad.xml`): outer frame `@color/background_light`; top area **touchpad + vertical scroll strip** side by side on `@color/touchpad_background` (strip weight 1, pad weight 5); **L/M/R row below** with vertical height ratio 5:2 vs pad area; bottom row uses `@dimen/basic_touchpad_mouse_column_horizontal_padding` for left/right inset.
- **KM Basic settings** (`fragment_settings_keyboard_mouse.xml`): same pattern as other settings—semantic text + **`app:cardBackgroundColor="@color/card_background"`** on cards.
- **Compose toolbar** (`BasicComposeFragment`): `MaterialColors` reads use **`ThemeManager`** / semantic color fallbacks so stroke and icon tints track the selected accent family.
- **Hold-lock popup** (`BasicHoldLockPopup`): accent tint fallbacks use **`ThemeManager.getColorPrimary`** instead of the legacy `@color/primary` orange.

## KM Pro–specific notes

- **KM Pro settings** (`fragment_km_pro_settings.xml`): surfaces and text use semantic colors; elevated cards use `@color/card_background`.
- **Pro touchpad mouse row** (`include_pro_touchpad_mouse_keys.xml`): `@style/Widget.KeyMod.ProTouchpadMouseButton` (inherits Basic numpad/mouse styling).
- **Programmatic keyboard chrome** (`CustomKeyboardView`): default label/icon tint follows `@color/text_primary`; accent-dependent bits use `MaterialColors` with fallbacks from `ThemeManager`.

Avoid hard-coded `0xFF…` text colors in new code; prefer `@color/text_primary` / theme attrs or `ThemeManager` helpers.
