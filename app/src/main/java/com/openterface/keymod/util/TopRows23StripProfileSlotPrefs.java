package com.openterface.keymod.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.openterface.keymod.preset.Rows23StripProfileConstants;
import com.openterface.keymod.preset.Rows23StripProfileManager;

/**
 * Persists which Rows 2–3 strip profile id is bound to each of three quick-toggle slots on
 * fixed strip page 3 (row 3). Independent of {@link TopShortcutProfileSlotPrefs} (Row 1 app profiles).
 */
public final class TopRows23StripProfileSlotPrefs {

    private static final String PREFS_NAME = "TopRows23StripProfileSlotPrefs";
    private static final String KEY_PREFIX = "strip_profile_slot_";

    private TopRows23StripProfileSlotPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String prefKey(int slotIndex1Based) {
        return KEY_PREFIX + slotIndex1Based;
    }

    /** Default strip profile id for slot 1..3 (left-to-right on page 3 row 3). */
    @NonNull
    public static String defaultStripProfileIdForSlot(int slotIndex1Based) {
        switch (slotIndex1Based) {
            case 1:
                return Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
            case 2:
                return Rows23StripProfileConstants.SYMBOLS_PROFILE_ID;
            case 3:
                return Rows23StripProfileConstants.MATH_PROFILE_ID;
            default:
                return Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
        }
    }

    @NonNull
    public static String getStripProfileIdForSlot(Context context, int slotIndex1Based) {
        if (slotIndex1Based < 1 || slotIndex1Based > 3) {
            return Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
        }
        String def = defaultStripProfileIdForSlot(slotIndex1Based);
        return prefs(context).getString(prefKey(slotIndex1Based), def);
    }

    /**
     * Returns stored id if that strip profile exists in {@code mgr}; otherwise the slot default id.
     */
    @NonNull
    public static String getResolvedStripProfileIdForSlot(
            Context context,
            int slotIndex1Based,
            Rows23StripProfileManager mgr
    ) {
        String raw = getStripProfileIdForSlot(context, slotIndex1Based);
        if (mgr != null && mgr.getProfileById(raw) != null) {
            return raw;
        }
        return defaultStripProfileIdForSlot(slotIndex1Based);
    }

    public static void setStripProfileIdForSlot(Context context, int slotIndex1Based, String profileId) {
        if (slotIndex1Based < 1 || slotIndex1Based > 3 || profileId == null) {
            return;
        }
        prefs(context).edit().putString(prefKey(slotIndex1Based), profileId).apply();
    }
}
