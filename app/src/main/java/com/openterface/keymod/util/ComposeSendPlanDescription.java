package com.openterface.keymod.util;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Human-readable send plans for compose warning previews. Logic mirrors {@link
 * HidTextKeystrokeSender#countSendUnits} / token walk so unit counts stay consistent.
 */
public final class ComposeSendPlanDescription {

    /** Same cap as {@link ComposeSendPreviewDialog} for ASCII payload display. */
    public static final int ASCII_PAYLOAD_MAX_CHARS = 1200;

    /** Max characters for the Unicode transcript body (excluding truncation footer). */
    public static final int UNICODE_TRANSCRIPT_MAX_CHARS = 8000;

    private ComposeSendPlanDescription() {}

    public static final class AsciiPlan {
        public final int originalLength;
        public final int sendableLength;
        public final int droppedNonAsciiCount;
        @NonNull public final String asciiPayload;

        AsciiPlan(
                int originalLength,
                int sendableLength,
                int droppedNonAsciiCount,
                @NonNull String asciiPayload) {
            this.originalLength = originalLength;
            this.sendableLength = sendableLength;
            this.droppedNonAsciiCount = droppedNonAsciiCount;
            this.asciiPayload = asciiPayload;
        }
    }

    @NonNull
    public static AsciiPlan buildAsciiPlan(@NonNull String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder ascii = new StringBuilder(text.length());
        int dropped = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp > 0x7E) {
                dropped++;
            } else {
                ascii.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return new AsciiPlan(text.length(), ascii.length(), dropped, ascii.toString());
    }

    /**
     * Builds a monospace-friendly transcript for the Unicode-capable send path (same steps as {@link
     * HidTextKeystrokeSender#send} with {@code allowUnicode=true}).
     */
    @NonNull
    public static String buildUnicodeTranscript(
            @NonNull String text, @NonNull String targetOs, int maxOutputChars) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(targetOs, "targetOs");
        List<String> tokens = HidTextKeystrokeSender.tokenizeInput(text);
        StringBuilder out = new StringBuilder();
        int activeMods = 0;
        int unicodeLinesOmitted = 0;
        StringBuilder asciiRun = new StringBuilder();

        for (String token : tokens) {
            if (token.startsWith("</") && token.endsWith(">")) {
                flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                appendLine(out, maxOutputChars, "Token: " + token + " (modifier released)");
                activeMods = 0;
                continue;
            }
            switch (token) {
                case "<CTRL>":
                    flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                    appendLine(out, maxOutputChars, "Token: <CTRL> (modifier held)");
                    activeMods |= 0x01;
                    continue;
                case "<SHIFT>":
                    flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                    appendLine(out, maxOutputChars, "Token: <SHIFT> (modifier held)");
                    activeMods |= 0x02;
                    continue;
                case "<ALT>":
                    flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                    appendLine(out, maxOutputChars, "Token: <ALT> (modifier held)");
                    activeMods |= 0x04;
                    continue;
                case "<CMD>":
                case "<WIN>":
                    flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                    appendLine(out, maxOutputChars, "Token: " + token + " (modifier held)");
                    activeMods |= 0x08;
                    continue;
                default:
                    break;
            }
            if (token.equals("<DELAY1S>")) {
                flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                appendLine(out, maxOutputChars, "Pause: 1s");
                continue;
            }
            if (token.equals("<DELAY2S>")) {
                flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                appendLine(out, maxOutputChars, "Pause: 2s");
                continue;
            }
            if (token.equals("<DELAY5S>")) {
                flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                appendLine(out, maxOutputChars, "Pause: 5s");
                continue;
            }
            if (token.equals("<DELAY10S>")) {
                flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                appendLine(out, maxOutputChars, "Pause: 10s");
                continue;
            }
            int special = HidTextKeystrokeSender.specialTokenToHidCode(token);
            if (special > 0) {
                flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                appendLine(out, maxOutputChars, "Key: " + humanSpecialToken(token));
                continue;
            }
            for (int ci = 0; ci < token.length(); ) {
                int cp = token.codePointAt(ci);
                ci += Character.charCount(cp);
                if (cp > 0x7E) {
                    flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);
                    if (!appendUnicodeCodePointLine(out, maxOutputChars, cp, targetOs)) {
                        unicodeLinesOmitted++;
                    }
                } else {
                    char c = (char) cp;
                    int hid = HidTextKeystrokeSender.mapCharToHidCode(c);
                    if (hid < 0) {
                        continue;
                    }
                    asciiRun.append(c);
                }
            }
        }
        flushAsciiTranscriptRun(asciiRun, out, maxOutputChars, activeMods);

        if (unicodeLinesOmitted > 0) {
            appendLine(
                    out,
                    maxOutputChars,
                    "… +" + unicodeLinesOmitted + " more non-ASCII code point(s) not shown (truncated).");
        }
        return out.toString().trim();
    }

    private static void flushAsciiTranscriptRun(
            StringBuilder asciiRun,
            StringBuilder out,
            int maxOutputChars,
            int activeMods) {
        if (asciiRun.length() == 0) {
            return;
        }
        appendLine(
                out,
                maxOutputChars,
                "Type: "
                        + quoteForTranscript(asciiRun.toString())
                        + (activeMods != 0 ? " (with modifier chord)" : ""));
        asciiRun.setLength(0);
    }

    private static void appendLine(StringBuilder out, int maxChars, String line) {
        if (out.length() + line.length() + 1 > maxChars) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(line);
    }

    private static boolean appendUnicodeCodePointLine(
            StringBuilder out, int maxChars, int codePoint, String targetOs) {
        String hexLower = String.format(Locale.ROOT, "%04x", codePoint);
        String hexUpper = hexLower.toUpperCase(Locale.ROOT);
        String label = unicodeCodePointLabel(codePoint);
        String detail;
        if ("macos".equals(targetOs)) {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < hexLower.length(); i++) {
                if (i > 0) {
                    digits.append(", ");
                }
                digits.append(hexLower.charAt(i));
            }
            detail =
                    "Unicode Hex (macOS): hold Option, then type hex digits: "
                            + digits
                            + " (U+"
                            + hexUpper
                            + label
                            + ")";
        } else if ("windows".equals(targetOs)) {
            detail =
                    "Unicode (Windows): Alt+Numpad+ then hex digits "
                            + hexUpper
                            + " (U+"
                            + hexUpper
                            + label
                            + ")";
        } else if ("linux".equals(targetOs)) {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < hexLower.length(); i++) {
                if (i > 0) {
                    digits.append(' ');
                }
                digits.append(hexLower.charAt(i));
            }
            detail =
                    "Unicode (Linux): Ctrl+Shift+U, then "
                            + digits
                            + ", Enter — U+"
                            + hexUpper
                            + label
                            + ")";
        } else {
            detail = "Unicode: U+" + hexUpper + label + " (enable OS-specific Unicode entry on target)";
        }
        if (out.length() + detail.length() + 1 > maxChars) {
            return false;
        }
        appendLine(out, maxChars, detail);
        return true;
    }

    @NonNull
    private static String unicodeCodePointLabel(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) {
            return "";
        }
        if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.UNASSIGNED) {
            return "";
        }
        if (Character.charCount(codePoint) == 1) {
            char ch = (char) codePoint;
            if (ch == '\'' || ch == '\\') {
                return "";
            }
            return " \"" + ch + "\"";
        }
        return "";
    }

    @NonNull
    private static String quoteForTranscript(@NonNull String s) {
        String escaped = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    @NonNull
    private static String humanSpecialToken(@NonNull String token) {
        if (!token.startsWith("<") || !token.endsWith(">")) {
            return token;
        }
        String inner = token.substring(1, token.length() - 1);
        return inner + " (" + token + ")";
    }

    /** ASCII payload truncated for UI (matches preview dialog behavior). */
    @NonNull
    public static String truncateAsciiPayload(@NonNull String asciiPayload) {
        if (asciiPayload.length() <= ASCII_PAYLOAD_MAX_CHARS) {
            return asciiPayload;
        }
        return asciiPayload.substring(0, ASCII_PAYLOAD_MAX_CHARS) + "\n…";
    }
}
