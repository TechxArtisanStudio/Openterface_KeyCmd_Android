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

    @Test
    public void repoRootXyabJson_loadsAndValidates() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File f = new File(repoRoot, "gamepad/xyab.json");
        assertTrue("Expected gamepad/xyab.json under " + repoRoot, f.isFile());
        String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        GamepadLayoutPresetDocument doc = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(doc);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        assertNotNull(find(doc, "button_a"));
        assertNotNull(find(doc, "button_b"));
        assertNotNull(find(doc, "button_x"));
        assertNotNull(find(doc, "button_y"));
    }

    @Test
    public void repoRootXyabTouchpadJson_loadsAndValidates() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File f = new File(repoRoot, "gamepad/xyab_touchpad.json");
        assertTrue("Expected gamepad/xyab_touchpad.json under " + repoRoot, f.isFile());
        String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        GamepadLayoutPresetDocument doc = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(doc);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        assertNotNull(find(doc, "touchpad_1"));
        GamepadLayoutPresetDocument.GamepadModule l = find(doc, "mouse_btn_l");
        GamepadLayoutPresetDocument.GamepadModule r = find(doc, "mouse_btn_r");
        assertNotNull(l);
        assertNotNull(r);
        assertEquals(Integer.valueOf(1), l.mouseButton);
        assertEquals(Integer.valueOf(3), r.mouseButton);
    }

    @Test
    public void repoRootMinecraftJavaJson_loadsAndValidates() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File f = new File(repoRoot, "gamepad/minecraft_java.json");
        assertTrue("Expected gamepad/minecraft_java.json under " + repoRoot, f.isFile());
        String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        GamepadLayoutPresetDocument doc = GamepadLayoutPresetDocument.parseOrNull(json);
        assertNotNull(doc);
        GamepadLayoutPresetDocument.validateOrThrow(doc);
        assertEquals(GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        assertEquals("preset_pack_minecraft_java", doc.meta.id);
        assertNotNull(find(doc, "stick_left"));
        assertNotNull(find(doc, "touchpad_1"));
        assertNotNull(find(doc, "mouse_btn_l"));
        assertNotNull(find(doc, "mouse_btn_r"));
        assertNotNull(find(doc, "scroll_strip_1"));
        GamepadLayoutPresetDocument.GamepadModule sneak = find(doc, "button_x");
        assertNotNull(sneak);
        assertTrue(Boolean.TRUE.equals(sneak.keyboardHoldLock));
        GamepadLayoutPresetDocument.GamepadModule jump = find(doc, "button_a");
        assertNotNull(jump);
        assertNotNull(jump.gestureLock);
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_HOLD_LOCK,
                GamepadGestureLock.slotAction(
                        GamepadGestureLock.slotForKey(
                                jump.gestureLock,
                                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_UP_LEFT)));
        assertEquals(
                GamepadLayoutPresetConstants.GESTURE_LOCK_ACTION_TURBO,
                GamepadGestureLock.slotAction(
                        GamepadGestureLock.slotForKey(
                                jump.gestureLock,
                                GamepadLayoutPresetConstants.GESTURE_LOCK_SLOT_DOWN_LEFT)));
    }

    @Test
    public void repoRootAllGamepadJsonFiles_loadAndValidate() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File gamepadDir = new File(repoRoot, "gamepad");
        assertTrue("Expected gamepad/ under " + repoRoot, gamepadDir.isDirectory());
        File[] files = gamepadDir.listFiles((d, name) -> name != null && name.endsWith(".json"));
        assertNotNull(files);
        assertTrue("Expected at least one gamepad/*.json", files.length >= 1);
        for (File f : files) {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            GamepadLayoutPresetDocument doc = GamepadLayoutPresetDocument.parseOrNull(json);
            assertNotNull(f.getName(), doc);
            GamepadLayoutPresetDocument.validateOrThrow(doc);
            assertEquals(f.getName(), GamepadLayoutPresetConstants.SCHEMA_VERSION, doc.schemaVersion);
        }
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
