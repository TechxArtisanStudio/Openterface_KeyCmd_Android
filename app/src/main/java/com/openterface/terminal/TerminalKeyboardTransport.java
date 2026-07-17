package com.openterface.terminal;

import android.util.Log;

import com.openterface.keymod.hid.KeyboardTransport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Terminal transport — converts HID key events to ANSI/xterm escape sequences
 * and writes them to the terminal's output stream (e.g. SSH channel stdin).
 *
 * <p><b>Design principle:</b> {@code KEY_MAP} only stores <em>unmodified</em>
 * key → byte-sequence mappings. Modifier handling (Ctrl, Shift, Alt) is done
 * at runtime in {@link #sendKey} because the same physical key produces
 * different output depending on the active modifiers.
 *
 * <p>HID usage codes are verified against project source code:
 * <ul>
 *   <li>F1–F12: 0x3A–0x45 (verified in KeyParser.java case 58–69)</li>
 *   <li>↑↓←→: 0x52/0x51/0x50/0x4F (verified in HIDSender.java case 82/81/80/79)</li>
 *   <li>Home/End: 0x4A/0x4D (verified in HIDSender.java case 74/77)</li>
 *   <li>PgUp/PgDn/Del: 0x4B/0x4E/0x4C (verified in KeyParser.java case 75/78/76)</li>
 * </ul>
 */
public class TerminalKeyboardTransport implements KeyboardTransport {

    private static final String TAG = "TerminalKbTransport";

    // Modifier bit masks (matches HID spec / KeyboardTransport contract)
    private static final int MOD_CTRL  = 0x01;
    private static final int MOD_SHIFT = 0x02;
    private static final int MOD_ALT   = 0x04;
    private static final int MOD_WIN   = 0x08;

    // HID usage code ranges
    private static final int HID_KEY_A = 0x04;
    private static final int HID_KEY_Z = 0x1D;
    private static final int HID_KEY_1 = 0x1E;
    private static final int HID_KEY_0 = 0x27;

    /**
     * Unmodified key → ANSI byte sequence.
     *
     * <p>Only keys that produce special sequences (F-keys, arrows, navigation,
     * control chars) are stored here. Plain letters/digits are computed
     * arithmetically in {@link #sendKey}.
     */
    private static final Map<Integer, byte[]> KEY_MAP = new HashMap<>();

    static {
        // ── F1–F12 (standard VT100 / xterm sequences) ──────────────────
        KEY_MAP.put(0x3A, new byte[]{0x1B, 'O', 'P'});                  // F1
        KEY_MAP.put(0x3B, new byte[]{0x1B, 'O', 'Q'});                  // F2
        KEY_MAP.put(0x3C, new byte[]{0x1B, 'O', 'R'});                  // F3
        KEY_MAP.put(0x3D, new byte[]{0x1B, 'O', 'S'});                  // F4
        KEY_MAP.put(0x3E, new byte[]{0x1B, '[', '1', '5', '~'});       // F5
        KEY_MAP.put(0x3F, new byte[]{0x1B, '[', '1', '7', '~'});       // F6
        KEY_MAP.put(0x40, new byte[]{0x1B, '[', '1', '8', '~'});       // F7
        KEY_MAP.put(0x41, new byte[]{0x1B, '[', '1', '9', '~'});       // F8
        KEY_MAP.put(0x42, new byte[]{0x1B, '[', '2', '0', '~'});       // F9
        KEY_MAP.put(0x43, new byte[]{0x1B, '[', '2', '1', '~'});       // F10
        KEY_MAP.put(0x44, new byte[]{0x1B, '[', '2', '3', '~'});       // F11
        KEY_MAP.put(0x45, new byte[]{0x1B, '[', '2', '4', '~'});       // F12

        // ── Arrow keys ─────────────────────────────────────────────────
        KEY_MAP.put(0x52, new byte[]{0x1B, '[', 'A'});                  // ↑
        KEY_MAP.put(0x51, new byte[]{0x1B, '[', 'B'});                  // ↓
        KEY_MAP.put(0x50, new byte[]{0x1B, '[', 'D'});                  // ←
        KEY_MAP.put(0x4F, new byte[]{0x1B, '[', 'C'});                  // →

        // ── Navigation keys ────────────────────────────────────────────
        KEY_MAP.put(0x4A, new byte[]{0x1B, '[', 'H'});                  // Home
        KEY_MAP.put(0x4D, new byte[]{0x1B, '[', 'F'});                  // End
        KEY_MAP.put(0x4B, new byte[]{0x1B, '[', '5', '~'});            // PgUp
        KEY_MAP.put(0x4E, new byte[]{0x1B, '[', '6', '~'});            // PgDn
        KEY_MAP.put(0x4C, new byte[]{0x1B, '[', '3', '~'});            // Delete

        // ── Control keys ───────────────────────────────────────────────
        KEY_MAP.put(0x28, new byte[]{0x0D});                            // Enter → CR
        KEY_MAP.put(0x29, new byte[]{0x1B});                            // Esc   → ESC
        KEY_MAP.put(0x2A, new byte[]{0x7F});                            // Backspace → DEL
        KEY_MAP.put(0x2B, new byte[]{0x09});                            // Tab   → HT
        KEY_MAP.put(0x2C, new byte[]{0x20});                            // Space → SP

        // ── Symbol keys (HID 0x2D–0x38, excluding 0x32) ─────────────
        KEY_MAP.put(0x2D, new byte[]{(byte) '-'});                      // - / _
        KEY_MAP.put(0x2E, new byte[]{(byte) '='});                      // = / +
        KEY_MAP.put(0x2F, new byte[]{(byte) '['});                      // [ / {
        KEY_MAP.put(0x30, new byte[]{(byte) ']'});                      // ] / }
        KEY_MAP.put(0x31, new byte[]{(byte) '\\'});                     // \ / |
        KEY_MAP.put(0x33, new byte[]{(byte) ';'});                      // ; / :
        KEY_MAP.put(0x34, new byte[]{(byte) '\''});                     // ' / "
        KEY_MAP.put(0x35, new byte[]{(byte) '`'});                      // ` / ~
        KEY_MAP.put(0x36, new byte[]{(byte) ','});                      // , / <
        KEY_MAP.put(0x37, new byte[]{(byte) '.'});                      // . / >
        KEY_MAP.put(0x38, new byte[]{(byte) '/'});                      // / / ?
    }

    /**
     * Shifted variants for symbol keys.
     * Used in {@link #sendKey} when Shift is held on a symbol key (HID 0x2D–0x38).
     */
    private static final Map<Integer, byte[]> SHIFTED_SYMBOLS = new HashMap<>();
    static {
        SHIFTED_SYMBOLS.put(0x2D, new byte[]{(byte) '_'});              // Shift+- → _
        SHIFTED_SYMBOLS.put(0x2E, new byte[]{(byte) '+'});              // Shift+= → +
        SHIFTED_SYMBOLS.put(0x2F, new byte[]{(byte) '{'});              // Shift+[ → {
        SHIFTED_SYMBOLS.put(0x30, new byte[]{(byte) '}'});              // Shift+] → }
        SHIFTED_SYMBOLS.put(0x31, new byte[]{(byte) '|'});              // Shift+\ → |
        SHIFTED_SYMBOLS.put(0x33, new byte[]{(byte) ':'});              // Shift+; → :
        SHIFTED_SYMBOLS.put(0x34, new byte[]{(byte) '"'});              // Shift+' → "
        SHIFTED_SYMBOLS.put(0x35, new byte[]{(byte) '~'});              // Shift+` → ~
        SHIFTED_SYMBOLS.put(0x36, new byte[]{(byte) '<'});              // Shift+, → <
        SHIFTED_SYMBOLS.put(0x37, new byte[]{(byte) '>'});              // Shift+. → >
        SHIFTED_SYMBOLS.put(0x38, new byte[]{(byte) '?'});              // Shift+/ → ?
    }

    /** The terminal's writable output (SSH channel stdin, PTY fd, etc.). Non-final so {@link #disconnect()} can clear it. */
    private OutputStream output;

    /**
     * Construct a terminal transport.
     *
     * @param output terminal output stream (SSH channel stdin, PTY fd, pipe).
     *               May be {@code null}; {@link #isConnected()} will return false.
     */
    public TerminalKeyboardTransport(OutputStream output) {
        this.output = output;
    }

    /**
     * Convert an HID key event to the appropriate terminal byte sequence.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Ctrl+Shift + letter → xterm modified key sequence (\e[&lt;code&gt;;6~)</li>
     *   <li>Ctrl + letter (HID 0x04–0x1D) → control character (0x01–0x1A)</li>
     *   <li>Shift + letter → uppercase ASCII (0x41–0x5A)</li>
     *   <li>Special keys + Ctrl/Shift → xterm modifier-encoded (\e[1;5A etc.)</li>
     *   <li>Alt + anything → ESC prefix + the key's normal output (Meta key)</li>
     *   <li>Plain letters/digits → raw ASCII</li>
     * </ol>
     *
     * @param modifierMask modifier mask (Ctrl=0x01, Shift=0x02, Alt=0x04, Win=0x08)
     * @param keyCode      HID usage code
     */
    @Override
    public void sendKey(int modifierMask, int keyCode) {
        boolean ctrl  = (modifierMask & MOD_CTRL)  != 0;
        boolean shift = (modifierMask & MOD_SHIFT) != 0;
        boolean alt   = (modifierMask & MOD_ALT)   != 0;

        byte[] payload;

        // ── 1. Letters (HID 0x04–0x1D) ────────────────────────────────
        if (keyCode >= HID_KEY_A && keyCode <= HID_KEY_Z) {
            if (ctrl && shift) {
                // xterm modified key: ESC[<code>;<mod>~  mod=6 (ctrl+shift)
                int param = keyCode - HID_KEY_A + 65;   // A=65, B=66, ...
                String seq = "[" + param + ";6~";
                payload = seq.getBytes(StandardCharsets.US_ASCII);
            } else if (ctrl) {
                // Ctrl+A(a)=0x01, Ctrl+B(b)=0x02, ... Ctrl+Z(z)=0x1A
                payload = new byte[]{(byte) (keyCode - HID_KEY_A + 0x01)};
            } else if (shift) {
                // Uppercase: A=0x41 … Z=0x5A
                payload = new byte[]{(byte) (keyCode - HID_KEY_A + 0x41)};
            } else {
                // Lowercase: a=0x61 … z=0x7A
                payload = new byte[]{(byte) (keyCode - HID_KEY_A + 0x61)};
            }
        }
        // ── 2. Digits (HID 0x1E–0x27) ─────────────────────────────────
        else if (keyCode >= HID_KEY_1 && keyCode <= HID_KEY_0) {
            // HID order: 1,2,3,...,9,0
            int digit;
            if (keyCode <= 0x26) {
                digit = keyCode - HID_KEY_1 + 1;     // 1–9
            } else {
                digit = 0;                            // 0 (HID 0x27 = '0')
            }
            payload = new byte[]{(byte) (digit + 0x30)};
        }
        // ── 3. Special keys → ANSI lookup ─────────────────────────────
        else {
            payload = KEY_MAP.get(keyCode);
            if (payload == null) {
                Log.v(TAG, "Unmapped key: 0x" + Integer.toHexString(keyCode)
                        + " mod=0x" + Integer.toHexString(modifierMask));
                return;
            }
            // Shift + symbol key → shifted ASCII (e.g. Shift+- → _)
            if (shift && SHIFTED_SYMBOLS.containsKey(keyCode)) {
                payload = SHIFTED_SYMBOLS.get(keyCode);
            }
            // Apply xterm modifier encoding for Ctrl/Shift on special keys
            else if (ctrl || shift) {
                payload = applyXtermModifier(payload, ctrl, shift);
            }
        }

        // ── 4. Alt prefix: ESC + the payload (Meta key) ───────────────
        if (alt) {
            byte[] prefixed = new byte[payload.length + 1];
            prefixed[0] = 0x1B;  // ESC
            System.arraycopy(payload, 0, prefixed, 1, payload.length);
            payload = prefixed;
        }

        write(payload);
    }

    @Override
    public void sendAllKeysReleased() {
        // No-op: terminals have no "key release" concept.
    }

    @Override
    public boolean isConnected() {
        return output != null;
    }

    /**
     * Disconnect the output stream. After this call, {@link #isConnected()} returns false
     * and all subsequent writes are silently discarded.
     *
     * <p>Called by {@code TerminalFragment.teardownTerminalKeyboard()} to ensure the transport
     * stops accepting writes after the SSH session is torn down.
     */
    public void disconnect() {
        output = null;
    }

    /**
     * Convert a special-key ANSI sequence to xterm modifier-encoded form.
     *
     * <p>xterm encodes modifiers as {@code ESC[<params>;<mod><final>} where
     * mod = 1 + 2*(shift) + 4*(ctrl) + 8*(alt).
     *
     * <p>Handles two sequence shapes:
     * <ul>
     *   <li>CSI letter:    ESC[X → ESC[1;{mod}X    (arrows, Home=ESC[H, End=ESC[F)</li>
     *   <li>CSI params ~:  ESC[X~ → ESC[X;{mod}~   (F5-F12, PgUp, PgDn, Del)</li>
     * </ul>
     *
     * <p>SS3 sequences (ESC O x, used by F1-F4) are not modified — xterm does not
     * parameterize SS3 sequences.
     */
    private byte[] applyXtermModifier(byte[] seq, boolean ctrl, boolean shift) {
        if (seq.length < 3 || seq[0] != 0x1B || seq[1] != '[') {
            return seq;  // Not a CSI sequence (e.g. SS3 for F1-F4) — return unchanged
        }

        int mod = 1;
        if (shift) mod += 2;
        if (ctrl)  mod += 4;
        if (mod == 1) return seq;  // No modifiers — unchanged

        byte last = seq[seq.length - 1];

        if (last == '~') {
            // CSI <params> ~  →  CSI <params> ; <mod> ~
            // e.g. \e[5~ → \e[5;5~
            String inner = new String(seq, 2, seq.length - 3, StandardCharsets.US_ASCII);
            String result = "[" + inner + ";" + mod + "~";
            return result.getBytes(StandardCharsets.US_ASCII);
        } else {
            // CSI <letter>  →  CSI 1 ; <mod> <letter>
            // e.g. \e[A → \e[1;5A
            char letter = (char) last;
            String result = "[1;" + mod + letter;
            return result.getBytes(StandardCharsets.US_ASCII);
        }
    }

    /**
     * Write bytes to the terminal output stream.
     *
     * <p>Failures are logged but not thrown — keyboard input is best-effort.
     * The terminal session will surface connection problems through its own
     * error reporting rather than via keyboard callbacks.
     *
     * <p>Note: flush() is intentionally NOT called here. The actual flush happens
     * in the {@code KeySender} lambda inside {@code SshClient.startShell()}, which
     * writes directly to JSch's ChannelOutputStream and flushes it.
     */
    private void write(byte[] bytes) {
        if (output == null) {
            return;
        }
        try {
            output.write(bytes);
        } catch (IOException e) {
            Log.e(TAG, "Write failed: " + e.getMessage());
        }
    }
}
