package com.openterface.keymod.gamepad;

public final class GamepadLayoutPresetConstants {

    private GamepadLayoutPresetConstants() {}

    public static final String DOCUMENT_FORMAT = "openterface.gamepad.layout.v1";
    public static final int SCHEMA_VERSION_V1 = 1;
    public static final int SCHEMA_VERSION = 2;

    public static final String MODULE_TYPE_STICK_KEY = "STICK_KEY";
    /** Left stick only: connected retro cross (WASD-style); same key fields as {@link #MODULE_TYPE_STICK_KEY}. */
    public static final String MODULE_TYPE_WASD_CROSS = "WASD_CROSS";
    public static final String MODULE_TYPE_STICK_MOUSE = "STICK_MOUSE";
    public static final String MODULE_TYPE_BUTTON = "BUTTON";
    public static final String MODULE_TYPE_TOUCHPAD = "TOUCHPAD";
    /** Physical mouse buttons (HID mouse report), not keyboard keys. */
    public static final String MODULE_TYPE_MOUSE_BUTTON = "MOUSE_BUTTON";

    /** Optional third stick: same STICK_KEY behavior as left stick, distinct module id. */
    public static final String STICK_KEY_EXTRA_ID = "stick_key_extra";

    public static final String MOUSE_BTN_LEFT_ID = "mouse_btn_l";
    public static final String MOUSE_BTN_MIDDLE_ID = "mouse_btn_m";
    public static final String MOUSE_BTN_RIGHT_ID = "mouse_btn_r";

    public static final int MAX_STICK_MODULES = 3;
    public static final int MAX_MOUSE_BUTTON_MODULES = 3;

    public static final String DEFAULT_PRESET_ID = "preset_default";

    /**
     * Built-in sibling of {@link #DEFAULT_PRESET_ID}: same layout intent but {@code layout.showTwoButtons}
     * true and a {@code button_b} module. Users switch 1-button vs 2-button by changing active preset
     * (short tap / preset list), not a separate toggle.
     */
    public static final String BUILT_IN_TWO_BUTTON_PRESET_ID = "preset_two_buttons";

    public static final int MAX_BUTTON_MODULES = 20;
}
