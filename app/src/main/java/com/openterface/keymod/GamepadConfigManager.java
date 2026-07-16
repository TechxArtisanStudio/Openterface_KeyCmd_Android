package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Gamepad Configuration Manager
 * Handles saving/loading button positions and mappings
 */
public class GamepadConfigManager {

    private static final String TAG = "GamepadConfigManager";
    private static final String PREFS_NAME = "GamepadConfig";
    private static final String KEY_POSITIONS = "button_positions";
    private static final String KEY_MAPPINGS = "button_mappings";

    private final SharedPreferences prefs;
    private final Gson gson;

    public GamepadConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * Save component positions for a layout
     */
    public void saveLayoutPositions(GamepadLayout layout, Map<String, ComponentPosition> positions) {
        String key = KEY_POSITIONS + "_" + layout.name();
        String json = gson.toJson(positions);
        prefs.edit().putString(key, json).apply();
        Log.v(TAG, "Saved positions for " + layout.name() + ": " + positions.size() + " components");
    }

    /**
     * Load component positions for a layout
     */
    public Map<String, ComponentPosition> loadLayoutPositions(GamepadLayout layout) {
        String key = KEY_POSITIONS + "_" + layout.name();
        String json = prefs.getString(key, null);
        
        if (json == null) {
            // Return default positions
            return getDefaultPositions(layout);
        }

        Type type = new TypeToken<Map<String, ComponentPosition>>(){}.getType();
        Map<String, ComponentPosition> positions = gson.fromJson(json, type);
        Log.v(TAG, "Loaded positions for " + layout.name() + ": " + positions.size() + " components");
        return positions;
    }

    /**
     * Save button mapping
     */
    public void saveButtonMapping(String buttonId, int keyCode) {
        String key = KEY_MAPPINGS + "_" + buttonId;
        prefs.edit().putInt(key, keyCode).apply();
        Log.v(TAG, "Saved mapping: " + buttonId + " -> " + keyCode);
    }

    /**
     * Load button mapping
     */
    public int loadButtonMapping(String buttonId, int defaultKeyCode) {
        String key = KEY_MAPPINGS + "_" + buttonId;
        int keyCode = prefs.getInt(key, defaultKeyCode);
        Log.v(TAG, "Loaded mapping: " + buttonId + " -> " + keyCode);
        return keyCode;
    }

    /**
     * Reset all mappings for a layout to defaults
     */
    public void resetLayoutMappings(GamepadLayout layout) {
        // Clear all mappings for this layout
        SharedPreferences.Editor editor = prefs.edit();
        // This is simplified - in production, track all button IDs per layout
        editor.apply();
        Log.v(TAG, "Reset mappings for " + layout.name());
    }

    /**
     * Get default positions for a layout
     */
    private Map<String, ComponentPosition> getDefaultPositions(GamepadLayout layout) {
        Map<String, ComponentPosition> positions = new HashMap<>();
        
        switch (layout) {
            case XBOX:
                // Default Xbox layout positions (percentage-based)
                positions.put("dpad", new ComponentPosition(0.15f, 0.5f));
                positions.put("stick_left", new ComponentPosition(0.25f, 0.65f));
                positions.put("stick_right", new ComponentPosition(0.75f, 0.65f));
                positions.put("button_a", new ComponentPosition(0.85f, 0.55f));
                positions.put("button_b", new ComponentPosition(0.90f, 0.45f));
                positions.put("button_x", new ComponentPosition(0.80f, 0.45f));
                positions.put("button_y", new ComponentPosition(0.85f, 0.35f));
                positions.put("lb", new ComponentPosition(0.30f, 0.15f));
                positions.put("rb", new ComponentPosition(0.70f, 0.15f));
                positions.put("lt", new ComponentPosition(0.30f, 0.08f));
                positions.put("rt", new ComponentPosition(0.70f, 0.08f));
                positions.put("back", new ComponentPosition(0.40f, 0.40f));
                positions.put("start", new ComponentPosition(0.60f, 0.40f));
                break;

            case PLAYSTATION:
                // Default PlayStation layout positions
                positions.put("dpad", new ComponentPosition(0.15f, 0.5f));
                positions.put("stick_left", new ComponentPosition(0.25f, 0.65f));
                positions.put("stick_right", new ComponentPosition(0.75f, 0.65f));
                positions.put("button_cross", new ComponentPosition(0.85f, 0.55f));
                positions.put("button_circle", new ComponentPosition(0.90f, 0.45f));
                positions.put("button_square", new ComponentPosition(0.80f, 0.45f));
                positions.put("button_triangle", new ComponentPosition(0.85f, 0.35f));
                positions.put("l1", new ComponentPosition(0.30f, 0.15f));
                positions.put("r1", new ComponentPosition(0.70f, 0.15f));
                positions.put("l2", new ComponentPosition(0.30f, 0.08f));
                positions.put("r2", new ComponentPosition(0.70f, 0.08f));
                positions.put("select", new ComponentPosition(0.40f, 0.40f));
                positions.put("start", new ComponentPosition(0.60f, 0.40f));
                break;

            case NES:
                // Default NES layout positions (simpler)
                positions.put("dpad", new ComponentPosition(0.20f, 0.5f));
                positions.put("button_a", new ComponentPosition(0.80f, 0.55f));
                positions.put("button_b", new ComponentPosition(0.90f, 0.45f));
                positions.put("select", new ComponentPosition(0.40f, 0.70f));
                positions.put("start", new ComponentPosition(0.60f, 0.70f));
                break;

            case SIMPLE:
                // Simple layout: analog stick left, single button far right
                positions.put("stick_left", new ComponentPosition(0.20f, 0.5f));
                positions.put("button_a", new ComponentPosition(0.85f, 0.5f));
                positions.put("button_b", new ComponentPosition(0.93f, 0.4f));
                break;
        }

        return positions;
    }

    /**
     * Component Position class
     * Represents x,y position as percentages (0.0 - 1.0)
     */
    public static class ComponentPosition {
        public float x, y;

        public ComponentPosition() {
            this.x = 0.5f;
            this.y = 0.5f;
        }

        public ComponentPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
