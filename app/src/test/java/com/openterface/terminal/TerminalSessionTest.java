package com.openterface.terminal;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TerminalSession.
 * Verifies screen buffer operations, cursor movement, scrollback, and ANSI processing.
 */
public class TerminalSessionTest {

    private TerminalSession session;

    @Before
    public void setUp() {
        session = new TerminalSession(10, 20, 100);
    }

    // ── Dimensions ──────────────────────────────────────────────

    @Test
    public void dimensions_returnsConfiguredValues() {
        assertEquals(10, session.getRows());
        assertEquals(20, session.getColumns());
    }

    @Test
    public void initialCursor_atOrigin() {
        assertEquals(0, session.getCursorX());
        assertEquals(0, session.getCursorY());
    }

    // ── Character Output ────────────────────────────────────────

    @Test
    public void appendPlainText_writesToBuffer() {
        session.append("Hello".getBytes(), 5);
        char[] line = session.getLineChars(0);
        assertEquals('H', line[0]);
        assertEquals('e', line[1]);
        assertEquals('l', line[2]);
        assertEquals('l', line[3]);
        assertEquals('o', line[4]);
        // Rest of line should be spaces
        assertEquals(' ', line[5]);
    }

    @Test
    public void appendPlainText_advancesCursor() {
        session.append("ABC".getBytes(), 3);
        assertEquals(3, session.getCursorX());
        assertEquals(0, session.getCursorY());
    }

    @Test
    public void appendNewline_movesToNextLine() {
        session.append("ABC\n".getBytes(), 4);
        assertEquals(0, session.getCursorX());
        assertEquals(1, session.getCursorY());
    }

    @Test
    public void appendCarriageReturn_resetsColumn() {
        session.append("ABC\rD".getBytes(), 5);
        assertEquals(1, session.getCursorX()); // D overwrites A
        assertEquals(0, session.getCursorY());
        char[] line = session.getLineChars(0);
        assertEquals('D', line[0]);
        assertEquals('B', line[1]);
        assertEquals('C', line[2]);
    }

    @Test
    public void appendTab_advancesToNextTabStop() {
        session.append("\t".getBytes(), 1);
        assertEquals(8, session.getCursorX());
    }

    @Test
    public void appendTab_fromNonZero() {
        session.append("AB\t".getBytes(), 3);
        assertEquals(8, session.getCursorX());
    }

    @Test
    public void appendBackspace_movesCursorBack() {
        session.append(new byte[]{'A', 'B', 'C', 0x7F}, 4);
        assertEquals(2, session.getCursorX());
    }

    @Test
    public void appendBackspace_atColumnZero_doesNotMove() {
        session.append(new byte[]{0x7F}, 1);
        assertEquals(0, session.getCursorX());
    }

    // ── Line Wrap ───────────────────────────────────────────────

    @Test
    public void lineWrap_movesToNextLine() {
        // Write exactly 20 chars (full line), next char wraps
        byte[] data = new byte[21];
        for (int i = 0; i < 20; i++) data[i] = 'A';
        data[20] = 'B';
        session.append(data, 21);
        assertEquals(1, session.getCursorX());
        assertEquals(1, session.getCursorY());
        assertEquals('B', session.getLineChars(1)[0]);
    }

    // ── Scrolling ───────────────────────────────────────────────

    @Test
    public void scrollUp_whenBeyondLastRow() {
        // Fill 9 rows with distinct content (19 chars + \n), last row without \n
        for (int i = 0; i < 9; i++) {
            char label = (char) ('A' + i);
            byte[] line = new byte[20];
            java.util.Arrays.fill(line, 0, 19, (byte) label);
            line[19] = '\n';
            session.append(line, 20);
        }
        // Row 0='A', Row 8='I', cursor at row 9, col 0
        assertEquals(9, session.getCursorY());
        assertEquals('A', session.getLineChars(0)[0]);
        assertEquals('I', session.getLineChars(8)[0]);

        // Fill the 10th row with Z's (no \n, so no scroll)
        byte[] bLine = new byte[19];
        java.util.Arrays.fill(bLine, 0, 19, (byte) 'Z');
        session.append(bLine, 19);

        assertEquals(9, session.getCursorY());
        assertEquals('Z', session.getLineChars(9)[0]);

        // Now add \n to trigger scroll — 'A' scrolls off
        session.append("\n".getBytes(), 1);

        assertEquals(9, session.getCursorY());
        assertEquals('B', session.getLineChars(0)[0]);
        // Z's scrolled off, bottom line cleared
        assertEquals(' ', session.getLineChars(9)[0]);
    }

    // ── CSI Cursor Movement ─────────────────────────────────────

    @Test
    public void csi_cursorUp() {
        // Move to row 3: \n advances, "B" writes, \n advances again
        session.append("A\nB\nC\n".getBytes(), 6);
        assertEquals(3, session.getCursorY());
        session.append("\033[A".getBytes(), 3); // CSI A = cursor up 1
        assertEquals(2, session.getCursorY());
    }

    @Test
    public void csi_cursorDown() {
        session.append("\033[B".getBytes(), 3); // CSI B = cursor down
        assertEquals(1, session.getCursorY());
    }

    @Test
    public void csi_cursorForward() {
        session.append("\033[5C".getBytes(), 4); // CSI 5C = cursor forward 5
        assertEquals(5, session.getCursorX());
    }

    @Test
    public void csi_cursorBack() {
        session.append("AAAAA".getBytes(), 5);
        assertEquals(5, session.getCursorX());
        session.append("\033[2D".getBytes(), 4); // CSI 2D = cursor back 2
        assertEquals(3, session.getCursorX());
    }

    @Test
    public void csi_cursorPosition() {
        session.append("\033[5;10H".getBytes(), 7); // CSI 5;10H
        assertEquals(4, session.getCursorY()); // 1-based → 0-based
        assertEquals(9, session.getCursorX());
    }

    // ── CSI Erase ───────────────────────────────────────────────

    @Test
    public void csi_eraseLine_toEnd() {
        session.append("Hello World".getBytes(), 11);
        session.append("\r\033[0K".getBytes(), 5); // CR + erase to end
        char[] line = session.getLineChars(0);
        assertEquals(' ', line[0]); // erased
        assertEquals(' ', line[10]); // erased
    }

    @Test
    public void csi_eraseLine_entire() {
        session.append("Hello World".getBytes(), 11);
        session.append("\033[2K".getBytes(), 4); // erase entire line
        char[] line = session.getLineChars(0);
        for (int i = 0; i < 11; i++) {
            assertEquals(' ', line[i]);
        }
    }

    @Test
    public void csi_eraseScreen_toEnd() {
        session.append("Line0\nLine1\nLine2".getBytes(), 17);
        // Move cursor to row 2, col 0 (1-based: 2;1)
        session.append("\033[2;1H".getBytes(), 6); // CSI 2;1H → row 1, col 0
        assertEquals(1, session.getCursorY());
        assertEquals(0, session.getCursorX());
        session.append("\033[0J".getBytes(), 4); // erase from cursor to end
        // Row 0 should be intact (above cursor)
        assertEquals('L', session.getLineChars(0)[0]);
        // Row 1+ should be erased
        assertEquals(' ', session.getLineChars(1)[0]);
        assertEquals(' ', session.getLineChars(2)[0]);
    }

    @Test
    public void csi_eraseScreen_entire() {
        session.append("Hello\nWorld".getBytes(), 11);
        session.append("\033[2J".getBytes(), 4); // erase entire screen
        assertEquals(' ', session.getLineChars(0)[0]);
        assertEquals(' ', session.getLineChars(1)[0]);
        assertEquals(0, session.getCursorX());
        assertEquals(0, session.getCursorY());
    }

    // ── Key Input ───────────────────────────────────────────────

    @Test
    public void keyInput_forwardsToSender() {
        final byte[][] received = {null};
        session.setKeySender(data -> received[0] = data);
        byte[] keyData = "test".getBytes();
        session.onKeyInput(keyData);
        assertNotNull(received[0]);
        assertArrayEquals(keyData, received[0]);
    }

    @Test
    public void keyInput_noSender_doesNotCrash() {
        // No sender set — should not throw
        session.onKeyInput("test".getBytes());
    }

    // ── Edge Cases ──────────────────────────────────────────────

    @Test
    public void cursorClamped_toBounds() {
        session.append("\033[100;100H".getBytes(), 10);
        assertEquals(9, session.getCursorY());  // max row
        assertEquals(19, session.getCursorX()); // max col
    }

    @Test
    public void nullLineAccess_returnsNull() {
        assertNull(session.getLineChars(-1));
        assertNull(session.getLineChars(10));
    }

    @Test
    public void emptyAppend_doesNothing() {
        session.append(new byte[0], 0);
        assertEquals(0, session.getCursorX());
        assertEquals(0, session.getCursorY());
    }
}
