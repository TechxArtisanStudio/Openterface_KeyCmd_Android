package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Ensures {@code gamepad/default.json} at repo root (copied to assets by {@code syncBundledGamepadPresets})
 * parses and passes {@link GamepadLayoutPresetDocument#validateOrThrow}.
 */
public class RepoRootDefaultGamepadJsonTest {

    @Test
    public void repoRootDefaultJson_loadsAndValidates() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File def = new File(repoRoot, "gamepad/default.json");
        assertTrue("Expected gamepad/default.json under " + repoRoot, def.isFile());
        String json = new String(Files.readAllBytes(def.toPath()), StandardCharsets.UTF_8);
        GamepadLayoutPresetDocument doc = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(doc);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID, doc.meta.id);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        assertNotNull(find(doc, "button_a"));
        assertNotNull(find(doc, "button_b"));
    }

    private static GamepadLayoutPresetDocument.GamepadModule find(
            GamepadLayoutPresetDocument doc, String id) {
        if (doc.modules == null) {
            return null;
        }
        for (GamepadLayoutPresetDocument.GamepadModule m : doc.modules) {
            if (m != null && id.equals(m.id)) {
                return m;
            }
        }
        return null;
    }
}
