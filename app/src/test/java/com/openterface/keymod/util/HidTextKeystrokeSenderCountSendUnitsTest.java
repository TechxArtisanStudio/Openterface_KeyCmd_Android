package com.openterface.keymod.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HidTextKeystrokeSenderCountSendUnitsTest {

    @Test
    public void countSendUnits_empty_isZero() {
        assertEquals(0, HidTextKeystrokeSender.countSendUnits("", false, "macos"));
    }

    @Test
    public void countSendUnits_ascii_matchesLength() {
        assertEquals(5, HidTextKeystrokeSender.countSendUnits("hello", false, "macos"));
    }

    @Test
    public void countSendUnits_specialEnterToken_countsOne() {
        assertEquals(3, HidTextKeystrokeSender.countSendUnits("a<ENTER>b", false, "macos"));
    }

    @Test
    public void countSendUnits_nonAscii_skippedWhenUnicodeDisallowed() {
        String withNonAscii = "a" + "\u00E9" + "b";
        assertEquals(2, HidTextKeystrokeSender.countSendUnits(withNonAscii, false, "macos"));
    }

    @Test
    public void countSendUnits_delayToken_contributesZero() {
        assertEquals(1, HidTextKeystrokeSender.countSendUnits("<DELAY1S>x", false, "macos"));
    }

    @Test
    public void countSendUnits_modifierOpenOnly_noUnits() {
        assertEquals(1, HidTextKeystrokeSender.countSendUnits("<CTRL>x", false, "macos"));
    }

    @Test
    public void countSendUnits_unicodeAllowed_countsOnePerCodePoint() {
        assertEquals(1, HidTextKeystrokeSender.countSendUnits("\u00E9", true, "macos"));
    }
}
