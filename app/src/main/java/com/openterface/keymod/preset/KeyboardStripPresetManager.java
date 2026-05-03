package com.openterface.keymod.preset;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openterface.keymod.BuildConfig;
import com.openterface.keymod.ShortcutProfileManager;
import com.openterface.keymod.ShortcutProfileManager.Shortcut;
import com.openterface.keymod.ShortcutProfileManager.ShortcutProfile;
import com.openterface.keymod.util.TopShortcutProfileSlotPrefs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Builds and applies {@link KeyboardStripPreset} JSON (row 1 order for an app profile, hub slots,
 * display mode, optional strip slot map, portable definitions for the strip profile).
 */
public class KeyboardStripPresetManager {

    private static final String TAG = "KeyboardStripPresetMgr";
    private static final String APP_PREFS = "AppPrefs";
    private static final String KEY_TOP_SHORTCUT_DISPLAY_MODE = "top_shortcut_display_mode";

    private final Context appContext;
    private final ShortcutProfileManager profileManager;
    private final StripSlotMapStore slotMapStore;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public KeyboardStripPresetManager(Context context, ShortcutProfileManager profileManager) {
        this.appContext = context.getApplicationContext();
        this.profileManager = profileManager;
        this.slotMapStore = new StripSlotMapStore(appContext);
    }

    public StripSlotMapStore getSlotMapStore() {
        return slotMapStore;
    }

    @Nullable
    public String exportPreset(boolean portable) {
        profileManager.ensureKeyboardStripLayoutProfile();
        ShortcutProfile active = profileManager.getActiveProfile();
        if (active == null) {
            return null;
        }

        KeyboardStripPreset p = new KeyboardStripPreset();
        p.format = KeyboardStripPresetConstants.FORMAT;
        p.schemaVersion = KeyboardStripPresetConstants.SCHEMA_VERSION;
        p.meta = new KeyboardStripPreset.Meta();
        p.meta.displayName = portable ? "KeyMod strip (portable)" : "KeyMod strip (thin)";
        p.meta.description = "";
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        p.meta.exportedAt = iso.format(new Date());
        p.meta.sourceAppVersion = BuildConfig.VERSION_NAME;

        p.scope = new KeyboardStripPreset.Scope();
        p.scope.row1ShortcutHubProfileId = active.id;
        p.scope.stripProfileId = KeyboardStripPresetConstants.STRIP_PROFILE_ID;
        p.scope.bundle = portable ? KeyboardStripPresetConstants.BUNDLE_PORTABLE : KeyboardStripPresetConstants.BUNDLE_THIN;

        p.view = new KeyboardStripPreset.ViewBlock();
        p.view.topShortcutDisplayMode = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TOP_SHORTCUT_DISPLAY_MODE, 1);

        p.strip = new KeyboardStripPreset.Strip();
        p.strip.topology = KeyboardStripPreset.defaultTopology();
        List<Shortcut> ordered = profileManager.getOrderedShortcutsForTopStrip(active.id);
        List<String> ids = new ArrayList<>();
        if (ordered != null) {
            for (Shortcut s : ordered) {
                if (s != null && s.id != null) {
                    ids.add(s.id);
                }
            }
        }
        p.strip.myShortcutsOrder = ids;

        List<String> hub = new ArrayList<>(7);
        for (int i = 1; i <= 7; i++) {
            hub.add(TopShortcutProfileSlotPrefs.getProfileIdForSlot(appContext, i));
        }
        p.strip.profileHubSlotProfileIds = hub;
        p.strip.stripSlotMap = new HashMap<>(slotMapStore.snapshotForExport());

        p.strip.fixedKeyLayers = new KeyboardStripPreset.FixedKeyLayers();
        p.strip.fixedKeyLayers.pages = new ArrayList<>();
        p.strip.fixedKeyLayers.pages.add(fixedPage(0, "strip_fixed_page0_f_keys"));
        p.strip.fixedKeyLayers.pages.add(fixedPage(1, "strip_fixed_page1_modifiers_nav"));
        p.strip.fixedKeyLayers.pages.add(fixedPage(2, "strip_fixed_page2_symbols_profile_hub"));

        if (portable) {
            p.shortcuts = new KeyboardStripPreset.ShortcutsBlock();
            p.shortcuts.definitions = collectPortableDefinitions(p.strip.myShortcutsOrder, p.strip.stripSlotMap);
        }

        return gson.toJson(p);
    }

    private static KeyboardStripPreset.FixedPage fixedPage(int index, String id) {
        KeyboardStripPreset.FixedPage fp = new KeyboardStripPreset.FixedPage();
        fp.pageIndex = index;
        fp.builtinLayoutId = id;
        return fp;
    }

    private List<Shortcut> collectPortableDefinitions(List<String> row1Ids, Map<String, String> slotMap) {
        Set<String> need = new HashSet<>();
        if (row1Ids != null) {
            need.addAll(row1Ids);
        }
        if (slotMap != null) {
            need.addAll(slotMap.values());
        }
        List<Shortcut> out = new ArrayList<>();
        ShortcutProfile strip = profileManager.getProfileById(KeyboardStripPresetConstants.STRIP_PROFILE_ID);
        Map<String, Shortcut> byId = new HashMap<>();
        if (strip != null) {
            for (Shortcut s : strip.getAllShortcutsFlat()) {
                if (s != null && s.id != null) {
                    byId.put(s.id, s);
                }
            }
        }
        ShortcutProfile active = profileManager.getActiveProfile();
        if (active != null) {
            for (Shortcut s : active.getAllShortcutsFlat()) {
                if (s != null && s.id != null) {
                    byId.put(s.id, s);
                }
            }
        }
        for (String id : need) {
            Shortcut s = byId.get(id);
            if (s != null) {
                out.add(copyShortcut(s));
            }
        }
        return out;
    }

    private static Shortcut copyShortcut(Shortcut s) {
        Shortcut c = new Shortcut();
        c.id = s.id;
        c.name = s.name;
        c.label = s.label;
        c.modifiers = s.modifiers;
        c.keyCode = s.keyCode;
        c.icon = s.icon;
        c.displayOrder = s.displayOrder;
        return c;
    }

    /**
     * @return null on success, or an error message for the UI.
     */
    @Nullable
    public String importPreset(String json) {
        KeyboardStripPreset p = KeyboardStripPreset.parseOrNull(json);
        if (p == null) {
            return "Invalid JSON";
        }
        p.normalizeCollections();
        try {
            KeyboardStripPreset.validateOrThrow(p);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        profileManager.ensureKeyboardStripLayoutProfile();

        SharedPreferences appPrefs = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE);
        appPrefs.edit().putInt(KEY_TOP_SHORTCUT_DISPLAY_MODE, p.view.topShortcutDisplayMode).apply();

        List<String> hub = p.strip.profileHubSlotProfileIds;
        if (hub != null) {
            for (int i = 0; i < Math.min(hub.size(), 7); i++) {
                String pid = hub.get(i);
                if (pid != null && profileManager.getProfileById(pid) != null) {
                    TopShortcutProfileSlotPrefs.setProfileIdForSlot(appContext, i + 1, pid);
                }
            }
        }

        if (p.strip.stripSlotMap != null) {
            slotMapStore.replaceAll(p.strip.stripSlotMap);
        }

        boolean portable = KeyboardStripPresetConstants.BUNDLE_PORTABLE.equals(p.scope.bundle);
        if (portable && p.shortcuts != null && p.shortcuts.definitions != null) {
            profileManager.upsertShortcutsInStripProfileGeneral(p.shortcuts.definitions);
        }

        String row1ProfileId = p.scope.row1ShortcutHubProfileId;
        if (row1ProfileId == null || profileManager.getProfileById(row1ProfileId) == null) {
            row1ProfileId = profileManager.getActiveProfile() != null
                    ? profileManager.getActiveProfile().id
                    : "default";
        }
        List<Shortcut> newRow1 = buildMyShortcutsFromIds(row1ProfileId, p.strip.myShortcutsOrder);
        profileManager.reorderMyShortcuts(row1ProfileId, newRow1, true);

        Log.d(TAG, "Imported strip preset for row1 profile " + row1ProfileId);
        return null;
    }

    private List<Shortcut> buildMyShortcutsFromIds(String profileId, List<String> ids) {
        List<Shortcut> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        ShortcutProfile profile = profileManager.getProfileById(profileId);
        if (profile == null) {
            return out;
        }
        Map<String, Shortcut> byId = new HashMap<>();
        for (Shortcut s : profile.getAllShortcutsFlat()) {
            if (s != null && s.id != null) {
                byId.put(s.id, s);
            }
        }
        for (String id : ids) {
            Shortcut s = byId.get(id);
            if (s != null) {
                out.add(copyShortcut(s));
            }
        }
        profileManager.sortShortcutsListForStripPublic(out);
        return out;
    }

}
