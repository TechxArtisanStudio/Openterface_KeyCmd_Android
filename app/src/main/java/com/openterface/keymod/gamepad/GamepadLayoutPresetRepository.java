package com.openterface.keymod.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.openterface.keymod.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    /** Slugs (filename-based) for bundled presets the user removed; sync skips these until reinstall. */
    private static final String KEY_DELETED_BUNDLED_SLUGS = "deleted_bundled_pack_slugs";
    /**
     * Last {@link BuildConfig#BUNDLED_GAMEPAD_DEFAULT_FINGERPRINT} applied to {@code preset_default.json};
     * when the repo file changes and the app is rebuilt, we re-import bundled default from assets.
     */
    private static final String KEY_BUNDLED_DEFAULT_FINGERPRINT_APPLIED = "bundled_default_fingerprint_applied";
    /**
     * v1–v6: preset store evolution (default, two-button, classic built-ins, relabels, Classic_1 geometry).
     * v7: removes built-in {@code preset_two_buttons} and four {@code preset_classic_*} presets from index and disk.
     */
    private static final int STORE_VERSION = 7;

    /** Built-in layouts removed in store v7 (ids kept here for migration only). */
    private static final String[] REMOVED_BUILTIN_PRESET_IDS = {
            "preset_two_buttons",
            "preset_classic_xbox",
            "preset_classic_playstation",
            "preset_classic_nintendo",
            "preset_classic_nes",
    };

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
                GamepadLayoutPresetDocument doc = loadBundledDefaultPresetFromAssets();
                if (doc == null) {
                    doc = GamepadLayoutDocumentStore.buildDefaultFromLegacyPrefs(context);
                    GamepadLayoutPresetDocument.validateOrThrow(doc);
                }
                writeFile(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, doc);
                List<PresetRef> initial = new ArrayList<>();
                initial.add(new PresetRef(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, "Default"));
                saveIndex(initial);
                storePrefs.edit()
                        .putString(KEY_ACTIVE, GamepadLayoutPresetConstants.DEFAULT_PRESET_ID)
                        .apply();
                try {
                    GamepadLayoutPresetApplier.apply(context, doc, true);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Apply seeded default preset to prefs", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
            }
            storePrefs.edit().putInt(KEY_STORE_VERSION, STORE_VERSION).apply();
            return;
        }
        try {
            if (version < 7) {
                removeDiscontinuedBuiltinPresets();
            }
            applyActivePresetToDefaultPrefs();
        } catch (Exception e) {
            Log.e(TAG, "Preset store migration", e);
        }
        storePrefs.edit().putInt(KEY_STORE_VERSION, STORE_VERSION).apply();
    }

    private static boolean isRemovedBuiltinPresetId(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (String x : REMOVED_BUILTIN_PRESET_IDS) {
            if (x.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Store v7: drop two-button + classic built-ins from the index and delete their JSON files. */
    private void removeDiscontinuedBuiltinPresets() {
        String active = getActivePresetId();
        List<PresetRef> index = readIndex();
        List<PresetRef> next = new ArrayList<>();
        for (PresetRef r : index) {
            if (r == null || r.id == null) {
                continue;
            }
            if (isRemovedBuiltinPresetId(r.id)) {
                File f = presetFile(r.id);
                if (f.isFile() && !f.delete()) {
                    Log.w(TAG, "Could not delete discontinued preset " + r.id);
                }
                continue;
            }
            next.add(r);
        }
        saveIndex(next);
        for (String id : REMOVED_BUILTIN_PRESET_IDS) {
            File f = presetFile(id);
            if (f.isFile() && !f.delete()) {
                Log.w(TAG, "Could not delete discontinued preset file " + id);
            }
        }
        String defId = GamepadLayoutPresetConstants.DEFAULT_PRESET_ID;
        if (!presetFile(defId).isFile()) {
            try {
                GamepadLayoutPresetDocument doc = loadBundledDefaultPresetFromAssets();
                if (doc == null) {
                    doc = GamepadLayoutDocumentStore.buildDefaultFromLegacyPrefs(context);
                    GamepadLayoutPresetDocument.validateOrThrow(doc);
                }
                writeFile(defId, doc);
            } catch (Exception e) {
                Log.e(TAG, "Could not restore default preset after v7 cleanup", e);
            }
        }
        if (next.isEmpty() && presetFile(defId).isFile()) {
            next.add(new PresetRef(defId, "Default"));
            saveIndex(next);
        }
        if (active != null && isRemovedBuiltinPresetId(active)) {
            setActivePresetId(defId);
            applyActivePresetToDefaultPrefs();
        }
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
            GamepadLayoutPresetApplier.apply(context, d, true);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "apply active after migration", e);
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
                GamepadLayoutPresetApplier.apply(context, parsed, true);
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
            GamepadLayoutPresetApplier.apply(context, d, true);
            setActivePresetId(id);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Replaces the built-in default preset file with {@code assets/.../default.json}. Keeps the
     * current index display name (and writes it into {@link GamepadLayoutPresetDocument.Meta}).
     * <p>
     * Changing {@code gamepad/default.json} in the repo only affects the next app build’s assets until
     * this method overwrites on-disk {@code preset_default.json}. {@link #syncBundledPresetsFromAssets}
     * does not refresh that file. When Default is the active preset, default SharedPreferences (including
     * {@link GamepadPreferenceKeys#LAYOUT_DOCUMENT_JSON} for the canvas) are updated as well; otherwise only
     * the preset file on disk changes until the user activates Default.
     *
     * @return null on success, or an error message.
     */
    @Nullable
    public String resetDefaultPresetFromBundled() {
        GamepadLayoutPresetDocument fresh = loadBundledDefaultPresetFromAssets();
        if (fresh == null) {
            return "Could not load bundled default layout";
        }
        String displayKeep = "Default";
        List<PresetRef> idx = readIndex();
        for (PresetRef r : idx) {
            if (r != null && GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(r.id)) {
                if (r.displayName != null && !r.displayName.trim().isEmpty()) {
                    displayKeep = r.displayName.trim();
                }
                break;
            }
        }
        if (fresh.meta == null) {
            fresh.meta = new GamepadLayoutPresetDocument.Meta();
        }
        fresh.meta.id = GamepadLayoutPresetConstants.DEFAULT_PRESET_ID;
        fresh.meta.displayName = displayKeep;
        try {
            writeFile(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, fresh);
        } catch (IOException e) {
            return e.getMessage();
        }
        if (GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(getActivePresetId())) {
            try {
                GamepadLayoutPresetApplier.apply(context, fresh, true);
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        }
        storePrefs.edit()
                .putString(KEY_BUNDLED_DEFAULT_FINGERPRINT_APPLIED, BuildConfig.BUNDLED_GAMEPAD_DEFAULT_FINGERPRINT)
                .apply();
        return null;
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
        if (GamepadLayoutPresetConstants.isBundledPackPresetId(id)) {
            String slug = id.substring(GamepadLayoutPresetConstants.BUNDLED_PRESET_ID_PREFIX.length());
            addDeletedBundledSlug(slug);
        }
        String active = getActivePresetId();
        if (id.equals(active)) {
            return activateAndApply(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
        }
        return null;
    }

    /**
     * Imports JSON shipped under assets/{@link GamepadLayoutPresetConstants#BUNDLED_GAMEPAD_ASSET_DIR}
     * (recursively; subfolders are supported — slug is derived from the path under that dir).
     * Stable ids {@code preset_pack_<slug>} from relative paths; skips slugs the user deleted (until reinstall).
     * Safe to call on every launch (cheap when nothing new).
     * <p>
     * {@code default.json} at the root of that folder is reserved for the built-in
     * {@link GamepadLayoutPresetConstants#DEFAULT_PRESET_ID} layout and is intentionally skipped here so optional pack
     * presets import does not overwrite it.
     * Refreshing the on-disk default preset from a new build asset is {@link #resetDefaultPresetFromBundled()}.
     */
    public void syncBundledPresetsFromAssets() {
        String dir = GamepadLayoutPresetConstants.BUNDLED_GAMEPAD_ASSET_DIR;
        List<String> relativeJsonPaths = new ArrayList<>();
        try {
            collectBundledJsonRelativePaths(dir, "", relativeJsonPaths);
        } catch (IOException e) {
            Log.w(TAG, "list bundled gamepad assets", e);
            maybeRefreshBuiltInDefaultFromBundledFingerprint();
            return;
        }
        if (relativeJsonPaths.isEmpty()) {
            maybeRefreshBuiltInDefaultFromBundledFingerprint();
            return;
        }
        relativeJsonPaths.sort(String.CASE_INSENSITIVE_ORDER);
        Set<String> deletedSlugs = readDeletedBundledSlugs();
        List<PresetRef> index = readIndex();
        Set<String> indexIds = new HashSet<>();
        for (PresetRef r : index) {
            if (r != null && r.id != null) {
                indexIds.add(r.id);
            }
        }
        boolean indexDirty = false;
        for (String rel : relativeJsonPaths) {
            if (rel == null || rel.isEmpty()) {
                continue;
            }
            String slug = bundledSlugFromBundledAssetRelativePath(rel);
            if (slug.isEmpty()) {
                continue;
            }
            if (deletedSlugs.contains(slug)) {
                continue;
            }
            // Reserved for built-in preset_default (see loadBundledDefaultPresetFromAssets); not a pack preset.
            if ("default".equals(slug)) {
                continue;
            }
            String id = GamepadLayoutPresetConstants.BUNDLED_PRESET_ID_PREFIX + slug;
            if (presetFile(id).isFile()) {
                if (!indexIds.contains(id)) {
                    GamepadLayoutPresetDocument doc = loadDocument(id);
                    if (doc != null) {
                        index.add(new PresetRef(id, displayNameForBundled(doc, slug)));
                        indexIds.add(id);
                        indexDirty = true;
                    }
                }
                continue;
            }
            String assetPath = dir + "/" + rel;
            String err = importBundledAssetAtPath(assetPath, id, slug, index);
            if (err != null) {
                Log.w(TAG, "bundled preset " + rel + ": " + err);
                continue;
            }
            indexIds.add(id);
            indexDirty = true;
        }
        if (indexDirty) {
            saveIndex(index);
        }
        maybeRefreshBuiltInDefaultFromBundledFingerprint();
    }

    /**
     * Depth-first collect of {@code *.json} paths relative to {@code listPathRoot} (e.g. {@code bundled_gamepad}),
     * using {@code AssetManager.list}. Non-JSON entries are treated as subdirectories when {@code list} returns a
     * non-empty array.
     */
    private void collectBundledJsonRelativePaths(
            @NonNull String listPathRoot,
            @NonNull String relativePrefix,
            @NonNull List<String> out) throws IOException {
        String[] names = context.getAssets().list(listPathRoot);
        if (names == null || names.length == 0) {
            return;
        }
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            String rel = relativePrefix.isEmpty() ? name : relativePrefix + "/" + name;
            if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
                out.add(rel);
                continue;
            }
            String childListPath = listPathRoot + "/" + name;
            String[] sub = context.getAssets().list(childListPath);
            if (sub != null && sub.length > 0) {
                collectBundledJsonRelativePaths(childListPath, rel, out);
            }
        }
    }

    /**
     * Re-writes {@link GamepadLayoutPresetConstants#DEFAULT_PRESET_ID} from {@code assets/.../default.json}
     * when the file at build time (fingerprint in {@link BuildConfig}) differs from what we last applied.
     * Ensures dev installs and upgrades pick up {@code gamepad/default.json} without clearing app data.
     */
    private void maybeRefreshBuiltInDefaultFromBundledFingerprint() {
        String fp = BuildConfig.BUNDLED_GAMEPAD_DEFAULT_FINGERPRINT;
        if (fp == null) {
            return;
        }
        String applied = storePrefs.getString(KEY_BUNDLED_DEFAULT_FINGERPRINT_APPLIED, "");
        if (fp.equals(applied)) {
            return;
        }
        GamepadLayoutPresetDocument bundled = loadBundledDefaultPresetFromAssets();
        if (bundled == null) {
            Log.w(TAG, "Bundled default.json missing or invalid; cannot sync fingerprint " + fp);
            return;
        }
        String displayKeep = "Default";
        for (PresetRef r : readIndex()) {
            if (r != null && GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(r.id)) {
                if (r.displayName != null && !r.displayName.trim().isEmpty()) {
                    displayKeep = r.displayName.trim();
                }
                break;
            }
        }
        if (bundled.meta == null) {
            bundled.meta = new GamepadLayoutPresetDocument.Meta();
        }
        bundled.meta.id = GamepadLayoutPresetConstants.DEFAULT_PRESET_ID;
        bundled.meta.displayName = displayKeep;
        try {
            writeFile(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, bundled);
        } catch (IOException e) {
            Log.e(TAG, "Could not write preset_default for bundled fingerprint sync", e);
            return;
        }
        storePrefs.edit().putString(KEY_BUNDLED_DEFAULT_FINGERPRINT_APPLIED, fp).apply();
        if (GamepadLayoutPresetConstants.DEFAULT_PRESET_ID.equals(getActivePresetId())) {
            try {
                GamepadLayoutPresetApplier.apply(context, bundled, true);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "apply after bundled default fingerprint sync", e);
            }
        }
    }

    private Set<String> readDeletedBundledSlugs() {
        Set<String> raw = storePrefs.getStringSet(KEY_DELETED_BUNDLED_SLUGS, null);
        if (raw == null || raw.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(raw);
    }

    private void addDeletedBundledSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return;
        }
        Set<String> next = readDeletedBundledSlugs();
        next.add(slug);
        storePrefs.edit().putStringSet(KEY_DELETED_BUNDLED_SLUGS, next).apply();
    }

    static String bundledSlugFromAssetFilename(String assetFileName) {
        String base = assetFileName;
        if (base.toLowerCase(Locale.ROOT).endsWith(".json")) {
            base = base.substring(0, base.length() - 5);
        }
        String lower = base.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (c == '_' || c == '-') {
                sb.append('_');
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString().replaceAll("_+", "_");
        while (s.startsWith("_")) {
            s = s.substring(1);
        }
        while (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Slug for a preset file path under {@code bundled_gamepad/} (may include subdirs), e.g.
     * {@code packs/foo.json} → {@code packs_foo}. Used with {@link GamepadLayoutPresetConstants#BUNDLED_PRESET_ID_PREFIX}.
     */
    static String bundledSlugFromBundledAssetRelativePath(String relativePathUnderBundledDir) {
        if (relativePathUnderBundledDir == null) {
            return "";
        }
        String p = relativePathUnderBundledDir.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (p.endsWith(".json")) {
            p = p.substring(0, p.length() - 5);
        }
        p = p.replace('/', '_');
        return bundledSlugFromAssetFilename(p + ".json");
    }

    private static String displayNameForBundled(GamepadLayoutPresetDocument doc, String slug) {
        if (doc != null && doc.meta != null && doc.meta.displayName != null) {
            String d = doc.meta.displayName.trim();
            if (!d.isEmpty()) {
                return d;
            }
        }
        return slug.replace('_', ' ');
    }

    private static String readStreamUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) {
                bos.write(buf, 0, n);
            }
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Loads {@code assets/bundled_gamepad/default.json} (from repo {@code gamepad/default.json} at build time).
     * Used to seed the deletion-protected {@link GamepadLayoutPresetConstants#DEFAULT_PRESET_ID} layout.
     */
    @Nullable
    private GamepadLayoutPresetDocument loadBundledDefaultPresetFromAssets() {
        String path = GamepadLayoutPresetConstants.BUNDLED_GAMEPAD_ASSET_DIR + "/default.json";
        try (InputStream in = context.getAssets().open(path)) {
            String json = readStreamUtf8(in);
            GamepadLayoutPresetDocument doc = GamepadLayoutPresetDocument.parseOrNull(json);
            if (doc == null) {
                return null;
            }
            GamepadLayoutPresetDocument.validateOrThrow(doc);
            if (doc.meta == null) {
                doc.meta = new GamepadLayoutPresetDocument.Meta();
            }
            doc.meta.displayName = "Default";
            return doc;
        } catch (IOException e) {
            Log.w(TAG, "bundled default.json not found or unreadable", e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "bundled default.json invalid: " + e.getMessage());
            return null;
        }
    }

    /**
     * @return null on success, else error message for logging.
     */
    @Nullable
    private String importBundledAssetAtPath(String assetPath, String id, String slug, List<PresetRef> index) {
        try (InputStream in = context.getAssets().open(assetPath)) {
            String json = readStreamUtf8(in);
            GamepadLayoutPresetDocument parsed = GamepadLayoutPresetDocument.parseOrNull(json);
            if (parsed == null) {
                return "Invalid JSON";
            }
            GamepadLayoutPresetDocument.validateOrThrow(parsed);
            if (parsed.meta == null) {
                parsed.meta = new GamepadLayoutPresetDocument.Meta();
            }
            parsed.meta.id = id;
            String display = displayNameForBundled(parsed, slug);
            parsed.meta.displayName = display;
            writeFile(id, parsed);
            index.add(new PresetRef(id, display));
            return null;
        } catch (IOException e) {
            return e.getMessage();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
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
