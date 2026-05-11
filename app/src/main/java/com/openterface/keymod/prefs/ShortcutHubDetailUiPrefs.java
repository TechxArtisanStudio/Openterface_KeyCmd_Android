package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Per-profile Shortcut Hub profile-detail layout (list vs card) and how shortcut rows show
 * name, icon, chord, or hybrid. Stored alongside shortcut profiles in {@code ShortcutProfiles_v2}.
 */
public final class ShortcutHubDetailUiPrefs {

    /** Same file as {@link com.openterface.keymod.ShortcutProfileManager}. */
    private static final String PREFS_NAME = "ShortcutProfiles_v2";
    private static final String KEY_LAYOUT_PREFIX = "hub_detail_layout_";
    private static final String KEY_DISPLAY_PREFIX = "hub_detail_display_";

    public static final int LAYOUT_LIST = 0;
    public static final int LAYOUT_CARD = 1;

    public static final int DISPLAY_NAME = 0;
    public static final int DISPLAY_ICON = 1;
    public static final int DISPLAY_CHORD = 2;
    public static final int DISPLAY_HYBRID = 3;

    private ShortcutHubDetailUiPrefs() {
    }

    public static int clampLayout(int layout) {
        if (layout != LAYOUT_CARD) {
            return LAYOUT_LIST;
        }
        return LAYOUT_CARD;
    }

    public static int clampDisplay(int display) {
        if (display < DISPLAY_NAME || display > DISPLAY_HYBRID) {
            return DISPLAY_NAME;
        }
        return display;
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String layoutKey(String profileId) {
        return KEY_LAYOUT_PREFIX + profileId;
    }

    private static String displayKey(String profileId) {
        return KEY_DISPLAY_PREFIX + profileId;
    }

    public static int readLayout(Context context, String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return LAYOUT_LIST;
        }
        return clampLayout(sp(context).getInt(layoutKey(profileId), LAYOUT_LIST));
    }

    public static void writeLayout(Context context, String profileId, int layout) {
        if (profileId == null || profileId.isEmpty()) {
            return;
        }
        sp(context).edit().putInt(layoutKey(profileId), clampLayout(layout)).apply();
    }

    public static int readDisplay(Context context, String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return DISPLAY_NAME;
        }
        return clampDisplay(sp(context).getInt(displayKey(profileId), DISPLAY_NAME));
    }

    public static void writeDisplay(Context context, String profileId, int display) {
        if (profileId == null || profileId.isEmpty()) {
            return;
        }
        sp(context).edit().putInt(displayKey(profileId), clampDisplay(display)).apply();
    }
}
