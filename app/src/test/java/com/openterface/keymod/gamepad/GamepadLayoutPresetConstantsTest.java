package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamepadLayoutPresetConstantsTest {

    @Test
    public void contentMinEdgeScaleFactor_referenceIsUnity() {
        assertEquals(1f, GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(800, 1200), 0.0001f);
        assertEquals(1f, GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(2000, 800), 0.0001f);
    }

    @Test
    public void contentMinEdgeScaleFactor_scalesWithMinEdge() {
        assertEquals(0.5f, GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(400, 900), 0.0001f);
        assertEquals(1.25f, GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(1000, 1600), 0.0001f);
    }

    @Test
    public void contentMinEdgeScaleFactor_clamped() {
        assertEquals(GamepadLayoutPresetConstants.DYNAMIC_LAYOUT_SCALE_MIN,
                GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(1, 200), 0.0001f);
        assertEquals(GamepadLayoutPresetConstants.DYNAMIC_LAYOUT_SCALE_MAX,
                GamepadLayoutPresetConstants.contentMinEdgeScaleFactor(5000, 3000), 0.0001f);
    }

    @Test
    public void isPresetDeletionProtected_defaultOnly() {
        assertTrue(GamepadLayoutPresetConstants.isPresetDeletionProtected(
                GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_custom_unit"));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_two_buttons"));
        assertFalse(GamepadLayoutPresetConstants.isPresetDeletionProtected("preset_classic_xbox"));
    }
}
