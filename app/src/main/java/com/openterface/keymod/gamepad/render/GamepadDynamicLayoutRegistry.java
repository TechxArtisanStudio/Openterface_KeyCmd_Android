package com.openterface.keymod.gamepad.render;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

import com.openterface.keymod.GamepadView;
import com.openterface.keymod.gamepad.GamepadLayoutPresetConstants;
import com.openterface.keymod.gamepad.GamepadLayoutPresetDocument;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Dispatches schema-driven SIMPLE layout drawing by module {@code type} (renderer registry).
 */
public final class GamepadDynamicLayoutRegistry {

    private GamepadDynamicLayoutRegistry() {}

    public interface ModuleDrawer {
        void draw(@NonNull GamepadView view, @NonNull Canvas canvas,
                @NonNull GamepadLayoutPresetDocument.GamepadModule m, int viewWidth, int viewHeight);
    }

    private static final java.util.Map<String, ModuleDrawer> DRAWERS = new java.util.HashMap<>();

    static {
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY, GamepadView::drawDynamicStickLikeModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE, GamepadView::drawDynamicStickLikeModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_DPAD, GamepadView::drawDynamicDpadModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON, GamepadView::drawDynamicButtonModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_TOUCHPAD, GamepadView::drawDynamicTouchpadModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_MOUSE_BUTTON, GamepadView::drawDynamicMouseButtonModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER, GamepadView::drawDynamicShoulderOrTriggerModule);
        DRAWERS.put(GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER, GamepadView::drawDynamicShoulderOrTriggerModule);
    }

    public static void drawSortedModules(
            @NonNull GamepadView view,
            @NonNull Canvas canvas,
            @NonNull GamepadLayoutPresetDocument doc,
            int viewWidth,
            int viewHeight) {
        List<GamepadLayoutPresetDocument.GamepadModule> mods = new java.util.ArrayList<>(doc.modules);
        Collections.sort(mods, Comparator.comparingInt(m -> m.zIndex));
        for (GamepadLayoutPresetDocument.GamepadModule m : mods) {
            if (m == null) {
                continue;
            }
            ModuleDrawer d = DRAWERS.get(m.type);
            if (d != null) {
                d.draw(view, canvas, m, viewWidth, viewHeight);
            }
        }
    }
}
