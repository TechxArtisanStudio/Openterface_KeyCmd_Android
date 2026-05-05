package com.openterface.keymod.gamepad;

import java.util.Iterator;

/**
 * Mutations for {@link GamepadLayoutPresetDocument} (add/remove optional modules).
 */
public final class GamepadLayoutDocEditor {

    /** Default touchpad footprint: square (normalized to view width/height). */
    public static final float TOUCHPAD_DEFAULT_SIZE_NORM = 0.28f;

    private GamepadLayoutDocEditor() {}

    public static boolean hasStickRight(GamepadLayoutPresetDocument doc) {
        return find(doc, "stick_right") != null;
    }

    public static boolean hasTouchpad(GamepadLayoutPresetDocument doc) {
        return find(doc, "touchpad_1") != null;
    }

    public static boolean hasStickKeyExtra(GamepadLayoutPresetDocument doc) {
        return find(doc, GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID) != null;
    }

    public static int countSticks(GamepadLayoutPresetDocument doc) {
        int c = 0;
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                    || GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type))) {
                c++;
            }
        }
        return c;
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

    /**
     * Adds a third {@link GamepadLayoutPresetConstants#MODULE_TYPE_STICK_KEY} module (arrow keys by default),
     * only if fewer than {@link GamepadLayoutPresetConstants#MAX_STICK_MODULES} sticks exist and the slot is free.
     */
    public static void addStickKeyExtra(GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return;
        }
        if (countSticks(doc) >= GamepadLayoutPresetConstants.MAX_STICK_MODULES) {
            return;
        }
        if (hasStickKeyExtra(doc)) {
            return;
        }
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = GamepadLayoutPresetConstants.STICK_KEY_EXTRA_ID;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY;
        m.zIndex = nextZ(doc);
        m.scale = 1.0f;
        m.anchorX = 0.5f;
        m.anchorY = 0.72f;
        // HID arrow keys (same usages as d-pad arrows in GamepadView)
        m.stickUpKey = 82;
        m.stickLeftKey = 80;
        m.stickDownKey = 81;
        m.stickRightKey = 79;
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
        GamepadLayoutPresetDocument.GamepadModule refA = find(doc, "button_a");
        m.scale = refA != null ? refA.scale : 1.0f;
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
        m.widthNorm = TOUCHPAD_DEFAULT_SIZE_NORM;
        m.heightNorm = TOUCHPAD_DEFAULT_SIZE_NORM;
        doc.modules.add(m);
        appendBundledMouseButtonsIfNeeded(doc);
    }

    private static void appendBundledMouseButtonsIfNeeded(GamepadLayoutPresetDocument doc) {
        if (find(doc, GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID) != null) {
            return;
        }
        float y = 0.52f;
        float moduleScale = 1.0f;
        addMouseBtn(doc, GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID, 1, 0.38f, y, moduleScale);
        addMouseBtn(doc, GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID, 2, 0.5f, y, moduleScale);
        addMouseBtn(doc, GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID, 3, 0.62f, y, moduleScale);
    }

    private static void addMouseBtn(GamepadLayoutPresetDocument doc, String id, int btn,
                                      float anchorX, float anchorY, float moduleScale) {
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = id;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON;
        m.zIndex = nextZ(doc);
        m.scale = moduleScale;
        m.anchorX = anchorX;
        m.anchorY = anchorY;
        m.mouseButton = btn;
        m.displayLabel = btn == 1 ? "L" : (btn == 2 ? "M" : "R");
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
        boolean removedTouchpad = "touchpad_1".equals(componentId);
        Iterator<GamepadLayoutPresetDocument.GamepadModule> it = doc.modules.iterator();
        while (it.hasNext()) {
            if (componentId.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
        if (removedTouchpad) {
            removeBundledMouseButtons(doc);
        }
        if ("button_b".equals(componentId)) {
            doc.layout.showTwoButtons = false;
        }
    }

    private static void removeBundledMouseButtons(GamepadLayoutPresetDocument doc) {
        removeIfPresent(doc, GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID);
        removeIfPresent(doc, GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID);
        removeIfPresent(doc, GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID);
    }

    private static void removeIfPresent(GamepadLayoutPresetDocument doc, String id) {
        Iterator<GamepadLayoutPresetDocument.GamepadModule> it = doc.modules.iterator();
        while (it.hasNext()) {
            if (id.equals(it.next().id)) {
                it.remove();
                return;
            }
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
