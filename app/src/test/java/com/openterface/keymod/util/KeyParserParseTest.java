package com.openterface.keymod.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyParserParseTest {

    @Test
    public void parse_wrappedCtrlLowercaseC_preservesCtrlAfterCloseTag() {
        KeyParser.ParsedKey pk = KeyParser.parse("<CTRL>c</CTRL>");
        assertEquals(6, pk.keyCode);
        assertEquals(0x01, pk.modifiers);
    }

    @Test
    public void parse_wrappedCtrlUppercaseC_includesShiftFromChar() {
        KeyParser.ParsedKey pk = KeyParser.parse("<CTRL>C</CTRL>");
        assertEquals(6, pk.keyCode);
        assertEquals(0x01 | 0x02, pk.modifiers);
    }

    @Test
    public void parse_wrappedShiftLowercaseC_preservesShift() {
        KeyParser.ParsedKey pk = KeyParser.parse("<SHIFT>c</SHIFT>");
        assertEquals(6, pk.keyCode);
        assertEquals(0x02, pk.modifiers);
    }

    @Test
    public void toLabel_windows_usesWinNotCmd() {
        int mods = 0x08 | 0x01;
        assertEquals("Win+Ctrl+C", KeyParser.toLabelForTargetOs(6, mods, "windows"));
    }

    @Test
    public void toLabel_macos_usesCmd() {
        int mods = 0x08 | 0x01;
        assertEquals("Cmd+Ctrl+C", KeyParser.toLabelForTargetOs(6, mods, "macos"));
    }

    @Test
    public void toLabel_linux_usesSuper() {
        int mods = 0x08;
        assertEquals("Super+C", KeyParser.toLabelForTargetOs(6, mods, "linux"));
    }

    @Test
    public void toLabel_hidGraveTilde_usage53_notKey53() {
        assertEquals("`", KeyParser.toLabelForTargetOs(53, 0, "macos"));
        assertEquals("~", KeyParser.toLabelForTargetOs(53, 0x02, "windows"));
    }

    @Test
    public void toLabel_hidGraveTilde_withCtrl_keepsBaseGlyph() {
        assertEquals("Ctrl+`", KeyParser.toLabelForTargetOs(53, 0x01, "macos"));
        assertEquals("Ctrl+Shift+`", KeyParser.toLabelForTargetOs(53, 0x01 | 0x02, "macos"));
    }

    @Test
    public void toLabel_shiftNineZero_areParentheses() {
        assertEquals("(", KeyParser.toLabelForTargetOs(0x26, 0x02, "macos"));
        assertEquals(")", KeyParser.toLabelForTargetOs(0x27, 0x02, "windows"));
    }

    @Test
    public void toLabel_nineZero_unshifted() {
        assertEquals("9", KeyParser.toLabelForTargetOs(0x26, 0, "macos"));
        assertEquals("0", KeyParser.toLabelForTargetOs(0x27, 0, "macos"));
    }
}
