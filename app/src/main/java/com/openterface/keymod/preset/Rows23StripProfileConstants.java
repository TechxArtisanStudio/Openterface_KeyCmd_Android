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

    /** Non-deletable built-in: thematic emoji glyph showcase across all strip slots. */
    public static final String SYMBOLS_PROFILE_ID = "strip_symbols";

    /** Non-deletable built-in: math/science glyph theme across all strip slots. */
    public static final String MATH_PROFILE_ID = "strip_math";

    /**
     * Non-deletable built-in: box-drawing &amp; block elements for terminal table layouts.
     * Caps render the codepoint glyph and presses dispatch via {@code HidTextKeystrokeSender}'s
     * per-OS Unicode Hex Input alt-code path.
     */
    public static final String BOX_LINES_PROFILE_ID = "strip_boxlines";

    /** Non-deletable built-in: Latin-1 supplement letters, fractions, and units. */
    public static final String LATIN_PROFILE_ID = "strip_latin";

    /** Non-deletable built-in: arrows and geometric shapes. */
    public static final String ARROWS_PROFILE_ID = "strip_arrows";

    /** Non-deletable built-in: currencies and decorative punctuation. */
    public static final String CURRENCY_PROFILE_ID = "strip_currency";

    public static boolean isBuiltInProfileId(@Nullable String id) {
        return DEFAULT_PROFILE_ID.equals(id)
                || SYMBOLS_PROFILE_ID.equals(id)
                || MATH_PROFILE_ID.equals(id)
                || BOX_LINES_PROFILE_ID.equals(id)
                || LATIN_PROFILE_ID.equals(id)
                || ARROWS_PROFILE_ID.equals(id)
                || CURRENCY_PROFILE_ID.equals(id);
    }
}
