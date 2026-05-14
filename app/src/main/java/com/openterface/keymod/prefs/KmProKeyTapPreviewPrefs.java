package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * KM Pro built-in keyboard: floating tap preview (Basic-style bubble), stored in {@code AppPrefs}.
 * Defaults to {@code true} when the preference has never been written (first install).
 */
public final class KmProKeyTapPreviewPrefs {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_ENABLED = "km_pro_key_tap_preview_enabled";

    private KmProKeyTapPreviewPrefs() {}

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
