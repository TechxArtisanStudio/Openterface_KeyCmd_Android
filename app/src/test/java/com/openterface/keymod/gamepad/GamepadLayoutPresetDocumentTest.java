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
}
