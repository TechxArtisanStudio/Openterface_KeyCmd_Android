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
        /** STICK_*: HID key codes for virtual D-pad on analog ring. */
        @Nullable public Integer stickUpKey;
        @Nullable public Integer stickLeftKey;
        @Nullable public Integer stickDownKey;
        @Nullable public Integer stickRightKey;
        /** BUTTON: primary HID key and modifier bitmask (same encoding as GamepadFragment). */
        @Nullable public Integer hidKey;
        @Nullable public Integer modifierMask;
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
        if (d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + d.schemaVersion);
        }
        if (d.layout == null) {
            throw new IllegalArgumentException("Missing layout");
        }
        if (d.modules == null) {
            d.modules = new ArrayList<>();
        }
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
        for (GamepadModule m : d.modules) {
            if (m.scale <= 0 || m.scale > 4.0f) {
                throw new IllegalArgumentException("Module " + m.id + ": scale out of range (0,4]");
            }
            if (m.anchorX < 0 || m.anchorX > 1 || m.anchorY < 0 || m.anchorY > 1) {
                throw new IllegalArgumentException("Module " + m.id + ": anchor must be in [0,1]");
            }
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
