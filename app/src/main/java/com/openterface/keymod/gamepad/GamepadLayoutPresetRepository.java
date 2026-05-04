package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists gamepad layout presets as JSON files under app files dir; tracks index + active preset in prefs.
 */
public class GamepadLayoutPresetRepository {

    private static final String TAG = "GamepadPresetRepo";
    private static final String PREFS_NAME = "GamepadLayoutPresetStore";
    private static final String KEY_INDEX = "preset_index_json";
    private static final String KEY_ACTIVE = "active_preset_id";
    private static final String KEY_STORE_VERSION = "store_version";
    private static final int STORE_VERSION = 1;

    private final Context context;
    private final SharedPreferences storePrefs;
    private final Gson gson = new Gson();

    public GamepadLayoutPresetRepository(Context context) {
        this.context = context.getApplicationContext();
        this.storePrefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private File presetFile(String id) {
        File dir = new File(context.getFilesDir(), "gamepad_layout_presets");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "Could not mkdir presets");
        }
        return new File(dir, id + ".json");
    }

    public void ensureMigratedFromLegacy() {
        if (storePrefs.getInt(KEY_STORE_VERSION, 0) >= STORE_VERSION) {
            return;
        }
        List<PresetRef> index = readIndex();
        if (index.isEmpty()) {
            try {
                GamepadLayoutPresetDocument doc = GamepadLayoutPresetSnapshotBuilder.buildFrom(
                        context, GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, "Default");
                writeFile(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, doc);
                index.add(new PresetRef(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, "Default"));
                saveIndex(index);
                storePrefs.edit()
                        .putString(KEY_ACTIVE, GamepadLayoutPresetConstants.DEFAULT_PRESET_ID)
                        .putInt(KEY_STORE_VERSION, STORE_VERSION)
                        .apply();
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
                storePrefs.edit().putInt(KEY_STORE_VERSION, STORE_VERSION).apply();
            }
        } else {
            storePrefs.edit().putInt(KEY_STORE_VERSION, STORE_VERSION).apply();
        }
    }

    @Nullable
    public String getActivePresetId() {
        return storePrefs.getString(KEY_ACTIVE, GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
    }

    public void setActivePresetId(String id) {
        storePrefs.edit().putString(KEY_ACTIVE, id).apply();
    }

    @NonNull
    public List<PresetRef> listPresets() {
        return new ArrayList<>(readIndex());
    }

    @Nullable
    public GamepadLayoutPresetDocument loadDocument(String id) {
        File f = presetFile(id);
        if (!f.isFile()) {
            return null;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(f);
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            GamepadLayoutPresetDocument d = gson.fromJson(r, GamepadLayoutPresetDocument.class);
            if (d != null) {
                GamepadLayoutPresetDocument.validateOrThrow(d);
            }
            return d;
        } catch (Exception e) {
            Log.e(TAG, "load " + id, e);
            return null;
        }
    }

    public void writeFile(String id, GamepadLayoutPresetDocument doc) throws IOException {
        doc.meta.id = id;
        File f = presetFile(id);
        try (OutputStreamWriter w = new OutputStreamWriter(
                new java.io.FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(GamepadLayoutPresetDocument.toJsonPretty(doc));
        }
    }

    public void persistActiveSnapshot() {
        String active = getActivePresetId();
        if (active == null) {
            return;
        }
        List<PresetRef> idx = readIndex();
        String name = "Preset";
        for (PresetRef r : idx) {
            if (active.equals(r.id)) {
                name = r.displayName != null ? r.displayName : name;
                break;
            }
        }
        try {
            GamepadLayoutPresetDocument doc = GamepadLayoutPresetSnapshotBuilder.buildFrom(context, active, name);
            writeFile(active, doc);
        } catch (IOException e) {
            Log.e(TAG, "persist snapshot", e);
        }
    }

    /**
     * @return null on success, or error message.
     */
    @Nullable
    public String importFromJson(String json, boolean setActive) {
        GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
        if (parsed == null) {
            return "Invalid JSON";
        }
        try {
            GamepadLayoutPresetDocument.validateOrThrow(parsed);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        String newId = "preset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String display = parsed.meta != null && parsed.meta.displayName != null
                ? parsed.meta.displayName
                : "Imported";
        parsed.meta = parsed.meta != null ? parsed.meta : new GamepadLayoutPresetDocument.Meta();
        parsed.meta.id = newId;
        parsed.meta.displayName = display;
        try {
            writeFile(newId, parsed);
        } catch (IOException e) {
            return e.getMessage();
        }
        List<PresetRef> idx = readIndex();
        idx.add(new PresetRef(newId, display));
        saveIndex(idx);
        if (setActive) {
            setActivePresetId(newId);
            try {
                GamepadLayoutPresetApplier.apply(context, parsed);
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        }
        return null;
    }

    /**
     * Reads JSON from a content URI (import picker).
     */
    @Nullable
    public String importFromUri(android.net.Uri uri, boolean setActive) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return "Could not open file";
            }
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                int n;
                while ((n = r.read(buf)) >= 0) {
                    sb.append(buf, 0, n);
                }
            }
            return importFromJson(sb.toString(), setActive);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    @Nullable
    public String activateAndApply(String id) {
        GamepadLayoutPresetDocument d = loadDocument(id);
        if (d == null) {
            return "Preset not found";
        }
        try {
            GamepadLayoutPresetApplier.apply(context, d);
            setActivePresetId(id);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public static class PresetRef {
        public String id;
        public String displayName;

        public PresetRef() {}

        public PresetRef(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    private List<PresetRef> readIndex() {
        String json = storePrefs.getString(KEY_INDEX, "[]");
        Type type = new TypeToken<List<PresetRef>>() {}.getType();
        List<PresetRef> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    private void saveIndex(List<PresetRef> list) {
        storePrefs.edit().putString(KEY_INDEX, gson.toJson(list)).apply();
    }
}
