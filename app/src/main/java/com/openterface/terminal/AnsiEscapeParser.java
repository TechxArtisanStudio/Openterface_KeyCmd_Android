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

    private int state = STATE_NORMAL;
    private StringBuilder csiBuffer = new StringBuilder();

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
                } else if (ch == 0x1B) {
                    // ESC in OSC — might be ESC \ (ST)
                    state = STATE_ESCAPE;
                    // Check next byte for \
                } else {
                    csiBuffer.append((char) ch);
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
            case 'c': // Reset
                callback.onResetAttributes();
                break;
            default:
                callback.onUnknownSequence("ESC " + (char) ch);
        }
    }

    private void handleCsiSequence(String params, char finalChar, Callback callback) {
        String[] parts = params.isEmpty() ? new String[0] : params.split(";");

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
                    for (String part : parts) {
                        if (!part.isEmpty()) {
                            callback.onSetAttribute(parseIntOr0(part));
                        }
                    }
                }
                break;

            case 'n': // Device Status Report
                // Ignore for now
                break;

            case 's': // Save cursor position
                // Store current cursor position (Phase 4+)
                break;

            case 'u': // Restore cursor position
                // Restore cursor position (Phase 4+)
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
