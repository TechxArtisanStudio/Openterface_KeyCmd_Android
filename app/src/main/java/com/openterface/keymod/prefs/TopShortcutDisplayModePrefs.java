package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Row-1 shortcut strip cap style (names, icons, or chord text), stored in {@code AppPrefs}.
 * Kept in sync with the DISPLAY key on the keyboard strip and Keyboard and Mouse Pro setup.
 */
public final class TopShortcutDisplayModePrefs {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_MODE = "top_shortcut_display_mode";
    /** Legacy boolean; migrated once to {@link #KEY_MODE}. */
    private static final String KEY_LEGACY_SHOW_ACTION_LABELS = "top_shortcut_show_action_labels";

    /** Show shortcut names on eligible strip keys. */
    public static final int MODE_NAME = 0;
    /** Icon-first display. */
    public static final int MODE_ICON = 1;
    /** Combo / chord text (e.g. Alt+X). */
    public static final int MODE_CHORD = 2;

    private TopShortcutDisplayModePrefs() {
    }

    public static int clamp(int mode) {
        if (mode < MODE_NAME || mode > MODE_CHORD) {
            return MODE_ICON;
        }
        return mode;
    }

    /**
     * Reads the current mode, running one-time migration from the legacy boolean pref if needed.
     */
    public static int readMode(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_MODE)) {
            boolean legacyOn = sp.getBoolean(KEY_LEGACY_SHOW_ACTION_LABELS, false);
            int migrated = legacyOn ? MODE_NAME : MODE_ICON;
            sp.edit()
                    .putInt(KEY_MODE, migrated)
                    .remove(KEY_LEGACY_SHOW_ACTION_LABELS)
                    .apply();
        }
        return sp.getInt(KEY_MODE, MODE_ICON);
    }

    public static void writeMode(Context context, int mode) {
        int n = clamp(mode);
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MODE, n)
                .apply();
    }
}
