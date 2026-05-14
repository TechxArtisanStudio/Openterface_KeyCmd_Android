package com.openterface.keymod.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public class MacUnicodeHexAuditPresetsTest {

    @Test
    public void presetA_lengthAndCoverage() {
        assertEquals(4, MacUnicodeHexAuditPresets.PRESET_A_CODEPOINTS.length);
        assertTrue(MacUnicodeHexAuditPresets.coversAllHexKeys(MacUnicodeHexAuditPresets.PRESET_A_CODEPOINTS));
        Set<Character> u = MacUnicodeHexAuditPresets.hexDigitUnion(MacUnicodeHexAuditPresets.PRESET_A_CODEPOINTS);
        assertEquals(16, u.size());
    }

    @Test
    public void presetB_lengthAndCoverage() {
        assertEquals(6, MacUnicodeHexAuditPresets.PRESET_B_CODEPOINTS.length);
        assertTrue(MacUnicodeHexAuditPresets.coversAllHexKeys(MacUnicodeHexAuditPresets.PRESET_B_CODEPOINTS));
        Set<Character> u = MacUnicodeHexAuditPresets.hexDigitUnion(MacUnicodeHexAuditPresets.PRESET_B_CODEPOINTS);
        assertEquals(16, u.size());
    }

    @Test
    public void presetB_firstAndLastCodePoints() {
        int[] b = MacUnicodeHexAuditPresets.PRESET_B_CODEPOINTS;
        assertEquals(0x2318, b[0]);
        assertEquals(0x00BF, b[b.length - 1]);
    }

    @Test
    public void presetA_firstAndLastCodePoints() {
        int[] a = MacUnicodeHexAuditPresets.PRESET_A_CODEPOINTS;
        assertEquals(0x516D, a[0]);
        assertEquals(0x03B2, a[a.length - 1]);
    }
}
