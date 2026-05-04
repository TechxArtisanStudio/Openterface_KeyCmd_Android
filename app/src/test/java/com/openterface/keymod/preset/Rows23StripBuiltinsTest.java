package com.openterface.keymod.preset;

import com.openterface.keymod.ShortcutProfileManager.Shortcut;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates the two non-deletable Rows 2–3 built-ins ("Symbols ★", "Math ∑") so future edits to
 * {@link Rows23StripProfileBuiltins} cannot regress slot integrity, modifier limits, or the
 * non-deletable contract enforced by {@link Rows23StripProfileConstants#isBuiltInProfileId}.
 */
public class Rows23StripBuiltinsTest {

    @Test
    public void isBuiltInProfileId_recognizesAllThree() {
        Assert.assertTrue(Rows23StripProfileConstants.isBuiltInProfileId(
                Rows23StripProfileConstants.DEFAULT_PROFILE_ID));
        Assert.assertTrue(Rows23StripProfileConstants.isBuiltInProfileId(
                Rows23StripProfileConstants.SYMBOLS_PROFILE_ID));
        Assert.assertTrue(Rows23StripProfileConstants.isBuiltInProfileId(
                Rows23StripProfileConstants.MATH_PROFILE_ID));
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId("strip_user_made"));
        Assert.assertFalse(Rows23StripProfileConstants.isBuiltInProfileId(null));
    }

    @Test
    public void symbolsProfile_hasIdNameAndContent() {
        Rows23StripProfile p = Rows23StripProfileBuiltins.buildSymbolsProfile();
        Assert.assertEquals(Rows23StripProfileConstants.SYMBOLS_PROFILE_ID, p.id);
        Assert.assertNotNull(p.name);
        Assert.assertFalse(p.name.trim().isEmpty());
        Assert.assertFalse(p.slotMap.isEmpty());
        Assert.assertFalse(p.shortcuts.isEmpty());
    }

    @Test
    public void mathProfile_hasIdNameAndContent() {
        Rows23StripProfile p = Rows23StripProfileBuiltins.buildMathProfile();
        Assert.assertEquals(Rows23StripProfileConstants.MATH_PROFILE_ID, p.id);
        Assert.assertNotNull(p.name);
        Assert.assertFalse(p.name.trim().isEmpty());
        Assert.assertFalse(p.slotMap.isEmpty());
        Assert.assertFalse(p.shortcuts.isEmpty());
    }

    @Test
    public void symbolsProfile_everySlotKeyParsesAndPointsAtKnownShortcut() {
        assertProfileSlotIntegrity(Rows23StripProfileBuiltins.buildSymbolsProfile());
    }

    @Test
    public void mathProfile_everySlotKeyParsesAndPointsAtKnownShortcut() {
        assertProfileSlotIntegrity(Rows23StripProfileBuiltins.buildMathProfile());
    }

    @Test
    public void symbolsProfile_modifiersAreStripCompliant() {
        assertModifiersStripCompliant(Rows23StripProfileBuiltins.buildSymbolsProfile());
    }

    @Test
    public void mathProfile_modifiersAreStripCompliant() {
        assertModifiersStripCompliant(Rows23StripProfileBuiltins.buildMathProfile());
    }

    @Test
    public void symbolsProfile_coversBothLayersAcrossAllPages() {
        assertCoversAllSlotPositions(Rows23StripProfileBuiltins.buildSymbolsProfile());
    }

    @Test
    public void mathProfile_coversBothLayersAcrossAllPages() {
        assertCoversAllSlotPositions(Rows23StripProfileBuiltins.buildMathProfile());
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
    public void symbolsAndMathProfiles_page2Row2FnUsesGraveTildeHid() {
        for (Rows23StripProfile p : new Rows23StripProfile[]{
                Rows23StripProfileBuiltins.buildSymbolsProfile(),
                Rows23StripProfileBuiltins.buildMathProfile(),
        }) {
            for (int col = 0; col <= 1; col++) {
                String sk = StripSlotMapStore.slotKey(2, 2, col, true);
                String sid = p.slotMap.get(sk);
                Assert.assertNotNull("slot " + sk, sid);
                Shortcut found = null;
                for (Shortcut x : p.shortcuts) {
                    if (sid.equals(x.id)) {
                        found = x;
                        break;
                    }
                }
                Assert.assertNotNull(found);
                Assert.assertEquals(0x35, found.keyCode);
                int m = HidKeyCatalog.normalizeStripModifiers(found.modifiers);
                Assert.assertEquals(col == 0 ? 0 : 0x02, m);
            }
        }
    }

    @Test
    public void symbolsAndMathProfiles_page2Row2BaseUsesParenHid() {
        for (Rows23StripProfile p : new Rows23StripProfile[]{
                Rows23StripProfileBuiltins.buildSymbolsProfile(),
                Rows23StripProfileBuiltins.buildMathProfile(),
        }) {
            for (int col = 0; col <= 1; col++) {
                String sk = StripSlotMapStore.slotKey(2, 2, col, false);
                String sid = p.slotMap.get(sk);
                Assert.assertNotNull("slot " + sk, sid);
                Shortcut found = null;
                for (Shortcut x : p.shortcuts) {
                    if (sid.equals(x.id)) {
                        found = x;
                        break;
                    }
                }
                Assert.assertNotNull(found);
                Assert.assertEquals(col == 0 ? 0x26 : 0x27, found.keyCode);
                Assert.assertEquals(0x02, HidKeyCatalog.normalizeStripModifiers(found.modifiers));
            }
        }
    }

    /**
     * Every built-in slot must populate {@link Shortcut#icon} so the strip cell renderer always
     * has a glyph to draw (drawable name OR Unicode glyph). Future authors stripping this would
     * regress Symbols ★ / Math ∑ to text-only caps.
     */
    @Test
    public void symbolsAndMathProfiles_everySlotShortcutHasNonEmptyIcon() {
        for (Rows23StripProfile p : new Rows23StripProfile[]{
                Rows23StripProfileBuiltins.buildSymbolsProfile(),
                Rows23StripProfileBuiltins.buildMathProfile(),
        }) {
            for (Shortcut s : p.shortcuts) {
                Assert.assertNotNull("shortcut " + s.id + " missing icon", s.icon);
                Assert.assertFalse(
                        "shortcut " + s.id + " has empty icon",
                        s.icon.trim().isEmpty());
            }
        }
    }

    /**
     * Symbols/Math use non-ASCII display glyphs for page 2 row 2 cols 1–2 (same HID as "(" / ")")
     * so the strip uses the same centered-glyph autosize path as other slots.
     */
    @Test
    public void mathProfile_page2Row2BaseUsesAngleBracketGlyphs() {
        Rows23StripProfile p = Rows23StripProfileBuiltins.buildMathProfile();
        Shortcut b0 = shortcutForSlot(p, StripSlotMapStore.slotKey(2, 2, 0, false));
        Shortcut b1 = shortcutForSlot(p, StripSlotMapStore.slotKey(2, 2, 1, false));
        Assert.assertEquals("\u27E8", b0.label.trim());
        Assert.assertEquals("\u27E9", b1.label.trim());
        Assert.assertEquals("\u27E8", b0.icon.trim());
        Assert.assertEquals("\u27E9", b1.icon.trim());
    }

    @Test
    public void symbolsProfile_page2Row2BaseUsesTortoiseShellGlyphs() {
        Rows23StripProfile p = Rows23StripProfileBuiltins.buildSymbolsProfile();
        Shortcut b0 = shortcutForSlot(p, StripSlotMapStore.slotKey(2, 2, 0, false));
        Shortcut b1 = shortcutForSlot(p, StripSlotMapStore.slotKey(2, 2, 1, false));
        Assert.assertEquals("\u2985", b0.label.trim());
        Assert.assertEquals("\u2986", b1.label.trim());
    }

    @Test
    public void symbolsAndMathProfiles_page2Row2FnCol2UsesQuoteNotDuplicateGrave() {
        for (Rows23StripProfile p : new Rows23StripProfile[]{
                Rows23StripProfileBuiltins.buildSymbolsProfile(),
                Rows23StripProfileBuiltins.buildMathProfile(),
        }) {
            Shortcut fn = shortcutForSlot(p, StripSlotMapStore.slotKey(2, 2, 2, true));
            Assert.assertEquals(0x34, fn.keyCode);
            Assert.assertEquals(0, HidKeyCatalog.normalizeStripModifiers(fn.modifiers));
        }
    }

    private static Shortcut shortcutForSlot(Rows23StripProfile p, String slotKey) {
        String sid = p.slotMap.get(slotKey);
        Assert.assertNotNull(sid);
        for (Shortcut x : p.shortcuts) {
            if (sid.equals(x.id)) {
                return x;
            }
        }
        Assert.fail("missing shortcut for " + slotKey);
        return null;
    }
}
