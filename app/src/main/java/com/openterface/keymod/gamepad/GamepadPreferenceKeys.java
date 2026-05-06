package com.openterface.keymod.gamepad;

/**
 * Shared preference keys for Gamepad mode (fragment + preset import/export).
 */
public final class GamepadPreferenceKeys {

    private GamepadPreferenceKeys() {}

    public static final String STICK_MODE = "gamepad_stick_mode";
    public static final String STICK_UP = "gamepad_stick_up";
    public static final String STICK_LEFT = "gamepad_stick_left";
    public static final String STICK_DOWN = "gamepad_stick_down";
    public static final String STICK_RIGHT = "gamepad_stick_right";
    public static final String BUTTON_A_KEY = "gamepad_button_a_key";
    public static final String BUTTON_B_KEY = "gamepad_button_b_key";
    public static final String BUTTON_A_MOD = "gamepad_button_a_mod";
    public static final String BUTTON_B_MOD = "gamepad_button_b_mod";
    public static final String BUTTON_SIZE = "gamepad_button_size";
    public static final String STICK_SIZE = "gamepad_stick_size";
    public static final String BG_IMAGE = "gamepad_bg_image";
    public static final String BG_SCALE = "gamepad_bg_scale";
    public static final String BG_OFFSET_X = "gamepad_bg_offset_x";
    public static final String BG_OFFSET_Y = "gamepad_bg_offset_y";
    /**
     * Legacy mirror of {@code layout.showTwoButtons}; still written when applying a preset for
     * compatibility. Active preset + layout document are authoritative; no dedicated UI toggle.
     */
    public static final String TWO_BUTTON_MODE = "gamepad_two_button_mode";

    /** Full {@link GamepadLayoutPresetDocument} JSON (schema v2); authoritative for modules. */
    public static final String LAYOUT_DOCUMENT_JSON = "gamepad_layout_document_json";

    /** Default {@link androidx.preference.PreferenceManager} key for mouse speed (shared with app). */
    public static final String MOUSE_SENSITIVITY = "mouse_sensitivity";

    /** Optional multiplier for right-stick mouse (preset layout). */
    public static final String RIGHT_STICK_MOUSE_GAIN = "gamepad_right_stick_mouse_gain";

    /**
     * Customize mode: long-press duration before module / empty-area menu (ms). Applied via
     * {@code GamepadView#setEditLongPressConfig} (clamped 250–1200). Default 600.
     */
    public static final String EDIT_LONG_PRESS_MS = "gamepad_edit_long_press_ms";

    /**
     * Customize mode: finger movement in dp that cancels a pending long-press. Default 10dp
     * (converted to px when applied). Lower = stricter (easier to trigger menu while dragging).
     */
    public static final String EDIT_LONG_PRESS_CANCEL_DP = "gamepad_edit_long_press_cancel_dp";
}
