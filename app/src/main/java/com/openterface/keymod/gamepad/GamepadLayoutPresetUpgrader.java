package com.openterface.keymod.gamepad;

/**
 * Normalizes older preset documents to {@link GamepadLayoutPresetConstants#SCHEMA_VERSION}.
 */
public final class GamepadLayoutPresetUpgrader {

    private GamepadLayoutPresetUpgrader() {}

    public static void upgradeToLatest(GamepadLayoutPresetDocument d) {
        if (d == null) {
            return;
        }
        if (d.schemaVersion >= GamepadLayoutPresetConstants.SCHEMA_VERSION) {
            return;
        }
        if (d.schemaVersion == GamepadLayoutPresetConstants.SCHEMA_VERSION_V1) {
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        }
    }

    /**
     * Earlier builds passed anchor X (0.38f) as {@link GamepadLayoutPresetDocument.GamepadModule#scale}
     * for bundled touchpad mouse buttons, making them tiny. Reset that mistaken default to 1.0.
     */
    public static void normalizeBundledMouseButtonModuleScales(GamepadLayoutPresetDocument d) {
        if (d == null || d.modules == null) {
            return;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : d.modules) {
            if (m == null || m.type == null) {
                continue;
            }
            if (!GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(m.type)) {
                continue;
            }
            if (Math.abs(m.scale - 0.38f) < 0.02f) {
                m.scale = 1.0f;
            }
        }
    }
}
