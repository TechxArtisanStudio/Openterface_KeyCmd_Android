package com.openterface.keymod.preset;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists optional overrides for fixed strip slots (page, row, col, base|fn) → shortcut id
 * in the reserved strip profile catalog. Keys use {@code b-p0r2c1} … {@code b-p0r2c7} (base) and
 * {@code f-p0r2c1} … {@code f-p0r2c7} (fn); column is 1-based in the id, matching the seven keys per row.
 */
public final class StripSlotMapStore {

    private static final String PREFS = "KeyboardStripSlotMap_v1";
    private static final String KEY_MAP = "slot_map_json";

    /** Legacy: {@code p0_r2_c0_base} (0-based column suffix). */
    private static final Pattern LEGACY_SLOT_KEY = Pattern.compile(
            "^p(\\d+)_r(2|3)_c(\\d+)_(base|fn)$", Pattern.CASE_INSENSITIVE);

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public StripSlotMapStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Canonical slot id for persistence and UI. {@code col} is 0-based (0…{@link KeyboardStripPresetConstants#TOP_PANEL_COLUMNS}-1);
     * the encoded column is {@code col + 1} (…c1…c7).
     */
    @NonNull
    public static String slotKey(int fixedPage, int row, int col, boolean fnLayer) {
        if (col < 0 || col >= KeyboardStripPresetConstants.TOP_PANEL_COLUMNS) {
            throw new IllegalArgumentException("col out of range: " + col);
        }
        return (fnLayer ? "f" : "b") + "-p" + fixedPage + "r" + row + "c" + (col + 1);
    }

    /**
     * If {@code key} uses the legacy {@code p0_r2_c0_base} form, returns the canonical {@code b-p0r2c1} form;
     * otherwise returns {@code key} trimmed (already canonical or unknown).
     */
    @NonNull
    public static String canonicalSlotKeyOrSelf(@Nullable String key) {
        if (key == null) {
            return "";
        }
        String t = key.trim();
        Matcher m = LEGACY_SLOT_KEY.matcher(t);
        if (!m.matches()) {
            return t;
        }
        int page = Integer.parseInt(m.group(1));
        int row = Integer.parseInt(m.group(2));
        int col0 = Integer.parseInt(m.group(3));
        boolean fn = "fn".equalsIgnoreCase(m.group(4));
        if (col0 < 0 || col0 >= KeyboardStripPresetConstants.TOP_PANEL_COLUMNS) {
            return t;
        }
        return slotKey(page, row, col0, fn);
    }

    /**
     * Rebuilds a slot map with canonical keys only (migrates legacy keys in one pass).
     */
    @NonNull
    public static Map<String, String> remapSlotMapKeysToCanonical(@NonNull Map<String, String> slotMap) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : slotMap.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String nk = canonicalSlotKeyOrSelf(e.getKey());
            String v = e.getValue();
            if (v != null && !v.trim().isEmpty()) {
                out.put(nk, v.trim());
            }
        }
        return out;
    }

    public Map<String, String> getAll() {
        String json = prefs.getString(KEY_MAP, null);
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> m = gson.fromJson(json, type);
        Map<String, String> raw = m != null ? new HashMap<>(m) : new HashMap<>();
        Map<String, String> remapped = remapSlotMapKeysToCanonical(raw);
        if (!remapped.equals(raw)) {
            replaceAll(remapped);
        }
        return remapped;
    }

    public void replaceAll(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            prefs.edit().remove(KEY_MAP).apply();
            return;
        }
        Map<String, String> canonical = remapSlotMapKeysToCanonical(map);
        prefs.edit().putString(KEY_MAP, gson.toJson(canonical)).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY_MAP).apply();
    }

    /** Immutable snapshot for export. */
    public Map<String, String> snapshotForExport() {
        return Collections.unmodifiableMap(new HashMap<>(getAll()));
    }
}
