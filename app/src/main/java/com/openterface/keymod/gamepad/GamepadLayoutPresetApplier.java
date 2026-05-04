package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;

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
        GamepadLayoutPresetDocument.GamepadModule stick = require(modules, "stick_left");
        if (GamepadLayoutPresetConstants.MODULE_TYPE_STICK_KEY.equals(stick.type)) {
            ed.putString(GamepadPreferenceKeys.STICK_MODE, "key");
            ed.putInt(GamepadPreferenceKeys.STICK_UP, stick.stickUpKey);
            ed.putInt(GamepadPreferenceKeys.STICK_LEFT, stick.stickLeftKey);
            ed.putInt(GamepadPreferenceKeys.STICK_DOWN, stick.stickDownKey);
            ed.putInt(GamepadPreferenceKeys.STICK_RIGHT, stick.stickRightKey);
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
        }
        ed.putFloat(GamepadPreferenceKeys.STICK_SIZE, stick.scale);

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
}
