package com.openterface.keymod.util;

import android.util.Log;

import androidx.annotation.Nullable;

import com.openterface.keymod.ConnectionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends plain text (and optional &lt;TAG&gt; tokens) as HID keystrokes via {@link ConnectionManager}.
 * Used by Voice Input and Compose modes.
 */
public final class HidTextKeystrokeSender {

    private static final String TAG = "HidTextKeystrokeSender";

    public enum Result {
        COMPLETED,
        CANCELLED,
    }

    /** Invoked from the send thread after each completed send unit (see {@link #countSendUnits}). */
    @FunctionalInterface
    public interface SendProgressListener {
        /**
         * @param completed send units finished so far, in range {@code 1..total}
         * @param total from {@link #countSendUnits}; matches completed at end of a full send
         */
        void onProgress(int completed, int total);
    }

    private HidTextKeystrokeSender() {}

    /**
     * Counts HID send units for {@link #send} with the same {@code text}, {@code allowUnicode}, and {@code
     * targetOs}. Modifier tags, closing tags, and delay tokens contribute {@code 0}. Each special key token
     * (e.g. {@code <ENTER>}) is one unit; each ASCII code point with a HID mapping is one unit; skipped
     * non-ASCII when {@code allowUnicode} is false contributes {@code 0}; each Unicode code point when
     * {@code allowUnicode} is true contributes one unit (one OS-specific sequence).
     */
    public static int countSendUnits(String text, boolean allowUnicode, String targetOs) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(targetOs, "targetOs");
        List<String> tokens = tokenizeInput(text);
        int activeMods = 0;
        int units = 0;
        for (String token : tokens) {
            if (token.startsWith("</") && token.endsWith(">")) {
                activeMods = 0;
                continue;
            }
            switch (token) {
                case "<CTRL>":
                    activeMods |= 0x01;
                    continue;
                case "<SHIFT>":
                    activeMods |= 0x02;
                    continue;
                case "<ALT>":
                    activeMods |= 0x04;
                    continue;
                case "<CMD>":
                case "<WIN>":
                    activeMods |= 0x08;
                    continue;
                default:
                    break;
            }
            if (token.equals("<DELAY1S>")
                    || token.equals("<DELAY2S>")
                    || token.equals("<DELAY5S>")
                    || token.equals("<DELAY10S>")) {
                continue;
            }
            int specialHid = specialTokenToHidCode(token);
            if (specialHid > 0) {
                units++;
                continue;
            }
            for (int ci = 0; ci < token.length(); ) {
                int cp = token.codePointAt(ci);
                ci += Character.charCount(cp);
                if (cp > 0x7E) {
                    if (!allowUnicode) {
                        continue;
                    }
                    units++;
                } else {
                    char c = (char) cp;
                    int hidCode = mapCharToHidCode(c);
                    if (hidCode < 0) {
                        continue;
                    }
                    units++;
                }
            }
        }
        return units;
    }

    private static void reportProgress(
            @Nullable SendProgressListener progress, int totalUnits, int[] completedHolder) {
        if (progress == null || totalUnits <= 0) {
            return;
        }
        // Cap so UI never sees completed > total if send/count logic ever diverges.
        int next = Math.min(completedHolder[0] + 1, totalUnits);
        completedHolder[0] = next;
        progress.onProgress(next, totalUnits);
    }

    /**
     * @param cancel if non-null, {@link AtomicBoolean#get()} is polled between steps; true aborts after key release.
     * @param allowUnicode when false, code points {@code > 0x7E} are skipped (logged).
     */
    public static Result send(
            String text,
            ConnectionManager connectionManager,
            String targetOs,
            boolean allowUnicode,
            @Nullable AtomicBoolean cancel
    ) throws InterruptedException {
        return send(text, connectionManager, targetOs, allowUnicode, cancel, null);
    }

    /**
     * Same as {@link #send(String, ConnectionManager, String, boolean, AtomicBoolean)} with optional progress
     * after each send unit (see {@link #countSendUnits}). Callbacks run on the caller thread.
     */
    public static Result send(
            String text,
            ConnectionManager connectionManager,
            String targetOs,
            boolean allowUnicode,
            @Nullable AtomicBoolean cancel,
            @Nullable SendProgressListener progress
    ) throws InterruptedException {
        List<String> tokens = tokenizeInput(text);
        int activeMods = 0;
        final int totalUnits = progress != null ? countSendUnits(text, allowUnicode, targetOs) : 0;
        final int[] completed = progress != null ? new int[] {0} : null;

        for (String token : tokens) {
            if (isCancelled(cancel, connectionManager)) {
                return Result.CANCELLED;
            }

            if (token.startsWith("</") && token.endsWith(">")) {
                activeMods = 0;
                continue;
            }

            switch (token) {
                case "<CTRL>":
                    activeMods |= 0x01;
                    continue;
                case "<SHIFT>":
                    activeMods |= 0x02;
                    continue;
                case "<ALT>":
                    activeMods |= 0x04;
                    continue;
                case "<CMD>":
                case "<WIN>":
                    activeMods |= 0x08;
                    continue;
            }

            if (token.equals("<DELAY1S>")) {
                Thread.sleep(1000);
                continue;
            }
            if (token.equals("<DELAY2S>")) {
                Thread.sleep(2000);
                continue;
            }
            if (token.equals("<DELAY5S>")) {
                Thread.sleep(5000);
                continue;
            }
            if (token.equals("<DELAY10S>")) {
                Thread.sleep(10000);
                continue;
            }

            int specialHid = specialTokenToHidCode(token);
            if (specialHid > 0) {
                connectionManager.sendKeyEvent(activeMods, specialHid);
                Thread.sleep(30);
                connectionManager.sendKeyRelease();
                Thread.sleep(10);
                reportProgress(progress, totalUnits, completed);
                continue;
            }

            for (int ci = 0; ci < token.length(); ) {
                if (isCancelled(cancel, connectionManager)) {
                    return Result.CANCELLED;
                }
                int cp = token.codePointAt(ci);
                ci += Character.charCount(cp);

                if (cp > 0x7E) {
                    if (!allowUnicode) {
                        Log.w(TAG, "Skipping non-ASCII code point U+" + Integer.toHexString(cp));
                        continue;
                    }
                    switch (targetOs) {
                        case "windows":
                            sendUnicodeCharWindows(cp, connectionManager);
                            break;
                        case "linux":
                            sendUnicodeCharLinux(cp, connectionManager);
                            break;
                        default:
                            sendUnicodeCharMacOS(cp, connectionManager);
                            break;
                    }
                    reportProgress(progress, totalUnits, completed);
                } else {
                    char c = (char) cp;
                    int hidCode = mapCharToHidCode(c);
                    if (hidCode < 0) {
                        Log.w(TAG, "No HID code for char: '" + c + "' (U+" + Integer.toHexString(cp) + ")");
                        continue;
                    }
                    int mods = (activeMods != 0) ? activeMods : (needsShift(c) ? 0x02 : 0x00);
                    connectionManager.sendKeyEvent(mods, hidCode);
                    Thread.sleep(30);
                    connectionManager.sendKeyRelease();
                    Thread.sleep(10);
                    reportProgress(progress, totalUnits, completed);
                }
            }
        }
        return Result.COMPLETED;
    }

    private static boolean isCancelled(@Nullable AtomicBoolean cancel, ConnectionManager cm)
            throws InterruptedException {
        if (cancel == null || !cancel.get()) {
            return false;
        }
        cm.sendKeyRelease();
        Thread.sleep(20);
        return true;
    }

    public static List<String> tokenizeInput(String text) {
        Pattern p = Pattern.compile("</?[A-Z0-9]+>|[\\s\\S]");
        Matcher m = p.matcher(text);
        List<String> tokens = new ArrayList<>();
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    public static int specialTokenToHidCode(String token) {
        if (!token.startsWith("<") || !token.endsWith(">") || token.startsWith("</")) {
            return -1;
        }
        String content = token.substring(1, token.length() - 1).toUpperCase(Locale.ROOT);
        switch (content) {
            case "ENTER":
                return 0x28;
            case "ESC":
                return 0x29;
            case "BACK":
            case "BACKSPACE":
                return 0x2A;
            case "TAB":
                return 0x2B;
            case "SPACE":
                return 0x2C;
            case "RIGHT":
                return 0x4F;
            case "LEFT":
                return 0x50;
            case "DOWN":
                return 0x51;
            case "UP":
                return 0x52;
            case "HOME":
                return 0x4A;
            case "END":
                return 0x4D;
            case "PAGEUP":
            case "PGUP":
                return 0x4B;
            case "PAGEDOWN":
            case "PGDN":
                return 0x4E;
            case "INSERT":
                return 0x49;
            case "DELETE":
            case "DEL":
                return 0x4C;
            case "F1":
                return 0x3A;
            case "F2":
                return 0x3B;
            case "F3":
                return 0x3C;
            case "F4":
                return 0x3D;
            case "F5":
                return 0x3E;
            case "F6":
                return 0x3F;
            case "F7":
                return 0x40;
            case "F8":
                return 0x41;
            case "F9":
                return 0x42;
            case "F10":
                return 0x43;
            case "F11":
                return 0x44;
            case "F12":
                return 0x45;
            default:
                return -1;
        }
    }

    public static int mapCharToHidCode(char c) {
        if (c >= 'a' && c <= 'z') {
            return 4 + (c - 'a');
        }
        if (c >= 'A' && c <= 'Z') {
            return 4 + (c - 'A');
        }
        if (c >= '1' && c <= '9') {
            return 30 + (c - '1');
        }
        if (c == '0') {
            return 39;
        }
        switch (c) {
            case ' ':
                return 44;
            case '\n':
                return 40;
            case '\t':
                return 43;
            case '-':
            case '_':
                return 45;
            case '=':
            case '+':
                return 46;
            case '[':
            case '{':
                return 47;
            case ']':
            case '}':
                return 48;
            case '\\':
            case '|':
                return 49;
            case ';':
            case ':':
                return 51;
            case '\'':
            case '"':
                return 52;
            case ',':
            case '<':
                return 54;
            case '.':
            case '>':
                return 55;
            case '/':
            case '?':
                return 56;
            case '`':
            case '~':
                return 53;
            case '!':
                return 30;
            case '@':
                return 31;
            case '#':
                return 32;
            case '$':
                return 33;
            case '%':
                return 34;
            case '^':
                return 35;
            case '&':
                return 36;
            case '*':
                return 37;
            case '(':
                return 38;
            case ')':
                return 39;
            default:
                return -1;
        }
    }

    public static boolean needsShift(char c) {
        if (Character.isUpperCase(c)) {
            return true;
        }
        return "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
    }

    public static void sendUnicodeCharMacOS(int codePoint, ConnectionManager cm)
            throws InterruptedException {
        String hex = String.format("%04x", codePoint);
        Log.d(TAG, "Unicode hex input: U+" + hex.toUpperCase(Locale.ROOT) + " for codePoint=" + codePoint);

        final int kAlt = 0x04;

        cm.sendRawHIDReport(kAlt, 0x00);
        Thread.sleep(50);

        for (char c : hex.toCharArray()) {
            int code = hexCharToHidCode(c);
            if (code < 0) {
                continue;
            }
            cm.sendRawHIDReport(kAlt, code);
            Thread.sleep(50);
            cm.sendRawHIDReport(kAlt, 0x00);
            Thread.sleep(50);
        }

        cm.sendRawHIDReport(0x00, 0x00);
        Thread.sleep(100);
    }

    public static void sendUnicodeCharWindows(int codePoint, ConnectionManager cm)
            throws InterruptedException {
        String hex = String.format("%04X", codePoint);
        Log.d(TAG, "Windows Unicode input: U+" + hex);

        final int kAlt = 0x04;
        final int kNumpadPlus = 0x57;

        cm.sendRawHIDReport(kAlt, 0x00);
        Thread.sleep(50);
        cm.sendRawHIDReport(kAlt, kNumpadPlus);
        Thread.sleep(50);
        cm.sendRawHIDReport(kAlt, 0x00);
        Thread.sleep(50);

        for (char c : hex.toCharArray()) {
            int code = windowsHexKeyCode(c);
            if (code < 0) {
                continue;
            }
            cm.sendRawHIDReport(kAlt, code);
            Thread.sleep(50);
            cm.sendRawHIDReport(kAlt, 0x00);
            Thread.sleep(50);
        }

        cm.sendRawHIDReport(0x00, 0x00);
        Thread.sleep(100);
    }

    public static int windowsHexKeyCode(char c) {
        switch (c) {
            case '0':
                return 0x62;
            case '1':
                return 0x59;
            case '2':
                return 0x5A;
            case '3':
                return 0x5B;
            case '4':
                return 0x5C;
            case '5':
                return 0x5D;
            case '6':
                return 0x5E;
            case '7':
                return 0x5F;
            case '8':
                return 0x60;
            case '9':
                return 0x61;
            case 'A':
                return 0x04;
            case 'B':
                return 0x05;
            case 'C':
                return 0x06;
            case 'D':
                return 0x07;
            case 'E':
                return 0x08;
            case 'F':
                return 0x09;
            default:
                return -1;
        }
    }

    public static void sendUnicodeCharLinux(int codePoint, ConnectionManager cm)
            throws InterruptedException {
        String hex = String.format("%04x", codePoint);
        Log.d(TAG, "Linux Unicode input: U+" + hex);

        final int kCtrlShift = 0x01 | 0x02;

        cm.sendRawHIDReport(kCtrlShift, 0x18);
        Thread.sleep(80);
        cm.sendRawHIDReport(0x00, 0x00);
        Thread.sleep(80);

        for (char c : hex.toCharArray()) {
            int code = hexCharToHidCode(c);
            if (code < 0) {
                continue;
            }
            cm.sendRawHIDReport(0x00, code);
            Thread.sleep(50);
            cm.sendRawHIDReport(0x00, 0x00);
            Thread.sleep(50);
        }

        cm.sendRawHIDReport(0x00, 0x28);
        Thread.sleep(80);
        cm.sendRawHIDReport(0x00, 0x00);
        Thread.sleep(50);
    }

    public static int hexCharToHidCode(char c) {
        switch (c) {
            case '0':
                return 0x27;
            case '1':
                return 0x1E;
            case '2':
                return 0x1F;
            case '3':
                return 0x20;
            case '4':
                return 0x21;
            case '5':
                return 0x22;
            case '6':
                return 0x23;
            case '7':
                return 0x24;
            case '8':
                return 0x25;
            case '9':
                return 0x26;
            case 'a':
                return 0x04;
            case 'b':
                return 0x05;
            case 'c':
                return 0x06;
            case 'd':
                return 0x07;
            case 'e':
                return 0x08;
            case 'f':
                return 0x09;
            default:
                return -1;
        }
    }
}
