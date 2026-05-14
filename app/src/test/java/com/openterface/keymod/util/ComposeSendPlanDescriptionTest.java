package com.openterface.keymod.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComposeSendPlanDescriptionTest {

    @Test
    public void asciiPlan_dropsNonAscii() {
        ComposeSendPlanDescription.AsciiPlan p = ComposeSendPlanDescription.buildAsciiPlan("a\u5C0Ab");
        assertEquals(3, p.originalLength);
        assertEquals(2, p.sendableLength);
        assertEquals(1, p.droppedNonAsciiCount);
        assertEquals("ab", p.asciiPayload);
    }

    @Test
    public void unicodeTranscript_zun_macos_matchesCountSendUnits() {
        String text = "\u5C0A";
        String t = ComposeSendPlanDescription.buildUnicodeTranscript(text, "macos", 8000);
        assertTrue(t.contains("U+5C0A"));
        assertTrue(t.contains("5, c, 0, a"));
        assertEquals(1, HidTextKeystrokeSender.countSendUnits(text, true, "macos"));
    }

    @Test
    public void unicodeTranscript_mixedAsciiAndZun() {
        String text = "a\u5C0Ab";
        assertEquals(
                3,
                HidTextKeystrokeSender.countSendUnits(text, true, "macos"));
        String tr = ComposeSendPlanDescription.buildUnicodeTranscript(text, "macos", 8000);
        assertTrue(tr.contains("U+5C0A"));
        assertTrue(tr.contains("Type:"));
    }

    @Test
    public void unicodeTranscript_enterToken() {
        String text = "<ENTER>";
        assertEquals(1, HidTextKeystrokeSender.countSendUnits(text, true, "macos"));
        String tr = ComposeSendPlanDescription.buildUnicodeTranscript(text, "macos", 8000);
        assertTrue(tr.contains("ENTER") && tr.contains("<ENTER>"));
    }

    @Test
    public void countSendUnits_false_skipsNonAscii_matchesAsciiUnits() {
        String text = "a\u5C0Ab";
        int u = HidTextKeystrokeSender.countSendUnits(text, true, "macos");
        int a = HidTextKeystrokeSender.countSendUnits(text, false, "macos");
        assertEquals(3, u);
        assertEquals(2, a);
    }
}
