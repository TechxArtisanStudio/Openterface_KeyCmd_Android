package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, assetDir = "src/test/assets")
public class GamepadLayoutPresetRepositoryBundledSyncTest {

    private static final String BUNDLED_TEST_ID = "preset_pack_bundled_sync_test_only";

    private GamepadLayoutPresetRepository repo;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        clearPresetStore(ctx);
        repo = new GamepadLayoutPresetRepository(ctx);
        repo.ensureMigratedFromLegacy();
    }

    private static void clearPresetStore(Context ctx) {
        ctx.getSharedPreferences("GamepadLayoutPresetStore", Context.MODE_PRIVATE).edit().clear().commit();
        File dir = new File(ctx.getFilesDir(), "gamepad_layout_presets");
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    @Test
    public void syncBundled_importsTestAssetOnce() {
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            assertFalse(BUNDLED_TEST_ID.equals(r.id));
        }

        repo.syncBundledPresetsFromAssets();

        boolean found = false;
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (BUNDLED_TEST_ID.equals(r.id)) {
                assertEquals("BundledSyncTest", r.displayName);
                found = true;
            }
        }
        assertTrue(found);
        assertNotNull(repo.loadDocument(BUNDLED_TEST_ID));

        repo.syncBundledPresetsFromAssets();
        int count = 0;
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (BUNDLED_TEST_ID.equals(r.id)) {
                count++;
            }
        }
        assertEquals(1, count);
    }

    @Test
    public void syncBundled_afterDelete_doesNotReimport() {
        repo.syncBundledPresetsFromAssets();
        assertNull(repo.deletePreset(BUNDLED_TEST_ID));

        repo.syncBundledPresetsFromAssets();

        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            assertFalse(BUNDLED_TEST_ID.equals(r.id));
        }
        assertNull(repo.loadDocument(BUNDLED_TEST_ID));
    }
}
