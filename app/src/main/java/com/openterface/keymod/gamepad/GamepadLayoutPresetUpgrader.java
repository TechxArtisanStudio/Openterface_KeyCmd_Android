package com.openterface.keymod.gamepad;

import java.util.Iterator;

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
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V5;
        }
        if (d.schemaVersion == GamepadLayoutPresetConstants.SCHEMA_VERSION_V5) {
            migrateV5ToV6(d);
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V6;
        }
        if (d.schemaVersion == GamepadLayoutPresetConstants.SCHEMA_VERSION_V6) {
            migrateV6ToV7(d);
            d.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        }
    }

    /** v6 → v7: optional {@code stick_left}; no structural rewrite (documents may omit the primary left slot). */
    private static void migrateV6ToV7(GamepadLayoutPresetDocument d) {
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
     * v4 → v5: when {@code stick_key_extra} is absent, legacy {@code stick_right} is renamed to
     * {@link GamepadLayoutPresetConstants#LEGACY_STICK_KEY_EXTRA_MODULE_ID} (intermediate id; v6 restores {@code stick_right}).
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
            if (GamepadLayoutPresetConstants.LEGACY_STICK_KEY_EXTRA_MODULE_ID.equals(m.id)) {
                hasArrowSlot = true;
            }
            if ("stick_right".equals(m.id)) {
                stickRight = m;
            }
        }
        if (stickRight != null && !hasArrowSlot) {
            stickRight.id = GamepadLayoutPresetConstants.LEGACY_STICK_KEY_EXTRA_MODULE_ID;
        }
    }

    /**
     * v5 → v6: remove {@code stick_aux_*}; rename lone {@code stick_key_extra} → {@code stick_right};
     * if both {@code stick_right} and {@code stick_key_extra} exist, drop {@code stick_key_extra}.
     */
    private static void migrateV5ToV6(GamepadLayoutPresetDocument d) {
        if (d.modules == null) {
            return;
        }
        Iterator<GamepadLayoutPresetDocument.GamepadModule> it = d.modules.iterator();
        while (it.hasNext()) {
            GamepadLayoutPresetDocument.GamepadModule m = it.next();
            if (m == null || m.id == null) {
                continue;
            }
            if (m.id.startsWith("stick_aux_")) {
                it.remove();
            }
        }
        boolean hasStickRight = false;
        GamepadLayoutPresetDocument.GamepadModule keyExtra = null;
        for (GamepadLayoutPresetDocument.GamepadModule m : d.modules) {
            if (m == null || m.id == null) {
                continue;
            }
            if ("stick_right".equals(m.id)) {
                hasStickRight = true;
            }
            if (GamepadLayoutPresetConstants.LEGACY_STICK_KEY_EXTRA_MODULE_ID.equals(m.id)) {
                keyExtra = m;
            }
        }
        if (keyExtra != null) {
            if (!hasStickRight) {
                keyExtra.id = "stick_right";
            } else {
                d.modules.remove(keyExtra);
            }
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
