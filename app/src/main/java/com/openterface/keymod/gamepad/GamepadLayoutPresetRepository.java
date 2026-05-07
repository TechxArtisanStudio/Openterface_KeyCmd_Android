package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.openterface.keymod.R;

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
    /**
     * v1: initial preset store; v2: built-in two-button sibling preset + optional active switch from legacy toggle;
     * v3: built-in classic layout presets (dual-stick variants + compact);
     * v4: one-time relabel of classic built-in preset display names (index + JSON meta) to neutral strings;
     * v5: relabel classics to {@code Classic_1} … {@code Classic_4} from string resources;
     * v6: rewrite {@code preset_classic_xbox} (Classic_1) from factory geometry while preserving index
     * {@code displayName} when set.
     */
    private static final int STORE_VERSION = 6;

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
        final int previousStoreVersion = version;
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
            ensureBuiltInClassicPresets();
            if (previousStoreVersion < 5) {
                relabelClassicBuiltinPresetsFromResources();
            }
            if (previousStoreVersion < 6) {
                rewriteClassic1BuiltInFromFactory();
            }
            applyActivePresetToDefaultPrefs();
        } catch (Exception e) {
            Log.e(TAG, "Built-in two-button preset migration", e);
        }
        storePrefs.edit().putInt(KEY_STORE_VERSION, STORE_VERSION).apply();
    }

    /**
     * Rewrites {@code meta.displayName} and index labels for the four classic built-ins from current
     * string resources (when upgrading preset store past v3; runs again for v5 {@code Classic_n} labels).
     * Custom renames of those presets are overwritten on each such upgrade step.
     */
    private void relabelClassicBuiltinPresetsFromResources() {
        try {
            relabelClassicBuiltinDocument(
                    GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX,
                    R.string.gamepad_preset_builtin_classic_xbox);
            relabelClassicBuiltinDocument(
                    GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION,
                    R.string.gamepad_preset_builtin_classic_playstation);
            relabelClassicBuiltinDocument(
                    GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO,
                    R.string.gamepad_preset_builtin_classic_nintendo);
            relabelClassicBuiltinDocument(
                    GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES,
                    R.string.gamepad_preset_builtin_classic_nes);
        } catch (IOException e) {
            Log.e(TAG, "Classic preset relabel", e);
        }
        List<PresetRef> index = readIndex();
        boolean changed = false;
        for (PresetRef r : index) {
            if (r == null || r.id == null) {
                continue;
            }
            String label = null;
            if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX.equals(r.id)) {
                label = context.getString(R.string.gamepad_preset_builtin_classic_xbox);
            } else if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION.equals(r.id)) {
                label = context.getString(R.string.gamepad_preset_builtin_classic_playstation);
            } else if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO.equals(r.id)) {
                label = context.getString(R.string.gamepad_preset_builtin_classic_nintendo);
            } else if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES.equals(r.id)) {
                label = context.getString(R.string.gamepad_preset_builtin_classic_nes);
            }
            if (label != null && !label.equals(r.displayName)) {
                r.displayName = label;
                changed = true;
            }
        }
        if (changed) {
            saveIndex(index);
        }
    }

    private void relabelClassicBuiltinDocument(String presetId, int displayNameRes) throws IOException {
        GamepadLayoutPresetDocument doc = loadDocument(presetId);
        if (doc == null) {
            return;
        }
        if (doc.meta == null) {
            doc.meta = new GamepadLayoutPresetDocument.Meta();
        }
        doc.meta.displayName = context.getString(displayNameRes);
        writeFile(presetId, doc);
    }

    /**
     * Refreshes on-disk Classic_1 ({@link GamepadLayoutPresetConstants#BUILT_IN_PRESET_CLASSIC_XBOX}) from
     * {@link GamepadBuiltInLayoutPresets#buildClassicXbox} so upgrades pick up the dual-zone layout; keeps the
     * index row's {@link PresetRef#displayName} when non-empty (falls back to existing JSON meta).
     */
    private void rewriteClassic1BuiltInFromFactory() {
        String id = GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX;
        if (!presetFile(id).isFile()) {
            return;
        }
        try {
            String keepName = null;
            for (PresetRef r : readIndex()) {
                if (r == null || r.id == null) {
                    continue;
                }
                if (id.equals(r.id) && r.displayName != null) {
                    String t = r.displayName.trim();
                    if (!t.isEmpty()) {
                        keepName = t;
                    }
                    break;
                }
            }
            if (keepName == null) {
                GamepadLayoutPresetDocument cur = loadDocument(id);
                if (cur != null && cur.meta != null && cur.meta.displayName != null) {
                    String t = cur.meta.displayName.trim();
                    if (!t.isEmpty()) {
                        keepName = t;
                    }
                }
            }
            GamepadLayoutPresetDocument d = GamepadBuiltInLayoutPresets.buildClassicXbox(context);
            if (keepName != null) {
                if (d.meta == null) {
                    d.meta = new GamepadLayoutPresetDocument.Meta();
                }
                d.meta.displayName = keepName;
            }
            writeFile(id, d);
        } catch (IOException e) {
            Log.e(TAG, "Classic_1 built-in geometry refresh (store v6)", e);
        }
    }

    /**
     * Writes missing classic preset JSON files and appends {@link PresetRef} entries after
     * {@link GamepadLayoutPresetConstants#BUILT_IN_TWO_BUTTON_PRESET_ID} when absent (idempotent).
     */
    private void ensureBuiltInClassicPresets() throws IOException {
        if (!presetFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX).isFile()) {
            writeFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX,
                    GamepadBuiltInLayoutPresets.buildClassicXbox(context));
        }
        if (!presetFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION).isFile()) {
            writeFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION,
                    GamepadBuiltInLayoutPresets.buildClassicPlayStation(context));
        }
        if (!presetFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO).isFile()) {
            writeFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO,
                    GamepadBuiltInLayoutPresets.buildClassicNintendo(context));
        }
        if (!presetFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES).isFile()) {
            writeFile(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES,
                    GamepadBuiltInLayoutPresets.buildClassicNes(context));
        }

        ensureClassicPresetIndexEntry(
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX,
                R.string.gamepad_preset_builtin_classic_xbox);
        ensureClassicPresetIndexEntry(
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION,
                R.string.gamepad_preset_builtin_classic_playstation);
        ensureClassicPresetIndexEntry(
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO,
                R.string.gamepad_preset_builtin_classic_nintendo);
        ensureClassicPresetIndexEntry(
                GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES,
                R.string.gamepad_preset_builtin_classic_nes);
    }

    private static final String[] CLASSIC_PRESET_ORDER = {
            GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX,
            GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION,
            GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO,
            GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES,
    };

    private int insertionIndexAfterBuiltInTwins(List<PresetRef> index) {
        for (int i = 0; i < index.size(); i++) {
            PresetRef r = index.get(i);
            if (r != null && GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID.equals(r.id)) {
                return i + 1;
            }
        }
        for (int i = 0; i < index.size(); i++) {
            PresetRef r = index.get(i);
            if (r != null && GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(r.id)) {
                return i + 1;
            }
        }
        return index.size();
    }

    private static int indexOfPresetId(List<PresetRef> index, String id) {
        for (int i = 0; i < index.size(); i++) {
            PresetRef r = index.get(i);
            if (r != null && id.equals(r.id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Insert position for {@code insertingId} so classic built-ins stay in {@link #CLASSIC_PRESET_ORDER}.
     */
    private int insertionIndexForClassicPreset(List<PresetRef> index, String insertingId) {
        int insertAt = insertionIndexAfterBuiltInTwins(index);
        int ord = -1;
        for (int i = 0; i < CLASSIC_PRESET_ORDER.length; i++) {
            if (CLASSIC_PRESET_ORDER[i].equals(insertingId)) {
                ord = i;
                break;
            }
        }
        if (ord <= 0) {
            return insertAt;
        }
        for (int j = 0; j < ord; j++) {
            int idx = indexOfPresetId(index, CLASSIC_PRESET_ORDER[j]);
            if (idx >= 0) {
                insertAt = Math.max(insertAt, idx + 1);
            }
        }
        return insertAt;
    }

    private void ensureClassicPresetIndexEntry(String id, int displayNameRes) {
        List<PresetRef> index = readIndex();
        if (indexContainsId(index, id)) {
            return;
        }
        int at = insertionIndexForClassicPreset(index, id);
        index.add(at, new PresetRef(id, context.getString(displayNameRes)));
        saveIndex(index);
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
        GamepadLayoutPresetBackgroundCodec.prepareForPersistence(context, doc);
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
     * Overwrites a built-in classic preset file with the canonical factory document and re-applies it
     * when it is the active preset. Preserves the index/display {@link PresetRef#displayName} in JSON meta when set.
     *
     * @return null on success, or error message.
     */
    @Nullable
    public String resetClassicPresetToFactory(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return context.getString(R.string.gamepad_preset_reset_invalid);
        }
        if (!GamepadLayoutPresetConstants.isClassicBuiltInPresetId(id)) {
            return context.getString(R.string.gamepad_preset_reset_invalid);
        }
        try {
            GamepadLayoutPresetDocument doc = buildClassicPresetDocumentForContext(id);
            List<PresetRef> idx = readIndex();
            for (PresetRef r : idx) {
                if (r != null && id.equals(r.id) && r.displayName != null && !r.displayName.trim().isEmpty()) {
                    if (doc.meta == null) {
                        doc.meta = new GamepadLayoutPresetDocument.Meta();
                    }
                    doc.meta.displayName = r.displayName.trim();
                    break;
                }
            }
            writeFile(id, doc);
            if (id.equals(getActivePresetId())) {
                GamepadLayoutPresetDocument applied = loadDocument(id);
                if (applied != null) {
                    GamepadLayoutPresetApplier.apply(context, applied);
                }
            }
            return null;
        } catch (IOException e) {
            return e.getMessage() != null ? e.getMessage() : "Reset failed";
        } catch (IllegalArgumentException e) {
            return e.getMessage() != null ? e.getMessage() : "Reset failed";
        }
    }

    private GamepadLayoutPresetDocument buildClassicPresetDocumentForContext(String id) {
        if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX.equals(id)) {
            return GamepadBuiltInLayoutPresets.buildClassicXbox(context);
        }
        if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION.equals(id)) {
            return GamepadBuiltInLayoutPresets.buildClassicPlayStation(context);
        }
        if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO.equals(id)) {
            return GamepadBuiltInLayoutPresets.buildClassicNintendo(context);
        }
        if (GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES.equals(id)) {
            return GamepadBuiltInLayoutPresets.buildClassicNes(context);
        }
        throw new IllegalArgumentException("Unknown classic preset: " + id);
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
