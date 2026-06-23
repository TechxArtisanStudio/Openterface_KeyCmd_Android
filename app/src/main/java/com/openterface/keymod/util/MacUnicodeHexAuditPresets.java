package com.openterface.keymod.util;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Fixed code-point lists for macOS Unicode Hex diagnostic: each round uses {@link
 * HidTextKeystrokeSender#sendUnicodeCharMacOS(int, com.openterface.keymod.ConnectionManager)}.
 * Union of hex digits across all rounds in a preset must cover {@code 0-9} and {@code a-f}.
 */
public final class MacUnicodeHexAuditPresets {

    /** Preset A — 4 rounds (CJK-heavy; fewer confirmations). */
    public static final int[] PRESET_A_CODEPOINTS = {
        0x516D, 0x897F, 0x4EAC, 0x03B2,
    };

    /** Preset B — 6 rounds (symbols; language-neutral expected glyphs). */
    public static final int[] PRESET_B_CODEPOINTS = {
        0x2318, 0x23CE, 0x00D7, 0x00A9, 0x2465, 0x00BF,
    };

    private static final Set<Character> ALL_HEX_KEYS = allHexKeySet();

    private MacUnicodeHexAuditPresets() {}

    @NonNull
    public static String hexForCodePoint(int codePoint) {
        return String.format(Locale.ROOT, "%04x", codePoint);
    }

    /** Union of hex characters (lowercase) used in {@code sendUnicodeCharMacOS} for these code points. */
    @NonNull
    public static Set<Character> hexDigitUnion(@NonNull int[] codePoints) {
        Set<Character> out = new HashSet<>();
        for (int cp : codePoints) {
            for (char c : hexForCodePoint(cp).toCharArray()) {
                out.add(c);
            }
        }
        return out;
    }

    /** True iff the union equals every digit {@code 0-9} and every letter {@code a-f}. */
    public static boolean coversAllHexKeys(@NonNull int[] codePoints) {
        return hexDigitUnion(codePoints).equals(ALL_HEX_KEYS);
    }

    @NonNull
    private static Set<Character> allHexKeySet() {
        Set<Character> s = new HashSet<>();
        for (char c = '0'; c <= '9'; c++) {
            s.add(c);
        }
        for (char c = 'a'; c <= 'f'; c++) {
            s.add(c);
        }
        return s;
    }

    /** @return a new array copy for callers that may mutate */
    @NonNull
    public static int[] copyPresetA() {
        return Arrays.copyOf(PRESET_A_CODEPOINTS, PRESET_A_CODEPOINTS.length);
    }

    @NonNull
    public static int[] copyPresetB() {
        return Arrays.copyOf(PRESET_B_CODEPOINTS, PRESET_B_CODEPOINTS.length);
    }
}
