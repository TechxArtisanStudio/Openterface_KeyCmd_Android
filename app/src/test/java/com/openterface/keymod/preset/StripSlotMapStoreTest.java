package com.openterface.keymod.preset;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class StripSlotMapStoreTest {

    @Test
    public void slotKey_firstAndLastColumn_useOneBasedColumnInId() {
        Assert.assertEquals("b-p0r2c1", StripSlotMapStore.slotKey(0, 2, 0, false));
        Assert.assertEquals("b-p0r2c7", StripSlotMapStore.slotKey(0, 2, 6, false));
        Assert.assertEquals("f-p1r3c4", StripSlotMapStore.slotKey(1, 3, 3, true));
    }

    @Test
    public void canonicalSlotKeyOrSelf_migratesLegacy() {
        Assert.assertEquals("b-p0r2c1", StripSlotMapStore.canonicalSlotKeyOrSelf("p0_r2_c0_base"));
        Assert.assertEquals("f-p0r2c7", StripSlotMapStore.canonicalSlotKeyOrSelf("p0_r2_c6_fn"));
    }

    @Test
    public void remapSlotMapKeysToCanonical_rewritesLegacyKeys() {
        Map<String, String> in = new HashMap<>();
        in.put("p0_r2_c0_base", "s1");
        in.put("p0_r2_c3_fn", "s2");
        Map<String, String> out = StripSlotMapStore.remapSlotMapKeysToCanonical(in);
        Assert.assertEquals("s1", out.get("b-p0r2c1"));
        Assert.assertEquals("s2", out.get("f-p0r2c4"));
        Assert.assertFalse(out.containsKey("p0_r2_c0_base"));
    }

    @Test
    public void slotKey_page3_row2_column5_isCanonical() {
        Assert.assertEquals("b-p3r2c5", StripSlotMapStore.slotKey(3, 2, 4, false));
        Assert.assertEquals("f-p3r3c7", StripSlotMapStore.slotKey(3, 3, 6, true));
    }

    @Test
    public void parseSlotKey_acceptsCanonicalAndLegacy() {
        FixedStripLayoutCatalog.ParsedSlotKey a =
                FixedStripLayoutCatalog.parseSlotKey("b-p0r2c7");
        Assert.assertNotNull(a);
        Assert.assertEquals(0, a.pageIndex);
        Assert.assertEquals(2, a.stripRow);
        Assert.assertEquals(6, a.col);
        Assert.assertFalse(a.fnLayer);

        FixedStripLayoutCatalog.ParsedSlotKey b =
                FixedStripLayoutCatalog.parseSlotKey("F-P2R3C1");
        Assert.assertNotNull(b);
        Assert.assertEquals(2, b.pageIndex);
        Assert.assertEquals(3, b.stripRow);
        Assert.assertEquals(0, b.col);
        Assert.assertTrue(b.fnLayer);

        FixedStripLayoutCatalog.ParsedSlotKey c =
                FixedStripLayoutCatalog.parseSlotKey("p1_r3_c2_base");
        Assert.assertNotNull(c);
        Assert.assertEquals(1, c.pageIndex);
        Assert.assertEquals(3, c.stripRow);
        Assert.assertEquals(2, c.col);
        Assert.assertFalse(c.fnLayer);
    }
}
