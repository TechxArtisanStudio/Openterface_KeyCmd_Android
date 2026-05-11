package com.openterface.keymod.gamepad;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Resolves effective hold/turbo gesture timing for the gamepad: optional {@link
 * GamepadLayoutPresetDocument.LayoutGlobals} overrides beat SharedPreferences defaults.
 */
public final class GamepadGestureLockSensitivity {

    public static final int MIN_PRESS_MS_DEFAULT = 0;
    public static final int MIN_PRESS_MS_MAX = 1000;
    public static final float RADIUS_SCALE_DEFAULT = 1f;
    public static final float RADIUS_SCALE_MIN = 0.5f;
    public static final float RADIUS_SCALE_MAX = 3f;

    /** Default delay (ms) between turbo on/off half-steps; matches legacy hardcoded behavior. */
    public static final int TURBO_PULSE_PERIOD_MS_DEFAULT = 70;
    public static final int TURBO_PULSE_PERIOD_MS_MIN = 25;
    public static final int TURBO_PULSE_PERIOD_MS_MAX = 300;

    private GamepadGestureLockSensitivity() {}

    public static int clampMinPressMs(int ms) {
        if (ms < 0) {
            return 0;
        }
        if (ms > MIN_PRESS_MS_MAX) {
            return MIN_PRESS_MS_MAX;
        }
        return ms;
    }

    public static float clampRadiusScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            return RADIUS_SCALE_DEFAULT;
        }
        if (scale < RADIUS_SCALE_MIN) {
            return RADIUS_SCALE_MIN;
        }
        if (scale > RADIUS_SCALE_MAX) {
            return RADIUS_SCALE_MAX;
        }
        return scale;
    }

    /** Preset {@code layout.gestureLockMinPressMs} when set, else {@link GamepadPreferenceKeys} int pref. */
    public static int resolveMinPressMs(
            @Nullable GamepadLayoutPresetDocument doc, @Nullable SharedPreferences prefs) {
        if (doc != null && doc.layout != null && doc.layout.gestureLockMinPressMs != null) {
            return clampMinPressMs(doc.layout.gestureLockMinPressMs);
        }
        if (prefs != null) {
            return clampMinPressMs(prefs.getInt(GamepadPreferenceKeys.GESTURE_LOCK_MIN_PRESS_MS, MIN_PRESS_MS_DEFAULT));
        }
        return MIN_PRESS_MS_DEFAULT;
    }

    /** Preset {@code layout.gestureLockDiagonalRadiusScale} when set, else float pref (default 1). */
    public static float resolveRadiusScale(
            @Nullable GamepadLayoutPresetDocument doc, @Nullable SharedPreferences prefs) {
        if (doc != null && doc.layout != null && doc.layout.gestureLockDiagonalRadiusScale != null) {
            return clampRadiusScale(doc.layout.gestureLockDiagonalRadiusScale);
        }
        if (prefs != null) {
            return clampRadiusScale(
                    prefs.getFloat(
                            GamepadPreferenceKeys.GESTURE_LOCK_DIAGONAL_RADIUS_SCALE, RADIUS_SCALE_DEFAULT));
        }
        return RADIUS_SCALE_DEFAULT;
    }

    public static int clampTurboPulsePeriodMs(int ms) {
        if (ms < TURBO_PULSE_PERIOD_MS_MIN) {
            return TURBO_PULSE_PERIOD_MS_MIN;
        }
        if (ms > TURBO_PULSE_PERIOD_MS_MAX) {
            return TURBO_PULSE_PERIOD_MS_MAX;
        }
        return ms;
    }

    /** Preset {@code layout.turboPulsePeriodMs} when set, else int pref (default {@link #TURBO_PULSE_PERIOD_MS_DEFAULT}). */
    public static int resolveTurboPulsePeriodMs(
            @Nullable GamepadLayoutPresetDocument doc, @Nullable SharedPreferences prefs) {
        if (doc != null && doc.layout != null && doc.layout.turboPulsePeriodMs != null) {
            return clampTurboPulsePeriodMs(doc.layout.turboPulsePeriodMs);
        }
        if (prefs != null) {
            return clampTurboPulsePeriodMs(
                    prefs.getInt(
                            GamepadPreferenceKeys.TURBO_PULSE_PERIOD_MS, TURBO_PULSE_PERIOD_MS_DEFAULT));
        }
        return TURBO_PULSE_PERIOD_MS_DEFAULT;
    }
}
