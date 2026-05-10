package com.openterface.keymod.gamepad;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GamepadLayoutPresetConstants {

    private GamepadLayoutPresetConstants() {}

    public static final String DOCUMENT_FORMAT = "openterface.gamepad.layout.v1";
    public static final int SCHEMA_VERSION_V1 = 1;
    public static final int SCHEMA_VERSION_V2 = 2;
    public static final int SCHEMA_VERSION_V3 = 3;
    /** v4: shoulder/trigger modules, face templates, gyro flag, stick metadata. */
    public static final int SCHEMA_VERSION_V4 = 4;
    public static final int SCHEMA_VERSION_V5 = 5;
    /** v6 removes legacy extra-thumb ids (see {@link #isObsoleteRemovedThumbStickId(String)}). */
    public static final int SCHEMA_VERSION_V6 = 6;
    /**
     * v7 makes the primary left thumb ({@code stick_left}) optional alongside
     * {@code stick_left_2+} (validation only; no automatic module rewrites).
     */
    public static final int SCHEMA_VERSION_V7 = 7;
    /**
     * v8 adds optional per-module {@link GamepadLayoutPresetDocument.GamepadModule#gestureLock} (diagonal
     * swipe actions: hold lock, turbo, optional alternate key).
     */
    public static final int SCHEMA_VERSION_V8 = 8;
    /** Current preset schema. */
    public static final int SCHEMA_VERSION = SCHEMA_VERSION_V8;

    /**
     * Dynamic layout (BUTTON, STICK_*, DPAD, MOUSE_BUTTON, SHOULDER/TRIGGER) scales module draw sizes by
     * {@code min(contentW, contentH) / REFERENCE} so presets stay usable across phones and aspect ratios.
     * Legacy fixed-pixel bases (100 / 180) matched roughly this reference on a typical landscape phone.
     */
    public static final int DYNAMIC_LAYOUT_REFERENCE_MIN_EDGE_PX = 800;
    /** Lower clamp for {@link #contentMinEdgeScaleFactor(int, int)} (very small windows). */
    public static final float DYNAMIC_LAYOUT_SCALE_MIN = 0.35f;
    /** Upper clamp so tablets do not inflate controls without bound. */
    public static final float DYNAMIC_LAYOUT_SCALE_MAX = 2.5f;

    /**
     * Scale factor for preset module sizes given the gamepad <em>content</em> width and height (after insets).
     * At {@link #DYNAMIC_LAYOUT_REFERENCE_MIN_EDGE_PX} px min-edge, returns {@code 1.0f} (legacy pixel bases).
     */
    public static float contentMinEdgeScaleFactor(int contentWidthPx, int contentHeightPx) {
        int w = Math.max(1, contentWidthPx);
        int h = Math.max(1, contentHeightPx);
        int minEdge = Math.min(w, h);
        float raw = minEdge / (float) DYNAMIC_LAYOUT_REFERENCE_MIN_EDGE_PX;
        if (raw < DYNAMIC_LAYOUT_SCALE_MIN) {
            return DYNAMIC_LAYOUT_SCALE_MIN;
        }
        if (raw > DYNAMIC_LAYOUT_SCALE_MAX) {
            return DYNAMIC_LAYOUT_SCALE_MAX;
        }
        return raw;
    }

    /** Max length for {@link GamepadLayoutPresetDocument.Meta#creator} after trim. */
    public static final int META_CREATOR_MAX_CHARS = 64;

    /** JSON / Gson field names on {@link GamepadLayoutPresetDocument.GestureLockConfig}. */
    public static final String GESTURE_LOCK_SLOT_UP_LEFT = "upLeft";
    public static final String GESTURE_LOCK_SLOT_UP_RIGHT = "upRight";
    public static final String GESTURE_LOCK_SLOT_DOWN_LEFT = "downLeft";
    public static final String GESTURE_LOCK_SLOT_DOWN_RIGHT = "downRight";

    public static final String GESTURE_LOCK_ACTION_NONE = "none";
    public static final String GESTURE_LOCK_ACTION_HOLD_LOCK = "hold_lock";
    public static final String GESTURE_LOCK_ACTION_TURBO = "turbo";
    public static final String GESTURE_LOCK_ACTION_KEY_HOLD = "key_hold";
    public static final String GESTURE_LOCK_ACTION_KEY_TURBO = "key_turbo";

    public static boolean isAllowedGestureLockAction(@Nullable String action) {
        if (action == null || action.trim().isEmpty()) {
            return false;
        }
        String a = action.trim();
        return GESTURE_LOCK_ACTION_NONE.equals(a)
                || GESTURE_LOCK_ACTION_HOLD_LOCK.equals(a)
                || GESTURE_LOCK_ACTION_TURBO.equals(a)
                || GESTURE_LOCK_ACTION_KEY_HOLD.equals(a)
                || GESTURE_LOCK_ACTION_KEY_TURBO.equals(a);
    }

    /** Max decoded bytes for embedded gamepad background image (JSON interchange). */
    public static final int MAX_BACKGROUND_EMBED_DECODED_BYTES = 6 * 1024 * 1024;
    /**
     * Max base64 character count for embedded background (~4/3 of decoded size + padding).
     * Decoded size must not exceed {@link #MAX_BACKGROUND_EMBED_DECODED_BYTES}.
     */
    public static final int MAX_BACKGROUND_EMBED_BASE64_CHARS = 8_400_000;
    public static final String BACKGROUND_EMBED_ENCODING_BASE64 = "base64";
    public static final String BACKGROUND_MEDIA_TYPE_PNG = "image/png";
    public static final String BACKGROUND_MEDIA_TYPE_JPEG = "image/jpeg";
    public static final String BACKGROUND_MEDIA_TYPE_WEBP = "image/webp";

    /** No pattern overlay (JSON may omit or use this value). */
    public static final String BACKGROUND_PATTERN_NONE = "none";
    public static final String BACKGROUND_PATTERN_DOTS = "dots";
    public static final String BACKGROUND_PATTERN_MICRO_GRID = "micro_grid";
    public static final String BACKGROUND_PATTERN_DIAGONAL_HATCH = "diagonal_hatch";
    public static final String BACKGROUND_PATTERN_NOISE = "noise";

    public static boolean isAllowedBackgroundPattern(@Nullable String p) {
        if (p == null) {
            return true;
        }
        String x = p.trim();
        if (x.isEmpty() || BACKGROUND_PATTERN_NONE.equalsIgnoreCase(x)) {
            return true;
        }
        String y = x.toLowerCase(java.util.Locale.ROOT);
        return BACKGROUND_PATTERN_DOTS.equals(y)
                || BACKGROUND_PATTERN_MICRO_GRID.equals(y)
                || BACKGROUND_PATTERN_DIAGONAL_HATCH.equals(y)
                || BACKGROUND_PATTERN_NOISE.equals(y);
    }

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

    /**
     * Cross / floating / clicky D-pad arms only: draw nothing on the four arms (hub unchanged).
     */
    public static final String DPAD_CROSS_ARM_DECORATION_NONE = "none";
    /** Cross / floating / clicky D-pad arms only: mapped HID key label per arm. */
    public static final String DPAD_CROSS_ARM_DECORATION_LABELS = "labels";
    /** Cross / floating / clicky D-pad arms only: Material direction arrow per arm. */
    public static final String DPAD_CROSS_ARM_DECORATION_ICONS = "icons";

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

    public static final String MOUSE_BTN_LEFT_ID = "mouse_btn_l";
    public static final String MOUSE_BTN_MIDDLE_ID = "mouse_btn_m";
    public static final String MOUSE_BTN_RIGHT_ID = "mouse_btn_r";
    /** Non-canonical {@code MOUSE_BUTTON} duplicate ids: {@code mouse_btn_copy_1}, {@code mouse_btn_copy_2}, … */
    public static final String MOUSE_BTN_COPY_ID_PREFIX = "mouse_btn_copy_";

    public static final String SHOULDER_L_ID = "shoulder_l";
    public static final String SHOULDER_R_ID = "shoulder_r";
    public static final String TRIGGER_L_ID = "trigger_l";
    public static final String TRIGGER_R_ID = "trigger_r";

    /** Canonical L/M/R plus user-duplicated {@link #MOUSE_BTN_COPY_ID_PREFIX} modules. */
    public static final int MAX_MOUSE_BUTTON_MODULES = 24;
    public static final int MAX_SHOULDER_MODULES = 2;
    public static final int MAX_TRIGGER_MODULES = 2;

    public static final String DEFAULT_PRESET_ID = "preset_default";

    /**
     * Shipped JSON lives under assets/bundled_gamepad/ (recursively); imported preset ids are
     * {@code preset_pack_<slug>} derived from the path under that dir (see
     * {@link GamepadLayoutPresetRepository#syncBundledPresetsFromAssets()}).
     * To ship a preset again after the user removed it, use a new filename or path so the slug changes.
     */
    public static final String BUNDLED_GAMEPAD_ASSET_DIR = "bundled_gamepad";
    public static final String BUNDLED_PRESET_ID_PREFIX = "preset_pack_";

    public static boolean isBundledPackPresetId(@Nullable String presetId) {
        return presetId != null && presetId.startsWith(BUNDLED_PRESET_ID_PREFIX);
    }

    /**
     * Presets that must not be removed from the store (user may still rename for display).
     */
    public static boolean isPresetDeletionProtected(@Nullable String presetId) {
        return DEFAULT_PRESET_ID.equals(presetId);
    }

    /**
     * Stick-like module ids: {@code stick_} plus lowercase letters, digits, and underscores
     * (e.g. {@code stick_left}, {@code stick_right}).
     */
    public static boolean isStickModuleId(@Nullable String id) {
        return id != null && id.matches("stick_[a-z0-9_]+");
    }

    /**
     * Optional extra left thumb modules: {@code stick_left_2}, {@code stick_left_3}, …
     * (Primary {@code stick_left} is optional from schema v7 onward; {@code stick_left_1} is not valid.)
     */
    public static boolean isAuxLeftStickModuleId(@Nullable String id) {
        if (id == null || !id.startsWith("stick_left_")) {
            return false;
        }
        String suffix = id.substring("stick_left_".length());
        if (!suffix.matches("[0-9]+")) {
            return false;
        }
        try {
            return Integer.parseInt(suffix) >= 2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Preset touchpad modules use ids {@code touchpad_1}, {@code touchpad_2}, … */
    public static boolean isTouchpadModuleId(@Nullable String id) {
        return id != null && id.matches("touchpad_[0-9]+");
    }

    /** {@code true} for ids {@code mouse_btn_copy_}<em>n</em> with positive integer {@code n}. */
    public static boolean isMouseButtonCopyModuleId(@Nullable String id) {
        if (id == null || !id.startsWith(MOUSE_BTN_COPY_ID_PREFIX)) {
            return false;
        }
        String suffix = id.substring(MOUSE_BTN_COPY_ID_PREFIX.length());
        return !suffix.isEmpty() && suffix.matches("[0-9]+");
    }

    /**
     * Legacy id used in v4→v5 migration before schema v6; not valid in current presets.
     */
    public static final String LEGACY_STICK_KEY_EXTRA_MODULE_ID = "stick_key_extra";

    /** {@code stick_key_extra} / {@code stick_aux_*} were removed in schema v6 (see {@link GamepadLayoutPresetUpgrader}). */
    public static boolean isObsoleteRemovedThumbStickId(@Nullable String id) {
        return LEGACY_STICK_KEY_EXTRA_MODULE_ID.equals(id)
                || (id != null && id.startsWith("stick_aux_"));
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

    /**
     * BUTTON only: horizontal half-extent as a multiple of the base face radius ({@code 1} = same as legacy circle).
     * Wider values elongate into a bar or stadium along X before rotation.
     */
    public static final float BUTTON_WIDTH_RATIO_MIN = 0.25f;
    public static final float BUTTON_WIDTH_RATIO_MAX = 3.5f;
    public static final float BUTTON_WIDTH_RATIO_DEFAULT = 1f;

    /**
     * BUTTON only: vertical half-extent as a multiple of the base face radius ({@code 1} = legacy circle).
     */
    public static final float BUTTON_HEIGHT_RATIO_MIN = 0.25f;
    public static final float BUTTON_HEIGHT_RATIO_MAX = 3.5f;
    public static final float BUTTON_HEIGHT_RATIO_DEFAULT = 1f;

    /** BUTTON only: clockwise rotation in degrees (hit box uses the axis-aligned bounding box of the rotated shape). */
    public static final float BUTTON_ROTATION_DEG_ABS_MAX = 180f;

    /** @return clamped width ratio; null or non-finite → {@link #BUTTON_WIDTH_RATIO_DEFAULT}. */
    public static float clampButtonWidthRatio(@Nullable Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return BUTTON_WIDTH_RATIO_DEFAULT;
        }
        return Math.max(BUTTON_WIDTH_RATIO_MIN, Math.min(BUTTON_WIDTH_RATIO_MAX, v));
    }

    /** @return clamped height ratio; null or non-finite → {@link #BUTTON_HEIGHT_RATIO_DEFAULT}. */
    public static float clampButtonHeightRatio(@Nullable Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return BUTTON_HEIGHT_RATIO_DEFAULT;
        }
        return Math.max(BUTTON_HEIGHT_RATIO_MIN, Math.min(BUTTON_HEIGHT_RATIO_MAX, v));
    }

    /**
     * @return rotation in degrees normalized to {@code (-180, 180]}; null or non-finite → {@code 0}.
     */
    public static float clampButtonRotationDeg(@Nullable Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return 0f;
        }
        float a = (float) Math.IEEEremainder(v, 360.0);
        if (a > 180f) {
            a -= 360f;
        }
        if (a <= -180f) {
            a += 360f;
        }
        return Math.max(-BUTTON_ROTATION_DEG_ABS_MAX,
                Math.min(BUTTON_ROTATION_DEG_ABS_MAX, a));
    }

    /** Default stick positions: parallel horizontal pair (symmetrical template). */
    public static final String STICK_LAYOUT_SYMMETRICAL = "symmetrical";
    /** Asymmetric offset stick pair (offset template; see {@link com.openterface.keymod.GamepadLayout} anchors). */
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

    public static boolean isAllowedDpadCrossArmDecoration(@Nullable String v) {
        if (v == null || v.trim().isEmpty()) {
            return true;
        }
        String x = v.trim().toLowerCase(java.util.Locale.ROOT);
        return DPAD_CROSS_ARM_DECORATION_NONE.equals(x)
                || DPAD_CROSS_ARM_DECORATION_LABELS.equals(x)
                || DPAD_CROSS_ARM_DECORATION_ICONS.equals(x);
    }

    /**
     * Canonical cross-arm decoration string for JSON ({@link #DPAD_CROSS_ARM_DECORATION_LABELS} if unknown).
     */
    @NonNull
    public static String normalizeDpadCrossArmDecoration(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DPAD_CROSS_ARM_DECORATION_LABELS;
        }
        String x = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (DPAD_CROSS_ARM_DECORATION_NONE.equals(x)) {
            return DPAD_CROSS_ARM_DECORATION_NONE;
        }
        if (DPAD_CROSS_ARM_DECORATION_ICONS.equals(x)) {
            return DPAD_CROSS_ARM_DECORATION_ICONS;
        }
        return DPAD_CROSS_ARM_DECORATION_LABELS;
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

    public static boolean isAllowedBackgroundEmbedMediaType(@Nullable String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String x = mediaType.trim().toLowerCase(java.util.Locale.ROOT);
        return BACKGROUND_MEDIA_TYPE_PNG.equals(x)
                || BACKGROUND_MEDIA_TYPE_JPEG.equals(x)
                || BACKGROUND_MEDIA_TYPE_WEBP.equals(x);
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
