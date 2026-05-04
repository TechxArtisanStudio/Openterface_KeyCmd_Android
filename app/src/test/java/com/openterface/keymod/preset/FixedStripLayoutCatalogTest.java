package com.openterface.keymod.preset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class FixedStripLayoutCatalogTest {

    @Test
    public void page2Row3Col3_pipeSlot_isShiftBackslash() {
        FixedStripLayoutCatalog.ParsedSlotKey slot =
                FixedStripLayoutCatalog.parseSlotKey("b-p2r3c3");
        assertNotNull(slot);
        assertEquals(2, slot.pageIndex);
        assertEquals(3, slot.stripRow);
        assertEquals(2, slot.col);
        assertEquals(false, slot.fnLayer);

        FixedStripLayoutCatalog.FactoryHid hid =
                FixedStripLayoutCatalog.resolveFactoryHidForSlot(slot);
        assertNotNull(hid);
        assertEquals(0x31, hid.keyCode);
        assertEquals(0x02, hid.modifiers);
    }

    @Test
    public void page2Row3Col2_backslashSlot_noShift() {
        FixedStripLayoutCatalog.ParsedSlotKey slot =
                FixedStripLayoutCatalog.parseSlotKey("b-p2r3c2");
        assertNotNull(slot);
        FixedStripLayoutCatalog.FactoryHid hid =
                FixedStripLayoutCatalog.resolveFactoryHidForSlot(slot);
        assertNotNull(hid);
        assertEquals(0x31, hid.keyCode);
        assertEquals(0, hid.modifiers);
    }
}
