package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GamepadBuiltInLayoutPresetsTest {

    private static GamepadLayoutPresetDocument.GamepadModule find(
            GamepadLayoutPresetDocument doc, String id) {
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }

    @Test
    public void buildClassicXbox_validatesAndHasExpectedHints() {
        Context ctx = RuntimeEnvironment.getApplication();
        GamepadLayoutPresetDocument doc = GamepadBuiltInLayoutPresets.buildClassicXbox(ctx);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.STICK_LAYOUT_OFFSET, doc.layout.stickLayoutTemplate);
        assertEquals(GamepadLayoutPresetConstants.FACE_TEMPLATE_XBOX_ABXY, doc.layout.faceButtonTemplate);
        assertNotNull(find(doc, "stick_right"));
        assertNotNull(find(doc, "shoulder_l"));
    }

    @Test
    public void buildClassicPlayStation_symmetricalTemplate() {
        Context ctx = RuntimeEnvironment.getApplication();
        GamepadLayoutPresetDocument doc = GamepadBuiltInLayoutPresets.buildClassicPlayStation(ctx);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.STICK_LAYOUT_SYMMETRICAL, doc.layout.stickLayoutTemplate);
        assertEquals(GamepadLayoutPresetConstants.FACE_TEMPLATE_PLAYSTATION_SYMBOLS, doc.layout.faceButtonTemplate);
    }

    @Test
    public void buildClassicNintendo_offsetAndDiamond() {
        Context ctx = RuntimeEnvironment.getApplication();
        GamepadLayoutPresetDocument doc = GamepadBuiltInLayoutPresets.buildClassicNintendo(ctx);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.STICK_LAYOUT_OFFSET, doc.layout.stickLayoutTemplate);
        assertEquals(GamepadLayoutPresetConstants.FACE_TEMPLATE_NINTENDO_DIAMOND, doc.layout.faceButtonTemplate);
    }

    @Test
    public void buildClassicNes_noRightStick() {
        Context ctx = RuntimeEnvironment.getApplication();
        GamepadLayoutPresetDocument doc = GamepadBuiltInLayoutPresets.buildClassicNes(ctx);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertNull(find(doc, "stick_right"));
        assertNotNull(find(doc, "button_select"));
        assertNotNull(find(doc, "button_start"));
        assertEquals(GamepadLayoutPresetConstants.MODULE_TYPE_DPAD, find(doc, "stick_left").type);
    }
}
