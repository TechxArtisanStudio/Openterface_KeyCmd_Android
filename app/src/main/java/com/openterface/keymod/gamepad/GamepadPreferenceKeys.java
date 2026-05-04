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
    public static final String TWO_BUTTON_MODE = "gamepad_two_button_mode";

    /** Default {@link androidx.preference.PreferenceManager} key for mouse speed (shared with app). */
    public static final String MOUSE_SENSITIVITY = "mouse_sensitivity";
}
