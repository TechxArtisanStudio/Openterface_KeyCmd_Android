package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GamepadButtonKeyMappingTest {

    @Test
    public void normalize_setsMaskForModifierOnlyHidKey() {
        assertEquals(0x02, GamepadButtonKeyMapping.normalize(225, 0));
    }

    @Test
    public void normalize_preservesChordMaskForRegularKey() {
        assertEquals(0x03, GamepadButtonKeyMapping.normalize(40, 0x03));
    }

    @Test
    public void normalize_orCombinesWhenMaskPartial() {
        assertEquals(0x02, GamepadButtonKeyMapping.normalize(225, 0x01));
    }

    @Test
    public void modifierBit_leftAndRightCtrlAreDistinct() {
        assertEquals(0x01, GamepadButtonKeyMapping.modifierBit(224));
        assertEquals(0x10, GamepadButtonKeyMapping.modifierBit(228));
    }

    @Test
    public void isModifierKey_bounds() {
        assertFalse(GamepadButtonKeyMapping.isModifierKey(223));
        assertTrue(GamepadButtonKeyMapping.isModifierKey(224));
        assertTrue(GamepadButtonKeyMapping.isModifierKey(231));
        assertFalse(GamepadButtonKeyMapping.isModifierKey(232));
    }

    @Test
    public void modifierEntries_countEight() {
        assertEquals(8, GamepadButtonKeyMapping.MODIFIER_ENTRIES.length);
    }
}
