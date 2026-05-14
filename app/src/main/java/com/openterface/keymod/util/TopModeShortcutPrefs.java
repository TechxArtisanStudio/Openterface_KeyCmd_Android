package com.openterface.keymod.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.openterface.keymod.LaunchPanelActivity;
import com.openterface.keymod.R;

/**
 * Persists which app mode each top-strip PH slot (1–3) opens; defaults match product spec.
 */
public final class TopModeShortcutPrefs {

    private static final String PREFS_NAME = "TopModeShortcutPrefs";
    private static final String KEY_SLOT_1 = "top_mode_slot_1";
    private static final String KEY_SLOT_2 = "top_mode_slot_2";
    private static final String KEY_SLOT_3 = "top_mode_slot_3";

    /** Order matches {@code nav_menu.xml}: Keyboard & Mouse, Presentation, then the rest. */
    private static final String[] SELECTABLE_MODES = {
            LaunchPanelActivity.MODE_KEYBOARD_MOUSE,
            LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO,
            LaunchPanelActivity.MODE_PRESENTATION,
            LaunchPanelActivity.MODE_GAMEPAD,
            LaunchPanelActivity.MODE_SHORTCUTS,
            LaunchPanelActivity.MODE_MACROS,
            LaunchPanelActivity.MODE_VOICE,
    };

    private TopModeShortcutPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String defaultForSlot(int slotIndex1Based) {
        switch (slotIndex1Based) {
            case 1:
                return LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
            case 2:
                return LaunchPanelActivity.MODE_PRESENTATION;
            case 3:
                return LaunchPanelActivity.MODE_GAMEPAD;
            default:
                return LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
        }
    }

    private static String prefKeyForSlot(int slotIndex1Based) {
        switch (slotIndex1Based) {
            case 1:
                return KEY_SLOT_1;
            case 2:
                return KEY_SLOT_2;
            case 3:
                return KEY_SLOT_3;
            default:
                return KEY_SLOT_1;
        }
    }

    public static String getModeForSlot(Context context, int slotIndex1Based) {
        String def = defaultForSlot(slotIndex1Based);
        String v = prefs(context).getString(prefKeyForSlot(slotIndex1Based), def);
        return normalizeMode(v);
    }

    public static void setModeForSlot(Context context, int slotIndex1Based, String mode) {
        prefs(context).edit().putString(prefKeyForSlot(slotIndex1Based), normalizeMode(mode)).apply();
    }

    /** Returns a valid launch mode; unknown values fall back to keyboard_mouse. */
    public static String normalizeMode(String mode) {
        if (mode == null) {
            return LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
        }
        for (String m : SELECTABLE_MODES) {
            if (m.equals(mode)) {
                return mode;
            }
        }
        return LaunchPanelActivity.MODE_KEYBOARD_MOUSE;
    }

    public static String[] getSelectableModes() {
        return SELECTABLE_MODES.clone();
    }

    public static CharSequence[] getModeLabels(Context context) {
        CharSequence[] out = new CharSequence[SELECTABLE_MODES.length];
        for (int i = 0; i < SELECTABLE_MODES.length; i++) {
            out[i] = context.getString(labelResForMode(SELECTABLE_MODES[i]));
        }
        return out;
    }

    @StringRes
    public static int labelResForMode(String mode) {
        switch (normalizeMode(mode)) {
            case LaunchPanelActivity.MODE_PRESENTATION:
                return R.string.presentation_mode;
            case LaunchPanelActivity.MODE_GAMEPAD:
                return R.string.top_mode_label_gamepad;
            case LaunchPanelActivity.MODE_SHORTCUTS:
                return R.string.top_mode_label_shortcuts;
            case LaunchPanelActivity.MODE_MACROS:
                return R.string.top_mode_label_macros;
            case LaunchPanelActivity.MODE_VOICE:
                return R.string.settings_tab_voice;
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE:
                return R.string.top_mode_label_keyboard_mouse;
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO:
                return R.string.top_mode_label_keyboard_mouse_pro;
            default:
                return R.string.top_mode_label_keyboard_mouse;
        }
    }

    @DrawableRes
    public static int iconResForMode(String mode) {
        switch (normalizeMode(mode)) {
            case LaunchPanelActivity.MODE_PRESENTATION:
                return R.drawable.ic_presentation;
            case LaunchPanelActivity.MODE_GAMEPAD:
                return R.drawable.gamepad;
            case LaunchPanelActivity.MODE_SHORTCUTS:
                return R.drawable.three_dots;
            case LaunchPanelActivity.MODE_MACROS:
                return R.drawable.macros;
            case LaunchPanelActivity.MODE_VOICE:
                return R.drawable.voice;
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE:
                return R.drawable.keyboard_mouse;
            case LaunchPanelActivity.MODE_KEYBOARD_MOUSE_PRO:
                return R.drawable.keyboard_mouse_pro;
            default:
                return R.drawable.keyboard_mouse;
        }
    }
}
