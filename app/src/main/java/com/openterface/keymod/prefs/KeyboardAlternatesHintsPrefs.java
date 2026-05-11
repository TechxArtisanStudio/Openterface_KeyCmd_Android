package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Long-press alternate hints on letter keys vs hold-to-repeat (gaming), stored in {@code AppPrefs}.
 * Synced with Fn + main Shift in Pro and with Keyboard and Mouse Pro setup.
 */
public final class KeyboardAlternatesHintsPrefs {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_ENABLED = "keyboard_alternates_hints_enabled";

    private KeyboardAlternatesHintsPrefs() {
    }

    public static boolean read(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    public static void write(Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
