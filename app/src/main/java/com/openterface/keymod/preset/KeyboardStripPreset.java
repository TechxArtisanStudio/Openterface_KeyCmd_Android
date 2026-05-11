package com.openterface.keymod.preset;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gson-friendly document for exporting / importing strip-related settings (preset JSON v1).
 */
@SuppressWarnings("unused")
public class KeyboardStripPreset {

    public String format;
    public int schemaVersion;
    public Meta meta;
    public Scope scope;
    public ViewBlock view;
    public Strip strip;
    /** Present when {@link Scope#bundle} is {@link KeyboardStripPresetConstants#BUNDLE_PORTABLE}. */
    public ShortcutsBlock shortcuts;

    public static class Meta {
        public String displayName;
        public String description;
        public String exportedAt;
        public String sourceAppVersion;
    }

    public static class Scope {
        public String row1ShortcutHubProfileId;
        public String stripProfileId;
        public String bundle;
    }

    public static class ViewBlock {
        public int topShortcutDisplayMode;
    }

    public static class Strip {
        public Topology topology;
        public List<String> myShortcutsOrder;
        public List<String> profileHubSlotProfileIds;
        public Map<String, String> stripSlotMap;
        public FixedKeyLayers fixedKeyLayers;
    }

    public static class Topology {
        public int columns;
        public int favoritesTrailingSystemSlots;
        public int fixedRowBuiltinPages;
        public int fixedRowsPerBuiltinPage;
    }

    public static class FixedKeyLayers {
        public List<FixedPage> pages;
    }

    public static class FixedPage {
        public int pageIndex;
        public String builtinLayoutId;
    }

    public static class ShortcutsBlock {
        public List<com.openterface.keymod.ShortcutProfileManager.Shortcut> definitions;
    }

    public static boolean looksLikeStripPresetJson(String json) {
        if (json == null) {
            return false;
        }
        String t = json.trim();
        return t.startsWith("{") && t.contains("\"format\"") && t.contains("openterface_keymod_keyboard_preset");
    }

    @Nullable
    public static KeyboardStripPreset parseOrNull(String json) {
        try {
            KeyboardStripPreset p = new Gson().fromJson(json, KeyboardStripPreset.class);
            if (p == null || p.format == null) {
                return null;
            }
            return p;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static void validateOrThrow(KeyboardStripPreset p) throws IllegalArgumentException {
        if (p == null) {
            throw new IllegalArgumentException("null preset");
        }
        if (!KeyboardStripPresetConstants.FORMAT.equals(p.format)) {
            throw new IllegalArgumentException("Unknown format: " + p.format);
        }
        if (p.schemaVersion != KeyboardStripPresetConstants.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + p.schemaVersion);
        }
        if (p.scope == null) {
            throw new IllegalArgumentException("Missing scope");
        }
        String stripId = p.scope.stripProfileId != null ? p.scope.stripProfileId.trim() : "";
        if (!stripId.isEmpty() && !KeyboardStripPresetConstants.STRIP_PROFILE_ID.equals(stripId)) {
            throw new IllegalArgumentException("stripProfileId must be " + KeyboardStripPresetConstants.STRIP_PROFILE_ID);
        }
        if (p.strip != null && p.strip.topology != null) {
            Topology top = p.strip.topology;
            if (top.columns != KeyboardStripPresetConstants.TOP_PANEL_COLUMNS
                    || (top.fixedRowBuiltinPages != KeyboardStripPresetConstants.FIXED_ROW_BUILTIN_PAGES
                            && top.fixedRowBuiltinPages != 4)
                    || top.fixedRowsPerBuiltinPage != KeyboardStripPresetConstants.FIXED_ROWS_PER_BUILTIN_PAGE
                    || top.favoritesTrailingSystemSlots
                            != KeyboardStripPresetConstants.FAVORITES_TRAILING_SYSTEM_SLOTS) {
                throw new IllegalArgumentException("Topology mismatch with this app version");
            }
        }
    }

    /** Normalizes null collections for consumers. */
    public void normalizeCollections() {
        if (meta == null) {
            meta = new Meta();
        }
        if (scope == null) {
            scope = new Scope();
        }
        if (scope.stripProfileId == null || scope.stripProfileId.trim().isEmpty()) {
            scope.stripProfileId = KeyboardStripPresetConstants.STRIP_PROFILE_ID;
        }
        if (view == null) {
            view = new ViewBlock();
        }
        if (strip == null) {
            strip = new Strip();
        }
        if (strip.myShortcutsOrder == null) {
            strip.myShortcutsOrder = new ArrayList<>();
        }
        if (strip.profileHubSlotProfileIds == null) {
            strip.profileHubSlotProfileIds = new ArrayList<>();
        }
        if (strip.stripSlotMap == null) {
            strip.stripSlotMap = new HashMap<>();
        }
        if (strip.topology == null) {
            strip.topology = defaultTopology();
        } else if (strip.topology.fixedRowBuiltinPages == 4) {
            // Legacy exports before fixed-strip page 3 (hub toggles) was removed.
            strip.topology.fixedRowBuiltinPages = KeyboardStripPresetConstants.FIXED_ROW_BUILTIN_PAGES;
        }
        if (shortcuts != null && shortcuts.definitions == null) {
            shortcuts.definitions = new ArrayList<>();
        }
    }

    public static Topology defaultTopology() {
        Topology t = new Topology();
        t.columns = KeyboardStripPresetConstants.TOP_PANEL_COLUMNS;
        t.favoritesTrailingSystemSlots = KeyboardStripPresetConstants.FAVORITES_TRAILING_SYSTEM_SLOTS;
        t.fixedRowBuiltinPages = KeyboardStripPresetConstants.FIXED_ROW_BUILTIN_PAGES;
        t.fixedRowsPerBuiltinPage = KeyboardStripPresetConstants.FIXED_ROWS_PER_BUILTIN_PAGE;
        return t;
    }
}
