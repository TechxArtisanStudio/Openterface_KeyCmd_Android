package com.openterface.keymod.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Shared preferences for KM Pro submode tabs, portrait BI/IME surface, and landscape full/split layout.
 */
public final class KmProSubmodePrefs {

    private static final String PREFS = "AppPrefs";

    public static final String SUBMODE_KEYBOARD = "keyboard";
    public static final String SUBMODE_NUMPAD = "numpad";
    public static final String SUBMODE_COMPOSE = "compose";

    public static final String INPUT_SURFACE_BUILT_IN = "built_in";
    public static final String INPUT_SURFACE_IME = "ime";

    public static final String LAYOUT_FULL = "full";
    public static final String LAYOUT_SPLIT = "split";

    private static final String KEY_SUBMODE = "km_pro_submode";
    private static final String KEY_PORTRAIT_INPUT_SURFACE = "km_pro_portrait_input_surface";
    private static final String KEY_LANDSCAPE_LAYOUT = "km_pro_landscape_layout";

    private KmProSubmodePrefs() {}

    private static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public static String getSubmode(@NonNull Context ctx) {
        return prefs(ctx).getString(KEY_SUBMODE, SUBMODE_KEYBOARD);
    }

    public static void setSubmode(@NonNull Context ctx, @NonNull String submode) {
        prefs(ctx).edit().putString(KEY_SUBMODE, submode).apply();
    }

    public static boolean isPortraitImeSurface(@NonNull Context ctx) {
        return INPUT_SURFACE_IME.equals(
                prefs(ctx).getString(KEY_PORTRAIT_INPUT_SURFACE, INPUT_SURFACE_BUILT_IN));
    }

    public static void setPortraitInputSurface(@NonNull Context ctx, boolean ime) {
        prefs(ctx)
                .edit()
                .putString(
                        KEY_PORTRAIT_INPUT_SURFACE,
                        ime ? INPUT_SURFACE_IME : INPUT_SURFACE_BUILT_IN)
                .apply();
    }

    /** Persisted landscape built-in layout: {@link #LAYOUT_FULL} or {@link #LAYOUT_SPLIT}. */
    @NonNull
    public static String getLandscapeLayoutKey(@NonNull Context ctx) {
        return prefs(ctx).getString(KEY_LANDSCAPE_LAYOUT, LAYOUT_FULL);
    }

    public static void setLandscapeLayoutKey(@NonNull Context ctx, @NonNull String layoutKey) {
        prefs(ctx).edit().putString(KEY_LANDSCAPE_LAYOUT, layoutKey).apply();
    }

    public static boolean isLandscapeSplit(@NonNull Context ctx) {
        return LAYOUT_SPLIT.equals(getLandscapeLayoutKey(ctx));
    }
}
