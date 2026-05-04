package com.openterface.keymod.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.openterface.keymod.ShortcutProfileManager;

/**
 * Persists which Shortcut Hub profile id is bound to each of the seven slots (shown on
 * fixed strip page 3 row 2 for slots 1–3). Defaults match bundled profiles in {@link ShortcutProfileManager}.
 */
public final class TopShortcutProfileSlotPrefs {

    private static final String PREFS_NAME = "TopShortcutProfileSlotPrefs";
    private static final String KEY_PREFIX = "profile_slot_";

    private TopShortcutProfileSlotPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String prefKey(int slotIndex1Based) {
        return KEY_PREFIX + slotIndex1Based;
    }

    /** Default profile id for slot 1..7 (col order). */
    @NonNull
    public static String defaultProfileIdForSlot(int slotIndex1Based) {
        switch (slotIndex1Based) {
            case 1:
                return "default";
            case 2:
                return "kicad";
            case 3:
                return "blender";
            case 4:
                return "fusion360";
            case 5:
                return "vscode";
            case 6:
                return "photoshop";
            case 7:
                return "nomad";
            default:
                return "default";
        }
    }

    public static String getProfileIdForSlot(Context context, int slotIndex1Based) {
        if (slotIndex1Based < 1 || slotIndex1Based > 7) {
            return "default";
        }
        String def = defaultProfileIdForSlot(slotIndex1Based);
        return prefs(context).getString(prefKey(slotIndex1Based), def);
    }

    /**
     * Returns stored id if that profile exists in {@code mgr}; otherwise the slot default id.
     */
    public static String getResolvedProfileIdForSlot(
            Context context,
            int slotIndex1Based,
            ShortcutProfileManager mgr
    ) {
        String raw = getProfileIdForSlot(context, slotIndex1Based);
        if (mgr != null && mgr.getProfileById(raw) != null) {
            return raw;
        }
        return defaultProfileIdForSlot(slotIndex1Based);
    }

    public static void setProfileIdForSlot(Context context, int slotIndex1Based, String profileId) {
        if (slotIndex1Based < 1 || slotIndex1Based > 7 || profileId == null) {
            return;
        }
        prefs(context).edit().putString(prefKey(slotIndex1Based), profileId).apply();
    }
}
