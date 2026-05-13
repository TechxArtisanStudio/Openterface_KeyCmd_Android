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
    private static final String KEY_SCROLL_STRIP_ENABLED = "km_pro_touchpad_scroll_strip_enabled";
    private static final String KEY_STRIP_SCROLL_SENSITIVITY = "km_pro_touchpad_strip_scroll_sensitivity";
    private static final String KEY_GESTURE_STATUS_VISIBLE = "km_pro_touchpad_gesture_status_visible";

    /** Same percent range as KM Basic strip scroll sensitivity. */
    public static final int STRIP_SCROLL_SENSITIVITY_MIN_PERCENT = 20;
    public static final int STRIP_SCROLL_SENSITIVITY_MAX_PERCENT = 200;
    public static final int STRIP_SCROLL_SENSITIVITY_DEFAULT_PERCENT = 100;

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

    /** Default on so composite touchpad matches KM Basic’s always-visible wheel strip unless disabled. */
    public static boolean isScrollStripEnabled(Context context) {
        SharedPreferences sp =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_SCROLL_STRIP_ENABLED, true);
    }

    public static void writeScrollStripEnabled(Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SCROLL_STRIP_ENABLED, enabled)
                .apply();
    }

    public static int getStripScrollSensitivityPercent(Context context) {
        SharedPreferences sp =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int v =
                sp.getInt(
                        KEY_STRIP_SCROLL_SENSITIVITY, STRIP_SCROLL_SENSITIVITY_DEFAULT_PERCENT);
        return Math.max(
                STRIP_SCROLL_SENSITIVITY_MIN_PERCENT,
                Math.min(STRIP_SCROLL_SENSITIVITY_MAX_PERCENT, v));
    }

    public static void writeStripScrollSensitivityPercent(Context context, int percent) {
        int p =
                Math.max(
                        STRIP_SCROLL_SENSITIVITY_MIN_PERCENT,
                        Math.min(STRIP_SCROLL_SENSITIVITY_MAX_PERCENT, percent));
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_STRIP_SCROLL_SENSITIVITY, p)
                .apply();
    }

    /** When {@code false}, the composite touchpad hides the compact gesture / button status line. */
    public static boolean isGestureStatusLineVisible(Context context) {
        SharedPreferences sp =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_GESTURE_STATUS_VISIBLE, true);
    }

    public static void writeGestureStatusLineVisible(Context context, boolean visible) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_GESTURE_STATUS_VISIBLE, visible)
                .apply();
    }
}
