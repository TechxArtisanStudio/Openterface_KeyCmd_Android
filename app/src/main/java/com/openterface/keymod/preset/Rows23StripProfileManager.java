package com.openterface.keymod.preset;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.Shortcut;
import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Persists Rows 2–3 strip profiles (slot map + shortcut definitions), active profile id, and
 * handles migration from legacy {@link StripSlotMapStore} + reserved {@code keyboard_strip} profile.
 */
public class Rows23StripProfileManager {

    private static final String TAG = "Rows23StripProfMgr";
    private static final String PREFS = "StripProfiles_v1";
    private static final String KEY_PROFILES_JSON = "profiles_json";
    private static final String KEY_ACTIVE_ID = "active_strip_profile_id";
    private static final String KEY_MIGRATED_V1 = "migration_rows23_strip_profiles_v1";

    private final Context appContext;
    private final Gson gson = new Gson();
    private final ShortcutProfileManager shortcutProfileManager;

    private List<Rows23StripProfile> profiles = new ArrayList<>();
    private String activeProfileId = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;

    public Rows23StripProfileManager(@NonNull Context context, @NonNull ShortcutProfileManager shortcutProfileManager) {
        this.appContext = context.getApplicationContext();
        this.shortcutProfileManager = shortcutProfileManager;
        load();
        migrateFromLegacyIfNeeded();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void load() {
        String json = prefs().getString(KEY_PROFILES_JSON, null);
        if (json == null || json.trim().isEmpty()) {
            profiles = new ArrayList<>();
        } else {
            Type type = new TypeToken<List<Rows23StripProfile>>() {}.getType();
            List<Rows23StripProfile> parsed = gson.fromJson(json, type);
            profiles = parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        }
        activeProfileId = prefs().getString(KEY_ACTIVE_ID, Rows23StripProfileConstants.DEFAULT_PROFILE_ID);
        normalizeLoaded();
    }

    private void normalizeLoaded() {
        if (profiles.isEmpty()) {
            return;
        }
        for (Rows23StripProfile p : profiles) {
            if (p.slotMap == null) {
                p.slotMap = new HashMap<>();
            }
            if (p.shortcuts == null) {
                p.shortcuts = new ArrayList<>();
            }
        }
    }

    private void save() {
        prefs().edit()
                .putString(KEY_PROFILES_JSON, gson.toJson(profiles))
                .putString(KEY_ACTIVE_ID, activeProfileId != null ? activeProfileId : Rows23StripProfileConstants.DEFAULT_PROFILE_ID)
                .apply();
    }

    private void migrateFromLegacyIfNeeded() {
        if (!prefs().getBoolean(KEY_MIGRATED_V1, false) && profiles.isEmpty()) {
            Rows23StripProfile def = new Rows23StripProfile();
            def.id = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
            def.name = "Default";
            def.createdAt = System.currentTimeMillis();
            def.slotMap = new HashMap<>(new StripSlotMapStore(appContext).getAll());
            shortcutProfileManager.ensureKeyboardStripLayoutProfile();
            ShortcutProfile strip = shortcutProfileManager.getProfileById(KeyboardStripPresetConstants.STRIP_PROFILE_ID);
            def.shortcuts = new ArrayList<>();
            if (strip != null) {
                for (Shortcut s : strip.getAllShortcutsFlat()) {
                    if (s != null && s.id != null) {
                        def.shortcuts.add(copyShortcut(s));
                    }
                }
            }
            profiles.add(def);
            activeProfileId = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
            prefs().edit().putBoolean(KEY_MIGRATED_V1, true).apply();
            save();
            return;
        }
        if (!prefs().getBoolean(KEY_MIGRATED_V1, false)) {
            prefs().edit().putBoolean(KEY_MIGRATED_V1, true).apply();
        }
        if (profiles.isEmpty()) {
            Rows23StripProfile def = new Rows23StripProfile();
            def.id = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
            def.name = "Default";
            def.createdAt = System.currentTimeMillis();
            def.slotMap = new HashMap<>();
            def.shortcuts = new ArrayList<>();
            profiles.add(def);
            activeProfileId = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
            save();
        }
    }

    private static Shortcut copyShortcut(Shortcut s) {
        Shortcut c = new Shortcut();
        c.id = s.id;
        c.name = s.name;
        c.label = s.label;
        c.modifiers = s.modifiers;
        c.keyCode = s.keyCode;
        c.icon = s.icon != null ? s.icon : "";
        c.displayOrder = s.displayOrder;
        return c;
    }

    @NonNull
    public List<Rows23StripProfile> getProfiles() {
        return Collections.unmodifiableList(new ArrayList<>(profiles));
    }

    @Nullable
    public Rows23StripProfile getProfileById(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Rows23StripProfile p : profiles) {
            if (p != null && id.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public Rows23StripProfile getActiveProfile() {
        Rows23StripProfile p = getProfileById(activeProfileId);
        if (p != null) {
            return p;
        }
        return getProfileById(Rows23StripProfileConstants.DEFAULT_PROFILE_ID);
    }

    @NonNull
    public String getActiveProfileId() {
        Rows23StripProfile p = getActiveProfile();
        return p != null ? p.id : Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
    }

    public void setActiveProfileId(@NonNull String id) {
        if (getProfileById(id) != null) {
            activeProfileId = id;
            save();
        }
    }

    public boolean isDefaultProfileId(@Nullable String id) {
        return Rows23StripProfileConstants.DEFAULT_PROFILE_ID.equals(id);
    }

    /**
     * Creates a new strip profile (copy of active slot map + shortcut defs, or empty if none).
     */
    @NonNull
    public Rows23StripProfile createProfile(@NonNull String name) {
        Rows23StripProfile p = new Rows23StripProfile();
        p.id = "strip_" + System.currentTimeMillis();
        p.name = name.trim().isEmpty() ? "Untitled" : name.trim();
        p.createdAt = System.currentTimeMillis();
        Rows23StripProfile src = getActiveProfile();
        if (src != null) {
            p.slotMap = new HashMap<>(src.slotMap);
            p.shortcuts = new ArrayList<>();
            for (Shortcut s : src.shortcuts) {
                if (s != null) {
                    p.shortcuts.add(copyShortcut(s));
                }
            }
        } else {
            p.slotMap = new HashMap<>();
            p.shortcuts = new ArrayList<>();
        }
        profiles.add(p);
        save();
        return p;
    }

    public void deleteProfile(@NonNull String profileId) {
        if (Rows23StripProfileConstants.DEFAULT_PROFILE_ID.equals(profileId)) {
            return;
        }
        for (int i = 0; i < profiles.size(); i++) {
            Rows23StripProfile p = profiles.get(i);
            if (p != null && profileId.equals(p.id)) {
                profiles.remove(i);
                if (profileId.equals(activeProfileId)) {
                    activeProfileId = Rows23StripProfileConstants.DEFAULT_PROFILE_ID;
                }
                save();
                return;
            }
        }
    }

    public void updateProfile(@NonNull Rows23StripProfile updated) {
        for (int i = 0; i < profiles.size(); i++) {
            Rows23StripProfile p = profiles.get(i);
            if (p != null && updated.id != null && updated.id.equals(p.id)) {
                profiles.set(i, updated);
                save();
                return;
            }
        }
    }

    @Nullable
    public Shortcut findShortcut(@NonNull String profileId, @Nullable String shortcutId) {
        if (shortcutId == null || shortcutId.isEmpty()) {
            return null;
        }
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null || p.shortcuts == null) {
            return null;
        }
        for (Shortcut s : p.shortcuts) {
            if (s != null && shortcutId.equals(s.id)) {
                return s;
            }
        }
        return null;
    }

    public boolean profileHasChordExcluding(
            @NonNull String profileId,
            int keyCode,
            int modifiers,
            @NonNull String targetOs,
            @Nullable String excludeShortcutId
    ) {
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null || p.shortcuts == null) {
            return false;
        }
        for (Shortcut s : p.shortcuts) {
            if (s == null || s.id == null) {
                continue;
            }
            if (excludeShortcutId != null && excludeShortcutId.equals(s.id)) {
                continue;
            }
            if (s.keyCode == keyCode && s.modifiers == modifiers) {
                return true;
            }
        }
        return false;
    }

    public void upsertShortcut(@NonNull String profileId, @NonNull Shortcut shortcut) {
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null) {
            return;
        }
        if (p.shortcuts == null) {
            p.shortcuts = new ArrayList<>();
        }
        boolean replaced = false;
        for (int i = 0; i < p.shortcuts.size(); i++) {
            Shortcut ex = p.shortcuts.get(i);
            if (ex != null && shortcut.id != null && shortcut.id.equals(ex.id)) {
                p.shortcuts.set(i, shortcut);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            p.shortcuts.add(shortcut);
        }
        save();
    }

    public void putSlot(@NonNull String profileId, @NonNull String slotKey, @NonNull String shortcutId) {
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null) {
            return;
        }
        if (p.slotMap == null) {
            p.slotMap = new HashMap<>();
        }
        p.slotMap.put(slotKey, shortcutId);
        save();
    }

    public void removeSlot(@NonNull String profileId, @NonNull String slotKey) {
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null || p.slotMap == null) {
            return;
        }
        p.slotMap.remove(slotKey);
        save();
    }

    /**
     * Swaps shortcut assignments for exactly two slot keys (each may be {@code *_base} or {@code *_fn}).
     * No-op if keys are equal.
     */
    public void swapSlotAssignments(
            @NonNull String profileId,
            @NonNull String slotKeyA,
            @NonNull String slotKeyB
    ) {
        if (slotKeyA.equals(slotKeyB)) {
            return;
        }
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null) {
            return;
        }
        if (p.slotMap == null) {
            p.slotMap = new HashMap<>();
        }
        String valA = p.slotMap.get(slotKeyA);
        String valB = p.slotMap.get(slotKeyB);
        putOrRemoveSlotValue(p.slotMap, slotKeyA, valB);
        putOrRemoveSlotValue(p.slotMap, slotKeyB, valA);
        save();
    }

    /**
     * Swaps shortcut assignments for Base+Fn together between two columns on the same strip row.
     * No-op if {@code colA == colB}.
     */
    public void swapStripColumnSlotAssignments(
            @NonNull String profileId,
            int pageIndex,
            int stripRow,
            int colA,
            int colB
    ) {
        if (colA == colB) {
            return;
        }
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null) {
            return;
        }
        if (p.slotMap == null) {
            p.slotMap = new HashMap<>();
        }
        swapColumnAssignmentsInMap(p.slotMap, pageIndex, stripRow, colA, colB);
        save();
    }

    /**
     * Mutates {@code slotMap} in place (used by {@link #swapStripColumnSlotAssignments}; package-visible for unit tests).
     */
    static void swapColumnAssignmentsInMap(
            @NonNull Map<String, String> slotMap,
            int pageIndex,
            int stripRow,
            int colA,
            int colB
    ) {
        if (colA == colB) {
            return;
        }
        String baseA = StripSlotMapStore.slotKey(pageIndex, stripRow, colA, false);
        String fnA = StripSlotMapStore.slotKey(pageIndex, stripRow, colA, true);
        String baseB = StripSlotMapStore.slotKey(pageIndex, stripRow, colB, false);
        String fnB = StripSlotMapStore.slotKey(pageIndex, stripRow, colB, true);
        String vBaseA = slotMap.get(baseA);
        String vFnA = slotMap.get(fnA);
        String vBaseB = slotMap.get(baseB);
        String vFnB = slotMap.get(fnB);
        putOrRemoveSlotValue(slotMap, baseA, vBaseB);
        putOrRemoveSlotValue(slotMap, fnA, vFnB);
        putOrRemoveSlotValue(slotMap, baseB, vBaseA);
        putOrRemoveSlotValue(slotMap, fnB, vFnA);
    }

    private static void putOrRemoveSlotValue(
            @NonNull Map<String, String> slotMap,
            @NonNull String slotKey,
            @Nullable String shortcutId
    ) {
        if (shortcutId == null || shortcutId.trim().isEmpty()) {
            slotMap.remove(slotKey);
        } else {
            slotMap.put(slotKey, shortcutId.trim());
        }
    }

    /**
     * Merges document profile into target (by id if present, else by name, else overwrite targetId).
     */
    @Nullable
    public String importDocumentIntoProfile(@NonNull String targetProfileId, @NonNull String json) {
        Rows23StripProfileDocument doc = Rows23StripProfileDocument.parseOrNull(json);
        if (doc == null) {
            return "Invalid JSON";
        }
        try {
            Rows23StripProfileDocument.validateOrThrow(doc);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        Rows23StripProfile incoming = doc.profile;
        Rows23StripProfile target = getProfileById(targetProfileId);
        if (target == null) {
            return "Unknown strip profile";
        }
        if (incoming.slotMap != null) {
            if (target.slotMap == null) {
                target.slotMap = new HashMap<>();
            }
            target.slotMap.putAll(incoming.slotMap);
        }
        if (incoming.shortcuts != null) {
            if (target.shortcuts == null) {
                target.shortcuts = new ArrayList<>();
            }
            for (Shortcut s : incoming.shortcuts) {
                if (s == null || s.id == null) {
                    continue;
                }
                upsertShortcutMergeList(target, s);
            }
        }
        updateProfile(target);
        return null;
    }

    private void upsertShortcutMergeList(@NonNull Rows23StripProfile target, @NonNull Shortcut incoming) {
        boolean replaced = false;
        for (int i = 0; i < target.shortcuts.size(); i++) {
            Shortcut ex = target.shortcuts.get(i);
            if (ex != null && incoming.id.equals(ex.id)) {
                target.shortcuts.set(i, copyShortcut(incoming));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            target.shortcuts.add(copyShortcut(incoming));
        }
    }

    @Nullable
    public String exportProfileToJson(@NonNull String profileId) {
        Rows23StripProfile p = getProfileById(profileId);
        if (p == null) {
            return null;
        }
        Rows23StripProfileDocument doc = new Rows23StripProfileDocument();
        doc.format = Rows23StripProfileConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = Rows23StripProfileConstants.SCHEMA_VERSION;
        doc.meta = new Rows23StripProfileDocument.Meta();
        doc.meta.displayName = p.name != null ? p.name : p.id;
        doc.meta.description = "";
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        doc.meta.exportedAt = iso.format(new Date());
        doc.meta.sourceAppVersion = BuildConfig.VERSION_NAME;
        Rows23StripProfile out = new Rows23StripProfile();
        out.id = p.id;
        out.name = p.name;
        out.createdAt = p.createdAt;
        out.slotMap = p.slotMap != null ? new HashMap<>(p.slotMap) : new HashMap<>();
        out.shortcuts = new ArrayList<>();
        if (p.shortcuts != null) {
            for (Shortcut s : p.shortcuts) {
                if (s != null) {
                    out.shortcuts.add(copyShortcut(s));
                }
            }
        }
        doc.profile = out;
        com.google.gson.GsonBuilder gb = new com.google.gson.GsonBuilder();
        return gb.setPrettyPrinting().create().toJson(doc);
    }

    /** Slot map for active profile (empty map if none). */
    @NonNull
    public Map<String, String> getActiveSlotMap() {
        Rows23StripProfile p = getActiveProfile();
        if (p == null || p.slotMap == null) {
            return new HashMap<>();
        }
        return new HashMap<>(p.slotMap);
    }

    /** Shortcut definitions for active profile (for runtime lookup). */
    @NonNull
    public List<Shortcut> getActiveShortcuts() {
        Rows23StripProfile p = getActiveProfile();
        if (p == null || p.shortcuts == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(p.shortcuts);
    }

    @Nullable
    public Shortcut resolveActiveSlotShortcut(@NonNull String slotKey) {
        Rows23StripProfile p = getActiveProfile();
        if (p == null || p.slotMap == null) {
            return null;
        }
        String sid = p.slotMap.get(slotKey);
        if (sid == null || sid.isEmpty()) {
            return null;
        }
        return findShortcut(p.id, sid);
    }

    /** Count of slot entries for UI (edition dialog, etc.). */
    public int getActiveOverrideSlotCount() {
        return getActiveSlotMap().size();
    }
}
