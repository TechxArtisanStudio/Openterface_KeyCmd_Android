package com.openterface.keymod;

import android.content.Context;

import androidx.annotation.NonNull;

import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;

/**
 * Localized labels for built-in profiles whose persisted {@link ShortcutProfile#name} stays English
 * for compatibility with imports, export filenames, and migrations.
 */
public final class ProfileUiStrings {

    private ProfileUiStrings() {
    }

    @NonNull
    public static String displayName(@NonNull Context context, @NonNull ShortcutProfile profile) {
        if ("default".equals(profile.id)) {
            return context.getString(R.string.profile_builtin_default_name);
        }
        return profile.name != null ? profile.name : "";
    }

    @NonNull
    public static String displayDescription(@NonNull Context context, @NonNull ShortcutProfile profile) {
        if ("default".equals(profile.id)) {
            return context.getString(R.string.profile_builtin_default_description);
        }
        return profile.description != null ? profile.description : "";
    }
}
