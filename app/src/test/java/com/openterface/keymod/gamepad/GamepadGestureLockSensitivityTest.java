package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GamepadGestureLockSensitivityTest {

    @Test
    public void resolveMinPress_prefersLayoutOverride() {
        GamepadLayoutPresetDocument d = new GamepadLayoutPresetDocument();
        d.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        d.layout.gestureLockMinPressMs = 150;
        assertEquals(150, GamepadGestureLockSensitivity.resolveMinPressMs(d, null));
    }

    @Test
    public void resolveRadiusScale_prefersLayoutOverride() {
        GamepadLayoutPresetDocument d = new GamepadLayoutPresetDocument();
        d.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        d.layout.gestureLockDiagonalRadiusScale = 1.5f;
        assertEquals(1.5f, GamepadGestureLockSensitivity.resolveRadiusScale(d, null), 0.001f);
    }

    @Test
    public void clampMinPressMs_bounds() {
        assertEquals(0, GamepadGestureLockSensitivity.clampMinPressMs(-1));
        assertEquals(1000, GamepadGestureLockSensitivity.clampMinPressMs(2000));
    }

    @Test
    public void clampRadiusScale_bounds() {
        assertEquals(0.5f, GamepadGestureLockSensitivity.clampRadiusScale(0.1f), 0.001f);
        assertEquals(3f, GamepadGestureLockSensitivity.clampRadiusScale(99f), 0.001f);
    }

    @Test
    public void resolveTurboPulsePeriodMs_prefersLayoutOverride() {
        GamepadLayoutPresetDocument d = new GamepadLayoutPresetDocument();
        d.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        d.layout.turboPulsePeriodMs = 120;
        assertEquals(120, GamepadGestureLockSensitivity.resolveTurboPulsePeriodMs(d, null));
    }

    @Test
    public void clampTurboPulsePeriodMs_bounds() {
        assertEquals(25, GamepadGestureLockSensitivity.clampTurboPulsePeriodMs(10));
        assertEquals(300, GamepadGestureLockSensitivity.clampTurboPulsePeriodMs(400));
    }
}
