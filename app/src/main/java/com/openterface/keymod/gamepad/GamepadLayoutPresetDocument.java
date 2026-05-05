package com.openterface.keymod.gamepad;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * Gson document for gamepad layout presets (shareable JSON).
 */
@SuppressWarnings("unused")
public class GamepadLayoutPresetDocument {

    public String format;
    public int schemaVersion;
    public Meta meta;
    public LayoutGlobals layout;
    public List<GamepadModule> modules;

    public static class Meta {
        public String id;
        public String displayName;
        public String description;
        public String exportedAt;
        public String sourceAppVersion;
    }

    public static class LayoutGlobals {
        public float mouseSensitivity = 1.0f;
        /** Optional extra multiplier for right-stick mouse mode (null = use app default tuning). */
        @Nullable public Float rightStickMouseGain;
        /**
         * Optional multiplier for {@code MOUSE_BUTTON} draw radius (touchpad L/M/R). Null = 1.0.
         * Valid range when set: {@code [0.5, 2.0]}.
         */
        @Nullable public Float touchpadMouseButtonScale;
        public boolean showTwoButtons;
        @Nullable public String backgroundImageFile;
        public float backgroundScale = 1.0f;
        public float backgroundOffsetX;
        public float backgroundOffsetY;
    }

    public static class GamepadModule {
        public String id;
        public String type;
        public int zIndex;
        public float scale = 1.0f;
        public float anchorX;
        public float anchorY;
        @Nullable public Float widthNorm;
        @Nullable public Float heightNorm;
        /** BUTTON: optional short label shown on the control. */
        @Nullable public String displayLabel;
        /** STICK_*: HID key codes for virtual D-pad on analog ring. */
        @Nullable public Integer stickUpKey;
        @Nullable public Integer stickLeftKey;
        @Nullable public Integer stickDownKey;
        @Nullable public Integer stickRightKey;
        /** BUTTON: primary HID key and modifier bitmask (same encoding as GamepadFragment). */
        @Nullable public Integer hidKey;
        @Nullable public Integer modifierMask;
        /** MOUSE_BUTTON: 1 = left, 2 = middle, 3 = right (same convention as {@code sendMouseClick}). */
        @Nullable public Integer mouseButton;
    }

    public static boolean looksLikeDocument(String json) {
        if (json == null) {
            return false;
        }
        String t = json.trim();
        return t.startsWith("{")
                && t.contains("\"format\"")
                && t.contains(GamepadLayoutPresetConstants.DOCUMENT_FORMAT);
    }

    @Nullable
    public static GamepadLayoutPresetDocument parseOrNull(String json) {
        try {
            GamepadLayoutPresetDocument d = new Gson().fromJson(json, GamepadLayoutPresetDocument.class);
            if (d == null || d.format == null) {
                return null;
            }
            return d;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static String toJsonPretty(GamepadLayoutPresetDocument doc) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(doc);
    }

    public static void validateOrThrow(GamepadLayoutPresetDocument d) throws IllegalArgumentException {
        if (d == null) {
            throw new IllegalArgumentException("null document");
        }
        if (!GamepadLayoutPresetConstants.DOCUMENT_FORMAT.equals(d.format)) {
            throw new IllegalArgumentException("Unknown format: " + d.format);
        }
        if (d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION
                && d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION_V1) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + d.schemaVersion);
        }
        if (d.layout == null) {
            throw new IllegalArgumentException("Missing layout");
        }
        if (d.layout.rightStickMouseGain != null) {
            float rg = d.layout.rightStickMouseGain;
            if (rg < 0.25f || rg > 4.0f) {
                throw new IllegalArgumentException("layout.rightStickMouseGain must be in [0.25, 4]");
            }
        }
        if (d.layout.touchpadMouseButtonScale != null) {
            float t = d.layout.touchpadMouseButtonScale;
            if (t < 0.5f || t > 2.0f) {
                throw new IllegalArgumentException("layout.touchpadMouseButtonScale must be in [0.5, 2]");
            }
        }
        if (d.modules == null) {
            d.modules = new ArrayList<>();
        }
        GamepadLayoutPresetUpgrader.upgradeToLatest(d);
        GamepadLayoutPresetUpgrader.normalizeBundledMouseButtonModuleScales(d);
        GamepadModule stick = findModule(d.modules, "stick_left");
        if (stick == null) {
            throw new IllegalArgumentException("Missing module id=stick_left");
        }
        if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(stick.type)
                && !GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(stick.type)) {
            throw new IllegalArgumentException("stick_left must be STICK_KEY or STICK_MOUSE");
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(stick.type)) {
            if (stick.stickUpKey == null || stick.stickLeftKey == null
                    || stick.stickDownKey == null || stick.stickRightKey == null) {
                throw new IllegalArgumentException("STICK_KEY requires stickUp/Left/Down/Right key codes");
            }
        }
        GamepadModule btnA = findModule(d.modules, "button_a");
        if (btnA == null || !GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(btnA.type)) {
            throw new IllegalArgumentException("Missing BUTTON module id=button_a");
        }
        if (btnA.hidKey == null) {
            throw new IllegalArgumentException("button_a missing hidKey");
        }
        if (d.layout.showTwoButtons) {
            GamepadModule btnB = findModule(d.modules, "button_b");
            if (btnB == null || btnB.hidKey == null) {
                throw new IllegalArgumentException("showTwoButtons requires BUTTON module id=button_b with hidKey");
            }
        }
        int stickCount = 0;
        int touchpadCount = 0;
        int buttonCount = 0;
        int mouseButtonCount = 0;
        boolean hasStickLeft = false;
        for (GamepadModule m : d.modules) {
            if (m == null || m.id == null || m.type == null) {
                throw new IllegalArgumentException("Invalid module entry");
            }
            if (m.scale <= 0 || m.scale > 4.0f) {
                throw new IllegalArgumentException("Module " + m.id + ": scale out of range (0,4]");
            }
            if (m.anchorX < 0 || m.anchorX > 1 || m.anchorY < 0 || m.anchorY > 1) {
                throw new IllegalArgumentException("Module " + m.id + ": anchor must be in [0,1]");
            }
            if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)) {
                stickCount++;
                if (!"stick_left".equals(m.id) && !"stick_right".equals(m.id)
                        && !GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(m.id)) {
                    throw new IllegalArgumentException("Unknown stick module id: " + m.id);
                }
                if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(m.id)
                        && !GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)) {
                    throw new IllegalArgumentException("stick_key_extra must be STICK_KEY");
                }
                if ("stick_left".equals(m.id)) {
                    hasStickLeft = true;
                }
                if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                        && (m.stickUpKey == null || m.stickLeftKey == null
                        || m.stickDownKey == null || m.stickRightKey == null)) {
                    throw new IllegalArgumentException("Module " + m.id + ": STICK_KEY needs four direction keys");
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)) {
                buttonCount++;
                if (m.hidKey == null) {
                    throw new IllegalArgumentException("Module " + m.id + ": BUTTON needs hidKey");
                }
                if (!m.id.matches("button_[a-z0-9]+")) {
                    throw new IllegalArgumentException("Invalid button id: " + m.id);
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD.equals(m.type)) {
                touchpadCount++;
                if (!"touchpad_1".equals(m.id)) {
                    throw new IllegalArgumentException("Touchpad id must be touchpad_1");
                }
                if (m.widthNorm == null || m.heightNorm == null
                        || m.widthNorm <= 0 || m.widthNorm > 1 || m.heightNorm <= 0 || m.heightNorm > 1) {
                    throw new IllegalArgumentException("Module " + m.id + ": TOUCHPAD needs widthNorm/heightNorm in (0,1]");
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(m.type)) {
                mouseButtonCount++;
                if (!GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID.equals(m.id)
                        && !GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID.equals(m.id)
                        && !GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID.equals(m.id)) {
                    throw new IllegalArgumentException("Invalid MOUSE_BUTTON id: " + m.id);
                }
                if (m.mouseButton == null || m.mouseButton < 1 || m.mouseButton > 3) {
                    throw new IllegalArgumentException("Module " + m.id + ": MOUSE_BUTTON needs mouseButton 1–3");
                }
                if ((GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID.equals(m.id) && m.mouseButton != 1)
                        || (GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID.equals(m.id) && m.mouseButton != 2)
                        || (GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID.equals(m.id) && m.mouseButton != 3)) {
                    throw new IllegalArgumentException("Module " + m.id + ": id does not match mouseButton value");
                }
            } else {
                throw new IllegalArgumentException("Unknown module type: " + m.type);
            }
        }
        if (!hasStickLeft) {
            throw new IllegalArgumentException("Missing stick_left");
        }
        if (stickCount > GamepadLayoutPresetConstants.MAX_STICK_MODULES) {
            throw new IllegalArgumentException("At most " + GamepadLayoutPresetConstants.MAX_STICK_MODULES
                    + " stick modules allowed");
        }
        if (touchpadCount > 1) {
            throw new IllegalArgumentException("At most one touchpad module allowed");
        }
        if (buttonCount > GamepadLayoutPresetConstants.MAX_BUTTON_MODULES) {
            throw new IllegalArgumentException("Too many BUTTON modules (max "
                    + GamepadLayoutPresetConstants.MAX_BUTTON_MODULES + ")");
        }
        if (mouseButtonCount > GamepadLayoutPresetConstants.MAX_MOUSE_BUTTON_MODULES) {
            throw new IllegalArgumentException("Too many MOUSE_BUTTON modules (max "
                    + GamepadLayoutPresetConstants.MAX_MOUSE_BUTTON_MODULES + ")");
        }
    }

    @Nullable
    private static GamepadModule findModule(List<GamepadModule> modules, String id) {
        for (GamepadModule m : modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }
}
