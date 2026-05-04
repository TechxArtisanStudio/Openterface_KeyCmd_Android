package com.openterface.keymod.gamepad;

public final class GamepadLayoutPresetConstants {

    private GamepadLayoutPresetConstants() {}

    public static final String DOCUMENT_FORMAT = "openterface.gamepad.layout.v1";
    public static final int SCHEMA_VERSION_V1 = 1;
    public static final int SCHEMA_VERSION = 2;

    public static final String MODULE_TYPE_STICK_KEY = "STICK_KEY";
    public static final String MODULE_TYPE_STICK_MOUSE = "STICK_MOUSE";
    public static final String MODULE_TYPE_BUTTON = "BUTTON";
    public static final String MODULE_TYPE_TOUCHPAD = "TOUCHPAD";

    public static final String DEFAULT_PRESET_ID = "preset_default";

    /**
     * Built-in sibling of {@link #DEFAULT_PRESET_ID}: same layout intent but {@code layout.showTwoButtons}
     * true and a {@code button_b} module. Users switch 1-button vs 2-button by changing active preset
     * (short tap / preset list), not a separate toggle.
     */
    public static final String BUILT_IN_TWO_BUTTON_PRESET_ID = "preset_two_buttons";

    public static final int MAX_BUTTON_MODULES = 20;
}
