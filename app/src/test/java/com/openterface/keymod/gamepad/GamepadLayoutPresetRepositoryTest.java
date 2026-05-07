package com.openterface.keymod.gamepad;

import static org.junit.Assert.assertEquals;
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
    public void deletePreset_rejectsBuiltInDefault() {
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
    }

    @Test
    public void deletePreset_rejectsBuiltInTwoButton() {
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.BUILT_IN_TWO_BUTTON_PRESET_ID));
    }

    @Test
    public void deletePreset_rejectsBuiltInClassicPresets() {
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX));
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_PLAYSTATION));
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NINTENDO));
        assertNotNull(repo.deletePreset(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES));
    }

    @Test
    public void resetClassicPresetToFactory_rejectsNonClassic() {
        assertNotNull(repo.resetClassicPresetToFactory(GamepadLayoutPresetConstants.DEFAULT_PRESET_ID));
        assertNotNull(repo.resetClassicPresetToFactory(null));
    }

    @Test
    public void resetClassicPresetToFactory_restoresCanonicalLayout() throws Exception {
        String id = GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES;
        repo.writeFile(id, minimalButtonAOnlyPresetStub());
        GamepadLayoutPresetDocument broken = repo.loadDocument(id);
        assertNotNull(broken);
        assertEquals(1, broken.modules.size());
        assertNull(repo.resetClassicPresetToFactory(id));
        GamepadLayoutPresetDocument after = repo.loadDocument(id);
        assertNotNull(after);
        assertTrue(after.modules.size() >= 4);
    }

    /** Intentionally not a NES layout; used to verify reset overwrites invalid on-disk JSON. */
    private static GamepadLayoutPresetDocument minimalButtonAOnlyPresetStub() {
        GamepadLayoutPresetDocument doc = new GamepadLayoutPresetDocument();
        doc.format = GamepadLayoutPresetConstants.DOCUMENT_FORMAT;
        doc.schemaVersion = GamepadLayoutPresetConstants.SCHEMA_VERSION;
        doc.meta = new GamepadLayoutPresetDocument.Meta();
        doc.meta.id = GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES;
        doc.meta.displayName = "stub";
        doc.layout = new GamepadLayoutPresetDocument.LayoutGlobals();
        doc.layout.mouseSensitivity = 1.0f;
        doc.layout.showTwoButtons = false;
        ArrayList<GamepadLayoutPresetDocument.GamepadModule> modules = new ArrayList<>();
        GamepadLayoutPresetDocument.GamepadModule btnA = new GamepadLayoutPresetDocument.GamepadModule();
        btnA.id = "button_a";
        btnA.type = GamepadLayoutPresetConstants.MODULE_TYPE_BUTTON;
        btnA.zIndex = 0;
        btnA.scale = 1.0f;
        btnA.anchorX = 0.5f;
        btnA.anchorY = 0.5f;
        btnA.hidKey = 40;
        btnA.modifierMask = 0;
        modules.add(btnA);
        doc.modules = modules;
        return doc;
    }

    @Test
    public void ensureMigratedFromLegacy_includesClassicPresetIds() {
        boolean seenXbox = false;
        boolean seenNes = false;
        for (GamepadLayoutPresetRepository.PresetRef r : repo.listPresets()) {
            if (r != null && GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX.equals(r.id)) {
                seenXbox = true;
            }
            if (r != null && GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_NES.equals(r.id)) {
                seenNes = true;
            }
        }
        assertTrue(seenXbox);
        assertTrue(seenNes);
        assertNotNull(repo.loadDocument(GamepadLayoutPresetConstants.BUILT_IN_PRESET_CLASSIC_XBOX));
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
