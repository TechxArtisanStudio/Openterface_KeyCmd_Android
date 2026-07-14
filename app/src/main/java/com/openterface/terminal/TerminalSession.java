package com.openterface.terminal;

import android.util.Log;

import com.openterface.keymod.BuildConfig;

/**
 * Terminal session state: screen buffer, cursor, scrollback.
 * Implements VT100/ANSI escape sequence processing with color support.
 * Features: 8/16/256/true-color, alternate screen buffer, scroll regions, saved cursor, DEC modes.
 */
public class TerminalSession {

    private final int scrollbackSize;
    private char[][] screen;       // visible screen (or alt screen)
    private CellAttribute[][] attrs; // per-cell attributes
    private CellAttribute currentAttr; // current attribute for new chars
    private int cursorX, cursorY;
    private java.util.List<char[]> scrollback;
    private java.util.List<CellAttribute[]> scrollbackAttrs;

    // Mutable dimensions for dynamic growth
    private int currentRows, currentCols;

    // Alternate screen buffer
    private char[][] altScreen;
    private CellAttribute[][] altAttrs;
    private int savedCursorX, savedCursorY;
    private CellAttribute savedAttr;
    private boolean usingAltScreen = false;

    // Primary screen backup (for when alt screen is active)
    private char[][] mainScreen;
    private CellAttribute[][] mainAttrs;

    // Saved cursor position
    private int savedCursorXPos, savedCursorYPos;
    private CellAttribute savedCursorAttr;
    private boolean hasSavedCursor = false;

    // Scroll region
    private int scrollTop = 0;
    private int scrollBottom = -1; // -1 means currentRows-1

    // DEC private modes
    private boolean cursorVisible = true;
    private boolean applicationCursorKeys = false;
    private boolean lineWrap = true;

    // Pending line wrap state (cursor at right margin, waiting for next char)
    private boolean pendingWrap = false;

    // ANSI parser
    private final AnsiEscapeParser ansiParser = new AnsiEscapeParser();

    // Callback for sending keystrokes to remote
    public interface KeySender { void send(byte[] data); }
    private KeySender keySender;

    // Callback for sending responses back to remote
    public interface ResponseSender { void send(byte[] data); }
    private ResponseSender responseSender;

    public TerminalSession(int rows, int cols, int scrollbackSize) {
        this.scrollbackSize = scrollbackSize;
        this.currentRows = rows;
        this.currentCols = cols;
        this.scrollback = new java.util.ArrayList<>();
        this.scrollbackAttrs = new java.util.ArrayList<>();
        this.currentAttr = new CellAttribute();
        this.scrollBottom = rows - 1;
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
            @Override public void onSetFg256(int index) {}
            @Override public void onSetBg256(int index) {}
            @Override public void onSetFgTrueColor(int r, int g, int b) {}
            @Override public void onSetBgTrueColor(int r, int g, int b) {}
            @Override public void onResetAttributes() {}
            @Override public void onDecModeSet(int mode) {}
            @Override public void onDecModeReset(int mode) {}
            @Override public void onDeviceStatusReport(int code) {}
            @Override public void onDeviceAttributesRequest() {}
            @Override public void onSaveCursor() {}
            @Override public void onRestoreCursor() {}
            @Override public void onSetScrollRegion(int top, int bottom) {}
            @Override public void onUnknownSequence(String sequence) {}
        });
    }

    private void clearScreen() {
        screen = new char[currentRows][currentCols];
        attrs = new CellAttribute[currentRows][currentCols];
        cursorX = 0;
        cursorY = 0;
        CellAttribute defaultAttr = new CellAttribute();
        for (int r = 0; r < currentRows; r++) {
            java.util.Arrays.fill(screen[r], ' ');
            for (int c = 0; c < currentCols; c++) {
                attrs[r][c] = defaultAttr.copy();
            }
        }
    }

    /**
     * Process incoming data from the remote side.
     * Parses ANSI escape sequences and updates screen state.
     */
    public synchronized void append(byte[] data, int len) {
        if (BuildConfig.DEBUG && len > 0) {
            StringBuilder rawHex = new StringBuilder();
            for (int i = 0; i < Math.min(len, 200); i++) {
                int b = data[i] & 0xFF;
                if (b == 0x1B) rawHex.append("\\e");
                else if (b >= 0x20 && b < 0x7F) rawHex.append((char)b);
                else rawHex.append(String.format("\\x%02X", b));
            }
            Log.v("TerminalSession", "RAW[" + len + "]: " + rawHex.toString());
        }

        ansiParser.parse(data, len, new AnsiEscapeParser.Callback() {
            @Override
            public void onCharacter(char ch) {
                writeChar(ch);
            }

            @Override
            public void onNewline() {
                cursorX = 0;
                cursorY++;
                pendingWrap = false;
                int bottomLimit = (scrollBottom == -1) ? currentRows : scrollBottom + 1;
                if (cursorY >= bottomLimit) {
                    scrollUp();
                    cursorY = bottomLimit - 1;
                }
            }

            @Override
            public void onCarriageReturn() {
                cursorX = 0;
                pendingWrap = false;
            }

            @Override
            public void onTab() {
                // Advance to next 8-column tab stop
                cursorX = ((cursorX / 8) + 1) * 8;
                if (cursorX >= currentCols) {
                    cursorX = currentCols - 1;
                }
                pendingWrap = false;
            }

            @Override
            public void onBackspace() {
                if (cursorX > 0) cursorX--;
                pendingWrap = false;
            }

            @Override
            public void onCursorMove(int deltaRow, int deltaCol) {
                // Relative cursor movement (CSI A/B/C/D)
                int bottomLimit = (scrollBottom == -1) ? currentRows - 1 : scrollBottom;
                cursorY = Math.max(scrollTop, Math.min(bottomLimit, cursorY + deltaRow));
                cursorX = Math.max(0, Math.min(currentCols - 1, cursorX + deltaCol));
                pendingWrap = false;
            }

            @Override
            public void onCursorSet(int row, int col) {
                // Absolute cursor position (CSI H/f)
                cursorY = Math.max(0, Math.min(currentRows - 1, row));
                cursorX = Math.max(0, Math.min(currentCols - 1, col));
                pendingWrap = false;
            }

            @Override
            public void onEraseLine(int mode) {
                CellAttribute eraseAttr = currentAttr.copy();
                switch (mode) {
                    case 0: // cursor to end
                        java.util.Arrays.fill(screen[cursorY], cursorX, currentCols, ' ');
                        for (int c = cursorX; c < currentCols; c++) {
                            attrs[cursorY][c] = eraseAttr.copy();
                        }
                        break;
                    case 1: // start to cursor
                        java.util.Arrays.fill(screen[cursorY], 0, cursorX + 1, ' ');
                        for (int c = 0; c <= cursorX; c++) {
                            attrs[cursorY][c] = eraseAttr.copy();
                        }
                        break;
                    case 2: // entire line
                        java.util.Arrays.fill(screen[cursorY], ' ');
                        for (int c = 0; c < currentCols; c++) {
                            attrs[cursorY][c] = eraseAttr.copy();
                        }
                        break;
                }
            }

            @Override
            public void onEraseScreen(int mode) {
                CellAttribute eraseAttr = currentAttr.copy();
                switch (mode) {
                    case 0: // cursor to end
                        java.util.Arrays.fill(screen[cursorY], cursorX, currentCols, ' ');
                        for (int c = cursorX; c < currentCols; c++) {
                            attrs[cursorY][c] = eraseAttr.copy();
                        }
                        for (int r = cursorY + 1; r < currentRows; r++) {
                            java.util.Arrays.fill(screen[r], ' ');
                            for (int c = 0; c < currentCols; c++) {
                                attrs[r][c] = eraseAttr.copy();
                            }
                        }
                        break;
                    case 1: // start to cursor
                        java.util.Arrays.fill(screen[cursorY], 0, cursorX + 1, ' ');
                        for (int c = 0; c <= cursorX; c++) {
                            attrs[cursorY][c] = eraseAttr.copy();
                        }
                        for (int r = 0; r < cursorY; r++) {
                            java.util.Arrays.fill(screen[r], ' ');
                            for (int c = 0; c < currentCols; c++) {
                                attrs[r][c] = eraseAttr.copy();
                            }
                        }
                        break;
                    case 2: // entire screen
                        for (int r = 0; r < currentRows; r++) {
                            java.util.Arrays.fill(screen[r], ' ');
                            for (int c = 0; c < currentCols; c++) {
                                attrs[r][c] = eraseAttr.copy();
                            }
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
                applySgr(code);
            }

            @Override
            public void onSetFg256(int index) {
                currentAttr.fgColor = CellAttribute.color256(index);
            }

            @Override
            public void onSetBg256(int index) {
                currentAttr.bgColor = CellAttribute.color256(index);
            }

            @Override
            public void onSetFgTrueColor(int r, int g, int b) {
                currentAttr.fgColor = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            }

            @Override
            public void onSetBgTrueColor(int r, int g, int b) {
                currentAttr.bgColor = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            }

            @Override
            public void onResetAttributes() {
                currentAttr.reset();
            }

            @Override
            public void onDecModeSet(int mode) {
                handleDecMode(mode, true);
            }

            @Override
            public void onDecModeReset(int mode) {
                handleDecMode(mode, false);
            }

            @Override
            public void onDeviceStatusReport(int code) {
                if (code == 6 && responseSender != null) {
                    // Cursor Position Report (CPR): ESC [ row ; col R
                    int reportX = pendingWrap ? currentCols - 1 : cursorX;
                    String response = "\033[" + (cursorY + 1) + ";" + (reportX + 1) + "R";
                    responseSender.send(response.getBytes());
                }
            }

            @Override
            public void onDeviceAttributesRequest() {
                if (responseSender != null) {
                    // DA1 response: ESC [ ? 6 2 ; c (VT220-compatible)
                    responseSender.send("\033[?62;c".getBytes());
                }
            }

            @Override
            public void onSaveCursor() {
                savedCursorXPos = cursorX;
                savedCursorYPos = cursorY;
                savedCursorAttr = currentAttr.copy();
                hasSavedCursor = true;
            }

            @Override
            public void onRestoreCursor() {
                if (hasSavedCursor) {
                    cursorX = savedCursorXPos;
                    cursorY = savedCursorYPos;
                    currentAttr.copyFrom(savedCursorAttr);
                }
            }

            @Override
            public void onSetScrollRegion(int top, int bottom) {
                scrollTop = Math.max(0, Math.min(currentRows - 1, top));
                scrollBottom = (bottom == -1) ? currentRows - 1 : Math.max(0, Math.min(currentRows - 1, bottom));
                if (scrollTop > scrollBottom) {
                    // Invalid region, reset to full screen
                    scrollTop = 0;
                    scrollBottom = currentRows - 1;
                }
                // Cursor to home position after setting scroll region
                cursorX = 0;
                cursorY = 0;
            }

            @Override
            public void onUnknownSequence(String sequence) {
                // Don't log sequence content - could contain sensitive data from remote session
            }
        });
    }

    /** Handle DEC private mode set/reset */
    private void handleDecMode(int mode, boolean set) {
        // Don't log DEC mode details to avoid potential information leakage
        switch (mode) {
            case 1: // Application cursor keys
                applicationCursorKeys = set;
                break;
            case 7: // Line wrap
                lineWrap = set;
                break;
            case 25: // Cursor visibility
                cursorVisible = set;
                break;
            case 47: // Alternate screen buffer (old variant)
            case 1047: // Alternate screen buffer
                if (set && !usingAltScreen) {
                    // Switch to alt screen
                    mainScreen = screen;
                    mainAttrs = attrs;
                    if (altScreen == null) {
                        altScreen = new char[currentRows][currentCols];
                        altAttrs = new CellAttribute[currentRows][currentCols];
                    }
                    // Always clear alt screen before use
                    CellAttribute defaultAttr = new CellAttribute();
                    for (int r = 0; r < currentRows; r++) {
                        java.util.Arrays.fill(altScreen[r], ' ');
                        for (int c = 0; c < currentCols; c++) {
                            altAttrs[r][c] = defaultAttr.copy();
                        }
                    }
                    screen = altScreen;
                    attrs = altAttrs;
                    usingAltScreen = true;
                    // Save cursor
                    savedCursorXPos = cursorX;
                    savedCursorYPos = cursorY;
                    savedCursorAttr = currentAttr.copy();
                    // Reset cursor and pending wrap
                    cursorX = 0;
                    cursorY = 0;
                    pendingWrap = false;
                } else if (!set && usingAltScreen) {
                    // Restore main screen
                    screen = mainScreen;
                    attrs = mainAttrs;
                    usingAltScreen = false;
                    // Restore cursor
                    cursorX = savedCursorXPos;
                    cursorY = savedCursorYPos;
                    currentAttr.copyFrom(savedCursorAttr);
                }
                break;
            case 1049: // Alternate screen buffer (with cursor save/restore)
                if (set && !usingAltScreen) {
                    // Switch to alt screen
                    mainScreen = screen;
                    mainAttrs = attrs;
                    if (altScreen == null) {
                        altScreen = new char[currentRows][currentCols];
                        altAttrs = new CellAttribute[currentRows][currentCols];
                    }
                    // Always clear alt screen before use
                    CellAttribute defaultAttr = new CellAttribute();
                    for (int r = 0; r < currentRows; r++) {
                        java.util.Arrays.fill(altScreen[r], ' ');
                        for (int c = 0; c < currentCols; c++) {
                            altAttrs[r][c] = defaultAttr.copy();
                        }
                    }
                    screen = altScreen;
                    attrs = altAttrs;
                    usingAltScreen = true;
                    // Save cursor
                    savedCursorXPos = cursorX;
                    savedCursorYPos = cursorY;
                    savedCursorAttr = currentAttr.copy();
                    // Reset cursor and pending wrap
                    cursorX = 0;
                    cursorY = 0;
                    pendingWrap = false;
                } else if (!set && usingAltScreen) {
                    // Restore main screen
                    screen = mainScreen;
                    attrs = mainAttrs;
                    usingAltScreen = false;
                    // Restore cursor
                    cursorX = savedCursorXPos;
                    cursorY = savedCursorYPos;
                    currentAttr.copyFrom(savedCursorAttr);
                }
                break;
            // Other modes: ignore for now
            // 12: cursor blink
            // 1000-1006: mouse tracking
            // 2004: bracketed paste
        }
    }

    /** Apply a simple SGR code to currentAttr */
    private void applySgr(int code) {
        if (code == 0) {
            currentAttr.reset();
        } else if (code == 1) {
            currentAttr.bold = true;
        } else if (code == 3) {
            currentAttr.italic = true;
        } else if (code == 4) {
            currentAttr.underline = true;
        } else if (code == 5 || code == 6) {
            currentAttr.blink = true;
        } else if (code == 7) {
            currentAttr.inverse = true;
        } else if (code == 22) {
            currentAttr.bold = false;
        } else if (code == 23) {
            currentAttr.italic = false;
        } else if (code == 24) {
            currentAttr.underline = false;
        } else if (code == 25) {
            currentAttr.blink = false;
        } else if (code == 27) {
            currentAttr.inverse = false;
        } else if (code >= 30 && code <= 37) {
            currentAttr.fgColor = CellAttribute.color256(code - 30);
        } else if (code == 39) {
            currentAttr.fgColor = CellAttribute.DEFAULT_FG;
        } else if (code >= 40 && code <= 47) {
            currentAttr.bgColor = CellAttribute.color256(code - 40 + 0); // 40-47 → index 0-7
        } else if (code == 49) {
            currentAttr.bgColor = CellAttribute.DEFAULT_BG;
        } else if (code >= 90 && code <= 97) {
            currentAttr.fgColor = CellAttribute.color256(code - 90 + 8); // bright fg
        } else if (code >= 100 && code <= 107) {
            currentAttr.bgColor = CellAttribute.color256(code - 100 + 8); // bright bg
        }
    }

    /** Write a single printable character at the cursor position. */
    private void writeChar(char ch) {
        if (pendingWrap) {
            // Perform deferred line wrap
            cursorX = 0;
            cursorY++;
            int bottomLimit = (scrollBottom == -1) ? currentRows : scrollBottom + 1;
            if (cursorY >= bottomLimit) {
                scrollUp();
                cursorY = bottomLimit - 1;
            }
            pendingWrap = false;
        }
        if (cursorX >= 0 && cursorX < currentCols && cursorY >= 0 && cursorY < currentRows) {
            screen[cursorY][cursorX] = ch;
            attrs[cursorY][cursorX] = currentAttr.copy();

            // Handle wide (CJK) characters that occupy 2 columns
            int charWidth = getCharDisplayWidth(ch);
            if (charWidth == 2) {
                // Clear the next cell to avoid overlap
                if (cursorX + 1 < currentCols) {
                    screen[cursorY][cursorX + 1] = ' ';
                    attrs[cursorY][cursorX + 1] = currentAttr.copy();
                }
                cursorX += 2;
            } else {
                cursorX++;
            }

            if (cursorX >= currentCols) {
                // Don't wrap yet — defer to next character
                pendingWrap = true;
            }
        }
    }

    /**
     * Determine the display width of a character.
     * CJK characters and other wide characters occupy 2 columns in terminal.
     */
    private int getCharDisplayWidth(char ch) {
        // Common CJK ranges that are double-width in terminals
        if (ch >= 0x1100 && ch <= 0x115F) return 2; // Hangul Jamo
        if (ch >= 0x2E80 && ch <= 0x303E) return 2; // CJK Radicals Supplement, etc.
        if (ch >= 0x3040 && ch <= 0x33BF) return 2; // Japanese, Korean, CJK Compatibility
        if (ch >= 0x3400 && ch <= 0x4DBF) return 2; // CJK Unified Ideographs Extension A
        if (ch >= 0x4E00 && ch <= 0x9FFF) return 2; // CJK Unified Ideographs
        if (ch >= 0xA000 && ch <= 0xA4CF) return 2; // Yi Syllables
        if (ch >= 0xAC00 && ch <= 0xD7AF) return 2; // Hangul Syllables
        if (ch >= 0xF900 && ch <= 0xFAFF) return 2; // CJK Compatibility Ideographs
        if (ch >= 0xFE30 && ch <= 0xFE6F) return 2; // CJK Compatibility Forms
        if (ch >= 0xFF01 && ch <= 0xFF60) return 2; // Fullwidth Forms
        if (ch >= 0xFFE0 && ch <= 0xFFE6) return 2; // Fullwidth Signs
        return 1; // Default: single width
    }

    private void scrollUp() {
        // Save top line to scrollback (only for main screen)
        if (!usingAltScreen && scrollbackSize > 0 && scrollTop == 0) {
            scrollback.add(screen[0].clone());
            scrollbackAttrs.add(attrs[0].clone());
            while (scrollback.size() > scrollbackSize) {
                scrollback.remove(0);
                scrollbackAttrs.remove(0);
            }
        }
        int bottomLimit = (scrollBottom == -1) ? currentRows - 1 : scrollBottom;
        // Shift lines up within scroll region
        for (int r = scrollTop; r < bottomLimit; r++) {
            System.arraycopy(screen[r + 1], 0, screen[r], 0, currentCols);
            System.arraycopy(attrs[r + 1], 0, attrs[r], 0, currentCols);
        }
        // Clear bottom line of scroll region
        CellAttribute clearAttr = new CellAttribute();
        java.util.Arrays.fill(screen[bottomLimit], ' ');
        for (int c = 0; c < currentCols; c++) {
            attrs[bottomLimit][c] = clearAttr.copy();
        }
    }

    private void scrollDown() {
        int bottomLimit = (scrollBottom == -1) ? currentRows - 1 : scrollBottom;
        // Shift lines down within scroll region
        for (int r = bottomLimit; r > scrollTop; r--) {
            System.arraycopy(screen[r - 1], 0, screen[r], 0, currentCols);
            System.arraycopy(attrs[r - 1], 0, attrs[r], 0, currentCols);
        }
        // Clear top line of scroll region
        CellAttribute clearAttr = new CellAttribute();
        java.util.Arrays.fill(screen[scrollTop], ' ');
        for (int c = 0; c < currentCols; c++) {
            attrs[scrollTop][c] = clearAttr.copy();
        }
    }

    public synchronized void onKeyInput(byte[] data) {
        if (keySender != null) {
            keySender.send(data);
        }
    }

    public synchronized void setKeySender(KeySender sender) { this.keySender = sender; }

    public synchronized void setResponseSender(ResponseSender sender) { this.responseSender = sender; }

    /**
     * Returns the character array for the given row.
     * WARNING: Returns a direct reference to the internal screen buffer.
     * Callers must NOT modify the returned array. The reference is only safe
     * while holding the session lock (e.g., inside a synchronized(session) block).
     */
    public synchronized char[] getLineChars(int row) {
        if (row < 0 || row >= currentRows) return null;
        return screen[row];
    }

    /**
     * Returns the cell attribute array for the given row.
     * WARNING: Returns a direct reference to the internal attrs buffer.
     * Callers must NOT modify the returned array. The reference is only safe
     * while holding the session lock (e.g., inside a synchronized(session) block).
     */
    public synchronized CellAttribute[] getLineAttrs(int row) {
        if (row < 0 || row >= currentRows) return null;
        return attrs[row];
    }

    public synchronized char[] getScrollbackLineChars(int scrollbackIndex) {
        if (scrollbackIndex < 0 || scrollbackIndex >= scrollback.size()) return null;
        return scrollback.get(scrollbackIndex);
    }

    public synchronized CellAttribute[] getScrollbackLineAttrs(int scrollbackIndex) {
        if (scrollbackIndex < 0 || scrollbackIndex >= scrollbackAttrs.size()) return null;
        return scrollbackAttrs.get(scrollbackIndex);
    }

    public synchronized int getScrollbackSize() { return scrollback.size(); }

    public synchronized int getCursorX() { return cursorX; }
    public synchronized int getCursorY() { return cursorY; }
    public synchronized int getColumns() { return currentCols; }
    public synchronized int getRows() { return currentRows; }

    /**
     * Dynamically grow the terminal buffer if needed (thread-safe, only grows).
     * Called from TerminalView.onSizeChanged when view needs more rows/cols.
     */
    public synchronized void growIfNeeded(int minRows, int minCols) {
        if (minRows <= currentRows && minCols <= currentCols) return;

        int newRows = Math.max(currentRows, minRows);
        int newCols = Math.max(currentCols, minCols);

        // Save old dimensions for scroll region check
        int oldRows = currentRows;

        char[][] newScreen = new char[newRows][newCols];
        CellAttribute[][] newAttrs = new CellAttribute[newRows][newCols];
        CellAttribute defaultAttr = new CellAttribute();

        for (int r = 0; r < newRows; r++) {
            java.util.Arrays.fill(newScreen[r], ' ');
            for (int c = 0; c < newCols; c++) {
                newAttrs[r][c] = defaultAttr.copy();
            }
        }

        // Copy existing content
        int copyRows = Math.min(currentRows, newRows);
        int copyCols = Math.min(currentCols, newCols);
        for (int r = 0; r < copyRows; r++) {
            System.arraycopy(screen[r], 0, newScreen[r], 0, copyCols);
            System.arraycopy(attrs[r], 0, newAttrs[r], 0, copyCols);
        }

        screen = newScreen;
        attrs = newAttrs;
        currentRows = newRows;
        currentCols = newCols;

        // Also grow backup buffers if they exist (to prevent ArrayIndexOutOfBoundsException
        // when switching to alt screen after resize). Setting to null forces reallocation
        // on next use, which loses alt screen content but is safe.
        mainScreen = null;
        mainAttrs = null;
        altScreen = null;
        altAttrs = null;

        // Adjust cursor if needed
        cursorX = Math.min(cursorX, newCols - 1);
        cursorY = Math.min(cursorY, newRows - 1);

        // Update scroll region only if it was at default full-screen (use old dimensions)
        if (scrollTop == 0 && scrollBottom == oldRows - 1) {
            scrollBottom = newRows - 1;
        }
        // Boundary protection: ensure scroll region doesn't exceed new dimensions
        scrollTop = Math.min(scrollTop, newRows - 1);
        scrollBottom = Math.min(scrollBottom, newRows - 1);
    }

    public synchronized boolean isCursorVisible() { return cursorVisible; }
    public synchronized boolean isApplicationCursorKeys() { return applicationCursorKeys; }
    public synchronized boolean isUsingAltScreen() { return usingAltScreen; }
}
