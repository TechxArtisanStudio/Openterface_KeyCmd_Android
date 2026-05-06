package com.openterface.keymod.gamepad;

import android.graphics.Color;

import androidx.annotation.Nullable;

/**
 * Per-module accent color: preset palette, JSON {@link GamepadLayoutPresetDocument.GamepadModule#moduleAccentArgb},
 * and resolution against the app theme accent when unset.
 */
public final class GamepadModuleAccent {

    private GamepadModuleAccent() {}

    /** Curated opaque swatches for the config UI (ARGB). */
    public static final int[] PRESET_ARGB = {
            0xFF2196F3,
            0xFF4CAF50,
            0xFFE91E63,
            0xFFFFC107,
            0xFF9C27B0,
            0xFF00BCD4,
            0xFFFF5722,
            0xFF795548,
            0xFF607D8B,
            0xFF3F51B5,
            0xFF009688,
            0xFFFF9800,
            0xFF673AB7,
            0xFF37474F,
    };

    /** Opaque ARGB for rendering and storage from user/custom picks. */
    public static int toOpaqueArgb(int argb) {
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    /**
     * @param moduleAccentArgb from preset JSON; null = use {@code themeAccentPrimary}
     */
    public static int resolve(@Nullable Integer moduleAccentArgb, int themeAccentPrimary) {
        if (moduleAccentArgb == null) {
            return themeAccentPrimary;
        }
        return toOpaqueArgb(moduleAccentArgb);
    }

    public static boolean hasCustomAccent(@Nullable Integer moduleAccentArgb) {
        return moduleAccentArgb != null;
    }
}
