package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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
    private static final String NESTED_BUNDLED_TEST_ID = "preset_pack_subpack_nested_bundled_sync";

    private GamepadLayoutPresetRepository repo;

    @Before
    public void setUp() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        String[] bundled = ctx.getAssets().list("bundled_gamepad");
        assumeTrue("Robolectric needs merged Android assets (e.g. testOptions.unitTests.includeAndroidResources)",
                bundled != null && bundled.length > 0);
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

    @Test
    public void syncBundled_importsNestedAssetOnce() {
        repo.syncBundledPresetsFromAssets();

        boolean found = false;
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (NESTED_BUNDLED_TEST_ID.equals(r.id)) {
                assertEquals("NestedSubpackTest", r.displayName);
                found = true;
            }
        }
        assertTrue(found);
        assertNotNull(repo.loadDocument(NESTED_BUNDLED_TEST_ID));
    }

    @Test
    public void resetShipped_restoresDeletedBundledAndKeepsUserPreset() {
        repo.syncBundledPresetsFromAssets();
        assertNotNull(repo.loadDocument(BUNDLED_TEST_ID));

        GamepadLayoutPresetRepository.DuplicateResult dup =
                repo.duplicatePreset(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
        assertTrue(dup.isSuccess());
        assertNotNull(dup.newId);
        String userId = dup.newId;
        assertTrue(userId.startsWith("preset_"));
        assertNotNull(repo.loadDocument(userId));

        assertNull(repo.deletePreset(BUNDLED_TEST_ID));
        boolean hadBundled = false;
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (BUNDLED_TEST_ID.equals(r.id)) {
                hadBundled = true;
                break;
            }
        }
        assertFalse(hadBundled);
        assertNull(repo.loadDocument(BUNDLED_TEST_ID));

        assertNull(repo.resetAllShippedGamepadLayoutsFromAssets());

        boolean bundledBack = false;
        boolean userStill = false;
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (BUNDLED_TEST_ID.equals(r.id)) {
                bundledBack = true;
            }
            if (userId.equals(r.id)) {
                userStill = true;
            }
        }
        assertTrue(bundledBack);
        assertTrue(userStill);
        assertNotNull(repo.loadDocument(BUNDLED_TEST_ID));
        assertNotNull(repo.loadDocument(userId));
    }
}
