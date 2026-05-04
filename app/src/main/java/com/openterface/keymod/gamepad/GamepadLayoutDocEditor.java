package com.openterface.keymod.gamepad;

import java.util.Iterator;

/**
 * Mutations for {@link GamepadLayoutPresetDocument} (add/remove optional modules).
 */
public final class GamepadLayoutDocEditor {

    private GamepadLayoutDocEditor() {}

    public static boolean hasStickRight(GamepadLayoutPresetDocument doc) {
        return find(doc, "stick_right") != null;
    }

    public static boolean hasTouchpad(GamepadLayoutPresetDocument doc) {
        return find(doc, "touchpad_1") != null;
    }

    public static int countButtons(GamepadLayoutPresetDocument doc) {
        int c = 0;
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(m.type)) {
                c++;
            }
        }
        return c;
    }

    public static void addStickRight(GamepadLayoutPresetDocument doc) {
        if (hasStickRight(doc)) {
            return;
        }
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = "stick_right";
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        m.zIndex = nextZ(doc);
        m.scale = 1.0f;
        m.anchorX = 0.75f;
        m.anchorY = 0.5f;
        m.stickUpKey = 12; // I
        m.stickLeftKey = 13; // J
        m.stickDownKey = 14; // K
        m.stickRightKey = 15; // L
        doc.modules.add(m);
    }

    public static void addButton(GamepadLayoutPresetDocument doc) {
        if (countButtons(doc) >= GamepadLayoutPresetConstants.MAX_BUTTON_MODULES) {
            return;
        }
        String id = GamepadLayoutDocumentStore.nextButtonModuleId(doc);
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = id;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        m.zIndex = nextZ(doc);
        m.scale = find(doc, "button_a") != null ? find(doc, "button_a").scale : 1.0f;
        m.anchorX = 0.5f;
        m.anchorY = 0.55f;
        m.hidKey = 40;
        m.modifierMask = 0;
        m.displayLabel = "Btn";
        doc.modules.add(m);
    }

    public static void addTouchpad(GamepadLayoutPresetDocument doc) {
        if (hasTouchpad(doc)) {
            return;
        }
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = "touchpad_1";
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD;
        m.zIndex = nextZ(doc);
        m.scale = 1.0f;
        m.anchorX = 0.5f;
        m.anchorY = 0.35f;
        m.widthNorm = 0.38f;
        m.heightNorm = 0.22f;
        doc.modules.add(m);
    }

    /**
     * Ensures {@code button_b} exists when {@code layout.showTwoButtons} is true.
     */
    public static void ensureButtonB(GamepadLayoutPresetDocument doc) {
        if (find(doc, "button_b") != null) {
            return;
        }
        GamepadLayoutPresetDocument.GamepadModule btnA = find(doc, "button_a");
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = "button_b";
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        m.zIndex = nextZ(doc);
        m.scale = btnA != null ? btnA.scale : 1.0f;
        m.anchorX = btnA != null ? Math.min(0.97f, btnA.anchorX + 0.08f) : 0.93f;
        m.anchorY = btnA != null ? btnA.anchorY - 0.1f : 0.40f;
        m.hidKey = 41;
        m.modifierMask = 0;
        doc.modules.add(m);
    }

    public static boolean canRemove(String componentId) {
        return !"stick_left".equals(componentId) && !"button_a".equals(componentId);
    }

    public static void removeModule(GamepadLayoutPresetDocument doc, String componentId) {
        if (!canRemove(componentId)) {
            return;
        }
        Iterator<GamepadLayoutPresetDocument.GamepadModule> it = doc.modules.iterator();
        while (it.hasNext()) {
            if (componentId.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
        if ("button_b".equals(componentId)) {
            doc.layout.showTwoButtons = false;
        }
    }

    private static int nextZ(GamepadLayoutPresetDocument doc) {
        int z = 0;
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            z = Math.max(z, m.zIndex);
        }
        return z + 1;
    }

    private static GamepadLayoutPresetDocument.GamepadModule find(GamepadLayoutPresetDocument doc, String id) {
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }
}
