package com.openterface.keymod.gamepad;

import androidx.annotation.NonNull;

/**
 * HID keyboard modifier usages (page 0x07) and {@code modifierMask} bit encoding for gamepad button mappings.
 */
public final class GamepadButtonKeyMapping {

    /** Left then right: label, HID usage (224–231). */
    public static final String[][] MODIFIER_ENTRIES = {
            {"Ctrl", "224"}, {"Shift", "225"}, {"Alt", "226"}, {"Fn", "227"},
            {"RCtrl", "228"}, {"RShift", "229"}, {"RAlt", "230"}, {"RGui", "231"},
    };

    public static final int HID_MODIFIER_MIN = 224;
    public static final int HID_MODIFIER_MAX = 231;

    private GamepadButtonKeyMapping() {
    }

    public static boolean isModifierKey(int keyCode) {
        return keyCode >= HID_MODIFIER_MIN && keyCode <= HID_MODIFIER_MAX;
    }

    /** HID modifier byte bit for a modifier usage, or 0 if not a modifier key. */
    public static int modifierBit(int keyCode) {
        switch (keyCode) {
            case 224:
                return 0x01;
            case 225:
                return 0x02;
            case 226:
                return 0x04;
            case 227:
                return 0x08;
            case 228:
                return 0x10;
            case 229:
                return 0x20;
            case 230:
                return 0x40;
            case 231:
                return 0x80;
            default:
                return 0;
        }
    }

    /**
     * Ensures {@code modifierMask} includes the bit for {@code hidKey} when it is a modifier usage.
     */
    public static int normalize(int hidKey, int modifierMask) {
        if (isModifierKey(hidKey)) {
            return modifierMask | modifierBit(hidKey);
        }
        return modifierMask;
    }

    @NonNull
    public static String labelForModifierHid(int keyCode) {
        for (String[] entry : MODIFIER_ENTRIES) {
            if (Integer.parseInt(entry[1]) == keyCode) {
                return entry[0];
            }
        }
        switch (keyCode) {
            case 224:
                return "Ctrl";
            case 225:
                return "Shift";
            case 226:
                return "Alt";
            case 227:
                return "Fn";
            case 228:
                return "RCtrl";
            case 229:
                return "RShift";
            case 230:
                return "RAlt";
            case 231:
                return "RGui";
            default:
                return String.valueOf(keyCode);
        }
    }
}
