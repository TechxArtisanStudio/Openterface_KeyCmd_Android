package com.openterface.keymod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openterface.keymod.util.KeyParser;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shortcut Profile Manager - Manages app-specific shortcut profiles
 * Phase 3: Shortcut Hub
 */
public class ShortcutProfileManager {

    private static final String TAG = "ShortcutProfileManager";
    private static final String PREFS_NAME = "ShortcutProfiles_v2";
    private static final String KEY_PROFILES = "profiles_list";
    private static final String KEY_ACTIVE_PROFILE = "active_profile_id";
    private static final String MY_SHORTCUTS_KEY_PREFIX = "MyShortcuts_";
    /** One-time: move Default flat shortcuts into a category; seed empty My Shortcuts lists. */
    private static final String KEY_MIGRATION_MY_STRIP_V1 = "migration_my_strip_favorites_v1";
    /** One-time: append common shortcuts (New/Open/Print/Close/Replace) to Default's General category. */
    private static final String KEY_MIGRATION_DEFAULT_GENERAL_V2 = "migration_default_general_v2";
    /** One-time: append the same 5 IDs to existing users' Default Favorites list. */
    private static final String KEY_MIGRATION_DEFAULT_FAVORITES_V2 = "migration_default_favorites_v2";

    // ─── HID Keyboard Usage IDs (USB HID Specification) ───────────────────────
    private static final int KEY_A=4,KEY_B=5,KEY_C=6,KEY_D=7,KEY_E=8,KEY_F=9;
    private static final int KEY_G=10,KEY_H=11,KEY_I=12,KEY_J=13,KEY_K=14,KEY_L=15;
    private static final int KEY_M=16,KEY_N=17,KEY_O=18,KEY_P=19,KEY_Q=20,KEY_R=21;
    private static final int KEY_S=22,KEY_T=23,KEY_U=24,KEY_V=25,KEY_W=26,KEY_X=27;
    private static final int KEY_Y=28,KEY_Z=29;
    private static final int KEY_1=30,KEY_2=31,KEY_3=32,KEY_4=33,KEY_5=34;
    private static final int KEY_6=35,KEY_7=36,KEY_8=37,KEY_9=38,KEY_0=39;
    private static final int KEY_ENTER=40,KEY_ESC=41,KEY_BACKSPACE=42,KEY_TAB=43;
    private static final int KEY_SPACE=44,KEY_MINUS=45,KEY_EQUALS=46;
    private static final int KEY_LBRACKET=47,KEY_RBRACKET=48,KEY_BACKSLASH=49;
    private static final int KEY_SEMICOLON=51,KEY_QUOTE=52,KEY_GRAVE=53;
    private static final int KEY_COMMA=54,KEY_PERIOD=55,KEY_SLASH=56;
    private static final int KEY_F1=58,KEY_F2=59,KEY_F3=60,KEY_F4=61,KEY_F5=62,KEY_F6=63;
    private static final int KEY_F7=64,KEY_F8=65,KEY_F9=66,KEY_F10=67,KEY_F11=68,KEY_F12=69;
    private static final int KEY_HOME=74,KEY_PAGEUP=75,KEY_DELETE=76,KEY_END=77,KEY_PAGEDOWN=78;
    private static final int KEY_RIGHT=79,KEY_LEFT=80,KEY_DOWN=81,KEY_UP=82;
    private static final int NUMPAD_SLASH=84,NUMPAD_ASTERISK=85,NUMPAD_MINUS=86,NUMPAD_PLUS=87;
    private static final int NUMPAD_ENTER=88,NUMPAD_1=89,NUMPAD_2=90,NUMPAD_3=91,NUMPAD_4=92;
    private static final int NUMPAD_5=93,NUMPAD_6=94,NUMPAD_7=95,NUMPAD_8=96,NUMPAD_9=97;
    private static final int NUMPAD_0=98,NUMPAD_DOT=99;
    // HID Modifier bitmasks
    private static final int MOD_NONE=0,MOD_CTRL=1,MOD_SHIFT=2,MOD_ALT=4;
    private static final int MOD_CTRL_SHIFT=3,MOD_CTRL_ALT=5,MOD_SHIFT_ALT=6;
    // ──────────────────────────────────────────────────────────────────────────

    private final SharedPreferences prefs;
    private final Gson gson;
    private List<ShortcutProfile> profiles;
    private String activeProfileId;
    private ProfileChangeListener listener;

    public ShortcutProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        profiles = loadProfiles();
        activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE, "default");
        
        // Create default profile if none exist
        if (profiles.isEmpty()) {
            createDefaultProfiles();
        }
        if (migrateDefaultProfileIfNeeded()) {
            saveProfiles();
        }
        if (migrateDefaultFlatToGeneralCategory()) {
            saveProfiles();
        }
        seedEmptyMyShortcutsIfNeeded();
        if (migrateDefaultGeneralAddV2()) {
            saveProfiles();
        }
        migrateDefaultFavoritesAddV2();
    }

    /**
     * Load profiles from SharedPreferences
     */
    private List<ShortcutProfile> loadProfiles() {
        String json = prefs.getString(KEY_PROFILES, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<ShortcutProfile>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Save profiles to SharedPreferences
     */
    private void saveProfiles() {
        String json = gson.toJson(profiles);
        prefs.edit().putString(KEY_PROFILES, json).apply();
        Log.d(TAG, "Saved " + profiles.size() + " profiles");
    }

    /**
     * Create default profiles (v2 – correct HID codes, categories matching iOS)
     */
    private void createDefaultProfiles() {
        profiles.add(createDefaultProfile());
        profiles.add(createBlenderProfile());
        profiles.add(createKiCADProfile());
        profiles.add(createNomadProfile());
        profiles.add(createFusion360Profile());
        profiles.add(createPhotoshopProfile());
        profiles.add(createVSCodeProfile());
        saveProfiles();
        Log.d(TAG, "Created " + profiles.size() + " default profiles");
    }

    private ShortcutProfile createDefaultProfile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "default";
        p.name = "Default";
        p.description = "Common shortcuts for any app";
        p.icon = "ic_default";
        p.createdAt = System.currentTimeMillis();
        p.shortcuts.add(new Shortcut("default_select_all", "Select All", "Ctrl+A", MOD_CTRL, KEY_A, "select_all_24", 1));
        p.shortcuts.add(new Shortcut("default_copy",       "Copy",       "Ctrl+C", MOD_CTRL, KEY_C, "content_copy_24", 2));
        p.shortcuts.add(new Shortcut("default_cut",        "Cut",        "Ctrl+X", MOD_CTRL, KEY_X, "content_cut_24", 3));
        p.shortcuts.add(new Shortcut("default_paste",      "Paste",      "Ctrl+V", MOD_CTRL, KEY_V, "content_paste_24", 4));
        p.shortcuts.add(new Shortcut("default_save",       "Save",       "Ctrl+S", MOD_CTRL, KEY_S, "save_24", 5));
        p.shortcuts.add(new Shortcut("default_undo",       "Undo",       "Ctrl+Z", MOD_CTRL, KEY_Z, "undo_24", 6));
        p.shortcuts.add(new Shortcut("default_redo",       "Redo",       "Ctrl+Y", MOD_CTRL, KEY_Y, "redo_24", 8));
        p.shortcuts.add(new Shortcut("default_find",       "Find",       "Ctrl+F", MOD_CTRL, KEY_F, "search_24", 7));
        p.shortcuts.add(new Shortcut("default_new",        "New",        "Ctrl+N", MOD_CTRL, KEY_N, "add_24", 9));
        p.shortcuts.add(new Shortcut("default_open",       "Open",       "Ctrl+O", MOD_CTRL, KEY_O, "folder_open_24", 10));
        p.shortcuts.add(new Shortcut("default_print",      "Print",      "Ctrl+P", MOD_CTRL, KEY_P, "print_24", 11));
        p.shortcuts.add(new Shortcut("default_close",      "Close",      "Ctrl+W", MOD_CTRL, KEY_W, "close_24", 12));
        p.shortcuts.add(new Shortcut("default_replace",    "Replace",    "Ctrl+H", MOD_CTRL, KEY_H, "find_replace_24", 13));
        return p;
    }

    private boolean migrateDefaultProfileIfNeeded() {
        ShortcutProfile profile = getProfileById("default");
        if (profile == null) {
            return false;
        }

        boolean changed = false;
        List<Shortcut> source = profile.shortcuts != null ? profile.shortcuts : new ArrayList<>();
        Map<String, Shortcut> bySignature = new HashMap<>();
        for (Shortcut shortcut : source) {
            if (shortcut == null) continue;
            if (shortcut.icon == null || shortcut.icon.trim().isEmpty()) {
                shortcut.icon = inferDefaultIcon(shortcut);
                changed = true;
            }
            if (shortcut.displayOrder <= 0) {
                shortcut.displayOrder = Integer.MAX_VALUE;
                changed = true;
            }
            bySignature.put(signature(shortcut.modifiers, shortcut.keyCode), shortcut);
        }

        List<Shortcut> ordered = new ArrayList<>();
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_select_all", "Select All", "Ctrl+A", MOD_CTRL, KEY_A, "select_all_24", 1);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_copy", "Copy", "Ctrl+C", MOD_CTRL, KEY_C, "content_copy_24", 2);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_cut", "Cut", "Ctrl+X", MOD_CTRL, KEY_X, "content_cut_24", 3);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_paste", "Paste", "Ctrl+V", MOD_CTRL, KEY_V, "content_paste_24", 4);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_save", "Save", "Ctrl+S", MOD_CTRL, KEY_S, "save_24", 5);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_undo", "Undo", "Ctrl+Z", MOD_CTRL, KEY_Z, "undo_24", 6);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_redo", "Redo", "Ctrl+Y", MOD_CTRL, KEY_Y, "redo_24", 8);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_find", "Find", "Ctrl+F", MOD_CTRL, KEY_F, "search_24", 7);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_new", "New", "Ctrl+N", MOD_CTRL, KEY_N, "add_24", 9);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_open", "Open", "Ctrl+O", MOD_CTRL, KEY_O, "folder_open_24", 10);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_print", "Print", "Ctrl+P", MOD_CTRL, KEY_P, "print_24", 11);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_close", "Close", "Ctrl+W", MOD_CTRL, KEY_W, "close_24", 12);
        changed |= addOrCreateDefaultShortcut(ordered, bySignature, "default_replace", "Replace", "Ctrl+H", MOD_CTRL, KEY_H, "find_replace_24", 13);

        if (source.size() != ordered.size()) {
            changed = true;
        }
        profile.shortcuts = ordered;
        return changed;
    }

    /**
     * Moves Default profile shortcuts from the flat list into a "General" category so every profile
     * has at least one section in Shortcut Hub.
     */
    private boolean migrateDefaultFlatToGeneralCategory() {
        ShortcutProfile def = getProfileById("default");
        if (def == null) {
            return false;
        }
        boolean hasCategories = def.categories != null && !def.categories.isEmpty();
        if (hasCategories) {
            return false;
        }
        List<Shortcut> flat = def.shortcuts != null ? def.shortcuts : new ArrayList<>();
        if (flat.isEmpty()) {
            return false;
        }
        ShortcutCategory general = new ShortcutCategory("general", "General");
        general.shortcuts.addAll(flat);
        if (def.categories == null) {
            def.categories = new ArrayList<>();
        }
        def.categories.add(general);
        def.shortcuts = new ArrayList<>();
        return true;
    }

    private void seedEmptyMyShortcutsIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATION_MY_STRIP_V1, false)) {
            return;
        }
        for (ShortcutProfile p : profiles) {
            if (p == null || p.id == null) {
                continue;
            }
            List<Shortcut> my = getMyShortcuts(p.id);
            if (my != null && !my.isEmpty()) {
                continue;
            }
            List<Shortcut> seeded = buildSeededMyShortcuts(p);
            if (!seeded.isEmpty()) {
                renumberDisplayOrder(seeded);
                updateMyShortcuts(p.id, seeded);
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATION_MY_STRIP_V1, true).apply();
    }

    /**
     * v2: Adds the 5 universally common shortcuts (New/Open/Print/Close/Replace) to the Default
     * profile's General category for users whose profile was migrated to categories before these
     * IDs existed. Idempotent: skipped after first successful run, and individual entries are only
     * appended when no shortcut with the same (modifiers, keyCode) signature exists.
     *
     * @return true if the in-memory profile was modified and the caller should persist.
     */
    private boolean migrateDefaultGeneralAddV2() {
        if (prefs.getBoolean(KEY_MIGRATION_DEFAULT_GENERAL_V2, false)) {
            return false;
        }
        ShortcutProfile def = getProfileById("default");
        if (def == null) {
            prefs.edit().putBoolean(KEY_MIGRATION_DEFAULT_GENERAL_V2, true).apply();
            return false;
        }
        ShortcutCategory general = findOrCreateGeneralCategory(def);
        Map<String, Shortcut> bySig = new HashMap<>();
        for (Shortcut s : def.getAllShortcutsFlat()) {
            if (s != null) {
                bySig.put(signature(s.modifiers, s.keyCode), s);
            }
        }
        boolean changed = false;
        int order = nextDisplayOrderAcrossProfile(def);
        Object[][] additions = new Object[][]{
                {"default_new",     "New",     "Ctrl+N", MOD_CTRL, KEY_N, "add_24"},
                {"default_open",    "Open",    "Ctrl+O", MOD_CTRL, KEY_O, "folder_open_24"},
                {"default_print",   "Print",   "Ctrl+P", MOD_CTRL, KEY_P, "print_24"},
                {"default_close",   "Close",   "Ctrl+W", MOD_CTRL, KEY_W, "close_24"},
                {"default_replace", "Replace", "Ctrl+H", MOD_CTRL, KEY_H, "find_replace_24"},
        };
        for (Object[] row : additions) {
            int mods = (int) row[3];
            int kc = (int) row[4];
            if (bySig.containsKey(signature(mods, kc))) {
                continue;
            }
            Shortcut s = new Shortcut(
                    (String) row[0], (String) row[1], (String) row[2],
                    mods, kc, (String) row[5], order++);
            general.shortcuts.add(s);
            bySig.put(signature(mods, kc), s);
            changed = true;
        }
        prefs.edit().putBoolean(KEY_MIGRATION_DEFAULT_GENERAL_V2, true).apply();
        return changed;
    }

    /**
     * v2: Appends the 5 new Default shortcuts to the user's existing Favorites list (after
     * {@link #migrateDefaultGeneralAddV2()} ensures they exist in General). Skips IDs already
     * present so reorders/removals from the user are preserved. Runs once per device.
     */
    private void migrateDefaultFavoritesAddV2() {
        if (prefs.getBoolean(KEY_MIGRATION_DEFAULT_FAVORITES_V2, false)) {
            return;
        }
        ShortcutProfile def = getProfileById("default");
        if (def == null) {
            prefs.edit().putBoolean(KEY_MIGRATION_DEFAULT_FAVORITES_V2, true).apply();
            return;
        }
        Map<String, Shortcut> byId = new HashMap<>();
        for (Shortcut s : def.getAllShortcutsFlat()) {
            if (s != null && s.id != null) {
                byId.put(s.id, s);
            }
        }
        List<Shortcut> my = new ArrayList<>(getMyShortcuts("default"));
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (Shortcut s : my) {
            if (s != null && s.id != null) {
                existingIds.add(s.id);
            }
        }
        String[] newIds = {
                "default_new", "default_open", "default_print", "default_close", "default_replace"
        };
        boolean changed = false;
        for (String id : newIds) {
            if (existingIds.contains(id)) {
                continue;
            }
            Shortcut src = byId.get(id);
            if (src == null) {
                continue;
            }
            Shortcut copy = cloneShortcut(src);
            if (copy != null) {
                my.add(copy);
                changed = true;
            }
        }
        if (changed) {
            renumberDisplayOrder(my);
            updateMyShortcuts("default", my);
        }
        prefs.edit().putBoolean(KEY_MIGRATION_DEFAULT_FAVORITES_V2, true).apply();
    }

    private void renumberDisplayOrder(List<Shortcut> list) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Shortcut s = list.get(i);
            if (s != null) {
                s.displayOrder = i + 1;
            }
        }
    }

    private List<Shortcut> buildSeededMyShortcuts(ShortcutProfile p) {
        String[] ids = myFavoritesSeedIdsForProfile(p.id);
        List<Shortcut> flat = p.getAllShortcutsFlat();
        Map<String, Shortcut> byId = new HashMap<>();
        for (Shortcut s : flat) {
            if (s != null && s.id != null) {
                byId.put(s.id, s);
            }
        }
        List<Shortcut> out = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Shortcut src = byId.get(id);
                if (src != null) {
                    out.add(cloneShortcut(src));
                }
            }
        }
        if (out.isEmpty()) {
            int n = Math.min(7, flat.size());
            for (int i = 0; i < n; i++) {
                out.add(cloneShortcut(flat.get(i)));
            }
        }
        return out;
    }

    private Shortcut cloneShortcut(Shortcut src) {
        if (src == null) {
            return null;
        }
        return gson.fromJson(gson.toJson(src), Shortcut.class);
    }

    /**
     * Appends a deep copy of {@code source} to {@code workingMy} if no entry with the same non-null
     * {@link Shortcut#id} exists. Used by the reorder bottom sheet staging list.
     *
     * @return true if appended, false if duplicate (same id) or invalid input
     */
    public boolean appendCloneIfAbsent(List<Shortcut> workingMy, Shortcut source) {
        if (workingMy == null || source == null) {
            return false;
        }
        if (source.id != null && !source.id.isEmpty()) {
            for (Shortcut s : workingMy) {
                if (s != null && source.id.equals(s.id)) {
                    return false;
                }
            }
        }
        Shortcut copy = cloneShortcut(source);
        if (copy == null) {
            return false;
        }
        workingMy.add(copy);
        return true;
    }

    private static String[] myFavoritesSeedIdsForProfile(String profileId) {
        if (profileId == null) {
            return new String[0];
        }
        switch (profileId) {
            case "default":
                return new String[]{
                        "default_select_all", "default_copy", "default_cut", "default_paste",
                        "default_save", "default_undo", "default_find", "default_redo",
                        "default_new", "default_open", "default_print", "default_close", "default_replace"
                };
            case "blender":
                return new String[]{"b-t-1", "b-t-2", "b-t-3", "b-s-1", "b-v-1", "b-mo-1", "b-to-1"};
            case "kicad":
                return new String[]{"k-vz-1", "k-de-1", "k-c-1", "k-pd-1", "k-f-1", "k-f-4", "k-l-1"};
            case "nomad":
                return new String[]{"n-t-1", "n-t-2", "n-tr-1", "n-v-7", "n-e-1", "n-e-3", "n-b-1"};
            case "fusion360":
                return new String[]{"f-fi-3", "f-ge-1", "f-ge-3", "f-sk-1", "f-sk-2", "f-mo-1", "f-nav-1"};
            case "photoshop":
                return new String[]{"ps-t-1", "ps-t-2", "ps-e-1", "ps-e-3", "ps-e-5", "ps-e-6", "ps-v-3"};
            case "vscode":
                return new String[]{"v-nav-1", "v-c-1", "v-e-1", "v-e-5", "v-e-6", "v-nav-7", "v-v-4"};
            default:
                return new String[0];
        }
    }

    private void sortShortcutsListForStrip(List<Shortcut> sorted) {
        if (sorted == null || sorted.isEmpty()) {
            return;
        }
        final Map<String, Integer> existingOrder = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            Shortcut shortcut = sorted.get(i);
            existingOrder.put(shortcut.id != null ? shortcut.id : ("index_" + i), i);
        }
        Collections.sort(sorted, Comparator
                .comparingInt((Shortcut s) -> s.displayOrder > 0 ? s.displayOrder : Integer.MAX_VALUE)
                .thenComparingInt(s -> existingOrder.getOrDefault(s.id != null ? s.id : "", Integer.MAX_VALUE)));
    }

    private boolean addOrCreateDefaultShortcut(
            List<Shortcut> target,
            Map<String, Shortcut> bySignature,
            String id,
            String name,
            String label,
            int modifiers,
            int keyCode,
            String icon,
            int displayOrder
    ) {
        Shortcut shortcut = bySignature.get(signature(modifiers, keyCode));
        boolean changed = false;
        if (shortcut == null) {
            shortcut = new Shortcut(id, name, label, modifiers, keyCode, icon, displayOrder);
            changed = true;
        } else {
            if (shortcut.id == null || shortcut.id.trim().isEmpty()) {
                shortcut.id = id;
                changed = true;
            }
            if (!name.equals(shortcut.name)) {
                shortcut.name = name;
                changed = true;
            }
            if (!label.equals(shortcut.label)) {
                shortcut.label = label;
                changed = true;
            }
            if (shortcut.icon == null || shortcut.icon.trim().isEmpty() || !icon.equals(shortcut.icon)) {
                shortcut.icon = icon;
                changed = true;
            }
            if (shortcut.displayOrder != displayOrder) {
                shortcut.displayOrder = displayOrder;
                changed = true;
            }
        }
        target.add(shortcut);
        return changed;
    }

    private String signature(int modifiers, int keyCode) {
        return modifiers + ":" + keyCode;
    }

    private String inferDefaultIcon(Shortcut shortcut) {
        if (shortcut == null || shortcut.keyCode <= 0) return "";
        if (shortcut.keyCode == KEY_A && shortcut.modifiers == MOD_CTRL) return "select_all_24";
        if (shortcut.keyCode == KEY_C && shortcut.modifiers == MOD_CTRL) return "content_copy_24";
        if (shortcut.keyCode == KEY_X && shortcut.modifiers == MOD_CTRL) return "content_cut_24";
        if (shortcut.keyCode == KEY_V && shortcut.modifiers == MOD_CTRL) return "content_paste_24";
        if (shortcut.keyCode == KEY_TAB && shortcut.modifiers == MOD_NONE) return "keyboard_tab_24";
        if (shortcut.keyCode == KEY_S && shortcut.modifiers == MOD_CTRL) return "save_24";
        if (shortcut.keyCode == KEY_Z && shortcut.modifiers == MOD_CTRL) return "undo_24";
        if (shortcut.keyCode == KEY_Y && shortcut.modifiers == MOD_CTRL) return "redo_24";
        if (shortcut.keyCode == KEY_F && shortcut.modifiers == MOD_CTRL) return "search_24";
        return "";
    }

    private ShortcutProfile createBlenderProfile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "blender"; p.name = "Blender 3D";
        p.description = "Shortcuts for Blender 3D modeling";
        p.icon = "ic_blender"; p.createdAt = System.currentTimeMillis();

        ShortcutCategory transform = new ShortcutCategory("transform", "Transform");
        transform.shortcuts.add(new Shortcut("b-t-1", "Move",    "G", MOD_NONE, KEY_G));
        transform.shortcuts.add(new Shortcut("b-t-2", "Rotate",  "R", MOD_NONE, KEY_R));
        transform.shortcuts.add(new Shortcut("b-t-3", "Scale",   "S", MOD_NONE, KEY_S));
        transform.shortcuts.add(new Shortcut("b-t-4", "Extrude", "E", MOD_NONE, KEY_E));
        p.categories.add(transform);

        ShortcutCategory selection = new ShortcutCategory("selection", "Selection");
        selection.shortcuts.add(new Shortcut("b-s-1", "Select All",       "A",      MOD_NONE,       KEY_A));
        selection.shortcuts.add(new Shortcut("b-s-2", "Select Linked",    "Alt+L",  MOD_ALT,        KEY_L));
        selection.shortcuts.add(new Shortcut("b-s-3", "Linked All",       "Ctrl+L", MOD_CTRL,       KEY_L));
        selection.shortcuts.add(new Shortcut("b-s-4", "Invert Sel.",      "Ctrl+I", MOD_CTRL,       KEY_I));
        selection.shortcuts.add(new Shortcut("b-s-5", "Circle Select",    "C",      MOD_NONE,       KEY_C));
        selection.shortcuts.add(new Shortcut("b-s-6", "Grow Selection",   "Ctrl++", MOD_CTRL_SHIFT, KEY_EQUALS));
        selection.shortcuts.add(new Shortcut("b-s-7", "Shrink Selection", "Ctrl+-", MOD_CTRL,       KEY_MINUS));
        p.categories.add(selection);

        ShortcutCategory view = new ShortcutCategory("view", "View");
        view.shortcuts.add(new Shortcut("b-v-1", "Top",          "Numpad 7", MOD_NONE, NUMPAD_7));
        view.shortcuts.add(new Shortcut("b-v-2", "Front",        "Numpad 1", MOD_NONE, NUMPAD_1));
        view.shortcuts.add(new Shortcut("b-v-3", "Right",        "Numpad 3", MOD_NONE, NUMPAD_3));
        view.shortcuts.add(new Shortcut("b-v-4", "Opposite",     "Numpad 9", MOD_NONE, NUMPAD_9));
        view.shortcuts.add(new Shortcut("b-v-5", "Camera",       "Numpad 0", MOD_NONE, NUMPAD_0));
        view.shortcuts.add(new Shortcut("b-v-6", "Toggle X-Ray", "Alt+Z",    MOD_ALT,  KEY_Z));
        p.categories.add(view);

        ShortcutCategory mesh = new ShortcutCategory("mesh", "Mesh");
        mesh.shortcuts.add(new Shortcut("b-m-1",  "Loop Cut",       "Ctrl+R",  MOD_CTRL,  KEY_R));
        mesh.shortcuts.add(new Shortcut("b-m-2",  "Bevel",          "Ctrl+B",  MOD_CTRL,  KEY_B));
        mesh.shortcuts.add(new Shortcut("b-m-3",  "Merge",          "M",       MOD_NONE,  KEY_M));
        mesh.shortcuts.add(new Shortcut("b-m-4",  "Create Face",    "F",       MOD_NONE,  KEY_F));
        mesh.shortcuts.add(new Shortcut("b-m-5",  "Inset Faces",    "I",       MOD_NONE,  KEY_I));
        mesh.shortcuts.add(new Shortcut("b-m-6",  "Knife",          "K",       MOD_NONE,  KEY_K));
        mesh.shortcuts.add(new Shortcut("b-m-7",  "Rip",            "V",       MOD_NONE,  KEY_V));
        mesh.shortcuts.add(new Shortcut("b-m-8",  "Bend",           "Shift+W", MOD_SHIFT, KEY_W));
        mesh.shortcuts.add(new Shortcut("b-m-9",  "Recalc Normals", "Shift+N", MOD_SHIFT, KEY_N));
        mesh.shortcuts.add(new Shortcut("b-m-10", "Delete",         "X",       MOD_NONE,  KEY_X));
        p.categories.add(mesh);

        ShortcutCategory mode = new ShortcutCategory("mode", "Mode");
        mode.shortcuts.add(new Shortcut("b-mo-1", "Edit \u2194 Object", "Tab", MOD_NONE, KEY_TAB));
        p.categories.add(mode);

        ShortcutCategory component = new ShortcutCategory("component", "Component");
        component.shortcuts.add(new Shortcut("b-c-1", "Vertex", "1", MOD_NONE, KEY_1));
        component.shortcuts.add(new Shortcut("b-c-2", "Edge",   "2", MOD_NONE, KEY_2));
        component.shortcuts.add(new Shortcut("b-c-3", "Face",   "3", MOD_NONE, KEY_3));
        p.categories.add(component);

        ShortcutCategory object = new ShortcutCategory("object", "Object");
        object.shortcuts.add(new Shortcut("b-o-1", "Duplicate", "Alt+D",  MOD_ALT,  KEY_D));
        object.shortcuts.add(new Shortcut("b-o-2", "Separate",  "P",      MOD_NONE, KEY_P));
        object.shortcuts.add(new Shortcut("b-o-3", "Join",      "Ctrl+J", MOD_CTRL, KEY_J));
        p.categories.add(object);

        ShortcutCategory tools = new ShortcutCategory("tools", "Tools");
        tools.shortcuts.add(new Shortcut("b-to-1", "Search",      "F3",      MOD_NONE,  KEY_F3));
        tools.shortcuts.add(new Shortcut("b-to-2", "Repeat Last", "Shift+R", MOD_SHIFT, KEY_R));
        p.categories.add(tools);

        ShortcutCategory ui = new ShortcutCategory("ui", "UI");
        ui.shortcuts.add(new Shortcut("b-ui-1", "Toolbar", "T", MOD_NONE, KEY_T));
        ui.shortcuts.add(new Shortcut("b-ui-2", "Sidebar", "N", MOD_NONE, KEY_N));
        p.categories.add(ui);

        ShortcutCategory navigation = new ShortcutCategory("navigation", "Navigation");
        navigation.shortcuts.add(new Shortcut("b-n-1", "Zoom In",      "+",       MOD_SHIFT, KEY_EQUALS));
        navigation.shortcuts.add(new Shortcut("b-n-2", "Zoom Out",     "-",       MOD_NONE,  KEY_MINUS));
        navigation.shortcuts.add(new Shortcut("b-n-3", "Reset Cursor", "Shift+C", MOD_SHIFT, KEY_C));
        p.categories.add(navigation);

        ShortcutCategory shading = new ShortcutCategory("shading", "Shading");
        shading.shortcuts.add(new Shortcut("b-sh-1", "Shading Pie",  "Z",           MOD_NONE,       KEY_Z));
        shading.shortcuts.add(new Shortcut("b-sh-2", "UV Mapping",   "U",           MOD_NONE,       KEY_U));
        shading.shortcuts.add(new Shortcut("b-sh-3", "Connect Nodes","F",           MOD_NONE,       KEY_F));
        shading.shortcuts.add(new Shortcut("b-sh-4", "Tex Setup",    "Ctrl+T",      MOD_CTRL,       KEY_T));
        shading.shortcuts.add(new Shortcut("b-sh-5", "Princ. Setup", "Ctrl+Sft+T",  MOD_CTRL_SHIFT, KEY_T));
        p.categories.add(shading);

        ShortcutCategory animation = new ShortcutCategory("animation", "Animation");
        animation.shortcuts.add(new Shortcut("b-a-1", "Add Keyframe", "I",          MOD_NONE,     KEY_I));
        animation.shortcuts.add(new Shortcut("b-a-2", "Set Camera",   "Ctrl+Alt+0", MOD_CTRL_ALT, KEY_0));
        p.categories.add(animation);
        return p;
    }

    private ShortcutProfile createKiCADProfile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "kicad"; p.name = "KiCAD";
        p.description = "Shortcuts for KiCAD PCB design";
        p.icon = "ic_kicad"; p.createdAt = System.currentTimeMillis();

        ShortcutCategory viewZoom = new ShortcutCategory("view-zoom", "View & Zoom");
        viewZoom.shortcuts.add(new Shortcut("k-vz-1", "Zoom In",       "F1",     MOD_NONE, KEY_F1));
        viewZoom.shortcuts.add(new Shortcut("k-vz-2", "Zoom Out",      "F2",     MOD_NONE, KEY_F2));
        viewZoom.shortcuts.add(new Shortcut("k-vz-3", "Redraw",        "F3",     MOD_NONE, KEY_F3));
        viewZoom.shortcuts.add(new Shortcut("k-vz-4", "Center Zoom",   "F4",     MOD_NONE, KEY_F4));
        viewZoom.shortcuts.add(new Shortcut("k-vz-5", "Fit Screen",    "Home",   MOD_NONE, KEY_HOME));
        viewZoom.shortcuts.add(new Shortcut("k-vz-6", "Switch Units",  "Ctrl+U", MOD_CTRL, KEY_U));
        viewZoom.shortcuts.add(new Shortcut("k-vz-7", "Reset Coords",  "Space",  MOD_NONE, KEY_SPACE));
        p.categories.add(viewZoom);

        ShortcutCategory layers = new ShortcutCategory("layers", "Layers");
        layers.shortcuts.add(new Shortcut("k-l-1", "Copper Layer",    "PgDn",  MOD_NONE,  KEY_PAGEDOWN));
        layers.shortcuts.add(new Shortcut("k-l-2", "Component Layer", "PgUp",  MOD_NONE,  KEY_PAGEUP));
        layers.shortcuts.add(new Shortcut("k-l-3", "Inner Layer 1",   "F5",    MOD_NONE,  KEY_F5));
        layers.shortcuts.add(new Shortcut("k-l-4", "Inner Layer 2",   "F6",    MOD_NONE,  KEY_F6));
        layers.shortcuts.add(new Shortcut("k-l-5", "Next Layer",      "+",     MOD_SHIFT, KEY_EQUALS));
        layers.shortcuts.add(new Shortcut("k-l-6", "Prev Layer",      "-",     MOD_NONE,  KEY_MINUS));
        layers.shortcuts.add(new Shortcut("k-l-7", "High Contrast",   "H",     MOD_NONE,  KEY_H));
        p.categories.add(layers);

        ShortcutCategory drawing = new ShortcutCategory("drawing-edit", "Drawing & Edit");
        drawing.shortcuts.add(new Shortcut("k-de-1",  "Begin Wire",   "W",  MOD_NONE, KEY_W));
        drawing.shortcuts.add(new Shortcut("k-de-2",  "Begin Bus",    "B",  MOD_NONE, KEY_B));
        drawing.shortcuts.add(new Shortcut("k-de-3",  "Add Label",    "L",  MOD_NONE, KEY_L));
        drawing.shortcuts.add(new Shortcut("k-de-4",  "Add Power",    "P",  MOD_NONE, KEY_P));
        drawing.shortcuts.add(new Shortcut("k-de-5",  "Add Junction", "J",  MOD_NONE, KEY_J));
        drawing.shortcuts.add(new Shortcut("k-de-6",  "Add Sheet",    "S",  MOD_NONE, KEY_S));
        drawing.shortcuts.add(new Shortcut("k-de-7",  "No Connect",   "Q",  MOD_NONE, KEY_Q));
        drawing.shortcuts.add(new Shortcut("k-de-8",  "Hier. Label",  "H",  MOD_NONE, KEY_H));
        drawing.shortcuts.add(new Shortcut("k-de-9",  "Wire Entry",   "Z",  MOD_NONE, KEY_Z));
        drawing.shortcuts.add(new Shortcut("k-de-10", "Bus Entry",    "/",  MOD_NONE, KEY_SLASH));
        drawing.shortcuts.add(new Shortcut("k-de-11", "End Wire/Bus", "K",  MOD_NONE, KEY_K));
        p.categories.add(drawing);

        ShortcutCategory components = new ShortcutCategory("components", "Components");
        components.shortcuts.add(new Shortcut("k-c-1", "Add Component", "A",  MOD_NONE, KEY_A));
        components.shortcuts.add(new Shortcut("k-c-2", "Move Item",     "M",  MOD_NONE, KEY_M));
        components.shortcuts.add(new Shortcut("k-c-3", "Copy Item",     "C",  MOD_NONE, KEY_C));
        components.shortcuts.add(new Shortcut("k-c-4", "Drag Item",     "G",  MOD_NONE, KEY_G));
        components.shortcuts.add(new Shortcut("k-c-5", "Rotate Item",   "R",  MOD_NONE, KEY_R));
        components.shortcuts.add(new Shortcut("k-c-6", "Mirror X",      "X",  MOD_NONE, KEY_X));
        components.shortcuts.add(new Shortcut("k-c-7", "Mirror Y",      "Y",  MOD_NONE, KEY_Y));
        components.shortcuts.add(new Shortcut("k-c-8", "Flip Item",     "F",  MOD_NONE, KEY_F));
        components.shortcuts.add(new Shortcut("k-c-9", "Orient Normal", "N",  MOD_NONE, KEY_N));
        p.categories.add(components);

        ShortcutCategory properties = new ShortcutCategory("properties", "Properties");
        properties.shortcuts.add(new Shortcut("k-p-1", "Edit Item",      "E",  MOD_NONE, KEY_E));
        properties.shortcuts.add(new Shortcut("k-p-2", "Edit Value",     "V",  MOD_NONE, KEY_V));
        properties.shortcuts.add(new Shortcut("k-p-3", "Edit Ref.",      "U",  MOD_NONE, KEY_U));
        properties.shortcuts.add(new Shortcut("k-p-4", "Edit Footprint", "F",  MOD_NONE, KEY_F));
        properties.shortcuts.add(new Shortcut("k-p-5", "Get Footprint",  "T",  MOD_NONE, KEY_T));
        p.categories.add(properties);

        ShortcutCategory pcb = new ShortcutCategory("pcb-design", "PCB Design");
        pcb.shortcuts.add(new Shortcut("k-pd-1", "Add Track",      "X",      MOD_NONE, KEY_X));
        pcb.shortcuts.add(new Shortcut("k-pd-2", "Add Via",        "V",      MOD_NONE, KEY_V));
        pcb.shortcuts.add(new Shortcut("k-pd-3", "Add Microvia",   "Ctrl+V", MOD_CTRL, KEY_V));
        pcb.shortcuts.add(new Shortcut("k-pd-4", "Track Posture",  "/",      MOD_NONE, KEY_SLASH));
        pcb.shortcuts.add(new Shortcut("k-pd-5", "Drag Track",     "D",      MOD_NONE, KEY_D));
        pcb.shortcuts.add(new Shortcut("k-pd-6", "End Track",      "End",    MOD_NONE, KEY_END));
        pcb.shortcuts.add(new Shortcut("k-pd-7", "Add Module",     "O",      MOD_NONE, KEY_O));
        pcb.shortcuts.add(new Shortcut("k-pd-8", "Track Width +",  "W",      MOD_NONE, KEY_W));
        pcb.shortcuts.add(new Shortcut("k-pd-9", "Track Width -",  "Ctrl+W", MOD_CTRL, KEY_W));
        p.categories.add(pcb);

        ShortcutCategory fileMisc = new ShortcutCategory("file-misc", "File & Misc");
        fileMisc.shortcuts.add(new Shortcut("k-f-1", "Save Board",  "Ctrl+S",  MOD_CTRL, KEY_S));
        fileMisc.shortcuts.add(new Shortcut("k-f-2", "Load Board",  "Ctrl+L",  MOD_CTRL, KEY_L));
        fileMisc.shortcuts.add(new Shortcut("k-f-3", "Find Item",   "Ctrl+F",  MOD_CTRL, KEY_F));
        fileMisc.shortcuts.add(new Shortcut("k-f-4", "Undo",        "Ctrl+Z",  MOD_CTRL, KEY_Z));
        fileMisc.shortcuts.add(new Shortcut("k-f-5", "Redo",        "Ctrl+Y",  MOD_CTRL, KEY_Y));
        fileMisc.shortcuts.add(new Shortcut("k-f-6", "Delete Item", "Del",     MOD_NONE, KEY_DELETE));
        fileMisc.shortcuts.add(new Shortcut("k-f-7", "Delete Seg.", "BkSp",    MOD_NONE, KEY_BACKSPACE));
        p.categories.add(fileMisc);
        return p;
    }

    private ShortcutProfile createNomadProfile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "nomad"; p.name = "Nomad Sculpt";
        p.description = "Shortcuts for Nomad Sculpt";
        p.icon = "ic_nomad"; p.createdAt = System.currentTimeMillis();

        ShortcutCategory sculpt = new ShortcutCategory("tools", "Tools");
        sculpt.shortcuts.add(new Shortcut("n-t-1",  "Clay",    "C", MOD_NONE, KEY_C));
        sculpt.shortcuts.add(new Shortcut("n-t-2",  "Smooth",  "S", MOD_NONE, KEY_S));
        sculpt.shortcuts.add(new Shortcut("n-t-3",  "Flatten", "F", MOD_NONE, KEY_F));
        sculpt.shortcuts.add(new Shortcut("n-t-4",  "Inflate", "I", MOD_NONE, KEY_I));
        sculpt.shortcuts.add(new Shortcut("n-t-5",  "Crease",  "R", MOD_NONE, KEY_R));
        sculpt.shortcuts.add(new Shortcut("n-t-6",  "Pinch",   "P", MOD_NONE, KEY_P));
        sculpt.shortcuts.add(new Shortcut("n-t-7",  "Nudge",   "N", MOD_NONE, KEY_N));
        sculpt.shortcuts.add(new Shortcut("n-t-8",  "Stamp",   "T", MOD_NONE, KEY_T));
        sculpt.shortcuts.add(new Shortcut("n-t-9",  "Tube",    "U", MOD_NONE, KEY_U));
        sculpt.shortcuts.add(new Shortcut("n-t-10", "Drag",    "D", MOD_NONE, KEY_D));
        sculpt.shortcuts.add(new Shortcut("n-t-11", "Trim",    "X", MOD_NONE, KEY_X));
        sculpt.shortcuts.add(new Shortcut("n-t-12", "Split",   "V", MOD_NONE, KEY_V));
        p.categories.add(sculpt);

        ShortcutCategory brush = new ShortcutCategory("brush", "Brush");
        brush.shortcuts.add(new Shortcut("n-b-1", "Radius +",     "]",        MOD_NONE,  KEY_RBRACKET));
        brush.shortcuts.add(new Shortcut("n-b-2", "Radius -",     "[",        MOD_NONE,  KEY_LBRACKET));
        brush.shortcuts.add(new Shortcut("n-b-3", "Intensity +",  "Shift++",  MOD_SHIFT, KEY_EQUALS));
        brush.shortcuts.add(new Shortcut("n-b-4", "Intensity -",  "Shift+-",  MOD_SHIFT, KEY_MINUS));
        p.categories.add(brush);

        ShortcutCategory transform = new ShortcutCategory("transform", "Transform");
        transform.shortcuts.add(new Shortcut("n-tr-1", "Move",   "W", MOD_NONE, KEY_W));
        transform.shortcuts.add(new Shortcut("n-tr-2", "Rotate", "E", MOD_NONE, KEY_E));
        transform.shortcuts.add(new Shortcut("n-tr-3", "Scale",  "Z", MOD_NONE, KEY_Z));
        transform.shortcuts.add(new Shortcut("n-tr-4", "Sym X",  "1", MOD_NONE, KEY_1));
        transform.shortcuts.add(new Shortcut("n-tr-5", "Sym Y",  "2", MOD_NONE, KEY_2));
        transform.shortcuts.add(new Shortcut("n-tr-6", "Sym Z",  "3", MOD_NONE, KEY_3));
        p.categories.add(transform);

        ShortcutCategory view = new ShortcutCategory("view", "View");
        view.shortcuts.add(new Shortcut("n-v-1", "Front",        "F1",      MOD_NONE,  KEY_F1));
        view.shortcuts.add(new Shortcut("n-v-2", "Back",         "F2",      MOD_NONE,  KEY_F2));
        view.shortcuts.add(new Shortcut("n-v-3", "Right",        "F3",      MOD_NONE,  KEY_F3));
        view.shortcuts.add(new Shortcut("n-v-4", "Left",         "F4",      MOD_NONE,  KEY_F4));
        view.shortcuts.add(new Shortcut("n-v-5", "Top",          "F5",      MOD_NONE,  KEY_F5));
        view.shortcuts.add(new Shortcut("n-v-6", "Bottom",       "F6",      MOD_NONE,  KEY_F6));
        view.shortcuts.add(new Shortcut("n-v-7", "Reset Camera", "Home",    MOD_NONE,  KEY_HOME));
        view.shortcuts.add(new Shortcut("n-v-8", "Frame Object", "Shift+F", MOD_SHIFT, KEY_F));
        p.categories.add(view);

        ShortcutCategory mesh = new ShortcutCategory("mesh", "Mesh");
        mesh.shortcuts.add(new Shortcut("n-m-1", "Subdivide",   "Tab",    MOD_NONE, KEY_TAB));
        mesh.shortcuts.add(new Shortcut("n-m-2", "Voxel Remesh","M",      MOD_NONE, KEY_M));
        mesh.shortcuts.add(new Shortcut("n-m-3", "Dyn. Topo",   "B",      MOD_NONE, KEY_B));
        mesh.shortcuts.add(new Shortcut("n-m-4", "Validate",    "Ctrl+V", MOD_CTRL, KEY_V));
        p.categories.add(mesh);

        ShortcutCategory masking = new ShortcutCategory("masking", "Masking");
        masking.shortcuts.add(new Shortcut("n-ma-1", "Mask Brush",   "K",       MOD_NONE, KEY_K));
        masking.shortcuts.add(new Shortcut("n-ma-2", "Invert Mask",  "Ctrl+I",  MOD_CTRL, KEY_I));
        masking.shortcuts.add(new Shortcut("n-ma-3", "Clear Mask",   "Alt+M",   MOD_ALT,  KEY_M));
        masking.shortcuts.add(new Shortcut("n-ma-4", "Fill Mask",    "Ctrl+M",  MOD_CTRL, KEY_M));
        masking.shortcuts.add(new Shortcut("n-ma-5", "Sharpen Mask", "Ctrl+S",  MOD_CTRL, KEY_S));
        p.categories.add(masking);

        ShortcutCategory edit = new ShortcutCategory("edit", "Edit");
        edit.shortcuts.add(new Shortcut("n-e-1", "Undo",       "Ctrl+Z", MOD_CTRL, KEY_Z));
        edit.shortcuts.add(new Shortcut("n-e-2", "Redo",       "Ctrl+Y", MOD_CTRL, KEY_Y));
        edit.shortcuts.add(new Shortcut("n-e-3", "Save",       "Ctrl+S", MOD_CTRL, KEY_S));
        edit.shortcuts.add(new Shortcut("n-e-4", "Duplicate",  "Ctrl+D", MOD_CTRL, KEY_D));
        edit.shortcuts.add(new Shortcut("n-e-5", "Delete",     "Del",    MOD_NONE, KEY_DELETE));
        edit.shortcuts.add(new Shortcut("n-e-6", "Select All", "Ctrl+A", MOD_CTRL, KEY_A));
        p.categories.add(edit);
        return p;
    }

    private ShortcutProfile createFusion360Profile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "fusion360"; p.name = "Fusion 360";
        p.description = "Shortcuts for Autodesk Fusion 360";
        p.icon = "ic_fusion360"; p.createdAt = System.currentTimeMillis();

        ShortcutCategory navigation = new ShortcutCategory("navigation", "Navigation");
        navigation.shortcuts.add(new Shortcut("f-nav-1", "Zoom to Fit", "F6",   MOD_NONE, KEY_F6));
        navigation.shortcuts.add(new Shortcut("f-nav-2", "Home View",   "Home", MOD_NONE, KEY_HOME));
        navigation.shortcuts.add(new Shortcut("f-nav-3", "Look At",     "L",    MOD_NONE, KEY_L));
        navigation.shortcuts.add(new Shortcut("f-nav-4", "Full Screen", "F11",  MOD_NONE, KEY_F11));
        p.categories.add(navigation);

        ShortcutCategory file = new ShortcutCategory("file", "File");
        file.shortcuts.add(new Shortcut("f-fi-1", "New Design", "Ctrl+N",       MOD_CTRL,       KEY_N));
        file.shortcuts.add(new Shortcut("f-fi-2", "Open",       "Ctrl+O",       MOD_CTRL,       KEY_O));
        file.shortcuts.add(new Shortcut("f-fi-3", "Save",       "Ctrl+S",       MOD_CTRL,       KEY_S));
        file.shortcuts.add(new Shortcut("f-fi-4", "Save As",    "Ctrl+Shift+S", MOD_CTRL_SHIFT, KEY_S));
        file.shortcuts.add(new Shortcut("f-fi-5", "Export",     "Ctrl+E",       MOD_CTRL,       KEY_E));
        file.shortcuts.add(new Shortcut("f-fi-6", "3D Print",   "Ctrl+P",       MOD_CTRL,       KEY_P));
        p.categories.add(file);

        ShortcutCategory general = new ShortcutCategory("general", "General");
        general.shortcuts.add(new Shortcut("f-ge-1", "Undo",       "Ctrl+Z", MOD_CTRL, KEY_Z));
        general.shortcuts.add(new Shortcut("f-ge-2", "Redo",       "Ctrl+Y", MOD_CTRL, KEY_Y));
        general.shortcuts.add(new Shortcut("f-ge-3", "Copy",       "Ctrl+C", MOD_CTRL, KEY_C));
        general.shortcuts.add(new Shortcut("f-ge-4", "Paste",      "Ctrl+V", MOD_CTRL, KEY_V));
        general.shortcuts.add(new Shortcut("f-ge-5", "Delete",     "Del",    MOD_NONE, KEY_DELETE));
        general.shortcuts.add(new Shortcut("f-ge-6", "Select All", "Ctrl+A", MOD_CTRL, KEY_A));
        general.shortcuts.add(new Shortcut("f-ge-7", "Find",       "Ctrl+F", MOD_CTRL, KEY_F));
        general.shortcuts.add(new Shortcut("f-ge-8", "Escape",     "Esc",    MOD_NONE, KEY_ESC));
        p.categories.add(general);

        ShortcutCategory sketch = new ShortcutCategory("sketch", "Sketch");
        sketch.shortcuts.add(new Shortcut("f-sk-1",  "Create Sketch",  "S",     MOD_NONE, KEY_S));
        sketch.shortcuts.add(new Shortcut("f-sk-2",  "Line",           "L",     MOD_NONE, KEY_L));
        sketch.shortcuts.add(new Shortcut("f-sk-3",  "Rectangle",      "R",     MOD_NONE, KEY_R));
        sketch.shortcuts.add(new Shortcut("f-sk-4",  "Circle",         "C",     MOD_NONE, KEY_C));
        sketch.shortcuts.add(new Shortcut("f-sk-5",  "Arc",            "A",     MOD_NONE, KEY_A));
        sketch.shortcuts.add(new Shortcut("f-sk-6",  "Dimension",      "D",     MOD_NONE, KEY_D));
        sketch.shortcuts.add(new Shortcut("f-sk-7",  "Trim",           "T",     MOD_NONE, KEY_T));
        sketch.shortcuts.add(new Shortcut("f-sk-8",  "Offset",         "O",     MOD_NONE, KEY_O));
        sketch.shortcuts.add(new Shortcut("f-sk-9",  "Mirror",         "M",     MOD_NONE, KEY_M));
        sketch.shortcuts.add(new Shortcut("f-sk-10", "Fillet",         "F",     MOD_NONE, KEY_F));
        sketch.shortcuts.add(new Shortcut("f-sk-11", "Construction",   "X",     MOD_NONE, KEY_X));
        sketch.shortcuts.add(new Shortcut("f-sk-12", "Project",        "P",     MOD_NONE, KEY_P));
        sketch.shortcuts.add(new Shortcut("f-sk-13", "Finish Sketch",  "Enter", MOD_NONE, KEY_ENTER));
        p.categories.add(sketch);

        ShortcutCategory modeling = new ShortcutCategory("modeling", "Modeling");
        modeling.shortcuts.add(new Shortcut("f-mo-1", "Extrude",      "E",            MOD_NONE,       KEY_E));
        modeling.shortcuts.add(new Shortcut("f-mo-2", "Press Pull",   "Q",            MOD_NONE,       KEY_Q));
        modeling.shortcuts.add(new Shortcut("f-mo-3", "Fillet",       "F",            MOD_NONE,       KEY_F));
        modeling.shortcuts.add(new Shortcut("f-mo-4", "Chamfer",      "Ctrl+Shift+F", MOD_CTRL_SHIFT, KEY_F));
        modeling.shortcuts.add(new Shortcut("f-mo-5", "Hole",         "H",            MOD_NONE,       KEY_H));
        modeling.shortcuts.add(new Shortcut("f-mo-6", "Combine",      "Ctrl+Shift+C", MOD_CTRL_SHIFT, KEY_C));
        modeling.shortcuts.add(new Shortcut("f-mo-7", "Move/Copy",    "M",            MOD_NONE,       KEY_M));
        modeling.shortcuts.add(new Shortcut("f-mo-8", "Appearance",   "A",            MOD_NONE,       KEY_A));
        modeling.shortcuts.add(new Shortcut("f-mo-9", "Phys. Mat.",   "P",            MOD_NONE,       KEY_P));
        p.categories.add(modeling);

        ShortcutCategory construction = new ShortcutCategory("construction", "Construction");
        construction.shortcuts.add(new Shortcut("f-co-1", "Offset Plane", "Ctrl+Shift+P", MOD_CTRL_SHIFT, KEY_P));
        construction.shortcuts.add(new Shortcut("f-co-2", "Midplane",     "Ctrl+Shift+M", MOD_CTRL_SHIFT, KEY_M));
        construction.shortcuts.add(new Shortcut("f-co-3", "Axis",         "Ctrl+Shift+A", MOD_CTRL_SHIFT, KEY_A));
        construction.shortcuts.add(new Shortcut("f-co-4", "Measure",      "I",            MOD_NONE,       KEY_I));
        p.categories.add(construction);

        ShortcutCategory assembly = new ShortcutCategory("assembly", "Assembly");
        assembly.shortcuts.add(new Shortcut("f-as-1", "As-Built Joint", "Ctrl+Shift+J", MOD_CTRL_SHIFT, KEY_J));
        assembly.shortcuts.add(new Shortcut("f-as-2", "Joint",          "J",            MOD_NONE,       KEY_J));
        assembly.shortcuts.add(new Shortcut("f-as-3", "Ground",         "G",            MOD_NONE,       KEY_G));
        assembly.shortcuts.add(new Shortcut("f-as-4", "Align",          "Ctrl+Shift+L", MOD_CTRL_SHIFT, KEY_L));
        p.categories.add(assembly);

        ShortcutCategory display = new ShortcutCategory("display", "Display");
        display.shortcuts.add(new Shortcut("f-di-1", "Visibility",       "V",            MOD_NONE,       KEY_V));
        display.shortcuts.add(new Shortcut("f-di-2", "Show/Hide Bodies", "Ctrl+Shift+V", MOD_CTRL_SHIFT, KEY_V));
        display.shortcuts.add(new Shortcut("f-di-3", "Section Analysis", "Ctrl+Shift+9", MOD_CTRL_SHIFT, KEY_9));
        display.shortcuts.add(new Shortcut("f-di-4", "Wireframe",        "Ctrl+1",       MOD_CTRL,       KEY_1));
        display.shortcuts.add(new Shortcut("f-di-5", "Shaded",           "Ctrl+2",       MOD_CTRL,       KEY_2));
        display.shortcuts.add(new Shortcut("f-di-6", "Shaded+Wire",      "Ctrl+3",       MOD_CTRL,       KEY_3));
        display.shortcuts.add(new Shortcut("f-di-7", "Toggle Grid",      "Ctrl+Shift+G", MOD_CTRL_SHIFT, KEY_G));
        p.categories.add(display);
        return p;
    }

    private ShortcutProfile createPhotoshopProfile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "photoshop"; p.name = "Photoshop";
        p.description = "Shortcuts for Adobe Photoshop";
        p.icon = "ic_photoshop"; p.createdAt = System.currentTimeMillis();

        ShortcutCategory tools = new ShortcutCategory("tools", "Tools");
        tools.shortcuts.add(new Shortcut("ps-t-1",  "Move",          "V",  MOD_NONE, KEY_V));
        tools.shortcuts.add(new Shortcut("ps-t-2",  "Brush",         "B",  MOD_NONE, KEY_B));
        tools.shortcuts.add(new Shortcut("ps-t-3",  "Eraser",        "E",  MOD_NONE, KEY_E));
        tools.shortcuts.add(new Shortcut("ps-t-4",  "Clone Stamp",   "S",  MOD_NONE, KEY_S));
        tools.shortcuts.add(new Shortcut("ps-t-5",  "Healing Brush", "J",  MOD_NONE, KEY_J));
        tools.shortcuts.add(new Shortcut("ps-t-6",  "Text",          "T",  MOD_NONE, KEY_T));
        tools.shortcuts.add(new Shortcut("ps-t-7",  "Pen",           "P",  MOD_NONE, KEY_P));
        tools.shortcuts.add(new Shortcut("ps-t-8",  "Rectangle",     "U",  MOD_NONE, KEY_U));
        tools.shortcuts.add(new Shortcut("ps-t-9",  "Hand",          "H",  MOD_NONE, KEY_H));
        tools.shortcuts.add(new Shortcut("ps-t-10", "Zoom",          "Z",  MOD_NONE, KEY_Z));
        p.categories.add(tools);

        ShortcutCategory edit = new ShortcutCategory("edit", "Edit");
        edit.shortcuts.add(new Shortcut("ps-e-1",  "Undo",           "Ctrl+Z",       MOD_CTRL,       KEY_Z));
        edit.shortcuts.add(new Shortcut("ps-e-2",  "Redo",           "Ctrl+Y",       MOD_CTRL,       KEY_Y));
        edit.shortcuts.add(new Shortcut("ps-e-3",  "Copy",           "Ctrl+C",       MOD_CTRL,       KEY_C));
        edit.shortcuts.add(new Shortcut("ps-e-4",  "Paste",          "Ctrl+V",       MOD_CTRL,       KEY_V));
        edit.shortcuts.add(new Shortcut("ps-e-5",  "Save",           "Ctrl+S",       MOD_CTRL,       KEY_S));
        edit.shortcuts.add(new Shortcut("ps-e-6",  "Free Transform", "Ctrl+T",       MOD_CTRL,       KEY_T));
        edit.shortcuts.add(new Shortcut("ps-e-7",  "Deselect",       "Ctrl+D",       MOD_CTRL,       KEY_D));
        edit.shortcuts.add(new Shortcut("ps-e-8",  "Select All",     "Ctrl+A",       MOD_CTRL,       KEY_A));
        edit.shortcuts.add(new Shortcut("ps-e-9",  "Levels",         "Ctrl+L",       MOD_CTRL,       KEY_L));
        edit.shortcuts.add(new Shortcut("ps-e-10", "Curves",         "Ctrl+M",       MOD_CTRL,       KEY_M));
        edit.shortcuts.add(new Shortcut("ps-e-11", "New Layer",      "Ctrl+Shift+N", MOD_CTRL_SHIFT, KEY_N));
        p.categories.add(edit);

        ShortcutCategory view = new ShortcutCategory("view", "View");
        view.shortcuts.add(new Shortcut("ps-v-1", "Zoom In",    "Ctrl++",     MOD_CTRL_SHIFT, KEY_EQUALS));
        view.shortcuts.add(new Shortcut("ps-v-2", "Zoom Out",   "Ctrl+-",     MOD_CTRL,       KEY_MINUS));
        view.shortcuts.add(new Shortcut("ps-v-3", "Fit Screen", "Ctrl+0",     MOD_CTRL,       KEY_0));
        view.shortcuts.add(new Shortcut("ps-v-4", "100%",       "Ctrl+Alt+0", MOD_CTRL_ALT,   KEY_0));
        view.shortcuts.add(new Shortcut("ps-v-5", "Rulers",     "Ctrl+R",     MOD_CTRL,       KEY_R));
        p.categories.add(view);
        return p;
    }

    private ShortcutProfile createVSCodeProfile() {
        ShortcutProfile p = new ShortcutProfile();
        p.id = "vscode"; p.name = "VS Code";
        p.description = "Shortcuts for Visual Studio Code";
        p.icon = "ic_vscode"; p.createdAt = System.currentTimeMillis();

        ShortcutCategory navigation = new ShortcutCategory("navigation", "Navigation");
        navigation.shortcuts.add(new Shortcut("v-nav-1", "Quick Open",   "Ctrl+P",       MOD_CTRL,       KEY_P));
        navigation.shortcuts.add(new Shortcut("v-nav-2", "Cmd Palette",  "Ctrl+Shift+P", MOD_CTRL_SHIFT, KEY_P));
        navigation.shortcuts.add(new Shortcut("v-nav-3", "Go to Line",   "Ctrl+G",       MOD_CTRL,       KEY_G));
        navigation.shortcuts.add(new Shortcut("v-nav-4", "Explorer",     "Ctrl+Shift+E", MOD_CTRL_SHIFT, KEY_E));
        navigation.shortcuts.add(new Shortcut("v-nav-5", "Search",       "Ctrl+Shift+F", MOD_CTRL_SHIFT, KEY_F));
        navigation.shortcuts.add(new Shortcut("v-nav-6", "Extensions",   "Ctrl+Shift+X", MOD_CTRL_SHIFT, KEY_X));
        navigation.shortcuts.add(new Shortcut("v-nav-7", "Terminal",     "Ctrl+`",       MOD_CTRL,       KEY_GRAVE));
        p.categories.add(navigation);

        ShortcutCategory editting = new ShortcutCategory("edit", "Edit");
        editting.shortcuts.add(new Shortcut("v-e-1",  "Find",          "Ctrl+F",       MOD_CTRL,      KEY_F));
        editting.shortcuts.add(new Shortcut("v-e-2",  "Replace",       "Ctrl+H",       MOD_CTRL,      KEY_H));
        editting.shortcuts.add(new Shortcut("v-e-3",  "Comment",       "Ctrl+/",       MOD_CTRL,      KEY_SLASH));
        editting.shortcuts.add(new Shortcut("v-e-4",  "Format",        "Shift+Alt+F",  MOD_SHIFT_ALT, KEY_F));
        editting.shortcuts.add(new Shortcut("v-e-5",  "Undo",          "Ctrl+Z",       MOD_CTRL,      KEY_Z));
        editting.shortcuts.add(new Shortcut("v-e-6",  "Redo",          "Ctrl+Shift+Z", MOD_CTRL_SHIFT,KEY_Z));
        editting.shortcuts.add(new Shortcut("v-e-7",  "Dup. Line",     "Shift+Alt+↓",  MOD_SHIFT_ALT, KEY_DOWN));
        editting.shortcuts.add(new Shortcut("v-e-8",  "Move Line Up",  "Alt+↑",        MOD_ALT,       KEY_UP));
        editting.shortcuts.add(new Shortcut("v-e-9",  "Move Line Dn",  "Alt+↓",        MOD_ALT,       KEY_DOWN));
        editting.shortcuts.add(new Shortcut("v-e-10", "Delete Line",   "Ctrl+Shift+K", MOD_CTRL_SHIFT,KEY_K));
        p.categories.add(editting);

        ShortcutCategory code = new ShortcutCategory("code", "Code");
        code.shortcuts.add(new Shortcut("v-c-1", "Save",           "Ctrl+S",    MOD_CTRL,  KEY_S));
        code.shortcuts.add(new Shortcut("v-c-2", "Save All",       "Ctrl+K S",  MOD_CTRL,  KEY_S));
        code.shortcuts.add(new Shortcut("v-c-3", "Rename",         "F2",        MOD_NONE,  KEY_F2));
        code.shortcuts.add(new Shortcut("v-c-4", "Go to Def.",     "F12",       MOD_NONE,  KEY_F12));
        code.shortcuts.add(new Shortcut("v-c-5", "Peek Def.",      "Alt+F12",   MOD_ALT,   KEY_F12));
        code.shortcuts.add(new Shortcut("v-c-6", "Find All Refs.", "Shift+F12", MOD_SHIFT, KEY_F12));
        code.shortcuts.add(new Shortcut("v-c-7", "Quick Fix",      "Ctrl+.",    MOD_CTRL,  KEY_PERIOD));
        p.categories.add(code);

        ShortcutCategory viewCat = new ShortcutCategory("view", "View");
        viewCat.shortcuts.add(new Shortcut("v-v-1", "Sidebar",     "Ctrl+B",       MOD_CTRL,       KEY_B));
        viewCat.shortcuts.add(new Shortcut("v-v-2", "Full Screen", "F11",          MOD_NONE,       KEY_F11));
        viewCat.shortcuts.add(new Shortcut("v-v-3", "Split Editor","Ctrl+\\",      MOD_CTRL,       KEY_BACKSLASH));
        viewCat.shortcuts.add(new Shortcut("v-v-4", "Zoom In",     "Ctrl++",       MOD_CTRL_SHIFT, KEY_EQUALS));
        viewCat.shortcuts.add(new Shortcut("v-v-5", "Zoom Out",    "Ctrl+-",       MOD_CTRL,       KEY_MINUS));
        p.categories.add(viewCat);
        return p;
    }

    /**
     * Get all profiles
     */
    public List<ShortcutProfile> getAllProfiles() {
        return new ArrayList<>(profiles);
    }

    /**
     * Get profile by ID
     */
    public ShortcutProfile getProfileById(String id) {
        for (ShortcutProfile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Get active profile. Re-reads active id from preferences so multiple {@link ShortcutProfileManager}
     * instances (e.g. keyboard vs Shortcut Hub) stay aligned after {@link #setActiveProfile}.
     */
    public ShortcutProfile getActiveProfile() {
        activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE,
                activeProfileId != null ? activeProfileId : "default");
        return getProfileById(activeProfileId);
    }

    /**
     * Reloads profiles JSON and active profile id from disk. Safe if prefs are unchanged.
     * Call before listing profiles from the keyboard after edits in Shortcut Hub.
     */
    public void reloadProfilesFromPreferences() {
        List<ShortcutProfile> loaded = loadProfiles();
        if (loaded != null && !loaded.isEmpty()) {
            profiles = loaded;
        }
        activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE, "default");
    }

    /**
     * Set active profile
     */
    public void setActiveProfile(String profileId) {
        if (getProfileById(profileId) != null) {
            activeProfileId = profileId;
            prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply();
            Log.d(TAG, "Active profile set to: " + profileId);
            
            if (listener != null) {
                listener.onProfileChanged(profileId);
            }
        }
    }

    /**
     * Create new profile
     */
    public ShortcutProfile createProfile(String name, String description) {
        ShortcutProfile profile = new ShortcutProfile();
        profile.id = "custom_" + System.currentTimeMillis();
        profile.name = name;
        profile.description = description;
        profile.icon = "ic_custom";
        profile.shortcuts = new ArrayList<>();
        profile.categories = new ArrayList<>();
        profile.categories.add(new ShortcutCategory("general", "General"));
        profile.createdAt = System.currentTimeMillis();
        
        profiles.add(profile);
        saveProfiles();
        
        if (listener != null) {
            listener.onProfileCreated(profile);
        }
        
        return profile;
    }

    /**
     * Update profile
     */
    public void updateProfile(ShortcutProfile profile) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                saveProfiles();
                
                if (listener != null) {
                    listener.onProfileUpdated(profile);
                }
                return;
            }
        }
    }

    /**
     * Delete profile
     */
    public void deleteProfile(String profileId) {
        // Don't allow deleting default profile
        if ("default".equals(profileId)) {
            Log.w(TAG, "Cannot delete default profile");
            return;
        }
        
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profileId)) {
                profiles.remove(i);
                saveProfiles();
                
                // Switch to default if active profile was deleted
                if (activeProfileId.equals(profileId)) {
                    setActiveProfile("default");
                }
                
                if (listener != null) {
                    listener.onProfileDeleted(profileId);
                }
                return;
            }
        }
    }

    /**
     * Export profile to JSON
     */
    public String exportProfile(String profileId) {
        ShortcutProfile profile = getProfileById(profileId);
        if (profile != null) {
            return gson.toJson(profile);
        }
        return null;
    }

    /**
     * Import profile from JSON
     */
    public ShortcutProfile importProfile(String json) {
        try {
            Type type = new TypeToken<ShortcutProfile>(){}.getType();
            ShortcutProfile profile = gson.fromJson(json, type);
            
            // Generate new ID to avoid conflicts
            profile.id = "imported_" + System.currentTimeMillis();
            profile.createdAt = System.currentTimeMillis();
            
            profiles.add(profile);
            saveProfiles();
            
            if (listener != null) {
                listener.onProfileImported(profile);
            }
            
            return profile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to import profile: " + e.getMessage());
            return null;
        }
    }

    /**
     * Duplicate profile
     */
    public ShortcutProfile duplicateProfile(String profileId) {
        ShortcutProfile original = getProfileById(profileId);
        if (original != null) {
            ShortcutProfile duplicate = new ShortcutProfile();
            duplicate.id = "copy_" + System.currentTimeMillis();
            duplicate.name = original.name + " (Copy)";
            duplicate.description = original.description;
            duplicate.icon = original.icon;
            duplicate.shortcuts = new ArrayList<>(original.shortcuts);
            duplicate.createdAt = System.currentTimeMillis();
            
            profiles.add(duplicate);
            saveProfiles();
            
            if (listener != null) {
                listener.onProfileCreated(duplicate);
            }
            
            return duplicate;
        }
        return null;
    }

    /**
     * Set listener for profile changes
     */
    public void setListener(ProfileChangeListener listener) {
        this.listener = listener;
    }

    // ─── My Shortcuts (per-profile favorites) ───────────────────────────────

    /**
     * Returns the user's saved "My Shortcuts" list for the given profile.
     */
    public List<Shortcut> getMyShortcuts(String profileId) {
        String json = prefs.getString(MY_SHORTCUTS_KEY_PREFIX + profileId, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Shortcut>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Persists the user's "My Shortcuts" list for the given profile.
     */
    public void updateMyShortcuts(String profileId, List<Shortcut> shortcuts) {
        writeMyShortcutsToPrefs(profileId, shortcuts, false);
    }

    private void writeMyShortcutsToPrefs(String profileId, List<Shortcut> shortcuts, boolean synchronous) {
        if (profileId == null || shortcuts == null) {
            return;
        }
        String json = gson.toJson(shortcuts);
        android.content.SharedPreferences.Editor ed =
                prefs.edit().putString(MY_SHORTCUTS_KEY_PREFIX + profileId, json);
        if (synchronous) {
            ed.commit();
        } else {
            ed.apply();
        }
    }

    /**
     * Persists My Shortcuts in the given order (e.g. after drag-reorder). Updates {@link Shortcut#displayOrder}.
     *
     * @param notifyListener when false, skips {@link ProfileChangeListener#onProfileUpdated} (e.g. per-row
     *                       drag in Shortcut Hub to avoid refreshing the list mid-gesture).
     */
    public void reorderMyShortcuts(String profileId, List<Shortcut> ordered, boolean notifyListener) {
        if (profileId == null || ordered == null) {
            return;
        }
        List<Shortcut> copy = new ArrayList<>(ordered);
        renumberDisplayOrder(copy);
        writeMyShortcutsToPrefs(profileId, copy, true);
        if (notifyListener) {
            ShortcutProfile p = getProfileById(profileId);
            if (listener != null && p != null) {
                listener.onProfileUpdated(p);
            }
        }
    }

    public void reorderMyShortcuts(String profileId, List<Shortcut> ordered) {
        reorderMyShortcuts(profileId, ordered, true);
    }

    public List<Shortcut> getOrderedShortcutsForTopStrip(String profileId) {
        ShortcutProfile profile = getProfileById(profileId);
        if (profile == null) {
            return new ArrayList<>();
        }
        List<Shortcut> source = getMyShortcuts(profileId);
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<Shortcut> sorted = new ArrayList<>(source);
        sortShortcutsListForStrip(sorted);
        return sorted;
    }

    /**
     * Puts the chosen My-favorite shortcut at {@code stripIndex} in the ordered strip (0 = leftmost).
     * The shortcut must already exist in My Shortcuts for this profile.
     */
    public void assignMyShortcutToStripIndex(String profileId, int stripIndex, String shortcutId) {
        if (profileId == null || shortcutId == null || stripIndex < 0) {
            return;
        }
        List<Shortcut> ordered = new ArrayList<>(getMyShortcuts(profileId));
        sortShortcutsListForStrip(ordered);
        Shortcut chosen = null;
        for (Shortcut s : ordered) {
            if (s != null && shortcutId.equals(s.id)) {
                chosen = s;
                break;
            }
        }
        if (chosen == null) {
            return;
        }
        ordered.removeIf(s -> s != null && shortcutId.equals(s.id));
        int pos = Math.min(stripIndex, ordered.size());
        ordered.add(pos, chosen);
        renumberDisplayOrder(ordered);
        updateMyShortcuts(profileId, ordered);
        ShortcutProfile p = getProfileById(profileId);
        if (listener != null && p != null) {
            listener.onProfileUpdated(p);
        }
    }

    /**
     * Normalizes modifier bits for storage/display consistency (e.g. lone Ctrl → Cmd on macOS target).
     */
    public static int normalizeModifiersForTargetOs(int modifiers, String targetOs) {
        final int modCtrl = 0x01;
        final int modCmd = 0x08;
        if ("macos".equals(targetOs)) {
            boolean hasCtrl = (modifiers & modCtrl) != 0;
            boolean hasCmd = (modifiers & modCmd) != 0;
            if (hasCtrl && !hasCmd) {
                return (modifiers & ~modCtrl) | modCmd;
            }
        }
        return modifiers;
    }

    private static int nextDisplayOrderAcrossProfile(ShortcutProfile profile) {
        int max = 0;
        for (Shortcut s : profile.getAllShortcutsFlat()) {
            if (s != null && s.displayOrder > max) {
                max = s.displayOrder;
            }
        }
        return max + 1;
    }

    private static ShortcutCategory findOrCreateGeneralCategory(ShortcutProfile profile) {
        if (profile.categories == null) {
            profile.categories = new ArrayList<>();
        }
        for (ShortcutCategory c : profile.categories) {
            if (c != null && "general".equals(c.id)) {
                if (c.shortcuts == null) {
                    c.shortcuts = new ArrayList<>();
                }
                return c;
            }
        }
        ShortcutCategory general = new ShortcutCategory("general", "General");
        if (general.shortcuts == null) {
            general.shortcuts = new ArrayList<>();
        }
        profile.categories.add(0, general);
        return general;
    }

    /**
     * True if the profile already has a shortcut with the same HID key and semantically same modifiers
     * for the target OS (e.g. Ctrl vs Cmd on macOS).
     */
    public boolean profileContainsChordNormalized(
            ShortcutProfile profile,
            int keyCode,
            int modifiers,
            String targetOs
    ) {
        if (profile == null) {
            return false;
        }
        int want = normalizeModifiersForTargetOs(modifiers, targetOs);
        for (Shortcut s : profile.getAllShortcutsFlat()) {
            if (s == null) {
                continue;
            }
            int have = normalizeModifiersForTargetOs(s.modifiers, targetOs);
            if (s.keyCode == keyCode && have == want) {
                return true;
            }
        }
        return false;
    }

    /** True if the profile contains the same chord (normalized per target OS). */
    public boolean profileHasChord(String profileId, int keyCode, int modifiers, String targetOs) {
        ShortcutProfile p = getProfileById(profileId);
        if (p == null) {
            return false;
        }
        return profileContainsChordNormalized(p, keyCode, modifiers, targetOs);
    }

    /**
     * Adds a new shortcut to General (or flat list), appends it to My Shortcuts for the profile, and saves.
     *
     * @return the new shortcut, or null if the profile is missing or the chord already exists
     */
    public Shortcut addQuickShortcutToGeneralAndFavorites(
            String profileId,
            int keyCode,
            int modifiers,
            String targetOs
    ) {
        ShortcutProfile profile = getProfileById(profileId);
        if (profile == null || keyCode < 0) {
            return null;
        }
        if (profileContainsChordNormalized(profile, keyCode, modifiers, targetOs)) {
            return null;
        }
        // Store raw HID modifier bits from the parser; apply target-OS mapping only when sending
        // (see CustomKeyboardView.normalizeShortcutModifiersForTargetOs) so shortcuts stay correct
        // when the user changes target OS.
        String label = KeyParser.toLabelForTargetOs(keyCode, modifiers, targetOs);
        Shortcut shortcut = new Shortcut(
                "user-" + System.currentTimeMillis(),
                "",
                label,
                modifiers,
                keyCode);
        shortcut.icon = "";
        shortcut.displayOrder = nextDisplayOrderAcrossProfile(profile);

        if (profile.categories != null && !profile.categories.isEmpty()) {
            ShortcutCategory gen = findOrCreateGeneralCategory(profile);
            if (gen.shortcuts == null) {
                gen.shortcuts = new ArrayList<>();
            }
            gen.shortcuts.add(shortcut);
        } else {
            if (profile.shortcuts == null) {
                profile.shortcuts = new ArrayList<>();
            }
            profile.shortcuts.add(shortcut);
        }

        List<Shortcut> my = new ArrayList<>(getMyShortcuts(profileId));
        boolean already = false;
        for (Shortcut s : my) {
            if (s != null && shortcut.id.equals(s.id)) {
                already = true;
                break;
            }
        }
        if (!already) {
            my.add(shortcut);
            renumberDisplayOrder(my);
            updateMyShortcuts(profileId, my);
        }

        updateProfile(profile);
        return shortcut;
    }

    private ShortcutCategory findCategoryById(ShortcutProfile profile, String categoryId) {
        if (profile == null || categoryId == null || profile.categories == null) {
            return null;
        }
        for (ShortcutCategory c : profile.categories) {
            if (c != null && categoryId.equals(c.id)) {
                if (c.shortcuts == null) {
                    c.shortcuts = new ArrayList<>();
                }
                return c;
            }
        }
        return null;
    }

    private Shortcut buildQuickShortcut(int keyCode, int modifiers, String targetOs, ShortcutProfile profile) {
        String label = KeyParser.toLabelForTargetOs(keyCode, modifiers, targetOs);
        Shortcut shortcut = new Shortcut(
                "user-" + System.currentTimeMillis(),
                "",
                label,
                modifiers,
                keyCode);
        shortcut.icon = "";
        shortcut.displayOrder = nextDisplayOrderAcrossProfile(profile);
        return shortcut;
    }

    /**
     * Adds a new shortcut to a specific category (or to flat shortcuts if profile is legacy-only),
     * without adding it to Favorites.
     *
     * @return new shortcut, or null if invalid / duplicate / category missing
     */
    public Shortcut addQuickShortcutToCategoryOnly(
            String profileId,
            @Nullable String categoryId,
            int keyCode,
            int modifiers,
            String targetOs
    ) {
        ShortcutProfile profile = getProfileById(profileId);
        if (profile == null || keyCode < 0) {
            return null;
        }
        if (profileContainsChordNormalized(profile, keyCode, modifiers, targetOs)) {
            return null;
        }
        Shortcut shortcut = buildQuickShortcut(keyCode, modifiers, targetOs, profile);
        if (profile.categories != null && !profile.categories.isEmpty()) {
            if (categoryId == null || categoryId.trim().isEmpty()) {
                return null;
            }
            ShortcutCategory target = findCategoryById(profile, categoryId);
            if (target == null) {
                return null;
            }
            target.shortcuts.add(shortcut);
        } else {
            if (profile.shortcuts == null) {
                profile.shortcuts = new ArrayList<>();
            }
            profile.shortcuts.add(shortcut);
        }
        updateProfile(profile);
        return shortcut;
    }

    public boolean isBuiltInProfileId(@Nullable String profileId) {
        if (profileId == null) {
            return false;
        }
        switch (profileId) {
            case "default":
            case "blender":
            case "kicad":
            case "nomad":
            case "fusion360":
            case "photoshop":
            case "vscode":
                return true;
            default:
                return false;
        }
    }

    /**
     * Resets Favorites for the given built-in profile to seeded defaults used by fresh install flow.
     */
    public boolean resetMyShortcutsToDefaultForProfile(String profileId) {
        if (!isBuiltInProfileId(profileId)) {
            return false;
        }
        ShortcutProfile profile = getProfileById(profileId);
        if (profile == null) {
            return false;
        }
        List<Shortcut> seeded = buildSeededMyShortcuts(profile);
        renumberDisplayOrder(seeded);
        updateMyShortcuts(profileId, seeded);
        if (listener != null) {
            listener.onProfileUpdated(profile);
        }
        return true;
    }

    /**
     * Profile change listener interface
     */
    public interface ProfileChangeListener {
        void onProfileChanged(String profileId);
        void onProfileCreated(ShortcutProfile profile);
        void onProfileUpdated(ShortcutProfile profile);
        void onProfileDeleted(String profileId);
        void onProfileImported(ShortcutProfile profile);
    }

    /** Shortcut data class */
    public static class Shortcut {
        public String id;
        public String name;
        public String label;
        public int modifiers;
        public int keyCode;
        public String icon;
        public int displayOrder;

        public Shortcut() {}

        public Shortcut(String id, String name, String label, int modifiers, int keyCode) {
            this.id = id;
            this.name = name;
            this.label = label;
            this.modifiers = modifiers;
            this.keyCode = keyCode;
            this.icon = "";
            this.displayOrder = 0;
        }

        public Shortcut(String id, String name, String label, int modifiers, int keyCode, String icon, int displayOrder) {
            this(id, name, label, modifiers, keyCode);
            this.icon = icon;
            this.displayOrder = displayOrder;
        }
    }

    /** Category grouping shortcuts within a profile */
    public static class ShortcutCategory {
        public String id;
        public String name;
        public List<Shortcut> shortcuts;

        public ShortcutCategory() { shortcuts = new ArrayList<>(); }

        public ShortcutCategory(String id, String name) {
            this.id = id;
            this.name = name;
            this.shortcuts = new ArrayList<>();
        }
    }

    /** Profile data class */
    public static class ShortcutProfile {
        public String id;
        public String name;
        public String description;
        public String icon;
        /** Flat shortcuts list (used for legacy profiles or My Shortcuts). */
        public List<Shortcut> shortcuts;
        /** Category-grouped shortcuts (used by built-in profiles). */
        public List<ShortcutCategory> categories;
        public long createdAt;

        public ShortcutProfile() {
            shortcuts = new ArrayList<>();
            categories = new ArrayList<>();
        }

        /** Returns all shortcuts flattened across categories (and flat list). */
        public List<Shortcut> getAllShortcutsFlat() {
            List<Shortcut> all = new ArrayList<>();
            if (shortcuts != null) {
                all.addAll(shortcuts);
            }
            if (categories != null) {
                for (ShortcutCategory cat : categories) {
                    if (cat != null && cat.shortcuts != null) {
                        all.addAll(cat.shortcuts);
                    }
                }
            }
            return all;
        }

        public int getShortcutCount() {
            int count = shortcuts != null ? shortcuts.size() : 0;
            if (categories != null) {
                for (ShortcutCategory cat : categories) count += cat.shortcuts.size();
            }
            return count;
        }
    }
}
