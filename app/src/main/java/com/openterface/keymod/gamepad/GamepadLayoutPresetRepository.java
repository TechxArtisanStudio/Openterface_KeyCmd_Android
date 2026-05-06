package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    /** v1: initial preset store; v2: built-in two-button sibling preset + optional active switch from legacy toggle. */
    private static final int STORE_VERSION = 2;

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
        int version = storePrefs.getInt(KEY_STORE_VERSION, 0);
        if (version >= STORE_VERSION) {
            return;
        }
        List<PresetRef> index = readIndex();
        if (index.isEmpty()) {
            try {
                GamepadLayoutPresetDocument doc = GamepadLayoutDocumentStore.buildDefaultFromLegacyPrefs(context);
                GamepadLayoutPresetDocument.validateOrThrow(doc);
                writeFile(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, doc);
                index.add(new PresetRef(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, "Default"));
                saveIndex(index);
                storePrefs.edit()
                        .putString(KEY_ACTIVE, GamepadLayoutPresetConstants.DEFAULT_PRESET_ID)
                        .apply();
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
            }
        }
        try {
            index = readIndex();
            ensureBuiltInTwoButtonPreset(index);
            if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(GamepadPreferenceKeys.TWO_BUTTON_MODE, false)) {
                GamepadLayoutPresetDocument twoDoc = loadDocument(GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID);
                if (twoDoc != null) {
                    setActivePresetId(GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID);
                }
            }
            applyActivePresetToDefaultPrefs();
        } catch (Exception e) {
            Log.e(TAG, "Built-in two-button preset migration", e);
        }
        storePrefs.edit().putInt(KEY_STORE_VERSION, STORE_VERSION).apply();
    }

    private void applyActivePresetToDefaultPrefs() {
        String active = getActivePresetId();
        if (active == null) {
            return;
        }
        GamepadLayoutPresetDocument d = loadDocument(active);
        if (d == null) {
            return;
        }
        try {
            GamepadLayoutPresetApplier.apply(context, d);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "apply active after migration", e);
        }
    }

    /**
     * Ensures {@link GamepadLayoutPresetConstants#BUILT_IN_TWO_BUTTON_PRESET_ID} exists on disk and in the index,
     * cloned from the default preset (or built from legacy prefs if default file is missing).
     */
    private void ensureBuiltInTwoButtonPreset(List<PresetRef> index) throws IOException {
        String twoId = GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID;
        if (presetFile(twoId).isFile() && indexContainsId(index, twoId)) {
            return;
        }
        GamepadLayoutPresetDocument base = loadDocument(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
        if (base == null) {
            base = GamepadLayoutDocumentStore.buildDefaultFromLegacyPrefs(context);
            GamepadLayoutPresetDocument.validateOrThrow(base);
        } else {
            base = gson.fromJson(gson.toJson(base), GamepadLayoutPresetDocument.class);
        }
        base.layout.showTwoButtons = true;
        GamepadLayoutDocEditor.ensureButtonB(base);
        GamepadLayoutPresetDocument.validateOrThrow(base);
        writeFile(twoId, base);
        if (!indexContainsId(index, twoId)) {
            int insertAt = 0;
            for (int i = 0; i < index.size(); i++) {
                if (GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(index.get(i).id)) {
                    insertAt = i + 1;
                    break;
                }
            }
            index.add(insertAt, new PresetRef(twoId, "Two buttons"));
            saveIndex(index);
        }
    }

    private static boolean indexContainsId(List<PresetRef> index, String id) {
        for (PresetRef r : index) {
            if (r != null && id.equals(r.id)) {
                return true;
            }
        }
        return false;
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

    /**
     * Updates display name in index and in the preset JSON on disk.
     *
     * @return null on success, or error message.
     */
    @Nullable
    public String renamePreset(@Nullable String id, @Nullable String newDisplayName) {
        if (id == null || id.isEmpty()) {
            return "Invalid preset";
        }
        if (newDisplayName == null || newDisplayName.trim().isEmpty()) {
            return "Name required";
        }
        String trimmed = newDisplayName.trim();
        List<PresetRef> idx = readIndex();
        boolean found = false;
        for (PresetRef r : idx) {
            if (r != null && id.equals(r.id)) {
                r.displayName = trimmed;
                found = true;
                break;
            }
        }
        if (!found) {
            return "Preset not found";
        }
        GamepadLayoutPresetDocument doc = loadDocument(id);
        if (doc == null) {
            return "Preset not found";
        }
        if (doc.meta == null) {
            doc.meta = new GamepadLayoutPresetDocument.Meta();
        }
        doc.meta.displayName = trimmed;
        try {
            writeFile(id, doc);
        } catch (IOException e) {
            return e.getMessage();
        }
        saveIndex(idx);
        return null;
    }

    public static final class DuplicateResult {
        @Nullable public final String newId;
        @Nullable public final String error;

        DuplicateResult(@Nullable String newId, @Nullable String error) {
            this.newId = newId;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null && newId != null;
        }
    }

    /**
     * Deep-copies a preset to a new id. Display name becomes {@code "<name> (copy)"} based on index or meta.
     */
    @NonNull
    public DuplicateResult duplicatePreset(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return new DuplicateResult(null, "Invalid preset");
        }
        GamepadLayoutPresetDocument src = loadDocument(id);
        if (src == null) {
            return new DuplicateResult(null, "Preset not found");
        }
        String baseLabel = id;
        for (PresetRef r : readIndex()) {
            if (r != null && id.equals(r.id)) {
                if (r.displayName != null && !r.displayName.isEmpty()) {
                    baseLabel = r.displayName;
                }
                break;
            }
        }
        if (src.meta != null && src.meta.displayName != null && !src.meta.displayName.trim().isEmpty()) {
            baseLabel = src.meta.displayName.trim();
        }
        String newId = "preset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        GamepadLayoutPresetDocument copy = gson.fromJson(gson.toJson(src), GamepadLayoutPresetDocument.class);
        if (copy == null) {
            return new DuplicateResult(null, "Copy failed");
        }
        if (copy.meta == null) {
            copy.meta = new GamepadLayoutPresetDocument.Meta();
        }
        copy.meta.id = newId;
        String copyName = baseLabel + " (copy)";
        copy.meta.displayName = copyName;
        try {
            GamepadLayoutPresetDocument.validateOrThrow(copy);
            writeFile(newId, copy);
        } catch (Exception e) {
            return new DuplicateResult(null, e.getMessage() != null ? e.getMessage() : "Write failed");
        }
        List<PresetRef> idx = readIndex();
        idx.add(new PresetRef(newId, copyName));
        saveIndex(idx);
        return new DuplicateResult(newId, null);
    }

    /**
     * Removes a user preset from disk and index. Built-in presets cannot be removed.
     *
     * @return null on success, or error message.
     */
    @Nullable
    public String deletePreset(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return "Invalid preset";
        }
        if (GamepadLayoutPresetConstants.isPresetDeletionProtected(id)) {
            return "Cannot delete built-in layout";
        }
        List<PresetRef> idx = readIndex();
        boolean removed = false;
        List<PresetRef> next = new ArrayList<>();
        for (PresetRef r : idx) {
            if (r == null || r.id == null) {
                continue;
            }
            if (id.equals(r.id)) {
                removed = true;
                continue;
            }
            next.add(r);
        }
        if (!removed) {
            return "Preset not found";
        }
        File f = presetFile(id);
        if (f.isFile() && !f.delete()) {
            return "Could not delete file";
        }
        saveIndex(next);
        String active = getActivePresetId();
        if (id.equals(active)) {
            String fallback = GamepadLayoutPresetConstants.DEFAULT_PRESET_ID;
            if (!presetFile(fallback).isFile()) {
                fallback = GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID;
            }
            return activateAndApply(fallback);
        }
        return null;
    }

    /**
     * Reorders presets in the index. Must contain exactly the same ids as the current index (one each).
     *
     * @return null on success, or error message.
     */
    @Nullable
    public String reorderPresets(@Nullable List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return "Invalid order";
        }
        List<PresetRef> current = readIndex();
        if (orderedIds.size() != current.size()) {
            return "Count mismatch";
        }
        Set<String> expected = new HashSet<>();
        for (PresetRef r : current) {
            if (r != null && r.id != null) {
                expected.add(r.id);
            }
        }
        Set<String> got = new HashSet<>();
        for (String s : orderedIds) {
            if (s == null || s.isEmpty() || !expected.contains(s) || !got.add(s)) {
                return "Invalid order";
            }
        }
        if (got.size() != expected.size()) {
            return "Invalid order";
        }
        List<PresetRef> byId = new ArrayList<>();
        for (String id : orderedIds) {
            for (PresetRef r : current) {
                if (r != null && id.equals(r.id)) {
                    byId.add(r);
                    break;
                }
            }
        }
        saveIndex(byId);
        return null;
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
