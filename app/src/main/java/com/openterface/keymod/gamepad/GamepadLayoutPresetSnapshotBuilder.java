package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a {@link GamepadLayoutPresetDocument} from current SharedPreferences + {@link GamepadConfigManager}.
 */
public final class GamepadLayoutPresetSnapshotBuilder {

    private static final String STICK_MODE_KEY = "key";

    private GamepadLayoutPresetSnapshotBuilder() {}

    public static GamepadLayoutPresetDocument buildFrom(Context context, String presetId, String displayName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        GamepadConfigManager cm = new GamepadConfigManager(context);
        Map<String, GamepadConfigManager.ComponentPosition> pos =
                cm.loadLayoutPositions(GamepadLayout.SIMPLE);

        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = presetId != null ? presetId : UUID.randomUUID().toString();
        doc.meta.displayName = displayName != null ? displayName : "Preset";
        doc.meta.exportedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        doc.meta.sourceAppVersion = BuildConfig.VERSION_NAME;

        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = prefs.getFloat(GamepadPreferenceKeys.MOUSE_SENSITIVITY, 1.0f);
        doc.layout.showTwoButtons = prefs.getBoolean(GamepadPreferenceKeys.TWO_BUTTON_MODE, false);
        doc.layout.backgroundImageFile = prefs.getString(GamepadPreferenceKeys.BG_IMAGE, null);
        doc.layout.backgroundScale = prefs.getFloat(GamepadPreferenceKeys.BG_SCALE, 1.0f);
        doc.layout.backgroundOffsetX = prefs.getFloat(GamepadPreferenceKeys.BG_OFFSET_X, 0f);
        doc.layout.backgroundOffsetY = prefs.getFloat(GamepadPreferenceKeys.BG_OFFSET_Y, 0f);

        List<GamepadLayoutPresetDocument.GamepadModule> modules = new ArrayList<>();

        String stickMode = prefs.getString(GamepadPreferenceKeys.STICK_MODE, STICK_MODE_KEY);
        GamepadLayoutPresetDocument.GamepadModule stick = new GamepadLayoutPresetDocument.GamepadModule();
        stick.id = "stick_left";
        stick.type = STICK_MODE_KEY.equals(stickMode)
                ? GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY
                : GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE;
        stick.zIndex = 0;
        stick.scale = prefs.getFloat(GamepadPreferenceKeys.STICK_SIZE, 1.0f);
        putAnchor(stick, pos, "stick_left", 0.20f, 0.50f);
        stick.stickUpKey = prefs.getInt(GamepadPreferenceKeys.STICK_UP, 26);
        stick.stickLeftKey = prefs.getInt(GamepadPreferenceKeys.STICK_LEFT, 4);
        stick.stickDownKey = prefs.getInt(GamepadPreferenceKeys.STICK_DOWN, 22);
        stick.stickRightKey = prefs.getInt(GamepadPreferenceKeys.STICK_RIGHT, 7);
        modules.add(stick);

        GamepadLayoutPresetDocument.GamepadModule btnA = new GamepadLayoutPresetDocument.GamepadModule();
        btnA.id = "button_a";
        btnA.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnA.zIndex = 1;
        btnA.scale = prefs.getFloat(GamepadPreferenceKeys.BUTTON_SIZE, 1.0f);
        putAnchor(btnA, pos, "button_a", 0.85f, 0.50f);
        btnA.hidKey = prefs.getInt(GamepadPreferenceKeys.BUTTON_A_KEY, 40);
        btnA.modifierMask = prefs.getInt(GamepadPreferenceKeys.BUTTON_A_MOD, 0);
        modules.add(btnA);

        if (doc.layout.showTwoButtons) {
            GamepadLayoutPresetDocument.GamepadModule btnB = new GamepadLayoutPresetDocument.GamepadModule();
            btnB.id = "button_b";
            btnB.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
            btnB.zIndex = 2;
            btnB.scale = btnA.scale;
            putAnchor(btnB, pos, "button_b", 0.93f, 0.40f);
            btnB.hidKey = prefs.getInt(GamepadPreferenceKeys.BUTTON_B_KEY, 41);
            btnB.modifierMask = prefs.getInt(GamepadPreferenceKeys.BUTTON_B_MOD, 0);
            modules.add(btnB);
        }

        doc.modules = modules;
        return doc;
    }

    private static void putAnchor(GamepadLayoutPresetDocument.GamepadModule m,
                                  Map<String, GamepadConfigManager.ComponentPosition> pos,
                                  String componentId,
                                  float defX,
                                  float defY) {
        GamepadConfigManager.ComponentPosition p = pos.get(componentId);
        m.anchorX = p != null ? p.x : defX;
        m.anchorY = p != null ? p.y : defY;
    }
}
