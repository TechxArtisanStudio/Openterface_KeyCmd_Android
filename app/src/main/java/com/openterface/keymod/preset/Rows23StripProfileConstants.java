package com.openterface.keymod.preset;

import androidx.annotation.Nullable;

/**
 * JSON document identity for per-profile Rows 2–3 strip configuration (import / share).
 */
public final class Rows23StripProfileConstants {

    private Rows23StripProfileConstants() {
    }

    public static final String DOCUMENT_FORMAT = "openterface_keymod_rows23_strip_profile";
    public static final int SCHEMA_VERSION = 1;

    /** Non-deletable built-in profile; migrated from legacy global slot map + strip catalog. */
    public static final String DEFAULT_PROFILE_ID = "strip_default";

    /**
     * Non-deletable built-in: same factory starting point as {@link #DEFAULT_PROFILE_ID} (empty
     * overrides + page 2 paren / grave–tilde slots). Shown in UI as {@code Mine}.
     */
    public static final String PERSONAL_PROFILE_ID = "strip_personal";

    /**
     * Themed strip presets removed in favor of HID-first Default / Mine; still recognized when
     * cleaning persisted profiles and page-3 quick-toggle prefs.
     */
    private static final String[] LEGACY_REMOVED_THEMATIC_STRIP_IDS = new String[]{
            "strip_symbols",
            "strip_math",
            "strip_boxlines",
            "strip_latin",
            "strip_arrows",
            "strip_currency",
    };

    public static boolean isLegacyRemovedThematicStripId(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (String legacy : LEGACY_REMOVED_THEMATIC_STRIP_IDS) {
            if (legacy.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuiltInProfileId(@Nullable String id) {
        return DEFAULT_PROFILE_ID.equals(id) || PERSONAL_PROFILE_ID.equals(id);
    }
}
