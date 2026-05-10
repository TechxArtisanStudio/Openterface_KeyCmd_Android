package com.openterface.keymod.basic;

import android.content.Context;

import androidx.preference.PreferenceManager;

/**
 * KM Basic touchpad preferences (Keyboard &amp; Mouse (Basic) → Setup tab).
 */
public final class KmBasicTouchpadPrefs {

    /** Percent 20–200; default 100 = 1.0× (same scale as General → touchpad two-finger scroll). */
    public static final String PREF_STRIP_SCROLL_SENSITIVITY = "basic_touchpad_strip_scroll_sensitivity";

    public static final int STRIP_SCROLL_SENSITIVITY_MIN_PERCENT = 20;
    public static final int STRIP_SCROLL_SENSITIVITY_MAX_PERCENT = 200;
    public static final int STRIP_SCROLL_SENSITIVITY_DEFAULT_PERCENT = 100;

    private KmBasicTouchpadPrefs() {}

    public static int getStripScrollSensitivityPercent(Context context) {
        int v =
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getInt(PREF_STRIP_SCROLL_SENSITIVITY, STRIP_SCROLL_SENSITIVITY_DEFAULT_PERCENT);
        return Math.max(
                STRIP_SCROLL_SENSITIVITY_MIN_PERCENT,
                Math.min(STRIP_SCROLL_SENSITIVITY_MAX_PERCENT, v));
    }
}
