package com.openterface.keymod.gamepad;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

/**
 * Reads/writes the optional export author name and stamps {@link GamepadLayoutPresetDocument.Meta#creator}.
 */
public final class GamepadPresetExportCreator {

    private GamepadPresetExportCreator() {}

    public static void stampMeta(@NonNull Context ctx, @Nullable GamepadLayoutPresetDocument.Meta meta) {
        if (meta == null) {
            return;
        }
        String c = readTrimmed(ctx);
        meta.creator = c.isEmpty() ? null : c;
    }

    @NonNull
    public static String readTrimmed(@NonNull Context ctx) {
        String s = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(GamepadPreferenceKeys.PRESET_EXPORT_CREATOR_NAME, "");
        return s != null ? s.trim() : "";
    }

    /** True once the user has completed the first-time export prompt (value may be empty). */
    public static boolean hasStoredExportCreatorPreference(@NonNull Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx)
                .contains(GamepadPreferenceKeys.PRESET_EXPORT_CREATOR_NAME);
    }

    /** Persists trimmed creator; {@code null} or blank after trim clears the stored value to "". */
    public static void putExportCreatorValue(@NonNull Context ctx, @Nullable String normalizedOrNull) {
        String s = normalizedOrNull != null ? normalizedOrNull.trim() : "";
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(GamepadPreferenceKeys.PRESET_EXPORT_CREATOR_NAME, s)
                .apply();
    }
}
