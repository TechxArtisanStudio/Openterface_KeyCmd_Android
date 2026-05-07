package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads/saves the active {@link GamepadLayoutPresetDocument} in default SharedPreferences.
 */
public final class GamepadLayoutDocumentStore {

    private static final Gson GSON = new Gson();

    private GamepadLayoutDocumentStore() {}

    public static GamepadLayoutPresetDocument loadOrCreate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(GamepadPreferenceKeys.LAYOUT_DOCUMENT_JSON, null);
        if (json != null) {
            GamepadLayoutPresetDocument d = GamepadLayoutPresetDocument.parseOrNull(json);
            if (d != null) {
                try {
                    GamepadLayoutPresetDocument.validateOrThrow(d);
                    mergeAnchorsFromDisk(context, d);
                    return d;
                } catch (IllegalArgumentException ignored) {
                    // fall through to recreate
                }
            }
        }
        GamepadLayoutPresetDocument doc = buildDefaultFromLegacyPrefs(context);
        save(context, doc);
        return doc;
    }

    public static void save(Context context, GamepadLayoutPresetDocument doc) {
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        GamepadLayoutPresetBackgroundCodec.prepareForPersistence(context, doc);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(GamepadPreferenceKeys.LAYOUT_DOCUMENT_JSON, GSON.toJson(doc))
                .apply();
        Map<String, GamepadConfigManager.ComponentPosition> pos = new java.util.HashMap<>();
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            pos.put(m.id, new GamepadConfigManager.ComponentPosition(m.anchorX, m.anchorY));
        }
        new GamepadConfigManager(context).saveLayoutPositions(GamepadLayout.SIMPLE, pos);
    }

    private static void mergeAnchorsFromDisk(Context context, GamepadLayoutPresetDocument doc) {
        Map<String, GamepadConfigManager.ComponentPosition> disk =
                new GamepadConfigManager(context).loadLayoutPositions(GamepadLayout.SIMPLE);
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            GamepadConfigManager.ComponentPosition p = disk.get(m.id);
            if (p != null) {
                m.anchorX = p.x;
                m.anchorY = p.y;
            }
        }
    }

    /**
     * Builds a v2 document from legacy flat prefs (first install / migration).
     */
    public static GamepadLayoutPresetDocument buildDefaultFromLegacyPrefs(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        GamepadConfigManager cm = new GamepadConfigManager(context);
        Map<String, GamepadConfigManager.ComponentPosition> pos =
                cm.loadLayoutPositions(GamepadLayout.SIMPLE);

        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "inline";
        doc.meta.displayName = "Default";
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
        String stickMode = prefs.getString(GamepadPreferenceKeys.STICK_MODE, "key");
        GamepadLayoutPresetDocument.GamepadModule stick = new GamepadLayoutPresetDocument.GamepadModule();
        stick.id = "stick_left";
        stick.type = "key".equals(stickMode)
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
        btnA.displayLabel = null;
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

    public static String nextButtonModuleId(GamepadLayoutPresetDocument doc) {
        int maxNum = 1;
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m.id == null || !m.id.startsWith("button_")) {
                continue;
            }
            String suffix = m.id.substring("button_".length());
            if (suffix.matches("\\d+")) {
                maxNum = Math.max(maxNum, Integer.parseInt(suffix));
            }
        }
        return "button_" + (maxNum + 1);
    }

    /** Next id {@code touchpad_1}, {@code touchpad_2}, … based on existing TOUCHPAD modules. */
    public static String nextTouchpadModuleId(GamepadLayoutPresetDocument doc) {
        int maxNum = 0;
        if (doc != null && doc.modules != null) {
            for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
                if (m == null || m.id == null || !m.id.startsWith("touchpad_")) {
                    continue;
                }
                String suffix = m.id.substring("touchpad_".length());
                if (suffix.matches("[0-9]+")) {
                    maxNum = Math.max(maxNum, Integer.parseInt(suffix));
                }
            }
        }
        return "touchpad_" + (maxNum + 1);
    }

    /**
     * Next id {@code stick_left_2}, {@code stick_left_3}, … for optional extra left thumb modules.
     */
    public static String nextAuxLeftStickModuleId(@Nullable GamepadLayoutPresetDocument doc) {
        int maxNum = 1;
        if (doc != null && doc.modules != null) {
            for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
                if (m == null || m.id == null || !m.id.startsWith("stick_left_")) {
                    continue;
                }
                String suffix = m.id.substring("stick_left_".length());
                if (suffix.matches("[0-9]+")) {
                    maxNum = Math.max(maxNum, Integer.parseInt(suffix));
                }
            }
        }
        return "stick_left_" + (maxNum + 1);
    }

    private static GamepadLayoutPresetDocument.GamepadModule find(List<GamepadLayoutPresetDocument.GamepadModule> modules, String id) {
        for (GamepadLayoutPresetDocument.GamepadModule m : modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }
}
