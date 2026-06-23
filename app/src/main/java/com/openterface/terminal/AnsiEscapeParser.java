package com.openterface.terminal;

/**
 * Parses ANSI/VT100 escape sequences from incoming byte streams.
 * Phase 1: Minimal parser handling basic sequences.
 * Phase 4+: Full CSI/DCS/OSC sequence support with colors.
 */
public class AnsiEscapeParser {

    // Parser states
    private static final int STATE_NORMAL = 0;
    private static final int STATE_ESCAPE = 1;
    private static final int STATE_CSI = 2;
    private static final int STATE_OSC = 3;
    private static final int STATE_OSC_ESC = 4;

    private int state = STATE_NORMAL;
    private StringBuilder csiBuffer = new StringBuilder();

    // UTF-8 multi-byte decoding
    private int utf8BytesRemaining = 0;
    private int utf8Codepoint = 0;

    public interface Callback {
        void onCharacter(char ch);
        void onNewline();
        void onCarriageReturn();
        void onTab();
        void onBackspace();
        /** Relative cursor offset (from CSI A/B/C/D). Positive=down/right, negative=up/left. */
        void onCursorMove(int deltaRow, int deltaCol);
        /** Absolute cursor position (from CSI H/f), 0-based. */
        void onCursorSet(int row, int col);
        void onEraseLine(int mode);
        void onEraseScreen(int mode);
        void onScroll(int lines);
        void onSetAttribute(int code);
        void onResetAttributes();
        /** 256-color foreground (SGR 38;5;n) */
        void onSetFg256(int index);
        /** 256-color background (SGR 48;5;n) */
        void onSetBg256(int index);
        /** 24-bit true color foreground (SGR 38;2;r;g;b) */
        void onSetFgTrueColor(int r, int g, int b);
        /** 24-bit true color background (SGR 48;2;r;g;b) */
        void onSetBgTrueColor(int r, int g, int b);
        /** DEC private mode set (CSI ?mode h) e.g. ?1049h (alt screen), ?25h (show cursor) */
        void onDecModeSet(int mode);
        /** DEC private mode reset (CSI ?mode l) */
        void onDecModeReset(int mode);
        /** Device Status Report requested — terminal should respond */
        void onDeviceStatusReport(int code);
        /** Device Attributes requested — terminal should respond */
        void onDeviceAttributesRequest();
        /** Save cursor position (ESC 7 or CSI s) */
        void onSaveCursor();
        /** Restore cursor position (ESC 8 or CSI u) */
        void onRestoreCursor();
        /** Set scroll region: top and bottom rows (bottom=-1 means rows-1) */
        void onSetScrollRegion(int top, int bottom);
        void onUnknownSequence(String sequence);
    }

    /**
     * Feed data into the parser. Callbacks are invoked for each parsed element.
     */
    public void parse(byte[] data, int len, Callback callback) {
        for (int i = 0; i < len; i++) {
            processByte(data[i], callback);
        }
    }

    private void processByte(byte b, Callback callback) {
        int ch = b & 0xFF;

        // Handle ongoing UTF-8 multi-byte sequence (before any state check)
        if (utf8BytesRemaining > 0) {
            if ((ch & 0xC0) == 0x80) {
                // Continuation byte
                utf8Codepoint = (utf8Codepoint << 6) | (ch & 0x3F);
                utf8BytesRemaining--;
                if (utf8BytesRemaining == 0) {
                    // Decoded a full codepoint — emit it
                    if (utf8Codepoint <= 0xFFFF) {
                        callback.onCharacter((char) utf8Codepoint);
                    } else {
                        // Supplementary plane — emit as surrogate pair
                        int sp = utf8Codepoint - 0x10000;
                        callback.onCharacter((char) (0xD800 | (sp >> 10)));
                        callback.onCharacter((char) (0xDC00 | (sp & 0x3FF)));
                    }
                }
            } else {
                // Invalid UTF-8 — discard and re-process this byte
                utf8BytesRemaining = 0;
                processByte(b, callback);
            }
            return;
        }

        // If byte looks like a UTF-8 multi-byte start, begin decoding
        if (ch >= 0xC0 && ch < 0xF8) {
            if (ch >= 0xF0) {
                // 4-byte sequence: 11110xxx
                utf8Codepoint = ch & 0x07;
                utf8BytesRemaining = 3;
            } else if (ch >= 0xE0) {
                // 3-byte sequence: 1110xxxx
                utf8Codepoint = ch & 0x0F;
                utf8BytesRemaining = 2;
            } else {
                // 2-byte sequence: 110xxxxx
                utf8Codepoint = ch & 0x1F;
                utf8BytesRemaining = 1;
            }
            return;
        }

        switch (state) {
            case STATE_NORMAL:
                if (ch == 0x1B) { // ESC
                    state = STATE_ESCAPE;
                    csiBuffer.setLength(0);
                } else if (ch == '\n') {
                    callback.onNewline();
                } else if (ch == '\r') {
                    callback.onCarriageReturn();
                } else if (ch == '\t') {
                    callback.onTab();
                } else if (ch == 0x7F || ch == 0x08) { // DEL or BS
                    callback.onBackspace();
                } else if (ch >= 0x20 && ch < 0x7F) {
                    callback.onCharacter((char) ch);
                }
                // Control characters 0x00-0x1F (except \n, \r, \t) are ignored
                break;

            case STATE_ESCAPE:
                if (ch == '[') {
                    state = STATE_CSI;
                    csiBuffer.setLength(0);
                } else if (ch == ']') {
                    state = STATE_OSC;
                    csiBuffer.setLength(0);
                } else if (ch == '(' || ch == ')') {
                    // Character set selection — ignore for now
                    state = STATE_NORMAL;
                } else if (ch >= 0x40 && ch <= 0x7E) {
                    // Single escape sequence (e.g., ESC D = index, ESC M = reverse index)
                    handleSingleEscape(ch, callback);
                    state = STATE_NORMAL;
                } else {
                    state = STATE_NORMAL;
                }
                break;

            case STATE_CSI:
                if (ch >= 0x40 && ch <= 0x7E) {
                    // Final byte — process CSI sequence
                    handleCsiSequence(csiBuffer.toString(), (char) ch, callback);
                    state = STATE_NORMAL;
                } else if (ch == 0x1B) {
                    // ESC inside CSI — reset
                    state = STATE_ESCAPE;
                    csiBuffer.setLength(0);
                } else {
                    csiBuffer.append((char) ch);
                }
                break;

            case STATE_OSC:
                if (ch == 0x07) { // BEL terminates OSC
                    state = STATE_NORMAL;
                    csiBuffer.setLength(0);
                } else if (ch == 0x1B) {
                    // ESC in OSC — look for ESC \ (ST)
                    // Mark that we need next byte to be \ to properly terminate
                    state = STATE_OSC_ESC;
                } else {
                    csiBuffer.append((char) ch);
                }
                break;

            case STATE_OSC_ESC:
                // We consumed ESC inside OSC. If next byte is \, it's ST (String Terminator).
                // Otherwise, treat this ESC as a normal ESC and handle the current byte.
                if (ch == '\\') {
                    state = STATE_NORMAL;
                    csiBuffer.setLength(0);
                } else {
                    // ESC was consumed. Now handle current byte as if we're in STATE_ESCAPE.
                    csiBuffer.setLength(0); // Clear OSC data
                    if (ch == '[') {
                        state = STATE_CSI;
                    } else if (ch == ']') {
                        state = STATE_OSC;
                    } else if (ch == '(' || ch == ')') {
                        state = STATE_NORMAL;
                    } else if (ch >= 0x40 && ch <= 0x7E) {
                        handleSingleEscape(ch, callback);
                        state = STATE_NORMAL;
                    } else {
                        state = STATE_NORMAL;
                    }
                }
                break;
        }
    }

    private void handleSingleEscape(int ch, Callback callback) {
        switch (ch) {
            case 'D': // Index (scroll down)
                callback.onScroll(1);
                break;
            case 'M': // Reverse Index (scroll up)
                callback.onScroll(-1);
                break;
            case 'E': // Next Line
                callback.onNewline();
                callback.onCarriageReturn();
                break;
            case '7': // Save cursor (DEC)
                callback.onSaveCursor();
                break;
            case '8': // Restore cursor (DEC)
                callback.onRestoreCursor();
                break;
            case 'c': // Reset
                callback.onResetAttributes();
                break;
            default:
                callback.onUnknownSequence("ESC " + (char) ch);
        }
    }

    private void handleCsiSequence(String params, char finalChar, Callback callback) {
        String[] parts = params.isEmpty() ? new String[0] : params.split(";");

        // Check for '?' private mode prefix (stripped since '?' < 0x40 and ends up in params)
        boolean decPrivate = params.startsWith("?");
        if (decPrivate) {
            // Strip '?' prefix, split into modes
            String modeParams = params.substring(1);
            if (!modeParams.isEmpty()) {
                String[] modes = modeParams.split(";");
                for (String modeStr : modes) {
                    int mode = parseIntOr0(modeStr);
                    if (finalChar == 'h') {
                        callback.onDecModeSet(mode);
                    } else if (finalChar == 'l') {
                        callback.onDecModeReset(mode);
                    }
                }
            }
            return;
        }

        switch (finalChar) {
            case 'A': // Cursor Up
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    int n = parseIntOr1(parts[0]);
                    callback.onCursorMove(-n, 0);
                } else {
                    callback.onCursorMove(-1, 0);
                }
                break;

            case 'B': // Cursor Down
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    int n = parseIntOr1(parts[0]);
                    callback.onCursorMove(n, 0);
                } else {
                    callback.onCursorMove(1, 0);
                }
                break;

            case 'C': // Cursor Forward
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    int n = parseIntOr1(parts[0]);
                    callback.onCursorMove(0, n);
                } else {
                    callback.onCursorMove(0, 1);
                }
                break;

            case 'D': // Cursor Back
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    int n = parseIntOr1(parts[0]);
                    callback.onCursorMove(0, -n);
                } else {
                    callback.onCursorMove(0, -1);
                }
                break;

            case 'H': // Cursor Position (1-based)
            case 'f': // Cursor Position (alternate)
                // VT100: 0 means 1 (default). Clamp to >= 1 before subtracting.
                int rawRow = (parts.length > 0 && !parts[0].isEmpty()) ? parseIntOr1(parts[0]) : 1;
                int rawCol = (parts.length > 1 && !parts[1].isEmpty()) ? parseIntOr1(parts[1]) : 1;
                int row = Math.max(1, rawRow) - 1;
                int col = Math.max(1, rawCol) - 1;
                callback.onCursorSet(row, col);
                break;

            case 'J': // Erase in Display
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    callback.onEraseScreen(parseIntOr0(parts[0]));
                } else {
                    callback.onEraseScreen(0);
                }
                break;

            case 'K': // Erase in Line
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    callback.onEraseLine(parseIntOr0(parts[0]));
                } else {
                    callback.onEraseLine(0);
                }
                break;

            case 'm': // Select Graphic Rendition (colors/attributes)
                if (parts.length == 0 || (parts.length == 1 && parts[0].isEmpty())) {
                    callback.onResetAttributes();
                } else {
                    int i = 0;
                    while (i < parts.length) {
                        if (parts[i].isEmpty()) {
                            i++;
                            continue;
                        }
                        int code = parseIntOr0(parts[i]);

                        // Check for 256-color or true-color sub-sequences
                        if ((code == 38 || code == 48) && i + 1 < parts.length) {
                            int subMode = parseIntOr0(parts[i + 1]);
                            if (subMode == 5 && i + 2 < parts.length) {
                                // 256-color: \e[38;5;n or \e[48;5;n
                                int colorIndex = parseIntOr0(parts[i + 2]);
                                if (code == 38) {
                                    callback.onSetFg256(colorIndex);
                                } else {
                                    callback.onSetBg256(colorIndex);
                                }
                                i += 3;
                                continue;
                            } else if (subMode == 2 && i + 4 < parts.length) {
                                // True color: \e[38;2;r;g;b or \e[48;2;r;g;b
                                int r = parseIntOr0(parts[i + 2]);
                                int g = parseIntOr0(parts[i + 3]);
                                int b = parseIntOr0(parts[i + 4]);
                                if (code == 38) {
                                    callback.onSetFgTrueColor(r, g, b);
                                } else {
                                    callback.onSetBgTrueColor(r, g, b);
                                }
                                i += 5;
                                continue;
                            }
                        }

                        // Simple SGR code
                        callback.onSetAttribute(code);
                        i++;
                    }
                }
                break;

            case 'r': // Set scroll region (CSI top;bottom r)
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    int top = parseIntOr1(parts[0]) - 1;
                    int bottom = parseIntOr1(parts[1]) - 1;
                    callback.onSetScrollRegion(top, bottom);
                } else {
                    // Default: entire screen
                    callback.onSetScrollRegion(0, -1);
                }
                break;

            case 'n': // Device Status Report
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    callback.onDeviceStatusReport(parseIntOr0(parts[0]));
                } else {
                    callback.onDeviceStatusReport(6); // default DSR
                }
                break;

            case 'c': // Device Attributes (DA1: ESC [ c, DA2: ESC [ > c)
                if (params.startsWith(">")) {
                    // DA2 — respond with ESC[>0;0;0c
                    callback.onUnknownSequence("CSI >" + params.substring(1) + "c (DA2)");
                } else {
                    callback.onDeviceAttributesRequest();
                }
                break;

            case 's': // Save cursor position
                callback.onSaveCursor();
                break;

            case 'u': // Restore cursor position
                callback.onRestoreCursor();
                break;

            case 'h': // Standard mode set (non-private)
                // Fall through — these are typically SM modes, ignore for now
                break;

            case 'l': // Standard mode reset (non-private)
                // Fall through — these are typically RM modes, ignore for now
                break;

            case '?': // Private mode sequences (e.g., ?25h show cursor)
                // Ignore for now
                break;

            default:
                callback.onUnknownSequence("CSI " + params + finalChar);
        }
    }

    private int parseIntOr1(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private int parseIntOr0(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
