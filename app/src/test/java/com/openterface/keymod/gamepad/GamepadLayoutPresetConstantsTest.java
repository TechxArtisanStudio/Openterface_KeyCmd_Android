package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamepadLayoutPresetConstantsTest {

    @Test
    public void isPresetDeletionProtected_defaultOnly() {
        assertTrue(GamepadLayoutPresetConstants.isPresetDeletionProtected(
                GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_custom_unit"));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_two_buttons"));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_classic_xbox"));
    }
}
