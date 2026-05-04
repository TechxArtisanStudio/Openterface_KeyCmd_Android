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
}
