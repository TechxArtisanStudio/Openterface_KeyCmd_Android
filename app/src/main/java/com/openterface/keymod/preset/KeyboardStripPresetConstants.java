package com.openterface.keymod.preset;

/**
 * Shared constants for keyboard strip presets (rows 2–3) and the reserved strip profile.
 */
public final class KeyboardStripPresetConstants {

    private KeyboardStripPresetConstants() {
    }

    public static final String FORMAT = "openterface_keymod_keyboard_preset";
    public static final int SCHEMA_VERSION = 1;

    /** Reserved Shortcut Hub profile id for fixed rows 2–3 (independent of active app profile). */
    public static final String STRIP_PROFILE_ID = "keyboard_strip";

    public static final int TOP_PANEL_COLUMNS = 7;
    /** Fixed strip swipe pages: F-row (0), modifiers/nav (1), Shortcut Hub punctuation (2). */
    public static final int FIXED_ROW_BUILTIN_PAGES = 3;
    public static final int FIXED_ROWS_PER_BUILTIN_PAGE = 2;
    public static final int FAVORITES_TRAILING_SYSTEM_SLOTS = 2;

    public static final String BUNDLE_THIN = "thin";
    public static final String BUNDLE_PORTABLE = "portable";
}
