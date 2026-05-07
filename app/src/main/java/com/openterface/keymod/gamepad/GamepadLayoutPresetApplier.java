package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import com.openterface.keymod.GamepadConfigManager;
import com.openterface.keymod.GamepadLayout;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a validated {@link GamepadLayoutPresetDocument} into SharedPreferences and {@link GamepadConfigManager}.
 */
public final class GamepadLayoutPresetApplier {

    private static final Gson GSON = new Gson();

    private GamepadLayoutPresetApplier() {}

    public static void apply(Context context, GamepadLayoutPresetDocument doc) throws IllegalArgumentException {
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        GamepadLayoutPresetBackgroundCodec.prepareForPersistence(context, doc);
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();
        GamepadLayoutPresetDocument.LayoutGlobals L = doc.layout;

        ed.putFloat(GamepadPreferenceKeys.MOUSE_SENSITIVITY, L.mouseSensitivity);
        if (L.rightStickMouseGain != null) {
            ed.putFloat(GamepadPreferenceKeys.RIGHT_STICK_MOUSE_GAIN, L.rightStickMouseGain);
        } else {
            ed.remove(GamepadPreferenceKeys.RIGHT_STICK_MOUSE_GAIN);
        }
        ed.putBoolean(GamepadPreferenceKeys.TWO_BUTTON_MODE, L.showTwoButtons);
        if (L.backgroundImageFile != null) {
            File bg = new File(context.getFilesDir(), L.backgroundImageFile);
            if (bg.isFile()) {
                ed.putString(GamepadPreferenceKeys.BG_IMAGE, L.backgroundImageFile);
                ed.putFloat(GamepadPreferenceKeys.BG_SCALE, L.backgroundScale);
                ed.putFloat(GamepadPreferenceKeys.BG_OFFSET_X, L.backgroundOffsetX);
                ed.putFloat(GamepadPreferenceKeys.BG_OFFSET_Y, L.backgroundOffsetY);
            } else {
                // Shared JSON cannot carry image bytes; missing file → no background on this device.
                L.backgroundImageFile = null;
                L.backgroundScale = 1.0f;
                L.backgroundOffsetX = 0f;
                L.backgroundOffsetY = 0f;
                ed.remove(GamepadPreferenceKeys.BG_IMAGE);
                ed.remove(GamepadPreferenceKeys.BG_SCALE);
                ed.remove(GamepadPreferenceKeys.BG_OFFSET_X);
                ed.remove(GamepadPreferenceKeys.BG_OFFSET_Y);
            }
        } else {
            ed.remove(GamepadPreferenceKeys.BG_IMAGE);
            ed.putFloat(GamepadPreferenceKeys.BG_SCALE, L.backgroundScale);
            ed.putFloat(GamepadPreferenceKeys.BG_OFFSET_X, L.backgroundOffsetX);
            ed.putFloat(GamepadPreferenceKeys.BG_OFFSET_Y, L.backgroundOffsetY);
        }

        List<GamepadLayoutPresetDocument.GamepadModule> modules = doc.modules;
        GamepadLayoutPresetDocument.GamepadModule stick = resolveLeftThumbStickForPrefs(modules);
        if (stick == null) {
            ed.remove(GamepadPreferenceKeys.STICK_MODE);
            ed.remove(GamepadPreferenceKeys.STICK_UP);
            ed.remove(GamepadPreferenceKeys.STICK_LEFT);
            ed.remove(GamepadPreferenceKeys.STICK_DOWN);
            ed.remove(GamepadPreferenceKeys.STICK_RIGHT);
            ed.remove(GamepadPreferenceKeys.STICK_SIZE);
        } else if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(stick.type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(stick.type)) {
            ed.putString(GamepadPreferenceKeys.STICK_MODE, "key");
            ed.putInt(GamepadPreferenceKeys.STICK_UP, stick.stickUpKey);
            ed.putInt(GamepadPreferenceKeys.STICK_LEFT, stick.stickLeftKey);
            ed.putInt(GamepadPreferenceKeys.STICK_DOWN, stick.stickDownKey);
            ed.putInt(GamepadPreferenceKeys.STICK_RIGHT, stick.stickRightKey);
            ed.putFloat(GamepadPreferenceKeys.STICK_SIZE, stick.scale);
        } else {
            ed.putString(GamepadPreferenceKeys.STICK_MODE, "analog");
            int up = stick.stickUpKey != null ? stick.stickUpKey : 26;
            int left = stick.stickLeftKey != null ? stick.stickLeftKey : 4;
            int down = stick.stickDownKey != null ? stick.stickDownKey : 22;
            int right = stick.stickRightKey != null ? stick.stickRightKey : 7;
            ed.putInt(GamepadPreferenceKeys.STICK_UP, up);
            ed.putInt(GamepadPreferenceKeys.STICK_LEFT, left);
            ed.putInt(GamepadPreferenceKeys.STICK_DOWN, down);
            ed.putInt(GamepadPreferenceKeys.STICK_RIGHT, right);
            ed.putFloat(GamepadPreferenceKeys.STICK_SIZE, stick.scale);
        }

        GamepadLayoutPresetDocument.GamepadModule btnA = require(modules, "button_a");
        ed.putFloat(GamepadPreferenceKeys.BUTTON_SIZE, btnA.scale);
        ed.putInt(GamepadPreferenceKeys.BUTTON_A_KEY, btnA.hidKey);
        ed.putInt(GamepadPreferenceKeys.BUTTON_A_MOD, btnA.modifierMask != null ? btnA.modifierMask : 0);

        if (L.showTwoButtons) {
            GamepadLayoutPresetDocument.GamepadModule btnB = require(modules, "button_b");
            ed.putInt(GamepadPreferenceKeys.BUTTON_B_KEY, btnB.hidKey);
            ed.putInt(GamepadPreferenceKeys.BUTTON_B_MOD, btnB.modifierMask != null ? btnB.modifierMask : 0);
        }

        ed.putString(GamepadPreferenceKeys.LAYOUT_DOCUMENT_JSON, GSON.toJson(doc));
        ed.apply();

        Map<String, GamepadConfigManager.ComponentPosition> positions = new HashMap<>();
        for (GamepadLayoutPresetDocument.GamepadModule m : modules) {
            positions.put(m.id, new GamepadConfigManager.ComponentPosition(m.anchorX, m.anchorY));
        }
        if (!L.showTwoButtons && !positions.containsKey("button_b")) {
            positions.put("button_b", new GamepadConfigManager.ComponentPosition(0.93f, 0.40f));
        }
        new GamepadConfigManager(context).saveLayoutPositions(GamepadLayout.SIMPLE, positions);
    }

    private static GamepadLayoutPresetDocument.GamepadModule require(
            List<GamepadLayoutPresetDocument.GamepadModule> modules, String id) {
        for (GamepadLayoutPresetDocument.GamepadModule m : modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Missing module: " + id);
    }

    /**
     * Legacy prefs mirror: prefer {@code stick_left}, else lowest-numbered {@code stick_left_2+} thumb
     * ({@code STICK_KEY} / {@code DPAD} / {@code STICK_MOUSE}).
     */
    @Nullable
    private static GamepadLayoutPresetDocument.GamepadModule resolveLeftThumbStickForPrefs(
            List<GamepadLayoutPresetDocument.GamepadModule> modules) {
        if (modules == null) {
            return null;
        }
        GamepadLayoutPresetDocument.GamepadModule primary = null;
        GamepadLayoutPresetDocument.GamepadModule bestAux = null;
        int bestNum = Integer.MAX_VALUE;
        for (GamepadLayoutPresetDocument.GamepadModule m : modules) {
            if (m == null || m.id == null || m.type == null) {
                continue;
            }
            if (!isLeftThumbStickPrefsType(m)) {
                continue;
            }
            if ("stick_left".equals(m.id)) {
                primary = m;
                break;
            }
            if (GamepadLayoutPresetConstants.isAuxLeftStickModuleId(m.id)) {
                String suf = m.id.substring("stick_left_".length());
                try {
                    int n = Integer.parseInt(suf);
                    if (n < bestNum) {
                        bestNum = n;
                        bestAux = m;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return primary != null ? primary : bestAux;
    }

    private static boolean isLeftThumbStickPrefsType(GamepadLayoutPresetDocument.GamepadModule m) {
        return GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(m.type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_DPAD.equals(m.type)
                || GamepadLayoutPresetConstants.MODULE_TYPE_STICK_MOUSE.equals(m.type);
    }
}
