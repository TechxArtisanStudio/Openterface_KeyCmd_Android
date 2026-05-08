package com.openterface.keymod.gamepad;

import androidx.annotation.NonNull;
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

    /**
     * Cross / floating / clicky DPAD arm overlay for rendering (handles legacy {@link GamepadModule#dpadDirectionIconsVisible}).
     */
    @NonNull
    public static String effectiveDpadCrossArmDecoration(@Nullable GamepadModule m) {
        if (m == null || !GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
            return GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_NONE;
        }
        if (!GamepadDpadVariantArt.usesCrossArmDecoration(m.dpadVariant)) {
            return GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_NONE;
        }
        if (m.dpadCrossArmDecoration != null && !m.dpadCrossArmDecoration.trim().isEmpty()) {
            return GamepadLayoutPresetConstants.normalizeDpadCrossArmDecoration(m.dpadCrossArmDecoration);
        }
        if (Boolean.TRUE.equals(m.dpadDirectionIconsVisible)) {
            return GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_ICONS;
        }
        return GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_LABELS;
    }

    private static void normalizeDpadCrossArmDecorationOnModule(@Nullable GamepadModule m) {
        if (m == null || !GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
            return;
        }
        if (!GamepadDpadVariantArt.usesCrossArmDecoration(m.dpadVariant)) {
            m.dpadCrossArmDecoration = null;
            m.dpadDirectionIconsVisible = null;
            return;
        }
        if (m.dpadCrossArmDecoration != null && !m.dpadCrossArmDecoration.trim().isEmpty()) {
            if (!GamepadLayoutPresetConstants.isAllowedDpadCrossArmDecoration(m.dpadCrossArmDecoration)) {
                throw new IllegalArgumentException("Module " + m.id + ": invalid dpadCrossArmDecoration");
            }
            m.dpadCrossArmDecoration =
                    GamepadLayoutPresetConstants.normalizeDpadCrossArmDecoration(m.dpadCrossArmDecoration);
            m.dpadDirectionIconsVisible = null;
            return;
        }
        if (Boolean.TRUE.equals(m.dpadDirectionIconsVisible)) {
            m.dpadCrossArmDecoration = GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_ICONS;
        } else {
            m.dpadCrossArmDecoration = GamepadLayoutPresetConstants.DPAD_CROSS_ARM_DECORATION_LABELS;
        }
        m.dpadDirectionIconsVisible = null;
    }

    public static class Meta {
        public String id;
        public String displayName;
        public String description;
        public String exportedAt;
        public String sourceAppVersion;
        /** Optional human-readable author for exported JSON; null when unset. */
        @Nullable public String creator;
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
         * Portable interchange only: encoding for {@link #backgroundImageData}
         * (e.g. {@link GamepadLayoutPresetConstants#BACKGROUND_EMBED_ENCODING_BASE64}).
         * Cleared after the image is written to {@link #backgroundImageFile} so layout JSON in prefs stays small.
         */
        @Nullable public String backgroundImageEncoding;
        /** Declared media type; must match decoded bytes (e.g. {@link GamepadLayoutPresetConstants#BACKGROUND_MEDIA_TYPE_PNG}). */
        @Nullable public String backgroundImageMediaType;
        /** Raw base64 body (no {@code data:} URL prefix). */
        @Nullable public String backgroundImageData;
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
        /**
         * Custom canvas fill when no {@link #backgroundImageFile} (packed ARGB). Null = use built-in default gradient
         * unless only {@link #backgroundPattern} is set (pattern over default gradient).
         */
        @Nullable public Integer backgroundFillArgb;
        /**
         * Subtle procedural pattern over fill or default gradient; see
         * {@link GamepadLayoutPresetConstants#BACKGROUND_PATTERN_NONE} and related ids.
         */
        @Nullable public String backgroundPattern;
    }

    /** Preset JSON object {@code gestureLock}: four diagonal slots. */
    public static class GestureLockConfig {
        @Nullable public GestureLockSlot upLeft;
        @Nullable public GestureLockSlot upRight;
        @Nullable public GestureLockSlot downLeft;
        @Nullable public GestureLockSlot downRight;
    }

    /** One slot: {@code action} plus optional override HID codes for {@code key_*} actions. */
    public static class GestureLockSlot {
        @Nullable public String action;
        @Nullable public Integer hidKey;
        @Nullable public Integer modifierMask;
    }

    /**
     * One drawable plus touch target on the gamepad canvas.
     * <p><b>Layers:</b> {@code id} is <em>which</em> control (e.g. {@code stick_left}, {@code stick_right}).
     * {@code type} is <em>what the host receives</em>
     * ({@code STICK_KEY}, {@code STICK_MOUSE}, {@code DPAD}, {@code BUTTON}, …). Fields such as
     * {@code dpadVariant}, {@code dpadSplitGapRatio}, {@code stickMouseSensitivity}, and
     * {@code stickVisualVariant} are <em>parameters</em> on that same module, not separate module types.
     * See {@code docs/USER_GUIDE.md} (Gamepad module model) and {@code .cursor/plans/gamepad_module_taxonomy.plan.md}
     * for user vocabulary and roadmap.
     */
    public static class GamepadModule {
        public String id;
        public String type;
        public int zIndex;
        public float scale = 1.0f;
        public float anchorX;
        public float anchorY;
        @Nullable public Float widthNorm;
        @Nullable public Float heightNorm;
        /**
         * Optional short label (max {@link GamepadCapLabels#MAX_CAP_LABEL_CODE_POINTS} code points): centered on
         * BUTTON / MOUSE_BUTTON / SHOULDER / TRIGGER caps; STICK_KEY / STICK_MOUSE thumb cap center; DPAD hub center;
         * TOUCHPAD center title (defaults to {@code TOUCHPAD} when null).
         */
        @Nullable public String displayLabel;
        /**
         * Optional ARGB for {@link #displayLabel}. Null = theme / module-style default (face button cap style for
         * BUTTON / MOUSE_BUTTON; blended accent for touchpad title; white on shoulder/trigger when unset; stick /
         * D-pad hub uses theme-appropriate contrast).
         */
        @Nullable public Integer displayLabelColorArgb;
        /** STICK_* / DPAD: HID key codes for four-way digital input (virtual stick ring or D-pad). */
        @Nullable public Integer stickUpKey;
        @Nullable public Integer stickLeftKey;
        @Nullable public Integer stickDownKey;
        @Nullable public Integer stickRightKey;
        /**
         * Optional hub / inner-disc key for STICK_KEY or cross DPAD. {@code null} = hub sends no key
         * (dead zone until user maps one in stick config).
         */
        @Nullable public Integer stickCenterKey;
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
        /**
         * DPAD cross-style arms only ({@code cross}, {@code floating}, {@code clicky}): {@link GamepadLayoutPresetConstants#DPAD_CROSS_ARM_DECORATION_NONE},
         * {@link GamepadLayoutPresetConstants#DPAD_CROSS_ARM_DECORATION_LABELS}, or {@link GamepadLayoutPresetConstants#DPAD_CROSS_ARM_DECORATION_ICONS}.
         * Ignored for split/disc/pivot; normalized when validating presets.
         */
        @Nullable public String dpadCrossArmDecoration;
        /**
         * Legacy: when {@code true} and {@link #dpadCrossArmDecoration} is unset, treated as {@code icons}.
         * Cleared during validation when {@link #dpadCrossArmDecoration} is canonical.
         */
        @Nullable public Boolean dpadDirectionIconsVisible;
        /** Optional thumbstick cap look (reserved; null = default). */
        @Nullable public String stickVisualVariant;
        /** BUTTON: primary HID key and modifier bitmask (same encoding as GamepadFragment). */
        @Nullable public Integer hidKey;
        @Nullable public Integer modifierMask;
        /**
         * BUTTON: when {@code false}, do not draw the mapped-key pill under the cap; {@code null} = show
         * (default). Independent of the global “key mapping hints” toolbar toggle.
         */
        @Nullable public Boolean mappedKeyLabelVisible;
        /**
         * BUTTON / SHOULDER / TRIGGER / MOUSE_BUTTON: when {@code true}, swipe hold-lock gesture is enabled
         * (legacy: vertical up/down only when {@link #gestureLock} is absent or has no non-{@code none}
         * actions). {@code null} or {@code false} = off unless {@link #gestureLock} supplies actions.
         */
        @Nullable public Boolean keyboardHoldLock;
        /**
         * Optional per-diagonal swipe actions (hold lock, turbo, alternate key). When present with any
         * non-{@code none} slot, diagonal classification is used; otherwise legacy vertical hold-lock
         * applies if {@link #keyboardHoldLock} is true.
         */
        @Nullable public GestureLockConfig gestureLock;

        /**
         * BUTTON: shape from square ({@code 0}) to circle ({@code 1}); see
         * {@link GamepadLayoutPresetConstants#clampButtonCornerRadiusNorm}.
         */
        @Nullable public Float buttonCornerRadiusNorm;
        /**
         * BUTTON: horizontal half-size relative to the layout face radius ({@code null} = {@code 1});
         * see {@link GamepadLayoutPresetConstants#clampButtonWidthRatio}.
         */
        @Nullable public Float buttonWidthRatio;
        /**
         * BUTTON: vertical half-size relative to the layout face radius ({@code null} = {@code 1});
         * see {@link GamepadLayoutPresetConstants#clampButtonHeightRatio}.
         */
        @Nullable public Float buttonHeightRatio;
        /** BUTTON: clockwise rotation in degrees ({@code null} = {@code 0}); see {@link GamepadLayoutPresetConstants#clampButtonRotationDeg}. */
        @Nullable public Float buttonRotationDeg;
        /** MOUSE_BUTTON: 1 = left, 2 = middle, 3 = right (same convention as {@code sendMouseClick}). */
        @Nullable public Integer mouseButton;
        /** TRIGGER: reserved for future analog simulation; false = digital edge on {@code hidKey}. */
        @Nullable public Boolean triggerAnalog;
        /** TRIGGER: {@link GamepadLayoutPresetConstants#TRIGGER_VARIANT_DIGITAL} and siblings (UI / future use). */
        @Nullable public String triggerVariant;
        /**
         * STICK_MOUSE only: pointer movement gain {@code [0.25, 4]}. Null uses {@link LayoutGlobals#rightStickMouseGain}
         * or built-in default.
         */
        @Nullable public Float stickMouseSensitivity;
        /**
         * STICK_MOUSE only: optional hub (inner disc) keyboard HID key while the finger stays in the hub.
         * When non-null, {@link #stickPointerCenterMouseMask} is ignored for hub presses.
         */
        @Nullable public Integer stickPointerCenterKey;
        /**
         * STICK_MOUSE only: HID relative-mouse button mask for hub press when {@link #stickPointerCenterKey} is null.
         * Use {@code 1} (left), {@code 2} (right), or {@code 4} (middle); null defaults to left ({@code 1}).
         */
        @Nullable public Integer stickPointerCenterMouseMask;
        /**
         * Optional per-module accent (ARGB). Null = theme default for sticks/D-pad and template colors for face
         * buttons; see {@link GamepadModuleAccent}.
         */
        @Nullable public Integer moduleAccentArgb;
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
        if (d.schemaVersion < GamepadLayoutPresetConstants.SCHEMA_VERSION_V1
                || d.schemaVersion > GamepadLayoutPresetConstants.SCHEMA_VERSION) {
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
        validateBackgroundEmbed(d.layout);
        validateBackgroundFillAndPattern(d.layout);
        validateMetaCreator(d.meta);
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
        GamepadModule btnA = findModule(d.modules, "button_a");
        if (btnA != null) {
            if (!GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(btnA.type)) {
                throw new IllegalArgumentException("button_a must be BUTTON type");
            }
            if (btnA.hidKey == null) {
                throw new IllegalArgumentException("button_a missing hidKey");
            }
        }
        if (d.layout.showTwoButtons) {
            if (btnA == null) {
                throw new IllegalArgumentException("showTwoButtons requires BUTTON module id=button_a");
            }
            GamepadModule btnB = findModule(d.modules, "button_b");
            if (btnB == null || !GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(btnB.type)
                    || btnB.hidKey == null) {
                throw new IllegalArgumentException("showTwoButtons requires BUTTON module id=button_b with hidKey");
            }
        }
        int mouseButtonCount = 0;
        int shoulderCount = 0;
        int triggerCount = 0;
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
            if (m.moduleAccentArgb != null) {
                int a = argbAlphaFromPackedInt(m.moduleAccentArgb);
                if (a < 64) {
                    throw new IllegalArgumentException("Module " + m.id + ": moduleAccentArgb alpha must be >= 64");
                }
            }
            if (m.displayLabel != null) {
                String dl = m.displayLabel.trim();
                if (!dl.isEmpty()
                        && GamepadCapLabels.codePointCount(dl) > GamepadCapLabels.MAX_CAP_LABEL_CODE_POINTS) {
                    throw new IllegalArgumentException("Module " + m.id + ": displayLabel exceeds "
                            + GamepadCapLabels.MAX_CAP_LABEL_CODE_POINTS + " Unicode code points");
                }
            }
            if (m.displayLabelColorArgb != null) {
                int a = argbAlphaFromPackedInt(m.displayLabelColorArgb);
                if (a < 64) {
                    throw new IllegalArgumentException("Module " + m.id + ": displayLabelColorArgb alpha must be >= 64");
                }
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
            if ((m.buttonWidthRatio != null || m.buttonHeightRatio != null || m.buttonRotationDeg != null)
                    && !GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)) {
                throw new IllegalArgumentException("Module " + m.id + ": button shape ratios/rotation only on BUTTON");
            }
            if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                if (!GamepadLayoutPresetConstants.isStickModuleId(m.id)) {
                    throw new IllegalArgumentException("Unknown stick module id: " + m.id);
                }
                if (m.id.startsWith("stick_left_") && !GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)) {
                    throw new IllegalArgumentException("Invalid stick_left_* module id: " + m.id);
                }
                if (GamepadLayoutPresetConstants.isObsoleteRemovedThumbStickId(m.id)) {
                    throw new IllegalArgumentException("Obsolete stick module id (no longer supported): " + m.id);
                }
                if (GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)
                        && GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                    String dv = m.dpadVariant != null ? m.dpadVariant.trim().toLowerCase(java.util.Locale.ROOT) : "";
                    if (!GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS.equals(dv)) {
                        throw new IllegalArgumentException("Module " + m.id
                                + ": extra left DPAD supports dpadVariant cross only");
                    }
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
                    normalizeDpadCrossArmDecorationOnModule(m);
                }
                if (m.stickMouseSensitivity != null) {
                    if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickMouseSensitivity only on STICK_MOUSE");
                    }
                    float s = m.stickMouseSensitivity;
                    if (Float.isNaN(s) || Float.isInfinite(s) || s < 0.25f || s > 4.0f) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickMouseSensitivity must be in [0.25, 4]");
                    }
                }
                if ((GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                        || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type))
                        && (m.stickUpKey == null || m.stickLeftKey == null
                        || m.stickDownKey == null || m.stickRightKey == null)) {
                    throw new IllegalArgumentException("Module " + m.id + ": STICK_KEY/DPAD needs four direction keys");
                }
                if (m.stickCenterKey != null) {
                    if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                            && !GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickCenterKey only on STICK_KEY/DPAD");
                    }
                    int c = m.stickCenterKey;
                    if (c < 1 || c > 255) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickCenterKey out of range");
                    }
                }
                if (m.stickPointerCenterKey != null) {
                    if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickPointerCenterKey only on STICK_MOUSE");
                    }
                    int pk = m.stickPointerCenterKey;
                    if (pk < 1 || pk > 255) {
                        throw new IllegalArgumentException("Module " + m.id + ": stickPointerCenterKey out of range");
                    }
                }
                if (m.stickPointerCenterMouseMask != null) {
                    if (!GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type)) {
                        throw new IllegalArgumentException("Module " + m.id
                                + ": stickPointerCenterMouseMask only on STICK_MOUSE");
                    }
                    int mk = m.stickPointerCenterMouseMask;
                    if (mk != 1 && mk != 2 && mk != 4) {
                        throw new IllegalArgumentException("Module " + m.id
                                + ": stickPointerCenterMouseMask must be 1, 2, or 4");
                    }
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
                if (m.buttonWidthRatio != null) {
                    if (m.buttonWidthRatio.isNaN() || m.buttonWidthRatio.isInfinite()) {
                        throw new IllegalArgumentException("Module " + m.id + ": invalid buttonWidthRatio");
                    }
                    float w = m.buttonWidthRatio;
                    if (w < GamepadLayoutPresetConstants.BUTTON_WIDTH_RATIO_MIN
                            || w > GamepadLayoutPresetConstants.BUTTON_WIDTH_RATIO_MAX) {
                        throw new IllegalArgumentException("Module " + m.id + ": buttonWidthRatio out of range");
                    }
                }
                if (m.buttonHeightRatio != null) {
                    if (m.buttonHeightRatio.isNaN() || m.buttonHeightRatio.isInfinite()) {
                        throw new IllegalArgumentException("Module " + m.id + ": invalid buttonHeightRatio");
                    }
                    float hh = m.buttonHeightRatio;
                    if (hh < GamepadLayoutPresetConstants.BUTTON_HEIGHT_RATIO_MIN
                            || hh > GamepadLayoutPresetConstants.BUTTON_HEIGHT_RATIO_MAX) {
                        throw new IllegalArgumentException("Module " + m.id + ": buttonHeightRatio out of range");
                    }
                }
                if (m.buttonRotationDeg != null) {
                    if (m.buttonRotationDeg.isNaN() || m.buttonRotationDeg.isInfinite()) {
                        throw new IllegalArgumentException("Module " + m.id + ": invalid buttonRotationDeg");
                    }
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD.equals(m.type)) {
                if (!GamepadLayoutPresetConstants.isTouchpadModuleId(m.id)) {
                    throw new IllegalArgumentException("Invalid TOUCHPAD id (expected touchpad_<n>): " + m.id);
                }
                if (m.widthNorm == null || m.heightNorm == null
                        || m.widthNorm <= 0 || m.widthNorm > 1 || m.heightNorm <= 0 || m.heightNorm > 1) {
                    throw new IllegalArgumentException("Module " + m.id + ": TOUCHPAD needs widthNorm/heightNorm in (0,1]");
                }
            } else if (GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(m.type)) {
                mouseButtonCount++;
                boolean canonicalMouseBtn = GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID.equals(m.id)
                        || GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID.equals(m.id)
                        || GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID.equals(m.id);
                if (!canonicalMouseBtn && !GamepadLayoutPresetConstants.isMouseButtonCopyModuleId(m.id)) {
                    throw new IllegalArgumentException("Invalid MOUSE_BUTTON id: " + m.id);
                }
                if (m.mouseButton == null || m.mouseButton < 1 || m.mouseButton > 3) {
                    throw new IllegalArgumentException("Module " + m.id + ": MOUSE_BUTTON needs mouseButton 1–3");
                }
                if (canonicalMouseBtn) {
                    if ((GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID.equals(m.id) && m.mouseButton != 1)
                            || (GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID.equals(m.id) && m.mouseButton != 2)
                            || (GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID.equals(m.id) && m.mouseButton != 3)) {
                        throw new IllegalArgumentException("Module " + m.id + ": id does not match mouseButton value");
                    }
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
            if (GamepadGestureLock.moduleGesturesEnabled(m)) {
                if (!GamepadGestureLock.gesturesAllowedModuleType(m.type)) {
                    throw new IllegalArgumentException(
                            "Module " + m.id
                                    + ": keyboardHoldLock / gestureLock is only valid for BUTTON, SHOULDER, TRIGGER, or MOUSE_BUTTON");
                }
            }
            GamepadGestureLock.validateGestureLockOnModule(m);
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

    /** ARGB packed int; JVM-safe (no {@code android.graphics.Color} stub needed in unit tests). */
    private static int argbAlphaFromPackedInt(int colorArgb) {
        return (colorArgb >>> 24) & 0xFF;
    }

    private static void validateMetaCreator(@Nullable Meta meta) throws IllegalArgumentException {
        if (meta == null || meta.creator == null) {
            return;
        }
        String c = meta.creator.trim();
        if (c.isEmpty()) {
            meta.creator = null;
            return;
        }
        if (c.length() > GamepadLayoutPresetConstants.META_CREATOR_MAX_CHARS) {
            throw new IllegalArgumentException(
                    "meta.creator exceeds " + GamepadLayoutPresetConstants.META_CREATOR_MAX_CHARS + " characters");
        }
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                throw new IllegalArgumentException("meta.creator must not contain line breaks or tabs");
            }
        }
        meta.creator = c;
    }

    /**
     * Validates and normalizes {@code meta.creator} (trim, length, no line breaks) for UI / prefs persistence.
     */
    public static void validateMetaCreatorForUi(@NonNull Meta meta) throws IllegalArgumentException {
        validateMetaCreator(meta);
    }

    private static void validateBackgroundEmbed(LayoutGlobals L) {
        String enc = trimOrNull(L.backgroundImageEncoding);
        String mime = trimOrNull(L.backgroundImageMediaType);
        String data = stripBase64Whitespace(L.backgroundImageData);
        boolean hasData = data != null && !data.isEmpty();
        boolean any = enc != null || mime != null || hasData;
        if (!any) {
            return;
        }
        if (enc == null || mime == null || !hasData) {
            throw new IllegalArgumentException(
                    "layout backgroundImageEncoding, backgroundImageMediaType, and backgroundImageData must all be set together");
        }
        if (!GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64.equalsIgnoreCase(enc)) {
            throw new IllegalArgumentException("Unsupported backgroundImageEncoding");
        }
        if (!GamepadLayoutPresetConstants.isAllowedBackgroundEmbedMediaType(mime)) {
            throw new IllegalArgumentException("Unsupported backgroundImageMediaType");
        }
        if (data.length() > GamepadLayoutPresetConstants.MAX_BACKGROUND_EMBED_BASE64_CHARS) {
            throw new IllegalArgumentException("backgroundImageData too large");
        }
    }

    private static void validateBackgroundFillAndPattern(LayoutGlobals L) {
        if (L.backgroundPattern != null) {
            String raw = L.backgroundPattern.trim();
            if (raw.isEmpty()) {
                L.backgroundPattern = null;
            } else if (!GamepadLayoutPresetConstants.isAllowedBackgroundPattern(raw)) {
                throw new IllegalArgumentException("layout.backgroundPattern is not a known pattern id");
            } else if (GamepadLayoutPresetConstants.BACKGROUND_PATTERN_NONE.equalsIgnoreCase(raw)) {
                L.backgroundPattern = null;
            } else {
                L.backgroundPattern = raw.toLowerCase(java.util.Locale.ROOT);
            }
        }
    }

    @Nullable
    private static String trimOrNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Nullable
    private static String stripBase64Whitespace(@Nullable String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
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
