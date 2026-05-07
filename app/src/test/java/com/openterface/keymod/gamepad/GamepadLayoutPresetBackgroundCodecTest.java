package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

public class GamepadLayoutPresetBackgroundCodecTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** 1×1 PNG (valid signature and chunks). */
    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Test
    public void prepareForPersistence_writesFileAndStripsEmbed() throws Exception {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "preset_test123";
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.backgroundImageEncoding = GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64;
        doc.layout.backgroundImageMediaType = GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_PNG;
        doc.layout.backgroundImageData = TINY_PNG_BASE64;

        File root = folder.newFolder();
        GamepadLayoutPresetBackgroundCodec.prepareForPersistence(root, doc);

        String name = GamepadLayoutPresetBackgroundCodec.bgFileNameForPresetId("preset_test123");
        File written = new File(root, name);
        assertTrue(written.isFile());
        assertEquals(name, doc.layout.backgroundImageFile);
        assertNull(doc.layout.backgroundImageData);
        assertNull(doc.layout.backgroundImageEncoding);
        assertNull(doc.layout.backgroundImageMediaType);
    }

    @Test
    public void injectEmbedForExport_roundTripsWithPrepare() throws Exception {
        File root = folder.newFolder();
        String name = GamepadLayoutPresetBackgroundCodec.bgFileNameForPresetId("preset_xyz");
        byte[] pngBytes = java.util.Base64.getDecoder().decode(TINY_PNG_BASE64);
        try (FileOutputStream out = new FileOutputStream(new File(root, name))) {
            out.write(pngBytes);
        }

        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.backgroundImageFile = name;

        GamepadLayoutPresetBackgroundCodec.injectEmbedForExport(root, doc, null);
        assertNotNull(doc.layout.backgroundImageData);
        assertEquals(GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64, doc.layout.backgroundImageEncoding);
        assertEquals(GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_PNG, doc.layout.backgroundImageMediaType);

        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "preset_xyz";
        GamepadLayoutPresetBackgroundCodec.prepareForPersistence(root, doc);
        assertArrayEquals(pngBytes, java.nio.file.Files.readAllBytes(new File(root, name).toPath()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void prepareForPersistence_rejectsMimeMismatch() throws Exception {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = "a";
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.backgroundImageEncoding = GamepadLayoutPresetConstants.BACKGROUND_EMBED_ENCODING_BASE64;
        doc.layout.backgroundImageMediaType = GamepadLayoutPresetConstants.BACKGROUND_MEDIA_TYPE_JPEG;
        doc.layout.backgroundImageData = TINY_PNG_BASE64;

        GamepadLayoutPresetBackgroundCodec.prepareForPersistence(folder.newFolder(), doc);
    }

    @Test
    public void clearEmbedFields_clearsPartialGarbage() {
        GamepadLayoutPresetDocument.LayoutGlobals L = new GamepadLayoutPresetDocument.LayoutGlobals();
        L.backgroundImageEncoding = "base64";
        L.backgroundImageData = null;
        GamepadLayoutPresetBackgroundCodec.clearEmbedFields(L);
        assertNull(L.backgroundImageEncoding);
        assertNull(L.backgroundImageMediaType);
        assertNull(L.backgroundImageData);
    }

    @Test
    public void injectEmbed_skipsPathTraversalBasename() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        GamepadLayoutPresetBackgroundCodec.injectEmbedForExport(folder.getRoot(), doc, "../secrets");
        assertNull(doc.layout.backgroundImageData);
    }

    @Test
    public void injectEmbed_readsOverrideBasename() throws Exception {
        File root = folder.newFolder();
        String custom = "custom_bg.png";
        byte[] pngBytes = java.util.Base64.getDecoder().decode(TINY_PNG_BASE64);
        try (FileOutputStream out = new FileOutputStream(new File(root, custom))) {
            out.write(pngBytes);
        }
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        GamepadLayoutPresetBackgroundCodec.injectEmbedForExport(root, doc, custom);
        assertNotNull(doc.layout.backgroundImageData);
    }
}
