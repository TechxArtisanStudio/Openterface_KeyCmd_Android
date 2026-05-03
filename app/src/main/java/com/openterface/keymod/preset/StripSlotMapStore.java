package com.openterface.keymod.preset;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists optional overrides for fixed strip slots (page, row, col, base|fn) → shortcut id
 * in the reserved strip profile catalog. Keys look like {@code p0_r2_c0_base}.
 */
public final class StripSlotMapStore {

    private static final String PREFS = "KeyboardStripSlotMap_v1";
    private static final String KEY_MAP = "slot_map_json";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public StripSlotMapStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String slotKey(int fixedPage, int row, int col, boolean fnLayer) {
        return "p" + fixedPage + "_r" + row + "_c" + col + "_" + (fnLayer ? "fn" : "base");
    }

    public Map<String, String> getAll() {
        String json = prefs.getString(KEY_MAP, null);
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> m = gson.fromJson(json, type);
        return m != null ? new HashMap<>(m) : new HashMap<>();
    }

    public void replaceAll(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            prefs.edit().remove(KEY_MAP).apply();
            return;
        }
        prefs.edit().putString(KEY_MAP, gson.toJson(map)).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY_MAP).apply();
    }

    /** Immutable snapshot for export. */
    public Map<String, String> snapshotForExport() {
        return Collections.unmodifiableMap(new HashMap<>(getAll()));
    }
}
