package com.openterface.keymod.gamepad;

import android.content.Context;

import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;
import com.openterface.keymod.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical built-in layout documents (classic Xbox / PlayStation / Nintendo dual-stick, NES minimal).
 */
public final class GamepadBuiltInLayoutPresets {

    private GamepadBuiltInLayoutPresets() {}

    public static GamepadLayoutPresetDocument buildClassicXbox(Context context) {
        GamepadLayoutPresetDocument doc = dualStickShell(context,
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX,
                R.string.gamepad_preset_builtin_classic_xbox,
                GamepadLayoutPresetConstants.STICK_LAYOUT_OFFSET,
                GamepadLayoutPresetConstants.FACE_TEMPLATE_XBOX_ABXY);
        Map<String, GamepadConfigManager.ComponentPosition> pos =
                new GamepadConfigManager(context).loadLayoutPositions(GamepadLayout.XBOX);
        addDualStickModules(doc, pos, "lb", "rb", "lt", "rt");
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        return doc;
    }

    public static GamepadLayoutPresetDocument buildClassicPlayStation(Context context) {
        GamepadLayoutPresetDocument doc = dualStickShell(context,
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION,
                R.string.gamepad_preset_builtin_classic_playstation,
                GamepadLayoutPresetConstants.STICK_LAYOUT_SYMMETRICAL,
                GamepadLayoutPresetConstants.FACE_TEMPLATE_PLAYSTATION_SYMBOLS);
        Map<String, GamepadConfigManager.ComponentPosition> pos =
                new GamepadConfigManager(context).loadLayoutPositions(GamepadLayout.PLAYSTATION);
        addDualStickModules(doc, pos, "l1", "r1", "l2", "r2");
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        return doc;
    }

    public static GamepadLayoutPresetDocument buildClassicNintendo(Context context) {
        GamepadLayoutPresetDocument doc = dualStickShell(context,
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO,
                R.string.gamepad_preset_builtin_classic_nintendo,
                GamepadLayoutPresetConstants.STICK_LAYOUT_OFFSET,
                GamepadLayoutPresetConstants.FACE_TEMPLATE_NINTENDO_DIAMOND);
        Map<String, GamepadConfigManager.ComponentPosition> pos =
                new GamepadConfigManager(context).loadLayoutPositions(GamepadLayout.XBOX);
        addDualStickModules(doc, pos, "lb", "rb", "lt", "rt");
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        return doc;
    }

    public static GamepadLayoutPresetDocument buildClassicNes(Context context) {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES;
        doc.meta.displayName = context.getString(R.string.gamepad_preset_builtin_classic_nes);
        doc.meta.exportedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        doc.meta.sourceAppVersion = BuildConfig.VERSION_NAME;

        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = 1.0f;
        doc.layout.showTwoButtons = false;

        Map<String, GamepadConfigManager.ComponentPosition> pos =
                new GamepadConfigManager(context).loadLayoutPositions(GamepadLayout.NES);

        List<GamepadLayoutPresetDocument.GamepadModule> modules = new ArrayList<>();

        GamepadLayoutPresetDocument.GamepadModule dpad = new GamepadLayoutPresetDocument.GamepadModule();
        dpad.id = "stick_left";
        dpad.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        dpad.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        dpad.zIndex = 0;
        dpad.scale = 1.0f;
        putAnchor(dpad, pos, "dpad", 0.20f, 0.5f);
        dpad.stickUpKey = 26;
        dpad.stickLeftKey = 4;
        dpad.stickDownKey = 22;
        dpad.stickRightKey = 7;
        modules.add(dpad);

        GamepadLayoutPresetDocument.GamepadModule btnA = new GamepadLayoutPresetDocument.GamepadModule();
        btnA.id = "button_a";
        btnA.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnA.zIndex = 1;
        btnA.scale = 1.0f;
        putAnchor(btnA, pos, "button_a", 0.80f, 0.55f);
        btnA.hidKey = 40;
        btnA.modifierMask = 0;
        modules.add(btnA);

        GamepadLayoutPresetDocument.GamepadModule btnB = new GamepadLayoutPresetDocument.GamepadModule();
        btnB.id = "button_b";
        btnB.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnB.zIndex = 2;
        btnB.scale = 1.0f;
        putAnchor(btnB, pos, "button_b", 0.90f, 0.45f);
        btnB.hidKey = 41;
        btnB.modifierMask = 0;
        modules.add(btnB);

        GamepadLayoutPresetDocument.GamepadModule sel = new GamepadLayoutPresetDocument.GamepadModule();
        sel.id = "button_select";
        sel.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        sel.zIndex = 3;
        sel.scale = 0.75f;
        putAnchor(sel, pos, "select", 0.40f, 0.70f);
        sel.hidKey = 62;
        sel.modifierMask = 0;
        sel.displayLabel = "Select";
        modules.add(sel);

        GamepadLayoutPresetDocument.GamepadModule st = new GamepadLayoutPresetDocument.GamepadModule();
        st.id = "button_start";
        st.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        st.zIndex = 4;
        st.scale = 0.75f;
        putAnchor(st, pos, "start", 0.60f, 0.70f);
        st.hidKey = 63;
        st.modifierMask = 0;
        st.displayLabel = "Start";
        modules.add(st);

        doc.modules = modules;
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        return doc;
    }

    private static GamepadLayoutPresetDocument dualStickShell(
            Context context,
            String presetId,
            int displayNameRes,
            String stickLayoutTemplate,
            String faceTemplate) {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = presetId;
        doc.meta.displayName = context.getString(displayNameRes);
        doc.meta.exportedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        doc.meta.sourceAppVersion = BuildConfig.VERSION_NAME;

        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = 1.0f;
        doc.layout.showTwoButtons = false;
        doc.layout.stickLayoutTemplate = stickLayoutTemplate;
        doc.layout.faceButtonTemplate = faceTemplate;
        doc.modules = new ArrayList<>();
        return doc;
    }

    private static void addDualStickModules(
            GamepadLayoutPresetDocument doc,
            Map<String, GamepadConfigManager.ComponentPosition> pos,
            String shoulderLeftKey,
            String shoulderRightKey,
            String triggerLeftKey,
            String triggerRightKey) {
        List<GamepadLayoutPresetDocument.GamepadModule> modules = doc.modules;

        GamepadLayoutPresetDocument.GamepadModule stickLeft = new GamepadLayoutPresetDocument.GamepadModule();
        stickLeft.id = "stick_left";
        stickLeft.type = GamepadLayoutPresetConstants.MODULE_TYPE_DPAD;
        stickLeft.dpadVariant = GamepadLayoutPresetConstants.DPAD_VARIANT_CROSS;
        stickLeft.zIndex = 0;
        stickLeft.scale = 1.0f;
        putAnchor(stickLeft, pos, "stick_left", 0.20f, 0.50f);
        stickLeft.stickUpKey = 26;
        stickLeft.stickLeftKey = 4;
        stickLeft.stickDownKey = 22;
        stickLeft.stickRightKey = 7;
        modules.add(stickLeft);

        GamepadLayoutPresetDocument.GamepadModule stickRight = new GamepadLayoutPresetDocument.GamepadModule();
        stickRight.id = "stick_right";
        stickRight.type = GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        stickRight.zIndex = 1;
        stickRight.scale = 1.0f;
        putAnchor(stickRight, pos, "stick_right", 0.75f, 0.50f);
        stickRight.stickUpKey = 12;
        stickRight.stickLeftKey = 13;
        stickRight.stickDownKey = 14;
        stickRight.stickRightKey = 15;
        modules.add(stickRight);

        GamepadFaceButtonTemplates.applyTemplate(doc, doc.layout.faceButtonTemplate);

        addShoulder(modules, pos, GamepadLayoutPresetConstants.SHOULDER_L_ID, shoulderLeftKey,
                58, "L1", 6);
        addShoulder(modules, pos, GamepadLayoutPresetConstants.SHOULDER_R_ID, shoulderRightKey,
                59, "R1", 7);
        addTrigger(modules, pos, GamepadLayoutPresetConstants.TRIGGER_L_ID, triggerLeftKey,
                60, "L2", 8);
        addTrigger(modules, pos, GamepadLayoutPresetConstants.TRIGGER_R_ID, triggerRightKey,
                61, "R2", 9);
    }

    private static void addShoulder(
            List<GamepadLayoutPresetDocument.GamepadModule> modules,
            Map<String, GamepadConfigManager.ComponentPosition> pos,
            String moduleId,
            String positionKey,
            int hidKey,
            String label,
            int zIndex) {
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = moduleId;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_SHOULDER;
        m.zIndex = zIndex;
        m.scale = 1.0f;
        putAnchor(m, pos, positionKey, 0.15f, 0.12f);
        m.hidKey = hidKey;
        m.displayLabel = label;
        modules.add(m);
    }

    private static void addTrigger(
            List<GamepadLayoutPresetDocument.GamepadModule> modules,
            Map<String, GamepadConfigManager.ComponentPosition> pos,
            String moduleId,
            String positionKey,
            int hidKey,
            String label,
            int zIndex) {
        GamepadLayoutPresetDocument.GamepadModule m = new GamepadLayoutPresetDocument.GamepadModule();
        m.id = moduleId;
        m.type = GamepadLayoutPresetConstants.MODULE_TYPE_TRIGGER;
        m.zIndex = zIndex;
        m.scale = 1.0f;
        putAnchor(m, pos, positionKey, 0.15f, 0.06f);
        m.hidKey = hidKey;
        m.triggerVariant = GamepadLayoutPresetConstants.TRIGGER_VARIANT_DIGITAL;
        m.displayLabel = label;
        modules.add(m);
    }

    private static void putAnchor(
            GamepadLayoutPresetDocument.GamepadModule m,
            Map<String, GamepadConfigManager.ComponentPosition> pos,
            String componentId,
            float defX,
            float defY) {
        GamepadConfigManager.ComponentPosition p = pos != null ? pos.get(componentId) : null;
        m.anchorX = p != null ? p.x : defX;
        m.anchorY = p != null ? p.y : defY;
    }
}
