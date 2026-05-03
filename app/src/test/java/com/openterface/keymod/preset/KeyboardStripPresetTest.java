package com.openterface.keymod.preset;

import com.google.gson.Gson;
import com.openterface.keymod.ShortcutProfileManager;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class KeyboardStripPresetTest {

    @Test
    public void roundTrip_validateTopology() {
        KeyboardStripPreset p = new KeyboardStripPreset();
        p.format = KeyboardStripPresetConstants.FORMAT;
        p.schemaVersion = KeyboardStripPresetConstants.SCHEMA_VERSION;
        p.meta = new KeyboardStripPreset.Meta();
        p.meta.displayName = "Test";
        p.scope = new KeyboardStripPreset.Scope();
        p.scope.row1ShortcutHubProfileId = "default";
        p.scope.stripProfileId = KeyboardStripPresetConstants.STRIP_PROFILE_ID;
        p.scope.bundle = KeyboardStripPresetConstants.BUNDLE_THIN;
        p.view = new KeyboardStripPreset.ViewBlock();
        p.view.topShortcutDisplayMode = 1;
        p.strip = new KeyboardStripPreset.Strip();
        p.strip.topology = KeyboardStripPreset.defaultTopology();
        p.strip.myShortcutsOrder = Collections.singletonList("default_copy");
        p.strip.profileHubSlotProfileIds = Collections.singletonList("default");
        p.strip.stripSlotMap = Collections.emptyMap();

        Gson gson = new Gson();
        String json = gson.toJson(p);
        KeyboardStripPreset parsed = KeyboardStripPreset.parseOrNull(json);
        Assert.assertNotNull(parsed);
        parsed.normalizeCollections();
        KeyboardStripPreset.validateOrThrow(parsed);
        Assert.assertEquals(KeyboardStripPresetConstants.TOP_PANEL_COLUMNS, parsed.strip.topology.columns);
    }

    @Test
    public void portableDefinitions_roundTrip() {
        KeyboardStripPreset p = new KeyboardStripPreset();
        p.format = KeyboardStripPresetConstants.FORMAT;
        p.schemaVersion = 1;
        p.meta = new KeyboardStripPreset.Meta();
        p.scope = new KeyboardStripPreset.Scope();
        p.scope.row1ShortcutHubProfileId = "vscode";
        p.scope.bundle = KeyboardStripPresetConstants.BUNDLE_PORTABLE;
        p.view = new KeyboardStripPreset.ViewBlock();
        p.strip = new KeyboardStripPreset.Strip();
        p.strip.topology = KeyboardStripPreset.defaultTopology();
        p.strip.myShortcutsOrder = Collections.emptyList();
        p.strip.profileHubSlotProfileIds = Collections.emptyList();
        p.shortcuts = new KeyboardStripPreset.ShortcutsBlock();
        ShortcutProfileManager.Shortcut s = new ShortcutProfileManager.Shortcut(
                "x1", "Name", "Ctrl+A", 1, 4, "icon", 1);
        p.shortcuts.definitions = Collections.singletonList(s);

        Gson gson = new Gson();
        String json = gson.toJson(p);
        KeyboardStripPreset out = gson.fromJson(json, KeyboardStripPreset.class);
        Assert.assertNotNull(out.shortcuts);
        Assert.assertEquals(1, out.shortcuts.definitions.size());
        Assert.assertEquals("x1", out.shortcuts.definitions.get(0).id);
    }
}
