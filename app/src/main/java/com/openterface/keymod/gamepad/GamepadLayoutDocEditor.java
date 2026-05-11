package com.openterface.keymod.gamepad;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.util.Iterator;
import java.util.Locale;

/**
 * Mutations for {@link GamepadLayoutPresetDocument} (add/remove optional modules).
 */
public final class GamepadLayoutDocEditor {

    /** Default touchpad footprint: square (normalized to view width/height). */
    public static final float TOUCHPAD_DEFAULT_SIZE_NORM = 0.28f;
    /** Default scroll strip: narrow vertical bar. */
    public static final float SCROLL_STRIP_DEFAULT_WIDTH_NORM = 0.10f;
    public static final float SCROLL_STRIP_DEFAULT_HEIGHT_NORM = 0.36f;

    private static final Gson DUPLICATE_GSON = new Gson();

    private GamepadLayoutDocEditor() {}

    public static boolean hasStickRight(GamepadLayoutPresetDocument doc) {
        return find(doc, "stick_right") != null;
    }

    /** @return true if the layout has at least one {@link GamepadLayoutPresetConstants#MODULE_TYPE_TOUCHPAD} module. */
    public static boolean hasTouchpad(GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return false;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD.equals(m.type)) {
                return true;
            }
        }
        return false;
    }

    private static int countTouchpadModules(GamepadLayoutPresetDocument doc) {
        int n = 0;
        if (doc == null || doc.modules == null) {
            return 0;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD.equals(m.type)) {
                n++;
            }
        }
        return n;
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
     * Sets {@code stick_left} to {@link GamepadLayoutPresetConstants#MODULE_TYPE_DPAD} with
     * {@link GamepadLayoutPresetConstants#DPAD_VARIANT_CROSS}. Fills missing direction keys with default WASD HID usages.
     */
    public static void setLeftStickDpadCross(GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return;
        }
        GamepadLayoutPresetDocument.GamepadModule left = find(doc, "stick_left");
        if (left == null) {
            return;
        }
        left.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        left.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        if (left.stickUpKey == null) {
            left.stickUpKey = 26;
        }
        if (left.stickLeftKey == null) {
            left.stickLeftKey = 4;
        }
        if (left.stickDownKey == null) {
            left.stickDownKey = 22;
        }
        if (left.stickRightKey == null) {
            left.stickRightKey = 7;
        }
    }

    private static int countAuxLeftStickModules(GamepadLayoutPresetDocument doc) {
        int n = 0;
        if (doc == null || doc.modules == null) {
            return 0;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Adds a left D-pad / stick module: creates {@code stick_left} when absent, otherwise appends
     * {@code stick_left_2}, {@code stick_left_3}, … (DPAD cross + default WASD).
     */
    public static void addLeftDpadStickModule(GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return;
        }
        if (find(doc, "stick_left") == null) {
            GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
            m.id = "stick_left";
            m.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
            m.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
            m.zIndex = nextZ(doc);
            m.scale = 1.0f;
            m.anchorX = 0.20f;
            m.anchorY = 0.50f;
            m.stickUpKey = 26;
            m.stickLeftKey = 4;
            m.stickDownKey = 22;
            m.stickRightKey = 7;
            doc.modules.add(m);
            return;
        }
        int idx = countAuxLeftStickModules(doc);
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = GamepadLayoutDocumentStore.nextAuxLeftStickModuleId(doc);
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        m.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        m.zIndex = nextZ(doc);
        m.scale = 1.0f;
        float baseX = 0.20f;
        float baseY = 0.50f;
        float ax = baseX - 0.14f * (idx % 4);
        float ay = baseY + 0.08f * ((idx / 4) % 3);
        m.anchorX = Math.max(0.08f, Math.min(0.42f, ax));
        m.anchorY = Math.max(0.18f, Math.min(0.78f, ay));
        m.stickUpKey = 26;
        m.stickLeftKey = 4;
        m.stickDownKey = 22;
        m.stickRightKey = 7;
        doc.modules.add(m);
    }

    public static void addButton(GamepadLayoutPresetDocument doc) {
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
        if (doc == null || doc.modules == null) {
            return;
        }
        int idx = countTouchpadModules(doc);
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = GamepadLayoutDocumentStore.nextTouchpadModuleId(doc);
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD;
        m.zIndex = nextZ(doc);
        m.scale = 1.0f;
        float ax = 0.5f + 0.06f * (idx % 4) - 0.09f;
        float ay = 0.35f + 0.07f * (idx / 4);
        m.anchorX = Math.max(0.1f, Math.min(0.9f, ax));
        m.anchorY = Math.max(0.12f, Math.min(0.82f, ay));
        m.widthNorm = TOUCHPAD_DEFAULT_SIZE_NORM;
        m.heightNorm = TOUCHPAD_DEFAULT_SIZE_NORM;
        doc.modules.add(m);
        appendBundledMouseButtonsIfNeeded(doc);
    }

    public static int countScrollStripModules(@Nullable GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return 0;
        }
        int n = 0;
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && GamepadLayoutPresetConstants.MODULE_TYPE_SCROLL_STRIP.equals(m.type)) {
                n++;
            }
        }
        return n;
    }

    /** Adds a {@link GamepadLayoutPresetConstants#MODULE_TYPE_SCROLL_STRIP} module if under the per-layout cap. */
    public static void addScrollStrip(GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return;
        }
        if (countScrollStripModules(doc) >= GamepadLayoutPresetConstants.MAX_SCROLL_STRIP_MODULES) {
            return;
        }
        int idx = countScrollStripModules(doc);
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = GamepadLayoutDocumentStore.nextScrollStripModuleId(doc);
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_SCROLL_STRIP;
        m.zIndex = nextZ(doc);
        m.scale = 1.0f;
        float ax = 0.06f + 0.05f * (idx % 5);
        float ay = 0.48f + 0.04f * (idx / 5);
        m.anchorX = Math.max(0.06f, Math.min(0.94f, ax));
        m.anchorY = Math.max(0.2f, Math.min(0.8f, ay));
        m.widthNorm = SCROLL_STRIP_DEFAULT_WIDTH_NORM;
        m.heightNorm = SCROLL_STRIP_DEFAULT_HEIGHT_NORM;
        m.displayLabel = "Wheel";
        doc.modules.add(m);
    }

    /**
     * When a touchpad is first added, bundle left + right only (no middle by default).
     * Fills in either side if missing so re-imported layouts can self-heal missing halves.
     */
    private static void appendBundledMouseButtonsIfNeeded(GamepadLayoutPresetDocument doc) {
        float y = 0.52f;
        float moduleScale = 1.0f;
        if (find(doc, GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID) == null) {
            addMouseBtn(doc, GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID, 1, 0.4f, y, moduleScale);
        }
        if (find(doc, GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID) == null) {
            addMouseBtn(doc, GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID, 3, 0.6f, y, moduleScale);
        }
    }

    public static int countMouseButtonModules(@Nullable GamepadLayoutPresetDocument doc) {
        if (doc == null || doc.modules == null) {
            return 0;
        }
        int n = 0;
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(m.type)) {
                n++;
            }
        }
        return n;
    }

    private static boolean addTouchpadBundledMouseButton(GamepadLayoutPresetDocument doc, String id,
                                                         int semanticButton, float anchorX, float anchorY) {
        if (doc == null || doc.modules == null || !hasTouchpad(doc)) {
            return false;
        }
        if (find(doc, id) != null) {
            return false;
        }
        if (countMouseButtonModules(doc) >= GamepadLayoutPresetConstants.MAX_MOUSE_BUTTON_MODULES) {
            return false;
        }
        addMouseBtn(doc, id, semanticButton, anchorX, anchorY, 1.0f);
        return true;
    }

    /** Re-add bundled left mouse button below touchpad (no-op if already present or no touchpad). */
    public static boolean addTouchpadMouseButtonLeft(GamepadLayoutPresetDocument doc) {
        return addTouchpadBundledMouseButton(doc, GamepadLayoutPresetConstants.MOUSE_BTN_LEFT_ID, 1, 0.4f, 0.52f);
    }

    /** Re-add middle mouse button (optional; not part of default touchpad bundle). */
    public static boolean addTouchpadMouseButtonMiddle(GamepadLayoutPresetDocument doc) {
        return addTouchpadBundledMouseButton(doc, GamepadLayoutPresetConstants.MOUSE_BTN_MIDDLE_ID, 2, 0.5f, 0.52f);
    }

    /** Re-add bundled right mouse button below touchpad. */
    public static boolean addTouchpadMouseButtonRight(GamepadLayoutPresetDocument doc) {
        return addTouchpadBundledMouseButton(doc, GamepadLayoutPresetConstants.MOUSE_BTN_RIGHT_ID, 3, 0.6f, 0.52f);
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

    /**
     * Applies a face-button cluster template (Nintendo / Xbox / PlayStation anchors and labels).
     */
    public static void applyFaceButtonTemplate(GamepadLayoutPresetDocument doc, String templateId) {
        GamepadFaceButtonTemplates.applyTemplate(doc, templateId);
    }

    public static boolean canRemove(String componentId) {
        return componentId != null && !componentId.isEmpty();
    }

    /**
     * Whether the module can be deep-copied with a new id ({@code stick_right} and fixed shoulder/trigger ids are
     * excluded; {@code MOUSE_BUTTON} copies use {@link GamepadLayoutPresetConstants#MOUSE_BTN_COPY_ID_PREFIX} ids).
     * Extra left DPAD modules must stay cross-only; duplicating a non–cross-DPAD left thumb would produce an invalid doc.
     */
    public static boolean canDuplicateModule(@Nullable String moduleId, @Nullable GamepadLayoutPresetDocument doc) {
        if (moduleId == null || doc == null || doc.modules == null) {
            return false;
        }
        GamepadLayoutPresetDocument.GamepadModule src = find(doc, moduleId);
        if (src == null || src.type == null) {
            return false;
        }
        if ("stick_right".equals(moduleId)) {
            return false;
        }
        if (GamepadLayoutPresetConstants.SHOULDER_L_ID.equals(moduleId)
                || GamepadLayoutPresetConstants.SHOULDER_R_ID.equals(moduleId)
                || GamepadLayoutPresetConstants.TRIGGER_L_ID.equals(moduleId)
                || GamepadLayoutPresetConstants.TRIGGER_R_ID.equals(moduleId)) {
            return false;
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(src.type)) {
            String dv = src.dpadVariant != null ? src.dpadVariant.trim().toLowerCase(Locale.ROOT) : "";
            if (!GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS.equals(dv)) {
                return false;
            }
        }
        if (GamepadLayoutPresetConstants.isStickModuleId(moduleId)) {
            return "stick_left".equals(moduleId)
                    || GamepadLayoutPresetConstants.isAuxLeftStickModuleId(moduleId);
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(src.type)) {
            return true;
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD.equals(src.type)) {
            return true;
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_SCROLL_STRIP.equals(src.type)) {
            return countScrollStripModules(doc) < GamepadLayoutPresetConstants.MAX_SCROLL_STRIP_MODULES;
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(src.type)) {
            return countMouseButtonModules(doc) < GamepadLayoutPresetConstants.MAX_MOUSE_BUTTON_MODULES;
        }
        return false;
    }

    /**
     * Appends a copy of the module with a new id, nudged anchor, and fresh z-index. Validates the document before keeping
     * the clone; returns the new id, or {@code null} if duplication is not allowed or validation fails.
     */
    @Nullable
    public static String duplicateModule(GamepadLayoutPresetDocument doc, String sourceId) {
        if (!canDuplicateModule(sourceId, doc)) {
            return null;
        }
        GamepadLayoutPresetDocument.GamepadModule src = find(doc, sourceId);
        if (src == null) {
            return null;
        }
        GamepadLayoutPresetDocument.GamepadModule clone =
                DUPLICATE_GSON.fromJson(DUPLICATE_GSON.toJson(src), GamepadLayoutPresetDocument.GamepadModule.class);
        clone.id = allocateDuplicateModuleId(doc, clone);
        if (clone.id == null) {
            return null;
        }
        clone.zIndex = nextZ(doc);
        clone.anchorX = clamp01(src.anchorX + 0.06f);
        clone.anchorY = clamp01(src.anchorY + 0.05f);
        if (clone.anchorX > 0.94f && clone.anchorY > 0.88f) {
            clone.anchorX = clamp01(src.anchorX - 0.08f);
            clone.anchorY = clamp01(src.anchorY - 0.06f);
        }
        doc.modules.add(clone);
        try {
            GamepadLayoutPresetDocument.validateOrThrow(doc);
        } catch (IllegalArgumentException ex) {
            doc.modules.remove(clone);
            return null;
        }
        return clone.id;
    }

    @Nullable
    private static String allocateDuplicateModuleId(GamepadLayoutPresetDocument doc,
                                                    GamepadLayoutPresetDocument.GamepadModule clone) {
        if (clone.type == null) {
            return null;
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON.equals(clone.type)) {
            return GamepadLayoutDocumentStore.nextButtonModuleId(doc);
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD.equals(clone.type)) {
            return GamepadLayoutDocumentStore.nextTouchpadModuleId(doc);
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_SCROLL_STRIP.equals(clone.type)) {
            return GamepadLayoutDocumentStore.nextScrollStripModuleId(doc);
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON.equals(clone.type)) {
            return GamepadLayoutDocumentStore.nextMouseButtonCopyModuleId(doc);
        }
        if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(clone.type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(clone.type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(clone.type)) {
            return GamepadLayoutDocumentStore.nextAuxLeftStickModuleId(doc);
        }
        return null;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static void removeModule(GamepadLayoutPresetDocument doc, String componentId) {
        if (!canRemove(componentId)) {
            return;
        }
        if ("button_a".equals(componentId)) {
            doc.layout.showTwoButtons = false;
        }
        boolean removedTouchpad = GamepadLayoutPresetConstants.isTouchpadModuleId(componentId);
        Iterator<GamepadLayoutPresetDocument.GamepadModule> it = doc.modules.iterator();
        while (it.hasNext()) {
            if (componentId.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
        if (removedTouchpad && !hasTouchpad(doc)) {
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
