package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

public class GamepadLayoutDocEditorTest {

    @Test
    public void normalizeModuleZOrderAssignsContiguousZByDrawOrder() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.modules = new ArrayList<>();
        GamepadLayoutPresetDocument.GamepadModule a = new GamepadLayoutPresetDocument.GamepadModule();
        a.id = "a";
        a.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        a.zIndex = 5;
        a.anchorX = 0.1f;
        a.anchorY = 0.1f;
        a.hidKey = 40;
        a.modifierMask = 0;
        GamepadLayoutPresetDocument.GamepadModule b = new GamepadLayoutPresetDocument.GamepadModule();
        b.id = "b";
        b.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        b.zIndex = 2;
        b.anchorX = 0.2f;
        b.anchorY = 0.2f;
        b.hidKey = 41;
        b.modifierMask = 0;
        doc.modules.add(a);
        doc.modules.add(b);
        GamepadLayoutDocEditor.normalizeModuleZOrder(doc);
        assertEquals(1, a.zIndex);
        assertEquals(0, b.zIndex);
    }

    @Test
    public void bringAndSendMutateZ() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.modules = new ArrayList<>();
        addButton(doc, "x", 0);
        addButton(doc, "y", 1);
        assertTrue(GamepadLayoutDocEditor.bringModuleToFront(doc, "x"));
        assertTrue(GamepadLayoutDocEditor.isStrictlyInFront(doc, "x"));
        assertTrue(GamepadLayoutDocEditor.sendModuleToBack(doc, "x"));
        assertTrue(GamepadLayoutDocEditor.isStrictlyInBack(doc, "x"));
        assertFalse(GamepadLayoutDocEditor.sendModuleToBack(doc, "x"));
    }

    private static void addButton(GamepadLayoutPresetDocument doc, String id, int z) {
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = id;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        m.zIndex = z;
        m.scale = 1f;
        m.anchorX = 0.5f;
        m.anchorY = 0.5f;
        m.hidKey = 40;
        m.modifierMask = 0;
        doc.modules.add(m);
    }
}
