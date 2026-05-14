package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * When on (default), KM Pro embedded Compose keeps the long-text buffer in memory while you switch
 * to Keyboard or NumPad (or other paths that remove the compose fragment).
 */
public final class KmProComposeDraftRetentionPrefs {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_ENABLED = "km_pro_compose_draft_retention_enabled";

    private KmProComposeDraftRetentionPrefs() {}

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
