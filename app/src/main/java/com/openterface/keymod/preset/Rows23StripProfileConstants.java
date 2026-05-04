package com.openterface.keymod.preset;

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
}
