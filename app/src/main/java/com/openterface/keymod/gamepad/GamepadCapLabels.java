package com.openterface.keymod.gamepad;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Limits for optional per-module {@link GamepadLayoutPresetDocument.GamepadModule#displayLabel}
 * shown on button caps (and similar). Uses Unicode code point count so most emoji count as one.
 */
public final class GamepadCapLabels {

    /** Maximum code points allowed on a cap label (letters, digits, or emoji). */
    public static final int MAX_CAP_LABEL_CODE_POINTS = 6;

    private GamepadCapLabels() {}

    public static int codePointCount(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        return s.codePointCount(0, s.length());
    }

    /**
     * Truncates to at most {@code maxCodePoints} Unicode code points (not {@code String#length()}).
     */
    @NonNull
    public static String clampToMaxCodePoints(@Nullable String raw, int maxCodePoints) {
        if (raw == null || raw.isEmpty() || maxCodePoints <= 0) {
            return "";
        }
        if (codePointCount(raw) <= maxCodePoints) {
            return raw;
        }
        int end = raw.offsetByCodePoints(0, maxCodePoints);
        return raw.substring(0, end);
    }
}
