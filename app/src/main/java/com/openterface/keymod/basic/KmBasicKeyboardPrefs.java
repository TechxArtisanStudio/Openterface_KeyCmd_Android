package com.openterface.keymod.basic;

import android.content.Context;

import androidx.preference.PreferenceManager;

/**
 * KM Basic full keyboard modifier behavior (see Settings → Keyboard &amp; Mouse).
 */
public final class KmBasicKeyboardPrefs {

    public static final String PREF_KEY = "km_basic_modifier_behavior";
    public static final String VALUE_STICKY = "sticky";
    public static final String VALUE_MOMENTARY_CHORD = "momentary_chord";

    /** Default for new installs and when the preference has never been set. */
    public static final String PREF_DEFAULT_VALUE = VALUE_MOMENTARY_CHORD;

    /**
     * When chord mode is on: long-press sends a real modifier-down to the target and tapKey
     * re-applies modifier-down after each key release so chording works across multiple keys.
     */
    public static final String PREF_CHORD_SUSTAIN_HID = "km_basic_chord_sustain_hid";

    /**
     * Full-keyboard keys (letters, Space, arrows, etc.): repeat tap cycles vs one HID key-down until release.
     */
    public static final String PREF_LONG_PRESS_BEHAVIOR = "km_basic_long_press_behavior";
    public static final String VALUE_LONG_PRESS_REPEAT = "repeat";
    public static final String VALUE_LONG_PRESS_HOLD = "hold";

    public static final String PREF_LONG_PRESS_DEFAULT = VALUE_LONG_PRESS_REPEAT;

    private KmBasicKeyboardPrefs() {}

    public static boolean isMomentaryChordMode(Context context) {
        return VALUE_MOMENTARY_CHORD.equals(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_KEY, PREF_DEFAULT_VALUE));
    }

    public static boolean isChordSustainHidEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_CHORD_SUSTAIN_HID, true);
    }

    /** One HID key-down until finger lifts (games / OS repeat). */
    public static boolean isLongPressSustainedHoldMode(Context context) {
        return VALUE_LONG_PRESS_HOLD.equals(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_LONG_PRESS_BEHAVIOR, PREF_LONG_PRESS_DEFAULT));
    }
}
