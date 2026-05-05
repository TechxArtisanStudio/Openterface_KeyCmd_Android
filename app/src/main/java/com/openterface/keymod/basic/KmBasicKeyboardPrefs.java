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

    private KmBasicKeyboardPrefs() {}

    public static boolean isMomentaryChordMode(Context context) {
        return VALUE_MOMENTARY_CHORD.equals(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(PREF_KEY, PREF_DEFAULT_VALUE));
    }
}
