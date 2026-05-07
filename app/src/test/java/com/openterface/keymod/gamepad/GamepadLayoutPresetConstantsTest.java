package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamepadLayoutPresetConstantsTest {

    @Test
    public void isPresetDeletionProtected_builtInsOnly() {
        assertTrue(GamepadLayoutPresetConstants.isPresetDeletionProtected(
                GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
        assertTrue(GamepadLayoutPresetConstants.isPresetDeletionProtected(
                GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_custom_unit"));
        assertTrue(GamepadLayoutPresetConstants.isPresetDeletionProtected(
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX));
    }

    @Test
    public void isClassicBuiltInPresetId_fourClassicsOnly() {
        assertTrue(GamepadLayoutPresetConstants.isClassicBuiltInPresetId(
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES));
        assertFalse(GamepadLayoutPresetConstants.isClassicBuiltInPresetId(
                GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
        assertFalse(GamepadLayoutPresetConstants.isClassicBuiltInPresetId(null));
    }
}
