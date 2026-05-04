package com.openterface.keymod.preset;

import com.openterface.keymod.ShortcutProfileManager.Shortcut;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates the non-deletable Rows 2–3 built-ins so future edits to
 * {@link Rows23StripProfileBuiltins} cannot regress slot integrity, modifier limits, the
 * non-deletable contract enforced by {@link Rows23StripProfileConstants#isBuiltInProfileId},
 * the page-2 row-2 paren/grave-tilde invariant relied on by
 * {@code Rows23StripProfileManager.replaceBuiltInProfileIfPage2Row2LayoutStale}, or the
 * "Unicode shortcut → no HID keyCode/modifiers, icon == codepoint string" contract used by
 * {@code CustomKeyboardView.sendStripUnicodeShortcut}.
 */
public class Rows23StripBuiltinsTest {

    /**
     * All built-in profiles whose page-2 row-2 cols 0–1 must keep ASCII paren / grave-tilde and
     * which are subject to the slot-integrity / coverage / modifier checks below.
     */
    private static Rows23StripProfile[] allBuiltIns() {
        return new Rows23StripProfile[]{
                Rows23StripProfileBuiltins.buildSymbolsProfile(),
                Rows23StripProfileBuiltins.buildMathProfile(),
                Rows23StripProfileBuiltins.buildBoxLinesProfile(),
                Rows23StripProfileBuiltins.buildLatinProfile(),
                Rows23StripProfileBuiltins.buildArrowsProfile(),
                Rows23StripProfileBuiltins.buildCurrencyProfile(),
        };
    }

    @Test
    public void isBuiltInProfileId_recognizesAllSeven() {
        String[] builtIns = new String[]{
                Rows23StripProfileConstants.DEFAULT_PROFILE_ID,
                Rows23StripProfileConstants.SYMBOLS_PROFILE_ID,
                Rows23StripProfileConstants.MATH_PROFILE_ID,
                Rows23StripProfileConstants.BOX_LINES_PROFILE_ID,
                Rows23StripProfileConstants.LATIN_PROFILE_ID,
                Rows23StripProfileConstants.ARROWS_PROFILE_ID,
                Rows23StripProfileConstants.CURRENCY_PROFILE_ID,
        };
        for (String id : builtIns) {
            Assert.assertTrue("expected built-in: " + id,
                    Rows23StripProfileConstants.isBuiltInProfileId(id));
        }
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId("strip_user_made"));
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId(null));
    }

    @Test
    public void symbolsProfile_hasIdNameAndContent() {
        assertProfileShellComplete(
                Rows23StripProfileBuiltins.buildSymbolsProfile(),
                Rows23StripProfileConstants.SYMBOLS_PROFILE_ID);
    }

    @Test
    public void mathProfile_hasIdNameAndContent() {
        assertProfileShellComplete(
                Rows23StripProfileBuiltins.buildMathProfile(),
                Rows23StripProfileConstants.MATH_PROFILE_ID);
    }

    @Test
    public void boxLinesProfile_hasIdNameAndContent() {
        assertProfileShellComplete(
                Rows23StripProfileBuiltins.buildBoxLinesProfile(),
                Rows23StripProfileConstants.BOX_LINES_PROFILE_ID);
    }

    @Test
    public void latinProfile_hasIdNameAndContent() {
        assertProfileShellComplete(
                Rows23StripProfileBuiltins.buildLatinProfile(),
                Rows23StripProfileConstants.LATIN_PROFILE_ID);
    }

    @Test
    public void arrowsProfile_hasIdNameAndContent() {
        assertProfileShellComplete(
                Rows23StripProfileBuiltins.buildArrowsProfile(),
                Rows23StripProfileConstants.ARROWS_PROFILE_ID);
    }

    @Test
    public void currencyProfile_hasIdNameAndContent() {
        assertProfileShellComplete(
                Rows23StripProfileBuiltins.buildCurrencyProfile(),
                Rows23StripProfileConstants.CURRENCY_PROFILE_ID);
    }

    private static void assertProfileShellComplete(Rows23StripProfile p, String expectedId) {
        Assert.assertEquals(expectedId, p.id);
        Assert.assertNotNull(p.name);
        Assert.assertFalse(p.name.trim().isEmpty());
        Assert.assertFalse(p.slotMap.isEmpty());
        Assert.assertFalse(p.shortcuts.isEmpty());
    }

    @Test
    public void allBuiltIns_everySlotKeyParsesAndPointsAtKnownShortcut() {
        for (Rows23StripProfile p : allBuiltIns()) {
            assertProfileSlotIntegrity(p);
        }
    }

    @Test
    public void allBuiltIns_modifiersAreStripCompliant() {
        for (Rows23StripProfile p : allBuiltIns()) {
            assertModifiersStripCompliant(p);
        }
    }

    @Test
    public void allBuiltIns_coversBothLayersAcrossAllPages() {
        for (Rows23StripProfile p : allBuiltIns()) {
            assertCoversAllSlotPositions(p);
        }
    }

    private static void assertProfileSlotIntegrity(Rows23StripProfile p) {
        Set<String> shortcutIds = new HashSet<>();
        for (Shortcut s : p.shortcuts) {
            Assert.assertNotNull(s.id);
            Assert.assertFalse(s.id.trim().isEmpty());
            Assert.assertTrue("duplicate shortcut id: " + s.id, shortcutIds.add(s.id));
        }
        for (Map.Entry<String, String> e : p.slotMap.entrySet()) {
            String slotKey = e.getKey();
            FixedStripLayoutCatalog.ParsedSlotKey parsed =
                    FixedStripLayoutCatalog.parseSlotKey(slotKey);
            Assert.assertNotNull("slot key did not parse: " + slotKey, parsed);
            Assert.assertEquals("slot key not canonical: " + slotKey,
                    StripSlotMapStore.slotKey(parsed.pageIndex, parsed.stripRow, parsed.col, parsed.fnLayer),
                    slotKey);
            Assert.assertTrue(
                    "slotMap references missing shortcut id: " + e.getValue(),
                    shortcutIds.contains(e.getValue()));
        }
    }

    private static void assertModifiersStripCompliant(Rows23StripProfile p) {
        for (Shortcut s : p.shortcuts) {
            int normalized = HidKeyCatalog.normalizeStripModifiers(s.modifiers);
            Assert.assertEquals(
                    "shortcut " + s.id + " has non-strip modifiers: 0x"
                            + Integer.toHexString(s.modifiers),
                    s.modifiers,
                    normalized);
        }
    }

    private static void assertCoversAllSlotPositions(Rows23StripProfile p) {
        // 38 customizable strip positions (excluding FN/IME slots) × 2 layers (base + Fn).
        int[][] expectedSlotsPerPageRow = {
                {0, 2, 7}, {0, 3, 6}, // page 0 row 2 has 7 cols; row 3 has 6 (col 6 = FN)
                {1, 2, 6}, {1, 3, 6}, // page 1 row 2 has 6 cols (col 6 = IME); row 3 has 6 (col 6 = FN)
                {2, 2, 7}, {2, 3, 6}, // page 2 row 2 has 7 cols; row 3 has 6 (col 6 = FN)
        };
        int totalExpected = 0;
        for (int[] r : expectedSlotsPerPageRow) {
            totalExpected += r[2] * 2;
        }
        Assert.assertEquals(76, totalExpected);
        Assert.assertEquals("expected coverage of all customizable slots",
                totalExpected, p.slotMap.size());
        for (int[] r : expectedSlotsPerPageRow) {
            int page = r[0];
            int row = r[1];
            int cols = r[2];
            for (int c = 0; c < cols; c++) {
                String base = StripSlotMapStore.slotKey(page, row, c, false);
                String fn = StripSlotMapStore.slotKey(page, row, c, true);
                Assert.assertTrue("missing base slot: " + base, p.slotMap.containsKey(base));
                Assert.assertTrue("missing fn slot: " + fn, p.slotMap.containsKey(fn));
            }
        }
    }

    @Test
    public void allBuiltIns_page2Row2FnUsesGraveTildeHid() {
        for (Rows23StripProfile p : allBuiltIns()) {
            for (int col = 0; col <= 1; col++) {
                String sk = StripSlotMapStore.slotKey(2, 2, col, true);
                String sid = p.slotMap.get(sk);
                Assert.assertNotNull("slot " + sk, sid);
                Shortcut found = findShortcut(p, sid);
                Assert.assertNotNull(found);
                Assert.assertEquals(
                        "page-2 row-2 fn col " + col + " in " + p.id + " must keep grave/tilde HID",
                        0x35, found.keyCode);
                int m = HidKeyCatalog.normalizeStripModifiers(found.modifiers);
                Assert.assertEquals(col == 0 ? 0 : 0x02, m);
                Assert.assertEquals(
                        "page-2 row-2 fn col " + col + " must not be a Unicode shortcut",
                        0, found.unicodeCodePoint);
            }
        }
    }

    @Test
    public void allBuiltIns_page2Row2BaseUsesParenHid() {
        for (Rows23StripProfile p : allBuiltIns()) {
            for (int col = 0; col <= 1; col++) {
                String sk = StripSlotMapStore.slotKey(2, 2, col, false);
                String sid = p.slotMap.get(sk);
                Assert.assertNotNull("slot " + sk, sid);
                Shortcut found = findShortcut(p, sid);
                Assert.assertNotNull(found);
                Assert.assertEquals(col == 0 ? 0x26 : 0x27, found.keyCode);
                Assert.assertEquals(0x02, HidKeyCatalog.normalizeStripModifiers(found.modifiers));
                Assert.assertEquals(
                        "page-2 row-2 base col " + col + " must not be a Unicode shortcut",
                        0, found.unicodeCodePoint);
            }
        }
    }

    /**
     * Every built-in slot must populate {@link Shortcut#icon} so the strip cell renderer always
     * has a glyph to draw (drawable name OR Unicode glyph). Future authors stripping this would
     * regress every themed built-in to text-only caps.
     */
    @Test
    public void allBuiltIns_everySlotShortcutHasNonEmptyIcon() {
        for (Rows23StripProfile p : allBuiltIns()) {
            for (Shortcut s : p.shortcuts) {
                Assert.assertNotNull("shortcut " + s.id + " missing icon", s.icon);
                Assert.assertFalse(
                        "shortcut " + s.id + " has empty icon",
                        s.icon.trim().isEmpty());
            }
        }
    }

    /**
     * Page 2 row 2 cols 0–1 are ASCII "(" / ")"; renderer's {@code isEmojiIcon} treats pure
     * printable-ASCII as text-path so chord cap typography wins. This test locks the data side of
     * that contract: those slots' icons must be ASCII so they do NOT get routed through the
     * larger {@code customIconGlyph} centered path.
     */
    @Test
    public void allBuiltIns_page2Row2BaseIconsAreAsciiForChordTextPath() {
        for (Rows23StripProfile p : allBuiltIns()) {
            for (int col = 0; col <= 1; col++) {
                String sk = StripSlotMapStore.slotKey(2, 2, col, false);
                String sid = p.slotMap.get(sk);
                Shortcut found = findShortcut(p, sid);
                Assert.assertNotNull(found);
                String expected = col == 0 ? "(" : ")";
                Assert.assertEquals(expected, found.icon);
                assertPureAscii("page-2 row-2 base col " + col + " icon (" + p.id + ")",
                        found.icon);
            }
        }
    }

    /**
     * Every {@link Shortcut#unicodeCodePoint} that is non-zero must:
     * (1) zero out {@link Shortcut#keyCode} / {@link Shortcut#modifiers} so the strip dispatcher
     *     never accidentally falls through to {@code sendShortcutWithModifiers},
     * (2) populate {@link Shortcut#icon} with the exact codepoint string, so the chord/glyph
     *     renderer shows the same character that gets typed, and
     * (3) be a single BMP code point (the per-OS Unicode Hex Input path emits 4 hex digits).
     */
    @Test
    public void unicodeProfiles_unicodeShortcuts_haveZeroKeyCodeAndCodepointMatchesIcon() {
        for (Rows23StripProfile p : allBuiltIns()) {
            for (Shortcut s : p.shortcuts) {
                if (s.unicodeCodePoint == 0) {
                    continue;
                }
                Assert.assertEquals(
                        "Unicode shortcut " + s.id + " must have keyCode 0",
                        0, s.keyCode);
                Assert.assertEquals(
                        "Unicode shortcut " + s.id + " must have modifiers 0",
                        0, s.modifiers);
                String expected = new String(Character.toChars(s.unicodeCodePoint));
                Assert.assertEquals(
                        "Unicode shortcut " + s.id + " icon must equal codepoint string",
                        expected, s.icon);
                Assert.assertTrue(
                        "Unicode shortcut " + s.id + " must be in BMP for OS hex-input path: U+"
                                + Integer.toHexString(s.unicodeCodePoint),
                        s.unicodeCodePoint >= 0x80 && s.unicodeCodePoint <= 0xFFFF);
            }
        }
    }

    /**
     * The four newer Unicode-typing profiles ("Box & Lines", "Latin Extended", "Arrows & Shapes",
     * "Currency & Punctuation") should populate every customizable slot apart from the four
     * page-2 row-2 cols 0–1 paren/grave-tilde reservations with a non-zero
     * {@link Shortcut#unicodeCodePoint}. This guards against accidentally leaving an ASCII HID
     * shortcut in a slot of a Unicode profile (which would type the wrong character).
     */
    @Test
    public void unicodeProfiles_haveExactlyFourAsciiReservedSlots() {
        Rows23StripProfile[] unicodeProfiles = new Rows23StripProfile[]{
                Rows23StripProfileBuiltins.buildBoxLinesProfile(),
                Rows23StripProfileBuiltins.buildLatinProfile(),
                Rows23StripProfileBuiltins.buildArrowsProfile(),
                Rows23StripProfileBuiltins.buildCurrencyProfile(),
        };
        for (Rows23StripProfile p : unicodeProfiles) {
            int asciiCount = 0;
            int unicodeCount = 0;
            for (Shortcut s : p.shortcuts) {
                if (s.unicodeCodePoint == 0) {
                    asciiCount++;
                } else {
                    unicodeCount++;
                }
            }
            Assert.assertEquals(
                    "Unicode profile " + p.id + " should have exactly 4 ASCII paren/grave-tilde slots",
                    4, asciiCount);
            Assert.assertEquals(
                    "Unicode profile " + p.id + " should have 72 Unicode-typing slots",
                    72, unicodeCount);
        }
    }

    private static Shortcut findShortcut(Rows23StripProfile p, String shortcutId) {
        for (Shortcut x : p.shortcuts) {
            if (shortcutId.equals(x.id)) {
                return x;
            }
        }
        return null;
    }

    private static void assertPureAscii(String message, String s) {
        Assert.assertNotNull(message, s);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            Assert.assertTrue(
                    message + ": expected pure printable ASCII, got code point 0x"
                            + Integer.toHexString(cp),
                    cp >= 0x21 && cp <= 0x7E);
            i += Character.charCount(cp);
        }
    }
}
