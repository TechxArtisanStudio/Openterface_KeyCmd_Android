package com.openterface.keymod.gamepad;

import androidx.annotation.Nullable;

public final class GamepadLayoutPresetConstants {

    private GamepadLayoutPresetConstants() {}

    public static final String DOCUMENT_FORMAT = "openterface.gamepad.layout.v1";
    public static final int SCHEMA_VERSION_V1 = 1;
    public static final int SCHEMA_VERSION_V2 = 2;
    public static final int SCHEMA_VERSION_V3 = 3;
    /**
     * Current preset schema: v3 fields plus optional {@code SHOULDER}/{@code TRIGGER} modules,
     * {@code faceButtonTemplate}, {@code gyroEnabled}, stick/trigger metadata.
     */
    public static final int SCHEMA_VERSION = 4;

    public static final String MODULE_TYPE_STICK_KEY = "STICK_KEY";
    /**
     * Digital directional pad on any stick-shaped module id ({@link #isStickModuleId(String)}); same direction key
     * fields as {@link #MODULE_TYPE_STICK_KEY}. Requires {@link GamepadLayoutPresetDocument.GamepadModule#dpadVariant}.
     */
    public static final String MODULE_TYPE_DPAD = "DPAD";
    public static final String DPAD_VARIANT_CROSS = "cross";
    public static final String DPAD_VARIANT_DISC = "disc";
    public static final String DPAD_VARIANT_SPLIT = "split";
    public static final String DPAD_VARIANT_FLOATING = "floating";
    public static final String DPAD_VARIANT_CLICKY = "clicky";
    public static final String DPAD_VARIANT_PIVOT = "pivot";
    public static final String MODULE_TYPE_STICK_MOUSE = "STICK_MOUSE";
    public static final String MODULE_TYPE_BUTTON = "BUTTON";
    public static final String MODULE_TYPE_TOUCHPAD = "TOUCHPAD";
    /** Physical mouse buttons (HID mouse report), not keyboard keys. */
    public static final String MODULE_TYPE_MOUSE_BUTTON = "MOUSE_BUTTON";
    /** Digital shoulder / bumper (e.g. L1/R1); uses {@code hidKey}. */
    public static final String MODULE_TYPE_SHOULDER = "SHOULDER";
    /**
     * Trigger module: {@code hidKey} for digital edge; optional {@code triggerAnalog} and {@code triggerVariant}
     * for UI copy / future analog simulation.
     */
    public static final String MODULE_TYPE_TRIGGER = "TRIGGER";

    /** Optional third stick: same STICK_KEY behavior as left stick, distinct module id. */
    public static final String STICK_KEY_EXTRA_ID = "stick_key_extra";

    public static final String MOUSE_BTN_LEFT_ID = "mouse_btn_l";
    public static final String MOUSE_BTN_MIDDLE_ID = "mouse_btn_m";
    public static final String MOUSE_BTN_RIGHT_ID = "mouse_btn_r";

    public static final String SHOULDER_L_ID = "shoulder_l";
    public static final String SHOULDER_R_ID = "shoulder_r";
    public static final String TRIGGER_L_ID = "trigger_l";
    public static final String TRIGGER_R_ID = "trigger_r";

    public static final int MAX_MOUSE_BUTTON_MODULES = 3;
    public static final int MAX_SHOULDER_MODULES = 2;
    public static final int MAX_TRIGGER_MODULES = 2;

    public static final String DEFAULT_PRESET_ID = "preset_default";

    /**
     * Built-in sibling of {@link #DEFAULT_PRESET_ID}: same layout intent but {@code layout.showTwoButtons}
     * true and a {@code button_b} module. Users switch 1-button vs 2-button by changing active preset
     * (short tap / preset list), not a separate toggle.
     */
    public static final String BUILT_IN_TWO_BUTTON_PRESET_ID = "preset_two_buttons";

    /**
     * Presets that must not be removed from the store (user may still rename for display).
     */
    public static boolean isPresetDeletionProtected(@Nullable String presetId) {
        return DEFAULT_PRESET_ID.equals(presetId)
                || BUILT_IN_TWO_BUTTON_PRESET_ID.equals(presetId);
    }

    /**
     * Stick-like module ids: {@code stick_} plus lowercase letters, digits, and underscores
     * (e.g. {@code stick_left}, {@code stick_right}, {@code stick_key_extra}, {@code stick_aux_1}).
     */
    public static boolean isStickModuleId(@Nullable String id) {
        return id != null && id.matches("stick_[a-z0-9_]+");
    }

    /**
     * BUTTON only: corner radius of the face shape as a fraction of half-width (same as legacy circle when {@code 1}).
     * {@code 0} = square with sharp corners; {@code 1} = circle (pill with radius equal to half-size).
     */
    public static final float BUTTON_CORNER_RADIUS_NORM_MIN = 0f;
    public static final float BUTTON_CORNER_RADIUS_NORM_MAX = 1f;
    public static final float BUTTON_CORNER_RADIUS_NORM_DEFAULT = 1f;

    /** @return clamped {@code [}{@link #BUTTON_CORNER_RADIUS_NORM_MIN}, {@link #BUTTON_CORNER_RADIUS_NORM_MAX}{@code ]}; null or non-finite → default (circle). */
    public static float clampButtonCornerRadiusNorm(@Nullable Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return BUTTON_CORNER_RADIUS_NORM_DEFAULT;
        }
        return Math.max(BUTTON_CORNER_RADIUS_NORM_MIN,
                Math.min(BUTTON_CORNER_RADIUS_NORM_MAX, v));
    }

    /** Default stick positions: PlayStation-style horizontal pair. */
    public static final String STICK_LAYOUT_SYMMETRICAL = "symmetrical";
    /** Xbox / Switch Pro–style offset sticks. */
    public static final String STICK_LAYOUT_OFFSET = "offset";
    /** Alias of {@link #STICK_LAYOUT_SYMMETRICAL} (parallel sticks). */
    public static final String STICK_LAYOUT_PARALLEL = "parallel";

    /** Optional hint for face button cluster geometry (templates). */
    public static final String FACE_TEMPLATE_NINTENDO_DIAMOND = "nintendo_diamond";
    public static final String FACE_TEMPLATE_XBOX_ABXY = "xbox_abxy";
    public static final String FACE_TEMPLATE_PLAYSTATION_SYMBOLS = "playstation_symbols";

    /** Thumb cap look (draw-only for most values). */
    public static final String STICK_VISUAL_DEFAULT = "default";
    public static final String STICK_VISUAL_CONCAVE = "concave";
    public static final String STICK_VISUAL_CONVEX = "convex";
    public static final String STICK_VISUAL_LOW_PROFILE = "low_profile";
    public static final String STICK_VISUAL_C_STICK = "c_stick";
    /** Cosmetic / educational label only. */
    public static final String STICK_VISUAL_HALL_EFFECT = "hall_effect";

    public static final String TRIGGER_VARIANT_DIGITAL = "digital";
    public static final String TRIGGER_VARIANT_ANALOG = "analog";
    public static final String TRIGGER_VARIANT_HAIR = "hair";
    public static final String TRIGGER_VARIANT_ADAPTIVE = "adaptive";

    public static boolean isAllowedDpadVariant(@Nullable String v) {
        return DPAD_VARIANT_CROSS.equals(v)
                || DPAD_VARIANT_DISC.equals(v)
                || DPAD_VARIANT_SPLIT.equals(v)
                || DPAD_VARIANT_FLOATING.equals(v)
                || DPAD_VARIANT_CLICKY.equals(v)
                || DPAD_VARIANT_PIVOT.equals(v);
    }

    public static boolean isAllowedFaceButtonTemplate(@Nullable String t) {
        if (t == null || t.trim().isEmpty()) {
            return true;
        }
        String x = t.trim();
        return FACE_TEMPLATE_NINTENDO_DIAMOND.equals(x)
                || FACE_TEMPLATE_XBOX_ABXY.equals(x)
                || FACE_TEMPLATE_PLAYSTATION_SYMBOLS.equals(x);
    }

    public static boolean isAllowedStickVisualVariant(@Nullable String v) {
        if (v == null || v.trim().isEmpty()) {
            return true;
        }
        String x = v.trim().toLowerCase(java.util.Locale.ROOT);
        return STICK_VISUAL_DEFAULT.equals(x)
                || STICK_VISUAL_CONCAVE.equals(x)
                || STICK_VISUAL_CONVEX.equals(x)
                || STICK_VISUAL_LOW_PROFILE.equals(x)
                || STICK_VISUAL_C_STICK.equals(x)
                || STICK_VISUAL_HALL_EFFECT.equals(x);
    }

    public static boolean isAllowedTriggerVariant(@Nullable String v) {
        if (v == null || v.trim().isEmpty()) {
            return true;
        }
        String x = v.trim().toLowerCase(java.util.Locale.ROOT);
        return TRIGGER_VARIANT_DIGITAL.equals(x)
                || TRIGGER_VARIANT_ANALOG.equals(x)
                || TRIGGER_VARIANT_HAIR.equals(x)
                || TRIGGER_VARIANT_ADAPTIVE.equals(x);
    }

    /**
     * Split D-pad only: gap between the inner corners of the four segments (dead zone in the middle),
     * as a fraction of the pad radius ({@code half}). JSON {@code null} uses {@link #DPAD_SPLIT_GAP_RATIO_DEFAULT}.
     */
    public static final float DPAD_SPLIT_GAP_RATIO_MIN = 0.05f;
    public static final float DPAD_SPLIT_GAP_RATIO_MAX = 0.38f;
    public static final float DPAD_SPLIT_GAP_RATIO_DEFAULT = 0.14f;

    public static float clampDpadSplitGapRatio(@Nullable Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return DPAD_SPLIT_GAP_RATIO_DEFAULT;
        }
        return Math.max(DPAD_SPLIT_GAP_RATIO_MIN, Math.min(DPAD_SPLIT_GAP_RATIO_MAX, v));
    }

    /**
     * Split D-pad only: distance from layout center to the <b>outer</b> edge of each segment, as a fraction of
     * {@code half} (same basis as {@link #DPAD_SPLIT_GAP_RATIO_DEFAULT}). {@code 1.0} matches the classic layout
     * (keys reach the module edge). Smaller values pull keys toward the center.
     */
    public static final float DPAD_SPLIT_OUTER_REACH_RATIO_MIN = 0.28f;
    public static final float DPAD_SPLIT_OUTER_REACH_RATIO_MAX = 1.0f;
    public static final float DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT = 1.0f;

    /**
     * Split pads: fixed along-axis thickness as a fraction of {@code half} (does not shrink when outer reach changes).
     */
    public static final float DPAD_SPLIT_SEGMENT_DEPTH_NORM = 0.52f;
    /**
     * Split pads: fixed cross-axis span as a fraction of {@code half} (width of up/down pads, height of left/right).
     */
    public static final float DPAD_SPLIT_SEGMENT_BREADTH_NORM = 0.44f;

    public static float clampDpadSplitOuterReachRatio(@Nullable Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return DPAD_SPLIT_OUTER_REACH_RATIO_DEFAULT;
        }
        return Math.max(DPAD_SPLIT_OUTER_REACH_RATIO_MIN,
                Math.min(DPAD_SPLIT_OUTER_REACH_RATIO_MAX, v));
    }

    /**
     * Minimum outer-reach ratio for a given gap so fixed-size segments fit between inner gap and outer edge
     * ({@code outer * half >= inner + depth + margin}).
     */
    public static float minOuterReachRatioForGapRatio(float gapRatioClamped) {
        float need = gapRatioClamped * 0.5f + DPAD_SPLIT_SEGMENT_DEPTH_NORM + 0.02f;
        return Math.min(DPAD_SPLIT_OUTER_REACH_RATIO_MAX,
                Math.max(DPAD_SPLIT_OUTER_REACH_RATIO_MIN, need));
    }

    /**
     * Largest split gap ratio in {@code [}{@link #DPAD_SPLIT_GAP_RATIO_MIN}, {@code upperBoundGapRatio}{@code ]}
     * whose {@link #minOuterReachRatioForGapRatio} is at most {@code outerReachRatio} (after clamping both).
     * Used when the user pulls “distance to keys” inward: shrink the center gap first instead of only pushing
     * outer reach back out.
     */
    public static float largestGapRatioUpToOuterReach(float outerReachRatio, float upperBoundGapRatio) {
        float o = clampDpadSplitOuterReachRatio(outerReachRatio);
        float upper = clampDpadSplitGapRatio(upperBoundGapRatio);
        float gMin = DPAD_SPLIT_GAP_RATIO_MIN;
        if (minOuterReachRatioForGapRatio(upper) <= o) {
            return upper;
        }
        if (minOuterReachRatioForGapRatio(gMin) > o) {
            return gMin;
        }
        float lo = gMin;
        float hi = upper;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5f;
            if (minOuterReachRatioForGapRatio(mid) <= o) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return clampDpadSplitGapRatio(lo);
    }
}
