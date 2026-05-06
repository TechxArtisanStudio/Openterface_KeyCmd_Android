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
        if (d.schemaVersion < GamepadLayoutPresetConstants.SCHEMA_VERSION_V2) {
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V2;
        }
        if (d.schemaVersion == GamepadLayoutPresetConstants.SCHEMA_VERSION_V2) {
            migrateV2ToV3(d);
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V3;
        }
        if (d.schemaVersion == GamepadLayoutPresetConstants.SCHEMA_VERSION_V3) {
            migrateV3ToV4(d);
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V4;
        }
        if (d.schemaVersion == GamepadLayoutPresetConstants.SCHEMA_VERSION_V4) {
            migrateV4ToV5(d);
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        }
    }

    /**
     * v2 → v3: {@code WASD_CROSS} → {@code DPAD} + {@code dpadVariant=cross}; default missing DPAD variants.
     */
    private static void migrateV2ToV3(GamepadLayoutPresetDocument d) {
        if (d.modules == null) {
            return;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : d.modules) {
            if (m == null || m.type == null) {
                continue;
            }
            if ("WASD_CROSS".equals(m.type)) {
                m.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
                m.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
            }
            if (GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)
                    && (m.dpadVariant == null || m.dpadVariant.trim().isEmpty())) {
                m.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
            }
        }
    }

    /** v3 → v4: optional new fields; schema bump only. */
    private static void migrateV3ToV4(GamepadLayoutPresetDocument d) {
        if (d.layout != null && d.layout.faceButtonTemplate != null
                && d.layout.faceButtonTemplate.trim().isEmpty()) {
            d.layout.faceButtonTemplate = null;
        }
    }

    /**
     * v4 → v5: legacy optional {@code stick_right} merges into the arrow stick slot {@code stick_key_extra}
     * when that id is not already used (add-module UI no longer creates {@code stick_right}).
     */
    private static void migrateV4ToV5(GamepadLayoutPresetDocument d) {
        if (d.modules == null) {
            return;
        }
        boolean hasArrowSlot = false;
        GamepadLayoutPresetDocument.GamepadModule stickRight = null;
        for (GamepadLayoutPresetDocument.GamepadModule m : d.modules) {
            if (m == null || m.id == null) {
                continue;
            }
            if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(m.id)) {
                hasArrowSlot = true;
            }
            if ("stick_right".equals(m.id)) {
                stickRight = m;
            }
        }
        if (stickRight != null && !hasArrowSlot) {
            stickRight.id = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID;
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
