package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GamepadLayoutPresetDocumentTest {

    @Test
    public void validateAcceptsMinimalSimpleLayout() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void roundTripJson() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        String json = GamepadLayoutPresetDocument.toJsonPretty(doc);
        assertTrue(json.contains(GamepadLayoutPresetConstants.DOCUMENT_FORMAT));
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(parsed);
        GamepadLayoutPresetDocument.validateOrThrow(parsed);
        assertEquals(doc.modules.size(), parsed.modules.size());
        assertEquals("stick_left", parsed.modules.get(0).id);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsWrongFormat() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.format = "other";
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test
    public void upgradeV1SchemaThenValidates() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION_V1;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
    }

    @Test
    public void validateAcceptsExtraStickTouchpadMouseButtonsAndGain() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouseAndExtra();
        doc.layout.rightStickMouseGain = 1.5f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsRightStickMouseGainOutOfRange() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        doc.layout.rightStickMouseGain = 10f;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsUnknownStickId() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouseAndExtra();
        GamepadLayoutPresetDocument.GamepadModule rogue = new GamepadLayoutPresetDocument.GamepadModule();
        rogue.id = "stick_other";
        rogue.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        rogue.zIndex = 99;
        rogue.scale = 1f;
        rogue.anchorX = 0.5f;
        rogue.anchorY = 0.5f;
        rogue.stickUpKey = 82;
        rogue.stickLeftKey = 80;
        rogue.stickDownKey = 81;
        rogue.stickRightKey = 79;
        doc.modules.add(rogue);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsFourthStick() {
        GamepadLayoutPresetDocument doc = layoutWithRightStickTouchpadMouseAndExtra();
        GamepadLayoutPresetDocument.GamepadModule dup = new GamepadLayoutPresetDocument.GamepadModule();
        dup.id = "stick_left";
        dup.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        dup.zIndex = 100;
        dup.scale = 1f;
        dup.anchorX = 0.1f;
        dup.anchorY = 0.1f;
        dup.stickUpKey = 26;
        dup.stickLeftKey = 4;
        dup.stickDownKey = 22;
        dup.stickRightKey = 7;
        doc.modules.add(dup);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateRejectsMouseButtonIdMismatch() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule mb = new GamepadLayoutPresetDocument.GamepadModule();
        mb.id = GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID;
        mb.type = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON;
        mb.zIndex = 5;
        mb.scale = 1f;
        mb.anchorX = 0.5f;
        mb.anchorY = 0.5f;
        mb.mouseButton = 3;
        doc.modules.add(mb);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
    }

    private static GamepadLayoutPresetDocument minimalValidDocument() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "test";
        doc.meta.displayName = "Test";
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = 1.0f;
        doc.layout.showTwoButtons = false;
        List<GamepadLayoutPresetDocument.GamepadModule> modules = new ArrayList<>();
        GamepadLayoutPresetDocument.GamepadModule stick = new GamepadLayoutPresetDocument.GamepadModule();
        stick.id = "stick_left";
        stick.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        stick.zIndex = 0;
        stick.scale = 1.0f;
        stick.anchorX = 0.2f;
        stick.anchorY = 0.5f;
        stick.stickUpKey = 26;
        stick.stickLeftKey = 4;
        stick.stickDownKey = 22;
        stick.stickRightKey = 7;
        modules.add(stick);
        GamepadLayoutPresetDocument.GamepadModule btnA = new GamepadLayoutPresetDocument.GamepadModule();
        btnA.id = "button_a";
        btnA.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnA.zIndex = 1;
        btnA.scale = 1.0f;
        btnA.anchorX = 0.85f;
        btnA.anchorY = 0.5f;
        btnA.hidKey = 40;
        btnA.modifierMask = 0;
        modules.add(btnA);
        doc.modules = modules;
        return doc;
    }

    private static GamepadLayoutPresetDocument layoutWithRightStickTouchpadMouseAndExtra() {
        GamepadLayoutPresetDocument doc = minimalValidDocument();
        GamepadLayoutPresetDocument.GamepadModule right = new GamepadLayoutPresetDocument.GamepadModule();
        right.id = "stick_right";
        right.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        right.zIndex = 2;
        right.scale = 1f;
        right.anchorX = 0.75f;
        right.anchorY = 0.5f;
        right.stickUpKey = 12;
        right.stickLeftKey = 13;
        right.stickDownKey = 14;
        right.stickRightKey = 15;
        doc.modules.add(right);

        GamepadLayoutPresetDocument.GamepadModule extra = new GamepadLayoutPresetDocument.GamepadModule();
        extra.id = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID;
        extra.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        extra.zIndex = 3;
        extra.scale = 1f;
        extra.anchorX = 0.5f;
        extra.anchorY = 0.72f;
        extra.stickUpKey = 82;
        extra.stickLeftKey = 80;
        extra.stickDownKey = 81;
        extra.stickRightKey = 79;
        doc.modules.add(extra);

        GamepadLayoutPresetDocument.GamepadModule tp = new GamepadLayoutPresetDocument.GamepadModule();
        tp.id = "touchpad_1";
        tp.type = GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD;
        tp.zIndex = 4;
        tp.scale = 1f;
        tp.anchorX = 0.5f;
        tp.anchorY = 0.35f;
        tp.widthNorm = 0.28f;
        tp.heightNorm = 0.28f;
        doc.modules.add(tp);

        doc.modules.add(mouseModule(GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID, 1, 5));
        doc.modules.add(mouseModule(GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID, 2, 6));
        doc.modules.add(mouseModule(GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID, 3, 7));
        return doc;
    }

    private static GamepadLayoutPresetDocument.GamepadModule mouseModule(String id, int btn, int z) {
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = id;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON;
        m.zIndex = z;
        m.scale = 1f;
        m.anchorX = 0.55f;
        m.anchorY = 0.2f;
        m.mouseButton = btn;
        return m;
    }
}
