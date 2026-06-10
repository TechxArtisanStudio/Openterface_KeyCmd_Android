package com.openterface.terminal;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for AnsiEscapeParser.
 * Verifies parsing of plain text, escape sequences, and CSI commands.
 */
public class AnsiEscapeParserTest {

    private AnsiEscapeParser parser;
    private TestCallback callback;

    @Before
    public void setUp() {
        parser = new AnsiEscapeParser();
        callback = new TestCallback();
    }

    // ── Plain Text ──────────────────────────────────────────────

    @Test
    public void plainText_charactersAreDelivered() {
        parser.parse("Hello".getBytes(), 5, callback);
        assertEquals("Hello", callback.chars.toString());
    }

    @Test
    public void plainText_newlineIsDelivered() {
        parser.parse("Hi\n".getBytes(), 3, callback);
        assertEquals("Hi", callback.chars.toString());
        assertEquals(1, callback.newlineCount);
    }

    @Test
    public void plainText_carriageReturnIsDelivered() {
        parser.parse("AB\rCD".getBytes(), 5, callback);
        assertEquals("ABCD", callback.chars.toString());
        assertEquals(1, callback.crCount);
    }

    @Test
    public void plainText_tabIsDelivered() {
        parser.parse("A\tB".getBytes(), 3, callback);
        assertEquals("AB", callback.chars.toString());
        assertEquals(1, callback.tabCount);
    }

    @Test
    public void plainText_backspaceIsDelivered() {
        parser.parse(new byte[]{'A', 'B', 0x7F, 'C'}, 4, callback);
        assertEquals("ABC", callback.chars.toString());
        assertEquals(1, callback.backspaceCount);
    }

    @Test
    public void plainText_controlCharsIgnored() {
        // NUL (0x00) should be ignored
        parser.parse(new byte[]{'A', 0x00, 'B'}, 3, callback);
        assertEquals("AB", callback.chars.toString());
    }

    // ── Single Escape Sequences ─────────────────────────────────

    @Test
    public void escapeIndex_scrollDown() {
        // ESC D = Index (scroll down 1)
        parser.parse(new byte[]{0x1B, 'D'}, 2, callback);
        assertEquals(1, callback.scrollLines);
    }

    @Test
    public void escapeReverseIndex_scrollUp() {
        // ESC M = Reverse Index (scroll up 1)
        parser.parse(new byte[]{0x1B, 'M'}, 2, callback);
        assertEquals(-1, callback.scrollLines);
    }

    @Test
    public void escapeNextLine_newlinePlusCR() {
        // ESC E = Next Line (NL + CR)
        parser.parse(new byte[]{0x1B, 'E'}, 2, callback);
        assertEquals(1, callback.newlineCount);
        assertEquals(1, callback.crCount);
    }

    @Test
    public void escapeReset_attributesReset() {
        // ESC c = Reset
        parser.parse(new byte[]{0x1B, 'c'}, 2, callback);
        assertEquals(1, callback.resetCount);
    }

    // ── CSI Cursor Movement ─────────────────────────────────────

    @Test
    public void csi_cursorUp() {
        // CSI A = Cursor Up 1
        parser.parse(new byte[]{0x1B, '[', 'A'}, 3, callback);
        assertEquals(-1, callback.cursorDeltaRow);
        assertEquals(0, callback.cursorDeltaCol);
    }

    @Test
    public void csi_cursorUpWithCount() {
        // CSI 3A = Cursor Up 3
        parser.parse(new byte[]{0x1B, '[', '3', 'A'}, 4, callback);
        assertEquals(-3, callback.cursorDeltaRow);
    }

    @Test
    public void csi_cursorDown() {
        parser.parse(new byte[]{0x1B, '[', 'B'}, 3, callback);
        assertEquals(1, callback.cursorDeltaRow);
    }

    @Test
    public void csi_cursorForward() {
        parser.parse(new byte[]{0x1B, '[', 'C'}, 3, callback);
        assertEquals(1, callback.cursorDeltaCol);
    }

    @Test
    public void csi_cursorBack() {
        parser.parse(new byte[]{0x1B, '[', 'D'}, 3, callback);
        assertEquals(-1, callback.cursorDeltaCol);
    }

    // ── CSI Cursor Position ─────────────────────────────────────

    @Test
    public void csi_cursorHome_defaultTo1_1() {
        // CSI H = cursor to 1,1 (0-based: 0,0)
        parser.parse(new byte[]{0x1B, '[', 'H'}, 3, callback);
        assertEquals(0, callback.cursorAbsoluteRow);
        assertEquals(0, callback.cursorAbsoluteCol);
    }

    @Test
    public void csi_cursorPosition_row5_col10() {
        // CSI 5;10H = row 5, col 10 (1-based) → 4,9 (0-based)
        parser.parse(new byte[]{0x1B, '[', '5', ';', '1', '0', 'H'}, 7, callback);
        assertEquals(4, callback.cursorAbsoluteRow);
        assertEquals(9, callback.cursorAbsoluteCol);
    }

    @Test
    public void csi_cursorPosition_altF() {
        // CSI 3;7f = alternate cursor position
        parser.parse(new byte[]{0x1B, '[', '3', ';', '7', 'f'}, 6, callback);
        assertEquals(2, callback.cursorAbsoluteRow);
        assertEquals(6, callback.cursorAbsoluteCol);
    }

    // ── CSI Erase ───────────────────────────────────────────────

    @Test
    public void csi_eraseLine_default() {
        parser.parse(new byte[]{0x1B, '[', 'K'}, 3, callback);
        assertEquals(1, callback.eraseLineCalls);
        assertEquals(0, callback.eraseLineMode);
    }

    @Test
    public void csi_eraseLine_toStart() {
        parser.parse(new byte[]{0x1B, '[', '1', 'K'}, 4, callback);
        assertEquals(1, callback.eraseLineCalls);
        assertEquals(1, callback.eraseLineMode);
    }

    @Test
    public void csi_eraseLine_entire() {
        parser.parse(new byte[]{0x1B, '[', '2', 'K'}, 4, callback);
        assertEquals(1, callback.eraseLineCalls);
        assertEquals(2, callback.eraseLineMode);
    }

    @Test
    public void csi_eraseScreen_default() {
        parser.parse(new byte[]{0x1B, '[', 'J'}, 3, callback);
        assertEquals(1, callback.eraseScreenCalls);
        assertEquals(0, callback.eraseScreenMode);
    }

    @Test
    public void csi_eraseScreen_entire() {
        parser.parse(new byte[]{0x1B, '[', '2', 'J'}, 4, callback);
        assertEquals(1, callback.eraseScreenCalls);
        assertEquals(2, callback.eraseScreenMode);
    }

    // ── CSI Graphic Rendition ───────────────────────────────────

    @Test
    public void csi_sgr_reset() {
        parser.parse(new byte[]{0x1B, '[', 'm'}, 3, callback);
        assertEquals(1, callback.sgrResetCount);
    }

    @Test
    public void csi_sgr_bold() {
        // CSI 1m = bold
        parser.parse(new byte[]{0x1B, '[', '1', 'm'}, 4, callback);
        assertEquals(0, callback.sgrResetCount);
        assertEquals(1, callback.sgrAttributes.size());
        assertEquals(Integer.valueOf(1), callback.sgrAttributes.get(0));
    }

    @Test
    public void csi_sgr_multiple() {
        // CSI 1;31m = bold + red foreground
        parser.parse(new byte[]{0x1B, '[', '1', ';', '3', '1', 'm'}, 7, callback);
        assertEquals(2, callback.sgrAttributes.size());
        assertEquals(Integer.valueOf(1), callback.sgrAttributes.get(0));
        assertEquals(Integer.valueOf(31), callback.sgrAttributes.get(1));
    }

    // ── Mixed Content ───────────────────────────────────────────

    @Test
    public void mixed_textAndCsiSequence() {
        // "Hello" + CSI 2J (erase screen) + "World\n"
        byte[] data = new byte[]{
            'H', 'e', 'l', 'l', 'o',
            0x1B, '[', '2', 'J',
            'W', 'o', 'r', 'l', 'd', '\n'
        };
        parser.parse(data, data.length, callback);
        assertEquals("HelloWorld", callback.chars.toString());
        assertEquals(1, callback.newlineCount);
        assertEquals(1, callback.eraseScreenCalls);
    }

    @Test
    public void mixed_cursorMovementFollowedByText() {
        // CSI 5;10H then "X"
        byte[] data = new byte[]{
            0x1B, '[', '5', ';', '1', '0', 'H',
            'X'
        };
        parser.parse(data, data.length, callback);
        assertEquals("X", callback.chars.toString());
        assertEquals(4, callback.cursorAbsoluteRow);
        assertEquals(9, callback.cursorAbsoluteCol);
    }

    // ── Edge Cases ──────────────────────────────────────────────

    @Test
    public void emptyData_noCallbacks() {
        parser.parse(new byte[0], 0, callback);
        assertEquals(0, callback.chars.length());
    }

    @Test
    public void escapedBracket_noMatch() {
        // ESC [ without final char — should be absorbed, no crash
        parser.parse(new byte[]{0x1B, '['}, 2, callback);
        // Incomplete CSI, no callback expected
        assertEquals(0, callback.cursorDeltaRow);
    }

    @Test
    public void escapeFollowedByNormalChar_resets() {
        // ESC x (unknown single escape) then "A"
        parser.parse(new byte[]{0x1B, 'x', 'A'}, 3, callback);
        assertEquals("A", callback.chars.toString());
        assertEquals(1, callback.unknownCount);
    }

    // ── Helper Test Callback ────────────────────────────────────

    private static class TestCallback implements AnsiEscapeParser.Callback {
        StringBuilder chars = new StringBuilder();
        int newlineCount, crCount, tabCount, backspaceCount;
        int cursorDeltaRow, cursorDeltaCol;
        int cursorAbsoluteRow = -1, cursorAbsoluteCol = -1;
        int eraseLineCalls, eraseLineMode;
        int eraseScreenCalls, eraseScreenMode;
        int sgrResetCount;
        List<Integer> sgrAttributes = new ArrayList<>();
        int scrollLines;
        int resetCount;
        int unknownCount;

        @Override public void onCharacter(char ch) { chars.append(ch); }
        @Override public void onNewline() { newlineCount++; }
        @Override public void onCarriageReturn() { crCount++; }
        @Override public void onTab() { tabCount++; }
        @Override public void onBackspace() { backspaceCount++; }
        @Override
        public void onCursorMove(int deltaRow, int deltaCol) {
            cursorDeltaRow += deltaRow;
            cursorDeltaCol += deltaCol;
        }
        @Override
        public void onCursorSet(int row, int col) {
            cursorAbsoluteRow = row;
            cursorAbsoluteCol = col;
        }
        @Override public void onEraseLine(int mode) { eraseLineCalls++; eraseLineMode = mode; }
        @Override public void onEraseScreen(int mode) { eraseScreenCalls++; eraseScreenMode = mode; }
        @Override public void onScroll(int lines) { scrollLines += lines; }
        @Override public void onSetAttribute(int code) { sgrAttributes.add(code); }
        @Override public void onResetAttributes() { sgrResetCount++; resetCount++; }
        @Override public void onUnknownSequence(String s) { unknownCount++; }
    }
}
