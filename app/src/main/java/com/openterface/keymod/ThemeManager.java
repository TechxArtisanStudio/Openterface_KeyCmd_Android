package com.openterface.keymod;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

/**
 * Central theme resolver for color family + light/dark mode behavior.
 */
public final class ThemeManager {

    public static final String PREF_THEME_COLOR_FAMILY = "theme_color_family";
    public static final String PREF_THEME_FOLLOW_SYSTEM = "theme_follow_system";
    public static final String PREF_THEME_MODE_OVERRIDE = "theme_mode_override";

    public static final String FAMILY_ORANGE = "orange";
    public static final String FAMILY_BLUE = "blue";
    public static final String FAMILY_GREEN = "green";
    public static final String FAMILY_PINK = "pink";
    public static final String FAMILY_PURPLE = "purple";
    public static final String FAMILY_RED = "red";
    public static final String FAMILY_TEAL = "teal";
    public static final String FAMILY_INDIGO = "indigo";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private ThemeManager() {
    }

    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean followSystem = prefs.getBoolean(PREF_THEME_FOLLOW_SYSTEM, false);
        String modeOverride = prefs.getString(PREF_THEME_MODE_OVERRIDE, MODE_DARK);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);

        int desiredNightMode;
        if (followSystem) {
            desiredNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if (MODE_LIGHT.equals(modeOverride)) {
            desiredNightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            desiredNightMode = AppCompatDelegate.MODE_NIGHT_YES;
        }

        if (AppCompatDelegate.getDefaultNightMode() != desiredNightMode) {
            AppCompatDelegate.setDefaultNightMode(desiredNightMode);
        }

        activity.setTheme(resolveTheme(family));
    }

    public static void savePreferences(Context context, String family, boolean followSystem, String modeOverride) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_THEME_COLOR_FAMILY, family)
                .putBoolean(PREF_THEME_FOLLOW_SYSTEM, followSystem)
                .putString(PREF_THEME_MODE_OVERRIDE, modeOverride)
                .apply();
    }

    public static int getSelectedThemeResId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);
        return resolveTheme(family);
    }

    /**
     * Resolves a theme color attribute (e.g. {@link android.R.attr#colorPrimary}) from the
     * context's current theme — matches General Settings color family + light/dark.
     */
    public static int resolveThemeColor(Context context, int attrId, int fallbackArgb) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrId, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data;
            }
            if (typedValue.resourceId != 0) {
                try {
                    return ContextCompat.getColor(context, typedValue.resourceId);
                } catch (Exception ignored) {
                    // Fall through to fallback if resource cannot be resolved as a color.
                }
            }
        }
        return fallbackArgb;
    }

    /** Material / app theme primary accent (Orange, Blue, … per user setting). */
    public static int getColorPrimary(Context context) {
        // Drive accent from persisted family selection so color stays stable even if this context
        // is not fully theme-wrapped (seen in custom keyboard subviews).
        return getConfiguredAccentColor(context);
    }

    /**
     * Primary container fill (pressed / selected keycaps) for the current color family.
     * Matches {@link #getColorPrimary} — safe when the view context is not theme-wrapped.
     */
    public static int getColorPrimaryContainer(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);
        int colorResId;
        if (FAMILY_BLUE.equals(family)) {
            colorResId = R.color.theme_accent_blue_container;
        } else if (FAMILY_GREEN.equals(family)) {
            colorResId = R.color.theme_accent_green_container;
        } else if (FAMILY_PINK.equals(family)) {
            colorResId = R.color.theme_accent_pink_container;
        } else if (FAMILY_PURPLE.equals(family)) {
            colorResId = R.color.theme_accent_purple_container;
        } else if (FAMILY_RED.equals(family)) {
            colorResId = R.color.theme_accent_red_container;
        } else if (FAMILY_TEAL.equals(family)) {
            colorResId = R.color.theme_accent_teal_container;
        } else if (FAMILY_INDIGO.equals(family)) {
            colorResId = R.color.theme_accent_indigo_container;
        } else {
            colorResId = R.color.theme_accent_orange_container;
        }
        return ContextCompat.getColor(context, colorResId);
    }

    /**
     * Text/icon color on {@link #getColorPrimaryContainer} fills (Material {@code colorOnPrimaryContainer}).
     * Use when a view context may not resolve theme attrs (e.g. custom keyboard subtree).
     */
    public static int getColorOnPrimaryContainer(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);
        int colorResId;
        if (FAMILY_BLUE.equals(family)) {
            colorResId = R.color.theme_accent_blue_on_container;
        } else if (FAMILY_GREEN.equals(family)) {
            colorResId = R.color.theme_accent_green_on_container;
        } else if (FAMILY_PINK.equals(family)) {
            colorResId = R.color.theme_accent_pink_on_container;
        } else if (FAMILY_PURPLE.equals(family)) {
            colorResId = R.color.theme_accent_purple_on_container;
        } else if (FAMILY_RED.equals(family)) {
            colorResId = R.color.theme_accent_red_on_container;
        } else if (FAMILY_TEAL.equals(family)) {
            colorResId = R.color.theme_accent_teal_on_container;
        } else if (FAMILY_INDIGO.equals(family)) {
            colorResId = R.color.theme_accent_indigo_on_container;
        } else {
            colorResId = R.color.theme_accent_orange_on_container;
        }
        return ContextCompat.getColor(context, colorResId);
    }

    public static int getConfiguredAccentColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);
        int colorResId;
        if (FAMILY_BLUE.equals(family)) {
            colorResId = R.color.theme_accent_blue;
        } else if (FAMILY_GREEN.equals(family)) {
            colorResId = R.color.theme_accent_green;
        } else if (FAMILY_PINK.equals(family)) {
            colorResId = R.color.theme_accent_pink;
        } else if (FAMILY_PURPLE.equals(family)) {
            colorResId = R.color.theme_accent_purple;
        } else if (FAMILY_RED.equals(family)) {
            colorResId = R.color.theme_accent_red;
        } else if (FAMILY_TEAL.equals(family)) {
            colorResId = R.color.theme_accent_teal;
        } else if (FAMILY_INDIGO.equals(family)) {
            colorResId = R.color.theme_accent_indigo;
        } else {
            colorResId = R.color.theme_accent_orange;
        }
        return ContextCompat.getColor(context, colorResId);
    }

    private static int resolveTheme(String family) {
        if (FAMILY_BLUE.equals(family)) {
            return R.style.Theme_KeyMod_Blue;
        }
        if (FAMILY_GREEN.equals(family)) {
            return R.style.Theme_KeyMod_Green;
        }
        if (FAMILY_PINK.equals(family)) {
            return R.style.Theme_KeyMod_Pink;
        }
        if (FAMILY_PURPLE.equals(family)) {
            return R.style.Theme_KeyMod_Purple;
        }
        if (FAMILY_RED.equals(family)) {
            return R.style.Theme_KeyMod_Red;
        }
        if (FAMILY_TEAL.equals(family)) {
            return R.style.Theme_KeyMod_Teal;
        }
        if (FAMILY_INDIGO.equals(family)) {
            return R.style.Theme_KeyMod_Indigo;
        }
        return R.style.Theme_KeyMod_Orange;
    }
}
