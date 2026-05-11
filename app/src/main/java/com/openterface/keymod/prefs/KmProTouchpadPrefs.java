package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Keyboard &amp; Mouse Pro: touchpad chrome mode (gestures-only vs strip vs hybrid). Stored in
 * {@code AppPrefs} alongside other KM Pro settings.
 */
public final class KmProTouchpadPrefs {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_MODE = "km_pro_touchpad_mode";

    /** Legacy default: no mouse key strip. */
    public static final int MODE_GESTURES_ONLY = 0;
    /** Pad gestures + L/M/R strip and hold-lock, Basic parity (no gesture→key visual sync). */
    public static final int MODE_MOUSE_KEYS_BASIC = 1;
    /** Same as basic-like plus aligned gesture feedback on L/M/R views. */
    public static final int MODE_HYBRID = 2;

    private KmProTouchpadPrefs() {
    }

    public static int clampMode(int mode) {
        if (mode < MODE_GESTURES_ONLY || mode > MODE_HYBRID) {
            return MODE_GESTURES_ONLY;
        }
        return mode;
    }

    public static int readMode(Context context) {
        SharedPreferences sp =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return clampMode(sp.getInt(KEY_MODE, MODE_GESTURES_ONLY));
    }

    public static void writeMode(Context context, int mode) {
        int m = clampMode(mode);
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MODE, m)
                .apply();
    }

    public static boolean isHybridMode(Context context) {
        return readMode(context) == MODE_HYBRID;
    }

    public static boolean showsMouseKeyStrip(Context context) {
        int m = readMode(context);
        return m == MODE_MOUSE_KEYS_BASIC || m == MODE_HYBRID;
    }

    /**
     * {@code true} for "Pad + mouse keys" only: pointer move and two-finger scroll on the pad;
     * clicks and drag come from the L/M/R strip, not pad tap/long-press/two-finger-right.
     */
    public static boolean isPadPlusMouseKeysNoTouchClickGestures(Context context) {
        return readMode(context) == MODE_MOUSE_KEYS_BASIC;
    }
}
