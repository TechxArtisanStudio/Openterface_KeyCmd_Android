package com.openterface.terminal;

/**
 * Terminal session state: screen buffer, cursor, scrollback.
 * Implements basic VT100/ANSI escape sequence processing.
 *
 * Phase 1: Minimal parser with basic cursor movement and character output.
 * Phase 4+: Full color, scroll regions, scrollback, attributes.
 */
public class TerminalSession {

    private final int rows, cols, scrollbackSize;
    private char[][] screen;       // visible screen
    private int cursorX, cursorY;
    private java.util.List<char[]> scrollback;

    // ANSI parser
    private final AnsiEscapeParser ansiParser = new AnsiEscapeParser();

    // Callback for sending keystrokes to remote
    public interface KeySender { void send(byte[] data); }
    private KeySender keySender;

    public TerminalSession(int rows, int cols, int scrollbackSize) {
        this.rows = rows;
        this.cols = cols;
        this.scrollbackSize = scrollbackSize;
        this.scrollback = new java.util.ArrayList<>();
        clearScreen();

        // Set up ANSI parser callback (constructor dummy — real callbacks in append())
        ansiParser.parse(new byte[0], 0, new AnsiEscapeParser.Callback() {
            @Override public void onCharacter(char ch) {}
            @Override public void onNewline() {}
            @Override public void onCarriageReturn() {}
            @Override public void onTab() {}
            @Override public void onBackspace() {}
            @Override public void onCursorMove(int dr, int dc) {}
            @Override public void onCursorSet(int row, int col) {}
            @Override public void onEraseLine(int mode) {}
            @Override public void onEraseScreen(int mode) {}
            @Override public void onScroll(int lines) {}
            @Override public void onSetAttribute(int code) {}
            @Override public void onResetAttributes() {}
            @Override public void onUnknownSequence(String sequence) {}
        });
    }

    private void clearScreen() {
        screen = new char[rows][cols];
        cursorX = 0;
        cursorY = 0;
        for (int r = 0; r < rows; r++) {
            java.util.Arrays.fill(screen[r], ' ');
        }
    }

    /**
     * Process incoming data from the remote side.
     * Parses ANSI escape sequences and updates screen state.
     */
    public void append(byte[] data, int len) {
        ansiParser.parse(data, len, new AnsiEscapeParser.Callback() {
            @Override
            public void onCharacter(char ch) {
                writeChar(ch);
            }

            @Override
            public void onNewline() {
                cursorX = 0;
                cursorY++;
                if (cursorY >= rows) {
                    scrollUp();
                    cursorY = rows - 1;
                }
            }

            @Override
            public void onCarriageReturn() {
                cursorX = 0;
            }

            @Override
            public void onTab() {
                // Advance to next 8-column tab stop
                cursorX = ((cursorX / 8) + 1) * 8;
                if (cursorX >= cols) {
                    cursorX = cols - 1;
                }
            }

            @Override
            public void onBackspace() {
                if (cursorX > 0) cursorX--;
            }

            @Override
            public void onCursorMove(int deltaRow, int deltaCol) {
                // Relative cursor movement (CSI A/B/C/D)
                cursorY = Math.max(0, Math.min(rows - 1, cursorY + deltaRow));
                cursorX = Math.max(0, Math.min(cols - 1, cursorX + deltaCol));
            }

            @Override
            public void onCursorSet(int row, int col) {
                // Absolute cursor position (CSI H/f)
                cursorY = Math.max(0, Math.min(rows - 1, row));
                cursorX = Math.max(0, Math.min(cols - 1, col));
            }

            @Override
            public void onEraseLine(int mode) {
                switch (mode) {
                    case 0: // cursor to end
                        java.util.Arrays.fill(screen[cursorY], cursorX, cols, ' ');
                        break;
                    case 1: // start to cursor
                        java.util.Arrays.fill(screen[cursorY], 0, cursorX + 1, ' ');
                        break;
                    case 2: // entire line
                        java.util.Arrays.fill(screen[cursorY], ' ');
                        break;
                }
            }

            @Override
            public void onEraseScreen(int mode) {
                switch (mode) {
                    case 0: // cursor to end
                        java.util.Arrays.fill(screen[cursorY], cursorX, cols, ' ');
                        for (int r = cursorY + 1; r < rows; r++) {
                            java.util.Arrays.fill(screen[r], ' ');
                        }
                        break;
                    case 1: // start to cursor
                        java.util.Arrays.fill(screen[cursorY], 0, cursorX + 1, ' ');
                        for (int r = 0; r < cursorY; r++) {
                            java.util.Arrays.fill(screen[r], ' ');
                        }
                        break;
                    case 2: // entire screen
                        for (int r = 0; r < rows; r++) {
                            java.util.Arrays.fill(screen[r], ' ');
                        }
                        cursorX = 0;
                        cursorY = 0;
                        break;
                }
            }

            @Override
            public void onScroll(int lines) {
                if (lines > 0) {
                    for (int i = 0; i < lines; i++) scrollUp();
                } else {
                    for (int i = 0; i < -lines; i++) scrollDown();
                }
            }

            @Override
            public void onSetAttribute(int code) {
                // Phase 4+: track color/attribute state
            }

            @Override
            public void onResetAttributes() {
                // Phase 4+: reset color/attribute state
            }

            @Override
            public void onUnknownSequence(String sequence) {
                // Log unknown sequences for debugging (Phase 4+)
            }
        });
    }

    /** Write a single printable character at the cursor position. */
    private void writeChar(char ch) {
        if (cursorX >= cols) {
            // Line wrap
            cursorX = 0;
            cursorY++;
            if (cursorY >= rows) {
                scrollUp();
                cursorY = rows - 1;
            }
        }
        if (cursorX >= 0 && cursorX < cols && cursorY >= 0 && cursorY < rows) {
            screen[cursorY][cursorX] = ch;
            cursorX++;
        }
    }

    private void scrollUp() {
        // Save top line to scrollback
        if (scrollbackSize > 0) {
            scrollback.add(screen[0].clone());
            while (scrollback.size() > scrollbackSize) {
                scrollback.remove(0);
            }
        }
        // Shift lines up
        for (int r = 0; r < rows - 1; r++) {
            System.arraycopy(screen[r + 1], 0, screen[r], 0, cols);
        }
        // Clear bottom line
        java.util.Arrays.fill(screen[rows - 1], ' ');
    }

    private void scrollDown() {
        // Save bottom line to scrollback
        if (scrollbackSize > 0) {
            scrollback.add(screen[rows - 1].clone());
            while (scrollback.size() > scrollbackSize) {
                scrollback.remove(0);
            }
        }
        // Shift lines down
        for (int r = rows - 1; r > 0; r--) {
            System.arraycopy(screen[r - 1], 0, screen[r], 0, cols);
        }
        // Clear top line
        java.util.Arrays.fill(screen[0], ' ');
    }

    public void onKeyInput(byte[] data) {
        if (keySender != null) {
            keySender.send(data);
        }
    }

    public void setKeySender(KeySender sender) { this.keySender = sender; }

    public char[] getLineChars(int row) {
        if (row < 0 || row >= rows) return null;
        return screen[row];
    }

    public int getCursorX() { return cursorX; }
    public int getCursorY() { return cursorY; }
    public int getColumns() { return cols; }
    public int getRows() { return rows; }
}
