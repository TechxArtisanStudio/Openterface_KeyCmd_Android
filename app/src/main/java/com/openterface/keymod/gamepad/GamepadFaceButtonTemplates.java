package com.openterface.keymod.gamepad;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies canonical face-button cluster anchors/labels to existing {@code BUTTON} modules.
 */
public final class GamepadFaceButtonTemplates {

    private GamepadFaceButtonTemplates() {}

    /**
     * Sets {@link GamepadLayoutPresetDocument.LayoutGlobals#faceButtonTemplate} and ensures four face
     * {@code BUTTON} modules exist with diamond-style anchors (normalized 0–1).
     */
    public static void applyTemplate(
            @NonNull GamepadLayoutPresetDocument doc,
            @NonNull String templateId) throws IllegalArgumentException {
        if (!GamepadLayoutPresetConstants.isAllowedFaceButtonTemplate(templateId)) {
            throw new IllegalArgumentException("Unknown faceButtonTemplate: " + templateId);
        }
        if (doc.layout == null) {
            doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        }
        doc.layout.faceButtonTemplate = templateId.trim();
        if (doc.modules == null) {
            doc.modules = new ArrayList<>();
        }
        String t = doc.layout.faceButtonTemplate;
        if (GamepadLayoutPresetConstants.FACE_TEMPLATE_NINTENDO_DIAMOND.equals(t)) {
            upsertFaceButton(doc.modules, "button_y", 0.82f, 0.28f, 5, "Y", 3);
            upsertFaceButton(doc.modules, "button_b", 0.90f, 0.42f, 41, "B", 4);
            upsertFaceButton(doc.modules, "button_a", 0.86f, 0.56f, 40, "A", 5);
            upsertFaceButton(doc.modules, "button_x", 0.74f, 0.40f, 27, "X", 2);
        } else if (GamepadLayoutPresetConstants.FACE_TEMPLATE_XBOX_ABXY.equals(t)) {
            upsertFaceButton(doc.modules, "button_y", 0.82f, 0.30f, 28, "Y", 3);
            upsertFaceButton(doc.modules, "button_x", 0.74f, 0.44f, 27, "X", 2);
            upsertFaceButton(doc.modules, "button_b", 0.90f, 0.44f, 5, "B", 4);
            upsertFaceButton(doc.modules, "button_a", 0.84f, 0.58f, 40, "A", 5);
        } else if (GamepadLayoutPresetConstants.FACE_TEMPLATE_PLAYSTATION_SYMBOLS.equals(t)) {
            upsertFaceButton(doc.modules, "button_y", 0.80f, 0.26f, 101, "\u25B3", 3);
            upsertFaceButton(doc.modules, "button_b", 0.92f, 0.40f, 41, "\u25CB", 4);
            upsertFaceButton(doc.modules, "button_a", 0.86f, 0.58f, 40, "\u00D7", 5);
            upsertFaceButton(doc.modules, "button_x", 0.72f, 0.40f, 42, "\u25A1", 2);
        }
    }

    private static void upsertFaceButton(
            @NonNull List<GamepadLayoutPresetDocument.GamepadModule> modules,
            @NonNull String id,
            float anchorX,
            float anchorY,
            int hidKey,
            @Nullable String displayLabel,
            int zIndex) {
        GamepadLayoutPresetDocument.GamepadModule found = null;
        for (GamepadLayoutPresetDocument.GamepadModule m : modules) {
            if (m != null && id.equals(m.id)) {
                found = m;
                break;
            }
        }
        if (found == null) {
            found = new GamepadLayoutPresetDocument.GamepadModule();
            found.id = id;
            found.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
            modules.add(found);
        }
        found.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        found.anchorX = anchorX;
        found.anchorY = anchorY;
        found.hidKey = hidKey;
        found.modifierMask = 0;
        found.displayLabel = displayLabel;
        found.zIndex = zIndex;
        if (found.scale <= 0f) {
            found.scale = 0.85f;
        }
    }
}
