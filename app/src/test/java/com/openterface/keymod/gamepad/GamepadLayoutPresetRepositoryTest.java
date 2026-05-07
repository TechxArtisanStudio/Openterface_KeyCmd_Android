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

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GamepadLayoutPresetRepositoryTest {

    private GamepadLayoutPresetRepository repo;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        repo = new GamepadLayoutPresetRepository(ctx);
        repo.ensureMigratedFromLegacy();
    }

    @Test
    public void bundledSlugFromAssetFilename_stripsJsonAndLowercases() {
        assertEquals("classic_1", GamepadLayoutPresetRepository.bundledSlugFromAssetFilename("Classic_1.json"));
        assertEquals("classic_1", GamepadLayoutPresetRepository.bundledSlugFromAssetFilename("CLASSIC_1.JSON"));
    }

    @Test
    public void bundledSlugFromAssetFilename_mapsNonAlphanumericToUnderscore() {
        assertEquals("my_preset", GamepadLayoutPresetRepository.bundledSlugFromAssetFilename("My Preset.json"));
    }

    @Test
    public void deletePreset_rejectsBuiltInDefault() {
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
    }

    @Test
    public void listPresets_excludesDiscontinuedBuiltinIds() {
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (r == null || r.id == null) {
                continue;
            }
            assertFalse("preset_two_buttons".equals(r.id));
            assertFalse(r.id.startsWith("preset_classic_"));
        }
    }

    @Test
    public void deletePreset_unknownId_returnsError() {
        assertNotNull(repo.deletePreset("preset_does_not_exist"));
    }

    @Test
    public void duplicateRenameDelete_roundTrip() {
        GamepadLayoutPresetRepository.DuplicateResult dup =
                repo.duplicatePreset(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
        assertTrue(dup.isSuccess());
        assertNotNull(dup.newId);
        assertNull(repo.renamePreset(dup.newId, "UnitTestLayout"));
        assertNull(repo.deletePreset(dup.newId));
    }

    @Test
    public void reorderPresets_rejectsWrongSize() {
        List<String> one = new ArrayList<>();
        one.add(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID);
        assertNotNull(repo.reorderPresets(one));
    }

    @Test
    public void reorderPresets_acceptsReverseOrder() {
        List<GamepadLayoutPresetRepository.PresetRef> before = repo.listPresets();
        if (before.size() < 2) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (int i = before.size() - 1; i >= 0; i--) {
            ids.add(before.get(i).id);
        }
        assertNull(repo.reorderPresets(ids));
        List<GamepadLayoutPresetRepository.PresetRef> after = repo.listPresets();
        assertEquals(before.size(), after.size());
        assertEquals(ids.get(0), after.get(0).id);
    }
}
