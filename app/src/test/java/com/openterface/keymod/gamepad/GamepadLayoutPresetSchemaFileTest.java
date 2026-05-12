package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Ensures the hand-maintained JSON Schema file stays parseable and points at the same
 * {@link GamepadLayoutPresetConstants#DOCUMENT_FORMAT} as the Gson model.
 */
public class GamepadLayoutPresetSchemaFileTest {

    @Test
    public void schemaFile_existsAndParses() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File schema = new File(repoRoot, "docs/gamepad_layout_preset.schema.json");
        assertTrue("Expected docs/gamepad_layout_preset.schema.json under " + repoRoot, schema.isFile());
        JsonElement root;
        try (BufferedReader r = Files.newBufferedReader(schema.toPath(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(r);
        }
        assertTrue(root.isJsonObject());
        JsonObject o = root.getAsJsonObject();
        assertEquals(
                "\"https://json-schema.org/draft/2020-12/schema\"",
                o.get("$schema").toString());
        JsonObject props = o.getAsJsonObject("properties");
        assertNotNull(props);
        JsonObject format = props.getAsJsonObject("format");
        assertNotNull(format);
        assertTrue(format.has("const"));
        assertEquals(
                "\"" + GamepadLayoutPresetConstants.DOCUMENT_FORMAT + "\"",
                format.get("const").toString());
    }

    @Test
    public void schemaFile_mentionsCanonicalValidatorInDescription() throws Exception {
        File appDir = new File(System.getProperty("user.dir"));
        File repoRoot = appDir.getName().equals("app") ? appDir.getParentFile() : appDir;
        File schema = new File(repoRoot, "docs/gamepad_layout_preset.schema.json");
        String text = new String(Files.readAllBytes(schema.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("GamepadLayoutPresetDocument.validateOrThrow"));
    }
}
