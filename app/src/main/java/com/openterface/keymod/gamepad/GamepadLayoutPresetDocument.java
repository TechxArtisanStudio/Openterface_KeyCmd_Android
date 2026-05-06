package com.openterface.keymod.gamepad;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        /**
         * Optional preset geometry hint: {@link GamepadLayoutPresetConstants#STICK_LAYOUT_SYMMETRICAL},
         * {@link GamepadLayoutPresetConstants#STICK_LAYOUT_OFFSET}, or {@link GamepadLayoutPresetConstants#STICK_LAYOUT_PARALLEL}.
         */
        @Nullable public String stickLayoutTemplate;
        /**
         * Optional face cluster hint (Nintendo / Xbox / PlayStation anchors); see
         * {@link GamepadFaceButtonTemplates}.
         */
        @Nullable public String faceButtonTemplate;
        /** When true, device tilt can drive relative pointer movement (see gamepad screen + USER_GUIDE). */
        @Nullable public Boolean gyroEnabled;
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
        /** STICK_* / DPAD: HID key codes for four-way digital input (virtual stick ring or D-pad). */
        @Nullable public Integer stickUpKey;
        @Nullable public Integer stickLeftKey;
        @Nullable public Integer stickDownKey;
        @Nullable public Integer stickRightKey;
        /** DPAD: visual/interaction variant (e.g. {@link GamepadLayoutPresetConstants#DPAD_VARIANT_CROSS}). */
        @Nullable public String dpadVariant;
        /**
         * DPAD {@code split} only: center gap as a fraction of radius; see
         * {@link GamepadLayoutPresetConstants#DPAD_SPLIT_GAP_RATIO_DEFAULT}.
         */
        @Nullable public Float dpadSplitGapRatio;
        /**
         * DPAD {@code split} only: center-to-outer-edge distance as a fraction of {@code half}; see
         * {@link GamepadLayoutPresetConstants#DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT}.
         */
        @Nullable public Float dpadSplitOuterReachRatio;
        /** Optional thumbstick cap look (reserved; null = default). */
        @Nullable public String stickVisualVariant;
        /** BUTTON: primary HID key and modifier bitmask (same encoding as GamepadFragment). */
        @Nullable public Integer hidKey;
        @Nullable public Integer modifierMask;
        /**
         * BUTTON: shape from square ({@code 0}) to circle ({@code 1}); see
         * {@link GamepadLayoutPresetConstants#clampButtonCornerRadiusNorm}.
         */
        @Nullable public Float buttonCornerRadiusNorm;
        /** MOUSE_BUTTON: 1 = left, 2 = middle, 3 = right (same convention as {@code sendMouseClick}). */
        @Nullable public Integer mouseButton;
        /** TRIGGER: reserved for future analog simulation; false = digital edge on {@code hidKey}. */
        @Nullable public Boolean triggerAnalog;
        /** TRIGGER: {@link GamepadLayoutPresetConstants#TRIGGER_VARIANT_DIGITAL} and siblings (UI / future use). */
        @Nullable public String triggerVariant;
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
                && d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION_V1
                && d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION_V2
                && d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION_V3) {
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
        if (d.schemaVersion != GamepadLayoutPresetConstants.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion after upgrade: " + d.schemaVersion);
        }
        if (d.layout.stickLayoutTemplate != null) {
            String t = d.layout.stickLayoutTemplate;
            if (!GamepadLayoutPresetConstants.STICK_LAYOUT_SYMMETRICAL.equals(t)
                    && !GamepadLayoutPresetConstants.STICK_LAYOUT_OFFSET.equals(t)
                    && !GamepadLayoutPresetConstants.STICK_LAYOUT_PARALLEL.equals(t)) {
                throw new IllegalArgumentException("layout.stickLayoutTemplate must be symmetrical, offset, or parallel");
            }
        }
        if (d.layout.faceButtonTemplate != null) {
            String ft = d.layout.faceButtonTemplate.trim();
            if (ft.isEmpty()) {
                d.layout.faceButtonTemplate = null;
            } else if (!GamepadLayoutPresetConstants.isAllowedFaceButtonTemplate(ft)) {
                throw new IllegalArgumentException("layout.faceButtonTemplate is not a known template id");
            } else {
                d.layout.faceButtonTemplate = ft;
            }
        }
        GamepadModule stick = findModule(d.modules, "stick_left");
        if (stick == null) {
            throw new IllegalArgumentException("Missing module id=stick_left");
        }
        if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(stick.type)
                && !GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(stick.type)
                && !GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(stick.type)) {
            throw new IllegalArgumentException("stick_left must be STICK_KEY, DPAD, or STICK_MOUSE");
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(stick.type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(stick.type)) {
            if (stick.stickUpKey == null || stick.stickLeftKey == null
                    || stick.stickDownKey == null || stick.stickRightKey == null) {
                throw new IllegalArgumentException("STICK_KEY/DPAD requires stickUp/Left/Down/Right key codes");
            }
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(stick.type)) {
            if (stick.dpadVariant == null || stick.dpadVariant.trim().isEmpty()) {
                throw new IllegalArgumentException("DPAD on stick_left requires dpadVariant");
            }
            if (!GamepadLayoutPresetConstants.isAllowedDpadVariant(stick.dpadVariant.trim())) {
                throw new IllegalArgumentException("Invalid dpadVariant: " + stick.dpadVariant);
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
        int touchpadCount = 0;
        int mouseButtonCount = 0;
        int shoulderCount = 0;
        int triggerCount = 0;
        boolean hasStickLeft = false;
        Set<String> seenModuleIds = new HashSet<>();
        for (GamepadModule m : d.modules) {
            if (m == null || m.id == null || m.type == null) {
                throw new IllegalArgumentException("Invalid module entry");
            }
            if (!seenModuleIds.add(m.id)) {
                throw new IllegalArgumentException("Duplicate module id: " + m.id);
            }
            if (m.scale <= 0 || m.scale > 4.0f) {
                throw new IllegalArgumentException("Module " + m.id + ": scale out of range (0,4]");
            }
            if (m.anchorX < 0 || m.anchorX > 1 || m.anchorY < 0 || m.anchorY > 1) {
                throw new IllegalArgumentException("Module " + m.id + ": anchor must be in [0,1]");
            }
            if (m.stickVisualVariant != null) {
                String sv = m.stickVisualVariant.trim();
                if (!sv.isEmpty()) {
                    if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                            && !GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickVisualVariant only on STICK_*");
                    }
                    if (!GamepadLayoutPresetConstants.isAllowedStickVisualVariant(sv)) {
                        throw new IllegalArgumentException("Module " + m.id + ": invalid stickVisualVariant");
                    }
                }
            }
            if (m.buttonCornerRadiusNorm != null
                    && !GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)) {
                throw new IllegalArgumentException("Module " + m.id + ": buttonCornerRadiusNorm only on BUTTON");
            }
            if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                if (!GamepadLayoutPresetConstants.isStickModuleId(m.id)) {
                    throw new IllegalArgumentException("Unknown stick module id: " + m.id);
                }
                if (GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                    if (m.dpadVariant == null || m.dpadVariant.trim().isEmpty()) {
                        throw new IllegalArgumentException("Module " + m.id + ": DPAD requires dpadVariant");
                    }
                    if (!GamepadLayoutPresetConstants.isAllowedDpadVariant(m.dpadVariant.trim())) {
                        throw new IllegalArgumentException("Module " + m.id + ": invalid dpadVariant");
                    }
                    String dv = m.dpadVariant.trim().toLowerCase(java.util.Locale.ROOT);
                    if (m.dpadSplitGapRatio != null) {
                        if (Float.isNaN(m.dpadSplitGapRatio) || Float.isInfinite(m.dpadSplitGapRatio)) {
                            throw new IllegalArgumentException("Module " + m.id + ": invalid dpadSplitGapRatio");
                        }
                        if (!GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT.equals(dv)) {
                            throw new IllegalArgumentException("Module " + m.id
                                    + ": dpadSplitGapRatio is only valid for dpadVariant split");
                        }
                        float g = m.dpadSplitGapRatio;
                        if (g < GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MIN
                                || g > GamepadLayoutPresetConstants.DPAD_SPLIT_GAP_RATIO_MAX) {
                            throw new IllegalArgumentException("Module " + m.id + ": dpadSplitGapRatio out of range");
                        }
                    }
                    if (m.dpadSplitOuterReachRatio != null) {
                        if (Float.isNaN(m.dpadSplitOuterReachRatio) || Float.isInfinite(m.dpadSplitOuterReachRatio)) {
                            throw new IllegalArgumentException("Module " + m.id + ": invalid dpadSplitOuterReachRatio");
                        }
                        if (!GamepadLayoutPresetConstants.DPAD_VARIANT_SPLIT.equals(dv)) {
                            throw new IllegalArgumentException("Module " + m.id
                                    + ": dpadSplitOuterReachRatio is only valid for dpadVariant split");
                        }
                        float o = m.dpadSplitOuterReachRatio;
                        if (o < GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MIN
                                || o > GamepadLayoutPresetConstants.DPAD_SPLIT_OUTER_REACH_RATIO_MAX) {
                            throw new IllegalArgumentException("Module " + m.id + ": dpadSplitOuterReachRatio out of range");
                        }
                        float gEff = GamepadLayoutPresetConstants.clampDpadSplitGapRatio(m.dpadSplitGapRatio);
                        if (o <= GamepadLayoutPresetConstants.minOuterReachRatioForGapRatio(gEff) - 0.001f) {
                            throw new IllegalArgumentException("Module " + m.id
                                    + ": dpadSplitOuterReachRatio too small for this dpadSplitGapRatio");
                        }
                    }
                }
                if (GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID.equals(m.id)
                        && !GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)) {
                    throw new IllegalArgumentException("stick_key_extra must be STICK_KEY");
                }
                if ("stick_left".equals(m.id)) {
                    hasStickLeft = true;
                }
                if ((GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                        || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type))
                        && (m.stickUpKey == null || m.stickLeftKey == null
                        || m.stickDownKey == null || m.stickRightKey == null)) {
                    throw new IllegalArgumentException("Module " + m.id + ": STICK_KEY/DPAD needs four direction keys");
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)) {
                if (m.hidKey == null) {
                    throw new IllegalArgumentException("Module " + m.id + ": BUTTON needs hidKey");
                }
                if (!m.id.matches("button_[a-z0-9]+")) {
                    throw new IllegalArgumentException("Invalid button id: " + m.id);
                }
                if (m.buttonCornerRadiusNorm != null) {
                    if (m.buttonCornerRadiusNorm.isNaN() || m.buttonCornerRadiusNorm.isInfinite()) {
                        throw new IllegalArgumentException("Module " + m.id + ": invalid buttonCornerRadiusNorm");
                    }
                    float c = m.buttonCornerRadiusNorm;
                    if (c < GamepadLayoutPresetConstants.BUTTON_CORNER_RADIUS_NORM_MIN
                            || c > GamepadLayoutPresetConstants.BUTTON_CORNER_RADIUS_NORM_MAX) {
                        throw new IllegalArgumentException("Module " + m.id + ": buttonCornerRadiusNorm out of range");
                    }
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
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER.equals(m.type)) {
                shoulderCount++;
                if (m.hidKey == null) {
                    throw new IllegalArgumentException("Module " + m.id + ": SHOULDER needs hidKey");
                }
                if (!GamepadLayoutPresetConstants.SHOULDER_L_ID.equals(m.id)
                        && !GamepadLayoutPresetConstants.SHOULDER_R_ID.equals(m.id)) {
                    throw new IllegalArgumentException("SHOULDER id must be shoulder_l or shoulder_r");
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER.equals(m.type)) {
                triggerCount++;
                if (m.hidKey == null) {
                    throw new IllegalArgumentException("Module " + m.id + ": TRIGGER needs hidKey");
                }
                if (!GamepadLayoutPresetConstants.TRIGGER_L_ID.equals(m.id)
                        && !GamepadLayoutPresetConstants.TRIGGER_R_ID.equals(m.id)) {
                    throw new IllegalArgumentException("TRIGGER id must be trigger_l or trigger_r");
                }
                if (m.triggerVariant != null && !m.triggerVariant.trim().isEmpty()
                        && !GamepadLayoutPresetConstants.isAllowedTriggerVariant(m.triggerVariant.trim())) {
                    throw new IllegalArgumentException("Module " + m.id + ": invalid triggerVariant");
                }
            } else {
                throw new IllegalArgumentException("Unknown module type: " + m.type);
            }
        }
        if (!hasStickLeft) {
            throw new IllegalArgumentException("Missing stick_left");
        }
        if (touchpadCount > 1) {
            throw new IllegalArgumentException("At most one touchpad module allowed");
        }
        if (mouseButtonCount > GamepadLayoutPresetConstants.MAX_MOUSE_BUTTON_MODULES) {
            throw new IllegalArgumentException("Too many MOUSE_BUTTON modules (max "
                    + GamepadLayoutPresetConstants.MAX_MOUSE_BUTTON_MODULES + ")");
        }
        if (shoulderCount > GamepadLayoutPresetConstants.MAX_SHOULDER_MODULES) {
            throw new IllegalArgumentException("Too many SHOULDER modules (max "
                    + GamepadLayoutPresetConstants.MAX_SHOULDER_MODULES + ")");
        }
        if (triggerCount > GamepadLayoutPresetConstants.MAX_TRIGGER_MODULES) {
            throw new IllegalArgumentException("Too many TRIGGER modules (max "
                    + GamepadLayoutPresetConstants.MAX_TRIGGER_MODULES + ")");
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
