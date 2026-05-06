package com.openterface.keymod.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.openterface.keymod.preset.Rows23StripProfileConstants;
import com.openterface.keymod.preset.Rows23StripProfileManager;

/**
 * Persists which Rows 2–3 strip profile id is bound to each of six quick-toggle slots on
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

    /**
     * Pref value for a page-3 strip quick-toggle slot with no profile bound (blank cap, tap no-op).
     */
    @NonNull
    public static final String STRIP_PROFILE_SLOT_UNASSIGNED = "";

    /** Default strip profile id for slot 1..6 (left-to-right on page 3 row 3). */
    @NonNull
    public static String defaultStripProfileIdForSlot(int slotIndex1Based) {
        switch (slotIndex1Based) {
            case 1:
                return Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
            case 2:
                return Rows23StripProfileConstants.PERSONAL_PROFILE_ID;
            case 3:
            case 4:
            case 5:
            case 6:
                return STRIP_PROFILE_SLOT_UNASSIGNED;
            default:
                return Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
        }
    }

    /**
     * Rewrites quick-toggle prefs that still reference removed themed strip profiles so slots
     * resolve to existing profiles (fresh-install defaults per slot).
     */
    public static void migrateRemovedThematicStripProfilePrefs(@NonNull Context context) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor ed = p.edit();
        boolean changed = false;
        for (int slot = 1; slot <= 6; slot++) {
            String key = prefKey(slot);
            String stored = p.getString(key, null);
            if (stored != null && Rows23StripProfileConstants.isLegacyRemovedThematicStripId(stored)) {
                ed.putString(key, defaultStripProfileIdForSlot(slot));
                changed = true;
            }
        }
        if (changed) {
            ed.apply();
        }
    }

    private static final String KEY_MIGRATE_SLOTS3456_CLEAR_STORED_DEFAULT =
            "migrate_strip_slots_3456_clear_stored_default_v1";

    /**
     * Older builds defaulted strip quick-toggle slots 3–6 to {@link Rows23StripProfileConstants#DEFAULT_PROFILE_ID}
     * and wrote that into prefs. Current product defaults leave those slots unassigned; clear a
     * stored {@code strip_default} for slots 3–6 once so column 3 (and 4–6) match fresh installs.
     */
    public static void migrateStripSlots3456StoredDefaultToUnassignedOnce(@NonNull Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_MIGRATE_SLOTS3456_CLEAR_STORED_DEFAULT, false)) {
            return;
        }
        SharedPreferences.Editor ed = p.edit();
        boolean changed = false;
        for (int slot = 3; slot <= 6; slot++) {
            String key = prefKey(slot);
            if (!p.contains(key)) {
                continue;
            }
            String stored = p.getString(key, "");
            if (Rows23StripProfileConstants.DEFAULT_PROFILE_ID.equals(stored)) {
                ed.putString(key, STRIP_PROFILE_SLOT_UNASSIGNED);
                changed = true;
            }
        }
        ed.putBoolean(KEY_MIGRATE_SLOTS3456_CLEAR_STORED_DEFAULT, true);
        ed.apply();
    }

    @NonNull
    public static String getStripProfileIdForSlot(Context context, int slotIndex1Based) {
        if (slotIndex1Based < 1 || slotIndex1Based > 6) {
            return Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
        }
        String def = defaultStripProfileIdForSlot(slotIndex1Based);
        return prefs(context).getString(prefKey(slotIndex1Based), def);
    }

    /**
     * Returns the profile id for this slot: unassigned ({@link #STRIP_PROFILE_SLOT_UNASSIGNED}),
     * a valid stored id, or the slot default when the stored id no longer exists.
     */
    @NonNull
    public static String getResolvedStripProfileIdForSlot(
            Context context,
            int slotIndex1Based,
            Rows23StripProfileManager mgr
    ) {
        String raw = getStripProfileIdForSlot(context, slotIndex1Based);
        if (raw == null || raw.trim().isEmpty()) {
            return STRIP_PROFILE_SLOT_UNASSIGNED;
        }
        if (Rows23StripProfileConstants.isLegacyRemovedThematicStripId(raw)) {
            return defaultStripProfileIdForSlot(slotIndex1Based);
        }
        if (mgr != null && mgr.getProfileById(raw) != null) {
            return raw;
        }
        return defaultStripProfileIdForSlot(slotIndex1Based);
    }

    public static void setStripProfileIdForSlot(Context context, int slotIndex1Based, String profileId) {
        if (slotIndex1Based < 1 || slotIndex1Based > 6) {
            return;
        }
        if (profileId == null) {
            return;
        }
        prefs(context).edit().putString(prefKey(slotIndex1Based), profileId).apply();
    }
}
