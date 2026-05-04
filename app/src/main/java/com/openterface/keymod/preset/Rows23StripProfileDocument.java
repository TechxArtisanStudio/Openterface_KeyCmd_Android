package com.openterface.keymod.preset;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Gson document for sharing / importing a single {@link Rows23StripProfile}.
 */
@SuppressWarnings("unused")
public class Rows23StripProfileDocument {

    public String format;
    public int schemaVersion;
    public Meta meta;
    public Rows23StripProfile profile;

    public static class Meta {
        public String displayName;
        public String description;
        public String exportedAt;
        public String sourceAppVersion;
    }

    public static boolean looksLikeDocument(String json) {
        if (json == null) {
            return false;
        }
        String t = json.trim();
        return t.startsWith("{")
                && t.contains("\"format\"")
                && t.contains(Rows23StripProfileConstants.DOCUMENT_FORMAT);
    }

    @Nullable
    public static Rows23StripProfileDocument parseOrNull(String json) {
        try {
            Rows23StripProfileDocument d = new Gson().fromJson(json, Rows23StripProfileDocument.class);
            if (d == null || d.format == null) {
                return null;
            }
            return d;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static void validateOrThrow(Rows23StripProfileDocument d) throws IllegalArgumentException {
        if (d == null) {
            throw new IllegalArgumentException("null document");
        }
        if (!Rows23StripProfileConstants.DOCUMENT_FORMAT.equals(d.format)) {
            throw new IllegalArgumentException("Unknown format: " + d.format);
        }
        if (d.schemaVersion != Rows23StripProfileConstants.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + d.schemaVersion);
        }
        if (d.profile == null) {
            throw new IllegalArgumentException("Missing profile");
        }
        if (d.profile.slotMap == null) {
            d.profile.slotMap = new java.util.HashMap<>();
        }
        if (d.profile.shortcuts == null) {
            d.profile.shortcuts = new java.util.ArrayList<>();
        }
    }
}
