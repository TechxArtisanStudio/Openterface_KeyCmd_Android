package com.openterface.keymod.preset;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class Rows23StripProfileColumnSwapTest {

    @Test
    public void swapColumnAssignmentsInMap_swapsBaseAndFnPairs() {
        Map<String, String> m = new HashMap<>();
        m.put(StripSlotMapStore.slotKey(0, 2, 0, false), "a");
        m.put(StripSlotMapStore.slotKey(0, 2, 0, true), "b");
        m.put(StripSlotMapStore.slotKey(0, 2, 1, false), "c");
        m.put(StripSlotMapStore.slotKey(0, 2, 1, true), "d");

        Rows23StripProfileManager.swapColumnAssignmentsInMap(m, 0, 2, 0, 1);

        Assert.assertEquals("c", m.get(StripSlotMapStore.slotKey(0, 2, 0, false)));
        Assert.assertEquals("d", m.get(StripSlotMapStore.slotKey(0, 2, 0, true)));
        Assert.assertEquals("a", m.get(StripSlotMapStore.slotKey(0, 2, 1, false)));
        Assert.assertEquals("b", m.get(StripSlotMapStore.slotKey(0, 2, 1, true)));
    }

    @Test
    public void swapColumnAssignmentsInMap_sameColumn_noop() {
        Map<String, String> m = new HashMap<>();
        m.put(StripSlotMapStore.slotKey(0, 2, 0, false), "x");
        Rows23StripProfileManager.swapColumnAssignmentsInMap(m, 0, 2, 0, 0);
        Assert.assertEquals(1, m.size());
        Assert.assertEquals("x", m.get(StripSlotMapStore.slotKey(0, 2, 0, false)));
    }

    @Test
    public void swapColumnAssignmentsInMap_sparseMap() {
        Map<String, String> m = new HashMap<>();
        m.put(StripSlotMapStore.slotKey(0, 2, 0, false), "onlyA");

        Rows23StripProfileManager.swapColumnAssignmentsInMap(m, 0, 2, 0, 3);

        Assert.assertFalse(m.containsKey(StripSlotMapStore.slotKey(0, 2, 0, false)));
        Assert.assertEquals("onlyA", m.get(StripSlotMapStore.slotKey(0, 2, 3, false)));
        Assert.assertEquals(1, m.size());
    }
}
