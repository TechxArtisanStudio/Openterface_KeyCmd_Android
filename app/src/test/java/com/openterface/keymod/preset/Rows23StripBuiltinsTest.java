package com.openterface.keymod.preset;

import com.openterface.keymod.util.TopRows23StripProfileSlotPrefs;

import org.junit.Assert;
import org.junit.Test;

/**
 * Rows 2–3 strip built-ins: Default + Mine (strip_personal) only; legacy themed ids for migration.
 */
public class Rows23StripBuiltinsTest {

    @Test
    public void isBuiltInProfileId_recognizesDefaultAndMineBuiltin() {
        Assert.assertTrue(Rows23StripProfileConstants.isBuiltInProfileId(
                Rows23StripProfileConstants.DEFAULT_PROFILE_ID));
        Assert.assertTrue(Rows23StripProfileConstants.isBuiltInProfileId(
                Rows23StripProfileConstants.PERSONAL_PROFILE_ID));
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId("strip_symbols"));
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId("strip_user_made"));
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId(null));
    }

    @Test
    public void isLegacyRemovedThematicStripId_recognizesOldPresets() {
        Assert.assertTrue(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_symbols"));
        Assert.assertTrue(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_math"));
        Assert.assertTrue(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_boxlines"));
        Assert.assertTrue(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_latin"));
        Assert.assertTrue(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_arrows"));
        Assert.assertTrue(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_currency"));
        Assert.assertFalse(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_default"));
        Assert.assertFalse(Rows23StripProfileConstants.isLegacyRemovedThematicStripId("strip_personal"));
        Assert.assertFalse(Rows23StripProfileConstants.isLegacyRemovedThematicStripId(null));
    }

    @Test
    public void defaultStripProfileSlots_oneAndTwoSet_threeThroughSixUnassigned() {
        Assert.assertEquals(Rows23StripProfileConstants.DEFAULT_PROFILE_ID,
                TopRows23StripProfileSlotPrefs.defaultStripProfileIdForSlot(1));
        Assert.assertEquals(Rows23StripProfileConstants.PERSONAL_PROFILE_ID,
                TopRows23StripProfileSlotPrefs.defaultStripProfileIdForSlot(2));
        for (int s = 3; s <= 6; s++) {
            Assert.assertEquals(TopRows23StripProfileSlotPrefs.STRIP_PROFILE_SLOT_UNASSIGNED,
                    TopRows23StripProfileSlotPrefs.defaultStripProfileIdForSlot(s));
        }
    }

    @Test
    public void mineStripProfile_isEmptyShellLikeFreshDefault() {
        Rows23StripProfile p = Rows23StripProfileBuiltins.buildPersonalStripProfile();
        Assert.assertEquals(Rows23StripProfileConstants.PERSONAL_PROFILE_ID, p.id);
        Assert.assertEquals("Mine", p.name);
        Assert.assertNotNull(p.slotMap);
        Assert.assertNotNull(p.shortcuts);
        Assert.assertTrue(p.slotMap.isEmpty());
        Assert.assertTrue(p.shortcuts.isEmpty());
    }
}
